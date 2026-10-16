# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""What the run achieved and cost: the target's JaCoCo coverage and tokens.

The pull-request description opens with these numbers
(§FS-real-use-test-generation.2.7). Coverage comes from the project's JaCoCo
report, which covers only the target library's classes
(§AR-real-use-test-harness.2); tokens come from the record Rhei writes after
each agent invocation (§AR-real-use-test-workflow.6).
"""
from __future__ import annotations

import glob
import os
import xml.etree.ElementTree as ET
from typing import Any

from run_state import read_json

JACOCO_REPORT: str = os.path.join("build", "reports", "jacoco", "test", "jacocoTestReport.xml")
ACCOUNTING_INVOCATIONS: str = os.path.join("runtime", "accounting", "invocations")
# JaCoCo counter type -> the `libraryCoverage` key `stats/` uses for it.
COVERAGE_COUNTERS: dict[str, str] = {"LINE": "line", "INSTRUCTION": "instruction", "METHOD": "method"}
# Rhei's record says whether `tokens.input.total` already counts the cached classes.
INPUT_EXCLUDES_CACHE: str = "input-total-excludes-cache"


def library_coverage(report_path: str) -> dict[str, Any]:
    """The report's totals in the `stats/` `libraryCoverage` shape, `N/A` for an absent counter.

    The totals are the report root's own counters: JaCoCo sums every class of
    the target library into them.
    """
    root: ET.Element = ET.parse(report_path).getroot()
    counters: dict[str, ET.Element] = {counter.get("type", ""): counter for counter in root.findall("counter")}
    coverage: dict[str, Any] = {}
    for counter_type, key in COVERAGE_COUNTERS.items():
        counter: ET.Element | None = counters.get(counter_type)
        if counter is None:
            coverage[key] = "N/A"
            continue
        covered: int = int(counter.get("covered", "0"))
        total: int = covered + int(counter.get("missed", "0"))
        coverage[key] = {"covered": covered, "total": total, "ratio": covered / total if total else 1.0}
    return coverage


def _measured(record: dict[str, Any], *keys: str) -> int | None:
    value: Any = record.get("tokens", {})
    for key in keys:
        value = value.get(key, {}) if isinstance(value, dict) else {}
    measured: Any = value.get("value") if isinstance(value, dict) else None
    return measured if type(measured) is int and measured >= 0 else None


def _sum(values: list[int | None]) -> int | None:
    """The sum of what was measured; `None` only when nothing was."""
    measured: list[int] = [value for value in values if value is not None]
    return sum(measured) if measured else None


def agent_tokens(workspace: str) -> dict[str, int | None]:
    """Tokens summed over every agent invocation of the run.

    Input is ordinary input, disjoint from the two cached classes, as the
    other generation workflows report it. A class no invocation measured
    stays `None` rather than a zero.
    """
    records: list[dict[str, Any]] = [
        read_json(path) for path in sorted(glob.glob(os.path.join(workspace, ACCOUNTING_INVOCATIONS, "*.json")))
    ]
    ordinary: list[int | None] = []
    for record in records:
        total: int | None = _measured(record, "input", "total")
        cached: int = (_measured(record, "input", "cached_read") or 0) + (_measured(record, "input", "cache_write") or 0)
        if total is not None and record.get("token_convention") != INPUT_EXCLUDES_CACHE:
            total -= cached
        ordinary.append(total)
    return {
        "input": _sum(ordinary),
        "cached_input": _sum([_measured(record, "input", "cached_read") for record in records]),
        "cache_write_input": _sum([_measured(record, "input", "cache_write") for record in records]),
        "output": _sum([_measured(record, "output", "total") for record in records]),
    }
