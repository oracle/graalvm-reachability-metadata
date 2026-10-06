# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Fork and catch-boundary evidence for one invoke that never ran.

Walks the caller's control flow backwards from the invoke
(§AR-code-coverage-deep-navigation.3.2) and shapes what it finds into the
report's JSON: the fork line with its branches, or the catch handlers the
dead region is entered through (§AR-code-coverage-deep-navigation.3.3).
"""

from __future__ import annotations

import sys
from collections.abc import Callable
from dataclasses import dataclass

from utility_scripts.code_coverage_jacoco import JacocoLineCoverage
from utility_scripts.code_coverage_model import MethodRef
from utility_scripts.code_coverage_profile_flow import Branch, DeadRegion, MethodFlow
from utility_scripts.code_coverage_profile_graph import CallGraph
from utility_scripts.code_coverage_profile_inputs import line_at
from utility_scripts.code_coverage_profile_navigation import NavigationEvidence

LineRecord = tuple[int, JacocoLineCoverage]


def line_to_json(source_path: str, line: int, coverage: JacocoLineCoverage) -> dict:
    return {
        "sourcePath": source_path,
        "line": line,
        "mi": coverage.mi,
        "ci": coverage.ci,
        "mb": coverage.mb,
        "cb": coverage.cb,
    }


def _branch_to_json(
        branch: Branch,
        flow: MethodFlow,
        dead: frozenset[int],
        lines: tuple[tuple[int, int], ...],
        counts: dict[int, int] | None,
) -> dict:
    """One branch with each successor's landing line, count, and target reach.

    A successor reaches the target when it lands in the never-executed region
    around the invoke (§AR-code-coverage-deep-navigation.3.2).
    """
    return {
        "bci": branch.bci,
        "blockStart": flow.block_of(branch.bci),
        "reach": sum(counts.values()) if counts is not None else None,
        "successors": [
            {
                "bci": successor,
                "line": line_at(lines, successor),
                "count": counts.get(successor) if counts is not None else None,
                "reachesTarget": flow.block_of(successor) in dead,
            }
            for successor in branch.reachable_successors
        ],
    }


@dataclass(frozen=True)
class ForkTrace:
    """What the control-flow walk found above an invoke that never ran."""

    #: The fork line and its branches, or `None` when no candidate line forks.
    fork: LineRecord | None
    branches: list[dict]
    #: The catch boundary when the region is entered only through handlers.
    exception: dict | None


def trace_fork(
        caller: MethodRef,
        edge: dict,
        target_line: int | None,
        candidates: list[LineRecord],
        line_status: dict[int, JacocoLineCoverage],
        graph: CallGraph,
        evidence: NavigationEvidence,
) -> ForkTrace | None:
    """The nearest candidate line holding a branch that forks into the dead
    region around the invoke (§AR-code-coverage-deep-navigation.3.2), or the
    catch boundary that encloses it (§AR-code-coverage-deep-navigation.3.3).

    `None` when the caller has no control-flow or line table, so the fork falls
    back to line order.
    """
    flow: MethodFlow | None = (
        evidence.flows.get(caller.canonical_id) if evidence.flows is not None else None
    )
    lines: tuple[tuple[int, int], ...] = evidence.line_numbers.get(caller.canonical_id, ())
    try:
        invoke_bci: int = int(edge.get("bci", ""))
    except ValueError:
        return None
    if flow is None or not lines:
        return None

    def branch_counts(branch: Branch) -> dict[int, int] | None:
        if evidence.counters is None:
            return None
        return evidence.counters.branches.get((caller.canonical_id, branch.bci))

    def line_ran(bci: int) -> bool | None:
        line: int | None = line_at(lines, bci)
        return line_status[line].covered if line in line_status else None

    region: DeadRegion | None = flow.dead_region(invoke_bci, branch_counts, line_ran)
    if region is None:
        return None
    fork_bcis: set[int] = {branch.bci for branch, _ in region.forks}
    for record in candidates:
        line_branches: list[Branch] = [
            branch for branch in flow.branches
            if not branch.plumbing and line_at(lines, branch.bci) == record[0]
            # On the invoking line only a branch before the call can decide it.
            and (record[0] != target_line or branch.bci < invoke_bci)
        ]
        if any(branch.bci in fork_bcis for branch in line_branches):
            return ForkTrace(record, [
                _branch_to_json(branch, flow, region.blocks, lines, branch_counts(branch))
                for branch in line_branches
            ], None)
    if not region.catches:
        return ForkTrace(None, [], None)
    return ForkTrace(None, [], _exception_to_json(caller, flow, region, lines, branch_counts, graph))


def _exception_to_json(
        caller: MethodRef,
        flow: MethodFlow,
        region: DeadRegion,
        lines: tuple[tuple[int, int], ...],
        branch_counts: Callable[[Branch], dict[int, int] | None],
        graph: CallGraph,
) -> dict:
    """The catch boundary: each handler with the `try` lines that ran and can
    raise into it, each with its own count (§AR-code-coverage-deep-navigation.3.3)."""
    counts: dict[int, int | None] = flow.block_counts(branch_counts)
    caller_id: int | None = graph.key_to_id.get(caller.canonical_id)
    calls_by_bci: dict[int, str] = {}
    for call in graph.adjacency.get(caller_id, []) if caller_id is not None else []:
        callee: MethodRef | None = graph.methods.get(call.get("callee"))
        try:
            bci: int = int(call.get("bci", ""))
        except ValueError:
            continue
        if call.get("kind") == "call" and callee is not None:
            calls_by_bci[bci] = f"{callee.owner.rsplit('.', 1)[-1]}.{callee.name}"
    handlers: list[dict] = []
    for handler in sorted({handler for handler, _ in region.catches}):
        sources: list[int] = [source for h, source in region.catches if h == handler]
        raising: list[int] = [source for source in sources if source in flow.throwing_blocks]
        by_line: dict[int | None, dict] = {}
        for block in raising or sources:
            end: int = next((start for start in flow.block_starts if start > block), sys.maxsize)
            line: int | None = line_at(lines, block)
            entry: dict = by_line.setdefault(line, {"line": line, "count": None, "calls": []})
            count: int | None = counts.get(block)
            if count is not None and (entry["count"] is None or count > entry["count"]):
                entry["count"] = count
            entry["calls"] += [name for bci, name in sorted(calls_by_bci.items()) if block <= bci < end]
        handlers.append({
            "bci": handler,
            "line": line_at(lines, handler),
            "type": flow.handler_types.get(handler, "any"),
            "sources": [by_line[line] for line in sorted(by_line, key=lambda value: value or 0)],
        })
    return {"handlers": handlers}


def fork_to_json(
        source_path: str,
        fork: LineRecord,
        branches: list[dict] | None,
) -> dict:
    reaches: list[int] = [
        branch["reach"] for branch in branches or [] if branch["reach"] is not None
    ]
    return {
        **line_to_json(source_path, *fork),
        "evidence": "control-flow" if branches is not None else "line-order",
        "reach": max(reaches) if reaches else None,
        "branches": branches,
    }
