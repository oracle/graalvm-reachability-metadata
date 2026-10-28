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
RULE: str = "-" * 120
BANNER_START: str = f"""{"=" * 120}
GraalVM Native Image: Generating 'caffeine_tests-tests' (executable)...
{"=" * 120}
[1/8] Initializing...                                                                                   (9.0s @ 0.29GiB)
"""
# The tail of the `test` failure of #10906, trimmed: it starts inside the build banner.
BANNER_TAIL: str = f"""[8/8] Creating image...       [**]                                                                      (3.4s @ 1.08GiB)
{RULE}
Top 10 origins of code area:                                Top 10 object types in image heap:
 991.04KiB o.g.n.runtime/com.oracle.svm.core.code              2.71MiB byte[] for string data
 358.59KiB com.github.benmanes.caffeine.cache                  1.07MiB java.base/java.lang.Class
 221.21KiB org.assertj.core.internal.bytebuddy.jar.asm       436.05KiB byte[] for reflection metadata
   5.01MiB for 343 more packages                               2.26MiB for 2135 more object types
{RULE}
Security report:
 - Binary includes Java deserialization.
{RULE}
Recommendations:
 G1GC: Use the G1 GC ('--gc=G1') for improved latency and throughput.
 FUTR: Use '--future-defaults=all' to prepare for future releases.
 CPU:  Enable more CPU features with '-march=native' for improved performance.
{RULE}
Build artifacts:
 /work/build/native/nativeTestCompile/caffeine_tests-tests (executable, 22.63MiB)
{"=" * 120}
Finished generating 'caffeine_tests-tests' in 1m 1s.
"""
CAFFEINE: str = BANNER_TAIL + """
> Task :nativeTest
com_github_ben_manes_caffeine.caffeine.SIMSTest > cachesSoftValuesUntilInvalidated() FAILED
Failures (1):
Execution failed for task ':nativeTest'.
  JUnit Jupiter:SIMSTest:cachesSoftValuesUntilInvalidated()
    => java.lang.IllegalStateException: SIMS
* Try:
       com.github.benmanes.caffeine.cache.LocalCacheFactory.newFactory(LocalCacheFactory.java:122)
> Run with --stacktrace option to get the stack trace.
       [...]
     Caused by: java.lang.ClassNotFoundException: com.github.benmanes.caffeine.cache.SIMS
       org.graalvm.nativeimage.builder/com.oracle.svm.core.hub.registry.ClassRegistries.forName(ClassRegistries.java:243)
       java.base@25.0.4.1.1/java.lang.Class.forName0(DynamicHub.java:2057)
       java.base@25.0.4.1.1/java.lang.Class.forName(DynamicHub.java:547)
       java.base@25.0.4.1.1/java.lang.invoke.MethodHandles$Lookup.findClass(MethodHandles.java:2708)
       com.github.benmanes.caffeine.cache.LocalCacheFactory.newFactory(LocalCacheFactory.java:112)
       [...]
[        24 tests found           ]
[         2 tests failed          ]

> Task :nativeTest FAILED
> Task :tckTest FAILED
"""


def row(error: str) -> str:
    """The brief the table gives for one error."""
    brief: handoff.Brief = next(brief for brief in handoff.BRIEFS if brief.error == error)
    return f"{brief.error}: {brief.text}"


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

    def test_serialization_failure_registers_the_streamed_class(self) -> None:
        briefs = self.briefs("./gradlew test -Pcoordinates=x",
                             "java.lang.ClassNotFoundException: org.example.Codec\n"
                             "  java.base/java.io.ObjectInputStream.resolveClass(ObjectInputStream.java:812)")
        self.assertEqual(1, len(briefs))
        self.assertTrue(briefs[0].startswith("serialization failure:"))
        self.assertIn("`serializable`", briefs[0])

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

    def test_condition_outside_allowed_packages_needs_a_condition_after_routing(self) -> None:
        output = "TypeReached: org.other.Type\ndoesn't belong to any of the specified packages: [org.example]"
        briefs = self.briefs("./gradlew checkMetadataFiles -Pcoordinates=x", output)
        self.assertEqual([f"condition outside the allowed packages: {handoff.RECONDITION}; "
                          "`routeForeignMetadata` already ran."], briefs)

    def test_test_only_type_kept_by_the_split_needs_a_library_condition(self) -> None:
        output = "TypeReached: org_example.CodecTest\ndoesn't belong to any of the specified packages: [org.example]"
        briefs = self.briefs("./gradlew checkMetadataFiles -Pcoordinates=x", output)
        self.assertEqual([f"test-only type: {handoff.RECONDITION}; `splitTestOnlyMetadata` already ran and kept it."],
                         briefs)
        self.assertNotIn("Run `splitTestOnlyMetadata`", briefs[0])

    def test_an_unroutable_foreign_condition_is_reconditioned(self) -> None:
        output = ("routeForeignMetadata failed for org.example:lib:2.0.0: Cannot relocate metadata entry "
                  "org.other.Type: no resolved runtime dependency JAR contains org.other.Owner")
        briefs = self.briefs("./gradlew routeForeignMetadata -Pcoordinates=x", output)
        self.assertEqual(1, len(briefs))
        self.assertTrue(briefs[0].startswith(f"foreign condition with no supported owner: {handoff.RECONDITION};"))
        self.assertEqual([handoff.DEFAULT_BRIEF], self.briefs("./gradlew routeForeignMetadata -Pcoordinates=x",
                                                              "Cannot resolve runtime dependencies"))

    def test_style_check_is_the_only_brief(self) -> None:
        briefs = self.briefs("./gradlew checkstyle -Pcoordinates=x", "[ERROR] Test.java:1: FAILED something")
        self.assertEqual(["style violation: Run `spotlessApply`, then fix what `checkstyle` reports."], briefs)

    def test_unmatched_failure_gets_the_default(self) -> None:
        self.assertEqual([handoff.DEFAULT_BRIEF], self.briefs("./gradlew finalizeThing", "boom"))


