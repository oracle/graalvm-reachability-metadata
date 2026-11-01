# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Tests for what a held or interrupted run leaves a maintainer and the next claim.

A held run keeps its work at a ref of its own that no later run moves
(§FS-human-intervention-policy); an attempt whose launcher died, or that Rhei
ran no state of, keeps its worktree and is resumed (§AR-forge-launcher.1).
"""
from __future__ import annotations

import contextlib
import io
import json
import os
import shutil
import subprocess
import tempfile
import unittest
from typing import Any
from unittest.mock import patch

import forge_metadata
from dispatcher import rhei_workspace, run_launch
from tests.test_forge_launcher import issue, new_claim
from tests.test_gradle_environment import _init_git_repo_with_worktree
from utility_scripts.gradle_environment import FORGE_GRADLE_USER_HOME_ENV, gradle_command_environment

HELD_REF: str = "refs/forge-held/forge-7-1"


def git(cwd: str, *args: str) -> str:
    return subprocess.run(["git", *args], cwd=cwd, check=True, stdout=subprocess.PIPE, text=True).stdout.strip()


def write(path: str, text: str) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(text)


class RunWorktreeCase(unittest.TestCase):
    """A run workspace whose state names a linked worktree on the branch `linked`."""

    def setUp(self) -> None:
        self.root: str = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, self.root, True)
        self.repo: str = os.path.join(self.root, "repo")
        self.worktree: str = os.path.join(self.root, "linked")
        _init_git_repo_with_worktree(self.repo, self.worktree)
        self.workspace: str = os.path.join(self.root, "runs", "forge-7-1")
        write(os.path.join(self.workspace, "runtime", "forge", "state.json"),
              json.dumps({"worktree": self.worktree, "branch": "linked"}))
        environment = patch.dict(os.environ, {FORGE_GRADLE_USER_HOME_ENV: os.path.join(self.root, "gradle-home")})
        environment.start()
        self.addCleanup(environment.stop)
        self.home: str = gradle_command_environment(self.worktree)["GRADLE_USER_HOME"]
        stop = patch("utility_scripts.gradle_dependency_cache.stop_gradle_daemons_of_home")
        stop.start()
        self.addCleanup(stop.stop)

    def finish(self, outcome: dict[str, Any] | None, advanced: bool = True, halt: str = "",
               budget: bool = False) -> dict[str, Any]:
        if outcome is not None:
            write(os.path.join(self.workspace, run_launch.OUTCOME), json.dumps(outcome))
        started = run_launch.Started(workspace=self.workspace, advanced=advanced, halt=halt, budget=budget)
        with contextlib.redirect_stderr(io.StringIO()) as self.stderr, contextlib.redirect_stdout(io.StringIO()):
            return run_launch.finish_run(self.repo, started)

    def held_refs(self) -> list[str]:
        return git(self.repo, "for-each-ref", "--format=%(refname)", "refs/forge-held").splitlines()


class HeldRunTest(RunWorktreeCase):
    """§FS-human-intervention-policy"""

    def edit_worktree(self) -> None:
        write(os.path.join(self.worktree, "README.md"), "fixed by the fix state\n")
        write(os.path.join(self.worktree, "metadata", "reachability-metadata.json"), "{}\n")

    def test_a_held_run_commits_its_uncommitted_work_at_its_own_ref_then_gives_up_its_worktree(self) -> None:
        self.edit_worktree()
        outcome = self.finish({"outcome": "human-intervention", "reason": "native still fails", "sender": "native"})
        self.assertEqual(HELD_REF, outcome["held_ref"])
        self.assertEqual("Held at native: uncommitted work", git(self.repo, "log", "-1", "--format=%s", HELD_REF))
        self.assertEqual("fixed by the fix state", git(self.repo, "show", f"{HELD_REF}:README.md"))
        self.assertEqual("{}", git(self.repo, "show", f"{HELD_REF}:metadata/reachability-metadata.json"))
        # The worktree goes with its Gradle home (§FS-forge-run-requirements.4).
        self.assertFalse(os.path.exists(self.worktree))
        self.assertFalse(os.path.exists(self.home))

    def test_a_rerun_of_the_issue_leaves_the_held_ref_alone(self) -> None:
        self.edit_worktree()
        self.finish({"outcome": "human-intervention", "reason": "the fix could not help", "sender": "finalize"})
        held: str = git(self.repo, "rev-parse", HELD_REF)
        # The next attempt drops the branch, and its `prepare` spawns the worktree again on a fresh base.
        run_launch.release_worktree(self.repo, self.workspace)
        git(self.repo, "worktree", "add", "--quiet", "-B", "linked", self.worktree, "main")
        self.assertEqual(git(self.repo, "rev-parse", "main"), git(self.repo, "rev-parse", "linked"))
        self.assertEqual(held, git(self.repo, "rev-parse", HELD_REF))
        self.assertEqual("fixed by the fix state", git(self.repo, "show", f"{HELD_REF}:README.md"))

    def test_a_held_run_with_nothing_uncommitted_keeps_its_branch_head(self) -> None:
        head: str = git(self.worktree, "rev-parse", "HEAD")
        self.finish({"outcome": "human-intervention", "reason": "audit", "step": "audit"})
        self.assertEqual(head, git(self.repo, "rev-parse", HELD_REF))

    def test_every_outcome_the_close_out_labels_keeps_its_work(self) -> None:
        for outcome in ({"outcome": "failed", "kind": "bad-input", "reason": "jvm: bad input", "step": "jvm"},
                        {"outcome": "nothing-to-publish", "reason": "unchanged", "step": "publish"}):
            with self.subTest(outcome=outcome["outcome"]):
                self.setUp()
                self.edit_worktree()
                self.assertEqual(HELD_REF, self.finish(outcome)["held_ref"])
                self.assertEqual(f"Held at {outcome['step']}: uncommitted work",
                                 git(self.repo, "log", "-1", "--format=%s", HELD_REF))

    def test_an_outcome_that_holds_nothing_keeps_no_ref(self) -> None:
        for outcome in ({"outcome": "completed", "reason": "pushed"},
                        {"outcome": "failed", "kind": "infrastructure", "reason": "x"}):
            with self.subTest(outcome=outcome["outcome"]):
                self.setUp()
                self.assertNotIn("held_ref", self.finish(outcome))
                self.assertEqual([], self.held_refs())
                self.assertFalse(os.path.exists(self.worktree))

    def test_a_held_run_whose_work_cannot_be_committed_keeps_its_worktree(self) -> None:
        self.edit_worktree()
        with open(os.path.join(git(self.worktree, "rev-parse", "--absolute-git-dir"), "index.lock"), "w"):
            pass
        outcome = self.finish({"outcome": "human-intervention", "reason": "native still fails", "sender": "native"})
        self.assertNotIn("held_ref", outcome)
        self.assertEqual([], self.held_refs())
        self.assertTrue(os.path.isfile(os.path.join(self.worktree, "metadata", "reachability-metadata.json")))
        self.assertIn(f"so its worktree {self.worktree} stays", self.stderr.getvalue())

    def test_a_held_ref_another_checkout_keeps_under_the_same_name_is_never_moved(self) -> None:
        git(self.repo, "update-ref", HELD_REF, "main")
        kept: str = git(self.repo, "rev-parse", HELD_REF)
        self.edit_worktree()
        outcome = self.finish({"outcome": "human-intervention", "reason": "native still fails", "sender": "native"})
        self.assertNotIn("held_ref", outcome)
        self.assertEqual(kept, git(self.repo, "rev-parse", HELD_REF))
        self.assertTrue(os.path.isdir(self.worktree))
        self.assertIn("reference already exists", self.stderr.getvalue())

    def test_the_comment_names_the_ref_and_how_to_continue_from_it(self) -> None:
        body: str = run_launch.intervention_comment(
            {"outcome": "human-intervention", "reason": "native still fails", "held_ref": HELD_REF})
        self.assertTrue(body.endswith(
            f"is kept on the Forge host at `{HELD_REF}`. Continue from it on a branch of your own: "
            f"`git worktree add -b <branch> <path> {HELD_REF}`.\n"), body)


class RanNoStateTest(RunWorktreeCase):
    """A `rhei run` that ran no state finished nothing (§AR-forge-launcher.1)."""

    def claimed(self) -> run_launch.Claim:
        return new_claim(7)

    def record(self, name: str, attempt: int) -> str:
        path: str = os.path.join(self.workspace, "runtime", "spawns", f"{name}.json")
        write(path, json.dumps({"attempt": attempt}))
        return path

    def halts(self) -> int:
        with open(os.path.join(self.workspace, rhei_workspace.NO_ADVANCE), encoding="utf-8") as handle:
            return json.load(handle)["halts"]

    def test_the_first_no_advance_halt_keeps_the_worktree_for_the_next_claim(self) -> None:
        outcome = self.finish(None, advanced=False)
        self.assertEqual(run_launch.NO_ADVANCE_KIND, outcome["kind"])
        self.assertEqual(1, self.halts())
        self.assertFalse(os.path.exists(os.path.join(self.workspace, run_launch.OUTCOME)))
        self.assertTrue(os.path.isdir(self.worktree))
        self.assertEqual("linked", git(self.worktree, "branch", "--show-current"))
        self.assertEqual(self.workspace, run_launch.workspace_path(self.root, 7))

    def test_a_second_no_advance_halt_in_a_row_holds_the_attempt_and_keeps_its_work(self) -> None:
        write(os.path.join(self.workspace, "runtime", "state-transitions.log"),
              "forge-7-1.forge native@finalize\n")
        write(os.path.join(self.worktree, "README.md"), "native passed\n")
        refusal: str = "refusing to spawn: task-forge-7-1.forge-finalize.log exists and no spawn record accounts for it"
        self.finish(None, advanced=False, halt=refusal)
        outcome = self.finish(None, advanced=False, halt=refusal)
        self.assertEqual("human-intervention", outcome["outcome"])
        self.assertEqual("finalize", outcome["sender"])
        self.assertEqual(HELD_REF, outcome["held_ref"])
        with open(os.path.join(self.workspace, run_launch.OUTCOME), encoding="utf-8") as handle:
            self.assertEqual("human-intervention", json.load(handle)["outcome"])
        self.assertEqual("Held at finalize: uncommitted work", git(self.repo, "log", "-1", "--format=%s", HELD_REF))
        self.assertEqual("native passed", git(self.repo, "show", f"{HELD_REF}:README.md"))
        self.assertFalse(os.path.exists(self.worktree))
        # The next claim starts a new attempt rather than resuming this one.
        self.assertEqual(os.path.join(self.root, "runs", "forge-7-2"), run_launch.workspace_path(self.root, 7))
        body: str = run_launch.intervention_comment(outcome)
        self.assertIn("Forge could not finish this issue: Rhei ran no state of forge-7-1 in 2 runs in a row.", body)
        self.assertIn(f"Rhei's halt message:\n\n```text\n{refusal}\n```", body)

    def test_a_state_run_between_two_halts_resets_the_count(self) -> None:
        self.finish(None, advanced=False)
        # A resume ran a state, and its launcher died before it could finish the run.
        self.record("task-forge-7-1.forge-finalize", 1)
        self.assertEqual(run_launch.NO_ADVANCE_KIND, self.finish(None, advanced=False)["kind"])
        self.assertEqual(1, self.halts())
        self.assertTrue(os.path.isdir(self.worktree))

    def test_a_run_that_advances_clears_the_count(self) -> None:
        self.finish(None, advanced=False)
        self.finish({"outcome": "completed", "reason": "pushed"})
        self.assertFalse(os.path.exists(os.path.join(self.workspace, rhei_workspace.NO_ADVANCE)))

    def test_a_budget_halt_never_holds_the_attempt(self) -> None:
        halt: str = "budget halt on tokens: wait for the allowance to reset"
        for _ in range(run_launch.NO_ADVANCE_LIMIT + 1):
            outcome = self.finish(None, advanced=False, halt=halt, budget=True)
            self.assertEqual(run_launch.BUDGET_HALT_KIND, outcome["kind"])
        self.assertIn(f"[budget] {halt}", self.stderr.getvalue())
        self.assertFalse(os.path.exists(os.path.join(self.workspace, rhei_workspace.NO_ADVANCE)))
        self.assertFalse(os.path.exists(os.path.join(self.workspace, run_launch.OUTCOME)))
        self.assertEqual([], self.held_refs())
        self.assertTrue(os.path.isdir(self.worktree))
        self.assertEqual(self.workspace, run_launch.workspace_path(self.root, 7))

    def test_a_budget_halt_leaves_the_count_as_it_was(self) -> None:
        self.finish(None, advanced=False)
        self.finish(None, advanced=False, budget=True)
        self.assertEqual(1, self.halts())
        self.assertEqual("human-intervention", self.finish(None, advanced=False)["outcome"])

    def test_start_run_takes_the_halt_message_from_this_run_s_event_log_only(self) -> None:
        events: str = os.path.join(self.workspace, rhei_workspace.EVENTS)
        records: list[dict[str, Any]] = [
            {"event": "run_started", "seq": 1},
            {"event": "message", "level": "error", "text": "  refusing to spawn: x.log exists"},
            {"event": "message", "level": "info", "text": "No tasks could be advanced."}]
        write(events, "stale\n")
        for name, rewrite, halt in (("a rewritten log", True, "refusing to spawn: x.log exists"),
                                    ("an earlier run's log", False, "")):
            def rhei_run(*_: Any, **__: Any) -> None:
                if rewrite:
                    write(events, "".join(json.dumps(record) + "\n" for record in records))
            with self.subTest(name), patch.object(run_launch.subprocess, "run", side_effect=rhei_run):
                started = run_launch.start_run(self.root, self.claimed(), "claude-code:sonnet", "pi:x/y", [])
                self.assertEqual((False, halt), (started.advanced, started.halt))
        write(events, json.dumps(records[2]) + "\n")
        self.assertEqual("No tasks could be advanced.",
                         rhei_workspace.halt_message(rhei_workspace.run_events(self.workspace), run_launch.QUOTE_LIMIT))

    def test_start_run_tells_a_budget_halt_from_its_event_log(self) -> None:
        events: str = os.path.join(self.workspace, rhei_workspace.EVENTS)
        for name, record, budget in (
                ("a budget halt", {"event": "budget_halt", "dimension": "tokens", "remedy": "wait"}, True),
                ("an error", {"event": "message", "level": "error", "text": "refusing to spawn"}, False)):
            def rhei_run(*_: Any, **__: Any) -> None:
                write(events, json.dumps(record) + "\n")
            with self.subTest(name), patch.object(run_launch.subprocess, "run", side_effect=rhei_run):
                started = run_launch.start_run(self.root, self.claimed(), "claude-code:sonnet", "pi:x/y", [])
                self.assertEqual((False, budget), (started.advanced, started.budget))
        self.assertEqual("budget halt on tokens: wait", rhei_workspace.halt_message(
            [{"event": "budget_halt", "dimension": "tokens", "remedy": "wait"}], run_launch.QUOTE_LIMIT))

    def test_start_run_tells_whether_rhei_ran_a_state(self) -> None:
        rewritten: str = self.record("task-forge-7-1.forge-prepare", 1)
        os.utime(rewritten, ns=(0, 0))
        for name, ran in (("nothing", lambda: None),
                          ("a new record", lambda: self.record("task-forge-7-1.forge-setup", 1)),
                          ("a rewritten record", lambda: self.record("task-forge-7-1.forge-prepare", 2))):
            with self.subTest(name), patch.object(run_launch.subprocess, "run", side_effect=lambda *_, **__: ran()):
                started = run_launch.start_run(self.root, self.claimed(), "claude-code:sonnet", "pi:x/y", [])
                self.assertEqual(name != "nothing", started.advanced)

    def test_the_launcher_backs_off_after_the_first_halt_and_holds_the_issue_after_the_second(self) -> None:
        codes: list[int] = []
        with patch.object(forge_metadata, "REPO_DIR", self.repo), \
                patch.object(forge_metadata, "FORGE_DIR", os.path.join(self.root, "forge")), \
                patch.object(forge_metadata, "fetch_candidates", return_value=[("library-new-request", issue(7))]), \
                patch.object(forge_metadata, "resolve_authenticated_user", return_value="forge-bot"), \
                patch.object(forge_metadata, "claim", return_value=self.claimed()), \
                patch.object(forge_metadata, "start_run",
                             return_value=run_launch.Started(workspace=self.workspace, advanced=False, halt="x")), \
                patch.object(run_launch, "add_issue_label") as label, \
                patch.object(run_launch, "post_issue_comment"), \
                patch.object(run_launch, "revert_issue_claim") as revert, \
                contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            codes.append(forge_metadata.main([]))
            # The first halt only returns the issue to `Todo`.
            self.assertEqual((0, 1), (label.call_count, revert.call_count))
            codes.append(forge_metadata.main([]))
        self.assertEqual([forge_metadata.RAN_NO_STATE, forge_metadata.RAN], codes)
        self.assertEqual((1, 2), (label.call_count, revert.call_count))
        self.assertEqual([HELD_REF], self.held_refs())

    def test_the_launcher_backs_off_a_budget_halt_as_a_transient_failure(self) -> None:
        budget = run_launch.Started(workspace=self.workspace, advanced=False, halt="budget halt", budget=True)
        with patch.object(forge_metadata, "REPO_DIR", self.repo), \
                patch.object(forge_metadata, "FORGE_DIR", os.path.join(self.root, "forge")), \
                patch.object(forge_metadata, "fetch_candidates", return_value=[("library-new-request", issue(7))]), \
                patch.object(forge_metadata, "resolve_authenticated_user", return_value="forge-bot"), \
                patch.object(forge_metadata, "claim", return_value=self.claimed()), \
                patch.object(forge_metadata, "start_run", return_value=budget), \
                patch.object(run_launch, "add_issue_label") as label, \
                patch.object(run_launch, "post_issue_comment"), \
                patch.object(run_launch, "revert_issue_claim") as revert, \
                contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            codes: list[int] = [forge_metadata.main([]) for _ in range(run_launch.NO_ADVANCE_LIMIT)]
        self.assertEqual([75, 75], codes)
        self.assertEqual(forge_metadata.BUDGET_HALTED, codes[0])
        self.assertEqual((0, 2), (label.call_count, revert.call_count))
        self.assertEqual([], self.held_refs())
        self.assertTrue(os.path.isdir(self.worktree))

    def test_a_resume_moves_aside_only_the_logs_no_spawn_record_accounts_for(self) -> None:
        logs: str = os.path.join(self.workspace, "runtime", "logs")
        names: list[str] = ["task-forge-7-1.forge-prepare.log", "task-forge-7-1.forge-finalize.log",
                            "task-forge-7-1.forge-native-2.log", "task-forge-7-1.forge-native-2-attempt2.log",
                            "task-forge-7-1.forge-fix.log", "task-forge-7-1.forge-fix-attempt2.log",
                            "task-forge-7-1.forge-cover.log"]
        for name in names:
            write(os.path.join(logs, name), name)
        self.record("task-forge-7-1.forge-prepare", 1)
        self.record("task-forge-7-1.forge-native-2", 1)
        self.record("task-forge-7-1.forge-fix", 2)
        write(os.path.join(self.workspace, "runtime", "spawns", "task-forge-7-1.forge-cover.json"), "{")
        interrupted: str = os.path.join(self.workspace, rhei_workspace.INTERRUPTED)
        earlier: str = os.path.join(interrupted, "task-forge-7-1.forge-finalize.log")
        write(earlier, "an earlier resume")
        moved: list[str] = rhei_workspace.set_aside_interrupted_logs(self.workspace)
        self.assertEqual([os.path.join(interrupted, "2-task-forge-7-1.forge-finalize.log"),
                          os.path.join(interrupted, "task-forge-7-1.forge-native-2-attempt2.log")], moved)
        self.assertEqual(sorted(set(names) - {"task-forge-7-1.forge-finalize.log",
                                              "task-forge-7-1.forge-native-2-attempt2.log"}),
                         sorted(os.listdir(logs)))
        with open(moved[0], encoding="utf-8") as handle:
            self.assertEqual("task-forge-7-1.forge-finalize.log", handle.read())
        with open(earlier, encoding="utf-8") as handle:
            self.assertEqual("an earlier resume", handle.read())

    def test_a_resumed_attempt_moves_its_interrupted_log_aside_before_rhei_runs(self) -> None:
        orphan: str = os.path.join(self.workspace, "runtime", "logs", "task-forge-7-1.forge-finalize.log")
        write(orphan, "killed mid-state")
        seen: list[tuple[list[str], bool]] = []
        with patch.object(run_launch.subprocess, "run",
                          side_effect=lambda command, **_: seen.append((command, os.path.exists(orphan)))), \
                contextlib.redirect_stderr(io.StringIO()):
            run_launch.start_run(self.root, self.claimed(), "claude-code:sonnet", "pi:x/y", [])
        self.assertEqual([(["rhei", "run", self.workspace, "--dashboard"], False)], seen)
        self.assertTrue(os.path.isfile(os.path.join(self.workspace, rhei_workspace.INTERRUPTED,
                                                    "task-forge-7-1.forge-finalize.log")))


if __name__ == "__main__":
    unittest.main()
