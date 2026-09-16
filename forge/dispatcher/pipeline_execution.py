# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Pipeline execution for one claimed issue: strategy-checked driver runs
(§AR-forge-dispatcher-decomposition, §FS-forge-issue-resolution-goal).
"""


import shlex
import time
import traceback
from utility_scripts.run_location import PHASE_CLAIM
from utility_scripts.run_location import log_step_progress
from utility_scripts.run_location import pipeline_step
from utility_scripts.run_location import run_step
from utility_scripts.stage_logger import debug_logging_enabled
from utility_scripts.stage_logger import log_stage
from dispatcher.config import (
    LABEL_LIBRARY_NEW,
    LABEL_LIBRARY_UPDATE,
)
from dispatcher.continuation import (
    create_or_load_run_continuation_marker,
    library_update_route_artifact_root,
    record_library_preparation_preflight_in_marker,
    record_library_update_route_in_marker,
    restore_library_preparation_preflight_from_marker,
    restore_library_update_route_from_marker,
)
from dispatcher.driver_invocation import (
    build_workflow_driver_invocation,
    resolve_run_strategy_name,
    run_library_preparation_preflight,
)
from dispatcher.dynamic_access import (
    _dispatcher_uncovered_class_count,
    prepare_dynamic_access_chunking,
)
from dispatcher.fixture_support import is_fixture_testing_enabled
from dispatcher.human_intervention import is_external_failure_exception
from dispatcher.interrupts import (
    is_interrupt_exit_code,
    is_user_interrupt_requested,
    preserve_user_interrupt_reason,
)
from dispatcher.records import (
    ClaimedIssue,
    WorkflowRunResult,
)
from dispatcher.worktrees import require_claimed_issue_worktree

from ai_workflows.agents.agent import AgentFailureError
from ai_workflows.drivers.library_update_router import ROUTE_IMPROVE_COVERAGE
from ai_workflows.drivers.library_update_router import select_library_update_route
from utility_scripts.continuation_marker import PHASE_PUBLICATION
from utility_scripts.continuation_marker import load_continuation_marker
from utility_scripts.run_location import PHASE_SETUP
from utility_scripts.run_location import STEP_NEURAL_SETUP
from utility_scripts.run_location import STEP_NORMAL_SETUP
from utility_scripts.run_location import STEP_ROUTE_TO_DRIVER
from utility_scripts.run_location import bind_continuation_marker
from utility_scripts.run_location import report_run_failure
from utility_scripts.run_location import resolve_failure_location
from utility_scripts.source_context_discovery import GradleBootstrapFailure
from utility_scripts.stage_logger import log_detail

@pipeline_step(
    PHASE_CLAIM,
    STEP_ROUTE_TO_DRIVER,
    operand=lambda arguments: f"issue #{arguments['claimed_issue'].issue['number']}",
)
def invoke_pipeline(
        claimed_issue: ClaimedIssue,
        strategy_name: str | None,
        keep_tests_without_dynamic_access: bool,
) -> bool:
    """Run the matching workflow driver in the claimed issue's worktree.

    Label-based routing stays in the dispatcher (§AR-forge-control-plane);
    workflow drivers then own the run end to end
    (§AR-forge-workflow-boundary), receiving resolved coordinates, paths,
    strategy names, and chunk context.
    """
    issue_number = claimed_issue.issue["number"]
    log_step_progress(
        PHASE_CLAIM,
        STEP_ROUTE_TO_DRIVER,
        f"Routing issue #{issue_number} to its workflow driver",
    )
    require_claimed_issue_worktree(claimed_issue, "workflow execution")
    strategy_override = strategy_name
    if claimed_issue.continuation_marker is not None:
        strategy_override = claimed_issue.continuation_marker.strategy_name

    library_update_route = None
    if claimed_issue.label == LABEL_LIBRARY_UPDATE:
        library_update_route = restore_library_update_route_from_marker(
            claimed_issue,
            claimed_issue.continuation_marker,
        )
        if library_update_route is None:
            library_update_route = select_library_update_route(
                claimed_issue.worktree_path,
                library_update_route_artifact_root(claimed_issue),
                claimed_issue.issue_coordinates,
            )

    run_strategy_name = resolve_run_strategy_name(
        claimed_issue,
        library_update_route,
        strategy_override,
    )
    log_step_progress(
        PHASE_CLAIM,
        STEP_ROUTE_TO_DRIVER,
        f"Issue #{issue_number} routed to {claimed_issue.label} with strategy {run_strategy_name}",
    )
    continuation_path = create_or_load_run_continuation_marker(
        claimed_issue,
        run_strategy_name,
    )
    # Failures recorded from here on travel to the preserved branch in the
    # marker. §FS-forge-run-location-reporting.3
    bind_continuation_marker(continuation_path)
    marker = load_continuation_marker(continuation_path)
    record_library_update_route_in_marker(continuation_path, library_update_route)
    if marker is not None and marker.continue_from == PHASE_PUBLICATION:
        restore_library_update_route_from_marker(claimed_issue, marker, required=True)
        log_stage(
            "continuation",
            f"Issue #{claimed_issue.issue['number']} resumes at publication; skipping workflow driver.",
        )
        return True
    setup_phase = {} if marker is None else marker.phases.get("setup", {})
    if bool(setup_phase.get("preflightDone")):
        library_preparation_preflight_path = restore_library_preparation_preflight_from_marker(
            claimed_issue,
            marker,
        )
        log_detail(
            "continuation",
            f"Issue #{claimed_issue.issue['number']} skips completed setup preflight.",
        )
        log_step_progress(
            PHASE_SETUP,
            STEP_NEURAL_SETUP,
            f"Reusing completed library preflight for {claimed_issue.issue_coordinates}",
        )
    else:
        # The preflight agent is the run's neural setup, and it runs before the
        # driver prepares the tree. §FS-forge-run-location-reporting.1
        with run_step(PHASE_SETUP, STEP_NEURAL_SETUP, operand=claimed_issue.issue_coordinates):
            library_preparation_preflight_path = run_library_preparation_preflight(
                claimed_issue,
            )
        record_library_preparation_preflight_in_marker(
            continuation_path,
            library_preparation_preflight_path,
        )
    chunk_class_count = None
    if (
            claimed_issue.label == LABEL_LIBRARY_NEW
            or (
                    claimed_issue.label == LABEL_LIBRARY_UPDATE
                    and library_update_route is not None
                    and library_update_route.selected_driver == ROUTE_IMPROVE_COVERAGE
            )
    ):
        with run_step(PHASE_SETUP, STEP_NORMAL_SETUP, operand=claimed_issue.issue_coordinates):
            log_step_progress(
                PHASE_SETUP,
                STEP_NORMAL_SETUP,
                f"Inspecting dynamic access for {claimed_issue.issue_coordinates}",
            )
            chunk_class_count = prepare_dynamic_access_chunking(
                claimed_issue,
                run_strategy_name,
            )
            uncovered_classes = _dispatcher_uncovered_class_count(claimed_issue)
            budget_suffix = f", class budget {chunk_class_count}" if chunk_class_count is not None else ""
            inspection_result = (
                "report unavailable"
                if uncovered_classes == "unavailable"
                else f"{uncovered_classes} uncovered classes{budget_suffix}"
            )
            log_step_progress(
                PHASE_SETUP,
                STEP_NORMAL_SETUP,
                f"Dynamic-access inspection ready for {claimed_issue.issue_coordinates}: "
                f"{inspection_result}",
            )
    invocation = build_workflow_driver_invocation(
            claimed_issue,
            strategy_override,
            keep_tests_without_dynamic_access,
            library_preparation_preflight_path,
            chunk_class_count,
            library_update_route,
            continuation_path,
        )
    if debug_logging_enabled() and "--verbose" not in invocation.argv:
        invocation.argv.append("--verbose")

    log_detail(invocation.log_stage_name, invocation.log_message)
    if is_fixture_testing_enabled():
        display_argv = list(invocation.argv)
        if "--issue-requested-metadata-context" in display_argv:
            context_flag_index = display_argv.index("--issue-requested-metadata-context")
            del display_argv[context_flag_index:context_flag_index + 2]
        log_stage(
            "workflow-driver",
            (
                f"Fixture mode invoking {invocation.script_name}: "
                f"{' '.join(shlex.quote(argument) for argument in display_argv)}"
            ),
        )
    rc = invocation.runner(invocation.argv)
    if is_interrupt_exit_code(rc):
        preserve_user_interrupt_reason()
        raise KeyboardInterrupt
    if rc != 0:
        # A driver that returns non-zero has already recorded the step it failed
        # in; the location leads its error. §FS-forge-run-location-reporting.3
        report_run_failure(
            resolve_failure_location(),
            (
                f"ERROR: {invocation.failure_name} workflow failed for issue "
                f"#{invocation.issue_number} (exit {rc})"
            ),
        )
        return False

    print()
    log_stage("pipeline", f"Pipeline succeeded for issue #{invocation.issue_number}")
    return True


def run_claimed_issue(
        claimed_issue: ClaimedIssue,
        strategy_name: str | None,
        keep_tests_without_dynamic_access: bool,
) -> WorkflowRunResult:
    """Execute the claimed issue workflow inside its isolated worktree."""
    started_at = time.time()
    failure_was_external = False
    try:
        success = invoke_pipeline(
            claimed_issue,
            strategy_name,
            keep_tests_without_dynamic_access,
        )
    except GradleBootstrapFailure:
        raise
    except KeyboardInterrupt:
        preserve_user_interrupt_reason()
        raise
    except Exception as exc:
        if is_user_interrupt_requested():
            raise KeyboardInterrupt from exc
        # The location the step annotated onto the exception leads its detail.
        # §FS-forge-run-location-reporting.3
        error_detail = str(exc) if isinstance(exc, AgentFailureError) else (
            f"Issue #{claimed_issue.issue['number']} workflow raised an exception: {exc!r}"
        )
        report_run_failure(
            resolve_failure_location(exc),
            error_detail,
            log_path=exc.log_path if isinstance(exc, AgentFailureError) else None,
        )
        if not isinstance(exc, AgentFailureError):
            traceback.print_exc()
        success = False
        failure_was_external = is_external_failure_exception(exc)
    if is_user_interrupt_requested():
        raise KeyboardInterrupt
    return WorkflowRunResult(
        claimed_issue=claimed_issue,
        success=success,
        started_at=started_at,
        failure_was_external=failure_was_external,
    )
