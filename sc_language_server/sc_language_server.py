"""
A sdtio wrapper for the LanguageServer.quark LSP server for SuperCollider.

See:
    https://github.com/scztt/LanguageServer.quark

It allows the language server to be used via stdin/stdout streams for
LSP clients that don't support UDP transport.
"""

from __future__ import annotations

import argparse
import asyncio
import fcntl
import json
import logging
import os
import re
import selectors
import signal
import socket
import sys
import time
from asyncio.streams import StreamReader
from contextlib import closing
from threading import Event, Thread

LOCALHOST = "127.0.0.1"
MAX_UDP_PACKET_SIZE = 508


def _get_free_ports() -> tuple[int, int]:
    """
    Determines two free localhost ports.
    """
    with (
        closing(socket.socket(socket.AF_INET, socket.SOCK_STREAM)) as s,
        closing(socket.socket(socket.AF_INET, socket.SOCK_STREAM)) as c,
    ):
        s.bind(("", 0))
        c.bind(("", 0))
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        c.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        return s.getsockname()[1], c.getsockname()[1]


class UDPSender:
    def __init__(self, transport, logger, remote_addr, remote_port):
        self.transport = transport
        self.logger = logger
        self.remote_addr = remote_addr
        self.remote_port = remote_port
        self._closed = False

    def send(self, msg):
        if self._closed:
            self.logger.warning("Attempted to send data on a closed UDPSender")
            return

        packet_size = len(msg)

        for offset in range(0, packet_size, MAX_UDP_PACKET_SIZE):
            chunk = msg[offset : offset + MAX_UDP_PACKET_SIZE]
            chunk = chunk.encode("utf-8") if isinstance(chunk, str) else chunk
            try:
                self._send_chunk(chunk)
            except Exception as e:
                self.logger.error(f"Error sending chunk: {e}")

    def _send_chunk(self, chunk):
        self.transport.sendto(chunk, (self.remote_addr, self.remote_port))

    def close(self):
        if not self._closed:
            self._closed = True
            self.transport.close()
            self.logger.info("UDPSender closed")


class StdinThread(Thread):
    """
    A small thread that reads stdin and calls a function when data is
    received.
    """

    def __init__(self, on_stdin_received):
        super().__init__()
        self._stop_event = Event()
        self._on_received = on_stdin_received
        self._selector = selectors.DefaultSelector()

    def run(self):
        self._selector.register(sys.stdin, selectors.EVENT_READ, self.__read)

        while not self._stop_event.is_set():
            try:
                # Timeout ensures we go back around the while loop
                # and check the stop event, but at a slow enough
                # rate we don't eat CPU.
                events = self._selector.select(5)
                for key, mask in events:
                    callback = key.data
                    callback(key.fileobj, mask)
            except KeyboardInterrupt:
                break
            except Exception:
                break

    def __read(self, fileobj, mask):
        if not mask & selectors.EVENT_READ:
            return
        data = fileobj.read()
        if data:
            self._on_received(data)
        else:
            # Sleep briefly to avoid pegging the CPU
            time.sleep(0.1)

    def close(self):
        """
        Stops the threads main run loop.
        """
        self._stop_event.set()


class UDPReceiveToStdoutProtocol(asyncio.DatagramProtocol):
    """
    A UDP protocol handler that buffers, reconstructs, and writes LSP messages to stdout
    """

    def __init__(self, logger: logging.Logger) -> None:
        super().__init__()
        self.__logger = logger
        self.__buffer = bytearray()
        self.__content_length = None

    def connection_made(self, transport):
        self.__logger.info("UDP connection made")

    def datagram_received(self, data, addr):
        try:
            self.__buffer.extend(data)
            self.__process_buffer()
        except Exception as e:
            self.__logger.error(f"Error processing datagram: {e}")

    def error_received(self, exc):
        self.__logger.info("Error %s", exc)
        sys.stderr.write(f"UDP error: {exc}\n")

    def __process_buffer(self):
        while True:
            if self.__content_length is None:
                if b"\r\n\r\n" not in self.__buffer:
                    # Not enough data to read the header
                    return

                # Extract the Content-Length
                header, self.__buffer = self.__buffer.split(b"\r\n\r\n", 1)
                header = header.decode("ascii")
                match = re.search(r"Content-Length: (\d+)", header)
                if not match:
                    self.__logger.error("Invalid header received")
                    return
                self.__content_length = int(match.group(1))

            if len(self.__buffer) < self.__content_length:
                # Not enough data for the full message, wait for another packet to arrive
                return

            # Extract the full message body from the buffer
            message = self.__buffer[: self.__content_length].decode("utf-8")
            self.__buffer = self.__buffer[self.__content_length :]
            self.__content_length = None

            # Process the message
            try:
                full_message = f"Content-Length: {len(message)}\r\n\r\n{message}"
                self.__write_message(full_message)
            except json.JSONDecodeError:
                self.__logger.error(f"Invalid JSON received: {message}")

            if not self.__buffer:
                # No more data to process
                return

    def __write_message(self, message):
        sys.stdout.write(message)
        sys.stdout.flush()


