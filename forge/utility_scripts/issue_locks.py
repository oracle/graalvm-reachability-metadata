# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Each launcher's lock on the issue it works on, and the Forge runs those locks show live on this host.

Launchers that share one checkout share its runs, so each holds its issue's
lock from before the claim until the close-out (§FS-forge-stranded-claims.2).
The launcher reads the locks to find a dead launcher's claim
(§FS-forge-stranded-claims.1); the pre-publication gate reads them to tell
another run's Docker pull from its own (§FS-local-ci-equivalent-verification.2.2).
"""
from __future__ import annotations

import contextlib
import fcntl
import os
import re
from typing import Iterator

# One lock per issue, beside its attempts: a new attempt's workspace exists only after the claim.
LOCKS: str = os.path.join("runs", "locks")
LOCK_FILE: re.Pattern[str] = re.compile(r"^forge-(\d+)\.lock$")


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


def lock_held(path: str) -> bool:
    """Whether a live process holds an exclusive `flock` on `path`, probed with a shared one released at once."""
    try:
        handle = open(path, encoding="utf-8")
    except FileNotFoundError:
        return False
    with handle:
        try:
            fcntl.flock(handle.fileno(), fcntl.LOCK_SH | fcntl.LOCK_NB)
        except BlockingIOError:
            return True
        fcntl.flock(handle.fileno(), fcntl.LOCK_UN)
    return False


def live_issues(forge_dir: str) -> set[int]:
    """The issues whose launcher lock is held on this host now."""
    locks: str = os.path.join(forge_dir, LOCKS)
    names: list[str] = os.listdir(locks) if os.path.isdir(locks) else []
    return {int(match.group(1)) for match in map(LOCK_FILE.match, names)
            if match is not None and lock_held(os.path.join(locks, match.group(0)))}
