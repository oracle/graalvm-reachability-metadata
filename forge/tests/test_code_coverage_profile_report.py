# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Tests for JaCoCo-exact deep paths with sampled-PGO guidance."""

import json
import os
import shutil
import tempfile
import unittest

from utility_scripts.code_coverage_jacoco import (
    JacocoMethodCoverage,
    JacocoReportError,
    load_jacoco_method_coverage,
)
from utility_scripts.code_coverage_model import (
    MethodRef,
    method_ref_from_call_tree_row,
    method_ref_from_iprof,
    normalize_type_name,
    parse_inventory_id,
)
from utility_scripts import code_coverage_profile_report as report_module

FIXTURES = os.path.join(os.path.dirname(__file__), "fixtures", "code_coverage")
JACOCO_PATH = os.path.join(FIXTURES, "near_call_jacoco.xml")

INIT_ID = "com.example.Registry#init():void"
RESOLVE_ID = "com.example.Registry#resolve(java.lang.String):void"
RESOLVE_INTEGER_ID = "com.example.Registry#resolve(java.lang.Integer):void"
LOAD_ID = "com.example.Registry#load(java.lang.String):com.example.Driver"
RELOAD_ID = "com.example.Registry#reload():void"
ORPHAN_ID = "com.example.Registry#orphan(int):void"
OF_ID = "com.example.parser.Config#of():com.example.parser.Config"
PARSE_ID = "com.example.parser.Config#parse(java.lang.String):com.example.parser.Config"
JACOCO_ONLY_ID = "com.example.diagnostic.JacocoOnly#ghost():void"


def _load_inventory() -> dict:
    with open(os.path.join(FIXTURES, "near_call_inventory.json"), encoding="utf-8") as inventory_file:
        return json.load(inventory_file)


def _load_jacoco() -> dict[str, JacocoMethodCoverage]:
    return load_jacoco_method_coverage([JACOCO_PATH])


def _coverage(ref: MethodRef, covered: bool = False) -> JacocoMethodCoverage:
    return JacocoMethodCoverage(
        method_ref=ref,
        covered=covered,
        source_path=f"{ref.owner.replace('.', '/')}.java",
        source_line=1,
        report_paths=("fixture.xml",),
    )


class MethodIdentityTest(unittest.TestCase):

    def test_normalize_array_descriptors(self) -> None:
        self.assertEqual(normalize_type_name("[B"), "byte[]")
        self.assertEqual(normalize_type_name("[Ljava.lang.String;"), "java.lang.String[]")
        self.assertEqual(normalize_type_name("java/lang/String"), "java.lang.String")

    def test_iprof_signature_is_decl_ret_params(self) -> None:
        types = {1: "void", 30: "com.example.Registry", 31: "java.lang.String"}
        ref = method_ref_from_iprof({"id": 7, "name": "resolve", "signature": [30, 1, 31]}, types)
        self.assertEqual(ref.canonical_id, RESOLVE_ID)

    def test_call_tree_row_and_inventory_id_agree(self) -> None:
        row = {
            "Type": "com.example.Registry",
            "Name": "load",
            "Parameters": "java.lang.String",
            "Return": "com.example.Driver",
        }
        self.assertEqual(
            method_ref_from_call_tree_row(row).canonical_id,
            parse_inventory_id(LOAD_ID).canonical_id,
        )

    def test_inventory_id_requires_exact_canonical_round_trip(self) -> None:
        invalid = ("A#m(int,):void", "A#m(,):void", "A#m():", "A#m()")
        for target_id in invalid:
            with self.subTest(target_id=target_id):
                self.assertIsNone(parse_inventory_id(target_id))


