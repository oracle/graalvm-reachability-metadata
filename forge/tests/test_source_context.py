# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from utility_scripts.source_context import (
    GradleBootstrapFailure,
    discover_artifact_metadata,
)


class SourceContextDiscoveryTests(unittest.TestCase):
    def test_discover_artifact_metadata_uses_no_daemon(self) -> None:
        with tempfile.TemporaryDirectory() as repo:
            log_path = os.path.join(repo, "discover.log")
            completed = subprocess.CompletedProcess([], 0, stdout="success\n")

            with (
                patch("utility_scripts.source_context.require_complete_reachability_repo"),
                patch("utility_scripts.source_context.build_task_log_path", return_value=log_path),
                patch("utility_scripts.source_context.gradle_command_environment", return_value={"ENV": "1"}),
                patch("utility_scripts.source_context.subprocess.run", return_value=completed) as run,
            ):
                discover_artifact_metadata(repo, "org.example:demo:1.0.0", agent_command="/bin/true")

            run.assert_called_once()
            command = run.call_args.args[0]
            self.assertEqual(command[:3], ["./gradlew", "--no-daemon", "discoverArtifactMetadata"])
            self.assertIn("--coordinates=org.example:demo:1.0.0", command)
            self.assertIn("--agent-command=/bin/true", command)
            with open(log_path, "r", encoding="utf-8") as log_file:
                log_content = log_file.read()
            self.assertIn("[forge] Gradle discovery attempt: initial", log_content)
            self.assertIn("success", log_content)

    def test_discover_artifact_metadata_retries_spotless_plugin_bootstrap_failure(self) -> None:
        with tempfile.TemporaryDirectory() as repo:
            log_path = os.path.join(repo, "discover.log")
            first = subprocess.CompletedProcess(
                [],
                1,
                stdout=(
                    "Plugin [id: 'com.diffplug.spotless', version: '6.3.0'] was not found\n"
                    "could not resolve plugin artifact "
                    "'com.diffplug.spotless:com.diffplug.spotless.gradle.plugin:6.3.0'\n"
                    "Searched in the following repositories:\n"
                    "  Gradle Central Plugin Repository\n"
                ),
            )
            second = subprocess.CompletedProcess([], 0, stdout="retry success\n")

            with (
                patch("utility_scripts.source_context.require_complete_reachability_repo"),
                patch("utility_scripts.source_context.build_task_log_path", return_value=log_path),
                patch("utility_scripts.source_context.gradle_command_environment", return_value={"ENV": "1"}),
                patch("utility_scripts.source_context.time.sleep") as sleep,
                patch("utility_scripts.source_context.subprocess.run", side_effect=[first, second]) as run,
            ):
                discover_artifact_metadata(repo, "org.example:demo:1.0.0", agent_command="/bin/true")

            self.assertEqual(run.call_count, 2)
            retry_command = run.call_args_list[1].args[0]
            self.assertIn("--stacktrace", retry_command)
            self.assertIn("--info", retry_command)
            self.assertIn("--refresh-dependencies", retry_command)
            sleep.assert_called_once()
            with open(log_path, "r", encoding="utf-8") as log_file:
                log_content = log_file.read()
            self.assertIn("[forge] Gradle discovery attempt: initial", log_content)
            self.assertIn("[forge] Gradle discovery attempt: diagnostic-retry", log_content)
            self.assertIn("retry success", log_content)

    def test_discover_artifact_metadata_raises_bootstrap_failure_when_retry_is_exhausted(self) -> None:
        with tempfile.TemporaryDirectory() as repo:
            log_path = os.path.join(repo, "discover.log")
            failed = subprocess.CompletedProcess(
                [],
                1,
                stdout=(
                    "Plugin [id: 'com.diffplug.spotless', version: '6.3.0'] was not found\n"
                    "could not resolve plugin artifact "
                    "'com.diffplug.spotless:com.diffplug.spotless.gradle.plugin:6.3.0'\n"
                    "Searched in the following repositories:\n"
                    "  Gradle Central Plugin Repository\n"
                ),
            )

            with (
                patch("utility_scripts.source_context.require_complete_reachability_repo"),
                patch("utility_scripts.source_context.build_task_log_path", return_value=log_path),
                patch("utility_scripts.source_context.gradle_command_environment", return_value={"ENV": "1"}),
                patch("utility_scripts.source_context.time.sleep"),
                patch("utility_scripts.source_context.subprocess.run", side_effect=[failed, failed]) as run,
            ):
                with self.assertRaises(GradleBootstrapFailure) as raised:
                    discover_artifact_metadata(repo, "org.example:demo:1.0.0", agent_command="/bin/true")

            self.assertEqual(run.call_count, 2)
            self.assertEqual(raised.exception.coordinate, "org.example:demo:1.0.0")
            self.assertEqual(raised.exception.log_path, log_path)
            with open(log_path, "r", encoding="utf-8") as log_file:
                log_content = log_file.read()
            self.assertIn("[forge] Gradle discovery attempt: initial", log_content)
            self.assertIn("[forge] Gradle discovery attempt: diagnostic-retry", log_content)

    def test_discover_artifact_metadata_retries_gradle_wrapper_download_bootstrap_failure(self) -> None:
        with tempfile.TemporaryDirectory() as repo:
            log_path = os.path.join(repo, "discover.log")
            first = subprocess.CompletedProcess(
                [],
                1,
                stdout=(
                    "Downloading https://services.gradle.org/distributions/gradle-8.14.3-bin.zip\n"
                    "Exception in thread \"main\" java.net.SocketTimeoutException: Read timed out\n"
                ),
            )
            second = subprocess.CompletedProcess([], 0, stdout="retry success\n")

            with (
                patch("utility_scripts.source_context.require_complete_reachability_repo"),
                patch("utility_scripts.source_context.build_task_log_path", return_value=log_path),
                patch("utility_scripts.source_context.gradle_command_environment", return_value={"ENV": "1"}),
                patch("utility_scripts.source_context.time.sleep"),
                patch("utility_scripts.source_context.subprocess.run", side_effect=[first, second]) as run,
            ):
                discover_artifact_metadata(repo, "org.example:demo:1.0.0", agent_command="/bin/true")

            self.assertEqual(run.call_count, 2)

    def test_discover_artifact_metadata_does_not_retry_non_bootstrap_failure(self) -> None:
        with tempfile.TemporaryDirectory() as repo:
            log_path = os.path.join(repo, "discover.log")
            failed = subprocess.CompletedProcess([], 1, stdout="compileJava failed\n")

            with (
                patch("utility_scripts.source_context.require_complete_reachability_repo"),
                patch("utility_scripts.source_context.build_task_log_path", return_value=log_path),
                patch("utility_scripts.source_context.gradle_command_environment", return_value={"ENV": "1"}),
                patch("utility_scripts.source_context.subprocess.run", return_value=failed) as run,
            ):
                with self.assertRaises(SystemExit):
                    discover_artifact_metadata(repo, "org.example:demo:1.0.0", agent_command="/bin/true")

            run.assert_called_once()
            with open(log_path, "r", encoding="utf-8") as log_file:
                log_content = log_file.read()
            self.assertNotIn("diagnostic-retry", log_content)


if __name__ == "__main__":
    unittest.main()
