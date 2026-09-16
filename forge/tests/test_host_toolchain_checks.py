# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import json
import os
import subprocess
import tempfile
import unittest
from unittest.mock import Mock, patch

from utility_scripts.host_probes import (
    codex_doctor_provider_status,
    codex_unattended_policy_status,
    parse_gradle_version,
    parse_grype_version,
)
from utility_scripts.host_requirements import HostRequirements, QueueRequirements


class HostToolchainChecksTests(unittest.TestCase):
    def test_codex_doctor_provider_status_requires_reachable_provider(self) -> None:
        output = json.dumps({
            "checks": {
                "network.provider_reachability": {
                    "status": "fail",
                    "summary": "provider endpoint is unreachable",
                },
            },
        })

        passed, detail = codex_doctor_provider_status(output)

        self.assertFalse(passed)
        self.assertIn("unreachable", detail)

    def test_grype_version_parsing(self) -> None:
        self.assertEqual("0.104.0", parse_grype_version("Application: grype\nVersion: 0.104.0\n"))
        self.assertEqual("0.104.0", parse_grype_version("Version: v0.104.0\n"))
        self.assertEqual("9.1.0", parse_gradle_version("\nWelcome to Gradle 9.1.0!\n\nGradle 9.1.0\n"))

    def test_codex_managed_policy_rejects_unattended_recovery(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            bundle_path = os.path.join(temp_dir, "cloud-config-bundle-cache.json")
            with open(bundle_path, "w", encoding="utf-8") as bundle_file:
                json.dump({
                    "requirements_toml": {
                        "enterprise_managed": [{
                            "name": "Codex Developers",
                            "contents": (
                                'allowed_approval_policies = ["on-request", "untrusted"]\n'
                                'allowed_sandbox_modes = ["read-only", "workspace-write"]\n'
                            ),
                        }],
                    },
                }, bundle_file)

            passed, detail = codex_unattended_policy_status(temp_dir)

        self.assertFalse(passed)
        self.assertIn("Codex Developers", detail)
        self.assertIn("`never` is disallowed", detail)
        self.assertNotIn("`workspace-write` is disallowed", detail)

    def test_only_selected_agent_executables_are_required(self) -> None:
        host_requirements = HostRequirements(
            "/repo/forge",
            "python3",
            requirements=QueueRequirements(issue_work=False, review_work=True),
            analysis_agent="claude",
            analysis_family="claude-code",
            analysis_model="claude-opus-4-1",
        )
        with patch.object(host_requirements, "_check_tool") as check_tool, \
                patch.object(host_requirements, "_check_grype"), \
                patch.object(host_requirements, "_check_gradle_wrapper"):
            host_requirements._check_tools()

        required_agent_checks = [
            call_args.args[1]
            for call_args in check_tool.call_args_list
            if call_args.args[0].startswith("Agent backend") and call_args.args[2]
        ]
        self.assertEqual(required_agent_checks, ["claude"])

    def test_role_agent_family_and_command_are_checked_independently(self) -> None:
        host_requirements = HostRequirements(
            "/repo/forge",
            "python3",
            requirements=QueueRequirements(issue_work=False, review_work=True),
            analysis_agent="cdx",
            analysis_family="codex",
            analysis_model="gpt-5.6-terra",
        )
        with patch.object(host_requirements, "_check_tool") as check_tool, \
                patch.object(host_requirements, "_check_grype"), \
                patch.object(host_requirements, "_check_gradle_wrapper"):
            host_requirements._check_tools()

        required_agent_checks = [
            call_args.args[1]
            for call_args in check_tool.call_args_list
            if call_args.args[0].startswith("Agent backend") and call_args.args[2]
        ]
        self.assertEqual(required_agent_checks, ["cdx"])

    def test_setup_role_gets_every_host_capability_check(self) -> None:
        environment = {"HOME": "/operator"}
        host_requirements = HostRequirements(
            "/repo/forge",
            "python3",
            environment=environment,
            requirements=QueueRequirements(
                issue_work=True,
                review_work=False,
                github_work=False,
            ),
            analysis_family="codex",
            analysis_agent="codex",
            setup_family="opencode",
            setup_agent="setup-code",
            setup_model="setup-model",
            setup_provider="openrouter",
        )

        with patch.object(host_requirements, "_check_tool") as check_tool, \
                patch.object(host_requirements, "_check_grype"), \
                patch.object(host_requirements, "_check_gradle_wrapper"):
            host_requirements._check_tools()

        required_agent_commands = {
            call_args.args[1]
            for call_args in check_tool.call_args_list
            if call_args.args[0].startswith("Agent backend") and call_args.args[2]
        }
        self.assertIn("setup-code", required_agent_commands)

        with patch(
                "utility_scripts.host_access_checks.probe_write_access",
                return_value=(True, "writable"),
        ), patch.object(
            host_requirements,
            "_check_git_remote_access",
        ), patch(
            "utility_scripts.host_access_checks.probe_tcp_host",
            return_value=(True, "reachable"),
        ) as probe_host, patch(
            "utility_scripts.host_access_checks.probe_http_200",
            return_value=(True, "HTTP 200"),
        ):
            host_requirements._check_write_permissions()
            host_requirements._check_network()

        result_by_name = {result.name: result for result in host_requirements.results}
        self.assertIn("opencode state", result_by_name)
        self.assertTrue(result_by_name["opencode state"].required)
        self.assertIn("/operator/.local/share/opencode", result_by_name["opencode state"].remediation)
        probed_hosts = [call.args[0] for call in probe_host.call_args_list]
        self.assertIn("openrouter.ai", probed_hosts)

        with patch(
                "utility_scripts.host_toolchain_checks.resolve_executable",
                side_effect=lambda executable: f"/bin/{executable}",
        ), patch.object(
            host_requirements,
            "_selected_agent_authentication",
            return_value=(True, "ready"),
        ) as authenticate:
            host_requirements._check_selected_agents()

        self.assertIn(
            ("opencode", "setup-code", "setup-model", "openrouter"),
            [call.args for call in authenticate.call_args_list],
        )

    def test_all_enabled_strategy_agents_are_validated(self) -> None:
        host_requirements = HostRequirements(
            "/repo/forge",
            "python3",
            environment={},
            requirements=QueueRequirements(issue_work=True, review_work=False),
            test_strategy_names=[
                "dynamic_access_main_sources_pi_gpt-5.6-sol",
                "dynamic_access_main_sources_codex_gpt-5.6-sol",
            ],
        )

        self.assertEqual(
            {requirement.family for requirement in host_requirements.test_requirements},
            {"pi", "codex"},
        )
        with patch(
                "utility_scripts.host_toolchain_checks.resolve_executable",
                side_effect=lambda executable: f"/bin/{executable}",
        ), patch.object(
            host_requirements,
            "_selected_agent_authentication",
            return_value=(True, "ready"),
        ) as authenticate:
            host_requirements._check_selected_agents()

        authenticated_families = {call.args[0] for call in authenticate.call_args_list}
        self.assertEqual(authenticated_families, {"codex", "pi"})

    @patch("utility_scripts.host_toolchain_checks.resolve_executable", return_value="/usr/bin/docker")
    @patch("utility_scripts.host_toolchain_checks.run_command")
    def test_docker_daemon_check_does_not_require_docker_specific_template_fields(
            self,
            command: Mock,
            _resolve: Mock,
    ) -> None:
        command.return_value = subprocess.CompletedProcess(
            ["docker", "info"],
            0,
            "host:\n  arch: amd64\n",
            "",
        )
        host_requirements = HostRequirements("/repo/forge", "python3", {"FORGE_WORK_LIMIT": "1"})

        host_requirements._check_docker()

        command.assert_called_once_with(["docker", "info"], host_requirements.environment)
        self.assertTrue(host_requirements.results[-1].passed)


if __name__ == "__main__":
    unittest.main()
