# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import json
import os
import subprocess
from datetime import datetime, timezone

from ai_workflows.agents.agent import Agent
from ai_workflows.agents.pi_rpc_client import PiRpcClient, PiRpcError, PromptResult
from utility_scripts.pi_logs import build_pi_log_path


@Agent.register("pi")
class PiAgent(Agent):
    """Agent adapter that drives Pi through its RPC mode."""

    def __init__(
            self,
            model_name: str,
            working_dir: str,
            timeout: int = 720,
            provider: str | None = None,
            pi_command: str = "pi",
            session_dir: str | None = None,
            library: str | None = None,
            task_type: str = "session",
            **_,
    ):
        self._model_name = model_name
        self._provider = provider
        self._working_dir = os.path.abspath(working_dir)
        self._timeout = timeout
        self._session_path: str | None = None
        self._total_tokens_sent = 0
        self._cached_input_tokens_used = 0
        self._total_tokens_received = 0
        self._prev_input = 0
        self._prev_output = 0
        self._prev_cache = 0
        self._library = library
        self._task_type = task_type
        self._session_log_path: str | None = None
        self._rpc_client = PiRpcClient(
            pi_command=pi_command,
            session_dir=session_dir,
            provider=self._provider,
            model=self._model_name,
            working_dir=self._working_dir,
            timeout=self._timeout,
        )

    @property
    def total_tokens_sent(self) -> int:
        return self._total_tokens_sent

    @property
    def total_tokens_received(self) -> int:
        return self._total_tokens_received

    @property
    def cached_input_tokens_used(self) -> int:
        return self._cached_input_tokens_used

    def graphify(self, source_dirs: list[str]) -> str:
        """Send /skill:graphify to the Pi session to build a merged knowledge graph context."""
        from utility_scripts.stage_logger import log_stage
        from utility_scripts.task_logs import display_log_path
        if not source_dirs:
            return ""
        log_stage("graphify", f"Initializing knowledge graph context for {len(source_dirs)} source(s)")
        result = self.send_prompt(f"/skill:graphify {source_dirs[0]}")
        for extra_dir in source_dirs[1:]:
            log_stage("graphify", f"Merging graph from {display_log_path(extra_dir)}")
            result = self.send_prompt(f"/skill:graphify {extra_dir} --update")
        log_stage("graphify", "Knowledge graph context initialized")
        return result

    def send_prompt(self, prompt: str) -> str:
        original_session_path = self._session_path
        try:
            result = self._rpc_client.run_prompt(
                prompt,
                session=self._session_path,
                progress_callback=lambda detail: self._print_live_status("Pi", detail),
            )
        except PiRpcError as exc:
            self._ensure_failure_log_path()
            self._print_session_log_once("Pi", self._session_log_path)
            self._write_failure_log(original_session_path, prompt, exc)
            raise
        finally:
            self._clear_live_status()
        self._session_path = result.session_file
        if self._session_log_path is None or self._session_path != original_session_path:
            self._set_session_log_path(self._build_generation_log_path())
        self._update_token_counters(result.session_stats)
        self._print_session_log_once("Pi", self._session_log_path)
        self._write_turn_log(self._session_path or original_session_path, prompt, result)
        return result.text

    def fork(self, prompt: str) -> "PiAgent":
        if self._session_path is None:
            raise RuntimeError("Cannot fork a Pi agent without a session.")

        if self._session_log_path is None:
            self._set_session_log_path(self._build_generation_log_path())
        try:
            result = self._rpc_client.fork(
                self._session_path,
                prompt,
                progress_callback=lambda detail: self._print_live_status("Pi", detail),
            )
        except PiRpcError as exc:
            self._print_session_log_once("Pi", self._session_log_path)
            self._write_failure_log(self._session_path, prompt, exc)
            raise
        finally:
            self._clear_live_status()
        child = self._clone()
        child._session_path = result.session_file
        child._set_session_log_path(child._build_generation_log_path())
        child._total_tokens_sent = self._total_tokens_sent
        child._cached_input_tokens_used = self._cached_input_tokens_used
        child._total_tokens_received = self._total_tokens_received
        child._prev_input = self._prev_input
        child._prev_output = self._prev_output
        child._prev_cache = self._prev_cache
        child._apply_fork_token_delta(result.session_stats)
        child._print_session_log_once("Pi", child._session_log_path)
        child._write_turn_log(child._session_path, prompt, result)
        return child

    def compact_fork(self, prompt: str) -> "PiAgent":
        # Pi RPC currently exposes no documented compaction-aware fork operation.
        return self.fork(prompt)

    def clear_context(self) -> None:
        self._session_path = None
        self._prev_input = 0
        self._prev_output = 0
        self._prev_cache = 0

    def run_test_command(self, test_cmd: str) -> str:
        result = subprocess.run(
            test_cmd,
            cwd=self._working_dir,
            shell=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            check=False,
        )
        return result.stdout

    def _update_token_counters(self, session_stats: dict) -> None:
        tokens = self._extract_token_totals(session_stats)
        delta_input = max(tokens["input"] - self._prev_input, 0)
        delta_output = max(tokens["output"] - self._prev_output, 0)
        delta_cache = max(tokens["cacheRead"] - self._prev_cache, 0)

        self._total_tokens_sent += delta_input
        self._total_tokens_received += delta_output
        self._cached_input_tokens_used += delta_cache

        self._prev_input = tokens["input"]
        self._prev_output = tokens["output"]
        self._prev_cache = tokens["cacheRead"]

    def _apply_fork_token_delta(self, session_stats: dict) -> None:
        tokens = self._extract_token_totals(session_stats)
        self._total_tokens_sent += max(tokens["input"] - self._prev_input, 0)
        self._total_tokens_received += max(tokens["output"] - self._prev_output, 0)
        self._cached_input_tokens_used += max(tokens["cacheRead"] - self._prev_cache, 0)

        self._prev_input = tokens["input"]
        self._prev_output = tokens["output"]
        self._prev_cache = tokens["cacheRead"]

    @staticmethod
    def _extract_token_totals(session_stats: dict) -> dict[str, int]:
        tokens = session_stats.get("tokens") or {}
        return {
            "input": int(tokens.get("input", 0) or 0),
            "output": int(tokens.get("output", 0) or 0),
            "cacheRead": int(tokens.get("cacheRead", 0) or 0),
        }

    def _clone(self) -> "PiAgent":
        child = PiAgent(
            model_name=self._model_name,
            working_dir=self._working_dir,
            timeout=self._timeout,
            provider=self._provider,
            library=self._library,
            task_type=self._task_type,
        )
        child._rpc_client = self._rpc_client
        return child

    @staticmethod
    def _display_path(path: str | None, base_path: str | None = None) -> str:
        if not path:
            return "unknown"
        if not os.path.isabs(path):
            return path
        base = base_path or os.getcwd()
        return os.path.relpath(path, base)

    @staticmethod
    def _render_rpc_transcript(rpc_transcript: list[dict]) -> list[str]:
        lines: list[str] = []
        pending_prefix: str | None = None
        pending_chunks: list[str] = []

        def flush_pending() -> None:
            nonlocal pending_prefix, pending_chunks
            if pending_prefix is None:
                return
            rendered_text = "".join(pending_chunks)
            if rendered_text:
                lines.extend(PiAgent._indent_block(rendered_text, prefix=pending_prefix))
            pending_prefix = None
            pending_chunks = []

        for entry in rpc_transcript:
            direction = entry.get("direction", "?")
            payload = entry.get("payload") or {}
            payload_type = payload.get("type", "unknown")

            if direction == "send" and payload_type == "prompt":
                flush_pending()
                lines.append("[send] prompt")
                lines.extend(PiAgent._indent_block(str(payload.get("message") or ""), prefix="  "))
                continue

            if direction == "send" and payload_type in {"get_state", "get_session_stats"}:
                flush_pending()
                lines.append(f"[send] {payload_type}")
                continue

            if direction == "recv" and payload_type == "response":
                flush_pending()
                command = payload.get("command", "unknown")
                success = payload.get("success")
                status = "success" if success else "failure"
                lines.append(f"[recv] {command} {status}")
                if command == "get_state" and success:
                    data = payload.get("data") or {}
                    if data.get("sessionFile"):
                        lines.append(f"  sessionFile: {PiAgent._display_path(data.get('sessionFile'))}")
                    if data.get("sessionId"):
                        lines.append(f"  sessionId: {data.get('sessionId')}")
                elif command == "get_session_stats" and success:
                    tokens = (payload.get("data") or {}).get("tokens") or {}
                    lines.append(
                        "  tokens: input={input} output={output} cacheRead={cache_read} "
                        "cacheWrite={cache_write} total={total}".format(
                            input=tokens.get("input", 0),
                            output=tokens.get("output", 0),
                            cache_read=tokens.get("cacheRead", 0),
                            cache_write=tokens.get("cacheWrite", 0),
                            total=tokens.get("total", 0),
                        )
                    )
                elif not success and payload.get("error"):
                    lines.append(f"  error: {payload.get('error')}")
                continue

            if direction == "recv" and payload_type == "message_update":
                message_event = payload.get("assistantMessageEvent") or {}
                event_type = message_event.get("type", "unknown")
                if event_type == "text_delta":
                    prefix = "[assistant] "
                    if pending_prefix != prefix:
                        flush_pending()
                        pending_prefix = prefix
                    pending_chunks.append(str(message_event.get("delta") or ""))
                elif event_type == "thinking_delta":
                    prefix = "[thinking] "
                    if pending_prefix != prefix:
                        flush_pending()
                        pending_prefix = prefix
                    pending_chunks.append(str(message_event.get("delta") or ""))
                elif event_type == "toolcall_start":
                    flush_pending()
                    lines.append(f"[tool-call] start id={message_event.get('toolCallId', 'unknown')}")
                elif event_type == "toolcall_delta":
                    prefix = "[tool-call] delta "
                    if pending_prefix != prefix:
                        flush_pending()
                        pending_prefix = prefix
                    pending_chunks.append(str(message_event.get("delta") or ""))
                elif event_type == "toolcall_end":
                    flush_pending()
                    tool_call = message_event.get("toolCall") or {}
                    lines.append(
                        "[tool-call] end id={tool_call_id} name={name} args={args}".format(
                            tool_call_id=message_event.get("toolCallId", tool_call.get("id", "unknown")),
                            name=tool_call.get("name", "unknown"),
                            args=json.dumps(tool_call.get("arguments") or {}, ensure_ascii=False),
                        )
                    )
                elif event_type in {"text_start", "text_end", "thinking_start", "thinking_end", "start", "done", "error"}:
                    flush_pending()
                    lines.append(f"[assistant-event] {event_type}")
                else:
                    flush_pending()
                    lines.append(f"[message_update] {json.dumps(message_event, ensure_ascii=False)}")
                continue

            if direction == "recv" and payload_type == "tool_execution_start":
                flush_pending()
                lines.append(
                    "[tool-exec] start id={tool_call_id} name={name} args={args}".format(
                        tool_call_id=payload.get("toolCallId", "unknown"),
                        name=payload.get("toolName", "unknown"),
                        args=json.dumps(payload.get("args") or {}, ensure_ascii=False),
                    )
                )
                continue

            if direction == "recv" and payload_type == "tool_execution_update":
                flush_pending()
                lines.append(
                    "[tool-exec] update id={tool_call_id} name={name}".format(
                        tool_call_id=payload.get("toolCallId", "unknown"),
                        name=payload.get("toolName", "unknown"),
                    )
                )
                partial_result = payload.get("partialResult") or {}
                partial_text = PiAgent._extract_text_content(partial_result.get("content") or [])
                if partial_text:
                    lines.extend(PiAgent._indent_block(partial_text, prefix="  "))
                continue

            if direction == "recv" and payload_type == "tool_execution_end":
                flush_pending()
                status = "error" if payload.get("isError") else "success"
                lines.append(
                    "[tool-exec] end id={tool_call_id} name={name} status={status}".format(
                        tool_call_id=payload.get("toolCallId", "unknown"),
                        name=payload.get("toolName", "unknown"),
                        status=status,
                    )
                )
                result = payload.get("result") or {}
                result_text = PiAgent._extract_text_content(result.get("content") or [])
                if result_text:
                    lines.extend(PiAgent._indent_block(result_text, prefix="  "))
                continue

            if direction == "recv" and payload_type == "agent_end":
                flush_pending()
                lines.append("[recv] agent_end")
                continue

            flush_pending()
            lines.append(f"[{direction}] {json.dumps(payload, ensure_ascii=False)}")

        flush_pending()
        return lines

    @staticmethod
    def _extract_text_content(content_blocks: list[dict]) -> str:
        parts: list[str] = []
        for block in content_blocks:
            if block.get("type") == "text":
                parts.append(str(block.get("text") or ""))
        return "\n".join(part for part in parts if part)

    @staticmethod
    def _indent_block(text: str, prefix: str) -> list[str]:
        if not text:
            return [prefix.rstrip()]
        return [f"{prefix}{line}" for line in text.splitlines()]

    def _build_generation_log_path(self) -> str:
        """Build a Pi generation log path for the current library."""
        return build_pi_log_path("generation", self._library, task_type=self._task_type)

    def _set_session_log_path(self, log_path: str) -> None:
        """Update the active log path and re-announce it when the target file changes."""
        if self._session_log_path == log_path:
            return
        self._session_log_path = log_path
        self._session_log_announced = False

    def _ensure_failure_log_path(self) -> None:
        """Ensure failures are written to a generation log file."""
        if self._session_log_path is not None:
            return
        self._set_session_log_path(self._build_generation_log_path())

    def _write_turn_log(self, session_path: str | None, prompt: str, result: PromptResult) -> None:
        with open(self._session_log_path, "a", encoding="utf-8") as log_file:
            log_file.write("=" * 80)
            log_file.write("\n")
            log_file.write(f"Timestamp: {datetime.now(timezone.utc).isoformat()}\n")
            log_file.write(f"Session: {self._display_path(session_path, self._working_dir)}\n")
            log_file.write("Event Trace:\n")
            for line in self._render_rpc_transcript(result.rpc_transcript):
                log_file.write(line)
                if not line.endswith("\n"):
                    log_file.write("\n")
            log_file.write("\n")

    def _write_failure_log(self, session_path: str | None, prompt: str, exc: PiRpcError) -> None:
        with open(self._session_log_path, "a", encoding="utf-8") as log_file:
            log_file.write("=" * 80)
            log_file.write("\n")
            log_file.write(f"Timestamp: {datetime.now(timezone.utc).isoformat()}\n")
            log_file.write(f"Session: {self._display_path(session_path, self._working_dir)}\n")
            log_file.write("Prompt:\n")
            for line in self._indent_block(prompt, prefix="  "):
                log_file.write(f"{line}\n")
            log_file.write("Error:\n")
            for line in self._indent_block(str(exc), prefix="  "):
                log_file.write(f"{line}\n")
            if exc.rpc_transcript:
                log_file.write("Event Trace:\n")
                for line in self._render_rpc_transcript(exc.rpc_transcript):
                    log_file.write(line)
                    if not line.endswith("\n"):
                        log_file.write("\n")
            log_file.write("\n")
