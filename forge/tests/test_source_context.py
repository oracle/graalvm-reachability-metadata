# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import contextlib
import json
import os
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from utility_scripts.source_context import (
    GradleBootstrapFailure,
    SourceArtifactContext,
    _is_gradle_bootstrap_failure,
    discover_artifact_metadata,
    prepare_source_contexts,
)

SPOTLESS_BOOTSTRAP_OUTPUT = (
    "Plugin [id: 'com.diffplug.spotless', version: '6.3.0'] was not found\n"
    "could not resolve plugin artifact "
    "'com.diffplug.spotless:com.diffplug.spotless.gradle.plugin:6.3.0'\n"
    "Searched in the following repositories:\n"
    "  Gradle Central Plugin Repository\n"
)
WRAPPER_DOWNLOAD_FAILURE_OUTPUT = (
    "Downloading https://services.gradle.org/distributions/gradle-8.14.3-bin.zip\n"
    'Exception in thread "main" java.net.SocketTimeoutException: Read timed out\n'
)
# Observed verbatim from a wrapper bootstrap against an unreachable
# services.gradle.org: the message is "Connect timed out", not "Connection".
WRAPPER_CONNECT_TIMEOUT_OUTPUT = (
    "Downloading https://services.gradle.org/distributions/gradle-9.1.0-bin.zip\n"
    "\n"
    'Exception in thread "main" java.io.IOException: Downloading from '
    "https://services.gradle.org/distributions/gradle-9.1.0-bin.zip failed: timeout (10000ms)\n"
    "\tat org.gradle.wrapper.Install.forceFetch(SourceFile:4)\n"
    "\tat org.gradle.wrapper.GradleWrapperMain.main(SourceFile:67)\n"
    "Caused by: java.net.SocketTimeoutException: Connect timed out\n"
)
WRAPPER_DOWNLOAD_SUCCESS_OUTPUT = (
    "Downloading https://services.gradle.org/distributions/gradle-9.1.0-bin.zip\n"
    "BUILD SUCCESSFUL in 42s\n"
)


class SourceContextTests(unittest.TestCase):
    def test_source_url_templates_use_requested_version_for_tested_version_target(self) -> None:
        with tempfile.TemporaryDirectory() as repo_root:
            reachability_repo = os.path.join(repo_root, "reachability")
            metadata_dir = os.path.join(reachability_repo, "metadata", "org.example", "demo")
            os.makedirs(metadata_dir)
            with open(os.path.join(metadata_dir, "index.json"), "w", encoding="utf-8") as file:
                json.dump(
                    [
                        {
                            "metadata-version": "1.0.0",
                            "tested-versions": ["1.0.0", "1.0.1"],
                            "source-code-url": "https://example.test/demo-$version$-sources.jar",
                        }
                    ],
                    file,
                )

            downloaded_urls: list[str] = []

            def fake_download(base_dir: str, source_type: str, url: str) -> SourceArtifactContext:
                del base_dir
                downloaded_urls.append(url)
                return SourceArtifactContext(source_type, url, None, [], True)

            with patch("utility_scripts.source_context.download_source_artifact", side_effect=fake_download):
                prepare_source_contexts(
                    repo_root=repo_root,
                    reachability_repo_path=reachability_repo,
                    coordinate="org.example:demo:1.0.1",
                    source_context_types=["main"],
                )

            self.assertEqual(downloaded_urls, ["https://example.test/demo-1.0.1-sources.jar"])


