from unittest.mock import Mock, call

import pytest

from sc_language_server import MAX_UDP_PACKET_SIZE, UDPSender

LOCALHOST = "127.0.0.1"
PORT = 8888


@pytest.fixture
def mock_transport():
    return Mock()


@pytest.fixture
def mock_logger():
    return Mock()


@pytest.fixture
def udp_sender(mock_transport, mock_logger):
    return UDPSender(mock_transport, mock_logger, LOCALHOST, PORT)


def test_init(udp_sender):
    assert udp_sender.remote_addr == LOCALHOST
    assert udp_sender.remote_port == PORT
    assert not udp_sender._closed


def test_send_single_chunk(udp_sender, mock_transport):
    msg = "Hello, World!"
    udp_sender.send(msg)
    mock_transport.sendto.assert_called_once_with(b"Hello, World!", (LOCALHOST, PORT))


def test_send_multiple_chunks(udp_sender, mock_transport):
    msg = "A" * (MAX_UDP_PACKET_SIZE + 10)
    udp_sender.send(msg)
    expected_calls = [call(b"A" * MAX_UDP_PACKET_SIZE, (LOCALHOST, PORT)), call(b"A" * 10, (LOCALHOST, PORT))]
    mock_transport.sendto.assert_has_calls(expected_calls)


def test_send_closed(udp_sender, mock_logger):
    udp_sender.close()
    udp_sender.send("Test message")
    mock_logger.warning.assert_called_once_with("Attempted to send data on a closed UDPSender")


def test_send_exception(udp_sender, mock_transport, mock_logger):
    mock_transport.sendto.side_effect = Exception("Test exception")
    udp_sender.send("Test message")
    mock_logger.error.assert_called_once_with("Error sending chunk: Test exception")


def test_close(udp_sender, mock_transport, mock_logger):
    udp_sender.close()
    assert udp_sender._closed
    mock_transport.close.assert_called_once()
    mock_logger.info.assert_called_once_with("UDPSender closed")


def test_close_idempotent(udp_sender, mock_transport, mock_logger):
    udp_sender.close()
    udp_sender.close()
    mock_transport.close.assert_called_once()
    mock_logger.info.assert_called_once_with("UDPSender closed")
