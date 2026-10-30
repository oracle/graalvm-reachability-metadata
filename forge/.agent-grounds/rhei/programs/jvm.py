# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The `jvm` program: make a version that already has tests work on the JVM.

It runs `javaTest`, then `codeCoverageTest` when the version has the extension
suite, then `generateMetadata`, and the first failure is one fix request for
that command alone; `native-repair` follows with its own
(§AR-forge-workflow-pipeline.4, §AR-forge-workflow-pipeline.5). `setup-check`
has already run the setup tail, and a new library never comes here
(§AR-forge-workflow-pipeline.4.1).
"""
from __future__ import annotations

import sys

from utility_scripts.metadata_index import find_index_entry_for_version

import handoff
import native_check
from build_tools import JAVA_TEST, TaskResult, Unreachable, dependency_failure, jvm_suites
from exit_codes import INFRASTRUCTURE, BadInput
from run_state import RunState, load

SENDER: str = "jvm"


def jvm(workspace: str) -> int:
    """Run every JVM suite, then `generateMetadata`, and conclude on the first failure.

    A bad input raises `BadInput` naming it (§FS-forge-run-requirements.2).
    """
    state: RunState = load(workspace)
    handoff.write_placeholder(workspace, SENDER)
    if find_index_entry_for_version(state.worktree, state.group, state.artifact, state.version) is None:
        raise BadInput(f"{state.index_path} has no entry for {state.version}")
    result: TaskResult = native_check.run_task(workspace, state, SENDER, JAVA_TEST)
    failure: BadInput | Unreachable | None = dependency_failure(result, "a declared dependency does not resolve")
    if isinstance(failure, BadInput):
        raise failure
    if isinstance(failure, Unreachable):
        # No fix request: a later cycle retries (§AR-forge-workflow-pipeline.4.1).
        print(f"ERROR: a repository could not be reached:\n{failure.detail}", file=sys.stderr)
        return INFRASTRUCTURE
    # The extension suite before `generateMetadata`: `analyze`'s first report runs it
    # (§AR-forge-workflow-pipeline.4.5).
    later: list[str] = [task for task in jvm_suites(state.path(state.test_dir)) if task != JAVA_TEST]
    for task in (*later, native_check.GENERATE):
        if not result.ok:
            break
        result = native_check.run_task(workspace, state, SENDER, task)
    return native_check.conclude(workspace, state, SENDER, native_check.failures_of(result),
                                 state.coordinate_paths, f"Repair {state.coordinate}")
