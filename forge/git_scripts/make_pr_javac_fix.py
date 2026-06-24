# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import argparse
import shutil
import sys

from git_scripts.common_git import (
    ensure_gh_authenticated,
    gh,
    parse_coordinate_parts,
    get_origin_owner,
    find_issue_for_coordinates as find_issue_common,
    get_model_display_name,
    get_agent_name,
    format_stats_diff,
    format_forge_revision_section,
    assert_no_dynamic_access_category_regressions,
)
from git_scripts.pr_publication import (
    BASE_BRANCH,
    REPO,
    REVIEWERS,
    generate_diff_text,
    publish_branch,
    stage_library_version_paths,
)
from utility_scripts.local_ci_verification import (
    HUMAN_INTERVENTION_LABEL,
    LOCAL_CI_VERIFICATION_KEY,
    format_local_ci_verification_pr_section,
    local_ci_requires_human_intervention,
)
from utility_scripts.metrics_writer import read_pending_metrics
from utility_scripts.repo_path_resolver import resolve_repo_roots

DEFAULT_PR_LABEL = "fixes-javac-fail"


def create_pull_request(
        branch: str,
        old_coordinates: str,
        new_coordinates: str,
        group: str,
        artifact: str,
        old_version: str,
        new_version: str,
        metrics_repo_root: str,
        repo_path: str,
        issue_number: int | None = None,
        pr_label: str = DEFAULT_PR_LABEL,
):
    """
    Create a GitHub pull request for the current branch with an auto-generated body,
    linking to the matching issue and embedding a diff of test changes.
    """
    if shutil.which("gh") is None:
        print("gh CLI not found. Skipping PR creation.")
        return

    # Use the fork owner from the 'origin' remote for PR head ref
    origin_owner = get_origin_owner(cwd=repo_path)

    # If a PR already exists for this branch, do nothing
    view = gh("pr", "view", "--repo", REPO, "--head", f"{origin_owner}:{branch}", check=False)

    if view.returncode == 0:
        print(f"Pull request already exists for branch {branch}.")
        return

    assert_no_dynamic_access_category_regressions(repo_path, old_coordinates, new_coordinates)

    title, body, metrics_entry = build_pull_request_preview(
        old_coordinates=old_coordinates,
        new_coordinates=new_coordinates,
        group=group,
        artifact=artifact,
        old_version=old_version,
        new_version=new_version,
        metrics_repo_root=metrics_repo_root,
        repo_path=repo_path,
        issue_number=issue_number,
    )

    cmd = [
        "gh",
        "pr",
        "create",
        "--repo",
        REPO,
        "--title",
        title,
        "--body",
        body,
        "--base",
        BASE_BRANCH,
        "--head",
        f"{origin_owner}:{branch}",
        "--label",
        pr_label,
        "--label",
        "GenAI",
    ]
    if local_ci_requires_human_intervention(metrics_entry.get(LOCAL_CI_VERIFICATION_KEY)):
        cmd.extend(["--label", HUMAN_INTERVENTION_LABEL])
    if REVIEWERS:
        for reviewer in REVIEWERS:
            cmd.extend(["--reviewer", reviewer])
    gh(*cmd[1:])


