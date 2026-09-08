# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Exact JaCoCo method coverage for code-coverage workflows.

JaCoCo is the authoritative coverage source in both workflow phases. This
module preserves every reported method's complete JVM identity and source
evidence so overloads cannot inherit one another's status
(§AR-code-coverage-improvement.3.1).
"""

from __future__ import annotations

from dataclasses import dataclass
import os
from typing import NamedTuple
import xml.etree.ElementTree as ET

from utility_scripts.code_coverage_model import MethodRef, parse_jvm_descriptor


class JacocoReportError(RuntimeError):
    """Raised when required JaCoCo evidence is missing or malformed."""


@dataclass(frozen=True)
class JacocoMethodCoverage:
    """Coverage and source evidence for one exact JaCoCo method record."""

    method_ref: MethodRef
    covered: bool
    source_path: str | None
    source_line: int | None
    report_paths: tuple[str, ...]

    @property
    def status(self) -> str:
        """Return the stable report status for this method."""
        return "covered" if self.covered else "uncovered"


class JacocoLineCoverage(NamedTuple):
    """Instruction and branch counters for one source line."""

    mi: int
    ci: int
    mb: int
    cb: int

    @property
    def covered(self) -> bool:
        """Whether JaCoCo observed any instruction on this line."""
        return self.ci > 0


@dataclass(frozen=True)
class JacocoCoverage:
    """Method and source-line evidence loaded from the same XML reports."""

    methods: dict[str, JacocoMethodCoverage]
    lines: dict[str, dict[int, JacocoLineCoverage]]


def _source_path(class_element: ET.Element) -> str | None:
    source_file: str = class_element.get("sourcefilename") or ""
    class_name: str = class_element.get("name") or ""
    if not source_file:
        return None
    package_path: str = class_name.rpartition("/")[0]
    if not package_path:
        return source_file
    return os.path.join(package_path, source_file).replace(os.sep, "/")


def _source_line(method_element: ET.Element, method_id: str, report_path: str) -> int | None:
    raw_line: str | None = method_element.get("line")
    if raw_line is None:
        return None
    try:
        return int(raw_line)
    except ValueError as error:
        raise JacocoReportError(
            f"JaCoCo report '{report_path}' has invalid source line "
            f"'{raw_line}' for '{method_id}'."
        ) from error


def _int_attribute(
        element: ET.Element,
        name: str,
        context: str,
        report_path: str,
) -> int:
    raw_value: str = element.get(name, "")
    try:
        value: int = int(raw_value)
    except ValueError as error:
        raise JacocoReportError(
            f"JaCoCo report '{report_path}' has invalid {name} count "
            f"'{raw_value}' for {context}."
        ) from error
    if value < 0:
        raise JacocoReportError(
            f"JaCoCo report '{report_path}' has negative {name} count "
            f"'{raw_value}' for {context}."
        )
    return value


def _sourcefile_path(package_element: ET.Element, sourcefile_element: ET.Element) -> str:
    source_file: str = sourcefile_element.get("name") or ""
    if not source_file:
        raise JacocoReportError("JaCoCo sourcefile record has no name.")
    package_path: str = package_element.get("name") or ""
    if not package_path:
        return source_file
    return os.path.join(package_path, source_file).replace(os.sep, "/")


def _line_coverage(
        line_element: ET.Element,
        source_path: str,
        report_path: str,
) -> tuple[int, JacocoLineCoverage]:
    context: str = f"'{source_path}' line '{line_element.get('nr', '')}'"
    line: int = _int_attribute(line_element, "nr", context, report_path)
    return line, JacocoLineCoverage(
        mi=_int_attribute(line_element, "mi", context, report_path),
        ci=_int_attribute(line_element, "ci", context, report_path),
        mb=_int_attribute(line_element, "mb", context, report_path),
        cb=_int_attribute(line_element, "cb", context, report_path),
    )


def _merge_line_coverage(
        previous: JacocoLineCoverage,
        current: JacocoLineCoverage,
) -> JacocoLineCoverage:
    """Merge repeated reports conservatively toward observed coverage."""
    return JacocoLineCoverage(
        mi=min(previous.mi, current.mi),
        ci=max(previous.ci, current.ci),
        mb=min(previous.mb, current.mb),
        cb=max(previous.cb, current.cb),
    )


def _method_covered(method_element: ET.Element, method_id: str, report_path: str) -> bool:
    counters: dict[str, ET.Element] = {
        counter.get("type", ""): counter for counter in method_element.findall("counter")
    }
    counter: ET.Element | None = counters.get("METHOD")
    if counter is None:
        counter = counters.get("INSTRUCTION")
    if counter is None:
        raise JacocoReportError(
            f"JaCoCo report '{report_path}' has no METHOD or INSTRUCTION counter "
            f"for '{method_id}'."
        )
    raw_covered: str = counter.get("covered", "")
    try:
        return int(raw_covered) > 0
    except ValueError as error:
        raise JacocoReportError(
            f"JaCoCo report '{report_path}' has invalid covered count "
            f"'{raw_covered}' for '{method_id}'."
        ) from error


def _merge_coverage(
        previous: JacocoMethodCoverage,
        current: JacocoMethodCoverage,
) -> JacocoMethodCoverage:
    report_paths: tuple[str, ...] = tuple(
        dict.fromkeys(previous.report_paths + current.report_paths)
    )
    return JacocoMethodCoverage(
        method_ref=previous.method_ref,
        covered=previous.covered or current.covered,
        source_path=previous.source_path or current.source_path,
        source_line=previous.source_line if previous.source_line is not None else current.source_line,
        report_paths=report_paths,
    )


def _load_report(report_path: str) -> JacocoCoverage:
    if not os.path.isfile(report_path):
        raise JacocoReportError(f"JaCoCo report does not exist: '{report_path}'.")
    try:
        root: ET.Element = ET.parse(report_path).getroot()
    except (OSError, ET.ParseError) as error:
        raise JacocoReportError(f"Cannot parse JaCoCo report '{report_path}': {error}") from error

    methods: dict[str, JacocoMethodCoverage] = {}
    for class_element in root.iter("class"):
        owner: str = (class_element.get("name") or "").replace("/", ".")
        if not owner:
            raise JacocoReportError(
                f"JaCoCo report '{report_path}' contains a class without a name."
            )
        source_path: str | None = _source_path(class_element)
        for method_element in class_element.findall("method"):
            name: str = method_element.get("name") or ""
            descriptor: str = method_element.get("desc") or ""
            if not name or not descriptor:
                raise JacocoReportError(
                    f"JaCoCo report '{report_path}' contains a method without a name or "
                    f"descriptor in '{owner}'."
                )
            try:
                params: tuple[str, ...]
                return_type: str
                params, return_type = parse_jvm_descriptor(descriptor)
            except (IndexError, ValueError) as error:
                raise JacocoReportError(
                    f"JaCoCo report '{report_path}' has invalid descriptor "
                    f"'{descriptor}' for '{owner}#{name}'."
                ) from error
            method_ref: MethodRef = MethodRef(
                owner=owner,
                name=name,
                params=params,
                return_type=return_type,
            )
            method_id: str = method_ref.canonical_id
            coverage: JacocoMethodCoverage = JacocoMethodCoverage(
                method_ref=method_ref,
                covered=_method_covered(method_element, method_id, report_path),
                source_path=source_path,
                source_line=_source_line(method_element, method_id, report_path),
                report_paths=(report_path,),
            )
            previous: JacocoMethodCoverage | None = methods.get(method_id)
            methods[method_id] = (
                coverage if previous is None else _merge_coverage(previous, coverage)
            )

    if not methods:
        raise JacocoReportError(f"JaCoCo report '{report_path}' contains no method records.")

    lines: dict[str, dict[int, JacocoLineCoverage]] = {}
    for package_element in root.findall("package"):
        for sourcefile_element in package_element.findall("sourcefile"):
            source_path: str = _sourcefile_path(package_element, sourcefile_element)
            source_lines: dict[int, JacocoLineCoverage] = lines.setdefault(
                source_path, {}
            )
            for line_element in sourcefile_element.findall("line"):
                line, coverage = _line_coverage(
                    line_element, source_path, report_path
                )
                previous_line: JacocoLineCoverage | None = source_lines.get(line)
                source_lines[line] = (
                    coverage
                    if previous_line is None
                    else _merge_line_coverage(previous_line, coverage)
                )
    return JacocoCoverage(methods=methods, lines=lines)


def load_jacoco_coverage(
        xml_paths: list[str],
) -> JacocoCoverage:
    """Load method and line evidence, parsing each XML report once."""
    if not xml_paths:
        raise JacocoReportError("No JaCoCo XML reports were provided.")

    methods: dict[str, JacocoMethodCoverage] = {}
    lines: dict[str, dict[int, JacocoLineCoverage]] = {}
    for report_path in xml_paths:
        report: JacocoCoverage = _load_report(report_path)
        for method_id, coverage in report.methods.items():
            previous: JacocoMethodCoverage | None = methods.get(method_id)
            methods[method_id] = (
                coverage if previous is None else _merge_coverage(previous, coverage)
            )
        for source_path, report_lines in report.lines.items():
            source_lines: dict[int, JacocoLineCoverage] = lines.setdefault(
                source_path, {}
            )
            for line, coverage in report_lines.items():
                previous_line: JacocoLineCoverage | None = source_lines.get(line)
                source_lines[line] = (
                    coverage
                    if previous_line is None
                    else _merge_line_coverage(previous_line, coverage)
                )
    return JacocoCoverage(methods=methods, lines=lines)


def load_jacoco_method_coverage(
        xml_paths: list[str],
) -> dict[str, JacocoMethodCoverage]:
    """Load all exact method records, merging repeat evidence covered-first."""
    return load_jacoco_coverage(xml_paths).methods
