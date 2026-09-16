# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Tests for call-graph loading, sampled-profile mapping, and node translation."""

import json
import os
import shutil
import tempfile
import unittest

from utility_scripts.code_coverage_jacoco import JacocoMethodCoverage
from utility_scripts.code_coverage_model import (
    MethodRef,
    method_ref_from_call_tree_row,
    method_ref_from_iprof,
    normalize_type_name,
    parse_inventory_id,
)
from utility_scripts import code_coverage_profile_graph as graph_module
from utility_scripts import code_coverage_profile_records as records_module
from utility_scripts import code_coverage_profile_render as render_module
from utility_scripts import code_coverage_profile_report as report_module
from utility_scripts import code_coverage_profile_routes as routes_module
from utility_scripts.code_coverage_profile_inputs import ProfileFormatError

from tests.code_coverage_profile_support import (
    FIXTURES,
    INIT_ID,
    LOAD_ID,
    RESOLVE_ID,
    _coverage,
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
        graph = graph_module.load_call_graph(FIXTURES)
        self.assertEqual(len(graph.methods), 10)
        self.assertIn(LOAD_ID, graph.key_to_id)
        self.assertEqual(len(graph.loose_to_ids["com.example.Registry#resolve/1"]), 2)

    def test_call_graph_maps_invoke_bci_through_library_line_table(self) -> None:
        graph = graph_module.load_call_graph(
            FIXTURES,
            line_numbers={INIT_ID: ((0, 7), (8, 11), (20, 18))},
        )

        target_id: int = graph.key_to_id[RESOLVE_ID]
        edge: dict = next(
            candidate
            for candidate in graph.reverse_adjacency[target_id]
            if candidate["caller"] == graph.key_to_id[INIT_ID]
        )
        self.assertEqual(edge["bci"], "10")
        self.assertEqual(edge["source_line"], 11)

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

            graph = graph_module.load_call_graph(reports_dir)

        self.assertEqual(len(graph.methods), 10)
        self.assertIn(LOAD_ID, graph.key_to_id)

    def test_instrumented_profile_is_rejected(self) -> None:
        graph = graph_module.load_call_graph(FIXTURES)
        with self.assertRaises(ProfileFormatError):
            routes_module.load_sampled_profile(
                os.path.join(FIXTURES, "near_call_instrumented.iprof"), graph
            )

    def test_sampled_stack_maps_root_first_and_counts_positive_observations(self) -> None:
        graph = graph_module.load_call_graph(FIXTURES)
        profile = routes_module.load_sampled_profile(
            os.path.join(FIXTURES, "near_call_sampling.iprof"), graph
        )
        self.assertEqual(profile.total_sample_count, 42)
        self.assertEqual(profile.samples[0].context_id, "sample-1")
        names = [graph.methods[static_id].name for static_id, _ in profile.samples[0].path]
        self.assertEqual(names, ["run", "testInit", "init"])
        self.assertEqual(profile.sample_counts[graph.key_to_id[INIT_ID]], 42)

    def test_non_positive_sample_contexts_are_not_observations(self) -> None:
        graph = graph_module.load_call_graph(FIXTURES)
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
            profile = routes_module.load_sampled_profile(profile_path, graph)

        self.assertEqual(profile.context_count, 1)
        self.assertEqual(profile.total_sample_count, 42)
        self.assertEqual(len(profile.samples), 1)
        self.assertEqual(profile.samples[0].context_id, "sample-3")
        self.assertEqual(len(profile.sample_counts), 3)
        self.assertEqual(set(profile.sample_counts.values()), {42})

    def test_ambiguous_loose_overload_is_not_mapped(self) -> None:
        graph = graph_module.load_call_graph(FIXTURES)
        ambiguous = MethodRef("com.example.Registry", "resolve", ("unknown.Type",), "void")
        self.assertIsNone(graph_module._resolve_graph_id(graph, ambiguous))


class SyntheticLambdaTest(unittest.TestCase):
    """Lambda attribution and route honesty (§AR-code-coverage-improvement.4.2.1).

    The graph models one closure end to end: `reload` captures it, the generated
    class carries it, its body calls `persist`, and an unrelated `drain` invokes
    the functional interface alongside a real implementation.
    """

    METHODS = [
        (1, "reload", "com.example.Registry", "empty", "void"),
        (2, "<init>", "com.example.Registry$$Lambda/0x1", "com.example.Registry", "void"),
        (3, "run", "com.example.Registry$$Lambda/0x1", "empty", "void"),
        (4, "lambda$reload$0", "com.example.Registry", "empty", "void"),
        (5, "drain", "com.example.Worker", "empty", "void"),
        (6, "run", "com.example.RealTask", "empty", "void"),
        (7, "init", "com.example.Registry", "empty", "void"),
        (8, "submit", "com.example.Executor", "java.lang.Runnable", "void"),
        (9, "persist", "com.example.Registry", "empty", "void"),
    ]
    #: `(invoke id, caller, direct, [targets])`.
    INVOKES = [
        (1, 7, "true", [1]),
        (2, 1, "true", [2]),
        (3, 1, "true", [8]),
        (4, 3, "true", [4]),
        (5, 5, "false", [3, 6]),
        (6, 7, "true", [5]),
        (7, 4, "true", [9]),
    ]

    INIT = "com.example.Registry#init():void"
    RELOAD = "com.example.Registry#reload():void"
    BODY = "com.example.Registry#lambda$reload$0():void"
    PERSIST = "com.example.Registry#persist():void"
    REAL_RUN = "com.example.RealTask#run():void"
    DRAIN = "com.example.Worker#drain():void"

    def setUp(self) -> None:
        self.directory = tempfile.mkdtemp(prefix="synthetic-lambda-")
        self.addCleanup(shutil.rmtree, self.directory)
        with open(
                os.path.join(self.directory, "call_tree_methods_demo.csv"),
                "w", encoding="utf-8",
        ) as handle:
            handle.write("Id,Name,Type,Parameters,Return,Display,Flags,IsEntryPoint\n")
            for static_id, name, owner, params, return_type in self.METHODS:
                handle.write(f"{static_id},{name},{owner},{params},{return_type},d,,false\n")
        with open(
                os.path.join(self.directory, "call_tree_invokes_demo.csv"),
                "w", encoding="utf-8",
        ) as handle:
            handle.write("Id,MethodId,BytecodeIndexes,IsDirect\n")
            for invoke_id, caller, direct, _ in self.INVOKES:
                handle.write(f"{invoke_id},{caller},{invoke_id},{direct}\n")
        with open(
                os.path.join(self.directory, "call_tree_targets_demo.csv"),
                "w", encoding="utf-8",
        ) as handle:
            handle.write("InvokeId,TargetId\n")
            for invoke_id, _, _, targets in self.INVOKES:
                for target in targets:
                    handle.write(f"{invoke_id},{target}\n")

        self.graph = graph_module.load_call_graph(self.directory)
        covered: set[str] = {self.INIT, self.DRAIN}
        self.jacoco = {
            ref.canonical_id: _coverage(ref, covered=ref.canonical_id in covered)
            for ref in self.graph.methods.values()
        }
        self.report, self.records = report_module.correlate(
            routes_module.SampledProfile(),
            self.graph,
            {"targets": [{"id": self.INIT, "kind": "method"}]},
            self.jacoco,
        )
        self.paths = {entry["id"]: entry for entry in self.report["uncoveredPaths"]}

    def test_lambda_body_stays_in_the_denominator_but_never_reaches_the_prompt(self) -> None:
        self.assertIn(self.BODY, {entry["id"] for entry in self.report["deepMethods"]})
        self.assertNotIn(self.BODY, self.report["promptTargetIds"])
        self.assertIn(self.RELOAD, self.report["promptTargetIds"])
        self.assertEqual(self.report["summary"]["deepSyntheticMethods"], 1)
        self.assertEqual(self.report["summary"]["deepSyntheticUncovered"], 1)
        self.assertEqual(self.report["summary"]["syntheticExcludedFromPrompt"], 1)

    def test_closure_count_moves_onto_the_creating_method(self) -> None:
        reload_entry = self.paths[self.RELOAD]
        self.assertEqual(reload_entry["closures"], {"total": 1, "unexecuted": 1})
        self.assertIsNone(self.paths[self.PERSIST]["closures"])

    def test_prompt_paths_carry_no_compiler_owned_name(self) -> None:
        persist = self.paths[self.PERSIST]
        self.assertEqual(persist["reachingPath"], [self.INIT, self.RELOAD, self.PERSIST])
        self.assertIn(self.BODY, persist["reachingPathRaw"])
        rendered = render_module._display_path(
            [self.graph.key_to_id[method_id] for method_id in persist["reachingPathRaw"]],
            self.graph,
        )
        self.assertNotIn("lambda$", rendered)
        self.assertNotIn("$$Lambda", rendered)

    def test_route_uses_the_creation_edge_and_reports_the_hand_off(self) -> None:
        persist = self.paths[self.PERSIST]
        self.assertEqual(
            [edge["kind"] for edge in persist["edges"]], ["call", "creation", "call"]
        )
        self.assertEqual(persist["handOff"], "Executor.submit")

    def test_dispatch_stays_in_the_graph_but_never_carries_a_route(self) -> None:
        drain_edges = self.graph.adjacency[self.graph.key_to_id[self.DRAIN]]
        self.assertEqual({edge["kind"] for edge in drain_edges}, {"dispatch"})
        self.assertIn(
            self.graph.key_to_id[self.REAL_RUN],
            {edge["callee"] for edge in drain_edges},
        )
        self.assertEqual(self.paths[self.REAL_RUN]["joinKind"], "none")
        self.assertNotIn(self.REAL_RUN, self.report["promptTargetIds"])

    def test_markdown_states_the_closure_and_the_thread_hand_off(self) -> None:
        markdown_path = os.path.join(self.directory, "deep.md")
        render_module.write_markdown(
            self.report, self.records, self.graph, "example:library:1", 0, None, markdown_path
        )
        with open(markdown_path, encoding="utf-8") as handle:
            markdown = handle.read()
        self.assertIn("1 closures, 1 never executed", markdown)
        self.assertIn("runs on another thread via `Executor.submit`", markdown)
        self.assertNotIn("lambda$", markdown)


class FactoryStubTranslationTest(unittest.TestCase):
    """Native Image factory paths use verified constructors.

    §AR-code-coverage-improvement.4.2.1
    """

    CALLER = MethodRef("com.example.Api", "start", (), "void")
    ALPHA_FACTORY = MethodRef(
        graph_module.FACTORY_METHOD_HOLDER,
        "AlphaEntry_generated",
        ("java.lang.String",),
        "com.example.AlphaEntry",
    )
    ALPHA_CONSTRUCTOR = MethodRef(
        "com.example.AlphaEntry", "<init>", ("java.lang.String",), "void",
    )
    ALPHA_TARGET = MethodRef("com.example.AlphaEntry", "read", (), "void")
    ZULU_FACTORY = MethodRef(
        graph_module.FACTORY_METHOD_HOLDER,
        "ZuluEntry_generated",
        (),
        "com.example.ZuluEntry",
    )
    ZULU_CONSTRUCTOR = MethodRef("com.example.ZuluEntry", "<init>", (), "void")
    ZULU_TARGET = MethodRef("com.example.ZuluEntry", "read", (), "void")
    UNMATCHED_FACTORY = MethodRef(
        graph_module.FACTORY_METHOD_HOLDER,
        "External_generated",
        (),
        "external.Entry",
    )

    def setUp(self) -> None:
        methods: dict[int, MethodRef] = {
            1: self.CALLER,
            2: self.ALPHA_FACTORY,
            3: self.ALPHA_CONSTRUCTOR,
            4: self.ALPHA_TARGET,
            5: self.ZULU_FACTORY,
            6: self.ZULU_TARGET,
            7: self.UNMATCHED_FACTORY,
        }

        def edge(caller: int, callee: int) -> dict:
            return {
                "caller": caller,
                "callee": callee,
                "bci": "",
                "is_direct": "true",
                "kind": "call",
            }

        self.graph = graph_module.CallGraph(
            methods=methods,
            key_to_id={
                ref.canonical_id: static_id for static_id, ref in methods.items()
            },
            adjacency={
                1: [edge(1, 2), edge(1, 5)],
                2: [edge(2, 3)],
                3: [edge(3, 4)],
                5: [edge(5, 6)],
            },
        )
        library_methods: set[str] = {
            self.ALPHA_CONSTRUCTOR.canonical_id,
            self.ZULU_CONSTRUCTOR.canonical_id,
        }
        graph_module._index_factory_stubs(self.graph, library_methods)
        jacoco: dict[str, JacocoMethodCoverage] = {
            ref.canonical_id: _coverage(ref)
            for ref in (self.ALPHA_TARGET, self.ZULU_TARGET)
        }
        self.report, _ = report_module.correlate(
            routes_module.SampledProfile(),
            self.graph,
            {"targets": [{"id": self.CALLER.canonical_id, "kind": "method"}]},
            jacoco,
        )
        self.paths: dict[str, dict] = {
            entry["id"]: entry for entry in self.report["uncoveredPaths"]
        }

    def test_verified_factories_render_as_constructors(self) -> None:
        alpha_path: str = render_module._display_path([1, 2, 3, 4], self.graph)
        zulu_path: str = render_module._display_path([1, 5, 6], self.graph)

        self.assertEqual(alpha_path, "Api.start() → AlphaEntry(...) → read()")
        self.assertEqual(zulu_path, "Api.start() → ZuluEntry() → read()")
        self.assertNotIn("FactoryMethodHolder", alpha_path)
        self.assertNotIn("FactoryMethodHolder", zulu_path)

    def test_semantic_distance_collapses_only_a_duplicate_constructor(self) -> None:
        alpha: dict = self.paths[self.ALPHA_TARGET.canonical_id]
        zulu: dict = self.paths[self.ZULU_TARGET.canonical_id]

        self.assertEqual(alpha["stepsRemaining"], 2)
        self.assertEqual(zulu["stepsRemaining"], 2)
        self.assertEqual(
            alpha["reachingPath"],
            [
                self.CALLER.canonical_id,
                self.ALPHA_CONSTRUCTOR.canonical_id,
                self.ALPHA_TARGET.canonical_id,
            ],
        )
        self.assertIn(self.ALPHA_FACTORY.canonical_id, alpha["reachingPathRaw"])
        self.assertIn(self.ALPHA_CONSTRUCTOR.canonical_id, alpha["reachingPathRaw"])
        self.assertEqual(len(alpha["reachingPathRaw"]), 4)
        self.assertEqual(len(zulu["reachingPathRaw"]), 3)

    def test_semantic_distance_controls_ranking(self) -> None:
        self.assertEqual(
            self.report["promptTargetIds"],
            [self.ALPHA_TARGET.canonical_id, self.ZULU_TARGET.canonical_id],
        )

    def test_route_selection_uses_semantic_distance(self) -> None:
        first_factory = MethodRef(
            graph_module.FACTORY_METHOD_HOLDER, "First_generated", (), "com.example.First",
        )
        first_constructor = MethodRef("com.example.First", "<init>", (), "void")
        second_factory = MethodRef(
            graph_module.FACTORY_METHOD_HOLDER, "Second_generated", (), "com.example.Second",
        )
        second_constructor = MethodRef("com.example.Second", "<init>", (), "void")
        raw_steps: list[MethodRef] = [
            MethodRef("com.example.Raw", name, (), "void")
            for name in ("one", "two", "three")
        ]
        target = MethodRef("com.example.Target", "hit", (), "void")
        refs: list[MethodRef] = [
            self.CALLER,
            first_factory,
            first_constructor,
            second_factory,
            second_constructor,
            *raw_steps,
            target,
        ]
        graph = graph_module.CallGraph(
            methods={index: ref for index, ref in enumerate(refs, start=1)},
            key_to_id={ref.canonical_id: index for index, ref in enumerate(refs, start=1)},
        )

        def add_edge(caller: int, callee: int) -> None:
            graph.adjacency.setdefault(caller, []).append({
                "caller": caller,
                "callee": callee,
                "kind": "call",
            })

        semantic_path: list[int] = [1, 2, 3, 4, 5, 9]
        raw_shorter_path: list[int] = [1, 6, 7, 8, 9]
        for path in (semantic_path, raw_shorter_path):
            for caller, callee in zip(path, path[1:]):
                add_edge(caller, callee)
        graph_module._index_factory_stubs(
            graph,
            {first_constructor.canonical_id, second_constructor.canonical_id},
        )

        routes = routes_module._public_entry_routes(graph, [self.CALLER])
        selected, _ = routes_module._route_to(9, routes)

        self.assertEqual(selected, semantic_path)
        self.assertEqual(routes.distance[9], 3)
        self.assertEqual(records_module._path_distance(selected, graph), 3)

    def test_unmatched_factory_remains_unchanged(self) -> None:
        translated: list[MethodRef] = records_module._translated_path([1, 7], self.graph)
        rendered: str = render_module._display_path([1, 7], self.graph)
        self.assertEqual(translated[-1], self.UNMATCHED_FACTORY)
        self.assertIn("FactoryMethodHolder.External_generated()", rendered)


if __name__ == "__main__":
    unittest.main()
