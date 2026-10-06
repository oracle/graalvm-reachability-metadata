# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""
Split a deep pass's prompt into group sessions and hand them out one by one
(§AR-code-coverage-deep-sessions).

Measurement plans the sessions from the prompt records, renders one prompt per
session, and writes the pass's queue. The dispatch program state pops one
session per visit and writes its prompt as the cover state's handoff.

Usage (dispatch state):
  python3 utility_scripts/code_coverage_deep_sessions.py dispatch \
    --queue runtime/code-coverage/prompts/deep-session-queue.json \
    --prompt runtime/code-coverage/prompts/deep-cover-prompt.md
"""

from __future__ import annotations

import argparse
import json
import os
import shutil
import sys
from dataclasses import dataclass

from utility_scripts.code_coverage_profile_graph import CallGraph
from utility_scripts.code_coverage_profile_records import (
    MAX_LISTED_METHODS,
    NearCallRecord,
    translated_path,
)
from utility_scripts.code_coverage_profile_render import write_markdown

#: A session below this size is not worth an agent session of its own
#: (§AR-code-coverage-deep-sessions.1.2).
MIN_SESSION_TARGETS: int = 10
#: The most targets one agent session is asked to finish.
MAX_SESSION_TARGETS: int = 25
#: Every session but the last pooled one holds at least the minimum, so this
#: bounds the sessions of one pass (§AR-code-coverage-deep-sessions.3).
MAX_SESSIONS_PER_PASS: int = MAX_LISTED_METHODS // MIN_SESSION_TARGETS + 1

MONOLITH: str = "monolith"
MIXED: str = "mixed"

#: Exit codes of the dispatch state (§AR-code-coverage-deep-sessions.2).
DISPATCH_SESSION: int = 10
DISPATCH_PASS_SPENT: int = 0
DISPATCH_FAILED: int = 1


class DeepSessionError(Exception):
    """The session queue is missing or malformed."""


@dataclass
class DeepSession:
    """One agent session's share of a deep pass's prompt."""

    records: list[NearCallRecord]
    entry_owners: list[str]

    @property
    def kind(self) -> str:
        # The kind follows the routes the session holds, not the rule that
        # formed it (§AR-code-coverage-deep-sessions.1.3).
        return MONOLITH if len(self.entry_owners) == 1 else MIXED


def entry_owner(record: NearCallRecord, graph: CallGraph) -> str:
    """The owner class of the first method on the record's prompted route."""
    assert record.static_path, record.target_ref.canonical_id
    return translated_path(record.static_path, graph)[0].owner


def _package(owner: str) -> str:
    return owner.rsplit(".", 1)[0] if "." in owner else ""


def group_sessions(entry_owners: list[str]) -> list[list[int]]:
    """Split prompt positions into sessions from each position's entry owner
    (§AR-code-coverage-deep-sessions.1.2).

    Positions are in prompt order. Returns each session's positions, sessions
    ordered by their best-ranked position.
    """
    assert len(entry_owners) <= MAX_LISTED_METHODS, len(entry_owners)
    groups: dict[str, list[int]] = {}
    for position, owner in enumerate(entry_owners):
        groups.setdefault(owner, []).append(position)

    sessions: list[list[int]] = []
    pool: list[int] = []
    for positions in groups.values():
        while len(positions) >= MIN_SESSION_TARGETS:
            sessions.append(positions[:MAX_SESSION_TARGETS])
            positions = positions[MAX_SESSION_TARGETS:]
        pool.extend(positions)
    # Neighbouring classes share a mixed session.
    pool.sort(key=lambda position: (_package(entry_owners[position]), position))
    for start in range(0, len(pool), MAX_SESSION_TARGETS):
        sessions.append(pool[start:start + MAX_SESSION_TARGETS])
    sessions.sort(key=min)

    assert sorted(position for session in sessions for position in session) == list(
        range(len(entry_owners))
    )
    assert len(sessions) <= MAX_SESSIONS_PER_PASS, len(sessions)
    return sessions


def plan_sessions(prompt_records: list[NearCallRecord], graph: CallGraph) -> list[DeepSession]:
    """The pass's sessions, grouped by the entry owner of each prompted route
    (§AR-code-coverage-deep-sessions.1)."""
    owners: list[str] = [entry_owner(record, graph) for record in prompt_records]
    return [
        DeepSession(
            records=[prompt_records[position] for position in session],
            entry_owners=list(dict.fromkeys(owners[position] for position in session)),
        )
        for session in group_sessions(owners)
    ]


def session_directory(output_dir: str, iteration: int) -> str:
    """Session prompts live in their own directory: measurement sets aside
    numbered siblings of a report by their last number, which a
    `-session-<k>` suffix would hijack."""
    return os.path.join(output_dir, f"deep-sessions-{iteration}")


