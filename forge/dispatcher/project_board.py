# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""GitHub project board item state and status transitions
(§AR-forge-dispatcher-decomposition, §FS-forge-issue-resolution-goal)."""

import sys
from typing import Optional

from git_scripts.common_git import get_issue_project_item_status
from git_scripts.github_cli import run_github_command_with_retries
from utility_scripts.stage_logger import log_debug

from dispatcher.config import PROJECT_NUMBER, REPO, STATUS_FIELD_NAME
from dispatcher.github_api import gh, gh_json


def get_project_item_state(issue_number: int) -> tuple[str | None, str | None]:
    """
    Fetch the project item ID and current Status field value for an issue via GraphQL.
    Returns (item ID, status option name), or (None, None) if not found.
    """
    item_id, status = get_issue_project_item_status(
        REPO,
        PROJECT_NUMBER,
        issue_number,
        STATUS_FIELD_NAME,
    )
    if item_id:
        status_text = status if status is not None else "unknown"
        log_debug(
            "project-item",
            (
                f"Issue #{issue_number} is linked to GitHub project item {item_id} "
                f"in project {PROJECT_NUMBER} with Status '{status_text}'"
            ),
        )
    return item_id, status


def get_project_item_id(issue_number: int):
    """
    Fetch the project item ID for a given issue number via GraphQL.
    Looks for the item linked to PROJECT_NUMBER.
    Returns the item ID (PVTI_...) or None if not found.
    """
    item_id, _ = get_project_item_state(issue_number)
    return item_id


def get_item_status(item_id: str):
    """
    Query the current Status field value of a project item via GraphQL.
    Returns the status option name (e.g. "Todo") or None.
    """
    query = """
    query($item: ID!) {
      node(id: $item) {
        ... on ProjectV2Item {
          fieldValues(first: 5) {
            nodes {
              ... on ProjectV2ItemFieldSingleSelectValue {
                name
                field { ... on ProjectV2FieldCommon { name } }
              }
            }
          }
        }
      }
    }
    """
    result = gh_json(
        "api", "graphql",
        "-f", f"query={query}",
        "-f", f"item={item_id}",
    )
    nodes = (
        result.get("data", {})
        .get("node", {})
        .get("fieldValues", {})
        .get("nodes", [])
    )
    for node in nodes:
        if node.get("field", {}).get("name") == STATUS_FIELD_NAME:
            return node.get("name")
    return None


def get_project_field_info() -> tuple[str, str, dict[str, str]]:
    """
    Fetch the project node ID, Status field ID, and option name->ID mapping.
    Returns (project_node_id, field_id, {option_name: option_id}).
    """
    owner, _ = REPO.split("/")
    query = f"""
    query {{
      organization(login: "{owner}") {{
        projectV2(number: {PROJECT_NUMBER}) {{
          id
          fields(first: 5) {{
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
    project_node_id = project.get("id")
    fields = project.get("fields", {}).get("nodes", [])

    for field in fields:
        if field.get("name") == STATUS_FIELD_NAME:
            field_id = field["id"]
            options = {opt["name"]: opt["id"] for opt in field["options"]}
            return project_node_id, field_id, options

    print(
        f"ERROR: Could not find field '{STATUS_FIELD_NAME}' in project {PROJECT_NUMBER}",
        file=sys.stderr,
    )
    sys.exit(2)

project_node_id: Optional[str] = None
field_id: Optional[str] = None
option_ids: Optional[dict[str, str]] = None

def get_cached_field_info() -> tuple[str, str, dict[str, str]]:
    global project_node_id, field_id, option_ids
    if field_id is None:
        project_node_id, field_id, option_ids = get_project_field_info()
    return project_node_id, field_id, option_ids


def set_item_status(item_id: str, status: str) -> None:
    """
    Update the Status field of a project item to the given status option name.
    """
    log_debug("project-status", f"Setting project item {item_id} -> {status}")
    project_node_id, field_id, option_ids = get_cached_field_info()
    option_id = option_ids.get(status)
    run_github_command_with_retries(
        gh,
        (
            "project", "item-edit",
            "--id", item_id,
            "--project-id", project_node_id,
            "--field-id", field_id,
            "--single-select-option-id", option_id,
        ),
    )
