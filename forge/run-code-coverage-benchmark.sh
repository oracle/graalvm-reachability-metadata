#!/usr/bin/env bash
# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly RUNNER="$SCRIPT_DIR/benchmarks/code_coverage_benchmark.py"

# Run the fixed matrix or retry locally preserved unpublished workspaces.
# §FS-code-coverage-benchmarking.2 §FS-code-coverage-benchmarking.3
if [[ "${1:-}" == "retry-pending" ]]; then
    shift
    exec python3 "$RUNNER" retry-pending "$@"
fi

exec python3 "$RUNNER" run "$@"
