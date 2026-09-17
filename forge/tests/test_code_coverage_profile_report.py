# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Tests for the deep-method correlation over JaCoCo and the sampled graph."""

import os
import tempfile
import unittest

from utility_scripts.code_coverage_jacoco import JacocoLineCoverage, JacocoMethodCoverage
from utility_scripts.code_coverage_model import MethodRef
from utility_scripts import code_coverage_profile_report as report_module
from utility_scripts.code_coverage_profile_graph import CallGraph, load_call_graph
from utility_scripts.code_coverage_profile_inputs import (
    ProfileFormatError,
    TargetState,
    load_library_line_numbers,
    load_library_methods,
)
from utility_scripts.code_coverage_profile_records import (
    MAX_LISTED_METHODS,
    NearCallRecord,
    edge_miss_classification,
)
from utility_scripts.code_coverage_profile_routes import (
    Sample,
    SampledProfile,
    load_sampled_profile,
)

from tests.code_coverage_profile_support import (
    FIXTURES,
    INIT_ID,
    JACOCO_ONLY_ID,
    JACOCO_PATH,
    LOAD_ID,
    OF_ID,
    ORPHAN_ID,
    PARSE_ID,
    RELOAD_ID,
    RESOLVE_ID,
    RESOLVE_INTEGER_ID,
    _coverage,
    _load_inventory,
    _load_jacoco,
)


