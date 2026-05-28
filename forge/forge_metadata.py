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
orchestration contract in §ORCH-forge-orchestration-spec.

Usage:
  python forge-metadata.py --label <label> [--limit N] [--offset N|--random-offset]
      [--strategy-name <name>] [--reachability-metadata-path <path>]
  python forge-metadata.py --fixture-testing --issue-number <number>
      --strategy-name <name> [--reachability-metadata-path <path>]
  python forge-metadata.py --review-pr <label> [--limit N]
      [--reachability-metadata-path <path>] [--review-model <model>] [--period <seconds|Nm|Nh|Nd>]
"""

import argparse
import concurrent.futures
import errno
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
from dataclasses import dataclass, replace
from typing import Any, Optional
from urllib.parse import quote

import ai_workflows.core  # noqa: F401 - triggers strategy registration
from ai_workflows.drivers.add_new_library_support import (
    DEFAULT_MODEL_NAME,
    init_agent as init_workflow_agent,
    list_all_files,
    main as run_add_new_library_support_workflow,
)
from ai_workflows.drivers.fix_javac_fail import (
    main as run_fix_javac_workflow,
)
from ai_workflows.drivers.fix_java_run_fail import (
    main as run_fix_java_run_workflow,
)
from ai_workflows.drivers.fix_ni_run import (
    main as run_fix_ni_run_workflow,
)
from ai_workflows.drivers.improve_library_coverage import (
    main as run_improve_library_coverage_workflow,
)
from ai_workflows.core.workflow_strategy import RUN_STATUS_CHUNK_READY, RUN_STATUS_FAILURE
from git_scripts.common_git import (
    GITHUB_TRANSIENT_RETRY_ATTEMPTS,
    GitHubRateLimitExceeded,
    _github_retry_delay_seconds,
    _log_github_transient_retry,
    ensure_gh_authenticated,
    get_issue_project_item_status,
    get_origin_owner,
    is_github_rate_limit_text,
    is_github_transient_failure_text,
    log_github_query,
    run_github_command_with_retries,
    run_github_json_with_retries,
    run_github_with_retries,
)
from git_scripts.make_pr_javac_fix import main as run_make_pr_javac_fix
from git_scripts.make_pr_java_run_fix import main as run_make_pr_java_run_fix
from git_scripts.make_pr_new_library_support import main as run_make_pr_new_library_support
from git_scripts.make_pr_not_for_native_image import main as run_make_pr_not_for_native_image
from git_scripts.make_pr_ni_run_fix import main as run_make_pr_ni_run_fix
from git_scripts.make_pr_improve_coverage import main as run_make_pr_improve_coverage
from utility_scripts.dynamic_access_report import load_dynamic_access_coverage_report
from utility_scripts.fixture_github import FixtureGitHubState, load_fixture_github_state
from utility_scripts.library_stats import resolve_stats_file_path
from utility_scripts.large_library_progress import (
    LABEL_LARGE_LIBRARY_BLOCKED,
    LABEL_LARGE_LIBRARY_NEXT_PART,
    LABEL_LARGE_LIBRARY_PART,
    LABEL_LARGE_LIBRARY_SERIES,
    LargeLibraryProgressState,
    copy_progress_artifacts,
    find_progress_state_path,
)
from utility_scripts.library_update_compatibility_probe import (
    ROUTE_COMPATIBLE,
    ROUTE_JAVA_RUN_FAILURE,
    ROUTE_JAVAC_FAILURE,
    ROUTE_SETUP_FAILURE,
    probe_missing_version_compatibility,
)
from utility_scripts.metadata_index import (
    MATCH_NEW_VERSION,
    LibraryUpdateTarget,
    coordinate_parts as metadata_coordinate_parts,
    get_not_for_native_image_marker,
    is_not_for_native_image,
    resolve_library_update_target,
    resolve_metadata_version,
    resolve_test_dir,
)
from utility_scripts.metrics_writer import PENDING_METRICS_FILENAME, execution_metrics_path, read_pending_metrics
from utility_scripts.repo_path_resolver import (
    git_env_limited_to_repo_root,
    get_forge_subdir_name,
    get_repo_root,
    require_complete_reachability_repo,
    resolve_repo_roots,
)
from utility_scripts.stage_logger import log_failure_banner, log_stage, log_success_banner
from utility_scripts.shutdown_signal import get_active_shutdown_signal_path, is_shutdown_requested
from utility_scripts.schema_validator import validate_run_metrics
from utility_scripts.strategy_loader import load_strategy_by_name, require_strategy_by_name
from utility_scripts.task_logs import (
    build_task_log_path,
    display_log_path,
    resolve_logs_root,
    sanitize_library_log_segment,
)
from utility_scripts.workflow_setup import require_graalvm_home_env

try:
    import fcntl
except ImportError:
    fcntl = None

DEFAULT_MAX_ISSUES = 5  # Default maximum number of issues to process per run
DEFAULT_PARALLELISM = 1
MAX_PARALLELISM = 4
DEFAULT_ISSUE_SCAN_BATCH_SIZE = 25
ISSUE_SCAN_PROGRESS_LOG_INTERVAL = 100
GITHUB_API_MAX_PAGE_SIZE = 100
GITHUB_SEARCH_MAX_RESULTS = 1000
PRIORITY_BLOCKING_LIBRARY_THRESHOLD = 10
PRIORITY_BLOCKING_ISSUE_QUERY_CHUNK_SIZE = 25
DEFAULT_TAKE_BLOCKED_ISSUES = True
# GitHub validates GraphQL node cost against worst-case first values; 5 issues can exceed 500k here.
ISSUE_CLAIM_PREFLIGHT_CHUNK_SIZE = 4
ISSUE_CLAIM_CACHE_VERSION = 1
ISSUE_CLAIM_CACHE_FILENAME = "issue-cache-v1.json"
ISSUE_CLAIM_CACHE_LOCK_FILENAME = "issue-cache-v1.lock"
DEFAULT_ISSUE_CLAIM_CACHE_TTL_SECONDS = 15 * 60
ISSUE_SEARCH_CACHE_VERSION = 1
ISSUE_SEARCH_CACHE_FILENAME = "issue-search-cache-v1.json"
ISSUE_SEARCH_CACHE_LOCK_FILENAME = "issue-search-cache-v1.lock"
DEFAULT_ISSUE_SEARCH_CACHE_TTL_SECONDS = 10 * 60
GITHUB_RATE_LIMIT_EXIT_CODE = 75
REVIEW_PERIOD_SUFFIX_SECONDS = {
    "s": 1,
    "m": 60,
    "h": 60 * 60,
    "d": 24 * 60 * 60,
}
FAILED_CI_STATES = {"FAILURE", "ERROR"}
RERUNNABLE_WORKFLOW_RUN_CONCLUSIONS = {"failure"}
MAX_AUTOMATED_WORKFLOW_RERUN_ATTEMPTS = 3

REPO = "oracle/graalvm-reachability-metadata"
PROJECT_NUMBER = 30

STATUS_FIELD_NAME = "Status"  # Name of the project Status field
STATUS_TODO = "Todo"
STATUS_IN_PROGRESS = "In Progress"
STATUS_DONE = "Done"

LABEL_LIBRARY_NEW = "library-new-request"
LABEL_LIBRARY_UPDATE = "library-update-request"
LABEL_JAVAC_FAIL = "fails-javac-compile"
LABEL_JAVA_RUN_FAIL = "fails-java-run"
LABEL_NI_RUN_FAIL = "fails-native-image-run"
LABEL_PR_JAVAC_FIX = "fixes-javac-fail"
LABEL_PR_JAVA_RUN_FIX = "fixes-java-run-fail"
LABEL_PR_NI_RUN_FIX = "fixes-native-image-run-fail"
LABEL_PR_LIBRARY_UPDATE = "library-update-request"
LABEL_PR_LIBRARY_BULK_UPDATE = "library-bulk-update"
LABEL_PRIORITY = "priority"
LABEL_HUMAN_INTERVENTION = "human-intervention"
LABEL_HUMAN_INTERVENTION_FIXED = "human-intervention-fixed"
LABEL_NOT_FOR_NATIVE_IMAGE = "not-for-native-image"
FIXTURE_AUTHENTICATED_USER = "fixture-runner"

SCRATCH_WORKTREE_DIRNAME = "forge_worktrees"
SCRATCH_REVIEW_WORKTREE_DIRNAME = "forge_review_worktrees"
SCRATCH_FINAL_INDEX_VALIDATION_WORKTREE_DIRNAME = "forge_final_index_validation_worktrees"
SCRATCH_METRICS_DIRNAME = "forge_run_metrics"
ISSUE_CLAIM_LOCK_DIRNAME = "metadata-forge-issue-claim-locks"
ISSUE_CLAIM_CACHE_REASON_ASSIGNED = "assigned"
ISSUE_CLAIM_CACHE_REASON_HUMAN_INTERVENTION = "human_intervention"
ISSUE_CLAIM_CACHE_REASON_NOT_FOR_NATIVE_IMAGE = "not_for_native_image"
ISSUE_CLAIM_CACHE_REASON_BLOCKED = "blocked"
ISSUE_CLAIM_CACHE_REASON_CLOSED = "closed"
ISSUE_CLAIM_CACHE_REASON_MISSING_PROJECT_ITEM = "missing_project_item"
ISSUE_CLAIM_CACHE_REASON_NON_TODO = "non_todo"
ISSUE_CLAIM_CACHE_REASON_IN_PROGRESS = "in_progress"
ISSUE_CLAIM_CACHE_REASONS = {
    ISSUE_CLAIM_CACHE_REASON_ASSIGNED,
    ISSUE_CLAIM_CACHE_REASON_HUMAN_INTERVENTION,
    ISSUE_CLAIM_CACHE_REASON_NOT_FOR_NATIVE_IMAGE,
    ISSUE_CLAIM_CACHE_REASON_BLOCKED,
    ISSUE_CLAIM_CACHE_REASON_CLOSED,
    ISSUE_CLAIM_CACHE_REASON_MISSING_PROJECT_ITEM,
    ISSUE_CLAIM_CACHE_REASON_NON_TODO,
    ISSUE_CLAIM_CACHE_REASON_IN_PROGRESS,
}
HUMAN_INTERVENTION_LOGS_DIRNAME = "human-intervention-logs"
SCRIPT_RUN_METRICS_DIR = "script_run_metrics"
FIXTURE_E2E_REPORT_DIR = "fixture-e2e"
FIXTURE_E2E_REPORT_FILENAME = "fixture-e2e-report.json"
ADD_NEW_LIBRARY_METRICS_FILE = "add_new_library_support.json"
FIX_JAVAC_METRICS_FILE = "fix_javac_fail.json"
FIX_JAVA_RUN_METRICS_FILE = "fix_java_run_fail.json"
LOCAL_CI_VERIFICATION_METRICS_KEY = "local_ci_verification"
LOW_DYNAMIC_ACCESS_COVERAGE_RATIO = 0.10
HUMAN_INTERVENTION_LABEL_COLOR = "B60205"
HUMAN_INTERVENTION_LABEL_DESCRIPTION = (
    "Requires manual follow-up because automated processing needs human attention"
)
NOT_FOR_NATIVE_IMAGE_LABEL_COLOR = "5319E7"
NOT_FOR_NATIVE_IMAGE_LABEL_DESCRIPTION = (
    "Artifact is tracked but is not applicable to GraalVM Native Image reachability metadata"
)
PRIORITY_LABEL_COLOR = "FBCA04"
PRIORITY_LABEL_DESCRIPTION = "Automation should process this issue before regular queue items"
LARGE_LIBRARY_LABEL_COLOR = "C5DEF5"
LARGE_LIBRARY_SERIES_LABEL_DESCRIPTION = "Issue is intentionally split across multiple reviewable PR parts"
LARGE_LIBRARY_NEXT_PART_LABEL_DESCRIPTION = "Previous large-library part merged; automation may resume the next part"
LARGE_LIBRARY_BLOCKED_LABEL_DESCRIPTION = "Large-library series cannot continue without manual help"
LARGE_LIBRARY_PART_LABEL_DESCRIPTION = "Pull request is one part of a large-library series"


DEFAULT_REVIEW_MODEL = "gpt-5.4"
DEFAULT_NEW_LIBRARY_STRATEGY_NAME = "basic_iterative_pi_gpt-5.4"
DEFAULT_LIBRARY_UPDATE_STRATEGY_NAME = "library_update_pi_gpt-5.5"
DEFAULT_JAVAC_STRATEGY_NAME = "javac_iterative_with_coverage_sources_pi_gpt-5.5"
DEFAULT_JAVA_RUN_STRATEGY_NAME = "java_run_iterative_with_coverage_sources_pi_gpt-5.5"
DEFAULT_WORK_QUEUE_STRATEGY_NAME = "dynamic_access_main_sources_pi_gpt-5.5"
CODEX_REVIEW_TIMEOUT_SECONDS = 1800
DEFAULT_WORKTREE_BASE_REF = "master"
DEV_GRAALVM_ENV_VAR = "GRAALVM_HOME"
POST_GENERATION_GRAALVM_ENV_VAR = "GRAALVM_HOME_25_0"
LATEST_EA_GRAALVM_ENV_VAR = "GRAALVM_HOME_LATEST_EA"
INTERRUPT_EXIT_CODES = {130, -int(signal.SIGINT)}
INTERRUPT_REASON_CTRL_C = "Ctrl+C interrupt"
INTERRUPT_REASON_SHUTDOWN = "shutdown request"
SHUTDOWN_SIGNAL_POLL_SECONDS = 5.0

_user_interrupt_requested = threading.Event()
_user_interrupt_reason = INTERRUPT_REASON_CTRL_C


@dataclass(frozen=True)
class ClaimedIssue:
    issue: dict
    label: str
    item_id: str
    base_reachability_metadata_path: str
    worktree_path: str
    scratch_metrics_repo_path: str
    issue_coordinates: str
    current_coordinates: str | None = None
    new_version: str | None = None
    large_library_resume_artifact: str | None = None
    large_library_part: int | None = None


@dataclass(frozen=True)
class WorkQueueConfig:
    label: str
    limit: int
    strategy_name: str | None = None
    random_offset: bool = False


@dataclass(frozen=True)
class ReviewQueueConfig:
    label: str
    limit: int
    model: str


@dataclass(frozen=True)
class IssueClaimPreflight:
    issue_number: int
    item_id: str | None
    project_status: str | None
    assignees: tuple[str, ...]
    open_blockers: tuple[int, ...]
    complete: bool


@dataclass(frozen=True)
class IssueClaimCacheObservation:
    issue_number: int
    reason: str
    assignees: tuple[str, ...] = ()
    project_status: str | None = None
    open_blockers: tuple[int, ...] = ()


@dataclass(frozen=True)
class CachedIssueClaimSkip:
    issue_number: int
    reason: str
    observed_at_epoch: float
    assignees: tuple[str, ...] = ()
    project_status: str | None = None
    open_blockers: tuple[int, ...] = ()


@dataclass(frozen=True)
class LibraryUpdateRouteLifecycle:
    """Dispatcher-owned record for a missing-version library-update route."""

    requested_coordinate: str
    requested_version: str | None
    selected_route: str
    selected_baseline_coordinate: str | None
    selected_driver_name: str | None
    selected_driver_script: str | None
    route_sidecar_json_path: str | None
    route_log_paths: tuple[str, ...]
    pending_metrics_file_path: str | None = None
    durable_metrics_file_path: str | None = None
    publication_script: str | None = None
    publication_label: str | None = None
    setup_error: str | None = None

    def to_json(self) -> dict:
        return {
            "requested_coordinate": self.requested_coordinate,
            "requested_version": self.requested_version,
            "selected_route": self.selected_route,
            "selected_baseline_coordinate": self.selected_baseline_coordinate,
            "selected_driver_name": self.selected_driver_name,
            "selected_driver_script": self.selected_driver_script,
            "route_sidecar_json_path": self.route_sidecar_json_path,
            "route_log_paths": list(self.route_log_paths),
            "pending_metrics_file_path": self.pending_metrics_file_path,
            "durable_metrics_file_path": self.durable_metrics_file_path,
            "publication_script": self.publication_script,
            "publication_label": self.publication_label,
            "setup_error": self.setup_error,
        }


@dataclass(frozen=True)
class WorkflowRunResult:
    claimed_issue: ClaimedIssue
    success: bool
    started_at: float | None = None
    routed_driver: "WorkflowDriverInvocation | None" = None
    library_update_route: LibraryUpdateRouteLifecycle | None = None


@dataclass(frozen=True)
class DynamicAccessCoverageSnapshot:
    covered_calls: int
    total_calls: int
    coverage_ratio: float
    source: str


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
    large_library_args: list[str]
    large_library_state_path: str | None
    large_library_part: int | None
    large_library_final: bool | None
    large_library_series_id: str | None
    not_for_native_image: bool = False

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
            "large_library_args": list(self.large_library_args),
            "large_library_state_path": self.large_library_state_path,
            "large_library_part": self.large_library_part,
            "large_library_final": self.large_library_final,
            "large_library_series_id": self.large_library_series_id,
            "not_for_native_image": self.not_for_native_image,
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


@dataclass(frozen=True)
class RoutedWorkflowDriverInvocation:
    invocation: WorkflowDriverInvocation | None
    library_update_route: LibraryUpdateRouteLifecycle | None = None


@dataclass(frozen=True)
class WorkflowExecutionOutcome:
    success: bool
    routed_driver: WorkflowDriverInvocation | None = None
    library_update_route: LibraryUpdateRouteLifecycle | None = None


@dataclass(frozen=True)
class FixtureE2EReportContext:
    issue_number: int
    fixture_path: str
    canonical_metrics_repo_path: str
    report_dir: str
    report_path: str
    run_id: str
    queue_label: str
    strategy_name: str
    model: str
    workflow_engine: str
    command_used: dict
    routed_driver: WorkflowDriverInvocation | None
    library_update_route: LibraryUpdateRouteLifecycle | None
    keep_tests_without_dynamic_access: bool


def mark_user_interrupt_requested(reason: str = INTERRUPT_REASON_CTRL_C) -> None:
    """Record that the current run is shutting down before normal completion."""
    global _user_interrupt_reason

    _user_interrupt_reason = reason
    _user_interrupt_requested.set()


def clear_user_interrupt_requested() -> None:
    """Clear the shutdown marker before starting a new top-level run."""
    global _user_interrupt_reason

    _user_interrupt_reason = INTERRUPT_REASON_CTRL_C
    _user_interrupt_requested.clear()


def is_user_interrupt_requested() -> bool:
    """Return True when the current run is unwinding before normal completion."""
    return _user_interrupt_requested.is_set()


def get_user_interrupt_reason() -> str:
    """Return the current run's shutdown reason."""
    return _user_interrupt_reason


def is_shutdown_request_interrupt() -> bool:
    """Return True when the current run is stopping because of the shared marker."""
    return is_user_interrupt_requested() and get_user_interrupt_reason() == INTERRUPT_REASON_SHUTDOWN


def mark_shutdown_requested() -> None:
    """Record that the shared Forge stop marker requested shutdown."""
    mark_user_interrupt_requested(INTERRUPT_REASON_SHUTDOWN)


def describe_active_shutdown_signal_path() -> str:
    """Return the active stop marker path for log messages."""
    return get_active_shutdown_signal_path() or "the configured stop marker"


def raise_if_shutdown_requested() -> None:
    """Raise KeyboardInterrupt when the shared Forge stop marker exists."""
    if is_shutdown_requested():
        mark_shutdown_requested()
        raise KeyboardInterrupt


def is_interrupt_exit_code(returncode: int | None) -> bool:
    """Return True for process return codes that conventionally mean Ctrl+C."""
    return returncode in INTERRUPT_EXIT_CODES


def is_interrupt_exception(exc: BaseException) -> bool:
    """Return True when an exception represents a Ctrl+C interrupt."""
    if isinstance(exc, KeyboardInterrupt):
        return True
    if isinstance(exc, SystemExit) and isinstance(exc.code, int):
        return is_interrupt_exit_code(exc.code)
    return False


def _handle_sigint(_signum, _frame) -> None:
    mark_user_interrupt_requested(INTERRUPT_REASON_CTRL_C)
    raise KeyboardInterrupt


def validate_issue_processing_environment() -> None:
    """Validate environment required before issue processing can start."""
    require_graalvm_home_env(DEV_GRAALVM_ENV_VAR)
    require_graalvm_home_env(POST_GENERATION_GRAALVM_ENV_VAR)
    require_graalvm_home_env(LATEST_EA_GRAALVM_ENV_VAR)


def gh(
        *args: str,
        check: bool = True,
        input_text: str | None = None,
        cwd: str | None = None,
        quiet: bool = False,
        max_attempts: int = GITHUB_TRANSIENT_RETRY_ATTEMPTS,
) -> subprocess.CompletedProcess:
    """Run a gh CLI command and return the completed process."""
    if max_attempts < 1:
        raise ValueError("max_attempts must be at least 1")
    cmd = ["gh", *args]
    env = {**os.environ, "GH_PROMPT_DISABLED": "1", "GH_PAGER": ""}
    if not quiet:
        log_github_query(args)
    for attempt in range(1, max_attempts + 1):
        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            env=env,
            input=input_text,
            cwd=cwd,
        )
        if result.returncode == 0:
            return result

        error_text = "\n".join(part for part in (result.stderr, result.stdout) if part)
        if check and is_github_rate_limit_text(error_text):
            raise GitHubRateLimitExceeded("GitHub API rate limit exceeded")
        if attempt < max_attempts and is_github_transient_failure_text(error_text):
            _log_github_transient_retry(error_text, attempt, max_attempts, quiet)
            time.sleep(_github_retry_delay_seconds(attempt))
            continue
        if check:
            if not quiet:
                print(f"ERROR: {' '.join(cmd)}\n{result.stderr}", file=sys.stderr)
            result.check_returncode()
        return result

    raise RuntimeError("GitHub command exhausted retries")


def gh_json(*args: str, quiet: bool = False) -> any:
    """Run a gh CLI command and parse its stdout as JSON."""
    return run_github_json_with_retries(gh, args, quiet=quiet)


def format_github_exception_details(exc: Exception) -> str:
    """Return concise GitHub command failure details without embedding long commands."""
    if isinstance(exc, subprocess.CalledProcessError):
        output = "\n".join(
            text.strip()
            for text in (exc.stderr, exc.stdout)
            if isinstance(text, str) and text.strip()
        )
        if output:
            return output
        return f"gh command exited with code {exc.returncode}"
    return repr(exc)


