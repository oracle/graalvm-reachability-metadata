# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Find, claim, and start one Forge issue run, or review the open Forge pull requests.

One cycle fetches the queues with `gh issue list`, claims the first eligible
issue whose form is valid, runs its `forge-metadata` Rhei workspace, and closes
it out; every decision about the issue is the run's
(§AR-forge-launcher.1, §FS-forge-issue-resolution-goal). A claim this host's
dead launcher left is resumed first (§FS-forge-stranded-claims), after the
releases an outage left pending are retried (§FS-forge-stranded-claims.4). `--review`
reviews the published pull requests instead, and `--review --dry-run` writes
nothing on GitHub (§FS-published-pr-review).

Usage:
  python3 forge_metadata.py [--label LABEL]... [--setup-agent TARGET] [--worker-agent TARGET]
      [--issues-fixture FILE] [--dry-run] [-- RHEI_RUN_OPTIONS...]
  python3 forge_metadata.py --review [--review-agent TARGET] [--pr NUMBER]... [--head SHA]
      [--dry-run]
"""
from __future__ import annotations

import argparse
import os
import shlex
import sys
from typing import Any

from dispatcher.github_api import format_github_exception_details, resolve_authenticated_user
from dispatcher.issue_fetch import QUEUE_LABELS, fetch_candidates, fetch_commands
from dispatcher.pr_review import review_pass, review_target_error
from dispatcher.run_launch import (BUDGET_HALT_KIND, NO_ADVANCE_KIND, Claim, Started, claim, close_out, finish_run,
                                   start_run, target_error)
from dispatcher.stranded_claims import issue_lock, release_pending, stranded_claim
from git_scripts.github_cli import is_github_outage
from utility_scripts.host_requirements import TRANSIENT_FAILURE_EXIT_STATUS

FORGE_DIR: str = os.path.dirname(os.path.abspath(__file__))
REPO_DIR: str = os.path.dirname(FORGE_DIR)
DEFAULT_SETUP_AGENT: str = "claude-code[medium]:sonnet"
DEFAULT_WORKER_AGENT: str = "pi[high]:openai-codex/gpt-5.6-luna"
DEFAULT_REVIEW_AGENT: str = "claude-code[high]:opus"
# Exit codes the loop reads: an issue ran, or nothing could be claimed. An uncaught exception
# exits 1, so nothing claimed has a status no crash produces (§AR-forge-launcher.2).
RAN: int = 0
NOTHING_CLAIMED: int = 4
BAD_ARGUMENTS: int = 2
# The claimed issue's run halted without advancing for the first time: a failed cycle, so
# the loop backs off before a later claim resumes the run (§AR-forge-launcher.1).
RAN_NO_STATE: int = 3
# A Rhei budget refused the claimed issue's run: a transient host condition, so the loop
# backs off as after a transient host check (§AR-forge-launcher.1).
BUDGET_HALTED: int = TRANSIENT_FAILURE_EXIT_STATUS
# GitHub refused a claim or a pending release transiently: the loop backs off rather than claim
# into the same outage (§FS-forge-stranded-claims.4.3, §FS-forge-stranded-claims.4.4).
GITHUB_OUTAGE: int = TRANSIENT_FAILURE_EXIT_STATUS


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser: argparse.ArgumentParser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--label", action="append", choices=QUEUE_LABELS,
                        help="Queue to fetch; repeat for several. Default: every queue, in claim order.")
    parser.add_argument("--setup-agent", default=DEFAULT_SETUP_AGENT,
                        help=f"Rhei target of the setup state, a claude-code target. Default: {DEFAULT_SETUP_AGENT}")
    parser.add_argument("--worker-agent", default=DEFAULT_WORKER_AGENT,
                        help=f"Rhei target of the cover and fix states. Default: {DEFAULT_WORKER_AGENT}")
    parser.add_argument("--review-agent", default=DEFAULT_REVIEW_AGENT,
                        help=f"Target of the pull-request review, a claude-code target. "
                             f"Default: {DEFAULT_REVIEW_AGENT}")
    parser.add_argument("--take-blocked-issues", action="store_true",
                        help="Fetch and claim issues with open blockers; every other claim condition still "
                             "holds (§FS-forge-run-requirements.2).")
    parser.add_argument("--issues-fixture", help="JSON list of issue payloads that stands in for the fetch.")
    parser.add_argument("--dry-run", action="store_true",
                        help="Print the fetch commands and the candidates, and claim nothing; with --review, "
                             "review but approve, merge, rerun, push, label, comment, and open nothing.")
    parser.add_argument("--review", action="store_true", help="Review the open Forge pull requests instead.")
    parser.add_argument("--pr", action="append", type=int,
                        help="With --review, only this pull request; with --dry-run also a closed one, replayed. "
                             "Repeat for several.")
    parser.add_argument("--head", help="With --review --dry-run and one --pr, replay that pull request at this head.")
    parser.add_argument("rhei_args", nargs="*", help="Options forwarded to `rhei run` after `--`.")
    return parser.parse_args(argv)


def run_claimed(claimed: Claim, args: argparse.Namespace) -> int:
    """Run the claimed or resumed issue's attempt and close it out; the cycle's exit code."""
    number: int = claimed.issue["number"]
    started: Started | None = start_run(FORGE_DIR, claimed, args.setup_agent, args.worker_agent, args.rhei_args)
    if started is None:
        print(f"ERROR: Cannot create the workspace for issue #{number}.", file=sys.stderr)
        close_out(claimed, {"outcome": "failed", "kind": "infrastructure", "reason": "workspace not created"})
        return RAN
    outcome: dict[str, Any] = finish_run(REPO_DIR, started)
    close_out(claimed, outcome)
    return {NO_ADVANCE_KIND: RAN_NO_STATE, BUDGET_HALT_KIND: BUDGET_HALTED}.get(str(outcome.get("kind")), RAN)


def run_cycle(args: argparse.Namespace) -> int:
    labels: tuple[str, ...] = tuple(args.label or QUEUE_LABELS)
    if args.dry_run:
        for _, command in fetch_commands(labels, args.take_blocked_issues):
            print(shlex.join(command))
    candidates = fetch_candidates(labels, args.issues_fixture, args.take_blocked_issues)
    if args.dry_run:
        for label, issue in candidates:
            print(f"#{issue['number']} [{label}] {issue.get('title', '')}")
        return RAN if candidates else NOTHING_CLAIMED
    user: str = resolve_authenticated_user()
    # A release an outage left pending comes before any other work (§FS-forge-stranded-claims.4.3).
    if not release_pending(FORGE_DIR, user):
        print("ERROR: GitHub still refuses a pending release; claiming nothing until it clears.", file=sys.stderr)
        return GITHUB_OUTAGE
    # This host's stranded work comes first, whatever the queue (§FS-forge-stranded-claims.3).
    with stranded_claim(FORGE_DIR, user) as stranded:
        if stranded is not None:
            return run_claimed(stranded, args)
    for label, issue in candidates:
        # Held to the close-out, so the claim never looks stranded (§FS-forge-stranded-claims.2).
        with issue_lock(FORGE_DIR, issue["number"]) as locked:
            if not locked:
                continue
            try:
                claimed: Claim | None = claim(issue, label, user, REPO_DIR, args.take_blocked_issues)
            except Exception as error:
                if not is_github_outage(error):
                    raise
                # Its writes are released, or the release recorded (§FS-forge-stranded-claims.4.4).
                print(f"ERROR: GitHub failed during the claim of issue #{issue['number']}: "
                      f"{format_github_exception_details(error)}", file=sys.stderr)
                return GITHUB_OUTAGE
            if claimed is None:
                continue
            print(f"Claimed {label} issue #{issue['number']}.")
            return run_claimed(claimed, args)
    print("No eligible issue could be claimed.", file=sys.stderr)
    return NOTHING_CLAIMED


def argument_error(args: argparse.Namespace) -> str | None:
    """Why the arguments cannot run, or `None` (§FS-forge-agent-runtime-selection)."""
    if args.review:
        if args.head and not (args.dry_run and len(args.pr or []) == 1):
            return "--head replays one --pr in a --dry-run"
        return review_target_error(args.review_agent)
    return target_error(args.setup_agent, args.worker_agent)


def github_outage_cause(error: BaseException) -> BaseException | None:
    """The exception, or the first of its `__cause__` chain, that is a transient GitHub failure."""
    link: BaseException | None = error
    while link is not None:
        if is_github_outage(link):
            return link
        link = link.__cause__
    return None


def main(argv: list[str] | None = None) -> int:
    args: argparse.Namespace = parse_args(argv)
    error: str | None = argument_error(args)
    if error is not None:
        print(f"ERROR: {error}.", file=sys.stderr)
        return BAD_ARGUMENTS
    try:
        if args.review:
            reviewed: int = review_pass(REPO_DIR, args.review_agent, resolve_authenticated_user(), args.dry_run,
                                        args.pr, args.head)
            print(f"[review] reviewed {reviewed} pull requests", file=sys.stderr)
            return RAN
        return run_cycle(args)
    except Exception as crash:
        # A crash GitHub caused transiently halts like the other transient halts; any other keeps
        # its traceback and exits 1, a failed cycle (§AR-forge-launcher.2).
        cause: BaseException | None = github_outage_cause(crash)
        if cause is None:
            raise
        print(f"ERROR: GitHub failed transiently: {format_github_exception_details(cause)}", file=sys.stderr)
        return GITHUB_OUTAGE


if __name__ == "__main__":
    sys.exit(main())
