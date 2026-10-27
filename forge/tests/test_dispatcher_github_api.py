# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

from dispatcher_test_support import *  # noqa: F401,F403 - shared dispatcher test fixtures


class IssueClaimPreflightTests(unittest.TestCase):

    def test_forge_gh_does_not_log_github_query_by_default(self) -> None:
        completed_process = subprocess.CompletedProcess(
            ["gh"],
            0,
            stdout="{}",
            stderr="",
        )

        with patch.object(subprocess, "run", return_value=completed_process), \
                patch.dict(os.environ, {common_git.GITHUB_QUERY_LOG_ENV_VAR: ""}), \
                patch("sys.stdout", new_callable=io.StringIO) as stdout:
            github_api.gh(
                "api",
                "--method",
                "GET",
                "/search/issues",
                "-f",
                "q=repo:oracle/graalvm-reachability-metadata is:issue",
            )

        self.assertEqual("", stdout.getvalue())

    def test_forge_gh_logs_github_query_when_enabled(self) -> None:
        completed_process = subprocess.CompletedProcess(
            ["gh"],
            0,
            stdout="{}",
            stderr="",
        )

        with patch.object(subprocess, "run", return_value=completed_process), \
                patch.dict(os.environ, {common_git.GITHUB_QUERY_LOG_ENV_VAR: "1"}), \
                patch("sys.stdout", new_callable=io.StringIO) as stdout:
            github_api.gh(
                "api",
                "--method",
                "GET",
                "/search/issues",
                "-f",
                "q=repo:oracle/graalvm-reachability-metadata is:issue",
            )

        self.assertIn(
            (
                "[github-query] gh api --method GET /search/issues -f "
                "q=repo:oracle/graalvm-reachability-metadata is:issue"
            ),
            stdout.getvalue(),
        )

    def test_gh_raises_typed_rate_limit_error_from_stderr(self) -> None:
        completed_process = subprocess.CompletedProcess(
            ["gh"],
            1,
            stdout="",
            stderr="GraphQL: API rate limit already exceeded for user ID 352820.",
        )

        with patch.object(subprocess, "run", return_value=completed_process):
            with self.assertRaises(forge_metadata.GitHubRateLimitExceeded):
                github_api.gh("issue", "view", "2099")

    def test_gh_retries_direct_transient_failure(self) -> None:
        failed_process = subprocess.CompletedProcess(
            ["gh"],
            1,
            stdout="",
            stderr="gh: HTTP 503",
        )
        successful_process = subprocess.CompletedProcess(
            ["gh"],
            0,
            stdout="",
            stderr="",
        )

        with patch.object(
                subprocess,
                "run",
                side_effect=[failed_process, successful_process],
        ) as run, \
                patch.object(time, "sleep") as sleep, \
                patch("sys.stderr", new_callable=io.StringIO) as stderr:
            github_api.gh("issue", "edit", "2099", "--add-label", "human-intervention")

        self.assertEqual(run.call_count, 2)
        sleep.assert_called_once_with(common_git.GITHUB_TRANSIENT_RETRY_BASE_DELAY_SECONDS)
        self.assertIn("GitHub API transient failure", stderr.getvalue())

    def test_gh_retries_direct_transient_failure_with_check_false(self) -> None:
        failed_process = subprocess.CompletedProcess(
            ["gh"],
            1,
            stdout="",
            stderr="gh: HTTP 504",
        )
        successful_process = subprocess.CompletedProcess(
            ["gh"],
            0,
            stdout="{}",
            stderr="",
        )

        with patch.object(
                subprocess,
                "run",
                side_effect=[failed_process, successful_process],
        ) as run, \
                patch.object(time, "sleep") as sleep, \
                patch("sys.stderr", new_callable=io.StringIO):
            result = github_api.gh("api", "/repos/example/repo/labels/demo", check=False)

        self.assertEqual(result.returncode, 0)
        self.assertEqual(run.call_count, 2)
        sleep.assert_called_once_with(common_git.GITHUB_TRANSIENT_RETRY_BASE_DELAY_SECONDS)

    def test_gh_json_raises_typed_rate_limit_error_from_graphql_payload(self) -> None:
        completed_process = subprocess.CompletedProcess(
            ["gh"],
            0,
            stdout='{"errors":[{"type":"RATE_LIMITED","message":"API rate limit exceeded"}]}',
            stderr="",
        )

        with patch.object(github_api, "gh", return_value=completed_process):
            with self.assertRaises(forge_metadata.GitHubRateLimitExceeded):
                github_api.gh_json("api", "graphql")

    def test_gh_json_retries_transient_http_504(self) -> None:
        failed_process = subprocess.CompletedProcess(
            ["gh"],
            1,
            stdout="",
            stderr="gh: HTTP 504",
        )
        successful_process = subprocess.CompletedProcess(
            ["gh"],
            0,
            stdout='{"data":{"ok":true}}',
            stderr="",
        )

        with patch.object(
                subprocess,
                "run",
                side_effect=[failed_process, successful_process],
        ) as run, \
                patch.object(github_cli.time, "sleep") as sleep, \
                patch("sys.stderr", new_callable=io.StringIO) as stderr:
            self.assertEqual(
                github_api.gh_json("api", "graphql"),
                {"data": {"ok": True}},
            )

        self.assertEqual(run.call_count, 2)
        sleep.assert_called_once_with(common_git.GITHUB_TRANSIENT_RETRY_BASE_DELAY_SECONDS)
        self.assertIn("GitHub API transient failure", stderr.getvalue())
        self.assertNotIn("query=", stderr.getvalue())

    def test_get_authenticated_user_retries_transient_timeout(self) -> None:
        failed_process = subprocess.CompletedProcess(
            ["gh"],
            1,
            stdout="",
            stderr='Get "https://api.github.com/user": dial tcp 140.82.121.5:443: i/o timeout',
        )
        successful_process = subprocess.CompletedProcess(
            ["gh"],
            0,
            stdout="vjovanov\n",
            stderr="",
        )

        with patch.object(
                subprocess,
                "run",
                side_effect=[failed_process, successful_process],
        ) as run, \
                patch.object(github_cli.time, "sleep") as sleep, \
                patch("sys.stderr", new_callable=io.StringIO):
            self.assertEqual(github_api.get_authenticated_user(), "vjovanov")

        self.assertEqual(run.call_count, 2)
        sleep.assert_called_once_with(common_git.GITHUB_TRANSIENT_RETRY_BASE_DELAY_SECONDS)
