# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Dispatcher configuration constants (§AR-forge-dispatcher-decomposition)."""

import os
import signal

from ai_workflows.core.workflow_strategy import (
    RUN_STATUS_CHUNK_READY,
    RUN_STATUS_SUCCESS,
    SUCCESS_WITH_INTERVENTION_STATUS,
)
from utility_scripts.host_graalvm_checks import ISSUE_GRAALVM_ENV_VARS


DEFAULT_MAX_ISSUES = 5  # Default maximum number of issues to process per run
DEFAULT_PARALLELISM = 1
MAX_PARALLELISM = 4
DEFAULT_ISSUE_SCAN_BATCH_SIZE = 25
ISSUE_SCAN_PROGRESS_LOG_INTERVAL = 100
GITHUB_API_MAX_PAGE_SIZE = 100
GITHUB_SEARCH_MAX_RESULTS = 1000
# Issue queues scan by creation date so an issue Forge itself touches cannot bump
# itself back to the head of the queue and starve the rest of the backlog.
ISSUE_SEARCH_SORT = "created"
ISSUE_SEARCH_ORDER = "desc"
DEFAULT_TAKE_BLOCKED_ISSUES = False
# GitHub validates GraphQL node cost against worst-case first values; 5 issues can exceed 500k here.
ISSUE_CLAIM_PREFLIGHT_CHUNK_SIZE = 4
ISSUE_CLAIM_CACHE_VERSION = 1
ISSUE_CLAIM_CACHE_FILENAME = "issue-cache-v1.json"
ISSUE_CLAIM_CACHE_LOCK_FILENAME = "issue-cache-v1.lock"
DEFAULT_ISSUE_CLAIM_CACHE_TTL_SECONDS = 15 * 60
ISSUE_SEARCH_CACHE_VERSION = 2
ISSUE_SEARCH_CACHE_FILENAME = "issue-search-cache-v2.json"
ISSUE_SEARCH_CACHE_LOCK_FILENAME = "issue-search-cache-v1.lock"
DEFAULT_ISSUE_SEARCH_CACHE_TTL_SECONDS = 10 * 60
GITHUB_RATE_LIMIT_EXIT_CODE = 75
GRADLE_BOOTSTRAP_EXIT_CODE = 76
FIXTURE_E2E_LOG_DIRNAME = "fixture-e2e-logs"
FIXTURE_RUN_LOG_FILENAME = "run.log"
FIXTURE_PUBLICATION_FILENAME = "publication.md"
FIXTURE_PREFLIGHT_EVIDENCE_DIRNAME = "preflight-evidence"
REVIEW_PERIOD_SUFFIX_SECONDS = {
    "s": 1,
    "m": 60,
    "h": 60 * 60,
    "d": 24 * 60 * 60,
}
FAILED_CI_STATES = {"FAILURE", "ERROR"}
RERUNNABLE_WORKFLOW_RUN_CONCLUSIONS = {"failure"}

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
LABEL_PR_CODE_COVERAGE = "code-coverage-improvement"
LABEL_HIGH_PRIORITY = "high-priority"
LABEL_PRIORITY = "priority"
PRIORITY_HIGH = "high"
PRIORITY_NORMAL = "normal"
PRIORITY_CHOICES: tuple[str, ...] = (PRIORITY_HIGH, LABEL_PRIORITY, PRIORITY_NORMAL)
LABEL_HUMAN_INTERVENTION = "human-intervention"
LABEL_HUMAN_INTERVENTION_FIXED = "human-intervention-fixed"
LABEL_FORGE_MERGE_FOLLOW_UP = "forge-merge-follow-up"
LABEL_NOT_FOR_NATIVE_IMAGE = "not-for-native-image"
LABEL_LIBRARY_UNSUPPORTED_VERSION = "library-unsupported-version"
LABEL_CHUNKED_DYNAMIC_ACCESS = "chunked-dynamic-access"
LABEL_RESUMABLE = "resumable"
FIXTURE_AUTHENTICATED_USER = "fixture-runner"
FIXTURE_COVERAGE_FOLLOW_UP_ISSUE_OFFSET = 9_000_000
NON_USER_REQUESTED_ISSUE_AUTHORS: tuple[str, ...] = (
    "graalvmbot",
    "github-actions[bot]",
    "github-actions-bot",
    "vjovanov",
    "kimeta",
    "jormundur00",
)

