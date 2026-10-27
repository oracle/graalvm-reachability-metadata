# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The native check `repair` and `native` share, and the `native` program.

`generateMetadata` runs the JVM tests under the native-image agent and merges
what the filter allows into the shipped metadata; `checkMetadataFiles` and
`nativeTest` then run once each. The program judges only pass or fail and never
edits metadata itself; whatever still fails goes to one fix request
(§AR-forge-workflow-pipeline.5).
"""
from __future__ import annotations

import handoff
from build_tools import TaskResult, checkpoint, coordinate_task, run_gradle
from exit_codes import AGENT_WORK, OK, STILL_FAILING
from run_state import RunState, load, runtime_path, save

SENDER: str = "native"


def run_native_check(workspace: str, state: RunState, prefix: str) -> list[handoff.Failure]:
    """Run the three native-check commands and return the failing ones."""
    generated: TaskResult = run_gradle(
        state.worktree, coordinate_task("generateMetadata", state.coordinate),
        runtime_path(workspace, "logs", f"{prefix}-generateMetadata.log"),
    )
    if not generated.ok:
        # The JVM tests failed under the agent; the checks would only judge stale metadata.
        return [handoff.Failure(generated.command, generated.tail)]
    failures: list[handoff.Failure] = []
    for task in ("checkMetadataFiles", "nativeTest"):
        result: TaskResult = run_gradle(
            state.worktree, coordinate_task(task, state.coordinate),
            runtime_path(workspace, "logs", f"{prefix}-{task}.log"),
        )
        if not result.ok:
            failures.append(handoff.Failure(result.command, result.tail))
    return failures


def native(workspace: str) -> int:
    """The native check over the tests exploration added (§AR-forge-workflow-pipeline.4)."""
    state: RunState = load(workspace)
    handoff.write_placeholder(workspace, SENDER)
    failures: list[handoff.Failure] = run_native_check(workspace, state, SENDER)
    if not failures:
        state.pending_request = None
        state.checkpoint = checkpoint(state.worktree, state.contribution_paths(), f"Collect metadata for {state.coordinate}")
        save(workspace, state)
        print(f"[native] the native check passes at {state.checkpoint}")
        return OK
    if handoff.guard_tripped(state, SENDER):
        print("[native] the native check still fails after its fix")
        return STILL_FAILING
    handoff.write_fix_request(workspace, state, SENDER, failures)
    save(workspace, state)
    print(f"[native] {len(failures)} command(s) fail; a fix request follows")
    return AGENT_WORK
