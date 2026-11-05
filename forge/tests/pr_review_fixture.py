# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The fixture of the review tests (§FS-published-pr-review).

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
from dispatcher.config import REPO
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


class ReviewFixture(unittest.TestCase):
    """One open Forge pull request with a failed check, and helpers to change it and run a pass."""

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

    def fault(self, fragment: str, error: str) -> None:
        state: dict[str, Any] = self.state()
        state.setdefault("faults", []).append([fragment, error])
        self.save(state)

    def answer(self, outcome: str, **extra: Any) -> dict[str, Any]:
        return {"outcome": outcome, "case": 3, "cause": "metadata miss", "summary": "The lane failed.", **extra}
