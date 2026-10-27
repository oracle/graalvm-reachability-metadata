# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

from dispatcher_test_support import *  # noqa: F401,F403 - shared dispatcher test fixtures


class IssueClaimPreflightTests(unittest.TestCase):

    def test_prioritized_issue_fetch_drains_each_tier_before_the_next(self) -> None:
        tier_issues = {
            (config.LABEL_HIGH_PRIORITY,): [
                _search_issue(1414, [config.LABEL_HIGH_PRIORITY]),
            ],
            (config.LABEL_PRIORITY,): [
                _search_issue(1413, [config.LABEL_PRIORITY]),
            ],
            (): [_search_issue(1412)],
        }

        def fake_get_issues(
                _label: str,
                _limit: int,
                offset: int = 0,
                extra_labels: list[str] | None = None,
                _excluded_labels: list[str] | None = None,
        ) -> list[dict]:
            return [] if offset else tier_issues[tuple(extra_labels or ())]

        fetched: list[list[int]] = []
        scan_state = None
        with patch.object(
                issue_queue,
                "get_issues_with_label",
                side_effect=fake_get_issues,
        ) as get_issues:
            for _ in range(4):
                issues, scan_state = issue_queue.get_prioritized_issues_with_label(
                    forge_metadata.LABEL_LIBRARY_NEW,
                    25,
                    scan_state,
                )
                fetched.append([issue["number"] for issue in issues])

        self.assertEqual(fetched, [[1414], [1413], [1412], []])
        self.assertTrue(scan_state.exhausted)
        self.assertEqual(
            [(call.args[2], call.args[3], call.args[4]) for call in get_issues.call_args_list],
            [
                (0, [config.LABEL_HIGH_PRIORITY], []),
                (1, [config.LABEL_HIGH_PRIORITY], []),
                (0, [config.LABEL_PRIORITY], [config.LABEL_HIGH_PRIORITY]),
                (1, [config.LABEL_PRIORITY], [config.LABEL_HIGH_PRIORITY]),
                (0, [], [config.LABEL_HIGH_PRIORITY, config.LABEL_PRIORITY]),
                (1, [], [config.LABEL_HIGH_PRIORITY, config.LABEL_PRIORITY]),
            ],
        )

    def test_tier_search_query_keeps_the_not_for_native_image_exclusion(self) -> None:
        with patch.dict(os.environ, {"FORGE_ISSUE_SEARCH_CACHE": "0"}), \
                patch.object(issue_queue, "gh_json", return_value={"items": []}) as gh_json:
            issue_queue.search_issues_with_label(
                forge_metadata.LABEL_LIBRARY_NEW,
                25,
                excluded_labels=[config.LABEL_HIGH_PRIORITY],
            )

        self.assertEqual(
            gh_json.call_args.args[5],
            (
                f"q=repo:{config.REPO} is:issue is:open "
                f'label:"{forge_metadata.LABEL_LIBRARY_NEW}" '
                f'-label:"{config.LABEL_NOT_FOR_NATIVE_IMAGE}" '
                f'-label:"{config.LABEL_HIGH_PRIORITY}"'
            ),
        )

    def test_issue_scan_batch_size_returns_candidate_batch_size(self) -> None:
        self.assertEqual(
            issue_processing.get_issue_scan_batch_size(1, 1),
            config.DEFAULT_ISSUE_SCAN_BATCH_SIZE,
        )
        self.assertEqual(
            issue_processing.get_issue_scan_batch_size(5, 1),
            config.DEFAULT_ISSUE_SCAN_BATCH_SIZE,
        )
        self.assertEqual(
            issue_processing.get_issue_scan_batch_size(100, 4),
            config.DEFAULT_ISSUE_SCAN_BATCH_SIZE,
        )

    def test_offset_issue_fetch_uses_search_page_instead_of_expanding_limit(self) -> None:
        page_items = [_search_issue(number) for number in range(200, 300)]

        with patch.dict(os.environ, {"FORGE_ISSUE_SEARCH_CACHE": "0"}), \
                patch.object(
                        issue_queue,
                        "gh_json",
                        return_value={"items": page_items},
                ) as gh_json:
            issues = issue_queue.get_issues_with_label(
                forge_metadata.LABEL_LIBRARY_NEW,
                1,
                211,
            )

        self.assertEqual([issue["number"] for issue in issues], [211])
        gh_json.assert_called_once_with(
            "api", "--method", "GET", "/search/issues",
            "-f", (
                f"q=repo:{config.REPO} is:issue is:open "
                f'label:"{forge_metadata.LABEL_LIBRARY_NEW}" -label:"{config.LABEL_NOT_FOR_NATIVE_IMAGE}"'
            ),
            "-f", "sort=created",
            "-f", "order=desc",
            "-F", "per_page=100",
            "-F", "page=3",
        )

    def test_user_requested_issue_fetch_uses_regular_search_query_and_normalizes_author(self) -> None:
        page_items = [
            _search_issue(1, author="external-user"),
            _search_issue(2, author="graalvmbot"),
        ]

        with patch.dict(os.environ, {"FORGE_ISSUE_SEARCH_CACHE": "0"}), \
                patch.object(issue_queue, "gh_json", return_value={"items": page_items}) as gh_json:
            issues = issue_queue.get_issues_with_label(
                forge_metadata.LABEL_LIBRARY_NEW,
                2,
                user_requested_only=True,
            )

        self.assertEqual(
            [issue["author"] for issue in issues],
            [{"login": "external-user"}, {"login": "graalvmbot"}],
        )
        gh_json.assert_called_once_with(
            "api", "--method", "GET", "/search/issues",
            "-f", (
                f"q=repo:{config.REPO} is:issue is:open "
                f'label:"{forge_metadata.LABEL_LIBRARY_NEW}" -label:"{config.LABEL_NOT_FOR_NATIVE_IMAGE}"'
            ),
            "-f", "sort=created",
            "-f", "order=desc",
            "-F", "per_page=100",
            "-F", "page=1",
        )

    def test_user_requested_issue_filter_excludes_configured_authors_locally(self) -> None:
        issues = [
            {"number": 1, "author": {"login": "external-user"}},
            {"number": 2, "author": {"login": "graalvmbot"}},
            {"number": 3, "author": {"login": "vjovanov"}},
        ]

        filtered = issue_queue.filter_user_requested_issues(issues, user_requested_only=True)

        self.assertEqual([issue["number"] for issue in filtered], [1])

    def test_prioritized_issue_fetch_filters_authors_but_advances_raw_offset(self) -> None:
        issues = [
            {"number": 1, "author": {"login": "graalvmbot"}, "labels": []},
            {"number": 2, "author": {"login": "external-user"}, "labels": []},
        ]

        with patch.object(issue_queue, "get_issues_with_label", return_value=issues) as get_issues:
            filtered, scan_state = issue_queue.get_prioritized_issues_with_label(
                forge_metadata.LABEL_LIBRARY_NEW,
                25,
                user_requested_only=True,
            )

        get_issues.assert_called_once_with(
            forge_metadata.LABEL_LIBRARY_NEW,
            25,
            0,
            [config.LABEL_HIGH_PRIORITY],
            [],
        )
        self.assertEqual([issue["number"] for issue in filtered], [2])
        self.assertEqual(scan_state.tier_offset, 2)
        self.assertFalse(scan_state.exhausted)

    def test_prioritized_issue_fetch_keeps_scanning_past_a_fully_filtered_batch(self) -> None:
        batches = [
            [{"number": 1, "author": {"login": "graalvmbot"}, "labels": []}],
            [{"number": 2, "author": {"login": "external-user"}, "labels": []}],
        ]

        with patch.object(issue_queue, "get_issues_with_label", side_effect=batches) as get_issues:
            filtered, scan_state = issue_queue.get_prioritized_issues_with_label(
                forge_metadata.LABEL_LIBRARY_NEW,
                25,
                user_requested_only=True,
            )

        self.assertEqual([issue["number"] for issue in filtered], [2])
        self.assertEqual(get_issues.call_count, 2)
        self.assertEqual(scan_state.scanned_count, 2)
        self.assertFalse(scan_state.exhausted)

    def test_random_issue_scan_offset_uses_open_issue_count(self) -> None:
        with patch.object(issue_processing, "count_issues_with_label", return_value=500), \
                patch.object(random, "randrange", return_value=123) as randrange:
            self.assertEqual(
                forge_metadata.resolve_random_issue_scan_offset(forge_metadata.LABEL_LIBRARY_NEW),
                123,
            )

        randrange.assert_called_once_with(500)

    def test_random_issue_scan_offset_uses_user_requested_count(self) -> None:
        with patch.object(issue_processing, "count_issues_with_label", return_value=500) as count_issues, \
                patch.object(random, "randrange", return_value=123):
            forge_metadata.resolve_random_issue_scan_offset(
                forge_metadata.LABEL_LIBRARY_NEW,
                user_requested_only=True,
            )

        count_issues.assert_called_once_with(
            forge_metadata.LABEL_LIBRARY_NEW,
            user_requested_only=True,
        )

    def test_random_issue_scan_offset_counts_only_selected_priority_tier(self) -> None:
        with patch.object(issue_processing, "count_issues_with_label", return_value=50) as count_issues, \
                patch.object(random, "randrange", return_value=12):
            offset = forge_metadata.resolve_random_issue_scan_offset(
                forge_metadata.LABEL_LIBRARY_NEW,
                priority=config.PRIORITY_NORMAL,
            )

        self.assertEqual(offset, 12)
        count_issues.assert_called_once_with(
            forge_metadata.LABEL_LIBRARY_NEW,
            [],
            False,
            [config.LABEL_HIGH_PRIORITY, config.LABEL_PRIORITY],
        )

    def test_fixture_issue_listing_can_exclude_non_user_authors(self) -> None:
        state = FixtureGitHubState([
            FixtureIssue(
                number=1,
                title="Add support for org.example:user:1.0.0",
                author="external-user",
                body="",
                state="OPEN",
                labels=[forge_metadata.LABEL_LIBRARY_NEW],
                assignees=[],
                project_number=forge_metadata.PROJECT_NUMBER,
                project_item_id="item-1",
                project_status=config.STATUS_TODO,
                blockers=[],
                comments=[],
                continuation_marker=None,
                worktree_files={},
                fixture_path="/tmp/fixture.yaml",
                url="fixture://issue/1",
            ),
            FixtureIssue(
                number=2,
                title="Add support for org.example:bot:1.0.0",
                author="graalvmbot",
                body="",
                state="OPEN",
                labels=[forge_metadata.LABEL_LIBRARY_NEW],
                assignees=[],
                project_number=forge_metadata.PROJECT_NUMBER,
                project_item_id="item-2",
                project_status=config.STATUS_TODO,
                blockers=[],
                comments=[],
                continuation_marker=None,
                worktree_files={},
                fixture_path="/tmp/fixture.yaml",
                url="fixture://issue/2",
            ),
        ])

        issues = state.list_open_issues_by_label(
            forge_metadata.LABEL_LIBRARY_NEW,
            limit=10,
            excluded_authors=config.NON_USER_REQUESTED_ISSUE_AUTHORS,
        )

        self.assertEqual([issue["number"] for issue in issues], [1])
