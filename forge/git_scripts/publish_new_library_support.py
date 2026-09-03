# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import argparse
import json
import os
import sys
from dataclasses import dataclass

from git_scripts.common_git import (
    ensure_gh_authenticated,
    parse_coordinate_parts,
    find_issue_for_coordinates as find_issue_common,
    load_library_stats,
)
from git_scripts.branch_publication import (
    BASE_BRANCH,
    REPO,
    publish_branch,
    stage_library_version_paths,
)
from git_scripts.publication_descriptor import descriptor_input_from_pending_metrics
from utility_scripts.metrics_writer import (
    collect_new_library_support_quality_issues,
    read_pending_metrics,
)
from utility_scripts.dynamic_access_exhaust_report import (
    DynamicAccessExhaustReport,
    find_dynamic_access_exhaust_report_path,
)
from utility_scripts.dynamic_access_report import DynamicAccessCallSite, load_dynamic_access_coverage_report
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
        prog="publish_new_library_support.py",
        description=(
            f"Create and push a verified feature branch with new library support and its publication "
            f"descriptor to '{REPO}'; trusted GitHub Actions open the pull request against "
            f"base branch '{BASE_BRANCH}'.\n\n"
        ),
        epilog=(
            "Example:\n"
            "  python3 git_scripts/publish_new_library_support.py \\\n"
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
        issue_number: int | None = None,
        chunked_dynamic_access: bool = False,
        chunk_final: bool = True,
):
    """Create, locally verify, and push a feature branch for PR publication.

    Local CI-equivalent verification (§FS-local-ci-equivalent-verification) is
    required before pushing the branch that will back a PR — the precondition
    for PR eligibility (§AR-pr-eligibility).
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


    def descriptor_input():
        if issue_number is None:
            raise ValueError("Publication requires an explicit issue number")
        exhaust_report = load_dynamic_access_exhaust_report(repo_path, coordinates)
        evidence = load_dynamic_access_metadata_evidence(repo_path, coordinates)
        render = {
            "library_stats": load_library_stats(repo_path, coordinates),
            "dynamic_access": None if exhaust_report is None else exhaust_report.to_dict(),
            "dynamic_access_evidence": None if evidence is None else {
                "covered_call_sites": evidence.covered_call_sites,
                "metadata_rules": evidence.metadata_rules,
            },
        }
        return descriptor_input_from_pending_metrics(
            metrics_repo_path=metrics_repo_path,
            issue_number=issue_number,
            task_type="library-new-request",
            template_type="library-new-request",
            chunked_dynamic_access=chunked_dynamic_access,
            chunk_final=chunk_final or not chunked_dynamic_access,
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
    )

    return new_branch


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
    if issue_number is None:
        issue_number = find_issue_common(coordinates, REPO)

    push_current_branch_to_origin(
        coordinates=coordinates,
        repo_path=repo_path,
        metrics_repo_path=metrics_repo_path,
        issue_number=issue_number,
        chunked_dynamic_access=chunked_dynamic_access,
        chunk_final=chunk_final,
    )


if __name__ == "__main__":
    if any(a in ("-h", "--help") for a in sys.argv[1:]):
        build_parser().print_help()
        sys.exit(0)
    main()
