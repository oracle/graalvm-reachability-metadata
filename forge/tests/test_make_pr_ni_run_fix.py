# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from git_scripts import make_pr_ni_run_fix


class SevereMetadataDropGuardrailTests(unittest.TestCase):
    def test_is_severe_metadata_drop_requires_existing_baseline_and_below_threshold(self) -> None:
        self.assertTrue(make_pr_ni_run_fix.is_severe_metadata_drop(34, 0))
        self.assertTrue(make_pr_ni_run_fix.is_severe_metadata_drop(100, 24))
        self.assertFalse(make_pr_ni_run_fix.is_severe_metadata_drop(100, 25))
        self.assertFalse(make_pr_ni_run_fix.is_severe_metadata_drop(100, 26))
        self.assertFalse(make_pr_ni_run_fix.is_severe_metadata_drop(0, 0))

    def test_create_pull_request_routes_severe_metadata_drop_to_human_intervention(self) -> None:
        gh_calls: list[tuple[str, ...]] = []

        def fake_gh(*args: str, check: bool = True):
            del check
            gh_calls.append(args)
            return subprocess.CompletedProcess(["gh", *args], 1 if args[:2] == ("pr", "view") else 0)

        with patch.object(make_pr_ni_run_fix, "get_origin_owner", return_value="me"), \
                patch.object(make_pr_ni_run_fix, "gh", side_effect=fake_gh), \
                patch.object(make_pr_ni_run_fix, "find_issue_common", return_value=5181), \
                patch.object(make_pr_ni_run_fix, "count_metadata_entries", side_effect=[0, 34]), \
                patch.object(make_pr_ni_run_fix, "count_test_only_metadata_entries", return_value=0), \
                patch.object(make_pr_ni_run_fix, "collect_version_coverage_metrics", return_value=(0.0, None)), \
                patch.object(make_pr_ni_run_fix, "format_stats_diff", return_value=""), \
                patch.object(make_pr_ni_run_fix, "format_forge_revision_section", return_value="Forge revision\n"):
            make_pr_ni_run_fix.create_pull_request(
                branch="ai/test/fix-native-image-run-org.example-demo-2.0.0",
                old_coordinates="org.example:demo:1.0.0",
                new_coordinates="org.example:demo:2.0.0",
                group="org.example",
                artifact="demo",
                repo_path="/repo",
            )

        create_call = gh_calls[1]
        body = create_call[create_call.index("--body") + 1]
        labels = [
            create_call[index + 1]
            for index, value in enumerate(create_call[:-1])
            if value == "--label"
        ]

        self.assertIn("Severe Metadata Drop", body)
        self.assertIn("Previous metadata entries (`org.example:demo:1.0.0`): 34", body)
        self.assertIn("New metadata entries (`org.example:demo:2.0.0`): 0", body)
        self.assertIn("Retained metadata entries: 0.00%", body)
        self.assertIn("fixes-native-image-run-fail", labels)
        self.assertIn("human-intervention", labels)


class NativeImageRunFinalizationTests(unittest.TestCase):
    def test_stage_and_commit_includes_test_native_image_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path:
            native_image_metadata_dir = os.path.join(
                repo_path,
                "tests",
                "src",
                "org.example",
                "demo",
                "1.0.0",
                "src",
                "test",
                "resources",
                "META-INF",
                "native-image",
            )
            os.makedirs(native_image_metadata_dir)

            with patch.object(make_pr_ni_run_fix, "stage_and_commit_common") as stage_and_commit, \
                    patch.object(
                        make_pr_ni_run_fix,
                        "stats_artifact_dir",
                        return_value=os.path.join(repo_path, "stats", "org.example", "demo"),
                    ):
                make_pr_ni_run_fix.stage_and_commit(
                    group="org.example",
                    artifact="demo",
                    test_version="1.0.0",
                    metadata_version="2.0.0",
                    coordinates="org.example:demo:2.0.0",
                    repo_path=repo_path,
                )

        staged_paths = stage_and_commit.call_args.args[0]
        self.assertIn(
            os.path.join(
                "tests",
                "src",
                "org.example",
                "demo",
                "1.0.0",
                "src",
                "test",
                "resources",
                "META-INF",
                "native-image",
            ),
            staged_paths,
        )

    def test_tracked_worktree_guard_reports_remaining_paths(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path:
            subprocess.run(["git", "init"], cwd=repo_path, check=True, stdout=subprocess.DEVNULL)
            subprocess.run(["git", "config", "user.email", "forge@example.com"], cwd=repo_path, check=True)
            subprocess.run(["git", "config", "user.name", "Forge Test"], cwd=repo_path, check=True)

            tracked_file = os.path.join(repo_path, "tracked.txt")
            with open(tracked_file, "w", encoding="utf-8") as file:
                file.write("before\n")
            subprocess.run(["git", "add", "tracked.txt"], cwd=repo_path, check=True)
            subprocess.run(["git", "commit", "-m", "baseline"], cwd=repo_path, check=True, stdout=subprocess.DEVNULL)

            with open(tracked_file, "w", encoding="utf-8") as file:
                file.write("after\n")

            with self.assertRaisesRegex(RuntimeError, "tracked.txt"):
                make_pr_ni_run_fix.assert_no_tracked_worktree_changes(repo_path)


if __name__ == "__main__":
    unittest.main()
