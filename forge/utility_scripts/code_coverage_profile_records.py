# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Uncovered-target records for the deep-method report.

Builds one record per exact JaCoCo-uncovered deep method — its best static
route, ranking keys, miss classification from the caller's line region, and
the JSON forms the report serializes (§AR-code-coverage-improvement.4.2).
"""

from __future__ import annotations

import sys
from dataclasses import dataclass

from utility_scripts.code_coverage_jacoco import JacocoLineCoverage, JacocoMethodCoverage
from utility_scripts.code_coverage_model import MethodRef
from utility_scripts.code_coverage_profile_graph import (
    CallGraph,
    _format_static_id,
    _is_synthetic_method,
    _translated_ref,
)
from utility_scripts.code_coverage_profile_inputs import TargetState
from utility_scripts.code_coverage_profile_routes import RouteMap, Sample, _route_to

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
    semantic_distance: int | None = None

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


def _build_record(
        coverage: JacocoMethodCoverage,
        graph: CallGraph,
        sampled_routes: RouteMap,
        entry_routes: RouteMap,
        target_state: TargetState,
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
        )
    if target_id in sampled_routes.distance:
        path, edges = _route_to(target_id, sampled_routes)
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
            semantic_distance=_path_distance(path, graph),
        )
    if target_id in entry_routes.distance:
        path, edges = _route_to(target_id, entry_routes)
        return NearCallRecord(
            coverage=coverage,
            target_id=target_id,
            target_state=target_state,
            join_kind="public-entry",
            static_path=path,
            static_path_edges=edges,
            sample=None,
            sampled_join_path_index=None,
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
    )


def _record_rank_key(record: NearCallRecord) -> tuple:
    join_order: dict[str, int] = {"sampled": 0, "public-entry": 1, "none": 2}
    distance: int = record.distance if record.distance is not None else sys.maxsize
    return (distance, join_order[record.join_kind], -record.sample_count, record.target_ref.canonical_id)


def _prompt_selection_key(record: NearCallRecord) -> tuple:
    return (record.attempt_count, *_record_rank_key(record))


def _method_evidence(coverage: JacocoMethodCoverage, graph_status: str = "present") -> dict:
    return {
        "id": coverage.method_ref.canonical_id,
        "status": coverage.status,
        "synthetic": _is_synthetic_method(coverage.method_ref),
        "graphStatus": graph_status,
        "sourcePath": coverage.source_path,
        "sourceLine": coverage.source_line,
        "jacocoReportPaths": list(coverage.report_paths),
    }


def _edge_to_json(edge: dict, graph: CallGraph) -> dict:
    return {
        "caller": _format_static_id(edge["caller"], graph),
        "callee": _format_static_id(edge["callee"], graph),
        "bci": edge["bci"],
        "invokeId": edge.get("invoke_id"),
        "isDirect": edge["is_direct"],
        "kind": edge["kind"],
    }


def _line_to_json(
        source_path: str,
        line: int,
        coverage: JacocoLineCoverage,
) -> dict:
    return {
        "sourcePath": source_path,
        "line": line,
        "mi": coverage.mi,
        "ci": coverage.ci,
        "mb": coverage.mb,
        "cb": coverage.cb,
    }


def _method_line_region(
        caller: MethodRef,
        jacoco_methods: dict[str, JacocoMethodCoverage],
        jacoco_lines: dict[str, dict[int, JacocoLineCoverage]],
) -> tuple[str | None, list[tuple[int, JacocoLineCoverage]]]:
    """Return the caller's source lines, bounded by the next reported method."""
    coverage: JacocoMethodCoverage | None = jacoco_methods.get(caller.canonical_id)
    if (
            coverage is None
            or coverage.source_path is None
            or coverage.source_line is None
    ):
        return None, []
    source_lines: dict[int, JacocoLineCoverage] = jacoco_lines.get(
        coverage.source_path, {}
    )
    later_starts: list[int] = sorted({
        method.source_line
        for method in jacoco_methods.values()
        if method.source_path == coverage.source_path
        and method.source_line is not None
        and method.source_line > coverage.source_line
    })
    end_line: int = (
        later_starts[0] - 1 if later_starts else max(source_lines, default=coverage.source_line)
    )
    return coverage.source_path, [
        (line, line_coverage)
        for line, line_coverage in sorted(source_lines.items())
        if coverage.source_line <= line <= end_line
    ]


def _candidate_is_coverage_suite(ref: MethodRef) -> bool:
    """Recognize generated extension-suite classes by their naming contract."""
    owner: str = ref.owner.rsplit(".", 1)[-1].split("$", 1)[0]
    return "CoverageTest" in owner


