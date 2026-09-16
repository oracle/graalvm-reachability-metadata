# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""
Fetches open issues from the graalvm-reachability-metadata project board,
claims them via optimistic locking (set-assignee + wait + verify),
runs the matching pipeline, and updates the project item status.

This module is the Forge control-plane dispatcher (§AR-forge-control-plane): it
owns queue selection, issue claiming, isolated worktree setup, workflow routing,
publication handoff, follow-up labeling, and cleanup, implementing the
orchestration contract in §AR-forge-orchestration.

Usage:
  python forge-metadata.py --label <label> [--limit N] [--offset N|--random-offset]
      [--strategy-name <name>] [--reachability-metadata-path <path>]
  python forge-metadata.py --fixture-testing --issue-number <number>
      --strategy-name <name> [--reachability-metadata-path <path>]
  python forge-metadata.py --review-pr <label> [--limit N]
      [--reachability-metadata-path <path>] [--period <seconds|Nm|Nh|Nd>]
"""

import argparse
import concurrent.futures
import contextlib
import errno
import importlib.util
import hashlib
import json
import os
import random
import re
import shlex
import shutil
import signal
import subprocess
import sys
import tempfile
import threading
import time
import traceback
import uuid
from collections.abc import Callable
from dataclasses import dataclass
from datetime import datetime
from types import SimpleNamespace
from typing import Any, Optional
from urllib.parse import quote

import ai_workflows.core  # noqa: F401 - triggers strategy registration
from ai_workflows.drivers.add_new_library_setup import (
    DEFAULT_MODEL_NAME,
    ScaffoldError,
    create_feature_branch_for_library,
    init_agent as init_workflow_agent,
    prepare_native_image_eligible_artifact,
    run_scaffold as run_new_library_scaffold,
)
from ai_workflows.drivers.add_new_library_support import (
    DEFAULT_STRATEGY_NAME as DEFAULT_NEW_LIBRARY_STRATEGY_NAME,
    main as run_add_new_library_support_workflow,
)
from ai_workflows.drivers.fix_javac_fail import (
    main as run_fix_javac_workflow,
)
from ai_workflows.drivers.fix_java_run_fail import (
    main as run_fix_java_run_workflow,
)
from ai_workflows.drivers.fix_ni_run import (
    DEFAULT_STRATEGY_NAME as DEFAULT_NI_RUN_STRATEGY_NAME,
    main as run_fix_ni_run_workflow,
)
from ai_workflows.drivers.improve_library_coverage import (
    DEFAULT_STRATEGY_NAME as DEFAULT_LIBRARY_UPDATE_STRATEGY_NAME,
    main as run_improve_library_coverage_workflow,
)
from ai_workflows.drivers.library_update_preparation import prepare_library_update_target
from ai_workflows.drivers.library_update_router import (
    ROUTE_FIX_JAVA_RUN,
    ROUTE_FIX_JAVAC,
    ROUTE_FIX_NI_RUN,
    ROUTE_IMPROVE_COVERAGE,
    LibraryUpdateRoute,
    load_library_update_route,
    select_library_update_route,
    write_library_update_route,
)
from ai_workflows.drivers.java_fail_workflow import (
    DEFAULT_JAVAC_STRATEGY,
    DEFAULT_JAVA_RUN_STRATEGY,
)
from ai_workflows.core.workflow_strategy import (
    RUN_STATUS_CHUNK_READY,
    RUN_STATUS_FAILURE,
    RUN_STATUS_SUCCESS,
    SUCCESS_WITH_INTERVENTION_STATUS,
)
from ai_workflows.agents.agent import AgentFailureError
from ai_workflows.agents.codex_agent import extract_codex_token_usage
from ai_workflows.agents.agent_runtime import (
    analysis_agent_run,
    get_analysis_agent,
)
from git_scripts.common_git import (
    GitTransportError,
    get_issue_project_item_status,
    get_origin_owner,
    run_git_transport,
)
from git_scripts.github_cli import (
    GITHUB_TRANSIENT_RETRY_ATTEMPTS,
    GitHubError,
    GitHubRateLimitExceeded,
    _format_github_retry_reason,
    _github_retry_delay_seconds,
    _log_github_transient_retry,
    ensure_gh_authenticated,
    is_github_rate_limit_text,
    is_github_transient_failure_text,
    log_github_query,
    run_github_command_with_retries,
    run_github_json_with_retries,
    run_github_with_retries,
)
from git_scripts.publish_javac_fix import (
    main as run_publish_javac_fix,
)
from git_scripts.publish_java_run_fix import (
    main as run_publish_java_run_fix,
)
from git_scripts.publish_new_library_support import (
    main as run_publish_new_library_support,
)
from git_scripts.publish_not_for_native_image import (
    main as run_publish_not_for_native_image,
)
from git_scripts.publish_ni_run_fix import (
    main as run_publish_ni_run_fix,
)
from git_scripts.publish_improve_coverage import (
    main as run_publish_improve_coverage,
)
from git_scripts.local_review_records import (
    LocalReviewVerdict,
    _record_finding,
)
from git_scripts.local_review_session import (
    REVIEW_SKILLS_BY_TASK_TYPE,
    _read_verdict,
)
from git_scripts.publication_descriptor import validate_publication_descriptor
from utility_scripts.dynamic_access_exhaust_report import (
    DynamicAccessExhaustReport,
    dynamic_access_exhaust_report_path,
    find_dynamic_access_exhaust_report_path,
)
from utility_scripts.continuation_marker import (
    CONTINUATION_MARKER_FILENAME,
    PHASE_EXPLORE,
    PHASE_PUBLICATION,
    RESUMED_TREE_PHASES,
    ContinuationMarker,
    continuation_marker_path,
    load_continuation_marker,
    save_phase_update,
)
from utility_scripts.dynamic_access_report import load_dynamic_access_coverage_report
from utility_scripts.fixture_github import FixtureGitHubState, load_fixture_github_state
from utility_scripts.gradle_environment import gradle_command_environment
from utility_scripts.logged_command import run_logged_command
from utility_scripts.library_stats import resolve_stats_file_path
from utility_scripts.library_preparation_preflight import (
    LIBRARY_PREPARATION_PREFLIGHT_FILENAME,
    load_library_preparation_preflight,
    run_library_preparation_preflight as run_preflight_decision,
    write_library_preparation_preflight,
)
from utility_scripts.java_fix_coverage_follow_up import (
    ensure_coverage_follow_up_issue,
    uncovered_dynamic_access_class_count,
)
from utility_scripts.library_update_follow_up_issue import extract_follow_up_issue_numbers
from utility_scripts.metadata_index import (
    coordinate_parts as metadata_coordinate_parts,
    get_not_for_native_image_marker,
    is_newer_than_latest_metadata_version,
    is_not_for_native_image,
    resolve_metadata_version,
    resolve_test_dir,
)
from utility_scripts.metrics_writer import (
    PENDING_METRICS_FILENAME,
    load_execution_metrics_for_timestamp,
    read_pending_metrics,
    write_pending_metrics,
)
from utility_scripts.token_costs import calc_model_session_cost
from utility_scripts.native_image_artifact import (
    ARTIFACT_REPOSITORY_URLS,
    artifact_is_published,
)
from utility_scripts.repo_path_resolver import (
    git_env_limited_to_repo_root,
    get_forge_subdir_name,
    get_repo_root,
    require_complete_reachability_repo,
    resolve_metrics_repo_root,
    resolve_reachability_repo_root,
)
from utility_scripts.run_location import (
    PHASE_CLAIM,
    PHASE_SETUP,
    STEP_CHECK_HOST_REQUIREMENTS,
    STEP_CHECK_ISSUE_FORM,
    STEP_CHECK_STRATEGY_AND_MODEL,
    STEP_CLAIM_ISSUE,
    STEP_CREATE_ISSUE_WORKSPACE,
    STEP_NEURAL_SETUP,
    STEP_NORMAL_SETUP,
    STEP_PUBLISH_BRANCH,
    STEP_ROUTE_TO_DRIVER,
    RunLocation,
    bind_continuation_marker,
    bind_run_context,
    enter_phase,
    format_run_failure_line,
    log_step_progress,
    marker_failure_location,
    pipeline_step,
    record_step_failure,
    report_run_failure,
    reset_run_location,
    resolve_failure_location,
    run_step,
)
from utility_scripts.source_context_discovery import GradleBootstrapFailure
from utility_scripts.stage_logger import (
    debug_logging_enabled,
    enable_verbose_logging,
    log_debug,
    log_detail,
    log_failure_banner,
    log_stage,
    log_success_banner,
)
from utility_scripts.shutdown_signal import get_active_shutdown_signal_path, is_shutdown_requested
from utility_scripts.strategy_loader import load_strategy_by_name, require_strategy_by_name
from utility_scripts.task_logs import (
    build_task_log_path,
    display_log_path,
    new_session_id,
    resolve_logs_root,
    sanitize_library_log_segment,
)
from utility_scripts.host_graalvm_checks import (
    DEFAULT_GRAALVM_VERSION_CHECK,
    GRAALVM_VERSION_CHECK_ENV_VAR,
    GRAALVM_VERSION_CHECK_MODES,
    ISSUE_GRAALVM_ENV_VARS,
    require_issue_graalvm_homes,
    resolve_graalvm_version_check,
)
from utility_scripts.host_requirements import (
    QueueRequirements,
    ensure_host_requirements,
    resolve_queue_requirements,
)
from utility_scripts.workflow_setup import list_all_files

try:
    import fcntl
except ImportError:
    fcntl = None

from dispatcher.config import (  # noqa: F401 - re-exported dispatcher API
    DEFAULT_MAX_ISSUES,
    DEFAULT_PARALLELISM,
    MAX_PARALLELISM,
    DEFAULT_ISSUE_SCAN_BATCH_SIZE,
    ISSUE_SCAN_PROGRESS_LOG_INTERVAL,
    GITHUB_API_MAX_PAGE_SIZE,
    GITHUB_SEARCH_MAX_RESULTS,
    ISSUE_SEARCH_SORT,
    ISSUE_SEARCH_ORDER,
    DEFAULT_TAKE_BLOCKED_ISSUES,
    ISSUE_CLAIM_PREFLIGHT_CHUNK_SIZE,
    ISSUE_CLAIM_CACHE_VERSION,
    ISSUE_CLAIM_CACHE_FILENAME,
    ISSUE_CLAIM_CACHE_LOCK_FILENAME,
    DEFAULT_ISSUE_CLAIM_CACHE_TTL_SECONDS,
    ISSUE_SEARCH_CACHE_VERSION,
    ISSUE_SEARCH_CACHE_FILENAME,
    ISSUE_SEARCH_CACHE_LOCK_FILENAME,
    DEFAULT_ISSUE_SEARCH_CACHE_TTL_SECONDS,
    GITHUB_RATE_LIMIT_EXIT_CODE,
    GRADLE_BOOTSTRAP_EXIT_CODE,
    FIXTURE_E2E_LOG_DIRNAME,
    FIXTURE_RUN_LOG_FILENAME,
    FIXTURE_PUBLICATION_FILENAME,
    FIXTURE_PREFLIGHT_EVIDENCE_DIRNAME,
    REVIEW_PERIOD_SUFFIX_SECONDS,
    FAILED_CI_STATES,
    RERUNNABLE_WORKFLOW_RUN_CONCLUSIONS,
    REPO,
    PROJECT_NUMBER,
    STATUS_FIELD_NAME,
    STATUS_TODO,
    STATUS_IN_PROGRESS,
    STATUS_DONE,
    LABEL_LIBRARY_NEW,
    LABEL_LIBRARY_UPDATE,
    LABEL_JAVAC_FAIL,
    LABEL_JAVA_RUN_FAIL,
    LABEL_NI_RUN_FAIL,
    LABEL_PR_JAVAC_FIX,
    LABEL_PR_JAVA_RUN_FIX,
    LABEL_PR_NI_RUN_FIX,
    LABEL_PR_LIBRARY_UPDATE,
    LABEL_PR_CODE_COVERAGE,
    LABEL_HIGH_PRIORITY,
    LABEL_PRIORITY,
    PRIORITY_HIGH,
    PRIORITY_NORMAL,
    PRIORITY_CHOICES,
    LABEL_HUMAN_INTERVENTION,
    LABEL_HUMAN_INTERVENTION_FIXED,
    LABEL_FORGE_MERGE_FOLLOW_UP,
    LABEL_NOT_FOR_NATIVE_IMAGE,
    LABEL_LIBRARY_UNSUPPORTED_VERSION,
    LABEL_CHUNKED_DYNAMIC_ACCESS,
    LABEL_RESUMABLE,
    FIXTURE_AUTHENTICATED_USER,
    FIXTURE_COVERAGE_FOLLOW_UP_ISSUE_OFFSET,
    NON_USER_REQUESTED_ISSUE_AUTHORS,
    SCRATCH_WORKTREE_DIRNAME,
    PREFLIGHT_INFO_DIRNAME,
    SCRATCH_REVIEW_WORKTREE_DIRNAME,
    SCRATCH_FINAL_INDEX_VALIDATION_WORKTREE_DIRNAME,
    SCRATCH_CONFLICT_RESOLUTION_WORKTREE_DIRNAME,
    SCRATCH_METRICS_DIRNAME,
    ISSUE_CLAIM_LOCK_DIRNAME,
    ISSUE_CLAIM_CACHE_REASON_ASSIGNED,
    ISSUE_CLAIM_CACHE_REASON_HUMAN_INTERVENTION,
    ISSUE_CLAIM_CACHE_REASON_NOT_FOR_NATIVE_IMAGE,
    ISSUE_CLAIM_CACHE_REASON_BLOCKED,
    ISSUE_CLAIM_CACHE_REASON_CLOSED,
    ISSUE_CLAIM_CACHE_REASON_MISSING_PROJECT_ITEM,
    ISSUE_CLAIM_CACHE_REASON_NON_TODO,
    ISSUE_CLAIM_CACHE_REASON_IN_PROGRESS,
    ISSUE_CLAIM_CACHE_REASONS,
    HUMAN_INTERVENTION_LOGS_DIRNAME,
    SCRIPT_RUN_METRICS_DIR,
    ADD_NEW_LIBRARY_METRICS_FILE,
    FIX_JAVAC_METRICS_FILE,
    FIX_JAVA_RUN_METRICS_FILE,
    LOW_DYNAMIC_ACCESS_COVERAGE_RATIO,
    HUMAN_INTERVENTION_LABEL_COLOR,
    HUMAN_INTERVENTION_LABEL_DESCRIPTION,
    FORGE_MERGE_FOLLOW_UP_LABEL_COLOR,
    FORGE_MERGE_FOLLOW_UP_LABEL_DESCRIPTION,
    HUMAN_INTERVENTION_NON_FAILURE_STATUSES,
    PUBLICATION_METRICS_EXTRA_KEYS,
    NOT_FOR_NATIVE_IMAGE_LABEL_COLOR,
    NOT_FOR_NATIVE_IMAGE_LABEL_DESCRIPTION,
    PRIORITY_LABEL_COLOR,
    PRIORITY_LABEL_DESCRIPTION,
    CHUNKED_DYNAMIC_ACCESS_LABEL_COLOR,
    CHUNKED_DYNAMIC_ACCESS_LABEL_DESCRIPTION,
    LIBRARY_UNSUPPORTED_VERSION_LABEL_COLOR,
    LIBRARY_UNSUPPORTED_VERSION_LABEL_DESCRIPTION,
    RESUMABLE_LABEL_COLOR,
    RESUMABLE_LABEL_DESCRIPTION,
    DEFAULT_DYNAMIC_ACCESS_CHUNK_CLASS_THRESHOLD,
    DEFAULT_WORK_QUEUE_STRATEGY_NAME,
    FAILURE_ANALYSIS_TIMEOUT_SECONDS,
    REVIEW_TIMEOUT_SECONDS,
    DEFAULT_WORKTREE_BASE_REF,
    TRUSTED_FORGE_PUBLISHER_LOGIN,
    BENCHMARK_PUBLICATION_TASK_TYPE,
    SUPPORTED_FORGE_REVIEW_TASK_TYPES,
    LOCAL_REVIEW_CLOSE_MARKER,
    FORGE_APPROVAL_BODY_PREFIX,
    DEV_GRAALVM_ENV_VAR,
    POST_GENERATION_GRAALVM_ENV_VAR,
    LATEST_EA_GRAALVM_ENV_VAR,
    FORGE_DIR,
    INTERRUPT_EXIT_CODES,
    INTERRUPT_REASON_CTRL_C,
    INTERRUPT_REASON_SHUTDOWN,
    INTERRUPT_REASON_GRADLE_BOOTSTRAP,
    SHUTDOWN_SIGNAL_POLL_SECONDS,
    PIPELINE_LABELS,
    FAILURE_PIPELINE_LABELS,
    ISSUE_FORM_RULE_SINGLE_WORKFLOW_LABEL,
    ISSUE_FORM_RULE_MAVEN_COORDINATES,
    ISSUE_FORM_RULE_CURRENT_LATEST_VERSION,
    ISSUE_FORM_RULE_NEWER_THAN_LATEST,
    ISSUE_FORM_RULE_PUBLISHED_ARTIFACT,
    ISSUE_FORM_REJECTION_MARKER_PREFIX,
    CLAIM_BACKOFF_MIN,
    CLAIM_BACKOFF_MAX,
)
from dispatcher.records import (  # noqa: F401 - re-exported dispatcher API
    ClaimedIssue,
    WorkQueueConfig,
    IssueClaimPreflight,
    IssueClaimCacheObservation,
    CachedIssueClaimSkip,
    WorkflowRunResult,
    DynamicAccessCoverageSnapshot,
)
from dispatcher.interrupts import (  # noqa: F401 - re-exported dispatcher API
    mark_user_interrupt_requested,
    preserve_user_interrupt_reason,
    clear_user_interrupt_requested,
    is_user_interrupt_requested,
    get_user_interrupt_reason,
    is_shutdown_request_interrupt,
    is_gradle_bootstrap_interrupt,
    mark_shutdown_requested,
    describe_active_shutdown_signal_path,
    raise_if_shutdown_requested,
    is_interrupt_exit_code,
    is_interrupt_exception,
    _handle_sigint,
)
from dispatcher.github_api import (  # noqa: F401 - re-exported dispatcher API
    gh,
    gh_json,
    format_github_exception_details,
)
from dispatcher.fixture_support import (  # noqa: F401 - re-exported dispatcher API
    FixtureRunLogTee,
    _fixture_run_timestamp,
    get_fixture_run_timestamp,
    get_fixture_issue_artifact_dir,
    fixture_issue_run_log,
    configure_fixture_testing,
    is_fixture_testing_enabled,
    require_fixture_github_state,
)
from dispatcher.issue_cache import (  # noqa: F401 - re-exported dispatcher API
    LocalIssueClaimLock,
    LocalIssueClaimCacheWriterLock,
    LocalIssueSearchCacheWriterLock,
    get_issue_claim_locks_root,
    get_issue_claim_lock_path,
    get_issue_claim_cache_path,
    get_issue_claim_cache_lock_path,
    get_issue_search_cache_path,
    get_issue_search_cache_lock_path,
    try_acquire_issue_claim_lock,
    is_issue_claim_cache_enabled,
    get_issue_claim_cache_ttl_seconds,
    _read_issue_claim_cache_payload,
    _parse_issue_claim_cache_entry,
    read_issue_claim_cache,
    _write_issue_claim_cache_entries,
    record_issue_claim_cache_observations,
    invalidate_issue_claim_cache_entry,
    _remove_file_if_exists,
    clear_issue_claim_cache,
    is_issue_search_cache_enabled,
    get_issue_search_cache_ttl_seconds,
    _read_issue_search_cache_payload,
    _empty_issue_search_cache_payload,
    _read_issue_search_cache_payload_or_empty,
    _write_issue_search_cache_payload,
    clear_issue_search_cache,
    clear_issue_caches,
    build_issue_search_cache_key,
    _is_fresh_issue_search_entry,
    _get_cached_issue_search_page,
    _set_cached_issue_search_page,
    _get_cached_issue_search_count,
    _set_cached_issue_search_count,
)
from dispatcher.github_api import (  # noqa: F401 - re-exported dispatcher API
    get_authenticated_user,
    is_authored_by_user,
    resolve_authenticated_user,
)
from dispatcher.env_config import (  # noqa: F401 - re-exported dispatcher API
    validate_parallelism,
    validate_non_negative_integer,
    validate_review_period,
    get_env_non_negative_int,
    get_env_parallelism,
    get_env_zero_one_bool,
)
from dispatcher.issue_admin import (  # noqa: F401 - re-exported dispatcher API
    set_issue_assignee,
    clear_issue_assignees,
    get_issue_assignees,
    ensure_repo_label_exists,
    post_issue_comment,
    add_issue_label,
    remove_issue_label,
    add_pull_request_label,
    remove_pull_request_label,
    get_issue_comments,
    comment_author_login,
    close_issue,
)
from dispatcher.project_board import (  # noqa: F401 - re-exported dispatcher API
    get_project_item_state,
    get_project_item_id,
    get_item_status,
    get_project_field_info,
    get_cached_field_info,
    set_item_status,
)
from dispatcher.issue_queue import (  # noqa: F401 - re-exported dispatcher API
    get_issue_by_number,
    get_issue_claim_payload,
    get_issue_body,
    build_issue_search_query,
    normalize_github_issue_search_item,
    search_issues_with_label,
    fetch_issue_search_page,
    get_issue_search_page,
    count_issues_with_label,
    fetch_issue_search_count,
    get_issue_search_count,
    get_issues_with_label,
    get_issue_label_names,
    issue_has_label,
    issue_is_resumable,
    add_issue_label_to_payload,
    get_issue_payload_assignees,
    is_assigned_only_to_authenticated_user,
    cached_skip_blocks_authenticated_user,
    IssuePriorityTier,
    ISSUE_PRIORITY_TIERS,
    ISSUE_PRIORITY_TIERS_BY_NAME,
    get_issue_priority_tier,
    IssueQueueScanState,
    get_prioritized_issues_with_label,
    resolve_user_requested_only,
    get_user_requested_issue_excluded_authors,
    get_issue_author_login,
    filter_user_requested_issues,
)
from dispatcher.claim_preflight import (  # noqa: F401 - re-exported dispatcher API
    get_open_blocking_issue_numbers,
    _get_issue_node_project_status,
    _get_issue_node_project_item_state,
    _get_issue_node_assignees,
    _connection_has_next_page,
    _open_issue_numbers_from_connection,
    _project_item_status_preflight_fields,
    _assignees_preflight_fields,
    _extract_issue_claim_preflight,
    get_issue_claim_cache_observation_from_payload,
    refresh_issue_payload_for_claim,
    get_issue_claim_cache_observation_from_preflight,
    format_cached_issue_claim_skip,
    get_cached_issue_claim_skips,
    get_issue_claim_preflights,
    issue_needs_claim_preflight,
    get_issue_claim_preflights_or_empty,
    should_skip_issue_from_preflight,
    resolve_next_issue_claim_candidate_batch,
)
from dispatcher.issue_claiming import (  # noqa: F401 - re-exported dispatcher API
    is_issue_blocked,
    revert_claimed_issue,
    revert_issue_claim,
    revert_issue_claim_if_still_owned_by_user,
    try_claim_issue,
    try_claim_issue_with_local_lock,
)








@dataclass(frozen=True)
class ReviewQueueConfig:
    label: str
    limit: int


@dataclass(frozen=True)
class ReviewQueueSelection:
    ready: list[dict]
    failed: list[dict]
    waiting_count: int


@dataclass(frozen=True)
class CIRepairOutcome:
    """One structured failed-CI diagnosis. §FS-automated-pr-review"""

    decision: str
    review_verdict: LocalReviewVerdict | None = None
    workflow_run_ids: tuple[int, ...] = ()
    review_comment: str = ""












@dataclass(frozen=True)
class HumanInterventionCandidate:
    strategy_name: str | None
    workflow_status: str
    coverage: DynamicAccessCoverageSnapshot | None = None
    reason: str = "low_dynamic_access_coverage"


@dataclass(frozen=True)
class FailurePreservationResult:
    branch_name: str
    branch_url: str
    committed_changes: bool
    reviewable_worktree_path: str | None = None
    scratch_metrics_path: str | None = None
    copied_logs_destination: str | None = None
    copied_logs_destination_relpath: str | None = None
    fixture_mode: bool = False

    def to_json(self) -> dict:
        return {
            "branch_name": self.branch_name,
            "branch_url": self.branch_url,
            "committed_changes": self.committed_changes,
            "reviewable_worktree_path": self.reviewable_worktree_path,
            "scratch_metrics_path": self.scratch_metrics_path,
            "copied_logs_destination": self.copied_logs_destination,
            "copied_logs_destination_relpath": self.copied_logs_destination_relpath,
            "fixture_mode": self.fixture_mode,
        }


@dataclass(frozen=True)
class PublicationHandoff:
    script_name: str
    runner_name: str
    runner: Callable[[list[str]], None]
    argv: list[str]
    issue_number: int
    issue_label: str
    result_label: str
    coordinates: str | None
    current_coordinates: str | None
    new_version: str | None
    worktree_path: str
    scratch_metrics_path: str
    workflow_status: str | None
    chunked_dynamic_access_args: list[str]
    dynamic_access_exhaust_report_path: str | None
    chunked_dynamic_access_final: bool | None
    not_for_native_image: bool = False
    publication_kind: str | None = None
    coverage_follow_up_issue_number: int | None = None
    coverage_follow_up_class_count: int | None = None
    coverage_follow_up_class_threshold: int | None = None

    def to_json(self) -> dict:
        return {
            "script_name": self.script_name,
            "runner_name": self.runner_name,
            "argv": list(self.argv),
            "issue_number": self.issue_number,
            "issue_label": self.issue_label,
            "result_label": self.result_label,
            "coordinates": self.coordinates,
            "current_coordinates": self.current_coordinates,
            "new_version": self.new_version,
            "worktree_path": self.worktree_path,
            "scratch_metrics_path": self.scratch_metrics_path,
            "workflow_status": self.workflow_status,
            "chunked_dynamic_access_args": list(self.chunked_dynamic_access_args),
            "dynamic_access_exhaust_report_path": self.dynamic_access_exhaust_report_path,
            "chunked_dynamic_access_final": self.chunked_dynamic_access_final,
            "not_for_native_image": self.not_for_native_image,
            "publication_kind": self.publication_kind,
        }


@dataclass(frozen=True)
class WorkflowDriverInvocation:
    driver_name: str
    script_name: str
    runner_name: str
    runner: Callable[[list[str]], int]
    argv: list[str]
    issue_number: int
    issue_label: str
    coordinates: str | None
    current_coordinates: str | None
    new_version: str | None
    log_stage_name: str
    log_message: str
    failure_name: str

    def to_json(self) -> dict:
        return {
            "driver_name": self.driver_name,
            "script_name": self.script_name,
            "runner_name": self.runner_name,
            "argv": list(self.argv),
            "issue_number": self.issue_number,
            "issue_label": self.issue_label,
            "coordinates": self.coordinates,
            "current_coordinates": self.current_coordinates,
            "new_version": self.new_version,
        }



def validate_issue_processing_environment() -> None:
    """Validate the GraalVM environment required before issue processing can start.

    The requirement itself is defined once in `utility_scripts/host_requirements.py`
    and is also enforced by the startup gate (§FS-forge-host-requirements).
    """
    require_issue_graalvm_homes()





preservation_failed_worktree_paths: set[str] = set()







def get_pull_requests_with_label(
        label: str,
        fetch_limit: int,
        state: str = "open",
) -> list[dict]:
    """Fetch pull requests in one state that carry the given label."""
    return get_pull_requests_with_labels([label], fetch_limit, state=state)


def get_pull_requests_with_labels(
        labels: list[str],
        fetch_limit: int,
        state: str = "open",
) -> list[dict]:
    """Fetch pull requests in one state that carry all given labels."""
    unique_labels = list(dict.fromkeys(labels))
    label_args: list[str] = []
    for label in unique_labels:
        label_args.extend(["--label", label])
    label_description = "', '".join(unique_labels)
    print(
        f"\n[Fetching {state} pull requests with label(s) '{label_description}' from {REPO} "
        f"(fetched={fetch_limit})]"
    )
    data = gh_json(
        "pr", "list",
        "--repo", REPO,
        *label_args,
        "--state", state,
        "--limit", str(fetch_limit),
        "--json", "number,title,url,author,labels,body,headRefOid,mergedAt",
    )
    return data




def attach_pull_request_state(
        pull_request: dict,
        state_cache: dict[int, dict],
) -> dict:
    """Return a pull request payload enriched with review, merge, and CI state."""
    pr_number = pull_request.get("number")
    if not isinstance(pr_number, int):
        print("ERROR: Missing pull request number while fetching state.", file=sys.stderr)
        raise RuntimeError("Missing pull request number while fetching state")

    if pr_number not in state_cache:
        state_cache[pr_number] = get_pull_request_state(pr_number)

    enriched_pull_request = dict(pull_request)
    enriched_pull_request.update(state_cache[pr_number])
    return enriched_pull_request


def pull_request_has_label(pr: dict, label_name: str) -> bool:
    """Return True when the GitHub pull request payload contains the given label."""
    return issue_has_label(pr, label_name)


def get_pull_request_state(pr_number: int) -> dict:
    """Fetch the latest review and merge state for a pull request."""
    owner, repo_name = REPO.split("/")
    query = f"""
    query {{
      repository(owner: "{owner}", name: "{repo_name}") {{
        pullRequest(number: {pr_number}) {{
          id
          number
          url
          body
          author {{
            login
          }}
          headRepository {{
            nameWithOwner
          }}
          headRefOid
          headRefName
          isCrossRepository
          state
          mergedAt
          reviewDecision
          mergeStateStatus
          mergeable
          isMergeQueueEnabled
          autoMergeRequest {{
            enabledAt
          }}
          statusCheckRollup {{
            state
            contexts(first: 100) {{
              nodes {{
                __typename
                ... on CheckRun {{
                  name
                  status
                  conclusion
                  checkSuite {{
                    app {{
                      slug
                    }}
                    commit {{
                      oid
                    }}
                    workflowRun {{
                      event
                      workflow {{
                        name
                      }}
                      file {{
                        path
                      }}
                    }}
                  }}
                }}
              }}
            }}
          }}
          repository {{
            viewerDefaultMergeMethod
            mergeCommitAllowed
            rebaseMergeAllowed
            squashMergeAllowed
          }}
        }}
      }}
    }}
    """
    result = gh_json("api", "graphql", "-f", f"query={query}")
    pull_request = (
        result.get("data", {})
        .get("repository", {})
        .get("pullRequest", {})
    )
    if not isinstance(pull_request, dict) or not pull_request:
        print(f"ERROR: Missing state for pull request #{pr_number}.", file=sys.stderr)
        raise RuntimeError(f"Missing state for pull request #{pr_number}")
    return pull_request


def get_pull_request_reviews(pr_number: int) -> list[dict]:
    """Fetch submitted reviews for a pull request."""
    reviews = gh_json(
        "api",
        "--method",
        "GET",
        f"/repos/{REPO}/pulls/{pr_number}/reviews",
        "-F",
        "per_page=100",
    )
    if not isinstance(reviews, list):
        print(f"ERROR: Missing reviews for pull request #{pr_number}.", file=sys.stderr)
        raise RuntimeError(f"Missing reviews for pull request #{pr_number}")
    return reviews


def dismiss_requested_changes_reviews(pr_number: int, message: str | None = None) -> int:
    """Dismiss all requested-changes reviews on a pull request."""
    dismiss_message = (
        message
        or f"Dismissed because trusted maintainer marked this PR as '{LABEL_HUMAN_INTERVENTION_FIXED}'."
    )
    dismissed_count = 0
    for review in get_pull_request_reviews(pr_number):
        if not isinstance(review, dict) or review.get("state") != "CHANGES_REQUESTED":
            continue

        review_id = review.get("id")
        if not isinstance(review_id, int):
            print(
                f"ERROR: Missing review id for requested-changes review on PR #{pr_number}.",
                file=sys.stderr,
            )
            raise RuntimeError(f"Missing review id for requested-changes review on PR #{pr_number}")

        gh(
            "api",
            "--method",
            "PUT",
            f"/repos/{REPO}/pulls/{pr_number}/reviews/{review_id}/dismissals",
            "-f",
            f"message={dismiss_message}",
        )
        dismissed_count += 1

    return dismissed_count


def has_successful_pull_request_ci(pr: dict) -> bool:
    """Return True when the pull request's combined CI status is successful."""
    status_check_rollup = pr.get("statusCheckRollup")
    ci_state = status_check_rollup.get("state") if isinstance(status_check_rollup, dict) else None
    return ci_state == "SUCCESS"


