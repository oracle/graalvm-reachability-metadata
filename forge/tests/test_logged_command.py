# Copyright and related rights waived via CC0

import contextlib
import io
import os
import sys
import tempfile
import unittest
from unittest.mock import patch

from utility_scripts.logged_command import LoggedCommandResult, run_logged_command
from utility_scripts.run_location import PHASE_SETUP
from utility_scripts.run_location import enter_phase, reset_run_location


class LoggedCommandTests(unittest.TestCase):
    """Concise command output backed by complete durable logs.

    §FS-forge-run-output-legibility.4 §FS-durable-generation-logs
    """

    def tearDown(self) -> None:
        reset_run_location()

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

    def test_compact_setup_hides_success_but_keeps_failure_and_log(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            log_path = os.path.join(temp_dir, "command.log")
            terminal = io.StringIO()
            enter_phase(PHASE_SETUP)
            with patch(
                    "utility_scripts.logged_command.build_timestamped_task_log_path",
                    return_value=log_path,
            ), contextlib.redirect_stdout(terminal):
                result = run_logged_command(
                    [sys.executable, "-c", "raise SystemExit(7)"],
                    cwd=temp_dir,
                    task_type="setup-test",
                    subject="org.example:demo:1.0.0",
                    action="failingSetup",
                )

        self.assertEqual(result.returncode, 7)
        self.assertNotIn("Running failingSetup", terminal.getvalue())
        self.assertIn("failingSetup failed with exit code 7", terminal.getvalue())
        self.assertIn("command.log", terminal.getvalue())

    def test_compact_setup_hides_a_failure_the_caller_handles(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            log_path = os.path.join(temp_dir, "command.log")
            terminal = io.StringIO()
            enter_phase(PHASE_SETUP)
            with patch(
                    "utility_scripts.logged_command.build_timestamped_task_log_path",
                    return_value=log_path,
            ), contextlib.redirect_stdout(terminal):
                result = run_logged_command(
                    [sys.executable, "-c", "raise SystemExit(1)"],
                    cwd=temp_dir,
                    task_type="setup-test",
                    subject="org.example:demo:1.0.0",
                    action="handledSetup",
                    failure_is_detail=True,
                )

        self.assertEqual(result.returncode, 1)
        self.assertEqual("", terminal.getvalue())

if __name__ == "__main__":
    unittest.main()
