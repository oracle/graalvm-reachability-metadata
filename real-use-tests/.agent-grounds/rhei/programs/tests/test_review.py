# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Unit tests for the launcher's review of the open real-use pull requests.

§FS-real-use-test-generation.4 states the rules; §AR-real-use-test-launcher the wiring.
A fake `gh` answers from a table of pull requests and records every call.
"""
from __future__ import annotations

import io
import json
import os
import sys
import unittest
from contextlib import redirect_stderr
from datetime import datetime, timedelta, timezone
from typing import Any, Callable
from unittest.mock import patch

PROGRAMS: str = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, PROGRAMS)

import review  # noqa: E402

NOW: datetime = datetime(2026, 10, 17, 12, 0, tzinfo=timezone.utc)


def _stamp(moment: datetime) -> str:
    return moment.strftime("%Y-%m-%dT%H:%M:%SZ")


def _pr(number: int, created_hours_ago: int, decision: str = "REVIEW_REQUIRED",
        labeled_hours_ago: float | None = None) -> dict[str, Any]:
    """A listed pull request; `labeled_hours_ago` puts `in-review` on it that long ago."""
    labels: list[dict[str, str]] = [{"name": "real-use-test"}]
    labels += [{"name": review.IN_REVIEW}] if labeled_hours_ago is not None else []
    return {"number": number, "state": "OPEN", "headRefName": f"ai/kimeta/real-use-{number}",
            "headRefOid": f"head{number}",
            "reviewDecision": decision, "labels": labels,
            "createdAt": _stamp(NOW - timedelta(hours=created_hours_ago)),
            "labeledAt": None if labeled_hours_ago is None else _stamp(NOW - timedelta(hours=labeled_hours_ago))}


class FakeGitHub:
    """Stands in for `review.gh` and `review.run_agent`, logging both in one ordered list.

    `during_agent` maps a pull request number to a change applied to the table while its agent runs.
    """

    def __init__(self, pulls: list[dict[str, Any]],
                 during_agent: dict[int, Callable[[dict[int, dict[str, Any]]], None]] | None = None) -> None:
        self.pulls: dict[int, dict[str, Any]] = {pull["number"]: pull for pull in pulls}
        self.listed: list[dict[str, Any]] = pulls
        self.during_agent: dict[int, Callable[[dict[int, dict[str, Any]]], None]] = during_agent or {}
        self.log: list[tuple[Any, ...]] = []

    def gh(self, arguments: list[str], action: str) -> str:
        if arguments[:2] == ["pr", "list"]:
            self.log.append(("list", arguments))
            return json.dumps([{"number": pull["number"], "createdAt": pull["createdAt"]} for pull in self.listed])
        if arguments[:2] == ["pr", "view"]:
            number: int = int(arguments[2])
            self.log.append(("view", number))
            return json.dumps({key: value for key, value in self.pulls[number].items()
                               if key not in ("labeledAt", "createdAt")})
        if arguments[0] == "api":
            number = int(arguments[2].split("/")[-2])
            self.log.append(("timeline", number))
            stamp: str | None = self.pulls[number]["labeledAt"]
            older: str = _stamp(NOW - timedelta(days=2))
            return f"{older}\n{stamp}\n" if stamp else ""
        if arguments[:2] == ["pr", "edit"]:
            assert arguments[3:] == ["--repo", review.issue.REPOSITORY, arguments[5], review.IN_REVIEW], arguments
            self.log.append((arguments[5], int(arguments[2])))
            return ""
        raise AssertionError(f"unexpected gh {arguments}")

    def run_agent(self, repo: str, target: str, number: int, branch: str, head: str) -> None:
        self.log.append(("agent", number, branch, head))
        self.during_agent.get(number, lambda pulls: None)(self.pulls)

    def run(self) -> int:
        with patch.object(review, "gh", side_effect=self.gh), \
                patch.object(review, "run_agent", side_effect=self.run_agent), redirect_stderr(io.StringIO()):
            return review.review("/repo", "claude-code:opus", NOW)

    def agents(self) -> list[int]:
        return [entry[1] for entry in self.log if entry[0] == "agent"]


class PromptTest(unittest.TestCase):

    def test_prompt_is_three_sentences_naming_the_number_branch_and_head(self) -> None:
        text: str = review.prompt(42, "ai/kimeta/real-use-x", "abc123")

        self.assertEqual(3, text.count(". ") + 1)
        self.assertIn("#42", text)
        self.assertIn("`ai/kimeta/real-use-x`", text)
        self.assertIn("--match-head-commit abc123", text)
        self.assertIn("Without waiting for running checks", text)
        self.assertNotIn("--watch", text)

    def test_a_flaky_fix_is_approved_and_auto_merged_for_the_head_the_agent_pushed(self) -> None:
        text: str = review.prompt(42, "ai/kimeta/real-use-x", "abc123")
        fixed: str = text[text.index("if you find one"):text.index("If you find none")]

        self.assertIn("push it to `ai/kimeta/real-use-x`", fixed)
        self.assertIn("`gh pr review 42 --approve`", fixed)
        self.assertIn("`gh pr merge 42 --auto --squash --match-head-commit <the new head you pushed>`", fixed)
        self.assertNotIn("abc123", fixed)

    def test_agent_command_follows_the_worker_target(self) -> None:
        self.assertEqual(
            ["claude", "--permission-mode", "bypassPermissions", "--model", "opus", "--effort", "high", "-p", "go"],
            review.agent_command("claude-code[high]:opus", "go"))
        self.assertEqual(
            ["pi", "--no-session", "--model", "gpt-5.6-luna", "--provider", "openai-codex", "--thinking", "high",
             "-p", "go"],
            review.agent_command("pi[high]:openai-codex/gpt-5.6-luna", "go"))
        with self.assertRaises(ValueError):
            review.agent_command("codex:gpt", "go")


class ReviewTest(unittest.TestCase):

    def test_the_queue_is_the_open_labeled_pull_requests_listed_with_gh(self) -> None:
        github: FakeGitHub = FakeGitHub([])

        self.assertEqual(0, github.run())
        arguments: list[str] = github.log[0][1]
        self.assertEqual("real-use-test", arguments[arguments.index("--label") + 1])
        self.assertEqual("open", arguments[arguments.index("--state") + 1])
        self.assertEqual(review.issue.REPOSITORY, arguments[arguments.index("--repo") + 1])
        self.assertEqual(
            {"number", "createdAt"}, set(arguments[arguments.index("--json") + 1].split(",")))

    def test_pull_requests_are_reviewed_oldest_first(self) -> None:
        github: FakeGitHub = FakeGitHub([_pr(3, 1), _pr(1, 30), _pr(2, 5)])

        self.assertEqual(3, github.run())
        self.assertEqual([1, 2, 3], github.agents())

    def test_the_agent_gets_the_branch_and_head_inside_the_label(self) -> None:
        github: FakeGitHub = FakeGitHub([_pr(7, 1)])

        github.run()
        self.assertEqual(
            [("view", 7), ("--add-label", 7), ("agent", 7, "ai/kimeta/real-use-7", "head7"),
             ("--remove-label", 7)],
            github.log[1:])

    def test_approved_and_freshly_held_pull_requests_are_skipped_without_an_agent(self) -> None:
        github: FakeGitHub = FakeGitHub([_pr(1, 9, decision="APPROVED"), _pr(2, 8, labeled_hours_ago=2.5)])

        self.assertEqual(0, github.run())
        self.assertEqual([], github.agents())
        self.assertNotIn(("timeline", 1), github.log)
        self.assertIn(("timeline", 2), github.log)
        self.assertFalse([entry for entry in github.log if entry[0] in ("--add-label", "--remove-label")])

    def test_a_pull_request_blocked_on_graalvm_is_skipped_without_an_agent(self) -> None:
        pull: dict[str, Any] = _pr(6, 9)
        pull["labels"].append({"name": "graalvm-bug"})
        github: FakeGitHub = FakeGitHub([pull])

        self.assertEqual(0, github.run())
        self.assertEqual([], github.agents())
        self.assertFalse([entry for entry in github.log if entry[0] in ("--add-label", "--remove-label")])

    def test_a_label_older_than_three_hours_is_taken_over(self) -> None:
        github: FakeGitHub = FakeGitHub([_pr(4, 9, labeled_hours_ago=3.5)])

        self.assertEqual(1, github.run())
        self.assertEqual(
            [("view", 4), ("timeline", 4), ("--add-label", 4), ("agent", 4, "ai/kimeta/real-use-4", "head4"),
             ("--remove-label", 4)],
            github.log[1:])

    def test_a_label_without_a_timeline_event_fails_the_review(self) -> None:
        pull: dict[str, Any] = _pr(5, 9, labeled_hours_ago=1)
        pull["labeledAt"] = None
        github: FakeGitHub = FakeGitHub([pull])

        with self.assertRaises(RuntimeError):
            github.run()

    def test_the_review_stops_after_three_reviewed_and_skipped_ones_do_not_count(self) -> None:
        pulls: list[dict[str, Any]] = [
            _pr(1, 10, decision="APPROVED"), _pr(2, 9), _pr(3, 8, labeled_hours_ago=1), _pr(4, 7), _pr(5, 6),
            _pr(6, 5),
        ]
        github: FakeGitHub = FakeGitHub(pulls)

        self.assertEqual(3, github.run())
        self.assertEqual([2, 4, 5], github.agents())
        self.assertNotIn(("--add-label", 6), github.log)
        self.assertNotIn(("view", 6), github.log)

    def test_each_pull_request_is_judged_as_it_is_after_the_earlier_agents(self) -> None:
        def meanwhile(pulls: dict[int, dict[str, Any]]) -> None:
            pulls[2]["labels"].append({"name": review.IN_REVIEW})
            pulls[2]["labeledAt"] = _stamp(NOW - timedelta(minutes=30))
            pulls[3]["state"] = "MERGED"
            pulls[4]["reviewDecision"] = "APPROVED"
            pulls[5]["headRefOid"] = "pushed5"

        github: FakeGitHub = FakeGitHub([_pr(1, 10), _pr(2, 9), _pr(3, 8), _pr(4, 7), _pr(5, 6)],
                                        during_agent={1: meanwhile})

        self.assertEqual(2, github.run())
        self.assertEqual([1, 5], github.agents())
        self.assertIn(("agent", 5, "ai/kimeta/real-use-5", "pushed5"), github.log)

    def test_the_label_is_removed_after_the_agent_whatever_it_did(self) -> None:
        github: FakeGitHub = FakeGitHub([_pr(8, 1)])
        with patch.object(review.subprocess, "run") as run, patch.object(review, "gh", side_effect=github.gh), \
                redirect_stderr(io.StringIO()):
            run.return_value.returncode = 1
            review.review("/repo", "claude-code:opus", NOW)

        commands: list[list[str]] = [call.args[0] for call in run.call_args_list]
        self.assertEqual("claude", commands[2][0])
        self.assertEqual(["worktree", "remove"], commands[3][3:5])
        self.assertEqual(("--remove-label", 8), github.log[-1])

    def test_main_takes_no_branches(self) -> None:
        with patch.object(review, "review", return_value=0) as reviewed, redirect_stderr(io.StringIO()):
            self.assertEqual(0, review.main(["--repo", "/repo", "--worker-agent", "claude-code:opus"]))
            reviewed.assert_called_once_with("/repo", "claude-code:opus")
            with self.assertRaises(SystemExit):
                review.main(["--repo", "/repo", "--worker-agent", "claude-code:opus", "some-branch"])


if __name__ == "__main__":
    unittest.main()