def has_failed_pull_request_ci(pr: dict) -> bool:
    """Return True when the pull request's combined CI status is failed."""
    status_check_rollup = pr.get("statusCheckRollup")
    ci_state = status_check_rollup.get("state") if isinstance(status_check_rollup, dict) else None
    return ci_state in FAILED_CI_STATES


def _load_trusted_publisher_module(reachability_metadata_path: str) -> Any:
    """Load the default-branch publication validator used by Actions."""
    publisher_path = os.path.join(
        reachability_metadata_path,
        ".github",
        "scripts",
        "forge_pr_publisher",
        "publisher.py",
    )
    module_name = "_forge_trusted_pr_publisher"
    existing = sys.modules.get(module_name)
    if existing is not None and getattr(existing, "__file__", None) == publisher_path:
        return existing
    spec = importlib.util.spec_from_file_location(module_name, publisher_path)
    if spec is None or spec.loader is None:
        raise RuntimeError("Trusted Forge publisher module is unavailable")
    module = importlib.util.module_from_spec(spec)
    sys.modules[module_name] = module
    spec.loader.exec_module(module)
    return module


def validate_pull_request_publication(
        pull_request: dict,
        reachability_metadata_path: str,
) -> Any:
    """Validate the trusted descriptor on the exact current PR head.

    §FS-automated-pr-review
    """
    pr_number = pull_request.get("number")
    head_sha = pull_request.get("headRefOid")
    head_branch = pull_request.get("headRefName")
    head_repository = pull_request.get("headRepository")
    author = pull_request.get("author")
    if (
            not isinstance(pr_number, int)
            or not isinstance(head_sha, str)
            or not head_sha
            or not isinstance(head_branch, str)
            or not head_branch.startswith("ai/")
    ):
        raise ValueError(f"PR #{pr_number} has no eligible exact upstream ai/** head")
    if (
            pull_request.get("isCrossRepository")
            or not isinstance(head_repository, dict)
            or head_repository.get("nameWithOwner") != REPO
    ):
        raise ValueError(f"PR #{pr_number} head repository is not {REPO}")
    if not isinstance(author, dict) or author.get("login") != TRUSTED_FORGE_PUBLISHER_LOGIN:
        raise ValueError(f"PR #{pr_number} was not created by the trusted Forge publisher")
    if subprocess.run(
            ["git", "check-ref-format", "--branch", head_branch],
            cwd=reachability_metadata_path,
            check=False,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
    ).returncode != 0:
        raise ValueError(f"PR #{pr_number} head branch is not a valid git ref")

    run_git_transport(
        ["fetch", "--quiet", "origin", DEFAULT_WORKTREE_BASE_REF],
        cwd=reachability_metadata_path,
    )
    run_git_transport(
        [
            "fetch", "--quiet", "origin",
            f"+refs/heads/{head_branch}:refs/remotes/origin/{head_branch}",
        ],
        cwd=reachability_metadata_path,
    )
    producer_parts = head_branch.split("/", 2)
    if len(producer_parts) != 3 or not producer_parts[1]:
        raise ValueError(f"PR #{pr_number} head branch does not name a producer")
    publisher = _load_trusted_publisher_module(reachability_metadata_path)
    with contextlib.chdir(reachability_metadata_path):
        validated = publisher.validate_publication(
            head_sha=head_sha,
            branch=head_branch,
            actor=producer_parts[1],
            repository=REPO,
            pull_request_head=True,
        )

    descriptor = validated.descriptor
    publication_id = descriptor.get("publication_id")
    body = pull_request.get("body")
    trailer = f"Forge-Publication-ID: {publication_id}"
    if not isinstance(body, str) or trailer not in body.splitlines():
        raise ValueError(f"PR #{pr_number} does not carry its trusted publication identity")
    if descriptor.get("task_type") not in SUPPORTED_FORGE_REVIEW_TASK_TYPES:
        raise ValueError(f"PR #{pr_number} descriptor task is not eligible for Forge review")
    return validated


def publication_review_disposition(validated: Any) -> tuple[str, str | None]:
    """Return the exact-head descriptor decision executed by Forge."""
    descriptor = validated.descriptor
    if descriptor.get("task_type") == BENCHMARK_PUBLICATION_TASK_TYPE:
        return "approved", None
    review = descriptor.get("local_review")
    if not isinstance(review, dict):
        raise ValueError("Generated publication descriptor has no local review")
    decision = review.get("decision")
    action = review.get("action")
    if decision == "approved" and action is None:
        return decision, None
    if decision == "rejected" and action in {"human-intervention", "close"}:
        return decision, str(action)
    raise ValueError("Publication descriptor has an invalid local-review disposition")


def approve_pull_request_from_descriptor(pull_request: dict) -> None:
    """Approve the exact descriptor-validated PR head without an agent."""
    pr_number = pull_request.get("number")
    head_sha = pull_request.get("headRefOid")
    if not isinstance(pr_number, int) or not isinstance(head_sha, str) or not head_sha:
        raise RuntimeError(f"Missing head metadata for descriptor-approved PR #{pr_number}")
    gh(
        "api",
        "--method",
        "POST",
        f"/repos/{REPO}/pulls/{pr_number}/reviews",
        "-f",
        f"commit_id={head_sha}",
        "-f",
        "event=APPROVE",
        "-f",
        f"body={FORGE_APPROVAL_BODY_PREFIX}{head_sha}.",
    )
    pull_request["reviewDecision"] = "APPROVED"


def enable_pull_request_auto_merge(pull_request: dict) -> None:
    """Enable auto-merge without allowing a different head to be merged.

    §FS-automated-pr-review
    """
    pr_number = pull_request.get("number")
    head_sha = pull_request.get("headRefOid")
    if not isinstance(pr_number, int) or not isinstance(head_sha, str) or not head_sha:
        raise RuntimeError(f"Missing auto-merge metadata for PR #{pr_number}")
    if pull_request.get("autoMergeRequest") is not None:
        print(f"[Auto-merge is already enabled for approved PR #{pr_number}.]")
        return

    merge_args = [
        "pr", "merge", str(pr_number),
        "--repo", REPO,
        "--auto",
        "--match-head-commit", head_sha,
    ]
    if not pull_request.get("isMergeQueueEnabled"):
        merge_args.append(resolve_pull_request_merge_flag(pull_request))
    gh(*merge_args)
    pull_request["autoMergeRequest"] = {"enabledAt": "forge"}
    print(f"[Enabled auto-merge for approved PR #{pr_number} at {head_sha}.]")


def disable_pull_request_auto_merge(pull_request: dict) -> None:
    """Disable an active auto-merge request before human intervention."""
    pr_number = pull_request.get("number")
    if not isinstance(pr_number, int):
        raise RuntimeError("Cannot disable auto-merge without a pull request number")
    if pull_request.get("autoMergeRequest") is None:
        return
    gh("pr", "merge", str(pr_number), "--repo", REPO, "--disable-auto")
    pull_request["autoMergeRequest"] = None
    print(f"[Disabled auto-merge for PR #{pr_number}.]")


def dismiss_forge_approval_reviews(
        pull_request: dict,
        message: str = "Forge withdrew approval because this pull request needs human intervention.",
) -> int:
    """Dismiss only exact-head approvals previously submitted by Forge."""
    pr_number = pull_request.get("number")
    if not isinstance(pr_number, int):
        raise RuntimeError("Cannot dismiss Forge approval without a pull request number")
    dismissed_count = 0
    for review in get_pull_request_reviews(pr_number):
        if not isinstance(review, dict) or review.get("state") != "APPROVED":
            continue
        if not str(review.get("body") or "").startswith(FORGE_APPROVAL_BODY_PREFIX):
            continue
        review_id = review.get("id")
        if not isinstance(review_id, int):
            raise RuntimeError(f"Forge approval on PR #{pr_number} has no review id")
        gh(
            "api",
            "--method", "PUT",
            f"/repos/{REPO}/pulls/{pr_number}/reviews/{review_id}/dismissals",
            "-f", f"message={message}",
        )
        dismissed_count += 1

    if dismissed_count:
        pull_request["reviewDecision"] = "REVIEW_REQUIRED"
        print(f"[Dismissed {dismissed_count} Forge approval(s) on PR #{pr_number}.]")
    return dismissed_count


def ensure_pull_request_unapproved(pull_request: dict) -> None:
    """Withdraw Forge merge readiness. §FS-automated-pr-review"""
    disable_pull_request_auto_merge(pull_request)
    dismiss_forge_approval_reviews(pull_request)


def reconcile_rejected_publication(
        pull_request: dict,
        validated: Any,
) -> None:
    """Apply the exact rejected action idempotently without waiting for CI."""
    decision, action = publication_review_disposition(validated)
    if decision != "rejected":
        return
    pr_number = pull_request.get("number")
    if not isinstance(pr_number, int):
        raise RuntimeError("Rejected publication is missing its pull request number")
    ensure_pull_request_unapproved(pull_request)
    if pull_request_has_label(pull_request, LABEL_FORGE_MERGE_FOLLOW_UP):
        remove_pull_request_label(pr_number, LABEL_FORGE_MERGE_FOLLOW_UP)
    if action == "human-intervention":
        add_pull_request_label(pr_number, LABEL_HUMAN_INTERVENTION)
        print(f"[Kept rejected PR #{pr_number} open for human intervention.]")
        return
    if action != "close":
        raise ValueError(f"Rejected publication has unsupported action {action!r}")

    issue_number = validated.descriptor.get("issue_number")
    if not isinstance(issue_number, int):
        raise RuntimeError(f"Rejected close PR #{pr_number} has no linked issue")

    review = validated.descriptor["local_review"]
    if not any(
            LOCAL_REVIEW_CLOSE_MARKER in str(comment.get("body") or "")
            for comment in get_issue_comments(pr_number)
    ):
        post_issue_comment(
            pr_number,
            "\n\n".join([
                "This pull request is closed because of the local reviewer's decision.",
                f"**{review['finding_title']}**\n\n{review['finding_body']}",
                str(review["review_comment"]),
                LOCAL_REVIEW_CLOSE_MARKER,
            ]),
        )
    add_issue_label(issue_number, LABEL_LIBRARY_UNSUPPORTED_VERSION)
    close_issue(issue_number, f"local review rejected PR #{pr_number} as unsupported")
    gh("pr", "close", str(pr_number), "--repo", REPO)
    print(f"[Closed rejected PR #{pr_number} and unsupported issue #{issue_number}.]")


def get_pull_request_workflow_runs(head_sha: str) -> list[dict]:
    """Return GitHub Actions workflow runs for the pull request head commit."""
    data = gh_json(
        "api",
        "--method",
        "GET",
        f"/repos/{REPO}/actions/runs",
        "-F",
        f"head_sha={head_sha}",
        "-F",
        "event=pull_request",
        "-F",
        "per_page=100",
    )
    workflow_runs = data.get("workflow_runs") if isinstance(data, dict) else None
    if not isinstance(workflow_runs, list):
        print(f"ERROR: Missing workflow runs for pull request head {head_sha}.", file=sys.stderr)
        raise RuntimeError(f"Missing workflow runs for pull request head {head_sha}")
    return workflow_runs


def get_failed_workflow_run_ids(workflow_runs: list[dict]) -> list[int]:
    """Return failed run IDs from exact-head evidence. §FS-automated-pr-review"""
    return [
        int(workflow_run["id"])
        for workflow_run in workflow_runs
        if (
            isinstance(workflow_run, dict)
            and isinstance(workflow_run.get("id"), int)
            and workflow_run.get("conclusion") in RERUNNABLE_WORKFLOW_RUN_CONCLUSIONS
        )
    ]


def rerun_failed_pull_request_workflow_jobs(
        pr_number: int,
        head_sha: str,
        requested_run_ids: tuple[int, ...],
) -> int:
    """Rerun only agent-selected failed jobs on this head. §FS-automated-pr-review"""
    pull_request = get_pull_request_state(pr_number)
    if (
            pull_request.get("headRefOid") != head_sha
            or pull_request.get("state") not in {None, "OPEN"}
    ):
        print(
            f"[Skipping transient rerun for PR #{pr_number}: "
            f"{head_sha} is no longer its open head.]"
        )
        return 0
    workflow_runs = get_pull_request_workflow_runs(head_sha)
    failed_run_ids = set(get_failed_workflow_run_ids(workflow_runs))
    stale_run_ids = [run_id for run_id in requested_run_ids if run_id not in failed_run_ids]
    if stale_run_ids:
        print(
            f"[Skipping transient rerun for PR #{pr_number}: workflow run(s) "
            f"{', '.join(str(run_id) for run_id in stale_run_ids)} are no longer failed "
            f"on head {head_sha}.]"
        )
        return 0

    for run_id in requested_run_ids:
        print(
            f"[Rerunning agent-classified transient jobs for PR #{pr_number}, "
            f"workflow run {run_id}.]"
        )
        gh(
            "api",
            "--method",
            "POST",
            f"/repos/{REPO}/actions/runs/{run_id}/rerun-failed-jobs",
        )

    return len(requested_run_ids)


def resolve_pull_request_merge_flag(pr: dict) -> str:
    """Resolve the merge method flag, preferring squash merges by default."""
    repository = pr.get("repository")
    if not isinstance(repository, dict):
        return "--squash"

    merge_flag_by_method = {
        "SQUASH": ("squashMergeAllowed", "--squash"),
        "MERGE": ("mergeCommitAllowed", "--merge"),
        "REBASE": ("rebaseMergeAllowed", "--rebase"),
    }
    for method in ("SQUASH", "MERGE", "REBASE"):
        allowed_key, merge_flag = merge_flag_by_method[method]
        if repository.get(allowed_key):
            return merge_flag

    return "--squash"


def is_metadata_index_file_path(path: str) -> bool:
    """Return True when a repository path is a library index file."""
    parts = path.split("/")
    return len(parts) == 4 and parts[0] == "metadata" and parts[3] == "index.json"


def get_pull_request_changed_files(pr_number: int) -> list[str]:
    """Return changed file paths for a pull request."""
    result = gh(
        "pr",
        "diff",
        str(pr_number),
        "--repo",
        REPO,
        "--name-only",
        quiet=True,
    )
    return [line.strip() for line in result.stdout.splitlines() if line.strip()]


def get_pull_request_changed_index_files(pr_number: int) -> list[str]:
    """Return changed library index files for a pull request."""
    return [
        path for path in get_pull_request_changed_files(pr_number)
        if is_metadata_index_file_path(path)
    ]


def output_tail(output: str | None, max_lines: int = 80) -> str:
    """Return the final lines from command output."""
    if not output:
        return ""
    return "\n".join(output.strip().splitlines()[-max_lines:])


def run_checked_command(
        command: list[str],
        cwd: str,
        error_message: str,
) -> subprocess.CompletedProcess:
    """Run a command and report its captured output on failure."""
    try:
        return subprocess.run(
            command,
            cwd=cwd,
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
        )
    except subprocess.CalledProcessError as exc:
        tail = output_tail(exc.stdout)
        print(f"ERROR: {error_message}" + (f":\n{tail}" if tail else ""), file=sys.stderr)
        raise


def validate_index_files_on_current_master_candidate(
        pr_number: int,
        head_ref_oid: str,
        reachability_metadata_path: str,
) -> None:
    """Validate index files after applying the pull request head to current master."""
    repo_root = get_repo_root()
    validation_worktrees_root = os.path.join(
        repo_root,
        "local_repositories",
        SCRATCH_FINAL_INDEX_VALIDATION_WORKTREE_DIRNAME,
    )
    os.makedirs(validation_worktrees_root, exist_ok=True)

    validation_run_id = f"index-pr-{pr_number}-{uuid.uuid4().hex[:8]}"
    validation_worktree_path = os.path.join(validation_worktrees_root, validation_run_id)

    fetch_review_base_ref(reachability_metadata_path)
    create_detached_worktree(
        reachability_metadata_path,
        validation_worktree_path,
        f"origin/{DEFAULT_WORKTREE_BASE_REF}",
        f"Failed to create final index validation worktree for PR #{pr_number}",
    )
    try:
        try:
            run_git_transport(
                ["fetch", "--quiet", "origin", f"refs/pull/{pr_number}/head"],
                cwd=validation_worktree_path,
            )
        except GitTransportError as exc:
            print(
                f"ERROR: Failed to fetch PR #{pr_number} head for final index validation: {exc}",
                file=sys.stderr,
            )
            raise
        fetched_head = run_checked_command(
            ["git", "rev-parse", "FETCH_HEAD"],
            validation_worktree_path,
            f"Failed to resolve fetched PR #{pr_number} head for final index validation",
        ).stdout.strip()
        if fetched_head != head_ref_oid:
            print(
                (
                    f"ERROR: PR #{pr_number} head changed before final index validation: "
                    f"expected {head_ref_oid}, fetched {fetched_head}."
                ),
                file=sys.stderr,
            )
            raise RuntimeError(f"Pull request #{pr_number} head changed before final index validation")

        run_checked_command(
            ["git", "merge", "--no-commit", "--no-ff", "FETCH_HEAD"],
            validation_worktree_path,
            (
                f"Failed to merge PR #{pr_number} into current "
                f"{DEFAULT_WORKTREE_BASE_REF} for final index validation"
            ),
        )
        print(
            f"[Validating all index files on current {DEFAULT_WORKTREE_BASE_REF} "
            f"plus PR #{pr_number}.]"
        )
        run_checked_command(
            ["./gradlew", "validateIndexFiles", "-Pcoordinates=all", "--stacktrace"],
            validation_worktree_path,
            f"Final index validation failed for PR #{pr_number}",
        )
    finally:
        remove_worktree(reachability_metadata_path, validation_worktree_path)


def validate_pull_request_indexes_before_merge(
        pr_number: int,
        head_ref_oid: str,
        reachability_metadata_path: str,
) -> None:
    """Run final-tree index validation for pull requests that change index files."""
    changed_index_files = get_pull_request_changed_index_files(pr_number)
    if not changed_index_files:
        return

    print(
        f"[PR #{pr_number} changes {len(changed_index_files)} index file(s); "
        f"running final index validation before merge.]"
    )
    validate_index_files_on_current_master_candidate(
        pr_number,
        head_ref_oid,
        reachability_metadata_path,
    )


def resolve_non_final_chunked_dynamic_access_issue(pr: dict) -> int | None:
    """Return the linked issue for a non-final chunked dynamic-access PR body."""
    body = pr.get("body")
    if not isinstance(body, str):
        return None
    if "Chunked dynamic-access: yes" not in body:
        return None
    if re.search(r"(?m)^Fixes:\s*#\d+\b", body):
        return None
    match = re.search(r"(?m)^Refs:\s*#(\d+)\b", body)
    if match is None:
        return None
    return int(match.group(1))


def apply_chunked_dynamic_access_merge_follow_up(pr: dict) -> None:
    """Restore a merged non-final chunk issue for a clean next chunk.

    §FS-forge-chunked-dynamic-access
    """
    issue_number = resolve_non_final_chunked_dynamic_access_issue(pr)
    if issue_number is None:
        return

    item_id = get_project_item_id(issue_number)
    if item_id is None:
        raise RuntimeError(
            f"Chunked dynamic-access PR #{pr.get('number')} references issue #{issue_number}, "
            f"but the issue is not linked to project {PROJECT_NUMBER}."
        )
    issue: dict = get_issue_claim_payload(issue_number)
    for label_name in (LABEL_HUMAN_INTERVENTION, LABEL_RESUMABLE):
        if issue_has_label(issue, label_name):
            remove_issue_label(issue_number, label_name)
    set_item_status(item_id, STATUS_TODO)
    clear_issue_assignees(issue_number)
    invalidate_issue_claim_cache_entry(issue_number)
    log_stage(
        "chunked-dynamic-access",
        f"Released issue #{issue_number} for the next chunk after PR #{pr.get('number')} merged.",
    )


def pull_request_needs_merge_follow_up(pull_request: dict) -> bool:
    """Return whether a GitHub-completed merge needs Forge issue bookkeeping."""
    return (
        resolve_non_final_chunked_dynamic_access_issue(pull_request) is not None
        or bool(extract_follow_up_issue_numbers(pull_request.get("body")))
    )


def mark_pull_request_merge_follow_up_pending(pull_request: dict) -> None:
    """Persist post-merge work before auto-merge completes. §FS-automated-pr-review"""
    if not pull_request_needs_merge_follow_up(pull_request):
        return
    if pull_request_has_label(pull_request, LABEL_FORGE_MERGE_FOLLOW_UP):
        return
    pr_number = pull_request.get("number")
    if not isinstance(pr_number, int):
        raise RuntimeError("Cannot mark merge follow-up without a pull request number")
    add_pull_request_label(pr_number, LABEL_FORGE_MERGE_FOLLOW_UP)
    labels = pull_request.setdefault("labels", [])
    if isinstance(labels, list):
        labels.append({"name": LABEL_FORGE_MERGE_FOLLOW_UP})


def apply_unblocked_issue_merge_follow_up(pr: dict) -> None:
    """Release issues parked behind a PR after that PR merges.

    The shared `Forge-Unblocks-Issue` trailer releases both library-update alias
    successors and deferred Java-fix coverage issues from `In Progress` to
    `Todo`. §FS-library-update-tested-version-split,
    §AR-forge-driver-queues.3
    """
    for issue_number in extract_follow_up_issue_numbers(pr.get("body")):
        item_id = get_project_item_id(issue_number)
        if item_id is None:
            raise RuntimeError(
                f"PR #{pr.get('number')} unblocks issue #{issue_number}, "
                f"but the issue is not linked to project {PROJECT_NUMBER}."
            )
        set_item_status(item_id, STATUS_TODO)
        clear_issue_assignees(issue_number)
        invalidate_issue_claim_cache_entry(issue_number)
        log_stage(
            "issue-unblock",
            f"Released issue #{issue_number} after PR #{pr.get('number')} merged.",
        )


def reconcile_auto_merged_pull_request_follow_ups(fetch_limit: int = 100) -> None:
    """Apply durable follow-ups for auto-merged PRs. §FS-automated-pr-review"""
    merged_pull_requests = get_pull_requests_with_labels(
        [LABEL_FORGE_MERGE_FOLLOW_UP],
        fetch_limit,
        state="merged",
    )
    for pull_request in merged_pull_requests:
        pr_number = pull_request.get("number")
        if not isinstance(pr_number, int):
            continue
        try:
            apply_chunked_dynamic_access_merge_follow_up(pull_request)
            apply_unblocked_issue_merge_follow_up(pull_request)
            remove_pull_request_label(pr_number, LABEL_FORGE_MERGE_FOLLOW_UP)
            print(f"[Reconciled linked issues after auto-merge of PR #{pr_number}.]")
        except Exception as exc:
            print(
                f"ERROR: Failed auto-merge follow-up for PR #{pr_number}: {exc!r}",
                file=sys.stderr,
            )


def is_pull_request_conflicting(pull_request: dict) -> bool:
    """Return True when GitHub reports the pull request as conflicting with its base."""
    return pull_request.get("mergeable") == "CONFLICTING"


_BENCHMARK_RESULTS_PATH_PATTERN = re.compile(
    r"^code-coverage-benchmarks/[^/]+/[^/]+/[^/]+\.json$"
)


def _entries_by_run_id(entries: list) -> dict:
    """Key benchmark result entries by runId, rejecting malformed shapes."""
    keyed: dict = {}
    for entry in entries:
        run_id = entry.get("runId") if isinstance(entry, dict) else None
        if not isinstance(run_id, str) or run_id in keyed:
            raise ValueError("entries are not uniquely keyed by runId")
        keyed[run_id] = entry
    return keyed


def resolve_benchmark_results_conflict(worktree_path: str, path: str) -> bool:
    """Union-merge one conflicted benchmark results file by run ID.

    The file's entries are keyed by run ID and the only legal edit is adding
    one, so concurrent publications conflict textually while never actually
    disagreeing; the resolution is the base side's entries plus the entries
    the head added (§FS-automated-pr-review). It may never modify or drop an
    existing entry, and a run ID on both sides with different content is a
    real disagreement, so this returns False and the caller escalates.
    """
    try:
        stages = [
            json.loads(
                run_checked_command(
                    ["git", "show", f":{stage}:{path}"],
                    worktree_path,
                    f"Failed to read merge stage {stage} of {path}",
                ).stdout
            )
            for stage in (1, 2, 3)
        ]
        if not all(isinstance(stage, list) for stage in stages):
            return False
        ancestor, head, base = (_entries_by_run_id(stage) for stage in stages)
    except (RuntimeError, ValueError, json.JSONDecodeError):
        return False

    added = {run_id: entry for run_id, entry in head.items() if run_id not in ancestor}
    # Everything the head did not add must match the merge ancestor exactly.
    if {run_id: entry for run_id, entry in head.items() if run_id not in added} != ancestor:
        return False
    for run_id, entry in added.items():
        if run_id in base and base[run_id] != entry:
            return False

    merged = list(base.values()) + [
        entry for run_id, entry in added.items() if run_id not in base
    ]
    merged.sort(key=lambda entry: (entry["timestamp"], entry["runId"]))
    absolute_path = os.path.join(worktree_path, path)
    with open(absolute_path, "w", encoding="utf-8") as destination:
        json.dump(merged, destination, indent=2, ensure_ascii=False)
        destination.write("\n")
    run_checked_command(
        ["git", "add", path],
        worktree_path,
        f"Failed to stage the resolved {path}",
    )
    return True


def resolve_pull_request_merge_conflict(
        pull_request: dict,
        reachability_metadata_path: str,
) -> bool:
    """Merge the base branch into a conflicting PR head and push what git resolved.

    Returns True only when the merge left no conflict behind and the result
    reached the head branch. Anything git could not resolve on its own is a real
    disagreement over content and stays for a maintainer (§FS-automated-pr-review).
    """
    pr_number = pull_request.get("number")
    head_ref_name = pull_request.get("headRefName")
    head_ref_oid = pull_request.get("headRefOid")
    if not isinstance(pr_number, int) or not isinstance(head_ref_name, str) or not head_ref_name:
        print(f"ERROR: Missing head branch metadata for pull request #{pr_number}.", file=sys.stderr)
        raise RuntimeError(f"Missing head branch metadata for pull request #{pr_number}")
    if pull_request.get("isCrossRepository"):
        print(
            f"[Leaving PR #{pr_number} conflicting: its head branch lives in a fork "
            "that Forge cannot push to.]"
        )
        return False

    repo_root = get_repo_root()
    conflict_worktrees_root = os.path.join(
        repo_root,
        "local_repositories",
        SCRATCH_CONFLICT_RESOLUTION_WORKTREE_DIRNAME,
    )
    os.makedirs(conflict_worktrees_root, exist_ok=True)
    worktree_path = os.path.join(
        conflict_worktrees_root,
        f"conflict-pr-{pr_number}-{uuid.uuid4().hex[:8]}",
    )

    fetch_review_base_ref(reachability_metadata_path)
    run_git_transport(
        ["fetch", "--quiet", "origin", f"refs/pull/{pr_number}/head"],
        cwd=reachability_metadata_path,
    )
    create_detached_worktree(
        reachability_metadata_path,
        worktree_path,
        "FETCH_HEAD",
        f"Failed to create conflict resolution worktree for PR #{pr_number}",
    )
    try:
        fetched_head = run_checked_command(
            ["git", "rev-parse", "HEAD"],
            worktree_path,
            f"Failed to resolve fetched PR #{pr_number} head for conflict resolution",
        ).stdout.strip()
        if fetched_head != head_ref_oid:
            print(
                (
                    f"[Leaving PR #{pr_number} conflicting: head changed before resolution, "
                    f"expected {head_ref_oid}, fetched {fetched_head}.]"
                )
            )
            return False

        # `--no-ff` so a head that is merely behind the base can never fast-forward
        # onto it, which would leave the pull request with nothing to merge.
        merge_result = subprocess.run(
            [
                "git", "merge", "--no-ff",
                f"origin/{DEFAULT_WORKTREE_BASE_REF}",
                "-m", f"Merge {DEFAULT_WORKTREE_BASE_REF} into {head_ref_name}",
            ],
            cwd=worktree_path,
            check=False,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
        )
        if merge_result.returncode != 0:
            conflicted_paths = run_checked_command(
                ["git", "diff", "--name-only", "--diff-filter=U"],
                worktree_path,
                f"Failed to list unresolved conflicts for PR #{pr_number}",
            ).stdout.split()
            # Benchmark results conflict textually while never disagreeing, so
            # they are resolved, not escalated (§FS-automated-pr-review).
            resolvable = bool(conflicted_paths) and all(
                _BENCHMARK_RESULTS_PATH_PATTERN.match(path)
                for path in conflicted_paths
            )
            if resolvable and all(
                resolve_benchmark_results_conflict(worktree_path, path)
                for path in conflicted_paths
            ):
                run_checked_command(
                    ["git", "commit", "--no-edit"],
                    worktree_path,
                    f"Failed to conclude the resolved merge for PR #{pr_number}",
                )
            else:
                subprocess.run(["git", "merge", "--abort"], cwd=worktree_path, check=False)
                print(
                    f"[Leaving PR #{pr_number} conflicting: git could not resolve "
                    f"{', '.join(conflicted_paths) or 'the merge'}.]"
                )
                return False

        ensure_pull_request_unapproved(pull_request)
        run_git_transport(
            ["push", "origin", f"HEAD:refs/heads/{head_ref_name}"],
            cwd=worktree_path,
        )
        print(
            f"[Merged {DEFAULT_WORKTREE_BASE_REF} into PR #{pr_number} and pushed it; "
            "its checks restart, so the merge belongs to a later pass.]"
        )
        return True
    finally:
        remove_worktree(reachability_metadata_path, worktree_path)


def _ci_repair_changed_paths(worktree_path: str) -> list[str]:
    """Return tracked and untracked paths changed by the CI-repair agent."""
    tracked = run_checked_command(
        ["git", "diff", "--name-only", "--diff-filter=ACMRTD", "HEAD"],
        worktree_path,
        "Failed to list CI-repair changes",
    ).stdout.splitlines()
    untracked = run_checked_command(
        ["git", "ls-files", "--others", "--exclude-standard"],
        worktree_path,
        "Failed to list untracked CI-repair files",
    ).stdout.splitlines()
    return sorted(set(path for path in [*tracked, *untracked] if path))


def ensure_infrastructure_issue(evidence: dict[str, str]) -> tuple[int, str]:
    """Find one exact-title open infrastructure issue or create it."""
    candidates = gh_json(
        "issue", "list",
        "--repo", REPO,
        "--state", "open",
        "--search", f"{evidence['title']} in:title",
        "--limit", "100",
        "--json", "number,title,url",
    )
    if not isinstance(candidates, list):
        raise RuntimeError("Infrastructure issue search returned invalid data")
    for candidate in candidates:
        if isinstance(candidate, dict) and candidate.get("title") == evidence["title"]:
            return int(candidate["number"]), str(candidate["url"])
    created = gh_json(
        "api",
        "--method", "POST",
        f"/repos/{REPO}/issues",
        "-f", f"title={evidence['title']}",
        "-f", f"body={evidence['body']}",
    )
    if not isinstance(created, dict):
        raise RuntimeError("Infrastructure issue creation returned invalid data")
    return int(created["number"]), str(created["html_url"])


