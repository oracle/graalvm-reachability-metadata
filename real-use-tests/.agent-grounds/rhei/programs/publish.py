# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The `publish` program: record the native fix's deletions, commit, and push.

Publication checks nothing itself: `style-check` and the optional publication
fix ran before it (§FS-real-use-test-generation.2.7). Only the coordinate
project is staged (§FS-real-use-test-contract.2.2), the commit goes on top of
the run's checkpoints, and Forge's real-use route pushes the branch for the
trusted publisher, which renders the description from the facts written here
(§AR-real-use-test-workflow.6).
"""
from __future__ import annotations

import os
import subprocess
import sys
from typing import Any

import local_metadata
import scenarios
from build_tools import git
from exit_codes import INFRASTRUCTURE, NO_SCENARIO_LEFT, OK
from run_state import RunState, load, read_json, runtime_path, save, write_json

MAX_SUBJECT: int = 60
# The descriptor schema's bound on one removal reason.
MAX_REASON: int = 2000
PUBLISH_ROUTE: str = os.path.join("forge", "git_scripts", "publish_real_use_tests.py")


def _subject(state: RunState) -> str:
    subject: str = f"Add real-use tests for {state.artifact} {state.version}"
    return subject if len(subject) <= MAX_SUBJECT else subject[: MAX_SUBJECT - 3] + "..."


def _record_native_removals(workspace: str, state: RunState) -> None:
    """Record the scenarios the native fix agent deleted, with its reasons.

    §FS-real-use-test-generation.2.6
    """
    report_path: str = runtime_path(workspace, "reports", "native-agent-fix.json")
    if not os.path.isfile(report_path):
        return
    for item in read_json(report_path).get("deleted", []):
        path: str = scenarios.class_file(state.test_java_path, item.get("class", ""))
        if item.get("class") and not os.path.exists(path):
            state.record_removal(os.path.relpath(path, state.worktree), item.get("reason", ""))


def render_facts(state: RunState, files: list[str]) -> dict[str, Any]:
    """What the description reports: scenarios, removals, and test-local entries by library."""
    groups: dict[str, list[str]] = local_metadata.grouped_entries(
        state.metadata_path,
        state.allowed_packages,
        local_metadata.filter_prefixes(state.filter_path),
        scenarios.test_packages(state.test_java_path),
    )
    return {
        "scenarios": [
            {"class": scenarios.class_name(state.test_java_path, path), "subsystem": scenarios.subsystem(path)}
            for path in files
        ],
        "removed": [
            {"scenario": removal["scenario"], "reason": removal["reason"][:MAX_REASON]}
            for removal in state.removals
        ],
        "test_local_metadata": [{"library": group, "entries": entries} for group, entries in groups.items()],
    }


def _push(workspace: str, state: RunState, facts_path: str) -> int:
    """Hand the committed branch to Forge's real-use route, which pushes it."""
    forge: str = os.path.join(state.repo_checkout, "forge")
    venv_python: str = os.path.join(forge, ".venv", "bin", "python")
    command: list[str] = [
        venv_python if os.path.isfile(venv_python) else sys.executable,
        os.path.join(state.repo_checkout, PUBLISH_ROUTE),
        "--repo-path", state.worktree,
        "--coordinates", state.coordinate,
        "--issue-number", str(state.issue_number),
        "--timestamp", state.started_at,
        "--render-json", facts_path,
    ]
    print(f"[publish] {' '.join(command)}", flush=True)
    completed: subprocess.CompletedProcess[str] = subprocess.run(
        command,
        cwd=state.worktree,
        env=dict(os.environ, PYTHONPATH=forge),
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )
    with open(runtime_path(workspace, "logs", "publish-route.log"), "w", encoding="utf-8") as handle:
        handle.write(completed.stdout)
    pushed: list[str] = [line for line in completed.stdout.splitlines() if line.startswith("Pushed ")]
    if completed.returncode != 0 or not pushed:
        print(f"ERROR: Forge's real-use route did not push:\n{completed.stdout[-4000:]}", file=sys.stderr)
        return INFRASTRUCTURE
    with open(runtime_path(workspace, "publication", "branch.md"), "w", encoding="utf-8") as handle:
        handle.write(pushed[-1] + "\n")
    print(f"[publish] {pushed[-1]}")
    return OK


def publish(workspace: str) -> int:
    state: RunState = load(workspace)
    _record_native_removals(workspace, state)
    save(workspace, state)
    facts_path: str = runtime_path(workspace, "publication", "render.json")
    write_json(facts_path, render_facts(state, scenarios.scenario_files(state.test_java_path)))
    git(state.worktree, "add", "-A", "--", state.project_dir)
    if git(state.worktree, "diff", "--cached", "--name-only"):
        git(state.worktree, "commit", "--quiet", "-m", _subject(state))
        print(f"[publish] committed {git(state.worktree, 'rev-parse', 'HEAD').strip()} on {state.branch}")
    elif _subject(state) not in git(state.worktree, "log", "--format=%s", f"{state.base_commit}..HEAD").splitlines():
        # A retried publish finds its own commit and pushes again; otherwise nothing changed.
        print("[publish] the coordinate project is unchanged; nothing to publish")
        return NO_SCENARIO_LEFT
    return _push(workspace, state, facts_path)
