# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Build schema-validated final evidence for code coverage improvement.

JaCoCo is the only coverage authority. Sampled PGO is retained only as
navigation guidance (§AR-code-coverage-improvement).

Report reading and validation live in `code_coverage_finalize_reports.py` and
target-outcome classification in `code_coverage_target_outcomes.py`; both are
re-exported here.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from datetime import datetime, timezone
from typing import Any

from jsonschema import Draft202012Validator

from utility_scripts.code_coverage_finalize_reports import (
    COORDINATE_PATTERN,
    FinalizationError,
    _api_snapshot,
    _array,
    _check_coordinate,
    _coordinate,
    _coverage_statuses,
    _deep_snapshot,
    _integer,
    _percent,
    _pgo_snapshot,
    _read_object,
    _schema_path,
    _string,
)
from utility_scripts.code_coverage_jacoco import load_jacoco_method_coverage
from utility_scripts.code_coverage_target_outcomes import (
    TARGET_STATE_SCHEMA_FILE,
    TARGET_STATE_STATUSES,
    TERMINAL_NEGATIVE_STATUSES,
    _completed_transitions,
    _load_latest_target_states,
    _target_outcomes,
    _target_states_from_entries,
    _validate_target_state_document,
)

SCHEMA_VERSION = "1.3.0"

#: Run checkpoints, in run order. Each is one JaCoCo report, and each phase
#: begins at the checkpoint the previous phase ended on
#: (§AR-code-coverage-improvement.5.1).
CHECKPOINT_NAMES: tuple[str, ...] = ("runStart", "afterApiPhase", "final")
SCHEMA_FILE = "code_coverage_final_metrics_schema.json"


def _universe_ids(
        api_baseline_report: dict[str, Any],
        deep_baseline_report: dict[str, Any],
        run_start: dict[str, Any],
) -> tuple[list[str], list[str]]:
    """The complete method set every ratio in this run divides by.

    The run-start report defines the universe: both rosters are cut down to the
    methods it reports, and every later checkpoint must then still report all of
    them, which is where a universe that moved mid-run is caught. Methods JaCoCo
    does not report at all are dropped rather than charged to a denominator no
    run can cover them against, and the same rule applies to both rosters so
    that an unreported method is not silently dropped on one side and a hard
    error on the other. The two rosters must not overlap, since the deep roster
    is by construction the library methods the inventory does not hold, and an
    overlap would double count (§AR-code-coverage-improvement.5.1).
    """
    inventory_ids: list[str] = list(
        _coverage_statuses(
            api_baseline_report,
            "targets",
            "API baseline",
            frozenset({"covered", "uncovered", "not-reported"}),
        )
    )
    deep_ids: list[str] = list(
        _coverage_statuses(
            deep_baseline_report,
            "deepMethods",
            "Deep baseline",
            frozenset({"covered", "uncovered"}),
        )
    )
    overlap: set[str] = set(inventory_ids) & set(deep_ids)
    if overlap:
        raise FinalizationError(
            f"The API and deep rosters share {len(overlap)} method ids; "
            f"the coverage universes must be disjoint."
        )
    api_ids: list[str] = [
        method_id for method_id in inventory_ids if method_id in run_start
    ]
    deep_ids = [method_id for method_id in deep_ids if method_id in run_start]
    if not api_ids and not deep_ids:
        raise FinalizationError("The coverage universe is empty.")
    return api_ids, deep_ids


def _checkpoint(
        name: str,
        jacoco_path: str,
        api_ids: list[str],
        deep_ids: list[str],
) -> dict[str, Any]:
    """Count one instant of the run over the frozen universe.

    One report, one routine, both universes. Assembling a checkpoint from
    summary fields that separate phases computed for themselves is what lets two
    different instants of the run be presented as if they were comparable. The
    universe is cut from the run-start report, so a method missing here means a
    later report stopped covering ground the first one held.
    """
    coverage: dict[str, Any] = load_jacoco_method_coverage([jacoco_path])
    missing: list[str] = [
        method_id
        for method_id in api_ids + deep_ids
        if method_id not in coverage
    ]
    if missing:
        raise FinalizationError(
            f"Checkpoint '{name}' ({jacoco_path}) does not report "
            f"{len(missing)} of the frozen universe's methods, so the universe "
            f"moved during the run; first is '{missing[0]}'."
        )
    api_covered: int = sum(1 for i in api_ids if coverage[i].covered)
    deep_covered: int = sum(1 for i in deep_ids if coverage[i].covered)
    universe: int = len(api_ids) + len(deep_ids)
    return {
        "name": name,
        "apiCovered": api_covered,
        "deepCovered": deep_covered,
        "covered": api_covered + deep_covered,
        "uncovered": universe - api_covered - deep_covered,
        "coveragePercent": _percent(api_covered + deep_covered, universe),
    }


