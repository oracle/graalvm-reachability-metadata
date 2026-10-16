# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Review the pull requests the launcher's last runs published.

§FS-real-use-test-generation.4: for each pushed branch, skip a pull request
already approved, merged, or closed, and give every other one a single agent
invocation in a throwaway checkout of its head (§AR-real-use-test-launcher).
Each branch whose pull request is not open yet, or whose head the agent again,
is printed on stdout, so the launcher reviews it again in the next batch.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import tempfile
from typing import Any

import issue

TARGET: re.Pattern[str] = re.compile(r"^(pi|claude-code)(?:\[([A-Za-z0-9_-]+)\])?:(?:(.*)/)?([^/]+)$")


def prompt(number: int, branch: str, head: str) -> str:
    """The reviewer's three sentences (§FS-real-use-test-generation.4)."""
    return (
        f"Pull request #{number} in {issue.REPOSITORY} adds real-use tests, and your working directory is a "
        f"checkout of its head {head}. "
        f"Without waiting for running checks, look for a real-use test that can fail intermittently, from timing "
        f"or garbage collection, by reading the tests and any check that has already failed, and if you find one, "
        f"make the smallest fix that keeps every assertion and push it to `{branch}`. "
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


def pull_request(branch: str) -> dict[str, Any] | None:
    """The pull request whose head is `branch`, read with `gh`, or `None` before it opens."""
    completed: subprocess.CompletedProcess[str] = subprocess.run(
        ["gh", "pr", "list", "--repo", issue.REPOSITORY, "--head", branch, "--state", "all",
         "--json", "number,state,reviewDecision,headRefOid"],
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
    )
    if completed.returncode != 0:
        raise RuntimeError(f"cannot list pull requests for {branch}: {completed.stderr.strip()}")
    found: list[dict[str, Any]] = json.loads(completed.stdout)
    return found[0] if found else None


def needs_review(found: dict[str, Any]) -> bool:
    return found["state"] == "OPEN" and found.get("reviewDecision") != "APPROVED"


def run_agent(repo: str, target: str, number: int, branch: str, head: str) -> None:
    """One agent invocation in a detached worktree of the head, removed afterwards."""
    subprocess.run(["git", "-C", repo, "fetch", "--quiet", "origin", branch], check=True)
    with tempfile.TemporaryDirectory(prefix=f"real-use-review-{number}-") as parent:
        checkout: str = os.path.join(parent, "checkout")
        subprocess.run(["git", "-C", repo, "worktree", "add", "--quiet", "--detach", checkout, head], check=True)
        try:
            # stdout lists the branches to retry, so the agent's narration goes to stderr.
            subprocess.run(agent_command(target, prompt(number, branch, head)), cwd=checkout, check=False,
                           stdout=sys.stderr)
        finally:
            subprocess.run(["git", "-C", repo, "worktree", "remove", "--force", checkout], check=False)


def review(repo: str, target: str, branches: list[str]) -> list[str]:
    """Review each branch's pull request; return the branches the next batch must see again."""
    again: list[str] = []
    for branch in branches:
        found: dict[str, Any] | None = pull_request(branch)
        if found is None:
            print(f"[review] no pull request opened for {branch} yet; keeping it", file=sys.stderr)
            again.append(branch)
            continue
        number: int = found["number"]
        if not needs_review(found):
            print(f"[review] #{number} is {found['state'].lower()}, {found.get('reviewDecision')}; skipping",
                  file=sys.stderr)
            continue
        print(f"[review] #{number} at {found['headRefOid']}: invoking the agent", file=sys.stderr)
        run_agent(repo, target, number, branch, found["headRefOid"])
        after: dict[str, Any] | None = pull_request(branch)
        if after is not None and after["state"] == "OPEN" and after["headRefOid"] != found["headRefOid"]:
            again.append(branch)
    return again


def main(argv: list[str] | None = None) -> int:
    parser: argparse.ArgumentParser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--repo", required=True, help="The repository checkout the worktrees come from")
    parser.add_argument("--worker-agent", required=True, help="The launcher's Rhei target")
    parser.add_argument("branches", nargs="*", help="Pushed branches to review")
    args: argparse.Namespace = parser.parse_args(argv)
    for branch in review(args.repo, args.worker_agent, args.branches):
        print(branch)
    return 0


if __name__ == "__main__":
    sys.exit(main())
