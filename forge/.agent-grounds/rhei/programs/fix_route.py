# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The `fix-route` program: return to the sender, or stop when the fix could not help.

`fix` has one outgoing edge, so this program routes on the open request's
sender and the fix report: `cannot-fix` exits `40`, except for `native`, whose
budget it spends; anything else the sender's return code, whose own check then
decides (§AR-forge-workflow-pipeline.2,
§AR-forge-workflow-pipeline.6). A report is evidence, never a verdict.

`fix` writes the open request's report to one path, the one Rhei checks it
wrote; this program files it under the request's number, which the run state
keeps because Rhei counts no `fix` visit (§FS-durable-generation-logs).
"""
from __future__ import annotations

import contextlib
import json
import os
import shutil
import sys
from typing import Any

from exit_codes import CANNOT_FIX, INFRASTRUCTURE, RETURN_CODES
from run_state import RunState, load, runtime_path, write_json

CANNOT_FIX_RESULT: str = "cannot-fix"
# Its `cannot-fix` spends the next request of its budget (§AR-forge-workflow-pipeline.6).
BUDGETED_SENDER: str = "native"
# The open request's report, the `fix` state's declared output.
OPEN_REPORT: str = "fix-report.json"


def report_path(workspace: str, number: int) -> str:
    """Where the report of fix request `number`, counted from 1, is filed."""
    return runtime_path(workspace, "reports", f"fix-{number}.json")


def read_report(path: str) -> dict[str, Any]:
    """The report at `path`, or `{}` when it is missing or unreadable."""
    try:
        with open(path, encoding="utf-8") as handle:
            report: Any = json.load(handle)
    except (OSError, json.JSONDecodeError):
        return {}
    return report if isinstance(report, dict) else {}


def discard_open_report(workspace: str) -> None:
    """Drop the last request's report before the next visit, so none reads it as this one's."""
    with contextlib.suppress(FileNotFoundError):
        os.remove(runtime_path(workspace, OPEN_REPORT))


def file_report(workspace: str, state: RunState) -> dict[str, Any]:
    """File the open request's report under its number; `{}` when the visit wrote none."""
    written: str = runtime_path(workspace, OPEN_REPORT)
    if not state.fix_rounds or not os.path.isfile(written):
        return {}
    filed: str = report_path(workspace, len(state.fix_rounds))
    shutil.copyfile(written, filed)
    return read_report(filed)


def latest_report(workspace: str, state: RunState) -> dict[str, Any]:
    """The report filed for the latest request, or `{}` before any or when its visit wrote none."""
    return read_report(report_path(workspace, len(state.fix_rounds))) if state.fix_rounds else {}


def fix_requests(state: RunState) -> int:
    """The fix requests the worker handled, counted with the cover passes as iterations.

    §AR-forge-workflow-pipeline.4
    """
    return len(state.fix_rounds)


def route(state: RunState, report: dict[str, Any]) -> int:
    """The exit code `fix-route` takes for the open request and the fix report."""
    if state.pending_request not in RETURN_CODES:
        print(f"ERROR: no open fix request to return to (sender {state.pending_request!r}).", file=sys.stderr)
        return INFRASTRUCTURE
    if report.get("result") == CANNOT_FIX_RESULT and state.pending_request != BUDGETED_SENDER:
        return CANNOT_FIX
    return RETURN_CODES[state.pending_request]


def fix_route(workspace: str) -> int:
    state: RunState = load(workspace)
    report: dict[str, Any] = file_report(workspace, state)
    code: int = route(state, report)
    if report.get("result") == CANNOT_FIX_RESULT:
        # Kept as evidence whether or not the sender asks again.
        write_json(runtime_path(workspace, "cannot-fix.json"), report)
        print(f"[fix-route] the fix could not help: {report.get('summary', '')}")
    if code != CANNOT_FIX:
        print(f"[fix-route] back to {state.pending_request} ({report.get('result', 'no report')})")
    return code
