#!/usr/bin/env bash

# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

set -euo pipefail

if [ "$#" -ne 1 ]; then
    echo "Usage: $0 <groupId>:<artifactId>:<version>"
    exit 1
fi

GAV="$1"
IFS=':' read -r GROUP ARTIFACT VERSION <<< "$GAV"

REMOTE_BASE_URL="https://raw.githubusercontent.com/oracle/graalvm-reachability-metadata/master/metadata"
REMOTE_INDEX_URL="$REMOTE_BASE_URL/$GROUP/$ARTIFACT/index.json"

INDEX_CONTENT=$(curl -fsSL "$REMOTE_INDEX_URL" 2>/dev/null || true)

if [[ -z "$INDEX_CONTENT" ]]; then
    echo "Library $GAV is NOT supported by the GraalVM Reachability Metadata repository."
    exit 1
fi

FOUND=$(
    awk -v ver="$VERSION" '
      /"tested-versions"[[:space:]]*:/ {inside=1; next}
      inside && /\]/ {inside=0}
      inside && $0 ~ "\"" ver "\"" {print "yes"}
    ' <<< "$INDEX_CONTENT"
)

if [ "$FOUND" = "yes" ]; then
    echo "Library $GAV is supported by the GraalVM Reachability Metadata repository."
else
    echo "Library $GAV is NOT supported by the GraalVM Reachability Metadata repository."
fi
