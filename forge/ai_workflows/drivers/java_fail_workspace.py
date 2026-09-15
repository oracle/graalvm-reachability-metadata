# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Workspace preparation and git checkpoints for the java-fail fix workflows."""

from __future__ import annotations

import os
import shutil
import subprocess
import sys
from typing import TYPE_CHECKING

from git_scripts.common_git import (
    build_ai_branch_name,
    delete_remote_branch_if_exists,
    switch_branch_quietly,
)
from utility_scripts.gradle_environment import gradle_command_environment
from utility_scripts.logged_command import run_logged_command
from utility_scripts.metadata_index import is_newer_than_latest_metadata_version
from utility_scripts.repo_path_resolver import require_complete_reachability_repo
from utility_scripts.stage_logger import log_detail
from utility_scripts.worktree_reset import reset_worktree_to_commit

if TYPE_CHECKING:
    from ai_workflows.drivers.java_fail_workflow import JavaFailWorkflowConfig

ADD_LIBRARY_METADATA_INDEX_TASK = "addLibraryMetadataIndexJson"
ADD_LIBRARY_AS_LATEST_METADATA_INDEX_TASK = "addLibraryAsLatestMetadataIndexJson"


def _same_filesystem_path(first_path: str, second_path: str) -> bool:
    """Return True when two paths resolve to the same filesystem location."""
    return os.path.normcase(os.path.realpath(first_path)) == os.path.normcase(os.path.realpath(second_path))


def copy_and_prepare_project_dir(
        group: str,
        artifact: str,
        library_version: str,
        updated_library_version: str,
) -> None:
    """Copy versioned test project directory and update version references."""
    src_dir = os.path.join("tests", "src", group, artifact, library_version)
    dst_dir = os.path.join("tests", "src", group, artifact, updated_library_version)
    if not os.path.isdir(src_dir):
        raise FileNotFoundError(f"Missing source test project directory: {src_dir}")

    if _same_filesystem_path(src_dir, dst_dir):
        log_detail("project-prep", f"Source and destination test project are the same: {dst_dir}")
    else:
        os.makedirs(dst_dir, exist_ok=True)
        shutil.copytree(src_dir, dst_dir, dirs_exist_ok=True)

    gradle_properties_path = os.path.join(dst_dir, "gradle.properties")
    if os.path.isfile(gradle_properties_path):
        with open(gradle_properties_path, "r", encoding="utf-8") as file:
            gradle_props_content = file.read()
        gradle_props_content_updated = gradle_props_content.replace(library_version, updated_library_version)
        with open(gradle_properties_path, "w", encoding="utf-8") as file:
            file.write(gradle_props_content_updated)


def run_gradle_task(task: str, coordinates: str) -> None:
    """Run a Gradle task quietly with durable output for the coordinates.

    §FS-forge-run-output-legibility §FS-durable-generation-logs
    """
    repo_path = os.getcwd()
    require_complete_reachability_repo(repo_path)
    command = ["./gradlew", task, f"-Pcoordinates={coordinates}"]
    result = run_logged_command(
        command,
        cwd=repo_path,
        task_type="java-fail-setup",
        subject=coordinates,
        action=task,
        env=gradle_command_environment(repo_path),
        stage="java-fail-setup",
    )
    if result.returncode != 0:
        raise subprocess.CalledProcessError(result.returncode, command, output=result.stdout)


def update_metadata_index_json(
        config: JavaFailWorkflowConfig,
        group: str,
        artifact: str,
        updated_library_version: str,
) -> None:
    """Update metadata index.json for the target library version."""
    new_version_coordinates = f"{group}:{artifact}:{updated_library_version}"
    metadata_index_task = metadata_index_update_task(group, artifact, updated_library_version)
    run_gradle_task(metadata_index_task, new_version_coordinates)


def metadata_index_update_task(group: str, artifact: str, updated_library_version: str) -> str:
    """Return the metadata index task for the target version."""
    if is_newer_than_latest_metadata_version(os.getcwd(), group, artifact, updated_library_version):
        return ADD_LIBRARY_AS_LATEST_METADATA_INDEX_TASK
    return ADD_LIBRARY_METADATA_INDEX_TASK


