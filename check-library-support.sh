#!/usr/bin/env bash
# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

# User-facing support lookup: check the published index for either exact
# tested-version support or an artifact-level `not-for-native-image` decision.
# §FS-repository-functional-spec.8.1.
# Index semantics come from §FS-repository-functional-spec.4.7.

set -euo pipefail

if [ "$#" -ne 1 ]; then
    echo "Usage: $0 <groupId>:<artifactId>:<version>"
    exit 1
fi

GAV="$1"

if ! [[ "$GAV" =~ ^[^:]+:[^:]+:[^:]+$ ]]; then
    echo "Invalid library format: '$GAV'"
    echo "Expected format: <groupId>:<artifactId>:<version>"
    exit 1
fi

IFS=':' read -r GROUP ARTIFACT VERSION <<< "$GAV"

REMOTE_BASE_URL="https://raw.githubusercontent.com/oracle/graalvm-reachability-metadata/master/metadata"
REMOTE_INDEX_URL="$REMOTE_BASE_URL/$GROUP/$ARTIFACT/index.json"

INDEX_CONTENT=$(curl -fsSL "$REMOTE_INDEX_URL" 2>/dev/null || true)

if [[ -z "$INDEX_CONTENT" ]]; then
    echo "STATUS=unsupported"
    echo "Library $GAV is NOT supported by the GraalVM Reachability Metadata repository."
    exit 1
fi

if grep -q '"not-for-native-image"[[:space:]]*:[[:space:]]*true' <<< "$INDEX_CONTENT"; then
    REASON=$(
        sed -n 's/.*"reason"[[:space:]]*:[[:space:]]*"\(.*\)".*/\1/p' <<< "$INDEX_CONTENT" | head -n 1
    )
    REPLACEMENT=$(
        sed -n 's/.*"replacement"[[:space:]]*:[[:space:]]*"\(.*\)".*/\1/p' <<< "$INDEX_CONTENT" | head -n 1
    )
    echo "STATUS=not-for-native-image"
    echo "Library $GAV is marked as NOT_FOR_NATIVE_IMAGE by the GraalVM Reachability Metadata repository."
    if [[ -n "$REASON" ]]; then
        echo "Reason: $REASON"
    fi
    if [[ -n "$REPLACEMENT" ]]; then
        echo "Replacement: $REPLACEMENT"
    fi
    exit 0
fi

FOUND=$(
    awk -v ver="$VERSION" '
      /"tested-versions"[[:space:]]*:/ {inside=1; next}
      inside && /\]/ {inside=0}
      inside && $0 ~ "\"" ver "\"" {print "yes"}
    ' <<< "$INDEX_CONTENT"
)

if [ "$FOUND" = "yes" ]; then
    echo "STATUS=supported"
    echo "Library $GAV is supported by the GraalVM Reachability Metadata repository."
else
    echo "STATUS=unsupported"
    echo "Library $GAV is NOT supported by the GraalVM Reachability Metadata repository."
    exit 1
fi
