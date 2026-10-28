# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The `setup-check` program: the setup tail, once, right after `setup`.

It keeps only what `setup` may change, turns a `fails-*` verdict into the run's
skip record before anything else (§FS-unsupportable-version-diagnosis.3),
verifies the four URLs, downloads the main sources, writes the filter from the
library JAR, and checkpoints (§AR-forge-workflow-pipeline.4.1). A new library
has no version to repair and goes to `analyze`; every other route goes to
`repair`.
"""
from __future__ import annotations

import os
import re
import sys
import time
from typing import Any

from utility_scripts.metadata_index import find_index_entry_for_version
from utility_scripts.source_context import PreparedSourceContext, prepare_source_contexts

import skip_record
from build_tools import TaskResult, changed_paths, checkpoint, coordinate_task, restore_paths, run_gradle
from exit_codes import BAD_INPUT, INFRASTRUCTURE, NOTHING_TO_DO, OK
from run_state import ROUTE_NEW, ROUTE_REPAIR, RunState, load, runtime_path, save

URL_FIELDS: tuple[str, ...] = ("source-code-url", "test-code-url", "documentation-url", "repository-url")
DECLARATION_FILES: tuple[str, ...] = ("build.gradle", "required-docker-images.txt")
FILTER: str = "user-code-filter.json"
UNRESOLVED: re.Pattern[str] = re.compile(r"Could not resolve|Could not find [\w.-]+:[\w.-]+")
# A source download the network denies is retried after a pause (§AR-forge-workflow-pipeline.4.1).
SOURCE_ATTEMPTS: int = 3
SOURCE_RETRY_SECONDS: int = 300
# The pause's override, so a test with unreachable URLs does not wait.
SOURCE_RETRY_VARIABLE: str = "FORGE_SOURCE_RETRY_SECONDS"


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


def retry_seconds() -> int:
    """The pause before a denied download is retried."""
    return int(os.environ.get(SOURCE_RETRY_VARIABLE, SOURCE_RETRY_SECONDS))


def download_sources(state: RunState) -> list[str]:
    """Download the main sources into the run's source context; return the types still missing.

    A download the index names a URL for but the network denies is retried
    after a pause; a source with no URL is final. The run goes on without the
    sources it could not get: they only sharpen the cover prompts.
    """
    for attempt in range(1, SOURCE_ATTEMPTS + 1):
        prepared: PreparedSourceContext = prepare_source_contexts(
            os.path.join(state.worktree, "forge"), state.worktree, state.coordinate, ["main"])
        denied: list[str] = [artifact.source_type for artifact in prepared.artifacts
                             if artifact.url is not None and not artifact.available]
        if not denied:
            return []
        if attempt == SOURCE_ATTEMPTS:
            print(f"[setup-check] the {', '.join(denied)} sources could not be downloaded; continuing without them")
            return denied
        print(f"[setup-check] the {', '.join(denied)} sources could not be downloaded (attempt {attempt} of "
              f"{SOURCE_ATTEMPTS}); retrying in {retry_seconds()}s")
        time.sleep(retry_seconds())
    return []


def missing_urls(state: RunState) -> list[str]:
    entry: dict[str, Any] = find_index_entry_for_version(state.worktree, state.group, state.artifact, state.version) or {}
    return [field for field in URL_FIELDS if not str(entry.get(field) or "").strip()]


def apply_setup(workspace: str, state: RunState) -> int:
    """The setup tail (§AR-forge-workflow-pipeline.4.1)."""
    reverted: list[str] = revert_out_of_scope(state)
    if reverted:
        print(f"[setup-check] reverted setup edits outside its scope: {', '.join(reverted)}")
    if state.route == ROUTE_REPAIR:
        skipped: int | None = skip_record.apply(workspace, state)
        if skipped is not None:
            return skipped
    missing: list[str] = missing_urls(state)
    if missing:
        print(f"ERROR: {state.coordinate} has no {', '.join(missing)} in {state.index_path}.", file=sys.stderr)
        return BAD_INPUT
    download_sources(state)
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
    return OK


def setup_check(workspace: str) -> int:
    state: RunState = load(workspace)
    code: int = apply_setup(workspace, state)
    save(workspace, state)
    if code != OK:
        return code
    if state.route == ROUTE_NEW:
        print("[setup-check] a new library has no version to repair")
        return NOTHING_TO_DO
    return OK
