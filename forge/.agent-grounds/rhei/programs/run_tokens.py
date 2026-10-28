# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Token usage of the run's agent invocations, read from Rhei's accounting records.

The run metrics and the pull request report what the run cost per agent state
(§FS-forge-run-metrics). A cache write is input, and each invocation is priced at
the rates of the model it ran (§AR-forge-workflow-pipeline.4).
"""
from __future__ import annotations

import glob
import os
from dataclasses import dataclass
from typing import Any

from utility_scripts.token_costs import calc_model_session_cost

from run_state import read_json

ACCOUNTING_INVOCATIONS: str = os.path.join("runtime", "accounting", "invocations")
# Rhei's record says whether `tokens.input.total` already counts the cached classes.
INPUT_EXCLUDES_CACHE: str = "input-total-excludes-cache"


@dataclass
class Usage:
    """Input, cache reads, and output; input counts cache writes and excludes cache reads."""

    input: int = 0
    cached: int = 0
    output: int = 0

    def add(self, other: "Usage") -> None:
        self.input += other.input
        self.cached += other.cached
        self.output += other.output


def _measured(record: dict[str, Any], *keys: str) -> int:
    value: Any = record.get("tokens", {})
    for key in keys:
        value = value.get(key, {}) if isinstance(value, dict) else {}
    measured: Any = value.get("value") if isinstance(value, dict) else None
    return measured if type(measured) is int and measured >= 0 else 0


def usage_of(record: dict[str, Any]) -> Usage:
    cached: int = _measured(record, "input", "cached_read")
    total: int = _measured(record, "input", "total")
    if record.get("token_convention") == INPUT_EXCLUDES_CACHE:
        total += _measured(record, "input", "cache_write")
    else:
        total = max(total - cached, 0)
    return Usage(input=total, cached=cached, output=_measured(record, "output", "total"))


def _records(workspace: str) -> list[dict[str, Any]]:
    """The run's invocation records in start order; their file names are content hashes."""
    records: list[dict[str, Any]] = [
        read_json(path) for path in sorted(glob.glob(os.path.join(workspace, ACCOUNTING_INVOCATIONS, "*.json")))]
    return sorted(records, key=lambda record: str(record.get("started_at", "")))


def usage_by_state(workspace: str) -> dict[str, Usage]:
    """Usage summed per agent state, in first-invocation order."""
    by_state: dict[str, Usage] = {}
    for record in _records(workspace):
        by_state.setdefault(str(record.get("state", "agent")), Usage()).add(usage_of(record))
    return by_state


def total_usage(workspace: str) -> Usage:
    total: Usage = Usage()
    for usage in usage_by_state(workspace).values():
        total.add(usage)
    return total


def cost_usd(workspace: str) -> float:
    """The run's cost: each invocation priced at the rates of the model it ran, summed."""
    cost: float = 0.0
    for record in _records(workspace):
        usage: Usage = usage_of(record)
        cost += calc_model_session_cost(str(record.get("model") or ""), usage.input, usage.cached, usage.output)
    return round(cost, 4)
