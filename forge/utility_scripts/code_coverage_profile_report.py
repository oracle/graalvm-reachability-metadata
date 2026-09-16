# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

# Deep-method coverage, with JaCoCo as the only coverage authority and sampled PGO as
# navigation: §AR-code-coverage-improvement.4.2, §AR-code-coverage-improvement.3.

"""
JaCoCo-exact deep-method report with sampled-PGO path guidance.

The analyzer selects exact library methods reported by JaCoCo but absent from
the public API inventory. Sampled PGO and the static graph rank graph-present
paths; graph-absent methods remain in full JSON but never enter prompts.
Input loading, graph construction, routing, record building, and rendering
live in the sibling `code_coverage_profile_*` modules.

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
import json
import os
import sys

from utility_scripts.code_coverage_jacoco import (
    JacocoCoverage,
    JacocoLineCoverage,
    JacocoMethodCoverage,
    JacocoReportError,
    load_jacoco_coverage,
)
from utility_scripts.code_coverage_model import MethodRef, parse_inventory_id
from utility_scripts.code_coverage_profile_graph import (
    CallGraph,
    _is_synthetic_method,
    load_call_graph,
)
from utility_scripts.code_coverage_profile_inputs import (
    ProfileFormatError,
    TargetState,
    _effective_target_state,
    _load_json_object,
    _parse_target_state,
    _require_coordinate,
    _target_state_to_json,
    library_owners,
    load_library_line_numbers,
    load_library_methods,
    load_target_states,
)
from utility_scripts.code_coverage_profile_records import (
    MAX_LISTED_METHODS,
    NearCallRecord,
    _build_record,
    _classify_miss,
    _method_evidence,
    _prompt_selection_key,
    _record_rank_key,
    _record_to_json,
)
from utility_scripts.code_coverage_profile_render import write_lcov, write_markdown
from utility_scripts.code_coverage_profile_routes import (
    RouteMap,
    SampledProfile,
    _observed_contexts,
    _observed_methods,
    _public_entry_routes,
    _sample_routes,
    load_sampled_profile,
)


def correlate(
        profile: SampledProfile,
        graph: CallGraph,
        inventory: dict,
        jacoco_methods: dict[str, JacocoMethodCoverage],
        max_listed: int = MAX_LISTED_METHODS,
        attempt_counts: dict[str, int] | None = None,
        target_states: dict[str, TargetState] | None = None,
        library_methods: set[str] | None = None,
        jacoco_lines: dict[str, dict[int, JacocoLineCoverage]] | None = None,
) -> tuple[dict, list[NearCallRecord]]:
    """Build exact public coverage and deep uncovered-method path records."""
    if max_listed <= 0:
        raise ProfileFormatError("max_listed must be positive.")
    attempts: dict[str, int] = attempt_counts or {}
    states: dict[str, TargetState] = target_states or {}
    lines: dict[str, dict[int, JacocoLineCoverage]] = jacoco_lines or {}
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
    # A JaCoCo report covers every instrumented class on the test runtime
    # classpath, which for libraries publishing a `test`-classifier artifact
    # includes their own unit tests. Restrict the deep universe to methods the
    # resolved library jars actually declare (§AR-code-coverage-improvement.4.2).
    deep_candidate_ids: set[str] = jacoco_ids - inventory_ids
    foreign_ids: set[str] = (
        deep_candidate_ids - library_methods if library_methods is not None else set()
    )
    deep_ids: list[str] = sorted(deep_candidate_ids - foreign_ids)
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
    deep_uncovered_ids: set[str] = {
        coverage.method_ref.canonical_id for coverage in deep_uncovered
    }

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
                jacoco_uncovered=True,
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
    # Compiler-owned methods stay in the universe and the denominator, but no
    # agent can write a test naming one, and for nearly all of them the
    # enclosing method is an offered target already
    # (§AR-code-coverage-improvement.4.2.1).
    bulk_records: list[NearCallRecord] = [
        record
        for record in uncovered_records
        if record.join_kind != "none"
        and not _is_synthetic_method(record.target_ref)
    ]
    actionable_records: list[NearCallRecord] = [
        record for record in bulk_records if not record.target_state.terminal
    ]
    synthetic_excluded: int = sum(
        1
        for record in uncovered_records
        if record.join_kind != "none"
        and not record.target_state.terminal
        and _is_synthetic_method(record.target_ref)
    )
    prompt_records: list[NearCallRecord] = sorted(
        actionable_records, key=_prompt_selection_key,
    )[:effective_limit]
    miss_classifications: dict[str, dict] = {
        record.target_ref.canonical_id: _classify_miss(
            record, graph, jacoco_methods, lines
        )
        for record in uncovered_records
    }
    uncovered_json: list[dict] = [
        _record_to_json(
            record,
            graph,
            mathematical_ranks[record.target_ref.canonical_id],
            jacoco_methods,
            miss_classifications[record.target_ref.canonical_id],
        )
        for record in uncovered_records
    ]
    bulk_json: list[dict] = [
        _record_to_json(
            record,
            graph,
            mathematical_ranks[record.target_ref.canonical_id],
            jacoco_methods,
            miss_classifications[record.target_ref.canonical_id],
        )
        for record in bulk_records
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
            "deepSyntheticMethods": sum(
                1 for coverage in deep_coverage if _is_synthetic_method(coverage.method_ref)
            ),
            "deepSyntheticUncovered": sum(
                1 for coverage in deep_uncovered if _is_synthetic_method(coverage.method_ref)
            ),
            "syntheticExcludedFromPrompt": synthetic_excluded,
            "nonLibraryMethodsExcluded": len(foreign_ids),
            "foreignDispatchSites": graph.foreign_dispatch_sites,
            "foreignDispatchEdges": graph.foreign_dispatch_edges,
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
            _target_state_to_json(
                method_id,
                _effective_target_state(
                    method_id,
                    states,
                    attempts,
                    jacoco_uncovered=method_id in deep_uncovered_ids,
                ),
            )
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
        "bulkTargets": bulk_json,
        "caveats": [
            "JaCoCo is the only coverage authority; sampled PGO evidence is guidance only.",
            "Absence of a sample never proves non-execution.",
            "The analysis call graph over-approximates; static paths may be infeasible.",
        ] + ([
            "No library method list was supplied, so the deep universe still counts "
            "every JaCoCo-reported method, including any the library's own "
            "test-classifier artifact contributes.",
            "Without that list, virtual call sites declaring a foreign type still "
            "route, so a path may rest on an edge class-hierarchy analysis could "
            "not rule out.",
        ] if library_methods is None else []) + ([
            f"{graph.unjudged_dispatch_sites} virtual call sites carry no declared "
            "target in the call-tree dump; they still route, so a path may rest on "
            "an edge class-hierarchy analysis could not rule out."
        ] if graph.unjudged_dispatch_sites else []),
    }
    return report, prompt_records


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
        library_methods_path: str | None = None,
) -> dict:
    if not isinstance(coordinate, str) or not coordinate.strip():
        raise ProfileFormatError("coordinate must be non-empty.")
    if iteration < 0:
        raise ProfileFormatError("iteration must be non-negative.")
    if max_listed <= 0:
        raise ProfileFormatError("max_listed must be positive.")
    library_methods: set[str] | None = (
        load_library_methods(library_methods_path) if library_methods_path else None
    )
    line_numbers: dict[str, tuple[tuple[int, int], ...]] = (
        load_library_line_numbers(library_methods_path)
        if library_methods_path else {}
    )
    graph: CallGraph = load_call_graph(
        reports_dir,
        library_owners(library_methods),
        line_numbers,
        library_methods,
    )
    profile: SampledProfile = load_sampled_profile(profile_path, graph)
    inventory: dict = _load_json_object(api_inventory_path, "API inventory")
    _require_coordinate(inventory, coordinate, "API inventory")
    jacoco: JacocoCoverage = load_jacoco_coverage(jacoco_xml_paths)
    jacoco_methods: dict[str, JacocoMethodCoverage] = jacoco.methods
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
        library_methods,
        jacoco.lines,
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
    parser.add_argument(
        "--library-methods",
        help="methods.csv from the bytecode call-graph extractor; restricts the deep "
             "universe to methods the resolved library jars declare.",
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
            library_methods_path=args.library_methods,
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
