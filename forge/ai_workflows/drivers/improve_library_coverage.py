# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Workflow entry point for improving dynamic-access coverage on an existing library.

This operates on an already-passing test suite and
focuses solely on generating new tests to improve dynamic-access coverage.

Usage:
  python3 ai_workflows/drivers/improve_library_coverage.py \
    --coordinates group:artifact:version \
    [--reachability-metadata-path /path/to/graalvm-reachability-metadata] \
    [--metrics-repo-path /path/to/metrics-storage] \
    [--docs-path /path/to/docs] \
    [--strategy-name "library_update_optimistic_pi_gpt-5.6-sol"] \
    [-v]
"""

import argparse
import json
import os
import subprocess
import sys

if __package__ in (None, ""):
    sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))

import ai_workflows.agents  # noqa: F401 - triggers agent registration
import ai_workflows.core  # noqa: F401 - triggers strategy registration
from ai_workflows.agents import Agent
from ai_workflows.drivers.library_update_preparation import (
    prepare_library_update_target,
    reset_failed_library_update_worktree,
    write_library_update_target_sidecar,
)
from ai_workflows.core.workflow_strategy import (
    RUN_STATUS_CHUNK_READY,
    RUN_STATUS_SUCCESS,
    SUCCESS_WITH_INTERVENTION_STATUS,
    WorkflowStrategy,
    strategy_skips_initial_fix_phase,
)
from git_scripts.common_git import (
    build_ai_branch_name,
    delete_remote_branch_if_exists,
    ensure_gh_authenticated,
    load_library_stats,
    switch_branch_quietly,
)
from utility_scripts import metrics_writer
from utility_scripts.continuation_marker import (
    PHASE_FINALIZATION,
    PHASE_SETUP,
    load_continuation_marker,
    save_phase_update,
)
from utility_scripts.dynamic_access_exhaust_report import resolve_workflow_exhaust_report
from utility_scripts.edit_scope import format_resolved_edit_scope_context
from utility_scripts.issue_requested_metadata import format_issue_requested_metadata_context
from utility_scripts.library_preparation_preflight import (
    prepare_library_preparation_preflight,
)
from utility_scripts.metadata_index import MATCH_NEW_VERSION
from utility_scripts.metrics_writer import count_metadata_entries, count_test_only_metadata_entries, create_failure_run_metrics_output
from utility_scripts.run_location import (
    STEP_NORMAL_SETUP,
    STEP_RUN_WORKFLOW_ENGINE,
    RunLocation,
    enter_phase,
    log_step_progress,
    record_step_failure,
    run_step,
)
from utility_scripts.source_context import (
    normalize_source_context_types,
    populate_artifact_urls,
    prepare_source_contexts,
    resolve_test_source_layout,
    source_context_urls_available,
)
from utility_scripts.stage_logger import log_detail, log_stage
from utility_scripts.strategy_loader import require_strategy_by_name
from utility_scripts.workflow_setup import (
    list_all_files,
    resolve_graalvm_java_home,
    resolve_workflow_repo_paths,
    validate_repo_paths,
)

DEFAULT_MODEL_NAME = "gpt-5.6-sol"
DEFAULT_STRATEGY_NAME = "library_update_optimistic_pi_gpt-5.6-sol"
METRICS_TASK_TYPE = "improve_library_coverage"
BASELINE_STATS_FILENAME = ".baseline-stats.json"


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
        "--chunk-class-count",
        type=int,
        default=0,
        help="Stop a chunked dynamic-access run after this many processed classes. 0 disables chunking.",
    )
    parser.add_argument(
        "--issue-number",
        type=int,
        help="GitHub issue number for the dynamic-access exhaust report.",
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
        flags.chunk_class_count,
        flags.issue_number,
        flags.issue_requested_metadata_context,
        flags.library_preparation_preflight_path,
        flags.continuation_marker_path,
    )


def main(argv=None) -> int:
    """Run one library-update coverage workflow from setup through metrics.

    The single-run driver (§AR-forge-drivers) for
    §AR-forge-driver-queues.2.
    """
    (
        group,
        artifact,
        version,
        docs_path,
        strategy_name,
        is_verbose,
        explicit_repo_path,
        explicit_metrics_repo_path,
        chunk_class_count,
        issue_number,
        issue_requested_metadata_context,
        library_preparation_preflight_path,
        continuation_marker_path,
    ) = parse_flags(argv if argv is not None else sys.argv[1:])

    library = f"{group}:{artifact}:{version}"
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

    log_detail("setup", f"Selected strategy: {strategy_name}")
    setup_action = (
        "Reusing prepared coverage workspace"
        if resume_existing_tree
        else "Preparing coverage workspace"
    )
    log_step_progress(PHASE_SETUP, STEP_NORMAL_SETUP, f"{setup_action} for {library}")
    update_target = prepare_library_update_target(
        reachability_repo_path,
        group,
        artifact,
        version,
        issue_requested_metadata_context=issue_requested_metadata_context,
    )
    if update_target.match_type != MATCH_NEW_VERSION:
        log_detail(
            "library-update-target",
            (
                f"Using {update_target.match_type} target: "
                f"metadata-version={update_target.resolved_metadata_version}, "
                f"test-version={update_target.resolved_test_version}"
            ),
        )
    model_name = strategy.get("model") or DEFAULT_MODEL_NAME
    if not resume_existing_tree:
        # Branching the improvement run is its model-free setup.
        # §FS-forge-run-location-reporting.2
        with run_step(PHASE_SETUP, STEP_NORMAL_SETUP, operand=library):
            new_branch = build_ai_branch_name(f"improve-coverage-{group}-{artifact}-{version}")
            delete_remote_branch_if_exists(new_branch)
            switch_branch_quietly(new_branch)
    else:
        log_detail("continuation", f"Resuming {library} from preserved branch at phase {resume_from}")

    # Commit existing state as checkpoint
    test_version = update_target.resolved_test_version
    tests_dir = update_target.test_dir
    dynamic_access_exhaust_report, dynamic_access_exhaust_report_path = resolve_workflow_exhaust_report(
        repo_path=reachability_repo_path,
        issue_number=issue_number,
        coordinate=library,
        chunk_class_count=chunk_class_count,
    )
    if not os.path.isdir(tests_dir):
        print(
            "ERROR: Test directory for {library} does not exist: {path}".format(
                library=library,
                path=os.path.relpath(tests_dir, reachability_repo_path),
            ),
            file=sys.stderr,
        )
        record_step_failure(location=RunLocation(PHASE_SETUP, STEP_NORMAL_SETUP, library))
        return 1
    if test_version != version:
        log_detail(
            "setup",
            "Using indexed test directory tests/src/{group}/{artifact}/{test_version} for {library}".format(
                group=group,
                artifact=artifact,
                test_version=test_version,
                library=library,
            ),
        )
    index_json = os.path.join(reachability_repo_path, "metadata", group, artifact, "index.json")
    baseline_stats_path = os.path.join(tests_dir, BASELINE_STATS_FILENAME)
    if resume_existing_tree:
        if not os.path.isfile(baseline_stats_path):
            print(
                f"ERROR: Resumed {library} is missing cached baseline snapshot: "
                f"{os.path.relpath(baseline_stats_path, reachability_repo_path)}",
                file=sys.stderr,
            )
            record_step_failure(location=RunLocation(PHASE_SETUP, STEP_NORMAL_SETUP, library))
            return 1
        log_detail("setup", f"Reusing cached baseline snapshot from {BASELINE_STATS_FILENAME}")
    else:
        # Snapshot baseline stats and metadata entry counts before the workflow modifies them.
        baseline_stats = load_library_stats(reachability_repo_path, library)
        baseline_metadata_entries = count_metadata_entries(reachability_repo_path, group, artifact, version)
        baseline_test_only_entries = count_test_only_metadata_entries(reachability_repo_path, group, artifact, version)
        baseline_snapshot = {
            "stats": baseline_stats,
            "metadata_entries": baseline_metadata_entries,
            "test_only_metadata_entries": baseline_test_only_entries,
        }
        with open(baseline_stats_path, "w", encoding="utf-8") as f:
            json.dump(baseline_snapshot, f, indent=2)
        log_detail("setup", f"Saved baseline snapshot to {BASELINE_STATS_FILENAME}")
    if not resume_existing_tree:
        subprocess.run(["git", "add", tests_dir, index_json], check=False)
        subprocess.run(
            ["git", "commit", "--allow-empty", "-m", f"Checkpoint for coverage improvement of {library}"],
            capture_output=True, text=True, check=False,
        )
    checkpoint_commit = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
    log_detail("setup", f"Checkpoint commit: {checkpoint_commit}")
    if not resume_existing_tree:
        save_phase_update(
            continuation_marker_path,
            lambda marker: marker.mark_setup_done(
                skip_fix_phase=strategy_skips_initial_fix_phase(strategy),
            ),
        )

    source_context_types = normalize_source_context_types(strategy.get("parameters", {}).get("source-context-types"))
    if not resume_existing_tree or not source_context_urls_available(
            reachability_repo_path,
            library,
            source_context_types,
    ):
        populate_artifact_urls(reachability_repo_path, library)
    else:
        log_detail(
            "populate-artifact-urls",
            f"Skipping artifact URL population for resumed {library}; index.json already has requested source-context URLs.",
        )
    prepared_source_context = prepare_source_contexts(
        repo_root=os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
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
        test_version=test_version,
        build_gradle_file=build_gradle_file,
        source_context_overview=prepared_source_context.to_prompt_overview(),
        source_context_available=prepared_source_context.is_available,
        source_context_files=prepared_source_context.read_only_files,
        issue_requested_metadata_context=format_issue_requested_metadata_context(issue_requested_metadata_context),
        resolved_edit_scope_context=format_resolved_edit_scope_context(
            reachability_repo_path,
            tests_dir,
            test_source_layout.source_root,
            build_gradle_file,
        ),
        test_language=test_source_layout.language,
        test_language_display_name=test_source_layout.display_language,
        test_source_dir_name=test_source_layout.source_dir_name,
        metadata_version=update_target.resolved_metadata_version,
        library_preparation_preflight_context=library_preparation_preflight_context,
        dynamic_access_exhaust_report=dynamic_access_exhaust_report,
        dynamic_access_exhaust_report_path=dynamic_access_exhaust_report_path,
        chunk_class_count=chunk_class_count,
        continuation_marker_path=continuation_marker_path,
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

    log_detail("init-agent", f"Initializing {strategy_agent} agent")
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
        persistent_instructions=strategy_obj.persistent_instructions,
        thinking_level=strategy.get("thinking-level"),
        agent_name=strategy.get("agent-command"),
    )

    log_step_progress(PHASE_SETUP, STEP_NORMAL_SETUP, f"Setup ready for {library}")
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
                f"Starting workflow {strategy_name} for {library}",
            )
            run_result = strategy_obj.run(
                agent=agent,
                checkpoint_commit_hash=checkpoint_commit,
            )
        workflow_status, iterations = run_result[0], run_result[1]

    if workflow_status == RUN_STATUS_SUCCESS:
        # No successful strategy reaches finalization without this phase.
        # §forge/AR-forge-driver-queues.2.1
        issue_phase_ok, issue_phase_iterations = strategy_obj.run_issue_requested_metadata_phase()
        iterations += issue_phase_iterations
        if not issue_phase_ok:
            workflow_status = RUN_STATUS_FAILURE

    if workflow_status in {RUN_STATUS_SUCCESS, RUN_STATUS_CHUNK_READY}:
        workflow_status = strategy_obj.finalize_run(checkpoint_commit, workflow_status)

    ending_commit = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()

    if workflow_status not in {RUN_STATUS_SUCCESS, SUCCESS_WITH_INTERVENTION_STATUS, RUN_STATUS_CHUNK_READY}:
        log_stage("status", "Coverage improvement failed")
        ending_commit = reset_failed_library_update_worktree(
            reachability_repo_path,
            checkpoint_commit,
            update_target,
        )
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
            library_preparation_preflight=library_preparation_preflight,
        )
    else:
        if workflow_status == SUCCESS_WITH_INTERVENTION_STATUS:
            log_detail("status", "Coverage improvement produced PR-eligible post-generation intervention output")
        else:
            log_detail("status", "Coverage improvement succeeded")
        run_metrics = metrics_writer.create_run_metrics_output_json(
            repo_path=reachability_repo_path,
            package=group,
            artifact=artifact,
            library_version=version,
            agent=agent,
            model_name=model_name,
            global_iterations=iterations,
            strategy_name=strategy_name,
            status=workflow_status,
            starting_commit=checkpoint_commit,
            ending_commit=ending_commit,
            post_generation_intervention=strategy_obj.post_generation_intervention,
            library_preparation_preflight=library_preparation_preflight,
        )

    write_library_update_target_sidecar(metrics_repo_root, update_target)
    metrics_writer.write_workflow_run_metrics(run_metrics, metrics_repo_dir, metrics_repo_root, METRICS_TASK_TYPE)
    return 0 if workflow_status in {RUN_STATUS_SUCCESS, SUCCESS_WITH_INTERVENTION_STATUS, RUN_STATUS_CHUNK_READY} else 1


if __name__ == "__main__":
    if any(a in ("-h", "--help") for a in sys.argv[1:]):
        build_parser().print_help()
        sys.exit(0)
    sys.exit(main())
