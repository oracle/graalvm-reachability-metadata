#!/bin/bash

# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

set -u
set -x

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

for VERSION in "${VERSIONS[@]}"; do
  # check if javac works for the new version
  GVM_TCK_LV="$VERSION" ./gradlew clean javac -Pcoordinates="$TEST_COORDINATES"
  RESULT=$?
  if [ "$RESULT" -ne 0 ]; then
    echo "FAILED [javac compile]:$VERSION"
    break
  fi

  # check if native-image can be built
  GVM_TCK_LV="$VERSION" ./gradlew clean nativeTestCompile -Pcoordinates="$TEST_COORDINATES"
  RESULT=$?
    if [ "$RESULT" -ne 0 ]; then
      echo "FAILED [native-image build]:$VERSION"
      break
    fi

  echo "Running test with GVM_TCK_LV=$VERSION and coordinates=$TEST_COORDINATES"
  GVM_TCK_LV="$VERSION" ./gradlew test -Pcoordinates="$TEST_COORDINATES"
  RESULT=$?
  ATTEMPTS=1

  # maybe we failed because the test was flaky => try two more times to be sure
  while [ "$RESULT" -ne 0 ] && [ $ATTEMPTS -le 2 ]; do
    echo "Re-running the test with GVM_TCK_LV=$VERSION and coordinates=$TEST_COORDINATES"
    GVM_TCK_LV="$VERSION" ./gradlew clean test -Pcoordinates="$TEST_COORDINATES"
    RESULT=$?
    ATTEMPTS=$((ATTEMPTS + 1))
  done

  if [ "$RESULT" -eq 0 ]; then
    echo "PASSED:$VERSION"
  else
    echo "FAILED [native-image run]:$VERSION"
    break
  fi
done

exit 0