def create_versioned_metadata_dir(reachability_repo_path, group, artifact, updated_library_version):
    """Ensure the metadata directory for the updated library version exists."""
    metadata_version_dir = os.path.join(reachability_repo_path, "metadata", group, artifact, updated_library_version)
    os.makedirs(metadata_version_dir, exist_ok=True)


def create_project_prep_checkpoint(config: JavaFailWorkflowConfig, group, artifact, updated_library_version):
    """Reset the workflow branch to the current detached HEAD and commit project prep."""
    new_branch = build_ai_branch_name(
        f"{config.branch_prefix}-{group}-{artifact}-{updated_library_version}",
    )
    delete_remote_branch_if_exists(new_branch)
    switch_branch_quietly(new_branch)

    candidate_paths = [
        os.path.join("tests", "src", group, artifact, updated_library_version),
        os.path.join("metadata", group, artifact, "index.json"),
    ]

    subprocess.run(["git", "add", *candidate_paths], check=False)

    has_staged_changes = subprocess.run(["git", "diff", "--cached", "--quiet"], check=False)
    if has_staged_changes.returncode == 0:
        log_detail("project-prep", "No project preparation changes to commit.")
        return subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()

    subprocess.run(
        ["git", "commit", "-m", f"Prepare project for {group}:{artifact}:{updated_library_version}"],
        capture_output=True,
        text=True,
        check=True,
    )
    return subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()


def commit_last_passing_candidate(
        reachability_repo_path: str,
        group: str,
        artifact: str,
        updated_library_version: str,
) -> str | None:
    """Commit the generated candidate that already passed the workflow test loop."""
    candidate_paths: list[str] = [
        os.path.join("tests", "src", group, artifact, updated_library_version),
        os.path.join("metadata", group, artifact),
    ]
    add_result = subprocess.run(
        ["git", "add", "-A", "--", *candidate_paths],
        cwd=reachability_repo_path,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=False,
    )
    if add_result.returncode != 0:
        print("ERROR: Failed to stage last passing candidate.", file=sys.stderr)
        print(add_result.stdout)
        return None

    diff_result = subprocess.run(
        ["git", "diff", "--cached", "--quiet", "--", *candidate_paths],
        cwd=reachability_repo_path,
        check=False,
    )
    if diff_result.returncode == 0:
        return subprocess.check_output(
            ["git", "rev-parse", "HEAD"],
            cwd=reachability_repo_path,
            text=True,
        ).strip()
    if diff_result.returncode != 1:
        print("ERROR: Failed to inspect staged last passing candidate.", file=sys.stderr)
        return None

    commit_result = subprocess.run(
        ["git", "commit", "-m", f"Preserve last passing candidate for {group}:{artifact}:{updated_library_version}"],
        cwd=reachability_repo_path,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=False,
    )
    if commit_result.returncode != 0:
        print("ERROR: Failed to commit last passing candidate.", file=sys.stderr)
        print(commit_result.stdout)
        return None
    return subprocess.check_output(
        ["git", "rev-parse", "HEAD"],
        cwd=reachability_repo_path,
        text=True,
    ).strip()


def reset_failed_java_fix_worktree(
        reachability_repo_path: str,
        commit_checkpoint: str,
        last_passing_candidate_commit: str | None,
        tests_dir: str,
        metadata_dir: str,
        tests_dir_preexisted: bool,
        metadata_dir_preexisted: bool,
) -> str:
    """Reset failed workflow output while keeping a passing candidate when one exists."""
    reset_target = last_passing_candidate_commit or commit_checkpoint
    if last_passing_candidate_commit is not None:
        print(f"[Test fixing failed; preserving last passing candidate at {last_passing_candidate_commit}.]")
    head_commit = reset_worktree_to_commit(reachability_repo_path, reset_target)
    if last_passing_candidate_commit is None:
        if not tests_dir_preexisted:
            shutil.rmtree(tests_dir, ignore_errors=True)
        if not metadata_dir_preexisted:
            shutil.rmtree(metadata_dir, ignore_errors=True)
    return head_commit
