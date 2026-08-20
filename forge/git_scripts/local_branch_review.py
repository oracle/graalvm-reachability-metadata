# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Pre-push review of a verified publication branch (§FS-local-branch-review).

The branch is reviewed twice: here, against the verified worktree and the local
CI gate records, and again after the push against `gh pr diff` alone
(§FS-automated-pr-review). This phase runs between the pre-publication gate and
the descriptor write (§GIT-shared-publication-pipeline), so its verdict reaches
the descriptor the trusted publisher reads.
"""

from __future__ import annotations

import copy
import json
import os
import shutil
import subprocess
import uuid
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any

from git_scripts.common_git import parse_coordinate_parts, stage_and_commit
from utility_scripts.host_requirements import (
    DEFAULT_REVIEW_MODEL,
    PI_PROVIDER,
    check_pi_authentication,
)
from utility_scripts.local_ci_verification import (
    FINDINGS_RELATIVE_PATH,
    LocalCIVerificationError,
    LocalCIVerificationResult,
    RepairAttempt,
    run_local_ci_verification,
    run_repair_agent,
    write_verification_metrics,
)
from utility_scripts.stage_logger import log_stage
from utility_scripts.task_logs import build_timestamped_task_log_path, display_log_path

# This phase is on the publication critical path, unlike the post-push review, so
# it takes a tighter budget than that reviewer's 1800s. §FS-local-branch-review
LOCAL_REVIEW_TIMEOUT_SECONDS = 900
LOCAL_REVIEW_MODEL_ENVIRONMENT_VARIABLE = "FORGE_LOCAL_REVIEW_MODEL"
REVIEW_WORKTREE_DIRNAME = "forge_prepublication_review_worktrees"
FINDINGS_TITLE = "# Forge pre-push review findings"
FINDINGS_PREAMBLE = (
    "Rendered by Forge from the pre-push branch review of §FS-local-branch-review.\n"
    "Newest entry first; every non-approval is recorded, including one a repair "
    "later cleared."
)
# A degraded review cannot compact a finding, so it takes a fixed title whose
# recurrence measures how often the phase is unavailable. §FS-local-branch-review
UNAVAILABLE_FINDING_TITLE = "Pre-push review unavailable"
MAX_REVIEW_TEXT_CHARS = 20_000

# The post-push reviewer selects its rules from the PR label, and labels are
# derived from `task_type`, so keying off `task_type` makes both reviews judge the
# branch against one rule set. The publisher's label table is trusted-side code in
# `.github/scripts/`, unreachable from Forge, so the mapping is restated here.
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
    """The reviewer's own output, as it wrote it."""

    is_approved: bool
    review_comment: str
    finding_title: str
    finding_body: str


@dataclass
class LocalBranchReviewOutcome:
    """Final state of the review phase, after any repair and re-verification."""

    is_approved: bool
    review_comment: str
    model: str
    local_ci_verification: LocalCIVerificationResult
    original_finding: str | None = None
    repair_summary: str | None = None
    repair_changed_paths: list[str] = field(default_factory=list)

    def to_descriptor_payload(self) -> dict[str, Any]:
        """Return the optional `local_review` descriptor object."""
        payload: dict[str, Any] = {
            "is_approved": self.is_approved,
            "review_comment": _bounded(self.review_comment),
            "model": self.model,
        }
        if self.original_finding:
            payload["original_finding"] = _bounded(self.original_finding)
        if self.repair_summary:
            payload["repair_summary"] = _bounded(self.repair_summary)
        if self.repair_changed_paths:
            payload["repair_changed_paths"] = self.repair_changed_paths[:200]
        return payload


def _log_review(message: str, indent_level: int = 0) -> None:
    log_stage("local-review", message, indent_level=indent_level)


def _bounded(text: str) -> str:
    return text if len(text) <= MAX_REVIEW_TEXT_CHARS else text[:MAX_REVIEW_TEXT_CHARS].rstrip() + "\n[truncated]"


def _git_stdout(repo_path: str, args: list[str]) -> str:
    return subprocess.check_output(["git", *args], cwd=repo_path, text=True).strip()


