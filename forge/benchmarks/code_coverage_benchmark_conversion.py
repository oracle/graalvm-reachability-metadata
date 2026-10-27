# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Issue-free benchmark conversion inside an isolated worktree.

Writes the issue-compatible conversion artifacts for one benchmark cell
without any GitHub access (§FS-code-coverage-benchmarking.2).
"""

from __future__ import annotations

import argparse
from pathlib import Path

from benchmarks.code_coverage_benchmark_common import (
    BenchmarkError,
    ensure_run_record,
    git_output,
    write_json,
    write_terminal_result,
)
from utility_scripts.metadata_index import resolve_test_dir


def convert_workspace(args: argparse.Namespace) -> None:
    """Write issue-compatible conversion artifacts without GitHub access."""
    workspace = Path(args.workspace).resolve()
    source_worktree = Path(args.source_worktree).resolve()
    runner_forge_path = Path(args.runner_forge_path).resolve()
    if git_output(source_worktree, "rev-parse", "HEAD") != args.suite_commit:
        raise BenchmarkError(
            "Benchmark source worktree is not at the configured suite commit."
        )
    if git_output(runner_forge_path.parent, "rev-parse", "HEAD") != args.runner_commit:
        raise BenchmarkError(
            "Benchmark runner checkout is not at the recorded runner commit."
        )
    group, artifact, _ = args.coordinate.split(":")
    test_dir = Path(
        resolve_test_dir(
            str(source_worktree),
            group,
            artifact,
            args.coordinate.rsplit(":", 1)[1],
        )
    )
    coverage_suite = test_dir / "code-coverage-improvement"
    if coverage_suite.exists():
        raise BenchmarkError(
            "The fixed benchmark input already contains a coverage suite: "
            f"{coverage_suite}"
        )
    # The pin supplies the input; the runner supplies the measurement. Resolving
    # helpers from the pinned worktree would freeze ranking, classification, and
    # prompt rendering at the suite commit and split the final-metrics contract
    # away from the publisher that has to read it.
    # §FS-code-coverage-benchmarking.1 §AR-code-coverage-benchmarking.1
    work_path = runner_forge_path
    conversion = {
        "coordinate": args.coordinate,
        "worktreePath": str(source_worktree),
        "workPath": str(work_path),
        "coverageSuiteAbsolutePath": str(coverage_suite),
        "coverageSuiteRepoRelativePath": str(
            coverage_suite.relative_to(source_worktree)
        ),
    }
    issue_dir = workspace / "runtime" / "code-coverage" / "issues"
    write_json(issue_dir / "conversion.json", conversion)
    (issue_dir / "inventory.md").write_text(
        f"# Benchmark input\n\n- Coordinate: `{args.coordinate}`\n"
        f"- Suite commit: `{args.suite_commit}`\n",
        encoding="utf-8",
    )
    (issue_dir / "conversion.md").write_text(
        "# Benchmark conversion\n\n"
        f"- Coordinate: `{args.coordinate}`\n"
        f"- Measured input: `{source_worktree}` at `{args.suite_commit}`\n"
        f"- Measuring helpers: `{work_path}` at `{args.runner_commit}`\n"
        f"- Coverage suite: `{coverage_suite}`\n",
        encoding="utf-8",
    )
    run_identity = {
        "schemaVersion": "1.0.0",
        "runId": args.run_id,
        "startedAt": args.started_at,
        "benchmarkSuiteCommit": args.suite_commit,
        "runnerCommit": args.runner_commit,
        "coordinate": args.coordinate,
        "workspaceName": workspace.name,
        "agent": args.agent,
        "configuredModel": args.configured_model,
        "targetModel": args.target_model,
        "thinking": args.thinking,
        "strategy": args.strategy,
        "checkedInAllMethods": args.checked_in_all_methods,
        "sourceWorktree": str(source_worktree),
        "runnerForgePath": str(runner_forge_path),
    }
    ensure_run_record(workspace, run_identity)
    work_path_output = (
        workspace
        / "runtime"
        / "code-coverage"
        / "work"
        / "code-coverage-99000.code-coverage-convert.md"
    )
    work_path_output.parent.mkdir(parents=True, exist_ok=True)
    work_path_output.write_text(
        "# Benchmark conversion complete\n\n"
        "No GitHub issue or Project operation was performed.\n",
        encoding="utf-8",
    )
    write_terminal_result(
        f"Prepared benchmark {args.run_id} for {args.coordinate} at suite "
        f"commit {args.suite_commit}. Conversion artifacts written; no GitHub "
        f"issue or Project operation was performed."
    )
