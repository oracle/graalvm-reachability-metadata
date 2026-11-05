# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Tests for the claim and release board writes that a GitHub outage must not strand.

FS-forge-stranded-claims.4 retries each write on a rate limit or an unreachable
GitHub, records a release an outage still refuses, and has the next cycle
retry it before it claims anything; AR-forge-launcher.1.2 says how. Every test
answers `gh` with a fake that keeps one issue's board state.
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
from datetime import datetime, timedelta, timezone
from typing import Any, Callable
from unittest.mock import Mock, patch

import forge_metadata
from dispatcher import config, issue_admin, issue_cache, issue_claiming, pending_releases, project_board, run_launch
from dispatcher import stranded_claims
from git_scripts.github_cli import GITHUB_TRANSIENT_RETRY_ATTEMPTS, GitHubRateLimitExceeded
from tests.test_forge_launcher import issue

USER: str = "forge-bot"
OUTAGE: str = "error connecting to api.github.com"
RATE_LIMIT: str = "gh: API rate limit exceeded for user ID 1."
GRAPHQL_ERROR: str = ("GraphQL: Something went wrong while executing your query. Please include `C0DE:1234` when "
                      "reporting this issue.")
NOT_FOUND: str = "gh: Not Found (HTTP 404)"
FORBIDDEN: str = "gh: Resource not accessible by integration (HTTP 403)"
OPTIONS: dict[str, str] = {config.STATUS_TODO: "option-todo", config.STATUS_IN_PROGRESS: "option-in-progress"}


class FakeGitHub:
    """`subprocess.run` for `gh`: one issue's assignees, project status and last update, with scripted failures."""

    def __init__(self, assignees: tuple[str, ...] = (), status: str = config.STATUS_TODO) -> None:
        self.assignees: list[str] = list(assignees)
        self.status: str = status
        self.state: str = "open"
        self.updated_at: str = "2000-01-01T00:00:00Z"
        # What each kind of call meets first, a failure or `None` for an answer, and the failure every call
        # meets while GitHub is down.
        self.script: dict[str, list[str | None]] = {}
        self.down: str | None = None
        self.calls: list[tuple[str, str | None]] = []

    @staticmethod
    def kind(args: list[str]) -> str:
        if args[:2] == ["project", "item-edit"]:
            return "status-write"
        if args[:3] == ["api", "--method", "PATCH"]:
            return "assignee-write"
        if args[:2] == ["api", "graphql"]:
            return "status-read"
        if args[:2] == ["issue", "view"]:
            return "assignee-read"
        return "issue-read"

    def __call__(self, command: list[str], **kwargs: Any) -> subprocess.CompletedProcess[str]:
        args: list[str] = command[1:]
        kind: str = self.kind(args)
        self.calls.append((kind, kwargs.get("input")))
        failure: str | None = self.down or (self.script.get(kind) or [None]).pop(0)
        if failure is not None:
            return subprocess.CompletedProcess(command, 1, stdout="", stderr=failure)
        return subprocess.CompletedProcess(command, 0, stdout=self.answer(kind, args, kwargs.get("input")), stderr="")

    def answer(self, kind: str, args: list[str], stdin: str | None) -> str:
        if kind == "status-write":
            option: str = args[args.index("--single-select-option-id") + 1]
            self.status = next(name for name, value in OPTIONS.items() if value == option)
        elif kind == "assignee-write":
            self.assignees = [arg.split("=", 1)[1] for arg in args if arg.startswith("assignees[]=")]
            if stdin is not None:
                self.assignees = json.loads(stdin)["assignees"]
        elif kind == "status-read":
            return json.dumps({"data": {"node": {"fieldValues": {"nodes": [
                {"name": self.status, "field": {"name": config.STATUS_FIELD_NAME}}]}}}})
        elif kind == "assignee-read":
            return json.dumps({"assignees": [{"login": login} for login in self.assignees]})
        else:
            return json.dumps({"state": self.state, "updated_at": self.updated_at,
                               "assignees": [{"login": login} for login in self.assignees]})
        return ""

    def count(self, kind: str) -> int:
        return sum(1 for called, _ in self.calls if called == kind)


