# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Shared orchestration for javac and java-run fix workflows."""

import argparse
import os
import subprocess
import sys

if __package__ in (None, ""):
    sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))

import ai_workflows.agents  # noqa: F401 - triggers agent registration
import ai_workflows.core  # noqa: F401 - triggers strategy registration
from ai_workflows.agents import Agent
from ai_workflows.core.workflow_strategy import (
    RUN_STATUS_FAILURE,
    RUN_STATUS_SUCCESS,
    SUCCESS_WITH_INTERVENTION_STATUS,
    WorkflowStrategy,
)
from ai_workflows.drivers.java_fail_workspace import (
    commit_last_passing_candidate,
    copy_and_prepare_project_dir,
    create_project_prep_checkpoint,
    create_versioned_metadata_dir,
    reset_failed_java_fix_worktree,
    update_metadata_index_json,
)
from git_scripts.common_git import ensure_gh_authenticated
from utility_scripts import metrics_writer
from utility_scripts.continuation_marker import (
    PHASE_FINALIZATION,
    PHASE_SETUP,
    load_continuation_marker,
    save_phase_update,
)
from utility_scripts.edit_scope import format_resolved_edit_scope_context
from utility_scripts.issue_requested_metadata import format_issue_requested_metadata_context
from utility_scripts.library_preparation_preflight import (
    prepare_library_preparation_preflight,
    preflight_skip_record_entries,
)
from utility_scripts.skip_record import apply_preflight_skip_record
from utility_scripts.metrics_writer import create_failure_run_metrics_output
from utility_scripts.run_location import (
    STEP_NORMAL_SETUP,
    STEP_RUN_WORKFLOW_ENGINE,
    enter_phase,
    log_step_progress,
    run_step,
)
from utility_scripts.source_context import (
    normalize_source_context_types,
    prepare_source_contexts,
    resolve_test_source_layout,
)
from utility_scripts.source_context_discovery import populate_artifact_urls
from utility_scripts.stage_logger import log_detail
from utility_scripts.strategy_loader import require_strategy_by_name
from utility_scripts.workflow_setup import (
    list_all_files,
    resolve_graalvm_java_home,
    resolve_workflow_repo_paths,
    validate_repo_paths,
)

DEFAULT_MODEL_NAME = "gpt5"

# Default strategy names per mode
DEFAULT_JAVAC_STRATEGY = "javac_iterative_with_coverage_sources_pi_gpt-5.6-sol"
DEFAULT_JAVA_RUN_STRATEGY = "java_run_iterative_with_coverage_sources_pi_gpt-5.6-sol"


class JavaFailWorkflowConfig:
    """Mode-specific configuration for a Java fail workflow."""

    def __init__(
        self,
        *,
        mode: str,
        default_strategy_name: str,
        task_type: str,
        branch_prefix: str,
        metrics_task_type: str,
    ):
        self.mode = mode
        self.default_strategy_name = default_strategy_name
        self.task_type = task_type
        self.branch_prefix = branch_prefix
        self.metrics_task_type = metrics_task_type


JAVAC_CONFIG = JavaFailWorkflowConfig(
    mode="javac",
    default_strategy_name=DEFAULT_JAVAC_STRATEGY,
    task_type="fix-javac-fail",
    branch_prefix="fix-javac",
    metrics_task_type="fix_javac_fail",
)

JAVA_RUN_CONFIG = JavaFailWorkflowConfig(
    mode="java-run",
    default_strategy_name=DEFAULT_JAVA_RUN_STRATEGY,
    task_type="fix-java-run-fail",
    branch_prefix="fix-java-run",
    metrics_task_type="fix_java_run_fail",
)


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
    parser.add_argument(
        "--issue-requested-metadata-context",
        default="",
        help="Reporter-provided missing metadata context extracted from the GitHub issue body.",
    )
    parser.add_argument(
        "--library-preparation-preflight-path",
        help="Path to the dispatcher-created library preparation preflight JSON record.",
    )
    parser.add_argument(
        "--continuation-marker-path",
        help="Path to the Forge run-continuation marker for this issue run.",
    )
    parser.add_argument(
        "--dynamic-access-class-threshold",
        type=int,
        default=0,
        help=(
            "Skip post-repair dynamic-access exploration when the uncovered class count "
            "exceeds this value. "
            "A value of 0 disables the check."
        ),
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
        flags.issue_requested_metadata_context,
        flags.library_preparation_preflight_path,
        flags.continuation_marker_path,
        flags.dynamic_access_class_threshold,
    )


