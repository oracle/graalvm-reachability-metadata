# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

from dispatcher_test_support import *  # noqa: F401,F403 - shared dispatcher test fixtures


class IssueClaimCacheTests(unittest.TestCase):
    def test_read_cache_ignores_missing_corrupt_and_expired_cache(self) -> None:
        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(issue_cache, "get_issue_claim_locks_root", return_value=lock_root):
                self.assertEqual(issue_cache.read_issue_claim_cache(now=100.0), {})

                with open(issue_cache.get_issue_claim_cache_path(), "w", encoding="utf-8") as cache_file:
                    cache_file.write("{not json")
                self.assertEqual(issue_cache.read_issue_claim_cache(now=100.0), {})

                with open(issue_cache.get_issue_claim_cache_path(), "w", encoding="utf-8") as cache_file:
                    json.dump(
                        {
                            "version": config.ISSUE_CLAIM_CACHE_VERSION,
                            "repo": config.REPO,
                            "updated_at_epoch": 0.0,
                            "entries": {
                                "1412": {
                                    "observed_at_epoch": 0.0,
                                    "reason": config.ISSUE_CLAIM_CACHE_REASON_IN_PROGRESS,
                                    "project_status": config.STATUS_IN_PROGRESS,
                                },
                            },
                        },
                        cache_file,
                    )

                self.assertEqual(issue_cache.read_issue_claim_cache(now=901.0), {})

    def test_record_and_invalidate_cache_entry(self) -> None:
        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(issue_cache, "get_issue_claim_locks_root", return_value=lock_root):
                issue_cache.record_issue_claim_cache_observations(
                    [
                        records.IssueClaimCacheObservation(
                            issue_number=1412,
                            reason=config.ISSUE_CLAIM_CACHE_REASON_ASSIGNED,
                            assignees=("automation-user",),
                        ),
                    ],
                    now=100.0,
                )

                cache = issue_cache.read_issue_claim_cache(now=100.0)
                self.assertEqual(cache[1412].reason, config.ISSUE_CLAIM_CACHE_REASON_ASSIGNED)
                self.assertEqual(cache[1412].assignees, ("automation-user",))

                issue_cache.invalidate_issue_claim_cache_entry(1412, now=101.0)
                self.assertEqual(issue_cache.read_issue_claim_cache(now=101.0), {})

    def test_clear_issue_caches_removes_claim_and_search_caches(self) -> None:
        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(issue_cache, "get_issue_claim_locks_root", return_value=lock_root):
                issue_cache.record_issue_claim_cache_observations(
                    [
                        records.IssueClaimCacheObservation(
                            issue_number=1412,
                            reason=config.ISSUE_CLAIM_CACHE_REASON_BLOCKED,
                            open_blockers=(99,),
                        ),
                    ],
                    now=100.0,
                )
                issue_cache._write_issue_search_cache_payload(
                    issue_cache._empty_issue_search_cache_payload(100.0),
                    100.0,
                )

                self.assertTrue(os.path.exists(issue_cache.get_issue_claim_cache_path()))
                self.assertTrue(os.path.exists(issue_cache.get_issue_search_cache_path()))

                forge_metadata.clear_issue_caches()

                self.assertFalse(os.path.exists(issue_cache.get_issue_claim_cache_path()))
                self.assertFalse(os.path.exists(issue_cache.get_issue_search_cache_path()))

    def test_cached_own_assignment_is_not_returned_as_skip(self) -> None:
        issue = {
            "number": 1412,
            "title": "Add support for org.example:cached:1.0.0",
            "labels": [],
            "assignees": [],
        }

        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(issue_cache, "get_issue_claim_locks_root", return_value=lock_root):
                issue_cache.record_issue_claim_cache_observations(
                    [
                        records.IssueClaimCacheObservation(
                            issue_number=1412,
                            reason=config.ISSUE_CLAIM_CACHE_REASON_ASSIGNED,
                            assignees=("automation-user",),
                        ),
                    ],
                )

                self.assertEqual(
                    claim_preflight.get_cached_issue_claim_skips([issue], "automation-user"),
                    {},
                )
                self.assertIn(
                    1412,
                    claim_preflight.get_cached_issue_claim_skips([issue], "other-user"),
                )

    def test_process_loop_does_not_preflight_cached_issue(self) -> None:
        cached_issue = {
            "number": 1,
            "title": "Add support for org.example:cached:1.0.0",
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
                            reason=config.ISSUE_CLAIM_CACHE_REASON_IN_PROGRESS,
                            project_status=config.STATUS_IN_PROGRESS,
                        ),
                    ],
                )

                with patch.object(issue_processing, "validate_issue_processing_environment"), \
                        patch.object(
                            issue_processing,
                            "get_prioritized_issues_with_label",
                            return_value=([cached_issue, claimable_issue], _scan_state(2, exhausted=True)),
                        ), \
                        patch.object(
                            claim_preflight,
                            "get_issue_claim_preflights_or_empty",
                        ) as get_issue_claim_preflights_or_empty, \
                        patch.object(
                            issue_processing,
                            "claim_issue_for_processing",
                            return_value=_claimed_issue(),
                        ), \
                        patch.object(
                            issue_processing,
                            "process_claimed_issue_lifecycle",
                            return_value=True,
                        ):
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
                    )

        self.assertEqual(processed, 1)
        get_issue_claim_preflights_or_empty.assert_not_called()

    def test_process_loop_accepts_claim_negative_result_without_preflight(self) -> None:
        issue = {
            "number": 1,
            "title": "Add support for org.example:blocked:1.0.0",
            "labels": [],
            "assignees": [],
        }

        def claim_and_cache_negative_result(*_args: object, **_kwargs: object) -> None:
            issue_cache.record_issue_claim_cache_observations(
                [
                    records.IssueClaimCacheObservation(
                        issue_number=1,
                        reason=config.ISSUE_CLAIM_CACHE_REASON_BLOCKED,
                        open_blockers=(99,),
                    ),
                ],
            )
            return None

        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(issue_cache, "get_issue_claim_locks_root", return_value=lock_root), \
                    patch.object(issue_processing, "validate_issue_processing_environment"), \
                    patch.object(
                        issue_processing,
                        "get_prioritized_issues_with_label",
                        side_effect=[
                            ([issue], _scan_state(1)),
                            ([], _scan_state(1, exhausted=True)),
                        ],
                    ), \
                    patch.object(
                        claim_preflight,
                        "get_issue_claim_preflights_or_empty",
                    ) as get_issue_claim_preflights_or_empty, \
                    patch.object(
                        issue_processing,
                        "claim_issue_for_processing",
                        side_effect=claim_and_cache_negative_result,
                    ) as claim_issue_for_processing:
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
                )

                cache = issue_cache.read_issue_claim_cache()
                self.assertEqual(cache[1].reason, config.ISSUE_CLAIM_CACHE_REASON_BLOCKED)
                self.assertEqual(cache[1].open_blockers, (99,))

        self.assertEqual(processed, 0)
        get_issue_claim_preflights_or_empty.assert_not_called()
        claim_issue_for_processing.assert_called_once()

    def test_process_loop_attempts_uncached_candidates_without_preflight(self) -> None:
        issues = [
            {
                "number": issue_number,
                "title": f"Add support for org.example:lib{issue_number}:1.0.0",
                "labels": [],
                "assignees": [],
            }
            for issue_number in range(1, 7)
        ]

        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(issue_cache, "get_issue_claim_locks_root", return_value=lock_root), \
                    patch.object(issue_processing, "validate_issue_processing_environment"), \
                    patch.object(
                        issue_processing,
                        "get_prioritized_issues_with_label",
                        return_value=(issues, _scan_state(len(issues), exhausted=True)),
                    ), \
                    patch.object(
                        claim_preflight,
                        "get_issue_claim_preflights_or_empty",
                    ) as get_issue_claim_preflights_or_empty, \
                    patch.object(
                        issue_processing,
                        "claim_issue_for_processing",
                        side_effect=[None, None, None, None, None, _claimed_issue()],
                    ) as claim_issue_for_processing, \
                    patch.object(
                        issue_processing,
                        "process_claimed_issue_lifecycle",
                        return_value=True,
                    ):
                self.assertEqual(
                    forge_metadata.process_issues_with_label(
                        forge_metadata.LABEL_LIBRARY_NEW,
                        1,
                        0,
                        "/tmp/reachability",
                        "/tmp/metrics",
                        None,
                        False,
                        "automation-user",
                        1,
                    ),
                    1,
                )

        get_issue_claim_preflights_or_empty.assert_not_called()
        self.assertEqual(
            claim_issue_for_processing.call_args_list,
            [
                call(
                    issue,
                    forge_metadata.LABEL_LIBRARY_NEW,
                    "/tmp/reachability",
                    "/tmp/metrics",
                    "automation-user",
                )
                for issue in issues
            ],
        )

    def test_verbose_process_loop_logs_scan_start_and_progress(self) -> None:
        issues = [
            {
                "number": issue_number,
                "title": f"Add support for org.example:lib{issue_number}:1.0.0",
                "labels": [],
                "assignees": [],
            }
            for issue_number in range(1, 251)
        ]

        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(issue_cache, "get_issue_claim_locks_root", return_value=lock_root), \
                    patch.object(issue_processing, "validate_issue_processing_environment"), \
                    patch.object(
                        issue_processing,
                        "get_prioritized_issues_with_label",
                        return_value=(issues, _scan_state(len(issues), exhausted=True)),
                    ), \
                    patch.object(
                        issue_processing,
                        "claim_issue_for_processing",
                        return_value=None,
                    ), \
                    patch.dict(os.environ, {"FORGE_VERBOSE": "1"}), \
                    patch("sys.stdout", new_callable=io.StringIO) as stdout:
                self.assertEqual(
                    forge_metadata.process_issues_with_label(
                        forge_metadata.LABEL_LIBRARY_NEW,
                        1,
                        0,
                        "/tmp/reachability",
                        "/tmp/metrics",
                        None,
                        False,
                        "automation-user",
                        1,
                    ),
                    0,
                )

        output = stdout.getvalue()
        self.assertIn("Starting issue scan for label 'library-new-request'", output)
        self.assertIn("Looked through 100 issue(s) for label 'library-new-request'", output)
        self.assertIn("Looked through 200 issue(s) for label 'library-new-request'", output)
        self.assertNotIn("Looked through 300 issue(s)", output)

    def test_process_loop_fetches_only_selected_priority_tier(self) -> None:
        priority = config.PRIORITY_NORMAL
        tier = issue_queue.get_issue_priority_tier(priority)
        with patch.object(issue_processing, "validate_issue_processing_environment"), \
                patch.object(issue_processing, "get_prioritized_issues_with_label") as prioritized_fetch, \
                patch.object(issue_processing, "get_issues_with_label", return_value=[]) as get_issues, \
                patch("sys.stdout", new_callable=io.StringIO):
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
                priority=priority,
            )

        self.assertEqual(processed, 0)
        get_issues.assert_called_once_with(
            forge_metadata.LABEL_LIBRARY_NEW,
            config.DEFAULT_ISSUE_SCAN_BATCH_SIZE,
            0,
            list(tier.extra_labels),
            list(tier.excluded_labels),
            user_requested_only=False,
        )
        prioritized_fetch.assert_not_called()

    def test_priority_choices_map_to_exclusive_label_filters(self) -> None:
        expected_filters = {
            config.PRIORITY_HIGH: (
                (config.LABEL_HIGH_PRIORITY,),
                (),
            ),
            config.LABEL_PRIORITY: (
                (config.LABEL_PRIORITY,),
                (config.LABEL_HIGH_PRIORITY,),
            ),
            config.PRIORITY_NORMAL: (
                (),
                (config.LABEL_HIGH_PRIORITY, config.LABEL_PRIORITY),
            ),
        }
        for priority, filters in expected_filters.items():
            with self.subTest(priority=priority):
                tier = issue_queue.get_issue_priority_tier(priority)
                self.assertEqual((tier.extra_labels, tier.excluded_labels), filters)


