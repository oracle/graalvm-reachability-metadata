# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Open a PR that records an artifact as not applicable to Native Image."""

import argparse
import os
from datetime import datetime, timezone

from git_scripts.common_git import (
    ensure_gh_authenticated,
    find_issue_for_coordinates,
    parse_coordinate_parts,
    stage_and_commit,
)
from git_scripts.branch_publication import (
    REPO,
    publish_branch,
)
from git_scripts.publication_descriptor import PublicationDescriptorInput
from utility_scripts.metadata_index import get_not_for_native_image_marker
from utility_scripts.local_ci_verification import (
    LocalCIVerificationResult,
)
from utility_scripts.repo_path_resolver import resolve_repo_roots


def build_parser() -> argparse.ArgumentParser:
    """Build CLI parser."""
    parser = argparse.ArgumentParser(
        prog="publish_not_for_native_image.py",
        description="Create a PR that adds a not-for-native-image marker index.",
    )
    parser.add_argument("--coordinates", required=True, help="Coordinates in group:artifact:version form")
    parser.add_argument("--issue-number", type=int, help="Explicit backing GitHub issue number.")
    parser.add_argument("--reachability-metadata-path", help="Path to the reachability-metadata checkout")
    parser.add_argument(
        "--metrics-repo-path",
        default=None,
        help="Path to the metrics repository. If omitted, the forge directory in the selected worktree is used.",
    )
    return parser


def push_marker_branch(
        coordinates: str,
        repo_path: str,
        metrics_repo_path: str | None = None,
        issue_number: int | None = None,
) -> tuple[str, LocalCIVerificationResult]:
    """Create, commit, rebase, and push the marker branch."""
    group, artifact, _version = parse_coordinate_parts(coordinates)
    if issue_number is None:
        raise ValueError("Publication requires an explicit issue number")
    marker = get_not_for_native_image_marker(repo_path, group, artifact)
    if marker is None:
        raise ValueError(f"Missing not-for-native-image marker for {group}:{artifact}")
    descriptor_input = PublicationDescriptorInput(
        issue_number=issue_number,
        task_type="not-for-native-image",
        template_type="not-for-native-image",
        status="success",
        timestamp=datetime.now(timezone.utc).isoformat(),
        render={
            "reason": str(marker.get("reason") or ""),
            "replacement": marker.get("replacement"),
        },
    )
    return publish_branch(
        repo_path=repo_path,
        branch_suffix=f"not-for-native-image-{group}-{artifact}",
        coordinates=coordinates,
        stage=lambda: stage_and_commit(
            [os.path.join("metadata", group, artifact, "index.json")],
            f"Mark {group}:{artifact} as not for Native Image",
            cwd=repo_path,
        ),
        metrics_repo_path=metrics_repo_path,
        descriptor_input=descriptor_input,
    )


def main(argv=None) -> None:
    """Run the marker PR flow."""
    args = build_parser().parse_args(argv)
    repo_path, metrics_repo_path = resolve_repo_roots(
        args.reachability_metadata_path,
        args.metrics_repo_path,
    )
    ensure_gh_authenticated()
    issue_number = args.issue_number
    if issue_number is None:
        issue_number = find_issue_for_coordinates(args.coordinates, REPO)
    push_marker_branch(
        args.coordinates, repo_path, metrics_repo_path, issue_number,
    )


if __name__ == "__main__":
    main()
