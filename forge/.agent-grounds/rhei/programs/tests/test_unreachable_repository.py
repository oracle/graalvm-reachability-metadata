# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""A repository outage in any Gradle build of a run is an infrastructure failure.

§FS-human-intervention-policy, §AR-forge-workflow-pipeline.4.1

The runner raises it before the step reads the failure, so no step turns an
outage into a fix request, a basic explore mode, or a stop that needs a human;
the run ends as infrastructure and a later cycle retries.
"""
from __future__ import annotations

import os
import unittest
from typing import Any

from test_audit import FinalizedRun

import forge_run  # noqa: E402
from cover_prompts import MODE_BASIC  # noqa: E402
from exit_codes import AGENT_WORK, INFRASTRUCTURE, OK  # noqa: E402
from run_state import read_json, runtime_path  # noqa: E402


class UnreachableRepositoryTest(FinalizedRun):

    def assert_retried(self, step: str, task: str) -> None:
        """`step` fails as infrastructure when `task` cannot reach Maven Central, and asks no one for a fix."""
        rounds: int = len(self.state.fix_rounds)
        self.fake.control({"unreachableIn": task})
        self.assertEqual(INFRASTRUCTURE, self.step(step))
        outcome: dict[str, Any] = read_json(runtime_path(self.fake.workspace, forge_run.OUTCOME))
        self.assertEqual(("infrastructure", f"{step}: infrastructure failure"), (outcome["kind"], outcome["reason"]))
        self.assertEqual(rounds, len(self.state.fix_rounds))

    def lanes_ran(self) -> int:
        """How many native lanes ran `test`; a lane's call names its mode in brackets."""
        with open(self.state.path("gradle-calls.log"), encoding="utf-8") as handle:
            return sum(1 for line in handle if "test" in line.split() and "[" in line)

    def test_jvm_generate_metadata(self) -> None:
        self.assertEqual(OK, self.set_up("fails-java-run"))
        self.assert_retried("jvm", "generateMetadata")

    def test_native_repair(self) -> None:
        self.assertEqual(OK, self.set_up("fails-native-image-run"))
        self.assertEqual(OK, self.step("jvm"))
        self.assert_retried("native-repair", "test")

    def test_analyze_listing_takes_no_basic_mode(self) -> None:
        self.repaired("library-update-request")
        self.assert_retried("analyze", "generateDynamicAccessReport")
        self.assertNotEqual(MODE_BASIC, self.state.explore.mode)

    def test_measure_suite_rerun(self) -> None:
        self.repaired("library-update-request")
        self.fake.control({"classes": {"org.example.lib.Codec": 6}})
        self.assertEqual(AGENT_WORK, self.step("analyze"))
        self.write(os.path.join(self.state.test_dir, "src", "test", "java", "org_example", "CodecMoreTest.java"),
                   "package org_example;\npublic class CodecMoreTest {}\n")
        # The report fails, and so does the `javaTest` rerun that would blame the pass.
        self.fake.control({"coverageFails": True})
        self.assert_retried("measure", "javaTest")
        self.assertEqual(0, self.state.explore.passes)

    def test_native(self) -> None:
        self.repaired()
        self.assert_retried("native", "generateMetadata")
        self.assertEqual(0, self.state.native_requests)

    def test_finalize_step(self) -> None:
        self.repaired()
        self.assert_retried("finalize", "checkstyle")
        self.assertEqual(0, self.state.finalize_requests)

    def test_finalize_native_lane(self) -> None:
        # The pre-publication gate's lanes run `test` under each native-image mode.
        self.repaired()
        self.assert_retried("finalize", "test")
        self.assertGreater(self.lanes_ran(), 0)
        self.assertEqual(0, self.state.finalize_requests)

    def test_audit_rerun(self) -> None:
        self.repaired()
        self.assertEqual(OK, self.step("finalize"))
        self.assertEqual(AGENT_WORK, self.step("audit"))
        self.routed()
        self.write(os.path.join(self.state.test_dir, "src", "test", "java", "org_example", "CodecTest.java"),
                   "package org_example;\npublic class CodecTest {}\n")
        lanes: int = self.lanes_ran()
        self.assert_retried("audit", "test")
        self.assertEqual(lanes, self.lanes_ran())
        self.assertFalse(self.state.audit_passed)


if __name__ == "__main__":
    unittest.main()
