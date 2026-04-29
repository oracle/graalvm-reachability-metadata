# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import json
import os
import queue
import subprocess
import threading
import time
from dataclasses import dataclass, field
from typing import Any, Callable


@dataclass(frozen=True)
class PromptResult:
    """Result returned from a Pi RPC prompt exchange."""

    text: str
    session_file: str
    session_stats: dict[str, Any]
    rpc_transcript: list[dict[str, Any]] = field(default_factory=list)


class PiRpcError(RuntimeError):
    """RPC failure that preserves partial diagnostics for logging."""

    def __init__(
            self,
            message: str,
            rpc_transcript: list[dict[str, Any]] | None = None,
            stderr_text: str = "",
    ):
        super().__init__(message)
        self.rpc_transcript = list(rpc_transcript or [])
        self.stderr_text = stderr_text


class _LineReader(threading.Thread):
    """Collect a text stream into a queue so callers can wait with timeouts."""

    def __init__(self, stream):
        super().__init__(daemon=True)
        self._stream = stream
        self._queue: queue.Queue[str | None] = queue.Queue()

    def run(self) -> None:
        for line in self._stream:
            self._queue.put(line)
        self._queue.put(None)

    def read_line(self, timeout: float) -> str | None:
        try:
            return self._queue.get(timeout=timeout)
        except queue.Empty as exc:
            raise TimeoutError("Timed out while waiting for Pi RPC output.") from exc


class _StderrCollector(threading.Thread):
    """Collect stderr text without blocking the subprocess."""

    def __init__(self, stream):
        super().__init__(daemon=True)
        self._stream = stream
        self._chunks: list[str] = []

    def run(self) -> None:
        for line in self._stream:
            self._chunks.append(line)

    def get_output(self) -> str:
        return "".join(self._chunks)


