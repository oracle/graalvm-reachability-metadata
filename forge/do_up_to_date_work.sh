#!/usr/bin/env bash
# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PYTHON_BIN="${PYTHON_BIN:-python3}"
SLEEP_SECONDS="${DO_WORK_SLEEP_SECONDS:-${DO_MY_WORK_SLEEP_SECONDS:-300}}"
CLEAN_LOCAL_REPOSITORIES_EVERY="${DO_WORK_CLEAN_LOCAL_REPOSITORIES_EVERY:-${DO_MY_WORK_CLEAN_LOCAL_REPOSITORIES_EVERY:-10}}"
JAVAC_WORK_LIMIT="${FORGE_JAVAC_WORK_LIMIT:-1}"
JAVAC_WORK_STRATEGY_NAME="${FORGE_JAVAC_STRATEGY_NAME:-}"
JAVA_RUN_WORK_LIMIT="${FORGE_JAVA_RUN_WORK_LIMIT:-1}"
JAVA_RUN_WORK_STRATEGY_NAME="${FORGE_JAVA_RUN_STRATEGY_NAME:-}"
NI_RUN_WORK_LIMIT="${FORGE_NI_RUN_WORK_LIMIT:-1}"
NI_RUN_WORK_STRATEGY_NAME="${FORGE_NI_RUN_STRATEGY_NAME:-}"
LIBRARY_UPDATE_WORK_LIMIT="${FORGE_LIBRARY_UPDATE_WORK_LIMIT:-1}"
LIBRARY_UPDATE_WORK_STRATEGY_NAME="${FORGE_LIBRARY_UPDATE_STRATEGY_NAME:-}"
WORK_LABEL="${FORGE_WORK_LABEL:-library-new-request}"
WORK_LIMIT="${FORGE_WORK_LIMIT:-1}"
RANDOM_WORK_OFFSET="${FORGE_RANDOM_WORK_OFFSET:-0}"
PRIORITY_TIER=""
PARALLELISM="${FORGE_PARALLELISM:-1}"
REVIEW_LABEL="${FORGE_REVIEW_LABEL:-}"
REVIEW_LIMIT="${FORGE_REVIEW_LIMIT:-1}"
ANALYSIS_AGENT="${FORGE_ANALYSIS_AGENT:-}"
ANALYSIS_MODEL="${FORGE_ANALYSIS_MODEL:-}"
ANALYSIS_PROVIDER="${FORGE_ANALYSIS_PROVIDER:-${FORGE_AGENT_PROVIDER:-}}"
ANALYSIS_FAMILY="${FORGE_ANALYSIS_FAMILY:-${FORGE_ANALYSIS_AGENT_FAMILY:-${FORGE_AGENT_FAMILY:-}}}"
SETUP_AGENT="${FORGE_SETUP_AGENT:-}"
SETUP_MODEL="${FORGE_SETUP_MODEL:-}"
SETUP_PROVIDER="${FORGE_SETUP_PROVIDER:-}"
SETUP_FAMILY="${FORGE_SETUP_FAMILY:-}"
# Alias only the test executable; the strategy still owns selection.
# §FS-forge-agent-runtime-selection
TEST_AGENT_ALIAS="${FORGE_TEST_AGENT_ALIAS:-}"
AGENT_FAMILY="${FORGE_AGENT_FAMILY:-}"
FAIL_FAST="${FORGE_FAIL_FAST:-0}"
USER_REQUESTED_ONLY="${FORGE_USER_REQUESTED_ISSUES_ONLY:-0}"
# Explicitly bypass only the open-blocker claim predicate.
# §FS-forge-run-requirements.2
TAKE_BLOCKED_ISSUES="${FORGE_TAKE_BLOCKED_ISSUES:-0}"
GRAALVM_VERSION_CHECK="${FORGE_GRAALVM_VERSION_CHECK:-strict}"
WORK_STRATEGY_NAME="${FORGE_STRATEGY_NAME:-optimistic_dynamic_access_iterative_pi_gpt-5.6-sol}"
GITHUB_RATE_LIMIT_EXIT_CODE=75
GRADLE_BOOTSTRAP_EXIT_CODE=76
MAX_PARALLELISM=4
RUN_ONCE=0
REQUEST_STOP=0
CLEAR_STOP=0
CLEAR_ISSUE_CACHES=0
BRANCH_ARG_PROVIDED=0
SLEEP_POLL_SECONDS="${FORGE_DO_WORK_SLEEP_POLL_SECONDS:-5}"

if [[ -n "${FORGE_DO_WORK_STOP_FILE:-}" ]]; then
    STOP_FILE="$FORGE_DO_WORK_STOP_FILE"
elif [[ -n "${HOME:-}" ]]; then
    STOP_FILE="$HOME/.metadata-forge-stop"
else
    STOP_FILE=""
fi

LOCAL_REPOSITORIES_DIR="$SCRIPT_DIR/local_repositories"
REACHABILITY_REPO_DIR="$LOCAL_REPOSITORIES_DIR/graalvm-reachability-metadata"
METRICS_REPO_DIR="$LOCAL_REPOSITORIES_DIR/metadata-forge-metrics"
WORKTREES_DIR="$LOCAL_REPOSITORIES_DIR/forge_worktrees"
REVIEW_WORKTREES_DIR="$LOCAL_REPOSITORIES_DIR/forge_review_worktrees"
RUN_METRICS_DIR="$LOCAL_REPOSITORIES_DIR/forge_run_metrics"

