# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Local GitHub issue fixtures for hermetic Forge E2E runs.

The module models the fixture-backed GitHub state required by
§E2E-forge-workflow-testing.2 without importing `forge_metadata.py`.
"""

from __future__ import annotations

import json
import os
import re
import shutil
import sys
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any

try:
    import yaml
except ImportError:  # pragma: no cover - exercised only when the dependency is missing.
    yaml = None


ALLOWED_ISSUE_STATES = {"OPEN", "CLOSED"}
ALLOWED_PROJECT_STATUSES = {"Todo", "In Progress", "Done"}
LABEL_LIBRARY_NEW = "library-new-request"
LABEL_JAVAC_FAIL = "fails-javac-compile"
LABEL_JAVA_RUN_FAIL = "fails-java-run"
LABEL_NI_RUN_FAIL = "fails-native-image-run"

JsonObject = dict[str, Any]


class FixtureValidationError(ValueError):
    """Raised when a fixture file does not match the fixture GitHub contract."""


class FixtureIssueNotFoundError(KeyError):
    """Raised when fixture state is asked for an unknown issue number."""


@dataclass
class FixtureComment:
    author: str
    body: str
    created_at: str | None = None

    def to_json(self) -> JsonObject:
        payload: JsonObject = {
            "author": self.author,
            "body": self.body,
        }
        if self.created_at is not None:
            payload["created_at"] = self.created_at
        return payload

    def to_github_payload(self) -> JsonObject:
        payload: JsonObject = {
            "author": {"login": self.author},
            "body": self.body,
        }
        if self.created_at is not None:
            payload["createdAt"] = self.created_at
        return payload


@dataclass
class FixtureIssue:
    number: int
    title: str
    body: str
    state: str
    labels: list[str]
    assignees: list[str]
    project_number: int
    project_item_id: str
    project_status: str
    blockers: list[int]
    comments: list[FixtureComment]
    fixture_path: str
    url: str
    library_preparation_preflight_response: Any | None = None

    def issue_view_payload(self, include_body: bool = False, include_state: bool = True) -> JsonObject:
        payload: JsonObject = {
            "number": self.number,
            "title": self.title,
            "url": self.url,
            "labels": [{"name": label} for label in self.labels],
            "assignees": [{"login": assignee} for assignee in self.assignees],
        }
        if include_state:
            payload["state"] = self.state
        if include_body:
            payload["body"] = self.body
        return payload

    def issue_search_payload(self) -> JsonObject:
        return {
            "number": self.number,
            "title": self.title,
            "url": self.url,
            "labels": [{"name": label} for label in self.labels],
            "assignees": [{"login": assignee} for assignee in self.assignees],
        }

    def to_json(self) -> JsonObject:
        payload: JsonObject = {
            "number": self.number,
            "title": self.title,
            "body": self.body,
            "state": self.state,
            "url": self.url,
            "labels": list(self.labels),
            "assignees": list(self.assignees),
            "project": {
                "number": self.project_number,
                "item_id": self.project_item_id,
                "status": self.project_status,
            },
            "blockers": list(self.blockers),
            "comments": [comment.to_json() for comment in self.comments],
            "fixture_path": self.fixture_path,
        }
        if self.library_preparation_preflight_response is not None:
            payload["library_preparation_preflight_response"] = self.library_preparation_preflight_response
        return payload


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

    def get_issue_library_preparation_preflight_response(self, issue_number: int) -> Any | None:
        return self._issue(issue_number).library_preparation_preflight_response

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
    ) -> list[JsonObject]:
        """Return `gh issue list`/search-shaped open issue payloads."""
        if limit <= 0:
            return []
        matched = [
            issue.issue_search_payload()
            for issue in self._iter_open_issues()
            if _issue_has_all_labels(issue, [label, *(extra_labels or [])])
            and not _issue_has_any_label(issue, excluded_labels or [])
        ]
        return matched[offset:offset + limit]

    def count_open_issues_by_label(
            self,
            label: str,
            extra_labels: list[str] | None = None,
            excluded_labels: list[str] | None = None,
    ) -> int:
        return len(self.list_open_issues_by_label(
            label,
            limit=len(self._issue_order),
            offset=0,
            extra_labels=extra_labels,
            excluded_labels=excluded_labels,
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


def load_fixture_github_state(fixture_paths: list[str] | None = None) -> FixtureGitHubState:
    """Load all requested YAML fixtures into mutable fixture GitHub state."""
    if yaml is None:
        raise RuntimeError("PyYAML is required to load GitHub issue fixtures.")

    issues: list[FixtureIssue] = []
    for fixture_path in discover_fixture_paths(fixture_paths):
        raw_document = _load_yaml_document(fixture_path)
        for raw_issue in _iter_raw_issues(raw_document, fixture_path):
            issues.append(normalize_fixture_issue(raw_issue, fixture_path))
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


def default_fixture_dir() -> str:
    return os.path.abspath(os.path.join(
        os.path.dirname(__file__),
        os.pardir,
        "fixture_github_issues",
    ))


def discover_fixture_paths(fixture_paths: list[str] | None = None) -> list[str]:
    """Resolve fixture files from explicit paths or the default fixture directory."""
    requested_paths = fixture_paths or [default_fixture_dir()]
    discovered: list[str] = []

    for requested_path in requested_paths:
        absolute_path = os.path.abspath(requested_path)
        if os.path.isdir(absolute_path):
            directory_fixture_count = 0
            for entry in sorted(os.listdir(absolute_path)):
                entry_path = os.path.join(absolute_path, entry)
                if os.path.isfile(entry_path) and _is_yaml_path(entry_path):
                    directory_fixture_count += 1
                    discovered.append(entry_path)
            if directory_fixture_count == 0:
                raise FileNotFoundError(f"No YAML fixture files found in directory: {requested_path}")
            continue
        if os.path.isfile(absolute_path) and _is_yaml_path(absolute_path):
            discovered.append(absolute_path)
            continue
        raise FileNotFoundError(f"Fixture path does not exist or is not YAML: {requested_path}")

    return discovered


def normalize_fixture_issue(raw_issue: JsonObject, fixture_path: str) -> FixtureIssue:
    context = f"{fixture_path}"
    issue = _require_mapping(raw_issue, context)
    number = _require_int(issue, "number", context)
    title = _require_str(issue, "title", context)
    body = _optional_str(issue, "body", context, default="")
    state = _require_str(issue, "state", context).upper()
    if state not in ALLOWED_ISSUE_STATES:
        raise FixtureValidationError(
            f"{context}: `state` must be one of {sorted(ALLOWED_ISSUE_STATES)}"
        )
    labels = _normalize_named_values(_require_list(issue, "labels", context), "labels", "name", context)
    assignees = _normalize_named_values(
        _require_list(issue, "assignees", context),
        "assignees",
        "login",
        context,
    )
    project_number, project_item_id, project_status = _normalize_project(issue, context)
    blockers = _normalize_blockers(issue, context)
    comments = _normalize_comments(_optional_list(issue, "comments", context, default=[]), context)
    url = _optional_str(issue, "url", context, default=f"fixture://fixture_github_issues/{number}")
    library_preparation_preflight_response = issue.get("library_preparation_preflight_response")
    if library_preparation_preflight_response is not None and not isinstance(
            library_preparation_preflight_response,
            (dict, str),
    ):
        raise FixtureValidationError(
            f"{context}: `library_preparation_preflight_response` must be an object or string"
        )

    return FixtureIssue(
        number=number,
        title=title,
        body=body,
        state=state,
        labels=labels,
        assignees=assignees,
        project_number=project_number,
        project_item_id=project_item_id,
        project_status=project_status,
        blockers=blockers,
        comments=comments,
        fixture_path=os.path.abspath(fixture_path),
        url=url,
        library_preparation_preflight_response=library_preparation_preflight_response,
    )


def _load_yaml_document(fixture_path: str) -> Any:
    with open(fixture_path, "r", encoding="utf-8") as fixture_file:
        return yaml.safe_load(fixture_file)


def _iter_raw_issues(raw_document: Any, fixture_path: str) -> list[JsonObject]:
    if isinstance(raw_document, list):
        return [_require_mapping(item, fixture_path) for item in raw_document]
    if isinstance(raw_document, dict):
        if "issues" in raw_document:
            return [
                _require_mapping(item, f"{fixture_path}:issues")
                for item in _require_list(raw_document, "issues", fixture_path)
            ]
        return [_require_mapping(raw_document, fixture_path)]
    raise FixtureValidationError(f"{fixture_path}: fixture YAML must be an issue object or issue list")


def _normalize_project(issue: JsonObject, context: str) -> tuple[int, str, str]:
    project = issue.get("project")
    if isinstance(project, dict):
        project_context = f"{context}:project"
        project_number = _require_int(project, "number", project_context)
        project_item_id = _require_str(project, "item_id", project_context)
        project_status = _require_str(project, "status", project_context)
        _validate_project_status(project_status, project_context)
        return (
            project_number,
            project_item_id,
            project_status,
        )

    project_number = _require_int(issue, "project_number", context)
    project_item_id = _require_str(issue, "project_item_id", context)
    project_status = _require_str(issue, "project_status", context)
    _validate_project_status(project_status, context)
    return (
        project_number,
        project_item_id,
        project_status,
    )


def _validate_project_status(status: str, context: str) -> None:
    if status not in ALLOWED_PROJECT_STATUSES:
        raise FixtureValidationError(
            f"{context}: project status must be one of {sorted(ALLOWED_PROJECT_STATUSES)}"
        )


def _normalize_blocker_numbers(raw_blockers: list[Any], context: str) -> list[int]:
    blockers: list[int] = []
    for index, value in enumerate(raw_blockers):
        if not _is_int(value):
            raise FixtureValidationError(f"{context}: blockers[{index}] must be an integer")
        blockers.append(value)
    return list(dict.fromkeys(blockers))


def _normalize_blockers(issue: JsonObject, context: str) -> list[int]:
    if "blocked_by" in issue:
        raw_blockers = _require_list(issue, "blocked_by", context)
    elif "blockers" in issue:
        raw_blockers = _require_list(issue, "blockers", context)
    else:
        raw_blockers = []
    return _normalize_blocker_numbers(raw_blockers, context)


def _normalize_comments(raw_comments: list[Any], context: str) -> list[FixtureComment]:
    comments: list[FixtureComment] = []
    for index, raw_comment in enumerate(raw_comments):
        comment_context = f"{context}:comments[{index}]"
        if isinstance(raw_comment, str):
            comments.append(FixtureComment(author="fixture-author", body=raw_comment))
            continue
        comment = _require_mapping(raw_comment, comment_context)
        raw_author = comment.get("author", "fixture-author")
        if isinstance(raw_author, dict):
            author = _require_str(raw_author, "login", f"{comment_context}:author")
        elif isinstance(raw_author, str) and raw_author:
            author = raw_author
        else:
            raise FixtureValidationError(f"{comment_context}: author must be a string or login object")
        body = _require_str(comment, "body", comment_context)
        created_at = _optional_str(comment, "created_at", comment_context, default=None)
        if created_at is None:
            created_at = _optional_str(comment, "createdAt", comment_context, default=None)
        comments.append(FixtureComment(author=author, body=body, created_at=created_at))
    return comments


def _issue_has_all_labels(issue: FixtureIssue, labels: list[str]) -> bool:
    return all(label in issue.labels for label in labels)


def _issue_has_any_label(issue: FixtureIssue, labels: list[str]) -> bool:
    return any(label in issue.labels for label in labels)


def _normalize_named_values(raw_values: list[Any], field_name: str, object_key: str, context: str) -> list[str]:
    names: list[str] = []
    for index, raw_value in enumerate(raw_values):
        if isinstance(raw_value, str) and raw_value:
            names.append(raw_value)
            continue
        if isinstance(raw_value, dict):
            value = raw_value.get(object_key)
            if isinstance(value, str) and value:
                names.append(value)
                continue
        raise FixtureValidationError(
            f"{context}: {field_name}[{index}] must be a string or object with `{object_key}`"
        )
    return list(dict.fromkeys(names))


def _require_mapping(value: Any, context: str) -> JsonObject:
    if not isinstance(value, dict):
        raise FixtureValidationError(f"{context}: expected an object")
    return value


def _require_str(mapping: JsonObject, key: str, context: str) -> str:
    value = mapping.get(key)
    if not isinstance(value, str) or not value:
        raise FixtureValidationError(f"{context}: `{key}` must be a non-empty string")
    return value


def _require_non_empty_text(value: str, field_name: str) -> None:
    if not isinstance(value, str) or not value:
        raise FixtureValidationError(f"{field_name} must be a non-empty string")


def _optional_str(mapping: JsonObject, key: str, context: str, default: str | None = "") -> str | None:
    value = mapping.get(key, default)
    if value is None:
        return None
    if not isinstance(value, str):
        raise FixtureValidationError(f"{context}: `{key}` must be a string")
    return value


def _require_int(mapping: JsonObject, key: str, context: str) -> int:
    value = mapping.get(key)
    if not _is_int(value):
        raise FixtureValidationError(f"{context}: `{key}` must be an integer")
    return value


def _require_list(mapping: JsonObject, key: str, context: str) -> list[Any]:
    value = mapping.get(key)
    if not isinstance(value, list):
        raise FixtureValidationError(f"{context}: `{key}` must be a list")
    return value


def _optional_list(mapping: JsonObject, key: str, context: str, default: list[Any]) -> list[Any]:
    value = mapping.get(key, default)
    if not isinstance(value, list):
        raise FixtureValidationError(f"{context}: `{key}` must be a list")
    return value


def _is_int(value: Any) -> bool:
    return isinstance(value, int) and not isinstance(value, bool)


def _is_yaml_path(path: str) -> bool:
    return path.endswith((".yaml", ".yml"))


def _utc_timestamp() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
