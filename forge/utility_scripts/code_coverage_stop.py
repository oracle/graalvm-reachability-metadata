# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""
Decide whether a coverage phase has stopped producing coverage
(§AR-code-coverage-improvement.3.3, §AR-code-coverage-improvement-architecture.1).

Both measurement states share this module, so the API and deep phases cannot
drift onto different stop rules. A phase's pass yield is the number of methods
JaCoCo newly reports covered between two consecutive measurements of that phase,
counted on the phase's own roster; the phase stops once the last `window` passes
each yielded at least zero and fewer than `threshold` methods.

The thresholds are expressed in methods rather than percentage points because a
percentage is not portable across libraries: one point is 3.5 methods on a
347-method roster and 70 on a 7065-method one, and a one-point rule stops a
measured run four passes before the pass that produced 38% of its coverage.

A negative delta is recorded as zero yield. Losing coverage means the cover
agent produced no net coverage gain, so it counts as a low-yield pass rather
than creating a negative value in the recorded series.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from typing import Any

SCHEMA_VERSION = "1.0.0"

#: Phase report file names, keyed by phase, with the summary field that carries
#: the phase's covered-method count on its own roster.
PHASE_REPORTS: dict[str, tuple[str, str]] = {
    "api": ("api-cover-report", "covered"),
    "deep": ("discovery-report", "deepCovered"),
}
DECISION_FILE = "stop-decision.json"
ACTIVE_MEASUREMENT_FILE = "active-measurement.txt"

#: Why a phase ended. `None` while it continues.
REASON_NO_TARGETS = "no-targets"
REASON_BUDGET_SPENT = "budget-spent"
REASON_MARGINAL_YIELD = "marginal-yield"


class StopDecisionError(ValueError):
    """Raised when the stop evaluator's inputs violate the workflow contract."""


def begin_measurement(reports_dir: str, phase: str) -> int:
    """Open or resume the measurement for one logical cover-pass iteration.

    A measurement repair returns to measurement without running the cover agent.
    The marker keeps that retry on the same iteration even when the failed
    measurement already wrote its numbered report, so a retry cannot become a
    zero-yield cover pass (§AR-code-coverage-improvement.3.3).
    """
    try:
        stem, _ = PHASE_REPORTS[phase]
    except KeyError:
        raise StopDecisionError(f"unknown phase: {phase}") from None
    pattern: re.Pattern[str] = re.compile(rf"^{re.escape(stem)}-(\d+)\.json$")
    report_count: int = sum(
        1 for name in os.listdir(reports_dir) if pattern.match(name)
    )
    marker_path: str = os.path.join(
        reports_dir, f"{phase}-{ACTIVE_MEASUREMENT_FILE}"
    )
    if os.path.isfile(marker_path):
        with open(marker_path, encoding="utf-8") as marker:
            raw_iteration: str = marker.read().strip()
        try:
            iteration: int = int(raw_iteration)
        except ValueError:
            raise StopDecisionError(
                f"active measurement is not an integer: {marker_path}"
            ) from None
        valid_iterations: set[int] = {report_count}
        if report_count:
            valid_iterations.add(report_count - 1)
        if iteration not in valid_iterations:
            raise StopDecisionError(
                f"active measurement {iteration} is incoherent with "
                f"{report_count} reports: {marker_path}"
            )
        return iteration

    iteration: int = report_count
    with open(marker_path, "w", encoding="utf-8") as marker:
        marker.write(f"{iteration}\n")
    return iteration


def complete_measurement(reports_dir: str, phase: str, iteration: int) -> None:
    """Close a successfully measured iteration so the next cover pass advances."""
    marker_path: str = os.path.join(
        reports_dir, f"{phase}-{ACTIVE_MEASUREMENT_FILE}"
    )
    if not os.path.isfile(marker_path):
        raise StopDecisionError(f"active measurement is missing: {marker_path}")
    with open(marker_path, encoding="utf-8") as marker:
        raw_iteration: str = marker.read().strip()
    if raw_iteration != str(iteration):
        raise StopDecisionError(
            f"active measurement is {raw_iteration}, not {iteration}: {marker_path}"
        )
    os.remove(marker_path)


def covered_series(reports_dir: str, phase: str) -> list[int]:
    """Read one phase's covered-method count from every report it has written.

    Reports are ordered by their iteration suffix, not lexically: `-10` sorts
    before `-2` as text, which would silently reverse the yield series.
    """
    try:
        stem, field = PHASE_REPORTS[phase]
    except KeyError:
        raise StopDecisionError(f"unknown phase: {phase}") from None
    pattern: re.Pattern[str] = re.compile(rf"^{re.escape(stem)}-(\d+)\.json$")
    numbered: list[tuple[int, str]] = []
    for name in os.listdir(reports_dir):
        match: re.Match[str] | None = pattern.match(name)
        if match:
            numbered.append((int(match.group(1)), os.path.join(reports_dir, name)))
    series: list[int] = []
    for iteration, path in sorted(numbered):
        with open(path, "r", encoding="utf-8") as source:
            report: Any = json.load(source)
        if not isinstance(report, dict):
            raise StopDecisionError(f"report is not an object: {path}")
        summary: Any = report.get("summary")
        if not isinstance(summary, dict):
            raise StopDecisionError(f"report has no summary object: {path}")
        covered: Any = summary.get(field)
        if not isinstance(covered, int) or isinstance(covered, bool):
            raise StopDecisionError(f"summary.{field} is not an integer: {path}")
        if len(series) != iteration:
            raise StopDecisionError(
                f"report history has a gap: expected iteration {len(series)}, "
                f"found {iteration} at {path}"
            )
        series.append(covered)
    if not series:
        raise StopDecisionError(f"no {phase} reports under {reports_dir}")
    return series


