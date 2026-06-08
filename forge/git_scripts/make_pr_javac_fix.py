# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import argparse
import os
import shutil
import subprocess
import sys
import tempfile

from git_scripts.common_git import (
    ensure_gh_authenticated,
    gh,
    parse_coordinate_parts,
    get_origin_owner,
    git_files_under,
    is_java_fix_test_module_file,
    stage_and_commit as stage_and_commit_common,
    find_issue_for_coordinates as find_issue_common,
    get_model_display_name,
    get_agent_name,
    format_stats_diff,
    format_forge_revision_section,
    assert_no_dynamic_access_category_regressions,
    build_ai_branch_name,
    delete_remote_branch_if_exists,
    get_configured_reviewers,
    run_git_transport,
)
from utility_scripts.library_stats import stats_artifact_dir
from utility_scripts.local_ci_verification import (
    HUMAN_INTERVENTION_LABEL,
    LOCAL_CI_VERIFICATION_KEY,
    fetch_pr_base_ref,
    format_local_ci_verification_pr_section,
    local_ci_requires_human_intervention,
    run_local_ci_verification,
)
from utility_scripts.metrics_writer import read_pending_metrics
from utility_scripts.repo_path_resolver import resolve_repo_roots

REPO = "oracle/graalvm-reachability-metadata"
BASE_BRANCH = 'master'
REVIEWERS = get_configured_reviewers()
def resolve_repo_paths(
        explicit_repo_path: str | None,
        explicit_metrics_repo_path: str | None,
):
    """Resolve repo and metrics paths using provided values or local defaults."""
    return resolve_repo_roots(
        explicit_repo_path,
        explicit_metrics_repo_path,
    )


def stage_and_commit(
        group: str,
        artifact: str,
        library_version: str,
        coordinates: str,
        repo_path: str,
        metrics_repo_path: str | None = None,
        include_in_repo_metrics: bool = False,
):
    """Stage the expected files/directories and commit with the required message."""
    candidate_paths = [
        str(os.path.join("tests", "src", group, artifact, library_version)),
        str(os.path.join("metadata", group, artifact, "index.json")),
        str(os.path.join("metadata", group, artifact, library_version)),
        str(os.path.relpath(stats_artifact_dir(repo_path, group, artifact), repo_path)),
    ]
    del metrics_repo_path, include_in_repo_metrics
    candidate_paths = [path for path in candidate_paths if os.path.exists(os.path.join(repo_path, path))]

    commit_message = f"Fixed test for {coordinates}"
    stage_and_commit_common(candidate_paths, commit_message, cwd=repo_path)


def _copy_git_files_under(repo_path: str, source_dir: str, destination_dir: str) -> None:
    os.makedirs(destination_dir, exist_ok=True)
    for git_path in git_files_under(repo_path, source_dir):
        source_file = os.path.join(repo_path, git_path)
        relative_file = os.path.relpath(source_file, source_dir)
        if relative_file.startswith(os.pardir + os.sep) or relative_file == os.pardir:
            continue
        if not is_java_fix_test_module_file(relative_file):
            continue
        destination_file = os.path.join(destination_dir, relative_file)
        os.makedirs(os.path.dirname(destination_file), exist_ok=True)
        shutil.copy2(source_file, destination_file)