class SCRunner:
    """
    A class to manage a sclang suprocess, and connect stdin/out to it
    via UDP.
    """

    ##pylint: disable=too-many-instance-attributes

    defaults = {
        "darwin": "/Applications/SuperCollider.app/Contents/MacOS/sclang",
        "linux": "sclang",
    }

    sclang_path = defaults.get(sys.platform, "")
    ide_name = "vscode"
    server_log_level = "warning"
    receive_port: int
    send_port: int
    ready_message = "***LSP READY***"

    __logger: logging.Logger
    __udp_receiver: asyncio.DatagramTransport | None = None
    __udp_sender: UDPSender | None = None
    __subprocess = None
    __stdin_thread = None

    def __init__(
        self,
        logger: logging.Logger,
        server_log_level: str,
        send_port: int,
        receive_port: int,
        sclang_path: str | None,
        ide_name: str | None,
    ):
        """
        Constructs a new LSPRunner, defaults will be configured for the
        host platform, and can be changed prior to calling start.
        """
        self.__logger = logger
        self.server_log_level = server_log_level
        self.send_port = send_port
        self.receive_port = receive_port

        # Set the optional attributes if provided
        self.sclang_path = sclang_path or self.sclang_path
        self.ide_name = ide_name or self.ide_name

    async def start(self, extra_args: list[str] = []) -> int:
        """
        Starts a sclang subprocess, enabling the LSP server.
        Stdin/out are connected to the server via UDP.
        """
        if self.__subprocess:
            self.__stop_subprocess()

        my_env = os.environ.copy()

        additional_vars = {
            "SCLANG_LSP_ENABLE": "1",
            "SCLANG_LSP_LOGLEVEL": self.server_log_level,
            "SCLANG_LSP_CLIENTPORT": str(self.send_port),
            "SCLANG_LSP_SERVERPORT": str(self.receive_port),
        }

        self.__logger.info("SC env vars: %s", repr(additional_vars))

        command = [self.sclang_path, "-i", self.ide_name, *extra_args]

        self.__logger.info(f"RUNNER: Launching SC with cmd: '{command}'")

        try:
            self.__subprocess = await asyncio.create_subprocess_exec(
                *command,
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE,
                # stdin must be set to PIPE so stdin flows to the main program
                stdin=asyncio.subprocess.PIPE,
                env={**my_env, **additional_vars},
            )
        except FileNotFoundError as e:
            e.strerror = ("The specified sclang path does not exist")
            raise

        # receive stdout and stderr from sclang
        if self.__subprocess.stdout and self.__subprocess.stderr:
            self.__logger.info("stdout, stderr, gather")
            await asyncio.gather(
                self.__receive_output(self.__subprocess.stdout, "SC:STDOUT"),
                self.__receive_output(self.__subprocess.stderr, "SC:STDERR"),
            )

        # Await subprocess termination
        sc_exit_code = await self.__subprocess.wait()

        self.__logger.info("calling stop from sc runner start")
        self.stop()
        return sc_exit_code

    def stop(self):
        self.__logger.info("Stopping SCRunner")
        """
        Stops the running sclang process and UDP relay.
        """

        if self.__udp_sender:
            self.__udp_sender.close()

        if self.__udp_receiver:
            self.__udp_receiver.close()

        if self.__stdin_thread:
            self.__stdin_thread.join(timeout=5)
            self.__stdin_thread.close()

        self.__stop_subprocess()

    async def __start_communication_to_sc(self):
        transport, _ = await asyncio.get_running_loop().create_datagram_endpoint(
            asyncio.DatagramProtocol,
            remote_addr=(LOCALHOST, self.send_port),
        )
        self.__udp_sender = UDPSender(transport, self.__logger, LOCALHOST, self.send_port)

        def on_stdin_received(text):
            self.__udp_sender.send(text)

        self.__stdin_thread = StdinThread(on_stdin_received)
        self.__stdin_thread.start()
        self.__logger.info("UDP Sender running on %s:%d", LOCALHOST, self.send_port)

    async def __start_communication_from_sc(self):
        """
        Starts a UDP server to listen to messages from SC. Passes these
        messages to stdout.
        """
        transport, _ = await asyncio.get_running_loop().create_datagram_endpoint(
            lambda: UDPReceiveToStdoutProtocol(self.__logger.getChild("UDP receive")),
            local_addr=(LOCALHOST, self.receive_port),
        )
        self.__udp_receiver = transport
        self.__logger.info("UDP receiver running on %s:%d", LOCALHOST, self.receive_port)

    def __stop_subprocess(self):
        """
        Terminates the sclang subprocess if running.
        """
        if self.__subprocess and self.__subprocess.returncode is None:
            self.__subprocess.terminate()

    async def __receive_output(self, stream: StreamReader, prefix: str):
        """
        Handles stdout/stderr from the sclang subprocess
        """
        async for line in stream:
            output = line.decode().rstrip()

            if output:
                self.__logger.info(f"{prefix}: {output}")

            if self.ready_message in output:
                self.__logger.info("ready message received")
                asyncio.create_task(self.__start_communication_from_sc())
                asyncio.create_task(self.__start_communication_to_sc())


