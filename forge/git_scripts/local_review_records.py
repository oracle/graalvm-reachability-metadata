# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Verdict records of the pre-push branch review (§FS-local-branch-review).

Owns the reviewer verdict and outcome dataclasses, their persistence in pending
metrics, and the rendering of findings into `forge/FINDINGS.md`. The review
orchestration lives in `local_branch_review.py`.
"""

from __future__ import annotations

import os
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any

from git_scripts.common_git import parse_coordinate_parts, stage_and_commit
from utility_scripts.local_ci_verification import (
    FINDINGS_RELATIVE_PATH,
    LocalCIVerificationResult,
)
from utility_scripts.metrics_writer import read_pending_metrics, write_pending_metrics
from utility_scripts.stage_logger import log_stage

LOCAL_REVIEW_METRICS_KEY: str = "local_review"
FINDINGS_TITLE: str = "# Forge pre-push review findings"
FINDINGS_PREAMBLE: str = (
    "Rendered by Forge from the pre-push branch review of §FS-local-branch-review.\n"
    "Newest entry first; every finding is recorded, including one the reviewer repaired."
)
UNAVAILABLE_FINDING_TITLE: str = "Pre-push review unavailable"
UNAVAILABLE_FINDING_BODY: str = (
    "Forge could not obtain a readable pre-push review verdict. This records a review "
    "availability problem, not a reviewer finding against the branch."
)


@dataclass(frozen=True)
class LocalReviewVerdict:
    """The authoritative reviewer's structured decision."""

    decision: str
    review_comment: str
    finding_title: str
    finding_body: str
    fix_note: str
    action: str | None = None
    infrastructure_issue: dict[str, str] | None = None


@dataclass(frozen=True)
class ReviewExecution:
    """The observable result of the isolated reviewer process."""

    verdict: LocalReviewVerdict | None
    session_id: str
    changed_paths: list[str] = field(default_factory=list)


@dataclass
class LocalBranchReviewOutcome:
    """Reviewer decision plus the evidence Forge persists."""

    model: str
    session_id: str
    local_ci_verification: LocalCIVerificationResult
    verdict: LocalReviewVerdict
    changed_paths: list[str] = field(default_factory=list)

    @property
    def is_approved(self) -> bool:
        return self.verdict.decision == "approved"

    @property
    def review_comment(self) -> str:
        return self.verdict.review_comment

    def to_descriptor_payload(self) -> dict[str, Any]:
        """Return the descriptor object consumed by trusted automation."""
        payload: dict[str, Any] = {
            "decision": self.verdict.decision,
            "review_comment": self.verdict.review_comment,
            "finding_title": self.verdict.finding_title,
            "finding_body": self.verdict.finding_body,
            "fix_note": self.verdict.fix_note,
            "model": self.model,
            "changed_paths": list(self.changed_paths[:200]),
        }
        # A session identifier, not a log path: the operator's `logs/` tree is
        # gitignored, so a path here resolves for nobody who reads the pull
        # request. §AR-publication-descriptor
        if self.session_id:
            payload["session_id"] = self.session_id
        if self.verdict.action is not None:
            payload["action"] = self.verdict.action
        return payload

    @classmethod
    def from_descriptor_payload(
            cls,
            payload: dict[str, Any],
            local_ci_verification: LocalCIVerificationResult,
    ) -> LocalBranchReviewOutcome:
        """Reconstruct a persisted verdict without re-running the reviewer."""
        verdict = LocalReviewVerdict(
            decision=str(payload["decision"]),
            action=(
                str(payload["action"])
                if isinstance(payload.get("action"), str)
                else None
            ),
            review_comment=str(payload["review_comment"]),
            finding_title=str(payload["finding_title"]),
            finding_body=str(payload["finding_body"]),
            fix_note=str(payload["fix_note"]),
        )
        return cls(
            model=str(payload["model"]),
            session_id=str(payload.get("session_id") or ""),
            local_ci_verification=local_ci_verification,
            verdict=verdict,
            changed_paths=[str(path) for path in payload.get("changed_paths", [])],
        )


def _log_review(message: str, indent_level: int = 0) -> None:
    log_stage("local-review", message, indent_level=indent_level)


