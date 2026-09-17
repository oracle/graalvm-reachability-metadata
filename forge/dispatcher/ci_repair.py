# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Failed-CI diagnosis and repair inside the review boundary
(§AR-forge-dispatcher-decomposition, §FS-automated-pr-review)."""


import json
import os
import shutil
import subprocess
import uuid
from ai_workflows.agents.agent_runtime import analysis_agent_run
from ai_workflows.agents.agent_runtime import get_analysis_agent
from dataclasses import dataclass
from git_scripts.common_git import run_git_transport
from git_scripts.local_branch_review import LocalReviewVerdict
from git_scripts.local_branch_review import _record_finding
from git_scripts.local_branch_review import REVIEW_SKILLS_BY_TASK_TYPE
from git_scripts.local_branch_review import _read_verdict
from git_scripts.publication_descriptor import validate_publication_descriptor
from types import SimpleNamespace
from typing import Any
from utility_scripts.task_logs import display_log_path
from utility_scripts.task_logs import new_session_id
from dispatcher.config import (
    FORGE_DIR,
    REPO,
    REVIEW_TIMEOUT_SECONDS,
)
from dispatcher.github_api import gh_json
from dispatcher.issue_admin import (
    get_issue_comments,
    post_issue_comment,
)
from dispatcher.pr_merge import run_checked_command
from dispatcher.pr_publication import (
    ensure_pull_request_unapproved,
    reconcile_rejected_publication,
)
from dispatcher.pr_state import (
    get_failed_workflow_run_ids,
    get_pull_request_workflow_runs,
    has_failed_pull_request_ci,
    rerun_failed_pull_request_workflow_jobs,
)
from dispatcher.worktrees import create_detached_worktree

from dispatcher.worktrees import cleanup_review_workspace

@dataclass(frozen=True)
class CIRepairOutcome:
    """One structured failed-CI diagnosis. §FS-automated-pr-review"""

    decision: str
    review_verdict: LocalReviewVerdict | None = None
    workflow_run_ids: tuple[int, ...] = ()
    review_comment: str = ""

def _ci_repair_changed_paths(worktree_path: str) -> list[str]:
    """Return tracked and untracked paths changed by the CI-repair agent."""
    tracked = run_checked_command(
        ["git", "diff", "--name-only", "--diff-filter=ACMRTD", "HEAD"],
        worktree_path,
        "Failed to list CI-repair changes",
    ).stdout.splitlines()
    untracked = run_checked_command(
        ["git", "ls-files", "--others", "--exclude-standard"],
        worktree_path,
        "Failed to list untracked CI-repair files",
    ).stdout.splitlines()
    return sorted(set(path for path in [*tracked, *untracked] if path))


def ensure_infrastructure_issue(evidence: dict[str, str]) -> tuple[int, str]:
    """Find one exact-title open infrastructure issue or create it."""
    candidates = gh_json(
        "issue", "list",
        "--repo", REPO,
        "--state", "open",
        "--search", f"{evidence['title']} in:title",
        "--limit", "100",
        "--json", "number,title,url",
    )
    if not isinstance(candidates, list):
        raise RuntimeError("Infrastructure issue search returned invalid data")
    for candidate in candidates:
        if isinstance(candidate, dict) and candidate.get("title") == evidence["title"]:
            return int(candidate["number"]), str(candidate["url"])
    created = gh_json(
        "api",
        "--method", "POST",
        f"/repos/{REPO}/issues",
        "-f", f"title={evidence['title']}",
        "-f", f"body={evidence['body']}",
    )
    if not isinstance(created, dict):
        raise RuntimeError("Infrastructure issue creation returned invalid data")
    return int(created["number"]), str(created["html_url"])


def build_ci_repair_prompt(
        pr_number: int,
        descriptor: dict[str, Any],
        evidence_path: str,
        verdict_path: str,
) -> str:
    """Build the failed-CI repair and authoritative re-review prompt."""
    task_type = str(descriptor["task_type"])
    skill = REVIEW_SKILLS_BY_TASK_TYPE.get(task_type)
    skill_instruction = (
        f"Apply every relevant enumerated rule in skills/{skill}/SKILL.md."
        if skill is not None
        else "This is a benchmark-result publication; preserve its measured result."
    )
    return "\n".join([
        f"Repair failed required CI for pull request #{pr_number}.",
        f"Read the exact descriptor and failed-check evidence from {evidence_path}.",
        "Use gh pr checks and gh run view when more failure output is needed. Do not "
        "rerun jobs or make any other GitHub mutation; Forge owns those actions.",
        skill_instruction,
        "Apply the first matching disposition in §root/FS-contribution-contract.5.",
        "",
        "Edit only the contribution's own library files. Do not edit shared build logic, "
        "the test harness, workflows, another coordinate, the publication descriptor, or "
        "forge/FINDINGS.md. Run the failed checks needed to verify your repair. Then review "
        "the complete resulting diff with the same responsibility as local_review.",
        "",
        f"Write exactly one JSON verdict to {verdict_path}. If the evidence proves the "
        "failure is transient, make no edits and use decision transient with the exact "
        "current-head workflow_run_ids whose failed jobs Forge should rerun. Use decision "
        "approved and omit action only when you repaired the contribution and the resulting "
        "tree is ready. Otherwise use decision rejected with action human-intervention or "
        "close and a non-empty finding. For a shared repository defect, also add "
        "infrastructure_issue with non-empty title and body evidence.",
        json.dumps({
            "decision": "approved",
            "review_comment": "What failed, what you checked, and the resulting decision.",
            "finding_title": "Reusable finding title, or empty when there was no finding.",
            "finding_body": "Failure evidence and rule, or empty when there was no finding.",
            "fix_note": "What you changed and how you verified it.",
        }, indent=2),
        "For a transient failure, instead write:",
        json.dumps({
            "decision": "transient",
            "workflow_run_ids": [123456789],
            "review_comment": "Evidence that the selected failed run is transient.",
        }, indent=2),
        "Do not commit or push; Forge owns the descriptor, findings ledger, commit, and push.",
    ])


def _read_ci_repair_outcome(verdict_path: str) -> CIRepairOutcome | None:
    """Read a repair, rejection, or transient-rerun diagnosis."""
    try:
        with open(verdict_path, "r", encoding="utf-8") as verdict_file:
            payload = json.load(verdict_file)
    except (OSError, json.JSONDecodeError):
        return None
    if not isinstance(payload, dict):
        return None
    if payload.get("decision") != "transient":
        review_verdict = _read_verdict(verdict_path)
        if review_verdict is None:
            return None
        return CIRepairOutcome(
            decision=review_verdict.decision,
            review_verdict=review_verdict,
        )

    run_ids = payload.get("workflow_run_ids")
    review_comment = payload.get("review_comment")
    if (
            not isinstance(run_ids, list)
            or not run_ids
            or any(not isinstance(run_id, int) or run_id <= 0 for run_id in run_ids)
            or len(set(run_ids)) != len(run_ids)
            or not isinstance(review_comment, str)
            or not review_comment.strip()
            or len(review_comment) > 20_000
    ):
        return None
    return CIRepairOutcome(
        decision="transient",
        workflow_run_ids=tuple(run_ids),
        review_comment=review_comment.strip(),
    )


def _ci_repair_path_is_allowed(path: str, descriptor: dict[str, Any]) -> bool:
    """Enforce the exact file set. §root/FS-contribution-contract.2"""
    library = descriptor["library"]
    group = str(library["group"])
    artifact = str(library["artifact"])
    version = str(library["version"])
    exact_paths = {
        f"metadata/{group}/{artifact}/index.json",
        f"metadata/{group}/{artifact}/{version}/reachability-metadata.json",
    }
    allowed_prefixes = (
        f"tests/src/{group}/{artifact}/{version}/",
        f"stats/{group}/{artifact}/{version}/",
        "tests/tck-build-logic/src/main/resources/allowed-docker-images/",
    )
    return path in exact_paths or path.startswith(allowed_prefixes)


def repair_failed_ci_pull_request(
        pull_request: dict,
        validated: Any,
        reachability_metadata_path: str,
) -> bool:
    """Diagnose failed CI, then rerun transient jobs or push one repaired decision."""
    pr_number = pull_request.get("number")
    head_sha = pull_request.get("headRefOid")
    head_branch = pull_request.get("headRefName")
    if (
            not isinstance(pr_number, int)
            or not isinstance(head_sha, str)
            or not isinstance(head_branch, str)
    ):
        raise RuntimeError("Failed-CI repair requires complete pull request head metadata")

    worktree_root = os.path.join(
        FORGE_DIR, "local_repositories", "forge_ci_repair_worktrees",
    )
    os.makedirs(worktree_root, exist_ok=True)
    worktree_path = os.path.join(
        worktree_root, f"ci-repair-pr-{pr_number}-{uuid.uuid4().hex[:8]}",
    )
    create_detached_worktree(
        reachability_metadata_path,
        worktree_path,
        head_sha,
        f"Failed to create CI-repair worktree for PR #{pr_number}",
    )
    try:
        session_id = new_session_id()
        evidence_dir = os.path.join(worktree_path, f".forge-ci-repair-{uuid.uuid4().hex[:8]}")
        os.makedirs(evidence_dir)
        evidence_path = os.path.join(evidence_dir, "evidence.json")
        verdict_path = os.path.join(evidence_dir, "verdict.json")
        workflow_runs = get_pull_request_workflow_runs(head_sha)
        with open(evidence_path, "w", encoding="utf-8") as evidence_file:
            json.dump(
                {
                    "pull_request": {
                        "number": pr_number,
                        "head_sha": head_sha,
                        "head_branch": head_branch,
                    },
                    "descriptor": validated.descriptor,
                    "workflow_runs": workflow_runs,
                    "failed_checks": (
                        pull_request.get("statusCheckRollup", {})
                        .get("contexts", {})
                        .get("nodes", [])
                    ),
                },
                evidence_file,
                indent=2,
                sort_keys=True,
            )
            evidence_file.write("\n")

        selection = get_analysis_agent()
        trusted_environment = dict(os.environ)
        trusted_environment["_FORGE_AGENT_ALLOW_GITHUB_ACCESS"] = "1"
        result = analysis_agent_run(
            working_dir=worktree_path,
            context=build_ci_repair_prompt(
                pr_number, validated.descriptor, evidence_path, verdict_path,
            ),
            task_type="pr-ci-repair",
            library=str(validated.descriptor["library"]["coordinates"]),
            timeout=REVIEW_TIMEOUT_SECONDS,
            environment=trusted_environment,
            thinking_level="xhigh",
        )
        # The identifier is what the descriptor and the pull request carry; the
        # path is printed for the operator only. §FS-durable-generation-logs
        print(
            f"[CI repair session {session_id}; log: {display_log_path(result.log_path)}]"
        )
        outcome = _read_ci_repair_outcome(verdict_path) if result.return_code == 0 else None
        shutil.rmtree(evidence_dir, ignore_errors=True)
        changed_paths = _ci_repair_changed_paths(worktree_path)
        verdict = outcome.review_verdict if outcome is not None else None

        if outcome is not None and outcome.decision == "transient":
            invalid_run_ids = sorted(
                set(outcome.workflow_run_ids) - set(get_failed_workflow_run_ids(workflow_runs))
            )
            if changed_paths:
                verdict = LocalReviewVerdict(
                    decision="rejected",
                    action="human-intervention",
                    review_comment=outcome.review_comment,
                    finding_title="Transient CI diagnosis changed the contribution",
                    finding_body=(
                        "A transient verdict must leave the exact failing tree unchanged, but "
                        f"the agent changed: {', '.join(changed_paths)}"
                    ),
                    fix_note="The out-of-scope transient edits were discarded.",
                )
            elif invalid_run_ids:
                verdict = LocalReviewVerdict(
                    decision="rejected",
                    action="human-intervention",
                    review_comment=outcome.review_comment,
                    finding_title="Transient CI diagnosis selected unrelated runs",
                    finding_body=(
                        "The transient verdict requested workflow runs that were not failed "
                        f"on the exact reviewed head: {', '.join(map(str, invalid_run_ids))}"
                    ),
                    fix_note="No workflow was rerun.",
                )
            else:
                rerun_count = rerun_failed_pull_request_workflow_jobs(
                    pr_number,
                    head_sha,
                    outcome.workflow_run_ids,
                )
                print(
                    f"[CI repair agent classified {rerun_count} workflow run(s) as transient "
                    f"for approved PR #{pr_number}; waiting for the rerun.]"
                )
                return True

        if verdict is None:
            verdict = LocalReviewVerdict(
                decision="rejected",
                action="human-intervention",
                review_comment=(
                    "Forge could not obtain a valid CI-repair verdict in review session "
                    f"{session_id}."
                ),
                finding_title="CI repair reviewer unavailable",
                finding_body="The failed-CI repair turn did not return a readable decision.",
                fix_note="",
            )
        elif verdict.decision == "approved" and not changed_paths:
            verdict = LocalReviewVerdict(
                decision="rejected",
                action="human-intervention",
                review_comment=verdict.review_comment,
                finding_title="Failed CI was not repaired",
                finding_body="The CI-repair reviewer approved without changing the failing head.",
                fix_note=verdict.fix_note,
            )

        infrastructure_evidence = verdict.infrastructure_issue

        invalid_paths = [
            path for path in changed_paths
            if not _ci_repair_path_is_allowed(path, validated.descriptor)
        ]
        allowed_docker_paths = [
            path for path in changed_paths
            if path.startswith(
                "tests/tck-build-logic/src/main/resources/allowed-docker-images/"
            )
        ]
        if len(allowed_docker_paths) > 1:
            invalid_paths.extend(allowed_docker_paths)
        if invalid_paths:
            verdict = LocalReviewVerdict(
                decision="rejected",
                action="human-intervention",
                review_comment="The attempted CI repair changed files outside the contribution.",
                finding_title="CI repair crossed the contribution boundary",
                finding_body=(
                    "The repair attempted to change shared or unrelated paths: "
                    + ", ".join(invalid_paths)
                ),
                fix_note="The out-of-scope edits were discarded.",
            )

        if verdict.decision == "rejected":
            subprocess.run(["git", "reset", "--hard", head_sha], cwd=worktree_path, check=True)
            subprocess.run(["git", "clean", "-fd"], cwd=worktree_path, check=True)
            changed_paths = []
            if infrastructure_evidence is not None:
                issue_number, issue_url = ensure_infrastructure_issue(infrastructure_evidence)
                verdict = LocalReviewVerdict(
                    decision=verdict.decision,
                    action=verdict.action,
                    review_comment=verdict.review_comment,
                    finding_title=verdict.finding_title,
                    finding_body=(
                        f"{verdict.finding_body.rstrip()}\n\n"
                        f"Infrastructure issue: {issue_url} (#{issue_number})"
                    ),
                    fix_note=verdict.fix_note,
                )
        library = validated.descriptor["library"]

        if verdict.finding_title:
            _record_finding(
                repo_path=worktree_path,
                coordinates=str(library["coordinates"]),
                descriptor_input=SimpleNamespace(
                    issue_number=validated.descriptor.get("issue_number"),
                ),
                title=verdict.finding_title,
                body=verdict.finding_body,
            )

        descriptor = json.loads(json.dumps(validated.descriptor))
        descriptor["local_review"] = {
            "decision": verdict.decision,
            "review_comment": verdict.review_comment,
            "finding_title": verdict.finding_title,
            "finding_body": verdict.finding_body,
            "fix_note": verdict.fix_note,
            "model": selection.model,
            "session_id": session_id,
            "changed_paths": changed_paths[:200],
        }
        if verdict.action is not None:
            descriptor["local_review"]["action"] = verdict.action
        validate_publication_descriptor(worktree_path, descriptor)
        descriptor_path = os.path.join(worktree_path, validated.descriptor_path)
        with open(descriptor_path, "w", encoding="utf-8") as descriptor_file:
            json.dump(descriptor, descriptor_file, indent=2, sort_keys=True)
            descriptor_file.write("\n")
        subprocess.run(["git", "add", "-A"], cwd=worktree_path, check=True)
        subprocess.run(
            ["git", "commit", "-m", f"Repair failed CI for PR #{pr_number}"],
            cwd=worktree_path,
            check=True,
        )
        ensure_pull_request_unapproved(pull_request)
        run_git_transport(
            [
                "push", "origin", f"HEAD:refs/heads/{head_branch}",
                f"--force-with-lease=refs/heads/{head_branch}:{head_sha}",
            ],
            cwd=worktree_path,
        )
        print(
            f"[Pushed CI-repair decision for PR #{pr_number}; "
            "the new exact head will be evaluated on the next pass.]"
        )
        if verdict.decision == "rejected":
            rejected_marker = f"<!-- forge-ci-repair-rejected:{head_sha} -->"
            if not any(
                    rejected_marker in str(comment.get("body") or "")
                    for comment in get_issue_comments(pr_number)
            ):
                post_issue_comment(
                    pr_number,
                    f"Forge could not fix the failed CI by changing this contribution.\n\n"
                    f"{verdict.review_comment}\n\n{rejected_marker}",
                )
            reconcile_rejected_publication(
                pull_request,
                SimpleNamespace(descriptor=descriptor),
            )
        return True
    finally:
        cleanup_review_workspace(
            reachability_metadata_path, worktree_path, pr_number,
        )


def reconcile_failed_ci_pull_request(
        pull_request: dict,
        validated: Any,
        reachability_metadata_path: str,
) -> None:
    """Invoke scoped CI diagnosis immediately without automatic reruns."""
    pr_number = pull_request.get("number")
    head_sha = pull_request.get("headRefOid")
    if not isinstance(pr_number, int) or not isinstance(head_sha, str) or not head_sha:
        raise RuntimeError(f"Missing head metadata for failed-CI PR #{pr_number}")
    if not has_failed_pull_request_ci(pull_request):
        print(f"[Skipping failed-CI follow-up for PR #{pr_number}: CI state changed.]")
        return

    print(f"[Starting immediate contribution-scoped CI diagnosis for PR #{pr_number}.]")
    repair_failed_ci_pull_request(
        pull_request,
        validated,
        reachability_metadata_path,
    )