class DiscoverArtifactMetadataTests(unittest.TestCase):
    def test_discovery_runs_without_daemon_and_logs_the_attempt(self) -> None:
        with _discovery_harness([subprocess.CompletedProcess([], 0, stdout="success\n")]) as harness:
            discover_artifact_metadata(harness.repo, "org.example:demo:1.0.0", agent_command="/bin/true")

            harness.run.assert_called_once()
            command = harness.run.call_args.args[0]
            self.assertEqual(command[:3], ["./gradlew", "--no-daemon", "discoverArtifactMetadata"])
            self.assertIn("--coordinates=org.example:demo:1.0.0", command)
            self.assertIn("--agent-command=/bin/true", command)
            log_content = harness.read_log()
            self.assertIn("[forge] Gradle discovery attempt: initial", log_content)
            self.assertIn("[forge] Exit code: 0", log_content)
            self.assertIn("success", log_content)

    def test_spotless_plugin_bootstrap_failure_is_retried_with_dependency_refresh(self) -> None:
        attempts = [
            subprocess.CompletedProcess([], 1, stdout=SPOTLESS_BOOTSTRAP_OUTPUT),
            subprocess.CompletedProcess([], 0, stdout="retry success\n"),
        ]
        with _discovery_harness(attempts) as harness:
            discover_artifact_metadata(harness.repo, "org.example:demo:1.0.0", agent_command="/bin/true")

            self.assertEqual(harness.run.call_count, 2)
            retry_command = harness.run.call_args_list[1].args[0]
            self.assertIn("--stacktrace", retry_command)
            self.assertIn("--info", retry_command)
            self.assertIn("--refresh-dependencies", retry_command)
            harness.sleep.assert_called_once()
            log_content = harness.read_log()
            self.assertIn("[forge] Gradle discovery attempt: initial", log_content)
            self.assertIn("[forge] Gradle discovery attempt: diagnostic-retry", log_content)
            self.assertIn("retry success", log_content)

    def test_wrapper_download_bootstrap_failure_is_retried(self) -> None:
        attempts = [
            subprocess.CompletedProcess([], 1, stdout=WRAPPER_DOWNLOAD_FAILURE_OUTPUT),
            subprocess.CompletedProcess([], 0, stdout="retry success\n"),
        ]
        with _discovery_harness(attempts) as harness:
            discover_artifact_metadata(harness.repo, "org.example:demo:1.0.0", agent_command="/bin/true")

            self.assertEqual(harness.run.call_count, 2)

    def test_wrapper_connect_timeout_is_recognized_as_a_bootstrap_failure(self) -> None:
        attempts = [
            subprocess.CompletedProcess([], 1, stdout=WRAPPER_CONNECT_TIMEOUT_OUTPUT),
            subprocess.CompletedProcess([], 0, stdout="retry success\n"),
        ]
        with _discovery_harness(attempts) as harness:
            discover_artifact_metadata(harness.repo, "org.example:demo:1.0.0", agent_command="/bin/true")

            self.assertEqual(harness.run.call_count, 2)

    def test_downloaded_distribution_alone_is_not_a_bootstrap_failure(self) -> None:
        self.assertFalse(_is_gradle_bootstrap_failure(WRAPPER_DOWNLOAD_SUCCESS_OUTPUT))
        self.assertFalse(_is_gradle_bootstrap_failure("compileJava FAILED\n"))
        self.assertFalse(_is_gradle_bootstrap_failure(None))

    def test_exhausted_bootstrap_retry_raises_gradle_bootstrap_failure(self) -> None:
        failed = subprocess.CompletedProcess([], 1, stdout=SPOTLESS_BOOTSTRAP_OUTPUT)
        with _discovery_harness([failed, failed]) as harness:
            with self.assertRaises(GradleBootstrapFailure) as raised:
                discover_artifact_metadata(harness.repo, "org.example:demo:1.0.0", agent_command="/bin/true")

            self.assertEqual(harness.run.call_count, 2)
            self.assertEqual(raised.exception.coordinate, "org.example:demo:1.0.0")
            self.assertEqual(raised.exception.log_path, harness.log_path)
            log_content = harness.read_log()
            self.assertIn("[forge] Gradle discovery attempt: initial", log_content)
            self.assertIn("[forge] Gradle discovery attempt: diagnostic-retry", log_content)

    def test_library_failure_is_not_retried_and_stays_a_library_failure(self) -> None:
        failed = subprocess.CompletedProcess([], 1, stdout="compileJava failed\n")
        with _discovery_harness([failed]) as harness:
            with self.assertRaises(SystemExit):
                discover_artifact_metadata(harness.repo, "org.example:demo:1.0.0", agent_command="/bin/true")

            harness.run.assert_called_once()
            self.assertNotIn("diagnostic-retry", harness.read_log())


class _DiscoveryHarness:
    def __init__(self, repo: str, log_path: str, run, sleep) -> None:
        self.repo = repo
        self.log_path = log_path
        self.run = run
        self.sleep = sleep

    def read_log(self) -> str:
        with open(self.log_path, "r", encoding="utf-8") as log_file:
            return log_file.read()


@contextlib.contextmanager
def _discovery_harness(attempts: list[subprocess.CompletedProcess]):
    """Run discovery against stubbed Gradle attempts in a throwaway repo."""
    with tempfile.TemporaryDirectory() as repo:
        log_path = os.path.join(repo, "discover.log")
        with (
            patch("utility_scripts.source_context.require_complete_reachability_repo"),
            patch("utility_scripts.source_context.build_task_log_path", return_value=log_path),
            patch("utility_scripts.source_context.gradle_command_environment", return_value={"ENV": "1"}),
            patch("utility_scripts.source_context.time.sleep") as sleep,
            patch("utility_scripts.source_context.subprocess.run", side_effect=attempts) as run,
        ):
            yield _DiscoveryHarness(repo, log_path, run, sleep)


if __name__ == "__main__":
    unittest.main()
