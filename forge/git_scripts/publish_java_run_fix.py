# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import argparse
import sys

from git_scripts.common_git import (
    ensure_gh_authenticated,
    parse_coordinate_parts,
    find_issue_for_coordinates as find_issue_common,
)
from git_scripts.branch_publication import (
    BASE_BRANCH,
    REPO,
    publish_branch,
    stage_library_version_paths,
)
from git_scripts.publication_descriptor import descriptor_input_from_pending_metrics
from utility_scripts.metrics_writer import read_pending_metrics
from utility_scripts.repo_path_resolver import resolve_repo_roots

DEFAULT_PR_LABEL = "fixes-java-run-fail"


def build_parser():
    parser = argparse.ArgumentParser(
        prog="publish_java_run_fix.py",
        description=(
            f"Create and push a verified feature branch with java-run fixes and its publication descriptor "
            f"to '{REPO}'; trusted GitHub Actions open the pull request against base branch "
            f"'{BASE_BRANCH}'. Metrics are loaded from fix_java_run_fail JSON output.\n\n"
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
        help="Path to the reachability-metadata repository to operate on.",
    )
    parser.add_argument(
        "--metrics-repo-path",
        dest="metrics_repo_path",
        help="Path to the metrics repository root.",
    )
    parser.add_argument("--issue-number", type=int, help="Explicit backing GitHub issue number.")
    parser.add_argument("--coverage-follow-up-issue-number", type=int)
    parser.add_argument("--coverage-follow-up-class-count", type=int)
    parser.add_argument("--coverage-follow-up-class-threshold", type=int)
    parser.add_argument(
        "--pr-label",
        default=DEFAULT_PR_LABEL,
        help="Primary label to apply to the generated pull request.",
    )
    return parser


def parse_flags(argv_list):
    """Parse CLI flags and resolve repository paths."""
    parser = build_parser()
    flags = parser.parse_args(argv_list)
    repo_path, metrics_repo_path = resolve_repo_roots(
        flags.reachability_metadata_path,
        flags.metrics_repo_path,
    )
    follow_up_values = (
        flags.coverage_follow_up_class_count,
        flags.coverage_follow_up_class_threshold,
    )
    if any(value is not None for value in follow_up_values) and not all(
            value is not None for value in follow_up_values
    ):
        parser.error(
            "coverage follow-up class count and threshold must be provided together"
        )
    return (
        flags.coordinates,
        flags.new_version,
        repo_path,
        metrics_repo_path,
        flags.issue_number,
        flags.pr_label,
        flags.coverage_follow_up_issue_number,
        flags.coverage_follow_up_class_count,
        flags.coverage_follow_up_class_threshold,
    )


def push_current_branch_to_origin(
        old_coordinates: str,
        new_version: str,
        repo_path: str,
        metrics_repo_path: str | None = None,
        issue_number: int | None = None,
        pr_label: str = DEFAULT_PR_LABEL,
        coverage_follow_up_issue_number: int | None = None,
        coverage_follow_up_class_count: int | None = None,
        coverage_follow_up_class_threshold: int | None = None,
):
    """Create a feature branch, stage and commit changes, and push to the remote."""
    group, artifact, old_version = parse_coordinate_parts(old_coordinates)
    new_coordinates = f"{group}:{artifact}:{new_version}"
    if issue_number is None or metrics_repo_path is None:
        raise ValueError("Publication requires an issue number and metrics path")
    follow_ups: list[dict] = []
    if coverage_follow_up_class_count is not None and coverage_follow_up_class_threshold is not None:
        if coverage_follow_up_issue_number is None:
            raise ValueError("Deferred coverage follow-up requires a locally created issue number")
        follow_ups.append({
            "type": "deferred_dynamic_access_coverage",
            "coordinate": new_coordinates,
            "uncovered_class_count": coverage_follow_up_class_count,
            "class_threshold": coverage_follow_up_class_threshold,
            "issue_number": coverage_follow_up_issue_number,
        })
    descriptor_input = descriptor_input_from_pending_metrics(
        metrics_repo_path=metrics_repo_path,
        issue_number=issue_number,
        task_type=pr_label,
        template_type=DEFAULT_PR_LABEL,
        previous_coordinates=old_coordinates,
        follow_ups=follow_ups,
    )

    branch, _ = publish_branch(
        repo_path=repo_path,
        branch_suffix=f"fix-java-run-{group}-{artifact}-{new_version}",
        coordinates=new_coordinates,
        stage=lambda: stage_library_version_paths(
            group, artifact, new_version, repo_path, f"Fixed test for {new_coordinates}",
        ),
        metrics_repo_path=metrics_repo_path,
        descriptor_input=descriptor_input,
    )
    return branch, group, artifact, old_version, new_coordinates


def main(argv=None):
    ensure_gh_authenticated()

    (
        old_coordinates,
        new_version,
        repo_path,
        metrics_repo_path,
        issue_number,
        pr_label,
        coverage_follow_up_issue_number,
        coverage_follow_up_class_count,
        coverage_follow_up_class_threshold,
    ) = parse_flags(
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
        coverage_follow_up_issue_number=coverage_follow_up_issue_number,
        coverage_follow_up_class_count=coverage_follow_up_class_count,
        coverage_follow_up_class_threshold=coverage_follow_up_class_threshold,
    )

if __name__ == "__main__":
    if any(a in ("-h", "--help") for a in sys.argv[1:]):
        build_parser().print_help()
        sys.exit(0)
    main()
