# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Input loading and durable target-state handling for the deep-method report.

Owns the analyzer's input contract: JSON documents, the extractor's library
method list, and the repeatable coordinate-scoped target states
(§AR-code-coverage-improvement.4.2).
"""

from __future__ import annotations

import csv
import json
from dataclasses import dataclass

from utility_scripts.code_coverage_model import parse_inventory_id

#: Uncovered targets leave the prompt after this many unsuccessful attempts
#: (§AR-code-coverage-improvement.4.2).
MAX_UNCOVERED_ATTEMPTS = 3
TARGET_STATE_STATUSES: frozenset[str] = frozenset({
    "pending", "selected", "attempted", "completed", "skipped", "exhausted", "failed",
})
TERMINAL_TARGET_STATUSES: frozenset[str] = frozenset({"completed", "skipped", "exhausted", "failed"})


class ProfileFormatError(RuntimeError):
    """Raised when report inputs violate the analyzer contract."""


def _load_json_object(path: str, label: str) -> dict:
    try:
        with open(path, "r", encoding="utf-8") as json_file:
            document = json.load(json_file)
    except (OSError, UnicodeError) as error:
        raise ProfileFormatError(f"Cannot read {label} '{path}'.") from error
    except json.JSONDecodeError as error:
        raise ProfileFormatError(f"Invalid JSON in {label} '{path}'.") from error
    if not isinstance(document, dict):
        raise ProfileFormatError(f"{label.capitalize()} '{path}' must contain a JSON object.")
    return document


def load_library_methods(path: str) -> set[str]:
    """Read the canonical ids the resolved library jars declare.

    The file is the `methods.csv` that `java/CallGraphExtractor.java` writes for
    the API phase, so both phases anchor to the same jars rather than to
    whatever the JaCoCo report happens to instrument.
    """
    try:
        with open(path, "r", encoding="utf-8", newline="") as handle:
            rows = list(csv.reader(handle))
    except OSError as error:
        raise ProfileFormatError(f"Cannot read library method list '{path}'.") from error
    if not rows or rows[0][:1] != ["id"]:
        raise ProfileFormatError(f"Library method list '{path}' has no 'id' header column.")
    methods: set[str] = {row[0] for row in rows[1:] if row and row[0]}
    if not methods:
        raise ProfileFormatError(f"Library method list '{path}' declares no methods.")
    return methods


def load_library_line_numbers(path: str) -> dict[str, tuple[tuple[int, int], ...]]:
    """Read exact bytecode-to-source mappings from extractor method rows."""
    try:
        with open(path, "r", encoding="utf-8", newline="") as handle:
            reader = csv.DictReader(handle)
            if reader.fieldnames is None or "id" not in reader.fieldnames:
                raise ProfileFormatError(
                    f"Library method list '{path}' has no 'id' header column."
                )
            if "lineNumbers" not in reader.fieldnames:
                return {}
            rows: list[dict[str, str | None]] = list(reader)
    except OSError as error:
        raise ProfileFormatError(f"Cannot read library method list '{path}'.") from error

    result: dict[str, tuple[tuple[int, int], ...]] = {}
    for row in rows:
        method_id: str = row.get("id") or ""
        raw_entries: str = row.get("lineNumbers") or ""
        entries: list[tuple[int, int]] = []
        for raw_entry in raw_entries.split(";"):
            if not raw_entry:
                continue
            raw_bci, separator, raw_line = raw_entry.partition(":")
            try:
                bci: int = int(raw_bci)
                line: int = int(raw_line)
            except ValueError as error:
                raise ProfileFormatError(
                    f"Library method list '{path}' has invalid line-number entry "
                    f"'{raw_entry}' for '{method_id}'."
                ) from error
            if not separator or bci < 0 or line < 0:
                raise ProfileFormatError(
                    f"Library method list '{path}' has invalid line-number entry "
                    f"'{raw_entry}' for '{method_id}'."
                )
            entries.append((bci, line))
        if entries:
            result[method_id] = tuple(sorted(set(entries)))
    return result


def library_owners(library_methods: set[str] | None) -> set[str] | None:
    """The declaring types of the library method list, for Definition 10'.

    Jar membership, not package prefix: the library's own test-classifier
    artifact ships classes in the library's packages
    (§AR-code-coverage-improvement.4.2).
    """
    if library_methods is None:
        return None
    return {method_id.split("#", 1)[0] for method_id in library_methods}


@dataclass(frozen=True)
class TargetState:
    """Durable state for one exact deep-coverage target."""

    status: str = "pending"
    attempt_count: int = 0
    last_attempted_iteration: int | None = None
    reason: str | None = None

    @property
    def terminal(self) -> bool:
        return self.status in TERMINAL_TARGET_STATUSES


def _require_coordinate(document: dict, expected: str, label: str) -> None:
    actual = document.get("coordinate")
    if not isinstance(actual, str) or not actual.strip():
        raise ProfileFormatError(f"{label} is missing coordinate.")
    if actual != expected:
        raise ProfileFormatError(
            f"{label} coordinate '{actual}' does not match '{expected}'."
        )


def _parse_target_state(entry: object, path: str, index: int) -> tuple[str, TargetState]:
    if not isinstance(entry, dict):
        raise ProfileFormatError(f"Target state '{path}' target {index} must be an object.")

    method_id = entry.get("id")
    ref = parse_inventory_id(method_id) if isinstance(method_id, str) else None
    if (
            ref is None
            or ref.return_type is None
            or ref.canonical_id != method_id
    ):
        raise ProfileFormatError(
            f"Target state '{path}' target {index} has a non-canonical method id."
        )

    status = entry.get("status")
    if status not in TARGET_STATE_STATUSES:
        raise ProfileFormatError(
            f"Target state '{path}' target '{method_id}' has unknown status '{status}'."
        )

    attempt_count = entry.get("attemptCount")
    if type(attempt_count) is not int or attempt_count < 0:
        raise ProfileFormatError(
            f"Target state '{path}' target '{method_id}' needs a non-negative attemptCount."
        )

    last_iteration = entry.get("lastAttemptedIteration")
    if (
            last_iteration is not None
            and (type(last_iteration) is not int or last_iteration <= 0)
    ):
        raise ProfileFormatError(
            f"Target state '{path}' target '{method_id}' has invalid lastAttemptedIteration."
        )

    reason = entry.get("reason")
    if reason is not None and (not isinstance(reason, str) or not reason.strip()):
        raise ProfileFormatError(
            f"Target state '{path}' target '{method_id}' has an invalid reason."
        )
    if status in {"skipped", "exhausted", "failed"} and reason is None:
        raise ProfileFormatError(
            f"Target state '{path}' target '{method_id}' requires a reason."
        )

    return method_id, TargetState(
        status=status,
        attempt_count=attempt_count,
        last_attempted_iteration=last_iteration,
        reason=reason,
    )


def load_target_states(paths: list[str] | None, coordinate: str) -> dict[str, TargetState]:
    """Load repeatable coordinate-scoped state files; later files take precedence."""
    states: dict[str, TargetState] = {}
    for path in paths or []:
        document = _load_json_object(path, "target state")
        _require_coordinate(document, coordinate, "Target state")
        entries = document.get("targets")
        if not isinstance(entries, list):
            raise ProfileFormatError(f"Target state '{path}' must contain a targets list.")
        seen: set[str] = set()
        for index, entry in enumerate(entries, start=1):
            method_id, state = _parse_target_state(entry, path, index)
            if method_id in seen:
                raise ProfileFormatError(
                    f"Target state '{path}' repeats target '{method_id}'."
                )
            seen.add(method_id)
            states[method_id] = state
    return states


def _target_state_to_json(method_id: str, state: TargetState) -> dict:
    return {
        "id": method_id,
        "status": state.status,
        "terminal": state.terminal,
        "attemptCount": state.attempt_count,
        "lastAttemptedIteration": state.last_attempted_iteration,
        "reason": state.reason,
    }


def _effective_target_state(
        method_id: str,
        target_states: dict[str, TargetState],
        attempt_counts: dict[str, int],
        jacoco_uncovered: bool = False,
) -> TargetState:
    state = target_states.get(method_id, TargetState())
    attempt_count: int = max(state.attempt_count, attempt_counts.get(method_id, 0))
    if (
            jacoco_uncovered
            and not state.terminal
            and attempt_count >= MAX_UNCOVERED_ATTEMPTS
    ):
        return TargetState(
            status="exhausted",
            attempt_count=attempt_count,
            last_attempted_iteration=state.last_attempted_iteration,
            reason=f"{MAX_UNCOVERED_ATTEMPTS} attempts without coverage change",
        )
    return TargetState(
        status=state.status,
        attempt_count=attempt_count,
        last_attempted_iteration=state.last_attempted_iteration,
        reason=state.reason,
    )
