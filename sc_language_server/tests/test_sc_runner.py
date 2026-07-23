import os
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from sc_language_server import SCRunner

# Mock with the current test file since SCRunner will complain if the sclang_path does not exist!
current_file_path = os.path.abspath(__file__)


@pytest.fixture
def mock_logger():
    return MagicMock()


@pytest.fixture
def sc_runner(mock_logger):
    return SCRunner(
        logger=mock_logger,
        server_log_level="warning",
        send_port=57120,
        receive_port=57121,
        sclang_path=current_file_path,
        ide_name="vscode",
    )


@pytest.mark.asyncio
async def test_SCRunner_initialization(sc_runner):
    assert sc_runner.server_log_level == "warning"
    assert sc_runner.send_port == 57120
    assert sc_runner.receive_port == 57121
    assert sc_runner.sclang_path == current_file_path
    assert sc_runner.ide_name == "vscode"


@pytest.mark.asyncio
async def test_SCRunner_start(sc_runner):
    with patch("asyncio.create_subprocess_exec", new_callable=AsyncMock) as mock_subprocess:
        mock_subprocess.return_value = AsyncMock()
        mock_subprocess.return_value.stdout = AsyncMock()
        mock_subprocess.return_value.stderr = AsyncMock()
        mock_subprocess.return_value.wait = AsyncMock(return_value=0)

        with patch.object(
            sc_runner, "_SCRunner__receive_output", new_callable=AsyncMock
        ) as mock_receive_output:
            exit_code = await sc_runner.start()

            assert exit_code == 0
            mock_subprocess.assert_called_once()
            assert mock_receive_output.call_count == 2


@pytest.mark.asyncio
async def test_SCRunner_stop(sc_runner):
    sc_runner._SCRunner__udp_sender = MagicMock()
    sc_runner._SCRunner__udp_receiver = MagicMock()
    sc_runner._SCRunner__stdin_thread = MagicMock()

    mock_subprocess = MagicMock()
    mock_subprocess.returncode = None

    sc_runner._SCRunner__subprocess = mock_subprocess

    sc_runner.stop()

    sc_runner._SCRunner__udp_sender.close.assert_called_once()
    sc_runner._SCRunner__udp_receiver.close.assert_called_once()
    sc_runner._SCRunner__stdin_thread.join.assert_called_once()
    mock_subprocess.terminate.assert_called_once()


@pytest.mark.asyncio
async def test_SCRunner_start_communication_to_sc(sc_runner):
    with patch("asyncio.get_running_loop") as mock_loop:
        mock_transport = MagicMock()
        mock_loop.return_value.create_datagram_endpoint = AsyncMock(return_value=(mock_transport, None))

        with patch("sc_language_server.UDPSender") as mock_udp_sender, patch(
            "sc_language_server.StdinThread"
        ) as mock_stdin_thread:
            await sc_runner._SCRunner__start_communication_to_sc()

            mock_loop.return_value.create_datagram_endpoint.assert_called_once()
            mock_udp_sender.assert_called_once()
            mock_stdin_thread.assert_called_once()
            mock_stdin_thread.return_value.start.assert_called_once()


@pytest.mark.asyncio
async def test_SCRunner_start_communication_from_sc(sc_runner):
    with patch("asyncio.get_running_loop") as mock_loop:
        mock_transport = MagicMock()
        mock_loop.return_value.create_datagram_endpoint = AsyncMock(return_value=(mock_transport, None))

        await sc_runner._SCRunner__start_communication_from_sc()

        mock_loop.return_value.create_datagram_endpoint.assert_called_once()
        assert sc_runner._SCRunner__udp_receiver == mock_transport


@pytest.mark.asyncio
async def test_SCRunner_receive_output_ready(sc_runner):
    mock_stream = AsyncMock()
    mock_stream.__aiter__.return_value = [b"test output\n", b"***LSP READY***\n"]

    with patch.object(
        sc_runner, "_SCRunner__start_communication_from_sc", new_callable=AsyncMock
    ) as mock_start_from_sc, patch.object(
        sc_runner, "_SCRunner__start_communication_to_sc", new_callable=AsyncMock
    ) as mock_start_to_sc:
        await sc_runner._SCRunner__receive_output(mock_stream, "PREFIX_")

        mock_start_from_sc.assert_called_once()
        mock_start_to_sc.assert_called_once()


@pytest.mark.asyncio
async def test_SCRunner_receive_output_not_ready(sc_runner):
    mock_stream = AsyncMock()
    mock_stream.__aiter__.return_value = [b"test output\n", b"***LOGGED INFO***\n"]

    with patch.object(
        sc_runner, "_SCRunner__start_communication_from_sc", new_callable=AsyncMock
    ) as mock_start_from_sc, patch.object(
        sc_runner, "_SCRunner__start_communication_to_sc", new_callable=AsyncMock
    ) as mock_start_to_sc:
        await sc_runner._SCRunner__receive_output(mock_stream, "PREFIX_")

        mock_start_from_sc.assert_not_called()
        mock_start_to_sc.assert_not_called()
