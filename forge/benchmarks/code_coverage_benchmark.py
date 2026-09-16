# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Run and publish fixed-input code coverage improvement benchmarks.

The runner owns deterministic matrix expansion, isolated worktrees, benchmark
conversion, compact metrics extraction, and diff-validated PR publication.
Rhei continues to own the coverage phases themselves.
§FS-code-coverage-benchmarking §AR-code-coverage-benchmarking
"""

from __future__ import annotations

import argparse
import datetime as dt
import os
import re
import shutil
import subprocess
import sys
import uuid
from pathlib import Path
from typing import Any

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from benchmarks.code_coverage_benchmark_common import (  # noqa: E402
    DEFAULT_WORKSPACE_ROOT,
    FORGE_ROOT,
    PUBLICATION_MARKER,
    REPOSITORY_ROOT,
    RESULT_RECORD,
    RUN_RECORD,
    BenchmarkError,
    _create_source_worktree,
    _discard_source_worktree,
    _ensure_run_record,
    _git_output,
    _publication_marker_path,
    _read_json,
    _record_completion,
    _remove_worktree,
    _result_record_path,
    _run_record_path,
    _runner_commit,
    _write_json,
    _write_terminal_result,
)
from benchmarks.code_coverage_benchmark_conversion import convert_workspace  # noqa: E402
from benchmarks.code_coverage_benchmark_metrics import (  # noqa: E402
    _collect_result,
    _failure_phase,
    _final_metrics_path,
)
from benchmarks.code_coverage_benchmark_publication import (  # noqa: E402
    COMMIT_SUBJECT,
    BenchmarkPublication,
    _commit_paths,
    _merge_result,
    _metrics_relative_path,
    _publish_result,
)
from benchmarks.code_coverage_benchmark_suite import (  # noqa: E402
    KNOWN_STRATEGIES,
    MatrixCell,
    Suite,
    expand_matrix,
    load_suite,
    print_matrix,
)
from git_scripts.common_git import GitTransportError  # noqa: E402


def _verify_preconditions(suite: Suite, cells: list[MatrixCell]) -> None:
    if shutil.which("rhei") is None:
        raise BenchmarkError("rhei is required and must be available on PATH.")
    for agent in {cell.configuration.agent for cell in cells}:
        executable = "claude" if agent == "claude-code" else agent
        if shutil.which(executable) is None:
            raise BenchmarkError(
                f"{executable} is required for selected agent '{agent}'."
            )
    subprocess.run(
        ["git", "cat-file", "-e", f"{suite.commit}^{{commit}}"],
        cwd=REPOSITORY_ROOT,
        check=True,
    )
    tracked_status = _git_output(
        REPOSITORY_ROOT,
        "status",
        "--porcelain",
        "--untracked-files=no",
    )
    if tracked_status:
        raise BenchmarkError(
            "The benchmark runner checkout must have no tracked changes."
        )


def _safe_segment(value: str) -> str:
    return re.sub(r"[^A-Za-z0-9_.-]+", "-", value).strip("-.")


def _new_run_id(cell: MatrixCell) -> str:
    timestamp = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    segments = [
        timestamp,
        f"l{cell.library.index}",
        _safe_segment(cell.configuration.agent),
        _safe_segment(cell.configuration.configured_model),
        cell.thinking,
    ]
    # Guided run ids keep their historical shape; only the baseline arm names
    # its strategy (§AR-code-coverage-benchmarking.2).
    if cell.strategy != "guided":
        segments.append(cell.strategy)
    segments.append(uuid.uuid4().hex[:10])
    return "-".join(segments)


def _run_identity(
        *,
        run_id: str,
        suite: Suite,
        runner_commit: str,
        cell: MatrixCell,
        source_worktree: Path,
) -> dict[str, Any]:
    return {
        "schemaVersion": "1.0.0",
        "runId": run_id,
        "startedAt": (
            dt.datetime.now(dt.timezone.utc)
            .isoformat(timespec="seconds")
            .replace("+00:00", "Z")
        ),
        "benchmarkSuiteCommit": suite.commit,
        "runnerCommit": runner_commit,
        "coordinate": cell.library.coordinate,
        "workspaceName": suite.workspace_name,
        "agent": cell.configuration.agent,
        "configuredModel": cell.configuration.configured_model,
        "targetModel": cell.configuration.target_model,
        "thinking": cell.thinking,
        "strategy": cell.strategy,
        "checkedInAllMethods": cell.library.all_methods,
        "sourceWorktree": str(source_worktree.resolve()),
        "runnerForgePath": str(FORGE_ROOT.resolve()),
    }


def _instantiate_command(
        suite: Suite,
        cell: MatrixCell,
        run_id: str,
        runner_commit: str,
        source_worktree: Path,
        workspace: Path,
        identity: dict[str, Any],
) -> list[str]:
    configuration = cell.configuration
    values = {
        "issue_number": str(suite.issue_number),
        "coordinate": cell.library.coordinate,
        "benchmark": "true",
        "benchmark_run_id": run_id,
        "benchmark_suite_commit": suite.commit,
        "benchmark_runner_commit": runner_commit,
        "benchmark_started_at": identity["startedAt"],
        "benchmark_source_worktree": str(source_worktree.resolve()),
        "benchmark_runner_forge_path": str(FORGE_ROOT.resolve()),
        "benchmark_agent": configuration.agent,
        "benchmark_model": configuration.configured_model,
        "benchmark_target_model": configuration.target_model,
        "benchmark_thinking": cell.thinking,
        "benchmark_checked_in_all_methods": str(cell.library.all_methods),
        "strategy": cell.strategy,
        "repo_checkout": str(source_worktree.resolve()),
        "work_subdir": "forge",
        "workspace_path": str(workspace.resolve()),
        "worker_agent": configuration.target(cell.thinking),
    }
    command = ["rhei", "instantiate", "code-coverage-improvement"]
    command.extend(f"{key}={value}" for key, value in values.items())
    command.extend(["--output", str(workspace), "--execute"])
    return command


def _execute_cell(
        suite: Suite,
        cell: MatrixCell,
        runner_commit: str,
        workspace_root: Path,
) -> tuple[bool, bool]:
    run_id = _new_run_id(cell)
    run_parent = workspace_root / run_id
    source_worktree = run_parent / "source"
    workspace = run_parent / suite.workspace_name
    try:
        _create_source_worktree(source_worktree, suite.commit)
    except (OSError, subprocess.SubprocessError) as error:
        configuration = cell.configuration
        print(
            "ERROR: Skipping benchmark cell "
            f"{cell.library.coordinate} | {configuration.agent} | "
            f"{configuration.configured_model} | {cell.thinking}: "
            f"could not create source worktree {source_worktree}: {error}",
            file=sys.stderr,
        )
        return False, False
    identity = _run_identity(
        run_id=run_id,
        suite=suite,
        runner_commit=runner_commit,
        cell=cell,
        source_worktree=source_worktree,
    )
    command = _instantiate_command(
        suite,
        cell,
        run_id,
        runner_commit,
        source_worktree,
        workspace,
        identity,
    )
    environment: dict[str, str] = dict(os.environ)
    # Repairs belong to the cell, so its agent drives them rather than the
    # launching machine's ambient role (§FS-code-coverage-benchmarking.2).
    environment.update(
        cell.configuration.analysis_role_environment(cell.thinking)
    )
    try:
        result = subprocess.run(
            command, cwd=FORGE_ROOT, check=False, env=environment
        )
        _ensure_run_record(workspace, identity)
        result_path = _result_record_path(workspace)
        recorded_result = _read_json(result_path) if result_path.is_file() else None
        status = (
            "success"
            if _publication_marker_path(workspace).is_file()
            or (
                isinstance(recorded_result, dict)
                and recorded_result.get("status") == "success"
            )
            else "failure"
        )
        _record_completion(workspace, status, result.returncode)
        # A workflow that already wrote its immutable record only failed to
        # push it, so Git publication is retried; a workflow that stopped
        # earlier is held for a human instead of publishing a crash-time
        # snapshot (§FS-code-coverage-benchmarking.3).
        if not _publication_marker_path(workspace).is_file() and result_path.is_file():
            publish_workspace(workspace, exit_code=result.returncode)
        if _publication_marker_path(workspace).is_file():
            _discard_source_worktree(source_worktree)
            print(f"Preserved benchmark workspace: {workspace.resolve()}")
            return status == "success", True
    except (BenchmarkError, OSError, subprocess.SubprocessError) as error:
        print(f"ERROR: Benchmark run {run_id} failed: {error}", file=sys.stderr)
        try:
            # The run record makes the held workspace discoverable by
            # retry-pending even when the failure preceded its first write.
            _ensure_run_record(workspace, identity)
            _record_completion(workspace, "failure", None)
        except (BenchmarkError, OSError) as record_error:
            print(
                f"ERROR: Benchmark run {run_id} has no run record: "
                f"{record_error}",
                file=sys.stderr,
            )
    _report_pending_intervention(workspace, source_worktree)
    return False, False


def _report_pending_intervention(workspace: Path, source_worktree: Path) -> None:
    """A benchmark run has no GitHub issue, so pending human intervention is
    launcher-local state: nothing is published, and the operator either resumes
    the workspace to terminal publication or explicitly publishes the failure
    (§FS-code-coverage-benchmarking.3)."""
    phase = _failure_phase(workspace)
    reason = (
        f"stopped in the {phase} phase"
        if phase
        else "stopped before terminal publication"
    )
    print(
        f"PENDING HUMAN INTERVENTION: {reason}; nothing was published. "
        "Resume the workspace or publish it with an explicit --status failure."
    )
    print(f"Preserved benchmark workspace: {workspace.resolve()}")
    print(f"Preserved source worktree: {source_worktree.resolve()}")


def publish_workspace(
        workspace: Path,
        *,
        requested_status: str | None = None,
        exit_code: int | None = None,
        repository_root: Path = REPOSITORY_ROOT,
) -> dict[str, Any]:
    """Collect and idempotently publish one workspace's compact result."""
    workspace = workspace.resolve()
    run: dict[str, Any] = _read_json(_run_record_path(workspace))
    known_exit = exit_code
    if known_exit is None:
        candidate = run.get("rheiExitCode")
        known_exit = candidate if type(candidate) is int else None
    result = _collect_result(workspace, requested_status, known_exit)
    publication = _publish_result(repository_root.resolve(), workspace, result)
    marker = {
        "schemaVersion": "1.0.0",
        "runId": result["runId"],
        "publishedAt": (
            dt.datetime.now(dt.timezone.utc)
            .isoformat(timespec="seconds")
            .replace("+00:00", "Z")
        ),
        "repositoryCommit": publication.commit,
        "publicationBranch": publication.branch,
        "publicationId": publication.publication_id,
        "publicationState": (
            "merged" if publication.already_merged else "branch-pushed"
        ),
        "resultPath": str(_metrics_relative_path(result["coordinate"])),
    }
    _write_json(_publication_marker_path(workspace), marker)
    print(
        f"Published benchmark result {result['runId']} on "
        + (
            "origin/master."
            if publication.already_merged
            else f"branch {publication.branch}; trusted Actions will open the PR."
        )
    )
    _write_terminal_result(
        f"Published benchmark result {result['runId']} "
        f"({result['status']}) for {result['coordinate']} at "
        f"{marker['resultPath']}, publication commit "
        f"{marker['repositoryCommit']}."
    )
    return result


