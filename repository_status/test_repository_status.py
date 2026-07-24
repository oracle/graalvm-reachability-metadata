"""Fixture tests for repository issue status. §FS-repository-status-report"""

from __future__ import annotations

import json
import subprocess
import unittest
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from unittest.mock import MagicMock, patch

from repository_status.github_source import _run_issue_list
from repository_status.measurements import build_report
from repository_status.model import Issue, Policy, StatusReportError, issue_from_github, load_policy
from repository_status.renderers import render_html, render_json


POLICY_PATH: Path = Path(__file__).with_name("policy.json")
GENERATED_AT: datetime = datetime(2026, 7, 23, 12, 0, tzinfo=timezone.utc)


def github_issue(
        number: int,
        title: str,
        state: str,
        created_at: str,
        updated_at: str,
        labels: list[str],
        project_status: str | None,
        closed_at: str | None = None,
) -> dict[str, Any]:
    """Create a representative `gh issue list` record."""
    project_items: list[dict[str, Any]] = []
    if project_status is not None:
        project_items.append(
            {
                "title": "GraalVM Reachability Metadata",
                "status": {"name": project_status, "optionId": "fixture"},
            }
        )
    return {
        "number": number,
        "title": title,
        "url": f"https://github.com/oracle/graalvm-reachability-metadata/issues/{number}",
        "state": state,
        "createdAt": created_at,
        "closedAt": closed_at,
        "updatedAt": updated_at,
        "labels": [{"name": label} for label in labels],
        "projectItems": project_items,
    }


