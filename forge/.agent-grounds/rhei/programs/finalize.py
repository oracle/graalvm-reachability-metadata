# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The `finalize` program: style, stats, the reviewer gates, and the pre-publication gate, each once.

`spotlessApply` runs first, then `spotlessCheck`, `checkstyle`, and
`generateLibraryStats` over the coordinate, then the reviewer gates
(§FS-local-ci-equivalent-verification.1.1), and then the pre-publication gate
(§FS-local-ci-equivalent-verification.2) with no agent fixup of its own; the
first failing step becomes the fix request (§AR-forge-workflow-pipeline.4).
`audit` reruns the same lane over its edit (§AR-forge-workflow-pipeline.4.3).
"""
from __future__ import annotations

from utility_scripts.local_ci_verification import (
    CommandRecord,
    LocalCIVerificationError,
    LocalCIVerificationResult,
    run_local_ci_verification,
)

import gates
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


def step_arguments(step: list[str], state: RunState) -> list[str]:
    return step if step[0].startswith("spotless") else coordinate_task(step[0], state.coordinate)


def lane_commands(workspace: str, state: RunState) -> list[str]:
    """The lane as the commands an agent would type, for a request that asks to rerun it."""
    commands: list[str] = [" ".join(["./gradlew", *step_arguments(step, state)]) for step in STEPS]
    return commands + [gates.gate_command(workspace),
                       " ".join(["./gradlew", *coordinate_task("validateIndexFiles", state.coordinate)])]


def run_lane(workspace: str, state: RunState, prefix: str) -> list[handoff.Failure]:
    """Run each step once, stopping at the first that fails; a pass checkpoints and records the gate."""
    for step in STEPS:
        result: TaskResult = run_gradle(state.worktree, step_arguments(step, state),
                                        runtime_path(workspace, "logs", f"{prefix}-{step[0]}.log"))
        if not result.ok:
            return [handoff.Failure(result.command, result.tail)]
    breached: list[handoff.Failure] = gates.failures(workspace, state)
    if breached:
        return breached
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
            return [handoff.Failure(f"pre-publication gate {error.result.failure_gate}", str(error))]
        failed: CommandRecord = error.result.commands[-1]
        return [handoff.Failure(" ".join(failed.command), failed.output_excerpt)]
    state.local_ci = verification.to_metrics()
    return []


def finalize(workspace: str) -> int:
    state: RunState = load(workspace)
    handoff.write_placeholder(workspace, SENDER)
    failures: list[handoff.Failure] = run_lane(workspace, state, SENDER)
    if not failures:
        state.pending_request = None
        save(workspace, state)
        print(f"[finalize] every check passes at {state.checkpoint}")
        return OK
    commands: str = ", ".join(sorted({failure.command for failure in failures}))
    if handoff.guard_tripped(state, SENDER):
        save(workspace, state)
        print(f"[finalize] `{commands}` still fails after its fix")
        return STILL_FAILING
    handoff.write_fix_request(workspace, state, SENDER, failures)
    save(workspace, state)
    print(f"[finalize] `{commands}` fails; a fix request follows")
    return AGENT_WORK
