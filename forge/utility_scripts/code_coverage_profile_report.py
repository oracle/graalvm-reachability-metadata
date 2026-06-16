# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""
Native Image PGO coverage correlation report for the code coverage improvement
workflow (§WF-code-coverage-improvement, §WF-code-coverage-improvement-architecture).

This is the phase-6 discovery analyzer. It treats the instrumented GraalVM PGO
profile (`.iprof`) as positive-only evidence, joins it against the analysis call
graph (the reachable-method denominator), and emits prompt-ready reports plus
LCOV. Soundness rules enforced here:

1. Instrumented profiles only; sampled profiles are rejected, because the
   `count == 0 => not executed` invariant only holds for `--pgo-instrument`.
2. Executed methods and observed call edges are derived from the profile
   `<`-chain *contexts*, not from the `methods` symbol table, which lists every
   referenced method regardless of execution.
3. Reachable-but-unobserved targets are computed as call-graph methods minus
   profile-observed methods, and each is annotated with a reaching path obtained
   by walking the call graph backward to the nearest public-API inventory entry.

Profile context chains are ordered leaf-first: in `A:bciA<B:bciB`, `B` calls `A`
at `bciB` (verified against the shipped `jdk_profile.iprof`, where leaf methods
such as `String.length` dominate the left end and roots such as `<clinit>`
dominate the right end). Observed edges are therefore `right -> left`.

Usage:
  python3 utility_scripts/code_coverage_profile_report.py \
    --profile <default.iprof> \
    --call-tree <reports/call_tree_*.txt> \
    --api-inventory <api-inventory.json> \
    --coordinate group:artifact:version \
    --iteration 1 \
    --output-dir runtime/code-coverage/discovery \
    [--used-methods <reports/used_methods_*.txt>]