def _run_coverage(
        api_baseline_report: dict[str, Any],
        deep_baseline_report: dict[str, Any],
        jacoco_paths: tuple[str, str, str],
) -> dict[str, Any]:
    """Whole-run coverage: one denominator, one routine, sequential checkpoints.

    The per-phase blocks record each phase against its own roster, which is what
    a phase's own guidance is ranked on. This block is the run as a reader sees
    it: every checkpoint a share of the same complete method count, so a phase's
    gain is the distance from the previous checkpoint and the phase gains sum to
    the run's gain (§AR-code-coverage-improvement.5.1).
    """
    run_start_coverage: dict[str, Any] = load_jacoco_method_coverage(
        [jacoco_paths[0]]
    )
    api_ids: list[str]
    deep_ids: list[str]
    api_ids, deep_ids = _universe_ids(
        api_baseline_report, deep_baseline_report, run_start_coverage
    )
    checkpoints: list[dict[str, Any]] = [
        _checkpoint(name, path, api_ids, deep_ids)
        for name, path in zip(CHECKPOINT_NAMES, jacoco_paths)
    ]
    phases: list[dict[str, Any]] = [
        {
            "name": phase_name,
            "covered": later["covered"] - earlier["covered"],
            "coveragePercentagePoints": round(
                later["coveragePercent"] - earlier["coveragePercent"], 2
            ),
        }
        for phase_name, earlier, later in (
            ("api", checkpoints[0], checkpoints[1]),
            ("deep", checkpoints[1], checkpoints[2]),
        )
    ]
    return {
        "universe": len(api_ids) + len(deep_ids),
        "apiUniverse": len(api_ids),
        "deepUniverse": len(deep_ids),
        "checkpoints": checkpoints,
        "phases": phases,
    }


def _stop_decisions(paths: list[str]) -> list[dict[str, Any]]:
    """Carry each phase's recorded loop decision into the run's evidence.

    A phase that ends before its budget is spent is not a failure, but a run
    that cannot say why it ended short is indistinguishable from a crashed one
    (§AR-code-coverage-improvement.4.3).
    """
    decisions: list[dict[str, Any]] = []
    for path in paths:
        label: str = f"stop decision {os.path.basename(path)}"
        record: dict[str, Any] = _read_object(path, label)
        phase: str = _string(record.get("phase"), f"{label}.phase")
        if phase not in ("api", "deep"):
            raise FinalizationError(f"{label}.phase must be api or deep: {phase}")
        reason: Any = record.get("reason")
        if reason is not None and not isinstance(reason, str):
            raise FinalizationError(f"{label}.reason must be a string or null")
        yields: list[Any] = _array(record.get("passYields"), f"{label}.passYields")
        decisions.append({
            "phase": phase,
            "passes": _integer(record.get("passes"), f"{label}.passes"),
            "budget": _integer(record.get("budget"), f"{label}.budget"),
            "threshold": _integer(record.get("threshold"), f"{label}.threshold"),
            "window": _integer(record.get("window"), f"{label}.window"),
            "floor": _integer(record.get("floor"), f"{label}.floor"),
            "reason": reason,
            "passYields": [
                _integer(value, f"{label}.passYields[]") for value in yields
            ],
        })
    phases: list[str] = [decision["phase"] for decision in decisions]
    if len(set(phases)) != len(phases):
        raise FinalizationError(f"stop decisions repeat a phase: {phases}")
    return sorted(decisions, key=lambda decision: decision["phase"] != "api")



