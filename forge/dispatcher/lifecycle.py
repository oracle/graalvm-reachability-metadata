# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The claimed-issue lifecycle: completion, failure handling, and cleanup
(§AR-forge-dispatcher-decomposition, §FS-forge-run-status)."""


import os
import sys
import time
import traceback
from utility_scripts.continuation_marker import continuation_marker_path
from utility_scripts.continuation_marker import load_continuation_marker
from utility_scripts.run_location import bind_run_context
from utility_scripts.run_location import report_run_failure
from utility_scripts.run_location import reset_run_location
from utility_scripts.source_context_discovery import GradleBootstrapFailure
from utility_scripts.stage_logger import log_failure_banner
from utility_scripts.stage_logger import log_stage
from utility_scripts.stage_logger import log_success_banner
from dispatcher.config import (
    INTERRUPT_REASON_GRADLE_BOOTSTRAP,
    LABEL_RESUMABLE,
)
from dispatcher.pipeline_execution import run_claimed_issue
from dispatcher.failure_follow_up import (
    apply_failed_run_follow_up,
    maybe_apply_human_intervention_follow_up,
)
from dispatcher.failure_preservation import (
    FailurePreservationResult,
    build_fixture_failure_preservation_result,
    preservation_failed_worktree_paths,
    preserve_failed_work_branch,
    refresh_preserved_branch_logs,
    resolve_claimed_issue_failure_location,
)
from dispatcher.fixture_support import is_fixture_testing_enabled
from dispatcher.human_intervention import is_external_failure_exception
from dispatcher.interrupts import (
    get_user_interrupt_reason,
    is_interrupt_exception,
    is_user_interrupt_requested,
    mark_user_interrupt_requested,
    preserve_user_interrupt_reason,
)
from dispatcher.issue_admin import remove_issue_label
from dispatcher.issue_claiming import revert_claimed_issue
from dispatcher.issue_queue import format_issue_result_message
from dispatcher.records import (
    ClaimedIssue,
    WorkflowRunResult,
)
from dispatcher.worktrees import cleanup_issue_workspace

from utility_scripts.run_location import enter_phase
from utility_scripts.run_location import run_step
from dispatcher.config import LABEL_CHUNKED_DYNAMIC_ACCESS
from dispatcher.human_intervention import _load_pending_run_metrics
from dispatcher.issue_admin import add_issue_label
from dispatcher.publication import (
    build_publication_handoff,
    prepare_java_fix_coverage_follow_up,
    preserve_fixture_preflight_evidence,
)

from ai_workflows.core.workflow_strategy import RUN_STATUS_CHUNK_READY
from utility_scripts.continuation_marker import PHASE_PUBLICATION
from utility_scripts.dynamic_access_exhaust_report import find_dynamic_access_exhaust_report_path
from utility_scripts.run_location import STEP_PUBLISH_BRANCH

def preserve_failed_work_for_follow_up(claimed_issue: ClaimedIssue) -> FailurePreservationResult | None:
    """Push failed work to a branch for human follow-up."""
    if is_fixture_testing_enabled():
        preservation_result = build_fixture_failure_preservation_result(claimed_issue)
        preservation_failed_worktree_paths.add(claimed_issue.worktree_path)
        log_stage(
            "preserve-failed-work",
            (
                f"Fixture mode: dry-run failure preservation handoff for branch "
                f"{preservation_result.branch_name} for issue #{claimed_issue.issue['number']}."
            ),
        )
        return preservation_result
    try:
        return preserve_failed_work_branch(claimed_issue)
    except Exception as exc:
        preservation_failed_worktree_paths.add(claimed_issue.worktree_path)
        print(
            (
                f"ERROR: Failed to preserve work for issue #{claimed_issue.issue['number']} "
                f"before cleanup: {exc!r}. Keeping the local worktree for manual recovery."
            ),
            file=sys.stderr,
        )
        traceback.print_exc()
        return None


def _repeated_resume_failure_phase(claimed_issue: ClaimedIssue) -> str | None:
    """Return the phase when a resumed run failed again without advancing."""
    resumed_marker = claimed_issue.continuation_marker
    if resumed_marker is None:
        return None
    active_marker = load_continuation_marker(continuation_marker_path(claimed_issue.worktree_path))
    if active_marker is None:
        return None
    resumed_phase = resumed_marker.continue_from
    if resumed_phase and active_marker.continue_from == resumed_phase:
        return resumed_phase
    return None


def handle_repeated_resume_failure(claimed_issue: ClaimedIssue, phase_name: str, reason: str) -> None:
    """Stop automatic continuation after the same resumed phase fails again."""
    issue_number = claimed_issue.issue["number"]
    log_stage(
        "continuation",
        (
            f"Issue #{issue_number} resumed at phase {phase_name} and failed there again; "
            f"removing '{LABEL_RESUMABLE}' without posting another human-intervention report."
        ),
    )
    try:
        remove_issue_label(issue_number, LABEL_RESUMABLE)
    except Exception as exc:
        print(
            f"ERROR: Failed to remove '{LABEL_RESUMABLE}' label from issue #{issue_number}: {exc!r}",
            file=sys.stderr,
        )
        traceback.print_exc()
    revert_claimed_issue(claimed_issue, f"{reason}; repeated resume failure at {phase_name}")


def handle_failed_claimed_issue(
        claimed_issue: ClaimedIssue,
        reason: str,
        started_at: float | None = None,
        external: bool = False,
) -> None:
    """Handle a failed claimed issue, then revert its claim.

    An external failure (a typed GitHub or git-transport exception) is not the
    issue's fault: release the claim silently so it is retried, with no preserved
    branch, comment, or `human-intervention` label. A logical failure preserves
    work and applies the follow-up. §FS-human-intervention-policy
    """
    if is_user_interrupt_requested():
        raise KeyboardInterrupt
    # Every terminal failure names where it failed, whatever route it took here.
    # §FS-forge-run-location-reporting.3
    failure_location = resolve_claimed_issue_failure_location(claimed_issue)
    report_run_failure(failure_location, f"ERROR: {reason}")
    if external:
        log_stage(
            "issue-external-failure",
            (
                f"Issue #{claimed_issue.issue['number']} failed on an external dependency "
                f"({reason}); releasing the claim for retry without human-intervention."
            ),
        )
        revert_claimed_issue(claimed_issue, reason)
        return
    repeated_resume_failure_phase = _repeated_resume_failure_phase(claimed_issue)
    if repeated_resume_failure_phase is not None:
        handle_repeated_resume_failure(claimed_issue, repeated_resume_failure_phase, reason)
        return
    preservation_result = preserve_failed_work_for_follow_up(claimed_issue)
    if is_user_interrupt_requested():
        raise KeyboardInterrupt
    apply_failed_run_follow_up(
        claimed_issue,
        started_at=started_at,
        preservation_result=preservation_result,
        failure_location=failure_location,
    )
    try:
        refresh_preserved_branch_logs(claimed_issue, preservation_result)
    except Exception as exc:
        print(
            f"ERROR: Failed to refresh preserved logs for issue #{claimed_issue.issue['number']}: {exc!r}",
            file=sys.stderr,
        )
        traceback.print_exc()
    revert_claimed_issue(claimed_issue, reason)


def handle_completed_run(run_result: WorkflowRunResult) -> bool:
    """Finalize a completed workflow run and return the final handled result."""
    claimed_issue = run_result.claimed_issue
    try:
        if is_user_interrupt_requested():
            raise KeyboardInterrupt
        if not run_result.success:
            handle_failed_claimed_issue(
                claimed_issue,
                "workflow failure",
                started_at=run_result.started_at,
                external=run_result.failure_was_external,
            )
            return False
        try:
            finalize_successful_issue(claimed_issue)
        except Exception as exc:
            print(
                f"ERROR: Issue #{claimed_issue.issue['number']} finalization raised an exception: {exc!r}",
                file=sys.stderr,
            )
            traceback.print_exc()
            handle_failed_claimed_issue(
                claimed_issue,
                "finalization failure",
                started_at=run_result.started_at,
                external=is_external_failure_exception(exc),
            )
            return False
        apply_chunked_dynamic_access_completion_follow_up(claimed_issue)
        maybe_apply_human_intervention_follow_up(
            claimed_issue,
            workflow_success=True,
            started_at=run_result.started_at,
        )
        return True
    except KeyboardInterrupt:
        preserve_user_interrupt_reason()
        raise
    except Exception as exc:
        print(
            f"ERROR: Issue #{claimed_issue.issue['number']} failure handling raised an exception: {exc!r}",
            file=sys.stderr,
        )
        traceback.print_exc()
        return False


def process_claimed_issue_lifecycle(
        claimed_issue: ClaimedIssue,
        strategy_name: str | None,
        keep_tests_without_dynamic_access: bool,
        canonical_metrics_repo_path: str,
) -> bool:
    """Run workflow, finalize or revert, and always clean up the claimed issue workspace."""
    lifecycle_completed = False
    started_at = time.time()
    stable_cwd = claimed_issue.base_reachability_metadata_path
    # This run owns its thread's location for the whole lifecycle.
    # §FS-forge-run-location-reporting.2
    reset_run_location()
    bind_run_context(f"issue #{claimed_issue.issue['number']} {claimed_issue.issue_coordinates}")
    try:
        os.chdir(stable_cwd)
        run_result = run_claimed_issue(
            claimed_issue,
            strategy_name,
            keep_tests_without_dynamic_access,
        )
        if is_user_interrupt_requested():
            raise KeyboardInterrupt
        handled = handle_completed_run(run_result)
        lifecycle_completed = True
        if handled:
            log_success_banner(
                format_issue_result_message(
                    claimed_issue,
                    "Workflow finished, follow-up completed, and the issue was finalized.",
                )
            )
        else:
            log_failure_banner(
                format_issue_result_message(
                    claimed_issue,
                    (
                        "Workflow failed; failure follow-up was attempted. Check prior errors "
                        "for follow-up and claim status."
                    ),
                ),
                file=sys.stderr,
            )
        return handled
    except GradleBootstrapFailure as exc:
        if not lifecycle_completed:
            mark_user_interrupt_requested(INTERRUPT_REASON_GRADLE_BOOTSTRAP)
            revert_claimed_issue(claimed_issue, INTERRUPT_REASON_GRADLE_BOOTSTRAP)
            log_failure_banner(
                format_issue_result_message(
                    claimed_issue,
                    (
                        "Run stopped on a shared Gradle bootstrap failure; the issue claim was "
                        "reverted without failed-work preservation or human-intervention follow-up."
                    ),
                ),
                file=sys.stderr,
            )
            raise KeyboardInterrupt from exc
        raise
    except BaseException as exc:
        if not lifecycle_completed:
            if is_interrupt_exception(exc) or is_user_interrupt_requested():
                preserve_user_interrupt_reason()
                revert_claimed_issue(claimed_issue, get_user_interrupt_reason())
                raise
            else:
                try:
                    handle_failed_claimed_issue(
                        claimed_issue,
                        f"unhandled lifecycle failure ({type(exc).__name__})",
                        started_at=started_at,
                        external=is_external_failure_exception(exc),
                    )
                    log_failure_banner(
                        format_issue_result_message(
                            claimed_issue,
                            (
                                "Workflow failed with an unhandled lifecycle error; failure follow-up "
                                "was attempted and the issue claim was reverted."
                            ),
                        ),
                        file=sys.stderr,
                    )
                except KeyboardInterrupt:
                    preserve_user_interrupt_reason()
                    revert_claimed_issue(claimed_issue, get_user_interrupt_reason())
                    raise
                return False
        raise
    finally:
        try:
            os.chdir(stable_cwd)
            cleanup_issue_workspace(claimed_issue, canonical_metrics_repo_path)
        except Exception as exc:
            print(
                f"ERROR: Failed to clean up workspaces for issue #{claimed_issue.issue['number']}: {exc!r}",
                file=sys.stderr,
            )
            traceback.print_exc()


def apply_chunked_dynamic_access_completion_follow_up(claimed_issue: ClaimedIssue) -> None:
    """Apply issue labels after a chunked dynamic-access part was published."""
    run_metrics = _load_pending_run_metrics(claimed_issue.scratch_metrics_repo_path)
    workflow_status = None if run_metrics is None else run_metrics.get("status")
    exhaust_report_path = find_dynamic_access_exhaust_report_path(
        claimed_issue.worktree_path,
        claimed_issue.issue_coordinates,
    )
    if exhaust_report_path is None:
        return
    issue_number = claimed_issue.issue["number"]
    if workflow_status == RUN_STATUS_CHUNK_READY:
        add_issue_label(issue_number, LABEL_CHUNKED_DYNAMIC_ACCESS)


def finalize_successful_issue(
        claimed_issue: ClaimedIssue,
) -> None:
    """Create the PR for a successful isolated workflow run.

    Publication is delegated to workflow-specific git scripts only after the
    workflow records a PR-eligible status (§AR-pr-eligibility), keeping
    generation and publication separate (§AR-forge-verification-publication-boundary).
    """
    enter_phase(PHASE_PUBLICATION)
    coverage_follow_up = prepare_java_fix_coverage_follow_up(claimed_issue)
    coverage_follow_up_args = coverage_follow_up or (None, None, None)
    if is_fixture_testing_enabled():
        preserve_fixture_preflight_evidence(claimed_issue)
        log_stage(
            "publication",
            (
                f"Fixture mode: skipping publication for issue "
                f"#{claimed_issue.issue['number']}; publication is exercised against GitHub, "
                "not by the hermetic fixture run."
            ),
        )
        return

    with run_step(
        PHASE_PUBLICATION,
        STEP_PUBLISH_BRANCH,
        operand=claimed_issue.issue_coordinates,
    ):
        handoff = build_publication_handoff(claimed_issue, *coverage_follow_up_args)
        handoff.runner(handoff.argv)
