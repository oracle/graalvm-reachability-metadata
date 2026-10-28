# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The `repair` program: "make this version work", for a version that already has tests.

It runs `javaTest` and the native check, and any failure is one fix request
(§AR-forge-workflow-pipeline.4). `setup-check` has already run the setup tail,
and a new library never comes here (§AR-forge-workflow-pipeline.4.1).
"""
from __future__ import annotations

import sys

from utility_scripts.metadata_index import find_index_entry_for_version

import handoff
import native_check
from build_tools import TaskResult, checkpoint, coordinate_task, run_gradle
from exit_codes import AGENT_WORK, BAD_INPUT, OK, STILL_FAILING
from run_state import RunState, load, runtime_path, save
from setup_check import UNRESOLVED

SENDER: str = "repair"


def repair(workspace: str) -> int:
    state: RunState = load(workspace)
    handoff.write_placeholder(workspace, SENDER)
    if find_index_entry_for_version(state.worktree, state.group, state.artifact, state.version) is None:
        print(f"ERROR: {state.index_path} has no entry for {state.version}.", file=sys.stderr)
        return BAD_INPUT
    failures: list[handoff.Failure] = []
    tested: TaskResult = run_gradle(
        state.worktree, coordinate_task("javaTest", state.coordinate), runtime_path(workspace, "logs", "repair-javaTest.log"),
    )
    if not tested.ok:
        if UNRESOLVED.search(tested.output):
            print(f"ERROR: a declared dependency does not resolve:\n{tested.tail}", file=sys.stderr)
            return BAD_INPUT
        # The skipped native check gets no request of its own (§AR-forge-workflow-pipeline.5).
        failures.append(handoff.Failure(tested.command, tested.tail, blocked=native_check.blocked_commands(state)))
    else:
        failures = native_check.run_native_check(workspace, state, SENDER)
    if not failures:
        state.pending_request = None
        state.checkpoint = checkpoint(state.worktree, state.coordinate_paths, f"Repair {state.coordinate}")
        save(workspace, state)
        print(f"[repair] {state.coordinate} works at {state.checkpoint}")
        return OK
    if handoff.guard_tripped(state, SENDER):
        print("[repair] the version still fails after its fix")
        return STILL_FAILING
    handoff.write_fix_request(workspace, state, SENDER, failures)
    save(workspace, state)
    print(f"[repair] {state.coordinate} fails; a fix request follows")
    return AGENT_WORK