"""

from __future__ import annotations

import argparse
from collections import defaultdict, deque
from dataclasses import dataclass, field
import json
import os
import sys

from utility_scripts.code_coverage_model import (
    MethodRef,
    method_ref_from_iprof,
    parse_fqn_method_label,
    parse_inventory_id,
)

_MAX_REACHING_PATH_DEPTH = 64


class SampledProfileError(RuntimeError):
    """Raised when a profile is not instrumented and cannot be read as coverage."""


@dataclass
class ProfileData:
    executed: dict[str, MethodRef] = field(default_factory=dict)
    executed_loose: set[str] = field(default_factory=set)
    exec_counts: dict[str, int] = field(default_factory=dict)
    observed_edges: set[tuple[str, str]] = field(default_factory=set)


@dataclass
class CallGraph:
    reachable: dict[str, MethodRef] = field(default_factory=dict)
    reachable_loose: set[str] = field(default_factory=set)
    # callee canonical id -> set of caller canonical ids (reverse adjacency)
    callers_of: dict[str, set[str]] = field(default_factory=lambda: defaultdict(set))


def _parse_ctx(ctx: str) -> list[int]:
    """Return method ids from a profile context chain, leaf-first."""
    ids: list[int] = []
    for token in ctx.split("<"):
        method_part = token.split(":", 1)[0]
        try:
            ids.append(int(method_part))
        except ValueError:
            continue
    return ids


def load_profile(profile_path: str) -> ProfileData:
    """Parse an instrumented `.iprof`, rejecting sampled profiles."""
    with open(profile_path, "r", encoding="utf-8") as profile_file:
        document = json.load(profile_file)

    call_count = document.get("callCountProfiles") or []
    conditional = document.get("conditionalProfiles") or []
    virtual = document.get("virtualInvokeProfiles") or []
    sampling = document.get("samplingProfiles") or []

    if sampling and not call_count:
        raise SampledProfileError(
            "Profile contains only samplingProfiles; sampled profiles break the "
            "count==0 => not-executed invariant and must not be read as coverage."
        )
    if not call_count and not conditional and not virtual:
        raise SampledProfileError(
            "Profile has no instrumented context records (callCount/conditional/"
            "virtualInvoke). Re-collect with --pgo-instrument."
        )

    type_names = {entry["id"]: entry["name"] for entry in document.get("types", [])}
    method_refs: dict[int, MethodRef] = {}
    for record in document.get("methods", []):
        ref = method_ref_from_iprof(record, type_names)
        if ref is not None:
            method_refs[record["id"]] = ref

    data = ProfileData()

    def record_executed(method_id: int) -> MethodRef | None:
        ref = method_refs.get(method_id)
        if ref is None:
            return None
        data.executed.setdefault(ref.canonical_id, ref)
        data.executed_loose.add(ref.loose_key)
        return ref

    def ingest(profiles: list[dict], with_counts: bool) -> None:
        for profile in profiles:
            ids = _parse_ctx(profile.get("ctx", ""))
            if not ids:
                continue
            leaf_ref = record_executed(ids[0])
            if with_counts and leaf_ref is not None:
                records = profile.get("records") or [0]
                data.exec_counts[leaf_ref.canonical_id] = (
                    data.exec_counts.get(leaf_ref.canonical_id, 0) + int(records[0])
                )
            for index in range(1, len(ids)):
                record_executed(ids[index])
            # Adjacent pair (callee=left, caller=right): observed edge caller->callee.
            for index in range(len(ids) - 1):
                callee = method_refs.get(ids[index])
                caller = method_refs.get(ids[index + 1])
                if callee is not None and caller is not None:
                    data.observed_edges.add((caller.canonical_id, callee.canonical_id))

    ingest(call_count, with_counts=True)
    ingest(conditional, with_counts=False)
    ingest(virtual, with_counts=False)
    return data


def _line_indent_depth(prefix: str) -> int:
    return len(prefix) // 4


def load_call_graph(call_tree_path: str, used_methods_path: str | None) -> CallGraph:
    """Parse the analysis call tree into a reachable set and reverse adjacency."""
    graph = CallGraph()

    def register(ref: MethodRef) -> None:
        graph.reachable.setdefault(ref.canonical_id, ref)
        graph.reachable_loose.add(ref.loose_key)

    # Stack of (depth, effective_caller_canonical_id_or_None). A "virtually calls"
    # site carries no resolved method, so its children attach to its own caller.
    stack: list[tuple[int, str | None]] = []
    with open(call_tree_path, "r", encoding="utf-8") as tree_file:
        for raw_line in tree_file:
            line = raw_line.rstrip("\n")
            marker = max(line.find("├── "), line.find("└── "))
            if marker < 0:
                continue
            depth = _line_indent_depth(line[:marker])
            rest = line[marker + 4:].strip()

            while stack and stack[-1][0] >= depth:
                stack.pop()
            parent_caller = stack[-1][1] if stack else None

            if rest.startswith("entry "):
                label = rest[len("entry "):].split(", parsing reason")[0]
                label = label.split(" id=")[0].split(" id-ref=")[0].strip()
                ref = parse_fqn_method_label(label)
                effective = ref.canonical_id if ref else None
                if ref:
                    register(ref)
                stack.append((depth, effective))
                continue

            for prefix in ("directly calls ", "is overridden by ", "inlined "):
                if rest.startswith(prefix):
                    label = rest[len(prefix):].split(" id=")[0].split(" id-ref=")[0].strip()
                    ref = parse_fqn_method_label(label)
                    if ref:
                        register(ref)
                        if parent_caller:
                            graph.callers_of[ref.canonical_id].add(parent_caller)
                        stack.append((depth, ref.canonical_id))
                    else:
                        stack.append((depth, parent_caller))
                    break
            else:
                if rest.startswith("virtually calls "):
                    # Abstract dispatch site: transparent to its concrete overrides.
                    stack.append((depth, parent_caller))

    if used_methods_path and os.path.isfile(used_methods_path):
        with open(used_methods_path, "r", encoding="utf-8") as methods_file:
            for raw_line in methods_file:
                ref = parse_fqn_method_label(raw_line.strip())
                if ref:
                    register(ref)
    return graph


def _reaching_path(target: str, graph: CallGraph, entry_ids: set[str]) -> list[str] | None:
    """Walk the call graph backward to the nearest public-API entry id."""
    if target in entry_ids:
        return [target]
    visited = {target}
    queue: deque[tuple[str, list[str]]] = deque([(target, [target])])
    while queue:
        current, path = queue.popleft()
        if len(path) > _MAX_REACHING_PATH_DEPTH:
            continue
        for caller in sorted(graph.callers_of.get(current, ())):
            if caller in visited:
                continue
            visited.add(caller)
            new_path = [caller] + path
            if caller in entry_ids:
                return new_path
            queue.append((caller, new_path))
    return None


def _library_packages(inventory_refs: list[MethodRef]) -> set[str]:
    packages: set[str] = set()
    for ref in inventory_refs:
        segments = ref.owner.split(".")
        if len(segments) >= 2:
            packages.add(".".join(segments[:2]))
        elif segments:
            packages.add(segments[0])
    return packages


def correlate(
        profile: ProfileData,
        graph: CallGraph,
        inventory: dict,
) -> dict:
    """Join profile evidence and call graph against the API inventory."""
    inventory_targets = inventory.get("targets", [])
    inventory_refs: list[MethodRef] = []
    inv_exact: dict[str, dict] = {}
    inv_loose: set[str] = set()
    for target in inventory_targets:
        ref = parse_inventory_id(target.get("id", ""))
        if ref is None:
            continue
        inventory_refs.append(ref)
        inv_exact[ref.canonical_id] = target
        inv_loose.add(ref.loose_key)
    entry_ids = set(inv_exact.keys())

    inventory_report: list[dict] = []
    counts = {"covered": 0, "reachableUncovered": 0, "excluded": 0}
    for ref, target in ((parse_inventory_id(t.get("id", "")), t) for t in inventory_targets):
        if ref is None:
            continue
        executed = ref.canonical_id in profile.executed or ref.loose_key in profile.executed_loose
        reachable = ref.canonical_id in graph.reachable or ref.loose_key in graph.reachable_loose
        entry = {"id": ref.canonical_id, "kind": target.get("kind")}
        if executed:
            entry["status"] = "covered"
            entry["executionCount"] = profile.exec_counts.get(ref.canonical_id, 0)
            counts["covered"] += 1
        elif reachable:
            entry["status"] = "reachable-uncovered"
            path = _reaching_path(ref.canonical_id, graph, entry_ids)
            entry["reachingPath"] = path
            counts["reachableUncovered"] += 1
        else:
            entry["status"] = "excluded"
            counts["excluded"] += 1
        inventory_report.append(entry)

    # Deep discovery: library reachable-but-unobserved methods that are not
    # themselves inventory targets, grouped by nearest public-API entry.
    library_packages = _library_packages(inventory_refs)

    def in_library(owner: str) -> bool:
        return any(owner == pkg or owner.startswith(pkg + ".") for pkg in library_packages)

    discovery_targets: list[dict] = []
    for canonical_id, ref in graph.reachable.items():
        if canonical_id in profile.executed or ref.loose_key in profile.executed_loose:
            continue
        if canonical_id in inv_exact:
            continue
        if not in_library(ref.owner):
            continue
        path = _reaching_path(canonical_id, graph, entry_ids)
        discovery_targets.append({
            "id": canonical_id,
            "owner": ref.owner,
            "ownerClass": ref.owner_class_simple,
            "nearestPublicEntry": path[0] if path else None,
            "reachingPath": path,
            "feasible": path is not None,
        })
    discovery_targets.sort(key=lambda item: (item["nearestPublicEntry"] or "~", item["id"]))

    return {
        "summary": {
            "reachableMethods": len(graph.reachable),
            "executedMethods": len(profile.executed),
            "observedEdges": len(profile.observed_edges),
            "inventoryTargets": len(inventory_report),
            "inventoryCovered": counts["covered"],
            "inventoryReachableUncovered": counts["reachableUncovered"],
            "inventoryExcluded": counts["excluded"],
            "discoveryTargets": len(discovery_targets),
            "discoveryFeasible": sum(1 for item in discovery_targets if item["feasible"]),
        },
        "inventory": inventory_report,
        "discoveryTargets": discovery_targets,
        "caveats": [
            "Profile is positive-only: absence means not-observed, never impossible.",
            "The analysis call graph over-approximates; statically-reachable-but-"
            "infeasible targets are legitimate skipped/exhausted outcomes.",
            "A reaching path is necessary, not sufficient: edges may sit behind "
            "guards that still require satisfying inputs.",
        ],
    }


def write_lcov(report: dict, inventory: dict, profile: ProfileData, lcov_path: str) -> None:
    """Emit function/branch-granularity LCOV for the library's inventory targets."""
    by_source: dict[str, list[tuple[MethodRef, int]]] = defaultdict(list)
    status_by_id = {entry["id"]: entry for entry in report["inventory"]}
    for target in inventory.get("targets", []):
        ref = parse_inventory_id(target.get("id", ""))
        if ref is None:
            continue
        source_path = target.get("sourcePath") or f"{ref.owner.replace('.', '/')}.java"
        line = int(target.get("sourceLine", 1) or 1)
        by_source[source_path].append((ref, line))

    lines: list[str] = ["TN:code-coverage-pgo"]
    for source_path in sorted(by_source):
        lines.append(f"SF:{source_path}")
        hit = 0
        for ref, line in sorted(by_source[source_path], key=lambda item: item[1]):
            lines.append(f"FN:{line},{ref.name}")
        for ref, line in sorted(by_source[source_path], key=lambda item: item[1]):
            entry = status_by_id.get(ref.canonical_id, {})
            count = entry.get("executionCount", 0) if entry.get("status") == "covered" else 0
            if count:
                hit += 1
            lines.append(f"FNDA:{count},{ref.name}")
        lines.append(f"FNF:{len(by_source[source_path])}")
        lines.append(f"FNH:{hit}")
        lines.append("end_of_record")
    with open(lcov_path, "w", encoding="utf-8") as lcov_file:
        lcov_file.write("\n".join(lines) + "\n")


