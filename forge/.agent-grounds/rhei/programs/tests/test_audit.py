# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The audit and the finalize reviewer gates (§AR-forge-workflow-pipeline.4.3).

The audit is one ordinary fix request: the tree it leaves decides whether the
run publishes, reruns the checks once, or stops in `human-intervention`; each
reviewer gate fails `finalize` with a brief that names it.
"""
from __future__ import annotations

import json
import os
import subprocess
import unittest
from typing import Any

import yaml

from support import FakeRepository, working_directory
from test_prepare import prepare_arguments
from test_workflow import URLS

import audit  # noqa: E402
import forge_run  # noqa: E402
import gates  # noqa: E402
from exit_codes import (  # noqa: E402
    AGENT_WORK,
    CANNOT_FIX,
    NOTHING_TO_DO,
    OK,
    RETURN_AUDIT,
    STILL_FAILING,
)
from run_state import RunState, load, read_json, runtime_path, write_json  # noqa: E402

TEMPLATE: str = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
                             "templates", "forge-metadata", "states.yaml")
REQUEST: str = 'It fails at runtime; please add:\n```json\n{"reflection": [{"type": "org.example.lib.Codec"}]}\n```\n'


class FinalizedRun(unittest.TestCase):
    """A fake run brought up to `finalize`, and the steps that play the agents after it."""

    def setUp(self) -> None:
        self.fake: FakeRepository = FakeRepository()
        self.addCleanup(self.fake.close)

    def step(self, *argv: str) -> int:
        with working_directory(self.fake.workspace):
            return forge_run.main(list(argv))

    @property
    def state(self) -> RunState:
        return load(self.fake.workspace)

    def read(self, name: str) -> str:
        with open(runtime_path(self.fake.workspace, name), encoding="utf-8") as handle:
            return handle.read()

    def outcome(self) -> str:
        return read_json(runtime_path(self.fake.workspace, forge_run.OUTCOME))["outcome"]

    def write(self, relative: str, text: str) -> None:
        path: str = self.state.path(relative)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(text)

    def repaired(self, label: str = "library-new-request", body: str = "") -> None:
        """Prepare and set up the issue, and let `repair` pass: the run is ready for `finalize`."""
        if label != "library-new-request":
            self.fake.supported_library()
        self.fake.issue(label, body=body)
        self.assertEqual(OK, self.step(*prepare_arguments()))
        state: RunState = self.state
        with open(state.path(state.index_path), encoding="utf-8") as handle:
            entries: list[dict[str, Any]] = json.load(handle)
        for entry in entries:
            entry.update(URLS)
        with open(state.path(state.index_path), "w", encoding="utf-8") as handle:
            json.dump(entries, handle, indent=2)
        checked: int = self.step("setup-check")
        self.assertIn(checked, (OK, NOTHING_TO_DO))
        if checked == OK:
            self.assertEqual(OK, self.step("repair"))

    def audited(self, result: str = "fixed") -> int:
        """Finalize, send the audit, play `fix`, and route back."""
        self.assertEqual(OK, self.step("finalize"))
        self.assertEqual(AGENT_WORK, self.step("audit"))
        return self.routed(result)

    def routed(self, result: str = "fixed") -> int:
        """Play the audit's `fix` and route back."""
        write_json(runtime_path(self.fake.workspace, "reports", "fix-1.json"),
                   {"result": result, "changed": [], "summary": "played"})
        return self.step("fix-route")

    def gradle_calls(self, task: str) -> int:
        with open(self.state.path("gradle-calls.log"), encoding="utf-8") as handle:
            return sum(1 for line in handle if line.split()[0] == task)


