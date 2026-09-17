# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Issue queue search, priority tiers, and queue-side payload helpers
(§AR-forge-dispatcher-decomposition, §FS-forge-issue-resolution-goal)."""

import os
import sys
import time
from dataclasses import dataclass
from typing import Optional

from utility_scripts.stage_logger import log_debug

from dispatcher.config import (
    DEFAULT_TAKE_BLOCKED_ISSUES,
    GITHUB_API_MAX_PAGE_SIZE,
    GITHUB_SEARCH_MAX_RESULTS,
    ISSUE_SEARCH_ORDER,
    ISSUE_SEARCH_SORT,
    LABEL_HIGH_PRIORITY,
    LABEL_NOT_FOR_NATIVE_IMAGE,
    LABEL_PRIORITY,
    LABEL_RESUMABLE,
    ISSUE_CLAIM_CACHE_REASON_ASSIGNED,
    ISSUE_CLAIM_CACHE_REASON_BLOCKED,
    NON_USER_REQUESTED_ISSUE_AUTHORS,
    PIPELINE_LABELS,
    PRIORITY_HIGH,
    PRIORITY_NORMAL,
    REPO,
)
from dispatcher.env_config import get_env_zero_one_bool
from dispatcher.fixture_support import is_fixture_testing_enabled, require_fixture_github_state
from dispatcher.github_api import gh, gh_json
from dispatcher.issue_cache import (
    LocalIssueSearchCacheWriterLock,
    get_cached_issue_search_count,
    get_cached_issue_search_page,
    read_issue_search_cache_payload,
    read_issue_search_cache_payload_or_empty,
    write_issue_search_cache_payload,
    set_cached_issue_search_count,
    set_cached_issue_search_page,
    build_issue_search_cache_key,
    get_issue_search_cache_ttl_seconds,
    is_issue_search_cache_enabled,
)
from dispatcher.records import CachedIssueClaimSkip


from dispatcher.records import ClaimedIssue

def get_issue_by_number(issue_number: int) -> tuple[dict, str]:
    """Fetch a single issue by number and determine its pipeline label."""
    if is_fixture_testing_enabled():
        data = require_fixture_github_state().get_issue_by_number(issue_number)
    else:
        data = gh_json(
            "issue", "view",
            str(issue_number),
            "--repo", REPO,
            "--json", "number,title,url,labels,assignees",
        )
    for label in data.get("labels", []):
        label_name = label.get("name") if isinstance(label, dict) else None
        if label_name in PIPELINE_LABELS:
            return data, label_name
    found_labels = [l.get("name", "?") for l in data.get("labels", []) if isinstance(l, dict)]
    print(
        f"ERROR: Issue #{issue_number} has no recognized pipeline label. "
        f"Found labels: {found_labels}",
        file=sys.stderr,
    )
    sys.exit(1)


def get_issue_claim_payload(issue_number: int) -> dict:
    """Fetch mutable issue state immediately before claim decisions."""
    return gh_json(
        "issue", "view",
        str(issue_number),
        "--repo", REPO,
        "--json", "number,title,url,state,labels,assignees",
    )


def get_issue_body(issue_number: int) -> str:
    """Fetch an issue body only for workflows that explicitly need reporter context."""
    if is_fixture_testing_enabled():
        return require_fixture_github_state().get_issue_body(issue_number)
    data = gh_json(
        "issue", "view",
        str(issue_number),
        "--repo", REPO,
        "--json", "body",
    )
    body = data.get("body")
    return body if isinstance(body, str) else ""


def build_issue_search_query(
        label: str,
        extra_labels: list[str] | None = None,
        excluded_labels: list[str] | None = None,
) -> str:
    """Build a GitHub issue search query for open issues with all requested labels."""
    label_terms = " ".join(
        f'label:"{label_name}"'
        for label_name in [label, *(extra_labels or [])]
    )
    excluded_terms = " ".join(
        f'-label:"{label_name}"'
        for label_name in (excluded_labels or [LABEL_NOT_FOR_NATIVE_IMAGE])
    )
    return f"repo:{REPO} is:issue is:open {label_terms} {excluded_terms}".strip()


def normalize_github_issue_search_item(item: dict) -> dict:
    """Convert a GitHub Search API issue item to the shape used by `gh issue list --json`."""
    author = item.get("user") if isinstance(item.get("user"), dict) else {}
    author_login = author.get("login")
    return {
        "number": item["number"],
        "title": item.get("title", ""),
        "author": {"login": author_login} if author_login else None,
        "url": item.get("html_url") or item.get("url"),
        "labels": [
            {"name": label["name"]}
            for label in item.get("labels", [])
            if isinstance(label, dict) and label.get("name")
        ],
        "assignees": [
            {"login": assignee["login"]}
            for assignee in item.get("assignees", [])
            if isinstance(assignee, dict) and assignee.get("login")
        ],
    }


def search_issues_with_label(
        label: str,
        limit: int,
        offset: int = 0,
        extra_labels: list[str] | None = None,
        excluded_labels: list[str] | None = None,
) -> list[dict]:
    """
    Fetch open issues that carry the given label using GitHub search pagination.

    `excluded_labels` are excluded on top of the always-excluded
    `not-for-native-image` label, not instead of it.
    """
    if limit <= 0:
        return []
    if offset >= GITHUB_SEARCH_MAX_RESULTS:
        return []
    limit = min(limit, GITHUB_SEARCH_MAX_RESULTS - offset)

    per_page = GITHUB_API_MAX_PAGE_SIZE
    page = (offset // per_page) + 1
    page_offset = offset % per_page
    query = build_issue_search_query(
        label,
        extra_labels,
        [LABEL_NOT_FOR_NATIVE_IMAGE, *(excluded_labels or [])],
    )
    items = get_issue_search_page(query, page, per_page)
    return items[page_offset:page_offset + limit]


def fetch_issue_search_page(query: str, page: int, per_page: int) -> list[dict]:
    """Fetch and normalize one GitHub search result page for issues."""
    data = gh_json(
        "api", "--method", "GET", "/search/issues",
        "-f", f"q={query}",
        "-f", f"sort={ISSUE_SEARCH_SORT}",
        "-f", f"order={ISSUE_SEARCH_ORDER}",
        "-F", f"per_page={per_page}",
        "-F", f"page={page}",
    )
    return [
        normalize_github_issue_search_item(item)
        for item in data.get("items", [])
    ]


def get_issue_search_page(query: str, page: int, per_page: int) -> list[dict]:
    """Return one issue search page, using the shared local cache when fresh."""
    if not is_issue_search_cache_enabled() or get_issue_search_cache_ttl_seconds() <= 0:
        return fetch_issue_search_page(query, page, per_page)

    ttl_seconds = get_issue_search_cache_ttl_seconds()
    cache_key = build_issue_search_cache_key(
        "page", query, ISSUE_SEARCH_SORT, ISSUE_SEARCH_ORDER, page, per_page
    )
    now = time.time()
    cached_payload = read_issue_search_cache_payload()
    if cached_payload is not None:
        cached_page = get_cached_issue_search_page(cached_payload, cache_key, now, ttl_seconds)
        if cached_page is not None:
            return cached_page

    with LocalIssueSearchCacheWriterLock():
        now = time.time()
        payload = read_issue_search_cache_payload_or_empty(now)
        cached_page = get_cached_issue_search_page(payload, cache_key, now, ttl_seconds)
        if cached_page is not None:
            return cached_page

        issues = fetch_issue_search_page(query, page, per_page)
        set_cached_issue_search_page(payload, cache_key, issues, now)
        write_issue_search_cache_payload(payload, now)
        return issues


def count_issues_with_label(
        label: str,
        extra_labels: list[str] | None = None,
        user_requested_only: bool = False,
        excluded_labels: list[str] | None = None,
) -> int:
    """Return GitHub's count of open issues carrying the given label set."""
    if is_fixture_testing_enabled():
        return require_fixture_github_state().count_open_issues_by_label(
            label,
            extra_labels,
            excluded_labels,
            excluded_authors=get_user_requested_issue_excluded_authors(user_requested_only),
        )
    query = build_issue_search_query(
        label,
        extra_labels,
        [LABEL_NOT_FOR_NATIVE_IMAGE, *(excluded_labels or [])],
    )
    return get_issue_search_count(query)


