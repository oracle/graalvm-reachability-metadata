# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""What Rhei left in an attempt's workspace, read so the launcher can resume the attempt.

Its spawn records say whether a `rhei run` ran a state, its logs which state a
dead launcher was killed in, and its event log why a run ran none. The launcher
resumes an interrupted attempt and bounds the runs that cannot advance it
(§AR-forge-launcher.1); one that still cannot is held for a maintainer
with its work kept (§FS-human-intervention-policy).
"""
from __future__ import annotations

import contextlib
import json
import os
import re
from typing import Any

# Where a resumed attempt's interrupted state logs go (§AR-forge-launcher.1).
INTERRUPTED: str = os.path.join("runtime", "forge", "interrupted")
# A Rhei log: its invocation's name, then `-attempt<n>` for a retry within one visit.
RHEI_LOG: re.Pattern[str] = re.compile(r"^(?P<invocation>.+?)(?:-attempt(?P<attempt>[0-9]+))?\.log$")
# An attempt's no-advance halts in a row; the second ends it (§AR-forge-launcher.1).
NO_ADVANCE: str = os.path.join("runtime", "forge", "no-advance.json")
NO_ADVANCE_LIMIT: int = 2
# Rhei's event log of the workspace's last run, truncated when a run starts.
EVENTS: str = os.path.join("runtime", "events.jsonl")


def spawn_records(workspace: str) -> dict[str, int]:
    """Rhei's spawn records of the workspace, each with the time it was last written.

    Rhei writes a state's record when the worker it spawned ends, so a `rhei run`
    that leaves them as they were ran no state.
    """
    spawns: str = os.path.join(workspace, "runtime", "spawns")
    names: list[str] = os.listdir(spawns) if os.path.isdir(spawns) else []
    return {name: os.stat(os.path.join(spawns, name)).st_mtime_ns for name in names}


def recorded_attempt(workspace: str, invocation: str) -> int | None:
    """The last attempt the invocation's spawn record accounts for: `0` without a record, `None` when unreadable."""
    path: str = os.path.join(workspace, "runtime", "spawns", f"{invocation}.json")
    if not os.path.isfile(path):
        return 0
    try:
        with open(path, encoding="utf-8") as handle:
            return int(json.load(handle)["attempt"])
    except (OSError, ValueError, KeyError, TypeError):
        return None


def set_aside_interrupted_logs(workspace: str) -> list[str]:
    """Move every log no spawn record accounts for to `runtime/forge/interrupted/`; the moved paths.

    Such a log is the state a dead launcher was killed in: Rhei writes the record
    only when the worker ends, and refuses to spawn over a log no record accounts
    for, so without the move the attempt could never be resumed
    (§AR-forge-launcher.1). An unreadable record is left to Rhei.
    """
    logs: str = os.path.join(workspace, "runtime", "logs")
    moved: list[str] = []
    for name in sorted(os.listdir(logs)) if os.path.isdir(logs) else []:
        match: re.Match[str] | None = RHEI_LOG.match(name)
        recorded: int | None = None if match is None else recorded_attempt(workspace, match["invocation"])
        if recorded is None or int(match["attempt"] or 1) <= recorded:
            continue
        target: str = os.path.join(workspace, INTERRUPTED, name)
        copy: int = 1
        while os.path.exists(target):
            copy += 1
            target = os.path.join(workspace, INTERRUPTED, f"{copy}-{name}")
        os.makedirs(os.path.dirname(target), exist_ok=True)
        os.replace(os.path.join(logs, name), target)
        moved.append(target)
    return moved


def file_stamp(path: str) -> tuple[int, int, int] | None:
    """What changes when `path` is rewritten: its inode, modification time, and size; `None` when absent."""
    try:
        status: os.stat_result = os.stat(path)
    except FileNotFoundError:
        return None
    return status.st_ino, status.st_mtime_ns, status.st_size


def run_events(workspace: str) -> list[dict[str, Any]]:
    """The records of Rhei's event log of the workspace's last run; none when it cannot be read.

    The records are Rhei's `rhei run --json` contract, written to `runtime/events.jsonl`.
    """
    try:
        with open(os.path.join(workspace, EVENTS), encoding="utf-8") as handle:
            lines: list[str] = handle.readlines()
    except OSError:
        return []
    records: list[dict[str, Any]] = []
    for line in lines:
        with contextlib.suppress(ValueError):
            record: Any = json.loads(line)
            if isinstance(record, dict):
                records.append(record)
    return records


def halt_message(events: list[dict[str, Any]], limit: int) -> str:
    """What a run that ran no state logged: its errors and budget halts, else its last message."""
    said: list[str] = []
    last: str = ""
    for record in events:
        if record.get("event") == "message":
            last = str(record.get("text", "")).strip()
            if record.get("level") == "error":
                said.append(last)
        elif record.get("event") == "budget_halt":
            said.append(f"budget halt on {record.get('dimension')}: {record.get('remedy')}")
    text: str = "\n".join(said) or last
    return text if len(text) <= limit else text[:limit].rstrip() + " …"


def budget_halted(events: list[dict[str, Any]]) -> bool:
    """Whether a Rhei budget refused the run: a transient host condition, never a no-advance halt.

    It resets with the allowance, so counting it would hold every issue claimed
    while the budget is spent (§AR-forge-launcher.1).
    """
    return any(record.get("event") == "budget_halt" for record in events)


def count_no_advance(workspace: str) -> int:
    """Count a no-advance halt of the attempt, and return how many there are in a row.

    The count keeps the spawn records it was taken at, so a run that ran a state
    in between resets it, even one whose launcher died (§AR-forge-launcher.1).
    """
    path: str = os.path.join(workspace, NO_ADVANCE)
    records: dict[str, int] = spawn_records(workspace)
    previous: dict[str, Any] = {}
    if os.path.isfile(path):
        with contextlib.suppress(OSError, ValueError):
            with open(path, encoding="utf-8") as handle:
                previous = json.load(handle)
    halts: int = int(previous.get("halts", 0)) + 1 if previous.get("records") == records else 1
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump({"halts": halts, "records": records}, handle)
    return halts


def current_state(workspace: str) -> str | None:
    """The Rhei state the attempt's task stands in, from the last line of its transition ledger."""
    path: str = os.path.join(workspace, "runtime", "state-transitions.log")
    if not os.path.isfile(path):
        return None
    with open(path, encoding="utf-8") as handle:
        moves: list[str] = handle.read().split()
    state: str = moves[-1].rsplit("@", 1)[-1] if moves else ""
    return state if re.fullmatch(r"[A-Za-z0-9_-]+", state) else None
