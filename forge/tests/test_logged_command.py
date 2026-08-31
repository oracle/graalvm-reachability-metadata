# Copyright and related rights waived via CC0

import contextlib
import io
import os
import sys
import tempfile
import unittest
from unittest.mock import patch

from utility_scripts.logged_command import run_logged_command


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


if __name__ == "__main__":
    unittest.main()