def write_prompts(
        report: dict,
        prompt_records: list[NearCallRecord],
        graph: CallGraph,
        coordinate: str,
        iteration: int,
        output_dir: str,
) -> list[dict]:
    """Render the pass's whole prompt and one prompt per session; return the
    sessions the report records (§AR-code-coverage-deep-sessions.1)."""
    # The whole prompt stays the pass's audit record of what it asked for.
    write_markdown(
        report,
        prompt_records,
        graph,
        coordinate,
        iteration,
        os.path.join(output_dir, f"discovery-report-{iteration}.md"),
    )
    directory: str = session_directory(output_dir, iteration)
    shutil.rmtree(directory, ignore_errors=True)
    os.makedirs(directory)
    sessions: list[DeepSession] = plan_sessions(prompt_records, graph)
    entries: list[dict] = []
    for index, session in enumerate(sessions, start=1):
        entry: dict = {
            "index": index,
            "kind": session.kind,
            "entryOwners": session.entry_owners,
            "packages": list(dict.fromkeys(_package(owner) for owner in session.entry_owners)),
            "targetIds": [record.target_ref.canonical_id for record in session.records],
            "prompt": os.path.join(os.path.basename(directory), f"session-{index}.md"),
        }
        write_markdown(
            report,
            session.records,
            graph,
            coordinate,
            iteration,
            os.path.join(output_dir, entry["prompt"]),
            session={**entry, "count": len(sessions)},
        )
        entries.append(entry)
    return entries


def write_queue(report_path: str, queue_path: str) -> int:
    """Write the pass's session queue from a discovery report; return its length."""
    with open(report_path, encoding="utf-8") as report_file:
        report: dict = json.load(report_file)
    base: str = os.path.dirname(report_path)
    queue: list[dict] = [
        {
            "index": session["index"],
            "kind": session["kind"],
            "targets": len(session["targetIds"]),
            "prompt": os.path.join(base, session["prompt"]),
        }
        for session in report.get("deepSessions", [])
    ]
    _write_json(queue_path, queue)
    return len(queue)


def dispatch(queue_path: str, prompt_path: str) -> int:
    """Hand out the next queued session, or report the pass spent
    (§AR-code-coverage-deep-sessions.2)."""
    try:
        with open(queue_path, encoding="utf-8") as queue_file:
            queue: list[dict] = json.load(queue_file)
        if not isinstance(queue, list):
            raise DeepSessionError(f"session queue is not a list: {queue_path}")
    except (OSError, ValueError, DeepSessionError) as error:
        print(f"[deep-dispatch] unreadable session queue: {error}", file=sys.stderr)
        return DISPATCH_FAILED
    os.makedirs(os.path.dirname(prompt_path) or ".", exist_ok=True)
    if not queue:
        # Rhei checks the declared handoff on every zero exit, so the path
        # records that no cover session follows.
        with open(prompt_path, "w", encoding="utf-8") as prompt_file:
            prompt_file.write("No cover session follows: the pass's session queue is empty.\n")
        print("[deep-dispatch] queue empty; the pass is spent", flush=True)
        return DISPATCH_PASS_SPENT
    session: dict = queue.pop(0)
    # The session leaves the queue before its agent runs, so a failed session
    # is not repeated in the same pass.
    try:
        shutil.copyfile(session["prompt"], prompt_path)
    except (KeyError, OSError) as error:
        print(f"[deep-dispatch] session prompt unavailable: {error}", file=sys.stderr)
        return DISPATCH_FAILED
    _write_json(queue_path, queue)
    print(
        f"[deep-dispatch] session {session.get('index')} ({session.get('kind')}, "
        f"{session.get('targets')} targets); {len(queue)} left",
        flush=True,
    )
    return DISPATCH_SESSION


def _write_json(path: str, value: object) -> None:
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    with open(path, "w", encoding="utf-8") as json_file:
        json.dump(value, json_file, indent=2)
        json_file.write("\n")


def main() -> None:
    parser = argparse.ArgumentParser(description="Deep-pass group sessions.")
    commands = parser.add_subparsers(dest="command", required=True)
    queue_command = commands.add_parser("queue", help="Write a pass's session queue.")
    queue_command.add_argument("--report", required=True, help="discovery-report-<n>.json path.")
    queue_command.add_argument("--queue", required=True, help="Session queue path.")
    dispatch_command = commands.add_parser("dispatch", help="Hand out the next session.")
    dispatch_command.add_argument("--queue", required=True, help="Session queue path.")
    dispatch_command.add_argument("--prompt", required=True, help="Cover handoff prompt path.")
    args = parser.parse_args()
    if args.command == "queue":
        print(f"[deep-sessions] queued {write_queue(args.report, args.queue)} sessions")
        sys.exit(0)
    sys.exit(dispatch(args.queue, args.prompt))


if __name__ == "__main__":
    main()
