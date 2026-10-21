# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Pick and claim the next `real-use-test` issue for the launcher, or release one.

§FS-real-use-test-generation.1: candidates are open, unassigned, unblocked
issues in project status `Todo` whose title names one coordinate, claimed the
way Forge claims (§AR-real-use-test-launcher). Run with Forge on `PYTHONPATH`;
the claimed number is the only line on stdout, and Forge's narration goes to
stderr.
"""
from __future__ import annotations

import argparse
import contextlib
import json
import subprocess
import sys
from typing import Any

from dispatcher.github_api import get_authenticated_user
from dispatcher.issue_claiming import revert_issue_claim_if_still_owned_by_user, try_claim_issue
from dispatcher.project_board import get_project_item_state

import issue

PROJECT_TITLE: str = "GraalVM Reachability Metadata"
PRIORITY_TIERS: tuple[str, ...] = ("high-priority", "priority")


def list_open_issues() -> list[dict[str, Any]]:
    """Open, unassigned, unblocked `real-use-test` issues, read with `gh`."""
    completed: subprocess.CompletedProcess[str] = subprocess.run(
        ["gh", "issue", "list", "--repo", issue.REPOSITORY, "--label", issue.LABEL, "--state", "open",
         "--search", "no:assignee -is:blocked", "--limit", "1000",
         "--json", "number,title,state,labels,projectItems"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    if completed.returncode != 0:
        raise RuntimeError(f"cannot list {issue.LABEL} issues: {completed.stderr.strip()}")
    return json.loads(completed.stdout)


def _in_todo(candidate: dict[str, Any]) -> bool:
    return any(
        item.get("title") == PROJECT_TITLE and (item.get("status") or {}).get("name") == "Todo"
        for item in candidate.get("projectItems", [])
    )


def _tier(candidate: dict[str, Any]) -> int:
    labels: set[str] = {label.get("name", "") for label in candidate.get("labels", [])}
    return next((index for index, label in enumerate(PRIORITY_TIERS) if label in labels), len(PRIORITY_TIERS))


def candidates(listed: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """The `Todo` issues with one coordinate in the title, most urgent first, newest first in a tier."""
    eligible: list[dict[str, Any]] = []
    for candidate in listed:
        if not _in_todo(candidate):
            continue
        try:
            issue.coordinate_of(candidate)
        except ValueError as error:
            print(f"Skipping issue #{candidate['number']}: {error}.", file=sys.stderr)
            continue
        eligible.append(candidate)
    return sorted(eligible, key=lambda candidate: (_tier(candidate), -candidate["number"]))


def claim_next() -> int | None:
    """Claim the first candidate Forge's claim wins, or `None` when none is won."""
    user: str = get_authenticated_user()
    for candidate in candidates(list_open_issues()):
        number: int = candidate["number"]
        with contextlib.redirect_stdout(sys.stderr):
            claimed: str | None = try_claim_issue(candidate, user, required_label=issue.LABEL)
        if claimed is not None:
            return number
    return None


def release(number: int) -> None:
    """Return the issue to `Todo`, unassigned, if the launcher's account still solely holds it."""
    item_id, _status = get_project_item_state(number)
    if item_id is None:
        raise RuntimeError(f"issue #{number} is not on the {PROJECT_TITLE} project")
    with contextlib.redirect_stdout(sys.stderr):
        revert_issue_claim_if_still_owned_by_user(
            item_id, number, get_authenticated_user(), "the run's workspace could not be created")


def main(argv: list[str] | None = None) -> int:
    parser: argparse.ArgumentParser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--release", type=int, metavar="ISSUE", help="Release this issue's claim instead")
    args: argparse.Namespace = parser.parse_args(argv)
    if args.release is not None:
        release(args.release)
        return 0
    number: int | None = claim_next()
    if number is None:
        print(f"ERROR: No eligible {issue.LABEL} issue could be claimed.", file=sys.stderr)
        return 1
    print(number)
    return 0


if __name__ == "__main__":
    sys.exit(main())
