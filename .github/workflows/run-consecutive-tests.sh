#!/bin/bash

# Usage: ./run-consecutive-tests.sh "<test-coordinates>" '[ "1.0", "2.0", "3.0" ]'

set -u
set -x

if [ $# -ne 2 ]; then
  echo "Usage: $0 <test-coordinates> <versions-json-array>"
  exit 1
fi

# Input parameters
TEST_COORDINATES="$1"
VERSIONS_JSON="$2"
PASSED_VERSIONS=()
FAILED_VERSION=""

# Parse JSON array into Bash array using jq
if ! command -v jq &> /dev/null; then
  echo "jq is required but not installed."
  exit 1
fi

# Remove surrounding single quotes if present (when called from workflow)
VERSIONS_JSON="${VERSIONS_JSON#"${VERSIONS_JSON%%[!\']*}"}"
VERSIONS_JSON="${VERSIONS_JSON%"${VERSIONS_JSON##*[!\']}"}"

# Parse versions with jq
readarray -t VERSIONS < <(echo "$VERSIONS_JSON" | jq -r '.[]')

for VERSION in "${VERSIONS[@]}"; do
  echo "Running test with GVM_TCK_LV=$VERSION and coordinates=$TEST_COORDINATES"
  GVM_TCK_LV="$VERSION" ./gradlew test -Pcoordinates="$TEST_COORDINATES"
  RESULT=$?
  if [ "$RESULT" -eq 0 ]; then
    PASSED_VERSIONS+=("$VERSION")
    echo "PASSED:$VERSION"
  else
    FAILED_VERSION="$VERSION"
    echo "FAILED:$VERSION"
    break
  fi
done

# Script ends here; output already provided in loop for workflows to process
exit 0
