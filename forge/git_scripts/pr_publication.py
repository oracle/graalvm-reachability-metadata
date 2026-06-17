# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Shared branch-publication pipeline for the make_pr_* publishers (§GIT-shared-publication-pipeline)."""

import os
import re
import shutil
import subprocess
import tempfile
from collections.abc import Callable

from git_scripts.common_git import (
    build_ai_branch_name,
    delete_remote_branch_if_exists,
    get_configured_reviewers,
    git_files_under,
    is_java_fix_test_module_file,
    run_git_transport,
    stage_and_commit as stage_and_commit_common,
)
from utility_scripts.library_stats import stats_artifact_dir
from utility_scripts.local_ci_verification import (
    LocalCIVerificationResult,
    fetch_pr_base_ref,
    run_local_ci_verification,
)
from utility_scripts.continuation_marker import (
    CONTINUATION_MARKER_FILENAME,
    PHASE_PUBLICATION,
    ContinuationMarker,
    continuation_marker_path,
    load_continuation_marker,
)
from utility_scripts.metrics_writer import PENDING_METRICS_FILENAME

REPO: str = "oracle/graalvm-reachability-metadata"
BASE_BRANCH: str = "master"
REVIEWERS: list[str] = get_configured_reviewers()
PRESERVATION_ONLY_PATHS: set[str] = {
    f"forge/{CONTINUATION_MARKER_FILENAME}",
    f"forge/{PENDING_METRICS_FILENAME}",
}
PRESERVATION_ONLY_DIRECTORIES: tuple[str, ...] = ("forge/human-intervention-logs",)
PRESERVATION_ONLY_PREFIXES: tuple[str, ...] = ("forge/human-intervention-logs/",)


def _publication_resume_marker(repo_path: str) -> ContinuationMarker | None:
    """Return the publication continuation marker when this run resumes publication."""
    marker = load_continuation_marker(continuation_marker_path(repo_path))
    if marker is None or marker.continue_from != PHASE_PUBLICATION:
        return None
    return marker


def _record_publication_pushed(
        repo_path: str,
        branch: str,
        marker: ContinuationMarker | None = None,
) -> None:
    """Record the push boundary in the continuation marker if one exists."""
    marker_path = continuation_marker_path(repo_path)
    active_marker = marker or load_continuation_marker(marker_path)
    if active_marker is None:
        return
    active_marker.record_publication_pushed(branch)
    active_marker.save(marker_path)


def _record_publication_branch(
        repo_path: str,
        branch: str,
        marker: ContinuationMarker | None = None,
) -> None:
    """Record the branch namespace before publication reaches the push."""
    marker_path = continuation_marker_path(repo_path)
    active_marker = marker or load_continuation_marker(marker_path)
    if active_marker is None:
        return
    active_marker.record_publication_branch(branch)
    active_marker.save(marker_path)


def _is_preservation_only_path(path: str) -> bool:
    """Return True for files that belong only on failed-run preservation branches."""
    normalized_path = path.replace(os.sep, "/")
    return (
        normalized_path in PRESERVATION_ONLY_PATHS
        or any(normalized_path.startswith(prefix) for prefix in PRESERVATION_ONLY_PREFIXES)
    )


def _commit_touched_paths(repo_path: str, commit: str) -> set[str]:
    """Return paths touched by one commit."""
    output = subprocess.check_output(
        ["git", "diff-tree", "--no-commit-id", "--name-only", "-r", commit],
        cwd=repo_path,
        text=True,
    )
    return {line.strip() for line in output.splitlines() if line.strip()}


def _non_preservation_commits(repo_path: str, base_ref: str, head_ref: str) -> list[str]:
    """Return commits to replay while excluding preservation-only wrapper commits."""
    output = subprocess.check_output(
        ["git", "rev-list", "--reverse", f"{base_ref}..{head_ref}"],
        cwd=repo_path,
        text=True,
    )
    commits: list[str] = []
    for commit in [line.strip() for line in output.splitlines() if line.strip()]:
        touched_paths = _commit_touched_paths(repo_path, commit)
        if touched_paths and all(_is_preservation_only_path(path) for path in touched_paths):
            continue
        commits.append(commit)
    return commits