def build_ci_repair_prompt(
        pr_number: int,
        descriptor: dict[str, Any],
        evidence_path: str,
        verdict_path: str,
) -> str:
    """Build the failed-CI repair and authoritative re-review prompt."""
    task_type = str(descriptor["task_type"])
    skill = REVIEW_SKILLS_BY_TASK_TYPE.get(task_type)
    skill_instruction = (
        f"Apply every relevant enumerated rule in skills/{skill}/SKILL.md."
        if skill is not None
        else "This is a benchmark-result publication; preserve its measured result."
    )
    return "\n".join([
        f"Repair failed required CI for pull request #{pr_number}.",
        f"Read the exact descriptor and failed-check evidence from {evidence_path}.",
        "Use gh pr checks and gh run view when more failure output is needed. Do not "
        "rerun jobs or make any other GitHub mutation; Forge owns those actions.",
        skill_instruction,
        "Apply the first matching disposition in §root/FS-contribution-contract.5.",
        "",
        "Edit only the contribution's own library files. Do not edit shared build logic, "
        "the test harness, workflows, another coordinate, the publication descriptor, or "
        "forge/FINDINGS.md. Run the failed checks needed to verify your repair. Then review "
        "the complete resulting diff with the same responsibility as local_review.",
        "",
        f"Write exactly one JSON verdict to {verdict_path}. If the evidence proves the "
        "failure is transient, make no edits and use decision transient with the exact "
        "current-head workflow_run_ids whose failed jobs Forge should rerun. Use decision "
        "approved and omit action only when you repaired the contribution and the resulting "
        "tree is ready. Otherwise use decision rejected with action human-intervention or "
        "close and a non-empty finding. For a shared repository defect, also add "
        "infrastructure_issue with non-empty title and body evidence.",
        json.dumps({
            "decision": "approved",
            "review_comment": "What failed, what you checked, and the resulting decision.",
            "finding_title": "Reusable finding title, or empty when there was no finding.",
            "finding_body": "Failure evidence and rule, or empty when there was no finding.",
            "fix_note": "What you changed and how you verified it.",
        }, indent=2),
        "For a transient failure, instead write:",
        json.dumps({
            "decision": "transient",
            "workflow_run_ids": [123456789],
            "review_comment": "Evidence that the selected failed run is transient.",
        }, indent=2),
        "Do not commit or push; Forge owns the descriptor, findings ledger, commit, and push.",
    ])


def _read_ci_repair_outcome(verdict_path: str) -> CIRepairOutcome | None:
    """Read a repair, rejection, or transient-rerun diagnosis."""
    try:
        with open(verdict_path, "r", encoding="utf-8") as verdict_file:
            payload = json.load(verdict_file)
    except (OSError, json.JSONDecodeError):
        return None
    if not isinstance(payload, dict):
        return None
    if payload.get("decision") != "transient":
        review_verdict = _read_verdict(verdict_path)
        if review_verdict is None:
            return None
        return CIRepairOutcome(
            decision=review_verdict.decision,
            review_verdict=review_verdict,
        )

    run_ids = payload.get("workflow_run_ids")
    review_comment = payload.get("review_comment")
    if (
            not isinstance(run_ids, list)
            or not run_ids
            or any(not isinstance(run_id, int) or run_id <= 0 for run_id in run_ids)
            or len(set(run_ids)) != len(run_ids)
            or not isinstance(review_comment, str)
            or not review_comment.strip()
            or len(review_comment) > 20_000
    ):
        return None
    return CIRepairOutcome(
        decision="transient",
        workflow_run_ids=tuple(run_ids),
        review_comment=review_comment.strip(),
    )


def _ci_repair_path_is_allowed(path: str, descriptor: dict[str, Any]) -> bool:
    """Enforce the exact file set. §root/FS-contribution-contract.2"""
    library = descriptor["library"]
    group = str(library["group"])
    artifact = str(library["artifact"])
    version = str(library["version"])
    exact_paths = {
        f"metadata/{group}/{artifact}/index.json",
        f"metadata/{group}/{artifact}/{version}/reachability-metadata.json",
    }
    allowed_prefixes = (
        f"tests/src/{group}/{artifact}/{version}/",
        f"stats/{group}/{artifact}/{version}/",
        "tests/tck-build-logic/src/main/resources/allowed-docker-images/",
    )
    return path in exact_paths or path.startswith(allowed_prefixes)


def repair_failed_ci_pull_request(
        pull_request: dict,
        validated: Any,
        reachability_metadata_path: str,
) -> bool:
    """Diagnose failed CI, then rerun transient jobs or push one repaired decision."""
    pr_number = pull_request.get("number")
    head_sha = pull_request.get("headRefOid")
    head_branch = pull_request.get("headRefName")
    if (
            not isinstance(pr_number, int)
            or not isinstance(head_sha, str)
            or not isinstance(head_branch, str)
    ):
        raise RuntimeError("Failed-CI repair requires complete pull request head metadata")

    worktree_root = os.path.join(
        FORGE_DIR, "local_repositories", "forge_ci_repair_worktrees",
    )
    os.makedirs(worktree_root, exist_ok=True)
    worktree_path = os.path.join(
        worktree_root, f"ci-repair-pr-{pr_number}-{uuid.uuid4().hex[:8]}",
    )
    create_detached_worktree(
        reachability_metadata_path,
        worktree_path,
        head_sha,
        f"Failed to create CI-repair worktree for PR #{pr_number}",
    )
    try:
        session_id = new_session_id()
        evidence_dir = os.path.join(worktree_path, f".forge-ci-repair-{uuid.uuid4().hex[:8]}")
        os.makedirs(evidence_dir)
        evidence_path = os.path.join(evidence_dir, "evidence.json")
        verdict_path = os.path.join(evidence_dir, "verdict.json")
        workflow_runs = get_pull_request_workflow_runs(head_sha)
        with open(evidence_path, "w", encoding="utf-8") as evidence_file:
            json.dump(
                {
                    "pull_request": {
                        "number": pr_number,
                        "head_sha": head_sha,
                        "head_branch": head_branch,
                    },
                    "descriptor": validated.descriptor,
                    "workflow_runs": workflow_runs,
                    "failed_checks": (
                        pull_request.get("statusCheckRollup", {})
                        .get("contexts", {})
                        .get("nodes", [])
                    ),
                },
                evidence_file,
                indent=2,
                sort_keys=True,
            )
            evidence_file.write("\n")

        selection = get_analysis_agent()
        trusted_environment = dict(os.environ)
        trusted_environment["_FORGE_AGENT_ALLOW_GITHUB_ACCESS"] = "1"
        result = analysis_agent_run(
            working_dir=worktree_path,
            context=build_ci_repair_prompt(
                pr_number, validated.descriptor, evidence_path, verdict_path,
            ),
            task_type="pr-ci-repair",
            library=str(validated.descriptor["library"]["coordinates"]),
            timeout=REVIEW_TIMEOUT_SECONDS,
            environment=trusted_environment,
            thinking_level="xhigh",
        )
        # The identifier is what the descriptor and the pull request carry; the
        # path is printed for the operator only. §FS-durable-generation-logs
        print(
            f"[CI repair session {session_id}; log: {display_log_path(result.log_path)}]"
        )
        outcome = _read_ci_repair_outcome(verdict_path) if result.return_code == 0 else None
        shutil.rmtree(evidence_dir, ignore_errors=True)
        changed_paths = _ci_repair_changed_paths(worktree_path)
        verdict = outcome.review_verdict if outcome is not None else None

        if outcome is not None and outcome.decision == "transient":
            invalid_run_ids = sorted(
                set(outcome.workflow_run_ids) - set(get_failed_workflow_run_ids(workflow_runs))
            )
            if changed_paths:
                verdict = LocalReviewVerdict(
                    decision="rejected",
                    action="human-intervention",
                    review_comment=outcome.review_comment,
                    finding_title="Transient CI diagnosis changed the contribution",
                    finding_body=(
                        "A transient verdict must leave the exact failing tree unchanged, but "
                        f"the agent changed: {', '.join(changed_paths)}"
                    ),
                    fix_note="The out-of-scope transient edits were discarded.",
                )
            elif invalid_run_ids:
                verdict = LocalReviewVerdict(
                    decision="rejected",
                    action="human-intervention",
                    review_comment=outcome.review_comment,
                    finding_title="Transient CI diagnosis selected unrelated runs",
                    finding_body=(
                        "The transient verdict requested workflow runs that were not failed "
                        f"on the exact reviewed head: {', '.join(map(str, invalid_run_ids))}"
                    ),
                    fix_note="No workflow was rerun.",
                )
            else:
                rerun_count = rerun_failed_pull_request_workflow_jobs(
                    pr_number,
                    head_sha,
                    outcome.workflow_run_ids,
                )
                print(
                    f"[CI repair agent classified {rerun_count} workflow run(s) as transient "
                    f"for approved PR #{pr_number}; waiting for the rerun.]"
                )
                return True

        if verdict is None:
            verdict = LocalReviewVerdict(
                decision="rejected",
                action="human-intervention",
                review_comment=(
                    "Forge could not obtain a valid CI-repair verdict in review session "
                    f"{session_id}."
                ),
                finding_title="CI repair reviewer unavailable",
                finding_body="The failed-CI repair turn did not return a readable decision.",
                fix_note="",
            )
        elif verdict.decision == "approved" and not changed_paths:
            verdict = LocalReviewVerdict(
                decision="rejected",
                action="human-intervention",
                review_comment=verdict.review_comment,
                finding_title="Failed CI was not repaired",
                finding_body="The CI-repair reviewer approved without changing the failing head.",
                fix_note=verdict.fix_note,
            )

        infrastructure_evidence = verdict.infrastructure_issue

        invalid_paths = [
            path for path in changed_paths
            if not _ci_repair_path_is_allowed(path, validated.descriptor)
        ]
        allowed_docker_paths = [
            path for path in changed_paths
            if path.startswith(
                "tests/tck-build-logic/src/main/resources/allowed-docker-images/"
            )
        ]
        if len(allowed_docker_paths) > 1:
            invalid_paths.extend(allowed_docker_paths)
        if invalid_paths:
            verdict = LocalReviewVerdict(
                decision="rejected",
                action="human-intervention",
                review_comment="The attempted CI repair changed files outside the contribution.",
                finding_title="CI repair crossed the contribution boundary",
                finding_body=(
                    "The repair attempted to change shared or unrelated paths: "
                    + ", ".join(invalid_paths)
                ),
                fix_note="The out-of-scope edits were discarded.",
            )

        if verdict.decision == "rejected":
            subprocess.run(["git", "reset", "--hard", head_sha], cwd=worktree_path, check=True)
            subprocess.run(["git", "clean", "-fd"], cwd=worktree_path, check=True)
            changed_paths = []
            if infrastructure_evidence is not None:
                issue_number, issue_url = ensure_infrastructure_issue(infrastructure_evidence)
                verdict = LocalReviewVerdict(
                    decision=verdict.decision,
                    action=verdict.action,
                    review_comment=verdict.review_comment,
                    finding_title=verdict.finding_title,
                    finding_body=(
                        f"{verdict.finding_body.rstrip()}\n\n"
                        f"Infrastructure issue: {issue_url} (#{issue_number})"
                    ),
                    fix_note=verdict.fix_note,
                )
        library = validated.descriptor["library"]

        if verdict.finding_title:
            _record_finding(
                repo_path=worktree_path,
                coordinates=str(library["coordinates"]),
                descriptor_input=SimpleNamespace(
                    issue_number=validated.descriptor.get("issue_number"),
                ),
                title=verdict.finding_title,
                body=verdict.finding_body,
            )

        descriptor = json.loads(json.dumps(validated.descriptor))
        descriptor["local_review"] = {
            "decision": verdict.decision,
            "review_comment": verdict.review_comment,
            "finding_title": verdict.finding_title,
            "finding_body": verdict.finding_body,
            "fix_note": verdict.fix_note,
            "model": selection.model,
            "session_id": session_id,
            "changed_paths": changed_paths[:200],
        }
        if verdict.action is not None:
            descriptor["local_review"]["action"] = verdict.action
        validate_publication_descriptor(worktree_path, descriptor)
        descriptor_path = os.path.join(worktree_path, validated.descriptor_path)
        with open(descriptor_path, "w", encoding="utf-8") as descriptor_file:
            json.dump(descriptor, descriptor_file, indent=2, sort_keys=True)
            descriptor_file.write("\n")
        subprocess.run(["git", "add", "-A"], cwd=worktree_path, check=True)
        subprocess.run(
            ["git", "commit", "-m", f"Repair failed CI for PR #{pr_number}"],
            cwd=worktree_path,
            check=True,
        )
        ensure_pull_request_unapproved(pull_request)
        run_git_transport(
            [
                "push", "origin", f"HEAD:refs/heads/{head_branch}",
                f"--force-with-lease=refs/heads/{head_branch}:{head_sha}",
            ],
            cwd=worktree_path,
        )
        print(
            f"[Pushed CI-repair decision for PR #{pr_number}; "
            "the new exact head will be evaluated on the next pass.]"
        )
        if verdict.decision == "rejected":
            rejected_marker = f"<!-- forge-ci-repair-rejected:{head_sha} -->"
            if not any(
                    rejected_marker in str(comment.get("body") or "")
                    for comment in get_issue_comments(pr_number)
            ):
                post_issue_comment(
                    pr_number,
                    f"Forge could not fix the failed CI by changing this contribution.\n\n"
                    f"{verdict.review_comment}\n\n{rejected_marker}",
                )
            reconcile_rejected_publication(
                pull_request,
                SimpleNamespace(descriptor=descriptor),
            )
        return True
    finally:
        cleanup_review_workspace(
            reachability_metadata_path, worktree_path, pr_number,
        )


def reconcile_failed_ci_pull_request(
        pull_request: dict,
        validated: Any,
        reachability_metadata_path: str,
) -> None:
    """Invoke scoped CI diagnosis immediately without automatic reruns."""
    pr_number = pull_request.get("number")
    head_sha = pull_request.get("headRefOid")
    if not isinstance(pr_number, int) or not isinstance(head_sha, str) or not head_sha:
        raise RuntimeError(f"Missing head metadata for failed-CI PR #{pr_number}")
    if not has_failed_pull_request_ci(pull_request):
        print(f"[Skipping failed-CI follow-up for PR #{pr_number}: CI state changed.]")
        return

    print(f"[Starting immediate contribution-scoped CI diagnosis for PR #{pr_number}.]")
    repair_failed_ci_pull_request(
        pull_request,
        validated,
        reachability_metadata_path,
    )


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


def get_pull_request_url(pr_number: int) -> str:
    """Resolve the GitHub URL for the target pull request."""
    pull_request = gh_json(
        "pr",
        "view",
        str(pr_number),
        "--repo",
        REPO,
        "--json",
        "url",
    )
    pull_request_url = pull_request.get("url")
    if not isinstance(pull_request_url, str) or not pull_request_url:
        print(f"ERROR: Missing URL for pull request #{pr_number}.", file=sys.stderr)
        raise RuntimeError(f"Missing URL for pull request #{pr_number}")
    return pull_request_url


def read_log_tail(log_path: str, max_lines: int = 20) -> str:
    """Return the last lines from a log file when it exists."""
    if not os.path.isfile(log_path):
        return ""
    with open(log_path, "r", encoding="utf-8") as log_file:
        return "\n".join(log_file.read().strip().splitlines()[-max_lines:])


def read_log_text(log_path: str) -> str:
    """Return the complete text from a log file when it exists."""
    if not os.path.isfile(log_path):
        return ""
    with open(log_path, "r", encoding="utf-8") as log_file:
        return log_file.read().strip()


def extract_codex_final_message(log_path: str) -> str:
    """Return the final assistant message from a Codex JSONL log."""
    if not os.path.isfile(log_path):
        return ""

    final_message = ""
    with open(log_path, "r", encoding="utf-8") as log_file:
        for line in log_file:
            line = line.strip()
            if not line:
                continue
            try:
                payload = json.loads(line)
            except json.JSONDecodeError:
                continue
            if payload.get("type") != "item.completed":
                continue
            item = payload.get("item", {})
            if item.get("type") == "agent_message":
                final_message = item.get("text", "") or final_message
    return final_message.strip()


