# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Tests for the control-flow table and the backward dead-region walk.

§AR-code-coverage-deep-navigation.1.3, §AR-code-coverage-deep-navigation.3.2
"""

import os
import subprocess
import tempfile
import unittest

from utility_scripts.code_coverage_profile_flow import (
    Branch,
    DeadRegion,
    MethodFlow,
    load_library_flow,
)
from utility_scripts.code_coverage_profile_inputs import line_at, load_library_line_numbers

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

    static int shielded(String text) {
        try {
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

    def test_handler_edges_carry_the_caught_type_and_raising_blocks_are_marked(self) -> None:
        signature: str = "guarded(java.lang.String):int"
        flow: MethodFlow = self._flow(signature)
        lines: tuple[tuple[int, int], ...] = self._lines[f"{_OWNER}#{signature}"]
        handler_block: int | None = flow.block_of(next(bci for bci, line in lines if line == 45))
        self.assertEqual(flow.handler_types.get(handler_block), "java.lang.NumberFormatException")
        parse_block: int | None = flow.block_of(next(bci for bci, line in lines if line == 44))
        constant_block: int | None = flow.block_of(next(bci for bci, line in lines if line == 42))
        self.assertIn(parse_block, flow.throwing_blocks)
        self.assertNotIn(constant_block, flow.throwing_blocks)

    def test_a_return_inside_a_try_has_no_normal_exit(self) -> None:
        flow: MethodFlow = self._flow("thrower(boolean):int")
        throwing: list[int] = [
            block for block in flow.block_starts
            if flow.exception_successors.get(block) and not flow.normal_successors(block)
        ]
        self.assertTrue(throwing, "the `throw` block leaves only through its handler")

    def test_a_branch_free_try_has_a_row_with_exception_edges(self) -> None:
        flow: MethodFlow = self._flow("shielded(java.lang.String):int")
        self.assertEqual(flow.branches, ())
        self.assertTrue(any(flow.exception_successors.values()))
        self.assertEqual(set(flow.handler_types.values()), {"java.lang.NumberFormatException"})

    def test_straight_line_methods_have_no_row(self) -> None:
        self.assertNotIn(f"{_OWNER}#straight():int", self._flows)


# `try { if (text.isEmpty()) return 0; return parseInt(text); } catch (E e) {…}`:
# block 0 tests, block 7 returns 0, block 9 parses; handler 14 hangs off all three.
TRY: MethodFlow = MethodFlow(
    branches=(Branch(4, (7, 9), frozenset(), False),),
    block_starts=(0, 7, 8, 9, 13, 14),
    block_successors={0: (9, 7, 14), 7: (8, 14), 8: (), 9: (13, 14), 13: (), 14: ()},
    exception_successors={0: frozenset({14}), 7: frozenset({14}), 9: frozenset({14})},
    handler_types={14: "java.lang.NumberFormatException"},
    throwing_blocks=frozenset({0, 9}),
)


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

    def test_the_table_parses_exception_types_and_raising_marks(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path: str = os.path.join(directory, "flow.csv")
            with open(path, "w", encoding="utf-8") as handle:
                handle.write('id,branches,blocks\n')
                handle.write('"m","4>7,9","0!>9,7,14~java.lang.E;7>8,14~java.lang.E;8>;9!>13,14~java.lang.E;13>;14>"\n')
            flow: MethodFlow = load_library_flow(path)["m"]
        self.assertEqual(flow.block_successors[0], (9, 7, 14))
        self.assertEqual(flow.exception_successors[0], {14})
        self.assertEqual(flow.handler_types, {14: "java.lang.E"})
        self.assertEqual(flow.throwing_blocks, {0, 9})
        self.assertEqual(flow.normal_successors(9), (13,))

    def test_a_handler_entered_only_from_code_that_ran_is_a_catch_boundary(self) -> None:
        counts = {4: {7: 100, 9: 8_000_000}}
        region: DeadRegion | None = TRY.dead_region(
            14, lambda branch: counts.get(branch.bci), lambda bci: True
        )
        assert region is not None
        self.assertEqual((region.blocks, region.forks), ({14}, ()))
        self.assertEqual(region.catches, ((14, 0), (14, 7), (14, 9)))

    def test_block_counts_follow_the_branch_counters_forward(self) -> None:
        counts = {4: {7: 100, 9: 8_000_000}}
        self.assertEqual(
            TRY.block_counts(lambda branch: counts.get(branch.bci)),
            {0: 8_000_100, 7: 100, 8: 100, 9: 8_000_000, 13: 8_000_000, 14: None},
        )

    def test_an_exception_edge_out_of_a_dead_try_is_a_catch_boundary_too(self) -> None:
        # `if (mode) { try { parse(x); } catch (E e) { target(); } }` with `mode`
        # never true: the try body at block 3 never ran, and the hint reports it
        # under the handler at zero rather than walking through it to the `if`.
        flow: MethodFlow = _flow(
            [(2, (3, 20))], {0: (3, 20), 3: (20, 10), 10: (20,), 20: ()}, {3: frozenset({10})},
        )
        counts = {2: {3: 0, 20: 9}}
        region: DeadRegion | None = flow.dead_region(
            11, lambda branch: counts.get(branch.bci), lambda bci: bci >= 20
        )
        assert region is not None
        self.assertEqual((region.blocks, region.forks), ({10}, ()))
        self.assertEqual(region.catches, ((10, 3),))

    def test_block_lookup_before_the_first_block_is_absent(self) -> None:
        flow: MethodFlow = _flow([(2, (3, 5))], {1: (3, 5), 3: (), 5: ()})
        self.assertIsNone(flow.block_of(0))
        self.assertIsNone(flow.dead_region(0, lambda branch: None, lambda bci: None))


if __name__ == "__main__":
    unittest.main()
