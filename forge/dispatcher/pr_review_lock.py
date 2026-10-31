# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The `in-review` lock of the published pull-request review (§FS-published-pr-review.3).

Every launcher reviews as one account, and a second add of a label leaves no
trace, so the label only keeps other passes' skips cheap: a claim comment decides
who holds the lock, the earliest live one winning. What one review took is a
`Lock`, and its release gives back exactly that
(§AR-forge-dispatcher-decomposition.1).
"""
from __future__ import annotations

import secrets
import subprocess
import sys
from dataclasses import dataclass, field
from datetime import timedelta
from typing import Any

from dispatcher import pr_review_state as state
from dispatcher.config import REPO
from dispatcher.github_api import gh, gh_json
from dispatcher.issue_admin import add_pull_request_label, ensure_repo_label_exists, remove_pull_request_label

STALE_AFTER: timedelta = timedelta(hours=3)
LABEL_DESCRIPTION: tuple[str, str] = ("fbca04", "A review agent is reviewing this pull request")


@dataclass
class Lock:
    """What one review holds of a pull request's lock, so its release undoes exactly that."""

    number: int
    labeled: bool = False
    claims: list[int] = field(default_factory=list)


class Locks:
    """The claims of one pass; a dry run only prints them."""

    def __init__(self, login: str, dry_run: bool) -> None:
        self.login: str = login
        self.dry_run: bool = dry_run
        # Tells this pass's claims from those of other launchers on the same account.
        self.token: str = secrets.token_hex(6)

    def claim(self, pull: dict[str, Any], lock: Lock) -> str | None:
        """Take the lock into `lock`, or say why another review holds it: its claim came first."""
        number: int = pull["number"]
        head: str = pull["headRefOid"]
        if self.dry_run:
            print(f"[review] #{number}: would add {state.IN_REVIEW}", file=sys.stderr)
            return None
        lock.labeled = True
        ensure_repo_label_exists(state.IN_REVIEW, *LABEL_DESCRIPTION)
        add_pull_request_label(number, state.IN_REVIEW)
        text: str = (f"Forge is reviewing `{head}`; this claim is deleted when the review ends.\n\n"
                     f"<!-- forge-review-claim:{head}:{self.token} -->")
        mine: state.Claim | None = state.claim_of(gh_json("api", "--method", "POST",
                                                          f"repos/{REPO}/issues/{number}/comments", "-f",
                                                          f"body={text}"))
        assert mine is not None, "a posted claim carries its marker"
        # Recorded before the read, so a failed read still withdraws it.
        lock.claims = [mine.id]
        found: list[state.Claim] = [mine, *state.claims(number, self.login)]
        lock.claims = sorted({claim.id for claim in found if claim.token == self.token})
        live: list[state.Claim] = [claim for claim in found if mine.created - claim.created <= STALE_AFTER]
        holder: state.Claim = min(live, key=lambda claim: claim.id)
        if holder.token != self.token:
            # The label is the holder's to remove.
            lock.labeled = False
            return f"held by another review since {holder.created.isoformat()}"
        print(f"[review] #{number}: holds {state.IN_REVIEW} as {self.token}", file=sys.stderr)
        # Killed reviews left these; a failed delete leaves them to be ignored again.
        for stale in (claim for claim in found if claim not in live):
            gh("api", "--method", "DELETE", f"repos/{REPO}/issues/comments/{stale.id}", check=False)
        return None

    def release(self, lock: Lock) -> None:
        """Delete the claims in `lock` and, when it held the lock, remove the label.

        A failed write leaves that part to go stale, as a killed pass would.
        """
        if self.dry_run:
            print(f"[review] #{lock.number}: would remove {state.IN_REVIEW}", file=sys.stderr)
            return
        failed: list[str] = [f"claim {claim}" for claim in lock.claims
                             if gh("api", "--method", "DELETE", f"repos/{REPO}/issues/comments/{claim}",
                                   check=False).returncode != 0]
        try:
            if lock.labeled:
                remove_pull_request_label(lock.number, state.IN_REVIEW)
        except (RuntimeError, subprocess.CalledProcessError) as error:
            failed.append(f"label ({error})")
        if failed:
            print(f"[review] #{lock.number}: could not release {', '.join(failed)}; it goes stale",
                  file=sys.stderr)
