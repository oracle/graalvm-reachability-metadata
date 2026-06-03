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
    _run_recorded_command,
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

    def test_verification_once_runs_only_index_and_docker_gates(self) -> None:
        result = LocalCIVerificationResult(status="running", base_commit="base")
        recorded_gates: list[str] = []

        def fake_recorded(repo_path, gate, command, result_arg, env=None, failure_output_pattern=None):
            del repo_path, command, result_arg, env, failure_output_pattern
            recorded_gates.append(gate)
            return None

        changed_files = [
            "metadata/org.example/demo/1.0.0/reachability-metadata.json",
            "metadata/org.example/demo/index.json",
            "tests/tck-build-logic/src/main/resources/allowed-docker-images/demo.json",
        ]
        with patch("utility_scripts.local_ci_verification.changed_files_for_ci", return_value=changed_files), \
                patch("utility_scripts.local_ci_verification._run_index_validation", return_value=None) as index_gate, \
                patch("utility_scripts.local_ci_verification._run_recorded_command", side_effect=fake_recorded):
            failed = _run_verification_once("/repo", "base", result)

        self.assertIsNone(failed)
        index_gate.assert_called_once()
        # The library-scoped gates (checkMetadataFiles, spotless, stats, doc-links)
        # are owned by generation/finalization, so only the shared Docker scan runs here.
        self.assertEqual(recorded_gates, ["docker-image-scan"])

    def test_reproduce_full_ci_runs_native_matrix_and_spring_aot(self) -> None:
        result = LocalCIVerificationResult(status="running", base_commit="base")
        gradle_tasks: list[str] = []

        def fake_gradle_json_output(repo_path, task_name, base_commit, result_arg, gate, extra_args=None):
            del repo_path, base_commit, result_arg, gate, extra_args
            gradle_tasks.append(task_name)
            return {"include": []}

        changed_files = ["metadata/org.example/demo/1.0.0/reachability-metadata.json"]
        with patch("utility_scripts.local_ci_verification.changed_files_for_ci", return_value=changed_files), \
                patch("utility_scripts.local_ci_verification._run_index_validation", return_value=None), \
                patch("utility_scripts.local_ci_verification._gradle_json_output", side_effect=fake_gradle_json_output), \
                patch("utility_scripts.local_ci_verification._run_test_matrix_entries", return_value=None) as matrix, \
                patch("utility_scripts.local_ci_verification._run_spring_aot_verification", return_value=None) as spring:
            failed = _run_verification_once("/repo", "base", result, reproduce_full_ci=True)

        self.assertIsNone(failed)
        self.assertIn("generateChangedMetadataTestMatrix", gradle_tasks)
        matrix.assert_called_once()
        spring.assert_called_once()

    def test_default_gate_does_not_run_native_matrix(self) -> None:
        result = LocalCIVerificationResult(status="running", base_commit="base")
        with patch(
                "utility_scripts.local_ci_verification.changed_files_for_ci",
                return_value=["metadata/org.example/demo/1.0.0/reachability-metadata.json"],
        ), patch("utility_scripts.local_ci_verification._run_index_validation", return_value=None), \
                patch("utility_scripts.local_ci_verification._run_test_matrix_entries", return_value=None) as matrix:
            failed = _run_verification_once("/repo", "base", result)

        self.assertIsNone(failed)
        matrix.assert_not_called()

    def test_verification_once_skips_docker_scan_without_allowed_image_changes(self) -> None:
        result = LocalCIVerificationResult(status="running", base_commit="base")
        with patch(
                "utility_scripts.local_ci_verification.changed_files_for_ci",
                return_value=["metadata/org.example/demo/1.0.0/reachability-metadata.json"],
        ), patch("utility_scripts.local_ci_verification._run_index_validation", return_value=None), \
                patch("utility_scripts.local_ci_verification._run_recorded_command") as recorded:
            failed = _run_verification_once("/repo", "base", result)

        self.assertIsNone(failed)
        recorded.assert_not_called()

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

    def test_run_recorded_command_removes_inherited_gradle_java_home_overrides(self) -> None:
        captured_env: dict[str, str] = {}

        def fake_run(
                command: list[str],
                cwd: str | None = None,
                env: dict[str, str] | None = None,
                stdin=None,
                stdout=None,
                stderr=None,
                text: bool | None = None,
                check: bool | None = None,
        ) -> subprocess.CompletedProcess[str]:
            del cwd, stdin, stdout, stderr, text, check
            captured_env.update(env or {})
            return subprocess.CompletedProcess(command, 0, stdout="ok\n")

        with tempfile.TemporaryDirectory() as repo_path, tempfile.TemporaryDirectory() as log_path:
            result = LocalCIVerificationResult(status="running", base_commit="base")
            with patch.dict(
                    os.environ,
                    {
                        "GRADLE_OPTS": "-Xmx2g -Dorg.gradle.java.home=/wrong/jdk -Dfile.encoding=UTF-8",
                        "JAVA_OPTS": "-Dorg.gradle.java.home=/other/jdk",
                    },
                    clear=True,
            ), patch(
                    "utility_scripts.local_ci_verification.build_timestamped_task_log_path",
                    return_value=os.path.join(log_path, "command.log"),
            ), patch("utility_scripts.local_ci_verification.subprocess.run", side_effect=fake_run):
                failed = _run_recorded_command(
                    repo_path,
                    "run-consecutive-tests",
                    ["bash", "script.sh"],
                    result,
                    env={"JAVA_HOME": "/matrix/jdk"},
                )

        self.assertIsNone(failed)
        self.assertEqual(captured_env["JAVA_HOME"], "/matrix/jdk")
        self.assertEqual(captured_env["GRADLE_OPTS"], "-Xmx2g -Dfile.encoding=UTF-8")
        self.assertNotIn("JAVA_OPTS", captured_env)

    def test_run_recorded_command_rejects_sudo_command_without_running_it(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path, tempfile.TemporaryDirectory() as log_path:
            result = LocalCIVerificationResult(status="running", base_commit="base")
            with patch(
                    "utility_scripts.local_ci_verification.build_timestamped_task_log_path",
                    return_value=os.path.join(log_path, "command.log"),
            ), patch("utility_scripts.local_ci_verification.subprocess.run") as run:
                failed = _run_recorded_command(
                    repo_path,
                    "privileged-command",
                    ["sudo", "systemctl", "restart", "docker"],
                    result,
                )

            self.assertIsNotNone(failed)
            run.assert_not_called()
            self.assertEqual(result.commands[0].returncode, 1)
            self.assertIn("must not invoke sudo", result.commands[0].output_excerpt)

    def test_run_recorded_command_rejects_sudo_script_without_running_it(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path, tempfile.TemporaryDirectory() as log_path:
            script_path = os.path.join(repo_path, "needs-sudo.sh")
            with open(script_path, "w", encoding="utf-8") as file:
                file.write("#!/bin/bash\nsudo systemctl restart docker\n")
            result = LocalCIVerificationResult(status="running", base_commit="base")
            with patch(
                    "utility_scripts.local_ci_verification.build_timestamped_task_log_path",
                    return_value=os.path.join(log_path, "command.log"),
            ), patch("utility_scripts.local_ci_verification.subprocess.run") as run:
                failed = _run_recorded_command(
                    repo_path,
                    "privileged-script",
                    ["bash", "./needs-sudo.sh"],
                    result,
                )

            self.assertIsNotNone(failed)
            run.assert_not_called()
            self.assertEqual(result.commands[0].returncode, 1)
            self.assertIn("needs-sudo.sh", result.commands[0].output_excerpt)

    def test_run_recorded_command_rejects_repo_local_script_without_suffix(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path, tempfile.TemporaryDirectory() as log_path:
            script_path = os.path.join(repo_path, "gradlew")
            with open(script_path, "w", encoding="utf-8") as file:
                file.write("#!/bin/bash\nsudo systemctl restart docker\n")
            result = LocalCIVerificationResult(status="running", base_commit="base")
            with patch(
                    "utility_scripts.local_ci_verification.build_timestamped_task_log_path",
                    return_value=os.path.join(log_path, "command.log"),
            ), patch("utility_scripts.local_ci_verification.subprocess.run") as run:
                failed = _run_recorded_command(
                    repo_path,
                    "gradle-wrapper",
                    ["./gradlew", "test"],
                    result,
                )

            self.assertIsNotNone(failed)
            run.assert_not_called()
            self.assertEqual(result.commands[0].returncode, 1)
            self.assertIn("gradlew", result.commands[0].output_excerpt)

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
