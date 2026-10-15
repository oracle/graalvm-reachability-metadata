# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The native phase's programs: `native` and the optional `collect-metadata`.

`native` runs the native task once. Only a failure leads on: `collect-metadata`
runs the JVM tests once with the native-image agent, copies what it collects
into the test-local file, and runs the native task again; a failure after that
goes to the native fix agent (§FS-real-use-test-generation.2.4,
§FS-real-use-test-generation.2.5).
"""
from __future__ import annotations

import os
import shutil

import local_metadata
import prompts
import scenarios
from build_tools import TAIL_LINES, TaskResult, restore_paths, run_gradle
from exit_codes import INFRASTRUCTURE, NATIVE_FAILED, NO_SCENARIO_LEFT, OK
from run_state import RunState, load, runtime_path

# The single-file metadata the native-image agent writes into its output directory.
AGENT_OUTPUT: str = "reachability-metadata.json"


def _run_native(workspace: str, state: RunState, log_name: str) -> TaskResult:
    return run_gradle(state.worktree, "realUseNativeTest", state.coordinate, runtime_path(workspace, "logs", log_name))


def native(workspace: str) -> int:
    """Run the native task once, built as a consumer builds it.

    §FS-real-use-test-generation.2.4
    """
    state: RunState = load(workspace)
    if not scenarios.scenario_files(state.test_java_path):
        print("[native] no scenario remains")
        return NO_SCENARIO_LEFT
    if _run_native(workspace, state, "realUseNativeTest.log").ok:
        print("[native] every scenario passes")
        return OK
    print("[native] the native run fails; the metadata collection follows")
    return NATIVE_FAILED


def _hand_to_agent(workspace: str, state: RunState, tail: str) -> int:
    """Write the native fix agent's prompt and route to it (§FS-real-use-test-generation.2.5)."""
    with open(runtime_path(workspace, "prompts", "native-fix.md"), "w", encoding="utf-8") as handle:
        handle.write(prompts.native_fix(state, local_metadata.filter_prefixes(state.filter_path), tail))
    return NATIVE_FAILED


def collect_metadata(workspace: str) -> int:
    """Run the JVM tests with the agent once, copy what it collects test-local, and retry natively."""
    state: RunState = load(workspace)
    with open(runtime_path(workspace, "prompts", "native-fix.md"), "w", encoding="utf-8") as handle:
        # The prompt is a declared output, so it exists on every exit.
        handle.write("The collected metadata did not reach the native fix.\n")
    # Start from the checkpointed file: nothing a generate pass wrote there survives.
    restore_paths(state.worktree, state.base_commit, [state.metadata_repo_path])
    output: str = runtime_path(workspace, "agent-output", "x")
    shutil.rmtree(os.path.dirname(output), ignore_errors=True)
    os.makedirs(os.path.dirname(output))
    traced: TaskResult = run_gradle(
        state.worktree, "realUseJavaTest", state.coordinate,
        runtime_path(workspace, "logs", "realUseJavaTest-agent.log"),
        {"tck.realUse.agentOutput": os.path.dirname(output)},
    )
    if not traced.ok:
        print("[collect-metadata] the JVM run with the agent failed; copying what it collected")
    copied: int = local_metadata.copy_collected(
        os.path.join(os.path.dirname(output), AGENT_OUTPUT),
        state.metadata_path,
        local_metadata.filter_prefixes(state.filter_path),
        scenarios.test_packages(state.test_java_path),
    )
    if not copied:
        print("[collect-metadata] the agent collected nothing new; the native fix agent follows")
        with open(runtime_path(workspace, "logs", "realUseNativeTest.log"), encoding="utf-8") as handle:
            return _hand_to_agent(workspace, state, "\n".join(handle.read().splitlines()[-TAIL_LINES:]))
    retry: TaskResult = _run_native(workspace, state, "realUseNativeTest-collected.log")
    if retry.ok:
        print(f"[collect-metadata] {copied} collected entries made every scenario pass")
        return OK
    print(f"[collect-metadata] {copied} entries copied; the native run still fails")
    return _hand_to_agent(workspace, state, retry.tail)
