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
from dataclasses import replace
from datetime import datetime, timedelta, timezone
from typing import Any
from unittest.mock import Mock, patch

import forge_metadata
from dispatcher import run_launch, stranded_claims
from dispatcher.issue_form import ISSUE_FORM_ACCEPTED
from tests.test_forge_launcher import CLAIMED_AT, FORGE_DIR, issue, new_claim
from utility_scripts.issue_locks import LOCKS

USER: str = "forge-bot"
ORPHAN: str = os.path.join("runtime", "logs", "task-forge-7-1.forge-finalize.log")
# The attempt's own assignment, confirmed by the claim before its time was taken.
ASSIGNED: datetime = CLAIMED_AT - timedelta(seconds=12)
# Another launcher's claim after a release by hand, as on #2624.
LATER: datetime = datetime(2026, 10, 31, 13, 26, 23, tzinfo=timezone.utc)


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
        self.assigned: dict[int, datetime] = {}
        self.read: Mock = Mock(side_effect=lambda number: dict(self.issues[number]))
        self.item: Mock = Mock(side_effect=lambda number: self.items[number])
        self.last_assigned: Mock = Mock(side_effect=lambda number, _: self.assigned[number])
        for name, fake in (("get_issue_claim_payload", self.read), ("get_project_item_state", self.item),
                           ("last_assigned", self.last_assigned)):
            patcher = patch.object(stranded_claims, name, fake)
            patcher.start()
            self.addCleanup(patcher.stop)

    def attempt(self, number: int, attempt: int = 1, finished: bool = False,
                claimed_at: datetime | None = CLAIMED_AT) -> str:
        workspace: str = os.path.join(self.forge, "runs", f"forge-{number}-{attempt}")
        os.makedirs(os.path.join(workspace, "runtime", "forge"))
        if claimed_at is not None:
            run_launch.record_claim(workspace, claimed_at)
        if finished:
            write(os.path.join(workspace, run_launch.OUTCOME), json.dumps({"outcome": "failed"}))
        return workspace

    def claimed_on_github(self, number: int, status: str = "In Progress", assigned_at: datetime = ASSIGNED,
                          **fields: Any) -> None:
        self.issues[number] = payload(number, **fields)
        self.items[number] = (f"item-{number}", status)
        self.assigned[number] = assigned_at

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
                                   "from utility_scripts.issue_locks import issue_lock\n"
                                   "with issue_lock(sys.argv[1], 7) as held:\n"
                                   "    print(held, flush=True)\n"
                                   "    time.sleep(120)\n", self.forge],
            cwd=FORGE_DIR, stdout=subprocess.PIPE, text=True)
        self.addCleanup(holder.kill)
        self.assertEqual("True", holder.stdout.readline().strip())
        with open(os.path.join(self.forge, LOCKS, "forge-7.lock"), encoding="utf-8") as handle:
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

    def test_a_claim_made_after_the_attempts_own_is_another_launchers(self) -> None:
        # Released by hand, then claimed from another checkout (#2624): that launcher's run is left alone.
        self.attempt(7)
        self.claimed_on_github(7, assigned_at=LATER)
        self.attempt(8)
        self.claimed_on_github(8)
        claimed = self.stranded()
        self.assertEqual((8, CLAIMED_AT), (claimed.issue["number"], claimed.claimed_at))
        self.assertIn("[resume] issue #7 was claimed again at 2026-10-31T13:26:23+00:00, after its attempt's claim",
                      self.stderr.getvalue())
        self.assertNotIn("resuming forge-7-1", self.stderr.getvalue())
        self.last_assigned.assert_any_call(7, USER)

    def test_only_an_assignment_after_the_attempts_claim_refuses_the_resume(self) -> None:
        self.attempt(7)
        for assigned_at, resumed in ((ASSIGNED, True), (CLAIMED_AT, True), (CLAIMED_AT + timedelta(seconds=1), False)):
            with self.subTest(assigned_at=assigned_at):
                self.claimed_on_github(7, assigned_at=assigned_at)
                self.assertEqual(resumed, self.stranded() is not None)

    def test_an_attempt_that_records_no_claim_time_is_never_resumed(self) -> None:
        # Who made its claim cannot be told, as for an attempt made before attempts recorded it.
        self.attempt(7, claimed_at=None)
        self.claimed_on_github(7)
        self.assertIsNone(self.stranded())
        self.assertIn("ERROR: Issue #7 is In Progress, but its unfinished attempt records no claim time, so it is "
                      "not resumed. If no launcher works on it, release it by hand.", self.stderr.getvalue())
        self.last_assigned.assert_not_called()
        # Reported only while GitHub still shows the claim, not for every old unfinished attempt.
        self.claimed_on_github(7, "Todo")
        self.assertIsNone(self.stranded())
        self.assertEqual("", self.stderr.getvalue())

    def test_an_assignment_read_github_refuses_leaves_the_issue_for_a_later_cycle(self) -> None:
        self.attempt(7)
        self.claimed_on_github(7)
        self.last_assigned.side_effect = subprocess.CalledProcessError(1, ["gh"], output="", stderr="HTTP 502")
        self.assertIsNone(self.stranded())
        self.assertIn("ERROR: Issue #7 has an unfinished attempt, but GitHub could not be read to tell whether its "
                      "claim is stranded: HTTP 502", self.stderr.getvalue())

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


