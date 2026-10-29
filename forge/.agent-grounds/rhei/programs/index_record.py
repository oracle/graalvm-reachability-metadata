# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Index-only outcomes and their gate (§FS-not-for-native-image-record.2)."""
from __future__ import annotations

import os
import sys

from utility_scripts.local_ci_verification import (
    LocalCIVerificationError,
    LocalCIVerificationResult,
    run_local_ci_verification,
)
from utility_scripts.metadata_index import write_not_for_native_image_marker
from utility_scripts.native_image_artifact import NativeImageEligibility, inspect_maven_artifact

from build_tools import TaskResult, checkpoint, coordinate_task, head, rebase_onto_upstream, run_gradle
from exit_codes import BAD_INPUT, NOT_FOR_NATIVE_IMAGE_RECORDED
from run_state import ROUTE_NEW, RunState, runtime_path


def gate(state: RunState) -> LocalCIVerificationResult:
    """The pre-publication gate on an index-only tree rebased onto `master`; a rejection raises
    `LocalCIVerificationError` (§FS-local-ci-equivalent-verification.2)."""
    return run_local_ci_verification(repo_path=state.worktree, coordinates=state.coordinate,
                                     base_commit=state.base_commit, max_fixup_attempts=0)


def record_not_for_native_image(workspace: str, state: RunState) -> int | None:
    """Record published non-JVM evidence before scaffolding (§FS-not-for-native-image-record.2)."""
    if state.route != ROUTE_NEW or os.path.isfile(state.path(state.index_path)):
        return None
    verdict: NativeImageEligibility = inspect_maven_artifact(state.coordinate, strict=True)
    if not verdict.not_for_native_image:
        return None
    assert verdict.reason
    write_not_for_native_image_marker(state.worktree, state.group, state.artifact,
                                      verdict.reason, verdict.replacement)
    validated: TaskResult = run_gradle(
        state.worktree, coordinate_task("validateIndexFiles", state.coordinate),
        runtime_path(workspace, "logs", "not-for-native-image-validateIndexFiles.log"))
    if not validated.ok:
        print(f"ERROR: validateIndexFiles rejected the not-for-native-image record:\n{validated.tail}", file=sys.stderr)
        return BAD_INPUT
    checkpoint(state.worktree, state.coordinate_paths, f"Record {state.group}:{state.artifact} as not-for-native-image")
    state.base_commit = rebase_onto_upstream(state.worktree)
    state.checkpoint = head(state.worktree)
    try:
        verified: LocalCIVerificationResult = gate(state)
    except LocalCIVerificationError as error:
        print(f"ERROR: the pre-publication gate rejected the not-for-native-image record: {error}", file=sys.stderr)
        return BAD_INPUT
    state.local_ci = verified.to_metrics()
    state.not_for_native_image = {"reason": verdict.reason, "replacement": verdict.replacement}
    return NOT_FOR_NATIVE_IMAGE_RECORDED
