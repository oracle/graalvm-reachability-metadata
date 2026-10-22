# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The launcher's CI pass over published Forge pull requests (§FS-published-pr-ci-failures).

Merged pull requests first release their issues; then each eligible open Forge
pull request whose head fails CI, oldest first and at most `CI_LIMIT` of them,
gets one diagnosis by the setup agent in a throwaway checkout of the head. The
agent writes its decision and this module carries it out — a rerun, a push, or
an issue — and records the head as handled only once the action is confirmed
(§AR-forge-dispatcher-decomposition.1).
"""
from __future__ import annotations

import json
import os
import re
import subprocess
import sys
import tempfile
from contextlib import contextmanager
from dataclasses import dataclass
from typing import Any, Iterator

from dispatcher.config import LABEL_HUMAN_INTERVENTION, LABEL_HUMAN_INTERVENTION_FIXED, REPO
from dispatcher.github_api import gh, gh_json
from dispatcher.issue_admin import add_pull_request_label, post_issue_comment
from dispatcher.pr_merge import reconcile_auto_merged_pull_request_follow_ups

CI_LIMIT: int = 3
FIX_PUSH_LIMIT: int = 2
STILL_RUNNING: int = 75
TARGET: re.Pattern[str] = re.compile(r"^(pi|claude-code)(?:\[([A-Za-z0-9_-]+)\])?:(?:(.*)/)?([^/]+)$")
# The Forge route labels of §FS-forge-functional-spec, whose pull requests the pass watches.
ROUTE_LABELS: tuple[str, ...] = (
    "library-new-request",
    "library-update-request",
    "fixes-javac-fail",
    "fixes-java-run-fail",
    "fixes-native-image-run-fail",
)
# The action each cause allows (§FS-published-pr-ci-failures.3).
CAUSE_ACTIONS: dict[str, str] = {"transient": "rerun", "defect": "push", "flaky": "push", "outside": "issue"}
MARKER: re.Pattern[str] = re.compile(r"<!-- forge-ci:([0-9a-f]{40}):(rerun|push|issue) -->")
SIGNATURE: str = "forge-ci-signature"
DESCRIPTOR: re.Pattern[str] = re.compile(r"^stats/([^/]+)/([^/]+)/([^/]+)/forge-publication\.json$")
EVIDENCE_LIMIT: int = 6000
# A hung agent ends as an unverified diagnosis, not a stuck launcher.
AGENT_TIMEOUT_SECONDS: int = 2 * 60 * 60
# A dry run's agent gets no GitHub credential and no push URL, so it cannot write
# even against its instruction (§FS-published-pr-ci-failures.5).
DRY_RUN_ENVIRONMENT: dict[str, str] = {"GH_TOKEN": "forge-ci-dry-run", "GIT_CONFIG_COUNT": "1",
                                       "GIT_CONFIG_KEY_0": "remote.origin.pushurl",
                                       "GIT_CONFIG_VALUE_0": "forge-ci-dry-run:no-push"}
TEMPLATE: str = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                             "prompt_templates", "ci_failure", "diagnosis.md")


@dataclass
class Failing:
    """A head's failing jobs and their evidence, as `.ephor/ci-failures.sh` reports them."""

    jobs: list[dict[str, str]]
    evidence: str


@dataclass
class Outcome:
    """What the pass did, or would do in a dry run, for one pull request."""

    action: str
    detail: str


def agent_command(target: str, text: str) -> list[str]:
    """The one non-interactive invocation of the agent (§FS-forge-agent-runtime-selection)."""
    matched: re.Match[str] | None = TARGET.match(target)
    if matched is None:
        raise ValueError(f"agent {target} must be pi or claude-code")
    family, mode, provider, model = matched.groups()
    if family == "claude-code":
        command: list[str] = ["claude", "--permission-mode", "bypassPermissions", "--model", model]
        return [*command, *(["--effort", mode] if mode else []), "-p", text]
    command = ["pi", "--no-session", "--model", model]
    command += ["--provider", provider] if provider else []
    return [*command, *(["--thinking", mode] if mode else []), "-p", text]


def route_label(pull: dict[str, Any]) -> str | None:
    labels: set[str] = {label["name"] for label in pull.get("labels", [])}
    return next((label for label in ROUTE_LABELS if label in labels), None)


