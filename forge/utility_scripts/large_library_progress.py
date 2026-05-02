# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import glob
import json
import os
import re
import shutil
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any


RUN_STATUS_CHUNK_READY = "chunk_ready"

LABEL_LARGE_LIBRARY_SERIES = "large-library-series"
LABEL_LARGE_LIBRARY_NEXT_PART = "large-library-next-part"
LABEL_LARGE_LIBRARY_BLOCKED = "large-library-blocked"
LABEL_LARGE_LIBRARY_PART = "large-library-part"

LARGE_LIBRARY_PROGRESS_DIR = "large_library_series"
PROGRESS_STATE_FILENAME = "state.json"


def utc_now_iso() -> str:
    """Return an ISO-8601 UTC timestamp."""
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def sanitize_segment(value: str) -> str:
    """Return a filesystem and branch friendly segment."""
    return re.sub(r"[^A-Za-z0-9._-]+", "-", value).strip("-._") or "unknown"


def build_series_id(coordinate: str, issue_number: int | None) -> str:
    """Build a stable large-library series ID."""
    suffix = "manual" if issue_number is None else str(issue_number)
    return f"{sanitize_segment(coordinate)}-{suffix}"


def progress_state_dir(metrics_repo_root: str, issue_number: int | None, series_id: str) -> str:
    """Return the directory that stores a durable large-library state artifact."""
    issue_segment = "manual" if issue_number is None else f"issue-{issue_number}"
    return os.path.join(metrics_repo_root, LARGE_LIBRARY_PROGRESS_DIR, issue_segment, sanitize_segment(series_id))


def progress_state_path(metrics_repo_root: str, issue_number: int | None, series_id: str) -> str:
    """Return the default JSON state path for a large-library series."""
    return os.path.join(progress_state_dir(metrics_repo_root, issue_number, series_id), PROGRESS_STATE_FILENAME)


def find_progress_state_path(metrics_repo_root: str, issue_number: int) -> str | None:
    """Return the most recently modified state path for an issue, if any."""
    pattern = os.path.join(metrics_repo_root, LARGE_LIBRARY_PROGRESS_DIR, f"issue-{issue_number}", "*", PROGRESS_STATE_FILENAME)
    matches = [path for path in glob.glob(pattern) if os.path.isfile(path)]
    if not matches:
        return None
    return max(matches, key=os.path.getmtime)


def resolve_workflow_progress_state(
        metrics_repo_root: str,
        issue_number: int | None,
        coordinate: str,
        request_label: str,
        strategy_name: str,
        large_library_series: bool,
        resume_artifact: str | None = None,
) -> tuple["LargeLibraryProgressState | None", str | None]:
    """Resolve the progress state used by a workflow invocation."""
    if resume_artifact:
        state = LargeLibraryProgressState.load(resume_artifact)
        return state, state.default_path(metrics_repo_root)

    if not large_library_series:
        return None, None

    if issue_number is not None:
        state_path = find_progress_state_path(metrics_repo_root, issue_number)
        if state_path is not None:
            return LargeLibraryProgressState.load(state_path), state_path

    state = LargeLibraryProgressState.create(
        coordinate=coordinate,
        issue_number=issue_number,
        request_label=request_label,
        strategy_name=strategy_name,
    )
    return state, state.default_path(metrics_repo_root)


def copy_progress_artifacts(source_metrics_root: str, destination_metrics_root: str, issue_number: int) -> None:
    """Copy large-library progress artifacts out of a scratch metrics root before cleanup."""
    source_dir = os.path.join(source_metrics_root, LARGE_LIBRARY_PROGRESS_DIR, f"issue-{issue_number}")
    if not os.path.isdir(source_dir):
        return
    destination_dir = os.path.join(destination_metrics_root, LARGE_LIBRARY_PROGRESS_DIR, f"issue-{issue_number}")
    os.makedirs(os.path.dirname(destination_dir), exist_ok=True)
    shutil.copytree(source_dir, destination_dir, dirs_exist_ok=True)


