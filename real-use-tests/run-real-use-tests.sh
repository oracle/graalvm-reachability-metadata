#!/usr/bin/env bash
# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
readonly FORGE_DIR="$REPO_ROOT/forge"
readonly PROGRAMS="$SCRIPT_DIR/.agent-grounds/rhei/programs"
# The template's default worker_agent.
readonly DEFAULT_WORKER_AGENT="pi[high]:openai-codex/gpt-5.6-luna"

# Claim the next real-use-test issue and run its workflow with the Rhei dashboard.
# §FS-real-use-test-generation.1 §AR-real-use-test-launcher

usage() {
    cat <<USAGE
Usage: $0 [--worker-agent TARGET] [-- RHEI_RUN_OPTIONS...]

Purpose:
  Check the host, claim the most urgent eligible real-use-test issue, and run
  its Rhei workspace with the browser dashboard.

Options:
  -h, --help
      Show this help text.
  --worker-agent TARGET
      Rhei target for the agent states, with agent pi or claude-code.
      Default: $DEFAULT_WORKER_AGENT
      Example: claude-code[high]:opus
  -- RHEI_RUN_OPTIONS...
      Forward options to rhei run. The dashboard is always enabled.
USAGE
}

worker_agent="$DEFAULT_WORKER_AGENT"
while (( $# > 0 )); do
    case "$1" in
        -h|--help)
            usage
            exit 0
            ;;
        --worker-agent)
            if (( $# < 2 )); then
                printf 'ERROR: --worker-agent needs a target.\n' >&2
                exit 2
            fi
            worker_agent="$2"
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
if [[ ! "$worker_agent" =~ ^(pi|claude-code)(\[[A-Za-z0-9_-]+\])?:((.*)/)?([^/]+)$ ]]; then
    printf 'ERROR: --worker-agent %s: the agent must be pi or claude-code.\n' "$worker_agent" >&2
    printf 'Fix: Pass a target such as %s or claude-code[high]:opus.\n' "$DEFAULT_WORKER_AGENT" >&2
    exit 2
fi
agent_family="${BASH_REMATCH[1]}"
agent_provider="${BASH_REMATCH[4]}"
agent_model="${BASH_REMATCH[5]}"
agent_executable="pi"
[[ "$agent_family" == "pi" ]] || agent_executable="claude"

# Gradle and the native-image agent run on JAVA_HOME, so both name the checked GraalVM.
export GRAALVM_HOME="${GRAALVM_HOME:-${JAVA_HOME:-}}"
export JAVA_HOME="${JAVA_HOME:-$GRAALVM_HOME}"
if [[ -n "$JAVA_HOME" && "$(realpath "$JAVA_HOME")" != "$(realpath "$GRAALVM_HOME")" ]]; then
    printf 'ERROR: JAVA_HOME=%s and GRAALVM_HOME=%s name different installations.\n' \
        "$JAVA_HOME" "$GRAALVM_HOME" >&2
    printf 'Fix: Point both at the same GraalVM of JDK 25 or newer.\n' >&2
    exit 1
fi

# shellcheck source=../forge/utility_scripts/rhei_launcher_host.sh
source "$FORGE_DIR/utility_scripts/rhei_launcher_host.sh"
require_rhei_launcher_host real-use "$FORGE_DIR" "$REPO_ROOT" \
    "$agent_family" "$agent_executable" "$agent_model" "$agent_provider"

python="$FORGE_DIR/.venv/bin/python"
[[ -x "$python" ]] || python="python3"
claim() {
    PYTHONPATH="$FORGE_DIR" "$python" "$PROGRAMS/claim.py" "$@"
}

issue_number="$(claim)"
printf 'Claimed real-use-test issue #%s.\n' "$issue_number"

workspace="runs/real-use-$issue_number"
cd "$SCRIPT_DIR"
if ! rhei instantiate real-use-tests "issue_number=$issue_number" "worker_agent=$worker_agent" --output "$workspace"; then
    printf 'ERROR: Cannot create %s; releasing issue #%s.\n' "$workspace" "$issue_number" >&2
    claim --release "$issue_number"
    exit 1
fi
exec rhei run "$workspace" --dashboard "$@"