class AuditTest(FinalizedRun):

    def test_the_audit_request_is_an_ordinary_fix_request(self) -> None:
        self.repaired()
        self.assertEqual(OK, self.step("finalize"))
        self.assertEqual(AGENT_WORK, self.step("audit"))
        request: str = self.read("fix-request.md")
        self.assertIn("Return: 34", request)
        for command in ("./gradlew nativeTest -Pcoordinates=org.example:lib:2.0.0", "./gradlew spotlessCheck",
                        "./gradlew generateLibraryStats -Pcoordinates=org.example:lib:2.0.0", "gates.py"):
            self.assertIn(command, request)
        self.assertIn("cheating patterns", request)
        self.assertNotIn("## Output", request)
        self.assertNotIn("stats/org.example", request.split("## You may edit")[1])
        self.assertEqual("audit", self.state.pending_request)

    def test_the_audit_brief_sets_the_scaffold_and_zero_call_bars(self) -> None:
        self.repaired()
        self.assertEqual(OK, self.step("finalize"))
        self.assertEqual(AGENT_WORK, self.step("audit"))
        request: str = self.read("fix-request.md")
        self.assertIn("never rewrite a test\n  because it looks thin", request)
        self.assertIn("reports no dynamic-access calls", request)
        self.assertIn("reporter-request.md", request)

    def test_an_update_audits_only_what_the_run_changed(self) -> None:
        self.repaired()
        state: RunState = self.state
        state.previous_coordinate = "org.example:lib:1.0.0"
        text: str = audit.brief(state)
        self.assertIn("tests/src/org.example/lib/1.0.0/", text)
        self.assertNotIn("since `", text)

    def test_an_audit_with_no_edits_publishes(self) -> None:
        self.repaired()
        self.assertEqual(RETURN_AUDIT, self.audited())
        self.assertEqual(OK, self.step("audit"))
        self.assertEqual(1, self.gradle_calls("generateLibraryStats"))
        self.assertTrue(self.state.audit_passed)
        self.assertEqual(OK, self.step("publish"))
        self.assertEqual("completed", self.outcome())

    def test_an_audit_edit_reruns_the_checks_and_publishes(self) -> None:
        self.repaired()
        self.assertEqual(RETURN_AUDIT, self.audited())
        state: RunState = self.state
        self.write(os.path.join(state.test_dir, "src", "test", "java", "org_example", "CodecTest.java"),
                   "package org_example;\npublic class CodecTest {}\n")
        self.assertEqual(OK, self.step("audit"))
        self.assertEqual((2, 1), (self.gradle_calls("generateLibraryStats"), self.gradle_calls("nativeTest")))
        self.assertIn("CodecTest.java", self.head_files())
        # A resumed run never audits twice.
        self.assertEqual(OK, self.step("audit"))
        self.assertEqual(OK, self.step("publish"))
        self.assertEqual("completed", self.outcome())

    def head_files(self) -> str:
        state: RunState = self.state
        return subprocess.run(["git", "show", "--name-only", "--format=", "HEAD"], cwd=state.worktree, check=True,
                              stdout=subprocess.PIPE, text=True).stdout

    def test_an_audit_edit_that_breaks_a_gate_needs_a_human(self) -> None:
        self.repaired()
        self.assertEqual(RETURN_AUDIT, self.audited())
        state: RunState = self.state
        self.write(os.path.join(state.test_dir, "src", "test", "resources", "META-INF", "native-image",
                                "reflect-config.json"), "[]\n")
        self.assertEqual(STILL_FAILING, self.step("audit"))
        self.assertEqual("human-intervention", self.outcome())
        self.assertFalse(self.state.audit_passed)

    def test_cannot_fix_needs_a_human(self) -> None:
        self.repaired()
        self.assertEqual(CANNOT_FIX, self.audited("cannot-fix"))
        self.assertEqual("human-intervention", self.outcome())

    def test_a_coverage_drop_fails_finalize_with_the_coverage_brief(self) -> None:
        self.repaired("library-update-request")
        self.fake.control({"stats": {"coveredCalls": 1, "totalCalls": 6, "coverageRatio": 0.17, "breakdown": {}}})
        self.assertEqual(AGENT_WORK, self.step("finalize"))
        request: str = self.read("fix-request.md")
        self.assertIn("Return: 33", request)
        self.assertIn("- dynamic-access coverage gate:", request)
        self.assertIn("dropped below 1.5.0's 2/6", request)

    def test_missing_requested_metadata_fails_finalize_with_its_brief(self) -> None:
        self.repaired(body=REQUEST)
        self.assertEqual({"types": ["org.example.lib.Codec"], "globs": []}, self.state.requested_metadata)
        self.assertEqual(AGENT_WORK, self.step("finalize"))
        request: str = self.read("fix-request.md")
        self.assertIn("- reporter-requested metadata gate:", request)
        self.assertIn("lacks type org.example.lib.Codec", request)
        self.write(os.path.join(self.state.metadata_dir, "reachability-metadata.json"),
                   json.dumps({"reflection": [{"type": "org.example.lib.Codec"}]}))
        self.assertEqual(OK, self.step("finalize"))

    def test_a_path_outside_the_closed_file_set_fails_finalize_with_the_scope_brief(self) -> None:
        self.repaired("library-update-request")
        self.write("metadata/org.example/lib/1.5.0/reachability-metadata.json", '{"reflection": []}\n')
        self.assertEqual(AGENT_WORK, self.step("finalize"))
        request: str = self.read("fix-request.md")
        self.assertIn("- diff-scope gate:", request)
        self.assertIn("metadata/org.example/lib/1.5.0/reachability-metadata.json", request)

    def test_the_skip_record_bypasses_the_audit(self) -> None:
        with open(TEMPLATE, encoding="utf-8") as handle:
            transitions: list[dict[str, Any]] = yaml.safe_load(handle)["transitions"]
        into_publish: set[tuple[str, Any]] = {(edge["from"], edge.get("exit_code")) for edge in transitions
                                              if edge["to"] == "publish"}
        self.assertEqual({("setup-check", 21), ("audit", 0)}, into_publish)
        self.assertIn({"from": "fix-route", "to": "audit", "exit_code": RETURN_AUDIT},
                      [{key: edge.get(key) for key in ("from", "to", "exit_code")} for edge in transitions])


