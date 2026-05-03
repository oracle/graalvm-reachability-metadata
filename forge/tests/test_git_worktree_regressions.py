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

import forge_metadata
from git_scripts.common_git import get_origin_owner
from utility_scripts.metrics_writer import commit_run_metrics_with_retry
from utility_scripts.repo_path_resolver import (
    ensure_local_metrics_repo,
    require_complete_reachability_repo,
    resolve_repo_roots,
)


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


def _create_reachability_repo(repo_root: str) -> str:
    os.makedirs(repo_root, exist_ok=True)
    _git(["init", "-b", "master"], cwd=repo_root)
    for directory in ("forge", "metadata", "tests", os.path.join("gradle", "wrapper")):
        os.makedirs(os.path.join(repo_root, directory), exist_ok=True)
    _commit_file(repo_root, "gradlew", "#!/usr/bin/env sh\n", "add gradle wrapper")
    _commit_file(repo_root, "settings.gradle", "rootProject.name = 'test'\n", "add settings")
    _commit_file(repo_root, "build.gradle", "plugins { id 'java' }\n", "add build")
    _commit_file(repo_root, "gradle/wrapper/gradle-wrapper.jar", "wrapper jar\n", "add wrapper jar")
    _commit_file(
        repo_root,
        "gradle/wrapper/gradle-wrapper.properties",
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-bin.zip\n",
        "add wrapper properties",
    )
    _commit_file(repo_root, "forge/.gitkeep", "", "add forge")
    _commit_file(repo_root, "metadata/.gitkeep", "", "add metadata")
    _commit_file(repo_root, "tests/.gitkeep", "", "add tests")
    return repo_root


class GitWorktreeRegressionTests(unittest.TestCase):
    def test_complete_reachability_repo_validation_accepts_full_checkout(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = _create_reachability_repo(os.path.join(temp_dir, "graalvm-reachability-metadata"))

            self.assertEqual(require_complete_reachability_repo(repo_root), repo_root)

    def test_complete_reachability_repo_validation_rejects_partial_library_dir(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            partial_dir = os.path.join(temp_dir, "tests", "src", "org.example", "demo", "1.0.0")
            os.makedirs(partial_dir)

            with self.assertRaises(SystemExit):
                require_complete_reachability_repo(partial_dir)

    def test_complete_reachability_repo_validation_rejects_downloaded_artifact_dir(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            artifact_dir = os.path.join(temp_dir, "source_context", "org.example", "demo", "1.0.0", "main")
            os.makedirs(artifact_dir)

            with self.assertRaises(SystemExit):
                require_complete_reachability_repo(artifact_dir)

    def test_complete_reachability_repo_validation_rejects_checkout_missing_gradlew(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = os.path.join(temp_dir, "graalvm-reachability-metadata")
            os.makedirs(repo_root)
            _git(["init", "-b", "master"], cwd=repo_root)
            for directory in ("forge", "metadata", "tests", os.path.join("gradle", "wrapper")):
                os.makedirs(os.path.join(repo_root, directory), exist_ok=True)
            _commit_file(repo_root, "settings.gradle", "rootProject.name = 'test'\n", "add settings")
            _commit_file(repo_root, "build.gradle", "plugins { id 'java' }\n", "add build")
            _commit_file(repo_root, "gradle/wrapper/gradle-wrapper.jar", "wrapper jar\n", "add wrapper jar")
            _commit_file(
                repo_root,
                "gradle/wrapper/gradle-wrapper.properties",
                "distributionUrl=https\\://services.gradle.org/distributions/gradle-bin.zip\n",
                "add wrapper properties",
            )
            _commit_file(repo_root, "forge/.gitkeep", "", "add forge")
            _commit_file(repo_root, "metadata/.gitkeep", "", "add metadata")
            _commit_file(repo_root, "tests/.gitkeep", "", "add tests")

            with self.assertRaises(SystemExit):
                require_complete_reachability_repo(repo_root)

    def test_complete_reachability_repo_validation_rejects_missing_gradle_build_logic(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = os.path.join(temp_dir, "graalvm-reachability-metadata")
            os.makedirs(repo_root)
            _git(["init", "-b", "master"], cwd=repo_root)
            for directory in ("forge", "metadata", "tests"):
                os.makedirs(os.path.join(repo_root, directory), exist_ok=True)
            _commit_file(repo_root, "gradlew", "#!/usr/bin/env sh\n", "add gradle wrapper")
            _commit_file(repo_root, "settings.gradle", "rootProject.name = 'test'\n", "add settings")
            _commit_file(repo_root, "forge/.gitkeep", "", "add forge")
            _commit_file(repo_root, "metadata/.gitkeep", "", "add metadata")
            _commit_file(repo_root, "tests/.gitkeep", "", "add tests")

            with self.assertRaises(SystemExit):
                require_complete_reachability_repo(repo_root)

    def test_defaults_to_parent_repo_and_in_repo_forge_metrics(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            reachability_repo = _create_reachability_repo(os.path.join(temp_dir, "graalvm-reachability-metadata"))
            forge_root = os.path.join(reachability_repo, "forge")

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
            worktree_path = _create_reachability_repo(os.path.join(temp_dir, "run-worktree"))
            forge_root = os.path.join(worktree_path, "forge")

            reachability_root, metrics_root = resolve_repo_roots(
                worktree_path,
                None,
            )

            self.assertEqual(reachability_root, worktree_path)
            self.assertEqual(metrics_root, forge_root)
            self.assertTrue(os.path.isdir(os.path.join(forge_root, "script_run_metrics")))
            self.assertTrue(os.path.isdir(os.path.join(forge_root, "benchmark_run_metrics")))

    def test_explicit_reachability_path_rejects_partial_tree(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            partial_dir = os.path.join(temp_dir, "metadata", "org.example", "demo")
            os.makedirs(partial_dir)

            with self.assertRaises(SystemExit):
                resolve_repo_roots(partial_dir, None)

    def test_create_issue_workspace_validates_created_worktree(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            reachability_repo = _create_reachability_repo(os.path.join(temp_dir, "graalvm-reachability-metadata"))
            metrics_root = os.path.join(reachability_repo, "forge")

            with patch.object(forge_metadata, "get_repo_root", return_value=metrics_root), \
                    patch.object(forge_metadata, "require_complete_reachability_repo") as validate:
                worktree_path, scratch_metrics_path = forge_metadata.create_issue_workspace(
                    reachability_repo,
                    metrics_root,
                    issue_number=1412,
                )

            self.assertEqual(scratch_metrics_path, os.path.join(worktree_path, "forge"))
            validate.assert_called_once_with(worktree_path)
            _git(["worktree", "remove", "--force", worktree_path], cwd=reachability_repo)

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
            state_relative_path = os.path.join(
                "large_library_series",
                "issue-1412",
                "com.example-demo-1.0.0-1412",
                "state.json",
            )
            state_absolute_path = os.path.join(worktree_path, state_relative_path)
            os.makedirs(os.path.dirname(state_absolute_path), exist_ok=True)
            with open(state_absolute_path, "w", encoding="utf-8") as file_handle:
                json.dump({"issueNumber": 1412}, file_handle)

            commit_run_metrics_with_retry(
                metrics_repo_root=worktree_path,
                metrics_json_relative_path="script_run_metrics/add_new_library_support.json",
                run_metrics=run_metrics,
                commit_message="Persist metrics from scratch worktree",
                extra_paths_to_stage=[os.path.dirname(state_relative_path)],
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
            self.assertTrue(os.path.isfile(os.path.join(canonical_repo, state_relative_path)))
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
