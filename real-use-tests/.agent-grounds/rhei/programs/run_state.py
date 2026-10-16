# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Run state shared by every program state of the real-use workflow.

Programs own the checkpoints and the removal record; no agent writes this
file (§AR-real-use-test-workflow.2, §FS-real-use-test-generation).
"""
from __future__ import annotations

import json
import os
from dataclasses import asdict, dataclass, field
from typing import Any

RUNTIME_DIR: str = os.path.join("runtime", "real-use")
TEST_LOCAL_METADATA: str = os.path.join(
    "src", "test", "resources", "META-INF", "native-image", "reachability-metadata.json"
)
TEST_JAVA_DIR: str = os.path.join("src", "test", "java")
USER_CODE_FILTER: str = "user-code-filter.json"


@dataclass
class RunState:
    """Everything a later program needs to know about the run so far."""

    coordinate: str
    # The real-use-test issue the run resolves (§FS-real-use-test-generation.1).
    issue_number: int
    repo_checkout: str
    worktree: str
    branch: str
    # The latest checkpoint; preflight's scope and the metadata reset measure against it.
    base_commit: str
    scenario_passes: int
    # UTC start of the run: the publication's durable identity (§AR-real-use-test-workflow.6).
    started_at: str
    removals: list[dict[str, str]] = field(default_factory=list)

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
    def project_dir(self) -> str:
        """The coordinate project, relative to the repository root (§FS-real-use-tests.1)."""
        return os.path.join("real-use-tests", "src", self.group, self.artifact, self.version)

    @property
    def project_path(self) -> str:
        return os.path.join(self.worktree, self.project_dir)

    @property
    def test_java_path(self) -> str:
        return os.path.join(self.project_path, TEST_JAVA_DIR)

    @property
    def metadata_path(self) -> str:
        return os.path.join(self.project_path, TEST_LOCAL_METADATA)

    @property
    def metadata_repo_path(self) -> str:
        return os.path.join(self.project_dir, TEST_LOCAL_METADATA)

    @property
    def filter_path(self) -> str:
        return os.path.join(self.project_path, USER_CODE_FILTER)

    def record_removal(self, scenario: str, reason: str) -> None:
        """Remember one removed scenario for the pull-request description.

        §FS-real-use-test-generation.2.6
        """
        self.removals.append({"scenario": scenario, "reason": reason})


def runtime_path(workspace: str, *parts: str) -> str:
    """Return a path under the workspace's `runtime/real-use/`, creating its parent."""
    path: str = os.path.join(workspace, RUNTIME_DIR, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    return path


def save(workspace: str, state: RunState) -> None:
    path: str = runtime_path(workspace, "state.json")
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(asdict(state), handle, indent=2, sort_keys=True)
        handle.write("\n")


def load(workspace: str) -> RunState:
    path: str = runtime_path(workspace, "state.json")
    with open(path, encoding="utf-8") as handle:
        raw: dict[str, Any] = json.load(handle)
    return RunState(**raw)


def write_json(path: str, value: Any) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(value, handle, indent=2, sort_keys=True)
        handle.write("\n")


def read_json(path: str) -> Any:
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)
