# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import argparse
import json
import os
import sys

from git_scripts.common_git import (
    ensure_gh_authenticated,
    parse_coordinate_parts,
    stage_and_commit as stage_and_commit_common,
    find_issue_for_coordinates as find_issue_common,
    load_library_stats,
)
from git_scripts.branch_publication import (
    BASE_BRANCH,
    REPO,
    publish_branch,
)
from git_scripts.publication_descriptor import descriptor_input_from_pending_metrics
from utility_scripts.library_stats import stats_artifact_dir
from utility_scripts.metadata_index import resolve_metadata_version, resolve_test_version
from utility_scripts.metrics_writer import (
    count_metadata_entries,
    count_test_only_metadata_entries,
)
from utility_scripts.dynamic_access_exhaust_report import (
    DynamicAccessExhaustReport,
    find_dynamic_access_exhaust_report_path,
)
from utility_scripts.repo_path_resolver import resolve_repo_roots
from utility_scripts.library_update_alias_split import (
    ensure_alias_split_follow_up_issue,
    maybe_split_library_update_tested_versions,
)

BASELINE_STATS_FILENAME = ".baseline-stats.json"
LIBRARY_UPDATE_TARGET_FILENAME = ".library_update_target.json"


def baseline_snapshot_path(repo_path: str, group: str, artifact: str, version: str) -> str:
    """Return the baseline snapshot path written by improve_library_coverage.py."""
    test_version = resolve_test_version(repo_path, group, artifact, version)
    return os.path.join(repo_path, "tests", "src", group, artifact, test_version, BASELINE_STATS_FILENAME)


def load_baseline_snapshot(repo_path: str, group: str, artifact: str, version: str) -> dict | None:
    """Load baseline snapshot written by improve_library_coverage.py."""
    baseline_path = baseline_snapshot_path(repo_path, group, artifact, version)
    if not os.path.isfile(baseline_path):
        return None
    try:
        with open(baseline_path, "r", encoding="utf-8") as f:
            snapshot = json.load(f)
    except (OSError, json.JSONDecodeError):
        snapshot = None
    return snapshot


def load_and_remove_baseline_snapshot(repo_path: str, group: str, artifact: str, version: str) -> dict | None:
    """Load baseline snapshot written by improve_library_coverage.py and delete the file."""
    baseline_path = baseline_snapshot_path(repo_path, group, artifact, version)
    snapshot = load_baseline_snapshot(repo_path, group, artifact, version)
    if not os.path.isfile(baseline_path):
        return snapshot
    os.remove(baseline_path)
    return snapshot


def load_library_update_target_sidecar(metrics_repo_root: str) -> dict | None:
    """Load PR-only target-resolution details written by improve_library_coverage."""
    sidecar_path = os.path.join(metrics_repo_root, LIBRARY_UPDATE_TARGET_FILENAME)
    if not os.path.isfile(sidecar_path):
        return None
    try:
        with open(sidecar_path, "r", encoding="utf-8") as sidecar_file:
            sidecar = json.load(sidecar_file)
    except (OSError, json.JSONDecodeError):
        return None
    return sidecar if isinstance(sidecar, dict) else None


def _normalize_relative_path(path: str) -> str:
    """Normalize a git relative path for scope comparisons."""
    return os.path.normpath(path).replace(os.sep, "/")


def expected_update_paths(
        group: str,
        artifact: str,
        library_version: str,
        repo_path: str,
) -> list[str]:
    """Return the resolved paths that belong in the generated coverage PR."""
    test_version = resolve_test_version(repo_path, group, artifact, library_version)
    metadata_version = resolve_metadata_version(repo_path, group, artifact, library_version)
    candidate_paths = [
        os.path.join("tests", "src", group, artifact, test_version),
        os.path.join("metadata", group, artifact, "index.json"),
        os.path.join("metadata", group, artifact, metadata_version),
        os.path.relpath(stats_artifact_dir(repo_path, group, artifact), repo_path),
    ]
    return [
        _normalize_relative_path(path)
        for path in candidate_paths
        if os.path.exists(os.path.join(repo_path, path))
    ]


