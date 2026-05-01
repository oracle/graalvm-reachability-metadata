# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import argparse
import os
import subprocess
import sys

from git_scripts.common_git import (
    ensure_gh_authenticated,
    gh,
    parse_coordinate_parts,
    get_origin_owner,
    stage_and_commit as stage_and_commit_common,
    find_issue_for_coordinates as find_issue_common,
    format_stats_diff,
    format_forge_revision_section,
    build_ai_branch_name,
    delete_remote_branch_if_exists,
    get_configured_reviewers,
)
from utility_scripts.metrics_writer import (
    count_metadata_entries,
    count_test_only_metadata_entries,
    collect_version_coverage_metrics,
)
from utility_scripts.library_stats import stats_artifact_dir
from utility_scripts.repo_path_resolver import add_in_metadata_repo_argument, resolve_repo_roots

REPO = "oracle/graalvm-reachability-metadata"
BASE_BRANCH = 'master'
REVIEWERS = get_configured_reviewers()


def resolve_repo_path(explicit_repo_path: str | None, in_metadata_repo: bool = True) -> str:
    """Resolve repo path using provided value or local default."""
    repo_path, _ = resolve_repo_roots(
        explicit_repo_path,
        None,
        in_metadata_repo=in_metadata_repo,
    )
    return repo_path


def stage_and_commit(
        group: str,
        artifact: str,
        test_version: str,
        metadata_version: str,
        coordinates: str,
        repo_path: str,
):
    """Stage the expected files/directories and commit with the required message."""
    test_version_dir = os.path.join("tests", "src", group, artifact, test_version)
    test_sources_dir = os.path.join(test_version_dir, "src", "test", "java")
    candidate_paths = [
        str(os.path.join(test_version_dir, "build.gradle")),
        str(os.path.join("metadata", group, artifact, "index.json")),
        str(os.path.join("metadata", group, artifact, metadata_version)),
        str(os.path.relpath(stats_artifact_dir(repo_path, group, artifact), repo_path)),
    ]

    if os.path.exists(os.path.join(repo_path, test_sources_dir)):
        candidate_paths.append(str(test_sources_dir))

    user_code_filter = os.path.join(test_version_dir, "user-code-filter.json")
    if os.path.exists(os.path.join(repo_path, user_code_filter)):
        candidate_paths.append(str(user_code_filter))

    commit_message = f"Generated metadata for {coordinates}"
    stage_and_commit_common(candidate_paths, commit_message, cwd=repo_path)


def create_pull_request(
        branch: str,
        old_coordinates: str,
        new_coordinates: str,
        group: str,
        artifact: str,
        repo_path: str,
):
    """Create a GitHub pull request for the current branch."""
    origin_owner = get_origin_owner(cwd=repo_path)

    # If a PR already exists for this branch, do nothing
    view = gh("pr", "view", "--repo", REPO, "--head", branch, check=False)
    if view.returncode == 0:
        print(f"Pull request already exists for branch {branch}.")
        return

    issue_no = find_issue_common(new_coordinates, REPO)

    _, _, old_version = parse_coordinate_parts(old_coordinates)
    _, _, new_version = parse_coordinate_parts(new_coordinates)

    new_entries = count_metadata_entries(repo_path, group, artifact, new_version)
    previous_entries = count_metadata_entries(repo_path, group, artifact, old_version)
    new_test_entries = count_test_only_metadata_entries(repo_path, group, artifact, new_version)
    previous_test_entries = count_test_only_metadata_entries(repo_path, group, artifact, old_version)
    previous_coverage, _ = collect_version_coverage_metrics(repo_path, group, artifact, old_version)
    new_coverage, _ = collect_version_coverage_metrics(repo_path, group, artifact, new_version)

    stats_section = format_stats_diff(repo_path, old_coordinates, new_coordinates)

    previous_test_entries_line = ""
    if previous_test_entries > 0:
        previous_test_entries_line = (
            f"- Test-only metadata entries (previous `{old_coordinates}`): {previous_test_entries}\n"
        )
    new_test_entries_line = ""
    if new_test_entries > 0:
        new_test_entries_line = f"- Test-only metadata entries (new `{new_coordinates}`): {new_test_entries}\n"

    metrics_section = (
        f"\n\n"
        f"- Metadata entries (previous `{old_coordinates}`): {previous_entries}\n"
        f"{previous_test_entries_line}"
        f"- Metadata entries (new `{new_coordinates}`): {new_entries}\n"
        f"{new_test_entries_line}"
        f"- Library coverage (previous): {previous_coverage:.2f}%\n"
        f"- Library coverage (new): {new_coverage:.2f}%"
    )

    title = f"[Automation] Generated metadata for {new_coordinates}"
    body = (
        f"Fixes: {REPO}#{issue_no}\n\n"
        f"This PR provides new metadata needed for the {new_coordinates}, "
        f"addressing Native Image run failures caused by changes in the updated library version."
        f"{metrics_section}"
        f"\n\n{format_forge_revision_section()}"
        f"{stats_section}"
    )

    cmd = [
        "gh", "pr", "create",
        "--repo", REPO,
        "--title", title,
        "--body", body,
        "--base", BASE_BRANCH,
        "--head", f"{origin_owner}:{branch}",
        "--label", "fixes-native-image-run-fail",
    ]
    for r in REVIEWERS:
        cmd.extend(["--reviewer", r])
    gh(*cmd[1:])



