# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Detached worktree management for isolated runs
(§AR-forge-dispatcher-decomposition, §FS-forge-issue-resolution-goal)."""


import os
import subprocess
import sys
import uuid
from git_scripts.common_git import GitTransportError
from git_scripts.common_git import run_git_transport
from utility_scripts.gradle_dependency_cache import discard_worktree_gradle_home
from utility_scripts.gradle_dependency_cache import ensure_shared_dependency_cache
from utility_scripts.repo_path_resolver import get_repo_root
from utility_scripts.repo_path_resolver import git_env_limited_to_repo_root
from utility_scripts.repo_path_resolver import require_complete_reachability_repo
from utility_scripts.stage_logger import log_debug
from dispatcher.config import (
    DEFAULT_WORKTREE_BASE_REF,
    PREFLIGHT_INFO_DIRNAME,
)
from dispatcher.records import ClaimedIssue

from dispatcher.config import SCRATCH_REVIEW_WORKTREE_DIRNAME
from dispatcher.github_api import gh

import shutil
from utility_scripts.repo_path_resolver import get_forge_subdir_name
from utility_scripts.run_location import PHASE_CLAIM
from utility_scripts.run_location import STEP_CREATE_ISSUE_WORKSPACE
from utility_scripts.run_location import log_step_progress
from utility_scripts.run_location import pipeline_step
from utility_scripts.stage_logger import log_stage
from dispatcher.config import SCRATCH_WORKTREE_DIRNAME
from dispatcher.fixture_support import is_fixture_testing_enabled

# Worktrees whose failed-work preservation itself failed; cleanup must keep them.
preservation_failed_worktree_paths: set[str] = set()


def claimed_issue_worktree_is_valid(claimed_issue: ClaimedIssue, stage: str) -> bool:
    """Return True when analysis agents may safely run in the claimed issue worktree."""
    try:
        require_claimed_issue_worktree(claimed_issue, stage)
        return True
    except Exception as exc:
        print(
            (
                f"ERROR: Skipping {stage} for issue #{claimed_issue.issue['number']} "
                f"because the claimed worktree is invalid: {exc!r}"
            ),
            file=sys.stderr,
        )
        return False

def require_claimed_issue_worktree(claimed_issue: ClaimedIssue, stage: str) -> str:
    """Require the claimed issue path to be the exact isolated reachability worktree."""
    try:
        resolved_path = require_complete_reachability_repo(claimed_issue.worktree_path)
    except SystemExit as exc:
        raise RuntimeError(
            (
                f"Issue #{claimed_issue.issue['number']} {stage} requires a valid isolated "
                f"worktree at {claimed_issue.worktree_path}."
            )
        ) from exc
    expected_path = os.path.abspath(claimed_issue.worktree_path)
    if resolved_path != expected_path:
        raise RuntimeError(
            (
                f"Issue #{claimed_issue.issue['number']} {stage} resolved the wrong "
                f"worktree root: expected {expected_path}, got {resolved_path}."
            )
        )
    return resolved_path

def build_issue_run_id(issue_number: int) -> str:
    """Create a unique ID for an isolated issue run."""
    return f"{issue_number}-{uuid.uuid4().hex[:8]}"


def create_preflight_info_dir(worktree_path: str) -> str:
    """Create the ignored per-run directory for preflight handoff and evidence."""
    run_id = os.path.basename(os.path.abspath(worktree_path))
    preflight_info_path = os.path.join(
        get_repo_root(),
        "local_repositories",
        PREFLIGHT_INFO_DIRNAME,
        run_id,
    )
    os.makedirs(preflight_info_path, exist_ok=True)
    return preflight_info_path

def create_detached_worktree(
        repo_path: str,
        worktree_path: str,
        start_ref: str,
        error_message: str,
) -> None:
    """Create a detached worktree from the requested starting ref.

    The first worktree of a process also readies the Gradle dependency cache
    every worktree reads (§FS-forge-run-requirements.4).
    """
    try:
        subprocess.run(
            ["git", "worktree", "add", "--detach", worktree_path, start_ref],
            cwd=repo_path,
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
        )
    except subprocess.CalledProcessError as exc:
        print(f"ERROR: {error_message}: {exc.stdout}", file=sys.stderr)
        raise
    ensure_shared_dependency_cache(repo_path, worktree_path)


def fetch_default_base_ref(repo_path: str, operation: str) -> str:
    """Refresh and return the remote-tracking ref for the default issue base."""
    remote_tracking_ref = f"refs/remotes/origin/{DEFAULT_WORKTREE_BASE_REF}"
    try:
        run_git_transport(
            [
                "fetch",
                "--quiet",
                "origin",
                f"+{DEFAULT_WORKTREE_BASE_REF}:{remote_tracking_ref}",
            ],
            cwd=repo_path,
        )
    except GitTransportError as exc:
        print(
            f"ERROR: Failed to fetch origin/{DEFAULT_WORKTREE_BASE_REF} before {operation}: {exc}",
            file=sys.stderr,
        )
        raise
    return remote_tracking_ref


def resolve_git_commit(repo_path: str, ref: str) -> str:
    """Resolve one Git ref to an immutable commit SHA."""
    try:
        result = subprocess.run(
            ["git", "rev-parse", "--verify", f"{ref}^{{commit}}"],
            cwd=repo_path,
            env=git_env_limited_to_repo_root(repo_path),
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
        )
    except subprocess.CalledProcessError as exc:
        print(f"ERROR: Failed to resolve Git commit {ref}: {exc.stdout}", file=sys.stderr)
        raise
    commit = result.stdout.strip()
    if not commit:
        print(f"ERROR: Git ref {ref} resolved to an empty commit SHA", file=sys.stderr)
        raise RuntimeError(f"Git ref {ref} resolved to an empty commit SHA")
    return commit


def fetch_issue_base_commit(repo_path: str) -> str:
    """Fetch and pin the current origin/master commit before an issue claim.

    The checkout containing Forge may be on a feature branch; only the fetched
    remote-tracking ref supplies repository state for issue work
    (§FS-forge-run-requirements.2).
    """
    log_debug(
        "claim",
        f"Fetching newest origin/{DEFAULT_WORKTREE_BASE_REF} for the next issue claim",
    )
    remote_tracking_ref = fetch_default_base_ref(repo_path, "issue claim")
    commit = resolve_git_commit(repo_path, remote_tracking_ref)
    log_debug(
        "claim",
        f"Pinned origin/{DEFAULT_WORKTREE_BASE_REF} at {commit[:12]}",
    )
    return commit

def remove_worktree(repo_path: str, worktree_path: str) -> None:
    """Remove a detached worktree when it exists, with its Gradle daemons and home.

    §FS-forge-run-requirements.4
    """
    discard_worktree_gradle_home(worktree_path, repo_path)
    subprocess.run(
        ["git", "worktree", "remove", "--force", worktree_path],
        cwd=repo_path,
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )


def fetch_review_base_ref(repo_path: str) -> None:
    """Refresh the upstream PR base ref used by local review diffs."""
    fetch_default_base_ref(repo_path, "PR review")


def build_review_run_id(pr_number: int) -> str:
    """Create a unique ID for an isolated pull-request review run."""
    return f"pr-{pr_number}-{uuid.uuid4().hex[:8]}"


def create_review_workspace(base_reachability_metadata_path: str, pr_number: int) -> str:
    """Create an isolated review worktree and detach it at the target pull request."""
    repo_root = get_repo_root()
    review_worktrees_root = os.path.join(repo_root, "local_repositories", SCRATCH_REVIEW_WORKTREE_DIRNAME)
    os.makedirs(review_worktrees_root, exist_ok=True)

    review_run_id = build_review_run_id(pr_number)
    review_worktree_path = os.path.join(review_worktrees_root, review_run_id)
    fetch_review_base_ref(base_reachability_metadata_path)
    create_detached_worktree(
        base_reachability_metadata_path,
        review_worktree_path,
        DEFAULT_WORKTREE_BASE_REF,
        f"Failed to create review worktree for PR #{pr_number}",
    )
    try:
        gh(
            "pr",
            "checkout",
            str(pr_number),
            "--detach",
            cwd=review_worktree_path,
            check=True,
        )
    except subprocess.CalledProcessError as exc:
        remove_worktree(base_reachability_metadata_path, review_worktree_path)
        print(f"ERROR: Failed to check out PR #{pr_number} in review worktree: {exc.stdout}", file=sys.stderr)
        raise
    try:
        require_complete_reachability_repo(review_worktree_path)
    except SystemExit:
        remove_worktree(base_reachability_metadata_path, review_worktree_path)
        raise
    return review_worktree_path


def cleanup_review_workspace(
        base_reachability_metadata_path: str,
        review_worktree_path: str,
        pr_number: int,
) -> None:
    """Remove an isolated pull-request review worktree."""
    try:
        remove_worktree(base_reachability_metadata_path, review_worktree_path)
    except Exception as exc:
        print(
            f"ERROR: Failed to clean up review workspace for PR #{pr_number}: {exc!r}",
            file=sys.stderr,
        )


@pipeline_step(
    PHASE_CLAIM,
    STEP_CREATE_ISSUE_WORKSPACE,
    operand=lambda arguments: f"issue #{arguments['issue_number']}",
)
def create_issue_workspace(
        base_reachability_metadata_path: str,
        canonical_metrics_repo_path: str,
        issue_number: int,
        issue_base_commit: str = DEFAULT_WORKTREE_BASE_REF,
) -> tuple[str, str]:
    """Create an isolated issue worktree from the pinned repository base.

    §FS-forge-run-requirements.4
    """
    log_step_progress(
        PHASE_CLAIM,
        STEP_CREATE_ISSUE_WORKSPACE,
        f"Creating workspace for issue #{issue_number} from newest master {issue_base_commit[:12]}",
    )
    log_debug(
        "claim",
        f"Workspace base for issue #{issue_number}: {issue_base_commit}",
    )
    repo_root = get_repo_root()
    worktrees_root = os.path.join(repo_root, "local_repositories", SCRATCH_WORKTREE_DIRNAME)
    os.makedirs(worktrees_root, exist_ok=True)

    run_id = build_issue_run_id(issue_number)
    worktree_path = os.path.join(worktrees_root, run_id)

    create_detached_worktree(
        base_reachability_metadata_path,
        worktree_path,
        issue_base_commit,
        f"Failed to create worktree for issue #{issue_number} from {issue_base_commit}",
    )
    try:
        require_complete_reachability_repo(worktree_path)
    except SystemExit:
        remove_worktree(base_reachability_metadata_path, worktree_path)
        raise
    log_step_progress(
        PHASE_CLAIM,
        STEP_CREATE_ISSUE_WORKSPACE,
        f"Workspace created for issue #{issue_number}: {os.path.relpath(worktree_path, repo_root)}",
    )

    scratch_metrics_repo_path = os.path.join(worktree_path, get_forge_subdir_name())
    del canonical_metrics_repo_path, issue_number
    return worktree_path, scratch_metrics_repo_path


def cleanup_issue_workspace(claimed_issue: ClaimedIssue, canonical_metrics_repo_path: str) -> None:
    """Remove the isolated issue worktrees for reachability-metadata and metrics."""
    del canonical_metrics_repo_path
    if claimed_issue.preflight_info_path:
        shutil.rmtree(claimed_issue.preflight_info_path, ignore_errors=True)
    if claimed_issue.worktree_path in preservation_failed_worktree_paths:
        print(
            (
                f"[Keeping worktree for issue #{claimed_issue.issue['number']} because failed work "
                "could not be pushed; cleanup skipped.]"
            ),
            file=sys.stderr,
        )
    else:
        if is_fixture_testing_enabled():
            log_stage(
                "fixture-cleanup",
                (
                    f"Cleaning isolated fixture worktree for issue #{claimed_issue.issue['number']}: "
                    f"{claimed_issue.worktree_path}"
                ),
            )
        remove_worktree(claimed_issue.base_reachability_metadata_path, claimed_issue.worktree_path)
        if is_fixture_testing_enabled():
            log_stage(
                "fixture-cleanup",
                f"Cleaned isolated fixture worktree for issue #{claimed_issue.issue['number']}.",
            )
