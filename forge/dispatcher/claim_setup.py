# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Issue claiming into a prepared workspace: claim metadata, terminal-label
handling, and workspace setup (§AR-forge-dispatcher-decomposition,
§FS-forge-issue-resolution-goal)."""


import shutil
import sys
from typing import Optional
from utility_scripts.continuation_marker import ContinuationMarker
from utility_scripts.continuation_marker import continuation_marker_path
from utility_scripts.continuation_marker import load_continuation_marker
from utility_scripts.metadata_index import coordinate_parts as metadata_coordinate_parts
from utility_scripts.metadata_index import get_not_for_native_image_marker
from utility_scripts.metadata_index import is_not_for_native_image
from utility_scripts.run_location import PHASE_CLAIM
from utility_scripts.run_location import STEP_CLAIM_ISSUE
from utility_scripts.run_location import enter_phase
from utility_scripts.run_location import log_step_progress
from utility_scripts.stage_logger import log_stage
from dispatcher.claim_preflight import refresh_issue_payload_for_claim
from dispatcher.config import (
    DEFAULT_TAKE_BLOCKED_ISSUES,
    ISSUE_CLAIM_CACHE_REASON_NOT_FOR_NATIVE_IMAGE,
    LABEL_HUMAN_INTERVENTION,
    LABEL_LIBRARY_NEW,
    LABEL_LIBRARY_UPDATE,
    LABEL_NOT_FOR_NATIVE_IMAGE,
    LABEL_RESUMABLE,
)
from dispatcher.continuation import (
    checkout_continuation_branch,
    resolve_issue_continuation_marker,
)
from dispatcher.coordinates import (
    extract_coordinate_parts,
    extract_maven_coordinates,
    load_current_metadata_version,
)
from dispatcher.dynamic_access import (
    resolve_chunked_dynamic_access_exhaust_report,
    verify_chunked_dynamic_access_base_contains_published_commit,
)
from dispatcher.fixture_support import (
    is_fixture_testing_enabled,
    require_fixture_github_state,
)
from dispatcher.interrupts import is_interrupt_exception
from dispatcher.issue_admin import (
    add_issue_label,
    post_issue_comment,
)
from dispatcher.issue_cache import record_issue_claim_cache_observations
from dispatcher.issue_claiming import (
    revert_issue_claim,
    try_claim_issue,
)
from dispatcher.issue_form import (
    check_issue_form,
    reject_issue_form,
)
from dispatcher.issue_queue import issue_is_resumable
from dispatcher.records import (
    ClaimedIssue,
    IssueClaimCacheObservation,
)
from dispatcher.worktrees import (
    create_issue_workspace,
    create_preflight_info_dir,
    fetch_issue_base_commit,
    remove_worktree,
    resolve_git_commit,
)

def build_claim_metadata(
        issue: dict,
        label: str,
        base_reachability_metadata_path: str,
) -> Optional[tuple[str, str | None, str | None]]:
    """Resolve the coordinates needed to execute a claimed issue.

    The issue is the run's input, so a title that does not resolve to Maven
    coordinates is rejected at the boundary (§FS-forge-run-requirements.3).
    """
    issue_coordinates = extract_maven_coordinates(issue["title"])
    if issue_coordinates is None:
        print(f"ERROR: No coordinates found in issue title: {issue['title']}", file=sys.stderr)
        return None

    if label in {LABEL_LIBRARY_NEW, LABEL_LIBRARY_UPDATE}:
        return issue_coordinates, None, None

    coordinate_parts = extract_coordinate_parts(issue["title"])
    if coordinate_parts is None:
        print(f"ERROR: No coordinates found in issue title: {issue['title']}", file=sys.stderr)
        return None

    group, artifact, new_version = coordinate_parts
    # `fails-*` issues target the newest version, so keep `latest` as the signal.
    # §AR-forge-driver-queues
    current_version = load_current_metadata_version(
        base_reachability_metadata_path,
        group,
        artifact,
    )
    if current_version is None:
        return None

    current_coordinates = f"{group}:{artifact}:{current_version}"
    return issue_coordinates, current_coordinates, new_version


def extract_issue_requested_metadata_context(issue_body: str | None, max_chars: int = 50000) -> str:
    """Return reporter-provided missing metadata context from an issue body."""
    if not issue_body:
        return ""

    context = issue_body.strip()
    if len(context) > max_chars:
        context = context[:max_chars].rstrip() + "\n[truncated]"
    return context

def maybe_handle_not_for_native_image_issue(issue: dict, base_reachability_metadata_path: str) -> bool:
    """Apply terminal labels and comment when the repository already has a marker for the artifact."""
    issue_coordinates = extract_maven_coordinates(issue["title"])
    if issue_coordinates is None:
        return False
    group, artifact, _version = metadata_coordinate_parts(issue_coordinates)
    if not is_not_for_native_image(base_reachability_metadata_path, group, artifact):
        return False

    marker = get_not_for_native_image_marker(base_reachability_metadata_path, group, artifact) or {}
    reason = marker.get("reason") or "The artifact is marked as not applicable to GraalVM Native Image metadata."
    replacement = marker.get("replacement")
    body = (
        f"`{group}:{artifact}` is already tracked in the repository as `not-for-native-image`, "
        "so Forge will not run reachability-metadata workflows for this artifact.\n\n"
        f"Reason: {reason}"
    )
    if replacement:
        body += f"\n\nReplacement guidance: {replacement}"
    try:
        post_issue_comment(issue["number"], body)
        add_issue_label(issue["number"], LABEL_NOT_FOR_NATIVE_IMAGE)
        add_issue_label(issue["number"], LABEL_HUMAN_INTERVENTION)
    except Exception as exc:
        print(
            f"ERROR: Failed to apply not-for-native-image follow-up to issue #{issue['number']}: {exc!r}",
            file=sys.stderr,
        )
    record_issue_claim_cache_observations([
        IssueClaimCacheObservation(
            issue_number=issue["number"],
            reason=ISSUE_CLAIM_CACHE_REASON_NOT_FOR_NATIVE_IMAGE,
        )
    ])
    return True

def cleanup_claim_preparation_workspace(
        base_reachability_metadata_path: str,
        worktree_path: str | None,
        preflight_info_path: str | None,
) -> None:
    """Remove workspace state when claim preparation does not reach dispatch.

    §FS-forge-run-requirements.2
    """
    if preflight_info_path:
        shutil.rmtree(preflight_info_path, ignore_errors=True)
    if not worktree_path:
        return
    try:
        remove_worktree(base_reachability_metadata_path, worktree_path)
    except Exception as exc:
        print(
            f"ERROR: Failed to clean up unstarted issue worktree {worktree_path}: {exc!r}",
            file=sys.stderr,
        )


def claim_issue_for_processing(
        issue: dict,
        label: str,
        base_reachability_metadata_path: str,
        canonical_metrics_repo_path: str,
        authenticated_user: str,
        take_blocked_issues: bool = DEFAULT_TAKE_BLOCKED_ISSUES,
) -> Optional[ClaimedIssue]:
    """Claim an issue and prepare its isolated execution workspace.

    Forge code may run from a monitored feature branch, but repository-dependent
    preconditions and generated work use one freshly fetched, pinned
    `origin/master` commit (§FS-forge-run-requirements.2,
    §FS-forge-run-requirements.4).
    """
    enter_phase(PHASE_CLAIM)
    issue_number = issue["number"]
    log_step_progress(PHASE_CLAIM, STEP_CLAIM_ISSUE, f"Claiming issue #{issue_number}")
    if not refresh_issue_payload_for_claim(issue, label, authenticated_user):
        log_step_progress(
            PHASE_CLAIM,
            STEP_CLAIM_ISSUE,
            f"Issue #{issue_number} not claimed: live issue state is no longer eligible",
        )
        return None

    try:
        issue_base_commit = fetch_issue_base_commit(base_reachability_metadata_path)
    except BaseException as exc:
        if isinstance(exc, Exception):
            log_step_progress(
                PHASE_CLAIM,
                STEP_CLAIM_ISSUE,
                f"Issue #{issue_number} not claimed: newest origin/master could not be pinned",
            )
            return None
        raise

    item_id = try_claim_issue(issue, authenticated_user, label, take_blocked_issues)
    if not item_id:
        log_step_progress(
            PHASE_CLAIM,
            STEP_CLAIM_ISSUE,
            f"Issue #{issue_number} not claimed: live claimability check did not pass",
        )
        return None

    worktree_path: str | None = None
    scratch_metrics_repo_path: str | None = None
    preflight_info_path: str | None = None
    handoff_complete = False
    failure_stage = "claim setup"
    try:
        worktree_path, scratch_metrics_repo_path = create_issue_workspace(
            base_reachability_metadata_path,
            canonical_metrics_repo_path,
            issue["number"],
            issue_base_commit,
        )
        preflight_info_path = create_preflight_info_dir(worktree_path)

        # The claim makes form rejection exclusive, while the pinned worktree
        # makes every repository lookup independent from the Forge code branch.
        # §FS-forge-run-requirements.3
        failure_stage = "issue-form check"
        form_verdict = check_issue_form(issue, label, worktree_path)
        if form_verdict.rejection is not None:
            rejection_succeeded = reject_issue_form(issue, form_verdict.rejection)
            if not rejection_succeeded:
                revert_issue_claim(
                    item_id,
                    issue["number"],
                    f"issue-form rule '{form_verdict.rejection.rule}' could not close the issue",
                )
            return None
        if not form_verdict.accepted:
            revert_issue_claim(
                item_id,
                issue["number"],
                "issue-form check was undecided",
            )
            return None

        failure_stage = "post-claim preparation"
        not_for_native_image: bool = maybe_handle_not_for_native_image_issue(issue, worktree_path)
        claim_metadata: Optional[tuple[str, str | None, str | None]] = (
            None
            if not_for_native_image
            else build_claim_metadata(issue, label, worktree_path)
        )
        continuation_marker: ContinuationMarker | None = (
            resolve_issue_continuation_marker(
                issue,
                label,
                claim_metadata[0],
                worktree_path,
            )
            if claim_metadata is not None
            else None
        )

        if not_for_native_image:
            revert_issue_claim(
                item_id,
                issue["number"],
                "artifact is marked not-for-native-image",
            )
            return None
        if claim_metadata is None:
            revert_issue_claim(
                item_id,
                issue["number"],
                "issue-form accepted but claim metadata could not be built",
            )
            return None

        issue_coordinates, current_coordinates, new_version = claim_metadata
        if issue_is_resumable(issue) and continuation_marker is None:
            log_stage(
                "continuation",
                (
                    f"Skipping issue #{issue['number']}: label '{LABEL_RESUMABLE}' is present, "
                    "but no valid continuation marker was found on a preserved branch."
                ),
            )
            revert_issue_claim(
                item_id,
                issue["number"],
                "resumable issue has no valid continuation marker",
            )
            return None

        failure_stage = "chunked-dynamic-access setup"
        chunked_exhaust_report = resolve_chunked_dynamic_access_exhaust_report(
            issue,
            worktree_path,
            issue_coordinates,
            continuation_marker,
        )

        failure_stage = "claim setup"
        if continuation_marker is not None and not checkout_continuation_branch(
                worktree_path,
                continuation_marker,
                issue_base_commit,
        ):
            continuation_marker = None
        if chunked_exhaust_report is not None:
            verify_chunked_dynamic_access_base_contains_published_commit(
                chunked_exhaust_report,
                worktree_path,
            )

        claimed_issue = ClaimedIssue(
            issue=issue,
            label=label,
            item_id=item_id,
            base_reachability_metadata_path=base_reachability_metadata_path,
            worktree_path=worktree_path,
            scratch_metrics_repo_path=scratch_metrics_repo_path,
            issue_coordinates=issue_coordinates,
            issue_base_commit=issue_base_commit,
            current_coordinates=current_coordinates,
            new_version=new_version,
            preflight_info_path=preflight_info_path,
            continuation_marker=continuation_marker,
        )
        handoff_complete = True
        return claimed_issue
    except BaseException as exc:
        if failure_stage == "chunked-dynamic-access setup":
            print(
                f"ERROR: Cannot resume chunked-dynamic-access issue #{issue['number']}: {exc}",
                file=sys.stderr,
            )
        else:
            print(
                f"ERROR: Issue #{issue['number']} {failure_stage} failed: {exc!r}",
                file=sys.stderr,
            )
        revert_issue_claim(
            item_id,
            issue["number"],
            f"{failure_stage} interrupted by Ctrl+C" if is_interrupt_exception(exc)
            else f"{failure_stage} failure ({type(exc).__name__})",
        )
        if isinstance(exc, Exception):
            return None
        raise
    finally:
        if not handoff_complete:
            cleanup_claim_preparation_workspace(
                base_reachability_metadata_path,
                worktree_path,
                preflight_info_path,
            )


def build_fixture_claimed_issue(
        issue: dict,
        label: str,
        base_reachability_metadata_path: str,
        canonical_metrics_repo_path: str,
) -> Optional[ClaimedIssue]:
    """Prepare a fixture issue without simulating GitHub claim mechanics.

    §AR-forge-control-plane
    """
    if not is_fixture_testing_enabled():
        raise RuntimeError("Fixture issue preparation requires fixture testing mode")

    issue_number = issue["number"]
    try:
        item_id = require_fixture_github_state().get_issue_project_item_id(issue_number)
    except Exception:
        item_id = f"fixture-project-item-{issue_number}"

    try:
        worktree_path, scratch_metrics_repo_path = create_issue_workspace(
            base_reachability_metadata_path,
            canonical_metrics_repo_path,
            issue_number,
        )
        preflight_info_path = create_preflight_info_dir(worktree_path)
        log_stage(
            "fixture-worktree",
            (
                f"Created isolated fixture worktree for issue #{issue_number}: "
                f"worktree={worktree_path}, metrics={scratch_metrics_repo_path}, "
                f"preflight={preflight_info_path}"
            ),
        )
    except BaseException as exc:
        if isinstance(exc, Exception):
            print(
                f"ERROR: Failed to prepare fixture workspace for issue #{issue_number}: {exc!r}",
                file=sys.stderr,
            )
            return None
        raise

    try:
        require_fixture_github_state().prepare_issue_worktree(issue_number, label, worktree_path)
        # Fixture masking rewinds the index inside the worktree, so the gate is
        # decided against the repository state this run actually uses.
        # §FS-forge-run-requirements.3 §AR-forge-workflow-pipeline
        form_verdict = check_issue_form(issue, label, worktree_path)
        if form_verdict.rejection is not None:
            reject_issue_form(issue, form_verdict.rejection)
        claim_metadata = (
            build_claim_metadata(issue, label, worktree_path)
            if form_verdict.accepted
            else None
        )
    except BaseException as exc:
        if isinstance(exc, Exception):
            print(
                f"ERROR: Failed to prepare fixture issue #{issue_number}: {exc!r}",
                file=sys.stderr,
            )
            try:
                remove_worktree(base_reachability_metadata_path, worktree_path)
            except Exception as cleanup_exc:
                print(
                    f"ERROR: Failed to clean up fixture worktree {worktree_path}: {cleanup_exc!r}",
                    file=sys.stderr,
                )
            return None
        raise
    if claim_metadata is None:
        try:
            remove_worktree(base_reachability_metadata_path, worktree_path)
        except Exception as cleanup_exc:
            print(
                f"ERROR: Failed to clean up fixture worktree {worktree_path}: {cleanup_exc!r}",
                file=sys.stderr,
            )
        return None

    issue_coordinates, current_coordinates, new_version = claim_metadata
    continuation_marker = load_continuation_marker(continuation_marker_path(worktree_path))
    return ClaimedIssue(
        issue=issue,
        label=label,
        item_id=item_id,
        base_reachability_metadata_path=base_reachability_metadata_path,
        worktree_path=worktree_path,
        scratch_metrics_repo_path=scratch_metrics_repo_path,
        issue_coordinates=issue_coordinates,
        issue_base_commit=resolve_git_commit(worktree_path, "HEAD"),
        current_coordinates=current_coordinates,
        new_version=new_version,
        preflight_info_path=preflight_info_path,
        continuation_marker=continuation_marker,
    )
