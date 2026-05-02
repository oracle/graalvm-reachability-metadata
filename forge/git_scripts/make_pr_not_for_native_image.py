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
from utility_scripts.repo_path_resolver import add_in_metadata_repo_argument, resolve_repo_roots


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
    add_in_metadata_repo_argument(parser)
    return parser


def fetch_pr_base(repo_path: str) -> str:
    """Fetch the upstream PR base and return the ref to rebase onto."""
    upstream_url = f"https://github.com/{REPO}.git"
    upstream_remote_url = subprocess.run(
        ["git", "remote", "get-url", "upstream"],
        cwd=repo_path,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        text=True,
        check=False,
    )
    if upstream_remote_url.returncode == 0 and REPO in upstream_remote_url.stdout.strip():
        subprocess.run(["git", "fetch", "upstream", BASE_BRANCH], check=True, cwd=repo_path)
        return f"upstream/{BASE_BRANCH}"

    subprocess.run(["git", "fetch", upstream_url, BASE_BRANCH], check=True, cwd=repo_path)
    return "FETCH_HEAD"


def push_marker_branch(coordinates: str, repo_path: str) -> str:
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
    subprocess.run(["git", "push", "origin", branch], check=True, cwd=repo_path)
    return branch


def create_pull_request(branch: str, coordinates: str, repo_path: str) -> None:
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
    for reviewer in REVIEWERS:
        cmd.extend(["--reviewer", reviewer])
    gh(*cmd[1:])


def main(argv=None) -> None:
    """Run the marker PR flow."""
    args = build_parser().parse_args(argv)
    repo_path, _metrics_path = resolve_repo_roots(
        args.reachability_metadata_path,
        None,
        in_metadata_repo=args.in_metadata_repo,
    )
    ensure_gh_authenticated()
    branch = push_marker_branch(args.coordinates, repo_path)
    create_pull_request(branch, args.coordinates, repo_path)


if __name__ == "__main__":
    main()
