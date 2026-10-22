# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The unsupportable-version verdict: the bar `run.md` names and the skip record it becomes.

On a `fails-*` label `setup` may record its verdict as `skipped-versions`
entries in `index.json` (§FS-unsupportable-version-diagnosis.2). Before anything
else the setup tail turns an entry for the target version into an index-only
tree on the pinned base, validates it, and exits `SKIP_RECORDED` to `publish`;
a verdict that cannot be applied is reverted and the repair goes on
(§FS-unsupportable-version-diagnosis.3, §AR-forge-workflow-pipeline.4.1).
"""
from __future__ import annotations

import json
import os
import shutil
from typing import Any

from utility_scripts.local_ci_verification import LocalCIVerificationError, LocalCIVerificationResult, run_local_ci_verification

from build_tools import TaskResult, changed_paths, checkpoint, coordinate_task, git, reset_to, restore_paths, run_gradle
from exit_codes import SKIP_RECORDED
from run_state import RunState, runtime_path

FIELD: str = "skipped-versions"

# The five-step bar of §FS-unsupportable-version-diagnosis.1, ported from the
# retired library preparation preflight; `run.md` carries it on `fails-*` labels only.
BAR: str = """## Unsupportable-version verdict

Before the URLs, decide whether `{version}` can be supported under GraalVM
Native Image at all. It is unsupportable only when covering its dynamic-access
calls requires behavior Native Image does not support: runtime bytecode
generation or class definition, runtime lambda definition, Java agent
self-attach, class redefinition or instrumentation, native-image substitution
paths, or URL/plugin/OSGi class loader paths that introduce classes not in the
image.

Run these steps in order. An unsupportable verdict requires ALL of them; stop
at the first that fails and treat the version as repairable:

1. FAILURE: state the failing operation from the issue's failure evidence alone.
2. TRACE: follow the failing symbol into the library's own source and quote the
   code performing the operation.
3. CLASSIFY: show the operation is in the list above. Anything reachability
   metadata can register is repairable.
4. SOLE PATH: prove every public API route the metadata would justify passes
   through this operation, with no configuration, property, or fallback branch
   around it. One viable fallback means repairable.
5. RANGE: list newer released versions whose sources you verified share the
   same mechanism. Include only versions you actually verified; when you cannot
   verify newer versions, record only `{version}`.

Never base the verdict on a red test, a library's own error type, or its error
message: only on the mechanism you traced in the library's source. Any
uncertainty at any step means repairable: attempting the fix is the safe
default, and a wrong skip freezes versions that were never attempted.

On an unsupportable verdict, add to the coordinate's entry in the index file a
`"skipped-versions"` list of `{{"version": "<version>", "reason": "<reason>"}}`
objects: `{version}` and every version step 5 verified. Write each reason for a
reader who has not seen this analysis, naming the mechanism and why the image
cannot perform it; reasons are published verbatim. Then make no dependency or
image edit and change nothing else. On a repairable verdict, add no entry.
"""


def bar(version: str) -> str:
    return BAR.format(version=version)


def _records(entry: dict[str, Any]) -> list[dict[str, Any]]:
    return [record for record in entry.get(FIELD) or [] if isinstance(record, dict) and record.get("version")]


def recorded_versions(entries: list[dict[str, Any]]) -> set[str]:
    return {str(record["version"]) for entry in entries for record in _records(entry)}


def added(edited: list[dict[str, Any]], before: list[dict[str, Any]]) -> list[dict[str, str]]:
    """The `skipped-versions` records `edited` gained over `before`, in order, one per version."""
    known: set[str] = recorded_versions(before)
    records: list[dict[str, str]] = []
    for entry in edited:
        for record in _records(entry):
            version: str = str(record["version"])
            if version not in known:
                known.add(version)
                records.append({"version": version, "reason": str(record.get("reason") or "").strip()})
    return records


def with_skips(entry: dict[str, Any], records: list[dict[str, Any]]) -> dict[str, Any]:
    """`entry` with `records` as its skip list, placed after `tested-versions` as `validateIndexFiles` renders it."""
    rebuilt: dict[str, Any] = {}
    for key, value in entry.items():
        if key == FIELD:
            continue
        rebuilt[key] = value
        if key == "tested-versions" and records:
            rebuilt[FIELD] = records
    if records and FIELD not in rebuilt:
        rebuilt[FIELD] = records
    return rebuilt


def read_index(state: RunState, commit: str | None = None) -> list[dict[str, Any]]:
    """The coordinate's index, from the worktree or from `commit`."""
    if commit is not None:
        return json.loads(git(state.worktree, "show", f"{commit}:{state.index_path}"))
    with open(state.path(state.index_path), encoding="utf-8") as handle:
        return json.load(handle)


