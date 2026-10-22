# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Fetch the Forge issue queues with `gh issue list`, every cycle, with no cache.

One query per label and priority tier, `high-priority` first, then `priority`,
then the rest, oldest first, unassigned, and never `not-for-native-image`
(§AR-forge-workflow-pipeline.1, §FS-forge-scope). A fixture file of issue
payloads can stand in for GitHub.
"""
from __future__ import annotations

import json
import subprocess
from typing import Any

from dispatcher.config import (
    LABEL_HIGH_PRIORITY,
    LABEL_JAVA_RUN_FAIL,
    LABEL_JAVAC_FAIL,
    LABEL_LIBRARY_NEW,
    LABEL_LIBRARY_UPDATE,
    LABEL_NI_RUN_FAIL,
    LABEL_NOT_FOR_NATIVE_IMAGE,
    LABEL_PRIORITY,
    REPO,
)

QUEUE_LABELS: tuple[str, ...] = (
    LABEL_LIBRARY_NEW,
    LABEL_LIBRARY_UPDATE,
    LABEL_JAVAC_FAIL,
    LABEL_JAVA_RUN_FAIL,
    LABEL_NI_RUN_FAIL,
)
# Each tier excludes the labels of the tiers above it, so an issue is served once, in its highest tier.
TIERS: tuple[tuple[tuple[str, ...], tuple[str, ...]], ...] = (
    ((LABEL_HIGH_PRIORITY,), ()),
    ((LABEL_PRIORITY,), (LABEL_HIGH_PRIORITY,)),
    ((), (LABEL_HIGH_PRIORITY, LABEL_PRIORITY)),
)
FETCH_LIMIT: int = 20
FIELDS: str = "number,title,labels,assignees"


def fetch_command(label: str, extra_labels: tuple[str, ...], excluded: tuple[str, ...]) -> list[str]:
    """The one `gh issue list` of a label and tier."""
    search: list[str] = ["no:assignee", f"-label:{LABEL_NOT_FOR_NATIVE_IMAGE}"]
    search += [f"-label:{name}" for name in excluded]
    search.append("sort:created-asc")
    command: list[str] = ["gh", "issue", "list", "--repo", REPO, "--state", "open", "--label", label]
    for name in extra_labels:
        command += ["--label", name]
    return [*command, "--search", " ".join(search), "--json", FIELDS, "--limit", str(FETCH_LIMIT)]


def fetch_commands(labels: tuple[str, ...]) -> list[tuple[str, list[str]]]:
    """Every query of one cycle, most urgent first, as `(label, command)`."""
    return [(label, fetch_command(label, extra, excluded)) for extra, excluded in TIERS for label in labels]


def _run(command: list[str]) -> list[dict[str, Any]]:
    completed: subprocess.CompletedProcess[str] = subprocess.run(
        command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
    )
    if completed.returncode != 0:
        raise RuntimeError(f"cannot list issues with {' '.join(command)}: {completed.stderr.strip()}")
    return json.loads(completed.stdout)


def fetch_candidates(labels: tuple[str, ...], fixture: str | None = None) -> list[tuple[str, dict[str, Any]]]:
    """The candidate issues of one cycle in claim order, as `(queue label, payload)`, each once."""
    if fixture is not None:
        with open(fixture, encoding="utf-8") as handle:
            issues: list[dict[str, Any]] = json.load(handle)
        return [(label, issue) for label in labels for issue in issues
                if label in {entry.get("name") for entry in issue.get("labels", [])}]
    seen: set[int] = set()
    candidates: list[tuple[str, dict[str, Any]]] = []
    for label, command in fetch_commands(labels):
        for issue in _run(command):
            if issue["number"] not in seen:
                seen.add(issue["number"])
                candidates.append((label, issue))
    return candidates
