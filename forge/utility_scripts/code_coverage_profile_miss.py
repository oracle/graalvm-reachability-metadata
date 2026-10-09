# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Miss classification for the deep-method report.

Explains why each uncovered target did not run, from the JaCoCo line region of
the method that makes the route's call into it and — when present — the
extractor's control-flow table and the instrumented counters: a different
implementation answered, a covered fork went the other way, or no fork precedes
the call at all (§AR-code-coverage-deep-navigation.3).
"""

from __future__ import annotations

from utility_scripts.code_coverage_jacoco import JacocoLineCoverage, JacocoMethodCoverage
from utility_scripts.code_coverage_model import MethodRef
from utility_scripts.code_coverage_profile_counters import SiteDispatch, site_dispatch
from utility_scripts.code_coverage_profile_fork import (
    ForkTrace,
    LineRecord,
    fork_to_json,
    line_to_json,
    trace_fork,
)
from utility_scripts.code_coverage_profile_graph import CallGraph, translated_ref
from utility_scripts.code_coverage_profile_inputs import line_at
from utility_scripts.code_coverage_profile_navigation import NavigationEvidence
from utility_scripts.code_coverage_profile_records import NearCallRecord

def _method_line_region(
        caller: MethodRef,
        jacoco_methods: dict[str, JacocoMethodCoverage],
        jacoco_lines: dict[str, dict[int, JacocoLineCoverage]],
        line_table: tuple[tuple[int, int], ...],
) -> tuple[str | None, list[LineRecord]]:
    """Return the caller's own source lines.

    The extractor's line table names exactly the lines of the caller's
    bytecode, so a lambda body or an anonymous or local class nested in its
    range stays out (§AR-code-coverage-deep-navigation.3). Without a table the
    region runs to the next method JaCoCo reports in the same file.
    """
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
    if line_table:
        own_lines: set[int] = {line for _, line in line_table}
        return coverage.source_path, [
            (line, line_coverage)
            for line, line_coverage in sorted(source_lines.items())
            if line in own_lines
        ]
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


def _dispatch_candidates(
        edge: dict,
        graph: CallGraph,
        dispatch: SiteDispatch | None,
) -> list[dict]:
    invoke_id: int | None = edge.get("invoke_id")
    method_ids: list[int] = (
        graph.invoke_fan_out.get(invoke_id, []) if invoke_id is not None else []
    )
    if not method_ids and edge.get("callee") in graph.methods:
        method_ids = [edge["callee"]]
    resolved: dict[int, int] | None = dispatch.dispatches if dispatch is not None else None
    return [
        {
            "id": graph.methods[method_id].canonical_id,
            "coverageSuite": _candidate_is_coverage_suite(graph.methods[method_id]),
            "dispatches": resolved.get(method_id) if resolved is not None else None,
        }
        for method_id in method_ids
        if method_id in graph.methods
    ]


def _site_to_json(dispatch: SiteDispatch | None, callee: int | None) -> dict | None:
    if dispatch is None:
        return None
    return {
        "dispatches": dispatch.total,
        "receivers": [
            {"type": receiver, "count": count}
            for receiver, count in sorted(
                dispatch.receivers.items(), key=lambda item: (-item[1], item[0])
            )
        ],
        "targetDispatches": (
            dispatch.dispatches.get(callee) if dispatch.dispatches is not None else None
        ),
    }


def _missed_blocks(region: list[LineRecord]) -> list[list[LineRecord]]:
    blocks: list[list[LineRecord]] = []
    current: list[LineRecord] = []
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


def _inferred_invoking_line(edge: dict, region: list[LineRecord]) -> LineRecord | None:
    """Locate the line region containing the invoke without extra artifacts.

    The class-file extractor maps the call-tree bytecode index to an explicit
    source line. Old extractor artifacts lack that mapping, so they fall back
    deterministically to the final uncovered block and its strongest
    non-branch instruction signal.
    """
    explicit_line: int | None = edge.get("source_line")
    if explicit_line is not None:
        explicit: LineRecord | None = next(
            (record for record in region if record[0] == explicit_line), None
        )
        if explicit is not None:
            return explicit

    blocks: list[list[LineRecord]] = _missed_blocks(region)
    if blocks:
        block: list[LineRecord] = blocks[-1]
        non_branch_lines: list[LineRecord] = [
            record for record in block if record[1].mb == 0 and record[1].cb == 0
        ]
        candidates_for_line: list[LineRecord] = non_branch_lines or block
        most_instructions: int = max(
            coverage.mi for _, coverage in candidates_for_line
        )
        return next(
            record
            for record in candidates_for_line
            if record[1].mi == most_instructions
        )

    return next((record for record in region if record[1].covered), None)


def edge_miss_classification(
        edge: dict,
        graph: CallGraph,
        jacoco_methods: dict[str, JacocoMethodCoverage],
        jacoco_lines: dict[str, dict[int, JacocoLineCoverage]],
        evidence: NavigationEvidence = NavigationEvidence(),
) -> dict:
    """Classify one reverse call site from its caller's JaCoCo line region."""
    raw_caller: MethodRef | None = graph.methods.get(edge.get("caller"))
    # A caller JaCoCo does not report, such as a factory stub, is judged on the
    # lines of the method it stands for (§AR-code-coverage-deep-navigation.2).
    caller: MethodRef | None = (
        translated_ref(edge["caller"], graph)
        if raw_caller is not None and raw_caller.canonical_id not in jacoco_methods
        else raw_caller
    )
    dispatch: SiteDispatch | None = site_dispatch(edge, graph, evidence.counters, evidence.types)
    candidates: list[dict] = _dispatch_candidates(edge, graph, dispatch)
    base: dict = {
        "target": None,
        "invokingMethod": caller.canonical_id if caller is not None else None,
        "invokeBci": edge.get("bci"),
        "fork": None,
        "nearestCovered": None,
        "candidates": candidates,
        "site": _site_to_json(dispatch, edge.get("callee")),
        "reach": None,
    }
    if caller is None:
        return {"kind": "no-fork", **base}

    source_path, region = _method_line_region(
        caller,
        jacoco_methods,
        jacoco_lines,
        evidence.line_numbers.get(caller.canonical_id, ()),
    )
    target_record: LineRecord | None = _inferred_invoking_line(edge, region)
    base["target"] = (
        line_to_json(source_path, *target_record)
        if source_path is not None and target_record is not None
        else None
    )
    if target_record is not None and target_record[1].covered and len(candidates) > 1:
        base["reach"] = dispatch.total if dispatch is not None else None
        return {"kind": "dispatched-elsewhere", **base}

    target_index: int = (
        region.index(target_record) if target_record is not None else len(region)
    )
    preceding: list[LineRecord] = region[:target_index]
    nearest_covered: LineRecord | None = next(
        (record for record in reversed(preceding) if record[1].covered), None
    )
    base["nearestCovered"] = (
        line_to_json(source_path, *nearest_covered)
        if source_path is not None and nearest_covered is not None
        else None
    )

    containing_block: list[LineRecord] = next(
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
    forks_above: list[LineRecord] = [
        record for record in reversed(preceding) if record[1].covered and record[1].mb > 0
    ]
    same_line: list[LineRecord] = [
        record for record in [target_record]
        if record is not None and record[1].covered and record[1].mb > 0
    ]
    # The invoke's bci belongs to the raw caller, so only its own control
    # flow can trace it.
    traced: ForkTrace | None = trace_fork(
        caller,
        edge,
        target_record[0] if target_record is not None else None,
        [*same_line, *forks_above],
        dict(region),
        graph,
        evidence,
    ) if caller is raw_caller else None
    fork: LineRecord | None = None
    branches: list[dict] | None = None
    exception: dict | None = None
    if traced is not None:
        fork, branches, exception = traced.fork, traced.branches, traced.exception
    elif not exception_handler_entry:
        # Without a control-flow table, line order is all there is: the nearest
        # missed branch above, unless the lines read as a catch entry.
        fork = next(iter(forks_above), None)
    if fork is not None and source_path is not None:
        base["fork"] = fork_to_json(source_path, fork, branches)
        base["reach"] = base["fork"]["reach"]
        return {"kind": "fork-not-taken", **base}
    return {"kind": "no-fork", **base, "exception": exception}


def _routed_last_call(record: NearCallRecord, graph: CallGraph) -> dict | None:
    """The route's call into the target's source-level method, if it has one.

    A step that stays inside the target, such as a factory stub reaching its
    constructor, is looked through to the stub's caller on the route
    (§AR-code-coverage-deep-navigation.3).
    """
    if record.target_id is None:
        return None
    target_ref_id: str = translated_ref(record.target_id, graph).canonical_id
    for edge in reversed(record.static_path_edges):
        if translated_ref(edge["caller"], graph).canonical_id != target_ref_id:
            return edge
    return None


def classify_miss(
        record: NearCallRecord,
        graph: CallGraph,
        jacoco_methods: dict[str, JacocoMethodCoverage],
        jacoco_lines: dict[str, dict[int, JacocoLineCoverage]],
        executed: dict[int, bool],
        evidence: NavigationEvidence = NavigationEvidence(),
) -> dict:
    """Judge the one call the route makes into the target.

    No other call site of the target is consulted, so the route and its
    diagnosis describe the same call; a route call without a fork above it is
    `no-fork` there (§AR-code-coverage-deep-navigation.3).
    """
    edge: dict | None = _routed_last_call(record, graph)
    # Route search lets only a covered method make the last call
    # (§AR-code-coverage-deep-navigation.2).
    if edge is not None and executed.get(edge["caller"]) is True:
        return edge_miss_classification(edge, graph, jacoco_methods, jacoco_lines, evidence)
    return {
        "kind": "no-fork",
        "target": None,
        "invokingMethod": None,
        "invokeBci": None,
        "fork": None,
        "nearestCovered": None,
        "candidates": [],
        "site": None,
        "reach": None,
    }
