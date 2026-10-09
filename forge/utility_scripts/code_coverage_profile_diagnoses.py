# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The diagnosis sections of one deep prompt group.

After a group's routes come its miss classifications, one section per cause
(§AR-code-coverage-deep-navigation.3.3): each fork once, with only the branches
that lead to a target (§AR-code-coverage-deep-navigation.3.2), the catch
boundaries with the calls that ran, and the dispatch sites with their receivers
(§AR-code-coverage-deep-navigation.3.1). The JSON report keeps the full
classification; this module only chooses what the prompt shows.
"""

from __future__ import annotations

import os
from dataclasses import dataclass

from utility_scripts.code_coverage_model import MethodRef, parse_inventory_id
from utility_scripts.code_coverage_profile_records import simple_owner

MAX_RENDERED_DISPATCH_CANDIDATES = 12
MAX_RENDERED_RECEIVERS = 6

BRANCHES_SECTION = "Branches not taken:"
EXCEPTION_SECTION = "Reached only through an exception:"
DISPATCH_SECTION = "Dispatched elsewhere:"


def display_method(ref: MethodRef, qualify_owner: bool) -> str:
    """`Owner.name(...)`, a constructor as `Owner(...)`; the owner only when asked."""
    owner = simple_owner(ref.owner)
    arguments = "..." if ref.params else ""
    if ref.name == "<init>":
        return f"{owner}({arguments})"
    method = f"{ref.name}({arguments})"
    return f"{owner}.{method}" if qualify_owner else method


class SourceRoot:
    """The extracted library sources a prompt's locations resolve against
    (§AR-code-coverage-deep-navigation.3.2).

    A location is the JaCoCo source path when that file exists under the root,
    else the file name alone; `resolved` records whether any location used the
    root, so a prompt states the root only when it is needed.
    """

    def __init__(self, path: str | None) -> None:
        self.path: str | None = (
            os.path.abspath(path) if path and os.path.isdir(path) else None
        )
        self.resolved: bool = False

    def location(self, source_path: str) -> str:
        if self.path is not None and os.path.isfile(os.path.join(self.path, source_path)):
            self.resolved = True
            return source_path
        return os.path.basename(source_path)


@dataclass(frozen=True)
class Diagnosis:
    """One route's uncovered call, as the route names it, and its classification."""

    call: str
    classification: dict


def _line_location(evidence: dict, sources: SourceRoot | None) -> str:
    source_path: str = evidence["sourcePath"]
    shown: str = (
        sources.location(source_path) if sources is not None
        else os.path.basename(source_path)
    )
    return f"{shown}:{evidence['line']}"


def _call_line(classification: dict) -> str:
    target: dict | None = classification.get("target")
    return f"line {target['line']}" if target is not None else "an unknown line"


def _branch_items(diagnosis: Diagnosis, fork: dict) -> list[str]:
    """The fork's successors that land in the dead region, numbered among every
    successor of the line in bytecode order (§AR-code-coverage-deep-navigation.3.2)."""
    branches: list[dict] = fork.get("branches") or []
    call: str = f"{diagnosis.call} at {_call_line(diagnosis.classification)}"
    # A successor that lands on a later condition of the same line is named
    # after it; with one condition on the line there is nothing to name.
    conditions: dict[int, int] = {
        branch["blockStart"]: index for index, branch in enumerate(branches, start=1)
    } if len(branches) > 1 else {}
    successors: list[dict] = [
        successor for branch in branches for successor in branch["successors"]
    ]
    items: list[str] = []
    for number, successor in enumerate(successors, start=1):
        if not successor["reachesTarget"]:
            continue
        if successor["bci"] in conditions:
            landing: str = f"condition {conditions[successor['bci']]} of line {fork['line']}"
        elif successor["line"] is not None:
            landing = f"line {successor['line']}"
        else:
            landing = f"bci {successor['bci']}"
        items.append(f"    branch {number} at {landing}: {call}")
    # Without a control-flow table only the fork line is known.
    return items or [f"    untraced branch: {call}"]


def _catch_items(diagnosis: Diagnosis, location: str) -> list[str]:
    """Per handler, the calls in its `try` range that ran, each at its own line
    and without counts (§AR-code-coverage-deep-navigation.3.3)."""
    items: list[str] = []
    for handler in diagnosis.classification["exception"]["handlers"]:
        caught: str = handler["type"].rsplit(".", 1)[-1]
        where: str = f" at line {handler['line']}" if handler["line"] is not None else ""
        items.append(f"  `{location}`: {diagnosis.call} in catch ({caught}){where}")
        for call in handler.get("calls", []):
            ref: MethodRef | None = parse_inventory_id(call["callee"])
            if not call["ran"] or ref is None:
                continue
            line: str = f"line {call['line']}" if call["line"] is not None else "line ?"
            items.append(f"    {line}: {display_method(ref, True)} never threw it")
    return items


