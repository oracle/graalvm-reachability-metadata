# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import io
import os
import subprocess
import unittest
from unittest.mock import patch

from git_scripts import common_git


class ForgeRevisionSectionTests(unittest.TestCase):
    def test_forge_revision_section_includes_monitored_branch_from_environment(self) -> None:
        with patch.dict(os.environ, {"FORGE_MONITORED_BRANCH": "origin/test-branch"}), \
                patch.object(common_git, "get_forge_revision_info", return_value=("test-branch", "abc123")):
            section = common_git.format_forge_revision_section()

        self.assertIn("- Forge monitored branch: `origin/test-branch`", section)
        self.assertIn("- Forge branch: `test-branch`", section)
        self.assertIn("- Forge commit hash: `abc123`", section)


class GitHubRateLimitTests(unittest.TestCase):
    def test_common_gh_raises_typed_rate_limit_error_from_stderr(self) -> None:
        completed_process = subprocess.CompletedProcess(
            ["gh"],
            1,
            stdout="",
            stderr="gh: API rate limit exceeded for user ID 352820.",
        )

        with patch.object(common_git.subprocess, "run", return_value=completed_process):
            with self.assertRaises(common_git.GitHubRateLimitExceeded):
                common_git.gh("api", "graphql")

    def test_common_gh_json_raises_typed_rate_limit_error_from_graphql_payload(self) -> None:
        completed_process = subprocess.CompletedProcess(
            ["gh"],
            0,
            stdout='{"errors":[{"type":"RATE_LIMITED","message":"API rate limit exceeded"}]}',
            stderr="",
        )

        with patch.object(common_git, "gh", return_value=completed_process):
            with self.assertRaises(common_git.GitHubRateLimitExceeded):
                common_git.gh_json("api", "graphql")

    def test_common_gh_json_retries_transient_graphql_payload(self) -> None:
        transient_process = subprocess.CompletedProcess(
            ["gh"],
            0,
            stdout='{"errors":[{"type":"INTERNAL","message":"Something went wrong"}]}',
            stderr="",
        )
        successful_process = subprocess.CompletedProcess(
            ["gh"],
            0,
            stdout='{"data":{"ok":true}}',
            stderr="",
        )

        with patch.object(common_git, "gh", side_effect=[transient_process, successful_process]) as gh, \
                patch.object(common_git.time, "sleep") as sleep, \
                patch("sys.stderr", new_callable=io.StringIO):
            self.assertEqual(
                common_git.gh_json("api", "graphql"),
                {"data": {"ok": True}},
            )

        self.assertEqual(gh.call_count, 2)
        sleep.assert_called_once_with(common_git.GITHUB_TRANSIENT_RETRY_BASE_DELAY_SECONDS)


if __name__ == "__main__":
    unittest.main()