def build_parser():
    parser = argparse.ArgumentParser(
        prog="make_pr_ni_run_fix.py",
        description=(
            f"Create and push a feature branch with Native Image run fixes and open a GitHub Pull Request "
            f"on your fork against base branch '{BASE_BRANCH}'.\n\n"
        ),
        epilog=(
            "Example:\n"
            "  python3 git_scripts/make_pr_ni_run_fix.py \\\n"
            "      --coordinates com.example:lib:1.2.3 \\\n"
            "      --new-version 1.2.4 \\\n"
            "      --reachability-metadata-path /path/to/graalvm-reachability-metadata\n\n"
            "Notes:\n"
            "  - Requires the 'gh' CLI configured and authenticated with access to the target repository.\n"
        ),
        formatter_class=argparse.RawTextHelpFormatter,
    )
    parser.add_argument(
        "--coordinates",
        required=True,
        help="Maven coordinates Group:Artifact:Version for the current library version",
    )
    parser.add_argument(
        "--new-version",
        required=True,
        help="Target library version which was fixed by the workflow",
    )
    parser.add_argument(
        "--reachability-metadata-path",
        help=(
            "Path to the reachability-metadata repository to operate on. "
            "If omitted, the parent checkout of this Forge directory is used."
        ),
    )
    add_in_metadata_repo_argument(parser)
    return parser


def parse_flags(argv_list):
    """Parse CLI flags and resolve repository path."""
    parser = build_parser()
    flags = parser.parse_args(argv_list)
    repo_path = resolve_repo_path(flags.reachability_metadata_path, in_metadata_repo=flags.in_metadata_repo)
    return flags.coordinates, flags.new_version, repo_path


def push_current_branch_to_origin(
        old_coordinates: str,
        new_version: str,
        repo_path: str,
):
    """
    Switch to the feature branch, stage and commit changes,
    and push to the remote.
    """
    group, artifact, old_version = parse_coordinate_parts(old_coordinates)
    new_coordinates = f"{group}:{artifact}:{new_version}"

    branch = build_ai_branch_name(
        f"fix-native-image-run-{group}-{artifact}-{new_version}",
        cwd=repo_path,
    )
    delete_remote_branch_if_exists(branch, cwd=repo_path)
    subprocess.run(
        ["git", "switch", "-C", branch],
        cwd=repo_path,
        check=True,
    )
    stage_and_commit(
        group=group,
        artifact=artifact,
        test_version=old_version,
        metadata_version=new_version,
        coordinates=new_coordinates,
        repo_path=repo_path,
    )

    subprocess.run(
        ["git", "push", "origin", branch],
        cwd=repo_path,
        check=True,
    )

    return branch, group, artifact, new_coordinates


def main(argv=None):
    ensure_gh_authenticated()

    old_coordinates, new_version, repo_path = parse_flags(argv if argv is not None else sys.argv[1:])

    branch, group, artifact, new_coordinates = push_current_branch_to_origin(
        old_coordinates=old_coordinates,
        new_version=new_version,
        repo_path=repo_path,
    )
    create_pull_request(
        branch=branch,
        old_coordinates=old_coordinates,
        new_coordinates=new_coordinates,
        group=group,
        artifact=artifact,
        repo_path=repo_path,
    )


if __name__ == "__main__":
    if any(a in ("-h", "--help") for a in sys.argv[1:]):
        build_parser().print_help()
        sys.exit(0)
    main()
