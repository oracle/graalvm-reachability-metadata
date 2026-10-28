# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Claim one issue, check its form, start its `forge-metadata` run, and close it out.

The launcher only finds and claims work: the live claimability check, the
optimistic claim, and the issue-form gate are the dispatcher's, unchanged
(§FS-forge-run-requirements.2, §FS-forge-run-requirements.3), and every
decision about the issue is the run's (§AR-forge-workflow-pipeline.1).
"""
from __future__ import annotations

import contextlib
import json
import os
import re
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from typing import Any, Iterator

from utility_scripts.gradle_environment import gradle_command_environment
from utility_scripts.metadata_index import extract_coordinate_parts

from dispatcher.claim_preflight import refresh_issue_payload_for_claim
from dispatcher.config import LABEL_HUMAN_INTERVENTION
from dispatcher.issue_admin import add_issue_label, post_issue_comment
from dispatcher.issue_claiming import revert_issue_claim, try_claim_issue
from dispatcher.issue_form import check_issue_form, reject_issue_form
from dispatcher.worktrees import fetch_issue_base_commit, remove_worktree

TEMPLATE: str = "forge-metadata"
OUTCOME: str = os.path.join("runtime", "forge", "outcome.json")
# <agent>[<mode>]:<model> with agent pi or claude-code (§FS-forge-agent-runtime-selection).
TARGET: re.Pattern[str] = re.compile(r"^(pi|claude-code)(\[[A-Za-z0-9_-]+\])?:.+$")
# Agent text the comment quotes is bounded, and neither cites nor mentions anyone
# (§FS-human-intervention-policy).
QUOTE_LIMIT: int = 1500
UNESCAPED_MARKER: re.Pattern[str] = re.compile(r"(?<!<)§(?!>)")
MENTION: re.Pattern[str] = re.compile(r"@(?=[A-Za-z0-9])")
BACKTICKS: re.Pattern[str] = re.compile(r"`+")


@dataclass(frozen=True)
class Claim:
    issue: dict[str, Any]
    label: str
    item_id: str


def target_error(setup_agent: str, worker_agent: str) -> str | None:
    """Why the two agent targets cannot run, or `None` (§FS-forge-run-requirements.1)."""
    for name, value in (("--setup-agent", setup_agent), ("--worker-agent", worker_agent)):
        if TARGET.match(value) is None:
            return f"{name} {value}: the agent must be pi or claude-code"
    if not setup_agent.startswith("claude-code"):
        return f"--setup-agent {setup_agent}: setup needs web search, so it must be a claude-code target"
    return None


@contextlib.contextmanager
def pinned_index(repo: str, title: str) -> Iterator[str]:
    """A directory holding the titled artifact's `index.json` as freshly fetched `master` has it."""
    base: str = fetch_issue_base_commit(repo)
    with tempfile.TemporaryDirectory(prefix="forge-form-") as root:
        parts: tuple[str, str, str] | None = extract_coordinate_parts(title)
        if parts is not None:
            relative: str = os.path.join("metadata", parts[0], parts[1], "index.json")
            shown: subprocess.CompletedProcess[str] = subprocess.run(
                ["git", "show", f"{base}:{relative}"], cwd=repo, stdout=subprocess.PIPE,
                stderr=subprocess.DEVNULL, text=True,
            )
            if shown.returncode == 0:
                os.makedirs(os.path.dirname(os.path.join(root, relative)))
                with open(os.path.join(root, relative), "w", encoding="utf-8") as handle:
                    handle.write(shown.stdout)
        yield root


