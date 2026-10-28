# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The native check and the two programs that run it: `native-repair` and `native`.

`generateMetadata` runs the JVM tests under the native-image agent and merges
what the filter allows into the shipped metadata; `test` runs the JVM and
Native Image tests. `native-repair` runs only `test`, so a fix's metadata is
tested as the fix left it; `native` regenerates first. Neither runs
`checkMetadataFiles`, which `finalize` runs after the harness split and routing.
The programs judge only pass or fail and never edit metadata; a failure is one
fix request (§AR-forge-workflow-pipeline.5).
"""
from __future__ import annotations

import handoff
from build_tools import TaskResult, checkpoint, coordinate_task, gradle_display, run_gradle
from exit_codes import AGENT_WORK, OK, STILL_FAILING
from run_state import RunState, load, runtime_path, save

SENDER: str = "native"
REPAIR_SENDER: str = "native-repair"
GENERATE: str = "generateMetadata"
TEST: str = "test"
TASKS: tuple[str, ...] = (GENERATE, TEST)


def commands(state: RunState, tasks: tuple[str, ...] = TASKS) -> tuple[str, ...]:
    """The native-check commands as an agent would type them."""
    return tuple(gradle_display(coordinate_task(task, state.coordinate)) for task in tasks)


def run_task(workspace: str, state: RunState, prefix: str, task: str) -> TaskResult:
    """Run one coordinate task, logging under the program's prefix."""
    return run_gradle(state.worktree, coordinate_task(task, state.coordinate),
                      runtime_path(workspace, "logs", f"{prefix}-{task}.log"))


def failures_of(result: TaskResult, blocked: tuple[str, ...] = ()) -> list[handoff.Failure]:
    """The failure of one command, or none when it passed."""
    return [] if result.ok else [handoff.Failure(result.command, result.tail, blocked=blocked)]


def run_native_check(workspace: str, state: RunState, prefix: str) -> list[handoff.Failure]:
    """Regenerate, then run `test`; return the failing command."""
    generated: TaskResult = run_task(workspace, state, prefix, GENERATE)
    if not generated.ok:
        # `test` would judge stale metadata; no later request covers it.
        # §AR-forge-workflow-pipeline.5
        return failures_of(generated, blocked=commands(state, (TEST,)))
    return failures_of(run_task(workspace, state, prefix, TEST))


def conclude(workspace: str, state: RunState, sender: str, failures: list[handoff.Failure],
             paths: list[str], message: str) -> int:
    """Checkpoint a pass, or send one fix request; a failure right after it exits `12`.

    §AR-forge-workflow-pipeline.6
    """
    if not failures:
        state.pending_request = None
        state.checkpoint = checkpoint(state.worktree, paths, message)
        save(workspace, state)
        print(f"[{sender}] passes at {state.checkpoint}")
        return OK
    if handoff.guard_tripped(state, sender):
        print(f"[{sender}] `{failures[0].command}` still fails after its fix")
        return STILL_FAILING
    handoff.write_fix_request(workspace, state, sender, failures)
    save(workspace, state)
    print(f"[{sender}] `{failures[0].command}` fails; a fix request follows")
    return AGENT_WORK


def native_repair(workspace: str) -> int:
    """Right after `jvm`: only `test`, over the metadata `jvm` generated.

    §AR-forge-workflow-pipeline.4
    """
    state: RunState = load(workspace)
    handoff.write_placeholder(workspace, REPAIR_SENDER)
    failures: list[handoff.Failure] = failures_of(run_task(workspace, state, REPAIR_SENDER, TEST))
    return conclude(workspace, state, REPAIR_SENDER, failures, state.coordinate_paths,
                    f"Repair {state.coordinate} on Native Image")


def native(workspace: str) -> int:
    """The native check over the tests exploration added, up to `native_fixes` requests.

    §AR-forge-workflow-pipeline.4, §AR-forge-workflow-pipeline.6
    """
    state: RunState = load(workspace)
    handoff.write_placeholder(workspace, SENDER)
    failures: list[handoff.Failure] = run_native_check(workspace, state, SENDER)
    if not failures:
        return conclude(workspace, state, SENDER, failures, state.contribution_paths(),
                        f"Collect metadata for {state.coordinate}")
    if state.native_requests >= state.budgets.native_fixes:
        print(f"[native] `{failures[0].command}` still fails after the last of {state.budgets.native_fixes} fixes")
        return STILL_FAILING
    handoff.write_fix_request(workspace, state, SENDER, failures)
    state.native_requests += 1
    save(workspace, state)
    print(f"[native] `{failures[0].command}` fails; fix request {state.native_requests} of "
          f"{state.budgets.native_fixes} follows")
    return AGENT_WORK