def _convert_parser(subparsers: Any) -> None:
    parser = subparsers.add_parser("convert")
    parser.add_argument("--workspace", required=True)
    parser.add_argument("--coordinate", required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--started-at", required=True)
    parser.add_argument("--suite-commit", required=True)
    parser.add_argument("--runner-commit", required=True)
    parser.add_argument("--source-worktree", required=True)
    parser.add_argument("--runner-forge-path", required=True)
    parser.add_argument("--agent", required=True, choices=("pi", "claude-code"))
    parser.add_argument("--configured-model", required=True)
    parser.add_argument("--target-model", required=True)
    parser.add_argument(
        "--thinking",
        required=True,
        choices=("medium", "high", "xhigh"),
    )
    parser.add_argument("--checked-in-all-methods", required=True, type=int)
    parser.add_argument(
        "--strategy",
        default="guided",
        choices=KNOWN_STRATEGIES,
    )


def _run_parser(subparsers: Any) -> None:
    parser = subparsers.add_parser("run")
    parser.add_argument("--library-index", nargs="+", type=int)
    parser.add_argument("--agent", nargs="+")
    parser.add_argument("--model", nargs="+")
    parser.add_argument("--thinking", nargs="+")
    parser.add_argument("--strategy", nargs="+", choices=KNOWN_STRATEGIES)
    parser.add_argument(
        "--workspace-root",
        type=Path,
        default=DEFAULT_WORKSPACE_ROOT,
    )
    parser.add_argument("--dry-run", action="store_true")


def _publish_parser(subparsers: Any) -> None:
    parser = subparsers.add_parser("publish")
    parser.add_argument("--workspace", required=True, type=Path)
    parser.add_argument("--status", choices=("success", "failure"))
    parser.add_argument("--exit-code", type=int)


def _retry_parser(subparsers: Any) -> None:
    parser = subparsers.add_parser("retry-pending")
    parser.add_argument(
        "--workspace-root",
        type=Path,
        default=DEFAULT_WORKSPACE_ROOT,
    )


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="code_coverage_benchmark.py",
        description="Run or publish fixed code coverage benchmark cells.",
    )
    subparsers = parser.add_subparsers(dest="command", required=True)
    _run_parser(subparsers)
    _convert_parser(subparsers)
    _publish_parser(subparsers)
    _retry_parser(subparsers)
    return parser


