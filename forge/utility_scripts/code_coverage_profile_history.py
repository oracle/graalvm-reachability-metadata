# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Discovery-report history for the deep-method report.

Measurement carries attempt state deterministically in its own report history:
the previous report says which targets were prompted and what state they
reached, so no agent ever writes target state (§AR-code-coverage-improvement.4.2).
"""

from __future__ import annotations

import os

from utility_scripts.code_coverage_profile_inputs import (
    ProfileFormatError,
    TargetState,
    load_json_object,
    parse_target_state,
)


def previous_report(output_dir: str, iteration: int) -> dict | None:
    previous_path: str = os.path.join(output_dir, f"discovery-report-{iteration - 1}.json")
    if iteration <= 0 or not os.path.isfile(previous_path):
        return None
    return load_json_object(previous_path, "previous discovery report")


def next_attempt_counts(previous: dict | None) -> dict[str, int]:
    if previous is None:
        return {}
    counts: dict[str, int] = {
        entry["id"]: int(entry.get("attemptCount", 0))
        for entry in previous.get("uncoveredPaths", [])
    }
    for method_id in previous.get("promptTargetIds", []):
        counts[method_id] = counts.get(method_id, 0) + 1
    return counts


def carried_public_targets(previous: dict | None) -> list[str] | None:
    """The public targets the phase froze at its first report; `None` before it.

    §AR-code-coverage-improvement.4.2
    """
    if previous is None:
        return None
    entries = previous.get("publicTargets")
    if not isinstance(entries, list):
        raise ProfileFormatError("Previous discovery report has no publicTargets list.")
    return [entry["id"] for entry in entries]


def previous_target_states(previous: dict | None) -> dict[str, TargetState]:
    if previous is None:
        return {}
    entries = previous.get("targetStates", [])
    if not isinstance(entries, list):
        raise ProfileFormatError("Previous discovery report has invalid targetStates.")
    states: dict[str, TargetState] = {}
    for index, entry in enumerate(entries, start=1):
        method_id, state = parse_target_state(entry, "previous discovery report", index)
        if method_id in states:
            raise ProfileFormatError(
                f"Previous discovery report repeats target '{method_id}'."
            )
        states[method_id] = state
    return states


def progress_since(previous: dict | None, report: dict) -> dict | None:
    if previous is None:
        return None
    previous_uncovered: set[str] = {
        entry["id"] for entry in previous.get("uncoveredPaths", [])
    }
    now_covered: set[str] = {
        entry["id"]
        for entry in [*report["deepMethods"], *report["publicTargets"]]
        if entry["status"] == "covered"
    }
    return {"newlyCovered": sorted(previous_uncovered & now_covered)}
