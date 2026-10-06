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
    DeadRegion,
    MethodFlow,
    load_library_flow,
)
from utility_scripts.code_coverage_profile_graph import CallGraph
from utility_scripts.code_coverage_profile_inputs import line_at, load_library_line_numbers
from utility_scripts.code_coverage_profile_miss import edge_miss_classification
from utility_scripts.code_coverage_profile_navigation import NavigationEvidence

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

    static int thrower(boolean fail) {
        try {
            if (fail) {
                throw new IllegalStateException("fail");
            }
            return 1;
        } catch (IllegalStateException error) {
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

    def test_a_never_entered_loop_body_forks_at_the_condition(self) -> None:
        signature: str = "loop(int):int"
        flow: MethodFlow = self._flow(signature)
        lines: tuple[tuple[int, int], ...] = self._lines[f"{_OWNER}#{signature}"]
        (condition,) = flow.branches
        body_bci: int = next(bci for bci, line in lines if line == 34)
        region: DeadRegion | None = flow.dead_region(
            body_bci, lambda branch: None, lambda bci: line_at(lines, bci) != 34
        )
        assert region is not None
        body_block: int | None = flow.block_of(body_bci)
        self.assertEqual(region.forks, ((condition, body_block),))
        self.assertEqual(
            [successor in region.blocks for successor in condition.successors],
            [successor == body_block for successor in condition.successors],
        )

    def test_exception_edges_into_a_handler_are_not_walked(self) -> None:
        signature: str = "guarded(java.lang.String):int"
        flow: MethodFlow = self._flow(signature)
        lines: tuple[tuple[int, int], ...] = self._lines[f"{_OWNER}#{signature}"]
        handler_bci: int = next(bci for bci, line in lines if line == 45)
        handler_block: int | None = flow.block_of(handler_bci)
        covered: list[int] = [
            block for block, handlers in flow.exception_successors.items() if handler_block in handlers
        ]
        self.assertTrue(covered, "every block in the try range carries the handler as an exception edge")
        for block in covered:
            self.assertNotIn(handler_block, flow.normal_successors(block))
        region: DeadRegion | None = flow.dead_region(
            handler_bci, lambda branch: None, lambda bci: line_at(lines, bci) != 45
        )
        assert region is not None
        self.assertEqual(region.forks, ())

    def test_a_return_inside_a_try_has_no_normal_exit(self) -> None:
        flow: MethodFlow = self._flow("thrower(boolean):int")
        throwing: list[int] = [
            block for block in flow.block_starts
            if flow.exception_successors.get(block) and not flow.normal_successors(block)
        ]
        self.assertTrue(throwing, "the `throw` block leaves only through its handler")

    def test_straight_line_methods_have_no_row(self) -> None:
        self.assertNotIn(f"{_OWNER}#straight():int", self._flows)


def _flow(
        branches: list[tuple[int, tuple[int, ...]]],
        blocks: dict[int, tuple[int, ...]],
        handlers: dict[int, frozenset[int]] | None = None,
) -> MethodFlow:
    return MethodFlow(
        branches=tuple(Branch(bci, successors, frozenset(), False) for bci, successors in branches),
        block_starts=tuple(sorted(blocks)),
        block_successors=blocks,
        exception_successors=handlers or {},
    )


class DeadRegionTests(unittest.TestCase):
    """The backward walk on hand-built tables (§AR-code-coverage-deep-navigation.3.2)."""

    # Block 0 `if` (bci 2) -> 3 or 6. Block 3 sits in a `try` whose handler
    # starts at 12; it and the handler fall into the call block 9, block 6
    # returns.
    FLOW: MethodFlow = _flow(
        [(2, (3, 6))], {0: (3, 6), 3: (9, 12), 6: (), 9: (), 12: (9,)}, {3: frozenset({12})},
    )

    def test_predecessors_invert_the_successor_table(self) -> None:
        self.assertEqual(self.FLOW.predecessors(), {3: (0,), 6: (0,), 9: (3, 12), 12: (3,)})

    def test_a_branch_that_ran_forks_into_the_dead_arm(self) -> None:
        counts = {2: {3: 0, 6: 8}}
        region: DeadRegion | None = self.FLOW.dead_region(
            10, lambda branch: counts.get(branch.bci), lambda bci: None
        )
        assert region is not None
        self.assertEqual(region.blocks, {9, 3, 12})
        self.assertEqual(region.forks, ((self.FLOW.branches[0], 3),))

    def test_the_handler_is_entered_only_by_an_exception_edge(self) -> None:
        self.assertEqual(self.FLOW.normal_successors(3), (9,))
        region: DeadRegion | None = self.FLOW.dead_region(
            12, lambda branch: None, lambda bci: True
        )
        assert region is not None
        self.assertEqual((region.blocks, region.forks), ({12}, ()))

    def test_a_positive_count_into_a_dead_block_stops_the_walk(self) -> None:
        counts = {2: {3: 5, 6: 0}}
        region: DeadRegion | None = self.FLOW.dead_region(
            4, lambda branch: counts.get(branch.bci), lambda bci: None
        )
        assert region is not None
        self.assertEqual((region.blocks, region.forks), ({3}, ()))

    def test_a_branch_whose_every_arm_is_dead_is_no_fork(self) -> None:
        # Both arms end in the never-run call block: the branch ran and left
        # by an exception, so it decided nothing.
        flow: MethodFlow = _flow([(2, (3, 6))], {0: (3, 6), 3: (9,), 6: (9,), 9: ()})
        counts = {2: {3: 9, 6: 0}}
        region: DeadRegion | None = flow.dead_region(
            10, lambda branch: counts.get(branch.bci), lambda bci: None
        )
        assert region is not None
        self.assertEqual((region.blocks, region.forks), ({3, 6, 9}, ()))

    def test_without_a_counter_the_walk_passes_a_covered_branch_whose_arms_are_dead(self) -> None:
        flow: MethodFlow = _flow([(2, (3, 6))], {0: (3, 6), 3: (9,), 6: (9,), 9: ()})
        region: DeadRegion | None = flow.dead_region(10, lambda branch: None, lambda bci: True)
        assert region is not None
        self.assertEqual((region.blocks, region.forks), ({0, 3, 6, 9}, ()))

    def test_unknown_liveness_stops_the_walk(self) -> None:
        flow: MethodFlow = _flow([(2, (3, 6))], {0: (3, 6), 3: (9,), 6: (9,), 9: ()})
        region: DeadRegion | None = flow.dead_region(10, lambda branch: None, lambda bci: None)
        assert region is not None
        self.assertEqual((region.blocks, region.forks), ({3, 6, 9}, ()))

    def test_block_lookup_before_the_first_block_is_absent(self) -> None:
        flow: MethodFlow = _flow([(2, (3, 5))], {1: (3, 5), 3: (), 5: ()})
        self.assertIsNone(flow.block_of(0))
        self.assertIsNone(flow.dead_region(0, lambda branch: None, lambda bci: None))


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

    def test_a_loop_does_not_carry_the_walk_around_an_or_condition(self) -> None:
        # h2 `Tokenizer.tokenize`: a loop headed at bci 0 holds, on line 2,
        # `if (c2 == 'X' || c2 == 'x') { readHexNumber(); continue; }`. Block 3
        # tests 'X', block 7 tests 'x', both jump to the call block 10, and the
        # else side at 20 loops back through block 3. The walk never leaves the
        # dead call block, so the loop cannot carry it around.
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

    def test_a_conditional_argument_on_the_call_line_does_not_hide_the_guard(self) -> None:
        # `if (ok) target(flag ? 1 : 2);` on line 3: the `if` at bci 2 and the
        # ternary at bci 5 share the line, and only the `if` ever ran.
        self.lines = {self.CALLER.canonical_id: ((0, 3), (20, 4))}
        self.jacoco_lines["example/Router.java"][3] = JacocoLineCoverage(mi=6, ci=3, mb=3, cb=1)
        counters = InstrumentedCounters(branches={
            (self.CALLER.canonical_id, 2): {3: 0, 20: 7},
            (self.CALLER.canonical_id, 5): {6: 0, 8: 0},
        })
        classification: dict = self._classify(counters)
        self.assertEqual(classification["kind"], "fork-not-taken")
        fork: dict = classification["fork"]
        self.assertEqual((fork["line"], fork["evidence"], fork["reach"]), (3, "control-flow", 7))
        self.assertEqual(
            [
                [(successor["line"], successor["count"], successor["reachesTarget"])
                 for successor in branch["successors"]]
                for branch in fork["branches"]
            ],
            [[(3, 0, True), (4, 7, False)], [(3, 0, True), (3, 0, True)]],
        )

    def test_a_branch_whose_arms_both_end_in_the_dead_call_is_no_fork(self) -> None:
        # `if (c) { foo(); } else { bar(); } target();` where `foo` always
        # threw: the `if` ran, yet neither arm avoids the never-run call.
        self.flow = _flow([(2, (3, 6))], {0: (3, 6), 3: (9,), 6: (9,), 9: ()})
        self.lines = {self.CALLER.canonical_id: ((0, 1), (3, 2), (6, 3), (9, 4))}
        self.edge["bci"] = "10"
        self.edge["source_line"] = 4
        self.jacoco_lines["example/Router.java"] = {
            1: JacocoLineCoverage(mi=0, ci=3, mb=1, cb=1),
            2: JacocoLineCoverage(mi=0, ci=2, mb=0, cb=0),
            3: JacocoLineCoverage(mi=2, ci=0, mb=0, cb=0),
            4: JacocoLineCoverage(mi=2, ci=0, mb=0, cb=0),
        }
        counters = InstrumentedCounters(branches={(self.CALLER.canonical_id, 2): {3: 9, 6: 0}})
        self.assertEqual(self._classify(counters)["kind"], "no-fork")
        self.assertEqual(self._classify()["kind"], "no-fork")


if __name__ == "__main__":
    unittest.main()
