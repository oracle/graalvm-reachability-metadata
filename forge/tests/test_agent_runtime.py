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
from ai_workflows.agents.codex_agent import CodexAgent
from ai_workflows.agents.codex_app_server import CodexAppServerClient
from ai_workflows.agents.opencode_agent import OpenCodeAgent
from ai_workflows.agents.agent_runtime import PROVIDER_AWARE_BACKENDS, resolve_provider
from ai_workflows.agents.agent_runtime import (
    CODEX_BYPASS_APPROVALS_AND_SANDBOX_FLAG,
    AgentSelection,
    DEFAULT_AGENT,
    SUPPORTED_AGENT_BACKENDS,
    default_model_for_backend,
    get_analysis_agent,
    get_setup_agent,
    agent_process_environment,
    normalize_backend_name,
    run_agent_task,
)
from utility_scripts.source_context import url_fetch_agent_command
from utility_scripts.strategy_loader import (
    list_strategy_names,
    load_predefined_strategies,
    load_strategy_by_name,
)


class AgentRuntimeTests(unittest.TestCase):
    def test_all_supported_backends_are_registered(self) -> None:
        self.assertEqual(
            set(SUPPORTED_AGENT_BACKENDS),
            {name for name in SUPPORTED_AGENT_BACKENDS if Agent.get_class(name)},
        )

    def test_agent_timeout_prints_named_quiet_activity_and_log(self) -> None:
        with tempfile.TemporaryDirectory() as work_dir:
            agent = CodexAgent(
                model_name="gpt-5.6-luna",
                working_dir=work_dir,
                timeout=1200,
                library="org.example:demo:1.0.0",
                task_type="add-new-library-support",
            )
            timeout = subprocess.TimeoutExpired(["codex"], 1200, output="partial private output")
            output = io.StringIO()
            with patch.object(agent, "_run_codex_command", side_effect=timeout), \
                    contextlib.redirect_stdout(output), self.assertRaises(AgentTimeoutError) as raised:
                agent.send_prompt_for_action("prompt", "dynamic_access_iteration()")

        rendered = output.getvalue()
        self.assertIn("Running dynamic_access_iteration()", rendered)
        self.assertIn("dynamic_access_iteration() timed out", rendered)
        self.assertIn("log:", rendered)
        self.assertNotIn("partial private output", rendered)
        self.assertEqual(raised.exception.timeout_seconds, 1200)

    def test_one_shot_agent_failure_preserves_message(self) -> None:
        failed_agent = Mock()
        failed_agent.send_prompt_for_action.side_effect = AgentTimeoutError(
            "native_test_verify()",
            1800,
            "/tmp/native-session.log",
        )
        failed_agent.total_tokens_sent = 0
        failed_agent.total_tokens_received = 0
        failed_agent.cached_input_tokens_used = 0
        failed_agent._session_log_path = "/tmp/native-session.log"

        with tempfile.TemporaryDirectory() as temp_dir:
            task_log_path = os.path.join(temp_dir, "native-task.log")
            with patch(
                    "ai_workflows.agents.agent_runtime.create_agent",
                    return_value=failed_agent,
            ), patch(
                    "ai_workflows.agents.agent_runtime.build_task_log_path",
                    return_value=task_log_path,
            ):
                result = run_agent_task(
                    selection=AgentSelection(backend="codex", model="test-model"),
                    working_dir=temp_dir,
                    prompt="repair",
                    task_type="native-test-verify",
                    library="g:a:1.0",
                    timeout=1800,
                )

        self.assertEqual(
            result.failure_message,
            "Agent native_test_verify() timed out after 30:00",
        )

    def test_the_environment_selects_the_analysis_role_only(self) -> None:
        """The worker configures analysis; a bundle owns the test role it declares."""
        environment = {
            "FORGE_ANALYSIS_FAMILY": "claude-code",
            "FORGE_ANALYSIS_MODEL": "claude-opus-4-1",
            "FORGE_ANALYSIS_AGENT": "cdx",
        }
        selection = get_analysis_agent(environment)
        self.assertEqual(selection.backend, "claude-code")
        self.assertEqual(selection.agent, "cdx")
        self.assertEqual(selection.model, "claude-opus-4-1")

        retarget = {
            "FORGE_TEST_AGENT": "opencode",
            "FORGE_TEST_FAMILY": "opencode",
            "FORGE_TEST_MODEL": "anthropic/claude-sonnet-4-5",
            "FORGE_TEST_PROVIDER": "openrouter",
            "FORGE_AGENT_FAMILY": "opencode",
        }
        with patch.dict(os.environ, retarget, clear=False):
            strategy = load_strategy_by_name("basic_iterative_pi_gpt-5.4")
        self.assertEqual(strategy["agent"], "pi")
        self.assertEqual(strategy["model"], "gpt-5.4")
        self.assertEqual(strategy["provider"], "openai-codex")

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
        self.assertIsNone(setup.provider)

        setup_only = {
            "FORGE_SETUP_FAMILY": "pi",
            "FORGE_SETUP_MODEL": "cheap-model",
            "FORGE_SETUP_PROVIDER": "openrouter",
        }
        analysis = get_analysis_agent(setup_only)
        self.assertEqual(analysis.backend, DEFAULT_AGENT)
        self.assertEqual(analysis.model, default_model_for_backend(DEFAULT_AGENT))
        self.assertIsNone(analysis.provider)

    def test_each_role_reads_its_own_settings(self) -> None:
        environment = {
            "FORGE_ANALYSIS_FAMILY": "codex",
            "FORGE_ANALYSIS_MODEL": "gpt-5.6-luna",
            "FORGE_SETUP_FAMILY": "pi",
            "FORGE_SETUP_MODEL": "cheap-model",
            "FORGE_SETUP_PROVIDER": "openrouter",
        }
        setup = get_setup_agent(environment)
        self.assertEqual((setup.backend, setup.model, setup.provider),
                         ("pi", "cheap-model", "openrouter"))
        analysis = get_analysis_agent(environment)
        self.assertEqual((analysis.backend, analysis.model), ("codex", "gpt-5.6-luna"))

    def test_url_discovery_reads_the_setup_role_and_carries_its_provider(self) -> None:
        with patch.dict(os.environ, {
                "FORGE_SETUP_FAMILY": "pi",
                "FORGE_SETUP_AGENT": "pi",
                "FORGE_SETUP_MODEL": "gpt-5.6-terra",
                "FORGE_SETUP_PROVIDER": "openrouter",
        }, clear=True):
            command = url_fetch_agent_command()
        self.assertIn("--provider openrouter", command)
        self.assertIn("--model gpt-5.6-terra", command)

    def test_url_discovery_carries_the_role_thinking_level(self) -> None:
        """Both setup steps run at the same effort, each backend in its own spelling."""
        expected = {
            "codex": "-c reasoning.effort=high",
            "claude-code": "--effort high",
            "pi": "--thinking high",
            "opencode": '"reasoningEffort":"high"',
        }
        for backend, fragment in expected.items():
            with self.subTest(backend=backend), patch.dict(os.environ, {
                    "FORGE_SETUP_FAMILY": backend,
                    "FORGE_SETUP_MODEL": "m",
                    "FORGE_SETUP_THINKING_LEVEL": "high",
            }, clear=True):
                self.assertIn(fragment, url_fetch_agent_command())

    def test_url_discovery_omits_thinking_when_the_role_resolves_none(self) -> None:
        for backend in SUPPORTED_AGENT_BACKENDS:
            with self.subTest(backend=backend), patch.dict(os.environ, {
                    "FORGE_SETUP_FAMILY": backend,
                    "FORGE_SETUP_MODEL": "m",
            }, clear=True):
                command = url_fetch_agent_command()
            for fragment in ("reasoning.effort", "--effort", "--thinking", "reasoningEffort"):
                self.assertNotIn(fragment, command)

    def test_adapters_pass_the_thinking_level_to_their_backend(self) -> None:
        with tempfile.TemporaryDirectory() as work_dir:
            claude = ClaudeCodeAgent(
                model_name="sonnet", working_dir=work_dir, thinking_level="xhigh"
            )
            command = claude._build_command("hi")  # noqa: SLF001
            self.assertEqual(command[command.index("--effort") + 1], "xhigh")

            opencode = OpenCodeAgent(
                model_name="m", working_dir=work_dir,
                provider="openrouter", thinking_level="high",
            )
            config = json.loads(opencode._environment["OPENCODE_CONFIG_CONTENT"])  # noqa: SLF001
            self.assertEqual(
                config["provider"]["openrouter"]["models"]["m"]["options"]["reasoningEffort"],
                "high",
            )

    def test_backend_aware_model_defaults(self) -> None:
        codex = get_analysis_agent({"FORGE_ANALYSIS_AGENT": "codex"})
        claude = get_analysis_agent({"FORGE_ANALYSIS_AGENT": "claude-code"})
        self.assertEqual(codex.model, "gpt-5.6-luna")
        self.assertEqual(codex.thinking_level, "high")
        self.assertEqual(claude.model, "sonnet")

    def test_family_only_selection_uses_the_family_default_executable(self) -> None:
        claude = get_analysis_agent({"FORGE_ANALYSIS_FAMILY": "claude-code"})
        self.assertEqual(claude.backend, "claude-code")
        self.assertEqual(claude.agent, "claude")

    def test_provider_defaults_only_for_provider_aware_backends(self) -> None:
        """Pi and OpenCode address a model through a provider; the others do not."""
        self.assertEqual(resolve_provider("pi", None), "openai-codex")
        self.assertEqual(resolve_provider("opencode", None), "openai-codex")
        self.assertIsNone(resolve_provider("codex", None))
        self.assertIsNone(resolve_provider("claude-code", None))

    def test_provider_configuration_is_dropped_for_backends_without_one(self) -> None:
        """A configured provider never reaches a CLI that has no such flag."""
        self.assertEqual(resolve_provider("pi", "openrouter"), "openrouter")
        self.assertIsNone(resolve_provider("codex", "openrouter"))

    def test_analysis_role_resolves_its_provider(self) -> None:
        selection = get_analysis_agent(
            {"FORGE_ANALYSIS_FAMILY": "pi", "FORGE_ANALYSIS_PROVIDER": "openrouter"}
        )
        self.assertEqual(selection.provider, "openrouter")
        self.assertEqual(
            get_analysis_agent({"FORGE_ANALYSIS_FAMILY": "pi"}).provider,
            "openai-codex",
        )
        self.assertIsNone(get_analysis_agent({"FORGE_ANALYSIS_FAMILY": "codex"}).provider)

    def test_loading_a_strategy_resolves_the_provider_its_backend_needs(self) -> None:
        """Pi and OpenCode bundles take openai-codex unless they name their own."""
        for name in list_strategy_names():
            strategy = load_strategy_by_name(name)
            if strategy.get("agent") in PROVIDER_AWARE_BACKENDS:
                self.assertIsNotNone(strategy.get("provider"), name)
            else:
                self.assertIsNone(strategy.get("provider"), name)

        raw = {
            entry["name"]: entry
            for entry in load_predefined_strategies()
        }
        defaulted = next(
            name for name, entry in raw.items()
            if entry.get("agent") == "pi" and "provider" not in entry
        )
        self.assertEqual(load_strategy_by_name(defaulted)["provider"], "openai-codex")
        declared = next(
            name for name, entry in raw.items() if entry.get("provider") == "openrouter"
        )
        self.assertEqual(load_strategy_by_name(declared)["provider"], "openrouter")

    def test_opencode_qualifies_its_model_with_the_provider(self) -> None:
        """OpenCode addresses a model as `provider/model`, and never doubles the prefix."""
        with tempfile.TemporaryDirectory() as work_dir:
            agent = OpenCodeAgent(model_name="some-model", working_dir=work_dir, provider="openrouter")
            self.assertEqual(agent._qualified_model, "openrouter/some-model")  # noqa: SLF001
            already = OpenCodeAgent(
                model_name="openrouter/some-model", working_dir=work_dir, provider="openrouter"
            )
            self.assertEqual(already._qualified_model, "openrouter/some-model")  # noqa: SLF001
            bare = OpenCodeAgent(model_name="some-model", working_dir=work_dir)
            self.assertEqual(bare._qualified_model, "some-model")  # noqa: SLF001

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

    def test_codex_agent_uses_the_unattended_bypass_flag(self) -> None:
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

        command = agent._build_exec_command("prompt")  # noqa: SLF001
        self.assertIn(CODEX_BYPASS_APPROVALS_AND_SANDBOX_FLAG, command)
        self.assertNotIn("--ignore-user-config", command)
        self.assertFalse(any("approval_policy" in argument for argument in command))
        self.assertFalse(any("sandbox_mode" in argument for argument in command))

    def test_url_discovery_uses_the_role_agent_executable(self) -> None:
        with patch.dict(
                os.environ,
                {
                    "FORGE_SETUP_AGENT": "cdx",
                    "FORGE_SETUP_FAMILY": "codex",
                    "FORGE_SETUP_MODEL": "gpt-5.6-terra",
                },
                clear=True,
        ):
            command = url_fetch_agent_command()
        self.assertTrue(command.startswith("cdx exec "))
        command_tokens = shlex.split(command)
        self.assertIn(CODEX_BYPASS_APPROVALS_AND_SANDBOX_FLAG, command_tokens)
        self.assertNotIn("--ignore-user-config", command_tokens)

    def test_url_discovery_honors_custom_executables_for_every_backend(self) -> None:
        for backend in SUPPORTED_AGENT_BACKENDS:
            executable = f"my-{backend}"
            with self.subTest(backend=backend), patch.dict(
                    os.environ,
                    {
                        "FORGE_SETUP_AGENT": executable,
                        "FORGE_SETUP_FAMILY": backend,
                        "FORGE_SETUP_MODEL": "gpt-5.6-terra",
                    },
                    clear=True,
            ):
                command = url_fetch_agent_command()
            self.assertIn(executable, shlex.split(command))


if __name__ == "__main__":
    unittest.main()
