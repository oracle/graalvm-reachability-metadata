# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import json
import os
import tempfile
import unittest
from unittest.mock import patch

from git_scripts.make_pr_new_library_support import (
    DynamicAccessMetadataEvidence,
    build_pull_request_body,
    load_dynamic_access_metadata_evidence,
)


class MakePrNewLibrarySupportTests(unittest.TestCase):
    def test_build_pull_request_body_explains_large_dynamic_access_metadata_count_gap(self) -> None:
        body = build_pull_request_body(
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
        self.assertIn("single metadata rule can cover multiple observed call sites", body)

    def test_build_pull_request_body_includes_call_sites_and_metadata_rules_for_count_gap(self) -> None:
        body = build_pull_request_body(
            issue_no=3744,
            coordinates="org.osgi:org.osgi.framework:1.8.0",
            model_display_name="gpt-5.5",
            agent_name="pi",
            strategy_name="dynamic_access_main_sources_pi_gpt-5.5",
            run_status="success",
            metrics={
                "metadata_entries": 2,
            },
            library_stats={
                "dynamicAccess": {
                    "coveredCalls": 4,
                    "totalCalls": 4,
                    "coverageRatio": 1.0,
                    "breakdown": {},
                },
            },
            dynamic_access_evidence=DynamicAccessMetadataEvidence(
                covered_call_sites=[
                    "[reflection] java.lang.Class#getMethod(String,Class[]) <- "
                    "org.osgi.framework.FrameworkUtil$FilterImpl.valueOf(java.lang.Object,java.lang.String) "
                    "(line 1144)",
                ],
                metadata_rules=[
                    "when `org.osgi.framework.FrameworkUtil$FilterImpl` is reached, "
                    "make `org.osgi.framework.Version.valueOf(java.lang.String)` available for reflection",
                ],
            ),
        )

        self.assertIn("- Covered call sites:", body)
        self.assertIn("FrameworkUtil$FilterImpl.valueOf", body)
        self.assertIn("- Generated metadata rules:", body)
        self.assertIn("org.osgi.framework.Version.valueOf(java.lang.String)", body)

    def test_build_pull_request_body_explains_covered_calls_with_zero_metadata_entries(self) -> None:
        body = build_pull_request_body(
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
                    "coveredCalls": 4,
                    "totalCalls": 4,
                    "coverageRatio": 1.0,
                    "breakdown": {},
                },
            },
        )

        self.assertIn("### Metadata/dynamic-access evidence", body)
        self.assertIn("- Covered dynamic-access calls: 4", body)
        self.assertIn("- Metadata entries: 0", body)

    def test_load_dynamic_access_metadata_evidence_reads_report_and_metadata_rule(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path:
            report_dir = os.path.join(
                repo_path,
                "tests",
                "src",
                "org.osgi",
                "org.osgi.framework",
                "1.8.0",
                "build",
                "reports",
                "dynamic-access",
            )
            os.makedirs(report_dir)
            with open(os.path.join(report_dir, "dynamic-access-coverage.json"), "w", encoding="utf-8") as file:
                json.dump(
                    {
                        "coordinate": "org.osgi:org.osgi.framework:1.8.0",
                        "hasDynamicAccess": True,
                        "totals": {
                            "coveredCalls": 1,
                            "totalCalls": 1,
                        },
                        "classes": [
                            {
                                "className": "org.osgi.framework.FrameworkUtil$FilterImpl",
                                "sourceFile": "FrameworkUtil.java",
                                "coveredCalls": 1,
                                "totalCalls": 1,
                                "callSites": [
                                    {
                                        "metadataType": "reflection",
                                        "trackedApi": "java.lang.Class#getMethod(String,Class[])",
                                        "frame": (
                                            "org.osgi.framework.FrameworkUtil$FilterImpl."
                                            "valueOf(java.lang.Object,java.lang.String)"
                                        ),
                                        "line": 1144,
                                        "covered": True,
                                    },
                                ],
                            },
                        ],
                    },
                    file,
                )

            metadata_dir = os.path.join(
                repo_path,
                "metadata",
                "org.osgi",
                "org.osgi.framework",
                "1.8.0",
            )
            os.makedirs(metadata_dir)
            with open(os.path.join(metadata_dir, "reachability-metadata.json"), "w", encoding="utf-8") as file:
                json.dump(
                    {
                        "reflection": [
                            {
                                "condition": {
                                    "typeReached": "org.osgi.framework.FrameworkUtil$FilterImpl",
                                },
                                "type": "org.osgi.framework.Version",
                                "methods": [
                                    {
                                        "name": "valueOf",
                                        "parameterTypes": [
                                            "java.lang.String",
                                        ],
                                    },
                                ],
                            },
                        ],
                    },
                    file,
                )

            evidence = load_dynamic_access_metadata_evidence(
                repo_path,
                "org.osgi:org.osgi.framework:1.8.0",
            )

        self.assertIsNotNone(evidence)
        assert evidence is not None
        self.assertEqual(1, len(evidence.covered_call_sites))
        self.assertIn("FrameworkUtil$FilterImpl.valueOf", evidence.covered_call_sites[0])
        self.assertEqual(1, len(evidence.metadata_rules))
        self.assertIn("org.osgi.framework.Version.valueOf(java.lang.String)", evidence.metadata_rules[0])

    def test_build_pull_request_body_omits_count_gap_note_for_close_counts(self) -> None:
        with patch("git_scripts.make_pr_new_library_support.format_forge_revision_section", return_value="Forge"):
            body = build_pull_request_body(
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


if __name__ == "__main__":
    unittest.main()
