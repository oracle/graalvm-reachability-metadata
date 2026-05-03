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
    format_local_ci_verification_pr_section,
    local_ci_requires_human_intervention,
    run_local_ci_verification,
    _github_repo_slug_from_url,
    _graalvm_home_for_java_version,
    _run_recorded_command,
    _run_test_matrix_entries,
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

    def test_test_matrix_restores_docker_after_test_failure(self) -> None:
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
        self.assertEqual(gates[-1], "restore-docker-networking")

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
