# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from git_scripts import branch_publication
from utility_scripts.metrics_writer import PENDING_METRICS_FILENAME


def _git(repo_path: str, *args: str) -> str:
    result = subprocess.run(
        ["git", *args],
        cwd=repo_path,
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    return result.stdout


class BranchPublicationTests(unittest.TestCase):
    def test_format_bounded_test_diff_section_truncates_large_diff(self) -> None:
        diff_text = "Q" * (branch_publication.MAX_INLINE_TEST_DIFF_CHARS + 1)
        with patch.object(
                branch_publication,
                "_generate_test_diff_outputs",
                return_value=(diff_text, " 1 file changed, 1 insertion(+)")
        ):
            section = branch_publication.format_bounded_test_diff_section(
                "org.example", "demo", "1.0.0", "2.0.0", "/repo",
            )

        self.assertIn("1 file changed", section)
        self.assertIn("Files changed", section)
        self.assertNotIn(diff_text, section)
        self.assertEqual(section.count("Q"), branch_publication.MAX_INLINE_TEST_DIFF_CHARS)

    def test_bound_pr_body_keeps_body_below_github_limit(self) -> None:
        oversized_body = (
            "x" * (branch_publication.MAX_PR_BODY_CHARS + 100)
            + "\n\n## Local CI Verification\n\nPassed"
        )

        bounded_body = branch_publication.bound_pr_body(oversized_body)

        self.assertLessEqual(len(bounded_body), branch_publication.MAX_PR_BODY_CHARS)
        self.assertIn("GitHub's size limit", bounded_body)
        self.assertIn("Local CI Verification", bounded_body)

if __name__ == "__main__":
    unittest.main()
