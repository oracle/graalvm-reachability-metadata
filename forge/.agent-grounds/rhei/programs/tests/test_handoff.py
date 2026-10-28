# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Tests for the fix request and its brief table (§AR-forge-workflow-prompts.5)."""
from __future__ import annotations

import os
import tempfile
import unittest

from support import COORDINATE, run_state

import handoff  # noqa: E402
from handoff import Failure  # noqa: E402
from run_state import ROUTE_REPAIR, runtime_path  # noqa: E402

PACKAGES: set[str] = {"org_example"}


class BriefTest(unittest.TestCase):

    def briefs(self, command: str, output: str) -> list[str]:
        return handoff.briefs_for(Failure(command, output), PACKAGES)

    def test_compilation_error_moves_tests_to_the_new_api(self) -> None:
        briefs = self.briefs("./gradlew javaTest -Pcoordinates=x",
                             "> Task :compileTestJava FAILED\nerror: cannot find symbol")
        self.assertEqual(["test compilation error: Move the tests to the API this version has, keeping what each "
                          "asserts."], briefs)

    def test_jvm_test_failure_adapts_the_test(self) -> None:
        briefs = self.briefs("./gradlew javaTest -Pcoordinates=x", "3 tests completed, 1 failed")
        self.assertEqual(1, len(briefs))
        self.assertTrue(briefs[0].startswith("JVM test failure:"))

    def test_missing_registration_adds_the_entry(self) -> None:
        briefs = self.briefs("./gradlew test -Pcoordinates=x",
                             "org.graalvm.nativeimage.MissingReflectionRegistrationError: org.example.Codec")
        self.assertEqual(1, len(briefs))
        self.assertIn("typeReached", briefs[0])
        self.assertTrue(briefs[0].startswith("missing registration:"))

    def test_missing_registration_outside_native_test_is_not_matched(self) -> None:
        briefs = self.briefs("./gradlew javaTest -Pcoordinates=x", "MissingReflectionRegistrationError")
        self.assertEqual([handoff.DEFAULT_BRIEF], briefs)

    def test_url_protocol_names_a_flag(self) -> None:
        briefs = self.briefs("./gradlew test -Pcoordinates=x",
                             "Accessing an URL protocol that was not enabled; add --enable-url-protocols=https")
        self.assertIn("the native build or run names a flag: Add it if it is on the allowed list, "
                      "in its narrowest form.", briefs)

    def test_runtime_class_definition_reshapes_the_test(self) -> None:
        briefs = self.briefs("./gradlew test -Pcoordinates=x", "Classes cannot be defined at runtime")
        self.assertTrue(any(brief.startswith("runtime class definition") for brief in briefs))

    def test_condition_outside_allowed_packages_regenerates(self) -> None:
        output = "TypeReached: org.other.Type\ndoesn't belong to any of the specified packages: [org.example]"
        briefs = self.briefs("./gradlew checkMetadataFiles -Pcoordinates=x", output)
        self.assertEqual(1, len(briefs))
        self.assertTrue(briefs[0].startswith("condition outside the allowed packages:"))

    def test_test_only_type_splits_the_metadata(self) -> None:
        output = "TypeReached: org_example.CodecTest\ndoesn't belong to any of the specified packages: [org.example]"
        briefs = self.briefs("./gradlew checkMetadataFiles -Pcoordinates=x", output)
        self.assertEqual(["test-only type: Run `splitTestOnlyMetadata`."], briefs)

    def test_style_check_is_the_only_brief(self) -> None:
        briefs = self.briefs("./gradlew checkstyle -Pcoordinates=x", "[ERROR] Test.java:1: FAILED something")
        self.assertEqual(["style violation: Run `spotlessApply`, then fix what `checkstyle` reports."], briefs)

    def test_unmatched_failure_gets_the_default(self) -> None:
        self.assertEqual([handoff.DEFAULT_BRIEF], self.briefs("./gradlew finalizeThing", "boom"))


class RequestTest(unittest.TestCase):

    def setUp(self) -> None:
        self.workspace: str = tempfile.mkdtemp()
        self.worktree: str = tempfile.mkdtemp()

    def test_request_carries_return_code_commands_briefs_output_and_scope(self) -> None:
        state = run_state(ROUTE_REPAIR, self.worktree)
        failures = [Failure("./gradlew checkMetadataFiles -Pcoordinates=" + COORDINATE, "all fine?"),
                    Failure("./gradlew test -Pcoordinates=" + COORDINATE, "MissingReflectionRegistrationError")]
        handoff.write_fix_request(self.workspace, state, "native", failures)
        with open(runtime_path(self.workspace, handoff.FIX_REQUEST), encoding="utf-8") as handle:
            text: str = handle.read()
        self.assertTrue(text.startswith(f"# Fix: {COORDINATE}\nReturn: 32\n"))
        self.assertIn("- ./gradlew test -Pcoordinates=" + COORDINATE, text)
        self.assertIn("- test: missing registration:", text)
        self.assertIn("- checkMetadataFiles: Make the command pass.", text)
        self.assertIn("MissingReflectionRegistrationError", text)
        self.assertIn(os.path.join(self.worktree, "metadata", "org.example", "lib") + "/**", text)
        self.assertEqual("native", state.pending_request)

    def test_request_names_the_commands_a_failure_kept_from_running(self) -> None:
        state = run_state(ROUTE_REPAIR, self.worktree)
        blocked = ("./gradlew checkMetadataFiles -Pcoordinates=" + COORDINATE, "./gradlew test -Pcoordinates=" + COORDINATE)
        handoff.write_fix_request(self.workspace, state, "native",
                                  [Failure("./gradlew generateMetadata -Pcoordinates=" + COORDINATE, "failed", blocked=blocked)])
        with open(runtime_path(self.workspace, handoff.FIX_REQUEST), encoding="utf-8") as handle:
            text: str = handle.read()
        then_run: str = text.split("## Then run", 1)[1].split("## Briefs", 1)[0]
        self.assertEqual([f"- {command}" for command in blocked],
                         [line for line in then_run.splitlines() if line.startswith("- ")])

    def test_measure_may_edit_only_the_tests(self) -> None:
        state = run_state(ROUTE_REPAIR, self.worktree)
        self.assertEqual([os.path.join(self.worktree, state.test_dir) + "/**"],
                         handoff.editable_paths(state, "measure"))

    def test_finalize_may_also_edit_the_stats(self) -> None:
        paths = handoff.editable_paths(run_state(ROUTE_REPAIR, self.worktree), "finalize")
        self.assertTrue(paths[-1].endswith(os.path.join("stats", "org.example", "lib") + "/**"))

    def test_loop_guard_trips_only_for_the_same_sender(self) -> None:
        state = run_state(ROUTE_REPAIR, self.worktree, pending_request="native")
        self.assertTrue(handoff.guard_tripped(state, "native"))
        self.assertFalse(handoff.guard_tripped(state, "finalize"))

    def test_placeholder_satisfies_the_declared_output(self) -> None:
        handoff.write_placeholder(self.workspace, "native")
        self.assertTrue(os.path.isfile(runtime_path(self.workspace, handoff.FIX_REQUEST)))


if __name__ == "__main__":
    unittest.main()

