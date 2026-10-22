# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Tests for `analyze`'s mode decision and `measure`'s advance (§AR-forge-workflow-pipeline.7)."""
from __future__ import annotations

import unittest

from support import BUDGETS, run_state

from utility_scripts.dynamic_access_report import (  # noqa: E402
    DynamicAccessCallSite,
    DynamicAccessClass,
    DynamicAccessCoverageReport,
)

import explore  # noqa: E402
from cover_prompts import MODE_BASIC, MODE_BULK, MODE_CLASS  # noqa: E402
from exit_codes import AGENT_WORK, NOTHING_TO_DO, OK  # noqa: E402
from run_state import ROUTE_NEW, ROUTE_REPAIR, ROUTE_UPDATE, Chunk, Explore  # noqa: E402


def report(covered: dict[str, tuple[int, int]]) -> DynamicAccessCoverageReport:
    """A report with `class -> (covered, total)` calls."""
    classes: list[DynamicAccessClass] = []
    for name, (done, total) in covered.items():
        sites = [DynamicAccessCallSite("reflection", "Class#forName", f"{name}.f(X.java:{line})", line, line <= done)
                 for line in range(1, total + 1)]
        classes.append(DynamicAccessClass(name, "X.java", None, total, done, sites))
    return DynamicAccessCoverageReport(
        coordinate="g:a:1", has_dynamic_access=bool(classes),
        total_calls=sum(total for _, total in covered.values()),
        covered_calls=sum(done for done, _ in covered.values()), classes=classes,
    )


def classes(count: int, covered: int = 0) -> dict[str, tuple[int, int]]:
    return {f"p.C{index}": (covered, 2) for index in range(count)}


class DecideTest(unittest.TestCase):

    def decide(self, route: str, measured: DynamicAccessCoverageReport, goal: tuple[int, int] | None = None,
               request: bool = False, chunk: Chunk | None = None) -> explore.Decision:
        covered, total = goal or (None, None)
        return explore.decide(route, measured, BUDGETS, chunk or Chunk(), covered, total, request)

    def test_new_library_without_dynamic_access_gets_a_basic_pass(self) -> None:
        self.assertEqual(MODE_BASIC, self.decide(ROUTE_NEW, report({})).mode)

    def test_update_without_dynamic_access_is_skipped(self) -> None:
        self.assertEqual(explore.MODE_SKIP, self.decide(ROUTE_UPDATE, report({})).mode)

    def test_reporter_request_turns_a_skip_into_one_basic_pass(self) -> None:
        self.assertEqual(MODE_BASIC, self.decide(ROUTE_UPDATE, report({}), request=True).mode)

    def test_fully_covered_report_is_skipped(self) -> None:
        self.assertEqual(explore.MODE_SKIP, self.decide(ROUTE_NEW, report(classes(3, covered=2))).mode)

    def test_previous_coverage_already_met_is_skipped(self) -> None:
        self.assertEqual(explore.MODE_SKIP, self.decide(ROUTE_UPDATE, report(classes(6, covered=1)), goal=(6, 12)).mode)

    def test_the_goal_is_the_previous_ratio_not_its_count(self) -> None:
        # Six covered of eighteen is below the previous 6/12, however many calls that was.
        sparse: dict[str, tuple[int, int]] = {f"p.C{index}": (1, 3) for index in range(6)}
        self.assertEqual(MODE_BULK, self.decide(ROUTE_UPDATE, report(sparse), goal=(6, 12)).mode)

    def test_a_goal_without_a_total_is_a_count(self) -> None:
        self.assertEqual(explore.MODE_SKIP, self.decide(ROUTE_UPDATE, report(classes(6, covered=1)), goal=(6, 0)).mode)

    def test_oversized_repair_is_deferred(self) -> None:
        decision = self.decide(ROUTE_REPAIR, report(classes(16)))
        self.assertEqual(explore.MODE_DEFER, decision.mode)
        self.assertEqual(16, len(decision.targets))

    def test_repair_at_the_threshold_explores(self) -> None:
        self.assertEqual(MODE_BULK, self.decide(ROUTE_REPAIR, report(classes(15))).mode)

    def test_oversized_new_library_starts_a_chunk(self) -> None:
        decision = self.decide(ROUTE_NEW, report(classes(16)))
        self.assertEqual(MODE_BULK, decision.mode)
        self.assertTrue(decision.start_chunk)

    def test_bulk_from_bulk_min_classes(self) -> None:
        self.assertEqual(MODE_BULK, self.decide(ROUTE_NEW, report(classes(5))).mode)

    def test_class_only_below_bulk_min_classes(self) -> None:
        decision = self.decide(ROUTE_NEW, report(classes(4)))
        self.assertEqual(MODE_CLASS, decision.mode)
        self.assertFalse(decision.start_chunk)

    def test_processed_classes_of_earlier_chunks_are_not_targets(self) -> None:
        chunk = Chunk(active=True, processed=["p.C0", "p.C1"], budget=15)
        decision = self.decide(ROUTE_NEW, report(classes(6)), chunk=chunk)
        self.assertEqual(("p.C2", "p.C3", "p.C4", "p.C5"), decision.targets)
        self.assertEqual(MODE_CLASS, decision.mode)


