"""GitHub CLI data source for repository issue status. §FS-repository-status-report.1"""

from __future__ import annotations

import json
import shutil
import subprocess
from datetime import datetime, timedelta
from typing import Any

from repository_status.model import Issue, Policy, StatusReportError, issue_from_github


ISSUE_FIELDS: str = (
    "number,title,url,state,createdAt,closedAt,updatedAt,labels,projectItems"
)
GITHUB_SEARCH_MAX_RESULTS: int = 1_000


def fetch_snapshot(
        policy: Policy,
        generated_at: datetime,
        window_days: int,
        issue_limit: int,
) -> tuple[list[Issue], list[Issue]]:
    """Fetch open issues and recently closed issues used by the flow window."""
    if shutil.which("gh") is None:
        raise StatusReportError("GitHub CLI 'gh' is required but was not found")

    open_payload: list[dict[str, Any]] = _run_issue_list(
        repository=policy.repository,
        state="open",
        limit=issue_limit,
    )
    cutoff_date: str = (generated_at - timedelta(days=window_days)).date().isoformat()
    closed_payload: list[dict[str, Any]] = _run_issue_list(
        repository=policy.repository,
        state="closed",
        limit=issue_limit,
        search=f"closed:>={cutoff_date}",
    )
    return (
        [issue_from_github(item, policy.project_title) for item in open_payload],
        [issue_from_github(item, policy.project_title) for item in closed_payload],
    )


def _run_issue_list(
        repository: str,
        state: str,
        limit: int,
        search: str | None = None,
) -> list[dict[str, Any]]:
    """Run one bounded `gh issue list` query and validate its JSON shape."""
    query_limit: int = min(limit, GITHUB_SEARCH_MAX_RESULTS) if search else limit
    command: list[str] = [
        "gh",
        "issue",
        "list",
        "--repo",
        repository,
        "--state",
        state,
        "--limit",
        str(query_limit),
        "--json",
        ISSUE_FIELDS,
    ]
    if search is not None:
        command.extend(["--search", search])
    try:
        result: subprocess.CompletedProcess[str] = subprocess.run(
            command,
            capture_output=True,
            text=True,
            check=False,
            timeout=180,
        )
    except subprocess.TimeoutExpired as exc:
        raise StatusReportError(f"GitHub issue query timed out for state '{state}'") from exc
    if result.returncode != 0:
        detail: str = (result.stderr or result.stdout).strip()
        raise StatusReportError(
            f"GitHub issue query failed for state '{state}': {detail or 'unknown error'}"
        )
    try:
        payload: Any = json.loads(result.stdout)
    except json.JSONDecodeError as exc:
        raise StatusReportError("GitHub CLI returned invalid JSON") from exc
    if not isinstance(payload, list) or any(not isinstance(item, dict) for item in payload):
        raise StatusReportError("GitHub CLI issue result must be an array of objects")
    if len(payload) >= query_limit:
        limit_kind: str = "GitHub search" if search else "configured query"
        raise StatusReportError(
            f"GitHub query reached the {limit_kind} limit of {query_limit}; "
            "reduce --window-days or increase --issue-limit where applicable"
        )
    return payload
