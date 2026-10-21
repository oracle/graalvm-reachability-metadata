# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The `apply-preflight` program: keep the declared libraries, write the filter, fix both.

The preflight agent may change only the build file and the Docker image list;
every other edit is reverted. The filter is then written from the JARs of every
declared library and checkpointed with them, so no scenario pass can change
what the project depends on (§FS-real-use-test-generation.2.2).
"""
from __future__ import annotations

import os
import sys

from build_tools import changed_paths, checkpoint, restore_paths, run_gradle
from exit_codes import INFRASTRUCTURE, OK
from run_state import USER_CODE_FILTER, RunState, load, runtime_path, save

DECLARATION_FILES: tuple[str, ...] = ("build.gradle", "required-docker-images.txt")


def apply_preflight(workspace: str) -> int:
    state: RunState = load(workspace)
    allowed: set[str] = {os.path.join(state.project_dir, name) for name in DECLARATION_FILES}
    out_of_scope: list[str] = [
        path for path in changed_paths(state.worktree, state.base_commit) if path not in allowed
    ]
    if out_of_scope:
        restore_paths(state.worktree, state.base_commit, out_of_scope)
        print(f"[apply-preflight] reverted edits outside {', '.join(DECLARATION_FILES)}: {', '.join(out_of_scope)}")
    result = run_gradle(
        state.worktree, "generateRealUseFilter", state.coordinate,
        runtime_path(workspace, "logs", "generateRealUseFilter.log"),
    )
    if not result.ok or not os.path.isfile(state.filter_path):
        print(f"ERROR: the declared libraries do not resolve into a filter:\n{result.tail}", file=sys.stderr)
        return INFRASTRUCTURE
    state.base_commit = checkpoint(
        state.worktree, [state.project_dir], f"Declare real-use libraries for {state.artifact}",
    )
    save(workspace, state)
    print(f"[apply-preflight] libraries and {USER_CODE_FILTER} fixed at {state.base_commit}")
    return OK
