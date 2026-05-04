# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import io
import json
import os
import subprocess
import tempfile
import unittest
from unittest.mock import call, patch

import forge_metadata
from git_scripts import common_git


def _project_item_status_response(status: str) -> dict:
    return {
        "data": {
            "repository": {
                "issue_1412": {
                    "projectItems": {
                        "nodes": [
                            {
                                "id": "other-project-item",
                                "project": {"number": 999},
                                "fieldValues": {"nodes": []},
                            },
                            {
                                "id": "project-item",
                                "project": {"number": forge_metadata.PROJECT_NUMBER},
                                "fieldValues": {
                                    "nodes": [
                                        {
                                            "name": status,
                                            "field": {"name": forge_metadata.STATUS_FIELD_NAME},
                                        },
                                    ],
                                },
                            },
                        ],
                    },
                },
            },
        },
    }


def _empty_preflight_response(issue_numbers: list[int]) -> dict:
    return {
        "data": {
            "repository": {
                f"issue_{issue_number}": None
                for issue_number in issue_numbers
            },
        },
    }


def _search_issue(number: int, label_names: list[str] | None = None) -> dict:
    return {
        "number": number,
        "title": f"Issue {number}",
        "html_url": f"https://github.com/oracle/graalvm-reachability-metadata/issues/{number}",
        "labels": [
            {"name": label_name}
            for label_name in (label_names or [])
        ],
        "assignees": [],
    }


def _preflight(
        *,
        issue_number: int = 1412,
        item_id: str | None = "project-item",
        project_status: str | None = forge_metadata.STATUS_TODO,
        assignees: tuple[str, ...] = (),
        open_blockers: tuple[int, ...] = (),
        complete: bool = True,
) -> forge_metadata.IssueClaimPreflight:
    return forge_metadata.IssueClaimPreflight(
        issue_number=issue_number,
        item_id=item_id,
        project_status=project_status,
        assignees=assignees,
        open_blockers=open_blockers,
        complete=complete,
    )


def _claimed_issue(
        label: str = forge_metadata.LABEL_LIBRARY_NEW,
        large_library_resume_artifact: str | None = None,
        large_library_part: int | None = None,
) -> forge_metadata.ClaimedIssue:
    return forge_metadata.ClaimedIssue(
        issue={
            "number": 1412,
            "title": "Add support for org.example:lib:1.0.0",
        },
        label=label,
        item_id="item-1",
        base_reachability_metadata_path="/tmp/reachability",
        worktree_path="/tmp/reachability-worktree",
        scratch_metrics_repo_path="/tmp/metrics-worktree",
        issue_coordinates="org.example:lib:1.0.0",
        large_library_resume_artifact=large_library_resume_artifact,
        large_library_part=large_library_part,
    )


class FinalizeSuccessfulIssueTests(unittest.TestCase):
    def test_not_for_native_image_pr_receives_metrics_repo_path_for_local_ci(self) -> None:
        claimed_issue = _claimed_issue()

        with patch.object(forge_metadata, "find_progress_state_path", return_value=None), \
                patch.object(forge_metadata, "_load_pending_run_metrics", return_value={"status": "success"}), \
                patch.object(forge_metadata, "metadata_coordinate_parts", return_value=("org.example", "lib", "1.0.0")), \
                patch.object(forge_metadata, "is_not_for_native_image", return_value=True), \
                patch.object(forge_metadata, "run_make_pr_not_for_native_image") as make_pr:
            forge_metadata.finalize_successful_issue(claimed_issue)

        make_pr.assert_called_once_with([
            "--coordinates", "org.example:lib:1.0.0",
            "--reachability-metadata-path", "/tmp/reachability-worktree",
            "--metrics-repo-path", "/tmp/metrics-worktree",
        ])