def fetch_issue_search_count(query: str) -> int:
    """Fetch GitHub's count of open issues for a search query."""
    data = gh_json(
        "api", "--method", "GET", "/search/issues",
        "-f", f"q={query}",
        "-F", "per_page=1",
    )
    return int(data.get("total_count", 0))


def get_issue_search_count(query: str) -> int:
    """Return an issue search count, using the shared local cache when fresh."""
    if not is_issue_search_cache_enabled() or get_issue_search_cache_ttl_seconds() <= 0:
        return fetch_issue_search_count(query)

    ttl_seconds = get_issue_search_cache_ttl_seconds()
    cache_key = build_issue_search_cache_key("count", query)
    now = time.time()
    cached_payload = read_issue_search_cache_payload()
    if cached_payload is not None:
        cached_count = get_cached_issue_search_count(cached_payload, cache_key, now, ttl_seconds)
        if cached_count is not None:
            return cached_count

    with LocalIssueSearchCacheWriterLock():
        now = time.time()
        payload = read_issue_search_cache_payload_or_empty(now)
        cached_count = get_cached_issue_search_count(payload, cache_key, now, ttl_seconds)
        if cached_count is not None:
            return cached_count

        total_count = fetch_issue_search_count(query)
        set_cached_issue_search_count(payload, cache_key, total_count, now)
        write_issue_search_cache_payload(payload, now)
        return total_count


