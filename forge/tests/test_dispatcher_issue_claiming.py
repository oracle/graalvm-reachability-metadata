# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

from dispatcher_test_support import *  # noqa: F401,F403 - shared dispatcher test fixtures


class IssueClaimLockTests(unittest.TestCase):
    def test_try_claim_issue_skips_when_local_runner_holds_issue_lock(self) -> None:
        issue = {
            "number": 1412,
            "title": "Add support for org.example:lib:1.0.0",
            "labels": [],
            "assignees": [],
        }
        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(issue_cache, "get_issue_claim_locks_root", return_value=lock_root):
                claim_lock = issue_cache.try_acquire_issue_claim_lock(issue["number"])
                self.assertIsNotNone(claim_lock)
                try:
                    with patch.object(
                            issue_claiming,
                            "get_open_blocking_issue_numbers",
                    ) as get_open_blocking_issues:
                        self.assertIsNone(issue_claiming.try_claim_issue(issue, "automation-user"))
                        get_open_blocking_issues.assert_not_called()
                finally:
                    claim_lock.release()

    def test_try_claim_issue_does_not_prioritize_open_blockers(self) -> None:
        issue = {
            "number": 1412,
            "title": "Add support for org.example:lib:1.0.0",
            "labels": [],
            "assignees": [],
        }
        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(issue_cache, "get_issue_claim_locks_root", return_value=lock_root), \
                    patch.object(issue_claiming, "refresh_issue_payload_for_claim", return_value=True), \
                    patch.object(issue_claiming, "get_open_blocking_issue_numbers", return_value=[1392]), \
                    patch.object(issue_admin, "add_issue_label") as add_issue_label, \
                    patch.object(issue_claiming, "get_issue_assignees") as get_issue_assignees:
                self.assertIsNone(
                    issue_claiming.try_claim_issue(
                        issue,
                        "automation-user",
                        take_blocked_issues=False,
                    )
                )

        add_issue_label.assert_not_called()
        get_issue_assignees.assert_not_called()

    def test_try_claim_issue_refreshes_paused_issue_before_claim_checks(self) -> None:
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
                    patch.object(claim_preflight, "get_issue_claim_payload", return_value=fresh_issue), \
                    patch.object(issue_claiming, "get_open_blocking_issue_numbers") as get_blockers:
                self.assertIsNone(
                    issue_claiming.try_claim_issue(
                        issue,
                        "automation-user",
                        forge_metadata.LABEL_LIBRARY_NEW,
                    )
                )
                cache = issue_cache.read_issue_claim_cache()

        get_blockers.assert_not_called()
        self.assertEqual(cache[1412].reason, config.ISSUE_CLAIM_CACHE_REASON_HUMAN_INTERVENTION)

    def test_try_claim_issue_refreshes_assignees_after_local_lock(self) -> None:
        issue = {
            "number": 1412,
            "title": "Add support for org.example:lib:1.0.0",
            "labels": [],
            "assignees": [],
        }
        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(issue_cache, "get_issue_claim_locks_root", return_value=lock_root), \
                    patch.object(issue_claiming, "refresh_issue_payload_for_claim", return_value=True), \
                    patch.object(issue_claiming, "get_open_blocking_issue_numbers", return_value=[]), \
                    patch.object(issue_claiming, "get_issue_assignees", return_value=["other-user"]), \
                    patch.object(issue_claiming, "get_project_item_state") as get_project_item_state:
                self.assertIsNone(issue_claiming.try_claim_issue(issue, "automation-user"))
                get_project_item_state.assert_not_called()
                cache = issue_cache.read_issue_claim_cache()
                self.assertEqual(cache[1412].reason, config.ISSUE_CLAIM_CACHE_REASON_ASSIGNED)
                self.assertEqual(cache[1412].assignees, ("other-user",))

    def test_try_claim_issue_accepts_existing_authenticated_user_assignment(self) -> None:
        issue = {
            "number": 1412,
            "title": "Add support for org.example:lib:1.0.0",
            "labels": [],
            "assignees": [{"login": "automation-user"}],
        }
        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(issue_cache, "get_issue_claim_locks_root", return_value=lock_root), \
                    patch.object(issue_claiming, "refresh_issue_payload_for_claim", return_value=True), \
                    patch.object(issue_claiming, "get_open_blocking_issue_numbers", return_value=[]), \
                    patch.object(issue_claiming, "get_issue_assignees", side_effect=[
                        ["automation-user"],
                        ["automation-user"],
                    ]), \
                    patch.object(
                        issue_claiming,
                        "get_project_item_state",
                        return_value=("project-item", config.STATUS_TODO),
                    ), \
                    patch.object(issue_claiming, "set_issue_assignee") as set_issue_assignee, \
                    patch.object(issue_claiming, "set_item_status") as set_item_status, \
                    patch.object(random, "uniform", return_value=0), \
                    patch.object(time, "sleep"), \
                    patch.dict(os.environ, {"FORGE_VERBOSE": "0", "FORGE_DEBUG_LOGGING": "0"}), \
                    patch("sys.stdout", new_callable=io.StringIO) as stdout:
                self.assertEqual(
                    issue_claiming.try_claim_issue(issue, "automation-user"),
                    "project-item",
                )

        set_issue_assignee.assert_called_once_with(1412, "automation-user")
        set_item_status.assert_called_once_with("project-item", config.STATUS_IN_PROGRESS)
        self.assertIn("[claim] Issue #1412 claimed (3/6)", stdout.getvalue())
        self.assertNotIn("Setting issue #1412 assignee", stdout.getvalue())
        self.assertNotIn("Waiting", stdout.getvalue())

    def test_try_claim_issue_skips_chunked_dynamic_access_when_in_progress(self) -> None:
        issue = _search_issue(1412, [config.LABEL_CHUNKED_DYNAMIC_ACCESS])
        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(issue_cache, "get_issue_claim_locks_root", return_value=lock_root), \
                    patch.object(issue_claiming, "refresh_issue_payload_for_claim", return_value=True), \
                    patch.object(issue_claiming, "get_open_blocking_issue_numbers", return_value=[]), \
                    patch.object(issue_claiming, "get_issue_assignees", return_value=[]), \
                    patch.object(
                        issue_claiming,
                        "get_project_item_state",
                        return_value=("project-item", config.STATUS_IN_PROGRESS),
                    ), \
                    patch.object(issue_claiming, "set_issue_assignee") as set_issue_assignee, \
                    patch.object(issue_claiming, "set_item_status") as set_item_status, \
                    patch.object(random, "uniform", return_value=0), \
                    patch.object(time, "sleep"):
                self.assertIsNone(
                    issue_claiming.try_claim_issue(issue, "automation-user"),
                )

        set_issue_assignee.assert_not_called()
        set_item_status.assert_not_called()

    def test_try_claim_issue_uses_combined_project_status_lookup(self) -> None:
        issue = {
            "number": 1412,
            "title": "Add support for org.example:lib:1.0.0",
            "labels": [],
            "assignees": [],
        }
        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(issue_cache, "get_issue_claim_locks_root", return_value=lock_root), \
                    patch.object(issue_claiming, "refresh_issue_payload_for_claim", return_value=True), \
                    patch.object(issue_claiming, "get_open_blocking_issue_numbers", return_value=[]), \
                    patch.object(issue_claiming, "get_issue_assignees", side_effect=[[], ["automation-user"]]), \
                    patch.object(
                        issue_claiming,
                        "get_project_item_state",
                        return_value=("project-item", config.STATUS_TODO),
                    ) as get_project_item_state, \
                    patch.object(issue_claiming, "get_item_status") as get_item_status, \
                    patch.object(issue_claiming, "set_issue_assignee") as set_issue_assignee, \
                    patch.object(issue_claiming, "set_item_status") as set_item_status, \
                    patch.object(random, "uniform", return_value=0), \
                    patch.object(time, "sleep"):
                self.assertEqual(
                    issue_claiming.try_claim_issue(issue, "automation-user"),
                    "project-item",
                )
                cache = issue_cache.read_issue_claim_cache()
                self.assertEqual(cache[1412].reason, config.ISSUE_CLAIM_CACHE_REASON_IN_PROGRESS)

            get_project_item_state.assert_called_once_with(1412)
            get_item_status.assert_not_called()
            set_issue_assignee.assert_called_once_with(1412, "automation-user")
            set_item_status.assert_called_once_with("project-item", config.STATUS_IN_PROGRESS)

    def test_revert_issue_claim_invalidates_cache_entry(self) -> None:
        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(issue_cache, "get_issue_claim_locks_root", return_value=lock_root):
                issue_cache.record_issue_claim_cache_observations(
                    [
                        records.IssueClaimCacheObservation(
                            issue_number=1412,
                            reason=config.ISSUE_CLAIM_CACHE_REASON_IN_PROGRESS,
                            project_status=config.STATUS_IN_PROGRESS,
                        ),
                    ],
                )

                with patch.object(issue_claiming, "set_item_status") as set_item_status, \
                        patch.object(issue_claiming, "clear_issue_assignees") as clear_issue_assignees, \
                        patch.object(issue_claiming, "get_item_status", return_value=config.STATUS_TODO), \
                        patch.object(issue_claiming, "get_issue_assignees", return_value=[]):
                    issue_claiming.revert_issue_claim("item-1", 1412, "test")

                self.assertEqual(issue_cache.read_issue_claim_cache(), {})

        set_item_status.assert_called_once_with("item-1", config.STATUS_TODO)
        clear_issue_assignees.assert_called_once_with(1412)

    def test_revert_issue_claim_clears_assignees_after_status_update_error(self) -> None:
        status_error = subprocess.CalledProcessError(
            1,
            ["gh", "project", "item-edit"],
            output="",
            stderr="non-200 OK status code: 502 Bad Gateway",
        )

        with patch.object(issue_claiming, "set_item_status", side_effect=status_error), \
                patch.object(issue_claiming, "clear_issue_assignees") as clear_issue_assignees, \
                patch.object(issue_claiming, "get_item_status", return_value=config.STATUS_TODO), \
                patch.object(issue_claiming, "get_issue_assignees", return_value=[]), \
                patch("sys.stderr", new_callable=io.StringIO) as stderr:
            issue_claiming.revert_issue_claim("item-1", 1412, "test")

        clear_issue_assignees.assert_called_once_with(1412)
        self.assertIn("could not set project item", stderr.getvalue())
