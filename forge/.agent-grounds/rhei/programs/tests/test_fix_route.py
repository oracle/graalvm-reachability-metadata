# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Tests for `fix-route` (§AR-forge-workflow-pipeline.2, §AR-forge-workflow-pipeline.6).

A run it stops records what the close-out quotes (§AR-forge-workflow-pipeline.1),
and each fix request files a report of its own (§FS-durable-generation-logs).
"""
from __future__ import annotations

import os
import tempfile
import unittest

import yaml

from support import play_fix, run_state, working_directory
from test_audit import TEMPLATE

import fix_route  # noqa: E402
import forge_run  # noqa: E402
import handoff  # noqa: E402
from exit_codes import (  # noqa: E402
    CANNOT_FIX,
    INFRASTRUCTURE,
    RETURN_AUDIT,
    RETURN_FINALIZE,
    RETURN_JVM,
    RETURN_MEASURE,
    RETURN_NATIVE,
    RETURN_NATIVE_REPAIR,
)
from run_state import RUNTIME_DIR, ROUTE_REPAIR, RunState, load, read_json, runtime_path, save  # noqa: E402

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
        self.state: RunState = run_state(ROUTE_REPAIR)

    def send(self, sender: str) -> None:
        """Send one fix request and save the state, as every sender does before it exits to `fix`."""
        handoff.write_fix_request(self.workspace, self.state, sender, [handoff.Failure(COMMAND, ERROR)])
        save(self.workspace, self.state)

    def route(self) -> int:
        with working_directory(self.workspace):
            return forge_run.main(["fix-route"])

    def test_the_template_declares_the_open_report(self) -> None:
        with open(TEMPLATE, encoding="utf-8") as handle:
            fix: dict = yaml.safe_load(handle)["states"]["fix"]
        self.assertEqual([os.path.join(RUNTIME_DIR, fix_route.OPEN_REPORT)], [out["path"] for out in fix["outputs"]])

    def test_each_request_files_its_own_report(self) -> None:
        for sender, code in (("native", RETURN_NATIVE), ("finalize", RETURN_FINALIZE), ("audit", RETURN_AUDIT)):
            self.send(sender)
            play_fix(self.workspace, summary=f"{sender} round")
            self.assertEqual(code, self.route())
        state: RunState = load(self.workspace)
        self.assertEqual(["native round", "finalize round", "audit round"],
                         [read_json(fix_route.report_path(self.workspace, n))["summary"] for n in (1, 2, 3)])
        self.assertEqual((["native", "finalize", "audit"], [COMMAND]),
                         ([fix.sender for fix in state.fix_rounds], state.fix_rounds[1].commands))
        self.assertEqual(3, fix_route.fix_requests(state))
        self.assertEqual("audit round", fix_route.latest_report(self.workspace, state)["summary"])

    def test_a_visit_without_a_report_reuses_none(self) -> None:
        self.send("native")
        play_fix(self.workspace, "cannot-fix", "two failures remain")
        self.assertEqual(RETURN_NATIVE, self.route())
        self.send("finalize")
        # The last round's `cannot-fix` would stop this one: neither Rhei nor `fix-route` may see it.
        self.assertFalse(os.path.exists(runtime_path(self.workspace, fix_route.OPEN_REPORT)))
        self.assertEqual(RETURN_FINALIZE, self.route())
        self.assertEqual({}, fix_route.latest_report(self.workspace, load(self.workspace)))
        self.assertFalse(os.path.exists(fix_route.report_path(self.workspace, 2)))

    def test_unreadable_report_is_empty_but_filed(self) -> None:
        self.send("finalize")
        with open(runtime_path(self.workspace, fix_route.OPEN_REPORT), "w", encoding="utf-8") as handle:
            handle.write("not json")
        self.assertEqual(RETURN_FINALIZE, self.route())
        self.assertEqual({}, fix_route.latest_report(self.workspace, load(self.workspace)))
        self.assertTrue(os.path.isfile(fix_route.report_path(self.workspace, 1)))

    def test_no_request_has_no_report(self) -> None:
        self.assertEqual((0, {}), (fix_route.fix_requests(self.state),
                                   fix_route.latest_report(self.workspace, self.state)))

    def test_cannot_fix_records_the_human_intervention_outcome(self) -> None:
        state = run_state(ROUTE_REPAIR)
        handoff.write_fix_request(self.workspace, state, "finalize", [handoff.Failure(COMMAND, NATIVE_OUTPUT)])
        save(self.workspace, state)
        play_fix(self.workspace, "cannot-fix", "a shared harness defect")
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
        play_fix(self.workspace, "cannot-fix", "x")
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
