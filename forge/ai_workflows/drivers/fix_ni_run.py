# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

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
from ai_workflows.drivers.fix_ni_run_setup import (
    build_strategy_and_agent,
    commit_checkpoint,
    create_or_switch_branch,
    run_fix_test_native_image_run,
    should_explore_new_version,
)
from ai_workflows.drivers.improve_library_coverage import prepare_library_update_target
from git_scripts.common_git import build_ai_branch_name
from utility_scripts import metrics_writer
from utility_scripts.continuation_marker import (
    PHASE_EXPLORE,
    PHASE_FINALIZATION,
    PHASE_FIX,
    PHASE_SETUP,
    load_continuation_marker,
    save_phase_update,
)
from utility_scripts.library_preparation_preflight import (
    prepare_library_preparation_preflight,
    preflight_skip_record_entries,
)
from utility_scripts.skip_record import apply_preflight_skip_record
from utility_scripts.metadata_index import resolve_test_version
from utility_scripts.metrics_writer import create_failure_run_metrics_output
from utility_scripts.native_test_verification import global_output_dir
from utility_scripts.run_location import (
    STEP_NORMAL_SETUP,
    STEP_RUN_WORKFLOW_ENGINE,
    clear_recorded_failure,
    enter_phase,
    log_step_progress,
    record_step_failure,
    run_step,
)
from utility_scripts.source_context_discovery import populate_artifact_urls
from utility_scripts.stage_logger import log_detail, log_stage
from utility_scripts.workflow_setup import (
    resolve_graalvm_java_home,
    resolve_workflow_repo_paths,
    validate_repo_paths,
)

DEFAULT_STRATEGY_NAME = "library_update_pi_gpt-5.6-sol"
METRICS_TASK_TYPE = "fix_ni_run"


