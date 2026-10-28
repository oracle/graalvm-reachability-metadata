# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""What the review of published pull requests reads from GitHub (§FS-published-pr-review).

Every read is fresh, so a pass on another host reaches the same decisions: the
open Forge pull requests, one pull request as it is now, the checks of a head
commit with the tail of each failed log, when `in-review` was added, and the
head markers the Forge account left (§AR-forge-dispatcher-decomposition.1).
"""
from __future__ import annotations

import json
import re
from dataclasses import dataclass
from datetime import datetime
from typing import Any

from dispatcher.config import REPO
from dispatcher.github_api import gh, gh_json

IN_REVIEW: str = "in-review"
REVIEWED: str = "reviewed"
# The Forge route labels of §FS-forge-functional-spec, whose pull requests the review takes.
ROUTE_LABELS: tuple[str, ...] = (
    "library-new-request",
    "library-update-request",
    "fixes-javac-fail",
    "fixes-java-run-fail",
    "fixes-native-image-run-fail",
)
FIELDS: str = ("number,state,createdAt,title,body,headRefName,headRefOid,headRepository,reviewDecision,labels,"
               "files,autoMergeRequest")
MARKER: re.Pattern[str] = re.compile(r"<!-- forge-review:([0-9a-f]{40}):(rerun|push|issue) -->")
DESCRIPTOR: re.Pattern[str] = re.compile(r"^stats/([^/]+)/([^/]+)/([^/]+)/forge-publication\.json$")
JOB_URL: re.Pattern[str] = re.compile(r"/actions/runs/(\d+)/job/(\d+)")
FAILED: frozenset[str] = frozenset({"failure", "cancelled", "timed_out", "action_required", "startup_failure",
                                    "stale", "error"})
PASSED: frozenset[str] = frozenset({"success", "neutral", "skipped"})
LOG_LINES: int = 150
LOGGED_JOBS: int = 12


@dataclass
class Check:
    """One check run or commit status on a head commit."""

    name: str
    state: str
    conclusion: str
    url: str
    run: str | None = None
    job: str | None = None


def parse_time(text: str) -> datetime:
    """A GitHub timestamp such as `2026-10-17T17:32:49Z`."""
    return datetime.fromisoformat(text.replace("Z", "+00:00"))


def labels(pull: dict[str, Any]) -> set[str]:
    return {label["name"] for label in pull.get("labels", [])}


def route_label(pull: dict[str, Any]) -> str | None:
    return next((label for label in ROUTE_LABELS if label in labels(pull)), None)


def coordinate(pull: dict[str, Any]) -> str | None:
    """The coordinate whose publication descriptor the pull request carries."""
    for entry in pull.get("files", []):
        matched: re.Match[str] | None = DESCRIPTOR.match(str(entry.get("path", "")))
        if matched is not None:
            return ":".join(matched.groups())
    return None


def open_pull_requests() -> list[int]:
    """Open pull requests on upstream `ai/**` branches, oldest first (§FS-published-pr-review.1)."""
    found: list[dict[str, Any]] = gh_json("pr", "list", "--repo", REPO, "--state", "open", "--search", "head:ai/",
                                          "--limit", "200", "--json", "number,createdAt")
    return [pull["number"] for pull in sorted(found, key=lambda pull: parse_time(pull["createdAt"]))]


def pull_request(number: int) -> dict[str, Any]:
    """Pull request `number` as it is now, read right before it is judged."""
    return gh_json("pr", "view", str(number), "--repo", REPO, "--json", FIELDS)


def label_added_at(number: int) -> datetime:
    """When `in-review` was last added to pull request `number`, from its issue timeline."""
    lines: list[str] = gh("api", "--paginate", f"repos/{REPO}/issues/{number}/timeline", "--jq",
                          f'.[] | select(.event == "labeled" and .label.name == "{IN_REVIEW}") | .created_at'
                          ).stdout.split()
    if not lines:
        raise RuntimeError(f"#{number} carries {IN_REVIEW} but its timeline has no labeled event for it")
    return max(parse_time(line) for line in lines)


def _json_lines(text: str) -> list[dict[str, Any]]:
    return [json.loads(line) for line in text.splitlines() if line.strip()]


def head_checks(sha: str) -> list[Check]:
    """Every check run and commit status on `sha`, the latest attempt of each.

    §FS-published-pr-review.2
    """
    checks: list[Check] = []
    for run in _json_lines(gh("api", "--paginate", f"repos/{REPO}/commits/{sha}/check-runs?per_page=100",
                              "--jq", ".check_runs[]").stdout):
        conclusion: str = str(run.get("conclusion") or "")
        state: str = ("running" if run.get("status") != "completed"
                      else "failed" if conclusion in FAILED else "passed")
        url: str = str(run.get("html_url") or run.get("details_url") or "")
        matched: re.Match[str] | None = JOB_URL.search(url)
        checks.append(Check(str(run.get("name", "?")), state, conclusion, url,
                            matched.group(1) if matched else None, matched.group(2) if matched else None))
    for status in _json_lines(gh("api", f"repos/{REPO}/commits/{sha}/status", "--jq", ".statuses[]").stdout):
        value: str = str(status.get("state", ""))
        state = "running" if value == "pending" else "failed" if value in FAILED else "passed"
        checks.append(Check(str(status.get("context", "?")), state, value, str(status.get("target_url") or "")))
    return checks


def failed_log(job: str) -> str:
    """The tail of a failed job's log, where Gradle and JUnit put the verdict."""
    completed = gh("run", "view", "--job", job, "--repo", REPO, "--log-failed", check=False, quiet=True)
    lines: list[str] = [re.sub(r"^[^\t]*\t[^\t]*\t", "", line) for line in completed.stdout.splitlines()]
    lines = [re.sub(r"^\d{4}-\d{2}-\d{2}T[0-9:.]+Z ", "", line) for line in lines if line.strip()]
    return "\n".join(lines[-LOG_LINES:]) or completed.stderr.strip() or "no failed log"


