# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The CI pass over published pull requests (§FS-published-pr-review).

A fake `gh` keeps the GitHub side in a JSON file and answers the real
`.ephor/ci-failures.sh` and `.ephor/ci-restart.sh`; the pull request's branch
lives in a real bare remote, and a fake agent writes the diagnosis it is told to.
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
from typing import Any
from unittest.mock import patch

from dispatcher import pr_ci
from dispatcher.config import LABEL_HUMAN_INTERVENTION, REPO

REPOSITORY: str = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
LOGIN: str = "forge-bot"
NUMBER: int = 7
BRANCH: str = "ai/forge-bot/lib-1.0.0"
COORDINATE_PATH: str = "stats/org.example/lib/1.0.0/forge-publication.json"

FAKE_GH: str = r'''#!/usr/bin/env python3
import json, os, sys
path = os.environ["FAKE_GH_STATE"]
state = json.load(open(path))
args = sys.argv[1:]
with open(path + ".calls", "a") as log:
    log.write(json.dumps(args) + "\n")
def value(flag, default=None):
    for name in (flag,) + {"--repo": ("-R",), "--commit": ("-c",)}.get(flag, ()):
        if name in args:
            return args[args.index(name) + 1]
    return default
def save():
    json.dump(state, open(path, "w"))
def reply(data):
    jq = value("--jq")
    if jq:
        data = data[jq.lstrip(".")]
        print(data if isinstance(data, str) else json.dumps(data))
    else:
        print(json.dumps(data))
    sys.exit(0)
pr = lambda: state["prs"][args[2]]
if args[:2] == ["pr", "list"]:
    reply(state["pulls"])
if args[:2] == ["pr", "view"]:
    fields = value("--json").split(",")
    reply({field: pr()[field] for field in fields})
if args[:2] == ["run", "view"]:
    print(state["logs"].get(value("--job"), "no log"))
    sys.exit(0)
if args[:2] == ["run", "list"]:
    reply(state["runs"].get(value("--commit"), []))
if args[:2] == ["run", "rerun"]:
    for runs in state["runs"].values():
        for run in runs:
            if str(run["databaseId"]) == args[2]:
                run["status"], run["attempt"] = "queued", run.get("attempt", 1) + 1
    save()
    sys.exit(0)
if args[:2] == ["issue", "list"]:
    reply([issue for issue in state["issues"] if issue["state"] == "OPEN"])
if args[:2] == ["issue", "create"]:
    number = 500 + len(state["issues"])
    state["issues"].append({"number": number, "state": "OPEN", "title": value("--title"), "body": value("--body")})
    save()
    print(f"https://github.com/{value('--repo')}/issues/{number}")
    sys.exit(0)
if args[:2] == ["issue", "view"]:
    issue = next(issue for issue in state["issues"] if str(issue["number"]) == args[2])
    reply({"state": issue["state"]})
if args[:2] == ["issue", "comment"]:
    state["prs"][args[2]]["comments"].append({"author": {"login": state["login"]}, "body": value("--body")})
    save()
    sys.exit(0)
if args[0] == "api" and args[-1].startswith("labels[]="):
    number = args[args.index("POST") + 1].split("/")[-2]
    state["prs"][number]["labels"].append({"name": args[-1].split("=", 1)[1]})
    save()
    sys.exit(0)
if args[0] == "api" and "/labels/" in args[1]:
    sys.exit(0)
print("unexpected gh " + " ".join(args), file=sys.stderr)
sys.exit(1)
'''

FAKE_AGENT: str = r'''#!/usr/bin/env python3
import json, os, re, subprocess, sys
plan = json.load(open(os.environ["FAKE_AGENT_PLAN"]))
with open(os.environ["FAKE_AGENT_PLAN"] + ".calls", "a") as log:
    log.write("diagnosed\n")
for relative, text in plan.get("files", {}).items():
    os.makedirs(os.path.dirname(relative) or ".", exist_ok=True)
    open(relative, "w").write(text)
if plan.get("files"):
    subprocess.run(["git", "add", "-A"], check=True)
    subprocess.run(["git", "-c", "user.name=Agent", "-c", "user.email=a@example.com", "commit", "-q", "-m", "Fix"],
                   check=True)
if plan.get("diagnosis") is not None:
    target = re.search(r"Write `([^`]+)` as JSON", sys.argv[1]).group(1)
    json.dump(plan["diagnosis"], open(target, "w"))
'''


