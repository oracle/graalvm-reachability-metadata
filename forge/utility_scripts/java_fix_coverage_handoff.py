# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Deferred dynamic-access coverage handoff for Java repair workflows."""

import os
import re
from typing import Any

from git_scripts.common_git import gh
from utility_scripts.dynamic_access_report import DynamicAccessCoverageReport
from utility_scripts.library_update_alias_split import (
    PROJECT_NUMBER,
    STATUS_IN_PROGRESS,
    ensure_issue_project_status,
    format_follow_up_trailer,
)
from utility_scripts.metrics_writer import (
    PENDING_METRICS_FILENAME,
    read_pending_metrics,
    write_pending_metrics,
)


DYNAMIC_ACCESS_HANDOFF_KEY = "dynamic_access_handoff"
LIBRARY_UPDATE_LABEL = "library-update-request"


def build_dynamic_access_handoff(
        coordinate: str,
        report: DynamicAccessCoverageReport | None,
        class_threshold: int,
) -> dict[str, Any] | None:
    """Return a handoff when post-repair exploration exceeds the class threshold."""
    if report is None or class_threshold <= 0:
        return None
    uncovered_classes = [
        class_coverage.class_name
        for class_coverage in report.classes
        if class_coverage.uncovered_calls > 0
    ]
    if len(uncovered_classes) <= class_threshold:
        return None
    return {
        "coordinate": coordinate,
        "uncovered_class_count": len(uncovered_classes),
        "class_threshold": class_threshold,
        "exploration_skipped": True,
    }


def load_dynamic_access_handoff(metrics_repo_path: str | None) -> dict[str, Any] | None:
    """Load a Java-fix coverage handoff from pending metrics."""
    if metrics_repo_path is None:
        return None
    pending_path = os.path.join(metrics_repo_path, PENDING_METRICS_FILENAME)
    if not os.path.isfile(pending_path):
        return None
    metrics = read_pending_metrics(metrics_repo_path)
    handoff = metrics.get(DYNAMIC_ACCESS_HANDOFF_KEY)
    return dict(handoff) if isinstance(handoff, dict) else None


def write_dynamic_access_handoff(metrics_repo_path: str | None, handoff: dict[str, Any]) -> None:
    """Persist handoff publication state in pending metrics."""
    if metrics_repo_path is None:
        raise RuntimeError("Cannot persist dynamic-access handoff without a metrics repository path.")
    pending_path = os.path.join(metrics_repo_path, PENDING_METRICS_FILENAME)
    if not os.path.isfile(pending_path):
        raise RuntimeError(f"Cannot persist dynamic-access handoff; missing {pending_path}.")
    metrics = read_pending_metrics(metrics_repo_path)
    metrics[DYNAMIC_ACCESS_HANDOFF_KEY] = handoff
    write_pending_metrics(metrics_repo_path, metrics)


def record_coverage_follow_up_issue(
        *,
        metrics_repo_path: str | None,
        issue_number: int,
        issue_url: str,
) -> dict[str, Any]:
    """Attach one newly created follow-up issue to the current handoff."""
    handoff = load_dynamic_access_handoff(metrics_repo_path)
    if handoff is None:
        raise RuntimeError("Cannot record a coverage follow-up issue without a dynamic-access handoff.")
    handoff["follow_up_issue_number"] = int(issue_number)
    handoff["follow_up_issue_url"] = issue_url
    write_dynamic_access_handoff(metrics_repo_path, handoff)
    return handoff


def ensure_coverage_follow_up_issue(
        *,
        metrics_repo_path: str | None,
        repair_issue_number: int,
        repo: str,
) -> dict[str, Any] | None:
    """Always create and park a new library-update issue for a fresh handoff.

    A publication retry reuses only the issue number persisted by this same
    handoff attempt; it never searches for or adopts another matching issue.
    """
    handoff = load_dynamic_access_handoff(metrics_repo_path)
    if handoff is None:
        return None
    existing_issue_number = handoff.get("follow_up_issue_number")
    if isinstance(existing_issue_number, int):
        ensure_issue_project_status(
            repo,
            PROJECT_NUMBER,
            existing_issue_number,
            STATUS_IN_PROGRESS,
        )
        return handoff

    coordinate = str(handoff["coordinate"])
    uncovered_class_count = int(handoff["uncovered_class_count"])
    class_threshold = int(handoff["class_threshold"])
    body = (
        f"This issue tracks deferred dynamic-access exploration for `{coordinate}`.\n\n"
        f"Forge repaired the compilation or JVM-runtime failure in issue #{repair_issue_number}, "
        "then skipped exploration because the post-repair report contained "
        f"{uncovered_class_count} uncovered classes, above the configured threshold of "
        f"{class_threshold}.\n\n"
        "This issue remains parked until the repair PR merges into the default branch. "
        "Forge will then process it through the normal chunked `library-update-request` workflow.\n"
    )
    result = gh(
        "issue",
        "create",
        "--repo",
        repo,
        "--title",
        f"Improve coverage for {coordinate}",
        "--body",
        body,
        "--label",
        LIBRARY_UPDATE_LABEL,
    )
    issue_url = result.stdout.strip()
    match = re.search(r"/issues/(\d+)", issue_url)
    if match is None:
        raise RuntimeError(f"Could not parse created coverage issue number from: {issue_url}")
    issue_number = int(match.group(1))
    handoff = record_coverage_follow_up_issue(
        metrics_repo_path=metrics_repo_path,
        issue_number=issue_number,
        issue_url=issue_url,
    )
    ensure_issue_project_status(repo, PROJECT_NUMBER, issue_number, STATUS_IN_PROGRESS)
    return handoff


def format_dynamic_access_handoff_pr_section(
        handoff: dict[str, Any] | None,
        repo: str,
) -> str:
    """Return repair-PR text explaining skipped exploration and its new issue."""
    if not isinstance(handoff, dict):
        return ""
    issue_number = handoff.get("follow_up_issue_number")
    if not isinstance(issue_number, int):
        return ""
    issue_url = handoff.get("follow_up_issue_url")
    if not isinstance(issue_url, str) or not issue_url:
        issue_url = f"https://github.com/{repo}/issues/{issue_number}"
    return (
        "\n### Deferred Dynamic-Access Exploration\n\n"
        "Exploration was skipped after the repair succeeded because the "
        f"dynamic-access report contained **{int(handoff['uncovered_class_count'])} uncovered classes**, "
        f"above the configured threshold of **{int(handoff['class_threshold'])}**.\n\n"
        f"Coverage work will continue in the newly opened [library-update-request #{issue_number}]({issue_url}).\n\n"
        f"Refs: #{issue_number}\n"
        f"{format_follow_up_trailer(issue_number)}\n"
    )
