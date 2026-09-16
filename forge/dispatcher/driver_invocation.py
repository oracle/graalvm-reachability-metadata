# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Workflow driver routing and invocation for one claimed issue
(§AR-forge-dispatcher-decomposition, §FS-forge-issue-resolution-goal)."""


import shlex
import time
import traceback
from ai_workflows.agents.agent import AgentFailureError
from ai_workflows.drivers.add_new_library_support import DEFAULT_STRATEGY_NAME as DEFAULT_NEW_LIBRARY_STRATEGY_NAME
from ai_workflows.drivers.add_new_library_support import main as run_add_new_library_support_workflow
from ai_workflows.drivers.fix_java_run_fail import main as run_fix_java_run_workflow
from ai_workflows.drivers.fix_javac_fail import main as run_fix_javac_workflow
from ai_workflows.drivers.fix_ni_run import DEFAULT_STRATEGY_NAME as DEFAULT_NI_RUN_STRATEGY_NAME
from ai_workflows.drivers.fix_ni_run import main as run_fix_ni_run_workflow
from ai_workflows.drivers.improve_library_coverage import DEFAULT_STRATEGY_NAME as DEFAULT_LIBRARY_UPDATE_STRATEGY_NAME
from ai_workflows.drivers.improve_library_coverage import main as run_improve_library_coverage_workflow
from ai_workflows.drivers.java_fail_workflow import DEFAULT_JAVAC_STRATEGY
from ai_workflows.drivers.java_fail_workflow import DEFAULT_JAVA_RUN_STRATEGY
from ai_workflows.drivers.library_update_router import LibraryUpdateRoute
from ai_workflows.drivers.library_update_router import ROUTE_FIX_JAVAC
from ai_workflows.drivers.library_update_router import ROUTE_FIX_JAVA_RUN
from ai_workflows.drivers.library_update_router import ROUTE_FIX_NI_RUN
from ai_workflows.drivers.library_update_router import ROUTE_IMPROVE_COVERAGE
from ai_workflows.drivers.library_update_router import select_library_update_route
from collections.abc import Callable
from dataclasses import dataclass
from utility_scripts.continuation_marker import PHASE_PUBLICATION
from utility_scripts.continuation_marker import load_continuation_marker
from utility_scripts.library_preparation_preflight import run_library_preparation_preflight as run_preflight_decision
from utility_scripts.run_location import PHASE_CLAIM
from utility_scripts.run_location import PHASE_SETUP
from utility_scripts.run_location import STEP_NEURAL_SETUP
from utility_scripts.run_location import STEP_NORMAL_SETUP
from utility_scripts.run_location import STEP_ROUTE_TO_DRIVER
from utility_scripts.run_location import bind_continuation_marker
from utility_scripts.run_location import log_step_progress
from utility_scripts.run_location import pipeline_step
from utility_scripts.run_location import report_run_failure
from utility_scripts.run_location import resolve_failure_location
from utility_scripts.run_location import run_step
from utility_scripts.source_context_discovery import GradleBootstrapFailure
from utility_scripts.stage_logger import debug_logging_enabled
from utility_scripts.stage_logger import log_detail
from utility_scripts.stage_logger import log_stage
from dispatcher.claim_setup import extract_issue_requested_metadata_context
from dispatcher.config import (
    LABEL_JAVAC_FAIL,
    LABEL_JAVA_RUN_FAIL,
    LABEL_LIBRARY_NEW,
    LABEL_LIBRARY_UPDATE,
    LABEL_NI_RUN_FAIL,
)
from dispatcher.continuation import (
    create_or_load_run_continuation_marker,
    library_update_route_artifact_root,
    record_library_preparation_preflight_in_marker,
    record_library_update_route_in_marker,
    restore_library_preparation_preflight_from_marker,
    restore_library_update_route_from_marker,
)
from dispatcher.dynamic_access import (
    _dispatcher_uncovered_class_count,
    append_chunked_dynamic_access_workflow_args,
    dynamic_access_chunk_class_threshold,
    prepare_dynamic_access_chunking,
)
from dispatcher.fixture_support import is_fixture_testing_enabled
from dispatcher.human_intervention import is_external_failure_exception
from dispatcher.interrupts import (
    is_interrupt_exit_code,
    is_user_interrupt_requested,
    preserve_user_interrupt_reason,
)
from dispatcher.issue_queue import get_issue_body
from dispatcher.records import (
    ClaimedIssue,
    WorkflowRunResult,
)
from dispatcher.worktrees import require_claimed_issue_worktree

@dataclass(frozen=True)
class WorkflowDriverInvocation:
    driver_name: str
    script_name: str
    runner_name: str
    runner: Callable[[list[str]], int]
    argv: list[str]
    issue_number: int
    issue_label: str
    coordinates: str | None
    current_coordinates: str | None
    new_version: str | None
    log_stage_name: str
    log_message: str
    failure_name: str

    def to_json(self) -> dict:
        return {
            "driver_name": self.driver_name,
            "script_name": self.script_name,
            "runner_name": self.runner_name,
            "argv": list(self.argv),
            "issue_number": self.issue_number,
            "issue_label": self.issue_label,
            "coordinates": self.coordinates,
            "current_coordinates": self.current_coordinates,
            "new_version": self.new_version,
        }

def run_library_preparation_preflight(
        claimed_issue: ClaimedIssue,
) -> str | None:
    """Run and persist the library-specific preparation preflight before workflow dispatch."""
    if claimed_issue.preflight_info_path is None:
        return None
    return run_preflight_decision(
        claimed_issue=claimed_issue,
        issue_body_provider=get_issue_body,
    )


def append_library_preparation_preflight_arg(pipeline_argv: list[str], preflight_path: str | None) -> None:
    """Append the dispatcher preflight path when one was produced."""
    if preflight_path:
        pipeline_argv.extend(["--library-preparation-preflight-path", preflight_path])


def append_issue_requested_metadata_context_arg(pipeline_argv: list[str], issue_number: int) -> None:
    """Append the reporter's request so every driver can attempt it.

    Routing decides which repair a reported issue needs; it does not decide
    whether the reporter's own request is carried. §forge/AR-forge-drivers.2.1
    """
    issue_requested_metadata_context = extract_issue_requested_metadata_context(get_issue_body(issue_number))
    if issue_requested_metadata_context:
        pipeline_argv.extend(["--issue-requested-metadata-context", issue_requested_metadata_context])


def append_continuation_marker_arg(pipeline_argv: list[str], marker_path: str | None) -> None:
    """Append the continuation marker path when continuation tracking is active."""
    if marker_path:
        pipeline_argv.extend(["--continuation-marker-path", marker_path])


def build_workflow_driver_invocation(
        claimed_issue: ClaimedIssue,
        strategy_name: str | None,
        keep_tests_without_dynamic_access: bool,
        library_preparation_preflight_path: str | None = None,
        chunk_class_count: int | None = None,
        library_update_route: LibraryUpdateRoute | None = None,
        continuation_marker_path: str | None = None,
) -> WorkflowDriverInvocation:
    """Build the routed workflow-driver command for a claimed issue."""
    issue_number = claimed_issue.issue["number"]
    if claimed_issue.label == LABEL_LIBRARY_NEW:
        pipeline_argv = [
            "--coordinates", claimed_issue.issue_coordinates,
            "--reachability-metadata-path", claimed_issue.worktree_path,
            "--metrics-repo-path", claimed_issue.scratch_metrics_repo_path,
        ]
        append_library_preparation_preflight_arg(pipeline_argv, library_preparation_preflight_path)
        append_continuation_marker_arg(pipeline_argv, continuation_marker_path)
        append_issue_requested_metadata_context_arg(pipeline_argv, issue_number)
        if strategy_name:
            pipeline_argv.extend(["--strategy-name", strategy_name])
        if keep_tests_without_dynamic_access:
            pipeline_argv.append("--keep-tests-without-dynamic-access")
        append_chunked_dynamic_access_workflow_args(pipeline_argv, claimed_issue, chunk_class_count)
        return WorkflowDriverInvocation(
            driver_name="add_new_library_support",
            script_name="add_new_library_support.py",
            runner_name="run_add_new_library_support_workflow",
            runner=run_add_new_library_support_workflow,
            argv=pipeline_argv,
            issue_number=issue_number,
            issue_label=claimed_issue.label,
            coordinates=claimed_issue.issue_coordinates,
            current_coordinates=claimed_issue.current_coordinates,
            new_version=claimed_issue.new_version,
            log_stage_name="new-library-workflow",
            log_message=(
                f"Invoking add_new_library_support workflow for issue #{issue_number}: "
                f"{claimed_issue.issue_coordinates}"
            ),
            failure_name="add_new_library_support",
        )

    elif claimed_issue.label == LABEL_JAVAC_FAIL:
        pipeline_argv = [
            "--coordinates", claimed_issue.current_coordinates,
            "--new-version", claimed_issue.new_version,
            "--reachability-metadata-path", claimed_issue.worktree_path,
            "--metrics-repo-path", claimed_issue.scratch_metrics_repo_path,
            "--dynamic-access-class-threshold",
            str(dynamic_access_chunk_class_threshold()),
        ]
        append_library_preparation_preflight_arg(pipeline_argv, library_preparation_preflight_path)
        append_continuation_marker_arg(pipeline_argv, continuation_marker_path)
        append_issue_requested_metadata_context_arg(pipeline_argv, issue_number)
        if strategy_name:
            pipeline_argv.extend(["--strategy-name", strategy_name])
        return WorkflowDriverInvocation(
            driver_name="fix_javac_fail",
            script_name="fix_javac_fail.py",
            runner_name="run_fix_javac_workflow",
            runner=run_fix_javac_workflow,
            argv=pipeline_argv,
            issue_number=issue_number,
            issue_label=claimed_issue.label,
            coordinates=None,
            current_coordinates=claimed_issue.current_coordinates,
            new_version=claimed_issue.new_version,
            log_stage_name="javac-fix-workflow",
            log_message=(
                f"Invoking fix_javac_fail workflow for issue #{issue_number}: "
                f"{claimed_issue.current_coordinates} -> {claimed_issue.new_version}"
            ),
            failure_name="fix_javac",
        )

    elif claimed_issue.label == LABEL_JAVA_RUN_FAIL:
        pipeline_argv = [
            "--coordinates", claimed_issue.current_coordinates,
            "--new-version", claimed_issue.new_version,
            "--reachability-metadata-path", claimed_issue.worktree_path,
            "--metrics-repo-path", claimed_issue.scratch_metrics_repo_path,
            "--dynamic-access-class-threshold",
            str(dynamic_access_chunk_class_threshold()),
        ]
        append_library_preparation_preflight_arg(pipeline_argv, library_preparation_preflight_path)
        append_continuation_marker_arg(pipeline_argv, continuation_marker_path)
        append_issue_requested_metadata_context_arg(pipeline_argv, issue_number)
        if strategy_name:
            pipeline_argv.extend(["--strategy-name", strategy_name])
        return WorkflowDriverInvocation(
            driver_name="fix_java_run_fail",
            script_name="fix_java_run_fail.py",
            runner_name="run_fix_java_run_workflow",
            runner=run_fix_java_run_workflow,
            argv=pipeline_argv,
            issue_number=issue_number,
            issue_label=claimed_issue.label,
            coordinates=None,
            current_coordinates=claimed_issue.current_coordinates,
            new_version=claimed_issue.new_version,
            log_stage_name="java-run-fix-workflow",
            log_message=(
                f"Invoking fix_java_run_fail workflow for issue #{issue_number}: "
                f"{claimed_issue.current_coordinates} -> {claimed_issue.new_version}"
            ),
            failure_name="fix_java_run",
        )

    elif claimed_issue.label == LABEL_NI_RUN_FAIL:
        pipeline_argv = [
            "--coordinates", claimed_issue.current_coordinates,
            "--new-version", claimed_issue.new_version,
            "--reachability-metadata-path", claimed_issue.worktree_path,
            "--metrics-repo-path", claimed_issue.scratch_metrics_repo_path,
        ]
        append_library_preparation_preflight_arg(pipeline_argv, library_preparation_preflight_path)
        append_continuation_marker_arg(pipeline_argv, continuation_marker_path)
        append_issue_requested_metadata_context_arg(pipeline_argv, issue_number)
        if strategy_name:
            pipeline_argv.extend(["--strategy-name", strategy_name])
        return WorkflowDriverInvocation(
            driver_name="fix_ni_run",
            script_name="fix_ni_run.py",
            runner_name="run_fix_ni_run_workflow",
            runner=run_fix_ni_run_workflow,
            argv=pipeline_argv,
            issue_number=issue_number,
            issue_label=claimed_issue.label,
            coordinates=None,
            current_coordinates=claimed_issue.current_coordinates,
            new_version=claimed_issue.new_version,
            log_stage_name="native-image-fix-workflow",
            log_message=(
                f"Invoking fix_ni_run workflow for issue #{issue_number}: "
                f"{claimed_issue.current_coordinates} -> {claimed_issue.new_version}"
            ),
            failure_name="fix_ni_run",
        )

    elif claimed_issue.label == LABEL_LIBRARY_UPDATE:
        if library_update_route is None:
            raise ValueError("Library-update workflow invocation requires a selected route.")
        route = library_update_route
        if route.selected_driver == ROUTE_FIX_JAVAC:
            if route.baseline_coordinates is None:
                raise ValueError(f"Missing baseline coordinates for route {route.selected_driver}.")
            pipeline_argv = [
                "--coordinates", route.baseline_coordinates,
                "--new-version", route.new_version,
                "--reachability-metadata-path", claimed_issue.worktree_path,
                "--metrics-repo-path", claimed_issue.scratch_metrics_repo_path,
                "--dynamic-access-class-threshold",
                str(dynamic_access_chunk_class_threshold()),
            ]
            append_library_preparation_preflight_arg(pipeline_argv, library_preparation_preflight_path)
            append_continuation_marker_arg(pipeline_argv, continuation_marker_path)
            append_issue_requested_metadata_context_arg(pipeline_argv, issue_number)
            return WorkflowDriverInvocation(
                driver_name="fix_javac_fail",
                script_name="fix_javac_fail.py",
                runner_name="run_fix_javac_workflow",
                runner=run_fix_javac_workflow,
                argv=pipeline_argv,
                issue_number=issue_number,
                issue_label=claimed_issue.label,
                coordinates=None,
                current_coordinates=route.baseline_coordinates,
                new_version=route.new_version,
                log_stage_name="javac-fix-workflow",
                log_message=(
                    f"Invoking fix_javac_fail workflow for library-update issue #{issue_number}: "
                    f"{route.baseline_coordinates} -> {route.new_version}"
                ),
                failure_name="fix_javac",
            )
        if route.selected_driver == ROUTE_FIX_JAVA_RUN:
            if route.baseline_coordinates is None:
                raise ValueError(f"Missing baseline coordinates for route {route.selected_driver}.")
            pipeline_argv = [
                "--coordinates", route.baseline_coordinates,
                "--new-version", route.new_version,
                "--reachability-metadata-path", claimed_issue.worktree_path,
                "--metrics-repo-path", claimed_issue.scratch_metrics_repo_path,
                "--dynamic-access-class-threshold",
                str(dynamic_access_chunk_class_threshold()),
            ]
            append_library_preparation_preflight_arg(pipeline_argv, library_preparation_preflight_path)
            append_continuation_marker_arg(pipeline_argv, continuation_marker_path)
            append_issue_requested_metadata_context_arg(pipeline_argv, issue_number)
            return WorkflowDriverInvocation(
                driver_name="fix_java_run_fail",
                script_name="fix_java_run_fail.py",
                runner_name="run_fix_java_run_workflow",
                runner=run_fix_java_run_workflow,
                argv=pipeline_argv,
                issue_number=issue_number,
                issue_label=claimed_issue.label,
                coordinates=None,
                current_coordinates=route.baseline_coordinates,
                new_version=route.new_version,
                log_stage_name="java-run-fix-workflow",
                log_message=(
                    f"Invoking fix_java_run_fail workflow for library-update issue #{issue_number}: "
                    f"{route.baseline_coordinates} -> {route.new_version}"
                ),
                failure_name="fix_java_run",
            )
        if route.selected_driver == ROUTE_FIX_NI_RUN:
            if route.baseline_coordinates is None:
                raise ValueError(f"Missing baseline coordinates for route {route.selected_driver}.")
            pipeline_argv = [
                "--coordinates", route.baseline_coordinates,
                "--new-version", route.new_version,
                "--reachability-metadata-path", claimed_issue.worktree_path,
                "--metrics-repo-path", claimed_issue.scratch_metrics_repo_path,
            ]
            append_library_preparation_preflight_arg(pipeline_argv, library_preparation_preflight_path)
            append_continuation_marker_arg(pipeline_argv, continuation_marker_path)
            append_issue_requested_metadata_context_arg(pipeline_argv, issue_number)
            return WorkflowDriverInvocation(
                driver_name="fix_ni_run",
                script_name="fix_ni_run.py",
                runner_name="run_fix_ni_run_workflow",
                runner=run_fix_ni_run_workflow,
                argv=pipeline_argv,
                issue_number=issue_number,
                issue_label=claimed_issue.label,
                coordinates=None,
                current_coordinates=route.baseline_coordinates,
                new_version=route.new_version,
                log_stage_name="native-image-fix-workflow",
                log_message=(
                    f"Invoking fix_ni_run workflow for library-update issue #{issue_number}: "
                    f"{route.baseline_coordinates} -> {route.new_version}"
                ),
                failure_name="fix_ni_run",
            )
        if route.selected_driver != ROUTE_IMPROVE_COVERAGE:
            raise ValueError(f"Unknown library-update route '{route.selected_driver}'")
        pipeline_argv = [
            "--coordinates", claimed_issue.issue_coordinates,
            "--reachability-metadata-path", claimed_issue.worktree_path,
            "--metrics-repo-path", claimed_issue.scratch_metrics_repo_path,
        ]
        append_library_preparation_preflight_arg(pipeline_argv, library_preparation_preflight_path)
        append_continuation_marker_arg(pipeline_argv, continuation_marker_path)
        append_issue_requested_metadata_context_arg(pipeline_argv, issue_number)
        if strategy_name:
            pipeline_argv.extend(["--strategy-name", strategy_name])
        append_chunked_dynamic_access_workflow_args(pipeline_argv, claimed_issue, chunk_class_count)
        return WorkflowDriverInvocation(
            driver_name="improve_library_coverage",
            script_name="improve_library_coverage.py",
            runner_name="run_improve_library_coverage_workflow",
            runner=run_improve_library_coverage_workflow,
            argv=pipeline_argv,
            issue_number=issue_number,
            issue_label=claimed_issue.label,
            coordinates=claimed_issue.issue_coordinates,
            current_coordinates=route.baseline_coordinates,
            new_version=route.new_version,
            log_stage_name="improve-coverage-workflow",
            log_message=(
                f"Invoking improve_library_coverage workflow for issue #{issue_number}: "
                f"{claimed_issue.issue_coordinates}"
            ),
            failure_name="improve_library_coverage",
        )

    raise ValueError(f"Unknown label '{claimed_issue.label}'")


def resolve_workflow_default_strategy_name(
        claimed_issue: ClaimedIssue,
        library_update_route: LibraryUpdateRoute | None,
) -> str:
    """Return the selected workflow driver.s default strategy for durable run state.

    An unresolvable strategy raises rather than failing one issue at a time
    (§FS-forge-run-requirements.1). Omitted strategy overrides remain omitted at
    dispatch (§FS-forge-predefined-strategy-contract).
    """
    if claimed_issue.label == LABEL_LIBRARY_NEW:
        return DEFAULT_NEW_LIBRARY_STRATEGY_NAME
    if claimed_issue.label == LABEL_JAVAC_FAIL:
        return DEFAULT_JAVAC_STRATEGY
    if claimed_issue.label == LABEL_JAVA_RUN_FAIL:
        return DEFAULT_JAVA_RUN_STRATEGY
    if claimed_issue.label == LABEL_NI_RUN_FAIL:
        return DEFAULT_NI_RUN_STRATEGY_NAME
    if claimed_issue.label != LABEL_LIBRARY_UPDATE or library_update_route is None:
        raise ValueError(f"Cannot resolve workflow default strategy for label '{claimed_issue.label}'")
    if library_update_route.selected_driver == ROUTE_FIX_JAVAC:
        return DEFAULT_JAVAC_STRATEGY
    if library_update_route.selected_driver == ROUTE_FIX_JAVA_RUN:
        return DEFAULT_JAVA_RUN_STRATEGY
    if library_update_route.selected_driver == ROUTE_FIX_NI_RUN:
        return DEFAULT_NI_RUN_STRATEGY_NAME
    if library_update_route.selected_driver == ROUTE_IMPROVE_COVERAGE:
        return DEFAULT_LIBRARY_UPDATE_STRATEGY_NAME
    raise ValueError(f"Unknown library-update route '{library_update_route.selected_driver}'")


def resolve_run_strategy_name(
        claimed_issue: ClaimedIssue,
        library_update_route: LibraryUpdateRoute | None,
        strategy_override: str | None,
) -> str:
    """Resolve the strategy recorded before preflight and workflow dispatch."""
    default_strategy_name = resolve_workflow_default_strategy_name(claimed_issue, library_update_route)
    if (
            claimed_issue.label == LABEL_LIBRARY_UPDATE
            and library_update_route is not None
            and library_update_route.selected_driver != ROUTE_IMPROVE_COVERAGE
    ):
        return default_strategy_name
    return strategy_override or default_strategy_name
