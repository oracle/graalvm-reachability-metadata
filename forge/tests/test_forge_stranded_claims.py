# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Tests for the claims a dead launcher leaves, and the lock that tells them from a live launcher's.

§FS-forge-stranded-claims fixes what is stranded and that it is resumed in place;
the lock a launcher holds from before its claim to its close-out is what tells
a dead launcher's attempt from a live one's (§AR-forge-launcher.1.1).
"""
from __future__ import annotations

import contextlib
import fcntl
import io
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from typing import Any
from unittest.mock import Mock, patch

import forge_metadata
from dispatcher import run_launch, stranded_claims
from tests.test_forge_launcher import FORGE_DIR, issue

USER: str = "forge-bot"
ORPHAN: str = os.path.join("runtime", "logs", "task-forge-7-1.forge-finalize.log")


def write(path: str, text: str) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(text)


def payload(number: int, state: str = "OPEN", assignees: tuple[str, ...] = (USER,),
            labels: tuple[str, ...] = ("library-new-request",)) -> dict[str, Any]:
    return {**issue(number, *labels), "state": state, "assignees": [{"login": login} for login in assignees]}


class StrandedCase(unittest.TestCase):
    """A Forge directory whose runs hold the attempts a test lays out, and a fake GitHub."""

    def setUp(self) -> None:
        self.forge: str = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, self.forge, True)
        self.issues: dict[int, dict[str, Any]] = {}
        self.items: dict[int, tuple[str | None, str | None]] = {}
        self.read: Mock = Mock(side_effect=lambda number: dict(self.issues[number]))
        self.item: Mock = Mock(side_effect=lambda number: self.items[number])
        for name, fake in (("get_issue_claim_payload", self.read), ("get_project_item_state", self.item)):
            patcher = patch.object(stranded_claims, name, fake)
            patcher.start()
            self.addCleanup(patcher.stop)

    def attempt(self, number: int, attempt: int = 1, finished: bool = False) -> str:
        workspace: str = os.path.join(self.forge, "runs", f"forge-{number}-{attempt}")
        os.makedirs(os.path.join(workspace, "runtime", "forge"))
        if finished:
            write(os.path.join(workspace, run_launch.OUTCOME), json.dumps({"outcome": "failed"}))
        return workspace

    def claimed_on_github(self, number: int, status: str = "In Progress", **fields: Any) -> None:
        self.issues[number] = payload(number, **fields)
        self.items[number] = (f"item-{number}", status)

    def stranded(self) -> run_launch.Claim | None:
        with contextlib.redirect_stderr(io.StringIO()) as self.stderr:
            with stranded_claims.stranded_claim(self.forge, USER) as claimed:
                return claimed


class StrandedClaimTest(StrandedCase):
    """§FS-forge-stranded-claims.1"""

    def test_a_dead_launchers_unfinished_attempt_is_stranded(self) -> None:
        self.attempt(7)
        self.claimed_on_github(7)
        claimed = self.stranded()
        self.assertEqual((7, "library-new-request", "item-7"),
                         (claimed.issue["number"], claimed.label, claimed.item_id))
        self.assertIn("[resume] issue #7 is In Progress and its launcher is gone; resuming forge-7-1",
                      self.stderr.getvalue())

    def test_a_live_launchers_attempt_is_left_alone(self) -> None:
        self.attempt(7)
        self.claimed_on_github(7)
        with stranded_claims.issue_lock(self.forge, 7) as held:
            self.assertTrue(held)
            self.assertIsNone(self.stranded())
        self.read.assert_not_called()
        self.assertIsNotNone(self.stranded())

    def test_a_launcher_killed_with_sigkill_frees_its_issue(self) -> None:
        # The kernel drops a flock with its holder, however it dies (§FS-forge-stranded-claims.2).
        self.attempt(7)
        self.claimed_on_github(7)
        holder = subprocess.Popen(
            [sys.executable, "-c", "import sys, time\n"
                                   "from dispatcher.stranded_claims import issue_lock\n"
                                   "with issue_lock(sys.argv[1], 7) as held:\n"
                                   "    print(held, flush=True)\n"
                                   "    time.sleep(120)\n", self.forge],
            cwd=FORGE_DIR, stdout=subprocess.PIPE, text=True)
        self.addCleanup(holder.kill)
        self.assertEqual("True", holder.stdout.readline().strip())
        with open(os.path.join(self.forge, stranded_claims.LOCKS, "forge-7.lock"), encoding="utf-8") as handle:
            self.assertEqual(f"pid={holder.pid}\n", handle.read())
        self.assertIsNone(self.stranded())
        holder.kill()
        holder.wait()
        holder.stdout.close()
        self.assertEqual(7, self.stranded().issue["number"])

    def test_a_rhei_run_that_outlived_its_launcher_keeps_the_attempt_live(self) -> None:
        workspace: str = self.attempt(7)
        self.claimed_on_github(7)
        write(os.path.join(workspace, stranded_claims.RHEI_RUN_LOCK), "{}")
        with open(os.path.join(workspace, stranded_claims.RHEI_RUN_LOCK), encoding="utf-8") as rhei:
            fcntl.flock(rhei.fileno(), fcntl.LOCK_EX)
            self.assertIsNone(self.stranded())
        self.read.assert_not_called()
        self.assertIsNotNone(self.stranded())

    def test_a_finished_attempt_is_not_stranded(self) -> None:
        # Its close-out, not a resume, decides the issue; only the latest attempt counts.
        self.attempt(7, finished=True)
        self.attempt(8, 1)
        self.attempt(8, 2, finished=True)
        for number in (7, 8):
            self.claimed_on_github(number)
        self.assertIsNone(self.stranded())
        self.read.assert_not_called()

    def test_an_issue_without_a_workspace_on_this_host_is_left_alone(self) -> None:
        # It may be the same user's launcher on another host: GitHub is never read for it.
        self.attempt(8, finished=True)
        self.claimed_on_github(7)
        self.assertIsNone(self.stranded())
        shutil.rmtree(os.path.join(self.forge, "runs"))
        self.assertIsNone(self.stranded())
        self.read.assert_not_called()

    def test_only_a_claim_github_still_shows_as_the_users_own_is_stranded(self) -> None:
        self.attempt(7)
        for name, fields, status in (
                ("closed", {"state": "CLOSED"}, "In Progress"),
                ("released to Todo", {}, "Todo"),
                ("unassigned", {"assignees": ()}, "In Progress"),
                ("another user's", {"assignees": ("kimeta",)}, "In Progress"),
                ("shared with another user", {"assignees": (USER, "kimeta")}, "In Progress"),
                ("held", {"labels": ("library-new-request", "human-intervention")}, "In Progress"),
                ("blocked on GraalVM", {"labels": ("library-new-request", "graalvm-bug")}, "In Progress"),
                ("not for Native Image", {"labels": ("library-new-request", "not-for-native-image")},
                 "In Progress"),
                ("off the board", {}, None)):
            with self.subTest(name):
                self.claimed_on_github(7, status, **fields)
                if status is None:
                    self.items[7] = (None, None)
                self.assertIsNone(self.stranded())

    def test_a_read_github_refuses_skips_the_issue_without_stopping_the_launcher(self) -> None:
        self.attempt(7)
        self.attempt(8)
        self.claimed_on_github(8)

        def read(number: int) -> dict[str, Any]:
            if number == 7:
                raise subprocess.CalledProcessError(1, ["gh"], output="", stderr="error connecting to api.github.com")
            return dict(self.issues[number])

        self.read.side_effect = read
        self.assertEqual(8, self.stranded().issue["number"])
        self.assertIn("ERROR: Issue #7 has an unfinished attempt, but GitHub could not be read",
                      self.stderr.getvalue())

    def test_an_attempt_its_last_holder_finished_is_read_again_under_the_lock(self) -> None:
        # Listed as unfinished, then finished by a live launcher before its lock came free.
        self.attempt(7, finished=True)
        self.claimed_on_github(7)
        with patch.object(stranded_claims, "unfinished_issues", return_value=[7]):
            self.assertIsNone(self.stranded())
        self.read.assert_not_called()

    def test_the_lock_is_released_with_the_block_whether_it_returns_or_raises(self) -> None:
        self.attempt(7)
        self.claimed_on_github(7)
        with self.assertRaises(RuntimeError), contextlib.redirect_stderr(io.StringIO()):
            with stranded_claims.stranded_claim(self.forge, USER) as claimed:
                self.assertIsNotNone(claimed)
                with stranded_claims.issue_lock(self.forge, 7) as held:
                    self.assertFalse(held)
                raise RuntimeError("the run crashed")
        with stranded_claims.issue_lock(self.forge, 7) as held:
            self.assertTrue(held)


class CycleTest(StrandedCase):
    """A cycle resumes its host's stranded claim first, and a claim holds its lock (§FS-forge-stranded-claims)."""

    def cycle(self, candidates: list[tuple[str, dict[str, Any]]], **patches: Any) -> int:
        with patch.object(forge_metadata, "FORGE_DIR", self.forge), \
                patch.object(forge_metadata, "REPO_DIR", self.forge), \
                patch.object(forge_metadata, "fetch_candidates", return_value=candidates), \
                patch.object(forge_metadata, "resolve_authenticated_user", return_value=USER), \
                contextlib.ExitStack() as stack, \
                contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            for name, value in patches.items():
                stack.enter_context(patch.object(forge_metadata, name, value))
            return forge_metadata.main([])

    def locked(self, number: int) -> bool:
        with stranded_claims.issue_lock(self.forge, number) as held:
            return not held

    def test_a_cycle_resumes_its_stranded_claim_in_place_before_it_claims_anything(self) -> None:
        workspace: str = self.attempt(7)
        write(os.path.join(workspace, ORPHAN), "killed mid-state")
        self.claimed_on_github(7)
        seen: list[tuple[list[str], bool, bool]] = []

        def rhei_run(command: list[str], **_: Any) -> None:
            seen.append((command, os.path.exists(os.path.join(workspace, ORPHAN)), self.locked(7)))
            write(os.path.join(workspace, "runtime", "spawns", "task-forge-7-1.forge-finalize.json"), "{}")
            write(os.path.join(workspace, run_launch.OUTCOME), json.dumps({"outcome": "completed"}))

        close_out: Mock = Mock(side_effect=lambda *_: seen.append((["close_out"], False, self.locked(7))))
        claim: Mock = Mock()
        with patch.object(run_launch.subprocess, "run", side_effect=rhei_run), \
                patch.object(run_launch, "agent_environment", return_value=None):
            code: int = self.cycle([("fails-java-run", issue(9, "fails-java-run"))], claim=claim,
                                   close_out=close_out)
        self.assertEqual(forge_metadata.RAN, code)
        # The attempt is resumed, not re-created, with its interrupted log moved aside and its lock held.
        self.assertEqual([(["rhei", "run", workspace, "--dashboard"], False, True), (["close_out"], False, True)],
                         seen)
        claim.assert_not_called()
        claimed, outcome = close_out.call_args.args
        self.assertEqual((7, "item-7", "completed"), (claimed.issue["number"], claimed.item_id, outcome["outcome"]))
        self.assertFalse(self.locked(7))

    def test_a_claimed_issue_holds_its_lock_from_before_the_claim_to_its_close_out(self) -> None:
        claimed = run_launch.Claim(issue=issue(9, "fails-java-run"), label="fails-java-run", item_id="item-9")
        held: list[tuple[str, bool]] = []
        started = run_launch.Started(workspace=os.path.join(self.forge, "runs", "forge-9-1"), advanced=True)
        code: int = self.cycle(
            [("fails-java-run", claimed.issue)],
            claim=Mock(side_effect=lambda *_: held.append(("claim", self.locked(9))) or claimed),
            start_run=Mock(side_effect=lambda *_: held.append(("run", self.locked(9))) or started),
            finish_run=Mock(return_value={"outcome": "completed"}),
            close_out=Mock(side_effect=lambda *_: held.append(("close_out", self.locked(9)))))
        self.assertEqual(forge_metadata.RAN, code)
        self.assertEqual([("claim", True), ("run", True), ("close_out", True)], held)
        self.assertFalse(self.locked(9))

    def test_a_candidate_another_launcher_holds_is_not_claimed(self) -> None:
        claim: Mock = Mock(return_value=None)
        with stranded_claims.issue_lock(self.forge, 9) as held:
            self.assertTrue(held)
            code: int = self.cycle([("fails-java-run", issue(9, "fails-java-run"))], claim=claim)
        self.assertEqual(forge_metadata.NOTHING_CLAIMED, code)
        claim.assert_not_called()

    def test_a_cycle_without_stranded_work_claims_as_before(self) -> None:
        self.attempt(7, finished=True)
        claim: Mock = Mock(return_value=None)
        code: int = self.cycle([("fails-java-run", issue(9, "fails-java-run"))], claim=claim)
        self.assertEqual(forge_metadata.NOTHING_CLAIMED, code)
        self.assertEqual(9, claim.call_args.args[0]["number"])
        self.read.assert_not_called()


if __name__ == "__main__":
    unittest.main()