def extract_codex_token_usage_summary(log_path: str, model_name: str | None = None) -> str:
    """Return a human-readable Codex token-usage line from a JSONL log, or '' when unavailable.

    Includes the session cost derived from `model_name`'s per-token rates.
    """
    if not os.path.isfile(log_path):
        return ""
    with open(log_path, "r", encoding="utf-8") as log_file:
        usage = extract_codex_token_usage(log_file.read())
    if usage is None:
        return ""
    input_tokens, cached_input_tokens, output_tokens = usage
    cost_usd = calc_model_session_cost(model_name, input_tokens, cached_input_tokens, output_tokens)
    return (
        f"input={input_tokens} cached_input={cached_input_tokens} output={output_tokens} "
        f"cost=${cost_usd:.4f}"
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




def extract_coordinate_parts(title: str) -> Optional[tuple[str, str, str]]:
    """
    Extract Maven coordinate parts (groupId, artifactId, version) from an issue title.
    """
    match = re.search(r'([\w.\-]+):([\w.\-]+):([\w.\-]+)', title)
    if match:
        return match.group(1), match.group(2), match.group(3)
    return None


def extract_maven_coordinates(title: str) -> Optional[str]:
    """
    Extract Maven coordinates (groupId:artifactId:version) from an issue title.
    Returns the coordinates string or None if not found.
    """
    coordinate_parts = extract_coordinate_parts(title)
    if coordinate_parts:
        return ":".join(coordinate_parts)
    return None


def load_current_metadata_version(
        reachability_metadata_path: str,
        group: str,
        artifact: str,
        report_errors: bool = True,
) -> Optional[str]:
    """Load the current metadata version from the latest index.json entry."""
    index_json_path = os.path.join(
        reachability_metadata_path,
        "metadata",
        group,
        artifact,
        "index.json",
    )
    index_json_path_display = _repo_relative_path(index_json_path, reachability_metadata_path)
    if not os.path.isfile(index_json_path):
        if report_errors:
            print(f"ERROR: Missing metadata index file: {index_json_path_display}", file=sys.stderr)
        return None

    with open(index_json_path, "r", encoding="utf-8") as index_file:
        index_entries = json.load(index_file)

    for entry in index_entries:
        if entry.get("latest") is True:
            return entry.get("test-version") or entry.get("metadata-version")

    if report_errors:
        print(
            f"ERROR: No latest entry found in metadata index: {index_json_path_display}",
            file=sys.stderr,
        )
    return None


def _load_pending_run_metrics(metrics_worktree_path: str) -> dict | None:
    """Load the pending run metrics from the metrics worktree."""
    try:
        return read_pending_metrics(metrics_worktree_path)
    except (json.JSONDecodeError, TypeError, FileNotFoundError):
        return None


def _load_dynamic_access_snapshot_from_metrics(run_metrics: dict | None) -> DynamicAccessCoverageSnapshot | None:
    """Load dynamic-access coverage from the stored run metrics stats block."""
    if not isinstance(run_metrics, dict):
        return None

    stats = run_metrics.get("stats")
    if not isinstance(stats, dict):
        return None

    dynamic_access = stats.get("dynamicAccess")
    if not isinstance(dynamic_access, dict):
        return None

    total_calls = int(dynamic_access.get("totalCalls", 0) or 0)
    if total_calls <= 0:
        return None

    covered_calls = int(dynamic_access.get("coveredCalls", 0) or 0)
    coverage_ratio = dynamic_access.get("coverageRatio")
    if coverage_ratio is None:
        coverage_ratio = covered_calls / total_calls

    return DynamicAccessCoverageSnapshot(
        covered_calls=covered_calls,
        total_calls=total_calls,
        coverage_ratio=float(coverage_ratio),
        source="stats",
    )


def _resolve_dynamic_access_report_path(claimed_issue: ClaimedIssue) -> str:
    """Resolve the dynamic-access coverage report path for a new-library issue."""
    group, artifact, version = claimed_issue.issue_coordinates.split(":")
    return os.path.join(
        resolve_test_dir(claimed_issue.worktree_path, group, artifact, version),
        "build",
        "reports",
        "dynamic-access",
        "dynamic-access-coverage.json",
    )


def _load_dynamic_access_snapshot_from_report(claimed_issue: ClaimedIssue) -> DynamicAccessCoverageSnapshot | None:
    """Load dynamic-access coverage directly from the generated report as a fallback."""
    report_path = _resolve_dynamic_access_report_path(claimed_issue)
    try:
        report = load_dynamic_access_coverage_report(report_path)
    except FileNotFoundError:
        return None

    if not report.has_dynamic_access or report.total_calls <= 0:
        return None

    return DynamicAccessCoverageSnapshot(
        covered_calls=report.covered_calls,
        total_calls=report.total_calls,
        coverage_ratio=(report.covered_calls / report.total_calls),
        source="report",
    )


def is_external_failure_exception(exc: BaseException | None) -> bool:
    """Return True when a workflow exception is an external dependency failure.

    GitHub (rate-limit or transient), git transport, and shared Gradle bootstrap
    outages surface as typed exceptions (`GitHubError` / `GitTransportError` /
    `GradleBootstrapFailure`). An external failure releases the issue claim for
    retry without `human-intervention`, while any other exception (including agent
    timeouts) is logical. The cause/context chain is walked so a wrapped external
    error is still recognized.
    §FS-human-intervention-policy
    """
    error: BaseException | None = exc
    while error is not None:
        if isinstance(error, (GitHubError, GitTransportError, GradleBootstrapFailure)):
            return True
        error = error.__cause__ or error.__context__
    return False


def resolve_human_intervention_candidate(
        claimed_issue: ClaimedIssue,
        workflow_success: bool = True,
) -> HumanInterventionCandidate | None:
    """Return follow-up data for a logical issue failure that needs human intervention.

    External failures are filtered out upstream (see `handle_failed_claimed_issue`),
    so any library-issue failure that reaches here is logical (driver/core/CI check,
    including agent timeouts) and is labeled. §FS-human-intervention-policy
    """
    if claimed_issue.label not in {LABEL_LIBRARY_NEW, LABEL_LIBRARY_UPDATE}:
        return None

    run_metrics = _load_pending_run_metrics(claimed_issue.scratch_metrics_repo_path)
    if not workflow_success:
        strategy_name = None
        workflow_status = RUN_STATUS_FAILURE
        coverage_snapshot = None
        if isinstance(run_metrics, dict):
            strategy_name = run_metrics.get("strategy_name")
            workflow_status = str(run_metrics.get("status") or RUN_STATUS_FAILURE)
            coverage_snapshot = _load_dynamic_access_snapshot_from_metrics(run_metrics)
        if workflow_status in HUMAN_INTERVENTION_NON_FAILURE_STATUSES:
            return None
        if coverage_snapshot is None:
            coverage_snapshot = _load_dynamic_access_snapshot_from_report(claimed_issue)
        return HumanInterventionCandidate(
            strategy_name=strategy_name,
            workflow_status=workflow_status,
            coverage=coverage_snapshot,
            reason="test_generation_failed",
        )

    if run_metrics is None:
        return None

    strategy_name = run_metrics.get("strategy_name")
    if not strategy_name:
        return None
    if run_metrics.get("status") == RUN_STATUS_CHUNK_READY:
        return None

    strategy = load_strategy_by_name(strategy_name)
    if strategy is None or strategy.get("workflow") != "dynamic_access_iterative":
        return None

    coverage_snapshot = _load_dynamic_access_snapshot_from_metrics(run_metrics)
    if coverage_snapshot is None:
        coverage_snapshot = _load_dynamic_access_snapshot_from_report(claimed_issue)
    if coverage_snapshot is None:
        return None

    if (
            coverage_snapshot.covered_calls > 0
            and coverage_snapshot.coverage_ratio > LOW_DYNAMIC_ACCESS_COVERAGE_RATIO
    ):
        return None

    return HumanInterventionCandidate(
        strategy_name=strategy_name,
        workflow_status=str(run_metrics.get("status") or "unknown"),
        coverage=coverage_snapshot,
        reason="low_dynamic_access_coverage",
    )


def _collect_human_intervention_read_only_files(claimed_issue: ClaimedIssue) -> list[str]:
    """Collect the key generated files that help the analysis agent explain the gap."""
    group, artifact, version = claimed_issue.issue_coordinates.split(":")
    metadata_version = resolve_metadata_version(claimed_issue.worktree_path, group, artifact, version)
    candidate_paths = [
        resolve_test_dir(claimed_issue.worktree_path, group, artifact, version),
        os.path.join(claimed_issue.worktree_path, "metadata", group, artifact, "index.json"),
        os.path.join(claimed_issue.worktree_path, "metadata", group, artifact, metadata_version),
        resolve_stats_file_path(claimed_issue.worktree_path, group, artifact, version),
        _resolve_dynamic_access_report_path(claimed_issue),
    ]

    read_only_files: list[str] = []
    for candidate_path in candidate_paths:
        if os.path.isdir(candidate_path):
            read_only_files.extend(list_all_files(candidate_path))
        elif os.path.isfile(candidate_path):
            read_only_files.append(candidate_path)

    # Preserve order while removing duplicates.
    return list(dict.fromkeys(read_only_files))


def _build_human_intervention_analysis_prompt(
        claimed_issue: ClaimedIssue,
        candidate: HumanInterventionCandidate,
) -> str:
    """Create the analysis prompt for the follow-up agent run."""
    if candidate.coverage is None:
        return _build_failed_generation_analysis_prompt(claimed_issue, candidate, [], None)

    coverage = candidate.coverage
    coverage_percent = coverage.coverage_ratio * 100.0
    report_path = _resolve_dynamic_access_report_path(claimed_issue)
    report_path_display = _repo_relative_path(report_path, claimed_issue.worktree_path)
    return (
        "Read-only analysis task. Do not modify files, create commits, or propose automated edits.\n\n"
        "Project issue details:\n"
        f"- Issue number: {claimed_issue.issue['number']}\n"
        f"- Library: {claimed_issue.issue_coordinates}\n"
        f"- Workflow strategy: {candidate.strategy_name}\n"
        f"- Workflow status: {candidate.workflow_status}\n"
        f"- Dynamic access coverage: {coverage.covered_calls}/{coverage.total_calls} "
        f"({coverage_percent:.2f}%) from {coverage.source}\n"
        f"- Dynamic access report path: {report_path_display}\n\n"
        "Task:\n"
        "Analyze the generated tests, metadata, stats, and dynamic-access report in this repository. "
        "Write the exact GitHub issue comment that should be posted because this library likely needs human follow-up.\n\n"
        "Comment requirements:\n"
        "- Start with a short heading: `Human intervention needed`.\n"
        "- Explain briefly why dynamic-access coverage is missing or still very low.\n"
        "- Ground the explanation in concrete project observations.\n"
        "- End with 2 or 3 specific manual next steps.\n"
        "- Keep the comment concise and do not use code fences.\n"
        "- Do not mention being an AI."
    )


def _build_human_intervention_fallback_comment(
        claimed_issue: ClaimedIssue,
        candidate: HumanInterventionCandidate,
) -> str:
    """Build a deterministic fallback comment when the analysis agent fails."""
    if candidate.reason == "test_generation_failed" or candidate.coverage is None:
        return _build_failed_generation_fallback_comment(claimed_issue, candidate, [])

    coverage_percent = candidate.coverage.coverage_ratio * 100.0
    return (
        "Human intervention needed\n\n"
        f"Automation completed for `{claimed_issue.issue_coordinates}`, but dynamic-access coverage remains "
        f"`{candidate.coverage.covered_calls}/{candidate.coverage.total_calls}` ({coverage_percent:.2f}%). "
        "This suggests the generated tests are not exercising the required dynamic-access paths well enough.\n\n"
        "Recommended manual follow-up:\n"
        "- Inspect the generated tests and compare them with the dynamic-access report to find uncovered call sites.\n"
        "- Verify whether the library requires environment setup, fixtures, or execution paths that automation did not trigger.\n"
        "- Add or adjust focused tests for the uncovered paths before relying on the generated metadata."
    )


def _sanitize_log_name(value: str) -> str:
    """Return a filesystem-safe log name segment."""
    return re.sub(r"[^A-Za-z0-9_.-]+", "_", value).strip("_") or "unknown"


def _repo_relative_path(path: str, repo_path: str) -> str:
    """Return a path relative to the given repository root."""
    return os.path.relpath(os.path.abspath(path), os.path.abspath(repo_path))


def get_codex_failure_analysis_log_path(issue_number: int, coordinates: str) -> str:
    """Return the Codex failure-analysis log path for the target issue."""
    return build_task_log_path(
        "failure-analysis",
        coordinates,
        f"codex_failure_analysis_issue_{issue_number}.log",
    )


def _log_file_mentions_issue(log_path: str, claimed_issue: ClaimedIssue) -> bool:
    """Return True when a log file appears to belong to the claimed issue."""
    coordinate = claimed_issue.issue_coordinates
    group, artifact, version = coordinate.split(":")
    try:
        with open(log_path, "r", encoding="utf-8", errors="replace") as log_file:
            content = log_file.read(2_000_000)
    except OSError:
        return False

    if coordinate in content:
        return True
    return artifact in content and version in content and group in content


def collect_issue_log_paths(claimed_issue: ClaimedIssue, started_at: float | None) -> list[str]:
    """Collect run logs that are useful for a failed library-generation analysis."""
    log_dir = resolve_logs_root()
    if not os.path.isdir(log_dir):
        return []

    coordinate = claimed_issue.issue_coordinates
    group, artifact, version = coordinate.split(":")
    filename_needles = {
        sanitize_library_log_segment(coordinate),
        _sanitize_log_name(coordinate),
        _sanitize_log_name(coordinate.replace(":", "_")),
        _sanitize_log_name(f"{group}_{artifact}_{version}"),
    }
    candidates: list[tuple[float, str]] = []
    for root, _, file_names in os.walk(log_dir):
        for file_name in file_names:
            log_path = os.path.join(root, file_name)
            if not os.path.isfile(log_path):
                continue
            try:
                modified_at = os.path.getmtime(log_path)
            except OSError:
                continue

            relative_path = os.path.relpath(log_path, log_dir)
            path_matches = any(needle and needle in relative_path for needle in filename_needles)
            recent_enough = started_at is not None and modified_at >= started_at - 5
            if path_matches or (recent_enough and _log_file_mentions_issue(log_path, claimed_issue)):
                candidates.append((modified_at, log_path))
    candidates.sort(reverse=True)
    return [path for _, path in candidates[:8]]


def _format_failure_metrics_summary(run_metrics: dict | None) -> str:
    """Format relevant failure metrics for a Codex issue-analysis prompt."""
    if not isinstance(run_metrics, dict):
        return "- Metrics: unavailable"

    metrics = run_metrics.get("metrics")
    if not isinstance(metrics, dict):
        metrics = {}

    lines = [
        f"- Metrics status: {run_metrics.get('status', 'unknown')}",
        f"- Strategy: {run_metrics.get('strategy_name', 'unknown')}",
        f"- Agent: {run_metrics.get('agent', 'unknown')}",
        f"- Model: {run_metrics.get('model', 'unknown')}",
        f"- Iterations: {metrics.get('iterations', 'unknown')}",
        f"- Generated LOC: {metrics.get('generated_loc', 'unknown')}",
        f"- Starting commit: {run_metrics.get('starting_commit', 'unknown')}",
        f"- Ending commit: {run_metrics.get('ending_commit', 'unknown')}",
    ]
    return "\n".join(lines)


def _format_log_path_list(log_paths: list[str]) -> str:
    """Format log paths for prompts and fallback comments."""
    if not log_paths:
        return "- No matching run logs were found."
    return "\n".join(f"- {display_log_path(log_path)}" for log_path in log_paths)


def _build_failed_generation_analysis_prompt(
        claimed_issue: ClaimedIssue,
        candidate: HumanInterventionCandidate,
        log_paths: list[str],
        run_metrics: dict | None,
        preservation_result: FailurePreservationResult | None = None,
) -> str:
    """Create a Codex prompt that asks for the exact failure-analysis issue comment."""
    coverage_details = "- Dynamic-access coverage: unavailable"
    if candidate.coverage is not None:
        coverage_percent = candidate.coverage.coverage_ratio * 100.0
        coverage_details = (
            f"- Dynamic-access coverage: {candidate.coverage.covered_calls}/"
            f"{candidate.coverage.total_calls} ({coverage_percent:.2f}%) from {candidate.coverage.source}"
        )

    preserved_work_details = "- Preserved work branch: unavailable"
    if preservation_result is not None:
        preserved_work_details = (
            f"- Preserved work branch: {preservation_result.branch_url}\n"
            f"- Branch name: `{preservation_result.branch_name}`\n"
            f"- Preservation commit created: {preservation_result.committed_changes}"
        )

    return (
        "Read-only analysis task. Do not modify files, create commits, run fix commands, or open a PR.\n\n"
        "Project issue details:\n"
        f"- Issue number: {claimed_issue.issue['number']}\n"
        f"- Library: {claimed_issue.issue_coordinates}\n"
        f"- Pipeline label: {claimed_issue.label}\n"
        f"- Workflow status: {candidate.workflow_status}\n"
        f"- Human-intervention reason: {candidate.reason}\n"
        f"{coverage_details}\n\n"
        "Preserved work:\n"
        f"{preserved_work_details}\n\n"
        "Run metrics summary:\n"
        f"{_format_failure_metrics_summary(run_metrics)}\n\n"
        "Relevant log paths:\n"
        f"{_format_log_path_list(log_paths)}\n\n"
        "Generated project paths to inspect if present:\n"
        "- Current reachability-metadata worktree\n"
        "- Scratch metrics repository for this run\n\n"
        "Task:\n"
        "Inspect the available logs, generated tests, Gradle output, and metrics. "
        "Write the exact GitHub issue comment that should be posted for human follow-up.\n\n"
        "Comment requirements:\n"
        "- Start with the heading `Human intervention needed`.\n"
        "- State that the automated job failed.\n"
        "- Include the preserved work branch URL when it is available.\n"
        "- Explain the most likely failing stage and the concrete evidence from logs or metrics.\n"
        "- Mention specific log paths that a maintainer should inspect.\n"
        "- End with 2 or 3 specific manual next steps.\n"
        "- Keep the comment concise but detailed enough to act on.\n"
        "- Do not use code fences.\n"
        "- Do not mention being an AI."
    )


def _build_failed_generation_fallback_comment(
        claimed_issue: ClaimedIssue,
        candidate: HumanInterventionCandidate,
        log_paths: list[str],
        preservation_result: FailurePreservationResult | None = None,
) -> str:
    """Build a deterministic failed-generation comment when Codex analysis fails."""
    logs_section = _format_log_path_list(log_paths)
    preserved_work_section = "Preserved work branch: unavailable"
    if preservation_result is not None:
        preserved_work_section = (
            f"Preserved work branch: {preservation_result.branch_url}\n"
            f"Branch name: `{preservation_result.branch_name}`"
        )

    return (
        "Human intervention needed\n\n"
        f"The automated `{claimed_issue.label}` job failed for `{claimed_issue.issue_coordinates}` with "
        f"workflow status `{candidate.workflow_status}`. The automation could not produce a PR-ready update, "
        "so this issue needs manual follow-up. Any work left in the failed run has been preserved.\n\n"
        f"{preserved_work_section}\n\n"
        "Relevant logs:\n"
        f"{logs_section}\n\n"
        "Recommended manual follow-up:\n"
        "- Inspect the run logs to identify the first Gradle or agent failure.\n"
        "- Review the preserved branch and decide which generated changes can be salvaged.\n"
        "- Re-run the workflow or continue manually after addressing the root cause."
    )


def claimed_issue_worktree_is_valid(claimed_issue: ClaimedIssue, stage: str) -> bool:
    """Return True when analysis agents may safely run in the claimed issue worktree."""
    try:
        require_claimed_issue_worktree(claimed_issue, stage)
        return True
    except Exception as exc:
        print(
            (
                f"ERROR: Skipping {stage} for issue #{claimed_issue.issue['number']} "
                f"because the claimed worktree is invalid: {exc!r}"
            ),
            file=sys.stderr,
        )
        return False


def run_codex_failed_generation_analysis(
        claimed_issue: ClaimedIssue,
        candidate: HumanInterventionCandidate,
        started_at: float | None,
        preservation_result: FailurePreservationResult | None = None,
) -> str:
    """Use the analysis agent to analyze a failed generation run."""
    run_metrics = _load_pending_run_metrics(claimed_issue.scratch_metrics_repo_path)
    log_paths = collect_issue_log_paths(claimed_issue, started_at)
    if not claimed_issue_worktree_is_valid(claimed_issue, "failed-run analysis"):
        return _build_failed_generation_fallback_comment(
            claimed_issue,
            candidate,
            log_paths,
            preservation_result,
        )
    prompt = _build_failed_generation_analysis_prompt(
        claimed_issue,
        candidate,
        log_paths,
        run_metrics,
        preservation_result,
    )
    selection = get_analysis_agent()

    log_stage(
        "failure-analysis",
        f"Running {selection.backend} failure analysis for issue #{claimed_issue.issue['number']}",
    )
    result = analysis_agent_run(
        working_dir=claimed_issue.worktree_path,
        context=prompt,
        task_type="failure-analysis",
        library=claimed_issue.issue_coordinates,
        timeout=FAILURE_ANALYSIS_TIMEOUT_SECONDS,
    )
    if result.return_code == 0 and result.response.strip():
        return result.response.strip()

    output_tail = read_log_tail(result.log_path)
    print(
        (
            f"ERROR: {selection.backend} failure analysis failed for issue "
            f"#{claimed_issue.issue['number']}. Log: {display_log_path(result.log_path)}."
            + (f"\n{output_tail}" if output_tail else "")
        ),
        file=sys.stderr,
    )
    return _build_failed_generation_fallback_comment(
        claimed_issue,
        candidate,
        log_paths,
        preservation_result,
    )


def run_human_intervention_analysis(
        claimed_issue: ClaimedIssue,
        candidate: HumanInterventionCandidate,
        started_at: float | None = None,
        preservation_result: FailurePreservationResult | None = None,
) -> str:
    """Run a separate agent pass that writes the issue comment for human follow-up."""
    if candidate.reason in {"test_generation_failed", "job_failed"}:
        return run_codex_failed_generation_analysis(
            claimed_issue,
            candidate,
            started_at,
            preservation_result,
        )

    strategy = load_strategy_by_name(candidate.strategy_name)
    if strategy is None:
        return _build_human_intervention_fallback_comment(claimed_issue, candidate)

    model_name = strategy.get("model") or DEFAULT_MODEL_NAME
    read_only_files = _collect_human_intervention_read_only_files(claimed_issue)
    prompt = _build_human_intervention_analysis_prompt(claimed_issue, candidate)
    if not claimed_issue_worktree_is_valid(claimed_issue, "human-intervention analysis"):
        return _build_human_intervention_fallback_comment(claimed_issue, candidate)

    try:
        agent = init_workflow_agent(
            strategy=strategy,
            working_dir=claimed_issue.worktree_path,
            editable_files=[],
            read_only_files=read_only_files,
            verbose=False,
            model_name=model_name,
        )
        response = (agent.send_prompt(prompt) or "").strip()
        if response:
            return response
    except Exception as exc:
        print(
            f"ERROR: Human-intervention analysis failed for issue #{claimed_issue.issue['number']}: {exc!r}",
            file=sys.stderr,
        )

    return _build_human_intervention_fallback_comment(claimed_issue, candidate)


def _sanitize_branch_segment(value: str) -> str:
    """Return a branch-safe path segment."""
    return re.sub(r"[^A-Za-z0-9._-]+", "-", value).strip("-._") or "unknown"


def build_failure_preservation_branch_name(claimed_issue: ClaimedIssue) -> str:
    """Build a unique branch name for preserving a failed issue run."""
    branch_prefix = build_failure_preservation_branch_prefix(
        issue_number=int(claimed_issue.issue["number"]),
        label=claimed_issue.label,
        coordinate=claimed_issue.issue_coordinates,
        github_login=get_authenticated_user(),
    )
    return f"{branch_prefix}{uuid.uuid4().hex[:8]}"


def build_failure_preservation_branch_prefix(
        *,
        issue_number: int,
        label: str,
        coordinate: str,
        github_login: str,
) -> str:
    """Build the deterministic prefix for failed-work preservation branches."""
    authenticated_login = _sanitize_branch_segment(github_login)
    coordinate_segment = _sanitize_branch_segment(coordinate)
    label_segment = _sanitize_branch_segment(label)
    return (
        f"ai/{authenticated_login}/human-intervention/"
        f"issue-{issue_number}-{label_segment}-{coordinate_segment}-"
    )


def build_origin_branch_url(repo_path: str, branch_name: str) -> str:
    """Build the browser URL for a branch pushed to the origin remote."""
    try:
        origin_owner = get_origin_owner(cwd=repo_path)
    except subprocess.CalledProcessError:
        origin_owner = None
    if not origin_owner:
        origin_owner = REPO.split("/", 1)[0]
    repo_name = REPO.split("/", 1)[1]
    return f"https://github.com/{origin_owner}/{repo_name}/tree/{quote(branch_name, safe='')}"


def list_remote_branches_by_prefix(repo_path: str, branch_prefix: str) -> list[str]:
    """Return remote branch names whose heads match the given prefix."""
    result = run_git_transport(
        ["ls-remote", "--heads", "origin", f"{branch_prefix}*"],
        cwd=repo_path,
        env=git_env_limited_to_repo_root(repo_path),
    )
    branch_names = []
    for line in result.stdout.splitlines():
        parts = line.split()
        if len(parts) < 2:
            continue
        ref_name = parts[1]
        if not ref_name.startswith("refs/heads/"):
            continue
        branch_name = ref_name[len("refs/heads/"):]
        if branch_name.startswith(branch_prefix):
            branch_names.append(branch_name)
    return sorted(set(branch_names))


def remote_branch_commit_timestamp(repo_path: str, branch_name: str) -> int:
    """Fetch a remote branch and return its tip commit timestamp."""
    remote_ref = fetch_remote_branch(repo_path, branch_name)
    result = subprocess.run(
        ["git", "show", "-s", "--format=%ct", remote_ref],
        cwd=repo_path,
        env=git_env_limited_to_repo_root(repo_path),
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=True,
    )
    return int(result.stdout.strip())


def sort_remote_branches_by_commit_time(repo_path: str, branch_names: list[str]) -> list[str]:
    """Return remote branch names newest-first by tip commit time."""
    return sorted(
        branch_names,
        key=lambda branch_name: remote_branch_commit_timestamp(repo_path, branch_name),
        reverse=True,
    )


def find_preserved_branch_candidates(
        issue: dict,
        label: str,
        coordinate: str,
        repo_path: str,
) -> list[str]:
    """Return likely preserved-work branches derived from recent comment authors."""
    issue_number = int(issue["number"])
    candidates = []
    seen_branches = set()
    seen_prefixes = set()
    for comment in reversed(get_issue_comments(issue_number)):
        author_login = comment_author_login(comment)
        if author_login is None:
            continue
        branch_prefix = build_failure_preservation_branch_prefix(
            issue_number=issue_number,
            label=label,
            coordinate=coordinate,
            github_login=author_login,
        )
        if branch_prefix in seen_prefixes:
            continue
        seen_prefixes.add(branch_prefix)
        for branch_name in sort_remote_branches_by_commit_time(
                repo_path,
                list_remote_branches_by_prefix(repo_path, branch_prefix),
        ):
            if branch_name in seen_branches:
                continue
            candidates.append(branch_name)
            seen_branches.add(branch_name)
    return candidates


def fetch_remote_branch(repo_path: str, branch_name: str) -> str:
    """Fetch a remote branch and return its local remote-tracking ref."""
    remote_ref = f"refs/remotes/origin/{branch_name}"
    run_git_transport(
        ["fetch", "origin", f"+refs/heads/{branch_name}:{remote_ref}"],
        cwd=repo_path,
        env=git_env_limited_to_repo_root(repo_path),
    )
    return remote_ref


def load_continuation_marker_from_branch(
        repo_path: str,
        branch_name: str,
) -> ContinuationMarker | None:
    """Load the continuation marker stored on a remote preserved branch."""
    remote_ref = fetch_remote_branch(repo_path, branch_name)
    marker_relpath = f"forge/{CONTINUATION_MARKER_FILENAME}"
    result = subprocess.run(
        ["git", "show", f"{remote_ref}:{marker_relpath}"],
        cwd=repo_path,
        env=git_env_limited_to_repo_root(repo_path),
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=False,
    )
    if result.returncode != 0:
        log_stage(
            "continuation",
            f"Preserved branch {branch_name} has no {marker_relpath}; running issue from scratch.",
        )
        return None
    try:
        marker = ContinuationMarker.from_dict(json.loads(result.stdout))
    except Exception as exc:
        print(
            f"ERROR: Failed to parse continuation marker on branch {branch_name}: {exc!r}",
            file=sys.stderr,
        )
        return None
    if marker.preserved_branch != branch_name:
        marker.record_preserved_branch(branch_name)
    return marker


def resolve_issue_continuation_marker(
        issue: dict,
        label: str,
        coordinate: str,
        base_reachability_metadata_path: str,
) -> ContinuationMarker | None:
    """Resolve a previously preserved continuation marker for this claim."""
    if not issue_is_resumable(issue):
        return None
    issue_number = int(issue["number"])
    for branch_name in find_preserved_branch_candidates(
            issue,
            label,
            coordinate,
            base_reachability_metadata_path,
    ):
        marker = load_continuation_marker_from_branch(base_reachability_metadata_path, branch_name)
        if marker is None:
            continue
        if marker.issue_number == issue_number and marker.label == label:
            return marker
        log_stage(
            "continuation",
            (
                f"Ignoring preserved marker on {branch_name}: "
                f"marker issue/label #{marker.issue_number} {marker.label} does not match "
                f"#{issue_number} {label}."
            ),
        )
    return None


def checkout_continuation_branch(
        worktree_path: str,
        marker: ContinuationMarker,
        issue_base_commit: str,
) -> bool:
    """Check out and rebase preserved work onto the pinned issue base.

    §FS-forge-run-requirements.4
    """
    branch_name = marker.preserved_branch
    if not branch_name:
        return False
    git_env = git_env_limited_to_repo_root(worktree_path)
    remote_ref = fetch_remote_branch(worktree_path, branch_name)
    subprocess.run(
        ["git", "switch", "-C", branch_name, remote_ref],
        cwd=worktree_path,
        env=git_env,
        check=True,
    )
    rebase_result = subprocess.run(
        ["git", "rebase", issue_base_commit],
        cwd=worktree_path,
        env=git_env,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=False,
    )
    if rebase_result.returncode == 0:
        log_stage(
            "continuation",
            f"Resuming issue #{marker.issue_number} from {branch_name} at phase {marker.continue_from}.",
        )
        return True
    print(
        (
            f"ERROR: Could not rebase preserved branch {branch_name} onto "
            f"pinned issue base {issue_base_commit[:12]}; falling back to a clean run.\n"
            f"{rebase_result.stdout}"
        ),
        file=sys.stderr,
    )
    subprocess.run(["git", "rebase", "--abort"], cwd=worktree_path, env=git_env, check=False)
    subprocess.run(
        ["git", "switch", "--detach", issue_base_commit],
        cwd=worktree_path,
        env=git_env,
        check=True,
    )
    marker_path = continuation_marker_path(worktree_path)
    if os.path.exists(marker_path):
        os.remove(marker_path)
    return False


def create_or_load_run_continuation_marker(
        claimed_issue: ClaimedIssue,
        strategy_name: str,
) -> str:
    """Ensure this claimed run has an eager continuation marker."""
    marker_path = continuation_marker_path(claimed_issue.worktree_path)
    marker = load_continuation_marker(marker_path)
    if marker is None:
        marker = claimed_issue.continuation_marker or ContinuationMarker.create(
            strategy_name=strategy_name,
            issue_number=int(claimed_issue.issue["number"]),
            label=claimed_issue.label,
            coordinate=claimed_issue.issue_coordinates,
            new_version=claimed_issue.new_version,
        )
    marker.save(marker_path)
    return marker_path


def library_update_route_from_marker(marker: ContinuationMarker | None) -> LibraryUpdateRoute | None:
    """Return the marker-backed library-update route when continuation has one."""
    if marker is None or marker.library_update_route is None:
        return None
    return LibraryUpdateRoute.from_json(marker.library_update_route)


def restore_library_update_route_from_marker(
        claimed_issue: ClaimedIssue,
        marker: ContinuationMarker | None,
        *,
        required: bool = False,
) -> LibraryUpdateRoute | None:
    """Restore marker-backed library-update route state into the run sidecar."""
    if claimed_issue.label != LABEL_LIBRARY_UPDATE:
        return None
    route = library_update_route_from_marker(marker)
    if route is None:
        if required:
            raise ValueError(
                f"Issue #{claimed_issue.issue['number']} resumes publication without a library-update route."
            )
        return None
    write_library_update_route(library_update_route_artifact_root(claimed_issue), route)
    log_stage(
        "continuation",
        f"Restored library-update route {route.selected_driver} for issue #{claimed_issue.issue['number']}.",
    )
    return route


def record_library_update_route_in_marker(marker_path: str | None, route: LibraryUpdateRoute | None) -> None:
    """Persist the selected library-update route into the continuation marker."""
    if marker_path is None or route is None:
        return
    save_phase_update(
        marker_path,
        lambda marker: marker.record_library_update_route(route.to_json()),
    )


def restore_library_preparation_preflight_from_marker(
        claimed_issue: ClaimedIssue,
        marker: ContinuationMarker | None,
) -> str | None:
    """Restore marker-backed dispatcher preflight state into the run sidecar."""
    if marker is None or marker.library_preparation_preflight is None:
        return None
    preflight_root = claimed_issue.preflight_info_path or claimed_issue.scratch_metrics_repo_path
    preflight_path = write_library_preparation_preflight(
        preflight_root,
        marker.library_preparation_preflight,
    )
    log_stage(
        "continuation",
        f"Restored library preparation preflight for issue #{claimed_issue.issue['number']}.",
    )
    return preflight_path


def record_library_preparation_preflight_in_marker(
        marker_path: str | None,
        preflight_path: str | None,
) -> None:
    """Persist dispatcher preflight output into the continuation marker."""
    if marker_path is None or preflight_path is None:
        return
    preflight = load_library_preparation_preflight(preflight_path)
    if preflight is None:
        return
    save_phase_update(
        marker_path,
        lambda marker: marker.record_library_preparation_preflight(preflight),
    )


def require_claimed_issue_worktree(claimed_issue: ClaimedIssue, stage: str) -> str:
    """Require the claimed issue path to be the exact isolated reachability worktree."""
    try:
        resolved_path = require_complete_reachability_repo(claimed_issue.worktree_path)
    except SystemExit as exc:
        raise RuntimeError(
            (
                f"Issue #{claimed_issue.issue['number']} {stage} requires a valid isolated "
                f"worktree at {claimed_issue.worktree_path}."
            )
        ) from exc
    expected_path = os.path.abspath(claimed_issue.worktree_path)
    if resolved_path != expected_path:
        raise RuntimeError(
            (
                f"Issue #{claimed_issue.issue['number']} {stage} resolved the wrong "
                f"worktree root: expected {expected_path}, got {resolved_path}."
            )
        )
    return resolved_path


def copy_library_logs_to_preserved_worktree(claimed_issue: ClaimedIssue) -> str | None:
    """Copy the current library logs into the preserved worktree and return the repo-relative path."""
    safe_library_name = sanitize_library_log_segment(claimed_issue.issue_coordinates)
    source_log_dir = os.path.join(resolve_logs_root(), safe_library_name)
    if not os.path.isdir(source_log_dir):
        log_stage(
            "preserve-failed-work",
            f"No workflow logs found for {claimed_issue.issue_coordinates}; skipping log copy.",
        )
        return None

    logs_destination_relpath = os.path.join(HUMAN_INTERVENTION_LOGS_DIRNAME, safe_library_name)
    logs_destination_path = os.path.join(claimed_issue.worktree_path, logs_destination_relpath)
    if os.path.isdir(logs_destination_path):
        shutil.rmtree(logs_destination_path)
    elif os.path.exists(logs_destination_path):
        os.remove(logs_destination_path)
    os.makedirs(os.path.dirname(logs_destination_path), exist_ok=True)
    shutil.copytree(source_log_dir, logs_destination_path)
    log_stage(
        "preserve-failed-work",
        "Copied workflow logs for {library} from {source} to {destination}".format(
            library=claimed_issue.issue_coordinates,
            source=display_log_path(source_log_dir),
            destination=logs_destination_relpath,
        ),
    )
    return logs_destination_relpath


def baseline_stats_relpaths_for_preservation(repo_path: str) -> list[str]:
    """Return improve-coverage baseline snapshots that must survive continuation."""
    tests_root = os.path.join(repo_path, "tests", "src")
    if not os.path.isdir(tests_root):
        return []

    relpaths: list[str] = []
    for root, _dirs, files in os.walk(tests_root):
        if ".baseline-stats.json" not in files:
            continue
        relpaths.append(os.path.relpath(os.path.join(root, ".baseline-stats.json"), repo_path))
    return sorted(relpaths)


def git_rebase_in_progress(repo_path: str, git_env: dict[str, str]) -> bool:
    """Return whether a rebase is currently halted in the given worktree."""
    # `git rev-parse --git-path` resolves the sequencer directories against the
    # worktree-specific gitdir, so this stays correct inside linked worktrees.
    for sequencer_dir in ("rebase-merge", "rebase-apply"):
        resolved = subprocess.run(
            ["git", "rev-parse", "--git-path", sequencer_dir],
            cwd=repo_path,
            env=git_env,
            check=True,
            capture_output=True,
            text=True,
        ).stdout.strip()
        if resolved and os.path.isdir(os.path.join(repo_path, resolved)):
            return True
    return False


def run_preservation_git(
        args: list[str],
        cwd: str,
        env: dict[str, str],
        check: bool = True,
) -> subprocess.CompletedProcess[str]:
    """Run one git command of the failure handoff without narrating it.

    Failure output is the location, the error, and the preserved branch, so the
    handoff's own git chatter is captured and replayed only when the command
    fails or when debug logging is on. §FS-forge-run-location-reporting.4
    """
    result = subprocess.run(
        ["git", *args],
        cwd=cwd,
        env=env,
        capture_output=True,
        text=True,
        check=False,
    )
    detail = "\n".join(
        stream.strip()
        for stream in (result.stdout, result.stderr)
        if stream and stream.strip()
    )
    if result.returncode != 0 and check:
        if detail:
            print(detail, file=sys.stderr)
        raise subprocess.CalledProcessError(result.returncode, ["git", *args], result.stdout, result.stderr)
    if detail:
        log_debug("preserve-failed-work", detail)
    return result


def preserve_failed_work_branch(claimed_issue: ClaimedIssue) -> FailurePreservationResult:
    """Commit and push the failed run worktree so it survives workspace cleanup."""
    branch_name = build_failure_preservation_branch_name(claimed_issue)
    repo_path = require_claimed_issue_worktree(claimed_issue, "failure preservation")
    git_env = git_env_limited_to_repo_root(repo_path)
    issue_number = claimed_issue.issue["number"]

    log_debug("preserve-failed-work", f"Preserving failed work for issue #{issue_number} on branch {branch_name}")
    # A publication `git rebase` that halts on an index.json conflict leaves the
    # worktree mid-rebase with unmerged entries, and `git switch -C` then refuses
    # ("resolve your current index first"). Clear the sequencer state first so the
    # generated work is still preserved for later resume. Gate the abort on an
    # actual rebase so the common clean-worktree path does not log a spurious
    # "fatal: No rebase in progress?" from git. §FS-forge-run-continuation
    if git_rebase_in_progress(repo_path, git_env):
        log_debug("preserve-failed-work", f"Aborting in-progress rebase before preserving issue #{issue_number}.")
        run_preservation_git(["rebase", "--abort"], repo_path, git_env, check=False)
    run_preservation_git(["switch", "-C", branch_name], repo_path, git_env)
    logs_destination_relpath = copy_library_logs_to_preserved_worktree(claimed_issue)
    marker_path = continuation_marker_path(repo_path)
    marker = load_continuation_marker(marker_path)
    if marker is not None:
        record_publication_metrics_from_pending(marker, claimed_issue.scratch_metrics_repo_path)
        marker.record_preserved_branch(branch_name)
        marker.save(marker_path)
    run_preservation_git(["add", "-A"], repo_path, git_env)
    if logs_destination_relpath is not None:
        run_preservation_git(["add", "-f", "--", logs_destination_relpath], repo_path, git_env)
    force_add_paths = []
    marker_relpath = os.path.relpath(marker_path, repo_path)
    if os.path.exists(marker_path):
        force_add_paths.append(marker_relpath)
    pending_metrics_relpath = os.path.join(get_forge_subdir_name(), PENDING_METRICS_FILENAME)
    if os.path.exists(os.path.join(repo_path, pending_metrics_relpath)):
        force_add_paths.append(pending_metrics_relpath)
    force_add_paths.extend(baseline_stats_relpaths_for_preservation(repo_path))
    if force_add_paths:
        run_preservation_git(["add", "-f", "--", *force_add_paths], repo_path, git_env)
    diff_result = run_preservation_git(["diff", "--cached", "--quiet"], repo_path, git_env, check=False)
    committed_changes = diff_result.returncode != 0
    if committed_changes:
        run_preservation_git(
            ["commit", "-m", f"Preserve failed automation work for issue #{issue_number}"],
            repo_path,
            git_env,
        )
    else:
        log_debug("preserve-failed-work", f"No uncommitted work found for issue #{issue_number}; pushing branch at current HEAD.")

    run_git_transport(["push", "-u", "origin", branch_name], cwd=repo_path, env=git_env)
    branch_url = build_origin_branch_url(repo_path, branch_name)
    log_stage("preserve-failed-work", f"Preserved failed work for issue #{issue_number}: {branch_url}")
    return FailurePreservationResult(
        branch_name=branch_name,
        branch_url=branch_url,
        committed_changes=committed_changes,
        reviewable_worktree_path=repo_path,
        scratch_metrics_path=claimed_issue.scratch_metrics_repo_path,
        copied_logs_destination=None if logs_destination_relpath is None else os.path.join(
            repo_path,
            logs_destination_relpath,
        ),
        copied_logs_destination_relpath=logs_destination_relpath,
    )


def build_fixture_failure_preservation_result(claimed_issue: ClaimedIssue) -> FailurePreservationResult:
    """Record the preservation branch that fixture mode would have pushed."""
    issue_number = claimed_issue.issue["number"]
    branch_name = build_failure_preservation_branch_name(claimed_issue)
    worktree_path = os.path.abspath(claimed_issue.worktree_path)
    logs_destination_relpath = None
    if os.path.isdir(worktree_path):
        logs_destination_relpath = copy_library_logs_to_preserved_worktree(claimed_issue)
    else:
        log_stage(
            "preserve-failed-work",
            f"Fixture mode: worktree for issue #{issue_number} is not present at {worktree_path}.",
        )
    return FailurePreservationResult(
        branch_name=branch_name,
        branch_url=f"fixture://preserved-work/{issue_number}/{quote(branch_name, safe='')}",
        committed_changes=False,
        reviewable_worktree_path=worktree_path,
        scratch_metrics_path=claimed_issue.scratch_metrics_repo_path,
        copied_logs_destination=None if logs_destination_relpath is None else os.path.join(
            worktree_path,
            logs_destination_relpath,
        ),
        copied_logs_destination_relpath=logs_destination_relpath,
        fixture_mode=True,
    )


def refresh_preserved_branch_logs(
        claimed_issue: ClaimedIssue,
        preservation_result: FailurePreservationResult | None,
) -> None:
    """Refresh library logs on an already pushed human-intervention branch."""
    if preservation_result is None:
        return
    if is_fixture_testing_enabled():
        log_stage(
            "preserve-failed-work",
            (
                f"Fixture mode: preserved branch log refresh for issue "
                f"#{claimed_issue.issue['number']} was already recorded locally."
            ),
        )
        return

    repo_path = require_claimed_issue_worktree(claimed_issue, "preserved log refresh")
    git_env = git_env_limited_to_repo_root(repo_path)
    issue_number = claimed_issue.issue["number"]
    run_preservation_git(["switch", preservation_result.branch_name], repo_path, git_env)
    logs_destination_relpath = copy_library_logs_to_preserved_worktree(claimed_issue)
    if logs_destination_relpath is None:
        return

    run_preservation_git(["add", "-f", "--", logs_destination_relpath], repo_path, git_env)
    diff_result = run_preservation_git(
        ["diff", "--cached", "--quiet", "--", logs_destination_relpath],
        repo_path,
        git_env,
        check=False,
    )
    if diff_result.returncode == 0:
        log_debug("preserve-failed-work", f"No new workflow logs to add for issue #{issue_number}.")
        return

    run_preservation_git(
        ["commit", "-m", f"Add automation logs for issue #{issue_number}"],
        repo_path,
        git_env,
    )
    run_git_transport(["push"], cwd=repo_path, env=git_env)
    log_debug("preserve-failed-work", f"Updated preserved branch logs for issue #{issue_number}.")


def resolve_claimed_issue_failure_location(
        claimed_issue: ClaimedIssue,
        exc: BaseException | None = None,
) -> RunLocation:
    """Return the phase/step this claimed issue failed in.

    Prefers the location the failing step recorded in this process; falls back to
    the continuation marker so a driver-side or resumed failure still names its
    step. §FS-forge-run-location-reporting.3
    """
    location = resolve_failure_location(exc)
    if location.is_located:
        return location
    marker = load_continuation_marker(continuation_marker_path(claimed_issue.worktree_path))
    return marker_failure_location(marker) or location


def lead_comment_with_failure_location(comment_body: str, location: RunLocation) -> str:
    """Lead the human-intervention comment with the same pair the terminal printed."""
    failure_line = format_run_failure_line(location)
    if comment_body.lstrip().startswith(("`" + failure_line, failure_line)):
        return comment_body
    return f"`{failure_line}`\n\n{comment_body}"


def ensure_preserved_branch_link_in_comment(
        comment_body: str,
        preservation_result: FailurePreservationResult | None,
) -> str:
    """Ensure the issue comment links to the branch that preserves failed work."""
    if preservation_result is None or preservation_result.branch_url in comment_body:
        return comment_body
    return (
        f"{comment_body.rstrip()}\n\n"
        "Preserved work branch:\n"
        f"{preservation_result.branch_url}"
    )


def post_human_intervention_comment_and_label(
        issue_number: int,
        comment_body: str | None,
        *,
        resumable: bool = False,
) -> None:
    """Post the human-intervention comment and always attempt to apply the label."""
    if is_user_interrupt_requested():
        return

    if comment_body:
        try:
            post_issue_comment(issue_number, comment_body)
        except Exception as exc:
            print(
                f"ERROR: Failed to post human-intervention comment to issue #{issue_number}: {exc!r}",
                file=sys.stderr,
            )
            traceback.print_exc()

    try:
        add_issue_label(issue_number, LABEL_HUMAN_INTERVENTION)
    except Exception as exc:
        print(
            f"ERROR: Failed to add '{LABEL_HUMAN_INTERVENTION}' label to issue #{issue_number}: {exc!r}",
            file=sys.stderr,
        )
        traceback.print_exc()

    if resumable:
        try:
            add_issue_label(issue_number, LABEL_RESUMABLE)
        except Exception as exc:
            print(
                f"ERROR: Failed to add '{LABEL_RESUMABLE}' label to issue #{issue_number}: {exc!r}",
                file=sys.stderr,
            )
            traceback.print_exc()


def preservation_result_has_continuation_marker(preservation_result: FailurePreservationResult | None) -> bool:
    """Return True when preserved work carries a continuation marker."""
    if preservation_result is None:
        return False
    return os.path.isfile(continuation_marker_path(preservation_result.reviewable_worktree_path))


def load_preservation_result_continuation_marker(
        preservation_result: FailurePreservationResult | None,
) -> ContinuationMarker | None:
    """Load the continuation marker carried by preserved work."""
    if preservation_result is None or preservation_result.reviewable_worktree_path is None:
        return None
    return load_continuation_marker(
        continuation_marker_path(preservation_result.reviewable_worktree_path),
    )


def maybe_apply_human_intervention_follow_up(
        claimed_issue: ClaimedIssue,
        workflow_success: bool = True,
        started_at: float | None = None,
        preservation_result: FailurePreservationResult | None = None,
) -> bool:
    """Post a human-intervention comment and label when an issue run needs follow-up."""
    if is_user_interrupt_requested():
        return False

    candidate = resolve_human_intervention_candidate(claimed_issue, workflow_success)
    if candidate is None:
        return False

    try:
        comment_body = run_human_intervention_analysis(
            claimed_issue,
            candidate,
            started_at,
            preservation_result,
        )
        comment_body = ensure_preserved_branch_link_in_comment(comment_body, preservation_result)
    except Exception as exc:
        print(
            f"ERROR: Failed to apply human-intervention follow-up to issue #{claimed_issue.issue['number']}: {exc!r}",
            file=sys.stderr,
        )
        traceback.print_exc()
        comment_body = None
    if is_user_interrupt_requested():
        return False
    post_human_intervention_comment_and_label(
        claimed_issue.issue["number"],
        comment_body,
        resumable=preservation_result_has_continuation_marker(preservation_result),
    )
    return True


def _build_finalization_failure_comment(claimed_issue: ClaimedIssue) -> str:
    """Explain a post-success finalization/publication failure for maintainers.

    The workflow produced a PR-ready result, so there is no generation gap to
    analyze; publication simply did not complete. The preserved branch carries a
    continuation marker, so this issue is resumable. §FS-forge-run-continuation
    """
    return (
        f"Automated generation for `{claimed_issue.issue_coordinates}` completed, but "
        "publishing the pull request did not finish during finalization (for example a "
        "publication `git rebase` that could not complete on the work branch). The "
        "generated work and a continuation marker are preserved on the branch below, so "
        f"a later Forge run can resume from the publication phase; the `{LABEL_RESUMABLE}` "
        "label marks it for that resume. A maintainer can also finish publication "
        "manually from the preserved branch."
    )


def apply_failed_run_follow_up(
        claimed_issue: ClaimedIssue,
        started_at: float | None = None,
        preservation_result: FailurePreservationResult | None = None,
        failure_location: RunLocation | None = None,
) -> None:
    """Run Codex failure analysis and apply the `human-intervention` follow-up.

    Only reached for logical failures; external failures are released upstream
    without any issue action. A successful workflow that fails during
    finalization/publication carries no analysis candidate but still preserves a
    continuation marker, so it is labeled resumable. §FS-human-intervention-policy
    """
    if is_user_interrupt_requested():
        raise KeyboardInterrupt

    candidate = resolve_human_intervention_candidate(
        claimed_issue,
        workflow_success=False,
    )
    if candidate is None:
        marker = load_preservation_result_continuation_marker(preservation_result)
        # A successful workflow that fails during publication has no generation-analysis
        # candidate. Gate this fallback on the authoritative marker phase so earlier
        # workflow failures are not presented as PR-ready. §FS-forge-run-continuation.2
        if marker is not None and marker.continue_from == PHASE_PUBLICATION:
            post_human_intervention_comment_and_label(
                claimed_issue.issue["number"],
                ensure_preserved_branch_link_in_comment(
                    _lead_failed_run_comment(
                        _build_finalization_failure_comment(claimed_issue),
                        failure_location,
                    ),
                    preservation_result,
                ),
                resumable=True,
            )
        return

    try:
        comment_body = run_codex_failed_generation_analysis(
            claimed_issue,
            candidate,
            started_at,
            preservation_result,
        )
        # The comment leads with the same pair the terminal printed.
        # §FS-forge-run-location-reporting.3
        comment_body = _lead_failed_run_comment(comment_body, failure_location)
        comment_body = ensure_preserved_branch_link_in_comment(comment_body, preservation_result)
    except Exception as exc:
        print(
            f"ERROR: Failed to apply failed-run follow-up to issue #{claimed_issue.issue['number']}: {exc!r}",
            file=sys.stderr,
        )
        traceback.print_exc()
        comment_body = None if failure_location is None else _lead_failed_run_comment("", failure_location)
    if is_user_interrupt_requested():
        raise KeyboardInterrupt
    post_human_intervention_comment_and_label(
        claimed_issue.issue["number"],
        comment_body,
        resumable=preservation_result_has_continuation_marker(preservation_result),
    )


def _lead_failed_run_comment(comment_body: str, failure_location: RunLocation | None) -> str:
    """Prefix a failed-run comment with its run location when one is known."""
    if failure_location is None:
        return comment_body
    return lead_comment_with_failure_location(comment_body, failure_location).rstrip() + "\n"


def dynamic_access_chunk_class_threshold() -> int:
    """Return the configured chunked dynamic-access class threshold."""
    raw_value = os.environ.get("FORGE_DYNAMIC_ACCESS_CHUNK_CLASS_THRESHOLD")
    if raw_value is None or raw_value.strip() == "":
        return DEFAULT_DYNAMIC_ACCESS_CHUNK_CLASS_THRESHOLD
    try:
        threshold = int(raw_value)
    except ValueError as exc:
        raise ValueError("FORGE_DYNAMIC_ACCESS_CHUNK_CLASS_THRESHOLD must be an integer") from exc
    if threshold < 1:
        raise ValueError("FORGE_DYNAMIC_ACCESS_CHUNK_CLASS_THRESHOLD must be >= 1")
    return threshold


def _issue_has_chunked_dynamic_access_state(issue: dict) -> bool:
    """Return whether the issue is already in chunked dynamic-access mode."""
    return issue_has_label(issue, LABEL_CHUNKED_DYNAMIC_ACCESS)


def _processed_dynamic_access_classes(report: DynamicAccessExhaustReport | None) -> set[str]:
    """Return classes that a resumed chunk must not select again."""
    if report is None:
        return set()
    return report.processed_classes()


def _continuation_processed_dynamic_access_classes(marker: ContinuationMarker | None) -> set[str]:
    """Return dynamic-access classes recorded in the continuation marker."""
    if marker is None:
        return set()
    explore_phase: dict[str, object] = marker.phases.get(PHASE_EXPLORE, {})
    exhausted_classes: object = explore_phase.get("exhaustedClasses", [])
    if not isinstance(exhausted_classes, list):
        return set()
    return {
        class_name
        for class_name in exhausted_classes
        if isinstance(class_name, str) and class_name
    }


def _dynamic_access_processed_class_count(
        report: DynamicAccessExhaustReport | None,
        marker: ContinuationMarker | None,
) -> int:
    """Return the processed class count used for chunking logs."""
    processed_classes = _processed_dynamic_access_classes(report)
    processed_classes.update(_continuation_processed_dynamic_access_classes(marker))
    return len(processed_classes)


def _continuation_active_chunk_remaining_budget(marker: ContinuationMarker | None) -> int | None:
    """Return the remaining class budget for a resumed active chunk."""
    if marker is None or marker.continue_from != PHASE_EXPLORE:
        return None
    explore_phase: dict[str, object] = marker.phases.get(PHASE_EXPLORE, {})
    raw_chunk_class_count: object = explore_phase.get("chunkClassCount")
    if raw_chunk_class_count is None:
        return None
    try:
        chunk_class_count = int(raw_chunk_class_count)
        chunk_processed_class_count = int(explore_phase.get("chunkProcessedClassCount") or 0)
    except (TypeError, ValueError):
        return None
    if chunk_class_count <= 0:
        return None
    return max(chunk_class_count - max(chunk_processed_class_count, 0), 0)


def _remaining_uncovered_dynamic_access_classes(
        report,
        exhaust_report: DynamicAccessExhaustReport | None,
        continuation_marker: ContinuationMarker | None,
) -> list[str]:
    """Return current uncovered classes not already recorded as processed."""
    processed_classes = _processed_dynamic_access_classes(exhaust_report)
    processed_classes.update(_continuation_processed_dynamic_access_classes(continuation_marker))
    return [
        class_coverage.class_name
        for class_coverage in report.classes
        if class_coverage.uncovered_calls > 0 and class_coverage.class_name not in processed_classes
    ]


def _prepare_new_library_dynamic_access_report(claimed_issue: ClaimedIssue) -> bool:
    """Run new-library setup far enough for the dispatcher dynamic-access report."""
    group, artifact, version = claimed_issue.issue_coordinates.split(":")
    with contextlib.chdir(claimed_issue.worktree_path):
        create_feature_branch_for_library(group, artifact, version)
        if not prepare_native_image_eligible_artifact(
            claimed_issue.worktree_path,
            claimed_issue.issue_coordinates,
        ):
            log_stage(
                "native-image-eligibility",
                (
                    f"{group}:{artifact} is not a Native Image metadata target; "
                    "skipping dispatcher dynamic-access pre-scan"
                ),
            )
            return False
        try:
            run_new_library_scaffold(claimed_issue.issue_coordinates)
        except ScaffoldError as exc:
            print(
                f"ERROR: Dispatcher scaffold failed for {claimed_issue.issue_coordinates}: {exc}",
                file=sys.stderr,
            )
            raise
    return True


def _prepare_library_update_dynamic_access_report(claimed_issue: ClaimedIssue) -> None:
    """Run library-update setup far enough for the dispatcher dynamic-access report."""
    group, artifact, version = claimed_issue.issue_coordinates.split(":")
    prepare_library_update_target(claimed_issue.worktree_path, group, artifact, version)


def _generate_dispatcher_dynamic_access_report(claimed_issue: ClaimedIssue) -> None:
    """Generate or refresh the dynamic-access report used for dispatcher chunking."""
    result = run_logged_command(
        [
            "./gradlew",
            "generateDynamicAccessCoverageReport",
            f"-Pcoordinates={claimed_issue.issue_coordinates}",
        ],
        cwd=claimed_issue.worktree_path,
        task_type="dynamic-access-report",
        subject=claimed_issue.issue_coordinates,
        action="generateDynamicAccessCoverageReport",
        env=gradle_command_environment(claimed_issue.worktree_path),
        stage="dynamic-access",
    )
    if result.returncode != 0:
        log_detail(
            "dynamic-access-chunking",
            (
                "Dispatcher dynamic-access report refresh failed for "
                f"{claimed_issue.issue_coordinates}; workflow fallback/failure handling will decide the run."
            ),
        )


def _load_dispatcher_dynamic_access_report(claimed_issue: ClaimedIssue):
    """Load the dispatcher-generated dynamic-access report if it is usable."""
    try:
        report = load_dynamic_access_coverage_report(_resolve_dynamic_access_report_path(claimed_issue))
    except FileNotFoundError:
        return None
    if not report.has_dynamic_access or report.total_calls <= 0:
        return None
    return report


def _resolve_dynamic_access_exhaust_report(
        claimed_issue: ClaimedIssue,
) -> tuple[DynamicAccessExhaustReport | None, str | None]:
    """Load the existing exhaust report for this issue, if one exists."""
    report_path = find_dynamic_access_exhaust_report_path(
        claimed_issue.worktree_path,
        claimed_issue.issue_coordinates,
    )
    if report_path is not None:
        return DynamicAccessExhaustReport.load(report_path), report_path
    return None, None


def _create_dynamic_access_exhaust_report(
        claimed_issue: ClaimedIssue,
) -> tuple[DynamicAccessExhaustReport, str]:
    """Create the coordinate-derived exhaust report for a newly chunked issue."""
    exhaust_report = DynamicAccessExhaustReport.create(
        coordinate=claimed_issue.issue_coordinates,
        issue_number=claimed_issue.issue["number"],
    )
    return exhaust_report, exhaust_report.default_path(claimed_issue.worktree_path)


def _strategy_has_bulk_phase(strategy_name: str | None) -> bool:
    """Return whether the selected strategy makes the chunk decision after bulk."""
    if not strategy_name:
        return False
    strategy: dict = require_strategy_by_name(strategy_name)
    workflow_name: str | None = strategy.get("workflow")
    return (
        workflow_name == "bulk_dynamic_access"
        or (
            workflow_name == "increase_dynamic_access_coverage"
            and strategy.get("primary-workflow") == "bulk_dynamic_access"
        )
    )


def _continuation_resumes_existing_tree(marker: ContinuationMarker | None) -> bool:
    """Return whether the run resumes a preserved tree the drivers keep as is."""
    return marker is not None and marker.continue_from in RESUMED_TREE_PHASES


def _prepare_dispatcher_dynamic_access_report(claimed_issue: ClaimedIssue) -> bool:
    """Build the dynamic-access report input every chunk-eligible run measures.

    Preparation precedes every chunk decision, including the ones deferred to a
    bulk phase, so no workflow starts against a report that was never
    built (§FS-forge-chunked-dynamic-access).
    """
    if _continuation_resumes_existing_tree(claimed_issue.continuation_marker):
        log_detail(
            "dynamic-access-chunking",
            (
                f"Issue #{claimed_issue.issue['number']} resumes a preserved tree; "
                "refreshing the dynamic-access report without re-preparing it."
            ),
        )
    elif claimed_issue.label == LABEL_LIBRARY_NEW:
        if not _prepare_new_library_dynamic_access_report(claimed_issue):
            return False
    else:
        _prepare_library_update_dynamic_access_report(claimed_issue)
    _generate_dispatcher_dynamic_access_report(claimed_issue)
    return True


def _dispatcher_uncovered_class_count(claimed_issue: ClaimedIssue) -> str:
    """Return the prepared report's uncovered class count, or that it is unavailable.

    A library that reports no dynamic access counts zero; only a report that was
    never written is unavailable (§FS-forge-chunked-dynamic-access).
    """
    try:
        report = load_dynamic_access_coverage_report(_resolve_dynamic_access_report_path(claimed_issue))
    except FileNotFoundError:
        return "unavailable"
    return str(uncovered_dynamic_access_class_count(report))


def prepare_dynamic_access_chunking(
        claimed_issue: ClaimedIssue,
        strategy_name: str | None,
) -> int | None:
    """Return the iterative budget or deferred post-bulk class boundary.

    Every chunk-eligible run prepares the same report input. Iterative-only work
    then keeps dispatcher-owned report selection, while a bulk phase
    receives the configured boundary and decides after its gated bulk loop, when
    its exact progress is known (§FS-forge-chunked-dynamic-access).
    """
    if claimed_issue.label not in {LABEL_LIBRARY_NEW, LABEL_LIBRARY_UPDATE}:
        return None

    threshold: int = dynamic_access_chunk_class_threshold()
    active_chunk_remaining_budget: int | None = _continuation_active_chunk_remaining_budget(
        claimed_issue.continuation_marker,
    )
    if not _prepare_dispatcher_dynamic_access_report(claimed_issue):
        return None

    if _strategy_has_bulk_phase(strategy_name):
        chunk_boundary: int = threshold
        if active_chunk_remaining_budget is not None:
            chunk_boundary = min(chunk_boundary, active_chunk_remaining_budget)
        log_detail(
            "dynamic-access-chunking",
            "Deferring chunk selection for '{strategy}' until its bulk phase completes; "
            "uncovered_classes={uncovered}, class_boundary={boundary}.".format(
                strategy=strategy_name,
                uncovered=_dispatcher_uncovered_class_count(claimed_issue),
                boundary=chunk_boundary,
            ),
        )
        return chunk_boundary

    report = _load_dispatcher_dynamic_access_report(claimed_issue)
    if report is None:
        log_detail(
            "dynamic-access-chunking",
            (
                f"No dispatcher dynamic-access report for issue #{claimed_issue.issue['number']} "
                f"({claimed_issue.issue_coordinates}); chunking disabled for this run."
            ),
        )
        return None

    exhaust_report, exhaust_report_path = _resolve_dynamic_access_exhaust_report(claimed_issue)
    already_chunked = _issue_has_chunked_dynamic_access_state(claimed_issue.issue) or exhaust_report is not None
    current_uncovered_classes = [
        class_coverage.class_name
        for class_coverage in report.classes
        if class_coverage.uncovered_calls > 0
    ]
    remaining_classes = _remaining_uncovered_dynamic_access_classes(
        report,
        exhaust_report,
        claimed_issue.continuation_marker,
    )
    processed_class_count = _dynamic_access_processed_class_count(
        exhaust_report,
        claimed_issue.continuation_marker,
    )
    if not already_chunked and len(current_uncovered_classes) <= threshold:
        log_detail(
            "dynamic-access-chunking",
            (
                f"Chunking not selected for issue #{claimed_issue.issue['number']}: "
                f"total_uncovered_classes={len(current_uncovered_classes)}, "
                f"processed_classes={processed_class_count}, "
                f"remaining_classes={len(remaining_classes)}, threshold={threshold}."
            ),
        )
        return None

    current_chunk_class_count = min(threshold, len(remaining_classes))
    active_chunk_remaining_budget = _continuation_active_chunk_remaining_budget(
        claimed_issue.continuation_marker
    )
    if active_chunk_remaining_budget is not None:
        current_chunk_class_count = min(current_chunk_class_count, active_chunk_remaining_budget)
    if current_chunk_class_count <= 0:
        log_detail(
            "dynamic-access-chunking",
            (
                f"No chunk selected for issue #{claimed_issue.issue['number']}: "
                f"total_uncovered_classes={len(current_uncovered_classes)}, "
                f"processed_classes={processed_class_count}, "
                f"remaining_classes={len(remaining_classes)}."
            ),
        )
        return None

    if exhaust_report is None or exhaust_report_path is None:
        exhaust_report, exhaust_report_path = _create_dynamic_access_exhaust_report(claimed_issue)
    exhaust_report.update_chunk_limits(threshold, current_chunk_class_count)
    exhaust_report.save(exhaust_report_path)

    if not issue_has_label(claimed_issue.issue, LABEL_CHUNKED_DYNAMIC_ACCESS):
        add_issue_label(claimed_issue.issue["number"], LABEL_CHUNKED_DYNAMIC_ACCESS)
        add_issue_label_to_payload(claimed_issue.issue, LABEL_CHUNKED_DYNAMIC_ACCESS)

    log_detail(
        "dynamic-access-chunking",
        "Chunked dynamic-access selected for issue #{issue_number}: "
        "already_chunked={already_chunked}, total_uncovered_classes={uncovered_count}, "
        "processed_classes={processed_count}, remaining_classes={remaining_count}, "
        "threshold={threshold}, chunk_class_count={chunk_count}, exhaust_report={report_path}.".format(
            issue_number=claimed_issue.issue["number"],
            already_chunked=already_chunked,
            uncovered_count=len(current_uncovered_classes),
            processed_count=processed_class_count,
            remaining_count=len(remaining_classes),
            threshold=threshold,
            chunk_count=current_chunk_class_count,
            report_path=os.path.relpath(exhaust_report_path, claimed_issue.worktree_path),
        ),
    )
    return current_chunk_class_count


def append_chunked_dynamic_access_workflow_args(
        pipeline_argv: list[str],
        claimed_issue: ClaimedIssue,
        chunk_class_count: int | None,
) -> None:
    """Append issue context and concrete chunk limits for oversized dynamic-access runs.

    The dispatcher (§AR-forge-control-plane) computes the issue-scoped chunking
    context and passes only execution flags to the workflow driver, so the
    chunk limits stay consistent with the exhaust report
    (§AR-dynamic-access-exhaust-report).
    """
    issue_number = claimed_issue.issue["number"]
    pipeline_argv.extend(["--issue-number", str(issue_number)])
    if chunk_class_count is not None:
        pipeline_argv.extend(["--chunk-class-count", str(chunk_class_count)])


def run_library_preparation_preflight(
        claimed_issue: ClaimedIssue,
) -> str | None:
    """Run and persist the library-specific preparation preflight before workflow dispatch."""
    if claimed_issue.preflight_info_path is None:
        return None
    return run_preflight_decision(
        claimed_issue=claimed_issue,
        issue_body_provider=get_issue_body,
    )


def append_library_preparation_preflight_arg(pipeline_argv: list[str], preflight_path: str | None) -> None:
    """Append the dispatcher preflight path when one was produced."""
    if preflight_path:
        pipeline_argv.extend(["--library-preparation-preflight-path", preflight_path])


def append_issue_requested_metadata_context_arg(pipeline_argv: list[str], issue_number: int) -> None:
    """Append the reporter's request so every driver can attempt it.

    Routing decides which repair a reported issue needs; it does not decide
    whether the reporter's own request is carried. §forge/AR-forge-drivers.2.1
    """
    issue_requested_metadata_context = extract_issue_requested_metadata_context(get_issue_body(issue_number))
    if issue_requested_metadata_context:
        pipeline_argv.extend(["--issue-requested-metadata-context", issue_requested_metadata_context])


def append_continuation_marker_arg(pipeline_argv: list[str], marker_path: str | None) -> None:
    """Append the continuation marker path when continuation tracking is active."""
    if marker_path:
        pipeline_argv.extend(["--continuation-marker-path", marker_path])


def library_update_route_artifact_root(claimed_issue: ClaimedIssue) -> str:
    """Return the non-worktree artifact root for library-update route handoff."""
    return claimed_issue.preflight_info_path or claimed_issue.scratch_metrics_repo_path


def build_workflow_driver_invocation(
        claimed_issue: ClaimedIssue,
        strategy_name: str | None,
        keep_tests_without_dynamic_access: bool,
        library_preparation_preflight_path: str | None = None,
        chunk_class_count: int | None = None,
        library_update_route: LibraryUpdateRoute | None = None,
        continuation_marker_path: str | None = None,
) -> WorkflowDriverInvocation:
    """Build the routed workflow-driver command for a claimed issue."""
    issue_number = claimed_issue.issue["number"]
    if claimed_issue.label == LABEL_LIBRARY_NEW:
        pipeline_argv = [
            "--coordinates", claimed_issue.issue_coordinates,
            "--reachability-metadata-path", claimed_issue.worktree_path,
            "--metrics-repo-path", claimed_issue.scratch_metrics_repo_path,
        ]
        append_library_preparation_preflight_arg(pipeline_argv, library_preparation_preflight_path)
        append_continuation_marker_arg(pipeline_argv, continuation_marker_path)
        append_issue_requested_metadata_context_arg(pipeline_argv, issue_number)
        if strategy_name:
            pipeline_argv.extend(["--strategy-name", strategy_name])
        if keep_tests_without_dynamic_access:
            pipeline_argv.append("--keep-tests-without-dynamic-access")
        append_chunked_dynamic_access_workflow_args(pipeline_argv, claimed_issue, chunk_class_count)
        return WorkflowDriverInvocation(
            driver_name="add_new_library_support",
            script_name="add_new_library_support.py",
            runner_name="run_add_new_library_support_workflow",
            runner=run_add_new_library_support_workflow,
            argv=pipeline_argv,
            issue_number=issue_number,
            issue_label=claimed_issue.label,
            coordinates=claimed_issue.issue_coordinates,
            current_coordinates=claimed_issue.current_coordinates,
            new_version=claimed_issue.new_version,
            log_stage_name="new-library-workflow",
            log_message=(
                f"Invoking add_new_library_support workflow for issue #{issue_number}: "
                f"{claimed_issue.issue_coordinates}"
            ),
            failure_name="add_new_library_support",
        )

    elif claimed_issue.label == LABEL_JAVAC_FAIL:
        pipeline_argv = [
            "--coordinates", claimed_issue.current_coordinates,
            "--new-version", claimed_issue.new_version,
            "--reachability-metadata-path", claimed_issue.worktree_path,
            "--metrics-repo-path", claimed_issue.scratch_metrics_repo_path,
            "--dynamic-access-class-threshold",
            str(dynamic_access_chunk_class_threshold()),
        ]
        append_library_preparation_preflight_arg(pipeline_argv, library_preparation_preflight_path)
        append_continuation_marker_arg(pipeline_argv, continuation_marker_path)
        append_issue_requested_metadata_context_arg(pipeline_argv, issue_number)
        if strategy_name:
            pipeline_argv.extend(["--strategy-name", strategy_name])
        return WorkflowDriverInvocation(
            driver_name="fix_javac_fail",
            script_name="fix_javac_fail.py",
            runner_name="run_fix_javac_workflow",
            runner=run_fix_javac_workflow,
            argv=pipeline_argv,
            issue_number=issue_number,
            issue_label=claimed_issue.label,
            coordinates=None,
            current_coordinates=claimed_issue.current_coordinates,
            new_version=claimed_issue.new_version,
            log_stage_name="javac-fix-workflow",
            log_message=(
                f"Invoking fix_javac_fail workflow for issue #{issue_number}: "
                f"{claimed_issue.current_coordinates} -> {claimed_issue.new_version}"
            ),
            failure_name="fix_javac",
        )

    elif claimed_issue.label == LABEL_JAVA_RUN_FAIL:
        pipeline_argv = [
            "--coordinates", claimed_issue.current_coordinates,
            "--new-version", claimed_issue.new_version,
            "--reachability-metadata-path", claimed_issue.worktree_path,
            "--metrics-repo-path", claimed_issue.scratch_metrics_repo_path,
            "--dynamic-access-class-threshold",
            str(dynamic_access_chunk_class_threshold()),
        ]
        append_library_preparation_preflight_arg(pipeline_argv, library_preparation_preflight_path)
        append_continuation_marker_arg(pipeline_argv, continuation_marker_path)
        append_issue_requested_metadata_context_arg(pipeline_argv, issue_number)
        if strategy_name:
            pipeline_argv.extend(["--strategy-name", strategy_name])
        return WorkflowDriverInvocation(
            driver_name="fix_java_run_fail",
            script_name="fix_java_run_fail.py",
            runner_name="run_fix_java_run_workflow",
            runner=run_fix_java_run_workflow,
            argv=pipeline_argv,
            issue_number=issue_number,
            issue_label=claimed_issue.label,
            coordinates=None,
            current_coordinates=claimed_issue.current_coordinates,
            new_version=claimed_issue.new_version,
            log_stage_name="java-run-fix-workflow",
            log_message=(
                f"Invoking fix_java_run_fail workflow for issue #{issue_number}: "
                f"{claimed_issue.current_coordinates} -> {claimed_issue.new_version}"
            ),
            failure_name="fix_java_run",
        )

    elif claimed_issue.label == LABEL_NI_RUN_FAIL:
        pipeline_argv = [
            "--coordinates", claimed_issue.current_coordinates,
            "--new-version", claimed_issue.new_version,
            "--reachability-metadata-path", claimed_issue.worktree_path,
            "--metrics-repo-path", claimed_issue.scratch_metrics_repo_path,
        ]
        append_library_preparation_preflight_arg(pipeline_argv, library_preparation_preflight_path)
        append_continuation_marker_arg(pipeline_argv, continuation_marker_path)
        append_issue_requested_metadata_context_arg(pipeline_argv, issue_number)
        if strategy_name:
            pipeline_argv.extend(["--strategy-name", strategy_name])
        return WorkflowDriverInvocation(
            driver_name="fix_ni_run",
            script_name="fix_ni_run.py",
            runner_name="run_fix_ni_run_workflow",
            runner=run_fix_ni_run_workflow,
            argv=pipeline_argv,
            issue_number=issue_number,
            issue_label=claimed_issue.label,
            coordinates=None,
            current_coordinates=claimed_issue.current_coordinates,
            new_version=claimed_issue.new_version,
            log_stage_name="native-image-fix-workflow",
            log_message=(
                f"Invoking fix_ni_run workflow for issue #{issue_number}: "
                f"{claimed_issue.current_coordinates} -> {claimed_issue.new_version}"
            ),
            failure_name="fix_ni_run",
        )

    elif claimed_issue.label == LABEL_LIBRARY_UPDATE:
        if library_update_route is None:
            raise ValueError("Library-update workflow invocation requires a selected route.")
        route = library_update_route
        if route.selected_driver == ROUTE_FIX_JAVAC:
            if route.baseline_coordinates is None:
                raise ValueError(f"Missing baseline coordinates for route {route.selected_driver}.")
            pipeline_argv = [
                "--coordinates", route.baseline_coordinates,
                "--new-version", route.new_version,
                "--reachability-metadata-path", claimed_issue.worktree_path,
                "--metrics-repo-path", claimed_issue.scratch_metrics_repo_path,
                "--dynamic-access-class-threshold",
                str(dynamic_access_chunk_class_threshold()),
            ]
            append_library_preparation_preflight_arg(pipeline_argv, library_preparation_preflight_path)
            append_continuation_marker_arg(pipeline_argv, continuation_marker_path)
            append_issue_requested_metadata_context_arg(pipeline_argv, issue_number)
            return WorkflowDriverInvocation(
                driver_name="fix_javac_fail",
                script_name="fix_javac_fail.py",
                runner_name="run_fix_javac_workflow",
                runner=run_fix_javac_workflow,
                argv=pipeline_argv,
                issue_number=issue_number,
                issue_label=claimed_issue.label,
                coordinates=None,
                current_coordinates=route.baseline_coordinates,
                new_version=route.new_version,
                log_stage_name="javac-fix-workflow",
                log_message=(
                    f"Invoking fix_javac_fail workflow for library-update issue #{issue_number}: "
                    f"{route.baseline_coordinates} -> {route.new_version}"
                ),
                failure_name="fix_javac",
            )
        if route.selected_driver == ROUTE_FIX_JAVA_RUN:
            if route.baseline_coordinates is None:
                raise ValueError(f"Missing baseline coordinates for route {route.selected_driver}.")
            pipeline_argv = [
                "--coordinates", route.baseline_coordinates,
                "--new-version", route.new_version,
                "--reachability-metadata-path", claimed_issue.worktree_path,
                "--metrics-repo-path", claimed_issue.scratch_metrics_repo_path,
                "--dynamic-access-class-threshold",
                str(dynamic_access_chunk_class_threshold()),
            ]
            append_library_preparation_preflight_arg(pipeline_argv, library_preparation_preflight_path)
            append_continuation_marker_arg(pipeline_argv, continuation_marker_path)
            append_issue_requested_metadata_context_arg(pipeline_argv, issue_number)
            return WorkflowDriverInvocation(
                driver_name="fix_java_run_fail",
                script_name="fix_java_run_fail.py",
                runner_name="run_fix_java_run_workflow",
                runner=run_fix_java_run_workflow,
                argv=pipeline_argv,
                issue_number=issue_number,
                issue_label=claimed_issue.label,
                coordinates=None,
                current_coordinates=route.baseline_coordinates,
                new_version=route.new_version,
                log_stage_name="java-run-fix-workflow",
                log_message=(
                    f"Invoking fix_java_run_fail workflow for library-update issue #{issue_number}: "
                    f"{route.baseline_coordinates} -> {route.new_version}"
                ),
                failure_name="fix_java_run",
            )
        if route.selected_driver == ROUTE_FIX_NI_RUN:
            if route.baseline_coordinates is None:
                raise ValueError(f"Missing baseline coordinates for route {route.selected_driver}.")
            pipeline_argv = [
                "--coordinates", route.baseline_coordinates,
                "--new-version", route.new_version,
                "--reachability-metadata-path", claimed_issue.worktree_path,
                "--metrics-repo-path", claimed_issue.scratch_metrics_repo_path,
            ]
            append_library_preparation_preflight_arg(pipeline_argv, library_preparation_preflight_path)
            append_continuation_marker_arg(pipeline_argv, continuation_marker_path)
            append_issue_requested_metadata_context_arg(pipeline_argv, issue_number)
            return WorkflowDriverInvocation(
                driver_name="fix_ni_run",
                script_name="fix_ni_run.py",
                runner_name="run_fix_ni_run_workflow",
                runner=run_fix_ni_run_workflow,
                argv=pipeline_argv,
                issue_number=issue_number,
                issue_label=claimed_issue.label,
                coordinates=None,
                current_coordinates=route.baseline_coordinates,
                new_version=route.new_version,
                log_stage_name="native-image-fix-workflow",
                log_message=(
                    f"Invoking fix_ni_run workflow for library-update issue #{issue_number}: "
                    f"{route.baseline_coordinates} -> {route.new_version}"
                ),
                failure_name="fix_ni_run",
            )
        if route.selected_driver != ROUTE_IMPROVE_COVERAGE:
            raise ValueError(f"Unknown library-update route '{route.selected_driver}'")
        pipeline_argv = [
            "--coordinates", claimed_issue.issue_coordinates,
            "--reachability-metadata-path", claimed_issue.worktree_path,
            "--metrics-repo-path", claimed_issue.scratch_metrics_repo_path,
        ]
        append_library_preparation_preflight_arg(pipeline_argv, library_preparation_preflight_path)
        append_continuation_marker_arg(pipeline_argv, continuation_marker_path)
        append_issue_requested_metadata_context_arg(pipeline_argv, issue_number)
        if strategy_name:
            pipeline_argv.extend(["--strategy-name", strategy_name])
        append_chunked_dynamic_access_workflow_args(pipeline_argv, claimed_issue, chunk_class_count)
        return WorkflowDriverInvocation(
            driver_name="improve_library_coverage",
            script_name="improve_library_coverage.py",
            runner_name="run_improve_library_coverage_workflow",
            runner=run_improve_library_coverage_workflow,
            argv=pipeline_argv,
            issue_number=issue_number,
            issue_label=claimed_issue.label,
            coordinates=claimed_issue.issue_coordinates,
            current_coordinates=route.baseline_coordinates,
            new_version=route.new_version,
            log_stage_name="improve-coverage-workflow",
            log_message=(
                f"Invoking improve_library_coverage workflow for issue #{issue_number}: "
                f"{claimed_issue.issue_coordinates}"
            ),
            failure_name="improve_library_coverage",
        )

    raise ValueError(f"Unknown label '{claimed_issue.label}'")


def resolve_workflow_default_strategy_name(
        claimed_issue: ClaimedIssue,
        library_update_route: LibraryUpdateRoute | None,
) -> str:
    """Return the selected workflow driver.s default strategy for durable run state.

    An unresolvable strategy raises rather than failing one issue at a time
    (§FS-forge-run-requirements.1). Omitted strategy overrides remain omitted at
    dispatch (§FS-forge-predefined-strategy-contract).
    """
    if claimed_issue.label == LABEL_LIBRARY_NEW:
        return DEFAULT_NEW_LIBRARY_STRATEGY_NAME
    if claimed_issue.label == LABEL_JAVAC_FAIL:
        return DEFAULT_JAVAC_STRATEGY
    if claimed_issue.label == LABEL_JAVA_RUN_FAIL:
        return DEFAULT_JAVA_RUN_STRATEGY
    if claimed_issue.label == LABEL_NI_RUN_FAIL:
        return DEFAULT_NI_RUN_STRATEGY_NAME
    if claimed_issue.label != LABEL_LIBRARY_UPDATE or library_update_route is None:
        raise ValueError(f"Cannot resolve workflow default strategy for label '{claimed_issue.label}'")
    if library_update_route.selected_driver == ROUTE_FIX_JAVAC:
        return DEFAULT_JAVAC_STRATEGY
    if library_update_route.selected_driver == ROUTE_FIX_JAVA_RUN:
        return DEFAULT_JAVA_RUN_STRATEGY
    if library_update_route.selected_driver == ROUTE_FIX_NI_RUN:
        return DEFAULT_NI_RUN_STRATEGY_NAME
    if library_update_route.selected_driver == ROUTE_IMPROVE_COVERAGE:
        return DEFAULT_LIBRARY_UPDATE_STRATEGY_NAME
    raise ValueError(f"Unknown library-update route '{library_update_route.selected_driver}'")


def resolve_run_strategy_name(
        claimed_issue: ClaimedIssue,
        library_update_route: LibraryUpdateRoute | None,
        strategy_override: str | None,
) -> str:
    """Resolve the strategy recorded before preflight and workflow dispatch."""
    default_strategy_name = resolve_workflow_default_strategy_name(claimed_issue, library_update_route)
    if (
            claimed_issue.label == LABEL_LIBRARY_UPDATE
            and library_update_route is not None
            and library_update_route.selected_driver != ROUTE_IMPROVE_COVERAGE
    ):
        return default_strategy_name
    return strategy_override or default_strategy_name


@pipeline_step(
    PHASE_CLAIM,
    STEP_ROUTE_TO_DRIVER,
    operand=lambda arguments: f"issue #{arguments['claimed_issue'].issue['number']}",
)
def invoke_pipeline(
        claimed_issue: ClaimedIssue,
        strategy_name: str | None,
        keep_tests_without_dynamic_access: bool,
) -> bool:
    """Run the matching workflow driver in the claimed issue's worktree.

    Label-based routing stays in the dispatcher (§AR-forge-control-plane);
    workflow drivers then own the run end to end
    (§AR-forge-workflow-boundary), receiving resolved coordinates, paths,
    strategy names, and chunk context.
    """
    issue_number = claimed_issue.issue["number"]
    log_step_progress(
        PHASE_CLAIM,
        STEP_ROUTE_TO_DRIVER,
        f"Routing issue #{issue_number} to its workflow driver",
    )
    require_claimed_issue_worktree(claimed_issue, "workflow execution")
    strategy_override = strategy_name
    if claimed_issue.continuation_marker is not None:
        strategy_override = claimed_issue.continuation_marker.strategy_name

    library_update_route = None
    if claimed_issue.label == LABEL_LIBRARY_UPDATE:
        library_update_route = restore_library_update_route_from_marker(
            claimed_issue,
            claimed_issue.continuation_marker,
        )
        if library_update_route is None:
            library_update_route = select_library_update_route(
                claimed_issue.worktree_path,
                library_update_route_artifact_root(claimed_issue),
                claimed_issue.issue_coordinates,
            )

    run_strategy_name = resolve_run_strategy_name(
        claimed_issue,
        library_update_route,
        strategy_override,
    )
    log_step_progress(
        PHASE_CLAIM,
        STEP_ROUTE_TO_DRIVER,
        f"Issue #{issue_number} routed to {claimed_issue.label} with strategy {run_strategy_name}",
    )
    continuation_path = create_or_load_run_continuation_marker(
        claimed_issue,
        run_strategy_name,
    )
    # Failures recorded from here on travel to the preserved branch in the
    # marker. §FS-forge-run-location-reporting.3
    bind_continuation_marker(continuation_path)
    marker = load_continuation_marker(continuation_path)
    record_library_update_route_in_marker(continuation_path, library_update_route)
    if marker is not None and marker.continue_from == PHASE_PUBLICATION:
        restore_library_update_route_from_marker(claimed_issue, marker, required=True)
        log_stage(
            "continuation",
            f"Issue #{claimed_issue.issue['number']} resumes at publication; skipping workflow driver.",
        )
        return True
    setup_phase = {} if marker is None else marker.phases.get("setup", {})
    if bool(setup_phase.get("preflightDone")):
        library_preparation_preflight_path = restore_library_preparation_preflight_from_marker(
            claimed_issue,
            marker,
        )
        log_detail(
            "continuation",
            f"Issue #{claimed_issue.issue['number']} skips completed setup preflight.",
        )
        log_step_progress(
            PHASE_SETUP,
            STEP_NEURAL_SETUP,
            f"Reusing completed library preflight for {claimed_issue.issue_coordinates}",
        )
    else:
        # The preflight agent is the run's neural setup, and it runs before the
        # driver prepares the tree. §FS-forge-run-location-reporting.1
        with run_step(PHASE_SETUP, STEP_NEURAL_SETUP, operand=claimed_issue.issue_coordinates):
            library_preparation_preflight_path = run_library_preparation_preflight(
                claimed_issue,
            )
        record_library_preparation_preflight_in_marker(
            continuation_path,
            library_preparation_preflight_path,
        )
    chunk_class_count = None
    if (
            claimed_issue.label == LABEL_LIBRARY_NEW
            or (
                    claimed_issue.label == LABEL_LIBRARY_UPDATE
                    and library_update_route is not None
                    and library_update_route.selected_driver == ROUTE_IMPROVE_COVERAGE
            )
    ):
        with run_step(PHASE_SETUP, STEP_NORMAL_SETUP, operand=claimed_issue.issue_coordinates):
            log_step_progress(
                PHASE_SETUP,
                STEP_NORMAL_SETUP,
                f"Inspecting dynamic access for {claimed_issue.issue_coordinates}",
            )
            chunk_class_count = prepare_dynamic_access_chunking(
                claimed_issue,
                run_strategy_name,
            )
            uncovered_classes = _dispatcher_uncovered_class_count(claimed_issue)
            budget_suffix = f", class budget {chunk_class_count}" if chunk_class_count is not None else ""
            inspection_result = (
                "report unavailable"
                if uncovered_classes == "unavailable"
                else f"{uncovered_classes} uncovered classes{budget_suffix}"
            )
            log_step_progress(
                PHASE_SETUP,
                STEP_NORMAL_SETUP,
                f"Dynamic-access inspection ready for {claimed_issue.issue_coordinates}: "
                f"{inspection_result}",
            )
    invocation = build_workflow_driver_invocation(
            claimed_issue,
            strategy_override,
            keep_tests_without_dynamic_access,
            library_preparation_preflight_path,
            chunk_class_count,
            library_update_route,
            continuation_path,
        )
    if debug_logging_enabled() and "--verbose" not in invocation.argv:
        invocation.argv.append("--verbose")

    log_detail(invocation.log_stage_name, invocation.log_message)
    if is_fixture_testing_enabled():
        display_argv = list(invocation.argv)
        if "--issue-requested-metadata-context" in display_argv:
            context_flag_index = display_argv.index("--issue-requested-metadata-context")
            del display_argv[context_flag_index:context_flag_index + 2]
        log_stage(
            "workflow-driver",
            (
                f"Fixture mode invoking {invocation.script_name}: "
                f"{' '.join(shlex.quote(argument) for argument in display_argv)}"
            ),
        )
    rc = invocation.runner(invocation.argv)
    if is_interrupt_exit_code(rc):
        preserve_user_interrupt_reason()
        raise KeyboardInterrupt
    if rc != 0:
        # A driver that returns non-zero has already recorded the step it failed
        # in; the location leads its error. §FS-forge-run-location-reporting.3
        report_run_failure(
            resolve_failure_location(),
            (
                f"ERROR: {invocation.failure_name} workflow failed for issue "
                f"#{invocation.issue_number} (exit {rc})"
            ),
        )
        return False

    print()
    log_stage("pipeline", f"Pipeline succeeded for issue #{invocation.issue_number}")
    return True


def get_work_queue_configs_from_environment(
        work_strategy_name_override: str | None = None,
        random_offset_override: bool | None = None,
) -> list[WorkQueueConfig]:
    """Return issue work queue configuration from the FORGE_* environment."""
    work_label = os.environ.get("FORGE_WORK_LABEL", LABEL_LIBRARY_NEW)
    if work_label not in PIPELINE_LABELS:
        print(f"ERROR: FORGE_WORK_LABEL must be one of {sorted(PIPELINE_LABELS)}.", file=sys.stderr)
        sys.exit(1)

    return [
        WorkQueueConfig(
            label=LABEL_JAVAC_FAIL,
            limit=get_env_non_negative_int("FORGE_JAVAC_WORK_LIMIT", 1),
            strategy_name=os.environ.get("FORGE_JAVAC_STRATEGY_NAME") or None,
        ),
        WorkQueueConfig(
            label=LABEL_JAVA_RUN_FAIL,
            limit=get_env_non_negative_int("FORGE_JAVA_RUN_WORK_LIMIT", 1),
            strategy_name=os.environ.get("FORGE_JAVA_RUN_STRATEGY_NAME") or None,
        ),
        WorkQueueConfig(
            label=LABEL_NI_RUN_FAIL,
            limit=get_env_non_negative_int("FORGE_NI_RUN_WORK_LIMIT", 1),
            strategy_name=os.environ.get("FORGE_NI_RUN_STRATEGY_NAME") or None,
        ),
        WorkQueueConfig(
            label=LABEL_LIBRARY_UPDATE,
            limit=get_env_non_negative_int("FORGE_LIBRARY_UPDATE_WORK_LIMIT", 1),
            strategy_name=os.environ.get("FORGE_LIBRARY_UPDATE_STRATEGY_NAME") or None,
        ),
        WorkQueueConfig(
            label=work_label,
            limit=get_env_non_negative_int("FORGE_WORK_LIMIT", 1),
            strategy_name=(
                work_strategy_name_override
                or os.environ.get("FORGE_STRATEGY_NAME")
                or DEFAULT_WORK_QUEUE_STRATEGY_NAME
            ),
            random_offset=(
                random_offset_override
                if random_offset_override is not None
                else get_env_zero_one_bool("FORGE_RANDOM_WORK_OFFSET", False)
            ),
        ),
    ]


def get_review_queue_configs_from_environment() -> list[ReviewQueueConfig]:
    """Return pull request review queue configurations from the FORGE_* environment."""
    review_label = os.environ.get("FORGE_REVIEW_LABEL")
    review_limit = get_env_non_negative_int("FORGE_REVIEW_LIMIT", 1)
    if review_label:
        return [
            ReviewQueueConfig(
                label=review_label,
                limit=review_limit,
            )
        ]

    return [
        ReviewQueueConfig(
            label=LABEL_LIBRARY_NEW,
            limit=get_env_non_negative_int("FORGE_LIBRARY_REVIEW_LIMIT", review_limit),
        ),
        ReviewQueueConfig(
            label=LABEL_PR_JAVAC_FIX,
            limit=get_env_non_negative_int("FORGE_JAVAC_REVIEW_LIMIT", review_limit),
        ),
        ReviewQueueConfig(
            label=LABEL_PR_JAVA_RUN_FIX,
            limit=get_env_non_negative_int("FORGE_JAVA_RUN_REVIEW_LIMIT", review_limit),
        ),
        ReviewQueueConfig(
            label=LABEL_PR_NI_RUN_FIX,
            limit=get_env_non_negative_int("FORGE_NI_RUN_REVIEW_LIMIT", review_limit),
        ),
        ReviewQueueConfig(
            label=LABEL_PR_LIBRARY_UPDATE,
            limit=get_env_non_negative_int("FORGE_LIBRARY_UPDATE_REVIEW_LIMIT", review_limit),
        ),
        ReviewQueueConfig(
            label=LABEL_PR_CODE_COVERAGE,
            limit=get_env_non_negative_int("FORGE_BENCHMARK_REVIEW_LIMIT", review_limit),
        ),
    ]


@pipeline_step(PHASE_CLAIM, STEP_CHECK_STRATEGY_AND_MODEL)
def validate_work_queue_strategies(queue_configs: list[WorkQueueConfig]) -> None:
    """Validate strategy names configured for enabled issue queues."""
    log_step_progress(
        PHASE_CLAIM,
        STEP_CHECK_STRATEGY_AND_MODEL,
        "Checking configured strategies",
    )
    seen_strategy_names: set[str] = set()
    for queue_config in queue_configs:
        strategy_name = queue_config.strategy_name
        if queue_config.limit <= 0 or not strategy_name or strategy_name in seen_strategy_names:
            continue
        require_strategy_by_name(strategy_name)
        seen_strategy_names.add(strategy_name)
    log_step_progress(
        PHASE_CLAIM,
        STEP_CHECK_STRATEGY_AND_MODEL,
        f"Configured strategies accepted: {len(seen_strategy_names)}",
    )


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


def sleep_until_shutdown_or_timeout(period_seconds: int) -> bool:
    """Sleep for up to the requested period and return True if shutdown was requested."""
    deadline = time.monotonic() + period_seconds
    while True:
        if is_shutdown_requested():
            log_stage(
                "shutdown",
                f"Stop marker exists at {describe_active_shutdown_signal_path()}; exiting sleep",
            )
            return True
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            return False
        time.sleep(min(remaining, SHUTDOWN_SIGNAL_POLL_SECONDS))


def build_issue_run_id(issue_number: int) -> str:
    """Create a unique ID for an isolated issue run."""
    return f"{issue_number}-{uuid.uuid4().hex[:8]}"


def create_preflight_info_dir(worktree_path: str) -> str:
    """Create the ignored per-run directory for preflight handoff and evidence."""
    run_id = os.path.basename(os.path.abspath(worktree_path))
    preflight_info_path = os.path.join(
        get_repo_root(),
        "local_repositories",
        PREFLIGHT_INFO_DIRNAME,
        run_id,
    )
    os.makedirs(preflight_info_path, exist_ok=True)
    return preflight_info_path


def build_review_run_id(pr_number: int) -> str:
    """Create a unique ID for an isolated pull-request review run."""
    return f"pr-{pr_number}-{uuid.uuid4().hex[:8]}"


def create_detached_worktree(
        repo_path: str,
        worktree_path: str,
        start_ref: str,
        error_message: str,
) -> None:
    """Create a detached worktree from the requested starting ref."""
    try:
        subprocess.run(
            ["git", "worktree", "add", "--detach", worktree_path, start_ref],
            cwd=repo_path,
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
        )
    except subprocess.CalledProcessError as exc:
        print(f"ERROR: {error_message}: {exc.stdout}", file=sys.stderr)
        raise


def fetch_default_base_ref(repo_path: str, operation: str) -> str:
    """Refresh and return the remote-tracking ref for the default issue base."""
    remote_tracking_ref = f"refs/remotes/origin/{DEFAULT_WORKTREE_BASE_REF}"
    try:
        run_git_transport(
            [
                "fetch",
                "--quiet",
                "origin",
                f"+{DEFAULT_WORKTREE_BASE_REF}:{remote_tracking_ref}",
            ],
            cwd=repo_path,
        )
    except GitTransportError as exc:
        print(
            f"ERROR: Failed to fetch origin/{DEFAULT_WORKTREE_BASE_REF} before {operation}: {exc}",
            file=sys.stderr,
        )
        raise
    return remote_tracking_ref


def resolve_git_commit(repo_path: str, ref: str) -> str:
    """Resolve one Git ref to an immutable commit SHA."""
    try:
        result = subprocess.run(
            ["git", "rev-parse", "--verify", f"{ref}^{{commit}}"],
            cwd=repo_path,
            env=git_env_limited_to_repo_root(repo_path),
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
        )
    except subprocess.CalledProcessError as exc:
        print(f"ERROR: Failed to resolve Git commit {ref}: {exc.stdout}", file=sys.stderr)
        raise
    commit = result.stdout.strip()
    if not commit:
        print(f"ERROR: Git ref {ref} resolved to an empty commit SHA", file=sys.stderr)
        raise RuntimeError(f"Git ref {ref} resolved to an empty commit SHA")
    return commit


def fetch_issue_base_commit(repo_path: str) -> str:
    """Fetch and pin the current origin/master commit before an issue claim.

    The checkout containing Forge may be on a feature branch; only the fetched
    remote-tracking ref supplies repository state for issue work
    (§FS-forge-run-requirements.2).
    """
    log_debug(
        "claim",
        f"Fetching newest origin/{DEFAULT_WORKTREE_BASE_REF} for the next issue claim",
    )
    remote_tracking_ref = fetch_default_base_ref(repo_path, "issue claim")
    commit = resolve_git_commit(repo_path, remote_tracking_ref)
    log_debug(
        "claim",
        f"Pinned origin/{DEFAULT_WORKTREE_BASE_REF} at {commit[:12]}",
    )
    return commit


def fetch_review_base_ref(repo_path: str) -> None:
    """Refresh the upstream PR base ref used by local review diffs."""
    fetch_default_base_ref(repo_path, "PR review")


def remove_worktree(repo_path: str, worktree_path: str) -> None:
    """Remove a detached worktree when it exists."""
    subprocess.run(
        ["git", "worktree", "remove", "--force", worktree_path],
        cwd=repo_path,
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )


def create_review_workspace(base_reachability_metadata_path: str, pr_number: int) -> str:
    """Create an isolated review worktree and detach it at the target pull request."""
    repo_root = get_repo_root()
    review_worktrees_root = os.path.join(repo_root, "local_repositories", SCRATCH_REVIEW_WORKTREE_DIRNAME)
    os.makedirs(review_worktrees_root, exist_ok=True)

    review_run_id = build_review_run_id(pr_number)
    review_worktree_path = os.path.join(review_worktrees_root, review_run_id)
    fetch_review_base_ref(base_reachability_metadata_path)
    create_detached_worktree(
        base_reachability_metadata_path,
        review_worktree_path,
        DEFAULT_WORKTREE_BASE_REF,
        f"Failed to create review worktree for PR #{pr_number}",
    )
    try:
        gh(
            "pr",
            "checkout",
            str(pr_number),
            "--detach",
            cwd=review_worktree_path,
            check=True,
        )
    except subprocess.CalledProcessError as exc:
        remove_worktree(base_reachability_metadata_path, review_worktree_path)
        print(f"ERROR: Failed to check out PR #{pr_number} in review worktree: {exc.stdout}", file=sys.stderr)
        raise
    try:
        require_complete_reachability_repo(review_worktree_path)
    except SystemExit:
        remove_worktree(base_reachability_metadata_path, review_worktree_path)
        raise
    return review_worktree_path


def cleanup_review_workspace(
        base_reachability_metadata_path: str,
        review_worktree_path: str,
        pr_number: int,
) -> None:
    """Remove an isolated pull-request review worktree."""
    try:
        remove_worktree(base_reachability_metadata_path, review_worktree_path)
    except Exception as exc:
        print(
            f"ERROR: Failed to clean up review workspace for PR #{pr_number}: {exc!r}",
            file=sys.stderr,
        )


@pipeline_step(
    PHASE_CLAIM,
    STEP_CREATE_ISSUE_WORKSPACE,
    operand=lambda arguments: f"issue #{arguments['issue_number']}",
)
def create_issue_workspace(
        base_reachability_metadata_path: str,
        canonical_metrics_repo_path: str,
        issue_number: int,
        issue_base_commit: str = DEFAULT_WORKTREE_BASE_REF,
) -> tuple[str, str]:
    """Create an isolated issue worktree from the pinned repository base.

    §FS-forge-run-requirements.4
    """
    log_step_progress(
        PHASE_CLAIM,
        STEP_CREATE_ISSUE_WORKSPACE,
        f"Creating workspace for issue #{issue_number} from newest master {issue_base_commit[:12]}",
    )
    log_debug(
        "claim",
        f"Workspace base for issue #{issue_number}: {issue_base_commit}",
    )
    repo_root = get_repo_root()
    worktrees_root = os.path.join(repo_root, "local_repositories", SCRATCH_WORKTREE_DIRNAME)
    os.makedirs(worktrees_root, exist_ok=True)

    run_id = build_issue_run_id(issue_number)
    worktree_path = os.path.join(worktrees_root, run_id)

    create_detached_worktree(
        base_reachability_metadata_path,
        worktree_path,
        issue_base_commit,
        f"Failed to create worktree for issue #{issue_number} from {issue_base_commit}",
    )
    try:
        require_complete_reachability_repo(worktree_path)
    except SystemExit:
        remove_worktree(base_reachability_metadata_path, worktree_path)
        raise
    log_step_progress(
        PHASE_CLAIM,
        STEP_CREATE_ISSUE_WORKSPACE,
        f"Workspace created for issue #{issue_number}: {os.path.relpath(worktree_path, repo_root)}",
    )

    scratch_metrics_repo_path = os.path.join(worktree_path, get_forge_subdir_name())
    del canonical_metrics_repo_path, issue_number
    return worktree_path, scratch_metrics_repo_path


def cleanup_issue_workspace(claimed_issue: ClaimedIssue, canonical_metrics_repo_path: str) -> None:
    """Remove the isolated issue worktrees for reachability-metadata and metrics."""
    del canonical_metrics_repo_path
    if claimed_issue.preflight_info_path:
        shutil.rmtree(claimed_issue.preflight_info_path, ignore_errors=True)
    if claimed_issue.worktree_path in preservation_failed_worktree_paths:
        print(
            (
                f"[Keeping worktree for issue #{claimed_issue.issue['number']} because failed work "
                "could not be pushed; cleanup skipped.]"
            ),
            file=sys.stderr,
        )
    else:
        if is_fixture_testing_enabled():
            log_stage(
                "fixture-cleanup",
                (
                    f"Cleaning isolated fixture worktree for issue #{claimed_issue.issue['number']}: "
                    f"{claimed_issue.worktree_path}"
                ),
            )
        remove_worktree(claimed_issue.base_reachability_metadata_path, claimed_issue.worktree_path)
        if is_fixture_testing_enabled():
            log_stage(
                "fixture-cleanup",
                f"Cleaned isolated fixture worktree for issue #{claimed_issue.issue['number']}.",
            )


def build_claim_metadata(
        issue: dict,
        label: str,
        base_reachability_metadata_path: str,
) -> Optional[tuple[str, str | None, str | None]]:
    """Resolve the coordinates needed to execute a claimed issue.

    The issue is the run's input, so a title that does not resolve to Maven
    coordinates is rejected at the boundary (§FS-forge-run-requirements.3).
    """
    issue_coordinates = extract_maven_coordinates(issue["title"])
    if issue_coordinates is None:
        print(f"ERROR: No coordinates found in issue title: {issue['title']}", file=sys.stderr)
        return None

    if label in {LABEL_LIBRARY_NEW, LABEL_LIBRARY_UPDATE}:
        return issue_coordinates, None, None

    coordinate_parts = extract_coordinate_parts(issue["title"])
    if coordinate_parts is None:
        print(f"ERROR: No coordinates found in issue title: {issue['title']}", file=sys.stderr)
        return None

    group, artifact, new_version = coordinate_parts
    # `fails-*` issues target the newest version, so keep `latest` as the signal.
    # §AR-forge-driver-queues
    current_version = load_current_metadata_version(
        base_reachability_metadata_path,
        group,
        artifact,
    )
    if current_version is None:
        return None

    current_coordinates = f"{group}:{artifact}:{current_version}"
    return issue_coordinates, current_coordinates, new_version


def extract_issue_requested_metadata_context(issue_body: str | None, max_chars: int = 50000) -> str:
    """Return reporter-provided missing metadata context from an issue body."""
    if not issue_body:
        return ""

    context = issue_body.strip()
    if len(context) > max_chars:
        context = context[:max_chars].rstrip() + "\n[truncated]"
    return context


def resolve_chunked_dynamic_access_exhaust_report(
        issue: dict,
        base_reachability_metadata_path: str,
        coordinate: str,
        continuation_marker: ContinuationMarker | None = None,
) -> DynamicAccessExhaustReport | None:
    """Resolve the persisted exhaust report for an already chunked issue."""
    if not issue_has_label(issue, LABEL_CHUNKED_DYNAMIC_ACCESS):
        return None
    report_path = find_dynamic_access_exhaust_report_path(base_reachability_metadata_path, coordinate)
    if report_path is None:
        if continuation_marker is not None:
            log_stage(
                "chunked-dynamic-access",
                (
                    f"Resuming chunked dynamic-access issue #{issue['number']} without an exhaust report; "
                    "processed classes will be read from the continuation marker."
                ),
            )
            return None
        raise RuntimeError(
            f"Chunked dynamic-access issue #{issue['number']} has no exhaust report for {coordinate}."
        )
    exhaust_report = DynamicAccessExhaustReport.load(report_path)
    verify_chunked_dynamic_access_previous_pr_merged(exhaust_report)
    return exhaust_report


def verify_chunked_dynamic_access_previous_pr_merged(exhaust_report: DynamicAccessExhaustReport) -> None:
    """Fail fast when continuation is requested before the previous PR merged."""
    has_publication_identity = (
        exhaust_report.latest_chunk_publication_id is not None
        or exhaust_report.latest_chunk_branch is not None
    )
    if not has_publication_identity and exhaust_report.latest_chunk_pull_request is None:
        return
    if is_fixture_testing_enabled():
        previous_publication = (
            exhaust_report.latest_chunk_publication_id
            or f"PR #{exhaust_report.latest_chunk_pull_request}"
        )
        log_stage(
            "chunked-dynamic-access",
            f"Fixture mode: treating previous chunk publication {previous_publication} as merged.",
        )
        return
    payload = resolve_chunked_dynamic_access_previous_pr(exhaust_report)
    if payload.get("state") != "MERGED":
        raise RuntimeError(
            f"Chunked dynamic-access issue #{exhaust_report.issue_number} cannot continue "
            f"before PR #{payload.get('number')} is merged "
            f"(state={payload.get('state')})."
        )


def resolve_chunked_dynamic_access_previous_pr(
        exhaust_report: DynamicAccessExhaustReport,
) -> dict[str, Any]:
    """Resolve the previous chunk PR by publication identity or legacy number."""
    publication_id = exhaust_report.latest_chunk_publication_id
    branch = exhaust_report.latest_chunk_branch
    if publication_id is not None or branch is not None:
        if publication_id is None or branch is None:
            raise RuntimeError(
                "Chunked dynamic-access publication identity requires both ID and branch"
            )
        result = gh(
            "pr",
            "list",
            "--repo",
            REPO,
            "--head",
            branch,
            "--state",
            "all",
            "--limit",
            "100",
            "--json",
            "number,state,body,mergeCommit",
            check=True,
        )
        payload = json.loads(result.stdout)
        trailer = f"Forge-Publication-ID: {publication_id}"
        matches = [
            pull_request
            for pull_request in payload
            if trailer in str(pull_request.get("body") or "").splitlines()
        ]
        if not matches:
            raise RuntimeError(
                f"No PR yet for branch {branch!r} and publication {publication_id!r}. "
                "The branch is pushed but trusted publication has not opened its PR; "
                "retry once the Forge Open PR workflow has run."
            )
        if len(matches) > 1:
            raise RuntimeError(
                f"Ambiguous PRs for branch {branch!r} and publication {publication_id!r}; "
                f"found {len(matches)}"
            )
        return matches[0]

    previous_pr = exhaust_report.latest_chunk_pull_request
    if previous_pr is None:
        raise RuntimeError("Chunked dynamic-access report has no previous publication identity")
    result = gh(
        "pr",
        "view",
        str(previous_pr),
        "--repo",
        REPO,
        "--json",
        "number,state,body,mergeCommit",
        check=True,
    )
    return dict(json.loads(result.stdout))

def verify_chunked_dynamic_access_base_contains_published_commit(
        exhaust_report: DynamicAccessExhaustReport,
        worktree_path: str,
) -> None:
    """Ensure the continuation worktree includes the latest published chunk commit."""
    commit = resolve_chunked_dynamic_access_published_base_commit(exhaust_report)
    if not commit:
        return
    result = subprocess.run(
        ["git", "merge-base", "--is-ancestor", commit, "HEAD"],
        cwd=worktree_path,
        check=False,
    )
    if result.returncode != 0:
        raise RuntimeError(
            f"Base branch does not contain latest chunked dynamic-access published commit {commit}"
        )


def resolve_chunked_dynamic_access_published_base_commit(
        exhaust_report: DynamicAccessExhaustReport,
) -> str | None:
    """Return the commit that should be present on the continuation base."""
    if is_fixture_testing_enabled():
        return exhaust_report.latest_chunk_commit
    has_previous_publication = (
        exhaust_report.latest_chunk_publication_id is not None
        or exhaust_report.latest_chunk_branch is not None
        or exhaust_report.latest_chunk_pull_request is not None
    )
    if has_previous_publication:
        payload = resolve_chunked_dynamic_access_previous_pr(exhaust_report)
        merge_commit = payload.get("mergeCommit") or {}
        oid = merge_commit.get("oid")
        if oid:
            return str(oid)
    return exhaust_report.latest_chunk_commit

def maybe_handle_not_for_native_image_issue(issue: dict, base_reachability_metadata_path: str) -> bool:
    """Apply terminal labels and comment when the repository already has a marker for the artifact."""
    issue_coordinates = extract_maven_coordinates(issue["title"])
    if issue_coordinates is None:
        return False
    group, artifact, _version = metadata_coordinate_parts(issue_coordinates)
    if not is_not_for_native_image(base_reachability_metadata_path, group, artifact):
        return False

    marker = get_not_for_native_image_marker(base_reachability_metadata_path, group, artifact) or {}
    reason = marker.get("reason") or "The artifact is marked as not applicable to GraalVM Native Image metadata."
    replacement = marker.get("replacement")
    body = (
        f"`{group}:{artifact}` is already tracked in the repository as `not-for-native-image`, "
        "so Forge will not run reachability-metadata workflows for this artifact.\n\n"
        f"Reason: {reason}"
    )
    if replacement:
        body += f"\n\nReplacement guidance: {replacement}"
    try:
        post_issue_comment(issue["number"], body)
        add_issue_label(issue["number"], LABEL_NOT_FOR_NATIVE_IMAGE)
        add_issue_label(issue["number"], LABEL_HUMAN_INTERVENTION)
    except Exception as exc:
        print(
            f"ERROR: Failed to apply not-for-native-image follow-up to issue #{issue['number']}: {exc!r}",
            file=sys.stderr,
        )
    record_issue_claim_cache_observations([
        IssueClaimCacheObservation(
            issue_number=issue["number"],
            reason=ISSUE_CLAIM_CACHE_REASON_NOT_FOR_NATIVE_IMAGE,
        )
    ])
    return True


@dataclass(frozen=True)
class IssueFormRejection:
    """The one issue-form rule that failed, and the value that failed it."""

    rule: str
    offending_value: str
    requirement: str


@dataclass(frozen=True)
class IssueFormVerdict:
    """Outcome of the claim-held issue-form gate.

    Three-valued on purpose: a rule can fail, or the gate can be unable to
    decide because a remote repository did not answer. Only a failure rejects
    an issue; an undecided answer leaves it for a later cycle.
    §FS-forge-run-requirements.3
    """

    rejection: Optional[IssueFormRejection] = None
    undecided_reason: Optional[str] = None

    @property
    def accepted(self) -> bool:
        return self.rejection is None and self.undecided_reason is None


ISSUE_FORM_ACCEPTED = IssueFormVerdict()


@pipeline_step(
    PHASE_CLAIM,
    STEP_CHECK_ISSUE_FORM,
    operand=lambda arguments: f"issue #{arguments['issue']['number']}",
)
def check_issue_form(
        issue: dict,
        label: str,
        reachability_metadata_path: str,
) -> IssueFormVerdict:
    """Decide every issue-form rule from the payload and the repository.

    Runs inside the pinned issue-base worktree while the exclusive claim is held.
    The rules are decided one at a time and the gate stops at the first failure,
    so the verdict always names the rule and the value that failed it. The only
    rule that leaves the machine is decided last
    (§FS-forge-run-requirements.3, §root/PRCPL-verify-inputs).
    """
    issue_number = issue["number"]
    log_step_progress(
        PHASE_CLAIM,
        STEP_CHECK_ISSUE_FORM,
        f"Checking issue #{issue_number} against newest master in its pinned workspace",
    )

    workflow_labels = sorted(
        label_name for label_name in get_issue_label_names(issue) if label_name in PIPELINE_LABELS
    )
    if len(workflow_labels) > 1:
        return IssueFormVerdict(rejection=IssueFormRejection(
            rule=ISSUE_FORM_RULE_SINGLE_WORKFLOW_LABEL,
            offending_value=", ".join(workflow_labels),
            requirement=(
                "An issue must carry exactly one workflow label, because each one routes to a "
                "different driver working from different assumptions about what the issue asks "
                "for. Keep the single label that describes the work — one of "
                f"{', '.join(f'`{name}`' for name in sorted(PIPELINE_LABELS))} — and remove the rest."
            ),
        ))

    title = str(issue.get("title") or "")
    coordinate_parts = extract_coordinate_parts(title)
    if coordinate_parts is None:
        return IssueFormVerdict(rejection=IssueFormRejection(
            rule=ISSUE_FORM_RULE_MAVEN_COORDINATES,
            offending_value=title,
            requirement=(
                "The issue title must name the library as Maven coordinates "
                "`group:artifact:version`, for example "
                "`Add support for org.postgresql:postgresql:42.7.3`."
            ),
        ))

    group, artifact, requested_version = coordinate_parts
    coordinate = f"{group}:{artifact}:{requested_version}"

    if label in FAILURE_PIPELINE_LABELS:
        current_version = load_current_metadata_version(
            reachability_metadata_path,
            group,
            artifact,
            report_errors=False,
        )
        if current_version is None:
            return IssueFormVerdict(rejection=IssueFormRejection(
                rule=ISSUE_FORM_RULE_CURRENT_LATEST_VERSION,
                offending_value=f"{group}:{artifact}",
                requirement=(
                    f"A `{label}` issue repairs the move from the currently supported version to "
                    "the requested one, so `metadata/<group>/<artifact>/index.json` must already "
                    "carry an entry marked `\"latest\": true`. This artifact has none, so there is "
                    "no supported version to repair from — file a `library-new-request` instead."
                ),
            ))
        if not is_newer_than_latest_metadata_version(
                reachability_metadata_path,
                group,
                artifact,
                requested_version,
        ):
            return IssueFormVerdict(rejection=IssueFormRejection(
                rule=ISSUE_FORM_RULE_NEWER_THAN_LATEST,
                offending_value=requested_version,
                requirement=(
                    f"A `{label}` issue must request a version strictly above the currently "
                    f"supported `latest` version `{current_version}`. Request a newer version, or "
                    "file a `library-update-request` to add support at or below "
                    f"`{current_version}`."
                ),
            ))

    published = artifact_is_published(coordinate)
    if published is None:
        undecided_reason = (
            f"no configured artifact repository answered for {coordinate}; "
            "the issue form stays undecided and the issue is left for a later cycle"
        )
        log_stage("issue-form", f"Issue #{issue_number}: {undecided_reason}")
        return IssueFormVerdict(undecided_reason=undecided_reason)
    if not published:
        return IssueFormVerdict(rejection=IssueFormRejection(
            rule=ISSUE_FORM_RULE_PUBLISHED_ARTIFACT,
            offending_value=coordinate,
            requirement=(
                "The coordinate must be published in a repository the build resolves against: "
                f"{', '.join(ARTIFACT_REPOSITORY_URLS)}. None of them publishes a POM for it, so "
                "no run could resolve the library. Check the group, artifact, and version for "
                "typos."
            ),
        ))

    log_step_progress(
        PHASE_CLAIM,
        STEP_CHECK_ISSUE_FORM,
        f"Issue #{issue_number} accepted: {label} for {coordinate}",
    )
    return ISSUE_FORM_ACCEPTED


def build_issue_form_rejection_marker(rejection: IssueFormRejection) -> str:
    """Return the hidden comment marker keyed on the failed rule and its value."""
    key = hashlib.sha1(
        f"{rejection.rule}\n{rejection.offending_value}".encode("utf-8")
    ).hexdigest()[:12]
    return f"{ISSUE_FORM_REJECTION_MARKER_PREFIX} rule={rejection.rule} key={key} -->"


def build_issue_form_rejection_comment(rejection: IssueFormRejection) -> str:
    """Render the predefined comment the failed rule selects."""
    return (
        f"{build_issue_form_rejection_marker(rejection)}\n"
        f"Forge did not start a run for this issue: it fails the issue-form rule "
        f"`{rejection.rule}`.\n\n"
        f"Offending value: `{rejection.offending_value}`\n\n"
        f"{rejection.requirement}\n\n"
        "This issue is closed because nothing about it changes until someone edits it. "
        "Correct it and reopen it, or file a corrected issue."
    )


def issue_has_issue_form_rejection_comment(issue_number: int, rejection: IssueFormRejection) -> bool:
    """Return whether this exact rule and value were already reported on the issue."""
    marker = build_issue_form_rejection_marker(rejection)
    return any(
        marker in str(comment.get("body") or "")
        for comment in get_issue_comments(issue_number)
    )


def reject_issue_form(
        issue: dict,
        rejection: IssueFormRejection,
) -> bool:
    """Report the failed rule and close the issue while its claim is held.

    A form defect is an input defect outside Forge's generation boundary: no
    `human-intervention` label is applied and no branch is preserved. Closing is
    what takes the issue out of every queue it cannot leave on its own, and the
    comment marker keeps a reopened, unedited issue from collecting a second
    comment. The Forge assignee is cleared only after the issue is closed, so
    another worker cannot enter the rejection sequence
    (§FS-forge-run-requirements.3).
    """
    issue_number = issue["number"]
    log_stage(
        "issue-form",
        f"Rejecting issue #{issue_number}: rule '{rejection.rule}' failed on "
        f"'{rejection.offending_value}'",
    )
    try:
        if issue_has_issue_form_rejection_comment(issue_number, rejection):
            log_stage(
                "issue-form",
                f"Skipping rejection comment for issue #{issue_number}: "
                f"rule '{rejection.rule}' was already reported",
            )
        else:
            log_stage(
                "issue-form",
                f"Posting rejection comment to issue #{issue_number}: rule '{rejection.rule}'",
            )
            post_issue_comment(issue_number, build_issue_form_rejection_comment(rejection))
        close_issue(issue_number, f"issue-form rule '{rejection.rule}' failed")
    except Exception as exc:
        print(
            f"ERROR: Failed to reject issue #{issue_number} for issue-form rule "
            f"'{rejection.rule}': {exc!r}",
            file=sys.stderr,
        )
        return False

    if not is_fixture_testing_enabled():
        try:
            log_stage(
                "issue-close",
                f"Clearing Forge assignee from closed issue #{issue_number}",
            )
            clear_issue_assignees(issue_number)
        except Exception as exc:
            print(
                f"ERROR: Failed to clear Forge assignee from closed issue "
                f"#{issue_number}: {exc!r}",
                file=sys.stderr,
            )
    return True


def cleanup_claim_preparation_workspace(
        base_reachability_metadata_path: str,
        worktree_path: str | None,
        preflight_info_path: str | None,
) -> None:
    """Remove workspace state when claim preparation does not reach dispatch.

    §FS-forge-run-requirements.2
    """
    if preflight_info_path:
        shutil.rmtree(preflight_info_path, ignore_errors=True)
    if not worktree_path:
        return
    try:
        remove_worktree(base_reachability_metadata_path, worktree_path)
    except Exception as exc:
        print(
            f"ERROR: Failed to clean up unstarted issue worktree {worktree_path}: {exc!r}",
            file=sys.stderr,
        )


def claim_issue_for_processing(
        issue: dict,
        label: str,
        base_reachability_metadata_path: str,
        canonical_metrics_repo_path: str,
        authenticated_user: str,
        take_blocked_issues: bool = DEFAULT_TAKE_BLOCKED_ISSUES,
) -> Optional[ClaimedIssue]:
    """Claim an issue and prepare its isolated execution workspace.

    Forge code may run from a monitored feature branch, but repository-dependent
    preconditions and generated work use one freshly fetched, pinned
    `origin/master` commit (§FS-forge-run-requirements.2,
    §FS-forge-run-requirements.4).
    """
    enter_phase(PHASE_CLAIM)
    issue_number = issue["number"]
    log_step_progress(PHASE_CLAIM, STEP_CLAIM_ISSUE, f"Claiming issue #{issue_number}")
    if not refresh_issue_payload_for_claim(issue, label, authenticated_user):
        log_step_progress(
            PHASE_CLAIM,
            STEP_CLAIM_ISSUE,
            f"Issue #{issue_number} not claimed: live issue state is no longer eligible",
        )
        return None

    try:
        issue_base_commit = fetch_issue_base_commit(base_reachability_metadata_path)
    except BaseException as exc:
        if isinstance(exc, Exception):
            log_step_progress(
                PHASE_CLAIM,
                STEP_CLAIM_ISSUE,
                f"Issue #{issue_number} not claimed: newest origin/master could not be pinned",
            )
            return None
        raise

    item_id = try_claim_issue(issue, authenticated_user, label, take_blocked_issues)
    if not item_id:
        log_step_progress(
            PHASE_CLAIM,
            STEP_CLAIM_ISSUE,
            f"Issue #{issue_number} not claimed: live claimability check did not pass",
        )
        return None

    worktree_path: str | None = None
    scratch_metrics_repo_path: str | None = None
    preflight_info_path: str | None = None
    handoff_complete = False
    failure_stage = "claim setup"
    try:
        worktree_path, scratch_metrics_repo_path = create_issue_workspace(
            base_reachability_metadata_path,
            canonical_metrics_repo_path,
            issue["number"],
            issue_base_commit,
        )
        preflight_info_path = create_preflight_info_dir(worktree_path)

        # The claim makes form rejection exclusive, while the pinned worktree
        # makes every repository lookup independent from the Forge code branch.
        # §FS-forge-run-requirements.3
        failure_stage = "issue-form check"
        form_verdict = check_issue_form(issue, label, worktree_path)
        if form_verdict.rejection is not None:
            rejection_succeeded = reject_issue_form(issue, form_verdict.rejection)
            if not rejection_succeeded:
                revert_issue_claim(
                    item_id,
                    issue["number"],
                    f"issue-form rule '{form_verdict.rejection.rule}' could not close the issue",
                )
            return None
        if not form_verdict.accepted:
            revert_issue_claim(
                item_id,
                issue["number"],
                "issue-form check was undecided",
            )
            return None

        failure_stage = "post-claim preparation"
        not_for_native_image: bool = maybe_handle_not_for_native_image_issue(issue, worktree_path)
        claim_metadata: Optional[tuple[str, str | None, str | None]] = (
            None
            if not_for_native_image
            else build_claim_metadata(issue, label, worktree_path)
        )
        continuation_marker: ContinuationMarker | None = (
            resolve_issue_continuation_marker(
                issue,
                label,
                claim_metadata[0],
                worktree_path,
            )
            if claim_metadata is not None
            else None
        )

        if not_for_native_image:
            revert_issue_claim(
                item_id,
                issue["number"],
                "artifact is marked not-for-native-image",
            )
            return None
        if claim_metadata is None:
            revert_issue_claim(
                item_id,
                issue["number"],
                "issue-form accepted but claim metadata could not be built",
            )
            return None

        issue_coordinates, current_coordinates, new_version = claim_metadata
        if issue_is_resumable(issue) and continuation_marker is None:
            log_stage(
                "continuation",
                (
                    f"Skipping issue #{issue['number']}: label '{LABEL_RESUMABLE}' is present, "
                    "but no valid continuation marker was found on a preserved branch."
                ),
            )
            revert_issue_claim(
                item_id,
                issue["number"],
                "resumable issue has no valid continuation marker",
            )
            return None

        failure_stage = "chunked-dynamic-access setup"
        chunked_exhaust_report = resolve_chunked_dynamic_access_exhaust_report(
            issue,
            worktree_path,
            issue_coordinates,
            continuation_marker,
        )

        failure_stage = "claim setup"
        if continuation_marker is not None and not checkout_continuation_branch(
                worktree_path,
                continuation_marker,
                issue_base_commit,
        ):
            continuation_marker = None
        if chunked_exhaust_report is not None:
            verify_chunked_dynamic_access_base_contains_published_commit(
                chunked_exhaust_report,
                worktree_path,
            )

        claimed_issue = ClaimedIssue(
            issue=issue,
            label=label,
            item_id=item_id,
            base_reachability_metadata_path=base_reachability_metadata_path,
            worktree_path=worktree_path,
            scratch_metrics_repo_path=scratch_metrics_repo_path,
            issue_coordinates=issue_coordinates,
            issue_base_commit=issue_base_commit,
            current_coordinates=current_coordinates,
            new_version=new_version,
            preflight_info_path=preflight_info_path,
            continuation_marker=continuation_marker,
        )
        handoff_complete = True
        return claimed_issue
    except BaseException as exc:
        if failure_stage == "chunked-dynamic-access setup":
            print(
                f"ERROR: Cannot resume chunked-dynamic-access issue #{issue['number']}: {exc}",
                file=sys.stderr,
            )
        else:
            print(
                f"ERROR: Issue #{issue['number']} {failure_stage} failed: {exc!r}",
                file=sys.stderr,
            )
        revert_issue_claim(
            item_id,
            issue["number"],
            f"{failure_stage} interrupted by Ctrl+C" if is_interrupt_exception(exc)
            else f"{failure_stage} failure ({type(exc).__name__})",
        )
        if isinstance(exc, Exception):
            return None
        raise
    finally:
        if not handoff_complete:
            cleanup_claim_preparation_workspace(
                base_reachability_metadata_path,
                worktree_path,
                preflight_info_path,
            )


def build_fixture_claimed_issue(
        issue: dict,
        label: str,
        base_reachability_metadata_path: str,
        canonical_metrics_repo_path: str,
) -> Optional[ClaimedIssue]:
    """Prepare a fixture issue without simulating GitHub claim mechanics.

    §AR-forge-control-plane
    """
    if not is_fixture_testing_enabled():
        raise RuntimeError("Fixture issue preparation requires fixture testing mode")

    issue_number = issue["number"]
    try:
        item_id = require_fixture_github_state().get_issue_project_item_id(issue_number)
    except Exception:
        item_id = f"fixture-project-item-{issue_number}"

    try:
        worktree_path, scratch_metrics_repo_path = create_issue_workspace(
            base_reachability_metadata_path,
            canonical_metrics_repo_path,
            issue_number,
        )
        preflight_info_path = create_preflight_info_dir(worktree_path)
        log_stage(
            "fixture-worktree",
            (
                f"Created isolated fixture worktree for issue #{issue_number}: "
                f"worktree={worktree_path}, metrics={scratch_metrics_repo_path}, "
                f"preflight={preflight_info_path}"
            ),
        )
    except BaseException as exc:
        if isinstance(exc, Exception):
            print(
                f"ERROR: Failed to prepare fixture workspace for issue #{issue_number}: {exc!r}",
                file=sys.stderr,
            )
            return None
        raise

    try:
        require_fixture_github_state().prepare_issue_worktree(issue_number, label, worktree_path)
        # Fixture masking rewinds the index inside the worktree, so the gate is
        # decided against the repository state this run actually uses.
        # §FS-forge-run-requirements.3 §AR-forge-workflow-pipeline
        form_verdict = check_issue_form(issue, label, worktree_path)
        if form_verdict.rejection is not None:
            reject_issue_form(issue, form_verdict.rejection)
        claim_metadata = (
            build_claim_metadata(issue, label, worktree_path)
            if form_verdict.accepted
            else None
        )
    except BaseException as exc:
        if isinstance(exc, Exception):
            print(
                f"ERROR: Failed to prepare fixture issue #{issue_number}: {exc!r}",
                file=sys.stderr,
            )
            try:
                remove_worktree(base_reachability_metadata_path, worktree_path)
            except Exception as cleanup_exc:
                print(
                    f"ERROR: Failed to clean up fixture worktree {worktree_path}: {cleanup_exc!r}",
                    file=sys.stderr,
                )
            return None
        raise
    if claim_metadata is None:
        try:
            remove_worktree(base_reachability_metadata_path, worktree_path)
        except Exception as cleanup_exc:
            print(
                f"ERROR: Failed to clean up fixture worktree {worktree_path}: {cleanup_exc!r}",
                file=sys.stderr,
            )
        return None

    issue_coordinates, current_coordinates, new_version = claim_metadata
    continuation_marker = load_continuation_marker(continuation_marker_path(worktree_path))
    return ClaimedIssue(
        issue=issue,
        label=label,
        item_id=item_id,
        base_reachability_metadata_path=base_reachability_metadata_path,
        worktree_path=worktree_path,
        scratch_metrics_repo_path=scratch_metrics_repo_path,
        issue_coordinates=issue_coordinates,
        issue_base_commit=resolve_git_commit(worktree_path, "HEAD"),
        current_coordinates=current_coordinates,
        new_version=new_version,
        preflight_info_path=preflight_info_path,
        continuation_marker=continuation_marker,
    )


def run_claimed_issue(
        claimed_issue: ClaimedIssue,
        strategy_name: str | None,
        keep_tests_without_dynamic_access: bool,
) -> WorkflowRunResult:
    """Execute the claimed issue workflow inside its isolated worktree."""
    started_at = time.time()
    failure_was_external = False
    try:
        success = invoke_pipeline(
            claimed_issue,
            strategy_name,
            keep_tests_without_dynamic_access,
        )
    except GradleBootstrapFailure:
        raise
    except KeyboardInterrupt:
        preserve_user_interrupt_reason()
        raise
    except Exception as exc:
        if is_user_interrupt_requested():
            raise KeyboardInterrupt from exc
        # The location the step annotated onto the exception leads its detail.
        # §FS-forge-run-location-reporting.3
        error_detail = str(exc) if isinstance(exc, AgentFailureError) else (
            f"Issue #{claimed_issue.issue['number']} workflow raised an exception: {exc!r}"
        )
        report_run_failure(
            resolve_failure_location(exc),
            error_detail,
            log_path=exc.log_path if isinstance(exc, AgentFailureError) else None,
        )
        if not isinstance(exc, AgentFailureError):
            traceback.print_exc()
        success = False
        failure_was_external = is_external_failure_exception(exc)
    if is_user_interrupt_requested():
        raise KeyboardInterrupt
    return WorkflowRunResult(
        claimed_issue=claimed_issue,
        success=success,
        started_at=started_at,
        failure_was_external=failure_was_external,
    )


def build_chunked_dynamic_access_pr_args(
        exhaust_report_path: str | None,
        workflow_status: str | None,
) -> list[str]:
    """Build PR flags for an active chunked dynamic-access issue.

    Non-final chunks publish with continuation state and must not close the
    backing issue, per the chunk PR linking contract
    (§AR-chunked-dynamic-access-pr-linking, §AR-issue-linking).
    """
    if exhaust_report_path is None:
        return []
    args = ["--chunked-dynamic-access"]
    if workflow_status != RUN_STATUS_CHUNK_READY:
        args.append("--chunk-final")
    return args


def _require_publication_value(value: str | None, field_name: str, claimed_issue: ClaimedIssue) -> str:
    if value is None:
        raise ValueError(
            f"Issue #{claimed_issue.issue['number']} {claimed_issue.label} publication requires {field_name}."
        )
    return value


def _load_library_update_publication_route(claimed_issue: ClaimedIssue) -> LibraryUpdateRoute | None:
    if claimed_issue.label != LABEL_LIBRARY_UPDATE:
        return None
    return load_library_update_route(library_update_route_artifact_root(claimed_issue))


def _pending_run_metrics_path(claimed_issue: ClaimedIssue) -> str:
    """Return the transient metrics file path for this claimed issue."""
    return os.path.join(
        claimed_issue.scratch_metrics_repo_path,
        PENDING_METRICS_FILENAME,
    )


def record_publication_metrics_from_pending(
        marker: ContinuationMarker,
        metrics_repo_path: str,
) -> bool:
    """Copy local-only pending metrics fields into the continuation marker."""
    pending_metrics_path = os.path.join(metrics_repo_path, PENDING_METRICS_FILENAME)
    if not os.path.isfile(pending_metrics_path):
        return False
    try:
        run_metrics = read_pending_metrics(metrics_repo_path)
    except (OSError, json.JSONDecodeError, TypeError) as exc:
        log_stage(
            "publication",
            f"Could not snapshot pending metrics into continuation marker: {exc!r}.",
        )
        return False
    marker.record_publication_metrics(run_metrics, PUBLICATION_METRICS_EXTRA_KEYS)
    return marker.publication_metrics is not None


def record_pending_publication_metrics_for_resume(claimed_issue: ClaimedIssue) -> None:
    """Persist the pending metrics reconstruction inputs in the continuation marker."""
    marker_path = continuation_marker_path(claimed_issue.worktree_path)
    marker = load_continuation_marker(marker_path)
    if marker is None:
        return
    if record_publication_metrics_from_pending(marker, claimed_issue.scratch_metrics_repo_path):
        marker.save(marker_path)


def _restore_pending_run_metrics_from_marker(
        claimed_issue: ClaimedIssue,
        marker: ContinuationMarker,
) -> bool:
    """Restore transient metrics from durable metrics plus marker-local extras."""
    marker_metrics = marker.publication_metrics
    if not isinstance(marker_metrics, dict):
        return False
    library = marker_metrics.get("library")
    timestamp = marker_metrics.get("timestamp")
    if not isinstance(library, str) or not isinstance(timestamp, str):
        return False
    run_metrics = load_execution_metrics_for_timestamp(claimed_issue.worktree_path, library, timestamp)
    if run_metrics is None:
        log_stage(
            "publication",
            (
                "Could not restore pending metrics from continuation marker because no durable "
                f"execution metrics entry matched {library} at {timestamp}."
            ),
        )
        return False
    extras = marker_metrics.get("extras")
    if isinstance(extras, dict):
        run_metrics.update(extras)
    write_pending_metrics(claimed_issue.scratch_metrics_repo_path, run_metrics)
    log_stage(
        "publication",
        (
            "Restored missing pending metrics from durable execution metrics and "
            f"continuation marker extras for {library}."
        ),
    )
    return True


def restore_pending_run_metrics_from_execution_metrics(claimed_issue: ClaimedIssue) -> None:
    """Restore or snapshot transient publication metrics for resumable PR creation."""
    pending_metrics_path = _pending_run_metrics_path(claimed_issue)
    if os.path.isfile(pending_metrics_path):
        record_pending_publication_metrics_for_resume(claimed_issue)
        return

    marker = load_continuation_marker(continuation_marker_path(claimed_issue.worktree_path))
    if marker is not None:
        _restore_pending_run_metrics_from_marker(claimed_issue, marker)


def build_publication_handoff(
        claimed_issue: ClaimedIssue,
        coverage_follow_up_issue_number: int | None = None,
        coverage_follow_up_class_count: int | None = None,
        coverage_follow_up_class_threshold: int | None = None,
) -> PublicationHandoff:
    """Build the live-or-fixture PR publication handoff.

    The dispatcher makes the routing decision once, then either executes the
    matching git script or records a dry-run fixture handoff
    (§AR-forge-verification-publication-boundary).
    """
    require_claimed_issue_worktree(claimed_issue, "successful finalization")
    issue_number = claimed_issue.issue["number"]
    restore_pending_run_metrics_from_execution_metrics(claimed_issue)
    exhaust_report_path = find_dynamic_access_exhaust_report_path(
        claimed_issue.worktree_path,
        claimed_issue.issue_coordinates,
    )
    run_metrics = _load_pending_run_metrics(claimed_issue.scratch_metrics_repo_path)
    workflow_status = None if run_metrics is None else run_metrics.get("status")
    issue_number_args = ["--issue-number", str(issue_number)]
    chunked_dynamic_access_args = build_chunked_dynamic_access_pr_args(
        exhaust_report_path,
        workflow_status,
    )
    chunked_dynamic_access_final = None
    coverage_follow_up_args: list[str] = []
    if coverage_follow_up_issue_number is not None:
        if coverage_follow_up_class_count is None or coverage_follow_up_class_threshold is None:
            raise ValueError("Coverage follow-up count and threshold are required")
        coverage_follow_up_args = [
            "--coverage-follow-up-issue-number",
            str(coverage_follow_up_issue_number),
            "--coverage-follow-up-class-count",
            str(coverage_follow_up_class_count),
            "--coverage-follow-up-class-threshold",
            str(coverage_follow_up_class_threshold),
        ]
    if exhaust_report_path is not None:
        chunked_dynamic_access_final = workflow_status != RUN_STATUS_CHUNK_READY

    script_name: str
    runner_name: str
    runner: Callable[[list[str]], None]
    argv: list[str]
    result_label: str
    publication_kind: str | None = None
    coordinates: str | None = claimed_issue.issue_coordinates
    current_coordinates: str | None = claimed_issue.current_coordinates
    new_version: str | None = claimed_issue.new_version
    not_for_native_image = False
    library_update_route = _load_library_update_publication_route(claimed_issue)

    if claimed_issue.label == LABEL_LIBRARY_NEW:
        group, artifact, _version = metadata_coordinate_parts(claimed_issue.issue_coordinates)
        not_for_native_image = is_not_for_native_image(claimed_issue.worktree_path, group, artifact)
        if not_for_native_image:
            script_name = "git_scripts/publish_not_for_native_image.py"
            runner_name = "run_publish_not_for_native_image"
            runner = run_publish_not_for_native_image
            result_label = LABEL_NOT_FOR_NATIVE_IMAGE
            argv = [
                "--coordinates", claimed_issue.issue_coordinates,
                *issue_number_args,
                "--reachability-metadata-path", claimed_issue.worktree_path,
                "--metrics-repo-path", claimed_issue.scratch_metrics_repo_path,
            ]
        else:
            script_name = "git_scripts/publish_new_library_support.py"
            runner_name = "run_publish_new_library_support"
            runner = run_publish_new_library_support
            result_label = LABEL_LIBRARY_NEW
            argv = [
                "--coordinates", claimed_issue.issue_coordinates,
                *issue_number_args,
                "--reachability-metadata-path", claimed_issue.worktree_path,
                "--metrics-repo-path", claimed_issue.scratch_metrics_repo_path,
                *chunked_dynamic_access_args,
            ]
    elif claimed_issue.label == LABEL_JAVAC_FAIL:
        script_name = "git_scripts/publish_javac_fix.py"
        runner_name = "run_publish_javac_fix"
        runner = run_publish_javac_fix
        result_label = LABEL_PR_JAVAC_FIX
        current_coordinates = _require_publication_value(
            claimed_issue.current_coordinates,
            "current_coordinates",
            claimed_issue,
        )
        new_version = _require_publication_value(claimed_issue.new_version, "new_version", claimed_issue)
        coordinates = None
        argv = [
            "--coordinates", current_coordinates,
            "--new-version", new_version,
            "--issue-number", str(issue_number),
            "--reachability-metadata-path", claimed_issue.worktree_path,
            "--metrics-repo-path", claimed_issue.scratch_metrics_repo_path,
            *coverage_follow_up_args,
        ]
    elif claimed_issue.label == LABEL_JAVA_RUN_FAIL:
        script_name = "git_scripts/publish_java_run_fix.py"
        runner_name = "run_publish_java_run_fix"
        runner = run_publish_java_run_fix
        result_label = LABEL_PR_JAVA_RUN_FIX
        current_coordinates = _require_publication_value(
            claimed_issue.current_coordinates,
            "current_coordinates",
            claimed_issue,
        )
        new_version = _require_publication_value(claimed_issue.new_version, "new_version", claimed_issue)
        coordinates = None
        argv = [
            "--coordinates", current_coordinates,
            "--new-version", new_version,
            "--issue-number", str(issue_number),
            "--reachability-metadata-path", claimed_issue.worktree_path,
            "--metrics-repo-path", claimed_issue.scratch_metrics_repo_path,
            *coverage_follow_up_args,
        ]
    elif claimed_issue.label == LABEL_NI_RUN_FAIL:
        script_name = "git_scripts/publish_ni_run_fix.py"
        runner_name = "run_publish_ni_run_fix"
        runner = run_publish_ni_run_fix
        result_label = LABEL_PR_NI_RUN_FIX
        current_coordinates = _require_publication_value(
            claimed_issue.current_coordinates,
            "current_coordinates",
            claimed_issue,
        )
        new_version = _require_publication_value(claimed_issue.new_version, "new_version", claimed_issue)
        coordinates = None
        argv = [
            "--coordinates", current_coordinates,
            "--new-version", new_version,
            "--issue-number", str(issue_number),
            "--reachability-metadata-path", claimed_issue.worktree_path,
            "--metrics-repo-path", claimed_issue.scratch_metrics_repo_path,
        ]
    elif claimed_issue.label == LABEL_LIBRARY_UPDATE:
        if library_update_route is not None and library_update_route.selected_driver == ROUTE_FIX_JAVAC:
            script_name = "git_scripts/publish_javac_fix.py"
            runner_name = "run_publish_javac_fix"
            runner = run_publish_javac_fix
            result_label = LABEL_PR_LIBRARY_UPDATE
            publication_kind = LABEL_PR_JAVAC_FIX
            current_coordinates = _require_publication_value(
                library_update_route.baseline_coordinates,
                "library_update_route.baseline_coordinates",
                claimed_issue,
            )
            new_version = library_update_route.new_version
            coordinates = None
            argv = [
                "--coordinates", current_coordinates,
                "--new-version", new_version,
                "--issue-number", str(issue_number),
                "--pr-label", result_label,
                "--reachability-metadata-path", claimed_issue.worktree_path,
                "--metrics-repo-path", claimed_issue.scratch_metrics_repo_path,
                *coverage_follow_up_args,
            ]
        elif library_update_route is not None and library_update_route.selected_driver == ROUTE_FIX_JAVA_RUN:
            script_name = "git_scripts/publish_java_run_fix.py"
            runner_name = "run_publish_java_run_fix"
            runner = run_publish_java_run_fix
            result_label = LABEL_PR_LIBRARY_UPDATE
            publication_kind = LABEL_PR_JAVA_RUN_FIX
            current_coordinates = _require_publication_value(
                library_update_route.baseline_coordinates,
                "library_update_route.baseline_coordinates",
                claimed_issue,
            )
            new_version = library_update_route.new_version
            coordinates = None
            argv = [
                "--coordinates", current_coordinates,
                "--new-version", new_version,
                "--issue-number", str(issue_number),
                "--pr-label", result_label,
                "--reachability-metadata-path", claimed_issue.worktree_path,
                "--metrics-repo-path", claimed_issue.scratch_metrics_repo_path,
                *coverage_follow_up_args,
            ]
        elif library_update_route is not None and library_update_route.selected_driver == ROUTE_FIX_NI_RUN:
            script_name = "git_scripts/publish_ni_run_fix.py"
            runner_name = "run_publish_ni_run_fix"
            runner = run_publish_ni_run_fix
            result_label = LABEL_PR_LIBRARY_UPDATE
            publication_kind = LABEL_PR_NI_RUN_FIX
            current_coordinates = _require_publication_value(
                library_update_route.baseline_coordinates,
                "library_update_route.baseline_coordinates",
                claimed_issue,
            )
            new_version = library_update_route.new_version
            coordinates = None
            argv = [
                "--coordinates", current_coordinates,
                "--new-version", new_version,
                "--issue-number", str(issue_number),
                "--pr-label", result_label,
                "--reachability-metadata-path", claimed_issue.worktree_path,
                "--metrics-repo-path", claimed_issue.scratch_metrics_repo_path,
            ]
        else:
            script_name = "git_scripts/publish_improve_coverage.py"
            runner_name = "run_publish_improve_coverage"
            runner = run_publish_improve_coverage
            result_label = LABEL_PR_LIBRARY_UPDATE
            argv = [
                "--coordinates", claimed_issue.issue_coordinates,
                *issue_number_args,
                "--reachability-metadata-path", claimed_issue.worktree_path,
                "--metrics-repo-path", claimed_issue.scratch_metrics_repo_path,
                *chunked_dynamic_access_args,
            ]
    else:
        raise ValueError(f"Unknown label '{claimed_issue.label}'")

    return PublicationHandoff(
        script_name=script_name,
        runner_name=runner_name,
        runner=runner,
        argv=argv,
        issue_number=issue_number,
        issue_label=claimed_issue.label,
        result_label=result_label,
        coordinates=coordinates,
        current_coordinates=current_coordinates,
        new_version=new_version,
        worktree_path=claimed_issue.worktree_path,
        scratch_metrics_path=claimed_issue.scratch_metrics_repo_path,
        workflow_status=workflow_status,
        chunked_dynamic_access_args=chunked_dynamic_access_args,
        dynamic_access_exhaust_report_path=exhaust_report_path,
        chunked_dynamic_access_final=chunked_dynamic_access_final,
        not_for_native_image=not_for_native_image,
        publication_kind=publication_kind or result_label,
        coverage_follow_up_issue_number=coverage_follow_up_issue_number,
        coverage_follow_up_class_count=coverage_follow_up_class_count,
        coverage_follow_up_class_threshold=coverage_follow_up_class_threshold,
    )


def preserve_fixture_preflight_evidence(claimed_issue: ClaimedIssue) -> None:
    """Copy fixture metrics/preflight files before scratch worktree cleanup."""
    issue_number = int(claimed_issue.issue["number"])
    evidence_dir = os.path.join(
        get_fixture_issue_artifact_dir(issue_number),
        FIXTURE_PREFLIGHT_EVIDENCE_DIRNAME,
    )
    os.makedirs(evidence_dir, exist_ok=True)
    evidence_files = [
        (claimed_issue.scratch_metrics_repo_path, PENDING_METRICS_FILENAME, "pending-metrics.json"),
        (
            claimed_issue.preflight_info_path or claimed_issue.scratch_metrics_repo_path,
            LIBRARY_PREPARATION_PREFLIGHT_FILENAME,
            LIBRARY_PREPARATION_PREFLIGHT_FILENAME,
        ),
        (
            claimed_issue.preflight_info_path or claimed_issue.scratch_metrics_repo_path,
            "library-preflight-prompt.txt",
            "library-preflight-prompt.txt",
        ),
        (
            claimed_issue.preflight_info_path or claimed_issue.scratch_metrics_repo_path,
            "library-preflight-response.txt",
            "library-preflight-response.txt",
        ),
    ]
    copied_files: list[str] = []
    for source_root, source_name, target_name in evidence_files:
        source_path = os.path.join(source_root, source_name)
        if not os.path.isfile(source_path):
            continue
        target_path = os.path.join(evidence_dir, target_name)
        shutil.copy2(source_path, target_path)
        copied_files.append(target_name)
    if copied_files:
        log_stage(
            "fixture-evidence",
            (
                f"Preserved fixture preflight evidence for issue #{issue_number}: "
                f"{', '.join(copied_files)}"
            ),
        )


def apply_chunked_dynamic_access_completion_follow_up(claimed_issue: ClaimedIssue) -> None:
    """Apply issue labels after a chunked dynamic-access part was published."""
    run_metrics = _load_pending_run_metrics(claimed_issue.scratch_metrics_repo_path)
    workflow_status = None if run_metrics is None else run_metrics.get("status")
    exhaust_report_path = find_dynamic_access_exhaust_report_path(
        claimed_issue.worktree_path,
        claimed_issue.issue_coordinates,
    )
    if exhaust_report_path is None:
        return
    issue_number = claimed_issue.issue["number"]
    if workflow_status == RUN_STATUS_CHUNK_READY:
        add_issue_label(issue_number, LABEL_CHUNKED_DYNAMIC_ACCESS)


def prepare_java_fix_coverage_follow_up(
        claimed_issue: ClaimedIssue,
) -> tuple[int, int, int] | None:
    """Open a fixed-version coverage issue when post-repair exploration was oversized.

    The composite strategy already made this decision against the report it had
    when the repair finished, so publication reads that recorded decision instead
    of regenerating a report and deciding a second time.
    §AR-forge-driver-queues.3
    """
    is_java_fix_issue = claimed_issue.label in {LABEL_JAVAC_FAIL, LABEL_JAVA_RUN_FAIL}
    library_update_route = _load_library_update_publication_route(claimed_issue)
    is_library_update_java_fix = (
        claimed_issue.label == LABEL_LIBRARY_UPDATE
        and library_update_route is not None
        and library_update_route.selected_driver in {ROUTE_FIX_JAVAC, ROUTE_FIX_JAVA_RUN}
    )
    if not is_java_fix_issue and not is_library_update_java_fix:
        return None

    marker = load_continuation_marker(continuation_marker_path(claimed_issue.worktree_path))
    deferred_coverage = marker.deferred_dynamic_access_coverage() if marker is not None else None
    if deferred_coverage is None:
        return None
    uncovered_class_count, threshold = deferred_coverage
    existing_issue_number = marker.coverage_follow_up_issue_number()
    marker_path = continuation_marker_path(claimed_issue.worktree_path)

    if is_fixture_testing_enabled():
        issue_number = existing_issue_number
        if issue_number is None:
            issue_number = FIXTURE_COVERAGE_FOLLOW_UP_ISSUE_OFFSET + int(
                claimed_issue.issue["number"]
            )
            save_phase_update(
                marker_path,
                lambda current_marker: current_marker.record_coverage_follow_up_issue(
                    issue_number
                ),
            )
        log_stage(
            "coverage-follow-up",
            f"Fixture mode: using simulated library-update issue #{issue_number}.",
        )
        return issue_number, uncovered_class_count, threshold

    issue_number = ensure_coverage_follow_up_issue(
        coordinate=claimed_issue.issue_coordinates,
        repair_issue_number=int(claimed_issue.issue["number"]),
        uncovered_class_count=uncovered_class_count,
        class_threshold=threshold,
        repo=REPO,
        existing_issue_number=existing_issue_number,
        record_issue_number=lambda resolved_issue_number: save_phase_update(
            marker_path,
            lambda current_marker: current_marker.record_coverage_follow_up_issue(
                resolved_issue_number
            ),
        ),
    )
    return issue_number, uncovered_class_count, threshold


def finalize_successful_issue(
        claimed_issue: ClaimedIssue,
) -> None:
    """Create the PR for a successful isolated workflow run.

    Publication is delegated to workflow-specific git scripts only after the
    workflow records a PR-eligible status (§AR-pr-eligibility), keeping
    generation and publication separate (§AR-forge-verification-publication-boundary).
    """
    enter_phase(PHASE_PUBLICATION)
    coverage_follow_up = prepare_java_fix_coverage_follow_up(claimed_issue)
    coverage_follow_up_args = coverage_follow_up or (None, None, None)
    if is_fixture_testing_enabled():
        preserve_fixture_preflight_evidence(claimed_issue)
        log_stage(
            "publication",
            (
                f"Fixture mode: skipping publication for issue "
                f"#{claimed_issue.issue['number']}; publication is exercised against GitHub, "
                "not by the hermetic fixture run."
            ),
        )
        return

    with run_step(
        PHASE_PUBLICATION,
        STEP_PUBLISH_BRANCH,
        operand=claimed_issue.issue_coordinates,
    ):
        handoff = build_publication_handoff(claimed_issue, *coverage_follow_up_args)
        handoff.runner(handoff.argv)


def preserve_failed_work_for_follow_up(claimed_issue: ClaimedIssue) -> FailurePreservationResult | None:
    """Push failed work to a branch for human follow-up."""
    if is_fixture_testing_enabled():
        preservation_result = build_fixture_failure_preservation_result(claimed_issue)
        preservation_failed_worktree_paths.add(claimed_issue.worktree_path)
        log_stage(
            "preserve-failed-work",
            (
                f"Fixture mode: dry-run failure preservation handoff for branch "
                f"{preservation_result.branch_name} for issue #{claimed_issue.issue['number']}."
            ),
        )
        return preservation_result
    try:
        return preserve_failed_work_branch(claimed_issue)
    except Exception as exc:
        preservation_failed_worktree_paths.add(claimed_issue.worktree_path)
        print(
            (
                f"ERROR: Failed to preserve work for issue #{claimed_issue.issue['number']} "
                f"before cleanup: {exc!r}. Keeping the local worktree for manual recovery."
            ),
            file=sys.stderr,
        )
        traceback.print_exc()
        return None


def _repeated_resume_failure_phase(claimed_issue: ClaimedIssue) -> str | None:
    """Return the phase when a resumed run failed again without advancing."""
    resumed_marker = claimed_issue.continuation_marker
    if resumed_marker is None:
        return None
    active_marker = load_continuation_marker(continuation_marker_path(claimed_issue.worktree_path))
    if active_marker is None:
        return None
    resumed_phase = resumed_marker.continue_from
    if resumed_phase and active_marker.continue_from == resumed_phase:
        return resumed_phase
    return None


def handle_repeated_resume_failure(claimed_issue: ClaimedIssue, phase_name: str, reason: str) -> None:
    """Stop automatic continuation after the same resumed phase fails again."""
    issue_number = claimed_issue.issue["number"]
    log_stage(
        "continuation",
        (
            f"Issue #{issue_number} resumed at phase {phase_name} and failed there again; "
            f"removing '{LABEL_RESUMABLE}' without posting another human-intervention report."
        ),
    )
    try:
        remove_issue_label(issue_number, LABEL_RESUMABLE)
    except Exception as exc:
        print(
            f"ERROR: Failed to remove '{LABEL_RESUMABLE}' label from issue #{issue_number}: {exc!r}",
            file=sys.stderr,
        )
        traceback.print_exc()
    revert_claimed_issue(claimed_issue, f"{reason}; repeated resume failure at {phase_name}")


def handle_failed_claimed_issue(
        claimed_issue: ClaimedIssue,
        reason: str,
        started_at: float | None = None,
        external: bool = False,
) -> None:
    """Handle a failed claimed issue, then revert its claim.

    An external failure (a typed GitHub or git-transport exception) is not the
    issue's fault: release the claim silently so it is retried, with no preserved
    branch, comment, or `human-intervention` label. A logical failure preserves
    work and applies the follow-up. §FS-human-intervention-policy
    """
    if is_user_interrupt_requested():
        raise KeyboardInterrupt
    # Every terminal failure names where it failed, whatever route it took here.
    # §FS-forge-run-location-reporting.3
    failure_location = resolve_claimed_issue_failure_location(claimed_issue)
    report_run_failure(failure_location, f"ERROR: {reason}")
    if external:
        log_stage(
            "issue-external-failure",
            (
                f"Issue #{claimed_issue.issue['number']} failed on an external dependency "
                f"({reason}); releasing the claim for retry without human-intervention."
            ),
        )
        revert_claimed_issue(claimed_issue, reason)
        return
    repeated_resume_failure_phase = _repeated_resume_failure_phase(claimed_issue)
    if repeated_resume_failure_phase is not None:
        handle_repeated_resume_failure(claimed_issue, repeated_resume_failure_phase, reason)
        return
    preservation_result = preserve_failed_work_for_follow_up(claimed_issue)
    if is_user_interrupt_requested():
        raise KeyboardInterrupt
    apply_failed_run_follow_up(
        claimed_issue,
        started_at=started_at,
        preservation_result=preservation_result,
        failure_location=failure_location,
    )
    try:
        refresh_preserved_branch_logs(claimed_issue, preservation_result)
    except Exception as exc:
        print(
            f"ERROR: Failed to refresh preserved logs for issue #{claimed_issue.issue['number']}: {exc!r}",
            file=sys.stderr,
        )
        traceback.print_exc()
    revert_claimed_issue(claimed_issue, reason)


def handle_completed_run(run_result: WorkflowRunResult) -> bool:
    """Finalize a completed workflow run and return the final handled result."""
    claimed_issue = run_result.claimed_issue
    try:
        if is_user_interrupt_requested():
            raise KeyboardInterrupt
        if not run_result.success:
            handle_failed_claimed_issue(
                claimed_issue,
                "workflow failure",
                started_at=run_result.started_at,
                external=run_result.failure_was_external,
            )
            return False
        try:
            finalize_successful_issue(claimed_issue)
        except Exception as exc:
            print(
                f"ERROR: Issue #{claimed_issue.issue['number']} finalization raised an exception: {exc!r}",
                file=sys.stderr,
            )
            traceback.print_exc()
            handle_failed_claimed_issue(
                claimed_issue,
                "finalization failure",
                started_at=run_result.started_at,
                external=is_external_failure_exception(exc),
            )
            return False
        apply_chunked_dynamic_access_completion_follow_up(claimed_issue)
        maybe_apply_human_intervention_follow_up(
            claimed_issue,
            workflow_success=True,
            started_at=run_result.started_at,
        )
        return True
    except KeyboardInterrupt:
        preserve_user_interrupt_reason()
        raise
    except Exception as exc:
        print(
            f"ERROR: Issue #{claimed_issue.issue['number']} failure handling raised an exception: {exc!r}",
            file=sys.stderr,
        )
        traceback.print_exc()
        return False


def process_claimed_issue_lifecycle(
        claimed_issue: ClaimedIssue,
        strategy_name: str | None,
        keep_tests_without_dynamic_access: bool,
        canonical_metrics_repo_path: str,
) -> bool:
    """Run workflow, finalize or revert, and always clean up the claimed issue workspace."""
    lifecycle_completed = False
    started_at = time.time()
    stable_cwd = claimed_issue.base_reachability_metadata_path
    # This run owns its thread's location for the whole lifecycle.
    # §FS-forge-run-location-reporting.2
    reset_run_location()
    bind_run_context(f"issue #{claimed_issue.issue['number']} {claimed_issue.issue_coordinates}")
    try:
        os.chdir(stable_cwd)
        run_result = run_claimed_issue(
            claimed_issue,
            strategy_name,
            keep_tests_without_dynamic_access,
        )
        if is_user_interrupt_requested():
            raise KeyboardInterrupt
        handled = handle_completed_run(run_result)
        lifecycle_completed = True
        if handled:
            log_success_banner(
                format_issue_result_message(
                    claimed_issue,
                    "Workflow finished, follow-up completed, and the issue was finalized.",
                )
            )
        else:
            log_failure_banner(
                format_issue_result_message(
                    claimed_issue,
                    (
                        "Workflow failed; failure follow-up was attempted. Check prior errors "
                        "for follow-up and claim status."
                    ),
                ),
                file=sys.stderr,
            )
        return handled
    except GradleBootstrapFailure as exc:
        if not lifecycle_completed:
            mark_user_interrupt_requested(INTERRUPT_REASON_GRADLE_BOOTSTRAP)
            revert_claimed_issue(claimed_issue, INTERRUPT_REASON_GRADLE_BOOTSTRAP)
            log_failure_banner(
                format_issue_result_message(
                    claimed_issue,
                    (
                        "Run stopped on a shared Gradle bootstrap failure; the issue claim was "
                        "reverted without failed-work preservation or human-intervention follow-up."
                    ),
                ),
                file=sys.stderr,
            )
            raise KeyboardInterrupt from exc
        raise
    except BaseException as exc:
        if not lifecycle_completed:
            if is_interrupt_exception(exc) or is_user_interrupt_requested():
                preserve_user_interrupt_reason()
                revert_claimed_issue(claimed_issue, get_user_interrupt_reason())
                raise
            else:
                try:
                    handle_failed_claimed_issue(
                        claimed_issue,
                        f"unhandled lifecycle failure ({type(exc).__name__})",
                        started_at=started_at,
                        external=is_external_failure_exception(exc),
                    )
                    log_failure_banner(
                        format_issue_result_message(
                            claimed_issue,
                            (
                                "Workflow failed with an unhandled lifecycle error; failure follow-up "
                                "was attempted and the issue claim was reverted."
                            ),
                        ),
                        file=sys.stderr,
                    )
                except KeyboardInterrupt:
                    preserve_user_interrupt_reason()
                    revert_claimed_issue(claimed_issue, get_user_interrupt_reason())
                    raise
                return False
        raise
    finally:
        try:
            os.chdir(stable_cwd)
            cleanup_issue_workspace(claimed_issue, canonical_metrics_repo_path)
        except Exception as exc:
            print(
                f"ERROR: Failed to clean up workspaces for issue #{claimed_issue.issue['number']}: {exc!r}",
                file=sys.stderr,
            )
            traceback.print_exc()



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


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Resolve graalvm-reachability-metadata issues automatically."
    )

    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument(
        "--label",
        choices=[LABEL_LIBRARY_NEW, LABEL_LIBRARY_UPDATE, LABEL_JAVAC_FAIL, LABEL_JAVA_RUN_FAIL, LABEL_NI_RUN_FAIL],
        help="GitHub label to filter issues and select the pipeline.",
    )
    mode.add_argument(
        "--issue-number", type=int,
        help="Process a single issue by number. The pipeline label is detected from the issue.",
    )
    mode.add_argument(
        "--review-pr",
        metavar="LABEL",
        help="Review open pull requests with the given GitHub label using the analysis agent.",
    )
    mode.add_argument(
        "--run-work-queues",
        action="store_true",
        help="Process all configured issue and review queues in one Python process.",
    )
    mode.add_argument(
        "--clear-issue-caches",
        action="store_true",
        help="Delete local issue claim/search caches used by work-queue scanning and exit.",
    )

    parser.add_argument(
        "--limit", type=int, default=DEFAULT_MAX_ISSUES,
        help=f"Maximum number of items to process per run (default: {DEFAULT_MAX_ISSUES}).",
    )
    parser.add_argument(
        "--reachability-metadata-path",
        help=(
            "Path to the graalvm-reachability-metadata repository. "
            "If omitted, the parent checkout of this Forge directory is used."
        ),
    )
    parser.add_argument(
        "--strategy-name",
        help="Workflow strategy name to pass to the pipeline. If omitted, the pipeline default is used.",
    )
    parser.add_argument(
        "--fixture-testing",
        action="store_true",
        help=(
            "Use local GitHub issue fixtures instead of live GitHub. Combine with "
            "--issue-number, --label/--limit, or --run-work-queues for the E2E run."
        ),
    )
    parser.add_argument(
        "--graalvm-version-check",
        choices=GRAALVM_VERSION_CHECK_MODES,
        default=None,
        help=(
            "How the startup host-requirement gate treats a GraalVM version mismatch: `strict` stops "
            "the run, `warn` reports it, `off` skips the version match. Native Image and the "
            "reachability-metadata schema stay mandatory in every mode. Defaults to "
            f"{GRAALVM_VERSION_CHECK_ENV_VAR}, then {DEFAULT_GRAALVM_VERSION_CHECK}."
        ),
    )
    parser.add_argument(
        "--period",
        type=validate_review_period,
        help=(
            "Repeat `--review-pr` runs after the given period. Accepts seconds or "
            "s/m/h/d suffixes, for example `300`, `5m`, or `1h`."
        ),
    )
    parser.add_argument(
        "--keep-tests-without-dynamic-access",
        action="store_true",
        help=(
            "Forwarded to the new-library pipeline. Keeps generated tests for dynamic-access "
            "strategies even when no dynamic-access coverage is added."
        ),
    )
    parser.add_argument(
        "--offset", type=validate_non_negative_integer, default=0,
        help="Number of issues to skip from the start of the list (default: 0).",
    )
    parser.add_argument(
        "--priority",
        choices=PRIORITY_CHOICES,
        help=(
            "Process only one issue tier: high, priority, or normal. "
            "Without this option, all tiers are drained in order."
        ),
    )
    parser.add_argument(
        "--random-offset",
        dest="random_offset",
        action="store_true",
        default=None,
        help=(
            "Start issue scanning at a random open issue offset for the selected label "
            "or run-work-queues work queue. "
            f"The random range is capped at GitHub search's first {GITHUB_SEARCH_MAX_RESULTS} results."
        ),
    )
    parser.add_argument(
        "--no-random-offset",
        dest="random_offset",
        action="store_false",
        help="Disable random issue scan offsets for run-work-queues or selected-label runs.",
    )
    parser.add_argument(
        "--user-requested-only",
        dest="user_requested_only",
        action="store_true",
        default=None,
        help=(
            "Fetch only user-requested issue queue items by excluding configured "
            "automation and maintainer issue authors."
        ),
    )
    parser.add_argument(
        "--parallelism",
        type=validate_parallelism,
        default=DEFAULT_PARALLELISM,
        help=f"Number of workflows to run in parallel (1-{MAX_PARALLELISM}).",
    )
    parser.add_argument(
        "--take-blocked-issues",
        dest="take_blocked_issues",
        action="store_true",
        default=DEFAULT_TAKE_BLOCKED_ISSUES,
        help="Claim issues even when GitHub shows open blocking issues. Defaults to disabled.",
    )
    parser.add_argument(
        "-v", "--verbose",
        action="store_true",
        help="Show narration hidden by the compact default output.",
    )

    args = parser.parse_args(argv)
    if args.period is not None and args.review_pr is None:
        parser.error("--period can only be used with --review-pr")
    if args.fixture_testing:
        if args.review_pr is not None:
            parser.error("--fixture-testing cannot be combined with --review-pr")
        if args.offset != 0:
            parser.error("--fixture-testing cannot be combined with --offset")
        if args.random_offset is not None:
            parser.error("--fixture-testing cannot be combined with --random-offset/--no-random-offset")
    if args.random_offset is not None and args.label is None and not args.run_work_queues:
        parser.error("--random-offset/--no-random-offset can only be used with --label or --run-work-queues")
    if args.priority is not None and args.label is None and not args.run_work_queues:
        parser.error("--priority can only be used with --label or --run-work-queues")
    if args.random_offset is True and args.offset != 0:
        parser.error("--random-offset cannot be combined with --offset")
    return args


