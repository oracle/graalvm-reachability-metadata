# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Uncovered-target records for the deep-method report.

Builds one record per exact JaCoCo-uncovered deep target — its best static
route, ranking keys, and the JSON forms the report serializes
(§AR-code-coverage-deep-navigation.2). Miss classification lives in
`code_coverage_profile_miss`.
"""

from __future__ import annotations

import sys
from dataclasses import dataclass

from utility_scripts.code_coverage_jacoco import JacocoMethodCoverage
from utility_scripts.code_coverage_model import MethodRef
from utility_scripts.code_coverage_profile_graph import (
    CallGraph,
    format_static_id,
    is_synthetic_method,
    translated_ref,
)
from utility_scripts.code_coverage_profile_inputs import TargetState
from utility_scripts.code_coverage_profile_routes import RouteMap, Sample, route_to

MAX_LISTED_METHODS = 200

#: Methods that hand a closure to another thread, so its body runs later, on a
#: thread the test does not control (§AR-code-coverage-improvement.4.2.1).
HAND_OFF_METHOD_NAMES: frozenset[str] = frozenset({
    "execute",
    "invokeAll",
    "invokeAny",
    "runAsync",
    "schedule",
    "scheduleAtFixedRate",
    "scheduleWithFixedDelay",
    "start",
    "submit",
    "supplyAsync",
})


@dataclass
class NearCallRecord:
    """One exact JaCoCo-uncovered deep method and its best static route."""

    coverage: JacocoMethodCoverage
    target_id: int | None
    target_state: TargetState
    join_kind: str
    static_path: list[int]
    static_path_edges: list[dict]
    sample: Sample | None
    sampled_join_path_index: int | None
    #: Whether the target is a public method the API phase left uncovered
    #: (§AR-code-coverage-improvement.4.2).
    public_api: bool = False
    semantic_distance: int | None = None
    #: Reach count of the target's miss diagnosis: how often its fork or
    #: dispatch site ran (§AR-code-coverage-deep-navigation.2.2).
    reach: int | None = None

    @property
    def target_ref(self) -> MethodRef:
        return self.coverage.method_ref

    @property
    def attempt_count(self) -> int:
        return self.target_state.attempt_count

    @property
    def distance(self) -> int | None:
        if self.join_kind == "none":
            return None
        if self.semantic_distance is not None:
            return self.semantic_distance
        return max(0, len(self.static_path) - 1)

    @property
    def sample_count(self) -> int:
        return self.sample.count if self.sample is not None else 0

    @property
    def unobserved_steps(self) -> list[dict]:
        """Route edges a profiled call site never dispatched to."""
        return [edge for edge in self.static_path_edges if edge.get("unobserved")]


def build_record(
        coverage: JacocoMethodCoverage,
        graph: CallGraph,
        sampled_routes: RouteMap,
        entry_routes: RouteMap,
        target_state: TargetState,
        public_api: bool,
) -> NearCallRecord:
    target_id: int | None = graph.key_to_id.get(coverage.method_ref.canonical_id)
    if target_id is None:
        return NearCallRecord(
            coverage=coverage,
            target_id=None,
            target_state=target_state,
            join_kind="none",
            static_path=[],
            static_path_edges=[],
            sample=None,
            sampled_join_path_index=None,
            public_api=public_api,
        )
    if target_id in sampled_routes.distance:
        path, edges = route_to(target_id, sampled_routes)
        sample, path_index = sampled_routes.payload[target_id]
        return NearCallRecord(
            coverage=coverage,
            target_id=target_id,
            target_state=target_state,
            join_kind="sampled",
            static_path=path,
            static_path_edges=edges,
            sample=sample,
            sampled_join_path_index=path_index,
            public_api=public_api,
            semantic_distance=_path_distance(path, graph),
        )
    if target_id in entry_routes.distance:
        path, edges = route_to(target_id, entry_routes)
        return NearCallRecord(
            coverage=coverage,
            target_id=target_id,
            target_state=target_state,
            join_kind="public-entry",
            static_path=path,
            static_path_edges=edges,
            sample=None,
            sampled_join_path_index=None,
            public_api=public_api,
            semantic_distance=_path_distance(path, graph),
        )
    return NearCallRecord(
        coverage=coverage,
        target_id=target_id,
        target_state=target_state,
        join_kind="none",
        static_path=[target_id],
        static_path_edges=[],
        sample=None,
        sampled_join_path_index=None,
        public_api=public_api,
    )


def record_rank_key(record: NearCallRecord) -> tuple:
    """Distance first; everything after it only breaks equal-distance ties
    (§AR-code-coverage-deep-navigation.2.2)."""
    join_order: dict[str, int] = {"sampled": 0, "public-entry": 1, "none": 2}
    distance: int = record.distance if record.distance is not None else sys.maxsize
    return (
        distance,
        len(record.unobserved_steps),
        join_order[record.join_kind],
        -(record.reach or 0),
        -record.sample_count,
        record.target_ref.canonical_id,
    )


def prompt_selection_key(record: NearCallRecord) -> tuple:
    return (record.attempt_count, *record_rank_key(record))


def method_evidence(coverage: JacocoMethodCoverage, graph_status: str = "present") -> dict:
    return {
        "id": coverage.method_ref.canonical_id,
        "status": coverage.status,
        "synthetic": is_synthetic_method(coverage.method_ref),
        "graphStatus": graph_status,
        "sourcePath": coverage.source_path,
        "sourceLine": coverage.source_line,
        "jacocoReportPaths": list(coverage.report_paths),
    }


def _edge_to_json(edge: dict, graph: CallGraph) -> dict:
    return {
        "caller": format_static_id(edge["caller"], graph),
        "callee": format_static_id(edge["callee"], graph),
        "bci": edge["bci"],
        "invokeId": edge.get("invoke_id"),
        "isDirect": edge["is_direct"],
        "kind": edge["kind"],
        "unobserved": bool(edge.get("unobserved")),
    }


def _closure_stats(
        record: NearCallRecord,
        graph: CallGraph,
        jacoco_methods: dict[str, JacocoMethodCoverage],
) -> dict | None:
    """How many closures this method owns, and how many never execute.

    This is what the excluded synthetic rows carried, moved onto the one name an
    agent can act on (§AR-code-coverage-improvement.4.2.1). A body JaCoCo never
    reported counts as not executed, exactly as an unreported method counts as
    uncovered everywhere else in this phase.
    """
    if record.target_id is None:
        return None
    body_ids: list[int] = graph.closures_of.get(record.target_id, [])
    if not body_ids:
        return None
    executed: int = sum(
        1
        for body_id in body_ids
        if (coverage := jacoco_methods.get(graph.methods[body_id].canonical_id)) is not None
        and coverage.covered
    )
    return {"total": len(body_ids), "unexecuted": len(body_ids) - executed}


def _hand_off_note(record: NearCallRecord, graph: CallGraph) -> str | None:
    """Name the scheduler or executor a route's closure is handed to, if any."""
    for edge in record.static_path_edges:
        if edge["kind"] != "creation":
            continue
        for outgoing in graph.adjacency.get(edge["caller"], []):
            callee: MethodRef = graph.methods[outgoing["callee"]]
            if callee.name in HAND_OFF_METHOD_NAMES:
                return f"{simple_owner(callee.owner)}.{callee.name}"
    return None


