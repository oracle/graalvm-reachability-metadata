# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The `jvm` program: make a version that already has tests work on the JVM.

It runs `javaTest`, then `generateMetadata`, and the first failure is one fix
request for that command alone; `native-repair` follows with its own
(§AR-forge-workflow-pipeline.4, §AR-forge-workflow-pipeline.5). `setup-check`
has already run the setup tail, and a new library never comes here
(§AR-forge-workflow-pipeline.4.1).
"""
from __future__ import annotations

import sys

from utility_scripts.metadata_index import find_index_entry_for_version

import handoff
import native_check
from build_tools import TaskResult
from exit_codes import BAD_INPUT
from run_state import RunState, load
from setup_check import UNRESOLVED

SENDER: str = "jvm"
JAVA_TEST: str = "javaTest"


def jvm(workspace: str) -> int:
    state: RunState = load(workspace)
    handoff.write_placeholder(workspace, SENDER)
    if find_index_entry_for_version(state.worktree, state.group, state.artifact, state.version) is None:
        print(f"ERROR: {state.index_path} has no entry for {state.version}.", file=sys.stderr)
        return BAD_INPUT
    result: TaskResult = native_check.run_task(workspace, state, SENDER, JAVA_TEST)
    if not result.ok and UNRESOLVED.search(result.output):
        print(f"ERROR: a declared dependency does not resolve:\n{result.tail}", file=sys.stderr)
        return BAD_INPUT
    if result.ok:
        result = native_check.run_task(workspace, state, SENDER, native_check.GENERATE)
    return native_check.conclude(workspace, state, SENDER, native_check.failures_of(result),
                                 state.coordinate_paths, f"Repair {state.coordinate}")
