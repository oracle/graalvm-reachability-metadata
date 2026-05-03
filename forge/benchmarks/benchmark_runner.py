# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""
Script for benchmarking the AI workflows.
Cleans up existing tests/metadata in the reachability-metadata repo for the provided coordinates and
runs ai_workflows/add_new_library_support.py per library in the benchmark suite with the selected strategy and logs the metrics.

Usage:
  python3 benchmarks/benchmark_runner.py --benchmark-name benchmark-name \
  [--reachability-metadata-path /path/to/graalvm-reachability-metadata] [--metrics-repo-path /path/to/metrics-storage] [-v]
"""

import argparse
import json
import os
import sys
import shutil
import subprocess
import uuid
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BENCHMARK_SUITE_PATH = Path(os.path.join(REPO_ROOT, "benchmarks", "benchmark_suite.json"))
STRATEGIES_PATH = Path(os.path.join(REPO_ROOT, "strategies", "predefined_strategies.json"))
BENCHMARK_WORKTREE_DIRNAME = "forge_benchmark_worktrees"

from utility_scripts.repo_path_resolver import resolve_repo_roots
from utility_scripts.metrics_writer import count_metadata_entries
from git_scripts.common_git import build_ai_branch_name


def parse_args(argv):
    parser = argparse.ArgumentParser(
        prog="benchmark_runner.py",
        description="Run a benchmark suite of libraries using a predefined strategy and aggregate results.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument(
        "--benchmark-name",
        required=True,
        help="Benchmark name.",
    )
    parser.add_argument(
        "--reachability-metadata-path",
        help=(
            "Path to the graalvm-reachability-metadata repository to operate on. "
            "If omitted, the parent checkout of this Forge directory is used."
        ),
    )
    parser.add_argument(
        "--metrics-repo-path",
        help=(
            "Path where workflow metrics will be written (results.json). "
            "If omitted, the forge directory in the selected worktree is used."
        ),
    )
    parser.add_argument(
        "-v", "--verbose",
        action="store_true",
        help="Verbose logging and pass -v to the underlying workflow.",
    )
    parser.add_argument(
        "--keep-tests-without-dynamic-access",
        action="store_true",
        help=(
            "Forwarded to add_new_library_support.py. Keeps generated tests for dynamic-access strategies "
            "even when no dynamic-access coverage is added."
        ),
    )
    return parser.parse_args(argv)


def load_json_file(path: Path):
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def split_coords(coords: str):
    group, artifact, version = coords.split(":")
    return group, artifact, version


def safe_rmtree(path: Path):
    if path.exists():
        print("Removing directory: %s" % (path,))
        shutil.rmtree(path, ignore_errors=True)


def _sanitize_worktree_segment(value: str) -> str:
    """Return a filesystem-safe segment for benchmark worktree names."""
    return "".join(char if char.isalnum() or char in {"-", "_", "."} else "_" for char in value)


def create_benchmark_worktree(base_repo_path: Path, benchmark_name: str) -> Path:
    """Create a detached reachability-metadata worktree for a benchmark run."""
    worktrees_root = Path(os.path.join(REPO_ROOT, "local_repositories", BENCHMARK_WORKTREE_DIRNAME))
    worktrees_root.mkdir(parents=True, exist_ok=True)
    run_id = f"{_sanitize_worktree_segment(benchmark_name)}-{uuid.uuid4().hex[:12]}"
    worktree_path = worktrees_root / run_id
    subprocess.run(
        ["git", "worktree", "add", "--detach", str(worktree_path), "master"],
        check=True,
        cwd=base_repo_path,
    )
    return worktree_path


def remove_benchmark_worktree(base_repo_path: Path, worktree_path: Path | None) -> None:
    """Remove a benchmark worktree when one was created."""
    if worktree_path is None:
        return
    subprocess.run(
        ["git", "worktree", "remove", "--force", str(worktree_path)],
        check=False,
        cwd=base_repo_path,
    )


def resolve_benchmark_execution_paths(
        reachability_root: str,
        metrics_root: str,
        *,
        benchmark_name: str,
        explicit_reachability_path: str | None,
        explicit_metrics_repo_path: str | None,
) -> tuple[Path, Path, Path | None, Path]:
    """Resolve the reachability worktree and durable metrics root for a benchmark run."""
    base_reachability_path = Path(reachability_root).resolve()
    metrics_repo_dir = Path(metrics_root).resolve()
    created_worktree_path = None

    if explicit_metrics_repo_path is None:
        metrics_repo_dir = Path(REPO_ROOT).resolve()
        os.makedirs(os.path.join(metrics_repo_dir, "benchmark_run_metrics"), exist_ok=True)

    reachability_metadata_path = base_reachability_path
    if explicit_reachability_path is None:
        created_worktree_path = create_benchmark_worktree(base_reachability_path, benchmark_name)
        reachability_metadata_path = created_worktree_path.resolve()

    return reachability_metadata_path, metrics_repo_dir, created_worktree_path, base_reachability_path


def cleanup_repository_for_coords(repo_path: Path, coords: str) -> None:
    """
    Cleanup step for a given 'group:artifact:version' in the reachability repo:
      - Remove tests/src/<group>/<artifact>
      - Remove metadata/<group>/<artifact>
      - Best-effort remove group:artifact entries in metadata/index.json (ignore version)
      - Create parent dirs as needed; ignore if already clean
    """
    group, artifact, version = split_coords(coords)
    tests_dir = Path(os.path.join(repo_path, "tests", "src", group, artifact))
    metadata_dir = Path(os.path.join(repo_path, "metadata", group, artifact))
    index_path = Path(os.path.join(repo_path, "metadata", "index.json"))
    print(f"[Cleaning the subdirectories for [{coords}...]")
    # Remove specific versioned directories
    safe_rmtree(tests_dir)
    safe_rmtree(metadata_dir)

    if not index_path.exists():
        print("metadata/index.json not found, skipping index cleanup")
        return

    with index_path.open("r", encoding="utf-8") as f:
        index_data = json.load(f)

    changed = False
    expected_directory = f"{group}/{artifact}"

    def matches_directory(entry):
        directory = entry.get("directory")
        return directory == expected_directory

    filtered: List[Any] = []
    for entry in index_data:
        if matches_directory(entry):
            changed = True
        else:
            filtered.append(entry)
    index_data = filtered

    if changed:
        with index_path.open("w", encoding="utf-8") as f:
            json.dump(index_data, f, indent=2, ensure_ascii=False)
            f.write("\n")
    else:
        print("ERROR: No matching entries for directory '%s' found in %s" % (expected_directory, index_path))
        exit(1)


def compute_original_metadata_entries(repo_path: Path, coords: str):
    group, artifact, version = split_coords(coords)
    return count_metadata_entries(str(repo_path), group, artifact, version)


def run_workflow_for_coords(coords: str, reachability_metadata_path: str, metrics_repo_path: str,
                            strategy_name: str, verbose: bool, keep_tests_without_dynamic_access: bool,
                            ):
    """
    Execute ai_workflows/add_new_library_support.py for the given coordinates.
    On completion, validate that output/results.json exists and is valid JSON (array).
    """
    cmd = [sys.executable, os.path.join(REPO_ROOT, "ai_workflows", "add_new_library_support.py"), "--coordinates",
           coords]
    cmd.extend(["--reachability-metadata-path", reachability_metadata_path])
    cmd.extend(["--metrics-repo-path", metrics_repo_path])
    cmd.extend(["--strategy-name", strategy_name])

    # Ensure benchmark runs are recorded under benchmark_run_metrics in the metrics repo
    cmd.append("--benchmark-mode")
    if keep_tests_without_dynamic_access:
        cmd.append("--keep-tests-without-dynamic-access")
    if verbose:
        cmd.append("-v")

    try:
        subprocess.run(cmd)
    finally:
        subprocess.run(["git", "restore", "."], check=True, cwd=reachability_metadata_path)
        subprocess.run(["git", "switch", "master"], check=True, cwd=reachability_metadata_path)


def load_benchmark_configuration(benchmark_name: str) -> Dict[str, Any]:
    benchmarks = load_json_file(BENCHMARK_SUITE_PATH)

    if not isinstance(benchmarks, list):
        print(f"ERROR: Benchmark suite must be a list in the {BENCHMARK_SUITE_PATH}", file=sys.stderr)
        sys.exit(1)

    benchmark = None
    for b in benchmarks:
        if b.get("name") == benchmark_name:
            benchmark = b
            break
    if benchmark is None:
        print("ERROR: No benchmark named '%s'" % benchmark_name)
        exit(1)

    return benchmark


def initialize_benchmark_metrics_record(benchmark_name: str, libraries: List[str],
                                        reachability_metadata_path: Path, metrics_repo_dir: Path) -> Path:
    benchmark_metrics_path = Path(
        os.path.join(metrics_repo_dir, "benchmark_run_metrics", "add_new_library_support.json"))
    os.makedirs(benchmark_metrics_path.parent, exist_ok=True)

    benchmark_record = {
        "benchmark_name": benchmark_name,
        "timestamp": subprocess.check_output(["date", "-u", "+%Y-%m-%dT%H:%M:%SZ"], text=True).strip(),
        "metrics": [],
    }

    for coords in libraries:
        benchmark_record["metrics"].append({
            "library": coords,
            "status": "failure",
            "input_tokens_used": 0,
            "output_tokens_used": 0,
            "iterations": 0,
            "cost_usd": 0.0,
            "generated_loc": 0,
            "tested_library_loc": 0,
            "code_coverage_percent": 0.0,
            "metadata_entries": 0,
            "original_metadata_entry_count": compute_original_metadata_entries(reachability_metadata_path, coords),
        })

    existing = []
    if benchmark_metrics_path.is_file():
        with benchmark_metrics_path.open("r", encoding="utf-8") as f:
            data = json.load(f)
        if isinstance(data, list):
            existing = data

    existing.append(benchmark_record)
    with benchmark_metrics_path.open("w", encoding="utf-8") as f:
        json.dump(existing, f, indent=2, ensure_ascii=False)
        f.write("\n")

    return benchmark_metrics_path


def cleanup_and_commit_for_libraries(reachability_metadata_path: Path, libraries: List[str]) -> None:
    # Cleanup for each library
    for coords in libraries:
        cleanup_repository_for_coords(reachability_metadata_path, coords)

    # Commit checkpoint after cleanup
    subprocess.run(
        ["git", "add", "-A"],
        check=True,
    )
    subprocess.run(
        ["git", "commit", "-m", "[BENCHMARKING] Repository cleanup"],
        check=True,
    )


def run_workflow_for_libraries(libraries: List[str], reachability_metadata_path: Path,
                               metrics_repo_dir: Path, strategy_name: str, verbose: bool,
                               keep_tests_without_dynamic_access: bool) -> None:
    for coords in libraries:
        print(f"[Executing AI workflow for {coords}...]")
        run_workflow_for_coords(
            coords,
            str(reachability_metadata_path),
            str(metrics_repo_dir),
            strategy_name,
            verbose,
            keep_tests_without_dynamic_access,
        )


def main(argv: Optional[List[str]] = None):
    args = parse_args(argv)

    benchmark_name = args.benchmark_name
    reachability_metadata_path_arg = args.reachability_metadata_path
    metrics_repo_path_arg = args.metrics_repo_path

    reachability_root, metrics_root = resolve_repo_roots(
        reachability_metadata_path_arg,
        metrics_repo_path_arg,
    )

    reachability_metadata_path, metrics_repo_dir, created_worktree_path, base_reachability_path = (
        resolve_benchmark_execution_paths(
            reachability_root,
            metrics_root,
            benchmark_name=benchmark_name,
            explicit_reachability_path=reachability_metadata_path_arg,
            explicit_metrics_repo_path=metrics_repo_path_arg,
        )
    )
    if not reachability_metadata_path.exists():
        print(
            f"ERROR: graalvm-reachability-metadata path does not exist: {os.path.relpath(reachability_metadata_path)}",
            file=sys.stderr,
        )
        sys.exit(1)
    if not metrics_repo_dir.exists():
        print(
            f"ERROR: metadata-forge-metrics path does not exist: {os.path.relpath(metrics_repo_dir)}",
            file=sys.stderr,
        )
        sys.exit(1)
    verbose = args.verbose
    keep_tests_without_dynamic_access = args.keep_tests_without_dynamic_access

    benchmark = load_benchmark_configuration(benchmark_name)

    libraries = benchmark.get("libraries")
    strategy_name = benchmark.get("strategy")
    os.chdir(reachability_metadata_path)

    print("[Cleaning the graalvm_reachability-metadata...]")
    # Stash any existing changes in the target repo to avoid interfering with cleanup/commits.
    subprocess.run(
        ["git", "stash", "push", "-u", "-m", "[BENCHMARKING] Pre-cleanup worktree stash"],
        check=True,
    )

    subprocess.run(["git", "switch", "master"], check=True)
    # Save current commit hash so we can restore the repository state after the benchmark run.
    original_commit = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()

    initialize_benchmark_metrics_record(benchmark_name, libraries, reachability_metadata_path, metrics_repo_dir)
    try:
        cleanup_and_commit_for_libraries(reachability_metadata_path, libraries)

        run_workflow_for_libraries(
            libraries,
            reachability_metadata_path,
            metrics_repo_dir,
            strategy_name,
            verbose,
            keep_tests_without_dynamic_access,
        )
    finally:
        print(f"[Restoring repository to original state...]")
        subprocess.run([
            "git",
            "reset",
            "--hard",
            original_commit,
        ], check=True)

        # Cleans all the untracked files
        subprocess.run([
            "git",
            "clean",
            "-fd"
        ], check=True)

        # Attempt to restore any stashed changes back on top of the original commit.
        subprocess.run([
            "git",
            "stash",
            "pop",
        ], check=False)

        for coords in libraries:
            group, artifact, library_version = split_coords(coords)
            new_branch = build_ai_branch_name(f"add-lib-support-{group}-{artifact}-{library_version}")
            subprocess.run([
                "git",
                "branch",
                "-D",
                new_branch,
            ], check=False)
        os.chdir(REPO_ROOT)
        remove_benchmark_worktree(base_reachability_path, created_worktree_path)


if __name__ == "__main__":
    sys.exit(main())