class IssueClaimPreflightTests(unittest.TestCase):
    def test_forge_gh_does_not_log_github_query_by_default(self) -> None:
        completed_process = subprocess.CompletedProcess(
            ["gh"],
            0,
            stdout="{}",
            stderr="",
        )

        with patch.object(forge_metadata.subprocess, "run", return_value=completed_process), \
                patch.dict(os.environ, {common_git.GITHUB_QUERY_LOG_ENV_VAR: ""}), \
                patch("sys.stdout", new_callable=io.StringIO) as stdout:
            forge_metadata.gh(
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

        with patch.object(forge_metadata.subprocess, "run", return_value=completed_process), \
                patch.dict(os.environ, {common_git.GITHUB_QUERY_LOG_ENV_VAR: "1"}), \
                patch("sys.stdout", new_callable=io.StringIO) as stdout:
            forge_metadata.gh(
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

        with patch.object(forge_metadata.subprocess, "run", return_value=completed_process):
            with self.assertRaises(forge_metadata.GitHubRateLimitExceeded):
                forge_metadata.gh("issue", "view", "2099")

    def test_gh_json_raises_typed_rate_limit_error_from_graphql_payload(self) -> None:
        completed_process = subprocess.CompletedProcess(
            ["gh"],
            0,
            stdout='{"errors":[{"type":"RATE_LIMITED","message":"API rate limit exceeded"}]}',
            stderr="",
        )

        with patch.object(forge_metadata, "gh", return_value=completed_process):
            with self.assertRaises(forge_metadata.GitHubRateLimitExceeded):
                forge_metadata.gh_json("api", "graphql")

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
                forge_metadata.subprocess,
                "run",
                side_effect=[failed_process, successful_process],
        ) as run, \
                patch.object(common_git.time, "sleep") as sleep, \
                patch("sys.stderr", new_callable=io.StringIO) as stderr:
            self.assertEqual(
                forge_metadata.gh_json("api", "graphql"),
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
                forge_metadata.subprocess,
                "run",
                side_effect=[failed_process, successful_process],
        ) as run, \
                patch.object(common_git.time, "sleep") as sleep, \
                patch("sys.stderr", new_callable=io.StringIO):
            self.assertEqual(forge_metadata.get_authenticated_user(), "vjovanov")

        self.assertEqual(run.call_count, 2)
        sleep.assert_called_once_with(common_git.GITHUB_TRANSIENT_RETRY_BASE_DELAY_SECONDS)

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
                forge_metadata,
                "get_cached_field_info",
                return_value=("project-id", "field-id", {forge_metadata.STATUS_IN_PROGRESS: "option-id"}),
        ), \
                patch.object(
                    forge_metadata.subprocess,
                    "run",
                    side_effect=[failed_process, successful_process],
                ) as run, \
                patch.object(common_git.time, "sleep") as sleep, \
                patch("sys.stderr", new_callable=io.StringIO) as stderr:
            forge_metadata.set_item_status("item-id", forge_metadata.STATUS_IN_PROGRESS)

        self.assertEqual(run.call_count, 2)
        sleep.assert_called_once_with(common_git.GITHUB_TRANSIENT_RETRY_BASE_DELAY_SECONDS)
        self.assertIn("GitHub API transient failure", stderr.getvalue())

    def test_preflight_fallback_does_not_continue_after_rate_limit(self) -> None:
        issue = {"number": 1412, "labels": []}

        with patch.object(
                forge_metadata,
                "get_issue_claim_preflights",
                side_effect=forge_metadata.GitHubRateLimitExceeded("GitHub API rate limit exceeded"),
        ):
            with self.assertRaises(forge_metadata.GitHubRateLimitExceeded):
                forge_metadata.get_issue_claim_preflights_or_empty([issue])

    def test_preflight_fallback_reports_github_error_without_traceback(self) -> None:
        issue = {"number": 1412, "labels": []}
        error = subprocess.CalledProcessError(
            1,
            ["gh", "api", "graphql", "-f", "query=\nquery { ... }"],
            output="",
            stderr="GraphQL: Field 'blockedBy' doesn't exist on type 'Issue'",
        )

        with patch.object(forge_metadata, "get_issue_claim_preflights", side_effect=error), \
                patch("sys.stderr", new_callable=io.StringIO) as stderr:
            self.assertEqual(
                forge_metadata.get_issue_claim_preflights_or_empty([issue]),
                {},
            )

        error_output = stderr.getvalue()
        self.assertIn("GraphQL: Field 'blockedBy' doesn't exist on type 'Issue'", error_output)
        self.assertNotIn("Traceback", error_output)
        self.assertNotIn("query=", error_output)

    def test_claimable_preflight_does_not_skip(self) -> None:
        issue = {"number": 1412, "labels": []}
        self.assertFalse(
            forge_metadata.should_skip_issue_from_preflight(issue, _preflight())
        )

    def test_assigned_preflight_skips_issue(self) -> None:
        issue = {"number": 1412, "labels": []}
        self.assertTrue(
            forge_metadata.should_skip_issue_from_preflight(
                issue,
                _preflight(assignees=("automation-user",)),
            )
        )

    def test_preflight_assigned_to_authenticated_user_does_not_skip(self) -> None:
        issue = {"number": 1412, "labels": []}
        self.assertFalse(
            forge_metadata.should_skip_issue_from_preflight(
                issue,
                _preflight(assignees=("automation-user",)),
                authenticated_user="automation-user",
            )
        )

    def test_non_todo_preflight_skips_issue(self) -> None:
        issue = {"number": 1412, "labels": []}
        self.assertTrue(
            forge_metadata.should_skip_issue_from_preflight(
                issue,
                _preflight(project_status=forge_metadata.STATUS_IN_PROGRESS),
            )
        )

    def test_large_library_next_part_preflight_allows_in_progress_issue(self) -> None:
        issue = _search_issue(1412, [forge_metadata.LABEL_LARGE_LIBRARY_NEXT_PART])
        self.assertFalse(
            forge_metadata.should_skip_issue_from_preflight(
                issue,
                _preflight(project_status=forge_metadata.STATUS_IN_PROGRESS),
            )
        )

    def test_open_blocker_preflight_skips_issue(self) -> None:
        issue = {"number": 1412, "labels": []}
        self.assertTrue(
            forge_metadata.should_skip_issue_from_preflight(
                issue,
                _preflight(open_blockers=(1392,)),
            )
        )

    def test_incomplete_preflight_falls_back_to_fresh_checks(self) -> None:
        issue = {"number": 1412, "labels": []}
        self.assertFalse(
            forge_metadata.should_skip_issue_from_preflight(
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
                                                "name": forge_metadata.STATUS_TODO,
                                                "field": {"name": forge_metadata.STATUS_FIELD_NAME},
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

        with patch.object(forge_metadata, "gh_json", return_value=response) as gh_json:
            preflights = forge_metadata.get_issue_claim_preflights([1412])

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

        with patch.object(forge_metadata, "gh_json", side_effect=responses) as gh_json:
            preflights = forge_metadata.get_issue_claim_preflights(issue_numbers)

        self.assertEqual(gh_json.call_count, 3)
        self.assertEqual(set(preflights), set(issue_numbers))
        self.assertLessEqual(forge_metadata.ISSUE_CLAIM_PREFLIGHT_CHUNK_SIZE, 4)

    def test_open_issues_blocked_by_issue_counts_use_blocking_edge(self) -> None:
        blocking_nodes = [
            {"number": issue_number, "closed": False}
            for issue_number in range(1, 11)
        ]
        blocking_nodes.append({"number": 99, "closed": True})
        response = {
            "data": {
                "repository": {
                    "issue_1412": {
                        "blocking": {
                            "nodes": blocking_nodes,
                            "pageInfo": {"hasNextPage": False, "endCursor": None},
                        },
                    },
                },
            },
        }

        with patch.object(forge_metadata, "gh_json", return_value=response) as gh_json:
            counts = forge_metadata.get_open_issues_blocked_by_issue_counts([1412])

        self.assertEqual(counts, {1412: forge_metadata.PRIORITY_BLOCKING_LIBRARY_THRESHOLD})
        gh_json.assert_called_once()
        self.assertEqual(gh_json.call_args.kwargs, {"quiet": True})
        self.assertIn("blocking(first: 100", gh_json.call_args.args[-1])
        self.assertNotIn("blockedBy(first:", gh_json.call_args.args[-1])

    def test_mark_issues_blocking_many_libraries_adds_priority_label_to_payload(self) -> None:
        priority_issue = _search_issue(1412)
        regular_issue = _search_issue(1413)

        with patch.object(
                forge_metadata,
                "get_open_issues_blocked_by_issue_counts",
                return_value={1412: forge_metadata.PRIORITY_BLOCKING_LIBRARY_THRESHOLD, 1413: 9},
        ), \
                patch.object(forge_metadata, "add_issue_label") as add_issue_label, \
                patch("sys.stdout", new_callable=io.StringIO):
            forge_metadata.mark_issues_blocking_many_libraries_as_priority([
                priority_issue,
                regular_issue,
            ])

        add_issue_label.assert_called_once_with(1412, forge_metadata.LABEL_PRIORITY)
        self.assertTrue(forge_metadata.issue_has_label(priority_issue, forge_metadata.LABEL_PRIORITY))
        self.assertFalse(forge_metadata.issue_has_label(regular_issue, forge_metadata.LABEL_PRIORITY))

    def test_prioritized_issue_fetch_marks_blocking_libraries_before_sorting(self) -> None:
        regular_issue = _search_issue(1412)
        priority_issue = _search_issue(1413)

        with patch.object(
                forge_metadata,
                "get_issues_with_label",
                return_value=[regular_issue, priority_issue],
        ), \
                patch.object(
                    forge_metadata,
                    "get_open_issues_blocked_by_issue_counts",
                    return_value={
                        1412: 0,
                        1413: forge_metadata.PRIORITY_BLOCKING_LIBRARY_THRESHOLD,
                    },
                ), \
                patch.object(forge_metadata, "add_issue_label"), \
                patch("sys.stdout", new_callable=io.StringIO):
            issues, priority_offset, regular_offset, priority_exhausted, exhausted = (
                forge_metadata.get_prioritized_issues_with_label(
                    forge_metadata.LABEL_LIBRARY_NEW,
                    2,
                )
            )

        self.assertEqual([issue["number"] for issue in issues], [1413, 1412])
        self.assertEqual(priority_offset, 0)
        self.assertEqual(regular_offset, 2)
        self.assertTrue(priority_exhausted)
        self.assertFalse(exhausted)

    def test_refresh_issue_payload_for_claim_skips_closed_issue(self) -> None:
        issue = _search_issue(1412, [forge_metadata.LABEL_LIBRARY_NEW])
        fresh_issue = {
            **_search_issue(1412, [forge_metadata.LABEL_LIBRARY_NEW]),
            "state": "CLOSED",
        }

        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(forge_metadata, "get_issue_claim_locks_root", return_value=lock_root), \
                    patch.object(forge_metadata, "get_issue_claim_payload", return_value=fresh_issue):
                self.assertFalse(
                    forge_metadata.refresh_issue_payload_for_claim(
                        issue,
                        forge_metadata.LABEL_LIBRARY_NEW,
                    )
                )
                cache = forge_metadata.read_issue_claim_cache()

        self.assertEqual(issue["state"], "CLOSED")
        self.assertEqual(cache[1412].reason, forge_metadata.ISSUE_CLAIM_CACHE_REASON_CLOSED)

    def test_refresh_issue_payload_for_claim_skips_human_intervention_label(self) -> None:
        issue = _search_issue(1412, [forge_metadata.LABEL_LIBRARY_NEW])
        fresh_issue = {
            **_search_issue(
                1412,
                [forge_metadata.LABEL_LIBRARY_NEW, forge_metadata.LABEL_HUMAN_INTERVENTION],
            ),
            "state": "OPEN",
        }

        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(forge_metadata, "get_issue_claim_locks_root", return_value=lock_root), \
                    patch.object(forge_metadata, "get_issue_claim_payload", return_value=fresh_issue):
                self.assertFalse(
                    forge_metadata.refresh_issue_payload_for_claim(
                        issue,
                        forge_metadata.LABEL_LIBRARY_NEW,
                    )
                )
                cache = forge_metadata.read_issue_claim_cache()

        self.assertTrue(forge_metadata.issue_has_label(issue, forge_metadata.LABEL_HUMAN_INTERVENTION))
        self.assertEqual(cache[1412].reason, forge_metadata.ISSUE_CLAIM_CACHE_REASON_HUMAN_INTERVENTION)

    def test_refresh_issue_payload_for_claim_skips_removed_queue_label(self) -> None:
        issue = _search_issue(1412, [forge_metadata.LABEL_LIBRARY_NEW])
        fresh_issue = {
            **_search_issue(1412, []),
            "state": "OPEN",
        }

        with patch.object(forge_metadata, "get_issue_claim_payload", return_value=fresh_issue), \
                patch("sys.stdout", new_callable=io.StringIO) as stdout:
            self.assertFalse(
                forge_metadata.refresh_issue_payload_for_claim(
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
            with patch.object(forge_metadata, "get_issue_claim_locks_root", return_value=lock_root), \
                    patch.object(forge_metadata, "get_issue_claim_payload", return_value=fresh_issue):
                self.assertFalse(
                    forge_metadata.refresh_issue_payload_for_claim(
                        issue,
                        forge_metadata.LABEL_LIBRARY_NEW,
                        "automation-user",
                    )
                )
                cache = forge_metadata.read_issue_claim_cache()

        self.assertEqual(cache[1412].reason, forge_metadata.ISSUE_CLAIM_CACHE_REASON_ASSIGNED)
        self.assertEqual(cache[1412].assignees, ("other-user",))

    def test_refresh_issue_payload_for_claim_allows_issue_assigned_to_authenticated_user(self) -> None:
        issue = _search_issue(1412, [forge_metadata.LABEL_LIBRARY_NEW])
        fresh_issue = {
            **_search_issue(1412, [forge_metadata.LABEL_LIBRARY_NEW]),
            "state": "OPEN",
            "assignees": [{"login": "automation-user"}],
        }

        with patch.object(forge_metadata, "get_issue_claim_payload", return_value=fresh_issue):
            self.assertTrue(
                forge_metadata.refresh_issue_payload_for_claim(
                    issue,
                    forge_metadata.LABEL_LIBRARY_NEW,
                    "automation-user",
                )
            )

    def test_issue_scan_batch_size_returns_candidate_batch_size(self) -> None:
        self.assertEqual(
            forge_metadata.get_issue_scan_batch_size(1, 1),
            forge_metadata.DEFAULT_ISSUE_SCAN_BATCH_SIZE,
        )
        self.assertEqual(
            forge_metadata.get_issue_scan_batch_size(5, 1),
            forge_metadata.DEFAULT_ISSUE_SCAN_BATCH_SIZE,
        )
        self.assertEqual(
            forge_metadata.get_issue_scan_batch_size(100, 4),
            forge_metadata.DEFAULT_ISSUE_SCAN_BATCH_SIZE,
        )

    def test_preflight_skips_issue_payloads_that_are_already_locally_unclaimable(self) -> None:
        human_intervention_issue = {
            "number": 1,
            "labels": [{"name": forge_metadata.LABEL_HUMAN_INTERVENTION}],
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
                forge_metadata,
                "get_issue_claim_preflights",
                return_value={
                    4: _preflight(issue_number=4, assignees=("current-user",)),
                    3: _preflight(issue_number=3),
                },
        ) as get_issue_claim_preflights:
            self.assertEqual(
                forge_metadata.get_issue_claim_preflights_or_empty(
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
            forge_metadata.should_skip_issue_from_preflight(issue, None)
        )

    def test_payload_assigned_to_authenticated_user_does_not_skip(self) -> None:
        issue = {
            "number": 1412,
            "labels": [],
            "assignees": [{"login": "automation-user"}],
        }

        self.assertFalse(
            forge_metadata.should_skip_issue_from_preflight(
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
        cached_skip = forge_metadata.CachedIssueClaimSkip(
            issue_number=1412,
            reason=forge_metadata.ISSUE_CLAIM_CACHE_REASON_IN_PROGRESS,
            observed_at_epoch=100.0,
            project_status=forge_metadata.STATUS_IN_PROGRESS,
        )

        self.assertTrue(
            forge_metadata.should_skip_issue_from_preflight(issue, None, cached_skip)
        )

    def test_cached_own_assignment_does_not_skip(self) -> None:
        issue = {
            "number": 1412,
            "labels": [],
            "assignees": [],
        }
        cached_skip = forge_metadata.CachedIssueClaimSkip(
            issue_number=1412,
            reason=forge_metadata.ISSUE_CLAIM_CACHE_REASON_ASSIGNED,
            observed_at_epoch=100.0,
            assignees=("automation-user",),
        )

        self.assertFalse(
            forge_metadata.should_skip_issue_from_preflight(
                issue,
                None,
                cached_skip,
                authenticated_user="automation-user",
            )
        )

    def test_offset_issue_fetch_uses_search_page_instead_of_expanding_limit(self) -> None:
        page_items = [_search_issue(number) for number in range(200, 300)]

        with patch.dict(os.environ, {"FORGE_ISSUE_SEARCH_CACHE": "0"}), \
                patch.object(
                        forge_metadata,
                        "gh_json",
                        return_value={"items": page_items},
                ) as gh_json:
            issues = forge_metadata.get_issues_with_label(
                forge_metadata.LABEL_LIBRARY_NEW,
                1,
                211,
            )

        self.assertEqual([issue["number"] for issue in issues], [211])
        gh_json.assert_called_once_with(
            "api", "--method", "GET", "/search/issues",
            "-f", (
                f"q=repo:{forge_metadata.REPO} is:issue is:open "
                f'label:"{forge_metadata.LABEL_LIBRARY_NEW}" -label:"{forge_metadata.LABEL_NOT_FOR_NATIVE_IMAGE}"'
            ),
            "-f", "sort=updated",
            "-f", "order=desc",
            "-F", "per_page=100",
            "-F", "page=3",
        )

    def test_random_issue_scan_offset_uses_open_issue_count(self) -> None:
        with patch.object(forge_metadata, "count_issues_with_label", return_value=500), \
                patch.object(forge_metadata.random, "randrange", return_value=123) as randrange:
            self.assertEqual(
                forge_metadata.resolve_random_issue_scan_offset(forge_metadata.LABEL_LIBRARY_NEW),
                123,
            )

        randrange.assert_called_once_with(500)

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
            with patch.object(forge_metadata, "get_issue_claim_locks_root", return_value=lock_root):
                forge_metadata.record_issue_claim_cache_observations(
                    [
                        forge_metadata.IssueClaimCacheObservation(
                            issue_number=1,
                            reason=forge_metadata.ISSUE_CLAIM_CACHE_REASON_BLOCKED,
                            open_blockers=(99,),
                        ),
                    ],
                )

            with patch.object(forge_metadata, "get_issue_claim_locks_root", return_value=lock_root), \
                    patch.object(forge_metadata, "validate_issue_processing_environment"), \
                    patch.object(
                        forge_metadata,
                        "get_prioritized_issues_with_label",
                        side_effect=[
                            ([skipped_issue], 0, 1, True, False),
                            ([claimable_issue], 0, 2, True, False),
                        ],
                    ) as get_prioritized_issues_with_label, \
                    patch.object(
                        forge_metadata,
                        "get_issue_claim_preflights_or_empty",
                    ) as get_issue_claim_preflights_or_empty, \
                    patch.object(
                        forge_metadata,
                        "claim_issue_for_processing",
                        return_value=_claimed_issue(),
                    ) as claim_issue_for_processing, \
                    patch.object(
                        forge_metadata,
                        "process_claimed_issue_lifecycle",
                        return_value=True,
                    ), \
                    patch.object(forge_metadata, "get_open_blocking_issue_numbers") as get_open_blocking_issue_numbers, \
                    patch.object(forge_metadata, "get_issue_assignees") as get_issue_assignees, \
                    patch.object(forge_metadata, "get_project_item_state") as get_project_item_state:
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
        self.assertEqual(
            get_prioritized_issues_with_label.call_args_list,
            [
                call(
                    forge_metadata.LABEL_LIBRARY_NEW,
                    forge_metadata.DEFAULT_ISSUE_SCAN_BATCH_SIZE,
                    0,
                    0,
                    False,
                ),
                call(
                    forge_metadata.LABEL_LIBRARY_NEW,
                    forge_metadata.DEFAULT_ISSUE_SCAN_BATCH_SIZE,
                    0,
                    1,
                    True,
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


class SingleIssueProcessingTests(unittest.TestCase):
    def test_append_large_library_workflow_args_passes_issue_context_for_first_run(self) -> None:
        claimed_issue = _claimed_issue()
        pipeline_argv = ["--coordinates", claimed_issue.issue_coordinates]

        with patch.dict(os.environ, {"FORGE_LARGE_LIBRARY_CHUNK_CLASS_LIMIT": "4"}, clear=True):
            forge_metadata.append_large_library_workflow_args(pipeline_argv, claimed_issue)

        self.assertEqual(
            pipeline_argv,
            [
                "--coordinates", claimed_issue.issue_coordinates,
                "--issue-number", "1412",
                "--chunk-class-limit", "4",
            ],
        )

    def test_append_large_library_workflow_args_does_not_pass_resume_artifact(self) -> None:
        claimed_issue = _claimed_issue(
            large_library_resume_artifact="/tmp/large-library-state.json",
            large_library_part=2,
        )
        pipeline_argv = ["--coordinates", claimed_issue.issue_coordinates]

        with patch.dict(os.environ, {}, clear=True):
            forge_metadata.append_large_library_workflow_args(pipeline_argv, claimed_issue)

        self.assertEqual(
            pipeline_argv,
            [
                "--coordinates", claimed_issue.issue_coordinates,
                "--issue-number", "1412",
                "--large-library-series",
            ],
        )

    def test_large_library_base_check_uses_pr_merge_commit_for_squash_merges(self) -> None:
        state = forge_metadata.LargeLibraryProgressState.create(
            coordinate="org.example:lib:1.0.0",
            issue_number=1412,
            request_label=forge_metadata.LABEL_LIBRARY_NEW,
            strategy_name="dynamic_access_main_sources_pi_gpt-5.5",
        )
        state.record_published_pr("ai/example-part-0001", "head-commit", 4242)

        with patch.object(
                forge_metadata,
                "gh",
                return_value=subprocess.CompletedProcess(
                    ["gh"],
                    0,
                    stdout=json.dumps({"mergeCommit": {"oid": "squash-merge-commit"}}),
                ),
        ), \
                patch.object(
                    forge_metadata.subprocess,
                    "run",
                    return_value=subprocess.CompletedProcess(["git"], 0),
                ) as run:
            forge_metadata.verify_large_library_base_contains_published_commit(
                state,
                "/tmp/reachability-worktree",
            )

        run.assert_called_once_with(
            ["git", "merge-base", "--is-ancestor", "squash-merge-commit", "HEAD"],
            cwd="/tmp/reachability-worktree",
            check=False,
        )

    def test_process_single_issue_claims_without_large_library_artifact_override(self) -> None:
        issue = {
            "number": 1412,
            "title": "Add support for org.example:lib:1.0.0",
            "labels": [],
            "assignees": [],
        }

        with patch.object(forge_metadata, "validate_issue_processing_environment"), \
                patch.object(
                    forge_metadata,
                    "get_issue_by_number",
                    return_value=(issue, forge_metadata.LABEL_LIBRARY_NEW),
                ), \
                patch.object(
                    forge_metadata,
                    "claim_issue_for_processing",
                    return_value=_claimed_issue(),
                ) as claim_issue_for_processing, \
                patch.object(
                    forge_metadata,
                    "process_claimed_issue_lifecycle",
                    return_value=True,
                ):
            self.assertTrue(
                forge_metadata.process_single_issue(
                    1412,
                    "/tmp/reachability",
                    "/tmp/metrics",
                    None,
                    False,
                    "automation-user",
                )
            )

        claim_issue_for_processing.assert_called_once_with(
            issue,
            forge_metadata.LABEL_LIBRARY_NEW,
            "/tmp/reachability",
            "/tmp/metrics",
            "automation-user",
        )

    def test_process_large_library_continuation_passes_explicit_artifact_to_claim(self) -> None:
        issue = _search_issue(1412, [forge_metadata.LABEL_LARGE_LIBRARY_NEXT_PART])
        with tempfile.TemporaryDirectory() as tmpdir:
            state = forge_metadata.LargeLibraryProgressState.create(
                coordinate="org.example:lib:1.0.0",
                issue_number=1412,
                request_label=forge_metadata.LABEL_LIBRARY_NEW,
                strategy_name="dynamic_access_main_sources_pi_gpt-5.5",
            )
            state.part = 2
            resume_artifact = state.default_path(tmpdir)
            state.save(resume_artifact)

            with patch.object(
                    forge_metadata,
                    "get_issue_by_number",
                    return_value=(issue, forge_metadata.LABEL_LIBRARY_NEW),
            ), \
                    patch.object(
                        forge_metadata,
                        "claim_issue_for_processing",
                        return_value=_claimed_issue(
                            large_library_resume_artifact=resume_artifact,
                            large_library_part=2,
                        ),
                    ) as claim_issue_for_processing, \
                    patch.object(
                        forge_metadata,
                        "process_claimed_issue_lifecycle",
                        return_value=True,
                    ) as process_claimed_issue_lifecycle:
                self.assertTrue(
                    forge_metadata.process_large_library_continuation(
                        resume_artifact,
                        "/tmp/reachability",
                        "/tmp/metrics",
                        None,
                        False,
                        "automation-user",
                    )
                )

        claim_issue_for_processing.assert_called_once_with(
            issue,
            forge_metadata.LABEL_LIBRARY_NEW,
            "/tmp/reachability",
            "/tmp/metrics",
            "automation-user",
            large_library_resume_artifact_override=resume_artifact,
        )
        claimed_issue = process_claimed_issue_lifecycle.call_args.args[0]
        self.assertEqual(claimed_issue.large_library_resume_artifact, resume_artifact)
        self.assertEqual(claimed_issue.large_library_part, 2)


class WorkQueueSchedulerTests(unittest.TestCase):
    def test_work_queue_configs_allow_zero_limits_from_environment(self) -> None:
        env = {
            "FORGE_JAVAC_WORK_LIMIT": "0",
            "FORGE_JAVA_RUN_WORK_LIMIT": "2",
            "FORGE_NI_RUN_WORK_LIMIT": "0",
            "FORGE_LIBRARY_UPDATE_WORK_LIMIT": "0",
            "FORGE_WORK_LIMIT": "3",
            "FORGE_STRATEGY_NAME": "custom-strategy",
            "FORGE_WORK_LABEL": forge_metadata.LABEL_LIBRARY_NEW,
        }

        with patch.dict(os.environ, env, clear=True):
            configs = forge_metadata.get_work_queue_configs_from_environment()

        self.assertEqual(
            [(config.label, config.limit, config.strategy_name, config.random_offset) for config in configs],
            [
                (forge_metadata.LABEL_JAVAC_FAIL, 0, None, False),
                (forge_metadata.LABEL_JAVA_RUN_FAIL, 2, None, False),
                (forge_metadata.LABEL_NI_RUN_FAIL, 0, None, False),
                (forge_metadata.LABEL_LIBRARY_UPDATE, 0, None, False),
                (forge_metadata.LABEL_LIBRARY_NEW, 3, "custom-strategy", False),
            ],
        )

    def test_default_review_queue_configs_include_bulk_update_reviews(self) -> None:
        env = {
            "FORGE_REVIEW_LIMIT": "2",
            "FORGE_LIBRARY_REVIEW_LIMIT": "0",
            "FORGE_BULK_UPDATE_REVIEW_LIMIT": "4",
            "FORGE_REVIEW_MODEL": "review-model",
        }

        with patch.dict(os.environ, env, clear=True):
            configs = forge_metadata.get_review_queue_configs_from_environment()

        self.assertEqual(
            [(config.label, config.limit, config.model) for config in configs],
            [
                (forge_metadata.LABEL_LIBRARY_NEW, 0, "review-model"),
                (forge_metadata.LABEL_PR_JAVAC_FIX, 2, "review-model"),
                (forge_metadata.LABEL_PR_JAVA_RUN_FIX, 2, "review-model"),
                (forge_metadata.LABEL_PR_NI_RUN_FIX, 2, "review-model"),
                (forge_metadata.LABEL_PR_LIBRARY_UPDATE, 2, "review-model"),
                (forge_metadata.LABEL_PR_LIBRARY_BULK_UPDATE, 4, "review-model"),
            ],
        )

    def test_random_work_offset_can_be_disabled_from_environment(self) -> None:
        env = {
            "FORGE_JAVAC_WORK_LIMIT": "0",
            "FORGE_JAVA_RUN_WORK_LIMIT": "0",
            "FORGE_NI_RUN_WORK_LIMIT": "0",
            "FORGE_LIBRARY_UPDATE_WORK_LIMIT": "0",
            "FORGE_WORK_LIMIT": "1",
            "FORGE_RANDOM_WORK_OFFSET": "0",
        }

        with patch.dict(os.environ, env, clear=True):
            configs = forge_metadata.get_work_queue_configs_from_environment()

        self.assertFalse(configs[-1].random_offset)

    def test_random_work_offset_can_be_enabled_from_environment(self) -> None:
        env = {
            "FORGE_JAVAC_WORK_LIMIT": "0",
            "FORGE_JAVA_RUN_WORK_LIMIT": "0",
            "FORGE_NI_RUN_WORK_LIMIT": "0",
            "FORGE_LIBRARY_UPDATE_WORK_LIMIT": "0",
            "FORGE_WORK_LIMIT": "1",
            "FORGE_RANDOM_WORK_OFFSET": "1",
        }

        with patch.dict(os.environ, env, clear=True):
            configs = forge_metadata.get_work_queue_configs_from_environment()

        self.assertTrue(configs[-1].random_offset)

    def test_random_work_offset_can_be_disabled_from_cli_override(self) -> None:
        env = {
            "FORGE_JAVAC_WORK_LIMIT": "0",
            "FORGE_JAVA_RUN_WORK_LIMIT": "0",
            "FORGE_NI_RUN_WORK_LIMIT": "0",
            "FORGE_LIBRARY_UPDATE_WORK_LIMIT": "0",
            "FORGE_WORK_LIMIT": "1",
            "FORGE_RANDOM_WORK_OFFSET": "1",
        }

        with patch.dict(os.environ, env, clear=True):
            configs = forge_metadata.get_work_queue_configs_from_environment(
                random_offset_override=False,
            )

        self.assertFalse(configs[-1].random_offset)

    def test_run_work_queues_accepts_random_offset_flags(self) -> None:
        random_args = forge_metadata.parse_args(["--run-work-queues", "--random-offset"])
        no_random_args = forge_metadata.parse_args(["--run-work-queues", "--no-random-offset"])

        self.assertTrue(random_args.random_offset)
        self.assertFalse(no_random_args.random_offset)

    def test_review_label_environment_overrides_default_review_queues(self) -> None:
        env = {
            "FORGE_REVIEW_LABEL": forge_metadata.LABEL_PR_LIBRARY_BULK_UPDATE,
            "FORGE_REVIEW_LIMIT": "3",
            "FORGE_BULK_UPDATE_REVIEW_LIMIT": "0",
            "FORGE_REVIEW_MODEL": "review-model",
        }

        with patch.dict(os.environ, env, clear=True):
            configs = forge_metadata.get_review_queue_configs_from_environment()

        self.assertEqual(
            [(config.label, config.limit, config.model) for config in configs],
            [
                (forge_metadata.LABEL_PR_LIBRARY_BULK_UPDATE, 3, "review-model"),
            ],
        )

    def test_process_work_queues_skips_zero_limit_queues(self) -> None:
        env = {
            "FORGE_JAVAC_WORK_LIMIT": "0",
            "FORGE_JAVA_RUN_WORK_LIMIT": "1",
            "FORGE_NI_RUN_WORK_LIMIT": "0",
            "FORGE_LIBRARY_UPDATE_WORK_LIMIT": "0",
            "FORGE_WORK_LIMIT": "0",
            "FORGE_REVIEW_LIMIT": "0",
            "FORGE_JAVA_RUN_STRATEGY_NAME": "java-run-strategy",
        }

        with patch.dict(os.environ, env, clear=True), \
                patch.object(forge_metadata, "require_strategy_by_name") as require_strategy_by_name, \
                patch.object(forge_metadata, "validate_issue_processing_environment") as validate_environment, \
                patch.object(forge_metadata, "process_issues_with_label", return_value=0) as process_issues, \
                patch.object(forge_metadata, "process_pull_requests_with_label") as process_reviews:
            forge_metadata.process_work_queues(
                "/tmp/reachability",
                "/tmp/metrics",
                "automation-user",
            )

        require_strategy_by_name.assert_called_once_with("java-run-strategy")
        validate_environment.assert_called_once()
        process_issues.assert_called_once_with(
            forge_metadata.LABEL_JAVA_RUN_FAIL,
            1,
            0,
            "/tmp/reachability",
            "/tmp/metrics",
            "java-run-strategy",
            False,
            "automation-user",
            forge_metadata.DEFAULT_PARALLELISM,
            environment_already_validated=True,
        )
        process_reviews.assert_not_called()

    def test_process_work_queues_uses_random_offset_for_new_library_queue(self) -> None:
        env = {
            "FORGE_JAVAC_WORK_LIMIT": "0",
            "FORGE_JAVA_RUN_WORK_LIMIT": "0",
            "FORGE_NI_RUN_WORK_LIMIT": "0",
            "FORGE_LIBRARY_UPDATE_WORK_LIMIT": "0",
            "FORGE_WORK_LIMIT": "1",
            "FORGE_REVIEW_LIMIT": "0",
            "FORGE_RANDOM_WORK_OFFSET": "1",
            "FORGE_STRATEGY_NAME": "custom-strategy",
            "FORGE_WORK_LABEL": forge_metadata.LABEL_LIBRARY_NEW,
        }

        with patch.dict(os.environ, env, clear=True), \
                patch.object(forge_metadata, "require_strategy_by_name"), \
                patch.object(forge_metadata, "validate_issue_processing_environment"), \
                patch.object(
                    forge_metadata, "resolve_random_issue_scan_offset", return_value=42
                ) as random_offset, \
                patch.object(forge_metadata, "process_issues_with_label", return_value=0) as process_issues, \
                patch.object(forge_metadata, "process_pull_requests_with_label") as process_reviews:
            forge_metadata.process_work_queues(
                "/tmp/reachability",
                "/tmp/metrics",
                "automation-user",
            )

        random_offset.assert_called_once_with(forge_metadata.LABEL_LIBRARY_NEW)
        process_issues.assert_called_once_with(
            forge_metadata.LABEL_LIBRARY_NEW,
            1,
            42,
            "/tmp/reachability",
            "/tmp/metrics",
            "custom-strategy",
            False,
            "automation-user",
            forge_metadata.DEFAULT_PARALLELISM,
            environment_already_validated=True,
        )
        process_reviews.assert_not_called()

    def test_process_work_queues_resolves_auth_for_review_only_queue(self) -> None:
        env = {
            "FORGE_JAVAC_WORK_LIMIT": "0",
            "FORGE_JAVA_RUN_WORK_LIMIT": "0",
            "FORGE_NI_RUN_WORK_LIMIT": "0",
            "FORGE_LIBRARY_UPDATE_WORK_LIMIT": "0",
            "FORGE_WORK_LIMIT": "0",
            "FORGE_REVIEW_LIMIT": "1",
            "FORGE_REVIEW_LABEL": forge_metadata.LABEL_LIBRARY_NEW,
            "FORGE_REVIEW_MODEL": "review-model",
        }

        with patch.dict(os.environ, env, clear=True), \
                patch.object(
                    forge_metadata,
                    "resolve_authenticated_user",
                    return_value="automation-user",
                ) as resolve_authenticated_user, \
                patch.object(forge_metadata, "process_pull_requests_with_label") as process_reviews:
            forge_metadata.process_work_queues(
                "/tmp/reachability",
                "/tmp/metrics",
                None,
            )

        resolve_authenticated_user.assert_called_once_with(None)
        process_reviews.assert_called_once_with(
            forge_metadata.LABEL_LIBRARY_NEW,
            1,
            "/tmp/reachability",
            "automation-user",
            "review-model",
        )

    def test_process_work_queues_skips_remaining_work_when_shutdown_requested(self) -> None:
        env = {
            "FORGE_JAVAC_WORK_LIMIT": "1",
            "FORGE_REVIEW_LIMIT": "1",
            "FORGE_REVIEW_LABEL": forge_metadata.LABEL_LIBRARY_NEW,
        }

        with patch.dict(os.environ, env, clear=True), \
                patch.object(forge_metadata, "is_shutdown_requested", return_value=True), \
                patch.object(forge_metadata, "validate_issue_processing_environment") as validate_environment, \
                patch.object(forge_metadata, "process_issues_with_label") as process_issues, \
                patch.object(forge_metadata, "process_pull_requests_with_label") as process_reviews:
            forge_metadata.process_work_queues(
                "/tmp/reachability",
                "/tmp/metrics",
                "automation-user",
            )

        validate_environment.assert_not_called()
        process_issues.assert_not_called()
        process_reviews.assert_not_called()


class IssueClaimCacheTests(unittest.TestCase):
    def test_read_cache_ignores_missing_corrupt_and_expired_cache(self) -> None:
        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(forge_metadata, "get_issue_claim_locks_root", return_value=lock_root):
                self.assertEqual(forge_metadata.read_issue_claim_cache(now=100.0), {})

                with open(forge_metadata.get_issue_claim_cache_path(), "w", encoding="utf-8") as cache_file:
                    cache_file.write("{not json")
                self.assertEqual(forge_metadata.read_issue_claim_cache(now=100.0), {})

                with open(forge_metadata.get_issue_claim_cache_path(), "w", encoding="utf-8") as cache_file:
                    json.dump(
                        {
                            "version": forge_metadata.ISSUE_CLAIM_CACHE_VERSION,
                            "repo": forge_metadata.REPO,
                            "updated_at_epoch": 0.0,
                            "entries": {
                                "1412": {
                                    "observed_at_epoch": 0.0,
                                    "reason": forge_metadata.ISSUE_CLAIM_CACHE_REASON_IN_PROGRESS,
                                    "project_status": forge_metadata.STATUS_IN_PROGRESS,
                                },
                            },
                        },
                        cache_file,
                    )

                self.assertEqual(forge_metadata.read_issue_claim_cache(now=901.0), {})

    def test_record_and_invalidate_cache_entry(self) -> None:
        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(forge_metadata, "get_issue_claim_locks_root", return_value=lock_root):
                forge_metadata.record_issue_claim_cache_observations(
                    [
                        forge_metadata.IssueClaimCacheObservation(
                            issue_number=1412,
                            reason=forge_metadata.ISSUE_CLAIM_CACHE_REASON_ASSIGNED,
                            assignees=("automation-user",),
                        ),
                    ],
                    now=100.0,
                )

                cache = forge_metadata.read_issue_claim_cache(now=100.0)
                self.assertEqual(cache[1412].reason, forge_metadata.ISSUE_CLAIM_CACHE_REASON_ASSIGNED)
                self.assertEqual(cache[1412].assignees, ("automation-user",))

                forge_metadata.invalidate_issue_claim_cache_entry(1412, now=101.0)
                self.assertEqual(forge_metadata.read_issue_claim_cache(now=101.0), {})

    def test_clear_issue_caches_removes_claim_and_search_caches(self) -> None:
        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(forge_metadata, "get_issue_claim_locks_root", return_value=lock_root):
                forge_metadata.record_issue_claim_cache_observations(
                    [
                        forge_metadata.IssueClaimCacheObservation(
                            issue_number=1412,
                            reason=forge_metadata.ISSUE_CLAIM_CACHE_REASON_BLOCKED,
                            open_blockers=(99,),
                        ),
                    ],
                    now=100.0,
                )
                forge_metadata._write_issue_search_cache_payload(
                    forge_metadata._empty_issue_search_cache_payload(100.0),
                    100.0,
                )

                self.assertTrue(os.path.exists(forge_metadata.get_issue_claim_cache_path()))
                self.assertTrue(os.path.exists(forge_metadata.get_issue_search_cache_path()))

                forge_metadata.clear_issue_caches()

                self.assertFalse(os.path.exists(forge_metadata.get_issue_claim_cache_path()))
                self.assertFalse(os.path.exists(forge_metadata.get_issue_search_cache_path()))

    def test_cached_own_assignment_is_not_returned_as_skip(self) -> None:
        issue = {
            "number": 1412,
            "title": "Add support for org.example:cached:1.0.0",
            "labels": [],
            "assignees": [],
        }

        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(forge_metadata, "get_issue_claim_locks_root", return_value=lock_root):
                forge_metadata.record_issue_claim_cache_observations(
                    [
                        forge_metadata.IssueClaimCacheObservation(
                            issue_number=1412,
                            reason=forge_metadata.ISSUE_CLAIM_CACHE_REASON_ASSIGNED,
                            assignees=("automation-user",),
                        ),
                    ],
                )

                self.assertEqual(
                    forge_metadata.get_cached_issue_claim_skips([issue], "automation-user"),
                    {},
                )
                self.assertIn(
                    1412,
                    forge_metadata.get_cached_issue_claim_skips([issue], "other-user"),
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
            with patch.object(forge_metadata, "get_issue_claim_locks_root", return_value=lock_root):
                forge_metadata.record_issue_claim_cache_observations(
                    [
                        forge_metadata.IssueClaimCacheObservation(
                            issue_number=1,
                            reason=forge_metadata.ISSUE_CLAIM_CACHE_REASON_IN_PROGRESS,
                            project_status=forge_metadata.STATUS_IN_PROGRESS,
                        ),
                    ],
                )

                with patch.object(forge_metadata, "validate_issue_processing_environment"), \
                        patch.object(
                            forge_metadata,
                            "get_prioritized_issues_with_label",
                            return_value=([cached_issue, claimable_issue], 0, 2, True, True),
                        ), \
                        patch.object(
                            forge_metadata,
                            "get_issue_claim_preflights_or_empty",
                        ) as get_issue_claim_preflights_or_empty, \
                        patch.object(
                            forge_metadata,
                            "claim_issue_for_processing",
                            return_value=_claimed_issue(),
                        ), \
                        patch.object(
                            forge_metadata,
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
            forge_metadata.record_issue_claim_cache_observations(
                [
                    forge_metadata.IssueClaimCacheObservation(
                        issue_number=1,
                        reason=forge_metadata.ISSUE_CLAIM_CACHE_REASON_BLOCKED,
                        open_blockers=(99,),
                    ),
                ],
            )
            return None

        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(forge_metadata, "get_issue_claim_locks_root", return_value=lock_root), \
                    patch.object(forge_metadata, "validate_issue_processing_environment"), \
                    patch.object(
                        forge_metadata,
                        "get_prioritized_issues_with_label",
                        side_effect=[
                            ([issue], 0, 1, True, False),
                            ([], 0, 1, True, True),
                        ],
                    ), \
                    patch.object(
                        forge_metadata,
                        "get_issue_claim_preflights_or_empty",
                    ) as get_issue_claim_preflights_or_empty, \
                    patch.object(
                        forge_metadata,
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

                cache = forge_metadata.read_issue_claim_cache()
                self.assertEqual(cache[1].reason, forge_metadata.ISSUE_CLAIM_CACHE_REASON_BLOCKED)
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
            with patch.object(forge_metadata, "get_issue_claim_locks_root", return_value=lock_root), \
                    patch.object(forge_metadata, "validate_issue_processing_environment"), \
                    patch.object(
                        forge_metadata,
                        "get_prioritized_issues_with_label",
                        return_value=(issues, 0, len(issues), True, True),
                    ), \
                    patch.object(
                        forge_metadata,
                        "get_issue_claim_preflights_or_empty",
                    ) as get_issue_claim_preflights_or_empty, \
                    patch.object(
                        forge_metadata,
                        "claim_issue_for_processing",
                        side_effect=[None, None, None, None, None, _claimed_issue()],
                    ) as claim_issue_for_processing, \
                    patch.object(
                        forge_metadata,
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

    def test_process_loop_logs_scan_start_and_progress(self) -> None:
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
            with patch.object(forge_metadata, "get_issue_claim_locks_root", return_value=lock_root), \
                    patch.object(forge_metadata, "validate_issue_processing_environment"), \
                    patch.object(
                        forge_metadata,
                        "get_prioritized_issues_with_label",
                        return_value=(issues, 0, len(issues), True, True),
                    ), \
                    patch.object(
                        forge_metadata,
                        "claim_issue_for_processing",
                        return_value=None,
                    ), \
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


class ProjectItemStatusTests(unittest.TestCase):
    def test_common_helper_fetches_project_item_and_status_with_one_graphql_call(self) -> None:
        with patch.object(
                common_git,
                "gh_json",
                return_value=_project_item_status_response(forge_metadata.STATUS_TODO),
        ) as gh_json:
            self.assertEqual(
                common_git.get_issue_project_item_status(
                    forge_metadata.REPO,
                    forge_metadata.PROJECT_NUMBER,
                    1412,
                    forge_metadata.STATUS_FIELD_NAME,
                ),
                ("project-item", forge_metadata.STATUS_TODO),
            )

        gh_json.assert_called_once()

    def test_forge_project_item_state_uses_combined_lookup(self) -> None:
        with patch.object(
                forge_metadata,
                "get_issue_project_item_status",
                return_value=("project-item", forge_metadata.STATUS_TODO),
        ) as get_issue_project_item_status, \
                patch("sys.stdout", new_callable=io.StringIO) as stdout:
            self.assertEqual(
                forge_metadata.get_project_item_state(1412),
                ("project-item", forge_metadata.STATUS_TODO),
            )

        get_issue_project_item_status.assert_called_once_with(
            forge_metadata.REPO,
            forge_metadata.PROJECT_NUMBER,
            1412,
            forge_metadata.STATUS_FIELD_NAME,
        )
        self.assertIn(
            (
                "[project-item] Issue #1412 is linked to GitHub project item project-item "
                f"in project {forge_metadata.PROJECT_NUMBER} with Status '{forge_metadata.STATUS_TODO}'"
            ),
            stdout.getvalue(),
        )


class IssueClaimLockTests(unittest.TestCase):
    def test_try_claim_issue_skips_when_local_runner_holds_issue_lock(self) -> None:
        issue = {
            "number": 1412,
            "title": "Add support for org.example:lib:1.0.0",
            "labels": [],
            "assignees": [],
        }
        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(forge_metadata, "get_issue_claim_locks_root", return_value=lock_root):
                claim_lock = forge_metadata.try_acquire_issue_claim_lock(issue["number"])
                self.assertIsNotNone(claim_lock)
                try:
                    with patch.object(
                            forge_metadata,
                            "get_open_blocking_issue_numbers",
                    ) as get_open_blocking_issues:
                        self.assertIsNone(forge_metadata.try_claim_issue(issue, "automation-user"))
                        get_open_blocking_issues.assert_not_called()
                finally:
                    claim_lock.release()

    def test_try_claim_issue_marks_open_blockers_that_block_many_libraries(self) -> None:
        issue = {
            "number": 1412,
            "title": "Add support for org.example:lib:1.0.0",
            "labels": [],
            "assignees": [],
        }
        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(forge_metadata, "get_issue_claim_locks_root", return_value=lock_root), \
                    patch.object(forge_metadata, "refresh_issue_payload_for_claim", return_value=True), \
                    patch.object(forge_metadata, "get_open_blocking_issue_numbers", return_value=[1392]), \
                    patch.object(
                        forge_metadata,
                        "try_mark_issue_numbers_blocking_many_libraries_as_priority",
                    ) as mark_priority, \
                    patch.object(forge_metadata, "get_issue_assignees") as get_issue_assignees:
                self.assertIsNone(forge_metadata.try_claim_issue(issue, "automation-user"))

        mark_priority.assert_called_once_with([1392])
        get_issue_assignees.assert_not_called()

    def test_try_claim_issue_refreshes_paused_issue_before_claim_checks(self) -> None:
        issue = _search_issue(1412, [forge_metadata.LABEL_LIBRARY_NEW])
        fresh_issue = {
            **_search_issue(
                1412,
                [forge_metadata.LABEL_LIBRARY_NEW, forge_metadata.LABEL_HUMAN_INTERVENTION],
            ),
            "state": "OPEN",
        }
        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(forge_metadata, "get_issue_claim_locks_root", return_value=lock_root), \
                    patch.object(forge_metadata, "get_issue_claim_payload", return_value=fresh_issue), \
                    patch.object(forge_metadata, "get_open_blocking_issue_numbers") as get_blockers:
                self.assertIsNone(
                    forge_metadata.try_claim_issue(
                        issue,
                        "automation-user",
                        forge_metadata.LABEL_LIBRARY_NEW,
                    )
                )
                cache = forge_metadata.read_issue_claim_cache()

        get_blockers.assert_not_called()
        self.assertEqual(cache[1412].reason, forge_metadata.ISSUE_CLAIM_CACHE_REASON_HUMAN_INTERVENTION)

    def test_try_claim_issue_refreshes_assignees_after_local_lock(self) -> None:
        issue = {
            "number": 1412,
            "title": "Add support for org.example:lib:1.0.0",
            "labels": [],
            "assignees": [],
        }
        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(forge_metadata, "get_issue_claim_locks_root", return_value=lock_root), \
                    patch.object(forge_metadata, "refresh_issue_payload_for_claim", return_value=True), \
                    patch.object(forge_metadata, "get_open_blocking_issue_numbers", return_value=[]), \
                    patch.object(forge_metadata, "get_issue_assignees", return_value=["other-user"]), \
                    patch.object(forge_metadata, "get_project_item_state") as get_project_item_state:
                self.assertIsNone(forge_metadata.try_claim_issue(issue, "automation-user"))
                get_project_item_state.assert_not_called()
                cache = forge_metadata.read_issue_claim_cache()
                self.assertEqual(cache[1412].reason, forge_metadata.ISSUE_CLAIM_CACHE_REASON_ASSIGNED)
                self.assertEqual(cache[1412].assignees, ("other-user",))

    def test_try_claim_issue_accepts_existing_authenticated_user_assignment(self) -> None:
        issue = {
            "number": 1412,
            "title": "Add support for org.example:lib:1.0.0",
            "labels": [],
            "assignees": [{"login": "automation-user"}],
        }
        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(forge_metadata, "get_issue_claim_locks_root", return_value=lock_root), \
                    patch.object(forge_metadata, "refresh_issue_payload_for_claim", return_value=True), \
                    patch.object(forge_metadata, "get_open_blocking_issue_numbers", return_value=[]), \
                    patch.object(forge_metadata, "get_issue_assignees", side_effect=[
                        ["automation-user"],
                        ["automation-user"],
                    ]), \
                    patch.object(
                        forge_metadata,
                        "get_project_item_state",
                        return_value=("project-item", forge_metadata.STATUS_TODO),
                    ), \
                    patch.object(forge_metadata, "set_issue_assignee") as set_issue_assignee, \
                    patch.object(forge_metadata, "set_item_status") as set_item_status, \
                    patch.object(forge_metadata.random, "uniform", return_value=0), \
                    patch.object(forge_metadata.time, "sleep"):
                self.assertEqual(
                    forge_metadata.try_claim_issue(issue, "automation-user"),
                    "project-item",
                )

        set_issue_assignee.assert_called_once_with(1412, "automation-user")
        set_item_status.assert_called_once_with("project-item", forge_metadata.STATUS_IN_PROGRESS)

    def test_try_claim_issue_accepts_large_library_next_part_in_progress(self) -> None:
        issue = _search_issue(1412, [forge_metadata.LABEL_LARGE_LIBRARY_NEXT_PART])
        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(forge_metadata, "get_issue_claim_locks_root", return_value=lock_root), \
                    patch.object(forge_metadata, "refresh_issue_payload_for_claim", return_value=True), \
                    patch.object(forge_metadata, "get_open_blocking_issue_numbers", return_value=[]), \
                    patch.object(forge_metadata, "get_issue_assignees", side_effect=[[], ["automation-user"]]), \
                    patch.object(
                        forge_metadata,
                        "get_project_item_state",
                        return_value=("project-item", forge_metadata.STATUS_IN_PROGRESS),
                    ), \
                    patch.object(forge_metadata, "set_issue_assignee") as set_issue_assignee, \
                    patch.object(forge_metadata, "set_item_status") as set_item_status, \
                    patch.object(forge_metadata.random, "uniform", return_value=0), \
                    patch.object(forge_metadata.time, "sleep"):
                self.assertEqual(
                    forge_metadata.try_claim_issue(issue, "automation-user"),
                    "project-item",
                )

        set_issue_assignee.assert_called_once_with(1412, "automation-user")
        set_item_status.assert_called_once_with("project-item", forge_metadata.STATUS_IN_PROGRESS)

    def test_try_claim_issue_uses_combined_project_status_lookup(self) -> None:
        issue = {
            "number": 1412,
            "title": "Add support for org.example:lib:1.0.0",
            "labels": [],
            "assignees": [],
        }
        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(forge_metadata, "get_issue_claim_locks_root", return_value=lock_root), \
                    patch.object(forge_metadata, "refresh_issue_payload_for_claim", return_value=True), \
                    patch.object(forge_metadata, "get_open_blocking_issue_numbers", return_value=[]), \
                    patch.object(forge_metadata, "get_issue_assignees", side_effect=[[], ["automation-user"]]), \
                    patch.object(
                        forge_metadata,
                        "get_project_item_state",
                        return_value=("project-item", forge_metadata.STATUS_TODO),
                    ) as get_project_item_state, \
                    patch.object(forge_metadata, "get_item_status") as get_item_status, \
                    patch.object(forge_metadata, "set_issue_assignee") as set_issue_assignee, \
                    patch.object(forge_metadata, "set_item_status") as set_item_status, \
                    patch.object(forge_metadata.random, "uniform", return_value=0), \
                    patch.object(forge_metadata.time, "sleep"):
                self.assertEqual(
                    forge_metadata.try_claim_issue(issue, "automation-user"),
                    "project-item",
                )
                cache = forge_metadata.read_issue_claim_cache()
                self.assertEqual(cache[1412].reason, forge_metadata.ISSUE_CLAIM_CACHE_REASON_IN_PROGRESS)

            get_project_item_state.assert_called_once_with(1412)
            get_item_status.assert_not_called()
            set_issue_assignee.assert_called_once_with(1412, "automation-user")
            set_item_status.assert_called_once_with("project-item", forge_metadata.STATUS_IN_PROGRESS)

    def test_revert_issue_claim_invalidates_cache_entry(self) -> None:
        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(forge_metadata, "get_issue_claim_locks_root", return_value=lock_root):
                forge_metadata.record_issue_claim_cache_observations(
                    [
                        forge_metadata.IssueClaimCacheObservation(
                            issue_number=1412,
                            reason=forge_metadata.ISSUE_CLAIM_CACHE_REASON_IN_PROGRESS,
                            project_status=forge_metadata.STATUS_IN_PROGRESS,
                        ),
                    ],
                )

                with patch.object(forge_metadata, "set_item_status") as set_item_status, \
                        patch.object(forge_metadata, "clear_issue_assignees") as clear_issue_assignees, \
                        patch.object(forge_metadata, "get_item_status", return_value=forge_metadata.STATUS_TODO), \
                        patch.object(forge_metadata, "get_issue_assignees", return_value=[]):
                    forge_metadata.revert_issue_claim("item-1", 1412, "test")

                self.assertEqual(forge_metadata.read_issue_claim_cache(), {})

        set_item_status.assert_called_once_with("item-1", forge_metadata.STATUS_TODO)
        clear_issue_assignees.assert_called_once_with(1412)

    def test_revert_issue_claim_clears_assignees_after_status_update_error(self) -> None:
        status_error = subprocess.CalledProcessError(
            1,
            ["gh", "project", "item-edit"],
            output="",
            stderr="non-200 OK status code: 502 Bad Gateway",
        )

        with patch.object(forge_metadata, "set_item_status", side_effect=status_error), \
                patch.object(forge_metadata, "clear_issue_assignees") as clear_issue_assignees, \
                patch.object(forge_metadata, "get_item_status", return_value=forge_metadata.STATUS_TODO), \
                patch.object(forge_metadata, "get_issue_assignees", return_value=[]), \
                patch("sys.stderr", new_callable=io.StringIO) as stderr:
            forge_metadata.revert_issue_claim("item-1", 1412, "test")

        clear_issue_assignees.assert_called_once_with(1412)
        self.assertIn("could not set project item", stderr.getvalue())


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
            with patch.object(forge_metadata, "get_issue_claim_locks_root", return_value=lock_root), \
                    patch.object(forge_metadata.time, "time", return_value=100.0), \
                    patch.object(forge_metadata, "fetch_issue_search_page", return_value=[issue]) as fetch_page:
                self.assertEqual(
                    forge_metadata.get_issues_with_label(forge_metadata.LABEL_LIBRARY_NEW, 1),
                    [issue],
                )
                self.assertEqual(
                    forge_metadata.get_issues_with_label(forge_metadata.LABEL_LIBRARY_NEW, 1),
                    [issue],
                )

        fetch_page.assert_called_once()

    def test_search_count_cache_is_shared_by_random_offset_resolution(self) -> None:
        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(forge_metadata, "get_issue_claim_locks_root", return_value=lock_root), \
                    patch.object(forge_metadata.time, "time", return_value=100.0), \
                    patch.object(forge_metadata, "fetch_issue_search_count", return_value=42) as fetch_count:
                self.assertEqual(forge_metadata.count_issues_with_label(forge_metadata.LABEL_LIBRARY_NEW), 42)
                self.assertEqual(forge_metadata.count_issues_with_label(forge_metadata.LABEL_LIBRARY_NEW), 42)

        fetch_count.assert_called_once()


class EnvironmentValidationTests(unittest.TestCase):
    def test_issue_processing_requires_dev_and_ci_graalvm_homes(self) -> None:
        with patch.object(forge_metadata, "require_graalvm_home_env") as require_graalvm_home:
            forge_metadata.validate_issue_processing_environment()

        require_graalvm_home.assert_has_calls([
            call(forge_metadata.DEV_GRAALVM_ENV_VAR),
            call(forge_metadata.POST_GENERATION_GRAALVM_ENV_VAR),
            call(forge_metadata.LATEST_EA_GRAALVM_ENV_VAR),
        ])


class InterruptHandlingTests(unittest.TestCase):
    def setUp(self) -> None:
        forge_metadata.clear_user_interrupt_requested()

    def tearDown(self) -> None:
        forge_metadata.clear_user_interrupt_requested()

    def test_interrupt_return_code_from_workflow_raises_keyboard_interrupt(self) -> None:
        claimed_issue = _claimed_issue()

        with patch.object(forge_metadata, "run_add_new_library_support_workflow", return_value=130), \
                patch.object(forge_metadata, "require_graalvm_home_env") as require_graalvm_home:
            with self.assertRaises(KeyboardInterrupt):
                forge_metadata.invoke_pipeline(claimed_issue, None, False)

        self.assertTrue(forge_metadata.is_user_interrupt_requested())
        require_graalvm_home.assert_not_called()

    def test_interrupted_failed_workflow_skips_human_intervention_handling(self) -> None:
        claimed_issue = _claimed_issue()

        def interrupted_run(*_args):
            forge_metadata.mark_user_interrupt_requested()
            return forge_metadata.WorkflowRunResult(
                claimed_issue=claimed_issue,
                success=False,
                started_at=123.0,
            )

        with patch.object(forge_metadata, "run_claimed_issue", side_effect=interrupted_run), \
                patch.object(forge_metadata, "handle_completed_run") as handle_completed_run, \
                patch.object(forge_metadata, "handle_failed_claimed_issue") as handle_failed_claimed_issue, \
                patch.object(forge_metadata, "revert_claimed_issue") as revert_claimed_issue, \
                patch.object(forge_metadata, "cleanup_issue_workspace") as cleanup_issue_workspace:
            with self.assertRaises(KeyboardInterrupt):
                forge_metadata.process_claimed_issue_lifecycle(
                    claimed_issue,
                    strategy_name=None,
                    keep_tests_without_dynamic_access=False,
                    canonical_metrics_repo_path="/tmp/metrics",
                )

        handle_completed_run.assert_not_called()
        handle_failed_claimed_issue.assert_not_called()
        revert_claimed_issue.assert_called_once_with(claimed_issue, "Ctrl+C interrupt")
        cleanup_issue_workspace.assert_called_once_with(claimed_issue, "/tmp/metrics")

    def test_human_intervention_posting_noops_after_interrupt(self) -> None:
        forge_metadata.mark_user_interrupt_requested()

        with patch.object(forge_metadata, "post_issue_comment") as post_issue_comment, \
                patch.object(forge_metadata, "add_issue_label") as add_issue_label:
            forge_metadata.post_human_intervention_comment_and_label(1412, "comment")

        post_issue_comment.assert_not_called()
        add_issue_label.assert_not_called()

    def test_process_issues_with_label_skips_queue_when_shutdown_requested(self) -> None:
        with patch.object(forge_metadata, "is_shutdown_requested", return_value=True), \
                patch.object(forge_metadata, "validate_issue_processing_environment") as validate_environment, \
                patch.object(forge_metadata, "resolve_authenticated_user") as resolve_authenticated_user:
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

        self.assertEqual(processed, 0)
        validate_environment.assert_not_called()
        resolve_authenticated_user.assert_not_called()


class PullRequestReviewTests(unittest.TestCase):
    def test_reconcile_approved_pr_with_failed_ci_reruns_failed_jobs_without_merging(self) -> None:
        pr = {
            "number": 3513,
            "url": "https://github.com/oracle/graalvm-reachability-metadata/pull/3513",
            "headRefOid": "abc123",
            "reviewDecision": "APPROVED",
            "mergeable": "MERGEABLE",
            "mergeStateStatus": "CLEAN",
            "statusCheckRollup": {"state": "FAILURE"},
        }

        with patch.object(forge_metadata, "get_pull_request_state", return_value=pr), \
                patch.object(
                    forge_metadata,
                    "rerun_failed_pull_request_workflow_jobs",
                    return_value=1,
                ) as rerun_failed_jobs, \
                patch.object(forge_metadata, "merge_pull_request") as merge_pull_request:
            self.assertTrue(forge_metadata.reconcile_reviewed_pull_request(3513))

        rerun_failed_jobs.assert_called_once_with(3513, "abc123")
        merge_pull_request.assert_not_called()

    def test_reconcile_unapproved_pr_with_failed_ci_does_not_rerun_failed_jobs(self) -> None:
        pr = {
            "number": 3513,
            "url": "https://github.com/oracle/graalvm-reachability-metadata/pull/3513",
            "headRefOid": "abc123",
            "reviewDecision": "REVIEW_REQUIRED",
            "mergeable": "MERGEABLE",
            "mergeStateStatus": "CLEAN",
            "statusCheckRollup": {"state": "FAILURE"},
        }

        with patch.object(forge_metadata, "get_pull_request_state", return_value=pr), \
                patch.object(
                    forge_metadata,
                    "rerun_failed_pull_request_workflow_jobs",
                ) as rerun_failed_jobs, \
                patch.object(forge_metadata, "merge_pull_request") as merge_pull_request:
            self.assertTrue(forge_metadata.reconcile_reviewed_pull_request(3513))

        rerun_failed_jobs.assert_not_called()
        merge_pull_request.assert_not_called()

    def test_rerun_failed_pull_request_workflow_jobs_reruns_failures_under_attempt_limit(self) -> None:
        workflow_runs = [
            {"id": 101, "conclusion": "failure", "run_attempt": 1},
            {"id": 102, "conclusion": "failure", "run_attempt": 2},
            {"id": 103, "conclusion": "failure", "run_attempt": 3},
            {"id": 104, "conclusion": "success", "run_attempt": 1},
            {"id": 105, "conclusion": None, "run_attempt": 1},
        ]

        with patch.object(forge_metadata, "get_pull_request_workflow_runs", return_value=workflow_runs), \
                patch.object(forge_metadata, "gh") as gh:
            self.assertEqual(
                forge_metadata.rerun_failed_pull_request_workflow_jobs(3513, "abc123"),
                2,
            )

        self.assertEqual(
            gh.call_args_list,
            [
                call(
                    "api",
                    "--method",
                    "POST",
                    f"/repos/{forge_metadata.REPO}/actions/runs/101/rerun-failed-jobs",
                ),
                call(
                    "api",
                    "--method",
                    "POST",
                    f"/repos/{forge_metadata.REPO}/actions/runs/102/rerun-failed-jobs",
                ),
            ],
        )

    def test_fetch_review_base_ref_updates_origin_master_without_pull(self) -> None:
        completed_process = subprocess.CompletedProcess(args=[], returncode=0, stdout="")

        with patch.object(forge_metadata.subprocess, "run", return_value=completed_process) as run:
            forge_metadata.fetch_review_base_ref("/repo")

        run.assert_called_once_with(
            [
                "git",
                "fetch",
                "--quiet",
                "origin",
                "+master:refs/remotes/origin/master",
            ],
            cwd="/repo",
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
        )

    def test_review_prompt_makes_github_pr_diff_authoritative(self) -> None:
        prompt = forge_metadata.build_review_prompt(3513)

        self.assertIn("gh pr diff 3513 --name-only", prompt)
        self.assertIn("gh pr diff 3513 --patch", prompt)
        self.assertIn("authoritative", prompt)
        self.assertIn("if local git output disagrees with `gh pr diff`, trust `gh pr diff`", prompt)
        self.assertIn("A fresh `origin/master` ref was fetched before checkout", prompt)


if __name__ == "__main__":
    unittest.main()
