# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import io
import os
import subprocess
import tempfile
import unittest
from unittest.mock import call, patch

import forge_metadata
from git_scripts import common_git


def _project_status(status: str) -> dict:
    return {
        "projectItems": {
            "nodes": [
                {
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
    }


def _blocker(number: int, status: str, assignees: list[str], repo: str = forge_metadata.REPO) -> dict:
    owner, repo_name = repo.split("/")
    blocker = {
        "__typename": "Issue",
        "number": number,
        "closed": False,
        "repository": {"owner": {"login": owner}, "name": repo_name},
        "assignees": {"nodes": [{"login": assignee} for assignee in assignees]},
    }
    blocker.update(_project_status(status))
    return blocker


def _blocked_by_response(blockers: list[dict]) -> dict:
    return {
        "data": {
            "repository": {
                "issue": {
                    "blockedBy": {
                        "nodes": blockers,
                        "pageInfo": {"hasNextPage": False, "endCursor": None},
                    },
                },
            },
        },
    }


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


def _claimed_issue(label: str = forge_metadata.LABEL_LIBRARY_NEW) -> forge_metadata.ClaimedIssue:
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
        in_metadata_repo=False,
        issue_coordinates="org.example:lib:1.0.0",
    )


class IssueClaimPreflightTests(unittest.TestCase):
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

    def test_non_todo_preflight_skips_issue(self) -> None:
        issue = {"number": 1412, "labels": []}
        self.assertTrue(
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

    def test_offset_issue_fetch_uses_search_page_instead_of_expanding_limit(self) -> None:
        page_items = [_search_issue(number) for number in range(200, 300)]

        with patch.object(
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
                f'label:"{forge_metadata.LABEL_LIBRARY_NEW}"'
            ),
            "-f", "sort=created",
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

    def test_process_loop_uses_preflight_to_skip_unclaimable_candidates(self) -> None:
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

        with patch.object(forge_metadata, "validate_issue_processing_environment"), \
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
                    side_effect=[
                        {1: _preflight(issue_number=1, assignees=("automation-user",))},
                        {2: _preflight(issue_number=2)},
                    ],
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
        self.assertEqual(
            get_issue_claim_preflights_or_empty.call_args_list,
            [
                call([skipped_issue]),
                call([claimable_issue]),
            ],
        )
        claim_issue_for_processing.assert_called_once_with(
            claimable_issue,
            forge_metadata.LABEL_LIBRARY_NEW,
            "/tmp/reachability",
            "/tmp/metrics",
            "automation-user",
            in_metadata_repo=True,
        )
        get_open_blocking_issue_numbers.assert_not_called()
        get_issue_assignees.assert_not_called()
        get_project_item_state.assert_not_called()


class WorkQueueSchedulerTests(unittest.TestCase):
    def test_work_queue_configs_allow_zero_limits_from_environment(self) -> None:
        env = {
            "FORGE_JAVAC_WORK_LIMIT": "0",
            "FORGE_JAVA_RUN_WORK_LIMIT": "2",
            "FORGE_NI_RUN_WORK_LIMIT": "0",
            "FORGE_WORK_LIMIT": "3",
            "FORGE_STRATEGY_NAME": "custom-strategy",
            "FORGE_WORK_LABEL": forge_metadata.LABEL_LIBRARY_NEW,
        }

        with patch.dict(os.environ, env, clear=True):
            configs = forge_metadata.get_work_queue_configs_from_environment()

        self.assertEqual(
            [(config.label, config.limit, config.strategy_name) for config in configs],
            [
                (forge_metadata.LABEL_JAVAC_FAIL, 0, None),
                (forge_metadata.LABEL_JAVA_RUN_FAIL, 2, None),
                (forge_metadata.LABEL_NI_RUN_FAIL, 0, None),
                (forge_metadata.LABEL_LIBRARY_NEW, 3, "custom-strategy"),
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
                (forge_metadata.LABEL_PR_LIBRARY_BULK_UPDATE, 4, "review-model"),
            ],
        )

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
            in_metadata_repo=True,
            environment_already_validated=True,
        )
        process_reviews.assert_not_called()

    def test_process_work_queues_uses_random_offset_for_new_library_queue(self) -> None:
        env = {
            "FORGE_JAVAC_WORK_LIMIT": "0",
            "FORGE_JAVA_RUN_WORK_LIMIT": "0",
            "FORGE_NI_RUN_WORK_LIMIT": "0",
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
            in_metadata_repo=True,
            environment_already_validated=True,
        )
        process_reviews.assert_not_called()


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
        ) as get_issue_project_item_status:
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


class ActiveSiblingBlockerTests(unittest.TestCase):
    def test_open_dependent_issue_numbers_ignore_pull_requests(self) -> None:
        response = {
            "data": {
                "repository": {
                    "issue": {
                        "blocking": {
                            "nodes": [
                                {"__typename": "PullRequest", "number": 834, "closed": False},
                                {"__typename": "Issue", "number": 1392, "closed": False},
                            ],
                            "pageInfo": {"hasNextPage": False, "endCursor": None},
                        },
                    },
                },
            },
        }

        with patch.object(forge_metadata, "gh_json", return_value=response) as gh_json:
            self.assertEqual(
                forge_metadata.get_open_dependent_issue_numbers(1391),
                [1392],
            )

        self.assertIn("__typename", gh_json.call_args.args[-1])

    def test_open_dependent_issues_preserve_repository(self) -> None:
        response = {
            "data": {
                "repository": {
                    "issue": {
                        "blocking": {
                            "nodes": [
                                {
                                    "__typename": "Issue",
                                    "number": 834,
                                    "closed": False,
                                    "repository": {
                                        "owner": {"login": "graalvm"},
                                        "name": "native-build-tools",
                                    },
                                },
                            ],
                            "pageInfo": {"hasNextPage": False, "endCursor": None},
                        },
                    },
                },
            },
        }

        with patch.object(forge_metadata, "gh_json", return_value=response):
            self.assertEqual(
                forge_metadata.get_open_dependent_issues(995),
                [forge_metadata.IssueReference("graalvm", "native-build-tools", 834)],
            )

    def test_in_progress_unassigned_sibling_is_not_active(self) -> None:
        with patch.object(
                forge_metadata,
                "get_open_dependent_issues",
                return_value=[forge_metadata.IssueReference("oracle", "graalvm-reachability-metadata", 1392)],
        ):
            with patch.object(
                forge_metadata,
                "gh_json",
                return_value=_blocked_by_response(
                    [_blocker(1462, forge_metadata.STATUS_IN_PROGRESS, [])]
                ),
            ):
                self.assertEqual(
                    forge_metadata.get_active_sibling_blocker_numbers(1391),
                    [],
                )

    def test_assigned_sibling_is_active_even_when_status_is_stale(self) -> None:
        with patch.object(
                forge_metadata,
                "get_open_dependent_issues",
                return_value=[forge_metadata.IssueReference("oracle", "graalvm-reachability-metadata", 1392)],
        ):
            with patch.object(
                forge_metadata,
                "gh_json",
                return_value=_blocked_by_response(
                    [_blocker(1462, forge_metadata.STATUS_TODO, ["automation-user"])]
                ),
            ) as gh_json:
                self.assertEqual(
                    forge_metadata.get_active_sibling_blocker_numbers(1391),
                    [1462],
                )
        self.assertNotIn("projectItems", gh_json.call_args.args[-1])

    def test_active_sibling_check_uses_dependent_issue_repository(self) -> None:
        with patch.object(
                forge_metadata,
                "get_open_dependent_issues",
                return_value=[forge_metadata.IssueReference("graalvm", "native-build-tools", 834)],
        ):
            with patch.object(
                    forge_metadata,
                    "gh_json",
                    return_value=_blocked_by_response(
                        [_blocker(1462, forge_metadata.STATUS_TODO, ["automation-user"])]
                    ),
            ) as gh_json:
                self.assertEqual(
                    forge_metadata.get_active_sibling_blocker_numbers(995),
                    [1462],
                )

        self.assertIn(
            'repository(owner: "graalvm", name: "native-build-tools")',
            gh_json.call_args.args[-1],
        )

    def test_active_sibling_check_ignores_blockers_from_other_repositories(self) -> None:
        with patch.object(
                forge_metadata,
                "get_open_dependent_issues",
                return_value=[forge_metadata.IssueReference("graalvm", "native-build-tools", 834)],
        ):
            with patch.object(
                    forge_metadata,
                    "gh_json",
                    return_value=_blocked_by_response(
                        [_blocker(
                            12,
                            forge_metadata.STATUS_TODO,
                            ["automation-user"],
                            repo="graalvm/native-build-tools",
                        )]
                    ),
            ):
                self.assertEqual(
                    forge_metadata.get_active_sibling_blocker_numbers(995),
                    [],
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

    def test_try_claim_issue_refreshes_assignees_after_local_lock(self) -> None:
        issue = {
            "number": 1412,
            "title": "Add support for org.example:lib:1.0.0",
            "labels": [],
            "assignees": [],
        }
        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(forge_metadata, "get_issue_claim_locks_root", return_value=lock_root), \
                    patch.object(forge_metadata, "get_open_blocking_issue_numbers", return_value=[]), \
                    patch.object(forge_metadata, "get_issue_assignees", return_value=["automation-user"]), \
                    patch.object(forge_metadata, "get_project_item_state") as get_project_item_state:
                self.assertIsNone(forge_metadata.try_claim_issue(issue, "automation-user"))
                get_project_item_state.assert_not_called()

    def test_try_claim_issue_uses_combined_project_status_lookup(self) -> None:
        issue = {
            "number": 1412,
            "title": "Add support for org.example:lib:1.0.0",
            "labels": [],
            "assignees": [],
        }
        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(forge_metadata, "get_issue_claim_locks_root", return_value=lock_root), \
                    patch.object(forge_metadata, "get_open_blocking_issue_numbers", return_value=[]), \
                    patch.object(forge_metadata, "get_issue_assignees", side_effect=[[], ["automation-user"]]), \
                    patch.object(
                        forge_metadata,
                        "get_project_item_state",
                        return_value=("project-item", forge_metadata.STATUS_TODO),
                    ) as get_project_item_state, \
                    patch.object(forge_metadata, "get_item_status") as get_item_status, \
                    patch.object(forge_metadata, "get_active_sibling_blocker_numbers", return_value=[]), \
                    patch.object(forge_metadata, "set_issue_assignee") as set_issue_assignee, \
                    patch.object(forge_metadata, "set_item_status") as set_item_status, \
                    patch.object(forge_metadata.random, "uniform", return_value=0), \
                    patch.object(forge_metadata.time, "sleep"):
                self.assertEqual(
                    forge_metadata.try_claim_issue(issue, "automation-user"),
                    "project-item",
                )

        get_project_item_state.assert_called_once_with(1412)
        get_item_status.assert_not_called()
        set_issue_assignee.assert_called_once_with(1412, "automation-user")
        set_item_status.assert_called_once_with("project-item", forge_metadata.STATUS_IN_PROGRESS)


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


class PullRequestReviewTests(unittest.TestCase):
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
