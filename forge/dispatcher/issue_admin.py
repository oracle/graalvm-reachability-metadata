# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Issue and pull-request administration primitives: assignees, labels,
comments, and closing (§AR-forge-dispatcher-decomposition,
§FS-forge-issue-resolution-goal)."""

import sys
from urllib.parse import quote

from utility_scripts.stage_logger import log_debug, log_stage

from dispatcher.config import (
    CHUNKED_DYNAMIC_ACCESS_LABEL_COLOR,
    CHUNKED_DYNAMIC_ACCESS_LABEL_DESCRIPTION,
    FIXTURE_AUTHENTICATED_USER,
    FORGE_MERGE_FOLLOW_UP_LABEL_COLOR,
    FORGE_MERGE_FOLLOW_UP_LABEL_DESCRIPTION,
    HUMAN_INTERVENTION_LABEL_COLOR,
    HUMAN_INTERVENTION_LABEL_DESCRIPTION,
    ISSUE_CLAIM_CACHE_REASON_CLOSED,
    LABEL_CHUNKED_DYNAMIC_ACCESS,
    LABEL_FORGE_MERGE_FOLLOW_UP,
    LABEL_LIBRARY_UNSUPPORTED_VERSION,
    LABEL_NOT_FOR_NATIVE_IMAGE,
    LABEL_PRIORITY,
    LABEL_RESUMABLE,
    LIBRARY_UNSUPPORTED_VERSION_LABEL_COLOR,
    LIBRARY_UNSUPPORTED_VERSION_LABEL_DESCRIPTION,
    NOT_FOR_NATIVE_IMAGE_LABEL_COLOR,
    NOT_FOR_NATIVE_IMAGE_LABEL_DESCRIPTION,
    PRIORITY_LABEL_COLOR,
    PRIORITY_LABEL_DESCRIPTION,
    REPO,
    RESUMABLE_LABEL_COLOR,
    RESUMABLE_LABEL_DESCRIPTION,
)
from dispatcher.issue_cache import record_issue_claim_cache_observations
from dispatcher.records import IssueClaimCacheObservation

from dispatcher.github_api import gh, gh_json
from dispatcher.fixture_support import is_fixture_testing_enabled, require_fixture_github_state


ensured_issue_labels: set[str] = set()

def set_issue_assignee(issue_number: int, username: str):
    """Set a single assignee to an issue."""
    gh(
        "api",
        "--method",
        "PATCH",
        f"/repos/{REPO}/issues/{issue_number}",
        "-f",
        f"assignees[]={username}",
    )


def clear_issue_assignees(issue_number: int):
    """Remove all assignees from an issue."""
    gh(
        "api",
        "--method",
        "PATCH",
        f"/repos/{REPO}/issues/{issue_number}",
        "--input",
        "-",
        input_text='{"assignees":[]}',
    )



def get_issue_assignees(issue_number: int) -> list[str]:
    """Return the list of assignee logins for an issue."""
    data = gh_json(
        "issue",
        "view",
        str(issue_number),
        "--repo",
        REPO,
        "--json",
        "assignees",
    )
    return [a["login"] for a in data.get("assignees", [])]

def ensure_repo_label_exists(label_name: str, color: str, description: str) -> None:
    """Ensure a repository label exists before applying it to an issue or pull request."""
    if label_name in ensured_issue_labels:
        return
    if is_fixture_testing_enabled():
        ensured_issue_labels.add(label_name)
        return

    encoded_label = quote(label_name, safe="")
    label_lookup = gh("api", f"/repos/{REPO}/labels/{encoded_label}", check=False)
    if label_lookup.returncode == 0:
        ensured_issue_labels.add(label_name)
        return

    create_result = gh(
        "api",
        "--method",
        "POST",
        f"/repos/{REPO}/labels",
        "-f",
        f"name={label_name}",
        "-f",
        f"color={color}",
        "-f",
        f"description={description}",
        check=False,
    )
    if create_result.returncode != 0:
        error_output = "\n".join(
            value for value in [create_result.stdout.strip(), create_result.stderr.strip()] if value
        )
        print(
            f"ERROR: Failed to ensure label '{label_name}'.\n{error_output}",
            file=sys.stderr,
        )
        create_result.check_returncode()

    ensured_issue_labels.add(label_name)


def post_issue_comment(issue_number: int, body: str) -> None:
    """Post a comment to a GitHub issue."""
    if is_fixture_testing_enabled():
        require_fixture_github_state().post_issue_comment(
            issue_number,
            body,
            FIXTURE_AUTHENTICATED_USER,
        )
        return
    gh(
        "issue",
        "comment",
        str(issue_number),
        "--repo",
        REPO,
        "--body",
        body,
    )


def add_issue_label(issue_number: int, label_name: str) -> None:
    """Add a label to a GitHub issue, creating the label if necessary."""
    label_color = HUMAN_INTERVENTION_LABEL_COLOR
    label_description = HUMAN_INTERVENTION_LABEL_DESCRIPTION
    if label_name == LABEL_NOT_FOR_NATIVE_IMAGE:
        label_color = NOT_FOR_NATIVE_IMAGE_LABEL_COLOR
        label_description = NOT_FOR_NATIVE_IMAGE_LABEL_DESCRIPTION
    elif label_name == LABEL_PRIORITY:
        label_color = PRIORITY_LABEL_COLOR
        label_description = PRIORITY_LABEL_DESCRIPTION
    elif label_name == LABEL_CHUNKED_DYNAMIC_ACCESS:
        label_color = CHUNKED_DYNAMIC_ACCESS_LABEL_COLOR
        label_description = CHUNKED_DYNAMIC_ACCESS_LABEL_DESCRIPTION
    elif label_name == LABEL_RESUMABLE:
        label_color = RESUMABLE_LABEL_COLOR
        label_description = RESUMABLE_LABEL_DESCRIPTION
    elif label_name == LABEL_LIBRARY_UNSUPPORTED_VERSION:
        label_color = LIBRARY_UNSUPPORTED_VERSION_LABEL_COLOR
        label_description = LIBRARY_UNSUPPORTED_VERSION_LABEL_DESCRIPTION
    ensure_repo_label_exists(
        label_name,
        label_color,
        label_description,
    )
    if is_fixture_testing_enabled():
        require_fixture_github_state().add_issue_label(issue_number, label_name)
        return
    gh(
        "issue",
        "edit",
        str(issue_number),
        "--repo",
        REPO,
        "--add-label",
        label_name,
    )


def remove_issue_label(issue_number: int, label_name: str) -> None:
    """Remove a label from a GitHub issue if it is present."""
    if is_fixture_testing_enabled():
        require_fixture_github_state().remove_issue_label(issue_number, label_name)
        return
    result = gh(
        "issue",
        "edit",
        str(issue_number),
        "--repo",
        REPO,
        "--remove-label",
        label_name,
        check=False,
    )
    if result.returncode != 0 and "not found" not in (result.stderr or "").lower():
        result.check_returncode()


def add_pull_request_label(pr_number: int, label_name: str) -> None:
    """Add a label to a GitHub pull request, creating the label if necessary."""
    label_color = HUMAN_INTERVENTION_LABEL_COLOR
    label_description = HUMAN_INTERVENTION_LABEL_DESCRIPTION
    if label_name == LABEL_FORGE_MERGE_FOLLOW_UP:
        label_color = FORGE_MERGE_FOLLOW_UP_LABEL_COLOR
        label_description = FORGE_MERGE_FOLLOW_UP_LABEL_DESCRIPTION
    ensure_repo_label_exists(
        label_name,
        label_color,
        label_description,
    )
    gh(
        "api",
        "--method",
        "POST",
        f"/repos/{REPO}/issues/{pr_number}/labels",
        "-f",
        f"labels[]={label_name}",
    )


def remove_pull_request_label(pr_number: int, label_name: str) -> None:
    """Remove a label from a pull request when present."""
    encoded_label = quote(label_name, safe="")
    result = gh(
        "api",
        "--method", "DELETE",
        f"/repos/{REPO}/issues/{pr_number}/labels/{encoded_label}",
        check=False,
    )
    if result.returncode != 0:
        error_text = "\n".join((result.stderr or "", result.stdout or "")).lower()
        if "not found" not in error_text and "404" not in error_text:
            result.check_returncode()

def get_issue_comments(issue_number: int) -> list[dict]:
    """Fetch issue comments used for continuation branch discovery."""
    if is_fixture_testing_enabled():
        return require_fixture_github_state().get_issue_comments(issue_number)
    data = gh_json(
        "issue",
        "view",
        str(issue_number),
        "--repo",
        REPO,
        "--json",
        "comments",
    )
    comments = data.get("comments")
    return comments if isinstance(comments, list) else []


def comment_author_login(comment: dict) -> str | None:
    """Return the GitHub login that posted an issue comment."""
    author = comment.get("author")
    if isinstance(author, dict):
        login = author.get("login")
        return login if isinstance(login, str) and login else None
    if isinstance(author, str) and author:
        return author
    return None

def close_issue(issue_number: int, reason: str) -> None:
    """Close an issue Forge will never process and stop scanning it."""
    log_stage("issue-close", f"Closing issue #{issue_number}: {reason}")
    if is_fixture_testing_enabled():
        require_fixture_github_state().close_issue(issue_number)
    else:
        gh("issue", "close", str(issue_number), "--repo", REPO, "--reason", "not planned")
    record_issue_claim_cache_observations([
        IssueClaimCacheObservation(
            issue_number=issue_number,
            reason=ISSUE_CLAIM_CACHE_REASON_CLOSED,
        )
    ])
