#!/bin/bash
# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

set -u

TIMEOUT="5m"
SOFT_DEADLINE="${GVM_TCK_SOFT_DEADLINE:-}"

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

parse_duration_to_seconds() {
  local duration="$1"

  if [[ "$duration" =~ ^([0-9]+)([smhd]?)$ ]]; then
    local value="${BASH_REMATCH[1]}"
    local unit="${BASH_REMATCH[2]}"
    case "$unit" in
      "" | "s")
        echo "$value"
        ;;
      "m")
        echo $((value * 60))
        ;;
      "h")
        echo $((value * 3600))
        ;;
      "d")
        echo $((value * 86400))
        ;;
      *)
        echo "Unsupported duration unit in '$duration'." >&2
        exit 1
        ;;
    esac
    return
  fi

  echo "Unsupported duration format '$duration'. Use an integer with optional s/m/h/d suffix." >&2
  exit 1
}

SOFT_DEADLINE_SECONDS=""
SOFT_DEADLINE_AT=""
if [ -n "$SOFT_DEADLINE" ]; then
  SOFT_DEADLINE_SECONDS="$(parse_duration_to_seconds "$SOFT_DEADLINE")"
  SOFT_DEADLINE_AT=$(($(date +%s) + SOFT_DEADLINE_SECONDS))
  echo "Soft deadline enabled: stopping after approximately $SOFT_DEADLINE of wall-clock time."
fi

should_stop_for_soft_deadline() {
  if [ -z "$SOFT_DEADLINE_AT" ]; then
    return 1
  fi

  local now
  now="$(date +%s)"
  if [ "$now" -ge "$SOFT_DEADLINE_AT" ]; then
    echo "SOFT-DEADLINE: Stopping after completed versions because $SOFT_DEADLINE has elapsed."
    return 0
  fi

  return 1
}

run_multiple_attempts() {
  local stage="$1"
  local max_attempts="$2"
  local gradle_command="$3"
  local report_failed="${4:-1}"

  echo "$DELIMITER"
  echo " $TEST_COORDINATES:$VERSION stage $stage"
  echo "$DELIMITER"

  local attempt=0
  local result=0

  while [ $attempt -lt "$max_attempts" ]; do
    local native_image_mode_prefix=""
    if [ -n "${GVM_TCK_NATIVE_IMAGE_MODE:-}" ]; then
      native_image_mode_prefix="GVM_TCK_NATIVE_IMAGE_MODE=\"$GVM_TCK_NATIVE_IMAGE_MODE\" "
    fi
    local cmd_str="${native_image_mode_prefix}GVM_TCK_LV=\"$VERSION\" ./gradlew clean $gradle_command -Pcoordinates=\"$TEST_COORDINATES\""
    if [ $attempt -gt 0 ]; then
      echo "Re-running stage '$stage' (attempt $((attempt + 1))/$max_attempts)"
    fi

    timeout --signal=QUIT --kill-after=20s "$TIMEOUT" bash -c "$cmd_str"
    result=$?

    if [ "$result" -eq 0 ]; then
      return 0
    fi

    if [ "$result" -eq 124 ] || [ "$result" -eq 131 ] || [ "$result" -eq 137 ]; then
      echo "⚠️  TIMEOUT: '$stage' for $VERSION took longer than $TIMEOUT (exit code $result)."
    else
      echo "❌ ERROR: '$stage' for $VERSION failed with exit code $result."
    fi

    attempt=$((attempt + 1))
  done

  if [ "$report_failed" -ne 0 ]; then
    echo "FAILED[$stage][$VERSION][$cmd_str]"
  else
    echo "SOFT-FAIL[$stage][$VERSION][$cmd_str]"
  fi
  return $result
}

for VERSION in "${VERSIONS[@]}"; do
  if should_stop_for_soft_deadline; then
    break
  fi

  echo "$DELIMITER"
  echo " Testing $TEST_COORDINATES:$VERSION"
  echo "$DELIMITER"

  if ! run_multiple_attempts "native-image run" 1 test 0; then
      # failing execution: bisect
      if ! run_multiple_attempts "javac compile" 1 compileTestJava; then
        break
      fi

      if ! run_multiple_attempts "java run" 1 javaTest; then
        break
      fi

      if ! run_multiple_attempts "native-image build" 1 nativeTestCompile; then
        break
      fi

      if ! run_multiple_attempts "native-image run" 3 test; then
        break
      fi
  fi

  echo "PASSED:$VERSION"
done

exit 0
