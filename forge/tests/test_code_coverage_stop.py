# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import json
import os
import tempfile
import unittest

from utility_scripts import code_coverage_stop as module

#: Per-pass yields of four measured runs, used as the rule's regression guard
#: (§AR-code-coverage-improvement.3.3). The two commons-compress phases saturate
#: and the rule must act on them; the two kafka-streams runs stay productive to
#: their last pass and the rule must stay silent on them, including the unguided
#: run whose fifth pass produced 38% of its coverage after two dull passes.
MEASURED_YIELDS: dict[str, list[int]] = {
    "commons-compress-api": [701, 316, 215, 73, 33, 93, 13, 0, 5, 5],
    "commons-compress-deep": [91, 251, 41, 6, 47, 7, 24, 2, 8, 10],
    "kafka-streams-api-guided": [381, 147, 194, 15, 80, 96, 112, 36, 70, 48],
    "kafka-streams-unguided": [121, 23, 76, 39, 354, 75, 70, 58, 83, 21],
}
THRESHOLD = 10
WINDOW = 2
FLOOR = 4


def _series(yields: list[int]) -> list[int]:
    """The covered-method series a phase with these per-pass yields would write."""
    series: list[int] = [0]
    for value in yields:
        series.append(series[-1] + value)
    return series


def _first_stop(yields: list[int], window: int = WINDOW) -> int | None:
    """The pass after which the rule ends a phase, or None if it never does."""
    for passes in range(1, len(yields) + 1):
        stopped, _ = module.evaluate(
            _series(yields[:passes]), THRESHOLD, window, FLOOR
        )
        if stopped:
            return passes
    return None


class CoverageStopRuleTests(unittest.TestCase):

    def test_acts_on_measured_saturating_runs(self) -> None:
        self.assertEqual(_first_stop(MEASURED_YIELDS["commons-compress-api"]), 9)
        self.assertEqual(_first_stop(MEASURED_YIELDS["commons-compress-deep"]), 9)

    def test_stays_silent_on_measured_productive_runs(self) -> None:
        """The rule must not end a run whose late passes still produce."""
        self.assertIsNone(_first_stop(MEASURED_YIELDS["kafka-streams-api-guided"]))
        self.assertIsNone(_first_stop(MEASURED_YIELDS["kafka-streams-unguided"]))

    def test_window_of_three_would_save_no_pass(self) -> None:
        """Why the window is two: yields oscillate, so a longer streak resets.

        A window of three either never fires or fires on the final pass, which
        ends a phase that was ending anyway.
        """
        for name, yields in MEASURED_YIELDS.items():
            with self.subTest(run=name):
                stop: int | None = _first_stop(yields, window=3)
                self.assertIn(stop, (None, len(yields)))

    def test_floor_protects_a_broken_opening(self) -> None:
        """A dead first pass is an environment fault, not a saturated phase."""
        yields: list[int] = [0, 0, 202, 152]
        self.assertIsNone(_first_stop(yields))
        stopped, _ = module.evaluate(_series(yields[:2]), THRESHOLD, WINDOW, 1)
        self.assertTrue(stopped)

    def test_a_negative_pass_breaks_the_streak(self) -> None:
        """Lost coverage is a rewritten test suite, not a phase out of material."""
        stopped, yields = module.evaluate(
            _series([50, 40, 30, 5, -27]), THRESHOLD, WINDOW, FLOOR
        )
        self.assertFalse(stopped)
        self.assertEqual(yields[-1], -27)

    def test_evaluates_only_once_the_floor_is_reached(self) -> None:
        for passes in range(1, FLOOR):
            with self.subTest(passes=passes):
                stopped, _ = module.evaluate(
                    _series([0] * passes), THRESHOLD, WINDOW, FLOOR
                )
                self.assertFalse(stopped)
        stopped, _ = module.evaluate(_series([0] * FLOOR), THRESHOLD, WINDOW, FLOOR)
        self.assertTrue(stopped)

    def test_rejects_a_nonsensical_configuration(self) -> None:
        with self.assertRaises(module.StopDecisionError):
            module.evaluate([0, 1], 0, WINDOW, FLOOR)


class CoverageStopDecisionTests(unittest.TestCase):

    def setUp(self) -> None:
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)

    def _write_reports(self, phase: str, series: list[int]) -> str:
        stem, field = module.PHASE_REPORTS[phase]
        for iteration, covered in enumerate(series):
            payload: dict = {"iteration": iteration, "summary": {field: covered}}
            path: str = os.path.join(
                self.directory.name, f"{stem}-{iteration}.json"
            )
            with open(path, "w", encoding="utf-8") as report:
                json.dump(payload, report)
        return self.directory.name

    def test_reads_both_phase_rosters(self) -> None:
        for phase in ("api", "deep"):
            with self.subTest(phase=phase):
                directory: str = self._write_reports(phase, [5, 25, 60])
                self.assertEqual(
                    module.covered_series(directory, phase), [5, 25, 60]
                )
                self.assertEqual(
                    module.pass_yields(module.covered_series(directory, phase)),
                    [20, 35],
                )

    def test_orders_reports_numerically(self) -> None:
        """`-10` sorts before `-2` as text, which would reverse the yields."""
        directory: str = self._write_reports("api", list(range(0, 121, 10)))
        self.assertEqual(module.covered_series(directory, "api")[-1], 120)

    def test_rejects_a_gap_in_the_report_history(self) -> None:
        directory: str = self._write_reports("api", [0, 10, 20])
        os.remove(os.path.join(directory, "api-cover-report-1.json"))
        with self.assertRaises(module.StopDecisionError):
            module.covered_series(directory, "api")

    def test_no_targets_completes_the_phase_before_the_floor(self) -> None:
        record = module.decision(
            [0, 900], THRESHOLD, WINDOW, FLOOR, budget=15, targets_remaining=0
        )
        self.assertTrue(record["stopped"])
        self.assertEqual(record["reason"], module.REASON_NO_TARGETS)

    def test_a_spent_budget_outranks_the_yield_rule(self) -> None:
        record = module.decision(
            _series([100] * 15), THRESHOLD, WINDOW, FLOOR,
            budget=15, targets_remaining=42,
        )
        self.assertEqual(record["reason"], module.REASON_BUDGET_SPENT)

    def test_records_the_series_on_a_pass_that_does_not_stop(self) -> None:
        directory: str = self._write_reports("deep", _series([91, 251, 41, 6, 47]))
        record = module.decision(
            module.covered_series(directory, "deep"),
            THRESHOLD, WINDOW, FLOOR, budget=15, targets_remaining=110,
        )
        path: str = module.record(directory, "deep", record)

        with open(path, encoding="utf-8") as written:
            stored: dict = json.load(written)
        self.assertEqual(stored["phase"], "deep")
        self.assertFalse(stored["stopped"])
        self.assertIsNone(stored["reason"])
        self.assertEqual(stored["passYields"], [91, 251, 41, 6, 47])
        self.assertIn("continuing", module.summarize(record))


if __name__ == "__main__":
    unittest.main()
