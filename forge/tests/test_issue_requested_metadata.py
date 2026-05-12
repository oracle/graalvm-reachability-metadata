# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import json
import os
import tempfile
import unittest

from utility_scripts.issue_requested_metadata import (
    apply_issue_requested_metadata,
    extract_issue_requested_reflection_entries,
    format_issue_requested_test_requirements,
)


HIKARI_ISSUE_CONTEXT = """
The methods in HikariConfig and CodahaleHealthChecker should be exposed.

Caused by: org.graalvm.nativeimage.MissingReflectionRegistrationError: Cannot reflectively invoke method
'public void com.zaxxer.hikari.HikariConfig.setPassword(java.lang.String)'.

{
  "type": "com.zaxxer.hikari.HikariConfig",
  "methods": [
    {
      "name": "setPassword",
      "parameterTypes": [
        "java.lang.String"
      ]
    }
  ]
}

It looks like java.util.UUID[].class also needs to be registered for Hikari to work.
"""


class IssueRequestedMetadataTests(unittest.TestCase):
    def test_extracts_missing_reflection_method_and_class_literal_with_conditions(self) -> None:
        entries = extract_issue_requested_reflection_entries(HIKARI_ISSUE_CONTEXT)

        self.assertIn(
            {
                "condition": {"typeReached": "com.zaxxer.hikari.HikariConfig"},
                "type": "com.zaxxer.hikari.HikariConfig",
                "methods": [
                    {
                        "name": "setPassword",
                        "parameterTypes": ["java.lang.String"],
                    }
                ],
            },
            entries,
        )
        self.assertIn(
            {
                "condition": {"typeReached": "com.zaxxer.hikari.HikariConfig"},
                "type": "java.util.UUID[]",
            },
            entries,
        )

    def test_applies_issue_requested_metadata_to_existing_metadata_file(self) -> None:
        with tempfile.TemporaryDirectory() as metadata_dir:
            metadata_path = os.path.join(metadata_dir, "reachability-metadata.json")
            with open(metadata_path, "w", encoding="utf-8") as metadata_file:
                json.dump(
                    {
                        "reflection": [
                            {
                                "condition": {"typeReached": "com.zaxxer.hikari.HikariConfig"},
                                "type": "com.zaxxer.hikari.HikariConfig",
                                "allDeclaredFields": True,
                            }
                        ]
                    },
                    metadata_file,
                )

            added = apply_issue_requested_metadata(metadata_dir, HIKARI_ISSUE_CONTEXT)

            self.assertEqual(added, 2)
            with open(metadata_path, "r", encoding="utf-8") as metadata_file:
                metadata = json.load(metadata_file)
            hikari_config = [
                entry for entry in metadata["reflection"]
                if entry.get("type") == "com.zaxxer.hikari.HikariConfig"
                and entry.get("condition") == {"typeReached": "com.zaxxer.hikari.HikariConfig"}
            ][0]
            self.assertIn(
                {
                    "name": "setPassword",
                    "parameterTypes": ["java.lang.String"],
                },
                hikari_config["methods"],
            )
            self.assertIn(
                {
                    "condition": {"typeReached": "com.zaxxer.hikari.HikariConfig"},
                    "type": "java.util.UUID[]",
                },
                metadata["reflection"],
            )

    def test_formats_issue_requested_metadata_as_mandatory_test_coverage(self) -> None:
        requirements = format_issue_requested_test_requirements(HIKARI_ISSUE_CONTEXT)

        self.assertIn("Mandatory issue-requested test coverage", requirements)
        self.assertIn("public library API paths", requirements)
        self.assertIn("Do not satisfy these requirements with direct test reflection", requirements)
        self.assertIn(
            "Exercise code that requires `com.zaxxer.hikari.HikariConfig.setPassword(java.lang.String)`",
            requirements,
        )
        self.assertIn(
            "Exercise code that requires `java.util.UUID[]` to be registered",
            requirements,
        )


if __name__ == "__main__":
    unittest.main()
