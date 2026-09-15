# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Deep target-state validation and per-target outcome classification.

Turns baseline/final coverage statuses and the optional target-state files into
the finalization's completed/skipped/exhausted/failed target lists
(§AR-code-coverage-improvement.3.2).
"""

from __future__ import annotations

from typing import Any

from jsonschema import Draft202012Validator

from utility_scripts.code_coverage_finalize_reports import (
    FinalizationError,
    _array,
    _check_coordinate,
    _coverage_statuses,
    _integer,
    _method_id,
    _object,
    _read_object,
    _schema_path,
    _string,
)

TARGET_STATE_SCHEMA_FILE = "code_coverage_target_state_schema.json"
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


def _target_states_from_entries(
        value: Any, label: str
) -> dict[str, dict[str, Any]]:
    states: dict[str, dict[str, Any]] = {}
    entries: list[Any] = _array(value, label)
    for index, item in enumerate(entries):
        entry_label: str = f"{label}[{index}]"
        target: dict[str, Any] = _object(item, entry_label)
        target_id: str = _method_id(target.get("id"), f"{entry_label}.id")
        if target_id in states:
            raise FinalizationError(f"{label} repeats target '{target_id}'.")
        status: str = _string(target.get("status"), f"{entry_label}.status")
        if status not in TARGET_STATE_STATUSES:
            raise FinalizationError(
                f"Target-state target '{target_id}' has unknown status '{status}'."
            )
        reason: str | None = target.get("reason")
        if reason is not None:
            _string(reason, f"{entry_label}.reason")
        if status in TERMINAL_NEGATIVE_STATUSES and reason is None:
            raise FinalizationError(
                f"Target '{target_id}' with status '{status}' requires a reason."
            )
        last_iteration_value: Any = target.get("lastAttemptedIteration")
        last_iteration: int | None = None
        if last_iteration_value is not None:
            last_iteration = _integer(
                last_iteration_value, f"{entry_label}.lastAttemptedIteration"
            )
            if last_iteration == 0:
                raise FinalizationError(
                    f"{entry_label}.lastAttemptedIteration must be positive."
                )
        states[target_id] = {
            "id": target_id,
            "status": status,
            "attemptCount": _integer(
                target.get("attemptCount"), f"{entry_label}.attemptCount"
            ),
            "lastAttemptedIteration": last_iteration,
            "reason": reason,
        }
    return states


def _load_latest_target_states(
        paths: list[str], coordinate: str
) -> dict[str, dict[str, Any]]:
    """Load optional externally supplied state; the workflow itself writes none."""
    latest: dict[str, dict[str, Any]] = {}
    for path in paths:
        document: dict[str, Any] = _read_object(path, "Target-state file")
        _validate_target_state_document(document, path)
        _check_coordinate(document, coordinate, "Target-state file")
        latest.update(_target_states_from_entries(
            document.get("targets"), f"Target-state file '{path}'.targets"
        ))
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
    # Measurement owns automatic attempt and exhaustion state; explicit files
    # remain later overrides for compatibility (§AR-code-coverage-improvement.3.2).
    states: dict[str, dict[str, Any]] = _target_states_from_entries(
        deep_final_report.get("targetStates", []), "Deep final.targetStates"
    )
    states.update(_load_latest_target_states(paths, coordinate))
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
