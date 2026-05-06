#!/usr/bin/env bash
set -u -o pipefail

if [[ $# -ne 1 ]]; then
  printf 'Usage: %s <group:artifact:version>\n' "$0" >&2
  exit 2
fi

COORDINATE="$1"

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
FORGE_DIR="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd -- "${FORGE_DIR}/.." && pwd)"

BRANCH="${BRANCH:-ai/vjovanov/pgo-per-call-unreachable-stats}"
WORKTREE_ROOT="${WORKTREE_ROOT:-/tmp}"
STRATEGY="${STRATEGY:-pgo_profile_driven_exploration_main_sources_pi_gpt-5.5}"
PYTHON_BIN="${PYTHON_BIN:-python3}"
METRICS_REPO_PATH="${METRICS_REPO_PATH:-}"
LOG_FILE="${LOG_FILE:-}"

BUILD_LOGIC_FIX_PATH="tests/tck-build-logic/src/main/groovy/org/graalvm/internal/tck/harness/tasks/GeneratePgoDynamicAccessNearCallReportInvocationTask.java"

slug_for_coordinate() {
  printf '%s' "$1" | tr ':._' '---' | tr -cs 'A-Za-z0-9-' '-'
}

sync_local_build_logic_fix() {
  local worktree="$1"
  local diff_file
  diff_file="$(mktemp)"
  git -C "$REPO_ROOT" diff -- "$BUILD_LOGIC_FIX_PATH" > "$diff_file"
  if [[ -s "$diff_file" ]]; then
    if git -C "$worktree" apply --check "$diff_file" >/dev/null 2>&1; then
      git -C "$worktree" apply "$diff_file"
    fi
  fi
  rm -f "$diff_file"
}

remove_existing_worktree() {
  local worktree="$1"
  git -C "$REPO_ROOT" worktree prune

  if git -C "$REPO_ROOT" worktree list --porcelain | grep -Fxq "worktree $worktree"; then
    printf 'Removing existing worktree: %s\n' "$worktree"
    if ! git -C "$REPO_ROOT" worktree remove --force "$worktree"; then
      printf 'Git worktree removal failed; removing stale path directly: %s\n' "$worktree" >&2
      rm -rf "$worktree"
      git -C "$REPO_ROOT" worktree prune
    fi
    return
  fi

  if [[ -e "$worktree" ]]; then
    printf 'Removing existing worktree path: %s\n' "$worktree"
    rm -rf "$worktree"
  fi
}

run_coordinate() {
  local coordinate="$1"
  local slug
  local worktree
  local log_file
  local metrics_args=()

  slug="$(slug_for_coordinate "$coordinate")"
  worktree="${WORKTREE_ROOT}/grm-pgo-${slug}"

  mkdir -p "$WORKTREE_ROOT"
  if [[ -n "$METRICS_REPO_PATH" ]]; then
    mkdir -p "$METRICS_REPO_PATH"
    metrics_args=(--metrics-repo-path "$METRICS_REPO_PATH")
  fi
  if [[ -n "$LOG_FILE" ]]; then
    log_file="$LOG_FILE"
  elif [[ -n "$METRICS_REPO_PATH" ]]; then
    log_file="${METRICS_REPO_PATH}/run.log"
  else
    log_file="${worktree}.log"
  fi
  mkdir -p "$(dirname -- "$log_file")"

  {
    remove_existing_worktree "$worktree"
    git -C "$REPO_ROOT" worktree add --detach "$worktree" "$BRANCH"

    sync_local_build_logic_fix "$worktree"

    printf 'PGO coverage strategy: %s\n' "$STRATEGY"
    printf 'Branch: %s\n' "$BRANCH"
    printf 'Coordinate: %s\n' "$coordinate"
    printf 'Worktree: %s\n' "$worktree"
    if [[ -n "$METRICS_REPO_PATH" ]]; then
      printf 'Metrics: %s\n' "$METRICS_REPO_PATH"
    fi
    printf 'Log: %s\n\n' "$log_file"

    (
      cd "$worktree/forge" || exit 1
      PYTHONUNBUFFERED=1 PYTHONPATH="$PWD" "$PYTHON_BIN" -u ai_workflows/improve_library_coverage.py \
        --coordinates "$coordinate" \
        "${metrics_args[@]}" \
        --strategy-name "$STRATEGY" \
        -v
    )
  } 2>&1 | tee "$log_file"

  return "${PIPESTATUS[0]}"
}

run_coordinate "$COORDINATE"
