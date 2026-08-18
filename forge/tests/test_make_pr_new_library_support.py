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
    load_dynamic_access_metadata_evidence,
    validate_no_scaffold_placeholders,
    validate_run_quality,
)


class MakePrNewLibrarySupportTests(unittest.TestCase):
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

    def test_validate_run_quality_rejects_suspicious_generated_test_targets(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path, tempfile.TemporaryDirectory() as metrics_repo_path:
            metrics_path = os.path.join(metrics_repo_path, ".pending_metrics.json")
            with open(metrics_path, "w", encoding="utf-8") as file:
                json.dump(
                    {
                        "status": "success",
                        "metrics": {
                            "code_coverage_percent": 5.0,
                        },
                    },
                    file,
                )

            test_file = os.path.join(
                repo_path,
                "tests",
                "src",
                "plexus",
                "plexus-utils",
                "1.0.2",
                "src",
                "test",
                "java",
                "plexus",
                "plexus_utils",
                "ReflectorTest.java",
            )
            os.makedirs(os.path.dirname(test_file), exist_ok=True)
            with open(test_file, "w", encoding="utf-8") as file:
                file.write(
                    """
import org.junit.jupiter.api.Test;

class ReflectorTest {
    @Test
    void objectPropertyFindsAccessorButFailsBeforeInvokingIt() {
        // Version 1.0.2 fails before invoking the accessor.
        assertThatThrownBy(() -> reflector.getObjectProperty(fixture, "value"))
                .isInstanceOf(ReflectorException.class);
    }
}
""".lstrip()
                )

            with self.assertRaisesRegex(ValueError, "suspicious generated test target"):
                validate_run_quality("plexus:plexus-utils:1.0.2", metrics_repo_path, repo_path)

    def test_validate_no_scaffold_placeholders_rejects_placeholder_text(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path:
            test_file = os.path.join(
                repo_path,
                "tests",
                "src",
                "org.example",
                "demo",
                "1.0.0",
                "src",
                "test",
                "java",
                "org_example",
                "demo",
                "DemoTest.java",
            )
            os.makedirs(os.path.dirname(test_file), exist_ok=True)
            with open(test_file, "w", encoding="utf-8") as file:
                file.write(
                    """
package org_example.demo;

class DemoTest {
    void test() {
        System.out.println("This is just a placeholder, implement your test");
    }
}
""",
                )

            with self.assertRaisesRegex(ValueError, "scaffold placeholder remains"):
                validate_no_scaffold_placeholders("org.example:demo:1.0.0", repo_path)


if __name__ == "__main__":
    unittest.main()