usage() {
    cat <<EOF
Usage: $0 [options] [metadata-forge-branch]

Purpose:
  Keep this metadata-forge checkout up to date, process the configured work
  queues, sleep, and re-exec the latest do_up_to_date_work.sh.

Arguments:
  metadata-forge-branch
      Optional branch to monitor on origin. The origin/ prefix is accepted and
      stripped. Defaults to DO_WORK_MONITORED_BRANCH, then
      DO_MY_WORK_MONITORED_BRANCH, then master.

Options:
  -h, --help
      Show this help text.
  --once
      Run one update/work cycle and exit without sleeping.
  --fail-fast
      Exit nonzero when the first work cycle is unsuccessful.
  --stop
      Request Forge do-work loops to exit. Without a branch argument this
      creates the global stop marker ~/.metadata-forge-stop. With --branch or
      a positional branch, it creates a branch-scoped marker next to the global
      marker, for example ~/.metadata-forge-stop.master.
  --clear-stop, --resume
      Clear the matching global or branch-scoped stop marker so future do-work
      loops can run.
  --clear-issue-caches
      Delete local issue claim/search caches used by work-queue scanning and
      exit.
  --branch BRANCH
      Branch to monitor on origin. Equivalent to the optional positional
      metadata-forge-branch argument.
  --javac-limit N, --javac-work-limit N
      Process up to N fails-javac-compile tasks per run; 0 disables it. Defaults to
      FORGE_JAVAC_WORK_LIMIT, then 1.
  --java-run-limit N, --java-run-work-limit N
      Process up to N fails-java-run tasks per run; 0 disables it. Defaults to
      FORGE_JAVA_RUN_WORK_LIMIT, then 1.
  --ni-run-limit N, --native-image-run-limit N, --ni-run-work-limit N
      Process up to N fails-native-image-run tasks per run; 0 disables it. Defaults to
      FORGE_NI_RUN_WORK_LIMIT, then 1.
  --new-limit N, --work-limit N, --new-work-limit N
      Process up to N new-library tasks per run; 0 disables it. Defaults to
      FORGE_WORK_LIMIT, then 1.
  --random-offset
      Start new-library issue scans at a random offset.
  --no-random-offset
      Start new-library issue scans from the beginning of the issue list. This
      is the default.
  --priority {high,priority,normal}
      Process only the selected issue priority tier in every issue queue.
  --parallelism N
      Run up to N issue workflows in parallel. Defaults to FORGE_PARALLELISM,
      then 1. The maximum is 4.
  --review-limit N
      Process up to N review tasks per PR label per run; 0 disables review queues.
      Defaults to FORGE_REVIEW_LIMIT, then 1. Without FORGE_REVIEW_LABEL, reviews
      library-new-request, fixes-javac-fail, fixes-java-run-fail,
      fixes-native-image-run-fail, and library-bulk-update PRs each cycle.
  --analysis-agent COMMAND
      Select the executable used for analysis/recovery/review work. The
      command may use any local name, such as cdx.
  --analysis-family {claude-code,pi,codex,opencode}
      Select the analysis agent family (adapter/protocol).
  --analysis-provider PROVIDER
      Select the provider for a pi or opencode analysis agent. Defaults to
      FORGE_ANALYSIS_PROVIDER, then openai-codex. Ignored by codex and
      claude-code, which authenticate their own provider.
  --analysis-model MODEL
      Select its backend-specific model. Defaults to FORGE_ANALYSIS_MODEL,
      then gpt-5.6-luna for Codex or sonnet for Claude Code.
  --setup-agent COMMAND
      Select the executable used for URL discovery and library preflight. The
      command may use any local name.
  --setup-family {claude-code,pi,codex,opencode}
      Select the setup agent family (adapter/protocol).
  --setup-provider PROVIDER
      Select the provider for a pi or opencode setup agent. Ignored by codex and
      claude-code, which authenticate their own provider.
  --setup-model MODEL
      Select its backend-specific model. Setup and analysis are independent:
      an unset setup option takes the shared default, never the analysis
      setting.
  --agent-family FAMILY
      Backward-compatible alias for --analysis-family. The test-generation
      backend, model, and provider come from the selected strategy.
  --test-agent-alias COMMAND
      Use a machine-local executable name for the test agent without changing
      the backend, model, or provider selected by its strategy.
  --user-requested-only
      Fetch only user-requested issue queue items by excluding configured
      automation and maintainer issue authors. Defaults to
      FORGE_USER_REQUESTED_ISSUES_ONLY, then 0.
  --take-blocked-issues
      Claim issues even when GitHub shows open blocking issues. This bypasses
      only the blocker check. Defaults to FORGE_TAKE_BLOCKED_ISSUES, then 0.
  --graalvm-version-check {strict,warn,off}
      How host validation treats a GraalVM version mismatch: strict stops the
      worker, warn reports it, off skips the version match. Native Image and the
      reachability-metadata schema stay mandatory in every mode, so a locally
      built Graal can be used with warn or off. Defaults to
      FORGE_GRAALVM_VERSION_CHECK, then strict.

Environment:
  DO_WORK_SLEEP_SECONDS
      Seconds to sleep between runs. Defaults to DO_MY_WORK_SLEEP_SECONDS,
      then 300.
  DO_WORK_CLEAN_LOCAL_REPOSITORIES_EVERY
      Clean local_repositories every N iterations. Defaults to
      DO_MY_WORK_CLEAN_LOCAL_REPOSITORIES_EVERY, then 10.
  DO_WORK_MONITORED_BRANCH
      Branch to monitor when no branch argument is provided. Defaults to
      DO_MY_WORK_MONITORED_BRANCH, then master.
  FORGE_DO_WORK_STOP_FILE
      Path to the shared stop marker. Defaults to ~/.metadata-forge-stop.
  FORGE_RANDOM_WORK_OFFSET
      Set to 1 to start new-library issue scans at a random offset, or 0 to
      scan from the beginning. Defaults to 0.
  FORGE_PARALLELISM
      Run up to this many issue workflows in parallel. Defaults to 1. The
      maximum is 4.
  FORGE_REVIEW_LABEL
      Review only PRs with this label. If unset, each generated PR label is
      reviewed every cycle.
  FORGE_ANALYSIS_AGENT, FORGE_ANALYSIS_FAMILY, FORGE_ANALYSIS_MODEL,
  FORGE_ANALYSIS_PROVIDER
      Configure offline analysis, recovery, style-fix, and review work.
      Codex defaults to gpt-5.6-luna/high (xhigh for review); Claude Code
      defaults to sonnet.
  FORGE_SETUP_AGENT, FORGE_SETUP_FAMILY, FORGE_SETUP_MODEL,
  FORGE_SETUP_PROVIDER
      Configure artifact-URL discovery and library preparation preflight,
      independently of the analysis role.
  FORGE_AGENT_FAMILY
      Backward-compatible analysis family fallback selected by --agent-family.
  FORGE_TEST_AGENT_ALIAS
      Machine-local executable name for the strategy-selected test agent.
  FORGE_FAIL_FAST
      Set to 1 to exit nonzero on the first unsuccessful work cycle.
  FORGE_USER_REQUESTED_ISSUES_ONLY
      Set to 1 to fetch only user-requested issue queue items, or 0 to process
      all eligible issue authors. Defaults to 0.
  FORGE_TAKE_BLOCKED_ISSUES
      Set to 1 to claim issues with open blockers, or 0 to leave them queued.
      Defaults to 0.
  FORGE_LIBRARY_REVIEW_LIMIT, FORGE_JAVAC_REVIEW_LIMIT, FORGE_JAVA_RUN_REVIEW_LIMIT,
  FORGE_NI_RUN_REVIEW_LIMIT, FORGE_BULK_UPDATE_REVIEW_LIMIT
      Override FORGE_REVIEW_LIMIT for one default review queue.
  FORGE_GRAALVM_VERSION_CHECK
      Default --graalvm-version-check mode: strict, warn, or off. Defaults to
      strict.

Examples:
  $0
  $0 master
  $0 --javac-limit 3 --new-limit 1
  $0 --user-requested-only --new-limit 1
  $0 --once --branch master
  $0 --once --graalvm-version-check warn
  $0 --clear-issue-caches
  DO_WORK_SLEEP_SECONDS=60 $0 origin/main
EOF
}

