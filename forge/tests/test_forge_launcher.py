# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Tests for the launcher: fetch, targets, close-out, and the review's entry.

§AR-forge-workflow-pipeline.1 fixes the fetch and the close-out, and
§FS-published-pr-review the review and its agent target.
"""
from __future__ import annotations

import contextlib
import io
import json
import os
import subprocess
import tempfile
import unittest
from unittest.mock import ANY, patch

import forge_metadata
from dispatcher import issue_fetch, pr_review, run_launch

FORGE_DIR: str = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def issue(number: int, *labels: str) -> dict:
    return {"number": number, "title": f"Support org.example:lib{number}:1.0.0",
            "labels": [{"name": name} for name in labels], "assignees": []}


class FetchTest(unittest.TestCase):

    def test_priority_tier_query_matches_the_launcher_contract(self) -> None:
        command = issue_fetch.fetch_command("library-new-request", ("priority",), ("high-priority",))
        self.assertEqual([
            "gh", "issue", "list", "--repo", "oracle/graalvm-reachability-metadata", "--state", "open",
            "--label", "library-new-request", "--label", "priority",
            "--search", "no:assignee -label:not-for-native-image -label:high-priority sort:created-asc",
            "--json", "number,title,labels,assignees", "--limit", "20",
        ], command)

    def test_every_label_of_a_tier_is_fetched_before_the_next_tier(self) -> None:
        commands = issue_fetch.fetch_commands(issue_fetch.QUEUE_LABELS)
        self.assertEqual(15, len(commands))
        self.assertTrue(all("high-priority" in command for _, command in commands[:5]))
        self.assertTrue(all("-label:priority" in " ".join(command) for _, command in commands[10:]))

    def test_candidates_are_unique_in_claim_order(self) -> None:
        pages = {"high": [issue(3, "fails-java-run")], "rest": [issue(3, "fails-java-run"), issue(4, "fails-java-run")]}

        def listed(command: list[str]) -> list[dict]:
            return pages["high"] if "high-priority" in command else pages["rest"]

        with patch.object(issue_fetch, "_run", side_effect=listed):
            candidates = issue_fetch.fetch_candidates(("fails-java-run",))
        self.assertEqual([3, 4], [payload["number"] for _, payload in candidates])

    def test_fixture_stands_in_for_github(self) -> None:
        with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False) as handle:
            json.dump([issue(8, "library-update-request"), issue(9, "library-new-request")], handle)
        self.addCleanup(os.remove, handle.name)
        candidates = issue_fetch.fetch_candidates(("library-new-request", "library-update-request"), handle.name)
        self.assertEqual([("library-new-request", 9), ("library-update-request", 8)],
                         [(label, payload["number"]) for label, payload in candidates])


class TargetTest(unittest.TestCase):

    def test_default_targets_are_accepted(self) -> None:
        self.assertIsNone(run_launch.target_error(forge_metadata.DEFAULT_SETUP_AGENT, forge_metadata.DEFAULT_WORKER_AGENT))

    def test_setup_must_be_claude_code(self) -> None:
        self.assertIn("claude-code", run_launch.target_error("pi[high]:openai-codex/gpt-5.6-luna", "pi:x/y") or "")

    def test_codex_and_opencode_are_rejected(self) -> None:
        self.assertIsNotNone(run_launch.target_error("claude-code:sonnet", "codex:gpt-5.6-luna"))
        self.assertIsNotNone(run_launch.target_error("claude-code:sonnet", "opencode:openai/gpt-5.6-luna"))

    def test_bad_target_stops_before_any_fetch(self) -> None:
        with patch.object(forge_metadata, "fetch_candidates") as fetch, contextlib.redirect_stderr(io.StringIO()):
            code = forge_metadata.main(["--worker-agent", "codex:gpt-5.6-luna"])
        self.assertEqual(forge_metadata.BAD_ARGUMENTS, code)
        fetch.assert_not_called()

    def test_dry_run_prints_the_queries_and_claims_nothing(self) -> None:
        with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False) as handle:
            json.dump([issue(5, "fails-javac-compile")], handle)
        self.addCleanup(os.remove, handle.name)
        output = io.StringIO()
        with patch.object(forge_metadata, "claim") as claim, contextlib.redirect_stdout(output):
            code = forge_metadata.main(["--label", "fails-javac-compile", "--issues-fixture", handle.name, "--dry-run"])
        self.assertEqual(forge_metadata.RAN, code)
        claim.assert_not_called()
        self.assertIn("--label fails-javac-compile --label high-priority", output.getvalue())
        self.assertIn("#5 [fails-javac-compile]", output.getvalue())


    def test_blocked_issues_are_claimed_only_on_request(self) -> None:
        with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False) as handle:
            json.dump([issue(5, "fails-javac-compile")], handle)
        self.addCleanup(os.remove, handle.name)
        for extra, expected in (([], False), (["--take-blocked-issues"], True)):
            with patch.object(forge_metadata, "claim", return_value=None) as claim, \
                    patch.object(forge_metadata, "resolve_authenticated_user", return_value="forge-bot"), \
                    contextlib.redirect_stderr(io.StringIO()):
                forge_metadata.main(["--issues-fixture", handle.name, *extra])
            self.assertEqual(expected, claim.call_args.args[4])

    def test_the_claim_forwards_the_blocker_override(self) -> None:
        with patch.object(run_launch, "refresh_issue_payload_for_claim", return_value=True), \
                patch.object(run_launch, "try_claim_issue", return_value=None) as try_claim:
            run_launch.claim(issue(5, "fails-javac-compile"), "fails-javac-compile", "forge-bot", FORGE_DIR, True)
        try_claim.assert_called_once_with(ANY, "forge-bot", "fails-javac-compile", True)


class CloseOutTest(unittest.TestCase):

    def close_out(self, outcome: dict) -> tuple[list, list, list]:
        claimed = run_launch.Claim(issue=issue(7, "library-new-request"), label="library-new-request", item_id="item")
        with patch.object(run_launch, "add_issue_label") as label, \
                patch.object(run_launch, "post_issue_comment") as comment, \
                patch.object(run_launch, "revert_issue_claim") as revert, \
                contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            run_launch.close_out(claimed, outcome)
        return label.call_args_list, comment.call_args_list, revert.call_args_list

    def test_completed_run_keeps_the_claim_for_the_pull_request(self) -> None:
        self.assertEqual(([], [], []), self.close_out({"outcome": "completed", "reason": "pushed"}))

    def test_human_intervention_labels_comments_and_releases(self) -> None:
        labels, comments, reverts = self.close_out({"outcome": "human-intervention", "reason": "native still fails"})
        self.assertEqual("human-intervention", labels[0].args[1])
        self.assertIn("native still fails", comments[0].args[1])
        self.assertEqual(1, len(reverts))

    def test_bad_input_needs_a_maintainer(self) -> None:
        labels, _, reverts = self.close_out({"outcome": "failed", "kind": "bad-input", "reason": "no URL"})
        self.assertEqual(1, len(labels))
        self.assertEqual(1, len(reverts))

    def test_infrastructure_failure_only_releases_the_claim(self) -> None:
        labels, comments, reverts = self.close_out({"outcome": "failed", "kind": "infrastructure", "reason": "x"})
        self.assertEqual(([], []), (labels, comments))
        self.assertEqual(1, len(reverts))

    def test_a_run_without_an_outcome_is_an_infrastructure_failure(self) -> None:
        self.assertEqual("infrastructure", run_launch.read_outcome(tempfile.mkdtemp())["kind"])


class WorkspaceTest(unittest.TestCase):

    def setUp(self) -> None:
        self.forge: str = tempfile.mkdtemp()
        self.addCleanup(__import__("shutil").rmtree, self.forge, True)

    def attempt(self, number: int, finished: bool) -> str:
        workspace: str = os.path.join(self.forge, "runs", f"forge-7-{number}")
        os.makedirs(os.path.dirname(os.path.join(workspace, run_launch.OUTCOME)))
        if finished:
            with open(os.path.join(workspace, run_launch.OUTCOME), "w", encoding="utf-8") as handle:
                json.dump({"outcome": "failed"}, handle)
        return workspace

    def test_the_first_attempt_is_numbered_one(self) -> None:
        self.assertEqual(os.path.join(self.forge, "runs", "forge-7-1"), run_launch.workspace_path(self.forge, 7))

    def test_an_unfinished_attempt_is_resumed(self) -> None:
        self.attempt(1, finished=True)
        unfinished: str = self.attempt(2, finished=False)
        self.assertEqual(unfinished, run_launch.workspace_path(self.forge, 7))

    def test_a_finished_attempt_never_gives_its_path_to_the_next(self) -> None:
        for number in (1, 2, 10):
            self.attempt(number, finished=True)
        self.assertEqual(os.path.join(self.forge, "runs", "forge-7-11"), run_launch.workspace_path(self.forge, 7))
        self.assertEqual([], run_launch.attempts(self.forge, 70))

class ReviewEntryTest(unittest.TestCase):

    def test_agent_command_follows_the_claude_code_target(self) -> None:
        self.assertEqual(["claude", "--permission-mode", "bypassPermissions", "--model", "opus", "--effort",
                          "high", "-p", "do"], pr_review.agent_command("claude-code[high]:opus", "do"))
        self.assertEqual(["claude", "--permission-mode", "bypassPermissions", "--model", "sonnet", "-p", "do"],
                         pr_review.agent_command("claude-code:sonnet", "do"))

    def test_the_review_agent_must_be_claude_code(self) -> None:
        for target in ("pi[high]:openai-codex/gpt-5.6-luna", "codex:gpt-5.6-luna", "claude-code:anthropic/opus"):
            self.assertIn("must be a claude-code target", pr_review.review_target_error(target))
            with self.assertRaises(ValueError):
                pr_review.agent_command(target, "do")
            with contextlib.redirect_stderr(io.StringIO()) as logged:
                self.assertEqual(forge_metadata.BAD_ARGUMENTS,
                                 forge_metadata.main(["--review", "--review-agent", target]))
            self.assertIn("--review-agent", logged.getvalue())
        self.assertIsNone(pr_review.review_target_error(forge_metadata.DEFAULT_REVIEW_AGENT))

    def test_review_runs_the_pass_with_the_review_agent(self) -> None:
        with patch.object(forge_metadata, "review_pass", return_value=1) as review_pass, \
                patch.object(forge_metadata, "resolve_authenticated_user", return_value="forge-bot"), \
                contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(forge_metadata.RAN, forge_metadata.main(
                ["--review", "--dry-run", "--pr", "9628", "--head", "a" * 40]))
        review_pass.assert_called_once_with(forge_metadata.REPO_DIR, forge_metadata.DEFAULT_REVIEW_AGENT,
                                            "forge-bot", True, [9628], "a" * 40)

    def test_a_head_replays_one_pull_request_in_a_dry_run_only(self) -> None:
        for argv in (["--review", "--pr", "1", "--head", "a" * 40],
                     ["--review", "--dry-run", "--pr", "1", "--pr", "2", "--head", "a" * 40]):
            with contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(forge_metadata.BAD_ARGUMENTS, forge_metadata.main(argv))


class ShellLauncherTest(unittest.TestCase):

    def test_setup_agent_without_web_search_is_rejected_before_the_host_check(self) -> None:
        completed = subprocess.run(
            [os.path.join(FORGE_DIR, "run-forge.sh"), "--setup-agent", "pi[high]:openai-codex/gpt-5.6-luna"],
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
        )
        self.assertEqual(2, completed.returncode)
        self.assertIn("must be a claude-code target", completed.stderr)

    def test_worker_agent_must_be_pi_or_claude_code(self) -> None:
        completed = subprocess.run(
            [os.path.join(FORGE_DIR, "run-forge.sh"), "--worker-agent", "codex:gpt-5.6-luna"],
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
        )
        self.assertEqual(2, completed.returncode)

    def test_review_agent_must_be_claude_code(self) -> None:
        completed = subprocess.run(
            [os.path.join(FORGE_DIR, "run-forge.sh"), "--review-agent", "pi[high]:openai-codex/gpt-5.6-luna"],
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
        )
        self.assertEqual(2, completed.returncode)
        self.assertIn("--review-agent", completed.stderr)


if __name__ == "__main__":
    unittest.main()
