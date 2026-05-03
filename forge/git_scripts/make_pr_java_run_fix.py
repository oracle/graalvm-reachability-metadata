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
    get_model_display_name,
    get_agent_name,
    format_stats_diff,
    format_forge_revision_section,
    assert_no_dynamic_access_category_regressions,
    build_ai_branch_name,
    delete_remote_branch_if_exists,
)
from git_scripts.make_pr_javac_fix import generate_diff_text
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
import shutil

REPO = 'oracle/graalvm-reachability-metadata'
BASE_BRANCH = 'master'
REVIEWERS = ["vjovanov", "jormundur00", "kimeta"]
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
):
    """Create a GitHub pull request for the java-run fix branch."""
    if shutil.which("gh") is None:
        print("gh CLI not found. Skipping PR creation.")
        return

    origin_owner = get_origin_owner(cwd=repo_path)

    view = gh("pr", "view", "--repo", REPO, "--head", f"{origin_owner}:{branch}", check=False)

    if view.returncode == 0:
        print(f"Pull request already exists for branch {branch}.")
        return

    assert_no_dynamic_access_category_regressions(repo_path, old_coordinates, new_coordinates)

    issue_no = find_issue_common(new_coordinates, REPO)
    metrics_entry = read_pending_metrics(metrics_repo_root)
    metrics = metrics_entry.get("metrics", {})

    strategy_name = metrics_entry.get("strategy_name", "")
    model_display_name = get_model_display_name(strategy_name)
    agent_name = get_agent_name(strategy_name)
    title = f"[GenAI] Test fix for {new_coordinates} using {model_display_name}"

    input_tokens_used = int(metrics.get("input_tokens_used", 0))
    cached_input_tokens_used = int(metrics.get("cached_input_tokens_used", 0) or 0)
    output_tokens_used = int(metrics.get("output_tokens_used", 0))
    entries_found = int(metrics.get("metadata_entries", 0))
    test_only_metadata_entries = int(metrics.get("test_only_metadata_entries", 0) or 0)
    iterations = int(metrics.get("iterations", 0))
    code_coverage_percent = metrics.get("code_coverage_percent", 0)
    previous_library_metadata_entries = int(metrics.get("previous_library_metadata_entries", 0))
    previous_library_test_only_metadata_entries = int(metrics.get("previous_library_test_only_metadata_entries", 0) or 0)
    previous_library_coverage_percent = metrics.get("previous_library_coverage_percent", 0)
    test_only_metadata_entries_line = ""
    if test_only_metadata_entries > 0:
        test_only_metadata_entries_line = f"- Test-only metadata entries: {test_only_metadata_entries}\n"
    previous_test_only_metadata_entries_line = ""
    if previous_library_test_only_metadata_entries > 0:
        previous_test_only_metadata_entries_line = (
            f"- Previous library version test-only metadata entries: {previous_library_test_only_metadata_entries}\n"
        )

    diff_text = generate_diff_text(group, artifact, old_version, new_version, repo_path)
    stats_section = format_stats_diff(repo_path, old_coordinates, new_coordinates)

    body = f"""## What does this PR do?

Fixes: #{issue_no}

This PR provides test fixes and new metadata for {new_coordinates}, addressing runtime java test failures caused by changes in the updated library version.

Summary:
- Strategy: {strategy_name}
- Agent: {agent_name}
- Model: {model_display_name}
- Input tokens: {input_tokens_used}
- Cached input tokens: {cached_input_tokens_used}
- Output tokens: {output_tokens_used}
- Metadata entries: {entries_found}
{test_only_metadata_entries_line}\
- Iterations: {iterations}
- Library coverage percentage: {code_coverage_percent}
- Previous library version metadata entries: {previous_library_metadata_entries}
{previous_test_only_metadata_entries_line}\
- Previous library version coverage percentage: {previous_library_coverage_percent}

{format_forge_revision_section()}
{stats_section}
**Comparison between existing test version and AI-Generated update**

```diff
{diff_text}
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
        "fixes-java-run-fail",
        "--label",
        "GenAI",
    ]
    if local_ci_requires_human_intervention(metrics_entry.get(LOCAL_CI_VERIFICATION_KEY)):
        cmd.extend(["--label", HUMAN_INTERVENTION_LABEL])
    if REVIEWERS:
        for reviewer in REVIEWERS:
            cmd.extend(["--reviewer", reviewer])
    gh(*cmd[1:])


def build_parser():
    parser = argparse.ArgumentParser(
        prog="make_pr_java_run_fix.py",
        description=(
            f"Create and push a feature branch with java-run fixes and open a GitHub Pull Request to '{REPO}' "
            f"against base branch '{BASE_BRANCH}'. Metrics are loaded from fix_java_run_fail JSON output.\n\n"
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
    return parser


def parse_flags(argv_list):
    """Parse CLI flags and resolve repository paths."""
    parser = build_parser()
    flags = parser.parse_args(argv_list)
    repo_path, metrics_repo_path = resolve_repo_paths(
        explicit_repo_path=flags.reachability_metadata_path,
        explicit_metrics_repo_path=flags.metrics_repo_path,
    )
    return flags.coordinates, flags.new_version, repo_path, metrics_repo_path


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
        f"fix-java-run-{group}-{artifact}-{new_version}",
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

    subprocess.run(
        ["git", "push", "origin", branch],
        check=True,
        cwd=repo_path,
    )

    return branch, group, artifact, old_version, new_coordinates


def main(argv=None):
    ensure_gh_authenticated()

    old_coordinates, new_version, repo_path, metrics_repo_path = parse_flags(
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
    )

if __name__ == "__main__":
    if any(a in ("-h", "--help") for a in sys.argv[1:]):
        build_parser().print_help()
        sys.exit(0)
    main()
