# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Build schema-validated final evidence for code coverage improvement.

JaCoCo is the only coverage authority. Sampled PGO is retained only as
navigation guidance (§WF-code-coverage-improvement).
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from typing import Any

from jsonschema import Draft202012Validator

from utility_scripts.code_coverage_model import parse_inventory_id

SCHEMA_VERSION = "1.0.0"
SCHEMA_FILE = "code_coverage_final_metrics_schema.json"
TARGET_STATE_SCHEMA_FILE = "code_coverage_target_state_schema.json"
COORDINATE_PATTERN = re.compile(
    r"^[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+:[A-Za-z0-9_.+-]+$"
)
TERMINAL_NEGATIVE_STATUSES = frozenset({"skipped", "exhausted", "failed"})
TARGET_STATE_STATUSES = frozenset({
    "pending",
    "selected",
    "attempted",
    "completed",
    "skipped",
    "exhausted",
    "failed",
})


class FinalizationError(ValueError):
    """Raised when finalization inputs violate the workflow contract."""


def _read_object(path: str, label: str) -> dict[str, Any]:
    try:
        with open(path, "r", encoding="utf-8") as source:
            value: Any = json.load(source)
    except json.JSONDecodeError as error:
        raise FinalizationError(f"{label} is not valid JSON: {path}: {error}") from error
    if not isinstance(value, dict):
        raise FinalizationError(f"{label} must contain a JSON object: {path}")
    return value


