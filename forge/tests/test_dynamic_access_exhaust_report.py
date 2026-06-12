# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os
import tempfile
import unittest

from git_scripts.pr_publication import parse_pr_number
from utility_scripts.dynamic_access_exhaust_report import (
    DynamicAccessExhaustReport,
    dynamic_access_exhaust_report_path,
    find_dynamic_access_exhaust_report_path,
    resolve_workflow_exhaust_report,
)


class ParsePrNumberTests(unittest.TestCase):
    def test_pr_number_extracted_from_url(self) -> None:
        output = "https://github.com/oracle/graalvm-reachability-metadata/pull/4242\n"
        self.assertEqual(parse_pr_number(output), 4242)

    def test_pr_number_ignores_org_or_repo_with_digits(self) -> None:
        output = "https://github.com/owner123/repo456/pull/77\n"
        self.assertEqual(parse_pr_number(output), 77)

    def test_pr_number_returns_none_when_url_absent(self) -> None:
        self.assertIsNone(parse_pr_number(""))
        self.assertIsNone(parse_pr_number("Pull request already exists for branch foo.\n"))


class DynamicAccessExhaustReportTests(unittest.TestCase):
    def test_report_round_trip_preserves_minimal_chunk_state(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            report = DynamicAccessExhaustReport.create(
                coordinate="org.example:lib:1.0.0",
                issue_number=4241,
            )
            report.update_chunk_limits(5, 2)
            report.mark_completed("a.A")
            report.mark_skipped("b.B")
            report.mark_exhausted("c.C")
            report.mark_failed("d.D")
            report.record_published_chunk("abc123", 4242)
            path = report.default_path(tmpdir)
            report.save(path)

            loaded = DynamicAccessExhaustReport.load(path)

            self.assertEqual(loaded.coordinate, "org.example:lib:1.0.0")
            self.assertEqual(loaded.issue_number, 4241)
            self.assertEqual(loaded.class_threshold, 5)
            self.assertEqual(loaded.current_chunk_class_count, 2)
            self.assertEqual(loaded.completed_classes, ["a.A"])
            self.assertEqual(loaded.skipped_classes, ["b.B"])
            self.assertEqual(loaded.exhausted_classes, ["c.C"])
            self.assertEqual(loaded.failed_classes, ["d.D"])
            self.assertEqual(loaded.latest_chunk_commit, "abc123")
            self.assertEqual(loaded.latest_chunk_pull_request, 4242)

    def test_report_path_is_coordinate_derived_below_test_suite(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            path = dynamic_access_exhaust_report_path(tmpdir, "org.example:lib:1.0.0")

        self.assertEqual(
            path,
            os.path.join(
                tmpdir,
                "tests",
                "src",
                "org.example",
                "lib",
                "1.0.0",
                "dynamic-access-exhaust-report.json",
            ),
        )

    def test_processed_classes_unifies_all_terminal_class_sets(self) -> None:
        report = DynamicAccessExhaustReport.create(
            coordinate="org.example:lib:1.0.0",
            issue_number=4241,
        )
        report.mark_completed("a.A")
        report.mark_skipped("b.B")
        report.mark_exhausted("c.C")
        report.mark_failed("d.D")

        self.assertEqual(report.processed_classes(), {"a.A", "b.B", "c.C", "d.D"})

    def test_resolve_workflow_exhaust_report_creates_only_for_chunked_runs(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            report, path = resolve_workflow_exhaust_report(
                repo_path=tmpdir,
                issue_number=4241,
                coordinate="org.example:lib:1.0.0",
                chunk_class_count=0,
            )
            self.assertIsNone(report)
            self.assertIsNone(path)

            report, path = resolve_workflow_exhaust_report(
                repo_path=tmpdir,
                issue_number=4241,
                coordinate="org.example:lib:1.0.0",
                chunk_class_count=2,
            )

            self.assertIsNotNone(report)
            self.assertEqual(report.issue_number, 4241)
            self.assertEqual(path, dynamic_access_exhaust_report_path(tmpdir, "org.example:lib:1.0.0"))
            self.assertIsNone(find_dynamic_access_exhaust_report_path(tmpdir, "org.example:lib:1.0.0"))


if __name__ == "__main__":
    unittest.main()