class CallGraphAndProfileTest(unittest.TestCase):

    def test_call_graph_csv_loaded_via_prefix_match(self) -> None:
        graph = report_module.load_call_graph(FIXTURES)
        self.assertEqual(len(graph.methods), 10)
        self.assertIn(LOAD_ID, graph.key_to_id)
        self.assertEqual(len(graph.loose_to_ids["com.example.Registry#resolve/1"]), 2)

    def test_call_graph_selects_one_complete_suffix_atomically(self) -> None:
        with tempfile.TemporaryDirectory(prefix="call-tree-triplet-") as reports_dir:
            for kind in ("methods", "invokes", "targets"):
                source = os.path.join(
                    FIXTURES, f"call_tree_{kind}_demo_20260101.csv"
                )
                shutil.copy(source, reports_dir)
                with open(
                        os.path.join(reports_dir, f"call_tree_{kind}.csv"),
                        "w",
                        encoding="utf-8",
                ) as stale_file:
                    stale_file.write(
                        "Id\n" if kind != "targets" else "InvokeId,TargetId\n"
                    )
            shutil.copy(
                os.path.join(FIXTURES, "call_tree_methods_demo_20260101.csv"),
                os.path.join(reports_dir, "call_tree_methods_zzz_incomplete.csv"),
            )

            graph = report_module.load_call_graph(reports_dir)

        self.assertEqual(len(graph.methods), 10)
        self.assertIn(LOAD_ID, graph.key_to_id)

    def test_instrumented_profile_is_rejected(self) -> None:
        graph = report_module.load_call_graph(FIXTURES)
        with self.assertRaises(report_module.ProfileFormatError):
            report_module.load_sampled_profile(
                os.path.join(FIXTURES, "near_call_instrumented.iprof"), graph
            )

    def test_sampled_stack_maps_root_first_and_counts_positive_observations(self) -> None:
        graph = report_module.load_call_graph(FIXTURES)
        profile = report_module.load_sampled_profile(
            os.path.join(FIXTURES, "near_call_sampling.iprof"), graph
        )
        self.assertEqual(profile.total_sample_count, 42)
        self.assertEqual(profile.samples[0].context_id, "sample-1")
        names = [graph.methods[static_id].name for static_id, _ in profile.samples[0].path]
        self.assertEqual(names, ["run", "testInit", "init"])
        self.assertEqual(profile.sample_counts[graph.key_to_id[INIT_ID]], 42)

    def test_non_positive_sample_contexts_are_not_observations(self) -> None:
        graph = report_module.load_call_graph(FIXTURES)
        with open(
                os.path.join(FIXTURES, "near_call_sampling.iprof"),
                encoding="utf-8",
        ) as profile_file:
            document = json.load(profile_file)
        context = document["samplingProfiles"][0]["ctx"]
        document["samplingProfiles"] = [
            {"ctx": context, "records": [0]},
            {"ctx": context, "records": [-2]},
            *document["samplingProfiles"],
        ]

        with tempfile.TemporaryDirectory(prefix="sample-counts-") as temp_dir:
            profile_path = os.path.join(temp_dir, "profile.iprof")
            with open(profile_path, "w", encoding="utf-8") as profile_file:
                json.dump(document, profile_file)
            profile = report_module.load_sampled_profile(profile_path, graph)

        self.assertEqual(profile.context_count, 1)
        self.assertEqual(profile.total_sample_count, 42)
        self.assertEqual(len(profile.samples), 1)
        self.assertEqual(profile.samples[0].context_id, "sample-3")
        self.assertEqual(len(profile.sample_counts), 3)
        self.assertEqual(set(profile.sample_counts.values()), {42})


    def test_ambiguous_loose_overload_is_not_mapped(self) -> None:
        graph = report_module.load_call_graph(FIXTURES)
        ambiguous = MethodRef("com.example.Registry", "resolve", ("unknown.Type",), "void")
        self.assertIsNone(report_module._resolve_graph_id(graph, ambiguous))