def build_parser():
    parser = argparse.ArgumentParser(
        prog="fix_ni_run.py",
        description="Run fixTestNativeImageRun Gradle task for a library version upgrade.",
        epilog=(
            "Example:\n"
            "  python3 ai_workflows/drivers/fix_ni_run.py \\\n"
            "      --coordinates com.example:lib:1.2.3 \\\n"
            "      --new-version 1.2.4 \\\n"
            "      --reachability-metadata-path /path/to/graalvm-reachability-metadata\n"
        ),
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
        help="Path where dispatcher-created workflow context artifacts are written.",
    )
    parser.add_argument(
        "--strategy-name",
        dest="strategy_name",
        default=DEFAULT_STRATEGY_NAME,
        metavar="NAME",
        help=(
            "Dynamic-access strategy used for the conditional exploration phase "
            f"(default: {DEFAULT_STRATEGY_NAME})"
        ),
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


def main(argv=None) -> int:
    """Run the Native Image fix driver.

    The single-run driver (§AR-forge-drivers) for
    §AR-forge-driver-queues.4. `fixTestNativeImageRun` produces the seed. A
    failed seed enters the shared native-test gate before any agent repair;
    dynamic-access exploration runs only when the new version has uncovered
    calls; the shared `finalize_run` path always gates PR eligibility.
    """
    args = build_parser().parse_args(sys.argv[1:] if argv is None else argv)

    reachability_metadata_path, metrics_repo_dir, metrics_repo_root = resolve_workflow_repo_paths(
        args.reachability_metadata_path,
        args.metrics_repo_path,
    )

    current_coordinates = args.coordinates
    new_version = args.new_version
    continuation_marker = load_continuation_marker(args.continuation_marker_path)
    resume_from = None if continuation_marker is None else continuation_marker.continue_from
    resume_existing_tree = resume_from in {"fix", PHASE_EXPLORE, PHASE_FINALIZATION, "publication"}
    resume_finalization = resume_from == PHASE_FINALIZATION
    enter_phase(PHASE_SETUP)
    # Apply deterministic preflight setup into the resolved worktree before
    # generation; only advisory guidance reaches the prompt context.
    library_preparation_preflight, library_preparation_preflight_context = (
        prepare_library_preparation_preflight(
            args.library_preparation_preflight_path,
            reachability_metadata_path,
        )
    )
    if not resume_existing_tree:
        save_phase_update(
            args.continuation_marker_path,
            lambda marker: (
                marker.mark_phase_running(PHASE_SETUP),
                marker.mark_setup_preflight_done(),
            ),
        )
    if library_preparation_preflight is not None:
        log_detail("pipeline", "Library preparation preflight:")
        log_detail("pipeline", library_preparation_preflight_context)

    resolve_graalvm_java_home()
    validate_repo_paths(reachability_metadata_path, metrics_repo_dir)
    os.chdir(reachability_metadata_path)

    group, artifact, old_version = current_coordinates.split(":")
    library = f"{group}:{artifact}:{new_version}"

    # An unsupportable verdict from the preflight replaces the whole fix run
    # with a committed skip record. §FS-unsupportable-version-diagnosis
    skip_entries = preflight_skip_record_entries(library_preparation_preflight, new_version)
    if skip_entries is not None and not resume_existing_tree:
        starting_commit = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
        ending_commit = apply_preflight_skip_record(
            reachability_repo_path=reachability_metadata_path,
            group=group,
            artifact=artifact,
            target_version=new_version,
            entries=skip_entries,
            continuation_marker_path=args.continuation_marker_path,
        )
        if ending_commit is not None:
            print("[Version recorded as skipped; no fix was attempted.]")
            run_metrics = metrics_writer.create_java_run_fix_run_metrics_output_json(
                repo_path=reachability_metadata_path,
                package=group,
                artifact=artifact,
                previous_library_version=old_version,
                new_library_version=new_version,
                agent=None,
                model_name=library_preparation_preflight.get("model"),
                global_iterations=0,
                strategy_name=args.strategy_name,
                status=RUN_STATUS_SUCCESS,
                starting_commit=starting_commit,
                ending_commit=ending_commit,
                library_preparation_preflight=library_preparation_preflight,
            )
            metrics_writer.write_workflow_run_metrics(run_metrics, metrics_repo_dir, metrics_repo_root, METRICS_TASK_TYPE)
            return 0

    branch = build_ai_branch_name(
        f"fix-native-image-run-{group}-{artifact}-{new_version}",
        cwd=reachability_metadata_path,
    )
    strategy_obj: WorkflowStrategy | None = None
    agent: Agent | None = None
    model_name: str | None = None
    tests_root: str | None = None
    setup_action = (
        "Reusing prepared native-image workspace"
        if resume_existing_tree
        else "Preparing native-image workspace"
    )
    log_step_progress(PHASE_SETUP, STEP_NORMAL_SETUP, f"{setup_action} for {library}")
    if not resume_existing_tree:
        # Branching and the deterministic native-image fix are the run's
        # model-free setup. §FS-forge-run-location-reporting.2
        with run_step(PHASE_SETUP, STEP_NORMAL_SETUP, operand=library):
            log_detail("pipeline", f"Creating branch: {branch}")
            create_or_switch_branch(reachability_metadata_path, branch)

            log_detail(
                "pipeline",
                f"Running fixTestNativeImageRun for: {current_coordinates} -> {new_version}",
            )
            result = run_fix_test_native_image_run(
                reachability_metadata_path=reachability_metadata_path,
                current_coordinates=current_coordinates,
                new_version=new_version,
            )

            if result.returncode != 0:
                # The failed JVM-agent seed reaches runtime truth before any agent
                # repair. §FS-native-test-verification-gate §AR-forge-workflow-pipeline
                strategy_obj, agent, model_name, tests_root = build_strategy_and_agent(
                    strategy_name=args.strategy_name,
                    reachability_metadata_path=reachability_metadata_path,
                    library=library,
                    group=group,
                    artifact=artifact,
                    version=new_version,
                    library_preparation_preflight_context=library_preparation_preflight_context,
                    explore=False,
                    issue_requested_metadata_context=args.issue_requested_metadata_context,
                    continuation_marker_path=args.continuation_marker_path,
                )
                test_version = resolve_test_version(
                    reachability_metadata_path,
                    group,
                    artifact,
                    new_version,
                )
                if not strategy_obj.verify_native_test_gate(
                        global_output_dir(
                            reachability_metadata_path,
                            group,
                            artifact,
                            test_version,
                        ),
                        label="fixTestNativeImageRun failure",
                ):
                    print(
                        f"ERROR: native-test gate failed after fixTestNativeImageRun for {library}.",
                        file=sys.stderr,
                    )
                    record_step_failure()
                    return 1

            populate_artifact_urls(reachability_metadata_path, library)
            checkpoint = commit_checkpoint(reachability_metadata_path, library)
            save_phase_update(
                args.continuation_marker_path,
                lambda marker: (
                    marker.mark_setup_done(),
                    marker.mark_phase_completed(PHASE_FIX),
                ),
            )
    else:
        log_detail("continuation", f"Resuming {library} from preserved branch at phase {resume_from}")
        checkpoint = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()

    # Coverage gate: explore only when the new version has uncovered calls.
    explore = not resume_finalization and should_explore_new_version(reachability_metadata_path, group, artifact, new_version)
    if explore:
        log_detail("pipeline", f"Preparing version-specific test-suite for {library}")
        prepare_library_update_target(reachability_metadata_path, group, artifact, new_version)

    if strategy_obj is None or explore:
        strategy_obj, agent, model_name, tests_root = build_strategy_and_agent(
            strategy_name=args.strategy_name,
            reachability_metadata_path=reachability_metadata_path,
            library=library,
            group=group,
            artifact=artifact,
            version=new_version,
            library_preparation_preflight_context=library_preparation_preflight_context,
            explore=explore,
            issue_requested_metadata_context=args.issue_requested_metadata_context,
            continuation_marker_path=args.continuation_marker_path,
        )
    assert agent is not None
    assert model_name is not None
    assert tests_root is not None
    log_step_progress(PHASE_SETUP, STEP_NORMAL_SETUP, f"Setup ready for {library}")

    iterations = 0
    if explore:
        with run_step(PHASE_SETUP, STEP_RUN_WORKFLOW_ENGINE, operand=args.strategy_name):
            log_step_progress(
                PHASE_SETUP,
                STEP_RUN_WORKFLOW_ENGINE,
                f"Starting workflow {args.strategy_name} for {library}",
            )
            run_result = strategy_obj.run(agent=agent, checkpoint_commit_hash=checkpoint)
        explore_status, iterations = run_result[0], run_result[1]
        # Best-effort: a partial or failed explore must not abort. The seed is
        # already valid and the finalization gate decides PR eligibility.
        log_stage("explore", f"Dynamic-access exploration completed with status: {explore_status}")
        if explore_status != RUN_STATUS_SUCCESS:
            clear_recorded_failure()
            save_phase_update(
                args.continuation_marker_path,
                lambda marker: marker.mark_phase_completed(PHASE_EXPLORE, iteration=iterations),
            )
            strategy_obj, _seed_agent, model_name, tests_root = build_strategy_and_agent(
                strategy_name=args.strategy_name,
                reachability_metadata_path=reachability_metadata_path,
                library=library,
                group=group,
                artifact=artifact,
                version=new_version,
                library_preparation_preflight_context=library_preparation_preflight_context,
                explore=False,
                issue_requested_metadata_context=args.issue_requested_metadata_context,
                continuation_marker_path=args.continuation_marker_path,
            )
    else:
        log_step_progress(
            PHASE_SETUP,
            STEP_RUN_WORKFLOW_ENGINE,
            f"Dynamic-access workflow not needed for {library}",
        )
        save_phase_update(
            args.continuation_marker_path,
            lambda marker: marker.mark_phase_skipped(PHASE_EXPLORE),
        )

    # No driver closes a reported request without attempting it.
    # §forge/AR-forge-driver-queues.2.1
    issue_phase_ok, issue_phase_iterations = strategy_obj.run_issue_requested_metadata_phase()
    iterations += issue_phase_iterations
    if issue_phase_ok:
        finalize_status = strategy_obj.finalize_run(checkpoint)
    else:
        log_stage("explore", "Reporter-requested metadata phase did not succeed")
        finalize_status = RUN_STATUS_FAILURE

    succeeded = finalize_status in {RUN_STATUS_SUCCESS, SUCCESS_WITH_INTERVENTION_STATUS}

    if succeeded:
        if finalize_status == SUCCESS_WITH_INTERVENTION_STATUS:
            print("[pipeline] Finalization produced PR-eligible post-generation intervention output.")
        ending_commit = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
        run_metrics = metrics_writer.create_java_run_fix_run_metrics_output_json(
            repo_path=reachability_metadata_path,
            package=group,
            artifact=artifact,
            previous_library_version=old_version,
            new_library_version=new_version,
            agent=agent,
            model_name=model_name,
            global_iterations=iterations,
            strategy_name=args.strategy_name,
            status=finalize_status,
            starting_commit=checkpoint,
            ending_commit=ending_commit,
            post_generation_intervention=strategy_obj.post_generation_intervention,
            library_preparation_preflight=library_preparation_preflight,
        )
    else:
        # Keep the generated working-tree state as the debugging surface for the
        # next maintainer or Forge run (§AR-forge-driver-queues.4).
        print("[pipeline] Finalization failed. Skipping PR creation.", file=sys.stderr)
        ending_commit = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
        run_metrics = create_failure_run_metrics_output(
            package=group,
            artifact=artifact,
            library_version=new_version,
            agent=agent,
            model_name=model_name,
            global_iterations=iterations,
            strategy_name=args.strategy_name,
            starting_commit=checkpoint,
            ending_commit=ending_commit,
            library_preparation_preflight=library_preparation_preflight,
        )

    metrics_writer.write_workflow_run_metrics(run_metrics, metrics_repo_dir, metrics_repo_root, METRICS_TASK_TYPE)

    if not succeeded:
        return 1
    print(f"[pipeline] Workflow succeeded for {current_coordinates}")
    return 0


if __name__ == "__main__":
    if any(a in ("-h", "--help") for a in sys.argv[1:]):
        build_parser().print_help()
        sys.exit(0)
    sys.exit(main())
