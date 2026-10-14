# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Tests for unobserved call steps: route hops whose call line JaCoCo reports uncovered.

§AR-code-coverage-deep-navigation.2.1
"""

import os
import shutil
import tempfile
import unittest

from utility_scripts.code_coverage_jacoco import JacocoLineCoverage, JacocoMethodCoverage
from utility_scripts.code_coverage_model import MethodRef
from utility_scripts import code_coverage_profile_report as report_module
from utility_scripts.code_coverage_profile_graph import CallGraph
from utility_scripts.code_coverage_profile_render import write_markdown
from utility_scripts.code_coverage_profile_routes import execution_status, mark_unobserved_calls

from tests.code_coverage_profile_support import _coverage, _sampled_at

ENTRY = MethodRef("app.Api", "run", (), "void")
# `Alpha` sorts first, so without call-line evidence the canonical id would pick it.
UNSEEN = MethodRef("app.Alpha", "step", (), "void")
SEEN = MethodRef("app.Beta", "step", (), "void")
TARGET = MethodRef("app.Internal", "target", (), "void")
ONLY_VIA_UNSEEN = MethodRef("app.Internal", "rare", (), "void")
ENTRY_SOURCE = "app/Api.java"
UNSEEN_LINE = 10
SEEN_LINE = 11


def _edge(caller: int, callee: int, source_line: int | None) -> dict:
    edge: dict = {"caller": caller, "callee": callee, "bci": "1", "is_direct": "true",
                  "kind": "call"}
    if source_line is not None:
        edge["source_line"] = source_line
    return edge


class UnobservedCallStepTests(unittest.TestCase):
    """`Api.run()` calls `Alpha.step()` on a line no test completed and `Beta.step()`
    on a covered one; both are covered, and both call the uncovered target."""

    def setUp(self) -> None:
        methods: dict[int, MethodRef] = {
            1: ENTRY, 2: UNSEEN, 3: SEEN, 4: TARGET, 5: ONLY_VIA_UNSEEN,
        }
        self.graph = CallGraph(
            methods=methods,
            key_to_id={ref.canonical_id: static_id for static_id, ref in methods.items()},
            adjacency={
                1: [_edge(1, 2, UNSEEN_LINE), _edge(1, 3, SEEN_LINE)],
                2: [_edge(2, 4, 20), _edge(2, 5, 21)],
                3: [_edge(3, 4, 30)],
            },
        )
        self.methods: dict[str, JacocoMethodCoverage] = {
            ENTRY.canonical_id: JacocoMethodCoverage(
                ENTRY, True, ENTRY_SOURCE, UNSEEN_LINE, ("fixture.xml",)),
            UNSEEN.canonical_id: _coverage(UNSEEN, covered=True),
            SEEN.canonical_id: _coverage(SEEN, covered=True),
            TARGET.canonical_id: _coverage(TARGET),
            ONLY_VIA_UNSEEN.canonical_id: _coverage(ONLY_VIA_UNSEEN),
        }
        self.lines: dict[str, dict[int, JacocoLineCoverage]] = {
            ENTRY_SOURCE: {
                UNSEEN_LINE: JacocoLineCoverage(mi=3, ci=0, mb=0, cb=0),
                SEEN_LINE: JacocoLineCoverage(mi=0, ci=3, mb=0, cb=0),
            },
            "app/Alpha.java": {
                20: JacocoLineCoverage(mi=3, ci=0, mb=0, cb=0),
                21: JacocoLineCoverage(mi=3, ci=0, mb=0, cb=0),
            },
            "app/Beta.java": {30: JacocoLineCoverage(mi=3, ci=0, mb=0, cb=0)},
        }

    def _flags(self) -> list[tuple[int, int]]:
        return [
            (edge["caller"], edge["callee"])
            for edges in self.graph.adjacency.values() for edge in edges
            if edge.get("unobserved_call")
        ]

    def test_only_a_step_between_covered_methods_on_an_uncovered_line_is_flagged(self) -> None:
        executed: dict[int, bool] = execution_status(self.graph, self.methods)
        marked: int = mark_unobserved_calls(self.graph, self.methods, self.lines, executed)
        # The last calls into the uncovered targets sit on uncovered lines too,
        # which is what miss classification expects there.
        self.assertEqual((marked, self._flags()), (1, [(1, 2)]))

    def test_a_step_without_a_line_makes_no_claim(self) -> None:
        del self.graph.adjacency[1][0]["source_line"]
        executed: dict[int, bool] = execution_status(self.graph, self.methods)
        self.assertEqual(mark_unobserved_calls(self.graph, self.methods, self.lines, executed), 0)

    def test_a_dispatch_step_keeps_its_own_kind(self) -> None:
        self.graph.adjacency[1][0]["unobserved"] = True
        executed: dict[int, bool] = execution_status(self.graph, self.methods)
        self.assertEqual(mark_unobserved_calls(self.graph, self.methods, self.lines, executed), 0)

    def test_route_prefers_the_completed_call_and_the_prompt_names_a_needed_one(self) -> None:
        report, records = report_module.correlate(
            _sampled_at(1, ENTRY),
            self.graph,
            {"targets": [{"id": ENTRY.canonical_id, "kind": "method"}]},
            self.methods,
            jacoco_lines=self.lines,
        )
        by_id: dict[str, dict] = {entry["id"]: entry for entry in report["uncoveredPaths"]}
        self.assertEqual(by_id[TARGET.canonical_id]["reachingPath"][1], SEEN.canonical_id)
        self.assertEqual(by_id[TARGET.canonical_id]["unobservedSteps"], [])
        self.assertEqual(
            by_id[ONLY_VIA_UNSEEN.canonical_id]["unobservedSteps"],
            [{"caller": ENTRY.canonical_id, "callee": UNSEEN.canonical_id, "kind": "call"}],
        )
        self.assertEqual(report["summary"]["unobservedCallEdges"], 1)
        markdown_path: str = os.path.join(tempfile.mkdtemp(prefix="call-steps-"), "prompt.md")
        self.addCleanup(shutil.rmtree, os.path.dirname(markdown_path), True)
        write_markdown(report, records, self.graph, "app:api:1", 0, markdown_path)
        with open(markdown_path, encoding="utf-8") as handle:
            self.assertIn(
                "route assumes `Api.run() → Alpha.step()`, "
                "a call on a line JaCoCo reports uncovered",
                handle.read(),
            )


if __name__ == "__main__":
    unittest.main()