def _object(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise FinalizationError(f"{label} must be an object.")
    return value


def _array(value: Any, label: str) -> list[Any]:
    if not isinstance(value, list):
        raise FinalizationError(f"{label} must be an array.")
    return value


def _string(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise FinalizationError(f"{label} must be a non-empty string.")
    return value


def _integer(value: Any, label: str) -> int:
    if type(value) is not int or value < 0:
        raise FinalizationError(f"{label} must be a non-negative integer.")
    return value


def _method_id(value: Any, label: str) -> str:
    method_id: str = _string(value, label)
    ref: Any = parse_inventory_id(method_id)
    if ref is None or ref.return_type is None or ref.canonical_id != method_id:
        raise FinalizationError(f"{label} must be a canonical method id.")
    return method_id


def _coordinate(value: str) -> str:
    if not COORDINATE_PATTERN.fullmatch(value):
        raise FinalizationError(
            f"Coordinate must use group:artifact:version form; got '{value}'."
        )
    return value


def _check_coordinate(report: dict[str, Any], coordinate: str, label: str) -> None:
    actual: str = _string(report.get("coordinate"), f"{label}.coordinate")
    if actual != coordinate:
        raise FinalizationError(
            f"{label}.coordinate is '{actual}', expected '{coordinate}'."
        )


def _coverage_statuses(
        report: dict[str, Any],
        field: str,
        label: str,
        supported: frozenset[str],
) -> dict[str, str]:
    entries: list[Any] = _array(report.get(field), f"{label}.{field}")
    statuses: dict[str, str] = {}
    for index, item in enumerate(entries):
        entry: dict[str, Any] = _object(item, f"{label}.{field}[{index}]")
        method_id: str = _method_id(
            entry.get("id"), f"{label}.{field}[{index}].id"
        )
        if method_id in statuses:
            raise FinalizationError(
                f"{label}.{field} repeats method id '{method_id}'."
            )
        status: str = _string(
            entry.get("status"), f"{label}.{field}[{index}].status"
        )
        if status not in supported:
            raise FinalizationError(
                f"{label}.{field}[{index}].status is unsupported: '{status}'."
            )
        statuses[method_id] = status
    return statuses


def _percent(covered: int, denominator: int) -> float:
    return round(100.0 * covered / denominator, 2) if denominator else 0.0


def _api_snapshot(
        report: dict[str, Any], coordinate: str, label: str
) -> dict[str, Any]:
    _check_coordinate(report, coordinate, label)
    summary: dict[str, Any] = _object(report.get("summary"), f"{label}.summary")
    statuses: dict[str, str] = _coverage_statuses(
        report, "targets", label, frozenset({"covered", "uncovered", "not-reported"})
    )
    counts: dict[str, int] = {
        status: list(statuses.values()).count(status)
        for status in ("covered", "uncovered", "not-reported")
    }
    total: int = _integer(summary.get("total"), f"{label}.summary.total")
    measured: int = _integer(summary.get("measured"), f"{label}.summary.measured")
    covered: int = _integer(summary.get("covered"), f"{label}.summary.covered")
    uncovered: int = _integer(
        summary.get("uncovered"), f"{label}.summary.uncovered"
    )
    not_reported: int = _integer(
        summary.get("notReported"), f"{label}.summary.notReported"
    )
    actual: tuple[int, ...] = (
        total,
        measured,
        covered,
        uncovered,
        not_reported,
    )
    expected: tuple[int, ...] = (
        len(statuses),
        counts["covered"] + counts["uncovered"],
        counts["covered"],
        counts["uncovered"],
        counts["not-reported"],
    )
    if actual != expected:
        raise FinalizationError(
            f"{label}.summary counts do not match its target statuses."
        )
    return {
        "total": total,
        "measured": measured,
        "covered": covered,
        "uncovered": uncovered,
        "notReported": not_reported,
        "coveragePercent": _percent(covered, measured),
    }


def _deep_snapshot(
        report: dict[str, Any], coordinate: str, label: str
) -> dict[str, Any]:
    _check_coordinate(report, coordinate, label)
    if report.get("profileKind") != "sampled-guidance":
        raise FinalizationError(f"{label}.profileKind must be 'sampled-guidance'.")
    summary: dict[str, Any] = _object(report.get("summary"), f"{label}.summary")
    statuses: dict[str, str] = _coverage_statuses(
        report, "deepMethods", label, frozenset({"covered", "uncovered"})
    )
    counts: dict[str, int] = {
        status: list(statuses.values()).count(status)
        for status in ("covered", "uncovered")
    }
    total: int = _integer(
        summary.get("deepMethods"), f"{label}.summary.deepMethods"
    )
    covered: int = _integer(
        summary.get("deepCovered"), f"{label}.summary.deepCovered"
    )
    uncovered: int = _integer(
        summary.get("deepUncovered"), f"{label}.summary.deepUncovered"
    )
    if (
        total != len(statuses)
        or covered != counts["covered"]
        or uncovered != counts["uncovered"]
        or covered + uncovered != total
    ):
        raise FinalizationError(
            f"{label}.summary counts do not match its deepMethods statuses."
        )
    return {
        "total": total,
        "covered": covered,
        "uncovered": uncovered,
        "coveragePercent": _percent(covered, total),
    }


def _pgo_snapshot(report: dict[str, Any], label: str) -> dict[str, int]:
    summary: dict[str, Any] = _object(report.get("summary"), f"{label}.summary")
    fields: tuple[tuple[str, str], ...] = (
        ("samplingContexts", "samplingContexts"),
        ("sampledMethods", "sampledObservedMethods"),
        ("sampleCount", "totalSampleCount"),
        ("sampledJoins", "sampledJoins"),
    )
    return {
        output_name: _integer(summary.get(input_name), f"{label}.summary.{input_name}")
        for output_name, input_name in fields
    }


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


def _validate_target_state_document(
        document: dict[str, Any], path: str
) -> None:
    schema: dict[str, Any] = _read_object(
        _schema_path(TARGET_STATE_SCHEMA_FILE), "Target-state schema"
    )
    errors: list[Any] = sorted(
        Draft202012Validator(schema).iter_errors(document),
        key=lambda error: [str(part) for part in error.absolute_path],
    )
    if errors:
        error: Any = errors[0]
        location: str = (
            ".".join(str(part) for part in error.absolute_path) or "<root>"
        )
        raise FinalizationError(
            f"Target-state file '{path}' does not match "
            f"{TARGET_STATE_SCHEMA_FILE} at {location}: {error.message}"
        )


def _load_latest_target_states(
        paths: list[str], coordinate: str
) -> dict[str, dict[str, Any]]:
    if not paths:
        raise FinalizationError("At least one target-state JSON file is required.")
    latest: dict[str, dict[str, Any]] = {}
    for path in paths:
        document: dict[str, Any] = _read_object(path, "Target-state file")
        _validate_target_state_document(document, path)
        _check_coordinate(document, coordinate, "Target-state file")
        seen: set[str] = set()
        targets: list[Any] = _array(
            document.get("targets"), "Target-state file.targets"
        )
        for index, item in enumerate(targets):
            target: dict[str, Any] = _object(
                item, f"Target-state file.targets[{index}]"
            )
            target_id: str = _method_id(
                target.get("id"), f"Target-state file.targets[{index}].id"
            )
            if target_id in seen:
                raise FinalizationError(
                    f"Target-state file '{path}' repeats target '{target_id}'."
                )
            seen.add(target_id)
            status: str = _string(
                target.get("status"), f"Target-state file.targets[{index}].status"
            )
            if status not in TARGET_STATE_STATUSES:
                raise FinalizationError(
                    f"Target-state target '{target_id}' has unknown status '{status}'."
                )
            reason: str | None = target.get("reason")
            if reason is not None:
                _string(reason, f"Target-state file.targets[{index}].reason")
            if status in TERMINAL_NEGATIVE_STATUSES and reason is None:
                raise FinalizationError(
                    f"Target '{target_id}' with status '{status}' requires a reason."
                )
            latest[target_id] = {
                "id": target_id,
                "status": status,
                "attemptCount": _integer(
                    target.get("attemptCount"),
                    f"Target-state file.targets[{index}].attemptCount",
                ),
                "lastAttemptedIteration": target.get("lastAttemptedIteration"),
                "reason": reason,
            }
    return latest


def _completed_transitions(
        baseline: dict[str, str],
        final: dict[str, str],
        phase: str,
        states: dict[str, dict[str, Any]] | None = None,
) -> list[dict[str, Any]]:
    completed: list[dict[str, Any]] = []
    for method_id in sorted(baseline):
        if baseline[method_id] == "covered" or final.get(method_id) != "covered":
            continue
        target: dict[str, Any] = {
            "id": method_id,
            "phase": phase,
            "status": "completed",
        }
        state: dict[str, Any] | None = (states or {}).get(method_id)
        if phase == "deep" and state is not None:
            target["attemptCount"] = state["attemptCount"]
            target["lastAttemptedIteration"] = state["lastAttemptedIteration"]
        completed.append(target)
    return completed


def _target_outcomes(
        paths: list[str],
        coordinate: str,
        api_baseline_report: dict[str, Any],
        api_final_report: dict[str, Any],
        deep_baseline_report: dict[str, Any],
        deep_final_report: dict[str, Any],
) -> dict[str, list[dict[str, Any]]]:
    states: dict[str, dict[str, Any]] = _load_latest_target_states(
        paths, coordinate
    )
    api_baseline: dict[str, str] = _coverage_statuses(
        api_baseline_report,
        "targets",
        "API baseline",
        frozenset({"covered", "uncovered", "not-reported"}),
    )
    api_final: dict[str, str] = _coverage_statuses(
        api_final_report,
        "targets",
        "API final",
        frozenset({"covered", "uncovered", "not-reported"}),
    )
    deep_baseline: dict[str, str] = _coverage_statuses(
        deep_baseline_report,
        "deepMethods",
        "Deep baseline",
        frozenset({"covered", "uncovered"}),
    )
    deep_final: dict[str, str] = _coverage_statuses(
        deep_final_report,
        "deepMethods",
        "Deep final",
        frozenset({"covered", "uncovered"}),
    )
    invalid_state_ids: list[str] = sorted(set(states) - set(deep_final))
    if invalid_state_ids:
        raise FinalizationError(
            f"Target state id '{invalid_state_ids[0]}' is not in the current "
            "deep JaCoCo universe."
        )
    for method_id, state in states.items():
        if state["status"] == "completed" and deep_final[method_id] != "covered":
            raise FinalizationError(
                f"Target state '{method_id}' is completed but final JaCoCo "
                "reports it uncovered."
            )


    completed: list[dict[str, Any]] = [
        *_completed_transitions(api_baseline, api_final, "api"),
        *_completed_transitions(deep_baseline, deep_final, "deep", states),
    ]
    result: dict[str, list[dict[str, Any]]] = {
        "completed": sorted(
            completed, key=lambda target: (target["phase"], target["id"])
        ),
        "skipped": [],
        "exhausted": [],
        "failed": [],
    }
    for method_id, state in states.items():
        status: str = state["status"]
        if (
                status not in TERMINAL_NEGATIVE_STATUSES
                or deep_final.get(method_id) == "covered"
        ):
            continue
        result[status].append({
            "id": method_id,
            "phase": "deep",
            "status": status,
            "attemptCount": state["attemptCount"],
            "lastAttemptedIteration": state["lastAttemptedIteration"],
            "reason": state["reason"],
        })
    for status in TERMINAL_NEGATIVE_STATUSES:
        result[status].sort(key=lambda target: target["id"])
    return result


def _commands(values: list[str]) -> list[str]:
    if not values:
        raise FinalizationError("At least one validation command is required.")
    for index, value in enumerate(values):
        _string(value, f"validationCommands[{index}]")
    return list(values)


def _schema_path(file_name: str = SCHEMA_FILE) -> str:
    root: str = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    return os.path.join(root, "schemas", file_name)


def validate_final_metrics(metrics: dict[str, Any]) -> None:
    schema: dict[str, Any] = _read_object(_schema_path(), "Final metrics schema")
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
        target_state_paths: list[str],
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
        "coordinate": coordinate,
        "coverageSuitePath": _suite_path(coverage_suite_path),
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
            "--coverage-suite-path tests/group/artifact/version/code-coverage "
            "--api-baseline api-cover-report-0.json "
            "--api-final api-cover-report-5.json "
            "--deep-baseline discovery-report-0.json "
            "--deep-final discovery-report-5.json "
            "--target-state targets.json "
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
        "--target-state",
        action="append",
        required=True,
        dest="target_state_paths",
        help="Deep target-state JSON; repeat in chronological order.",
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
            args.target_state_paths,
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