def skip_reason(pull: dict[str, Any]) -> str | None:
    """Why the pass skips `pull`, or `None` when it looks at it.

    §FS-published-pr-ci-failures.1
    """
    labels: set[str] = {label["name"] for label in pull.get("labels", [])}
    if (pull.get("headRepository") or {}).get("nameWithOwner", REPO) != REPO:
        return "from another repository"
    if not str(pull.get("headRefName", "")).startswith("ai/"):
        return "not a Forge branch"
    if route_label(pull) is None:
        return "not a Forge route"
    if pull.get("reviewDecision") == "APPROVED":
        return "approved by a reviewer"
    if LABEL_HUMAN_INTERVENTION in labels and LABEL_HUMAN_INTERVENTION_FIXED not in labels:
        return "held for a maintainer"
    return None


def coordinate(pull: dict[str, Any]) -> str | None:
    """The coordinate whose publication descriptor the pull request carries."""
    for entry in pull.get("files", []):
        matched: re.Match[str] | None = DESCRIPTOR.match(str(entry.get("path", "")))
        if matched is not None:
            return ":".join(matched.groups())
    return None


FIELDS: str = "number,createdAt,headRefName,headRefOid,baseRefOid,headRepository,reviewDecision,labels,files"


def pull_requests(numbers: list[int] | None = None) -> list[dict[str, Any]]:
    """Open pull requests on `ai/**` branches, or the named ones in any state; oldest first."""
    pulls: list[dict[str, Any]] = (
        [gh_json("pr", "view", str(number), "--repo", REPO, "--json", FIELDS) for number in numbers] if numbers
        else gh_json("pr", "list", "--repo", REPO, "--state", "open", "--search", "head:ai/", "--limit", "200",
                     "--json", FIELDS))
    return sorted(pulls, key=lambda pull: pull["createdAt"])


def failing(repo: str, number: int) -> Failing | None:
    """The head's failing jobs, or `None` while a check runs or when every check passed.

    §FS-published-pr-ci-failures.2
    """
    with tempfile.NamedTemporaryFile(prefix="forge-ci-answer-", suffix=".json") as answer:
        completed: subprocess.CompletedProcess[str] = subprocess.run(
            [os.path.join(repo, ".ephor", "ci-failures.sh")], stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            text=True, env={**os.environ, "REPO": REPO, "NUMBER": str(number), "EPHOR_ANSWER": answer.name})
        if completed.returncode == STILL_RUNNING:
            return None
        if completed.returncode != 0:
            raise RuntimeError(f"the checks of #{number} could not be read: {completed.stderr.strip()}")
        with open(answer.name, encoding="utf-8") as handle:
            reply: dict[str, Any] = json.load(handle)
    if reply.get("pending", 0) or not reply.get("failures"):
        return None
    return Failing(reply["failures"], completed.stdout)


def ledger(number: int, login: str) -> list[tuple[str, str]]:
    """The `(head, action)` markers the Forge account left on the pull request, oldest first."""
    comments: list[dict[str, Any]] = gh_json("pr", "view", str(number), "--repo", REPO, "--json", "comments")["comments"]
    return [(matched.group(1), matched.group(2)) for comment in comments
            if (comment.get("author") or {}).get("login") == login
            for matched in MARKER.finditer(str(comment.get("body", "")))]


def git(*arguments: str, cwd: str) -> str:
    return subprocess.run(["git", *arguments], cwd=cwd, check=True, stdout=subprocess.PIPE, text=True).stdout.strip()


@contextmanager
def checkout(repo: str, pull: dict[str, Any]) -> Iterator[tuple[str, str]]:
    """A detached worktree of the head and the directory beside it, both removed afterwards."""
    # The pull ref outlives a deleted branch, so a closed pull request checks out too.
    git("fetch", "--quiet", "origin", "+refs/heads/master:refs/remotes/origin/master",
        f"+refs/pull/{pull['number']}/head:refs/remotes/origin/pull/{pull['number']}", cwd=repo)
    with tempfile.TemporaryDirectory(prefix=f"forge-ci-{pull['number']}-") as parent:
        path: str = os.path.join(parent, "checkout")
        git("worktree", "add", "--quiet", "--detach", path, pull["headRefOid"], cwd=repo)
        try:
            yield parent, path
        finally:
            subprocess.run(["git", "-C", repo, "worktree", "remove", "--force", path], check=False)


