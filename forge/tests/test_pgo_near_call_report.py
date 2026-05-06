# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import csv
import json
import os
import tempfile
import unittest

from utility_scripts.dynamic_access_report import DynamicAccessCallSite
from utility_scripts.pgo_near_call_report import (
    build_pgo_near_call_records,
    format_pgo_near_call_guidance,
)


class PgoNearCallReportTests(unittest.TestCase):
    def test_build_records_starts_static_path_at_sampled_call_graph_join(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            self._write_report_fixture(tmpdir)
            records = build_pgo_near_call_records(tmpdir, [self._call_site()])

        self.assertEqual(len(records), 1)
        record = records[0]
        self.assertEqual(record.static_path, [2, 3, 5])
        self.assertEqual(record.sampled_join_path_index, 1)
        self.assertEqual(record.prefix_length, 1)
        self.assertEqual(record.depth_remaining, 2)
        self.assertEqual(record.sample_count, 7)

    def test_format_guidance_names_reached_method_observed_branch_and_required_turn(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            self._write_report_fixture(tmpdir)
            guidance = format_pgo_near_call_guidance(tmpdir, [self._call_site()])

        self.assertIn("Target uncovered dynamic-access call:", guidance)
        self.assertIn("PGO sample profile:", guidance)
        self.assertIn("- PGO file: {path}".format(path=os.path.join(tmpdir, "native-test.iprof")), guidance)
        self.assertIn("- Total sample count: 7", guidance)
        self.assertIn("- Sampling contexts: 1", guidance)
        self.assertIn("Closest sampled stack from an existing test to the PGO/call-graph join point:", guidance)
        self.assertIn(
            "at example.Test.main():void @sample-bci=10  <-- existing test entry point",
            guidance,
        )
        self.assertIn(
            "at example.Router.route():void @sample-bci=20  <-- PGO/call-graph join point",
            guidance,
        )
        self.assertIn("PGO reached: example.Router.route():void", guidance)
        self.assertIn("Current sampled execution then goes to: example.Other.covered():void @sample-bci=21", guidance)
        self.assertIn(
            "To reach the uncovered call, tests need to drive: example.TargetHolder.call():void @invoke-bci=20",
            guidance,
        )
        self.assertIn("Static steps still missing: 2", guidance)

    def test_sampled_join_wins_when_target_caller_is_also_static_entry_point(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            self._write_report_fixture(tmpdir, target_holder_is_entry_point=True)
            records = build_pgo_near_call_records(tmpdir, [self._call_site()])
            guidance = format_pgo_near_call_guidance(tmpdir, [self._call_site()])

        self.assertEqual(records[0].static_path, [2, 3, 5])
        self.assertEqual(records[0].sampled_join_path_index, 1)
        self.assertIn("PGO reached: example.Router.route():void", guidance)
        self.assertIn("1. example.Router.route():void", guidance)

    def test_matching_uses_tracked_api_parameters_to_disambiguate_overloads(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            self._write_report_fixture(
                tmpdir,
                extra_methods=[
                    ["6", "java.lang.Class", "forName", "java.lang.String boolean java.lang.ClassLoader", "java.lang.Class", "false"],
                ],
                target_invokes=[
                    ["14", "3", "29", "true"],
                    ["15", "3", "30", "true"],
                ],
                target_targets=[
                    ["14", "6"],
                    ["15", "5"],
                ],
            )
            records = build_pgo_near_call_records(tmpdir, [self._call_site()])

        self.assertEqual(len(records), 1)
        self.assertEqual(records[0].static_path_edges[-1]["bci"], "30")

    def test_format_guidance_includes_pgo_profile_summary_when_no_call_site_matches(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            self._write_report_fixture(tmpdir)
            guidance = format_pgo_near_call_guidance(
                tmpdir,
                [self._call_site(tracked_api="java.lang.reflect.Method#invoke(java.lang.Object, java.lang.Object[])")],
            )

        self.assertTrue(guidance.startswith("- PGO near-call guidance unavailable:"))
        self.assertIn("no static call-tree edge matched the uncovered call sites", guidance)
        self.assertIn("- PGO file: {path}".format(path=os.path.join(tmpdir, "native-test.iprof")), guidance)
        self.assertIn("- Total sample count: 7", guidance)
        self.assertIn("- Sampling contexts: 1", guidance)

    def test_matching_returns_all_identical_call_site_edges(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            self._write_report_fixture(
                tmpdir,
                target_invokes=[
                    ["14", "3", "29", "true"],
                    ["15", "3", "30", "true"],
                ],
                target_targets=[
                    ["14", "5"],
                    ["15", "5"],
                ],
            )
            records = build_pgo_near_call_records(tmpdir, [self._call_site()])
            guidance = format_pgo_near_call_guidance(tmpdir, [self._call_site()])

        self.assertEqual({record.static_path_edges[-1]["bci"] for record in records}, {"29", "30"})
        self.assertIn("Other matching static call-tree edges for this call site:", guidance)

    def test_matching_accepts_simple_tracked_api_parameter_names(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            self._write_report_fixture(tmpdir)
            records = build_pgo_near_call_records(
                tmpdir,
                [self._call_site(tracked_api="java.lang.Class#forName(String)")],
            )

        self.assertEqual(len(records), 1)

    def test_matching_accepts_object_input_stream_read_object_lowering(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            self._write_report_fixture(
                tmpdir,
                extra_methods=[
                    ["6", "java.io.ObjectInputStream", "readObject", "java.lang.Class", "java.lang.Object", "false"],
                ],
                target_targets=[["14", "6"]],
            )
            records = build_pgo_near_call_records(
                tmpdir,
                [self._call_site(tracked_api="java.io.ObjectInputStream#readObject()")],
            )
            guidance = format_pgo_near_call_guidance(
                tmpdir,
                [self._call_site(tracked_api="java.io.ObjectInputStream#readObject()")],
            )

        self.assertEqual(len(records), 1)
        self.assertIn("java.io.ObjectInputStream.readObject(java.lang.Class):java.lang.Object", guidance)

    @staticmethod
    def _call_site(tracked_api: str = "java.lang.Class#forName(java.lang.String)") -> DynamicAccessCallSite:
        return DynamicAccessCallSite(
            metadata_type="reflection",
            tracked_api=tracked_api,
            frame="example.TargetHolder.call(TargetHolder.java:42)",
            line=42,
            covered=False,
        )

    def _write_report_fixture(
            self,
            report_dir: str,
            extra_methods: list[list[str]] | None = None,
            target_invokes: list[list[str]] | None = None,
            target_targets: list[list[str]] | None = None,
            target_holder_is_entry_point: bool = False,
    ) -> None:
        reports_dir = os.path.join(report_dir, "reports")
        os.makedirs(reports_dir)
        self._write_csv(
            os.path.join(reports_dir, "call_tree_methods.csv"),
            ["Id", "Type", "Name", "Parameters", "Return", "IsEntryPoint"],
            [
                ["1", "example.Test", "main", "empty", "void", "true"],
                ["2", "example.Router", "route", "empty", "void", "false"],
                ["3", "example.TargetHolder", "call", "empty", "void",
                 "true" if target_holder_is_entry_point else "false"],
                ["4", "example.Other", "covered", "empty", "void", "false"],
                ["5", "java.lang.Class", "forName", "java.lang.String", "java.lang.Class", "false"],
            ] + (extra_methods or []),
        )
        target_invokes = target_invokes or [["14", "3", "30", "true"]]
        target_targets = target_targets or [["14", "5"]]
        self._write_csv(
            os.path.join(reports_dir, "call_tree_invokes.csv"),
            ["Id", "MethodId", "BytecodeIndexes", "IsDirect"],
            [
                ["11", "1", "10", "true"],
                ["12", "2", "20", "true"],
                ["13", "2", "21", "true"],
            ] + target_invokes,
        )
        self._write_csv(
            os.path.join(reports_dir, "call_tree_targets.csv"),
            ["InvokeId", "TargetId"],
            [
                ["11", "2"],
                ["12", "3"],
                ["13", "4"],
            ] + target_targets,
        )
        with open(os.path.join(report_dir, "native-test.iprof"), "w", encoding="utf-8") as iprof_file:
            json.dump(
                {
                    "types": [
                        {"id": 1, "name": "example.Test"},
                        {"id": 2, "name": "void"},
                        {"id": 3, "name": "example.Router"},
                        {"id": 4, "name": "example.TargetHolder"},
                        {"id": 5, "name": "example.Other"},
                    ],
                    "methods": [
                        {"id": 101, "name": "main", "signature": [1, 2]},
                        {"id": 102, "name": "route", "signature": [3, 2]},
                        {"id": 104, "name": "covered", "signature": [5, 2]},
                    ],
                    "samplingProfiles": [
                        {
                            "ctx": "104:21<102:20<101:10",
                            "records": [7],
                        }
                    ],
                },
                iprof_file,
            )

    @staticmethod
    def _write_csv(path: str, fieldnames: list[str], rows: list[list[str]]) -> None:
        with open(path, "w", encoding="utf-8", newline="") as csv_file:
            writer = csv.writer(csv_file)
            writer.writerow(fieldnames)
            writer.writerows(rows)


if __name__ == "__main__":
    unittest.main()
