# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import argparse
import json
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
    get_model_display_name,
    get_agent_name,
    build_ai_branch_name,
    delete_remote_branch_if_exists,
    get_configured_reviewers,
    run_git_transport,
)
from git_scripts.make_pr_javac_fix import generate_diff_text
from utility_scripts.metadata_index import resolve_test_version
from utility_scripts.metrics_writer import (
    count_metadata_entries,
    count_test_only_metadata_entries,
    collect_version_coverage_metrics,
    read_pending_metrics,
)
from utility_scripts.library_stats import stats_artifact_dir
from utility_scripts.local_ci_verification import (
    HUMAN_INTERVENTION_LABEL,
    LocalCIVerificationResult,
    fetch_pr_base_ref,
    format_local_ci_verification_pr_section,
    run_local_ci_verification,
)
from utility_scripts.repo_path_resolver import resolve_repo_roots

REPO = "oracle/graalvm-reachability-metadata"
BASE_BRANCH = 'master'
REVIEWERS = get_configured_reviewers()
SEVERE_METADATA_DROP_RATIO = 0.25
TEST_SOURCE_DIR_NAMES: tuple[str, ...] = ("java", "kotlin", "groovy", "scala")


def is_severe_metadata_drop(previous_entries: int, new_entries: int) -> bool:
    """Return true when a native-image-run fix drops most prior metadata entries."""
    return previous_entries > 0 and new_entries < previous_entries * SEVERE_METADATA_DROP_RATIO


def format_severe_metadata_drop_pr_section(
        old_coordinates: str,
        new_coordinates: str,
        previous_entries: int,
        new_entries: int,
) -> str:
    """Format a PR warning for severe unexplained metadata drops."""
    retained_ratio = new_entries / previous_entries if previous_entries else 0
    retained_percent = retained_ratio * 100
    return (
        "\n\n### Human Intervention: Severe Metadata Drop\n\n"
        "Forge detected a severe drop in reachability metadata entries for this "
        "Native Image run fix. This PR needs human review unless the branch includes "
        "concrete proof that the new library version no longer needs the removed "
        "registrations.\n\n"
        f"- Previous metadata entries (`{old_coordinates}`): {previous_entries}\n"
        f"- New metadata entries (`{new_coordinates}`): {new_entries}\n"
        f"- Retained metadata entries: {retained_percent:.2f}%"
    )


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
        "Stage these paths in make_pr_ni_run_fix.py or discard them before finalization:\n"
        f"{status_output}"
    )