class BannerTest(unittest.TestCase):
    """The Native Image build banner names no error (§AR-forge-workflow-prompts.5)."""

    def briefs(self, output: str) -> list[str]:
        return handoff.briefs_for(Failure("./gradlew test -Pcoordinates=x", output), PACKAGES)

    def test_banner_names_no_flag_and_no_bytecode_generation(self) -> None:
        self.assertEqual([handoff.DEFAULT_BRIEF], self.briefs(BANNER_START + BANNER_TAIL))
        self.assertEqual([handoff.DEFAULT_BRIEF], self.briefs(BANNER_TAIL))

    def test_class_lookup_failing_in_native_frames_is_a_missing_registration(self) -> None:
        self.assertEqual([row("missing registration")], self.briefs(CAFFEINE))

    def test_jvm_class_lookup_failure_is_not_a_missing_registration(self) -> None:
        output = ("java.lang.ClassNotFoundException: org.example.Codec\n"
                  "    at java.base/jdk.internal.loader.BuiltinClassLoader.loadClass(BuiltinClassLoader.java:641)\n"
                  "    at java.base/java.lang.Class.forName0(Native Method)\n")
        self.assertEqual([handoff.DEFAULT_BRIEF], self.briefs(output))

    def test_flag_error_after_the_banner_names_the_flag(self) -> None:
        output = (BANNER_START + "[2/8] Performing analysis...\n" + "=" * 120 + "\n"
                  "Failed generating 'caffeine_tests-tests' after 20.1s.\n"
                  "Error: An object of type 'org.example.Codec' was found in the image heap.\n"
                  "1) If it is intended that objects of type 'org.example.Codec' are persisted in the image heap, add\n"
                  "    '--initialize-at-build-time=org.example.Codec'\n")
        self.assertEqual([row("the native build or run names a flag")], self.briefs(output))

    def test_hidden_class_definition_after_the_banner_reshapes_the_test(self) -> None:
        output = (BANNER_START + BANNER_TAIL + "\n> Task :nativeTest\n"
                  "    => com.oracle.svm.core.jdk.UnsupportedFeatureError: Defining hidden classes at runtime is "
                  "not supported.\n")
        self.assertEqual([row("runtime class definition or bytecode generation")], self.briefs(output))

    def test_only_a_closed_native_banner_is_dropped(self) -> None:
        output = BANNER_START + "Error: use --initialize-at-run-time=org.example.Codec\n"
        self.assertEqual([row("the native build or run names a flag")], self.briefs(output))
        output = "Error: use --initialize-at-run-time=org.example.Codec\nFinished generating test XML results\n"
        self.assertEqual([row("the native build or run names a flag")], self.briefs(output))


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

    def test_request_output_keeps_the_banner_its_briefs_ignore(self) -> None:
        state = run_state(ROUTE_REPAIR, self.worktree)
        handoff.write_fix_request(self.workspace, state, "native",
                                  [Failure("./gradlew test -Pcoordinates=" + COORDINATE, CAFFEINE)])
        with open(runtime_path(self.workspace, handoff.FIX_REQUEST), encoding="utf-8") as handle:
            text: str = handle.read()
        briefs, output = text.split("## Briefs", 1)[1].split("## Output", 1)
        self.assertEqual(f"\n- test: {row('missing registration')}\n\n", briefs)
        self.assertIn(CAFFEINE.rstrip(), output)

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

