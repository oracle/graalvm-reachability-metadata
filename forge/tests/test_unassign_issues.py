# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import argparse
import unittest
from unittest.mock import patch

import unassign_issues


class UnassignIssuesTests(unittest.TestCase):
    def test_main_batches_project_item_status_lookup_for_assigned_issues(self) -> None:
        issues = [
            {"number": 1, "assignees": [{"login": "automation-user"}]},
            {"number": 2, "assignees": [{"login": "automation-user"}]},
            {"number": 3, "assignees": []},
        ]
        project_item_states = {
            1: ("project-item-1", unassign_issues.STATUS_TODO),
            2: ("project-item-2", "In Progress"),
        }

        with patch.object(
                unassign_issues,
                "parse_args",
                return_value=argparse.Namespace(label=unassign_issues.DEFAULT_LABEL),
        ), \
                patch.object(unassign_issues, "get_issues", return_value=issues), \
                patch.object(
                    unassign_issues,
                    "get_project_item_states",
                    return_value=project_item_states,
                ) as get_project_item_states, \
                patch.object(unassign_issues, "unassign_all") as unassign_all:
            unassign_issues.main()

        get_project_item_states.assert_called_once_with([1, 2])
        unassign_all.assert_called_once_with(1, issues[0]["assignees"])


if __name__ == "__main__":
    unittest.main()
