# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Focused contract tests for reviewer-owned finalization. §FS-local-branch-review"""

import os
import unittest
from unittest.mock import patch

from git_scripts import review_finalization


class ReviewFinalizationTests(unittest.TestCase):
    def test_complete_finalization_disables_nested_agents(self) -> None:
        with patch.dict(os.environ, {"GRAALVM_HOME_25_0": "/jdk-25"}), patch.object(
                review_finalization,
                "_run_gradle_test",
                return_value=True,
        ) as gradle_test, patch.object(
                review_finalization,
                "resolve_metadata_version",
                return_value="1.0",
        ), patch.object(
                review_finalization,
                "run_library_finalization",
                return_value=True,
        ) as finalization:
            result = review_finalization.run_review_finalization(
                "/repo",
                "g:a:1.0",
                "base",
            )

        self.assertTrue(result)
        self.assertEqual(gradle_test.call_count, 3)
        finalization.assert_called_once_with(
            repo_path="/repo",
            library="g:a:1.0",
            group="g",
            artifact="a",
            library_version="1.0",
            base_commit="base",
            allow_agent_repairs=False,
        )

    def test_command_requires_a_second_pass_after_mutation(self) -> None:
        with patch.object(
                review_finalization,
                "publishable_tree_digest",
                side_effect=["before", "after"],
        ), patch.object(
                review_finalization,
                "run_review_finalization",
                return_value=True,
        ):
            result = review_finalization.main([
                "--repo-path", "/repo",
                "--coordinates", "g:a:1.0",
                "--base-commit", "base",
                "--receipt-path", "/receipt.json",
            ])

        self.assertEqual(result, 1)

    def test_command_accepts_a_stable_successful_pass(self) -> None:
        with patch.object(
                review_finalization,
                "publishable_tree_digest",
                return_value="stable",
        ), patch.object(
                review_finalization,
                "run_review_finalization",
                return_value=True,
        ), patch.object(
                review_finalization,
                "write_finalization_receipt",
        ) as write_receipt:
            result = review_finalization.main([
                "--repo-path", "/repo",
                "--coordinates", "g:a:1.0",
                "--base-commit", "base",
                "--receipt-path", "/receipt.json",
            ])

        self.assertEqual(result, 0)
        write_receipt.assert_called_once_with(
            receipt_path="/receipt.json",
            repo_path="/repo",
            coordinates="g:a:1.0",
            base_commit="base",
            tree_digest="stable",
        )


if __name__ == "__main__":
    unittest.main()
