# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""
Sampled-PGO near-call report for the code coverage improvement workflow
(§WF-code-coverage-improvement, §WF-code-coverage-improvement-architecture).

This is the phase-7 discovery analyzer. JaCoCo remains the only coverage
metric; the sampled GraalVM PGO profile (`.iprof`, `--pgo-sampling`) is
guidance-only evidence. For every uncovered public API inventory target the
analyzer computes a near-call record: the shortest static call-graph path from
a sampled stack frame (or, when no sampled frame joins, from the nearest public
API entry) to the target. Targets are ranked nearest-first, grouped by owning
class, and listed in a bulk prompt-ready report capped at
`MAX_LISTED_METHODS` (100) methods.

Inputs:
- `call_tree_{methods,invokes,targets}_*.csv` — the analysis call-tree CSV dump
  (`-H:+PrintAnalysisCallTree -H:PrintAnalysisCallTreeType=CSV`).
- a sampled `.iprof` — profile `<`-chain contexts are leaf-first
  (`callee:bci<caller:bci`), so sampled stacks read right-to-left from the root.
- `api-inventory.json` — the public user-callable target universe.
- optionally the latest `api-cover-report-*.json` (phase 5, JaCoCo): its
  `covered` statuses decide which targets are uncovered. Without it, sampled
  observation is used as a fallback covered-signal, still guidance-only.

Usage:
  python3 utility_scripts/code_coverage_profile_report.py \
    --profile <native-test.iprof> \
    --reports-dir <build/.../reports> \
    --api-inventory <api-inventory.json> \
    [--api-cover-report <api-cover-report-N.json>] \
    --coordinate group:artifact:version \
    --iteration 1 \
    --output-dir runtime/code-coverage/discovery
