# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The `audit` program: the local pre-push audit as one ordinary fix request.

The first visit checkpoints and asks `fix` to check every test and metadata
change against the repository rules; the return visit lets the tree decide: an
unchanged tree publishes, and a changed one reruns the native check and the
`finalize` lane once (§FS-local-branch-review, §AR-forge-workflow-pipeline.4.3).
There is no verdict and no second pass.
"""
from __future__ import annotations

import os

import finalize
import handoff
import native_check
from build_tools import changed_paths, checkpoint, coordinate_task
from exit_codes import AGENT_WORK, OK, STILL_FAILING
from run_state import RunState, load, save

SENDER: str = "audit"
NATIVE_TASKS: tuple[str, ...] = ("generateMetadata", "checkMetadataFiles", "nativeTest")


def brief(state: RunState) -> str:
    """The audit's one brief (§FS-local-branch-review.2); the rules themselves are the `fix` instruction's."""
    return (f"Check every test and metadata change since `{state.base_commit}` against the repository rules "
            "(§root/FS-test-contract.1, §root/FS-test-contract.2, §root/FS-test-contract.3, "
            "§root/FS-test-contract.4, §root/FS-test-contract.2.7) and the cheating patterns of "
            "§root/FS-contribution-contract.4 - scaffold-only or superficial tests, tests that bypass the public "
            "API, pinned tested-library versions, test-only types in shipped metadata, hand-added entries beyond "
            "what 2.7 allows, forbidden flags, and legacy `*-config.json` files - and fix each violation, "
            "then rerun the commands; change nothing that follows the rules.")


def commands(workspace: str, state: RunState) -> list[handoff.Failure]:
    """The native check and the `finalize` lane, every one passing, as the request's commands."""
    native: list[str] = [" ".join(["./gradlew", *coordinate_task(task, state.coordinate)]) for task in NATIVE_TASKS]
    return [handoff.Failure(command, "") for command in native + finalize.lane_commands(workspace, state)]


def changed(state: RunState) -> list[str]:
    """The published paths that differ from the audit checkpoint."""
    return [path for path in changed_paths(state.worktree, state.audit_checkpoint)
            if any(path == root or path.startswith(root.rstrip("/") + "/") for root in state.coordinate_paths)]


def audit(workspace: str) -> int:
    state: RunState = load(workspace)
    handoff.write_placeholder(workspace, SENDER)
    if state.audit_passed:
        print("[audit] already passed")
        return OK
    if not state.audit_checkpoint:
        state.audit_checkpoint = checkpoint(state.worktree, state.coordinate_paths, f"Audit {state.coordinate}")
        handoff.write_fix_request(workspace, state, SENDER, commands(workspace, state), [brief(state)])
        save(workspace, state)
        print(f"[audit] the audit request follows, from {state.audit_checkpoint}")
        return AGENT_WORK
    state.pending_request = None
    edited: list[str] = changed(state)
    if edited:
        print(f"[audit] the audit changed {', '.join(os.path.basename(path) for path in edited)}; rerunning the checks")
        failures: list[handoff.Failure] = (native_check.run_native_check(workspace, state, SENDER)
                                           or finalize.run_lane(workspace, state, SENDER))
        if failures:
            save(workspace, state)
            print(f"[audit] `{failures[0].command}` fails over the audit's edit")
            return STILL_FAILING
    state.audit_passed = True
    save(workspace, state)
    print(f"[audit] passes at {state.checkpoint}")
    return OK