def _delta(
        baseline: dict[str, Any],
        final: dict[str, Any],
        include_not_reported: bool,
) -> dict[str, Any]:
    result: dict[str, Any] = {
        "covered": final["covered"] - baseline["covered"],
        "uncovered": final["uncovered"] - baseline["uncovered"],
        "coveragePercentagePoints": round(
            final["coveragePercent"] - baseline["coveragePercent"], 2
        ),
    }
    if include_not_reported:
        result["notReported"] = final["notReported"] - baseline["notReported"]
    return result


def _generated_at() -> str:
    """Stamp the run so publication identity survives a retried publication.

    The trusted publisher derives one publication ID from durable run inputs,
    and this timestamp is the coverage workflow's (§AR-publication-descriptor).
    Re-running publication against the same finalization artifacts therefore
    reuses one branch and one pull request; re-running finalization is a new run.
    """
    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def _suite_path(value: str) -> str:
    _string(value, "coverageSuitePath")
    normalized: str = os.path.normpath(value)
    if (
        os.path.isabs(normalized)
        or normalized in {".", ".."}
        or normalized.startswith(f"..{os.sep}")
    ):
        raise FinalizationError(
            "coverageSuitePath must be a relative path inside the repository."
        )
    return normalized


def _commands(values: list[str]) -> list[str]:
    if not values:
        raise FinalizationError("At least one validation command is required.")
    for index, value in enumerate(values):
        _string(value, f"validationCommands[{index}]")
    return list(values)


def validate_final_metrics(metrics: dict[str, Any]) -> None:
    schema: dict[str, Any] = _read_object(_schema_path(SCHEMA_FILE), "Final metrics schema")
    errors: list[Any] = sorted(
        Draft202012Validator(schema).iter_errors(metrics),
        key=lambda error: [str(part) for part in error.absolute_path],
    )
    if errors:
        error: Any = errors[0]
        path: str = ".".join(str(part) for part in error.absolute_path) or "<root>"
        raise FinalizationError(
            f"Final metrics do not match {SCHEMA_FILE} at {path}: {error.message}"
        )


def load_validated_final_metrics(path: str) -> dict[str, Any]:
    metrics: dict[str, Any] = _read_object(path, "Final metrics")
    validate_final_metrics(metrics)
    return metrics


def _signed(value: int | float) -> str:
    return f"{'+' if value > 0 else ''}{value}"


def _target_lines(targets: list[dict[str, Any]]) -> list[str]:
    if not targets:
        return ["_None._"]
    lines: list[str] = []
    for target in targets:
        line: str = f"- [{target['phase']}] `{target['id']}`"
        if "attemptCount" in target:
            last_iteration: int | None = target["lastAttemptedIteration"]
            last: str = str(last_iteration) if last_iteration is not None else "none"
            line += (
                f" — attempts: {target['attemptCount']}, "
                f"last attempted iteration: {last}"
            )
        if target.get("reason"):
            line += f" — {target['reason']}"
        lines.append(line)
    return lines


