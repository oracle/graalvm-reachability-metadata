# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The review of published pull requests (§FS-published-pr-review).

A fake `gh` keeps the GitHub side in a JSON file, the pull request's branch lives
in a real bare remote, and a fake agent runs the commands and writes the answer
its plan names.
"""
from __future__ import annotations

import contextlib
import io
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from datetime import datetime, timezone
from typing import Any
from unittest.mock import patch

from dispatcher import issue_admin, pr_review
from dispatcher.config import LABEL_HUMAN_INTERVENTION, LABEL_HUMAN_INTERVENTION_FIXED, REPO
from tests.pr_review_fakes import FAKE_AGENT, FAKE_GH

LOGIN: str = "forge-bot"
NUMBER: int = 7
BRANCH: str = "ai/forge-bot/lib-1.0.0"
DESCRIPTOR: str = "stats/org.example/lib/1.0.0/forge-publication.json"
TEST: str = "tests/src/org.example/lib/1.0.0/LibTest.java"
NOW: datetime = datetime(2026, 10, 27, 12, 0, tzinfo=timezone.utc)


def _git(*arguments: str, cwd: str) -> str:
    return subprocess.run(["git", "-c", "user.name=T", "-c", "user.email=t@example.com", "-c", "commit.gpgsign=false",
                           *arguments], cwd=cwd, check=True, stdout=subprocess.PIPE, text=True).stdout.strip()


def _executable(path: str, text: str) -> None:
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(text)
    os.chmod(path, 0o755)


def check(name: str, status: str = "completed", conclusion: str | None = "success") -> dict[str, Any]:
    return {"name": name, "status": status, "conclusion": conclusion,
            "html_url": f"https://github.com/{REPO}/actions/runs/1/job/11"}


class ReviewTest(unittest.TestCase):

    def setUp(self) -> None:
        self.tmp: str = tempfile.mkdtemp(prefix="forge-review-test-")
        self.addCleanup(shutil.rmtree, self.tmp, True)
        self.remote: str = os.path.join(self.tmp, "origin.git")
        self.repo: str = os.path.join(self.tmp, "repo")
        _git("init", "-q", "--bare", "-b", "master", self.remote, cwd=self.tmp)
        _git("init", "-q", "-b", "master", self.repo, cwd=self.tmp)
        _git("remote", "add", "origin", self.remote, cwd=self.repo)
        self.write("README.md", "base\n")
        _git("add", "-A", cwd=self.repo)
        _git("commit", "-q", "-m", "base", cwd=self.repo)
        _git("push", "-q", "origin", "master", cwd=self.repo)
        _git("checkout", "-q", "-b", BRANCH, cwd=self.repo)
        self.write(DESCRIPTOR, "{}\n")
        self.write(TEST, "class LibTest {}\n")
        _git("add", "-A", cwd=self.repo)
        _git("commit", "-q", "-m", "contribution", cwd=self.repo)
        self.head: str = _git("rev-parse", "HEAD", cwd=self.repo)
        _git("push", "-q", "origin", BRANCH, f"{BRANCH}:refs/pull/{NUMBER}/head", cwd=self.repo)
        _git("checkout", "-q", "master", cwd=self.repo)
        bin_dir: str = os.path.join(self.tmp, "bin")
        os.makedirs(bin_dir)
        _executable(os.path.join(bin_dir, "gh"), FAKE_GH)
        agent: str = os.path.join(self.tmp, "agent.py")
        _executable(agent, FAKE_AGENT)
        self.state_path: str = os.path.join(self.tmp, "gh.json")
        self.plan_path: str = os.path.join(self.tmp, "plan.json")
        environment: dict[str, str] = {"PATH": bin_dir + os.pathsep + os.environ["PATH"],
                                       "FAKE_GH_STATE": self.state_path, "FAKE_AGENT_PLAN": self.plan_path}
        patches = (patch.dict(os.environ, environment),
                   patch.object(pr_review, "agent_command", lambda target, text: [sys.executable, agent, text]),
                   patch.object(pr_review, "reconcile_auto_merged_pull_request_follow_ups", lambda: None),
                   patch.object(issue_admin, "ensured_issue_labels", set()))
        for started in patches:
            started.start()
            self.addCleanup(started.stop)
        self.save({"login": LOGIN, "remote": self.remote, "issues": [], "timeline": {},
                   "logs": {"11": "Can't find '[toLeakAwareBuffer]' in io.netty.buffer.AbstractByteBufAllocator"},
                   "checks": {self.head: [check("lint"), check("test (25)", conclusion="failure")]},
                   "runs": {self.head: [{"databaseId": 1, "status": "completed", "attempt": 1}]},
                   "prs": {str(NUMBER): self.pull()}})
        self.plan({"answer": None})

    def write(self, relative: str, text: str) -> None:
        path: str = os.path.join(self.repo, relative)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(text)

    @staticmethod
    def pull(number: int = NUMBER, created: str = "2026-10-01T00:00:00Z") -> dict[str, Any]:
        return {"number": number, "state": "OPEN", "createdAt": created, "title": "Add lib", "body": "Local review",
                "headRefName": BRANCH, "headRepository": {"nameWithOwner": REPO}, "reviewDecision": "",
                "labels": [{"name": "GenAI"}, {"name": "library-new-request"}], "files": [{"path": DESCRIPTOR}],
                "autoMergeRequest": None, "comments": [], "reviews": []}

    def save(self, state: dict[str, Any]) -> None:
        with open(self.state_path, "w", encoding="utf-8") as handle:
            json.dump(state, handle)

    def state(self) -> dict[str, Any]:
        with open(self.state_path, encoding="utf-8") as handle:
            return json.load(handle)

    def update(self, **changes: Any) -> None:
        state: dict[str, Any] = self.state()
        state["prs"][str(NUMBER)].update(changes)
        self.save(state)

    def green(self) -> None:
        state: dict[str, Any] = self.state()
        state["checks"][self.head] = [check("lint"), check("test (25)")]
        self.save(state)

    def plan(self, plan: dict[str, Any]) -> None:
        with open(self.plan_path, "w", encoding="utf-8") as handle:
            json.dump({"branch": BRANCH, **plan}, handle)

    def invocations(self) -> list[dict[str, Any]]:
        calls: str = self.plan_path + ".calls"
        if not os.path.exists(calls):
            return []
        with open(calls, encoding="utf-8") as lines:
            return [json.loads(line) for line in lines]

    def run_pass(self, dry_run: bool = False, numbers: list[int] | None = None, head: str | None = None) -> str:
        with contextlib.redirect_stdout(io.StringIO()) as printed, contextlib.redirect_stderr(io.StringIO()) as logged:
            pr_review.review_pass(self.repo, "claude-code[high]:opus", LOGIN, dry_run, numbers, head, NOW)
        self.logged: str = logged.getvalue()
        return printed.getvalue()

    def labels(self) -> set[str]:
        return {label["name"] for label in self.state()["prs"][str(NUMBER)]["labels"]}

    def comments(self) -> list[str]:
        return [comment["body"] for comment in self.state()["prs"][str(NUMBER)]["comments"]]

    def remote_head(self) -> str:
        return _git("ls-remote", self.remote, f"refs/heads/{BRANCH}", cwd=self.repo).split("\t")[0]

    def answer(self, outcome: str, **extra: Any) -> dict[str, Any]:
        return {"outcome": outcome, "case": 3, "cause": "metadata miss", "summary": "The lane failed.", **extra}

    def test_every_skip_reason_spends_no_agent(self) -> None:
        cases: list[tuple[dict[str, Any], str]] = [
            ({"state": "MERGED"}, "is merged"),
            ({"state": "CLOSED"}, "is closed"),
            ({"reviewDecision": "APPROVED"}, "is approved"),
            ({"labels": [{"name": "library-new-request"}, {"name": "reviewed"}]}, "is labeled reviewed"),
            ({"labels": [{"name": "library-new-request"}, {"name": LABEL_HUMAN_INTERVENTION}]},
             f"is labeled {LABEL_HUMAN_INTERVENTION}"),
            ({"labels": [{"name": "library-new-request"}, {"name": "in-review"}]}, "is held by another review"),
            ({"headRefName": "feature/x"}, "is not a Forge branch"),
            ({"headRepository": {"nameWithOwner": "fork/repo"}}, "is from another repository"),
            ({"labels": [{"name": "GenAI"}]}, "is not a Forge route"),
        ]
        state: dict[str, Any] = self.state()
        state["timeline"][str(NUMBER)] = ["2026-10-27T11:00:00Z"]
        self.save(state)
        for changes, reason in cases:
            self.update(**{**self.pull(), **changes})
            self.run_pass()
            self.assertIn(f"#{NUMBER} {reason}", self.logged)
        self.update(**self.pull())
        for checks, reason in (([check("lint"), check("test", "in_progress", None)], "waiting for 1 running checks"),
                               ([], "waiting for its first check")):
            state = self.state()
            state["checks"][self.head] = checks
            self.save(state)
            self.run_pass()
            self.assertIn(reason, self.logged)
        self.assertEqual([], self.invocations())

    def test_a_maintainer_fix_reopens_a_reviewed_pull_request(self) -> None:
        self.green()
        self.update(labels=[{"name": "library-new-request"}, {"name": "reviewed"},
                            {"name": LABEL_HUMAN_INTERVENTION_FIXED}])
        self.run_pass()
        self.assertEqual(1, len(self.invocations()))

    def test_a_stale_in_review_lock_is_taken_over(self) -> None:
        state: dict[str, Any] = self.state()
        state["timeline"][str(NUMBER)] = ["2026-10-27T08:30:00Z"]
        self.save(state)
        self.update(labels=[*self.pull()["labels"], {"name": "in-review"}])
        self.plan({"answer": self.answer("rerun", case=2), "commands": [["gh", "run", "rerun", "1", "--failed"]]})
        printed: str = self.run_pass()
        self.assertIn("is stale since", self.logged)
        self.assertIn("rerun: reran", printed)
        self.assertNotIn("in-review", self.labels())

    def test_approval_is_offered_only_on_a_green_head_and_ends_the_review(self) -> None:
        self.plan({"answer": None})
        self.run_pass()
        self.assertIn("so this case does not apply: do not approve", self.invocations()[0]["prompt"])
        self.green()
        self.update(comments=[], labels=self.pull()["labels"])
        self.plan({"answer": self.answer("approve", case=1, cause="all green"),
                   "commands": [["gh", "pr", "review", str(NUMBER), "--approve"],
                                ["gh", "pr", "merge", str(NUMBER), "--auto", "--squash"]]})
        printed: str = self.run_pass()
        self.assertIn(f"--match-head-commit {self.head}", self.invocations()[-1]["prompt"])
        self.assertIn("approve (terminal): approved", printed)
        self.assertEqual({"GenAI", "library-new-request", "reviewed"}, self.labels())

    def test_an_approval_on_a_failed_head_is_escalated(self) -> None:
        self.plan({"answer": self.answer("approve", case=1)})
        self.run_pass()
        self.assertIn("approved a head whose checks failed", self.state()["issues"][0]["body"])
        self.assertTrue({LABEL_HUMAN_INTERVENTION, "reviewed"} <= self.labels())

    def test_an_unconfirmed_approval_is_escalated(self) -> None:
        self.green()
        self.plan({"answer": self.answer("approve", case=1)})
        self.run_pass()
        self.assertIn("could not be confirmed", self.state()["issues"][0]["body"])

    def test_a_rerun_is_not_terminal_and_is_bounded_to_once_per_head(self) -> None:
        self.plan({"answer": self.answer("rerun", case=2), "commands": [["gh", "run", "rerun", "1", "--failed"]]})
        self.run_pass()
        self.assertIn(f"<!-- forge-review:{self.head}:rerun -->", self.comments()[-1])
        self.assertEqual({"GenAI", "library-new-request"}, self.labels())
        self.assertIn("1 rerun of this head", self.invocations()[0]["prompt"])
        # The rerun failed again: the next pass says no rerun is left, and a second one ends in an issue.
        state: dict[str, Any] = self.state()
        state["runs"][self.head] = [{"databaseId": 1, "status": "completed", "attempt": 2}]
        self.save(state)
        self.run_pass()
        self.assertIn("0 rerun of this head", self.invocations()[1]["prompt"])
        self.assertIn("already rerun once", self.state()["issues"][0]["body"])
        self.assertTrue({LABEL_HUMAN_INTERVENTION, "reviewed"} <= self.labels())

    def test_a_pushed_fix_is_confirmed_marked_and_not_terminal(self) -> None:
        self.plan({"answer": self.answer("fix"), "files": {TEST: "class LibTest { }\n"},
                   "commands": [["git", "push", "-q", "origin", "HEAD:refs/heads/{branch}",
                                 f"HEAD:refs/pull/{NUMBER}/head"]]})
        printed: str = self.run_pass()
        self.assertNotEqual(self.head, self.remote_head())
        self.assertIn("fix: pushed", printed)
        self.assertIn(f"<!-- forge-review:{self.head}:push -->", self.comments()[-1])
        self.assertNotIn("reviewed", self.labels())
        self.assertNotIn("in-review", self.labels())

    def test_the_third_fix_push_is_escalated(self) -> None:
        self.update(comments=[{"author": {"login": LOGIN}, "body": f"<!-- forge-review:{'a' * 40}:push -->"},
                              {"author": {"login": LOGIN}, "body": f"<!-- forge-review:{'b' * 40}:push -->"},
                              {"author": {"login": "someone"}, "body": f"<!-- forge-review:{'c' * 40}:push -->"}])
        self.plan({"answer": self.answer("fix"), "files": {TEST: "class LibTest { }\n"}})
        self.run_pass()
        self.assertIn("0 fix\npushes", self.invocations()[0]["prompt"])
        self.assertIn("no fix push left", self.state()["issues"][0]["body"])
        self.assertTrue({LABEL_HUMAN_INTERVENTION, "reviewed"} <= self.labels())

    def test_a_fix_outside_the_contribution_is_escalated(self) -> None:
        self.plan({"answer": self.answer("fix"), "files": {"README.md": "changed\n"},
                   "commands": [["git", "push", "-q", "origin", "HEAD:refs/heads/{branch}",
                                 f"HEAD:refs/pull/{NUMBER}/head"]]})
        self.run_pass()
        self.assertIn("changes README.md", self.state()["issues"][0]["body"])

    def test_an_issue_reuses_its_signature_and_ends_the_review(self) -> None:
        state: dict[str, Any] = self.state()
        state["issues"].append({"number": 42, "state": "OPEN", "title": "NI 25.0 bug",
                                "body": "...\n\n<!-- forge-ci-signature: ni-25.0-type-not-found -->"})
        self.save(state)
        self.update(labels=[*self.pull()["labels"], {"name": LABEL_HUMAN_INTERVENTION_FIXED}])
        self.plan({"answer": self.answer("issue", case=4, cause="Native Image 25.0 bug",
                                         issue={"title": "t", "body": "b", "signature": "NI 25.0 type not found"})})
        printed: str = self.run_pass()
        self.assertIn("issue (terminal): reused #42", printed)
        self.assertEqual(1, len(self.state()["issues"]))
        self.assertEqual({"GenAI", "library-new-request", LABEL_HUMAN_INTERVENTION, "reviewed"}, self.labels())
        self.assertIn("tracked in #42", self.comments()[-1])

    def test_a_missing_answer_is_escalated_with_the_evidence(self) -> None:
        self.run_pass()
        body: str = self.state()["issues"][0]["body"]
        self.assertIn("missing or malformed", body)
        self.assertIn("toLeakAwareBuffer", body)

    def test_a_pass_reviews_at_most_three_pull_requests_oldest_first(self) -> None:
        state: dict[str, Any] = self.state()
        for number in (8, 9, 10):
            state["prs"][str(number)] = self.pull(number, f"2026-09-0{number - 7}T00:00:00Z")
            _git("push", "-q", "origin", f"{self.head}:refs/pull/{number}/head", cwd=self.repo)
        self.save(state)
        self.run_pass(dry_run=True)
        invoked: list[str] = [line for line in self.logged.splitlines() if "invoking the agent" in line]
        self.assertEqual(["#8", "#9", "#10"], [line.split()[1] for line in invoked])

    def test_a_dry_run_writes_nothing_and_withholds_credentials(self) -> None:
        self.plan({"answer": self.answer("fix"), "files": {TEST: "class LibTest { }\n"}})
        printed: str = self.run_pass(dry_run=True)
        self.assertIn("fix: would push", printed)
        self.assertEqual((self.head, [], [], {"GenAI", "library-new-request"}),
                         (self.remote_head(), self.comments(), self.state()["issues"], self.labels()))
        invocation: dict[str, Any] = self.invocations()[0]
        self.assertEqual("forge-review-dry-run", invocation["GH_TOKEN"])
        self.assertIn("pushinsteadof git@github.com:", invocation["push_rewrites"])
        self.assertIn("This is a dry run.", invocation["prompt"])
        with open(self.state_path + ".calls", encoding="utf-8") as handle:
            calls: str = handle.read()
        for write in ('"edit"', '"comment"', '"create"', '"rerun"', '"review"', '"merge"', '"POST"', '"DELETE"'):
            self.assertNotIn(write, calls)

    def test_a_dry_run_replays_a_closed_pull_request_at_an_earlier_head(self) -> None:
        self.update(state="MERGED", reviewDecision="APPROVED",
                    labels=[*self.pull()["labels"], {"name": LABEL_HUMAN_INTERVENTION}])
        self.plan({"answer": self.answer("issue", case=4, cause="Native Image 25.0 bug",
                                         issue={"title": "t", "body": "b", "signature": "ni-bug"})})
        printed: str = self.run_pass(dry_run=True, numbers=[NUMBER], head=self.head)
        self.assertIn(f"#{NUMBER} is merged; replaying it", self.logged)
        self.assertIn("issue (terminal): would open \"t\" [ni-bug]", printed)
        self.assertIn("case 4, Native Image 25.0 bug", printed)

    def test_the_fix_state_metadata_rules_reach_the_prompt(self) -> None:
        rules: str = pr_review.metadata_rules()
        self.assertTrue(rules.startswith("Metadata\n"))
        self.assertIn("typeReached", rules)
        self.assertIn("Native Image", rules)
        self.assertNotIn("Before your final message", rules)


if __name__ == "__main__":
    unittest.main()
