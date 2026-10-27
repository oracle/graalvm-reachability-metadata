# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The launcher's review of published Forge pull requests (§FS-published-pr-review).

Merged pull requests first release their issues; then the open Forge pull
requests, oldest first, are read again one by one, and up to `REVIEW_LIMIT` of
them that no skip applies to get one review agent each, in a throwaway checkout
of the head, under the `in-review` lock. The agent acts with `gh` and `git` and
answers with its outcome; this module confirms the outcome and writes every
label, marker, and issue (§AR-forge-dispatcher-decomposition.1).
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
from datetime import datetime, timedelta, timezone
from typing import Any, Iterator

import yaml

from dispatcher import pr_review_state as state
from dispatcher.config import LABEL_HUMAN_INTERVENTION, LABEL_HUMAN_INTERVENTION_FIXED, REPO
from dispatcher.github_api import gh, gh_json
from dispatcher.issue_admin import (
    add_pull_request_label,
    ensure_repo_label_exists,
    post_issue_comment,
    remove_pull_request_label,
)
from dispatcher.pr_merge import reconcile_auto_merged_pull_request_follow_ups

REVIEW_LIMIT: int = 3
FIX_PUSH_LIMIT: int = 2
STALE_AFTER: timedelta = timedelta(hours=3)
# Shorter than the lock's staleness, so no pass takes over a review that is still running.
AGENT_TIMEOUT_SECONDS: int = 2 * 60 * 60
CLAUDE_TARGET: re.Pattern[str] = re.compile(r"^claude-code(?:\[([A-Za-z0-9_-]+)\])?:([^/]+)$")
OUTCOMES: tuple[str, ...] = ("approve", "rerun", "fix", "issue")
SIGNATURE: str = "forge-ci-signature"
EVIDENCE_LIMIT: int = 6000
FORGE_DIR: str = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEMPLATE: str = os.path.join(FORGE_DIR, "prompt_templates", "pr_review", "review.md")
STATES: str = os.path.join(FORGE_DIR, ".agent-grounds", "rhei", "templates", "forge-metadata", "states.yaml")
# A dry run's agent gets no GitHub credential and no push URL (§FS-published-pr-review.6).
NO_PUSH: str = "forge-review-dry-run:no-push/"
DRY_RUN_ENVIRONMENT: dict[str, str] = {
    "GH_TOKEN": "forge-review-dry-run", "GITHUB_TOKEN": "forge-review-dry-run",
    "GIT_TERMINAL_PROMPT": "0", "GIT_CONFIG_COUNT": "3",
    **{f"GIT_CONFIG_KEY_{index}": f"url.{NO_PUSH}.pushInsteadOf" for index in range(3)},
    "GIT_CONFIG_VALUE_0": "git@github.com:", "GIT_CONFIG_VALUE_1": "https://", "GIT_CONFIG_VALUE_2": "ssh://",
}
DRY_RUN_NOTE: str = """
This is a dry run. You have no GitHub credential and cannot push, so run no `gh`
command and no `git push`; everything GitHub has to say is in the two files above.
Do everything else — read, reproduce, search the web, fix, and commit in this
checkout — and answer with the outcome you would have taken. Reproduce locally only
where the classification depends on it.
"""
LABEL_DESCRIPTIONS: dict[str, tuple[str, str]] = {
    state.IN_REVIEW: ("fbca04", "A review agent is reviewing this pull request"),
    state.REVIEWED: ("0e8a16", "Forge's review reached a terminal outcome for this pull request"),
}


@dataclass
class Outcome:
    """What the review did, or would do in a dry run, for one pull request."""

    action: str
    detail: str
    terminal: bool = False


def review_target_error(target: str) -> str | None:
    """Why `target` cannot review, or `None` (§FS-forge-agent-runtime-selection)."""
    if CLAUDE_TARGET.match(target) is None:
        return f"--review-agent {target}: the review needs the web and the GraalVM history, so it must be a " \
               f"claude-code target"
    return None