@dataclass
class LargeLibraryProgressState:
    schema_version: int
    coordinate: str
    issue_number: int | None
    request_label: str
    series_id: str
    strategy_name: str
    part: int = 1
    base_branch: str = "master"
    last_published_branch: str | None = None
    last_published_commit: str | None = None
    scaffold_commit: str | None = None
    baseline_stats: dict[str, Any] | None = None
    baseline_metadata_entries: int | None = None
    baseline_test_only_metadata_entries: int | None = None
    class_order: list[str] = field(default_factory=list)
    completed_classes: list[str] = field(default_factory=list)
    exhausted_classes: list[str] = field(default_factory=list)
    failed_classes: list[str] = field(default_factory=list)
    covered_calls: int = 0
    total_calls: int = 0
    dynamic_access_report_sha256: str | None = None
    created_pull_requests: list[int] = field(default_factory=list)
    updated_at: str = field(default_factory=utc_now_iso)

    @classmethod
    def create(
            cls,
            coordinate: str,
            issue_number: int | None,
            request_label: str,
            strategy_name: str,
            base_branch: str = "master",
    ) -> "LargeLibraryProgressState":
        """Create a new progress state with a stable series ID."""
        return cls(
            schema_version=1,
            coordinate=coordinate,
            issue_number=issue_number,
            request_label=request_label,
            series_id=build_series_id(coordinate, issue_number),
            strategy_name=strategy_name,
            base_branch=base_branch,
        )

    @classmethod
    def from_dict(cls, payload: dict[str, Any]) -> "LargeLibraryProgressState":
        """Build a progress state from a JSON payload."""
        return cls(
            schema_version=int(payload.get("schemaVersion", 1)),
            coordinate=str(payload["coordinate"]),
            issue_number=_optional_int(payload.get("issueNumber")),
            request_label=str(payload.get("requestLabel", "")),
            series_id=str(payload.get("seriesId", "")),
            strategy_name=str(payload.get("strategyName", "")),
            part=int(payload.get("part", 1)),
            base_branch=str(payload.get("baseBranch", "master")),
            last_published_branch=payload.get("lastPublishedBranch"),
            last_published_commit=payload.get("lastPublishedCommit"),
            scaffold_commit=payload.get("scaffoldCommit"),
            baseline_stats=payload.get("baselineStats"),
            baseline_metadata_entries=payload.get("baselineMetadataEntries"),
            baseline_test_only_metadata_entries=payload.get("baselineTestOnlyMetadataEntries"),
            class_order=list(payload.get("classOrder", [])),
            completed_classes=list(payload.get("completedClasses", [])),
            exhausted_classes=list(payload.get("exhaustedClasses", [])),
            failed_classes=list(payload.get("failedClasses", [])),
            covered_calls=int(payload.get("coveredCalls", 0)),
            total_calls=int(payload.get("totalCalls", 0)),
            dynamic_access_report_sha256=payload.get("dynamicAccessReportSha256"),
            created_pull_requests=[int(value) for value in payload.get("createdPullRequests", [])],
            updated_at=str(payload.get("updatedAt", utc_now_iso())),
        )

    @classmethod
    def load(cls, path: str) -> "LargeLibraryProgressState":
        """Load progress state from a JSON artifact."""
        with open(path, "r", encoding="utf-8") as state_file:
            return cls.from_dict(json.load(state_file))

    def to_dict(self) -> dict[str, Any]:
        """Return a JSON-serializable progress state payload."""
        return {
            "schemaVersion": self.schema_version,
            "coordinate": self.coordinate,
            "issueNumber": self.issue_number,
            "requestLabel": self.request_label,
            "seriesId": self.series_id,
            "strategyName": self.strategy_name,
            "part": self.part,
            "baseBranch": self.base_branch,
            "lastPublishedBranch": self.last_published_branch,
            "lastPublishedCommit": self.last_published_commit,
            "scaffoldCommit": self.scaffold_commit,
            "baselineStats": self.baseline_stats,
            "baselineMetadataEntries": self.baseline_metadata_entries,
            "baselineTestOnlyMetadataEntries": self.baseline_test_only_metadata_entries,
            "classOrder": self.class_order,
            "completedClasses": self.completed_classes,
            "exhaustedClasses": self.exhausted_classes,
            "failedClasses": self.failed_classes,
            "coveredCalls": self.covered_calls,
            "totalCalls": self.total_calls,
            "dynamicAccessReportSha256": self.dynamic_access_report_sha256,
            "createdPullRequests": self.created_pull_requests,
            "updatedAt": self.updated_at,
        }

    def save(self, path: str) -> None:
        """Write progress state to a JSON artifact."""
        self.updated_at = utc_now_iso()
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as state_file:
            json.dump(self.to_dict(), state_file, indent=2, sort_keys=True)
            state_file.write("\n")

    def default_path(self, metrics_repo_root: str) -> str:
        """Return the default state path below a metrics root."""
        return progress_state_path(metrics_repo_root, self.issue_number, self.series_id)

    def mark_class_order(self, class_names: list[str]) -> None:
        """Record the first observed class order without overwriting resumed state."""
        if not self.class_order:
            self.class_order = list(class_names)

    def mark_completed(self, class_name: str) -> None:
        """Record a completed class and remove it from failed/exhausted sets."""
        self.completed_classes = _append_unique(self.completed_classes, class_name)
        self.exhausted_classes = [value for value in self.exhausted_classes if value != class_name]
        self.failed_classes = [value for value in self.failed_classes if value != class_name]

    def mark_exhausted(self, class_name: str) -> None:
        """Record a class that should not be retried on continuation."""
        if class_name not in self.completed_classes:
            self.exhausted_classes = _append_unique(self.exhausted_classes, class_name)

    def mark_failed(self, class_name: str) -> None:
        """Record a failed class for later diagnostics."""
        self.failed_classes = _append_unique(self.failed_classes, class_name)

    def update_coverage(self, covered_calls: int, total_calls: int) -> None:
        """Record aggregate dynamic-access coverage."""
        self.covered_calls = int(covered_calls)
        self.total_calls = int(total_calls)

    def record_published_pr(self, branch: str, commit: str, pr_number: int | None) -> None:
        """Record the branch, commit, and optional PR number for the latest published part."""
        self.last_published_branch = branch
        self.last_published_commit = commit
        if pr_number is not None:
            self.created_pull_requests = _append_unique_int(self.created_pull_requests, pr_number)

    def advance_to_next_part(self) -> None:
        """Advance state to the next serial PR part."""
        self.part += 1


def _append_unique(values: list[str], value: str) -> list[str]:
    if value in values:
        return values
    return [*values, value]


def _append_unique_int(values: list[int], value: int) -> list[int]:
    if value in values:
        return values
    return [*values, value]


def _optional_int(value: Any) -> int | None:
    if value is None:
        return None
    return int(value)
