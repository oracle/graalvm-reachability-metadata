# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Fixed-version coverage follow-up for Java repair workflows."""

import re
from collections.abc import Callable

from git_scripts.common_git import gh, gh_json
from utility_scripts.dynamic_access_report import DynamicAccessCoverageReport
from utility_scripts.library_update_alias_split import (
    PROJECT_NUMBER,
    STATUS_IN_PROGRESS,
    ensure_issue_project_status,
    format_follow_up_trailer,
)


LIBRARY_UPDATE_LABEL = "library-update-request"


def uncovered_dynamic_access_class_count(
        report: DynamicAccessCoverageReport | None,
) -> int:
    """Return the number of classes with uncovered dynamic-access calls.

    An unusable report counts as zero so exploration still runs and regenerates it.
    """
    if report is None:
        return 0
    return sum(
        1
        for class_coverage in report.classes
        if class_coverage.uncovered_calls > 0
    )


def _repair_issue_reference(repair_issue_number: int) -> str:
    """Return the phrase that ties a follow-up issue back to its repair issue."""
    return f"while resolving #{repair_issue_number}"


def format_coverage_follow_up_issue_body(
        *,
        coordinate: str,
        repair_issue_number: int,
        uncovered_class_count: int,
        class_threshold: int,
) -> str:
    """Return the brief fixed-version coverage issue description."""
    return (
        f"This issue was opened by Forge {_repair_issue_reference(repair_issue_number)} because "
        f"dynamic-access generation found {uncovered_class_count} classes to cover for "
        f"`{coordinate}`, above the configured threshold of {class_threshold}.\n"
    )


def _find_coverage_follow_up_issue(repo: str, coordinate: str, repair_issue_number: int) -> int | None:
    """Recover the follow-up issue this repair already opened after an interrupted save."""
    issues = gh_json(
        "issue",
        "list",
        "--repo",
        repo,
        "--state",
        "all",
        "--label",
        LIBRARY_UPDATE_LABEL,
        "--json",
        "number,body",
        "--limit",
        "100",
    )
    if not isinstance(issues, list):
        return None
    reference = f"{_repair_issue_reference(repair_issue_number)} because"
    coordinate_reference = f"`{coordinate}`"
    for issue in issues:
        if not isinstance(issue, dict) or not isinstance(issue.get("number"), int):
            continue
        body = issue.get("body") or ""
        if reference in body and coordinate_reference in body:
            return int(issue["number"])
    return None


def ensure_coverage_follow_up_issue(
        *,
        coordinate: str,
        repair_issue_number: int,
        uncovered_class_count: int,
        class_threshold: int,
        repo: str,
        existing_issue_number: int | None = None,
        record_issue_number: Callable[[int], None] | None = None,
) -> int:
    """Open and park the fixed-version library-update issue for this repair, once.

    A retried publication reuses the issue this repair already opened rather than
    filing a duplicate. §WF-java-fail-fix-workflow.3
    """
    if existing_issue_number is None:
        existing_issue_number = _find_coverage_follow_up_issue(
            repo,
            coordinate,
            repair_issue_number,
        )
    if existing_issue_number is not None:
        if record_issue_number is not None:
            record_issue_number(existing_issue_number)
        ensure_issue_project_status(repo, PROJECT_NUMBER, existing_issue_number, STATUS_IN_PROGRESS)
        return existing_issue_number

    body = format_coverage_follow_up_issue_body(
        coordinate=coordinate,
        repair_issue_number=repair_issue_number,
        uncovered_class_count=uncovered_class_count,
        class_threshold=class_threshold,
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
    if record_issue_number is not None:
        record_issue_number(issue_number)
    ensure_issue_project_status(repo, PROJECT_NUMBER, issue_number, STATUS_IN_PROGRESS)
    return issue_number


def format_generation_statistics_blocks(metrics: dict, coverage_deferred: bool) -> tuple[str, str]:
    """Return the metadata-entry and coverage bullet blocks of a Java-fix PR summary.

    Both blocks are empty when exploration was deferred: that run only repaired the
    build, so entry counts and coverage percentages would describe work it never
    attempted and invite reading a deliberate deferral as a regression.
    §WF-java-fail-fix-workflow.3
    """
    if coverage_deferred:
        return "", ""

    metadata_entry_lines = f"- Metadata entries: {int(metrics.get('metadata_entries', 0))}\n"
    test_only_metadata_entries = int(metrics.get("test_only_metadata_entries", 0) or 0)
    if test_only_metadata_entries > 0:
        metadata_entry_lines += f"- Test-only metadata entries: {test_only_metadata_entries}\n"

    coverage_lines = f"- Library coverage percentage: {metrics.get('code_coverage_percent', 0)}\n"
    coverage_lines += (
        "- Previous library version metadata entries: "
        f"{int(metrics.get('previous_library_metadata_entries', 0))}\n"
    )
    previous_test_only_entries = int(metrics.get("previous_library_test_only_metadata_entries", 0) or 0)
    if previous_test_only_entries > 0:
        coverage_lines += (
            f"- Previous library version test-only metadata entries: {previous_test_only_entries}\n"
        )
    coverage_lines += (
        "- Previous library version coverage percentage: "
        f"{metrics.get('previous_library_coverage_percent', 0)}\n"
    )
    return metadata_entry_lines, coverage_lines


def format_coverage_follow_up_pr_section(
        *,
        issue_number: int | None,
        uncovered_class_count: int | None,
        class_threshold: int | None,
        repo: str,
) -> str:
    """Return repair-PR text linking the fixed-version coverage issue."""
    if issue_number is None:
        return ""
    if uncovered_class_count is None or class_threshold is None:
        raise ValueError("Coverage follow-up class count and threshold are required")
    issue_url = f"https://github.com/{repo}/issues/{issue_number}"
    return (
        "### Deferred Dynamic-Access Exploration\n\n"
        "Exploration was skipped after the repair succeeded because the "
        f"dynamic-access report contained **{uncovered_class_count} uncovered "
        f"classes**, above the configured threshold of **{class_threshold}**.\n\n"
        f"Coverage work will continue in the newly opened "
        f"[library-update-request #{issue_number}]({issue_url}).\n\n"
        f"Refs: #{issue_number}\n"
        f"{format_follow_up_trailer(issue_number)}\n\n"
    )