"""

from __future__ import annotations

import argparse
import csv
from collections import deque
from dataclasses import dataclass, field
import json
import os
import sys

from utility_scripts.code_coverage_model import (
    MethodRef,
    method_ref_from_call_tree_row,
    method_ref_from_iprof,
    parse_inventory_id,
)

MAX_LISTED_METHODS = 100
DETAILED_GUIDANCE_COUNT = 10

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
    """Raised when the `.iprof` was not collected with `--pgo-sampling`."""


@dataclass
class CallGraph:
    methods: dict[int, MethodRef] = field(default_factory=dict)
    key_to_id: dict[str, int] = field(default_factory=dict)
    loose_to_id: dict[str, int] = field(default_factory=dict)
    adjacency: dict[int, list[dict]] = field(default_factory=dict)
    reverse_adjacency: dict[int, list[dict]] = field(default_factory=dict)


@dataclass
class Sample:
    """One sampled stack, root-first, mapped onto static call-graph ids."""

    path: list[tuple[int, int]]
    full_path: list[tuple[MethodRef, int]]
    path_full_indexes: list[int]
    count: int


@dataclass
class SampledProfile:
    samples: list[Sample] = field(default_factory=list)
    observed_static_ids: set[int] = field(default_factory=set)
    total_sample_count: int = 0
    context_count: int = 0


@dataclass
class NearCallRecord:
    """Guidance for one uncovered inventory target."""

    target_ref: MethodRef
    target_id: int
    # "sampled": path starts at a sampled join frame; "entry": no sampled
    # join, path starts at the nearest public API entry; "none": no route.
    join_kind: str
    static_path: list[int]
    static_path_edges: list[dict]
    sample: Sample | None
    sampled_join_path_index: int | None

    @property
    def depth_remaining(self) -> int:
        return max(0, len(self.static_path) - 1)

    @property
    def sample_count(self) -> int:
        return self.sample.count if self.sample is not None else 0


def _find_file(directory: str, file_name: str) -> str:
    """Find `file_name` or a `prefix_*.suffix` sibling under `directory`."""
    exact = os.path.join(directory, file_name)
    if os.path.isfile(exact):
        return exact
    prefix, extension = os.path.splitext(file_name)
    for current_dir, _, file_names in os.walk(directory):
        for candidate in sorted(file_names):
            if candidate == file_name or (candidate.startswith(prefix + "_") and candidate.endswith(extension)):
                return os.path.join(current_dir, candidate)
    raise FileNotFoundError(os.path.join(directory, file_name))


def _read_csv_by_id(path: str) -> dict[int, dict]:
    with open(path, encoding="utf-8", newline="") as csv_file:
        return {int(row["Id"]): row for row in csv.DictReader(csv_file)}


def load_call_graph(reports_dir: str) -> CallGraph:
    """Load the analysis call-tree CSV dump into an id-indexed call graph."""
    methods_rows = _read_csv_by_id(_find_file(reports_dir, "call_tree_methods.csv"))
    invokes_rows = _read_csv_by_id(_find_file(reports_dir, "call_tree_invokes.csv"))
    with open(_find_file(reports_dir, "call_tree_targets.csv"), encoding="utf-8", newline="") as csv_file:
        target_rows = list(csv.DictReader(csv_file))

    graph = CallGraph()
    for method_id, row in methods_rows.items():
        ref = method_ref_from_call_tree_row(row)
        if ref is None:
            continue
        graph.methods[method_id] = ref
        graph.key_to_id.setdefault(ref.canonical_id, method_id)
        graph.loose_to_id.setdefault(ref.loose_key, method_id)

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
    return graph


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


def load_sampled_profile(profile_path: str, graph: CallGraph) -> SampledProfile:
    """Parse a sampled `.iprof` and map its stacks onto static call-graph ids."""
    with open(profile_path, "r", encoding="utf-8") as profile_file:
        document = json.load(profile_file)

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

    def static_id_for(ref: MethodRef) -> int | None:
        static_id = graph.key_to_id.get(ref.canonical_id)
        if static_id is None:
            static_id = graph.loose_to_id.get(ref.loose_key)
        return static_id

    profile = SampledProfile()
    for sampling in document.get("samplingProfiles") or []:
        records = sampling.get("records") or [0]
        count = int(records[0])
        profile.total_sample_count += sum(int(record) for record in records)
        profile.context_count += 1
        full_path: list[tuple[MethodRef, int]] = []
        path: list[tuple[int, int]] = []
        path_full_indexes: list[int] = []
        for method_id, bci in _parse_ctx(sampling.get("ctx", "")):
            ref = iprof_refs.get(method_id)
            if ref is None:
                continue
            full_path.append((ref, bci))
            static_id = static_id_for(ref)
            if static_id is not None and (not path or path[-1][0] != static_id):
                path.append((static_id, bci))
                path_full_indexes.append(len(full_path) - 1)
                profile.observed_static_ids.add(static_id)
        if path:
            profile.samples.append(Sample(
                path=path,
                full_path=full_path,
                path_full_indexes=path_full_indexes,
                count=count,
            ))
    return profile


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


def _next_edges_to_target(graph: CallGraph, target_id: int) -> dict[int, dict | None]:
    """Backward BFS: for each predecessor, the next edge toward the target."""
    next_edges: dict[int, dict | None] = {target_id: None}
    queue = deque([target_id])
    while queue:
        method_id = queue.popleft()
        for edge in graph.reverse_adjacency.get(method_id, ()):
            predecessor = edge["caller"]
            if predecessor in next_edges:
                continue
            next_edges[predecessor] = edge
            queue.append(predecessor)
    return next_edges


def _path_from_join(join_id: int, next_edges: dict[int, dict | None]) -> tuple[list[int], list[dict]]:
    path = [join_id]
    path_edges: list[dict] = []
    current = join_id
    while True:
        edge = next_edges.get(current)
        if edge is None:
            break
        path_edges.append(edge)
        current = edge["callee"]
        path.append(current)
    return path, path_edges


def _choose_best_sampled_join(
        graph: CallGraph,
        next_edges: dict[int, dict | None],
        samples: list[Sample],
) -> tuple[Sample, int, list[int], list[dict]] | None:
    """Pick the sampled frame closest to the target across all stacks."""
    best: tuple | None = None
    best_result: tuple[Sample, int, list[int], list[dict]] | None = None
    for sample in samples:
        for path_index, (static_id, _) in enumerate(sample.path):
            if static_id not in next_edges:
                continue
            static_path, static_path_edges = _path_from_join(static_id, next_edges)
            full_index = sample.path_full_indexes[path_index]
            test_index = _existing_test_frame_index(sample.full_path)
            join_after_test = test_index is None or full_index >= test_index
            score = (
                join_after_test,
                _frame_prompt_quality(graph.methods[static_id]),
                -len(static_path_edges),
                sample.count,
                path_index,
            )
            if best is None or score > best:
                best = score
                best_result = (sample, path_index, static_path, static_path_edges)
    return best_result


def _nearest_entry_path(
        next_edges: dict[int, dict | None],
        target_id: int,
        entry_static_ids: set[int],
) -> tuple[list[int], list[dict]] | None:
    """Shortest path from a public API entry to the target, if one exists."""
    best: tuple[int, list[int], list[dict]] | None = None
    for entry_id in entry_static_ids:
        if entry_id == target_id or entry_id not in next_edges:
            continue
        path, path_edges = _path_from_join(entry_id, next_edges)
        if best is None or len(path_edges) < best[0]:
            best = (len(path_edges), path, path_edges)
    if best is None:
        return None
    return best[1], best[2]


def build_near_call_record(
        target_ref: MethodRef,
        target_id: int,
        graph: CallGraph,
        profile: SampledProfile,
        entry_static_ids: set[int],
) -> NearCallRecord:
    next_edges = _next_edges_to_target(graph, target_id)

    join = _choose_best_sampled_join(graph, next_edges, profile.samples)
    if join is not None:
        sample, path_index, static_path, static_path_edges = join
        return NearCallRecord(
            target_ref=target_ref,
            target_id=target_id,
            join_kind="sampled",
            static_path=static_path,
            static_path_edges=static_path_edges,
            sample=sample,
            sampled_join_path_index=path_index,
        )

    entry_path = _nearest_entry_path(next_edges, target_id, entry_static_ids)
    if entry_path is not None:
        static_path, static_path_edges = entry_path
        return NearCallRecord(
            target_ref=target_ref,
            target_id=target_id,
            join_kind="entry",
            static_path=static_path,
            static_path_edges=static_path_edges,
            sample=None,
            sampled_join_path_index=None,
        )

    return NearCallRecord(
        target_ref=target_ref,
        target_id=target_id,
        join_kind="none",
        static_path=[target_id],
        static_path_edges=[],
        sample=None,
        sampled_join_path_index=None,
    )


def _record_rank_key(record: NearCallRecord) -> tuple:
    join_order = {"sampled": 0, "entry": 1, "none": 2}[record.join_kind]
    return (join_order, record.depth_remaining, -record.sample_count, record.target_ref.canonical_id)


def _covered_ids_from_cover_report(cover_report: dict | None) -> tuple[set[str], set[str]] | None:
    """Exact JaCoCo covered ids (canonical, loose) from an api-cover-report."""
    if cover_report is None:
        return None
    covered: set[str] = set()
    covered_loose: set[str] = set()
    for target in cover_report.get("targets", []):
        if target.get("status") != "covered":
            continue
        ref = parse_inventory_id(target.get("id", ""))
        if ref is None:
            continue
        covered.add(ref.canonical_id)
        covered_loose.add(ref.loose_key)
    return covered, covered_loose


def correlate(
        profile: SampledProfile,
        graph: CallGraph,
        inventory: dict,
        cover_report: dict | None,
        max_listed: int,
) -> tuple[dict, list[NearCallRecord]]:
    """Rank uncovered inventory targets by near-call distance."""
    inventory_refs: list[tuple[MethodRef, dict]] = []
    for target in inventory.get("targets", []):
        ref = parse_inventory_id(target.get("id", ""))
        if ref is not None:
            inventory_refs.append((ref, target))

    def static_id_for(ref: MethodRef) -> int | None:
        static_id = graph.key_to_id.get(ref.canonical_id)
        if static_id is None:
            static_id = graph.loose_to_id.get(ref.loose_key)
        return static_id

    entry_static_ids = {sid for sid in (static_id_for(ref) for ref, _ in inventory_refs) if sid is not None}

    jacoco_covered = _covered_ids_from_cover_report(cover_report)

    def is_covered(ref: MethodRef, static_id: int | None) -> bool:
        if jacoco_covered is not None:
            covered, covered_loose = jacoco_covered
            return ref.canonical_id in covered or ref.loose_key in covered_loose
        return static_id is not None and static_id in profile.observed_static_ids

    inventory_report: list[dict] = []
    counts = {"covered": 0, "uncovered": 0, "excluded": 0}
    uncovered_records: list[NearCallRecord] = []
    for ref, target in inventory_refs:
        static_id = static_id_for(ref)
        entry = {"id": ref.canonical_id, "kind": target.get("kind")}
        if is_covered(ref, static_id):
            entry["status"] = "covered"
            counts["covered"] += 1
        elif static_id is None:
            entry["status"] = "excluded"
            counts["excluded"] += 1
        else:
            entry["status"] = "uncovered"
            counts["uncovered"] += 1
            uncovered_records.append(build_near_call_record(ref, static_id, graph, profile, entry_static_ids))
        inventory_report.append(entry)

    uncovered_records.sort(key=_record_rank_key)
    listed_records = uncovered_records[:max_listed]

    report = {
        "summary": {
            "coverageSource": "jacoco" if jacoco_covered is not None else "sampled-fallback",
            "inventoryTargets": len(inventory_report),
            "covered": counts["covered"],
            "uncovered": counts["uncovered"],
            "excluded": counts["excluded"],
            "listedUncovered": len(listed_records),
            "omittedUncovered": len(uncovered_records) - len(listed_records),
            "sampledJoins": sum(1 for record in listed_records if record.join_kind == "sampled"),
            "totalSampleCount": profile.total_sample_count,
            "samplingContexts": profile.context_count,
        },
        "inventory": inventory_report,
        "bulkTargets": [_record_to_json(record, graph) for record in listed_records],
        "caveats": [
            "Sampled profiles are guidance only: absence of samples never proves "
            "non-execution; JaCoCo is the coverage metric.",
            "The analysis call graph over-approximates; statically-reachable-but-"
            "infeasible targets are legitimate skipped/exhausted outcomes.",
            "A reaching path is necessary, not sufficient: edges may sit behind "
            "guards that still require satisfying inputs.",
        ],
    }
    return report, listed_records


def _format_static_id(static_id: int, graph: CallGraph) -> str:
    ref = graph.methods.get(static_id)
    return ref.canonical_id if ref is not None else f"#{static_id}"


def _record_to_json(record: NearCallRecord, graph: CallGraph) -> dict:
    return {
        "id": record.target_ref.canonical_id,
        "owner": record.target_ref.owner,
        "ownerClass": record.target_ref.owner_class_simple,
        "joinKind": record.join_kind,
        "stepsRemaining": record.depth_remaining,
        "sampleCount": record.sample_count,
        "reachingPath": [_format_static_id(static_id, graph) for static_id in record.static_path],
    }


def _one_liner(record: NearCallRecord, graph: CallGraph) -> str:
    target = record.target_ref.canonical_id
    if record.join_kind == "sampled":
        join = _format_static_id(record.static_path[0], graph)
        via = _format_static_id(record.static_path[1], graph) if len(record.static_path) > 1 else target
        return (f"- `{target}` — {record.depth_remaining} step(s) from sampled `{join}` "
                f"(samples {record.sample_count}); via `{via}`")
    if record.join_kind == "entry":
        entry = _format_static_id(record.static_path[0], graph)
        return f"- `{target}` — no sampled join; {record.depth_remaining} step(s) from public entry `{entry}`"
    return f"- `{target}` — no public route in the call graph (candidate skip/exhaust)"


def _format_sampled_stack(record: NearCallRecord) -> list[str]:
    """Render the sampled stack from the test entry frame to the join frame."""
    if record.sample is None or record.sampled_join_path_index is None:
        return ["- No sampled stack joins the reaching path."]
    join_full_index = record.sample.path_full_indexes[record.sampled_join_path_index]
    test_index = _existing_test_frame_index(record.sample.full_path)
    start = test_index if test_index is not None and test_index <= join_full_index else 0
    lines = []
    if test_index is None:
        lines.append("- No existing test frame was identified; showing the sampled prefix.")
    for index in range(start, join_full_index + 1):
        ref, bci = record.sample.full_path[index]
        markers = []
        if index == test_index:
            markers.append("existing test entry point")
        if index == join_full_index:
            markers.append("sampled/call-graph join point")
        marker_suffix = f"  <-- {', '.join(markers)}" if markers else ""
        lines.append(f"    at {ref.canonical_id} @sample-bci={bci}{marker_suffix}")
    return lines


def _observed_branch_after_join(record: NearCallRecord) -> MethodRef | None:
    if record.sample is None or record.sampled_join_path_index is None:
        return None
    next_index = record.sampled_join_path_index + 1
    if next_index >= len(record.sample.path_full_indexes):
        return None
    return record.sample.full_path[record.sample.path_full_indexes[next_index]][0]


def _detailed_block(record: NearCallRecord, graph: CallGraph) -> list[str]:
    target = record.target_ref.canonical_id
    lines = [f"### `{target}`", ""]
    if record.join_kind == "sampled":
        lines.append("Closest sampled stack from an existing test to the join point:")
        lines.extend(_format_sampled_stack(record))
        lines.append("")
        lines.append("Static path from the sampled join to the target:")
    elif record.join_kind == "entry":
        lines.append("No sampled stack joins this target's reaching paths.")
        lines.append("")
        lines.append("Static path from the nearest public API entry to the target:")
    else:
        lines.append("No public route was found in the call graph; treat as a skip/exhaust candidate.")
        return lines
    for index, static_id in enumerate(record.static_path):
        bci_suffix = ""
        if 0 < index <= len(record.static_path_edges):
            bci_suffix = f" @invoke-bci={record.static_path_edges[index - 1]['bci']}"
        lines.append(f"{index + 1}. {_format_static_id(static_id, graph)}{bci_suffix}")

    lines.extend(["", "Divergence:"])
    if record.join_kind == "sampled":
        lines.append(f"- Sampled execution reached: {_format_static_id(record.static_path[0], graph)}")
        observed = _observed_branch_after_join(record)
        if observed is not None:
            lines.append(f"- Sampled execution then goes to: {observed.canonical_id}")
        else:
            lines.append("- The sampled stack ends at the join point.")
    if len(record.static_path) > 1:
        lines.append(f"- To reach the target, tests need to drive: "
                     f"{_format_static_id(record.static_path[1], graph)}")
    lines.append(f"- Static steps still missing: {record.depth_remaining}")
    return lines


def write_markdown(
        report: dict,
        listed_records: list[NearCallRecord],
        graph: CallGraph,
        coordinate: str,
        iteration: int,
        progress: dict | None,
        detailed_count: int,
        md_path: str,
) -> None:
    summary = report["summary"]
    lines = [
        f"# API coverage near-call report {iteration} — {coordinate}",
        "",
        "## Summary",
        "",
        f"- Coverage source: {summary['coverageSource']}",
        f"- Inventory targets: {summary['inventoryTargets']} "
        f"(covered {summary['covered']}, uncovered {summary['uncovered']}, "
        f"excluded {summary['excluded']})",
        f"- Listed uncovered methods: {summary['listedUncovered']} "
        f"(omitted beyond cap: {summary['omittedUncovered']})",
        f"- Sampled joins among listed: {summary['sampledJoins']}",
        f"- Sampling contexts: {summary['samplingContexts']} "
        f"(total samples {summary['totalSampleCount']})",
    ]

    if progress is not None:
        lines += ["", "## Progress since the previous report", ""]
        newly_covered = progress["newlyCovered"]
        if newly_covered:
            lines.append(f"- Newly covered ({len(newly_covered)}):")
            lines += [f"  - `{target_id}`" for target_id in newly_covered[:20]]
            if len(newly_covered) > 20:
                lines.append(f"  - ... {len(newly_covered) - 20} more")
        else:
            lines.append("- No inventory target changed from uncovered to covered.")
        lines.append(f"- Uncovered targets: {progress['previousUncovered']} -> {summary['uncovered']}")

    lines += ["", f"## Uncovered public API methods (grouped by class, nearest-first, max {MAX_LISTED_METHODS})", ""]
    if not listed_records:
        lines.append("_None — every inventory target is covered or excluded._")
    grouped: dict[str, list[NearCallRecord]] = {}
    group_order: list[str] = []
    for record in listed_records:
        group = record.target_ref.owner_class_simple
        if group not in grouped:
            grouped[group] = []
            group_order.append(group)
        grouped[group].append(record)
    for group in group_order:
        records = grouped[group]
        lines.append(f"### {records[0].target_ref.owner} — {len(records)} uncovered")
        lines += [_one_liner(record, graph) for record in records]
        lines.append("")
    if report["summary"]["omittedUncovered"]:
        lines.append(f"_{report['summary']['omittedUncovered']} more uncovered methods beyond the "
                     f"{MAX_LISTED_METHODS}-method cap; see the JSON report for the full inventory._")
        lines.append("")

    detailed = [record for record in listed_records if record.join_kind != "none"][:detailed_count]
    if detailed:
        lines += [f"## Detailed near-call guidance (top {len(detailed)})", ""]
        for record in detailed:
            lines.extend(_detailed_block(record, graph))
            lines.append("")

    lines += ["## Caveats", ""]
    lines += [f"- {caveat}" for caveat in report["caveats"]]
    with open(md_path, "w", encoding="utf-8") as md_file:
        md_file.write("\n".join(lines) + "\n")


def write_lcov(report: dict, inventory: dict, lcov_path: str) -> None:
    """Emit function-granularity LCOV for the inventory targets (guidance-only)."""
    status_by_id = {entry["id"]: entry["status"] for entry in report["inventory"]}
    by_source: dict[str, list[tuple[MethodRef, int]]] = {}
    for target in inventory.get("targets", []):
        ref = parse_inventory_id(target.get("id", ""))
        if ref is None:
            continue
        source_path = target.get("sourcePath") or f"{ref.owner.replace('.', '/')}.java"
        line = int(target.get("sourceLine", 1) or 1)
        by_source.setdefault(source_path, []).append((ref, line))

    lines: list[str] = ["TN:code-coverage-near-call"]
    for source_path in sorted(by_source):
        lines.append(f"SF:{source_path}")
        entries = sorted(by_source[source_path], key=lambda item: item[1])
        hit = 0
        for ref, line in entries:
            lines.append(f"FN:{line},{ref.name}")
        for ref, line in entries:
            covered = status_by_id.get(ref.canonical_id) == "covered"
            if covered:
                hit += 1
            lines.append(f"FNDA:{1 if covered else 0},{ref.name}")
        lines.append(f"FNF:{len(entries)}")
        lines.append(f"FNH:{hit}")
        lines.append("end_of_record")
    with open(lcov_path, "w", encoding="utf-8") as lcov_file:
        lcov_file.write("\n".join(lines) + "\n")


def _load_progress(output_dir: str, iteration: int, report: dict) -> dict | None:
    previous_path = os.path.join(output_dir, f"discovery-report-{iteration - 1}.json")
    if iteration <= 1 or not os.path.isfile(previous_path):
        return None
    with open(previous_path, "r", encoding="utf-8") as previous_file:
        previous = json.load(previous_file)
    previous_uncovered = {entry["id"] for entry in previous.get("inventory", [])
                          if entry.get("status") == "uncovered"}
    now_covered = {entry["id"] for entry in report["inventory"] if entry["status"] == "covered"}
    return {
        "newlyCovered": sorted(previous_uncovered & now_covered),
        "previousUncovered": len(previous_uncovered),
    }


def generate_report(
        profile_path: str,
        reports_dir: str,
        api_inventory_path: str,
        api_cover_report_path: str | None,
        coordinate: str,
        iteration: int,
        output_dir: str,
        max_listed: int = MAX_LISTED_METHODS,
        detailed_count: int = DETAILED_GUIDANCE_COUNT,
) -> dict:
    graph = load_call_graph(reports_dir)
    profile = load_sampled_profile(profile_path, graph)
    with open(api_inventory_path, "r", encoding="utf-8") as inventory_file:
        inventory = json.load(inventory_file)
    cover_report = None
    if api_cover_report_path:
        with open(api_cover_report_path, "r", encoding="utf-8") as cover_file:
            cover_report = json.load(cover_file)

    report, listed_records = correlate(profile, graph, inventory, cover_report, max_listed)
    report["coordinate"] = coordinate
    report["iteration"] = iteration
    report["profileKind"] = "sampled"

    os.makedirs(output_dir, exist_ok=True)
    progress = _load_progress(output_dir, iteration, report)
    json_path = os.path.join(output_dir, f"discovery-report-{iteration}.json")
    md_path = os.path.join(output_dir, f"discovery-report-{iteration}.md")
    lcov_path = os.path.join(output_dir, f"coverage-{iteration}.lcov")
    with open(json_path, "w", encoding="utf-8") as json_file:
        json.dump(report, json_file, indent=2)
        json_file.write("\n")
    write_markdown(report, listed_records, graph, coordinate, iteration, progress, detailed_count, md_path)
    write_lcov(report, inventory, lcov_path)
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate a sampled-PGO near-call report.")
    parser.add_argument("--profile", required=True, help="Sampled .iprof profile path.")
    parser.add_argument("--reports-dir", required=True,
                        help="Directory containing the call_tree_*.csv analysis dump.")
    parser.add_argument("--api-inventory", required=True, help="api-inventory.json path.")
    parser.add_argument("--api-cover-report", default=None,
                        help="Latest api-cover-report-N.json (JaCoCo covered statuses).")
    parser.add_argument("--coordinate", required=True, help="group:artifact:version.")
    parser.add_argument("--iteration", type=int, default=1, help="Discovery iteration number.")
    parser.add_argument("--output-dir", required=True, help="Directory for discovery artifacts.")
    parser.add_argument("--max-listed-methods", type=int, default=MAX_LISTED_METHODS,
                        help="Cap for the bulk uncovered-method list.")
    parser.add_argument("--detailed-guidance-count", type=int, default=DETAILED_GUIDANCE_COUNT,
                        help="How many top targets get detailed near-call guidance.")
    args = parser.parse_args()

    try:
        report = generate_report(
            profile_path=args.profile,
            reports_dir=args.reports_dir,
            api_inventory_path=args.api_inventory,
            api_cover_report_path=args.api_cover_report,
            coordinate=args.coordinate,
            iteration=args.iteration,
            output_dir=args.output_dir,
            max_listed=args.max_listed_methods,
            detailed_count=args.detailed_guidance_count,
        )
    except ProfileFormatError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(2)

    summary = report["summary"]
    print(
        "Near-call report {iteration}: {covered}/{targets} inventory targets covered, "
        "{listed} uncovered listed ({omitted} omitted), {joins} sampled joins.".format(
            iteration=args.iteration,
            covered=summary["covered"],
            targets=summary["inventoryTargets"],
            listed=summary["listedUncovered"],
            omitted=summary["omittedUncovered"],
            joins=summary["sampledJoins"],
        )
    )


if __name__ == "__main__":
    main()
