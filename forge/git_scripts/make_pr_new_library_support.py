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
from dataclasses import dataclass

from git_scripts.common_git import (
    ensure_gh_authenticated,
    gh,
    get_origin_owner,
    parse_coordinate_parts,
    stage_and_commit as stage_and_commit_common,
    find_issue_for_coordinates as find_issue_common,
    get_model_display_name,
    get_agent_name,
    load_library_stats,
    format_stats_section,
    format_forge_revision_section,
    build_ai_branch_name,
    delete_remote_branch_if_exists,
    get_configured_reviewers,
)
from utility_scripts.metrics_writer import (
    collect_new_library_support_quality_issues,
    read_pending_metrics,
)
from utility_scripts.large_library_progress import LABEL_LARGE_LIBRARY_PART, LargeLibraryProgressState
from utility_scripts.library_stats import stats_artifact_dir
from utility_scripts.dynamic_access_report import DynamicAccessCallSite, load_dynamic_access_coverage_report
from utility_scripts.local_ci_verification import (
    HUMAN_INTERVENTION_LABEL,
    LOCAL_CI_VERIFICATION_KEY,
    format_local_ci_verification_pr_section,
    local_ci_requires_human_intervention,
    run_local_ci_verification,
)
from utility_scripts.repo_path_resolver import resolve_repo_roots

REPO = "oracle/graalvm-reachability-metadata"
BASE_BRANCH = 'master'
REVIEWERS = get_configured_reviewers()
DYNAMIC_ACCESS_METADATA_ENTRY_NOTE_RATIO = 1.75
DYNAMIC_ACCESS_METADATA_ENTRY_NOTE_MAX_ITEMS = 8


@dataclass(frozen=True)
class DynamicAccessMetadataEvidence:
    covered_call_sites: list[str]
    metadata_rules: list[str]


def _extract_covered_dynamic_access_calls(library_stats: dict | None) -> int | None:
    """Return the total covered dynamic-access call count from library stats."""
    if not isinstance(library_stats, dict):
        return None

    dynamic_access = library_stats.get("dynamicAccess")
    if not isinstance(dynamic_access, dict):
        return None

    covered_calls = dynamic_access.get("coveredCalls")
    if not isinstance(covered_calls, int):
        return None
    return covered_calls


def format_dynamic_access_metadata_entry_note(
        metadata_entries: int,
        library_stats: dict | None,
        evidence: DynamicAccessMetadataEvidence | None = None,
) -> str:
    """Explain large dynamic-access/metadata-entry count differences in generated PR bodies."""
    covered_calls = _extract_covered_dynamic_access_calls(library_stats)
    if covered_calls is None:
        return ""
    if metadata_entries > 0 and covered_calls < metadata_entries * DYNAMIC_ACCESS_METADATA_ENTRY_NOTE_RATIO:
        return ""
    if metadata_entries <= 0 and covered_calls <= 0:
        return ""

    lines = [
        "",
        "### Metadata/dynamic-access evidence",
        "",
        f"- Covered dynamic-access calls: {covered_calls}",
        f"- Metadata entries: {metadata_entries}",
        "- These counts are different dimensions: covered dynamic-access calls count observed call sites, "
        "while metadata entries count generated reachability-config items. Depending on the access type, "
        "a single metadata rule can cover multiple observed call sites, or no shipped rule may be required "
        "when the covered access does not target fixed library-owned metadata.",
    ]
    if evidence and evidence.covered_call_sites:
        lines.append("- Covered call sites:")
        lines.extend(f"  - {call_site}" for call_site in evidence.covered_call_sites)
    if evidence and evidence.metadata_rules:
        lines.append("- Generated metadata rules:")
        lines.extend(f"  - {metadata_rule}" for metadata_rule in evidence.metadata_rules)
    lines.append(
        "- Use the call-site and metadata-rule lists together to review whether the observed dynamic-access "
        "paths are explained by the generated reachability metadata."
    )
    return "\n".join(lines) + "\n"