def translated_path(static_path: list[int], graph: CallGraph) -> list[MethodRef]:
    """Replace synthetic nodes by source-level methods, collapsing repeats."""
    translated: list[MethodRef] = []
    for static_id in static_path:
        ref: MethodRef = translated_ref(static_id, graph)
        if not translated or translated[-1].canonical_id != ref.canonical_id:
            translated.append(ref)
    return translated


def _path_distance(static_path: list[int], graph: CallGraph) -> int:
    """Count edges in the source-level path used for prompt ranking."""
    return max(0, len(translated_path(static_path, graph)) - 1)


def record_to_json(
        record: NearCallRecord,
        graph: CallGraph,
        rank: int,
        jacoco_methods: dict[str, JacocoMethodCoverage],
        miss_classification: dict,
) -> dict:
    graph_present: bool = record.target_id is not None
    return {
        "id": record.target_ref.canonical_id,
        "owner": record.target_ref.owner,
        "ownerClass": record.target_ref.owner_class_simple,
        "sourcePath": record.coverage.source_path,
        "sourceLine": record.coverage.source_line,
        "jacocoReportPaths": list(record.coverage.report_paths),
        "jacocoStatus": "uncovered",
        "publicApi": record.public_api,
        "rank": rank,
        "attemptCount": record.attempt_count,
        "targetStatus": record.target_state.status,
        "terminal": record.target_state.terminal,
        "stateReason": record.target_state.reason,
        "lastAttemptedIteration": record.target_state.last_attempted_iteration,
        "graphStatus": "present" if graph_present else "not-present",
        "joinKind": record.join_kind,
        "stepsRemaining": record.distance,
        "reach": record.reach,
        "unobservedSteps": [
            {
                "caller": format_static_id(edge["caller"], graph),
                "callee": format_static_id(edge["callee"], graph),
            }
            for edge in record.unobserved_steps
        ],
        "sampleCount": record.sample_count,
        "sampleContextId": record.sample.context_id if record.sample is not None else None,
        "synthetic": is_synthetic_method(record.target_ref),
        "closures": _closure_stats(record, graph, jacoco_methods),
        "handOff": _hand_off_note(record, graph),
        "missClassification": miss_classification,
        "reachingPath": (
            [
                ref.canonical_id
                for ref in translated_path(record.static_path, graph)
            ]
            if graph_present else None
        ),
        "reachingPathRaw": (
            [format_static_id(static_id, graph) for static_id in record.static_path]
            if graph_present else None
        ),
        "edges": (
            [_edge_to_json(edge, graph) for edge in record.static_path_edges]
            if graph_present else None
        ),
    }


def simple_owner(owner: str) -> str:
    return owner.rsplit(".", 1)[-1].replace("$", ".")
