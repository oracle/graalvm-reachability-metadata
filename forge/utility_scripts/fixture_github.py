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
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any

try:
    import yaml
except ImportError:  # pragma: no cover - exercised only when the dependency is missing.
    yaml = None


FIXTURE_REPORT_DIR = "fixture-e2e"
FIXTURE_REPORT_FILENAME = "fixture-state-report.json"
ALLOWED_ISSUE_STATES = {"OPEN", "CLOSED"}
ALLOWED_PROJECT_STATUSES = {"Todo", "In Progress", "Done"}

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
    expected_side_effects: list[Any]
    fixture_path: str
    url: str

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
        return {
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
            "expected_side_effects": _json_clone(self.expected_side_effects),
            "fixture_path": self.fixture_path,
        }


@dataclass
class FixtureSideEffect:
    timestamp: str
    issue_number: int
    action: str
    details: JsonObject = field(default_factory=dict)

    def to_json(self) -> JsonObject:
        return {
            "timestamp": self.timestamp,
            "issue_number": self.issue_number,
            "action": self.action,
            "details": _json_clone(self.details),
        }


class FixtureGitHubState:
    """Mutable in-memory GitHub fixture state for `forge_metadata.py` wiring."""

    def __init__(self, issues: list[FixtureIssue]) -> None:
        self._issues: dict[int, FixtureIssue] = {}
        self._issue_order: list[int] = []
        self._item_to_issue_number: dict[str, int] = {}
        self.side_effects: list[FixtureSideEffect] = []

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
        previous_labels = list(issue.labels)
        issue.labels.append(label_name)
        self.record_side_effect(
            issue_number,
            "add-label",
            {"label": label_name, "previous_labels": previous_labels, "labels": list(issue.labels)},
        )

    def remove_issue_label(self, issue_number: int, label_name: str) -> None:
        _require_non_empty_text(label_name, "label_name")
        issue = self._issue(issue_number)
        if label_name not in issue.labels:
            return
        previous_labels = list(issue.labels)
        issue.labels = [label for label in issue.labels if label != label_name]
        self.record_side_effect(
            issue_number,
            "remove-label",
            {"label": label_name, "previous_labels": previous_labels, "labels": list(issue.labels)},
        )

    def get_issue_assignees(self, issue_number: int) -> list[str]:
        return list(self._issue(issue_number).assignees)

    def set_issue_assignee(self, issue_number: int, username: str) -> None:
        _require_non_empty_text(username, "username")
        issue = self._issue(issue_number)
        previous_assignees = list(issue.assignees)
        issue.assignees = [username]
        self.record_side_effect(
            issue_number,
            "set-assignee",
            {
                "username": username,
                "previous_assignees": previous_assignees,
                "assignees": list(issue.assignees),
            },
        )

    def clear_issue_assignees(self, issue_number: int) -> None:
        issue = self._issue(issue_number)
        if not issue.assignees:
            return
        previous_assignees = list(issue.assignees)
        issue.assignees = []
        self.record_side_effect(
            issue_number,
            "clear-assignees",
            {"previous_assignees": previous_assignees, "assignees": []},
        )

    def get_issue_comments(self, issue_number: int) -> list[JsonObject]:
        return [comment.to_github_payload() for comment in self._issue(issue_number).comments]

    def post_issue_comment(self, issue_number: int, body: str, author: str = "fixture-runner") -> None:
        _require_non_empty_text(author, "author")
        _require_non_empty_text(body, "body")
        issue = self._issue(issue_number)
        comment = FixtureComment(author=author, body=body, created_at=_utc_timestamp())
        issue.comments.append(comment)
        self.record_side_effect(
            issue_number,
            "post-comment",
            {"author": author, "body": body, "comment_count": len(issue.comments)},
        )

    def get_project_item_state(self, issue_number: int) -> tuple[str, str]:
        issue = self._issue(issue_number)
        return issue.project_item_id, issue.project_status

    def get_item_status(self, item_id: str) -> str:
        issue = self._issue_by_item_id(item_id)
        return issue.project_status

    def set_item_status(self, item_id: str, status: str) -> None:
        _validate_project_status(status, item_id)
        issue = self._issue_by_item_id(item_id)
        previous_status = issue.project_status
        issue.project_status = status
        self.record_side_effect(
            issue.number,
            "set-project-status",
            {"item_id": item_id, "previous_status": previous_status, "status": status},
        )

    def set_project_status(self, issue_number: int, status: str) -> None:
        issue = self._issue(issue_number)
        self.set_item_status(issue.project_item_id, status)

    def get_open_blocking_issue_numbers(self, issue_number: int) -> list[int]:
        return list(self._issue(issue_number).blockers)

    def set_open_blocking_issue_numbers(self, issue_number: int, blockers: list[int]) -> None:
        issue = self._issue(issue_number)
        previous_blockers = list(issue.blockers)
        issue.blockers = _normalize_blocker_numbers(blockers, f"issue #{issue_number} blockers")
        self.record_side_effect(
            issue_number,
            "set-blockers",
            {"previous_blockers": previous_blockers, "blockers": list(issue.blockers)},
        )

    def add_open_blocker(self, issue_number: int, blocker_issue_number: int) -> None:
        if not _is_int(blocker_issue_number):
            raise FixtureValidationError("blocker_issue_number must be an integer")
        issue = self._issue(issue_number)
        if blocker_issue_number in issue.blockers:
            return
        issue.blockers.append(blocker_issue_number)
        self.record_side_effect(
            issue_number,
            "add-blocker",
            {"blocker": blocker_issue_number, "blockers": list(issue.blockers)},
        )

    def remove_open_blocker(self, issue_number: int, blocker_issue_number: int) -> None:
        if not _is_int(blocker_issue_number):
            raise FixtureValidationError("blocker_issue_number must be an integer")
        issue = self._issue(issue_number)
        if blocker_issue_number not in issue.blockers:
            return
        issue.blockers = [blocker for blocker in issue.blockers if blocker != blocker_issue_number]
        self.record_side_effect(
            issue_number,
            "remove-blocker",
            {"blocker": blocker_issue_number, "blockers": list(issue.blockers)},
        )

    def record_publication_handoff(self, issue_number: int, details: JsonObject) -> None:
        self.record_side_effect(issue_number, "publication-handoff", details)

    def record_failure_preservation(self, issue_number: int, details: JsonObject) -> None:
        self.record_side_effect(issue_number, "failure-preservation", details)

    def record_side_effect(self, issue_number: int, action: str, details: JsonObject | None = None) -> None:
        self._issue(issue_number)
        _require_non_empty_text(action, "action")
        self.side_effects.append(FixtureSideEffect(
            timestamp=_utc_timestamp(),
            issue_number=issue_number,
            action=action,
            details=_json_clone(details or {}),
        ))

    def compare_expected_side_effects(self, issue_number: int | None = None) -> JsonObject:
        """Compare recorded side effects with each fixture's expected effects."""
        issue_numbers = [issue_number] if issue_number is not None else self.issue_numbers
        issue_reports: JsonObject = {}
        passed = True

        for current_issue_number in issue_numbers:
            issue = self._issue(current_issue_number)
            actual = self._side_effects_for_issue(current_issue_number)
            report = _compare_issue_side_effects(issue.expected_side_effects, actual)
            issue_reports[str(current_issue_number)] = report
            passed = passed and bool(report["passed"])

        return {
            "passed": passed,
            "issues": issue_reports,
        }

    def build_report(self, issue_number: int | None = None, extra: JsonObject | None = None) -> JsonObject:
        issue_numbers = [issue_number] if issue_number is not None else self.issue_numbers
        issues = [self._issue(number).to_json() for number in issue_numbers]
        side_effects = [
            side_effect.to_json()
            for side_effect in self.side_effects
            if issue_number is None or side_effect.issue_number == issue_number
        ]
        return {
            "mode": "fixture-backed E2E",
            "generated_at": _utc_timestamp(),
            "issue_numbers": issue_numbers,
            "issues": issues,
            "side_effects": side_effects,
            "side_effect_comparison": self.compare_expected_side_effects(issue_number),
            "extra": _json_clone(extra or {}),
        }

    def write_report(
            self,
            metrics_repo_path: str,
            issue_number: int | None = None,
            extra: JsonObject | None = None,
    ) -> str:
        """Write persistent fixture state under `<metrics_repo>/script_run_metrics/fixture-e2e/`."""
        report_dir = fixture_report_dir(metrics_repo_path, issue_number)
        os.makedirs(report_dir, exist_ok=True)
        report_path = os.path.join(report_dir, FIXTURE_REPORT_FILENAME)
        with open(report_path, "w", encoding="utf-8") as report_file:
            json.dump(self.build_report(issue_number, extra), report_file, indent=2, sort_keys=True)
            report_file.write("\n")
        return report_path

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

    def _side_effects_for_issue(self, issue_number: int) -> list[JsonObject]:
        return [
            side_effect.to_json()
            for side_effect in self.side_effects
            if side_effect.issue_number == issue_number
        ]


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