def evidence(sha: str, checks: list[Check]) -> str:
    """The head's checks as Markdown, with the tail of each failed job's log."""
    failed: list[Check] = [check for check in checks if check.state == "failed"]
    running: list[Check] = [check for check in checks if check.state == "running"]
    passed: list[Check] = [check for check in checks if check.state == "passed"]
    parts: list[str] = [f"# Checks on `{sha}`", "",
                        f"{len(failed)} failed, {len(running)} running, {len(passed)} passed.", ""]
    parts += ["## Failed", ""] + [f"- {check.name} — {check.conclusion} — {check.url}" for check in failed]
    parts += ["", "## Passed", ""] + [f"- {check.name}" for check in passed]
    for check in [check for check in failed if check.job][:LOGGED_JOBS]:
        parts += ["", f"## Log of {check.name} (run {check.run}, job {check.job})", "", "```",
                  failed_log(str(check.job)), "```"]
    return "\n".join(parts) + "\n"


def markers(number: int, login: str) -> list[tuple[str, str]]:
    """The `(head, action)` markers the Forge account left on the pull request, oldest first."""
    comments: list[dict[str, Any]] = gh_json("pr", "view", str(number), "--repo", REPO, "--json",
                                             "comments")["comments"]
    return [(matched.group(1), matched.group(2)) for comment in comments
            if (comment.get("author") or {}).get("login") == login
            for matched in MARKER.finditer(str(comment.get("body", "")))]


def approved_by(number: int, login: str, head: str) -> bool:
    """Whether `login` approved exactly `head` and auto-merge is armed, or the pull request merged."""
    pull: dict[str, Any] = gh_json("pr", "view", str(number), "--repo", REPO, "--json",
                                   "state,reviews,autoMergeRequest")
    if pull.get("state") == "MERGED":
        return True
    approved: bool = any((review.get("author") or {}).get("login") == login and review.get("state") == "APPROVED"
                         and (review.get("commit") or {}).get("oid", head) == head
                         for review in pull.get("reviews", []))
    return approved and pull.get("autoMergeRequest") is not None


def rerun_started(head: str) -> bool:
    """Whether a workflow run on `head` is running again or is past its first attempt."""
    runs: list[dict[str, Any]] = gh_json("run", "list", "--repo", REPO, "--commit", head, "--limit", "100",
                                         "--json", "databaseId,status,attempt")
    return any(run.get("status") != "completed" or int(run.get("attempt", 1)) > 1 for run in runs)
