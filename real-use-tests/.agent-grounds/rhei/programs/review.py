# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Review the open real-use pull requests, oldest first, up to three of them.

§FS-real-use-test-generation.4: the queue is read from GitHub, an approved pull
request or one another review holds with a fresh `in-review` label is skipped,
and every other one gets a single agent invocation in a throwaway checkout of
its head (§AR-real-use-test-launcher). The outcome is the agent's; this program
does not check it.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import tempfile
from datetime import datetime, timedelta, timezone
from typing import Any

import issue

TARGET: re.Pattern[str] = re.compile(r"^(pi|claude-code)(?:\[([A-Za-z0-9_-]+)\])?:(?:(.*)/)?([^/]+)$")
IN_REVIEW: str = "in-review"
STALE_AFTER: timedelta = timedelta(hours=3)
REVIEW_LIMIT: int = 3


def prompt(number: int, branch: str, head: str) -> str:
    """The reviewer's three sentences (§FS-real-use-test-generation.4)."""
    return (
        f"Pull request #{number} in {issue.REPOSITORY} adds real-use tests, and your working directory is a "
        f"checkout of its head {head}. "
        f"Without waiting for running checks, look for a real-use test that can fail intermittently, from timing "
        f"or garbage collection, by reading the tests and any check that has already failed, and if you find one, "
        f"make the smallest fix that keeps every assertion, push it to `{branch}`, approve the pull request with "
        f"`gh pr review {number} --approve`, and enable auto-merge with "
        f"`gh pr merge {number} --auto --squash --match-head-commit <the new head you pushed>`. "
        f"If you find none and no check has failed, approve the pull request with "
        f"`gh pr review {number} --approve` and enable auto-merge with "
        f"`gh pr merge {number} --auto --squash --match-head-commit {head}`."
    )


def agent_command(target: str, text: str) -> list[str]:
    """The one non-interactive invocation of the launcher's worker agent."""
    matched: re.Match[str] | None = TARGET.match(target)
    if matched is None:
        raise ValueError(f"worker agent {target} must be pi or claude-code")
    family, mode, provider, model = matched.groups()
    if family == "claude-code":
        command: list[str] = ["claude", "--permission-mode", "bypassPermissions", "--model", model]
        return [*command, *(["--effort", mode] if mode else []), "-p", text]
    command = ["pi", "--no-session", "--model", model]
    command += ["--provider", provider] if provider else []
    return [*command, *(["--thinking", mode] if mode else []), "-p", text]


def gh(arguments: list[str], action: str) -> str:
    """Run `gh` and return its stdout, failing with `action` in the message."""
    completed: subprocess.CompletedProcess[str] = subprocess.run(
        ["gh", *arguments], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    if completed.returncode != 0:
        raise RuntimeError(f"cannot {action}: {completed.stderr.strip()}")
    return completed.stdout


def parse_time(text: str) -> datetime:
    """A GitHub timestamp such as `2026-10-17T17:32:49Z`."""
    return datetime.fromisoformat(text.replace("Z", "+00:00"))


def open_pull_requests() -> list[dict[str, Any]]:
    """The open `real-use-test` pull requests, oldest first (§FS-real-use-test-generation.4)."""
    found: list[dict[str, Any]] = json.loads(gh(
        ["pr", "list", "--repo", issue.REPOSITORY, "--label", issue.LABEL, "--state", "open", "--limit", "1000",
         "--json", "number,headRefName,headRefOid,reviewDecision,labels,createdAt"],
        "list the open real-use pull requests"))
    return sorted(found, key=lambda pull: parse_time(pull["createdAt"]))


def label_added_at(number: int) -> datetime:
    """When `in-review` was last added to pull request `number`, from its issue timeline."""
    lines: list[str] = gh(
        ["api", "--paginate", f"repos/{issue.REPOSITORY}/issues/{number}/timeline",
         "--jq", f'.[] | select(.event == "labeled" and .label.name == "{IN_REVIEW}") | .created_at'],
        f"read the timeline of #{number}").split()
    if not lines:
        raise RuntimeError(f"#{number} carries {IN_REVIEW} but its timeline has no labeled event for it")
    return max(parse_time(line) for line in lines)


def skip_reason(pull: dict[str, Any], now: datetime) -> str | None:
    """Why the review skips `pull`, or `None` when it reviews it, taking over a stale label."""
    if pull.get("reviewDecision") == "APPROVED":
        return "approved"
    if IN_REVIEW in {label["name"] for label in pull["labels"]}:
        added: datetime = label_added_at(pull["number"])
        if now - added <= STALE_AFTER:
            return f"held by another review since {added.isoformat()}"
        print(f"[review] #{pull['number']}: {IN_REVIEW} is stale since {added.isoformat()}; taking it over",
              file=sys.stderr)
    return None


def set_in_review(number: int, held: bool) -> None:
    """Add or remove the `in-review` label that keeps concurrent launchers off `number`."""
    flag: str = "--add-label" if held else "--remove-label"
    gh(["pr", "edit", str(number), "--repo", issue.REPOSITORY, flag, IN_REVIEW], f"label #{number}")


def run_agent(repo: str, target: str, number: int, branch: str, head: str) -> None:
    """One agent invocation in a detached worktree of the head, removed afterwards."""
    subprocess.run(["git", "-C", repo, "fetch", "--quiet", "origin", branch], check=True)
    with tempfile.TemporaryDirectory(prefix=f"real-use-review-{number}-") as parent:
        checkout: str = os.path.join(parent, "checkout")
        subprocess.run(["git", "-C", repo, "worktree", "add", "--quiet", "--detach", checkout, head], check=True)
        try:
            subprocess.run(agent_command(target, prompt(number, branch, head)), cwd=checkout, check=False)
        finally:
            subprocess.run(["git", "-C", repo, "worktree", "remove", "--force", checkout], check=False)


def review(repo: str, target: str, now: datetime | None = None) -> int:
    """Review up to `REVIEW_LIMIT` pull requests of the queue; return how many got an agent."""
    started: datetime = now or datetime.now(timezone.utc)
    reviewed: int = 0
    for pull in open_pull_requests():
        if reviewed == REVIEW_LIMIT:
            break
        number: int = pull["number"]
        reason: str | None = skip_reason(pull, started)
        if reason is not None:
            print(f"[review] #{number} is {reason}; skipping", file=sys.stderr)
            continue
        print(f"[review] #{number} at {pull['headRefOid']}: invoking the agent", file=sys.stderr)
        set_in_review(number, True)
        run_agent(repo, target, number, pull["headRefName"], pull["headRefOid"])
        # A plain step, not a `finally`: a killed review leaves a label that goes stale.
        set_in_review(number, False)
        reviewed += 1
    return reviewed


def main(argv: list[str] | None = None) -> int:
    parser: argparse.ArgumentParser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--repo", required=True, help="The repository checkout the worktrees come from")
    parser.add_argument("--worker-agent", required=True, help="The launcher's Rhei target")
    args: argparse.Namespace = parser.parse_args(argv)
    print(f"[review] reviewed {review(args.repo, args.worker_agent)} pull requests", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
