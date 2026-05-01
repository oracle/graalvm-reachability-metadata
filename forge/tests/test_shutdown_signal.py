# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os
import tempfile
import unittest
from unittest.mock import patch

from utility_scripts import shutdown_signal


class ShutdownSignalTests(unittest.TestCase):
    def test_shutdown_signal_defaults_to_home_marker(self) -> None:
        with tempfile.TemporaryDirectory() as home_dir:
            with patch.dict(os.environ, {"HOME": home_dir}, clear=True):
                self.assertEqual(
                    shutdown_signal.get_shutdown_signal_path(),
                    os.path.join(home_dir, ".metadata-forge-stop"),
                )

    def test_shutdown_signal_can_be_requested_and_cleared(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            signal_path = os.path.join(temp_dir, "stop")
            with patch.dict(os.environ, {"FORGE_DO_WORK_STOP_FILE": signal_path}, clear=True):
                self.assertFalse(shutdown_signal.is_shutdown_requested())
                self.assertEqual(shutdown_signal.request_shutdown(), signal_path)
                self.assertTrue(shutdown_signal.is_shutdown_requested())
                self.assertEqual(shutdown_signal.clear_shutdown_request(), signal_path)
                self.assertFalse(shutdown_signal.is_shutdown_requested())

    def test_branch_shutdown_signal_applies_only_to_monitored_branch(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            signal_path = os.path.join(temp_dir, "stop")
            with patch.dict(os.environ, {"FORGE_DO_WORK_STOP_FILE": signal_path}, clear=True):
                branch_signal_path = shutdown_signal.request_shutdown("origin/feature/test")

                self.assertEqual(branch_signal_path, f"{signal_path}.feature_test")
                self.assertFalse(shutdown_signal.is_shutdown_requested())

            with patch.dict(
                    os.environ,
                    {
                        "FORGE_DO_WORK_STOP_FILE": signal_path,
                        "FORGE_MONITORED_BRANCH": "origin/feature/test",
                    },
                    clear=True,
            ):
                self.assertTrue(shutdown_signal.is_shutdown_requested())
                self.assertEqual(shutdown_signal.get_active_shutdown_signal_path(), branch_signal_path)

            with patch.dict(os.environ, {"FORGE_DO_WORK_STOP_FILE": signal_path}, clear=True):
                shutdown_signal.clear_shutdown_request("feature/test")
