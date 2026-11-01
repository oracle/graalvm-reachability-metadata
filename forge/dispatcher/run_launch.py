# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Claim one issue, check its form, start its `forge-metadata` run, and close it out.

The launcher only finds and claims work: the live claimability check, the
optimistic claim, and the issue-form gate are the dispatcher's, unchanged
(§FS-forge-run-requirements.2, §FS-forge-run-requirements.3), and every
decision about the issue is the run's (§AR-forge-launcher.1).
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
from datetime import datetime, timezone
from typing import Any, Iterator

from git_scripts.common_git import stage_and_commit
from utility_scripts.gradle_environment import gradle_command_environment
from utility_scripts.metadata_index import extract_coordinate_parts

from dispatcher.claim_preflight import refresh_issue_payload_for_claim
from dispatcher.config import LABEL_HUMAN_INTERVENTION
from dispatcher.github_api import format_github_exception_details
from dispatcher.issue_admin import add_issue_label, post_issue_comment
from dispatcher.issue_claiming import release_closed_issue_claim, revert_issue_claim, try_claim_issue
from dispatcher.issue_form import check_issue_form, reject_issue_form
from dispatcher.rhei_workspace import (EVENTS, NO_ADVANCE, NO_ADVANCE_LIMIT, budget_halted, count_no_advance,
                                       current_state, file_stamp, halt_message, run_events,
                                       set_aside_interrupted_logs, spawn_records)
from dispatcher.worktrees import fetch_issue_base_commit, remove_worktree

TEMPLATE: str = "forge-metadata"
OUTCOME: str = os.path.join("runtime", "forge", "outcome.json")
# When the attempt made its claim, told on resume from a later one (§FS-forge-stranded-claims.1).
CLAIM_RECORD: str = os.path.join("runtime", "forge", "claim.json")
# <agent>[<mode>]:<model> with agent pi or claude-code (§FS-forge-agent-runtime-selection).
TARGET: re.Pattern[str] = re.compile(r"^(pi|claude-code)(\[[A-Za-z0-9_-]+\])?:.+$")
# Agent text the comment quotes is bounded, and neither cites nor mentions anyone
# (§FS-human-intervention-policy).
QUOTE_LIMIT: int = 1500
UNESCAPED_MARKER: re.Pattern[str] = re.compile(r"(?<!<)§(?!>)")
MENTION: re.Pattern[str] = re.compile(r"@(?=[A-Za-z0-9])")
BACKTICKS: re.Pattern[str] = re.compile(r"`+")
# Where a held attempt keeps its work, one ref per attempt (§FS-human-intervention-policy).
HELD_REFS: str = "refs/forge-held"
# The close-out kinds of a halt that a later claim resumes: the issue only returns to `Todo`.
NO_ADVANCE_KIND: str = "no-advance"
BUDGET_HALT_KIND: str = "budget-halt"


@dataclass(frozen=True)
class Claim:
    issue: dict[str, Any]
    label: str
    item_id: str
    # When the claim was made, taken once it is confirmed, so GitHub never shows its assignment later.
    claimed_at: datetime


@dataclass(frozen=True)
class Started:
    """An attempt `rhei run` returned from."""

    workspace: str
    # Whether Rhei ran a state of it; a run that ran none changed nothing.
    advanced: bool
    # What Rhei logged about a run that ran no state.
    halt: str = ""
    # Whether a Rhei budget refused that run, a transient host condition.
    budget: bool = False


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
    claimed_at: datetime = datetime.now(timezone.utc)
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
    return Claim(issue=issue, label=label, item_id=item_id, claimed_at=claimed_at)


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
    path is never reused (§AR-forge-launcher.1).
    """
    found: list[str] = attempts(forge_dir, issue_number)
    if found and not os.path.isfile(os.path.join(found[-1], OUTCOME)):
        return found[-1]
    number: int = int(found[-1].rsplit("-", 1)[1]) + 1 if found else 1
    return os.path.join(forge_dir, "runs", f"forge-{issue_number}-{number}")


def record_claim(workspace: str, claimed_at: datetime) -> None:
    """Record when the attempt's claim was made; a re-claim that resumes the attempt replaces it."""
    path: str = os.path.join(workspace, CLAIM_RECORD)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    temporary: str = path + ".tmp"
    with open(temporary, "w", encoding="utf-8") as handle:
        json.dump({"claimed_at": claimed_at.isoformat()}, handle)
    os.replace(temporary, path)


def recorded_claim(workspace: str) -> datetime | None:
    """When the attempt's claim was made, or `None` when it records no readable time."""
    try:
        with open(os.path.join(workspace, CLAIM_RECORD), encoding="utf-8") as handle:
            return datetime.fromisoformat(json.load(handle)["claimed_at"])
    except (OSError, ValueError, KeyError, TypeError):
        return None


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


def held(outcome: dict[str, Any]) -> bool:
    """Whether the close-out labels the issue `human-intervention` (§FS-human-intervention-policy)."""
    return outcome.get("outcome") in ("human-intervention", "nothing-to-publish") or outcome.get("kind") == "bad-input"


