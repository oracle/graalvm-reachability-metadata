# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Gradle and git invocations the program states share.

Every program runs the harness tasks from the run worktree and judges only pass
or fail (§AR-forge-workflow-pipeline.5); checkpoints are commits on the run
branch that a later program measures or restores against
(§AR-forge-workflow-boundary).
"""
from __future__ import annotations

import os
import shutil
import subprocess
from dataclasses import dataclass

from utility_scripts.gradle_environment import gradle_command_environment

# The output tail a fix request carries per failing command (§AR-forge-workflow-prompts.5).
TAIL_LINES: int = 200
UPSTREAM: str = "oracle/graalvm-reachability-metadata"
BASE_BRANCH: str = "master"


@dataclass
class TaskResult:
    """One Gradle run: the command as an agent would type it, its verdict, and its output."""

    command: str
    ok: bool
    log_path: str
    output: str

    @property
    def tail(self) -> str:
        return "\n".join(self.output.splitlines()[-TAIL_LINES:])


def run_gradle(worktree: str, arguments: list[str], log_path: str) -> TaskResult:
    """Run `./gradlew <arguments>` in the worktree and keep its full output in `log_path`."""
    command: list[str] = [os.path.join(worktree, "gradlew"), *arguments, "--console=plain"]
    display: str = " ".join(["./gradlew", *arguments])
    os.makedirs(os.path.dirname(log_path), exist_ok=True)
    print(f"[gradle] {display}", flush=True)
    completed: subprocess.CompletedProcess[str] = subprocess.run(
        command,
        cwd=worktree,
        env=gradle_command_environment(worktree),
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )
    with open(log_path, "w", encoding="utf-8") as handle:
        handle.write(completed.stdout)
    return TaskResult(command=display, ok=completed.returncode == 0, log_path=log_path, output=completed.stdout)


def coordinate_task(task: str, coordinate: str, *extra: str) -> list[str]:
    """The arguments of one coordinate-scoped harness task."""
    return [task, f"-Pcoordinates={coordinate}", *extra]


def git(worktree: str, *args: str) -> str:
    """Run git in the worktree and return stdout; a failure raises."""
    return subprocess.run(
        ["git", *args],
        cwd=worktree,
        check=True,
        stdout=subprocess.PIPE,
        text=True,
    ).stdout


def upstream_remote(repo: str) -> str:
    """The remote that points at the upstream repository, `origin` when none names it."""
    for line in git(repo, "remote", "-v").splitlines():
        name, url = line.split()[:2]
        if UPSTREAM in url.removesuffix(".git"):
            return name
    return "origin"


def rebase_onto_upstream(worktree: str) -> str:
    """Rebase the run branch onto fresh upstream `master` and return that commit; a conflict raises.

    The pre-publication gate then verifies the tree that is pushed
    (§FS-local-ci-equivalent-verification.2).
    """
    remote: str = upstream_remote(worktree)
    git(worktree, "fetch", "--quiet", remote, BASE_BRANCH)
    try:
        git(worktree, "rebase", "--quiet", f"{remote}/{BASE_BRANCH}")
    except subprocess.CalledProcessError:
        subprocess.run(["git", "rebase", "--abort"], cwd=worktree, check=False, capture_output=True)
        raise
    return git(worktree, "rev-parse", f"{remote}/{BASE_BRANCH}").strip()


def head(worktree: str) -> str:
    return git(worktree, "rev-parse", "HEAD").strip()


def checkpoint(worktree: str, paths: list[str], message: str) -> str:
    """Commit the current state of `paths` on the run branch and return HEAD."""
    existing: list[str] = [path for path in paths if os.path.exists(os.path.join(worktree, path))]
    tracked: list[str] = [path for path in paths if path not in existing and _tracked(worktree, path)]
    if existing or tracked:
        git(worktree, "add", "-A", "--", *existing, *tracked)
    if git(worktree, "diff", "--cached", "--name-only"):
        git(worktree, "commit", "--quiet", "-m", message)
    return head(worktree)


def _tracked(worktree: str, path: str) -> bool:
    return bool(git(worktree, "ls-files", "--", path).strip())


def changed_paths(worktree: str, base_commit: str) -> list[str]:
    """Every repository path that differs from `base_commit`, tracked or not."""
    tracked: list[str] = git(worktree, "diff", "--name-only", base_commit).splitlines()
    untracked: list[str] = git(worktree, "ls-files", "--others", "--exclude-standard").splitlines()
    return sorted(set(tracked) | set(untracked))


def restore_paths(worktree: str, base_commit: str, paths: list[str]) -> None:
    """Return each path to its `base_commit` content, deleting paths the base did not have."""
    for path in paths:
        exists_at_base: bool = subprocess.run(
            ["git", "cat-file", "-e", f"{base_commit}:{path}"],
            cwd=worktree,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        ).returncode == 0
        absolute: str = os.path.join(worktree, path)
        if exists_at_base:
            git(worktree, "checkout", base_commit, "--", path)
        elif os.path.isdir(absolute):
            shutil.rmtree(absolute)
        elif os.path.exists(absolute):
            os.remove(absolute)


def reset_to(worktree: str, commit: str, paths: list[str]) -> list[str]:
    """Discard everything under `paths` since `commit` and return what was discarded."""
    discarded: list[str] = [
        path for path in changed_paths(worktree, commit)
        if any(path == root or path.startswith(root.rstrip("/") + "/") for root in paths)
    ]
    git(worktree, "reset", "--quiet", "--hard", commit)
    restore_paths(worktree, commit, [path for path in discarded if os.path.exists(os.path.join(worktree, path))])
    return discarded
