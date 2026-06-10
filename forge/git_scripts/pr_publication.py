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
from utility_scripts.large_library_progress import LargeLibraryProgressState
from utility_scripts.library_stats import stats_artifact_dir
from utility_scripts.local_ci_verification import (
    LocalCIVerificationResult,
    fetch_pr_base_ref,
    run_local_ci_verification,
)

REPO: str = "oracle/graalvm-reachability-metadata"
BASE_BRANCH: str = "master"
REVIEWERS: list[str] = get_configured_reviewers()


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
    branch = build_ai_branch_name(branch_suffix, cwd=repo_path)
    delete_remote_branch_if_exists(branch, cwd=repo_path)
    subprocess.run(["git", "switch", "-C", branch], check=True, cwd=repo_path)
    stage()
    if before_rebase is not None:
        before_rebase()
    base_ref = fetch_pr_base_ref(repo_path, REPO, BASE_BRANCH)
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


def update_large_library_state_after_publish(
        state_path: str | None,
        branch: str,
        repo_path: str,
        pr_number: int | None,
        final_part: bool,
) -> None:
    """Record the published branch/commit in the large-library progress artifact.

    The next chunk resumes only after this published state is present on the
    base branch (§WF-dynamic-access-exhaust-report).
    """
    if not state_path:
        return
    state = LargeLibraryProgressState.load(state_path)
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo_path, text=True).strip()
    state.record_published_pr(branch, commit, pr_number)
    if not final_part:
        state.advance_to_next_part()
    state.save(state_path)


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
