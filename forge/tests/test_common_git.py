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


class RemoteBranchDeletionTests(unittest.TestCase):
    def test_delete_remote_branch_if_exists_skips_missing_remote_branch(self) -> None:
        with patch.object(common_git, "git_remote_branch_exists", return_value=False), \
                patch.object(common_git.subprocess, "run") as run:
            deleted = common_git.delete_remote_branch_if_exists("ai/user/fix-lib")

        self.assertFalse(deleted)
        run.assert_not_called()

    def test_delete_remote_branch_if_exists_deletes_existing_remote_branch(self) -> None:
        with patch.object(common_git, "git_remote_branch_exists", return_value=True), \
                patch.object(common_git.subprocess, "run") as run, \
                patch("sys.stdout", new_callable=io.StringIO):
            deleted = common_git.delete_remote_branch_if_exists("ai/user/fix-lib", cwd="/repo")

        self.assertTrue(deleted)
        run.assert_called_once_with(
            ["git", "push", "origin", "--delete", "ai/user/fix-lib"],
            cwd="/repo",
            check=True,
        )


class GitHubRateLimitTests(unittest.TestCase):
    def test_common_gh_does_not_log_github_query_by_default(self) -> None:
        completed_process = subprocess.CompletedProcess(
            ["gh"],
            0,
            stdout="{}",
            stderr="",
        )

        with patch.object(common_git.subprocess, "run", return_value=completed_process), \
                patch.dict(os.environ, {common_git.GITHUB_QUERY_LOG_ENV_VAR: ""}), \
                patch("sys.stdout", new_callable=io.StringIO) as stdout:
            common_git.gh(
                "api",
                "graphql",
                "-f",
                "query=query { viewer { login } }",
            )

        self.assertEqual("", stdout.getvalue())

    def test_common_gh_logs_github_query_when_enabled(self) -> None:
        completed_process = subprocess.CompletedProcess(
            ["gh"],
            0,
            stdout="{}",
            stderr="",
        )

        with patch.object(common_git.subprocess, "run", return_value=completed_process), \
                patch.dict(os.environ, {common_git.GITHUB_QUERY_LOG_ENV_VAR: "1"}), \
                patch("sys.stdout", new_callable=io.StringIO) as stdout:
            common_git.gh(
                "api",
                "graphql",
                "-f",
                "query=query { viewer { login } }",
            )

        self.assertIn(
            "[github-query] gh api graphql -f query=query { viewer { login } }",
            stdout.getvalue(),
        )

    def test_common_gh_log_redacts_body_argument(self) -> None:
        completed_process = subprocess.CompletedProcess(
            ["gh"],
            0,
            stdout="",
            stderr="",
        )

        with patch.object(common_git.subprocess, "run", return_value=completed_process), \
                patch.dict(os.environ, {common_git.GITHUB_QUERY_LOG_ENV_VAR: "1"}), \
                patch("sys.stdout", new_callable=io.StringIO) as stdout:
            common_git.gh(
                "issue",
                "comment",
                "1412",
                "--body",
                "long generated comment",
            )

        output = stdout.getvalue()
        self.assertIn("[github-query] gh issue comment 1412 --body <redacted>", output)
        self.assertNotIn("long generated comment", output)

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
