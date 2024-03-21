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
import logging
import os
import selectors
import signal
import socket
import sys
from asyncio.streams import StreamReader
from contextlib import closing
from threading import Event, Thread

LOCALHOST = "127.0.0.1"


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
                events = self._selector.select()
                for key, mask in events:
                    callback = key.data
                    callback(key.fileobj, mask)
            except KeyboardInterrupt:
                break

    def __read(self, fileobj, _):
        data = fileobj.read()
        if data:
            self._on_received(data)

    def close(self):
        """
        Stops the threads main run loop.
        """
        self._stop_event.set()


class StdoutBridgeProtocol(asyncio.DatagramProtocol):
    """
    A UDP protocol handler that writes messages to stdout
    """

    def __init__(self, logger: logging.Logger) -> None:
        super().__init__()
        self.__logger = logger

    def connection_made(self, _):
        self.__logger.info("UDP connection made")

    def datagram_received(self, data, addr):
        sys.stdout.write(data.decode())
        sys.stdout.flush()

    def error_received(self, exc):
        self.__logger.info("Error %s", exc)
        sys.stderr.write(f"UDP error: {exc}\n")


class SCRunner:
    """
    A class to manage a sclang suprocess, and connect stdin/out to it
    via UDP.
    """

    ##pylint: disable=too-many-instance-attributes

    defaults = {
        "darwin": {
            "sclang": "/Applications/SuperCollider.app/Contents/MacOS/sclang",
            "config": "~/Library/Application Support/SuperCollider/sclang_conf.yaml",
        }
    }

    sclang_path = ""
    config_path = ""

    ide_name = "vscode"
    server_log_level = "warning"
    receive_port = None
    send_port = None
    ready_message = "***LSP READY***"

    __logger: logging.Logger
    __udp_server = None
    __udp_client = None
    __subprocess = None
    __stdin_thread = None

    def __init__(self, logger: logging.Logger):
        """
        Constructs a new LSPRunner, defaults will be configured for the
        host platform, and can be changed prior to calling start.
        """
        self.__logger = logger
        defaults = self.defaults.get(sys.platform, {})
        self.sclang_path = defaults.get("sclang", "")
        self.config_path = defaults.get("config", "")

    async def start(self) -> int:
        """
        Starts an sclang subprocess, enabling the LSP server.
        Stdin/out are connected to the server via UDP.
        """
        if self.__subprocess:
            self.__stop_subprocess()

        my_env = os.environ.copy()

        if not self.receive_port or not self.send_port:
            self.receive_port, self.send_port = self.__get_free_ports()
            self.__logger.info(
                "Found free ports %s, %s", self.receive_port, self.send_port
            )

        if not os.path.exists(self.sclang_path):
            raise RuntimeError(
                f"The specified sclang path does not exist: {self.sclang_path}"
            )

        additional_vars = {
            "SCLANG_LSP_ENABLE": "1",
            "SCLANG_LSP_LOGLEVEL": self.server_log_level,
            "SCLANG_LSP_CLIENTPORT": str(self.send_port),
            "SCLANG_LSP_SERVERPORT": str(self.receive_port),
        }

        self.__logger.info("SC env vars: %s", repr(additional_vars))

        command = [self.sclang_path, "-i", self.ide_name]
        if self.config_path:
            config = os.path.expanduser(os.path.expandvars(self.config_path))
            if not os.path.exists(config):
                raise RuntimeError(
                    f"The specified config file does not exist: '{config}'"
                )
            command.extend(["-l", config])

        self.__logger.info(f"RUNNER: Launching SC with cmd: {command}")

        self.__subprocess = await asyncio.create_subprocess_exec(
            *command,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
            # stdin must be set to PIPE so stdin flows to the main program
            stdin=asyncio.subprocess.PIPE,
            env={**my_env, **additional_vars},
        )

        # receive stdout and stderr from sclang
        if self.__subprocess.stdout and self.__subprocess.stderr:
            await asyncio.gather(
                self.__receive_output(self.__subprocess.stdout, "SC:STDOUT"),
                self.__receive_output(self.__subprocess.stderr, "SC:STDERR"),
            )

        # Await subprocess termination
        sc_exit_code = await self.__subprocess.wait()
        if self.__stdin_thread:
            self.__stdin_thread.close()
        return sc_exit_code

    def stop(self):
        """
        Stops the running sclang process and UDP relay.
        """
        self.__stop_subprocess()
        if self.__stdin_thread:
            self.__stdin_thread.close()
        if self.__udp_client:
            self.__udp_client.close()
        if self.__udp_server:
            self.__udp_server.close()

    async def __start_communication_to_sc(self):
        """
        Starts communication to sclang
        - Starts a UDP client to write messages to sclang
        - Starts a thread which reads stdin of the main python program
        - Sends data from stdin to the UDP client
        """
        transport, _ = await asyncio.get_running_loop().create_datagram_endpoint(
            asyncio.DatagramProtocol,
            remote_addr=(LOCALHOST, self.send_port),
        )
        self.__udp_client = transport

        def on_stdin_received(text):
            transport.sendto(text.encode(encoding="utf-8"))

        self.__stdin_thread = StdinThread(on_stdin_received)
        self.__stdin_thread.start()

        self.__logger.info("UDP Sender running on %s:%d", LOCALHOST, self.send_port)

    async def __start_communication_from_sc(self):
        """
        Starts a UDP server to listen to messages from SC. Passes these
        messages to stdout.
        """
        transport, _ = await asyncio.get_running_loop().create_datagram_endpoint(
            lambda: StdoutBridgeProtocol(self.__logger.getChild("UDP receive")),
            local_addr=(LOCALHOST, self.receive_port),
        )
        self.__udp_server = transport
        self.__logger.info(
            "UDP receiver running on %s:%d", LOCALHOST, self.receive_port
        )

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

    def __get_free_ports(self) -> tuple[int, int]:
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