def review_model_name(model: str | None = None) -> str:
    """Return the model that reviews the branch before the push."""
    return model or os.environ.get(LOCAL_REVIEW_MODEL_ENVIRONMENT_VARIABLE) or DEFAULT_REVIEW_MODEL


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
    """Review the verified branch, repair one finding, and report the final state.

    Never raises for a review problem: an unavailable reviewer is a non-approval,
    because halting publication on a review outage turns it into a throughput
    outage (§FS-local-branch-review).
    """
    review_model = review_model_name(model)
    base_sha = _git_stdout(repo_path, ["rev-parse", f"{base_commit}^{{commit}}"])
    head_sha = _git_stdout(repo_path, ["rev-parse", "HEAD"])
    _log_review(f"Reviewing {coordinates} at {head_sha[:12]} against {base_sha[:12]} with {review_model}")

    verdict = _request_review(
        repo_path=repo_path,
        coordinates=coordinates,
        base_sha=base_sha,
        head_sha=head_sha,
        task_type=task_type,
        review_model=review_model,
        local_ci_verification=local_ci_verification,
        descriptor_input=descriptor_input,
    )

    if verdict is None:
        outcome = LocalBranchReviewOutcome(
            is_approved=False,
            review_comment=(
                "The pre-push branch review could not produce a verdict. This is a review "
                "availability problem, not a finding against the generated branch."
            ),
            model=review_model,
            local_ci_verification=local_ci_verification,
            repair_summary="Not attempted: the review phase produced no finding to repair.",
        )
        _record_finding(
            repo_path=repo_path,
            coordinates=coordinates,
            descriptor_input=descriptor_input,
            title=UNAVAILABLE_FINDING_TITLE,
            body=outcome.review_comment,
            outcome=outcome,
        )
        _log_review("Review unavailable; publishing labeled for human follow-up", indent_level=1)
        return outcome

    if verdict.is_approved:
        _log_review("Review approved the branch", indent_level=1)
        return LocalBranchReviewOutcome(
            is_approved=True,
            review_comment=verdict.review_comment,
            model=review_model,
            local_ci_verification=local_ci_verification,
        )

    _log_review(f"Review requested changes: {verdict.finding_title}", indent_level=1)
    outcome = _repair_finding(
        repo_path=repo_path,
        coordinates=coordinates,
        verdict=verdict,
        review_model=review_model,
        local_ci_verification=local_ci_verification,
        metrics_repo_path=metrics_repo_path,
        head_sha=head_sha,
        base_commit=base_commit,
    )
    _record_finding(
        repo_path=repo_path,
        coordinates=coordinates,
        descriptor_input=descriptor_input,
        title=verdict.finding_title,
        body=verdict.finding_body,
        outcome=outcome,
    )
    return outcome


