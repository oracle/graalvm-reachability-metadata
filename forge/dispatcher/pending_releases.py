# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The releases an outage left unconfirmed, recorded for the next cycle to retry.

A release that still fails transiently is recorded in the runs directory, and
one GitHub confirms or refuses drops its record, so a record always means the
last release of its issue met an outage (§FS-forge-stranded-claims.4.2,
§AR-forge-launcher.1.2). The cycle retries them before it claims anything.
"""
from __future__ import annotations

import contextlib
import json
import os
import re
from dataclasses import dataclass
from datetime import datetime, timezone

from dispatcher.config import FORGE_DIR

RELEASES: str = os.path.join(FORGE_DIR, "runs", "releases")
RECORD: re.Pattern[str] = re.compile(r"^forge-(\d+)\.json$")


@dataclass(frozen=True)
class PendingRelease:
    issue: int
    item_id: str
    reason: str
    # When the release last failed: an issue updated after it may have been claimed since.
    recorded_at: datetime


def record_path(issue_number: int) -> str:
    return os.path.join(RELEASES, f"forge-{issue_number}.json")


def record_pending_release(issue_number: int, item_id: str, reason: str) -> None:
    """Record the issue's release for the next cycle, replacing an earlier record of it."""
    os.makedirs(RELEASES, exist_ok=True)
    record: dict[str, str] = {"item_id": item_id, "reason": reason,
                              "recorded_at": datetime.now(timezone.utc).isoformat()}
    temporary: str = record_path(issue_number) + ".tmp"
    with open(temporary, "w", encoding="utf-8") as handle:
        json.dump(record, handle)
    os.replace(temporary, record_path(issue_number))


def drop_pending_release(issue_number: int) -> None:
    with contextlib.suppress(FileNotFoundError):
        os.remove(record_path(issue_number))


def is_release_pending(issue_number: int) -> bool:
    return os.path.isfile(record_path(issue_number))


def pending_issues() -> list[int]:
    """The issues whose release is recorded, oldest first."""
    names: list[str] = os.listdir(RELEASES) if os.path.isdir(RELEASES) else []
    return sorted(int(match.group(1)) for name in names if (match := RECORD.match(name)))


def pending_release(issue_number: int) -> PendingRelease | None:
    """The issue's recorded release, or `None` once a release dropped it."""
    try:
        with open(record_path(issue_number), encoding="utf-8") as handle:
            record: dict[str, str] = json.load(handle)
    except FileNotFoundError:
        return None
    return PendingRelease(issue=issue_number, item_id=record["item_id"], reason=record["reason"],
                          recorded_at=datetime.fromisoformat(record["recorded_at"]))
