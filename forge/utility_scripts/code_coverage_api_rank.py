# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""
Rank JaCoCo-uncovered public API entries by how much still-uncovered internal
code each one unlocks, and render the API-cover prompt
(§WF-code-coverage-improvement.3.1.1, §WF-code-coverage-improvement-architecture.1).

Selection is a budgeted greedy maximum-coverage pass over a static call graph
extracted from the library bytecode by `java/CallGraphExtractor.java`. After each
pick the winner's reachable set is removed from every remaining candidate, so
delegating overloads collapse to zero marginal value without special-casing.

The unlock universe is every library method with a body that is not a public API
inventory entry, taken from the bytecode rather than from JaCoCo: a JaCoCo report
only contains classes some test loaded, so a JaCoCo-derived universe omits
exactly the untouched code this phase exists to open up. JaCoCo stays the sole
coverage authority — it decides which methods are covered, and any universe
method absent from its report counts as uncovered.

Reachability over-approximates virtual dispatch and is navigation only; it never
changes a method's JaCoCo status.

Usage:
  python3 utility_scripts/code_coverage_api_rank.py \
    --graph-dir runtime/code-coverage/graph \
    --api-inventory runtime/code-coverage/api-inventory/api-inventory.json \
    --jacoco-xml path/to/jacocoCodeCoverageReport.xml \
    --iteration 0 \
    --output-dir runtime/code-coverage/validation \
    --prompt-path runtime/code-coverage/prompts/api-cover-prompt.md
