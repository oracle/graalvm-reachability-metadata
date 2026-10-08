# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

# Deep-method coverage, with JaCoCo as the only coverage authority and PGO evidence as
# navigation: §AR-code-coverage-improvement.4.2, §AR-code-coverage-deep-navigation.

"""
JaCoCo-exact deep-method report with PGO path guidance.

The analyzer targets exact library methods reported by JaCoCo but absent from
the public API inventory, plus the inventory entries JaCoCo reported uncovered
when the API phase ended. PGO evidence and the static graph route a target only
from code that ran; unrouted and graph-absent methods remain in full JSON but
never enter prompts.
Input loading, graph construction, the target universe, counters, control
flow, routing, record building, miss classification, and rendering live in the
sibling `code_coverage_profile_*` modules.

Inputs:
- `call_tree_{methods,invokes,targets}_*.csv` — the analysis call-tree CSV dump
  (`-H:+PrintAnalysisCallTree -H:PrintAnalysisCallTreeType=CSV`).
- an instrumented, sampled `.iprof` — profile `<`-chain contexts are leaf-first
  (`callee:bci<caller:bci`), so sampled stacks read right-to-left from the root;
  a profile without counters degrades to line-level hints.
- `api-inventory.json` — the public user-callable target universe.
- one or more JaCoCo XML reports — exact coverage for public and deep methods.
- the extractor's `methods.csv`, with its sibling `flow.csv` and `types.csv`
  when present — library membership, control flow, and the type hierarchy.

Usage:
  python3 utility_scripts/code_coverage_profile_report.py \
    --profile <native-test.iprof> \
    --reports-dir <build/.../reports> \
    --api-inventory <api-inventory.json> \
    --jacoco-xml <jacoco-N.xml> [--jacoco-xml <additional.xml>] \
    --coordinate group:artifact:version \
    --iteration 0 \
    [--target-state <deep-cover-N.json>] \
    [--source-root <extracted library sources>] \
    --output-dir runtime/code-coverage/discovery
"""

from __future__ import annotations

import argparse
import json
import os
import sys

