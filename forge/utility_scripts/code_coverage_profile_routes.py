# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Sampled-profile parsing and route computation for the deep-method report.

Maps sampled `.iprof` stacks onto static call-graph ids and computes
deterministic shortest semantic routes from sampled frames and public entries
(§AR-code-coverage-improvement.3).
"""

from __future__ import annotations

import heapq
from dataclasses import dataclass, field

from utility_scripts.code_coverage_jacoco import JacocoMethodCoverage
from utility_scripts.code_coverage_model import MethodRef, method_ref_from_iprof
from utility_scripts.code_coverage_profile_graph import (
    CallGraph,
    _format_static_id,
    _resolve_graph_id,
    _translated_ref,
)
from utility_scripts.code_coverage_profile_inputs import (
    ProfileFormatError,
    _load_json_object,
)

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
    """Compute deterministic shortest semantic paths from ranked source methods."""
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
        current_ref_id: str = _translated_ref(current, graph).canonical_id
        for edge in graph.adjacency.get(current, []):
            # A functional-interface call site names no callee of its own, so
            # routing through it invents a reachability claim
            # (§AR-code-coverage-improvement.4.2.1).
            if edge["kind"] == "dispatch":
                continue
            callee: int = edge["callee"]
            semantic_step: int = int(
                current_ref_id
                != _translated_ref(callee, graph).canonical_id
            )
            candidate_distance: int = distance + semantic_step
            candidate_key = (candidate_distance, seed_rank)
            if callee in best_keys and best_keys[callee] <= candidate_key:
                continue
            best_keys[callee] = candidate_key
            routes.distance[callee] = candidate_distance
            routes.previous[callee] = (current, edge)
            routes.payload[callee] = routes.payload[current]
            heapq.heappush(
                queue,
                (candidate_distance, seed_rank, graph.methods[callee].canonical_id, callee),
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