require_option_value() {
    local option="$1"
    local value="${2:-}"

    if [[ -z "$value" ]]; then
        echo "${option} requires a value." >&2
        usage >&2
        exit 1
    fi
}

require_positive_integer() {
    local name="$1"
    local value="$2"

    if ! [[ "$value" =~ ^[0-9]+$ ]] || [[ "$value" -lt 1 ]]; then
        echo "${name} must be a positive integer." >&2
        exit 1
    fi
}

require_nonnegative_integer() {
    local name="$1"
    local value="$2"

    if ! [[ "$value" =~ ^[0-9]+$ ]]; then
        echo "${name} must be a non-negative integer." >&2
        exit 1
    fi
}

require_parallelism() {
    local value="$1"

    if ! [[ "$value" =~ ^[0-9]+$ ]] || [[ "$value" -lt 1 || "$value" -gt "$MAX_PARALLELISM" ]]; then
        echo "FORGE_PARALLELISM must be between 1 and ${MAX_PARALLELISM}." >&2
        exit 1
    fi
}

log() {
    printf '[%(%Y-%m-%d %H:%M:%S)T] %s\n' -1 "$1"
}

require_stop_file() {
    if [[ -z "$STOP_FILE" ]]; then
        echo "HOME is not set; set FORGE_DO_WORK_STOP_FILE to use --stop or run do-work loops." >&2
        exit 1
    fi
}

request_stop() {
    local target_file="$1"
    local scope="$2"

    mkdir -p -- "$(dirname -- "$target_file")"
    printf 'Forge shutdown requested at %(%Y-%m-%d %H:%M:%S %Z)T\n' -1 > "$target_file"
    log "Requested ${scope} Forge do-work loops to stop via ${target_file}."
}

clear_stop() {
    local target_file="$1"
    local scope="$2"

    rm -f -- "$target_file"
    log "Cleared ${scope} Forge do-work stop marker at ${target_file}."
}

sanitize_branch_for_stop_file() {
    local branch="${1#origin/}"
    local sanitized="${branch//\//_}"
    sanitized="${sanitized//[!a-zA-Z0-9._-]/_}"
    printf '%s' "$sanitized"
}

get_branch_stop_file() {
    local branch="$1"
    printf '%s.%s' "$STOP_FILE" "$(sanitize_branch_for_stop_file "$branch")"
}

is_stop_requested() {
    [[ -e "$STOP_FILE" || -e "$(get_branch_stop_file "$MONITORED_BRANCH")" ]]
}

exit_if_stop_requested() {
    if is_stop_requested; then
        if [[ -e "$STOP_FILE" ]]; then
            log "Forge do-work stop marker exists at ${STOP_FILE}; exiting."
        else
            log "Forge do-work stop marker exists at $(get_branch_stop_file "$MONITORED_BRANCH"); exiting."
        fi
        exit 0
    fi
}

