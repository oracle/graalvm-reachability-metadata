# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import json
import os
import tempfile
import unittest
from unittest.mock import patch

from ai_workflows.drivers import library_update_router


def _write_file(path: str) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as output_file:
        output_file.write("\n")


class LibraryUpdateRouterTests(unittest.TestCase):
    def test_missing_netty_version_probes_same_line_baseline(self) -> None:
        with tempfile.TemporaryDirectory() as repo:
            group = "io.netty"
            artifact = "netty-common"
            index_dir = os.path.join(repo, "metadata", group, artifact)
            os.makedirs(index_dir, exist_ok=True)
            baseline_entry = {
                "metadata-version": "4.1.115.Final",
                "tested-versions": ["4.1.115.Final", "4.1.130.Final"],
            }
            latest_entry = {
                "latest": True,
                "metadata-version": "5.0.0.Alpha1",
                "tested-versions": ["5.0.0.Alpha1"],
            }
            with open(os.path.join(index_dir, "index.json"), "w", encoding="utf-8") as index_file:
                json.dump([baseline_entry, latest_entry], index_file)
            for version in ["4.1.115.Final", "5.0.0.Alpha1"]:
                _write_file(os.path.join(repo, "metadata", group, artifact, version, "metadata.json"))
                _write_file(os.path.join(repo, "tests", "src", group, artifact, version, "build.gradle"))
            compile_failure = library_update_router._ProbeCommandResult(
                exit_code=1,
                log_path="compile.log",
            )

            with patch.object(
                    library_update_router,
                    "_probe_baseline_suite",
                    return_value=(compile_failure, None, None),
            ) as probe, patch.object(
                    library_update_router,
                    "write_library_update_route",
            ), patch.object(library_update_router, "log_stage") as stage_log:
                route = library_update_router.select_library_update_route(
                    repo,
                    os.path.join(repo, "metrics"),
                    "io.netty:netty-common:4.1.132.Final",
                )

            self.assertEqual(route.selected_driver, library_update_router.ROUTE_FIX_JAVAC)
            self.assertEqual(route.baseline_coordinates, "io.netty:netty-common:4.1.115.Final")
            self.assertEqual(
                probe.call_args.kwargs["baseline_entry"]["metadata-version"],
                "4.1.115.Final",
            )
            messages = [call.args[1] for call in stage_log.call_args_list]
            self.assertTrue(any("nearest prior same major/minor" in message for message in messages))


if __name__ == "__main__":
    unittest.main()
