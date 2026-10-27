# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Tests for `prepare` (§AR-forge-workflow-pipeline.4): route, worktree, version, chunk, request."""
from __future__ import annotations

import argparse
import json
import os
import subprocess
import unittest

from support import COORDINATE, PREVIOUS, FakeRepository, working_directory

import forge_run  # noqa: E402
import prepare  # noqa: E402
from exit_codes import BAD_INPUT, OK  # noqa: E402
from run_state import ROUTE_NEW, ROUTE_REPAIR, ROUTE_UPDATE, load, runtime_path  # noqa: E402

SETUP_AGENT: str = "claude-code[medium]:sonnet"
WORKER_AGENT: str = "pi[high]:openai-codex/gpt-5.6-luna"


def prepare_arguments(setup_agent: str = SETUP_AGENT, worker_agent: str = WORKER_AGENT,
                      passes: str = "20") -> list[str]:
    return ["prepare", "--issue-number", "7", "--setup-agent", setup_agent, "--worker-agent", worker_agent,
            "--bulk-min-classes", "5", "--bulk-passes", "3", "--class-attempts", "3", "--defer-classes", "15",
            "--explore-passes", passes, "--repo-checkout", "../../repo", "--worktree-root", "../worktrees"]


class PrepareTest(unittest.TestCase):

    def setUp(self) -> None:
        self.fake: FakeRepository = FakeRepository()
        self.addCleanup(self.fake.close)

    def prepare(self, *arguments: str) -> int:
        with working_directory(self.fake.workspace):
            return forge_run.main(list(arguments) or prepare_arguments())

    def test_new_library_is_scaffolded_on_its_run_branch(self) -> None:
        self.fake.issue("library-new-request")
        self.assertEqual(OK, self.prepare())
        state = load(self.fake.workspace)
        self.assertEqual((ROUTE_NEW, COORDINATE, "rhei/forge-org.example-lib-2.0.0"), (state.route, state.coordinate, state.branch))
        self.assertTrue(os.path.isdir(state.path(state.test_dir)))
        log: str = subprocess.run(["git", "log", "--format=%s", "-1"], cwd=state.worktree, check=True,
                                  stdout=subprocess.PIPE, text=True).stdout.strip()
        self.assertEqual(f"Prepare {COORDINATE}", log)
        with open(runtime_path(self.fake.workspace, "run.md"), encoding="utf-8") as handle:
            note: str = handle.read()
        self.assertIn(f"# {COORDINATE} (issue #7, library-new-request)", note)
        self.assertIn("--agent-command='claude -p --permission-mode bypassPermissions --effort medium --model sonnet'",
                      note)
        with open(os.path.join(self.fake.workspace, "runtime", "worktree-refs", "ws.forge.yaml"), encoding="utf-8") as handle:
            self.assertEqual(f"path: {state.worktree}\n", handle.read())
        with open(state.path("gradle-calls.log"), encoding="utf-8") as handle:
            calls: list[str] = handle.read().splitlines()
        # An idle daemon of an earlier run would carry its build state into the scaffold.
        self.assertEqual("--stop", calls[0])
        self.assertTrue(calls[1].startswith("scaffold"))

    def test_update_adds_the_version_beside_the_supported_one(self) -> None:
        self.fake.supported_library()
        self.fake.issue("library-update-request")
        self.assertEqual(OK, self.prepare())
        state = load(self.fake.workspace)
        self.assertEqual((ROUTE_UPDATE, PREVIOUS, 2, 6),
                         (state.route, state.previous_coordinate, state.goal_covered_calls, state.goal_total_calls))
        self.assertTrue(os.path.isfile(state.path(os.path.join(state.test_dir, "src", "test", "java", "org_example",
                                                               "CodecTest.java"))))
        with open(state.path(state.index_path), encoding="utf-8") as handle:
            versions = [entry["metadata-version"] for entry in json.load(handle)]
        self.assertEqual(["1.5.0", "2.0.0"], versions)

    def test_a_tested_version_is_measured_against_its_metadata_version(self) -> None:
        self.fake.supported_library(tested_versions=("1.5.0", "1.5.1"))
        self.fake.issue("fails-javac-compile", title="Fails org.example:lib:1.5.1")
        self.assertEqual(OK, self.prepare())
        state = load(self.fake.workspace)
        self.assertEqual((ROUTE_REPAIR, PREVIOUS, 2, 6),
                         (state.route, state.previous_coordinate, state.goal_covered_calls, state.goal_total_calls))

    def test_a_version_owning_its_metadata_is_measured_against_the_one_before(self) -> None:
        self.fake.supported_library()
        self.fake.supported_version("2.0.0", covered=5, total=5)
        self.fake.issue("fails-native-image-run")
        self.assertEqual(OK, self.prepare())
        state = load(self.fake.workspace)
        self.assertEqual((ROUTE_REPAIR, PREVIOUS, 2, 6),
                         (state.route, state.previous_coordinate, state.goal_covered_calls, state.goal_total_calls))

    def test_fails_labels_share_the_repair_route(self) -> None:
        self.fake.supported_library()
        self.fake.issue("fails-native-image-run")
        self.assertEqual(OK, self.prepare())
        self.assertEqual(ROUTE_REPAIR, load(self.fake.workspace).route)

    def test_two_workflow_labels_are_a_bad_input(self) -> None:
        self.fake.issue("fails-javac-compile", extra_labels=("fails-java-run",))
        self.assertEqual(BAD_INPUT, self.prepare())

    def test_title_without_a_coordinate_is_a_bad_input(self) -> None:
        self.fake.issue("library-new-request", title="Support something")
        self.assertEqual(BAD_INPUT, self.prepare())

    def test_chunked_issue_without_exhaust_report_is_a_bad_input(self) -> None:
        self.fake.supported_library()
        self.fake.issue("library-update-request", extra_labels=("chunked-dynamic-access",))
        self.assertEqual(BAD_INPUT, self.prepare())

    def test_setup_agent_must_be_claude_code(self) -> None:
        self.fake.issue("library-new-request")
        self.assertEqual(BAD_INPUT, self.prepare(*prepare_arguments(setup_agent=WORKER_AGENT)))

    def test_codex_targets_are_rejected(self) -> None:
        self.fake.issue("library-new-request")
        self.assertEqual(BAD_INPUT, self.prepare(*prepare_arguments(worker_agent="codex:gpt-5.6-luna")))

    def test_human_reporter_request_is_kept_and_automation_bodies_are_not(self) -> None:
        self.fake.issue("library-new-request", body="Please register org.example.Plugin for reflection.")
        self.assertEqual(OK, self.prepare())
        self.assertTrue(load(self.fake.workspace).has_reporter_request)
        self.assertEqual("", prepare.reporter_request({"author": {"login": "app/github-actions"}, "body": "x"}))

    def test_a_resumed_prepare_keeps_the_run(self) -> None:
        self.fake.issue("library-new-request")
        self.assertEqual(OK, self.prepare())
        first = load(self.fake.workspace)
        self.assertEqual(OK, self.prepare())
        self.assertEqual(first.checkpoint, load(self.fake.workspace).checkpoint)

    def test_a_leftover_run_branch_restarts_from_the_pinned_base(self) -> None:
        self.fake.issue("library-new-request")
        self.assertEqual(OK, self.prepare())
        first = load(self.fake.workspace)
        # The launcher retires the workspace; the run branch and its worktree may outlive it.
        os.remove(runtime_path(self.fake.workspace, "state.json"))
        self.fake.commit("master moved on")
        self.assertEqual(OK, self.prepare())
        second = load(self.fake.workspace)
        self.assertNotEqual(first.base_commit, second.base_commit)
        self.assertEqual(second.base_commit, subprocess.run(
            ["git", "rev-parse", "HEAD~1"], cwd=second.worktree, check=True, stdout=subprocess.PIPE, text=True).stdout.strip())
        self.assertNotEqual(0, subprocess.run(["git", "merge-base", "--is-ancestor", first.checkpoint, "HEAD"],
                                              cwd=second.worktree).returncode)

    def test_validate_inputs_rejects_non_positive_budgets(self) -> None:
        namespace = argparse.Namespace(bulk_min_classes=0, bulk_passes=3, class_attempts=3, defer_classes=15,
                                       explore_passes=20, setup_agent=SETUP_AGENT, worker_agent=WORKER_AGENT)
        self.assertEqual("bulk_min_classes must be positive", prepare.validate_inputs(namespace))


if __name__ == "__main__":
    unittest.main()
