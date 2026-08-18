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
from dataclasses import dataclass

from git_scripts.common_git import (
    ensure_gh_authenticated,
    gh,
    get_origin_owner,
    parse_coordinate_parts,
    find_issue_for_coordinates as find_issue_common,
    get_model_display_name,
    get_agent_name,
    load_library_stats,
    format_stats_section,
    format_forge_revision_section,
    run_git_transport,
)
from git_scripts.pr_publication import (
    BASE_BRANCH,
    REPO,
    REVIEWERS,
    bound_pr_body,
    parse_pr_number,
    publish_branch,
    stage_library_version_paths,
)
from utility_scripts.metrics_writer import (
    collect_new_library_support_quality_issues,
    read_pending_metrics,
)
from utility_scripts.dynamic_access_exhaust_report import (
    DynamicAccessExhaustReport,
    find_dynamic_access_exhaust_report_path,
)
from utility_scripts.dynamic_access_report import DynamicAccessCallSite, load_dynamic_access_coverage_report
from utility_scripts.local_ci_verification import (
    HUMAN_INTERVENTION_LABEL,
    LOCAL_CI_VERIFICATION_KEY,
    format_local_ci_verification_pr_section,
    local_ci_requires_human_intervention,
)
from utility_scripts.repo_path_resolver import resolve_repo_roots
from utility_scripts.test_quality_checks import (
    collect_generated_test_validity_issues,
    find_scaffold_placeholder_occurrences,
    format_generated_test_validity_issue,
    format_placeholder_occurrence,
)

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
        chunked_dynamic_access: bool = False,
        chunk_final: bool = True,
        dynamic_access_exhaust_report: DynamicAccessExhaustReport | None = None,
        local_ci_verification=None,
        dynamic_access_evidence: DynamicAccessMetadataEvidence | None = None,
):
    """Build the PR body with metrics, strategy name, stats, and issue linkage.

    Chunked dynamic-access runs use ``Refs`` until the final chunk may close the
    backing issue (§WF-chunked-dynamic-access-pr-linking), following the chunked
    linking contract (§GIT-chunked-linking); the body records the run's tracked
    parameters (§GIT-pr-body) so verification and intervention context stay
    visible to reviewers.
    """
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
    if chunked_dynamic_access and not chunk_final:
        issue_reference = f"Refs: #{issue_no}"
    chunk_line = format_chunked_dynamic_access_summary(
        chunked_dynamic_access,
        dynamic_access_exhaust_report,
    )

    body = f"""
## What does this PR do?

{issue_reference}

This PR introduces tests and metadata for {coordinates}, enabling support for this library.

Summary:
{chunk_line}\
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


def create_pull_request(
        branch,
        coordinates,
        metrics_repo_root,
        repo_path,
        issue_number=None,
        chunked_dynamic_access: bool = False,
        chunk_final: bool = True,
):
    """Create a GitHub pull request for the current branch and matching issue.

    Links the PR to its issue per §GIT-issue-linking and applies the
    workflow PR label, reviewer list, and
    human-intervention visibility.
    """
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
        issue_number=issue_number,
        chunked_dynamic_access=chunked_dynamic_access,
        chunk_final=chunk_final,
    )

    cmd = [
        "gh", "pr", "create",
        "--repo", REPO,
        "--title", title,
        "--body", bound_pr_body(body),
        "--base", BASE_BRANCH,
        "--head", f"{origin_owner}:{branch}",
        "--label", "GenAI",
        "--label", "library-new-request",
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
    title = f"[GenAI] Add support for {coordinates} using {model_display_name}"
    if chunked_dynamic_access and not chunk_final:
        title = f"{title} (chunked dynamic-access)"
    body = build_pull_request_body(
        issue_no=issue_no,
        coordinates=coordinates,
        model_display_name=model_display_name,
        agent_name=agent_name,
        strategy_name=strategy_name,
        run_status=str(matched.get("status") or "unknown"),
        metrics=metrics,
        library_stats=load_library_stats(repo_path, coordinates),
        post_generation_intervention=matched.get("post_generation_intervention"),
        local_ci_verification=matched.get(LOCAL_CI_VERIFICATION_KEY),
        chunked_dynamic_access=chunked_dynamic_access,
        chunk_final=chunk_final,
        dynamic_access_exhaust_report=load_dynamic_access_exhaust_report(repo_path, coordinates),
        dynamic_access_evidence=load_dynamic_access_metadata_evidence(repo_path, coordinates),
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


def parse_flags(argv_list):
    """Parse CLI flags and return coordinates, repo_path, metrics_repo_path."""
    parser = build_parser()
    flags = parser.parse_args(argv_list)

    coordinates = flags.coordinates
    resolved_repo_path, resolved_metrics_repo_path = resolve_repo_roots(
        flags.reachability_metadata_path,
        flags.metrics_repo_path,
    )

    return (
        coordinates,
        resolved_repo_path,
        resolved_metrics_repo_path,
        flags.issue_number,
        flags.chunked_dynamic_access,
        flags.chunk_final,
    )


def push_current_branch_to_origin(
        coordinates,
        repo_path,
        metrics_repo_path=None,
        chunked_dynamic_access: bool = False,
        chunk_final: bool = True,
):
    """Create, locally verify, and push a feature branch for PR publication.

    Local CI-equivalent verification (§FS-local-ci-equivalent-verification) is
    required before pushing the branch that will back a PR — the precondition
    for PR eligibility (§GIT-pr-eligibility).
    """
    group, artifact, library_version = parse_coordinate_parts(coordinates)

    branch_suffix = f"add-lib-support-{group}-{artifact}-{library_version}"
    if chunked_dynamic_access:
        branch_suffix = f"{branch_suffix}-chunked"

    def stage() -> None:
        remove_dynamic_access_exhaust_report_for_final_chunk(
            repo_path,
            coordinates,
            chunked_dynamic_access,
            chunk_final,
        )
        stage_library_version_paths(
            group, artifact, library_version, repo_path, f"Add support for {coordinates}",
        )

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


def validate_run_quality(coordinates: str, metrics_repo_path: str, repo_path: str) -> None:
    """Raise ValueError if the run metrics are not good enough for a PR."""
    matched = read_pending_metrics(metrics_repo_path)
    quality_issues = collect_new_library_support_quality_issues(matched)
    group, artifact, version = coordinates.split(":")
    test_source_root = os.path.join(repo_path, "tests", "src", group, artifact, version, "src", "test")
    generated_test_validity_issues = collect_generated_test_validity_issues(test_source_root)
    quality_issues.extend(
        "suspicious generated test target requires human review: "
        f"{format_generated_test_validity_issue(issue, repo_path)}"
        for issue in generated_test_validity_issues
    )
    if quality_issues:
        details = "; ".join(quality_issues)
        raise ValueError(f"Refusing to create PR for {coordinates}: {details}")


def validate_no_scaffold_placeholders(coordinates: str, repo_path: str) -> None:
    """Raise ValueError if generated tests still contain scaffold placeholder text."""
    group, artifact, library_version = parse_coordinate_parts(coordinates)
    module_dir = os.path.join(repo_path, "tests", "src", group, artifact, library_version)
    occurrences = find_scaffold_placeholder_occurrences(module_dir)
    if occurrences:
        details = ", ".join(
            format_placeholder_occurrence(occurrence, repo_path)
            for occurrence in occurrences
        )
        raise ValueError(f"Refusing to create PR for {coordinates}: scaffold placeholder remains in {details}")


def main(argv=None):
    (
        coordinates,
        repo_path,
        metrics_repo_path,
        issue_number,
        chunked_dynamic_access,
        chunk_final,
    ) = parse_flags(argv if argv is not None else sys.argv[1:])

    ensure_gh_authenticated()
    validate_run_quality(coordinates, metrics_repo_path, repo_path)
    validate_no_scaffold_placeholders(coordinates, repo_path)

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