class IssueSearchCacheTests(unittest.TestCase):
    def test_search_page_cache_is_shared_by_label_queries(self) -> None:
        issue = {
            "number": 1412,
            "title": "Add support for org.example:cached:1.0.0",
            "url": "https://github.com/oracle/graalvm-reachability-metadata/issues/1412",
            "labels": [{"name": forge_metadata.LABEL_LIBRARY_NEW}],
            "assignees": [],
        }

        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(issue_cache, "get_issue_claim_locks_root", return_value=lock_root), \
                    patch.object(time, "time", return_value=100.0), \
                    patch.object(issue_queue, "fetch_issue_search_page", return_value=[issue]) as fetch_page:
                self.assertEqual(
                    issue_queue.get_issues_with_label(forge_metadata.LABEL_LIBRARY_NEW, 1),
                    [issue],
                )
                self.assertEqual(
                    issue_queue.get_issues_with_label(forge_metadata.LABEL_LIBRARY_NEW, 1),
                    [issue],
                )

        fetch_page.assert_called_once()

    def test_search_count_cache_is_shared_by_random_offset_resolution(self) -> None:
        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(issue_cache, "get_issue_claim_locks_root", return_value=lock_root), \
                    patch.object(time, "time", return_value=100.0), \
                    patch.object(issue_queue, "fetch_issue_search_count", return_value=42) as fetch_count:
                self.assertEqual(issue_queue.count_issues_with_label(forge_metadata.LABEL_LIBRARY_NEW), 42)
                self.assertEqual(issue_queue.count_issues_with_label(forge_metadata.LABEL_LIBRARY_NEW), 42)

        fetch_count.assert_called_once()