interruptible_sleep() {
    local remaining="$1"
    local sleep_chunk

    while [[ "$remaining" -gt 0 ]]; do
        exit_if_stop_requested
        sleep_chunk="$remaining"
        if [[ "$sleep_chunk" -gt "$SLEEP_POLL_SECONDS" ]]; then
            sleep_chunk="$SLEEP_POLL_SECONDS"
        fi
        sleep "$sleep_chunk"
        remaining=$((remaining - sleep_chunk))
    done
}

display_github_rate_limits() {
    local rate_limit_json

    if ! command -v gh >/dev/null 2>&1; then
        log "GitHub rate limits unavailable: gh CLI is not installed."
        return 0
    fi

    if ! rate_limit_json="$(gh api rate_limit 2>/dev/null)"; then
        log "GitHub rate limits unavailable: gh api rate_limit failed."
        return 0
    fi

    RATE_LIMIT_JSON="$rate_limit_json" "$PYTHON_BIN" - <<'PY'
import datetime
import json
import os
import sys

data = json.loads(os.environ["RATE_LIMIT_JSON"])
resources = data.get("resources", {})

print("GitHub rate limits:")
for name in ("core", "graphql", "search", "code_search"):
    bucket = resources.get(name)
    if not bucket:
        continue
    reset = datetime.datetime.fromtimestamp(bucket["reset"]).astimezone()
    print(
        f"- {name}: {bucket['remaining']}/{bucket['limit']} remaining, "
        f"resets {reset:%Y-%m-%d %H:%M:%S %Z}"
    )

exhausted_names = []
for name in ("core", "graphql"):
    bucket = resources.get(name)
    if bucket and bucket.get("remaining", 0) <= 0:
        exhausted_names.append(name)

if exhausted_names:
    print(f"GitHub API limit exhausted for: {', '.join(exhausted_names)}")
    sys.exit(1)
PY
}

cleanup_local_repositories() {
    local cleanup_targets=(
        "$REACHABILITY_REPO_DIR"
        "$METRICS_REPO_DIR"
        "$WORKTREES_DIR"
        "$REVIEW_WORKTREES_DIR"
        "$RUN_METRICS_DIR"
    )
    local target

    log "Cleaning local repositories."
    for target in "${cleanup_targets[@]}"; do
        if [[ -e "$target" ]]; then
            rm -rf -- "$target"
        fi
    done
}

update_metadata_forge() {
    log "Updating metadata-forge to the latest origin/${MONITORED_BRANCH}."

    if ! git -C "$SCRIPT_DIR" fetch origin "$MONITORED_BRANCH"; then
        log "origin/${MONITORED_BRANCH} is unavailable; continuing with the current metadata-forge checkout."
        return 1
    fi

    if git -C "$SCRIPT_DIR" show-ref --verify --quiet "refs/heads/${MONITORED_BRANCH}"; then
        if ! git -C "$SCRIPT_DIR" switch "$MONITORED_BRANCH"; then
            log "Local ${MONITORED_BRANCH} branch is unavailable; continuing with the current metadata-forge checkout."
            return 1
        fi
    else
        if ! git -C "$SCRIPT_DIR" switch -c "$MONITORED_BRANCH" --track "origin/${MONITORED_BRANCH}"; then
            log "Could not create local ${MONITORED_BRANCH} branch from origin/${MONITORED_BRANCH}; continuing with the current metadata-forge checkout."
            return 1
        fi
    fi

    if ! git -C "$SCRIPT_DIR" pull --ff-only origin "$MONITORED_BRANCH"; then
        log "Could not fast-forward ${MONITORED_BRANCH} from origin/${MONITORED_BRANCH}; continuing with the current metadata-forge checkout."
        return 1
    fi
}

run_step() {
    local description="$1"
    shift
    local status

    log "$description"
    set +e
    "$@"
    status=$?
    set -e

    if [[ "$status" -eq "$GITHUB_RATE_LIMIT_EXIT_CODE" ]]; then
        log "Skipping remaining work because the GitHub API limit is exhausted."
        return 0
    fi

    # §FS-shared-infrastructure-bootstrap-failure: a shared Gradle bootstrap outage
    # is host-wide, so skip the rest of this cycle and retry after the normal sleep.
    if [[ "$status" -eq "$GRADLE_BOOTSTRAP_EXIT_CODE" ]]; then
        log "Skipping remaining work because the shared Gradle bootstrap failed; retrying after sleep."
        return 0
    fi

    return "$status"
}

