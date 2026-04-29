#!/usr/bin/env bash
# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PYTHON_BIN="${PYTHON_BIN:-python3}"
JAVAC_WORK_LIMIT="${FORGE_JAVAC_WORK_LIMIT:-1}"
JAVAC_WORK_STRATEGY_NAME="${FORGE_JAVAC_STRATEGY_NAME:-}"
JAVA_RUN_WORK_LIMIT="${FORGE_JAVA_RUN_WORK_LIMIT:-1}"
JAVA_RUN_WORK_STRATEGY_NAME="${FORGE_JAVA_RUN_STRATEGY_NAME:-}"
NI_RUN_WORK_LIMIT="${FORGE_NI_RUN_WORK_LIMIT:-1}"
NI_RUN_WORK_STRATEGY_NAME="${FORGE_NI_RUN_STRATEGY_NAME:-}"
WORK_LABEL="${FORGE_WORK_LABEL:-library-new-request}"
WORK_LIMIT="${FORGE_WORK_LIMIT:-1}"
RANDOM_WORK_OFFSET="${FORGE_RANDOM_WORK_OFFSET:-0}"
REVIEW_LABEL="${FORGE_REVIEW_LABEL:-}"
REVIEW_LIMIT="${FORGE_REVIEW_LIMIT:-1}"
REVIEW_MODEL="${FORGE_REVIEW_MODEL:-gpt-5.4}"
WORK_STRATEGY_NAME="${FORGE_STRATEGY_NAME:-dynamic_access_main_sources_pi_gpt-5.5}"
GITHUB_RATE_LIMIT_EXIT_CODE=75

if ! [[ "$WORK_LIMIT" =~ ^[0-9]+$ ]]; then
    echo "FORGE_WORK_LIMIT must be a non-negative integer." >&2
    exit 1
fi

if [[ "$RANDOM_WORK_OFFSET" != "0" && "$RANDOM_WORK_OFFSET" != "1" ]]; then
    echo "FORGE_RANDOM_WORK_OFFSET must be 0 or 1." >&2
    exit 1
fi

if ! [[ "$JAVAC_WORK_LIMIT" =~ ^[0-9]+$ ]]; then
    echo "FORGE_JAVAC_WORK_LIMIT must be a non-negative integer." >&2
    exit 1
fi

if ! [[ "$JAVA_RUN_WORK_LIMIT" =~ ^[0-9]+$ ]]; then
    echo "FORGE_JAVA_RUN_WORK_LIMIT must be a non-negative integer." >&2
    exit 1
fi

if ! [[ "$NI_RUN_WORK_LIMIT" =~ ^[0-9]+$ ]]; then
    echo "FORGE_NI_RUN_WORK_LIMIT must be a non-negative integer." >&2
    exit 1
fi

if ! [[ "$REVIEW_LIMIT" =~ ^[0-9]+$ ]]; then
    echo "FORGE_REVIEW_LIMIT must be a non-negative integer." >&2
    exit 1
fi

log() {
    printf '[%(%Y-%m-%d %H:%M:%S)T] %s\n' -1 "$1"
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
        exit 0
    fi

    return "$status"
}

export FORGE_JAVAC_WORK_LIMIT="$JAVAC_WORK_LIMIT"
export FORGE_JAVAC_STRATEGY_NAME="$JAVAC_WORK_STRATEGY_NAME"
export FORGE_JAVA_RUN_WORK_LIMIT="$JAVA_RUN_WORK_LIMIT"
export FORGE_JAVA_RUN_STRATEGY_NAME="$JAVA_RUN_WORK_STRATEGY_NAME"
export FORGE_NI_RUN_WORK_LIMIT="$NI_RUN_WORK_LIMIT"
export FORGE_NI_RUN_STRATEGY_NAME="$NI_RUN_WORK_STRATEGY_NAME"
export FORGE_WORK_LABEL="$WORK_LABEL"
export FORGE_WORK_LIMIT="$WORK_LIMIT"
export FORGE_RANDOM_WORK_OFFSET="$RANDOM_WORK_OFFSET"
export FORGE_STRATEGY_NAME="$WORK_STRATEGY_NAME"
if [[ -n "$REVIEW_LABEL" ]]; then
    export FORGE_REVIEW_LABEL="$REVIEW_LABEL"
else
    unset FORGE_REVIEW_LABEL
fi
export FORGE_REVIEW_LIMIT="$REVIEW_LIMIT"
export FORGE_REVIEW_MODEL="$REVIEW_MODEL"

FORGE_METADATA_ARGS=("--run-work-queues")
if [[ "${FORGE_IN_METADATA_REPO:-0}" == "1" ]]; then
    FORGE_METADATA_ARGS+=("--in-metadata-repo")
fi

run_step "Processing configured work queues via forge_metadata." \
    "$PYTHON_BIN" "$SCRIPT_DIR/forge_metadata.py" "${FORGE_METADATA_ARGS[@]}"