def write_markdown(report: dict, coordinate: str, iteration: int, md_path: str) -> None:
    summary = report["summary"]
    lines = [
        f"# PGO discovery report {iteration} — {coordinate}",
        "",
        "## Summary",
        "",
        f"- Reachable methods (denominator): {summary['reachableMethods']}",
        f"- Executed methods (profile contexts): {summary['executedMethods']}",
        f"- Observed call edges: {summary['observedEdges']}",
        f"- Inventory targets: {summary['inventoryTargets']} "
        f"(covered {summary['inventoryCovered']}, reachable-uncovered "
        f"{summary['inventoryReachableUncovered']}, excluded {summary['inventoryExcluded']})",
        f"- Deep discovery targets: {summary['discoveryTargets']} "
        f"(with reaching path {summary['discoveryFeasible']})",
        "",
        "## Reachable-but-unobserved inventory targets",
        "",
    ]
    uncovered = [entry for entry in report["inventory"] if entry["status"] == "reachable-uncovered"]
    if not uncovered:
        lines.append("_None — every reachable inventory target was observed._")
    for entry in uncovered:
        path = entry.get("reachingPath")
        reach = " → ".join(path) if path else "no public entry found (candidate skip/exhaust)"
        lines.append(f"- `{entry['id']}`\n  - reaching path: {reach}")

    lines += ["", "## Deep discovery batches (group by entry point)", ""]
    grouped: dict[str, list[dict]] = defaultdict(list)
    for item in report["discoveryTargets"]:
        grouped[item["nearestPublicEntry"] or "(no public entry)"].append(item)
    if not grouped:
        lines.append("_No library reachable-but-unobserved methods outside the inventory._")
    for entry_point in sorted(grouped):
        lines.append(f"### Entry `{entry_point}`")
        for item in grouped[entry_point]:
            path = item.get("reachingPath")
            reach = " → ".join(path) if path else "no public entry found"
            lines.append(f"- `{item['id']}` — {reach}")
        lines.append("")

    lines += ["## Caveats", ""]
    lines += [f"- {caveat}" for caveat in report["caveats"]]
    with open(md_path, "w", encoding="utf-8") as md_file:
        md_file.write("\n".join(lines) + "\n")