def log_fixture_testing_selection(issue_number: int, label: str, strategy_name: str | None) -> None:
    """Log fixture-backed E2E routing before workflow execution starts."""
    fixture_state = require_fixture_github_state()
    fixture_path = fixture_state.get_issue_fixture_path(issue_number)
    strategy_override = f", strategy_override={strategy_name}" if strategy_name else ""

    print()
    log_stage(
        "fixture-testing",
        (
            "Selected fixture-backed issue run: "
            f"mode=fixture-testing, issue=#{issue_number}, fixture={fixture_path}, "
            f"queue_label={label}{strategy_override}"
        ),
    )


def process_single_issue(
        issue_number: int,
        base_reachability_metadata_path: str,
        canonical_metrics_repo_path: str,
        strategy_name: str | None,
        keep_tests_without_dynamic_access: bool,
        authenticated_user: str,
        take_blocked_issues: bool = DEFAULT_TAKE_BLOCKED_ISSUES,
) -> bool:
    """Fetch and process a single issue by number, claiming only in live GitHub mode."""
    validate_issue_processing_environment()

    issue, label = get_issue_by_number(issue_number)
    log_debug("issue-scan", f"Issue #{issue_number} matched pipeline label: {label}")

    if is_fixture_testing_enabled():
        with fixture_issue_run_log(issue_number):
            log_fixture_testing_selection(issue_number, label, strategy_name)
            claimed_issue = build_fixture_claimed_issue(
                issue,
                label,
                base_reachability_metadata_path,
                canonical_metrics_repo_path,
            )
            if not claimed_issue:
                log_failure_banner(f"Could not prepare fixture issue #{issue_number}.", file=sys.stderr)
                sys.exit(1)
            return process_claimed_issue_lifecycle(
                claimed_issue,
                strategy_name,
                keep_tests_without_dynamic_access,
                canonical_metrics_repo_path,
            )

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
        log_failure_banner(f"Could not claim issue #{issue_number}.", file=sys.stderr)
        sys.exit(1)

    return process_claimed_issue_lifecycle(
        claimed_issue,
        strategy_name,
        keep_tests_without_dynamic_access,
        canonical_metrics_repo_path,
    )


