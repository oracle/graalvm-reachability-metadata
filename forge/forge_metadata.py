# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Find, claim, and start one Forge issue run, or run the CI pass over the open Forge pull requests.

One cycle fetches the queues with `gh issue list`, claims the first eligible
issue whose form is valid, runs its `forge-metadata` Rhei workspace, and closes
it out; every decision about the issue is the run's
(§AR-forge-workflow-pipeline.1, §FS-forge-issue-resolution-goal). `--ci` runs
the CI pass over published pull requests instead, and `--ci --dry-run` acts on
none of them (§FS-published-pr-ci-failures).

Usage:
  python3 forge_metadata.py [--label LABEL]... [--setup-agent TARGET] [--worker-agent TARGET]
      [--issues-fixture FILE] [--dry-run] [-- RHEI_RUN_OPTIONS...]
  python3 forge_metadata.py --ci [--setup-agent TARGET] [--pr NUMBER]... [--dry-run]
"""
from __future__ import annotations

import argparse
import os
import shlex
import sys

from dispatcher.github_api import resolve_authenticated_user
from dispatcher.issue_fetch import QUEUE_LABELS, fetch_candidates, fetch_commands
from dispatcher.pr_ci import ci_pass
from dispatcher.run_launch import Claim, claim, close_out, read_outcome, start_run, target_error, workspace_path

FORGE_DIR: str = os.path.dirname(os.path.abspath(__file__))
REPO_DIR: str = os.path.dirname(FORGE_DIR)
DEFAULT_SETUP_AGENT: str = "claude-code[medium]:sonnet"
DEFAULT_WORKER_AGENT: str = "pi[high]:openai-codex/gpt-5.6-luna"
# Exit codes the loop reads: an issue ran, or nothing could be claimed.
RAN: int = 0
NOTHING_CLAIMED: int = 1
BAD_ARGUMENTS: int = 2


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser: argparse.ArgumentParser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--label", action="append", choices=QUEUE_LABELS,
                        help="Queue to fetch; repeat for several. Default: every queue, in claim order.")
    parser.add_argument("--setup-agent", default=DEFAULT_SETUP_AGENT,
                        help=f"Rhei target of the setup state and the CI diagnosis, a claude-code target. "
                             f"Default: {DEFAULT_SETUP_AGENT}")
    parser.add_argument("--worker-agent", default=DEFAULT_WORKER_AGENT,
                        help=f"Rhei target of the cover and fix states. Default: {DEFAULT_WORKER_AGENT}")
    parser.add_argument("--issues-fixture", help="JSON list of issue payloads that stands in for the fetch.")
    parser.add_argument("--dry-run", action="store_true",
                        help="Print the fetch commands and the candidates, and claim nothing; with --ci, "
                             "diagnose but rerun, push, open, label, and comment nothing.")
    parser.add_argument("--ci", action="store_true", help="Run the CI pass over open Forge pull requests instead.")
    parser.add_argument("--pr", action="append", type=int,
                        help="With --ci, only this pull request, open or closed; repeat for several.")
    parser.add_argument("rhei_args", nargs="*", help="Options forwarded to `rhei run` after `--`.")
    return parser.parse_args(argv)


def run_cycle(args: argparse.Namespace) -> int:
    labels: tuple[str, ...] = tuple(args.label or QUEUE_LABELS)
    if args.dry_run:
        for _, command in fetch_commands(labels):
            print(shlex.join(command))
    candidates = fetch_candidates(labels, args.issues_fixture)
    if args.dry_run:
        for label, issue in candidates:
            print(f"#{issue['number']} [{label}] {issue.get('title', '')}")
        return RAN if candidates else NOTHING_CLAIMED
    user: str = resolve_authenticated_user()
    for label, issue in candidates:
        claimed: Claim | None = claim(issue, label, user, REPO_DIR)
        if claimed is None:
            continue
        print(f"Claimed {label} issue #{issue['number']}.")
        if not start_run(FORGE_DIR, claimed, args.setup_agent, args.worker_agent, args.rhei_args):
            print(f"ERROR: Cannot create the workspace for issue #{issue['number']}.", file=sys.stderr)
            close_out(claimed, {"outcome": "failed", "kind": "infrastructure", "reason": "workspace not created"})
            return RAN
        close_out(claimed, read_outcome(workspace_path(FORGE_DIR, issue["number"])))
        return RAN
    print("No eligible issue could be claimed.", file=sys.stderr)
    return NOTHING_CLAIMED


def main(argv: list[str] | None = None) -> int:
    args: argparse.Namespace = parse_args(argv)
    error: str | None = target_error(args.setup_agent, args.worker_agent)
    if error is not None:
        print(f"ERROR: {error}.", file=sys.stderr)
        return BAD_ARGUMENTS
    if args.ci:
        handled: int = ci_pass(REPO_DIR, args.setup_agent, resolve_authenticated_user(), args.dry_run, args.pr)
        print(f"[ci] handled {handled} pull requests", file=sys.stderr)
        return RAN
    return run_cycle(args)


if __name__ == "__main__":
    sys.exit(main())
