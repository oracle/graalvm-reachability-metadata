# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The check states and the fix budgets.

§AR-forge-workflow-pipeline.5, §AR-forge-workflow-pipeline.6

`jvm` and `native-repair` each send one request that names only what failed,
`native` regenerates and tests without `checkMetadataFiles`, and `finalize`
splits and routes before its check and may ask `finalize_fixes` times; `native` may
ask `native_fixes` times, and its `cannot-fix` spends the next request. An audit
edit reruns `test` and the lane, never regenerating (§AR-forge-workflow-pipeline.4.3).
"""
from __future__ import annotations

import os
import unittest

from support import COORDINATE
from test_audit import FinalizedRun

from exit_codes import (  # noqa: E402
    AGENT_WORK,
    OK,
    RETURN_AUDIT,
    RETURN_FINALIZE,
    RETURN_JVM,
    RETURN_NATIVE,
    RETURN_NATIVE_REPAIR,
    STILL_FAILING,
)
from run_state import runtime_path, write_json  # noqa: E402

METADATA_STEPS: list[str] = ["splitTestOnlyMetadata", "routeForeignMetadata", "checkMetadataFiles"]


class CheckStateTest(FinalizedRun):

    def tasks(self, since: int = 0) -> list[str]:
        """The tasks run outside a native lane, in order, from call `since` on."""
        with open(self.state.path("gradle-calls.log"), encoding="utf-8") as handle:
            calls: list[str] = [line.split()[0] for line in handle if "[" not in line and line.strip() != "--stop"]
        return calls[since:]

    def fixed(self, visit: int) -> int:
        """Play `fix` for the open request and route back."""
        write_json(runtime_path(self.fake.workspace, "reports", f"fix-{visit}.json"),
                   {"result": "fixed", "changed": [], "summary": "played"})
        return self.step("fix-route")

    def failing_commands(self) -> list[str]:
        request: str = self.read("fix-request.md")
        section: str = request.split("## Failing commands", 1)[1].split("##", 1)[0]
        return [line[2:] for line in section.splitlines() if line.startswith("- ")]

    def test_a_failed_generation_in_jvm_asks_only_for_generate_metadata(self) -> None:
        self.assertEqual(OK, self.set_up("fails-java-run"))
        self.fake.control({"generateFails": True})
        self.assertEqual(AGENT_WORK, self.step("jvm"))
        self.assertEqual(["javaTest", "generateMetadata"], self.tasks()[-2:])
        self.assertEqual([f"./gradlew generateMetadata -Pcoordinates={COORDINATE}"], self.failing_commands())
        self.assertNotIn("## Then run", self.read("fix-request.md"))
        self.assertEqual(RETURN_JVM, self.fixed(1))
        self.fake.control({"generateFails": False})
        self.assertEqual(OK, self.step("jvm"))
        self.assertIsNone(self.state.pending_request)

    def test_native_repair_tests_the_fix_metadata_without_regenerating(self) -> None:
        self.assertEqual(OK, self.set_up("fails-native-image-run"))
        self.assertEqual(OK, self.step("jvm"))
        self.fake.control({"nativeFails": True})
        before: int = len(self.tasks())
        self.assertEqual(AGENT_WORK, self.step("native-repair"))
        request: str = self.read("fix-request.md")
        self.assertIn("Return: 35", request)
        self.assertIn("```text\norg.graalvm.nativeimage.MissingReflectionRegistrationError: "
                      "org.example.lib.Codec\n```", request)
        self.assertEqual(RETURN_NATIVE_REPAIR, self.fixed(1))
        self.write(os.path.join(self.state.metadata_dir, "reachability-metadata.json"),
                   '{"reflection": [{"type": "Fixed"}]}\n')
        self.assertEqual(OK, self.step("native-repair"))
        self.assertEqual(["test", "test"], self.tasks(before))

    def test_native_repair_asks_once(self) -> None:
        self.assertEqual(OK, self.set_up("fails-native-image-run"))
        self.assertEqual(OK, self.step("jvm"))
        self.fake.control({"nativeFails": True})
        self.assertEqual(AGENT_WORK, self.step("native-repair"))
        self.assertEqual(RETURN_NATIVE_REPAIR, self.fixed(1))
        self.assertEqual(STILL_FAILING, self.step("native-repair"))
        self.assertEqual("human-intervention", self.outcome())

    def test_native_regenerates_and_tests_without_the_metadata_check(self) -> None:
        self.repaired()
        before: int = len(self.tasks())
        self.assertEqual(OK, self.step("native"))
        self.assertEqual(["generateMetadata", "test"], self.tasks(before))

    def test_native_needs_a_human_after_its_third_fix(self) -> None:
        self.repaired()
        self.fake.control({"nativeFails": True})
        for visit in range(1, 4):
            self.assertEqual(AGENT_WORK, self.step("native"), visit)
            self.assertIn("Return: 32", self.read("fix-request.md"))
            self.assertEqual(RETURN_NATIVE, self.fixed(visit))
        self.assertEqual(STILL_FAILING, self.step("native"))
        self.assertEqual("human-intervention", self.outcome())
        self.assertEqual(3, self.state.native_requests)

    def test_native_cannot_fix_spends_the_next_request_and_quotes_it(self) -> None:
        self.repaired()
        self.fake.control({"nativeFails": True})
        self.assertEqual(AGENT_WORK, self.step("native"))
        write_json(runtime_path(self.fake.workspace, "reports", "fix-1.json"),
                   {"result": "cannot-fix", "changed": [], "summary": "two serialization failures remain"})
        self.assertEqual(RETURN_NATIVE, self.step("fix-route"))
        self.assertEqual(AGENT_WORK, self.step("native"))
        self.assertIn("## The previous fix reported\n\ntwo serialization failures remain", self.read("fix-request.md"))
        self.assertEqual(2, self.state.native_requests)

    def test_finalize_splits_and_routes_before_its_check(self) -> None:
        self.repaired()
        before: int = len(self.tasks())
        self.assertEqual(OK, self.step("finalize"))
        self.assertEqual([*METADATA_STEPS, "spotlessApply"], self.tasks(before)[:4])
        self.assertEqual(1, self.gradle_calls("checkMetadataFiles"))

    def test_a_fix_that_exposes_the_next_step_gets_another_request(self) -> None:
        self.repaired()
        self.fake.control({"checkFails": True})
        self.assertEqual(AGENT_WORK, self.step("finalize"))
        self.assertEqual([f"./gradlew checkMetadataFiles -Pcoordinates={COORDINATE}"], self.failing_commands())
        self.assertEqual(RETURN_FINALIZE, self.fixed(1))
        self.fake.control({"checkFails": False, "styleFails": "checkstyle"})
        self.assertEqual(AGENT_WORK, self.step("finalize"))
        self.assertEqual([f"./gradlew checkstyle -Pcoordinates={COORDINATE}"], self.failing_commands())
        self.assertEqual(RETURN_FINALIZE, self.fixed(2))
        self.fake.control({"styleFails": None})
        self.assertEqual(OK, self.step("finalize"))
        self.assertEqual(2, self.state.finalize_requests)

    def test_finalize_needs_a_human_after_its_fifth_fix(self) -> None:
        self.repaired()
        self.fake.control({"styleFails": "checkstyle"})
        for visit in range(1, 6):
            self.assertEqual(AGENT_WORK, self.step("finalize"), visit)
            self.assertIn("Return: 33", self.read("fix-request.md"))
            self.assertEqual(RETURN_FINALIZE, self.fixed(visit))
        self.assertEqual(STILL_FAILING, self.step("finalize"))
        self.assertEqual("human-intervention", self.outcome())
        self.assertEqual(5, self.state.finalize_requests)

    def test_an_audit_edit_reruns_test_and_the_lane_without_regenerating(self) -> None:
        self.repaired()
        self.assertEqual(RETURN_AUDIT, self.audited())
        self.write(os.path.join(self.state.test_dir, "src", "test", "java", "org_example", "CodecTest.java"),
                   "package org_example;\npublic class CodecTest {}\n")
        before: int = len(self.tasks())
        self.assertEqual(OK, self.step("audit"))
        tasks: list[str] = self.tasks(before)
        self.assertEqual(["test", *METADATA_STEPS, "spotlessApply"], tasks[:5])
        self.assertNotIn("generateMetadata", tasks)


if __name__ == "__main__":
    unittest.main()
