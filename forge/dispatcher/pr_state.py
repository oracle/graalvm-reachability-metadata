# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Pull request state, reviews, publication validation, and merge readiness
(§AR-forge-dispatcher-decomposition, §FS-automated-pr-review)."""


import contextlib
import importlib
import os
import subprocess
from git_scripts.common_git import run_git_transport
from typing import Any
from dispatcher.config import (
    BENCHMARK_PUBLICATION_TASK_TYPE,
    DEFAULT_WORKTREE_BASE_REF,
    FAILED_CI_STATES,
    FORGE_APPROVAL_BODY_PREFIX,
    LABEL_FORGE_MERGE_FOLLOW_UP,
    LABEL_HUMAN_INTERVENTION,
    LABEL_HUMAN_INTERVENTION_FIXED,
    LABEL_LIBRARY_UNSUPPORTED_VERSION,
    LOCAL_REVIEW_CLOSE_MARKER,
    REPO,
    RERUNNABLE_WORKFLOW_RUN_CONCLUSIONS,
    SUPPORTED_FORGE_REVIEW_TASK_TYPES,
    TRUSTED_FORGE_PUBLISHER_LOGIN,
)
from dispatcher.github_api import (
    gh,
    gh_json,
)
from dispatcher.issue_admin import (
    add_issue_label,
    add_pull_request_label,
    close_issue,
    get_issue_comments,
    post_issue_comment,
    remove_pull_request_label,
)
from dispatcher.issue_queue import issue_has_label

import sys

def get_pull_requests_with_label(
        label: str,
        fetch_limit: int,
        state: str = "open",
) -> list[dict]:
    """Fetch pull requests in one state that carry the given label."""
    return get_pull_requests_with_labels([label], fetch_limit, state=state)


def get_pull_requests_with_labels(
        labels: list[str],
        fetch_limit: int,
        state: str = "open",
) -> list[dict]:
    """Fetch pull requests in one state that carry all given labels."""
    unique_labels = list(dict.fromkeys(labels))
    label_args: list[str] = []
    for label in unique_labels:
        label_args.extend(["--label", label])
    label_description = "', '".join(unique_labels)
    print(
        f"\n[Fetching {state} pull requests with label(s) '{label_description}' from {REPO} "
        f"(fetched={fetch_limit})]"
    )
    data = gh_json(
        "pr", "list",
        "--repo", REPO,
        *label_args,
        "--state", state,
        "--limit", str(fetch_limit),
        "--json", "number,title,url,author,labels,body,headRefOid,mergedAt",
    )
    return data




def attach_pull_request_state(
        pull_request: dict,
        state_cache: dict[int, dict],
) -> dict:
    """Return a pull request payload enriched with review, merge, and CI state."""
    pr_number = pull_request.get("number")
    if not isinstance(pr_number, int):
        print("ERROR: Missing pull request number while fetching state.", file=sys.stderr)
        raise RuntimeError("Missing pull request number while fetching state")

    if pr_number not in state_cache:
        state_cache[pr_number] = get_pull_request_state(pr_number)

    enriched_pull_request = dict(pull_request)
    enriched_pull_request.update(state_cache[pr_number])
    return enriched_pull_request


def pull_request_has_label(pr: dict, label_name: str) -> bool:
    """Return True when the GitHub pull request payload contains the given label."""
    return issue_has_label(pr, label_name)


def get_pull_request_state(pr_number: int) -> dict:
    """Fetch the latest review and merge state for a pull request."""
    owner, repo_name = REPO.split("/")
    query = f"""
    query {{
      repository(owner: "{owner}", name: "{repo_name}") {{
        pullRequest(number: {pr_number}) {{
          id
          number
          url
          body
          author {{
            login
          }}
          headRepository {{
            nameWithOwner
          }}
          headRefOid
          headRefName
          isCrossRepository
          state
          mergedAt
          reviewDecision
          mergeStateStatus
          mergeable
          isMergeQueueEnabled
          autoMergeRequest {{
            enabledAt
          }}
          statusCheckRollup {{
            state
            contexts(first: 100) {{
              nodes {{
                __typename
                ... on CheckRun {{
                  name
                  status
                  conclusion
                  checkSuite {{
                    app {{
                      slug
                    }}
                    commit {{
                      oid
                    }}
                    workflowRun {{
                      event
                      workflow {{
                        name
                      }}
                      file {{
                        path
                      }}
                    }}
                  }}
                }}
              }}
            }}
          }}
          repository {{
            viewerDefaultMergeMethod
            mergeCommitAllowed
            rebaseMergeAllowed
            squashMergeAllowed
          }}
        }}
      }}
    }}
    """
    result = gh_json("api", "graphql", "-f", f"query={query}")
    pull_request = (
        result.get("data", {})
        .get("repository", {})
        .get("pullRequest", {})
    )
    if not isinstance(pull_request, dict) or not pull_request:
        print(f"ERROR: Missing state for pull request #{pr_number}.", file=sys.stderr)
        raise RuntimeError(f"Missing state for pull request #{pr_number}")
    return pull_request


def get_pull_request_reviews(pr_number: int) -> list[dict]:
    """Fetch submitted reviews for a pull request."""
    reviews = gh_json(
        "api",
        "--method",
        "GET",
        f"/repos/{REPO}/pulls/{pr_number}/reviews",
        "-F",
        "per_page=100",
    )
    if not isinstance(reviews, list):
        print(f"ERROR: Missing reviews for pull request #{pr_number}.", file=sys.stderr)
        raise RuntimeError(f"Missing reviews for pull request #{pr_number}")
    return reviews


def dismiss_requested_changes_reviews(pr_number: int, message: str | None = None) -> int:
    """Dismiss all requested-changes reviews on a pull request."""
    dismiss_message = (
        message
        or f"Dismissed because trusted maintainer marked this PR as '{LABEL_HUMAN_INTERVENTION_FIXED}'."
    )
    dismissed_count = 0
    for review in get_pull_request_reviews(pr_number):
        if not isinstance(review, dict) or review.get("state") != "CHANGES_REQUESTED":
            continue

        review_id = review.get("id")
        if not isinstance(review_id, int):
            print(
                f"ERROR: Missing review id for requested-changes review on PR #{pr_number}.",
                file=sys.stderr,
            )
            raise RuntimeError(f"Missing review id for requested-changes review on PR #{pr_number}")

        gh(
            "api",
            "--method",
            "PUT",
            f"/repos/{REPO}/pulls/{pr_number}/reviews/{review_id}/dismissals",
            "-f",
            f"message={dismiss_message}",
        )
        dismissed_count += 1

    return dismissed_count


def has_successful_pull_request_ci(pr: dict) -> bool:
    """Return True when the pull request's combined CI status is successful."""
    status_check_rollup = pr.get("statusCheckRollup")
    ci_state = status_check_rollup.get("state") if isinstance(status_check_rollup, dict) else None
    return ci_state == "SUCCESS"