def get_issues_with_label(
        label: str,
        limit: int,
        offset: int = 0,
        extra_labels: list[str] | None = None,
        excluded_labels: list[str] | None = None,
        user_requested_only: bool = False,
) -> list[dict]:
    """Fetch open issues that carry the given label (and any extra labels)."""
    if is_fixture_testing_enabled():
        return require_fixture_github_state().list_open_issues_by_label(
            label,
            limit,
            offset,
            extra_labels,
            excluded_labels,
            excluded_authors=get_user_requested_issue_excluded_authors(user_requested_only),
        )
    return search_issues_with_label(
        label,
        limit,
        offset,
        extra_labels,
        excluded_labels,
    )


def get_issue_label_names(issue: dict) -> list[str]:
    """Return the label names carried by a GitHub issue payload."""
    return [
        label["name"]
        for label in issue.get("labels", [])
        if isinstance(label, dict) and isinstance(label.get("name"), str) and label["name"]
    ]


def issue_has_label(issue: dict, label_name: str) -> bool:
    """Return True when the GitHub issue payload contains the given label."""
    return label_name in get_issue_label_names(issue)


def issue_is_resumable(issue: dict) -> bool:
    """Return True when an issue is explicitly eligible for run continuation."""
    return issue_has_label(issue, LABEL_RESUMABLE)


def add_issue_label_to_payload(issue: dict, label_name: str) -> None:
    """Update a local issue payload after a label was applied remotely."""
    if issue_has_label(issue, label_name):
        return
    labels = issue.get("labels")
    if not isinstance(labels, list):
        labels = []
        issue["labels"] = labels
    labels.append({"name": label_name})


def get_issue_payload_assignees(issue: dict) -> Optional[list[str]]:
    """Return assignee logins from an issue payload, or None if the payload omitted them."""
    if "assignees" not in issue:
        return None
    return [
        assignee["login"]
        for assignee in issue.get("assignees", [])
        if isinstance(assignee, dict) and assignee.get("login")
    ]


def is_assigned_only_to_authenticated_user(
        assignees: list[str] | tuple[str, ...] | None,
        authenticated_user: str | None,
) -> bool:
    """Return True when the assignee list is exactly the current worker user."""
    if not authenticated_user:
        return False
    return tuple(assignees or ()) == (authenticated_user,)


def cached_skip_blocks_authenticated_user(
        cached_skip: CachedIssueClaimSkip,
        authenticated_user: str | None,
        take_blocked_issues: bool = DEFAULT_TAKE_BLOCKED_ISSUES,
) -> bool:
    """Return True when a cached negative observation should still block this worker."""
    if take_blocked_issues and cached_skip.reason == ISSUE_CLAIM_CACHE_REASON_BLOCKED:
        return False
    if cached_skip.reason != ISSUE_CLAIM_CACHE_REASON_ASSIGNED:
        return True
    return not is_assigned_only_to_authenticated_user(cached_skip.assignees, authenticated_user)


@dataclass(frozen=True)
class IssuePriorityTier:
    """One urgency tier of an issue queue, expressed as a GitHub label filter."""

    name: str
    extra_labels: tuple[str, ...]
    excluded_labels: tuple[str, ...]


# Drained in order; each tier excludes the labels of the tiers above it so that an
# issue carrying several priority labels is served exactly once, in its highest tier.
ISSUE_PRIORITY_TIERS: tuple[IssuePriorityTier, ...] = (
    IssuePriorityTier(PRIORITY_HIGH, (LABEL_HIGH_PRIORITY,), ()),
    IssuePriorityTier(LABEL_PRIORITY, (LABEL_PRIORITY,), (LABEL_HIGH_PRIORITY,)),
    IssuePriorityTier(PRIORITY_NORMAL, (), (LABEL_HIGH_PRIORITY, LABEL_PRIORITY)),
)