class RepositoryStatusTest(unittest.TestCase):
    """Verify the shared measurements and both representations."""

    def setUp(self) -> None:
        self.policy: Policy = load_policy(POLICY_PATH)
        open_payloads: list[dict[str, Any]] = [
            github_issue(
                101,
                "High issue",
                "OPEN",
                "2026-07-20T12:00:00Z",
                "2026-07-22T12:00:00Z",
                ["priority", "high-priority"],
                "Todo",
            ),
            github_issue(
                102,
                "Priority issue",
                "OPEN",
                "2026-06-23T12:00:00Z",
                "2026-07-20T12:00:00Z",
                ["priority"],
                "In Progress",
            ),
            github_issue(
                103,
                "Normal issue",
                "OPEN",
                "2026-04-22T12:00:00Z",
                "2026-07-01T12:00:00Z",
                [],
                "Done",
            ),
            github_issue(
                104,
                "Unsafe <script>alert(1)</script>",
                "OPEN",
                "2026-07-23T11:00:00Z",
                "2026-07-23T11:00:00Z",
                [],
                None,
            ),
        ]
        closed_payloads: list[dict[str, Any]] = [
            github_issue(
                105,
                "Recently resolved priority",
                "CLOSED",
                "2026-07-01T12:00:00Z",
                "2026-07-22T12:00:00Z",
                ["priority"],
                "Done",
                "2026-07-22T12:00:00Z",
            ),
            github_issue(
                106,
                "Old resolution",
                "CLOSED",
                "2026-06-01T12:00:00Z",
                "2026-06-10T12:00:00Z",
                [],
                "Done",
                "2026-06-10T12:00:00Z",
            ),
        ]
        self.open_issues: list[Issue] = [
            issue_from_github(payload, self.policy.project_title) for payload in open_payloads
        ]
        self.closed_issues: list[Issue] = [
            issue_from_github(payload, self.policy.project_title) for payload in closed_payloads
        ]
        self.report: dict[str, Any] = build_report(
            policy=self.policy,
            open_issues=self.open_issues,
            recently_closed_issues=self.closed_issues,
            generated_at=GENERATED_AT,
            window_days=30,
        )

    def test_priority_backlog_age_debt_and_order(self) -> None:
        """Priority remains label-derived while age contributes debt. §FS-repository-status-report.2"""
        self.assertEqual(self.report["summary"]["weightedBacklog"], 17)
        self.assertEqual(self.report["summary"]["weightedAgeDebt"], 272)
        self.assertEqual(self.report["summary"]["priorityAgeDebt"], 180)
        self.assertEqual(
            self.report["ageDebtMeter"],
            {"value": 180, "maximum": 20000, "fillPercent": 0.9},
        )
        self.assertEqual(self.report["tiers"]["priority"]["weight"], 5)
        self.assertEqual(self.report["tiers"]["normal"]["oldestAgeDays"], 92)
        self.assertEqual(
            [item["number"] for item in self.report["attentionQueue"]],
            [101, 102, 103, 104],
        )

    def test_age_bands_project_state_and_warnings(self) -> None:
        """Age and inconsistent project data stay visible. §FS-repository-status-report.3"""
        self.assertEqual(
            self.report["ageBands"],
            {"fresh": 2, "aging": 1, "old": 0, "stale": 1},
        )
        self.assertEqual(
            self.report["project"],
            {"todo": 1, "inProgress": 1, "done": 1, "unknown": 1},
        )
        self.assertEqual(
            [warning["code"] for warning in self.report["warnings"]],
            ["BOTH_PRIORITY_LABELS", "OPEN_ISSUE_DONE"],
        )

    def test_recent_weighted_flow(self) -> None:
        """Recent flow separates progress from current state. §FS-repository-status-report.3"""
        flow: dict[str, Any] = self.report["flow"]
        self.assertEqual(flow["opened"], 4)
        self.assertEqual(flow["resolved"], 1)
        self.assertEqual(flow["weightedOpened"], 21)
        self.assertEqual(flow["weightedResolved"], 5)
        self.assertEqual(flow["weightedNetChange"], 16)
        self.assertEqual(flow["weightedBurnRatio"], 0.2381)
        self.assertEqual(flow["weightedBalancePercent"], 19.2)
        self.assertEqual(flow["direction"], "growing")

    def test_agent_json_is_round_trippable(self) -> None:
        """Agent output remains one stable JSON document. §FS-repository-status-report.4"""
        rendered: str = render_json(self.report)
        parsed: dict[str, Any] = json.loads(rendered)
        self.assertEqual(parsed["schemaVersion"], "1.0")
        self.assertEqual(parsed["attentionQueue"][0]["priority"], "high")
        self.assertTrue(rendered.endswith("\n"))

    def test_human_html_is_self_contained_and_escaped(self) -> None:
        """Human output is searchable HTML and escapes issue data. §FS-repository-status-report.4"""
        rendered: str = render_html(self.report)
        self.assertIn("<!doctype html>", rendered)
        self.assertIn('id="issue-filter"', rendered)
        self.assertIn('role="meter"', rendered)
        self.assertIn('aria-valuenow="19.2"', rendered)
        self.assertIn('aria-label="Priority age debt"', rendered)
        self.assertIn('aria-valuemax="20000"', rendered)
        self.assertLess(rendered.index("30-day flow"), rendered.index("Priority age debt"))
        self.assertLess(rendered.index("Priority pressure"), rendered.index("30-day flow"))
        self.assertNotIn("Unresolved issues", rendered)
        self.assertNotIn("Age distribution", rendered)
        self.assertNotIn("Project state", rendered)
        self.assertIn("Unsafe &lt;script&gt;alert(1)&lt;/script&gt;", rendered)
        self.assertNotIn("Unsafe <script>alert(1)</script>", rendered)

    @patch("repository_status.github_source.subprocess.run")
    def test_recent_flow_query_fails_at_github_search_cap(
            self,
            run_mock: MagicMock,
    ) -> None:
        """Recent flow cannot silently truncate at GitHub's search cap."""
        run_mock.return_value = subprocess.CompletedProcess(
            args=[],
            returncode=0,
            stdout=json.dumps([{}] * 1_000),
            stderr="",
        )
        with self.assertRaisesRegex(StatusReportError, "GitHub search limit of 1000"):
            _run_issue_list("owner/repository", "closed", 10_000, "closed:>=2026-01-01")
        command: list[str] = run_mock.call_args.args[0]
        limit_index: int = command.index("--limit") + 1
        self.assertEqual(command[limit_index], "1000")


if __name__ == "__main__":
    unittest.main()
