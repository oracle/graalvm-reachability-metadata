# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Tests for the deep coverage frontier and the public methods it carries.

Routes cross one uncovered method, made by a caller that ran
(§AR-code-coverage-deep-navigation.2), classification judges only such callers
(§AR-code-coverage-deep-navigation.3), and the deep universe carries the public
methods the API phase left uncovered (§AR-code-coverage-improvement.4.2).
"""

import os
import tempfile
import unittest

from utility_scripts import code_coverage_finalize as finalize_module
from utility_scripts import code_coverage_profile_report as report_module
from utility_scripts.code_coverage_jacoco import JacocoLineCoverage, JacocoMethodCoverage
from utility_scripts.code_coverage_model import MethodRef
from utility_scripts.code_coverage_profile_graph import (
    FACTORY_METHOD_HOLDER,
    CallGraph,
    _index_factory_stubs,
)
from utility_scripts.code_coverage_profile_history import carried_public_targets
from utility_scripts.code_coverage_profile_inputs import ProfileFormatError, TargetState
from utility_scripts.code_coverage_profile_render import write_markdown
from utility_scripts.code_coverage_profile_routes import Sample, SampledProfile

from tests.code_coverage_profile_support import _coverage
from tests.test_code_coverage_finalize import COORDINATE, _api, _deep


def _graph(methods: dict[int, MethodRef], edges: list[tuple[int, int, int]]) -> CallGraph:
    """A call graph from `(caller, callee, source line)` edges, one invoke each."""
    graph = CallGraph(
        methods=methods,
        key_to_id={ref.canonical_id: static_id for static_id, ref in methods.items()},
    )
    for invoke_id, (caller, callee, line) in enumerate(edges, start=1):
        edge: dict = {
            "caller": caller,
            "callee": callee,
            "bci": str(line),
            "is_direct": "true",
            "kind": "call",
            "invoke_id": invoke_id,
            "source_line": line,
        }
        graph.adjacency.setdefault(caller, []).append(edge)
        graph.reverse_adjacency.setdefault(callee, []).append(edge)
        graph.invoke_fan_out.setdefault(invoke_id, []).append(callee)
    return graph


def _paths(report: dict) -> dict[str, dict]:
    return {entry["id"]: entry for entry in report["uncoveredPaths"]}


class FrontierRouteTest(unittest.TestCase):

    def test_only_a_covered_method_makes_the_last_call(self) -> None:
        test_frame = MethodRef("example.LibraryTest", "run", (), "void")
        jdk = MethodRef("java.util.ArrayList", "forEach", (), "void")
        start = MethodRef("example.Library", "start", (), "void")
        helper = MethodRef("example.Library", "helper", (), "void")
        direct = MethodRef("example.Library", "direct", (), "void")
        via_jdk = MethodRef("example.Library", "viaJdk", (), "void")
        frontier = MethodRef("example.Library", "frontier", (), "void")
        behind_helper = MethodRef("example.Library", "behindHelper", (), "void")
        methods = dict(enumerate(
            (test_frame, jdk, start, helper, direct, via_jdk, frontier, behind_helper), start=1,
        ))
        graph = _graph(methods, [
            (1, 5, 1), (1, 2, 2), (2, 6, 1), (2, 4, 2), (1, 3, 3), (3, 7, 1), (4, 8, 1),
        ])
        profile = SampledProfile(samples=[Sample(
            context_id="sample-1", raw_context="", path=[(1, 0)],
            full_path=[(test_frame, 0)], path_full_indexes=[0], count=1,
        )])
        jacoco = {
            **{ref.canonical_id: _coverage(ref, covered=True) for ref in (start, helper)},
            **{ref.canonical_id: _coverage(ref) for ref in (direct, via_jdk, frontier, behind_helper)},
        }

        paths = _paths(report_module.correlate(profile, graph, {"targets": []}, jacoco)[0])

        # A test or JDK frame JaCoCo cannot rule on never makes the last call.
        self.assertEqual(paths[direct.canonical_id]["joinKind"], "none")
        self.assertEqual(paths[via_jdk.canonical_id]["joinKind"], "none")
        self.assertEqual(
            paths[frontier.canonical_id]["reachingPath"],
            [test_frame.canonical_id, start.canonical_id, frontier.canonical_id],
        )
        # But a route may pass through one.
        self.assertEqual(
            paths[behind_helper.canonical_id]["reachingPath"],
            [test_frame.canonical_id, jdk.canonical_id, helper.canonical_id,
             behind_helper.canonical_id],
        )

    def test_a_route_never_continues_out_of_an_uncovered_method(self) -> None:
        """`after` ran through some path the graph does not show; reaching it
        through `skipped` would cross a second uncovered method."""
        entry = MethodRef("example.Api", "start", (), "void")
        skipped = MethodRef("example.Library", "skipped", (), "void")
        after = MethodRef("example.Library", "after", (), "void")
        target = MethodRef("example.Library", "target", (), "void")
        graph = _graph(
            {1: entry, 2: skipped, 3: after, 4: target}, [(1, 2, 1), (2, 3, 1), (3, 4, 1)]
        )
        jacoco = {
            **{ref.canonical_id: _coverage(ref, covered=True) for ref in (entry, after)},
            **{ref.canonical_id: _coverage(ref) for ref in (skipped, target)},
        }

        paths = _paths(report_module.correlate(
            SampledProfile(),
            graph,
            {"targets": [{"id": entry.canonical_id, "kind": "method"}]},
            jacoco,
        )[0])

        self.assertEqual(paths[skipped.canonical_id]["stepsRemaining"], 1)
        self.assertEqual(paths[target.canonical_id]["joinKind"], "none")

    def test_a_public_entry_starts_a_route_only_once_it_ran(self) -> None:
        entry = MethodRef("example.Api", "start", (), "void")
        middle = MethodRef("example.Library", "middle", (), "void")
        target = MethodRef("example.Library", "target", (), "void")
        graph = _graph({1: entry, 2: middle, 3: target}, [(1, 2, 1), (2, 3, 1)])
        inventory = {"targets": [{"id": entry.canonical_id, "kind": "method"}]}
        jacoco = {
            middle.canonical_id: _coverage(middle, covered=True),
            target.canonical_id: _coverage(target),
        }

        unreported = _paths(report_module.correlate(SampledProfile(), graph, inventory, jacoco)[0])
        jacoco[entry.canonical_id] = _coverage(entry, covered=True)
        ran = _paths(report_module.correlate(SampledProfile(), graph, inventory, jacoco)[0])

        self.assertEqual(unreported[target.canonical_id]["joinKind"], "none")
        self.assertEqual(ran[target.canonical_id]["joinKind"], "public-entry")

    def test_a_constructor_behind_a_factory_stub_is_judged_at_every_stub_caller(self) -> None:
        """The route runs through `Api.start`, which has no fork; `Builder.make`
        reaches the same constructor through the same stub, and its fork is
        the diagnosis."""
        routed = MethodRef("example.Api", "start", (), "void")
        other = MethodRef("example.Builder", "make", (), "void")
        factory = MethodRef(FACTORY_METHOD_HOLDER, "Widget_generated", (), "example.Widget")
        constructor = MethodRef("example.Widget", "<init>", (), "void")
        graph = _graph(
            {1: routed, 2: other, 3: factory, 4: constructor},
            [(1, 3, 3), (2, 3, 3), (3, 4, 1)],
        )
        _index_factory_stubs(graph, {constructor.canonical_id})
        reached = JacocoLineCoverage(mi=0, ci=2, mb=0, cb=0)
        missed = JacocoLineCoverage(mi=4, ci=0, mb=0, cb=0)

        report, _ = report_module.correlate(
            SampledProfile(),
            graph,
            {"targets": [{"id": routed.canonical_id, "kind": "method"}]},
            {
                **{ref.canonical_id: _coverage(ref, covered=True) for ref in (routed, other)},
                constructor.canonical_id: _coverage(constructor),
            },
            jacoco_lines={
                "example/Api.java": {1: reached, 3: missed},
                "example/Builder.java": {
                    1: reached, 2: JacocoLineCoverage(mi=0, ci=3, mb=1, cb=1), 3: missed,
                },
            },
        )

        path: dict = _paths(report)[constructor.canonical_id]
        self.assertEqual(path["reachingPath"], [routed.canonical_id, constructor.canonical_id])
        classification: dict = path["missClassification"]
        self.assertEqual(classification["invokingMethod"], other.canonical_id)
        self.assertEqual(classification["kind"], "fork-not-taken")
        self.assertEqual(classification["fork"]["line"], 2)

    def test_a_stub_caller_is_judged_on_its_constructor_lines(self) -> None:
        """The image attributes a call inside a constructor body to its factory
        stub, which JaCoCo does not report."""
        entry = MethodRef("example.Api", "start", (), "void")
        factory = MethodRef(FACTORY_METHOD_HOLDER, "Widget_generated", (), "example.Widget")
        constructor = MethodRef("example.Widget", "<init>", (), "void")
        target = MethodRef("example.Widget", "configure", (), "void")
        graph = _graph(
            {1: entry, 2: factory, 3: constructor, 4: target},
            [(1, 2, 1), (2, 3, 0), (2, 4, 0)],
        )
        for edge in graph.adjacency[2]:
            edge.pop("source_line")
        _index_factory_stubs(graph, {constructor.canonical_id})

        report, _ = report_module.correlate(
            SampledProfile(),
            graph,
            {"targets": [{"id": entry.canonical_id, "kind": "method"}]},
            {
                **{ref.canonical_id: _coverage(ref, covered=True) for ref in (entry, constructor)},
                target.canonical_id: _coverage(target),
            },
            jacoco_lines={"example/Widget.java": {
                1: JacocoLineCoverage(mi=0, ci=3, mb=1, cb=1),
                2: JacocoLineCoverage(mi=4, ci=0, mb=0, cb=0),
            }},
        )

        classification: dict = _paths(report)[target.canonical_id]["missClassification"]
        self.assertEqual(classification["invokingMethod"], constructor.canonical_id)
        self.assertEqual(classification["kind"], "fork-not-taken")
        self.assertEqual(classification["fork"]["evidence"], "line-order")

    def test_a_caller_that_never_ran_is_not_judged(self) -> None:
        """An uncovered caller whose lines a covered method shares would read as
        a dispatch; it never ran, so it says nothing about this miss."""
        ran = MethodRef("example.Ran", "call", (), "void")
        never = MethodRef("example.Never", "call", (), "void")
        target = MethodRef("example.Impl", "work", (), "void")
        other = MethodRef("example.Other", "work", (), "void")
        graph = _graph({1: ran, 2: never, 3: target, 4: other}, [(1, 3, 3), (2, 3, 2)])
        graph.invoke_fan_out[2].append(4)
        lines: dict[str, dict[int, JacocoLineCoverage]] = {
            "example/Ran.java": {
                2: JacocoLineCoverage(mi=0, ci=3, mb=1, cb=1),
                3: JacocoLineCoverage(mi=4, ci=0, mb=0, cb=0),
            },
            "example/Never.java": {2: JacocoLineCoverage(mi=0, ci=5, mb=0, cb=0)},
        }

        report, _ = report_module.correlate(
            SampledProfile(),
            graph,
            {"targets": [{"id": ran.canonical_id, "kind": "method"}]},
            {
                ran.canonical_id: _coverage(ran, covered=True),
                never.canonical_id: _coverage(never),
                target.canonical_id: _coverage(target),
            },
            jacoco_lines=lines,
        )

        classification: dict = _paths(report)[target.canonical_id]["missClassification"]
        self.assertEqual(classification["invokingMethod"], ran.canonical_id)
        self.assertEqual(classification["kind"], "fork-not-taken")


class PublicCarryOverTest(unittest.TestCase):

    ENTRY = MethodRef("example.Api", "run", (), "void")
    CARRIED = MethodRef("example.Api", "helper", (), "void")
    UNREPORTED = MethodRef("example.Api", "unreported", (), "void")
    INTERNAL = MethodRef("example.Impl", "work", (), "void")

    def setUp(self) -> None:
        self.graph = _graph(
            {1: self.ENTRY, 2: self.CARRIED, 3: self.INTERNAL}, [(1, 2, 1), (1, 3, 2)]
        )
        self.inventory = {"targets": [
            {"id": ref.canonical_id, "kind": "method"}
            for ref in (self.ENTRY, self.CARRIED, self.UNREPORTED)
        ]}
        self.jacoco: dict[str, JacocoMethodCoverage] = {
            self.ENTRY.canonical_id: _coverage(self.ENTRY, covered=True),
            self.CARRIED.canonical_id: _coverage(self.CARRIED),
            self.INTERNAL.canonical_id: _coverage(self.INTERNAL),
        }

    def _correlate(
            self,
            carried: list[str] | None = None,
            states: dict[str, TargetState] | None = None,
    ) -> tuple[dict, list]:
        return report_module.correlate(
            SampledProfile(), self.graph, self.inventory, self.jacoco,
            target_states=states, carried_public_ids=carried,
        )

    def test_the_first_report_freezes_the_uncovered_public_methods(self) -> None:
        report, _ = self._correlate()

        self.assertEqual(
            [entry["id"] for entry in report["publicTargets"]], [self.CARRIED.canonical_id]
        )
        self.assertNotIn(
            self.CARRIED.canonical_id, {entry["id"] for entry in report["deepMethods"]}
        )
        self.assertEqual(
            {key: report["summary"][key] for key in (
                "publicTargets", "publicTargetsCovered", "publicTargetsUncovered",
                "deepCovered", "rosterCovered",
            )},
            {"publicTargets": 1, "publicTargetsCovered": 0, "publicTargetsUncovered": 1,
             "deepCovered": 0, "rosterCovered": 0},
        )
        paths = _paths(report)
        self.assertTrue(paths[self.CARRIED.canonical_id]["publicApi"])
        self.assertFalse(paths[self.INTERNAL.canonical_id]["publicApi"])
        self.assertEqual(
            report["promptTargetIds"], [self.CARRIED.canonical_id, self.INTERNAL.canonical_id]
        )

    def test_a_carried_method_stays_on_the_roster_once_covered(self) -> None:
        self.jacoco[self.CARRIED.canonical_id] = _coverage(self.CARRIED, covered=True)

        carried, _ = self._correlate([self.CARRIED.canonical_id])
        fresh, _ = self._correlate()

        self.assertEqual(carried["publicTargets"][0]["status"], "covered")
        self.assertEqual(carried["summary"]["rosterCovered"], 1)
        self.assertNotIn(self.CARRIED.canonical_id, _paths(carried))
        self.assertEqual(fresh["publicTargets"], [])

    def test_a_carried_method_that_left_the_universe_is_rejected(self) -> None:
        for moved in (self.UNREPORTED.canonical_id, "example.Gone#m():void"):
            with self.assertRaisesRegex(ProfileFormatError, "universe moved"):
                self._correlate([moved])

    def test_target_state_may_name_only_a_carried_public_method(self) -> None:
        attempted = TargetState(status="attempted", attempt_count=2)
        report, _ = self._correlate(states={self.CARRIED.canonical_id: attempted})
        self.assertEqual(_paths(report)[self.CARRIED.canonical_id]["attemptCount"], 2)

        with self.assertRaisesRegex(ProfileFormatError, "deep JaCoCo universe"):
            self._correlate(states={self.ENTRY.canonical_id: attempted})

    def test_history_carries_the_frozen_set(self) -> None:
        method_id: str = self.CARRIED.canonical_id
        self.assertIsNone(carried_public_targets(None))
        self.assertEqual(
            carried_public_targets({"publicTargets": [{"id": method_id, "status": "covered"}]}),
            [method_id],
        )
        with self.assertRaisesRegex(ProfileFormatError, "publicTargets"):
            carried_public_targets({"uncoveredPaths": []})

    def test_the_prompt_marks_public_targets(self) -> None:
        report, records = self._correlate()
        with tempfile.TemporaryDirectory(prefix="deep-frontier-") as directory:
            markdown_path: str = os.path.join(directory, "deep.md")
            write_markdown(report, records, self.graph, "example:api:1", 0, markdown_path)
            with open(markdown_path, encoding="utf-8") as handle:
                markdown: str = handle.read()

        self.assertIn("`Api.run() → helper()` — public API", markdown)
        self.assertIn("`Api.run() → Impl.work()`\n", markdown)
        # The counts are operator data: the JSON report keeps them, the prompt does not.
        self.assertEqual(report["summary"]["publicTargetsCovered"], 0)
        self.assertEqual(report["summary"]["publicTargetsUncovered"], 1)
        self.assertNotIn("covered since", markdown)


class FinalizeCarryOverTest(unittest.TestCase):

    CARRIED = "example.Api#m3():void"

    def test_deep_phase_completes_the_public_targets_it_carried(self) -> None:
        # A final remeasurement freezes its own set: only what is still uncovered.
        final = _deep(["uncovered"], 1)
        final["inventory"] = [{"id": self.CARRIED, "kind": "method", "status": "covered"}]
        unchanged_api = _api(["covered", "uncovered", "uncovered", "uncovered"])

        outcomes = finalize_module._target_outcomes(
            [],
            COORDINATE,
            unchanged_api,
            unchanged_api,
            _deep(["uncovered"], 1, {self.CARRIED: "uncovered"}),
            final,
        )

        self.assertEqual(
            outcomes["completed"],
            [{"id": self.CARRIED, "phase": "deep", "status": "completed"}],
        )

    def test_rejects_a_carried_public_target_the_final_inventory_lost(self) -> None:
        with self.assertRaisesRegex(finalize_module.FinalizationError, "carried public target"):
            finalize_module._target_outcomes(
                [],
                COORDINATE,
                _api([]),
                _api([]),
                _deep(["uncovered"], 1, {self.CARRIED: "uncovered"}),
                _deep(["uncovered"], 1),
            )


if __name__ == "__main__":
    unittest.main()
