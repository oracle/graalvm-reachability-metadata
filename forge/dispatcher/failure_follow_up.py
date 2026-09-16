# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Failed-run follow-ups: human-intervention labeling and resumable-state
comments (§AR-forge-dispatcher-decomposition, §FS-human-intervention-policy)."""


import os
import sys
import traceback
from utility_scripts.continuation_marker import ContinuationMarker
from utility_scripts.continuation_marker import PHASE_PUBLICATION
from utility_scripts.continuation_marker import continuation_marker_path
from utility_scripts.continuation_marker import load_continuation_marker
from utility_scripts.run_location import RunLocation
from dispatcher.config import (
    LABEL_HUMAN_INTERVENTION,
    LABEL_RESUMABLE,
)
from dispatcher.failure_preservation import (
    FailurePreservationResult,
    ensure_preserved_branch_link_in_comment,
    lead_comment_with_failure_location,
)
from dispatcher.human_intervention import (
    resolve_human_intervention_candidate,
    run_codex_failed_generation_analysis,
    run_human_intervention_analysis,
)
from dispatcher.interrupts import is_user_interrupt_requested
from dispatcher.issue_admin import (
    add_issue_label,
    post_issue_comment,
)
from dispatcher.records import ClaimedIssue

def post_human_intervention_comment_and_label(
        issue_number: int,
        comment_body: str | None,
        *,
        resumable: bool = False,
) -> None:
    """Post the human-intervention comment and always attempt to apply the label."""
    if is_user_interrupt_requested():
        return

    if comment_body:
        try:
            post_issue_comment(issue_number, comment_body)
        except Exception as exc:
            print(
                f"ERROR: Failed to post human-intervention comment to issue #{issue_number}: {exc!r}",
                file=sys.stderr,
            )
            traceback.print_exc()

    try:
        add_issue_label(issue_number, LABEL_HUMAN_INTERVENTION)
    except Exception as exc:
        print(
            f"ERROR: Failed to add '{LABEL_HUMAN_INTERVENTION}' label to issue #{issue_number}: {exc!r}",
            file=sys.stderr,
        )
        traceback.print_exc()

    if resumable:
        try:
            add_issue_label(issue_number, LABEL_RESUMABLE)
        except Exception as exc:
            print(
                f"ERROR: Failed to add '{LABEL_RESUMABLE}' label to issue #{issue_number}: {exc!r}",
                file=sys.stderr,
            )
            traceback.print_exc()


def preservation_result_has_continuation_marker(preservation_result: FailurePreservationResult | None) -> bool:
    """Return True when preserved work carries a continuation marker."""
    if preservation_result is None:
        return False
    return os.path.isfile(continuation_marker_path(preservation_result.reviewable_worktree_path))


def load_preservation_result_continuation_marker(
        preservation_result: FailurePreservationResult | None,
) -> ContinuationMarker | None:
    """Load the continuation marker carried by preserved work."""
    if preservation_result is None or preservation_result.reviewable_worktree_path is None:
        return None
    return load_continuation_marker(
        continuation_marker_path(preservation_result.reviewable_worktree_path),
    )


def maybe_apply_human_intervention_follow_up(
        claimed_issue: ClaimedIssue,
        workflow_success: bool = True,
        started_at: float | None = None,
        preservation_result: FailurePreservationResult | None = None,
) -> bool:
    """Post a human-intervention comment and label when an issue run needs follow-up."""
    if is_user_interrupt_requested():
        return False

    candidate = resolve_human_intervention_candidate(claimed_issue, workflow_success)
    if candidate is None:
        return False

    try:
        comment_body = run_human_intervention_analysis(
            claimed_issue,
            candidate,
            started_at,
            preservation_result,
        )
        comment_body = ensure_preserved_branch_link_in_comment(comment_body, preservation_result)
    except Exception as exc:
        print(
            f"ERROR: Failed to apply human-intervention follow-up to issue #{claimed_issue.issue['number']}: {exc!r}",
            file=sys.stderr,
        )
        traceback.print_exc()
        comment_body = None
    if is_user_interrupt_requested():
        return False
    post_human_intervention_comment_and_label(
        claimed_issue.issue["number"],
        comment_body,
        resumable=preservation_result_has_continuation_marker(preservation_result),
    )
    return True


def _build_finalization_failure_comment(claimed_issue: ClaimedIssue) -> str:
    """Explain a post-success finalization/publication failure for maintainers.

    The workflow produced a PR-ready result, so there is no generation gap to
    analyze; publication simply did not complete. The preserved branch carries a
    continuation marker, so this issue is resumable. §FS-forge-run-continuation
    """
    return (
        f"Automated generation for `{claimed_issue.issue_coordinates}` completed, but "
        "publishing the pull request did not finish during finalization (for example a "
        "publication `git rebase` that could not complete on the work branch). The "
        "generated work and a continuation marker are preserved on the branch below, so "
        f"a later Forge run can resume from the publication phase; the `{LABEL_RESUMABLE}` "
        "label marks it for that resume. A maintainer can also finish publication "
        "manually from the preserved branch."
    )


def apply_failed_run_follow_up(
        claimed_issue: ClaimedIssue,
        started_at: float | None = None,
        preservation_result: FailurePreservationResult | None = None,
        failure_location: RunLocation | None = None,
) -> None:
    """Run Codex failure analysis and apply the `human-intervention` follow-up.

    Only reached for logical failures; external failures are released upstream
    without any issue action. A successful workflow that fails during
    finalization/publication carries no analysis candidate but still preserves a
    continuation marker, so it is labeled resumable. §FS-human-intervention-policy
    """
    if is_user_interrupt_requested():
        raise KeyboardInterrupt

    candidate = resolve_human_intervention_candidate(
        claimed_issue,
        workflow_success=False,
    )
    if candidate is None:
        marker = load_preservation_result_continuation_marker(preservation_result)
        # A successful workflow that fails during publication has no generation-analysis
        # candidate. Gate this fallback on the authoritative marker phase so earlier
        # workflow failures are not presented as PR-ready. §FS-forge-run-continuation.2
        if marker is not None and marker.continue_from == PHASE_PUBLICATION:
            post_human_intervention_comment_and_label(
                claimed_issue.issue["number"],
                ensure_preserved_branch_link_in_comment(
                    _lead_failed_run_comment(
                        _build_finalization_failure_comment(claimed_issue),
                        failure_location,
                    ),
                    preservation_result,
                ),
                resumable=True,
            )
        return

    try:
        comment_body = run_codex_failed_generation_analysis(
            claimed_issue,
            candidate,
            started_at,
            preservation_result,
        )
        # The comment leads with the same pair the terminal printed.
        # §FS-forge-run-location-reporting.3
        comment_body = _lead_failed_run_comment(comment_body, failure_location)
        comment_body = ensure_preserved_branch_link_in_comment(comment_body, preservation_result)
    except Exception as exc:
        print(
            f"ERROR: Failed to apply failed-run follow-up to issue #{claimed_issue.issue['number']}: {exc!r}",
            file=sys.stderr,
        )
        traceback.print_exc()
        comment_body = None if failure_location is None else _lead_failed_run_comment("", failure_location)
    if is_user_interrupt_requested():
        raise KeyboardInterrupt
    post_human_intervention_comment_and_label(
        claimed_issue.issue["number"],
        comment_body,
        resumable=preservation_result_has_continuation_marker(preservation_result),
    )


def _lead_failed_run_comment(comment_body: str, failure_location: RunLocation | None) -> str:
    """Prefix a failed-run comment with its run location when one is known."""
    if failure_location is None:
        return comment_body
    return lead_comment_with_failure_location(comment_body, failure_location).rstrip() + "\n"
