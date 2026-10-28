# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Tests for the fix request, which carries facts and never a hint (§AR-forge-workflow-prompts.5)."""
from __future__ import annotations

import os
import tempfile
import unittest

from support import COORDINATE, run_state

import handoff  # noqa: E402
from handoff import Failure  # noqa: E402
from run_state import ROUTE_REPAIR, runtime_path  # noqa: E402

OUTPUT: str = """> Task :nativeTest
    => org.graalvm.nativeimage.MissingReflectionRegistrationError: org.example.Codec
       java.base/java.lang.Class.forName(DynamicHub.java:547)
> Task :nativeTest FAILED"""
GATE: str = "python3 gates.py /workspace"


def headings(text: str) -> list[str]:
    """The request's sections, in order."""
    return [line for line in text.splitlines() if line.startswith("## ")]


class RequestTest(unittest.TestCase):

    def setUp(self) -> None:
        self.workspace: str = tempfile.mkdtemp()
        self.worktree: str = tempfile.mkdtemp()

    def request(self, sender: str, failures: list[Failure], task: str | None = None) -> str:
        handoff.write_fix_request(self.workspace, run_state(ROUTE_REPAIR, self.worktree), sender, failures, task)
        with open(runtime_path(self.workspace, handoff.FIX_REQUEST), encoding="utf-8") as handle:
            return handle.read()

    def test_a_failure_request_carries_only_the_facts(self) -> None:
        for sender, code, task in (("jvm", 30, "javaTest"), ("native", 32, "test")):
            with self.subTest(sender=sender):
                command: str = f"./gradlew {task} -Pcoordinates={COORDINATE}"
                text: str = self.request(sender, [Failure(command, OUTPUT + "\n")])
                self.assertTrue(text.startswith(f"# Fix: {COORDINATE}\nReturn: {code}\n"))
                self.assertEqual(["## Failing commands", "## Output", "## You may edit"], headings(text))
                self.assertIn(f"## Failing commands\n- {command}\n", text)
                self.assertIn(f"### {command}\n\n```text\n{OUTPUT}\n```\n", text)
                self.assertIn(os.path.join(self.worktree, "metadata", "org.example", "lib") + "/**", text)

    def test_the_request_arms_the_senders_loop_guard(self) -> None:
        state = run_state(ROUTE_REPAIR, self.worktree)
        handoff.write_fix_request(self.workspace, state, "native", [Failure("./gradlew test", OUTPUT)])
        self.assertEqual("native", state.pending_request)

    def test_a_gate_failure_names_its_gate_under_the_gate_command(self) -> None:
        scope: str = "diff-scope gate: outside the closed file set: a, b"
        coverage: str = "dynamic-access coverage gate: 1/6 dynamic-access calls covered"
        text: str = self.request("finalize", [Failure(GATE, scope), Failure(GATE, coverage)])
        self.assertEqual(["## Failing commands", "## Output", "## You may edit"], headings(text))
        self.assertIn(f"## Failing commands\n- {GATE}\n\n", text)
        self.assertIn(f"### {GATE}\n\n```text\n{scope}\n{coverage}\n```\n", text)

    def test_the_audit_request_carries_the_audit_as_its_task(self) -> None:
        text: str = self.request("audit", [Failure("./gradlew test", ""), Failure(GATE, "")],
                                 "Check every test and metadata change.")
        self.assertEqual(["## Commands", "## Task", "## You may edit"], headings(text))
        self.assertIn("## Commands\n- ./gradlew test\n- python3 gates.py /workspace\n", text)
        self.assertIn("## Task\n\nCheck every test and metadata change.\n", text)

    def test_request_names_the_commands_a_failure_kept_from_running(self) -> None:
        blocked: tuple[str, ...] = (f"./gradlew checkMetadataFiles -Pcoordinates={COORDINATE}",
                                    f"./gradlew test -Pcoordinates={COORDINATE}")
        text: str = self.request("native", [Failure(f"./gradlew generateMetadata -Pcoordinates={COORDINATE}", "failed",
                                                    blocked=blocked)])
        then_run: str = text.split("## Then run", 1)[1].split("\n## ", 1)[0]
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