def _dispatch_candidates(edge: dict, graph: CallGraph) -> list[dict]:
    invoke_id: int | None = edge.get("invoke_id")
    method_ids: list[int] = (
        graph.invoke_fan_out.get(invoke_id, []) if invoke_id is not None else []
    )
    if not method_ids and edge.get("callee") in graph.methods:
        method_ids = [edge["callee"]]
    return [
        {
            "id": graph.methods[method_id].canonical_id,
            "coverageSuite": _candidate_is_coverage_suite(graph.methods[method_id]),
        }
        for method_id in method_ids
        if method_id in graph.methods
    ]


def _missed_blocks(
        region: list[tuple[int, JacocoLineCoverage]],
) -> list[list[tuple[int, JacocoLineCoverage]]]:
    blocks: list[list[tuple[int, JacocoLineCoverage]]] = []
    current: list[tuple[int, JacocoLineCoverage]] = []
    for line_record in region:
        _, coverage = line_record
        if coverage.ci == 0 and coverage.mi > 0:
            current.append(line_record)
            continue
        if current:
            blocks.append(current)
            current = []
    if current:
        blocks.append(current)
    return blocks


def _inferred_invoking_line(
        edge: dict,
        graph: CallGraph,
        region: list[tuple[int, JacocoLineCoverage]],
) -> tuple[int, JacocoLineCoverage] | None:
    """Locate the line region containing the invoke without extra artifacts.

    The class-file extractor maps the call-tree bytecode index to an explicit
    source line. Old extractor artifacts lack that mapping, so they fall back
    deterministically to the final uncovered block and its strongest
    non-branch instruction signal.
    """
    explicit_line: int | None = edge.get("source_line")
    if explicit_line is not None:
        explicit: tuple[int, JacocoLineCoverage] | None = next(
            (record for record in region if record[0] == explicit_line), None
        )
        if explicit is not None:
            return explicit

    blocks: list[list[tuple[int, JacocoLineCoverage]]] = _missed_blocks(region)
    if blocks:
        block: list[tuple[int, JacocoLineCoverage]] = blocks[-1]
        non_branch_lines: list[tuple[int, JacocoLineCoverage]] = [
            record for record in block if record[1].mb == 0 and record[1].cb == 0
        ]
        candidates_for_line: list[tuple[int, JacocoLineCoverage]] = (
            non_branch_lines or block
        )
        most_instructions: int = max(
            coverage.mi for _, coverage in candidates_for_line
        )
        return next(
            record
            for record in candidates_for_line
            if record[1].mi == most_instructions
        )

    return next((record for record in region if record[1].covered), None)


def _edge_miss_classification(
        edge: dict,
        graph: CallGraph,
        jacoco_methods: dict[str, JacocoMethodCoverage],
        jacoco_lines: dict[str, dict[int, JacocoLineCoverage]],
) -> dict:
    """Classify one reverse call site from its caller's JaCoCo line region."""
    caller: MethodRef | None = graph.methods.get(edge.get("caller"))
    candidates: list[dict] = _dispatch_candidates(edge, graph)
    if caller is None:
        return {
            "kind": "no-fork",
            "target": None,
            "invokingMethod": None,
            "invokeBci": edge.get("bci"),
            "fork": None,
            "nearestCovered": None,
            "candidates": candidates,
        }

    source_path, region = _method_line_region(caller, jacoco_methods, jacoco_lines)
    target_record: tuple[int, JacocoLineCoverage] | None = _inferred_invoking_line(
        edge, graph, region
    )
    target: dict | None = (
        _line_to_json(source_path, *target_record)
        if source_path is not None and target_record is not None
        else None
    )
    base: dict = {
        "target": target,
        "invokingMethod": caller.canonical_id,
        "invokeBci": edge.get("bci"),
        "fork": None,
        "nearestCovered": None,
        "candidates": candidates,
    }
    if target_record is not None and target_record[1].covered and len(candidates) > 1:
        return {"kind": "dispatched-elsewhere", **base}

    target_index: int = (
        region.index(target_record) if target_record is not None else len(region)
    )
    preceding: list[tuple[int, JacocoLineCoverage]] = region[:target_index]
    nearest_covered: tuple[int, JacocoLineCoverage] | None = next(
        (record for record in reversed(preceding) if record[1].covered), None
    )
    base["nearestCovered"] = (
        _line_to_json(source_path, *nearest_covered)
        if source_path is not None and nearest_covered is not None
        else None
    )

    containing_block: list[tuple[int, JacocoLineCoverage]] = next(
        (
            block
            for block in _missed_blocks(region)
            if target_record is not None and target_record in block
        ),
        [],
    )
    exception_handler_entry: bool = bool(
        containing_block
        and target_record != containing_block[0]
        and nearest_covered is not None
        and containing_block[0][0] == nearest_covered[0] + 1
        and containing_block[0][1].mi == 1
        and nearest_covered[1].mb == 0
    )
    fork: tuple[int, JacocoLineCoverage] | None = None
    if not exception_handler_entry:
        fork = next(
            (
                record
                for record in reversed(preceding)
                if record[1].covered and record[1].mb > 0
            ),
            None,
        )
    if fork is not None and source_path is not None:
        base["fork"] = _line_to_json(source_path, *fork)
        return {"kind": "fork-not-taken", **base}
    return {"kind": "no-fork", **base}


