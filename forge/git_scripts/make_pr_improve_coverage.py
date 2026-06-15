# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import argparse
import json
import os
import shutil
import subprocess
import sys

from git_scripts.common_git import (
    ensure_gh_authenticated,
    gh,
    parse_coordinate_parts,
    get_origin_owner,
    stage_and_commit as stage_and_commit_common,
    find_issue_for_coordinates as find_issue_common,
    get_model_display_name,
    get_agent_name,
    load_library_stats,
    format_stats_before_after,
    format_forge_revision_section,
    run_git_transport,
)
from git_scripts.pr_publication import (
    BASE_BRANCH,
    REPO,
    REVIEWERS,
    parse_pr_number,
    publish_branch,
)
from utility_scripts.library_stats import stats_artifact_dir
from utility_scripts.metadata_index import resolve_metadata_version, resolve_test_version
from utility_scripts.metrics_writer import (
    count_metadata_entries,
    count_test_only_metadata_entries,
    read_pending_metrics,
)
from utility_scripts.dynamic_access_exhaust_report import (
    DynamicAccessExhaustReport,
    find_dynamic_access_exhaust_report_path,
)
from utility_scripts.repo_path_resolver import resolve_repo_roots
from utility_scripts.local_ci_verification import (
    HUMAN_INTERVENTION_LABEL,
    LOCAL_CI_VERIFICATION_KEY,
    format_local_ci_verification_pr_section,
    local_ci_requires_human_intervention,
)

BASELINE_STATS_FILENAME = ".baseline-stats.json"
LIBRARY_UPDATE_TARGET_FILENAME = ".library_update_target.json"


def build_pull_request_body(
        issue_no: int | str,
        coordinates: str,
        model_display_name: str,
        agent_name: str,
        strategy_name: str,
        metrics: dict,
        baseline_stats=None,
        library_stats=None,
        baseline_metadata_entries: int | None = None,
        current_metadata_entries: int | None = None,
        baseline_test_only_entries: int | None = None,
        current_test_only_entries: int | None = None,
        post_generation_intervention: dict | None = None,
        library_update_target: dict | None = None,
        chunked_dynamic_access: bool = False,
        chunk_final: bool = True,
        dynamic_access_exhaust_report: DynamicAccessExhaustReport | None = None,
        local_ci_verification: dict | None = None,
) -> str:
    """Build the PR body with metrics and optional stats."""
    input_tokens_used = metrics.get("input_tokens_used", 0)
    output_tokens_used = metrics.get("output_tokens_used", 0)
    cached_input_tokens_used = metrics.get("cached_input_tokens_used", 0)
    iterations = metrics.get("iterations", 0)
    code_coverage_percent = metrics.get("code_coverage_percent", 0)
    generated_loc = metrics.get("generated_loc", 0)
    tested_library_loc = metrics.get("tested_library_loc", 0)

    metadata_comparison_lines = ""
    if baseline_metadata_entries is not None and current_metadata_entries is not None:
        metadata_comparison_lines += (
            f"- Metadata entries (before): {baseline_metadata_entries}\n"
            f"- Metadata entries (after): {current_metadata_entries}\n"
        )
        if baseline_test_only_entries or current_test_only_entries:
            metadata_comparison_lines += (
                f"- Test-only metadata entries (before): {baseline_test_only_entries or 0}\n"
                f"- Test-only metadata entries (after): {current_test_only_entries or 0}\n"
            )

    update_target_lines = ""
    if isinstance(library_update_target, dict):
        update_target_lines = (
            f"- Requested coordinate: `{library_update_target.get('requested_coordinate') or coordinates}`\n"
            f"- Match type: `{library_update_target.get('match_type') or 'unknown'}`\n"
            f"- Matched metadata version: `{library_update_target.get('matched_metadata_version') or 'none'}`\n"
            f"- Matched test version: `{library_update_target.get('matched_test_version') or 'none'}`\n"
            f"- Resolved metadata version: `{library_update_target.get('resolved_metadata_version') or 'unknown'}`\n"
            f"- Resolved test version: `{library_update_target.get('resolved_test_version') or 'unknown'}`\n"
        )
    validation_status = "not recorded"
    if isinstance(local_ci_verification, dict):
        validation_status = str(local_ci_verification.get("status") or "unknown")
    validation_coordinates = [coordinates]
    if isinstance(library_update_target, dict):
        coordinate_parts = coordinates.split(":")
        resolved_metadata_version = library_update_target.get("resolved_metadata_version")
        if (
                len(coordinate_parts) == 3
                and isinstance(resolved_metadata_version, str)
                and resolved_metadata_version
                and resolved_metadata_version != coordinate_parts[2]
        ):
            validation_coordinates.append(
                f"{coordinate_parts[0]}:{coordinate_parts[1]}:{resolved_metadata_version}"
            )
    validation_commands = ", ".join(
        f"`./gradlew test -Pcoordinates={coordinate}`" for coordinate in validation_coordinates
    )

    issue_reference = f"Fixes: #{issue_no}"
    if chunked_dynamic_access and not chunk_final:
        issue_reference = f"Refs: #{issue_no}"
    chunk_line = format_chunked_dynamic_access_summary(
        chunked_dynamic_access,
        dynamic_access_exhaust_report,
    )

    body = f"""
## What does this PR do?

{issue_reference}

This PR improves dynamic-access coverage for {coordinates} by generating additional tests.

Summary:
{chunk_line}\
- Validation commands: {validation_commands}
- Validation result: `{validation_status}`
{update_target_lines}\
- Strategy: {strategy_name}
- Agent: {agent_name}
- Model: {model_display_name}
- Input tokens: {input_tokens_used}
- Cached input tokens: {cached_input_tokens_used}
- Output tokens: {output_tokens_used}
{metadata_comparison_lines}\
- Iterations: {iterations}
- Library coverage percentage: {code_coverage_percent}
- Generated lines of code: {generated_loc}
- Tested library lines of code: {tested_library_loc}
"""
    body += "\n" + format_forge_revision_section() + "\n"
    if baseline_stats or library_stats:
        body += "\n" + format_stats_before_after(baseline_stats, library_stats, coordinates)
    if post_generation_intervention:
        body += "\n### Post-Generation Intervention\n\n"
        body += f"- Stage: `{post_generation_intervention.get('stage', 'unknown')}`\n\n"
        body += f"- Intervention file: `{post_generation_intervention.get('intervention_file', 'unknown')}`\n\n"
        body += str(post_generation_intervention.get("analysis_markdown", "")).strip() + "\n"
    body += format_local_ci_verification_pr_section(local_ci_verification)
    return body


