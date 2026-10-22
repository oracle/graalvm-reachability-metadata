# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The `finalize` program: style, stats, and the pre-publication gate, each once.

`spotlessApply` runs first, then `spotlessCheck`, `checkstyle`, and
`generateLibraryStats` over the coordinate, and then the pre-publication gate
(§FS-local-ci-equivalent-verification.2) with no agent fixup of its own; the
first failure becomes the fix request (§AR-forge-workflow-pipeline.4).
"""
from __future__ import annotations

from utility_scripts.local_ci_verification import (
    CommandRecord,
    LocalCIVerificationError,
    LocalCIVerificationResult,
    run_local_ci_verification,
)

import handoff
from build_tools import TaskResult, checkpoint, coordinate_task, run_gradle
from exit_codes import AGENT_WORK, OK, STILL_FAILING
from run_state import RunState, load, runtime_path, save

SENDER: str = "finalize"
STEPS: tuple[list[str], ...] = (
    ["spotlessApply"],
    ["spotlessCheck"],
    ["checkstyle"],
    ["generateLibraryStats"],
)


def first_failure(workspace: str, state: RunState) -> handoff.Failure | None:
    """Run each step once, stopping at the first that fails."""
    for step in STEPS:
        arguments: list[str] = step if step[0].startswith("spotless") else coordinate_task(step[0], state.coordinate)
        result: TaskResult = run_gradle(state.worktree, arguments, runtime_path(workspace, "logs", f"finalize-{step[0]}.log"))
        if not result.ok:
            return handoff.Failure(result.command, result.tail)
    state.checkpoint = checkpoint(state.worktree, state.coordinate_paths, f"Finalize {state.coordinate}")
    try:
        verification: LocalCIVerificationResult = run_local_ci_verification(
            repo_path=state.worktree,
            coordinates=state.coordinate,
            base_commit=state.base_commit,
            max_fixup_attempts=0,
        )
    except LocalCIVerificationError as error:
        if not error.result.commands:
            return handoff.Failure(f"pre-publication gate {error.result.failure_gate}", str(error))
        failed: CommandRecord = error.result.commands[-1]
        return handoff.Failure(" ".join(failed.command), failed.output_excerpt)
    state.local_ci = verification.to_metrics()
    return None


def finalize(workspace: str) -> int:
    state: RunState = load(workspace)
    handoff.write_placeholder(workspace, SENDER)
    failure: handoff.Failure | None = first_failure(workspace, state)
    if failure is None:
        state.pending_request = None
        save(workspace, state)
        print(f"[finalize] every check passes at {state.checkpoint}")
        return OK
    if handoff.guard_tripped(state, SENDER):
        save(workspace, state)
        print(f"[finalize] `{failure.command}` still fails after its fix")
        return STILL_FAILING
    handoff.write_fix_request(workspace, state, SENDER, [failure])
    save(workspace, state)
    print(f"[finalize] `{failure.command}` fails; a fix request follows")
    return AGENT_WORK
