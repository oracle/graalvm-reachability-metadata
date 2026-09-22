# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Shared paths, records, and worktree plumbing for the coverage benchmark.

Durable workspace record locations, JSON/schema helpers, and source-worktree
management shared by the benchmark runner's commands
(§FS-code-coverage-benchmarking §AR-code-coverage-benchmarking).
"""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
import uuid
from pathlib import Path
from typing import Any

from jsonschema import Draft202012Validator, FormatChecker

FORGE_ROOT = Path(__file__).resolve().parents[1]
REPOSITORY_ROOT = FORGE_ROOT.parent
RESULT_SCHEMA_PATH = (
    FORGE_ROOT / "schemas" / "code_coverage_benchmark_result_schema.json"
)
DEFAULT_WORKSPACE_ROOT = (
    FORGE_ROOT / "local_repositories" / "code_coverage_benchmarks"
)
BENCHMARK_DIR = Path("runtime") / "code-coverage" / "benchmark"
RUN_RECORD = BENCHMARK_DIR / "run.json"
RESULT_RECORD = BENCHMARK_DIR / "result.json"
PUBLICATION_MARKER = BENCHMARK_DIR / "publication.json"


class BenchmarkError(RuntimeError):
    """Raised when benchmark evidence or repository state is unsafe."""


def read_json(path: Path) -> Any:
    with path.open(encoding="utf-8") as source:
        return json.load(source)


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.{uuid.uuid4().hex}.tmp")
    with temporary.open("w", encoding="utf-8") as destination:
        json.dump(value, destination, indent=2, ensure_ascii=False)
        destination.write("\n")
    os.replace(temporary, path)


def validate(value: Any, schema_path: Path) -> None:
    schema: dict[str, Any] = read_json(schema_path)
    validator = Draft202012Validator(schema, format_checker=FormatChecker())
    validator.validate(value)


def git_output(repo_path: Path, *arguments: str) -> str:
    result = subprocess.run(
        ["git", *arguments],
        cwd=repo_path,
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()


def _runner_commit() -> str:
    return git_output(REPOSITORY_ROOT, "rev-parse", "HEAD")


def remove_worktree(
        path: Path,
        repository_root: Path = REPOSITORY_ROOT,
) -> None:
    subprocess.run(
        ["git", "worktree", "remove", "--force", str(path)],
        cwd=repository_root,
        check=True,
    )


def discard_source_worktree(path: Path) -> None:
    try:
        remove_worktree(path)
    except (OSError, subprocess.SubprocessError) as error:
        print(
            f"ERROR: Could not remove published source worktree {path}: "
            f"{error}",
            file=sys.stderr,
        )


def _path_key(path: Path) -> str:
    return os.path.normcase(os.path.abspath(path))


def _registered_worktree_paths(repository_root: Path) -> set[str]:
    output = git_output(
        repository_root,
        "worktree",
        "list",
        "--porcelain",
        "-z",
    )
    return {
        _path_key(Path(field.removeprefix("worktree ")))
        for field in output.split("\0")
        if field.startswith("worktree ")
    }


def _remove_existing_source_worktree(
        path: Path,
        repository_root: Path,
) -> None:
    if path.name != "source":
        raise BenchmarkError(
            f"Refusing to replace a non-source worktree path: {path}"
        )
    if _path_key(path) in _registered_worktree_paths(repository_root):
        remove_worktree(path, repository_root)
    if not os.path.lexists(path):
        return
    if path.is_symlink() or path.is_file():
        path.unlink()
    else:
        shutil.rmtree(path)


def create_source_worktree(
        path: Path,
        suite_commit: str,
        repository_root: Path = REPOSITORY_ROOT,
) -> None:
    _remove_existing_source_worktree(path, repository_root)
    path.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        ["git", "worktree", "add", "--detach", str(path), suite_commit],
        cwd=repository_root,
        check=True,
    )


def run_record_path(workspace: Path) -> Path:
    return workspace / RUN_RECORD


def result_record_path(workspace: Path) -> Path:
    return workspace / RESULT_RECORD


def publication_marker_path(workspace: Path) -> Path:
    return workspace / PUBLICATION_MARKER


def ensure_run_record(
        workspace: Path,
        identity: dict[str, Any],
) -> dict[str, Any]:
    path = run_record_path(workspace)
    if path.is_file():
        existing = read_json(path)
        for key, value in identity.items():
            if existing.get(key) != value:
                raise BenchmarkError(
                    f"Existing benchmark run record conflicts on '{key}'."
                )
        return existing
    write_json(path, identity)
    return identity


def record_completion(
        workspace: Path,
        status: str,
        exit_code: int | None,
) -> None:
    run_record = read_json(run_record_path(workspace))
    run_record["requestedStatus"] = status
    run_record["rheiExitCode"] = exit_code
    write_json(run_record_path(workspace), run_record)


def write_terminal_result(message: str) -> None:
    """Satisfy Rhei's terminal-result obligation for a program worker.

    Rhei requires a non-empty `runtime/results/<task-id>.md` on every edge into
    a final state, and hands a program the absolute path in `RHEI_RESULT_PATH`
    because a program has no prompt to carry it. A program state whose exit-0
    edge is terminal must write it, or the task stalls instead of advancing.
    §FS-code-coverage-benchmarking.2
    """
    result_path = os.environ.get("RHEI_RESULT_PATH")
    if not result_path:
        return
    path = Path(result_path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(message.rstrip("\n") + "\n", encoding="utf-8")