def diagnose(target: str, pull: dict[str, Any], found: Failing, pushes_left: int, parent: str, path: str,
             dry_run: bool = False) -> dict[str, Any] | None:
    """One agent turn over the evidence; its `diagnosis.json`, or `None` when it wrote none that parses."""
    evidence: str = os.path.join(parent, "evidence.md")
    with open(evidence, "w", encoding="utf-8") as handle:
        handle.write(found.evidence)
    group, artifact, _ = str(pull["coordinate"]).split(":")
    diagnosis: str = os.path.join(parent, "diagnosis.json")
    with open(TEMPLATE, encoding="utf-8") as handle:
        text: str = handle.read().format(
            number=pull["number"], repo=REPO, label=route_label(pull), coordinate=pull["coordinate"],
            group=group, artifact=artifact, head=pull["headRefOid"], branch=pull["headRefName"],
            base=pull.get("baseRefOid", ""), evidence=evidence, diagnosis=diagnosis, pushes_left=pushes_left)
    environment: dict[str, str] = {**os.environ, **(DRY_RUN_ENVIRONMENT if dry_run else {})}
    try:
        subprocess.run(agent_command(target, text), cwd=path, check=False, env=environment,
                       timeout=AGENT_TIMEOUT_SECONDS)
    except subprocess.TimeoutExpired:
        return None
    try:
        with open(diagnosis, encoding="utf-8") as handle:
            answer: Any = json.load(handle)
    except (OSError, json.JSONDecodeError):
        return None
    return answer if isinstance(answer, dict) else None


def signature(text: str) -> str:
    return re.sub(r"[^a-z0-9._:/-]+", "-", text.lower()).strip("-")[:120]


def foreign_paths(path: str, commit: str, coordinate_text: str) -> list[str]:
    """The commit's paths outside the contribution's file set, diffed against `master`.

    §FS-published-pr-ci-failures.4
    """
    group, artifact, _ = coordinate_text.split(":")
    allowed: tuple[str, ...] = tuple(f"{root}/{group}/{artifact}/" for root in ("metadata", "tests/src", "stats"))
    base: str = git("merge-base", "origin/master", commit, cwd=path)
    changed: list[str] = git("diff", "--name-only", base, commit, cwd=path).splitlines()
    return [changed_path for changed_path in changed if not changed_path.startswith(allowed)]


