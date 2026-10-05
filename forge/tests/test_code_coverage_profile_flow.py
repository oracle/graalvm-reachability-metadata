# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Tests for the control-flow table, control-flow fork selection, and the
caller's line region.

§AR-code-coverage-deep-navigation.1.3, §AR-code-coverage-deep-navigation.3,
§AR-code-coverage-deep-navigation.3.2
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
            flow.reaches(successor, body_bci, condition.bci) for successor in condition.successors
        ]
        self.assertEqual(sorted(reaching), [False, True])

    def test_exception_edges_reach_the_handler_from_both_sides(self) -> None:
        signature: str = "guarded(java.lang.String):int"
        flow: MethodFlow = self._flow(signature)
        lines: tuple[tuple[int, int], ...] = self._lines[f"{_OWNER}#{signature}"]
        (condition,) = flow.branches
        handler_bci: int = next(bci for bci, line in lines if line == 45)
        for successor in condition.successors:
            self.assertTrue(flow.reaches(successor, handler_bci, condition.bci))

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
        self.assertTrue(flow.reaches(0, 2, 6))
        self.assertFalse(flow.reaches(9, 2, 6))

    def test_block_lookup_before_the_first_block_is_absent(self) -> None:
        flow: MethodFlow = _flow([(2, (3, 5))], {1: (3, 5), 3: (), 5: ()})
        self.assertIsNone(flow.block_of(0))
        self.assertFalse(flow.reaches(0, 3, 2))


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


def _store_method(name: str, params: tuple[str, ...], returns: str) -> MethodRef:
    return MethodRef("example.Store", name, params, returns)


def _store_coverage(ref: MethodRef, line: int, covered: bool) -> JacocoMethodCoverage:
    return JacocoMethodCoverage(
        method_ref=ref, covered=covered, source_path="example/Store.java",
        source_line=line, report_paths=("fixture.xml",),
    )


class NestedLambdaRegionTests(unittest.TestCase):
    """A lambda body nested in the caller does not end the caller's line region,
    though JaCoCo reports it as a method starting inside its creator
    (§AR-code-coverage-deep-navigation.3)."""

    # The line and control-flow tables are javac's for this source:
    #
    #     13  Queue<Chunk> queue = new PriorityQueue<>((a, b) -> {
    #     14      int diff = Integer.compare(a.priority, b.priority);
    #     15      return diff != 0 ? diff : Long.compare(a.block, b.block);
    #     16  });
    #     17  for (Chunk chunk : chunks) {
    #     18      if (chunk.isSaved() && chunk.block > startBlock) {
    #     19          chunk.priority = getMovePriority(chunk);
    #     20          queue.offer(chunk);
    #     ...
    #     31  SecureRandom random = new SecureRandom();
    #     32  Thread seeder = new Thread(() -> {
    #     33      byte[] seed = random.generateSeed(20);
    #     34      random.setSeed(seed);
    #     35  });
    #     36  try {
    #     37      seeder.start();
    #     38      seeder.join(400);
    #     39  } catch (Exception e) {
    #     40      warn("SecureRandom", e);
    FIND = _store_method("findChunksToMove", ("long",), "java.util.Queue")
    PRIORITY = _store_method("getMovePriority", ("example.Store$Chunk",), "int")
    RANDOM = _store_method("getSecureRandom", (), "java.security.SecureRandom")
    WARN = _store_method("warn", ("java.lang.String", "java.lang.Exception"), "void")

    def setUp(self) -> None:
        self.graph = CallGraph(
            methods={1: self.FIND, 2: self.PRIORITY, 3: self.RANDOM, 4: self.WARN},
            invoke_fan_out={10: [2], 11: [4]},
        )
        comparator: MethodRef = _store_method(
            "lambda$findChunksToMove$0", ("example.Store$Chunk", "example.Store$Chunk"), "int"
        )
        seeder: MethodRef = _store_method(
            "lambda$getSecureRandom$0", ("java.security.SecureRandom",), "void"
        )
        self.jacoco_methods: dict[str, JacocoMethodCoverage] = {
            ref.canonical_id: _store_coverage(ref, line, covered)
            for ref, line, covered in (
                (self.FIND, 13, True), (comparator, 14, False), (self.PRIORITY, 27, False),
                (self.RANDOM, 31, True), (seeder, 33, True), (self.WARN, 46, False),
            )
        }
        self.jacoco_lines: dict[str, dict[int, JacocoLineCoverage]] = {"example/Store.java": {
            line: JacocoLineCoverage(*counters) for line, counters in {
                13: (0, 5, 0, 0), 14: (6, 0, 0, 0), 15: (12, 0, 2, 0), 17: (0, 11, 0, 2),
                18: (0, 8, 2, 2), 19: (5, 0, 0, 0), 20: (4, 0, 0, 0), 22: (0, 1, 0, 0),
                23: (0, 2, 0, 0), 27: (2, 0, 0, 0), 31: (0, 4, 0, 0), 32: (0, 6, 0, 0),
                33: (0, 4, 0, 0), 34: (0, 3, 0, 0), 35: (0, 1, 0, 0), 37: (0, 2, 0, 0),
                38: (0, 3, 0, 0), 39: (1, 0, 0, 0), 40: (3, 0, 0, 0), 41: (0, 1, 0, 0),
                42: (0, 2, 0, 0), 46: (1, 0, 0, 0),
            }.items()
        }}
        self.evidence = NavigationEvidence(
            line_numbers={
                self.FIND.canonical_id: (
                    (0, 13), (13, 17), (46, 18), (64, 19), (75, 20), (84, 22), (87, 23),
                ),
                self.RANDOM.canonical_id: (
                    (0, 31), (8, 32), (22, 37), (26, 38), (33, 41), (36, 39), (37, 40), (43, 42),
                ),
            },
            # A straight-line method such as `getSecureRandom` has no flow row.
            flows={self.FIND.canonical_id: _flow(
                [(31, (34, 87)), (51, (54, 84)), (61, (64, 84))],
                {0: (24,), 24: (87, 34), 34: (84, 54), 54: (84, 64), 64: (84,), 84: (24,), 87: ()},
            )},
        )

    def _classify(self, caller: int, callee: int, bci: int, invoke_id: int, line: int) -> dict:
        edge: dict = {
            "caller": caller, "callee": callee, "bci": str(bci), "is_direct": "true",
            "kind": "call", "invoke_id": invoke_id, "source_line": line,
        }
        return edge_miss_classification(
            edge, self.graph, self.jacoco_methods, self.jacoco_lines, self.evidence
        )

    def test_a_lambda_above_a_guarded_call_keeps_the_guard_as_the_fork(self) -> None:
        classification: dict = self._classify(1, 2, 69, 10, 19)
        self.assertEqual(classification["kind"], "fork-not-taken")
        self.assertEqual(
            (classification["target"]["line"], classification["fork"]["line"]), (19, 18)
        )
        self.assertEqual(classification["fork"]["evidence"], "control-flow")

    def test_a_lambda_above_a_catch_keeps_the_call_line_as_the_target(self) -> None:
        classification: dict = self._classify(3, 4, 40, 11, 40)
        self.assertEqual(classification["kind"], "no-fork")
        self.assertEqual(classification["target"]["line"], 40)
        self.assertEqual(classification["nearestCovered"]["line"], 38)


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
