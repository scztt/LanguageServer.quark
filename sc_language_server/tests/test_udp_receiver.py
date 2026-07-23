import json
import logging
from unittest.mock import Mock, patch

import pytest

from sc_language_server import MAX_UDP_PACKET_SIZE, UDPReceiveToStdoutProtocol


@pytest.fixture
def logger():
    return Mock(spec=logging.Logger)


@pytest.fixture
def protocol(logger):
    return UDPReceiveToStdoutProtocol(logger)


def test_connection_made(protocol, logger):
    transport = Mock()
    protocol.connection_made(transport)
    logger.info.assert_called_once_with("UDP connection made")


def test_error_received(protocol, logger):
    exc = Exception("Test error")
    with patch("sys.stderr.write") as mock_stderr_write:
        protocol.error_received(exc)
        logger.info.assert_called_once_with("Error %s", exc)
        mock_stderr_write.assert_called_once_with("UDP error: Test error\n")


@pytest.mark.parametrize(
    "content_length,content",
    [
        (59, {"command": "initialize", "arguments": {"cliVersion": 1.0}}),
        (
            225,
            {
                "method": "textDocument/completion",
                "params": {
                    "textDocument": {"uri": "file:///path/to/file.py"},
                    "position": {"line": 10, "character": 15},
                    "context": {"triggerKind": 1, "triggerCharacter": "."},
                },
                "jsonrpc": 2.0,
                "id": 1,
            },
        ),
        (
            510,
            {
                "method": "workspace/symbol",
                "params": {
                    "query": "myFunction",
                    "symbols": [
                        {
                            "name": "myFunction",
                            "kind": 12,
                            "location": {
                                "uri": "file:///path/to/file1.py",
                                "range": {
                                    "start": {"line": 5, "character": 4},
                                    "end": {"line": 5, "character": 14},
                                },
                            },
                            "containerName": "MyClass",
                        },
                        {
                            "name": "myFunction",
                            "kind": 12,
                            "location": {
                                "uri": "file:///path/to/file2.py",
                                "range": {
                                    "start": {"line": 15, "character": 4},
                                    "end": {"line": 15, "character": 14},
                                },
                            },
                            "containerName": "AnotherClass",
                        },
                    ],
                },
                "jsonrpc": "2.0",
                "id": 2,
            },
        ),
    ],
)
def test_udp_buffer_and_stdout_messages(protocol, content_length, content):
    """Tests that the messages received over UDP are buffered correctly and sent to stdout in their reconstructed form"""
    address = ("127.0.0.1", 12345)
    json_content = json.dumps(content)
    full_message = f"Content-Length: {content_length}\r\n\r\n{json_content}"
    byte_string = full_message.encode("utf-8")

    with patch("sys.stdout.write") as mock_stdout_write, patch("sys.stdout.flush") as mock_stdout_flush:
        # Send the message over UDP in chunks
        for i in range(0, len(byte_string), MAX_UDP_PACKET_SIZE):
            chunk = byte_string[i : i + MAX_UDP_PACKET_SIZE]
            protocol.datagram_received(chunk, address)

    # Ensure only the full message was sent over stdout
    mock_stdout_write.assert_called_once_with(full_message)
    mock_stdout_flush.assert_called_once()
