# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Token usage of the run's agent invocations, read from Rhei's accounting records.

The run metrics and the pull request report what the run cost per agent state
(§FS-forge-run-metrics).
"""
from __future__ import annotations

import glob
import os
from dataclasses import dataclass
from typing import Any

from run_state import read_json

ACCOUNTING_INVOCATIONS: str = os.path.join("runtime", "accounting", "invocations")
# Rhei's record says whether `tokens.input.total` already counts the cached classes.
INPUT_EXCLUDES_CACHE: str = "input-total-excludes-cache"


@dataclass
class Usage:
    """Ordinary input, cache reads, and output; ordinary input excludes both cached classes."""

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
    cache_write: int = _measured(record, "input", "cache_write")
    total: int = _measured(record, "input", "total")
    if record.get("token_convention") != INPUT_EXCLUDES_CACHE:
        total = max(total - cached - cache_write, 0)
    return Usage(input=total, cached=cached, output=_measured(record, "output", "total"))


def usage_by_state(workspace: str) -> dict[str, Usage]:
    """Usage summed per agent state, in first-invocation order."""
    by_state: dict[str, Usage] = {}
    for path in sorted(glob.glob(os.path.join(workspace, ACCOUNTING_INVOCATIONS, "*.json"))):
        record: dict[str, Any] = read_json(path)
        by_state.setdefault(str(record.get("state", "agent")), Usage()).add(usage_of(record))
    return by_state


def total_usage(workspace: str) -> Usage:
    total: Usage = Usage()
    for usage in usage_by_state(workspace).values():
        total.add(usage)
    return total
