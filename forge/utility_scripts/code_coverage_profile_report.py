# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""
JaCoCo-exact deep-method report with sampled-PGO path guidance
(§WF-code-coverage-improvement, §WF-code-coverage-improvement-architecture).

JaCoCo is the only coverage authority. The analyzer selects exact library
methods reported by JaCoCo but absent from the public API inventory. Sampled
PGO and the static graph rank graph-present paths; graph-absent methods remain
in full JSON but never enter prompts.

Inputs:
- `call_tree_{methods,invokes,targets}_*.csv` — the analysis call-tree CSV dump
  (`-H:+PrintAnalysisCallTree -H:PrintAnalysisCallTreeType=CSV`).
- a sampled `.iprof` — profile `<`-chain contexts are leaf-first
  (`callee:bci<caller:bci`), so sampled stacks read right-to-left from the root.
- `api-inventory.json` — the public user-callable target universe.
- one or more JaCoCo XML reports — exact coverage for public and deep methods.

Usage:
  python3 utility_scripts/code_coverage_profile_report.py \
    --profile <native-test.iprof> \
    --reports-dir <build/.../reports> \
    --api-inventory <api-inventory.json> \
    --jacoco-xml <jacoco-N.xml> [--jacoco-xml <additional.xml>] \
    --coordinate group:artifact:version \
    --iteration 0 \
    [--target-state <deep-cover-N.json>] \
    --output-dir runtime/code-coverage/discovery
