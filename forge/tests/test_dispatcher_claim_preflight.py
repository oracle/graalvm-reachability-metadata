# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

from dispatcher_test_support import *  # noqa: F401,F403 - shared dispatcher test fixtures


class IssueClaimPreflightTests(unittest.TestCase):

    def test_preflight_fallback_does_not_continue_after_rate_limit(self) -> None:
        issue = {"number": 1412, "labels": []}

        with patch.object(
                claim_preflight,
                "get_issue_claim_preflights",
                side_effect=forge_metadata.GitHubRateLimitExceeded("GitHub API rate limit exceeded"),
        ):
            with self.assertRaises(forge_metadata.GitHubRateLimitExceeded):
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
                                    "project": {"number": forge_metadata.PROJECT_NUMBER},
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
        issue = _search_issue(1412, [forge_metadata.LABEL_LIBRARY_NEW])
        fresh_issue = {
            **_search_issue(1412, [forge_metadata.LABEL_LIBRARY_NEW]),
            "state": "CLOSED",
        }

        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(issue_cache, "get_issue_claim_locks_root", return_value=lock_root), \
                    patch.object(claim_preflight, "get_issue_claim_payload", return_value=fresh_issue):
                self.assertFalse(
                    claim_preflight.refresh_issue_payload_for_claim(
                        issue,
                        forge_metadata.LABEL_LIBRARY_NEW,
                    )
                )
                cache = issue_cache.read_issue_claim_cache()

        self.assertEqual(issue["state"], "CLOSED")
        self.assertEqual(cache[1412].reason, config.ISSUE_CLAIM_CACHE_REASON_CLOSED)

    def test_refresh_issue_payload_for_claim_skips_human_intervention_label(self) -> None:
        issue = _search_issue(1412, [forge_metadata.LABEL_LIBRARY_NEW])
        fresh_issue = {
            **_search_issue(
                1412,
                [forge_metadata.LABEL_LIBRARY_NEW, config.LABEL_HUMAN_INTERVENTION],
            ),
            "state": "OPEN",
        }

        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(issue_cache, "get_issue_claim_locks_root", return_value=lock_root), \
                    patch.object(claim_preflight, "get_issue_claim_payload", return_value=fresh_issue):
                self.assertFalse(
                    claim_preflight.refresh_issue_payload_for_claim(
                        issue,
                        forge_metadata.LABEL_LIBRARY_NEW,
                    )
                )
                cache = issue_cache.read_issue_claim_cache()

        self.assertTrue(issue_queue.issue_has_label(issue, config.LABEL_HUMAN_INTERVENTION))
        self.assertEqual(cache[1412].reason, config.ISSUE_CLAIM_CACHE_REASON_HUMAN_INTERVENTION)

    def test_refresh_issue_payload_for_claim_skips_removed_queue_label(self) -> None:
        issue = _search_issue(1412, [forge_metadata.LABEL_LIBRARY_NEW])
        fresh_issue = {
            **_search_issue(1412, []),
            "state": "OPEN",
        }

        with patch.object(claim_preflight, "get_issue_claim_payload", return_value=fresh_issue), \
                patch("sys.stdout", new_callable=io.StringIO) as stdout:
            self.assertFalse(
                claim_preflight.refresh_issue_payload_for_claim(
                    issue,
                    forge_metadata.LABEL_LIBRARY_NEW,
                )
            )

        self.assertIn("no longer has label", stdout.getvalue())

    def test_refresh_issue_payload_for_claim_skips_issue_assigned_to_other_user(self) -> None:
        issue = _search_issue(1412, [forge_metadata.LABEL_LIBRARY_NEW])
        fresh_issue = {
            **_search_issue(1412, [forge_metadata.LABEL_LIBRARY_NEW]),
            "state": "OPEN",
            "assignees": [{"login": "other-user"}],
        }

        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(issue_cache, "get_issue_claim_locks_root", return_value=lock_root), \
                    patch.object(claim_preflight, "get_issue_claim_payload", return_value=fresh_issue):
                self.assertFalse(
                    claim_preflight.refresh_issue_payload_for_claim(
                        issue,
                        forge_metadata.LABEL_LIBRARY_NEW,
                        "automation-user",
                    )
                )
                cache = issue_cache.read_issue_claim_cache()

        self.assertEqual(cache[1412].reason, config.ISSUE_CLAIM_CACHE_REASON_ASSIGNED)
        self.assertEqual(cache[1412].assignees, ("other-user",))

    def test_refresh_issue_payload_for_claim_allows_issue_assigned_to_authenticated_user(self) -> None:
        issue = _search_issue(1412, [forge_metadata.LABEL_LIBRARY_NEW])
        fresh_issue = {
            **_search_issue(1412, [forge_metadata.LABEL_LIBRARY_NEW]),
            "state": "OPEN",
            "assignees": [{"login": "automation-user"}],
        }

        with patch.object(claim_preflight, "get_issue_claim_payload", return_value=fresh_issue):
            self.assertTrue(
                claim_preflight.refresh_issue_payload_for_claim(
                    issue,
                    forge_metadata.LABEL_LIBRARY_NEW,
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

    def test_process_loop_uses_cache_to_skip_unclaimable_candidates_without_preflight(self) -> None:
        skipped_issue = {
            "number": 1,
            "title": "Add support for org.example:skipped:1.0.0",
            "labels": [],
            "assignees": [],
        }
        claimable_issue = {
            "number": 2,
            "title": "Add support for org.example:claimable:1.0.0",
            "labels": [],
            "assignees": [],
        }

        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(issue_cache, "get_issue_claim_locks_root", return_value=lock_root):
                issue_cache.record_issue_claim_cache_observations(
                    [
                        records.IssueClaimCacheObservation(
                            issue_number=1,
                            reason=config.ISSUE_CLAIM_CACHE_REASON_BLOCKED,
                            open_blockers=(99,),
                        ),
                    ],
                )

            with patch.object(issue_cache, "get_issue_claim_locks_root", return_value=lock_root), \
                    patch.object(issue_processing, "validate_issue_processing_environment"), \
                    patch.object(
                        issue_processing,
                        "get_prioritized_issues_with_label",
                        side_effect=[
                            ([skipped_issue], _scan_state(1)),
                            ([claimable_issue], _scan_state(2)),
                        ],
                    ) as get_prioritized_issues_with_label, \
                    patch.object(
                        claim_preflight,
                        "get_issue_claim_preflights_or_empty",
                    ) as get_issue_claim_preflights_or_empty, \
                    patch.object(
                        issue_processing,
                        "claim_issue_for_processing",
                        return_value=_claimed_issue(),
                    ) as claim_issue_for_processing, \
                    patch.object(
                        issue_processing,
                        "process_claimed_issue_lifecycle",
                        return_value=True,
                    ), \
                    patch.object(issue_claiming, "get_open_blocking_issue_numbers") as get_open_blocking_issue_numbers, \
                    patch.object(issue_claiming, "get_issue_assignees") as get_issue_assignees, \
                    patch.object(issue_claiming, "get_project_item_state") as get_project_item_state:
                processed = forge_metadata.process_issues_with_label(
                    forge_metadata.LABEL_LIBRARY_NEW,
                    1,
                    0,
                    "/tmp/reachability",
                    "/tmp/metrics",
                    None,
                    False,
                    "automation-user",
                    1,
                    take_blocked_issues=False,
                )

        self.assertEqual(processed, 1)
        self.assertEqual(
            get_prioritized_issues_with_label.call_args_list,
            [
                call(
                    forge_metadata.LABEL_LIBRARY_NEW,
                    config.DEFAULT_ISSUE_SCAN_BATCH_SIZE,
                    issue_queue.IssueQueueScanState(),
                    False,
                ),
                call(
                    forge_metadata.LABEL_LIBRARY_NEW,
                    config.DEFAULT_ISSUE_SCAN_BATCH_SIZE,
                    _scan_state(1),
                    False,
                ),
            ],
        )
        get_issue_claim_preflights_or_empty.assert_not_called()
        claim_issue_for_processing.assert_called_once_with(
            claimable_issue,
            forge_metadata.LABEL_LIBRARY_NEW,
            "/tmp/reachability",
            "/tmp/metrics",
            "automation-user",
        )
        get_open_blocking_issue_numbers.assert_not_called()
        get_issue_assignees.assert_not_called()
        get_project_item_state.assert_not_called()