def _no_fork_items(diagnosis: Diagnosis, sources: SourceRoot | None) -> list[str]:
    classification: dict = diagnosis.classification
    target: dict | None = classification.get("target")
    if target is None:
        return [f"  {diagnosis.call}: invoking line unavailable"]
    location: str = _line_location(target, sources)
    if classification.get("exception"):
        return _catch_items(diagnosis, location)
    nearest: dict | None = classification.get("nearestCovered")
    nearest_text: str = (
        f"nearest covered line {nearest['line']}" if nearest is not None
        else "no covered line before it"
    )
    return [f"  `{location}`: {diagnosis.call}, no fork above; {nearest_text}"]


def _dispatch_items(diagnosis: Diagnosis, sources: SourceRoot | None) -> list[str]:
    """The site's receivers and candidates with their counts: the receiver
    histogram is what the agent acts on (§AR-code-coverage-deep-navigation.3.1)."""
    classification: dict = diagnosis.classification
    target: dict | None = classification.get("target")
    head: str = (
        f"  `{_line_location(target, sources)}`: {diagnosis.call}" if target is not None
        else f"  {diagnosis.call}"
    )
    site: dict | None = classification.get("site")
    candidate_records: list[dict] = sorted(
        classification["candidates"],
        key=lambda candidate: (
            not candidate["coverageSuite"],
            -(candidate.get("dispatches") or 0),
            candidate["id"],
        ),
    )
    candidates: list[str] = []
    for candidate in candidate_records[:MAX_RENDERED_DISPATCH_CANDIDATES]:
        tags: list[str] = ["coverage suite"] if candidate["coverageSuite"] else []
        dispatches: int | None = candidate.get("dispatches")
        if dispatches is not None:
            tags.append(f"dispatched ×{dispatches:,}" if dispatches else "never dispatched here")
        candidates.append(
            f"`{candidate['id']}`" + (f" [{', '.join(tags)}]" if tags else "")
        )
    omitted: int = len(candidate_records) - len(candidates)
    candidates_text: str = ", ".join(candidates)
    if omitted:
        candidates_text += f", … {omitted} more in JSON"
    if site is None:
        return [
            f"{head} — same line, different implementation answered",
            f"    candidates: {candidates_text}",
        ]
    receivers: list[str] = [
        f"{simple_owner(receiver['type'])} ×{receiver['count']:,}"
        for receiver in site["receivers"][:MAX_RENDERED_RECEIVERS]
    ]
    if len(site["receivers"]) > MAX_RENDERED_RECEIVERS:
        receivers.append(f"… {len(site['receivers']) - MAX_RENDERED_RECEIVERS} more")
    # `None` means a receiver did not resolve, so nothing is known about the
    # target's share; only a proven zero earns the claim.
    never: str = ", never to your target" if site.get("targetDispatches") == 0 else ""
    return [
        f"{head} — site dispatched {site['dispatches']:,}×{never}",
        f"    observed receivers: {' · '.join(receivers)}",
        f"    candidates: {candidates_text}",
    ]


def diagnosis_lines(
        diagnoses: list[Diagnosis],
        sources: SourceRoot | None = None,
) -> list[str]:
    """A group's diagnoses in one section per cause, each fork once
    (§AR-code-coverage-deep-navigation.3.3); every section opens with a blank line."""
    forks: dict[tuple[str, int], list[str]] = {}
    exceptions: list[str] = []
    dispatches: list[str] = []
    for diagnosis in diagnoses:
        kind: str = diagnosis.classification["kind"]
        if kind == "fork-not-taken":
            fork: dict = diagnosis.classification["fork"]
            key: tuple[str, int] = (fork["sourcePath"], fork["line"])
            if key not in forks:
                forks[key] = [
                    f"  `{_line_location(fork, sources)}` "
                    f"({fork['cb']} of {fork['mb'] + fork['cb']} taken)"
                ]
            forks[key] += _branch_items(diagnosis, fork)
        elif kind == "dispatched-elsewhere":
            dispatches += _dispatch_items(diagnosis, sources)
        else:
            exceptions += _no_fork_items(diagnosis, sources)
    lines: list[str] = []
    for title, items in (
            (BRANCHES_SECTION, [item for fork in forks.values() for item in fork]),
            (EXCEPTION_SECTION, exceptions),
            (DISPATCH_SECTION, dispatches),
    ):
        if items:
            lines += ["", title, *items]
    return lines
