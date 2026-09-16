# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Shared fixtures and helpers for the dispatcher test files."""

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
from dispatcher import (
    env_config,
    issue_admin,
)
from utility_scripts.native_image_artifact import ARTIFACT_REPOSITORY_URLS
from utility_scripts.continuation_marker import ContinuationMarker
from utility_scripts.dynamic_access_exhaust_report import DynamicAccessExhaustReport
from utility_scripts.source_context_discovery import GradleBootstrapFailure
from ai_workflows.drivers.library_update_router import LibraryUpdateRoute
from ai_workflows.drivers.library_update_router import ROUTE_FIX_JAVAC
from ai_workflows.drivers.library_update_router import ROUTE_IMPROVE_COVERAGE
from ai_workflows.core.workflow_strategy import RUN_STATUS_FAILURE
from utility_scripts.continuation_marker import continuation_marker_path
from utility_scripts.dynamic_access_exhaust_report import dynamic_access_exhaust_report_path
from utility_scripts.run_location import format_run_failure_line
from utility_scripts.continuation_marker import load_continuation_marker
import random
import time
from dispatcher import (
    claim_setup,
    config,
    driver_invocation,
    dynamic_access,
    failure_preservation,
    interrupts,
    issue_form,
    issue_processing,
    lifecycle,
    pipeline_execution,
    publication,
    queue_config,
    records,
)
from dispatcher import (
    ci_repair,
    claim_preflight,
    continuation,
    failure_follow_up,
    human_intervention,
    fixture_support,
    github_api,
    issue_cache,
    issue_claiming,
    issue_queue,
    pr_merge,
    pr_publication,
    pr_state,
    project_board,
    review_loop,
    worktrees,
)
from types import SimpleNamespace
from ai_workflows.agents.agent_runtime import AgentRunResult, AgentSelection
from git_scripts import common_git, github_cli
from utility_scripts import host_graalvm_checks, host_requirements, run_location
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
                                            "field": {"name": config.STATUS_FIELD_NAME},
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


def _scan_state(scanned_count: int = 0, exhausted: bool = False) -> issue_queue.IssueQueueScanState:
    """Build the scan state a `get_prioritized_issues_with_label` stub would return."""
    return issue_queue.IssueQueueScanState(
        tier_index=len(issue_queue.ISSUE_PRIORITY_TIERS) if exhausted else 0,
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
        "author": {"login": config.TRUSTED_FORGE_PUBLISHER_LOGIN},
        "headRepository": {"nameWithOwner": config.REPO},
        "body": "Forge-Publication-ID: forge-9962-test",
        "isCrossRepository": False,
        "state": "OPEN",
        "reviewDecision": "REVIEW_REQUIRED",
        "autoMergeRequest": None,
        "isMergeQueueEnabled": False,
        "repository": {
            "squashMergeAllowed": True,
            "mergeCommitAllowed": True,
            "rebaseMergeAllowed": True,
        },
        "mergeable": mergeable,
        "mergeStateStatus": "CLEAN" if mergeable == "MERGEABLE" else "DIRTY",
        "statusCheckRollup": {"state": ci_state},
    }


def _validated_publication(
        decision: str = "approved",
        action: str | None = None,
        task_type: str = "library-new-request",
) -> SimpleNamespace:
    review = {
        "decision": decision,
        "review_comment": "Checked.",
        "finding_title": "" if decision == "approved" else "Finding",
        "finding_body": "" if decision == "approved" else "Reason.",
        "fix_note": "",
        "model": "test-model",
        "session_id": "a1b2c3d4e5f60718",
        "changed_paths": [],
    }
    if action is not None:
        review["action"] = action
    descriptor = {
        "task_type": task_type,
        "publication_id": "forge-9962-test",
        "issue_number": 9962,
        "local_review": review,
        "library": {
            "group": "org.example",
            "artifact": "demo",
            "version": "1.0",
            "coordinates": "org.example:demo:1.0",
        },
    }
    if task_type == "code-coverage-benchmark-result":
        descriptor.pop("local_review")
    return SimpleNamespace(descriptor=descriptor)



def _preflight(
        *,
        issue_number: int = 1412,
        item_id: str | None = "project-item",
        project_status: str | None = config.STATUS_TODO,
        assignees: tuple[str, ...] = (),
        open_blockers: tuple[int, ...] = (),
        complete: bool = True,
) -> records.IssueClaimPreflight:
    return records.IssueClaimPreflight(
        issue_number=issue_number,
        item_id=item_id,
        project_status=project_status,
        assignees=assignees,
        open_blockers=open_blockers,
        complete=complete,
    )


def _claimed_issue(label: str = forge_metadata.LABEL_LIBRARY_NEW) -> records.ClaimedIssue:
    return records.ClaimedIssue(
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


def _claimed_issue_in(base_path: str, label: str = forge_metadata.LABEL_LIBRARY_NEW) -> records.ClaimedIssue:
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
        project_status: str = config.STATUS_TODO,
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




__all__ = [
    "contextlib",
    "dataclasses",
    "io",
    "json",
    "os",
    "subprocess",
    "sys",
    "tempfile",
    "unittest",
    "Callable",
    "call",
    "patch",
    "forge_metadata",
    "env_config",
    "issue_admin",
    "ARTIFACT_REPOSITORY_URLS",
    "ContinuationMarker",
    "DynamicAccessExhaustReport",
    "GradleBootstrapFailure",
    "LibraryUpdateRoute",
    "ROUTE_FIX_JAVAC",
    "ROUTE_IMPROVE_COVERAGE",
    "RUN_STATUS_FAILURE",
    "continuation_marker_path",
    "dynamic_access_exhaust_report_path",
    "format_run_failure_line",
    "load_continuation_marker",
    "random",
    "time",
    "claim_setup",
    "config",
    "driver_invocation",
    "dynamic_access",
    "failure_preservation",
    "interrupts",
    "issue_form",
    "issue_processing",
    "lifecycle",
    "pipeline_execution",
    "publication",
    "queue_config",
    "records",
    "ci_repair",
    "claim_preflight",
    "continuation",
    "failure_follow_up",
    "human_intervention",
    "fixture_support",
    "github_api",
    "issue_cache",
    "issue_claiming",
    "issue_queue",
    "pr_merge",
    "pr_publication",
    "pr_state",
    "project_board",
    "review_loop",
    "worktrees",
    "SimpleNamespace",
    "AgentRunResult",
    "AgentSelection",
    "common_git",
    "github_cli",
    "host_graalvm_checks",
    "host_requirements",
    "run_location",
    "PHASE_EXPLORE",
    "PHASE_FINALIZATION",
    "PHASE_PUBLICATION",
    "PHASE_SETUP",
    "FixtureComment",
    "FixtureGitHubState",
    "FixtureIssue",
    "DynamicAccessClass",
    "DynamicAccessCoverageReport",
    "PENDING_METRICS_FILENAME",
    "_project_item_status_response",
    "_empty_preflight_response",
    "_search_issue",
    "_scan_state",
    "_pull_request",
    "_pull_request_state",
    "_validated_publication",
    "_preflight",
    "_claimed_issue",
    "_claimed_issue_in",
    "_dynamic_access_report",
    "_form_issue",
    "_write_index",
    "_fixture_form_issue",
]
