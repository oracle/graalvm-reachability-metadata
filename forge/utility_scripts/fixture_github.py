# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Local GitHub issue fixtures for hermetic Forge runs.

The module models fixture-backed GitHub state without importing
`forge_metadata.py`. §AR-forge-control-plane

Fixture file discovery, parsing, and validation live in
`fixture_github_loader.py`, whose issue shapes are re-exported here.
"""

from __future__ import annotations

import json
import os
import re
import shutil
import sys
from datetime import datetime, timezone
from typing import Any

from utility_scripts.fixture_github_loader import (
    ALLOWED_ISSUE_STATES,
    ALLOWED_PROJECT_STATUSES,
    FixtureComment,
    FixtureIssue,
    FixtureIssueNotFoundError,
    FixtureValidationError,
    JsonObject,
    LABEL_JAVAC_FAIL,
    LABEL_JAVA_RUN_FAIL,
    LABEL_LIBRARY_NEW,
    LABEL_NI_RUN_FAIL,
    default_fixture_dir,
    discover_fixture_paths,
    load_fixture_issues,
    normalize_fixture_issue,
    _require_non_empty_text,
)


class FixtureGitHubState:
    """Mutable in-memory GitHub fixture state for `forge_metadata.py` wiring."""

    def __init__(self, issues: list[FixtureIssue]) -> None:
        self._issues: dict[int, FixtureIssue] = {}
        self._issue_order: list[int] = []
        self._item_to_issue_number: dict[str, int] = {}

        for issue in issues:
            if issue.number in self._issues:
                raise FixtureValidationError(f"Duplicate fixture issue number: {issue.number}")
            if issue.project_item_id in self._item_to_issue_number:
                raise FixtureValidationError(
                    f"Duplicate fixture project item id: {issue.project_item_id}"
                )
            self._issues[issue.number] = issue
            self._issue_order.append(issue.number)
            self._item_to_issue_number[issue.project_item_id] = issue.number

    @property
    def issue_numbers(self) -> list[int]:
        return list(self._issue_order)

    def get_issue_by_number(self, issue_number: int) -> JsonObject:
        """Return a `gh issue view`-shaped payload for one fixture issue."""
        return self._issue(issue_number).issue_view_payload(include_state=False)

    def get_issue_claim_payload(self, issue_number: int) -> JsonObject:
        """Return the mutable issue state shape used immediately before claiming."""
        return self._issue(issue_number).issue_view_payload(include_state=True)

    def get_issue_search_payload(self, issue_number: int) -> JsonObject:
        """Return a GitHub Search API-normalized issue payload."""
        return self._issue(issue_number).issue_search_payload()

    def get_issue_body(self, issue_number: int) -> str:
        return self._issue(issue_number).body

    def get_issue_fixture_path(self, issue_number: int) -> str:
        return self._issue(issue_number).fixture_path

    def get_issue_project_number(self, issue_number: int) -> int:
        return self._issue(issue_number).project_number

    def get_issue_project_item_id(self, issue_number: int) -> str:
        return self._issue(issue_number).project_item_id

    def list_open_issues_by_label(
            self,
            label: str,
            limit: int,
            offset: int = 0,
            extra_labels: list[str] | None = None,
            excluded_labels: list[str] | None = None,
            excluded_authors: tuple[str, ...] = (),
    ) -> list[JsonObject]:
        """Return `gh issue list`/search-shaped open issue payloads."""
        if limit <= 0:
            return []
        matched = [
            issue.issue_search_payload()
            for issue in self._iter_open_issues()
            if _issue_has_all_labels(issue, [label, *(extra_labels or [])])
            and not _issue_has_any_label(issue, excluded_labels or [])
            and issue.author not in excluded_authors
        ]
        return matched[offset:offset + limit]

    def count_open_issues_by_label(
            self,
            label: str,
            extra_labels: list[str] | None = None,
            excluded_labels: list[str] | None = None,
            excluded_authors: tuple[str, ...] = (),
    ) -> int:
        return len(self.list_open_issues_by_label(
            label,
            limit=len(self._issue_order),
            offset=0,
            extra_labels=extra_labels,
            excluded_labels=excluded_labels,
            excluded_authors=excluded_authors,
        ))

    def get_issue_labels(self, issue_number: int) -> list[str]:
        return list(self._issue(issue_number).labels)

    def add_issue_label(self, issue_number: int, label_name: str) -> None:
        _require_non_empty_text(label_name, "label_name")
        issue = self._issue(issue_number)
        if label_name in issue.labels:
            return
        issue.labels.append(label_name)

    def remove_issue_label(self, issue_number: int, label_name: str) -> None:
        _require_non_empty_text(label_name, "label_name")
        issue = self._issue(issue_number)
        if label_name not in issue.labels:
            return
        issue.labels = [label for label in issue.labels if label != label_name]

    def close_issue(self, issue_number: int) -> None:
        """Close a fixture issue so later fixture scans stop selecting it."""
        self._issue(issue_number).state = "CLOSED"

    def get_issue_assignees(self, issue_number: int) -> list[str]:
        return list(self._issue(issue_number).assignees)

    def set_issue_assignee(self, issue_number: int, username: str) -> None:
        _require_non_empty_text(username, "username")
        issue = self._issue(issue_number)
        issue.assignees = [username]

    def clear_issue_assignees(self, issue_number: int) -> None:
        issue = self._issue(issue_number)
        if not issue.assignees:
            return
        issue.assignees = []

    def get_issue_comments(self, issue_number: int) -> list[JsonObject]:
        return [comment.to_github_payload() for comment in self._issue(issue_number).comments]

    def post_issue_comment(self, issue_number: int, body: str, author: str = "fixture-runner") -> None:
        _require_non_empty_text(author, "author")
        _require_non_empty_text(body, "body")
        issue = self._issue(issue_number)
        comment = FixtureComment(author=author, body=body, created_at=_utc_timestamp())
        issue.comments.append(comment)

    def get_project_item_state(self, issue_number: int) -> tuple[str, str]:
        issue = self._issue(issue_number)
        return issue.project_item_id, issue.project_status

    def get_item_status(self, item_id: str) -> str:
        issue = self._issue_by_item_id(item_id)
        return issue.project_status

    def set_item_status(self, item_id: str, status: str) -> None:
        _validate_project_status(status, item_id)
        issue = self._issue_by_item_id(item_id)
        issue.project_status = status

    def set_project_status(self, issue_number: int, status: str) -> None:
        issue = self._issue(issue_number)
        self.set_item_status(issue.project_item_id, status)

    def get_open_blocking_issue_numbers(self, issue_number: int) -> list[int]:
        return list(self._issue(issue_number).blockers)

    def set_open_blocking_issue_numbers(self, issue_number: int, blockers: list[int]) -> None:
        issue = self._issue(issue_number)
        issue.blockers = _normalize_blocker_numbers(blockers, f"issue #{issue_number} blockers")

    def add_open_blocker(self, issue_number: int, blocker_issue_number: int) -> None:
        if not _is_int(blocker_issue_number):
            raise FixtureValidationError("blocker_issue_number must be an integer")
        issue = self._issue(issue_number)
        if blocker_issue_number in issue.blockers:
            return
        issue.blockers.append(blocker_issue_number)

    def remove_open_blocker(self, issue_number: int, blocker_issue_number: int) -> None:
        if not _is_int(blocker_issue_number):
            raise FixtureValidationError("blocker_issue_number must be an integer")
        issue = self._issue(issue_number)
        if blocker_issue_number not in issue.blockers:
            return
        issue.blockers = [blocker for blocker in issue.blockers if blocker != blocker_issue_number]

    def prepare_issue_worktree(self, issue_number: int, label: str, worktree_path: str) -> None:
        """Apply fixture-only repository masking inside an isolated issue worktree."""
        issue = self._issue(issue_number)
        if label not in issue.labels:
            raise FixtureValidationError(
                f"Fixture issue #{issue.number} was run as `{label}`, but its labels are "
                f"{sorted(issue.labels)}"
            )
        if label == LABEL_LIBRARY_NEW:
            self._prepare_new_library_worktree(issue, label, worktree_path)
        elif label in {LABEL_JAVAC_FAIL, LABEL_JAVA_RUN_FAIL, LABEL_NI_RUN_FAIL}:
            self._prepare_version_failure_worktree(issue, label, worktree_path)
        else:
            _log_fixture_setup(
                f"Fixture issue #{issue.number}: no issue-specific workspace cleanup requested."
            )
        self._prepare_worktree_files(issue, worktree_path)
        self._prepare_continuation_marker(issue, worktree_path)

    def _iter_open_issues(self) -> list[FixtureIssue]:
        return [
            self._issues[issue_number]
            for issue_number in self._issue_order
            if self._issues[issue_number].state == "OPEN"
        ]

    def _issue(self, issue_number: int) -> FixtureIssue:
        issue = self._issues.get(issue_number)
        if issue is None:
            raise FixtureIssueNotFoundError(f"Unknown fixture issue number: {issue_number}")
        return issue

    def _issue_by_item_id(self, item_id: str) -> FixtureIssue:
        issue_number = self._item_to_issue_number.get(item_id)
        if issue_number is None:
            raise FixtureIssueNotFoundError(f"Unknown fixture project item id: {item_id}")
        return self._issue(issue_number)

    def _prepare_new_library_worktree(
            self,
            issue: FixtureIssue,
            label: str,
            worktree_path: str,
    ) -> None:
        coordinate = _extract_coordinate_parts(issue.title)
        if coordinate is None:
            raise FixtureValidationError(
                f"Fixture issue #{issue.number} has no Maven coordinates in the title"
            )
        group, artifact, version = coordinate
        requested_coordinates = f"{group}:{artifact}:{version}"
        index_json_path = os.path.join(worktree_path, "metadata", group, artifact, "index.json")
        if not os.path.isfile(index_json_path):
            _log_fixture_setup(
                f"Fixture issue #{issue.number}: no existing metadata index to clean for "
                f"{requested_coordinates}."
            )
            return

        index_entries = _load_index_entries(index_json_path)
        removed_entries = [
            dict(entry)
            for entry in index_entries
            if isinstance(entry, dict) and _index_entry_matches_version(entry, version)
        ]
        if not removed_entries:
            _log_fixture_setup(
                f"Fixture issue #{issue.number}: no existing index entry to clean for "
                f"{requested_coordinates}."
            )
            return

        remaining_entries = [
            entry
            for entry in index_entries
            if isinstance(entry, dict) and not _index_entry_matches_version(entry, version)
        ]
        _write_index_entries(index_json_path, remaining_entries)

        cleaned_paths = _clean_version_paths(worktree_path, group, artifact, version)
        _log_fixture_setup(
            f"Fixture issue #{issue.number}: cleaned existing {requested_coordinates} "
            "from the isolated worktree for library-new-request. "
            f"Index={_repo_relative_path(index_json_path, worktree_path)}, "
            f"version paths={_format_cleaned_path_summary(cleaned_paths)}."
        )

    def _prepare_version_failure_worktree(
            self,
            issue: FixtureIssue,
            label: str,
            worktree_path: str,
    ) -> None:
        """Mask the requested version so a failure fixture replays a current -> new upgrade.

        Shared by `fails-javac-compile`, `fails-java-run`, and `fails-native-image-run`:
        all three model a scheduled-compatibility failure where the requested version is
        not yet supported and `latest` resolves to the previous tested version.
        """
        coordinate = _extract_coordinate_parts(issue.title)
        if coordinate is None:
            raise FixtureValidationError(
                f"Fixture issue #{issue.number} has no Maven coordinates in the title"
        )
        group, artifact, new_version = coordinate
        requested_coordinates = f"{group}:{artifact}:{new_version}"
        previous_coordinates = _previous_issue_coordinates(issue, group, artifact, new_version)
        if previous_coordinates is None:
            raise FixtureValidationError(
                f"Fixture issue #{issue.number} must include a reproducer with the previous "
                f"{group}:{artifact} coordinate."
            )
        _, _, previous_version = _split_maven_coordinate(previous_coordinates)

        index_json_path = os.path.join(worktree_path, "metadata", group, artifact, "index.json")
        if not os.path.isfile(index_json_path):
            raise FixtureValidationError(
                f"Fixture issue #{issue.number} is missing metadata index: {index_json_path}"
            )
        index_entries = _load_index_entries(index_json_path)
        remaining_entries = [
            entry
            for entry in index_entries
            if isinstance(entry, dict) and not _index_entry_matches_version(entry, new_version)
        ]
        if len(remaining_entries) == len(index_entries):
            raise FixtureValidationError(
                f"Fixture issue #{issue.number} could not find index entry for "
                f"requested version {new_version}"
            )

        previous_entry: JsonObject | None = None
        for entry in remaining_entries:
            if isinstance(entry, dict) and _index_entry_matches_version(entry, previous_version):
                previous_entry = entry
                break
        if previous_entry is None:
            raise FixtureValidationError(
                f"Fixture issue #{issue.number} could not find previous index entry for "
                f"{previous_coordinates}"
            )

        for entry in remaining_entries:
            if isinstance(entry, dict):
                entry.pop("latest", None)
        previous_entry["latest"] = True
        _write_index_entries(index_json_path, remaining_entries)

        cleaned_paths = _clean_version_paths(worktree_path, group, artifact, new_version)
        _log_fixture_setup(
            f"Fixture issue #{issue.number}: cleaned requested version {requested_coordinates} "
            f"from the isolated worktree; latest now resolves to {previous_coordinates}. "
            f"Index={_repo_relative_path(index_json_path, worktree_path)}, "
            f"version paths={_format_cleaned_path_summary(cleaned_paths)}."
        )

    def _prepare_continuation_marker(self, issue: FixtureIssue, worktree_path: str) -> None:
        """Write the fixture-provided run-continuation marker into the worktree."""
        if issue.continuation_marker is None:
            return
        marker_path = os.path.join(worktree_path, "forge", ".continuation-marker.json")
        os.makedirs(os.path.dirname(marker_path), exist_ok=True)
        with open(marker_path, "w", encoding="utf-8") as marker_file:
            json.dump(issue.continuation_marker, marker_file, indent=2, sort_keys=True)
            marker_file.write("\n")
        _log_fixture_setup(
            f"Fixture issue #{issue.number}: wrote continuation marker "
            f"{_repo_relative_path(marker_path, worktree_path)}."
        )

    def _prepare_worktree_files(self, issue: FixtureIssue, worktree_path: str) -> None:
        """Write fixture-declared files into the isolated issue worktree."""
        for relative_path, content in issue.worktree_files.items():
            normalized_path = os.path.normpath(relative_path)
            if os.path.isabs(normalized_path) or normalized_path == ".." or normalized_path.startswith("../"):
                raise FixtureValidationError(
                    f"Fixture issue #{issue.number}: invalid worktree file path: {relative_path}"
                )
            target_path = os.path.join(worktree_path, normalized_path)
            os.makedirs(os.path.dirname(target_path), exist_ok=True)
            with open(target_path, "w", encoding="utf-8") as worktree_file:
                worktree_file.write(content)
            _log_fixture_setup(
                f"Fixture issue #{issue.number}: wrote worktree file "
                f"{_repo_relative_path(target_path, worktree_path)}."
            )


def load_fixture_github_state(fixture_paths: list[str] | None = None) -> FixtureGitHubState:
    """Load all requested YAML fixtures into mutable fixture GitHub state."""
    issues: list[FixtureIssue] = load_fixture_issues(fixture_paths)
    if not issues:
        raise FixtureValidationError("No GitHub issue fixtures were loaded")
    return FixtureGitHubState(issues)


def _extract_coordinate_parts(title: str) -> tuple[str, str, str] | None:
    match = re.search(r"([\w.\-]+):([\w.\-]+):([\w.\-]+)", title)
    if match is None:
        return None
    return match.group(1), match.group(2), match.group(3)


def _split_maven_coordinate(coordinate: str) -> tuple[str, str, str]:
    parts = coordinate.split(":")
    if len(parts) != 3 or any(not part for part in parts):
        raise FixtureValidationError(f"Invalid Maven coordinate: {coordinate}")
    return parts[0], parts[1], parts[2]


def _previous_issue_coordinates(
        issue: FixtureIssue,
        group: str,
        artifact: str,
        new_version: str,
) -> str | None:
    coordinate_pattern = re.compile(
        r"-Pcoordinates="
        + re.escape(f"{group}:{artifact}:")
        + r"([^\s`]+)"
    )
    for match in coordinate_pattern.finditer(issue.body):
        previous_version = match.group(1).strip()
        if previous_version and previous_version != new_version:
            return f"{group}:{artifact}:{previous_version}"
    return None


def _load_index_entries(index_json_path: str) -> list[Any]:
    with open(index_json_path, "r", encoding="utf-8") as index_file:
        index_entries = json.load(index_file)
    if not isinstance(index_entries, list):
        raise FixtureValidationError(f"Fixture metadata index is not a list: {index_json_path}")
    return index_entries


def _write_index_entries(index_json_path: str, index_entries: list[Any]) -> None:
    with open(index_json_path, "w", encoding="utf-8") as index_file:
        json.dump(index_entries, index_file, indent=2)
        index_file.write("\n")


def _index_entry_matches_version(entry: JsonObject, version: str) -> bool:
    metadata_version = entry.get("metadata-version")
    if metadata_version == version:
        return True
    tested_versions = entry.get("tested-versions")
    return isinstance(tested_versions, list) and version in tested_versions


def _clean_version_paths(
        worktree_path: str,
        group: str,
        artifact: str,
        version: str,
) -> list[JsonObject]:
    cleaned_paths: list[JsonObject] = []
    for path in (
        os.path.join(worktree_path, "metadata", group, artifact, version),
        os.path.join(worktree_path, "tests", "src", group, artifact, version),
        os.path.join(worktree_path, "stats", group, artifact, version),
    ):
        path_evidence: JsonObject = {
            "path": path,
            "relative_path": _repo_relative_path(path, worktree_path),
            "existed": os.path.exists(path),
            "type": "missing",
        }
        if os.path.isdir(path):
            path_evidence["type"] = "directory"
            shutil.rmtree(path)
        elif os.path.exists(path):
            path_evidence["type"] = "file"
            os.remove(path)
        path_evidence["exists_after_cleanup"] = os.path.exists(path)
        cleaned_paths.append(path_evidence)
    return cleaned_paths


def _format_cleaned_path_summary(cleaned_paths: list[JsonObject]) -> str:
    cleaned = [
        str(path.get("relative_path"))
        for path in cleaned_paths
        if path.get("existed") is True and path.get("exists_after_cleanup") is False
    ]
    return ", ".join(cleaned) if cleaned else "none"


def _repo_relative_path(path: str, repo_path: str) -> str:
    return os.path.relpath(os.path.abspath(path), os.path.abspath(repo_path))


def _log_fixture_setup(message: str) -> None:
    print(f"[fixture-setup] {message}", file=sys.stderr)


def _issue_has_all_labels(issue: FixtureIssue, labels: list[str]) -> bool:
    return all(label in issue.labels for label in labels)


def _issue_has_any_label(issue: FixtureIssue, labels: list[str]) -> bool:
    return any(label in issue.labels for label in labels)



def _utc_timestamp() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