def default_fixture_dir() -> str:
    return os.path.abspath(os.path.join(
        os.path.dirname(__file__),
        os.pardir,
        "fixtures",
        "github-issues",
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
    expected_side_effects = _normalize_expected_side_effects(
        _optional_list(issue, "expected_side_effects", context, default=[]),
        context,
    )
    url = _optional_str(issue, "url", context, default=f"fixture://github-issues/{number}")

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
        expected_side_effects=expected_side_effects,
        fixture_path=os.path.abspath(fixture_path),
        url=url,
    )


def fixture_report_dir(metrics_repo_path: str, issue_number: int | None = None) -> str:
    timestamp = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H-%M-%S-%fZ")
    issue_segment = f"issue-{issue_number}" if issue_number is not None else "all-issues"
    return os.path.join(
        os.path.abspath(metrics_repo_path),
        "script_run_metrics",
        FIXTURE_REPORT_DIR,
        f"{issue_segment}-{timestamp}",
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


def _normalize_expected_side_effects(raw_expected: list[Any], context: str) -> list[Any]:
    expected: list[Any] = []
    for index, raw_entry in enumerate(raw_expected):
        entry_context = f"{context}:expected_side_effects[{index}]"
        if isinstance(raw_entry, str) and raw_entry:
            expected.append(raw_entry)
            continue
        entry = _require_mapping(raw_entry, entry_context)
        action = _require_str(entry, "action", entry_context)
        normalized = _json_clone(entry)
        normalized["action"] = action
        if "issue_number" in normalized and not _is_int(normalized["issue_number"]):
            raise FixtureValidationError(f"{entry_context}: issue_number must be an integer")
        if "details" in normalized and not isinstance(normalized["details"], dict):
            raise FixtureValidationError(f"{entry_context}: details must be an object")
        expected.append(normalized)
    return expected


def _compare_issue_side_effects(expected: list[Any], actual: list[JsonObject]) -> JsonObject:
    missing_expected: list[Any] = []
    matched_expected: list[JsonObject] = []
    matched_actual_indexes: set[int] = set()

    for expected_entry in expected:
        matched_index = _find_matching_side_effect_index(expected_entry, actual, matched_actual_indexes)
        if matched_index is None:
            missing_expected.append(_json_clone(expected_entry))
            continue
        matched_actual_indexes.add(matched_index)
        matched_expected.append({
            "expected": _json_clone(expected_entry),
            "actual": _json_clone(actual[matched_index]),
        })

    unexpected_actual = [
        _json_clone(effect)
        for index, effect in enumerate(actual)
        if index not in matched_actual_indexes
    ]

    return {
        "passed": not missing_expected and not unexpected_actual,
        "expected_count": len(expected),
        "actual_count": len(actual),
        "matched_expected": matched_expected,
        "missing_expected": missing_expected,
        "unexpected_actual": unexpected_actual,
    }


def _find_matching_side_effect_index(
        expected_entry: Any,
        actual: list[JsonObject],
        consumed_indexes: set[int],
) -> int | None:
    if isinstance(expected_entry, str):
        return next(
            (
                index
                for index, effect in enumerate(actual)
                if index not in consumed_indexes and effect.get("action") == expected_entry
            ),
            None,
        )
    if not isinstance(expected_entry, dict):
        return None

    expected_action = expected_entry.get("action")
    expected_issue_number = expected_entry.get("issue_number")
    expected_details = dict(expected_entry.get("details", {}))
    for key, value in expected_entry.items():
        if key not in {"action", "issue_number", "details"}:
            expected_details[key] = value

    for index, effect in enumerate(actual):
        if index in consumed_indexes:
            continue
        if effect.get("action") != expected_action:
            continue
        if expected_issue_number is not None and effect.get("issue_number") != expected_issue_number:
            continue
        actual_details = effect.get("details", {})
        if isinstance(expected_details, dict) and _mapping_contains(actual_details, expected_details):
            return index
    return None


def _mapping_contains(actual: Any, expected: JsonObject) -> bool:
    if not isinstance(actual, dict):
        return False
    for key, expected_value in expected.items():
        if key not in actual:
            return False
        actual_value = actual[key]
        if isinstance(expected_value, dict):
            if not _mapping_contains(actual_value, expected_value):
                return False
        elif actual_value != expected_value:
            return False
    return True


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


def _json_clone(value: Any) -> Any:
    return json.loads(json.dumps(value, default=str))