def format_chunked_dynamic_access_summary(
        chunked_dynamic_access: bool,
        exhaust_report: DynamicAccessExhaustReport | None,
) -> str:
    """Return compact PR body lines for a chunked dynamic-access run."""
    if not chunked_dynamic_access:
        return ""
    if exhaust_report is None:
        return "- Chunked dynamic-access: yes\n"
    return (
        "- Chunked dynamic-access: yes\n"
        f"- Chunk class threshold: {exhaust_report.class_threshold or 'unknown'}\n"
        f"- Current chunk class count: {exhaust_report.current_chunk_class_count or 'unknown'}\n"
        "- Processed dynamic-access classes: "
        f"completed={len(exhaust_report.completed_classes)}, "
        f"skipped={len(exhaust_report.skipped_classes)}, "
        f"exhausted={len(exhaust_report.exhausted_classes)}, "
        f"failed={len(exhaust_report.failed_classes)}\n"
    )


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


def create_pull_request(
        branch: str, coordinates: str, metrics_repo_root: str, repo_path: str,
        group: str, artifact: str, version: str,
        baseline_snapshot: dict | None = None,
        issue_number: int | None = None,
        chunked_dynamic_access: bool = False,
        chunk_final: bool = True,
) -> int | None:
    """Create a GitHub pull request for the current branch."""
    if shutil.which("gh") is None:
        print("gh CLI not found. Skipping PR creation.")
        return

    origin_owner = get_origin_owner(cwd=repo_path)

    view = gh("pr", "view", "--repo", REPO, "--head", f"{origin_owner}:{branch}", check=False)
    if view.returncode == 0:
        print(f"Pull request already exists for branch {branch}.")
        return

    title, body, matched = build_pull_request_preview(
        coordinates=coordinates,
        metrics_repo_root=metrics_repo_root,
        repo_path=repo_path,
        group=group,
        artifact=artifact,
        version=version,
        baseline_snapshot=baseline_snapshot,
        issue_number=issue_number,
        chunked_dynamic_access=chunked_dynamic_access,
        chunk_final=chunk_final,
    )

    cmd = [
        "gh", "pr", "create",
        "--repo", REPO,
        "--title", title,
        "--body", body,
        "--base", BASE_BRANCH,
        "--head", f"{origin_owner}:{branch}",
        "--label", "GenAI",
        "--label", "library-update-request",
    ]
    if chunked_dynamic_access:
        cmd.extend(["--label", "chunked-dynamic-access"])
    if local_ci_requires_human_intervention(matched.get(LOCAL_CI_VERIFICATION_KEY)):
        cmd.extend(["--label", HUMAN_INTERVENTION_LABEL])
    if REVIEWERS:
        for r in REVIEWERS:
            cmd.extend(["--reviewer", r])
    result = gh(*cmd[1:])
    return parse_pr_number(result.stdout)