"""

from __future__ import annotations

import argparse
import csv
import heapq
from dataclasses import dataclass, field
import json
import os
import sys

from utility_scripts.code_coverage_jacoco import (
    JacocoMethodCoverage,
    JacocoReportError,
    load_jacoco_method_coverage,
)
from utility_scripts.code_coverage_model import (
    MethodRef,
    method_ref_from_call_tree_row,
    method_ref_from_iprof,
    parse_inventory_id,
)

MAX_LISTED_METHODS = 100
TARGET_STATE_STATUSES: frozenset[str] = frozenset({
    "pending", "selected", "attempted", "completed", "skipped", "exhausted", "failed",
})
TERMINAL_TARGET_STATUSES: frozenset[str] = frozenset({"completed", "skipped", "exhausted", "failed"})

# Stack frames owned by these packages are runtime/harness plumbing, never a
# useful join point to show an agent.
FRAMEWORK_TYPE_PREFIXES = (
    "com.oracle.svm.",
    "java.",
    "javax.",
    "jdk.",
    "junit.",
    "org.gradle.",
    "org.graalvm.junit.",
    "org.junit.",
    "sun.",
    "worker.org.gradle.",
)

TEST_TYPE_SUFFIXES = ("IT", "ITCase", "Test", "TestCase", "Tests")


class ProfileFormatError(RuntimeError):
    """Raised when report inputs violate the analyzer contract."""


def _load_json_object(path: str, label: str) -> dict:
    try:
        with open(path, "r", encoding="utf-8") as json_file:
            document = json.load(json_file)
    except (OSError, UnicodeError) as error:
        raise ProfileFormatError(f"Cannot read {label} '{path}'.") from error
    except json.JSONDecodeError as error:
        raise ProfileFormatError(f"Invalid JSON in {label} '{path}'.") from error
    if not isinstance(document, dict):
        raise ProfileFormatError(f"{label.capitalize()} '{path}' must contain a JSON object.")
    return document


@dataclass
class CallGraph:
    methods: dict[int, MethodRef] = field(default_factory=dict)
    key_to_id: dict[str, int] = field(default_factory=dict)
    loose_to_ids: dict[str, list[int]] = field(default_factory=dict)
    adjacency: dict[int, list[dict]] = field(default_factory=dict)
    reverse_adjacency: dict[int, list[dict]] = field(default_factory=dict)


@dataclass
class Sample:
    """One sampled context, root-first, with its static-graph projection."""

    context_id: str
    raw_context: str
    path: list[tuple[int, int]]
    full_path: list[tuple[MethodRef, int]]
    path_full_indexes: list[int]
    count: int


@dataclass
class SampledProfile:
    samples: list[Sample] = field(default_factory=list)
    sample_counts: dict[int, int] = field(default_factory=dict)
    total_sample_count: int = 0
    context_count: int = 0


@dataclass(frozen=True)
class TargetState:
    """Durable state for one exact deep-coverage target."""

    status: str = "pending"
    attempt_count: int = 0
    last_attempted_iteration: int | None = None
    reason: str | None = None

    @property
    def terminal(self) -> bool:
        return self.status in TERMINAL_TARGET_STATUSES


def _require_coordinate(document: dict, expected: str, label: str) -> None:
    actual = document.get("coordinate")
    if not isinstance(actual, str) or not actual.strip():
        raise ProfileFormatError(f"{label} is missing coordinate.")
    if actual != expected:
        raise ProfileFormatError(
            f"{label} coordinate '{actual}' does not match '{expected}'."
        )


def _parse_target_state(entry: object, path: str, index: int) -> tuple[str, TargetState]:
    if not isinstance(entry, dict):
        raise ProfileFormatError(f"Target state '{path}' target {index} must be an object.")

    method_id = entry.get("id")
    ref = parse_inventory_id(method_id) if isinstance(method_id, str) else None
    if (
            ref is None
            or ref.return_type is None
            or ref.canonical_id != method_id
    ):
        raise ProfileFormatError(
            f"Target state '{path}' target {index} has a non-canonical method id."
        )

    status = entry.get("status")
    if status not in TARGET_STATE_STATUSES:
        raise ProfileFormatError(
            f"Target state '{path}' target '{method_id}' has unknown status '{status}'."
        )

    attempt_count = entry.get("attemptCount")
    if type(attempt_count) is not int or attempt_count < 0:
        raise ProfileFormatError(
            f"Target state '{path}' target '{method_id}' needs a non-negative attemptCount."
        )

    last_iteration = entry.get("lastAttemptedIteration")
    if (
            last_iteration is not None
            and (type(last_iteration) is not int or last_iteration <= 0)
    ):
        raise ProfileFormatError(
            f"Target state '{path}' target '{method_id}' has invalid lastAttemptedIteration."
        )

    reason = entry.get("reason")
    if reason is not None and (not isinstance(reason, str) or not reason.strip()):
        raise ProfileFormatError(
            f"Target state '{path}' target '{method_id}' has an invalid reason."
        )
    if status in {"skipped", "exhausted", "failed"} and reason is None:
        raise ProfileFormatError(
            f"Target state '{path}' target '{method_id}' requires a reason."
        )

    return method_id, TargetState(
        status=status,
        attempt_count=attempt_count,
        last_attempted_iteration=last_iteration,
        reason=reason,
    )


def load_target_states(paths: list[str] | None, coordinate: str) -> dict[str, TargetState]:
    """Load repeatable coordinate-scoped state files; later files take precedence."""
    states: dict[str, TargetState] = {}
    for path in paths or []:
        document = _load_json_object(path, "target state")
        _require_coordinate(document, coordinate, "Target state")
        entries = document.get("targets")
        if not isinstance(entries, list):
            raise ProfileFormatError(f"Target state '{path}' must contain a targets list.")
        seen: set[str] = set()
        for index, entry in enumerate(entries, start=1):
            method_id, state = _parse_target_state(entry, path, index)
            if method_id in seen:
                raise ProfileFormatError(
                    f"Target state '{path}' repeats target '{method_id}'."
                )
            seen.add(method_id)
            states[method_id] = state
    return states


def _target_state_to_json(method_id: str, state: TargetState) -> dict:
    return {
        "id": method_id,
        "status": state.status,
        "terminal": state.terminal,
        "attemptCount": state.attempt_count,
        "lastAttemptedIteration": state.last_attempted_iteration,
        "reason": state.reason,
    }


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
        return max(0, len(self.static_path) - 1)

    @property
    def sample_count(self) -> int:
        return self.sample.count if self.sample is not None else 0


def _find_call_tree_files(directory: str) -> tuple[str, str, str]:
    """Select one coherent methods/invokes/targets CSV suffix atomically."""
    kinds: tuple[str, ...] = ("methods", "invokes", "targets")
    candidates: dict[tuple[str, str], dict[str, str]] = {}
    for current_dir, dir_names, file_names in os.walk(directory):
        dir_names.sort()
        for file_name in sorted(file_names):
            for kind in kinds:
                prefix: str = f"call_tree_{kind}"
                suffix: str | None = None
                if file_name == f"{prefix}.csv":
                    suffix = ""
                elif file_name.startswith(prefix + "_") and file_name.endswith(".csv"):
                    suffix = file_name[len(prefix) + 1:-4]
                if suffix is None:
                    continue
                candidates.setdefault((current_dir, suffix), {})[kind] = os.path.join(
                    current_dir, file_name
                )
    complete: list[tuple[str, str, dict[str, str]]] = [
        (suffix, current_dir, files)
        for (current_dir, suffix), files in candidates.items()
        if all(kind in files for kind in kinds)
    ]
    if not complete:
        raise FileNotFoundError(
            os.path.join(directory, "call_tree_{methods,invokes,targets}_*.csv")
        )
    _, _, selected = max(complete, key=lambda item: (item[0], item[1]))
    return selected["methods"], selected["invokes"], selected["targets"]


def _read_csv_by_id(path: str) -> dict[int, dict]:
    with open(path, encoding="utf-8", newline="") as csv_file:
        return {int(row["Id"]): row for row in csv.DictReader(csv_file)}


def _load_call_graph(reports_dir: str) -> CallGraph:
    """Load the analysis call-tree CSV dump into an id-indexed call graph."""
    methods_path, invokes_path, targets_path = _find_call_tree_files(reports_dir)
    methods_rows = _read_csv_by_id(methods_path)
    invokes_rows = _read_csv_by_id(invokes_path)
    with open(targets_path, encoding="utf-8", newline="") as csv_file:
        target_rows = list(csv.DictReader(csv_file))

    graph = CallGraph()
    for method_id, row in methods_rows.items():
        ref = method_ref_from_call_tree_row(row)
        if ref is None:
            continue
        graph.methods[method_id] = ref
        graph.key_to_id.setdefault(ref.canonical_id, method_id)
        graph.loose_to_ids.setdefault(ref.loose_key, []).append(method_id)

    for target_row in target_rows:
        invoke = invokes_rows.get(int(target_row["InvokeId"]))
        if invoke is None:
            continue
        caller_id = int(invoke["MethodId"])
        callee_id = int(target_row["TargetId"])
        if caller_id not in graph.methods or callee_id not in graph.methods:
            continue
        edge = {
            "caller": caller_id,
            "callee": callee_id,
            "bci": invoke.get("BytecodeIndexes", ""),
            "is_direct": invoke.get("IsDirect", ""),
        }
        graph.adjacency.setdefault(caller_id, []).append(edge)
        graph.reverse_adjacency.setdefault(callee_id, []).append(edge)
    for method_ids in graph.loose_to_ids.values():
        method_ids.sort(key=lambda method_id: graph.methods[method_id].canonical_id)
    for edges in graph.adjacency.values():
        edges.sort(key=lambda edge: (
            graph.methods[edge["callee"]].canonical_id,
            str(edge["bci"]),
        ))
    for edges in graph.reverse_adjacency.values():
        edges.sort(key=lambda edge: (
            graph.methods[edge["caller"]].canonical_id,
            str(edge["bci"]),
        ))

    return graph


def load_call_graph(reports_dir: str) -> CallGraph:
    """Load one coherent call-tree triplet, failing closed on bad input."""
    try:
        return _load_call_graph(reports_dir)
    except (OSError, csv.Error, KeyError, TypeError, ValueError) as error:
        raise ProfileFormatError(f"Cannot load call-tree CSVs from '{reports_dir}'.") from error


def _resolve_graph_id(graph: CallGraph, ref: MethodRef) -> int | None:
    """Resolve an exact graph method, or an unambiguous loose guidance match."""
    static_id: int | None = graph.key_to_id.get(ref.canonical_id)
    if static_id is not None:
        return static_id
    loose_ids: list[int] = graph.loose_to_ids.get(ref.loose_key, [])
    return loose_ids[0] if len(loose_ids) == 1 else None



def _parse_ctx(ctx: str) -> list[tuple[int, int]]:
    """Return `(method_id, bci)` pairs from a context chain, root-first."""
    pairs: list[tuple[int, int]] = []
    for token in reversed(ctx.split("<")):
        method_part, _, bci_part = token.partition(":")
        try:
            pairs.append((int(method_part), int(bci_part or 0)))
        except ValueError:
            continue
    return pairs


def _load_sampled_profile(profile_path: str, graph: CallGraph) -> SampledProfile:
    """Parse a sampled `.iprof` and map its stacks onto static call-graph ids."""
    document: dict = _load_json_object(profile_path, "sampled profile")
    if "samplingProfiles" not in document:
        raise ProfileFormatError(
            "Profile has no samplingProfiles section. Re-collect the profile with "
            "the PGO-sampling harness tasks (nativeTestPGOSampling/runNativeTestPGO)."
        )

    type_names = {entry["id"]: entry["name"] for entry in document.get("types", [])}
    iprof_refs: dict[int, MethodRef] = {}
    for record in document.get("methods", []):
        ref = method_ref_from_iprof(record, type_names)
        if ref is not None:
            iprof_refs[record["id"]] = ref

    profile = SampledProfile()
    for context_index, sampling in enumerate(document.get("samplingProfiles") or [], start=1):
        records: list = sampling.get("records") or [0]
        count: int = sum(int(record) for record in records)
        if count <= 0:
            continue
        profile.total_sample_count += count
        profile.context_count += 1
        full_path: list[tuple[MethodRef, int]] = []
        path: list[tuple[int, int]] = []
        path_full_indexes: list[int] = []
        for method_id, bci in _parse_ctx(sampling.get("ctx", "")):
            ref: MethodRef | None = iprof_refs.get(method_id)
            if ref is None:
                continue
            full_path.append((ref, bci))
            static_id: int | None = _resolve_graph_id(graph, ref)
            if static_id is not None and (not path or path[-1][0] != static_id):
                path.append((static_id, bci))
                path_full_indexes.append(len(full_path) - 1)
        profile.samples.append(Sample(
            context_id=f"sample-{context_index}",
            raw_context=sampling.get("ctx", ""),
            path=path,
            full_path=full_path,
            path_full_indexes=path_full_indexes,
            count=count,
        ))
        for static_id in {static_id for static_id, _ in path}:
            profile.sample_counts[static_id] = profile.sample_counts.get(static_id, 0) + count
    return profile


def load_sampled_profile(profile_path: str, graph: CallGraph) -> SampledProfile:
    """Load a sampled profile, normalizing malformed content to one error type."""
    try:
        return _load_sampled_profile(profile_path, graph)
    except ProfileFormatError:
        raise
    except (KeyError, TypeError, ValueError) as error:
        raise ProfileFormatError(f"Malformed sampled profile '{profile_path}'.") from error


def _looks_like_framework_frame(ref: MethodRef) -> bool:
    return ref.owner.startswith(FRAMEWORK_TYPE_PREFIXES)


def _looks_like_existing_test_frame(ref: MethodRef) -> bool:
    if _looks_like_framework_frame(ref):
        return False
    simple_holder = ref.owner.rsplit(".", 1)[-1]
    return simple_holder.endswith(TEST_TYPE_SUFFIXES)


def _frame_prompt_quality(ref: MethodRef) -> int:
    if _looks_like_framework_frame(ref):
        return 0
    if _looks_like_existing_test_frame(ref):
        return 1
    return 2


def _existing_test_frame_index(full_path: list[tuple[MethodRef, int]]) -> int | None:
    for index, (ref, _) in enumerate(full_path):
        if _looks_like_existing_test_frame(ref):
            return index
    return None


@dataclass
class RouteMap:
    distance: dict[int, int] = field(default_factory=dict)
    previous: dict[int, tuple[int, dict] | None] = field(default_factory=dict)
    payload: dict[int, object] = field(default_factory=dict)


def _multi_source_routes(
        graph: CallGraph,
        seeds: list[tuple[int, tuple, object]],
) -> RouteMap:
    """Compute deterministic shortest paths from ranked source methods."""
    routes = RouteMap()
    best_keys: dict[int, tuple[int, tuple]] = {}
    queue: list[tuple[int, tuple, str, int]] = []
    for static_id, seed_rank, payload in seeds:
        candidate_key: tuple[int, tuple] = (0, seed_rank)
        if static_id in best_keys and best_keys[static_id] <= candidate_key:
            continue
        best_keys[static_id] = candidate_key
        routes.distance[static_id] = 0
        routes.previous[static_id] = None
        routes.payload[static_id] = payload
        heapq.heappush(queue, (0, seed_rank, graph.methods[static_id].canonical_id, static_id))

    while queue:
        distance, seed_rank, _, current = heapq.heappop(queue)
        if best_keys.get(current) != (distance, seed_rank):
            continue
        for edge in graph.adjacency.get(current, []):
            callee: int = edge["callee"]
            candidate_key = (distance + 1, seed_rank)
            if callee in best_keys and best_keys[callee] <= candidate_key:
                continue
            best_keys[callee] = candidate_key
            routes.distance[callee] = distance + 1
            routes.previous[callee] = (current, edge)
            routes.payload[callee] = routes.payload[current]
            heapq.heappush(
                queue,
                (distance + 1, seed_rank, graph.methods[callee].canonical_id, callee),
            )
    return routes


def _sample_routes(graph: CallGraph, profile: SampledProfile) -> RouteMap:
    seeds: list[tuple[int, tuple, object]] = []
    for sample in profile.samples:
        test_index: int | None = _existing_test_frame_index(sample.full_path)
        for path_index, (static_id, _) in enumerate(sample.path):
            full_index: int = sample.path_full_indexes[path_index]
            join_after_test: bool = test_index is None or full_index >= test_index
            # Distance is compared before this seed rank. Prompt quality only
            # breaks ties between equally short sampled routes.
            seed_rank: tuple = (
                0 if join_after_test else 1,
                -_frame_prompt_quality(graph.methods[static_id]),
                -sample.count,
                graph.methods[static_id].canonical_id,
                sample.context_id,
                path_index,
            )
            seeds.append((static_id, seed_rank, (sample, path_index)))
    return _multi_source_routes(graph, seeds)


def _public_entry_routes(graph: CallGraph, inventory_refs: list[MethodRef]) -> RouteMap:
    seeds: list[tuple[int, tuple, object]] = []
    seen: set[int] = set()
    for ref in inventory_refs:
        static_id: int | None = _resolve_graph_id(graph, ref)
        if static_id is None or static_id in seen:
            continue
        seen.add(static_id)
        seeds.append((static_id, (ref.canonical_id,), static_id))
    return _multi_source_routes(graph, seeds)


def _route_to(target_id: int, routes: RouteMap) -> tuple[list[int], list[dict]]:
    if target_id not in routes.distance:
        return [], []
    path: list[int] = [target_id]
    edges: list[dict] = []
    current: int = target_id
    while routes.previous.get(current) is not None:
        previous_edge = routes.previous[current]
        if previous_edge is None:
            break
        previous, edge = previous_edge
        path.append(previous)
        edges.append(edge)
        current = previous
    path.reverse()
    edges.reverse()
    return path, edges


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
        "isDirect": edge["is_direct"],
    }


def _record_to_json(record: NearCallRecord, graph: CallGraph, rank: int) -> dict:
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
        "reachingPath": (
            [_format_static_id(static_id, graph) for static_id in record.static_path]
            if graph_present else None
        ),
        "edges": (
            [_edge_to_json(edge, graph) for edge in record.static_path_edges]
            if graph_present else None
        ),
    }


def _observed_contexts(profile: SampledProfile, graph: CallGraph) -> list[dict]:
    contexts: list[dict] = []
    for sample in sorted(profile.samples, key=lambda item: (-item.count, item.context_id)):
        contexts.append({
            "id": sample.context_id,
            "sampleCount": sample.count,
            "rawContext": sample.raw_context,
            "fullPath": [ref.canonical_id for ref, _ in sample.full_path],
            "mappedPath": [_format_static_id(static_id, graph) for static_id, _ in sample.path],
        })
    return contexts


def _observed_methods(
        profile: SampledProfile,
        graph: CallGraph,
        jacoco_methods: dict[str, JacocoMethodCoverage],
) -> list[dict]:
    methods: list[dict] = []
    for static_id, count in profile.sample_counts.items():
        ref: MethodRef = graph.methods[static_id]
        coverage: JacocoMethodCoverage | None = jacoco_methods.get(ref.canonical_id)
        methods.append({
            "id": ref.canonical_id,
            "sampleCount": count,
            "jacocoStatus": coverage.status if coverage is not None else "unknown",
        })
    methods.sort(key=lambda item: (-item["sampleCount"], item["id"]))
    return methods


def _effective_target_state(
        method_id: str,
        target_states: dict[str, TargetState],
        attempt_counts: dict[str, int],
) -> TargetState:
    state = target_states.get(method_id, TargetState())
    return TargetState(
        status=state.status,
        attempt_count=max(state.attempt_count, attempt_counts.get(method_id, 0)),
        last_attempted_iteration=state.last_attempted_iteration,
        reason=state.reason,
    )


def correlate(
        profile: SampledProfile,
        graph: CallGraph,
        inventory: dict,
        jacoco_methods: dict[str, JacocoMethodCoverage],
        max_listed: int = MAX_LISTED_METHODS,
        attempt_counts: dict[str, int] | None = None,
        target_states: dict[str, TargetState] | None = None,
) -> tuple[dict, list[NearCallRecord]]:
    """Build exact public coverage and deep uncovered-method path records."""
    if max_listed <= 0:
        raise ProfileFormatError("max_listed must be positive.")
    attempts: dict[str, int] = attempt_counts or {}
    states: dict[str, TargetState] = target_states or {}
    inventory_refs: list[tuple[MethodRef, dict]] = []
    for target in inventory.get("targets", []):
        ref: MethodRef | None = parse_inventory_id(target.get("id", ""))
        if ref is not None:
            inventory_refs.append((ref, target))
    inventory_ids: set[str] = {ref.canonical_id for ref, _ in inventory_refs}

    inventory_report: list[dict] = []
    inventory_counts: dict[str, int] = {"covered": 0, "uncovered": 0, "unknown": 0}
    for ref, target in inventory_refs:
        coverage: JacocoMethodCoverage | None = jacoco_methods.get(ref.canonical_id)
        status: str = coverage.status if coverage is not None else "unknown"
        inventory_counts[status] += 1
        inventory_report.append({"id": ref.canonical_id, "kind": target.get("kind"), "status": status})

    graph_ids: set[str] = set(graph.key_to_id)
    jacoco_ids: set[str] = set(jacoco_methods)
    deep_ids: list[str] = sorted(jacoco_ids - inventory_ids)
    jacoco_only_ids: list[str] = sorted((jacoco_ids - graph_ids) - inventory_ids)
    graph_only_ids: list[str] = sorted((graph_ids - jacoco_ids) - inventory_ids)
    invalid_state_ids: list[str] = sorted(set(states) - set(deep_ids))
    if invalid_state_ids:
        raise ProfileFormatError(
            f"Target state id '{invalid_state_ids[0]}' is not in the current deep JaCoCo universe."
        )
    for method_id, state in states.items():
        if state.status == "completed" and not jacoco_methods[method_id].covered:
            raise ProfileFormatError(
                f"Target state '{method_id}' is completed but current JaCoCo reports it uncovered."
            )
    deep_coverage: list[JacocoMethodCoverage] = [jacoco_methods[method_id] for method_id in deep_ids]
    deep_uncovered: list[JacocoMethodCoverage] = [
        coverage for coverage in deep_coverage if not coverage.covered
    ]

    sampled_routes: RouteMap = _sample_routes(graph, profile)
    entry_routes: RouteMap = _public_entry_routes(graph, [ref for ref, _ in inventory_refs])
    uncovered_records: list[NearCallRecord] = [
        _build_record(
            coverage,
            graph,
            sampled_routes,
            entry_routes,
            _effective_target_state(
                coverage.method_ref.canonical_id,
                states,
                attempts,
            ),
        )
        for coverage in deep_uncovered
    ]
    uncovered_records.sort(key=_record_rank_key)
    mathematical_ranks: dict[str, int] = {
        record.target_ref.canonical_id: rank
        for rank, record in enumerate(uncovered_records, start=1)
    }

    effective_limit: int = min(max_listed, MAX_LISTED_METHODS)
    actionable_records: list[NearCallRecord] = [
        record
        for record in uncovered_records
        if record.join_kind != "none" and not record.target_state.terminal
    ]
    prompt_records: list[NearCallRecord] = sorted(
        actionable_records, key=_prompt_selection_key,
    )[:effective_limit]
    uncovered_json: list[dict] = [
        _record_to_json(record, graph, mathematical_ranks[record.target_ref.canonical_id])
        for record in uncovered_records
    ]
    prompt_json: list[dict] = [
        _record_to_json(record, graph, mathematical_ranks[record.target_ref.canonical_id])
        for record in prompt_records
    ]

    report: dict = {
        "summary": {
            "coverageSource": "jacoco",
            "inventoryTargets": len(inventory_report),
            "inventoryCovered": inventory_counts["covered"],
            "inventoryUncovered": inventory_counts["uncovered"],
            "inventoryUnknown": inventory_counts["unknown"],
            "jacocoMethods": len(jacoco_methods),
            "callGraphMethods": len(graph.key_to_id),
            "graphOnlyMethods": len(graph_ids - jacoco_ids),
            "jacocoOnlyMethods": len(jacoco_ids - graph_ids),
            "deepMethods": len(deep_coverage),
            "deepCovered": len(deep_coverage) - len(deep_uncovered),
            "deepUncovered": len(deep_uncovered),
            "terminalUncovered": sum(
                1 for record in uncovered_records if record.target_state.terminal
            ),
            "listedUncovered": len(prompt_records),
            "omittedUncovered": len(uncovered_records) - len(prompt_records),
            "sampledJoins": sum(1 for record in prompt_records if record.join_kind == "sampled"),
            "sampledObservedMethods": len(profile.sample_counts),
            "totalSampleCount": profile.total_sample_count,
            "samplingContexts": profile.context_count,
        },
        "inventory": inventory_report,
        "targetStates": [
            _target_state_to_json(method_id, _effective_target_state(method_id, states, attempts))
            for method_id in sorted(set(states) | set(attempts))
        ],
        "deepMethods": [
            _method_evidence(
                coverage,
                graph_status=("present" if coverage.method_ref.canonical_id in graph_ids
                              else "not-present"),
            )
            for coverage in deep_coverage
        ],
        "diagnosticMethods": [
            *[
                _method_evidence(jacoco_methods[method_id], graph_status="not-present")
                for method_id in jacoco_only_ids
            ],
            *[
                {
                    "id": method_id,
                    "status": "not-reported",
                    "graphStatus": "present",
                    "sourcePath": None,
                    "sourceLine": None,
                }
                for method_id in graph_only_ids
            ],
        ],
        "observed": _observed_contexts(profile, graph),
        "observedMethods": _observed_methods(profile, graph, jacoco_methods),
        "uncoveredPaths": uncovered_json,
        "promptTargetIds": [record.target_ref.canonical_id for record in prompt_records],
        "bulkTargets": prompt_json,
        "caveats": [
            "JaCoCo is the only coverage authority; sampled PGO evidence is guidance only.",
            "Absence of a sample never proves non-execution.",
            "The analysis call graph over-approximates; static paths may be infeasible.",
        ],
    }
    return report, prompt_records


def _format_static_id(static_id: int, graph: CallGraph) -> str:
    ref = graph.methods.get(static_id)
    return ref.canonical_id if ref is not None else f"#{static_id}"




def _simple_owner(owner: str) -> str:
    return owner.rsplit(".", 1)[-1].replace("$", ".")


def _display_method(ref: MethodRef, qualify_owner: bool) -> str:
    owner = _simple_owner(ref.owner)
    arguments = "..." if ref.params else ""
    if ref.name == "<init>":
        return f"{owner}({arguments})"
    method = f"{ref.name}({arguments})"
    return f"{owner}.{method}" if qualify_owner else method


def _display_path(static_path: list[int], graph: CallGraph, limit: int = 6) -> str:
    selected: list[int | None]
    if len(static_path) <= limit:
        selected = list(static_path)
    else:
        selected = [*static_path[:2], None, *static_path[-3:]]

    labels: list[str] = []
    previous_owner: str | None = None
    for static_id in selected:
        if static_id is None:
            labels.append("…")
            previous_owner = None
            continue
        ref = graph.methods.get(static_id)
        if ref is None:
            labels.append(f"method-{static_id}")
            previous_owner = None
            continue
        labels.append(_display_method(ref, ref.owner != previous_owner))
        previous_owner = ref.owner
    return " → ".join(labels)


def write_markdown(
        report: dict,
        prompt_records: list[NearCallRecord],
        graph: CallGraph,
        coordinate: str,
        iteration: int,
        progress: dict | None,
        md_path: str,
) -> None:
    summary: dict = report["summary"]
    lines: list[str] = [
        f"# Deep coverage paths {iteration} — {coordinate}",
        "",
        "JaCoCo is the coverage authority. Sampled PGO is guidance only.",
        "Attempt every listed uncovered path in this iteration through public API "
        "behavior; never invoke internal targets directly.",
        "",
        "## Summary",
        "",
        f"- Public inventory: {summary['inventoryCovered']} covered, "
        f"{summary['inventoryUncovered']} uncovered, {summary['inventoryUnknown']} unknown",
        f"- Deep methods: {summary['deepCovered']} covered, "
        f"{summary['deepUncovered']} uncovered",
        f"- Prompt list: {summary['listedUncovered']} "
        f"(omitted but retained in JSON: {summary['omittedUncovered']})",
        f"- Sampled contexts: {summary['samplingContexts']} "
        f"({summary['totalSampleCount']} samples)",
    ]

    if progress is not None:
        newly_covered: list[str] = progress["newlyCovered"]
        lines += ["", "## Progress", "", f"- Newly JaCoCo-covered deep methods: {len(newly_covered)}"]
        lines += [f"  - `{method_id}`" for method_id in newly_covered[:20]]


    sampled_groups: dict[tuple[str, int], list[NearCallRecord]] = {}
    sampled_group_order: list[tuple[str, int]] = []
    fallback_records: list[NearCallRecord] = []
    for record in prompt_records:
        if record.join_kind != "sampled" or record.sample is None or not record.static_path:
            fallback_records.append(record)
            continue
        group_key: tuple[str, int] = (record.sample.context_id, record.static_path[0])
        if group_key not in sampled_groups:
            sampled_groups[group_key] = []
            sampled_group_order.append(group_key)
        sampled_groups[group_key].append(record)

    lines += ["", "## Observed (sampled guidance only)", ""]
    if not sampled_group_order:
        lines.append("_No sampled context reaches an actionable uncovered path._")
    for group_key in sampled_group_order:
        records: list[NearCallRecord] = sampled_groups[group_key]
        representative: NearCallRecord = records[0]
        assert representative.sample is not None
        sample: Sample = representative.sample
        join_index: int = representative.sampled_join_path_index or 0
        observed_path: list[int] = [
            static_id
            for static_id, _ in sample.path[join_index:join_index + 2]
        ]
        lines.append("Observed:")
        lines.append(f"`{_display_path(observed_path, graph)}`")
        lines.append("")
        lines.append("Uncovered paths:")
        for record in records:
            lines.append(f"`{_display_path(record.static_path, graph)}`")
        lines.append("")

    fallback_groups: dict[int, list[NearCallRecord]] = {}
    fallback_order: list[int] = []
    for record in fallback_records:
        if not record.static_path:
            continue
        entry_id = record.static_path[0]
        if entry_id not in fallback_groups:
            fallback_groups[entry_id] = []
            fallback_order.append(entry_id)
        fallback_groups[entry_id].append(record)

    lines += ["", f"## Uncovered paths (JaCoCo-exact, top {MAX_LISTED_METHODS})", ""]
    if not fallback_order:
        lines.append("_All prompt paths are paired with sampled observations above._")
    for entry_id in fallback_order:
        lines.append("Public entry:")
        lines.append(f"`{_display_path([entry_id], graph)}`")
        lines.append("")
        lines.append("Uncovered paths:")
        for record in fallback_groups[entry_id]:
            lines.append(f"`{_display_path(record.static_path, graph)}`")
        lines.append("")
    if summary["omittedUncovered"]:
        lines.append(
            f"_{summary['omittedUncovered']} additional uncovered paths are retained in JSON._"
        )
        lines.append("")

    lines += ["## Caveats", ""]
    lines += [f"- {caveat}" for caveat in report["caveats"]]
    with open(md_path, "w", encoding="utf-8") as md_file:
        md_file.write("\n".join(lines) + "\n")


def write_lcov(
        profile: SampledProfile,
        graph: CallGraph,
        jacoco_methods: dict[str, JacocoMethodCoverage],
        lcov_path: str,
) -> None:
    """Emit only positive sampled observations as guidance-only LCOV."""
    by_method: dict[str, tuple[JacocoMethodCoverage, int]] = {}
    for static_id, count in profile.sample_counts.items():
        if count <= 0:
            continue
        ref: MethodRef = graph.methods[static_id]
        coverage: JacocoMethodCoverage | None = jacoco_methods.get(ref.canonical_id)
        if coverage is None:
            continue
        previous: tuple[JacocoMethodCoverage, int] | None = by_method.get(ref.canonical_id)
        total: int = count + (previous[1] if previous is not None else 0)
        by_method[ref.canonical_id] = (coverage, total)

    by_source: dict[str, list[tuple[MethodRef, int, int]]] = {}
    for coverage, count in by_method.values():
        ref: MethodRef = coverage.method_ref
        source_path: str = coverage.source_path or f"{ref.owner.replace('.', '/')}.java"
        source_line: int = coverage.source_line or 1
        by_source.setdefault(source_path, []).append((ref, source_line, count))

    lines: list[str] = ["TN:sampled-pgo-guidance"]
    for source_path in sorted(by_source):
        entries: list[tuple[MethodRef, int, int]] = sorted(
            by_source[source_path], key=lambda item: (item[1], item[0].canonical_id)
        )
        lines.append(f"SF:{source_path}")
        for ref, line, _ in entries:
            lines.append(f"FN:{line},{ref.canonical_id}")
        for ref, _, count in entries:
            lines.append(f"FNDA:{count},{ref.canonical_id}")
        lines.append(f"FNF:{len(entries)}")
        lines.append(f"FNH:{len(entries)}")
        lines.append("end_of_record")
    with open(lcov_path, "w", encoding="utf-8") as lcov_file:
        lcov_file.write("\n".join(lines) + "\n")


def _previous_report(output_dir: str, iteration: int) -> dict | None:
    previous_path: str = os.path.join(output_dir, f"discovery-report-{iteration - 1}.json")
    if iteration <= 0 or not os.path.isfile(previous_path):
        return None
    return _load_json_object(previous_path, "previous discovery report")


def _next_attempt_counts(previous: dict | None) -> dict[str, int]:
    if previous is None:
        return {}
    counts: dict[str, int] = {
        entry["id"]: int(entry.get("attemptCount", 0))
        for entry in previous.get("uncoveredPaths", [])
    }
    for method_id in previous.get("promptTargetIds", []):
        counts[method_id] = counts.get(method_id, 0) + 1
    return counts


def _previous_target_states(previous: dict | None) -> dict[str, TargetState]:
    if previous is None:
        return {}
    entries = previous.get("targetStates", [])
    if not isinstance(entries, list):
        raise ProfileFormatError("Previous discovery report has invalid targetStates.")
    states: dict[str, TargetState] = {}
    for index, entry in enumerate(entries, start=1):
        method_id, state = _parse_target_state(entry, "previous discovery report", index)
        if method_id in states:
            raise ProfileFormatError(
                f"Previous discovery report repeats target '{method_id}'."
            )
        states[method_id] = state
    return states


def _progress(previous: dict | None, report: dict) -> dict | None:
    if previous is None:
        return None
    previous_uncovered: set[str] = {
        entry["id"] for entry in previous.get("uncoveredPaths", [])
    }
    now_covered: set[str] = {
        entry["id"] for entry in report["deepMethods"] if entry["status"] == "covered"
    }
    return {"newlyCovered": sorted(previous_uncovered & now_covered)}


def generate_report(
        profile_path: str,
        reports_dir: str,
        api_inventory_path: str,
        jacoco_xml_paths: list[str],
        coordinate: str,
        iteration: int,
        output_dir: str,
        max_listed: int = MAX_LISTED_METHODS,
        target_state_paths: list[str] | None = None,
) -> dict:
    if not isinstance(coordinate, str) or not coordinate.strip():
        raise ProfileFormatError("coordinate must be non-empty.")
    if iteration < 0:
        raise ProfileFormatError("iteration must be non-negative.")
    if max_listed <= 0:
        raise ProfileFormatError("max_listed must be positive.")
    graph: CallGraph = load_call_graph(reports_dir)
    profile: SampledProfile = load_sampled_profile(profile_path, graph)
    inventory: dict = _load_json_object(api_inventory_path, "API inventory")
    _require_coordinate(inventory, coordinate, "API inventory")
    jacoco_methods: dict[str, JacocoMethodCoverage] = load_jacoco_method_coverage(
        jacoco_xml_paths
    )
    previous: dict | None = _previous_report(output_dir, iteration)
    target_states: dict[str, TargetState] = _previous_target_states(previous)
    target_states.update(load_target_states(target_state_paths, coordinate))
    report, prompt_records = correlate(
        profile,
        graph,
        inventory,
        jacoco_methods,
        max_listed,
        _next_attempt_counts(previous),
        target_states,
    )
    report["coordinate"] = coordinate
    report["iteration"] = iteration
    report["profileKind"] = "sampled-guidance"

    os.makedirs(output_dir, exist_ok=True)
    json_path: str = os.path.join(output_dir, f"discovery-report-{iteration}.json")
    md_path: str = os.path.join(output_dir, f"discovery-report-{iteration}.md")
    lcov_path: str = os.path.join(output_dir, f"coverage-{iteration}.lcov")
    with open(json_path, "w", encoding="utf-8") as json_file:
        json.dump(report, json_file, indent=2)
        json_file.write("\n")
    write_markdown(report, prompt_records, graph, coordinate, iteration, _progress(previous, report), md_path)
    write_lcov(profile, graph, jacoco_methods, lcov_path)
    return report


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Generate JaCoCo-exact deep paths with sampled-PGO guidance."
    )
    parser.add_argument("--profile", required=True, help="Sampled .iprof profile path.")
    parser.add_argument(
        "--reports-dir",
        required=True,
        help="Directory containing the call_tree_*.csv analysis dump.",
    )
    parser.add_argument("--api-inventory", required=True, help="api-inventory.json path.")
    parser.add_argument(
        "--jacoco-xml",
        action="append",
        required=True,
        dest="jacoco_xml_paths",
        help="Required JaCoCo XML method evidence; repeat for multiple reports.",
    )
    parser.add_argument(
        "--target-state",
        action="append",
        default=[],
        dest="target_state_paths",
        help="Coordinate-scoped target state; repeat in chronological order.",
    )
    parser.add_argument("--coordinate", required=True, help="group:artifact:version.")
    parser.add_argument("--iteration", type=int, default=1, help="Discovery iteration number.")
    parser.add_argument("--output-dir", required=True, help="Directory for discovery artifacts.")
    parser.add_argument(
        "--max-listed-methods",
        type=int,
        default=MAX_LISTED_METHODS,
        help="Prompt list size; values above 100 are clamped to 100.",
    )
    args = parser.parse_args()

    try:
        report: dict = generate_report(
            profile_path=args.profile,
            reports_dir=args.reports_dir,
            api_inventory_path=args.api_inventory,
            jacoco_xml_paths=args.jacoco_xml_paths,
            coordinate=args.coordinate,
            iteration=args.iteration,
            output_dir=args.output_dir,
            max_listed=args.max_listed_methods,
            target_state_paths=args.target_state_paths,
        )
    except (ProfileFormatError, JacocoReportError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(2) from error

    summary: dict = report["summary"]
    print(
        "Deep coverage report {iteration}: {covered}/{methods} deep methods covered; "
        "{listed} uncovered paths listed ({omitted} retained only in JSON).".format(
            iteration=args.iteration,
            covered=summary["deepCovered"],
            methods=summary["deepMethods"],
            listed=summary["listedUncovered"],
            omitted=summary["omittedUncovered"],
        )
    )


if __name__ == "__main__":
    main()
