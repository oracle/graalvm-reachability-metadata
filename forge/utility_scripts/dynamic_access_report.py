# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import json
import os
from dataclasses import dataclass


@dataclass(frozen=True)
class DynamicAccessCallSite:
    metadata_type: str
    tracked_api: str
    frame: str
    line: int | None
    covered: bool

    @property
    def key(self) -> str:
        # Null-separated composite key used to match call sites across reports
        # when computing coverage deltas (see compute_class_delta).
        return "\u0000".join((self.metadata_type, self.tracked_api, self.frame))


@dataclass(frozen=True)
class DynamicAccessClass:
    class_name: str
    source_file: str | None
    resolved_source_file: str | None
    total_calls: int
    covered_calls: int
    call_sites: list[DynamicAccessCallSite]

    @property
    def uncovered_call_sites(self) -> list[DynamicAccessCallSite]:
        return [call_site for call_site in self.call_sites if not call_site.covered]

    @property
    def uncovered_calls(self) -> int:
        return self.total_calls - self.covered_calls


@dataclass(frozen=True)
class DynamicAccessCoverageReport:
    coordinate: str
    has_dynamic_access: bool
    total_calls: int
    covered_calls: int
    classes: list[DynamicAccessClass]

    def get_class(self, class_name: str) -> DynamicAccessClass | None:
        for class_coverage in self.classes:
            if class_coverage.class_name == class_name:
                return class_coverage
        return None

    def next_uncovered_class(self, exhausted_classes: set[str]) -> DynamicAccessClass | None:
        for class_coverage in self.classes:
            if class_coverage.uncovered_calls > 0 and class_coverage.class_name not in exhausted_classes:
                return class_coverage
        return None

    def class_progress(self, class_name: str) -> tuple[int, int] | None:
        for index, class_coverage in enumerate(self.classes, start=1):
            if class_coverage.class_name == class_name:
                return index, len(self.classes)
        return None


@dataclass(frozen=True)
class DynamicAccessClassDelta:
    newly_covered: list[DynamicAccessCallSite]
    still_uncovered: list[DynamicAccessCallSite]


def load_dynamic_access_coverage_report(
        report_path: str,
        source_context_files: list[str] | None = None,
) -> DynamicAccessCoverageReport:
    if not os.path.isfile(report_path):
        raise FileNotFoundError(report_path)

    with open(report_path, "r", encoding="utf-8") as report_file:
        payload = json.load(report_file)

    classes: list[DynamicAccessClass] = []
    for class_payload in payload.get("classes", []):
        class_name = class_payload["className"]
        source_file = class_payload.get("sourceFile")
        call_sites = [
            DynamicAccessCallSite(
                metadata_type=call_site_payload["metadataType"],
                tracked_api=call_site_payload["trackedApi"],
                frame=call_site_payload["frame"],
                line=call_site_payload.get("line"),
                covered=bool(call_site_payload.get("covered")),
            )
            for call_site_payload in class_payload.get("callSites", [])
        ]
        classes.append(
            DynamicAccessClass(
                class_name=class_name,
                source_file=source_file,
                resolved_source_file=_resolve_source_file(class_name, source_file, source_context_files or []),
                total_calls=int(class_payload.get("totalCalls", 0)),
                covered_calls=int(class_payload.get("coveredCalls", 0)),
                call_sites=call_sites,
            )
        )

    totals = payload.get("totals", {})
    return DynamicAccessCoverageReport(
        coordinate=payload.get("coordinate", ""),
        has_dynamic_access=bool(payload.get("hasDynamicAccess")),
        total_calls=int(totals.get("totalCalls", 0)),
        covered_calls=int(totals.get("coveredCalls", 0)),
        classes=classes,
    )


def compute_class_delta(
        previous_report: DynamicAccessCoverageReport | None,
        current_report: DynamicAccessCoverageReport,
        class_name: str,
) -> DynamicAccessClassDelta:
    """Compare two reports for one class to find what changed between iterations."""
    previous_class = None if previous_report is None else previous_report.get_class(class_name)
    current_class = current_report.get_class(class_name)

    previous_covered_keys = set()
    if previous_class is not None:
        previous_covered_keys = {
            call_site.key for call_site in previous_class.call_sites if call_site.covered
        }

    current_call_sites = [] if current_class is None else current_class.call_sites
    newly_covered = [
        call_site for call_site in current_call_sites if call_site.covered and call_site.key not in previous_covered_keys
    ]
    still_uncovered = [call_site for call_site in current_call_sites if not call_site.covered]
    return DynamicAccessClassDelta(newly_covered=newly_covered, still_uncovered=still_uncovered)


def format_full_report(report: DynamicAccessCoverageReport) -> str:
    """Format all uncovered classes and their call sites for prompt display."""
    lines = []
    for class_coverage in report.classes:
        if class_coverage.uncovered_calls == 0:
            continue
        lines.append(f"### {class_coverage.class_name}")
        source_file = class_coverage.resolved_source_file or class_coverage.source_file
        if source_file:
            lines.append(f"Source: {source_file}")
        lines.append(f"Coverage: {class_coverage.covered_calls}/{class_coverage.total_calls}")
        lines.append("Uncovered call sites:")
        lines.append(format_call_sites(class_coverage.uncovered_call_sites))
        lines.append("")
    if not lines:
        return "All dynamic-access call sites are covered."
    return "\n".join(lines)


def format_call_sites(call_sites: list[DynamicAccessCallSite]) -> str:
    if not call_sites:
        return "- None"
    lines = []
    for call_site in call_sites:
        line_suffix = ""
        if call_site.line is not None:
            line_suffix = f" (line {call_site.line})"
        lines.append(
            f"- [{call_site.metadata_type}] {call_site.tracked_api} <- {call_site.frame}{line_suffix}"
        )
    return "\n".join(lines)


def _resolve_source_file(class_name: str, source_file: str | None, source_context_files: list[str]) -> str | None:
    if not source_file:
        return None

    if os.path.isabs(source_file) and os.path.isfile(source_file):
        return os.path.abspath(source_file)

    if not source_context_files:
        return None

    package_relative_path = _class_relative_source_path(class_name, source_file)
    package_matches = _matching_source_context_files(source_context_files, package_relative_path)
    if len(package_matches) == 1:
        return package_matches[0]

    suffix_matches = _matching_source_context_files(source_context_files, source_file)
    if len(suffix_matches) == 1:
        return suffix_matches[0]

    basename = os.path.basename(source_file)
    basename_matches = [
        os.path.abspath(candidate)
        for candidate in source_context_files
        if os.path.basename(candidate) == basename
    ]
    if len(basename_matches) == 1:
        return basename_matches[0]

    return None


def _class_relative_source_path(class_name: str, source_file: str) -> str:
    file_extension = os.path.splitext(source_file)[1] or ".java"
    package_path = class_name.replace(".", os.sep)
    return f"{package_path}{file_extension}"


def _matching_source_context_files(source_context_files: list[str], relative_path: str) -> list[str]:
    if not relative_path:
        return []

    normalized_relative_path = os.path.normpath(relative_path)
    matches = []
    for candidate in source_context_files:
        absolute_candidate = os.path.abspath(candidate)
        if absolute_candidate.endswith(normalized_relative_path):
            matches.append(absolute_candidate)
    return matches
