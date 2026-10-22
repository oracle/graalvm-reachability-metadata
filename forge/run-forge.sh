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

# Claim and run Forge issues until stopped, handling failing CI on the open Forge pull requests.
# §FS-forge-issue-resolution-goal §AR-forge-workflow-pipeline.1

usage() {
    cat <<USAGE
Usage: $0 [--setup-agent TARGET] [--worker-agent TARGET] [--label LABEL]... [-- RHEI_RUN_OPTIONS...]

Purpose:
  Check the host, then until stopped: claim the most urgent eligible Forge
  issue and run its forge-metadata Rhei workspace with the browser dashboard.
  Every third run, and when no issue can be claimed, run the CI pass: each open
  Forge pull request whose CI fails on a new head gets one diagnosis by the
  setup agent that ends in a rerun, a pushed fix, or an issue.

Options:
  -h, --help
      Show this help text.
  --setup-agent TARGET
      Rhei target of the setup state and the CI diagnosis; both need web search,
      so a claude-code target.
      Default: $DEFAULT_SETUP_AGENT
  --worker-agent TARGET
      Rhei target of the cover and fix states, with agent pi or claude-code.
      Default: $DEFAULT_WORKER_AGENT
  --label LABEL
      Fetch only this queue; repeat for several. Default: every queue.
  -- RHEI_RUN_OPTIONS...
      Forward options to rhei run. The dashboard is always enabled.
USAGE
}

setup_agent="$DEFAULT_SETUP_AGENT"
worker_agent="$DEFAULT_WORKER_AGENT"
label_arguments=()
while (( $# > 0 )); do
    case "$1" in
        -h|--help)
            usage
            exit 0
            ;;
        --setup-agent|--worker-agent|--label)
            if (( $# < 2 )); then
                printf 'ERROR: %s needs a value.\n' "$1" >&2
                exit 2
            fi
            case "$1" in
                --setup-agent) setup_agent="$2" ;;
                --worker-agent) worker_agent="$2" ;;
                --label) label_arguments+=(--label "$2") ;;
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

ci_pass() {
    forge_metadata --ci || printf 'WARNING: The CI pass failed; the next pass reads the pull requests again.\n' >&2
}

readonly IDLE_SECONDS=600
readonly CI_EVERY=3
cd "$SCRIPT_DIR"
runs=0
while true; do
    # A failed host check exits here, before the claim (§FS-forge-host-requirements).
    require_rhei_launcher_host forge "$SCRIPT_DIR" "$REPO_ROOT" \
        "$worker_family" "$worker_executable" "$worker_model" "$worker_provider" \
        claude-code claude "$setup_model"
    status=0
    forge_metadata "${label_arguments[@]}" -- "$@" || status=$?
    if (( status == 1 )); then
        ci_pass
        printf 'No issue to claim; looking again in %s seconds.\n' "$IDLE_SECONDS"
        sleep "$IDLE_SECONDS"
        continue
    fi
    (( status == 0 )) || printf 'ERROR: The cycle failed with status %s.\n' "$status" >&2
    runs=$((runs + 1))
    (( runs % CI_EVERY != 0 )) || ci_pass
done
