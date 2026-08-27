# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import subprocess
import unittest
from unittest.mock import patch

from utility_scripts.style_checks import run_style_fix_and_checks


class StyleChecksTests(unittest.TestCase):
    def test_checkstyle_failure_passes_after_first_analysis_repair(self) -> None:
        failed = subprocess.CompletedProcess([], 1, stdout="checkstyle failure")
        passed = subprocess.CompletedProcess([], 0, stdout="BUILD SUCCESSFUL")

        with patch("utility_scripts.style_checks._run_gradle_task", return_value=True), \
                patch(
                    "utility_scripts.style_checks._run_checkstyle",
                    side_effect=[failed, passed],
                ) as checkstyle, patch(
                    "utility_scripts.style_checks._run_analysis_checkstyle_fix",
                    return_value=True,
                ) as analysis_fix, patch(
                    "utility_scripts.style_checks._run_test",
                    return_value=passed,
                ) as coordinate_test:
            result = run_style_fix_and_checks("/repo", "g:a:1.0")

        self.assertTrue(result)
        analysis_fix.assert_called_once()
        self.assertEqual(checkstyle.call_count, 2)
        coordinate_test.assert_called_once()

    def test_failed_checkstyle_reruns_use_all_three_repairs(self) -> None:
        failed = subprocess.CompletedProcess([], 1, stdout="still failing")

        with patch("utility_scripts.style_checks._run_gradle_task", return_value=True), \
                patch(
                    "utility_scripts.style_checks._run_checkstyle",
                    side_effect=[failed, failed, failed, failed],
                ) as checkstyle, patch(
                    "utility_scripts.style_checks._run_analysis_checkstyle_fix",
                    return_value=True,
                ) as analysis_fix, patch("utility_scripts.style_checks._run_test") as coordinate_test:
            result = run_style_fix_and_checks("/repo", "g:a:1.0")

        self.assertFalse(result)
        self.assertEqual(analysis_fix.call_count, 3)
        self.assertEqual(checkstyle.call_count, 4)
        coordinate_test.assert_not_called()

    def test_test_failure_after_checkstyle_gets_analysis_recovery(self) -> None:
        checkstyle_failed = subprocess.CompletedProcess([], 1, stdout="checkstyle failure")
        test_failed = subprocess.CompletedProcess([], 1, stdout="test failure")
        passed = subprocess.CompletedProcess([], 0, stdout="BUILD SUCCESSFUL")

        with patch("utility_scripts.style_checks._run_gradle_task", return_value=True), \
                patch(
                    "utility_scripts.style_checks._run_checkstyle",
                    side_effect=[checkstyle_failed, passed, passed],
                ) as checkstyle, patch(
                    "utility_scripts.style_checks._run_analysis_checkstyle_fix",
                    return_value=True,
                ) as checkstyle_fix, patch(
                    "utility_scripts.style_checks._run_test",
                    side_effect=[test_failed, passed],
                ) as coordinate_test, patch(
                    "utility_scripts.style_checks._run_analysis_test_fix_after_checkstyle",
                    return_value=True,
                ) as test_fix:
            result = run_style_fix_and_checks("/repo", "g:a:1.0")

        self.assertTrue(result)
        checkstyle_fix.assert_called_once()
        test_fix.assert_called_once()
        self.assertEqual(coordinate_test.call_count, 2)
        self.assertEqual(checkstyle.call_count, 3)


if __name__ == "__main__":
    unittest.main()
