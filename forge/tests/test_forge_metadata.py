# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import contextlib
import dataclasses
import io
import json
import os
import subprocess
import sys
import tempfile
import unittest
from collections.abc import Callable
from unittest.mock import call, patch

import forge_metadata
from types import SimpleNamespace
from ai_workflows.agents.agent_runtime import AgentRunResult, AgentSelection
from git_scripts import common_git
from utility_scripts import host_requirements, run_location
from utility_scripts.continuation_marker import (
    PHASE_EXPLORE,
    PHASE_FINALIZATION,
    PHASE_PUBLICATION,
    PHASE_SETUP,
)
from utility_scripts.fixture_github import FixtureComment, FixtureGitHubState, FixtureIssue
from utility_scripts.dynamic_access_report import DynamicAccessClass, DynamicAccessCoverageReport
from utility_scripts.metrics_writer import PENDING_METRICS_FILENAME


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


def _search_issue(
        number: int,
        label_names: list[str] | None = None,
        author: str = "external-user",
) -> dict:
    return {
        "number": number,
        "title": f"Issue {number}",
        "user": {"login": author},
        "html_url": f"https://github.com/oracle/graalvm-reachability-metadata/issues/{number}",
        "labels": [
            {"name": label_name}
            for label_name in (label_names or [])
        ],
        "assignees": [],
    }


def _scan_state(scanned_count: int = 0, exhausted: bool = False) -> forge_metadata.IssueQueueScanState:
    """Build the scan state a `get_prioritized_issues_with_label` stub would return."""
    return forge_metadata.IssueQueueScanState(
        tier_index=len(forge_metadata.ISSUE_PRIORITY_TIERS) if exhausted else 0,
        tier_offset=0 if exhausted else scanned_count,
        scanned_count=scanned_count,
    )


def _pull_request(
        number: int,
        label_names: list[str] | None = None,
        author: str = "contributor",
) -> dict:
    return {
        "number": number,
        "title": f"Pull request {number}",
        "url": f"https://github.com/oracle/graalvm-reachability-metadata/pull/{number}",
        "author": {"login": author},
        "labels": [
            {"name": label_name}
            for label_name in (label_names or [])
        ],
    }


def _pull_request_state(number: int, ci_state: str, mergeable: str = "MERGEABLE") -> dict:
    return {
        "number": number,
        "headRefOid": f"head-{number}",
        "headRefName": f"ai/kimeta/pr-{number}",
        "isCrossRepository": False,
        "reviewDecision": "REVIEW_REQUIRED",
        "mergeable": mergeable,
        "mergeStateStatus": "CLEAN" if mergeable == "MERGEABLE" else "DIRTY",
        "statusCheckRollup": {"state": ci_state},
    }


def _local_review_attestation_check(head_sha: str) -> dict:
    return {
        "__typename": "CheckRun",
        "name": forge_metadata.LOCAL_REVIEW_ATTESTATION_CHECK_NAME,
        "status": "COMPLETED",
        "conclusion": "SUCCESS",
        "checkSuite": {
            "app": {"slug": forge_metadata.GITHUB_ACTIONS_APP_SLUG},
            "commit": {"oid": head_sha},
            "workflowRun": {
                "event": "push",
                "workflow": {"name": forge_metadata.FORGE_BRANCH_READY_WORKFLOW_NAME},
                "file": {"path": forge_metadata.FORGE_BRANCH_READY_WORKFLOW_PATH},
            },
        },
    }


def _add_local_review_attestation(state: dict, check_run: dict | None = None) -> dict:
    state["statusCheckRollup"]["contexts"] = {
        "nodes": [check_run or _local_review_attestation_check(state["headRefOid"])],
    }
    return state


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
        issue_coordinates="org.example:lib:1.0.0",
    )


def _claimed_issue_in(base_path: str, label: str = forge_metadata.LABEL_LIBRARY_NEW) -> forge_metadata.ClaimedIssue:
    """Build a claimed issue whose base checkout path exists on disk."""
    return dataclasses.replace(_claimed_issue(label), base_reachability_metadata_path=base_path)


def _dynamic_access_report(class_names: list[str]) -> DynamicAccessCoverageReport:
    return DynamicAccessCoverageReport(
        coordinate="org.example:lib:1.0.0",
        has_dynamic_access=True,
        total_calls=len(class_names),
        covered_calls=0,
        classes=[
            DynamicAccessClass(
                class_name=class_name,
                source_file=None,
                resolved_source_file=None,
                total_calls=1,
                covered_calls=0,
                call_sites=[],
            )
            for class_name in class_names
        ],
    )


