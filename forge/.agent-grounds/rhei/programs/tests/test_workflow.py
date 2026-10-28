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

import json
import os
import subprocess
import unittest

from support import FakeRepository, working_directory
from test_prepare import prepare_arguments

import forge_run  # noqa: E402
from exit_codes import (  # noqa: E402
    AGENT_WORK,
    BAD_INPUT,
    JVM_TESTS_FAIL,
    NOTHING_TO_DO,
    OK,
    RETURN_AUDIT,
    RETURN_MEASURE,
    RETURN_NATIVE,
    RETURN_REPAIR,
    STILL_FAILING,
)
from run_state import RunState, load, read_json, runtime_path, write_json  # noqa: E402

URLS: dict[str, str] = {
    "source-code-url": "file:///nonexistent/sources.jar",
    "test-code-url": "file:///nonexistent/tests.jar",
    "documentation-url": "file:///nonexistent/docs.jar",
    "repository-url": "file:///nonexistent/repo",
}


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

    def fix(self, result: str, visit: int) -> None:
        write_json(runtime_path(self.fake.workspace, "reports", f"fix-{visit}.json"),
                   {"result": result, "changed": [], "summary": "played"})

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
        self.fix("fixed", 1)
        self.assertEqual(RETURN_NATIVE, self.step("fix-route"))
        metadata = self.state.path(os.path.join(self.state.metadata_dir, "reachability-metadata.json"))
        with open(metadata, "w", encoding="utf-8") as handle:
            handle.write('{"reflection": [{"type": "Fixed"}]}\n')
        self.assertEqual(OK, self.step("native"))

        self.assertEqual(OK, self.step("finalize"))
        self.assertEqual("success", self.state.local_ci["status"])
        self.assertEqual(AGENT_WORK, self.step("audit"))
        self.assertIn("Return: 34", self.read("fix-request.md"))
        self.fix("fixed", 2)
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

    def test_fails_label_repairs_then_skips_a_met_goal(self) -> None:
        self.fake.supported_library()
        self.fake.issue("fails-javac-compile")
        self.fake.control({"classes": {"org.example.lib.Codec": 6}, "javaFails": True})
        self.assertEqual(OK, self.step(*prepare_arguments()))
        self.setup_agent()

        self.assertEqual(OK, self.step("setup-check"))
        self.assertEqual(AGENT_WORK, self.step("repair"))
        self.assertIn("Return: 30", self.read("fix-request.md"))
        self.assertIn("JVM test failure", self.read("fix-request.md"))
        self.fix("fixed", 1)
        self.assertEqual(RETURN_REPAIR, self.step("fix-route"))
        self.assertEqual(STILL_FAILING, self.step("repair"))
        self.assertEqual("human-intervention", read_json(runtime_path(self.fake.workspace, forge_run.OUTCOME))["outcome"])

    def test_a_cover_pass_that_breaks_the_tests_costs_only_that_pass(self) -> None:
        self.fake.supported_library()
        self.fake.issue("library-update-request")
        self.fake.control({"classes": {"org.example.lib.Codec": 6}})
        self.assertEqual(OK, self.step(*prepare_arguments()))
        self.setup_agent()
        self.assertEqual(OK, self.step("setup-check"))
        self.assertEqual(OK, self.step("repair"))
        self.assertEqual(AGENT_WORK, self.step("analyze"))

        self.cover("CodecTestBroken")
        self.fake.control({"javaFails": True})
        self.assertEqual(JVM_TESTS_FAIL, self.step("measure"))
        self.assertIn("Return: 31", self.read("fix-request.md"))
        self.fix("fixed", 1)
        self.assertEqual(RETURN_MEASURE, self.step("fix-route"))
        self.assertEqual(AGENT_WORK, self.step("measure"))
        self.assertIn("could not be fixed", self.state.explore.feedback)
        self.assertFalse(os.path.exists(self.state.path(os.path.join(
            self.state.test_dir, "src", "test", "java", "org_example", "CodecTestBroken.java"))))

    def test_previous_coverage_already_met_skips_exploration(self) -> None:
        self.fake.supported_library()
        self.fake.issue("library-update-request")
        self.fake.control({"classes": {"org.example.lib.Codec": 3}})
        self.assertEqual(OK, self.step(*prepare_arguments()))
        self.setup_agent()
        self.assertEqual(OK, self.step("setup-check"))
        self.assertEqual(OK, self.step("repair"))
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
        for task in ("checkMetadataFiles", "test"):
            self.assertIn(f"- ./gradlew {task} -Pcoordinates={self.state.coordinate}", request.split("## Then run")[1])

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
        self.fix("fixed", 1)
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
