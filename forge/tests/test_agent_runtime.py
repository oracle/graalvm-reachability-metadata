# Copyright and related rights waived via CC0

from __future__ import annotations

import contextlib
import io
import json
import os
import shlex
import subprocess
import tempfile
import unittest
from unittest.mock import Mock, patch

from ai_workflows.agents import Agent
from ai_workflows.agents.agent import AgentTimeoutError
from ai_workflows.agents.claude_code_agent import ClaudeCodeAgent
from ai_workflows.agents.pi_agent import PiAgent
from ai_workflows.agents.pi_rpc_client import PromptResult
from ai_workflows.agents.agent_runtime import PROVIDER_AWARE_BACKENDS, resolve_provider
from ai_workflows.agents.agent_runtime import CLAUDE_CODE_BYPASS_PERMISSIONS_FLAGS, AgentSelection, DEFAULT_AGENT, SUPPORTED_AGENT_BACKENDS, default_model_for_backend, get_analysis_agent, get_setup_agent, agent_process_environment, normalize_backend_name, run_agent_task


class AgentRuntimeTests(unittest.TestCase):
    def test_all_supported_backends_are_registered(self) -> None:
        self.assertEqual(
            set(SUPPORTED_AGENT_BACKENDS),
            {name for name in SUPPORTED_AGENT_BACKENDS if Agent.get_class(name)},
        )

    def test_pi_reuses_the_first_log_path_for_every_turn(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            log_path = os.path.join(temp_dir, "pi-generation.log")
            agent = PiAgent(
                model_name="test-model",
                working_dir=temp_dir,
                library="g:a:1.0",
                task_type="native-test-verify",
            )
            agent._rpc_client = Mock()
            agent._rpc_client.run_prompt.side_effect = [
                PromptResult("first", "/tmp/pi-session-1", {"tokens": {}}),
                PromptResult("second", "/tmp/pi-session-2", {"tokens": {}}),
            ]

            with patch.object(
                    agent,
                    "_build_generation_log_path",
                    return_value=log_path,
            ) as build_log_path, contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(agent.send_prompt("first prompt"), "first")
                self.assertEqual(agent.send_prompt("second prompt"), "second")

            with open(log_path, "r", encoding="utf-8") as log_file:
                durable_log = log_file.read()

        build_log_path.assert_called_once_with()
        self.assertEqual(agent._session_log_path, log_path)
        self.assertEqual(durable_log.count("Timestamp:"), 2)

    def test_worker_roles_do_not_read_each_other(self) -> None:
        """Retuning one role must never move the other; both take DEFAULT_AGENT."""
        analysis_only = {
            "FORGE_ANALYSIS_FAMILY": "pi",
            "FORGE_ANALYSIS_AGENT": "my-pi",
            "FORGE_ANALYSIS_MODEL": "gpt-5.6-sol",
            "FORGE_ANALYSIS_PROVIDER": "openrouter",
        }
        setup = get_setup_agent(analysis_only)
        self.assertEqual(setup.backend, DEFAULT_AGENT)
        self.assertEqual(setup.model, default_model_for_backend(DEFAULT_AGENT))
        self.assertEqual(setup.provider, "openai-codex")

        setup_only = {
            "FORGE_SETUP_FAMILY": "pi",
            "FORGE_SETUP_MODEL": "cheap-model",
            "FORGE_SETUP_PROVIDER": "openrouter",
        }
        analysis = get_analysis_agent(setup_only)
        self.assertEqual(analysis.backend, DEFAULT_AGENT)
        self.assertEqual(analysis.model, default_model_for_backend(DEFAULT_AGENT))
        self.assertEqual(analysis.provider, "openai-codex")

    def test_family_only_selection_uses_the_family_default_executable(self) -> None:
        claude = get_analysis_agent({"FORGE_ANALYSIS_FAMILY": "claude-code"})
        self.assertEqual(claude.backend, "claude-code")
        self.assertEqual(claude.agent, "claude")

    def test_agent_environment_withholds_github_credentials(self) -> None:
        environment = agent_process_environment({
            "PATH": "/bin",
            "GH_TOKEN": "secret",
            "GITHUB_TOKEN": "secret",
        })
        self.assertNotIn("GH_TOKEN", environment)
        self.assertNotIn("GITHUB_TOKEN", environment)
        self.assertEqual(environment["GH_CONFIG_DIR"], "/nonexistent/forge-agent-no-github-config")

    def test_trusted_agent_environment_retains_github_credentials(self) -> None:
        environment = agent_process_environment({
            "GH_TOKEN": "secret",
            "GITHUB_TOKEN": "secret",
            "GH_CONFIG_DIR": "/github-config",
            "_FORGE_AGENT_ALLOW_GITHUB_ACCESS": "1",
        })

        self.assertEqual(environment["GH_TOKEN"], "secret")
        self.assertEqual(environment["GITHUB_TOKEN"], "secret")
        self.assertEqual(environment["GH_CONFIG_DIR"], "/github-config")
        self.assertNotIn("_FORGE_AGENT_ALLOW_GITHUB_ACCESS", environment)

    def test_explicit_empty_agent_environment_does_not_inherit_ambient_credentials(self) -> None:
        with patch.dict(os.environ, {"GH_TOKEN": "ambient-secret"}, clear=False):
            environment = agent_process_environment({})
        self.assertNotIn("GH_TOKEN", environment)

if __name__ == "__main__":
    unittest.main()
