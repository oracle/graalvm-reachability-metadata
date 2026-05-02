# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from ai_workflows.java_fail_workflow import JAVAC_CONFIG, copy_and_prepare_project_dir, create_project_prep_checkpoint


class JavaFailWorkflowProjectPrepTests(unittest.TestCase):
    def test_copy_and_prepare_project_dir_skips_same_source_and_destination(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            test_project_dir = os.path.join(
                temp_dir,
                "tests",
                "src",
                "org.example",
                "demo",
                "1.0.0",
            )
            os.makedirs(test_project_dir)
            gradle_properties_path = os.path.join(test_project_dir, "gradle.properties")
            with open(gradle_properties_path, "w", encoding="utf-8") as file:
                file.write("version=1.0.0\n")

            previous_cwd = os.getcwd()
            try:
                os.chdir(temp_dir)
                with patch("ai_workflows.java_fail_workflow.shutil.copytree") as copytree:
                    copy_and_prepare_project_dir("org.example", "demo", "1.0.0", "1.0.0")
            finally:
                os.chdir(previous_cwd)

            copytree.assert_not_called()
            with open(gradle_properties_path, "r", encoding="utf-8") as file:
                self.assertEqual(file.read(), "version=1.0.0\n")

    def test_create_project_prep_checkpoint_allows_no_staged_changes(self) -> None:
        with patch("ai_workflows.java_fail_workflow.build_ai_branch_name", return_value="ai/test-branch"), \
                patch("ai_workflows.java_fail_workflow.delete_remote_branch_if_exists") as delete_remote, \
                patch("ai_workflows.java_fail_workflow.subprocess.run") as run, \
                patch(
                    "ai_workflows.java_fail_workflow.subprocess.check_output",
                    return_value="abc123\n",
                ) as check_output:
            run.side_effect = [
                subprocess.CompletedProcess(["git", "switch"], 0),
                subprocess.CompletedProcess(["git", "add"], 0),
                subprocess.CompletedProcess(["git", "diff"], 0),
            ]

            checkpoint = create_project_prep_checkpoint(JAVAC_CONFIG, "org.example", "demo", "1.0.0")

        self.assertEqual(checkpoint, "abc123")
        delete_remote.assert_called_once_with("ai/test-branch")
        self.assertEqual(run.call_count, 3)
        self.assertEqual(run.call_args_list[2].args[0], ["git", "diff", "--cached", "--quiet"])
        check_output.assert_called_once_with(["git", "rev-parse", "HEAD"], text=True)
