# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Cold pre-push review of a verified publication branch (§FS-local-branch-review)."""

from __future__ import annotations

import copy
import json
import os
import shlex
import shutil
import subprocess
import uuid
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any

from ai_workflows.agents.agent_runtime import (
    AgentRunResult,
    AgentSelection,
    analysis_agent_run,
    get_analysis_agent,
)
from git_scripts.common_git import gh, gh_json, parse_coordinate_parts, stage_and_commit
from git_scripts.review_finalization import finalization_receipt_matches
from utility_scripts.local_ci_verification import (
    FINDINGS_RELATIVE_PATH,
    LocalCIVerificationError,
    LocalCIVerificationResult,
    run_local_ci_verification,
    write_verification_metrics,
)
from utility_scripts.metrics_writer import read_pending_metrics, write_pending_metrics
from utility_scripts.stage_logger import log_stage
from utility_scripts.task_logs import build_timestamped_task_log_path, display_log_path

LOCAL_REVIEW_TIMEOUT_SECONDS: int = 3600
LOCAL_REVIEW_METRICS_KEY: str = "local_review"
REVIEW_WORKTREE_DIRNAME: str = "forge_prepublication_review_worktrees"
FINDINGS_TITLE: str = "# Forge pre-push review findings"
FINDINGS_PREAMBLE: str = (
    "Rendered by Forge from the pre-push branch review of §FS-local-branch-review.\n"
    "Newest entry first; every finding is recorded, including one the reviewer repaired."
)
UNAVAILABLE_FINDING_TITLE: str = "Pre-push review unavailable"
UNAVAILABLE_FINDING_BODY: str = (
    "Forge could not obtain a readable pre-push review verdict. This records a review "
    "availability problem, not a reviewer finding against the branch."
)
MAX_REVIEW_TEXT_CHARS: int = 20_000

REVIEW_SKILLS_BY_TASK_TYPE: dict[str, str] = {
    "library-new-request": "review-library-new-request",
    "library-update-request": "review-library-update-request",
    "fixes-javac-fail": "review-fixes-javac-fail",
    "fixes-java-run-fail": "review-fixes-java-run-fail",
    "fixes-native-image-run-fail": "review-fixes-native-image-run-fail",
    "not-for-native-image": "review-library-new-request",
}

@dataclass(frozen=True)
class LocalReviewVerdict:
    """The authoritative reviewer's structured decision."""

    decision: str
    review_comment: str
    finding_title: str
    finding_body: str
    fix_note: str
    action: str | None = None
    infrastructure_issue: dict[str, str] | None = None


@dataclass(frozen=True)
class ReviewExecution:
    """The observable result of the isolated reviewer process."""

    verdict: LocalReviewVerdict | None
    session_log_path: str
    changed_paths: list[str] = field(default_factory=list)


@dataclass
class LocalBranchReviewOutcome:
    """Reviewer decision plus the evidence Forge persists."""

    model: str
    session_log_path: str
    local_ci_verification: LocalCIVerificationResult
    verdict: LocalReviewVerdict
    changed_paths: list[str] = field(default_factory=list)

    @property
    def is_approved(self) -> bool:
        return self.verdict.decision == "approved"

    @property
    def review_comment(self) -> str:
        return self.verdict.review_comment

    def to_descriptor_payload(self) -> dict[str, Any]:
        """Return the descriptor object consumed by trusted automation."""
        payload: dict[str, Any] = {
            "decision": self.verdict.decision,
            "review_comment": self.verdict.review_comment,
            "finding_title": self.verdict.finding_title,
            "finding_body": self.verdict.finding_body,
            "fix_note": self.verdict.fix_note,
            "model": self.model,
            "session_log_path": self.session_log_path,
            "changed_paths": list(self.changed_paths[:200]),
        }
        if self.verdict.action is not None:
            payload["action"] = self.verdict.action
        return payload

    @classmethod
    def from_descriptor_payload(
            cls,
            payload: dict[str, Any],
            local_ci_verification: LocalCIVerificationResult,
    ) -> LocalBranchReviewOutcome:
        """Reconstruct a persisted verdict without re-running the reviewer."""
        verdict = LocalReviewVerdict(
            decision=str(payload["decision"]),
            action=(
                str(payload["action"])
                if isinstance(payload.get("action"), str)
                else None
            ),
            review_comment=str(payload["review_comment"]),
            finding_title=str(payload["finding_title"]),
            finding_body=str(payload["finding_body"]),
            fix_note=str(payload["fix_note"]),
        )
        return cls(
            model=str(payload["model"]),
            session_log_path=str(payload["session_log_path"]),
            local_ci_verification=local_ci_verification,
            verdict=verdict,
            changed_paths=[str(path) for path in payload.get("changed_paths", [])],
        )


