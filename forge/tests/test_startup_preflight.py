# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import io
import json
import os
import subprocess
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path
from unittest.mock import Mock, patch

from utility_scripts.startup_preflight import (
    StartupPreflight,
    codex_doctor_provider_status,
    codex_unattended_policy_status,
    graalvm_ea_version_matches,
    java_version_major,
    main,
    parse_ea_release_version,
    parse_graalvm_runtime_version,
    parse_gradle_version,
    parse_grype_version,
    parse_native_image_version,
    pi_approve_supported,
    resolve_queue_requirements,
)


class StartupPreflightTests(unittest.TestCase):
    def test_invalid_queue_limit_prints_one_actionable_fix(self) -> None:
        stdout = io.StringIO()
        stderr = io.StringIO()
        with patch.dict(os.environ, {"FORGE_WORK_LIMIT": "invalid"}, clear=True), \
                redirect_stdout(stdout), redirect_stderr(stderr):
            result = main(["--forge-dir", "/repo/forge"])

        self.assertEqual(1, result)
        self.assertEqual("", stdout.getvalue())
        self.assertIn("FORGE_WORK_LIMIT must be a non-negative integer", stderr.getvalue())
        self.assertEqual(1, stderr.getvalue().count("Fix:"))

    def test_queue_requirements_follow_effective_limits(self) -> None:
        environment = {
            "FORGE_JAVAC_WORK_LIMIT": "0",
            "FORGE_JAVA_RUN_WORK_LIMIT": "0",
            "FORGE_NI_RUN_WORK_LIMIT": "0",
            "FORGE_LIBRARY_UPDATE_WORK_LIMIT": "0",
            "FORGE_WORK_LIMIT": "0",
            "FORGE_REVIEW_LIMIT": "0",
            "FORGE_BULK_UPDATE_REVIEW_LIMIT": "2",
        }

        requirements = resolve_queue_requirements(environment)

        self.assertFalse(requirements.issue_work)
        self.assertTrue(requirements.review_work)

    def test_explicit_review_label_ignores_default_queue_overrides(self) -> None:
        environment = {
            "FORGE_JAVAC_WORK_LIMIT": "0",
            "FORGE_JAVA_RUN_WORK_LIMIT": "0",
            "FORGE_NI_RUN_WORK_LIMIT": "0",
            "FORGE_LIBRARY_UPDATE_WORK_LIMIT": "0",
            "FORGE_WORK_LIMIT": "0",
            "FORGE_REVIEW_LABEL": "library-new-request",
            "FORGE_REVIEW_LIMIT": "0",
            "FORGE_BULK_UPDATE_REVIEW_LIMIT": "2",
        }

        requirements = resolve_queue_requirements(environment)

        self.assertFalse(requirements.issue_work)
        self.assertFalse(requirements.review_work)

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

    def test_java_version_major_supports_current_and_legacy_version_lines(self) -> None:
        self.assertEqual(25, java_version_major('openjdk version "25.0.2" 2026-01-20'))
        self.assertEqual(8, java_version_major('java version "1.8.0_402"'))
        self.assertIsNone(java_version_major("java version unavailable"))

    def test_native_image_and_ea_version_parsing(self) -> None:
        native_output = (
            "native-image 25.0.4 2026-07-21\n"
            "GraalVM Runtime Environment GraalVM CE 25.2.4+7.1 "
            "(build 25.0.4+7-jvmci-25.2-b20)\n"
        )

        self.assertEqual("25.0.4", parse_native_image_version(native_output))
        self.assertEqual("25.2.4+7.1", parse_graalvm_runtime_version(native_output))
        self.assertEqual(
            ("25.3", "25.0.4.1", 2),
            parse_ea_release_version("25i3-25.0.4.1-ea.02"),
        )
        self.assertTrue(graalvm_ea_version_matches(
            "25i3-25.0.4.1-ea.02",
            "25.3.4.1-dev+0.1",
            "25.0.4.1+0-LTS-jvmci-25.3-b21",
        ))
        self.assertFalse(graalvm_ea_version_matches(
            "25i3-25.0.4.1-ea.02",
            "25.3.4.1-dev+0.0",
            "25.0.4.1+0-LTS-jvmci-25.3-b20",
        ))

    def test_grype_version_parsing(self) -> None:
        self.assertEqual("0.104.0", parse_grype_version("Application: grype\nVersion: 0.104.0\n"))
        self.assertEqual("0.104.0", parse_grype_version("Version: v0.104.0\n"))
        self.assertEqual("9.1.0", parse_gradle_version("\nWelcome to Gradle 9.1.0!\n\nGradle 9.1.0\n"))

    @patch("utility_scripts.startup_preflight.run_command")
    def test_pi_approve_support_is_checked_from_help(self, command: Mock) -> None:
        command.return_value = subprocess.CompletedProcess(
            ["pi", "--help"],
            0,
            "Usage: pi [options]\n  --approve  Approve all tool calls\n",
            "",
        )

        self.assertTrue(pi_approve_supported({}))

    def test_pinned_graalvm_25_version_is_recorded_in_repository(self) -> None:
        forge_dir = Path(__file__).resolve().parents[1]
        with (forge_dir / "graalvm-versions.json").open(encoding="utf-8") as version_file:
            version = json.load(version_file)["GRAALVM_HOME_25_0"]

        self.assertRegex(version, r"^25\.0\.\d+$")

    @patch("utility_scripts.startup_preflight.run_command")
    def test_graalvm_check_requires_repository_schema(self, command: Mock) -> None:
        command.return_value = subprocess.CompletedProcess(
            ["native-image", "--version"],
            0,
            "native-image 25.2.4 2026-07-28\n",
            "",
        )
        with tempfile.TemporaryDirectory() as temp_dir:
            bin_dir = Path(temp_dir) / "bin"
            bin_dir.mkdir()
            for executable in (bin_dir / "java", bin_dir / "native-image"):
                executable.touch(mode=0o755)
            preflight = StartupPreflight(
                "/repo/forge",
                "python3",
                "gpt-5.6-terra",
                {"GRAALVM_HOME": temp_dir},
            )

            preflight._check_graalvm_home("GRAALVM_HOME", True, "25.2.4")

        self.assertFalse(preflight.results[-1].passed)
        self.assertIn("schema=missing", preflight.results[-1].detail)

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
        self.assertIn("`danger-full-access` is disallowed", detail)

    def test_github_permission_results_name_each_required_mutation_boundary(self) -> None:
        environment = {
            "FORGE_WORK_LIMIT": "1",
            "FORGE_REVIEW_LIMIT": "1",
        }
        preflight = StartupPreflight("/repo/forge", "python3", "gpt-5.6-terra", environment)
        response = json.dumps({
            "data": {
                "viewer": {
                    "login": "automation-user",
                    "repository": {
                        "nameWithOwner": "automation-user/graalvm-reachability-metadata",
                        "viewerPermission": "ADMIN",
                    },
                },
                "repository": {
                    "nameWithOwner": "oracle/graalvm-reachability-metadata",
                    "viewerPermission": "MAINTAIN",
                },
                "organization": {
                    "projectV2": {
                        "title": "Reachability Metadata",
                        "viewerCanUpdate": True,
                    },
                },
            },
        })

        preflight._record_github_permissions(response)

        result_by_name = {result.name: result for result in preflight.results}
        self.assertTrue(result_by_name["oracle repository mutations"].passed)
        self.assertTrue(result_by_name["oracle project 30 updates"].passed)
        self.assertTrue(result_by_name["generated-branch push target"].passed)

    def test_required_failure_stops_before_work_and_prints_remediation(self) -> None:
        environment = {
            "FORGE_JAVAC_WORK_LIMIT": "0",
            "FORGE_JAVA_RUN_WORK_LIMIT": "0",
            "FORGE_NI_RUN_WORK_LIMIT": "0",
            "FORGE_LIBRARY_UPDATE_WORK_LIMIT": "0",
            "FORGE_WORK_LIMIT": "0",
            "FORGE_REVIEW_LIMIT": "1",
        }
        preflight = StartupPreflight("/repo/forge", "python3", "gpt-5.6-terra", environment)

        def fail_tools() -> None:
            preflight._add(
                "tool",
                "GitHub CLI",
                True,
                False,
                "gh was not found",
                "Install gh.",
            )

        stdout = io.StringIO()
        stderr = io.StringIO()
        with patch.object(preflight, "_check_tools", side_effect=fail_tools), \
                patch.object(preflight, "_check_environment"), \
                patch.object(preflight, "_check_write_permissions"), \
                patch.object(preflight, "_check_network"), \
                patch.object(preflight, "_check_github"), \
                patch.object(preflight, "_check_pi"), \
                patch.object(preflight, "_check_codex"), \
                patch.object(preflight, "_check_docker"), \
                redirect_stdout(stdout), redirect_stderr(stderr):
            passed = preflight.run()

        self.assertFalse(passed)
        self.assertIn("[FAIL] tool: GitHub CLI", stdout.getvalue())
        self.assertIn("Fix: Install gh.", stdout.getvalue())
        self.assertIn("No work was started", stdout.getvalue())
        self.assertEqual("", stderr.getvalue())

    @patch("utility_scripts.startup_preflight.resolve_executable", return_value="/usr/bin/docker")
    @patch("utility_scripts.startup_preflight.run_command")
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
        preflight = StartupPreflight("/repo/forge", "python3", "gpt-5.6-terra", {"FORGE_WORK_LIMIT": "1"})

        preflight._check_docker()

        command.assert_called_once_with(["docker", "info"], preflight.environment)
        self.assertTrue(preflight.results[-1].passed)

    def test_worker_runs_preflight_before_first_cycle(self) -> None:
        worker_path = Path(__file__).resolve().parents[1] / "do_up_to_date_work.sh"
        worker = worker_path.read_text(encoding="utf-8")
        startup = worker.rindex("\nrun_startup_preflight\n")
        first_cycle = worker.rindex("\nrun_cycle\n")

        self.assertLess(startup, first_cycle)


if __name__ == "__main__":
    unittest.main()