def create_arg_parser(sc_runner: type[SCRunner]):
    """
    Creates an argument parser for the CLI representing the supplied
    runner.
    """
    parser = argparse.ArgumentParser(
        description="Runs the SuperCollider LSP server and provides stdin/stdout access to it",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog='''
example with extra sclang args (custom langPort and libraryConfig):
  %(prog)s --sclang-path /path/to/sclang -v --log-file /path/to/logfile -- -u 57300 -l custom_sclang_conf.yaml
'''
    )

    print_default = '(default: %(default)s)'

    parser.add_argument(
        "--sclang-path",
        required=not sc_runner.sclang_path,
        default=sc_runner.sclang_path,
        help=print_default,
    )
    parser.add_argument("--send-port", type=int)
    parser.add_argument("--receive-port", type=int)
    parser.add_argument("--ide-name", default=sc_runner.ide_name, help=print_default)
    parser.add_argument("-v", "--verbose", action="store_true")
    parser.add_argument("-l", "--log-file")
    parser.add_argument("extra_sclang_args", nargs="*", help="cli arguments for sclang (see example below)")

    return parser


def main():
    """
    CLI entry point
    """
    logger = logging.getLogger("lsp_runner")

    parser = create_arg_parser(SCRunner)
    args = parser.parse_args()

    if args.log_file:
        formatter = logging.Formatter("%(asctime)s [%(name)s] %(levelname)s: %(message)s")
        handler = logging.FileHandler(args.log_file, mode="w")
        handler.setFormatter(formatter)
        logger.addHandler(handler)
        logger.setLevel(logging.DEBUG if args.verbose else logging.WARNING)
    else:
        logger.setLevel(logging.ERROR)

    if (args.send_port is None) != (args.receive_port is None):
        raise ValueError("Both server and client port must specified (or neither)")

    if args.send_port and args.receive_port:
        receive_port, send_port = args.send_port, args.receive_port
    else:
        receive_port, send_port = _get_free_ports()
        logger.info("Found free ports (receive: %s), (send: %s)", receive_port, send_port)

    sc_runner = SCRunner(
        logger=logger,
        server_log_level="debug" if args.verbose else "warning",
        send_port=send_port,
        receive_port=receive_port,
        sclang_path=args.sclang_path,
        ide_name=args.ide_name,
    )

    def signal_handler(signum, _):
        logger.info("Received termination signal %d", signum)
        sc_runner.stop()

    # Register signal handlers for termination signals
    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)

    # Add O_NONBLOCK to the stdin descriptor flags
    flags = fcntl.fcntl(0, fcntl.F_GETFL)
    fcntl.fcntl(0, fcntl.F_SETFL, flags | os.O_NONBLOCK)

    sys.exit(asyncio.run(sc_runner.start(args.extra_sclang_args)))


if __name__ == "__main__":
    main()
