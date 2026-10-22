# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The coordinate's native lanes `finalize` runs (§AR-forge-workflow-pipeline.4.4).

The lanes come from the real `ci.json`, and the fake harness runs the real
`run-consecutive-tests.sh`, so a failing lane's fix request carries the command
the script itself prints, with the lane's JDK in front.
"""
from __future__ import annotations

import importlib.util
import os
import subprocess
import sys
import unittest
from typing import Any

from support import REPOSITORY
from test_audit import FinalizedRun

from exit_codes import AGENT_WORK, OK, RETURN_AUDIT  # noqa: E402
from run_state import RunState, read_json  # noqa: E402

COORDINATE: str = "org.example:lib:2.0.0"


def load_publisher() -> Any:
    path: str = os.path.join(REPOSITORY, ".github", "scripts", "forge_pr_publisher", "publisher.py")
    spec = importlib.util.spec_from_file_location("forge_pr_publisher_matrix", path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class MatrixTest(FinalizedRun):

    def lanes(self) -> list[str]:
        """The `test` runs of the lanes, as `<version> <mode> <JAVA_HOME>`; each is the run's coordinate."""
        with open(self.state.path("gradle-calls.log"), encoding="utf-8") as handle:
            runs: list[str] = [line.rstrip("]\n") for line in handle if line.startswith("clean test ")]
        self.assertEqual({f"clean test -Pcoordinates={COORDINATE}"}, {run.split(" [", 1)[0] for run in runs})
        return [run.split(" [", 1)[1] for run in runs]

    def statuses(self, state: RunState) -> list[tuple[str, str, str]]:
        return [(entry["java"], entry["nativeImageMode"], entry["status"]) for entry in state.local_ci["test_matrix"]]

    def test_finalize_runs_the_three_lanes_of_the_coordinate_only(self) -> None:
        self.repaired("library-update-request")
        self.assertEqual(OK, self.step("finalize"))
        ea: str = os.environ["GRAALVM_HOME_LATEST_EA"]
        self.assertEqual([f"2.0.0 current-defaults {os.environ['GRAALVM_HOME_25_0']}",
                          f"2.0.0 current-defaults {ea}", f"2.0.0 future-defaults-all {ea}"], self.lanes())
        self.assertEqual([("25", "current-defaults", "passed"), ("latest-ea", "current-defaults", "passed"),
                          ("latest-ea", "future-defaults-all", "passed")], self.statuses(self.state))
        # Neither the changed-metadata matrix nor Spring AOT runs.
        for task in ("generateChangedMetadataTestMatrix", "generateAffectedSpringTestMatrix"):
            self.assertEqual(0, self.gradle_calls(task))

    def test_a_failing_lane_is_a_fix_request_that_reruns_exactly_it(self) -> None:
        self.repaired()
        self.fake.control({"matrixFails": "future-defaults-all"})
        self.assertEqual(AGENT_WORK, self.step("finalize"))
        ea: str = os.environ["GRAALVM_HOME_LATEST_EA"]
        command: str = (f"env GRAALVM_HOME={ea} JAVA_HOME={ea} GVM_TCK_LV=2.0.0 "
                        f"GVM_TCK_NATIVE_IMAGE_MODE=future-defaults-all ./gradlew clean test -Pcoordinates={COORDINATE}")
        request: str = self.read("fix-request.md")
        self.assertIn(f"- {command}\n", request)
        self.assertIn("- test: missing registration:", request)
        rerun = subprocess.run(command, shell=True, cwd=self.state.worktree, stdout=subprocess.PIPE, text=True)
        self.assertEqual(1, rerun.returncode)
        self.assertEqual(f"2.0.0 future-defaults-all {ea}", self.lanes()[-1])
        # The next visit runs every lane again.
        self.fake.control({"matrixFails": None})
        before: int = len(self.lanes())
        self.assertEqual(OK, self.step("finalize"))
        self.assertEqual(3, len(self.lanes()) - before)

    def test_a_missing_latest_ea_skips_its_entries_with_a_record(self) -> None:
        del os.environ["GRAALVM_HOME_LATEST_EA"]
        self.repaired()
        self.assertEqual(OK, self.step("finalize"))
        self.assertEqual([f"2.0.0 current-defaults {os.environ['GRAALVM_HOME_25_0']}"], self.lanes())
        state: RunState = self.state
        self.assertEqual([("25", "current-defaults", "passed"), ("latest-ea", "current-defaults", "skipped"),
                          ("latest-ea", "future-defaults-all", "skipped")], self.statuses(state))
        self.assertEqual("GRAALVM_HOME_LATEST_EA is not set", state.local_ci["test_matrix"][1]["reason"])
        self.assertEqual(AGENT_WORK, self.step("audit"))
        self.assertEqual(RETURN_AUDIT, self.routed())
        self.assertEqual(OK, self.step("audit"))
        self.assertEqual(OK, self.step("publish"))
        descriptor: dict[str, Any] = read_json(self.state.path("stats/org.example/lib/2.0.0/forge-publication.json"))
        body: str = load_publisher()._format_local_ci_verification_section(descriptor["local_ci_verification"])
        self.assertIn("- Native test matrix run locally:", body)
        self.assertIn("Java `latest-ea`, `future-defaults-all`: `org.example:lib:2.0.0` `2.0.0` — skipped "
                      "(GRAALVM_HOME_LATEST_EA is not set)", body)

    def test_the_audit_rerun_includes_the_lanes(self) -> None:
        self.repaired()
        self.assertEqual(RETURN_AUDIT, self.audited())
        state: RunState = self.state
        self.write(os.path.join(state.test_dir, "src", "test", "java", "org_example", "CodecTest.java"),
                   "package org_example;\npublic class CodecTest {}\n")
        self.assertEqual(OK, self.step("audit"))
        self.assertEqual(6, len(self.lanes()))


if __name__ == "__main__":
    unittest.main()