class Pass:
    """One CI pass: the side effects, each confirmed against GitHub, or only printed in a dry run."""

    def __init__(self, repo: str, target: str, login: str, dry_run: bool) -> None:
        self.repo: str = repo
        self.target: str = target
        self.login: str = login
        self.dry_run: bool = dry_run

    def handle(self, pull: dict[str, Any]) -> Outcome | None:
        """Decide and act for one eligible pull request; `None` when its CI needs nothing."""
        number: int = pull["number"]
        head: str = pull["headRefOid"]
        found: Failing | None = failing(self.repo, number)
        if found is None:
            return None
        marks: list[tuple[str, str]] = ledger(number, self.login)
        on_head: list[str] = [action for sha, action in marks if sha == head]
        if on_head == ["rerun"]:
            return self.escalate(pull, found, "rerun-failed-again",
                                 "The failed jobs were rerun as transient and failed again.")
        if on_head:
            return Outcome("none", f"head {head[:12]} was already handled ({', '.join(on_head)})")
        pull["coordinate"] = coordinate(pull)
        if pull["coordinate"] is None:
            return self.escalate(pull, found, "no-descriptor", "The pull request carries no publication descriptor.")
        pushes_left: int = max(0, FIX_PUSH_LIMIT - sum(1 for _, action in marks if action == "push"))
        with checkout(self.repo, pull) as (parent, path):
            answer: dict[str, Any] | None = diagnose(self.target, pull, found, pushes_left, parent, path,
                                                     self.dry_run)
            return self.act(pull, found, answer, pushes_left, path)

    def act(self, pull: dict[str, Any], found: Failing, answer: dict[str, Any] | None, pushes_left: int,
            path: str) -> Outcome:
        """Carry out the diagnosis, or escalate when it is malformed or its action is refused.

        §FS-published-pr-ci-failures.4
        """
        cause: str = str((answer or {}).get("cause", ""))
        summary: str = str((answer or {}).get("summary", "")).strip()
        action: str | None = CAUSE_ACTIONS.get(cause)
        if action is None or not summary:
            return self.escalate(pull, found, "unverified", "The diagnosis is missing or malformed.", summary)
        if action == "rerun":
            return self.rerun(pull, found, summary)
        if action == "push":
            if pushes_left == 0:
                return self.escalate(pull, found, "fix-pushes-spent",
                                     f"A {cause} fix was found, but the pull request has no fix push left.", summary)
            return self.push(pull, found, path, cause, summary)
        issue: Any = (answer or {}).get("issue")
        if not isinstance(issue, dict) or not all(str(issue.get(key, "")).strip()
                                                  for key in ("title", "body", "signature")):
            return self.escalate(pull, found, "unverified", "The diagnosis asks for an issue it does not describe.",
                                 summary)
        return self.file_issue(pull, str(issue["title"]), str(issue["body"]), signature(str(issue["signature"])),
                               summary)

    def rerun(self, pull: dict[str, Any], found: Failing, summary: str) -> Outcome:
        number: int = pull["number"]
        head: str = pull["headRefOid"]
        if self.dry_run:
            return Outcome("rerun", f"would rerun the failed jobs of {head[:12]}: {summary}")
        completed: subprocess.CompletedProcess[str] = subprocess.run(
            [os.path.join(self.repo, ".ephor", "ci-restart.sh")], stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            text=True, env={**os.environ, "REPO": REPO, "NUMBER": str(number), "EPHOR_RESTART": "failed"})
        runs: list[dict[str, Any]] = gh_json("run", "list", "--repo", REPO, "--commit", head, "--limit", "100",
                                             "--json", "databaseId,status,attempt")
        if completed.returncode != 0 or not any(run.get("status") != "completed" or int(run.get("attempt", 1)) > 1
                                                for run in runs):
            return self.escalate(pull, found, "unverified", "The rerun of the failed jobs could not be confirmed.",
                                 summary)
        self.record(pull, "rerun", f"Forge reran the failed jobs of `{head}` as transient: {summary}")
        return Outcome("rerun", f"reran the failed jobs of {head[:12]}")

    def push(self, pull: dict[str, Any], found: Failing, path: str, cause: str, summary: str) -> Outcome:
        head: str = pull["headRefOid"]
        branch: str = pull["headRefName"]
        commit: str = git("rev-parse", "HEAD", cwd=path)
        if commit == head:
            return self.escalate(pull, found, "unverified", f"The {cause} diagnosis left no commit.", summary)
        descends: bool = subprocess.run(["git", "merge-base", "--is-ancestor", head, commit], cwd=path).returncode == 0
        outside: list[str] = foreign_paths(path, commit, str(pull["coordinate"])) if descends else []
        if not descends or outside:
            reason: str = "does not descend from the head" if not descends else f"changes {', '.join(outside)}"
            return self.escalate(pull, found, "unverified", f"The {cause} fix {commit[:12]} {reason}.", summary)
        if self.dry_run:
            return Outcome("push", f"would push {commit[:12]} to {branch}: {summary}")
        subprocess.run(["git", "push", "--quiet", "origin", f"{commit}:refs/heads/{branch}",
                        f"--force-with-lease=refs/heads/{branch}:{head}"], cwd=path, check=False)
        remote: str = git("ls-remote", "origin", f"refs/heads/{branch}", cwd=path).split("\t")[0]
        if remote != commit:
            return self.escalate(pull, found, "unverified", f"The push of {commit[:12]} to {branch} did not land.",
                                 summary)
        self.record(pull, "push", f"Forge pushed `{commit}` to fix the failing CI of `{head}` ({cause}): {summary}")
        return Outcome("push", f"pushed {commit[:12]} to {branch}")

    def escalate(self, pull: dict[str, Any], found: Failing, reason: str, why: str, summary: str = "") -> Outcome:
        """An issue the program writes from the evidence when no agent action can be carried out."""
        head: str = pull["headRefOid"]
        body: str = "\n\n".join(part for part in (
            f"Forge could not resolve the failing CI of #{pull['number']} at `{head}`: {why}",
            f"Diagnosis: {summary}" if summary else "",
            found.evidence[-EVIDENCE_LIMIT:]) if part)
        return self.file_issue(pull, f"Failing CI on #{pull['number']} needs a maintainer", body,
                               signature(f"pr-{pull['number']}-{head[:12]}-{reason}"), why)

    def file_issue(self, pull: dict[str, Any], title: str, body: str, key: str, summary: str) -> Outcome:
        """Reuse an open issue with the same signature, or open one; then link it and hold the pull request."""
        number: int = pull["number"]
        head: str = pull["headRefOid"]
        existing: int | None = find_issue(key)
        if self.dry_run:
            target: str = f"reuse #{existing}" if existing is not None else f"open \"{title}\""
            return Outcome("issue", f"would {target} [{key}], link it, and label #{number} "
                                    f"{LABEL_HUMAN_INTERVENTION}: {summary}")
        issue: int = existing if existing is not None else open_issue(title, f"{body}\n\n{marker(key)}")
        if gh_json("issue", "view", str(issue), "--repo", REPO, "--json", "state").get("state") != "OPEN":
            raise RuntimeError(f"issue #{issue} for #{number} is not open")
        add_pull_request_label(number, LABEL_HUMAN_INTERVENTION)
        labels: list[dict[str, Any]] = gh_json("pr", "view", str(number), "--repo", REPO, "--json", "labels")["labels"]
        if LABEL_HUMAN_INTERVENTION not in {label["name"] for label in labels}:
            raise RuntimeError(f"#{number} does not carry {LABEL_HUMAN_INTERVENTION}")
        self.record(pull, "issue", f"Forge cannot fix the failing CI of `{head}`; it is tracked in #{issue}. {summary}")
        return Outcome("issue", f"{'reused' if existing is not None else 'opened'} #{issue}")

    def record(self, pull: dict[str, Any], action: str, text: str) -> None:
        """The ledger entry that marks the head handled (§AR-forge-dispatcher-decomposition.1)."""
        post_issue_comment(pull["number"], f"{text}\n\n<!-- forge-ci:{pull['headRefOid']}:{action} -->")


