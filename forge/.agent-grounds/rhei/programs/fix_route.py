# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The `fix-route` program: return to the sender, or stop when the fix could not help.

`fix` has one outgoing edge, so this program routes on the open request's
sender and the fix report: `cannot-fix` exits `40`, anything else the sender's
return code, whose own check then decides (§AR-forge-workflow-pipeline.2,
§AR-forge-workflow-pipeline.6). A report is evidence, never a verdict.
"""
from __future__ import annotations

import glob
import json
import os
import re
import sys
from typing import Any

from exit_codes import CANNOT_FIX, INFRASTRUCTURE, RETURN_CODES
from run_state import RUNTIME_DIR, RunState, load, runtime_path, write_json

CANNOT_FIX_RESULT: str = "cannot-fix"
REPORT_VISIT: re.Pattern[str] = re.compile(r"fix-(\d+)\.json$")


def reports(workspace: str) -> list[tuple[int, str]]:
    """Every fix report with its visit number: `fix` writes one per request it handles."""
    paths: list[str] = glob.glob(os.path.join(workspace, RUNTIME_DIR, "reports", "fix-*.json"))
    return [(int(match.group(1)), path) for path in paths if (match := REPORT_VISIT.search(path)) is not None]


def fix_requests(workspace: str) -> int:
    """The fix requests the worker handled, counted with the cover passes as iterations.

    §AR-forge-workflow-pipeline.4
    """
    return len(reports(workspace))


def latest_report(workspace: str) -> dict[str, Any]:
    """The report of the most recent `fix` visit, or `{}` when it is missing or unreadable."""
    numbered: list[tuple[int, str]] = reports(workspace)
    if not numbered:
        return {}
    try:
        with open(max(numbered)[1], encoding="utf-8") as handle:
            report: Any = json.load(handle)
    except (OSError, json.JSONDecodeError):
        return {}
    return report if isinstance(report, dict) else {}


def route(state: RunState, report: dict[str, Any]) -> int:
    """The exit code `fix-route` takes for the open request and the fix report."""
    if state.pending_request not in RETURN_CODES:
        print(f"ERROR: no open fix request to return to (sender {state.pending_request!r}).", file=sys.stderr)
        return INFRASTRUCTURE
    if report.get("result") == CANNOT_FIX_RESULT:
        return CANNOT_FIX
    return RETURN_CODES[state.pending_request]


def fix_route(workspace: str) -> int:
    state: RunState = load(workspace)
    report: dict[str, Any] = latest_report(workspace)
    code: int = route(state, report)
    if code == CANNOT_FIX:
        write_json(runtime_path(workspace, "cannot-fix.json"), report)
        print(f"[fix-route] the fix could not help: {report.get('summary', '')}")
    else:
        print(f"[fix-route] back to {state.pending_request} ({report.get('result', 'no report')})")
    return code
