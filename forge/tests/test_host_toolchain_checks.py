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
    parse_gradle_version,
    parse_grype_version,
)
from utility_scripts.host_requirements import HostRequirements, QueueRequirements


class HostToolchainChecksTests(unittest.TestCase):
    def test_grype_version_parsing(self) -> None:
        self.assertEqual("0.104.0", parse_grype_version("Application: grype\nVersion: 0.104.0\n"))
        self.assertEqual("0.104.0", parse_grype_version("Version: v0.104.0\n"))
        self.assertEqual("9.1.0", parse_gradle_version("\nWelcome to Gradle 9.1.0!\n\nGradle 9.1.0\n"))

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