class BoardCase(unittest.TestCase):
    """A fake GitHub, no real sleeps, and a temporary runs directory for the pending releases."""

    def setUp(self) -> None:
        self.forge: str = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, self.forge, True)
        self.github: FakeGitHub = FakeGitHub()
        self.sleep: Mock = Mock()
        for patcher in (patch.object(subprocess, "run", side_effect=lambda *a, **k: self.github(*a, **k)),
                        patch("time.sleep", self.sleep),
                        patch.object(project_board, "get_cached_field_info",
                                     return_value=("project", "field", OPTIONS)),
                        patch.object(pending_releases, "RELEASES", os.path.join(self.forge, "runs", "releases")),
                        patch.object(issue_cache, "get_issue_claim_locks_root",
                                     return_value=os.path.join(self.forge, "claim-locks"))):
            patcher.start()
            self.addCleanup(patcher.stop)
        self.stderr: io.StringIO = io.StringIO()
        redirect = contextlib.redirect_stderr(self.stderr)
        redirect.__enter__()
        self.addCleanup(redirect.__exit__, None, None, None)

    def record(self, issue_number: int, minutes_ago: int = 0) -> None:
        pending_releases.record_pending_release(issue_number, f"item-{issue_number}", "infrastructure")
        if minutes_ago:
            path: str = pending_releases.record_path(issue_number)
            with open(path, encoding="utf-8") as handle:
                record: dict[str, str] = json.load(handle)
            record["recorded_at"] = (datetime.now(timezone.utc) - timedelta(minutes=minutes_ago)).isoformat()
            with open(path, "w", encoding="utf-8") as handle:
                json.dump(record, handle)


WRITES: dict[str, tuple[str, Callable[[], Any]]] = {
    "set_item_status": ("status-write", lambda: project_board.set_item_status("item-7", config.STATUS_TODO)),
    "set_issue_assignee": ("assignee-write", lambda: issue_admin.set_issue_assignee(7, USER)),
    "clear_issue_assignees": ("assignee-write", lambda: issue_admin.clear_issue_assignees(7)),
    "get_item_status": ("status-read", lambda: project_board.get_item_status("item-7")),
    "get_issue_assignees": ("assignee-read", lambda: issue_admin.get_issue_assignees(7)),
}


class WriteRetryTest(BoardCase):
    """§FS-forge-stranded-claims.4.1: each write and verification read waits out a transient condition."""

    def test_each_write_survives_a_transient_failure(self) -> None:
        for name, (kind, write) in WRITES.items():
            for failure in (OUTAGE, RATE_LIMIT, GRAPHQL_ERROR):
                with self.subTest(name, failure=failure):
                    self.github.calls.clear()
                    self.sleep.reset_mock()
                    self.github.script = {kind: [failure]}
                    write()
                    self.assertEqual(2, self.github.count(kind))
                    self.sleep.assert_called_once_with(2.0)

    def test_the_assignee_clear_resends_its_body_on_a_retry(self) -> None:
        self.github.assignees = [USER]
        self.github.script = {"assignee-write": [OUTAGE]}
        issue_admin.clear_issue_assignees(7)
        self.assertEqual([("assignee-write", '{"assignees":[]}')] * 2, self.github.calls)
        self.assertEqual([], self.github.assignees)

    def test_a_persistent_rate_limit_is_raised_after_the_bounded_backoff(self) -> None:
        self.github.script = {"status-write": [RATE_LIMIT] * GITHUB_TRANSIENT_RETRY_ATTEMPTS}
        with self.assertRaises(GitHubRateLimitExceeded):
            project_board.set_item_status("item-7", config.STATUS_TODO)
        self.assertEqual(GITHUB_TRANSIENT_RETRY_ATTEMPTS, self.github.count("status-write"))
        self.assertEqual([2.0, 4.0, 8.0, 16.0], [call.args[0] for call in self.sleep.call_args_list])

    def test_a_refusal_is_not_retried(self) -> None:
        for name, (kind, write) in WRITES.items():
            for refusal in (NOT_FOUND, FORBIDDEN):
                with self.subTest(name, refusal=refusal):
                    self.github.calls.clear()
                    self.github.script = {kind: [refusal]}
                    with self.assertRaises(subprocess.CalledProcessError):
                        write()
                    self.assertEqual(1, self.github.count(kind))
        self.sleep.assert_not_called()

    def test_other_github_calls_still_raise_on_a_rate_limit_at_once(self) -> None:
        # A run stops for the reset instead (§AR-forge-launcher.1.2).
        self.github.script = {"issue-read": [RATE_LIMIT]}
        with self.assertRaises(GitHubRateLimitExceeded):
            issue_admin.gh_json("api", "user")
        self.assertEqual(1, self.github.count("issue-read"))


