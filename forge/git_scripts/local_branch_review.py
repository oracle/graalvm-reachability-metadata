# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Cold pre-push review of a verified publication branch (§FS-local-branch-review).

The verdict and finding records live in `local_review_records.py` and the
isolated reviewer session mechanics in `local_review_session.py`; both are
re-exported here.
"""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import uuid
from dataclasses import dataclass
from typing import Any

from ai_workflows.agents.agent_runtime import (
    AgentRunResult,
    AgentSelection,
    analysis_agent_run,
    get_analysis_agent,
)
from git_scripts.common_git import gh, gh_json
from git_scripts.local_review_records import (
    FINDINGS_PREAMBLE,
    FINDINGS_TITLE,
    LOCAL_REVIEW_METRICS_KEY,
    LocalBranchReviewOutcome,
    LocalReviewVerdict,
    ReviewExecution,
    UNAVAILABLE_FINDING_BODY,
    UNAVAILABLE_FINDING_TITLE,
    _load_persisted_outcome,
    _log_review,
    _persist_outcome,
    _record_outcome_finding,
)
from git_scripts.local_review_session import (
    MAX_REVIEW_TEXT_CHARS,
    REVIEW_SKILLS_BY_TASK_TYPE,
    REVIEW_WORKTREE_DIRNAME,
    _build_review_prompt,
    _commit_reviewer_edits,
    _create_review_worktree,
    _git_stdout,
    _read_verdict,
    _remove_review_worktree,
    _write_evidence,
    _write_unavailable_log,
)
from git_scripts.review_finalization import (
    finalization_receipt_matches,
    publishable_tree_digest,
)
from utility_scripts.local_ci_verification import (
    FINDINGS_RELATIVE_PATH,
    LocalCIVerificationError,
    LocalCIVerificationResult,
    run_local_ci_verification,
    write_verification_metrics,
)
from utility_scripts.task_logs import (
    build_timestamped_task_log_path,
    display_log_path,
    new_session_id,
)

LOCAL_REVIEW_TIMEOUT_SECONDS: int = 3600


def review_model_name(model: str | None = None) -> str:
    """Return the effective centralized analysis model used for local review."""
    selection: AgentSelection = get_analysis_agent()
    if model and not os.environ.get("FORGE_ANALYSIS_MODEL"):
        return model
    return selection.model

def _resolve_infrastructure_issue(verdict: LocalReviewVerdict) -> LocalReviewVerdict:
    """Open or reuse the shared-infrastructure issue named by the reviewer."""
    evidence = verdict.infrastructure_issue
    if evidence is None:
        return verdict
    candidates = gh_json(
        "issue", "list",
        "--repo", "oracle/graalvm-reachability-metadata",
        "--state", "open",
        "--search", f"{evidence['title']} in:title",
        "--limit", "100",
        "--json", "number,title,url",
    )
    match = next(
        (
            candidate for candidate in candidates
            if isinstance(candidate, dict) and candidate.get("title") == evidence["title"]
        ),
        None,
    )
    if match is None:
        created = json.loads(gh(
            "api",
            "--method", "POST",
            "/repos/oracle/graalvm-reachability-metadata/issues",
            "-f", f"title={evidence['title']}",
            "-f", f"body={evidence['body']}",
        ).stdout)
        issue_number = int(created["number"])
        issue_url = str(created["html_url"])
    else:
        issue_number = int(match["number"])
        issue_url = str(match["url"])
    link = f"Infrastructure issue: {issue_url} (#{issue_number})"
    return LocalReviewVerdict(
        decision=verdict.decision,
        action=verdict.action,
        review_comment=f"{verdict.review_comment.rstrip()}\n\n{link}",
        finding_title=verdict.finding_title,
        finding_body=f"{verdict.finding_body.rstrip()}\n\n{link}",
        fix_note=verdict.fix_note,
        infrastructure_issue=evidence,
    )



def run_local_branch_review(
        *,
        repo_path: str,
        coordinates: str,
        base_commit: str,
        task_type: str,
        local_ci_verification: LocalCIVerificationResult,
        descriptor_input: Any,
        metrics_repo_path: str | None = None,
        model: str | None = None,
) -> LocalBranchReviewOutcome:
    """Review once, verify actual edits, and always return a publishable outcome.

    The reviewer owns finalization and semantic repair in one cold pass. Forge
    replays only the cross-cutting gate without accepting a post-verdict mutation.
    §FS-local-branch-review
    """
    persisted: LocalBranchReviewOutcome | None = _load_persisted_outcome(
        metrics_repo_path,
        local_ci_verification,
        descriptor_input,
    )
    if persisted is not None:
        _log_review("Reusing the persisted pre-push review verdict", indent_level=1)
        return persisted

    review_model: str = review_model_name(model)
    base_sha: str = _git_stdout(repo_path, ["rev-parse", f"{base_commit}^{{commit}}"])
    verified_sha: str = _git_stdout(repo_path, ["rev-parse", "HEAD"])
    _log_review(
        f"Reviewing {coordinates} at {verified_sha[:12]} against {base_sha[:12]} "
        f"with {review_model}"
    )

    execution: ReviewExecution = _request_review(
        repo_path=repo_path,
        coordinates=coordinates,
        base_sha=base_sha,
        verified_sha=verified_sha,
        task_type=task_type,
        review_model=review_model,
        local_ci_verification=local_ci_verification,
        descriptor_input=descriptor_input,
    )
    verdict: LocalReviewVerdict = (
        execution.verdict
        if execution.verdict is not None
        else LocalReviewVerdict(
            decision="rejected",
            action="human-intervention",
            review_comment=UNAVAILABLE_FINDING_BODY,
            finding_title=UNAVAILABLE_FINDING_TITLE,
            finding_body=UNAVAILABLE_FINDING_BODY,
            fix_note="",
        )
    )
    verdict = _resolve_infrastructure_issue(verdict)
    outcome = LocalBranchReviewOutcome(
        model=review_model,
        session_id=execution.session_id,
        local_ci_verification=local_ci_verification,
        verdict=verdict,
        changed_paths=list(execution.changed_paths),
    )

    if execution.changed_paths:
        _log_review(
            f"Reviewer changed {len(execution.changed_paths)} path(s); "
            "re-running only the pre-publication gate without repairs",
            indent_level=1,
        )
        _verify_reviewer_edits(
            repo_path=repo_path,
            coordinates=coordinates,
            base_commit=base_commit,
            verified_sha=verified_sha,
            metrics_repo_path=metrics_repo_path,
            original_verification=local_ci_verification,
            outcome=outcome,
        )
    else:
        _log_review("Reviewer made no branch edits; verification is not repeated", indent_level=1)

    _record_outcome_finding(
        repo_path=repo_path,
        coordinates=coordinates,
        descriptor_input=descriptor_input,
        outcome=outcome,
    )
    _persist_outcome(
        metrics_repo_path,
        descriptor_input,
        outcome,
    )
    return outcome


@dataclass(frozen=True)
class PublishableTree:
    """The publishable content of the review worktree at one moment."""

    head: str
    digest: str
    status: tuple[str, ...]


def _capture_publishable_tree(repo_path: str) -> PublishableTree:
    """Snapshot only the content the branch would publish. §FS-local-branch-review"""
    return PublishableTree(
        head=_git_stdout(repo_path, ["rev-parse", "HEAD"]),
        digest=publishable_tree_digest(repo_path),
        status=tuple(_git_stdout(repo_path, ["status", "--porcelain"]).splitlines()),
    )


def _describe_tree_change(before: PublishableTree, after: PublishableTree) -> str:
    """Name what the gate changed so the finding diagnoses itself. §FS-local-branch-review"""
    details: list[str] = []
    if before.head != after.head:
        details.append(f"HEAD moved from {before.head[:12]} to {after.head[:12]}")
    details.extend(f"appeared: {line}" for line in after.status if line not in before.status)
    details.extend(f"disappeared: {line}" for line in before.status if line not in after.status)
    if not details:
        details.append("publishable content changed without a new `git status` entry")
        details.extend(f"still present: {line}" for line in after.status)
    return "; ".join(details)


def _verify_reviewer_edits(
        *,
        repo_path: str,
        coordinates: str,
        base_commit: str,
        verified_sha: str,
        metrics_repo_path: str | None,
        original_verification: LocalCIVerificationResult,
        outcome: LocalBranchReviewOutcome,
) -> None:
    """Replay the cross-cutting gate without accepting a post-verdict mutation."""
    reviewed_tree: PublishableTree = _capture_publishable_tree(repo_path)
    try:
        outcome.local_ci_verification = run_local_ci_verification(
            repo_path=repo_path,
            coordinates=coordinates,
            base_commit=base_commit,
            metrics_repo_path=metrics_repo_path,
            max_fixup_attempts=0,
        )
    except LocalCIVerificationError as error:
        _reset_reviewer_edits(
            repo_path, verified_sha, metrics_repo_path, original_verification,
        )
        outcome.local_ci_verification = original_verification
        _reject_failed_repair(
            outcome, error.result.failure_gate or "pre-publication-gate",
        )
        return

    gate_tree: PublishableTree = _capture_publishable_tree(repo_path)
    if gate_tree.digest == reviewed_tree.digest:
        return
    change_detail: str = _describe_tree_change(reviewed_tree, gate_tree)
    _log_review(
        f"Pre-publication gate changed the publishable tree: {change_detail}",
        indent_level=1,
    )
    _reset_reviewer_edits(
        repo_path, verified_sha, metrics_repo_path, original_verification,
    )
    outcome.local_ci_verification = original_verification
    _reject_failed_repair(
        outcome, "pre-publication-gate-mutated-reviewed-tree", change_detail,
    )


def _reject_failed_repair(
        outcome: LocalBranchReviewOutcome,
        failed_step: str,
        change_detail: str = "",
) -> None:
    """Ensure the descriptor decision describes the restored verified tree."""
    verdict: LocalReviewVerdict = outcome.verdict
    observed: str = f" ({change_detail})" if change_detail else ""
    detail: str = (
        f"The attempted review repair did not pass {failed_step}{observed}; Forge "
        "restored the last verified tree, where this finding remains unresolved."
    )
    finding_title: str = verdict.finding_title or "Pre-push review repair did not verify"
    finding_body: str = verdict.finding_body
    if finding_body:
        finding_body = f"{finding_body.rstrip()}\n\n{detail}"
    else:
        finding_body = detail
    outcome.verdict = LocalReviewVerdict(
        decision="rejected",
        action=verdict.action or "human-intervention",
        review_comment=f"{verdict.review_comment.rstrip()}\n\n{detail}",
        finding_title=finding_title,
        finding_body=finding_body,
        fix_note=verdict.fix_note,
    )


def _reset_reviewer_edits(
        repo_path: str,
        verified_sha: str,
        metrics_repo_path: str | None,
        original_verification: LocalCIVerificationResult,
) -> None:
    """Restore the tree the gate verified and its matching verification record."""
    subprocess.run(["git", "reset", "--hard", verified_sha], cwd=repo_path, check=True)
    write_verification_metrics(metrics_repo_path, original_verification)
    _log_review(
        f"Post-review checks did not pass; restored verified commit {verified_sha[:12]}",
        indent_level=1,
    )


def _request_review(
        *,
        repo_path: str,
        coordinates: str,
        base_sha: str,
        verified_sha: str,
        task_type: str,
        review_model: str,
        local_ci_verification: LocalCIVerificationResult,
        descriptor_input: Any,
) -> ReviewExecution:
    """Run the isolated reviewer, transfer its Git edits, and return its verdict."""
    session_id: str = new_session_id()
    unavailable_log_path: str = build_timestamped_task_log_path(
        "local-review", coordinates, "local_branch_review"
    )
    worktree_path: str | None = _create_review_worktree(repo_path, verified_sha)
    if worktree_path is None:
        _write_unavailable_log(
            unavailable_log_path, "Could not create detached review worktree.",
        )
        _log_review(
            f"review session {session_id}; log: {display_log_path(unavailable_log_path)}",
            indent_level=1,
        )
        return ReviewExecution(None, session_id)

    try:
        evidence_dir: str = os.path.join(
            worktree_path, f".forge-local-review-{uuid.uuid4().hex[:8]}"
        )
        os.makedirs(evidence_dir)
        evidence_path: str = os.path.join(evidence_dir, "review-evidence.json")
        verdict_path: str = os.path.join(evidence_dir, "verdict.json")
        finalization_receipt_path: str = os.path.join(
            evidence_dir, "finalization-receipt.json",
        )
        _write_evidence(
            evidence_path,
            coordinates,
            task_type,
            local_ci_verification,
            descriptor_input,
        )
        prompt: str = _build_review_prompt(
            coordinates=coordinates,
            base_sha=base_sha,
            verified_sha=verified_sha,
            task_type=task_type,
            evidence_path=evidence_path,
            verdict_path=verdict_path,
            finalization_receipt_path=finalization_receipt_path,
        )
        selection: AgentSelection = get_analysis_agent()
        result: AgentRunResult = analysis_agent_run(
            working_dir=worktree_path,
            context=prompt,
            task_type="local-branch-review",
            library=coordinates,
            timeout=LOCAL_REVIEW_TIMEOUT_SECONDS,
            model=review_model,
            thinking_level="high",
        )
        # The path resolves only on this machine; the identifier is what the
        # descriptor and the pull request carry. §FS-durable-generation-logs
        _log_review(
            f"{selection.backend} review session {session_id}; "
            f"log: {display_log_path(result.log_path)}",
            indent_level=1,
        )
        if result.return_code != 0:
            if result.timed_out:
                detail: str = (
                    f"timed out after {LOCAL_REVIEW_TIMEOUT_SECONDS} seconds"
                )
            else:
                detail = f"exited with code {result.return_code}"
            _log_review(
                f"Review {detail}",
                indent_level=1,
            )
            return ReviewExecution(None, session_id)

        verdict: LocalReviewVerdict | None = _read_verdict(verdict_path)
        if verdict is None:
            return ReviewExecution(None, session_id)
        finalization_verified: bool = (
            task_type == "not-for-native-image"
            or finalization_receipt_matches(
                finalization_receipt_path,
                worktree_path,
                coordinates,
                base_sha,
            )
        )
        shutil.rmtree(evidence_dir, ignore_errors=True)
        changed_paths: list[str] = _commit_reviewer_edits(worktree_path, verified_sha)
        if (
                changed_paths
                and verdict.decision == "approved"
                and not finalization_verified
        ):
            _log_review(
                "Reviewer edits do not have a stable finalization receipt",
                indent_level=1,
            )
            return ReviewExecution(None, session_id)
        if changed_paths and (
                not verdict.finding_title.strip() or not verdict.fix_note.strip()
        ):
            _log_review(
                "Reviewer edited the tree without a complete finding and fix note",
                indent_level=1,
            )
            return ReviewExecution(None, session_id)
        if verdict.decision == "rejected":
            return ReviewExecution(verdict, session_id)
        if changed_paths:
            review_commit: str = _git_stdout(worktree_path, ["rev-parse", "HEAD"])
            subprocess.run(["git", "cherry-pick", review_commit], cwd=repo_path, check=True)
        return ReviewExecution(verdict, session_id, changed_paths)
    finally:
        _remove_review_worktree(repo_path, worktree_path)
