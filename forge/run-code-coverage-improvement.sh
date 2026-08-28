#!/usr/bin/env bash
# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
readonly REPO="oracle/graalvm-reachability-metadata"
readonly ISSUE_LABEL="code-coverage-improvement"
readonly MIN_GH_VERSION="2.24.0"
readonly COVERAGE_AGENT="pi"
readonly COVERAGE_AGENT_MODEL="gpt-5.6-luna"
readonly COVERAGE_AGENT_PROVIDER="openai-codex"

# Launch one queued code-coverage run with its live Rhei dashboard.
# §FS-forge-scope §FS-forge-host-requirements §AR-code-coverage-improvement.2

usage() {
    cat <<EOF
Usage: $0 [-- RHEI_RUN_OPTIONS...]

Purpose:
  Select the highest-priority eligible code-coverage-improvement issue,
  instantiate its Rhei workspace, and execute it with the browser dashboard.

Options:
  -h, --help
      Show this help text.
  -- RHEI_RUN_OPTIONS...
      Forward options to rhei run. The dashboard is always enabled.

Examples:
  $0
  $0 -- --parallel 2
EOF
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

version_at_least() {
    local current="$1"
    local required="$2"
    local current_major current_minor current_patch
    local required_major required_minor required_patch

    IFS=. read -r current_major current_minor current_patch <<< "$current"
    IFS=. read -r required_major required_minor required_patch <<< "$required"
    (( 10#$current_major > 10#$required_major
        || (10#$current_major == 10#$required_major && 10#$current_minor > 10#$required_minor)
        || (10#$current_major == 10#$required_major && 10#$current_minor == 10#$required_minor
            && 10#$current_patch >= 10#$required_patch) ))
}

if ! command -v gh >/dev/null 2>&1; then
    printf 'ERROR: gh is required and must be available on PATH.\n' >&2
    exit 1
fi
gh_version_output="$(gh version 2>&1)" || {
    printf 'ERROR: gh version failed: %s\n' "$gh_version_output" >&2
    exit 1
}
if [[ ! "$gh_version_output" =~ gh[[:space:]]version[[:space:]]([0-9]+[.][0-9]+[.][0-9]+) ]]; then
    printf 'ERROR: Cannot determine the installed gh version from: %s\n' "$gh_version_output" >&2
    exit 1
fi
gh_version="${BASH_REMATCH[1]}"
if ! version_at_least "$gh_version" "$MIN_GH_VERSION"; then
    printf 'ERROR: gh %s is too old; gh %s or newer is required because issue selection reads --json projectItems.\n' \
        "$gh_version" "$MIN_GH_VERSION" >&2
    exit 1
fi
if ! command -v python3 >/dev/null 2>&1; then
    printf 'ERROR: python3 is required and must be available on PATH.\n' >&2
    exit 1
fi
if ! command -v rhei >/dev/null 2>&1; then
    printf 'ERROR: rhei is required and must be available on PATH.\n' >&2
    exit 1
fi

printf 'Validating code-coverage host requirements.\n'
python3 "$SCRIPT_DIR/utility_scripts/host_requirements.py" \
    --forge-dir "$SCRIPT_DIR" \
    --reachability-metadata-path "$REPO_ROOT" \
    --mode coverage \
    --analysis-agent "$COVERAGE_AGENT" \
    --analysis-family "$COVERAGE_AGENT" \
    --analysis-model "$COVERAGE_AGENT_MODEL" \
    --analysis-provider "$COVERAGE_AGENT_PROVIDER"

issue_number="$(
    gh issue list \
        --repo "$REPO" \
        --label "$ISSUE_LABEL" \
        --state open \
        --search 'no:assignee -is:blocked' \
        --limit 1000 \
        --json number,labels,projectItems \
        --jq '
            [.[] | select(any(.projectItems[]; .title == "GraalVM Reachability Metadata" and .status.name == "Todo"))]
            | sort_by(
                (if any(.labels[]; .name == "high-priority") then 0
                 elif any(.labels[]; .name == "priority") then 1
                 else 2 end),
                -.number
              )
            | first
            | .number
        '
)"

if [[ ! "$issue_number" =~ ^[1-9][0-9]*$ ]]; then
    printf 'ERROR: No eligible %s issue is available.\n' "$ISSUE_LABEL" >&2
    exit 1
fi

printf 'Selected %s issue #%s.\n' "$ISSUE_LABEL" "$issue_number"

cd "$SCRIPT_DIR"
exec rhei instantiate "$ISSUE_LABEL" \
    "issue_number=$issue_number" \
    --output "code-coverage-$issue_number" \
    --execute -- --dashboard "$@"