def _classify_miss(
        record: NearCallRecord,
        graph: CallGraph,
        jacoco_methods: dict[str, JacocoMethodCoverage],
        jacoco_lines: dict[str, dict[int, JacocoLineCoverage]],
) -> dict:
    """Choose the strongest diagnosis across all sites that invoke a target."""
    if record.target_id is None:
        edges: list[dict] = []
    else:
        routed_edges: list[dict] = [
            edge
            for edge in reversed(record.static_path_edges)
            if edge.get("callee") == record.target_id
        ]
        edges = [*routed_edges, *graph.reverse_adjacency.get(record.target_id, [])]
    unique_edges: list[dict] = []
    seen: set[tuple[object, ...]] = set()
    for edge in edges:
        key: tuple[object, ...] = (
            edge.get("invoke_id"), edge.get("caller"), edge.get("callee"), edge.get("bci")
        )
        if key not in seen:
            seen.add(key)
            unique_edges.append(edge)
    classifications: list[dict] = [
        _edge_miss_classification(
            edge, graph, jacoco_methods, jacoco_lines
        )
        for edge in unique_edges
    ]
    priority: dict[str, int] = {
        "dispatched-elsewhere": 0,
        "fork-not-taken": 1,
        "no-fork": 2,
    }
    if classifications:
        return min(
            classifications,
            key=lambda item: (
                priority[item["kind"]],
                (
                    item["target"]["sourcePath"]
                    if item["target"] is not None else ""
                ),
                item["target"]["line"] if item["target"] is not None else sys.maxsize,
            ),
        )
    return {
        "kind": "no-fork",
        "target": None,
        "invokingMethod": None,
        "invokeBci": None,
        "fork": None,
        "nearestCovered": None,
        "candidates": [],
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
                return f"{_simple_owner(callee.owner)}.{callee.name}"
    return None


def _translated_path(static_path: list[int], graph: CallGraph) -> list[MethodRef]:
    """Replace synthetic nodes by source-level methods, collapsing repeats."""
    translated: list[MethodRef] = []
    for static_id in static_path:
        ref: MethodRef = _translated_ref(static_id, graph)
        if not translated or translated[-1].canonical_id != ref.canonical_id:
            translated.append(ref)
    return translated


def _path_distance(static_path: list[int], graph: CallGraph) -> int:
    """Count edges in the source-level path used for prompt ranking."""
    return max(0, len(_translated_path(static_path, graph)) - 1)


def _record_to_json(
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
        "rank": rank,
        "attemptCount": record.attempt_count,
        "targetStatus": record.target_state.status,
        "terminal": record.target_state.terminal,
        "stateReason": record.target_state.reason,
        "lastAttemptedIteration": record.target_state.last_attempted_iteration,
        "graphStatus": "present" if graph_present else "not-present",
        "joinKind": record.join_kind,
        "stepsRemaining": record.distance,
        "sampleCount": record.sample_count,
        "sampleContextId": record.sample.context_id if record.sample is not None else None,
        "synthetic": _is_synthetic_method(record.target_ref),
        "closures": _closure_stats(record, graph, jacoco_methods),
        "handOff": _hand_off_note(record, graph),
        "missClassification": miss_classification,
        "reachingPath": (
            [
                ref.canonical_id
                for ref in _translated_path(record.static_path, graph)
            ]
            if graph_present else None
        ),
        "reachingPathRaw": (
            [_format_static_id(static_id, graph) for static_id in record.static_path]
            if graph_present else None
        ),
        "edges": (
            [_edge_to_json(edge, graph) for edge in record.static_path_edges]
            if graph_present else None
        ),
    }


def _simple_owner(owner: str) -> str:
    return owner.rsplit(".", 1)[-1].replace("$", ".")
