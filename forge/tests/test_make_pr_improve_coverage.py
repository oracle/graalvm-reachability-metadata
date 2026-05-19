# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import json
import os
import tempfile
import unittest
from unittest.mock import patch

from git_scripts.make_pr_improve_coverage import (
    assert_no_out_of_scope_changes,
    build_pull_request_body,
    load_library_update_target_sidecar,
)


class MakePrImproveCoverageTests(unittest.TestCase):
    def test_build_pull_request_body_includes_library_update_target_and_validation(self) -> None:
        body = build_pull_request_body(
            issue_no=1412,
            coordinates="org.example:demo:1.0.1",
            model_display_name="gpt-5.5",
            agent_name="pi",
            strategy_name="library_update_pi_gpt-5.5",
            metrics={},
            library_update_target={
                "requested_coordinate": "org.example:demo:1.0.1",
                "match_type": "tested-version",
                "matched_metadata_version": "1.0.0",
                "matched_test_version": "1.0.0",
                "resolved_metadata_version": "1.0.0",
                "resolved_test_version": "1.0.0",
            },
            local_ci_verification={
                "status": "success",
                "commands": [],
                "fixups": [],
            },
        )

        self.assertIn("- Requested coordinate: `org.example:demo:1.0.1`", body)
        self.assertIn("- Match type: `tested-version`", body)
        self.assertIn("- Matched metadata version: `1.0.0`", body)
        self.assertIn("- Matched test version: `1.0.0`", body)
        self.assertIn("- Resolved test version: `1.0.0`", body)
        self.assertIn(
            "- Validation commands: `./gradlew test -Pcoordinates=org.example:demo:1.0.1`, "
            "`./gradlew test -Pcoordinates=org.example:demo:1.0.0`",
            body,
        )
        self.assertIn("- Validation result: `success`", body)

    def test_load_library_update_target_sidecar_reads_pr_only_details(self) -> None:
        with tempfile.TemporaryDirectory() as metrics_root:
            sidecar_path = os.path.join(metrics_root, ".library_update_target.json")
            with open(sidecar_path, "w", encoding="utf-8") as sidecar_file:
                json.dump(
                    {
                        "requested_coordinate": "org.example:demo:1.0.1",
                        "match_type": "tested-version",
                    },
                    sidecar_file,
                )

            sidecar = load_library_update_target_sidecar(metrics_root)

        self.assertEqual(
            sidecar,
            {
                "requested_coordinate": "org.example:demo:1.0.1",
                "match_type": "tested-version",
            },
        )

    def test_finalization_scope_check_allows_expected_target_and_sidecar_paths(self) -> None:
        with patch(
            "git_scripts.make_pr_improve_coverage._status_paths",
            return_value=[
                "tests/src/org.example/demo/1.0.1/src/test/java/DemoTest.java",
                "metadata/org.example/demo/1.0.1/reachability-metadata.json",
                "forge/.library_update_target.json",
            ],
        ):
            assert_no_out_of_scope_changes(
                "/repo",
                [
                    "tests/src/org.example/demo/1.0.1",
                    "metadata/org.example/demo/1.0.1",
                ],
            )

    def test_finalization_scope_check_rejects_old_version_test_edits(self) -> None:
        with patch(
            "git_scripts.make_pr_improve_coverage._status_paths",
            return_value=[
                "tests/src/org.example/demo/1.0.1/src/test/java/DemoTest.java",
                "tests/src/org.example/demo/1.0.0/src/test/java/OldVersionTest.java",
                "tests/src/org.example/demo/1.0.0/user-code-filter.json",
            ],
        ):
            with self.assertRaisesRegex(RuntimeError, "Out-of-scope generated changes"):
                assert_no_out_of_scope_changes(
                    "/repo",
                    ["tests/src/org.example/demo/1.0.1"],
                )


if __name__ == "__main__":
    unittest.main()
