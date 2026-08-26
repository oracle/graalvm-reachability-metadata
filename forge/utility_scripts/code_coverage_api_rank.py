# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""
Rank JaCoCo-uncovered public API entries by how much still-uncovered internal
code each one unlocks, and render the API-cover prompt
(§CC-code-coverage-improvement.3.1.1, §CC-code-coverage-improvement-architecture.1).

The unlock universe, the greedy overlap-subtracting pass, and the
over-approximating call graph are specified in §CC-code-coverage-improvement.3.1.1;
candidacy is filtered by receiver obtainability
(§CC-code-coverage-improvement.3.1.2).

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
from dataclasses import dataclass, field

from utility_scripts.code_coverage_jacoco import (
    JacocoMethodCoverage,
    JacocoReportError,
    load_jacoco_method_coverage,
)
from utility_scripts.code_coverage_model import parse_inventory_id

#: Hard cap on prompt targets per pass, matching §CC-code-coverage-improvement.3.1.
MAX_PROMPT_TARGETS = 400


class ApiRankError(RuntimeError):
    """Raised when the ranking inputs are missing or incoherent."""


@dataclass(frozen=True)
class PublicMethod:
    """One public method of a public class, split for eligibility analysis."""

    id: str
    name: str
    #: `name(params)`, without the return type, so a covariant override matches.
    signature: str
    return_type: str
    is_static: bool

    @property
    def is_constructor(self) -> bool:
        return self.name == "<init>"


@dataclass
class TypeModel:
    """Declared types and their public methods, keyed by owner.

    Owner is the join key in both directions: everything before `#` in a
    canonical method id is a `types.csv` name
    (§CC-code-coverage-improvement.3.1.2).
    """

    #: Direct supertypes (superclass plus interfaces) of every declared type.
    supertypes: dict[str, list[str]] = field(default_factory=dict)
    #: Public methods of public classes, grouped by declaring type.
    public_methods: dict[str, list[PublicMethod]] = field(default_factory=dict)
    #: Every type the extractor declared, so an unknown owner can fail open.
    declared_types: set[str] = field(default_factory=set)

    def all_supertypes(self, type_name: str) -> set[str]:
        """Transitive supertypes of `type_name`, excluding itself."""
        found: set[str] = set()
        pending: list[str] = list(self.supertypes.get(type_name, ()))
        while pending:
            current: str = pending.pop()
            if current not in found:
                found.add(current)
                pending.extend(self.supertypes.get(current, ()))
        return found


def split_method_id(method_id: str) -> tuple[str, str, str, str]:
    """`owner#name(params):ret` -> owner, name, `name(params)`, return type."""
    owner, member = method_id.split("#", 1)
    head, _, return_type = member.rpartition("):")
    return owner, member.split("(", 1)[0], f"{head})", return_type


def element_type(type_name: str) -> str:
    """`X[][]` -> `X`: returning an array hands the test an element."""
    while type_name.endswith("[]"):
        type_name = type_name[:-2]
    return type_name


def load_type_model(graph_dir: str) -> TypeModel | None:
    """Load `types.csv` plus the public methods of `methods.csv`.

    Returns `None` when the extractor predates `types.csv`, which switches
    eligibility filtering off rather than blocking every entry: a workspace
    whose graph was built by an older extractor must still rank.
    """
    types_path: str = os.path.join(graph_dir, "types.csv")
    methods_path: str = os.path.join(graph_dir, "methods.csv")
    if not os.path.isfile(types_path):
        return None

    model = TypeModel()
    try:
        with open(types_path, encoding="utf-8", newline="") as types_file:
            for row in csv.DictReader(types_file):
                parents: list[str] = [row["super"]] if row["super"] else []
                parents.extend(name for name in row["interfaces"].split(";") if name)
                model.supertypes[row["name"]] = parents
                model.declared_types.add(row["name"])
    except (OSError, csv.Error, KeyError) as error:
        raise ApiRankError(f"Cannot read '{types_path}': {error}") from error

    try:
        with open(methods_path, encoding="utf-8", newline="") as methods_file:
            reader = csv.DictReader(methods_file)
            if "isStatic" not in (reader.fieldnames or []):
                return None
            for row in reader:
                if row["isPublicApi"] != "true":
                    continue
                owner, name, signature, return_type = split_method_id(row["id"])
                model.public_methods.setdefault(owner, []).append(PublicMethod(
                    id=row["id"],
                    name=name,
                    signature=signature,
                    return_type=return_type,
                    is_static=row["isStatic"] == "true",
                ))
    except (OSError, csv.Error, KeyError) as error:
        raise ApiRankError(f"Cannot read '{methods_path}': {error}") from error
    return model


