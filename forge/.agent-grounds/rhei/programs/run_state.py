# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Run state shared by every program state of the Forge workflow.

Programs own the checkpoints, the explore counters, and the loop guard; no
agent writes this file (§AR-forge-workflow-pipeline.6,
§FS-forge-issue-resolution-goal).
"""
from __future__ import annotations

import json
import os
from dataclasses import asdict, dataclass, field
from typing import Any

from utility_scripts.contribution_paths import contribution_paths, roots

from build_tools import changed_paths

RUNTIME_DIR: str = os.path.join("runtime", "forge")

ROUTE_NEW: str = "new"
ROUTE_UPDATE: str = "update"
ROUTE_REPAIR: str = "repair"


@dataclass
class Budgets:
    """The template's explore inputs (§AR-forge-workflow-pipeline.7)."""

    bulk_min_classes: int
    bulk_passes: int
    class_attempts: int
    defer_classes: int
    explore_passes: int


@dataclass
class Explore:
    """Where exploration stands; `measure` alone advances it."""

    mode: str = ""
    # The classes this run explores, fixed by `analyze`.
    targets: list[str] = field(default_factory=list)
    passes: int = 0
    bulk_passes: int = 0
    class_name: str | None = None
    class_attempt: int = 0
    covered_calls: int = 0
    total_calls: int = 0
    completed: list[str] = field(default_factory=list)
    exhausted: list[str] = field(default_factory=list)
    # The last explore commit every later reset returns to.
    checkpoint: str = ""
    # One line on the previous pass, for the next prompt of the same class.
    feedback: str = ""
    # Set when `analyze` defers exploration of a `fails-*` issue.
    deferred_classes: int | None = None


@dataclass
class Chunk:
    """The chunk state of an oversized dynamic-access issue (§FS-forge-chunked-dynamic-access)."""

    active: bool = False
    # Classes earlier chunks already completed, skipped, exhausted, or failed.
    processed: list[str] = field(default_factory=list)
    budget: int | None = None
    final: bool = True


@dataclass
class RunState:
    """Everything a later program needs to know about the run so far."""

    issue_number: int
    label: str
    route: str
    coordinate: str
    repo_checkout: str
    worktree: str
    branch: str
    # The upstream `master` the run sits on: pinned by `prepare`, moved by the rebase before the gate.
    base_commit: str
    # The latest checkpoint on the run branch.
    checkpoint: str
    started_at: str
    setup_agent: str
    worker_agent: str
    budgets: Budgets
    previous_coordinate: str | None = None
    # Covered and total dynamic-access calls of the previous version: its ratio is the explore goal
    # of every non-new route.
    goal_covered_calls: int | None = None
    goal_total_calls: int | None = None
    has_reporter_request: bool = False
    # The explicit metadata the reporter asked for (§FS-local-ci-equivalent-verification.1.1).
    requested_metadata: dict[str, list[str]] = field(default_factory=dict)
    setup_applied: bool = False
    # The run is the skip record of the setup verdict (§FS-unsupportable-version-diagnosis.3).
    skip_recorded: bool = False
    # The sender of the open fix request, for the loop guard (§AR-forge-workflow-pipeline.6).
    pending_request: str | None = None
    # The audit's checkpoint once sent, and whether it passed (§FS-local-branch-review.4).
    audit_checkpoint: str = ""
    audit_passed: bool = False
    explore: Explore = field(default_factory=Explore)
    chunk: Chunk = field(default_factory=Chunk)
    local_ci: dict[str, Any] | None = None

    @property
    def group(self) -> str:
        return self.coordinate.split(":")[0]

    @property
    def artifact(self) -> str:
        return self.coordinate.split(":")[1]

    @property
    def version(self) -> str:
        return self.coordinate.split(":")[2]

    @property
    def test_dir(self) -> str:
        """The coordinate's test project, relative to the repository root."""
        return os.path.join("tests", "src", self.group, self.artifact, self.version)

    @property
    def metadata_dir(self) -> str:
        return os.path.join("metadata", self.group, self.artifact, self.version)

    @property
    def index_path(self) -> str:
        return os.path.join("metadata", self.group, self.artifact, "index.json")

    @property
    def coordinate_paths(self) -> list[str]:
        """The coordinate's own roots, every version included, which resets and restores cover."""
        return roots(self.coordinate)

    def contribution_paths(self) -> list[str]:
        """What a checkpoint stages: the coordinate's roots and each admitted path outside them."""
        return contribution_paths(self.worktree, self.coordinate, self.base_commit,
                                  changed_paths(self.worktree, self.base_commit))

    def path(self, relative: str) -> str:
        return os.path.join(self.worktree, relative)


def runtime_path(workspace: str, *parts: str) -> str:
    """Return a path under the workspace's `runtime/forge/`, creating its parent."""
    path: str = os.path.join(workspace, RUNTIME_DIR, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    return path


def save(workspace: str, state: RunState) -> None:
    write_json(runtime_path(workspace, "state.json"), asdict(state))


def load(workspace: str) -> RunState:
    raw: dict[str, Any] = read_json(runtime_path(workspace, "state.json"))
    raw["budgets"] = Budgets(**raw["budgets"])
    raw["explore"] = Explore(**raw["explore"])
    raw["chunk"] = Chunk(**raw["chunk"])
    return RunState(**raw)


def write_json(path: str, value: Any) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(value, handle, indent=2, sort_keys=True)
        handle.write("\n")


def read_json(path: str) -> Any:
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def write_text(path: str, text: str) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(text)