def has_failed_pull_request_ci(pr: dict) -> bool:
    """Return True when the pull request's combined CI status is failed."""
    status_check_rollup = pr.get("statusCheckRollup")
    ci_state = status_check_rollup.get("state") if isinstance(status_check_rollup, dict) else None
    return ci_state in FAILED_CI_STATES


def get_pull_request_workflow_runs(head_sha: str) -> list[dict]:
    """Return GitHub Actions workflow runs for the pull request head commit."""
    data = gh_json(
        "api",
        "--method",
        "GET",
        f"/repos/{REPO}/actions/runs",
        "-F",
        f"head_sha={head_sha}",
        "-F",
        "event=pull_request",
        "-F",
        "per_page=100",
    )
    workflow_runs = data.get("workflow_runs") if isinstance(data, dict) else None
    if not isinstance(workflow_runs, list):
        print(f"ERROR: Missing workflow runs for pull request head {head_sha}.", file=sys.stderr)
        raise RuntimeError(f"Missing workflow runs for pull request head {head_sha}")
    return workflow_runs


def get_failed_workflow_run_ids(workflow_runs: list[dict]) -> list[int]:
    """Return failed run IDs from exact-head evidence. §FS-automated-pr-review"""
    return [
        int(workflow_run["id"])
        for workflow_run in workflow_runs
        if (
            isinstance(workflow_run, dict)
            and isinstance(workflow_run.get("id"), int)
            and workflow_run.get("conclusion") in RERUNNABLE_WORKFLOW_RUN_CONCLUSIONS
        )
    ]


