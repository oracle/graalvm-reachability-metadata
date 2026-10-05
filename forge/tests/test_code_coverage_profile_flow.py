# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Tests for the control-flow table and control-flow fork selection.

§AR-code-coverage-deep-navigation.1.3, §AR-code-coverage-deep-navigation.3.2
"""

import os
import subprocess
import tempfile
import unittest

from utility_scripts.code_coverage_jacoco import JacocoLineCoverage, JacocoMethodCoverage
from utility_scripts.code_coverage_model import MethodRef
from utility_scripts.code_coverage_profile_counters import InstrumentedCounters
from utility_scripts.code_coverage_profile_flow import (
    Branch,
    MethodFlow,
    load_library_flow,
)
from utility_scripts.code_coverage_profile_graph import CallGraph
from utility_scripts.code_coverage_profile_inputs import line_at, load_library_line_numbers
from utility_scripts.code_coverage_profile_miss import edge_miss_classification
from utility_scripts.code_coverage_profile_navigation import NavigationEvidence
from utility_scripts.code_coverage_profile_render import classification_lines

from tests.code_coverage_rank_test_utils import EXTRACTOR, _java_tool

_BRANCHY = """\
package com.example;

public class Branchy {
    enum Mode { ONE, TWO }

    static int both(boolean a, boolean b) {
        if (a && b) {
            return 1;
        }
        return 2;
    }

    static int word(String key) {
        switch (key) {
            case "alpha":
                return 1;
            case "beta":
                return 2;
            default:
                return 0;
        }
    }

    static int mode(Mode mode) {
        return switch (mode) {
            case ONE -> 1;
            case TWO -> 2;
        };
    }

    static int loop(int n) {
        int total = 0;
        for (int i = 0; i < n; i++) {
            total += i;
        }
        return total;
    }

    static int guarded(String text) {
        try {
            if (text.isEmpty()) {
                return 0;
            }
            return Integer.parseInt(text);
        } catch (NumberFormatException error) {
            return -1;
        }
    }

    static int straight() {
        return 3;
    }
}
"""

_OWNER = "com.example.Branchy"


@unittest.skipIf(_java_tool("java") is None, "java is required")
@unittest.skipIf(_java_tool("javac") is None, "javac is required")
class ExtractorControlFlowTests(unittest.TestCase):
    """The extractor's own `flow.csv`, from a class javac really compiled."""

    @classmethod
    def setUpClass(cls) -> None:
        cls._directory = tempfile.TemporaryDirectory()
        root: str = cls._directory.name
        source_dir: str = os.path.join(root, "com", "example")
        os.makedirs(source_dir)
        source: str = os.path.join(source_dir, "Branchy.java")
        with open(source, "w", encoding="utf-8") as handle:
            handle.write(_BRANCHY)
        subprocess.run([_java_tool("javac"), "-g", source], cwd=root, check=True)
        jar: str = os.path.join(root, "library.jar")
        subprocess.run([_java_tool("jar"), "cf", jar, "-C", root, "com"], cwd=root, check=True)
        graph_dir: str = os.path.join(root, "graph")
        subprocess.run(
            [_java_tool("java"), EXTRACTOR, "--output-dir", graph_dir, jar],
            check=True, capture_output=True, text=True,
        )
        cls._flows = load_library_flow(os.path.join(graph_dir, "flow.csv"))
        cls._lines = load_library_line_numbers(os.path.join(graph_dir, "methods.csv"))

    @classmethod
    def tearDownClass(cls) -> None:
        cls._directory.cleanup()

    def _flow(self, signature: str) -> MethodFlow:
        return self._flows[f"{_OWNER}#{signature}"]

    def test_short_circuit_condition_chains_into_the_next_one(self) -> None:
        flow: MethodFlow = self._flow("both(boolean,boolean):int")
        first, second = flow.branches
        self.assertFalse(first.plumbing or second.plumbing)
        self.assertEqual(first.successors[-1], second.successors[-1])
        self.assertIn(flow.block_of(second.bci), first.successors)

    def test_string_switch_keeps_only_the_case_switch(self) -> None:
        flow: MethodFlow = self._flow("word(java.lang.String):int")
        decisions: list[Branch] = [branch for branch in flow.branches if not branch.plumbing]
        self.assertEqual(len(decisions), 1)
        self.assertEqual(len(decisions[0].successors), 3)
        self.assertGreater(len(flow.branches), 1, "the hashCode switch is recorded as plumbing")

    def test_exhaustive_switch_default_is_synthetic(self) -> None:
        (switch,) = self._flow("mode(com.example.Branchy$Mode):int").branches
        self.assertEqual(len(switch.synthetic), 1)
        self.assertEqual(len(switch.reachable_successors), 2)

    def test_loop_condition_does_not_reach_through_its_own_back_edge(self) -> None:
        signature: str = "loop(int):int"
        flow: MethodFlow = self._flow(signature)
        lines: tuple[tuple[int, int], ...] = self._lines[f"{_OWNER}#{signature}"]
        (condition,) = flow.branches
        body_bci: int = next(bci for bci, line in lines if line == 34)
        reaching: list[bool] = [
            flow.reaches(successor, body_bci, (condition.bci,)) for successor in condition.successors
        ]
        self.assertEqual(sorted(reaching), [False, True])

    def test_exception_edges_reach_the_handler_from_both_sides(self) -> None:
        signature: str = "guarded(java.lang.String):int"
        flow: MethodFlow = self._flow(signature)
        lines: tuple[tuple[int, int], ...] = self._lines[f"{_OWNER}#{signature}"]
        (condition,) = flow.branches
        handler_bci: int = next(bci for bci, line in lines if line == 45)
        for successor in condition.successors:
            self.assertTrue(flow.reaches(successor, handler_bci, (condition.bci,)))

    def test_straight_line_methods_have_no_row(self) -> None:
        self.assertNotIn(f"{_OWNER}#straight():int", self._flows)