class FinalizeSuccessfulIssueTests(unittest.TestCase):
    def test_restores_missing_pending_metrics_from_execution_metrics_and_marker_extras(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path:
            forge_path = os.path.join(repo_path, "forge")
            metadata_index_dir = os.path.join(repo_path, "metadata", "org.example", "lib")
            stats_dir = os.path.join(repo_path, "stats", "org.example", "lib", "1.0.0")
            os.makedirs(forge_path)
            os.makedirs(metadata_index_dir)
            os.makedirs(stats_dir)
            with open(os.path.join(metadata_index_dir, "index.json"), "w", encoding="utf-8") as index_file:
                json.dump(
                    [
                        {
                            "metadata-version": "1.0.0",
                            "tested-versions": ["1.0.0"],
                        }
                    ],
                    index_file,
                )
            run_metrics = {
                "timestamp": "2026-06-18T14:44:56.782450Z",
                "library": "org.example:lib:1.0.0",
                "status": "success",
                "metrics": {"input_tokens_used": 1},
            }
            with open(os.path.join(stats_dir, "execution-metrics.json"), "w", encoding="utf-8") as metrics_file:
                json.dump({"add_new_library_support:2026-06-18": run_metrics}, metrics_file)
            marker = forge_metadata.ContinuationMarker.create(
                strategy_name="dynamic_access_main_sources_pi_gpt-5.6-sol",
                issue_number=1412,
                label=forge_metadata.LABEL_LIBRARY_NEW,
                coordinate="org.example:lib:1.0.0",
                new_version=None,
            )
            pending_metrics = {
                **run_metrics,
                "post_generation_intervention": {"stage": "future-defaults-all"},
                "local_ci_verification": {"status": "passed"},
            }
            marker.record_publication_metrics(pending_metrics, forge_metadata.PUBLICATION_METRICS_EXTRA_KEYS)
            marker.save(forge_metadata.continuation_marker_path(repo_path))

            claimed_issue = forge_metadata.ClaimedIssue(
                issue={"number": 1412},
                label=forge_metadata.LABEL_LIBRARY_NEW,
                item_id="project-item",
                base_reachability_metadata_path=repo_path,
                worktree_path=repo_path,
                scratch_metrics_repo_path=forge_path,
                issue_coordinates="org.example:lib:1.0.0",
            )

            forge_metadata.restore_pending_run_metrics_from_execution_metrics(claimed_issue)

            with open(os.path.join(forge_path, PENDING_METRICS_FILENAME), "r", encoding="utf-8") as pending_file:
                self.assertEqual(json.load(pending_file), pending_metrics)

    def test_records_existing_pending_metrics_in_continuation_marker(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path:
            forge_path = os.path.join(repo_path, "forge")
            os.makedirs(forge_path)
            run_metrics = {
                "timestamp": "2026-06-18T14:44:56.782450Z",
                "library": "org.example:lib:1.0.0",
                "status": "success",
                "metrics": {"input_tokens_used": 1},
                "post_generation_intervention": {"stage": "current-defaults"},
                "library_update_alias_split": {"follow_up_issue": 1413},
            }
            with open(os.path.join(forge_path, PENDING_METRICS_FILENAME), "w", encoding="utf-8") as pending_file:
                json.dump(run_metrics, pending_file)
            marker = forge_metadata.ContinuationMarker.create(
                strategy_name="dynamic_access_main_sources_pi_gpt-5.6-sol",
                issue_number=1412,
                label=forge_metadata.LABEL_LIBRARY_NEW,
                coordinate="org.example:lib:1.0.0",
                new_version=None,
            )
            marker.save(forge_metadata.continuation_marker_path(repo_path))

            claimed_issue = forge_metadata.ClaimedIssue(
                issue={"number": 1412},
                label=forge_metadata.LABEL_LIBRARY_NEW,
                item_id="project-item",
                base_reachability_metadata_path=repo_path,
                worktree_path=repo_path,
                scratch_metrics_repo_path=forge_path,
                issue_coordinates="org.example:lib:1.0.0",
            )

            forge_metadata.restore_pending_run_metrics_from_execution_metrics(claimed_issue)

            saved_marker = forge_metadata.load_continuation_marker(forge_metadata.continuation_marker_path(repo_path))
            self.assertIsNotNone(saved_marker)
            self.assertEqual(
                saved_marker.publication_metrics,
                {
                    "library": "org.example:lib:1.0.0",
                    "timestamp": "2026-06-18T14:44:56.782450Z",
                    "extras": {
                        "post_generation_intervention": {"stage": "current-defaults"},
                        "library_update_alias_split": {"follow_up_issue": 1413},
                    },
                },
            )

    def test_not_for_native_image_pr_receives_metrics_repo_path_for_local_ci(self) -> None:
        claimed_issue = _claimed_issue()

        with patch.object(forge_metadata, "find_dynamic_access_exhaust_report_path", return_value=None), \
                patch.object(forge_metadata, "require_claimed_issue_worktree"), \
                patch.object(forge_metadata, "_load_pending_run_metrics", return_value={"status": "success"}), \
                patch.object(forge_metadata, "metadata_coordinate_parts", return_value=("org.example", "lib", "1.0.0")), \
                patch.object(forge_metadata, "is_not_for_native_image", return_value=True), \
                patch.object(forge_metadata, "run_publish_not_for_native_image") as make_pr:
            forge_metadata.finalize_successful_issue(claimed_issue)

        make_pr.assert_called_once_with([
            "--coordinates", "org.example:lib:1.0.0",
            "--issue-number", "1412",
            "--reachability-metadata-path", "/tmp/reachability-worktree",
            "--metrics-repo-path", "/tmp/metrics-worktree",
        ])


class LibraryUpdateIssueTests(unittest.TestCase):
    def test_library_preflight_dispatches_without_a_strategy(self) -> None:
        claimed_issue = forge_metadata.ClaimedIssue(
            issue={"number": 1412, "title": "Update org.example:lib:1.0.0"},
            label=forge_metadata.LABEL_LIBRARY_UPDATE,
            item_id="item-1",
            base_reachability_metadata_path="/tmp/reachability",
            worktree_path="/tmp/reachability-worktree",
            scratch_metrics_repo_path="/tmp/metrics-worktree",
            issue_coordinates="org.example:lib:1.0.0",
            preflight_info_path="/tmp/preflight-info",
        )

        with patch.object(
                forge_metadata,
                "run_preflight_decision",
                return_value="/tmp/preflight-info/.library_preparation_preflight.json",
        ) as preflight:
            forge_metadata.run_library_preparation_preflight(claimed_issue)

        # The setup role owns the backend and model; no bundle is consulted.
        self.assertEqual(
            set(preflight.call_args.kwargs),
            {"claimed_issue", "issue_body_provider"},
        )

    def test_library_preflight_runs_on_the_setup_role(self) -> None:
        """Preflight prepares a library, so FORGE_SETUP_* selects it."""
        from utility_scripts import library_preparation_preflight as preflight_module

        claimed_issue = SimpleNamespace(
            issue={"number": 1412, "title": "Update org.example:lib:1.0.0"},
            issue_coordinates="org.example:lib:1.0.0",
            worktree_path="/tmp/reachability-worktree",
            preflight_info_path="/tmp/preflight-info",
            current_coordinates=None,
            new_version=None,
            label="library-update-request",
        )
        with patch.object(
                preflight_module,
                "setup_agent_run",
                return_value=AgentRunResult(
                    0, "/tmp/preflight.log", False,
                    '{"action":"no_action","summary":"nothing needed"}',
                    input_tokens=11, output_tokens=7,
                ),
        ) as setup, patch.object(
                preflight_module, "get_setup_agent",
                return_value=AgentSelection(backend="pi", model="cheap-model"),
        ), patch.object(
                preflight_module, "build_library_preflight_input_bundle",
                return_value={"library": "org.example:lib:1.0.0"},
        ), patch.object(
                preflight_module, "_write_text_artifact", return_value="/tmp/a.txt",
        ), patch.object(
                preflight_module, "_write_and_log_preflight", side_effect=lambda _i, record: record,
        ):
            record = preflight_module.run_library_preparation_preflight(
                claimed_issue=claimed_issue,
                issue_body_provider=lambda _n: "",
            )

        setup.assert_called_once()
        self.assertEqual(setup.call_args.kwargs["task_type"], "library-preparation-preflight")
        # The record names the model that ran, not a bundle's claim about it.
        self.assertEqual(record["model"], "cheap-model")
        self.assertEqual(record["input_tokens_used"], 11)
        self.assertEqual(record["output_tokens_used"], 7)

    def test_issue_lookup_does_not_request_body_for_generic_claiming(self) -> None:
        issue_payload = {
            "number": 1412,
            "title": "Update support for org.example:lib:1.0.0",
            "labels": [{"name": forge_metadata.LABEL_LIBRARY_UPDATE}],
            "assignees": [],
        }

        with patch.object(forge_metadata, "gh_json", return_value=issue_payload) as gh_json:
            issue, label = forge_metadata.get_issue_by_number(1412)

        self.assertEqual(label, forge_metadata.LABEL_LIBRARY_UPDATE)
        self.assertNotIn("body", issue)
        self.assertNotIn("body", gh_json.call_args.args[-1])

    def test_claim_payload_does_not_request_body_for_generic_claiming(self) -> None:
        issue_payload = {
            "number": 1412,
            "title": "Update support for org.example:lib:1.0.0",
            "state": "OPEN",
            "labels": [{"name": forge_metadata.LABEL_LIBRARY_UPDATE}],
            "assignees": [],
        }

        with patch.object(forge_metadata, "gh_json", return_value=issue_payload) as gh_json:
            issue = forge_metadata.get_issue_claim_payload(1412)

        self.assertNotIn("body", issue)
        self.assertNotIn("body", gh_json.call_args.args[-1])

    def test_issue_body_fetch_is_explicit_for_reporter_metadata_context(self) -> None:
        with patch.object(forge_metadata, "gh_json", return_value={"body": "Missing reflection metadata"}) as gh_json:
            body = forge_metadata.get_issue_body(1412)

        self.assertEqual(body, "Missing reflection metadata")
        self.assertEqual(gh_json.call_args.args[-1], "body")

    def test_library_update_uses_title_coordinate_when_body_mentions_other_coordinates(self) -> None:
        issue = {
            "number": 1412,
            "title": "Update support for org.example:title-lib:1.2.3",
            "body": (
                "The failure also mentions org.other:body-lib:9.9.9 and "
                "com.acme:context:4.5.6 in the stack trace."
            ),
        }

        claim_metadata = forge_metadata.build_claim_metadata(
            issue,
            forge_metadata.LABEL_LIBRARY_UPDATE,
            "/tmp/reachability",
        )

        self.assertEqual(claim_metadata, ("org.example:title-lib:1.2.3", None, None))

    def test_direct_repair_uses_latest_entry_as_failure_baseline(self) -> None:
        with tempfile.TemporaryDirectory() as repo:
            group = "io.netty"
            artifact = "netty-common"
            index_dir = os.path.join(repo, "metadata", group, artifact)
            os.makedirs(index_dir, exist_ok=True)
            with open(os.path.join(index_dir, "index.json"), "w", encoding="utf-8") as index_file:
                json.dump([
                    {
                        "metadata-version": "4.1.115.Final",
                        "tested-versions": ["4.1.115.Final", "4.1.130.Final"],
                    },
                    {
                        "latest": True,
                        "metadata-version": "5.0.0.Alpha1",
                        "tested-versions": ["5.0.0.Alpha1"],
                    },
                ], index_file)
            for version in ["4.1.115.Final", "5.0.0.Alpha1"]:
                os.makedirs(os.path.join(repo, "metadata", group, artifact, version), exist_ok=True)
                os.makedirs(os.path.join(repo, "tests", "src", group, artifact, version), exist_ok=True)
            issue = {
                "number": 9408,
                "title": "Fails native image run io.netty:netty-common:4.1.132.Final",
            }

            claim_metadata = forge_metadata.build_claim_metadata(
                issue,
                forge_metadata.LABEL_NI_RUN_FAIL,
                repo,
            )

            self.assertEqual(
                claim_metadata,
                (
                    "io.netty:netty-common:4.1.132.Final",
                    "io.netty:netty-common:5.0.0.Alpha1",
                    "4.1.132.Final",
                ),
            )

    def test_extract_issue_requested_metadata_context_keeps_full_issue_body(self) -> None:
        body = """
        The reporter may describe the missing metadata in arbitrary prose.

        Related coordinate: org.other:body-lib:9.9.9

        ```json
        {"reflection":[{"type":"org.example.Missing"}]}
        ```

        native-image reports missing resource file config/app.properties.
        """

        context = forge_metadata.extract_issue_requested_metadata_context(body)

        self.assertIn("arbitrary prose", context)
        self.assertIn("org.example.Missing", context)
        self.assertIn("missing resource file config/app.properties", context)
        self.assertIn("Related coordinate", context)

    def test_library_update_passes_issue_requested_metadata_context_to_workflow(self) -> None:
        claimed_issue = _claimed_issue(label=forge_metadata.LABEL_LIBRARY_UPDATE)

        with patch.object(forge_metadata, "require_claimed_issue_worktree"), \
                patch.object(forge_metadata, "run_library_preparation_preflight", return_value=None), \
                patch.object(forge_metadata, "prepare_dynamic_access_chunking", return_value=None), \
                patch.object(
                    forge_metadata,
                    "get_issue_body",
                    return_value=(
                        "Caused by: org.graalvm.nativeimage.MissingReflectionRegistrationError: "
                        "Cannot reflectively invoke method 'public void org.example.Demo.setName(java.lang.String)'."
                    ),
                ) as issue_body, \
                patch.object(
                    forge_metadata,
                    "select_library_update_route",
                    return_value=forge_metadata.LibraryUpdateRoute(
                        selected_driver=forge_metadata.ROUTE_IMPROVE_COVERAGE,
                        baseline_coordinates=None,
                        new_version="1.0.0",
                    ),
                ), \
                patch.object(forge_metadata, "run_improve_library_coverage_workflow", return_value=0) as workflow:
            self.assertTrue(forge_metadata.invoke_pipeline(claimed_issue, "library_update_pi_gpt-5.6-sol", False))

        issue_body.assert_called_once_with(1412)
        workflow.assert_called_once()
        argv = workflow.call_args.args[0]
        self.assertIn("--issue-requested-metadata-context", argv)
        context = argv[argv.index("--issue-requested-metadata-context") + 1]
        self.assertIn("org.example.Demo.setName", context)

    def test_library_new_does_not_read_issue_body_or_pass_reporter_context(self) -> None:
        claimed_issue = _claimed_issue(label=forge_metadata.LABEL_LIBRARY_NEW)

        with patch.object(forge_metadata, "require_claimed_issue_worktree"), \
                patch.object(forge_metadata, "run_library_preparation_preflight", return_value=None), \
                patch.object(forge_metadata, "prepare_dynamic_access_chunking", return_value=None), \
                patch.object(forge_metadata, "get_issue_body") as issue_body, \
                patch.object(forge_metadata, "run_add_new_library_support_workflow", return_value=0) as workflow:
            self.assertTrue(forge_metadata.invoke_pipeline(claimed_issue, "basic_iterative_pi_gpt-5.4", False))

        issue_body.assert_not_called()
        workflow.assert_called_once()
        argv = workflow.call_args.args[0]
        self.assertNotIn("--issue-requested-metadata-context", argv)


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
                forge_metadata.subprocess,
                "run",
                side_effect=[failed_process, successful_process],
        ) as run, \
                patch.object(forge_metadata.time, "sleep") as sleep, \
                patch("sys.stderr", new_callable=io.StringIO) as stderr:
            forge_metadata.gh("issue", "edit", "2099", "--add-label", "human-intervention")

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
                forge_metadata.subprocess,
                "run",
                side_effect=[failed_process, successful_process],
        ) as run, \
                patch.object(forge_metadata.time, "sleep") as sleep, \
                patch("sys.stderr", new_callable=io.StringIO):
            result = forge_metadata.gh("api", "/repos/example/repo/labels/demo", check=False)

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

    def test_chunked_dynamic_access_preflight_skips_in_progress_issue(self) -> None:
        issue = _search_issue(1412, [forge_metadata.LABEL_CHUNKED_DYNAMIC_ACCESS])
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
                take_blocked_issues=False,
            )
        )

    def test_open_blocker_preflight_allows_issue_when_override_is_enabled(self) -> None:
        issue = {"number": 1412, "labels": []}
        self.assertFalse(
            forge_metadata.should_skip_issue_from_preflight(
                issue,
                _preflight(open_blockers=(1392,)),
                take_blocked_issues=True,
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

    def test_prioritized_issue_fetch_drains_each_tier_before_the_next(self) -> None:
        tier_issues = {
            (forge_metadata.LABEL_HIGH_PRIORITY,): [
                _search_issue(1414, [forge_metadata.LABEL_HIGH_PRIORITY]),
            ],
            (forge_metadata.LABEL_PRIORITY,): [
                _search_issue(1413, [forge_metadata.LABEL_PRIORITY]),
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
                forge_metadata,
                "get_issues_with_label",
                side_effect=fake_get_issues,
        ) as get_issues:
            for _ in range(4):
                issues, scan_state = forge_metadata.get_prioritized_issues_with_label(
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
                (0, [forge_metadata.LABEL_HIGH_PRIORITY], []),
                (1, [forge_metadata.LABEL_HIGH_PRIORITY], []),
                (0, [forge_metadata.LABEL_PRIORITY], [forge_metadata.LABEL_HIGH_PRIORITY]),
                (1, [forge_metadata.LABEL_PRIORITY], [forge_metadata.LABEL_HIGH_PRIORITY]),
                (0, [], [forge_metadata.LABEL_HIGH_PRIORITY, forge_metadata.LABEL_PRIORITY]),
                (1, [], [forge_metadata.LABEL_HIGH_PRIORITY, forge_metadata.LABEL_PRIORITY]),
            ],
        )

    def test_tier_search_query_keeps_the_not_for_native_image_exclusion(self) -> None:
        with patch.dict(os.environ, {"FORGE_ISSUE_SEARCH_CACHE": "0"}), \
                patch.object(forge_metadata, "gh_json", return_value={"items": []}) as gh_json:
            forge_metadata.search_issues_with_label(
                forge_metadata.LABEL_LIBRARY_NEW,
                25,
                excluded_labels=[forge_metadata.LABEL_HIGH_PRIORITY],
            )

        self.assertEqual(
            gh_json.call_args.args[5],
            (
                f"q=repo:{forge_metadata.REPO} is:issue is:open "
                f'label:"{forge_metadata.LABEL_LIBRARY_NEW}" '
                f'-label:"{forge_metadata.LABEL_NOT_FOR_NATIVE_IMAGE}" '
                f'-label:"{forge_metadata.LABEL_HIGH_PRIORITY}"'
            ),
        )

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
                patch.object(forge_metadata, "gh_json", return_value={"items": page_items}) as gh_json:
            issues = forge_metadata.get_issues_with_label(
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
                f"q=repo:{forge_metadata.REPO} is:issue is:open "
                f'label:"{forge_metadata.LABEL_LIBRARY_NEW}" -label:"{forge_metadata.LABEL_NOT_FOR_NATIVE_IMAGE}"'
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

        filtered = forge_metadata.filter_user_requested_issues(issues, user_requested_only=True)

        self.assertEqual([issue["number"] for issue in filtered], [1])

    def test_prioritized_issue_fetch_filters_authors_but_advances_raw_offset(self) -> None:
        issues = [
            {"number": 1, "author": {"login": "graalvmbot"}, "labels": []},
            {"number": 2, "author": {"login": "external-user"}, "labels": []},
        ]

        with patch.object(forge_metadata, "get_issues_with_label", return_value=issues) as get_issues:
            filtered, scan_state = forge_metadata.get_prioritized_issues_with_label(
                forge_metadata.LABEL_LIBRARY_NEW,
                25,
                user_requested_only=True,
            )

        get_issues.assert_called_once_with(
            forge_metadata.LABEL_LIBRARY_NEW,
            25,
            0,
            [forge_metadata.LABEL_HIGH_PRIORITY],
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

        with patch.object(forge_metadata, "get_issues_with_label", side_effect=batches) as get_issues:
            filtered, scan_state = forge_metadata.get_prioritized_issues_with_label(
                forge_metadata.LABEL_LIBRARY_NEW,
                25,
                user_requested_only=True,
            )

        self.assertEqual([issue["number"] for issue in filtered], [2])
        self.assertEqual(get_issues.call_count, 2)
        self.assertEqual(scan_state.scanned_count, 2)
        self.assertFalse(scan_state.exhausted)

    def test_random_issue_scan_offset_uses_open_issue_count(self) -> None:
        with patch.object(forge_metadata, "count_issues_with_label", return_value=500), \
                patch.object(forge_metadata.random, "randrange", return_value=123) as randrange:
            self.assertEqual(
                forge_metadata.resolve_random_issue_scan_offset(forge_metadata.LABEL_LIBRARY_NEW),
                123,
            )

        randrange.assert_called_once_with(500)

    def test_random_issue_scan_offset_uses_user_requested_count(self) -> None:
        with patch.object(forge_metadata, "count_issues_with_label", return_value=500) as count_issues, \
                patch.object(forge_metadata.random, "randrange", return_value=123):
            forge_metadata.resolve_random_issue_scan_offset(
                forge_metadata.LABEL_LIBRARY_NEW,
                user_requested_only=True,
            )

        count_issues.assert_called_once_with(
            forge_metadata.LABEL_LIBRARY_NEW,
            user_requested_only=True,
        )

    def test_random_issue_scan_offset_counts_only_selected_priority_tier(self) -> None:
        with patch.object(forge_metadata, "count_issues_with_label", return_value=50) as count_issues, \
                patch.object(forge_metadata.random, "randrange", return_value=12):
            offset = forge_metadata.resolve_random_issue_scan_offset(
                forge_metadata.LABEL_LIBRARY_NEW,
                priority=forge_metadata.PRIORITY_NORMAL,
            )

        self.assertEqual(offset, 12)
        count_issues.assert_called_once_with(
            forge_metadata.LABEL_LIBRARY_NEW,
            [],
            False,
            [forge_metadata.LABEL_HIGH_PRIORITY, forge_metadata.LABEL_PRIORITY],
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
                project_status=forge_metadata.STATUS_TODO,
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
                project_status=forge_metadata.STATUS_TODO,
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
            excluded_authors=forge_metadata.NON_USER_REQUESTED_ISSUE_AUTHORS,
        )

        self.assertEqual([issue["number"] for issue in issues], [1])

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
                            ([skipped_issue], _scan_state(1)),
                            ([claimable_issue], _scan_state(2)),
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
                    take_blocked_issues=False,
                )

        self.assertEqual(processed, 1)
        self.assertEqual(
            get_prioritized_issues_with_label.call_args_list,
            [
                call(
                    forge_metadata.LABEL_LIBRARY_NEW,
                    forge_metadata.DEFAULT_ISSUE_SCAN_BATCH_SIZE,
                    forge_metadata.IssueQueueScanState(),
                    False,
                ),
                call(
                    forge_metadata.LABEL_LIBRARY_NEW,
                    forge_metadata.DEFAULT_ISSUE_SCAN_BATCH_SIZE,
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


def _form_issue(
        number: int = 4242,
        title: str = "Add support for org.example:widget:1.2.3",
        label_names: list[str] | None = None,
        assignees: list[str] | None = None,
) -> dict:
    return {
        "number": number,
        "title": title,
        "state": "OPEN",
        "url": f"https://github.com/example/repo/issues/{number}",
        "labels": [
            {"name": name}
            for name in (label_names if label_names is not None else [forge_metadata.LABEL_LIBRARY_NEW])
        ],
        "assignees": [{"login": login} for login in (assignees or [])],
    }


def _write_index(repo_path: str, group: str, artifact: str, entries: list[dict]) -> None:
    index_dir = os.path.join(repo_path, "metadata", group, artifact)
    os.makedirs(index_dir, exist_ok=True)
    with open(os.path.join(index_dir, "index.json"), "w", encoding="utf-8") as index_file:
        json.dump(entries, index_file)


def _fixture_form_issue(
        number: int = 4242,
        title: str = "Add support for org.example:widget:1.2.3",
        label_names: list[str] | None = None,
        assignees: list[str] | None = None,
        project_status: str = forge_metadata.STATUS_TODO,
        comments: list | None = None,
) -> FixtureIssue:
    return FixtureIssue(
        number=number,
        title=title,
        author="external-user",
        body="",
        state="OPEN",
        labels=list(label_names if label_names is not None else [forge_metadata.LABEL_LIBRARY_NEW]),
        assignees=list(assignees or []),
        project_number=forge_metadata.PROJECT_NUMBER,
        project_item_id=f"item-{number}",
        project_status=project_status,
        blockers=[],
        comments=list(comments or []),
        continuation_marker=None,
        worktree_files={},
        fixture_path="/tmp/fixture.yaml",
        url=f"fixture://issue/{number}",
    )


class IssueFormGateTests(unittest.TestCase):
    """The claim-held issue-form gate. §FS-forge-run-requirements.3"""

    def test_well_formed_issue_is_accepted(self) -> None:
        with patch.object(forge_metadata, "artifact_is_published", return_value=True):
            verdict = forge_metadata.check_issue_form(
                _form_issue(),
                forge_metadata.LABEL_LIBRARY_NEW,
                "/nonexistent",
            )

        self.assertTrue(verdict.accepted)
        self.assertIsNone(verdict.rejection)

    def test_two_workflow_labels_are_rejected_naming_both(self) -> None:
        issue = _form_issue(
            label_names=[
                forge_metadata.LABEL_LIBRARY_NEW,
                forge_metadata.LABEL_JAVAC_FAIL,
                forge_metadata.LABEL_PRIORITY,
            ],
        )

        with patch.object(forge_metadata, "artifact_is_published") as is_published:
            verdict = forge_metadata.check_issue_form(
                issue,
                forge_metadata.LABEL_LIBRARY_NEW,
                "/nonexistent",
            )

        self.assertEqual(verdict.rejection.rule, forge_metadata.ISSUE_FORM_RULE_SINGLE_WORKFLOW_LABEL)
        self.assertEqual(
            verdict.rejection.offending_value,
            f"{forge_metadata.LABEL_JAVAC_FAIL}, {forge_metadata.LABEL_LIBRARY_NEW}",
        )
        # A rule decidable from the payload never reaches for the network.
        is_published.assert_not_called()

    def test_title_without_coordinates_is_rejected_quoting_the_title(self) -> None:
        issue = _form_issue(title="Please add support for Widget")

        with patch.object(forge_metadata, "artifact_is_published") as is_published:
            verdict = forge_metadata.check_issue_form(
                issue,
                forge_metadata.LABEL_LIBRARY_NEW,
                "/nonexistent",
            )

        self.assertEqual(verdict.rejection.rule, forge_metadata.ISSUE_FORM_RULE_MAVEN_COORDINATES)
        self.assertEqual(verdict.rejection.offending_value, "Please add support for Widget")
        is_published.assert_not_called()

    def test_failure_issue_without_latest_entry_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path:
            _write_index(repo_path, "org.example", "widget", [{"metadata-version": "1.0.0"}])

            with (
                    patch.object(forge_metadata, "artifact_is_published") as is_published,
                    patch("sys.stderr", new_callable=io.StringIO) as stderr,
            ):
                verdict = forge_metadata.check_issue_form(
                    _form_issue(
                        title="Fix javac failure for org.example:widget:1.2.3",
                        label_names=[forge_metadata.LABEL_JAVAC_FAIL],
                    ),
                    forge_metadata.LABEL_JAVAC_FAIL,
                    repo_path,
                )

        self.assertEqual(verdict.rejection.rule, forge_metadata.ISSUE_FORM_RULE_CURRENT_LATEST_VERSION)
        self.assertEqual(verdict.rejection.offending_value, "org.example:widget")
        is_published.assert_not_called()
        self.assertEqual("", stderr.getvalue())

    def test_failure_issue_at_or_below_latest_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path:
            _write_index(
                repo_path,
                "org.example",
                "widget",
                [{"metadata-version": "2.0.0", "latest": True}],
            )

            with patch.object(forge_metadata, "artifact_is_published") as is_published:
                verdict = forge_metadata.check_issue_form(
                    _form_issue(
                        title="Fix javac failure for org.example:widget:1.9.0",
                        label_names=[forge_metadata.LABEL_JAVAC_FAIL],
                    ),
                    forge_metadata.LABEL_JAVAC_FAIL,
                    repo_path,
                )

        self.assertEqual(verdict.rejection.rule, forge_metadata.ISSUE_FORM_RULE_NEWER_THAN_LATEST)
        self.assertEqual(verdict.rejection.offending_value, "1.9.0")
        self.assertIn("2.0.0", verdict.rejection.requirement)
        is_published.assert_not_called()

    def test_failure_issue_above_latest_passes_the_version_rule(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path:
            _write_index(
                repo_path,
                "org.example",
                "widget",
                [{"metadata-version": "2.0.0", "latest": True}],
            )

            with patch.object(forge_metadata, "artifact_is_published", return_value=True):
                verdict = forge_metadata.check_issue_form(
                    _form_issue(
                        title="Fix javac failure for org.example:widget:2.1.0",
                        label_names=[forge_metadata.LABEL_JAVAC_FAIL],
                    ),
                    forge_metadata.LABEL_JAVAC_FAIL,
                    repo_path,
                )

        self.assertTrue(verdict.accepted)

    def test_unpublished_coordinate_is_rejected_naming_the_repositories(self) -> None:
        with patch.object(forge_metadata, "artifact_is_published", return_value=False):
            verdict = forge_metadata.check_issue_form(
                _form_issue(),
                forge_metadata.LABEL_LIBRARY_NEW,
                "/nonexistent",
            )

        self.assertEqual(verdict.rejection.rule, forge_metadata.ISSUE_FORM_RULE_PUBLISHED_ARTIFACT)
        self.assertEqual(verdict.rejection.offending_value, "org.example:widget:1.2.3")
        for repository_url in forge_metadata.ARTIFACT_REPOSITORY_URLS:
            self.assertIn(repository_url, verdict.rejection.requirement)

    def test_unreachable_repository_leaves_the_form_undecided(self) -> None:
        with patch.object(forge_metadata, "artifact_is_published", return_value=None):
            verdict = forge_metadata.check_issue_form(
                _form_issue(),
                forge_metadata.LABEL_LIBRARY_NEW,
                "/nonexistent",
            )

        self.assertIsNone(verdict.rejection)
        self.assertFalse(verdict.accepted)
        self.assertIn("org.example:widget:1.2.3", verdict.undecided_reason)


class IssueFormRejectionTests(unittest.TestCase):
    """Rejection feedback: one comment, then a closed issue. §FS-forge-run-requirements.3"""

    def setUp(self) -> None:
        self.addCleanup(setattr, forge_metadata, "fixture_github_state", None)
        self.addCleanup(forge_metadata.clear_issue_caches)

    def _reject(self, state: FixtureGitHubState, issue: dict) -> forge_metadata.IssueFormRejection:
        forge_metadata.configure_fixture_testing(fixture_state=state)
        rejection = forge_metadata.check_issue_form(
            issue,
            forge_metadata.LABEL_LIBRARY_NEW,
            "/nonexistent",
        ).rejection
        forge_metadata.reject_issue_form(issue, rejection)
        return rejection

    def test_rejection_comments_the_rule_then_closes_the_issue(self) -> None:
        state = FixtureGitHubState([_fixture_form_issue(title="Add support for Widget")])
        issue = _form_issue(title="Add support for Widget")

        with patch("sys.stdout", new_callable=io.StringIO) as stdout:
            rejection = self._reject(state, issue)

        comments = state.get_issue_comments(4242)
        self.assertEqual(len(comments), 1)
        body = comments[0]["body"]
        self.assertIn(forge_metadata.ISSUE_FORM_RULE_MAVEN_COORDINATES, body)
        self.assertIn("`Add support for Widget`", body)
        self.assertIn(rejection.requirement, body)
        self.assertEqual(state.get_issue_claim_payload(4242)["state"], "CLOSED")
        self.assertNotIn(
            forge_metadata.LABEL_HUMAN_INTERVENTION,
            state.get_issue_labels(4242),
        )
        output = stdout.getvalue()
        self.assertIn(
            "[issue-form] Rejecting issue #4242: rule 'maven-coordinates'",
            output,
        )
        self.assertIn(
            "[issue-form] Posting rejection comment to issue #4242: "
            "rule 'maven-coordinates'",
            output,
        )
        self.assertIn(
            "[issue-close] Closing issue #4242: "
            "issue-form rule 'maven-coordinates' failed",
            output,
        )

    def _reopened_state(self, title: str) -> FixtureGitHubState:
        """Fixture state for an issue reopened with its rejection comment still on it."""
        rejection = forge_metadata.IssueFormRejection(
            rule=forge_metadata.ISSUE_FORM_RULE_MAVEN_COORDINATES,
            offending_value=title,
            requirement="Name the coordinates.",
        )
        return FixtureGitHubState([
            _fixture_form_issue(
                title=title,
                comments=[FixtureComment(
                    author=forge_metadata.FIXTURE_AUTHENTICATED_USER,
                    body=forge_metadata.build_issue_form_rejection_comment(rejection),
                )],
            ),
        ])

    def test_reopened_issue_with_the_same_defect_is_closed_without_a_second_comment(self) -> None:
        state = self._reopened_state("Add support for Widget")

        with patch("sys.stdout", new_callable=io.StringIO) as stdout:
            self._reject(state, _form_issue(title="Add support for Widget"))

        self.assertEqual(len(state.get_issue_comments(4242)), 1)
        self.assertEqual(state.get_issue_claim_payload(4242)["state"], "CLOSED")
        self.assertIn(
            "[issue-form] Skipping rejection comment for issue #4242: "
            "rule 'maven-coordinates' was already reported",
            stdout.getvalue(),
        )

    def test_edited_title_is_judged_afresh_and_gets_its_own_comment(self) -> None:
        state = self._reopened_state("Add support for Widget")

        self._reject(state, _form_issue(title="Add support for Widget please"))

        self.assertEqual(len(state.get_issue_comments(4242)), 2)

    def _reject_live(self, issue: dict) -> tuple:
        rejection = forge_metadata.IssueFormRejection(
            rule=forge_metadata.ISSUE_FORM_RULE_MAVEN_COORDINATES,
            offending_value="Add support for Widget",
            requirement="Name the coordinates.",
        )
        events: list[str] = []

        def record(event: str) -> Callable[..., None]:
            return lambda *_args, **_kwargs: events.append(event)

        with patch.object(forge_metadata, "get_issue_comments", return_value=[]), \
                patch.object(
                    forge_metadata,
                    "post_issue_comment",
                    side_effect=record("comment"),
                ) as comment, \
                patch.object(
                    forge_metadata,
                    "close_issue",
                    side_effect=record("close"),
                ) as close, \
                patch.object(
                    forge_metadata,
                    "clear_issue_assignees",
                    side_effect=record("clear"),
                ) as clear, \
                patch.object(forge_metadata, "add_issue_label") as label:
            succeeded = forge_metadata.reject_issue_form(issue, rejection)
        return succeeded, events, comment, close, clear, label

    def test_rejection_closes_claim_before_clearing_assignee(self) -> None:
        issue = _form_issue(title="Add support for Widget", assignees=["runner"])

        succeeded, events, comment, close, clear, label = self._reject_live(issue)

        self.assertTrue(succeeded)
        self.assertEqual(events, ["comment", "close", "clear"])
        comment.assert_called_once()
        close.assert_called_once()
        clear.assert_called_once_with(4242)
        label.assert_not_called()

    def test_failed_comment_does_not_close_or_clear_the_claim(self) -> None:
        rejection = forge_metadata.IssueFormRejection(
            rule=forge_metadata.ISSUE_FORM_RULE_MAVEN_COORDINATES,
            offending_value="Add support for Widget",
            requirement="Name the coordinates.",
        )
        with patch.object(forge_metadata, "get_issue_comments", return_value=[]), \
                patch.object(
                    forge_metadata,
                    "post_issue_comment",
                    side_effect=RuntimeError("comment failed"),
                ), \
                patch.object(forge_metadata, "close_issue") as close, \
                patch.object(forge_metadata, "clear_issue_assignees") as clear:
            succeeded = forge_metadata.reject_issue_form(
                _form_issue(title="Add support for Widget", assignees=["runner"]),
                rejection,
            )

        self.assertFalse(succeeded)
        close.assert_not_called()
        clear.assert_not_called()


class IssueFormGateClaimOrderTests(unittest.TestCase):
    """Claim preparation pins a fresh base and checks the isolated worktree."""

    def test_issue_base_is_fetched_and_resolved(self) -> None:
        completed_process = subprocess.CompletedProcess(args=[], returncode=0, stdout="")

        with patch.object(
                forge_metadata,
                "run_git_transport",
                return_value=completed_process,
        ) as run, patch.object(
                forge_metadata,
                "resolve_git_commit",
                return_value="base-sha",
        ) as resolve:
            commit = forge_metadata.fetch_issue_base_commit("/repo")

        self.assertEqual(commit, "base-sha")
        run.assert_called_once_with(
            [
                "fetch",
                "--quiet",
                "origin",
                "+master:refs/remotes/origin/master",
            ],
            cwd="/repo",
        )
        resolve.assert_called_once_with("/repo", "refs/remotes/origin/master")

    def test_base_fetch_failure_leaves_issue_unclaimed(self) -> None:
        with patch.object(forge_metadata, "refresh_issue_payload_for_claim", return_value=True), \
                patch.object(
                    forge_metadata,
                    "fetch_issue_base_commit",
                    side_effect=RuntimeError("origin unavailable"),
                ), \
                patch.object(forge_metadata, "try_claim_issue") as claim, \
                patch.object(forge_metadata, "create_issue_workspace") as workspace:
            claimed_issue = forge_metadata.claim_issue_for_processing(
                _form_issue(),
                forge_metadata.LABEL_LIBRARY_NEW,
                "/repo",
                "/metrics",
                "runner",
            )

        self.assertIsNone(claimed_issue)
        claim.assert_not_called()
        workspace.assert_not_called()

    def test_malformed_issue_uses_pinned_worktree_before_rejection(self) -> None:
        issue = _form_issue(title="Add support for Widget")
        rejection = forge_metadata.IssueFormRejection(
            rule=forge_metadata.ISSUE_FORM_RULE_MAVEN_COORDINATES,
            offending_value="Add support for Widget",
            requirement="Name the coordinates.",
        )
        events: list[str] = []

        with patch.object(forge_metadata, "refresh_issue_payload_for_claim", return_value=True), \
                patch.object(
                    forge_metadata,
                    "fetch_issue_base_commit",
                    side_effect=lambda *_args: events.append("base") or "base-sha",
                ), \
                patch.object(
                    forge_metadata,
                    "try_claim_issue",
                    side_effect=lambda *_args: events.append("claim") or "item-4242",
                ), \
                patch.object(
                    forge_metadata,
                    "create_issue_workspace",
                    side_effect=lambda *_args: events.append("workspace") or ("/worktree", "/metrics"),
                ) as workspace, \
                patch.object(
                    forge_metadata,
                    "create_preflight_info_dir",
                    return_value="/preflight",
                ), \
                patch.object(
                    forge_metadata,
                    "check_issue_form",
                    side_effect=lambda *_args: (
                        events.append("check")
                        or forge_metadata.IssueFormVerdict(rejection=rejection)
                    ),
                ) as check, \
                patch.object(
                    forge_metadata,
                    "reject_issue_form",
                    side_effect=lambda *_args: events.append("reject") or True,
                ) as reject, \
                patch.object(
                    forge_metadata,
                    "cleanup_claim_preparation_workspace",
                    side_effect=lambda *_args: events.append("cleanup"),
                ) as cleanup:
            claimed_issue = forge_metadata.claim_issue_for_processing(
                issue,
                forge_metadata.LABEL_LIBRARY_NEW,
                "/repo",
                "/metrics",
                "runner",
            )

        self.assertIsNone(claimed_issue)
        self.assertEqual(events, ["base", "claim", "workspace", "check", "reject", "cleanup"])
        workspace.assert_called_once_with("/repo", "/metrics", 4242, "base-sha")
        check.assert_called_once_with(issue, forge_metadata.LABEL_LIBRARY_NEW, "/worktree")
        reject.assert_called_once_with(issue, rejection)
        cleanup.assert_called_once_with("/repo", "/worktree", "/preflight")

    def test_undecided_form_releases_claim_and_worktree(self) -> None:
        with patch.object(forge_metadata, "refresh_issue_payload_for_claim", return_value=True), \
                patch.object(forge_metadata, "fetch_issue_base_commit", return_value="base-sha"), \
                patch.object(forge_metadata, "try_claim_issue", return_value="item-4242"), \
                patch.object(
                    forge_metadata,
                    "create_issue_workspace",
                    return_value=("/worktree", "/metrics"),
                ), \
                patch.object(
                    forge_metadata,
                    "create_preflight_info_dir",
                    return_value="/preflight",
                ), \
                patch.object(
                    forge_metadata,
                    "check_issue_form",
                    return_value=forge_metadata.IssueFormVerdict(
                        undecided_reason="host unreachable",
                    ),
                ), \
                patch.object(forge_metadata, "reject_issue_form") as reject, \
                patch.object(forge_metadata, "revert_issue_claim") as revert, \
                patch.object(forge_metadata, "cleanup_claim_preparation_workspace") as cleanup:
            claimed_issue = forge_metadata.claim_issue_for_processing(
                _form_issue(),
                forge_metadata.LABEL_LIBRARY_NEW,
                "/repo",
                "/metrics",
                "runner",
            )

        self.assertIsNone(claimed_issue)
        reject.assert_not_called()
        revert.assert_called_once_with("item-4242", 4242, "issue-form check was undecided")
        cleanup.assert_called_once_with("/repo", "/worktree", "/preflight")

    def test_failed_rejection_releases_claim_and_worktree(self) -> None:
        rejection = forge_metadata.IssueFormRejection(
            rule=forge_metadata.ISSUE_FORM_RULE_MAVEN_COORDINATES,
            offending_value="Add support for Widget",
            requirement="Name the coordinates.",
        )
        with patch.object(forge_metadata, "refresh_issue_payload_for_claim", return_value=True), \
                patch.object(forge_metadata, "fetch_issue_base_commit", return_value="base-sha"), \
                patch.object(forge_metadata, "try_claim_issue", return_value="item-4242"), \
                patch.object(
                    forge_metadata,
                    "create_issue_workspace",
                    return_value=("/worktree", "/metrics"),
                ), \
                patch.object(
                    forge_metadata,
                    "create_preflight_info_dir",
                    return_value="/preflight",
                ), \
                patch.object(
                    forge_metadata,
                    "check_issue_form",
                    return_value=forge_metadata.IssueFormVerdict(rejection=rejection),
                ), \
                patch.object(forge_metadata, "reject_issue_form", return_value=False), \
                patch.object(forge_metadata, "revert_issue_claim") as revert, \
                patch.object(forge_metadata, "cleanup_claim_preparation_workspace") as cleanup:
            claimed_issue = forge_metadata.claim_issue_for_processing(
                _form_issue(title="Add support for Widget"),
                forge_metadata.LABEL_LIBRARY_NEW,
                "/repo",
                "/metrics",
                "runner",
            )

        self.assertIsNone(claimed_issue)
        revert.assert_called_once_with(
            "item-4242",
            4242,
            "issue-form rule 'maven-coordinates' could not close the issue",
        )
        cleanup.assert_called_once_with("/repo", "/worktree", "/preflight")

    def test_unexpected_post_claim_failure_releases_claim_and_worktree(self) -> None:
        with patch.object(forge_metadata, "refresh_issue_payload_for_claim", return_value=True), \
                patch.object(forge_metadata, "fetch_issue_base_commit", return_value="base-sha"), \
                patch.object(forge_metadata, "try_claim_issue", return_value="item-4242"), \
                patch.object(
                    forge_metadata,
                    "create_issue_workspace",
                    return_value=("/worktree", "/metrics"),
                ), \
                patch.object(
                    forge_metadata,
                    "create_preflight_info_dir",
                    return_value="/preflight",
                ), \
                patch.object(
                    forge_metadata,
                    "check_issue_form",
                    return_value=forge_metadata.ISSUE_FORM_ACCEPTED,
                ), \
                patch.object(
                    forge_metadata,
                    "maybe_handle_not_for_native_image_issue",
                    return_value=False,
                ), \
                patch.object(
                    forge_metadata,
                    "build_claim_metadata",
                    side_effect=RuntimeError("invalid metadata index"),
                ) as build_metadata, \
                patch.object(forge_metadata, "revert_issue_claim") as revert, \
                patch.object(forge_metadata, "cleanup_claim_preparation_workspace") as cleanup:
            claimed_issue = forge_metadata.claim_issue_for_processing(
                _form_issue(),
                forge_metadata.LABEL_LIBRARY_NEW,
                "/repo",
                "/metrics",
                "runner",
            )

        self.assertIsNone(claimed_issue)
        build_metadata.assert_called_once_with(
            unittest.mock.ANY,
            forge_metadata.LABEL_LIBRARY_NEW,
            "/worktree",
        )
        revert.assert_called_once_with(
            "item-4242",
            4242,
            "post-claim preparation failure (RuntimeError)",
        )
        cleanup.assert_called_once_with("/repo", "/worktree", "/preflight")

    def test_successful_claim_uses_one_pinned_base_for_all_repository_state(self) -> None:
        issue = _form_issue()
        with patch.object(forge_metadata, "refresh_issue_payload_for_claim", return_value=True), \
                patch.object(forge_metadata, "fetch_issue_base_commit", return_value="base-sha"), \
                patch.object(forge_metadata, "try_claim_issue", return_value="item-4242"), \
                patch.object(
                    forge_metadata,
                    "create_issue_workspace",
                    return_value=("/worktree", "/metrics"),
                ) as workspace, \
                patch.object(
                    forge_metadata,
                    "create_preflight_info_dir",
                    return_value="/preflight",
                ), \
                patch.object(
                    forge_metadata,
                    "check_issue_form",
                    return_value=forge_metadata.ISSUE_FORM_ACCEPTED,
                ) as check, \
                patch.object(
                    forge_metadata,
                    "maybe_handle_not_for_native_image_issue",
                    return_value=False,
                ) as native_image, \
                patch.object(
                    forge_metadata,
                    "build_claim_metadata",
                    return_value=("org.example:widget:1.2.3", None, None),
                ) as build_metadata, \
                patch.object(
                    forge_metadata,
                    "resolve_issue_continuation_marker",
                    return_value=None,
                ) as continuation, \
                patch.object(
                    forge_metadata,
                    "resolve_chunked_dynamic_access_exhaust_report",
                    return_value=None,
                ) as exhaust_report, \
                patch.object(forge_metadata, "cleanup_claim_preparation_workspace") as cleanup:
            claimed_issue = forge_metadata.claim_issue_for_processing(
                issue,
                forge_metadata.LABEL_LIBRARY_NEW,
                "/repo",
                "/canonical-metrics",
                "runner",
            )

        self.assertIsNotNone(claimed_issue)
        assert claimed_issue is not None
        self.assertEqual(claimed_issue.issue_base_commit, "base-sha")
        self.assertEqual(claimed_issue.worktree_path, "/worktree")
        workspace.assert_called_once_with("/repo", "/canonical-metrics", 4242, "base-sha")
        check.assert_called_once_with(issue, forge_metadata.LABEL_LIBRARY_NEW, "/worktree")
        native_image.assert_called_once_with(issue, "/worktree")
        build_metadata.assert_called_once_with(issue, forge_metadata.LABEL_LIBRARY_NEW, "/worktree")
        continuation.assert_called_once_with(
            issue,
            forge_metadata.LABEL_LIBRARY_NEW,
            "org.example:widget:1.2.3",
            "/worktree",
        )
        exhaust_report.assert_called_once_with(
            issue,
            "/worktree",
            "org.example:widget:1.2.3",
            None,
        )
        cleanup.assert_not_called()

    def test_missing_chunk_report_returns_claim_to_todo(self) -> None:
        issue = _form_issue(label_names=[
            forge_metadata.LABEL_LIBRARY_NEW,
            forge_metadata.LABEL_CHUNKED_DYNAMIC_ACCESS,
        ])
        with patch.object(forge_metadata, "refresh_issue_payload_for_claim", return_value=True), \
                patch.object(forge_metadata, "fetch_issue_base_commit", return_value="base-sha"), \
                patch.object(forge_metadata, "try_claim_issue", return_value="item-4242"), \
                patch.object(
                    forge_metadata,
                    "create_issue_workspace",
                    return_value=("/worktree", "/metrics"),
                ), \
                patch.object(
                    forge_metadata,
                    "create_preflight_info_dir",
                    return_value="/preflight",
                ), \
                patch.object(
                    forge_metadata,
                    "check_issue_form",
                    return_value=forge_metadata.ISSUE_FORM_ACCEPTED,
                ), \
                patch.object(
                    forge_metadata,
                    "maybe_handle_not_for_native_image_issue",
                    return_value=False,
                ), \
                patch.object(
                    forge_metadata,
                    "build_claim_metadata",
                    return_value=("org.example:widget:1.2.3", None, None),
                ), \
                patch.object(
                    forge_metadata,
                    "resolve_issue_continuation_marker",
                    return_value=None,
                ), \
                patch.object(
                    forge_metadata,
                    "resolve_chunked_dynamic_access_exhaust_report",
                    side_effect=RuntimeError("missing report"),
                ), \
                patch.object(forge_metadata, "revert_issue_claim") as revert, \
                patch.object(forge_metadata, "cleanup_claim_preparation_workspace") as cleanup:
            claimed_issue = forge_metadata.claim_issue_for_processing(
                issue,
                forge_metadata.LABEL_LIBRARY_NEW,
                "/repo",
                "/metrics",
                "runner",
            )

        self.assertIsNone(claimed_issue)
        revert.assert_called_once_with(
            "item-4242",
            4242,
            "chunked-dynamic-access setup failure (RuntimeError)",
        )
        cleanup.assert_called_once_with("/repo", "/worktree", "/preflight")

    def test_continuation_rebases_onto_pinned_base_without_refetching_master(self) -> None:
        marker = SimpleNamespace(
            preserved_branch="ai/runner/preserved",
            issue_number=4242,
            continue_from="explore",
        )
        completed = subprocess.CompletedProcess(args=[], returncode=0, stdout="")

        with patch.object(
                forge_metadata,
                "fetch_remote_branch",
                return_value="refs/remotes/origin/ai/runner/preserved",
        ) as fetch_branch, patch.object(
                forge_metadata.subprocess,
                "run",
                return_value=completed,
        ) as run:
            resumed = forge_metadata.checkout_continuation_branch(
                "/worktree",
                marker,
                "base-sha",
            )

        self.assertTrue(resumed)
        fetch_branch.assert_called_once_with("/worktree", "ai/runner/preserved")
        self.assertIn(
            call(
                ["git", "rebase", "base-sha"],
                cwd="/worktree",
                env=unittest.mock.ANY,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                check=False,
            ),
            run.call_args_list,
        )


class SingleIssueProcessingTests(unittest.TestCase):
    def test_append_chunked_dynamic_access_workflow_args_passes_issue_context_for_first_run(self) -> None:
        claimed_issue = _claimed_issue()
        pipeline_argv = ["--coordinates", claimed_issue.issue_coordinates]

        forge_metadata.append_chunked_dynamic_access_workflow_args(pipeline_argv, claimed_issue, 4)

        self.assertEqual(
            pipeline_argv,
            [
                "--coordinates", claimed_issue.issue_coordinates,
                "--issue-number", "1412",
                "--chunk-class-count", "4",
            ],
        )

    def test_append_chunked_dynamic_access_workflow_args_omits_count_when_not_chunked(self) -> None:
        claimed_issue = _claimed_issue()
        pipeline_argv = ["--coordinates", claimed_issue.issue_coordinates]

        forge_metadata.append_chunked_dynamic_access_workflow_args(pipeline_argv, claimed_issue, None)

        self.assertEqual(
            pipeline_argv,
            [
                "--coordinates", claimed_issue.issue_coordinates,
                "--issue-number", "1412",
            ],
        )

    def test_prepare_dynamic_access_chunking_records_threshold_and_applies_label(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            claimed_issue = _claimed_issue()
            claimed_issue = forge_metadata.ClaimedIssue(
                **{
                    **claimed_issue.__dict__,
                    "worktree_path": tmpdir,
                },
            )
            report = _dynamic_access_report([
                "org.example.A",
                "org.example.B",
                "org.example.C",
                "org.example.D",
                "org.example.E",
                "org.example.F",
            ])

            with patch.object(forge_metadata, "_prepare_new_library_dynamic_access_report"), \
                    patch.object(forge_metadata, "_generate_dispatcher_dynamic_access_report"), \
                    patch.object(forge_metadata, "_load_dispatcher_dynamic_access_report", return_value=report), \
                    patch.object(forge_metadata, "add_issue_label") as add_issue_label, \
                    patch.dict(os.environ, {"FORGE_DYNAMIC_ACCESS_CHUNK_CLASS_THRESHOLD": "5"}, clear=True):
                chunk_count = forge_metadata.prepare_dynamic_access_chunking(
                    claimed_issue,
                    "dynamic_access_main_sources_pi_gpt-5.6-sol",
                )

            report_path = forge_metadata.dynamic_access_exhaust_report_path(
                tmpdir,
                claimed_issue.issue_coordinates,
            )
            self.assertEqual(chunk_count, 5)
            self.assertTrue(forge_metadata.issue_has_label(claimed_issue.issue, forge_metadata.LABEL_CHUNKED_DYNAMIC_ACCESS))
            add_issue_label.assert_called_once_with(1412, forge_metadata.LABEL_CHUNKED_DYNAMIC_ACCESS)
            self.assertTrue(os.path.isfile(report_path))
            report_state = forge_metadata.DynamicAccessExhaustReport.load(report_path)
            self.assertEqual(report_state.class_threshold, 5)
            self.assertEqual(report_state.current_chunk_class_count, 5)

    def test_prepare_dynamic_access_chunking_uses_remaining_class_count_for_final_chunk(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            claimed_issue = _claimed_issue()
            claimed_issue.issue["labels"] = [{"name": forge_metadata.LABEL_CHUNKED_DYNAMIC_ACCESS}]
            claimed_issue = forge_metadata.ClaimedIssue(
                **{
                    **claimed_issue.__dict__,
                    "worktree_path": tmpdir,
                },
            )
            report_state = forge_metadata.DynamicAccessExhaustReport.create(
                coordinate=claimed_issue.issue_coordinates,
                issue_number=1412,
            )
            report_state.mark_completed("org.example.A")
            report_state.mark_completed("org.example.B")
            report_state.mark_completed("org.example.C")
            report_state.mark_completed("org.example.D")
            report_state.save(report_state.default_path(tmpdir))
            report = _dynamic_access_report([
                "org.example.A",
                "org.example.B",
                "org.example.C",
                "org.example.D",
                "org.example.E",
                "org.example.F",
            ])

            with patch.object(forge_metadata, "_prepare_new_library_dynamic_access_report"), \
                    patch.object(forge_metadata, "_generate_dispatcher_dynamic_access_report"), \
                    patch.object(forge_metadata, "_load_dispatcher_dynamic_access_report", return_value=report), \
                    patch.object(forge_metadata, "add_issue_label") as add_issue_label, \
                    patch.dict(os.environ, {"FORGE_DYNAMIC_ACCESS_CHUNK_CLASS_THRESHOLD": "5"}, clear=True):
                chunk_count = forge_metadata.prepare_dynamic_access_chunking(
                    claimed_issue,
                    "dynamic_access_main_sources_pi_gpt-5.6-sol",
                )

            report_state = forge_metadata.DynamicAccessExhaustReport.load(report_state.default_path(tmpdir))
            self.assertEqual(chunk_count, 2)
            self.assertEqual(report_state.class_threshold, 5)
            self.assertEqual(report_state.current_chunk_class_count, 2)
            add_issue_label.assert_not_called()

    def test_chunked_dynamic_access_base_check_uses_pr_merge_commit_for_squash_merges(self) -> None:
        report_state = forge_metadata.DynamicAccessExhaustReport.create(
            coordinate="org.example:lib:1.0.0",
            issue_number=1412,
        )
        report_state.latest_chunk_commit = "head-commit"
        report_state.latest_chunk_pull_request = 4242

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
            forge_metadata.verify_chunked_dynamic_access_base_contains_published_commit(
                report_state,
                "/tmp/reachability-worktree",
            )

        run.assert_called_once_with(
            ["git", "merge-base", "--is-ancestor", "squash-merge-commit", "HEAD"],
            cwd="/tmp/reachability-worktree",
            check=False,
        )

    def test_process_single_issue_claims_without_chunk_artifact_override(self) -> None:
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
        }

        with patch.dict(os.environ, env, clear=True):
            configs = forge_metadata.get_review_queue_configs_from_environment()

        self.assertEqual(
            [(config.label, config.limit) for config in configs],
            [
                (forge_metadata.LABEL_LIBRARY_NEW, 0),
                (forge_metadata.LABEL_PR_JAVAC_FIX, 2),
                (forge_metadata.LABEL_PR_JAVA_RUN_FIX, 2),
                (forge_metadata.LABEL_PR_NI_RUN_FIX, 2),
                (forge_metadata.LABEL_PR_LIBRARY_UPDATE, 2),
                (forge_metadata.LABEL_PR_LIBRARY_BULK_UPDATE, 4),
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

    def test_take_blocked_issues_is_disabled_by_default(self) -> None:
        default_args = forge_metadata.parse_args(["--label", forge_metadata.LABEL_LIBRARY_NEW])
        override_args = forge_metadata.parse_args([
            "--label",
            forge_metadata.LABEL_LIBRARY_NEW,
            "--take-blocked-issues",
        ])

        self.assertFalse(default_args.take_blocked_issues)
        self.assertTrue(override_args.take_blocked_issues)

    def test_issue_queue_modes_accept_priority_tiers(self) -> None:
        for priority in forge_metadata.PRIORITY_CHOICES:
            with self.subTest(priority=priority):
                work_queue_args = forge_metadata.parse_args([
                    "--run-work-queues",
                    "--priority",
                    priority,
                ])
                label_args = forge_metadata.parse_args([
                    "--label",
                    forge_metadata.LABEL_LIBRARY_NEW,
                    "--priority",
                    priority,
                ])

                self.assertEqual(work_queue_args.priority, priority)
                self.assertEqual(label_args.priority, priority)

    def test_issue_queue_modes_accept_user_requested_only_flag(self) -> None:
        work_queue_args = forge_metadata.parse_args(["--run-work-queues", "--user-requested-only"])
        label_args = forge_metadata.parse_args([
            "--label",
            forge_metadata.LABEL_LIBRARY_NEW,
            "--user-requested-only",
        ])

        self.assertTrue(work_queue_args.user_requested_only)
        self.assertTrue(label_args.user_requested_only)

    def test_review_label_environment_overrides_default_review_queues(self) -> None:
        env = {
            "FORGE_REVIEW_LABEL": forge_metadata.LABEL_PR_LIBRARY_BULK_UPDATE,
            "FORGE_REVIEW_LIMIT": "3",
            "FORGE_BULK_UPDATE_REVIEW_LIMIT": "0",
        }

        with patch.dict(os.environ, env, clear=True):
            configs = forge_metadata.get_review_queue_configs_from_environment()

        self.assertEqual(
            [(config.label, config.limit) for config in configs],
            [
                (forge_metadata.LABEL_PR_LIBRARY_BULK_UPDATE, 3),
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
            user_requested_only=False,
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

        random_offset.assert_called_once_with(
            forge_metadata.LABEL_LIBRARY_NEW,
            user_requested_only=False,
        )
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
            user_requested_only=False,
            environment_already_validated=True,
        )
        process_reviews.assert_not_called()

    def test_process_work_queues_passes_user_requested_only_to_issue_scans(self) -> None:
        env = {
            "FORGE_JAVAC_WORK_LIMIT": "0",
            "FORGE_JAVA_RUN_WORK_LIMIT": "0",
            "FORGE_NI_RUN_WORK_LIMIT": "0",
            "FORGE_LIBRARY_UPDATE_WORK_LIMIT": "0",
            "FORGE_WORK_LIMIT": "1",
            "FORGE_REVIEW_LIMIT": "0",
            "FORGE_RANDOM_WORK_OFFSET": "1",
            "FORGE_USER_REQUESTED_ISSUES_ONLY": "1",
        }

        with patch.dict(os.environ, env, clear=True), \
                patch.object(forge_metadata, "validate_issue_processing_environment"), \
                patch.object(
                    forge_metadata, "resolve_random_issue_scan_offset", return_value=42
                ) as random_offset, \
                patch.object(forge_metadata, "process_issues_with_label", return_value=0) as process_issues:
            forge_metadata.process_work_queues(
                "/tmp/reachability",
                "/tmp/metrics",
                "automation-user",
            )

        random_offset.assert_called_once_with(
            forge_metadata.LABEL_LIBRARY_NEW,
            user_requested_only=True,
        )
        process_issues.assert_called_once_with(
            forge_metadata.LABEL_LIBRARY_NEW,
            1,
            42,
            "/tmp/reachability",
            "/tmp/metrics",
            forge_metadata.DEFAULT_WORK_QUEUE_STRATEGY_NAME,
            False,
            "automation-user",
            forge_metadata.DEFAULT_PARALLELISM,
            user_requested_only=True,
            environment_already_validated=True,
        )

    def test_process_work_queues_forwards_take_blocked_issues_override(self) -> None:
        env = {
            "FORGE_JAVAC_WORK_LIMIT": "0",
            "FORGE_JAVA_RUN_WORK_LIMIT": "0",
            "FORGE_NI_RUN_WORK_LIMIT": "0",
            "FORGE_LIBRARY_UPDATE_WORK_LIMIT": "0",
            "FORGE_WORK_LIMIT": "1",
            "FORGE_REVIEW_LIMIT": "0",
            "FORGE_WORK_LABEL": forge_metadata.LABEL_LIBRARY_NEW,
        }

        with patch.dict(os.environ, env, clear=True), \
                patch.object(forge_metadata, "validate_issue_processing_environment"), \
                patch.object(forge_metadata, "process_issues_with_label", return_value=0) as process_issues:
            forge_metadata.process_work_queues(
                "/tmp/reachability",
                "/tmp/metrics",
                "automation-user",
                take_blocked_issues=True,
            )

        process_issues.assert_called_once_with(
            forge_metadata.LABEL_LIBRARY_NEW,
            1,
            0,
            "/tmp/reachability",
            "/tmp/metrics",
            forge_metadata.DEFAULT_WORK_QUEUE_STRATEGY_NAME,
            False,
            "automation-user",
            forge_metadata.DEFAULT_PARALLELISM,
            user_requested_only=False,
            environment_already_validated=True,
            take_blocked_issues=True,
        )

    def test_process_work_queues_resolves_auth_for_review_only_queue(self) -> None:
        env = {
            "FORGE_JAVAC_WORK_LIMIT": "0",
            "FORGE_JAVA_RUN_WORK_LIMIT": "0",
            "FORGE_NI_RUN_WORK_LIMIT": "0",
            "FORGE_LIBRARY_UPDATE_WORK_LIMIT": "0",
            "FORGE_WORK_LIMIT": "0",
            "FORGE_REVIEW_LIMIT": "1",
            "FORGE_REVIEW_LABEL": forge_metadata.LABEL_LIBRARY_NEW,
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


class PullRequestReviewSelectionTests(unittest.TestCase):
    def test_pull_request_state_loads_named_check_provenance(self) -> None:
        payload = {
            "data": {
                "repository": {
                    "pullRequest": _pull_request_state(9656, "SUCCESS"),
                },
            },
        }
        with patch.object(forge_metadata, "gh_json", return_value=payload) as gh_json:
            forge_metadata.get_pull_request_state(9656)

        query_argument = gh_json.call_args.args[-1]
        self.assertIn("contexts(first: 100)", query_argument)
        self.assertIn("checkSuite", query_argument)
        self.assertIn("workflowRun", query_argument)
        self.assertIn("commit", query_argument)

    def test_trusted_current_head_attestation_is_accepted(self) -> None:
        state = _add_local_review_attestation(_pull_request_state(9656, "SUCCESS"))
        self.assertTrue(forge_metadata.has_trusted_local_review_attestation(state))

    def test_non_current_or_untrusted_attestation_is_rejected(self) -> None:
        cases = {
            "older-sha": ("checkSuite", "commit", "oid", "older-head"),
            "failed": (None, None, "conclusion", "FAILURE"),
            "skipped": (None, None, "conclusion", "SKIPPED"),
            "untrusted-app": ("checkSuite", "app", "slug", "other-app"),
            "wrong-workflow": (
                "checkSuite", "workflowRun", "workflow", {"name": "Other Workflow"},
            ),
            "wrong-workflow-path": (
                "checkSuite",
                "workflowRun",
                "file",
                {"path": ".github/workflows/other.yml"},
            ),
        }
        for name, (outer, section, key, value) in cases.items():
            with self.subTest(name=name):
                state = _pull_request_state(9656, "SUCCESS")
                check_run = _local_review_attestation_check(state["headRefOid"])
                if outer is None:
                    check_run[key] = value
                else:
                    check_run[outer][section][key] = value
                _add_local_review_attestation(state, check_run)
                self.assertFalse(forge_metadata.has_trusted_local_review_attestation(state))

        self.assertFalse(
            forge_metadata.has_trusted_local_review_attestation(
                _pull_request_state(9656, "SUCCESS")
            )
        )

    def test_attested_approval_targets_exact_head_commit(self) -> None:
        state = _pull_request_state(9656, "SUCCESS")
        with patch.object(forge_metadata, "gh") as gh:
            forge_metadata.approve_pull_request_from_local_review_attestation(state)

        self.assertIn("commit_id=head-9656", gh.call_args.args)
        self.assertIn("event=APPROVE", gh.call_args.args)

    def test_bulk_pull_request_list_omits_status_check_rollup(self) -> None:
        with patch.object(forge_metadata, "gh_json", return_value=[]) as gh_json:
            self.assertEqual(
                [],
                forge_metadata.get_pull_requests_with_label(forge_metadata.LABEL_LIBRARY_NEW, 20),
            )

        args = gh_json.call_args.args
        self.assertEqual("number,title,url,author,labels", args[-1])
        self.assertNotIn("statusCheckRollup", args)

    def test_current_head_attestation_approves_without_agent(self) -> None:
        pull_request = _pull_request(9656, [forge_metadata.LABEL_LIBRARY_NEW])
        state = _add_local_review_attestation(_pull_request_state(9656, "SUCCESS"))

        with patch.object(forge_metadata, "get_pull_requests_with_labels", return_value=[]), \
                patch.object(
                    forge_metadata,
                    "get_pull_requests_with_label",
                    return_value=[pull_request],
                ), \
                patch.object(forge_metadata, "get_pull_request_state", return_value=state), \
                patch.object(
                    forge_metadata,
                    "approve_pull_request_from_local_review_attestation",
                ) as approve, \
                patch.object(forge_metadata, "review_pull_request") as review, \
                patch.object(
                    forge_metadata,
                    "reconcile_reviewed_pull_request",
                    return_value=True,
                ) as reconcile:
            forge_metadata.process_pull_requests_with_label(
                forge_metadata.LABEL_LIBRARY_NEW,
                1,
                "/tmp/reachability",
                "automation-user",
            )

        approve.assert_called_once()
        self.assertEqual(9656, approve.call_args.args[0]["number"])
        review.assert_not_called()
        reconcile.assert_called_once_with(9656, "/tmp/reachability")

    def test_non_attested_cases_use_agent(self) -> None:
        missing = _pull_request_state(9656, "SUCCESS")
        older = _pull_request_state(9656, "SUCCESS")
        _add_local_review_attestation(older, _local_review_attestation_check("older-head"))
        skipped = _pull_request_state(9656, "SUCCESS")
        skipped_check = _local_review_attestation_check(skipped["headRefOid"])
        skipped_check["conclusion"] = "SKIPPED"
        _add_local_review_attestation(skipped, skipped_check)
        failed = _pull_request_state(9656, "SUCCESS")
        failed_check = _local_review_attestation_check(failed["headRefOid"])
        failed_check["conclusion"] = "FAILURE"
        _add_local_review_attestation(failed, failed_check)
        untrusted = _pull_request_state(9656, "SUCCESS")
        untrusted_check = _local_review_attestation_check(untrusted["headRefOid"])
        untrusted_check["checkSuite"]["app"]["slug"] = "other-app"
        _add_local_review_attestation(untrusted, untrusted_check)
        malformed = _pull_request_state(9656, "SUCCESS")
        _add_local_review_attestation(malformed, {"name": "malformed"})

        cases = {
            "missing": missing,
            "older-sha": older,
            "skipped": skipped,
            "failed": failed,
            "untrusted": untrusted,
            "malformed": malformed,
        }
        for name, state in cases.items():
            with self.subTest(name=name):
                pull_request = _pull_request(9656, [forge_metadata.LABEL_LIBRARY_NEW])
                with patch.object(
                        forge_metadata,
                        "get_pull_requests_with_labels",
                        return_value=[],
                ), patch.object(
                        forge_metadata,
                        "get_pull_requests_with_label",
                        return_value=[pull_request],
                ), patch.object(
                        forge_metadata,
                        "get_pull_request_state",
                        return_value=state,
                ), patch.object(
                        forge_metadata,
                        "approve_pull_request_from_local_review_attestation",
                ) as approve, patch.object(
                        forge_metadata,
                        "review_pull_request",
                        return_value=True,
                ) as review, patch.object(
                        forge_metadata,
                        "reconcile_reviewed_pull_request",
                        return_value=True,
                ):
                    forge_metadata.process_pull_requests_with_label(
                        forge_metadata.LABEL_LIBRARY_NEW,
                        1,
                        "/tmp/reachability",
                        "automation-user",
                    )

                approve.assert_not_called()
                review.assert_called_once()

    def test_direct_approval_failure_stops_review_processing(self) -> None:
        pull_request = _pull_request(9656, [forge_metadata.LABEL_LIBRARY_NEW])
        state = _add_local_review_attestation(_pull_request_state(9656, "SUCCESS"))

        with patch.object(forge_metadata, "get_pull_requests_with_labels", return_value=[]), \
                patch.object(
                    forge_metadata,
                    "get_pull_requests_with_label",
                    return_value=[pull_request],
                ), \
                patch.object(forge_metadata, "get_pull_request_state", return_value=state), \
                patch.object(
                    forge_metadata,
                    "approve_pull_request_from_local_review_attestation",
                    side_effect=RuntimeError("approval failed"),
                ), \
                patch.object(forge_metadata, "review_pull_request") as review, \
                patch.object(
                    forge_metadata,
                    "reconcile_reviewed_pull_request",
                ) as reconcile, \
                self.assertRaisesRegex(SystemExit, "1"):
            forge_metadata.process_pull_requests_with_label(
                forge_metadata.LABEL_LIBRARY_NEW,
                1,
                "/tmp/reachability",
                "automation-user",
            )

        review.assert_not_called()
        reconcile.assert_not_called()

    def test_process_pull_requests_fetches_state_only_after_cheap_filters(self) -> None:
        buried_pull_requests = [
            _pull_request(
                number,
                [
                    forge_metadata.LABEL_LIBRARY_NEW,
                    forge_metadata.LABEL_HUMAN_INTERVENTION,
                ],
            )
            for number in range(1, 21)
        ]
        eligible_pull_request = _pull_request(100, [forge_metadata.LABEL_LIBRARY_NEW])

        with patch.object(forge_metadata, "get_pull_requests_with_labels", return_value=[]), \
                patch.object(
                    forge_metadata,
                    "get_pull_requests_with_label",
                    side_effect=[
                        buried_pull_requests,
                        [*buried_pull_requests, eligible_pull_request],
                    ],
                ) as get_pull_requests, \
                patch.object(
                    forge_metadata,
                    "get_pull_request_state",
                    return_value=_pull_request_state(100, "SUCCESS"),
                ) as get_pull_request_state, \
                patch.object(forge_metadata, "review_pull_request", return_value=True) as review_pull_request, \
                patch.object(
                    forge_metadata,
                    "reconcile_reviewed_pull_request",
                    return_value=True,
                ) as reconcile_reviewed_pull_request:
            forge_metadata.process_pull_requests_with_label(
                forge_metadata.LABEL_LIBRARY_NEW,
                1,
                "/tmp/reachability",
                "automation-user",
            )

        get_pull_requests.assert_has_calls([
            call(forge_metadata.LABEL_LIBRARY_NEW, 20),
            call(forge_metadata.LABEL_LIBRARY_NEW, 40),
        ])
        get_pull_request_state.assert_called_once_with(100)
        review_pull_request.assert_called_once_with(
            100,
            "/tmp/reachability",
            "https://github.com/oracle/graalvm-reachability-metadata/pull/100",
            None,
        )
        reconcile_reviewed_pull_request.assert_called_once_with(100, "/tmp/reachability")

    def test_process_pull_requests_reviews_only_successful_ci(self) -> None:
        pull_requests = [
            _pull_request(1, [forge_metadata.LABEL_LIBRARY_NEW]),
            _pull_request(2, [forge_metadata.LABEL_LIBRARY_NEW]),
            _pull_request(3, [forge_metadata.LABEL_LIBRARY_NEW]),
        ]
        states = {
            1: _pull_request_state(1, "FAILURE"),
            2: _pull_request_state(2, "PENDING"),
            3: _pull_request_state(3, "SUCCESS"),
        }

        with patch.object(forge_metadata, "get_pull_requests_with_labels", return_value=[]), \
                patch.object(forge_metadata, "get_pull_requests_with_label", return_value=pull_requests), \
                patch.object(
                    forge_metadata,
                    "get_pull_request_state",
                    side_effect=lambda number: states[number],
                ), \
                patch.object(
                    forge_metadata,
                    "reconcile_failed_ci_pull_request",
                ) as reconcile_failed_ci, \
                patch.object(forge_metadata, "review_pull_request", return_value=True) as review_pull_request, \
                patch.object(
                    forge_metadata,
                    "reconcile_reviewed_pull_request",
                    return_value=True,
                ) as reconcile_reviewed_pull_request:
            forge_metadata.process_pull_requests_with_label(
                forge_metadata.LABEL_LIBRARY_NEW,
                1,
                "/tmp/reachability",
                "automation-user",
            )

        self.assertEqual(1, reconcile_failed_ci.call_args.args[0]["number"])
        review_pull_request.assert_called_once_with(
            3,
            "/tmp/reachability",
            "https://github.com/oracle/graalvm-reachability-metadata/pull/3",
            None,
        )
        reconcile_reviewed_pull_request.assert_called_once_with(3, "/tmp/reachability")

    def test_process_pull_requests_refreshes_conflict_before_review(self) -> None:
        pull_request = _pull_request(4, [forge_metadata.LABEL_LIBRARY_NEW])
        state = _pull_request_state(4, "FAILURE", mergeable="CONFLICTING")

        with patch.object(forge_metadata, "get_pull_requests_with_labels", return_value=[]), \
                patch.object(forge_metadata, "get_pull_requests_with_label", return_value=[pull_request]), \
                patch.object(forge_metadata, "get_pull_request_state", return_value=state), \
                patch.object(
                    forge_metadata,
                    "resolve_pull_request_merge_conflict",
                    return_value=True,
                ) as resolve_conflict, \
                patch.object(forge_metadata, "reconcile_failed_ci_pull_request") as reconcile_failed_ci, \
                patch.object(forge_metadata, "review_pull_request") as review_pull_request:
            forge_metadata.process_pull_requests_with_label(
                forge_metadata.LABEL_LIBRARY_NEW,
                1,
                "/tmp/reachability",
                "automation-user",
            )

        self.assertEqual(4, resolve_conflict.call_args.args[0]["number"])
        self.assertEqual("/tmp/reachability", resolve_conflict.call_args.args[1])
        reconcile_failed_ci.assert_not_called()
        review_pull_request.assert_not_called()


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
                            return_value=([cached_issue, claimable_issue], _scan_state(2, exhausted=True)),
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
                            ([issue], _scan_state(1)),
                            ([], _scan_state(1, exhausted=True)),
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
                        return_value=(issues, _scan_state(len(issues), exhausted=True)),
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
                        return_value=(issues, _scan_state(len(issues), exhausted=True)),
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

    def test_process_loop_fetches_only_selected_priority_tier(self) -> None:
        priority = forge_metadata.PRIORITY_NORMAL
        tier = forge_metadata.get_issue_priority_tier(priority)
        with patch.object(forge_metadata, "validate_issue_processing_environment"), \
                patch.object(forge_metadata, "get_prioritized_issues_with_label") as prioritized_fetch, \
                patch.object(forge_metadata, "get_issues_with_label", return_value=[]) as get_issues, \
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
            forge_metadata.DEFAULT_ISSUE_SCAN_BATCH_SIZE,
            0,
            list(tier.extra_labels),
            list(tier.excluded_labels),
            user_requested_only=False,
        )
        prioritized_fetch.assert_not_called()

    def test_priority_choices_map_to_exclusive_label_filters(self) -> None:
        expected_filters = {
            forge_metadata.PRIORITY_HIGH: (
                (forge_metadata.LABEL_HIGH_PRIORITY,),
                (),
            ),
            forge_metadata.LABEL_PRIORITY: (
                (forge_metadata.LABEL_PRIORITY,),
                (forge_metadata.LABEL_HIGH_PRIORITY,),
            ),
            forge_metadata.PRIORITY_NORMAL: (
                (),
                (forge_metadata.LABEL_HIGH_PRIORITY, forge_metadata.LABEL_PRIORITY),
            ),
        }
        for priority, filters in expected_filters.items():
            with self.subTest(priority=priority):
                tier = forge_metadata.get_issue_priority_tier(priority)
                self.assertEqual((tier.extra_labels, tier.excluded_labels), filters)


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

    def test_try_claim_issue_does_not_prioritize_open_blockers(self) -> None:
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
                    patch.object(forge_metadata, "add_issue_label") as add_issue_label, \
                    patch.object(forge_metadata, "get_issue_assignees") as get_issue_assignees:
                self.assertIsNone(
                    forge_metadata.try_claim_issue(
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

    def test_try_claim_issue_skips_chunked_dynamic_access_when_in_progress(self) -> None:
        issue = _search_issue(1412, [forge_metadata.LABEL_CHUNKED_DYNAMIC_ACCESS])
        with tempfile.TemporaryDirectory() as lock_root:
            with patch.object(forge_metadata, "get_issue_claim_locks_root", return_value=lock_root), \
                    patch.object(forge_metadata, "refresh_issue_payload_for_claim", return_value=True), \
                    patch.object(forge_metadata, "get_open_blocking_issue_numbers", return_value=[]), \
                    patch.object(forge_metadata, "get_issue_assignees", return_value=[]), \
                    patch.object(
                        forge_metadata,
                        "get_project_item_state",
                        return_value=("project-item", forge_metadata.STATUS_IN_PROGRESS),
                    ), \
                    patch.object(forge_metadata, "set_issue_assignee") as set_issue_assignee, \
                    patch.object(forge_metadata, "set_item_status") as set_item_status, \
                    patch.object(forge_metadata.random, "uniform", return_value=0), \
                    patch.object(forge_metadata.time, "sleep"):
                self.assertIsNone(
                    forge_metadata.try_claim_issue(issue, "automation-user"),
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
        with patch.object(forge_metadata, "require_issue_graalvm_homes") as require_graalvm_homes:
            forge_metadata.validate_issue_processing_environment()

        require_graalvm_homes.assert_called_once_with()
        self.assertEqual(
            (
                forge_metadata.DEV_GRAALVM_ENV_VAR,
                forge_metadata.POST_GENERATION_GRAALVM_ENV_VAR,
                forge_metadata.LATEST_EA_GRAALVM_ENV_VAR,
            ),
            host_requirements.ISSUE_GRAALVM_ENV_VARS,
        )

    def test_review_only_runs_do_not_require_graalvm(self) -> None:
        args = forge_metadata.parse_args(["--review-pr", "library-new-request"])

        requirements = forge_metadata.resolve_host_requirement_queues(args)

        self.assertFalse(requirements.issue_work)
        self.assertTrue(requirements.review_work)
        self.assertTrue(requirements.github_work)

    def test_issue_runs_require_graalvm_without_review_capabilities(self) -> None:
        args = forge_metadata.parse_args(["--issue-number", "9101"])

        requirements = forge_metadata.resolve_host_requirement_queues(args)

        self.assertTrue(requirements.issue_work)
        self.assertFalse(requirements.review_work)

    def test_fixture_runs_do_not_require_live_github_access(self) -> None:
        args = forge_metadata.parse_args(["--fixture-testing", "--issue-number", "9101"])

        requirements = forge_metadata.resolve_host_requirement_queues(args)

        self.assertTrue(requirements.issue_work)
        self.assertFalse(requirements.github_work)

    def test_work_queue_runs_derive_capabilities_from_enabled_queue_limits(self) -> None:
        args = forge_metadata.parse_args(["--run-work-queues"])
        disabled_issue_queues = {
            name: "0"
            for name in host_requirements.ISSUE_LIMIT_ENV_VARS
        }

        with patch.dict(os.environ, {**disabled_issue_queues, "FORGE_REVIEW_LIMIT": "1"}, clear=True):
            requirements = forge_metadata.resolve_host_requirement_queues(args)

        self.assertFalse(requirements.issue_work)
        self.assertTrue(requirements.review_work)

    def test_work_queue_host_gate_collects_every_enabled_strategy(self) -> None:
        args = forge_metadata.parse_args(["--run-work-queues"])
        environment = {
            name: "0"
            for name in host_requirements.ISSUE_LIMIT_ENV_VARS
        }
        environment.update({
            "FORGE_JAVAC_WORK_LIMIT": "1",
            "FORGE_JAVAC_STRATEGY_NAME": "dynamic_access_main_sources_codex_gpt-5.6-sol",
            "FORGE_WORK_LIMIT": "1",
            "FORGE_STRATEGY_NAME": "dynamic_access_main_sources_pi_gpt-5.6-sol",
        })

        with patch.dict(os.environ, environment, clear=True):
            strategy_names = forge_metadata.resolve_host_requirement_strategy_names(args)

        self.assertEqual(
            strategy_names,
            [
                "dynamic_access_main_sources_codex_gpt-5.6-sol",
                "dynamic_access_main_sources_pi_gpt-5.6-sol",
            ],
        )

    def test_every_work_starting_invocation_validates_host_requirements(self) -> None:
        with patch.object(forge_metadata, "ensure_host_requirements") as ensure, \
                patch.object(forge_metadata, "resolve_authenticated_user", return_value="forge-bot"), \
                patch.object(forge_metadata, "resolve_reachability_repo_root", return_value="/repo"), \
                patch.object(forge_metadata, "resolve_metrics_repo_root", return_value="/metrics"), \
                patch.object(forge_metadata, "run_pull_request_review_loop") as review_loop, \
                patch.object(sys, "argv", ["forge_metadata.py", "--review-pr", "library-new-request"]):
            forge_metadata.main()

        ensure.assert_called_once()
        self.assertEqual(
            host_requirements.QueueRequirements(issue_work=False, review_work=True, github_work=True),
            ensure.call_args.kwargs["requirements"],
        )
        self.assertNotIn("environment", ensure.call_args.kwargs)
        review_loop.assert_called_once()

    def test_host_requirements_check_the_selected_repository_not_the_forge_parent(self) -> None:
        checked_paths: list[tuple[str, str]] = []

        def record_gate(forge_dir: str, **kwargs: object) -> None:
            checked_paths.append((forge_dir, str(kwargs["repo_dir"])))

        with patch.object(forge_metadata, "ensure_host_requirements", side_effect=record_gate), \
                patch.object(forge_metadata, "resolve_authenticated_user", return_value="forge-bot"), \
                patch.object(
                    forge_metadata,
                    "resolve_reachability_repo_root",
                    return_value="/other/repo",
                ) as resolve_repo, \
                patch.object(forge_metadata, "resolve_metrics_repo_root", return_value="/other/repo/forge"), \
                patch.object(forge_metadata, "process_single_issue") as process_issue, \
                patch.object(sys, "argv", [
                    "forge_metadata.py",
                    "--issue-number", "1412",
                    "--reachability-metadata-path", "/other/repo",
                ]):
            forge_metadata.main()

        resolve_repo.assert_called_once_with("/other/repo")
        self.assertEqual([(forge_metadata.FORGE_DIR, "/other/repo")], checked_paths)
        self.assertEqual("/other/repo", process_issue.call_args.args[1])

    def test_cache_maintenance_does_not_validate_host_requirements(self) -> None:
        with patch.object(forge_metadata, "ensure_host_requirements") as ensure, \
                patch.object(forge_metadata, "clear_issue_caches") as clear_issue_caches, \
                patch.object(sys, "argv", ["forge_metadata.py", "--clear-issue-caches"]):
            forge_metadata.main()

        clear_issue_caches.assert_called_once()
        ensure.assert_not_called()


class RunFailureLocationTests(unittest.TestCase):
    """The failure location a step recorded reaches every reporting surface.

    §FS-forge-run-location-reporting.3
    """

    def setUp(self) -> None:
        run_location.reset_run_location()

    def tearDown(self) -> None:
        run_location.reset_run_location()

    def test_terminal_failure_prints_and_forwards_the_recorded_phase_and_step(self) -> None:
        claimed_issue = _claimed_issue()
        expected_line = "run failed in explore/native_trace_gate()[com.acme.Thing]"
        stderr = io.StringIO()

        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(stderr), \
                patch.object(forge_metadata, "preserve_failed_work_for_follow_up", return_value=None), \
                patch.object(forge_metadata, "refresh_preserved_branch_logs"), \
                patch.object(forge_metadata, "revert_claimed_issue"), \
                patch.object(forge_metadata, "apply_failed_run_follow_up") as follow_up:
            with run_location.run_step(
                run_location.PHASE_EXPLORE,
                run_location.STEP_NATIVE_TRACE_GATE,
                operand="com.acme.Thing",
            ):
                run_location.record_step_failure()
            forge_metadata.handle_failed_claimed_issue(claimed_issue, "workflow failure")

        self.assertIn(expected_line, stderr.getvalue())
        forwarded = follow_up.call_args.kwargs["failure_location"]
        self.assertEqual(forge_metadata.format_run_failure_line(forwarded), expected_line)

    def test_human_intervention_comment_leads_with_the_same_pair(self) -> None:
        claimed_issue = _claimed_issue(forge_metadata.LABEL_JAVAC_FAIL)
        failure_location = run_location.RunLocation(
            run_location.PHASE_EXPLORE,
            run_location.STEP_NATIVE_TRACE_GATE,
            "com.acme.Thing",
        )

        with patch.object(
                forge_metadata,
                "resolve_human_intervention_candidate",
                return_value="candidate",
        ), \
                patch.object(
                    forge_metadata,
                    "run_codex_failed_generation_analysis",
                    return_value="Analysis body.",
                ), \
                patch.object(
                    forge_metadata,
                    "post_human_intervention_comment_and_label",
                ) as post_follow_up:
            forge_metadata.apply_failed_run_follow_up(
                claimed_issue,
                failure_location=failure_location,
            )

        comment_body = post_follow_up.call_args.args[1]
        self.assertTrue(
            comment_body.startswith("`run failed in explore/native_trace_gate()[com.acme.Thing]`"),
            comment_body,
        )
        self.assertIn("Analysis body.", comment_body)


class FailedRunFollowUpTests(unittest.TestCase):
    def test_publication_marker_applies_publication_failure_follow_up(self) -> None:
        claimed_issue = _claimed_issue(forge_metadata.LABEL_JAVAC_FAIL)

        with tempfile.TemporaryDirectory() as repo_path:
            marker = forge_metadata.ContinuationMarker.create(
                strategy_name="strategy",
                issue_number=1412,
                label=claimed_issue.label,
                coordinate=claimed_issue.issue_coordinates,
                new_version=None,
            )
            marker.mark_setup_done(skip_fix_phase=True)
            marker.mark_phase_skipped(PHASE_EXPLORE)
            marker.mark_phase_completed(PHASE_FINALIZATION)
            self.assertEqual(marker.continue_from, PHASE_PUBLICATION)
            marker.save(forge_metadata.continuation_marker_path(repo_path))
            preservation_result = forge_metadata.FailurePreservationResult(
                branch_name="ai/test/preserved",
                branch_url="https://github.com/oracle/graalvm-reachability-metadata/tree/ai/test/preserved",
                committed_changes=True,
                reviewable_worktree_path=repo_path,
            )

            with patch.object(forge_metadata, "resolve_human_intervention_candidate", return_value=None), \
                    patch.object(
                        forge_metadata,
                        "post_human_intervention_comment_and_label",
                    ) as post_follow_up:
                forge_metadata.apply_failed_run_follow_up(
                    claimed_issue,
                    preservation_result=preservation_result,
                )

        post_follow_up.assert_called_once()
        self.assertEqual(post_follow_up.call_args.args[0], 1412)
        self.assertIn("publishing the pull request did not finish", post_follow_up.call_args.args[1])
        self.assertEqual(post_follow_up.call_args.kwargs, {"resumable": True})

    def test_setup_marker_does_not_apply_publication_failure_follow_up(self) -> None:
        claimed_issue = _claimed_issue(forge_metadata.LABEL_JAVAC_FAIL)

        with tempfile.TemporaryDirectory() as repo_path:
            marker = forge_metadata.ContinuationMarker.create(
                strategy_name="strategy",
                issue_number=1412,
                label=claimed_issue.label,
                coordinate=claimed_issue.issue_coordinates,
                new_version=None,
            )
            self.assertEqual(marker.continue_from, PHASE_SETUP)
            marker.save(forge_metadata.continuation_marker_path(repo_path))
            preservation_result = forge_metadata.FailurePreservationResult(
                branch_name="ai/test/preserved",
                branch_url="https://github.com/oracle/graalvm-reachability-metadata/tree/ai/test/preserved",
                committed_changes=True,
                reviewable_worktree_path=repo_path,
            )

            with patch.object(forge_metadata, "resolve_human_intervention_candidate", return_value=None), \
                    patch.object(
                        forge_metadata,
                        "post_human_intervention_comment_and_label",
                    ) as post_follow_up:
                forge_metadata.apply_failed_run_follow_up(
                    claimed_issue,
                    preservation_result=preservation_result,
                )

        post_follow_up.assert_not_called()


class InterruptHandlingTests(unittest.TestCase):
    def setUp(self) -> None:
        forge_metadata.clear_user_interrupt_requested()
        self._original_cwd = os.getcwd()

    def tearDown(self) -> None:
        forge_metadata.clear_user_interrupt_requested()
        os.chdir(self._original_cwd)

    def test_interrupt_return_code_from_workflow_raises_keyboard_interrupt(self) -> None:
        claimed_issue = _claimed_issue()

        with patch.object(forge_metadata, "run_add_new_library_support_workflow", return_value=130), \
                patch.object(forge_metadata, "require_claimed_issue_worktree"), \
                patch.object(forge_metadata, "run_library_preparation_preflight", return_value=None), \
                patch.object(forge_metadata, "prepare_dynamic_access_chunking", return_value=None), \
                patch.object(forge_metadata, "require_issue_graalvm_homes") as require_graalvm_homes:
            with self.assertRaises(KeyboardInterrupt):
                forge_metadata.invoke_pipeline(claimed_issue, None, False)

        self.assertTrue(forge_metadata.is_user_interrupt_requested())
        require_graalvm_homes.assert_not_called()

    def test_interrupted_failed_workflow_skips_human_intervention_handling(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path:
            claimed_issue = _claimed_issue_in(repo_path)

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

    def test_unhandled_system_exit_is_failed_issue_not_queue_stopper(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path:
            claimed_issue = _claimed_issue_in(repo_path)

            with patch.object(forge_metadata, "run_claimed_issue", side_effect=SystemExit(1)), \
                    patch.object(forge_metadata, "handle_completed_run") as handle_completed_run, \
                    patch.object(forge_metadata, "handle_failed_claimed_issue") as handle_failed_claimed_issue, \
                    patch.object(forge_metadata, "cleanup_issue_workspace") as cleanup_issue_workspace:
                handled = forge_metadata.process_claimed_issue_lifecycle(
                    claimed_issue,
                    strategy_name=None,
                    keep_tests_without_dynamic_access=False,
                    canonical_metrics_repo_path="/tmp/metrics",
                )

        self.assertFalse(handled)
        handle_completed_run.assert_not_called()
        handle_failed_claimed_issue.assert_called_once()
        cleanup_issue_workspace.assert_called_once_with(claimed_issue, "/tmp/metrics")

    def test_failed_run_analysis_uses_fallback_when_worktree_is_invalid(self) -> None:
        claimed_issue = _claimed_issue()
        candidate = forge_metadata.HumanInterventionCandidate(
            strategy_name=None,
            workflow_status=forge_metadata.RUN_STATUS_FAILURE,
            reason="job_failed",
        )

        with patch.object(forge_metadata, "_load_pending_run_metrics", return_value=None), \
                patch.object(forge_metadata, "collect_issue_log_paths", return_value=[]), \
                patch.object(forge_metadata, "require_claimed_issue_worktree", side_effect=RuntimeError("invalid")), \
                patch.object(forge_metadata.subprocess, "run") as run:
            comment = forge_metadata.run_codex_failed_generation_analysis(
                claimed_issue,
                candidate,
                started_at=123.0,
                preservation_result=None,
            )

        self.assertIn("Human intervention needed", comment)
        run.assert_not_called()

    def test_human_intervention_analysis_uses_fallback_when_worktree_is_invalid(self) -> None:
        claimed_issue = _claimed_issue()
        candidate = forge_metadata.HumanInterventionCandidate(
            strategy_name="strategy",
            workflow_status=forge_metadata.RUN_STATUS_FAILURE,
            reason="low_dynamic_access_coverage",
        )
        strategy = {"model": "test-model"}

        with patch.object(forge_metadata, "load_strategy_by_name", return_value=strategy), \
                patch.object(forge_metadata, "_collect_human_intervention_read_only_files", return_value=[]), \
                patch.object(forge_metadata, "require_claimed_issue_worktree", side_effect=RuntimeError("invalid")), \
                patch.object(forge_metadata, "init_workflow_agent") as init_agent:
            comment = forge_metadata.run_human_intervention_analysis(
                claimed_issue,
                candidate,
                started_at=123.0,
                preservation_result=None,
            )

        self.assertIn("Human intervention needed", comment)
        init_agent.assert_not_called()

    def test_human_intervention_posting_noops_after_interrupt(self) -> None:
        forge_metadata.mark_user_interrupt_requested()

        with patch.object(forge_metadata, "post_issue_comment") as post_issue_comment, \
                patch.object(forge_metadata, "add_issue_label") as add_issue_label:
            forge_metadata.post_human_intervention_comment_and_label(1412, "comment")

        post_issue_comment.assert_not_called()
        add_issue_label.assert_not_called()

    def test_no_unwind_path_relabels_a_recorded_bootstrap_stop_as_ctrl_c(self) -> None:
        """A concurrent worker observing the interrupt must not reset the reason.

        With parallelism > 1 one issue can record the bootstrap stop while another
        worker unwinds through a generic interrupt handler; the main loop would then
        revert the remaining claims, and exit, under the wrong reason.
        """
        claimed_issue = _claimed_issue()
        forge_metadata.mark_user_interrupt_requested(forge_metadata.INTERRUPT_REASON_GRADLE_BOOTSTRAP)

        with patch.object(forge_metadata, "run_add_new_library_support_workflow", return_value=130), \
                patch.object(forge_metadata, "require_claimed_issue_worktree"), \
                patch.object(forge_metadata, "run_library_preparation_preflight", return_value=None), \
                patch.object(forge_metadata, "prepare_dynamic_access_chunking", return_value=None), \
                patch.object(forge_metadata, "create_or_load_run_continuation_marker", return_value=None), \
                patch.object(forge_metadata, "load_continuation_marker", return_value=None), \
                patch.object(forge_metadata, "record_library_update_route_in_marker"):
            with self.assertRaises(KeyboardInterrupt):
                forge_metadata.invoke_pipeline(claimed_issue, None, False)

        self.assertTrue(forge_metadata.is_gradle_bootstrap_interrupt())

    def test_ctrl_c_is_still_recorded_when_no_reason_was_set(self) -> None:
        forge_metadata.preserve_user_interrupt_reason()

        self.assertTrue(forge_metadata.is_user_interrupt_requested())
        self.assertEqual(
            forge_metadata.get_user_interrupt_reason(),
            forge_metadata.INTERRUPT_REASON_CTRL_C,
        )

    def test_gradle_bootstrap_failure_is_classified_as_external(self) -> None:
        failure = forge_metadata.GradleBootstrapFailure("org.example:lib:1.0.0", "/tmp/discover.log")

        wrapped = RuntimeError("wrapped")
        wrapped.__cause__ = failure

        self.assertTrue(forge_metadata.is_external_failure_exception(failure))
        self.assertTrue(forge_metadata.is_external_failure_exception(wrapped))

    def test_preserved_interrupt_reason_survives_later_generic_handlers(self) -> None:
        forge_metadata.mark_user_interrupt_requested(forge_metadata.INTERRUPT_REASON_GRADLE_BOOTSTRAP)
        forge_metadata.preserve_user_interrupt_reason()

        self.assertTrue(forge_metadata.is_gradle_bootstrap_interrupt())
        self.assertEqual(
            forge_metadata.get_user_interrupt_reason(),
            forge_metadata.INTERRUPT_REASON_GRADLE_BOOTSTRAP,
        )

    def test_gradle_bootstrap_failure_reverts_claim_without_human_intervention_follow_up(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path:
            claimed_issue = _claimed_issue_in(repo_path)
            failure = forge_metadata.GradleBootstrapFailure(claimed_issue.issue_coordinates, "/tmp/discover.log")

            with patch.object(forge_metadata, "run_claimed_issue", side_effect=failure), \
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

        self.assertEqual(
            forge_metadata.get_user_interrupt_reason(),
            forge_metadata.INTERRUPT_REASON_GRADLE_BOOTSTRAP,
        )
        handle_completed_run.assert_not_called()
        handle_failed_claimed_issue.assert_not_called()
        revert_claimed_issue.assert_called_once_with(
            claimed_issue,
            forge_metadata.INTERRUPT_REASON_GRADLE_BOOTSTRAP,
        )
        cleanup_issue_workspace.assert_called_once_with(claimed_issue, "/tmp/metrics")

    def test_gradle_bootstrap_failure_stops_the_issue_queue(self) -> None:
        issues = [
            {
                "number": issue_number,
                "title": f"Add support for org.example:lib{issue_number}:1.0.0",
                "labels": [],
                "assignees": [],
            }
            for issue_number in range(1, 4)
        ]

        with tempfile.TemporaryDirectory() as repo_path, tempfile.TemporaryDirectory() as lock_root:
            claimed_issue = _claimed_issue_in(repo_path)
            failure = forge_metadata.GradleBootstrapFailure(claimed_issue.issue_coordinates, "/tmp/discover.log")

            with patch.object(forge_metadata, "get_issue_claim_locks_root", return_value=lock_root), \
                    patch.object(forge_metadata, "validate_issue_processing_environment"), \
                    patch.object(
                        forge_metadata,
                        "get_prioritized_issues_with_label",
                        return_value=(issues, _scan_state(len(issues), exhausted=True)),
                    ), \
                    patch.object(forge_metadata, "get_issue_claim_preflights_or_empty"), \
                    patch.object(
                        forge_metadata,
                        "claim_issue_for_processing",
                        return_value=claimed_issue,
                    ) as claim_issue_for_processing, \
                    patch.object(forge_metadata, "run_claimed_issue", side_effect=failure), \
                    patch.object(forge_metadata, "handle_completed_run") as handle_completed_run, \
                    patch.object(forge_metadata, "handle_failed_claimed_issue") as handle_failed_claimed_issue, \
                    patch.object(forge_metadata, "revert_claimed_issue"), \
                    patch.object(forge_metadata, "cleanup_issue_workspace"):
                with self.assertRaises(KeyboardInterrupt):
                    forge_metadata.process_issues_with_label(
                        forge_metadata.LABEL_LIBRARY_NEW,
                        len(issues),
                        0,
                        "/tmp/reachability",
                        "/tmp/metrics",
                        None,
                        False,
                        "automation-user",
                        1,
                    )

        claim_issue_for_processing.assert_called_once()
        handle_completed_run.assert_not_called()
        handle_failed_claimed_issue.assert_not_called()
        self.assertEqual(
            forge_metadata.get_user_interrupt_reason(),
            forge_metadata.INTERRUPT_REASON_GRADLE_BOOTSTRAP,
        )

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
    def test_review_pull_request_uses_centralized_analysis_selection(self) -> None:
        configured_environment = {
            "FORGE_ANALYSIS_AGENT": "my-pi",
            "FORGE_ANALYSIS_FAMILY": "pi",
            "FORGE_ANALYSIS_MODEL": "configured-model",
            "FORGE_ANALYSIS_PROVIDER": "openrouter",
            "FORGE_ANALYSIS_THINKING_LEVEL": "high",
            "GH_TOKEN": "secret",
            "GH_CONFIG_DIR": "/github-config",
        }
        with tempfile.TemporaryDirectory() as temp_dir, patch.dict(
                os.environ,
                configured_environment,
                clear=True,
        ), patch.object(
            forge_metadata,
            "create_review_workspace",
            return_value=temp_dir,
        ), patch.object(
            forge_metadata,
            "cleanup_review_workspace",
        ) as cleanup_review_workspace, patch(
            "ai_workflows.agents.agent_runtime.run_agent_task",
            return_value=AgentRunResult(
                0,
                os.path.join(temp_dir, "review.log"),
                False,
                "Submitted approval.",
            ),
        ) as run_agent, patch.object(
            forge_metadata,
            "print_pull_request_discussion",
        ) as print_discussion:
            self.assertTrue(
                forge_metadata.review_pull_request(
                    3513,
                    "/repo",
                    "https://github.com/oracle/graalvm-reachability-metadata/pull/3513",
                    "org.example:demo:1.0",
                )
            )

        invocation = run_agent.call_args.kwargs
        selection = invocation["selection"]
        self.assertEqual(selection.backend, "pi")
        self.assertEqual(selection.agent, "my-pi")
        self.assertEqual(selection.model, "configured-model")
        self.assertEqual(selection.provider, "openrouter")
        self.assertEqual(selection.thinking_level, "high")
        self.assertEqual(invocation["working_dir"], temp_dir)
        self.assertEqual(invocation["task_type"], "pr-review")
        self.assertEqual(invocation["library"], "org.example:demo:1.0")
        self.assertIn("submit the review directly", invocation["prompt"])
        agent_environment = invocation["environment"]
        self.assertEqual(agent_environment["GH_TOKEN"], "secret")
        self.assertEqual(agent_environment["GH_CONFIG_DIR"], "/github-config")
        self.assertEqual(agent_environment["_FORGE_AGENT_ALLOW_GITHUB_ACCESS"], "1")
        cleanup_review_workspace.assert_called_once_with("/repo", temp_dir, 3513)
        print_discussion.assert_called_once_with(3513)

    def test_review_pull_request_uses_analysis_role_defaults(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir, patch.dict(
                os.environ,
                {},
                clear=True,
        ), patch.object(
            forge_metadata,
            "create_review_workspace",
            return_value=temp_dir,
        ), patch.object(
            forge_metadata,
            "cleanup_review_workspace",
        ), patch(
            "ai_workflows.agents.agent_runtime.run_agent_task",
            return_value=AgentRunResult(
                0,
                os.path.join(temp_dir, "review.log"),
                False,
                "Submitted approval.",
            ),
        ) as run_agent, patch.object(
            forge_metadata,
            "print_pull_request_discussion",
        ):
            self.assertTrue(
                forge_metadata.review_pull_request(
                    3513,
                    "/repo",
                    "https://github.com/oracle/graalvm-reachability-metadata/pull/3513",
                )
            )

        invocation = run_agent.call_args.kwargs
        selection = invocation["selection"]
        self.assertEqual(selection.backend, "codex")
        self.assertEqual(selection.agent, "codex")
        self.assertEqual(selection.model, "gpt-5.6-luna")
        self.assertEqual(selection.thinking_level, "high")
        self.assertIsNone(selection.provider)
        agent_environment = invocation["environment"]
        self.assertEqual(agent_environment, {"_FORGE_AGENT_ALLOW_GITHUB_ACCESS": "1"})

    def test_review_pull_request_rejects_agent_failure(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir, patch.object(
                forge_metadata,
                "create_review_workspace",
                return_value=temp_dir,
        ), patch.object(
            forge_metadata,
            "cleanup_review_workspace",
        ) as cleanup_review_workspace, patch.object(
            forge_metadata,
            "analysis_agent_run",
            return_value=AgentRunResult(
                1,
                os.path.join(temp_dir, "review.log"),
                True,
            ),
        ), patch.object(
            forge_metadata,
            "print_pull_request_discussion",
        ) as print_discussion:
            self.assertFalse(
                forge_metadata.review_pull_request(
                    3513,
                    "/repo",
                    "https://github.com/oracle/graalvm-reachability-metadata/pull/3513",
                )
            )

        cleanup_review_workspace.assert_called_once_with("/repo", temp_dir, 3513)
        print_discussion.assert_not_called()

    def test_merge_pull_request_validates_index_candidate_before_merge(self) -> None:
        pr = {
            "number": 3513,
            "url": "https://github.com/oracle/graalvm-reachability-metadata/pull/3513",
            "headRefOid": "abc123",
        }

        with patch.object(
                forge_metadata,
                "get_pull_request_changed_index_files",
                return_value=["metadata/org.example/demo/index.json"],
        ), patch.object(
                forge_metadata,
                "validate_index_files_on_current_master_candidate",
        ) as validate_candidate, patch.object(forge_metadata, "gh") as gh:
            forge_metadata.merge_pull_request(pr, "/repo")

        validate_candidate.assert_called_once_with(3513, "abc123", "/repo")
        gh.assert_called_once_with(
            "pr",
            "merge",
            "3513",
            "--repo",
            forge_metadata.REPO,
            "--match-head-commit",
            "abc123",
            "--squash",
        )

    def test_merge_pull_request_skips_index_validation_when_index_unchanged(self) -> None:
        pr = {
            "number": 3513,
            "url": "https://github.com/oracle/graalvm-reachability-metadata/pull/3513",
            "headRefOid": "abc123",
        }

        with patch.object(
                forge_metadata,
                "get_pull_request_changed_index_files",
                return_value=[],
        ), patch.object(
                forge_metadata,
                "validate_index_files_on_current_master_candidate",
        ) as validate_candidate, patch.object(forge_metadata, "gh") as gh:
            forge_metadata.merge_pull_request(pr, "/repo")

        validate_candidate.assert_not_called()
        gh.assert_called_once()

    def test_merge_pull_request_releases_non_final_chunked_dynamic_access_issue(self) -> None:
        pr = {
            "number": 3513,
            "url": "https://github.com/oracle/graalvm-reachability-metadata/pull/3513",
            "headRefOid": "abc123",
            "body": "Refs: #1412\n\nSummary:\n- Chunked dynamic-access: yes\n",
        }

        with patch.object(forge_metadata, "get_pull_request_changed_index_files", return_value=[]), \
                patch.object(forge_metadata, "gh"), \
                patch.object(forge_metadata, "get_project_item_id", return_value="project-item"), \
                patch.object(forge_metadata, "set_item_status") as set_item_status, \
                patch.object(forge_metadata, "clear_issue_assignees") as clear_issue_assignees, \
                patch.object(forge_metadata, "invalidate_issue_claim_cache_entry") as invalidate_cache:
            forge_metadata.merge_pull_request(pr, "/repo")

        set_item_status.assert_called_once_with("project-item", forge_metadata.STATUS_TODO)
        clear_issue_assignees.assert_called_once_with(1412)
        invalidate_cache.assert_called_once_with(1412)

    def test_merge_pull_request_does_not_release_final_chunked_dynamic_access_issue(self) -> None:
        pr = {
            "number": 3513,
            "url": "https://github.com/oracle/graalvm-reachability-metadata/pull/3513",
            "headRefOid": "abc123",
            "body": "Fixes: #1412\n\nSummary:\n- Chunked dynamic-access: yes\n",
        }

        with patch.object(forge_metadata, "get_pull_request_changed_index_files", return_value=[]), \
                patch.object(forge_metadata, "gh"), \
                patch.object(forge_metadata, "set_item_status") as set_item_status, \
                patch.object(forge_metadata, "clear_issue_assignees") as clear_issue_assignees:
            forge_metadata.merge_pull_request(pr, "/repo")

        set_item_status.assert_not_called()
        clear_issue_assignees.assert_not_called()

    def test_merge_pull_request_stops_when_final_index_validation_fails(self) -> None:
        pr = {
            "number": 3513,
            "url": "https://github.com/oracle/graalvm-reachability-metadata/pull/3513",
            "headRefOid": "abc123",
        }

        with patch.object(
                forge_metadata,
                "get_pull_request_changed_index_files",
                return_value=["metadata/org.example/demo/index.json"],
        ), patch.object(
                forge_metadata,
                "validate_index_files_on_current_master_candidate",
                side_effect=RuntimeError("invalid index"),
        ), patch.object(forge_metadata, "gh") as gh:
            with self.assertRaises(RuntimeError):
                forge_metadata.merge_pull_request(pr, "/repo")

        gh.assert_not_called()

    def test_get_pull_request_changed_index_files_filters_library_indexes(self) -> None:
        with patch.object(
                forge_metadata,
                "get_pull_request_changed_files",
                return_value=[
                    "metadata/org.example/demo/index.json",
                    "metadata/schemas/metadata-library-index-schema-v2.3.0.json",
                    "tests/src/org.example/demo/1.0.0/build.gradle",
                    "metadata/org.example/demo/1.0.0/reachability-metadata.json",
                ],
        ):
            self.assertEqual(
                forge_metadata.get_pull_request_changed_index_files(3513),
                ["metadata/org.example/demo/index.json"],
            )

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

    def test_reconcile_unapproved_pr_with_failed_ci_reruns_failed_jobs(self) -> None:
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
                    return_value=1,
                ) as rerun_failed_jobs, \
                patch.object(forge_metadata, "merge_pull_request") as merge_pull_request:
            self.assertTrue(forge_metadata.reconcile_reviewed_pull_request(3513))
        rerun_failed_jobs.assert_called_once_with(3513, "abc123")
        merge_pull_request.assert_not_called()

    def test_reconcile_approved_conflicting_pr_resolves_the_conflict_instead_of_merging(self) -> None:
        pr = {
            "number": 3513,
            "url": "https://github.com/oracle/graalvm-reachability-metadata/pull/3513",
            "headRefOid": "abc123",
            "headRefName": "ai/kimeta/demo",
            "reviewDecision": "APPROVED",
            "mergeable": "CONFLICTING",
            "mergeStateStatus": "DIRTY",
            "statusCheckRollup": {"state": "SUCCESS"},
        }

        with patch.object(forge_metadata, "get_pull_request_state", return_value=pr), \
                patch.object(
                    forge_metadata,
                    "resolve_pull_request_merge_conflict",
                    return_value=True,
                ) as resolve_conflict, \
                patch.object(forge_metadata, "merge_pull_request") as merge_pull_request:
            self.assertTrue(forge_metadata.reconcile_reviewed_pull_request(3513, "/tmp/reachability"))

        resolve_conflict.assert_called_once_with(pr, "/tmp/reachability")
        merge_pull_request.assert_not_called()

    def test_reconcile_conflicting_pr_refreshes_before_ci_succeeds(self) -> None:
        pr = {
            "number": 3513,
            "url": "https://github.com/oracle/graalvm-reachability-metadata/pull/3513",
            "headRefOid": "abc123",
            "headRefName": "ai/kimeta/demo",
            "reviewDecision": "APPROVED",
            "mergeable": "CONFLICTING",
            "mergeStateStatus": "DIRTY",
            "statusCheckRollup": {"state": "PENDING"},
        }

        with patch.object(forge_metadata, "get_pull_request_state", return_value=pr), \
                patch.object(
                    forge_metadata,
                    "resolve_pull_request_merge_conflict",
                ) as resolve_conflict, \
                patch.object(forge_metadata, "merge_pull_request") as merge_pull_request:
            self.assertTrue(forge_metadata.reconcile_reviewed_pull_request(3513, "/tmp/reachability"))
        resolve_conflict.assert_called_once_with(pr, "/tmp/reachability")
        merge_pull_request.assert_not_called()

    def test_resolve_pull_request_merge_conflict_leaves_fork_heads_alone(self) -> None:
        pr = {
            "number": 3513,
            "headRefOid": "abc123",
            "headRefName": "contributor-branch",
            "isCrossRepository": True,
        }

        with patch.object(forge_metadata, "create_detached_worktree") as create_detached_worktree:
            self.assertFalse(
                forge_metadata.resolve_pull_request_merge_conflict(pr, "/tmp/reachability")
            )

        create_detached_worktree.assert_not_called()

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

        with patch.object(forge_metadata, "run_git_transport", return_value=completed_process) as run:
            forge_metadata.fetch_review_base_ref("/repo")

        run.assert_called_once_with(
            [
                "fetch",
                "--quiet",
                "origin",
                "+master:refs/remotes/origin/master",
            ],
            cwd="/repo",
        )

    def test_review_prompt_uses_live_targeted_evidence_and_direct_submission(self) -> None:
        prompt = forge_metadata.build_review_prompt(3513)

        self.assertIn("submit the review directly", prompt)
        self.assertIn("gh pr view", prompt)
        self.assertIn("gh pr checks", prompt)
        self.assertIn("git diff --name-status origin/master...HEAD", prompt)
        self.assertIn("targeted diffs", prompt)
        self.assertIn("fix-index-file-inconsistencies", prompt)
        self.assertNotIn(".forge-review-context.json", prompt)

        self.assertNotIn("Forge will validate and submit", prompt)

if __name__ == "__main__":
    unittest.main()
