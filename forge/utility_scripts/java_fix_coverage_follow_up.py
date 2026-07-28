# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Fixed-version coverage follow-up for Java repair workflows."""

import re

from git_scripts.common_git import gh
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
    """Return the number of classes with uncovered dynamic-access calls."""
    if report is None:
        return 0
    return sum(
        1
        for class_coverage in report.classes
        if class_coverage.uncovered_calls > 0
    )


def create_coverage_follow_up_issue(
        *,
        coordinate: str,
        repair_issue_number: int,
        repo: str,
) -> int:
    """Create and park a new fixed-version library-update issue."""
    body = (
        f"Forge repaired the compilation or JVM-runtime failure for `{coordinate}` "
        f"in issue #{repair_issue_number}, but skipped the oversized dynamic-access "
        "exploration phase.\n\n"
        "This issue remains parked until the repair PR merges into the default branch. "
        "Forge will then process it through the normal `library-update-request` workflow, "
        "which will select chunked execution when required.\n"
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
    ensure_issue_project_status(repo, PROJECT_NUMBER, issue_number, STATUS_IN_PROGRESS)
    return issue_number


def format_coverage_follow_up_pr_section(
        issue_number: int | None,
        repo: str,
) -> str:
    """Return repair-PR text linking the fixed-version coverage issue."""
    if issue_number is None:
        return ""
    issue_url = f"https://github.com/{repo}/issues/{issue_number}"
    return (
        "\n### Deferred Dynamic-Access Exploration\n\n"
        "Exploration was skipped after the repair succeeded because the fixed "
        "library has more uncovered classes than the configured threshold.\n\n"
        f"Coverage work will continue in the newly opened "
        f"[library-update-request #{issue_number}]({issue_url}).\n\n"
        f"Refs: #{issue_number}\n"
        f"{format_follow_up_trailer(issue_number)}\n"
    )
