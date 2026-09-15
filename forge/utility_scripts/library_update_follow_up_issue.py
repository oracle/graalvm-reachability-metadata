# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Successor-issue management for library-update tested-version splits.

After a split, the successor range gets its own parked library-update issue,
linked from the PR by a typed trailer, so the recorded split can be resumed
without re-running the sweep. §FS-library-update-tested-version-split
"""

import re
from typing import Any

from git_scripts.common_git import (
    get_issue_project_item_status,
    gh,
    gh_json,
)
from utility_scripts.library_update_alias_split import (
    load_alias_split_metrics,
    write_alias_split_metrics,
)


FOLLOW_UP_TRAILER = "Forge-Unblocks-Issue"

PROJECT_NUMBER = 30
STATUS_FIELD_NAME = "Status"
STATUS_IN_PROGRESS = "In Progress"


def extract_follow_up_issue_numbers(body: str | None) -> list[int]:
    """Return follow-up issues from PR text trailers, ignoring casual references."""
    if not isinstance(body, str):
        return []
    return [
        int(match.group(1))
        for match in re.finditer(rf"(?m)^{re.escape(FOLLOW_UP_TRAILER)}:\s*#(\d+)\b", body)
    ]


def ensure_alias_split_follow_up_issue(
        *,
        metrics_repo_path: str | None,
        current_issue_number: int | None,
        repo: str,
) -> dict[str, Any] | None:
    """Create and park the successor library-update issue after local CI passes.

    The issue is kept `In Progress` until the current PR merges, preventing the
    normal work queue from claiming the successor too early.
    §FS-library-update-tested-version-split
    """
    split = load_alias_split_metrics(metrics_repo_path)
    if split is None:
        return None
    existing_issue = split.get("follow_up_issue_number")
    if isinstance(existing_issue, int):
        return split

    coordinates = str(split["successor_coordinates"])
    issue_number = _find_existing_open_issue_number(repo, coordinates)
    if issue_number is None:
        issue_number = _create_follow_up_issue(repo, split, current_issue_number)
    ensure_issue_project_status(repo, PROJECT_NUMBER, issue_number, STATUS_IN_PROGRESS)

    updated_split = dict(split)
    updated_split["follow_up_issue_number"] = issue_number
    write_alias_split_metrics(metrics_repo_path, updated_split)
    return updated_split

def _find_existing_open_issue_number(repo: str, coordinates: str) -> int | None:
    issues = gh_json(
        "issue",
        "list",
        "--repo",
        repo,
        "--state",
        "open",
        "--search",
        f'"{coordinates}" in:title,body',
        "--json",
        "number,title,body",
        "--limit",
        "20",
    )
    if not isinstance(issues, list):
        return None
    for issue in issues:
        if not isinstance(issue, dict):
            continue
        text = f"{issue.get('title') or ''}\n{issue.get('body') or ''}"
        if coordinates in text and isinstance(issue.get("number"), int):
            return int(issue["number"])
    return None

def _create_follow_up_issue(repo: str, split: dict[str, Any], current_issue_number: int | None) -> int:
    coordinates = str(split["successor_coordinates"])
    blocked_by = f"Blocked by the current update issue #{current_issue_number}." if current_issue_number else ""
    body = f"""This issue tracks the successor library-update split for `{coordinates}`.

Forge split the previous tested-version range because generated JVM tests first failed at `{split['failed_version']}`.

{blocked_by}

Successor tested versions to preserve:
{_format_issue_version_lines(split.get("successor_versions"))}

