# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Isolated reviewer session mechanics for the pre-push branch review.

Owns the detached review worktree, the reviewer prompt and evidence files, the
verdict reading, and the transfer of reviewer edits back to the publication
branch (§FS-local-branch-review). The review orchestration lives in
`local_branch_review.py`.
"""

from __future__ import annotations

import copy
import json
import os
import shlex
import shutil
import subprocess
import uuid
from typing import Any

from git_scripts.local_review_records import LocalReviewVerdict, LOCAL_REVIEW_METRICS_KEY, _log_review
from utility_scripts.local_ci_verification import FINDINGS_RELATIVE_PATH, LocalCIVerificationResult

REVIEW_WORKTREE_DIRNAME: str = "forge_prepublication_review_worktrees"
MAX_REVIEW_TEXT_CHARS: int = 20_000

REVIEW_SKILLS_BY_TASK_TYPE: dict[str, str] = {
    "library-new-request": "review-library-new-request",
    "library-update-request": "review-library-update-request",
    "fixes-javac-fail": "review-fixes-javac-fail",
    "fixes-java-run-fail": "review-fixes-java-run-fail",
    "fixes-native-image-run-fail": "review-fixes-native-image-run-fail",
    "not-for-native-image": "review-library-new-request",
}


def _git_stdout(repo_path: str, args: list[str]) -> str:
    return subprocess.check_output(["git", *args], cwd=repo_path, text=True).strip()


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
        "uncertainty, or anything else requiring a maintainer. When "
        "§root/FS-contribution-contract.5.4 proves the library version is unsupportable and "
        "the artifact has an index entry, repair the contribution into the skip record that "
        "rule prescribes and approve it. Reject with action close only when that rule leaves "
        "nothing to record.",
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


def _write_unavailable_log(log_path: str, detail: str) -> None:
    os.makedirs(os.path.dirname(log_path), exist_ok=True)
    with open(log_path, "w", encoding="utf-8") as log_file:
        log_file.write(f"# Pre-push local review unavailable\n\n{detail}\n")