def _flow(branches: list[tuple[int, tuple[int, ...]]], blocks: dict[int, tuple[int, ...]]) -> MethodFlow:
    return MethodFlow(
        branches=tuple(Branch(bci, successors, frozenset(), False) for bci, successors in branches),
        block_starts=tuple(sorted(blocks)),
        block_successors=blocks,
    )


class ReachabilityTests(unittest.TestCase):

    def test_back_edge_into_the_branch_block_reaches_code_before_the_branch(self) -> None:
        # do { target(); } while (cond); target at bci 2, the branch at bci 6.
        flow: MethodFlow = _flow([(6, (0, 9))], {0: (0, 9), 9: ()})
        self.assertTrue(flow.reaches(0, 2, (6,)))
        self.assertFalse(flow.reaches(9, 2, (6,)))

    def test_block_lookup_before_the_first_block_is_absent(self) -> None:
        flow: MethodFlow = _flow([(2, (3, 5))], {1: (3, 5), 3: (), 5: ()})
        self.assertIsNone(flow.block_of(0))
        self.assertFalse(flow.reaches(0, 3, (2,)))


class ControlFlowForkTests(unittest.TestCase):
    """Fork selection by control flow rather than by line order."""

    CALLER = MethodRef("example.Router", "route", (), "void")
    TARGET = MethodRef("example.Handler", "handle", (), "void")

    def setUp(self) -> None:
        # Line 1: `if (a)` at bci 2 skips to bci 20. Line 2: `x = b ? 1 : 2` at
        # bci 5 merges again at 10. Line 3: the target call at bci 12.
        self.flow: MethodFlow = _flow(
            [(2, (3, 20)), (5, (6, 8))],
            {0: (3, 20), 3: (6, 8), 6: (10,), 8: (10,), 10: (20,), 20: ()},
        )
        self.graph = CallGraph(methods={1: self.CALLER, 2: self.TARGET}, invoke_fan_out={10: [2]})
        self.edge: dict = {
            "caller": 1, "callee": 2, "bci": "12", "is_direct": "true",
            "kind": "call", "invoke_id": 10, "source_line": 3,
        }
        self.jacoco_methods = {self.CALLER.canonical_id: JacocoMethodCoverage(
            method_ref=self.CALLER, covered=True, source_path="example/Router.java",
            source_line=1, report_paths=("fixture.xml",),
        )}
        self.jacoco_lines = {"example/Router.java": {
            1: JacocoLineCoverage(mi=0, ci=3, mb=1, cb=1),
            2: JacocoLineCoverage(mi=0, ci=4, mb=1, cb=1),
            3: JacocoLineCoverage(mi=2, ci=0, mb=0, cb=0),
            4: JacocoLineCoverage(mi=0, ci=1, mb=0, cb=0),
        }}
        self.lines: dict[str, tuple[tuple[int, int], ...]] = {
            self.CALLER.canonical_id: ((0, 1), (3, 2), (10, 3), (20, 4)),
        }

    def _classify(self, counters: InstrumentedCounters | None = None, flows: bool = True) -> dict:
        evidence = NavigationEvidence(
            line_numbers=self.lines,
            flows={self.CALLER.canonical_id: self.flow} if flows else None,
            counters=counters,
        )
        return edge_miss_classification(
            self.edge, self.graph, self.jacoco_methods, self.jacoco_lines, evidence
        )

    def test_a_merging_branch_is_skipped_for_the_one_that_decides_the_call(self) -> None:
        classification: dict = self._classify()
        self.assertEqual(classification["kind"], "fork-not-taken")
        fork: dict = classification["fork"]
        self.assertEqual((fork["line"], fork["evidence"]), (1, "control-flow"))
        self.assertEqual(
            [(successor["line"], successor["reachesTarget"]) for successor in fork["branches"][0]["successors"]],
            [(2, True), (4, False)],
        )

    def test_without_a_table_the_nearest_missed_branch_line_is_the_fork(self) -> None:
        fork: dict = self._classify(flows=False)["fork"]
        self.assertEqual((fork["line"], fork["evidence"], fork["branches"]), (2, "line-order", None))

    def test_a_branch_whose_target_side_ran_is_not_the_reason(self) -> None:
        counters = InstrumentedCounters(branches={(self.CALLER.canonical_id, 2): {3: 9, 20: 4}})
        classification: dict = self._classify(counters)
        self.assertEqual(classification["kind"], "no-fork")

    def test_counts_and_reach_come_from_the_counters(self) -> None:
        counters = InstrumentedCounters(branches={(self.CALLER.canonical_id, 2): {20: 40182}})
        classification: dict = self._classify(counters)
        self.assertEqual(classification["reach"], 40182)
        counts: list[int | None] = [
            successor["count"] for successor in classification["fork"]["branches"][0]["successors"]
        ]
        self.assertEqual(counts, [None, 40182])

    def test_a_conditional_expression_on_the_call_line_is_its_own_fork(self) -> None:
        # `return c ? call() : 0` - the branch at bci 11 precedes the call at 12.
        self.flow = _flow([(11, (12, 16))], {0: (12, 16), 12: (20,), 16: (20,), 20: ()})
        self.lines = {self.CALLER.canonical_id: ((0, 3), (20, 4))}
        self.jacoco_lines["example/Router.java"][3] = JacocoLineCoverage(mi=2, ci=3, mb=1, cb=1)
        fork: dict = self._classify()["fork"]
        self.assertEqual((fork["line"], fork["evidence"]), (3, "control-flow"))

    def test_a_loop_does_not_re_enter_an_or_condition_around_its_barrier(self) -> None:
        # h2 `Tokenizer.tokenize`: a loop headed at bci 0 holds, on line 2,
        # `if (c2 == 'X' || c2 == 'x') { readHexNumber(); continue; }`. Block 3
        # tests 'X', block 7 tests 'x', both jump to the call block 10, and the
        # else side at 20 loops back through block 3, which a one-block barrier
        # around block 7 lets it pass.
        self.flow = _flow(
            [(1, (3, 30)), (6, (7, 10)), (9, (10, 20))],
            {0: (3, 30), 3: (7, 10), 7: (10, 20), 10: (0,), 20: (0,), 30: ()},
        )
        self.lines = {self.CALLER.canonical_id: ((0, 1), (3, 2), (10, 3), (20, 4), (30, 5))}
        self.jacoco_lines["example/Router.java"].update({
            1: JacocoLineCoverage(mi=0, ci=3, mb=0, cb=2),
            2: JacocoLineCoverage(mi=0, ci=6, mb=2, cb=2),
            5: JacocoLineCoverage(mi=0, ci=1, mb=0, cb=0),
        })
        counters = InstrumentedCounters(branches={
            (self.CALLER.canonical_id, 6): {7: 54, 10: 0},
            (self.CALLER.canonical_id, 9): {10: 0, 20: 54},
        })
        classification: dict = self._classify(counters)
        self.assertEqual(classification["kind"], "fork-not-taken")
        fork: dict = classification["fork"]
        self.assertEqual((fork["line"], fork["evidence"]), (2, "control-flow"))
        self.assertEqual(
            [
                [(successor["line"], successor["count"], successor["reachesTarget"])
                 for successor in branch["successors"]]
                for branch in fork["branches"]
            ],
            [[(2, 54, False), (3, 0, True)], [(3, 0, True), (4, 54, False)]],
        )


