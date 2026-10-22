# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Tests for the setup tail's source download (§AR-forge-workflow-pipeline.4.1) and the result file on an early exit."""
from __future__ import annotations

import os
import tempfile
import unittest
from unittest import mock

from support import run_state, working_directory
from utility_scripts.source_context import PreparedSourceContext, SourceArtifactContext

import forge_run  # noqa: E402
import repair  # noqa: E402
from exit_codes import INFRASTRUCTURE  # noqa: E402
from run_state import read_json, runtime_path  # noqa: E402


def prepared(url: str | None, available: bool) -> PreparedSourceContext:
    return PreparedSourceContext(coordinate="org.example:lib:2.0.0", repository_url=None, artifacts=[
        SourceArtifactContext("main", url, None, [], available, None if available else "denied")])


class DownloadSourcesTest(unittest.TestCase):

    def download(self, *outcomes: PreparedSourceContext) -> tuple[list[str], list[int]]:
        pauses: list[int] = []
        with mock.patch.object(repair, "prepare_source_contexts", side_effect=list(outcomes)), \
                mock.patch.object(repair.time, "sleep", side_effect=pauses.append):
            return repair.download_sources(run_state()), pauses

    def test_a_denied_download_is_retried_after_a_pause(self) -> None:
        missing, pauses = self.download(prepared("https://example.org/src", False), prepared("https://example.org/src", True))
        self.assertEqual(([], [repair.retry_seconds()]), (missing, pauses))

    def test_a_source_without_a_url_is_not_retried(self) -> None:
        self.assertEqual(([], []), self.download(prepared(None, False)))

    def test_the_run_goes_on_after_the_last_attempt(self) -> None:
        denied: PreparedSourceContext = prepared("https://example.org/src", False)
        missing, pauses = self.download(*[denied] * repair.SOURCE_ATTEMPTS)
        self.assertEqual((["main"], repair.SOURCE_ATTEMPTS - 1), (missing, len(pauses)))


class EarlyExitTest(unittest.TestCase):

    def test_a_system_exit_still_writes_the_result_and_outcome(self) -> None:
        workspace: str = tempfile.mkdtemp()
        self.addCleanup(lambda: __import__("shutil").rmtree(workspace, ignore_errors=True))
        result_path: str = os.path.join(workspace, "runtime", "results", "ws.forge.md")

        def quit_early(_: str) -> int:
            raise SystemExit(1)

        with mock.patch.dict(forge_run.STEPS, {"repair": quit_early}), \
                mock.patch.dict(os.environ, {"RHEI_RESULT_PATH": result_path}), working_directory(workspace):
            self.assertEqual(INFRASTRUCTURE, forge_run.main(["repair"]))
        self.assertTrue(os.path.isfile(result_path))
        outcome = read_json(runtime_path(workspace, forge_run.OUTCOME))
        self.assertEqual(("failed", "infrastructure"), (outcome["outcome"], outcome["kind"]))


if __name__ == "__main__":
    unittest.main()
