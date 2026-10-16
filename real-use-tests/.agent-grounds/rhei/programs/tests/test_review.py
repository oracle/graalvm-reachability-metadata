# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Unit tests for the launcher's review of published pull requests.

§FS-real-use-test-generation.4 states the rules; §AR-real-use-test-launcher the wiring.
"""
from __future__ import annotations

import io
import os
import sys
import tempfile
import unittest
from contextlib import redirect_stderr
from typing import Any
from unittest.mock import patch

PROGRAMS: str = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, PROGRAMS)

import review  # noqa: E402


def _pr(number: int, state: str = "OPEN", decision: str = "REVIEW_REQUIRED", head: str = "a1") -> dict[str, Any]:
    return {"number": number, "state": state, "reviewDecision": decision, "headRefOid": head}


class ReviewTest(unittest.TestCase):

    def test_prompt_is_three_sentences_naming_the_number_branch_and_head(self) -> None:
        text: str = review.prompt(42, "ai/kimeta/real-use-x", "abc123")

        self.assertEqual(3, text.count(". ") + 1)
        self.assertIn("#42", text)
        self.assertIn("`ai/kimeta/real-use-x`", text)
        self.assertIn("--match-head-commit abc123", text)
        self.assertNotIn("--watch", text)

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

    def test_the_agent_writes_to_stderr_so_stdout_lists_only_branches(self) -> None:
        with patch.object(review.subprocess, "run") as run, tempfile.TemporaryDirectory() as repo:
            review.run_agent(repo, "claude-code[high]:opus", 7, "ai/x/branch", "abc")

        agent_calls = [call for call in run.call_args_list if call.args[0][0] == "claude"]
        self.assertEqual(1, len(agent_calls))
        self.assertIs(sys.stderr, agent_calls[0].kwargs["stdout"])

    def test_approved_merged_and_closed_pull_requests_are_skipped_without_an_agent(self) -> None:
        found: dict[str, dict[str, Any]] = {
            "approved": _pr(1, decision="APPROVED"),
            "merged": _pr(2, state="MERGED"),
            "closed": _pr(3, state="CLOSED"),
        }
        with patch.object(review, "pull_request", side_effect=found.get), \
                patch.object(review, "run_agent") as agent, redirect_stderr(io.StringIO()):
            again: list[str] = review.review("/repo", "claude-code:opus", list(found))

        self.assertEqual([], again)
        agent.assert_not_called()

    def test_a_branch_whose_head_the_agent_moved_is_reviewed_again(self) -> None:
        before: dict[str, dict[str, Any]] = {"fixed": _pr(4, head="old"), "approved": _pr(5, head="same")}
        after: dict[str, dict[str, Any]] = {"fixed": _pr(4, head="new"), "approved": _pr(5, "OPEN", "APPROVED", "same")}
        reads: list[dict[str, Any]] = [before["fixed"], after["fixed"], before["approved"], after["approved"]]
        with patch.object(review, "pull_request", side_effect=reads), \
                patch.object(review, "run_agent") as agent, redirect_stderr(io.StringIO()):
            again: list[str] = review.review("/repo", "claude-code:opus", ["fixed", "approved"])

        self.assertEqual(["fixed"], again)
        agent.assert_any_call("/repo", "claude-code:opus", 4, "fixed", "old")
        self.assertEqual(2, agent.call_count)

    def test_a_pull_request_not_open_yet_waits_for_the_next_batch(self) -> None:
        with patch.object(review, "pull_request", return_value=None), \
                patch.object(review, "run_agent") as agent, redirect_stderr(io.StringIO()) as narration:
            again: list[str] = review.review("/repo", "claude-code:opus", ["unopened"])

        self.assertEqual(["unopened"], again)
        agent.assert_not_called()
        self.assertIn("no pull request opened for unopened yet", narration.getvalue())


if __name__ == "__main__":
    unittest.main()
