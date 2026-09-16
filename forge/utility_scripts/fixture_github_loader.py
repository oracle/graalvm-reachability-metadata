# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Fixture GitHub issue documents: discovery, parsing, and validation.

Owns the fixture issue and comment shapes and turns YAML fixture files into
validated `FixtureIssue` objects. The mutable fixture state machine lives in
`fixture_github.py`. §AR-forge-control-plane
"""

from __future__ import annotations

import os
from dataclasses import dataclass
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
    author: str
    body: str
    state: str
    labels: list[str]
    assignees: list[str]
    project_number: int
    project_item_id: str
    project_status: str
    blockers: list[int]
    comments: list[FixtureComment]
    continuation_marker: JsonObject | None
    worktree_files: dict[str, str]
    fixture_path: str
    url: str

    def issue_view_payload(self, include_body: bool = False, include_state: bool = True) -> JsonObject:
        payload: JsonObject = {
            "number": self.number,
            "title": self.title,
            "author": {"login": self.author},
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
            "author": {"login": self.author},
            "url": self.url,
            "labels": [{"name": label} for label in self.labels],
            "assignees": [{"login": assignee} for assignee in self.assignees],
        }

    def to_json(self) -> JsonObject:
        payload: JsonObject = {
            "number": self.number,
            "title": self.title,
            "author": self.author,
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
        if self.continuation_marker is not None:
            payload["continuation_marker"] = dict(self.continuation_marker)
        if self.worktree_files:
            payload["worktree_files"] = dict(self.worktree_files)
        return payload



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
    author = _optional_str(issue, "author", context, default="fixture-author")
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
    continuation_marker = _optional_mapping(issue, "continuation_marker", context)
    if continuation_marker is None:
        continuation_marker = _optional_mapping(issue, "continuationMarker", context)
    worktree_files = _normalize_worktree_files(issue, context)
    url = _optional_str(issue, "url", context, default=f"fixture://fixture_github_issues/{number}")
    return FixtureIssue(
        number=number,
        title=title,
        author=author,
        body=body,
        state=state,
        labels=labels,
        assignees=assignees,
        project_number=project_number,
        project_item_id=project_item_id,
        project_status=project_status,
        blockers=blockers,
        comments=comments,
        continuation_marker=continuation_marker,
        worktree_files=worktree_files,
        fixture_path=os.path.abspath(fixture_path),
        url=url,
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


def _normalize_worktree_files(issue: JsonObject, context: str) -> dict[str, str]:
    raw_files = _optional_mapping(issue, "worktree_files", context)
    if raw_files is None:
        raw_files = _optional_mapping(issue, "worktreeFiles", context)
    if raw_files is None:
        return {}
    files: dict[str, str] = {}
    for raw_path, raw_content in raw_files.items():
        if not isinstance(raw_path, str) or not raw_path:
            raise FixtureValidationError(f"{context}: worktree file paths must be non-empty strings")
        normalized_path = _normalize_worktree_file_path(raw_path, context)
        if isinstance(raw_content, str):
            content = raw_content
        else:
            content = json.dumps(raw_content, indent=2, sort_keys=True)
            content += "\n"
        files[normalized_path] = content
    return files


def _normalize_worktree_file_path(path: str, context: str) -> str:
    normalized_path = os.path.normpath(path)
    if os.path.isabs(normalized_path) or normalized_path == ".." or normalized_path.startswith("../"):
        raise FixtureValidationError(f"{context}: invalid worktree file path: {path}")
    return normalized_path


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


def _optional_mapping(mapping: JsonObject, key: str, context: str) -> JsonObject | None:
    value = mapping.get(key)
    if value is None:
        return None
    if not isinstance(value, dict):
        raise FixtureValidationError(f"{context}: `{key}` must be an object")
    return dict(value)


def _is_int(value: Any) -> bool:
    return isinstance(value, int) and not isinstance(value, bool)


def _is_yaml_path(path: str) -> bool:
    return path.endswith((".yaml", ".yml"))



def load_fixture_issues(fixture_paths: list[str] | None = None) -> list[FixtureIssue]:
    """Load all requested YAML fixture files into validated fixture issues."""
    if yaml is None:
        raise RuntimeError("PyYAML is required to load GitHub issue fixtures.")

    issues: list[FixtureIssue] = []
    for fixture_path in discover_fixture_paths(fixture_paths):
        raw_document = _load_yaml_document(fixture_path)
        for raw_issue in _iter_raw_issues(raw_document, fixture_path):
            issues.append(normalize_fixture_issue(raw_issue, fixture_path))
    return issues
