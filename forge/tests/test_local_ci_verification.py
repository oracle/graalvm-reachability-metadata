# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import json
import os
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from utility_scripts.local_ci_verification import (
    CommandRecord,
    LOCAL_CI_VERIFICATION_KEY,
    LocalCIVerificationResult,
    classify_repo_fix_paths,
    fetch_pr_base_ref,
    format_local_ci_verification_pr_section,
    local_ci_requires_human_intervention,
    run_local_ci_verification,
    _github_repo_slug_from_url,
    _graalvm_home_for_java_version,
    _run_infrastructure_matrix_entries,
    _run_recorded_command,
    _run_spring_aot_matrix_entries,
    _run_test_matrix_entries,
    _run_verification_once,
)
from utility_scripts.metrics_writer import PENDING_METRICS_FILENAME


def _git(repo_path: str, *args: str) -> str:
    result = subprocess.run(
        ["git", *args],
        cwd=repo_path,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=True,
    )
    return result.stdout.strip()


def _commit_all(repo_path: str, message: str) -> str:
    _git(repo_path, "add", "-A")
    _git(repo_path, "-c", "user.name=Forge Test", "-c", "user.email=forge@example.com", "commit", "-m", message)
    return _git(repo_path, "rev-parse", "HEAD")


class LocalCIVerificationTests(unittest.TestCase):
    def test_fetch_pr_base_ref_falls_back_to_target_repo_when_origin_is_a_fork(self) -> None:
        commands: list[list[str]] = []

        def fake_run(
                command: list[str],
                cwd: str | None = None,
                stdout=None,
                stderr=None,
                text: bool | None = None,
                check: bool | None = None,
        ) -> subprocess.CompletedProcess[str]:
            del cwd, stdout, stderr, text, check
            commands.append(command)
            if command == ["git", "remote", "get-url", "upstream"]:
                return subprocess.CompletedProcess(command, 1, stdout="")
            if command == ["git", "remote", "get-url", "origin"]:
                return subprocess.CompletedProcess(command, 0, stdout="git@github.com:contributor/fork.git\n")
            if command[:2] == ["git", "fetch"]:
                return subprocess.CompletedProcess(command, 0, stdout="")
            raise AssertionError(f"unexpected command: {command}")

        with patch("utility_scripts.local_ci_verification.subprocess.run", side_effect=fake_run):
            base_ref = fetch_pr_base_ref("/repo", "oracle/graalvm-reachability-metadata")

        self.assertEqual(base_ref, "FETCH_HEAD")
        self.assertIn(
            ["git", "fetch", "https://github.com/oracle/graalvm-reachability-metadata.git", "master"],
            commands,
        )

    def test_fetch_pr_base_ref_uses_origin_only_when_origin_matches_target_repo(self) -> None:
        commands: list[list[str]] = []

        def fake_run(
                command: list[str],
                cwd: str | None = None,
                stdout=None,
                stderr=None,
                text: bool | None = None,
                check: bool | None = None,
        ) -> subprocess.CompletedProcess[str]:
            del cwd, stdout, stderr, text, check
            commands.append(command)
            if command == ["git", "remote", "get-url", "upstream"]:
                return subprocess.CompletedProcess(command, 1, stdout="")
            if command == ["git", "remote", "get-url", "origin"]:
                return subprocess.CompletedProcess(
                    command,
                    0,
                    stdout="git@github.com:oracle/graalvm-reachability-metadata.git\n",
                )
            if command[:2] == ["git", "fetch"]:
                return subprocess.CompletedProcess(command, 0, stdout="")
            raise AssertionError(f"unexpected command: {command}")

        with patch("utility_scripts.local_ci_verification.subprocess.run", side_effect=fake_run):
            base_ref = fetch_pr_base_ref("/repo", "oracle/graalvm-reachability-metadata")

        self.assertEqual(base_ref, "origin/master")
        self.assertIn(["git", "fetch", "origin", "master"], commands)

    def test_classify_repo_fix_paths_flags_only_files_outside_target_library_scope(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path:
            _git(repo_path, "init")
            os.makedirs(os.path.join(repo_path, "metadata", "org.example", "demo", "1.0.0"))
            os.makedirs(os.path.join(repo_path, "tests", "src", "org.example", "demo", "1.0.0"))
            os.makedirs(os.path.join(repo_path, "stats", "org.example", "demo", "1.0.0"))
            with open(os.path.join(repo_path, "README.md"), "w", encoding="utf-8") as file:
                file.write("base\n")
            base = _commit_all(repo_path, "base")

            with open(
                    os.path.join(repo_path, "metadata", "org.example", "demo", "1.0.0", "reachability-metadata.json"),
                    "w",
                    encoding="utf-8",
            ) as file:
                file.write("{}\n")
            with open(os.path.join(repo_path, "tests", "src", "org.example", "demo", "1.0.0", "build.gradle"), "w", encoding="utf-8") as file:
                file.write("plugins {}\n")
            with open(os.path.join(repo_path, "stats", "org.example", "demo", "1.0.0", "stats.json"), "w", encoding="utf-8") as file:
                file.write("{}\n")
            with open(os.path.join(repo_path, "build.gradle"), "w", encoding="utf-8") as file:
                file.write("tasks.register('repoFix')\n")
            _commit_all(repo_path, "generated support with repo fix")

            self.assertEqual(
                classify_repo_fix_paths(repo_path, base, "org.example:demo:1.0.0"),
                ["build.gradle"],
            )

    def test_successful_verification_records_metrics_and_human_intervention_requirement(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path, tempfile.TemporaryDirectory() as metrics_path:
            _git(repo_path, "init")
            with open(os.path.join(repo_path, "README.md"), "w", encoding="utf-8") as file:
                file.write("base\n")
            base = _commit_all(repo_path, "base")
            with open(os.path.join(repo_path, "build.gradle"), "w", encoding="utf-8") as file:
                file.write("tasks.register('repoFix')\n")
            _commit_all(repo_path, "repo fix")
            pending_metrics = {
                "library": "org.example:demo:1.0.0",
                "status": "success",
                "metrics": {},
            }
            with open(os.path.join(metrics_path, PENDING_METRICS_FILENAME), "w", encoding="utf-8") as file:
                json.dump(pending_metrics, file)

            with patch("utility_scripts.local_ci_verification._run_verification_once", return_value=None):
                result = run_local_ci_verification(
                    repo_path=repo_path,
                    coordinates="org.example:demo:1.0.0",
                    base_commit=base,
                    metrics_repo_path=metrics_path,
                )

            self.assertTrue(result.human_intervention_required)
            self.assertEqual(result.repo_fix_paths, ["build.gradle"])
            with open(os.path.join(metrics_path, PENDING_METRICS_FILENAME), "r", encoding="utf-8") as file:
                updated_metrics = json.load(file)
            self.assertTrue(updated_metrics[LOCAL_CI_VERIFICATION_KEY]["human_intervention_required"])
            self.assertEqual(updated_metrics[LOCAL_CI_VERIFICATION_KEY]["repo_fix_paths"], ["build.gradle"])

    def test_pr_section_mentions_repo_fix_paths(self) -> None:
        metrics = {
            "status": "success",
            "commands": [{"gate": "doc-links"}],
            "fixups": [{"gate": "doc-links"}],
            "human_intervention_required": True,
            "repo_fix_paths": ["build.gradle"],
        }

        section = format_local_ci_verification_pr_section(metrics)

        self.assertTrue(local_ci_requires_human_intervention(metrics))
        self.assertIn("Local CI Verification", section)
        self.assertIn("build.gradle", section)

    def test_run_recorded_command_fails_on_ci_failure_output_pattern(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path, tempfile.TemporaryDirectory() as log_path:
            result = LocalCIVerificationResult(status="running", base_commit="base")
            command = ["python3", "-c", "print('FAILED[javaTest][1.0.0][./gradlew test]')"]
            with patch(
                    "utility_scripts.local_ci_verification.build_timestamped_task_log_path",
                    return_value=os.path.join(log_path, "command.log"),
            ):
                failed = _run_recorded_command(
                    repo_path,
                    "run-consecutive-tests",
                    command,
                    result,
                    failure_output_pattern=r"^FAILED",
                )

            self.assertIsNotNone(failed)
            self.assertEqual(result.commands[0].returncode, 1)
            self.assertIn("FAILED[javaTest]", result.commands[0].output_excerpt)

    def test_test_matrix_skips_docker_networking_for_non_docker_coordinate(self) -> None:
        result = LocalCIVerificationResult(status="running", base_commit="base")
        failed_record = CommandRecord(gate="run-consecutive-tests", command=["bash"], returncode=1)
        gates: list[str] = []

        def fake_run_recorded_command(
                repo_path: str,
                gate: str,
                command: list[str],
                local_result: LocalCIVerificationResult,
                env: dict[str, str] | None = None,
                failure_output_pattern: str | None = None,
        ) -> CommandRecord | None:
            gates.append(gate)
            if gate == "run-consecutive-tests":
                self.assertEqual(failure_output_pattern, r"^FAILED")
                return failed_record
            return None

        with patch("utility_scripts.local_ci_verification._run_recorded_command", side_effect=fake_run_recorded_command):
            failed = _run_test_matrix_entries(
                "/repo",
                [{"coordinates": "org.example:demo:1.0.0", "versions": ["1.0.0"]}],
                result,
            )

        self.assertIs(failed, failed_record)
        self.assertEqual(gates, ["check-metadata-files", "run-consecutive-tests"])
        self.assertNotIn("disable-docker-networking", gates)
        self.assertNotIn("restore-docker-networking", gates)

    def test_test_matrix_isolates_docker_networking_for_coordinates_that_declare_docker_images(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path:
            docker_test_dir = os.path.join(repo_path, "tests", "src", "org.example", "docker-demo", "1.0.0")
            os.makedirs(docker_test_dir)
            with open(os.path.join(docker_test_dir, "required-docker-images.txt"), "w", encoding="utf-8") as file:
                file.write("nginx:1-alpine-slim\n")

            result = LocalCIVerificationResult(status="running", base_commit="base")
            calls: list[tuple[str, list[str]]] = []

            def fake_run_recorded_command(
                    repo_path_arg: str,
                    gate: str,
                    command: list[str],
                    local_result: LocalCIVerificationResult,
                    env: dict[str, str] | None = None,
                    failure_output_pattern: str | None = None,
            ) -> CommandRecord | None:
                del repo_path_arg, local_result, env, failure_output_pattern
                calls.append((gate, command))
                return None

            with patch("utility_scripts.local_ci_verification._run_recorded_command", side_effect=fake_run_recorded_command):
                failed = _run_test_matrix_entries(
                    repo_path,
                    [
                        {"coordinates": "org.example:plain-demo:1.0.0", "versions": ["1.0.0"]},
                        {"coordinates": "org.example:docker-demo:1.0.0", "versions": ["1.0.0"]},
                    ],
                    result,
                )

            self.assertIsNone(failed)
            self.assertEqual(
                [call for call in calls if call[0] == "pull-allowed-docker-images"],
                [
                    (
                        "pull-allowed-docker-images",
                        ["./gradlew", "pullAllowedDockerImages", "-Pcoordinates=org.example:docker-demo:1.0.0"],
                    ),
                ],
            )
            self.assertIn("disable-docker-networking", [call[0] for call in calls])
            self.assertEqual(calls[-1][0], "restore-docker-networking")

    def test_test_matrix_resolves_docker_declaration_from_index_test_version(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path:
            metadata_dir = os.path.join(repo_path, "metadata", "org.example", "demo")
            docker_test_dir = os.path.join(repo_path, "tests", "src", "org.example", "demo", "1.0.0")
            os.makedirs(metadata_dir)
            os.makedirs(docker_test_dir)
            with open(os.path.join(metadata_dir, "index.json"), "w", encoding="utf-8") as file:
                json.dump([
                    {
                        "metadata-version": "2.0.0",
                        "test-version": "1.0.0",
                        "tested-versions": ["2.0.1"],
                    },
                ], file)
            with open(os.path.join(docker_test_dir, "required-docker-images.txt"), "w", encoding="utf-8") as file:
                file.write("# comment\n\nnginx:1-alpine-slim\n")

            result = LocalCIVerificationResult(status="running", base_commit="base")
            calls: list[tuple[str, list[str]]] = []

            def fake_run_recorded_command(
                    repo_path_arg: str,
                    gate: str,
                    command: list[str],
                    local_result: LocalCIVerificationResult,
                    env: dict[str, str] | None = None,
                    failure_output_pattern: str | None = None,
            ) -> CommandRecord | None:
                del repo_path_arg, local_result, env, failure_output_pattern
                calls.append((gate, command))
                return None

            with patch("utility_scripts.local_ci_verification._run_recorded_command", side_effect=fake_run_recorded_command):
                failed = _run_test_matrix_entries(
                    repo_path,
                    [{"coordinates": "org.example:demo:2.0.1", "versions": ["2.0.1"]}],
                    result,
                )

            self.assertIsNone(failed)
            self.assertEqual(
                [call for call in calls if call[0] == "pull-allowed-docker-images"],
                [
                    (
                        "pull-allowed-docker-images",
                        ["./gradlew", "pullAllowedDockerImages", "-Pcoordinates=org.example:demo:2.0.1"],
                    ),
                ],
            )
            self.assertIn("disable-docker-networking", [call[0] for call in calls])

    def test_infrastructure_matrix_runs_test_infra_for_each_entry(self) -> None:
        result = LocalCIVerificationResult(status="running", base_commit="base")
        calls: list[tuple[str, list[str], dict[str, str] | None]] = []

        def fake_run_recorded_command(
                repo_path: str,
                gate: str,
                command: list[str],
                local_result: LocalCIVerificationResult,
                env: dict[str, str] | None = None,
                failure_output_pattern: str | None = None,
        ) -> CommandRecord | None:
            del repo_path, local_result, failure_output_pattern
            calls.append((gate, command, env))
            return None

        with patch.dict(os.environ, {"GRAALVM_HOME_25_0": "/graalvm25"}, clear=True), \
                patch("utility_scripts.local_ci_verification._run_recorded_command", side_effect=fake_run_recorded_command):
            failed = _run_infrastructure_matrix_entries(
                "/repo",
                [{"coordinates": "org.example:demo:1.0.0", "version": "25", "nativeImageMode": "future-defaults-all"}],
                result,
            )

        self.assertIsNone(failed)
        self.assertEqual(
            [call[0] for call in calls],
            [
                "infrastructure-check-metadata-files",
                "test-infra",
            ],
        )
        self.assertEqual(
            calls[-1][1],
            ["./gradlew", "testInfra", "-Pcoordinates=org.example:demo:1.0.0", "-Pparallelism=1", "--stacktrace"],
        )
        self.assertEqual(calls[-1][2]["GRAALVM_HOME"], "/graalvm25")
        self.assertEqual(calls[-1][2]["GVM_TCK_NATIVE_IMAGE_MODE"], "future-defaults-all")

    def test_infrastructure_matrix_pulls_docker_images_when_coordinate_declares_them(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path:
            docker_test_dir = os.path.join(repo_path, "tests", "src", "org.example", "demo", "1.0.0")
            os.makedirs(docker_test_dir)
            with open(os.path.join(docker_test_dir, "required-docker-images.txt"), "w", encoding="utf-8") as file:
                file.write("nginx:1-alpine-slim\n")

            result = LocalCIVerificationResult(status="running", base_commit="base")
            gates: list[str] = []

            def fake_run_recorded_command(
                    repo_path_arg: str,
                    gate: str,
                    command: list[str],
                    local_result: LocalCIVerificationResult,
                    env: dict[str, str] | None = None,
                    failure_output_pattern: str | None = None,
            ) -> CommandRecord | None:
                del repo_path_arg, command, local_result, env, failure_output_pattern
                gates.append(gate)
                return None

            with patch("utility_scripts.local_ci_verification._run_recorded_command", side_effect=fake_run_recorded_command):
                failed = _run_infrastructure_matrix_entries(
                    repo_path,
                    [{"coordinates": "org.example:demo:1.0.0"}],
                    result,
                )

            self.assertIsNone(failed)
            self.assertEqual(
                gates,
                [
                    "infrastructure-pull-allowed-docker-images",
                    "infrastructure-check-metadata-files",
                    "test-infra",
                ],
            )

    def test_spring_aot_matrix_runs_triaged_smoke_test(self) -> None:
        result = LocalCIVerificationResult(status="running", base_commit="base")
        calls: list[tuple[str, list[str], dict[str, str] | None]] = []

        def fake_run_recorded_command(
                repo_path: str,
                gate: str,
                command: list[str],
                local_result: LocalCIVerificationResult,
                env: dict[str, str] | None = None,
                failure_output_pattern: str | None = None,
        ) -> CommandRecord | None:
            del repo_path, local_result, failure_output_pattern
            calls.append((gate, command, env))
            return None

        with patch.dict(os.environ, {"GRAALVM_HOME_25_0": "/graalvm25"}, clear=True), \
                patch("utility_scripts.local_ci_verification._run_recorded_command", side_effect=fake_run_recorded_command):
            failed = _run_spring_aot_matrix_entries(
                "/repo",
                "/tmp/spring-aot-smoke-tests",
                [{"project": ":data:data-mongodb", "java": "25"}],
                result,
            )

        self.assertIsNone(failed)
        self.assertEqual(calls[0][0], "spring-aot-smoke-test")
        self.assertEqual(
            calls[0][1],
            [
                "bash",
                ".github/workflows/scripts/run-spring-aot-triaged-test.sh",
                "/tmp/spring-aot-smoke-tests",
                ":data:data-mongodb",
                "4.1.x",
            ],
        )
        self.assertEqual(calls[0][2]["JAVA_HOME"], "/graalvm25")

    def test_verification_once_runs_infrastructure_and_spring_lanes_when_triggered(self) -> None:
        result = LocalCIVerificationResult(status="running", base_commit="base")
        gradle_tasks: list[str] = []

        def fake_gradle_json_output(
                repo_path: str,
                task_name: str,
                base_commit: str,
                local_result: LocalCIVerificationResult,
                gate: str,
                extra_args: list[str] | None = None,
        ) -> dict:
            del repo_path, base_commit, local_result, gate, extra_args
            gradle_tasks.append(task_name)
            if task_name == "generateInfrastructureChangedCoordinatesMatrix":
                return {"include": [{"coordinates": "org.example:demo:1.0.0"}]}
            return {"include": []}

        changed_files = [
            "build.gradle",
            "metadata/org.example/demo/1.0.0/reachability-metadata.json",
        ]
        with patch("utility_scripts.local_ci_verification.changed_files_for_ci", return_value=changed_files), \
                patch("utility_scripts.local_ci_verification._gradle_json_output", side_effect=fake_gradle_json_output), \
                patch("utility_scripts.local_ci_verification._run_test_matrix_entries", return_value=None), \
                patch("utility_scripts.local_ci_verification._run_infrastructure_matrix_entries", return_value=None) as infra, \
                patch("utility_scripts.local_ci_verification._run_spring_aot_verification", return_value=None) as spring, \
                patch("utility_scripts.local_ci_verification._run_index_validation", return_value=None), \
                patch("utility_scripts.local_ci_verification._run_style_validation", return_value=None), \
                patch("utility_scripts.local_ci_verification._run_recorded_command", return_value=None):
            failed = _run_verification_once("/repo", "base", result)

        self.assertIsNone(failed)
        self.assertIn("generateInfrastructureChangedCoordinatesMatrix", gradle_tasks)
        infra.assert_called_once()
        spring.assert_called_once_with("/repo", "base", changed_files, result)

    def test_latest_ea_requires_latest_ea_graalvm_home(self) -> None:
        with patch.dict(os.environ, {"GRAALVM_HOME": "/stable"}, clear=True):
            with self.assertRaisesRegex(RuntimeError, "GRAALVM_HOME_LATEST_EA"):
                _graalvm_home_for_java_version("latest-ea")

        with patch.dict(os.environ, {"GRAALVM_HOME_LATEST_EA": "/ea"}, clear=True):
            self.assertEqual(_graalvm_home_for_java_version("latest-ea"), "/ea")

    def test_github_repo_slug_from_url_accepts_https_and_ssh_forms(self) -> None:
        expected = "oracle/graalvm-reachability-metadata"

        self.assertEqual(
            _github_repo_slug_from_url("https://github.com/oracle/graalvm-reachability-metadata.git"),
            expected,
        )
        self.assertEqual(
            _github_repo_slug_from_url("git@github.com:oracle/graalvm-reachability-metadata.git"),
            expected,
        )
        self.assertEqual(
            _github_repo_slug_from_url("ssh://git@github.com/oracle/graalvm-reachability-metadata.git"),
            expected,
        )
        self.assertIsNone(_github_repo_slug_from_url("https://example.com/oracle/graalvm-reachability-metadata.git"))


if __name__ == "__main__":
    unittest.main()
