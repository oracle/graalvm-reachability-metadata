# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

from git_scripts.github_cli import GitHubRateLimitExceeded
from dispatcher_test_support import *  # noqa: F401,F403 - shared dispatcher test fixtures


class IssueClaimPreflightTests(unittest.TestCase):

    def test_preflight_fallback_does_not_continue_after_rate_limit(self) -> None:
        issue = {"number": 1412, "labels": []}

        with patch.object(
                claim_preflight,
                "get_issue_claim_preflights",
                side_effect=GitHubRateLimitExceeded("GitHub API rate limit exceeded"),
        ):
            with self.assertRaises(GitHubRateLimitExceeded):
                claim_preflight.get_issue_claim_preflights_or_empty([issue])

    def test_preflight_fallback_reports_github_error_without_traceback(self) -> None:
        issue = {"number": 1412, "labels": []}
        error = subprocess.CalledProcessError(
            1,
            ["gh", "api", "graphql", "-f", "query=\nquery { ... }"],
            output="",
            stderr="GraphQL: Field 'blockedBy' doesn't exist on type 'Issue'",
        )

        with patch.object(claim_preflight, "get_issue_claim_preflights", side_effect=error), \
                patch("sys.stderr", new_callable=io.StringIO) as stderr:
            self.assertEqual(
                claim_preflight.get_issue_claim_preflights_or_empty([issue]),
                {},
            )

        error_output = stderr.getvalue()
        self.assertIn("GraphQL: Field 'blockedBy' doesn't exist on type 'Issue'", error_output)
        self.assertNotIn("Traceback", error_output)
        self.assertNotIn("query=", error_output)

    def test_claimable_preflight_does_not_skip(self) -> None:
        issue = {"number": 1412, "labels": []}
        self.assertFalse(
            claim_preflight.should_skip_issue_from_preflight(issue, _preflight())
        )

    def test_assigned_preflight_skips_issue(self) -> None:
        issue = {"number": 1412, "labels": []}
        self.assertTrue(
            claim_preflight.should_skip_issue_from_preflight(
                issue,
                _preflight(assignees=("automation-user",)),
            )
        )

    def test_preflight_assigned_to_authenticated_user_does_not_skip(self) -> None:
        issue = {"number": 1412, "labels": []}
        self.assertFalse(
            claim_preflight.should_skip_issue_from_preflight(
                issue,
                _preflight(assignees=("automation-user",)),
                authenticated_user="automation-user",
            )
        )

    def test_non_todo_preflight_skips_issue(self) -> None:
        issue = {"number": 1412, "labels": []}
        self.assertTrue(
            claim_preflight.should_skip_issue_from_preflight(
                issue,
                _preflight(project_status=config.STATUS_IN_PROGRESS),
            )
        )

    def test_chunked_dynamic_access_preflight_skips_in_progress_issue(self) -> None:
        issue = _search_issue(1412, [config.LABEL_CHUNKED_DYNAMIC_ACCESS])
        self.assertTrue(
            claim_preflight.should_skip_issue_from_preflight(
                issue,
                _preflight(project_status=config.STATUS_IN_PROGRESS),
            )
        )

    def test_open_blocker_preflight_skips_issue(self) -> None:
        issue = {"number": 1412, "labels": []}
        self.assertTrue(
            claim_preflight.should_skip_issue_from_preflight(
                issue,
                _preflight(open_blockers=(1392,)),
                take_blocked_issues=False,
            )
        )

    def test_open_blocker_preflight_allows_issue_when_override_is_enabled(self) -> None:
        issue = {"number": 1412, "labels": []}
        self.assertFalse(
            claim_preflight.should_skip_issue_from_preflight(
                issue,
                _preflight(open_blockers=(1392,)),
                take_blocked_issues=True,
            )
        )

    def test_incomplete_preflight_falls_back_to_fresh_checks(self) -> None:
        issue = {"number": 1412, "labels": []}
        self.assertFalse(
            claim_preflight.should_skip_issue_from_preflight(
                issue,
                _preflight(assignees=("automation-user",), complete=False),
            )
        )

    def test_batched_preflight_extracts_claim_state(self) -> None:
        response = {
            "data": {
                "repository": {
                    "issue_1412": {
                        "number": 1412,
                        "assignees": {
                            "nodes": [],
                            "pageInfo": {"hasNextPage": False, "endCursor": None},
                        },
                        "projectItems": {
                            "nodes": [
                                {
                                    "id": "project-item",
                                    "project": {"number": dispatcher_config.PROJECT_NUMBER},
                                    "fieldValues": {
                                        "nodes": [
                                            {
                                                "name": config.STATUS_TODO,
                                                "field": {"name": config.STATUS_FIELD_NAME},
                                            },
                                        ],
                                    },
                                },
                            ],
                        },
                        "blockedBy": {
                            "nodes": [{"number": 1392, "closed": False}],
                            "pageInfo": {"hasNextPage": False, "endCursor": None},
                        },
                    },
                },
            },
        }

        with patch.object(claim_preflight, "gh_json", return_value=response) as gh_json:
            preflights = claim_preflight.get_issue_claim_preflights([1412])

        gh_json.assert_called_once()
        self.assertEqual(gh_json.call_args.kwargs, {"quiet": True})
        self.assertEqual(
            preflights[1412],
            _preflight(open_blockers=(1392,)),
        )
        self.assertNotIn("blocking(first:", gh_json.call_args.args[-1])

    def test_batched_preflight_default_chunk_size_stays_under_graphql_node_limit(self) -> None:
        issue_numbers = list(range(1, 10))
        responses = [
            _empty_preflight_response([1, 2, 3, 4]),
            _empty_preflight_response([5, 6, 7, 8]),
            _empty_preflight_response([9]),
        ]

        with patch.object(claim_preflight, "gh_json", side_effect=responses) as gh_json:
            preflights = claim_preflight.get_issue_claim_preflights(issue_numbers)

        self.assertEqual(gh_json.call_count, 3)
        self.assertEqual(set(preflights), set(issue_numbers))
        self.assertLessEqual(config.ISSUE_CLAIM_PREFLIGHT_CHUNK_SIZE, 4)

    def test_refresh_issue_payload_for_claim_skips_closed_issue(self) -> None:
        issue = _search_issue(1412, [dispatcher_config.LABEL_LIBRARY_NEW])
        fresh_issue = {
            **_search_issue(1412, [dispatcher_config.LABEL_LIBRARY_NEW]),
            "state": "CLOSED",
        }

        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(issue_cache, "get_issue_claim_locks_root", return_value=lock_root), \
                    patch.object(claim_preflight, "get_issue_claim_payload", return_value=fresh_issue):
                self.assertFalse(
                    claim_preflight.refresh_issue_payload_for_claim(
                        issue,
                        dispatcher_config.LABEL_LIBRARY_NEW,
                    )
                )
                cache = issue_cache.read_issue_claim_cache()

        self.assertEqual(issue["state"], "CLOSED")
        self.assertEqual(cache[1412].reason, config.ISSUE_CLAIM_CACHE_REASON_CLOSED)

    def test_refresh_issue_payload_for_claim_skips_human_intervention_label(self) -> None:
        issue = _search_issue(1412, [dispatcher_config.LABEL_LIBRARY_NEW])
        fresh_issue = {
            **_search_issue(
                1412,
                [dispatcher_config.LABEL_LIBRARY_NEW, config.LABEL_HUMAN_INTERVENTION],
            ),
            "state": "OPEN",
        }

        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(issue_cache, "get_issue_claim_locks_root", return_value=lock_root), \
                    patch.object(claim_preflight, "get_issue_claim_payload", return_value=fresh_issue):
                self.assertFalse(
                    claim_preflight.refresh_issue_payload_for_claim(
                        issue,
                        dispatcher_config.LABEL_LIBRARY_NEW,
                    )
                )
                cache = issue_cache.read_issue_claim_cache()

        self.assertTrue(issue_queue.issue_has_label(issue, config.LABEL_HUMAN_INTERVENTION))
        self.assertEqual(cache[1412].reason, config.ISSUE_CLAIM_CACHE_REASON_HUMAN_INTERVENTION)

    def test_legacy_resumable_label_does_not_lift_a_hold(self) -> None:
        # The fetch leaves every held issue out, so the claim must refuse them all alike.
        held_issue = _search_issue(
            1412,
            [dispatcher_config.LABEL_LIBRARY_NEW, config.LABEL_HUMAN_INTERVENTION, config.LABEL_RESUMABLE],
        )

        self.assertFalse(claim_preflight.issue_needs_claim_preflight(held_issue))
        observation = claim_preflight.get_issue_claim_cache_observation_from_payload(held_issue)
        self.assertEqual(observation.reason, config.ISSUE_CLAIM_CACHE_REASON_HUMAN_INTERVENTION)
        self.assertTrue(
            claim_preflight.should_skip_issue_from_preflight(
                held_issue,
                None,
                records.CachedIssueClaimSkip(1412, config.ISSUE_CLAIM_CACHE_REASON_HUMAN_INTERVENTION, 0.0),
            )
        )

    def test_refresh_issue_payload_for_claim_skips_removed_queue_label(self) -> None:
        issue = _search_issue(1412, [dispatcher_config.LABEL_LIBRARY_NEW])
        fresh_issue = {
            **_search_issue(1412, []),
            "state": "OPEN",
        }

        with patch.object(claim_preflight, "get_issue_claim_payload", return_value=fresh_issue), \
                patch("sys.stdout", new_callable=io.StringIO) as stdout:
            self.assertFalse(
                claim_preflight.refresh_issue_payload_for_claim(
                    issue,
                    dispatcher_config.LABEL_LIBRARY_NEW,
                )
            )

        self.assertIn("no longer has label", stdout.getvalue())

    def test_refresh_issue_payload_for_claim_skips_issue_assigned_to_other_user(self) -> None:
        issue = _search_issue(1412, [dispatcher_config.LABEL_LIBRARY_NEW])
        fresh_issue = {
            **_search_issue(1412, [dispatcher_config.LABEL_LIBRARY_NEW]),
            "state": "OPEN",
            "assignees": [{"login": "other-user"}],
        }

        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(issue_cache, "get_issue_claim_locks_root", return_value=lock_root), \
                    patch.object(claim_preflight, "get_issue_claim_payload", return_value=fresh_issue):
                self.assertFalse(
                    claim_preflight.refresh_issue_payload_for_claim(
                        issue,
                        dispatcher_config.LABEL_LIBRARY_NEW,
                        "automation-user",
                    )
                )
                cache = issue_cache.read_issue_claim_cache()

        self.assertEqual(cache[1412].reason, config.ISSUE_CLAIM_CACHE_REASON_ASSIGNED)
        self.assertEqual(cache[1412].assignees, ("other-user",))

    def test_refresh_issue_payload_for_claim_allows_issue_assigned_to_authenticated_user(self) -> None:
        issue = _search_issue(1412, [dispatcher_config.LABEL_LIBRARY_NEW])
        fresh_issue = {
            **_search_issue(1412, [dispatcher_config.LABEL_LIBRARY_NEW]),
            "state": "OPEN",
            "assignees": [{"login": "automation-user"}],
        }

        with patch.object(claim_preflight, "get_issue_claim_payload", return_value=fresh_issue):
            self.assertTrue(
                claim_preflight.refresh_issue_payload_for_claim(
                    issue,
                    dispatcher_config.LABEL_LIBRARY_NEW,
                    "automation-user",
                )
            )

    def test_preflight_skips_issue_payloads_that_are_already_locally_unclaimable(self) -> None:
        human_intervention_issue = {
            "number": 1,
            "labels": [{"name": config.LABEL_HUMAN_INTERVENTION}],
            "assignees": [],
        }
        assigned_issue = {
            "number": 2,
            "labels": [],
            "assignees": [{"login": "automation-user"}],
        }
        own_assigned_issue = {
            "number": 4,
            "labels": [],
            "assignees": [{"login": "current-user"}],
        }
        claimable_issue = {
            "number": 3,
            "labels": [],
            "assignees": [],
        }

        with patch.object(
                claim_preflight,
                "get_issue_claim_preflights",
                return_value={
                    4: _preflight(issue_number=4, assignees=("current-user",)),
                    3: _preflight(issue_number=3),
                },
        ) as get_issue_claim_preflights:
            self.assertEqual(
                claim_preflight.get_issue_claim_preflights_or_empty(
                    [human_intervention_issue, assigned_issue, own_assigned_issue, claimable_issue],
                    authenticated_user="current-user",
                ),
                {
                    4: _preflight(issue_number=4, assignees=("current-user",)),
                    3: _preflight(issue_number=3),
                },
            )

        get_issue_claim_preflights.assert_called_once_with([4, 3])

    def test_graalvm_bug_holds_an_issue_whatever_else_it_carries(self) -> None:
        issue: dict = {
            "number": 1412,
            "labels": [{"name": config.LABEL_GRAALVM_BUG}, {"name": config.LABEL_RESUMABLE}],
            "assignees": [],
        }
        self.assertFalse(claim_preflight.issue_needs_claim_preflight(issue, "automation-user"))
        observation = claim_preflight.get_issue_claim_cache_observation_from_payload(issue, "automation-user")
        self.assertEqual(observation.reason, config.ISSUE_CLAIM_CACHE_REASON_GRAALVM_BUG)

        cached_skip: records.CachedIssueClaimSkip = records.CachedIssueClaimSkip(
            issue_number=1412,
            reason=config.ISSUE_CLAIM_CACHE_REASON_GRAALVM_BUG,
            observed_at_epoch=100.0,
        )
        self.assertTrue(
            issue_queue.cached_skip_blocks_authenticated_user(cached_skip, "automation-user", take_blocked_issues=True)
        )
        self.assertEqual(
            claim_preflight.format_cached_issue_claim_skip(cached_skip),
            f"recently cached with label '{config.LABEL_GRAALVM_BUG}', blocked on a GraalVM defect",
        )

    def test_payload_assignees_do_not_skip_without_fresh_claim_state(self) -> None:
        issue = {
            "number": 1412,
            "labels": [],
            "assignees": [{"login": "automation-user"}],
        }

        self.assertFalse(
            claim_preflight.should_skip_issue_from_preflight(issue, None)
        )

    def test_payload_assigned_to_authenticated_user_does_not_skip(self) -> None:
        issue = {
            "number": 1412,
            "labels": [],
            "assignees": [{"login": "automation-user"}],
        }

        self.assertFalse(
            claim_preflight.should_skip_issue_from_preflight(
                issue,
                None,
                authenticated_user="automation-user",
            )
        )

    def test_cached_skip_skips_without_preflight(self) -> None:
        issue = {
            "number": 1412,
            "labels": [],
            "assignees": [],
        }
        cached_skip = records.CachedIssueClaimSkip(
            issue_number=1412,
            reason=config.ISSUE_CLAIM_CACHE_REASON_IN_PROGRESS,
            observed_at_epoch=100.0,
            project_status=config.STATUS_IN_PROGRESS,
        )

        self.assertTrue(
            claim_preflight.should_skip_issue_from_preflight(issue, None, cached_skip)
        )

    def test_cached_own_assignment_does_not_skip(self) -> None:
        issue = {
            "number": 1412,
            "labels": [],
            "assignees": [],
        }
        cached_skip = records.CachedIssueClaimSkip(
            issue_number=1412,
            reason=config.ISSUE_CLAIM_CACHE_REASON_ASSIGNED,
            observed_at_epoch=100.0,
            assignees=("automation-user",),
        )

        self.assertFalse(
            claim_preflight.should_skip_issue_from_preflight(
                issue,
                None,
                cached_skip,
                authenticated_user="automation-user",
            )
        )
