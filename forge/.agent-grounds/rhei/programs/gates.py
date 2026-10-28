# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The reviewer gates `finalize` runs after the stats: coverage, requested metadata, diff scope.

Each gate is a program over the reported evidence, never a judgment, and a
breach becomes a fix request whose brief names the gate
(§FS-local-ci-equivalent-verification.1.1). Run as a script with the workspace
path, it reruns every gate, which is the command a fix request names.
"""
from __future__ import annotations

import json
import os
import re
import sys
from dataclasses import dataclass
from typing import Any

if __name__ == "__main__":
    sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "..")))

from utility_scripts.contribution_paths import stray_paths  # noqa: E402
from utility_scripts.library_stats import load_library_stats_entry  # noqa: E402

import handoff  # noqa: E402
from build_tools import changed_paths  # noqa: E402
from run_state import ROUTE_NEW, ROUTE_UPDATE, RunState, load  # noqa: E402

TYPE_SECTIONS: tuple[str, ...] = ("reflection", "jni", "serialization")
FENCED: re.Pattern[str] = re.compile(r"```[A-Za-z]*\n(.*?)```", re.DOTALL)
OVERALL: str = "overall"

COVERAGE_BRIEF: str = ("dynamic-access coverage gate: Cover more call sites through the public API until the "
                       "gate passes; rerun `generateLibraryStats` before the gate.")
REQUESTED_BRIEF: str = ("reporter-requested metadata gate: Add the requested entries to the shipped metadata, "
                        "each with a test that reaches it through the library's public API.")
SCOPE_BRIEF: str = ("diff-scope gate: Revert the paths outside the contribution's file set, and move legacy "
                    "`*-config.json` content into `reachability-metadata.json`.")


@dataclass(frozen=True)
class Scope:
    """One overall or breakdown dynamic-access scope; `N/A` is `0/0`."""

    covered: int
    total: int

    @property
    def ratio(self) -> float:
        return self.covered / self.total if self.total else 0.0


def scopes(dynamic_access: Any) -> dict[str, Scope]:
    """The overall scope and every breakdown of one stats `dynamicAccess` value."""
    if not isinstance(dynamic_access, dict):
        return {OVERALL: Scope(0, 0)}
    found: dict[str, Scope] = {OVERALL: Scope(int(dynamic_access.get("coveredCalls") or 0),
                                              int(dynamic_access.get("totalCalls") or 0))}
    for name, value in (dynamic_access.get("breakdown") or {}).items():
        found[name] = scopes(value)[OVERALL]
    return found


def repair_scope_passes(previous: Scope, new: Scope) -> bool:
    """The ordered `fixes-*` comparison of §root/FS-contribution-contract.3, for one scope."""
    if new.total == 0:
        return True
    if new.covered == 0:
        return False
    if new.covered >= previous.covered or new.covered * previous.total >= previous.covered * new.total:
        return True
    if previous.total < 10 and new.total < 10:
        return previous.covered - new.covered <= 2
    return previous.ratio - new.ratio <= 0.20


def dynamic_access(state: RunState, version: str) -> Any:
    entry: dict[str, Any] | None = load_library_stats_entry(state.worktree, state.group, state.artifact, version)
    return (entry or {}).get("dynamicAccess")


def coverage_breach(state: RunState) -> str | None:
    """Why the regenerated stats miss the label's coverage gate, or `None`."""
    new: dict[str, Scope] = scopes(dynamic_access(state, state.version))
    overall: Scope = new[OVERALL]
    if state.chunk.active:
        # Only zero coverage fails a chunk (§FS-forge-chunked-dynamic-access).
        return None if overall.covered > 0 else "the chunk covers no dynamic-access call"
    if state.route == ROUTE_NEW:
        if overall.total and overall.covered * 5 <= overall.total:
            return f"{overall.covered}/{overall.total} dynamic-access calls covered; a new library needs above 20%"
        return None
    if state.previous_coordinate is None:
        return None
    previous_version: str = state.previous_coordinate.split(":")[2]
    previous: dict[str, Scope] = scopes(dynamic_access(state, previous_version))
    if state.route == ROUTE_UPDATE:
        before: Scope = previous[OVERALL]
        if overall.total and before.total and overall.covered * before.total < before.covered * overall.total:
            return (f"coverage {overall.covered}/{overall.total} dropped below {previous_version}'s "
                    f"{before.covered}/{before.total}")
        return None
    failing: list[str] = []
    for name in sorted(set(previous) | set(new)):
        before, after = previous.get(name, Scope(0, 0)), new.get(name, Scope(0, 0))
        if not repair_scope_passes(before, after):
            failing.append(f"{name} {before.covered}/{before.total} -> {after.covered}/{after.total}")
    if not failing:
        return None
    return f"against {previous_version}, these scopes fail the repair comparison: {', '.join(failing)}"


