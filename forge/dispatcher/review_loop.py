# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The pull request review loop: queue selection, descriptor processing, and
review workspaces (§AR-forge-dispatcher-decomposition,
§FS-automated-pr-review)."""


import os
import subprocess
import sys
import uuid
from dataclasses import dataclass
from utility_scripts.repo_path_resolver import get_repo_root
from utility_scripts.repo_path_resolver import require_complete_reachability_repo
from utility_scripts.shutdown_signal import is_shutdown_requested
from utility_scripts.stage_logger import log_stage
from dispatcher.ci_repair import reconcile_failed_ci_pull_request
from dispatcher.config import (
    DEFAULT_WORKTREE_BASE_REF,
    LABEL_FORGE_MERGE_FOLLOW_UP,
    LABEL_HUMAN_INTERVENTION,
    LABEL_HUMAN_INTERVENTION_FIXED,
    SCRATCH_REVIEW_WORKTREE_DIRNAME,
)
from dispatcher.github_api import (
    gh,
    is_authored_by_user,
)
from dispatcher.interrupts import (
    describe_active_shutdown_signal_path,
    sleep_until_shutdown_or_timeout,
)
from dispatcher.issue_admin import (
    add_pull_request_label,
    remove_pull_request_label,
)
from dispatcher.pr_merge import (
    is_pull_request_conflicting,
    mark_pull_request_merge_follow_up_pending,
    reconcile_auto_merged_pull_request_follow_ups,
    resolve_pull_request_merge_conflict,
    validate_pull_request_indexes_before_merge,
)
from dispatcher.pr_publication import (
    approve_pull_request_from_descriptor,
    enable_pull_request_auto_merge,
    ensure_pull_request_unapproved,
    publication_review_disposition,
    reconcile_rejected_publication,
    validate_pull_request_publication,
)
from dispatcher.pr_state import (
    attach_pull_request_state,
    dismiss_requested_changes_reviews,
    get_pull_requests_with_label,
    get_pull_requests_with_labels,
    has_failed_pull_request_ci,
    has_successful_pull_request_ci,
    pull_request_has_label,
)
from dispatcher.worktrees import (
    create_detached_worktree,
    fetch_default_base_ref,
    remove_worktree,
)

from dispatcher.worktrees import fetch_review_base_ref

@dataclass(frozen=True)
class ReviewQueueConfig:
    label: str
    limit: int


@dataclass(frozen=True)
class ReviewQueueSelection:
    ready: list[dict]
    failed: list[dict]
    waiting_count: int

def is_review_pull_request_base_eligible(
        pull_request: dict,
        authenticated_user: str,
        excluded_labels: tuple[str, ...] = (),
) -> bool:
    """Return True when cheap pull request fields do not exclude review processing."""
    return (
        not is_authored_by_user(pull_request, authenticated_user)
        and not pull_request_has_label(pull_request, LABEL_HUMAN_INTERVENTION)
        and not any(pull_request_has_label(pull_request, label) for label in excluded_labels)
    )


def is_review_pull_request_eligible(pull_request: dict, authenticated_user: str) -> bool:
    """Return True when the review queue may process the pull request."""
    return (
        is_review_pull_request_base_eligible(pull_request, authenticated_user)
        and has_successful_pull_request_ci(pull_request)
    )


def select_review_pull_requests(
        pull_requests: list[dict],
        authenticated_user: str,
        limit: int,
        state_cache: dict[int, dict],
        reachability_metadata_path: str,
        excluded_labels: tuple[str, ...] = (),
) -> ReviewQueueSelection:
    """Classify review candidates after deterministic conflict maintenance."""
    ready_pull_requests: list[dict] = []
    failed_pull_requests: list[dict] = []
    waiting_count = 0

    for pull_request in pull_requests:
        if len(ready_pull_requests) >= limit:
            break
        if not is_review_pull_request_base_eligible(pull_request, authenticated_user, excluded_labels):
            continue

        enriched_pull_request = attach_pull_request_state(
            pull_request,
            state_cache,
        )
        if is_pull_request_conflicting(enriched_pull_request):
            pr_number = enriched_pull_request["number"]
            if not resolve_pull_request_merge_conflict(
                    enriched_pull_request,
                    reachability_metadata_path,
            ):
                add_pull_request_label(pr_number, LABEL_HUMAN_INTERVENTION)
                print(
                    f"[Added label '{LABEL_HUMAN_INTERVENTION}' to conflicting "
                    f"PR #{pr_number}; git could not refresh its head without judgment.]"
                )
            state_cache[pr_number] = {
                **enriched_pull_request,
                "mergeable": "UNKNOWN",
                "mergeStateStatus": "UNKNOWN",
                "statusCheckRollup": {"state": "PENDING"},
            }
            waiting_count += 1
        elif has_successful_pull_request_ci(enriched_pull_request):
            ready_pull_requests.append(enriched_pull_request)
        elif has_failed_pull_request_ci(enriched_pull_request):
            failed_pull_requests.append(enriched_pull_request)
        else:
            waiting_count += 1

    return ReviewQueueSelection(
        ready=ready_pull_requests,
        failed=failed_pull_requests,
        waiting_count=waiting_count,
    )

def _process_descriptor_pull_request(
        pull_request: dict,
        reachability_metadata_path: str,
        maintainer_override: bool = False,
) -> None:
    """Execute one exact-head publication decision without semantic re-review."""
    pr_number = pull_request.get("number")
    if not isinstance(pr_number, int):
        raise RuntimeError("Descriptor-driven review requires a pull request number")
    validated = validate_pull_request_publication(
        pull_request,
        reachability_metadata_path,
    )
    decision, _action = publication_review_disposition(validated)
    if decision != "approved":
        reconcile_rejected_publication(pull_request, validated)
        return

    if (
            pull_request_has_label(pull_request, LABEL_HUMAN_INTERVENTION)
            and not maintainer_override
    ):
        ensure_pull_request_unapproved(pull_request)
        print(
            f"[Skipping PR #{pr_number}: it remains labeled "
            f"'{LABEL_HUMAN_INTERVENTION}'.]"
        )
        return

    conflicting = is_pull_request_conflicting(pull_request)
    if not conflicting:
        validate_pull_request_indexes_before_merge(
            pr_number,
            str(pull_request["headRefOid"]),
            reachability_metadata_path,
        )

    if maintainer_override:
        dismissed_count = dismiss_requested_changes_reviews(pr_number)
        if dismissed_count:
            print(
                f"[Dismissed {dismissed_count} requested-changes review(s) "
                f"on PR #{pr_number}.]"
            )
        for label_name in (LABEL_HUMAN_INTERVENTION, LABEL_HUMAN_INTERVENTION_FIXED):
            if pull_request_has_label(pull_request, label_name):
                remove_pull_request_label(pr_number, label_name)

    mark_pull_request_merge_follow_up_pending(pull_request)
    print(
        f"[Approving descriptor-validated PR #{pr_number} at "
        f"{pull_request['headRefOid']}; no semantic review agent launched.]"
    )
    approve_pull_request_from_descriptor(pull_request)
    enable_pull_request_auto_merge(pull_request)

    if conflicting:
        if not resolve_pull_request_merge_conflict(
                pull_request,
                reachability_metadata_path,
        ):
            ensure_pull_request_unapproved(pull_request)
            if pull_request_has_label(pull_request, LABEL_FORGE_MERGE_FOLLOW_UP):
                remove_pull_request_label(pr_number, LABEL_FORGE_MERGE_FOLLOW_UP)
            add_pull_request_label(pr_number, LABEL_HUMAN_INTERVENTION)
            print(
                f"[Added label '{LABEL_HUMAN_INTERVENTION}' to conflicting "
                f"PR #{pr_number}; git could not refresh its head without judgment.]"
            )
        return

    if has_failed_pull_request_ci(pull_request):
        reconcile_failed_ci_pull_request(
            pull_request,
            validated,
            reachability_metadata_path,
        )
        return
    if not has_successful_pull_request_ci(pull_request):
        print(f"[Waiting for CI to complete on approved auto-merge PR #{pr_number}.]")
        return

    print(f"[Approved PR #{pr_number} is ready for GitHub auto-merge.]")
    reconcile_auto_merged_pull_request_follow_ups()


def process_pull_requests_with_label(
        label: str,
        limit: int,
        reachability_metadata_path: str,
        authenticated_user: str,
) -> None:
    """Execute validated local-review decisions for one labeled PR queue.

    §FS-automated-pr-review
    """
    del authenticated_user
    reconcile_auto_merged_pull_request_follow_ups()
    fetch_limit = max(limit, 20)
    state_cache: dict[int, dict] = {}
    failures: list[int] = []
    attempted_numbers: set[int] = set()
    processed = 0
    while processed < limit:
        fixed_pull_requests = get_pull_requests_with_labels(
            [label, LABEL_HUMAN_INTERVENTION_FIXED],
            fetch_limit,
        )
        ordinary_pull_requests = get_pull_requests_with_label(label, fetch_limit)
        fixed_numbers = {
            pull_request.get("number")
            for pull_request in fixed_pull_requests
            if isinstance(pull_request.get("number"), int)
        }
        candidates = [
            *((pull_request, True) for pull_request in fixed_pull_requests),
            *((pull_request, False) for pull_request in ordinary_pull_requests
              if pull_request.get("number") not in fixed_numbers),
        ]

        for pull_request, maintainer_override in candidates:
            if processed >= limit:
                break
            pr_number = pull_request.get("number")
            if not isinstance(pr_number, int) or pr_number in attempted_numbers:
                continue
            attempted_numbers.add(pr_number)
            try:
                enriched = attach_pull_request_state(pull_request, state_cache)
                _process_descriptor_pull_request(
                    enriched,
                    reachability_metadata_path,
                    maintainer_override=maintainer_override,
                )
                processed += 1
            except ValueError as error:
                print(f"[Skipping ineligible PR #{pr_number}: {error}]")
            except Exception as error:
                print(
                    f"ERROR: Failed descriptor-driven processing for PR "
                    f"#{pr_number}: {error!r}",
                    file=sys.stderr,
                )
                failures.append(pr_number)

        if processed >= limit:
            break
        if (
                len(fixed_pull_requests) < fetch_limit
                and len(ordinary_pull_requests) < fetch_limit
        ):
            break
        fetch_limit *= 2
        print(
            f"[Found {processed} eligible Forge PR(s) after inspecting "
            f"{len(attempted_numbers)} candidate(s); fetching up to {fetch_limit}.]"
        )

    if processed == 0:
        print(
            f"\n[No exact-head Forge publications found with label '{label}'.]"
        )
    if failures:
        print(
            f"ERROR: Pull request processing failed for pull request(s): {failures}",
            file=sys.stderr,
        )
        sys.exit(1)

def run_pull_request_review_loop(
        label: str,
        limit: int,
        reachability_metadata_path: str,
        authenticated_user: str,
        period_seconds: int | None = None,
) -> None:
    """Run pull request reviews once or repeatedly after each configured period."""
    iteration = 1
    while True:
        if is_shutdown_requested():
            log_stage(
                "shutdown",
                f"Stop marker exists at {describe_active_shutdown_signal_path()}; skipping pull request review loop",
            )
            return
        if period_seconds is not None:
            print(f"\n[Starting review iteration {iteration}.]")
        process_pull_requests_with_label(
            label,
            limit,
            reachability_metadata_path,
            authenticated_user,
        )
        if period_seconds is None:
            return
        print(f"[Sleeping {period_seconds} second(s) before the next review iteration.]")
        if sleep_until_shutdown_or_timeout(period_seconds):
            return
        iteration += 1

