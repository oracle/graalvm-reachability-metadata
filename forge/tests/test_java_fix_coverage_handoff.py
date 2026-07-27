# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import subprocess
import tempfile
import unittest
from unittest.mock import patch

from git_scripts import make_pr_java_run_fix, make_pr_javac_fix
from utility_scripts.dynamic_access_report import DynamicAccessClass, DynamicAccessCoverageReport
from utility_scripts.java_fix_coverage_handoff import (
    DYNAMIC_ACCESS_HANDOFF_KEY,
    build_dynamic_access_handoff,
    ensure_coverage_follow_up_issue,
    format_dynamic_access_handoff_pr_section,
)
from utility_scripts.metrics_writer import read_pending_metrics, write_pending_metrics


class JavaFixCoverageHandoffTests(unittest.TestCase):
    def test_build_handoff_only_above_threshold(self) -> None:
        report = self._report(16)

        handoff = build_dynamic_access_handoff("org.example:lib:2.0.0", report, 15)

        self.assertEqual(
            handoff,
            {
                "coordinate": "org.example:lib:2.0.0",
                "uncovered_class_count": 16,
                "class_threshold": 15,
                "exploration_skipped": True,
            },
        )
        self.assertIsNone(
            build_dynamic_access_handoff("org.example:lib:2.0.0", self._report(15), 15)
        )

    def test_follow_up_is_always_new_and_retry_reuses_only_persisted_issue(self) -> None:
        with tempfile.TemporaryDirectory() as metrics_repo_path:
            write_pending_metrics(
                metrics_repo_path,
                {
                    "status": "success",
                    DYNAMIC_ACCESS_HANDOFF_KEY: {
                        "coordinate": "org.hibernate.orm:hibernate-core:7.0.0",
                        "uncovered_class_count": 90,
                        "class_threshold": 15,
                        "exploration_skipped": True,
                    },
                },
            )
            created = subprocess.CompletedProcess(
                args=["gh"],
                returncode=0,
                stdout="https://github.com/oracle/graalvm-reachability-metadata/issues/9999\n",
            )

            with patch(
                    "utility_scripts.java_fix_coverage_handoff.gh",
                    return_value=created,
            ) as create_issue, patch(
                    "utility_scripts.java_fix_coverage_handoff.ensure_issue_project_status"
            ) as park_issue:
                first = ensure_coverage_follow_up_issue(
                    metrics_repo_path=metrics_repo_path,
                    repair_issue_number=1234,
                    repo="oracle/graalvm-reachability-metadata",
                )
                second = ensure_coverage_follow_up_issue(
                    metrics_repo_path=metrics_repo_path,
                    repair_issue_number=1234,
                    repo="oracle/graalvm-reachability-metadata",
                )
            persisted = read_pending_metrics(metrics_repo_path)

        self.assertEqual(create_issue.call_count, 1)
        create_args = create_issue.call_args.args
        self.assertEqual(create_args[:2], ("issue", "create"))
        self.assertNotIn("list", create_args)
        self.assertIn("library-update-request", create_args)
        body = create_args[create_args.index("--body") + 1]
        self.assertIn("issue #1234", body)
        self.assertIn("90 uncovered classes", body)
        self.assertIn("threshold of 15", body)
        self.assertEqual(park_issue.call_count, 2)
        self.assertEqual(first, second)
        self.assertEqual(first["follow_up_issue_number"], 9999)
        self.assertEqual(
            persisted[DYNAMIC_ACCESS_HANDOFF_KEY]["follow_up_issue_number"],
            9999,
        )

    def test_pr_section_explains_skip_and_links_follow_up(self) -> None:
        section = format_dynamic_access_handoff_pr_section(
            {
                "coordinate": "org.hibernate.orm:hibernate-core:7.0.0",
                "uncovered_class_count": 90,
                "class_threshold": 15,
                "exploration_skipped": True,
                "follow_up_issue_number": 9999,
                "follow_up_issue_url": (
                    "https://github.com/oracle/graalvm-reachability-metadata/issues/9999"
                ),
            },
            "oracle/graalvm-reachability-metadata",
        )

        self.assertIn("Exploration was skipped", section)
        self.assertIn("90 uncovered classes", section)
        self.assertIn("threshold of **15**", section)
        self.assertIn("[library-update-request #9999]", section)
        self.assertIn("Refs: #9999", section)
        self.assertIn("Forge-Unblocks-Issue: #9999", section)

    def test_both_repair_pr_previews_include_deferred_coverage_handoff(self) -> None:
        handoff = {
            "coordinate": "org.hibernate.orm:hibernate-core:7.0.0",
            "uncovered_class_count": 90,
            "class_threshold": 15,
            "exploration_skipped": True,
            "follow_up_issue_number": 9999,
            "follow_up_issue_url": (
                "https://github.com/oracle/graalvm-reachability-metadata/issues/9999"
            ),
        }
        with tempfile.TemporaryDirectory() as metrics_repo_path:
            write_pending_metrics(
                metrics_repo_path,
                {
                    "strategy_name": "test-strategy",
                    "metrics": {},
                    DYNAMIC_ACCESS_HANDOFF_KEY: handoff,
                },
            )

            for pr_module in (make_pr_javac_fix, make_pr_java_run_fix):
                with self.subTest(pr_module=pr_module.__name__), patch.object(
                        pr_module,
                        "get_model_display_name",
                        return_value="Test Model",
                ), patch.object(
                        pr_module,
                        "get_agent_name",
                        return_value="test-agent",
                ), patch.object(
                        pr_module,
                        "format_forge_revision_section",
                        return_value="",
                ), patch.object(
                        pr_module,
                        "format_stats_diff",
                        return_value="",
                ), patch.object(
                        pr_module,
                        "format_bounded_test_diff_section",
                        return_value="",
                ):
                    _title, body, _metrics = pr_module.build_pull_request_preview(
                        old_coordinates="org.hibernate.orm:hibernate-core:6.0.0",
                        new_coordinates="org.hibernate.orm:hibernate-core:7.0.0",
                        group="org.hibernate.orm",
                        artifact="hibernate-core",
                        old_version="6.0.0",
                        new_version="7.0.0",
                        metrics_repo_root=metrics_repo_path,
                        repo_path="/repo",
                        issue_number=1234,
                    )

                self.assertIn("Fixes: #1234", body)
                self.assertIn("Exploration was skipped", body)
                self.assertIn("[library-update-request #9999]", body)
                self.assertIn("Forge-Unblocks-Issue: #9999", body)

    @staticmethod
    def _report(uncovered_class_count: int) -> DynamicAccessCoverageReport:
        return DynamicAccessCoverageReport(
            coordinate="org.example:lib:2.0.0",
            has_dynamic_access=True,
            total_calls=uncovered_class_count,
            covered_calls=0,
            classes=[
                DynamicAccessClass(
                    class_name=f"org.example.Class{index}",
                    source_file=None,
                    resolved_source_file=None,
                    total_calls=1,
                    covered_calls=0,
                    call_sites=[],
                )
                for index in range(uncovered_class_count)
            ],
        )


if __name__ == "__main__":
    unittest.main()
