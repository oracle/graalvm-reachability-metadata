# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Shared orchestration for javac and java-run fix workflows."""

import argparse
import os
import shutil
import subprocess
import sys

import ai_workflows.agents  # noqa: F401 - triggers agent registration
import ai_workflows.workflow_strategies  # noqa: F401 — triggers strategy registration
from ai_workflows.agents import Agent
from ai_workflows.workflow_strategies.workflow_strategy import (
    RUN_STATUS_SUCCESS,
    SUCCESS_WITH_INTERVENTION_STATUS,
)
from ai_workflows.workflow_strategies.workflow_strategy import WorkflowStrategy
from git_scripts.common_git import build_ai_branch_name, delete_remote_branch_if_exists, ensure_gh_authenticated
from utility_scripts import metrics_writer
from utility_scripts.metrics_writer import create_failure_run_metrics_output
from utility_scripts.repo_path_resolver import add_in_metadata_repo_argument, resolve_repo_roots
from utility_scripts.schema_validator import validate_run_metrics
from utility_scripts.source_context import (
    normalize_source_context_types,
    populate_artifact_urls,
    prepare_source_contexts,
    resolve_test_source_layout,
)
from utility_scripts.strategy_loader import require_strategy_by_name
from utility_scripts.workflow_setup import resolve_graalvm_java_home, validate_repo_paths

DEFAULT_MODEL_NAME = "oca/gpt5"

# Default strategy names per mode
DEFAULT_JAVAC_STRATEGY = "javac_iterative_with_coverage_sources_pi_gpt-5.5"
DEFAULT_JAVA_RUN_STRATEGY = "java_run_iterative_with_coverage_sources_pi_gpt-5.5"


class JavaFailWorkflowConfig:
    """Mode-specific configuration for a Java fail workflow."""

    def __init__(
        self,
        *,
        mode: str,
        default_strategy_name: str,
        task_type: str,
        branch_prefix: str,
        metrics_filename: str,
    ):
        self.mode = mode
        self.default_strategy_name = default_strategy_name
        self.task_type = task_type
        self.branch_prefix = branch_prefix
        self.metrics_filename = metrics_filename


JAVAC_CONFIG = JavaFailWorkflowConfig(
    mode="javac",
    default_strategy_name=DEFAULT_JAVAC_STRATEGY,
    task_type="fix-javac-fail",
    branch_prefix="fix-javac",
    metrics_filename="fix_javac_fail.json",
)

JAVA_RUN_CONFIG = JavaFailWorkflowConfig(
    mode="java-run",
    default_strategy_name=DEFAULT_JAVA_RUN_STRATEGY,
    task_type="fix-java-run-fail",
    branch_prefix="fix-java-run",
    metrics_filename="fix_java_run_fail.json",
)


def list_all_files(directory_path):
    """Recursively list all files under the provided directory path."""
    files = []
    if not directory_path or not os.path.exists(directory_path):
        return files

    for root_dir, _, file_names in os.walk(directory_path):
        for file_name in file_names:
            files.append(os.path.join(root_dir, file_name))
    return files


def build_parser(config: JavaFailWorkflowConfig):
    """Build and return the CLI parser for a java-fail workflow."""
    parser = argparse.ArgumentParser(
        description=f"Automate {config.mode} fixes for a version bump using configured workflow strategies.",
        formatter_class=argparse.RawTextHelpFormatter,
    )

    parser.add_argument(
        "--coordinates",
        required=True,
        help="Maven coordinates Group:Artifact:Version for the current library version",
    )
    parser.add_argument(
        "--new-version",
        required=True,
        help="Target library version which needs a fix",
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
            "Path where workflow metrics are written."
            "If omitted, the forge directory in the selected worktree is used."
        ),
    )
    add_in_metadata_repo_argument(parser)
    parser.add_argument(
        "--strategy-name",
        dest="strategy_name",
        default=config.default_strategy_name,
        metavar="NAME",
        help=(
            "Strategy name from strategies/predefined_strategies.json "
            f"(default: {config.default_strategy_name})"
        ),
    )
    parser.add_argument(
        "--docs-path",
        default=None,
        help="Optional path with additional read-only docs/sources for agent context",
    )
    parser.add_argument("-v", "--verbose", action="store_true", help="enable verbose mode for the configured agent")
    return parser