def agent_command(target: str, text: str) -> list[str]:
    """The one non-interactive Claude Code invocation of the review agent."""
    matched: re.Match[str] | None = CLAUDE_TARGET.match(target)
    if matched is None:
        raise ValueError(review_target_error(target))
    mode, model = matched.groups()
    command: list[str] = ["claude", "--permission-mode", "bypassPermissions", "--model", model]
    return [*command, *(["--effort", mode] if mode else []), "-p", text]


def metadata_rules() -> str:
    """The Metadata and Native Image rules of the run's `fix` state, verbatim."""
    with open(STATES, encoding="utf-8") as handle:
        text: str = yaml.safe_load(handle)["states"]["fix"]["instructions"]
    start: int = text.index("\nMetadata\n")
    return text[start + 1:text.index("\nBefore your final message", start)].strip()


def eligibility(pull: dict[str, Any]) -> str | None:
    """Why `pull` is not Forge's, or `None` (§FS-published-pr-review.1)."""
    if (pull.get("headRepository") or {}).get("nameWithOwner", REPO) != REPO:
        return "from another repository"
    if not str(pull.get("headRefName", "")).startswith("ai/"):
        return "not a Forge branch"
    if state.route_label(pull) is None:
        return "not a Forge route"
    return None


def ended(pull: dict[str, Any]) -> str | None:
    """Why `pull` needs no review: not open, approved, or labeled to stay out of it."""
    names: set[str] = state.labels(pull)
    if pull.get("state", "OPEN") != "OPEN":
        return str(pull["state"]).lower()
    if pull.get("reviewDecision") == "APPROVED":
        return "approved"
    if LABEL_HUMAN_INTERVENTION in names:
        return f"labeled {LABEL_HUMAN_INTERVENTION}"
    if state.REVIEWED in names and LABEL_HUMAN_INTERVENTION_FIXED not in names:
        return f"labeled {state.REVIEWED}"
    return None


def lifecycle_skip(pull: dict[str, Any], now: datetime) -> str | None:
    """Why the review skips `pull` before any agent, or `None`, taking over a stale lock.

    §FS-published-pr-review.2
    """
    reason: str | None = ended(pull)
    if reason is not None:
        return reason
    if state.IN_REVIEW in state.labels(pull):
        added: datetime = state.label_added_at(pull["number"])
        if now - added <= STALE_AFTER:
            return f"held by another review since {added.isoformat()}"
        print(f"[review] #{pull['number']}: {state.IN_REVIEW} is stale since {added.isoformat()}; taking it over",
              file=sys.stderr)
    return None


def checks_skip(checks: list[state.Check]) -> str | None:
    """Why the head's checks keep the review waiting, or `None` once every one has finished."""
    if not checks:
        return "waiting for its first check"
    running: int = sum(1 for check in checks if check.state == "running")
    return f"waiting for {running} running checks" if running else None


def git(*arguments: str, cwd: str) -> str:
    return subprocess.run(["git", *arguments], cwd=cwd, check=True, stdout=subprocess.PIPE, text=True).stdout.strip()


@contextmanager
def checkout(repo: str, number: int, head: str) -> Iterator[tuple[str, str]]:
    """A detached worktree of `head` and the directory beside it, both removed afterwards."""
    # The pull ref outlives a deleted branch, so a closed pull request checks out too.
    git("fetch", "--quiet", "origin", "+refs/heads/master:refs/remotes/origin/master",
        f"+refs/pull/{number}/head:refs/remotes/origin/pull/{number}", cwd=repo)
    if subprocess.run(["git", "cat-file", "-e", f"{head}^{{commit}}"], cwd=repo).returncode != 0:
        git("fetch", "--quiet", "origin", head, cwd=repo)
    with tempfile.TemporaryDirectory(prefix=f"forge-review-{number}-") as parent:
        path: str = os.path.join(parent, "checkout")
        git("worktree", "add", "--quiet", "--detach", path, head, cwd=repo)
        try:
            yield parent, path
        finally:
            subprocess.run(["git", "-C", repo, "worktree", "remove", "--force", path], check=False)