def create_arg_parser(sc_runner: SCRunner):
    """
    Creates an argument parser for the CLI representing the supplied
    runner.
    """
    parser = argparse.ArgumentParser(
        prog="sclsp_runner",
        description="Runs the SuperCollider LSP server and provides stdin/stdout access to it",
    )

    parser.add_argument(
        "--sclang-path",
        required=not sc_runner.sclang_path,
        default=sc_runner.sclang_path,
    )
    parser.add_argument(
        "--config-path",
        required=not sc_runner.config_path,
        default=sc_runner.config_path,
    )
    parser.add_argument("--send-port", type=int)
    parser.add_argument("--receive-port", type=int)
    parser.add_argument("--ide-name", default=sc_runner.ide_name)
    parser.add_argument("-v", "--verbose", action="store_true")
    parser.add_argument("-l", "--log-file")

    return parser


def main():
    """
    CLI entry point
    """
    logger = logging.getLogger("lsp_runner")

    sc_runner = SCRunner(logger)

    parser = create_arg_parser(sc_runner)
    args = parser.parse_args()

    if args.log_file:
        formatter = logging.Formatter(
            "%(asctime)s [%(name)s] %(levelname)s: %(message)s"
        )
        handler = logging.FileHandler(args.log_file)
        handler.setFormatter(formatter)
        logger.addHandler(handler)
        logger.setLevel(logging.DEBUG if args.verbose else logging.WARNING)
        sc_runner.server_log_level = "debug" if args.verbose else "warning"
    else:
        logger.setLevel(logging.ERROR)

    sc_runner.sclang_path = args.sclang_path
    sc_runner.config_path = args.config_path
    sc_runner.ide_name = args.ide_name

    if (args.send_port is None) != (args.receive_port is None):
        raise ValueError("Both server and client port must specified (or neither)")

    if args.send_port and args.receive_port:
        sc_runner.receive_port = args.receive_port
        sc_runner.send_port = args.send_port

    def signal_handler(signum, _):
        logger.info("Received termination signal %d", signum)
        sc_runner.stop()

    # Register signal handlers for termination signals
    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)

    # Add O_NONBLOCK to the stdin descriptor flags
    flags = fcntl.fcntl(0, fcntl.F_GETFL)
    fcntl.fcntl(0, fcntl.F_SETFL, flags | os.O_NONBLOCK)

    sys.exit(asyncio.run(sc_runner.start()))


if __name__ == "__main__":
    main()
