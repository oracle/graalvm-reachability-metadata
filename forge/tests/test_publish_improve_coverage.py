# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import json
import os
import tempfile
import unittest

from git_scripts.publish_improve_coverage import (
    load_library_update_target_sidecar,
)


class PublishImproveCoverageTests(unittest.TestCase):
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