class PendingReleaseTest(BoardCase):
    """§FS-forge-stranded-claims.4.2: a release an outage leaves unconfirmed is recorded, a refused one is not."""

    def release(self) -> bool:
        return issue_claiming.revert_issue_claim("item-7", 7, "infrastructure")

    def test_a_release_an_outage_keeps_unconfirmed_is_recorded(self) -> None:
        self.github.assignees, self.github.status, self.github.down = [USER], config.STATUS_IN_PROGRESS, OUTAGE
        self.assertFalse(self.release())
        self.assertEqual(config.CLAIM_RELEASE_ATTEMPTS * GITHUB_TRANSIENT_RETRY_ATTEMPTS,
                         self.github.count("status-write"))
        recorded = pending_releases.pending_release(7)
        self.assertEqual([7], pending_releases.pending_issues())
        self.assertEqual((7, "item-7", "infrastructure"), (recorded.issue, recorded.item_id, recorded.reason))
        self.assertIn("Issue #7 release is unconfirmed", self.stderr.getvalue())
        self.assertIn("the next cycle retries it before it claims new work", self.stderr.getvalue())

    def test_a_status_write_that_keeps_failing_after_the_assignee_cleared_is_recorded(self) -> None:
        # #2451: the assignee cleared, `Todo` did not, and the issue sat In Progress with nobody on it.
        self.github.assignees, self.github.status = [USER], config.STATUS_IN_PROGRESS
        self.github.script = {"status-write": [GRAPHQL_ERROR] * 100}
        self.assertFalse(self.release())
        self.assertEqual(([], config.STATUS_IN_PROGRESS), (self.github.assignees, self.github.status))
        self.assertTrue(pending_releases.is_release_pending(7))

    def test_a_refused_release_is_reported_for_a_maintainer_and_not_retried(self) -> None:
        self.record(7)
        self.github.assignees, self.github.status = [USER], config.STATUS_IN_PROGRESS
        self.github.script = {"status-write": [FORBIDDEN]}
        self.assertFalse(self.release())
        self.assertEqual(1, self.github.count("status-write"))
        self.assertFalse(pending_releases.is_release_pending(7))
        self.assertIn("release it by hand", self.stderr.getvalue())

    def test_a_confirmed_release_drops_its_record(self) -> None:
        self.record(7)
        self.github.assignees, self.github.status = [USER], config.STATUS_IN_PROGRESS
        self.assertTrue(self.release())
        self.assertEqual(([], config.STATUS_TODO), (self.github.assignees, self.github.status))
        self.assertEqual([], pending_releases.pending_issues())