def marker(key: str) -> str:
    return f"<!-- {SIGNATURE}: {key} -->"


def find_issue(key: str) -> int | None:
    """An open issue whose body carries the signature (§FS-published-pr-ci-failures.3.4)."""
    issues: list[dict[str, Any]] = gh_json(
        "issue", "list", "--repo", REPO, "--state", "open", "--search", f"\"{SIGNATURE}: {key}\" in:body",
        "--limit", "20", "--json", "number,body")
    return next((issue["number"] for issue in issues if marker(key) in str(issue.get("body", ""))), None)


def open_issue(title: str, body: str) -> int:
    url: str = gh("issue", "create", "--repo", REPO, "--title", title, "--body", body).stdout.strip()
    return int(url.rstrip("/").rsplit("/", 1)[1])


def ci_pass(repo: str, target: str, login: str, dry_run: bool = False, numbers: list[int] | None = None) -> int:
    """Reconcile merged pull requests, then handle up to `CI_LIMIT` failing ones; return how many were handled."""
    if not dry_run:
        reconcile_auto_merged_pull_request_follow_ups()
    runner: Pass = Pass(repo, target, login, dry_run)
    handled: int = 0
    for pull in pull_requests(numbers):
        if handled == CI_LIMIT:
            break
        reason: str | None = skip_reason(pull)
        if reason is not None:
            print(f"[ci] #{pull['number']} is {reason}; skipping", file=sys.stderr)
            continue
        try:
            outcome: Outcome | None = runner.handle(pull)
        except (RuntimeError, subprocess.CalledProcessError) as error:
            # Nothing is recorded, so the next pass looks at this head again.
            print(f"[ci] #{pull['number']}: {error}", file=sys.stderr)
            continue
        if outcome is None:
            print(f"[ci] #{pull['number']}: CI is running or green; skipping", file=sys.stderr)
            continue
        print(f"[ci] #{pull['number']}: {outcome.detail}")
        handled += outcome.action != "none"
    return handled
