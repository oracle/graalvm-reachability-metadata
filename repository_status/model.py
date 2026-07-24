"""Typed policy and GitHub issue models. §FS-repository-status-report"""

from __future__ import annotations

import json
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any


TIERS: tuple[str, ...] = ("high", "priority", "normal")
PROJECT_STATUSES: tuple[str, ...] = ("Todo", "In Progress", "Done")


class StatusReportError(RuntimeError):
    """Raised when status input or configuration is invalid."""


@dataclass(frozen=True)
class AgeBand:
    """An inclusive issue-age range."""

    name: str
    minimum_days: int
    maximum_days: int | None

    def contains(self, age_days: int) -> bool:
        """Return whether an age falls inside this band."""
        return age_days >= self.minimum_days and (
            self.maximum_days is None or age_days <= self.maximum_days
        )


@dataclass(frozen=True)
class Policy:
    """Versioned calculation policy."""

    schema_version: int
    repository: str
    project_title: str
    default_window_days: int
    age_debt_meter_maximum: int
    priority_labels: dict[str, str]
    priority_weights: dict[str, int]
    age_bands: tuple[AgeBand, ...]

    def priority_for(self, labels: tuple[str, ...]) -> str:
        """Classify labels into one mutually exclusive priority tier."""
        label_set: set[str] = set(labels)
        if self.priority_labels["high"] in label_set:
            return "high"
        if self.priority_labels["priority"] in label_set:
            return "priority"
        return "normal"

    def weight_for(self, tier: str) -> int:
        """Return the configured positive weight for a tier."""
        return self.priority_weights[tier]

    def age_band_for(self, age_days: int) -> str:
        """Return the configured age band for an age."""
        for age_band in self.age_bands:
            if age_band.contains(age_days):
                return age_band.name
        raise StatusReportError(f"No age band covers {age_days} day(s)")


@dataclass(frozen=True)
class Issue:
    """Normalized GitHub issue data used by all calculations."""

    number: int
    title: str
    url: str
    state: str
    created_at: datetime
    closed_at: datetime | None
    updated_at: datetime
    labels: tuple[str, ...]
    project_status: str | None


def parse_datetime(value: Any, field_name: str) -> datetime:
    """Parse a required GitHub ISO-8601 timestamp."""
    if not isinstance(value, str) or not value:
        raise StatusReportError(f"Issue field '{field_name}' must be a timestamp")
    normalized_value: str = value[:-1] + "+00:00" if value.endswith("Z") else value
    try:
        timestamp: datetime = datetime.fromisoformat(normalized_value)
    except ValueError as exc:
        raise StatusReportError(
            f"Issue field '{field_name}' has invalid timestamp '{value}'"
        ) from exc
    if timestamp.tzinfo is None:
        raise StatusReportError(f"Issue field '{field_name}' must include a timezone")
    return timestamp


def issue_from_github(payload: dict[str, Any], project_title: str) -> Issue:
    """Normalize one `gh issue list --json` record."""
    labels_payload: list[Any] = payload.get("labels", [])
    labels: tuple[str, ...] = tuple(
        sorted(
            label["name"]
            for label in labels_payload
            if isinstance(label, dict) and isinstance(label.get("name"), str)
        )
    )
    project_status: str | None = None
    project_items: list[Any] = payload.get("projectItems", [])
    for project_item in project_items:
        if not isinstance(project_item, dict) or project_item.get("title") != project_title:
            continue
        status_payload: Any = project_item.get("status")
        if isinstance(status_payload, dict) and isinstance(status_payload.get("name"), str):
            project_status = status_payload["name"]
        break

    closed_value: Any = payload.get("closedAt")
    return Issue(
        number=int(payload["number"]),
        title=str(payload["title"]),
        url=str(payload["url"]),
        state=str(payload["state"]).upper(),
        created_at=parse_datetime(payload.get("createdAt"), "createdAt"),
        closed_at=parse_datetime(closed_value, "closedAt") if closed_value else None,
        updated_at=parse_datetime(payload.get("updatedAt"), "updatedAt"),
        labels=labels,
        project_status=project_status,
    )