def generate_report(
        profile_path: str,
        call_tree_path: str,
        api_inventory_path: str,
        coordinate: str,
        iteration: int,
        output_dir: str,
        used_methods_path: str | None,
) -> dict:
    profile = load_profile(profile_path)
    graph = load_call_graph(call_tree_path, used_methods_path)
    with open(api_inventory_path, "r", encoding="utf-8") as inventory_file:
        inventory = json.load(inventory_file)

    report = correlate(profile, graph, inventory)
    report["coordinate"] = coordinate
    report["iteration"] = iteration
    report["profileKind"] = "instrumented"

    os.makedirs(output_dir, exist_ok=True)
    json_path = os.path.join(output_dir, f"discovery-report-{iteration}.json")
    md_path = os.path.join(output_dir, f"discovery-report-{iteration}.md")
    lcov_path = os.path.join(output_dir, f"coverage-{iteration}.lcov")
    with open(json_path, "w", encoding="utf-8") as json_file:
        json.dump(report, json_file, indent=2)
        json_file.write("\n")
    write_markdown(report, coordinate, iteration, md_path)
    write_lcov(report, inventory, profile, lcov_path)
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate a PGO coverage correlation report.")
    parser.add_argument("--profile", required=True, help="Instrumented .iprof profile path.")
    parser.add_argument("--call-tree", required=True, help="Analysis call_tree_*.txt path.")
    parser.add_argument("--api-inventory", required=True, help="api-inventory.json path.")
    parser.add_argument("--coordinate", required=True, help="group:artifact:version.")
    parser.add_argument("--iteration", type=int, default=1, help="Discovery iteration number.")
    parser.add_argument("--output-dir", required=True, help="Directory for discovery artifacts.")
    parser.add_argument("--used-methods", default=None, help="Optional used_methods_*.txt denominator.")
    args = parser.parse_args()

    try:
        report = generate_report(
            profile_path=args.profile,
            call_tree_path=args.call_tree,
            api_inventory_path=args.api_inventory,
            coordinate=args.coordinate,
            iteration=args.iteration,
            output_dir=args.output_dir,
            used_methods_path=args.used_methods,
        )
    except SampledProfileError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(2)

    summary = report["summary"]
    print(
        "PGO discovery report {iteration}: {executed}/{reachable} methods executed, "
        "{uncovered} inventory targets reachable-but-uncovered, {discovery} deep targets.".format(
            iteration=args.iteration,
            executed=summary["executedMethods"],
            reachable=summary["reachableMethods"],
            uncovered=summary["inventoryReachableUncovered"],
            discovery=summary["discoveryTargets"],
        )
    )


if __name__ == "__main__":
    main()
