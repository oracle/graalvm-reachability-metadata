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
    parse_coordinate_parts,
    stage_and_commit as stage_and_commit_common,
    find_issue_for_coordinates as find_issue_common,
)
from git_scripts.branch_publication import (
    BASE_BRANCH,
    REPO,
    format_bounded_test_diff_section,
    publish_branch,
)
from git_scripts.publication_descriptor import descriptor_input_from_pending_metrics
from utility_scripts.metadata_index import resolve_test_version
from utility_scripts.metrics_writer import (
    count_metadata_entries,
    count_test_only_metadata_entries,
    collect_version_coverage_metrics,
)
from utility_scripts.library_stats import stats_artifact_dir
from utility_scripts.repo_path_resolver import resolve_repo_roots

SEVERE_METADATA_DROP_RATIO = 0.25
TEST_SOURCE_DIR_NAMES: tuple[str, ...] = ("java", "kotlin", "groovy", "scala")
DEFAULT_PR_LABEL = "fixes-native-image-run-fail"


def is_severe_metadata_drop(previous_entries: int, new_entries: int) -> bool:
    """Return true when a native-image-run fix drops most prior metadata entries."""
    return previous_entries > 0 and new_entries < previous_entries * SEVERE_METADATA_DROP_RATIO


def stage_and_commit(
        group: str,
        artifact: str,
        test_version: str,
        metadata_version: str,
        coordinates: str,
        repo_path: str,
) -> None:
    """Stage the expected files/directories and commit with the required message."""
    test_version_dir = os.path.join("tests", "src", group, artifact, test_version)
    test_native_image_metadata_dir = os.path.join(
        test_version_dir,
        "src",
        "test",
        "resources",
        "META-INF",
        "native-image",
    )
    candidate_paths = [
        str(os.path.join(test_version_dir, "build.gradle")),
        str(os.path.join("metadata", group, artifact, "index.json")),
        str(os.path.join("metadata", group, artifact, metadata_version)),
        str(os.path.relpath(stats_artifact_dir(repo_path, group, artifact), repo_path)),
    ]

    for test_source_dir_name in TEST_SOURCE_DIR_NAMES:
        test_sources_dir = os.path.join(test_version_dir, "src", "test", test_source_dir_name)
        if os.path.exists(os.path.join(repo_path, test_sources_dir)):
            candidate_paths.append(str(test_sources_dir))
    if os.path.exists(os.path.join(repo_path, test_native_image_metadata_dir)):
        candidate_paths.append(str(test_native_image_metadata_dir))

    user_code_filter = os.path.join(test_version_dir, "user-code-filter.json")
    if os.path.exists(os.path.join(repo_path, user_code_filter)):
        candidate_paths.append(str(user_code_filter))

    commit_message = f"Generated metadata for {coordinates}"
    stage_and_commit_common(candidate_paths, commit_message, cwd=repo_path)


def assert_no_tracked_worktree_changes(repo_path: str) -> None:
    """Fail with actionable paths when expected staging left tracked changes behind."""
    result = subprocess.run(
        ["git", "status", "--porcelain", "--untracked-files=no"],
        cwd=repo_path,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=True,
    )
    status_output = result.stdout.strip()
    if not status_output:
        return

    raise RuntimeError(
        "Native-image-run PR finalization left tracked worktree changes before rebase. "
        "Stage these paths in publish_ni_run_fix.py or discard them before finalization:\n"
        f"{status_output}"
    )


def build_test_comparison_section(group: str, artifact: str, old_version: str, new_version: str, repo_path: str) -> str:
    """Format an old-vs-new test diff when exploration produced a version-specific suite.

    The metadata-first seed keeps tests shared under the old version (no new-version
    test dir), so the diff section is emitted only when exploration split a
    version-specific suite (§WF-native-image-run-fix-workflow.3).
    """
    new_test_dir = os.path.join(repo_path, "tests", "src", group, artifact, new_version)
    if not os.path.isdir(new_test_dir):
        return ""
    return format_bounded_test_diff_section(group, artifact, old_version, new_version, repo_path)


