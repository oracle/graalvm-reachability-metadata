# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Tests for the sampled-PGO near-call analyzer (§WF-code-coverage-improvement)."""

import json
import os
import shutil
import tempfile
import unittest

from utility_scripts.code_coverage_model import (
    method_ref_from_call_tree_row,
    method_ref_from_iprof,
    normalize_type_name,
    parse_inventory_id,
)
from utility_scripts.code_coverage_profile_report import (
    MAX_LISTED_METHODS,
    ProfileFormatError,
    correlate,
    generate_report,
    load_call_graph,
    load_sampled_profile,
)

FIXTURES = os.path.join(os.path.dirname(__file__), "fixtures", "code_coverage")

LOAD_ID = "com.example.Registry#load(java.lang.String):com.example.Driver"
RELOAD_ID = "com.example.Registry#reload():void"
PARSE_ID = "com.example.parser.Config#parse(java.lang.String):com.example.parser.Config"
ORPHAN_ID = "com.example.Registry#orphan(int):void"
UNREACHABLE_ID = "com.example.Registry#unreachable():void"


def _load_inventory() -> dict:
    with open(os.path.join(FIXTURES, "near_call_inventory.json"), encoding="utf-8") as inventory_file:
        return json.load(inventory_file)


def _load_cover_report() -> dict:
    with open(os.path.join(FIXTURES, "near_call_cover_report.json"), encoding="utf-8") as cover_file:
        return json.load(cover_file)


class MethodIdentityTest(unittest.TestCase):

    def test_normalize_array_descriptors(self) -> None:
        self.assertEqual(normalize_type_name("[B"), "byte[]")
        self.assertEqual(normalize_type_name("[Ljava.lang.String;"), "java.lang.String[]")
        self.assertEqual(normalize_type_name("java/lang/String"), "java.lang.String")

    def test_iprof_signature_is_decl_ret_params(self) -> None:
        types = {1: "void", 30: "com.example.Registry", 31: "java.lang.String"}
        ref = method_ref_from_iprof({"id": 7, "name": "resolve", "signature": [30, 1, 31]}, types)
        self.assertEqual(ref.canonical_id, "com.example.Registry#resolve(java.lang.String):void")

    def test_call_tree_row_and_inventory_id_agree(self) -> None:
        row = {"Type": "com.example.Registry", "Name": "load",
               "Parameters": "java.lang.String", "Return": "com.example.Driver"}
        self.assertEqual(method_ref_from_call_tree_row(row).canonical_id,
                         parse_inventory_id(LOAD_ID).canonical_id)

    def test_call_tree_row_empty_parameters_sentinel(self) -> None:
        row = {"Type": "com.example.Registry", "Name": "init", "Parameters": "empty", "Return": "void"}
        self.assertEqual(method_ref_from_call_tree_row(row).canonical_id,
                         "com.example.Registry#init():void")


class CallGraphAndProfileTest(unittest.TestCase):

    def test_call_graph_csv_loaded_via_prefix_match(self) -> None:
        graph = load_call_graph(FIXTURES)
        self.assertEqual(len(graph.methods), 9)
        self.assertIn("com.example.Registry#load(java.lang.String):com.example.Driver", graph.key_to_id)
        load_id = graph.key_to_id[LOAD_ID]
        self.assertEqual(len(graph.reverse_adjacency[load_id]), 1)

    def test_instrumented_profile_is_rejected(self) -> None:
        graph = load_call_graph(FIXTURES)
        with self.assertRaises(ProfileFormatError):
            load_sampled_profile(os.path.join(FIXTURES, "near_call_instrumented.iprof"), graph)

    def test_sampled_stack_maps_root_first(self) -> None:
        graph = load_call_graph(FIXTURES)
        profile = load_sampled_profile(os.path.join(FIXTURES, "near_call_sampling.iprof"), graph)
        self.assertEqual(profile.total_sample_count, 42)
        self.assertEqual(len(profile.samples), 1)
        sample = profile.samples[0]
        owners = [graph.methods[static_id].name for static_id, _ in sample.path]
        self.assertEqual(owners, ["run", "testInit", "init"])


