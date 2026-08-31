# Copyright and related rights waived via CC0

import contextlib
import io
import os
import sys
import tempfile
import unittest
from unittest.mock import patch

from ai_workflows.drivers import add_new_library_support, fix_ni_run, java_fail_workflow
from utility_scripts.logged_command import LoggedCommandResult, run_logged_command


class LoggedCommandTests(unittest.TestCase):
    """Concise command output backed by complete durable logs.

    §FS-forge-run-output-legibility.4 §FS-durable-generation-logs
    """

    def test_success_keeps_command_output_out_of_terminal_and_in_log(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            log_path = os.path.join(temp_dir, "command.log")
            terminal = io.StringIO()
            with patch(
                    "utility_scripts.logged_command.build_timestamped_task_log_path",
                    return_value=log_path,
            ), contextlib.redirect_stdout(terminal):
                result = run_logged_command(
                    [sys.executable, "-c", "print('private command output')"],
                    cwd=temp_dir,
                    task_type="test",
                    subject="org.example:demo:1.0.0",
                    action="exampleTask",
                )

            with open(log_path, "r", encoding="utf-8") as log_file:
                durable_output = log_file.read()

        self.assertEqual(result.returncode, 0)
        self.assertNotIn("private command output", terminal.getvalue())
        self.assertIn("Running exampleTask", terminal.getvalue())
        self.assertIn("exampleTask completed", terminal.getvalue())
        self.assertIn("private command output", durable_output)

    def test_debug_mode_tees_raw_output_while_preserving_log(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            log_path = os.path.join(temp_dir, "command.log")
            terminal = io.StringIO()
            with patch(
                    "utility_scripts.logged_command.build_timestamped_task_log_path",
                    return_value=log_path,
            ), patch.dict(
                    os.environ,
                    {"FORGE_DEBUG_LOGGING": "1"},
            ), contextlib.redirect_stdout(terminal):
                result = run_logged_command(
                    [sys.executable, "-c", "print('live debug output')"],
                    cwd=temp_dir,
                    task_type="test",
                    subject="org.example:demo:1.0.0",
                    action="exampleTask",
                )

            with open(log_path, "r", encoding="utf-8") as log_file:
                durable_output = log_file.read()

        self.assertEqual(result.returncode, 0)
        self.assertIn("live debug output", terminal.getvalue())
        self.assertIn("live debug output", durable_output)

    def test_remaining_driver_gradle_commands_use_durable_logging(self) -> None:
        successful_result = LoggedCommandResult(
            args=["./gradlew"],
            returncode=0,
            stdout="",
            log_path="/tmp/gradle.log",
            timed_out=False,
            duration_seconds=0.0,
        )
        with patch.object(
                add_new_library_support,
                "require_complete_reachability_repo",
        ), patch.object(
                add_new_library_support,
                "gradle_command_environment",
                return_value={},
        ), patch.object(
                add_new_library_support,
                "run_logged_command",
                return_value=successful_result,
        ) as new_library_command, patch.object(
                add_new_library_support.os,
                "getcwd",
                return_value="/repo",
        ):
            self.assertTrue(add_new_library_support.run_scaffold("g:a:1.0"))

        self.assertEqual(new_library_command.call_args.kwargs["task_type"], "scaffold")
        self.assertEqual(new_library_command.call_args.kwargs["subject"], "g:a:1.0")

        with patch.object(
                fix_ni_run,
                "require_complete_reachability_repo",
        ), patch.object(
                fix_ni_run,
                "gradle_command_environment",
                return_value={},
        ), patch.object(
                fix_ni_run,
                "run_logged_command",
                return_value=successful_result,
        ) as native_fix_command:
            fix_ni_run.run_fix_test_native_image_run("/repo", "g:a:1.0", "2.0")

        self.assertEqual(native_fix_command.call_args.kwargs["task_type"], "native-image-run-fix")
        self.assertEqual(native_fix_command.call_args.kwargs["subject"], "g:a:2.0")

        with patch.object(
                java_fail_workflow,
                "require_complete_reachability_repo",
        ), patch.object(
                java_fail_workflow,
                "gradle_command_environment",
                return_value={},
        ), patch.object(
                java_fail_workflow,
                "run_logged_command",
                return_value=successful_result,
        ) as java_fail_command, patch.object(
                java_fail_workflow.os,
                "getcwd",
                return_value="/repo",
        ):
            java_fail_workflow.run_gradle_task("addLibraryMetadataIndexJson", "g:a:2.0")

        self.assertEqual(java_fail_command.call_args.kwargs["task_type"], "java-fail-setup")
        self.assertEqual(java_fail_command.call_args.kwargs["subject"], "g:a:2.0")


if __name__ == "__main__":
    unittest.main()