class DeepCorrelationTest(unittest.TestCase):

    def setUp(self) -> None:
        self.graph = report_module.load_call_graph(FIXTURES)
        self.profile = report_module.load_sampled_profile(
            os.path.join(FIXTURES, "near_call_sampling.iprof"), self.graph
        )
        self.inventory = _load_inventory()
        self.jacoco = _load_jacoco()

    def _run(
            self,
            max_listed: int = report_module.MAX_LISTED_METHODS,
            attempts: dict[str, int] | None = None,
    ) -> tuple[dict, list[report_module.NearCallRecord]]:
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
            report_module.SampledProfile(),
            report_module.CallGraph(),
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

    def test_shortest_distance_beats_prompt_quality(self) -> None:
        framework = MethodRef("java.lang.Thread", "run", (), "void")
        app = MethodRef("app.Service", "work", (), "void")
        bridge = MethodRef("app.Service", "bridge", (), "void")
        target = MethodRef("app.Internal", "target", (), "void")
        graph = report_module.CallGraph(
            methods={1: framework, 2: app, 3: bridge, 4: target},
            key_to_id={ref.canonical_id: method_id for method_id, ref in {
                1: framework, 2: app, 3: bridge, 4: target,
            }.items()},
            adjacency={
                1: [{"caller": 1, "callee": 4, "bci": "1", "is_direct": "true"}],
                2: [{"caller": 2, "callee": 3, "bci": "2", "is_direct": "true"}],
                3: [{"caller": 3, "callee": 4, "bci": "3", "is_direct": "true"}],
            },
        )
        sample = report_module.Sample(
            context_id="sample-1",
            raw_context="",
            path=[(1, 0), (2, 0)],
            full_path=[(framework, 0), (app, 0)],
            path_full_indexes=[0, 1],
            count=1,
        )
        profile = report_module.SampledProfile(samples=[sample])
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
        refs = [MethodRef("bulk.Targets", f"method{index:03d}", (), "void") for index in range(101)]
        methods = {1: entry, **{index + 2: ref for index, ref in enumerate(refs)}}
        graph = report_module.CallGraph(
            methods=methods,
            key_to_id={ref.canonical_id: method_id for method_id, ref in methods.items()},
            adjacency={
                1: [
                    {
                        "caller": 1,
                        "callee": index + 2,
                        "bci": str(index),
                        "is_direct": "true",
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
            report_module.SampledProfile(), graph, inventory, jacoco, max_listed=1000
        )
        self.assertEqual(len(first["uncoveredPaths"]), 101)
        self.assertEqual(len(first["promptTargetIds"]), 100)
        omitted = next(
            entry for entry in first["uncoveredPaths"]
            if entry["id"] not in first["promptTargetIds"]
        )
        attempted_states = {
            method_id: report_module.TargetState(status="attempted", attempt_count=1)
            for method_id in first["promptTargetIds"]
        }
        second, _ = report_module.correlate(
            report_module.SampledProfile(), graph, inventory, jacoco,
            max_listed=100, target_states=attempted_states,
        )
        self.assertEqual(second["promptTargetIds"][0], omitted["id"])
        rotated = next(entry for entry in second["uncoveredPaths"] if entry["id"] == omitted["id"])
        self.assertEqual(rotated["rank"], 101)
        self.assertEqual(rotated["attemptCount"], 0)


class ReportArtifactsTest(unittest.TestCase):

    def setUp(self) -> None:
        self.output_dir = tempfile.mkdtemp(prefix="near-call-report-")
        self.addCleanup(shutil.rmtree, self.output_dir, True)

    def _write_json(self, name: str, document: dict) -> str:
        path = os.path.join(self.output_dir, name)
        with open(path, "w", encoding="utf-8") as json_file:
            json.dump(document, json_file)
        return path

    def _generate(
            self,
            iteration: int = 1,
            *,
            profile_path: str | None = None,
            reports_dir: str = FIXTURES,
            api_inventory_path: str | None = None,
            coordinate: str = "com.example:demo:1.0.0",
            max_listed: int = report_module.MAX_LISTED_METHODS,
            target_state_paths: list[str] | None = None,
    ) -> dict:
        return report_module.generate_report(
            profile_path=profile_path or os.path.join(FIXTURES, "near_call_sampling.iprof"),
            reports_dir=reports_dir,
            api_inventory_path=api_inventory_path or os.path.join(
                FIXTURES, "near_call_inventory.json"
            ),
            jacoco_xml_paths=[JACOCO_PATH],
            coordinate=coordinate,
            iteration=iteration,
            output_dir=self.output_dir,
            max_listed=max_listed,
            target_state_paths=target_state_paths,
        )

    def test_report_is_compact_and_json_is_complete(self) -> None:
        report = self._generate()
        self.assertEqual(report["profileKind"], "sampled-guidance")
        self.assertEqual(len(report["uncoveredPaths"]), 6)
        for name in ("discovery-report-1.json", "discovery-report-1.md", "coverage-1.lcov"):
            self.assertTrue(os.path.isfile(os.path.join(self.output_dir, name)), name)
        with open(os.path.join(self.output_dir, "discovery-report-1.md"), encoding="utf-8") as md_file:
            markdown = md_file.read()
        self.assertIn("## Observed (sampled guidance only)", markdown)
        self.assertIn("## Uncovered paths (JaCoCo-exact, top 100)", markdown)
        navigation = (
            "Observed:\n"
            "`Registry.init()`\n\n"
            "Uncovered paths:\n"
            "`Registry.init() → resolve(...)`\n"
            "`Registry.init() → resolve(...) → load(...)`\n"
            "`Registry.init() → resolve(...) → load(...) → reload()`\n"
        )
        public_navigation = (
            "Public entry:\n"
            "`Config.of()`\n\n"
            "Uncovered paths:\n"
            "`Config.of() → parse(...)`\n"
        )
        self.assertIn(navigation, markdown)
        self.assertIn(public_navigation, markdown)
        path_lines = [line for line in markdown.splitlines() if line.startswith("`")]
        for line in path_lines:
            self.assertNotIn("#", line)
            self.assertNotIn(":void", line)
        instruction = (
            "Attempt every listed uncovered path in this iteration through public API "
            "behavior; never invoke internal targets directly."
        )
        self.assertEqual(markdown.count(instruction), 1)
        self.assertNotIn("sibling", markdown)
        self.assertNotIn(" samples to ", markdown)
        self.assertNotIn(" step(s)", markdown)
        self.assertNotIn("###", markdown)
        self.assertEqual(markdown.count("additional uncovered paths are retained in JSON"), 1)
        self.assertNotIn("Detailed near-call guidance", markdown)

    def test_markdown_shows_next_sampled_frame_and_all_selected_groups(self) -> None:
        root = MethodRef("example.CoverageTest", "run", (), "void")
        join = MethodRef("example.Library", "dispatch", (), "void")
        observed = MethodRef("example.Library", "parseJson", (), "void")
        targets = [
            MethodRef("example.Library", f"parseAlternative{index}", (), "void")
            for index in range(21)
        ]
        methods = {
            1: root,
            2: join,
            3: observed,
            **{index + 4: target for index, target in enumerate(targets)},
        }
        graph = report_module.CallGraph(
            methods=methods,
            key_to_id={ref.canonical_id: method_id for method_id, ref in methods.items()},
        )
        records: list[report_module.NearCallRecord] = []
        for index, target in enumerate(targets):
            sample = report_module.Sample(
                context_id=f"sample-{index}",
                raw_context="",
                path=[(1, 0), (2, 1), (3, 2)],
                full_path=[(root, 0), (join, 1), (observed, 2)],
                path_full_indexes=[0, 1, 2],
                count=1,
            )
            records.append(report_module.NearCallRecord(
                coverage=_coverage(target),
                target_id=index + 4,
                target_state=report_module.TargetState(),
                join_kind="sampled",
                static_path=[2, index + 4],
                static_path_edges=[],
                sample=sample,
                sampled_join_path_index=1,
            ))
        report = {
            "summary": {
                "inventoryCovered": 1,
                "inventoryUncovered": 0,
                "inventoryUnknown": 0,
                "deepCovered": 0,
                "deepUncovered": len(records),
                "listedUncovered": len(records),
                "omittedUncovered": 0,
                "samplingContexts": len(records),
                "totalSampleCount": len(records),
            },
            "caveats": [],
        }
        markdown_path = os.path.join(self.output_dir, "all-groups.md")

        report_module.write_markdown(
            report,
            records,
            graph,
            "example:library:1",
            0,
            None,
            markdown_path,
        )

        with open(markdown_path, encoding="utf-8") as markdown_file:
            markdown = markdown_file.read()
        first_group = (
            "Observed:\n"
            "`Library.dispatch() → parseJson()`\n\n"
            "Uncovered paths:\n"
            "`Library.dispatch() → parseAlternative0()`\n"
        )
        self.assertIn(first_group, markdown)
        self.assertEqual(markdown.count("Observed:\n"), 21)
        self.assertIn(
            "`Library.dispatch() → parseAlternative20()`",
            markdown,
        )
        self.assertNotIn("###", markdown)
        self.assertNotIn("sibling", markdown)
        self.assertNotIn(" step(s)", markdown)

    def test_target_state_is_retained_and_terminal_targets_are_not_prompted(self) -> None:
        state_path = self._write_json("deep-cover-0.json", {
            "coordinate": "com.example:demo:1.0.0",
            "targets": [
                {
                    "id": RESOLVE_INTEGER_ID,
                    "status": "completed",
                    "attemptCount": 1,
                    "lastAttemptedIteration": 1,
                },
                {
                    "id": PARSE_ID,
                    "status": "skipped",
                    "attemptCount": 0,
                    "reason": "No supported public behavior reaches this branch.",
                },
                {
                    "id": LOAD_ID,
                    "status": "exhausted",
                    "attemptCount": 2,
                    "lastAttemptedIteration": 1,
                    "reason": "Meaningful inputs were exhausted.",
                },
                {
                    "id": RELOAD_ID,
                    "status": "attempted",
                    "attemptCount": 3,
                    "lastAttemptedIteration": 1,
                },
            ],
        })

        report = self._generate(iteration=0, target_state_paths=[state_path])

        by_id = {entry["id"]: entry for entry in report["uncoveredPaths"]}
        self.assertEqual(report["promptTargetIds"], [RESOLVE_ID, RELOAD_ID])
        self.assertEqual(report["summary"]["terminalUncovered"], 2)
        for method_id in (PARSE_ID, LOAD_ID):
            self.assertTrue(by_id[method_id]["terminal"])
            self.assertNotIn(method_id, report["promptTargetIds"])
        self.assertEqual(by_id[RELOAD_ID]["targetStatus"], "attempted")
        self.assertEqual(by_id[RELOAD_ID]["attemptCount"], 3)
        self.assertFalse(by_id[RELOAD_ID]["terminal"])
        persisted = {entry["id"]: entry for entry in report["targetStates"]}
        self.assertEqual(persisted[RESOLVE_INTEGER_ID]["status"], "completed")
        self.assertEqual(persisted[PARSE_ID]["status"], "skipped")
        self.assertEqual(persisted[LOAD_ID]["status"], "exhausted")

    def test_target_state_rejects_unknown_status(self) -> None:
        state_path = self._write_json("bad-state.json", {
            "coordinate": "com.example:demo:1.0.0",
            "targets": [{
                "id": RESOLVE_ID,
                "status": "mystery",
                "attemptCount": 0,
            }],
        })

        with self.assertRaisesRegex(report_module.ProfileFormatError, "unknown status"):
            self._generate(iteration=0, target_state_paths=[state_path])

    def test_target_state_cannot_override_jacoco_or_reference_non_deep_ids(self) -> None:
        cases = (
            (
                "completed-uncovered",
                RESOLVE_ID,
                "completed",
                "completed but current JaCoCo reports it uncovered",
            ),
            (
                "public-api",
                INIT_ID,
                "attempted",
                "not in the current deep JaCoCo universe",
            ),
            (
                "unknown-id",
                "com.example.Registry#typo():void",
                "attempted",
                "not in the current deep JaCoCo universe",
            ),
        )
        for name, method_id, status, error_pattern in cases:
            with self.subTest(name=name):
                state_path = self._write_json(f"{name}.json", {
                    "coordinate": "com.example:demo:1.0.0",
                    "targets": [{
                        "id": method_id,
                        "status": status,
                        "attemptCount": 1,
                    }],
                })
                with self.assertRaisesRegex(
                        report_module.ProfileFormatError,
                        error_pattern,
                ):
                    self._generate(iteration=0, target_state_paths=[state_path])


    def test_input_paths_and_invalid_json_fail_closed(self) -> None:
        missing = os.path.join(self.output_dir, "missing")
        with self.assertRaisesRegex(report_module.ProfileFormatError, "call-tree CSVs"):
            report_module.load_call_graph(missing)

        graph = report_module.load_call_graph(FIXTURES)
        with self.assertRaisesRegex(report_module.ProfileFormatError, "sampled profile"):
            report_module.load_sampled_profile(missing, graph)
        with self.assertRaisesRegex(report_module.ProfileFormatError, "API inventory"):
            self._generate(iteration=0, api_inventory_path=missing)

        invalid_json = os.path.join(self.output_dir, "invalid-inventory.json")
        with open(invalid_json, "w", encoding="utf-8") as invalid_file:
            invalid_file.write("{")
        with self.assertRaisesRegex(report_module.ProfileFormatError, "Invalid JSON"):
            self._generate(iteration=0, api_inventory_path=invalid_json)

    def test_inventory_coordinate_is_required_and_must_match(self) -> None:
        inventories = (
            ("missing-coordinate.json", {"targets": []}),
            (
                "wrong-coordinate.json",
                {"coordinate": "com.example:other:1.0.0", "targets": []},
            ),
        )
        for name, document in inventories:
            with self.subTest(name=name):
                path = self._write_json(name, document)
                with self.assertRaisesRegex(report_module.ProfileFormatError, "coordinate"):
                    self._generate(iteration=0, api_inventory_path=path)

    def test_report_zero_is_valid_and_negative_options_are_rejected(self) -> None:
        baseline = self._generate(iteration=0)
        self.assertEqual(baseline["iteration"], 0)
        self.assertTrue(os.path.isfile(
            os.path.join(self.output_dir, "discovery-report-0.json")
        ))

        next_report = self._generate(iteration=1)
        next_paths = {entry["id"]: entry for entry in next_report["uncoveredPaths"]}
        for method_id in baseline["promptTargetIds"]:
            self.assertEqual(next_paths[method_id]["attemptCount"], 1)

        with self.assertRaisesRegex(report_module.ProfileFormatError, "iteration"):
            self._generate(iteration=-1)
        with self.assertRaisesRegex(report_module.ProfileFormatError, "max_listed"):
            self._generate(iteration=0, max_listed=0)


    def test_lcov_contains_only_positive_sampled_observations(self) -> None:
        self._generate()
        with open(os.path.join(self.output_dir, "coverage-1.lcov"), encoding="utf-8") as lcov_file:
            lcov = lcov_file.read()
        self.assertIn("TN:sampled-pgo-guidance", lcov)
        self.assertIn(f"FNDA:42,{INIT_ID}", lcov)
        self.assertNotIn("FNDA:0", lcov)
        self.assertNotIn(f"FNDA:42,{LOAD_ID}", lcov)

    def test_lcov_preserves_overload_identity(self) -> None:
        graph = report_module.load_call_graph(FIXTURES)
        profile = report_module.SampledProfile(sample_counts={
            graph.key_to_id[RESOLVE_ID]: 2,
            graph.key_to_id[RESOLVE_INTEGER_ID]: 3,
        })
        lcov_path = os.path.join(self.output_dir, "overloads.lcov")

        report_module.write_lcov(profile, graph, _load_jacoco(), lcov_path)

        with open(lcov_path, encoding="utf-8") as lcov_file:
            lcov = lcov_file.read()
        self.assertIn(f"FNDA:2,{RESOLVE_ID}", lcov)
        self.assertIn(f"FNDA:3,{RESOLVE_INTEGER_ID}", lcov)

    def test_missing_jacoco_evidence_is_rejected(self) -> None:
        with self.assertRaises(JacocoReportError):
            report_module.generate_report(
                profile_path=os.path.join(FIXTURES, "near_call_sampling.iprof"),
                reports_dir=FIXTURES,
                api_inventory_path=os.path.join(FIXTURES, "near_call_inventory.json"),
                jacoco_xml_paths=[],
                coordinate="com.example:demo:1.0.0",
                iteration=1,
                output_dir=self.output_dir,
            )


if __name__ == "__main__":
    unittest.main()