def stage_and_commit(
        group: str,
        artifact: str,
        library_version: str,
        coordinates: str,
        repo_path: str,
) -> list[str]:
    """Stage the expected files/directories and commit."""
    test_version = resolve_test_version(repo_path, group, artifact, library_version)
    # Remove baseline stats snapshot before staging so it is not committed
    baseline_path = os.path.join(
        repo_path, "tests", "src", group, artifact, test_version, BASELINE_STATS_FILENAME,
    )
    if os.path.isfile(baseline_path):
        os.remove(baseline_path)
    candidate_paths = expected_update_paths(group, artifact, library_version, repo_path)
    commit_message = f"Improve coverage for {coordinates}"
    stage_and_commit_common(candidate_paths, commit_message, cwd=repo_path)
    return candidate_paths


def load_dynamic_access_exhaust_report(
        repo_path: str,
        coordinates: str,
) -> DynamicAccessExhaustReport | None:
    """Load the coordinate-derived exhaust report when this run is chunked."""
    report_path = find_dynamic_access_exhaust_report_path(repo_path, coordinates)
    if report_path is None:
        return None
    return DynamicAccessExhaustReport.load(report_path)


def remove_dynamic_access_exhaust_report_for_final_chunk(
        repo_path: str,
        coordinates: str,
        chunked_dynamic_access: bool,
        chunk_final: bool,
) -> None:
    """Remove the resumable exhaust report from the final chunk PR."""
    if not chunked_dynamic_access or not chunk_final:
        return
    report_path = find_dynamic_access_exhaust_report_path(repo_path, coordinates)
    if report_path is None:
        return
    os.remove(report_path)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="publish_improve_coverage.py",
        description=(
            f"Create and push a verified feature branch with coverage improvements and its publication "
            f"descriptor to '{REPO}'; trusted GitHub Actions open the pull request against "
            f"base branch '{BASE_BRANCH}'."
        ),
        formatter_class=argparse.RawTextHelpFormatter,
    )
    parser.add_argument(
        "--coordinates",
        required=True,
        help="Maven coordinates Group:Artifact:Version for the target library",
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
        help=(
            "Path to the metrics storage root. "
            "If omitted, the forge directory in the selected worktree is used."
        ),
    )
    parser.add_argument("--issue-number", type=int, help="Explicit backing GitHub issue number.")
    parser.add_argument(
        "--chunked-dynamic-access",
        action="store_true",
        help="Publish this PR as part of a chunked dynamic-access run.",
    )
    parser.add_argument(
        "--chunk-final",
        action="store_true",
        help="Use Fixes instead of Refs for a chunked dynamic-access run.",
    )
    return parser


def parse_flags(argv_list: list[str]):
    """Parse CLI flags and return coordinates, repo_path, metrics_repo_path."""
    flags = build_parser().parse_args(argv_list)
    repo_path, metrics_repo_path = resolve_repo_roots(
        flags.reachability_metadata_path,
        flags.metrics_repo_path,
    )
    return (
        flags.coordinates,
        repo_path,
        metrics_repo_path,
        flags.issue_number,
        flags.chunked_dynamic_access,
        flags.chunk_final,
    )


