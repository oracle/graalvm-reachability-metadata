# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Tests for the setup tail's source download, its filter, and the result file on an early exit.

§AR-forge-workflow-pipeline.4.1
"""
from __future__ import annotations

import json
import os
import subprocess
import tempfile
import unittest
from typing import Any
from unittest import mock

from support import COORDINATE, FakeRepository, run_state, working_directory
from test_prepare import prepare_arguments
from test_workflow import URLS
from utility_scripts.source_context import PreparedSourceContext, SourceArtifactContext
from utility_scripts.user_code_filter import DEFAULT_FILTER

import forge_run  # noqa: E402
import setup_check  # noqa: E402
from exit_codes import BAD_INPUT, INFRASTRUCTURE, OK  # noqa: E402
from run_state import RunState, load, read_json, runtime_path  # noqa: E402


def prepared(url: str | None, available: bool) -> PreparedSourceContext:
    return PreparedSourceContext(coordinate="org.example:lib:2.0.0", repository_url=None, artifacts=[
        SourceArtifactContext("main", url, None, [], available, None if available else "denied")])


class DownloadSourcesTest(unittest.TestCase):

    def download(self, *outcomes: PreparedSourceContext) -> tuple[list[str], list[int]]:
        pauses: list[int] = []
        with mock.patch.object(setup_check, "prepare_source_contexts", side_effect=list(outcomes)), \
                mock.patch.object(setup_check.time, "sleep", side_effect=pauses.append):
            return setup_check.download_sources(run_state()), pauses

    def test_a_denied_download_is_retried_after_a_pause(self) -> None:
        missing, pauses = self.download(prepared("https://example.org/src", False), prepared("https://example.org/src", True))
        self.assertEqual(([], [setup_check.retry_seconds()]), (missing, pauses))

    def test_a_source_without_a_url_is_not_retried(self) -> None:
        self.assertEqual(([], []), self.download(prepared(None, False)))

    def test_the_run_goes_on_after_the_last_attempt(self) -> None:
        denied: PreparedSourceContext = prepared("https://example.org/src", False)
        missing, pauses = self.download(*[denied] * setup_check.SOURCE_ATTEMPTS)
        self.assertEqual((["main"], setup_check.SOURCE_ATTEMPTS - 1), (missing, len(pauses)))


class EarlyExitTest(unittest.TestCase):

    def test_a_system_exit_still_writes_the_result_and_outcome(self) -> None:
        workspace: str = tempfile.mkdtemp()
        self.addCleanup(lambda: __import__("shutil").rmtree(workspace, ignore_errors=True))
        result_path: str = os.path.join(workspace, "runtime", "results", "ws.forge.md")

        def quit_early(_: str) -> int:
            raise SystemExit(1)

        with mock.patch.dict(forge_run.STEPS, {"setup-check": quit_early}), \
                mock.patch.dict(os.environ, {"RHEI_RESULT_PATH": result_path}), working_directory(workspace):
            self.assertEqual(INFRASTRUCTURE, forge_run.main(["setup-check"]))
        self.assertTrue(os.path.isfile(result_path))
        outcome = read_json(runtime_path(workspace, forge_run.OUTCOME))
        self.assertEqual(("failed", "infrastructure"), (outcome["outcome"], outcome["kind"]))


class FilterTest(unittest.TestCase):
    """A test project without a filter gets one from the library JAR, its tests never built."""

    def setUp(self) -> None:
        self.fake: FakeRepository = FakeRepository()
        self.addCleanup(self.fake.close)
        self.fake.supported_library()
        self.project: str = os.path.join("tests", "src", "org.example", "lib", "1.5.0")
        os.remove(os.path.join(self.fake.repo, self.project, DEFAULT_FILTER))

    def prepare(self) -> RunState:
        """Commit the supported project, prepare a `fails-javac-compile` run, and play `setup`."""
        self.fake.commit("the supported project's filter")
        self.fake.issue("fails-javac-compile")
        self.assertEqual(OK, self.step(*prepare_arguments()))
        state: RunState = load(self.fake.workspace)
        with open(state.path(state.index_path), encoding="utf-8") as handle:
            entries: list[dict[str, Any]] = json.load(handle)
        for entry in entries:
            entry.update(URLS)
        with open(state.path(state.index_path), "w", encoding="utf-8") as handle:
            json.dump(entries, handle, indent=2)
        return state

    def step(self, *argv: str) -> int:
        with working_directory(self.fake.workspace):
            return forge_run.main(list(argv))

    def tasks(self) -> list[str]:
        """The Gradle tasks run so far; `prepare` only stops the daemons."""
        with open(load(self.fake.workspace).path("gradle-calls.log"), encoding="utf-8") as handle:
            return [line.split()[0] for line in handle if line.strip() != "--stop"]

    def test_tests_that_do_not_compile_still_get_the_filter(self) -> None:
        self.prepare()
        self.fake.control({"javaFails": True})
        self.assertEqual(OK, self.step("setup-check"))
        state: RunState = load(self.fake.workspace)
        filter_path: str = os.path.join(state.test_dir, DEFAULT_FILTER)
        subprocess.run(["git", "cat-file", "-e", f"{state.checkpoint}:{filter_path}"], cwd=state.worktree, check=True)
        self.assertEqual([setup_check.FILTER_TASK], self.tasks())

    def test_the_filter_the_agent_block_names_is_kept(self) -> None:
        self.fake.write(os.path.join(self.project, "build.gradle"),
                        'graalvmNative { agent { modes { conditional {\n'
                        '    userCodeFilterPath = "metadata-conditions-filter.json"\n} } } }\n')
        self.fake.write(os.path.join(self.project, "metadata-conditions-filter.json"), '{"rules": []}\n')
        state: RunState = self.prepare()
        self.assertEqual(OK, self.step("setup-check"))
        self.assertEqual([], self.tasks())
        self.assertFalse(os.path.exists(state.path(os.path.join(state.test_dir, DEFAULT_FILTER))))

    def test_an_unresolvable_library_is_a_bad_input(self) -> None:
        self.prepare()
        self.fake.control({"filterFails": f"> Could not find {COORDINATE}."})
        self.assertEqual(BAD_INPUT, self.step("setup-check"))

    def test_any_other_filter_failure_is_infrastructure(self) -> None:
        self.prepare()
        self.fake.control({"filterFails": "Gradle build daemon disappeared unexpectedly"})
        self.assertEqual(INFRASTRUCTURE, self.step("setup-check"))


if __name__ == "__main__":
    unittest.main()