def resolve_host_requirement_queues(args: argparse.Namespace) -> QueueRequirements:
    """Select the capabilities the invoked Forge mode needs, not the ones its queue limits allow.

    §FS-forge-host-requirements
    """
    github_work = not args.fixture_testing
    if args.review_pr is not None:
        return QueueRequirements(issue_work=False, review_work=True, github_work=github_work)
    if args.run_work_queues:
        enabled_queues = resolve_queue_requirements(os.environ)
        return QueueRequirements(
            issue_work=enabled_queues.issue_work,
            review_work=enabled_queues.review_work,
            github_work=github_work,
        )
    return QueueRequirements(issue_work=True, review_work=False, github_work=github_work)


def resolve_host_requirement_strategy_names(args: argparse.Namespace) -> list[str]:
    """Return every test strategy reachable from the enabled issue queues.

    §FS-forge-host-requirements
    """
    if not resolve_host_requirement_queues(args).issue_work:
        return []
    if not args.run_work_queues:
        return [args.strategy_name] if args.strategy_name else []

    defaults_by_label = {
        LABEL_LIBRARY_NEW: DEFAULT_NEW_LIBRARY_STRATEGY_NAME,
        LABEL_LIBRARY_UPDATE: DEFAULT_LIBRARY_UPDATE_STRATEGY_NAME,
        LABEL_JAVAC_FAIL: DEFAULT_JAVAC_STRATEGY,
        LABEL_JAVA_RUN_FAIL: DEFAULT_JAVA_RUN_STRATEGY,
        LABEL_NI_RUN_FAIL: DEFAULT_NI_RUN_STRATEGY_NAME,
    }
    strategy_names: list[str] = []
    for queue_config in get_work_queue_configs_from_environment(args.strategy_name):
        if queue_config.limit <= 0:
            continue
        strategy_name = queue_config.strategy_name or defaults_by_label[queue_config.label]
        if strategy_name not in strategy_names:
            strategy_names.append(strategy_name)
    return strategy_names