def pass_yields(series: list[int]) -> list[int]:
    """Per-pass yields, clamping coverage loss to zero."""
    return [max(0, later - earlier) for earlier, later in zip(series, series[1:])]


def evaluate(
        series: list[int],
        threshold: int,
        window: int,
        floor: int,
) -> tuple[bool, list[int]]:
    """Decide the marginal-yield stop from a phase's covered-method series.

    The floor counts completed cover passes, not reports: a phase whose opening
    passes fail on an environment fault must not be ended by that fault before
    it has had a chance to produce anything (§AR-code-coverage-improvement.3.3).
    """
    if threshold < 1 or window < 1 or floor < 1:
        raise StopDecisionError(
            "threshold, window and floor must each be at least 1: "
            f"got {threshold}, {window}, {floor}"
        )
    yields: list[int] = pass_yields(series)
    if len(yields) < max(window, floor):
        return False, yields
    return all(0 <= value < threshold for value in yields[-window:]), yields


def decision(
        series: list[int],
        threshold: int,
        window: int,
        floor: int,
        budget: int,
        targets_remaining: int,
) -> dict[str, Any]:
    """The full record of one phase's loop decision, stopping or not."""
    stopped_on_yield: bool
    yields: list[int]
    stopped_on_yield, yields = evaluate(series, threshold, window, floor)
    passes: int = len(yields)
    reason: str | None = None
    if not targets_remaining:
        reason = REASON_NO_TARGETS
    elif passes >= budget:
        reason = REASON_BUDGET_SPENT
    elif stopped_on_yield:
        reason = REASON_MARGINAL_YIELD
    return {
        "schemaVersion": SCHEMA_VERSION,
        "threshold": threshold,
        "window": window,
        "floor": floor,
        "budget": budget,
        "passes": passes,
        "targetsRemaining": targets_remaining,
        "covered": list(series),
        "passYields": yields,
        "stopped": reason is not None,
        "reason": reason,
    }


def record(reports_dir: str, phase: str, record_value: dict[str, Any]) -> str:
    """Persist one phase's decision next to that phase's reports.

    Written on every pass rather than only on the pass that stops a phase: a
    short run is otherwise indistinguishable from a crashed one, and the
    thresholds can then only be re-argued rather than re-measured
    (§AR-code-coverage-improvement.3.3).
    """
    payload: dict[str, Any] = {"phase": phase, **record_value}
    path: str = os.path.join(reports_dir, f"{phase}-{DECISION_FILE}")
    with open(path, "w", encoding="utf-8") as output:
        json.dump(payload, output, indent=2)
        output.write("\n")
    return path


def summarize(record_value: dict[str, Any]) -> str:
    """One log line naming the yield series and what it decided."""
    recent: list[int] = record_value["passYields"][-record_value["window"]:]
    state: str = record_value["reason"] or "continuing"
    return (
        f"pass {record_value['passes']}/{record_value['budget']}: {state}; "
        f"last {len(recent)} yields {recent}, threshold {record_value['threshold']}, "
        f"{record_value['targetsRemaining']} targets remain"
    )


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "Evaluate and record a coverage phase's marginal-yield stop "
            "decision from its own report history."
        ),
    )
    parser.add_argument(
        "--reports-dir",
        required=True,
        help="Directory holding the phase's numbered reports.",
    )
    parser.add_argument(
        "--phase",
        required=True,
        choices=sorted(PHASE_REPORTS),
        help="Which phase's report history and roster to read.",
    )
    parser.add_argument("--threshold", type=int, required=True, help="Yield floor, in methods.")
    parser.add_argument("--window", type=int, required=True, help="Consecutive low passes to stop.")
    parser.add_argument("--floor", type=int, required=True, help="Passes before the rule applies.")
    parser.add_argument("--budget", type=int, required=True, help="Maximum cover passes.")
    parser.add_argument(
        "--targets-remaining",
        type=int,
        required=True,
        help="Actionable targets in the latest report; zero completes the phase.",
    )
    return parser


def main() -> int:
    args: argparse.Namespace = build_parser().parse_args()
    try:
        record_value: dict[str, Any] = decision(
            covered_series(args.reports_dir, args.phase),
            args.threshold,
            args.window,
            args.floor,
            args.budget,
            args.targets_remaining,
        )
        record(args.reports_dir, args.phase, record_value)
    except (StopDecisionError, OSError, json.JSONDecodeError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 2
    print(f"[{args.phase}-stop] {summarize(record_value)}")
    return 0 if record_value["stopped"] else 10


if __name__ == "__main__":
    sys.exit(main())