def run_selected(args: argparse.Namespace) -> int:
    suite = load_suite()
    cells = expand_matrix(
        suite,
        library_indexes=args.library_index,
        agents=args.agent,
        models=args.model,
        thinking_levels=args.thinking,
        strategies=args.strategy,
    )
    print_matrix(cells)
    if args.dry_run:
        return 0
    _verify_preconditions(suite, cells)
    runner_commit = _runner_commit()
    workspace_root = args.workspace_root.resolve()
    failures = 0
    for cell in cells:
        workflow_succeeded, _ = _execute_cell(
            suite,
            cell,
            runner_commit,
            workspace_root,
        )
        if not workflow_succeeded:
            failures += 1
    return 1 if failures else 0


def retry_pending(args: argparse.Namespace) -> int:
    workspace_root = args.workspace_root.resolve()
    run_records = sorted(workspace_root.glob(f"*/code-coverage-99000/{RUN_RECORD}"))
    pending = [
        path.parents[3]
        for path in run_records
        if not _publication_marker_path(path.parents[3]).is_file()
    ]
    print(f"Found {len(pending)} unpublished benchmark workspace(s).")
    if not pending:
        return 0
    failures = 0
    held = 0
    for workspace in pending:
        run = _read_json(_run_record_path(workspace))
        # Only a workspace with a written record retries publication; one
        # without a record is held for a human decision
        # (§FS-code-coverage-benchmarking.3).
        if not _result_record_path(workspace).is_file():
            held += 1
            _report_pending_intervention(workspace, Path(run["sourceWorktree"]))
            continue
        try:
            publish_workspace(workspace)
            source = Path(run["sourceWorktree"])
            if source.exists():
                _discard_source_worktree(source)
        except (BenchmarkError, OSError, subprocess.SubprocessError) as error:
            failures += 1
            print(
                f"ERROR: Could not publish {workspace}: {error}",
                file=sys.stderr,
            )
    if held:
        print(f"{held} workspace(s) are pending human intervention.")
    return 1 if failures else 0


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        if args.command == "run":
            return run_selected(args)
        if args.command == "convert":
            convert_workspace(args)
            return 0
        if args.command == "publish":
            publish_workspace(
                args.workspace,
                requested_status=args.status,
                exit_code=args.exit_code,
            )
            return 0
        if args.command == "retry-pending":
            return retry_pending(args)
        raise BenchmarkError(f"Unsupported command: {args.command}")
    except (
            BenchmarkError,
            GitTransportError,
            OSError,
            subprocess.SubprocessError,
            ValueError,
    ) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
