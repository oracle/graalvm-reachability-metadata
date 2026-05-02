# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Workflow entry point for improving dynamic-access coverage on an existing library.

This operates on an already-passing test suite and
focuses solely on generating new tests to improve dynamic-access coverage.

Usage:
  python3 ai_workflows/improve_library_coverage.py \
    --coordinates group:artifact:version \
    [--reachability-metadata-path /path/to/graalvm-reachability-metadata] \
    [--metrics-repo-path /path/to/metrics-storage] \
    [--docs-path /path/to/docs] \
    [--strategy-name "coverage_only_pi_gpt-5.5"] \
    [-v]
"""

import argparse
import json
import os
import subprocess
import sys

import ai_workflows.agents  # noqa: F401 - triggers agent registration
import ai_workflows.workflow_strategies  # noqa: F401 — triggers strategy registration
from ai_workflows.agents import Agent
from ai_workflows.workflow_strategies.workflow_strategy import (
    RUN_STATUS_FAILURE,
    RUN_STATUS_CHUNK_READY,
    RUN_STATUS_SUCCESS,
    SUCCESS_WITH_INTERVENTION_STATUS,
    WorkflowStrategy,
)
from git_scripts.common_git import build_ai_branch_name, delete_remote_branch_if_exists, ensure_gh_authenticated, load_library_stats
from utility_scripts import metrics_writer
from utility_scripts.large_library_progress import LargeLibraryProgressState
from utility_scripts.metrics_writer import count_metadata_entries, count_test_only_metadata_entries, create_failure_run_metrics_output
from utility_scripts.repo_path_resolver import add_in_metadata_repo_argument, resolve_repo_roots
from utility_scripts.schema_validator import validate_run_metrics
from utility_scripts.source_context import (
    normalize_source_context_types,
    populate_artifact_urls,
    prepare_source_contexts,
    resolve_test_source_layout,
)
from utility_scripts.stage_logger import log_stage
from utility_scripts.strategy_loader import require_strategy_by_name
from utility_scripts.workflow_setup import resolve_graalvm_java_home, validate_repo_paths

DEFAULT_MODEL_NAME = "oca/gpt-5.5"
DEFAULT_STRATEGY_NAME = "coverage_only_pi_gpt-5.5"
METRICS_TASK_TYPE = "improve_library_coverage"
BASELINE_STATS_FILENAME = ".baseline-stats.json"


def list_all_files(directory_path: str) -> list[str]:
    """Recursively list all file paths in the given directory."""
    files: list[str] = []
    if not directory_path or not os.path.isdir(directory_path):
        return files
    for root_dir, _, file_names in os.walk(directory_path):
        for file_name in file_names:
            files.append(os.path.join(root_dir, file_name))
    return files


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="improve_library_coverage.py",
        description="Improve dynamic-access coverage for an existing library version.",
        formatter_class=argparse.RawTextHelpFormatter,
    )
    parser.add_argument(
        "--coordinates",
        required=True,
        help="Maven coordinates Group:Artifact:Version for the target library",
    )
    parser.add_argument(
        "--reachability-metadata-path",
        help=(
            "Path to the graalvm-reachability-metadata repository. "
            "If omitted, the parent checkout of this Forge directory is used."
        ),
    )
    parser.add_argument(
        "--metrics-repo-path",
        help=(
            "Path where workflow metrics are written. "
            "If omitted, the forge directory in the selected worktree is used."
        ),
    )
    add_in_metadata_repo_argument(parser)
    parser.add_argument(
        "--strategy-name",
        dest="strategy_name",
        default=DEFAULT_STRATEGY_NAME,
        metavar="NAME",
        help=f"Strategy name from strategies/predefined_strategies.json (default: {DEFAULT_STRATEGY_NAME})",
    )
    parser.add_argument(
        "--docs-path",
        default=None,
        help="Optional path with additional read-only docs/sources for agent context",
    )
    parser.add_argument("-v", "--verbose", action="store_true", help="enable verbose mode for the configured agent")
    parser.add_argument(
        "--large-library-series",
        action="store_true",
        help="Enable resumable large-library chunking for dynamic-access workflows.",
    )
    parser.add_argument(
        "--chunk-class-limit",
        type=int,
        default=0,
        help="Stop a large-library part after this many resolved, skipped, or exhausted classes. 0 disables it.",
    )
    parser.add_argument(
        "--chunk-call-limit",
        type=int,
        default=0,
        help="Stop a large-library part after this many newly covered calls. 0 disables it.",
    )
    parser.add_argument(
        "--resume-artifact",
        help="Path to a large-library progress state JSON artifact to resume from.",
    )
    parser.add_argument(
        "--issue-number",
        type=int,
        help="GitHub issue number for durable large-library progress state.",
    )
    return parser


def parse_flags(argv_list: list[str]):
    """Parse CLI flags and return the core configuration tuple."""
    flags = build_parser().parse_args(argv_list)
    try:
        group, artifact, version = flags.coordinates.split(":")
    except ValueError:
        print(
            f"ERROR: Invalid coordinates format: {flags.coordinates}. Expected Group:Artifact:Version",
            file=sys.stderr,
        )
        sys.exit(1)
    return (
        group,
        artifact,
        version,
        flags.docs_path,
        flags.strategy_name,
        flags.verbose,
        flags.reachability_metadata_path,
        flags.metrics_repo_path,
        flags.in_metadata_repo,
        flags.large_library_series,
        flags.chunk_class_limit,
        flags.chunk_call_limit,
        flags.resume_artifact,
        flags.issue_number,
    )


def resolve_repo_paths(
        explicit_repo_path: str | None,
        explicit_metrics_repo_path: str | None,
        in_metadata_repo: bool = True,
) -> tuple[str, str, str]:
    """Resolve repository paths for code and metrics outputs."""
    resolved_reachability_repo, resolved_metrics_repo = resolve_repo_roots(
        explicit_repo_path,
        explicit_metrics_repo_path,
        in_metadata_repo=in_metadata_repo,
    )
    resolved_metrics_dir = os.path.join(resolved_metrics_repo, "script_run_metrics")
    os.makedirs(resolved_metrics_dir, exist_ok=True)
    return resolved_reachability_repo, resolved_metrics_dir, resolved_metrics_repo


def resolve_metrics_json(run_metrics: dict, metrics_repo_dir: str, metrics_repo_root: str | None) -> str:
    """Resolve the metrics JSON path for the current execution."""
    in_repo_root = metrics_writer.in_metadata_repo_metrics_root(metrics_repo_root)
    if in_repo_root:
        return metrics_writer.execution_metrics_path(in_repo_root, run_metrics)
    return os.path.join(metrics_repo_dir, f"{METRICS_TASK_TYPE}.json")


def write_metrics(run_metrics: dict, metrics_repo_dir: str, metrics_repo_root: str | None) -> None:
    """Append metrics to JSON, write pending metrics, and validate schema."""
    metrics_json = resolve_metrics_json(run_metrics, metrics_repo_dir, metrics_repo_root)
    in_repo_root = metrics_writer.in_metadata_repo_metrics_root(metrics_repo_root)
    if in_repo_root:
        written = metrics_writer.append_execution_metrics(in_repo_root, run_metrics, METRICS_TASK_TYPE)
        if written != metrics_json:
            raise ValueError(f"ERROR: Resolved metrics path {metrics_json} does not match written path {written}")
    else:
        metrics_writer.append_run_metrics(run_metrics, metrics_json)
    if metrics_repo_root:
        metrics_writer.write_pending_metrics(metrics_repo_root, run_metrics)
    validate_run_metrics(metrics_json)


def main(argv=None) -> int:
    (
        group,
        artifact,
        version,
        docs_path,
        strategy_name,
        is_verbose,
        explicit_repo_path,
        explicit_metrics_repo_path,
        in_metadata_repo,
        large_library_series,
        chunk_class_limit,
        chunk_call_limit,
        resume_artifact,
        issue_number,
    ) = parse_flags(argv if argv is not None else sys.argv[1:])

    library = f"{group}:{artifact}:{version}"
    strategy = require_strategy_by_name(strategy_name)
    reachability_repo_path, metrics_repo_dir, metrics_repo_root = resolve_repo_paths(
        explicit_repo_path,
        explicit_metrics_repo_path,
        in_metadata_repo=in_metadata_repo,
    )
    ensure_gh_authenticated()
    resolve_graalvm_java_home()
    validate_repo_paths(reachability_repo_path, metrics_repo_dir)
    os.chdir(reachability_repo_path)

    log_stage("setup", f"Selected strategy: {strategy_name}")
    model_name = strategy.get("model") or DEFAULT_MODEL_NAME
    large_library_state = None
    large_library_state_path = None
    if resume_artifact:
        large_library_state = LargeLibraryProgressState.load(resume_artifact)
        large_library_state_path = large_library_state.default_path(metrics_repo_root)
    elif large_library_series:
        large_library_state = LargeLibraryProgressState.create(
            coordinate=library,
            issue_number=issue_number,
            request_label="library-update-request",
            strategy_name=strategy_name,
        )
        large_library_state_path = large_library_state.default_path(metrics_repo_root)

    # Create feature branch
    new_branch = build_ai_branch_name(f"improve-coverage-{group}-{artifact}-{version}")
    delete_remote_branch_if_exists(new_branch)
    subprocess.run(["git", "switch", "-C", new_branch], check=True)

    # Commit existing state as checkpoint
    tests_dir = os.path.join(reachability_repo_path, "tests", "src", group, artifact, version)
    index_json = os.path.join(reachability_repo_path, "metadata", group, artifact, "index.json")
    subprocess.run(["git", "add", tests_dir, index_json], check=False)
    subprocess.run(
        ["git", "commit", "--allow-empty", "-m", f"Checkpoint for coverage improvement of {library}"],
        capture_output=True, text=True, check=False,
    )
    checkpoint_commit = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
    log_stage("setup", f"Checkpoint commit: {checkpoint_commit}")

    # Snapshot baseline stats and metadata entry counts before the workflow modifies them
    baseline_stats = load_library_stats(reachability_repo_path, library)
    baseline_metadata_entries = count_metadata_entries(reachability_repo_path, group, artifact, version)
    baseline_test_only_entries = count_test_only_metadata_entries(reachability_repo_path, group, artifact, version)
    baseline_snapshot = {
        "stats": baseline_stats,
        "metadata_entries": baseline_metadata_entries,
        "test_only_metadata_entries": baseline_test_only_entries,
    }
    if large_library_state is not None and large_library_state.baseline_stats is None:
        large_library_state.baseline_stats = baseline_stats
        large_library_state.baseline_metadata_entries = baseline_metadata_entries
        large_library_state.baseline_test_only_metadata_entries = baseline_test_only_entries
        large_library_state.save(large_library_state_path)
    baseline_stats_path = os.path.join(tests_dir, BASELINE_STATS_FILENAME)
    with open(baseline_stats_path, "w", encoding="utf-8") as f:
        json.dump(baseline_snapshot, f, indent=2)
    log_stage("setup", f"Saved baseline snapshot to {BASELINE_STATS_FILENAME}")

    populate_artifact_urls(reachability_repo_path, library)

    source_context_types = normalize_source_context_types(strategy.get("parameters", {}).get("source-context-types"))
    prepared_source_context = prepare_source_contexts(
        repo_root=os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
        reachability_repo_path=reachability_repo_path,
        coordinate=library,
        source_context_types=source_context_types,
    )

    build_gradle_file = os.path.join(tests_dir, "build.gradle")
    test_source_layout = resolve_test_source_layout(reachability_repo_path, library, tests_dir)

    workflow_name = strategy.get("workflow")
    if not workflow_name:
        print("ERROR: Strategy is missing required field: workflow", file=sys.stderr)
        return 1
    StrategyClass = WorkflowStrategy.get_class(workflow_name)
    strategy_obj = StrategyClass(
        strategy_obj=strategy,
        reachability_repo_path=reachability_repo_path,
        library=library,
        build_gradle_file=build_gradle_file,
        source_context_overview=prepared_source_context.to_prompt_overview(),
        source_context_available=prepared_source_context.is_available,
        source_context_files=prepared_source_context.read_only_files,
        test_language=test_source_layout.language,
        test_language_display_name=test_source_layout.display_language,
        test_source_dir_name=test_source_layout.source_dir_name,
        large_library_progress_state=large_library_state,
        large_library_progress_state_path=large_library_state_path,
        chunk_class_limit=chunk_class_limit,
        chunk_call_limit=chunk_call_limit,
    )

    # Initialize agent
    strategy_agent = strategy.get("agent")
    if not strategy_agent:
        print("ERROR: Strategy is missing required field: agent", file=sys.stderr)
        return 1

    editable_files = list_all_files(test_source_layout.source_root)
    editable_files.append(build_gradle_file)
    read_only_files = list_all_files(docs_path) if docs_path else []
    read_only_files.extend(prepared_source_context.read_only_files)

    log_stage("init-agent", f"Initializing {strategy_agent} agent")
    agent_class = Agent.get_class(strategy_agent)
    agent = agent_class(
        model_name=model_name,
        editable_files=editable_files,
        read_only_files=read_only_files,
        working_dir=reachability_repo_path,
        provider=strategy.get("provider"),
        library=library,
        task_type="improve-library-coverage",
        verbose=is_verbose,
        mcps=strategy.get("mcps", []),
    )

    workflow_status, iterations = strategy_obj.run(agent=agent)

    if workflow_status in {RUN_STATUS_SUCCESS, RUN_STATUS_CHUNK_READY}:
        finalize_status, _ = strategy_obj._finalize_successful_iteration()
        if finalize_status in {RUN_STATUS_SUCCESS, SUCCESS_WITH_INTERVENTION_STATUS} and workflow_status == RUN_STATUS_CHUNK_READY:
            workflow_status = RUN_STATUS_CHUNK_READY
        else:
            workflow_status = finalize_status

    ending_commit = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()

    if workflow_status not in {RUN_STATUS_SUCCESS, SUCCESS_WITH_INTERVENTION_STATUS, RUN_STATUS_CHUNK_READY}:
        log_stage("status", "Coverage improvement failed")
        subprocess.run(["git", "reset", "--hard", checkpoint_commit], check=True)
        ending_commit = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
        run_metrics = create_failure_run_metrics_output(
            package=group,
            artifact=artifact,
            library_version=version,
            agent=agent,
            model_name=model_name,
            global_iterations=iterations,
            strategy_name=strategy_name,
            starting_commit=checkpoint_commit,
            ending_commit=ending_commit,
        )
    else:
        if workflow_status == SUCCESS_WITH_INTERVENTION_STATUS:
            log_stage("status", "Coverage improvement produced PR-eligible post-generation intervention output")
        else:
            log_stage("status", "Coverage improvement succeeded")
        run_metrics = metrics_writer.create_run_metrics_output_json(
            repo_path=reachability_repo_path,
            package=group,
            artifact=artifact,
            library_version=version,
            agent=agent,
            model_name=model_name,
            global_iterations=iterations,
            tests_root=test_source_layout.source_root,
            strategy_name=strategy_name,
            status=workflow_status,
            starting_commit=checkpoint_commit,
            ending_commit=ending_commit,
            post_generation_intervention=strategy_obj.post_generation_intervention,
        )

    write_metrics(run_metrics, metrics_repo_dir, metrics_repo_root)
    return 0 if workflow_status in {RUN_STATUS_SUCCESS, SUCCESS_WITH_INTERVENTION_STATUS, RUN_STATUS_CHUNK_READY} else 1


if __name__ == "__main__":
    if any(a in ("-h", "--help") for a in sys.argv[1:]):
        build_parser().print_help()
        sys.exit(0)
    sys.exit(main())