def load_dynamic_access_metadata_evidence(repo_path: str, coordinates: str) -> DynamicAccessMetadataEvidence | None:
    """Load covered dynamic-access call sites and generated metadata rules for PR evidence."""
    group, artifact, version = parse_coordinate_parts(coordinates)
    report_path = os.path.join(
        repo_path,
        "tests",
        "src",
        group,
        artifact,
        version,
        "build",
        "reports",
        "dynamic-access",
        "dynamic-access-coverage.json",
    )
    metadata_path = os.path.join(
        repo_path,
        "metadata",
        group,
        artifact,
        version,
        "reachability-metadata.json",
    )

    covered_call_sites = _load_covered_dynamic_access_call_sites(report_path)
    metadata_rules = _load_metadata_rule_summaries(metadata_path)
    if not covered_call_sites and not metadata_rules:
        return None
    return DynamicAccessMetadataEvidence(
        covered_call_sites=covered_call_sites,
        metadata_rules=metadata_rules,
    )


def _load_covered_dynamic_access_call_sites(report_path: str) -> list[str]:
    if not os.path.isfile(report_path):
        return []
    report = load_dynamic_access_coverage_report(report_path)
    call_sites = [
        _format_dynamic_access_call_site(call_site)
        for class_coverage in report.classes
        for call_site in class_coverage.call_sites
        if call_site.covered
    ]
    return _limit_evidence_items(call_sites)


def _format_dynamic_access_call_site(call_site: DynamicAccessCallSite) -> str:
    line_suffix = ""
    if call_site.line is not None:
        line_suffix = f" (line {call_site.line})"
    return f"[{call_site.metadata_type}] {call_site.tracked_api} <- {call_site.frame}{line_suffix}"


def _load_metadata_rule_summaries(metadata_path: str) -> list[str]:
    if not os.path.isfile(metadata_path):
        return []
    with open(metadata_path, "r", encoding="utf-8") as metadata_file:
        metadata = json.load(metadata_file)

    summaries = []
    reflection = metadata.get("reflection")
    if isinstance(reflection, list):
        for segment in reflection:
            if not isinstance(segment, dict):
                continue
            summaries.extend(_summarize_reflection_segment(segment))

    resources = metadata.get("resources")
    if isinstance(resources, list):
        for segment in resources:
            if not isinstance(segment, dict):
                continue
            summaries.extend(_summarize_resource_segment(segment))

    return _limit_evidence_items(summaries)


def _summarize_reflection_segment(segment: dict) -> list[str]:
    type_name = segment.get("type")
    if not isinstance(type_name, str) or not type_name:
        return []

    condition_prefix = _format_metadata_condition_prefix(segment.get("condition"))
    methods = segment.get("methods")
    if isinstance(methods, list) and methods:
        return [
            f"{condition_prefix}make `{type_name}.{_format_metadata_method(method)}` available for reflection"
            for method in methods
            if isinstance(method, dict)
        ]

    return [f"{condition_prefix}make `{type_name}` available for reflection"]


def _format_metadata_method(method: dict) -> str:
    method_name = method.get("name")
    if not isinstance(method_name, str) or not method_name:
        method_name = "<unnamed>"
    parameter_types = method.get("parameterTypes")
    if not isinstance(parameter_types, list):
        parameter_types = []
    parameter_list = ", ".join(str(parameter_type) for parameter_type in parameter_types)
    return f"{method_name}({parameter_list})"


def _summarize_resource_segment(segment: dict) -> list[str]:
    condition_prefix = _format_metadata_condition_prefix(segment.get("condition"))
    pattern = segment.get("pattern")
    if isinstance(pattern, str) and pattern:
        return [f"{condition_prefix}include resource pattern `{pattern}`"]

    includes = segment.get("includes")
    if isinstance(includes, list):
        summaries = []
        for include in includes:
            if not isinstance(include, dict):
                continue
            include_pattern = include.get("pattern")
            if isinstance(include_pattern, str) and include_pattern:
                summaries.append(f"{condition_prefix}include resource pattern `{include_pattern}`")
        return summaries

    return []


def _format_metadata_condition_prefix(condition: object) -> str:
    if not isinstance(condition, dict):
        return ""
    type_reached = condition.get("typeReached")
    if isinstance(type_reached, str) and type_reached:
        return f"when `{type_reached}` is reached, "
    return ""


def _limit_evidence_items(items: list[str]) -> list[str]:
    if len(items) <= DYNAMIC_ACCESS_METADATA_ENTRY_NOTE_MAX_ITEMS:
        return items
    omitted = len(items) - DYNAMIC_ACCESS_METADATA_ENTRY_NOTE_MAX_ITEMS
    return [
        *items[:DYNAMIC_ACCESS_METADATA_ENTRY_NOTE_MAX_ITEMS],
        f"... and {omitted} more",
    ]


