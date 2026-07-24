"""Pure repository issue measurements. §FS-repository-status-report.2"""

from __future__ import annotations

from datetime import datetime, timedelta, timezone
from statistics import median
from typing import Any

from repository_status.model import PROJECT_STATUSES, TIERS, Issue, Policy, StatusReportError


TIER_RANK: dict[str, int] = {tier: rank for rank, tier in enumerate(TIERS)}


def build_report(
        policy: Policy,
        open_issues: list[Issue],
        recently_closed_issues: list[Issue],
        generated_at: datetime,
        window_days: int,
) -> dict[str, Any]:
    """Build the shared machine-readable report model."""
    if generated_at.tzinfo is None:
        raise StatusReportError("Report generation time must include a timezone")
    normalized_at: datetime = generated_at.astimezone(timezone.utc)
    current_open_issues: list[Issue] = [issue for issue in open_issues if issue.state == "OPEN"]
    issue_records: list[dict[str, Any]] = [
        _open_issue_record(policy, issue, normalized_at) for issue in current_open_issues
    ]
    issue_records.sort(
        key=lambda item: (
            TIER_RANK[item["priority"]],
            -item["ageDays"],
            item["number"],
        )
    )

    tiers: dict[str, dict[str, Any]] = {
        tier: _tier_measurements(policy, issue_records, tier) for tier in TIERS
    }
    age_bands: dict[str, int] = {
        age_band.name: sum(
            1 for issue_record in issue_records if issue_record["ageBand"] == age_band.name
        )
        for age_band in policy.age_bands
    }
    warnings: list[dict[str, Any]] = _warnings(policy, issue_records)
    flow: dict[str, Any] = _flow_measurements(
        policy,
        current_open_issues,
        recently_closed_issues,
        normalized_at,
        window_days,
    )
    project: dict[str, int] = _project_measurements(issue_records)
    age_debt_meter: dict[str, Any] = _age_debt_meter(policy, issue_records)

    return {
        "schemaVersion": f"{policy.schema_version}.0",
        "generatedAt": _isoformat(normalized_at),
        "repository": policy.repository,
        "calculationPolicy": {
            "priorityLabels": dict(policy.priority_labels),
            "priorityWeights": dict(policy.priority_weights),
            "ageBasis": "complete days since createdAt",
            "ageBands": [
                {
                    "name": age_band.name,
                    "minimumDays": age_band.minimum_days,
                    "maximumDays": age_band.maximum_days,
                }
                for age_band in policy.age_bands
            ],
            "attentionOrder": ["priority tier", "oldest first", "issue number"],
        },
        "summary": {
            "openIssues": len(issue_records),
            "weightedBacklog": sum(item["weight"] for item in issue_records),
            "totalOpenDays": sum(item["ageDays"] for item in issue_records),
            "weightedAgeDebt": sum(item["ageDebt"] for item in issue_records),
            "priorityAgeDebt": age_debt_meter["value"],
            "flowDirection": flow["direction"],
        },
        "tiers": tiers,
        "ageBands": age_bands,
        "flow": flow,
        "ageDebtMeter": age_debt_meter,
        "project": project,
        "warnings": warnings,
        "attentionQueue": issue_records,
    }


def _open_issue_record(policy: Policy, issue: Issue, generated_at: datetime) -> dict[str, Any]:
    """Calculate all display-independent values for one unresolved issue."""
    age_days: int = max(0, (generated_at - issue.created_at).days)
    tier: str = policy.priority_for(issue.labels)
    weight: int = policy.weight_for(tier)
    return {
        "number": issue.number,
        "title": issue.title,
        "url": issue.url,
        "priority": tier,
        "weight": weight,
        "ageDays": age_days,
        "ageBand": policy.age_band_for(age_days),
        "ageDebt": weight * age_days,
        "projectStatus": issue.project_status,
        "createdAt": _isoformat(issue.created_at),
        "updatedAt": _isoformat(issue.updated_at),
        "labels": list(issue.labels),
    }


def _tier_measurements(
        policy: Policy,
        issue_records: list[dict[str, Any]],
        tier: str,
) -> dict[str, Any]:
    """Aggregate current state for one priority tier."""
    records: list[dict[str, Any]] = [
        issue_record for issue_record in issue_records if issue_record["priority"] == tier
    ]
    ages: list[int] = [issue_record["ageDays"] for issue_record in records]
    return {
        "weight": policy.weight_for(tier),
        "open": len(records),
        "weightedBacklog": sum(issue_record["weight"] for issue_record in records),
        "totalOpenDays": sum(ages),
        "weightedAgeDebt": sum(issue_record["ageDebt"] for issue_record in records),
        "oldestAgeDays": max(ages) if ages else None,
        "medianAgeDays": median(ages) if ages else None,
        "ageBands": {
            age_band.name: sum(
                1 for issue_record in records if issue_record["ageBand"] == age_band.name
            )
            for age_band in policy.age_bands
        },
    }


