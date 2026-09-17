# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

from dispatcher_test_support import *  # noqa: F401,F403 - shared dispatcher test fixtures


class ProjectItemStatusTests(unittest.TestCase):
    def test_common_helper_fetches_project_item_and_status_with_one_graphql_call(self) -> None:
        with patch.object(
                common_git,
                "gh_json",
                return_value=_project_item_status_response(config.STATUS_TODO),
        ) as gh_json:
            self.assertEqual(
                common_git.get_issue_project_item_status(
                    config.REPO,
                    forge_metadata.PROJECT_NUMBER,
                    1412,
                    config.STATUS_FIELD_NAME,
                ),
                ("project-item", config.STATUS_TODO),
            )

        gh_json.assert_called_once()

    def test_forge_project_item_state_is_quiet_in_compact_output(self) -> None:
        with patch.object(
                project_board,
                "get_issue_project_item_status",
                return_value=("project-item", config.STATUS_TODO),
        ) as get_issue_project_item_status, \
                patch.dict(os.environ, {"FORGE_VERBOSE": "0", "FORGE_DEBUG_LOGGING": "0"}), \
                patch("sys.stdout", new_callable=io.StringIO) as stdout:
            self.assertEqual(
                project_board.get_project_item_state(1412),
                ("project-item", config.STATUS_TODO),
            )

        get_issue_project_item_status.assert_called_once_with(
            config.REPO,
            forge_metadata.PROJECT_NUMBER,
            1412,
            config.STATUS_FIELD_NAME,
        )
        self.assertEqual("", stdout.getvalue())

    def test_forge_project_item_state_is_available_in_verbose_output(self) -> None:
        with patch.object(
                project_board,
                "get_issue_project_item_status",
                return_value=("project-item", config.STATUS_TODO),
        ), patch.dict(os.environ, {"FORGE_VERBOSE": "1"}), \
                patch("sys.stdout", new_callable=io.StringIO) as stdout:
            project_board.get_project_item_state(1412)

        self.assertIn(
            (
                "[project-item] Issue #1412 is linked to GitHub project item project-item "
                f"in project {forge_metadata.PROJECT_NUMBER} with Status '{config.STATUS_TODO}'"
            ),
            stdout.getvalue(),
        )


class IssueClaimPreflightTests(unittest.TestCase):

    def test_set_item_status_retries_transient_http_502(self) -> None:
        failed_process = subprocess.CompletedProcess(
            ["gh"],
            1,
            stdout="",
            stderr="non-200 OK status code: 502 Bad Gateway",
        )
        successful_process = subprocess.CompletedProcess(
            ["gh"],
            0,
            stdout="",
            stderr="",
        )

        with patch.object(
                project_board,
                "get_cached_field_info",
                return_value=("project-id", "field-id", {config.STATUS_IN_PROGRESS: "option-id"}),
        ), \
                patch.object(
                    subprocess,
                    "run",
                    side_effect=[failed_process, successful_process],
                ) as run, \
                patch.object(github_cli.time, "sleep") as sleep, \
                patch("sys.stderr", new_callable=io.StringIO) as stderr:
            project_board.set_item_status("item-id", config.STATUS_IN_PROGRESS)

        self.assertEqual(run.call_count, 2)
        sleep.assert_called_once_with(common_git.GITHUB_TRANSIENT_RETRY_BASE_DELAY_SECONDS)
        self.assertIn("GitHub API transient failure", stderr.getvalue())
