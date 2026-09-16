# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Optimistic issue claiming and claim rollback
(§AR-forge-dispatcher-decomposition, §FS-forge-issue-resolution-goal).
"""

import random
import sys
import time
from typing import Optional

from utility_scripts.run_location import PHASE_CLAIM, STEP_CLAIM_ISSUE, log_step_progress, pipeline_step
from utility_scripts.stage_logger import log_debug, log_stage

from dispatcher.claim_preflight import get_open_blocking_issue_numbers, refresh_issue_payload_for_claim
from dispatcher.config import (
    CLAIM_BACKOFF_MAX,
    CLAIM_BACKOFF_MIN,
    DEFAULT_TAKE_BLOCKED_ISSUES,
    ISSUE_CLAIM_CACHE_REASON_ASSIGNED,
    ISSUE_CLAIM_CACHE_REASON_BLOCKED,
    ISSUE_CLAIM_CACHE_REASON_IN_PROGRESS,
    ISSUE_CLAIM_CACHE_REASON_MISSING_PROJECT_ITEM,
    ISSUE_CLAIM_CACHE_REASON_NON_TODO,
    PROJECT_NUMBER,
    STATUS_IN_PROGRESS,
    STATUS_TODO,
)
from dispatcher.fixture_support import is_fixture_testing_enabled
from dispatcher.github_api import format_github_exception_details, gh_json
from dispatcher.interrupts import is_interrupt_exception
from dispatcher.issue_admin import clear_issue_assignees, get_issue_assignees, set_issue_assignee
from dispatcher.issue_cache import (
    invalidate_issue_claim_cache_entry,
    record_issue_claim_cache_observations,
    try_acquire_issue_claim_lock,
)
from dispatcher.issue_queue import is_assigned_only_to_authenticated_user
from dispatcher.project_board import get_item_status, get_project_item_state, set_item_status
from dispatcher.records import ClaimedIssue, IssueClaimCacheObservation


def revert_issue_claim_if_still_owned_by_user(
        item_id: str,
        issue_number: int,
        authenticated_user: str,
        reason: str,
) -> None:
    """Revert a partially claimed issue only when we still own the assignment."""
    assignees = get_issue_assignees(issue_number)
    if assignees == [authenticated_user]:
        revert_issue_claim(item_id, issue_number, reason)
        return

    print(
        f"[Skipping revert for issue #{issue_number}: current assignees are {assignees}, "
        f"not solely {authenticated_user}]",
        file=sys.stderr,
    )


def revert_issue_claim(item_id: str, issue_number: int, reason: str) -> None:
    """Reset an issue claim back to Todo and clear all assignment, with verification."""
    if is_fixture_testing_enabled():
        log_stage(
            "issue-revert",
            f"Fixture mode: no GitHub claim was created for issue #{issue_number}; skipping claim revert.",
        )
        return

    # Claim-revert bookkeeping is narration, not failure output.
    # §FS-forge-run-location-reporting.4
    log_debug("issue-revert", f"Reverting issue #{issue_number} claim because {reason}")
    revert_errors: list[Exception] = []
    log_debug("issue-revert", f"Reverting issue #{issue_number}: setting project item {item_id} -> {STATUS_TODO}")
    try:
        set_item_status(item_id, STATUS_TODO)
    except Exception as exc:
        revert_errors.append(exc)
        print(
            f"ERROR: Issue #{issue_number} revert could not set project item {item_id} "
            f"to {STATUS_TODO}: {format_github_exception_details(exc)}",
            file=sys.stderr,
        )
    log_debug("issue-revert", f"Reverting issue #{issue_number}: clearing all assignees")
    try:
        clear_issue_assignees(issue_number)
    except Exception as exc:
        revert_errors.append(exc)
        print(
            f"ERROR: Issue #{issue_number} revert could not clear assignees: "
            f"{format_github_exception_details(exc)}",
            file=sys.stderr,
        )
    verified_status = get_item_status(item_id)
    verified_assignees = get_issue_assignees(issue_number)
    if verified_status != STATUS_TODO or verified_assignees:
        verification_error = RuntimeError(
            f"Issue #{issue_number} revert verification failed: "
            f"status={verified_status!r}, assignees={verified_assignees!r}"
        )
        if revert_errors:
            raise verification_error from revert_errors[0]
        raise verification_error
    invalidate_issue_claim_cache_entry(issue_number)
    log_debug(
        "issue-revert",
        f"Issue #{issue_number} failed due to {reason}; verified revert with "
        f"status={verified_status}, assignees={verified_assignees}",
    )


def revert_claimed_issue(claimed_issue: ClaimedIssue, reason: str) -> None:
    """Reset a failed claimed issue back to Todo and clear its assignment."""
    revert_issue_claim(claimed_issue.item_id, claimed_issue.issue["number"], reason)


def is_issue_blocked(issue_number: int) -> bool:
    """Return True when the issue has at least one currently open blocking issue."""
    return bool(get_open_blocking_issue_numbers(issue_number))


@pipeline_step(
    PHASE_CLAIM,
    STEP_CLAIM_ISSUE,
    operand=lambda arguments: f"issue #{arguments['issue']['number']}",
)
def try_claim_issue(
        issue: dict,
        authenticated_user: str,
        required_label: str | None = None,
        take_blocked_issues: bool = DEFAULT_TAKE_BLOCKED_ISSUES,
) -> Optional[str]:
    """
    Attempt to exclusively claim an issue.

    1. Take a non-blocking local per-issue lock so same-machine runners using the
       same GitHub account cannot both interpret the same assignee as ownership.
    2. Skip if the issue is assigned to someone else.
    3. SET ourselves as the sole assignee (replaces, not appends).
    4. Wait a random 5-10 s backoff so concurrent runners' SETs have time to land.
    5. Re-read assignees — if we are still the sole assignee, the claim is ours.
       If someone else overwrote us, back off.
    6. On successful claim, move the project item to In Progress.
    """
    number = issue["number"]

    claim_lock = try_acquire_issue_claim_lock(number)
    if claim_lock is None:
        return None

    try:
        if not refresh_issue_payload_for_claim(issue, required_label, authenticated_user):
            return None
        return try_claim_issue_with_local_lock(issue, authenticated_user, take_blocked_issues)
    finally:
        claim_lock.release()


def try_claim_issue_with_local_lock(
        issue: dict,
        authenticated_user: str,
        take_blocked_issues: bool = DEFAULT_TAKE_BLOCKED_ISSUES,
) -> Optional[str]:
    """Attempt the remote optimistic claim while holding the local per-issue lock."""
    number = issue["number"]

    if not take_blocked_issues:
        open_blockers = get_open_blocking_issue_numbers(number)
        if open_blockers:
            record_issue_claim_cache_observations([
                IssueClaimCacheObservation(
                    issue_number=number,
                    reason=ISSUE_CLAIM_CACHE_REASON_BLOCKED,
                    open_blockers=tuple(open_blockers),
                )
            ])
            return None

    # The issue-list payload can be stale when another local runner just claimed
    # the same issue as the same GitHub user, so always re-read after the lock.
    assignees = get_issue_assignees(number)
    if assignees and not is_assigned_only_to_authenticated_user(assignees, authenticated_user):
        record_issue_claim_cache_observations([
            IssueClaimCacheObservation(
                issue_number=number,
                reason=ISSUE_CLAIM_CACHE_REASON_ASSIGNED,
                assignees=tuple(assignees),
            )
        ])
        return None

    item_id, current_status = get_project_item_state(number)
    if not item_id:
        print(
            f"ERROR: Issue #{number} is not linked to project {PROJECT_NUMBER}",
            file=sys.stderr,
        )
        record_issue_claim_cache_observations([
            IssueClaimCacheObservation(
                issue_number=number,
                reason=ISSUE_CLAIM_CACHE_REASON_MISSING_PROJECT_ITEM,
            )
        ])
        return None

    if current_status != STATUS_TODO:
        reason = (
            ISSUE_CLAIM_CACHE_REASON_IN_PROGRESS
            if current_status == STATUS_IN_PROGRESS
            else ISSUE_CLAIM_CACHE_REASON_NON_TODO
        )
        record_issue_claim_cache_observations([
            IssueClaimCacheObservation(
                issue_number=number,
                reason=reason,
                project_status=current_status,
            )
        ])
        return None

    try:
        # SET ourselves as the sole assignee
        log_debug("issue-claim", f"Setting issue #{number} assignee to {authenticated_user}")
        set_issue_assignee(number, authenticated_user)

        # Random wait so concurrent runners' SETs have time to land.
        backoff = random.uniform(CLAIM_BACKOFF_MIN, CLAIM_BACKOFF_MAX)
        log_debug("issue-claim", f"Waiting {backoff:.1f}s before verifying claim on issue #{number}")
        time.sleep(backoff)

        # Verify we are still the assignee
        assignees = get_issue_assignees(number)
        if assignees != [authenticated_user]:
            log_debug("issue-claim", f"Issue #{number}: assignee is now {assignees}, not us. Backing off.")
            if assignees:
                record_issue_claim_cache_observations([
                    IssueClaimCacheObservation(
                        issue_number=number,
                        reason=ISSUE_CLAIM_CACHE_REASON_ASSIGNED,
                        assignees=tuple(assignees),
                    )
                ])
            return None

        set_item_status(item_id, STATUS_IN_PROGRESS)
        record_issue_claim_cache_observations([
            IssueClaimCacheObservation(
                issue_number=number,
                reason=ISSUE_CLAIM_CACHE_REASON_IN_PROGRESS,
                project_status=STATUS_IN_PROGRESS,
            )
        ])
        log_step_progress(PHASE_CLAIM, STEP_CLAIM_ISSUE, f"Issue #{number} claimed")
        log_debug("claim", f"Issue #{number} claimed; project status is In Progress")
        return item_id
    except BaseException as exc:
        revert_issue_claim_if_still_owned_by_user(
            item_id,
            number,
            authenticated_user,
            "claim interrupted by Ctrl+C" if is_interrupt_exception(exc)
            else f"claim failure ({type(exc).__name__})",
        )
        raise
