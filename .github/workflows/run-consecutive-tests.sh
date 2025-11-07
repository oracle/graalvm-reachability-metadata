#!/bin/bash

# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

set -u

if [ $# -ne 2 ]; then
  echo "Usage: $0 <test-coordinates> <versions-json-array>"
  exit 1
fi

# Input parameters
TEST_COORDINATES="$1"
VERSIONS_JSON="$2"

# Remove surrounding single quotes if present (when called from workflow)
VERSIONS_JSON="${VERSIONS_JSON#"${VERSIONS_JSON%%[!\']*}"}"
VERSIONS_JSON="${VERSIONS_JSON%"${VERSIONS_JSON##*[!\']}"}"

# Parse versions with jq
readarray -t VERSIONS < <(echo "$VERSIONS_JSON" | jq -r '.[]')
export DELIMITER="========================================================================================"

run_multiple_attempts() {
  local stage="$1"
  local max_attempts="$2"
  local gradle_command="$3"

  echo "$DELIMITER"
  echo " $TEST_COORDINATES:$VERSION stage $stage"
  echo "$DELIMITER"

  local attempt=0
  local result=0

  while [ $attempt -lt "$max_attempts" ]; do
    local cmd_str="GVM_TCK_LV=\"$VERSION\" ./gradlew clean $gradle_command -Pcoordinates=\"$TEST_COORDINATES\""
    if [ $attempt -gt 0 ]; then
      echo "Re-running stage '$stage' (attempt $((attempt + 1))/$max_attempts)"
    fi

    eval "$cmd_str"
    result=$?

    if [ "$result" -eq 0 ]; then
      return 0
    fi

    attempt=$((attempt + 1))
  done

  echo "FAILED [$stage][$VERSION][$cmd_str]"
  return $result
}

for VERSION in "${VERSIONS[@]}"; do
  echo "$DELIMITER"
  echo " Testing $TEST_COORDINATES:$VERSION"
  echo "$DELIMITER"


  if ! run_multiple_attempts "javac compile" 1 javac; then
    break
  fi

  if ! run_multiple_attempts "native-image build" 1 nativeTestCompile; then
    break
  fi

  if ! run_multiple_attempts "native-image run" 3 test; then
    break
  fi

  echo "PASSED:$VERSION"
done

exit 0
