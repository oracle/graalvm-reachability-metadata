#!/usr/bin/env bash
# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
# The template's default agent targets.
readonly DEFAULT_SETUP_AGENT="claude-code[medium]:sonnet"
readonly DEFAULT_WORKER_AGENT="pi[high]:openai-codex/gpt-5.6-luna"
readonly DEFAULT_REVIEW_AGENT="claude-code[high]:opus"

# Claim and run Forge issues until stopped, reviewing the open Forge pull requests.
# §FS-forge-issue-resolution-goal §AR-forge-workflow-pipeline.1 §FS-published-pr-review.1

usage() {
    cat <<USAGE
Usage: $0 [--setup-agent TARGET] [--worker-agent TARGET] [--review-agent TARGET]
          [--label LABEL[:COUNT]]... [--take-blocked-issues] [-- RHEI_RUN_OPTIONS...]

Purpose:
  Check the host, then until stopped: claim the most urgent eligible Forge
  issue and run its forge-metadata Rhei workspace with the browser dashboard.
  Every third run, and when no issue can be claimed, review up to three open
  Forge pull requests, oldest first, whose checks have finished: approve and
  arm auto-merge on a green one, or rerun, push a fix, or file an issue.

Options:
  -h, --help
      Show this help text.
  --setup-agent TARGET
      Rhei target of the setup state; it needs web search, so a claude-code
      target.
      Default: $DEFAULT_SETUP_AGENT
  --worker-agent TARGET
      Rhei target of the cover and fix states, with agent pi or claude-code.
      Default: $DEFAULT_WORKER_AGENT
  --review-agent TARGET
      Target of the pull-request review; it needs the web and the GraalVM
      history, so a claude-code target.
      Default: $DEFAULT_REVIEW_AGENT
  --label LABEL[:COUNT]
      Give this queue COUNT runs in a row, default 1; repeat for several. The
      queues take turns in the order given, and one with nothing to claim
      passes its turn on. Default: every queue in every run.
      Example: --label library-new-request:2 --label fails-java-run serves the
      new-library queue twice, then fails-java-run once, then starts over.
  --take-blocked-issues
      Fetch and claim issues with open blockers too; every other claim
      condition still holds. Default: off.
  -- RHEI_RUN_OPTIONS...
      Forward options to rhei run. The dashboard is always enabled.
USAGE
}

setup_agent="$DEFAULT_SETUP_AGENT"
worker_agent="$DEFAULT_WORKER_AGENT"
review_agent="$DEFAULT_REVIEW_AGENT"
claim_arguments=()
# The --label rotation: each queue label and its turns in a row (§AR-forge-workflow-pipeline.1).
labels=()
counts=()
add_label() {
    local label="${1%%:*}"
    local count=1
    local known
    [[ "$1" != *:* ]] || count="${1#*:}"
    if [[ ! "$count" =~ ^[1-9][0-9]*$ ]]; then
        printf 'ERROR: --label %s: COUNT must be a positive integer.\n' "$1" >&2
        exit 2
    fi
    for known in "${labels[@]}"; do
        if [[ "$known" == "$label" ]]; then
            printf 'ERROR: --label %s: the label is given twice.\n' "$label" >&2
            exit 2
        fi
    done
    labels+=("$label")
    counts+=("$count")
}
while (( $# > 0 )); do
    case "$1" in
        -h|--help)
            usage
            exit 0
            ;;
        --take-blocked-issues)
            claim_arguments+=(--take-blocked-issues)
            shift
            ;;
        --setup-agent|--worker-agent|--review-agent|--label)
            if (( $# < 2 )); then
                printf 'ERROR: %s needs a value.\n' "$1" >&2
                exit 2
            fi
            case "$1" in
                --setup-agent) setup_agent="$2" ;;
                --worker-agent) worker_agent="$2" ;;
                --review-agent) review_agent="$2" ;;
                --label) add_label "$2" ;;
            esac
            shift 2
            ;;
        --)
            shift
            break
            ;;
        *)
            printf 'ERROR: Unexpected argument: %s\n' "$1" >&2
            usage >&2
            exit 2
            ;;
    esac
done
rhei_arguments=("$@")

