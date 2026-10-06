# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Tests for fork selection and the catch boundary of one never-run invoke.

§AR-code-coverage-deep-navigation.3.2, §AR-code-coverage-deep-navigation.3.3
"""

import unittest

from utility_scripts.code_coverage_jacoco import JacocoLineCoverage, JacocoMethodCoverage
from utility_scripts.code_coverage_model import MethodRef
from utility_scripts.code_coverage_profile_counters import InstrumentedCounters
from utility_scripts.code_coverage_profile_flow import Branch, MethodFlow
from utility_scripts.code_coverage_profile_graph import CallGraph
from utility_scripts.code_coverage_profile_miss import edge_miss_classification
from utility_scripts.code_coverage_profile_navigation import NavigationEvidence
from utility_scripts.code_coverage_profile_render import classification_lines

from tests.test_code_coverage_profile_flow import TRY, _flow


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

    def test_a_target_in_a_never_entered_catch_names_the_raising_try_lines(self) -> None:
        # `try { if (text.isEmpty()) return 0; return Integer.parseInt(text); }
        #  catch (NumberFormatException e) { return handle(); }` where no input
        # ever failed to parse. Only lines holding a call can raise, and each
        # carries its own count: the `return 0` arm ran 100×, `parseInt` 8,000,000×.
        self.flow = TRY
        self.lines = {self.CALLER.canonical_id: (
            (0, 41), (7, 42), (9, 44), (14, 45), (15, 46), (18, 47),
        )}
        self.edge["bci"] = "15"
        self.edge["source_line"] = 46
        parse_int = MethodRef("java.lang.Integer", "parseInt", ("java.lang.String",), "int")
        is_empty = MethodRef("java.lang.String", "isEmpty", (), "boolean")
        self.graph = CallGraph(
            methods={1: self.CALLER, 2: self.TARGET, 3: parse_int, 4: is_empty},
            key_to_id={self.CALLER.canonical_id: 1},
            adjacency={1: [
                {"caller": 1, "callee": 4, "bci": "1", "kind": "call"},
                {"caller": 1, "callee": 3, "bci": "10", "kind": "call"},
                {"caller": 1, "callee": 2, "bci": "15", "kind": "call"},
            ]},
            invoke_fan_out={10: [2]},
        )
        self.jacoco_lines["example/Router.java"] = {
            41: JacocoLineCoverage(mi=0, ci=4, mb=0, cb=2),
            42: JacocoLineCoverage(mi=0, ci=2, mb=0, cb=0),
            44: JacocoLineCoverage(mi=0, ci=3, mb=0, cb=0),
            45: JacocoLineCoverage(mi=1, ci=0, mb=0, cb=0),
            46: JacocoLineCoverage(mi=3, ci=0, mb=0, cb=0),
            47: JacocoLineCoverage(mi=0, ci=1, mb=0, cb=0),
        }
        counters = InstrumentedCounters(branches={(self.CALLER.canonical_id, 4): {7: 100, 9: 8_000_000}})
        classification: dict = self._classify(counters)
        self.assertEqual(classification["kind"], "no-fork")
        self.assertEqual(classification["exception"], {"handlers": [{
            "bci": 14, "line": 45, "type": "java.lang.NumberFormatException",
            "sources": [
                {"line": 41, "count": 8_000_100, "calls": ["String.isEmpty"]},
                {"line": 44, "count": 8_000_000, "calls": ["Integer.parseInt"]},
            ],
        }]})
        self.assertEqual(classification_lines(classification, True)[1:], [
            "  reached only through catch (NumberFormatException) at line 45",
            "    line 41 `String.isEmpty` ran 8,000,100×, never threw it",
            "    line 44 `Integer.parseInt` ran 8,000,000×, never threw it",
        ])

    def test_a_dead_arm_inside_a_try_that_ran_is_not_the_fork(self) -> None:
        # `try { o = parse(s); if (o == null) return fallback(); return convert(o); }
        #  catch (ParseException e) { return target(); }` where `o` was never null.
        # The dead `fallback` arm sits under the try, yet the handler is a catch
        # boundary: the guard is not reported as a fork, and the arm shows at zero.
        self.flow = MethodFlow(
            branches=(Branch(7, (10, 12), frozenset(), False),),
            block_starts=(0, 10, 12, 20),
            block_successors={0: (10, 12, 20), 10: (20,), 12: (20,), 20: ()},
            exception_successors={0: frozenset({20}), 10: frozenset({20}), 12: frozenset({20})},
            handler_types={20: "example.ParseException"},
            throwing_blocks=frozenset({0, 10, 12}),
        )
        self.lines = {self.CALLER.canonical_id: (
            (0, 42), (7, 43), (10, 44), (12, 46), (20, 47), (21, 48),
        )}
        self.edge["bci"] = "21"
        self.edge["source_line"] = 48
        parse = MethodRef("example.Parser", "parse", ("java.lang.String",), "java.lang.Object")
        fallback = MethodRef("example.Router", "fallback", (), "java.lang.Object")
        convert = MethodRef("example.Router", "convert", ("java.lang.Object",), "java.lang.Object")
        self.graph = CallGraph(
            methods={1: self.CALLER, 2: self.TARGET, 3: parse, 4: fallback, 5: convert},
            key_to_id={self.CALLER.canonical_id: 1},
            adjacency={1: [
                {"caller": 1, "callee": 3, "bci": "1", "kind": "call"},
                {"caller": 1, "callee": 4, "bci": "10", "kind": "call"},
                {"caller": 1, "callee": 5, "bci": "13", "kind": "call"},
                {"caller": 1, "callee": 2, "bci": "21", "kind": "call"},
            ]},
            invoke_fan_out={10: [2]},
        )
        self.jacoco_lines["example/Router.java"] = {
            42: JacocoLineCoverage(mi=0, ci=3, mb=0, cb=0),
            43: JacocoLineCoverage(mi=0, ci=2, mb=1, cb=1),
            44: JacocoLineCoverage(mi=2, ci=0, mb=0, cb=0),
            46: JacocoLineCoverage(mi=0, ci=3, mb=0, cb=0),
            47: JacocoLineCoverage(mi=1, ci=0, mb=0, cb=0),
            48: JacocoLineCoverage(mi=3, ci=0, mb=0, cb=0),
        }
        counters = InstrumentedCounters(branches={(self.CALLER.canonical_id, 7): {10: 0, 12: 500}})
        classification: dict = self._classify(counters)
        self.assertEqual(classification["kind"], "no-fork")
        self.assertEqual(classification["exception"]["handlers"][0]["sources"], [
            {"line": 42, "count": 500, "calls": ["Parser.parse"]},
            {"line": 44, "count": 0, "calls": ["Router.fallback"]},
            {"line": 46, "count": 500, "calls": ["Router.convert"]},
        ])
        self.assertEqual(classification_lines(classification, True)[1:], [
            "  reached only through catch (ParseException) at line 47",
            "    line 42 `Parser.parse` ran 500×, never threw it",
            "    line 44 `Router.fallback` ran 0×, never threw it",
            "    line 46 `Router.convert` ran 500×, never threw it",
        ])
        self.assertEqual(self._classify()["kind"], "no-fork")

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
