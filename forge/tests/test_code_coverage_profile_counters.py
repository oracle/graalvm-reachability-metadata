# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Tests for instrumented counters, receiver resolution, and their hints.

§AR-code-coverage-deep-navigation.1.2, §AR-code-coverage-deep-navigation.2,
§AR-code-coverage-deep-navigation.3.1
"""

import json
import os
import shutil
import tempfile
import unittest

from utility_scripts.code_coverage_api_rank import TypeModel
from utility_scripts.code_coverage_model import MethodRef
from utility_scripts import code_coverage_profile_report as report_module
from utility_scripts.code_coverage_profile_counters import (
    InstrumentedCounters,
    counters_from_profile,
    mark_unobserved_dispatch,
    site_dispatch,
)
from utility_scripts.code_coverage_profile_graph import CallGraph
from utility_scripts.code_coverage_profile_inputs import ProfileFormatError, TargetState
from utility_scripts.code_coverage_profile_records import NearCallRecord, record_rank_key
from utility_scripts.code_coverage_profile_diagnoses import Diagnosis, diagnosis_lines
from utility_scripts.code_coverage_profile_render import write_markdown

from tests.code_coverage_profile_support import (
    FIXTURES,
    JACOCO_PATH,
    RESOLVE_ID,
    _coverage,
    _sampled_at,
)

OWNER = MethodRef("org.h2.mvstore.Page", "getMemory", (), "int")
VALUE = MethodRef("org.h2.mvstore.type.ValueDataType", "getMemory", ("java.lang.Object",), "int")
BASIC = MethodRef("org.h2.mvstore.type.BasicDataType", "getMemory", ("java.lang.Object",), "int")
ROW = MethodRef("org.h2.mvstore.db.RowDataType", "getMemory", ("java.lang.Object",), "int")


def _document(conditional: list, virtual: list) -> dict:
    return {
        "types": [
            {"id": 1, "name": "int"},
            {"id": 2, "name": "org.h2.mvstore.Page"},
            {"id": 3, "name": "org.h2.mvstore.type.ValueDataType"},
            {"id": 4, "name": "org.h2.mvstore.type.LongDataType"},
        ],
        "methods": [{"id": 10, "name": "getMemory", "signature": [2, 1]}],
        "callCountProfiles": [{"ctx": "10:0", "records": [5]}],
        "conditionalProfiles": conditional,
        "virtualInvokeProfiles": virtual,
        "samplingProfiles": [],
    }


class CounterFoldingTests(unittest.TestCase):

    def test_inlining_contexts_fold_onto_their_leaf_position(self) -> None:
        document: dict = _document(
            [
                {"ctx": "10:4", "records": [7, 0, 3, 12, 1, 0]},
                {"ctx": "10:4<99:8", "records": [7, 0, 2, 12, 1, 5, -1, 2, 9]},
            ],
            [{"ctx": "10:6<99:1", "records": [3, 14203, 4, 891]}],
        )
        counters = counters_from_profile(document, {10: OWNER}, {1: "int", 3: "org.h2.mvstore.type.ValueDataType",
                                                                 4: "org.h2.mvstore.type.LongDataType"})
        assert counters is not None
        self.assertEqual(counters.branches[(OWNER.canonical_id, 4)], {7: 5, 12: 5})
        self.assertEqual(
            counters.receivers[(OWNER.canonical_id, 6)],
            {"org.h2.mvstore.type.ValueDataType": 14203, "org.h2.mvstore.type.LongDataType": 891},
        )

    def test_a_sampling_only_profile_has_no_counters(self) -> None:
        document: dict = {**_document([], []), "callCountProfiles": []}
        self.assertIsNone(counters_from_profile(document, {10: OWNER}, {}))

    def test_truncated_records_fail_closed(self) -> None:
        with self.assertRaisesRegex(ProfileFormatError, "truncated"):
            counters_from_profile(_document([{"ctx": "10:4", "records": [7, 0]}], []), {10: OWNER}, {})


class ReceiverResolutionTests(unittest.TestCase):
    """Receivers resolve through the library type hierarchy, or not at all."""

    def setUp(self) -> None:
        self.graph = CallGraph(
            methods={1: OWNER, 2: BASIC, 3: ROW},
            invoke_fan_out={10: [2, 3]},
        )
        self.edges: list[dict] = [
            {"caller": 1, "callee": callee, "bci": "6", "is_direct": "false", "kind": "call", "invoke_id": 10}
            for callee in (2, 3)
        ]
        self.graph.adjacency = {1: self.edges}
        self.types = TypeModel(supertypes={
            "org.h2.mvstore.type.ValueDataType": ["org.h2.mvstore.type.BasicDataType"],
            "org.h2.mvstore.type.BasicDataType": ["java.lang.Object"],
            "org.h2.mvstore.db.RowDataType": ["org.h2.mvstore.type.BasicDataType"],
        })

    def _counters(self, receivers: dict[str, int]) -> InstrumentedCounters:
        return InstrumentedCounters(receivers={(OWNER.canonical_id, 6): receivers})

    def test_an_inherited_implementation_is_credited_through_the_superclass(self) -> None:
        dispatch = site_dispatch(
            self.edges[0], self.graph,
            self._counters({"org.h2.mvstore.type.ValueDataType": 14203}), self.types,
        )
        assert dispatch is not None
        self.assertEqual(dispatch.dispatches, {2: 14203, 3: 0})

    def test_one_unresolved_receiver_leaves_every_candidate_unlabelled(self) -> None:
        dispatch = site_dispatch(
            self.edges[0], self.graph,
            self._counters({"org.h2.mvstore.type.ValueDataType": 1, "com.acme.Foreign": 1}), self.types,
        )
        assert dispatch is not None
        self.assertIsNone(dispatch.dispatches)
        self.assertEqual(dispatch.total, 2)

    def test_edges_to_implementations_never_dispatched_are_marked(self) -> None:
        marked: int = mark_unobserved_dispatch(
            self.graph, self._counters({"org.h2.mvstore.type.ValueDataType": 3}), self.types
        )
        self.assertEqual(marked, 1)
        self.assertEqual([bool(edge.get("unobserved")) for edge in self.edges], [False, True])

    def test_a_site_without_a_histogram_makes_no_claim(self) -> None:
        self.assertEqual(mark_unobserved_dispatch(self.graph, InstrumentedCounters(), self.types), 0)


class UnobservedRouteTests(unittest.TestCase):
    """Equal-length routes prefer observed dispatch; a needed one is named."""

    def test_route_and_rank_prefer_the_observed_step(self) -> None:
        entry = MethodRef("app.Api", "run", (), "void")
        target = MethodRef("app.Internal", "target", (), "void")
        via_seen = MethodRef("app.Seen", "step", (), "void")
        via_unseen = MethodRef("app.Unseen", "step", (), "void")
        methods: dict[int, MethodRef] = {1: entry, 2: via_unseen, 3: via_seen, 4: target}

        def edge(caller: int, callee: int, unobserved: bool = False) -> dict:
            return {"caller": caller, "callee": callee, "bci": "1", "is_direct": "false",
                    "kind": "call", "unobserved": unobserved}

        graph = CallGraph(
            methods=methods,
            key_to_id={ref.canonical_id: static_id for static_id, ref in methods.items()},
            adjacency={1: [edge(1, 2, unobserved=True), edge(1, 3)], 2: [edge(2, 4)], 3: [edge(3, 4)]},
        )
        report, records = report_module.correlate(
            _sampled_at(1, entry),
            graph,
            {"targets": [{"id": entry.canonical_id, "kind": "method"}]},
            {entry.canonical_id: _coverage(entry, covered=True),
             target.canonical_id: _coverage(target),
             via_seen.canonical_id: _coverage(via_seen, covered=True),
             via_unseen.canonical_id: _coverage(via_unseen)},
        )
        by_id: dict[str, dict] = {entry["id"]: entry for entry in report["uncoveredPaths"]}
        self.assertEqual(by_id[target.canonical_id]["reachingPath"][1], via_seen.canonical_id)
        self.assertEqual(by_id[target.canonical_id]["unobservedSteps"], [])
        self.assertEqual(
            by_id[via_unseen.canonical_id]["unobservedSteps"],
            [{"caller": entry.canonical_id, "callee": via_unseen.canonical_id}],
        )
        markdown_path: str = os.path.join(tempfile.mkdtemp(prefix="unobserved-"), "prompt.md")
        self.addCleanup(shutil.rmtree, os.path.dirname(markdown_path), True)
        write_markdown(report, records, graph, "app:api:1", 0, markdown_path)
        with open(markdown_path, encoding="utf-8") as handle:
            self.assertIn(
                "route assumes `Api.run() → Unseen.step()`, a dispatch the run never made",
                handle.read(),
            )

    def test_equal_distance_ties_break_on_unobserved_steps_then_reach(self) -> None:
        def record(name: str, reach: int | None, unobserved: bool = False) -> NearCallRecord:
            return NearCallRecord(
                coverage=_coverage(MethodRef("app.Internal", name, (), "void")),
                target_id=1,
                target_state=TargetState(),
                join_kind="public-entry",
                static_path=[0, 1],
                static_path_edges=[{"caller": 0, "callee": 1, "unobserved": unobserved}],
                sample=None,
                sampled_join_path_index=None,
                semantic_distance=1,
                reach=reach,
            )

        ranked: list[str] = [
            item.target_ref.name
            for item in sorted(
                [record("a", None), record("b", 2), record("c", 40182), record("d", 90000, True)],
                key=record_rank_key,
            )
        ]
        self.assertEqual(ranked, ["c", "b", "a", "d"])


class DispatchRenderingTests(unittest.TestCase):

    def test_receivers_and_candidate_labels_are_named(self) -> None:
        lines: list[str] = diagnosis_lines([Diagnosis("RowDataType.getMemory(...)", {
            "kind": "dispatched-elsewhere",
            "target": {"sourcePath": "org/h2/mvstore/Page.java", "line": 88,
                       "mi": 0, "ci": 4, "mb": 0, "cb": 0},
            "candidates": [
                {"id": ROW.canonical_id, "coverageSuite": False, "dispatches": 0},
                {"id": VALUE.canonical_id, "coverageSuite": False, "dispatches": 14203},
            ],
            "site": {
                "dispatches": 15094,
                "receivers": [
                    {"type": "org.h2.mvstore.type.ValueDataType", "count": 14203},
                    {"type": "org.h2.mvstore.type.LongDataType", "count": 891},
                ],
                "targetDispatches": 0,
            },
        })])
        self.assertEqual(lines, [
            "",
            "Dispatched elsewhere:",
            "  `Page.java:88`: RowDataType.getMemory(...) — site dispatched 15,094×, "
            "never to your target",
            "    observed receivers: ValueDataType ×14,203 · LongDataType ×891",
            f"    candidates: `{VALUE.canonical_id}` [dispatched ×14,203], "
            f"`{ROW.canonical_id}` [never dispatched here]",
        ])

    def test_unresolved_receiver_makes_no_claim_about_the_target(self) -> None:
        """`targetDispatches` is `None`, not zero, so the hint must not say
        the site never reached the target (§AR-code-coverage-deep-navigation.3.1)."""
        lines: list[str] = diagnosis_lines([Diagnosis("RowDataType.getMemory(...)", {
            "kind": "dispatched-elsewhere",
            "target": {"sourcePath": "org/h2/mvstore/Page.java", "line": 88,
                       "mi": 0, "ci": 4, "mb": 0, "cb": 0},
            "candidates": [
                {"id": ROW.canonical_id, "coverageSuite": False, "dispatches": None},
                {"id": VALUE.canonical_id, "coverageSuite": False, "dispatches": None},
            ],
            "site": {
                "dispatches": 42,
                "receivers": [{"type": "org.h2.PageCoverageTest$1", "count": 42}],
                "targetDispatches": None,
            },
        })])
        self.assertEqual(lines[2:], [
            "  `Page.java:88`: RowDataType.getMemory(...) — site dispatched 42×",
            "    observed receivers: PageCoverageTest.1 ×42",
            f"    candidates: `{ROW.canonical_id}`, `{VALUE.canonical_id}`",
        ])


class InstrumentedReportTests(unittest.TestCase):
    """`generate_report` reads the counters and the extractor's sibling files."""

    def setUp(self) -> None:
        self.directory: str = tempfile.mkdtemp(prefix="instrumented-report-")
        self.addCleanup(shutil.rmtree, self.directory, True)

    def _generate(self, profile: dict, with_flow: bool) -> dict:
        profile_path: str = os.path.join(self.directory, "profile.iprof")
        with open(profile_path, "w", encoding="utf-8") as handle:
            json.dump(profile, handle)
        graph_dir: str = os.path.join(self.directory, "graph")
        os.makedirs(graph_dir, exist_ok=True)
        methods_path: str = os.path.join(graph_dir, "methods.csv")
        with open(methods_path, "w", encoding="utf-8") as handle:
            handle.write("id,hasCode,isPublicApi,isStatic,lineNumbers\n")
            handle.write(f'"{RESOLVE_ID}",true,false,false,"0:20"\n')
        if with_flow:
            with open(os.path.join(graph_dir, "flow.csv"), "w", encoding="utf-8") as handle:
                handle.write("id,branches,blocks\n")
                handle.write(f'"{RESOLVE_ID}","2>3,9","0>3,9;3>;9>"\n')
        return report_module.generate_report(
            profile_path=profile_path,
            reports_dir=FIXTURES,
            api_inventory_path=os.path.join(FIXTURES, "near_call_inventory.json"),
            jacoco_xml_paths=[JACOCO_PATH],
            coordinate="com.example:demo:1.0.0",
            iteration=0,
            output_dir=os.path.join(self.directory, "discovery"),
            library_methods_path=methods_path,
        )

    def _profile(self) -> dict:
        with open(os.path.join(FIXTURES, "near_call_sampling.iprof"), encoding="utf-8") as handle:
            profile: dict = json.load(handle)
        profile["callCountProfiles"] = [{"ctx": "102:0", "records": [42]}]
        profile["conditionalProfiles"] = [{"ctx": "102:3", "records": [5, 0, 42, 9, 1, 0]}]
        return profile

    def test_counters_and_the_control_flow_table_reach_the_report(self) -> None:
        report: dict = self._generate(self._profile(), with_flow=True)
        summary: dict = report["summary"]
        self.assertEqual(report["profileKind"], "instrumented-guidance")
        self.assertEqual(
            (summary["instrumentedCounters"], summary["profiledBranches"], summary["controlFlowMethods"]),
            (True, 1, 1),
        )
        self.assertFalse(any("control-flow table" in caveat for caveat in report["caveats"]))
        self.assertTrue(any("zero instrumented count" in caveat for caveat in report["caveats"]))

    def test_missing_evidence_is_declared_rather_than_silent(self) -> None:
        with open(os.path.join(FIXTURES, "near_call_sampling.iprof"), encoding="utf-8") as handle:
            sampled: dict = json.load(handle)
        report: dict = self._generate(sampled, with_flow=False)
        self.assertEqual(report["profileKind"], "sampled-guidance")
        caveats: str = " ".join(report["caveats"])
        self.assertIn("no instrumented counters", caveats)
        self.assertIn("No control-flow table", caveats)


if __name__ == "__main__":
    unittest.main()
