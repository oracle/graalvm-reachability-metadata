# Copyright and related rights waived via CC0

"""Offline OpenCode adapter for the Forge agent API."""

from __future__ import annotations

import json
import os
import subprocess

from ai_workflows.agents.agent import Agent
from ai_workflows.agents.runtime import agent_process_environment
from utility_scripts.gradle_test_runner import run_gradle_test_command


OFFLINE_OPENCODE_CONFIG = {
    "autoupdate": False,
    "share": "disabled",
    "permission": {
        "*": "deny",
        "read": "allow",
        "edit": "allow",
        "glob": "allow",
        "grep": "allow",
        "list": "allow",
        "lsp": "allow",
        "bash": "deny",
        "task": "deny",
        "external_directory": "deny",
        "webfetch": "deny",
        "websearch": "deny",
        "skill": "deny",
        "question": "deny",
    },
}


@Agent.register("opencode")
class OpenCodeAgent(Agent):
    """Drive OpenCode with an inline deny-by-default tool policy."""

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
        self._environment["OPENCODE_CONFIG_CONTENT"] = json.dumps(OFFLINE_OPENCODE_CONFIG)
        self._session_id: str | None = None
        self._total_tokens_sent = 0
        self._total_tokens_received = 0
        self._cached_input_tokens_used = 0
        self._session_log_path = self._create_session_log_path("opencode", task_type, library)

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
        return self._run_prompt(prompt, fork=False)

    def _run_prompt(self, prompt: str, fork: bool) -> str:
        self._print_session_log_once("OpenCode", self._session_log_path)
        effective_prompt = prompt
        if self._persistent_instructions:
            effective_prompt = f"{self._persistent_instructions}\n\n{prompt}"
        command = [
            "opencode", "run", "--format", "json", "--auto",
            "--model", self._model_name,
        ]
        if self._session_id:
            command.extend(["--session", self._session_id])
            if fork:
                command.append("--fork")
        command.append(effective_prompt)
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
            raise RuntimeError("OpenCode prompt timed out.") from exc
        self._append_log(prompt, result.stdout)
        if result.returncode != 0:
            raise RuntimeError(f"OpenCode command failed with exit code {result.returncode}.")
        response = self._parse_events(result.stdout)
        if not response:
            raise RuntimeError("OpenCode command completed without an assistant result.")
        return response

    def fork(self, prompt: str) -> "OpenCodeAgent":
        if self._session_id is None:
            raise RuntimeError("Cannot fork OpenCode without a session id.")
        child = self._clone()
        child._session_id = self._session_id
        child._run_prompt(prompt, fork=True)
        return child

    def compact_fork(self, prompt: str) -> "OpenCodeAgent":
        return self.fork(prompt)

    def clear_context(self) -> None:
        self._session_id = None

    def replace_persistent_instructions(self, persistent_instructions: str | None) -> None:
        self._persistent_instructions = persistent_instructions

    def run_test_command(self, test_cmd: str) -> str:
        return run_gradle_test_command(test_cmd, self._working_dir, library=self._library)

    def _clone(self) -> "OpenCodeAgent":
        return OpenCodeAgent(
            model_name=self._model_name,
            working_dir=self._working_dir,
            timeout=self._timeout,
            task_type=self._task_type,
            library=self._library,
            persistent_instructions=self._persistent_instructions,
            environment=self._environment,
        )

    def _parse_events(self, output: str) -> str:
        text_parts: list[str] = []
        for line in output.splitlines():
            try:
                event = json.loads(line)
            except json.JSONDecodeError:
                continue
            session_id = event.get("sessionID") or event.get("session_id")
            if session_id:
                self._session_id = str(session_id)
            if event.get("type") == "text":
                part = event.get("part") or {}
                text_parts.append(str(part.get("text") or event.get("text") or ""))
            usage = event.get("usage") or (event.get("part") or {}).get("usage") or {}
            self._total_tokens_sent += int(usage.get("input", 0) or 0)
            self._total_tokens_received += int(usage.get("output", 0) or 0)
            self._cached_input_tokens_used += int(usage.get("cacheRead", 0) or 0)
        return "".join(text_parts)

    def _append_log(self, prompt: str, output: str) -> None:
        with open(self._session_log_path, "a", encoding="utf-8") as log_file:
            log_file.write(f"Prompt:\n{prompt}\n\nConversation:\n{output}\n")
