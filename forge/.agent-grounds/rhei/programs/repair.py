# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The `repair` program: the setup tail once, then "make this version work".

The setup tail keeps only what `setup` may change, turns a `fails-*` verdict
into the run's skip record before anything else (§FS-unsupportable-version-diagnosis.3),
verifies the four URLs,
downloads the main sources, writes the filter from the library JAR, and
checkpoints (§AR-forge-workflow-pipeline.4.1). A new library then has nothing
to repair; every other route runs `javaTest` and the native check, and any
failure is one fix request (§AR-forge-workflow-pipeline.4).
"""
from __future__ import annotations

import os
import re
import sys
from typing import Any

from utility_scripts.metadata_index import find_index_entry_for_version
from utility_scripts.source_context import prepare_source_contexts

import handoff
import native_check
import skip_record
from build_tools import TaskResult, changed_paths, checkpoint, coordinate_task, restore_paths, run_gradle
from exit_codes import AGENT_WORK, BAD_INPUT, INFRASTRUCTURE, NOTHING_TO_DO, OK, SKIP_RECORDED, STILL_FAILING
from run_state import ROUTE_NEW, ROUTE_REPAIR, RunState, load, runtime_path, save

SENDER: str = "repair"
URL_FIELDS: tuple[str, ...] = ("source-code-url", "test-code-url", "documentation-url", "repository-url")
DECLARATION_FILES: tuple[str, ...] = ("build.gradle", "required-docker-images.txt")
FILTER: str = "user-code-filter.json"
UNRESOLVED: re.Pattern[str] = re.compile(r"Could not resolve|Could not find [\w.-]+:[\w.-]+")


def keep_url_edits(state: RunState, keep_skips: bool) -> None:
    """Restore `index.json` to the checkpoint except the four URL fields of each entry.

    With `keep_skips`, the `skipped-versions` records `setup` added survive too
    (§FS-unsupportable-version-diagnosis.2).
    """
    edited: list[dict[str, Any]] = skip_record.read_index(state)
    base: list[dict[str, Any]] = skip_record.read_index(state, state.checkpoint)
    by_version: dict[str, dict[str, Any]] = {str(entry.get("metadata-version")): entry for entry in edited}
    kept: list[dict[str, Any]] = []
    for entry in base:
        source: dict[str, Any] = by_version.get(str(entry.get("metadata-version")), {})
        for field in URL_FIELDS:
            if source.get(field):
                entry[field] = source[field]
        if keep_skips:
            gained: list[dict[str, str]] = skip_record.added([source], base)
            if gained:
                entry = skip_record.with_skips(entry, list(entry.get(skip_record.FIELD) or []) + gained)
        kept.append(entry)
    skip_record.write_index(state, kept)


def revert_out_of_scope(state: RunState) -> list[str]:
    """Revert every `setup` edit outside its scope; return what was reverted.

    The scope is the declarations, the URL fields, and on a `fails-*` label the
    verdict's `skipped-versions` records (§AR-forge-workflow-pipeline.4.1).
    """
    allowed: set[str] = {os.path.join(state.test_dir, name) for name in DECLARATION_FILES}
    reverted: list[str] = []
    for path in changed_paths(state.worktree, state.checkpoint):
        if path == state.index_path:
            keep_url_edits(state, keep_skips=state.route == ROUTE_REPAIR)
        elif path not in allowed:
            reverted.append(path)
    restore_paths(state.worktree, state.checkpoint, reverted)
    return reverted


def missing_urls(state: RunState) -> list[str]:
    entry: dict[str, Any] = find_index_entry_for_version(state.worktree, state.group, state.artifact, state.version) or {}
    return [field for field in URL_FIELDS if not str(entry.get(field) or "").strip()]


def apply_setup(workspace: str, state: RunState) -> int:
    """The setup tail (§AR-forge-workflow-pipeline.4.1)."""
    reverted: list[str] = revert_out_of_scope(state)
    if reverted:
        print(f"[repair] reverted setup edits outside its scope: {', '.join(reverted)}")
    if state.route == ROUTE_REPAIR:
        skipped: int | None = skip_record.apply(workspace, state)
        if skipped is not None:
            return skipped
    missing: list[str] = missing_urls(state)
    if missing:
        print(f"ERROR: {state.coordinate} has no {', '.join(missing)} in {state.index_path}.", file=sys.stderr)
        return BAD_INPUT
    prepare_source_contexts(os.path.join(state.worktree, "forge"), state.worktree, state.coordinate, ["main"])
    if not os.path.isfile(state.path(os.path.join(state.test_dir, FILTER))):
        generated: TaskResult = run_gradle(
            state.worktree,
            coordinate_task("generateMetadata", state.coordinate, "--agentAllowedPackages=fromJar"),
            runtime_path(workspace, "logs", "setup-generateMetadata.log"),
        )
        if not generated.ok:
            unresolved: bool = UNRESOLVED.search(generated.output) is not None
            print(f"ERROR: the filter could not be written from the library JAR:\n{generated.tail}", file=sys.stderr)
            return BAD_INPUT if unresolved else INFRASTRUCTURE
    state.checkpoint = checkpoint(state.worktree, state.coordinate_paths, f"Set up {state.coordinate}")
    state.setup_applied = True
    return OK


def repair(workspace: str) -> int:
    state: RunState = load(workspace)
    handoff.write_placeholder(workspace, SENDER)
    if not state.setup_applied:
        code: int = apply_setup(workspace, state)
        save(workspace, state)
        if code != OK:
            return code
    if state.skip_recorded:
        return SKIP_RECORDED
    if state.route == ROUTE_NEW:
        print("[repair] a new library has no version to repair")
        return NOTHING_TO_DO
    if find_index_entry_for_version(state.worktree, state.group, state.artifact, state.version) is None:
        print(f"ERROR: {state.index_path} has no entry for {state.version}.", file=sys.stderr)
        return BAD_INPUT
    failures: list[handoff.Failure] = []
    tested: TaskResult = run_gradle(
        state.worktree, coordinate_task("javaTest", state.coordinate), runtime_path(workspace, "logs", "repair-javaTest.log"),
    )
    if not tested.ok:
        if UNRESOLVED.search(tested.output):
            print(f"ERROR: a declared dependency does not resolve:\n{tested.tail}", file=sys.stderr)
            return BAD_INPUT
        failures.append(handoff.Failure(tested.command, tested.tail))
    else:
        failures = native_check.run_native_check(workspace, state, SENDER)
    if not failures:
        state.pending_request = None
        state.checkpoint = checkpoint(state.worktree, state.coordinate_paths, f"Repair {state.coordinate}")
        save(workspace, state)
        print(f"[repair] {state.coordinate} works at {state.checkpoint}")
        return OK
    if handoff.guard_tripped(state, SENDER):
        print("[repair] the version still fails after its fix")
        return STILL_FAILING
    handoff.write_fix_request(workspace, state, SENDER, failures)
    save(workspace, state)
    print(f"[repair] {state.coordinate} fails; a fix request follows")
    return AGENT_WORK