def _write_summary(metrics: dict[str, Any], path: str) -> None:
    api: dict[str, Any] = metrics["apiJacoco"]
    deep: dict[str, Any] = metrics["deepJacoco"]
    pgo: dict[str, Any] = metrics["pgoGuidance"]
    lines: list[str] = [
        f"# Code coverage finalization — {metrics['coordinate']}",
        "",
        f"- Coverage suite: `{metrics['coverageSuitePath']}`",
        "- Coverage authority: JaCoCo",
        f"- Needs human intervention: {'yes' if metrics['needsHumanIntervention'] else 'no'}",
        "",
        "## Public API JaCoCo",
        "",
        f"- Baseline: {api['baseline']['covered']}/{api['baseline']['measured']} "
        f"({api['baseline']['coveragePercent']}%)",
        f"- Final: {api['final']['covered']}/{api['final']['measured']} "
        f"({api['final']['coveragePercent']}%)",
        f"- Delta: {_signed(api['delta']['coveragePercentagePoints'])}pp",
        "",
        "## Deep-method JaCoCo",
        "",
        f"- Baseline: {deep['baseline']['covered']}/{deep['baseline']['total']} "
        f"({deep['baseline']['coveragePercent']}%)",
        f"- Final: {deep['final']['covered']}/{deep['final']['total']} "
        f"({deep['final']['coveragePercent']}%)",
        f"- Delta: {_signed(deep['delta']['coveragePercentagePoints'])}pp",
        "",
        "## Sampled PGO guidance only",
        "",
        pgo["note"],
        "",
        f"- Baseline: {pgo['baseline']['samplingContexts']} contexts, "
        f"{pgo['baseline']['sampledMethods']} sampled methods, "
        f"{pgo['baseline']['sampleCount']} samples",
        f"- Final: {pgo['final']['samplingContexts']} contexts, "
        f"{pgo['final']['sampledMethods']} sampled methods, "
        f"{pgo['final']['sampleCount']} samples",
        "",
        "## Target outcomes",
        "",
    ]
    for status in ("completed", "skipped", "exhausted", "failed"):
        targets: list[dict[str, Any]] = metrics["targets"][status]
        lines += [f"### {status.title()} ({len(targets)})", ""]
        lines += _target_lines(targets)
        lines.append("")
    if metrics["stopDecisions"]:
        lines += ["## Phase stop decisions", ""]
        for stop in metrics["stopDecisions"]:
            reason: str = stop["reason"] or "budget-spent"
            lines.append(
                f"- {stop['phase']}: {reason} after {stop['passes']}/{stop['budget']} "
                f"passes — yields {stop['passYields']}, low-yield rule "
                f"{stop['window']}×<{stop['threshold']} methods after pass {stop['floor']}"
            )
        lines.append("")
    lines += ["## Validation commands", "", "```console"]
    lines += metrics["validationCommands"]
    lines += ["```", ""]
    with open(path, "w", encoding="utf-8") as summary:
        summary.write("\n".join(lines))