def claim(issue: dict[str, Any], label: str, user: str, repo: str, take_blocked: bool = False) -> Claim | None:
    """Claim one candidate and pass its form gate, or leave GitHub as it was.

    `take_blocked` lifts only the open-blocker condition (§FS-forge-run-requirements.2).
    """
    if not refresh_issue_payload_for_claim(issue, label, user):
        return None
    item_id: str | None = try_claim_issue(issue, user, label, take_blocked)
    if item_id is None:
        return None
    try:
        with pinned_index(repo, str(issue.get("title") or "")) as base:
            verdict = check_issue_form(issue, label, base)
    except BaseException:
        revert_issue_claim(item_id, issue["number"], "issue-form check failed")
        raise
    if verdict.rejection is not None:
        if not reject_issue_form(issue, verdict.rejection):
            revert_issue_claim(item_id, issue["number"], f"issue-form rule '{verdict.rejection.rule}' could not close it")
        return None
    if not verdict.accepted:
        revert_issue_claim(item_id, issue["number"], "issue-form check was undecided")
        return None
    return Claim(issue=issue, label=label, item_id=item_id)


def attempts(forge_dir: str, issue_number: int) -> list[str]:
    """The issue's attempt workspaces, oldest first."""
    runs: str = os.path.join(forge_dir, "runs")
    pattern: re.Pattern[str] = re.compile(rf"^forge-{issue_number}-(\d+)$")
    numbered: list[tuple[int, str]] = [(int(match.group(1)), name) for name in os.listdir(runs)
                                       if (match := pattern.match(name))] if os.path.isdir(runs) else []
    return [os.path.join(runs, name) for _, name in sorted(numbered)]


def workspace_path(forge_dir: str, issue_number: int) -> str:
    """The attempt to run: the latest one while it is unfinished, else a new one.

    Rhei keys a workspace's budget ledger by its path, so a finished attempt's
    path is never reused (§AR-forge-workflow-pipeline.1).
    """
    found: list[str] = attempts(forge_dir, issue_number)
    if found and not os.path.isfile(os.path.join(found[-1], OUTCOME)):
        return found[-1]
    number: int = int(found[-1].rsplit("-", 1)[1]) + 1 if found else 1
    return os.path.join(forge_dir, "runs", f"forge-{issue_number}-{number}")


def instantiate_command(issue_number: int, setup_agent: str, worker_agent: str, workspace: str) -> list[str]:
    return ["rhei", "instantiate", TEMPLATE, f"issue_number={issue_number}", f"setup_agent={setup_agent}",
            f"worker_agent={worker_agent}", "--output", workspace]