from utility_scripts.code_coverage_deep_sessions import write_prompts
from utility_scripts.code_coverage_jacoco import (
    JacocoCoverage,
    JacocoLineCoverage,
    JacocoMethodCoverage,
    JacocoReportError,
    load_jacoco_coverage,
)
from utility_scripts.code_coverage_profile_graph import (
    CallGraph,
    is_synthetic_method,
    load_call_graph,
)
from utility_scripts.code_coverage_profile_history import (
    carried_public_targets,
    next_attempt_counts,
    previous_report,
    previous_target_states,
)
from utility_scripts.code_coverage_profile_inputs import (
    INSTRUMENTED_PROFILE_KIND,
    SAMPLED_PROFILE_KIND,
    ProfileFormatError,
    TargetState,
    effective_target_state,
    load_json_object,
    require_coordinate,
    target_state_to_json,
    library_owners,
    load_library_line_numbers,
    load_library_methods,
    load_target_states,
)
from utility_scripts.code_coverage_profile_miss import classify_miss
from utility_scripts.code_coverage_profile_navigation import (
    NavigationEvidence,
    load_navigation,
    navigation_caveats,
    navigation_summary,
)
from utility_scripts.code_coverage_profile_records import (
    MAX_LISTED_METHODS,
    NearCallRecord,
    build_record,
    method_evidence,
    prompt_selection_key,
    record_rank_key,
    record_to_json,
)
from utility_scripts.code_coverage_profile_render import write_lcov
from utility_scripts.code_coverage_profile_routes import (
    RouteMap,
    SampledProfile,
    execution_status,
    observed_contexts,
    observed_methods,
    public_entry_routes,
    sample_routes,
)
from utility_scripts.code_coverage_profile_universe import (
    DeepUniverse,
    InventoryCoverage,
    check_target_states,
    deep_universe,
    diagnostic_methods,
    inventory_coverage,
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
        evidence: NavigationEvidence | None = None,
        carried_public_ids: list[str] | None = None,
) -> tuple[dict, list[NearCallRecord]]:
    """Build exact public coverage and deep uncovered-method path records.

    `carried_public_ids` is the public part of the deep universe as the phase's
    first report froze it; `None` freezes it from this report
    (§AR-code-coverage-improvement.4.2).
    """
    if max_listed <= 0:
        raise ProfileFormatError("max_listed must be positive.")
    attempts: dict[str, int] = attempt_counts or {}
    states: dict[str, TargetState] = target_states or {}
    lines: dict[str, dict[int, JacocoLineCoverage]] = jacoco_lines or {}
    navigation: NavigationEvidence = evidence or NavigationEvidence()
    inventory_status: InventoryCoverage = inventory_coverage(inventory, jacoco_methods)
    inventory_ids: set[str] = {ref.canonical_id for ref in inventory_status.refs}
    graph_ids: set[str] = set(graph.key_to_id)
    jacoco_ids: set[str] = set(jacoco_methods)
    universe: DeepUniverse = deep_universe(
        jacoco_methods, inventory_ids, library_methods, carried_public_ids
    )
    check_target_states(states, universe, jacoco_methods)
    deep_coverage: list[JacocoMethodCoverage] = [
        jacoco_methods[method_id] for method_id in universe.internal_ids
    ]
    deep_uncovered: list[JacocoMethodCoverage] = [
        coverage for coverage in deep_coverage if not coverage.covered
    ]
    public_coverage: list[JacocoMethodCoverage] = [
        jacoco_methods[method_id] for method_id in universe.public_ids
    ]
    public_uncovered: list[JacocoMethodCoverage] = [
        coverage for coverage in public_coverage if not coverage.covered
    ]
    executed: dict[int, bool] = execution_status(graph, jacoco_methods)
    sampled_routes: RouteMap = sample_routes(graph, profile, executed)
    entry_routes: RouteMap = public_entry_routes(graph, inventory_status.refs, executed)
    uncovered_records: list[NearCallRecord] = [
        build_record(
            coverage,
            graph,
            sampled_routes,
            entry_routes,
            effective_target_state(coverage.method_ref.canonical_id, states, attempts),
            public_api,
        )
        for coverages, public_api in ((deep_uncovered, False), (public_uncovered, True))
        for coverage in coverages
    ]
    # The diagnosis carries the reach count that breaks ranking ties, so it
    # comes first (§AR-code-coverage-deep-navigation.2.2).
    miss_classifications: dict[str, dict] = {}
    for record in uncovered_records:
        classification: dict = classify_miss(
            record, graph, jacoco_methods, lines, executed, navigation
        )
        miss_classifications[record.target_ref.canonical_id] = classification
        record.reach = classification["reach"]
    uncovered_records.sort(key=record_rank_key)
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
        and not is_synthetic_method(record.target_ref)
    ]
    actionable_records: list[NearCallRecord] = [
        record for record in bulk_records if not record.target_state.terminal
    ]
    synthetic_excluded: int = sum(
        1
        for record in uncovered_records
        if record.join_kind != "none"
        and not record.target_state.terminal
        and is_synthetic_method(record.target_ref)
    )
    # Only a route from a sampled frame is prompted; public-entry routes stay
    # ranked in the JSON (§AR-code-coverage-deep-navigation.2).
    prompt_records: list[NearCallRecord] = sorted(
        (record for record in actionable_records if record.join_kind == "sampled"),
        key=prompt_selection_key,
    )[:effective_limit]
    uncovered_json: list[dict] = [
        record_to_json(
            record,
            graph,
            mathematical_ranks[record.target_ref.canonical_id],
            jacoco_methods,
            miss_classifications[record.target_ref.canonical_id],
        )
        for record in uncovered_records
    ]
    bulk_json: list[dict] = [
        record_to_json(
            record,
            graph,
            mathematical_ranks[record.target_ref.canonical_id],
            jacoco_methods,
            miss_classifications[record.target_ref.canonical_id],
        )
        for record in bulk_records
    ]

    def evidence_of(coverage: JacocoMethodCoverage) -> dict:
        present: bool = coverage.method_ref.canonical_id in graph_ids
        return method_evidence(coverage, graph_status="present" if present else "not-present")

    report: dict = {
        "summary": {
            "coverageSource": "jacoco",
            "inventoryTargets": len(inventory_status.entries),
            "inventoryCovered": inventory_status.counts["covered"],
            "inventoryUncovered": inventory_status.counts["uncovered"],
            "inventoryUnknown": inventory_status.counts["unknown"],
            "jacocoMethods": len(jacoco_methods),
            "callGraphMethods": len(graph.key_to_id),
            "graphOnlyMethods": len(graph_ids - jacoco_ids),
            "jacocoOnlyMethods": len(jacoco_ids - graph_ids),
            "deepMethods": len(deep_coverage),
            "deepCovered": len(deep_coverage) - len(deep_uncovered),
            "deepUncovered": len(deep_uncovered),
            "publicTargets": len(public_coverage),
            "publicTargetsCovered": len(public_coverage) - len(public_uncovered),
            "publicTargetsUncovered": len(public_uncovered),
            # The phase's own roster, which its pass yield counts
            # (§AR-code-coverage-improvement.4.3).
            "rosterCovered": (
                len(deep_coverage) - len(deep_uncovered)
                + len(public_coverage) - len(public_uncovered)
            ),
            "deepSyntheticMethods": sum(
                1 for coverage in deep_coverage if is_synthetic_method(coverage.method_ref)
            ),
            "deepSyntheticUncovered": sum(
                1 for coverage in deep_uncovered if is_synthetic_method(coverage.method_ref)
            ),
            "syntheticExcludedFromPrompt": synthetic_excluded,
            "nonLibraryMethodsExcluded": len(universe.foreign_ids),
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
            **navigation_summary(navigation, graph),
        },
        "inventory": inventory_status.entries,
        "targetStates": [
            target_state_to_json(
                method_id,
                effective_target_state(method_id, states, attempts),
            )
            for method_id in sorted(set(states) | set(attempts))
        ],
        "deepMethods": [evidence_of(coverage) for coverage in deep_coverage],
        "publicTargets": [evidence_of(coverage) for coverage in public_coverage],
        "diagnosticMethods": diagnostic_methods(jacoco_methods, graph_ids, inventory_ids),
        "observed": observed_contexts(profile, graph),
        "observedMethods": observed_methods(profile, graph, jacoco_methods),
        "uncoveredPaths": uncovered_json,
        "promptTargetIds": [record.target_ref.canonical_id for record in prompt_records],
        "bulkTargets": bulk_json,
        "caveats": [
            "JaCoCo is the only coverage authority; PGO evidence is guidance only.",
            "Absence of a sample never proves non-execution.",
            "The analysis call graph over-approximates; static paths may be infeasible.",
        ] + navigation_caveats(navigation) + ([
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
        source_root: str | None = None,
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
    profile, evidence = load_navigation(profile_path, graph, library_methods_path, line_numbers)
    inventory: dict = load_json_object(api_inventory_path, "API inventory")
    require_coordinate(inventory, coordinate, "API inventory")
    jacoco: JacocoCoverage = load_jacoco_coverage(jacoco_xml_paths)
    jacoco_methods: dict[str, JacocoMethodCoverage] = jacoco.methods
    previous: dict | None = previous_report(output_dir, iteration)
    target_states: dict[str, TargetState] = previous_target_states(previous)
    target_states.update(load_target_states(target_state_paths, coordinate))
    report, prompt_records = correlate(
        profile,
        graph,
        inventory,
        jacoco_methods,
        max_listed,
        next_attempt_counts(previous),
        target_states,
        library_methods,
        jacoco.lines,
        evidence,
        carried_public_targets(previous),
    )
    report["coordinate"] = coordinate
    report["iteration"] = iteration
    report["profileKind"] = (
        INSTRUMENTED_PROFILE_KIND if evidence.counters is not None else SAMPLED_PROFILE_KIND
    )

    os.makedirs(output_dir, exist_ok=True)
    report["deepSessions"] = write_prompts(
        report, prompt_records, graph, coordinate, iteration, output_dir, source_root)
    json_path: str = os.path.join(output_dir, f"discovery-report-{iteration}.json")
    lcov_path: str = os.path.join(output_dir, f"coverage-{iteration}.lcov")
    with open(json_path, "w", encoding="utf-8") as json_file:
        json.dump(report, json_file, indent=2)
        json_file.write("\n")
    write_lcov(profile, graph, jacoco_methods, lcov_path)
    return report


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Generate JaCoCo-exact deep paths with PGO guidance."
    )
    parser.add_argument(
        "--profile", required=True, help="Instrumented and sampled .iprof profile path."
    )
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
             "universe to methods the resolved library jars declare. Its sibling "
             "flow.csv and types.csv, when present, trace forks and resolve receivers.",
    )
    parser.add_argument("--source-root", help="Extracted library sources that prompt "
                        "locations resolve against (§AR-code-coverage-deep-navigation.3.2).")
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
            source_root=args.source_root,
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