export_work_configuration() {
    export FORGE_MONITORED_BRANCH="origin/${MONITORED_BRANCH}"
    export FORGE_JAVAC_WORK_LIMIT="$JAVAC_WORK_LIMIT"
    export FORGE_JAVAC_STRATEGY_NAME="$JAVAC_WORK_STRATEGY_NAME"
    export FORGE_JAVA_RUN_WORK_LIMIT="$JAVA_RUN_WORK_LIMIT"
    export FORGE_JAVA_RUN_STRATEGY_NAME="$JAVA_RUN_WORK_STRATEGY_NAME"
    export FORGE_NI_RUN_WORK_LIMIT="$NI_RUN_WORK_LIMIT"
    export FORGE_NI_RUN_STRATEGY_NAME="$NI_RUN_WORK_STRATEGY_NAME"
    export FORGE_LIBRARY_UPDATE_WORK_LIMIT="$LIBRARY_UPDATE_WORK_LIMIT"
    export FORGE_LIBRARY_UPDATE_STRATEGY_NAME="$LIBRARY_UPDATE_WORK_STRATEGY_NAME"
    export FORGE_WORK_LABEL="$WORK_LABEL"
    export FORGE_WORK_LIMIT="$WORK_LIMIT"
    export FORGE_RANDOM_WORK_OFFSET="$RANDOM_WORK_OFFSET"
    export FORGE_PARALLELISM="$PARALLELISM"
    export FORGE_STRATEGY_NAME="$WORK_STRATEGY_NAME"
    export FORGE_REVIEW_LIMIT="$REVIEW_LIMIT"
    export FORGE_ANALYSIS_AGENT="$ANALYSIS_AGENT"
    export FORGE_ANALYSIS_FAMILY="$ANALYSIS_FAMILY"
    export FORGE_ANALYSIS_MODEL="$ANALYSIS_MODEL"
    export FORGE_ANALYSIS_PROVIDER="$ANALYSIS_PROVIDER"
    export FORGE_FAIL_FAST="$FAIL_FAST"
    export FORGE_USER_REQUESTED_ISSUES_ONLY="$USER_REQUESTED_ONLY"
    export FORGE_TAKE_BLOCKED_ISSUES="$TAKE_BLOCKED_ISSUES"
    export FORGE_GRAALVM_VERSION_CHECK="$GRAALVM_VERSION_CHECK"

    if [[ -n "$AGENT_FAMILY" ]]; then
        export FORGE_AGENT_FAMILY="$AGENT_FAMILY"
    else
        unset FORGE_AGENT_FAMILY
    fi

    # Exported only when configured, so an unset option takes the shared
    # default rather than a stale value from a previous cycle.
    if [[ -n "$SETUP_AGENT" ]]; then
        export FORGE_SETUP_AGENT="$SETUP_AGENT"
    else
        unset FORGE_SETUP_AGENT
    fi
    if [[ -n "$SETUP_FAMILY" ]]; then
        export FORGE_SETUP_FAMILY="$SETUP_FAMILY"
    else
        unset FORGE_SETUP_FAMILY
    fi
    if [[ -n "$SETUP_MODEL" ]]; then
        export FORGE_SETUP_MODEL="$SETUP_MODEL"
    else
        unset FORGE_SETUP_MODEL
    fi
    if [[ -n "$SETUP_PROVIDER" ]]; then
        export FORGE_SETUP_PROVIDER="$SETUP_PROVIDER"
    else
        unset FORGE_SETUP_PROVIDER
    fi

    if [[ -n "$TEST_AGENT_ALIAS" ]]; then
        export FORGE_TEST_AGENT_ALIAS="$TEST_AGENT_ALIAS"
    else
        unset FORGE_TEST_AGENT_ALIAS
    fi

    if [[ -n "$REVIEW_LABEL" ]]; then
        export FORGE_REVIEW_LABEL="$REVIEW_LABEL"
    else
        unset FORGE_REVIEW_LABEL
    fi
}

process_work_queues() {
    local forge_metadata_args=(
        "--run-work-queues"
        "--parallelism"
        "$PARALLELISM"
    )
    if [[ -n "$PRIORITY_TIER" ]]; then
        forge_metadata_args+=("--priority" "$PRIORITY_TIER")
    fi
    if [[ "$TAKE_BLOCKED_ISSUES" == "1" ]]; then
        forge_metadata_args+=("--take-blocked-issues")
    fi

    run_step "Processing configured work queues via forge_metadata." \
        "$PYTHON_BIN" "$SCRIPT_DIR/forge_metadata.py" "${forge_metadata_args[@]}"
}

run_host_requirements() {
    local host_requirements_script="$SCRIPT_DIR/utility_scripts/host_requirements.py"
    local host_requirements_args=(
        --forge-dir "$SCRIPT_DIR"
        --python-bin "$PYTHON_BIN"
        --analysis-agent "$ANALYSIS_AGENT"
        --analysis-family "$ANALYSIS_FAMILY"
        --analysis-model "$ANALYSIS_MODEL"
        --analysis-provider "$ANALYSIS_PROVIDER"
        --graalvm-version-check "$GRAALVM_VERSION_CHECK"
    )

    if ! command -v "$PYTHON_BIN" >/dev/null 2>&1; then
        echo "ERROR: Forge host requirements need PYTHON_BIN='$PYTHON_BIN' to resolve to an executable." >&2
        echo "Fix: install Python 3 or export PYTHON_BIN=/absolute/path/to/python3." >&2
        return 1
    fi

    log "Validating Forge host requirements before any work starts."
    # The gate takes no review model or shared family: --agent-family already
    # reaches it as --analysis-family, and PR review runs on the analysis role
    # (§FS-forge-agent-runtime-selection).
    if [[ -n "$SETUP_AGENT" ]]; then
        host_requirements_args+=(--setup-agent "$SETUP_AGENT")
    fi
    if [[ -n "$SETUP_FAMILY" ]]; then
        host_requirements_args+=(--setup-family "$SETUP_FAMILY")
    fi
    if [[ -n "$SETUP_MODEL" ]]; then
        host_requirements_args+=(--setup-model "$SETUP_MODEL")
    fi
    if [[ -n "$SETUP_PROVIDER" ]]; then
        host_requirements_args+=(--setup-provider "$SETUP_PROVIDER")
    fi
    if (( WORK_LIMIT > 0 )); then
        host_requirements_args+=(--test-strategy "$WORK_STRATEGY_NAME")
    fi
    if (( JAVAC_WORK_LIMIT > 0 )) && [[ -n "$JAVAC_WORK_STRATEGY_NAME" ]]; then
        host_requirements_args+=(--test-strategy "$JAVAC_WORK_STRATEGY_NAME")
    fi
    if (( JAVA_RUN_WORK_LIMIT > 0 )) && [[ -n "$JAVA_RUN_WORK_STRATEGY_NAME" ]]; then
        host_requirements_args+=(--test-strategy "$JAVA_RUN_WORK_STRATEGY_NAME")
    fi
    if (( NI_RUN_WORK_LIMIT > 0 )) && [[ -n "$NI_RUN_WORK_STRATEGY_NAME" ]]; then
        host_requirements_args+=(--test-strategy "$NI_RUN_WORK_STRATEGY_NAME")
    fi
    if (( LIBRARY_UPDATE_WORK_LIMIT > 0 )) && [[ -n "$LIBRARY_UPDATE_WORK_STRATEGY_NAME" ]]; then
        host_requirements_args+=(--test-strategy "$LIBRARY_UPDATE_WORK_STRATEGY_NAME")
    fi
    "$PYTHON_BIN" "$host_requirements_script" "${host_requirements_args[@]}"
}