def git(cwd: str, *args: str) -> str:
    """Run git and return its stripped stdout; a failure raises with git's message on it."""
    return subprocess.run(["git", *args], cwd=cwd, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                          text=True).stdout.strip()


def keep_held_work(repo: str, workspace: str, outcome: dict[str, Any]) -> str | None:
    """Commit what a held run left uncommitted on its branch, and keep that commit at the attempt's own ref.

    No later run moves or deletes the ref, so a rerun of the issue cannot discard
    the checkpoint (§FS-human-intervention-policy). Checkouts share one ref namespace,
    so a ref an attempt of the same name in another checkout keeps is never moved
    either. `None` when the attempt has no run state, or when git fails; the
    worktree then stays.
    """
    state_path: str = os.path.join(workspace, "runtime", "forge", "state.json")
    if not os.path.isfile(state_path):
        return None
    with open(state_path, encoding="utf-8") as handle:
        state: dict[str, Any] = json.load(handle)
    worktree: str = state["worktree"]
    stopped: str = outcome.get("sender") or outcome.get("step") or "an unrecorded state"
    ref: str = f"{HELD_REFS}/{os.path.basename(workspace)}"
    try:
        if os.path.isdir(worktree):
            stage_and_commit(["."], f"Held at {stopped}: uncommitted work", cwd=worktree)
            tip: str = git(worktree, "rev-parse", "HEAD")
        else:
            tip = git(repo, "rev-parse", "--verify", f"refs/heads/{state['branch']}")
        git(repo, "update-ref", ref, tip, "")  # The empty old value creates the ref only if it does not exist.
    except subprocess.CalledProcessError as error:
        print(f"ERROR: the work of held run {os.path.basename(workspace)} was not kept at {ref}, so its "
              f"worktree {worktree} stays: {(error.stderr or '').strip() or error}", file=sys.stderr)
        return None
    return ref


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
              rhei_args: list[str]) -> Started | None:
    """Instantiate or resume the issue's attempt and run it; `None` when its workspace could not be created.

    The attempt records its claim's time before Rhei runs it, so a launcher that
    dies in the run leaves a claim a resume can tell is its own (§FS-forge-stranded-claims.1).
    """
    number: int = claimed.issue["number"]
    workspace: str = workspace_path(forge_dir, number)
    if not os.path.isdir(workspace):
        for finished in attempts(forge_dir, number):
            release_worktree(os.path.dirname(forge_dir), finished)
        created = subprocess.run(instantiate_command(number, setup_agent, worker_agent, workspace), cwd=forge_dir)
        if created.returncode != 0:
            return None
    else:
        for moved in set_aside_interrupted_logs(workspace):
            print(f"[resume] moved the log of the interrupted state to {moved}", file=sys.stderr)
    record_claim(workspace, claimed.claimed_at)
    before: dict[str, int] = spawn_records(workspace)
    events: tuple[int, int, int] | None = file_stamp(os.path.join(workspace, EVENTS))
    subprocess.run(["rhei", "run", workspace, "--dashboard", *rhei_args], cwd=forge_dir,
                   env=agent_environment(os.path.dirname(forge_dir), workspace, claimed.issue))
    if spawn_records(workspace) != before:
        return Started(workspace=workspace, advanced=True)
    # An event log this run did not rewrite is an earlier run's.
    rewritten: bool = file_stamp(os.path.join(workspace, EVENTS)) != events
    logged: list[dict[str, Any]] = run_events(workspace) if rewritten else []
    return Started(workspace=workspace, advanced=False, halt=halt_message(logged, QUOTE_LIMIT),
                   budget=budget_halted(logged))


def hold_unadvanced(workspace: str, halt: str) -> None:
    """Record the `human-intervention` outcome of an attempt Rhei ran no state of twice in a row."""
    outcome: dict[str, Any] = {
        "outcome": "human-intervention",
        "reason": f"Rhei ran no state of {os.path.basename(workspace)} in {NO_ADVANCE_LIMIT} runs in a row",
        "halt": halt or "Rhei logged no message for this run.",
    }
    state: str | None = current_state(workspace)
    if state is not None:
        outcome["sender"] = state
    with open(os.path.join(workspace, OUTCOME), "w", encoding="utf-8") as handle:
        json.dump(outcome, handle)