ISSUE_PRIORITY_TIERS_BY_NAME: dict[str, IssuePriorityTier] = {
    tier.name: tier
    for tier in ISSUE_PRIORITY_TIERS
}


def get_issue_priority_tier(priority: str) -> IssuePriorityTier:
    """Return the query filters for one CLI priority selector."""
    return ISSUE_PRIORITY_TIERS_BY_NAME[priority]


@dataclass
class IssueQueueScanState:
    """How far a prioritized queue scan has advanced through `ISSUE_PRIORITY_TIERS`."""

    tier_index: int = 0
    tier_offset: int = 0
    scanned_count: int = 0

    @property
    def exhausted(self) -> bool:
        """Whether every priority tier has been drained."""
        return self.tier_index >= len(ISSUE_PRIORITY_TIERS)

    @property
    def current_tier(self) -> IssuePriorityTier:
        """The tier the scan is currently paging through."""
        return ISSUE_PRIORITY_TIERS[self.tier_index]

    def advance_within_tier(self, fetched_count: int) -> None:
        """Record that `fetched_count` issues were fetched from the current tier."""
        self.tier_offset += fetched_count
        self.scanned_count += fetched_count

    def advance_to_next_tier(self) -> None:
        """Move to the next, less urgent tier and restart its pagination."""
        self.tier_index += 1
        self.tier_offset = 0

    def describe_position(self) -> str:
        """Return a concise description of the scan position for progress logs."""
        if self.exhausted:
            return "all priority tiers drained"
        return f"{self.current_tier.name} tier, offset {self.tier_offset}"


def get_prioritized_issues_with_label(
        label: str,
        limit: int,
        scan_state: IssueQueueScanState | None = None,
        user_requested_only: bool = False,
) -> tuple[list[dict], IssueQueueScanState]:
    """
    Fetch the next issue batch from the most urgent tier that still has issues.

    Tiers are drained globally rather than ranked inside a batch: every
    `high-priority` issue is served before any `priority` issue, and every
    `priority` issue before any unlabeled one (§FS-forge-issue-resolution-goal).
    An empty result means every tier is exhausted, so callers can stop scanning.
    """
    state = scan_state if scan_state is not None else IssueQueueScanState()
    while not state.exhausted:
        tier = state.current_tier
        raw_issues = get_issues_with_label(
            label,
            limit,
            state.tier_offset,
            list(tier.extra_labels),
            list(tier.excluded_labels),
        )
        if not raw_issues:
            state.advance_to_next_tier()
            continue
        state.advance_within_tier(len(raw_issues))
        issues = filter_user_requested_issues(raw_issues, user_requested_only)
        if issues:
            return issues, state
    return [], state

def resolve_user_requested_only(cli_override: bool | None = None) -> bool:
    """Return whether issue queues should exclude configured non-user authors."""
    if cli_override is not None:
        return cli_override
    return get_env_zero_one_bool("FORGE_USER_REQUESTED_ISSUES_ONLY", False)


def get_user_requested_issue_excluded_authors(user_requested_only: bool) -> tuple[str, ...]:
    """Return issue authors excluded when processing only user-requested issues."""
    if user_requested_only:
        return NON_USER_REQUESTED_ISSUE_AUTHORS
    return ()


def get_issue_author_login(issue: dict) -> str | None:
    """Return the issue author login when the issue payload contains it."""
    author = issue.get("author")
    if isinstance(author, dict):
        login = author.get("login")
        return login if isinstance(login, str) and login else None
    if isinstance(author, str) and author:
        return author
    return None


def filter_user_requested_issues(issues: list[dict], user_requested_only: bool) -> list[dict]:
    """Remove configured automation and maintainer-authored issues when requested."""
    excluded_authors = get_user_requested_issue_excluded_authors(user_requested_only)
    if not excluded_authors:
        return issues
    excluded_author_set = set(excluded_authors)
    return [
        issue
        for issue in issues
        if get_issue_author_login(issue) not in excluded_author_set
    ]


def get_issue_url(issue: dict) -> str:
    """Return the issue URL, preferring the GitHub API payload."""
    issue_url = issue.get("url")
    if issue_url:
        return issue_url
    return f"https://github.com/{REPO}/issues/{issue['number']}"


def format_issue_result_message(claimed_issue: ClaimedIssue, result: str) -> str:
    """Return a concise multiline issue result message for a status banner."""
    issue = claimed_issue.issue
    return (
        f"Issue #{issue['number']}: {issue['title']}\n"
        f"Ticket: {get_issue_url(issue)}\n"
        f"{result}"
    )