def finalize_coverage(
        coordinate: str,
        coverage_suite_path: str,
        api_baseline_path: str,
        api_final_path: str,
        deep_baseline_path: str,
        deep_final_path: str,
        jacoco_paths: tuple[str, str, str],
        target_state_paths: list[str],
        stop_decision_paths: list[str],
        validation_commands: list[str],
        output_dir: str,
) -> dict[str, Any]:
    coordinate = _coordinate(coordinate)
    api_baseline_report: dict[str, Any] = _read_object(
        api_baseline_path, "API baseline"
    )
    api_final_report: dict[str, Any] = _read_object(api_final_path, "API final")
    deep_baseline_report: dict[str, Any] = _read_object(
        deep_baseline_path, "Deep baseline"
    )
    deep_final_report: dict[str, Any] = _read_object(deep_final_path, "Deep final")
    api_baseline: dict[str, Any] = _api_snapshot(
        api_baseline_report, coordinate, "API baseline"
    )
    api_final: dict[str, Any] = _api_snapshot(
        api_final_report, coordinate, "API final"
    )
    deep_baseline: dict[str, Any] = _deep_snapshot(
        deep_baseline_report, coordinate, "Deep baseline"
    )
    deep_final: dict[str, Any] = _deep_snapshot(
        deep_final_report, coordinate, "Deep final"
    )
    target_outcomes: dict[str, list[dict[str, Any]]] = _target_outcomes(
        target_state_paths,
        coordinate,
        api_baseline_report,
        api_final_report,
        deep_baseline_report,
        deep_final_report,
    )
    metrics: dict[str, Any] = {
        "schemaVersion": SCHEMA_VERSION,
        "generatedAt": _generated_at(),
        "coordinate": coordinate,
        "coverageSuitePath": _suite_path(coverage_suite_path),
        "finalMeasurementArtifacts": {
            "jacoco": jacoco_paths[2],
            "discoveryReport": deep_final_path,
        },
        "runCoverage": _run_coverage(
            api_baseline_report, deep_baseline_report, jacoco_paths
        ),
        "apiJacoco": {
            "baseline": api_baseline,
            "final": api_final,
            "delta": _delta(api_baseline, api_final, True),
        },
        "deepJacoco": {
            "baseline": deep_baseline,
            "final": deep_final,
            "delta": _delta(deep_baseline, deep_final, False),
        },
        "pgoGuidance": {
            "guidanceOnly": True,
            "note": (
                "Sampled PGO is navigation evidence only. Sample counts do not "
                "measure coverage, and sample absence does not prove non-execution."
            ),
            "baseline": _pgo_snapshot(deep_baseline_report, "Deep baseline"),
            "final": _pgo_snapshot(deep_final_report, "Deep final"),
        },
        "targets": target_outcomes,
        "stopDecisions": _stop_decisions(stop_decision_paths),
        "needsHumanIntervention": bool(target_outcomes["failed"]),
        "validationCommands": _commands(validation_commands),
    }
    validate_final_metrics(metrics)
    os.makedirs(output_dir, exist_ok=True)
    with open(
            os.path.join(output_dir, "final-metrics.json"),
            "w",
            encoding="utf-8",
    ) as output:
        json.dump(metrics, output, indent=2)
        output.write("\n")
    _write_summary(metrics, os.path.join(output_dir, "final-summary.md"))
    return metrics


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Finalize separate JaCoCo results and guidance-only PGO evidence.",
        epilog=(
            "Example:\n"
            "  python3 utility_scripts/code_coverage_finalize.py "
            "--coordinate group:artifact:version "
            "--coverage-suite-path tests/src/group/artifact/version/code-coverage-improvement "
            "--api-baseline api-cover-report-0.json "
            "--api-final api-cover-report-5.json "
            "--deep-baseline discovery-report-0.json "
            "--deep-final discovery-report-5.json "
            "--jacoco-run-start validation/jacoco-0.xml "
            "--jacoco-after-api discovery/jacoco-deep-0.xml "
            "--jacoco-final discovery/jacoco-deep-5.xml "
            "--target-state targets.json "
            "--stop-decision validation/api-stop-decision.json "
            "--stop-decision discovery/deep-stop-decision.json "
            "--validation-command './gradlew test -Pcoordinates=group:artifact:version' "
            "--output-dir runtime/code-coverage/finalization"
        ),
        formatter_class=argparse.RawTextHelpFormatter,
    )
    parser.add_argument("--coordinate", required=True, help="group:artifact:version.")
    parser.add_argument(
        "--coverage-suite-path",
        required=True,
        help="Repository-relative dedicated coverage suite path.",
    )
    parser.add_argument("--api-baseline", required=True, help="API baseline JSON.")
    parser.add_argument("--api-final", required=True, help="API final JSON.")
    parser.add_argument("--deep-baseline", required=True, help="Deep baseline JSON.")
    parser.add_argument("--deep-final", required=True, help="Deep final JSON.")
    parser.add_argument(
        "--jacoco-run-start",
        required=True,
        help="JaCoCo XML for the run's first checkpoint, before any phase ran.",
    )
    parser.add_argument(
        "--jacoco-after-api",
        required=True,
        help=(
            "JaCoCo XML for the API/deep phase boundary. This is the deep "
            "phase's own first report, so both universes are counted from one "
            "instant rather than from two phases' separate snapshots."
        ),
    )
    parser.add_argument(
        "--jacoco-final",
        required=True,
        help="JaCoCo XML for the run's last checkpoint.",
    )
    parser.add_argument(
        "--target-state",
        action="append",
        default=[],
        dest="target_state_paths",
        help="Optional deep target-state JSON; repeat in chronological order.",
    )
    parser.add_argument(
        "--stop-decision",
        action="append",
        default=[],
        dest="stop_decision_paths",
        help="Optional phase stop-decision JSON; repeat once per phase.",
    )
    parser.add_argument(
        "--validation-command",
        action="append",
        required=True,
        dest="validation_commands",
        help="Successful command; repeat to preserve every exact command.",
    )
    parser.add_argument(
        "--output-dir",
        required=True,
        help="Directory for final-metrics.json and final-summary.md.",
    )
    return parser


def main() -> int:
    args: argparse.Namespace = build_parser().parse_args()
    try:
        metrics: dict[str, Any] = finalize_coverage(
            args.coordinate,
            args.coverage_suite_path,
            args.api_baseline,
            args.api_final,
            args.deep_baseline,
            args.deep_final,
            (args.jacoco_run_start, args.jacoco_after_api, args.jacoco_final),
            args.target_state_paths,
            args.stop_decision_paths,
            args.validation_commands,
            args.output_dir,
        )
    except (FinalizationError, OSError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 2
    print(
        f"Finalized {metrics['coordinate']}: "
        f"API JaCoCo {metrics['apiJacoco']['final']['coveragePercent']}%, "
        f"deep JaCoCo {metrics['deepJacoco']['final']['coveragePercent']}%."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