class ClaimRecordTest(StrandedCase):
    """An attempt records when its claim was made, the time a resume checks (§FS-forge-stranded-claims.1)."""

    def test_a_claim_is_timed_once_its_assignment_is_confirmed(self) -> None:
        # Timed earlier, the launcher would take its own assignment for another launcher's claim.
        confirmed: list[datetime] = []
        with patch.object(run_launch, "refresh_issue_payload_for_claim", return_value=True), \
                patch.object(run_launch, "try_claim_issue",
                             side_effect=lambda *_: confirmed.append(datetime.now(timezone.utc)) or "item-5"), \
                patch.object(run_launch, "pinned_index", return_value=contextlib.nullcontext(self.forge)), \
                patch.object(run_launch, "check_issue_form", return_value=ISSUE_FORM_ACCEPTED):
            claimed = run_launch.claim(issue(5, "fails-javac-compile"), "fails-javac-compile", USER, self.forge)
        self.assertEqual(("item-5", timezone.utc), (claimed.item_id, claimed.claimed_at.tzinfo))
        self.assertLessEqual(confirmed[0], claimed.claimed_at)

    def test_an_attempt_records_its_claim_before_rhei_runs_it(self) -> None:
        # A launcher that dies in the run leaves a claim a resume can tell is its own.
        workspace: str = self.attempt(7, claimed_at=None)
        recorded: list[datetime | None] = []
        reclaimed: run_launch.Claim = replace(new_claim(7), claimed_at=CLAIMED_AT + timedelta(hours=5))
        with patch.object(run_launch.subprocess, "run",
                          side_effect=lambda *_, **__: recorded.append(run_launch.recorded_claim(workspace))), \
                patch.object(run_launch, "agent_environment", return_value=None):
            run_launch.start_run(self.forge, new_claim(7), "claude-code:sonnet", "pi:x/y", [])
            # A later claim of the issue resumes the unfinished attempt, which now holds that claim.
            run_launch.start_run(self.forge, reclaimed, "claude-code:sonnet", "pi:x/y", [])
        self.assertEqual([CLAIMED_AT, reclaimed.claimed_at], recorded)


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
        claimed = new_claim(9, "fails-java-run", "item-9")
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

    def test_a_claim_records_its_time_so_a_resume_takes_over_only_that_claim(self) -> None:
        workspace: str = os.path.join(self.forge, "runs", "forge-9-1")

        def run(command: list[str], **_: Any) -> subprocess.CompletedProcess:
            if command[:2] == ["rhei", "instantiate"]:
                os.makedirs(command[-1])
                return subprocess.CompletedProcess(command, 0)
            raise RuntimeError("the launcher died in the run")

        claimed = new_claim(9, "fails-java-run", "item-9")
        with patch.object(run_launch.subprocess, "run", side_effect=run), \
                patch.object(run_launch, "agent_environment", return_value=None), self.assertRaises(RuntimeError):
            self.cycle([("fails-java-run", claimed.issue)], claim=Mock(return_value=claimed))
        self.assertEqual(CLAIMED_AT, run_launch.recorded_claim(workspace))
        self.claimed_on_github(9)
        resumed = self.stranded()
        self.assertEqual((9, CLAIMED_AT), (resumed.issue["number"], resumed.claimed_at))
        # Released by hand and claimed again from another checkout: this host's attempt leaves it alone.
        self.claimed_on_github(9, assigned_at=LATER)
        self.assertIsNone(self.stranded())

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


class LastAssignedTest(unittest.TestCase):
    """GitHub's time of a claim is the latest assignment of the issue to the user (§FS-forge-stranded-claims.1)."""

    @staticmethod
    def event(kind: str, login: str, at: str) -> str:
        return json.dumps({"event": kind, "assignee": {"login": login}, "created_at": at})

    def test_the_latest_assignment_to_the_user_counts_and_no_other_event(self) -> None:
        # #2624's events: the first launcher's claim, a release by hand, the second launcher's claim.
        printed: str = "\n".join([
            self.event("assigned", USER, "2026-10-31T09:04:28Z"),
            self.event("unassigned", USER, "2026-10-31T11:57:25Z"),
            self.event("assigned", USER, "2026-10-31T13:26:23Z"),
            self.event("unassigned", USER, "2026-10-31T13:26:47Z"),
            self.event("assigned", "kimeta", "2026-10-31T13:30:00Z"),
            json.dumps({"event": "labeled", "label": {"name": "human-intervention"},
                        "created_at": "2026-10-31T13:31:00Z"}),
        ]) + "\n"
        with patch.object(stranded_claims, "gh", return_value=subprocess.CompletedProcess([], 0, printed, "")) as gh:
            self.assertEqual(LATER, stranded_claims.last_assigned(2624, USER))
        # Every page: a long-lived issue's latest assignment is on its last one.
        self.assertEqual(("api", "--paginate", f"repos/{stranded_claims.REPO}/issues/2624/events?per_page=100",
                          "--jq", ".[]"), gh.call_args.args)

    def test_an_issue_whose_events_show_no_assignment_to_the_user_raises(self) -> None:
        # Its claim's time cannot be told, so the issue waits like one GitHub refused to read.
        printed: str = self.event("assigned", "kimeta", "2026-10-31T13:30:00Z") + "\n"
        with patch.object(stranded_claims, "gh", return_value=subprocess.CompletedProcess([], 0, printed, "")), \
                self.assertRaises(RuntimeError):
            stranded_claims.last_assigned(7, USER)


if __name__ == "__main__":
    unittest.main()
