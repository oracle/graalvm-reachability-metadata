# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import subprocess
import unittest
from unittest.mock import patch

from git_scripts import make_pr_new_library_support
from utility_scripts.dynamic_access_report import (
    DynamicAccessCallSite,
    DynamicAccessClass,
    DynamicAccessCoverageReport,
)


class MakePrNewLibrarySupportTests(unittest.TestCase):
    def _covered_report(self, covered_calls: int = 1) -> DynamicAccessCoverageReport:
        return DynamicAccessCoverageReport(
            coordinate="org.example:demo:1.0.0",
            has_dynamic_access=True,
            total_calls=covered_calls,
            covered_calls=covered_calls,
            classes=[
                DynamicAccessClass(
                    class_name="org.example.DemoTest",
                    source_file="DemoTest.java",
                    resolved_source_file=None,
                    total_calls=covered_calls,
                    covered_calls=covered_calls,
                    call_sites=[
                        DynamicAccessCallSite(
                            metadata_type="reflection",
                            tracked_api="java.lang.Class#forName(String)",
                            frame="org.example.DemoTest.loadsType(DemoTest.java:42)",
                            line=42,
                            covered=True,
                        )
                        for _ in range(covered_calls)
                    ],
                )
            ],
        )

    def test_build_pull_request_body_explains_large_dynamic_access_metadata_count_gap(self) -> None:
        body = make_pr_new_library_support.build_pull_request_body(
            issue_no=4527,
            coordinates="software.amazon.awssdk:utils:2.34.0",
            model_display_name="gpt-5.5",
            agent_name="pi",
            strategy_name="dynamic_access_main_sources_pi_gpt-5.5",
            run_status="success",
            metrics={
                "metadata_entries": 2,
            },
            library_stats={
                "dynamicAccess": {
                    "coveredCalls": 5,
                    "totalCalls": 5,
                    "coverageRatio": 1.0,
                    "breakdown": {},
                },
            },
        )

        self.assertIn("### Metadata/dynamic-access evidence", body)
        self.assertIn("- Covered dynamic-access calls: 5", body)
        self.assertIn("- Metadata entries: 2", body)
        self.assertIn("A single metadata entry can cover multiple observed call sites", body)

    def test_build_pull_request_body_omits_count_gap_note_for_close_counts(self) -> None:
        with patch("git_scripts.make_pr_new_library_support.format_forge_revision_section", return_value="Forge"):
            body = make_pr_new_library_support.build_pull_request_body(
                issue_no=1,
                coordinates="org.example:demo:1.0.0",
                model_display_name="gpt-5.5",
                agent_name="pi",
                strategy_name="dynamic_access_main_sources_pi_gpt-5.5",
                run_status="success",
                metrics={
                    "metadata_entries": 3,
                },
                library_stats={
                    "dynamicAccess": {
                        "coveredCalls": 5,
                        "totalCalls": 5,
                        "coverageRatio": 1.0,
                        "breakdown": {},
                    },
                },
            )

        self.assertNotIn("### Metadata/dynamic-access evidence", body)

    def test_build_pull_request_body_includes_zero_metadata_dynamic_access_call_site_evidence(self) -> None:
        with patch("git_scripts.make_pr_new_library_support.format_forge_revision_section", return_value="Forge"):
            body = make_pr_new_library_support.build_pull_request_body(
                issue_no=1,
                coordinates="org.example:demo:1.0.0",
                model_display_name="gpt-5.5",
                agent_name="pi",
                strategy_name="dynamic_access_main_sources_pi_gpt-5.5",
                run_status="success",
                metrics={
                    "metadata_entries": 0,
                },
                library_stats={
                    "dynamicAccess": {
                        "coveredCalls": 1,
                        "totalCalls": 1,
                        "coverageRatio": 1.0,
                        "breakdown": {},
                    },
                },
                dynamic_access_report=self._covered_report(),
            )

        self.assertIn("### Metadata/dynamic-access evidence", body)
        self.assertIn("- Covered dynamic-access calls: 1", body)
        self.assertIn("- Metadata entries: 0", body)
        self.assertIn("Covered dynamic-access call sites", body)
        self.assertIn("`org.example.DemoTest`: [reflection] java.lang.Class#forName(String)", body)

    def test_build_pull_request_body_rejects_empty_zero_metadata_dynamic_access_report(self) -> None:
        empty_report = DynamicAccessCoverageReport(
            coordinate="org.example:demo:1.0.0",
            has_dynamic_access=True,
            total_calls=1,
            covered_calls=1,
            classes=[],
        )

        with self.assertRaisesRegex(ValueError, "no covered call-site evidence"):
            make_pr_new_library_support.build_pull_request_body(
                issue_no=1,
                coordinates="org.example:demo:1.0.0",
                model_display_name="gpt-5.5",
                agent_name="pi",
                strategy_name="dynamic_access_main_sources_pi_gpt-5.5",
                run_status="success",
                metrics={
                    "metadata_entries": 0,
                },
                library_stats={
                    "dynamicAccess": {
                        "coveredCalls": 1,
                        "totalCalls": 1,
                        "coverageRatio": 1.0,
                        "breakdown": {},
                    },
                },
                dynamic_access_report=empty_report,
            )

    def test_create_pull_request_rejects_missing_zero_metadata_dynamic_access_report_before_pr_create(self) -> None:
        gh_calls: list[tuple[str, ...]] = []

        def fake_gh(*args, **kwargs):
            del kwargs
            gh_calls.append(args)
            if args[:2] == ("pr", "view"):
                return subprocess.CompletedProcess(args, 1, stdout="", stderr="")
            if args[:2] == ("pr", "create"):
                self.fail("gh pr create should not run without dynamic-access call-site evidence")
            return subprocess.CompletedProcess(args, 0, stdout="", stderr="")

        with patch("git_scripts.make_pr_new_library_support.shutil.which", return_value="/usr/bin/gh"), \
                patch("git_scripts.make_pr_new_library_support.get_origin_owner", return_value="me"), \
                patch("git_scripts.make_pr_new_library_support.gh", side_effect=fake_gh), \
                patch("git_scripts.make_pr_new_library_support.read_pending_metrics", return_value={
                    "status": "success",
                    "strategy_name": "dynamic_access_main_sources_pi_gpt-5.5",
                    "metrics": {
                        "metadata_entries": 0,
                    },
                }), \
                patch("git_scripts.make_pr_new_library_support.get_model_display_name", return_value="gpt-5.5"), \
                patch("git_scripts.make_pr_new_library_support.get_agent_name", return_value="pi"), \
                patch("git_scripts.make_pr_new_library_support.load_library_stats", return_value={
                    "dynamicAccess": {
                        "coveredCalls": 1,
                        "totalCalls": 1,
                        "coverageRatio": 1.0,
                        "breakdown": {},
                    },
                }), \
                patch(
                    "git_scripts.make_pr_new_library_support.load_dynamic_access_coverage_report_for_coordinates",
                    return_value=None,
                ):
            with self.assertRaisesRegex(ValueError, "generated dynamic-access report is required"):
                make_pr_new_library_support.create_pull_request(
                    branch="ai/add-lib-support-org-example-demo-1.0.0",
                    coordinates="org.example:demo:1.0.0",
                    metrics_repo_root="/tmp/metrics",
                    repo_path="/tmp/repo",
                    issue_number=1,
                )

        self.assertEqual([("pr", "view")], [call[:2] for call in gh_calls])


if __name__ == "__main__":
    unittest.main()
