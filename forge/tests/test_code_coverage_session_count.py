# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import json
import os
import shutil
import tempfile
import unittest
from unittest import mock

from utility_scripts import code_coverage_session_count as module
from utility_scripts.code_coverage_deep_sessions import (
    DISPATCH_PASS_SPENT,
    DISPATCH_SESSION,
    dispatch,
    write_queue,
)

from tests.code_coverage_profile_support import JACOCO_PATH


class SessionCountTest(unittest.TestCase):
    """Dispatch counts library coverage after each deep cover session.

    §AR-code-coverage-trajectory.4
    """

    def setUp(self) -> None:
        self.root: str = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, self.root)
        self.discovery: str = os.path.join(self.root, "discovery")
        os.makedirs(self.discovery)
        environment = mock.patch.dict(os.environ, {}, clear=False)
        environment.start()
        self.addCleanup(environment.stop)
        os.environ.pop("RHEI_ITERATION", None)

    def _count(self, iteration: int) -> dict:
        path: str = os.path.join(self.discovery, f"session-coverage-{iteration}.json")
        with open(path, encoding="utf-8") as count_file:
            return json.load(count_file)

    def test_the_first_dispatch_counts_the_measured_baseline(self) -> None:
        shutil.copy(JACOCO_PATH, os.path.join(self.discovery, "jacoco-deep-0.xml"))

        with mock.patch.object(module, "run_jacoco_report") as gradle:
            module.count_session(self.discovery, "/worktree", "g:a:1")

        gradle.assert_not_called()
        count: dict = self._count(0)
        self.assertIsNone(count["session"])
        self.assertEqual({"total": 9, "covered": 3}, {
            key: count["summary"][key] for key in ("total", "covered")
        })

    def test_a_returned_session_is_counted_from_a_fresh_jacoco_report(self) -> None:
        module.mark_session(self.discovery, {"index": 2, "kind": "mixed", "targets": 12})
        os.environ["RHEI_ITERATION"] = "5"

        with mock.patch.object(module, "run_jacoco_report", return_value=JACOCO_PATH) as gradle:
            module.count_session(self.discovery, "/worktree", "g:a:1")

        gradle.assert_called_once_with("/worktree", "g:a:1")
        count: dict = self._count(5)
        self.assertEqual(2, count["session"]["index"])
        self.assertEqual(3, count["summary"]["covered"])
        self.assertFalse(os.path.exists(os.path.join(self.discovery, module.SESSION_MARKER)))

    def test_a_later_pass_start_counts_nothing(self) -> None:
        os.environ["RHEI_ITERATION"] = "3"

        with mock.patch.object(module, "run_jacoco_report") as gradle:
            self.assertIsNone(module.count_session(self.discovery, "/worktree", "g:a:1"))

        gradle.assert_not_called()
        self.assertEqual([], os.listdir(self.discovery))

    def test_a_failed_report_writes_nothing_and_clears_the_session(self) -> None:
        module.mark_session(self.discovery, {"index": 1})
        os.environ["RHEI_ITERATION"] = "1"

        with mock.patch.object(module, "run_jacoco_report", return_value=None):
            self.assertIsNone(module.count_session(self.discovery, "/worktree", "g:a:1"))

        self.assertEqual([], os.listdir(self.discovery))

    def test_dispatch_marks_each_session_it_hands_out(self) -> None:
        sessions: list[dict] = []
        for index in (1, 2):
            prompt: str = os.path.join(self.discovery, f"session-{index}.md")
            with open(prompt, "w", encoding="utf-8") as prompt_file:
                prompt_file.write(f"session {index}\n")
            sessions.append({"index": index, "kind": "mixed", "targetIds": ["t"], "prompt": prompt})
        report: str = os.path.join(self.discovery, "discovery-report-0.json")
        with open(report, "w", encoding="utf-8") as report_file:
            json.dump({"deepSessions": sessions}, report_file)
        queue: str = os.path.join(self.root, "queue.json")
        prompt_path: str = os.path.join(self.root, "prompt.md")
        write_queue(report, queue)

        def mark(session: dict) -> None:
            module.mark_session(self.discovery, session)

        self.assertEqual(DISPATCH_SESSION, dispatch(queue, prompt_path, on_session=mark))
        with open(os.path.join(self.discovery, module.SESSION_MARKER), encoding="utf-8") as marker:
            self.assertEqual(1, json.load(marker)["index"])
        self.assertEqual(DISPATCH_SESSION, dispatch(queue, prompt_path, on_session=mark))
        self.assertEqual(DISPATCH_PASS_SPENT, dispatch(queue, prompt_path, on_session=mark))
        with open(os.path.join(self.discovery, module.SESSION_MARKER), encoding="utf-8") as marker:
            self.assertEqual(2, json.load(marker)["index"])


if __name__ == "__main__":
    unittest.main()
