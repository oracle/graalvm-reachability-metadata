# Copyright and related rights waived via CC0

from __future__ import annotations

import json
import os
import shlex
import tempfile
import unittest
from unittest.mock import patch

from ai_workflows.agents import Agent
from ai_workflows.agents.claude_code_agent import ClaudeCodeAgent
from ai_workflows.agents.codex_agent import CodexAgent
from ai_workflows.agents.codex_app_server import CodexAppServerClient
from ai_workflows.agents.opencode_agent import OFFLINE_OPENCODE_CONFIG, OpenCodeAgent
from ai_workflows.agents.runtime import (
    SUPPORTED_AGENT_BACKENDS,
    analysis_agent_selection,
    agent_process_environment,
    apply_test_agent_overrides,
    normalize_backend_name,
)
from utility_scripts.source_context import url_fetch_agent_command


class AgentRuntimeTests(unittest.TestCase):
    def test_all_supported_backends_are_registered(self) -> None:
        self.assertEqual(
            set(SUPPORTED_AGENT_BACKENDS),
            {name for name in SUPPORTED_AGENT_BACKENDS if Agent.get_class(name)},
        )

    def test_role_environment_overrides_are_independent(self) -> None:
        environment = {
            "FORGE_ANALYSIS_AGENT": "claude-code",
            "FORGE_ANALYSIS_MODEL": "claude-opus-4-1",
            "FORGE_TEST_AGENT": "opencode",
            "FORGE_TEST_MODEL": "anthropic/claude-sonnet-4-5",
            "FORGE_ANALYSIS_FAMILY": "claude-code",
            "FORGE_TEST_FAMILY": "opencode",
            "FORGE_ANALYSIS_AGENT": "cdx",
        }
        self.assertEqual(
            analysis_agent_selection(environment).backend,
            "claude-code",
        )
        strategy = apply_test_agent_overrides(
            {"agent": "pi", "model": "gpt-5.6-sol"},
            environment,
        )
        self.assertEqual(strategy["agent"], "opencode")
        self.assertEqual(strategy["model"], "anthropic/claude-sonnet-4-5")
        self.assertEqual(analysis_agent_selection(environment).agent, "cdx")

    def test_backend_aware_model_defaults(self) -> None:
        codex = analysis_agent_selection({"FORGE_ANALYSIS_AGENT": "codex"})
        claude = analysis_agent_selection({"FORGE_ANALYSIS_AGENT": "claude-code"})
        self.assertEqual(codex.model, "gpt-5.6-luna")
        self.assertEqual(codex.thinking_level, "high")
        self.assertEqual(claude.model, "sonnet")

    def test_test_agent_override_uses_backend_aware_model_default(self) -> None:
        strategy = {"agent": "pi", "model": "gpt-5.6-sol"}
        self.assertEqual(
            apply_test_agent_overrides(strategy, {"FORGE_TEST_AGENT": "codex"})["model"],
            "gpt-5.6-luna",
        )
        self.assertEqual(
            apply_test_agent_overrides(strategy, {"FORGE_TEST_AGENT": "claude-code"})["model"],
            "sonnet",
        )

    def test_aliases_normalize_to_canonical_names(self) -> None:
        self.assertEqual(normalize_backend_name("claude"), "claude-code")
        self.assertEqual(normalize_backend_name("open-code"), "opencode")

    def test_agent_environment_withholds_github_credentials(self) -> None:
        environment = agent_process_environment({
            "PATH": "/bin",
            "GH_TOKEN": "secret",
            "GITHUB_TOKEN": "secret",
        })
        self.assertNotIn("GH_TOKEN", environment)
        self.assertNotIn("GITHUB_TOKEN", environment)
        self.assertEqual(environment["GH_CONFIG_DIR"], "/nonexistent/forge-agent-no-github-config")

    def test_explicit_empty_agent_environment_does_not_inherit_ambient_credentials(self) -> None:
        with patch.dict(os.environ, {"GH_TOKEN": "ambient-secret"}, clear=False):
            environment = agent_process_environment({})
        self.assertNotIn("GH_TOKEN", environment)

    def test_codex_offline_config_disables_network_web_and_mcps(self) -> None:
        agent = CodexAgent.__new__(CodexAgent)
        agent._persistent_instructions = None
        agent._reasoning_effort = "high"
        rendered = " ".join(agent._build_config_args())
        self.assertIn('reasoning.effort="high"', rendered)
        self.assertIn('approval_policy="never"', rendered)
        self.assertIn('sandbox_mode="workspace-write"', rendered)
        self.assertIn("sandbox_workspace_write.network_access=false", rendered)
        self.assertIn('web_search="disabled"', rendered)
        self.assertIn("agents.enabled=false", rendered)
        self.assertIn("features.skill_mcp_dependency_install=false", rendered)
        self.assertIn("mcp_servers={}", rendered)

    def test_codex_agent_uses_the_role_agent_executable(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir, patch.object(
                Agent,
                "_create_session_log_path",
                return_value=os.path.join(temp_dir, "codex.log"),
        ):
            agent = CodexAgent(
                "gpt-5.6-luna",
                temp_dir,
                environment={"FORGE_ANALYSIS_FAMILY": "codex"},
                agent_name="cdx",
            )
        self.assertEqual(agent._codex_command, "cdx")
        self.assertEqual(agent._reasoning_effort, "high")

    def test_codex_thread_control_uses_the_offline_profile(self) -> None:
        client = CodexAppServerClient("gpt-5.6-terra", "/repo", environment={})
        params = client._build_common_thread_params()
        self.assertEqual(params["sandbox"], "workspace-write")
        self.assertFalse(params["config"]["sandbox_workspace_write.network_access"])
        self.assertEqual(params["config"]["web_search"], "disabled")
        self.assertFalse(params["config"]["agents.enabled"])
        self.assertEqual(params["config"]["mcp_servers"], {})
        self.assertIn("mcp_servers={}", client._command())

    def test_claude_code_command_allows_only_repository_tools(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir, patch.object(
                Agent,
                "_create_session_log_path",
                return_value=os.path.join(temp_dir, "claude.log"),
        ):
            agent = ClaudeCodeAgent("claude-opus-4-1", temp_dir)
        command = agent._build_command("edit tests")
        self.assertIn("Read,Edit,Write,Glob,Grep", command)
        self.assertNotIn("Bash", command)
        self.assertNotIn("WebFetch", command)
        self.assertNotIn("WebSearch", command)

    def test_opencode_inline_config_is_deny_by_default(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir, patch.object(
                Agent,
                "_create_session_log_path",
                return_value=os.path.join(temp_dir, "opencode.log"),
        ):
            agent = OpenCodeAgent("anthropic/claude-sonnet-4-5", temp_dir)
        config = json.loads(agent._environment["OPENCODE_CONFIG_CONTENT"])
        self.assertEqual(config, OFFLINE_OPENCODE_CONFIG)
        self.assertEqual(config["permission"]["*"], "deny")
        self.assertEqual(config["permission"]["edit"], "allow")

    def test_url_discovery_is_the_explicit_network_enabled_exception(self) -> None:
        with patch.dict(
                os.environ,
                {
                    "FORGE_ANALYSIS_AGENT": "codex",
                    "FORGE_ANALYSIS_MODEL": "gpt-5.6-terra",
                },
                clear=False,
        ):
            command = url_fetch_agent_command()
        self.assertIn("sandbox_workspace_write.network_access=true", command)
        self.assertIn('web_search="live"', command)
        self.assertIn("agents.enabled=false", command)

    def test_url_discovery_uses_the_role_agent_executable(self) -> None:
        with patch.dict(
                os.environ,
                {
                    "FORGE_ANALYSIS_AGENT": "cdx",
                    "FORGE_ANALYSIS_FAMILY": "codex",
                    "FORGE_ANALYSIS_MODEL": "gpt-5.6-terra",
                },
                clear=True,
        ):
            command = url_fetch_agent_command()
        self.assertTrue(command.startswith("cdx exec "))

    def test_every_backend_has_a_bounded_url_discovery_command(self) -> None:
        expected = {
            "claude-code": "WebFetch,WebSearch",
            "pi": "--provider openai-codex",
            "codex": "network_access=true",
            "opencode": "OPENCODE_CONFIG_CONTENT=",
        }
        for backend, marker in expected.items():
            with self.subTest(backend=backend), patch.dict(
                    os.environ,
                    {
                        "FORGE_ANALYSIS_AGENT": backend,
                        "FORGE_ANALYSIS_FAMILY": backend,
                        "FORGE_ANALYSIS_MODEL": (
                            "anthropic/claude-sonnet-4-5"
                            if backend == "opencode"
                            else "gpt-5.6-terra"
                        ),
                    },
                    clear=True,
            ):
                command = url_fetch_agent_command()
            self.assertIn(marker, command)
            if backend == "opencode":
                config_argument = next(
                    token for token in shlex.split(command)
                    if token.startswith("OPENCODE_CONFIG_CONTENT=")
                )
                config = json.loads(config_argument.split("=", 1)[1])
                self.assertEqual(config["permission"]["bash"], "deny")
                self.assertEqual(config["permission"]["webfetch"], "allow")


if __name__ == "__main__":
    unittest.main()