def create_pull_request(
        branch: str,
        old_coordinates: str,
        new_coordinates: str,
        group: str,
        artifact: str,
        repo_path: str,
        local_ci_verification: LocalCIVerificationResult | None = None,
        issue_number: int | None = None,
        metrics_repo_path: str | None = None,
):
    """Create a GitHub pull request for the current branch."""
    origin_owner = get_origin_owner(cwd=repo_path)

    # If a PR already exists for this branch, do nothing
    view = gh("pr", "view", "--repo", REPO, "--head", branch, check=False)
    if view.returncode == 0:
        print(f"Pull request already exists for branch {branch}.")
        return

    title, body, local_ci_human_intervention, severe_metadata_drop = build_pull_request_preview(
        old_coordinates=old_coordinates,
        new_coordinates=new_coordinates,
        group=group,
        artifact=artifact,
        repo_path=repo_path,
        local_ci_verification=local_ci_verification,
        issue_number=issue_number,
        metrics_repo_path=metrics_repo_path,
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
    if severe_metadata_drop or local_ci_human_intervention:
        cmd.extend(["--label", HUMAN_INTERVENTION_LABEL])
    for r in REVIEWERS:
        cmd.extend(["--reviewer", r])
    gh(*cmd[1:])


def build_forge_metrics_summary_section(metrics_repo_path: str | None) -> str:
    """Format the strategy/agent/token summary from pending run metrics, when available."""
    if not metrics_repo_path:
        return ""
    try:
        metrics_entry = read_pending_metrics(metrics_repo_path)
    except (json.JSONDecodeError, TypeError, FileNotFoundError, OSError):
        return ""
    if not isinstance(metrics_entry, dict):
        return ""
    metrics = metrics_entry.get("metrics", {})
    if not isinstance(metrics, dict):
        metrics = {}
    strategy_name = metrics_entry.get("strategy_name", "")
    if not strategy_name and not metrics:
        return ""
    return (
        "\n\n"
        f"- Strategy: {strategy_name}\n"
        f"- Agent: {get_agent_name(strategy_name)}\n"
        f"- Model: {get_model_display_name(strategy_name)}\n"
        f"- Input tokens: {int(metrics.get('input_tokens_used', 0) or 0)}\n"
        f"- Cached input tokens: {int(metrics.get('cached_input_tokens_used', 0) or 0)}\n"
        f"- Output tokens: {int(metrics.get('output_tokens_used', 0) or 0)}\n"
        f"- Iterations: {int(metrics.get('iterations', 0) or 0)}"
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
    return (
        "\n\n**Comparison between existing test version and AI-Generated update**\n\n"
        "```diff\n"
        f"{generate_diff_text(group, artifact, old_version, new_version, repo_path)}\n"
        "```"
    )


def build_pull_request_preview(
        old_coordinates: str,
        new_coordinates: str,
        group: str,
        artifact: str,
        repo_path: str,
        local_ci_verification: LocalCIVerificationResult | None = None,
        issue_number: int | None = None,
        metrics_repo_path: str | None = None,
) -> tuple[str, str, bool, bool]:
    """Build the PR title/body without creating a GitHub pull request."""
    issue_no = issue_number if issue_number is not None else find_issue_common(new_coordinates, REPO)
    _, _, old_version = parse_coordinate_parts(old_coordinates)
    _, _, new_version = parse_coordinate_parts(new_coordinates)
    new_entries = count_metadata_entries(repo_path, group, artifact, new_version)
    previous_entries = count_metadata_entries(repo_path, group, artifact, old_version)
    new_test_entries = count_test_only_metadata_entries(repo_path, group, artifact, new_version)
    previous_test_entries = count_test_only_metadata_entries(repo_path, group, artifact, old_version)
    previous_coverage, _ = collect_version_coverage_metrics(repo_path, group, artifact, old_version)
    new_coverage, _ = collect_version_coverage_metrics(repo_path, group, artifact, new_version)
    severe_metadata_drop = is_severe_metadata_drop(previous_entries, new_entries)
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
        f"{build_forge_metrics_summary_section(metrics_repo_path)}"
        f"\n\n{format_forge_revision_section()}"
        f"{format_stats_diff(repo_path, old_coordinates, new_coordinates)}"
        f"{build_test_comparison_section(group, artifact, old_version, new_version, repo_path)}"
    )
    if severe_metadata_drop:
        body += format_severe_metadata_drop_pr_section(
            old_coordinates,
            new_coordinates,
            previous_entries,
            new_entries,
        )
    if local_ci_verification is not None:
        body += format_local_ci_verification_pr_section(local_ci_verification.to_metrics())
    local_ci_human_intervention = (
        local_ci_verification is not None and local_ci_verification.human_intervention_required
    )
    return title, body, local_ci_human_intervention, severe_metadata_drop



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
    parser.add_argument(
        "--metrics-repo-path",
        dest="metrics_repo_path",
        help="Path to the metrics repository root.",
    )
    parser.add_argument("--issue-number", type=int, help="Explicit backing GitHub issue number.")
    return parser


def parse_flags(argv_list):
    """Parse CLI flags and resolve repository path."""
    parser = build_parser()
    flags = parser.parse_args(argv_list)
    repo_path, metrics_repo_path = resolve_repo_roots(
        flags.reachability_metadata_path,
        flags.metrics_repo_path,
    )
    return flags.coordinates, flags.new_version, repo_path, metrics_repo_path, flags.issue_number


def push_current_branch_to_origin(
        old_coordinates: str,
        new_version: str,
        repo_path: str,
        metrics_repo_path: str | None = None,
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
    assert_no_tracked_worktree_changes(repo_path)
    base_ref = fetch_pr_base_ref(repo_path, REPO, BASE_BRANCH)
    subprocess.run(["git", "rebase", base_ref], cwd=repo_path, check=True)
    local_ci_verification = run_local_ci_verification(
        repo_path=repo_path,
        coordinates=new_coordinates,
        base_commit=base_ref,
        metrics_repo_path=metrics_repo_path,
    )

    run_git_transport(["push", "origin", branch], cwd=repo_path)

    return branch, group, artifact, new_coordinates, local_ci_verification


def main(argv=None):
    ensure_gh_authenticated()

    old_coordinates, new_version, repo_path, metrics_repo_path, issue_number = parse_flags(
        argv if argv is not None else sys.argv[1:]
    )

    branch, group, artifact, new_coordinates, local_ci_verification = push_current_branch_to_origin(
        old_coordinates=old_coordinates,
        new_version=new_version,
        repo_path=repo_path,
        metrics_repo_path=metrics_repo_path,
    )
    create_pull_request(
        branch=branch,
        old_coordinates=old_coordinates,
        new_coordinates=new_coordinates,
        group=group,
        artifact=artifact,
        repo_path=repo_path,
        local_ci_verification=local_ci_verification,
        issue_number=issue_number,
        metrics_repo_path=metrics_repo_path,
    )


if __name__ == "__main__":
    if any(a in ("-h", "--help") for a in sys.argv[1:]):
        build_parser().print_help()
        sys.exit(0)
    main()
