# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Create idempotent follow-up issues for unsupported resolved metadata owners.

§FS-forge-scope §AR-forge-driver-finalization
"""

from __future__ import annotations

from dataclasses import dataclass
import json
import os

from git_scripts.common_git import gh, gh_json, resolve_github_repo_slug


REASONS_TO_LABELS = {
    "owner-library-unsupported": "library-new-request",
    "owner-version-unsupported": "library-update-request",
}


@dataclass(frozen=True)
class ForeignMetadataOwnerFailure:
    """The complete two-field routeForeignMetadata failure contract."""

    reason: str
    coordinate: str


def failure_report_path(repo_path: str, source_coordinate: str) -> str:
    """Return the ignored build path for one routing failure report."""
    file_name = source_coordinate.replace(":", "_") + ".json"
    return os.path.join(repo_path, "build", "reports", "route-foreign-metadata", file_name)


def load_foreign_metadata_owner_failure(
        repo_path: str,
        source_coordinate: str,
) -> ForeignMetadataOwnerFailure | None:
    """Load a valid unsupported-owner report, returning None for no report."""
    report_path = failure_report_path(repo_path, source_coordinate)
    if not os.path.isfile(report_path):
        return None
    try:
        with open(report_path, "r", encoding="utf-8") as report_file:
            report = json.load(report_file)
    except (OSError, json.JSONDecodeError):
        return None
    if not isinstance(report, dict) or set(report) != {"reason", "coordinate"}:
        return None
    reason = report.get("reason")
    coordinate = report.get("coordinate")
    if reason not in REASONS_TO_LABELS or not isinstance(coordinate, str) or not coordinate.strip():
        return None
    return ForeignMetadataOwnerFailure(reason=reason, coordinate=coordinate)


def ensure_foreign_metadata_owner_issue(
        repo_path: str,
        source_coordinate: str,
        failure: ForeignMetadataOwnerFailure,
) -> str:
    """Create or reuse the open queue issue for an unsupported owner coordinate."""
    repo = resolve_github_repo_slug(repo_path=repo_path)
    label = REASONS_TO_LABELS[failure.reason]
    marker = _issue_marker(failure)
    issues = gh_json(
        "issue",
        "list",
        "--repo",
        repo,
        "--state",
        "open",
        "--label",
        label,
        "--search",
        f'"{failure.coordinate}" in:title,body',
        "--json",
        "number,title,body,url",
        "--limit",
        "50",
    )
    if isinstance(issues, list):
        for issue in issues:
            if not isinstance(issue, dict):
                continue
            issue_text = f"{issue.get('title') or ''}\n{issue.get('body') or ''}"
            if marker in issue_text or failure.coordinate in issue_text:
                return str(issue["url"])

    title = (
        f"Support for {failure.coordinate}"
        if label == "library-new-request"
        else f"Update existing library: {failure.coordinate}"
    )
    body = (
        f"Forge resolved metadata generated while testing `{source_coordinate}` to the runtime dependency "
        f"`{failure.coordinate}`, which is not yet supported for this routing operation.\n\n"
        f"{marker}\n"
    )
    result = gh(
        "issue",
        "create",
        "--repo",
        repo,
        "--title",
        title,
        "--body",
        body,
        "--label",
        label,
    )
    issue_url = result.stdout.strip()
    if not issue_url:
        raise RuntimeError(f"GitHub did not return a URL for the {failure.coordinate} follow-up issue")
    return issue_url


def _issue_marker(failure: ForeignMetadataOwnerFailure) -> str:
    return f"<!-- forge-foreign-metadata-owner:{failure.reason}:{failure.coordinate} -->"
