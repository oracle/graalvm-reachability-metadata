# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Navigation evidence for the deep-method report.

Loads what steers the agent beyond JaCoCo lines — the sampled stacks and
instrumented counters of one `.iprof`, the extractor's control-flow table, and
the library type hierarchy — and states in the report which of it was missing
(§AR-code-coverage-deep-navigation.1).
"""

from __future__ import annotations

import os
from dataclasses import dataclass, field

from utility_scripts.code_coverage_api_rank import ApiRankError, TypeModel, load_type_model
from utility_scripts.code_coverage_profile_counters import (
    InstrumentedCounters,
    counters_from_profile,
    mark_unobserved_dispatch,
)
from utility_scripts.code_coverage_profile_flow import MethodFlow, load_library_flow
from utility_scripts.code_coverage_profile_graph import CallGraph
from utility_scripts.code_coverage_profile_inputs import (
    ProfileFormatError,
    load_json_object,
    profile_tables,
)
from utility_scripts.code_coverage_profile_routes import (
    SampledProfile,
    sampled_profile_from_document,
)


@dataclass(frozen=True)
class NavigationEvidence:
    """Evidence beyond JaCoCo lines; any piece may be absent."""

    line_numbers: dict[str, tuple[tuple[int, int], ...]] = field(default_factory=dict)
    flows: dict[str, MethodFlow] | None = None
    counters: InstrumentedCounters | None = None
    types: TypeModel | None = None


def _library_navigation(
        library_methods_path: str | None,
) -> tuple[dict[str, MethodFlow] | None, TypeModel | None]:
    """The control-flow table and type hierarchy beside the method list."""
    if not library_methods_path:
        return None, None
    graph_dir: str = os.path.dirname(os.path.abspath(library_methods_path))
    flow_path: str = os.path.join(graph_dir, "flow.csv")
    flows: dict[str, MethodFlow] | None = (
        load_library_flow(flow_path) if os.path.isfile(flow_path) else None
    )
    try:
        return flows, load_type_model(graph_dir)
    except ApiRankError as error:
        raise ProfileFormatError(str(error)) from error


def load_navigation(
        profile_path: str,
        graph: CallGraph,
        library_methods_path: str | None,
        line_numbers: dict[str, tuple[tuple[int, int], ...]],
) -> tuple[SampledProfile, NavigationEvidence]:
    """Parse the `.iprof` once for both its samples and its counters, and flag
    the graph's dispatch edges the counters never observed."""
    flows, types = _library_navigation(library_methods_path)
    document: dict = load_json_object(profile_path, "profile")
    try:
        method_refs, type_names = profile_tables(document)
    except (KeyError, TypeError, ValueError) as error:
        raise ProfileFormatError(f"Malformed profile '{profile_path}'.") from error
    profile: SampledProfile = sampled_profile_from_document(
        document, graph, method_refs, profile_path
    )
    counters: InstrumentedCounters | None = counters_from_profile(
        document, method_refs, type_names
    )
    mark_unobserved_dispatch(graph, counters, types)
    return profile, NavigationEvidence(line_numbers, flows, counters, types)


def navigation_summary(evidence: NavigationEvidence, graph: CallGraph) -> dict:
    counters: InstrumentedCounters | None = evidence.counters
    return {
        "instrumentedCounters": counters is not None,
        "profiledBranches": len(counters.branches) if counters is not None else 0,
        "profiledDispatchSites": len(counters.receivers) if counters is not None else 0,
        "unobservedDispatchEdges": sum(
            1 for edges in graph.adjacency.values() for edge in edges if edge.get("unobserved")
        ),
        "controlFlowMethods": len(evidence.flows) if evidence.flows is not None else 0,
    }


def navigation_caveats(evidence: NavigationEvidence) -> list[str]:
    """Say which hint evidence is missing instead of degrading silently
    (§AR-code-coverage-deep-navigation.1.2)."""
    caveats: list[str] = [
        "A zero instrumented count means the suite never went there, not that the "
        "code is unreachable."
        if evidence.counters is not None else
        "The profile carries no instrumented counters, so hints carry no branch or "
        "receiver counts."
    ]
    if evidence.flows is None:
        caveats.append(
            "No control-flow table was supplied, so forks are chosen by line order and "
            "branches are not traced to their targets."
        )
    return caveats


def report_caveats(
        evidence: NavigationEvidence,
        graph: CallGraph,
        library_methods_known: bool,
) -> list[str]:
    """Every caveat the report carries: what guidance can and cannot prove, and
    which input was missing (§AR-code-coverage-deep-navigation.1.2)."""
    return [
        "JaCoCo is the only coverage authority; PGO evidence is guidance only.",
        "Absence of a sample never proves non-execution.",
        "The analysis call graph over-approximates; static paths may be infeasible.",
    ] + navigation_caveats(evidence) + ([] if library_methods_known else [
        "No library method list was supplied, so the deep universe still counts "
        "every JaCoCo-reported method, including any the library's own "
        "test-classifier artifact contributes.",
        "Without that list, virtual call sites declaring a foreign type still "
        "route, so a path may rest on an edge class-hierarchy analysis could "
        "not rule out.",
    ]) + ([
        f"{graph.unjudged_dispatch_sites} virtual call sites carry no declared "
        "target in the call-tree dump; they still route, so a path may rest on "
        "an edge class-hierarchy analysis could not rule out."
    ] if graph.unjudged_dispatch_sites else [])