def record_outcome(workspace: str) -> dict[str, Any]:
    """The run's terminal outcome, recording an infrastructure failure when it never reached one.

    A halted run is finished: Rhei never re-runs a state whose attempts are spent
    (§AR-forge-launcher.1).
    """
    path: str = os.path.join(workspace, OUTCOME)
    if not os.path.isfile(path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as handle:
            json.dump({"outcome": "failed", "kind": "infrastructure",
                       "reason": "the run stopped before a final state"}, handle)
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def finish_run(repo: str, started: Started) -> dict[str, Any]:
    """Record the outcome of a run, then give up its worktree.

    A finished attempt keeps only its workspace and branch as the record, and a held
    one first keeps its work at its own ref (§FS-human-intervention-policy). One
    whose launcher died never gets here, and neither a budget halt nor the first
    no-advance halt finished anything: they keep their worktree, so a later claim
    resumes them. The second no-advance halt in a row holds the attempt; a budget
    halt leaves the count as it was (§AR-forge-launcher.1).
    """
    count: str = os.path.join(started.workspace, NO_ADVANCE)
    attempt: str = os.path.basename(started.workspace)
    if started.advanced:
        with contextlib.suppress(FileNotFoundError):
            os.remove(count)
    elif started.budget:
        print(f"[budget] {started.halt}", file=sys.stderr)
        return {"outcome": "failed", "kind": BUDGET_HALT_KIND,
                "reason": f"a Rhei budget refused the run of {attempt}, which a later claim resumes"}
    elif count_no_advance(started.workspace) < NO_ADVANCE_LIMIT:
        return {"outcome": "failed", "kind": NO_ADVANCE_KIND,
                "reason": f"rhei run ran no state of {attempt}, which a later claim resumes"}
    else:
        hold_unadvanced(started.workspace, started.halt)
    outcome: dict[str, Any] = record_outcome(started.workspace)
    if held(outcome):
        ref: str | None = keep_held_work(repo, started.workspace, outcome)
        if ref is None:
            return outcome
        outcome = {**outcome, "held_ref": ref}
    release_worktree(repo, started.workspace, keep_branch=True)
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

    That is a bad input's detail, the state that stopped, its failing commands with
    their output excerpts, the last fix report, and the ref that keeps the run's
    work (§FS-human-intervention-policy).
    """
    lines: list[str] = ["Human intervention needed", "",
                        f"Forge could not finish this issue: {outcome.get('reason', '')}."]
    if outcome.get("detail"):
        # The output a bad input was read from (§FS-forge-run-requirements.2).
        lines += ["", *fenced(outcome["detail"])]
    if outcome.get("sender"):
        lines += ["", f"Stopped in `{outcome['sender']}`."]
    for failure in outcome.get("failures", []):
        lines += ["", f"Failing command: `{failure['command']}`"]
        lines += ["", *fenced(failure["output"])] if failure["output"] else []
    if outcome.get("fix_summary"):
        result: str = f" `{outcome['fix_result']}`" if outcome.get("fix_result") else ""
        lines += ["", f"The last fix reported{result}:", "", *quoted(outcome["fix_summary"])]
    if outcome.get("halt"):
        lines += ["", "Rhei's halt message:", "", *fenced(outcome["halt"])]
    if outcome.get("held_ref"):
        ref: str = outcome["held_ref"]
        lines += ["", f"The run's work, uncommitted edits included, is kept on the Forge host at `{ref}`. "
                      f"Continue from it on a branch of your own: `git worktree add -b <branch> <path> {ref}`."]
    return "\n".join(lines) + "\n"


def hand_to_maintainer(number: int, outcome: dict[str, Any]) -> bool:
    """Label the issue `human-intervention` and post its comment; return whether the label landed."""
    try:
        add_issue_label(number, LABEL_HUMAN_INTERVENTION)
    except Exception as exc:
        print(f"ERROR: Issue #{number} could not be labeled {LABEL_HUMAN_INTERVENTION}: "
              f"{format_github_exception_details(exc)}", file=sys.stderr)
        return False
    try:
        post_issue_comment(number, intervention_comment(outcome))
    except Exception as exc:
        print(f"ERROR: Issue #{number} intervention comment could not be posted: "
              f"{format_github_exception_details(exc)}", file=sys.stderr)
    return True


def close_out(claimed: Claim, outcome: dict[str, Any]) -> None:
    """Apply the bookkeeping the final state implies; GitHub refusing it does not stop the launcher.

    §AR-forge-launcher.1
    """
    number: int = claimed.issue["number"]
    kind: str = outcome.get("outcome", "failed")
    reason: str = outcome.get("reason", "")
    if kind == "completed":
        print(f"Issue #{number}: published; the pull request closes it.")
        return
    if outcome.get("kind") == "issue-closed":
        cleared: bool = release_closed_issue_claim(number, reason)
        print(f"Issue #{number}: closed after the claim; dropped"
              f"{'' if cleared else ', its Forge assignee not cleared'}.", file=sys.stderr)
        return
    needs_human: bool = kind in ("human-intervention", "nothing-to-publish") or outcome.get("kind") == "bad-input"
    if needs_human and not hand_to_maintainer(number, outcome):
        # Released unlabeled, the issue would be claimed again and fail the same way.
        print(f"ERROR: Issue #{number}: {kind} ({reason}); it could not be labeled {LABEL_HUMAN_INTERVENTION}; "
              "the claim is kept to hold it out of the queue.", file=sys.stderr)
        return
    released: bool = revert_issue_claim(claimed.item_id, number, reason or kind)
    print(f"Issue #{number}: {kind} ({reason}); claim {'released' if released else 'release unconfirmed'}.",
          file=sys.stderr)