def signature(text: str) -> str:
    return re.sub(r"[^a-z0-9._:/-]+", "-", text.lower()).strip("-")[:120]


def marker(key: str) -> str:
    return f"<!-- {SIGNATURE}: {key} -->"


def find_issue(key: str) -> int | None:
    """An open issue whose body carries the signature (§FS-published-pr-review.4.6)."""
    issues: list[dict[str, Any]] = gh_json(
        "issue", "list", "--repo", REPO, "--state", "open", "--search", f"\"{SIGNATURE}: {key}\" in:body",
        "--limit", "20", "--json", "number,body")
    return next((issue["number"] for issue in issues if marker(key) in str(issue.get("body", ""))), None)


def open_issue(title: str, body: str) -> int:
    url: str = gh("issue", "create", "--repo", REPO, "--title", title, "--body", body).stdout.strip()
    return int(url.rstrip("/").rsplit("/", 1)[1])


def foreign_paths(path: str, head: str, commit: str, coordinate: str) -> list[str]:
    """The paths the repair from `head` to `commit` changed that it may not change.

    Only the repair is checked: what the head already changed is the contribution under review,
    and a path the repair left as `origin/master` has it came in with a merge of `master`.

    §FS-published-pr-review.4
    """
    group, artifact, _ = coordinate.split(":")
    allowed: tuple[str, ...] = tuple(f"{root}/{group}/{artifact}/" for root in ("metadata", "tests/src", "stats"))
    changed: list[str] = git("diff", "--name-only", head, commit, cwd=path).splitlines()
    unlike_master: set[str] = set(git("diff", "--name-only", "origin/master", commit, cwd=path).splitlines())
    return [name for name in changed
            if name in unlike_master and not name.startswith(allowed)]


