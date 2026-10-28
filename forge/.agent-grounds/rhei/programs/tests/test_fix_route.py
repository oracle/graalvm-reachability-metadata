# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Tests for `fix-route` (§AR-forge-workflow-pipeline.2, §AR-forge-workflow-pipeline.6).

A run it stops records what the close-out quotes (§AR-forge-workflow-pipeline.1).
"""
from __future__ import annotations

import os
import tempfile
import unittest

from support import run_state, working_directory

import fix_route  # noqa: E402
import forge_run  # noqa: E402
import handoff  # noqa: E402
from exit_codes import (  # noqa: E402
    CANNOT_FIX,
    INFRASTRUCTURE,
    RETURN_FINALIZE,
    RETURN_JVM,
    RETURN_MEASURE,
    RETURN_NATIVE,
    RETURN_NATIVE_REPAIR,
)
from run_state import ROUTE_REPAIR, read_json, runtime_path, save, write_json  # noqa: E402

COMMAND: str = "env JAVA_HOME=/jdk ./gradlew clean nativeTestCompile -Pcoordinates=org.example:lib:2.0.0"
ERROR: str = "Error: Type not found during analysis: AnalysisType<X509CertInfo>"
# A native build whose one error sits among progress lines, twice, as Native Image prints it.
NATIVE_OUTPUT: str = "\n".join([*(f"[{n}/8] Step" for n in range(40)), ERROR, "Trace: reached by", ERROR,
                                 *(f"  at frame{n}" for n in range(20)), "BUILD FAILED in 1m"])


class RouteTest(unittest.TestCase):

    def test_fixed_returns_to_the_sender(self) -> None:
        for sender, code in (("jvm", RETURN_JVM), ("native-repair", RETURN_NATIVE_REPAIR),
                             ("measure", RETURN_MEASURE), ("native", RETURN_NATIVE), ("finalize", RETURN_FINALIZE)):
            state = run_state(ROUTE_REPAIR, pending_request=sender)
            self.assertEqual(code, fix_route.route(state, {"result": "fixed"}))

    def test_cannot_fix_stops_the_run(self) -> None:
        state = run_state(ROUTE_REPAIR, pending_request="native-repair")
        self.assertEqual(CANNOT_FIX, fix_route.route(state, {"result": "cannot-fix", "summary": "shared harness bug"}))

    def test_cannot_fix_returns_to_native_whose_budget_decides(self) -> None:
        state = run_state(ROUTE_REPAIR, pending_request="native")
        self.assertEqual(RETURN_NATIVE, fix_route.route(state, {"result": "cannot-fix", "summary": "two left"}))

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
        state = run_state(ROUTE_REPAIR)
        handoff.write_fix_request(self.workspace, state, "finalize", [handoff.Failure(COMMAND, NATIVE_OUTPUT)])
        save(self.workspace, state)
        write_json(runtime_path(self.workspace, "reports", "fix-1.json"),
                   {"result": "cannot-fix", "changed": [], "summary": "a shared harness defect"})
        with working_directory(self.workspace):
            self.assertEqual(CANNOT_FIX, forge_run.main(["fix-route"]))
        outcome = read_json(runtime_path(self.workspace, forge_run.OUTCOME))
        self.assertEqual("human-intervention", outcome["outcome"])
        self.assertTrue(os.path.isfile(runtime_path(self.workspace, "cannot-fix.json")))
        # The state that sent the request stopped, not `fix-route`.
        self.assertEqual(("fix-route", "finalize"), (outcome["step"], outcome["sender"]))
        self.assertEqual([{"command": COMMAND, "output": f"{ERROR}\nBUILD FAILED in 1m"}], outcome["failures"])
        self.assertEqual(("cannot-fix", "a shared harness defect"), (outcome["fix_result"], outcome["fix_summary"]))

    def test_the_audit_request_has_no_failing_command_to_quote(self) -> None:
        state = run_state(ROUTE_REPAIR)
        handoff.write_fix_request(self.workspace, state, "audit", [handoff.Failure(COMMAND, "")], "Check it.")
        save(self.workspace, state)
        write_json(runtime_path(self.workspace, "reports", "fix-1.json"), {"result": "cannot-fix", "summary": "x"})
        with working_directory(self.workspace):
            self.assertEqual(CANNOT_FIX, forge_run.main(["fix-route"]))
        outcome = read_json(runtime_path(self.workspace, forge_run.OUTCOME))
        self.assertEqual(("audit", []), (outcome["sender"], outcome["failures"]))

    def test_an_infrastructure_failure_records_no_diagnostics(self) -> None:
        handoff.record_failures(self.workspace, "native", [handoff.Failure(COMMAND, ERROR)])
        with working_directory(self.workspace):
            forge_run._write_result(self.workspace, "native", INFRASTRUCTURE)
        self.assertNotIn("failures", read_json(runtime_path(self.workspace, forge_run.OUTCOME)))


class ExcerptTest(unittest.TestCase):

    def test_a_short_output_is_quoted_whole(self) -> None:
        output: str = "> Task :checkstyle\n[ERROR] A.java:1: bad"
        self.assertEqual(output, forge_run.excerpt(output + "\n"))

    def test_a_long_output_keeps_its_error_lines_once_each(self) -> None:
        self.assertEqual(f"{ERROR}\nBUILD FAILED in 1m", forge_run.excerpt(NATIVE_OUTPUT))

    def test_the_excerpt_is_bounded(self) -> None:
        quoted: list[str] = forge_run.excerpt("\n".join(f"Error: {n} {'x' * 400}" for n in range(50))).splitlines()
        self.assertEqual(forge_run.EXCERPT_LINES, len(quoted))
        self.assertTrue(quoted[0].startswith("Error: 0 "))
        self.assertEqual(forge_run.EXCERPT_WIDTH, max(len(line) for line in quoted))

    def test_a_long_output_without_error_lines_keeps_its_tail(self) -> None:
        lines: list[str] = [f"line {n}" for n in range(50)]
        self.assertEqual("\n".join(lines[-forge_run.EXCERPT_LINES:]), forge_run.excerpt("\n".join(lines)))


if __name__ == "__main__":
    unittest.main()
