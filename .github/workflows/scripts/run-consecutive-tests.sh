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
readonly SIGQUIT_WAIT=10       # Wait time after SIGQUIT for stacktrace dump

# Function to recursively find all descendant PIDs
get_all_descendants() {
  local parent_pid=$1
  local descendants=()

  # Get immediate children
  local children
  children=$(pgrep -P "$parent_pid" 2>/dev/null || true)

  for child in $children; do
    # Add child to list
    descendants+=("$child")
    # Recursively get descendants of this child
    local child_descendants
    child_descendants=$(get_all_descendants "$child")
    if [ -n "$child_descendants" ]; then
      descendants+=($child_descendants)
    fi
  done

  echo "${descendants[@]}"
}

# Function to monitor command execution with timeout handling
run_with_timeout() {
  local cmd_str="$1"
  local stage="$2"

  # Run command in background, creating a new process group
  eval "$cmd_str" &
  local cmd_pid=$!
  local cmd_pgid
  cmd_pgid=$(ps -o pgid= -p "$cmd_pid" 2>/dev/null | tr -d ' ')

  local elapsed=0
  local warned=false

  # Monitor the process
  while kill -0 "$cmd_pid" 2>/dev/null; do
    sleep 1
    elapsed=$((elapsed + 1))

    # Log warning when approaching timeout
    if [ $elapsed -ge $WARNING_THRESHOLD ] && [ "$warned" = false ]; then
      echo ""
      echo "=========================================="
      echo "WARNING: Test execution for stage '$stage' has been running for ${elapsed}s (threshold: ${WARNING_THRESHOLD}s)"
      echo "WARNING: Will timeout in $((TIMEOUT_DURATION - elapsed))s and attempt to capture stacktraces"
      echo "=========================================="
      echo ""
      warned=true
    fi

    # Handle timeout
    if [ $elapsed -ge $TIMEOUT_DURATION ]; then
      echo ""
      echo "=========================================="
      echo "TIMEOUT DETECTED"
      echo "=========================================="
      echo "ERROR: Timeout reached after ${TIMEOUT_DURATION}s for stage '$stage'"
      echo "ERROR: Command: $cmd_str"
      echo "ERROR: Main PID: $cmd_pid, Process Group ID: $cmd_pgid"
      echo ""

      # Get all descendant processes
      echo "Finding all descendant processes..."
      local all_descendants
      all_descendants=$(get_all_descendants "$cmd_pid")

      if [ -n "$all_descendants" ]; then
        echo "Descendant PIDs: $all_descendants"

        # Show process details for debugging
        echo ""
        echo "Process details:"
        for pid in $all_descendants; do
          if kill -0 "$pid" 2>/dev/null; then
            ps -p "$pid" -o pid,ppid,pgid,comm,args 2>/dev/null || true
          fi
        done
        echo ""
      else
        echo "No descendant processes found"
        echo ""
      fi

      # Send SIGQUIT to all descendant processes (especially Java/native-image)
      echo "Sending SIGQUIT (signal 3) to capture thread dumps and stacktraces..."
      local sigquit_count=0

      # Send to all descendants first (targets actual test processes)
      if [ -n "$all_descendants" ]; then
        for pid in $all_descendants; do
          if kill -0 "$pid" 2>/dev/null; then
            local cmd_name
            cmd_name=$(ps -p "$pid" -o comm= 2>/dev/null || echo "")
            if kill -QUIT "$pid" 2>/dev/null; then
              echo "  SIGQUIT sent to descendant PID $pid ($cmd_name)"
              sigquit_count=$((sigquit_count + 1))
            fi
          fi
        done
      fi

      # Also send to process group as backup
      if [ -n "$cmd_pgid" ]; then
        if kill -QUIT -"$cmd_pgid" 2>/dev/null; then
          echo "  SIGQUIT sent to process group -$cmd_pgid"
          sigquit_count=$((sigquit_count + 1))
        fi
      fi

      # Finally send to main process
      if kill -QUIT "$cmd_pid" 2>/dev/null; then
        local main_cmd_name
        main_cmd_name=$(ps -p "$cmd_pid" -o comm= 2>/dev/null || echo "")
        echo "  SIGQUIT sent to main PID $cmd_pid ($main_cmd_name)"
        sigquit_count=$((sigquit_count + 1))
      fi

      echo "SIGQUIT sent to $sigquit_count process(es)"
      echo ""

      # Wait for stacktrace dumps to complete and flush
      echo "Waiting ${SIGQUIT_WAIT}s for stacktrace dumps to complete..."
      local wait_elapsed=0
      while [ $wait_elapsed -lt $SIGQUIT_WAIT ]; do
        if ! kill -0 "$cmd_pid" 2>/dev/null; then
          echo "Main process terminated during stacktrace wait (after ${wait_elapsed}s)"
          break
        fi
        sleep 1
        wait_elapsed=$((wait_elapsed + 1))
      done
      echo ""

      # Force kill all remaining processes with SIGKILL (-9)
      echo "Force killing all remaining processes with SIGKILL..."
      local killed_count=0

      # Kill all descendants
      if [ -n "$all_descendants" ]; then
        for pid in $all_descendants; do
          if kill -0 "$pid" 2>/dev/null; then
            if kill -KILL "$pid" 2>/dev/null; then
              echo "  SIGKILL sent to descendant PID $pid"
              killed_count=$((killed_count + 1))
            fi
          fi
        done
      fi

      # Kill process group
      if [ -n "$cmd_pgid" ]; then
        if kill -KILL -"$cmd_pgid" 2>/dev/null; then
          echo "  SIGKILL sent to process group -$cmd_pgid"
          killed_count=$((killed_count + 1))
        fi
      fi

      # Kill main process
      if kill -0 "$cmd_pid" 2>/dev/null; then
        if kill -KILL "$cmd_pid" 2>/dev/null; then
          echo "  SIGKILL sent to main PID $cmd_pid"
          killed_count=$((killed_count + 1))
        fi
      fi

      echo "SIGKILL sent to $killed_count process(es)"
      sleep 1

      # Wait for process to be reaped
      wait "$cmd_pid" 2>/dev/null || true
      echo "=========================================="
      echo "TIMEOUT: Test execution exceeded ${TIMEOUT_DURATION}s limit"
      echo "=========================================="
      echo ""
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