def generate_diff_text(group: str, artifact: str, current_version: str, new_version: str, repo_path: str):
    """Build a diff text between the current and new test directories for a library version."""
    old_dir = os.path.join(repo_path, "tests", "src", group, artifact, current_version)
    new_dir = os.path.join(repo_path, "tests", "src", group, artifact, new_version)
    old_display_dir = os.path.relpath(old_dir, repo_path).replace(os.sep, "/")
    new_display_dir = os.path.relpath(new_dir, repo_path).replace(os.sep, "/")
    old_diff_display_dir = f"/{old_display_dir}"
    new_diff_display_dir = f"/{new_display_dir}"

    # Prepare temp copies from Git's file list with a narrow allow-list for the PR body diff.
    with tempfile.TemporaryDirectory() as tmpdir:
        tmp_old = os.path.join(tmpdir, "old")
        tmp_new = os.path.join(tmpdir, "new")

        _copy_git_files_under(repo_path, old_dir, tmp_old)
        _copy_git_files_under(repo_path, new_dir, tmp_new)

        # Compute diff text using: git diff --no-index --find-renames tmp_old tmp_new
        res = subprocess.run(
            ["git", "diff", "--no-index", "--find-renames", tmp_old, tmp_new],
            capture_output=True,
            text=True,
        )
        if res.returncode in (0, 1):
            diff_text = res.stdout

            diff_text = diff_text.replace(tmp_old.replace(os.sep, "/"), old_diff_display_dir)
            diff_text = diff_text.replace(tmp_new.replace(os.sep, "/"), new_diff_display_dir)
            diff_text = diff_text.replace(tmp_old, old_diff_display_dir)
            diff_text = diff_text.replace(tmp_new, new_diff_display_dir)
            return diff_text
        else:
            error_message = (
                f"Subprocess failed with return code: {res.returncode}. "
                f"Error output:\n{res.stderr.strip()}"
            )
            raise RuntimeError(error_message)


def load_fix_javac_metrics(metrics_repo_root: str, old_coordinates: str, new_coordinates: str):
    """Load the pending metrics entry for a javac-fix run."""
    return read_pending_metrics(metrics_repo_root)


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
        "fixes-javac-fail",
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
    metrics_entry = load_fix_javac_metrics(
        metrics_repo_root=metrics_repo_root,
        old_coordinates=old_coordinates,
        new_coordinates=new_coordinates,
    )
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
    return parser


def parse_flags(argv_list):
    """Parse CLI flags and resolve repository paths."""
    parser = build_parser()
    flags = parser.parse_args(argv_list)
    repo_path, metrics_repo_path = resolve_repo_paths(
        explicit_repo_path=flags.reachability_metadata_path,
        explicit_metrics_repo_path=flags.metrics_repo_path,
    )
    return flags.coordinates, flags.new_version, repo_path, metrics_repo_path, flags.issue_number


def push_current_branch_to_origin(
        old_coordinates: str,
        new_version: str,
        repo_path: str,
        metrics_repo_path: str | None = None,
        include_in_repo_metrics: bool = False,
):
    """Create a feature branch, stage and commit changes, and push to the remote."""
    group, artifact, old_version = parse_coordinate_parts(old_coordinates)
    new_coordinates = f"{group}:{artifact}:{new_version}"

    branch = build_ai_branch_name(
        f"fix-javac-{group}-{artifact}-{new_version}",
        cwd=repo_path,
    )
    delete_remote_branch_if_exists(branch, cwd=repo_path)
    subprocess.run(
        ["git", "switch", "-C", branch],
        check=True,
        cwd=repo_path,
    )
    stage_and_commit(
        group,
        artifact,
        new_version,
        new_coordinates,
        repo_path,
        metrics_repo_path=metrics_repo_path,
        include_in_repo_metrics=include_in_repo_metrics,
    )
    base_ref = fetch_pr_base_ref(repo_path, REPO, BASE_BRANCH)
    subprocess.run(["git", "rebase", base_ref], check=True, cwd=repo_path)
    run_local_ci_verification(
        repo_path=repo_path,
        coordinates=new_coordinates,
        base_commit=base_ref,
        metrics_repo_path=metrics_repo_path,
    )
    assert_no_dynamic_access_category_regressions(repo_path, old_coordinates, new_coordinates)

    run_git_transport(["push", "origin", branch], cwd=repo_path)

    return branch, group, artifact, old_version, new_coordinates


def main(argv=None):
    ensure_gh_authenticated()

    old_coordinates, new_version, repo_path, metrics_repo_path, issue_number = parse_flags(
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
    )

if __name__ == "__main__":
    if any(a in ("-h", "--help") for a in sys.argv[1:]):
        build_parser().print_help()
        sys.exit(0)
    main()