# <agent>[<mode>]:[<provider>/]<model>; Rhei splits the provider off at the last slash.
readonly TARGET_PATTERN='^(pi|claude-code)(\[[A-Za-z0-9_-]+\])?:((.*)/)?([^/]+)$'
if [[ ! "$worker_agent" =~ $TARGET_PATTERN ]]; then
    printf 'ERROR: --worker-agent %s: the agent must be pi or claude-code.\n' "$worker_agent" >&2
    exit 2
fi
worker_family="${BASH_REMATCH[1]}"
worker_provider="${BASH_REMATCH[4]}"
worker_model="${BASH_REMATCH[5]}"
worker_executable="pi"
[[ "$worker_family" == "pi" ]] || worker_executable="claude"
if [[ ! "$setup_agent" =~ $TARGET_PATTERN || "${BASH_REMATCH[1]}" != "claude-code" ]]; then
    printf 'ERROR: --setup-agent %s: setup needs web search, so it must be a claude-code target.\n' \
        "$setup_agent" >&2
    exit 2
fi
setup_model="${BASH_REMATCH[5]}"
# §FS-forge-agent-runtime-selection: the review needs the web and the GraalVM history.
if [[ ! "$review_agent" =~ ^claude-code(\[[A-Za-z0-9_-]+\])?:([^/]+)$ ]]; then
    printf 'ERROR: --review-agent %s: the review must be a claude-code target.\n' "$review_agent" >&2
    exit 2
fi
review_model="${BASH_REMATCH[2]}"

# Gradle and the native-image agent run on JAVA_HOME, so both name the checked GraalVM.
export GRAALVM_HOME="${GRAALVM_HOME:-${JAVA_HOME:-}}"
export JAVA_HOME="${JAVA_HOME:-$GRAALVM_HOME}"

# shellcheck source=utility_scripts/rhei_launcher_host.sh
source "$SCRIPT_DIR/utility_scripts/rhei_launcher_host.sh"

python="$SCRIPT_DIR/.venv/bin/python"
[[ -x "$python" ]] || python="python3"
forge_metadata() {
    "$python" "$SCRIPT_DIR/forge_metadata.py" --setup-agent "$setup_agent" --worker-agent "$worker_agent" "$@"
}

review() {
    forge_metadata --review --review-agent "$review_agent" \
        || printf 'WARNING: The review failed; the next review reads the queue again.\n' >&2
}

# The rotation's current label and the turns it has had in a row.
turn=0
taken=0
# Run one cycle on the queue whose turn it is; a queue with nothing to claim passes the turn, and
# the rest of its turns, to the next. Returns 1 only when no queue claimed anything, leaving the
# rotation where it stood (§AR-forge-workflow-pipeline.1).
claim_next() {
    local attempt index status
    if (( ${#labels[@]} == 0 )); then
        forge_metadata "${claim_arguments[@]}" -- "${rhei_arguments[@]}"
        return
    fi
    for (( attempt = 0; attempt < ${#labels[@]}; attempt++ )); do
        index=$(( (turn + attempt) % ${#labels[@]} ))
        status=0
        forge_metadata --label "${labels[index]}" "${claim_arguments[@]}" \
            -- "${rhei_arguments[@]}" || status=$?
        (( status != 1 )) || continue
        (( attempt == 0 )) || { turn=$index; taken=0; }
        taken=$((taken + 1))
        if (( taken == counts[index] )); then
            turn=$(( (index + 1) % ${#labels[@]} ))
            taken=0
        fi
        return "$status"
    done
    return 1
}

# §FS-published-pr-review.1: review every third run and whenever nothing can be claimed.
readonly IDLE_SECONDS=600
readonly REVIEW_EVERY=3
cd "$SCRIPT_DIR"
runs=0
while true; do
    # Issue work and PR review, with all three agents, are checked before the claim; a failed
    # check exits here (§FS-forge-host-requirements).
    require_rhei_launcher_host forge "$SCRIPT_DIR" "$REPO_ROOT" \
        "$worker_family" "$worker_executable" "$worker_model" "$worker_provider" \
        claude-code claude "$setup_model" "$review_model"
    status=0
    claim_next || status=$?
    if (( status == 1 )); then
        review
        printf 'No issue to claim; looking again in %s seconds.\n' "$IDLE_SECONDS"
        sleep "$IDLE_SECONDS"
        continue
    fi
    (( status == 0 )) || printf 'ERROR: The cycle failed with status %s.\n' "$status" >&2
    runs=$((runs + 1))
    (( runs % REVIEW_EVERY != 0 )) || review
done
