# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Tests for deep-pass group sessions: grouping, prompts, queue, and dispatch.

§AR-code-coverage-deep-sessions
"""

import json
import os
import shutil
import tempfile
import unittest

from utility_scripts import code_coverage_profile_report as report_module
from utility_scripts.code_coverage_deep_sessions import (
    DISPATCH_FAILED,
    DISPATCH_PASS_SPENT,
    DISPATCH_SESSION,
    MAX_SESSION_TARGETS,
    MAX_SESSIONS_PER_PASS,
    MIN_SESSION_TARGETS,
    MIXED,
    MONOLITH,
    dispatch,
    group_sessions,
    write_queue,
)
from utility_scripts.code_coverage_profile_records import MAX_LISTED_METHODS
from utility_scripts.code_coverage_profile_render import write_markdown

from tests.code_coverage_profile_support import FIXTURES, JACOCO_PATH


class GroupSessionsTest(unittest.TestCase):

    def test_large_groups_are_monoliths_and_small_groups_pool(self) -> None:
        owners: list[str] = (
            ["com.acme.cart.Cart"] * 12
            + ["com.acme.cart.CartView"] * 3
            + ["com.acme.report.ReportBuilder"] * 30
            + ["com.acme.util.Strings"] * 4
        )
        sessions: list[list[int]] = group_sessions(owners)

        self.assertEqual(
            [[owners[position] for position in session] for session in sessions],
            [
                ["com.acme.cart.Cart"] * 12,
                # Pooled by package: the cart view, the report remainder, util.
                ["com.acme.cart.CartView"] * 3
                + ["com.acme.report.ReportBuilder"] * 5
                + ["com.acme.util.Strings"] * 4,
                ["com.acme.report.ReportBuilder"] * MAX_SESSION_TARGETS,
            ],
        )

    def test_sessions_run_in_order_of_their_best_ranked_target(self) -> None:
        owners: list[str] = ["a.Small"] + ["b.Big"] * MIN_SESSION_TARGETS
        sessions: list[list[int]] = group_sessions(owners)

        self.assertEqual(sessions, [[0], list(range(1, MIN_SESSION_TARGETS + 1))])

    def test_every_target_lands_in_exactly_one_session(self) -> None:
        owners: list[str] = [f"p{index % 7}.C{index % 13}" for index in range(MAX_LISTED_METHODS)]
        sessions: list[list[int]] = group_sessions(owners)

        self.assertEqual(
            sorted(position for session in sessions for position in session),
            list(range(MAX_LISTED_METHODS)),
        )
        self.assertTrue(all(len(session) <= MAX_SESSION_TARGETS for session in sessions))

    def test_the_ceiling_holds_for_the_worst_split(self) -> None:
        """Every session but the last pooled one holds the minimum, so a full
        prompt never needs more sessions than the visit caps allow."""
        self.assertEqual(MAX_SESSIONS_PER_PASS, MAX_LISTED_METHODS // MIN_SESSION_TARGETS + 1)
        exact: list[str] = [
            f"p.C{index // MIN_SESSION_TARGETS}" for index in range(MAX_LISTED_METHODS)
        ]
        self.assertEqual(len(group_sessions(exact)), MAX_LISTED_METHODS // MIN_SESSION_TARGETS)
        almost: list[str] = [
            f"p.C{index // MIN_SESSION_TARGETS}" for index in range(MAX_LISTED_METHODS - 9)
        ] + [f"q.Tail{index}" for index in range(9)]
        self.assertLessEqual(len(group_sessions(almost)), MAX_SESSIONS_PER_PASS)
        scattered: list[str] = [f"p.C{index}" for index in range(MAX_LISTED_METHODS)]
        self.assertEqual(
            len(group_sessions(scattered)), -(-MAX_LISTED_METHODS // MAX_SESSION_TARGETS)
        )


class SessionPromptTest(unittest.TestCase):

    def setUp(self) -> None:
        self.output_dir: str = tempfile.mkdtemp(prefix="deep-sessions-")
        self.addCleanup(shutil.rmtree, self.output_dir, True)

    def _placement(self, session: dict) -> str:
        path: str = os.path.join(self.output_dir, "session.md")
        report: dict = {"summary": {}, "bulkTargets": []}
        write_markdown(report, [], None, "com.acme:shop:1.0", 3, path, session=session)
        with open(path, encoding="utf-8") as prompt_file:
            return prompt_file.read()

    def test_monolith_prompt_asks_for_one_class(self) -> None:
        markdown: str = self._placement({
            "index": 1, "count": 2, "kind": MONOLITH, "entryOwners": ["com.acme.cart.Cart"],
        })

        self.assertIn("(iteration 3, session 1 of 2)", markdown)
        self.assertIn("through `Cart`", markdown)
        self.assertIn("one test class", markdown)
        self.assertNotIn("more than one test file", markdown)

    def test_mixed_prompt_expects_several_test_files(self) -> None:
        markdown: str = self._placement({
            "index": 2,
            "count": 2,
            "kind": MIXED,
            "entryOwners": ["com.acme.cart.CartView", "com.acme.util.Strings"],
        })

        self.assertIn("`CartView`, `Strings`", markdown)
        self.assertIn("expect to change more than one test file", markdown)

    def test_report_records_and_renders_every_session(self) -> None:
        report: dict = report_module.generate_report(
            profile_path=os.path.join(FIXTURES, "near_call_sampling.iprof"),
            reports_dir=FIXTURES,
            api_inventory_path=os.path.join(FIXTURES, "near_call_inventory.json"),
            jacoco_xml_paths=[JACOCO_PATH],
            coordinate="com.example:demo:1.0.0",
            iteration=1,
            output_dir=self.output_dir,
        )

        sessions: list[dict] = report["deepSessions"]
        self.assertTrue(sessions)
        self.assertEqual(
            sorted(target for session in sessions for target in session["targetIds"]),
            sorted(report["promptTargetIds"]),
        )
        for session in sessions:
            self.assertIn(session["kind"], {MONOLITH, MIXED})
            self.assertEqual(session["kind"] == MONOLITH, len(session["entryOwners"]) == 1)
            with open(os.path.join(self.output_dir, session["prompt"]), encoding="utf-8") as md_file:
                self.assertEqual(md_file.read().count("  target "), len(session["targetIds"]))
        with open(os.path.join(self.output_dir, "discovery-report-1.json"), encoding="utf-8") as json_file:
            self.assertEqual(json.load(json_file)["deepSessions"], sessions)


class DispatchTest(unittest.TestCase):

    def setUp(self) -> None:
        self.work: str = tempfile.mkdtemp(prefix="deep-dispatch-")
        self.addCleanup(shutil.rmtree, self.work, True)
        self.queue: str = os.path.join(self.work, "prompts", "deep-session-queue.json")
        self.prompt: str = os.path.join(self.work, "prompts", "deep-cover-prompt.md")

    def _report_with_sessions(self, count: int) -> str:
        discovery: str = os.path.join(self.work, "discovery")
        os.makedirs(os.path.join(discovery, "deep-sessions-4"))
        sessions: list[dict] = []
        for index in range(1, count + 1):
            prompt: str = os.path.join("deep-sessions-4", f"session-{index}.md")
            with open(os.path.join(discovery, prompt), "w", encoding="utf-8") as prompt_file:
                prompt_file.write(f"session {index}\n")
            sessions.append({
                "index": index, "kind": MONOLITH, "targetIds": ["t"], "prompt": prompt,
            })
        report_path: str = os.path.join(discovery, "discovery-report-4.json")
        with open(report_path, "w", encoding="utf-8") as report_file:
            json.dump({"deepSessions": sessions}, report_file)
        return report_path

    def _prompt(self) -> str:
        with open(self.prompt, encoding="utf-8") as prompt_file:
            return prompt_file.read()

    def test_hands_out_sessions_in_order_then_reports_the_pass_spent(self) -> None:
        self.assertEqual(write_queue(self._report_with_sessions(2), self.queue), 2)

        self.assertEqual(dispatch(self.queue, self.prompt), DISPATCH_SESSION)
        self.assertEqual(self._prompt(), "session 1\n")
        self.assertEqual(dispatch(self.queue, self.prompt), DISPATCH_SESSION)
        self.assertEqual(self._prompt(), "session 2\n")
        self.assertEqual(dispatch(self.queue, self.prompt), DISPATCH_PASS_SPENT)
        self.assertIn("No cover session follows", self._prompt())

    def test_an_empty_pass_returns_straight_to_measurement(self) -> None:
        self.assertEqual(write_queue(self._report_with_sessions(0), self.queue), 0)

        self.assertEqual(dispatch(self.queue, self.prompt), DISPATCH_PASS_SPENT)

    def test_an_unreadable_queue_fails(self) -> None:
        self.assertEqual(dispatch(self.queue, self.prompt), DISPATCH_FAILED)
        os.makedirs(os.path.dirname(self.queue))
        with open(self.queue, "w", encoding="utf-8") as queue_file:
            queue_file.write("{}")
        self.assertEqual(dispatch(self.queue, self.prompt), DISPATCH_FAILED)


if __name__ == "__main__":
    unittest.main()
