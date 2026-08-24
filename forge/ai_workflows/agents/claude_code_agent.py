# Copyright and related rights waived via CC0

"""Offline Claude Code adapter for the Forge agent API."""

from __future__ import annotations

import json
import os
import subprocess
import uuid

from ai_workflows.agents.agent import Agent
from ai_workflows.agents.runtime import agent_process_environment
from utility_scripts.gradle_test_runner import run_gradle_test_command


@Agent.register("claude-code")
class ClaudeCodeAgent(Agent):
    """Drive Claude Code in print mode with repository-only tools."""

    def __init__(
            self,
            model_name: str,
            working_dir: str,
            timeout: int = 1200,
            task_type: str = "session",
            library: str | None = None,
            persistent_instructions: str | None = None,
            environment: dict[str, str] | None = None,
            **_,
    ):
        self._model_name = model_name
        self._working_dir = os.path.abspath(working_dir)
        self._timeout = timeout
        self._task_type = task_type
        self._library = library
        self._persistent_instructions = persistent_instructions
        self._environment = agent_process_environment(environment)
        self._session_id: str | None = None
        self._total_tokens_sent = 0
        self._total_tokens_received = 0
        self._cached_input_tokens_used = 0
        self._session_log_path = self._create_session_log_path(
            "claude-code", task_type, library
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

    def send_prompt(self, prompt: str) -> str:
        self._print_session_log_once("Claude Code", self._session_log_path)
        command = self._build_command(prompt)
        try:
            result = subprocess.run(
                command,
                cwd=self._working_dir,
                env=self._environment,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                timeout=self._timeout,
                check=False,
            )
        except subprocess.TimeoutExpired as exc:
            self._append_log(prompt, exc.stdout or "")
            raise RuntimeError("Claude Code prompt timed out.") from exc
        self._append_log(prompt, result.stdout)
        if result.returncode != 0:
            raise RuntimeError(f"Claude Code command failed with exit code {result.returncode}.")
        payload = self._parse_result(result.stdout)
        self._session_id = str(payload.get("session_id") or self._session_id or "") or None
        usage = payload.get("usage") or {}
        self._total_tokens_sent += int(usage.get("input_tokens", 0) or 0)
        self._total_tokens_received += int(usage.get("output_tokens", 0) or 0)
        self._cached_input_tokens_used += int(usage.get("cache_read_input_tokens", 0) or 0)
        response = str(payload.get("result") or "")
        if not response:
            raise RuntimeError("Claude Code command completed without an assistant result.")
        return response

    def _build_command(self, prompt: str, fork: bool = False) -> list[str]:
        command = [
            "claude", "-p", prompt,
            "--output-format", "json",
            "--permission-mode", "dontAsk",
            "--tools", "Read,Edit,Write,Glob,Grep",
            "--model", self._model_name,
        ]
        if self._persistent_instructions:
            command.extend(["--append-system-prompt", self._persistent_instructions])
        if self._session_id:
            command.extend(["--resume", self._session_id])
            if fork:
                command.append("--fork-session")
        else:
            command.extend(["--session-id", str(uuid.uuid4())])
        return command

    def fork(self, prompt: str) -> "ClaudeCodeAgent":
        if self._session_id is None:
            raise RuntimeError("Cannot fork Claude Code without a session id.")
        child = self._clone()
        child._session_id = self._session_id
        child.send_prompt_with_fork(prompt)
        return child

    def send_prompt_with_fork(self, prompt: str) -> str:
        command = self._build_command(prompt, fork=True)
        result = subprocess.run(
            command,
            cwd=self._working_dir,
            env=self._environment,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            timeout=self._timeout,
            check=False,
        )
        self._append_log(prompt, result.stdout)
        if result.returncode != 0:
            raise RuntimeError(f"Claude Code command failed with exit code {result.returncode}.")
        payload = self._parse_result(result.stdout)
        self._session_id = str(payload.get("session_id") or "") or None
        return str(payload.get("result") or "")

    def compact_fork(self, prompt: str) -> "ClaudeCodeAgent":
        return self.fork(prompt)

    def clear_context(self) -> None:
        self._session_id = None

    def replace_persistent_instructions(self, persistent_instructions: str | None) -> None:
        self._persistent_instructions = persistent_instructions

    def run_test_command(self, test_cmd: str) -> str:
        return run_gradle_test_command(test_cmd, self._working_dir, library=self._library)

    def _clone(self) -> "ClaudeCodeAgent":
        return ClaudeCodeAgent(
            model_name=self._model_name,
            working_dir=self._working_dir,
            timeout=self._timeout,
            task_type=self._task_type,
            library=self._library,
            persistent_instructions=self._persistent_instructions,
            environment=self._environment,
        )

    def _append_log(self, prompt: str, output: str) -> None:
        with open(self._session_log_path, "a", encoding="utf-8") as log_file:
            log_file.write(f"Prompt:\n{prompt}\n\nConversation:\n{output}\n")

    @staticmethod
    def _parse_result(output: str) -> dict:
        try:
            payload = json.loads(output)
        except json.JSONDecodeError as exc:
            raise RuntimeError("Claude Code emitted invalid JSON.") from exc
        if not isinstance(payload, dict):
            raise RuntimeError("Claude Code emitted a non-object JSON result.")
        return payload
