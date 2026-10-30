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
import re
import shutil
import subprocess
from dataclasses import dataclass

from utility_scripts.gradle_environment import gradle_command_environment

from exit_codes import BadInput

# The output tail a fix request carries per failing command (§AR-forge-workflow-prompts.5).
TAIL_LINES: int = 200
UPSTREAM: str = "oracle/graalvm-reachability-metadata"
BASE_BRANCH: str = "master"
# Gradle's innermost cause for a module no repository has, or none a JVM test can use, and
# the scaffold's for a coordinate without a JAR, such as a BOM (§AR-forge-workflow-pipeline.4.1).
# A file of a module, `Could not find ejb-2.0.jar (javax.ejb:ejb:2.0)`, is a POM without its JAR.
ABSENT: re.Pattern[str] = re.compile(r"Could not find (?:any matches for |any version that matches |[\w.-]+ \()?"
                                     r"[\w.-]+:[\w.-]+|No matching variant of |Failed to resolve JAR for ")
# The wrapper around any cause, an offline cache miss or a corrupt POM too: it only adds detail.
WRAPPER: re.Pattern[str] = re.compile(r"Could not resolve ")
# A repository access failure: Gradle reports a 404 as `Could not find`, so any status here,
# 401 and 403 included, means the repository failed (§AR-forge-workflow-pipeline.4.1).
UNREACHABLE: re.Pattern[str] = re.compile(r"Could not (?:GET|HEAD|get resource) '")


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


@dataclass
class Unreachable:
    """A repository the build could not reach, or that answered with anything but not-found."""

    detail: str


def dependency_failure(result: TaskResult, reason: str, coordinate: str = "") -> BadInput | Unreachable | None:
    """What a failed build's dependency resolution says, decided as the issue-form gate decides.

    A repository that failed is `Unreachable`, an infrastructure failure a later
    cycle retries; a module or its JAR no repository has, or none a JVM test can
    use, is a `BadInput` naming `reason`, since it fails identically on every retry
    (§AR-forge-workflow-pipeline.4.1); anything else is `None`. Only innermost
    cause lines decide, and with `coordinate` only those naming it. The detail is
    the cause and wrapper lines, each once and in order: an excerpt of a long
    output keeps only failure lines and would drop them (§FS-forge-run-requirements.2).
    """
    if result.ok:
        return None
    lines: list[str] = [line.rstrip() for line in result.output.splitlines()]
    unreachable: list[str] = [line for line in lines if UNREACHABLE.search(line)]
    if unreachable:
        return Unreachable("\n".join(dict.fromkeys(unreachable)))
    if not any(ABSENT.search(line) and _names(line, coordinate) for line in lines):
        return None
    detail: list[str] = [line for line in lines if WRAPPER.search(line) or ABSENT.search(line)]
    return BadInput(reason, "\n".join(dict.fromkeys(detail)))


def _names(line: str, coordinate: str) -> bool:
    """Whether the line names the coordinate, not a longer one; any line does when it is empty."""
    return not coordinate or re.search(rf"(?<![\w.-]){re.escape(coordinate)}(?![\w-]|\.\w)", line) is not None


def gradle_display(arguments: list[str]) -> str:
    """The command as an agent would type it from the worktree root."""
    return " ".join(["./gradlew", *arguments])


def run_gradle(worktree: str, arguments: list[str], log_path: str) -> TaskResult:
    """Run `./gradlew <arguments>` in the worktree and keep its full output in `log_path`."""
    command: list[str] = [os.path.join(worktree, "gradlew"), *arguments, "--console=plain"]
    display: str = gradle_display(arguments)
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


def stop_gradle_daemons(worktree: str) -> None:
    """Stop the daemons of the worktree's Gradle home, so no build state of an earlier run leaks in.

    §AR-forge-workflow-pipeline.4
    """
    subprocess.run([os.path.join(worktree, "gradlew"), "--stop"], cwd=worktree,
                   env=gradle_command_environment(worktree), stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                   check=False)


def coordinate_task(task: str, coordinate: str, *extra: str) -> list[str]:
    """The arguments of one coordinate-scoped harness task."""
    return [task, f"-Pcoordinates={coordinate}", *extra]


def git(worktree: str, *args: str, capture_stderr: bool = False) -> str:
    """Run git in the worktree and return stdout; a failure raises.

    With `capture_stderr`, git's own message rides on the `CalledProcessError`.
    """
    return subprocess.run(
        ["git", *args],
        cwd=worktree,
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE if capture_stderr else None,
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
