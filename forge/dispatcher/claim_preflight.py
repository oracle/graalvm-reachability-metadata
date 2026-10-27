# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Claim preflight, cached skip decisions, optimistic claiming, and claim
rollback (§AR-forge-dispatcher-decomposition, §FS-forge-issue-resolution-goal)."""

import random
import sys
import time
from typing import Optional

from utility_scripts.run_location import PHASE_CLAIM, STEP_CLAIM_ISSUE, log_step_progress, pipeline_step
from utility_scripts.stage_logger import log_debug, log_stage

from dispatcher.config import (
    CLAIM_BACKOFF_MAX,
    ISSUE_CLAIM_PREFLIGHT_CHUNK_SIZE,
    LABEL_CHUNKED_DYNAMIC_ACCESS,
    CLAIM_BACKOFF_MIN,
    DEFAULT_TAKE_BLOCKED_ISSUES,
    ISSUE_CLAIM_CACHE_REASON_ASSIGNED,
    ISSUE_CLAIM_CACHE_REASON_BLOCKED,
    ISSUE_CLAIM_CACHE_REASON_CLOSED,
    ISSUE_CLAIM_CACHE_REASON_HUMAN_INTERVENTION,
    ISSUE_CLAIM_CACHE_REASON_IN_PROGRESS,
    ISSUE_CLAIM_CACHE_REASON_MISSING_PROJECT_ITEM,
    ISSUE_CLAIM_CACHE_REASON_NON_TODO,
    ISSUE_CLAIM_CACHE_REASON_NOT_FOR_NATIVE_IMAGE,
    LABEL_HUMAN_INTERVENTION,
    LABEL_NOT_FOR_NATIVE_IMAGE,
    PROJECT_NUMBER,
    REPO,
    STATUS_FIELD_NAME,
    STATUS_IN_PROGRESS,
    STATUS_TODO,
)
from dispatcher.fixture_support import is_fixture_testing_enabled
from dispatcher.github_api import format_github_exception_details, gh_json
from git_scripts.github_cli import GitHubRateLimitExceeded
from dispatcher.interrupts import is_interrupt_exception
from dispatcher.issue_admin import clear_issue_assignees, get_issue_assignees, set_issue_assignee
from dispatcher.issue_cache import (
    invalidate_issue_claim_cache_entry,
    is_issue_claim_cache_enabled,
    read_issue_claim_cache,
    record_issue_claim_cache_observations,
    try_acquire_issue_claim_lock,
)
from dispatcher.issue_queue import (
    cached_skip_blocks_authenticated_user,
    get_issue_claim_payload,
    get_issue_label_names,
    get_issue_payload_assignees,
    is_assigned_only_to_authenticated_user,
    issue_has_label,
    issue_is_resumable,
)
from dispatcher.project_board import get_item_status, get_project_item_state, set_item_status
from dispatcher.records import (
    CachedIssueClaimSkip,
    ClaimedIssue,
    IssueClaimCacheObservation,
    IssueClaimPreflight,
)
from utility_scripts.stage_logger import log_detail


def get_open_blocking_issue_numbers(issue_number: int) -> list[int]:
    """Return the numbers of currently open issues that block the given issue."""
    owner, repo_name = REPO.split("/")
    open_blockers: list[int] = []
    cursor: str | None = None

    while True:
        after_clause = f', after: "{cursor}"' if cursor else ""
        query = f"""
        query {{
          repository(owner: "{owner}", name: "{repo_name}") {{
            issue(number: {issue_number}) {{
              blockedBy(first: 100{after_clause}) {{
                nodes {{
                  number
                  closed
                }}
                pageInfo {{
                  hasNextPage
                  endCursor
                }}
              }}
            }}
          }}
        }}
        """
        result = gh_json("api", "graphql", "-f", f"query={query}")
        issue = (
            result.get("data", {})
            .get("repository", {})
            .get("issue", {})
        )
        blocked_by = issue.get("blockedBy", {}) if isinstance(issue, dict) else {}
        for blocker in blocked_by.get("nodes", []):
            if isinstance(blocker, dict) and not blocker.get("closed", False):
                blocker_number = blocker.get("number")
                if isinstance(blocker_number, int):
                    open_blockers.append(blocker_number)

        page_info = blocked_by.get("pageInfo", {}) if isinstance(blocked_by, dict) else {}
        if not page_info.get("hasNextPage"):
            return open_blockers
        cursor = page_info.get("endCursor")
        if not cursor:
            return open_blockers


def _get_issue_node_project_status(issue_node: dict) -> str | None:
    project_items = issue_node.get("projectItems", {}) if isinstance(issue_node, dict) else {}
    for item in project_items.get("nodes", []):
        if str(item.get("project", {}).get("number")) != str(PROJECT_NUMBER):
            continue
        field_values = item.get("fieldValues", {})
        for field_value in field_values.get("nodes", []):
            field = field_value.get("field", {}) if isinstance(field_value, dict) else {}
            if field.get("name") == STATUS_FIELD_NAME:
                return field_value.get("name")
    return None


def _get_issue_node_project_item_state(issue_node: dict) -> tuple[str | None, str | None]:
    project_items = issue_node.get("projectItems", {}) if isinstance(issue_node, dict) else {}
    for item in project_items.get("nodes", []):
        if not isinstance(item, dict):
            continue
        if str(item.get("project", {}).get("number")) != str(PROJECT_NUMBER):
            continue
        return item.get("id"), _get_issue_node_project_status(issue_node)
    return None, None


def _get_issue_node_assignees(issue_node: dict) -> list[str]:
    assignees = issue_node.get("assignees", {}) if isinstance(issue_node, dict) else {}
    return [
        assignee["login"]
        for assignee in assignees.get("nodes", [])
        if isinstance(assignee, dict) and assignee.get("login")
    ]


def _connection_has_next_page(connection: dict) -> bool:
    page_info = connection.get("pageInfo", {}) if isinstance(connection, dict) else {}
    return bool(page_info.get("hasNextPage"))


def _open_issue_numbers_from_connection(connection: dict) -> tuple[int, ...]:
    if not isinstance(connection, dict):
        return ()
    open_numbers: list[int] = []
    for node in connection.get("nodes", []):
        if not isinstance(node, dict) or node.get("closed", False):
            continue
        issue_number = node.get("number")
        if isinstance(issue_number, int):
            open_numbers.append(issue_number)
    return tuple(open_numbers)


def _project_item_status_preflight_fields() -> str:
    return """
          projectItems(first: 5) {
            nodes {
              id
              project {
                number
              }
              fieldValues(first: 20) {
                nodes {
                  ... on ProjectV2ItemFieldSingleSelectValue {
                    name
                    field { ... on ProjectV2FieldCommon { name } }
                  }
                }
              }
            }
          }
    """


def _assignees_preflight_fields() -> str:
    return """
          assignees(first: 10) {
            nodes {
              login
            }
            pageInfo {
              hasNextPage
              endCursor
            }
          }
    """


def _extract_issue_claim_preflight(issue_number: int, issue_node: dict | None) -> IssueClaimPreflight:
    if not isinstance(issue_node, dict):
        return IssueClaimPreflight(issue_number, None, None, (), (), False)

    item_id, project_status = _get_issue_node_project_item_state(issue_node)
    assignees = tuple(_get_issue_node_assignees(issue_node))
    blocked_by = issue_node.get("blockedBy", {})
    open_blockers = _open_issue_numbers_from_connection(blocked_by)
    complete = (
        not _connection_has_next_page(issue_node.get("assignees", {}))
        and not _connection_has_next_page(blocked_by)
    )

    return IssueClaimPreflight(
        issue_number=issue_number,
        item_id=item_id,
        project_status=project_status,
        assignees=assignees,
        open_blockers=open_blockers,
        complete=complete,
    )


def get_issue_claim_cache_observation_from_payload(
        issue: dict,
        authenticated_user: str | None = None,
) -> IssueClaimCacheObservation | None:
    """Return a cache observation for locally visible negative issue state."""
    issue_number = issue.get("number")
    if not isinstance(issue_number, int):
        return None
    if issue_has_label(issue, LABEL_HUMAN_INTERVENTION) and not issue_is_resumable(issue):
        return IssueClaimCacheObservation(
            issue_number=issue_number,
            reason=ISSUE_CLAIM_CACHE_REASON_HUMAN_INTERVENTION,
        )
    if issue_has_label(issue, LABEL_NOT_FOR_NATIVE_IMAGE):
        return IssueClaimCacheObservation(
            issue_number=issue_number,
            reason=ISSUE_CLAIM_CACHE_REASON_NOT_FOR_NATIVE_IMAGE,
        )
    payload_assignees = get_issue_payload_assignees(issue)
    if payload_assignees and not is_assigned_only_to_authenticated_user(payload_assignees, authenticated_user):
        return IssueClaimCacheObservation(
            issue_number=issue_number,
            reason=ISSUE_CLAIM_CACHE_REASON_ASSIGNED,
            assignees=tuple(payload_assignees),
        )
    return None


def refresh_issue_payload_for_claim(
        issue: dict,
        required_label: str | None = None,
        authenticated_user: str | None = None,
) -> bool:
    """Refresh mutable issue state and return whether it remains claimable.

    The payload is re-read against live GitHub state at claim time rather than
    trusted from the scan (§FS-forge-run-requirements.2).
    """
    issue_number = issue.get("number")
    if not isinstance(issue_number, int):
        return False

    fresh_issue = get_issue_claim_payload(issue_number)
    issue.clear()
    issue.update(fresh_issue)

    state = str(issue.get("state", "")).upper()
    if state and state != "OPEN":
        record_issue_claim_cache_observations([
            IssueClaimCacheObservation(
                issue_number=issue_number,
                reason=ISSUE_CLAIM_CACHE_REASON_CLOSED,
            )
        ])
        return False

    if required_label is not None and not issue_has_label(issue, required_label):
        log_stage(
            "issue-claim",
            f"Skipping issue #{issue_number}: it no longer has label '{required_label}'",
        )
        return False

    observation = get_issue_claim_cache_observation_from_payload(issue, authenticated_user)
    if observation is not None:
        record_issue_claim_cache_observations([observation])
        return False

    return True


def get_issue_claim_cache_observation_from_preflight(
        preflight: IssueClaimPreflight,
        authenticated_user: str | None = None,
) -> IssueClaimCacheObservation | None:
    """Return a cache observation for negative GraphQL preflight state."""
    if not preflight.complete:
        return None
    if preflight.assignees and not is_assigned_only_to_authenticated_user(preflight.assignees, authenticated_user):
        return IssueClaimCacheObservation(
            issue_number=preflight.issue_number,
            reason=ISSUE_CLAIM_CACHE_REASON_ASSIGNED,
            assignees=preflight.assignees,
        )
    if preflight.open_blockers:
        return IssueClaimCacheObservation(
            issue_number=preflight.issue_number,
            reason=ISSUE_CLAIM_CACHE_REASON_BLOCKED,
            open_blockers=preflight.open_blockers,
        )
    if not preflight.item_id:
        return IssueClaimCacheObservation(
            issue_number=preflight.issue_number,
            reason=ISSUE_CLAIM_CACHE_REASON_MISSING_PROJECT_ITEM,
        )
    if preflight.project_status != STATUS_TODO:
        reason = (
            ISSUE_CLAIM_CACHE_REASON_IN_PROGRESS
            if preflight.project_status == STATUS_IN_PROGRESS
            else ISSUE_CLAIM_CACHE_REASON_NON_TODO
        )
        return IssueClaimCacheObservation(
            issue_number=preflight.issue_number,
            reason=reason,
            project_status=preflight.project_status,
        )
    return None


def format_cached_issue_claim_skip(cached_skip: CachedIssueClaimSkip) -> str:
    """Return a readable reason for a cached issue-claim skip."""
    if cached_skip.reason == ISSUE_CLAIM_CACHE_REASON_ASSIGNED:
        return f"recently cached as assigned to {list(cached_skip.assignees)}"
    if cached_skip.reason == ISSUE_CLAIM_CACHE_REASON_HUMAN_INTERVENTION:
        return f"recently cached with label '{LABEL_HUMAN_INTERVENTION}'"
    if cached_skip.reason == ISSUE_CLAIM_CACHE_REASON_NOT_FOR_NATIVE_IMAGE:
        return f"recently cached with label '{LABEL_NOT_FOR_NATIVE_IMAGE}'"
    if cached_skip.reason == ISSUE_CLAIM_CACHE_REASON_BLOCKED:
        blockers_text = ", ".join(f"#{blocker}" for blocker in cached_skip.open_blockers)
        return f"recently cached as blocked by open issue(s) {blockers_text}"
    if cached_skip.reason == ISSUE_CLAIM_CACHE_REASON_CLOSED:
        return "recently cached as closed"
    if cached_skip.reason == ISSUE_CLAIM_CACHE_REASON_MISSING_PROJECT_ITEM:
        return f"recently cached as missing project {PROJECT_NUMBER} item"
    if cached_skip.reason in {ISSUE_CLAIM_CACHE_REASON_NON_TODO, ISSUE_CLAIM_CACHE_REASON_IN_PROGRESS}:
        return f"recently cached as not Todo (it was '{cached_skip.project_status}')"
    return f"recently cached as {cached_skip.reason}"


def get_cached_issue_claim_skips(
        issues: list[dict],
        authenticated_user: str | None = None,
        take_blocked_issues: bool = DEFAULT_TAKE_BLOCKED_ISSUES,
) -> dict[int, CachedIssueClaimSkip]:
    """Return fresh cached skips for the given issue payloads."""
    cache = read_issue_claim_cache()
    if not cache:
        return {}
    issue_numbers = {
        issue["number"]
        for issue in issues
        if isinstance(issue, dict) and isinstance(issue.get("number"), int)
    }
    return {
        issue_number: cached_skip
        for issue_number, cached_skip in cache.items()
        if issue_number in issue_numbers
        and cached_skip_blocks_authenticated_user(cached_skip, authenticated_user, take_blocked_issues)
    }


def get_issue_claim_preflights(
        issue_numbers: list[int],
        chunk_size: int = ISSUE_CLAIM_PREFLIGHT_CHUNK_SIZE,
) -> dict[int, IssueClaimPreflight]:
    """Fetch claim preflight state for issue candidates with chunked GraphQL calls."""
    owner, repo_name = REPO.split("/")
    preflights: dict[int, IssueClaimPreflight] = {}
    issue_numbers = list(dict.fromkeys(issue_numbers))

    for index in range(0, len(issue_numbers), chunk_size):
        batch = issue_numbers[index:index + chunk_size]
        issue_fields = "\n".join(
            f"""
        issue_{issue_number}: issue(number: {issue_number}) {{
          number
{_assignees_preflight_fields()}
{_project_item_status_preflight_fields()}
          blockedBy(first: 100) {{
            nodes {{
              number
              closed
            }}
            pageInfo {{
              hasNextPage
              endCursor
            }}
          }}
        }}
            """
            for issue_number in batch
        )
        query = f"""
        query {{
          repository(owner: "{owner}", name: "{repo_name}") {{
{issue_fields}
          }}
        }}
        """
        result = gh_json("api", "graphql", "-f", f"query={query}", quiet=True)
        repository = (
            result.get("data", {})
            .get("repository", {})
        ) or {}
        for issue_number in batch:
            preflights[issue_number] = _extract_issue_claim_preflight(
                issue_number,
                repository.get(f"issue_{issue_number}"),
            )

    return preflights


def issue_needs_claim_preflight(issue: dict, authenticated_user: str | None = None) -> bool:
    """Return True when an issue needs GraphQL preflight before claim attempts."""
    if issue_has_label(issue, LABEL_HUMAN_INTERVENTION) and not issue_is_resumable(issue):
        return False
    if issue_has_label(issue, LABEL_NOT_FOR_NATIVE_IMAGE):
        return False
    payload_assignees = get_issue_payload_assignees(issue)
    if payload_assignees and not is_assigned_only_to_authenticated_user(payload_assignees, authenticated_user):
        return False
    return True


def get_issue_claim_preflights_or_empty(
        issues: list[dict],
        authenticated_user: str | None = None,
) -> dict[int, IssueClaimPreflight]:
    issue_numbers = [
        issue["number"]
        for issue in issues
        if isinstance(issue, dict) and isinstance(issue.get("number"), int)
        and issue_needs_claim_preflight(issue, authenticated_user)
    ]
    if not issue_numbers:
        return {}
    try:
        return get_issue_claim_preflights(issue_numbers)
    except GitHubRateLimitExceeded:
        raise
    except Exception as exc:
        print(
            "ERROR: Failed to fetch batched claim preflight state; "
            f"falling back to per-issue checks: {format_github_exception_details(exc)}",
            file=sys.stderr,
        )
        return {}


def should_skip_issue_from_preflight(
        issue: dict,
        preflight: IssueClaimPreflight | None,
        cached_skip: CachedIssueClaimSkip | None = None,
        authenticated_user: str | None = None,
        take_blocked_issues: bool = DEFAULT_TAKE_BLOCKED_ISSUES,
) -> bool:
    number = issue["number"]

    cached_chunked_issue_in_progress = (
        cached_skip is not None
        and cached_skip.reason == ISSUE_CLAIM_CACHE_REASON_IN_PROGRESS
        and issue_has_label(issue, LABEL_CHUNKED_DYNAMIC_ACCESS)
    )
    cached_human_intervention_now_resumable = (
        cached_skip is not None
        and cached_skip.reason == ISSUE_CLAIM_CACHE_REASON_HUMAN_INTERVENTION
        and issue_is_resumable(issue)
    )
    if (
            cached_skip is not None
            and not cached_chunked_issue_in_progress
            and not cached_human_intervention_now_resumable
            and cached_skip_blocks_authenticated_user(cached_skip, authenticated_user, take_blocked_issues)
    ):
        return True

    if preflight is None or not preflight.complete:
        return False

    if preflight.assignees and not is_assigned_only_to_authenticated_user(preflight.assignees, authenticated_user):
        return True

    if preflight.open_blockers and not take_blocked_issues:
        return True

    if not preflight.item_id:
        print(
            f"ERROR: Issue #{number} is not linked to project {PROJECT_NUMBER}",
            file=sys.stderr,
        )
        return True

    if preflight.project_status != STATUS_TODO:
        return True

    return False


def resolve_next_issue_claim_candidate_batch(
        unresolved_candidates: list[tuple[dict, CachedIssueClaimSkip | None]],
) -> list[tuple[dict, IssueClaimPreflight | None, CachedIssueClaimSkip | None]]:
    """Resolve the next candidate batch through cache/local state only."""
    batch_entries: list[tuple[dict, CachedIssueClaimSkip | None]] = []

    while unresolved_candidates:
        issue, cached_skip = unresolved_candidates.pop(0)
        batch_entries.append((issue, cached_skip))

    return [
        (
            issue,
            None,
            cached_skip,
        )
        for issue, cached_skip in batch_entries
    ]
