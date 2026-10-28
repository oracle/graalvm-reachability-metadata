# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Continue a chunked dynamic-access issue only after its previous chunk merged.

A later chunk loads the merged exhaust report, resolves the preceding pull
request by its exact head branch and publication ID, requires it merged, and
checks that its merge commit is an ancestor of the new base
(§FS-forge-chunked-dynamic-access).
"""
from __future__ import annotations

import subprocess
from typing import Any

from git_scripts.github_cli import gh_json
from utility_scripts.dynamic_access_exhaust_report import DynamicAccessExhaustReport

REPOSITORY: str = "oracle/graalvm-reachability-metadata"
PUBLICATION_TRAILER: str = "Forge-Publication-ID: "


def previous_chunk_pull_request(report: DynamicAccessExhaustReport) -> dict[str, Any] | None:
    """The pull request of the report's latest chunk, or `None` when no chunk was published."""
    if report.latest_chunk_publication_id is None and report.latest_chunk_branch is None:
        if report.latest_chunk_pull_request is None:
            return None
        return dict(gh_json(
            "pr", "view", str(report.latest_chunk_pull_request), "--repo", REPOSITORY,
            "--json", "number,state,body,mergeCommit",
        ))
    if report.latest_chunk_publication_id is None or report.latest_chunk_branch is None:
        raise RuntimeError("chunk publication identity requires both its ID and its branch")
    trailer: str = PUBLICATION_TRAILER + report.latest_chunk_publication_id
    matches: list[dict[str, Any]] = [
        pull_request
        for pull_request in gh_json(
            "pr", "list", "--repo", REPOSITORY, "--head", report.latest_chunk_branch, "--state", "all",
            "--limit", "100", "--json", "number,state,body,mergeCommit",
        )
        if trailer in str(pull_request.get("body") or "").splitlines()
    ]
    if len(matches) != 1:
        raise RuntimeError(
            f"expected one pull request for branch {report.latest_chunk_branch} and publication "
            f"{report.latest_chunk_publication_id}, found {len(matches)}; retry once the publisher has run"
        )
    return matches[0]


def require_previous_chunk_merged(report: DynamicAccessExhaustReport, worktree: str) -> None:
    """Raise unless the previous chunk merged and the worktree's base contains its merge commit."""
    pull_request: dict[str, Any] | None = previous_chunk_pull_request(report)
    commit: str | None = report.latest_chunk_commit
    if pull_request is not None:
        if pull_request.get("state") != "MERGED":
            raise RuntimeError(
                f"chunk pull request #{pull_request.get('number')} is {pull_request.get('state')}, not merged"
            )
        commit = (pull_request.get("mergeCommit") or {}).get("oid") or commit
    if commit and subprocess.run(
            ["git", "merge-base", "--is-ancestor", commit, "HEAD"], cwd=worktree, check=False,
    ).returncode != 0:
        raise RuntimeError(f"the base does not contain the previous chunk's commit {commit}")