class ForkRenderingTests(unittest.TestCase):

    FORK: dict = {
        "sourcePath": "org/h2/engine/Database.java", "line": 313,
        "mi": 0, "ci": 9, "mb": 2, "cb": 2, "evidence": "control-flow", "reach": 40182,
        "branches": [
            {"bci": 3, "blockStart": 0, "reach": 40182, "successors": [
                {"bci": 6, "line": 313, "count": 40182, "reachesTarget": False},
                {"bci": 15, "line": 320, "count": 0, "reachesTarget": True},
            ]},
            {"bci": 7, "blockStart": 6, "reach": 40182, "successors": [
                {"bci": 10, "line": 314, "count": 40182, "reachesTarget": False},
                {"bci": 15, "line": 320, "count": None, "reachesTarget": True},
            ]},
        ],
    }

    def _lines(self, counted: bool) -> list[str]:
        return classification_lines({
            "kind": "fork-not-taken",
            "target": {"sourcePath": "org/h2/engine/Database.java", "line": 322,
                       "mi": 3, "ci": 0, "mb": 0, "cb": 0},
            "fork": self.FORK,
        }, counted)

    def test_each_branch_is_one_numbered_line_with_landing_and_count(self) -> None:
        self.assertEqual(self._lines(counted=True)[1:], [
            "  fork `Database.java:313` reached 40,182×, 2 of 4 branches taken",
            "    branch 1 → condition 2 ×40,182",
            "    branch 2 → line 320 ×0 ← target",
            "    branch 3 → line 314 ×40,182",
            "    branch 4 → line 320 (no counter) ← target",
        ])

    def test_without_counters_the_hint_keeps_landings_and_the_target_marker(self) -> None:
        self.assertEqual(
            self._lines(counted=False)[2:4],
            ["    branch 1 → condition 2", "    branch 2 → line 320 ← target"],
        )

    def test_a_single_line_conditional_keeps_its_two_branches_apart(self) -> None:
        """`return c != null ? c : parse(key)`: both sides land on the fork
        line itself, so only the number, count, and marker tell them apart."""
        lines: list[str] = classification_lines({
            "kind": "fork-not-taken",
            "target": {"sourcePath": "a/Config.java", "line": 44,
                       "mi": 2, "ci": 3, "mb": 1, "cb": 1},
            "fork": {
                "sourcePath": "a/Config.java", "line": 44,
                "mi": 2, "ci": 3, "mb": 1, "cb": 1, "evidence": "control-flow", "reach": 1204,
                "branches": [{"bci": 9, "blockStart": 0, "reach": 1204, "successors": [
                    {"bci": 12, "line": 44, "count": 1204, "reachesTarget": False},
                    {"bci": 16, "line": 44, "count": 0, "reachesTarget": True},
                ]}],
            },
        }, counted=True)
        self.assertEqual(lines[1:], [
            "  fork `Config.java:44` reached 1,204×, 1 of 2 branches taken",
            "    branch 1 → line 44 ×1,204",
            "    branch 2 → line 44 ×0 ← target",
        ])

    def test_a_wide_switch_keeps_numbering_when_it_omits_cold_cases(self) -> None:
        successors: list[dict] = [
            {"bci": 20 + index, "line": 50 + index, "count": 0, "reachesTarget": index == 11}
            for index in range(12)
        ]
        successors[0]["count"] = 77
        lines: list[str] = classification_lines({
            "kind": "fork-not-taken",
            "target": {"sourcePath": "a/Op.java", "line": 61,
                       "mi": 2, "ci": 0, "mb": 0, "cb": 0},
            "fork": {
                "sourcePath": "a/Op.java", "line": 49, "mi": 0, "ci": 3, "mb": 11, "cb": 1,
                "evidence": "control-flow", "reach": 77,
                "branches": [{"bci": 4, "blockStart": 0, "reach": 77, "successors": successors}],
            },
        }, counted=True)
        self.assertEqual(lines[2], "    branch 1 → line 50 ×77")
        self.assertEqual(lines[-2], "    branch 12 → line 61 ×0 ← target")
        self.assertEqual(lines[-1], "    … 4 more")

    def test_line_table_lookup_uses_the_last_entry_at_or_before_the_bci(self) -> None:
        self.assertEqual(line_at(((0, 10), (4, 11), (9, 12)), 8), 11)
        self.assertIsNone(line_at(((5, 10),), 2))


if __name__ == "__main__":
    unittest.main()