class DeepCorrelationTest(unittest.TestCase):

    def setUp(self) -> None:
        self.graph = load_call_graph(FIXTURES)
        self.profile = load_sampled_profile(
            os.path.join(FIXTURES, "near_call_sampling.iprof"), self.graph
        )
        self.inventory = _load_inventory()
        self.jacoco = _load_jacoco()

    def _run(
            self,
            max_listed: int = MAX_LISTED_METHODS,
            attempts: dict[str, int] | None = None,
    ) -> tuple[dict, list[NearCallRecord]]:
        return report_module.correlate(
            self.profile,
            self.graph,
            self.inventory,
            self.jacoco,
            max_listed,
            attempts,
        )

    def test_target_universe_is_exact_jacoco_minus_inventory(self) -> None:
        report, _ = self._run()
        summary = report["summary"]
        self.assertEqual(summary["inventoryCovered"], 2)
        self.assertEqual(summary["deepMethods"], 7)
        self.assertEqual(summary["deepCovered"], 1)
        self.assertEqual(summary["deepUncovered"], 6)
        self.assertEqual(summary["graphOnlyMethods"], 2)
        self.assertEqual(summary["jacocoOnlyMethods"], 1)
        ids = {entry["id"] for entry in report["uncoveredPaths"]}
        self.assertNotIn(INIT_ID, ids)
        self.assertNotIn(OF_ID, ids)
        self.assertIn(JACOCO_ONLY_ID, ids)
        diagnostics = {entry["id"]: entry for entry in report["diagnosticMethods"]}
        jacoco_only = diagnostics[JACOCO_ONLY_ID]
        self.assertEqual(jacoco_only["status"], "uncovered")
        self.assertEqual(jacoco_only["graphStatus"], "not-present")
        self.assertIsNotNone(jacoco_only["sourcePath"])
        self.assertIsNotNone(jacoco_only["sourceLine"])
        self.assertEqual(jacoco_only["jacocoReportPaths"], [JACOCO_PATH])
        deep = {entry["id"]: entry for entry in report["deepMethods"]}
        self.assertEqual(deep[RESOLVE_ID]["jacocoReportPaths"], [JACOCO_PATH])
        self.assertEqual(deep[JACOCO_ONLY_ID]["graphStatus"], "not-present")
        uncovered = {entry["id"]: entry for entry in report["uncoveredPaths"]}
        self.assertEqual(uncovered[RESOLVE_ID]["jacocoReportPaths"], [JACOCO_PATH])
        graph_absent = uncovered[JACOCO_ONLY_ID]
        self.assertEqual(graph_absent["graphStatus"], "not-present")
        self.assertEqual(graph_absent["joinKind"], "none")
        self.assertIsNone(graph_absent["stepsRemaining"])
        self.assertIsNone(graph_absent["reachingPath"])
        self.assertIsNone(graph_absent["edges"])
        self.assertNotIn(JACOCO_ONLY_ID, report["promptTargetIds"])

    def test_graph_absent_covered_method_stays_in_deep_totals(self) -> None:
        covered = MethodRef("example.Internal", "covered", (), "void")
        uncovered = MethodRef("example.Internal", "uncovered", (), "void")

        report, _ = report_module.correlate(
            SampledProfile(),
            CallGraph(),
            {"targets": []},
            {
                covered.canonical_id: _coverage(covered, covered=True),
                uncovered.canonical_id: _coverage(uncovered),
            },
        )

        self.assertEqual(report["summary"]["deepMethods"], 2)
        self.assertEqual(report["summary"]["deepCovered"], 1)
        self.assertEqual(report["summary"]["deepUncovered"], 1)
        deep = {entry["id"]: entry for entry in report["deepMethods"]}
        self.assertEqual(deep[covered.canonical_id]["graphStatus"], "not-present")
        self.assertEqual(deep[covered.canonical_id]["status"], "covered")
        self.assertEqual(
            [entry["id"] for entry in report["uncoveredPaths"]],
            [uncovered.canonical_id],
        )
        self.assertEqual(report["promptTargetIds"], [])

    def test_same_arity_overloads_keep_exact_jacoco_status(self) -> None:
        report, _ = self._run()
        deep_status = {entry["id"]: entry["status"] for entry in report["deepMethods"]}
        self.assertEqual(deep_status[RESOLVE_ID], "uncovered")
        self.assertEqual(deep_status[RESOLVE_INTEGER_ID], "covered")
        self.assertIn(RESOLVE_ID, report["promptTargetIds"])
        self.assertNotIn(RESOLVE_INTEGER_ID, report["promptTargetIds"])

    def test_paths_rank_nearest_sampled_then_public_fallback_then_none(self) -> None:
        report, _ = self._run()
        self.assertEqual(
            [entry["id"] for entry in report["uncoveredPaths"]],
            [RESOLVE_ID, PARSE_ID, LOAD_ID, RELOAD_ID, ORPHAN_ID, JACOCO_ONLY_ID],
        )
        by_id = {entry["id"]: entry for entry in report["uncoveredPaths"]}
        self.assertEqual(by_id[RESOLVE_ID]["stepsRemaining"], 1)
        self.assertEqual(by_id[LOAD_ID]["stepsRemaining"], 2)
        self.assertEqual(by_id[PARSE_ID]["joinKind"], "public-entry")
        self.assertEqual(by_id[PARSE_ID]["stepsRemaining"], 1)
        self.assertEqual(by_id[ORPHAN_ID]["joinKind"], "none")
        self.assertIsNone(by_id[ORPHAN_ID]["stepsRemaining"])
        self.assertEqual(by_id[JACOCO_ONLY_ID]["graphStatus"], "not-present")
        self.assertEqual(by_id[JACOCO_ONLY_ID]["joinKind"], "none")
        self.assertIsNone(by_id[JACOCO_ONLY_ID]["stepsRemaining"])
        self.assertIsNone(by_id[JACOCO_ONLY_ID]["reachingPath"])
        self.assertIsNone(by_id[JACOCO_ONLY_ID]["edges"])
        self.assertNotIn(ORPHAN_ID, report["promptTargetIds"])
        self.assertNotIn(
            ORPHAN_ID,
            {entry["id"] for entry in report["bulkTargets"]},
        )

    def test_sampled_observation_never_changes_jacoco_status(self) -> None:
        resolve_id = self.graph.key_to_id[RESOLVE_ID]
        self.profile.samples[0].path.append((resolve_id, 0))
        self.profile.samples[0].path_full_indexes.append(
            len(self.profile.samples[0].full_path) - 1
        )
        self.profile.sample_counts[resolve_id] = 42
        report, _ = self._run()
        path = next(entry for entry in report["uncoveredPaths"] if entry["id"] == RESOLVE_ID)
        self.assertEqual(path["jacocoStatus"], "uncovered")
        self.assertEqual(path["stepsRemaining"], 0)

    def test_covered_call_site_is_dispatched_elsewhere_and_never_fork(self) -> None:
        caller = MethodRef("example.Router", "route", (), "void")
        target = MethodRef("example.Handler$1", "handle", (), "void")
        suite_handler = MethodRef(
            "example.RouteCoverageTest$1", "handle", (), "void"
        )
        target_edge: dict = {
            "caller": 1,
            "callee": 2,
            "bci": "1",
            "is_direct": "false",
            "kind": "call",
            "invoke_id": 10,
            "source_line": 1,
        }
        suite_edge: dict = {
            **target_edge,
            "callee": 3,
        }
        graph = CallGraph(
            methods={1: caller, 2: target, 3: suite_handler},
            key_to_id={
                caller.canonical_id: 1,
                target.canonical_id: 2,
                suite_handler.canonical_id: 3,
            },
            adjacency={1: [target_edge, suite_edge]},
            reverse_adjacency={2: [target_edge], 3: [suite_edge]},
            invoke_fan_out={10: [2, 3]},
        )
        caller_coverage = JacocoMethodCoverage(
            method_ref=caller,
            covered=True,
            source_path="example/Router.java",
            source_line=1,
            report_paths=("fixture.xml",),
        )
        report, _ = report_module.correlate(
            SampledProfile(),
            graph,
            {"targets": [{"id": caller.canonical_id, "kind": "method"}]},
            {
                caller.canonical_id: caller_coverage,
                target.canonical_id: _coverage(target),
            },
            jacoco_lines={
                "example/Router.java": {
                    1: JacocoLineCoverage(mi=0, ci=5, mb=1, cb=1),
                },
            },
        )

        classification: dict = report["bulkTargets"][0]["missClassification"]
        self.assertEqual(classification["kind"], "dispatched-elsewhere")
        self.assertIsNone(classification["fork"])
        self.assertTrue(
            next(
                candidate
                for candidate in classification["candidates"]
                if candidate["id"] == suite_handler.canonical_id
            )["coverageSuite"]
        )

    def test_target_at_missed_block_start_keeps_preceding_fork(self) -> None:
        caller = MethodRef("example.Router", "route", (), "void")
        target = MethodRef("example.Handler", "handle", (), "void")
        edge: dict = {
            "caller": 1,
            "callee": 2,
            "bci": "8",
            "is_direct": "true",
            "kind": "call",
            "invoke_id": 10,
            "source_line": 3,
        }
        graph = CallGraph(
            methods={1: caller, 2: target},
            invoke_fan_out={10: [2]},
        )
        caller_coverage = JacocoMethodCoverage(
            method_ref=caller,
            covered=True,
            source_path="example/Router.java",
            source_line=1,
            report_paths=("fixture.xml",),
        )

        classification: dict = edge_miss_classification(
            edge,
            graph,
            {caller.canonical_id: caller_coverage},
            {
                "example/Router.java": {
                    1: JacocoLineCoverage(mi=0, ci=5, mb=1, cb=1),
                    2: JacocoLineCoverage(mi=0, ci=2, mb=0, cb=0),
                    3: JacocoLineCoverage(mi=1, ci=0, mb=0, cb=0),
                },
            },
        )

        self.assertEqual(classification["kind"], "fork-not-taken")
        self.assertEqual(classification["fork"]["line"], 1)

    def test_shortest_distance_beats_prompt_quality(self) -> None:
        framework = MethodRef("java.lang.Thread", "run", (), "void")
        app = MethodRef("app.Service", "work", (), "void")
        bridge = MethodRef("app.Service", "bridge", (), "void")
        target = MethodRef("app.Internal", "target", (), "void")
        graph = CallGraph(
            methods={1: framework, 2: app, 3: bridge, 4: target},
            key_to_id={ref.canonical_id: method_id for method_id, ref in {
                1: framework, 2: app, 3: bridge, 4: target,
            }.items()},
            adjacency={
                1: [{"caller": 1, "callee": 4, "bci": "1", "is_direct": "true", "kind": "call"}],
                2: [{"caller": 2, "callee": 3, "bci": "2", "is_direct": "true", "kind": "call"}],
                3: [{"caller": 3, "callee": 4, "bci": "3", "is_direct": "true", "kind": "call"}],
            },
        )
        sample = Sample(
            context_id="sample-1",
            raw_context="",
            path=[(1, 0), (2, 0)],
            full_path=[(framework, 0), (app, 0)],
            path_full_indexes=[0, 1],
            count=1,
        )
        profile = SampledProfile(samples=[sample])
        report, _ = report_module.correlate(
            profile,
            graph,
            {"targets": []},
            {target.canonical_id: _coverage(target)},
        )
        path = report["uncoveredPaths"][0]
        self.assertEqual(path["stepsRemaining"], 1)
        self.assertEqual(path["reachingPath"][0], framework.canonical_id)

    def test_hard_cap_retains_full_json_and_rotates_attempted_targets(self) -> None:
        entry = MethodRef("bulk.PublicApi", "start", (), "void")
        refs = [MethodRef("bulk.Targets", f"method{index:03d}", (), "void") for index in range(201)]
        methods = {1: entry, **{index + 2: ref for index, ref in enumerate(refs)}}
        graph = CallGraph(
            methods=methods,
            key_to_id={ref.canonical_id: method_id for method_id, ref in methods.items()},
            adjacency={
                1: [
                    {
                        "caller": 1,
                        "callee": index + 2,
                        "bci": str(index),
                        "is_direct": "true",
                        "kind": "call",
                    }
                    for index in range(len(refs))
                ]
            },
        )
        jacoco = {
            entry.canonical_id: _coverage(entry, covered=True),
            **{ref.canonical_id: _coverage(ref) for ref in refs},
        }
        inventory = {"targets": [{"id": entry.canonical_id, "kind": "method"}]}
        first, _ = report_module.correlate(
            SampledProfile(), graph, inventory, jacoco, max_listed=1000
        )
        self.assertEqual(len(first["uncoveredPaths"]), 201)
        self.assertEqual(len(first["promptTargetIds"]), 200)
        omitted = next(
            entry for entry in first["uncoveredPaths"]
            if entry["id"] not in first["promptTargetIds"]
        )
        attempted_states = {
            method_id: TargetState(status="attempted", attempt_count=1)
            for method_id in first["promptTargetIds"]
        }
        second, _ = report_module.correlate(
            SampledProfile(), graph, inventory, jacoco,
            max_listed=200, target_states=attempted_states,
        )
        self.assertEqual(second["promptTargetIds"][0], omitted["id"])
        rotated = next(entry for entry in second["uncoveredPaths"] if entry["id"] == omitted["id"])
        self.assertEqual(rotated["rank"], 201)
        self.assertEqual(rotated["attemptCount"], 0)


