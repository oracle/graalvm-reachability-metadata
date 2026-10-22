# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The run note every agent state reads first.

The template renders before the issue is read, so the coordinate, the route,
and every absolute path reach the agents through this file
(§AR-forge-workflow-prompts.2).
"""
from __future__ import annotations

import os
import re

from utility_scripts.metadata_index import find_index_entry_for_version

import skip_record
from run_state import ROUTE_NEW, ROUTE_REPAIR, RunState, runtime_path, write_text

TARGET: re.Pattern[str] = re.compile(r"^(pi|claude-code)(?:\[([A-Za-z0-9_-]+)\])?:(?:(.*)/)?([^/]+)$")


def source_context_dir(state: RunState) -> str:
    """Where the setup tail downloads the main sources (§AR-forge-workflow-pipeline.4.1)."""
    return os.path.join(state.worktree, "forge", "local_repositories", "source_context",
                        state.group, state.artifact, state.version)


def url_discovery_command(state: RunState) -> str:
    """`populateArtifactURLs` with the setup agent as the command it delegates lookups to."""
    matched: re.Match[str] | None = TARGET.match(state.setup_agent)
    if matched is None:
        raise ValueError(f"setup agent {state.setup_agent} is not a pi or claude-code target")
    _, mode, _, model = matched.groups()
    agent: str = "claude -p --permission-mode bypassPermissions" + (f" --effort {mode}" if mode else "")
    return (f"./gradlew populateArtifactURLs --coordinates={state.coordinate} "
            f"--agent-command='{agent} --model {model}'")


def route_line(state: RunState) -> str:
    if state.route == ROUTE_NEW:
        return "Route: new library: scaffolded; explore until no actionable dynamic-access class remains."
    goal: str = (f"at least the previous version's dynamic-access coverage ratio, "
                 f"{state.goal_covered_calls}/{state.goal_total_calls} of its calls"
                 if state.goal_covered_calls is not None else "no actionable dynamic-access class left")
    repair: str = "repair the version, then explore" if state.route == ROUTE_REPAIR else "add the version, then explore"
    return f"Route: {repair}; goal: {goal}."


def write(workspace: str, state: RunState) -> None:
    entry = find_index_entry_for_version(state.worktree, state.group, state.artifact, state.version) or {}
    packages: str = ", ".join(f"`{package}`" for package in entry.get("allowed-packages") or []) or "set from the library JAR"
    lines: list[str] = [
        f"# {state.coordinate} (issue #{state.issue_number}, {state.label})",
        "",
        f"- {route_line(state)}",
        f"- Previous version: `{state.previous_coordinate}`" if state.previous_coordinate else "- Previous version: none",
        f"- Run worktree: `{state.worktree}`",
        f"- Test project: `{state.path(state.test_dir)}`",
        f"- Build file: `{state.path(os.path.join(state.test_dir, 'build.gradle'))}`",
        f"- Docker image list: `{state.path(os.path.join(state.test_dir, 'required-docker-images.txt'))}`",
        f"- Shipped metadata: `{state.path(state.metadata_dir)}`",
        f"- Index file: `{state.path(state.index_path)}`",
        f"- Allowed packages: {packages}",
        f"- Main sources, once the setup tail downloads them: `{source_context_dir(state)}`",
        f"- URL discovery: `{url_discovery_command(state)}`",
        f"- Reporter-requested metadata: `{runtime_path(workspace, 'reporter-request.md')}`"
        if state.has_reporter_request else "- Reporter-requested metadata: none",
        "",
    ]
    if state.route == ROUTE_REPAIR:
        # Only the `fails-*` labels see the bar (§FS-unsupportable-version-diagnosis).
        lines += [skip_record.bar(state.version)]
    write_text(runtime_path(workspace, "run.md"), "\n".join(lines))
