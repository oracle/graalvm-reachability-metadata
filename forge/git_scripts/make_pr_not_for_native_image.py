# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Open a PR that records an artifact as not applicable to Native Image."""

import argparse
import os
import shutil
import subprocess

from git_scripts.common_git import (
    build_ai_branch_name,
    delete_remote_branch_if_exists,
    ensure_gh_authenticated,
    find_issue_for_coordinates,
    format_forge_revision_section,
    get_configured_reviewers,
    get_origin_owner,
    gh,
    parse_coordinate_parts,
    stage_and_commit,
)
from utility_scripts.metadata_index import get_not_for_native_image_marker
from utility_scripts.local_ci_verification import (
    HUMAN_INTERVENTION_LABEL,
    LocalCIVerificationResult,
    fetch_pr_base_ref,
    format_local_ci_verification_pr_section,
    local_ci_requires_human_intervention,
    run_local_ci_verification,
)
from utility_scripts.repo_path_resolver import resolve_repo_roots


REPO = "oracle/graalvm-reachability-metadata"
BASE_BRANCH = "master"
REVIEWERS = get_configured_reviewers()


def build_parser() -> argparse.ArgumentParser:
    """Build CLI parser."""
    parser = argparse.ArgumentParser(
        prog="make_pr_not_for_native_image.py",
        description="Create a PR that adds a not-for-native-image marker index.",
    )
    parser.add_argument("--coordinates", required=True, help="Coordinates in group:artifact:version form")
    parser.add_argument("--reachability-metadata-path", help="Path to the reachability-metadata checkout")
    parser.add_argument(
        "--metrics-repo-path",
        default=None,
        help="Path to the metrics repository. If omitted, the forge directory in the selected worktree is used.",
    )
    return parser


def fetch_pr_base(repo_path: str) -> str:
    """Fetch the upstream PR base and return the ref to rebase onto."""
    return fetch_pr_base_ref(repo_path, REPO, BASE_BRANCH)


def push_marker_branch(
        coordinates: str,
        repo_path: str,
        metrics_repo_path: str | None = None,
) -> tuple[str, LocalCIVerificationResult]:
    """Create, commit, rebase, and push the marker branch."""
    group, artifact, _version = parse_coordinate_parts(coordinates)
    branch = build_ai_branch_name(f"not-for-native-image-{group}-{artifact}", cwd=repo_path)
    delete_remote_branch_if_exists(branch, cwd=repo_path)
    subprocess.run(["git", "switch", "-C", branch], check=True, cwd=repo_path)
    stage_and_commit(
        [os.path.join("metadata", group, artifact, "index.json")],
        f"Mark {group}:{artifact} as not for Native Image",
        cwd=repo_path,
    )
    base_ref = fetch_pr_base(repo_path)
    subprocess.run(["git", "rebase", base_ref], check=True, cwd=repo_path)
    local_ci_verification = run_local_ci_verification(
        repo_path=repo_path,
        coordinates=coordinates,
        base_commit=base_ref,
        metrics_repo_path=metrics_repo_path,
    )
    subprocess.run(["git", "push", "origin", branch], check=True, cwd=repo_path)
    return branch, local_ci_verification


def create_pull_request(
        branch: str,
        coordinates: str,
        repo_path: str,
        local_ci_verification: LocalCIVerificationResult | None = None,
) -> None:
    """Create the marker PR."""
    if shutil.which("gh") is None:
        print("gh CLI not found. Skipping PR creation.")
        return

    group, artifact, _version = parse_coordinate_parts(coordinates)
    marker = get_not_for_native_image_marker(repo_path, group, artifact)
    if marker is None:
        raise ValueError(f"Missing not-for-native-image marker for {group}:{artifact}")

    origin_owner = get_origin_owner(cwd=repo_path)
    view = gh("pr", "view", "--repo", REPO, "--head", f"{origin_owner}:{branch}", check=False)
    if view.returncode == 0:
        print(f"Pull request already exists for branch {branch}.")
        return

    issue_no = find_issue_for_coordinates(coordinates, REPO)
    title = f"[GenAI] Mark {group}:{artifact} as not for Native Image"
    body = f"""
## What does this PR do?

Fixes: #{issue_no}

This PR records `{group}:{artifact}` as `not-for-native-image`, so automation and downstream tools know this artifact is intentionally not a GraalVM Native Image reachability metadata target.

Reason:
- {marker.get("reason")}
"""
    replacement = marker.get("replacement")
    if replacement:
        body += f"\nReplacement guidance:\n- {replacement}\n"
    body += "\n" + format_forge_revision_section()
    local_ci_metrics = None if local_ci_verification is None else local_ci_verification.to_metrics()
    body += format_local_ci_verification_pr_section(local_ci_metrics)

    cmd = [
        "gh", "pr", "create",
        "--repo", REPO,
        "--title", title,
        "--body", body,
        "--base", BASE_BRANCH,
        "--head", f"{origin_owner}:{branch}",
        "--label", "GenAI",
        "--label", "library-new-request",
        "--label", "not-for-native-image",
    ]
    if local_ci_requires_human_intervention(local_ci_metrics):
        cmd.extend(["--label", HUMAN_INTERVENTION_LABEL])
    for reviewer in REVIEWERS:
        cmd.extend(["--reviewer", reviewer])
    gh(*cmd[1:])


def main(argv=None) -> None:
    """Run the marker PR flow."""
    args = build_parser().parse_args(argv)
    repo_path, metrics_repo_path = resolve_repo_roots(
        args.reachability_metadata_path,
        args.metrics_repo_path,
    )
    ensure_gh_authenticated()
    branch, local_ci_verification = push_marker_branch(args.coordinates, repo_path, metrics_repo_path)
    create_pull_request(branch, args.coordinates, repo_path, local_ci_verification)


if __name__ == "__main__":
    main()
