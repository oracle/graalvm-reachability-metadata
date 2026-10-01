# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Instrumented PGO counters for the deep-method report.

Reads the branch and virtual-call counters from the same `.iprof` that carries
the sampled stacks, folds every inlining context back onto the bytecode
position it describes, and resolves observed receiver types onto the
implementations a call site may dispatch to
(§AR-code-coverage-deep-navigation.1.2, §AR-code-coverage-deep-navigation.3.1).
"""

from __future__ import annotations

from dataclasses import dataclass, field

from utility_scripts.code_coverage_api_rank import TypeModel
from utility_scripts.code_coverage_model import MethodRef, normalize_type_name
from utility_scripts.code_coverage_profile_graph import CallGraph
from utility_scripts.code_coverage_profile_inputs import ProfileFormatError

#: `conditionalProfiles` records are (successor bci, successor index, count).
CONDITIONAL_RECORD_WIDTH = 3

#: Sections an instrumented run fills; a sampling-only run writes them empty.
COUNTER_SECTIONS = ("callCountProfiles", "conditionalProfiles", "virtualInvokeProfiles")


@dataclass
class InstrumentedCounters:
    """Counters keyed by the (method id, bci) position they describe."""

    #: Branch position -> successor bci -> count, summed over inlining contexts.
    branches: dict[tuple[str, int], dict[int, int]] = field(default_factory=dict)
    #: Invoke position -> receiver type -> count, summed over inlining contexts.
    receivers: dict[tuple[str, int], dict[str, int]] = field(default_factory=dict)


@dataclass(frozen=True)
class SiteDispatch:
    """What one virtual call site dispatched to in the instrumented run."""

    total: int
    receivers: dict[str, int]
    #: Candidate static id -> dispatch count; `None` when a receiver type does
    #: not resolve, because then no candidate can be called never-dispatched.
    dispatches: dict[int, int] | None


def _leaf(ctx: str) -> tuple[int, int]:
    """The position a context describes: its leaf is written first."""
    method_part, _, bci_part = ctx.split("<", 1)[0].partition(":")
    return int(method_part), int(bci_part)


def counters_from_profile(
        document: dict,
        method_refs: dict[int, MethodRef],
        type_names: dict[int, str],
) -> InstrumentedCounters | None:
    """Fold the counter sections of one `.iprof`; `None` for a sampling-only run.

    A sampling-only build still writes the counter sections, empty, so their
    presence proves nothing; an instrumented run always counts calls.
    """
    if not any(document.get(section) for section in COUNTER_SECTIONS):
        return None
    counters = InstrumentedCounters()
    try:
        for entry in document.get("conditionalProfiles") or []:
            method_id, bci = _leaf(entry["ctx"])
            ref: MethodRef | None = method_refs.get(method_id)
            records: list = entry.get("records") or []
            if len(records) % CONDITIONAL_RECORD_WIDTH:
                raise ProfileFormatError(f"Conditional profile '{entry['ctx']}' is truncated.")
            if ref is None:
                continue
            successors: dict[int, int] = counters.branches.setdefault((ref.canonical_id, bci), {})
            for index in range(0, len(records), CONDITIONAL_RECORD_WIDTH):
                successor: int = int(records[index])
                # A negative successor belongs to a branch the compiler merged
                # into an inlined callee; it names no bytecode position here.
                if successor >= 0:
                    successors[successor] = successors.get(successor, 0) + int(records[index + 2])
        for entry in document.get("virtualInvokeProfiles") or []:
            method_id, bci = _leaf(entry["ctx"])
            ref = method_refs.get(method_id)
            records = entry.get("records") or []
            if len(records) % 2:
                raise ProfileFormatError(f"Virtual invoke profile '{entry['ctx']}' is truncated.")
            if ref is None:
                continue
            histogram: dict[str, int] = counters.receivers.setdefault((ref.canonical_id, bci), {})
            for index in range(0, len(records), 2):
                receiver: str = normalize_type_name(type_names.get(int(records[index]), ""))
                if receiver:
                    histogram[receiver] = histogram.get(receiver, 0) + int(records[index + 1])
    except (KeyError, TypeError, ValueError) as error:
        raise ProfileFormatError("Malformed instrumented counters in profile.") from error
    counters.branches = {key: value for key, value in counters.branches.items() if value}
    counters.receivers = {key: value for key, value in counters.receivers.items() if value}
    return counters


def _dispatch_target(receiver: str, candidates: dict[str, int], types: TypeModel | None) -> int | None:
    """The candidate a receiver dispatches to: its class, else the nearest
    superclass, else a unique interface default."""
    current: str | None = receiver
    walked: set[str] = set()
    while current is not None and current not in walked:
        if current in candidates:
            return candidates[current]
        walked.add(current)
        parents: list[str] = types.supertypes.get(current, []) if types is not None else []
        current = parents[0] if parents else None
    if types is None:
        return None
    defaults: set[int] = {
        candidates[name] for name in types.all_supertypes(receiver) if name in candidates
    }
    return defaults.pop() if len(defaults) == 1 else None


def _site_edges(graph: CallGraph) -> dict[int, list[dict]]:
    sites: dict[int, list[dict]] = {}
    for edges in graph.adjacency.values():
        for edge in edges:
            if edge["kind"] == "call" and edge.get("invoke_id") is not None:
                sites.setdefault(edge["invoke_id"], []).append(edge)
    return sites


def site_dispatch(
        edge: dict,
        graph: CallGraph,
        counters: InstrumentedCounters | None,
        types: TypeModel | None,
) -> SiteDispatch | None:
    """The receiver histogram at one edge's call site, resolved to candidates."""
    if counters is None or edge.get("is_direct") != "false":
        return None
    caller: MethodRef | None = graph.methods.get(edge.get("caller"))
    try:
        bci: int = int(edge.get("bci", ""))
    except ValueError:
        return None
    receivers: dict[str, int] | None = (
        counters.receivers.get((caller.canonical_id, bci)) if caller is not None else None
    )
    if not receivers:
        return None
    candidates: dict[str, int] = {
        graph.methods[callee_id].owner: callee_id
        for callee_id in graph.invoke_fan_out.get(edge.get("invoke_id"), [edge["callee"]])
    }
    dispatches: dict[int, int] | None = {static_id: 0 for static_id in candidates.values()}
    for receiver, count in receivers.items():
        target: int | None = _dispatch_target(receiver, candidates, types)
        if target is None:
            dispatches = None
            break
        dispatches[target] += count
    return SiteDispatch(total=sum(receivers.values()), receivers=receivers, dispatches=dispatches)


def mark_unobserved_dispatch(
        graph: CallGraph,
        counters: InstrumentedCounters | None,
        types: TypeModel | None,
) -> int:
    """Flag call edges a profiled virtual site never dispatched to.

    The static graph keeps them — the agent may still make the site dispatch
    there — but route search prefers observed steps and the prompt names the
    ones a route still needs (§AR-code-coverage-deep-navigation.2.1).
    """
    marked: int = 0
    for edges in _site_edges(graph).values():
        dispatch: SiteDispatch | None = site_dispatch(edges[0], graph, counters, types)
        if dispatch is None or dispatch.dispatches is None:
            continue
        for edge in edges:
            if dispatch.dispatches.get(edge["callee"], 0) == 0:
                edge["unobserved"] = True
                marked += 1
    return marked