def parse_flags(config: JavaFailWorkflowConfig, argv_list):
    """Parse CLI flags and return the core configuration tuple."""
    flags = build_parser(config).parse_args(argv_list)

    try:
        group, artifact, old_version = flags.coordinates.split(":")
    except ValueError:
        print(
            f"ERROR: Invalid coordinates format: {flags.coordinates}. Expected Group:Artifact:Version",
            file=sys.stderr,
        )
        sys.exit(1)

    return (
        group,
        artifact,
        old_version,
        flags.new_version,
        flags.docs_path,
        flags.strategy_name,
        flags.verbose,
        flags.reachability_metadata_path,
        flags.metrics_repo_path,
        flags.in_metadata_repo,
    )


def resolve_repo_paths(explicit_repo_path, explicit_metrics_repo_path, in_metadata_repo: bool = True):
    """Resolve repository paths for code and metrics outputs."""
    resolved_reachability_repo, resolved_metrics_repo = resolve_repo_roots(
        explicit_repo_path,
        explicit_metrics_repo_path,
        in_metadata_repo=in_metadata_repo,
    )

    resolved_metrics_dir = os.path.join(resolved_metrics_repo, "script_run_metrics")
    os.makedirs(resolved_metrics_dir, exist_ok=True)
    return resolved_reachability_repo, resolved_metrics_dir, resolved_metrics_repo


def resolve_fix_metrics_json(
        config: JavaFailWorkflowConfig,
        run_metrics: dict,
        metrics_repo_dir: str,
        metrics_repo_root: str | None = None,
) -> str:
    """Resolve the metrics JSON path for the current execution mode."""
    in_repo_root = metrics_writer.in_metadata_repo_metrics_root(metrics_repo_root)
    if in_repo_root:
        return metrics_writer.execution_metrics_path(in_repo_root, run_metrics)

    return os.path.join(metrics_repo_dir, config.metrics_filename)


def copy_and_prepare_project_dir(group, artifact, library_version, updated_library_version):
    """Copy versioned test project directory and update version references."""
    src_dir = os.path.join("tests", "src", group, artifact, library_version)
    dst_dir = os.path.join("tests", "src", group, artifact, updated_library_version)
    os.makedirs(dst_dir, exist_ok=True)
    shutil.copytree(src_dir, dst_dir, dirs_exist_ok=True)

    gradle_properties_path = os.path.join(dst_dir, "gradle.properties")
    if os.path.isfile(gradle_properties_path):
        with open(gradle_properties_path, "r", encoding="utf-8") as file:
            gradle_props_content = file.read()
        gradle_props_content_updated = gradle_props_content.replace(library_version, updated_library_version)
        with open(gradle_properties_path, "w", encoding="utf-8") as file:
            file.write(gradle_props_content_updated)


def run_gradle_task(task, coordinates):
    """Run a Gradle task for the provided dependency coordinates."""
    command = f"./gradlew {task} -Pcoordinates={coordinates}"
    subprocess.run(command, shell=True, check=True)


def update_metadata_index_json(group, artifact, updated_library_version):
    """Update metadata index.json to point to the new library version."""
    new_version_coordinates = f"{group}:{artifact}:{updated_library_version}"
    run_gradle_task("addLibraryAsLatestMetadataIndexJson", new_version_coordinates)


def create_versioned_metadata_dir(reachability_repo_path, group, artifact, updated_library_version):
    """Ensure the metadata directory for the updated library version exists."""
    metadata_version_dir = os.path.join(reachability_repo_path, "metadata", group, artifact, updated_library_version)
    os.makedirs(metadata_version_dir, exist_ok=True)


