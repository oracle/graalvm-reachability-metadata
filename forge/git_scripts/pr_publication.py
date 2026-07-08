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
LIBRARY_UPDATE_TARGET_FILENAME: str = ".library_update_target.json"
PRESERVATION_ONLY_PATHS: set[str] = {
    f"forge/{CONTINUATION_MARKER_FILENAME}",
}
LOCAL_PUBLICATION_INPUT_PATHS: set[str] = {
    f"forge/{PENDING_METRICS_FILENAME}",
    f"forge/{LIBRARY_UPDATE_TARGET_FILENAME}",
}
PRESERVATION_ONLY_DIRECTORIES: tuple[str, ...] = (
    "human-intervention-logs",
    "forge/human-intervention-logs",
)
MAX_PR_BODY_CHARS: int = 60_000
MAX_INLINE_TEST_DIFF_CHARS: int = 12_000
PR_BODY_REQUIRED_TAIL_CHARS: int = 12_000


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


def _has_staged_changes(repo_path: str) -> bool:
    """Return True when the index contains changes ready to commit."""
    result = subprocess.run(["git", "diff", "--cached", "--quiet"], cwd=repo_path, check=False)
    return result.returncode != 0


def _snapshot_local_publication_inputs(repo_path: str) -> dict[str, bytes]:
    """Read local-only inputs that PR creation still needs after branch cleanup."""
    snapshots: dict[str, bytes] = {}
    for path in sorted(LOCAL_PUBLICATION_INPUT_PATHS):
        absolute_path = os.path.join(repo_path, path)
        if os.path.isfile(absolute_path):
            with open(absolute_path, "rb") as local_input:
                snapshots[path] = local_input.read()
    return snapshots


def _restore_local_publication_inputs(repo_path: str, snapshots: dict[str, bytes]) -> None:
    """Restore PR inputs as untracked files after removing them from the branch."""
    for path, contents in snapshots.items():
        absolute_path = os.path.join(repo_path, path)
        os.makedirs(os.path.dirname(absolute_path), exist_ok=True)
        with open(absolute_path, "wb") as local_input:
            local_input.write(contents)


def _remove_preservation_only_files(repo_path: str) -> None:
    """Remove files that are tracked only on failed-run preservation branches."""
    local_input_snapshots = _snapshot_local_publication_inputs(repo_path)
    paths: list[str] = sorted(PRESERVATION_ONLY_PATHS | LOCAL_PUBLICATION_INPUT_PATHS)
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
    _restore_local_publication_inputs(repo_path, local_input_snapshots)


def _commit_preservation_artifact_clearance(repo_path: str) -> None:
    """Commit removal of resume helper artifacts when publication resumes."""
    _remove_preservation_only_files(repo_path)
    if _has_staged_changes(repo_path):
        subprocess.run(["git", "commit", "-m", "Clear resume helper artifacts"], check=True, cwd=repo_path)


def _prepare_unpushed_publication_resume_branch(
        repo_path: str,
        branch: str,
        marker: ContinuationMarker,
) -> None:
    """Create the PR branch from the resumed preserved branch and clear helpers."""
    subprocess.run(["git", "switch", "-C", branch], check=True, cwd=repo_path)
    _record_publication_branch(repo_path, branch, marker)
    _commit_preservation_artifact_clearance(repo_path)
    _record_publication_branch(repo_path, branch, marker)


def publish_branch(
        repo_path: str,
        branch_suffix: str,
        coordinates: str,
        stage: Callable[[], None],
        metrics_repo_path: str | None = None,
        before_rebase: Callable[[], None] | None = None,
        before_verification: Callable[[str], None] | None = None,
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
        _prepare_unpushed_publication_resume_branch(repo_path, branch, resume_marker)
    stage()
    if before_rebase is not None:
        before_rebase()
    subprocess.run(["git", "rebase", base_ref], check=True, cwd=repo_path)
    if before_verification is not None:
        # Library-update publishers may refine alias buckets before local CI
        # decides PR eligibility. §FS-library-update-tested-version-split
        before_verification(base_ref)
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


def _generate_test_diff_outputs(
        group: str,
        artifact: str,
        current_version: str,
        new_version: str,
        repo_path: str,
) -> tuple[str, str]:
    """Build full and stat test diffs for bounded PR descriptions (§GIT-pr-body)."""
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

        diff_args = ["git", "diff", "--no-index", "--find-renames"]
        res = subprocess.run(
            [*diff_args, tmp_old, tmp_new],
            capture_output=True,
            text=True,
        )
        if res.returncode in (0, 1):
            diff_text = res.stdout
            stat_res = subprocess.run(
                [*diff_args, "--stat", tmp_old, tmp_new],
                capture_output=True,
                text=True,
            )
            if stat_res.returncode not in (0, 1):
                raise RuntimeError(
                    f"Subprocess failed with return code: {stat_res.returncode}. "
                    f"Error output:\n{stat_res.stderr.strip()}"
                )

            diff_text = diff_text.replace(tmp_old.replace(os.sep, "/"), old_diff_display_dir)
            diff_text = diff_text.replace(tmp_new.replace(os.sep, "/"), new_diff_display_dir)
            diff_text = diff_text.replace(tmp_old, old_diff_display_dir)
            diff_text = diff_text.replace(tmp_new, new_diff_display_dir)
            return diff_text, stat_res.stdout

        error_message = (
            f"Subprocess failed with return code: {res.returncode}. "
            f"Error output:\n{res.stderr.strip()}"
        )
        raise RuntimeError(error_message)


def generate_diff_text(group: str, artifact: str, current_version: str, new_version: str, repo_path: str) -> str:
    """Build the complete test diff for callers that need it."""
    diff_text, _diff_stat = _generate_test_diff_outputs(
        group, artifact, current_version, new_version, repo_path,
    )
    return diff_text


def format_bounded_test_diff_section(
        group: str,
        artifact: str,
        current_version: str,
        new_version: str,
        repo_path: str,
) -> str:
    """Format a reviewable test diff excerpt that cannot exhaust a PR body (§GIT-pr-body)."""
    diff_text, diff_stat = _generate_test_diff_outputs(
        group, artifact, current_version, new_version, repo_path,
    )
    excerpt = diff_text[:MAX_INLINE_TEST_DIFF_CHARS]
    truncation_note = ""
    if len(diff_text) > MAX_INLINE_TEST_DIFF_CHARS:
        truncation_note = (
            "\n\nThe complete test diff is available in this pull request's "
            "**Files changed** tab."
        )
    return (
        "**Test-source comparison**\n\n"
        "```text\n"
        f"{diff_stat.strip()}\n"
        "```\n\n"
        "```diff\n"
        f"{excerpt}\n"
        "```"
        f"{truncation_note}"
    )


def bound_pr_body(body: str) -> str:
    """Keep all publisher PR bodies below GitHub's limit (§GIT-pr-body)."""
    if len(body) <= MAX_PR_BODY_CHARS:
        return body
    truncation_notice = (
        "\n\n---\n\nAdditional generated detail was omitted to keep this pull request "
        "description within GitHub's size limit."
    )
    head_chars = MAX_PR_BODY_CHARS - len(truncation_notice) - PR_BODY_REQUIRED_TAIL_CHARS
    return body[:head_chars].rstrip() + truncation_notice + body[-PR_BODY_REQUIRED_TAIL_CHARS:]
