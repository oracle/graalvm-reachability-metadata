# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Exact JaCoCo method coverage for code-coverage workflows.

JaCoCo is the authoritative coverage source in both workflow phases. This
module preserves every reported method's complete JVM identity and source
evidence so overloads cannot inherit one another's status
(§WF-code-coverage-improvement.3.1).
"""

from __future__ import annotations

from dataclasses import dataclass
import os
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


def _load_report(report_path: str) -> dict[str, JacocoMethodCoverage]:
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
    return methods


def load_jacoco_method_coverage(
        xml_paths: list[str],
) -> dict[str, JacocoMethodCoverage]:
    """Load all exact method records, merging repeat evidence covered-first."""
    if not xml_paths:
        raise JacocoReportError("No JaCoCo XML reports were provided.")

    methods: dict[str, JacocoMethodCoverage] = {}
    for report_path in xml_paths:
        for method_id, coverage in _load_report(report_path).items():
            previous: JacocoMethodCoverage | None = methods.get(method_id)
            methods[method_id] = (
                coverage if previous is None else _merge_coverage(previous, coverage)
            )
    return methods