class Review:
    """One review pass: each side effect confirmed against GitHub, or only printed in a dry run."""

    def __init__(self, repo: str, target: str, login: str, dry_run: bool) -> None:
        self.repo: str = repo
        self.target: str = target
        self.login: str = login
        self.dry_run: bool = dry_run

    def review(self, pull: dict[str, Any], checks: list[state.Check]) -> Outcome:
        """Run the agent on one pull request's head and record its confirmed outcome."""
        number: int = pull["number"]
        head: str = pull["headRefOid"]
        marks: list[tuple[str, str]] = state.markers(number, self.login)
        reruns_left: int = 0 if (head, "rerun") in marks else 1
        pushes_left: int = max(0, FIX_PUSH_LIMIT - sum(1 for _, action in marks if action == "push"))
        found: str = state.evidence(head, checks)
        green: bool = all(check.state == "passed" for check in checks)
        pull["coordinate"] = state.coordinate(pull)
        if pull["coordinate"] is None:
            return self.escalate(pull, found, "no-descriptor", "The pull request carries no publication descriptor.")
        print(f"[review] #{number} at {head}: invoking the agent", file=sys.stderr)
        self.lock(number, True)
        with checkout(self.repo, number, head) as (parent, path):
            answer: dict[str, Any] | None = self.run_agent(pull, found, green, reruns_left, pushes_left, parent, path)
            outcome: Outcome = self.act(pull, found, answer, green, reruns_left, pushes_left, path)
        # A plain step, not a `finally`: a killed review leaves a lock that goes stale
        # (§FS-published-pr-review.3).
        self.lock(number, False)
        return outcome

    def run_agent(self, pull: dict[str, Any], found: str, green: bool, reruns_left: int, pushes_left: int,
                  parent: str, path: str) -> dict[str, Any] | None:
        """One agent turn; its answer, or `None` when it wrote none that parses."""
        files: dict[str, str] = {name: os.path.join(parent, name) for name in ("evidence.md", "pull-request.md")}
        with open(files["evidence.md"], "w", encoding="utf-8") as handle:
            handle.write(found)
        with open(files["pull-request.md"], "w", encoding="utf-8") as handle:
            handle.write(f"# {pull.get('title', '')}\n\n{pull.get('body', '')}\n")
        answer_path: str = os.path.join(parent, "review.json")
        environment: dict[str, str] = {**os.environ}
        if self.dry_run:
            environment.update(DRY_RUN_ENVIRONMENT, GH_CONFIG_DIR=os.path.join(parent, "no-gh-config"))
        try:
            subprocess.run(agent_command(self.target, self.prompt(pull, green, reruns_left, pushes_left, files,
                                                                  answer_path)),
                           cwd=path, check=False, env=environment, timeout=AGENT_TIMEOUT_SECONDS)
            with open(answer_path, encoding="utf-8") as handle:
                answer: Any = json.load(handle)
        except (subprocess.TimeoutExpired, OSError, json.JSONDecodeError):
            return None
        return answer if isinstance(answer, dict) else None

    def prompt(self, pull: dict[str, Any], green: bool, reruns_left: int, pushes_left: int, files: dict[str, str],
               answer: str) -> str:
        """The reviewer's instruction (§FS-published-pr-review.4)."""
        number: int = pull["number"]
        head: str = pull["headRefOid"]
        group, artifact, _ = str(pull["coordinate"]).split(":")
        approval: str = (
            f"Every check on the head passed. If the local review in the body is clean — the audit, the gates, and "
            f"the native lanes — approve the pull request with `gh pr review {number} --repo {REPO} --approve` and "
            f"arm auto-merge with `gh pr merge {number} --repo {REPO} --auto --squash --match-head-commit {head}` "
            f"(§FS-published-pr-review.4.1). If it is not clean, fix what it reports as in case 5."
            if green else "A check failed on this head, so this case does not apply: do not approve.")
        with open(TEMPLATE, encoding="utf-8") as handle:
            return handle.read().format(
                number=number, repo=REPO, coordinate=pull["coordinate"], label=state.route_label(pull),
                branch=pull["headRefName"], head=head, body=files["pull-request.md"],
                evidence=files["evidence.md"], group=group, artifact=artifact,
                checks="Every check on the head passed." if green else "Some checks on the head failed.",
                reruns_left=reruns_left, pushes_left=pushes_left, dry_run=DRY_RUN_NOTE if self.dry_run else "",
                approval=approval, metadata_rules=metadata_rules(), outcome=answer,
                did_suffix=", or would have done in this dry run" if self.dry_run else "")

    def act(self, pull: dict[str, Any], found: str, answer: dict[str, Any] | None, green: bool, reruns_left: int,
            pushes_left: int, path: str) -> Outcome:
        """Confirm the agent's outcome and record it, or escalate (§FS-published-pr-review.5)."""
        action: str = str((answer or {}).get("outcome", ""))
        summary: str = str((answer or {}).get("summary", "")).strip()
        classified: str = f"case {(answer or {}).get('case', '?')}, {(answer or {}).get('cause', '?')}"
        if action not in OUTCOMES or not summary:
            return self.escalate(pull, found, "unverified", "The review's answer is missing or malformed.", summary)
        if action == "approve":
            if not green:
                return self.escalate(pull, found, "approved-red", "The review approved a head whose checks failed.",
                                     summary)
            return self.approve(pull, found, classified, summary)
        if action == "rerun":
            if reruns_left == 0:
                return self.escalate(pull, found, "rerun-spent", "The head was already rerun once.", summary)
            return self.rerun(pull, found, classified, summary)
        if action == "fix":
            if pushes_left == 0:
                return self.escalate(pull, found, "fix-pushes-spent", "The pull request has no fix push left.",
                                     summary)
            return self.fix(pull, found, path, classified, summary)
        issue: Any = (answer or {}).get("issue")
        if not isinstance(issue, dict) or not all(str(issue.get(key, "")).strip()
                                                  for key in ("title", "body", "signature")):
            return self.escalate(pull, found, "unverified", "The review asks for an issue it does not describe.",
                                 summary)
        return self.file_issue(pull, str(issue["title"]), str(issue["body"]), signature(str(issue["signature"])),
                               f"{classified}: {summary}")

    def approve(self, pull: dict[str, Any], found: str, classified: str, summary: str) -> Outcome:
        number: int = pull["number"]
        head: str = pull["headRefOid"]
        if self.dry_run:
            return Outcome("approve", f"would approve and arm auto-merge for {head[:12]} ({classified}): {summary}",
                           True)
        if not state.approved_by(number, self.login, head):
            return self.escalate(pull, found, "unverified", "The approval or auto-merge could not be confirmed.",
                                 summary)
        self.label(number, state.REVIEWED, True)
        return Outcome("approve", f"approved {head[:12]} and armed auto-merge", True)

    def rerun(self, pull: dict[str, Any], found: str, classified: str, summary: str) -> Outcome:
        head: str = pull["headRefOid"]
        if self.dry_run:
            return Outcome("rerun", f"would rerun the failed jobs of {head[:12]} ({classified}): {summary}")
        if not state.rerun_started(head):
            return self.escalate(pull, found, "unverified", "The rerun of the failed jobs could not be confirmed.",
                                 summary)
        self.record(pull, "rerun", f"Forge reran the failed jobs of `{head}`: {summary}")
        return Outcome("rerun", f"reran the failed jobs of {head[:12]}")

    def fix(self, pull: dict[str, Any], found: str, path: str, classified: str, summary: str) -> Outcome:
        number: int = pull["number"]
        head: str = pull["headRefOid"]
        commit: str = git("rev-parse", "HEAD", cwd=path)
        if not self.dry_run:
            commit = state.pull_request(number)["headRefOid"]
            git("fetch", "--quiet", "origin", f"+refs/pull/{number}/head:refs/remotes/origin/pull/{number}", cwd=path)
        if commit == head:
            return self.escalate(pull, found, "unverified", "The fix left no new commit on the head.", summary)
        descends: bool = subprocess.run(["git", "merge-base", "--is-ancestor", head, commit], cwd=path).returncode == 0
        outside: list[str] = foreign_paths(path, head, commit, str(pull["coordinate"])) if descends else []
        if not descends or outside:
            reason: str = "does not descend from the head" if not descends else f"changes {', '.join(outside)}"
            return self.escalate(pull, found, "unverified", f"The fix {commit[:12]} {reason}.", summary)
        if self.dry_run:
            return Outcome("fix", f"would push {commit[:12]} to {pull['headRefName']} ({classified}): {summary}")
        self.record(pull, "push", f"Forge pushed `{commit}` to fix `{head}`: {summary}")
        return Outcome("fix", f"pushed {commit[:12]}")

    def escalate(self, pull: dict[str, Any], found: str, reason: str, why: str, summary: str = "") -> Outcome:
        """An issue the program writes from the evidence when the agent's outcome cannot stand."""
        head: str = pull["headRefOid"]
        body: str = "\n\n".join(part for part in (
            f"Forge's review could not resolve #{pull['number']} at `{head}`: {why}",
            f"Review: {summary}" if summary else "", found[-EVIDENCE_LIMIT:]) if part)
        return self.file_issue(pull, f"Forge review of #{pull['number']} needs a maintainer", body,
                               signature(f"pr-{pull['number']}-{head[:12]}-{reason}"), f"escalated: {why}")

    def file_issue(self, pull: dict[str, Any], title: str, body: str, key: str, summary: str) -> Outcome:
        """Reuse an open issue with the same signature, or open one; then link it and end the review."""
        number: int = pull["number"]
        existing: int | None = find_issue(key)
        if self.dry_run:
            target: str = f"reuse #{existing}" if existing is not None else f"open \"{title}\""
            return Outcome("issue", f"would {target} [{key}] and label #{number} {LABEL_HUMAN_INTERVENTION} and "
                                    f"{state.REVIEWED} ({summary})", True)
        issue: int = existing if existing is not None else open_issue(title, f"{body}\n\n{marker(key)}")
        if gh_json("issue", "view", str(issue), "--repo", REPO, "--json", "state").get("state") != "OPEN":
            raise RuntimeError(f"issue #{issue} for #{number} is not open")
        add_pull_request_label(number, LABEL_HUMAN_INTERVENTION)
        self.label(number, state.REVIEWED, True)
        remove_pull_request_label(number, LABEL_HUMAN_INTERVENTION_FIXED)
        self.record(pull, "issue", f"Forge cannot fix `{pull['headRefOid']}`; it is tracked in #{issue}. {summary}")
        return Outcome("issue", f"{'reused' if existing is not None else 'opened'} #{issue}", True)

    def record(self, pull: dict[str, Any], action: str, text: str) -> None:
        """The marker comment that bounds reruns and fix pushes (§FS-published-pr-review.5)."""
        post_issue_comment(pull["number"], f"{text}\n\n<!-- forge-review:{pull['headRefOid']}:{action} -->")

    def label(self, number: int, name: str, add: bool) -> None:
        if add:
            ensure_repo_label_exists(name, *LABEL_DESCRIPTIONS[name])
            add_pull_request_label(number, name)
        else:
            remove_pull_request_label(number, name)

    def lock(self, number: int, held: bool) -> None:
        """Add or remove the `in-review` lock; a dry run only prints it.

        §FS-published-pr-review.3
        """
        if self.dry_run:
            print(f"[review] #{number}: would {'add' if held else 'remove'} {state.IN_REVIEW}", file=sys.stderr)
            return
        self.label(number, state.IN_REVIEW, held)