PIPELINE_LABELS = {LABEL_LIBRARY_NEW, LABEL_LIBRARY_UPDATE, LABEL_JAVAC_FAIL, LABEL_JAVA_RUN_FAIL, LABEL_NI_RUN_FAIL}


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
    if is_fixture_testing_enabled():
        return require_fixture_github_state().get_issue_claim_payload(issue_number)
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
    return {
        "number": item["number"],
        "title": item.get("title", ""),
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
) -> list[dict]:
    """Fetch open issues that carry the given label using GitHub search pagination."""
    if limit <= 0:
        return []
    if is_fixture_testing_enabled():
        return require_fixture_github_state().list_open_issues_by_label(
            label,
            limit=limit,
            offset=offset,
            extra_labels=extra_labels,
            excluded_labels=[LABEL_NOT_FOR_NATIVE_IMAGE],
        )
    if offset >= GITHUB_SEARCH_MAX_RESULTS:
        return []
    limit = min(limit, GITHUB_SEARCH_MAX_RESULTS - offset)

    per_page = GITHUB_API_MAX_PAGE_SIZE
    page = (offset // per_page) + 1
    page_offset = offset % per_page
    query = build_issue_search_query(label, extra_labels)
    items = get_issue_search_page(query, page, per_page)
    return items[page_offset:page_offset + limit]


def fetch_issue_search_page(query: str, page: int, per_page: int) -> list[dict]:
    """Fetch and normalize one GitHub search result page for issues."""
    data = gh_json(
        "api", "--method", "GET", "/search/issues",
        "-f", f"q={query}",
        "-f", "sort=updated",
        "-f", "order=desc",
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
    cache_key = build_issue_search_cache_key("page", query, "updated", "desc", page, per_page)
    now = time.time()
    cached_payload = _read_issue_search_cache_payload()
    if cached_payload is not None:
        cached_page = _get_cached_issue_search_page(cached_payload, cache_key, now, ttl_seconds)
        if cached_page is not None:
            return cached_page

    with LocalIssueSearchCacheWriterLock():
        now = time.time()
        payload = _read_issue_search_cache_payload_or_empty(now)
        cached_page = _get_cached_issue_search_page(payload, cache_key, now, ttl_seconds)
        if cached_page is not None:
            return cached_page

        issues = fetch_issue_search_page(query, page, per_page)
        _set_cached_issue_search_page(payload, cache_key, issues, now)
        _write_issue_search_cache_payload(payload, now)
        return issues


def count_issues_with_label(label: str, extra_labels: list[str] | None = None) -> int:
    """Return GitHub's count of open issues carrying the given label set."""
    if is_fixture_testing_enabled():
        return require_fixture_github_state().count_open_issues_by_label(
            label,
            extra_labels=extra_labels,
            excluded_labels=[LABEL_NOT_FOR_NATIVE_IMAGE],
        )
    query = build_issue_search_query(label, extra_labels)
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
    cached_payload = _read_issue_search_cache_payload()
    if cached_payload is not None:
        cached_count = _get_cached_issue_search_count(cached_payload, cache_key, now, ttl_seconds)
        if cached_count is not None:
            return cached_count

    with LocalIssueSearchCacheWriterLock():
        now = time.time()
        payload = _read_issue_search_cache_payload_or_empty(now)
        cached_count = _get_cached_issue_search_count(payload, cache_key, now, ttl_seconds)
        if cached_count is not None:
            return cached_count

        total_count = fetch_issue_search_count(query)
        _set_cached_issue_search_count(payload, cache_key, total_count, now)
        _write_issue_search_cache_payload(payload, now)
        return total_count


def get_issues_with_label(label: str, limit: int, offset: int = 0, extra_labels: list[str] | None = None) -> list[dict]:
    """Fetch open issues that carry the given label (and any extra labels)."""
    return search_issues_with_label(label, limit, offset, extra_labels)


def get_pull_requests_with_label(label: str, fetch_limit: int) -> list[dict]:
    """Fetch open pull requests that carry the given label."""
    return get_pull_requests_with_labels([label], fetch_limit)


def get_pull_requests_with_labels(labels: list[str], fetch_limit: int) -> list[dict]:
    """Fetch open pull requests that carry all given labels."""
    unique_labels = list(dict.fromkeys(labels))
    label_args: list[str] = []
    for label in unique_labels:
        label_args.extend(["--label", label])
    label_description = "', '".join(unique_labels)
    print(
        f"\n[Fetching open pull requests with label(s) '{label_description}' from {REPO} "
        f"(fetched={fetch_limit})]"
    )
    data = gh_json(
        "pr", "list",
        "--repo", REPO,
        *label_args,
        "--state", "open",
        "--limit", str(fetch_limit),
        "--json", "number,title,url,author,labels",
    )
    return data


def get_pull_request_status_check_rollup(pr_number: int) -> list[dict]:
    """Fetch status check details for one pull request."""
    data = gh_json(
        "pr", "view",
        str(pr_number),
        "--repo", REPO,
        "--json", "statusCheckRollup",
    )
    status_checks = data.get("statusCheckRollup")
    return status_checks if isinstance(status_checks, list) else []


def attach_pull_request_status_check_rollup(
        pull_request: dict,
        status_check_cache: dict[int, list[dict]],
) -> dict:
    """Return a pull request payload enriched with status check details."""
    pr_number = pull_request.get("number")
    if not isinstance(pr_number, int):
        print("ERROR: Missing pull request number while fetching status checks.", file=sys.stderr)
        raise RuntimeError("Missing pull request number while fetching status checks")

    if pr_number not in status_check_cache:
        status_check_cache[pr_number] = get_pull_request_status_check_rollup(pr_number)

    enriched_pull_request = dict(pull_request)
    enriched_pull_request["statusCheckRollup"] = status_check_cache[pr_number]
    return enriched_pull_request


def issue_has_label(issue: dict, label_name: str) -> bool:
    """Return True when the GitHub issue payload contains the given label."""
    for label in issue.get("labels", []):
        if isinstance(label, dict) and label.get("name") == label_name:
            return True
    return False


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


class LocalIssueClaimLock:
    """Non-blocking per-issue lock for scripts running as the same GitHub user."""

    def __init__(self, issue_number: int):
        self.issue_number = issue_number
        self.lock_path = get_issue_claim_lock_path(issue_number)
        self.lock_file = None
        self.fallback_lock_path = f"{self.lock_path}.exclusive"

    def acquire(self) -> bool:
        with held_issue_claim_lock_guard:
            if self.issue_number in held_issue_claim_lock_numbers:
                return False
            held_issue_claim_lock_numbers.add(self.issue_number)

        try:
            os.makedirs(os.path.dirname(self.lock_path), exist_ok=True)
            if fcntl is None:
                return self._acquire_exclusive_file_lock()
            return self._acquire_fcntl_lock()
        except Exception:
            self._forget_process_lock()
            raise

    def release(self) -> None:
        if self.lock_file is None:
            self._forget_process_lock()
            return

        try:
            if fcntl is not None:
                fcntl.flock(self.lock_file.fileno(), fcntl.LOCK_UN)
        finally:
            self.lock_file.close()
            self.lock_file = None
            if fcntl is None:
                if os.path.exists(self.fallback_lock_path):
                    os.unlink(self.fallback_lock_path)
            self._forget_process_lock()

    def _acquire_fcntl_lock(self) -> bool:
        self.lock_file = open(self.lock_path, "a+", encoding="utf-8")
        try:
            fcntl.flock(self.lock_file.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError as exc:
            self.lock_file.close()
            self.lock_file = None
            self._forget_process_lock()
            if exc.errno in (errno.EACCES, errno.EAGAIN):
                return False
            raise

        self._write_lock_owner()
        return True

    def _acquire_exclusive_file_lock(self) -> bool:
        try:
            fd = os.open(self.fallback_lock_path, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
        except FileExistsError:
            self._forget_process_lock()
            return False

        self.lock_file = os.fdopen(fd, "w", encoding="utf-8")
        self._write_lock_owner()
        return True

    def _write_lock_owner(self) -> None:
        self.lock_file.seek(0)
        self.lock_file.truncate()
        self.lock_file.write(f"pid={os.getpid()} issue={self.issue_number}\n")
        self.lock_file.flush()

    def _forget_process_lock(self) -> None:
        with held_issue_claim_lock_guard:
            held_issue_claim_lock_numbers.discard(self.issue_number)


class LocalIssueClaimCacheWriterLock:
    """Short-lived exclusive lock for atomic issue-claim cache updates."""

    def __init__(self):
        self.lock_path = get_issue_claim_cache_lock_path()
        self.lock_file = None
        self.fallback_lock_path = f"{self.lock_path}.exclusive"

    def __enter__(self):
        self.acquire()
        return self

    def __exit__(self, _exc_type, _exc, _traceback) -> None:
        self.release()

    def acquire(self) -> None:
        os.makedirs(os.path.dirname(self.lock_path), exist_ok=True)
        if fcntl is None:
            self._acquire_exclusive_file_lock()
            return
        self.lock_file = open(self.lock_path, "a+", encoding="utf-8")
        fcntl.flock(self.lock_file.fileno(), fcntl.LOCK_EX)

    def release(self) -> None:
        if self.lock_file is None:
            return
        try:
            if fcntl is not None:
                fcntl.flock(self.lock_file.fileno(), fcntl.LOCK_UN)
        finally:
            self.lock_file.close()
            self.lock_file = None
            if fcntl is None and os.path.exists(self.fallback_lock_path):
                os.unlink(self.fallback_lock_path)

    def _acquire_exclusive_file_lock(self) -> None:
        while True:
            try:
                fd = os.open(self.fallback_lock_path, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
                self.lock_file = os.fdopen(fd, "w", encoding="utf-8")
                return
            except FileExistsError:
                time.sleep(0.05)


class LocalIssueSearchCacheWriterLock:
    """Short-lived exclusive lock for shared issue-search cache updates."""

    def __init__(self):
        self.lock_path = get_issue_search_cache_lock_path()
        self.lock_file = None
        self.fallback_lock_path = f"{self.lock_path}.exclusive"

    def __enter__(self):
        self.acquire()
        return self

    def __exit__(self, _exc_type, _exc, _traceback) -> None:
        self.release()

    def acquire(self) -> None:
        os.makedirs(os.path.dirname(self.lock_path), exist_ok=True)
        if fcntl is None:
            self._acquire_exclusive_file_lock()
            return
        self.lock_file = open(self.lock_path, "a+", encoding="utf-8")
        fcntl.flock(self.lock_file.fileno(), fcntl.LOCK_EX)

    def release(self) -> None:
        if self.lock_file is None:
            return
        try:
            if fcntl is not None:
                fcntl.flock(self.lock_file.fileno(), fcntl.LOCK_UN)
        finally:
            self.lock_file.close()
            self.lock_file = None
            if fcntl is None and os.path.exists(self.fallback_lock_path):
                os.unlink(self.fallback_lock_path)

    def _acquire_exclusive_file_lock(self) -> None:
        while True:
            try:
                fd = os.open(self.fallback_lock_path, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
                self.lock_file = os.fdopen(fd, "w", encoding="utf-8")
                return
            except FileExistsError:
                time.sleep(0.05)


def get_issue_claim_locks_root() -> str:
    """Return the shared local directory for issue claim locks."""
    return os.path.join(
        tempfile.gettempdir(),
        ISSUE_CLAIM_LOCK_DIRNAME,
        quote(REPO, safe=""),
    )


def get_issue_claim_lock_path(issue_number: int) -> str:
    """Return the local lock file path for an issue number."""
    return os.path.join(get_issue_claim_locks_root(), f"issue-{issue_number}.lock")


def get_issue_claim_cache_path() -> str:
    """Return the shared local issue-claim cache path."""
    return os.path.join(get_issue_claim_locks_root(), ISSUE_CLAIM_CACHE_FILENAME)


def get_issue_claim_cache_lock_path() -> str:
    """Return the shared local issue-claim cache writer lock path."""
    return os.path.join(get_issue_claim_locks_root(), ISSUE_CLAIM_CACHE_LOCK_FILENAME)


def get_issue_search_cache_path() -> str:
    """Return the shared local issue-search cache path."""
    return os.path.join(get_issue_claim_locks_root(), ISSUE_SEARCH_CACHE_FILENAME)


def get_issue_search_cache_lock_path() -> str:
    """Return the shared local issue-search cache writer lock path."""
    return os.path.join(get_issue_claim_locks_root(), ISSUE_SEARCH_CACHE_LOCK_FILENAME)


def try_acquire_issue_claim_lock(issue_number: int) -> LocalIssueClaimLock | None:
    """Acquire a local per-issue claim lock, or return None if another local runner holds it."""
    claim_lock = LocalIssueClaimLock(issue_number)
    if claim_lock.acquire():
        return claim_lock
    return None


def is_issue_claim_cache_enabled() -> bool:
    """Return True when the shared local issue-claim cache is enabled."""
    if is_fixture_testing_enabled():
        return False
    return os.environ.get("FORGE_ISSUE_CLAIM_CACHE", "1") != "0"


def get_issue_claim_cache_ttl_seconds() -> int:
    """Return the issue-claim cache TTL in seconds."""
    raw_value = os.environ.get("FORGE_ISSUE_CLAIM_CACHE_TTL_SECONDS")
    if raw_value is None or raw_value == "":
        return DEFAULT_ISSUE_CLAIM_CACHE_TTL_SECONDS
    try:
        value = int(raw_value)
    except ValueError:
        print("ERROR: FORGE_ISSUE_CLAIM_CACHE_TTL_SECONDS must be a non-negative integer.", file=sys.stderr)
        sys.exit(1)
    if value < 0:
        print("ERROR: FORGE_ISSUE_CLAIM_CACHE_TTL_SECONDS must be a non-negative integer.", file=sys.stderr)
        sys.exit(1)
    return value


def _read_issue_claim_cache_payload() -> dict | None:
    cache_path = get_issue_claim_cache_path()
    try:
        with open(cache_path, "r", encoding="utf-8") as cache_file:
            payload = json.load(cache_file)
    except (FileNotFoundError, json.JSONDecodeError, OSError):
        return None
    if not isinstance(payload, dict):
        return None
    if payload.get("version") != ISSUE_CLAIM_CACHE_VERSION:
        return None
    if payload.get("repo") != REPO:
        return None
    if not isinstance(payload.get("entries"), dict):
        return None
    return payload


def _parse_issue_claim_cache_entry(
        issue_number: int,
        entry: dict,
        now: float,
        ttl_seconds: int,
) -> CachedIssueClaimSkip | None:
    if not isinstance(entry, dict):
        return None
    reason = entry.get("reason")
    if reason not in ISSUE_CLAIM_CACHE_REASONS:
        return None
    try:
        observed_at_epoch = float(entry.get("observed_at_epoch"))
    except (TypeError, ValueError):
        return None
    if now - observed_at_epoch > ttl_seconds:
        return None

    assignee_values = entry.get("assignees", [])
    if not isinstance(assignee_values, list):
        assignee_values = []
    assignees = tuple(
        assignee
        for assignee in assignee_values
        if isinstance(assignee, str) and assignee
    )
    blocker_values = entry.get("open_blockers", [])
    if not isinstance(blocker_values, list):
        blocker_values = []
    open_blockers = tuple(
        blocker
        for blocker in blocker_values
        if isinstance(blocker, int)
    )
    project_status = entry.get("project_status")
    if project_status is not None and not isinstance(project_status, str):
        project_status = None

    return CachedIssueClaimSkip(
        issue_number=issue_number,
        reason=reason,
        observed_at_epoch=observed_at_epoch,
        assignees=assignees,
        project_status=project_status,
        open_blockers=open_blockers,
    )


def read_issue_claim_cache(now: float | None = None, require_fresh_cache: bool = True) -> dict[int, CachedIssueClaimSkip]:
    """Read fresh issue-claim cache entries without taking a lock."""
    if not is_issue_claim_cache_enabled():
        return {}
    ttl_seconds = get_issue_claim_cache_ttl_seconds()
    if ttl_seconds <= 0:
        return {}
    now = time.time() if now is None else now
    payload = _read_issue_claim_cache_payload()
    if payload is None:
        return {}
    try:
        updated_at_epoch = float(payload.get("updated_at_epoch"))
    except (TypeError, ValueError):
        return {}
    if require_fresh_cache and now - updated_at_epoch > ttl_seconds:
        return {}

    cache: dict[int, CachedIssueClaimSkip] = {}
    for issue_number_text, entry in payload.get("entries", {}).items():
        try:
            issue_number = int(issue_number_text)
        except (TypeError, ValueError):
            continue
        cached_skip = _parse_issue_claim_cache_entry(issue_number, entry, now, ttl_seconds)
        if cached_skip is not None:
            cache[issue_number] = cached_skip
    return cache


def _write_issue_claim_cache_entries(entries: dict[int, CachedIssueClaimSkip], updated_at_epoch: float) -> None:
    cache_path = get_issue_claim_cache_path()
    os.makedirs(os.path.dirname(cache_path), exist_ok=True)
    payload = {
        "version": ISSUE_CLAIM_CACHE_VERSION,
        "repo": REPO,
        "updated_at_epoch": updated_at_epoch,
        "entries": {
            str(issue_number): {
                "observed_at_epoch": cached_skip.observed_at_epoch,
                "reason": cached_skip.reason,
                "assignees": list(cached_skip.assignees),
                "project_status": cached_skip.project_status,
                "open_blockers": list(cached_skip.open_blockers),
            }
            for issue_number, cached_skip in sorted(entries.items())
        },
    }
    temp_path = f"{cache_path}.{os.getpid()}.{uuid.uuid4().hex}.tmp"
    try:
        with open(temp_path, "w", encoding="utf-8") as cache_file:
            json.dump(payload, cache_file, sort_keys=True)
            cache_file.write("\n")
            cache_file.flush()
            os.fsync(cache_file.fileno())
        os.replace(temp_path, cache_path)
    finally:
        if os.path.exists(temp_path):
            os.unlink(temp_path)


def record_issue_claim_cache_observations(
        observations: list[IssueClaimCacheObservation],
        now: float | None = None,
) -> None:
    """Record negative issue-claim observations in the shared local cache."""
    observations = [
        observation
        for observation in observations
        if observation.reason in ISSUE_CLAIM_CACHE_REASONS
    ]
    if not observations or not is_issue_claim_cache_enabled() or get_issue_claim_cache_ttl_seconds() <= 0:
        return
    now = time.time() if now is None else now
    with LocalIssueClaimCacheWriterLock():
        cache = read_issue_claim_cache(now, require_fresh_cache=False)
        for observation in observations:
            cache[observation.issue_number] = CachedIssueClaimSkip(
                issue_number=observation.issue_number,
                reason=observation.reason,
                observed_at_epoch=now,
                assignees=observation.assignees,
                project_status=observation.project_status,
                open_blockers=observation.open_blockers,
            )
        _write_issue_claim_cache_entries(cache, now)


def invalidate_issue_claim_cache_entry(issue_number: int, now: float | None = None) -> None:
    """Remove one issue from the shared local issue-claim cache."""
    if not is_issue_claim_cache_enabled() or get_issue_claim_cache_ttl_seconds() <= 0:
        return
    now = time.time() if now is None else now
    with LocalIssueClaimCacheWriterLock():
        cache = read_issue_claim_cache(now, require_fresh_cache=False)
        if issue_number not in cache:
            return
        cache.pop(issue_number, None)
        _write_issue_claim_cache_entries(cache, now)


def _remove_file_if_exists(path: str) -> bool:
    try:
        os.unlink(path)
        return True
    except FileNotFoundError:
        return False


def clear_issue_claim_cache() -> bool:
    """Delete the shared local issue-claim cache file."""
    with LocalIssueClaimCacheWriterLock():
        return _remove_file_if_exists(get_issue_claim_cache_path())


def is_issue_search_cache_enabled() -> bool:
    """Return True when the shared local issue-search cache is enabled."""
    return os.environ.get("FORGE_ISSUE_SEARCH_CACHE", "1") != "0"


def get_issue_search_cache_ttl_seconds() -> int:
    """Return the issue-search cache TTL in seconds."""
    raw_value = os.environ.get("FORGE_ISSUE_SEARCH_CACHE_TTL_SECONDS")
    if raw_value is None or raw_value == "":
        return DEFAULT_ISSUE_SEARCH_CACHE_TTL_SECONDS
    try:
        value = int(raw_value)
    except ValueError:
        print("ERROR: FORGE_ISSUE_SEARCH_CACHE_TTL_SECONDS must be a non-negative integer.", file=sys.stderr)
        sys.exit(1)
    if value < 0:
        print("ERROR: FORGE_ISSUE_SEARCH_CACHE_TTL_SECONDS must be a non-negative integer.", file=sys.stderr)
        sys.exit(1)
    return value


def _read_issue_search_cache_payload() -> dict | None:
    cache_path = get_issue_search_cache_path()
    try:
        with open(cache_path, "r", encoding="utf-8") as cache_file:
            payload = json.load(cache_file)
    except (FileNotFoundError, json.JSONDecodeError, OSError):
        return None
    if not isinstance(payload, dict):
        return None
    if payload.get("version") != ISSUE_SEARCH_CACHE_VERSION:
        return None
    if payload.get("repo") != REPO:
        return None
    if not isinstance(payload.get("pages"), dict):
        return None
    if not isinstance(payload.get("counts"), dict):
        return None
    return payload


def _empty_issue_search_cache_payload(now: float) -> dict:
    return {
        "version": ISSUE_SEARCH_CACHE_VERSION,
        "repo": REPO,
        "updated_at_epoch": now,
        "pages": {},
        "counts": {},
    }


def _read_issue_search_cache_payload_or_empty(now: float) -> dict:
    return _read_issue_search_cache_payload() or _empty_issue_search_cache_payload(now)


def _write_issue_search_cache_payload(payload: dict, updated_at_epoch: float) -> None:
    cache_path = get_issue_search_cache_path()
    os.makedirs(os.path.dirname(cache_path), exist_ok=True)
    payload["version"] = ISSUE_SEARCH_CACHE_VERSION
    payload["repo"] = REPO
    payload["updated_at_epoch"] = updated_at_epoch
    payload.setdefault("pages", {})
    payload.setdefault("counts", {})

    temp_path = f"{cache_path}.{os.getpid()}.{uuid.uuid4().hex}.tmp"
    try:
        with open(temp_path, "w", encoding="utf-8") as cache_file:
            json.dump(payload, cache_file, sort_keys=True)
            cache_file.write("\n")
            cache_file.flush()
            os.fsync(cache_file.fileno())
        os.replace(temp_path, cache_path)
    finally:
        if os.path.exists(temp_path):
            os.unlink(temp_path)


def clear_issue_search_cache() -> bool:
    """Delete the shared local issue-search cache file."""
    with LocalIssueSearchCacheWriterLock():
        return _remove_file_if_exists(get_issue_search_cache_path())


def clear_issue_caches() -> None:
    """Delete local issue queue caches used by work-queue scanning."""
    removed_claim_cache = clear_issue_claim_cache()
    removed_search_cache = clear_issue_search_cache()
    print()
    log_stage(
        "issue-cache",
        f"Cleared issue claim cache at {get_issue_claim_cache_path()} "
        f"({'removed' if removed_claim_cache else 'not present'})",
    )
    log_stage(
        "issue-cache",
        f"Cleared issue search cache at {get_issue_search_cache_path()} "
        f"({'removed' if removed_search_cache else 'not present'})",
    )


def build_issue_search_cache_key(*parts: object) -> str:
    """Return a stable compact cache key for a GitHub issue-search request."""
    key_payload = json.dumps(parts, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(key_payload.encode("utf-8")).hexdigest()


def _is_fresh_issue_search_entry(entry: dict, now: float, ttl_seconds: int) -> bool:
    try:
        observed_at_epoch = float(entry.get("observed_at_epoch"))
    except (TypeError, ValueError):
        return False
    return now - observed_at_epoch <= ttl_seconds


def _get_cached_issue_search_page(
        payload: dict,
        cache_key: str,
        now: float,
        ttl_seconds: int,
) -> list[dict] | None:
    entry = payload.get("pages", {}).get(cache_key)
    if not isinstance(entry, dict) or not _is_fresh_issue_search_entry(entry, now, ttl_seconds):
        return None
    issues = entry.get("issues")
    if not isinstance(issues, list):
        return None
    return [
        issue
        for issue in issues
        if isinstance(issue, dict) and isinstance(issue.get("number"), int)
    ]


def _set_cached_issue_search_page(payload: dict, cache_key: str, issues: list[dict], now: float) -> None:
    payload.setdefault("pages", {})[cache_key] = {
        "observed_at_epoch": now,
        "issues": issues,
    }


def _get_cached_issue_search_count(
        payload: dict,
        cache_key: str,
        now: float,
        ttl_seconds: int,
) -> int | None:
    entry = payload.get("counts", {}).get(cache_key)
    if not isinstance(entry, dict) or not _is_fresh_issue_search_entry(entry, now, ttl_seconds):
        return None
    total_count = entry.get("total_count")
    if not isinstance(total_count, int):
        return None
    return total_count


def _set_cached_issue_search_count(payload: dict, cache_key: str, total_count: int, now: float) -> None:
    payload.setdefault("counts", {})[cache_key] = {
        "observed_at_epoch": now,
        "total_count": total_count,
    }


def pull_request_has_label(pr: dict, label_name: str) -> bool:
    """Return True when the GitHub pull request payload contains the given label."""
    return issue_has_label(pr, label_name)


def get_open_issues_blocked_by_issue_counts(
        issue_numbers: list[int],
        minimum_count: int = PRIORITY_BLOCKING_LIBRARY_THRESHOLD,
        chunk_size: int = PRIORITY_BLOCKING_ISSUE_QUERY_CHUNK_SIZE,
) -> dict[int, int]:
    """Return open issue counts blocked by each issue, capped after `minimum_count`."""
    if minimum_count <= 0 or chunk_size <= 0:
        return {}

    if is_fixture_testing_enabled():
        fixture_state = require_fixture_github_state()
        unique_issue_numbers = list(dict.fromkeys(issue_numbers))
        counts = {issue_number: 0 for issue_number in unique_issue_numbers}
        counted_issue_numbers = set(unique_issue_numbers)
        for fixture_issue_number in fixture_state.issue_numbers:
            issue_payload = fixture_state.get_issue_claim_payload(fixture_issue_number)
            if str(issue_payload.get("state", "")).upper() != "OPEN":
                continue
            blocker_issue_numbers = fixture_state.get_open_blocking_issue_numbers(fixture_issue_number)
            for blocker_issue_number in blocker_issue_numbers:
                if blocker_issue_number not in counted_issue_numbers:
                    continue
                if counts[blocker_issue_number] < minimum_count:
                    counts[blocker_issue_number] += 1
        return counts

    owner, repo_name = REPO.split("/")
    unique_issue_numbers = list(dict.fromkeys(issue_numbers))
    counts = {issue_number: 0 for issue_number in unique_issue_numbers}
    cursors: dict[int, str | None] = {issue_number: None for issue_number in unique_issue_numbers}
    remaining_issue_numbers = set(unique_issue_numbers)

    while remaining_issue_numbers:
        batch = list(remaining_issue_numbers)[:chunk_size]
        issue_fields = "\n".join(
            f"""
        issue_{issue_number}: issue(number: {issue_number}) {{
          blocking(first: 100{f', after: "{cursors[issue_number]}"' if cursors[issue_number] else ""}) {{
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
            issue = repository.get(f"issue_{issue_number}")
            blocking = issue.get("blocking", {}) if isinstance(issue, dict) else {}
            for blocked_issue in blocking.get("nodes", []):
                if isinstance(blocked_issue, dict) and not blocked_issue.get("closed", False):
                    counts[issue_number] += 1
                    if counts[issue_number] >= minimum_count:
                        break

            page_info = blocking.get("pageInfo", {}) if isinstance(blocking, dict) else {}
            if counts[issue_number] >= minimum_count or not page_info.get("hasNextPage"):
                remaining_issue_numbers.discard(issue_number)
                continue

            cursor = page_info.get("endCursor")
            if cursor:
                cursors[issue_number] = cursor
            else:
                remaining_issue_numbers.discard(issue_number)

    return counts


def mark_issue_numbers_blocking_many_libraries_as_priority(
        issue_numbers: list[int],
        threshold: int = PRIORITY_BLOCKING_LIBRARY_THRESHOLD,
) -> set[int]:
    """Apply the priority label to issues that block at least `threshold` open issues."""
    priority_issue_numbers: set[int] = set()
    if not issue_numbers:
        return priority_issue_numbers

    counts = get_open_issues_blocked_by_issue_counts(issue_numbers, threshold)
    for issue_number in issue_numbers:
        if counts.get(issue_number, 0) < threshold or issue_number in priority_issue_numbers:
            continue
        add_issue_label(issue_number, LABEL_PRIORITY)
        priority_issue_numbers.add(issue_number)
        log_stage(
            "priority",
            (
                f"Issue #{issue_number} blocks at least {threshold} open issue(s); "
                f"added label '{LABEL_PRIORITY}'"
            ),
        )
    return priority_issue_numbers


def mark_issues_blocking_many_libraries_as_priority(
        issues: list[dict],
        threshold: int = PRIORITY_BLOCKING_LIBRARY_THRESHOLD,
) -> None:
    """Mark unprioritized issue payloads as priority when they block many open issues."""
    candidate_issue_numbers = [
        issue["number"]
        for issue in issues
        if isinstance(issue, dict)
        and isinstance(issue.get("number"), int)
        and not issue_has_label(issue, LABEL_PRIORITY)
    ]
    priority_issue_numbers = mark_issue_numbers_blocking_many_libraries_as_priority(
        candidate_issue_numbers,
        threshold,
    )
    for issue in issues:
        issue_number = issue.get("number") if isinstance(issue, dict) else None
        if issue_number in priority_issue_numbers:
            add_issue_label_to_payload(issue, LABEL_PRIORITY)


def try_mark_issues_blocking_many_libraries_as_priority(issues: list[dict]) -> None:
    """Best-effort priority labeling for queue ordering."""
    try:
        mark_issues_blocking_many_libraries_as_priority(issues)
    except GitHubRateLimitExceeded:
        raise
    except Exception as exc:
        print(
            "ERROR: Failed to mark blocker issues as priority; "
            f"continuing without dependency priority updates: {format_github_exception_details(exc)}",
            file=sys.stderr,
        )


def try_mark_issue_numbers_blocking_many_libraries_as_priority(issue_numbers: list[int]) -> None:
    """Best-effort priority labeling for blockers discovered during claim checks."""
    try:
        mark_issue_numbers_blocking_many_libraries_as_priority(issue_numbers)
    except GitHubRateLimitExceeded:
        raise
    except Exception as exc:
        print(
            "ERROR: Failed to mark blocker issues as priority; "
            f"continuing without dependency priority updates: {format_github_exception_details(exc)}",
            file=sys.stderr,
        )


def get_prioritized_issues_with_label(
        label: str,
        limit: int,
        priority_offset: int = 0,
        regular_offset: int = 0,
        priority_exhausted: bool = False,
) -> tuple[list[dict], int, int, bool, bool]:
    """Fetch one issue batch and return priority issues first within that batch."""
    regular_issues = get_issues_with_label(label, limit, regular_offset)
    regular_offset += len(regular_issues)
    try_mark_issues_blocking_many_libraries_as_priority(regular_issues)
    sorted_issues = sorted(
        regular_issues,
        key=lambda issue: not issue_has_label(issue, LABEL_PRIORITY),
    )
    exhausted = len(regular_issues) == 0
    return sorted_issues, priority_offset, regular_offset, True, exhausted


def get_project_item_state(issue_number: int) -> tuple[str | None, str | None]:
    """
    Fetch the project item ID and current Status field value for an issue via GraphQL.
    Returns (item ID, status option name), or (None, None) if not found.
    """
    if is_fixture_testing_enabled():
        item_id, status = require_fixture_github_state().get_project_item_state(issue_number)
    else:
        item_id, status = get_issue_project_item_status(
            REPO,
            PROJECT_NUMBER,
            issue_number,
            STATUS_FIELD_NAME,
        )
    if item_id:
        print()
        status_text = status if status is not None else "unknown"
        log_stage(
            "project-item",
            (
                f"Issue #{issue_number} is linked to GitHub project item {item_id} "
                f"in project {PROJECT_NUMBER} with Status '{status_text}'"
            ),
        )
    return item_id, status


def get_project_item_id(issue_number: int):
    """
    Fetch the project item ID for a given issue number via GraphQL.
    Looks for the item linked to PROJECT_NUMBER.
    Returns the item ID (PVTI_...) or None if not found.
    """
    item_id, _ = get_project_item_state(issue_number)
    return item_id


def get_item_status(item_id: str):
    """
    Query the current Status field value of a project item via GraphQL.
    Returns the status option name (e.g. "Todo") or None.
    """
    if is_fixture_testing_enabled():
        return require_fixture_github_state().get_item_status(item_id)
    query = """
    query($item: ID!) {
      node(id: $item) {
        ... on ProjectV2Item {
          fieldValues(first: 5) {
            nodes {
              ... on ProjectV2ItemFieldSingleSelectValue {
                name
                field { ... on ProjectV2FieldCommon { name } }
              }
            }
          }
        }
      }
    }
    """
    result = gh_json(
        "api", "graphql",
        "-f", f"query={query}",
        "-f", f"item={item_id}",
    )
    nodes = (
        result.get("data", {})
        .get("node", {})
        .get("fieldValues", {})
        .get("nodes", [])
    )
    for node in nodes:
        if node.get("field", {}).get("name") == STATUS_FIELD_NAME:
            return node.get("name")
    return None


def get_project_field_info() -> tuple[str, str, dict[str, str]]:
    """
    Fetch the project node ID, Status field ID, and option name->ID mapping.
    Returns (project_node_id, field_id, {option_name: option_id}).
    """
    owner, _ = REPO.split("/")
    query = f"""
    query {{
      organization(login: "{owner}") {{
        projectV2(number: {PROJECT_NUMBER}) {{
          id
          fields(first: 5) {{
            nodes {{
              ... on ProjectV2SingleSelectField {{
                id
                name
                options {{
                  id
                  name
                }}
              }}
            }}
          }}
        }}
      }}
    }}
    """
    result = gh_json("api", "graphql", "-f", f"query={query}")
    project = (
        result.get("data", {})
        .get("organization", {})
        .get("projectV2", {})
    )
    project_node_id = project.get("id")
    fields = project.get("fields", {}).get("nodes", [])

    for field in fields:
        if field.get("name") == STATUS_FIELD_NAME:
            field_id = field["id"]
            options = {opt["name"]: opt["id"] for opt in field["options"]}
            return project_node_id, field_id, options

    print(
        f"ERROR: Could not find field '{STATUS_FIELD_NAME}' in project {PROJECT_NUMBER}",
        file=sys.stderr,
    )
    sys.exit(2)

project_node_id: Optional[str] = None
field_id: Optional[str] = None
option_ids: Optional[dict[str, str]] = None
ensured_issue_labels: set[str] = set()
held_issue_claim_lock_numbers: set[int] = set()
held_issue_claim_lock_guard = threading.Lock()
preservation_failed_worktree_paths: set[str] = set()
fixture_github_state: FixtureGitHubState | None = None


CLAIM_BACKOFF_MIN = 5  # Minimum seconds to wait before verifying claim
CLAIM_BACKOFF_MAX = 10  # Maximum seconds to wait before verifying claim


def configure_fixture_testing(
        fixture_paths: list[str] | None = None,
        fixture_state: FixtureGitHubState | None = None,
) -> FixtureGitHubState:
    """Configure the dispatcher to use local fixture GitHub state."""
    global fixture_github_state
    if fixture_state is not None and fixture_paths is not None:
        raise ValueError("Pass either fixture_paths or fixture_state, not both")
    fixture_github_state = fixture_state or load_fixture_github_state(fixture_paths)
    return fixture_github_state


def is_fixture_testing_enabled() -> bool:
    """Return True when GitHub helper calls should use fixture state."""
    return fixture_github_state is not None


def require_fixture_github_state() -> FixtureGitHubState:
    """Return configured fixture GitHub state or fail with a dispatcher error."""
    if fixture_github_state is None:
        raise RuntimeError("Fixture GitHub state is not configured")
    return fixture_github_state


def claim_backoff_seconds() -> float:
    """Return the claim verification delay for live or fixture GitHub state."""
    if is_fixture_testing_enabled():
        return 0.0
    return random.uniform(CLAIM_BACKOFF_MIN, CLAIM_BACKOFF_MAX)


def get_authenticated_user() -> str:
    """Return the GitHub username of the currently authenticated gh user."""
    if is_fixture_testing_enabled():
        return FIXTURE_AUTHENTICATED_USER
    result = run_github_with_retries(gh, ("api", "user", "--jq", ".login"))
    return result.stdout.strip()


def resolve_authenticated_user(authenticated_user: str | None = None) -> str:
    """Resolve and log the authenticated GitHub username when remote work needs it."""
    if authenticated_user is not None:
        return authenticated_user
    if is_fixture_testing_enabled():
        print()
        log_stage("github-auth", f"Fixture authenticated as: {FIXTURE_AUTHENTICATED_USER}")
        return FIXTURE_AUTHENTICATED_USER
    ensure_gh_authenticated()
    resolved_user = get_authenticated_user()
    print()
    log_stage("github-auth", f"Authenticated as: {resolved_user}")
    return resolved_user


def is_authored_by_user(pr: dict, username: str) -> bool:
    """Return True when the GitHub pull request author matches the authenticated user."""
    author = pr.get("author")
    if not isinstance(author, dict):
        return False
    return author.get("login") == username


def has_completed_ci_tasks(pr: dict) -> bool:
    """Return True when the pull request has CI checks and none are still pending."""
    status_checks = pr.get("statusCheckRollup")
    if not isinstance(status_checks, list) or not status_checks:
        return False

    for status_check in status_checks:
        if not isinstance(status_check, dict):
            return False
        if status_check.get("status") != "COMPLETED":
            return False

    return True


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
          headRefOid
          reviewDecision
          mergeStateStatus
          mergeable
          isMergeQueueEnabled
          statusCheckRollup {{
            state
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


def has_passing_pull_request_gates(pr: dict) -> bool:
    """Return True when the pull request is mergeable and all status gates are green."""
    status_check_rollup = pr.get("statusCheckRollup")
    ci_state = status_check_rollup.get("state") if isinstance(status_check_rollup, dict) else None
    return (
        pr.get("mergeable") == "MERGEABLE"
        and pr.get("mergeStateStatus") == "CLEAN"
        and ci_state == "SUCCESS"
    )


def has_failed_pull_request_ci(pr: dict) -> bool:
    """Return True when the pull request's combined CI status is failed."""
    status_check_rollup = pr.get("statusCheckRollup")
    ci_state = status_check_rollup.get("state") if isinstance(status_check_rollup, dict) else None
    return ci_state in FAILED_CI_STATES


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


def get_rerunnable_failed_workflow_run_ids(workflow_runs: list[dict]) -> list[int]:
    """Return failed GitHub Actions run IDs below the automated rerun limit."""
    run_ids: list[int] = []
    for workflow_run in workflow_runs:
        if not isinstance(workflow_run, dict):
            continue
        run_id = workflow_run.get("id")
        run_attempt = workflow_run.get("run_attempt")
        conclusion = workflow_run.get("conclusion")
        if (
                isinstance(run_id, int)
                and isinstance(run_attempt, int)
                and run_attempt < MAX_AUTOMATED_WORKFLOW_RERUN_ATTEMPTS
                and conclusion in RERUNNABLE_WORKFLOW_RUN_CONCLUSIONS
        ):
            run_ids.append(run_id)
    return run_ids


def rerun_failed_pull_request_workflow_jobs(pr_number: int, head_sha: str) -> int:
    """Rerun failed GitHub Actions jobs for the current pull request head SHA."""
    workflow_runs = get_pull_request_workflow_runs(head_sha)
    run_ids = get_rerunnable_failed_workflow_run_ids(workflow_runs)
    if not run_ids:
        print(
            f"[No failed GitHub Actions workflow runs eligible for rerun on PR #{pr_number} "
            f"at head {head_sha}.]"
        )
        return 0

    for run_id in run_ids:
        print(f"[Rerunning failed GitHub Actions jobs for PR #{pr_number}, workflow run {run_id}.]")
        gh(
            "api",
            "--method",
            "POST",
            f"/repos/{REPO}/actions/runs/{run_id}/rerun-failed-jobs",
        )

    return len(run_ids)


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
        run_checked_command(
            ["git", "fetch", "--quiet", "origin", f"refs/pull/{pr_number}/head"],
            validation_worktree_path,
            f"Failed to fetch PR #{pr_number} head for final index validation",
        )
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


def merge_pull_request(pr: dict, reachability_metadata_path: str | None = None) -> None:
    """Merge a pull request using the repository's configured merge method."""
    pr_number = pr.get("number")
    head_ref_oid = pr.get("headRefOid")
    pr_url = pr.get("url") or f"https://github.com/{REPO}/pull/{pr_number}"
    if not isinstance(pr_number, int) or not isinstance(head_ref_oid, str) or not head_ref_oid:
        print(f"ERROR: Missing merge metadata for pull request #{pr_number}.", file=sys.stderr)
        raise RuntimeError(f"Missing merge metadata for pull request #{pr_number}")
    if reachability_metadata_path is None:
        reachability_metadata_path = get_repo_root()

    validate_pull_request_indexes_before_merge(pr_number, head_ref_oid, reachability_metadata_path)

    merge_args = [
        "pr",
        "merge",
        str(pr_number),
        "--repo",
        REPO,
        "--match-head-commit",
        head_ref_oid,
    ]
    if pr.get("isMergeQueueEnabled"):
        merge_args.append("--auto")
    else:
        merge_args.append(resolve_pull_request_merge_flag(pr))

    print(f"[Merging PR #{pr_number}: {pr_url}]")
    gh(*merge_args)


def reconcile_reviewed_pull_request(
        pr_number: int,
        reachability_metadata_path: str | None = None,
) -> bool:
    """Apply post-review PR follow-up actions based on the latest review state."""
    try:
        pr = get_pull_request_state(pr_number)
        review_decision = pr.get("reviewDecision")
        pr_url = pr.get("url") or f"https://github.com/{REPO}/pull/{pr_number}"

        print(
            f"[Post-review state for PR #{pr_number}: "
            f"decision={review_decision}, "
            f"mergeable={pr.get('mergeable')}, "
            f"mergeStateStatus={pr.get('mergeStateStatus')}, "
            f"ci={((pr.get('statusCheckRollup') or {}).get('state'))}]"
        )

        if review_decision == "CHANGES_REQUESTED":
            add_pull_request_label(pr_number, LABEL_HUMAN_INTERVENTION)
            print(f"[Added label '{LABEL_HUMAN_INTERVENTION}' to PR #{pr_number}: {pr_url}]")
            return True

        if review_decision != "APPROVED":
            print(f"[Skipping merge for PR #{pr_number}: review decision is '{review_decision}'.]")
            return True

        if has_failed_pull_request_ci(pr):
            head_sha = pr.get("headRefOid")
            if not isinstance(head_sha, str) or not head_sha:
                print(f"ERROR: Missing head SHA for approved PR #{pr_number} with failed CI.", file=sys.stderr)
                raise RuntimeError(f"Missing head SHA for approved PR #{pr_number} with failed CI")
            rerun_count = rerun_failed_pull_request_workflow_jobs(pr_number, head_sha)
            if rerun_count:
                print(
                    f"[Reran failed GitHub Actions job(s) in {rerun_count} workflow run(s) "
                    f"for approved PR #{pr_number}; skipping merge for this pass.]"
                )
            else:
                print(
                    f"[Skipping merge for approved PR #{pr_number}: CI failed, but no eligible "
                    "GitHub Actions workflow runs were found to rerun.]"
                )
            return True

        if not has_passing_pull_request_gates(pr):
            print(f"[Skipping merge for PR #{pr_number}: merge gates are not fully passing yet.]")
            return True

        merge_pull_request(pr, reachability_metadata_path)
        return True
    except Exception as exc:
        print(
            f"ERROR: Failed post-review follow-up for PR #{pr_number}: {exc!r}",
            file=sys.stderr,
        )
        return False


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
        and has_completed_ci_tasks(pull_request)
    )


def select_ci_complete_review_pull_requests(
        pull_requests: list[dict],
        authenticated_user: str,
        limit: int,
        status_check_cache: dict[int, list[dict]],
        excluded_labels: tuple[str, ...] = (),
) -> tuple[list[dict], int]:
    """Select review candidates, fetching CI details only after cheap filters pass."""
    selected_pull_requests: list[dict] = []
    incomplete_ci_count = 0

    for pull_request in pull_requests:
        if len(selected_pull_requests) >= limit:
            break
        if not is_review_pull_request_base_eligible(pull_request, authenticated_user, excluded_labels):
            continue

        enriched_pull_request = attach_pull_request_status_check_rollup(
            pull_request,
            status_check_cache,
        )
        if has_completed_ci_tasks(enriched_pull_request):
            selected_pull_requests.append(enriched_pull_request)
        else:
            incomplete_ci_count += 1

    return selected_pull_requests, incomplete_ci_count


def get_review_log_path(pr_number: int, coordinates: str | None = None) -> str:
    """Return the Codex review log path for the target pull request."""
    return build_task_log_path("pr-review", coordinates, f"codex_pr_review_{pr_number}.log")


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


def get_pull_request_discussion(pr_number: int) -> dict:
    """Fetch issue comments and submitted reviews for the target pull request."""
    return gh_json(
        "pr",
        "view",
        str(pr_number),
        "--repo",
        REPO,
        "--json",
        "comments,reviews",
    )


def format_structured_body(body: str | None) -> str:
    """Format a multiline comment body for terminal output."""
    normalized = (body or "").strip()
    if not normalized:
        return "  Body: <empty>"
    return "  Body:\n" + "\n".join(f"    {line}" for line in normalized.splitlines())


def print_pull_request_discussion(pr_number: int) -> None:
    """Print current pull request comments and reviews in a structured format."""
    try:
        discussion = get_pull_request_discussion(pr_number)
    except Exception as exc:
        print(
            f"ERROR: Failed to fetch comments for pull request #{pr_number}: {exc}",
            file=sys.stderr,
        )
        return

    comments = discussion.get("comments")
    reviews = discussion.get("reviews")
    comment_list = comments if isinstance(comments, list) else []
    review_list = reviews if isinstance(reviews, list) else []

    print(f"[PR discussion for #{pr_number}]")
    print(f"  Issue comments: {len(comment_list)}")
    if comment_list:
        for index, comment in enumerate(comment_list, start=1):
            author = comment.get("author", {}) if isinstance(comment, dict) else {}
            author_login = author.get("login", "unknown") if isinstance(author, dict) else "unknown"
            created_at = comment.get("createdAt", "unknown") if isinstance(comment, dict) else "unknown"
            body = comment.get("body", "") if isinstance(comment, dict) else ""
            print(f"  Comment {index}:")
            print(f"    Author: {author_login}")
            print(f"    Created: {created_at}")
            print(format_structured_body(body))
    else:
        print("  Comment details: none")

    print(f"  Reviews: {len(review_list)}")
    if review_list:
        for index, review in enumerate(review_list, start=1):
            author = review.get("author", {}) if isinstance(review, dict) else {}
            author_login = author.get("login", "unknown") if isinstance(author, dict) else "unknown"
            submitted_at = review.get("submittedAt", "unknown") if isinstance(review, dict) else "unknown"
            state = review.get("state", "unknown") if isinstance(review, dict) else "unknown"
            body = review.get("body", "") if isinstance(review, dict) else ""
            print(f"  Review {index}:")
            print(f"    Author: {author_login}")
            print(f"    State: {state}")
            print(f"    Submitted: {submitted_at}")
            print(format_structured_body(body))
    else:
        print("  Review details: none")


def review_pull_request(
        pr_number: int,
        reachability_metadata_path: str,
        review_model: str,
        pr_url: str | None = None,
        coordinates: str | None = None,
) -> bool:
    """Run a Codex review for the specified pull request number in the target repository."""
    if pr_url is None:
        pr_url = get_pull_request_url(pr_number)
    review_worktree_path = create_review_workspace(reachability_metadata_path, pr_number)
    prompt = build_review_prompt(pr_number)
    cmd = [
        "codex", "exec",
        "--dangerously-bypass-approvals-and-sandbox",
        "--json",
        "-c", 'reasoning.effort="medium"',
        "-m", review_model,
        prompt,
    ]
    log_path = get_review_log_path(pr_number, coordinates)
    log_path_display = display_log_path(log_path)
    print(f"\n[Reviewing PR #{pr_number} with Codex in an isolated worktree and submitting the review to GitHub.]")
    print(f"[PR link: {pr_url}]")
    print(f"[Codex review log: {log_path_display}]")
    try:
        with open(log_path, "w", encoding="utf-8") as log_file:
            result = subprocess.run(
                cmd,
                cwd=review_worktree_path,
                stdout=log_file,
                stderr=subprocess.STDOUT,
                text=True,
                timeout=CODEX_REVIEW_TIMEOUT_SECONDS,
                check=False,
            )
    except subprocess.TimeoutExpired:
        print(
            (
                f"ERROR: Codex PR review timed out after {CODEX_REVIEW_TIMEOUT_SECONDS} seconds "
                f"for PR #{pr_number}. See {log_path_display}."
            ),
            file=sys.stderr,
        )
        cleanup_review_workspace(reachability_metadata_path, review_worktree_path, pr_number)
        return False

    try:
        final_findings = extract_codex_final_message(log_path)
        if result.returncode != 0:
            output_tail = read_log_tail(log_path)
            print(
                (
                    f"ERROR: Codex PR review failed for PR #{pr_number} with exit code {result.returncode}. "
                    f"PR: {pr_url}. Log: {log_path_display}."
                    + (f"\n{output_tail}" if output_tail else "")
                ),
                file=sys.stderr,
            )
            if final_findings:
                print(f"[Final findings for PR #{pr_number}]\n{final_findings}")
            return False

        print(f"[Finished review for PR #{pr_number}: {pr_url}]")
        if final_findings:
            print(f"[Final findings for PR #{pr_number}]\n{final_findings}")
        else:
            print(f"[Final findings for PR #{pr_number}: unavailable in {log_path_display}]")
        print_pull_request_discussion(pr_number)
        return True
    finally:
        cleanup_review_workspace(reachability_metadata_path, review_worktree_path, pr_number)


def build_review_prompt(pr_number: int) -> str:
    """Build the Codex prompt for an isolated pull-request review."""
    return (
        f"Review pull request #{pr_number} in the current GitHub repository. "
        f"Submit the review directly on GitHub for exactly PR #{pr_number}. "
        "Use the GitHub CLI from this isolated review worktree. "
        "The pull request is already checked out in detached HEAD. "
        f"Use `gh pr diff {pr_number} --name-only` and `gh pr diff {pr_number} --patch` as the authoritative "
        "source for the pull request's changed files and patch content. "
        "A fresh `origin/master` ref was fetched before checkout, so local `git diff origin/master...HEAD` "
        "may be used for convenience, but if local git output disagrees with `gh pr diff`, trust `gh pr diff`. "
        "During normal review, do not run `gh pr checkout`, `git checkout`, or `git switch`, and do not write files. "
        "Exception: if the PR changes `metadata/<group>/<artifact>/index.json` files, run final index validation "
        "against current `origin/master` before approving. If that validation fails because tested versions are in "
        "the wrong metadata bucket or duplicated across buckets, use the `fix-index-file-inconsistencies` skill. "
        "In that exception path, you may check out the PR branch, fix only the required `index.json` files, commit "
        "the repair, and push it to this PR branch before submitting the GitHub review. "
        "If you find blocking issues, submit a review that requests changes with a concise summary. "
        "If there are no blocking issues, submit an approval review summarizing the check and stating that you found no blocking issues."
    )


def process_pull_requests_with_label(
        label: str,
        limit: int,
        reachability_metadata_path: str,
        authenticated_user: str,
        review_model: str,
) -> None:
    """Review labeled pull requests that are CI-complete, not self-authored, and need no human intervention."""
    status_check_cache: dict[int, list[dict]] = {}
    fetch_limit = max(limit, 20)
    fixed_pull_requests = get_pull_requests_with_labels(
        [label, LABEL_HUMAN_INTERVENTION_FIXED],
        fetch_limit,
    )
    fixed_candidate_pull_requests, fixed_incomplete_ci_count = select_ci_complete_review_pull_requests(
        fixed_pull_requests,
        authenticated_user,
        limit,
        status_check_cache,
    )
    while len(fixed_candidate_pull_requests) < limit and len(fixed_pull_requests) == fetch_limit:
        print(
            f"[Found {len(fixed_candidate_pull_requests)} eligible "
            f"'{LABEL_HUMAN_INTERVENTION_FIXED}' PR(s) after filtering "
            f"{len(fixed_pull_requests)} fetched PR(s); "
            "fetching more.]"
        )
        fetch_limit *= 2
        fixed_pull_requests = get_pull_requests_with_labels(
            [label, LABEL_HUMAN_INTERVENTION_FIXED],
            fetch_limit,
        )
        fixed_candidate_pull_requests, fixed_incomplete_ci_count = select_ci_complete_review_pull_requests(
            fixed_pull_requests,
            authenticated_user,
            limit,
            status_check_cache,
        )

    failed_reviews: list[int] = []
    fixed_pull_requests_to_merge = fixed_candidate_pull_requests[:limit]
    if fixed_incomplete_ci_count:
        print(
            f"[Skipping {fixed_incomplete_ci_count} '{LABEL_HUMAN_INTERVENTION_FIXED}' "
            "candidate PR(s) without completed CI tasks.]"
        )
    for pull_request in fixed_pull_requests_to_merge:
        pr_number = pull_request["number"]
        print(
            f"[Merging PR #{pr_number} labeled "
            f"'{LABEL_HUMAN_INTERVENTION_FIXED}' without automated review.]"
        )
        try:
            dismissed_count = dismiss_requested_changes_reviews(pr_number)
            if dismissed_count:
                print(f"[Dismissed {dismissed_count} requested-changes review(s) on PR #{pr_number}.]")
            gh(
                "pr",
                "review",
                str(pr_number),
                "--repo",
                REPO,
                "--approve",
                "--body",
                f"Approved after manual follow-up marked this PR as '{LABEL_HUMAN_INTERVENTION_FIXED}'.",
            )
            pr = get_pull_request_state(pr_number)
            merge_pull_request(pr, reachability_metadata_path)
        except Exception as exc:
            print(
                f"ERROR: Failed to merge human-intervention-fixed PR #{pr_number}: {exc!r}",
                file=sys.stderr,
            )
            failed_reviews.append(pr_number)

    remaining_limit = limit - len(fixed_pull_requests_to_merge)
    if remaining_limit <= 0:
        if failed_reviews:
            print(
                f"ERROR: Pull request processing failed for pull request(s): {failed_reviews}",
                file=sys.stderr,
            )
            sys.exit(1)
        return

    fetch_limit = max(remaining_limit, 20)
    pull_requests = get_pull_requests_with_label(label, fetch_limit)
    candidate_pull_requests, incomplete_ci_count = select_ci_complete_review_pull_requests(
        pull_requests,
        authenticated_user,
        remaining_limit,
        status_check_cache,
        excluded_labels=(LABEL_HUMAN_INTERVENTION_FIXED,),
    )
    while len(candidate_pull_requests) < remaining_limit and len(pull_requests) == fetch_limit:
        print(
            f"[Found {len(candidate_pull_requests)} eligible PR(s) after filtering "
            f"{len(pull_requests)} fetched PR(s); fetching more.]"
        )
        fetch_limit *= 2
        pull_requests = get_pull_requests_with_label(label, fetch_limit)
        candidate_pull_requests, incomplete_ci_count = select_ci_complete_review_pull_requests(
            pull_requests,
            authenticated_user,
            remaining_limit,
            status_check_cache,
            excluded_labels=(LABEL_HUMAN_INTERVENTION_FIXED,),
        )

    authored_pull_requests = [
        pull_request for pull_request in pull_requests
        if is_authored_by_user(pull_request, authenticated_user)
    ]
    human_intervention_pull_requests = [
        pull_request for pull_request in pull_requests
        if pull_request_has_label(pull_request, LABEL_HUMAN_INTERVENTION)
    ]
    filtered_pull_requests = candidate_pull_requests[:remaining_limit]

    if authored_pull_requests:
        print(f"[Skipping {len(authored_pull_requests)} PR(s) authored by {authenticated_user}.]")
    if human_intervention_pull_requests:
        print(
            f"[Skipping {len(human_intervention_pull_requests)} PR(s) labeled "
            f"'{LABEL_HUMAN_INTERVENTION}'.]"
        )
    if incomplete_ci_count:
        print(f"[Skipping {incomplete_ci_count} candidate PR(s) without completed CI tasks.]")

    if not filtered_pull_requests and not fixed_pull_requests_to_merge:
        print(
            f"\n[No open pull requests found with label '{label}' that lack the "
            f"'{LABEL_HUMAN_INTERVENTION}' label, have completed CI tasks, and are not "
            f"authored by {authenticated_user}.]"
        )
        return

    for pull_request in filtered_pull_requests:
        pr_number = pull_request["number"]
        pr_url = pull_request.get("url") if isinstance(pull_request.get("url"), str) else None
        pr_title = pull_request.get("title") if isinstance(pull_request.get("title"), str) else ""
        coordinates = extract_maven_coordinates(pr_title)
        if not review_pull_request(
                pr_number,
                reachability_metadata_path,
                review_model,
                pr_url,
                coordinates,
        ):
            failed_reviews.append(pr_number)
            continue
        if not reconcile_reviewed_pull_request(pr_number, reachability_metadata_path):
            failed_reviews.append(pr_number)

    if failed_reviews:
        print(
            f"ERROR: Pull request processing failed for pull request(s): {failed_reviews}",
            file=sys.stderr,
        )
        sys.exit(1)


def set_issue_assignee(issue_number: int, username: str):
    """Set a single assignee to an issue."""
    if is_fixture_testing_enabled():
        require_fixture_github_state().set_issue_assignee(issue_number, username)
        return
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
    if is_fixture_testing_enabled():
        require_fixture_github_state().clear_issue_assignees(issue_number)
        return
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
    if is_fixture_testing_enabled():
        return require_fixture_github_state().get_issue_assignees(issue_number)
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
        print(f"ERROR: Missing metadata index file: {index_json_path_display}", file=sys.stderr)
        return None

    with open(index_json_path, "r", encoding="utf-8") as index_file:
        index_entries = json.load(index_file)

    for entry in index_entries:
        if entry.get("latest") is True:
            return entry.get("test-version") or entry.get("metadata-version")

    print(f"ERROR: No latest entry found in metadata index: {index_json_path_display}", file=sys.stderr)
    return None


def get_cached_field_info() -> tuple[str, str, dict[str, str]]:
    global project_node_id, field_id, option_ids
    if field_id is None:
        project_node_id, field_id, option_ids = get_project_field_info()
    return project_node_id, field_id, option_ids


def set_item_status(item_id: str, status: str) -> None:
    """
    Update the Status field of a project item to the given status option name.
    """
    print()
    log_stage("project-status", f"Setting project item {item_id} -> {status}")
    if is_fixture_testing_enabled():
        require_fixture_github_state().set_item_status(item_id, status)
        return
    project_node_id, field_id, option_ids = get_cached_field_info()
    option_id = option_ids.get(status)
    run_github_command_with_retries(
        gh,
        (
            "project", "item-edit",
            "--id", item_id,
            "--project-id", project_node_id,
            "--field-id", field_id,
            "--single-select-option-id", option_id,
        ),
    )


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


def resolve_human_intervention_candidate(
        claimed_issue: ClaimedIssue,
        workflow_success: bool = True,
        library_update_route: LibraryUpdateRouteLifecycle | None = None,
) -> HumanInterventionCandidate | None:
    """Return follow-up data when an issue run needs human intervention."""
    if claimed_issue.label not in {LABEL_LIBRARY_NEW, LABEL_LIBRARY_UPDATE}:
        if workflow_success:
            return None
        return HumanInterventionCandidate(
            strategy_name=None,
            workflow_status=RUN_STATUS_FAILURE,
            coverage=None,
            reason="job_failed",
        )

    run_metrics = _load_pending_run_metrics(claimed_issue.scratch_metrics_repo_path)
    if route_uses_java_failure_publication(library_update_route):
        if workflow_success:
            if not isinstance(run_metrics, dict):
                return None
            route_strategy_name = run_metrics.get("strategy_name")
            route_strategy = load_strategy_by_name(route_strategy_name)
            if route_strategy is None or route_strategy.get("workflow") != "dynamic_access_iterative":
                return None
        strategy_name = None
        workflow_status = RUN_STATUS_FAILURE
        if isinstance(run_metrics, dict):
            strategy_name = run_metrics.get("strategy_name")
            workflow_status = str(run_metrics.get("status") or RUN_STATUS_FAILURE)
        if workflow_success:
            # Fall through to the ordinary low dynamic-access check only when
            # the actual run metrics prove a dynamic-access workflow result.
            pass
        else:
            return HumanInterventionCandidate(
                strategy_name=strategy_name,
                workflow_status=workflow_status,
                coverage=None,
                reason="job_failed",
            )

    if not workflow_success:
        strategy_name = None
        workflow_status = RUN_STATUS_FAILURE
        coverage_snapshot = None
        if isinstance(run_metrics, dict):
            strategy_name = run_metrics.get("strategy_name")
            workflow_status = str(run_metrics.get("status") or RUN_STATUS_FAILURE)
            coverage_snapshot = _load_dynamic_access_snapshot_from_metrics(run_metrics)
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


def build_command_used_evidence() -> dict:
    """Return the command line that launched this dispatcher run."""
    argv = [sys.executable, *sys.argv]
    return {
        "argv": argv,
        "display": " ".join(shlex.quote(argument) for argument in argv),
    }


def build_fixture_e2e_report_context(
        claimed_issue: ClaimedIssue,
        strategy_name: str,
        keep_tests_without_dynamic_access: bool,
        canonical_metrics_repo_path: str,
) -> FixtureE2EReportContext:
    """Create the dispatcher-owned fixture E2E report context for one claimed issue."""
    fixture_state = require_fixture_github_state()
    strategy = require_strategy_by_name(strategy_name)
    issue_number = claimed_issue.issue["number"]
    run_id = os.path.basename(os.path.abspath(claimed_issue.worktree_path))
    report_dir = os.path.join(
        os.path.abspath(canonical_metrics_repo_path),
        SCRIPT_RUN_METRICS_DIR,
        FIXTURE_E2E_REPORT_DIR,
        f"issue-{issue_number}-{run_id}",
    )
    return FixtureE2EReportContext(
        issue_number=issue_number,
        fixture_path=fixture_state.get_issue_fixture_path(issue_number),
        canonical_metrics_repo_path=os.path.abspath(canonical_metrics_repo_path),
        report_dir=report_dir,
        report_path=os.path.join(report_dir, FIXTURE_E2E_REPORT_FILENAME),
        run_id=run_id,
        queue_label=claimed_issue.label,
        strategy_name=strategy_name,
        model=str(strategy.get("model") or "unknown"),
        workflow_engine=str(strategy.get("workflow") or "unknown"),
        command_used=build_command_used_evidence(),
        routed_driver=build_workflow_driver_invocation(
            claimed_issue,
            strategy_name,
            keep_tests_without_dynamic_access,
        ),
        library_update_route=None,
        keep_tests_without_dynamic_access=keep_tests_without_dynamic_access,
    )


def _json_file_status(path: str, schema: str | None = None) -> dict:
    """Return JSON parse and optional schema status for a metrics file."""
    status: dict[str, Any] = {
        "path": path,
        "exists": os.path.isfile(path),
        "json_status": "missing",
        "schema_validation": {
            "status": "not-run",
            "reason": "file is missing",
        },
    }
    if not status["exists"]:
        return status

    try:
        with open(path, "r", encoding="utf-8") as json_file:
            json.load(json_file)
        status["json_status"] = "valid"
    except Exception as exc:
        status["json_status"] = "invalid"
        status["json_error"] = f"{type(exc).__name__}: {exc}"
        status["schema_validation"] = {
            "status": "not-run",
            "reason": "JSON parsing failed",
        }
        return status

    if schema is None:
        status["schema_validation"] = {
            "status": "not-applicable",
            "reason": "No schema was requested for this file.",
        }
        return status

    try:
        if schema == "run_metrics_output":
            validate_run_metrics(path)
        else:
            raise ValueError(f"Unsupported schema alias: {schema}")
        status["schema_validation"] = {
            "status": "passed",
            "schema": schema,
        }
    except Exception as exc:
        status["schema_validation"] = {
            "status": "failed",
            "schema": schema,
            "error": f"{type(exc).__name__}: {exc}",
        }
    return status


def _load_json_file_if_present(path: str | None) -> Any | None:
    """Load JSON evidence when it exists, returning None for missing or malformed files."""
    if path is None or not os.path.isfile(path):
        return None
    try:
        with open(path, "r", encoding="utf-8") as json_file:
            return json.load(json_file)
    except Exception:
        return None


def _copy_fixture_report_artifact(source_path: str | None, report_dir: str, filename: str) -> dict:
    """Copy an inspectable fixture artifact into the durable report directory."""
    evidence: dict[str, Any] = {
        "source_path": source_path,
        "path": None,
        "copied": False,
        "exists": False,
    }
    if source_path is None or not os.path.isfile(source_path):
        return evidence

    os.makedirs(report_dir, exist_ok=True)
    destination_path = os.path.join(report_dir, filename)
    shutil.copy2(source_path, destination_path)
    evidence.update({
        "path": destination_path,
        "copied": True,
        "exists": os.path.isfile(destination_path),
    })
    return evidence


def _replace_json_value(data: Any, old_value: str, new_value: str) -> None:
    """Replace matching string values in nested JSON-like evidence in place."""
    if isinstance(data, dict):
        for key, value in data.items():
            if value == old_value:
                data[key] = new_value
            else:
                _replace_json_value(value, old_value, new_value)
    elif isinstance(data, list):
        for value in data:
            _replace_json_value(value, old_value, new_value)


def preserve_fixture_report_artifacts(
        context: FixtureE2EReportContext,
        claimed_issue: ClaimedIssue,
        metrics_evidence: dict,
        library_update_route_evidence: dict | None = None,
) -> dict:
    """Preserve metrics and route files so fixture reports survive workspace cleanup."""
    artifacts: dict[str, dict] = {
        "pending_metrics": _copy_fixture_report_artifact(
            metrics_evidence.get("pending_metrics", {}).get("path"),
            context.report_dir,
            PENDING_METRICS_FILENAME,
        ),
        "durable_metrics": _copy_fixture_report_artifact(
            metrics_evidence.get("durable_metrics", {}).get("path"),
            context.report_dir,
            "execution-metrics.json",
        ),
    }

    if isinstance(library_update_route_evidence, dict):
        sidecar_path = library_update_route_evidence.get("route_sidecar_json_path")
        artifacts["library_update_route_sidecar"] = _copy_fixture_report_artifact(
            sidecar_path if isinstance(sidecar_path, str) else None,
            context.report_dir,
            "library-update-route-result.json",
        )
        route_log_artifacts: list[dict] = []
        route_log_paths = library_update_route_evidence.get("route_log_paths")
        if isinstance(route_log_paths, list):
            for index, route_log_path in enumerate(route_log_paths, start=1):
                if not isinstance(route_log_path, str):
                    continue
                route_log_artifacts.append(_copy_fixture_report_artifact(
                    route_log_path,
                    context.report_dir,
                    f"library-update-route-{index}-{os.path.basename(route_log_path)}",
                ))
        artifacts["library_update_route_logs"] = {
            "logs": route_log_artifacts,
        }
        for artifact in [artifacts["library_update_route_sidecar"], *route_log_artifacts]:
            source_path = artifact.get("source_path")
            preserved_path = artifact.get("path")
            if artifact.get("copied") and source_path and preserved_path:
                _replace_json_value(library_update_route_evidence, source_path, preserved_path)

    for artifact_name, status_key in (
        ("pending_metrics", "pending_metrics"),
        ("durable_metrics", "durable_metrics"),
    ):
        artifact = artifacts[artifact_name]
        source_path = artifact.get("source_path")
        preserved_path = artifact.get("path")
        if artifact.get("copied") and source_path and preserved_path:
            status = metrics_evidence.get(status_key)
            if isinstance(status, dict):
                status["source_path"] = source_path
                status["path"] = preserved_path
            _replace_json_value(metrics_evidence, source_path, preserved_path)

    return artifacts


def _path_evidence(kind: str, path: str, repo_root: str | None = None) -> dict:
    """Summarize a generated artifact path without copying its contents."""
    absolute_path = os.path.abspath(path)
    evidence: dict[str, Any] = {
        "kind": kind,
        "path": absolute_path,
        "exists": os.path.exists(absolute_path),
    }
    if repo_root is not None:
        evidence["relative_path"] = _repo_relative_path(absolute_path, repo_root)
    if os.path.isfile(absolute_path):
        evidence["type"] = "file"
        evidence["size_bytes"] = os.path.getsize(absolute_path)
    elif os.path.isdir(absolute_path):
        evidence["type"] = "directory"
        file_count = 0
        sample_files: list[str] = []
        for root, _, file_names in os.walk(absolute_path):
            for file_name in sorted(file_names):
                file_count += 1
                if len(sample_files) < 25:
                    sample_files.append(os.path.relpath(os.path.join(root, file_name), absolute_path))
        evidence["file_count"] = file_count
        evidence["sample_files"] = sample_files
    else:
        evidence["type"] = "missing"
    return evidence


def _fixture_artifact_coordinate(claimed_issue: ClaimedIssue) -> tuple[str, str, str]:
    """Return the coordinate whose generated artifacts should be inspected."""
    if claimed_issue.current_coordinates and claimed_issue.new_version:
        group, artifact, _old_version = metadata_coordinate_parts(claimed_issue.current_coordinates)
        return group, artifact, claimed_issue.new_version
    group, artifact, version = metadata_coordinate_parts(claimed_issue.issue_coordinates)
    if version is None:
        raise ValueError(f"Missing artifact version in {claimed_issue.issue_coordinates}")
    return group, artifact, version


def collect_fixture_generated_artifact_evidence(claimed_issue: ClaimedIssue) -> list[dict]:
    """Collect paths for generated tests, metadata, stats, and workflow artifacts."""
    group, artifact, version = _fixture_artifact_coordinate(claimed_issue)
    try:
        metadata_version = resolve_metadata_version(claimed_issue.worktree_path, group, artifact, version)
        test_dir = resolve_test_dir(claimed_issue.worktree_path, group, artifact, version)
    except Exception:
        metadata_version = version
        test_dir = os.path.join(claimed_issue.worktree_path, "tests", "src", group, artifact, version)

    candidate_paths = [
        ("generated-tests", test_dir),
        ("metadata-index", os.path.join(claimed_issue.worktree_path, "metadata", group, artifact, "index.json")),
        ("metadata", os.path.join(claimed_issue.worktree_path, "metadata", group, artifact, metadata_version)),
        ("stats", os.path.join(claimed_issue.worktree_path, "stats", group, artifact, metadata_version, "stats.json")),
        ("execution-metrics", os.path.join(
            claimed_issue.worktree_path,
            "stats",
            group,
            artifact,
            metadata_version,
            "execution-metrics.json",
        )),
        ("dynamic-access-report", os.path.join(
            test_dir,
            "build",
            "reports",
            "dynamic-access",
            "dynamic-access-coverage.json",
        )),
    ]
    large_library_state_path = find_progress_state_path(
        claimed_issue.scratch_metrics_repo_path,
        claimed_issue.issue["number"],
    )
    if large_library_state_path is not None:
        candidate_paths.append(("large-library-progress", large_library_state_path))

    seen: set[str] = set()
    artifacts: list[dict] = []
    for kind, path in candidate_paths:
        absolute_path = os.path.abspath(path)
        if absolute_path in seen:
            continue
        seen.add(absolute_path)
        artifacts.append(_path_evidence(kind, absolute_path, claimed_issue.worktree_path))
    return artifacts


def collect_fixture_metrics_evidence(claimed_issue: ClaimedIssue) -> dict:
    """Collect pending and durable metrics paths plus schema-validation status."""
    pending_metrics_path = os.path.join(claimed_issue.scratch_metrics_repo_path, PENDING_METRICS_FILENAME)
    run_metrics = _load_pending_run_metrics(claimed_issue.scratch_metrics_repo_path)
    durable_metrics_path = None
    if isinstance(run_metrics, dict):
        try:
            durable_metrics_path = execution_metrics_path(claimed_issue.worktree_path, run_metrics)
        except Exception:
            durable_metrics_path = None

    evidence: dict[str, Any] = {
        "scratch_metrics_root": claimed_issue.scratch_metrics_repo_path,
        "pending_metrics": _json_file_status(pending_metrics_path),
        "run_metrics_available": isinstance(run_metrics, dict),
    }
    if isinstance(run_metrics, dict):
        evidence["run_metrics_summary"] = {
            "library": run_metrics.get("library"),
            "strategy_name": run_metrics.get("strategy_name"),
            "agent": run_metrics.get("agent"),
            "model": run_metrics.get("model"),
            "status": run_metrics.get("status"),
            "timestamp": run_metrics.get("timestamp"),
        }
    if durable_metrics_path is not None:
        evidence["durable_metrics"] = _json_file_status(durable_metrics_path, "run_metrics_output")
    else:
        evidence["durable_metrics"] = {
            "path": None,
            "exists": False,
            "schema_validation": {
                "status": "not-run",
                "reason": "No pending run metrics were available to resolve the durable metrics path.",
            },
        }
    return evidence


def _fixture_side_effect_details(report: dict, action: str) -> list[dict]:
    """Return details for side effects with the requested action."""
    side_effects = report.get("side_effects")
    if not isinstance(side_effects, list):
        return []
    return [
        dict(effect.get("details") or {})
        for effect in side_effects
        if isinstance(effect, dict) and effect.get("action") == action
    ]


def _side_effect_comparison_passed(report: dict) -> bool:
    comparison = report.get("side_effect_comparison")
    return isinstance(comparison, dict) and bool(comparison.get("passed"))


def build_fixture_boundary_verification(
        report: dict,
        claimed_issue: ClaimedIssue,
        lifecycle_result: str,
        metrics_evidence: dict,
) -> dict:
    """Summarize the required E2E boundary evidence from §E2E-forge-workflow-testing.5."""
    side_effects = report.get("side_effects") if isinstance(report.get("side_effects"), list) else []
    side_effect_actions = [
        str(effect.get("action"))
        for effect in side_effects
        if isinstance(effect, dict)
    ]
    return {
        "issue_routing": {
            "queue_label": claimed_issue.label,
            "claim_side_effects_present": all(
                action in side_effect_actions
                for action in ("set-assignee", "set-project-status")
            ),
            "routed_driver": report.get("routed_driver"),
            "library_update_route": report.get("library_update_route"),
        },
        "setup": {
            "worktree_path": claimed_issue.worktree_path,
            "worktree_present_before_cleanup": os.path.isdir(claimed_issue.worktree_path),
            "scratch_metrics_root": claimed_issue.scratch_metrics_repo_path,
            "scratch_metrics_root_present_before_cleanup": os.path.isdir(claimed_issue.scratch_metrics_repo_path),
            "base_reachability_metadata_path": claimed_issue.base_reachability_metadata_path,
        },
        "workflow_execution": {
            "lifecycle_result": lifecycle_result,
            "pending_metrics_available": bool(metrics_evidence.get("run_metrics_available")),
        },
        "artifacts_and_metrics": {
            "durable_metrics_schema_validation": (
                metrics_evidence
                .get("durable_metrics", {})
                .get("schema_validation", {})
            ),
        },
        "issue_pr_result": {
            "publication_handoff_recorded": "publication-handoff" in side_effect_actions,
            "failure_preservation_recorded": "failure-preservation" in side_effect_actions,
            "side_effect_comparison_passed": _side_effect_comparison_passed(report),
        },
    }


def build_fixture_trust_assessment(
        report: dict,
        lifecycle_result: str,
        metrics_evidence: dict,
) -> dict:
    """Return suspicious behavior and residual risk evidence for the report."""
    suspicious_behavior: list[str] = []
    residual_risk: list[str] = []
    publication_handoffs = _fixture_side_effect_details(report, "publication-handoff")
    failure_preservations = _fixture_side_effect_details(report, "failure-preservation")

    if not _side_effect_comparison_passed(report):
        suspicious_behavior.append("fixture side effects did not match expected_side_effects")
    report_assertions = report.get("report_assertion_comparison")
    if isinstance(report_assertions, dict) and not report_assertions.get("passed"):
        suspicious_behavior.append("fixture report assertions did not match expected_report_assertions")
    if lifecycle_result == "success" and not publication_handoffs:
        suspicious_behavior.append("successful fixture run did not record a dry-run publication handoff")
    if lifecycle_result != "success" and not failure_preservations:
        suspicious_behavior.append("failed fixture run did not record a failure-preservation handoff")
    durable_schema = (
        metrics_evidence
        .get("durable_metrics", {})
        .get("schema_validation", {})
        .get("status")
    )
    if durable_schema == "failed":
        suspicious_behavior.append("durable metrics schema validation failed")
    if report.get("generated_artifacts_error"):
        suspicious_behavior.append("generated artifact discovery failed")

    if lifecycle_result != "success":
        residual_risk.append("workflow did not complete successfully; inspect preserved work and logs")
    if not metrics_evidence.get("run_metrics_available"):
        residual_risk.append("pending run metrics were not available for this fixture report")

    return {
        "suspicious_behavior": suspicious_behavior or ["none-observed"],
        "residual_risk": residual_risk or ["none-observed"],
        "result_should_not_be_trusted_reasons": suspicious_behavior + residual_risk,
    }


def write_fixture_e2e_report(
        context: FixtureE2EReportContext,
        claimed_issue: ClaimedIssue,
        lifecycle_result: str,
        started_at: float | None,
        run_result: WorkflowRunResult | None = None,
) -> str:
    """Write the persisted fixture-backed E2E evidence report."""
    fixture_state = require_fixture_github_state()
    metrics_evidence = collect_fixture_metrics_evidence(claimed_issue)
    routed_driver = (
        run_result.routed_driver
        if run_result is not None
        else context.routed_driver
    )
    library_update_route = (
        run_result.library_update_route
        if run_result is not None
        else context.library_update_route
    )
    library_update_route_evidence = (
        library_update_route.to_json() if library_update_route is not None else None
    )
    os.makedirs(context.report_dir, exist_ok=True)
    preserved_artifacts = preserve_fixture_report_artifacts(
        context,
        claimed_issue,
        metrics_evidence,
        library_update_route_evidence,
    )
    generated_artifacts: list[dict] = []
    generated_artifacts_error = None
    try:
        generated_artifacts = collect_fixture_generated_artifact_evidence(claimed_issue)
    except Exception as exc:
        generated_artifacts_error = f"{type(exc).__name__}: {exc}"
    preserved_route_sidecar_json = _load_json_file_if_present(
        preserved_artifacts
        .get("library_update_route_sidecar", {})
        .get("path")
    )

    report = fixture_state.build_report(context.issue_number)
    report.update({
        "fixture_e2e_report_version": 1,
        "mode": "fixture-backed E2E",
        "issue_number": context.issue_number,
        "fixture_path": context.fixture_path,
        "queue_label": context.queue_label,
        "strategy_name": context.strategy_name,
        "model": context.model,
        "command_used": context.command_used,
        "routed_driver": routed_driver.to_json() if routed_driver is not None else None,
        "library_update_route": library_update_route_evidence,
        "workflow_engine": context.workflow_engine,
        "run_id": context.run_id,
        "lifecycle_result": lifecycle_result,
        "keep_tests_without_dynamic_access": context.keep_tests_without_dynamic_access,
        "paths": {
            "report_dir": context.report_dir,
            "report_path": context.report_path,
            "worktree": claimed_issue.worktree_path,
            "scratch_metrics_root": claimed_issue.scratch_metrics_repo_path,
            "canonical_metrics_root": context.canonical_metrics_repo_path,
            "logs_root": resolve_logs_root(),
        },
        "important_log_paths": [
            _path_evidence("log", log_path)
            for log_path in collect_issue_log_paths(claimed_issue, started_at)
        ],
        "metrics_paths": metrics_evidence,
        "preserved_report_artifacts": preserved_artifacts,
        "library_update_route_sidecar_json": preserved_route_sidecar_json,
        "generated_artifacts": generated_artifacts,
        "generated_artifacts_error": generated_artifacts_error,
        "dry_run_publication_handoff": _fixture_side_effect_details(report, "publication-handoff"),
        "failure_preservation_handoff": _fixture_side_effect_details(report, "failure-preservation"),
    })
    report["boundary_verification"] = build_fixture_boundary_verification(
        report,
        claimed_issue,
        lifecycle_result,
        metrics_evidence,
    )
    report["report_assertion_comparison"] = fixture_state.compare_expected_report_assertions(
        context.issue_number,
        report,
    )
    report["trust_assessment"] = build_fixture_trust_assessment(
        report,
        lifecycle_result,
        metrics_evidence,
    )

    with open(context.report_path, "w", encoding="utf-8") as report_file:
        json.dump(report, report_file, indent=2, sort_keys=True)
        report_file.write("\n")
    return context.report_path


def write_fixture_e2e_report_best_effort(
        context: FixtureE2EReportContext | None,
        claimed_issue: ClaimedIssue,
        lifecycle_result: str,
        started_at: float | None,
        run_result: WorkflowRunResult | None = None,
) -> None:
    """Write fixture E2E evidence without masking the workflow result."""
    if context is None:
        return
    try:
        report_path = write_fixture_e2e_report(
            context,
            claimed_issue,
            lifecycle_result,
            started_at,
            run_result,
        )
        log_stage("fixture-report", f"Wrote fixture E2E report: {report_path}")
    except Exception as exc:
        print(
            f"ERROR: Fixture E2E report generation failed for issue #{claimed_issue.issue['number']}: {exc!r}",
            file=sys.stderr,
        )
        traceback.print_exc()


def append_fixture_report_path(message: str, context: FixtureE2EReportContext | None) -> str:
    """Add the fixture report path to a console summary when fixture mode is active."""
    if context is None:
        return message
    return f"{message}\nFixture E2E report: {context.report_path}"


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


def _format_library_update_route_summary(
        library_update_route: LibraryUpdateRouteLifecycle | None,
) -> str:
    """Format route evidence for analysis prompts and deterministic comments."""
    if library_update_route is None:
        return "- Library-update route: not applicable"
    sidecar = library_update_route.route_sidecar_json_path or "not recorded"
    logs = ", ".join(library_update_route.route_log_paths) or "none"
    return (
        f"- Library-update route: {library_update_route.selected_route}\n"
        f"- Requested coordinate: {library_update_route.requested_coordinate}\n"
        f"- Selected baseline coordinate: {library_update_route.selected_baseline_coordinate or 'none'}\n"
        f"- Selected driver: {library_update_route.selected_driver_name or 'none'}\n"
        f"- Route sidecar: {display_log_path(sidecar)}\n"
        f"- Route logs: {logs}"
    )


def _build_failed_generation_analysis_prompt(
        claimed_issue: ClaimedIssue,
        candidate: HumanInterventionCandidate,
        log_paths: list[str],
        run_metrics: dict | None,
        preservation_result: FailurePreservationResult | None = None,
        library_update_route: LibraryUpdateRouteLifecycle | None = None,
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
        f"{_format_library_update_route_summary(library_update_route)}\n"
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
        library_update_route: LibraryUpdateRouteLifecycle | None = None,
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
        "Route evidence:\n"
        f"{_format_library_update_route_summary(library_update_route)}\n\n"
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
        library_update_route: LibraryUpdateRouteLifecycle | None = None,
) -> str:
    """Use Codex to analyze a failed library-generation run and write an issue comment."""
    run_metrics = _load_pending_run_metrics(claimed_issue.scratch_metrics_repo_path)
    log_paths = collect_issue_log_paths(claimed_issue, started_at)
    log_paths.extend(library_update_route_evidence_paths(library_update_route))
    if not claimed_issue_worktree_is_valid(claimed_issue, "failed-run analysis"):
        return _build_failed_generation_fallback_comment(
            claimed_issue,
            candidate,
            log_paths,
            preservation_result,
            library_update_route,
        )
    prompt = _build_failed_generation_analysis_prompt(
        claimed_issue,
        candidate,
        log_paths,
        run_metrics,
        preservation_result,
        library_update_route,
    )
    log_path = get_codex_failure_analysis_log_path(
        claimed_issue.issue["number"],
        claimed_issue.issue_coordinates,
    )
    log_path_display = display_log_path(log_path)
    cmd = [
        "codex", "exec",
        "--dangerously-bypass-approvals-and-sandbox",
        "--json",
        "-c", 'reasoning.effort="medium"',
        "-m", DEFAULT_REVIEW_MODEL,
        prompt,
    ]

    log_stage(
        "failure-analysis",
        f"Running Codex failure analysis for issue #{claimed_issue.issue['number']}; output: {log_path_display}",
    )
    try:
        with open(log_path, "w", encoding="utf-8") as log_file:
            result = subprocess.run(
                cmd,
                cwd=claimed_issue.worktree_path,
                stdout=log_file,
                stderr=subprocess.STDOUT,
                text=True,
                timeout=CODEX_REVIEW_TIMEOUT_SECONDS,
                check=False,
            )
    except subprocess.TimeoutExpired:
        print(
            (
                f"ERROR: Codex failure analysis timed out after {CODEX_REVIEW_TIMEOUT_SECONDS} seconds "
                f"for issue #{claimed_issue.issue['number']}. See {log_path_display}."
            ),
            file=sys.stderr,
        )
        return _build_failed_generation_fallback_comment(
            claimed_issue,
            candidate,
            log_paths,
            preservation_result,
            library_update_route,
        )

    comment_body = extract_codex_final_message(log_path)
    if result.returncode == 0 and comment_body:
        return comment_body

    output_tail = read_log_tail(log_path)
    print(
        (
            f"ERROR: Codex failure analysis failed for issue #{claimed_issue.issue['number']} "
            f"with exit code {result.returncode}. Log: {log_path_display}."
            + (f"\n{output_tail}" if output_tail else "")
        ),
        file=sys.stderr,
    )
    return _build_failed_generation_fallback_comment(
        claimed_issue,
        candidate,
        log_paths,
        preservation_result,
        library_update_route,
    )


def run_human_intervention_analysis(
        claimed_issue: ClaimedIssue,
        candidate: HumanInterventionCandidate,
        started_at: float | None = None,
        preservation_result: FailurePreservationResult | None = None,
        library_update_route: LibraryUpdateRouteLifecycle | None = None,
) -> str:
    """Run a separate agent pass that writes the issue comment for human follow-up."""
    if candidate.reason in {"test_generation_failed", "job_failed"}:
        return run_codex_failed_generation_analysis(
            claimed_issue,
            candidate,
            started_at,
            preservation_result,
            library_update_route,
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
            f"ERROR: Failed to ensure label '{label_name}' exists.\n{error_output}",
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
    elif label_name == LABEL_LARGE_LIBRARY_SERIES:
        label_color = LARGE_LIBRARY_LABEL_COLOR
        label_description = LARGE_LIBRARY_SERIES_LABEL_DESCRIPTION
    elif label_name == LABEL_LARGE_LIBRARY_NEXT_PART:
        label_color = LARGE_LIBRARY_LABEL_COLOR
        label_description = LARGE_LIBRARY_NEXT_PART_LABEL_DESCRIPTION
    elif label_name == LABEL_LARGE_LIBRARY_BLOCKED:
        label_color = LARGE_LIBRARY_LABEL_COLOR
        label_description = LARGE_LIBRARY_BLOCKED_LABEL_DESCRIPTION
    elif label_name == LABEL_LARGE_LIBRARY_PART:
        label_color = LARGE_LIBRARY_LABEL_COLOR
        label_description = LARGE_LIBRARY_PART_LABEL_DESCRIPTION
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
    ensure_repo_label_exists(
        label_name,
        HUMAN_INTERVENTION_LABEL_COLOR,
        HUMAN_INTERVENTION_LABEL_DESCRIPTION,
    )
    gh(
        "api",
        "--method",
        "POST",
        f"/repos/{REPO}/issues/{pr_number}/labels",
        "-f",
        f"labels[]={label_name}",
    )


def _sanitize_branch_segment(value: str) -> str:
    """Return a branch-safe path segment."""
    return re.sub(r"[^A-Za-z0-9._-]+", "-", value).strip("-._") or "unknown"


def build_failure_preservation_branch_name(claimed_issue: ClaimedIssue) -> str:
    """Build a unique branch name for preserving a failed issue run."""
    authenticated_login = _sanitize_branch_segment(get_authenticated_user())
    coordinate = _sanitize_branch_segment(claimed_issue.issue_coordinates)
    label = _sanitize_branch_segment(claimed_issue.label)
    return (
        f"ai/{authenticated_login}/human-intervention/"
        f"issue-{claimed_issue.issue['number']}-{label}-{coordinate}-{uuid.uuid4().hex[:8]}"
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


def library_update_route_evidence_paths(
        library_update_route: LibraryUpdateRouteLifecycle | None,
) -> list[str]:
    """Return existing sidecar/log evidence paths for a missing-version route."""
    if library_update_route is None:
        return []
    paths = [
        library_update_route.route_sidecar_json_path,
        *library_update_route.route_log_paths,
    ]
    seen: set[str] = set()
    existing_paths: list[str] = []
    for path in paths:
        if not path or path in seen or not os.path.isfile(path):
            continue
        seen.add(path)
        existing_paths.append(path)
    return existing_paths


def copy_library_update_route_evidence_to_preserved_worktree(
        claimed_issue: ClaimedIssue,
        library_update_route: LibraryUpdateRouteLifecycle | None,
        logs_destination_relpath: str | None,
) -> str | None:
    """Copy router sidecar/log evidence into the preserved work branch."""
    evidence_paths = library_update_route_evidence_paths(library_update_route)
    if not evidence_paths:
        return logs_destination_relpath

    safe_library_name = sanitize_library_log_segment(claimed_issue.issue_coordinates)
    destination_relpath = logs_destination_relpath or os.path.join(
        HUMAN_INTERVENTION_LOGS_DIRNAME,
        safe_library_name,
    )
    destination_dir = os.path.join(
        claimed_issue.worktree_path,
        destination_relpath,
        "library-update-router",
    )
    os.makedirs(destination_dir, exist_ok=True)
    for path in evidence_paths:
        shutil.copy2(path, os.path.join(destination_dir, os.path.basename(path)))
    log_stage(
        "preserve-failed-work",
        (
            f"Copied library-update router evidence for {claimed_issue.issue_coordinates} "
            f"to {os.path.join(destination_relpath, 'library-update-router')}"
        ),
    )
    return destination_relpath


def preserve_failed_work_branch(
        claimed_issue: ClaimedIssue,
        library_update_route: LibraryUpdateRouteLifecycle | None = None,
) -> FailurePreservationResult:
    """Commit and push the failed run worktree so it survives workspace cleanup."""
    branch_name = build_failure_preservation_branch_name(claimed_issue)
    repo_path = require_claimed_issue_worktree(claimed_issue, "failure preservation")
    git_env = git_env_limited_to_repo_root(repo_path)
    issue_number = claimed_issue.issue["number"]

    log_stage("preserve-failed-work", f"Preserving failed work for issue #{issue_number} on branch {branch_name}")
    subprocess.run(["git", "switch", "-C", branch_name], cwd=repo_path, env=git_env, check=True)
    logs_destination_relpath = copy_library_logs_to_preserved_worktree(claimed_issue)
    logs_destination_relpath = copy_library_update_route_evidence_to_preserved_worktree(
        claimed_issue,
        library_update_route,
        logs_destination_relpath,
    )
    subprocess.run(["git", "add", "-A"], cwd=repo_path, env=git_env, check=True)
    if logs_destination_relpath is not None:
        subprocess.run(["git", "add", "-f", "--", logs_destination_relpath], cwd=repo_path, env=git_env, check=True)
    diff_result = subprocess.run(["git", "diff", "--cached", "--quiet"], cwd=repo_path, env=git_env, check=False)
    committed_changes = diff_result.returncode != 0
    if committed_changes:
        subprocess.run(
            ["git", "commit", "-m", f"Preserve failed automation work for issue #{issue_number}"],
            cwd=repo_path,
            env=git_env,
            check=True,
        )
    else:
        log_stage("preserve-failed-work", f"No uncommitted work found for issue #{issue_number}; pushing branch at current HEAD.")

    subprocess.run(["git", "push", "-u", "origin", branch_name], cwd=repo_path, env=git_env, check=True)
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


def build_fixture_failure_preservation_result(
        claimed_issue: ClaimedIssue,
        library_update_route: LibraryUpdateRouteLifecycle | None = None,
) -> FailurePreservationResult:
    """Record the preservation branch that fixture mode would have pushed."""
    issue_number = claimed_issue.issue["number"]
    branch_name = build_failure_preservation_branch_name(claimed_issue)
    worktree_path = os.path.abspath(claimed_issue.worktree_path)
    logs_destination_relpath = None
    if os.path.isdir(worktree_path):
        logs_destination_relpath = copy_library_logs_to_preserved_worktree(claimed_issue)
        logs_destination_relpath = copy_library_update_route_evidence_to_preserved_worktree(
            claimed_issue,
            library_update_route,
            logs_destination_relpath,
        )
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
        library_update_route: LibraryUpdateRouteLifecycle | None = None,
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
    subprocess.run(["git", "switch", preservation_result.branch_name], cwd=repo_path, env=git_env, check=True)
    logs_destination_relpath = copy_library_logs_to_preserved_worktree(claimed_issue)
    logs_destination_relpath = copy_library_update_route_evidence_to_preserved_worktree(
        claimed_issue,
        library_update_route,
        logs_destination_relpath,
    )
    if logs_destination_relpath is None:
        return

    subprocess.run(["git", "add", "-f", "--", logs_destination_relpath], cwd=repo_path, env=git_env, check=True)
    diff_result = subprocess.run(
        ["git", "diff", "--cached", "--quiet", "--", logs_destination_relpath],
        cwd=repo_path,
        env=git_env,
        check=False,
    )
    if diff_result.returncode == 0:
        log_stage("preserve-failed-work", f"No new workflow logs to add for issue #{issue_number}.")
        return

    subprocess.run(
        ["git", "commit", "-m", f"Add automation logs for issue #{issue_number}"],
        cwd=repo_path,
        env=git_env,
        check=True,
    )
    subprocess.run(["git", "push"], cwd=repo_path, env=git_env, check=True)
    log_stage("preserve-failed-work", f"Updated preserved branch logs for issue #{issue_number}.")


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


def post_human_intervention_comment_and_label(issue_number: int, comment_body: str | None) -> None:
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


def maybe_apply_human_intervention_follow_up(
        claimed_issue: ClaimedIssue,
        workflow_success: bool = True,
        started_at: float | None = None,
        preservation_result: FailurePreservationResult | None = None,
        library_update_route: LibraryUpdateRouteLifecycle | None = None,
) -> bool:
    """Post a human-intervention comment and label when an issue run needs follow-up."""
    if is_user_interrupt_requested():
        return False

    candidate = resolve_human_intervention_candidate(
        claimed_issue,
        workflow_success,
        library_update_route,
    )
    if candidate is None:
        return False

    try:
        comment_body = run_human_intervention_analysis(
            claimed_issue,
            candidate,
            started_at,
            preservation_result,
            library_update_route,
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
    post_human_intervention_comment_and_label(claimed_issue.issue["number"], comment_body)
    return True


def apply_failed_run_follow_up(
        claimed_issue: ClaimedIssue,
        started_at: float | None = None,
        preservation_result: FailurePreservationResult | None = None,
        library_update_route: LibraryUpdateRouteLifecycle | None = None,
) -> None:
    """Run Codex failure analysis and post the failed-run follow-up comment."""
    if is_user_interrupt_requested():
        raise KeyboardInterrupt

    candidate = resolve_human_intervention_candidate(
        claimed_issue,
        workflow_success=False,
        library_update_route=library_update_route,
    )
    if candidate is None:
        candidate = HumanInterventionCandidate(
            strategy_name=None,
            workflow_status=RUN_STATUS_FAILURE,
            coverage=None,
            reason="job_failed",
        )

    try:
        comment_body = run_codex_failed_generation_analysis(
            claimed_issue,
            candidate,
            started_at,
            preservation_result,
            library_update_route,
        )
        comment_body = ensure_preserved_branch_link_in_comment(comment_body, preservation_result)
    except Exception as exc:
        print(
            f"ERROR: Failed to apply failed-run follow-up to issue #{claimed_issue.issue['number']}: {exc!r}",
            file=sys.stderr,
        )
        traceback.print_exc()
        comment_body = None
    if is_user_interrupt_requested():
        raise KeyboardInterrupt
    post_human_intervention_comment_and_label(claimed_issue.issue["number"], comment_body)


def append_large_library_workflow_args(pipeline_argv: list[str], claimed_issue: ClaimedIssue) -> None:
    """Append issue context and concrete chunk limits for oversized dynamic-access runs.

    The dispatcher (§AR-forge-control-plane) computes the issue-scoped chunking
    context and passes only execution flags to the workflow driver, so the
    chunk limits stay consistent with the exhaust report
    (§WF-dynamic-access-exhaust-report).
    """
    issue_number = claimed_issue.issue["number"]
    pipeline_argv.extend(["--issue-number", str(issue_number)])
    if (
            claimed_issue.large_library_resume_artifact
            or issue_has_label(claimed_issue.issue, LABEL_LARGE_LIBRARY_SERIES)
            or issue_has_label(claimed_issue.issue, LABEL_LARGE_LIBRARY_NEXT_PART)
    ):
        pipeline_argv.append("--large-library-series")
    chunk_class_limit = os.environ.get("FORGE_LARGE_LIBRARY_CHUNK_CLASS_LIMIT")
    if chunk_class_limit:
        pipeline_argv.extend(["--chunk-class-limit", chunk_class_limit])
    chunk_call_limit = os.environ.get("FORGE_LARGE_LIBRARY_CHUNK_CALL_LIMIT")
    if chunk_call_limit:
        pipeline_argv.extend(["--chunk-call-limit", chunk_call_limit])


def build_improve_library_coverage_invocation(
        claimed_issue: ClaimedIssue,
        strategy_name: str | None,
) -> WorkflowDriverInvocation:
    """Build the ordinary improve-coverage driver invocation for a library update."""
    issue_number = claimed_issue.issue["number"]
    pipeline_argv = [
        "--coordinates", claimed_issue.issue_coordinates,
        "--reachability-metadata-path", claimed_issue.worktree_path,
        "--metrics-repo-path", claimed_issue.scratch_metrics_repo_path,
    ]
    issue_requested_metadata_context = extract_issue_requested_metadata_context(
        get_issue_body(issue_number)
    )
    if issue_requested_metadata_context:
        pipeline_argv.extend([
            "--issue-requested-metadata-context",
            issue_requested_metadata_context,
        ])
    if strategy_name:
        pipeline_argv.extend(["--strategy-name", strategy_name])
    append_large_library_workflow_args(pipeline_argv, claimed_issue)
    return WorkflowDriverInvocation(
        driver_name="improve_library_coverage",
        script_name="ai_workflows/drivers/improve_library_coverage.py",
        runner_name="run_improve_library_coverage_workflow",
        runner=run_improve_library_coverage_workflow,
        argv=pipeline_argv,
        issue_number=issue_number,
        issue_label=claimed_issue.label,
        coordinates=claimed_issue.issue_coordinates,
        current_coordinates=claimed_issue.current_coordinates,
        new_version=claimed_issue.new_version,
        log_stage_name="improve-coverage-workflow",
        log_message=(
            f"Invoking improve_library_coverage workflow for issue #{issue_number}: "
            f"{claimed_issue.issue_coordinates}"
        ),
        failure_name="improve_library_coverage",
    )


def resolve_claimed_library_update_target(claimed_issue: ClaimedIssue) -> LibraryUpdateTarget:
    """Resolve the requested library-update coordinate against the metadata index."""
    group, artifact, requested_version = metadata_coordinate_parts(
        claimed_issue.issue_coordinates
    )
    if not requested_version:
        raise ValueError(
            "library-update-request coordinates must include a requested version: "
            f"{claimed_issue.issue_coordinates}"
        )
    return resolve_library_update_target(
        claimed_issue.worktree_path,
        group,
        artifact,
        requested_version,
    )


def get_route_result_path_summary(route_result: dict[str, Any]) -> str:
    """Return concise sidecar/log path evidence for router diagnostics."""
    sidecar_json_path = route_result.get("sidecar_json_path") or "not recorded"
    log_paths = route_result.get("log_paths")
    if isinstance(log_paths, list) and log_paths:
        log_path_summary = ", ".join(str(path) for path in log_paths)
        return f"sidecar={sidecar_json_path}; logs={log_path_summary}"
    return f"sidecar={sidecar_json_path}"


def print_library_update_router_setup_failure(
        requested_coordinate: str,
        route_result: dict[str, Any],
        reason: str,
) -> None:
    """Print an actionable setup failure for the missing-version router."""
    setup_error = route_result.get("setup_error")
    setup_detail = f"; setup_error={setup_error}" if setup_error else ""
    print(
        (
            "ERROR: library-update-request compatibility routing failed for "
            f"{requested_coordinate}: {reason}{setup_detail}. "
            f"Inspect {get_route_result_path_summary(route_result)}."
        ),
        file=sys.stderr,
    )


def library_update_route_publication_fields(route_name: str) -> tuple[str | None, str | None]:
    """Return the PR script and label selected for a missing-version route."""
    if route_name == ROUTE_COMPATIBLE:
        return "git_scripts/make_pr_improve_coverage.py", LABEL_PR_LIBRARY_UPDATE
    if route_name == ROUTE_JAVAC_FAILURE:
        return "git_scripts/make_pr_javac_fix.py", LABEL_PR_JAVAC_FIX
    if route_name == ROUTE_JAVA_RUN_FAILURE:
        return "git_scripts/make_pr_java_run_fix.py", LABEL_PR_JAVA_RUN_FIX
    return None, None


def build_library_update_route_lifecycle(
        claimed_issue: ClaimedIssue,
        route_result: dict[str, Any],
        invocation: WorkflowDriverInvocation | None = None,
) -> LibraryUpdateRouteLifecycle:
    """Build the dispatcher lifecycle record for a missing-version route."""
    _group, _artifact, requested_version = metadata_coordinate_parts(
        claimed_issue.issue_coordinates
    )
    selected_route = str(route_result.get("selected_route") or ROUTE_SETUP_FAILURE)
    route_log_paths = route_result.get("log_paths")
    publication_script, publication_label = library_update_route_publication_fields(selected_route)
    return LibraryUpdateRouteLifecycle(
        requested_coordinate=str(
            route_result.get("requested_coordinate") or claimed_issue.issue_coordinates
        ),
        requested_version=requested_version,
        selected_route=selected_route,
        selected_baseline_coordinate=(
            route_result.get("selected_baseline_coordinate")
            if isinstance(route_result.get("selected_baseline_coordinate"), str)
            else None
        ),
        selected_driver_name=invocation.driver_name if invocation is not None else None,
        selected_driver_script=invocation.script_name if invocation is not None else None,
        route_sidecar_json_path=(
            route_result.get("sidecar_json_path")
            if isinstance(route_result.get("sidecar_json_path"), str)
            else None
        ),
        route_log_paths=tuple(str(path) for path in route_log_paths)
        if isinstance(route_log_paths, list)
        else (),
        publication_script=publication_script,
        publication_label=publication_label,
        setup_error=(
            route_result.get("setup_error")
            if isinstance(route_result.get("setup_error"), str)
            else None
        ),
    )


def enrich_library_update_route_metrics(
        route: LibraryUpdateRouteLifecycle | None,
        claimed_issue: ClaimedIssue,
) -> LibraryUpdateRouteLifecycle | None:
    """Attach actual pending and durable metrics paths to the route lifecycle."""
    if route is None:
        return None

    pending_metrics_path = os.path.join(
        claimed_issue.scratch_metrics_repo_path,
        PENDING_METRICS_FILENAME,
    )
    pending_metrics_file_path = pending_metrics_path if os.path.isfile(pending_metrics_path) else None
    durable_metrics_file_path = None
    run_metrics = _load_pending_run_metrics(claimed_issue.scratch_metrics_repo_path)
    if isinstance(run_metrics, dict):
        try:
            durable_metrics_path = execution_metrics_path(claimed_issue.worktree_path, run_metrics)
            if os.path.isfile(durable_metrics_path):
                durable_metrics_file_path = durable_metrics_path
        except Exception:
            durable_metrics_file_path = None

    return replace(
        route,
        pending_metrics_file_path=pending_metrics_file_path,
        durable_metrics_file_path=durable_metrics_file_path,
    )


def build_library_update_java_failure_invocation(
        claimed_issue: ClaimedIssue,
        route_name: str,
        selected_baseline_coordinate: str,
        requested_version: str,
        route_result: dict[str, Any],
) -> WorkflowDriverInvocation:
    """Build the javac/java-run driver invocation selected by the router."""
    issue_number = claimed_issue.issue["number"]
    if route_name == ROUTE_JAVAC_FAILURE:
        driver_name = "fix_javac_fail"
        script_name = "ai_workflows/drivers/fix_javac_fail.py"
        runner_name = "run_fix_javac_workflow"
        runner = run_fix_javac_workflow
        strategy_env_var = "FORGE_JAVAC_STRATEGY_NAME"
        log_stage_name = "javac-fix-workflow"
        failure_name = "fix_javac"
    elif route_name == ROUTE_JAVA_RUN_FAILURE:
        driver_name = "fix_java_run_fail"
        script_name = "ai_workflows/drivers/fix_java_run_fail.py"
        runner_name = "run_fix_java_run_workflow"
        runner = run_fix_java_run_workflow
        strategy_env_var = "FORGE_JAVA_RUN_STRATEGY_NAME"
        log_stage_name = "java-run-fix-workflow"
        failure_name = "fix_java_run"
    else:
        raise ValueError(f"Unsupported library-update router route: {route_name}")

    pipeline_argv = [
        "--coordinates", selected_baseline_coordinate,
        "--new-version", requested_version,
        "--reachability-metadata-path", claimed_issue.worktree_path,
        "--metrics-repo-path", claimed_issue.scratch_metrics_repo_path,
    ]
    route_strategy_name = os.environ.get(strategy_env_var) or None
    if route_strategy_name:
        pipeline_argv.extend(["--strategy-name", route_strategy_name])

    return WorkflowDriverInvocation(
        driver_name=driver_name,
        script_name=script_name,
        runner_name=runner_name,
        runner=runner,
        argv=pipeline_argv,
        issue_number=issue_number,
        issue_label=claimed_issue.label,
        coordinates=None,
        current_coordinates=selected_baseline_coordinate,
        new_version=requested_version,
        log_stage_name=log_stage_name,
        log_message=(
            f"Invoking {driver_name} workflow for issue #{issue_number} via "
            f"library-update router route={route_name}: "
            f"{selected_baseline_coordinate} -> {requested_version}; "
            f"{get_route_result_path_summary(route_result)}"
        ),
        failure_name=failure_name,
    )


def build_workflow_driver_invocation(
        claimed_issue: ClaimedIssue,
        strategy_name: str | None,
        keep_tests_without_dynamic_access: bool,
) -> WorkflowDriverInvocation:
    """Build the routed workflow-driver command for a claimed issue."""
    issue_number = claimed_issue.issue["number"]
    if claimed_issue.label == LABEL_LIBRARY_NEW:
        pipeline_argv = [
            "--coordinates", claimed_issue.issue_coordinates,
            "--reachability-metadata-path", claimed_issue.worktree_path,
            "--metrics-repo-path", claimed_issue.scratch_metrics_repo_path,
        ]
        if strategy_name:
            pipeline_argv.extend(["--strategy-name", strategy_name])
        if keep_tests_without_dynamic_access:
            pipeline_argv.append("--keep-tests-without-dynamic-access")
        append_large_library_workflow_args(pipeline_argv, claimed_issue)
        return WorkflowDriverInvocation(
            driver_name="add_new_library_support",
            script_name="ai_workflows/drivers/add_new_library_support.py",
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
        ]
        if strategy_name:
            pipeline_argv.extend(["--strategy-name", strategy_name])
        return WorkflowDriverInvocation(
            driver_name="fix_javac_fail",
            script_name="ai_workflows/drivers/fix_javac_fail.py",
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
        ]
        if strategy_name:
            pipeline_argv.extend(["--strategy-name", strategy_name])
        return WorkflowDriverInvocation(
            driver_name="fix_java_run_fail",
            script_name="ai_workflows/drivers/fix_java_run_fail.py",
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
        ]
        return WorkflowDriverInvocation(
            driver_name="fix_ni_run",
            script_name="ai_workflows/drivers/fix_ni_run.py",
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
        return build_improve_library_coverage_invocation(
            claimed_issue,
            strategy_name,
        )

    raise ValueError(f"Unknown label '{claimed_issue.label}'")


def build_routed_library_update_invocation(
        claimed_issue: ClaimedIssue,
        strategy_name: str | None,
) -> RoutedWorkflowDriverInvocation:
    """Resolve and route a library-update-request before workflow dispatch."""
    target = resolve_claimed_library_update_target(claimed_issue)
    issue_number = claimed_issue.issue["number"]
    if target.match_type != MATCH_NEW_VERSION:
        log_stage(
            "library-update-router",
            (
                f"Issue #{issue_number} uses existing library-update target "
                f"match_type={target.match_type}; dispatching improve_library_coverage "
                f"for {claimed_issue.issue_coordinates}"
            ),
        )
        return RoutedWorkflowDriverInvocation(
            invocation=build_improve_library_coverage_invocation(
                claimed_issue,
                strategy_name,
            ),
        )

    route_result = probe_missing_version_compatibility(
        claimed_issue.worktree_path,
        claimed_issue.scratch_metrics_repo_path,
        claimed_issue.issue_coordinates,
        issue_number=issue_number,
    )
    route_name = route_result.get("selected_route")
    log_stage(
        "library-update-router",
        (
            f"Issue #{issue_number} missing requested version "
            f"{claimed_issue.issue_coordinates}; selected route={route_name}; "
            f"selected_driver={library_update_route_driver_name(str(route_name))}; "
            f"baseline={route_result.get('selected_baseline_coordinate') or 'none'}; "
            f"{get_route_result_path_summary(route_result)}"
        ),
    )

    if route_name == ROUTE_SETUP_FAILURE:
        print_library_update_router_setup_failure(
            claimed_issue.issue_coordinates,
            route_result,
            "the compatibility probe selected setup-failure",
        )
        return RoutedWorkflowDriverInvocation(
            invocation=None,
            library_update_route=build_library_update_route_lifecycle(
                claimed_issue,
                route_result,
            ),
        )

    if route_name == ROUTE_COMPATIBLE:
        invocation = build_improve_library_coverage_invocation(
            claimed_issue,
            strategy_name,
        )
        return RoutedWorkflowDriverInvocation(
            invocation=invocation,
            library_update_route=build_library_update_route_lifecycle(
                claimed_issue,
                route_result,
                invocation,
            ),
        )

    if route_name not in {ROUTE_JAVAC_FAILURE, ROUTE_JAVA_RUN_FAILURE}:
        print_library_update_router_setup_failure(
            claimed_issue.issue_coordinates,
            route_result,
            f"unknown router route {route_name!r}",
        )
        return RoutedWorkflowDriverInvocation(
            invocation=None,
            library_update_route=build_library_update_route_lifecycle(
                claimed_issue,
                route_result,
            ),
        )

    selected_baseline_coordinate = route_result.get("selected_baseline_coordinate")
    if not isinstance(selected_baseline_coordinate, str) or not selected_baseline_coordinate:
        print_library_update_router_setup_failure(
            claimed_issue.issue_coordinates,
            route_result,
            f"route {route_name} did not provide selected_baseline_coordinate",
        )
        return RoutedWorkflowDriverInvocation(
            invocation=None,
            library_update_route=build_library_update_route_lifecycle(
                claimed_issue,
                route_result,
            ),
        )

    invocation = build_library_update_java_failure_invocation(
        claimed_issue,
        str(route_name),
        selected_baseline_coordinate,
        target.resolved_test_version,
        route_result,
    )
    return RoutedWorkflowDriverInvocation(
        invocation=invocation,
        library_update_route=build_library_update_route_lifecycle(
            claimed_issue,
            route_result,
            invocation,
        ),
    )


def library_update_route_driver_name(route_name: str) -> str:
    """Return the selected driver name for route log evidence."""
    if route_name == ROUTE_JAVAC_FAILURE:
        return "fix_javac_fail"
    if route_name == ROUTE_JAVA_RUN_FAILURE:
        return "fix_java_run_fail"
    if route_name == ROUTE_COMPATIBLE:
        return "improve_library_coverage"
    if route_name == ROUTE_SETUP_FAILURE:
        return "none"
    return "unknown"


def run_workflow_driver_invocation(invocation: WorkflowDriverInvocation) -> bool:
    """Run a workflow driver invocation with dispatcher-owned result handling."""
    print()
    log_stage(invocation.log_stage_name, invocation.log_message)
    rc = invocation.runner(invocation.argv)
    if is_interrupt_exit_code(rc):
        mark_user_interrupt_requested()
        raise KeyboardInterrupt
    if rc != 0:
        print(
            (
                f"ERROR: {invocation.failure_name} workflow failed for issue "
                f"#{invocation.issue_number} (exit {rc})"
            ),
            file=sys.stderr,
        )
        return False

    print()
    log_stage("pipeline", f"Pipeline succeeded for issue #{invocation.issue_number}")
    return True


def invoke_pipeline(
        claimed_issue: ClaimedIssue,
        strategy_name: str | None,
        keep_tests_without_dynamic_access: bool,
) -> WorkflowExecutionOutcome:
    """Run the matching workflow driver in the claimed issue's worktree.

    Label-based routing stays in the dispatcher (§AR-forge-control-plane);
    workflow drivers then own the run end to end
    (§AR-forge-workflow-boundary), receiving resolved coordinates, paths,
    strategy names, and chunk context.
    """
    require_claimed_issue_worktree(claimed_issue, "workflow execution")
    if claimed_issue.label == LABEL_LIBRARY_UPDATE:
        routed_invocation = build_routed_library_update_invocation(
            claimed_issue,
            strategy_name,
        )
        invocation = routed_invocation.invocation
        if invocation is None:
            return WorkflowExecutionOutcome(
                success=False,
                routed_driver=None,
                library_update_route=routed_invocation.library_update_route,
            )
        success = run_workflow_driver_invocation(invocation)
        return WorkflowExecutionOutcome(
            success=success,
            routed_driver=invocation,
            library_update_route=enrich_library_update_route_metrics(
                routed_invocation.library_update_route,
                claimed_issue,
            ),
        )

    invocation = build_workflow_driver_invocation(
        claimed_issue,
        strategy_name,
        keep_tests_without_dynamic_access,
    )
    return WorkflowExecutionOutcome(
        success=run_workflow_driver_invocation(invocation),
        routed_driver=invocation,
    )


def validate_parallelism(value: str) -> int:
    """Parse and validate the allowed forge_metadata parallelism."""
    parsed = int(value)
    if parsed < 1 or parsed > MAX_PARALLELISM:
        raise argparse.ArgumentTypeError(
            f"parallelism must be between 1 and {MAX_PARALLELISM}"
        )
    return parsed


def validate_non_negative_integer(value: str) -> int:
    """Parse and validate a non-negative integer argument."""
    parsed = int(value)
    if parsed < 0:
        raise argparse.ArgumentTypeError("value must be greater than or equal to 0")
    return parsed


def validate_review_period(value: str) -> int:
    """Parse a positive review period in seconds, supporting s/m/h/d suffixes."""
    normalized = value.strip().lower()
    match = re.fullmatch(r"(\d+)([smhd]?)", normalized)
    if match is None:
        raise argparse.ArgumentTypeError(
            "period must be a positive integer in seconds or use s/m/h/d suffixes"
        )

    amount = int(match.group(1))
    if amount < 1:
        raise argparse.ArgumentTypeError("period must be greater than 0")

    suffix = match.group(2) or "s"
    return amount * REVIEW_PERIOD_SUFFIX_SECONDS[suffix]


def get_env_non_negative_int(name: str, default: int) -> int:
    """Read a non-negative integer environment variable."""
    raw_value = os.environ.get(name)
    if raw_value is None or raw_value == "":
        return default
    try:
        value = int(raw_value)
    except ValueError:
        print(f"ERROR: {name} must be a non-negative integer.", file=sys.stderr)
        sys.exit(1)
    if value < 0:
        print(f"ERROR: {name} must be a non-negative integer.", file=sys.stderr)
        sys.exit(1)
    return value


def get_env_parallelism(name: str, default: int) -> int:
    """Read an optional parallelism environment variable."""
    raw_value = os.environ.get(name)
    if raw_value is None or raw_value == "":
        return default
    try:
        return validate_parallelism(raw_value)
    except (ValueError, argparse.ArgumentTypeError) as exc:
        print(f"ERROR: {name} {exc}", file=sys.stderr)
        sys.exit(1)


def get_env_zero_one_bool(name: str, default: bool) -> bool:
    """Read an optional 0-or-1 boolean environment variable."""
    raw_value = os.environ.get(name)
    if raw_value is None or raw_value == "":
        return default
    if raw_value not in {"0", "1"}:
        print(f"ERROR: {name} must be 0 or 1.", file=sys.stderr)
        sys.exit(1)
    return raw_value == "1"


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
    review_model = os.environ.get("FORGE_REVIEW_MODEL", DEFAULT_REVIEW_MODEL)
    if review_label:
        return [
            ReviewQueueConfig(
                label=review_label,
                limit=review_limit,
                model=review_model,
            )
        ]

    return [
        ReviewQueueConfig(
            label=LABEL_LIBRARY_NEW,
            limit=get_env_non_negative_int("FORGE_LIBRARY_REVIEW_LIMIT", review_limit),
            model=review_model,
        ),
        ReviewQueueConfig(
            label=LABEL_PR_JAVAC_FIX,
            limit=get_env_non_negative_int("FORGE_JAVAC_REVIEW_LIMIT", review_limit),
            model=review_model,
        ),
        ReviewQueueConfig(
            label=LABEL_PR_JAVA_RUN_FIX,
            limit=get_env_non_negative_int("FORGE_JAVA_RUN_REVIEW_LIMIT", review_limit),
            model=review_model,
        ),
        ReviewQueueConfig(
            label=LABEL_PR_NI_RUN_FIX,
            limit=get_env_non_negative_int("FORGE_NI_RUN_REVIEW_LIMIT", review_limit),
            model=review_model,
        ),
        ReviewQueueConfig(
            label=LABEL_PR_LIBRARY_UPDATE,
            limit=get_env_non_negative_int("FORGE_LIBRARY_UPDATE_REVIEW_LIMIT", review_limit),
            model=review_model,
        ),
        ReviewQueueConfig(
            label=LABEL_PR_LIBRARY_BULK_UPDATE,
            limit=get_env_non_negative_int("FORGE_BULK_UPDATE_REVIEW_LIMIT", review_limit),
            model=review_model,
        ),
    ]


def validate_work_queue_strategies(queue_configs: list[WorkQueueConfig]) -> None:
    """Validate strategy names configured for enabled issue queues."""
    seen_strategy_names: set[str] = set()
    for queue_config in queue_configs:
        strategy_name = queue_config.strategy_name
        if queue_config.limit <= 0 or not strategy_name or strategy_name in seen_strategy_names:
            continue
        require_strategy_by_name(strategy_name)
        seen_strategy_names.add(strategy_name)


def run_pull_request_review_loop(
        label: str,
        limit: int,
        reachability_metadata_path: str,
        authenticated_user: str,
        review_model: str,
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
            review_model,
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


def fetch_review_base_ref(repo_path: str) -> None:
    """Refresh the upstream PR base ref used by local review diffs."""
    remote_tracking_ref = f"refs/remotes/origin/{DEFAULT_WORKTREE_BASE_REF}"
    try:
        subprocess.run(
            [
                "git",
                "fetch",
                "--quiet",
                "origin",
                f"+{DEFAULT_WORKTREE_BASE_REF}:{remote_tracking_ref}",
            ],
            cwd=repo_path,
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
        )
    except subprocess.CalledProcessError as exc:
        print(
            f"ERROR: Failed to fetch origin/{DEFAULT_WORKTREE_BASE_REF} before PR review: {exc.stdout}",
            file=sys.stderr,
        )
        raise


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


def create_issue_workspace(
        base_reachability_metadata_path: str,
        canonical_metrics_repo_path: str,
        issue_number: int,
) -> tuple[str, str]:
    """Create isolated worktrees for reachability-metadata and metrics storage."""
    repo_root = get_repo_root()
    worktrees_root = os.path.join(repo_root, "local_repositories", SCRATCH_WORKTREE_DIRNAME)
    os.makedirs(worktrees_root, exist_ok=True)

    run_id = build_issue_run_id(issue_number)
    worktree_path = os.path.join(worktrees_root, run_id)

    create_detached_worktree(
        base_reachability_metadata_path,
        worktree_path,
        DEFAULT_WORKTREE_BASE_REF,
        f"Failed to create worktree for issue #{issue_number}",
    )
    try:
        require_complete_reachability_repo(worktree_path)
    except SystemExit:
        remove_worktree(base_reachability_metadata_path, worktree_path)
        raise

    scratch_metrics_repo_path = os.path.join(worktree_path, get_forge_subdir_name())
    copy_progress_artifacts(canonical_metrics_repo_path, scratch_metrics_repo_path, issue_number)
    return worktree_path, scratch_metrics_repo_path


def cleanup_issue_workspace(claimed_issue: ClaimedIssue, canonical_metrics_repo_path: str) -> None:
    """Remove the isolated issue worktrees for reachability-metadata and metrics."""
    copy_progress_artifacts(
        claimed_issue.scratch_metrics_repo_path,
        canonical_metrics_repo_path,
        claimed_issue.issue["number"],
    )
    if claimed_issue.worktree_path in preservation_failed_worktree_paths:
        print(
            (
                f"[Keeping worktree for issue #{claimed_issue.issue['number']} because failed work "
                "could not be pushed; cleanup skipped.]"
            ),
            file=sys.stderr,
        )
    else:
        remove_worktree(claimed_issue.base_reachability_metadata_path, claimed_issue.worktree_path)


def build_claim_metadata(
        issue: dict,
        label: str,
        base_reachability_metadata_path: str,
) -> Optional[tuple[str, str | None, str | None]]:
    """Resolve the coordinates needed to execute a claimed issue."""
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


def resolve_large_library_resume_artifact(issue: dict, canonical_metrics_repo_path: str) -> str | None:
    """Resolve a durable resume artifact for a large-library continuation issue."""
    if not issue_has_label(issue, LABEL_LARGE_LIBRARY_NEXT_PART):
        return None
    state_path = find_progress_state_path(canonical_metrics_repo_path, issue["number"])
    if state_path is None:
        raise RuntimeError(f"No large-library progress state found for issue #{issue['number']}")
    state = LargeLibraryProgressState.load(state_path)
    verify_large_library_previous_part_merged(state)
    return state_path


def verify_large_library_previous_part_merged(state: LargeLibraryProgressState) -> None:
    """Fail fast when continuation is requested before the previous PR merged."""
    if not state.created_pull_requests:
        return
    if is_fixture_testing_enabled():
        log_stage(
            "large-library",
            (
                f"Fixture mode: treating previous large-library PR "
                f"#{state.created_pull_requests[-1]} as merged."
            ),
        )
        return
    previous_pr = state.created_pull_requests[-1]
    result = gh(
        "pr",
        "view",
        str(previous_pr),
        "--repo",
        REPO,
        "--json",
        "state,merged",
        check=True,
    )
    payload = json.loads(result.stdout)
    if not payload.get("merged"):
        raise RuntimeError(
            f"Large-library series {state.series_id} cannot continue before PR #{previous_pr} is merged "
            f"(state={payload.get('state')})."
        )


def verify_large_library_base_contains_published_commit(
        state: LargeLibraryProgressState,
        worktree_path: str,
) -> None:
    """Ensure the continuation worktree includes the latest published part commit."""
    commit = resolve_large_library_published_base_commit(state)
    if not commit:
        return
    result = subprocess.run(
        ["git", "merge-base", "--is-ancestor", commit, "HEAD"],
        cwd=worktree_path,
        check=False,
    )
    if result.returncode != 0:
        raise RuntimeError(
            f"Base branch does not contain latest large-library published commit {commit}"
        )


def resolve_large_library_published_base_commit(state: LargeLibraryProgressState) -> str | None:
    """Return the commit that should be present on the continuation base."""
    if is_fixture_testing_enabled():
        return state.last_published_commit
    if state.created_pull_requests:
        previous_pr = state.created_pull_requests[-1]
        result = gh(
            "pr",
            "view",
            str(previous_pr),
            "--repo",
            REPO,
            "--json",
            "mergeCommit",
            check=True,
        )
        payload = json.loads(result.stdout)
        merge_commit = payload.get("mergeCommit") or {}
        oid = merge_commit.get("oid")
        if oid:
            return str(oid)
    return state.last_published_commit


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


def claim_issue_for_processing(
        issue: dict,
        label: str,
        base_reachability_metadata_path: str,
        canonical_metrics_repo_path: str,
        authenticated_user: str,
        large_library_resume_artifact_override: str | None = None,
        take_blocked_issues: bool = DEFAULT_TAKE_BLOCKED_ISSUES,
) -> Optional[ClaimedIssue]:
    """Claim an issue and prepare its isolated execution workspace.

    Claiming, assignment validation, and worktree creation are orchestration
    responsibilities, not strategy logic (§AR-forge-control-plane); resume-artifact
    transfer carries the exhaust report forward between chunks
    (§WF-dynamic-access-exhaust-report).
    """
    if not refresh_issue_payload_for_claim(issue, label, authenticated_user):
        return None

    if maybe_handle_not_for_native_image_issue(issue, base_reachability_metadata_path):
        return None

    claim_metadata = build_claim_metadata(issue, label, base_reachability_metadata_path)
    if claim_metadata is None:
        return None
    try:
        large_library_resume_artifact = (
            large_library_resume_artifact_override
            or resolve_large_library_resume_artifact(issue, canonical_metrics_repo_path)
        )
        large_library_part = None
        if large_library_resume_artifact:
            large_library_part = LargeLibraryProgressState.load(large_library_resume_artifact).part
    except Exception as exc:
        print(
            f"ERROR: Cannot resume large-library issue #{issue['number']}: {exc}",
            file=sys.stderr,
        )
        return None

    item_id = try_claim_issue(issue, authenticated_user, label, take_blocked_issues)
    if not item_id:
        return None

    try:
        worktree_path, scratch_metrics_repo_path = create_issue_workspace(
            base_reachability_metadata_path,
            canonical_metrics_repo_path,
            issue["number"],
        )
        if large_library_resume_artifact:
            large_library_state = LargeLibraryProgressState.load(large_library_resume_artifact)
            large_library_state.save(large_library_state.default_path(scratch_metrics_repo_path))
            verify_large_library_base_contains_published_commit(large_library_state, worktree_path)
    except BaseException as exc:
        revert_issue_claim(
            item_id,
            issue["number"],
            "claim setup interrupted by Ctrl+C" if is_interrupt_exception(exc)
            else f"claim setup failure ({type(exc).__name__})",
        )
        if isinstance(exc, Exception):
            return None
        raise

    issue_coordinates, current_coordinates, new_version = claim_metadata
    return ClaimedIssue(
        issue=issue,
        label=label,
        item_id=item_id,
        base_reachability_metadata_path=base_reachability_metadata_path,
        worktree_path=worktree_path,
        scratch_metrics_repo_path=scratch_metrics_repo_path,
        issue_coordinates=issue_coordinates,
        current_coordinates=current_coordinates,
        new_version=new_version,
        large_library_resume_artifact=large_library_resume_artifact,
        large_library_part=large_library_part,
    )


def run_claimed_issue(
        claimed_issue: ClaimedIssue,
        strategy_name: str | None,
        keep_tests_without_dynamic_access: bool,
) -> WorkflowRunResult:
    """Execute the claimed issue workflow inside its isolated worktree."""
    started_at = time.time()
    active_claimed_issue = claimed_issue
    outcome = WorkflowExecutionOutcome(success=False)
    try:
        outcome = invoke_pipeline(
            active_claimed_issue,
            strategy_name,
            keep_tests_without_dynamic_access,
        )
    except KeyboardInterrupt:
        mark_user_interrupt_requested()
        raise
    except Exception as exc:
        if is_user_interrupt_requested():
            raise KeyboardInterrupt from exc
        print(
            f"ERROR: Issue #{active_claimed_issue.issue['number']} workflow raised an exception: {exc!r}",
            file=sys.stderr,
        )
        traceback.print_exc()
    if is_user_interrupt_requested():
        raise KeyboardInterrupt
    return WorkflowRunResult(
        claimed_issue=active_claimed_issue,
        success=outcome.success,
        started_at=started_at,
        routed_driver=outcome.routed_driver,
        library_update_route=outcome.library_update_route,
    )


def revert_issue_claim(item_id: str, issue_number: int, reason: str) -> None:
    """Reset an issue claim back to Todo and clear all assignment, with verification."""
    print(f"\n[issue-revert] Reverting issue #{issue_number} claim because {reason}", file=sys.stderr)
    revert_errors: list[Exception] = []
    print(f"[issue-revert] Reverting issue #{issue_number}: setting project item {item_id} -> {STATUS_TODO}", file=sys.stderr)
    try:
        set_item_status(item_id, STATUS_TODO)
    except Exception as exc:
        revert_errors.append(exc)
        print(
            f"ERROR: Issue #{issue_number} revert could not set project item {item_id} "
            f"to {STATUS_TODO}: {format_github_exception_details(exc)}",
            file=sys.stderr,
        )
    print(f"[issue-revert] Reverting issue #{issue_number}: clearing all assignees", file=sys.stderr)
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
    print(
        f"ERROR: Issue #{issue_number} failed due to {reason}; verified revert with "
        f"status={verified_status}, assignees={verified_assignees}",
        file=sys.stderr,
    )


def revert_claimed_issue(claimed_issue: ClaimedIssue, reason: str) -> None:
    """Reset a failed claimed issue back to Todo and clear its assignment."""
    revert_issue_claim(claimed_issue.item_id, claimed_issue.issue["number"], reason)


def ensure_large_library_labels_exist_if_needed(claimed_issue: ClaimedIssue) -> None:
    """Ensure supplemental large-library labels exist before PR creation uses them."""
    label_descriptions = {
        LABEL_LARGE_LIBRARY_SERIES: LARGE_LIBRARY_SERIES_LABEL_DESCRIPTION,
        LABEL_LARGE_LIBRARY_NEXT_PART: LARGE_LIBRARY_NEXT_PART_LABEL_DESCRIPTION,
        LABEL_LARGE_LIBRARY_BLOCKED: LARGE_LIBRARY_BLOCKED_LABEL_DESCRIPTION,
        LABEL_LARGE_LIBRARY_PART: LARGE_LIBRARY_PART_LABEL_DESCRIPTION,
    }
    for label_name, description in label_descriptions.items():
        ensure_repo_label_exists(label_name, LARGE_LIBRARY_LABEL_COLOR, description)


def build_large_library_pr_args(
        claimed_issue: ClaimedIssue,
        large_library_state: LargeLibraryProgressState | None,
        large_library_state_path: str | None,
        workflow_status: str | None,
) -> list[str]:
    """Build part-aware PR flags for an active large-library series.

    Non-final chunks publish with continuation state and must not close the
    backing issue, per the chunk PR linking contract
    (§WF-chunked-dynamic-access-pr-linking, §GIT-issue-linking).
    """
    if large_library_state is None:
        return []
    args = [
        "--large-library-part", str(large_library_state.part),
        "--series-id", large_library_state.series_id,
        "--large-library-state-path", large_library_state_path or "",
    ]
    if workflow_status != RUN_STATUS_CHUNK_READY:
        args.append("--large-library-final")
    return args


def route_uses_java_failure_publication(route: LibraryUpdateRouteLifecycle | None) -> bool:
    """Return True when a library-update route selected a Java fail-fix lane."""
    return route is not None and route.selected_route in {
        ROUTE_JAVAC_FAILURE,
        ROUTE_JAVA_RUN_FAILURE,
    }


def publication_allows_large_library_args(
        claimed_issue: ClaimedIssue,
        route: LibraryUpdateRouteLifecycle | None,
) -> bool:
    """Return whether publication should include dynamic-access chunk metadata."""
    if claimed_issue.label == LABEL_LIBRARY_NEW:
        return True
    if claimed_issue.label != LABEL_LIBRARY_UPDATE:
        return False
    if route is None:
        return True
    return (
        route.selected_route == ROUTE_COMPATIBLE
        and route.selected_driver_name == "improve_library_coverage"
    )


def _require_publication_value(value: str | None, field_name: str, claimed_issue: ClaimedIssue) -> str:
    if value is None:
        raise ValueError(
            f"Issue #{claimed_issue.issue['number']} {claimed_issue.label} publication requires {field_name}."
        )
    return value


def build_publication_handoff(
        claimed_issue: ClaimedIssue,
        library_update_route: LibraryUpdateRouteLifecycle | None = None,
) -> PublicationHandoff:
    """Build the live-or-fixture PR publication handoff.

    The dispatcher makes the routing decision once, then either executes the
    matching git script or records a dry-run fixture handoff
    (§E2E-forge-workflow-testing.2, §AR-forge-verification-publication-boundary).
    """
    require_claimed_issue_worktree(claimed_issue, "successful finalization")
    issue_number = claimed_issue.issue["number"]
    run_metrics = _load_pending_run_metrics(claimed_issue.scratch_metrics_repo_path)
    workflow_status = None if run_metrics is None else run_metrics.get("status")
    issue_number_args = ["--issue-number", str(issue_number)]
    include_large_library_args = publication_allows_large_library_args(
        claimed_issue,
        library_update_route,
    )
    large_library_state_path = None
    large_library_state = None
    if include_large_library_args:
        large_library_state_path = find_progress_state_path(
            claimed_issue.scratch_metrics_repo_path,
            issue_number,
        )
        large_library_state = (
            LargeLibraryProgressState.load(large_library_state_path)
            if large_library_state_path
            else None
        )
        if large_library_state is not None:
            ensure_large_library_labels_exist_if_needed(claimed_issue)
    large_library_pr_args = build_large_library_pr_args(
        claimed_issue,
        large_library_state,
        large_library_state_path,
        workflow_status,
    )
    large_library_final = None
    if large_library_state is not None:
        large_library_final = workflow_status != RUN_STATUS_CHUNK_READY

    script_name: str
    runner_name: str
    runner: Callable[[list[str]], None]
    argv: list[str]
    result_label: str
    coordinates: str | None = claimed_issue.issue_coordinates
    current_coordinates: str | None = claimed_issue.current_coordinates
    new_version: str | None = claimed_issue.new_version
    not_for_native_image = False

    if claimed_issue.label == LABEL_LIBRARY_NEW:
        group, artifact, _version = metadata_coordinate_parts(claimed_issue.issue_coordinates)
        not_for_native_image = is_not_for_native_image(claimed_issue.worktree_path, group, artifact)
        if not_for_native_image:
            script_name = "git_scripts/make_pr_not_for_native_image.py"
            runner_name = "run_make_pr_not_for_native_image"
            runner = run_make_pr_not_for_native_image
            result_label = LABEL_NOT_FOR_NATIVE_IMAGE
            argv = [
                "--coordinates", claimed_issue.issue_coordinates,
                *issue_number_args,
                "--reachability-metadata-path", claimed_issue.worktree_path,
                "--metrics-repo-path", claimed_issue.scratch_metrics_repo_path,
            ]
        else:
            script_name = "git_scripts/make_pr_new_library_support.py"
            runner_name = "run_make_pr_new_library_support"
            runner = run_make_pr_new_library_support
            result_label = LABEL_LIBRARY_NEW
            argv = [
                "--coordinates", claimed_issue.issue_coordinates,
                *issue_number_args,
                "--reachability-metadata-path", claimed_issue.worktree_path,
                "--metrics-repo-path", claimed_issue.scratch_metrics_repo_path,
                *large_library_pr_args,
            ]
    elif claimed_issue.label == LABEL_JAVAC_FAIL:
        script_name = "git_scripts/make_pr_javac_fix.py"
        runner_name = "run_make_pr_javac_fix"
        runner = run_make_pr_javac_fix
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
        ]
    elif claimed_issue.label == LABEL_JAVA_RUN_FAIL:
        script_name = "git_scripts/make_pr_java_run_fix.py"
        runner_name = "run_make_pr_java_run_fix"
        runner = run_make_pr_java_run_fix
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
        ]
    elif claimed_issue.label == LABEL_NI_RUN_FAIL:
        script_name = "git_scripts/make_pr_ni_run_fix.py"
        runner_name = "run_make_pr_ni_run_fix"
        runner = run_make_pr_ni_run_fix
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
        if library_update_route is not None and library_update_route.selected_route == ROUTE_JAVAC_FAILURE:
            script_name = "git_scripts/make_pr_javac_fix.py"
            runner_name = "run_make_pr_javac_fix"
            runner = run_make_pr_javac_fix
            result_label = LABEL_PR_JAVAC_FIX
            current_coordinates = _require_publication_value(
                library_update_route.selected_baseline_coordinate,
                "selected_baseline_coordinate",
                claimed_issue,
            )
            new_version = _require_publication_value(
                library_update_route.requested_version,
                "requested_version",
                claimed_issue,
            )
            coordinates = None
            argv = [
                "--coordinates", current_coordinates,
                "--new-version", new_version,
                "--issue-number", str(issue_number),
                "--reachability-metadata-path", claimed_issue.worktree_path,
                "--metrics-repo-path", claimed_issue.scratch_metrics_repo_path,
            ]
        elif library_update_route is not None and library_update_route.selected_route == ROUTE_JAVA_RUN_FAILURE:
            script_name = "git_scripts/make_pr_java_run_fix.py"
            runner_name = "run_make_pr_java_run_fix"
            runner = run_make_pr_java_run_fix
            result_label = LABEL_PR_JAVA_RUN_FIX
            current_coordinates = _require_publication_value(
                library_update_route.selected_baseline_coordinate,
                "selected_baseline_coordinate",
                claimed_issue,
            )
            new_version = _require_publication_value(
                library_update_route.requested_version,
                "requested_version",
                claimed_issue,
            )
            coordinates = None
            argv = [
                "--coordinates", current_coordinates,
                "--new-version", new_version,
                "--issue-number", str(issue_number),
                "--reachability-metadata-path", claimed_issue.worktree_path,
                "--metrics-repo-path", claimed_issue.scratch_metrics_repo_path,
            ]
        elif library_update_route is not None and library_update_route.selected_route != ROUTE_COMPATIBLE:
            raise ValueError(
                "Unsupported library-update route for successful publication: "
                f"{library_update_route.selected_route}"
            )
        else:
            script_name = "git_scripts/make_pr_improve_coverage.py"
            runner_name = "run_make_pr_improve_coverage"
            runner = run_make_pr_improve_coverage
            result_label = LABEL_PR_LIBRARY_UPDATE
            coordinates = (
                library_update_route.requested_coordinate
                if library_update_route is not None
                else claimed_issue.issue_coordinates
            )
            argv = [
                "--coordinates", coordinates,
                *issue_number_args,
                "--reachability-metadata-path", claimed_issue.worktree_path,
                "--metrics-repo-path", claimed_issue.scratch_metrics_repo_path,
                *large_library_pr_args,
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
        large_library_args=large_library_pr_args,
        large_library_state_path=large_library_state_path,
        large_library_part=None if large_library_state is None else large_library_state.part,
        large_library_final=large_library_final,
        large_library_series_id=None if large_library_state is None else large_library_state.series_id,
        not_for_native_image=not_for_native_image,
    )


def apply_large_library_completion_follow_up(
        claimed_issue: ClaimedIssue,
        library_update_route: LibraryUpdateRouteLifecycle | None = None,
) -> None:
    """Apply issue labels after a large-library part was published."""
    if not publication_allows_large_library_args(claimed_issue, library_update_route):
        return
    run_metrics = _load_pending_run_metrics(claimed_issue.scratch_metrics_repo_path)
    workflow_status = None if run_metrics is None else run_metrics.get("status")
    state_path = find_progress_state_path(claimed_issue.scratch_metrics_repo_path, claimed_issue.issue["number"])
    if state_path is None and not issue_has_label(claimed_issue.issue, LABEL_LARGE_LIBRARY_SERIES):
        return
    issue_number = claimed_issue.issue["number"]
    if workflow_status == RUN_STATUS_CHUNK_READY:
        add_issue_label(issue_number, LABEL_LARGE_LIBRARY_SERIES)
        add_issue_label(issue_number, LABEL_LARGE_LIBRARY_NEXT_PART)
        remove_issue_label(issue_number, LABEL_LARGE_LIBRARY_BLOCKED)
        return
    remove_issue_label(issue_number, LABEL_LARGE_LIBRARY_NEXT_PART)
    remove_issue_label(issue_number, LABEL_LARGE_LIBRARY_BLOCKED)


def finalize_successful_issue(
        claimed_issue: ClaimedIssue,
        library_update_route: LibraryUpdateRouteLifecycle | None = None,
) -> None:
    """Create the PR for a successful isolated workflow run.

    Publication is delegated to workflow-specific git scripts only after the
    workflow records a PR-eligible status (§GIT-pr-eligibility), keeping
    generation and publication separate (§AR-forge-verification-publication-boundary).
    """
    if is_fixture_testing_enabled():
        handoff = build_publication_handoff(claimed_issue, library_update_route)
        require_fixture_github_state().record_publication_handoff(
            handoff.issue_number,
            handoff.to_json(),
        )
        log_stage(
            "publication",
            (
                f"Fixture mode: recorded dry-run publication handoff for issue "
                f"#{handoff.issue_number} to {handoff.script_name}."
            ),
        )
        return

    handoff = build_publication_handoff(claimed_issue, library_update_route)
    handoff.runner(handoff.argv)


def preserve_failed_work_for_follow_up(
        claimed_issue: ClaimedIssue,
        library_update_route: LibraryUpdateRouteLifecycle | None = None,
) -> FailurePreservationResult | None:
    """Push failed work to a branch for human follow-up."""
    if is_fixture_testing_enabled():
        preservation_result = build_fixture_failure_preservation_result(
            claimed_issue,
            library_update_route,
        )
        preservation_failed_worktree_paths.add(claimed_issue.worktree_path)
        require_fixture_github_state().record_failure_preservation(
            claimed_issue.issue["number"],
            preservation_result.to_json(),
        )
        log_stage(
            "preserve-failed-work",
            (
                f"Fixture mode: recorded dry-run failure preservation branch "
                f"{preservation_result.branch_name} for issue #{claimed_issue.issue['number']}."
            ),
        )
        return preservation_result
    try:
        return preserve_failed_work_branch(claimed_issue, library_update_route)
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


def handle_failed_claimed_issue(
        claimed_issue: ClaimedIssue,
        reason: str,
        started_at: float | None = None,
        library_update_route: LibraryUpdateRouteLifecycle | None = None,
) -> None:
    """Preserve failed work, run Codex analysis, apply follow-up, and revert the claim."""
    if is_user_interrupt_requested():
        raise KeyboardInterrupt
    preservation_result = preserve_failed_work_for_follow_up(
        claimed_issue,
        library_update_route,
    )
    if is_user_interrupt_requested():
        raise KeyboardInterrupt
    apply_failed_run_follow_up(
        claimed_issue,
        started_at=started_at,
        preservation_result=preservation_result,
        library_update_route=library_update_route,
    )
    try:
        refresh_preserved_branch_logs(
            claimed_issue,
            preservation_result,
            library_update_route,
        )
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
    library_update_route = run_result.library_update_route
    try:
        if is_user_interrupt_requested():
            raise KeyboardInterrupt
        if not run_result.success:
            handle_failed_claimed_issue(
                claimed_issue,
                "workflow failure",
                started_at=run_result.started_at,
                library_update_route=library_update_route,
            )
            return False
        try:
            finalize_successful_issue(claimed_issue, library_update_route)
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
                library_update_route=library_update_route,
            )
            return False
        apply_large_library_completion_follow_up(claimed_issue, library_update_route)
        maybe_apply_human_intervention_follow_up(
            claimed_issue,
            workflow_success=True,
            started_at=run_result.started_at,
            library_update_route=library_update_route,
        )
        return True
    except KeyboardInterrupt:
        mark_user_interrupt_requested()
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
        fixture_report_context: FixtureE2EReportContext | None = None,
) -> bool:
    """Run workflow, finalize or revert, and always clean up the claimed issue workspace."""
    lifecycle_completed = False
    lifecycle_result = "started"
    started_at = time.time()
    active_claimed_issue = claimed_issue
    run_result: WorkflowRunResult | None = None
    try:
        run_result = run_claimed_issue(
            claimed_issue,
            strategy_name,
            keep_tests_without_dynamic_access,
        )
        active_claimed_issue = run_result.claimed_issue
        if is_user_interrupt_requested():
            raise KeyboardInterrupt
        handled = handle_completed_run(run_result)
        lifecycle_completed = True
        if handled:
            lifecycle_result = "success"
            log_success_banner(
                append_fixture_report_path(
                    format_issue_result_message(
                        active_claimed_issue,
                        "Workflow finished, follow-up completed, and the issue was finalized.",
                    ),
                    fixture_report_context,
                )
            )
        else:
            lifecycle_result = "failure"
            log_failure_banner(
                append_fixture_report_path(
                    format_issue_result_message(
                        active_claimed_issue,
                        (
                            "Workflow failed; failure follow-up was attempted. Check prior errors "
                            "for follow-up and claim status."
                        ),
                    ),
                    fixture_report_context,
                ),
                file=sys.stderr,
            )
        return handled
    except BaseException as exc:
        if not lifecycle_completed:
            if is_interrupt_exception(exc) or is_user_interrupt_requested():
                lifecycle_result = "interrupted"
                mark_user_interrupt_requested()
                revert_claimed_issue(active_claimed_issue, "Ctrl+C interrupt")
                raise
            else:
                lifecycle_result = f"unhandled-lifecycle-failure:{type(exc).__name__}"
                try:
                    handle_failed_claimed_issue(
                        active_claimed_issue,
                        f"unhandled lifecycle failure ({type(exc).__name__})",
                        started_at=started_at,
                        library_update_route=(
                            run_result.library_update_route
                            if run_result is not None
                            else None
                        ),
                    )
                    log_failure_banner(
                        append_fixture_report_path(
                            format_issue_result_message(
                                active_claimed_issue,
                                (
                                    "Workflow failed with an unhandled lifecycle error; failure follow-up "
                                    "was attempted and the issue claim was reverted."
                                ),
                            ),
                            fixture_report_context,
                        ),
                        file=sys.stderr,
                    )
                except KeyboardInterrupt:
                    lifecycle_result = "interrupted"
                    mark_user_interrupt_requested()
                    revert_claimed_issue(active_claimed_issue, "Ctrl+C interrupt")
                    raise
                return False
        raise
    finally:
        write_fixture_e2e_report_best_effort(
            fixture_report_context,
            active_claimed_issue,
            lifecycle_result,
            started_at,
            run_result,
        )
        try:
            cleanup_issue_workspace(active_claimed_issue, canonical_metrics_repo_path)
        except Exception as exc:
            print(
                f"ERROR: Failed to clean up workspaces for issue #{active_claimed_issue.issue['number']}: {exc!r}",
                file=sys.stderr,
            )
            traceback.print_exc()


def get_open_blocking_issue_numbers(issue_number: int) -> list[int]:
    """Return the numbers of currently open issues that block the given issue."""
    if is_fixture_testing_enabled():
        return require_fixture_github_state().get_open_blocking_issue_numbers(issue_number)
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
    if issue_has_label(issue, LABEL_HUMAN_INTERVENTION):
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
    """Refresh mutable issue state and return whether it remains claimable."""
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
    if is_fixture_testing_enabled():
        fixture_state = require_fixture_github_state()
        preflights: dict[int, IssueClaimPreflight] = {}
        for issue_number in list(dict.fromkeys(issue_numbers)):
            item_id, project_status = fixture_state.get_project_item_state(issue_number)
            preflights[issue_number] = IssueClaimPreflight(
                issue_number=issue_number,
                item_id=item_id,
                project_status=project_status,
                assignees=tuple(fixture_state.get_issue_assignees(issue_number)),
                open_blockers=tuple(fixture_state.get_open_blocking_issue_numbers(issue_number)),
                complete=True,
            )
        return preflights

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
    if issue_has_label(issue, LABEL_HUMAN_INTERVENTION):
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

    cached_large_library_continuation = (
        cached_skip is not None
        and cached_skip.reason == ISSUE_CLAIM_CACHE_REASON_IN_PROGRESS
        and issue_has_label(issue, LABEL_LARGE_LIBRARY_NEXT_PART)
    )
    if (
            cached_skip is not None
            and not cached_large_library_continuation
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

    large_library_continuation = (
        issue_has_label(issue, LABEL_LARGE_LIBRARY_NEXT_PART)
        and preflight.project_status == STATUS_IN_PROGRESS
    )
    if preflight.project_status != STATUS_TODO and not large_library_continuation:
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


def is_issue_blocked(issue_number: int) -> bool:
    """Return True when the issue has at least one currently open blocking issue."""
    return bool(get_open_blocking_issue_numbers(issue_number))


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
            try_mark_issue_numbers_blocking_many_libraries_as_priority(open_blockers)
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

    large_library_continuation = (
        issue_has_label(issue, LABEL_LARGE_LIBRARY_NEXT_PART)
        and current_status == STATUS_IN_PROGRESS
    )
    if current_status != STATUS_TODO and not large_library_continuation:
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
        print()
        log_stage("issue-claim", f"Setting issue #{number} assignee to {authenticated_user}")
        set_issue_assignee(number, authenticated_user)

        # Live claims wait for GitHub propagation; fixture claims remain deterministic.
        backoff = claim_backoff_seconds()
        print()
        log_stage("issue-claim", f"Waiting {backoff:.1f}s before verifying claim on issue #{number}")
        time.sleep(backoff)

        # Verify we are still the assignee
        assignees = get_issue_assignees(number)
        if assignees != [authenticated_user]:
            print()
            log_stage("issue-claim", f"Issue #{number}: assignee is now {assignees}, not us. Backing off.")
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
        print()
        log_stage("issue-claim", f"Claimed issue #{number} (-> In Progress)")
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
        priority_offset: int,
        regular_offset: int,
) -> str:
    """Return a concise description of the current issue scan position."""
    if offset == 0:
        return f"priority offset {priority_offset}, regular offset {regular_offset}"
    return f"offset {current_offset}"


def log_issue_scan_start(label: str, offset: int) -> None:
    """Log where an issue scan starts."""
    if offset == 0:
        position = "from the most recently updated issues with priority-first ordering"
    else:
        position = f"from offset {offset}"
    print()
    log_stage("issue-scan", f"Starting issue scan for label '{label}' {position}")


def log_issue_scan_progress(
        label: str,
        scanned_count: int,
        offset: int,
        current_offset: int,
        priority_offset: int,
        regular_offset: int,
) -> None:
    """Log issue scan progress after another interval of candidates was inspected."""
    position = format_issue_scan_position(offset, current_offset, priority_offset, regular_offset)
    print()
    log_stage(
        "issue-scan",
        f"Looked through {scanned_count} issue(s) for label '{label}' ({position})",
    )


def resolve_random_issue_scan_offset(label: str) -> int:
    """Choose a random searchable offset for an issue label."""
    issue_count = count_issues_with_label(label)
    searchable_count = min(issue_count, GITHUB_SEARCH_MAX_RESULTS)
    if searchable_count <= 0:
        return 0
    return random.randrange(searchable_count)


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
    priority_offset = 0
    regular_offset = 0
    exhausted = False
    priority_exhausted = False
    unresolved_candidates: list[tuple[dict, CachedIssueClaimSkip | None]] = []
    pending_issues: list[tuple[dict, IssueClaimPreflight | None, CachedIssueClaimSkip | None]] = []
    active_futures: dict[concurrent.futures.Future[bool], ClaimedIssue] = {}

    log_issue_scan_start(label, offset)

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
                        if offset == 0:
                            issues, priority_offset, regular_offset, priority_exhausted, exhausted = (
                                get_prioritized_issues_with_label(
                                    label,
                                    fetch_limit,
                                    priority_offset,
                                    regular_offset,
                                    priority_exhausted,
                                )
                            )
                            if not issues:
                                if priority_offset == 0 and regular_offset == 0:
                                    print()
                                    log_stage("issue-scan", f"No open issues found with label '{label}'")
                                break
                        else:
                            issues = get_issues_with_label(label, fetch_limit, current_offset)
                            current_offset += len(issues)
                            if not issues:
                                if current_offset == offset:
                                    print()
                                    log_stage("issue-scan", f"No open issues found with label '{label}'")
                                exhausted = True
                                break

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
                                priority_offset,
                                regular_offset,
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
                    if not take_blocked_issues:
                        claim_kwargs["take_blocked_issues"] = False
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
            mark_user_interrupt_requested()
        interrupt_reason = get_user_interrupt_reason()
        interrupt_message = (
            f"Shutdown requested via {describe_active_shutdown_signal_path()}"
            if interrupt_reason == INTERRUPT_REASON_SHUTDOWN
            else "Ctrl+C received"
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

    print()
    log_stage("issue-scan", f"Scanned {scanned_count} issue(s) for label '{label}'")
    return processed_count


def process_work_queues(
        base_reachability_metadata_path: str,
        canonical_metrics_repo_path: str,
        authenticated_user: str | None,
        work_strategy_name_override: str | None = None,
        keep_tests_without_dynamic_access_override: bool = False,
        parallelism_default: int = DEFAULT_PARALLELISM,
        random_offset_override: bool | None = None,
) -> None:
    """Process all configured issue and review queues in one Python process."""
    queue_configs = get_work_queue_configs_from_environment(work_strategy_name_override, random_offset_override)
    review_queue_configs = get_review_queue_configs_from_environment()
    validate_work_queue_strategies(queue_configs)

    keep_tests_without_dynamic_access = (
        keep_tests_without_dynamic_access_override
        or os.environ.get("FORGE_KEEP_TESTS_WITHOUT_DYNAMIC_ACCESS") == "1"
    )
    parallelism = get_env_parallelism("FORGE_PARALLELISM", parallelism_default)
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
            print()
            log_stage("work-queue", f"Skipping issue queue '{queue_config.label}' because its limit is 0")
            continue

        authenticated_user = resolve_authenticated_user(authenticated_user)
        print()
        log_stage(
            "work-queue",
            f"Processing up to {queue_config.limit} issue(s) for label '{queue_config.label}'",
        )
        issue_scan_offset = 0
        if queue_config.random_offset:
            issue_scan_offset = resolve_random_issue_scan_offset(queue_config.label)
            print()
            log_stage(
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
            environment_already_validated=True,
        )

    for review_queue_config in review_queue_configs:
        if is_shutdown_requested():
            log_stage(
                "shutdown",
                f"Stop marker exists at {describe_active_shutdown_signal_path()}; skipping remaining review queues",
            )
            return
        if review_queue_config.limit <= 0:
            print()
            log_stage("work-queue", f"Skipping review queue '{review_queue_config.label}' because its limit is 0")
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
            review_queue_config.model,
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
        help="Review open pull requests with the given GitHub label using Codex.",
    )
    mode.add_argument(
        "--run-work-queues",
        action="store_true",
        help="Process all configured issue and review queues in one Python process.",
    )
    mode.add_argument(
        "--continue-large-library-artifact",
        metavar="PATH",
        help="Resume a large-library series from a durable progress state JSON artifact.",
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
        help="Use local GitHub issue fixtures for an exact single-issue E2E run.",
    )
    parser.add_argument(
        "--review-model",
        default=DEFAULT_REVIEW_MODEL,
        help=f"Codex model used for `--review-pr` runs (default: {DEFAULT_REVIEW_MODEL}).",
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
        help="Claim issues even when GitHub shows open blocking issues. Defaults to enabled.",
    )

    args = parser.parse_args(argv)
    if args.period is not None and args.review_pr is None:
        parser.error("--period can only be used with --review-pr")
    if args.fixture_testing:
        if args.issue_number is None:
            parser.error("--fixture-testing can only be used with --issue-number")
        if not args.strategy_name:
            parser.error("--fixture-testing requires --strategy-name")
        if args.offset != 0:
            parser.error("--fixture-testing cannot be combined with --offset")
        if args.random_offset is not None:
            parser.error("--fixture-testing cannot be combined with --random-offset/--no-random-offset")
    if args.random_offset is not None and args.label is None and not args.run_work_queues:
        parser.error("--random-offset/--no-random-offset can only be used with --label or --run-work-queues")
    if args.random_offset is True and args.offset != 0:
        parser.error("--random-offset cannot be combined with --offset")
    return args


def log_fixture_testing_selection(issue_number: int, label: str, strategy_name: str) -> None:
    """Log fixture-backed E2E routing before the issue claim mutates fixture state."""
    fixture_state = require_fixture_github_state()
    strategy = require_strategy_by_name(strategy_name)
    fixture_path = fixture_state.get_issue_fixture_path(issue_number)
    strategy_model = strategy.get("model") or "unknown"
    workflow_engine = strategy.get("workflow") or "unknown"

    print()
    log_stage(
        "fixture-testing",
        (
            "Selected fixture-backed issue run: "
            f"mode=fixture-testing, issue=#{issue_number}, fixture={fixture_path}, "
            f"queue_label={label}, strategy={strategy_name}, model={strategy_model}, "
            f"workflow_engine={workflow_engine}"
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
    """Fetch, claim, and process a single issue by number."""
    validate_issue_processing_environment()

    issue, label = get_issue_by_number(issue_number)
    print()
    log_stage("issue-scan", f"Issue #{issue_number} matched pipeline label: {label}")
    if is_fixture_testing_enabled():
        if strategy_name is None:
            raise RuntimeError("Fixture testing requires an explicit strategy name")
        log_fixture_testing_selection(issue_number, label, strategy_name)

    claim_kwargs: dict[str, bool] = {}
    if not take_blocked_issues:
        claim_kwargs["take_blocked_issues"] = False
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

    fixture_report_context = None
    if is_fixture_testing_enabled():
        if strategy_name is None:
            raise RuntimeError("Fixture testing requires an explicit strategy name")
        fixture_report_context = build_fixture_e2e_report_context(
            claimed_issue,
            strategy_name,
            keep_tests_without_dynamic_access,
            canonical_metrics_repo_path,
        )

    return process_claimed_issue_lifecycle(
        claimed_issue,
        strategy_name,
        keep_tests_without_dynamic_access,
        canonical_metrics_repo_path,
        fixture_report_context=fixture_report_context,
    )


def process_large_library_continuation(
        resume_artifact: str,
        base_reachability_metadata_path: str,
        canonical_metrics_repo_path: str,
        strategy_name: str | None,
        keep_tests_without_dynamic_access: bool,
        authenticated_user: str,
        take_blocked_issues: bool = DEFAULT_TAKE_BLOCKED_ISSUES,
) -> bool:
    """Resume a large-library issue from a durable progress artifact."""
    state = LargeLibraryProgressState.load(resume_artifact)
    if state.issue_number is None:
        raise ValueError("Large-library continuation requires `issueNumber` in the progress state")
    verify_large_library_previous_part_merged(state)
    issue, label = get_issue_by_number(state.issue_number)
    claim_kwargs: dict[str, bool] = {}
    if not take_blocked_issues:
        claim_kwargs["take_blocked_issues"] = False
    claimed_issue = claim_issue_for_processing(
        issue,
        label,
        base_reachability_metadata_path,
        canonical_metrics_repo_path,
        authenticated_user,
        large_library_resume_artifact_override=resume_artifact,
        **claim_kwargs,
    )
    if not claimed_issue:
        log_failure_banner(f"Could not claim issue #{state.issue_number}.", file=sys.stderr)
        sys.exit(1)
    return process_claimed_issue_lifecycle(
        claimed_issue,
        strategy_name,
        keep_tests_without_dynamic_access,
        canonical_metrics_repo_path,
    )


def main() -> None:
    clear_user_interrupt_requested()
    previous_sigint_handler = signal.getsignal(signal.SIGINT)
    signal.signal(signal.SIGINT, _handle_sigint)
    args = parse_args()

    try:
        if args.fixture_testing:
            configure_fixture_testing()
        if args.clear_issue_caches:
            clear_issue_caches()
            return
        if args.strategy_name:
            require_strategy_by_name(args.strategy_name)
        if args.review_pr is None and not args.run_work_queues:
            validate_issue_processing_environment()
        reachability_metadata_path, metrics_repo_path = resolve_repo_roots(
            args.reachability_metadata_path,
            None,
        )

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
            )
        elif args.review_pr is not None:
            authenticated_user = resolve_authenticated_user()
            run_pull_request_review_loop(
                args.review_pr,
                args.limit,
                reachability_metadata_path,
                authenticated_user,
                args.review_model,
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
        elif args.continue_large_library_artifact is not None:
            authenticated_user = resolve_authenticated_user()
            process_large_library_continuation(
                args.continue_large_library_artifact,
                reachability_metadata_path,
                metrics_repo_path,
                args.strategy_name,
                args.keep_tests_without_dynamic_access,
                authenticated_user,
                args.take_blocked_issues,
            )
        else:
            authenticated_user = resolve_authenticated_user()
            issue_scan_offset = args.offset
            if args.random_offset is True:
                issue_scan_offset = resolve_random_issue_scan_offset(args.label)
                print()
                log_stage("issue-scan", f"Selected random start offset {issue_scan_offset} for label '{args.label}'")
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
            )
    except KeyboardInterrupt:
        if is_shutdown_requested():
            mark_shutdown_requested()
        else:
            mark_user_interrupt_requested()
        if is_shutdown_request_interrupt():
            print(
                f"\nRun stopped because the Forge stop marker exists at {describe_active_shutdown_signal_path()}.",
                file=sys.stderr,
            )
            sys.exit(0)
        print("\nERROR: Run interrupted by Ctrl+C.", file=sys.stderr)
        sys.exit(130)
    except GitHubRateLimitExceeded as exc:
        log_failure_banner(f"{exc}. Stop current run and retry after reset.", file=sys.stderr)
        sys.exit(GITHUB_RATE_LIMIT_EXIT_CODE)
    finally:
        signal.signal(signal.SIGINT, previous_sigint_handler)

    log_success_banner("Run complete.")


if __name__ == "__main__":
    main()
