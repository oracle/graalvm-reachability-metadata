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
LIBRARY_UPDATE_WORK_LIMIT="${FORGE_LIBRARY_UPDATE_WORK_LIMIT:-0}"
LIBRARY_UPDATE_WORK_STRATEGY_NAME="${FORGE_LIBRARY_UPDATE_STRATEGY_NAME:-}"
WORK_LABEL="${FORGE_WORK_LABEL:-library-new-request}"
WORK_LIMIT="${FORGE_WORK_LIMIT:-1}"
RANDOM_WORK_OFFSET="${FORGE_RANDOM_WORK_OFFSET:-1}"
PARALLELISM="${FORGE_PARALLELISM:-1}"
REVIEW_LABEL="${FORGE_REVIEW_LABEL:-}"
REVIEW_LIMIT="${FORGE_REVIEW_LIMIT:-1}"
REVIEW_MODEL="${FORGE_REVIEW_MODEL:-gpt-5.4}"
WORK_STRATEGY_NAME="${FORGE_STRATEGY_NAME:-dynamic_access_main_sources_pi_gpt-5.5}"
GITHUB_RATE_LIMIT_EXIT_CODE=75
MAX_PARALLELISM=4
RUN_ONCE=0

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
      Start new-library issue scans at a random offset. This is the default.
  --no-random-offset
      Start new-library issue scans from the beginning of the issue list.
  --parallelism N
      Run up to N issue workflows in parallel. Defaults to FORGE_PARALLELISM,
      then 1. The maximum is 4.
  --review-limit N
      Process up to N review tasks per PR label per run; 0 disables review queues.
      Defaults to FORGE_REVIEW_LIMIT, then 1. Without FORGE_REVIEW_LABEL, reviews
      library-new-request, fixes-javac-fail, fixes-java-run-fail,
      fixes-native-image-run-fail, and library-bulk-update PRs each cycle.

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
  FORGE_RANDOM_WORK_OFFSET
      Set to 1 to start new-library issue scans at a random offset, or 0 to
      scan from the beginning. Defaults to 1.
  FORGE_PARALLELISM
      Run up to this many issue workflows in parallel. Defaults to 1. The
      maximum is 4.
  FORGE_REVIEW_LABEL
      Review only PRs with this label. If unset, each generated PR label is
      reviewed every cycle.
  FORGE_LIBRARY_REVIEW_LIMIT, FORGE_JAVAC_REVIEW_LIMIT, FORGE_JAVA_RUN_REVIEW_LIMIT,
  FORGE_NI_RUN_REVIEW_LIMIT, FORGE_BULK_UPDATE_REVIEW_LIMIT
      Override FORGE_REVIEW_LIMIT for one default review queue.

Examples:
  $0
  $0 master
  $0 --javac-limit 3 --new-limit 1
  $0 --once --branch master
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
    export FORGE_REVIEW_MODEL="$REVIEW_MODEL"

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

    run_step "Processing configured work queues via forge_metadata." \
        "$PYTHON_BIN" "$SCRIPT_DIR/forge_metadata.py" "${forge_metadata_args[@]}"
}

run_cycle() {
    local iteration="${DO_UP_TO_DATE_WORK_ITERATION:-0}"

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
    fi
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
        --branch)
            require_option_value "$1" "${2:-}"
            BRANCH_ARG="$2"
            shift 2
            ;;
        --branch=*)
            BRANCH_ARG="${1#*=}"
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
        --)
            shift
            if [[ "$#" -gt 1 || -n "$BRANCH_ARG" ]]; then
                usage >&2
                exit 1
            fi
            BRANCH_ARG="${1:-}"
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

require_positive_integer "DO_WORK_SLEEP_SECONDS" "$SLEEP_SECONDS"
require_positive_integer "DO_WORK_CLEAN_LOCAL_REPOSITORIES_EVERY" "$CLEAN_LOCAL_REPOSITORIES_EVERY"
require_nonnegative_integer "FORGE_JAVAC_WORK_LIMIT" "$JAVAC_WORK_LIMIT"
require_nonnegative_integer "FORGE_JAVA_RUN_WORK_LIMIT" "$JAVA_RUN_WORK_LIMIT"
require_nonnegative_integer "FORGE_NI_RUN_WORK_LIMIT" "$NI_RUN_WORK_LIMIT"
require_nonnegative_integer "FORGE_LIBRARY_UPDATE_WORK_LIMIT" "$LIBRARY_UPDATE_WORK_LIMIT"
require_nonnegative_integer "FORGE_WORK_LIMIT" "$WORK_LIMIT"
require_nonnegative_integer "FORGE_REVIEW_LIMIT" "$REVIEW_LIMIT"
require_parallelism "$PARALLELISM"

if [[ "$RANDOM_WORK_OFFSET" != "0" && "$RANDOM_WORK_OFFSET" != "1" ]]; then
    echo "FORGE_RANDOM_WORK_OFFSET must be 0 or 1." >&2
    exit 1
fi

export_work_configuration
run_cycle

if [[ "$RUN_ONCE" == "1" ]]; then
    exit 0
fi

log "Sleeping for ${SLEEP_SECONDS} second(s)."
sleep "$SLEEP_SECONDS"
exec "$SCRIPT_DIR/do_up_to_date_work.sh" "${ORIGINAL_ARGS[@]}"