def _repair_finding(
        *,
        repo_path: str,
        coordinates: str,
        verdict: LocalReviewVerdict,
        review_model: str,
        local_ci_verification: LocalCIVerificationResult,
        metrics_repo_path: str | None,
        head_sha: str,
        base_commit: str,
) -> LocalBranchReviewOutcome:
    """Run one bounded repair and re-verify, restoring the verified tree on failure."""
    original_finding = f"{verdict.finding_title}\n\n{verdict.finding_body}"
    unrepaired = LocalBranchReviewOutcome(
        is_approved=False,
        review_comment=verdict.review_comment,
        model=review_model,
        local_ci_verification=local_ci_verification,
        original_finding=original_finding,
    )

    attempt = run_repair_agent(
        repo_path=repo_path,
        prompt=_build_repair_prompt(coordinates, verdict),
        log_path=build_timestamped_task_log_path("local-review-repair", coordinates, "repair"),
        commit_message="Apply pre-push review repair",
    )
    if attempt.commit is None:
        unrepaired.repair_summary = (
            f"Repair attempt did not change the branch: the agent {attempt.failure_reason}. "
            f"Log: {attempt.log_path}."
        )
        _log_review(f"Repair did not land: the agent {attempt.failure_reason}", indent_level=1)
        return unrepaired

    _log_review(f"Repair committed {attempt.commit[:12]}; re-running the pre-publication gate", indent_level=1)
    try:
        reverified = run_local_ci_verification(
            repo_path=repo_path,
            coordinates=coordinates,
            base_commit=base_commit,
            metrics_repo_path=metrics_repo_path,
        )
    except LocalCIVerificationError as error:
        # A review finding must never cost an otherwise-publishable run, so the
        # verified pre-repair tree is restored and published. §FS-local-branch-review
        subprocess.run(["git", "reset", "--hard", head_sha], cwd=repo_path, check=True)
        # The discarded re-run left its failure in the pending metrics; restore the
        # result that actually describes the tree being published.
        write_verification_metrics(metrics_repo_path, local_ci_verification)
        unrepaired.repair_summary = (
            f"Repair {attempt.commit} was reverted: the pre-publication gate failed at gate "
            f"'{error.result.failure_gate or 'unknown'}' over the repaired tree, so the branch was "
            f"restored to the verified commit {head_sha}. Repair log: {attempt.log_path}."
        )
        unrepaired.repair_changed_paths = list(attempt.changed_paths)
        _log_review("Repaired tree failed re-verification; restored the verified commit", indent_level=1)
        return unrepaired

    _log_review("Repaired tree passed re-verification; the finding is cleared", indent_level=1)
    return LocalBranchReviewOutcome(
        is_approved=True,
        review_comment=(
            "The pre-push branch review requested changes, a bounded repair addressed the finding, "
            "and the pre-publication gate passed again over the repaired branch."
        ),
        model=review_model,
        local_ci_verification=reverified,
        original_finding=original_finding,
        repair_summary=(
            f"Repair {attempt.commit} changed {len(attempt.changed_paths)} path(s) and re-verified. "
            f"Log: {attempt.log_path}."
        ),
        repair_changed_paths=list(attempt.changed_paths),
    )


def _request_review(
        *,
        repo_path: str,
        coordinates: str,
        base_sha: str,
        head_sha: str,
        task_type: str,
        review_model: str,
        local_ci_verification: LocalCIVerificationResult,
        descriptor_input: Any,
) -> LocalReviewVerdict | None:
    """Run the isolated reviewer and return its verdict, or None when unavailable."""
    ready, detail = check_pi_authentication(review_model)
    if not ready:
        _log_review(
            f"Pi review authentication is not ready for provider '{PI_PROVIDER}' "
            f"and model '{review_model}': {detail}",
            indent_level=1,
        )
        return None

    log_path = build_timestamped_task_log_path("local-review", coordinates, "pi_local_branch_review")
    evidence_dir = os.path.join(os.path.dirname(log_path), f"evidence-{uuid.uuid4().hex[:8]}")
    os.makedirs(evidence_dir, exist_ok=True)
    evidence_path = os.path.join(evidence_dir, "review-evidence.json")
    verdict_path = os.path.join(evidence_dir, "verdict.json")
    _write_evidence(evidence_path, coordinates, task_type, local_ci_verification, descriptor_input)

    worktree_path = _create_review_worktree(repo_path, head_sha)
    if worktree_path is None:
        return None
    try:
        command = [
            "pi", "-p",
            # The same cold session the post-push reviewer uses: a review that
            # shares context with the generating run inherits its justifications.
            "--no-session",
            "--approve",
            "--provider", PI_PROVIDER,
            "--model", review_model,
            "--thinking", "medium",
            _build_review_prompt(
                coordinates=coordinates,
                base_sha=base_sha,
                head_sha=head_sha,
                task_type=task_type,
                evidence_path=evidence_path,
                verdict_path=verdict_path,
            ),
        ]
        _log_review(f"Pi review log: {display_log_path(log_path)}", indent_level=1)
        try:
            with open(log_path, "w", encoding="utf-8") as log_file:
                completed = subprocess.run(
                    command,
                    cwd=worktree_path,
                    stdout=log_file,
                    stderr=subprocess.STDOUT,
                    timeout=LOCAL_REVIEW_TIMEOUT_SECONDS,
                    check=False,
                )
        except subprocess.TimeoutExpired:
            _log_review(f"Review timed out after {LOCAL_REVIEW_TIMEOUT_SECONDS} seconds", indent_level=1)
            return None
        if completed.returncode != 0:
            _log_review(f"Review exited with code {completed.returncode}", indent_level=1)
            return None
        return _read_verdict(verdict_path)
    finally:
        _remove_review_worktree(repo_path, worktree_path)