def build_pull_request_body(
        issue_no,
        coordinates,
        model_display_name,
        agent_name,
        strategy_name,
        run_status,
        metrics,
        library_stats=None,
        post_generation_intervention=None,
        is_large_library_part=False,
        is_final_large_library_part=True,
        large_library_part=None,
        series_id=None,
        local_ci_verification=None,
        dynamic_access_evidence: DynamicAccessMetadataEvidence | None = None,
):
    """Build the PR body with metrics, strategy name, and optional stats."""
    input_tokens_used = metrics.get("input_tokens_used", 0)
    output_tokens_used = metrics.get("output_tokens_used", 0)
    cached_input_tokens_used = metrics.get("cached_input_tokens_used", 0)
    entries_found = int(metrics.get("metadata_entries", 0) or 0)
    test_only_metadata_entries = int(metrics.get("test_only_metadata_entries", 0) or 0)
    iterations = metrics.get("iterations", 0)
    code_coverage_percent = metrics.get("code_coverage_percent", 0)
    generated_loc = metrics.get("generated_loc", 0)
    tested_library_loc = metrics.get("tested_library_loc", 0)
    test_only_metadata_entries_line = ""
    if test_only_metadata_entries > 0:
        test_only_metadata_entries_line = f"- Test-only metadata entries: {test_only_metadata_entries}\n"

    issue_reference = f"Fixes: #{issue_no}"
    if is_large_library_part and not is_final_large_library_part:
        issue_reference = f"Refs: #{issue_no}"
    part_line = ""
    if is_large_library_part:
        part_line = f"- Large-library series: `{series_id or 'unknown'}`\n- Part: {large_library_part}\n"

    body = f"""
## What does this PR do?

{issue_reference}

This PR introduces tests and metadata for {coordinates}, enabling support for this library.

Summary:
{part_line}\
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
- Generated lines of code: {generated_loc}
- Tested library lines of code: {tested_library_loc}
"""
    body += format_dynamic_access_metadata_entry_note(entries_found, library_stats, dynamic_access_evidence)
    body += "\n" + format_forge_revision_section() + "\n"
    if library_stats:
        body += "\n" + format_stats_section(library_stats) + "\n"
    if post_generation_intervention:
        body += "\n### Post-Generation Intervention\n\n"
        body += f"- Stage: `{post_generation_intervention.get('stage', 'unknown')}`\n\n"
        body += f"- Intervention file: `{post_generation_intervention.get('intervention_file', 'unknown')}`\n\n"
        body += str(post_generation_intervention.get("analysis_markdown", "")).strip() + "\n"
    body += format_local_ci_verification_pr_section(local_ci_verification)

    return body


