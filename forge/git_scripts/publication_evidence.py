# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Dynamic-access evidence a publication descriptor carries for the pull request.

The covered call sites and the metadata rules they justify, bounded for the PR
body (§AR-pr-body).
"""

import json
import os
from dataclasses import dataclass

from git_scripts.common_git import parse_coordinate_parts
from utility_scripts.dynamic_access_report import DynamicAccessCallSite, load_dynamic_access_coverage_report

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