def _log_review(message: str, indent_level: int = 0) -> None:
    log_stage("local-review", message, indent_level=indent_level)


def _git_stdout(repo_path: str, args: list[str]) -> str:
    return subprocess.check_output(["git", *args], cwd=repo_path, text=True).strip()


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
        session_log_path=execution.session_log_path,
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


def _tree_matches_commit(repo_path: str, expected_sha: str) -> bool:
    """Keep the post-verdict gate read-only. §FS-local-branch-review"""
    current_sha: str = _git_stdout(repo_path, ["rev-parse", "HEAD"])
    status: str = _git_stdout(repo_path, ["status", "--porcelain"])
    return current_sha == expected_sha and not status


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
    reviewed_sha: str = _git_stdout(repo_path, ["rev-parse", "HEAD"])
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

    if not _tree_matches_commit(repo_path, reviewed_sha):
        _reset_reviewer_edits(
            repo_path, verified_sha, metrics_repo_path, original_verification,
        )
        outcome.local_ci_verification = original_verification
        _reject_failed_repair(
            outcome, "pre-publication-gate-mutated-reviewed-tree",
        )


def _reject_failed_repair(
        outcome: LocalBranchReviewOutcome,
        failed_step: str,
) -> None:
    """Ensure the descriptor decision describes the restored verified tree."""
    verdict: LocalReviewVerdict = outcome.verdict
    detail: str = (
        f"The attempted review repair did not pass {failed_step}; Forge restored "
        "the last verified tree, where this finding remains unresolved."
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
    unavailable_log_path: str = build_timestamped_task_log_path(
        "local-review", coordinates, "local_branch_review"
    )
    worktree_path: str | None = _create_review_worktree(repo_path, verified_sha)
    if worktree_path is None:
        _write_unavailable_log(
            unavailable_log_path, "Could not create detached review worktree.",
        )
        return ReviewExecution(None, display_log_path(unavailable_log_path))

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
        displayed_log_path: str = display_log_path(result.log_path)
        _log_review(
            f"{selection.backend} review log: {displayed_log_path}",
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
            return ReviewExecution(None, displayed_log_path)

        verdict: LocalReviewVerdict | None = _read_verdict(verdict_path)
        if verdict is None:
            return ReviewExecution(None, displayed_log_path)
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
            return ReviewExecution(None, displayed_log_path)
        if changed_paths and (
                not verdict.finding_title.strip() or not verdict.fix_note.strip()
        ):
            _log_review(
                "Reviewer edited the tree without a complete finding and fix note",
                indent_level=1,
            )
            return ReviewExecution(None, displayed_log_path)
        if verdict.decision == "rejected":
            return ReviewExecution(verdict, displayed_log_path)
        if changed_paths:
            review_commit: str = _git_stdout(worktree_path, ["rev-parse", "HEAD"])
            subprocess.run(["git", "cherry-pick", review_commit], cwd=repo_path, check=True)
        return ReviewExecution(verdict, displayed_log_path, changed_paths)
    finally:
        _remove_review_worktree(repo_path, worktree_path)


def _commit_reviewer_edits(worktree_path: str, verified_sha: str) -> list[str]:
    """Stage and commit actual review-worktree edits, excluding the findings record."""
    reviewer_head: str = _git_stdout(worktree_path, ["rev-parse", "HEAD"])
    if reviewer_head != verified_sha:
        # Normalize an accidental reviewer commit back to worktree edits so
        # Forge remains the only party that stages and commits the repair.
        subprocess.run(["git", "reset", "--mixed", verified_sha], cwd=worktree_path, check=True)
    subprocess.run(["git", "add", "-A"], cwd=worktree_path, check=True)
    findings_path: str = os.path.join(worktree_path, FINDINGS_RELATIVE_PATH)
    tracked_finding = subprocess.run(
        ["git", "ls-files", "--error-unmatch", "--", FINDINGS_RELATIVE_PATH],
        cwd=worktree_path,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        check=False,
    ).returncode == 0
    if tracked_finding:
        subprocess.run(
            [
                "git", "restore", "--source=HEAD", "--staged", "--worktree", "--",
                FINDINGS_RELATIVE_PATH,
            ],
            cwd=worktree_path,
            check=True,
        )
    elif os.path.isfile(findings_path):
        os.remove(findings_path)
        subprocess.run(
            ["git", "restore", "--staged", "--", FINDINGS_RELATIVE_PATH],
            cwd=worktree_path,
            check=False,
        )

    changed_output: str = subprocess.check_output(
        ["git", "diff", "--cached", "--name-only", "-z", "--diff-filter=ACMRTD"],
        cwd=worktree_path,
        text=True,
    )
    changed_paths: list[str] = [path for path in changed_output.split("\0") if path]
    if changed_paths:
        subprocess.run(
            ["git", "commit", "-m", "Apply pre-push reviewer edits"],
            cwd=worktree_path,
            check=True,
        )
    return changed_paths


def _create_review_worktree(repo_path: str, head_sha: str) -> str | None:
    """Detach a cold worktree at the verified commit from the shared object store."""
    worktree_root: str = os.path.join(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
        "local_repositories",
        REVIEW_WORKTREE_DIRNAME,
    )
    os.makedirs(worktree_root, exist_ok=True)
    worktree_path: str = os.path.join(worktree_root, f"review-{uuid.uuid4().hex[:8]}")
    result = subprocess.run(
        ["git", "worktree", "add", "--detach", worktree_path, head_sha],
        cwd=repo_path,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=False,
    )
    if result.returncode != 0:
        _log_review(
            f"Failed to create the isolated review worktree: {result.stdout.strip()}",
            indent_level=1,
        )
        return None
    return worktree_path


def _remove_review_worktree(repo_path: str, worktree_path: str) -> None:
    subprocess.run(
        ["git", "worktree", "remove", "--force", worktree_path],
        cwd=repo_path,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=False,
    )
    if os.path.isdir(worktree_path):
        shutil.rmtree(worktree_path, ignore_errors=True)


def _write_evidence(
        evidence_path: str,
        coordinates: str,
        task_type: str,
        local_ci_verification: LocalCIVerificationResult,
        descriptor_input: Any,
) -> None:
    """Write local gate records and resolved descriptor statistics for the reviewer."""
    evidence: dict[str, Any] = {
        "coordinates": coordinates,
        "task_type": task_type,
        "local_ci_verification": local_ci_verification.to_metrics(),
    }
    render = getattr(descriptor_input, "render", None)
    if isinstance(render, dict):
        evidence["render"] = copy.deepcopy(render)
    run_metrics = getattr(descriptor_input, "run_metrics", None)
    if isinstance(run_metrics, dict):
        evidence_run_metrics: dict[str, Any] = copy.deepcopy(run_metrics)
        evidence_run_metrics.pop(LOCAL_REVIEW_METRICS_KEY, None)
        evidence["run_metrics"] = evidence_run_metrics
    for attribute in ("issue_number", "template_type", "status", "previous_coordinates"):
        evidence[attribute] = getattr(descriptor_input, attribute, None)
    with open(evidence_path, "w", encoding="utf-8") as evidence_file:
        json.dump(evidence, evidence_file, indent=2, sort_keys=True, default=str)
        evidence_file.write("\n")


def _read_verdict(verdict_path: str) -> LocalReviewVerdict | None:
    """Read one complete verdict, treating malformed output as unavailable."""
    if not os.path.isfile(verdict_path):
        _log_review("Review wrote no verdict file", indent_level=1)
        return None
    try:
        with open(verdict_path, "r", encoding="utf-8") as verdict_file:
            payload = json.load(verdict_file)
    except (OSError, json.JSONDecodeError) as error:
        _log_review(f"Review verdict could not be read: {error}", indent_level=1)
        return None
    if not isinstance(payload, dict) or payload.get("decision") not in {
        "approved", "rejected",
    }:
        _log_review("Review verdict did not carry a valid decision", indent_level=1)
        return None

    decision: str = str(payload["decision"])
    action: Any = payload.get("action")
    if decision == "approved" and "action" in payload:
        _log_review("Approved review verdict carried an action", indent_level=1)
        return None
    if decision == "rejected" and action not in {"human-intervention", "close"}:
        _log_review("Rejected review verdict did not carry a valid action", indent_level=1)
        return None

    infrastructure_issue: dict[str, str] | None = None
    raw_infrastructure_issue = payload.get("infrastructure_issue")
    if raw_infrastructure_issue is not None:
        if (
                decision != "rejected"
                or action != "human-intervention"
                or not isinstance(raw_infrastructure_issue, dict)
        ):
            _log_review("Review verdict carried invalid infrastructure evidence", indent_level=1)
            return None
        issue_title = raw_infrastructure_issue.get("title")
        issue_body = raw_infrastructure_issue.get("body")
        if (
                not isinstance(issue_title, str)
                or not issue_title.strip()
                or not isinstance(issue_body, str)
                or not issue_body.strip()
        ):
            _log_review("Infrastructure evidence requires a title and body", indent_level=1)
            return None
        infrastructure_issue = {
            "title": issue_title.strip(),
            "body": issue_body.strip(),
        }

    values: dict[str, str] = {}
    for key in ("review_comment", "finding_title", "finding_body", "fix_note"):
        value = payload.get(key)
        if not isinstance(value, str) or len(value) > MAX_REVIEW_TEXT_CHARS:
            _log_review(f"Review verdict carried an invalid {key}", indent_level=1)
            return None
        values[key] = value
    if not values["review_comment"].strip():
        _log_review("Review verdict carried no review comment", indent_level=1)
        return None
    if bool(values["finding_title"].strip()) != bool(values["finding_body"].strip()):
        _log_review("Review verdict carried an incomplete finding", indent_level=1)
        return None
    if decision == "rejected" and not values["finding_title"].strip():
        _log_review("Review rejected the branch without a finding", indent_level=1)
        return None
    return LocalReviewVerdict(
        decision=decision,
        action=str(action) if isinstance(action, str) else None,
        review_comment=values["review_comment"],
        finding_title=values["finding_title"],
        finding_body=values["finding_body"],
        fix_note=values["fix_note"],
        infrastructure_issue=infrastructure_issue,
    )


def _build_review_finalization_instructions(
        coordinates: str,
        task_type: str,
        base_sha: str,
        finalization_receipt_path: str,
) -> list[str]:
    """Give one reviewer every finalization duty. §FS-local-branch-review"""
    if task_type == "not-for-native-image":
        return [
            "Before writing the verdict, ensure the marker and descriptor inputs are final. "
            "This route has no library finalization callback or second semantic agent.",
            "",
        ]
    command: str = shlex.join([
        "python3",
        "-m",
        "git_scripts.review_finalization",
        "--repo-path",
        ".",
        "--coordinates",
        coordinates,
        "--base-commit",
        base_sha,
        "--receipt-path",
        finalization_receipt_path,
    ])
    return [
        "If you edit the contribution, run the exact Forge-owned finalization command after your last "
        "manual edit. This is the only semantic repair pass; it disables nested agents and includes "
        "all three test lanes, foreign-metadata routing, deterministic allowed-package updates, "
        "metadata and style checks, generated-test validity, and library statistics:",
        f"- `PYTHONPATH=forge {command}`",
        "If it fails, use its logs and resulting diff to repair the contribution, then run the exact "
        "command again. A run that updates the publishable tree deliberately exits nonzero: inspect "
        "those changes as part of the review and rerun it. Write the verdict only after the command "
        "exits successfully, which proves a complete pass made no further publishable change. Do not "
        "edit the contribution after that successful run. Forge will not run finalization again.",
        "",
    ]


def _build_review_prompt(
        *,
        coordinates: str,
        base_sha: str,
        verified_sha: str,
        task_type: str,
        evidence_path: str,
        verdict_path: str,
        finalization_receipt_path: str,
) -> str:
    """Build the authoritative cold review-and-repair prompt from local evidence."""
    skill: str = REVIEW_SKILLS_BY_TASK_TYPE[task_type]
    return "\n".join([
        f"Review the branch Forge is about to push for {coordinates}.",
        "There is no pull request yet. Use these local equivalents:",
        f"- Review `git diff {base_sha} {verified_sha}`; it is the eventual PR diff minus the descriptor.",
        f"- Apply every relevant enumerated rule in `skills/{skill}/SKILL.md`.",
        "- Apply the first matching disposition in §root/FS-contribution-contract.5.",
        f"- Read local gate records and resolved render statistics from `{evidence_path}`.",
        "- Ignore skill steps about `gh`, PR labels, reviews, merges, and remote CI status.",
        "",
        "This is a detached worktree and a cold session. Do not run `gh`, push, commit, or edit "
        "forge/FINDINGS.md.",
        "",
        "Repair a violation when the complete fix stays inside the contribution's allowed files. "
        "If the repaired tree satisfies every rule, approve it. Never edit shared infrastructure "
        "or another coordinate. Reject with action human-intervention for shared infrastructure, "
        "uncertainty, or anything else requiring a maintainer. Reject with action close only when "
        "§root/FS-contribution-contract.5.4 proves the library version is unsupportable.",
        "For a shared infrastructure defect, include infrastructure_issue with a concise "
        "non-empty title and a body containing the cause and reproducible evidence. Forge "
        "will open or reuse that issue and add its link to the recorded finding.",
        "",
        *_build_review_finalization_instructions(
            coordinates,
            task_type,
            base_sha,
            finalization_receipt_path,
        ),
        f"Write exactly one JSON verdict to `{verdict_path}`. This is the only judgment Forge reads:",
        json.dumps({
            "decision": "approved",
            "review_comment": "What you checked and concluded.",
            "finding_title": "Reusable defect title, or empty when no finding.",
            "finding_body": "Rule violation and evidence, or empty when no finding.",
            "fix_note": "What you changed and why, or empty when you made no correction.",
        }, indent=2),
        "",
        "For rejection, set decision to rejected, add action as human-intervention or close, "
        "and provide a non-empty finding title and body. For approval, omit action. Keep a repaired "
        "finding in the verdict even when the repaired tree is approved.",
    ])


def _record_outcome_finding(
        *,
        repo_path: str,
        coordinates: str,
        descriptor_input: Any,
        outcome: LocalBranchReviewOutcome,
) -> None:
    """Record reviewer findings and the fixed outage finding after any checkpoint reset."""
    if outcome.verdict is None:
        title: str = UNAVAILABLE_FINDING_TITLE
        body: str = UNAVAILABLE_FINDING_BODY
    elif outcome.verdict.finding_title:
        title = outcome.verdict.finding_title
        body = outcome.verdict.finding_body
    else:
        return
    _record_finding(
        repo_path=repo_path,
        coordinates=coordinates,
        descriptor_input=descriptor_input,
        title=title,
        body=body,
    )


def _record_finding(
        *,
        repo_path: str,
        coordinates: str,
        descriptor_input: Any,
        title: str,
        body: str,
) -> None:
    """Render a stable newest-first finding entry and commit it."""
    findings_path: str = os.path.join(repo_path, FINDINGS_RELATIVE_PATH)
    entry: str = _render_finding_entry(coordinates, descriptor_input, title, body)
    existing: str = ""
    if os.path.isfile(findings_path):
        with open(findings_path, "r", encoding="utf-8") as findings_file:
            existing = findings_file.read()
    if existing.startswith(FINDINGS_TITLE):
        header, _, entries = existing.partition("\n## ")
        header_block: str = header.rstrip("\n")
        remaining: str = f"## {entries}" if entries else ""
    else:
        header_block = f"{FINDINGS_TITLE}\n\n{FINDINGS_PREAMBLE}"
        remaining = existing.strip()
    updated: str = f"{header_block}\n\n{entry}"
    if remaining:
        updated += f"\n{remaining.rstrip()}\n"
    os.makedirs(os.path.dirname(findings_path), exist_ok=True)
    with open(findings_path, "w", encoding="utf-8") as findings_file:
        findings_file.write(updated)
    stage_and_commit(
        [FINDINGS_RELATIVE_PATH],
        f"Record pre-push review finding for {coordinates}",
        cwd=repo_path,
    )
    _log_review(f"Recorded '{title}' in {FINDINGS_RELATIVE_PATH}", indent_level=1)


def _render_finding_entry(
        coordinates: str,
        descriptor_input: Any,
        title: str,
        body: str,
) -> str:
    """Render one finding only from its reviewer-supplied title and body."""
    group, artifact, version = parse_coordinate_parts(coordinates)
    issue_number = getattr(descriptor_input, "issue_number", None)
    issue_reference: str = f" (#{issue_number})" if issue_number else ""
    date: str = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    return "\n".join([
        f"## {date} — {group}:{artifact}:{version}{issue_reference}",
        "",
        f"**{title}**",
        "",
        body,
        "",
    ])


def _load_persisted_outcome(
        metrics_repo_path: str | None,
        local_ci_verification: LocalCIVerificationResult,
        descriptor_input: Any,
) -> LocalBranchReviewOutcome | None:
    if metrics_repo_path is None:
        return None
    try:
        metrics: dict[str, Any] = read_pending_metrics(metrics_repo_path)
    except (OSError, ValueError, TypeError):
        return None
    state = metrics.get(LOCAL_REVIEW_METRICS_KEY)
    if not isinstance(state, dict):
        return None
    expected_timestamp = getattr(descriptor_input, "timestamp", None)
    if state.get("timestamp") != expected_timestamp:
        return None
    payload = state.get("outcome")
    if not isinstance(payload, dict):
        return None
    try:
        return LocalBranchReviewOutcome.from_descriptor_payload(
            payload, local_ci_verification,
        )
    except (KeyError, TypeError, ValueError):
        return None


def _persist_outcome(
        metrics_repo_path: str | None,
        descriptor_input: Any,
        outcome: LocalBranchReviewOutcome,
) -> None:
    """Stage the verdict with the run's durable in-flight publication data."""
    if metrics_repo_path is None:
        return
    try:
        metrics: dict[str, Any] = read_pending_metrics(metrics_repo_path)
    except (OSError, ValueError, TypeError):
        metrics = {}
    metrics[LOCAL_REVIEW_METRICS_KEY] = {
        "timestamp": getattr(descriptor_input, "timestamp", None),
        "outcome": outcome.to_descriptor_payload(),
    }
    write_pending_metrics(metrics_repo_path, metrics)


def _write_unavailable_log(log_path: str, detail: str) -> None:
    os.makedirs(os.path.dirname(log_path), exist_ok=True)
    with open(log_path, "w", encoding="utf-8") as log_file:
        log_file.write(f"# Pre-push local review unavailable\n\n{detail}\n")