def _record_outcome_finding(
        *,
        repo_path: str,
        coordinates: str,
        descriptor_input: Any,
        outcome: LocalBranchReviewOutcome,
) -> None:
    """Record reviewer findings and the fixed outage finding after any checkpoint reset."""
    if outcome.verdict is None:
        title: str = UNAVAILABLE_FINDING_TITLE
        body: str = UNAVAILABLE_FINDING_BODY
    elif outcome.verdict.finding_title:
        title = outcome.verdict.finding_title
        body = outcome.verdict.finding_body
    else:
        return
    _record_finding(
        repo_path=repo_path,
        coordinates=coordinates,
        descriptor_input=descriptor_input,
        title=title,
        body=body,
    )


def _record_finding(
        *,
        repo_path: str,
        coordinates: str,
        descriptor_input: Any,
        title: str,
        body: str,
) -> None:
    """Render a stable newest-first finding entry and commit it."""
    findings_path: str = os.path.join(repo_path, FINDINGS_RELATIVE_PATH)
    entry: str = _render_finding_entry(coordinates, descriptor_input, title, body)
    existing: str = ""
    if os.path.isfile(findings_path):
        with open(findings_path, "r", encoding="utf-8") as findings_file:
            existing = findings_file.read()
    if existing.startswith(FINDINGS_TITLE):
        header, _, entries = existing.partition("\n## ")
        header_block: str = header.rstrip("\n")
        remaining: str = f"## {entries}" if entries else ""
    else:
        header_block = f"{FINDINGS_TITLE}\n\n{FINDINGS_PREAMBLE}"
        remaining = existing.strip()
    updated: str = f"{header_block}\n\n{entry}"
    if remaining:
        updated += f"\n{remaining.rstrip()}\n"
    os.makedirs(os.path.dirname(findings_path), exist_ok=True)
    with open(findings_path, "w", encoding="utf-8") as findings_file:
        findings_file.write(updated)
    stage_and_commit(
        [FINDINGS_RELATIVE_PATH],
        f"Record pre-push review finding for {coordinates}",
        cwd=repo_path,
    )
    _log_review(f"Recorded '{title}' in {FINDINGS_RELATIVE_PATH}", indent_level=1)


def _render_finding_entry(
        coordinates: str,
        descriptor_input: Any,
        title: str,
        body: str,
) -> str:
    """Render one finding only from its reviewer-supplied title and body."""
    group, artifact, version = parse_coordinate_parts(coordinates)
    issue_number = getattr(descriptor_input, "issue_number", None)
    issue_reference: str = f" (#{issue_number})" if issue_number else ""
    date: str = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    return "\n".join([
        f"## {date} — {group}:{artifact}:{version}{issue_reference}",
        "",
        f"**{title}**",
        "",
        body,
        "",
    ])


def _load_persisted_outcome(
        metrics_repo_path: str | None,
        local_ci_verification: LocalCIVerificationResult,
        descriptor_input: Any,
) -> LocalBranchReviewOutcome | None:
    if metrics_repo_path is None:
        return None
    try:
        metrics: dict[str, Any] = read_pending_metrics(metrics_repo_path)
    except (OSError, ValueError, TypeError):
        return None
    state = metrics.get(LOCAL_REVIEW_METRICS_KEY)
    if not isinstance(state, dict):
        return None
    expected_timestamp = getattr(descriptor_input, "timestamp", None)
    if state.get("timestamp") != expected_timestamp:
        return None
    payload = state.get("outcome")
    if not isinstance(payload, dict):
        return None
    try:
        return LocalBranchReviewOutcome.from_descriptor_payload(
            payload, local_ci_verification,
        )
    except (KeyError, TypeError, ValueError):
        return None


def _persist_outcome(
        metrics_repo_path: str | None,
        descriptor_input: Any,
        outcome: LocalBranchReviewOutcome,
) -> None:
    """Stage the verdict with the run's durable in-flight publication data."""
    if metrics_repo_path is None:
        return
    try:
        metrics: dict[str, Any] = read_pending_metrics(metrics_repo_path)
    except (OSError, ValueError, TypeError):
        metrics = {}
    metrics[LOCAL_REVIEW_METRICS_KEY] = {
        "timestamp": getattr(descriptor_input, "timestamp", None),
        "outcome": outcome.to_descriptor_payload(),
    }
    write_pending_metrics(metrics_repo_path, metrics)