def push_current_branch_to_origin(
        coordinates: str,
        repo_path: str,
        metrics_repo_path: str | None = None,
        issue_number: int | None = None,
        baseline_snapshot: dict | None = None,
        chunked_dynamic_access: bool = False,
        chunk_final: bool = True,
) -> str:
    """Create and push a feature branch, returning the branch name."""
    group, artifact, library_version = parse_coordinate_parts(coordinates)

    branch_suffix = f"improve-coverage-{group}-{artifact}-{library_version}"
    if chunked_dynamic_access:
        branch_suffix = f"{branch_suffix}-chunked"

    def stage() -> None:
        remove_dynamic_access_exhaust_report_for_final_chunk(
            repo_path,
            coordinates,
            chunked_dynamic_access,
            chunk_final,
        )
        stage_and_commit(group, artifact, library_version, coordinates, repo_path)

    def before_verification(base_ref: str) -> None:
        maybe_split_library_update_tested_versions(
            repo_path=repo_path,
            coordinates=coordinates,
            base_ref=base_ref,
            metrics_repo_path=metrics_repo_path,
        )

    def descriptor_input():
        if issue_number is None or metrics_repo_path is None:
            raise ValueError("Publication requires an issue number and metrics path")
        exhaust_report = load_dynamic_access_exhaust_report(repo_path, coordinates)
        alias_split = ensure_alias_split_follow_up_issue(
            metrics_repo_path=metrics_repo_path,
            current_issue_number=issue_number,
            repo=REPO,
        )
        follow_ups = []
        if alias_split is not None:
            follow_ups.append({
                "type": "tested_version_split",
                "coordinate": str(alias_split["successor_coordinates"]),
                "tested_version": str(alias_split["failed_version"]),
                "issue_number": int(alias_split["follow_up_issue_number"]),
                "reason": f"JVM compatibility first failed at {alias_split['failed_version']}",
            })
        render = {
            "baseline_stats": baseline_snapshot.get("stats") if baseline_snapshot else None,
            "library_stats": load_library_stats(repo_path, coordinates),
            "baseline_metadata_entries": (
                baseline_snapshot.get("metadata_entries") if baseline_snapshot else None
            ),
            "current_metadata_entries": count_metadata_entries(
                repo_path, group, artifact, library_version,
            ),
            "baseline_test_only_entries": (
                baseline_snapshot.get("test_only_metadata_entries") if baseline_snapshot else None
            ),
            "current_test_only_entries": count_test_only_metadata_entries(
                repo_path, group, artifact, library_version,
            ),
            "library_update_target": load_library_update_target_sidecar(metrics_repo_path),
            "dynamic_access": None if exhaust_report is None else exhaust_report.to_dict(),
            "alias_split": alias_split,
        }
        return descriptor_input_from_pending_metrics(
            metrics_repo_path=metrics_repo_path,
            issue_number=issue_number,
            task_type="library-update-request",
            template_type="library-update-request",
            chunked_dynamic_access=chunked_dynamic_access,
            chunk_final=chunk_final or not chunked_dynamic_access,
            follow_ups=follow_ups,
            render=render,
        )

    def record_chunk_identity(publication_id: str, branch: str) -> None:
        if not chunked_dynamic_access or chunk_final:
            return
        report_path = find_dynamic_access_exhaust_report_path(repo_path, coordinates)
        if report_path is None:
            raise ValueError("Chunked publication requires an exhaust report")
        report = DynamicAccessExhaustReport.load(report_path)
        report.record_publication_identity(publication_id, branch)
        report.save(report_path)

    new_branch, _ = publish_branch(
        repo_path=repo_path,
        branch_suffix=branch_suffix,
        coordinates=coordinates,
        stage=stage,
        metrics_repo_path=metrics_repo_path,
        descriptor_input=descriptor_input,
        before_stage=record_chunk_identity,
        before_verification=before_verification,
    )

    return new_branch


def main(argv=None) -> None:
    (
        coordinates,
        repo_path,
        metrics_repo_path,
        issue_number,
        chunked_dynamic_access,
        chunk_final,
    ) = parse_flags(argv if argv is not None else sys.argv[1:])

    ensure_gh_authenticated()

    group, artifact, version = parse_coordinate_parts(coordinates)
    baseline_snapshot = load_and_remove_baseline_snapshot(repo_path, group, artifact, version)
    if issue_number is None:
        issue_number = find_issue_common(coordinates, REPO)
    push_current_branch_to_origin(
        coordinates=coordinates,
        repo_path=repo_path,
        metrics_repo_path=metrics_repo_path,
        issue_number=issue_number,
        baseline_snapshot=baseline_snapshot,
        chunked_dynamic_access=chunked_dynamic_access,
        chunk_final=chunk_final,
    )


if __name__ == "__main__":
    if any(a in ("-h", "--help") for a in sys.argv[1:]):
        build_parser().print_help()
        sys.exit(0)
    main()