SCRATCH_WORKTREE_DIRNAME = "forge_worktrees"
PREFLIGHT_INFO_DIRNAME = "preflight_info"
SCRATCH_REVIEW_WORKTREE_DIRNAME = "forge_review_worktrees"
SCRATCH_FINAL_INDEX_VALIDATION_WORKTREE_DIRNAME = "forge_final_index_validation_worktrees"
SCRATCH_CONFLICT_RESOLUTION_WORKTREE_DIRNAME = "forge_conflict_resolution_worktrees"
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
ADD_NEW_LIBRARY_METRICS_FILE = "add_new_library_support.json"
FIX_JAVAC_METRICS_FILE = "fix_javac_fail.json"
FIX_JAVA_RUN_METRICS_FILE = "fix_java_run_fail.json"
LOW_DYNAMIC_ACCESS_COVERAGE_RATIO = 0.10
HUMAN_INTERVENTION_LABEL_COLOR = "B60205"
HUMAN_INTERVENTION_LABEL_DESCRIPTION = (
    "Requires manual follow-up because automated processing needs human attention"
)
FORGE_MERGE_FOLLOW_UP_LABEL_COLOR = "0E8A16"
FORGE_MERGE_FOLLOW_UP_LABEL_DESCRIPTION = (
    "Forge must reconcile linked issues after GitHub auto-merges this pull request"
)
HUMAN_INTERVENTION_NON_FAILURE_STATUSES = {
    RUN_STATUS_SUCCESS,
    RUN_STATUS_CHUNK_READY,
    SUCCESS_WITH_INTERVENTION_STATUS,
}
PUBLICATION_METRICS_EXTRA_KEYS: tuple[str, ...] = (
    "post_generation_intervention",
    "local_ci_verification",
    "library_update_alias_split",
    "local_review",
)
# A workflow failure is logical (driver/core/CI-check) and gets `human-intervention`
# by default. The only exception is an external dependency failure, which surfaces
# as a typed exception at the Python boundary that issues it: GitHub (`gh`) as
# `GitHubError` / `GitHubRateLimitExceeded`, remote git as `GitTransportError`, and
# a Gradle build that never left configuration as `GradleBootstrapFailure`.
# Maven Central and Docker registry failures have no such boundary (Gradle owns
# them inside `./gradlew test`); they fall through to the safe logical default.
# §FS-human-intervention-policy
NOT_FOR_NATIVE_IMAGE_LABEL_COLOR = "5319E7"
NOT_FOR_NATIVE_IMAGE_LABEL_DESCRIPTION = (
    "Artifact is tracked but is not applicable to GraalVM Native Image reachability metadata"
)
PRIORITY_LABEL_COLOR = "FBCA04"
PRIORITY_LABEL_DESCRIPTION = "Automation should process this issue before regular queue items"
CHUNKED_DYNAMIC_ACCESS_LABEL_COLOR = "C5DEF5"
CHUNKED_DYNAMIC_ACCESS_LABEL_DESCRIPTION = "Issue uses chunked dynamic-access processing"
LIBRARY_UNSUPPORTED_VERSION_LABEL_COLOR = "5319E7"
LIBRARY_UNSUPPORTED_VERSION_LABEL_DESCRIPTION = (
    "Library version cannot be supported by GraalVM Native Image"
)
RESUMABLE_LABEL_COLOR = "0E8A16"
RESUMABLE_LABEL_DESCRIPTION = "Issue has preserved automation work that Forge can resume"
DEFAULT_DYNAMIC_ACCESS_CHUNK_CLASS_THRESHOLD = 15


DEFAULT_WORK_QUEUE_STRATEGY_NAME = "optimistic_dynamic_access_iterative_pi_gpt-5.6-sol"
FAILURE_ANALYSIS_TIMEOUT_SECONDS = 1800
REVIEW_TIMEOUT_SECONDS = 1800
DEFAULT_WORKTREE_BASE_REF = "master"
TRUSTED_FORGE_PUBLISHER_LOGIN = "graalvmbot"
BENCHMARK_PUBLICATION_TASK_TYPE = "code-coverage-benchmark-result"
SUPPORTED_FORGE_REVIEW_TASK_TYPES = {
    "library-new-request",
    "library-update-request",
    "fixes-javac-fail",
    "fixes-java-run-fail",
    "fixes-native-image-run-fail",
    "not-for-native-image",
    BENCHMARK_PUBLICATION_TASK_TYPE,
}
LOCAL_REVIEW_CLOSE_MARKER = "<!-- forge-local-review-close -->"
FORGE_APPROVAL_BODY_PREFIX = "Approved from the authoritative local review for commit "
# The GraalVM lanes are named once in `host_requirements`, in this order.
DEV_GRAALVM_ENV_VAR, POST_GENERATION_GRAALVM_ENV_VAR, LATEST_EA_GRAALVM_ENV_VAR = ISSUE_GRAALVM_ENV_VARS
# The dispatcher package lives one level below the forge root this constant names.
FORGE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
INTERRUPT_EXIT_CODES = {130, -int(signal.SIGINT)}
INTERRUPT_REASON_CTRL_C = "Ctrl+C interrupt"
INTERRUPT_REASON_SHUTDOWN = "shutdown request"
INTERRUPT_REASON_GRADLE_BOOTSTRAP = "Gradle bootstrap infrastructure failure"
SHUTDOWN_SIGNAL_POLL_SECONDS = 5.0

PIPELINE_LABELS = {LABEL_LIBRARY_NEW, LABEL_LIBRARY_UPDATE, LABEL_JAVAC_FAIL, LABEL_JAVA_RUN_FAIL, LABEL_NI_RUN_FAIL}
FAILURE_PIPELINE_LABELS = {LABEL_JAVAC_FAIL, LABEL_JAVA_RUN_FAIL, LABEL_NI_RUN_FAIL}

# Named rules of the post-claim issue-form gate. The name of the failed rule is
# what a rejection reports and what selects its comment. §FS-forge-run-requirements.3
ISSUE_FORM_RULE_SINGLE_WORKFLOW_LABEL = "single-workflow-label"
ISSUE_FORM_RULE_MAVEN_COORDINATES = "maven-coordinates"
ISSUE_FORM_RULE_CURRENT_LATEST_VERSION = "current-latest-version"
ISSUE_FORM_RULE_NEWER_THAN_LATEST = "newer-than-latest"
ISSUE_FORM_RULE_PUBLISHED_ARTIFACT = "published-artifact"
ISSUE_FORM_REJECTION_MARKER_PREFIX = "<!-- forge-issue-form-rejection"

CLAIM_BACKOFF_MIN = 5  # Minimum seconds to wait before verifying claim
CLAIM_BACKOFF_MAX = 10  # Maximum seconds to wait before verifying claim
