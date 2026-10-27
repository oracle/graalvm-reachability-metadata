# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Publication validation, Forge approval, and auto-merge control
(§AR-forge-dispatcher-decomposition, §FS-automated-pr-review).
"""


import contextlib
import importlib
import os
import subprocess
import sys
from git_scripts.common_git import run_git_transport
from typing import Any
from dispatcher.config import (
    BENCHMARK_PUBLICATION_TASK_TYPE,
    DEFAULT_WORKTREE_BASE_REF,
    FORGE_APPROVAL_BODY_PREFIX,
    LABEL_FORGE_MERGE_FOLLOW_UP,
    LABEL_HUMAN_INTERVENTION,
    LABEL_LIBRARY_UNSUPPORTED_VERSION,
    LOCAL_REVIEW_CLOSE_MARKER,
    REPO,
    SUPPORTED_FORGE_REVIEW_TASK_TYPES,
    TRUSTED_FORGE_PUBLISHER_LOGIN,
)
from dispatcher.github_api import gh
from dispatcher.issue_admin import (
    add_issue_label,
    add_pull_request_label,
    close_issue,
    get_issue_comments,
    post_issue_comment,
    remove_pull_request_label,
)
from dispatcher.pr_state import (
    get_pull_request_reviews,
    pull_request_has_label,
    resolve_pull_request_merge_flag,
)

def _load_trusted_publisher_module(reachability_metadata_path: str) -> Any:
    """Load the default-branch publication validator used by Actions."""
    publisher_path = os.path.join(
        reachability_metadata_path,
        ".github",
        "scripts",
        "forge_pr_publisher",
        "publisher.py",
    )
    module_name = "_forge_trusted_pr_publisher"
    existing = sys.modules.get(module_name)
    if existing is not None and getattr(existing, "__file__", None) == publisher_path:
        return existing
    spec = importlib.util.spec_from_file_location(module_name, publisher_path)
    if spec is None or spec.loader is None:
        raise RuntimeError("Trusted Forge publisher module is unavailable")
    module = importlib.util.module_from_spec(spec)
    sys.modules[module_name] = module
    spec.loader.exec_module(module)
    return module


def validate_pull_request_publication(
        pull_request: dict,
        reachability_metadata_path: str,
) -> Any:
    """Validate the trusted descriptor on the exact current PR head.

    §FS-automated-pr-review
    """
    pr_number = pull_request.get("number")
    head_sha = pull_request.get("headRefOid")
    head_branch = pull_request.get("headRefName")
    head_repository = pull_request.get("headRepository")
    author = pull_request.get("author")
    if (
            not isinstance(pr_number, int)
            or not isinstance(head_sha, str)
            or not head_sha
            or not isinstance(head_branch, str)
            or not head_branch.startswith("ai/")
    ):
        raise ValueError(f"PR #{pr_number} has no eligible exact upstream ai/** head")
    if (
            pull_request.get("isCrossRepository")
            or not isinstance(head_repository, dict)
            or head_repository.get("nameWithOwner") != REPO
    ):
        raise ValueError(f"PR #{pr_number} head repository is not {REPO}")
    if not isinstance(author, dict) or author.get("login") != TRUSTED_FORGE_PUBLISHER_LOGIN:
        raise ValueError(f"PR #{pr_number} was not created by the trusted Forge publisher")
    if subprocess.run(
            ["git", "check-ref-format", "--branch", head_branch],
            cwd=reachability_metadata_path,
            check=False,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
    ).returncode != 0:
        raise ValueError(f"PR #{pr_number} head branch is not a valid git ref")

    run_git_transport(
        ["fetch", "--quiet", "origin", DEFAULT_WORKTREE_BASE_REF],
        cwd=reachability_metadata_path,
    )
    run_git_transport(
        [
            "fetch", "--quiet", "origin",
            f"+refs/heads/{head_branch}:refs/remotes/origin/{head_branch}",
        ],
        cwd=reachability_metadata_path,
    )
    producer_parts = head_branch.split("/", 2)
    if len(producer_parts) != 3 or not producer_parts[1]:
        raise ValueError(f"PR #{pr_number} head branch does not name a producer")
    publisher = _load_trusted_publisher_module(reachability_metadata_path)
    with contextlib.chdir(reachability_metadata_path):
        validated = publisher.validate_publication(
            head_sha=head_sha,
            branch=head_branch,
            actor=producer_parts[1],
            repository=REPO,
            pull_request_head=True,
        )

    descriptor = validated.descriptor
    publication_id = descriptor.get("publication_id")
    body = pull_request.get("body")
    trailer = f"Forge-Publication-ID: {publication_id}"
    if not isinstance(body, str) or trailer not in body.splitlines():
        raise ValueError(f"PR #{pr_number} does not carry its trusted publication identity")
    if descriptor.get("task_type") not in SUPPORTED_FORGE_REVIEW_TASK_TYPES:
        raise ValueError(f"PR #{pr_number} descriptor task is not eligible for Forge review")
    return validated


def publication_review_disposition(validated: Any) -> tuple[str, str | None]:
    """Return the exact-head descriptor decision executed by Forge."""
    descriptor = validated.descriptor
    if descriptor.get("task_type") == BENCHMARK_PUBLICATION_TASK_TYPE:
        return "approved", None
    review = descriptor.get("local_review")
    if not isinstance(review, dict):
        raise ValueError("Generated publication descriptor has no local review")
    decision = review.get("decision")
    action = review.get("action")
    if decision == "approved" and action is None:
        return decision, None
    if decision == "rejected" and action in {"human-intervention", "close"}:
        return decision, str(action)
    raise ValueError("Publication descriptor has an invalid local-review disposition")


def approve_pull_request_from_descriptor(pull_request: dict) -> None:
    """Approve the exact descriptor-validated PR head without an agent."""
    pr_number = pull_request.get("number")
    head_sha = pull_request.get("headRefOid")
    if not isinstance(pr_number, int) or not isinstance(head_sha, str) or not head_sha:
        raise RuntimeError(f"Missing head metadata for descriptor-approved PR #{pr_number}")
    gh(
        "api",
        "--method",
        "POST",
        f"/repos/{REPO}/pulls/{pr_number}/reviews",
        "-f",
        f"commit_id={head_sha}",
        "-f",
        "event=APPROVE",
        "-f",
        f"body={FORGE_APPROVAL_BODY_PREFIX}{head_sha}.",
    )
    pull_request["reviewDecision"] = "APPROVED"


def enable_pull_request_auto_merge(pull_request: dict) -> None:
    """Enable auto-merge without allowing a different head to be merged.

    §FS-automated-pr-review
    """
    pr_number = pull_request.get("number")
    head_sha = pull_request.get("headRefOid")
    if not isinstance(pr_number, int) or not isinstance(head_sha, str) or not head_sha:
        raise RuntimeError(f"Missing auto-merge metadata for PR #{pr_number}")
    if pull_request.get("autoMergeRequest") is not None:
        print(f"[Auto-merge is already enabled for approved PR #{pr_number}.]")
        return

    merge_args = [
        "pr", "merge", str(pr_number),
        "--repo", REPO,
        "--auto",
        "--match-head-commit", head_sha,
    ]
    if not pull_request.get("isMergeQueueEnabled"):
        merge_args.append(resolve_pull_request_merge_flag(pull_request))
    gh(*merge_args)
    pull_request["autoMergeRequest"] = {"enabledAt": "forge"}
    print(f"[Enabled auto-merge for approved PR #{pr_number} at {head_sha}.]")


def disable_pull_request_auto_merge(pull_request: dict) -> None:
    """Disable an active auto-merge request before human intervention."""
    pr_number = pull_request.get("number")
    if not isinstance(pr_number, int):
        raise RuntimeError("Cannot disable auto-merge without a pull request number")
    if pull_request.get("autoMergeRequest") is None:
        return
    gh("pr", "merge", str(pr_number), "--repo", REPO, "--disable-auto")
    pull_request["autoMergeRequest"] = None
    print(f"[Disabled auto-merge for PR #{pr_number}.]")


def dismiss_forge_approval_reviews(
        pull_request: dict,
        message: str = "Forge withdrew approval because this pull request needs human intervention.",
) -> int:
    """Dismiss only exact-head approvals previously submitted by Forge."""
    pr_number = pull_request.get("number")
    if not isinstance(pr_number, int):
        raise RuntimeError("Cannot dismiss Forge approval without a pull request number")
    dismissed_count = 0
    for review in get_pull_request_reviews(pr_number):
        if not isinstance(review, dict) or review.get("state") != "APPROVED":
            continue
        if not str(review.get("body") or "").startswith(FORGE_APPROVAL_BODY_PREFIX):
            continue
        review_id = review.get("id")
        if not isinstance(review_id, int):
            raise RuntimeError(f"Forge approval on PR #{pr_number} has no review id")
        gh(
            "api",
            "--method", "PUT",
            f"/repos/{REPO}/pulls/{pr_number}/reviews/{review_id}/dismissals",
            "-f", f"message={message}",
        )
        dismissed_count += 1

    if dismissed_count:
        pull_request["reviewDecision"] = "REVIEW_REQUIRED"
        print(f"[Dismissed {dismissed_count} Forge approval(s) on PR #{pr_number}.]")
    return dismissed_count


def ensure_pull_request_unapproved(pull_request: dict) -> None:
    """Withdraw Forge merge readiness. §FS-automated-pr-review"""
    disable_pull_request_auto_merge(pull_request)
    dismiss_forge_approval_reviews(pull_request)


def reconcile_rejected_publication(
        pull_request: dict,
        validated: Any,
) -> None:
    """Apply the exact rejected action idempotently without waiting for CI."""
    decision, action = publication_review_disposition(validated)
    if decision != "rejected":
        return
    pr_number = pull_request.get("number")
    if not isinstance(pr_number, int):
        raise RuntimeError("Rejected publication is missing its pull request number")
    ensure_pull_request_unapproved(pull_request)
    if pull_request_has_label(pull_request, LABEL_FORGE_MERGE_FOLLOW_UP):
        remove_pull_request_label(pr_number, LABEL_FORGE_MERGE_FOLLOW_UP)
    if action == "human-intervention":
        add_pull_request_label(pr_number, LABEL_HUMAN_INTERVENTION)
        print(f"[Kept rejected PR #{pr_number} open for human intervention.]")
        return
    if action != "close":
        raise ValueError(f"Rejected publication has unsupported action {action!r}")

    issue_number = validated.descriptor.get("issue_number")
    if not isinstance(issue_number, int):
        raise RuntimeError(f"Rejected close PR #{pr_number} has no linked issue")

    review = validated.descriptor["local_review"]
    if not any(
            LOCAL_REVIEW_CLOSE_MARKER in str(comment.get("body") or "")
            for comment in get_issue_comments(pr_number)
    ):
        post_issue_comment(
            pr_number,
            "\n\n".join([
                "This pull request is closed because of the local reviewer's decision.",
                f"**{review['finding_title']}**\n\n{review['finding_body']}",
                str(review["review_comment"]),
                LOCAL_REVIEW_CLOSE_MARKER,
            ]),
        )
    add_issue_label(issue_number, LABEL_LIBRARY_UNSUPPORTED_VERSION)
    close_issue(issue_number, f"local review rejected PR #{pr_number} as unsupported")
    gh("pr", "close", str(pr_number), "--repo", REPO)
    print(f"[Closed rejected PR #{pr_number} and unsupported issue #{issue_number}.]")