class CycleTest(BoardCase):
    """§FS-forge-stranded-claims.4.3 and .4.4: the next cycle retries a pending release, and a claim never crashes."""

    def cycle(self, candidates: list[tuple[str, dict[str, Any]]], **patches: Any) -> int:
        with patch.object(forge_metadata, "FORGE_DIR", self.forge), \
                patch.object(forge_metadata, "REPO_DIR", self.forge), \
                patch.object(forge_metadata, "fetch_candidates", return_value=candidates), \
                patch.object(forge_metadata, "resolve_authenticated_user", return_value=USER), \
                contextlib.ExitStack() as stack, contextlib.redirect_stdout(io.StringIO()):
            for name, value in patches.items():
                stack.enter_context(patch.object(forge_metadata, name, value))
            return forge_metadata.main([])

    def test_the_next_cycle_retries_a_pending_release_before_it_claims(self) -> None:
        self.record(7)
        self.github.assignees, self.github.status = [USER], config.STATUS_IN_PROGRESS
        seen: list[tuple[str, bool]] = []
        claim: Mock = Mock(side_effect=lambda *_: seen.append(
            (self.github.status, pending_releases.is_release_pending(7))))
        code: int = self.cycle([("fails-java-run", issue(9, "fails-java-run"))], claim=claim)
        self.assertEqual(forge_metadata.NOTHING_CLAIMED, code)
        self.assertEqual([(config.STATUS_TODO, False)], seen)
        self.assertEqual([], self.github.assignees)
        self.assertIn("retrying the release of issue #7", self.stderr.getvalue())

    def test_a_release_github_still_refuses_ends_the_cycle_before_anything_is_claimed(self) -> None:
        self.record(7)
        self.github.assignees, self.github.status = [USER], config.STATUS_IN_PROGRESS
        self.github.script = {"status-write": [OUTAGE] * 100}
        claim: Mock = Mock()
        scan: Mock = Mock()
        code: int = self.cycle([("fails-java-run", issue(9, "fails-java-run"))], claim=claim, stranded_claim=scan)
        self.assertEqual(forge_metadata.GITHUB_OUTAGE, code)
        self.assertEqual(75, code)
        claim.assert_not_called()
        scan.assert_not_called()
        self.assertTrue(pending_releases.is_release_pending(7))

    def test_an_issue_github_cannot_read_ends_the_cycle_and_keeps_the_record(self) -> None:
        self.record(7)
        self.github.down = OUTAGE
        claim: Mock = Mock()
        self.assertEqual(forge_metadata.GITHUB_OUTAGE, self.cycle([("fails-java-run", issue(9))], claim=claim))
        claim.assert_not_called()
        self.assertTrue(pending_releases.is_release_pending(7))

    def test_a_pending_release_whose_issue_changed_since_is_named_for_a_maintainer(self) -> None:
        # It may have been released by hand and claimed again: retrying would undo that claim.
        for name, change in (("updated since", lambda: setattr(self.github, "updated_at", "2099-01-01T00:00:00Z")),
                             ("another user's", lambda: setattr(self.github, "assignees", ["kimeta"])),
                             ("closed", lambda: setattr(self.github, "state", "closed"))):
            with self.subTest(name):
                self.github = FakeGitHub(assignees=(USER,), status=config.STATUS_IN_PROGRESS)
                self.record(7, minutes_ago=10)
                change()
                claim: Mock = Mock(return_value=None)
                self.assertEqual(forge_metadata.NOTHING_CLAIMED,
                                 self.cycle([("fails-java-run", issue(9))], claim=claim))
                self.assertEqual(0, self.github.count("status-write") + self.github.count("assignee-write"))
                self.assertFalse(pending_releases.is_release_pending(7))
                claim.assert_called_once()
                self.assertIn("Issue #7 release is not retried", self.stderr.getvalue())

    def test_a_pending_release_a_live_launcher_holds_is_left_to_its_close_out(self) -> None:
        self.record(7)
        with stranded_claims.issue_lock(self.forge, 7) as held:
            self.assertTrue(held)
            self.assertEqual(forge_metadata.NOTHING_CLAIMED,
                             self.cycle([("fails-java-run", issue(9))], claim=Mock(return_value=None)))
        self.assertEqual([], self.github.calls)
        self.assertTrue(pending_releases.is_release_pending(7))

    def test_a_record_another_launcher_retried_since_the_listing_is_read_again_under_the_lock(self) -> None:
        with patch.object(stranded_claims, "pending_issues", return_value=[7]):
            self.assertEqual(forge_metadata.NOTHING_CLAIMED,
                             self.cycle([("fails-java-run", issue(9))], claim=Mock(return_value=None)))
        self.assertEqual([], self.github.calls)
        self.assertNotIn("Issue #7", self.stderr.getvalue())

    def claim_issue_9(self) -> int:
        with patch.object(run_launch, "refresh_issue_payload_for_claim", return_value=True), \
                patch.object(issue_claiming, "refresh_issue_payload_for_claim", return_value=True), \
                patch.object(issue_claiming, "get_open_blocking_issue_numbers", return_value=[]), \
                patch.object(issue_claiming, "get_project_item_state", return_value=("item-9", config.STATUS_TODO)), \
                patch.object(issue_claiming.random, "uniform", return_value=0):
            return self.cycle([("fails-java-run", issue(9, "fails-java-run"))])

    def test_a_claim_whose_status_write_fails_is_released_instead_of_crashing_the_cycle(self) -> None:
        self.github.script = {"status-write": [GRAPHQL_ERROR] * GITHUB_TRANSIENT_RETRY_ATTEMPTS}
        self.assertEqual(forge_metadata.GITHUB_OUTAGE, self.claim_issue_9())
        self.assertEqual(([], config.STATUS_TODO), (self.github.assignees, self.github.status))
        self.assertFalse(pending_releases.is_release_pending(9))
        self.assertIn("GitHub failed during the claim of issue #9", self.stderr.getvalue())

    def test_a_claim_whose_release_github_also_refuses_records_it(self) -> None:
        # The claim's assignment lands; then GitHub writes nothing more.
        self.github.script = {"status-write": [OUTAGE] * 100, "assignee-write": [None] + [OUTAGE] * 100}
        self.assertEqual(forge_metadata.GITHUB_OUTAGE, self.claim_issue_9())
        self.assertEqual([USER], self.github.assignees)
        self.assertTrue(pending_releases.is_release_pending(9))

    def test_a_claim_github_refuses_for_good_still_raises_after_its_release(self) -> None:
        # A missing permission is a host fault, not an outage (§FS-forge-host-requirements.1).
        self.github.script = {"status-write": [FORBIDDEN]}
        with self.assertRaises(subprocess.CalledProcessError):
            self.claim_issue_9()
        self.assertEqual(([], config.STATUS_TODO), (self.github.assignees, self.github.status))
        self.assertFalse(pending_releases.is_release_pending(9))


if __name__ == "__main__":
    unittest.main()
