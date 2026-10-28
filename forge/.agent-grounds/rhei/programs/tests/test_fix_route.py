# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Tests for `fix-route` (§AR-forge-workflow-pipeline.2, §AR-forge-workflow-pipeline.6)."""
from __future__ import annotations

import os
import tempfile
import unittest

from support import run_state, working_directory

import fix_route  # noqa: E402
import forge_run  # noqa: E402
from exit_codes import CANNOT_FIX, INFRASTRUCTURE, RETURN_FINALIZE, RETURN_MEASURE, RETURN_NATIVE, RETURN_REPAIR  # noqa: E402
from run_state import ROUTE_REPAIR, read_json, runtime_path, save, write_json  # noqa: E402


class RouteTest(unittest.TestCase):

    def test_fixed_returns_to_the_sender(self) -> None:
        for sender, code in (("repair", RETURN_REPAIR), ("measure", RETURN_MEASURE), ("native", RETURN_NATIVE),
                             ("finalize", RETURN_FINALIZE)):
            state = run_state(ROUTE_REPAIR, pending_request=sender)
            self.assertEqual(code, fix_route.route(state, {"result": "fixed"}))

    def test_cannot_fix_stops_the_run(self) -> None:
        state = run_state(ROUTE_REPAIR, pending_request="native")
        self.assertEqual(CANNOT_FIX, fix_route.route(state, {"result": "cannot-fix", "summary": "shared harness bug"}))

    def test_a_missing_report_is_no_verdict_so_the_sender_decides(self) -> None:
        state = run_state(ROUTE_REPAIR, pending_request="finalize")
        self.assertEqual(RETURN_FINALIZE, fix_route.route(state, {}))

    def test_no_open_request_is_an_infrastructure_failure(self) -> None:
        self.assertEqual(INFRASTRUCTURE, fix_route.route(run_state(ROUTE_REPAIR), {"result": "fixed"}))


class ReportTest(unittest.TestCase):

    def setUp(self) -> None:
        self.workspace: str = tempfile.mkdtemp()

    def test_latest_visit_wins(self) -> None:
        write_json(runtime_path(self.workspace, "reports", "fix-2.json"), {"result": "fixed"})
        write_json(runtime_path(self.workspace, "reports", "fix-10.json"), {"result": "cannot-fix"})
        self.assertEqual("cannot-fix", fix_route.latest_report(self.workspace)["result"])

    def test_every_fix_visit_counts_as_one_request(self) -> None:
        for name in ("fix-1.json", "fix-2.json", "cover-1.json"):
            write_json(runtime_path(self.workspace, "reports", name), {"result": "fixed"})
        self.assertEqual(2, fix_route.fix_requests(self.workspace))
        self.assertEqual(0, fix_route.fix_requests(tempfile.mkdtemp()))

    def test_unreadable_report_is_empty(self) -> None:
        path = runtime_path(self.workspace, "reports", "fix-1.json")
        with open(path, "w", encoding="utf-8") as handle:
            handle.write("not json")
        self.assertEqual({}, fix_route.latest_report(self.workspace))

    def test_cannot_fix_records_the_human_intervention_outcome(self) -> None:
        save(self.workspace, run_state(ROUTE_REPAIR, pending_request="native"))
        write_json(runtime_path(self.workspace, "reports", "fix-1.json"),
                   {"result": "cannot-fix", "changed": [], "summary": "a shared harness defect"})
        with working_directory(self.workspace):
            self.assertEqual(CANNOT_FIX, forge_run.main(["fix-route"]))
        outcome = read_json(runtime_path(self.workspace, forge_run.OUTCOME))
        self.assertEqual("human-intervention", outcome["outcome"])
        self.assertTrue(os.path.isfile(runtime_path(self.workspace, "cannot-fix.json")))


if __name__ == "__main__":
    unittest.main()
