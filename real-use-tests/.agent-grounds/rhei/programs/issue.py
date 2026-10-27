# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Read the coordinate a run resolves from its `real-use-test` issue.

§FS-real-use-test-generation.1: the issue must be open, carry the label, and
name exactly one `group:artifact:version` in its title.
"""
from __future__ import annotations

import json
import re
import subprocess
from typing import Any

REPOSITORY: str = "oracle/graalvm-reachability-metadata"
LABEL: str = "real-use-test"
COORDINATE_IN_TEXT: re.Pattern[str] = re.compile(r"[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+:[A-Za-z0-9_.+-]*[A-Za-z0-9_+-]")


def coordinate_of(issue: dict[str, Any]) -> str:
    """The one coordinate an open, labeled issue names in its title; `ValueError` otherwise."""
    if issue.get("state") != "OPEN":
        raise ValueError(f"issue is {issue.get('state')}, not open")
    labels: set[str] = {label.get("name", "") for label in issue.get("labels", [])}
    if LABEL not in labels:
        raise ValueError(f"issue is not labeled {LABEL}")
    found: list[str] = sorted(set(COORDINATE_IN_TEXT.findall(issue.get("title", ""))))
    if len(found) != 1:
        raise ValueError(f"issue title must name exactly one group:artifact:version, found {found or 'none'}")
    return found[0]


def read_issue(number: int, cwd: str) -> dict[str, Any]:
    """The issue's title, state, and labels, read with `gh`."""
    completed: subprocess.CompletedProcess[str] = subprocess.run(
        ["gh", "issue", "view", str(number), "--repo", REPOSITORY, "--json", "title,state,labels"],
        cwd=cwd,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    if completed.returncode != 0:
        raise ValueError(f"cannot read issue #{number}: {completed.stderr.strip()}")
    return json.loads(completed.stdout)
