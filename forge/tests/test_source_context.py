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
    SourceArtifactContext,
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


if __name__ == "__main__":
    unittest.main()
