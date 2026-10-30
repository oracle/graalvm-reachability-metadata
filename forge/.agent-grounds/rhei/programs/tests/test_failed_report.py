# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The JVM suites the coverage report runs, and a first report that fails.

§AR-forge-workflow-pipeline.4.5

`jvm` judges the extension suite before `analyze` runs it, a basic pass over an
unlisted library is measured by every suite, and a failed first report stops the
run instead of releasing the claim for a retry that fails the same way.
"""
from __future__ import annotations

import os
import unittest
from typing import Any

import yaml

from support import COORDINATE, play_fix
from test_audit import TEMPLATE, FinalizedRun

import forge_run  # noqa: E402
from build_tools import EXTENSION_SUITE  # noqa: E402
from exit_codes import (  # noqa: E402
    AGENT_WORK,
    INFRASTRUCTURE,
    JVM_TESTS_FAIL,
    OK,
    RETURN_JVM,
    RETURN_MEASURE,
    STILL_FAILING,
)
from run_state import read_json, runtime_path  # noqa: E402

EXTENSION_TEST: str = f"./gradlew codeCoverageTest -Pcoordinates={COORDINATE}"


class FailedReportTest(FinalizedRun):

    def updated(self) -> None:
        """Prepare an update whose test project has the extension suite, and run the setup tail."""
        self.assertEqual(OK, self.set_up("library-update-request"))
        self.extension_test("CodecCoverageTest", "public class CodecCoverageTest {}")

    def extension_test(self, name: str, body: str) -> str:
        relative: str = os.path.join(self.state.test_dir, EXTENSION_SUITE, "src", "test", "java", "org_example",
                                     f"{name}.java")
        self.write(relative, f"package org_example;\n{body}\n")
        return self.state.path(relative)

    def failing_commands(self) -> list[str]:
        section: str = self.read("fix-request.md").split("## Failing commands", 1)[1].split("\n## ", 1)[0]
        return [line[2:] for line in section.splitlines() if line.startswith("- ")]

    def recorded_outcome(self) -> dict[str, Any]:
        return read_json(runtime_path(self.fake.workspace, forge_run.OUTCOME))

    def test_jvm_asks_to_fix_an_extension_suite_the_version_breaks(self) -> None:
        self.updated()
        self.fake.control({"extensionFails": True})
        self.assertEqual(AGENT_WORK, self.step("jvm"))
        self.assertEqual([EXTENSION_TEST], self.failing_commands())
        self.assertIn("Return: 30", self.read("fix-request.md"))
        play_fix(self.fake.workspace)
        self.assertEqual(RETURN_JVM, self.step("fix-route"))
        self.fake.control({"extensionFails": False})
        self.assertEqual(OK, self.step("jvm"))
        with open(self.state.path("gradle-calls.log"), encoding="utf-8") as handle:
            tasks: list[str] = [line.split()[0] for line in handle]
        self.assertEqual(["javaTest", "codeCoverageTest", "generateMetadata"], tasks[-3:])

    def test_a_basic_pass_that_breaks_the_extension_suite_asks_for_a_fix(self) -> None:
        self.updated()
        self.assertEqual(OK, self.step("jvm"))
        self.assertEqual(OK, self.step("native-repair"))
        self.fake.control({"listFails": True})
        self.assertEqual(AGENT_WORK, self.step("analyze"))
        self.assertFalse(self.state.explore.listed)

        broken: str = self.extension_test("CodecBrokenTest", "public class CodecBrokenTest { int a; int a; }")
        self.fake.control({"extensionFails": True})
        self.assertEqual(JVM_TESTS_FAIL, self.step("measure"))
        self.assertEqual([EXTENSION_TEST], self.failing_commands())
        play_fix(self.fake.workspace)
        self.assertEqual(RETURN_MEASURE, self.step("fix-route"))
        # The guard's exception: the pass that could not be fixed is reset, and the run goes on.
        self.assertEqual(OK, self.step("measure"))
        self.assertFalse(os.path.exists(broken))

    def test_a_first_report_that_fails_needs_a_human(self) -> None:
        """The report fails over passing suites, as `No main JAR found` does on every retry."""
        self.updated()
        self.assertEqual(OK, self.step("jvm"))
        self.assertEqual(OK, self.step("native-repair"))
        self.fake.control({"coverageFails": True})
        self.assertEqual(STILL_FAILING, self.step("analyze"))
        outcome: dict[str, Any] = self.recorded_outcome()
        self.assertEqual(("human-intervention", "the first coverage report fails before any cover pass", "analyze"),
                         (outcome["outcome"], outcome["reason"], outcome["sender"]))
        self.assertEqual([f"./gradlew generateDynamicAccessCoverageReport -Pcoordinates={COORDINATE}"],
                         [failure["command"].split(" -Ptck.")[0] for failure in outcome["failures"]])
        self.assertIn("the coverage report failed", outcome["failures"][0]["output"])
        with open(TEMPLATE, encoding="utf-8") as handle:
            edges: list[dict[str, Any]] = [edge for edge in yaml.safe_load(handle)["transitions"]
                                           if edge["from"] == "analyze"]
        self.assertEqual("human-intervention", next(edge["to"] for edge in edges if edge["exit_code"] == 12))
        self.assertLess(next(i for i, edge in enumerate(edges) if edge["exit_code"] == 12),
                        next(i for i, edge in enumerate(edges) if edge["exit_code"] == "nonzero"))

    def test_a_first_report_that_cannot_reach_a_repository_is_retried(self) -> None:
        # A later cycle retries it (§AR-forge-workflow-pipeline.4.1).
        self.updated()
        self.assertEqual(OK, self.step("jvm"))
        self.assertEqual(OK, self.step("native-repair"))
        self.fake.control({"coverageFails": "unreachable"})
        self.assertEqual(INFRASTRUCTURE, self.step("analyze"))
        self.assertEqual("infrastructure", self.recorded_outcome()["kind"])


if __name__ == "__main__":
    unittest.main()
