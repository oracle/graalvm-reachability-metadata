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

import contextlib
import io
import os
import unittest
from typing import Any
from unittest.mock import patch

import yaml

from support import COORDINATE, play_fix, run_state
from test_audit import TEMPLATE, FinalizedRun
from test_prepare import prepare_arguments

import forge_run  # noqa: E402
import index_record  # noqa: E402
from utility_scripts.local_ci_verification import LocalCIVerificationError, LocalCIVerificationResult  # noqa: E402
from utility_scripts.native_image_artifact import NativeImageEligibility  # noqa: E402
from exit_codes import (  # noqa: E402
    AGENT_WORK,
    BAD_INPUT,
    NOT_FOR_NATIVE_IMAGE_RECORDED,
    OK,
    RETURN_AUDIT,
    RETURN_FINALIZE,
    RETURN_JVM,
    RETURN_NATIVE,
    RETURN_NATIVE_REPAIR,
    STILL_FAILING,
)
from run_state import read_json, runtime_path  # noqa: E402

METADATA_STEPS: list[str] = ["splitTestOnlyMetadata", "routeForeignMetadata", "checkMetadataFiles"]


class CheckStateTest(FinalizedRun):

    def tasks(self, since: int = 0) -> list[str]:
        """The tasks run outside a native lane, in order, from call `since` on."""
        with open(self.state.path("gradle-calls.log"), encoding="utf-8") as handle:
            calls: list[str] = [line.split()[0] for line in handle if "[" not in line and line.strip() != "--stop"]
        return calls[since:]

    def fixed(self) -> int:
        """Play `fix` for the open request and route back."""
        play_fix(self.fake.workspace)
        return self.step("fix-route")

    def recorded_outcome(self) -> dict[str, Any]:
        return read_json(runtime_path(self.fake.workspace, forge_run.OUTCOME))

    def failing_commands(self) -> list[str]:
        request: str = self.read("fix-request.md")
        section: str = request.split("## Failing commands", 1)[1].split("##", 1)[0]
        return [line[2:] for line in section.splitlines() if line.startswith("- ")]

    def test_prepare_routes_a_record_straight_to_publish(self) -> None:
        """The record bypasses setup and audit (§FS-not-for-native-image-record.2)."""
        with open(TEMPLATE, encoding="utf-8") as handle:
            transitions = yaml.safe_load(handle)["transitions"]
        edges = [edge for edge in transitions if edge["from"] == "prepare"]
        self.assertEqual("publish", next(edge["to"] for edge in edges
                                        if edge["exit_code"] == NOT_FOR_NATIVE_IMAGE_RECORDED))
        self.assertLess(next(i for i, edge in enumerate(edges) if edge["exit_code"] == 22),
                        next(i for i, edge in enumerate(edges) if edge["exit_code"] == "nonzero"))

    def test_the_gate_rejecting_a_marker_is_bad_input(self) -> None:
        """A rejected record never falls through to scaffold (§FS-not-for-native-image-record.2)."""
        self.fake.issue("library-new-request")
        self.fake.artifact.return_value = NativeImageEligibility(True, "Maven packaging is `aar`.")
        rejected: LocalCIVerificationResult = LocalCIVerificationResult(
            status="failed", base_commit="base", failure_gate="index-file-validation")
        with patch.object(index_record, "run_local_ci_verification",
                          side_effect=LocalCIVerificationError(rejected)) as gate:
            with contextlib.redirect_stderr(io.StringIO()) as output:
                self.assertEqual(BAD_INPUT, self.step(*prepare_arguments()))
        self.assertEqual("failed", self.outcome())
        self.assertIn("index-file-validation", output.getvalue())
        worktree: str = gate.call_args.kwargs["repo_path"]
        with open(os.path.join(worktree, "gradle-calls.log"), encoding="utf-8") as handle:
            self.assertNotIn("scaffold", handle.read())
        self.assertEqual(0, gate.call_args.kwargs["max_fixup_attempts"])

    def test_a_non_new_route_without_an_index_is_never_asked(self) -> None:
        """The route excludes the verdict before file inspection
        (§FS-not-for-native-image-record.1)."""
        with patch.object(index_record, "inspect_maven_artifact") as inspect:
            self.assertIsNone(index_record.record_not_for_native_image(
                self.fake.workspace, run_state(route="repair")))
        inspect.assert_not_called()

    def test_a_failed_generation_in_jvm_asks_only_for_generate_metadata(self) -> None:
        self.assertEqual(OK, self.set_up("fails-java-run"))
        self.fake.control({"generateFails": True})
        self.assertEqual(AGENT_WORK, self.step("jvm"))
        self.assertEqual(["javaTest", "generateMetadata"], self.tasks()[-2:])
        self.assertEqual([f"./gradlew generateMetadata -Pcoordinates={COORDINATE}"], self.failing_commands())
        self.assertNotIn("## Then run", self.read("fix-request.md"))
        self.assertEqual(RETURN_JVM, self.fixed())
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
        self.assertEqual(RETURN_NATIVE_REPAIR, self.fixed())
        self.write(os.path.join(self.state.metadata_dir, "reachability-metadata.json"),
                   '{"reflection": [{"type": "Fixed"}]}\n')
        self.assertEqual(OK, self.step("native-repair"))
        self.assertEqual(["test", "test"], self.tasks(before))

    def test_native_repair_asks_once(self) -> None:
        self.assertEqual(OK, self.set_up("fails-native-image-run"))
        self.assertEqual(OK, self.step("jvm"))
        self.fake.control({"nativeFails": True})
        self.assertEqual(AGENT_WORK, self.step("native-repair"))
        self.assertEqual(RETURN_NATIVE_REPAIR, self.fixed())
        self.assertEqual(STILL_FAILING, self.step("native-repair"))
        self.assertEqual("human-intervention", self.outcome())
        self.assertEqual(("native-repair", [f"./gradlew test -Pcoordinates={COORDINATE}"]),
                         (self.recorded_outcome()["sender"],
                          [failure["command"] for failure in self.recorded_outcome()["failures"]]))

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
            self.assertEqual(RETURN_NATIVE, self.fixed())
        self.assertEqual(STILL_FAILING, self.step("native"))
        self.assertEqual("human-intervention", self.outcome())
        self.assertEqual(3, self.state.native_requests)

    def test_native_cannot_fix_spends_the_next_request_and_quotes_it(self) -> None:
        self.repaired()
        self.fake.control({"nativeFails": True})
        self.assertEqual(AGENT_WORK, self.step("native"))
        play_fix(self.fake.workspace, "cannot-fix", "two serialization failures remain")
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
        self.assertEqual(RETURN_FINALIZE, self.fixed())
        self.fake.control({"checkFails": False, "styleFails": "checkstyle"})
        self.assertEqual(AGENT_WORK, self.step("finalize"))
        self.assertEqual([f"./gradlew checkstyle -Pcoordinates={COORDINATE}"], self.failing_commands())
        self.assertEqual(RETURN_FINALIZE, self.fixed())
        self.fake.control({"styleFails": None})
        self.assertEqual(OK, self.step("finalize"))
        self.assertEqual(2, self.state.finalize_requests)

    def test_finalize_needs_a_human_after_its_fifth_fix(self) -> None:
        self.repaired()
        self.fake.control({"styleFails": "checkstyle"})
        for visit in range(1, 6):
            self.assertEqual(AGENT_WORK, self.step("finalize"), visit)
            self.assertIn("Return: 33", self.read("fix-request.md"))
            self.assertEqual(RETURN_FINALIZE, self.fixed())
        self.assertEqual(STILL_FAILING, self.step("finalize"))
        self.assertEqual("human-intervention", self.outcome())
        self.assertEqual(5, self.state.finalize_requests)
        # What stopped the run is recorded for the close-out (§AR-forge-workflow-pipeline.1).
        outcome: dict[str, Any] = self.recorded_outcome()
        self.assertEqual("finalize", outcome["sender"])
        self.assertEqual([{"command": f"./gradlew checkstyle -Pcoordinates={COORDINATE}",
                           "output": "[ERROR] Test.java:1: bad style"}], outcome["failures"])
        self.assertEqual(("fixed", "played"), (outcome["fix_result"], outcome["fix_summary"]))

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