class GateTest(unittest.TestCase):

    def test_the_repair_comparison_runs_in_order(self) -> None:
        cases: list[tuple[tuple[int, int], tuple[int, int], bool]] = [
            ((5, 10), (0, 0), True),     # 1. no calls left
            ((5, 10), (0, 4), False),    # 2. calls, none covered
            ((5, 10), (5, 20), True),    # 3. as many covered calls
            ((2, 4), (3, 9), True),      # 3. more covered calls at a lower ratio
            ((5, 8), (3, 9), True),      # 4. small scopes lose at most two
            ((6, 8), (3, 9), False),     # 4. three lost
            ((50, 100), (35, 100), True),   # 5. 15 points
            ((50, 100), (29, 100), False),  # 5. 21 points
        ]
        for previous, new, passes in cases:
            self.assertEqual(passes, gates.repair_scope_passes(gates.Scope(*previous), gates.Scope(*new)),
                             (previous, new))

    def test_breakdowns_and_na_are_scopes(self) -> None:
        found = gates.scopes({"coveredCalls": 3, "totalCalls": 4,
                              "breakdown": {"reflection": {"coveredCalls": 1, "totalCalls": 2}}})
        self.assertEqual({"overall": gates.Scope(3, 4), "reflection": gates.Scope(1, 2)}, found)
        self.assertEqual({"overall": gates.Scope(0, 0)}, gates.scopes("N/A"))

    def test_only_explicit_metadata_json_is_a_request(self) -> None:
        body: str = ("MissingReflectionRegistrationError: org.example.Other\n"
                     '```\n[{"name": "org.example.Legacy"}]\n```\n'
                     '```json\n{"resources": [{"glob": "lib/*.properties"}]}\n```\n```text\nnot json\n```\n')
        self.assertEqual({"types": ["org.example.Legacy"], "globs": ["lib/*.properties"]},
                         gates.requested_entries(body))
        self.assertEqual({}, gates.requested_entries("Please support this version."))


if __name__ == "__main__":
    unittest.main()
