# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import json
import os
import subprocess
import tempfile
import time
from ai_workflows.agents.agent import Agent, AgentTimeoutError
from ai_workflows.agents.agent_runtime import (
    CODEX_BYPASS_APPROVALS_AND_SANDBOX_FLAG,
    agent_process_environment,
)
from ai_workflows.agents.codex_app_server import CodexAppServerClient
from utility_scripts.gradle_test_runner import run_gradle_test_command


def extract_codex_token_usage(output: str) -> tuple[int, int, int] | None:
    """Parse cumulative `(input, cached_input, output)` token usage from a Codex `--json` stream.

    Returns `None` when the output carries no recognizable usage payload.
    """
    usage_candidates: list[tuple[int, int, int]] = []
    for line in output.splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            payload = json.loads(line)
        except json.JSONDecodeError:
            continue
        usage = _extract_usage_from_payload(payload)
        if usage is not None:
            usage_candidates.append(usage)

    if not usage_candidates:
        return None

    return (
        max(item[0] for item in usage_candidates),
        max(item[1] for item in usage_candidates),
        max(item[2] for item in usage_candidates),
    )


def _extract_usage_from_payload(payload) -> tuple[int, int, int] | None:
    if isinstance(payload, dict):
        input_tokens = 0
        cached_input_tokens = 0
        output_tokens = 0
        found = False
        for key, value in payload.items():
            if isinstance(value, (dict, list)):
                nested_usage = _extract_usage_from_payload(value)
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
            nested_usage = _extract_usage_from_payload(item)
            if nested_usage is None:
                continue
            input_tokens += nested_usage[0]
            cached_input_tokens += nested_usage[1]
            output_tokens += nested_usage[2]
            found = True
        return (input_tokens, cached_input_tokens, output_tokens) if found else None

    return None


@Agent.register("codex")
class CodexAgent(Agent):
    """Stateful Codex adapter that separates thread control from turn execution."""

    SPINNER_INTERVAL_SECONDS = 0.2

    def __init__(
            self,
            model_name: str,
            working_dir: str,
            timeout: int = 1200,
            task_type: str = "session",
            library: str | None = None,
            persistent_instructions: str | None = None,
            environment: dict[str, str] | None = None,
            agent_family: str | None = None,
            agent_name: str | None = None,
            thinking_level: str | None = None,
            **_,
    ):
        self._model_name = model_name
        self._working_dir = os.path.abspath(working_dir)
        self._timeout = timeout
        self._task_type = task_type
        self._library = library
        self._persistent_instructions = persistent_instructions
        source_environment = os.environ if environment is None else environment
        self._agent_family = agent_family or source_environment.get("FORGE_AGENT_FAMILY")
        self._reasoning_effort = thinking_level or (
            "high" if model_name == "gpt-5.6-luna" else "medium"
        )
        self._codex_command = agent_name or "codex"
        self._environment = agent_process_environment(source_environment)
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
            persistent_instructions=self._persistent_instructions,
            environment=self._environment,
            codex_command=self._codex_command,
            reasoning_effort=self._reasoning_effort,
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

    def graphify(self, source_dirs: list[str]) -> str:
        """Send $graphify to the Codex session to build a merged knowledge graph context."""
        from utility_scripts.stage_logger import log_detail
        from utility_scripts.task_logs import display_log_path
        if not source_dirs:
            return ""
        log_detail("graphify", f"Initializing knowledge graph context for {len(source_dirs)} source(s)")
        result = self.send_prompt(f"$graphify {source_dirs[0]}")
        for extra_dir in source_dirs[1:]:
            log_detail("graphify", f"Merging graph from {display_log_path(extra_dir)}")
            result = self.send_prompt(f"$graphify {extra_dir} --update")
        log_detail("graphify", "Knowledge graph context initialized")
        return result

    def send_prompt(self, prompt: str) -> str:
        self._print_session_log_once("Codex", self._session_log_path)
        with self._agent_activity("Codex"):
            original_thread_id = self._thread_id
            cmd = self._build_exec_command(prompt)
            try:
                returncode, output = self._run_codex_command(cmd)
            except subprocess.TimeoutExpired as exc:
                self._write_turn_log(original_thread_id, prompt, exc.output or "")
                raise AgentTimeoutError(
                    self._current_agent_action(), self._timeout, self._session_log_path,
                ) from exc

            self._record_token_usage(prompt, output)
            if self._thread_id is None:
                self._thread_id = self._extract_thread_id(output)
            self._write_turn_log(self._thread_id or original_thread_id, prompt, output)
            if returncode != 0:
                raise RuntimeError(f"Codex command failed with exit code {returncode}.")
            if self._thread_id is None:
                raise RuntimeError("Codex command completed without a thread id.")
            response = self._extract_last_message(output)
            if not response:
                raise RuntimeError("Codex command completed without an assistant message.")
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
                    env=self._environment,
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
        """Append one prompt/response generation turn to the durable session log
        (§FS-durable-generation-logs).
        """
        with open(self._session_log_path, "a", encoding="utf-8") as log_file:
            log_file.write(f"Thread ID:{thread_id or 'unknown'}\n")
            log_file.write(f"Persistent instructions: {self._persistent_instruction_status()}\n")
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

    def replace_persistent_instructions(self, persistent_instructions: str | None) -> None:
        self._persistent_instructions = persistent_instructions
        self._control_client = CodexAppServerClient(
            model_name=self._model_name,
            working_dir=self._working_dir,
            timeout=self._timeout,
            persistent_instructions=self._persistent_instructions,
            environment=self._environment,
            codex_command=self._codex_command,
            reasoning_effort=self._reasoning_effort,
        )

    def run_test_command(self, test_cmd: str) -> str:
        return run_gradle_test_command(test_cmd, self._working_dir, library=self._library)

    def _record_token_usage(self, prompt: str, output: str) -> None:
        usage = extract_codex_token_usage(output)
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

    def _clone(self) -> "CodexAgent":
        child = CodexAgent(
            model_name=self._model_name,
            working_dir=self._working_dir,
            timeout=self._timeout,
            task_type=self._task_type,
            library=self._library,
            persistent_instructions=self._persistent_instructions,
            environment=self._environment,
            agent_family=self._agent_family,
            agent_name=self._codex_command,
            thinking_level=self._reasoning_effort,
        )
        child._control_client = self._control_client
        return child

    def _build_config_args(self) -> list[str]:
        config = {
            "reasoning.effort": self._reasoning_effort,
        }
        if self._persistent_instructions:
            config["developer_instructions"] = self._persistent_instructions

        args: list[str] = []
        for key, value in config.items():
            raw_keys: set[str] = set()
            rendered = value if key in raw_keys else self._toml_string(value)
            args.extend(["-c", f"{key}={rendered}"])
        return args

    def _build_exec_command(self, prompt: str) -> list[str]:
        command = [self._codex_command, "exec"]
        if self._thread_id is not None:
            command.append("resume")
        command.extend([
            CODEX_BYPASS_APPROVALS_AND_SANDBOX_FLAG,
            "--json",
            *self._build_config_args(),
            "-m", self._model_name,
        ])
        if self._thread_id is not None:
            command.append(self._thread_id)
        command.append(prompt)
        return command

    @staticmethod
    def _toml_string(value: str) -> str:
        """Return a TOML basic string using JSON-compatible escaping."""
        return json.dumps(value, ensure_ascii=False)

    def _persistent_instruction_status(self) -> str:
        if not self._persistent_instructions:
            return "not configured"
        return f"configured ({len(self._persistent_instructions)} chars)"

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