def create_project_prep_checkpoint(config: JavaFailWorkflowConfig, group, artifact, updated_library_version):
    """Reset the workflow branch to the current detached HEAD and commit project prep."""
    new_branch = build_ai_branch_name(
        f"{config.branch_prefix}-{group}-{artifact}-{updated_library_version}",
    )
    delete_remote_branch_if_exists(new_branch)
    subprocess.run(
        ["git", "switch", "-C", new_branch],
        check=True,
    )

    candidate_paths = [
        os.path.join("tests", "src", group, artifact, updated_library_version),
        os.path.join("metadata", group, artifact, "index.json"),
    ]

    subprocess.run(["git", "add", *candidate_paths], check=False)

    subprocess.run(
        ["git", "commit", "-m", f"Prepare project for {group}:{artifact}:{updated_library_version}"],
        capture_output=True,
        text=True,
        check=True,
    )
    return subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()


def init_agent(
    strategy,
    reachability_repo_path,
    tests_root,
    build_gradle_file,
    docs_path,
    verbose,
    model_name,
    library=None,
    task_type="session",
):
    """Initialize and return the configured agent implementation."""
    editable_files = list_all_files(tests_root)
    editable_files.append(build_gradle_file)

    read_only_files = list_all_files(docs_path) if docs_path else []

    strategy_agent = strategy.get("agent")
    if not strategy_agent:
        print("ERROR: Strategy is missing required field: agent", file=sys.stderr)
        sys.exit(1)

    print(f"[Initializing {strategy_agent} agent...]")
    agent_class = Agent.get_class(strategy_agent)
    return agent_class(
        model_name=model_name,
        editable_files=editable_files,
        read_only_files=read_only_files,
        working_dir=reachability_repo_path,
        provider=strategy.get("provider"),
        library=library,
        task_type=task_type,
        verbose=verbose,
        mcps=strategy.get("mcps", []),
    )


def write_fix_metrics(config: JavaFailWorkflowConfig, run_metrics, metrics_repo_dir, metrics_repo_root=None):
    """Append fix metrics to JSON, write pending metrics, and validate schema."""
    metrics_json = resolve_fix_metrics_json(
        config=config,
        run_metrics=run_metrics,
        metrics_repo_dir=metrics_repo_dir,
        metrics_repo_root=metrics_repo_root,
    )
    in_repo_root = metrics_writer.in_metadata_repo_metrics_root(metrics_repo_root)
    if in_repo_root:
        task_type = os.path.splitext(config.metrics_filename)[0]
        written_metrics_json = metrics_writer.append_execution_metrics(in_repo_root, run_metrics, task_type)
        if written_metrics_json != metrics_json:
            raise ValueError(
                f"ERROR: Resolved metrics path {metrics_json} does not match written path {written_metrics_json}"
            )
    else:
        metrics_writer.append_run_metrics(run_metrics, metrics_json)
    if metrics_repo_root:
        metrics_writer.write_pending_metrics(metrics_repo_root, run_metrics)
    validate_run_metrics(metrics_json)


