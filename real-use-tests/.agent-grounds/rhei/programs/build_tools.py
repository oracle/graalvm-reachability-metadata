# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Gradle and git invocations the program states share.

Every program runs the harness tasks of §AR-real-use-test-harness.2 from the
run worktree; nothing here interprets results beyond pass and fail
(§FS-real-use-test-generation).
"""
from __future__ import annotations

import os
import subprocess
from dataclasses import dataclass

TAIL_LINES: int = 120


@dataclass
class TaskResult:
    """One harness task run: its verdict, the log on disk, and the log's tail."""

    ok: bool
    log_path: str
    tail: str


def run_gradle(
        worktree: str,
        task: str,
        coordinate: str,
        log_path: str,
        properties: dict[str, str] | None = None,
) -> TaskResult:
    """Run one coordinate-scoped harness task and keep its full output in `log_path`."""
    command: list[str] = [
        os.path.join(worktree, "gradlew"),
        task,
        f"-Pcoordinates={coordinate}",
        "--console=plain",
    ]
    for key, value in (properties or {}).items():
        command.append(f"-P{key}={value}")
    os.makedirs(os.path.dirname(log_path), exist_ok=True)
    print(f"[gradle] {' '.join(command)}", flush=True)
    completed: subprocess.CompletedProcess[str] = subprocess.run(
        command,
        cwd=worktree,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )
    with open(log_path, "w", encoding="utf-8") as handle:
        handle.write(completed.stdout)
    tail: str = "\n".join(completed.stdout.splitlines()[-TAIL_LINES:])
    return TaskResult(ok=completed.returncode == 0, log_path=log_path, tail=tail)


def git(worktree: str, *args: str) -> str:
    """Run git in the worktree and return stdout; a failure raises."""
    return subprocess.run(
        ["git", *args],
        cwd=worktree,
        check=True,
        stdout=subprocess.PIPE,
        text=True,
    ).stdout


def checkpoint(worktree: str, paths: list[str], message: str) -> str:
    """Commit the current state of `paths` on the run branch and return HEAD.

    A checkpoint fixes what an earlier phase produced, so a later program can
    measure or restore against it (§AR-real-use-test-workflow.2.2).
    """
    git(worktree, "add", "-A", "--", *paths)
    if git(worktree, "diff", "--cached", "--name-only"):
        git(worktree, "commit", "--quiet", "-m", message)
    return git(worktree, "rev-parse", "HEAD").strip()


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
        if exists_at_base:
            git(worktree, "checkout", base_commit, "--", path)
        elif os.path.isfile(os.path.join(worktree, path)):
            os.remove(os.path.join(worktree, path))