def build_pull_request_preview(
        coordinates: str,
        metrics_repo_root: str,
        repo_path: str,
        group: str,
        artifact: str,
        version: str,
        baseline_snapshot: dict | None = None,
        issue_number: int | None = None,
        chunked_dynamic_access: bool = False,
        chunk_final: bool = True,
) -> tuple[str, str, dict]:
    """Build the PR title/body without creating a GitHub pull request."""
    issue_no = issue_number if issue_number is not None else find_issue_common(coordinates, REPO)
    matched = read_pending_metrics(metrics_repo_root)
    metrics = matched.get("metrics", {})
    strategy_name = matched.get("strategy_name", "")
    model_display_name = get_model_display_name(strategy_name)
    agent_name = get_agent_name(strategy_name)
    title = f"[GenAI] Improve coverage for {coordinates} using {model_display_name}"
    if chunked_dynamic_access and not chunk_final:
        title = f"{title} (chunked dynamic-access)"
    body = build_pull_request_body(
        issue_no=issue_no,
        coordinates=coordinates,
        model_display_name=model_display_name,
        agent_name=agent_name,
        strategy_name=strategy_name,
        metrics=metrics,
        baseline_stats=baseline_snapshot.get("stats") if baseline_snapshot else None,
        library_stats=load_library_stats(repo_path, coordinates),
        baseline_metadata_entries=baseline_snapshot.get("metadata_entries") if baseline_snapshot else None,
        current_metadata_entries=count_metadata_entries(repo_path, group, artifact, version),
        baseline_test_only_entries=baseline_snapshot.get("test_only_metadata_entries") if baseline_snapshot else None,
        current_test_only_entries=count_test_only_metadata_entries(repo_path, group, artifact, version),
        post_generation_intervention=matched.get("post_generation_intervention"),
        library_update_target=load_library_update_target_sidecar(metrics_repo_root),
        local_ci_verification=matched.get(LOCAL_CI_VERIFICATION_KEY),
        chunked_dynamic_access=chunked_dynamic_access,
        chunk_final=chunk_final,
        dynamic_access_exhaust_report=load_dynamic_access_exhaust_report(repo_path, coordinates),
    )
    return title, body, matched


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
        prog="make_pr_improve_coverage.py",
        description=(
            f"Create and push a feature branch with coverage improvements and open a GitHub Pull Request "
            f"to '{REPO}' against base branch '{BASE_BRANCH}'."
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

    new_branch, _ = publish_branch(
        repo_path=repo_path,
        branch_suffix=branch_suffix,
        coordinates=coordinates,
        stage=stage,
        metrics_repo_path=metrics_repo_path,
    )

    return new_branch


def update_dynamic_access_exhaust_report_after_publish(
        coordinates: str,
        repo_path: str,
        branch: str,
        pr_number: int | None,
        chunked_dynamic_access: bool,
) -> None:
    """Record the published chunk PR/commit in the coordinate-local exhaust report."""
    if not chunked_dynamic_access:
        return
    report_path = find_dynamic_access_exhaust_report_path(repo_path, coordinates)
    if report_path is None:
        return
    report = DynamicAccessExhaustReport.load(report_path)
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo_path, text=True).strip()
    report.record_published_chunk(commit, pr_number)
    report.save(report_path)

    relative_report_path = os.path.relpath(report_path, repo_path)
    subprocess.run(["git", "add", "--", relative_report_path], cwd=repo_path, check=True)
    diff_result = subprocess.run(["git", "diff", "--cached", "--quiet"], cwd=repo_path, check=False)
    if diff_result.returncode == 0:
        return
    subprocess.run(
        ["git", "commit", "-m", f"Record chunked dynamic-access publication for {coordinates}"],
        cwd=repo_path,
        check=True,
    )
    run_git_transport(["push", "origin", f"HEAD:{branch}"], cwd=repo_path)


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
    branch = push_current_branch_to_origin(
        coordinates=coordinates,
        repo_path=repo_path,
        metrics_repo_path=metrics_repo_path,
        chunked_dynamic_access=chunked_dynamic_access,
        chunk_final=chunk_final,
    )
    pr_number = create_pull_request(
        branch,
        coordinates,
        metrics_repo_path,
        repo_path,
        group,
        artifact,
        version,
        baseline_snapshot,
        issue_number=issue_number,
        chunked_dynamic_access=chunked_dynamic_access,
        chunk_final=chunk_final or not chunked_dynamic_access,
    )
    update_dynamic_access_exhaust_report_after_publish(
        coordinates,
        repo_path,
        branch,
        pr_number,
        chunked_dynamic_access,
    )


if __name__ == "__main__":
    if any(a in ("-h", "--help") for a in sys.argv[1:]):
        build_parser().print_help()
        sys.exit(0)
    main()