def review_pass(repo: str, target: str, login: str, dry_run: bool = False, numbers: list[int] | None = None,
                head: str | None = None, now: datetime | None = None) -> int:
    """Reconcile merged pull requests, then review up to `REVIEW_LIMIT`; return how many.

    Named pull requests in a dry run are replays: their lifecycle skips are printed, not applied
    (§FS-published-pr-review.6).
    """
    if not dry_run:
        reconcile_auto_merged_pull_request_follow_ups()
    replay: bool = dry_run and bool(numbers)
    reviewer: Review = Review(repo, target, login, dry_run)
    reviewed: int = 0
    for number in numbers or state.open_pull_requests():
        if reviewed == REVIEW_LIMIT:
            break
        # Read again: an earlier agent may have run for an hour while another review labeled this one.
        pull: dict[str, Any] = state.pull_request(number)
        if head:
            pull["headRefOid"] = head
        reason: str | None = eligibility(pull)
        if reason is None and replay and ended(pull) is not None:
            print(f"[review] #{number} is {ended(pull)}; replaying it", file=sys.stderr)
        elif reason is None and not replay:
            reason = lifecycle_skip(pull, now or datetime.now(timezone.utc))
        checks: list[state.Check] = [] if reason else state.head_checks(pull["headRefOid"])
        reason = reason or checks_skip(checks)
        if reason is not None:
            print(f"[review] #{number} is {reason}; skipping", file=sys.stderr)
            continue
        try:
            outcome: Outcome = reviewer.review(pull, checks)
        except (RuntimeError, subprocess.CalledProcessError) as error:
            # Nothing is recorded, so the next pass reviews this head again.
            print(f"[review] #{number}: {error}", file=sys.stderr)
            continue
        print(f"[review] #{number}: {outcome.action}{' (terminal)' if outcome.terminal else ''}: {outcome.detail}")
        reviewed += 1
    return reviewed
