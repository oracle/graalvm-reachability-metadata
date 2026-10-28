# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The one fix-request writer every sender calls.

A request carries the facts of one failure and never a hint of what to do: the
coordinate, the return code, the failing commands and the ones they kept from
running, each command's output tail, and the files the fix may edit
(§AR-forge-workflow-prompts.5). The rules live in the `fix` instruction. Sending
a request arms the loop guard (§AR-forge-workflow-pipeline.6). The failures of
the last request, or those that stop the run, are also kept as data for the
close-out comment (§AR-forge-workflow-pipeline.1).
"""
from __future__ import annotations

import os
from dataclasses import asdict, dataclass
from typing import Any

from exit_codes import RETURN_CODES, STILL_FAILING
from fix_route import latest_report
from run_state import RunState, read_json, runtime_path, write_json, write_text

FIX_REQUEST: str = "fix-request.md"
FAILURES: str = "failures.json"


@dataclass(frozen=True)
class Failure:
    """One failing command and its output tail; a program check names itself in its output."""

    command: str
    output: str
    # The commands this failure kept from running, which `fix` must pass too.
    blocked: tuple[str, ...] = ()


def editable_paths(state: RunState, sender: str) -> list[str]:
    """The files the fix may edit: the test project, and the shipped metadata past `javaTest`.

    Past `javaTest`, metadata another library owns goes to that owner, entries only added
    (§root/FS-contribution-contract.2).
    """
    paths: list[str] = [state.path(state.test_dir) + "/**"]
    if sender != "measure":
        paths.append(state.path(os.path.join("metadata", state.group, state.artifact)) + "/**")
        paths.append("the owner's `metadata/<group>/<artifact>/<version>/reachability-metadata.json`, "
                     "through `routeForeignMetadata` only")
    if sender == "finalize":
        paths.append(state.path(os.path.join("stats", state.group, state.artifact)) + "/**")
    return paths


def outputs_by_command(failures: list[Failure]) -> dict[str, list[str]]:
    """Each command's non-empty outputs; reviewer gates share one command, each naming itself."""
    outputs: dict[str, list[str]] = {}
    for failure in failures:
        if failure.output.strip():
            outputs.setdefault(failure.command, []).append(failure.output.rstrip())
    return outputs


def render(state: RunState, sender: str, failures: list[Failure], task: str | None = None) -> str:
    """The request; `task` is the audit's, whose commands all pass (§FS-local-branch-review.2)."""
    lines: list[str] = [f"# Fix: {state.coordinate}", f"Return: {RETURN_CODES[sender]}", "",
                        "## Failing commands" if task is None else "## Commands"]
    lines += [f"- {command}" for command in dict.fromkeys(failure.command for failure in failures)]
    blocked: list[str] = list(dict.fromkeys(command for failure in failures for command in failure.blocked))
    if blocked:
        # No later request covers what no agent was shown (§AR-forge-workflow-pipeline.5).
        lines += ["", "## Then run", "", "These did not run because a command above failed; they must pass too."]
        lines += [f"- {command}" for command in blocked]
    lines += ["", "## Task", "", task] if task is not None else []
    # The output is the facts, never a hint of what to do (§AR-forge-workflow-prompts.5).
    outputs: dict[str, list[str]] = outputs_by_command(failures)
    lines += ["", "## Output"] if outputs else []
    for command, texts in outputs.items():
        lines += ["", f"### {command}", "", "```text", *texts, "```"]
    lines += ["", "## You may edit"]
    lines += [f"- {path}" for path in editable_paths(state, sender)]
    return "\n".join(lines) + "\n"


def write_fix_request(workspace: str, state: RunState, sender: str, failures: list[Failure],
                      task: str | None = None) -> None:
    """Write the request for `fix` and arm the sender's loop guard; the caller saves the state."""
    text: str = render(state, sender, failures, task)
    if sender == "native" and state.pending_request == sender:
        summary: str = str(latest_report(workspace).get("summary", "")).strip()
        # Its next request starts from what the last fix did and why it stopped.
        # §AR-forge-workflow-pipeline.6
        text += f"\n## The previous fix reported\n\n{summary}\n" if summary else ""
    write_text(runtime_path(workspace, FIX_REQUEST), text)
    # The audit's commands pass, so they are no failure to quote (§FS-local-branch-review.2).
    record_failures(workspace, sender, failures if task is None else [])
    state.pending_request = sender


def record_failures(workspace: str, sender: str, failures: list[Failure]) -> None:
    """Keep the sender and its failures for the close-out (§AR-forge-workflow-pipeline.1)."""
    write_json(runtime_path(workspace, FAILURES), {"sender": sender, "failures": [asdict(f) for f in failures]})


def recorded_failures(workspace: str) -> tuple[str, list[Failure]] | None:
    """The sender and failures last recorded, or `None` before any was."""
    path: str = runtime_path(workspace, FAILURES)
    if not os.path.isfile(path):
        return None
    recorded: dict[str, Any] = read_json(path)
    return recorded["sender"], [Failure(item["command"], item["output"], tuple(item["blocked"]))
                                for item in recorded["failures"]]


def still_failing(workspace: str, sender: str, failures: list[Failure]) -> int:
    """Record the failures that stop the run, then exit `12` (§AR-forge-workflow-pipeline.6)."""
    record_failures(workspace, sender, failures)
    return STILL_FAILING


def write_placeholder(workspace: str, sender: str) -> None:
    """A declared output must exist when its program exits 0; a placeholder is never handed to `fix`."""
    write_text(runtime_path(workspace, FIX_REQUEST), f"No fix request from `{sender}`.\n")


def guard_tripped(state: RunState, sender: str) -> bool:
    """Whether this failure follows the sender's own fix, so it must not ask again."""
    return state.pending_request == sender