run_cycle() {
    local iteration="${DO_UP_TO_DATE_WORK_ITERATION:-0}"

    exit_if_stop_requested

    if ! [[ "$iteration" =~ ^[0-9]+$ ]]; then
        iteration=0
    fi

    iteration=$((iteration + 1))
    export DO_UP_TO_DATE_WORK_ITERATION="$iteration"

    if ! display_github_rate_limits; then
        log "Skipping this run because the GitHub API limit is exhausted."
        return 0
    fi

    if (( iteration % CLEAN_LOCAL_REPOSITORIES_EVERY == 0 )); then
        cleanup_local_repositories
    fi

    if ! update_metadata_forge; then
        log "metadata-forge self-update failed; running work from the current checkout."
    fi

    log "Running do_up_to_date_work.sh while monitoring ${FORGE_MONITORED_BRANCH}."
    if ! process_work_queues; then
        log "do_up_to_date_work.sh failed; retrying after sleep."
        if [[ "$FAIL_FAST" == "1" ]]; then
            return 1
        fi
    fi

    exit_if_stop_requested
}

ORIGINAL_ARGS=("$@")
BRANCH_ARG=""

while [[ "$#" -gt 0 ]]; do
    case "$1" in
        -h|--help)
            usage
            exit 0
            ;;
        --once)
            RUN_ONCE=1
            shift
            ;;
        --stop)
            REQUEST_STOP=1
            shift
            ;;
        --clear-stop|--resume)
            CLEAR_STOP=1
            shift
            ;;
        --clear-issue-caches)
            CLEAR_ISSUE_CACHES=1
            shift
            ;;
        --branch)
            require_option_value "$1" "${2:-}"
            BRANCH_ARG="$2"
            BRANCH_ARG_PROVIDED=1
            shift 2
            ;;
        --branch=*)
            BRANCH_ARG="${1#*=}"
            BRANCH_ARG_PROVIDED=1
            shift
            ;;
        --javac-limit|--javac-work-limit)
            require_option_value "$1" "${2:-}"
            JAVAC_WORK_LIMIT="$2"
            shift 2
            ;;
        --javac-limit=*|--javac-work-limit=*)
            JAVAC_WORK_LIMIT="${1#*=}"
            shift
            ;;
        --java-run-limit|--java-run-work-limit)
            require_option_value "$1" "${2:-}"
            JAVA_RUN_WORK_LIMIT="$2"
            shift 2
            ;;
        --java-run-limit=*|--java-run-work-limit=*)
            JAVA_RUN_WORK_LIMIT="${1#*=}"
            shift
            ;;
        --ni-run-limit|--native-image-run-limit|--ni-run-work-limit)
            require_option_value "$1" "${2:-}"
            NI_RUN_WORK_LIMIT="$2"
            shift 2
            ;;
        --ni-run-limit=*|--native-image-run-limit=*|--ni-run-work-limit=*)
            NI_RUN_WORK_LIMIT="${1#*=}"
            shift
            ;;
        --new-limit|--work-limit|--new-work-limit)
            require_option_value "$1" "${2:-}"
            WORK_LIMIT="$2"
            shift 2
            ;;
        --new-limit=*|--work-limit=*|--new-work-limit=*)
            WORK_LIMIT="${1#*=}"
            shift
            ;;
        --random-offset)
            RANDOM_WORK_OFFSET=1
            shift
            ;;
        --no-random-offset)
            RANDOM_WORK_OFFSET=0
            shift
            ;;
        --priority)
            require_option_value "$1" "${2:-}"
            PRIORITY_TIER="$2"
            shift 2
            ;;
        --priority=*)
            PRIORITY_TIER="${1#*=}"
            shift
            ;;
        --parallelism)
            require_option_value "$1" "${2:-}"
            PARALLELISM="$2"
            shift 2
            ;;
        --parallelism=*)
            PARALLELISM="${1#*=}"
            shift
            ;;
        --review-limit)
            require_option_value "$1" "${2:-}"
            REVIEW_LIMIT="$2"
            shift 2
            ;;
        --review-limit=*)
            REVIEW_LIMIT="${1#*=}"
            shift
            ;;
        --analysis-agent)
            require_option_value "$1" "${2:-}"
            ANALYSIS_AGENT="$2"
            shift 2
            ;;
        --analysis-agent=*)
            ANALYSIS_AGENT="${1#*=}"
            shift
            ;;
        --analysis-family|--analysis-agent-family)
            require_option_value "$1" "${2:-}"
            ANALYSIS_FAMILY="$2"
            shift 2
            ;;
        --analysis-family=*|--analysis-agent-family=*)
            ANALYSIS_FAMILY="${1#*=}"
            require_option_value "--analysis-family" "$ANALYSIS_FAMILY"
            shift
            ;;
        --analysis-provider)
            [[ $# -ge 2 ]] || { echo "--analysis-provider requires a value" >&2; exit 2; }
            ANALYSIS_PROVIDER="$2"
            shift 2
            ;;
        --analysis-provider=*)
            ANALYSIS_PROVIDER="${1#*=}"
            shift
            ;;
        --analysis-model)
            require_option_value "$1" "${2:-}"
            ANALYSIS_MODEL="$2"
            shift 2
            ;;
        --analysis-model=*)
            ANALYSIS_MODEL="${1#*=}"
            shift
            ;;
        --setup-agent)
            require_option_value "$1" "${2:-}"
            SETUP_AGENT="$2"
            shift 2
            ;;
        --setup-agent=*)
            SETUP_AGENT="${1#*=}"
            require_option_value "--setup-agent" "$SETUP_AGENT"
            shift
            ;;
        --setup-family|--setup-agent-family)
            require_option_value "$1" "${2:-}"
            SETUP_FAMILY="$2"
            shift 2
            ;;
        --setup-family=*|--setup-agent-family=*)
            SETUP_FAMILY="${1#*=}"
            require_option_value "--setup-family" "$SETUP_FAMILY"
            shift
            ;;
        --setup-model)
            require_option_value "$1" "${2:-}"
            SETUP_MODEL="$2"
            shift 2
            ;;
        --setup-model=*)
            SETUP_MODEL="${1#*=}"
            require_option_value "--setup-model" "$SETUP_MODEL"
            shift
            ;;
        --setup-provider)
            require_option_value "$1" "${2:-}"
            SETUP_PROVIDER="$2"
            shift 2
            ;;
        --setup-provider=*)
            SETUP_PROVIDER="${1#*=}"
            require_option_value "--setup-provider" "$SETUP_PROVIDER"
            shift
            ;;
        --agent-family)
            require_option_value "$1" "${2:-}"
            AGENT_FAMILY="$2"
            ANALYSIS_FAMILY="$2"
            shift 2
            ;;
        --agent-family=*)
            AGENT_FAMILY="${1#*=}"
            require_option_value "--agent-family" "$AGENT_FAMILY"
            ANALYSIS_FAMILY="$AGENT_FAMILY"
            shift
            ;;
        --test-agent-alias)
            require_option_value "$1" "${2:-}"
            TEST_AGENT_ALIAS="$2"
            shift 2
            ;;
        --test-agent-alias=*)
            TEST_AGENT_ALIAS="${1#*=}"
            require_option_value "--test-agent-alias" "$TEST_AGENT_ALIAS"
            shift
            ;;
        --fail-fast)
            FAIL_FAST=1
            shift
            ;;
        --user-requested-only)
            USER_REQUESTED_ONLY=1
            shift
            ;;
        --take-blocked-issues)
            TAKE_BLOCKED_ISSUES=1
            shift
            ;;
        --graalvm-version-check)
            require_option_value "$1" "${2:-}"
            GRAALVM_VERSION_CHECK="$2"
            shift 2
            ;;
        --graalvm-version-check=*)
            GRAALVM_VERSION_CHECK="${1#*=}"
            shift
            ;;
        --)
            shift
            if [[ "$#" -gt 1 || -n "$BRANCH_ARG" ]]; then
                usage >&2
                exit 1
            fi
            BRANCH_ARG="${1:-}"
            if [[ -n "$BRANCH_ARG" ]]; then
                BRANCH_ARG_PROVIDED=1
            fi
            shift "$#"
            ;;
        -*)
            echo "Unknown option: $1" >&2
            usage >&2
            exit 1
            ;;
        *)
            if [[ -n "$BRANCH_ARG" ]]; then
                usage >&2
                exit 1
            fi
            BRANCH_ARG="$1"
            BRANCH_ARG_PROVIDED=1
            shift
            ;;
    esac
