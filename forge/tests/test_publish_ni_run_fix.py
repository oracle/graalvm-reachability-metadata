# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from git_scripts import publish_ni_run_fix


class SevereMetadataDropGuardrailTests(unittest.TestCase):
    def test_is_severe_metadata_drop_requires_existing_baseline_and_below_threshold(self) -> None:
        self.assertTrue(publish_ni_run_fix.is_severe_metadata_drop(34, 0))
        self.assertTrue(publish_ni_run_fix.is_severe_metadata_drop(100, 24))
        self.assertFalse(publish_ni_run_fix.is_severe_metadata_drop(100, 25))
        self.assertFalse(publish_ni_run_fix.is_severe_metadata_drop(100, 26))
        self.assertFalse(publish_ni_run_fix.is_severe_metadata_drop(0, 0))

class NativeImageRunFinalizationTests(unittest.TestCase):
    def test_build_test_comparison_section_returns_bounded_markdown_section(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path:
            new_test_dir = os.path.join(
                repo_path,
                "tests",
                "src",
                "org.example",
                "demo",
                "2.0.0",
            )
            os.makedirs(new_test_dir)
            bounded_section = (
                "**Test-source comparison**\n\n```text\n1 file changed\n```\n\n"
                "```diff\n+change\n```"
            )

            with patch.object(
                    publish_ni_run_fix,
                    "format_bounded_test_diff_section",
                    return_value=bounded_section,
            ):
                comparison_section = publish_ni_run_fix.build_test_comparison_section(
                    "org.example", "demo", "1.0.0", "2.0.0", repo_path,
                )

        self.assertEqual(comparison_section, bounded_section)

    def test_stage_and_commit_includes_jvm_test_source_directories(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path:
            test_version_dir = os.path.join(
                repo_path,
                "tests",
                "src",
                "org.example",
                "demo",
                "1.0.0",
            )
            for test_source_dir_name in ("java", "kotlin", "groovy", "scala"):
                os.makedirs(os.path.join(test_version_dir, "src", "test", test_source_dir_name))

            with patch.object(publish_ni_run_fix, "stage_and_commit_common") as stage_and_commit, \
                    patch.object(
                        publish_ni_run_fix,
                        "stats_artifact_dir",
                        return_value=os.path.join(repo_path, "stats", "org.example", "demo"),
                    ):
                publish_ni_run_fix.stage_and_commit(
                    group="org.example",
                    artifact="demo",
                    test_version="1.0.0",
                    metadata_version="2.0.0",
                    coordinates="org.example:demo:2.0.0",
                    repo_path=repo_path,
                )

        staged_paths = stage_and_commit.call_args.args[0]
        for test_source_dir_name in ("java", "kotlin", "groovy", "scala"):
            self.assertIn(
                os.path.join(
                    "tests",
                    "src",
                    "org.example",
                    "demo",
                    "1.0.0",
                    "src",
                    "test",
                    test_source_dir_name,
                ),
                staged_paths,
            )

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

            with patch.object(publish_ni_run_fix, "stage_and_commit_common") as stage_and_commit, \
                    patch.object(
                        publish_ni_run_fix,
                        "stats_artifact_dir",
                        return_value=os.path.join(repo_path, "stats", "org.example", "demo"),
                    ):
                publish_ni_run_fix.stage_and_commit(
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
                publish_ni_run_fix.assert_no_tracked_worktree_changes(repo_path)


if __name__ == "__main__":
    unittest.main()
