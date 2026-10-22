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
            "labels": [{"name": dispatcher_config.LABEL_LIBRARY_NEW}],
            "assignees": [],
        }

        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(issue_cache, "get_issue_claim_locks_root", return_value=lock_root), \
                    patch.object(time, "time", return_value=100.0), \
                    patch.object(issue_queue, "fetch_issue_search_page", return_value=[issue]) as fetch_page:
                self.assertEqual(
                    issue_queue.get_issues_with_label(dispatcher_config.LABEL_LIBRARY_NEW, 1),
                    [issue],
                )
                self.assertEqual(
                    issue_queue.get_issues_with_label(dispatcher_config.LABEL_LIBRARY_NEW, 1),
                    [issue],
                )

        fetch_page.assert_called_once()

    def test_search_count_cache_is_shared_by_random_offset_resolution(self) -> None:
        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(issue_cache, "get_issue_claim_locks_root", return_value=lock_root), \
                    patch.object(time, "time", return_value=100.0), \
                    patch.object(issue_queue, "fetch_issue_search_count", return_value=42) as fetch_count:
                self.assertEqual(issue_queue.count_issues_with_label(dispatcher_config.LABEL_LIBRARY_NEW), 42)
                self.assertEqual(issue_queue.count_issues_with_label(dispatcher_config.LABEL_LIBRARY_NEW), 42)

        fetch_count.assert_called_once()