class LibraryMethodFilterTest(unittest.TestCase):
    """The deep universe must hold library methods only.

    A library that publishes a `test`-classifier artifact puts its own unit
    tests on the test runtime classpath, and JaCoCo reports them in the same
    packages as the library itself, so package prefixes cannot separate them.
    """

    LIB = MethodRef("com.example.Internal", "work", (), "void")
    FOREIGN = MethodRef("com.example.InternalTest", "shouldWork", (), "void")

    def _report(self, library_methods: set[str] | None) -> dict:
        report, _ = report_module.correlate(
            SampledProfile(),
            CallGraph(),
            {"targets": []},
            {
                self.LIB.canonical_id: _coverage(self.LIB, covered=True),
                self.FOREIGN.canonical_id: _coverage(self.FOREIGN, covered=True),
            },
            library_methods=library_methods,
        )
        return report

    def test_foreign_test_methods_leave_the_deep_universe(self) -> None:
        summary = self._report({self.LIB.canonical_id})["summary"]
        self.assertEqual(summary["deepMethods"], 1)
        self.assertEqual(summary["deepCovered"], 1)
        self.assertEqual(summary["nonLibraryMethodsExcluded"], 1)

    def test_without_a_method_list_nothing_is_excluded_but_it_is_declared(self) -> None:
        report = self._report(None)
        self.assertEqual(report["summary"]["deepMethods"], 2)
        self.assertEqual(report["summary"]["nonLibraryMethodsExcluded"], 0)
        self.assertTrue(any("test-classifier" in caveat for caveat in report["caveats"]))

    def test_loading_rejects_a_file_without_the_id_header(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "methods.csv")
            with open(path, "w", encoding="utf-8") as handle:
                handle.write("name,hasCode\n\"a.B#c():void\",true\n")
            with self.assertRaises(ProfileFormatError) as raised:
                load_library_methods(path)
            self.assertIn("id", str(raised.exception))

    def test_loading_reads_canonical_ids_from_the_extractor_csv(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "methods.csv")
            with open(path, "w", encoding="utf-8") as handle:
                handle.write("id,hasCode,isPublicApi\n")
                handle.write(f'"{self.LIB.canonical_id}",true,false\n')
            self.assertEqual(load_library_methods(path), {self.LIB.canonical_id})

    def test_loading_reads_bytecode_line_numbers_from_extractor_csv(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "methods.csv")
            with open(path, "w", encoding="utf-8") as handle:
                handle.write("id,hasCode,isPublicApi,isStatic,lineNumbers\n")
                handle.write(
                    f'"{self.LIB.canonical_id}",true,false,false,"0:11;8:15"\n'
                )

            self.assertEqual(
                load_library_line_numbers(path),
                {self.LIB.canonical_id: ((0, 11), (8, 15))},
            )


if __name__ == "__main__":
    unittest.main()