def run_java_fail_workflow(config: JavaFailWorkflowConfig, argv=None):
    """Execute the end-to-end java-fail fix workflow for a version bump."""
    (
        group,
        artifact,
        old_library_version,
        updated_library_version,
        docs_path,
        strategy_name,
        is_verbose,
        explicit_repo_path,
        explicit_metrics_repo_path,
        in_metadata_repo,
    ) = parse_flags(config, argv if argv is not None else sys.argv[1:])

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

    copy_and_prepare_project_dir(group, artifact, old_library_version, updated_library_version)
    update_metadata_index_json(group, artifact, updated_library_version)
    create_versioned_metadata_dir(reachability_repo_path, group, artifact, updated_library_version)
    commit_checkpoint = create_project_prep_checkpoint(config, group, artifact, updated_library_version)
    updated_library = f"{group}:{artifact}:{updated_library_version}"
    populate_artifact_urls(reachability_repo_path, updated_library)

    source_context_types = normalize_source_context_types(strategy.get("parameters", {}).get("source-context-types"))
    prepared_source_context = prepare_source_contexts(
        repo_root=os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
        reachability_repo_path=reachability_repo_path,
        coordinate=updated_library,
        source_context_types=source_context_types,
    )

    build_gradle_file = os.path.join(
        reachability_repo_path,
        "tests",
        "src",
        group,
        artifact,
        updated_library_version,
        "build.gradle",
    )
    tests_dir = os.path.join(
        reachability_repo_path,
        "tests",
        "src",
        group,
        artifact,
        updated_library_version,
    )
    test_source_layout = resolve_test_source_layout(reachability_repo_path, updated_library, tests_dir)

    workflow_name = strategy.get("workflow")
    if not workflow_name:
        print("ERROR: Strategy is missing required field: workflow", file=sys.stderr)
        return 1
    StrategyClass = WorkflowStrategy.get_class(workflow_name)
    strategy_obj = StrategyClass(
        strategy_obj=strategy,
        reachability_repo_path=reachability_repo_path,
        updated_library=updated_library,
        old_version=old_library_version,
        new_version=updated_library_version,
        build_gradle_file=build_gradle_file,
        source_context_overview=prepared_source_context.to_prompt_overview(),
        source_context_available=prepared_source_context.is_available,
        source_context_files=prepared_source_context.read_only_files,
        test_language=test_source_layout.language,
        test_language_display_name=test_source_layout.display_language,
        test_source_dir_name=test_source_layout.source_dir_name,
    )

    model_name = strategy.get("model") or DEFAULT_MODEL_NAME

    agent = init_agent(
        strategy=strategy,
        reachability_repo_path=reachability_repo_path,
        tests_root=test_source_layout.source_root,
        build_gradle_file=build_gradle_file,
        docs_path=docs_path,
        verbose=is_verbose,
        model_name=model_name,
        library=updated_library,
        task_type=config.task_type,
    )

    workflow_status, iterations = strategy_obj.run(
        agent=agent,
    )

    if workflow_status == RUN_STATUS_SUCCESS:
        finalize_status, _ = strategy_obj._finalize_successful_iteration()
        workflow_status = finalize_status

    if workflow_status not in {RUN_STATUS_SUCCESS, SUCCESS_WITH_INTERVENTION_STATUS}:
        print("[Test fixing failed.]")
        subprocess.run(["git", "reset", "--hard", commit_checkpoint], check=True)
        ending_commit = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
        metadata_dir = os.path.join(
            reachability_repo_path,
            "metadata",
            group,
            artifact,
            updated_library_version,
        )
        shutil.rmtree(tests_dir, ignore_errors=True)
        shutil.rmtree(metadata_dir, ignore_errors=True)
        run_metrics = create_failure_run_metrics_output(
            package=group,
            artifact=artifact,
            library_version=updated_library_version,
            agent=agent,
            model_name=model_name,
            global_iterations=iterations,
            strategy_name=strategy_name,
            starting_commit=commit_checkpoint,
            ending_commit=ending_commit,
        )
    else:
        if workflow_status == SUCCESS_WITH_INTERVENTION_STATUS:
            print("[Test fixing produced PR-eligible post-generation failure output.]")
        else:
            print("[Test fixing succeeded.]")
        ending_commit = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
        run_metrics = metrics_writer.create_javac_fix_run_metrics_output_json(
            repo_path=reachability_repo_path,
            package=group,
            artifact=artifact,
            previous_library_version=old_library_version,
            new_library_version=updated_library_version,
            agent=agent,
            model_name=model_name,
            global_iterations=iterations,
            tests_root=test_source_layout.source_root,
            strategy_name=strategy_name,
            status=workflow_status,
            starting_commit=commit_checkpoint,
            ending_commit=ending_commit,
            post_generation_intervention=strategy_obj.post_generation_intervention,
        )
    write_fix_metrics(config, run_metrics, metrics_repo_dir, metrics_repo_root=metrics_repo_root)
    return 0 if workflow_status in {RUN_STATUS_SUCCESS, SUCCESS_WITH_INTERVENTION_STATUS} else 1