class CorrelateTest(unittest.TestCase):

    def setUp(self) -> None:
        self.graph = load_call_graph(FIXTURES)
        self.profile = load_sampled_profile(os.path.join(FIXTURES, "near_call_sampling.iprof"), self.graph)

    def test_statuses_come_from_jacoco_cover_report(self) -> None:
        report, _ = correlate(self.profile, self.graph, _load_inventory(), _load_cover_report(),
                              MAX_LISTED_METHODS)
        summary = report["summary"]
        self.assertEqual(summary["coverageSource"], "jacoco")
        self.assertEqual(summary["covered"], 2)
        self.assertEqual(summary["uncovered"], 4)
        self.assertEqual(summary["excluded"], 1)
        statuses = {entry["id"]: entry["status"] for entry in report["inventory"]}
        self.assertEqual(statuses[UNREACHABLE_ID], "excluded")

    def test_sampled_fallback_when_no_cover_report(self) -> None:
        report, _ = correlate(self.profile, self.graph, _load_inventory(), None, MAX_LISTED_METHODS)
        self.assertEqual(report["summary"]["coverageSource"], "sampled-fallback")
        statuses = {entry["id"]: entry["status"] for entry in report["inventory"]}
        # Config.of() ran under JaCoCo but was never sampled: fallback marks it uncovered.
        self.assertEqual(statuses["com.example.parser.Config#of():com.example.parser.Config"], "uncovered")

    def test_ranking_is_nearest_sampled_join_first(self) -> None:
        report, records = correlate(self.profile, self.graph, _load_inventory(), _load_cover_report(),
                                    MAX_LISTED_METHODS)
        self.assertEqual([entry["id"] for entry in report["bulkTargets"]],
                         [LOAD_ID, RELOAD_ID, PARSE_ID, ORPHAN_ID])
        by_id = {entry["id"]: entry for entry in report["bulkTargets"]}
        self.assertEqual(by_id[LOAD_ID]["joinKind"], "sampled")
        self.assertEqual(by_id[LOAD_ID]["stepsRemaining"], 2)
        self.assertEqual(by_id[RELOAD_ID]["stepsRemaining"], 3)
        self.assertEqual(by_id[PARSE_ID]["joinKind"], "entry")
        self.assertEqual(by_id[PARSE_ID]["stepsRemaining"], 1)
        self.assertEqual(by_id[ORPHAN_ID]["joinKind"], "none")

    def test_sampled_join_prefers_non_test_frame(self) -> None:
        _, records = correlate(self.profile, self.graph, _load_inventory(), _load_cover_report(),
                               MAX_LISTED_METHODS)
        load_record = next(record for record in records
                           if record.target_ref.canonical_id == LOAD_ID)
        # Both testInit and init are on the sampled stack; init has higher
        # prompt quality, so the join starts there, not at the test frame.
        self.assertEqual(self.graph.methods[load_record.static_path[0]].name, "init")
        self.assertEqual(load_record.sample_count, 42)

    def test_bulk_list_is_capped(self) -> None:
        report, records = correlate(self.profile, self.graph, _load_inventory(), _load_cover_report(),
                                    max_listed=2)
        self.assertEqual(report["summary"]["listedUncovered"], 2)
        self.assertEqual(report["summary"]["omittedUncovered"], 2)
        self.assertEqual(len(records), 2)
        self.assertEqual(MAX_LISTED_METHODS, 100)


class ReportArtifactsTest(unittest.TestCase):

    def setUp(self) -> None:
        self.output_dir = tempfile.mkdtemp(prefix="near-call-report-")
        self.addCleanup(shutil.rmtree, self.output_dir, True)

    def _generate(self, iteration: int, cover_report_name: str | None = "near_call_cover_report.json") -> dict:
        cover_path = os.path.join(FIXTURES, cover_report_name) if cover_report_name else None
        return generate_report(
            profile_path=os.path.join(FIXTURES, "near_call_sampling.iprof"),
            reports_dir=FIXTURES,
            api_inventory_path=os.path.join(FIXTURES, "near_call_inventory.json"),
            api_cover_report_path=cover_path,
            coordinate="com.example:demo:1.0.0",
            iteration=iteration,
            output_dir=self.output_dir,
        )

    def test_report_artifacts_written(self) -> None:
        self._generate(1)
        for name in ("discovery-report-1.json", "discovery-report-1.md", "coverage-1.lcov"):
            self.assertTrue(os.path.isfile(os.path.join(self.output_dir, name)), name)
        with open(os.path.join(self.output_dir, "discovery-report-1.md"), encoding="utf-8") as md_file:
            markdown = md_file.read()
        self.assertIn("## Uncovered public API methods (grouped by class, nearest-first, max 100)", markdown)
        self.assertIn("### com.example.Registry — 3 uncovered", markdown)
        self.assertIn("### com.example.parser.Config — 1 uncovered", markdown)
        self.assertIn("## Detailed near-call guidance", markdown)
        self.assertIn("existing test entry point", markdown)
        self.assertIn("sampled/call-graph join point", markdown)
        self.assertIn("Static steps still missing: 2", markdown)
        self.assertIn("candidate skip/exhaust", markdown)

    def test_lcov_uses_jacoco_covered_status(self) -> None:
        self._generate(1)
        with open(os.path.join(self.output_dir, "coverage-1.lcov"), encoding="utf-8") as lcov_file:
            lcov = lcov_file.read()
        self.assertIn("FNDA:1,init", lcov)
        self.assertIn("FNDA:0,load", lcov)

    def test_progress_section_reports_newly_covered(self) -> None:
        self._generate(1)
        # Iteration 2: load() flipped to covered in the JaCoCo report.
        updated = _load_cover_report()
        for target in updated["targets"]:
            if target["id"] == LOAD_ID:
                target["status"] = "covered"
        updated_path = os.path.join(self.output_dir, "cover-report-2.json")
        with open(updated_path, "w", encoding="utf-8") as cover_file:
            json.dump(updated, cover_file)
        generate_report(
            profile_path=os.path.join(FIXTURES, "near_call_sampling.iprof"),
            reports_dir=FIXTURES,
            api_inventory_path=os.path.join(FIXTURES, "near_call_inventory.json"),
            api_cover_report_path=updated_path,
            coordinate="com.example:demo:1.0.0",
            iteration=2,
            output_dir=self.output_dir,
        )
        with open(os.path.join(self.output_dir, "discovery-report-2.md"), encoding="utf-8") as md_file:
            markdown = md_file.read()
        self.assertIn("## Progress since the previous report", markdown)
        self.assertIn(f"- Newly covered (1):\n  - `{LOAD_ID}`", markdown)


if __name__ == "__main__":
    unittest.main()
