# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Preserved-branch discovery and run continuation markers
(§AR-forge-dispatcher-decomposition, §FS-forge-run-continuation)."""


import json
import os
import subprocess
import sys
from ai_workflows.drivers.library_update_router import LibraryUpdateRoute
from ai_workflows.drivers.library_update_router import write_library_update_route
from git_scripts.common_git import run_git_transport
from utility_scripts.continuation_marker import CONTINUATION_MARKER_FILENAME
from utility_scripts.continuation_marker import ContinuationMarker
from utility_scripts.continuation_marker import continuation_marker_path
from utility_scripts.continuation_marker import load_continuation_marker
from utility_scripts.continuation_marker import save_phase_update
from utility_scripts.library_preparation_preflight import load_library_preparation_preflight
from utility_scripts.library_preparation_preflight import write_library_preparation_preflight
from utility_scripts.repo_path_resolver import git_env_limited_to_repo_root
from utility_scripts.stage_logger import log_stage
from dispatcher.config import LABEL_LIBRARY_UPDATE
from dispatcher.failure_preservation import build_failure_preservation_branch_prefix
from dispatcher.issue_admin import (
    comment_author_login,
    get_issue_comments,
)
from dispatcher.issue_queue import issue_is_resumable
from dispatcher.records import ClaimedIssue

def list_remote_branches_by_prefix(repo_path: str, branch_prefix: str) -> list[str]:
    """Return remote branch names whose heads match the given prefix."""
    result = run_git_transport(
        ["ls-remote", "--heads", "origin", f"{branch_prefix}*"],
        cwd=repo_path,
        env=git_env_limited_to_repo_root(repo_path),
    )
    branch_names = []
    for line in result.stdout.splitlines():
        parts = line.split()
        if len(parts) < 2:
            continue
        ref_name = parts[1]
        if not ref_name.startswith("refs/heads/"):
            continue
        branch_name = ref_name[len("refs/heads/"):]
        if branch_name.startswith(branch_prefix):
            branch_names.append(branch_name)
    return sorted(set(branch_names))


def remote_branch_commit_timestamp(repo_path: str, branch_name: str) -> int:
    """Fetch a remote branch and return its tip commit timestamp."""
    remote_ref = fetch_remote_branch(repo_path, branch_name)
    result = subprocess.run(
        ["git", "show", "-s", "--format=%ct", remote_ref],
        cwd=repo_path,
        env=git_env_limited_to_repo_root(repo_path),
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=True,
    )
    return int(result.stdout.strip())


def sort_remote_branches_by_commit_time(repo_path: str, branch_names: list[str]) -> list[str]:
    """Return remote branch names newest-first by tip commit time."""
    return sorted(
        branch_names,
        key=lambda branch_name: remote_branch_commit_timestamp(repo_path, branch_name),
        reverse=True,
    )


def find_preserved_branch_candidates(
        issue: dict,
        label: str,
        coordinate: str,
        repo_path: str,
) -> list[str]:
    """Return likely preserved-work branches derived from recent comment authors."""
    issue_number = int(issue["number"])
    candidates = []
    seen_branches = set()
    seen_prefixes = set()
    for comment in reversed(get_issue_comments(issue_number)):
        author_login = comment_author_login(comment)
        if author_login is None:
            continue
        branch_prefix = build_failure_preservation_branch_prefix(
            issue_number=issue_number,
            label=label,
            coordinate=coordinate,
            github_login=author_login,
        )
        if branch_prefix in seen_prefixes:
            continue
        seen_prefixes.add(branch_prefix)
        for branch_name in sort_remote_branches_by_commit_time(
                repo_path,
                list_remote_branches_by_prefix(repo_path, branch_prefix),
        ):
            if branch_name in seen_branches:
                continue
            candidates.append(branch_name)
            seen_branches.add(branch_name)
    return candidates


def fetch_remote_branch(repo_path: str, branch_name: str) -> str:
    """Fetch a remote branch and return its local remote-tracking ref."""
    remote_ref = f"refs/remotes/origin/{branch_name}"
    run_git_transport(
        ["fetch", "origin", f"+refs/heads/{branch_name}:{remote_ref}"],
        cwd=repo_path,
        env=git_env_limited_to_repo_root(repo_path),
    )
    return remote_ref


def load_continuation_marker_from_branch(
        repo_path: str,
        branch_name: str,
) -> ContinuationMarker | None:
    """Load the continuation marker stored on a remote preserved branch."""
    remote_ref = fetch_remote_branch(repo_path, branch_name)
    marker_relpath = f"forge/{CONTINUATION_MARKER_FILENAME}"
    result = subprocess.run(
        ["git", "show", f"{remote_ref}:{marker_relpath}"],
        cwd=repo_path,
        env=git_env_limited_to_repo_root(repo_path),
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=False,
    )
    if result.returncode != 0:
        log_stage(
            "continuation",
            f"Preserved branch {branch_name} has no {marker_relpath}; running issue from scratch.",
        )
        return None
    try:
        marker = ContinuationMarker.from_dict(json.loads(result.stdout))
    except Exception as exc:
        print(
            f"ERROR: Failed to parse continuation marker on branch {branch_name}: {exc!r}",
            file=sys.stderr,
        )
        return None
    if marker.preserved_branch != branch_name:
        marker.record_preserved_branch(branch_name)
    return marker


def resolve_issue_continuation_marker(
        issue: dict,
        label: str,
        coordinate: str,
        base_reachability_metadata_path: str,
) -> ContinuationMarker | None:
    """Resolve a previously preserved continuation marker for this claim."""
    if not issue_is_resumable(issue):
        return None
    issue_number = int(issue["number"])
    for branch_name in find_preserved_branch_candidates(
            issue,
            label,
            coordinate,
            base_reachability_metadata_path,
    ):
        marker = load_continuation_marker_from_branch(base_reachability_metadata_path, branch_name)
        if marker is None:
            continue
        if marker.issue_number == issue_number and marker.label == label:
            return marker
        log_stage(
            "continuation",
            (
                f"Ignoring preserved marker on {branch_name}: "
                f"marker issue/label #{marker.issue_number} {marker.label} does not match "
                f"#{issue_number} {label}."
            ),
        )
    return None


def checkout_continuation_branch(
        worktree_path: str,
        marker: ContinuationMarker,
        issue_base_commit: str,
) -> bool:
    """Check out and rebase preserved work onto the pinned issue base.

    §FS-forge-run-requirements.4
    """
    branch_name = marker.preserved_branch
    if not branch_name:
        return False
    git_env = git_env_limited_to_repo_root(worktree_path)
    remote_ref = fetch_remote_branch(worktree_path, branch_name)
    subprocess.run(
        ["git", "switch", "-C", branch_name, remote_ref],
        cwd=worktree_path,
        env=git_env,
        check=True,
    )
    rebase_result = subprocess.run(
        ["git", "rebase", issue_base_commit],
        cwd=worktree_path,
        env=git_env,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=False,
    )
    if rebase_result.returncode == 0:
        log_stage(
            "continuation",
            f"Resuming issue #{marker.issue_number} from {branch_name} at phase {marker.continue_from}.",
        )
        return True
    print(
        (
            f"ERROR: Could not rebase preserved branch {branch_name} onto "
            f"pinned issue base {issue_base_commit[:12]}; falling back to a clean run.\n"
            f"{rebase_result.stdout}"
        ),
        file=sys.stderr,
    )
    subprocess.run(["git", "rebase", "--abort"], cwd=worktree_path, env=git_env, check=False)
    subprocess.run(
        ["git", "switch", "--detach", issue_base_commit],
        cwd=worktree_path,
        env=git_env,
        check=True,
    )
    marker_path = continuation_marker_path(worktree_path)
    if os.path.exists(marker_path):
        os.remove(marker_path)
    return False


def create_or_load_run_continuation_marker(
        claimed_issue: ClaimedIssue,
        strategy_name: str,
) -> str:
    """Ensure this claimed run has an eager continuation marker."""
    marker_path = continuation_marker_path(claimed_issue.worktree_path)
    marker = load_continuation_marker(marker_path)
    if marker is None:
        marker = claimed_issue.continuation_marker or ContinuationMarker.create(
            strategy_name=strategy_name,
            issue_number=int(claimed_issue.issue["number"]),
            label=claimed_issue.label,
            coordinate=claimed_issue.issue_coordinates,
            new_version=claimed_issue.new_version,
        )
    marker.save(marker_path)
    return marker_path


def library_update_route_from_marker(marker: ContinuationMarker | None) -> LibraryUpdateRoute | None:
    """Return the marker-backed library-update route when continuation has one."""
    if marker is None or marker.library_update_route is None:
        return None
    return LibraryUpdateRoute.from_json(marker.library_update_route)


def restore_library_update_route_from_marker(
        claimed_issue: ClaimedIssue,
        marker: ContinuationMarker | None,
        *,
        required: bool = False,
) -> LibraryUpdateRoute | None:
    """Restore marker-backed library-update route state into the run sidecar."""
    if claimed_issue.label != LABEL_LIBRARY_UPDATE:
        return None
    route = library_update_route_from_marker(marker)
    if route is None:
        if required:
            raise ValueError(
                f"Issue #{claimed_issue.issue['number']} resumes publication without a library-update route."
            )
        return None
    write_library_update_route(library_update_route_artifact_root(claimed_issue), route)
    log_stage(
        "continuation",
        f"Restored library-update route {route.selected_driver} for issue #{claimed_issue.issue['number']}.",
    )
    return route


def record_library_update_route_in_marker(marker_path: str | None, route: LibraryUpdateRoute | None) -> None:
    """Persist the selected library-update route into the continuation marker."""
    if marker_path is None or route is None:
        return
    save_phase_update(
        marker_path,
        lambda marker: marker.record_library_update_route(route.to_json()),
    )


def restore_library_preparation_preflight_from_marker(
        claimed_issue: ClaimedIssue,
        marker: ContinuationMarker | None,
) -> str | None:
    """Restore marker-backed dispatcher preflight state into the run sidecar."""
    if marker is None or marker.library_preparation_preflight is None:
        return None
    preflight_root = claimed_issue.preflight_info_path or claimed_issue.scratch_metrics_repo_path
    preflight_path = write_library_preparation_preflight(
        preflight_root,
        marker.library_preparation_preflight,
    )
    log_stage(
        "continuation",
        f"Restored library preparation preflight for issue #{claimed_issue.issue['number']}.",
    )
    return preflight_path


def record_library_preparation_preflight_in_marker(
        marker_path: str | None,
        preflight_path: str | None,
) -> None:
    """Persist dispatcher preflight output into the continuation marker."""
    if marker_path is None or preflight_path is None:
        return
    preflight = load_library_preparation_preflight(preflight_path)
    if preflight is None:
        return
    save_phase_update(
        marker_path,
        lambda marker: marker.record_library_preparation_preflight(preflight),
    )


def library_update_route_artifact_root(claimed_issue: ClaimedIssue) -> str:
    """Return the non-worktree artifact root for library-update route handoff."""
    return claimed_issue.preflight_info_path or claimed_issue.scratch_metrics_repo_path
