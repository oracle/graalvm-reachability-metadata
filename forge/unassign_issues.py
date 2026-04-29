# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""
Unassign assignees from open Todo issues with a given label.

Usage:
  python unassign_issues.py [--label LABEL]
"""

import argparse

from git_scripts.common_git import (
    gh,
    gh_json,
    get_issue_project_item_status,
    get_issue_project_item_statuses,
)

REPO = "oracle/graalvm-reachability-metadata"
PROJECT_NUMBER = 30
STATUS_FIELD_NAME = "Status"
STATUS_TODO = "Todo"
DEFAULT_LABEL = "library-new-request"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Unassign assignees from open Todo issues with a given label.",
    )
    parser.add_argument(
        "--label",
        default=DEFAULT_LABEL,
        help=f"Issue label to process. Defaults to {DEFAULT_LABEL}.",
    )
    return parser.parse_args()


def get_issues(label: str) -> list[dict]:
    return gh_json(
        "issue", "list",
        "--repo", REPO,
        "--label", label,
        "--state", "open",
        "--limit", "500",
        "--json", "number,assignees",
    )


def get_project_item_state(issue_number: int) -> tuple[str | None, str | None]:
    return get_issue_project_item_status(
        REPO,
        PROJECT_NUMBER,
        issue_number,
        STATUS_FIELD_NAME,
    )


def get_project_item_states(issue_numbers: list[int]) -> dict[int, tuple[str | None, str | None]]:
    return get_issue_project_item_statuses(
        REPO,
        PROJECT_NUMBER,
        issue_numbers,
        STATUS_FIELD_NAME,
    )


def should_unassign_issue(
        issue: dict,
        project_item_state: tuple[str | None, str | None] | None = None,
) -> bool:
    number = issue["number"]
    if not issue.get("assignees"):
        return False

    if project_item_state is None:
        project_item_state = get_project_item_state(number)
    item_id, current_status = project_item_state
    if not item_id:
        print(f"Skipping issue #{number}: not linked to project {PROJECT_NUMBER}")
        return False

    if current_status != STATUS_TODO:
        print(f"Skipping issue #{number}: status is '{current_status}', not '{STATUS_TODO}'")
        return False

    return True


def unassign_all(issue_number: int, assignees: list[dict]) -> None:
    logins = ", ".join(assignee["login"] for assignee in assignees)
    print(f"  Removing {logins} from issue #{issue_number}")
    gh(
        "api",
        "--method",
        "PATCH",
        f"/repos/{REPO}/issues/{issue_number}",
        "--input",
        "-",
        input_text='{"assignees":[]}',
    )


def main() -> None:
    args = parse_args()
    issues = get_issues(args.label)
    assigned = [issue for issue in issues if issue.get("assignees")]
    project_item_states = get_project_item_states([issue["number"] for issue in assigned])
    assigned_todo = [
        issue
        for issue in assigned
        if should_unassign_issue(issue, project_item_states.get(issue["number"]))
    ]
    print(
        f"Found {len(assigned_todo)} assigned Todo issue(s) out of "
        f"{len(assigned)} assigned issue(s) with label '{args.label}'"
    )

    for issue in assigned_todo:
        number = issue["number"]
        print(f"Unassigning issue #{number}...")
        unassign_all(number, issue["assignees"])

    print("Done.")


if __name__ == "__main__":
    main()
