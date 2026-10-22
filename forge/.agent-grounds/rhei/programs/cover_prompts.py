# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The cover prompts `analyze` and `measure` hand to `cover`.

The `cover` instruction is the same in every mode; a prompt carries only the
mode and the exact targets, rendered from the two dynamic-access templates
(§AR-forge-workflow-prompts.4). The first prompt also lists the reporter's
request as a required target.
"""
from __future__ import annotations

import os

from utility_scripts.dynamic_access_report import (
    DynamicAccessCoverageReport,
    format_call_sites,
    format_full_report,
)

from run_state import Explore, RunState, runtime_path, write_text

MODE_BASIC: str = "basic"
MODE_BULK: str = "bulk"
MODE_CLASS: str = "class"
COVER_PROMPT: str = "cover-prompt.md"
TEMPLATES: str = os.path.join("prompt_templates", "dynamic_access")
NO_REQUEST: str = "None."


def _template(state: RunState, name: str) -> str:
    """A renderer template from the source checkout's Forge directory."""
    with open(os.path.join(state.repo_checkout, "forge", TEMPLATES, name), encoding="utf-8") as handle:
        return handle.read()


def reporter_section(workspace: str, state: RunState) -> str:
    """The reporter's request on the first prompt, and nothing after it."""
    if not state.has_reporter_request or state.explore.passes > 0:
        return NO_REQUEST
    with open(runtime_path(workspace, "reporter-request.md"), encoding="utf-8") as handle:
        return "Required on this pass. " + handle.read().strip()


def explored_report(report: DynamicAccessCoverageReport, explore: Explore) -> DynamicAccessCoverageReport:
    """The report narrowed to this run's targets that are still open."""
    open_classes: set[str] = set(explore.targets) - set(explore.exhausted)
    return DynamicAccessCoverageReport(
        coordinate=report.coordinate,
        has_dynamic_access=report.has_dynamic_access,
        total_calls=report.total_calls,
        covered_calls=report.covered_calls,
        classes=[entry for entry in report.classes if entry.class_name in open_classes],
    )


def bulk_prompt(workspace: str, state: RunState, report: DynamicAccessCoverageReport) -> str:
    narrowed: DynamicAccessCoverageReport = explored_report(report, state.explore)
    open_classes: int = sum(1 for entry in narrowed.classes if entry.uncovered_calls > 0)
    progress: str = (
        f"Bulk pass {state.explore.bulk_passes + 1} of {state.budgets.bulk_passes}: "
        f"{report.covered_calls} of {report.total_calls} dynamic-access calls covered; "
        f"{open_classes} classes below still have uncovered calls."
    )
    return _template(state, "bulk-dynamic-access-iteration.md").format(
        library=state.coordinate,
        iteration_progress=progress,
        dynamic_access_full_report=format_full_report(narrowed),
        issue_requested_metadata_context=reporter_section(workspace, state),
    )


def class_prompt(workspace: str, state: RunState, report: DynamicAccessCoverageReport) -> str:
    explore: Explore = state.explore
    entry = report.get_class(explore.class_name or "")
    if entry is None:
        raise ValueError(f"class {explore.class_name} is not in the dynamic-access report")
    progress: str = f"Attempt {explore.class_attempt} of {state.budgets.class_attempts} on this class."
    if explore.feedback:
        progress += f"\n\nPrevious attempt: {explore.feedback}"
    return _template(state, "dynamic-access-iteration.md").format(
        library=state.coordinate,
        active_class_name=entry.class_name,
        active_class_source_file=entry.resolved_source_file or entry.source_file or "unknown",
        dynamic_access_progress=progress,
        uncovered_dynamic_access_calls=format_call_sites(entry.uncovered_call_sites),
        issue_requested_metadata_context=reporter_section(workspace, state),
    )


def basic_prompt(workspace: str, state: RunState) -> str:
    lines: list[str] = [
        f"# Cover: {state.coordinate}",
        "",
        "No dynamic-access call site is left to target. Write tests for the main public API in the "
        "allowed packages.",
        "",
        "## Reporter-requested metadata",
        "",
        reporter_section(workspace, state),
    ]
    return "\n".join(lines) + "\n"


def write_prompt(workspace: str, state: RunState, report: DynamicAccessCoverageReport | None) -> None:
    """Render the prompt of the current mode into the `cover` handoff."""
    mode: str = state.explore.mode
    if mode == MODE_BASIC or report is None:
        text: str = basic_prompt(workspace, state)
    elif mode == MODE_BULK:
        text = bulk_prompt(workspace, state, report)
    else:
        text = class_prompt(workspace, state, report)
    write_text(runtime_path(workspace, COVER_PROMPT), text)


def write_placeholder(workspace: str) -> None:
    """A declared output must exist when its program exits 0; a placeholder is never handed to `cover`."""
    write_text(runtime_path(workspace, COVER_PROMPT), "No cover prompt: exploration is over.\n")