done

MONITORED_BRANCH="${BRANCH_ARG:-${DO_WORK_MONITORED_BRANCH:-${DO_MY_WORK_MONITORED_BRANCH:-master}}}"
MONITORED_BRANCH="${MONITORED_BRANCH#origin/}"

if [[ "$MONITORED_BRANCH" == "" ]]; then
    echo "metadata-forge branch must not be empty." >&2
    usage >&2
    exit 1
fi

if [[ "$CLEAR_ISSUE_CACHES" == "1" ]]; then
    "$PYTHON_BIN" "$SCRIPT_DIR/forge_metadata.py" --clear-issue-caches
    exit 0
fi

require_stop_file

if [[ "$REQUEST_STOP" == "1" && "$CLEAR_STOP" == "1" ]]; then
    echo "--stop cannot be combined with --clear-stop or --resume." >&2
    exit 1
fi

if [[ "$REQUEST_STOP" == "1" ]]; then
    if [[ "$BRANCH_ARG_PROVIDED" == "1" ]]; then
        request_stop "$(get_branch_stop_file "$MONITORED_BRANCH")" "branch '${MONITORED_BRANCH}'"
    else
        request_stop "$STOP_FILE" "all"
    fi
    exit 0
fi

if [[ "$CLEAR_STOP" == "1" ]]; then
    if [[ "$BRANCH_ARG_PROVIDED" == "1" ]]; then
        clear_stop "$(get_branch_stop_file "$MONITORED_BRANCH")" "branch '${MONITORED_BRANCH}'"
    else
        clear_stop "$STOP_FILE" "all"
    fi
    exit 0
