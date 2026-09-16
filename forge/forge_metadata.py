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
from dispatcher.interrupts import sleep_until_shutdown_or_timeout  # noqa: F401 - re-exported dispatcher API
from dispatcher.run_logs import (  # noqa: F401 - re-exported dispatcher API
    output_tail,
    run_checked_command,
    read_log_tail,
    read_log_text,
    extract_codex_final_message,
    extract_codex_token_usage_summary,
)
from dispatcher.worktrees import (  # noqa: F401 - re-exported dispatcher API
    build_review_run_id,
    cleanup_review_workspace,
    create_review_workspace,
    fetch_review_base_ref,
    claimed_issue_worktree_is_valid,
    require_claimed_issue_worktree,
    build_issue_run_id,
    create_preflight_info_dir,
    create_detached_worktree,
    fetch_default_base_ref,
    resolve_git_commit,
    fetch_issue_base_commit,
    remove_worktree,
)
from dispatcher.pr_state import (  # noqa: F401 - re-exported dispatcher API
    get_pull_requests_with_label,
    get_pull_requests_with_labels,
    attach_pull_request_state,
    pull_request_has_label,
    get_pull_request_state,
    get_pull_request_reviews,
    dismiss_requested_changes_reviews,
    has_successful_pull_request_ci,
    has_failed_pull_request_ci,
    get_pull_request_workflow_runs,
    get_failed_workflow_run_ids,
    rerun_failed_pull_request_workflow_jobs,
    resolve_pull_request_merge_flag,
    is_metadata_index_file_path,
    get_pull_request_changed_files,
    get_pull_request_changed_index_files,
    get_pull_request_url,
)
from dispatcher.pr_publication import (  # noqa: F401 - re-exported dispatcher API
    _load_trusted_publisher_module,
    approve_pull_request_from_descriptor,
    disable_pull_request_auto_merge,
    dismiss_forge_approval_reviews,
    enable_pull_request_auto_merge,
    ensure_pull_request_unapproved,
    publication_review_disposition,
    reconcile_rejected_publication,
    validate_pull_request_publication,
)
from dispatcher.pr_merge import (  # noqa: F401 - re-exported dispatcher API
    validate_index_files_on_current_master_candidate,
    validate_pull_request_indexes_before_merge,
    resolve_non_final_chunked_dynamic_access_issue,
    apply_chunked_dynamic_access_merge_follow_up,
    pull_request_needs_merge_follow_up,
    mark_pull_request_merge_follow_up_pending,
    apply_unblocked_issue_merge_follow_up,
    reconcile_auto_merged_pull_request_follow_ups,
    is_pull_request_conflicting,
    _BENCHMARK_RESULTS_PATH_PATTERN,
    _entries_by_run_id,
    resolve_benchmark_results_conflict,
    resolve_pull_request_merge_conflict,
)
from dispatcher.ci_repair import (  # noqa: F401 - re-exported dispatcher API
    CIRepairOutcome,
    _ci_repair_changed_paths,
    ensure_infrastructure_issue,
    build_ci_repair_prompt,
    _read_ci_repair_outcome,
    _ci_repair_path_is_allowed,
    repair_failed_ci_pull_request,
    reconcile_failed_ci_pull_request,
)
from dispatcher.failure_preservation import (  # noqa: F401 - re-exported dispatcher API
    FailurePreservationResult,
    preservation_failed_worktree_paths,
    _sanitize_branch_segment,
    build_failure_preservation_branch_name,
    build_failure_preservation_branch_prefix,
    build_origin_branch_url,
    copy_library_logs_to_preserved_worktree,
    baseline_stats_relpaths_for_preservation,
    git_rebase_in_progress,
    run_preservation_git,
    preserve_failed_work_branch,
    build_fixture_failure_preservation_result,
    refresh_preserved_branch_logs,
    resolve_claimed_issue_failure_location,
    lead_comment_with_failure_location,
    ensure_preserved_branch_link_in_comment,
    record_publication_metrics_from_pending,
)
from dispatcher.continuation import (  # noqa: F401 - re-exported dispatcher API
    list_remote_branches_by_prefix,
    remote_branch_commit_timestamp,
    sort_remote_branches_by_commit_time,
    find_preserved_branch_candidates,
    fetch_remote_branch,
    load_continuation_marker_from_branch,
    resolve_issue_continuation_marker,
    checkout_continuation_branch,
    create_or_load_run_continuation_marker,
    library_update_route_from_marker,
    restore_library_update_route_from_marker,
    record_library_update_route_in_marker,
    restore_library_preparation_preflight_from_marker,
    record_library_preparation_preflight_in_marker,
    library_update_route_artifact_root,
)
from dispatcher.human_intervention import (  # noqa: F401 - re-exported dispatcher API
    HumanInterventionCandidate,
    _load_pending_run_metrics,
    _load_dynamic_access_snapshot_from_metrics,
    _resolve_dynamic_access_report_path,
    _load_dynamic_access_snapshot_from_report,
    is_external_failure_exception,
    resolve_human_intervention_candidate,
    _collect_human_intervention_read_only_files,
    _build_human_intervention_analysis_prompt,
    _build_human_intervention_fallback_comment,
    _sanitize_log_name,
    _repo_relative_path,
    get_codex_failure_analysis_log_path,
    _log_file_mentions_issue,
    collect_issue_log_paths,
    _format_failure_metrics_summary,
    _format_log_path_list,
    _build_failed_generation_analysis_prompt,
    _build_failed_generation_fallback_comment,
    run_codex_failed_generation_analysis,
    run_human_intervention_analysis,
)
from dispatcher.failure_follow_up import (  # noqa: F401 - re-exported dispatcher API
    post_human_intervention_comment_and_label,
    preservation_result_has_continuation_marker,
    load_preservation_result_continuation_marker,
    maybe_apply_human_intervention_follow_up,
    _build_finalization_failure_comment,
    apply_failed_run_follow_up,
    _lead_failed_run_comment,
)
from dispatcher.review_loop import (  # noqa: F401 - re-exported dispatcher API
    ReviewQueueConfig,
    ReviewQueueSelection,
    is_review_pull_request_base_eligible,
    is_review_pull_request_eligible,
    select_review_pull_requests,
    _process_descriptor_pull_request,
    process_pull_requests_with_label,
    run_pull_request_review_loop,
)












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
