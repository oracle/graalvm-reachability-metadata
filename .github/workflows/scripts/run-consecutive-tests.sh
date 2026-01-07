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

# Timeout configuration (in seconds)
readonly TIMEOUT_DURATION=600  # 10 minutes
readonly WARNING_THRESHOLD=480 # 8 minutes (warn before timeout)
readonly SIGQUIT_WAIT=5        # Wait time after SIGQUIT for stacktrace dump

# Function to monitor command execution with timeout handling
run_with_timeout() {
  local cmd_str="$1"
  local stage="$2"

  # Run command in background, creating a new process group
  eval "$cmd_str" &
  local cmd_pid=$!
  local cmd_pgid
  cmd_pgid=$(ps -o pgid= -p "$cmd_pid" | tr -d ' ')

  local elapsed=0
  local warned=false

  # Monitor the process
  while kill -0 "$cmd_pid" 2>/dev/null; do
    sleep 1
    elapsed=$((elapsed + 1))

    # Log warning when approaching timeout
    if [ $elapsed -ge $WARNING_THRESHOLD ] && [ "$warned" = false ]; then
      echo "WARNING: Test execution for stage '$stage' has been running for ${elapsed}s (threshold: ${WARNING_THRESHOLD}s)"
      echo "WARNING: Will timeout in $((TIMEOUT_DURATION - elapsed))s and attempt to capture stacktraces"
      warned=true
    fi

    # Handle timeout
    if [ $elapsed -ge $TIMEOUT_DURATION ]; then
      echo "ERROR: Timeout reached after ${TIMEOUT_DURATION}s for stage '$stage'"
      echo "ERROR: Sending SIGQUIT (signal 3) to process group $cmd_pgid to capture stacktraces..."

      # Send SIGQUIT to entire process group to get Java thread dumps
      if [ -n "$cmd_pgid" ]; then
        kill -QUIT -"$cmd_pgid" 2>/dev/null || true
      fi

      # Wait for stacktrace dump to complete
      echo "Waiting ${SIGQUIT_WAIT}s for stacktrace dump..."
      sleep "$SIGQUIT_WAIT"

      # Check if process is still running
      if kill -0 "$cmd_pid" 2>/dev/null; then
        echo "Process still running after SIGQUIT, sending SIGTERM..."
        kill -TERM -"$cmd_pgid" 2>/dev/null || kill -TERM "$cmd_pid" 2>/dev/null || true
        sleep 2

        # Force kill if still running
        if kill -0 "$cmd_pid" 2>/dev/null; then
          echo "Process still running after SIGTERM, sending SIGKILL..."
          kill -KILL -"$cmd_pgid" 2>/dev/null || kill -KILL "$cmd_pid" 2>/dev/null || true
        fi
      fi

      # Wait for process to be reaped
      wait "$cmd_pid" 2>/dev/null || true
      echo "TIMEOUT: Test execution exceeded ${TIMEOUT_DURATION}s limit"
      return 124  # Standard timeout exit code
    fi
  done

  # Process completed before timeout
  wait "$cmd_pid"
  return $?
}

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

    run_with_timeout "$cmd_str" "$stage"
    result=$?

    if [ "$result" -eq 0 ]; then
      return 0
    fi

    if [ "$result" -eq 124 ]; then
      echo "TIMEOUT [$stage][$VERSION][$cmd_str]"
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

  if ! run_multiple_attempts "native-image run" 1 test; then
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
