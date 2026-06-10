# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import json
import os
import tempfile
import unittest

from git_scripts.make_pr_improve_coverage import (
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


if __name__ == "__main__":
    unittest.main()