def _git(*arguments: str, cwd: str) -> str:
    return subprocess.run(["git", "-c", "user.name=T", "-c", "user.email=t@example.com", "-c", "commit.gpgsign=false",
                           *arguments], cwd=cwd, check=True, stdout=subprocess.PIPE, text=True).stdout.strip()


def _executable(path: str, text: str) -> None:
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(text)
    os.chmod(path, 0o755)


class CIPassTest(unittest.TestCase):

    def setUp(self) -> None:
        self.tmp: str = tempfile.mkdtemp(prefix="forge-ci-test-")
        self.addCleanup(shutil.rmtree, self.tmp, True)
        remote: str = os.path.join(self.tmp, "origin.git")
        self.repo: str = os.path.join(self.tmp, "repo")
        _git("init", "-q", "--bare", "-b", "master", remote, cwd=self.tmp)
        _git("init", "-q", "-b", "master", self.repo, cwd=self.tmp)
        _git("remote", "add", "origin", remote, cwd=self.repo)
        self.write("README.md", "base\n")
        _git("add", "-A", cwd=self.repo)
        _git("commit", "-q", "-m", "base", cwd=self.repo)
        self.base: str = _git("rev-parse", "HEAD", cwd=self.repo)
        _git("push", "-q", "origin", "master", cwd=self.repo)
        _git("checkout", "-q", "-b", BRANCH, cwd=self.repo)
        self.write(COORDINATE_PATH, "{}\n")
        self.write("tests/src/org.example/lib/1.0.0/LibTest.java", "class LibTest {}\n")
        _git("add", "-A", cwd=self.repo)
        _git("commit", "-q", "-m", "contribution", cwd=self.repo)
        self.head: str = _git("rev-parse", "HEAD", cwd=self.repo)
        _git("push", "-q", "origin", BRANCH, f"{BRANCH}:refs/pull/{NUMBER}/head", cwd=self.repo)
        _git("checkout", "-q", "master", cwd=self.repo)
        shutil.copytree(os.path.join(REPOSITORY, ".ephor"), os.path.join(self.repo, ".ephor"))
        bin_dir: str = os.path.join(self.tmp, "bin")
        os.makedirs(bin_dir)
        _executable(os.path.join(bin_dir, "gh"), FAKE_GH)
        self.agent: str = os.path.join(self.tmp, "agent.py")
        _executable(self.agent, FAKE_AGENT)
        self.state_path: str = os.path.join(self.tmp, "gh.json")
        self.plan_path: str = os.path.join(self.tmp, "plan.json")
        environment: dict[str, str] = {"PATH": bin_dir + os.pathsep + os.environ["PATH"],
                                       "FAKE_GH_STATE": self.state_path, "FAKE_AGENT_PLAN": self.plan_path}
        patches = (patch.dict(os.environ, environment),
                   patch.object(pr_ci, "agent_command", lambda target, text: [sys.executable, self.agent, text]),
                   patch.object(pr_ci, "reconcile_auto_merged_pull_request_follow_ups", lambda: None))
        for started in patches:
            started.start()
            self.addCleanup(started.stop)
        self.save({"login": LOGIN, "pulls": [self.pull()], "logs": {"11": "MissingReflectionRegistrationError: x"},
                   "issues": [], "runs": {self.head: [self.workflow_run("completed", "failure")]},
                   "prs": {str(NUMBER): {"headRefOid": self.head, "comments": [], "labels": self.pull()["labels"],
                                         "statusCheckRollup": [self.check("FAILURE")]}}})
        self.plan({"diagnosis": None})

    def write(self, relative: str, text: str) -> None:
        path: str = os.path.join(self.repo, relative)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(text)

    def pull(self) -> dict[str, Any]:
        return {"number": NUMBER, "createdAt": "2026-10-01T00:00:00Z", "headRefName": BRANCH,
                "headRefOid": self.head, "baseRefOid": self.base, "headRepository": {"nameWithOwner": REPO},
                "reviewDecision": "", "labels": [{"name": "GenAI"}, {"name": "library-new-request"}],
                "files": [{"path": COORDINATE_PATH}]}

    @staticmethod
    def check(conclusion: str, status: str = "COMPLETED") -> dict[str, str]:
        return {"name": "test (25, current-defaults)", "status": status, "conclusion": conclusion,
                "detailsUrl": "https://github.com/x/actions/runs/1/job/11"}

    @staticmethod
    def workflow_run(status: str, conclusion: str) -> dict[str, Any]:
        return {"databaseId": 1, "workflowName": "Test changed metadata", "status": status,
                "conclusion": conclusion, "attempt": 1}

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

    def plan(self, plan: dict[str, Any]) -> None:
        with open(self.plan_path, "w", encoding="utf-8") as handle:
            json.dump(plan, handle)

    def diagnoses(self) -> int:
        calls: str = self.plan_path + ".calls"
        if not os.path.exists(calls):
            return 0
        with open(calls, encoding="utf-8") as lines:
            return sum(1 for _ in lines)

    def handle(self, dry_run: bool = False) -> pr_ci.Outcome | None:
        return pr_ci.Pass(self.repo, "claude-code:sonnet", LOGIN, dry_run).handle(self.pull())

    def comments(self) -> list[str]:
        return [comment["body"] for comment in self.state()["prs"][str(NUMBER)]["comments"]]

    def labels(self) -> set[str]:
        return {label["name"] for label in self.state()["prs"][str(NUMBER)]["labels"]}

    def remote_head(self) -> str:
        return _git("ls-remote", "origin", f"refs/heads/{BRANCH}", cwd=self.repo).split("\t")[0]

    def test_a_green_head_is_skipped(self) -> None:
        self.update(statusCheckRollup=[self.check("SUCCESS")])
        self.assertIsNone(self.handle())
        self.assertEqual(0, self.diagnoses())

    def test_a_running_head_is_skipped_even_with_a_failure(self) -> None:
        self.update(statusCheckRollup=[self.check("FAILURE"), self.check("", "IN_PROGRESS")])
        self.assertIsNone(self.handle())
        self.update(statusCheckRollup=[self.check("", "IN_PROGRESS")])
        self.assertIsNone(self.handle())
        self.assertEqual(0, self.diagnoses())

    def test_a_handled_head_is_never_diagnosed_again(self) -> None:
        self.update(comments=[{"author": {"login": LOGIN}, "body": f"x <!-- forge-ci:{self.head}:push -->"}])
        self.assertEqual("none", self.handle().action)
        self.assertEqual(0, self.diagnoses())

    def test_a_marker_from_another_account_does_not_count(self) -> None:
        self.update(comments=[{"author": {"login": "someone"}, "body": f"<!-- forge-ci:{self.head}:push -->"}])
        self.plan({"diagnosis": {"cause": "transient", "summary": "The runner lost its network."}})
        self.assertEqual("rerun", self.handle().action)

    def test_a_transient_failure_is_rerun_once_and_then_becomes_an_issue(self) -> None:
        self.plan({"diagnosis": {"cause": "transient", "summary": "The Docker pull timed out."}})
        self.assertEqual("rerun", self.handle().action)
        self.assertEqual("queued", self.state()["runs"][self.head][0]["status"])
        self.assertIn(f"<!-- forge-ci:{self.head}:rerun -->", self.comments()[-1])
        # The rerun failed again: no second diagnosis, no second rerun.
        state: dict[str, Any] = self.state()
        state["runs"][self.head] = [self.workflow_run("completed", "failure")]
        self.save(state)
        outcome: pr_ci.Outcome = self.handle()
        self.assertEqual(("issue", 1), (outcome.action, self.diagnoses()))
        self.assertIn("rerun as transient and failed again", self.state()["issues"][0]["body"])
        self.assertIn(LABEL_HUMAN_INTERVENTION, self.labels())

    def test_a_defect_fix_is_pushed_and_confirmed(self) -> None:
        self.plan({"diagnosis": {"cause": "defect", "summary": "The test pins a timezone."},
                   "files": {"tests/src/org.example/lib/1.0.0/LibTest.java": "class LibTest { }\n"}})
        outcome: pr_ci.Outcome = self.handle()
        self.assertEqual("push", outcome.action)
        self.assertNotEqual(self.head, self.remote_head())
        self.assertIn(f"<!-- forge-ci:{self.head}:push -->", self.comments()[-1])

    def test_a_fix_outside_the_contribution_is_refused(self) -> None:
        self.plan({"diagnosis": {"cause": "defect", "summary": "The harness is wrong."},
                   "files": {"README.md": "changed\n"}})
        self.assertEqual("issue", self.handle().action)
        self.assertEqual(self.head, self.remote_head())
        self.assertIn("changes README.md", self.state()["issues"][0]["body"])

    def test_the_third_fix_push_becomes_an_issue(self) -> None:
        other: str = "a" * 40
        self.update(comments=[{"author": {"login": LOGIN}, "body": f"<!-- forge-ci:{other}:push -->"},
                              {"author": {"login": LOGIN}, "body": f"<!-- forge-ci:{'b' * 40}:push -->"}])
        self.plan({"diagnosis": {"cause": "flaky", "summary": "The test races a thread."},
                   "files": {"tests/src/org.example/lib/1.0.0/LibTest.java": "class LibTest { }\n"}})
        self.assertEqual("issue", self.handle().action)
        self.assertEqual(self.head, self.remote_head())
        self.assertIn("no fix push left", self.state()["issues"][0]["body"])

    def test_an_unverified_diagnosis_becomes_an_issue_and_human_intervention(self) -> None:
        outcome: pr_ci.Outcome = self.handle()
        self.assertEqual("issue", outcome.action)
        issue: dict[str, Any] = self.state()["issues"][0]
        self.assertIn("missing or malformed", issue["body"])
        self.assertIn("MissingReflectionRegistrationError", issue["body"])
        self.assertIn(LABEL_HUMAN_INTERVENTION, self.labels())
        self.assertIn(f"tracked in #{issue['number']}", self.comments()[-1])
        self.assertIn(f"<!-- forge-ci:{self.head}:issue -->", self.comments()[-1])

    def test_an_outside_cause_reuses_the_open_issue_with_its_signature(self) -> None:
        state: dict[str, Any] = self.state()
        state["issues"].append({"number": 42, "state": "OPEN", "title": "GraalVM EA breaks Spring AOT",
                                "body": "...\n\n<!-- forge-ci-signature: graalvm-ea-spring-aot-npe -->"})
        self.save(state)
        self.plan({"diagnosis": {"cause": "outside", "summary": "GraalVM EA regressed Spring AOT.",
                                 "issue": {"title": "t", "body": "b", "signature": "GraalVM EA Spring AOT NPE"}}})
        outcome: pr_ci.Outcome = self.handle()
        self.assertEqual(("issue", "reused #42"), (outcome.action, outcome.detail))
        self.assertEqual(1, len(self.state()["issues"]))
        self.assertIn(LABEL_HUMAN_INTERVENTION, self.labels())

    def test_a_dry_run_diagnoses_but_performs_nothing(self) -> None:
        self.plan({"diagnosis": {"cause": "outside", "summary": "GraalVM EA regressed Spring AOT.",
                                 "issue": {"title": "t", "body": "b", "signature": "graalvm-ea-spring-aot-npe"}}})
        outcome: pr_ci.Outcome = self.handle(dry_run=True)
        self.assertTrue(outcome.detail.startswith("would open"))
        self.assertEqual(([], [], 1), (self.state()["issues"], self.comments(), self.diagnoses()))
        self.assertNotIn(LABEL_HUMAN_INTERVENTION, self.labels())

    def test_the_pass_skips_ineligible_pull_requests_and_counts_what_it_handled(self) -> None:
        state: dict[str, Any] = self.state()
        state["pulls"].insert(0, {**self.pull(), "number": 3, "headRefName": "feature/x"})
        self.save(state)
        self.plan({"diagnosis": {"cause": "transient", "summary": "The runner lost its network."}})
        with contextlib.redirect_stdout(io.StringIO()) as printed, contextlib.redirect_stderr(io.StringIO()) as logged:
            self.assertEqual(1, pr_ci.ci_pass(self.repo, "claude-code:sonnet", LOGIN, dry_run=True))
        self.assertIn("#3 is not a Forge branch", logged.getvalue())
        self.assertIn(f"#{NUMBER}: would rerun the failed jobs of {self.head[:12]}", printed.getvalue())
        self.assertEqual("completed", self.state()["runs"][self.head][0]["status"])

    def test_eligibility_is_structural(self) -> None:
        pull: dict[str, Any] = self.pull()
        self.assertIsNone(pr_ci.skip_reason(pull))
        self.assertEqual("not a Forge branch", pr_ci.skip_reason({**pull, "headRefName": "feature/x"}))
        self.assertEqual("from another repository",
                         pr_ci.skip_reason({**pull, "headRepository": {"nameWithOwner": "fork/repo"}}))
        self.assertEqual("not a Forge route", pr_ci.skip_reason({**pull, "labels": [{"name": "GenAI"}]}))
        self.assertEqual("held for a maintainer",
                         pr_ci.skip_reason({**pull, "labels": [*pull["labels"], {"name": LABEL_HUMAN_INTERVENTION}]}))
        self.assertEqual("org.example:lib:1.0.0", pr_ci.coordinate(pull))

if __name__ == "__main__":
    unittest.main()