def _has_staged_changes(repo_path: str) -> bool:
    """Return True when the index contains changes ready to commit."""
    result = subprocess.run(["git", "diff", "--cached", "--quiet"], cwd=repo_path, check=False)
    return result.returncode != 0


def _remove_preservation_only_files(repo_path: str) -> None:
    """Remove files that are tracked only on failed-run preservation branches."""
    paths: list[str] = sorted(PRESERVATION_ONLY_PATHS)
    pathspecs = [*paths, *PRESERVATION_ONLY_DIRECTORIES]
    tracked_paths = subprocess.check_output(
        ["git", "ls-files", "--", *pathspecs],
        cwd=repo_path,
        text=True,
    ).splitlines()
    if tracked_paths:
        subprocess.run(["git", "rm", "-f", "-q", "--", *tracked_paths], cwd=repo_path, check=True)
    for path in paths:
        absolute_path = os.path.join(repo_path, path)
        if os.path.isfile(absolute_path):
            os.remove(absolute_path)
    for directory in PRESERVATION_ONLY_DIRECTORIES:
        absolute_directory = os.path.join(repo_path, directory)
        if os.path.isdir(absolute_directory):
            shutil.rmtree(absolute_directory)


def _commit_preservation_artifact_clearance(repo_path: str) -> None:
    """Commit removal of resume helper artifacts when publication resumes."""
    _remove_preservation_only_files(repo_path)
    if _has_staged_changes(repo_path):
        subprocess.run(["git", "commit", "-m", "Clear resume helper artifacts"], check=True, cwd=repo_path)


def _prepare_unpushed_publication_resume_branch(
        repo_path: str,
        branch: str,
        base_ref: str,
        marker: ContinuationMarker,
) -> None:
    """Create a PR branch from base and replay only non-preservation run commits."""
    preserved_head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo_path, text=True).strip()
    commits = _non_preservation_commits(repo_path, base_ref, preserved_head)
    subprocess.run(["git", "switch", "-C", branch, base_ref], check=True, cwd=repo_path)
    _record_publication_branch(repo_path, branch, marker)
    for commit in commits:
        subprocess.run(["git", "cherry-pick", commit], check=True, cwd=repo_path)
    _commit_preservation_artifact_clearance(repo_path)
    _record_publication_branch(repo_path, branch, marker)


def publish_branch(
        repo_path: str,
        branch_suffix: str,
        coordinates: str,
        stage: Callable[[], None],
        metrics_repo_path: str | None = None,
        before_rebase: Callable[[], None] | None = None,
        after_verification: Callable[[], None] | None = None,
) -> tuple[str, LocalCIVerificationResult]:
    """Create, stage, rebase, verify, and push the publication branch.

    The one shared path from a verified worktree to a pushed PR branch
    (§GIT-shared-publication-pipeline): publishers supply only their staging
    policy (§GIT-expected-paths) and optional pre-rebase / post-verification
    assertions. Local CI-equivalent verification always runs before the push
    that makes the branch PR-eligible (§GIT-pr-eligibility).
    """
    resume_marker = _publication_resume_marker(repo_path)
    resume_branch = None if resume_marker is None or not resume_marker.publication_is_pushed() else resume_marker.publication_branch()
    if resume_branch is not None:
        commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo_path, text=True).strip()
        return resume_branch, LocalCIVerificationResult(
            status="skipped-publication-resume",
            base_commit=commit,
            final_commit=commit,
        )

    base_ref = fetch_pr_base_ref(repo_path, REPO, BASE_BRANCH)
    branch = None if resume_marker is None else resume_marker.publication_branch()
    if branch is None:
        branch = build_ai_branch_name(branch_suffix, cwd=repo_path)
    _record_publication_branch(repo_path, branch, resume_marker)
    delete_remote_branch_if_exists(branch, cwd=repo_path)
    if resume_marker is None:
        subprocess.run(["git", "switch", "-C", branch], check=True, cwd=repo_path)
        _remove_preservation_only_files(repo_path)
    else:
        _prepare_unpushed_publication_resume_branch(repo_path, branch, base_ref, resume_marker)
    stage()
    if before_rebase is not None:
        before_rebase()
    subprocess.run(["git", "rebase", base_ref], check=True, cwd=repo_path)
    local_ci_verification = run_local_ci_verification(
        repo_path=repo_path,
        coordinates=coordinates,
        base_commit=base_ref,
        metrics_repo_path=metrics_repo_path,
    )
    if after_verification is not None:
        after_verification()
    run_git_transport(["push", "origin", branch], cwd=repo_path)
    _record_publication_pushed(repo_path, branch, resume_marker)
    return branch, local_ci_verification