def release_worktree(repo: str, workspace: str, keep_branch: bool = False) -> None:
    """Drop a finished attempt's worktree, and its branch unless kept; the workspace stays.

    The worktree goes with its Gradle daemons and home (§FS-forge-run-requirements.4).
    A re-claimed issue drops the branch too: it starts a fresh run from a freshly
    pinned base rather than resuming a run that reached a final state.
    """
    state_path: str = os.path.join(workspace, "runtime", "forge", "state.json")
    if os.path.isfile(state_path):
        with open(state_path, encoding="utf-8") as handle:
            state: dict[str, Any] = json.load(handle)
        if os.path.isdir(state["worktree"]):
            remove_worktree(repo, state["worktree"])
        if not keep_branch:
            subprocess.run(["git", "branch", "-D", state["branch"]], cwd=repo, check=False,
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def agent_environment(repo: str, workspace: str, issue: dict[str, Any]) -> dict[str, str] | None:
    """The `rhei run` environment, so agents build in the run worktree's Gradle home.

    Agents inherit `rhei run`'s environment, so their own `./gradlew` gets the
    home the programs use (§FS-forge-run-requirements.4). `prepare` names the
    worktree after the title's coordinate under the template's `../worktrees`.
    """
    parts: tuple[str, str, str] | None = extract_coordinate_parts(str(issue.get("title", "")))
    if parts is None:
        return None
    worktree: str = os.path.realpath(os.path.join(workspace, "..", "worktrees", "forge-" + "-".join(parts)))
    return gradle_command_environment(repo, worktree_root=worktree)


def start_run(forge_dir: str, claimed: Claim, setup_agent: str, worker_agent: str,
              rhei_args: list[str]) -> str | None:
    """Instantiate and run the issue's attempt; its workspace, or `None` when it could not be created."""
    number: int = claimed.issue["number"]
    workspace: str = workspace_path(forge_dir, number)
    if not os.path.isdir(workspace):
        for finished in attempts(forge_dir, number):
            release_worktree(os.path.dirname(forge_dir), finished)
        created = subprocess.run(instantiate_command(number, setup_agent, worker_agent, workspace), cwd=forge_dir)
        if created.returncode != 0:
            return None
    subprocess.run(["rhei", "run", workspace, "--dashboard", *rhei_args], cwd=forge_dir,
                   env=agent_environment(os.path.dirname(forge_dir), workspace, claimed.issue))
    return workspace


def record_outcome(workspace: str) -> dict[str, Any]:
    """The run's terminal outcome, recording an infrastructure failure when it never reached one.

    A halted run is finished: Rhei never re-runs a state whose attempts are spent
    (§AR-forge-workflow-pipeline.1).
    """
    path: str = os.path.join(workspace, OUTCOME)
    if not os.path.isfile(path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as handle:
            json.dump({"outcome": "failed", "kind": "infrastructure",
                       "reason": "the run stopped before a final state"}, handle)
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def finish_run(repo: str, workspace: str) -> dict[str, Any]:
    """Record the outcome of a run `rhei run` returned from, then give up its worktree.

    A finished attempt keeps only its workspace and branch as the record; one whose
    launcher died never gets here, so it is resumed in its worktree
    (§AR-forge-workflow-pipeline.1).
    """
    outcome: dict[str, Any] = record_outcome(workspace)
    release_worktree(repo, workspace, keep_branch=True)
    return outcome


def quoted(text: str) -> list[str]:
    """Agent-written `text` as quote lines, trimmed, `§` escaped as grund does, and `@` kept from mentioning."""
    bounded: str = text if len(text) <= QUOTE_LIMIT else text[:QUOTE_LIMIT].rstrip() + " …"
    safe: str = MENTION.sub("@\u200b", UNESCAPED_MARKER.sub("<§>", bounded))
    return [f"> {line}" for line in safe.splitlines()]


def fenced(text: str) -> list[str]:
    """`text` in a code fence that no backtick run inside it can close."""
    fence: str = "`" * max([3, *(len(run) + 1 for run in BACKTICKS.findall(text))])
    return [fence + "text", text, fence]


def intervention_comment(outcome: dict[str, Any]) -> str:
    """The comment a maintainer continues from: the reason and what the run recorded.

    That is the state that stopped, its failing commands with their output excerpts,
    and the last fix report (§FS-human-intervention-policy).
    """
    lines: list[str] = ["Human intervention needed", "",
                        f"Forge could not finish this issue: {outcome.get('reason', '')}."]
    if outcome.get("sender"):
        lines += ["", f"Stopped in `{outcome['sender']}`."]
    for failure in outcome.get("failures", []):
        lines += ["", f"Failing command: `{failure['command']}`"]
        lines += ["", *fenced(failure["output"])] if failure["output"] else []
    if outcome.get("fix_summary"):
        result: str = f" `{outcome['fix_result']}`" if outcome.get("fix_result") else ""
        lines += ["", f"The last fix reported{result}:", "", *quoted(outcome["fix_summary"])]
    return "\n".join(lines) + "\n"


def close_out(claimed: Claim, outcome: dict[str, Any]) -> None:
    """Apply the bookkeeping the final state implies (§AR-forge-workflow-pipeline.1)."""
    number: int = claimed.issue["number"]
    kind: str = outcome.get("outcome", "failed")
    reason: str = outcome.get("reason", "")
    if kind == "completed":
        print(f"Issue #{number}: published; the pull request closes it.")
        return
    needs_human: bool = kind in ("human-intervention", "nothing-to-publish") or outcome.get("kind") == "bad-input"
    if needs_human:
        add_issue_label(number, LABEL_HUMAN_INTERVENTION)
        post_issue_comment(number, intervention_comment(outcome))
    revert_issue_claim(claimed.item_id, number, reason or kind)
    print(f"Issue #{number}: {kind} ({reason}); claim released.", file=sys.stderr)