def _create_review_worktree(repo_path: str, head_sha: str) -> str | None:
    """Detach a cold worktree at the verified commit from the shared object store."""
    worktree_root = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                                 "local_repositories", REVIEW_WORKTREE_DIRNAME)
    os.makedirs(worktree_root, exist_ok=True)
    worktree_path = os.path.join(worktree_root, f"review-{uuid.uuid4().hex[:8]}")
    result = subprocess.run(
        ["git", "worktree", "add", "--detach", worktree_path, head_sha],
        cwd=repo_path,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=False,
    )
    if result.returncode != 0:
        _log_review(f"Failed to create the isolated review worktree: {result.stdout.strip()}", indent_level=1)
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
    """Write the evidence the post-push reviewer never gets: gate records and render stats."""
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
        evidence["run_metrics"] = copy.deepcopy(run_metrics)
    for attribute in ("issue_number", "template_type", "status", "previous_coordinates"):
        evidence[attribute] = getattr(descriptor_input, attribute, None)
    with open(evidence_path, "w", encoding="utf-8") as evidence_file:
        json.dump(evidence, evidence_file, indent=2, sort_keys=True, default=str)
        evidence_file.write("\n")


def _read_verdict(verdict_path: str) -> LocalReviewVerdict | None:
    """Read the reviewer's verdict file, treating anything unusable as unavailable."""
    if not os.path.isfile(verdict_path):
        _log_review("Review wrote no verdict file", indent_level=1)
        return None
    try:
        with open(verdict_path, "r", encoding="utf-8") as verdict_file:
            payload = json.load(verdict_file)
    except (OSError, json.JSONDecodeError) as error:
        _log_review(f"Review verdict could not be read: {error}", indent_level=1)
        return None
    if not isinstance(payload, dict) or not isinstance(payload.get("is_approved"), bool):
        _log_review("Review verdict did not carry a boolean approval state", indent_level=1)
        return None
    is_approved = bool(payload["is_approved"])
    comment = str(payload.get("review_comment") or "").strip()
    title = str(payload.get("finding_title") or "").strip()
    body = str(payload.get("finding_body") or "").strip()
    if not is_approved and not title:
        _log_review("Review requested changes without a finding title", indent_level=1)
        return None
    return LocalReviewVerdict(
        is_approved=is_approved,
        review_comment=comment or ("Approved with no blocking issues." if is_approved else title),
        finding_title=title,
        finding_body=body or comment,
    )