def stage_library_version_paths(
        group: str,
        artifact: str,
        library_version: str,
        repo_path: str,
        commit_message: str,
) -> None:
    """Stage the standard per-version tests/metadata/stats paths and commit.

    Publication scripts stage the workflow-specific expected paths instead of a
    generic repository-wide add, keeping the path boundary explicit
    (§GIT-expected-paths).
    """
    candidate_paths: list[str] = [
        str(os.path.join("tests", "src", group, artifact, library_version)),
        str(os.path.join("metadata", group, artifact, "index.json")),
        str(os.path.join("metadata", group, artifact, library_version)),
        str(os.path.relpath(stats_artifact_dir(repo_path, group, artifact), repo_path)),
    ]
    candidate_paths = [path for path in candidate_paths if os.path.exists(os.path.join(repo_path, path))]
    stage_and_commit_common(candidate_paths, commit_message, cwd=repo_path)


def parse_pr_number(output: str) -> int | None:
    """Extract a PR number from gh pr create output."""
    match = re.search(r"/pull/(\d+)", output)
    return int(match.group(1)) if match else None


def _copy_git_files_under(repo_path: str, source_dir: str, destination_dir: str) -> None:
    os.makedirs(destination_dir, exist_ok=True)
    for git_path in git_files_under(repo_path, source_dir):
        source_file = os.path.join(repo_path, git_path)
        relative_file = os.path.relpath(source_file, source_dir)
        if relative_file.startswith(os.pardir + os.sep) or relative_file == os.pardir:
            continue
        if not is_java_fix_test_module_file(relative_file):
            continue
        destination_file = os.path.join(destination_dir, relative_file)
        os.makedirs(os.path.dirname(destination_file), exist_ok=True)
        shutil.copy2(source_file, destination_file)


def generate_diff_text(group: str, artifact: str, current_version: str, new_version: str, repo_path: str) -> str:
    """Build a diff text between the current and new test directories for a library version."""
    old_dir = os.path.join(repo_path, "tests", "src", group, artifact, current_version)
    new_dir = os.path.join(repo_path, "tests", "src", group, artifact, new_version)
    old_display_dir = os.path.relpath(old_dir, repo_path).replace(os.sep, "/")
    new_display_dir = os.path.relpath(new_dir, repo_path).replace(os.sep, "/")
    old_diff_display_dir = f"/{old_display_dir}"
    new_diff_display_dir = f"/{new_display_dir}"

    # Prepare temp copies from Git's file list with a narrow allow-list for the PR body diff.
    with tempfile.TemporaryDirectory() as tmpdir:
        tmp_old = os.path.join(tmpdir, "old")
        tmp_new = os.path.join(tmpdir, "new")

        _copy_git_files_under(repo_path, old_dir, tmp_old)
        _copy_git_files_under(repo_path, new_dir, tmp_new)

        # Compute diff text using: git diff --no-index --find-renames tmp_old tmp_new
        res = subprocess.run(
            ["git", "diff", "--no-index", "--find-renames", tmp_old, tmp_new],
            capture_output=True,
            text=True,
        )
        if res.returncode in (0, 1):
            diff_text = res.stdout

            diff_text = diff_text.replace(tmp_old.replace(os.sep, "/"), old_diff_display_dir)
            diff_text = diff_text.replace(tmp_new.replace(os.sep, "/"), new_diff_display_dir)
            diff_text = diff_text.replace(tmp_old, old_diff_display_dir)
            diff_text = diff_text.replace(tmp_new, new_diff_display_dir)
            return diff_text

        error_message = (
            f"Subprocess failed with return code: {res.returncode}. "
            f"Error output:\n{res.stderr.strip()}"
        )
        raise RuntimeError(error_message)