class PiRpcClient:
    """Thin subprocess wrapper around `pi --mode rpc`."""

    def __init__(
            self,
            pi_command: str = "pi",
            session_dir: str | None = None,
            provider: str | None = None,
            model: str | None = None,
            working_dir: str | None = None,
            timeout: int = 720,
    ):
        self._pi_command = pi_command
        self._session_dir = os.path.abspath(session_dir) if session_dir else None
        self._provider = provider
        self._model = model
        self._working_dir = os.path.abspath(working_dir) if working_dir else None
        self._timeout = timeout

    def run_prompt(
            self,
            prompt: str,
            session: str | None = None,
            progress_callback: Callable[[str], None] | None = None,
    ) -> PromptResult:
        """Run a prompt in a new or continued Pi session."""
        command_flags: list[str] = []
        if session:
            command_flags.extend(["--session", session])
        return self._run(command_flags, prompt, progress_callback=progress_callback)

    def fork(
            self,
            session: str,
            prompt: str,
            progress_callback: Callable[[str], None] | None = None,
    ) -> PromptResult:
        """Fork an existing Pi session and run a prompt in the child session."""
        return self._run(["--fork", session], prompt, progress_callback=progress_callback)

    def _run(
            self,
            command_flags: list[str],
            prompt: str,
            progress_callback: Callable[[str], None] | None = None,
    ) -> PromptResult:
        process = None
        stdout_reader = None
        stderr_collector = None
        deadline = self._next_deadline()
        text_chunks: list[str] = []
        rpc_transcript: list[dict[str, Any]] = []
        progress_event_count = 0

        try:
            process, stdout_reader, stderr_collector = self._start_process(command_flags)
            self._send_command(
                process,
                {
                    "type": "prompt",
                    "message": prompt,
                },
                rpc_transcript,
            )

            saw_prompt_response = False
            while True:
                payload = self._read_payload(
                    stdout_reader,
                    process,
                    deadline,
                    "prompt",
                    stderr_collector,
                    rpc_transcript,
                )
                deadline = self._next_deadline()
                payload_type = payload.get("type")
                progress_message = self._progress_message(payload)
                if progress_callback is not None and progress_message is not None:
                    progress_event_count += 1
                    progress_callback(f"events {progress_event_count} | {progress_message}")

                # The documented Pi RPC protocol keys responses by `command`.
                if payload_type == "response" and payload.get("command") == "prompt":
                    saw_prompt_response = True
                    if payload.get("success") is False:
                        raise PiRpcError(
                            self._format_response_error(payload, stderr_collector),
                            rpc_transcript=rpc_transcript,
                            stderr_text=self._stderr_text(stderr_collector),
                        )
                    continue

                if payload_type == "extension_ui_request":
                    raise PiRpcError(
                        "Pi RPC emitted an unexpected extension UI request in headless mode.",
                        rpc_transcript=rpc_transcript,
                        stderr_text=self._stderr_text(stderr_collector),
                    )

                if payload_type == "message_update":
                    delta = payload.get("assistantMessageEvent") or {}
                    if delta.get("type") == "text_delta":
                        text_chunks.append(str(delta.get("delta") or ""))
                    continue

                if payload_type == "agent_end":
                    if not saw_prompt_response:
                        raise PiRpcError(
                            "Pi RPC prompt ended without a prompt response.",
                            rpc_transcript=rpc_transcript,
                            stderr_text=self._stderr_text(stderr_collector),
                        )
                    break

            state = self._request(
                process,
                stdout_reader,
                deadline,
                {"type": "get_state"},
                stderr_collector,
                rpc_transcript,
            )
            stats = self._request(
                process,
                stdout_reader,
                deadline,
                {"type": "get_session_stats"},
                stderr_collector,
                rpc_transcript,
            )

            state_data = state.get("data") or {}
            stats_data = stats.get("data") or {}
            session_file = state_data.get("sessionFile")
            if not session_file:
                raise PiRpcError(
                    "Pi RPC `get_state` returned no sessionFile.",
                    rpc_transcript=rpc_transcript,
                    stderr_text=self._stderr_text(stderr_collector),
                )

            return PromptResult(
                text="".join(text_chunks),
                session_file=str(session_file),
                session_stats=stats_data,
                rpc_transcript=rpc_transcript,
            )
        finally:
            self._cleanup_process(process)
            if stderr_collector:
                stderr_collector.join(timeout=1)

    def _start_process(self, command_flags: list[str]):
        cmd = [self._pi_command, "--mode", "rpc"]
        if self._session_dir:
            cmd.extend(["--session-dir", self._session_dir])
        if self._provider:
            cmd.extend(["--provider", self._provider])
        if self._model:
            cmd.extend(["--model", self._model])
        cmd.extend(command_flags)

        try:
            process = subprocess.Popen(
                cmd,
                cwd=self._working_dir,
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                bufsize=1,
            )
        except OSError as exc:
            raise RuntimeError("Failed to start Pi RPC subprocess.") from exc

        if process.stdin is None or process.stdout is None or process.stderr is None:
            self._cleanup_process(process)
            raise RuntimeError("Failed to open Pi RPC stdio streams.")

        stdout_reader = _LineReader(process.stdout)
        stderr_collector = _StderrCollector(process.stderr)
        stdout_reader.start()
        stderr_collector.start()
        return process, stdout_reader, stderr_collector

    @staticmethod
    def _send_command(
            process: subprocess.Popen,
            payload: dict[str, Any],
            rpc_transcript: list[dict[str, Any]] | None = None,
    ) -> None:
        if process.stdin is None:
            raise RuntimeError("Pi RPC stdin is not available.")
        if rpc_transcript is not None:
            rpc_transcript.append({"direction": "send", "payload": payload})
        process.stdin.write(json.dumps(payload) + "\n")
        process.stdin.flush()

    def _request(
            self,
            process: subprocess.Popen,
            stdout_reader: _LineReader,
            deadline: float,
            payload: dict[str, Any],
            stderr_collector: _StderrCollector | None,
            rpc_transcript: list[dict[str, Any]] | None = None,
    ) -> dict[str, Any]:
        command = str(payload["type"])
        self._send_command(process, payload, rpc_transcript)

        while True:
            response = self._read_payload(
                stdout_reader,
                process,
                deadline,
                command,
                stderr_collector,
                rpc_transcript,
            )
            deadline = self._next_deadline()
            if response.get("type") != "response" or response.get("command") != command:
                continue
            if response.get("success") is False:
                raise PiRpcError(
                    self._format_response_error(response, stderr_collector),
                    rpc_transcript=rpc_transcript,
                    stderr_text=self._stderr_text(stderr_collector),
                )
            return response

    @staticmethod
    def _format_response_error(payload: dict[str, Any], stderr_collector: _StderrCollector | None) -> str:
        command = payload.get("command") or payload.get("type") or "unknown"
        error = str(payload.get("error") or f"Pi RPC `{command}` failed.")
        stderr_text = PiRpcClient._stderr_text(stderr_collector)
        if stderr_text:
            return f"Pi RPC `{command}` failed: {error}\n{stderr_text}"
        return f"Pi RPC `{command}` failed: {error}"

    @staticmethod
    def _stderr_text(stderr_collector: _StderrCollector | None) -> str:
        return stderr_collector.get_output().strip() if stderr_collector else ""

    @staticmethod
    def _cleanup_process(process: subprocess.Popen | None) -> None:
        if process is None:
            return

        if process.stdin is not None and not process.stdin.closed:
            process.stdin.close()

        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)

        if process.stdout is not None and not process.stdout.closed:
            process.stdout.close()
        if process.stderr is not None and not process.stderr.closed:
            process.stderr.close()

    def _next_deadline(self) -> float:
        return time.monotonic() + self._timeout

    @staticmethod
    def _remaining_time(deadline: float, command: str) -> float:
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise RuntimeError(f"Pi RPC timed out during `{command}`.")
        return remaining

    def _read_payload(
            self,
            stdout_reader: _LineReader,
            process: subprocess.Popen,
            deadline: float,
            command: str,
            stderr_collector: _StderrCollector | None,
            rpc_transcript: list[dict[str, Any]] | None = None,
    ) -> dict[str, Any]:
        while True:
            try:
                line = stdout_reader.read_line(self._remaining_time(deadline, command))
            except TimeoutError as exc:
                stderr_text = self._stderr_text(stderr_collector)
                last_event = self._describe_last_event(rpc_transcript)
                message = (
                    f"Pi RPC timed out during `{command}` after {self._timeout}s without output."
                )
                if last_event:
                    message = f"{message}\nLast RPC event: {last_event}"
                if stderr_text:
                    message = f"{message}\n{stderr_text}"
                raise PiRpcError(
                    message,
                    rpc_transcript=rpc_transcript,
                    stderr_text=stderr_text,
                ) from exc
            if line is None:
                exit_code = process.poll()
                stderr_text = self._stderr_text(stderr_collector)
                message = f"Pi RPC exited before completing `{command}` (exit code {exit_code})."
                if stderr_text:
                    message = f"{message}\n{stderr_text}"
                raise PiRpcError(
                    message,
                    rpc_transcript=rpc_transcript,
                    stderr_text=stderr_text,
                )

            stripped = line.rstrip("\n")
            if not stripped:
                continue

            try:
                payload = json.loads(stripped)
            except json.JSONDecodeError as exc:
                raise PiRpcError(
                    "Pi RPC emitted invalid JSON.",
                    rpc_transcript=rpc_transcript,
                    stderr_text=self._stderr_text(stderr_collector),
                ) from exc
            if rpc_transcript is not None:
                rpc_transcript.append({"direction": "recv", "payload": payload})
            return payload

    @staticmethod
    def _describe_last_event(rpc_transcript: list[dict[str, Any]] | None) -> str | None:
        if not rpc_transcript:
            return None
        last_entry = rpc_transcript[-1]
        direction = last_entry.get("direction", "?")
        payload = last_entry.get("payload") or {}
        payload_type = payload.get("type", "unknown")
        if direction == "send":
            return f"sent {payload_type}"
        if payload_type == "response":
            command = payload.get("command", "unknown")
            success = payload.get("success")
            status = "success" if success else "failure"
            return f"received response {command} {status}"
        if payload_type == "message_update":
            message_event = payload.get("assistantMessageEvent") or {}
            return f"received message_update {message_event.get('type', 'unknown')}"
        return f"received {payload_type}"

    @staticmethod
    def _progress_message(payload: dict[str, Any]) -> str | None:
        payload_type = payload.get("type")
        if payload_type == "message_update":
            message_event = payload.get("assistantMessageEvent") or {}
            event_type = message_event.get("type", "unknown")
            if event_type in {"text_delta", "thinking_delta", "toolcall_delta"}:
                delta = str(message_event.get("delta") or "").strip()
                if delta:
                    return f"{event_type}: {delta.splitlines()[-1].strip()}"
                return event_type
            if event_type == "toolcall_start":
                return "toolcall_start"
            if event_type == "toolcall_end":
                tool_call = message_event.get("toolCall") or {}
                return "toolcall_end: {name}".format(name=tool_call.get("name", "unknown"))
            return f"assistant-event: {event_type}"

        if payload_type == "tool_execution_start":
            return "tool_execution_start: {name}".format(name=payload.get("toolName", "unknown"))

        if payload_type == "tool_execution_update":
            partial_result = payload.get("partialResult") or {}
            partial_text = PiRpcClient._extract_text_content(partial_result.get("content") or [])
            if partial_text:
                return "tool_execution_update: {last_line}".format(
                    last_line=partial_text.splitlines()[-1].strip(),
                )
            return "tool_execution_update: {name}".format(name=payload.get("toolName", "unknown"))

        if payload_type == "tool_execution_end":
            status = "error" if payload.get("isError") else "success"
            return "tool_execution_end: {name} ({status})".format(
                name=payload.get("toolName", "unknown"),
                status=status,
            )

        if payload_type == "agent_end":
            return "agent_end"

        return None

    @staticmethod
    def _extract_text_content(content_blocks: list[dict]) -> str:
        parts: list[str] = []
        for block in content_blocks:
            if block.get("type") == "text":
                parts.append(str(block.get("text") or ""))
        return "\n".join(part for part in parts if part)
