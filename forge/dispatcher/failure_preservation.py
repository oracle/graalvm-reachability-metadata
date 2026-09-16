# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Durable preservation of failed work: branch, logs, and worktree state
(§AR-forge-dispatcher-decomposition, §FS-forge-run-continuation)."""


import os
import re
import shutil
import subprocess
import sys
import uuid
from dataclasses import dataclass
from git_scripts.common_git import get_origin_owner
from git_scripts.common_git import run_git_transport
from urllib.parse import quote
from utility_scripts.continuation_marker import continuation_marker_path
from utility_scripts.continuation_marker import load_continuation_marker
from utility_scripts.metrics_writer import PENDING_METRICS_FILENAME
from utility_scripts.repo_path_resolver import get_forge_subdir_name
from utility_scripts.repo_path_resolver import git_env_limited_to_repo_root
from utility_scripts.run_location import RunLocation
from utility_scripts.run_location import format_run_failure_line
from utility_scripts.run_location import marker_failure_location
from utility_scripts.run_location import resolve_failure_location
from utility_scripts.stage_logger import log_debug
from utility_scripts.stage_logger import log_stage
from utility_scripts.task_logs import display_log_path
from utility_scripts.task_logs import resolve_logs_root
from utility_scripts.task_logs import sanitize_library_log_segment
from dispatcher.config import (
    HUMAN_INTERVENTION_LOGS_DIRNAME,
    REPO,
)
from dispatcher.fixture_support import is_fixture_testing_enabled
from dispatcher.github_api import get_authenticated_user
from dispatcher.records import ClaimedIssue
from dispatcher.worktrees import require_claimed_issue_worktree

import json
from utility_scripts.continuation_marker import ContinuationMarker
from utility_scripts.metrics_writer import read_pending_metrics
from dispatcher.config import PUBLICATION_METRICS_EXTRA_KEYS

@dataclass(frozen=True)
class FailurePreservationResult:
    branch_name: str
    branch_url: str
    committed_changes: bool
    reviewable_worktree_path: str | None = None
    scratch_metrics_path: str | None = None
    copied_logs_destination: str | None = None
    copied_logs_destination_relpath: str | None = None
    fixture_mode: bool = False

    def to_json(self) -> dict:
        return {
            "branch_name": self.branch_name,
            "branch_url": self.branch_url,
            "committed_changes": self.committed_changes,
            "reviewable_worktree_path": self.reviewable_worktree_path,
            "scratch_metrics_path": self.scratch_metrics_path,
            "copied_logs_destination": self.copied_logs_destination,
            "copied_logs_destination_relpath": self.copied_logs_destination_relpath,
            "fixture_mode": self.fixture_mode,
        }

preservation_failed_worktree_paths: set[str] = set()

def _sanitize_branch_segment(value: str) -> str:
    """Return a branch-safe path segment."""
    return re.sub(r"[^A-Za-z0-9._-]+", "-", value).strip("-._") or "unknown"


def build_failure_preservation_branch_name(claimed_issue: ClaimedIssue) -> str:
    """Build a unique branch name for preserving a failed issue run."""
    branch_prefix = build_failure_preservation_branch_prefix(
        issue_number=int(claimed_issue.issue["number"]),
        label=claimed_issue.label,
        coordinate=claimed_issue.issue_coordinates,
        github_login=get_authenticated_user(),
    )
    return f"{branch_prefix}{uuid.uuid4().hex[:8]}"


def build_failure_preservation_branch_prefix(
        *,
        issue_number: int,
        label: str,
        coordinate: str,
        github_login: str,
) -> str:
    """Build the deterministic prefix for failed-work preservation branches."""
    authenticated_login = _sanitize_branch_segment(github_login)
    coordinate_segment = _sanitize_branch_segment(coordinate)
    label_segment = _sanitize_branch_segment(label)
    return (
        f"ai/{authenticated_login}/human-intervention/"
        f"issue-{issue_number}-{label_segment}-{coordinate_segment}-"
    )


def build_origin_branch_url(repo_path: str, branch_name: str) -> str:
    """Build the browser URL for a branch pushed to the origin remote."""
    try:
        origin_owner = get_origin_owner(cwd=repo_path)
    except subprocess.CalledProcessError:
        origin_owner = None
    if not origin_owner:
        origin_owner = REPO.split("/", 1)[0]
    repo_name = REPO.split("/", 1)[1]
    return f"https://github.com/{origin_owner}/{repo_name}/tree/{quote(branch_name, safe='')}"

def copy_library_logs_to_preserved_worktree(claimed_issue: ClaimedIssue) -> str | None:
    """Copy the current library logs into the preserved worktree and return the repo-relative path."""
    safe_library_name = sanitize_library_log_segment(claimed_issue.issue_coordinates)
    source_log_dir = os.path.join(resolve_logs_root(), safe_library_name)
    if not os.path.isdir(source_log_dir):
        log_stage(
            "preserve-failed-work",
            f"No workflow logs found for {claimed_issue.issue_coordinates}; skipping log copy.",
        )
        return None

    logs_destination_relpath = os.path.join(HUMAN_INTERVENTION_LOGS_DIRNAME, safe_library_name)
    logs_destination_path = os.path.join(claimed_issue.worktree_path, logs_destination_relpath)
    if os.path.isdir(logs_destination_path):
        shutil.rmtree(logs_destination_path)
    elif os.path.exists(logs_destination_path):
        os.remove(logs_destination_path)
    os.makedirs(os.path.dirname(logs_destination_path), exist_ok=True)
    shutil.copytree(source_log_dir, logs_destination_path)
    log_stage(
        "preserve-failed-work",
        "Copied workflow logs for {library} from {source} to {destination}".format(
            library=claimed_issue.issue_coordinates,
            source=display_log_path(source_log_dir),
            destination=logs_destination_relpath,
        ),
    )
    return logs_destination_relpath


def baseline_stats_relpaths_for_preservation(repo_path: str) -> list[str]:
    """Return improve-coverage baseline snapshots that must survive continuation."""
    tests_root = os.path.join(repo_path, "tests", "src")
    if not os.path.isdir(tests_root):
        return []

    relpaths: list[str] = []
    for root, _dirs, files in os.walk(tests_root):
        if ".baseline-stats.json" not in files:
            continue
        relpaths.append(os.path.relpath(os.path.join(root, ".baseline-stats.json"), repo_path))
    return sorted(relpaths)


def git_rebase_in_progress(repo_path: str, git_env: dict[str, str]) -> bool:
    """Return whether a rebase is currently halted in the given worktree."""
    # `git rev-parse --git-path` resolves the sequencer directories against the
    # worktree-specific gitdir, so this stays correct inside linked worktrees.
    for sequencer_dir in ("rebase-merge", "rebase-apply"):
        resolved = subprocess.run(
            ["git", "rev-parse", "--git-path", sequencer_dir],
            cwd=repo_path,
            env=git_env,
            check=True,
            capture_output=True,
            text=True,
        ).stdout.strip()
        if resolved and os.path.isdir(os.path.join(repo_path, resolved)):
            return True
    return False


def run_preservation_git(
        args: list[str],
        cwd: str,
        env: dict[str, str],
        check: bool = True,
) -> subprocess.CompletedProcess[str]:
    """Run one git command of the failure handoff without narrating it.

    Failure output is the location, the error, and the preserved branch, so the
    handoff's own git chatter is captured and replayed only when the command
    fails or when debug logging is on. §FS-forge-run-location-reporting.4
    """
    result = subprocess.run(
        ["git", *args],
        cwd=cwd,
        env=env,
        capture_output=True,
        text=True,
        check=False,
    )
    detail = "\n".join(
        stream.strip()
        for stream in (result.stdout, result.stderr)
        if stream and stream.strip()
    )
    if result.returncode != 0 and check:
        if detail:
            print(detail, file=sys.stderr)
        raise subprocess.CalledProcessError(result.returncode, ["git", *args], result.stdout, result.stderr)
    if detail:
        log_debug("preserve-failed-work", detail)
    return result


def preserve_failed_work_branch(claimed_issue: ClaimedIssue) -> FailurePreservationResult:
    """Commit and push the failed run worktree so it survives workspace cleanup."""
    branch_name = build_failure_preservation_branch_name(claimed_issue)
    repo_path = require_claimed_issue_worktree(claimed_issue, "failure preservation")
    git_env = git_env_limited_to_repo_root(repo_path)
    issue_number = claimed_issue.issue["number"]

    log_debug("preserve-failed-work", f"Preserving failed work for issue #{issue_number} on branch {branch_name}")
    # A publication `git rebase` that halts on an index.json conflict leaves the
    # worktree mid-rebase with unmerged entries, and `git switch -C` then refuses
    # ("resolve your current index first"). Clear the sequencer state first so the
    # generated work is still preserved for later resume. Gate the abort on an
    # actual rebase so the common clean-worktree path does not log a spurious
    # "fatal: No rebase in progress?" from git. §FS-forge-run-continuation
    if git_rebase_in_progress(repo_path, git_env):
        log_debug("preserve-failed-work", f"Aborting in-progress rebase before preserving issue #{issue_number}.")
        run_preservation_git(["rebase", "--abort"], repo_path, git_env, check=False)
    run_preservation_git(["switch", "-C", branch_name], repo_path, git_env)
    logs_destination_relpath = copy_library_logs_to_preserved_worktree(claimed_issue)
    marker_path = continuation_marker_path(repo_path)
    marker = load_continuation_marker(marker_path)
    if marker is not None:
        record_publication_metrics_from_pending(marker, claimed_issue.scratch_metrics_repo_path)
        marker.record_preserved_branch(branch_name)
        marker.save(marker_path)
    run_preservation_git(["add", "-A"], repo_path, git_env)
    if logs_destination_relpath is not None:
        run_preservation_git(["add", "-f", "--", logs_destination_relpath], repo_path, git_env)
    force_add_paths = []
    marker_relpath = os.path.relpath(marker_path, repo_path)
    if os.path.exists(marker_path):
        force_add_paths.append(marker_relpath)
    pending_metrics_relpath = os.path.join(get_forge_subdir_name(), PENDING_METRICS_FILENAME)
    if os.path.exists(os.path.join(repo_path, pending_metrics_relpath)):
        force_add_paths.append(pending_metrics_relpath)
    force_add_paths.extend(baseline_stats_relpaths_for_preservation(repo_path))
    if force_add_paths:
        run_preservation_git(["add", "-f", "--", *force_add_paths], repo_path, git_env)
    diff_result = run_preservation_git(["diff", "--cached", "--quiet"], repo_path, git_env, check=False)
    committed_changes = diff_result.returncode != 0
    if committed_changes:
        run_preservation_git(
            ["commit", "-m", f"Preserve failed automation work for issue #{issue_number}"],
            repo_path,
            git_env,
        )
    else:
        log_debug("preserve-failed-work", f"No uncommitted work found for issue #{issue_number}; pushing branch at current HEAD.")

    run_git_transport(["push", "-u", "origin", branch_name], cwd=repo_path, env=git_env)
    branch_url = build_origin_branch_url(repo_path, branch_name)
    log_stage("preserve-failed-work", f"Preserved failed work for issue #{issue_number}: {branch_url}")
    return FailurePreservationResult(
        branch_name=branch_name,
        branch_url=branch_url,
        committed_changes=committed_changes,
        reviewable_worktree_path=repo_path,
        scratch_metrics_path=claimed_issue.scratch_metrics_repo_path,
        copied_logs_destination=None if logs_destination_relpath is None else os.path.join(
            repo_path,
            logs_destination_relpath,
        ),
        copied_logs_destination_relpath=logs_destination_relpath,
    )


def build_fixture_failure_preservation_result(claimed_issue: ClaimedIssue) -> FailurePreservationResult:
    """Record the preservation branch that fixture mode would have pushed."""
    issue_number = claimed_issue.issue["number"]
    branch_name = build_failure_preservation_branch_name(claimed_issue)
    worktree_path = os.path.abspath(claimed_issue.worktree_path)
    logs_destination_relpath = None
    if os.path.isdir(worktree_path):
        logs_destination_relpath = copy_library_logs_to_preserved_worktree(claimed_issue)
    else:
        log_stage(
            "preserve-failed-work",
            f"Fixture mode: worktree for issue #{issue_number} is not present at {worktree_path}.",
        )
    return FailurePreservationResult(
        branch_name=branch_name,
        branch_url=f"fixture://preserved-work/{issue_number}/{quote(branch_name, safe='')}",
        committed_changes=False,
        reviewable_worktree_path=worktree_path,
        scratch_metrics_path=claimed_issue.scratch_metrics_repo_path,
        copied_logs_destination=None if logs_destination_relpath is None else os.path.join(
            worktree_path,
            logs_destination_relpath,
        ),
        copied_logs_destination_relpath=logs_destination_relpath,
        fixture_mode=True,
    )


def refresh_preserved_branch_logs(
        claimed_issue: ClaimedIssue,
        preservation_result: FailurePreservationResult | None,
) -> None:
    """Refresh library logs on an already pushed human-intervention branch."""
    if preservation_result is None:
        return
    if is_fixture_testing_enabled():
        log_stage(
            "preserve-failed-work",
            (
                f"Fixture mode: preserved branch log refresh for issue "
                f"#{claimed_issue.issue['number']} was already recorded locally."
            ),
        )
        return

    repo_path = require_claimed_issue_worktree(claimed_issue, "preserved log refresh")
    git_env = git_env_limited_to_repo_root(repo_path)
    issue_number = claimed_issue.issue["number"]
    run_preservation_git(["switch", preservation_result.branch_name], repo_path, git_env)
    logs_destination_relpath = copy_library_logs_to_preserved_worktree(claimed_issue)
    if logs_destination_relpath is None:
        return

    run_preservation_git(["add", "-f", "--", logs_destination_relpath], repo_path, git_env)
    diff_result = run_preservation_git(
        ["diff", "--cached", "--quiet", "--", logs_destination_relpath],
        repo_path,
        git_env,
        check=False,
    )
    if diff_result.returncode == 0:
        log_debug("preserve-failed-work", f"No new workflow logs to add for issue #{issue_number}.")
        return

    run_preservation_git(
        ["commit", "-m", f"Add automation logs for issue #{issue_number}"],
        repo_path,
        git_env,
    )
    run_git_transport(["push"], cwd=repo_path, env=git_env)
    log_debug("preserve-failed-work", f"Updated preserved branch logs for issue #{issue_number}.")


def resolve_claimed_issue_failure_location(
        claimed_issue: ClaimedIssue,
        exc: BaseException | None = None,
) -> RunLocation:
    """Return the phase/step this claimed issue failed in.

    Prefers the location the failing step recorded in this process; falls back to
    the continuation marker so a driver-side or resumed failure still names its
    step. §FS-forge-run-location-reporting.3
    """
    location = resolve_failure_location(exc)
    if location.is_located:
        return location
    marker = load_continuation_marker(continuation_marker_path(claimed_issue.worktree_path))
    return marker_failure_location(marker) or location


def lead_comment_with_failure_location(comment_body: str, location: RunLocation) -> str:
    """Lead the human-intervention comment with the same pair the terminal printed."""
    failure_line = format_run_failure_line(location)
    if comment_body.lstrip().startswith(("`" + failure_line, failure_line)):
        return comment_body
    return f"`{failure_line}`\n\n{comment_body}"


def ensure_preserved_branch_link_in_comment(
        comment_body: str,
        preservation_result: FailurePreservationResult | None,
) -> str:
    """Ensure the issue comment links to the branch that preserves failed work."""
    if preservation_result is None or preservation_result.branch_url in comment_body:
        return comment_body
    return (
        f"{comment_body.rstrip()}\n\n"
        "Preserved work branch:\n"
        f"{preservation_result.branch_url}"
    )


def record_publication_metrics_from_pending(
        marker: ContinuationMarker,
        metrics_repo_path: str,
) -> bool:
    """Copy local-only pending metrics fields into the continuation marker."""
    pending_metrics_path = os.path.join(metrics_repo_path, PENDING_METRICS_FILENAME)
    if not os.path.isfile(pending_metrics_path):
        return False
    try:
        run_metrics = read_pending_metrics(metrics_repo_path)
    except (OSError, json.JSONDecodeError, TypeError) as exc:
        log_stage(
            "publication",
            f"Could not snapshot pending metrics into continuation marker: {exc!r}.",
        )
        return False
    marker.record_publication_metrics(run_metrics, PUBLICATION_METRICS_EXTRA_KEYS)
    return marker.publication_metrics is not None