def _flow_measurements(
        policy: Policy,
        open_issues: list[Issue],
        recently_closed_issues: list[Issue],
        generated_at: datetime,
        window_days: int,
) -> dict[str, Any]:
    """Calculate recent issue arrival and resolution flow."""
    cutoff: datetime = generated_at - timedelta(days=window_days)
    all_recent_candidates: list[Issue] = [*open_issues, *recently_closed_issues]
    opened: list[Issue] = [
        issue
        for issue in all_recent_candidates
        if cutoff <= issue.created_at <= generated_at
    ]
    resolved: list[Issue] = [
        issue
        for issue in recently_closed_issues
        if issue.closed_at is not None and cutoff <= issue.closed_at <= generated_at
    ]
    weighted_opened: int = sum(
        policy.weight_for(policy.priority_for(issue.labels)) for issue in opened
    )
    weighted_resolved: int = sum(
        policy.weight_for(policy.priority_for(issue.labels)) for issue in resolved
    )
    weighted_net_change: int = weighted_opened - weighted_resolved
    weighted_activity: int = weighted_opened + weighted_resolved
    weighted_balance_percent: float = (
        round(weighted_resolved / weighted_activity * 100, 1)
        if weighted_activity else 50.0
    )
    direction: str = (
        "growing" if weighted_net_change > 0 else "shrinking" if weighted_net_change < 0 else "stable"
    )
    return {
        "windowDays": window_days,
        "startsAt": _isoformat(cutoff),
        "opened": len(opened),
        "resolved": len(resolved),
        "weightedOpened": weighted_opened,
        "weightedResolved": weighted_resolved,
        "weightedNetChange": weighted_net_change,
        "weightedBurnRatio": (
            round(weighted_resolved / weighted_opened, 4) if weighted_opened else None
        ),
        "weightedBalancePercent": weighted_balance_percent,
        "direction": direction,
    }


def _age_debt_meter(policy: Policy, issue_records: list[dict[str, Any]]) -> dict[str, Any]:
    """Summarize standing high- and priority-tier age debt on a fixed meter."""
    value: int = sum(
        item["ageDebt"] for item in issue_records if item["priority"] != "normal"
    )
    maximum: int = policy.age_debt_meter_maximum
    fill_percent: float = round(min(value / maximum, 1.0) * 100, 1)
    return {"value": value, "maximum": maximum, "fillPercent": fill_percent}


def _project_measurements(issue_records: list[dict[str, Any]]) -> dict[str, int]:
    """Count current project states without removing inconsistent issues."""
    return {
        "todo": sum(item["projectStatus"] == "Todo" for item in issue_records),
        "inProgress": sum(item["projectStatus"] == "In Progress" for item in issue_records),
        "done": sum(item["projectStatus"] == "Done" for item in issue_records),
        "unknown": sum(item["projectStatus"] not in PROJECT_STATUSES for item in issue_records),
    }


def _warnings(policy: Policy, issue_records: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Return aggregated, deterministic data-quality warnings."""
    high_label: str = policy.priority_labels["high"]
    priority_label: str = policy.priority_labels["priority"]
    checks: tuple[tuple[str, str, Any], ...] = (
        (
            "BOTH_PRIORITY_LABELS",
            "Issue carries both priority labels and is classified as high.",
            lambda item: high_label in item["labels"] and priority_label in item["labels"],
        ),
        (
            "OPEN_ISSUE_DONE",
            "Open issue has project status Done and remains in the backlog.",
            lambda item: item["projectStatus"] == "Done",
        ),
    )
    warnings: list[dict[str, Any]] = []
    for code, message, predicate in checks:
        issue_numbers: list[int] = sorted(
            item["number"] for item in issue_records if predicate(item)
        )
        if issue_numbers:
            warnings.append(
                {
                    "code": code,
                    "message": message,
                    "count": len(issue_numbers),
                    "issueNumbers": issue_numbers,
                }
            )
    return warnings


def _isoformat(value: datetime) -> str:
    """Format a timestamp as canonical UTC ISO-8601."""
    return value.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")