def resolve_fix_metrics_json(
        config: JavaFailWorkflowConfig,
        run_metrics: dict,
        metrics_repo_dir: str,
        metrics_repo_root: str | None = None,
) -> str:
    """Resolve the metrics JSON path for the current execution mode."""
    return metrics_writer.resolve_workflow_metrics_json(
        run_metrics,
        metrics_repo_dir,
        metrics_repo_root,
        config.metrics_task_type,
    )


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
    persistent_instructions: str | None = None,
):
    """Initialize and return the configured agent implementation."""
    editable_files = list_all_files(tests_root)
    editable_files.append(build_gradle_file)

    read_only_files = list_all_files(docs_path) if docs_path else []

    strategy_agent = strategy.get("agent")
    if not strategy_agent:
        print("ERROR: Strategy is missing required field: agent", file=sys.stderr)
        sys.exit(1)

    log_detail("init-agent", f"Initializing {strategy_agent} agent")
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
        persistent_instructions=persistent_instructions,
        thinking_level=strategy.get("thinking-level"),
        agent_name=strategy.get("agent-command"),
    )


def write_fix_metrics(config: JavaFailWorkflowConfig, run_metrics, metrics_repo_dir, metrics_repo_root=None):
    """Append fix metrics to JSON, write pending metrics, and validate schema."""
    metrics_writer.write_workflow_run_metrics(
        run_metrics,
        metrics_repo_dir,
        metrics_repo_root,
        config.metrics_task_type,
    )


