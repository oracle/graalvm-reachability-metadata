# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Shared checkpoint-reset helpers for failed workflow runs (§WF-forge-workflow-drivers.3)."""

import os
import shutil
import subprocess
import tempfile


def reset_worktree_to_commit(repo_path: str, commit: str) -> str:
    """Hard-reset the worktree to a commit and return the resulting HEAD hash."""
    subprocess.run(["git", "reset", "--hard", commit], cwd=repo_path, check=True)
    return subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo_path, text=True).strip()


def snapshot_existing_paths(paths: list[str]) -> str | None:
    """Copy the existing paths into a temporary snapshot root, or return None when nothing exists."""
    snapshot_root = tempfile.mkdtemp(prefix="forge-update-failure-")
    copied = False
    for index, path in enumerate(paths):
        if not os.path.exists(path):
            continue
        destination = os.path.join(snapshot_root, str(index))
        if os.path.isdir(path):
            shutil.copytree(path, destination)
        else:
            shutil.copy2(path, destination)
        copied = True
    if copied:
        return snapshot_root
    shutil.rmtree(snapshot_root, ignore_errors=True)
    return None


def restore_snapshot_paths(snapshot_root: str | None, paths: list[str]) -> None:
    """Restore previously snapshotted paths over the current worktree content."""
    if snapshot_root is None:
        return
    for index, path in enumerate(paths):
        source = os.path.join(snapshot_root, str(index))
        if not os.path.exists(source):
            continue
        if os.path.exists(path):
            if os.path.isdir(path):
                shutil.rmtree(path)
            else:
                os.remove(path)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        if os.path.isdir(source):
            shutil.copytree(source, path)
        else:
            shutil.copy2(source, path)


def reset_worktree_preserving_paths(repo_path: str, commit: str, paths: list[str]) -> str:
    """Reset to a commit while preserving the current content of the given paths."""
    snapshot_root = snapshot_existing_paths(paths)
    try:
        subprocess.run(["git", "reset", "--hard", commit], cwd=repo_path, check=True)
        restore_snapshot_paths(snapshot_root, paths)
    finally:
        if snapshot_root is not None:
            shutil.rmtree(snapshot_root, ignore_errors=True)
    return subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo_path, text=True).strip()
