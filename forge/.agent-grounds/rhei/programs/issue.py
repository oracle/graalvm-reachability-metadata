# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Read the issue a run resolves and derive its route.

The label decides the route and the title the coordinate
(§FS-forge-scope, §FS-forge-run-requirements.3); the three `fails-*` labels
share one route (§AR-forge-control-plane).
"""
from __future__ import annotations

import json
import subprocess
from dataclasses import dataclass
from typing import Any

from utility_scripts.metadata_index import extract_coordinate_parts

from run_state import ROUTE_NEW, ROUTE_REPAIR, ROUTE_UPDATE

REPOSITORY: str = "oracle/graalvm-reachability-metadata"
ROUTES: dict[str, str] = {
    "library-new-request": ROUTE_NEW,
    "library-update-request": ROUTE_UPDATE,
    "fails-javac-compile": ROUTE_REPAIR,
    "fails-java-run": ROUTE_REPAIR,
    "fails-native-image-run": ROUTE_REPAIR,
}
# The trusted publisher's route for each issue label (§AR-actions-publication).
TASK_TYPES: dict[str, str] = {
    "library-new-request": "library-new-request",
    "library-update-request": "library-update-request",
    "fails-javac-compile": "fixes-javac-fail",
    "fails-java-run": "fixes-java-run-fail",
    "fails-native-image-run": "fixes-native-image-run-fail",
}
CHUNKED_LABEL: str = "chunked-dynamic-access"


@dataclass(frozen=True)
class Issue:
    number: int
    label: str
    coordinate: str
    body: str
    labels: frozenset[str]

    @property
    def route(self) -> str:
        return ROUTES[self.label]


def parse_issue(payload: dict[str, Any]) -> Issue:
    """The open issue's one workflow label and one coordinate; `ValueError` otherwise."""
    if str(payload.get("state", "")).upper() != "OPEN":
        raise ValueError(f"issue is {payload.get('state')}, not open")
    labels: frozenset[str] = frozenset(label.get("name", "") for label in payload.get("labels", []))
    workflow_labels: list[str] = sorted(labels & ROUTES.keys())
    if len(workflow_labels) != 1:
        raise ValueError(f"issue must carry exactly one workflow label, found {workflow_labels or 'none'}")
    parts: tuple[str, str, str] | None = extract_coordinate_parts(str(payload.get("title", "")))
    if parts is None:
        raise ValueError("issue title names no group:artifact:version")
    return Issue(
        number=int(payload["number"]),
        label=workflow_labels[0],
        coordinate=":".join(parts),
        body=str(payload.get("body") or ""),
        labels=labels,
    )


def read_issue(number: int, cwd: str) -> dict[str, Any]:
    """The issue's number, title, state, labels, body, and author, read with `gh`."""
    completed: subprocess.CompletedProcess[str] = subprocess.run(
        ["gh", "issue", "view", str(number), "--repo", REPOSITORY, "--json", "number,title,state,labels,body,author"],
        cwd=cwd,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    if completed.returncode != 0:
        raise ValueError(f"cannot read issue #{number}: {completed.stderr.strip()}")
    return json.loads(completed.stdout)
