#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 1 ]; then
    echo "Usage: $0 <groupId:artifactId:version>"
    exit 1
fi

GAV="$1"
IFS=':' read -r GROUP ARTIFACT VERSION <<< "$GAV"

INDEX_FILE="metadata/$GROUP/$ARTIFACT/index.json"

if [ ! -f "$INDEX_FILE" ]; then
    echo "Library $GAV is NOT supported by the GraalVM Reachability Metadata repository."
    exit 1
fi

# Check if the version exists in any tested-versions (exact match)
MATCH=$(jq --arg ver "$VERSION" 'any(.[]["tested-versions"][]?; . == $ver)' "$INDEX_FILE")

if [ "$MATCH" = "true" ]; then
    echo "Library $GAV is supported by the GraalVM Reachability Metadata repository.️"
else
    echo "Library $GAV is NOT supported by the GraalVM Reachability Metadata repository."
fi