def _build_review_prompt(
        *,
        coordinates: str,
        base_sha: str,
        head_sha: str,
        task_type: str,
        evidence_path: str,
        verdict_path: str,
) -> str:
    """Build the pre-push review prompt from local evidence.

    The review skills are written against `gh pr view/diff/checks`, none of which
    exist before the push, so each is substituted by its local equivalent
    (§FS-local-branch-review).
    """
    skill = REVIEW_SKILLS_BY_TASK_TYPE.get(task_type, "review-library-new-request")
    return "\n".join([
        f"Review the branch that Forge is about to push as a pull request for {coordinates}.",
        "There is no pull request yet, so no `gh pr` command applies. Use local evidence:",
        f"- The change under review is `git diff {base_sha} {head_sha}`; "
        f"`git diff --name-only {base_sha} {head_sha}` lists its files. "
        "This is exactly the eventual pull-request diff minus the publication descriptor commit.",
        f"- The review rules are the enumerated rules in `skills/{skill}/SKILL.md` in this worktree. "
        "Apply the rules that do not depend on an existing pull request; ignore the parts that "
        "describe posting a GitHub review, labels, merging, or CI check status.",
        f"- Local CI gate records and the resolved publication statistics are in `{evidence_path}`. "
        "They replace the pull request's check runs and the entry counts a pull request body would carry.",
        "",
        "The repository is checked out in a detached worktree at the branch head. Do not run "
        "`gh`, do not push, do not commit, and do not edit any file in this worktree.",
        "",
        "Request changes only for a concrete violation of an enumerated rule in that skill. Do not "
        "block on self-formed test-quality, test-scope, or 'end-user behavior' judgments that no "
        "enumerated rule backs; in particular, do not require a test to exercise a chosen public API "
        "entry point when it already exercises the library's types, including relocated or shaded "
        "types that ship in the library JAR. A non-approval here stops the branch from ever becoming "
        "a pull request, so the bar is a cited rule, not a preference.",
        "",
        f"Write your verdict as JSON to the absolute path `{verdict_path}` and write nothing else. "
        "That file is the only output that is read. Its shape is:",
        json.dumps(
            {
                "is_approved": True,
                "review_comment": "One paragraph: what was checked and what you concluded.",
                "finding_title": "Short reusable title for the defect class; empty when approved.",
                "finding_body": "What is wrong, which rule it violates, and where; empty when approved.",
            },
            indent=2,
        ),
        "",
        "When you approve, set `is_approved` to true and leave both finding fields empty. When you "
        "request changes, set it to false and write a title that would read the same for another "
        "branch with the same defect, so recurrences of one defect class are recognizable.",
    ])


def _build_repair_prompt(coordinates: str, verdict: LocalReviewVerdict) -> str:
    return "\n".join([
        "Fix the finding a pre-push branch review raised against the current worktree.",
        "Keep the change minimal and targeted to the finding; do not restructure unrelated code.",
        "The branch already passed local CI-equivalent verification, so do not break what passes.",
        "",
        f"Library: {coordinates}",
        f"Finding: {verdict.finding_title}",
        "",
        "Details:",
        "```text",
        verdict.finding_body,
        "```",
        "",
        "Reviewer comment:",
        "```text",
        verdict.review_comment,
        "```",
    ])


def _record_finding(
        *,
        repo_path: str,
        coordinates: str,
        descriptor_input: Any,
        title: str,
        body: str,
        outcome: LocalBranchReviewOutcome,
) -> None:
    """Append the finding to the tracked findings record and commit it.

    Committed before the descriptor commit so the descriptor commit stays the
    branch tip the trusted publisher requires (§GIT-shared-publication-pipeline).
    """
    findings_path = os.path.join(repo_path, FINDINGS_RELATIVE_PATH)
    entry = _render_finding_entry(coordinates, descriptor_input, title, body, outcome)
    existing = ""
    if os.path.isfile(findings_path):
        with open(findings_path, "r", encoding="utf-8") as findings_file:
            existing = findings_file.read()
    if existing.startswith(FINDINGS_TITLE):
        header, _, entries = existing.partition("\n## ")
        header_block = header.rstrip("\n")
        remaining = f"## {entries}" if entries else ""
    else:
        header_block = f"{FINDINGS_TITLE}\n\n{FINDINGS_PREAMBLE}"
        remaining = existing.strip()
    updated = f"{header_block}\n\n{entry}"
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
        outcome: LocalBranchReviewOutcome,
) -> str:
    """Render one chronological entry from the title and body the reviewer supplied."""
    group, artifact, version = parse_coordinate_parts(coordinates)
    issue_number = getattr(descriptor_input, "issue_number", None)
    issue_reference = f" (#{issue_number})" if issue_number else ""
    date = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    lines = [
        f"## {date} — {group}:{artifact}:{version}{issue_reference}",
        "",
        f"**{title}**",
        "",
        f"- Reviewing model: `{outcome.model}`",
        f"- Outcome: {'cleared by repair' if outcome.is_approved else 'published for human review'}",
    ]
    if outcome.repair_summary:
        lines.append(f"- Repair: {outcome.repair_summary}")
    if body:
        lines.extend(["", body.strip()])
    return "\n".join(lines) + "\n"
