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
# The template's default worker_agent, pi[high]:openai-codex/gpt-5.6-luna.
readonly WORKER_AGENT="pi"
readonly WORKER_MODEL="gpt-5.6-luna"
readonly WORKER_PROVIDER="openai-codex"

# Claim the next real-use-test issue and run its workflow with the Rhei dashboard.
# §FS-real-use-test-generation.1 §AR-real-use-test-launcher

usage() {
    cat <<USAGE
Usage: $0 [-- RHEI_RUN_OPTIONS...]

Purpose:
  Check the host, claim the most urgent eligible real-use-test issue, and run
  its Rhei workspace with the browser dashboard.

Options:
  -h, --help
      Show this help text.
  -- RHEI_RUN_OPTIONS...
      Forward options to rhei run. The dashboard is always enabled.
USAGE
}

case "${1:-}" in
    -h|--help)
        usage
        exit 0
        ;;
    --)
        shift
        ;;
    "")
        ;;
    *)
        printf 'ERROR: Unexpected argument: %s\n' "$1" >&2
        usage >&2
        exit 2
        ;;
esac

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
    "$WORKER_AGENT" "$WORKER_MODEL" "$WORKER_PROVIDER"

python="$FORGE_DIR/.venv/bin/python"
[[ -x "$python" ]] || python="python3"
claim() {
    PYTHONPATH="$FORGE_DIR" "$python" "$PROGRAMS/claim.py" "$@"
}

issue_number="$(claim)"
printf 'Claimed real-use-test issue #%s.\n' "$issue_number"

workspace="runs/real-use-$issue_number"
cd "$SCRIPT_DIR"
if ! rhei instantiate real-use-tests "issue_number=$issue_number" --output "$workspace"; then
    printf 'ERROR: Cannot create %s; releasing issue #%s.\n' "$workspace" "$issue_number" >&2
    claim --release "$issue_number"
    exit 1
fi
exec rhei run "$workspace" --dashboard "$@"