def load_policy(path: Path) -> Policy:
    """Load and assert the complete policy contract."""
    try:
        payload: Any = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise StatusReportError(f"Cannot load policy '{path}': {exc}") from exc
    if not isinstance(payload, dict):
        raise StatusReportError("Policy root must be an object")

    labels: Any = payload.get("priorityLabels")
    weights: Any = payload.get("priorityWeights")
    bands_payload: Any = payload.get("ageBands")
    if not isinstance(labels, dict) or set(labels) != {"high", "priority"}:
        raise StatusReportError("priorityLabels must define exactly high and priority")
    if not isinstance(weights, dict) or set(weights) != set(TIERS):
        raise StatusReportError("priorityWeights must define high, priority, and normal")
    if any(not isinstance(weights[tier], int) or weights[tier] <= 0 for tier in TIERS):
        raise StatusReportError("Every priority weight must be a positive integer")
    if not isinstance(bands_payload, list) or not bands_payload:
        raise StatusReportError("ageBands must be a non-empty array")

    age_bands: tuple[AgeBand, ...] = tuple(
        AgeBand(
            name=str(band["name"]),
            minimum_days=int(band["minimumDays"]),
            maximum_days=(
                int(band["maximumDays"])
                if band.get("maximumDays") is not None
                else None
            ),
        )
        for band in bands_payload
        if isinstance(band, dict)
    )
    if len(age_bands) != len(bands_payload):
        raise StatusReportError("Every age band must be an object")
    _validate_age_bands(age_bands)

    repository: Any = payload.get("repository")
    project_title: Any = payload.get("projectTitle")
    default_window_days: Any = payload.get("defaultWindowDays")
    age_debt_meter_maximum: Any = payload.get("ageDebtMeterMaximum")
    schema_version: Any = payload.get("schemaVersion")
    if not isinstance(repository, str) or repository.count("/") != 1:
        raise StatusReportError("repository must use owner/name format")
    if not isinstance(project_title, str) or not project_title:
        raise StatusReportError("projectTitle must be a non-empty string")
    if not isinstance(default_window_days, int) or default_window_days <= 0:
        raise StatusReportError("defaultWindowDays must be a positive integer")
    if not isinstance(age_debt_meter_maximum, int) or age_debt_meter_maximum <= 0:
        raise StatusReportError("ageDebtMeterMaximum must be a positive integer")
    if not isinstance(schema_version, int) or schema_version <= 0:
        raise StatusReportError("schemaVersion must be a positive integer")

    return Policy(
        schema_version=schema_version,
        repository=repository,
        project_title=project_title,
        default_window_days=default_window_days,
        age_debt_meter_maximum=age_debt_meter_maximum,
        priority_labels={key: str(value) for key, value in labels.items()},
        priority_weights={tier: int(weights[tier]) for tier in TIERS},
        age_bands=age_bands,
    )


def _validate_age_bands(age_bands: tuple[AgeBand, ...]) -> None:
    """Assert that age bands cover every non-negative day exactly once."""
    expected_minimum: int = 0
    seen_names: set[str] = set()
    for index, age_band in enumerate(age_bands):
        if not age_band.name or age_band.name in seen_names:
            raise StatusReportError("Age band names must be non-empty and unique")
        seen_names.add(age_band.name)
        if age_band.minimum_days != expected_minimum:
            raise StatusReportError("Age bands must be contiguous and begin at zero")
        if age_band.maximum_days is None:
            if index != len(age_bands) - 1:
                raise StatusReportError("Only the final age band may be unbounded")
            return
        if age_band.maximum_days < age_band.minimum_days:
            raise StatusReportError("Age band maximum cannot precede its minimum")
        expected_minimum = age_band.maximum_days + 1
    raise StatusReportError("The final age band must be unbounded")
