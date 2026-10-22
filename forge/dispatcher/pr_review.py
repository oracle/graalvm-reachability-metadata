# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The launcher's review of open Forge pull requests.

Merged pull requests first release their linked issues deterministically; then
up to three open Forge pull requests, oldest first, each get one worker-agent
turn in a throwaway checkout of their exact head, under the label's checked-in
review rules (§FS-published-pr-ci-failures). The outcome is the agent's; this module
does not check it.
"""
from __future__ import annotations

import json
import os
import re
import subprocess
import sys
import tempfile
from typing import Any

from dispatcher.config import LABEL_HUMAN_INTERVENTION, LABEL_HUMAN_INTERVENTION_FIXED, REPO
from dispatcher.pr_merge import reconcile_auto_merged_pull_request_follow_ups

REVIEW_LIMIT: int = 3
TARGET: re.Pattern[str] = re.compile(r"^(pi|claude-code)(?:\[([A-Za-z0-9_-]+)\])?:(?:(.*)/)?([^/]+)$")
# The Forge route labels whose pull requests have a checked-in review rule set.
REVIEWED_LABELS: tuple[str, ...] = (
    "library-new-request",
    "library-update-request",
    "fixes-javac-fail",
    "fixes-java-run-fail",
    "fixes-native-image-run-fail",
)


def agent_command(target: str, text: str) -> list[str]:
    """The one non-interactive invocation of the worker agent (§FS-forge-agent-runtime-selection)."""
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


def prompt(number: int, label: str, branch: str, head: str) -> str:
    """The reviewer's instruction: the label's rules decide everything else."""
    return (
        f"Review pull request #{number} in {REPO}, labeled `{label}`. Your working directory is a checkout of "
        f"its head {head} on branch `{branch}`. Follow `skills/review-{label}/SKILL.md` in this checkout "
        f"exactly, including its repair, approval, auto-merge, close, and human-intervention steps; approve "
        f"and arm auto-merge only for head {head}, and only once every check on it has passed."
    )


def gh_json(arguments: list[str]) -> Any:
    completed: subprocess.CompletedProcess[str] = subprocess.run(
        ["gh", *arguments], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    if completed.returncode != 0:
        raise RuntimeError(f"gh {' '.join(arguments[:2])} failed: {completed.stderr.strip()}")
    return json.loads(completed.stdout)


def route_label(pull: dict[str, Any]) -> str | None:
    labels: set[str] = {label["name"] for label in pull.get("labels", [])}
    return next((label for label in REVIEWED_LABELS if label in labels), None)


def skip_reason(pull: dict[str, Any]) -> str | None:
    """Why the review skips `pull`, or `None` when it reviews it (§FS-published-pr-ci-failures)."""
    labels: set[str] = {label["name"] for label in pull.get("labels", [])}
    if (pull.get("headRepository") or {}).get("nameWithOwner", REPO) != REPO:
        return "from another repository"
    if not str(pull.get("headRefName", "")).startswith("ai/"):
        return "not a Forge branch"
    if route_label(pull) is None:
        return "not a reviewed Forge route"
    if pull.get("reviewDecision") == "APPROVED":
        return "approved"
    if LABEL_HUMAN_INTERVENTION in labels and LABEL_HUMAN_INTERVENTION_FIXED not in labels:
        return "held for a maintainer"
    return None


def open_pull_requests() -> list[dict[str, Any]]:
    """Open pull requests on `ai/**` branches, oldest first."""
    pulls: list[dict[str, Any]] = gh_json(
        ["pr", "list", "--repo", REPO, "--state", "open", "--search", "head:ai/", "--limit", "200",
         "--json", "number,createdAt,headRefName,headRefOid,headRepository,reviewDecision,labels"])
    return sorted(pulls, key=lambda pull: pull["createdAt"])


def run_agent(repo: str, target: str, pull: dict[str, Any], label: str) -> None:
    """One agent turn in a detached worktree of the head, removed afterwards."""
    number: int = pull["number"]
    subprocess.run(["git", "-C", repo, "fetch", "--quiet", "origin", pull["headRefName"]], check=True)
    with tempfile.TemporaryDirectory(prefix=f"forge-review-{number}-") as parent:
        checkout: str = os.path.join(parent, "checkout")
        subprocess.run(["git", "-C", repo, "worktree", "add", "--quiet", "--detach", checkout, pull["headRefOid"]],
                       check=True)
        try:
            text: str = prompt(number, label, pull["headRefName"], pull["headRefOid"])
            subprocess.run(agent_command(target, text), cwd=checkout, check=False)
        finally:
            subprocess.run(["git", "-C", repo, "worktree", "remove", "--force", checkout], check=False)


def review(repo: str, target: str) -> int:
    """Reconcile merged pull requests, then review up to `REVIEW_LIMIT`; return how many got an agent."""
    reconcile_auto_merged_pull_request_follow_ups()
    reviewed: int = 0
    for pull in open_pull_requests():
        if reviewed == REVIEW_LIMIT:
            break
        reason: str | None = skip_reason(pull)
        if reason is not None:
            print(f"[review] #{pull['number']} is {reason}; skipping", file=sys.stderr)
            continue
        label: str = route_label(pull) or ""
        print(f"[review] #{pull['number']} ({label}) at {pull['headRefOid']}: invoking the agent", file=sys.stderr)
        run_agent(repo, target, pull, label)
        reviewed += 1
    return reviewed