The current PR keeps the generated progress for `{split['current_coordinates']}`
and copies baseline support for this successor range.
"""
    result = gh(
        "issue",
        "create",
        "--repo",
        repo,
        "--title",
        f"Update existing library: {coordinates}",
        "--body",
        body,
        "--label",
        "library-update-request",
    )
    match = re.search(r"/issues/(\d+)", result.stdout)
    if match is None:
        raise RuntimeError(f"Could not parse created follow-up issue number from: {result.stdout.strip()}")
    return int(match.group(1))

def ensure_issue_project_status(repo: str, project_number: int, issue_number: int, status: str) -> None:
    item_id, current_status = get_issue_project_item_status(
        repo,
        project_number,
        issue_number,
        STATUS_FIELD_NAME,
    )
    project_id, field_id, option_ids = _project_status_field_info(repo, project_number)
    if status not in option_ids:
        raise RuntimeError(f"Missing project status option {status!r}")
    if item_id is None:
        issue_id = _issue_node_id(repo, issue_number)
        item_id = _add_issue_to_project(project_id, issue_id)
    if current_status != status:
        _set_project_item_status(project_id, item_id, field_id, option_ids[status])

def _project_status_field_info(repo: str, project_number: int) -> tuple[str, str, dict[str, str]]:
    owner, _ = repo.split("/")
    query = f"""
    query {{
      organization(login: "{owner}") {{
        projectV2(number: {project_number}) {{
          id
          fields(first: 50) {{
            nodes {{
              ... on ProjectV2SingleSelectField {{
                id
                name
                options {{
                  id
                  name
                }}
              }}
            }}
          }}
        }}
      }}
    }}
    """
    result = gh_json("api", "graphql", "-f", f"query={query}")
    project = (
        result.get("data", {})
        .get("organization", {})
        .get("projectV2", {})
    )
    if not isinstance(project, dict) or not project.get("id"):
        raise RuntimeError(f"Missing GitHub project {project_number} for {owner}")
    for field in project.get("fields", {}).get("nodes", []):
        if not isinstance(field, dict) or field.get("name") != STATUS_FIELD_NAME:
            continue
        options = {
            str(option["name"]): str(option["id"])
            for option in field.get("options", [])
            if isinstance(option, dict) and option.get("name") and option.get("id")
        }
        return str(project["id"]), str(field["id"]), options
    raise RuntimeError(f"Missing project status field {STATUS_FIELD_NAME!r}")

def _issue_node_id(repo: str, issue_number: int) -> str:
    data = gh_json(
        "issue",
        "view",
        str(issue_number),
        "--repo",
        repo,
        "--json",
        "id",
    )
    issue_id = data.get("id") if isinstance(data, dict) else None
    if not isinstance(issue_id, str) or not issue_id:
        raise RuntimeError(f"Missing GitHub node id for issue #{issue_number}")
    return issue_id

def _add_issue_to_project(project_id: str, issue_id: str) -> str:
    mutation = """
    mutation($projectId: ID!, $contentId: ID!) {
      addProjectV2ItemById(input: {projectId: $projectId, contentId: $contentId}) {
        item {
          id
        }
      }
    }
    """
    result = gh_json(
        "api",
        "graphql",
        "-f",
        f"query={mutation}",
        "-f",
        f"projectId={project_id}",
        "-f",
        f"contentId={issue_id}",
    )
    item_id = (
        result.get("data", {})
        .get("addProjectV2ItemById", {})
        .get("item", {})
        .get("id")
    )
    if not isinstance(item_id, str) or not item_id:
        raise RuntimeError("Missing project item id after adding follow-up issue")
    return item_id

def _set_project_item_status(project_id: str, item_id: str, field_id: str, option_id: str) -> None:
    mutation = """
    mutation($projectId: ID!, $itemId: ID!, $fieldId: ID!, $optionId: String!) {
      updateProjectV2ItemFieldValue(input: {
        projectId: $projectId,
        itemId: $itemId,
        fieldId: $fieldId,
        value: {singleSelectOptionId: $optionId}
      }) {
        projectV2Item {
          id
        }
      }
    }
    """
    gh_json(
        "api",
        "graphql",
        "-f",
        f"query={mutation}",
        "-f",
        f"projectId={project_id}",
        "-f",
        f"itemId={item_id}",
        "-f",
        f"fieldId={field_id}",
        "-f",
        f"optionId={option_id}",
    )

def _format_issue_version_lines(value: Any) -> str:
    if not isinstance(value, list):
        return "- none"
    return "\n".join(f"- `{version}`" for version in value) or "- none"