def _collect(value: Any, types: set[str], globs: set[str]) -> None:
    """The types and resource globs of one metadata document, modern or legacy."""
    if isinstance(value, list):
        # A legacy `reflect-config.json` names each type in `name`.
        for entry in value:
            name: Any = entry.get("type", entry.get("name")) if isinstance(entry, dict) else None
            if isinstance(name, str):
                types.add(name)
        return
    if not isinstance(value, dict):
        return
    for section in TYPE_SECTIONS:
        for entry in value.get(section) or []:
            if isinstance(entry, dict) and isinstance(entry.get("type"), str):
                types.add(entry["type"])
    resources: Any = value.get("resources")
    for entry in resources if isinstance(resources, list) else []:
        if isinstance(entry, dict) and isinstance(entry.get("glob"), str):
            globs.add(entry["glob"])


def requested_entries(body: str) -> dict[str, list[str]]:
    """The explicit metadata JSON in the reporter's issue body: its types and resource globs."""
    types: set[str] = set()
    globs: set[str] = set()
    for block in FENCED.findall(body):
        try:
            _collect(json.loads(block), types, globs)
        except json.JSONDecodeError:
            continue
    return {"types": sorted(types), "globs": sorted(globs)} if types or globs else {}


def requested_breach(state: RunState) -> str | None:
    """Which requested entries the shipped metadata lacks, or `None`."""
    if not state.requested_metadata:
        return None
    types: set[str] = set()
    globs: set[str] = set()
    path: str = state.path(os.path.join(state.metadata_dir, "reachability-metadata.json"))
    if os.path.isfile(path):
        with open(path, encoding="utf-8") as handle:
            _collect(json.load(handle), types, globs)
    missing: list[str] = [f"type {name}" for name in state.requested_metadata.get("types", []) if name not in types]
    missing += [f"resource {glob}" for glob in state.requested_metadata.get("globs", []) if glob not in globs]
    return f"the shipped metadata lacks {', '.join(missing)}" if missing else None


def scope_breach(state: RunState) -> str | None:
    """Which changed paths, anywhere in the worktree, leave the closed file set, or `None`.

    §root/FS-contribution-contract.2
    """
    stray: list[str] = stray_paths(state.worktree, state.coordinate, state.base_commit,
                                   changed_paths(state.worktree, state.base_commit))
    return f"outside the closed file set: {', '.join(stray)}" if stray else None


def gate_command(workspace: str) -> str:
    return f"python3 {os.path.abspath(__file__)} {os.path.abspath(workspace)}"


def failures(workspace: str, state: RunState) -> list[handoff.Failure]:
    """One failure per breached gate, each naming its own brief."""
    command: str = gate_command(workspace)
    found: list[handoff.Failure] = []
    for check, brief in ((scope_breach, SCOPE_BRIEF), (requested_breach, REQUESTED_BRIEF),
                         (coverage_breach, COVERAGE_BRIEF)):
        breach: str | None = check(state)
        if breach is not None:
            found.append(handoff.Failure(command, breach, brief))
    return found


def main(argv: list[str]) -> int:
    """Rerun every gate over the workspace's run, printing each breach."""
    workspace: str = argv[0]
    breached: list[handoff.Failure] = failures(workspace, load(workspace))
    for failure in breached:
        print(f"FAILED {failure.brief.split(':')[0] if failure.brief else 'gate'}: {failure.output}")
    if not breached:
        print("every reviewer gate passes")
    return 1 if breached else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
