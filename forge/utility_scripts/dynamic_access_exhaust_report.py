# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Durable exhaust report for chunked dynamic-access issue runs."""

import json
import os
from dataclasses import dataclass, field
from typing import Any

from utility_scripts.metadata_index import coordinate_parts, resolve_test_dir


EXHAUST_REPORT_FILENAME = "dynamic-access-exhaust-report.json"


def dynamic_access_exhaust_report_path(repo_path: str, coordinate: str) -> str:
    """Return the coordinate-derived dynamic-access exhaust report path."""
    group, artifact, version = coordinate_parts(coordinate)
    if version is None:
        raise ValueError(f"Dynamic-access exhaust report requires versioned coordinate: {coordinate}")
    return os.path.join(resolve_test_dir(repo_path, group, artifact, version), EXHAUST_REPORT_FILENAME)


def find_dynamic_access_exhaust_report_path(repo_path: str, coordinate: str) -> str | None:
    """Return the coordinate-derived exhaust report path when it exists."""
    path = dynamic_access_exhaust_report_path(repo_path, coordinate)
    return path if os.path.isfile(path) else None


def resolve_workflow_exhaust_report(
        repo_path: str,
        issue_number: int | None,
        coordinate: str,
        chunk_class_count: int,
) -> tuple["DynamicAccessExhaustReport | None", str | None]:
    """Resolve or create the exhaust report for a chunked workflow invocation."""
    if chunk_class_count <= 0:
        return None, None

    report_path = dynamic_access_exhaust_report_path(repo_path, coordinate)
    if os.path.isfile(report_path):
        return DynamicAccessExhaustReport.load(report_path), report_path

    return (
        DynamicAccessExhaustReport.create(coordinate=coordinate, issue_number=issue_number),
        report_path,
    )


@dataclass
class DynamicAccessExhaustReport:
    """Minimal resumable state for one chunked dynamic-access issue.

    The report is intentionally not a precomputed chunk manifest. Every resume
    regenerates the current dynamic-access report and filters out these recorded
    class sets (§WF-dynamic-access-exhaust-report).
    """

    coordinate: str
    issue_number: int | None
    class_threshold: int | None = None
    current_chunk_class_count: int | None = None
    completed_classes: list[str] = field(default_factory=list)
    skipped_classes: list[str] = field(default_factory=list)
    exhausted_classes: list[str] = field(default_factory=list)
    failed_classes: list[str] = field(default_factory=list)
    latest_chunk_pull_request: int | None = None
    latest_chunk_commit: str | None = None

    @classmethod
    def create(
            cls,
            coordinate: str,
            issue_number: int | None,
    ) -> "DynamicAccessExhaustReport":
        """Create an empty exhaust report for a chunked issue."""
        return cls(coordinate=coordinate, issue_number=issue_number)

    @classmethod
    def from_dict(cls, payload: dict[str, Any]) -> "DynamicAccessExhaustReport":
        """Build an exhaust report from JSON."""
        return cls(
            coordinate=str(payload["coordinate"]),
            issue_number=_optional_int(payload.get("issueNumber")),
            class_threshold=_optional_int(payload.get("classThreshold")),
            current_chunk_class_count=_optional_int(payload.get("currentChunkClassCount")),
            completed_classes=list(payload.get("completedClasses", [])),
            skipped_classes=list(payload.get("skippedClasses", [])),
            exhausted_classes=list(payload.get("exhaustedClasses", [])),
            failed_classes=list(payload.get("failedClasses", [])),
            latest_chunk_pull_request=_optional_int(payload.get("latestChunkPullRequest")),
            latest_chunk_commit=payload.get("latestChunkCommit"),
        )

    @classmethod
    def load(cls, path: str) -> "DynamicAccessExhaustReport":
        """Load an exhaust report from JSON."""
        with open(path, "r", encoding="utf-8") as report_file:
            return cls.from_dict(json.load(report_file))

    def to_dict(self) -> dict[str, Any]:
        """Return a JSON-serializable exhaust report payload."""
        return {
            "coordinate": self.coordinate,
            "issueNumber": self.issue_number,
            "classThreshold": self.class_threshold,
            "currentChunkClassCount": self.current_chunk_class_count,
            "completedClasses": self.completed_classes,
            "skippedClasses": self.skipped_classes,
            "exhaustedClasses": self.exhausted_classes,
            "failedClasses": self.failed_classes,
            "latestChunkPullRequest": self.latest_chunk_pull_request,
            "latestChunkCommit": self.latest_chunk_commit,
        }

    def save(self, path: str) -> None:
        """Write the exhaust report to JSON."""
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as report_file:
            json.dump(self.to_dict(), report_file, indent=2, sort_keys=True)
            report_file.write("\n")

    def default_path(self, repo_path: str) -> str:
        """Return the coordinate-derived path below a reachability checkout."""
        return dynamic_access_exhaust_report_path(repo_path, self.coordinate)

    def processed_classes(self) -> set[str]:
        """Return all classes a resumed chunk must skip."""
        processed = set(self.completed_classes)
        processed.update(self.skipped_classes)
        processed.update(self.exhausted_classes)
        processed.update(self.failed_classes)
        return processed

    def processed_class_count(self) -> int:
        """Return the number of classes recorded as processed."""
        return len(self.processed_classes())

    def mark_completed(self, class_name: str) -> None:
        """Record a completed class and remove it from non-completed sets."""
        self.completed_classes = _append_unique(self.completed_classes, class_name)
        self.skipped_classes = [value for value in self.skipped_classes if value != class_name]
        self.exhausted_classes = [value for value in self.exhausted_classes if value != class_name]
        self.failed_classes = [value for value in self.failed_classes if value != class_name]

    def mark_skipped(self, class_name: str) -> None:
        """Record a skipped class."""
        if class_name not in self.completed_classes:
            self.skipped_classes = _append_unique(self.skipped_classes, class_name)

    def mark_exhausted(self, class_name: str) -> None:
        """Record an exhausted class."""
        if class_name not in self.completed_classes:
            self.exhausted_classes = _append_unique(self.exhausted_classes, class_name)

    def mark_failed(self, class_name: str) -> None:
        """Record a failed class."""
        self.failed_classes = _append_unique(self.failed_classes, class_name)

    def update_chunk_limits(self, class_threshold: int, current_chunk_class_count: int) -> None:
        """Record the dispatcher-selected threshold and concrete chunk size."""
        self.class_threshold = int(class_threshold)
        self.current_chunk_class_count = int(current_chunk_class_count)

    def record_published_chunk(self, commit: str, pr_number: int | None) -> None:
        """Record the latest published chunk PR and commit."""
        self.latest_chunk_commit = commit
        self.latest_chunk_pull_request = pr_number


def _append_unique(values: list[str], value: str) -> list[str]:
    if value in values:
        return values
    return [*values, value]


def _optional_int(value: Any) -> int | None:
    if value is None:
        return None
    return int(value)
