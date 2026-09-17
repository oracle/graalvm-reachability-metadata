# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Issue queue processing loops and the work-queue scheduler
(§AR-forge-dispatcher-decomposition, §FS-forge-issue-resolution-goal)."""


import concurrent
import os
import random
import sys
from utility_scripts.shutdown_signal import is_shutdown_requested
from utility_scripts.stage_logger import log_debug
from utility_scripts.stage_logger import log_stage
from dispatcher.claim_preflight import (
    get_cached_issue_claim_skips,
    get_issue_claim_cache_observation_from_payload,
    resolve_next_issue_claim_candidate_batch,
    should_skip_issue_from_preflight,
)
from dispatcher.claim_setup import (
    build_fixture_claimed_issue,
    claim_issue_for_processing,
)
from dispatcher.config import (
    DEFAULT_ISSUE_SCAN_BATCH_SIZE,
    DEFAULT_PARALLELISM,
    DEFAULT_TAKE_BLOCKED_ISSUES,
    GITHUB_SEARCH_MAX_RESULTS,
    INTERRUPT_REASON_SHUTDOWN,
    ISSUE_SCAN_PROGRESS_LOG_INTERVAL,
    REPO,
    SHUTDOWN_SIGNAL_POLL_SECONDS,
)
from dispatcher.env_config import (
    get_env_parallelism,
    validate_issue_processing_environment,
)
from dispatcher.fixture_support import (
    fixture_issue_run_log,
    is_fixture_testing_enabled,
    require_fixture_github_state,
)
from dispatcher.github_api import resolve_authenticated_user
from dispatcher.interrupts import (
    describe_active_shutdown_signal_path,
    get_user_interrupt_reason,
    mark_shutdown_requested,
    preserve_user_interrupt_reason,
    raise_if_shutdown_requested,
)
from dispatcher.issue_cache import record_issue_claim_cache_observations
from dispatcher.issue_claiming import revert_claimed_issue
from dispatcher.issue_queue import (
    IssuePriorityTier,
    IssueQueueScanState,
    count_issues_with_label,
    filter_user_requested_issues,
    get_issue_priority_tier,
    get_issues_with_label,
    get_prioritized_issues_with_label,
    get_user_requested_issue_excluded_authors,
    resolve_user_requested_only,
)
from dispatcher.issue_queue import format_issue_result_message, get_issue_url
from dispatcher.lifecycle import process_claimed_issue_lifecycle
from dispatcher.queue_config import (
    get_review_queue_configs_from_environment,
    get_work_queue_configs_from_environment,
    validate_work_queue_strategies,
)
from dispatcher.records import (
    CachedIssueClaimSkip,
    ClaimedIssue,
    IssueClaimPreflight,
)
from dispatcher.review_loop import process_pull_requests_with_label

def get_issue_scan_batch_size(_remaining_limit: int, _available_slots: int) -> int:
    """Return how many issue candidates to fetch for the next scan."""
    return DEFAULT_ISSUE_SCAN_BATCH_SIZE


def format_issue_scan_position(
        offset: int,
        current_offset: int,
        scan_state: IssueQueueScanState,
        priority: str | None = None,
) -> str:
    """Return a concise description of the current issue scan position."""
    if priority is not None:
        return f"{priority} tier, offset {current_offset}"
    if offset == 0:
        return scan_state.describe_position()
    return f"offset {current_offset}"


def log_issue_scan_start(label: str, offset: int, priority: str | None = None) -> None:
    """Log where an issue scan starts."""
    if priority is not None:
        position = f"in the {priority} tier from offset {offset}"
    elif offset == 0:
        position = "draining the high, priority, then normal tiers in turn"
    else:
        position = f"from offset {offset}"
    log_debug("issue-scan", f"Starting issue scan for label '{label}' {position}")


def log_issue_scan_progress(
        label: str,
        scanned_count: int,
        offset: int,
        current_offset: int,
        scan_state: IssueQueueScanState,
        priority: str | None = None,
) -> None:
    """Log issue scan progress after another interval of candidates was inspected."""
    position = format_issue_scan_position(offset, current_offset, scan_state, priority)
    log_debug(
        "issue-scan",
        f"Looked through {scanned_count} issue(s) for label '{label}' ({position})",
    )


def resolve_random_issue_scan_offset(
        label: str,
        user_requested_only: bool = False,
        priority: str | None = None,
) -> int:
    """Choose a random searchable offset for an issue label."""
    tier: IssuePriorityTier | None = (
        get_issue_priority_tier(priority)
        if priority is not None
        else None
    )
    if tier is None:
        issue_count = count_issues_with_label(
            label,
            user_requested_only=user_requested_only,
        )
    else:
        issue_count = count_issues_with_label(
            label,
            list(tier.extra_labels),
            user_requested_only,
            list(tier.excluded_labels),
        )
    searchable_count = min(issue_count, GITHUB_SEARCH_MAX_RESULTS)
    if searchable_count <= 0:
        return 0
    return random.randrange(searchable_count)


def process_fixture_issues_for_label(
        label: str,
        limit: int,
        base_reachability_metadata_path: str,
        canonical_metrics_repo_path: str,
        strategy_name: str | None,
        keep_tests_without_dynamic_access: bool,
        environment_already_validated: bool = False,
        user_requested_only: bool = False,
        priority: str | None = None,
) -> int:
    """Run the local fixture issues for one label, sequentially, one `run.log` each.

    Fixture selection is local to the loaded YAML and has no live claim or
    work-queue concurrency to model, so a queue/label run is simply each matching
    issue processed in turn under its own issue-scoped tee (§AR-forge-control-plane).
    """
    if not environment_already_validated:
        validate_issue_processing_environment()

    tier: IssuePriorityTier | None = (
        get_issue_priority_tier(priority)
        if priority is not None
        else None
    )
    issues = require_fixture_github_state().list_open_issues_by_label(
        label,
        limit,
        extra_labels=list(tier.extra_labels) if tier is not None else None,
        excluded_labels=list(tier.excluded_labels) if tier is not None else None,
        excluded_authors=get_user_requested_issue_excluded_authors(user_requested_only),
    )
    if not issues:
        print()
        log_stage("issue-scan", f"No open fixture issues found with label '{label}'")
        return 0

    processed_count = 0
    for issue in issues:
        with fixture_issue_run_log(issue["number"]):
            claimed_issue = build_fixture_claimed_issue(
                issue,
                label,
                base_reachability_metadata_path,
                canonical_metrics_repo_path,
            )
            if claimed_issue is not None:
                process_claimed_issue_lifecycle(
                    claimed_issue,
                    strategy_name,
                    keep_tests_without_dynamic_access,
                    canonical_metrics_repo_path,
                )
        if claimed_issue is not None:
            processed_count += 1
    return processed_count


def process_issues_with_label(
        label: str,
        limit: int,
        offset: int,
        base_reachability_metadata_path: str,
        canonical_metrics_repo_path: str,
        strategy_name: str | None,
        keep_tests_without_dynamic_access: bool,
        authenticated_user: str | None,
        parallelism: int,
        take_blocked_issues: bool = DEFAULT_TAKE_BLOCKED_ISSUES,
        environment_already_validated: bool = False,
        user_requested_only: bool = False,
        priority: str | None = None,
) -> int:
    """
    Process up to `limit` claimable issues, skipping over unclaimed candidates.

    The scan advances through the issue list using `offset`, but the returned count
    reflects only issues that were successfully claimed for processing.
    """
    if is_shutdown_requested():
        log_stage(
            "shutdown",
            f"Stop marker exists at {describe_active_shutdown_signal_path()}; skipping issue queue '{label}'",
        )
        return 0

    if not environment_already_validated:
        validate_issue_processing_environment()

    authenticated_user = resolve_authenticated_user(authenticated_user)

    processed_count = 0
    scanned_count = 0
    next_scan_progress_log_count = ISSUE_SCAN_PROGRESS_LOG_INTERVAL
    current_offset = offset
    scan_state = IssueQueueScanState()
    selected_tier: IssuePriorityTier | None = (
        get_issue_priority_tier(priority)
        if priority is not None
        else None
    )
    selected_extra_labels: list[str] | None = (
        list(selected_tier.extra_labels) if selected_tier is not None else None
    )
    selected_excluded_labels: list[str] | None = (
        list(selected_tier.excluded_labels) if selected_tier is not None else None
    )
    exhausted = False
    unresolved_candidates: list[tuple[dict, CachedIssueClaimSkip | None]] = []
    pending_issues: list[tuple[dict, IssueClaimPreflight | None, CachedIssueClaimSkip | None]] = []
    active_futures: dict[concurrent.futures.Future[bool], ClaimedIssue] = {}

    log_issue_scan_start(label, offset, priority)

    executor = concurrent.futures.ThreadPoolExecutor(max_workers=parallelism)
    try:
        while processed_count < limit or active_futures:
            raise_if_shutdown_requested()
            while processed_count < limit and len(active_futures) < parallelism:
                raise_if_shutdown_requested()
                remaining_limit = limit - processed_count
                available_slots = min(remaining_limit, parallelism - len(active_futures))
                if not pending_issues:
                    if unresolved_candidates:
                        pending_issues.extend(resolve_next_issue_claim_candidate_batch(unresolved_candidates))
                    elif not exhausted:
                        fetch_limit = get_issue_scan_batch_size(remaining_limit, available_slots)
                        if priority is None and offset == 0:
                            issues, scan_state = get_prioritized_issues_with_label(
                                label,
                                fetch_limit,
                                scan_state,
                                user_requested_only,
                            )
                            exhausted = scan_state.exhausted
                            if not issues:
                                if scan_state.scanned_count == 0:
                                    print()
                                    log_stage("issue-scan", f"No open issues found with label '{label}'")
                                break
                        else:
                            raw_issues = get_issues_with_label(
                                label,
                                fetch_limit,
                                current_offset,
                                selected_extra_labels,
                                selected_excluded_labels,
                                user_requested_only=user_requested_only,
                            )
                            current_offset += len(raw_issues)
                            if not raw_issues:
                                if current_offset == offset:
                                    print()
                                    log_stage("issue-scan", f"No open issues found with label '{label}'")
                                exhausted = True
                                break
                            issues = filter_user_requested_issues(raw_issues, user_requested_only)
                            if not issues:
                                continue

                        payload_cache_observations = [
                            observation
                            for observation in (
                                get_issue_claim_cache_observation_from_payload(issue, authenticated_user)
                                for issue in issues
                            )
                            if observation is not None
                        ]
                        record_issue_claim_cache_observations(payload_cache_observations)

                        cached_skips = get_cached_issue_claim_skips(
                            issues,
                            authenticated_user,
                            take_blocked_issues,
                        )
                        unresolved_candidates.extend(
                            (issue, cached_skips.get(issue["number"]))
                            for issue in issues
                        )
                        scanned_count += len(issues)
                        while scanned_count >= next_scan_progress_log_count:
                            log_issue_scan_progress(
                                label,
                                next_scan_progress_log_count,
                                offset,
                                current_offset,
                                scan_state,
                                priority,
                            )
                            next_scan_progress_log_count += ISSUE_SCAN_PROGRESS_LOG_INTERVAL
                        continue
                    else:
                        break

                while pending_issues and processed_count < limit and len(active_futures) < parallelism:
                    raise_if_shutdown_requested()
                    issue, preflight, cached_skip = pending_issues.pop(0)
                    if should_skip_issue_from_preflight(
                            issue,
                            preflight,
                            cached_skip,
                            authenticated_user,
                            take_blocked_issues,
                    ):
                        continue

                    claim_kwargs: dict[str, bool] = {}
                    if take_blocked_issues != DEFAULT_TAKE_BLOCKED_ISSUES:
                        claim_kwargs["take_blocked_issues"] = take_blocked_issues
                    claimed_issue = claim_issue_for_processing(
                        issue,
                        label,
                        base_reachability_metadata_path,
                        canonical_metrics_repo_path,
                        authenticated_user,
                        **claim_kwargs,
                    )
                    if not claimed_issue:
                        continue

                    future = executor.submit(
                        process_claimed_issue_lifecycle,
                        claimed_issue,
                        strategy_name,
                        keep_tests_without_dynamic_access,
                        canonical_metrics_repo_path,
                    )
                    active_futures[future] = claimed_issue
                    processed_count += 1

            if not active_futures:
                if exhausted:
                    break
                continue

            done_futures, _ = concurrent.futures.wait(
                active_futures.keys(),
                timeout=SHUTDOWN_SIGNAL_POLL_SECONDS,
                return_when=concurrent.futures.FIRST_COMPLETED,
            )
            if not done_futures:
                continue
            for future in done_futures:
                active_futures.pop(future, None)
                future.result()
    except KeyboardInterrupt:
        if is_shutdown_requested():
            mark_shutdown_requested()
        else:
            preserve_user_interrupt_reason()
        interrupt_reason = get_user_interrupt_reason()
        interrupt_message = (
            f"Shutdown requested via {describe_active_shutdown_signal_path()}"
            if interrupt_reason == INTERRUPT_REASON_SHUTDOWN
            else f"{interrupt_reason} detected"
        )
        print(
            f"\n[{interrupt_message}. Reverting all active claimed issues before exit.]",
            file=sys.stderr,
        )
        for future, claimed_issue in list(active_futures.items()):
            if future.cancel():
                print(
                    f"[Cancelled queued issue #{claimed_issue.issue['number']} future before revert.]",
                    file=sys.stderr,
                )
            revert_claimed_issue(claimed_issue, f"{interrupt_reason} in main loop")
        raise
    finally:
        executor.shutdown(wait=False, cancel_futures=True)

    log_debug("issue-scan", f"Scanned {scanned_count} issue(s) for label '{label}'")
    return processed_count


def process_work_queues(
        base_reachability_metadata_path: str,
        canonical_metrics_repo_path: str,
        authenticated_user: str | None,
        work_strategy_name_override: str | None = None,
        keep_tests_without_dynamic_access_override: bool = False,
        parallelism_default: int = DEFAULT_PARALLELISM,
        random_offset_override: bool | None = None,
        user_requested_only_override: bool | None = None,
        priority_override: str | None = None,
        take_blocked_issues: bool = DEFAULT_TAKE_BLOCKED_ISSUES,
) -> None:
    """Process all configured issue and review queues in one Python process."""
    queue_configs = get_work_queue_configs_from_environment(work_strategy_name_override, random_offset_override)
    review_queue_configs = [] if is_fixture_testing_enabled() else get_review_queue_configs_from_environment()
    validate_work_queue_strategies(queue_configs)

    keep_tests_without_dynamic_access = (
        keep_tests_without_dynamic_access_override
        or os.environ.get("FORGE_KEEP_TESTS_WITHOUT_DYNAMIC_ACCESS") == "1"
    )
    parallelism = get_env_parallelism("FORGE_PARALLELISM", parallelism_default)
    user_requested_only = resolve_user_requested_only(user_requested_only_override)
    priority_kwargs: dict[str, str] = {}
    if priority_override is not None:
        priority_kwargs["priority"] = priority_override
    claim_kwargs: dict[str, bool] = {}
    if take_blocked_issues != DEFAULT_TAKE_BLOCKED_ISSUES:
        claim_kwargs["take_blocked_issues"] = take_blocked_issues
    enabled_issue_queues = [queue_config for queue_config in queue_configs if queue_config.limit > 0]

    if is_shutdown_requested():
        log_stage(
            "shutdown",
            f"Stop marker exists at {describe_active_shutdown_signal_path()}; skipping all work queues",
        )
        return

    if enabled_issue_queues:
        validate_issue_processing_environment()

    for queue_config in queue_configs:
        if is_shutdown_requested():
            log_stage(
                "shutdown",
                f"Stop marker exists at {describe_active_shutdown_signal_path()}; skipping remaining work queues",
            )
            return
        if queue_config.limit <= 0:
            log_debug("work-queue", f"Skipping issue queue '{queue_config.label}' because its limit is 0")
            continue

        log_debug(
            "work-queue",
            f"Processing up to {queue_config.limit} issue(s) for label '{queue_config.label}'",
        )
        if is_fixture_testing_enabled():
            process_fixture_issues_for_label(
                queue_config.label,
                queue_config.limit,
                base_reachability_metadata_path,
                canonical_metrics_repo_path,
                queue_config.strategy_name,
                keep_tests_without_dynamic_access,
                user_requested_only=user_requested_only,
                environment_already_validated=True,
                **priority_kwargs,
            )
            continue

        authenticated_user = resolve_authenticated_user(authenticated_user)
        issue_scan_offset = 0
        if queue_config.random_offset:
            issue_scan_offset = resolve_random_issue_scan_offset(
                queue_config.label,
                user_requested_only=user_requested_only,
                **priority_kwargs,
            )
            log_debug(
                "issue-scan",
                f"Selected random start offset {issue_scan_offset} for label '{queue_config.label}'",
            )
        process_issues_with_label(
            queue_config.label,
            queue_config.limit,
            issue_scan_offset,
            base_reachability_metadata_path,
            canonical_metrics_repo_path,
            queue_config.strategy_name,
            keep_tests_without_dynamic_access,
            authenticated_user,
            parallelism,
            user_requested_only=user_requested_only,
            environment_already_validated=True,
            **priority_kwargs,
            **claim_kwargs,
        )

    for review_queue_config in review_queue_configs:
        if is_shutdown_requested():
            log_stage(
                "shutdown",
                f"Stop marker exists at {describe_active_shutdown_signal_path()}; skipping remaining review queues",
            )
            return
        if review_queue_config.limit <= 0:
            log_debug("work-queue", f"Skipping review queue '{review_queue_config.label}' because its limit is 0")
            continue

        authenticated_user = resolve_authenticated_user(authenticated_user)
        print()
        log_stage(
            "work-queue",
            f"Reviewing up to {review_queue_config.limit} pull request(s) for label "
            f"'{review_queue_config.label}'",
        )
        process_pull_requests_with_label(
            review_queue_config.label,
            review_queue_config.limit,
            base_reachability_metadata_path,
            authenticated_user,
        )