class AdvanceTest(unittest.TestCase):

    def state(self, mode: str, targets: int, route: str = ROUTE_NEW, **explore_values: object):
        values: dict[str, object] = {"mode": mode, "targets": [f"p.C{index}" for index in range(targets)],
                                     "passes": 1, **explore_values}
        return run_state(route, explore=Explore(**values))

    def test_basic_ends_after_one_pass(self) -> None:
        self.assertEqual(OK, explore.advance(self.state(MODE_BASIC, 0), report({}), True))

    def test_bulk_repeats_while_it_gains(self) -> None:
        state = self.state(MODE_BULK, 6)
        self.assertEqual(AGENT_WORK, explore.advance(state, report(classes(6, covered=1)), True))
        self.assertEqual(MODE_BULK, state.explore.mode)
        self.assertEqual(1, state.explore.bulk_passes)

    def test_bulk_that_gained_nothing_moves_to_the_first_open_class(self) -> None:
        state = self.state(MODE_BULK, 6)
        self.assertEqual(AGENT_WORK, explore.advance(state, report(classes(6)), False))
        self.assertEqual((MODE_CLASS, "p.C0", 1), (state.explore.mode, state.explore.class_name,
                                                   state.explore.class_attempt))

    def test_bulk_budget_moves_to_class_mode(self) -> None:
        state = self.state(MODE_BULK, 6, bulk_passes=BUDGETS.bulk_passes - 1)
        explore.advance(state, report(classes(6, covered=1)), True)
        self.assertEqual(MODE_CLASS, state.explore.mode)

    def test_class_stays_until_its_attempts_are_spent(self) -> None:
        state = self.state(MODE_CLASS, 2, class_name="p.C0", class_attempt=1)
        explore.advance(state, report(classes(2)), False)
        self.assertEqual(("p.C0", 2), (state.explore.class_name, state.explore.class_attempt))

    def test_exhausted_class_moves_to_the_next(self) -> None:
        state = self.state(MODE_CLASS, 2, class_name="p.C0", class_attempt=BUDGETS.class_attempts)
        explore.advance(state, report(classes(2)), False)
        self.assertEqual(["p.C0"], state.explore.exhausted)
        self.assertEqual(("p.C1", 1), (state.explore.class_name, state.explore.class_attempt))

    def test_covered_class_moves_to_the_next(self) -> None:
        state = self.state(MODE_CLASS, 2, class_name="p.C0", class_attempt=1)
        explore.advance(state, report({"p.C0": (2, 2), "p.C1": (0, 2)}), True)
        self.assertEqual(["p.C0"], state.explore.completed)
        self.assertEqual("p.C1", state.explore.class_name)

    def test_no_class_left_meets_the_goal(self) -> None:
        state = self.state(MODE_CLASS, 1, class_name="p.C0", class_attempt=BUDGETS.class_attempts)
        self.assertEqual(OK, explore.advance(state, report(classes(1)), False))

    def test_previous_coverage_met_ends_an_update(self) -> None:
        state = self.state(MODE_BULK, 6, ROUTE_UPDATE)
        state.goal_covered_calls, state.goal_total_calls = 6, 12
        self.assertEqual(OK, explore.advance(state, report(classes(6, covered=1)), True))

    def test_explore_passes_spent(self) -> None:
        state = self.state(MODE_CLASS, 2, class_name="p.C0", class_attempt=1, passes=BUDGETS.explore_passes)
        self.assertEqual(NOTHING_TO_DO, explore.advance(state, report(classes(2)), False))

    def test_chunk_budget_spent_leaves_the_chunk_open(self) -> None:
        state = self.state(MODE_CLASS, 20, class_name="p.C1", class_attempt=1, exhausted=["p.C0"])
        state.chunk = Chunk(active=True, budget=2)
        measured = report({**classes(20), "p.C1": (2, 2)})
        self.assertEqual(NOTHING_TO_DO, explore.advance(state, measured, True))
        self.assertFalse(state.chunk.final)

    def test_gain_is_judged_on_the_active_class(self) -> None:
        explore_state = Explore(mode=MODE_CLASS, class_name="p.C1")
        before = report({"p.C0": (0, 2), "p.C1": (0, 2)})
        self.assertFalse(explore.gained(explore_state, before, report({"p.C0": (2, 2), "p.C1": (0, 2)})))
        self.assertTrue(explore.gained(explore_state, before, report({"p.C0": (0, 2), "p.C1": (1, 2)})))


if __name__ == "__main__":
    unittest.main()
