# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import json
import os
import subprocess
import tempfile
import time
from ai_workflows.agents.agent import Agent
from ai_workflows.agents.codex_app_server import CodexAppServerClient


@Agent.register("codex")
class CodexAgent(Agent):
    """Stateful Codex adapter that separates thread control from turn execution."""

    SPINNER_INTERVAL_SECONDS = 0.2

    def __init__(
            self,
            model_name: str,
            working_dir: str,
            timeout: int = 600,
            task_type: str = "session",
            library: str | None = None,
            **_,
    ):
        self._model_name = model_name
        self._working_dir = os.path.abspath(working_dir)
        self._timeout = timeout
        self._task_type = task_type
        self._library = library
        self._total_tokens_sent = 0
        self._cached_input_tokens_used = 0
        self._total_tokens_received = 0
        self._thread_id: str | None = None
        self._session_log_path = self._create_session_log_path(
            "codex",
            self._task_type,
            self._library,
        )
        self._session_log_announced = False
        self._control_client = CodexAppServerClient(
            model_name=self._model_name,
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

    @property
    def thread_id(self) -> str | None:
        return self._thread_id

    def send_prompt(self, prompt: str) -> str:
        self._print_session_log_once("Codex", self._session_log_path)
        original_thread_id = self._thread_id
        if self._thread_id is None:
            cmd = [
                "codex", "exec",
                "--dangerously-bypass-approvals-and-sandbox",
                "--json",
                "-c", 'reasoning.effort="medium"',
                "-m", self._model_name,
                prompt,
            ]
        else:
            cmd = [
                "codex", "exec", "resume",
                "--dangerously-bypass-approvals-and-sandbox",
                "--json",
                "-c", 'reasoning.effort="medium"',
                "-m", self._model_name,
                self._thread_id,
                prompt,
            ]
        try:
            returncode, output = self._run_codex_command(cmd)
        except subprocess.TimeoutExpired as exc:
            self._write_turn_log(original_thread_id, prompt, exc.output or "")
            print(f"[Codex] Timed out after {self._timeout}s.", flush=True)
            raise RuntimeError("Codex prompt timed out.") from exc

        self._record_token_usage(prompt, output)
        if self._thread_id is None:
            self._thread_id = self._extract_thread_id(output)
        self._write_turn_log(self._thread_id or original_thread_id, prompt, output)
        if returncode != 0:
            print("[Codex] Failed.", flush=True)
            raise RuntimeError(f"Codex command failed with exit code {returncode}.")
        if self._thread_id is None:
            raise RuntimeError("Codex command completed without a thread id.")
        response = self._extract_last_message(output)
        if not response:
            raise RuntimeError("Codex command completed without an assistant message.")
        print("[Codex] Done.", flush=True)
        return response

    def _run_codex_command(self, cmd: list[str]) -> tuple[int, str]:
        start_time = time.monotonic()
        with tempfile.NamedTemporaryFile(
            mode="w",
            encoding="utf-8",
            suffix=".tmp",
            prefix="codex-turn-",
            dir=os.path.dirname(self._session_log_path),
            delete=False,
        ) as temp_output:
            temp_output_path = temp_output.name

        try:
            with open(temp_output_path, "w", encoding="utf-8") as temp_output:
                process = subprocess.Popen(
                    cmd,
                    cwd=self._working_dir,
                    stdout=temp_output,
                    stderr=subprocess.STDOUT,
                    text=True,
                )
                while True:
                    elapsed_seconds = time.monotonic() - start_time
                    if elapsed_seconds >= self._timeout:
                        process.kill()
                        process.wait(timeout=5)
                        raise subprocess.TimeoutExpired(
                            cmd,
                            self._timeout,
                            output=self._read_output_file(temp_output_path),
                        )

                    self._print_live_status(
                        "Codex",
                        self._summarize_output_progress(temp_output_path, elapsed_seconds),
                    )
                    try:
                        returncode = process.wait(timeout=self.SPINNER_INTERVAL_SECONDS)
                        break
                    except subprocess.TimeoutExpired:
                        continue
        except subprocess.TimeoutExpired as exc:
            output = self._read_output_file(temp_output_path) if os.path.exists(temp_output_path) else ""
            if os.path.exists(temp_output_path):
                os.remove(temp_output_path)
            raise subprocess.TimeoutExpired(cmd, self._timeout, output=output) from exc
        finally:
            self._clear_live_status()

        output = self._read_output_file(temp_output_path)
        os.remove(temp_output_path)
        return returncode or 0, output

    @staticmethod
    def _read_output_file(path: str) -> str:
        with open(path, "r", encoding="utf-8") as temp_output:
            return temp_output.read()

    @staticmethod
    def _summarize_output_progress(path: str, elapsed_seconds: float) -> str:
        if not os.path.exists(path):
            return f"{elapsed_seconds:.1f}s | waiting for log output"

        content = CodexAgent._read_output_file(path)
        lines = [line.strip() for line in content.splitlines() if line.strip()]
        if not lines:
            return f"{elapsed_seconds:.1f}s | log lines 0"

        return (
            "{elapsed:.1f}s | log lines {line_count} | {last_line}".format(
                elapsed=elapsed_seconds,
                line_count=len(lines),
                last_line=lines[-1],
            )
        )

    def _write_turn_log(self, thread_id: str | None, prompt: str, output: str) -> None:
        with open(self._session_log_path, "a", encoding="utf-8") as log_file:
            log_file.write(f"Thread ID:{thread_id or 'unknown'}\n")
            log_file.write("Prompt:\n")
            log_file.write(prompt)
            log_file.write("\n\nConversation:\n")
            log_file.write(output)
            if output and not output.endswith("\n"):
                log_file.write("\n")
            log_file.write("\n")

    def fork(self, prompt: str) -> "CodexAgent":
        if self._thread_id is None:
            raise RuntimeError("Cannot fork a Codex agent without a known thread id.")

        thread = self._control_client.fork_thread(self._thread_id)
        child = self._clone()
        child._thread_id = thread["id"]
        child._total_tokens_sent = self._total_tokens_sent
        child._cached_input_tokens_used = self._cached_input_tokens_used
        child._total_tokens_received = self._total_tokens_received
        child.send_prompt(prompt)
        return child

    def compact_fork(self, prompt: str) -> "CodexAgent":
        if self._thread_id is None:
            raise RuntimeError("Cannot compact-fork a Codex agent without a known thread id.")

        thread = self._control_client.fork_and_compact_thread(self._thread_id)
        child = self._clone()
        child._thread_id = thread["id"]
        child._total_tokens_sent = self._total_tokens_sent
        child._cached_input_tokens_used = self._cached_input_tokens_used
        child._total_tokens_received = self._total_tokens_received
        child.send_prompt(prompt)
        return child

    def clear_context(self) -> None:
        self._thread_id = None

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

    def _record_token_usage(self, prompt: str, output: str) -> None:
        usage = self._extract_usage_from_output(output)
        if usage is None:
            self._total_tokens_sent += self._estimate_tokens(prompt)
            self._total_tokens_received += self._estimate_tokens(output)
            return
        input_tokens, cached_input_tokens, output_tokens = usage
        self._total_tokens_sent += input_tokens
        self._cached_input_tokens_used += cached_input_tokens
        self._total_tokens_received += output_tokens

    @staticmethod
    def _estimate_tokens(text: str) -> int:
        return len(text.split())

    def _extract_usage_from_output(self, output: str) -> tuple[int, int, int] | None:
        usage_candidates: list[tuple[int, int, int]] = []
        for line in output.splitlines():
            line = line.strip()
            if not line:
                continue
            try:
                payload = json.loads(line)
            except json.JSONDecodeError:
                continue
            usage = self._extract_usage_from_payload(payload)
            if usage is not None:
                usage_candidates.append(usage)

        if not usage_candidates:
            return None

        return (
            max(item[0] for item in usage_candidates),
            max(item[1] for item in usage_candidates),
            max(item[2] for item in usage_candidates),
        )

    def _extract_usage_from_payload(self, payload) -> tuple[int, int, int] | None:
        if isinstance(payload, dict):
            input_tokens = 0
            cached_input_tokens = 0
            output_tokens = 0
            found = False
            for key, value in payload.items():
                if isinstance(value, (dict, list)):
                    nested_usage = self._extract_usage_from_payload(value)
                    if nested_usage is not None:
                        input_tokens += nested_usage[0]
                        cached_input_tokens += nested_usage[1]
                        output_tokens += nested_usage[2]
                        found = True
                    continue
                if not isinstance(value, int):
                    continue
                if key in {"input_tokens", "prompt_tokens", "total_input_tokens"}:
                    input_tokens += value
                    found = True
                if key in {"cached_input_tokens", "cached_prompt_tokens", "total_cached_input_tokens"}:
                    cached_input_tokens += value
                    found = True
                if key in {"output_tokens", "completion_tokens", "total_output_tokens"}:
                    output_tokens += value
                    found = True
            return (input_tokens, cached_input_tokens, output_tokens) if found else None

        if isinstance(payload, list):
            input_tokens = 0
            cached_input_tokens = 0
            output_tokens = 0
            found = False
            for item in payload:
                nested_usage = self._extract_usage_from_payload(item)
                if nested_usage is None:
                    continue
                input_tokens += nested_usage[0]
                cached_input_tokens += nested_usage[1]
                output_tokens += nested_usage[2]
                found = True
            return (input_tokens, cached_input_tokens, output_tokens) if found else None

        return None

    def _clone(self) -> "CodexAgent":
        child = CodexAgent(
            model_name=self._model_name,
            working_dir=self._working_dir,
            timeout=self._timeout,
            task_type=self._task_type,
            library=self._library,
        )
        child._control_client = self._control_client
        return child

    @staticmethod
    def _extract_thread_id(output: str) -> str | None:
        for line in output.splitlines():
            try:
                payload = json.loads(line)
            except json.JSONDecodeError:
                continue
            if payload.get("type") == "thread.started":
                return payload.get("thread_id")
        return None

    @staticmethod
    def _extract_last_message(output: str) -> str:
        last_message = ""
        for line in output.splitlines():
            try:
                payload = json.loads(line)
            except json.JSONDecodeError:
                continue
            if payload.get("type") != "item.completed":
                continue
            item = payload.get("item", {})
            if item.get("type") == "agent_message":
                last_message = item.get("text", "") or last_message
        return last_message
