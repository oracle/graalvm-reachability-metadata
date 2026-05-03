# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import argparse
import json
import os
import re
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
    build_ai_branch_name,
    delete_remote_branch_if_exists,
    get_configured_reviewers,
)
from utility_scripts.library_stats import stats_artifact_dir
from utility_scripts.metrics_writer import (
    count_metadata_entries,
    count_test_only_metadata_entries,
    read_pending_metrics,
)
from utility_scripts.large_library_progress import LABEL_LARGE_LIBRARY_PART, LargeLibraryProgressState
from utility_scripts.repo_path_resolver import resolve_repo_roots
from utility_scripts.local_ci_verification import (
    HUMAN_INTERVENTION_LABEL,
    LOCAL_CI_VERIFICATION_KEY,
    format_local_ci_verification_pr_section,
    local_ci_requires_human_intervention,
    run_local_ci_verification,
)

REPO = "oracle/graalvm-reachability-metadata"
BASE_BRANCH = "master"
REVIEWERS = get_configured_reviewers()
BASELINE_STATS_FILENAME = ".baseline-stats.json"


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
        is_large_library_part: bool = False,
        is_final_large_library_part: bool = True,
        large_library_part: int | None = None,
        series_id: str | None = None,
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

    issue_reference = f"Fixes: #{issue_no}"
    if is_large_library_part and not is_final_large_library_part:
        issue_reference = f"Refs: #{issue_no}"
    part_line = ""
    if is_large_library_part:
        part_line = f"- Large-library series: `{series_id or 'unknown'}`\n- Part: {large_library_part}\n"

    body = f"""
## What does this PR do?

{issue_reference}

This PR improves dynamic-access coverage for {coordinates} by generating additional tests.

Summary:
{part_line}\
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


def load_and_remove_baseline_snapshot(repo_path: str, group: str, artifact: str, version: str) -> dict | None:
    """Load baseline snapshot written by improve_library_coverage.py and delete the file."""
    baseline_path = os.path.join(repo_path, "tests", "src", group, artifact, version, BASELINE_STATS_FILENAME)
    if not os.path.isfile(baseline_path):
        return None
    try:
        with open(baseline_path, "r", encoding="utf-8") as f:
            snapshot = json.load(f)
    except (OSError, json.JSONDecodeError):
        snapshot = None
    os.remove(baseline_path)
    return snapshot


def stage_and_commit(
        group: str,
        artifact: str,
        library_version: str,
        coordinates: str,
        repo_path: str,
) -> None:
    """Stage the expected files/directories and commit."""
    # Remove baseline stats snapshot before staging so it is not committed
    baseline_path = os.path.join(
        repo_path, "tests", "src", group, artifact, library_version, BASELINE_STATS_FILENAME,
    )
    if os.path.isfile(baseline_path):
        os.remove(baseline_path)
    candidate_paths = [
        str(os.path.join("tests", "src", group, artifact, library_version)),
        str(os.path.join("metadata", group, artifact, "index.json")),
        str(os.path.join("metadata", group, artifact, library_version)),
        str(os.path.relpath(stats_artifact_dir(repo_path, group, artifact), repo_path)),
    ]
    candidate_paths = [path for path in candidate_paths if os.path.exists(os.path.join(repo_path, path))]
    commit_message = f"Improve coverage for {coordinates}"
    stage_and_commit_common(candidate_paths, commit_message, cwd=repo_path)


def _fetch_pr_base(repo_path: str) -> str:
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


def create_pull_request(
        branch: str, coordinates: str, metrics_repo_root: str, repo_path: str,
        group: str, artifact: str, version: str,
        baseline_snapshot: dict | None = None,
        issue_number: int | None = None,
        large_library_part: int | None = None,
        is_final_large_library_part: bool = True,
        series_id: str | None = None,
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

    issue_no = issue_number if issue_number is not None else find_issue_common(coordinates, REPO)

    matched = read_pending_metrics(metrics_repo_root)
    metrics = matched.get("metrics", {})
    strategy_name = matched.get("strategy_name", "")
    model_display_name = get_model_display_name(strategy_name)
    agent_name = get_agent_name(strategy_name)
    title = f"[GenAI] Improve coverage for {coordinates} using {model_display_name}"
    if large_library_part is not None:
        title = f"{title} (part {large_library_part})"

    baseline_stats = baseline_snapshot.get("stats") if baseline_snapshot else None
    baseline_metadata_entries = baseline_snapshot.get("metadata_entries") if baseline_snapshot else None
    baseline_test_only_entries = baseline_snapshot.get("test_only_metadata_entries") if baseline_snapshot else None

    library_stats = load_library_stats(repo_path, coordinates)
    current_metadata_entries = count_metadata_entries(repo_path, group, artifact, version)
    current_test_only_entries = count_test_only_metadata_entries(repo_path, group, artifact, version)

    body = build_pull_request_body(
        issue_no=issue_no,
        coordinates=coordinates,
        model_display_name=model_display_name,
        agent_name=agent_name,
        strategy_name=strategy_name,
        metrics=metrics,
        baseline_stats=baseline_stats,
        library_stats=library_stats,
        baseline_metadata_entries=baseline_metadata_entries,
        current_metadata_entries=current_metadata_entries,
        baseline_test_only_entries=baseline_test_only_entries,
        current_test_only_entries=current_test_only_entries,
        post_generation_intervention=matched.get("post_generation_intervention"),
        local_ci_verification=matched.get(LOCAL_CI_VERIFICATION_KEY),
        is_large_library_part=large_library_part is not None,
        is_final_large_library_part=is_final_large_library_part,
        large_library_part=large_library_part,
        series_id=series_id,
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
    if local_ci_requires_human_intervention(matched.get(LOCAL_CI_VERIFICATION_KEY)):
        cmd.extend(["--label", HUMAN_INTERVENTION_LABEL])
    if large_library_part is not None:
        cmd.extend(["--label", LABEL_LARGE_LIBRARY_PART])
    if REVIEWERS:
        for r in REVIEWERS:
            cmd.extend(["--reviewer", r])
    result = gh(*cmd[1:])
    return _parse_pr_number(result.stdout)


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
    parser.add_argument("--large-library-part", type=int, help="Part number for a large-library PR series.")
    parser.add_argument(
        "--large-library-final",
        action="store_true",
        help="Use Fixes instead of Refs for a large-library part.",
    )
    parser.add_argument("--series-id", help="Large-library series identifier for the PR body.")
    parser.add_argument("--large-library-state-path", help="Progress state JSON path to update after publishing.")
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
        flags.large_library_part,
        flags.large_library_final,
        flags.series_id,
        flags.large_library_state_path,
    )


def push_current_branch_to_origin(
        coordinates: str,
        repo_path: str,
        metrics_repo_path: str | None = None,
        large_library_part: int | None = None,
) -> str:
    """Create and push a feature branch, returning the branch name."""
    group, artifact, library_version = parse_coordinate_parts(coordinates)

    branch_suffix = f"improve-coverage-{group}-{artifact}-{library_version}"
    if large_library_part is not None:
        branch_suffix = f"{branch_suffix}-part-{large_library_part:04d}"
    new_branch = build_ai_branch_name(branch_suffix, cwd=repo_path)
    delete_remote_branch_if_exists(new_branch, cwd=repo_path)
    subprocess.run(["git", "switch", "-C", new_branch], check=True, cwd=repo_path)

    stage_and_commit(group, artifact, library_version, coordinates, repo_path)

    base_ref = _fetch_pr_base(repo_path)
    subprocess.run(["git", "rebase", base_ref], check=True, cwd=repo_path)
    run_local_ci_verification(
        repo_path=repo_path,
        coordinates=coordinates,
        base_commit=base_ref,
        metrics_repo_path=metrics_repo_path,
    )
    subprocess.run(["git", "push", "origin", new_branch], check=True, cwd=repo_path)

    return new_branch


def _parse_pr_number(output: str) -> int | None:
    """Extract a PR number from gh pr create output."""
    match = re.search(r"/pull/(\d+)", output)
    return int(match.group(1)) if match else None


def update_large_library_state_after_publish(
        state_path: str | None,
        branch: str,
        repo_path: str,
        pr_number: int | None,
        final_part: bool,
) -> None:
    """Record the published branch/commit in the large-library progress artifact."""
    if not state_path:
        return
    state = LargeLibraryProgressState.load(state_path)
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo_path, text=True).strip()
    state.record_published_pr(branch, commit, pr_number)
    if not final_part:
        state.advance_to_next_part()
    state.save(state_path)


def main(argv=None) -> None:
    (
        coordinates,
        repo_path,
        metrics_repo_path,
        issue_number,
        large_library_part,
        large_library_final,
        series_id,
        large_library_state_path,
    ) = parse_flags(argv if argv is not None else sys.argv[1:])

    ensure_gh_authenticated()

    group, artifact, version = parse_coordinate_parts(coordinates)
    baseline_snapshot = load_and_remove_baseline_snapshot(repo_path, group, artifact, version)
    branch = push_current_branch_to_origin(
        coordinates=coordinates,
        repo_path=repo_path,
        metrics_repo_path=metrics_repo_path,
        large_library_part=large_library_part,
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
        large_library_part=large_library_part,
        is_final_large_library_part=large_library_final or large_library_part is None,
        series_id=series_id,
    )
    update_large_library_state_after_publish(
        large_library_state_path,
        branch,
        repo_path,
        pr_number,
        large_library_final or large_library_part is None,
    )


if __name__ == "__main__":
    if any(a in ("-h", "--help") for a in sys.argv[1:]):
        build_parser().print_help()
        sys.exit(0)
    main()