def stage_and_commit(
        group,
        artifact,
        library_version,
        coordinates,
        repo_path,
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

    commit_message = f"Add support for {coordinates}"
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
        branch,
        coordinates,
        metrics_repo_root,
        repo_path,
        issue_number=None,
        large_library_part=None,
        is_final_large_library_part=True,
        series_id=None,
):
    """Create a GitHub pull request for the current branch, linking to the matching issue."""
    if shutil.which("gh") is None:
        print("gh CLI not found. Skipping PR creation.")
        return

    origin_owner = get_origin_owner(cwd=repo_path)

    view = gh("pr", "view", "--repo", REPO, "--head", f"{origin_owner}:{branch}", check=False)

    if view.returncode == 0:
        print(f"Pull request already exists for branch {branch}.")
        return

    issue_no = issue_number if issue_number is not None else find_issue_common(coordinates, REPO)

    # Read evaluations from the pending metrics file
    matched = read_pending_metrics(metrics_repo_root)

    metrics = matched.get("metrics", {})
    strategy_name = matched.get("strategy_name", "")
    model_display_name = get_model_display_name(strategy_name)
    agent_name = get_agent_name(strategy_name)
    title = f"[GenAI] Add support for {coordinates} using {model_display_name}"
    if large_library_part is not None:
        title = f"{title} (part {large_library_part})"

    library_stats = load_library_stats(repo_path, coordinates)
    dynamic_access_evidence = load_dynamic_access_metadata_evidence(repo_path, coordinates)
    body = build_pull_request_body(
        issue_no=issue_no,
        coordinates=coordinates,
        model_display_name=model_display_name,
        agent_name=agent_name,
        strategy_name=strategy_name,
        run_status=str(matched.get("status") or "unknown"),
        metrics=metrics,
        library_stats=library_stats,
        post_generation_intervention=matched.get("post_generation_intervention"),
        local_ci_verification=matched.get(LOCAL_CI_VERIFICATION_KEY),
        is_large_library_part=large_library_part is not None,
        is_final_large_library_part=is_final_large_library_part,
        large_library_part=large_library_part,
        series_id=series_id,
        dynamic_access_evidence=dynamic_access_evidence,
    )

    cmd = [
        "gh", "pr", "create",
        "--repo", REPO,
        "--title", title,
        "--body", body,
        "--base", BASE_BRANCH,
        "--head", f"{origin_owner}:{branch}",
        "--label", "GenAI",
        "--label", "library-new-request",
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


def build_parser():
    parser = argparse.ArgumentParser(
        prog="make_pr_new_library_support.py",
        description=(
            f"Create and push a feature branch with new library support and open a GitHub Pull Request to '{REPO}' "
            f"against base branch '{BASE_BRANCH}'.\n\n"
        ),
        epilog=(
            "Example:\n"
            "  python3 git_scripts/make_pr_new_library_support.py \\\n"
            "      --coordinates com.example:lib:1.2.3 \\\n"
            "      --reachability-metadata-path /path/to/graalvm-reachability-metadata\\\n"
            "      --metrics-repo-path /path/to/metrics_repo_root\n\n"
            "Notes:\n"
            "  - Requires the 'gh' CLI configured and authenticated with access to the target repository.\n"
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


def resolve_repo_paths(
        explicit_repo_path: str | None,
        explicit_metrics_repo_path: str | None,
):
    """Resolve repo and metrics paths similar to add_new_library_support.

    If explicit paths are provided via CLI, use them as-is. Otherwise, use
    local clones under 'local_repositories'.
    """

    return resolve_repo_roots(
        explicit_repo_path,
        explicit_metrics_repo_path,
    )


def parse_flags(argv_list):
    """Parse CLI flags and return coordinates, repo_path, metrics_repo_path."""
    parser = build_parser()
    flags = parser.parse_args(argv_list)

    coordinates = flags.coordinates
    resolved_repo_path, resolved_metrics_repo_path = resolve_repo_paths(
        flags.reachability_metadata_path,
        flags.metrics_repo_path,
    )

    return (
        coordinates,
        resolved_repo_path,
        resolved_metrics_repo_path,
        flags.issue_number,
        flags.large_library_part,
        flags.large_library_final,
        flags.series_id,
        flags.large_library_state_path,
    )


def push_current_branch_to_origin(
        coordinates,
        repo_path,
        metrics_repo_path=None,
        include_in_repo_metrics=False,
        large_library_part=None,
):
    """Create and push a feature branch, returning branch and coordinates."""
    group, artifact, library_version = parse_coordinate_parts(coordinates)

    branch_suffix = f"add-lib-support-{group}-{artifact}-{library_version}"
    if large_library_part is not None:
        branch_suffix = f"{branch_suffix}-part-{large_library_part:04d}"
    new_branch = build_ai_branch_name(branch_suffix, cwd=repo_path)
    delete_remote_branch_if_exists(new_branch, cwd=repo_path)
    subprocess.run(["git", "switch", "-C", new_branch], check=True, cwd=repo_path)

    stage_and_commit(
        group,
        artifact,
        library_version,
        coordinates,
        repo_path,
        metrics_repo_path=metrics_repo_path,
        include_in_repo_metrics=include_in_repo_metrics,
    )

    base_ref = _fetch_pr_base(repo_path)
    subprocess.run(["git", "rebase", base_ref], check=True, cwd=repo_path)
    run_local_ci_verification(
        repo_path=repo_path,
        coordinates=coordinates,
        base_commit=base_ref,
        metrics_repo_path=metrics_repo_path,
    )
    subprocess.run(
        ["git", "push", "origin", new_branch],
        check=True,
        cwd=repo_path,
    )

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


def validate_run_quality(coordinates: str, metrics_repo_path: str) -> None:
    """Raise ValueError if the run metrics are not good enough for a PR."""
    matched = read_pending_metrics(metrics_repo_path)
    quality_issues = collect_new_library_support_quality_issues(matched)
    if quality_issues:
        details = "; ".join(quality_issues)
        raise ValueError(f"Refusing to create PR for {coordinates}: {details}")


def main(argv=None):
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
    validate_run_quality(coordinates, metrics_repo_path)

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