def build_pull_request_preview(
        old_coordinates: str,
        new_coordinates: str,
        group: str,
        artifact: str,
        old_version: str,
        new_version: str,
        metrics_repo_root: str,
        repo_path: str,
        issue_number: int | None = None,
) -> tuple[str, str, dict]:
    """Build the PR title/body without creating a GitHub pull request."""
    issue_no = issue_number if issue_number is not None else find_issue_common(new_coordinates, REPO)
    metrics_entry = read_pending_metrics(metrics_repo_root)
    metrics = metrics_entry.get("metrics", {})
    strategy_name = metrics_entry.get("strategy_name", "")
    model_display_name = get_model_display_name(strategy_name)
    agent_name = get_agent_name(strategy_name)
    title = f"[GenAI] Test fix for {new_coordinates} using {model_display_name}"
    test_only_metadata_entries = int(metrics.get("test_only_metadata_entries", 0) or 0)
    previous_library_test_only_metadata_entries = int(metrics.get("previous_library_test_only_metadata_entries", 0) or 0)
    test_only_metadata_entries_line = ""
    if test_only_metadata_entries > 0:
        test_only_metadata_entries_line = f"- Test-only metadata entries: {test_only_metadata_entries}\n"
    previous_test_only_metadata_entries_line = ""
    if previous_library_test_only_metadata_entries > 0:
        previous_test_only_metadata_entries_line = (
            f"- Previous library version test-only metadata entries: {previous_library_test_only_metadata_entries}\n"
        )
    body = f"""## What does this PR do?

Fixes: #{issue_no}

This PR provides test fixes and new metadata for {new_coordinates}, addressing compile java failures caused by changes in the updated library version.

Summary:
- Strategy: {strategy_name}
- Agent: {agent_name}
- Model: {model_display_name}
- Input tokens: {int(metrics.get("input_tokens_used", 0))}
- Cached input tokens: {int(metrics.get("cached_input_tokens_used", 0) or 0)}
- Output tokens: {int(metrics.get("output_tokens_used", 0))}
- Metadata entries: {int(metrics.get("metadata_entries", 0))}
{test_only_metadata_entries_line}\
- Iterations: {int(metrics.get("iterations", 0))}
- Library coverage percentage: {metrics.get("code_coverage_percent", 0)}
- Previous library version metadata entries: {int(metrics.get("previous_library_metadata_entries", 0))}
{previous_test_only_metadata_entries_line}\
- Previous library version coverage percentage: {metrics.get("previous_library_coverage_percent", 0)}

{format_forge_revision_section()}
{format_stats_diff(repo_path, old_coordinates, new_coordinates)}
**Comparison between existing test version and AI-Generated update**

```diff
{generate_diff_text(group, artifact, old_version, new_version, repo_path)}
```
"""
    post_generation_intervention = metrics_entry.get("post_generation_intervention")
    if post_generation_intervention:
        body += (
            "\n### Post-Generation Intervention\n\n"
            f"- Stage: `{post_generation_intervention.get('stage', 'unknown')}`\n\n"
            f"- Intervention file: `{post_generation_intervention.get('intervention_file', 'unknown')}`\n\n"
            f"{str(post_generation_intervention.get('analysis_markdown', '')).strip()}\n"
        )
    body += format_local_ci_verification_pr_section(metrics_entry.get(LOCAL_CI_VERIFICATION_KEY))
    return title, body, metrics_entry



def build_parser():
    parser = argparse.ArgumentParser(
        prog="make_pr_javac_fix.py",
        description=(
            f"Create and push a feature branch with javac fixes and open a GitHub Pull Request to '{REPO}' "
            f"against base branch '{BASE_BRANCH}'. Metrics are loaded from fix_javac_fail JSON output.\n\n"
        ),
        epilog=(
            "Example:\n"
            "  python3 git_scripts/make_pr_javac_fix.py \\\n"
            "      --coordinates com.example:lib:1.2.3 \\\n"
            "      --new-version 1.2.4 \\\n"
            "      --reachability-metadata-path /path/to/graalvm-reachability-metadata \\\n"
            "      --metrics-repo-path /path/to/metrics-repo-root\n\n"
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
        help=(
            "Path to the metrics repository root. "
            "If omitted, the forge directory in the selected worktree is used."
        ),
    )
    parser.add_argument("--issue-number", type=int, help="Explicit backing GitHub issue number.")
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
    return flags.coordinates, flags.new_version, repo_path, metrics_repo_path, flags.issue_number, flags.pr_label


def push_current_branch_to_origin(
        old_coordinates: str,
        new_version: str,
        repo_path: str,
        metrics_repo_path: str | None = None,
):
    """Create a feature branch, stage and commit changes, and push to the remote."""
    group, artifact, old_version = parse_coordinate_parts(old_coordinates)
    new_coordinates = f"{group}:{artifact}:{new_version}"

    branch, _ = publish_branch(
        repo_path=repo_path,
        branch_suffix=f"fix-javac-{group}-{artifact}-{new_version}",
        coordinates=new_coordinates,
        stage=lambda: stage_library_version_paths(
            group, artifact, new_version, repo_path, f"Fixed test for {new_coordinates}",
        ),
        metrics_repo_path=metrics_repo_path,
        after_verification=lambda: assert_no_dynamic_access_category_regressions(
            repo_path, old_coordinates, new_coordinates,
        ),
    )

    return branch, group, artifact, old_version, new_coordinates


def main(argv=None):
    ensure_gh_authenticated()

    old_coordinates, new_version, repo_path, metrics_repo_path, issue_number, pr_label = parse_flags(
        argv if argv is not None else sys.argv[1:]
    )

    branch, group, artifact, old_version, new_coordinates = push_current_branch_to_origin(
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
        old_version=old_version,
        new_version=new_version,
        metrics_repo_root=metrics_repo_path,
        repo_path=repo_path,
        issue_number=issue_number,
        pr_label=pr_label,
    )

if __name__ == "__main__":
    if any(a in ("-h", "--help") for a in sys.argv[1:]):
        build_parser().print_help()
        sys.exit(0)
    main()
