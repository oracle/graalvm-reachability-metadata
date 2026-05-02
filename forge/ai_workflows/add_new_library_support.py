# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""
Usage:
  python3 ai_workflows/add_new_library_support.py \
    --coordinates group:artifact:version \
    [--reachability-metadata-path /path/to/graalvm-reachability-metadata] \
    [--metrics-repo-path /path/to/metrics-storage] \
    [--docs-path /path/to/docs] \
    [--strategy-name "basic_iterative"] \
    [--benchmark-mode] \
    [-v]
"""

import subprocess
import argparse
import json
import os
import sys

import ai_workflows.agents  # noqa: F401 - triggers agent registration
import ai_workflows.workflow_strategies  # noqa: F401 — triggers strategy registration
from ai_workflows.agents import Agent
from ai_workflows.workflow_strategies.workflow_strategy import (
    RUN_STATUS_FAILURE,
    RUN_STATUS_CHUNK_READY,
    RUN_STATUS_SUCCESS,
    SUCCESS_WITH_INTERVENTION_STATUS,
)
from ai_workflows.workflow_strategies.workflow_strategy import WorkflowStrategy
from git_scripts.common_git import build_ai_branch_name, delete_remote_branch_if_exists
from utility_scripts import metrics_writer
from utility_scripts.large_library_progress import resolve_workflow_progress_state
from utility_scripts.metadata_index import is_not_for_native_image, write_not_for_native_image_marker
from utility_scripts.native_image_artifact import evaluate_native_image_eligibility
from utility_scripts.repo_path_resolver import add_in_metadata_repo_argument, resolve_repo_roots
from utility_scripts.schema_validator import validate_run_metrics, validate_benchmark_run_metrics
from utility_scripts.source_context import (
    discover_artifact_metadata,
    normalize_source_context_types,
    populate_artifact_urls,
    prepare_source_contexts,
    resolve_test_source_layout,
)
from utility_scripts.stage_logger import log_stage
from utility_scripts.test_quality_checks import cleanup_scaffold_placeholder_tests, format_placeholder_occurrence
from utility_scripts.metrics_writer import create_failure_run_metrics_output
from utility_scripts.strategy_loader import require_strategy_by_name
from utility_scripts.workflow_setup import resolve_graalvm_java_home, validate_repo_paths

DEFAULT_MODEL_NAME = "oca/gpt-5.4"


class ScaffoldError(RuntimeError):
    """Raised when the Gradle scaffold task fails unexpectedly."""


def get_repo_root():
    return os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def list_all_files(directory_path):
    """Recursively list all file paths in the given directory."""
    files = []
    if not directory_path or not os.path.isdir(directory_path):
        return files

    for root_dir, _, file_names in os.walk(directory_path):
        for file_name in file_names:
            files.append(os.path.join(root_dir, file_name))
    return files


def build_parser():
    parser = argparse.ArgumentParser(
        prog="add_new_library_support.py",
        description=(
            "Iteratively generate and validate unit tests for a given library using the configured agent.\n\n"
        ),
        epilog=(
            "Example:\n"
            "  python3 ai_workflows/add_new_library_support.py \\\n"
            "  --coordinates com.example:lib:1.2.3 \\\n"
            "  --reachability-metadata-path /path/to/reachability-metadata \\\n"
            "  --metrics-repo-path /path/to/metrics-storage \\\n"
            "  --docs-path /optional/path/to/docs\n\n"
        ),
        formatter_class=argparse.RawTextHelpFormatter,
    )
    # Core workflow configuration
    parser.add_argument(
        "--coordinates",
        required=True,
        help="Maven coordinates Group:Artifact:Version for the target library",
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
    add_in_metadata_repo_argument(parser)
    parser.add_argument(
        "--docs-path",
        default=None,
        help="Optional path with additional read-only docs/sources for agent context",
    )

    parser.add_argument(
        "--benchmark-mode",
        action="store_true",
        help=(
            "When set, metrics will be written under 'benchmark_run_metrics' instead of 'script_run_metrics'"
        ),
    )

    parser.add_argument(
        "-v", "--verbose",
        action="store_true",
        help="enable verbose mode for the configured agent"
    )

    parser.add_argument(
        "--strategy-name",
        dest="strategy_name",
        metavar="NAME",
        default="basic_iterative_pi_gpt-5.4",
        help="select strategy by name from strategies/predefined_strategies.json",
    )
    parser.add_argument(
        "--keep-tests-without-dynamic-access",
        action="store_true",
        help=(
            "When using dynamic-access strategies, keep generated tests even when they do not improve "
            "dynamic-access coverage."
        ),
    )
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


def parse_flags(argv_list):
    """Parse CLI flags and return the core configuration tuple."""
    parser = build_parser()
    flags = parser.parse_args(argv_list)

    library_coordinates = flags.coordinates
    docs_path = flags.docs_path

    try:
        group, artifact, library_version = library_coordinates.split(":")
    except ValueError:
        print(
            f"ERROR: Invalid coordinates format: {library_coordinates}. Expected Group:Artifact:Version",
            file=sys.stderr,
        )
        sys.exit(1)

    return (
        library_coordinates,
        group,
        artifact,
        library_version,
        docs_path,
        flags.strategy_name,
        flags.verbose,
        flags.reachability_metadata_path,
        flags.metrics_repo_path,
        flags.benchmark_mode,
        flags.keep_tests_without_dynamic_access,
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
        benchmark_mode: bool,
        in_metadata_repo: bool = True,
):
    """Resolve paths for reachability-metadata and metrics-metadata-forge repositories."""

    res_reachability_repo, res_metrics_repo = resolve_repo_roots(
        explicit_repo_path,
        explicit_metrics_repo_path,
        in_metadata_repo=in_metadata_repo,
    )

    subdir_name = "benchmark_run_metrics" if benchmark_mode else "script_run_metrics"
    resolved_metrics_repo = os.path.join(res_metrics_repo, subdir_name)
    os.makedirs(resolved_metrics_repo, exist_ok=True)
    return res_reachability_repo, resolved_metrics_repo, res_metrics_repo


def resolve_add_new_library_support_metrics_json(
        run_metrics: dict,
        metrics_repo_dir: str,
        metrics_repo_root: str | None,
        is_benchmark_mode: bool,
) -> str:
    """Resolve the metrics JSON path for the current execution mode."""
    if is_benchmark_mode:
        return os.path.join(metrics_repo_dir, "add_new_library_support.json")

    in_repo_root = metrics_writer.in_metadata_repo_metrics_root(metrics_repo_root)
    if in_repo_root:
        return metrics_writer.execution_metrics_path(in_repo_root, run_metrics)

    return os.path.join(metrics_repo_dir, "add_new_library_support.json")


def create_feature_branch_for_library(group, artifact, library_version):
    """
    Reset the feature branch for the given coordinates to the current detached HEAD.
    Branch name is in the following format:
    ai/<login>/add-lib-support-<group>-<artifact>-<version>
    """

    new_branch = build_ai_branch_name(f"add-lib-support-{group}-{artifact}-{library_version}")
    delete_remote_branch_if_exists(new_branch)
    subprocess.run(
        ["git", "switch", "-C", new_branch],
        check=True,
    )


def _metadata_already_exists(scaffold_proc: subprocess.CompletedProcess) -> bool:
    """Return True when Gradle reports that metadata for the library already exists."""
    output = "\n".join(
        value for value in (scaffold_proc.stdout, scaffold_proc.stderr) if value
    )
    return "already exists" in output and "Use --force to overwrite existing metadata" in output


def run_scaffold(library: str) -> bool:
    log_stage("scaffold", f"Running scaffold for {library}")
    # Run scaffold for the given coordinates
    scaffold_proc = subprocess.run(
        f"./gradlew scaffold --coordinates={library} --rerun-tasks",
        shell=True,
        capture_output=True,
        text=True
    )
    if scaffold_proc.returncode == 0:
        return True
    if _metadata_already_exists(scaffold_proc):
        return False
    raise ScaffoldError(scaffold_proc.stderr or scaffold_proc.stdout or "Gradle scaffold task failed")


def init_agent(
        strategy,
        working_dir,
        editable_files,
        read_only_files,
        library=None,
        task_type="session",
        verbose=False,
        model_name=DEFAULT_MODEL_NAME,
):
    """Initialize the configured agent implementation."""
    strategy_agent = strategy.get("agent")
    if not strategy_agent:
        print("ERROR: Strategy is missing required field: agent", file=sys.stderr)
        sys.exit(1)

    log_stage("init-agent", f"Initializing {strategy_agent} agent")
    agent_class = Agent.get_class(strategy_agent)
    return agent_class(
        model_name=model_name,
        editable_files=editable_files,
        read_only_files=read_only_files,
        working_dir=working_dir,
        provider=strategy.get("provider"),
        library=library,
        task_type=task_type,
        verbose=verbose,
        mcps=strategy.get("mcps", []),
    )


def _build_benchmark_metrics_entry(run_metrics: dict) -> dict:
    """Return a benchmark metrics entry without workflow artifact paths."""
    benchmark_entry = dict(run_metrics)
    benchmark_entry.pop("artifacts", None)
    return benchmark_entry


def write_add_new_library_support_metrics(run_metrics, metrics_json, is_benchmark_mode, package, artifact,
                                          library_version, metrics_repo_root=None):
    """Write or update add_new_library_support metrics depending on the execution mode."""

    # When running in benchmark mode, update the last benchmark record instead of appending to script_run_metrics.
    if is_benchmark_mode:
        if not os.path.isfile(metrics_json):
            print(f"ERROR: Benchmark metrics file not found: {metrics_json}")
            sys.exit(1)

        with open(metrics_json, "r", encoding="utf-8") as f:
            data = json.load(f)

        benchmark_obj = data[-1]
        metrics_array = benchmark_obj.get("metrics")
        library_id = f"{package}:{artifact}:{library_version}"

        updated = False
        for index, item in enumerate(metrics_array):
            if item.get("library") == library_id:
                metrics_array[index] = _build_benchmark_metrics_entry(run_metrics)
                updated = True
                break

        if not updated:
            print(f"ERROR: No benchmark metrics entry found for library {library_id}.")
            sys.exit(1)

        with open(metrics_json, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
            f.write("\n")
    else:
        in_repo_root = metrics_writer.in_metadata_repo_metrics_root(metrics_repo_root)
        if in_repo_root:
            written_metrics_json = metrics_writer.append_execution_metrics(
                in_repo_root,
                run_metrics,
                "add_new_library_support",
            )
            if written_metrics_json != metrics_json:
                raise ValueError(
                    f"ERROR: Resolved metrics path {metrics_json} does not match written path {written_metrics_json}"
                )
        else:
            metrics_writer.append_run_metrics(run_metrics, metrics_json)
        if metrics_repo_root:
            metrics_writer.write_pending_metrics(metrics_repo_root, run_metrics)

    log_stage("schema-validation", "Validating schema")
    if not is_benchmark_mode:
        validate_run_metrics(metrics_json)
    elif is_benchmark_mode:
        validate_benchmark_run_metrics(metrics_json)
    log_stage("schema-validation", "Schema validated")



def main(argv=None):
    (
        library,
        package,
        artifact,
        library_version,
        docs_path,
        strategy_name,
        is_verbose,
        explicit_repo_path,
        explicit_metrics_repo_path,
        is_benchmark_mode,
        keep_tests_without_dynamic_access,
        in_metadata_repo,
        large_library_series,
        chunk_class_limit,
        chunk_call_limit,
        resume_artifact,
        issue_number,
    ) = parse_flags(argv if argv is not None else sys.argv[1:])

    strategy = require_strategy_by_name(strategy_name)

    # Resolve repository locations (possibly cloning)
    reachability_repo_path, metrics_repo_dir, metrics_repo_root = resolve_repo_paths(
        explicit_repo_path,
        explicit_metrics_repo_path,
        is_benchmark_mode,
        in_metadata_repo=in_metadata_repo,
    )
    resolve_graalvm_java_home()

    log_stage("setup", f"Selected strategy: {strategy_name}")
    model_name = strategy.get("model") or DEFAULT_MODEL_NAME
    workflow_name = strategy.get("workflow")
    if not workflow_name:
        print("ERROR: Strategy is missing required field: workflow", file=sys.stderr)
        sys.exit(1)
    StrategyClass = WorkflowStrategy.get_class(workflow_name)
    large_library_state, large_library_state_path = resolve_workflow_progress_state(
        metrics_repo_root=metrics_repo_root,
        issue_number=issue_number,
        coordinate=library,
        request_label="library-new-request",
        strategy_name=strategy_name,
        large_library_series=large_library_series,
        resume_artifact=resume_artifact,
    )

    validate_repo_paths(reachability_repo_path, metrics_repo_dir)

    os.chdir(reachability_repo_path)
    create_feature_branch_for_library(package, artifact, library_version)
    if is_not_for_native_image(reachability_repo_path, package, artifact):
        log_stage("native-image-eligibility", f"{package}:{artifact} is already marked not-for-native-image")
        return 0
    try:
        discover_artifact_metadata(reachability_repo_path, library)
        eligibility = evaluate_native_image_eligibility(reachability_repo_path, library)
        if eligibility.not_for_native_image:
            marker_path = write_not_for_native_image_marker(
                reachability_repo_path,
                package,
                artifact,
                eligibility.reason or "Artifact is not applicable to GraalVM Native Image metadata.",
                eligibility.replacement,
            )
            log_stage(
                "native-image-eligibility",
                f"Marked {package}:{artifact} as not-for-native-image in {os.path.relpath(marker_path, reachability_repo_path)}",
            )
            return 0
        run_scaffold(library)
    except ScaffoldError as exc:
        print(f"ERROR: Gradle 'scaffold' task failed for coordinates: {library}", file=sys.stderr)
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1
    populate_artifact_urls(reachability_repo_path, library)
    source_context_types = normalize_source_context_types(strategy.get("parameters", {}).get("source-context-types"))
    prepared_source_context = prepare_source_contexts(
        repo_root=get_repo_root(),
        reachability_repo_path=reachability_repo_path,
        coordinate=library,
        source_context_types=source_context_types,
    )
    module_dir = os.path.join(reachability_repo_path, "tests", "src", package, artifact, library_version)
    test_source_layout = resolve_test_source_layout(reachability_repo_path, library, module_dir)

    strategy_obj = StrategyClass(
        strategy_obj=strategy,
        library=library,
        reachability_repo_path=reachability_repo_path,
        source_context_overview=prepared_source_context.to_prompt_overview(),
        source_context_available=prepared_source_context.is_available,
        source_context_files=prepared_source_context.read_only_files,
        keep_tests_without_dynamic_access=keep_tests_without_dynamic_access,
        large_library_progress_state=large_library_state,
        large_library_progress_state_path=large_library_state_path,
        large_library_issue_number=issue_number,
        large_library_request_label="library-new-request",
        large_library_metrics_repo_root=metrics_repo_root,
        large_library_strategy_name=strategy_name,
        chunk_class_limit=chunk_class_limit,
        chunk_call_limit=chunk_call_limit,
        test_language=test_source_layout.language,
        test_language_display_name=test_source_layout.display_language,
        test_source_dir_name=test_source_layout.source_dir_name,
    )

    # Add generated files to git and commit; record commit hash (do not use it)
    directory_path = module_dir
    index_json_path = os.path.join(reachability_repo_path, "metadata", package, artifact, "index.json")
    subprocess.run(["git", "add", directory_path, index_json_path], check=False)
    subprocess.run(["git", "commit", "-m", f"Scaffold {library}"], check=False, capture_output=True, text=True)
    checkpoint_commit_hash = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
    if large_library_state is not None and not large_library_state.scaffold_commit:
        large_library_state.scaffold_commit = checkpoint_commit_hash
        large_library_state.save(large_library_state_path)
    log_stage("scaffold", f"Committed scaffold at {checkpoint_commit_hash}")

    editable_files = list_all_files(test_source_layout.source_root)
    build_gradle_file = os.path.join(
        reachability_repo_path,
        "tests",
        "src",
        package,
        artifact,
        library_version,
        "build.gradle",
    )

    editable_files = editable_files + [build_gradle_file]
    read_only_files = list_all_files(docs_path) if docs_path else []
    read_only_files.extend(prepared_source_context.read_only_files)
    agent = init_agent(
        strategy=strategy,
        working_dir=reachability_repo_path,
        editable_files=editable_files,
        read_only_files=read_only_files,
        library=library,
        task_type="add-new-library-support",
        verbose=is_verbose,
        model_name=model_name,
    )

    # Requires the graphify skill to be installed for the selected agent backend.
    # Install docs: https://github.com/safishamsi/graphify#install
    if strategy.get("parameters", {}).get("graphify-context"):
        graphify_dirs = [
            os.path.join(a.local_dir, "extracted")
            for a in prepared_source_context.artifacts
            if a.available and a.local_dir and os.path.isdir(os.path.join(a.local_dir, "extracted"))
        ]
        agent.graphify(graphify_dirs)

    workflow_status, global_iterations, unittest_number = strategy_obj.run(
        agent=agent,
        checkpoint_commit_hash=checkpoint_commit_hash,
    )

    scaffold_placeholder_quality_gate_failed = False
    if workflow_status in {RUN_STATUS_SUCCESS, RUN_STATUS_CHUNK_READY}:
        placeholder_cleanup = cleanup_scaffold_placeholder_tests(
            test_source_layout.source_root,
            reachability_repo_path,
            checkpoint_commit_hash,
        )
        for removed_file in placeholder_cleanup.removed_files:
            log_stage(
                "scaffold-cleanup",
                f"Removed scaffold placeholder test {os.path.relpath(removed_file, reachability_repo_path)}",
            )
        if placeholder_cleanup.remaining_placeholders:
            for occurrence in placeholder_cleanup.remaining_placeholders:
                log_stage(
                    "scaffold-cleanup",
                    f"WARNING: Scaffold placeholder test remains in generated sources: "
                    f"{format_placeholder_occurrence(occurrence, reachability_repo_path)}",
                )

    if workflow_status in {RUN_STATUS_SUCCESS, RUN_STATUS_CHUNK_READY}:
        if is_benchmark_mode:
            log_stage("generate-metadata", f"Benchmark mode: running generateMetadata and generateLibraryStats for {library}")
            if not strategy_obj._run_gradle_command([
                "./gradlew",
                "generateMetadata",
                f"-Pcoordinates={library}",
                "--agentAllowedPackages=fromJar",
            ]):
                workflow_status = RUN_STATUS_FAILURE
            elif not strategy_obj._run_gradle_command([
                "./gradlew",
                "generateLibraryStats",
                f"-Pcoordinates={library}",
            ]):
                workflow_status = RUN_STATUS_FAILURE
            elif not strategy_obj._commit_library_iteration():
                workflow_status = RUN_STATUS_FAILURE
        else:
            finalize_status, _ = strategy_obj._finalize_successful_iteration()
            if finalize_status in {RUN_STATUS_SUCCESS, SUCCESS_WITH_INTERVENTION_STATUS} and workflow_status == RUN_STATUS_CHUNK_READY:
                workflow_status = RUN_STATUS_CHUNK_READY
            else:
                workflow_status = finalize_status

    ending_commit_hash = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
    if workflow_status == RUN_STATUS_SUCCESS:
        log_stage("status", "Test generation successful")
    elif workflow_status == SUCCESS_WITH_INTERVENTION_STATUS:
        log_stage("status", "Test generation produced PR-eligible post-generation intervention output")
    else:
        log_stage("status", "Test generation failed")

    if unittest_number == 0 and workflow_status != SUCCESS_WITH_INTERVENTION_STATUS:
        log_stage("status", "No valid unit test generated")
        run_metrics = create_failure_run_metrics_output(
            package=package,
            artifact=artifact,
            library_version=library_version,
            agent=agent,
            model_name=model_name,
            global_iterations=global_iterations,
            strategy_name=strategy_name,
            starting_commit=checkpoint_commit_hash,
            ending_commit=ending_commit_hash,
        )
    else:
        run_metrics = metrics_writer.create_run_metrics_output_json(
            repo_path=reachability_repo_path,
            package=package,
            artifact=artifact,
            library_version=library_version,
            agent=agent,
            model_name=model_name,
            global_iterations=global_iterations,
            tests_root=test_source_layout.source_root,
            strategy_name=strategy_name,
            status=workflow_status,
            starting_commit=checkpoint_commit_hash,
            ending_commit=ending_commit_hash,
            post_generation_intervention=strategy_obj.post_generation_intervention,
        )

    metrics_json = resolve_add_new_library_support_metrics_json(
        run_metrics=run_metrics,
        metrics_repo_dir=metrics_repo_dir,
        metrics_repo_root=metrics_repo_root,
        is_benchmark_mode=is_benchmark_mode,
    )
    write_add_new_library_support_metrics(
        run_metrics=run_metrics,
        metrics_json=metrics_json,
        is_benchmark_mode=is_benchmark_mode,
        package=package,
        artifact=artifact,
        library_version=library_version,
        metrics_repo_root=metrics_repo_root,
    )
    return 0 if workflow_status in {RUN_STATUS_SUCCESS, SUCCESS_WITH_INTERVENTION_STATUS, RUN_STATUS_CHUNK_READY} else 1


if __name__ == "__main__":
    if any(a in ("-h", "--help") for a in sys.argv[1:]):
        build_parser().print_help()
        sys.exit(0)
    sys.exit(main())
