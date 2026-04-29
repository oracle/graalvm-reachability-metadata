# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import json
import os
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from git_scripts.common_git import get_origin_owner
from utility_scripts.metrics_writer import commit_run_metrics_with_retry
from utility_scripts.repo_path_resolver import ensure_local_metrics_repo, resolve_repo_roots


def _git(args: list[str], cwd: str) -> subprocess.CompletedProcess:
    return subprocess.run(
        ["git"] + args,
        cwd=cwd,
        check=True,
        capture_output=True,
        text=True,
    )


def _commit_file(repo_root: str, relative_path: str, content: str, message: str) -> None:
    absolute_path = os.path.join(repo_root, relative_path)
    parent_dir = os.path.dirname(absolute_path)
    if parent_dir:
        os.makedirs(parent_dir, exist_ok=True)
    with open(absolute_path, "w", encoding="utf-8") as file_handle:
        file_handle.write(content)
    _git(["add", relative_path], cwd=repo_root)
    _git(
        [
            "-c",
            "user.name=test",
            "-c",
            "user.email=test@example.com",
            "commit",
            "-m",
            message,
        ],
        cwd=repo_root,
    )


class GitWorktreeRegressionTests(unittest.TestCase):
    def test_defaults_to_parent_repo_and_in_repo_forge_metrics(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            reachability_repo = os.path.join(temp_dir, "graalvm-reachability-metadata")
            forge_root = os.path.join(reachability_repo, "forge")
            os.makedirs(os.path.join(reachability_repo, "metadata"))
            os.makedirs(os.path.join(reachability_repo, "tests"))
            os.makedirs(forge_root)
            _git(["init", "-b", "master"], cwd=reachability_repo)

            with patch("utility_scripts.repo_path_resolver.get_repo_root", return_value=forge_root):
                reachability_root, metrics_root = resolve_repo_roots(
                    None,
                    None,
                )

            self.assertEqual(reachability_root, reachability_repo)
            self.assertEqual(metrics_root, forge_root)
            self.assertTrue(os.path.isdir(os.path.join(forge_root, "script_run_metrics")))
            self.assertTrue(os.path.isdir(os.path.join(forge_root, "benchmark_run_metrics")))

    def test_explicit_reachability_path_uses_worktree_forge_metrics(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            worktree_path = os.path.join(temp_dir, "run-worktree")
            forge_root = os.path.join(worktree_path, "forge")
            os.makedirs(forge_root)

            reachability_root, metrics_root = resolve_repo_roots(
                worktree_path,
                None,
            )

            self.assertEqual(reachability_root, worktree_path)
            self.assertEqual(metrics_root, forge_root)
            self.assertTrue(os.path.isdir(os.path.join(forge_root, "script_run_metrics")))
            self.assertTrue(os.path.isdir(os.path.join(forge_root, "benchmark_run_metrics")))

    def test_ensure_local_metrics_repo_accepts_git_worktrees(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            canonical_repo = os.path.join(temp_dir, "metrics")
            os.makedirs(canonical_repo)
            _git(["init", "-b", "master"], cwd=canonical_repo)
            _commit_file(canonical_repo, "README.md", "seed\n", "seed")

            worktree_path = os.path.join(temp_dir, "metrics-worktree")
            _git(["worktree", "add", "--detach", worktree_path, "master"], cwd=canonical_repo)

            resolved_path = ensure_local_metrics_repo(worktree_path)

            self.assertEqual(resolved_path, worktree_path)
            self.assertTrue(os.path.isfile(os.path.join(worktree_path, ".git")))
            self.assertFalse(os.path.exists(os.path.join(worktree_path, ".gitignore")))

    def test_local_only_metrics_commit_targets_canonical_repo(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            canonical_repo = os.path.join(temp_dir, "metrics")
            ensure_local_metrics_repo(canonical_repo)

            worktree_path = os.path.join(temp_dir, "metrics-worktree")
            _git(["worktree", "add", "--detach", worktree_path, "master"], cwd=canonical_repo)

            run_metrics = {
                "library": "com.example:demo:1.0.0",
                "status": "success",
            }
            commit_run_metrics_with_retry(
                metrics_repo_root=worktree_path,
                metrics_json_relative_path="script_run_metrics/add_new_library_support.json",
                run_metrics=run_metrics,
                commit_message="Persist metrics from scratch worktree",
            )

            _git(["worktree", "remove", "--force", worktree_path], cwd=canonical_repo)

            metrics_json = os.path.join(
                canonical_repo,
                "script_run_metrics",
                "add_new_library_support.json",
            )
            with open(metrics_json, "r", encoding="utf-8") as file_handle:
                persisted = json.load(file_handle)

            self.assertEqual(persisted, [run_metrics])
            log_output = _git(["log", "--format=%s", "-1"], cwd=canonical_repo).stdout.strip()
            self.assertEqual(log_output, "Persist metrics from scratch worktree")

    def test_get_origin_owner_uses_origin_remote(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = os.path.join(temp_dir, "reachability")
            os.makedirs(repo_root)
            _git(["init", "-b", "master"], cwd=repo_root)
            _git(["remote", "add", "origin", "git@github.com:contributor/fork.git"], cwd=repo_root)
            _git(["remote", "add", "upstream", "git@github.com:oracle/graalvm-reachability-metadata.git"], cwd=repo_root)

            self.assertEqual(get_origin_owner(cwd=repo_root), "contributor")


if __name__ == "__main__":
    unittest.main()