def obtainable_types(model: TypeModel) -> set[str]:
    """Least fixed point of the three obtainability rules (§3.1.2).

    Seeds are what a test can write from nothing: a public constructor, and the
    return type of any public static method, which needs no receiver. From
    there, holding a type yields the return types of its public methods. Every
    admission also admits the type's supertypes, because holding an instance
    permits every supertype method call on it.

    The frontier is a worklist rather than a repeated sweep: a type's public
    methods only ever need to be walked once, when it first becomes obtainable.
    """
    obtainable: set[str] = set()
    frontier: list[str] = []

    def admit(type_name: str) -> None:
        candidates: set[str] = {type_name} | model.all_supertypes(type_name)
        for candidate in candidates:
            if candidate in model.declared_types and candidate not in obtainable:
                obtainable.add(candidate)
                frontier.append(candidate)

    for owner, methods in model.public_methods.items():
        for method in methods:
            if method.is_constructor:
                admit(owner)
            elif method.is_static:
                admit(element_type(method.return_type))

    while frontier:
        for method in model.public_methods.get(frontier.pop(), ()):
            if not method.is_constructor:
                admit(element_type(method.return_type))
    return obtainable


def eligible_targets(model: TypeModel) -> dict[str, str]:
    """Map each eligible public method id to how a test reaches it (§3.1.2).

    Ineligible ids are simply absent. The override case is checked last and
    against the *public* signatures of obtainable supertypes only: a protected
    or package-private declaration upstream is not something a test can call.
    """
    obtainable: set[str] = obtainable_types(model)
    public_signatures: dict[str, set[str]] = {
        owner: {method.signature for method in methods}
        for owner, methods in model.public_methods.items()
    }

    eligible: dict[str, str] = {}
    for owner, methods in model.public_methods.items():
        supertypes: set[str] | None = None
        for method in methods:
            if method.is_constructor:
                eligible[method.id] = "constructor"
            elif method.is_static:
                eligible[method.id] = "static"
            elif owner in obtainable:
                eligible[method.id] = "receiver"
            else:
                if supertypes is None:
                    supertypes = {
                        name for name in model.all_supertypes(owner) if name in obtainable
                    }
                if any(method.signature in public_signatures.get(name, ())
                       for name in supertypes):
                    eligible[method.id] = "override"
    return eligible


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
        universe_bit: dict[int, int],
) -> list[tuple[int, int]]:
    """Greedy maximum coverage: repeatedly take the largest marginal unlock.

    A candidate's own bit is never subtracted, so no other pick can eliminate it
    (§CC-code-coverage-improvement.3.1.1). Reaching an entry statically is an
    over-approximation over class-hierarchy-resolved dispatch, while the phase is
    scored on exact execution: dropping an entry because a selected one calls it
    would silently remove an uncovered public method from every future prompt.
    A delegating overload therefore sinks to its own single bit and ranks last,
    rather than disappearing.

    Scores only shrink as picks accumulate — the own bit is a constant added to a
    shrinking set — so a stale score is an upper bound and a lazy heap can
    re-score just the current front-runner. Ties break on canonical id, keeping
    selection deterministic.
    """
    own: dict[int, int] = {node: universe_bit.get(node, 0) for node in candidates}

    def marginal(node: int, unlocked: int) -> int:
        return ((reach[node] & ~unlocked) | own[node]).bit_count()

    heap: list[tuple[int, str, int]] = [
        (-marginal(node, 0), ids[node], node) for node in candidates
    ]
    heapq.heapify(heap)
    unlocked: int = 0
    selected: list[tuple[int, int]] = []
    while heap and len(selected) < limit:
        _, node_id, node = heapq.heappop(heap)
        exact: int = marginal(node, unlocked)
        if heap and exact < -heap[0][0]:
            # Another candidate's bound still beats this exact score, so this
            # node is not yet known to be the maximum. Re-queue it tightened.
            heapq.heappush(heap, (-exact, node_id, node))
            continue
        if exact == 0:
            # The maximum holds no bit of its own and unlocks nothing, so every
            # remaining candidate is bounded above by zero too. Only bodiless
            # entries reach this: JaCoCo never reports them, so they cannot be
            # targets (§CC-code-coverage-improvement.3.1.1).
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
    # as uncovered (§CC-code-coverage-improvement.3.1.1). Public API entries are
    # members too, so every candidate holds its own bit and is worth at least
    # one: this phase is scored on public methods covered, so covering the entry
    # itself is a real gain, not merely a means of reaching internal code.
    universe: list[str] = sorted(
        method_id for method_id, body in has_code.items()
        if body and method_id not in covered_ids
    )
    universe_bit: dict[int, int] = {
        index[method_id]: 1 << bit for bit, method_id in enumerate(universe)
    }

    # Eligibility is a membership filter applied before ranking, not a reorder
    # (§CC-code-coverage-improvement.3.1.2). An owner the extractor never
    # declared fails open: it came from a jar outside the graph, and excluding
    # it would be a guess rather than a finding.
    model: TypeModel | None = load_type_model(graph_dir)
    eligible: dict[str, str] = eligible_targets(model) if model is not None else {}

    def target_via(method_id: str) -> str | None:
        if model is None:
            return "unfiltered"
        owner: str = method_id.split("#", 1)[0]
        if owner not in model.declared_types:
            return "unknown-owner"
        return eligible.get(method_id)

    uncovered_ids: list[str] = sorted(
        method_id for method_id in inventory_ids
        if method_id in index and method_id not in covered_ids
    )
    via: dict[str, str] = {}
    for method_id in uncovered_ids:
        reached: str | None = target_via(method_id)
        if reached is not None:
            via[method_id] = reached

    candidates: list[int] = sorted(index[method_id] for method_id in via)
    reach: dict[int, int] = reachable_sets(adjacency, universe_bit, set(candidates))
    selected: list[tuple[int, int]] = select_targets(
        candidates, reach, limit, ids, universe_bit)

    def closures(node: int) -> list[str]:
        """The lambda bodies the compiler extracted out of this entry.

        The extractor resolves each `invokedynamic` to its bootstrap target, so
        the enclosing method already points at its own bodies — no name parsing,
        and therefore no overload ambiguity
        (§CC-code-coverage-improvement.3.2.1).
        """
        owner: str = ids[node].split("#", 1)[0]
        return sorted(
            ids[callee] for callee in adjacency[node]
            if ids[callee].startswith(f"{owner}#lambda$")
        )

    targets: list[dict] = []
    for position, (node, unlocks) in enumerate(selected, start=1):
        bodies: list[str] = closures(node)
        targets.append({
            "id": ids[node],
            "rank": position,
            "unlocks": unlocks,
            "reachableUncovered": reach[node].bit_count(),
            "targetVia": via[ids[node]],
            "behaviorHint": hints.get(ids[node], ""),
            "closures": len(bodies),
            "closuresUnexecuted": sum(1 for body in bodies if body not in covered_ids),
        })
    return {
        "coordinate": inventory.get("coordinate", ""),
        "summary": {
            "graphMethods": len(ids),
            "universeMethods": len(universe),
            "inventoryTargets": len(inventory_ids),
            "uncoveredEntries": len(uncovered_ids),
            "uncoveredCandidates": len(candidates),
            "ineligibleEntries": len(uncovered_ids) - len(candidates),
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
        f"uncovered public entries a test can reach because together they put "
        f"{summary['totalUnlocked']} currently-uncovered methods within reach.",
        "",
        "Write meaningful behavior tests for every target below. Each id is an",
        "exact JaCoCo-uncovered public method or constructor; use realistic public",
        "API usage with real assertions, never superficial coverage-only",
        "invocation. Cover every target, including the ones listed last: an entry",
        "worth 1 is still an uncovered public method this phase is measured on.",
        "",
        "## Where the tests go",
        "",
        "All of them belong in the dedicated coverage suite, and nowhere else: the",
        "library's regular test sources are metadata-generation tests and stay",
        "untouched.",
        "",
        "Inside that suite, write one test class per subsystem you drive, not one",
        "class per run. A subsystem is the set of owner classes a single realistic",
        "scenario exercises together — a store family and its iterators, a builder",
        "and the topology it produces, a serde pair. Name the class after that",
        "subsystem, and give it the setup that subsystem needs.",
        "",
        "Before extending an existing test class, check whether the group you are",
        "about to cover already has one. If it does not, add a class rather than",
        "growing an unrelated one: a scenario built for its own subsystem covers",
        "far more of it than one appended to a class built for something else.",
        "",
        "## Reading the target list",
        "",
        "Each `##` heading below is one owner class. Headings that share a package",
        "and a collaborator are usually the same subsystem and belong in one test",
        "class together.",
        "",
        "`unlocks N` is how many currently-uncovered methods that entry newly puts",
        "within reach, counting the entry itself. It is static navigation guidance,",
        "not a coverage measurement.",
        "",
        "`N closures of which M never run` means the method builds lambdas whose",
        "bodies no test has executed. Calling the method is not enough: drive the",
        "behavior that invokes those closures.",
        "",
        "An entry marked `via supertype` is declared on an implementation class you",
        "cannot construct. Reach it the way a user would — hold the public",
        "supertype the API hands you and call the method on that; dynamic dispatch",
        "lands in this implementation. Never cast to the implementation class.",
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
            route: str = ", via supertype" if target.get("targetVia") == "override" else ""
            unexecuted: int = target.get("closuresUnexecuted", 0)
            closures: str = (
                f", {target['closures']} closures of which {unexecuted} never run"
                if unexecuted else ""
            )
            lines.append(f"- `{member}` (unlocks {target['unlocks']}{route}{closures}){hint}")
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
        f"{summary['universeMethods']} uncovered methods."
    )


if __name__ == "__main__":
    main()
