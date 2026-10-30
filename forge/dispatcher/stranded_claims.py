# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Each launcher's lock on the issue it works on, and the stranded claims a dead launcher left.

A launcher holds its issue's lock from before the claim until the close-out, so
an `In Progress` claim whose latest attempt is unfinished and whose lock is free
is one whose launcher died; the launcher resumes such a claim in place before
it claims anything new (§FS-forge-stranded-claims, §AR-forge-launcher.1.1).
"""
from __future__ import annotations

import contextlib
import fcntl
import os
import re
import sys
from typing import Any, Iterator

from dispatcher.claim_preflight import get_issue_claim_cache_observation_from_payload
from dispatcher.config import STATUS_IN_PROGRESS
from dispatcher.github_api import format_github_exception_details
from dispatcher.issue_fetch import QUEUE_LABELS
from dispatcher.issue_queue import (get_issue_claim_payload, get_issue_payload_assignees,
                                    is_assigned_only_to_authenticated_user, issue_has_label)
from dispatcher.project_board import get_project_item_state
from dispatcher.run_launch import OUTCOME, Claim, workspace_path

# One lock per issue, beside its attempts: a new attempt's workspace exists only after the claim.
LOCKS: str = os.path.join("runs", "locks")
# What a live `rhei run` holds exclusively in its workspace.
RHEI_RUN_LOCK: str = os.path.join(".rhei", "run.lock")
ATTEMPT: re.Pattern[str] = re.compile(r"^forge-(\d+)-(\d+)$")


@contextlib.contextmanager
def issue_lock(forge_dir: str, issue_number: int) -> Iterator[bool]:
    """Hold the issue's launcher lock for the block; `False` when another launcher holds it.

    The kernel drops a `flock` with the last descriptor of its holder, so the death
    of the launcher, `SIGKILL` included, frees the issue (§FS-forge-stranded-claims.2).
    The descriptor is not inheritable, so no child of the run can keep it.
    """
    path: str = os.path.join(forge_dir, LOCKS, f"forge-{issue_number}.lock")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "a+", encoding="utf-8") as handle:
        try:
            fcntl.flock(handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            yield False
            return
        # For a reader only: the lock, not this record, says who holds the issue.
        handle.seek(0)
        handle.truncate()
        handle.write(f"pid={os.getpid()}\n")
        handle.flush()
        yield True


def rhei_run_live(workspace: str) -> bool:
    """Whether a `rhei run` holds the workspace, as one that outlived its launcher does."""
    try:
        handle = open(os.path.join(workspace, RHEI_RUN_LOCK), encoding="utf-8")
    except FileNotFoundError:
        return False
    with handle:
        try:
            fcntl.flock(handle.fileno(), fcntl.LOCK_SH | fcntl.LOCK_NB)
        except BlockingIOError:
            return True
        fcntl.flock(handle.fileno(), fcntl.LOCK_UN)
    return False


def unfinished_issues(forge_dir: str) -> list[int]:
    """The issues whose latest attempt records no outcome, oldest first, read from the runs directory alone."""
    runs: str = os.path.join(forge_dir, "runs")
    latest: dict[int, int] = {}
    for name in os.listdir(runs) if os.path.isdir(runs) else []:
        match: re.Match[str] | None = ATTEMPT.match(name)
        if match is not None:
            number, attempt = int(match.group(1)), int(match.group(2))
            latest[number] = max(attempt, latest.get(number, 0))
    return [number for number, attempt in sorted(latest.items())
            if not os.path.isfile(os.path.join(runs, f"forge-{number}-{attempt}", OUTCOME))]


def still_claimed(issue_number: int, user: str) -> Claim | None:
    """The issue's claim when GitHub shows it open, the user's alone, unheld, and `In Progress`; else `None`.

    A read GitHub refuses is reported, and the issue waits for a later cycle
    (§FS-forge-stranded-claims.1).
    """
    try:
        issue: dict[str, Any] = get_issue_claim_payload(issue_number)
        if str(issue.get("state", "")).upper() != "OPEN" \
                or not is_assigned_only_to_authenticated_user(get_issue_payload_assignees(issue), user) \
                or get_issue_claim_cache_observation_from_payload(issue, user) is not None:
            return None
        item_id, status = get_project_item_state(issue_number)
    except Exception as exc:
        print(f"ERROR: Issue #{issue_number} has an unfinished attempt, but GitHub could not be read to tell "
              f"whether its claim is stranded: {format_github_exception_details(exc)}", file=sys.stderr)
        return None
    if not item_id or status != STATUS_IN_PROGRESS:
        return None
    label: str = next((name for name in QUEUE_LABELS if issue_has_label(issue, name)), "")
    return Claim(issue=issue, label=label, item_id=item_id)


@contextlib.contextmanager
def stranded_claim(forge_dir: str, user: str) -> Iterator[Claim | None]:
    """The first stranded claim of this host, its issue lock held for the block; `None` when there is none.

    Only an unfinished attempt is a candidate, so an issue with no workspace on
    this host, which may be another host's, is never read (§FS-forge-stranded-claims.1).
    """
    for number in unfinished_issues(forge_dir):
        with issue_lock(forge_dir, number) as locked:
            if not locked:
                continue
            # Read again under the lock: its last holder may have finished the attempt since the listing.
            workspace: str = workspace_path(forge_dir, number)
            if not os.path.isdir(workspace) or rhei_run_live(workspace):
                continue
            claimed: Claim | None = still_claimed(number, user)
            if claimed is not None:
                print(f"[resume] issue #{number} is In Progress and its launcher is gone; resuming "
                      f"{os.path.basename(workspace)}", file=sys.stderr)
                yield claimed
                return
    yield None