def build_parser():
    parser = argparse.ArgumentParser(
        prog="publish_ni_run_fix.py",
        description=(
            f"Create and push a verified feature branch with Native Image run fixes and its publication "
            f"descriptor to '{REPO}'; trusted GitHub Actions open the pull request against "
            f"base branch '{BASE_BRANCH}'.\n\n"
        ),
        epilog=(
            "Example:\n"
            "  python3 git_scripts/publish_ni_run_fix.py \\\n"
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
    parser.add_argument(
        "--metrics-repo-path",
        dest="metrics_repo_path",
        help="Path to the metrics repository root.",
    )
    parser.add_argument("--issue-number", type=int, help="Explicit backing GitHub issue number.")
    parser.add_argument(
        "--pr-label",
        default=DEFAULT_PR_LABEL,
        help="Primary label to apply to the generated pull request.",
    )
    return parser


def parse_flags(argv_list):
    """Parse CLI flags and resolve repository path."""
    parser = build_parser()
    flags = parser.parse_args(argv_list)
    repo_path, metrics_repo_path = resolve_repo_roots(
        flags.reachability_metadata_path,
        flags.metrics_repo_path,
    )
    return flags.coordinates, flags.new_version, repo_path, metrics_repo_path, flags.issue_number, flags.pr_label


def push_current_branch_to_origin(
        old_coordinates: str,
        new_version: str,
        repo_path: str,
        metrics_repo_path: str | None = None,
        issue_number: int | None = None,
        pr_label: str = DEFAULT_PR_LABEL,
):
    """
    Switch to the feature branch, stage and commit changes,
    and push to the remote.
    """
    group, artifact, old_version = parse_coordinate_parts(old_coordinates)
    new_coordinates = f"{group}:{artifact}:{new_version}"
    if issue_number is None or metrics_repo_path is None:
        raise ValueError("Publication requires an issue number and metrics path")
    previous_coverage, _ = collect_version_coverage_metrics(
        repo_path, group, artifact, old_version,
    )
    new_coverage, _ = collect_version_coverage_metrics(
        repo_path, group, artifact, new_version,
    )
    descriptor_input = descriptor_input_from_pending_metrics(
        metrics_repo_path=metrics_repo_path,
        issue_number=issue_number,
        task_type=pr_label,
        template_type=DEFAULT_PR_LABEL,
        previous_coordinates=old_coordinates,
        render={
            "baseline_metadata_entries": count_metadata_entries(repo_path, group, artifact, old_version),
            "current_metadata_entries": count_metadata_entries(repo_path, group, artifact, new_version),
            "baseline_test_only_entries": count_test_only_metadata_entries(
                repo_path, group, artifact, old_version,
            ),
            "current_test_only_entries": count_test_only_metadata_entries(
                repo_path, group, artifact, new_version,
            ),
            "baseline_stats": {"coverage_percent": previous_coverage},
            "library_stats": {"coverage_percent": new_coverage},
        },
    )

    def stage() -> None:
        # Exploration splits the seed's shared entry into a version-specific one, so
        # the resolved test version is the new version; otherwise tests stay shared
        # under the old version (metadata-first seed).
        resolved_test_version = resolve_test_version(repo_path, group, artifact, new_version)
        stage_and_commit(
            group=group,
            artifact=artifact,
            test_version=resolved_test_version,
            metadata_version=new_version,
            coordinates=new_coordinates,
            repo_path=repo_path,
        )

    branch, local_ci_verification = publish_branch(
        repo_path=repo_path,
        branch_suffix=f"fix-native-image-run-{group}-{artifact}-{new_version}",
        coordinates=new_coordinates,
        stage=stage,
        metrics_repo_path=metrics_repo_path,
        before_rebase=lambda: assert_no_tracked_worktree_changes(repo_path),
        descriptor_input=descriptor_input,
    )

    return branch, group, artifact, new_coordinates, local_ci_verification


def main(argv=None):
    ensure_gh_authenticated()

    old_coordinates, new_version, repo_path, metrics_repo_path, issue_number, pr_label = parse_flags(
        argv if argv is not None else sys.argv[1:]
    )

    group, artifact, _old_version = parse_coordinate_parts(old_coordinates)
    new_coordinates = f"{group}:{artifact}:{new_version}"
    if issue_number is None:
        issue_number = find_issue_common(new_coordinates, REPO)
    push_current_branch_to_origin(
        old_coordinates=old_coordinates,
        new_version=new_version,
        repo_path=repo_path,
        metrics_repo_path=metrics_repo_path,
        issue_number=issue_number,
        pr_label=pr_label,
    )


if __name__ == "__main__":
    if any(a in ("-h", "--help") for a in sys.argv[1:]):
        build_parser().print_help()
        sys.exit(0)
    main()