def write_index(state: RunState, entries: list[dict[str, Any]]) -> None:
    """Write the index in the repository's own style, `"key" : value`."""
    rendered: str = json.dumps(entries, indent=2, ensure_ascii=False).replace('": ', '" : ')
    with open(state.path(state.index_path), "w", encoding="utf-8") as handle:
        handle.write(rendered + "\n")


def strip(state: RunState) -> None:
    """Return every entry's skip list to the checkpoint's, dropping the verdict."""
    before: dict[str, list[dict[str, Any]]] = {
        str(entry.get("metadata-version")): _records(entry) for entry in read_index(state, state.checkpoint)}
    write_index(state, [with_skips(entry, before.get(str(entry.get("metadata-version")), []))
                        for entry in read_index(state)])


def onto_base(base: list[dict[str, Any]], records: list[dict[str, str]]) -> list[dict[str, Any]]:
    """`base` with `records` appended to the `latest` entry, the one the compatibility automation reads."""
    target: dict[str, Any] = next((entry for entry in base if entry.get("latest") is True), base[0])
    return [with_skips(entry, _records(entry) + records) if entry is target else entry for entry in base]


def _inside(path: str, roots: list[str]) -> bool:
    return any(path == root or path.startswith(root.rstrip("/") + "/") for root in roots)


def _gate(state: RunState) -> LocalCIVerificationResult | None:
    """The pre-publication gate `finalize` would run, on the index-only tree."""
    try:
        return run_local_ci_verification(repo_path=state.worktree, coordinates=state.coordinate,
                                         base_commit=state.base_commit, max_fixup_attempts=0)
    except LocalCIVerificationError as error:
        print(f"[repair] the pre-publication gate rejected the skip record: {error}")
        return None


def apply(workspace: str, state: RunState) -> int | None:
    """Turn a verdict for the target version into the run's skip record; `None` falls through to repair."""
    records: list[dict[str, str]] = added(read_index(state), read_index(state, state.checkpoint))
    if not any(record["version"] == state.version for record in records):
        if records:
            print(f"[repair] dropped skip entries that do not name {state.version}")
            strip(state)
        return None
    base: list[dict[str, Any]] = read_index(state, state.base_commit)
    if state.version in recorded_versions(base) or any(not record["reason"] for record in records):
        print(f"[repair] {state.version} is already recorded as skipped, or a reason is empty; repairing instead")
        strip(state)
        return None
    prepared: str = state.checkpoint
    setup_commit: str = checkpoint(state.worktree, state.coordinate_paths, f"Set up {state.coordinate}")
    restore_paths(state.worktree, state.base_commit,
                  [path for path in changed_paths(state.worktree, state.base_commit)
                   if _inside(path, state.coordinate_paths)])
    for directory in (state.test_dir, state.metadata_dir):
        # Empty directories and ignored build output the restore leaves behind.
        if os.path.isdir(state.path(directory)) and not git(state.worktree, "ls-tree", state.base_commit, directory).strip():
            shutil.rmtree(state.path(directory))
    write_index(state, onto_base(base, [record for record in records if record["version"] not in recorded_versions(base)]))
    validated: TaskResult = run_gradle(state.worktree, coordinate_task("validateIndexFiles", state.coordinate),
                                       runtime_path(workspace, "logs", "skip-validateIndexFiles.log"))
    gate: LocalCIVerificationResult | None = None
    if validated.ok:
        recorded: str = checkpoint(state.worktree, state.coordinate_paths,
                                   f"Record skipped versions for {state.group}:{state.artifact}")
        gate = _gate(state)
        if gate is not None:
            state.checkpoint = recorded
            state.local_ci = gate.to_metrics()
            state.skip_recorded = True
            state.setup_applied = True
            print(f"[repair] recorded {', '.join(r['version'] for r in records)} as skipped at {recorded}")
            return SKIP_RECORDED
    else:
        print(f"[repair] validateIndexFiles rejected the skip record:\n{validated.tail}")
    reset_to(state.worktree, setup_commit, state.coordinate_paths)
    git(state.worktree, "reset", "--quiet", "--soft", prepared)
    strip(state)
    return None