fi

require_positive_integer "DO_WORK_SLEEP_SECONDS" "$SLEEP_SECONDS"
require_positive_integer "DO_WORK_CLEAN_LOCAL_REPOSITORIES_EVERY" "$CLEAN_LOCAL_REPOSITORIES_EVERY"
require_nonnegative_integer "FORGE_JAVAC_WORK_LIMIT" "$JAVAC_WORK_LIMIT"
require_nonnegative_integer "FORGE_JAVA_RUN_WORK_LIMIT" "$JAVA_RUN_WORK_LIMIT"
require_nonnegative_integer "FORGE_NI_RUN_WORK_LIMIT" "$NI_RUN_WORK_LIMIT"
require_nonnegative_integer "FORGE_LIBRARY_UPDATE_WORK_LIMIT" "$LIBRARY_UPDATE_WORK_LIMIT"
require_nonnegative_integer "FORGE_WORK_LIMIT" "$WORK_LIMIT"
require_nonnegative_integer "FORGE_REVIEW_LIMIT" "$REVIEW_LIMIT"
require_parallelism "$PARALLELISM"
require_positive_integer "FORGE_DO_WORK_SLEEP_POLL_SECONDS" "$SLEEP_POLL_SECONDS"

default_agent_command() {
    case "$1" in
        claude-code) printf '%s\n' "claude" ;;
        pi|codex|opencode) printf '%s\n' "$1" ;;
    esac
}

if [[ -z "$ANALYSIS_FAMILY" ]]; then
    if [[ -n "$ANALYSIS_AGENT" ]]; then
        ANALYSIS_FAMILY="$ANALYSIS_AGENT"
    else
        ANALYSIS_FAMILY="codex"
    fi
fi

if [[ -z "$ANALYSIS_AGENT" ]]; then
    ANALYSIS_AGENT="$(default_agent_command "$ANALYSIS_FAMILY")"
fi

if [[ -z "$ANALYSIS_MODEL" ]]; then
    case "$ANALYSIS_FAMILY" in
        codex) ANALYSIS_MODEL="gpt-5.6-luna" ;;
        claude-code) ANALYSIS_MODEL="sonnet" ;;
        *) ANALYSIS_MODEL="gpt-5.6-terra" ;;
    esac
fi
case "$ANALYSIS_FAMILY" in
    claude-code|pi|codex|opencode) ;;
    *)
        echo "--analysis-family must be claude-code, pi, codex, or opencode." >&2
        exit 1
        ;;
esac
if [[ -n "$SETUP_FAMILY" ]]; then
    case "$SETUP_FAMILY" in
        claude-code|pi|codex|opencode) ;;
        *)
            echo "--setup-family must be claude-code, pi, codex, or opencode." >&2
            exit 1
            ;;
    esac
fi
if [[ -z "$SETUP_FAMILY" && -n "$SETUP_AGENT" ]]; then
    case "$SETUP_AGENT" in
        claude-code|pi|codex|opencode) SETUP_FAMILY="$SETUP_AGENT" ;;
    esac
fi
if [[ -n "$PRIORITY_TIER" \
        && "$PRIORITY_TIER" != "high" \
        && "$PRIORITY_TIER" != "priority" \
        && "$PRIORITY_TIER" != "normal" ]]; then
    echo "--priority must be high, priority, or normal." >&2
    exit 1
fi

if [[ "$RANDOM_WORK_OFFSET" != "0" && "$RANDOM_WORK_OFFSET" != "1" ]]; then
    echo "FORGE_RANDOM_WORK_OFFSET must be 0 or 1." >&2
    exit 1
fi

if [[ "$USER_REQUESTED_ONLY" != "0" && "$USER_REQUESTED_ONLY" != "1" ]]; then
    echo "FORGE_USER_REQUESTED_ISSUES_ONLY must be 0 or 1." >&2
    exit 1
fi

if [[ "$TAKE_BLOCKED_ISSUES" != "0" && "$TAKE_BLOCKED_ISSUES" != "1" ]]; then
    echo "FORGE_TAKE_BLOCKED_ISSUES must be 0 or 1." >&2
    exit 1
fi

if [[ "$FAIL_FAST" != "0" && "$FAIL_FAST" != "1" ]]; then
    echo "FORGE_FAIL_FAST must be 0 or 1." >&2
    exit 1
fi

if [[ "$GRAALVM_VERSION_CHECK" != "strict" \
        && "$GRAALVM_VERSION_CHECK" != "warn" \
        && "$GRAALVM_VERSION_CHECK" != "off" ]]; then
    echo "--graalvm-version-check must be strict, warn, or off." >&2
    exit 1
fi

export_work_configuration
exit_if_stop_requested
run_host_requirements
run_cycle

if [[ "$RUN_ONCE" == "1" ]]; then
    exit 0
fi

log "Sleeping for ${SLEEP_SECONDS} second(s)."
interruptible_sleep "$SLEEP_SECONDS"
exec "$SCRIPT_DIR/do_up_to_date_work.sh" "${ORIGINAL_ARGS[@]}"
