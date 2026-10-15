# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The `prepare` program: verify inputs, spawn and attach the run worktree, scaffold.

§FS-real-use-test-generation.2.1. Inputs are verified before anything runs
(§root/PRCPL-verify-inputs); the worktree is branched from the checkout's HEAD
so the template and harness the run uses are the ones it was started from.
"""
from __future__ import annotations

import argparse
import os
import subprocess
import sys
from datetime import datetime, timezone
from typing import Any

from build_tools import checkpoint, git, run_gradle
from exit_codes import BAD_INPUT, INFRASTRUCTURE, OK
from run_state import COORDINATE_PATTERN, RunState, read_json, runtime_path, save


def _resolve(workspace: str, path: str) -> str:
    return path if os.path.isabs(path) else os.path.normpath(os.path.join(workspace, path))


def _spawn_worktree(repo: str, path: str, branch: str) -> None:
    """Create the run worktree, or reuse it when a resumed run already made it."""
    if os.path.exists(path):
        current: str = git(path, "rev-parse", "--abbrev-ref", "HEAD").strip()
        if current != branch:
            raise ValueError(f"{path} exists on branch {current}, expected {branch}")
        return
    os.makedirs(os.path.dirname(path), exist_ok=True)
    branch_exists: bool = subprocess.run(
        ["git", "rev-parse", "--verify", "--quiet", f"refs/heads/{branch}"],
        cwd=repo,
        stdout=subprocess.DEVNULL,
    ).returncode == 0
    if branch_exists:
        git(repo, "worktree", "add", path, branch)
    else:
        git(repo, "worktree", "add", "-b", branch, path, "HEAD")


def allowed_packages(repo: str, group: str, artifact: str, version: str) -> list[str]:
    """The `allowed-packages` of the `index.json` entry whose `tested-versions` lists `version`.

    The run only targets a library the repository already supports
    (§FS-real-use-test-generation.1), so the entry exists.
    """
    index: list[dict[str, Any]] = read_json(os.path.join(repo, "metadata", group, artifact, "index.json"))
    entry: dict[str, Any] = next(entry for entry in index if version in entry.get("tested-versions", []))
    return list(entry["allowed-packages"])


def _attach_worktree(workspace: str, worktree: str) -> None:
    """Point Rhei at the worktree, so every later agent state of the task runs inside it."""
    task_id: str | None = os.environ.get("RHEI_TASK_ID")
    if not task_id:
        return
    ref: str = os.path.join(workspace, "runtime", "worktree-refs", f"{task_id}.yaml")
    os.makedirs(os.path.dirname(ref), exist_ok=True)
    with open(ref, "w", encoding="utf-8") as handle:
        handle.write(f"path: {worktree}\n")


def run(args: argparse.Namespace) -> int:
    workspace: str = os.path.abspath(args.workspace)
    if not COORDINATE_PATTERN.match(args.coordinate):
        print(f"ERROR: '{args.coordinate}' is not group:artifact:version.", file=sys.stderr)
        return BAD_INPUT
    if args.scenario_passes < 1:
        print("ERROR: scenario passes must be positive.", file=sys.stderr)
        return BAD_INPUT
    repo: str = _resolve(workspace, args.repo_checkout)
    group, artifact, version = args.coordinate.split(":")

    # Resolved like git's own top-level path, which the worktree ref must name.
    worktree: str = os.path.realpath(
        os.path.join(_resolve(workspace, args.worktree_root), f"real-use-{artifact}-{version}")
    )
    branch: str = f"rhei/real-use-{artifact}-{version}"
    try:
        _spawn_worktree(repo, worktree, branch)
    except (ValueError, subprocess.CalledProcessError) as error:
        print(f"ERROR: worktree: {error}", file=sys.stderr)
        return BAD_INPUT
    state: RunState = RunState(
        coordinate=args.coordinate,
        repo_checkout=repo,
        worktree=worktree,
        branch=branch,
        base_commit=git(worktree, "rev-parse", "HEAD").strip(),
        allowed_packages=allowed_packages(repo, group, artifact, version),
        scenario_passes=args.scenario_passes,
        started_at=datetime.now(timezone.utc).replace(microsecond=0).isoformat(),
    )

    if not os.path.isdir(state.project_path):
        scaffold = run_gradle(
            worktree, "scaffoldRealUseTest", state.coordinate, runtime_path(workspace, "logs", "scaffold.log"),
        )
        if not scaffold.ok or not os.path.isdir(state.project_path):
            print(f"ERROR: scaffolding failed:\n{scaffold.tail}", file=sys.stderr)
            return INFRASTRUCTURE
    state.base_commit = checkpoint(worktree, [state.project_dir], f"Scaffold real-use project for {artifact}")
    _attach_worktree(workspace, worktree)
    save(workspace, state)
    print(f"[prepare] worktree {worktree} on {branch}, checkpoint {state.base_commit}")
    return OK