def rerun_failed_pull_request_workflow_jobs(
        pr_number: int,
        head_sha: str,
        requested_run_ids: tuple[int, ...],
) -> int:
    """Rerun only agent-selected failed jobs on this head. §FS-automated-pr-review"""
    pull_request = get_pull_request_state(pr_number)
    if (
            pull_request.get("headRefOid") != head_sha
            or pull_request.get("state") not in {None, "OPEN"}
    ):
        print(
            f"[Skipping transient rerun for PR #{pr_number}: "
            f"{head_sha} is no longer its open head.]"
        )
        return 0
    workflow_runs = get_pull_request_workflow_runs(head_sha)
    failed_run_ids = set(get_failed_workflow_run_ids(workflow_runs))
    stale_run_ids = [run_id for run_id in requested_run_ids if run_id not in failed_run_ids]
    if stale_run_ids:
        print(
            f"[Skipping transient rerun for PR #{pr_number}: workflow run(s) "
            f"{', '.join(str(run_id) for run_id in stale_run_ids)} are no longer failed "
            f"on head {head_sha}.]"
        )
        return 0

    for run_id in requested_run_ids:
        print(
            f"[Rerunning agent-classified transient jobs for PR #{pr_number}, "
            f"workflow run {run_id}.]"
        )
        gh(
            "api",
            "--method",
            "POST",
            f"/repos/{REPO}/actions/runs/{run_id}/rerun-failed-jobs",
        )

    return len(requested_run_ids)


def resolve_pull_request_merge_flag(pr: dict) -> str:
    """Resolve the merge method flag, preferring squash merges by default."""
    repository = pr.get("repository")
    if not isinstance(repository, dict):
        return "--squash"

    merge_flag_by_method = {
        "SQUASH": ("squashMergeAllowed", "--squash"),
        "MERGE": ("mergeCommitAllowed", "--merge"),
        "REBASE": ("rebaseMergeAllowed", "--rebase"),
    }
    for method in ("SQUASH", "MERGE", "REBASE"):
        allowed_key, merge_flag = merge_flag_by_method[method]
        if repository.get(allowed_key):
            return merge_flag

    return "--squash"


def is_metadata_index_file_path(path: str) -> bool:
    """Return True when a repository path is a library index file."""
    parts = path.split("/")
    return len(parts) == 4 and parts[0] == "metadata" and parts[3] == "index.json"


def get_pull_request_changed_files(pr_number: int) -> list[str]:
    """Return changed file paths for a pull request."""
    result = gh(
        "pr",
        "diff",
        str(pr_number),
        "--repo",
        REPO,
        "--name-only",
        quiet=True,
    )
    return [line.strip() for line in result.stdout.splitlines() if line.strip()]


def get_pull_request_changed_index_files(pr_number: int) -> list[str]:
    """Return changed library index files for a pull request."""
    return [
        path for path in get_pull_request_changed_files(pr_number)
        if is_metadata_index_file_path(path)
    ]

def get_pull_request_url(pr_number: int) -> str:
    """Resolve the GitHub URL for the target pull request."""
    pull_request = gh_json(
        "pr",
        "view",
        str(pr_number),
        "--repo",
        REPO,
        "--json",
        "url",
    )
    pull_request_url = pull_request.get("url")
    if not isinstance(pull_request_url, str) or not pull_request_url:
        print(f"ERROR: Missing URL for pull request #{pr_number}.", file=sys.stderr)
        raise RuntimeError(f"Missing URL for pull request #{pr_number}")
    return pull_request_url