def run_java_fail_workflow(config: JavaFailWorkflowConfig, argv=None):
    """Execute the shared java-fail driver implementation for a version bump.

    The shared implementation of §AR-java-fail-fix-workflow that both the javac
    and java-run drivers delegate to, structured as the single-run
    driver boundary (§AR-forge-drivers).
    """
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
        issue_requested_metadata_context,
        library_preparation_preflight_path,
        continuation_marker_path,
        dynamic_access_class_threshold,
    ) = parse_flags(config, argv if argv is not None else sys.argv[1:])

    strategy = require_strategy_by_name(strategy_name)
    continuation_marker = load_continuation_marker(continuation_marker_path)
    resume_from = None if continuation_marker is None else continuation_marker.continue_from
    resume_existing_tree = resume_from in {"fix", "explore", PHASE_FINALIZATION, "publication"}
    resume_finalization = resume_from == PHASE_FINALIZATION
    enter_phase(PHASE_SETUP)
    reachability_repo_path, metrics_repo_dir, metrics_repo_root = resolve_workflow_repo_paths(
        explicit_repo_path,
        explicit_metrics_repo_path,
    )
    # Apply deterministic preflight setup into the resolved worktree before
    # generation; only advisory guidance reaches the prompt context.
    library_preparation_preflight, library_preparation_preflight_context = (
        prepare_library_preparation_preflight(
            library_preparation_preflight_path,
            reachability_repo_path,
        )
    )
    updated_library = f"{group}:{artifact}:{updated_library_version}"
    setup_action = (
        "Reusing prepared repair workspace"
        if resume_existing_tree
        else "Preparing repair workspace"
    )
    log_step_progress(PHASE_SETUP, STEP_NORMAL_SETUP, f"{setup_action} for {updated_library}")
    if not resume_existing_tree:
        save_phase_update(
            continuation_marker_path,
            lambda marker: (
                marker.mark_phase_running(PHASE_SETUP),
                marker.mark_setup_preflight_done(),
            ),
        )
    ensure_gh_authenticated()
    resolve_graalvm_java_home()
    validate_repo_paths(reachability_repo_path, metrics_repo_dir)
    os.chdir(reachability_repo_path)

    # An unsupportable verdict from the preflight replaces the whole fix run
    # with a committed skip record. §FS-unsupportable-version-diagnosis
    skip_entries = preflight_skip_record_entries(library_preparation_preflight, updated_library_version)
    if skip_entries is not None and not resume_existing_tree:
        starting_commit = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
        ending_commit = apply_preflight_skip_record(
            reachability_repo_path=reachability_repo_path,
            group=group,
            artifact=artifact,
            target_version=updated_library_version,
            entries=skip_entries,
            continuation_marker_path=continuation_marker_path,
        )
        if ending_commit is not None:
            print("[Version recorded as skipped; no fix was attempted.]")
            run_metrics = metrics_writer.create_javac_fix_run_metrics_output_json(
                repo_path=reachability_repo_path,
                package=group,
                artifact=artifact,
                previous_library_version=old_library_version,
                new_library_version=updated_library_version,
                agent=None,
                model_name=library_preparation_preflight.get("model"),
                global_iterations=0,
                strategy_name=strategy_name,
                status=RUN_STATUS_SUCCESS,
                starting_commit=starting_commit,
                ending_commit=ending_commit,
                library_preparation_preflight=library_preparation_preflight,
            )
            write_fix_metrics(config, run_metrics, metrics_repo_dir, metrics_repo_root=metrics_repo_root)
            return 0

    tests_dir = os.path.join(
        reachability_repo_path,
        "tests",
        "src",
        group,
        artifact,
        updated_library_version,
    )
    metadata_dir = os.path.join(
        reachability_repo_path,
        "metadata",
        group,
        artifact,
        updated_library_version,
    )
    tests_dir_preexisted = os.path.exists(tests_dir)
    metadata_dir_preexisted = os.path.exists(metadata_dir)

    if not resume_existing_tree:
        # Copying the target version into the tree is the model-free setup.
        # §FS-forge-run-location-reporting.2
        with run_step(PHASE_SETUP, STEP_NORMAL_SETUP, operand=f"{group}:{artifact}:{updated_library_version}"):
            copy_and_prepare_project_dir(group, artifact, old_library_version, updated_library_version)
            update_metadata_index_json(config, group, artifact, updated_library_version)
            create_versioned_metadata_dir(reachability_repo_path, group, artifact, updated_library_version)
            commit_checkpoint = create_project_prep_checkpoint(config, group, artifact, updated_library_version)
            save_phase_update(
                continuation_marker_path,
                lambda marker: marker.mark_setup_done(),
            )
    else:
        log_detail("continuation", f"Resuming {updated_library} from phase {resume_from}")
        commit_checkpoint = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
    if not resume_existing_tree:
        populate_artifact_urls(reachability_repo_path, updated_library)

    source_context_types = normalize_source_context_types(strategy.get("parameters", {}).get("source-context-types"))
    prepared_source_context = prepare_source_contexts(
        repo_root=os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
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
        library=updated_library,
        old_version=old_library_version,
        new_version=updated_library_version,
        build_gradle_file=build_gradle_file,
        source_context_overview=prepared_source_context.to_prompt_overview(),
        source_context_available=prepared_source_context.is_available,
        source_context_files=prepared_source_context.read_only_files,
        test_language=test_source_layout.language,
        test_language_display_name=test_source_layout.display_language,
        test_source_dir_name=test_source_layout.source_dir_name,
        library_preparation_preflight_context=library_preparation_preflight_context,
        issue_requested_metadata_context=format_issue_requested_metadata_context(
            issue_requested_metadata_context,
        ),
        resolved_edit_scope_context=format_resolved_edit_scope_context(
            reachability_repo_path,
            tests_dir,
            test_source_layout.source_root,
            build_gradle_file,
        ),
        continuation_marker_path=continuation_marker_path,
        dynamic_access_class_threshold=dynamic_access_class_threshold,
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
        persistent_instructions=strategy_obj.persistent_instructions,
    )

    log_step_progress(PHASE_SETUP, STEP_NORMAL_SETUP, f"Setup ready for {updated_library}")
    if resume_finalization:
        log_step_progress(
            PHASE_SETUP,
            STEP_RUN_WORKFLOW_ENGINE,
            f"Reusing completed workflow for {strategy_name}",
        )
        workflow_status = RUN_STATUS_SUCCESS
        iterations = 0
    else:
        with run_step(PHASE_SETUP, STEP_RUN_WORKFLOW_ENGINE, operand=strategy_name):
            log_step_progress(
                PHASE_SETUP,
                STEP_RUN_WORKFLOW_ENGINE,
                f"Starting workflow {strategy_name} for {updated_library}",
            )
            workflow_status, iterations = strategy_obj.run(
                agent=agent,
            )

    if workflow_status == RUN_STATUS_SUCCESS:
        # No driver closes a reported request without attempting it.
        # §forge/AR-forge-driver-queues.2.1
        issue_phase_ok, issue_phase_iterations = strategy_obj.run_issue_requested_metadata_phase()
        iterations += issue_phase_iterations
        if not issue_phase_ok:
            workflow_status = RUN_STATUS_FAILURE

    last_passing_candidate_commit = None
    if workflow_status == RUN_STATUS_SUCCESS:
        last_passing_candidate_commit = commit_last_passing_candidate(
            reachability_repo_path,
            group,
            artifact,
            updated_library_version,
        )
        if last_passing_candidate_commit is None:
            workflow_status = RUN_STATUS_FAILURE
        else:
            workflow_status = strategy_obj.finalize_run(commit_checkpoint, workflow_status)

    if workflow_status not in {RUN_STATUS_SUCCESS, SUCCESS_WITH_INTERVENTION_STATUS}:
        print("[Test fixing failed.]")
        ending_commit = reset_failed_java_fix_worktree(
            reachability_repo_path=reachability_repo_path,
            commit_checkpoint=commit_checkpoint,
            last_passing_candidate_commit=last_passing_candidate_commit,
            tests_dir=tests_dir,
            metadata_dir=metadata_dir,
            tests_dir_preexisted=tests_dir_preexisted,
            metadata_dir_preexisted=metadata_dir_preexisted,
        )
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
            library_preparation_preflight=library_preparation_preflight,
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
            strategy_name=strategy_name,
            status=workflow_status,
            starting_commit=commit_checkpoint,
            ending_commit=ending_commit,
            post_generation_intervention=strategy_obj.post_generation_intervention,
            library_preparation_preflight=library_preparation_preflight,
        )
    write_fix_metrics(config, run_metrics, metrics_repo_dir, metrics_repo_root=metrics_repo_root)
    return 0 if workflow_status in {RUN_STATUS_SUCCESS, SUCCESS_WITH_INTERVENTION_STATUS} else 1