"""

from __future__ import annotations

import argparse
import csv
import heapq
import json
import os
import sys

from utility_scripts.code_coverage_jacoco import (
    JacocoMethodCoverage,
    JacocoReportError,
    load_jacoco_method_coverage,
)
from utility_scripts.code_coverage_model import parse_inventory_id

#: Hard cap on prompt targets per pass, matching §WF-code-coverage-improvement.3.1.
MAX_PROMPT_TARGETS = 200


class ApiRankError(RuntimeError):
    """Raised when the ranking inputs are missing or incoherent."""


def load_graph(graph_dir: str) -> tuple[list[str], dict[str, bool], list[list[int]]]:
    """Load `methods.csv`/`edges.csv` into ids, body flags, and adjacency.

    Node numbering is the sorted canonical-id order. Sorting by canonical id
    clusters methods by owner for free, which keeps the reachable-set integers
    narrow, and makes every downstream tie-break deterministic.
    """
    methods_path: str = os.path.join(graph_dir, "methods.csv")
    edges_path: str = os.path.join(graph_dir, "edges.csv")
    for path in (methods_path, edges_path):
        if not os.path.isfile(path):
            raise ApiRankError(f"call graph is missing '{path}'.")

    has_code: dict[str, bool] = {}
    try:
        with open(methods_path, encoding="utf-8", newline="") as methods_file:
            for row in csv.DictReader(methods_file):
                has_code[row["id"]] = row["hasCode"] == "true"
    except (OSError, csv.Error, KeyError) as error:
        raise ApiRankError(f"Cannot read '{methods_path}': {error}") from error

    ids: list[str] = sorted(has_code)
    index: dict[str, int] = {method_id: number for number, method_id in enumerate(ids)}
    adjacency: list[list[int]] = [[] for _ in ids]
    try:
        with open(edges_path, encoding="utf-8", newline="") as edges_file:
            for row in csv.DictReader(edges_file):
                caller: int | None = index.get(row["caller"])
                callee: int | None = index.get(row["callee"])
                if caller is not None and callee is not None:
                    adjacency[caller].append(callee)
    except (OSError, csv.Error, KeyError) as error:
        raise ApiRankError(f"Cannot read '{edges_path}': {error}") from error
    return ids, has_code, adjacency


def strongly_connected_components(adjacency: list[list[int]]) -> list[list[int]]:
    """Iterative Tarjan SCCs, emitted in reverse topological order.

    Reverse topological emission is what makes one propagation pass enough: when
    a component is emitted, every component it can reach is already complete.
    """
    size: int = len(adjacency)
    order: list[int] = [-1] * size
    low: list[int] = [0] * size
    on_stack: list[bool] = [False] * size
    component_stack: list[int] = []
    components: list[list[int]] = []
    counter: int = 0

    for root in range(size):
        if order[root] != -1:
            continue
        # Each frame is (node, index of the next successor to visit).
        work: list[list[int]] = [[root, 0]]
        order[root] = low[root] = counter
        counter += 1
        component_stack.append(root)
        on_stack[root] = True
        while work:
            node, next_successor = work[-1]
            if next_successor < len(adjacency[node]):
                work[-1][1] += 1
                successor: int = adjacency[node][next_successor]
                if order[successor] == -1:
                    order[successor] = low[successor] = counter
                    counter += 1
                    component_stack.append(successor)
                    on_stack[successor] = True
                    work.append([successor, 0])
                elif on_stack[successor]:
                    low[node] = min(low[node], order[successor])
                continue
            work.pop()
            if work:
                low[work[-1][0]] = min(low[work[-1][0]], low[node])
            if low[node] == order[node]:
                component: list[int] = []
                while True:
                    member: int = component_stack.pop()
                    on_stack[member] = False
                    component.append(member)
                    if member == node:
                        break
                components.append(component)
    return components


def reachable_sets(
        adjacency: list[list[int]],
        universe_bit: dict[int, int],
        wanted: set[int],
) -> dict[int, int]:
    """Reachable universe bits for every node in `wanted`, as integer bitsets.

    One pass over the condensation: a component's set is its own universe bits
    plus the union of its successors' sets. Sets for components no longer needed
    are dropped as soon as their last predecessor has consumed them, so peak
    memory tracks the graph's width instead of its node count.
    """
    components: list[list[int]] = strongly_connected_components(adjacency)
    component_of: list[int] = [0] * len(adjacency)
    for number, component in enumerate(components):
        for member in component:
            component_of[member] = number

    # Predecessor count per component, so a set can be freed once consumed.
    consumers: list[int] = [0] * len(components)
    for number, component in enumerate(components):
        successors: set[int] = set()
        for member in component:
            for successor in adjacency[member]:
                target: int = component_of[successor]
                if target != number:
                    successors.add(target)
        for target in successors:
            consumers[target] += 1

    component_sets: dict[int, int] = {}
    result: dict[int, int] = {}
    for number, component in enumerate(components):
        own: int = 0
        successors = set()
        for member in component:
            own |= universe_bit.get(member, 0)
            for successor in adjacency[member]:
                target = component_of[successor]
                if target != number:
                    successors.add(target)
        reachable: int = own
        for target in successors:
            reachable |= component_sets[target]
            consumers[target] -= 1
            if consumers[target] == 0:
                del component_sets[target]
        if consumers[number] > 0:
            component_sets[number] = reachable
        for member in component:
            if member in wanted:
                result[member] = reachable
    return result


def select_targets(
        candidates: list[int],
        reach: dict[int, int],
        limit: int,
        ids: list[str],
) -> list[tuple[int, int]]:
    """Greedy maximum coverage: repeatedly take the largest marginal unlock.

    Scores only shrink as picks accumulate, so a stale score is an upper bound
    and a lazy heap can re-score just the current front-runner. Ties break on
    canonical id, keeping selection deterministic.
    """
    heap: list[tuple[int, str, int]] = [
        (-reach[node].bit_count(), ids[node], node) for node in candidates
    ]
    heapq.heapify(heap)
    unlocked: int = 0
    selected: list[tuple[int, int]] = []
    while heap and len(selected) < limit:
        _, node_id, node = heapq.heappop(heap)
        exact: int = (reach[node] & ~unlocked).bit_count()
        if heap and exact < -heap[0][0]:
            # Another candidate's bound still beats this exact score, so this
            # node is not yet known to be the maximum. Re-queue it tightened.
            heapq.heappush(heap, (-exact, node_id, node))
            continue
        if exact == 0:
            # This node is the maximum and unlocks nothing, so every remaining
            # candidate is bounded above by zero too.
            break
        unlocked |= reach[node]
        selected.append((node, exact))
    return selected


def rank(
        graph_dir: str,
        inventory: dict,
        jacoco_xml_paths: list[str],
        limit: int,
) -> dict:
    """Rank uncovered public entries by marginal unlocked internal code."""
    ids, has_code, adjacency = load_graph(graph_dir)
    index: dict[str, int] = {method_id: number for number, method_id in enumerate(ids)}

    inventory_ids: set[str] = set()
    hints: dict[str, str] = {}
    for target in inventory.get("targets", []):
        target_id: str = target.get("id", "")
        if parse_inventory_id(target_id) is None:
            continue
        inventory_ids.add(target_id)
        hints[target_id] = target.get("behaviorHint", "")
    if not inventory_ids:
        raise ApiRankError("API inventory contains no parseable target ids.")

    coverage: dict[str, JacocoMethodCoverage] = load_jacoco_method_coverage(jacoco_xml_paths)
    covered_ids: set[str] = {
        method_id for method_id, entry in coverage.items() if entry.covered
    }

    # The universe is bytecode-derived; anything JaCoCo does not report counts
    # as uncovered (§WF-code-coverage-improvement.3.1.1).
    universe: list[str] = sorted(
        method_id for method_id, body in has_code.items()
        if body and method_id not in inventory_ids and method_id not in covered_ids
    )
    universe_bit: dict[int, int] = {
        index[method_id]: 1 << bit for bit, method_id in enumerate(universe)
    }

    candidates: list[int] = sorted(
        index[method_id] for method_id in inventory_ids
        if method_id in index and method_id not in covered_ids
    )
    reach: dict[int, int] = reachable_sets(adjacency, universe_bit, set(candidates))
    selected: list[tuple[int, int]] = select_targets(candidates, reach, limit, ids)

    targets: list[dict] = [
        {
            "id": ids[node],
            "rank": position,
            "unlocks": unlocks,
            "reachableUncovered": reach[node].bit_count(),
            "behaviorHint": hints.get(ids[node], ""),
        }
        for position, (node, unlocks) in enumerate(selected, start=1)
    ]
    return {
        "coordinate": inventory.get("coordinate", ""),
        "summary": {
            "graphMethods": len(ids),
            "universeMethods": len(universe),
            "inventoryTargets": len(inventory_ids),
            "uncoveredCandidates": len(candidates),
            "selected": len(targets),
            "totalUnlocked": sum(target["unlocks"] for target in targets),
        },
        "targets": targets,
    }


def render_prompt(report: dict) -> str:
    """Render the API-cover prompt, grouped by owner class.

    Grouping by owner keeps one class's source reading amortised across all of
    its selected entries, and the per-group unlock count tells the agent which
    groups repay extra construction effort.
    """
    groups: dict[str, list[dict]] = {}
    for target in report["targets"]:
        owner: str = target["id"].split("#", 1)[0]
        groups.setdefault(owner, []).append(target)

    summary: dict = report["summary"]
    lines: list[str] = [
        "# Uncovered public API targets",
        "",
        f"{summary['selected']} targets, selected from {summary['uncoveredCandidates']} "
        f"uncovered public entries because together they put "
        f"{summary['totalUnlocked']} currently-uncovered internal methods within reach.",
        "",
        "Write meaningful behavior tests in the dedicated coverage suite for every",
        "target below. Each id is an exact JaCoCo-uncovered public method or",
        "constructor; use realistic public API usage with real assertions, never",
        "superficial coverage-only invocation.",
        "",
        "`unlocks N` is how much internal code that entry newly puts within reach.",
        "It is static navigation guidance, not a coverage measurement.",
        "",
    ]
    for owner in sorted(groups, key=lambda name: (-sum(
            target["unlocks"] for target in groups[name]), name)):
        entries: list[dict] = sorted(groups[owner], key=lambda target: target["rank"])
        unlocked: int = sum(target["unlocks"] for target in entries)
        lines.append(f"## `{owner}` — {len(entries)} targets, unlocks {unlocked}")
        lines.append("")
        for target in entries:
            member: str = target["id"].split("#", 1)[1]
            hint: str = f" - {target['behaviorHint']}" if target["behaviorHint"] else ""
            lines.append(f"- `{member}` (unlocks {target['unlocks']}){hint}")
        lines.append("")
    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Rank uncovered public API entries by unlocked internal code."
    )
    parser.add_argument("--graph-dir", required=True, help="Directory holding methods.csv/edges.csv.")
    parser.add_argument("--api-inventory", required=True, help="api-inventory.json path.")
    parser.add_argument("--jacoco-xml", action="append", required=True,
                        help="JaCoCo XML report path (repeatable).")
    parser.add_argument("--iteration", type=int, required=True, help="Zero-based iteration number.")
    parser.add_argument("--output-dir", required=True, help="Directory for the rank report.")
    parser.add_argument("--prompt-path", required=True, help="Path for the rendered cover prompt.")
    parser.add_argument("--limit", type=int, default=MAX_PROMPT_TARGETS,
                        help=f"Maximum prompt targets (capped at {MAX_PROMPT_TARGETS}).")
    args = parser.parse_args()

    try:
        with open(args.api_inventory, encoding="utf-8") as inventory_file:
            inventory: dict = json.load(inventory_file)
    except (OSError, json.JSONDecodeError) as error:
        print(f"ERROR: cannot read API inventory: {error}", file=sys.stderr)
        raise SystemExit(2) from error

    try:
        report: dict = rank(
            graph_dir=args.graph_dir,
            inventory=inventory,
            jacoco_xml_paths=args.jacoco_xml,
            limit=max(1, min(args.limit, MAX_PROMPT_TARGETS)),
        )
    except (ApiRankError, JacocoReportError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(2) from error

    os.makedirs(args.output_dir, exist_ok=True)
    report_path: str = os.path.join(args.output_dir, f"api-rank-{args.iteration}.json")
    with open(report_path, "w", encoding="utf-8") as report_file:
        json.dump(report, report_file, indent=2)
        report_file.write("\n")

    os.makedirs(os.path.dirname(os.path.abspath(args.prompt_path)), exist_ok=True)
    with open(args.prompt_path, "w", encoding="utf-8") as prompt_file:
        prompt_file.write(render_prompt(report))

    summary: dict = report["summary"]
    print(
        f"API rank: selected {summary['selected']} of {summary['uncoveredCandidates']} "
        f"uncovered entries, unlocking {summary['totalUnlocked']} of "
        f"{summary['universeMethods']} uncovered internal methods."
    )


if __name__ == "__main__":
    main()
