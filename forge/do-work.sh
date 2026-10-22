#!/usr/bin/env bash
# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Keep this bootstrap wrapper stable: the Forge launcher loop is run-forge.sh
# (§AR-forge-workflow-pipeline.1).
exec "$SCRIPT_DIR/run-forge.sh" "$@"
