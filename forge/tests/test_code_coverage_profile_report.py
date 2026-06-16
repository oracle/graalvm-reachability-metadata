# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import json
import os
import tempfile
import unittest

from utility_scripts import code_coverage_profile_report as report_module
from utility_scripts.code_coverage_model import (
    method_ref_from_iprof,
    normalize_type_name,
    parse_fqn_method_label,
    parse_inventory_id,
)

FIXTURES = os.path.join(os.path.dirname(__file__), "fixtures", "code_coverage")


class ModelTests(unittest.TestCase):
    def test_normalize_array_descriptors(self) -> None:
        self.assertEqual(normalize_type_name("[B"), "byte[]")
        self.assertEqual(normalize_type_name("[Ljava.lang.String;"), "java.lang.String[]")
        self.assertEqual(normalize_type_name("[[I"), "int[][]")
        self.assertEqual(normalize_type_name("java.lang.String"), "java.lang.String")

    def test_iprof_signature_is_decl_ret_params(self) -> None:
        types = {40: "com.example.Greeter", 1: "void", 20: "java.lang.String"}
        ref = method_ref_from_iprof({"id": 1, "name": "<init>", "signature": [40, 1, 20]}, types)
        self.assertEqual(ref.canonical_id, "com.example.Greeter#<init>(java.lang.String):void")

    def test_call_tree_and_inventory_labels_agree(self) -> None:
        tree = parse_fqn_method_label("app:com.example.Greeter.greet():java.lang.String")
        inv = parse_inventory_id("com.example.Greeter#greet():java.lang.String")
        self.assertEqual(tree.canonical_id, inv.canonical_id)

    def test_used_methods_origin_prefix_dropped(self) -> None:
        ref = parse_fqn_method_label("app->null:com.example.Greeter.shout():java.lang.String")
        self.assertEqual(ref.canonical_id, "com.example.Greeter#shout():java.lang.String")


class ProfileParsingTests(unittest.TestCase):
    def test_executed_set_comes_from_contexts_not_symbol_table(self) -> None:
        profile = report_module.load_profile(os.path.join(FIXTURES, "demo.iprof"))
        self.assertIn("com.example.Greeter#<init>(java.lang.String):void", profile.executed)
        self.assertIn("com.example.Greeter#greet():java.lang.String", profile.executed)
        self.assertIn("com.example.Demo#main(java.lang.String[]):void", profile.executed)
        # shout and toUpperCase are in the methods symbol table but never in a
        # context chain, so they must not be counted as executed.
        self.assertNotIn("com.example.Greeter#shout():java.lang.String", profile.executed)
        self.assertNotIn("java.lang.String#toUpperCase():java.lang.String", profile.executed)

    def test_observed_edges_are_caller_to_callee(self) -> None:
        profile = report_module.load_profile(os.path.join(FIXTURES, "demo.iprof"))
        # ctx "1:0<4:5" => method 4 (main) calls method 1 (<init>): edge main -> <init>.
        self.assertIn(
            ("com.example.Demo#main(java.lang.String[]):void", "com.example.Greeter#<init>(java.lang.String):void"),
            profile.observed_edges,
        )

    def test_sampled_profile_is_rejected(self) -> None:
        with self.assertRaises(report_module.SampledProfileError):
            report_module.load_profile(os.path.join(FIXTURES, "demo_sampled.iprof"))


class CorrelationTests(unittest.TestCase):
    def _run(self) -> dict:
        with open(os.path.join(FIXTURES, "demo_api_inventory.json"), encoding="utf-8") as inv_file:
            inventory = json.load(inv_file)
        profile = report_module.load_profile(os.path.join(FIXTURES, "demo.iprof"))
        graph = report_module.load_call_graph(os.path.join(FIXTURES, "demo_call_tree.txt"), None)
        return report_module.correlate(profile, graph, inventory)

    def test_inventory_classification(self) -> None:
        report = self._run()
        status = {entry["id"]: entry["status"] for entry in report["inventory"]}
        self.assertEqual(status["com.example.Greeter#<init>(java.lang.String):void"], "covered")
        self.assertEqual(status["com.example.Greeter#greet():java.lang.String"], "covered")
        # shout is reachable in the call graph but never observed -> actionable.
        self.assertEqual(status["com.example.Greeter#shout():java.lang.String"], "reachable-uncovered")

    def test_deep_discovery_finds_reaching_path_to_public_entry(self) -> None:
        report = self._run()
        ids = {item["id"]: item for item in report["discoveryTargets"]}
        # whisper is a library method, reachable from greet, never observed, and
        # not itself an inventory target -> a deep discovery target.
        self.assertIn("com.example.Greeter#whisper():java.lang.String", ids)
        whisper = ids["com.example.Greeter#whisper():java.lang.String"]
        self.assertEqual(whisper["nearestPublicEntry"], "com.example.Greeter#greet():java.lang.String")
        self.assertEqual(
            whisper["reachingPath"],
            ["com.example.Greeter#greet():java.lang.String", "com.example.Greeter#whisper():java.lang.String"],
        )
        # JDK methods (java.lang.String#toUpperCase) must not be discovery targets.
        self.assertNotIn("java.lang.String#toUpperCase():java.lang.String", ids)

    def test_report_artifacts_written(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            report = report_module.generate_report(
                profile_path=os.path.join(FIXTURES, "demo.iprof"),
                call_tree_path=os.path.join(FIXTURES, "demo_call_tree.txt"),
                api_inventory_path=os.path.join(FIXTURES, "demo_api_inventory.json"),
                coordinate="com.example:demo:1.0.0",
                iteration=1,
                output_dir=tmp,
                used_methods_path=None,
            )
            self.assertEqual(report["profileKind"], "instrumented")
            for name in ("discovery-report-1.json", "discovery-report-1.md", "coverage-1.lcov"):
                self.assertTrue(os.path.isfile(os.path.join(tmp, name)), name)
            with open(os.path.join(tmp, "coverage-1.lcov"), encoding="utf-8") as lcov_file:
                lcov = lcov_file.read()
            self.assertIn("SF:src/main/java/com/example/Greeter.java", lcov)
            self.assertIn("FNDA:", lcov)


class RealCallTreeFixtureTests(unittest.TestCase):
    def test_parses_shipped_call_tree_slice(self) -> None:
        # The 120-line slice captured from a real `native-image
        # -H:+PrintAnalysisCallTree` run must parse into reachable methods.
        path = os.path.join(FIXTURES, "sample_call_tree.txt")
        if not os.path.isfile(path):
            self.skipTest("sample_call_tree.txt fixture not present")
        graph = report_module.load_call_graph(path, None)
        self.assertGreater(len(graph.reachable), 5)
        # Every reachable id is a canonical owner#name(...) form.
        self.assertTrue(all("#" in canonical for canonical in graph.reachable))


if __name__ == "__main__":
    unittest.main()
