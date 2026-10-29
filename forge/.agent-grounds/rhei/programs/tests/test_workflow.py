# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""End-to-end tests of the programs in the order `states.yaml` routes them.

A fake repository with a scripted `gradlew` and `gh` stands in for the harness
and GitHub, and the test plays the agents, so each exit code can be checked
against §AR-forge-workflow-pipeline.3.
"""
from __future__ import annotations

import importlib.util
import json
import os
import subprocess
import sys
import unittest
from typing import Any

from support import COORDINATE, REPOSITORY, FakeRepository, play_fix, working_directory
from test_prepare import prepare_arguments

import explore  # noqa: E402
import fix_route  # noqa: E402
import forge_run  # noqa: E402
from utility_scripts.native_image_artifact import NativeImageEligibility  # noqa: E402
from exit_codes import (  # noqa: E402
    AGENT_WORK,
    BAD_INPUT,
    INFRASTRUCTURE,
    JVM_TESTS_FAIL,
    NOTHING_TO_DO,
    NOT_FOR_NATIVE_IMAGE_RECORDED,
    OK,
    RETURN_AUDIT,
    RETURN_FINALIZE,
    RETURN_JVM,
    RETURN_MEASURE,
    RETURN_NATIVE,
    STILL_FAILING,
)
from run_state import RunState, load, read_json, runtime_path, save  # noqa: E402

PUBLISHER: str = os.path.join(REPOSITORY, ".github", "scripts", "forge_pr_publisher", "publisher.py")
URLS: dict[str, str] = {
    "source-code-url": "file:///nonexistent/sources.jar",
    "test-code-url": "file:///nonexistent/tests.jar",
    "documentation-url": "file:///nonexistent/docs.jar",
    "repository-url": "file:///nonexistent/repo",
}


def rendered_body(descriptor: dict[str, Any]) -> str:
    """The pull request body the trusted publisher renders, loaded from its file as Actions runs it."""
    spec: Any = importlib.util.spec_from_file_location("forge_pr_publisher_workflow", PUBLISHER)
    publisher: Any = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = publisher
    spec.loader.exec_module(publisher)
    return publisher.render_publication(descriptor)[1]


class WorkflowTest(unittest.TestCase):

    def setUp(self) -> None:
        self.fake: FakeRepository = FakeRepository()
        self.addCleanup(self.fake.close)

    def step(self, *argv: str) -> int:
        with working_directory(self.fake.workspace):
            return forge_run.main(list(argv))

    @property
    def state(self) -> RunState:
        return load(self.fake.workspace)

    def read(self, name: str) -> str:
        with open(runtime_path(self.fake.workspace, name), encoding="utf-8") as handle:
            return handle.read()

    def setup_agent(self, stray: bool = False) -> None:
        """Play `setup`: fill the URLs, and optionally touch a file outside its scope."""
        state = self.state
        with open(state.path(state.index_path), encoding="utf-8") as handle:
            entries = json.load(handle)
        for entry in entries:
            entry.update(URLS)
            entry["description"] = "an edit setup may not make"
        with open(state.path(state.index_path), "w", encoding="utf-8") as handle:
            json.dump(entries, handle, indent=2)
        if stray:
            with open(state.path("README.md"), "w", encoding="utf-8") as handle:
                handle.write("stray\n")

    def cover(self, test_name: str) -> None:
        """Play `cover`: add one test file."""
        state = self.state
        path = state.path(os.path.join(state.test_dir, "src", "test", "java", "org_example", f"{test_name}.java"))
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(f"package org_example;\npublic class {test_name} {{}}\n")

    def fix(self, result: str) -> None:
        play_fix(self.fake.workspace, result)

    def test_new_library_explores_class_by_class_and_publishes(self) -> None:
        self.fake.issue("library-new-request")
        self.fake.control({"classes": {"org.example.lib.Codec": 1, "org.example.lib.Registry": 1}})
        self.assertEqual(OK, self.step(*prepare_arguments()))
        self.setup_agent(stray=True)

        self.assertEqual(NOTHING_TO_DO, self.step("setup-check"))
        state = self.state
        self.assertFalse(os.path.exists(state.path("README.md")))
        with open(state.path(state.index_path), encoding="utf-8") as handle:
            entry = json.load(handle)[-1]
        self.assertEqual(URLS["source-code-url"], entry["source-code-url"])
        self.assertNotIn("description", entry)
        self.assertTrue(os.path.isfile(state.path(os.path.join(state.test_dir, "user-code-filter.json"))))

        self.assertEqual(AGENT_WORK, self.step("analyze"))
        self.assertEqual(("class", "org.example.lib.Codec"), (self.state.explore.mode, self.state.explore.class_name))
        self.assertIn("class org.example.lib.Codec", self.read("cover-prompt.md"))

        self.cover("CodecTest")
        self.assertEqual(AGENT_WORK, self.step("measure"))
        self.assertEqual(["org.example.lib.Codec"], self.state.explore.completed)
        self.assertIn("class org.example.lib.Registry", self.read("cover-prompt.md"))

        self.cover("Unrelated")
        self.assertEqual(AGENT_WORK, self.step("measure"))
        self.assertFalse(os.path.exists(self.state.path(os.path.join(
            self.state.test_dir, "src", "test", "java", "org_example", "Unrelated.java"))))
        self.assertIn("Unrelated.java", self.read("cover-prompt.md"))
        self.assertEqual(2, self.state.explore.class_attempt)

        self.cover("RegistryTest")
        self.assertEqual(OK, self.step("measure"))

        self.fake.control({"nativeFails": True})
        self.assertEqual(AGENT_WORK, self.step("native"))
        self.assertIn("Return: 32", self.read("fix-request.md"))
        self.fix("fixed")
        self.assertEqual(RETURN_NATIVE, self.step("fix-route"))
        metadata = self.state.path(os.path.join(self.state.metadata_dir, "reachability-metadata.json"))
        with open(metadata, "w", encoding="utf-8") as handle:
            handle.write('{"reflection": [{"type": "Fixed"}]}\n')
        self.assertEqual(OK, self.step("native"))

        self.assertEqual(OK, self.step("finalize"))
        self.assertEqual("success", self.state.local_ci["status"])
        self.assertEqual(AGENT_WORK, self.step("audit"))
        self.assertIn("Return: 34", self.read("fix-request.md"))
        self.fix("fixed")
        self.assertEqual(RETURN_AUDIT, self.step("fix-route"))
        self.assertEqual(OK, self.step("audit"))
        self.assertEqual(OK, self.step("publish"))
        outcome = read_json(runtime_path(self.fake.workspace, forge_run.OUTCOME))
        self.assertEqual("completed", outcome["outcome"])
        branches: str = subprocess.run(["git", "ls-remote", "--heads", self.fake.remote], check=True,
                                       stdout=subprocess.PIPE, text=True).stdout
        self.assertIn("refs/heads/ai/octocat/add-lib-support-org.example-lib-2.0.0-forge-7-", branches)
        descriptor = read_json(self.state.path(os.path.join("stats", "org.example", "lib", "2.0.0",
                                                            "forge-publication.json")))
        self.assertEqual(("library-new-request", "pi"), (descriptor["task_type"], descriptor["metrics"]["agent"]))
        self.assertGreater(descriptor["metrics"]["summary"]["generated_loc"], 0)
        # Three cover passes and two fix requests (§AR-forge-workflow-pipeline.4).
        self.assertEqual(5, descriptor["metrics"]["summary"]["iterations"])

    def test_every_fix_round_files_its_report_and_counts(self) -> None:
        """Run forge-2179-1's shape: one cover pass, then fixes for `native`, `finalize`, and the audit.

        Rhei names every `fix` visit's report the same, so the run state numbers them
        (§AR-forge-workflow-pipeline.4); `finalize`'s round is the gate's fixup
        (§FS-local-ci-equivalent-verification.2).
        """
        self.fake.issue("library-new-request")
        self.fake.control({"classes": {"org.example.lib.Codec": 1}})
        self.assertEqual(OK, self.step(*prepare_arguments()))
        self.setup_agent()
        self.assertEqual(NOTHING_TO_DO, self.step("setup-check"))
        self.assertEqual(AGENT_WORK, self.step("analyze"))
        self.cover("CodecTest")
        self.assertEqual(OK, self.step("measure"))

        self.fake.control({"nativeFails": True})
        self.assertEqual(AGENT_WORK, self.step("native"))
        play_fix(self.fake.workspace, summary="native round")
        self.assertEqual(RETURN_NATIVE, self.step("fix-route"))
        metadata = self.state.path(os.path.join(self.state.metadata_dir, "reachability-metadata.json"))
        with open(metadata, "w", encoding="utf-8") as handle:
            handle.write('{"reflection": [{"type": "Fixed"}]}\n')
        self.assertEqual(OK, self.step("native"))
        self.fake.control({"styleFails": "checkstyle"})
        self.assertEqual(AGENT_WORK, self.step("finalize"))
        test_file: str = os.path.join(self.state.test_dir, "src", "test", "java", "org_example", "CodecTest.java")
        play_fix(self.fake.workspace, summary="finalize round", changed=(test_file,))
        self.assertEqual(RETURN_FINALIZE, self.step("fix-route"))
        self.fake.control({"styleFails": None})
        self.assertEqual(OK, self.step("finalize"))
        self.assertEqual(AGENT_WORK, self.step("audit"))
        play_fix(self.fake.workspace, summary="audit round")
        self.assertEqual(RETURN_AUDIT, self.step("fix-route"))
        self.assertEqual(OK, self.step("audit"))
        self.assertEqual(OK, self.step("publish"))

        self.assertEqual(["native round", "finalize round", "audit round"],
                         [read_json(fix_route.report_path(self.fake.workspace, n))["summary"] for n in (1, 2, 3)])
        stats: str = os.path.join("stats", "org.example", "lib", "2.0.0")
        descriptor = read_json(self.state.path(os.path.join(stats, "forge-publication.json")))
        self.assertEqual((1, 4), (self.state.explore.passes, descriptor["metrics"]["summary"]["iterations"]))
        metrics = read_json(self.state.path(os.path.join(stats, "execution-metrics.json")))
        self.assertEqual([4], [entry["metrics"]["iterations"] for entry in metrics.values()])
        self.assertEqual([{"gate": "finalize", "command": [f"./gradlew checkstyle -Pcoordinates={COORDINATE}"],
                           "commit": None, "changed_paths": [test_file]}],
                         descriptor["local_ci_verification"]["fixups"])
        body: str = rendered_body(descriptor)
        self.assertIn("- Iterations: 4\n", body)
        self.assertIn("- Fixup attempts: 1\n", body)
        # The fix requests and reports stay in the workspace (§FS-forge-run-status).
        self.assertNotIn("Post-Generation Intervention", body)

    def test_non_jvm_artifact_publishes_only_its_index_and_descriptor(self) -> None:
        """Publish carries the verdict without execution metrics
        (§FS-not-for-native-image-record.2)."""
        self.fake.issue("library-new-request")
        reason: str = "Maven packaging is `klib`, not a JVM JAR."
        replacement: str = "Use `lib-jvm` if a JVM artifact is available."
        self.fake.artifact.return_value = NativeImageEligibility(True, reason, replacement)
        self.assertEqual(NOT_FOR_NATIVE_IMAGE_RECORDED, self.step(*prepare_arguments()))
        state: RunState = self.state
        state.explore.deferred_classes = 100
        save(self.fake.workspace, state)

        self.assertEqual(OK, self.step("publish"))
        state = self.state
        descriptor_path: str = "stats/org.example/lib/2.0.0/forge-publication.json"
        descriptor = read_json(state.path(descriptor_path))
        self.assertEqual(("not-for-native-image", "not-for-native-image"),
                         (descriptor["task_type"], descriptor["template_type"]))
        self.assertEqual((reason, replacement),
                         (descriptor["render"]["reason"], descriptor["render"]["replacement"]))
        self.assertEqual([], descriptor["follow_ups"])
        self.assertTrue(descriptor["modifiers"]["chunk_final"])
        self.assertFalse(descriptor["modifiers"]["chunked_dynamic_access"])
        self.assertIsNone(descriptor["render"]["library_stats"])
        self.assertIsNone(descriptor["render"]["dynamic_access"])
        self.assertIsNone(descriptor["render"]["dynamic_access_evidence"])
        self.assertEqual(0, descriptor["metrics"]["summary"]["generated_loc"])
        changed: list[str] = subprocess.run(
            ["git", "diff", "--name-only", state.base_commit, "HEAD"], cwd=state.worktree, check=True,
            stdout=subprocess.PIPE, text=True).stdout.splitlines()
        self.assertEqual([state.index_path, descriptor_path], changed)
        self.assertEqual("completed", read_json(runtime_path(self.fake.workspace, forge_run.OUTCOME))["outcome"])

    def test_fails_label_repairs_then_skips_a_met_goal(self) -> None:
        self.fake.supported_library()
        self.fake.issue("fails-javac-compile")
        self.fake.control({"classes": {"org.example.lib.Codec": 6}, "javaFails": True})
        self.assertEqual(OK, self.step(*prepare_arguments()))
        self.setup_agent()

        self.assertEqual(OK, self.step("setup-check"))
        self.assertEqual(AGENT_WORK, self.step("jvm"))
        request: str = self.read("fix-request.md")
        self.assertIn("Return: 30", request)
        self.assertIn("```text\n2 tests completed, 1 failed\n", request)
        self.assertNotIn("## Briefs", request)
        # `native-repair` sends its own request, so this one names only `javaTest`.
        failing: str = request.split("## Failing commands", 1)[1].split("\n## ", 1)[0]
        self.assertEqual([f"- ./gradlew javaTest -Pcoordinates={COORDINATE}"],
                         [line for line in failing.splitlines() if line.startswith("- ")])
        self.assertNotIn("## Then run", request)
        self.fix("fixed")
        self.assertEqual(RETURN_JVM, self.step("fix-route"))
        self.assertEqual(STILL_FAILING, self.step("jvm"))
        self.assertEqual("human-intervention", read_json(runtime_path(self.fake.workspace, forge_run.OUTCOME))["outcome"])

    def test_a_cover_pass_that_breaks_the_tests_costs_only_that_pass(self) -> None:
        self.fake.supported_library()
        self.fake.issue("library-update-request")
        self.fake.control({"classes": {"org.example.lib.Codec": 6}})
        self.assertEqual(OK, self.step(*prepare_arguments()))
        self.setup_agent()
        self.assertEqual(OK, self.step("setup-check"))
        self.assertEqual(OK, self.step("jvm"))
        self.assertEqual(OK, self.step("native-repair"))
        self.assertEqual(AGENT_WORK, self.step("analyze"))

        self.cover("CodecTestBroken")
        self.fake.control({"javaFails": True})
        self.assertEqual(JVM_TESTS_FAIL, self.step("measure"))
        self.assertIn("Return: 31", self.read("fix-request.md"))
        self.fix("fixed")
        self.assertEqual(RETURN_MEASURE, self.step("fix-route"))
        self.assertEqual(AGENT_WORK, self.step("measure"))
        self.assertIn("could not be fixed", self.state.explore.feedback)
        self.assertFalse(os.path.exists(self.state.path(os.path.join(
            self.state.test_dir, "src", "test", "java", "org_example", "CodecTestBroken.java"))))

    def test_a_broken_extension_suite_is_the_cover_pass_s_failure(self) -> None:
        self.fake.supported_library()
        self.fake.issue("library-update-request")
        self.fake.control({"classes": {"org.example.lib.Codec": 6}})
        self.assertEqual(OK, self.step(*prepare_arguments()))
        self.setup_agent()
        self.assertEqual(OK, self.step("setup-check"))
        self.assertEqual(OK, self.step("jvm"))
        self.assertEqual(OK, self.step("native-repair"))
        self.assertEqual(AGENT_WORK, self.step("analyze"))

        broken = self.state.path(os.path.join(self.state.test_dir, explore.EXTENSION_SUITE, "src", "test", "java",
                                              "org_example", "CodecCoverageTest.java"))
        os.makedirs(os.path.dirname(broken))
        with open(broken, "w", encoding="utf-8") as handle:
            handle.write("package org_example;\npublic class CodecCoverageTest { int a; int a; }\n")
        self.fake.control({"classes": {"org.example.lib.Codec": 6}, "extensionFails": True})
        self.assertEqual(JVM_TESTS_FAIL, self.step("measure"))
        # `javaTest` passes, so the request names only the suite that broke.
        failing: str = self.read("fix-request.md").split("## Failing commands", 1)[1].split("\n## ", 1)[0]
        self.assertEqual([f"- ./gradlew codeCoverageTest -Pcoordinates={COORDINATE}"],
                         [line for line in failing.splitlines() if line.startswith("- ")])
        self.fix("fixed", 1)
        self.assertEqual(RETURN_MEASURE, self.step("fix-route"))
        self.assertEqual(AGENT_WORK, self.step("measure"))
        self.assertIn("could not be fixed", self.state.explore.feedback)
        self.assertFalse(os.path.exists(broken))

    def test_a_report_that_fails_over_passing_suites_is_infrastructure(self) -> None:
        self.fake.supported_library()
        self.fake.issue("library-update-request")
        self.fake.control({"classes": {"org.example.lib.Codec": 6}})
        self.assertEqual(OK, self.step(*prepare_arguments()))
        self.setup_agent()
        self.assertEqual(OK, self.step("setup-check"))
        self.assertEqual(OK, self.step("jvm"))
        self.assertEqual(OK, self.step("native-repair"))
        self.assertEqual(AGENT_WORK, self.step("analyze"))

        self.cover("CodecTest")
        self.fake.control({"classes": {"org.example.lib.Codec": 6}, "coverageFails": True})
        self.assertEqual(INFRASTRUCTURE, self.step("measure"))

    def test_previous_coverage_already_met_skips_exploration(self) -> None:
        self.fake.supported_library()
        self.fake.issue("library-update-request")
        self.fake.control({"classes": {"org.example.lib.Codec": 3}})
        self.assertEqual(OK, self.step(*prepare_arguments()))
        self.setup_agent()
        self.assertEqual(OK, self.step("setup-check"))
        self.assertEqual(OK, self.step("jvm"))
        self.assertEqual(OK, self.step("native-repair"))
        # With the copied CodecTest, two tests cover the previous version's two calls.
        self.cover("CodecSecondTest")
        self.assertEqual(NOTHING_TO_DO, self.step("analyze"))

    def test_a_library_whose_image_does_not_build_yet_takes_one_basic_pass(self) -> None:
        self.fake.issue("library-new-request")
        self.fake.control({"classes": {"org.example.lib.Codec": 1}, "listFails": True, "nativeFails": True})
        self.assertEqual(OK, self.step(*prepare_arguments()))
        self.setup_agent()
        self.assertEqual(NOTHING_TO_DO, self.step("setup-check"))

        self.assertEqual(AGENT_WORK, self.step("analyze"))
        self.assertEqual(("basic", False), (self.state.explore.mode, self.state.explore.listed))
        self.assertIn("could not be listed", self.read("cover-prompt.md"))

        self.cover("ApiTest")
        self.assertEqual(OK, self.step("measure"))
        self.assertIn("ApiTest.java", subprocess.run(
            ["git", "show", "--name-only", "--format=", "HEAD"], cwd=self.state.worktree, check=True,
            stdout=subprocess.PIPE, text=True).stdout)
        self.assertEqual(AGENT_WORK, self.step("native"))
        self.assertIn("Return: 32", self.read("fix-request.md"))

    def test_a_new_library_without_calls_takes_one_measured_basic_pass(self) -> None:
        self.fake.issue("library-new-request")
        self.fake.control({"listsNoCalls": True})
        self.assertEqual(OK, self.step(*prepare_arguments()))
        self.setup_agent()
        self.assertEqual(NOTHING_TO_DO, self.step("setup-check"))

        self.assertEqual(AGENT_WORK, self.step("analyze"))
        self.assertEqual(("basic", True), (self.state.explore.mode, self.state.explore.listed))
        self.assertIn("No dynamic-access call site is left", self.read("cover-prompt.md"))

        self.cover("ApiTest")
        self.assertEqual(OK, self.step("measure"))
        with open(self.state.path("gradle-calls.log"), encoding="utf-8") as handle:
            self.assertIn("generateDynamicAccessCoverageReport", handle.read().splitlines()[-1])

    def test_an_update_without_calls_skips_exploration(self) -> None:
        self.fake.supported_library()
        self.fake.issue("library-update-request")
        # The empty listing, not `classes`, decides what the coverage report counts.
        self.fake.control({"classes": {"org.example.lib.Codec": 6}, "listsNoCalls": True})
        self.assertEqual(OK, self.step(*prepare_arguments()))
        self.setup_agent()
        self.assertEqual(OK, self.step("setup-check"))
        self.assertEqual(OK, self.step("jvm"))
        self.assertEqual(OK, self.step("native-repair"))
        self.assertEqual(NOTHING_TO_DO, self.step("analyze"))
        self.assertEqual((explore.MODE_SKIP, True, 0), (self.state.explore.mode, self.state.explore.listed,
                                                         self.state.explore.total_calls))

    def test_a_failed_metadata_generation_names_the_checks_it_kept_from_running(self) -> None:
        self.fake.issue("library-new-request")
        self.fake.control({"listFails": True})
        self.assertEqual(OK, self.step(*prepare_arguments()))
        self.setup_agent()
        self.assertEqual(NOTHING_TO_DO, self.step("setup-check"))
        self.assertEqual(AGENT_WORK, self.step("analyze"))
        self.cover("ApiTest")
        self.assertEqual(OK, self.step("measure"))

        self.fake.control({"javaFails": True})
        self.assertEqual(AGENT_WORK, self.step("native"))
        request: str = self.read("fix-request.md")
        self.assertIn("- ./gradlew generateMetadata -Pcoordinates=", request.split("## Then run")[0])
        then_run: str = request.split("## Then run", 1)[1].split("\n## ", 1)[0]
        self.assertEqual([f"- ./gradlew test -Pcoordinates={self.state.coordinate}"],
                         [line for line in then_run.splitlines() if line.startswith("- ")])

    def test_a_basic_pass_that_breaks_the_tests_asks_for_a_fix(self) -> None:
        self.fake.issue("library-new-request")
        self.fake.control({"listFails": True})
        self.assertEqual(OK, self.step(*prepare_arguments()))
        self.setup_agent()
        self.assertEqual(NOTHING_TO_DO, self.step("setup-check"))
        self.assertEqual(AGENT_WORK, self.step("analyze"))
        self.cover("BrokenTest")
        self.fake.control({"listFails": True, "javaFails": True})
        self.assertEqual(JVM_TESTS_FAIL, self.step("measure"))
        self.fix("fixed")
        self.assertEqual(RETURN_MEASURE, self.step("fix-route"))
        self.assertEqual(OK, self.step("measure"))
        self.assertFalse(os.path.exists(self.state.path(os.path.join(
            self.state.test_dir, "src", "test", "java", "org_example", "BrokenTest.java"))))

    def test_missing_url_is_a_bad_input(self) -> None:
        self.fake.issue("library-new-request")
        self.assertEqual(OK, self.step(*prepare_arguments()))
        self.assertEqual(BAD_INPUT, self.step("setup-check"))
        self.assertEqual("failed", read_json(runtime_path(self.fake.workspace, forge_run.OUTCOME))["outcome"])

    def test_unchanged_coordinate_has_nothing_to_publish(self) -> None:
        self.fake.issue("library-new-request")
        self.assertEqual(OK, self.step(*prepare_arguments()))
        state = self.state
        subprocess.run(["git", "reset", "-q", "--hard", state.base_commit], cwd=state.worktree, check=True)
        state.local_ci = {"status": "success"}
        self.assertEqual(NOTHING_TO_DO, self.step("publish"))
        self.assertEqual("nothing-to-publish", read_json(runtime_path(self.fake.workspace, forge_run.OUTCOME))["outcome"])
        with open(os.environ["RHEI_RESULT_PATH"], encoding="utf-8") as handle:
            self.assertTrue(handle.read().startswith("publish exited 20"))


if __name__ == "__main__":
    unittest.main()
