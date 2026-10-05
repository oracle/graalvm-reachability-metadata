# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Tests for generated discovery artifacts: report JSON, markdown, LCOV, and target-state lifecycle."""

import json
import os
import shutil
import tempfile
import unittest

from utility_scripts.code_coverage_jacoco import JacocoReportError
from utility_scripts.code_coverage_model import MethodRef
from utility_scripts import code_coverage_profile_report as report_module
from utility_scripts.code_coverage_profile_graph import CallGraph, load_call_graph
from utility_scripts.code_coverage_profile_inputs import ProfileFormatError, TargetState
from utility_scripts.code_coverage_profile_records import MAX_LISTED_METHODS, NearCallRecord
from utility_scripts.code_coverage_profile_render import write_lcov, write_markdown
from utility_scripts.code_coverage_profile_routes import (
    Sample,
    SampledProfile,
    load_sampled_profile,
)

from tests.code_coverage_profile_support import (
    FIXTURES,
    INIT_ID,
    JACOCO_PATH,
    LOAD_ID,
    PARSE_ID,
    RESOLVE_ID,
    RESOLVE_INTEGER_ID,
    _coverage,
    _load_jacoco,
)


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
            max_listed: int = MAX_LISTED_METHODS,
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
        self.assertIn("`Registry.init() → resolve(...)`", markdown)
        # `parse` is routed from a public entry only: it stays ranked in the
        # JSON and out of the prompt (§AR-code-coverage-deep-navigation.2).
        parse = next(entry for entry in report["uncoveredPaths"] if entry["id"] == PARSE_ID)
        self.assertEqual(parse["joinKind"], "public-entry")
        self.assertNotIn(PARSE_ID, report["promptTargetIds"])
        self.assertNotIn("Config.of()", markdown)
        self.assertNotIn("Public entry:", markdown)
        self.assertEqual(report["summary"]["listedUncovered"], report["summary"]["sampledJoins"])
        # `load` and `reload` lie behind the uncovered `resolve`, past the
        # frontier (§AR-code-coverage-deep-navigation.2).
        self.assertNotIn("load(...)`", markdown)
        self.assertNotIn("reload()`", markdown)
        for target in report["bulkTargets"]:
            self.assertIn("missClassification", target)
            self.assertIn(
                target["missClassification"]["kind"],
                {"dispatched-elsewhere", "fork-not-taken", "no-fork"},
            )
        self.assertEqual(markdown.count("  target "), len(report["promptTargetIds"]))
        path_lines = [line for line in markdown.splitlines() if line.startswith("`")]
        for line in path_lines:
            self.assertNotIn("#", line)
            self.assertNotIn(":void", line)
        # The preamble is the single public-entry obligation
        # (§AR-code-coverage-deep-navigation.2).
        self.assertTrue(markdown.startswith(
            "# Deep coverage paths (iteration 1) — com.example:demo:1.0.0\n\n"
            "Reach every target below through its public entry; never call internal "
            "methods directly.\n\n"
            "## Where the tests go\n"
        ))
        self.assertNotIn("sibling", markdown)
        self.assertNotIn(" samples to ", markdown)
        self.assertNotIn(" step(s)", markdown)
        self.assertNotIn("###", markdown)
        # Omitted counts and caveats are operator data and stay in the JSON.
        self.assertGreater(report["summary"]["omittedUncovered"], 0)
        self.assertNotIn("retained in JSON", markdown)
        self.assertTrue(report["caveats"])
        self.assertNotIn("## Caveats", markdown)
        self.assertNotIn("Detailed near-call guidance", markdown)

    def test_prompt_carries_no_operator_summary_or_progress(self) -> None:
        # A second report has history, which once rendered a progress block.
        self._generate(iteration=0)
        report = self._generate(iteration=1)
        with open(os.path.join(self.output_dir, "discovery-report-1.md"), encoding="utf-8") as md_file:
            markdown = md_file.read()
        # The counts stay in the JSON report, where operators read them.
        self.assertIn("listedUncovered", report["summary"])
        for removed in (
            "## Summary",
            "## Progress",
            "Newly JaCoCo-covered",
            "JaCoCo is the coverage authority. PGO samples and counters are guidance only.",
        ):
            self.assertNotIn(removed, markdown)

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
        graph = CallGraph(
            methods=methods,
            key_to_id={ref.canonical_id: method_id for method_id, ref in methods.items()},
        )
        records: list[NearCallRecord] = []
        for index, target in enumerate(targets):
            sample = Sample(
                context_id=f"sample-{index}",
                raw_context="",
                path=[(1, 0), (2, 1), (3, 2)],
                full_path=[(root, 0), (join, 1), (observed, 2)],
                path_full_indexes=[0, 1, 2],
                count=1,
            )
            records.append(NearCallRecord(
                coverage=_coverage(target),
                target_id=index + 4,
                target_state=TargetState(),
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
                "publicTargetsCovered": 0,
                "publicTargetsUncovered": 0,
                "listedUncovered": len(records),
                "omittedUncovered": 0,
                "samplingContexts": len(records),
                "totalSampleCount": len(records),
            },
            "bulkTargets": [],
            "caveats": [],
        }
        markdown_path = os.path.join(self.output_dir, "all-groups.md")

        write_markdown(
            report,
            records,
            graph,
            "example:library:1",
            0,
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
        self.assertIn("## Observed (sampled guidance only)\n\n" + first_group, markdown)
        self.assertEqual(markdown.count("Observed:\n"), 21)
        # A thematic break separates each pair of consecutive groups, and a
        # blank line before it keeps Markdown from reading a setext heading.
        self.assertEqual(markdown.count("\n\n---\n\nObserved:\n"), 20)
        self.assertEqual(markdown.count("---"), 20)
        self.assertIn(
            "`Library.dispatch() → parseAlternative20()`",
            markdown,
        )
        self.assertNotIn("###", markdown)
        self.assertNotIn("sibling", markdown)
        self.assertNotIn(" step(s)", markdown)

    def test_terminal_targets_leave_the_prompt_and_attempted_ones_stay(self) -> None:
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
                    "id": RESOLVE_ID,
                    "status": "attempted",
                    "attemptCount": 3,
                    "lastAttemptedIteration": 1,
                },
            ],
        })

        report = self._generate(iteration=0, target_state_paths=[state_path])

        by_id = {entry["id"]: entry for entry in report["uncoveredPaths"]}
        # Three failed attempts do not retire a target, while a terminal one
        # leaves the prompt even though it has a route.
        self.assertEqual(report["promptTargetIds"], [RESOLVE_ID])
        self.assertEqual(report["summary"]["terminalUncovered"], 2)
        self.assertEqual(by_id[PARSE_ID]["joinKind"], "public-entry")
        for method_id in (PARSE_ID, LOAD_ID):
            self.assertTrue(by_id[method_id]["terminal"])
            self.assertNotIn(method_id, report["promptTargetIds"])
        self.assertFalse(by_id[RESOLVE_ID]["terminal"])
        self.assertEqual(by_id[RESOLVE_ID]["targetStatus"], "attempted")
        self.assertEqual(by_id[RESOLVE_ID]["attemptCount"], 3)
        self.assertIsNone(by_id[RESOLVE_ID]["stateReason"])
        bulk = {entry["id"]: entry for entry in report["bulkTargets"]}
        self.assertEqual(bulk[RESOLVE_ID]["targetStatus"], "attempted")
        persisted = {entry["id"]: entry for entry in report["targetStates"]}
        self.assertEqual(persisted[RESOLVE_INTEGER_ID]["status"], "completed")
        self.assertEqual(persisted[PARSE_ID]["status"], "skipped")
        self.assertEqual(persisted[LOAD_ID]["status"], "exhausted")
        self.assertEqual(persisted[RESOLVE_ID]["status"], "attempted")

    def test_repeatedly_unproductive_targets_stay_in_rotation(self) -> None:
        reports: list[dict] = [self._generate(iteration=iteration) for iteration in range(5)]

        report: dict = reports[-1]
        bulk = {entry["id"]: entry for entry in report["bulkTargets"]}
        target = bulk[RESOLVE_ID]
        self.assertIn(RESOLVE_ID, report["promptTargetIds"])
        self.assertEqual(target["attemptCount"], 4)
        self.assertFalse(target["terminal"])
        self.assertIsNone(target["stateReason"])
        self.assertEqual(report["summary"]["terminalUncovered"], 0)

    def test_target_state_rejects_unknown_status(self) -> None:
        state_path = self._write_json("bad-state.json", {
            "coordinate": "com.example:demo:1.0.0",
            "targets": [{
                "id": RESOLVE_ID,
                "status": "mystery",
                "attemptCount": 0,
            }],
        })

        with self.assertRaisesRegex(ProfileFormatError, "unknown status"):
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
                        ProfileFormatError,
                        error_pattern,
                ):
                    self._generate(iteration=0, target_state_paths=[state_path])

    def test_input_paths_and_invalid_json_fail_closed(self) -> None:
        missing = os.path.join(self.output_dir, "missing")
        with self.assertRaisesRegex(ProfileFormatError, "call-tree CSVs"):
            load_call_graph(missing)

        graph = load_call_graph(FIXTURES)
        with self.assertRaisesRegex(ProfileFormatError, "sampled profile"):
            load_sampled_profile(missing, graph)
        with self.assertRaisesRegex(ProfileFormatError, "API inventory"):
            self._generate(iteration=0, api_inventory_path=missing)

        invalid_json = os.path.join(self.output_dir, "invalid-inventory.json")
        with open(invalid_json, "w", encoding="utf-8") as invalid_file:
            invalid_file.write("{")
        with self.assertRaisesRegex(ProfileFormatError, "Invalid JSON"):
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
                with self.assertRaisesRegex(ProfileFormatError, "coordinate"):
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

        with self.assertRaisesRegex(ProfileFormatError, "iteration"):
            self._generate(iteration=-1)
        with self.assertRaisesRegex(ProfileFormatError, "max_listed"):
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
        graph = load_call_graph(FIXTURES)
        profile = SampledProfile(sample_counts={
            graph.key_to_id[RESOLVE_ID]: 2,
            graph.key_to_id[RESOLVE_INTEGER_ID]: 3,
        })
        lcov_path = os.path.join(self.output_dir, "overloads.lcov")

        write_lcov(profile, graph, _load_jacoco(), lcov_path)

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