@pipeline_step(PHASE_CLAIM, STEP_CHECK_HOST_REQUIREMENTS)
def require_host_requirements(args: argparse.Namespace, reachability_metadata_path: str) -> None:
    """Stop before any work when this host cannot run the invoked Forge mode.

    Forge paths are checked against `FORGE_DIR` and the repository paths against the
    checkout this run selected, which `--reachability-metadata-path` can move away from
    the checkout that contains Forge (§FS-forge-host-requirements).
    """
    log_step_progress(
        PHASE_CLAIM,
        STEP_CHECK_HOST_REQUIREMENTS,
        "Checking host requirements",
    )
    ensure_host_requirements(
        FORGE_DIR,
        requirements=resolve_host_requirement_queues(args),
        graalvm_version_check=resolve_graalvm_version_check(args.graalvm_version_check),
        repo_dir=reachability_metadata_path,
        test_strategy_names=resolve_host_requirement_strategy_names(args),
        verbose=args.verbose or debug_logging_enabled(),
    )
    log_step_progress(
        PHASE_CLAIM,
        STEP_CHECK_HOST_REQUIREMENTS,
        "Host requirements passed",
    )


def main() -> None:
    clear_user_interrupt_requested()
    previous_sigint_handler = signal.getsignal(signal.SIGINT)
    signal.signal(signal.SIGINT, _handle_sigint)
    args = parse_args()
    if args.verbose:
        enable_verbose_logging()
    normal_exit = False

    try:
        if args.fixture_testing:
            # Fix one shared run timestamp; each issue's evidence is written under
            # `fixture-e2e-logs/issue-<number>/<timestamp>/` by `fixture_issue_run_log`.
            get_fixture_run_timestamp()
            fixture_state = configure_fixture_testing()
            if args.issue_number is not None:
                fixture_selection = f"selected issue #{args.issue_number}"
            elif args.run_work_queues:
                fixture_selection = "work-queue mode"
            else:
                fixture_selection = f"label '{args.label}'"
            log_stage(
                "fixture-loading",
                (
                    f"Loaded {len(fixture_state.issue_numbers)} fixture issue(s); "
                    f"{fixture_selection}."
                ),
            )
        if args.clear_issue_caches:
            clear_issue_caches()
            return
        # The gate must check the repository this run actually operates on, so it is resolved first.
        reachability_metadata_path = resolve_reachability_repo_root(args.reachability_metadata_path)
        enter_phase(PHASE_CLAIM)
        require_host_requirements(args, reachability_metadata_path)
        if args.strategy_name:
            with run_step(PHASE_CLAIM, STEP_CHECK_STRATEGY_AND_MODEL, operand=args.strategy_name):
                log_step_progress(
                    PHASE_CLAIM,
                    STEP_CHECK_STRATEGY_AND_MODEL,
                    f"Checking strategy {args.strategy_name}",
                )
                require_strategy_by_name(args.strategy_name)
                log_step_progress(
                    PHASE_CLAIM,
                    STEP_CHECK_STRATEGY_AND_MODEL,
                    f"Strategy {args.strategy_name} accepted",
                )
        metrics_repo_path = resolve_metrics_repo_root(reachability_metadata_path, None)

        if not PROJECT_NUMBER:
            print("ERROR: GITHUB_PROJECT_NUMBER env var is not set.", file=sys.stderr)
            sys.exit(1)
        if args.run_work_queues:
            process_work_queues(
                reachability_metadata_path,
                metrics_repo_path,
                None,
                args.strategy_name,
                args.keep_tests_without_dynamic_access,
                args.parallelism,
                random_offset_override=args.random_offset,
                user_requested_only_override=args.user_requested_only,
                priority_override=args.priority,
                take_blocked_issues=args.take_blocked_issues,
            )
        elif args.review_pr is not None:
            authenticated_user = resolve_authenticated_user()
            run_pull_request_review_loop(
                args.review_pr,
                args.limit,
                reachability_metadata_path,
                authenticated_user,
                args.period,
            )
        elif args.issue_number is not None:
            authenticated_user = resolve_authenticated_user()
            process_single_issue(
                args.issue_number,
                reachability_metadata_path,
                metrics_repo_path,
                args.strategy_name,
                args.keep_tests_without_dynamic_access,
                authenticated_user,
                args.take_blocked_issues,
            )
        elif is_fixture_testing_enabled():
            process_fixture_issues_for_label(
                args.label,
                args.limit,
                reachability_metadata_path,
                metrics_repo_path,
                args.strategy_name,
                args.keep_tests_without_dynamic_access,
                user_requested_only=resolve_user_requested_only(args.user_requested_only),
                priority=args.priority,
            )
        else:
            authenticated_user = resolve_authenticated_user()
            user_requested_only = resolve_user_requested_only(args.user_requested_only)
            issue_scan_offset = args.offset
            if args.random_offset is True:
                issue_scan_offset = resolve_random_issue_scan_offset(
                    args.label,
                    priority=args.priority,
                    user_requested_only=user_requested_only,
                )
                log_debug(
                    "issue-scan",
                    f"Selected random start offset {issue_scan_offset} for label '{args.label}'",
                )
            process_issues_with_label(
                args.label,
                args.limit,
                issue_scan_offset,
                reachability_metadata_path,
                metrics_repo_path,
                args.strategy_name,
                args.keep_tests_without_dynamic_access,
                authenticated_user,
                args.parallelism,
                take_blocked_issues=args.take_blocked_issues,
                user_requested_only=user_requested_only,
                priority=args.priority,
            )
        normal_exit = True
    except KeyboardInterrupt:
        if is_shutdown_requested():
            mark_shutdown_requested()
        else:
            preserve_user_interrupt_reason()
        if is_shutdown_request_interrupt():
            print(
                f"\nRun stopped because the Forge stop marker exists at {describe_active_shutdown_signal_path()}.",
                file=sys.stderr,
            )
            sys.exit(0)
        if is_gradle_bootstrap_interrupt():
            log_failure_banner(
                "Shared Gradle bootstrap failed after retry. Stop current run and retry later.",
                file=sys.stderr,
            )
            sys.exit(GRADLE_BOOTSTRAP_EXIT_CODE)
        print("\nERROR: Run interrupted by Ctrl+C.", file=sys.stderr)
        sys.exit(130)
    except GitHubRateLimitExceeded as exc:
        log_failure_banner(f"{exc}. Stop current run and retry after reset.", file=sys.stderr)
        sys.exit(GITHUB_RATE_LIMIT_EXIT_CODE)
    finally:
        if normal_exit:
            log_success_banner("Run complete.")
        signal.signal(signal.SIGINT, previous_sigint_handler)


if __name__ == "__main__":
    main()
