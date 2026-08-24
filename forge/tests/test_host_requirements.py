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
from contextlib import contextmanager, redirect_stderr, redirect_stdout
from pathlib import Path
from typing import Iterator
from unittest.mock import Mock, patch

from utility_scripts.host_requirements import (
    GRAALVM_SCHEMA_PATH,
    HostRequirements,
    QueueRequirements,
    check_graalvm_installation,
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
    probe_proxied_host,
    resolve_graalvm_version_check,
    resolve_https_proxy,
    resolve_queue_requirements,
)


@contextmanager
def _graalvm_home(native_image: bool = True, schema: bool = True) -> Iterator[str]:
    """Create a GraalVM home whose Native Image and schema presence can be controlled."""
    with tempfile.TemporaryDirectory() as temp_dir:
        executables = ["java", "native-image"] if native_image else ["java"]
        bin_dir = Path(temp_dir) / "bin"
        bin_dir.mkdir()
        for executable in executables:
            (bin_dir / executable).touch(mode=0o755)
        if schema:
            schema_path = Path(temp_dir) / GRAALVM_SCHEMA_PATH
            schema_path.parent.mkdir(parents=True)
            schema_path.touch()
        yield temp_dir


class HostRequirementsTests(unittest.TestCase):
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
        self.assertTrue(graalvm_ea_version_matches(
            "25i3-25.0.4.1-ea.03",
            "25.3.4.1-dev+0.1",
            "25.0.4.1+0-LTS-jvmci-25.3-b21",
        ))

    def test_grype_version_parsing(self) -> None:
        self.assertEqual("0.104.0", parse_grype_version("Application: grype\nVersion: 0.104.0\n"))
        self.assertEqual("0.104.0", parse_grype_version("Version: v0.104.0\n"))
        self.assertEqual("9.1.0", parse_gradle_version("\nWelcome to Gradle 9.1.0!\n\nGradle 9.1.0\n"))

    @patch("utility_scripts.host_requirements.run_command")
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

    @patch("utility_scripts.host_requirements.run_command")
    def test_graalvm_check_requires_repository_schema(self, command: Mock) -> None:
        command.return_value = subprocess.CompletedProcess(
            ["native-image", "--version"],
            0,
            "native-image 25.2.4 2026-07-28\n",
            "",
        )
        with _graalvm_home(schema=False) as graalvm_home:
            host_requirements = HostRequirements(
                "/repo/forge",
                "python3",
                "gpt-5.6-terra",
                {"GRAALVM_HOME": graalvm_home},
            )

            host_requirements._check_graalvm_home("GRAALVM_HOME", True, "25.2.4")

        self.assertFalse(host_requirements.results[-1].passed)
        self.assertIn(GRAALVM_SCHEMA_PATH, host_requirements.results[-1].detail)
        self.assertIn("is missing", host_requirements.results[-1].detail)

    @patch("utility_scripts.host_requirements.run_command")
    def test_strict_version_check_stops_work_on_a_mismatched_graalvm(self, command: Mock) -> None:
        command.return_value = subprocess.CompletedProcess(
            ["native-image", "--version"],
            0,
            "native-image 25.2.4 2026-07-28\n"
            "GraalVM Runtime Environment GraalVM CE 25.2.4+7.1 (build 25.0.4+7-jvmci-25.2-b20)\n",
            "",
        )
        with _graalvm_home() as graalvm_home:
            host_requirements = HostRequirements(
                "/repo/forge",
                "python3",
                "gpt-5.6-terra",
                {"GRAALVM_HOME": graalvm_home},
                graalvm_version_check="strict",
            )

            host_requirements._check_graalvm_home("GRAALVM_HOME", True, "25.3.0")

        installation, version = host_requirements.results
        self.assertEqual("PASS", installation.status)
        self.assertEqual("FAIL", version.status)
        self.assertTrue(version.blocks_work)

    @patch("utility_scripts.host_requirements.run_command")
    def test_warn_version_check_keeps_native_image_and_schema_mandatory(self, command: Mock) -> None:
        command.return_value = subprocess.CompletedProcess(
            ["native-image", "--version"],
            0,
            "native-image 25.2.4 2026-07-28\n"
            "GraalVM Runtime Environment GraalVM CE 25.2.4+7.1 (build 25.0.4+7-jvmci-25.2-b20)\n",
            "",
        )
        with _graalvm_home() as patched_graalvm, _graalvm_home(native_image=False) as broken_graalvm:
            host_requirements = HostRequirements(
                "/repo/forge",
                "python3",
                "gpt-5.6-terra",
                {"GRAALVM_HOME": patched_graalvm, "GRAALVM_HOME_25_0": broken_graalvm},
                graalvm_version_check="warn",
            )

            host_requirements._check_graalvm_home("GRAALVM_HOME", True, "25.3.0")
            host_requirements._check_graalvm_home("GRAALVM_HOME_25_0", True, "25.0.4")

        mismatched_version = host_requirements.results[1]
        self.assertEqual("WARN", mismatched_version.status)
        self.assertFalse(mismatched_version.blocks_work)
        self.assertTrue(host_requirements.results[2].blocks_work)

    def test_disabled_version_check_skips_version_resolution_and_comparison(self) -> None:
        with _graalvm_home() as graalvm_home:
            host_requirements = HostRequirements(
                "/repo/forge",
                "python3",
                "gpt-5.6-terra",
                {"GRAALVM_HOME": graalvm_home, "FORGE_WORK_LIMIT": "1"},
                graalvm_version_check="off",
            )

            with patch("utility_scripts.host_requirements.run_command") as command:
                host_requirements._check_graalvm_home("GRAALVM_HOME", True, None)

        command.assert_not_called()
        installation, version = host_requirements.results
        self.assertEqual("PASS", installation.status)
        self.assertEqual("SKIP", version.status)
        self.assertIn("version match disabled", version.detail)

    def test_version_check_mode_is_read_from_the_environment_and_validated(self) -> None:
        self.assertEqual("warn", resolve_graalvm_version_check(None, {"FORGE_GRAALVM_VERSION_CHECK": "warn"}))
        self.assertEqual("off", resolve_graalvm_version_check("off", {"FORGE_GRAALVM_VERSION_CHECK": "strict"}))
        self.assertEqual("strict", resolve_graalvm_version_check(None, {}))
        with self.assertRaises(ValueError):
            resolve_graalvm_version_check("lenient", {})

    def test_graalvm_installation_problems_are_named_once(self) -> None:
        with _graalvm_home(native_image=False, schema=False) as graalvm_home:
            problems = check_graalvm_installation(graalvm_home)

        self.assertEqual(
            [
                os.path.join("bin", "native-image") + " is missing or not executable",
                f"{GRAALVM_SCHEMA_PATH} is missing",
            ],
            problems,
        )

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
            "review-model",
            requirements=QueueRequirements(issue_work=False, review_work=True),
            analysis_agent="claude",
            analysis_family="claude-code",
            analysis_model="claude-opus-4-1",
            test_agent="opencode",
            test_model="anthropic/claude-sonnet-4-5",
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
            "review-model",
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

    def test_github_permission_results_name_each_required_mutation_boundary(self) -> None:
        environment = {
            "FORGE_WORK_LIMIT": "1",
            "FORGE_REVIEW_LIMIT": "1",
        }
        host_requirements = HostRequirements("/repo/forge", "python3", "gpt-5.6-terra", environment)
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

        host_requirements._record_github_permissions(response)

        result_by_name = {result.name: result for result in host_requirements.results}
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
        host_requirements = HostRequirements("/repo/forge", "python3", "gpt-5.6-terra", environment)

        def fail_tools() -> None:
            host_requirements._add(
                "tool",
                "GitHub CLI",
                True,
                False,
                "gh was not found",
                "Install gh.",
            )

        stdout = io.StringIO()
        stderr = io.StringIO()
        with patch.object(host_requirements, "_check_tools", side_effect=fail_tools), \
                patch.object(host_requirements, "_check_environment"), \
                patch.object(host_requirements, "_check_write_permissions"), \
                patch.object(host_requirements, "_check_network"), \
                patch.object(host_requirements, "_check_github"), \
                patch.object(host_requirements, "_check_pi"), \
                patch.object(host_requirements, "_check_codex"), \
                patch.object(host_requirements, "_check_docker"), \
                redirect_stdout(stdout), redirect_stderr(stderr):
            passed = host_requirements.run()

        self.assertFalse(passed)
        self.assertIn("[FAIL] tool: GitHub CLI", stdout.getvalue())
        self.assertIn("Fix: Install gh.", stdout.getvalue())
        self.assertIn("No work was started", stdout.getvalue())
        self.assertEqual("", stderr.getvalue())

    @patch("utility_scripts.host_requirements.resolve_executable", return_value="/usr/bin/docker")
    @patch("utility_scripts.host_requirements.run_command")
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
        host_requirements = HostRequirements("/repo/forge", "python3", "gpt-5.6-terra", {"FORGE_WORK_LIMIT": "1"})

        host_requirements._check_docker()

        command.assert_called_once_with(["docker", "info"], host_requirements.environment)
        self.assertTrue(host_requirements.results[-1].passed)

    def test_runs_without_live_github_skip_github_permissions_and_api_hosts(self) -> None:
        host_requirements = HostRequirements(
            "/repo/forge",
            "python3",
            "gpt-5.6-terra",
            {},
            requirements=QueueRequirements(issue_work=True, review_work=False, github_work=False),
        )

        with patch("utility_scripts.host_requirements.probe_tcp_host", return_value=(True, "reachable")), \
                patch.object(host_requirements, "_check_git_remote_access"):
            host_requirements._check_github()
            host_requirements._check_network()

        result_by_name = {result.name: result for result in host_requirements.results}
        self.assertEqual("SKIP", result_by_name["authentication and permissions"].status)
        self.assertEqual("SKIP", result_by_name["github.com"].status)
        self.assertEqual("SKIP", result_by_name["api.github.com"].status)
        self.assertEqual("PASS", result_by_name["chatgpt.com"].status)

    def test_selected_repository_paths_are_checked_instead_of_the_forge_parent_checkout(self) -> None:
        with tempfile.TemporaryDirectory() as target_repo:
            gradlew = Path(target_repo) / "gradlew"
            gradlew.touch(mode=0o755)
            host_requirements = HostRequirements(
                "/parent/forge",
                "python3",
                "gpt-5.6-terra",
                {},
                requirements=QueueRequirements(issue_work=True, review_work=False),
                repo_dir=target_repo,
            )

            with patch("utility_scripts.host_requirements.run_command") as command:
                command.return_value = subprocess.CompletedProcess(["gradlew"], 0, "Gradle 8.14\n", "")
                host_requirements._check_gradle_wrapper(True)
                host_requirements._check_write_permissions()
                host_requirements._check_git_remote_access()

        result_by_name = {result.name: result for result in host_requirements.results}
        self.assertIn(str(gradlew), result_by_name["Gradle wrapper"].detail)
        self.assertIn("/parent/.git", result_by_name["Forge git metadata"].detail)
        self.assertIn(
            os.path.join(target_repo, ".git"),
            result_by_name["Selected repository git metadata"].detail,
        )
        self.assertTrue(result_by_name["Selected repository git metadata"].required)
        self.assertIn(f"checkout={target_repo}", result_by_name["Selected repository git remote"].detail)
        self.assertIn("checkout=/parent/forge", result_by_name["Forge git self-update"].detail)

    def test_selected_repository_checks_collapse_when_forge_owns_the_checkout(self) -> None:
        host_requirements = HostRequirements(
            "/repo/forge",
            "python3",
            "gpt-5.6-terra",
            {},
            requirements=QueueRequirements(issue_work=False, review_work=True),
        )

        with patch("utility_scripts.host_requirements.run_command") as command:
            command.return_value = subprocess.CompletedProcess(["git"], 0, "origin\n", "")
            host_requirements._check_write_permissions()
            host_requirements._check_git_remote_access()

        result_by_name = {result.name: result for result in host_requirements.results}
        self.assertEqual("SKIP", result_by_name["Selected repository git metadata"].status)
        self.assertEqual("SKIP", result_by_name["Selected repository git remote"].status)
        self.assertIn("checkout that contains Forge", result_by_name["Selected repository git remote"].detail)

    def test_network_probes_follow_the_configured_proxy(self) -> None:
        proxied = {"https_proxy": "http://10.0.0.1:80", "no_proxy": "localhost,.internal.example"}

        self.assertEqual(("10.0.0.1", 80), resolve_https_proxy(proxied, "github.com"))
        self.assertIsNone(resolve_https_proxy(proxied, "build.internal.example"))
        self.assertIsNone(resolve_https_proxy({}, "github.com"))
        self.assertEqual(("proxy.example", 80), resolve_https_proxy({"HTTPS_PROXY": "proxy.example"}, "github.com"))

    def test_proxy_tunnel_result_reports_both_hops(self) -> None:
        connection = Mock()
        connection.__enter__ = Mock(return_value=connection)
        connection.__exit__ = Mock(return_value=False)
        connection.recv.return_value = b"HTTP/1.1 200 Connection established\r\n\r\n"

        with patch("utility_scripts.host_requirements.socket.create_connection", return_value=connection):
            passed, detail = probe_proxied_host("github.com", 443, ("10.0.0.1", 80))

        self.assertTrue(passed)
        self.assertIn("via proxy 10.0.0.1:80", detail)
        connection.sendall.assert_called_once_with(
            b"CONNECT github.com:443 HTTP/1.1\r\nHost: github.com:443\r\n\r\n"
        )

    def test_proxy_refusal_is_reported_as_unreachable(self) -> None:
        connection = Mock()
        connection.__enter__ = Mock(return_value=connection)
        connection.__exit__ = Mock(return_value=False)
        connection.recv.return_value = b"HTTP/1.1 403 Forbidden\r\n\r\n"

        with patch("utility_scripts.host_requirements.socket.create_connection", return_value=connection):
            passed, detail = probe_proxied_host("registry-1.docker.io", 443, ("10.0.0.1", 80))

        self.assertFalse(passed)
        self.assertIn("403 Forbidden", detail)

    def test_worker_validates_host_requirements_before_first_cycle(self) -> None:
        worker_path = Path(__file__).resolve().parents[1] / "do_up_to_date_work.sh"
        worker = worker_path.read_text(encoding="utf-8")
        startup = worker.rindex("\nrun_host_requirements\n")
        first_cycle = worker.rindex("\nrun_cycle\n")

        self.assertLess(startup, first_cycle)

    def test_worker_propagates_role_specific_agent_families(self) -> None:
        worker_path = Path(__file__).resolve().parents[1] / "do_up_to_date_work.sh"
        with tempfile.TemporaryDirectory() as temp_dir:
            fake_python = os.path.join(temp_dir, "python3")
            args_path = os.path.join(temp_dir, "args.txt")
            with open(fake_python, "w", encoding="utf-8") as output_file:
                output_file.write(
                    "#!/usr/bin/env bash\n"
                    "printf '%s\\n' \"$@\" > \"$FORGE_TEST_ARGS_FILE\"\n"
                    "exit 1\n"
                )
            os.chmod(fake_python, 0o755)
            environment = dict(os.environ)
            for variable in (
                    "FORGE_AGENT_FAMILY",
                    "FORGE_ANALYSIS_AGENT",
                    "FORGE_ANALYSIS_FAMILY",
                    "FORGE_ANALYSIS_MODEL",
                    "FORGE_TEST_AGENT",
                    "FORGE_TEST_FAMILY",
                    "FORGE_TEST_MODEL",
                    "FORGE_REVIEW_MODEL",
            ):
                environment.pop(variable, None)
            environment.update({
                "PYTHON_BIN": fake_python,
                "FORGE_TEST_ARGS_FILE": args_path,
            })

            result = subprocess.run(
                [
                    str(worker_path), "--once",
                    "--analysis-agent", "cdx",
                    "--analysis-family", "codex",
                    "--test-agent", "pi",
                    "--test-family", "pi",
                ],
                env=environment,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                check=False,
            )
            with open(args_path, encoding="utf-8") as input_file:
                arguments = input_file.read().splitlines()

        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(arguments[arguments.index("--analysis-agent") + 1], "cdx")
        self.assertEqual(arguments[arguments.index("--analysis-family") + 1], "codex")
        self.assertEqual(arguments[arguments.index("--analysis-model") + 1], "gpt-5.6-luna")
        self.assertEqual(arguments[arguments.index("--test-agent") + 1], "pi")
        self.assertEqual(arguments[arguments.index("--test-family") + 1], "pi")


if __name__ == "__main__":
    unittest.main()
