# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Diff-validated Git publication of one benchmark result record.

Serialized, idempotent publication of the compact result onto a
deterministic branch through a disposable worktree
(§FS-code-coverage-benchmarking.3 §AR-code-coverage-benchmarking).
"""

from __future__ import annotations

import fcntl
import json
import subprocess
import sys
import uuid
from dataclasses import dataclass
from pathlib import Path
from typing import Any, TextIO

from benchmarks.code_coverage_benchmark_common import (
    RESULT_SCHEMA_PATH,
    BenchmarkError,
    git_output,
    read_json,
    validate,
    write_json,
)
from git_scripts.common_git import (
    GitTransportError,
    get_authenticated_login,
    run_git_transport,
)
from git_scripts.publication_descriptor import (
    build_publication_branch,
    build_publication_id,
)

COMMIT_SUBJECT = "Record code coverage benchmark"
BENCHMARK_TASK_TYPE = "code-coverage-benchmark-result"
MAX_PUBLISH_ATTEMPTS = 5


@dataclass(frozen=True)
class BenchmarkPublication:
    """Durable location of one proposed or already merged result."""

    commit: str
    branch: str | None
    publication_id: str
    already_merged: bool


def metrics_relative_path(coordinate: str) -> Path:
    group, artifact, version = coordinate.split(":")
    return Path("code-coverage-benchmarks") / group / artifact / f"{version}.json"


def merge_result(path: Path, result: dict[str, Any]) -> bool:
    entries: list[dict[str, Any]] = []
    if path.is_file():
        value = read_json(path)
        if not isinstance(value, list):
            raise BenchmarkError(f"Benchmark result file is not a list: {path}")
        entries = value
    for existing in entries:
        if existing.get("runId") != result["runId"]:
            continue
        if existing != result:
            raise BenchmarkError(
                f"Run ID {result['runId']} already has different metrics."
            )
        validate(entries, RESULT_SCHEMA_PATH)
        return False
    entries.append(result)
    entries.sort(key=lambda entry: (entry["timestamp"], entry["runId"]))
    validate(entries, RESULT_SCHEMA_PATH)
    write_json(path, entries)
    return True


def _publish_lock(metrics_repo_path: Path) -> TextIO:
    common_dir = git_output(metrics_repo_path, "rev-parse", "--git-common-dir")
    common_path = Path(common_dir)
    if not common_path.is_absolute():
        common_path = (metrics_repo_path / common_path).resolve()
    lock_path = common_path / "code-coverage-benchmark-publish.lock"
    lock_handle = lock_path.open("a+", encoding="utf-8")
    fcntl.flock(lock_handle.fileno(), fcntl.LOCK_EX)
    return lock_handle


def _create_publication_worktree(
        repository_root: Path,
        path: Path,
        start_point: str,
) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        ["git", "worktree", "add", "--detach", str(path), start_point],
        cwd=repository_root,
        check=True,
    )


def _discard_publication_worktree(
        repository_root: Path,
        path: Path,
) -> None:
    removal = subprocess.run(
        ["git", "worktree", "remove", "--force", str(path)],
        cwd=repository_root,
        check=False,
        capture_output=True,
        text=True,
    )
    if removal.returncode != 0:
        detail = removal.stderr.strip() or removal.stdout.strip()
        print(
            f"ERROR: Could not remove disposable publication worktree "
            f"{path}: {detail}",
            file=sys.stderr,
        )


def commit_paths(repository: Path, paths: list[Path], subject: str) -> str:
    subprocess.run(
        ["git", "add", "--", *(str(path) for path in paths)],
        cwd=repository,
        check=True,
    )
    # Commit with the ambient identity, like every other publication path. An
    # override here reached GitHub as an address no account can own, so the
    # published commits carried no author at all.
    subprocess.run(
        ["git", "commit", "-m", subject],
        cwd=repository,
        check=True,
    )
    return git_output(repository, "rev-parse", "HEAD")


def _publication_identity(
        repository_root: Path,
        result: dict[str, Any],
) -> tuple[str, str, str]:
    producer = get_authenticated_login(cwd=str(repository_root))
    publication_id = build_publication_id(
        None,
        result["timestamp"],
        result["coordinate"],
        BENCHMARK_TASK_TYPE,
        benchmark_run_id=result["runId"],
    )
    _, artifact, version = result["coordinate"].split(":")
    branch_suffix = (
        f"benchmark-{artifact}-{version}-{result['configuredModel']}-{result['thinking']}"
    )
    branch = build_publication_branch(producer, branch_suffix, publication_id)
    return producer, publication_id, branch


def _json_at_ref(repository: Path, ref: str, path: Path) -> Any | None:
    completed = subprocess.run(
        ["git", "show", f"{ref}:{path}"],
        cwd=repository,
        check=False,
        capture_output=True,
        text=True,
    )
    if completed.returncode != 0:
        return None
    return json.loads(completed.stdout)


def _fetch_existing_publication(
        repository_root: Path,
        result: dict[str, Any],
        branch: str,
) -> str | None:
    remote_ref = f"refs/remotes/origin/{branch}"
    fetch = subprocess.run(
        [
            "git",
            "fetch",
            "--quiet",
            "origin",
            f"+refs/heads/{branch}:{remote_ref}",
        ],
        cwd=repository_root,
        check=False,
        capture_output=True,
        text=True,
    )
    if fetch.returncode != 0:
        return None
    entries = _json_at_ref(
        repository_root,
        remote_ref,
        metrics_relative_path(result["coordinate"]),
    )
    if not isinstance(entries, list):
        raise BenchmarkError(f"Existing publication branch is incomplete: {branch}")
    if result not in entries:
        raise BenchmarkError(f"Existing publication branch conflicts with run {result['runId']}")
    return git_output(repository_root, "rev-parse", remote_ref)


def publish_result(
        repository_root: Path,
        workspace: Path,
        result: dict[str, Any],
) -> BenchmarkPublication:
    relative_path = metrics_relative_path(result["coordinate"])
    _, publication_id, branch = _publication_identity(repository_root, result)
    lock_handle = _publish_lock(repository_root)
    try:
        for attempt in range(1, MAX_PUBLISH_ATTEMPTS + 1):
            publisher = workspace.parent / f"publisher-{uuid.uuid4().hex[:12]}"
            created = False
            try:
                run_git_transport(
                    ["fetch", "origin", "master"],
                    cwd=str(repository_root),
                )
                existing_commit = _fetch_existing_publication(
                    repository_root,
                    result,
                    branch,
                )
                if existing_commit is not None:
                    return BenchmarkPublication(
                        existing_commit, branch, publication_id, False,
                    )
                _create_publication_worktree(
                    repository_root,
                    publisher,
                    "origin/master",
                )
                created = True
                changed = merge_result(publisher / relative_path, result)
                if not changed:
                    return BenchmarkPublication(
                        git_output(publisher, "rev-parse", "HEAD"),
                        None,
                        publication_id,
                        True,
                    )
                result_commit = commit_paths(
                    publisher,
                    [relative_path],
                    COMMIT_SUBJECT,
                )
                run_git_transport(
                    ["push", "origin", f"HEAD:refs/heads/{branch}"],
                    cwd=str(publisher),
                )
                return BenchmarkPublication(
                    result_commit,
                    branch,
                    publication_id,
                    False,
                )
            except GitTransportError as error:
                if attempt == MAX_PUBLISH_ATTEMPTS:
                    raise
                print(
                    f"Retrying benchmark metrics publication after attempt "
                    f"{attempt} failed: {error}",
                    file=sys.stderr,
                )
            finally:
                if created:
                    _discard_publication_worktree(repository_root, publisher)
        raise BenchmarkError("Benchmark metrics publication exhausted retries.")
    finally:
        fcntl.flock(lock_handle.fileno(), fcntl.LOCK_UN)
        lock_handle.close()
