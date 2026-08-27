#!/usr/bin/env bash
# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly REPO="oracle/graalvm-reachability-metadata"
readonly ISSUE_LABEL="code-coverage-improvement"

# Launch one queued code-coverage run with its live Rhei dashboard.
# §FS-forge-scope §AR-code-coverage-improvement.2

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

if ! command -v gh >/dev/null 2>&1; then
    printf 'ERROR: gh is required and must be available on PATH.\n' >&2
    exit 1
fi
if ! command -v rhei >/dev/null 2>&1; then
    printf 'ERROR: rhei is required and must be available on PATH.\n' >&2
    exit 1
fi

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
