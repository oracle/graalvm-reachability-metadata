# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Validated reading of coverage phase reports for finalization.

Owns the finalization input primitives and the API, deep, and PGO report
snapshots that `code_coverage_finalize.py` assembles into the final evidence
(§AR-code-coverage-improvement).
"""

from __future__ import annotations

import json
import os
import re
from typing import Any

from utility_scripts.code_coverage_model import parse_inventory_id

COORDINATE_PATTERN = re.compile(
    r"^[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+:[A-Za-z0-9_.+-]+$"
)


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


def _schema_path(file_name: str) -> str:
    root: str = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    return os.path.join(root, "schemas", file_name)
