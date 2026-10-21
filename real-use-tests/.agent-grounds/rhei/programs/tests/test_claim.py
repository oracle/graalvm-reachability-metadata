# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Unit tests for the launcher's issue selection and claim.

§FS-real-use-test-generation.1 states the rules; §AR-real-use-test-launcher the wiring.
"""
from __future__ import annotations

import io
import os
import sys
import unittest
from contextlib import redirect_stderr, redirect_stdout
from typing import Any
from unittest.mock import patch

PROGRAMS: str = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FORGE: str = os.path.join(PROGRAMS, "..", "..", "..", "..", "forge")
sys.path.insert(0, PROGRAMS)
sys.path.insert(0, os.path.abspath(FORGE))

import claim  # noqa: E402


def _issue(number: int, title: str, status: str = "Todo", labels: tuple[str, ...] = ()) -> dict[str, Any]:
    return {
        "number": number,
        "title": title,
        "state": "OPEN",
        "labels": [{"name": name} for name in ("real-use-test", *labels)],
        "projectItems": [{"title": claim.PROJECT_TITLE, "status": {"name": status}}],
    }


class ClaimTest(unittest.TestCase):

    def test_candidates_keep_todo_single_coordinate_issues_most_urgent_then_newest(self) -> None:
        listed: list[dict[str, Any]] = [
            _issue(1, "Real-use tests for org.example:a:1.0.0"),
            _issue(2, "Real-use tests for org.example:b:1.0.0", status="In Progress"),
            _issue(3, "Real-use tests for org.example:c:1.0.0 and org.example:d:1.0.0"),
            _issue(4, "Real-use tests for org.example:e:1.0.0", labels=("priority",)),
            _issue(5, "Real-use tests for org.example:f:1.0.0"),
            _issue(6, "Real-use tests for org.example:g:1.0.0", labels=("high-priority",)),
        ]
        with redirect_stderr(io.StringIO()) as skipped:
            numbers: list[int] = [candidate["number"] for candidate in claim.candidates(listed)]

        self.assertEqual([6, 4, 5, 1], numbers)
        self.assertIn("Skipping issue #3", skipped.getvalue())

    def test_claim_next_moves_past_a_lost_claim_and_keeps_stdout_for_the_number(self) -> None:
        listed: list[dict[str, Any]] = [
            _issue(7, "Real-use tests for org.example:a:1.0.0"),
            _issue(8, "Real-use tests for org.example:b:1.0.0"),
        ]
        attempts: list[int] = []

        def fake_claim(candidate: dict[str, Any], user: str, required_label: str) -> str | None:
            self.assertEqual(("octocat", "real-use-test"), (user, required_label))
            attempts.append(candidate["number"])
            print("forge narration")
            return None if candidate["number"] == 8 else "item-7"

        with patch.object(claim, "list_open_issues", return_value=listed), \
                patch.object(claim, "get_authenticated_user", return_value="octocat"), \
                patch.object(claim, "try_claim_issue", side_effect=fake_claim), \
                redirect_stdout(io.StringIO()) as stdout, redirect_stderr(io.StringIO()):
            exit_code: int = claim.main([])

        self.assertEqual(0, exit_code)
        self.assertEqual([8, 7], attempts)
        self.assertEqual("7\n", stdout.getvalue())

    def test_no_claimable_issue_fails(self) -> None:
        with patch.object(claim, "list_open_issues", return_value=[]), \
                patch.object(claim, "get_authenticated_user", return_value="octocat"), \
                redirect_stdout(io.StringIO()) as stdout, redirect_stderr(io.StringIO()) as stderr:
            exit_code: int = claim.main([])

        self.assertEqual(1, exit_code)
        self.assertEqual("", stdout.getvalue())
        self.assertIn("No eligible real-use-test issue", stderr.getvalue())

    def test_release_reverts_only_through_forge_ownership_check(self) -> None:
        with patch.object(claim, "get_project_item_state", return_value=("item-9", "In Progress")), \
                patch.object(claim, "get_authenticated_user", return_value="octocat"), \
                patch.object(claim, "revert_issue_claim_if_still_owned_by_user") as revert:
            self.assertEqual(0, claim.main(["--release", "9"]))

        revert.assert_called_once_with("item-9", 9, "octocat", "the run's workspace could not be created")


if __name__ == "__main__":
    unittest.main()
