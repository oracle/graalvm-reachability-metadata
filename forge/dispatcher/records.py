# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Cross-stage dataclass records (§AR-forge-dispatcher-decomposition) carried
along the issue-to-run path (§FS-forge-issue-resolution-goal)."""

from dataclasses import dataclass

from utility_scripts.continuation_marker import ContinuationMarker

from dispatcher.config import DEFAULT_WORKTREE_BASE_REF


@dataclass(frozen=True)
class ClaimedIssue:
    issue: dict
    label: str
    item_id: str
    base_reachability_metadata_path: str
    worktree_path: str
    scratch_metrics_repo_path: str
    issue_coordinates: str
    issue_base_commit: str = DEFAULT_WORKTREE_BASE_REF
    current_coordinates: str | None = None
    new_version: str | None = None
    preflight_info_path: str | None = None
    continuation_marker: ContinuationMarker | None = None

@dataclass(frozen=True)
class WorkQueueConfig:
    label: str
    limit: int
    strategy_name: str | None = None
    random_offset: bool = False

@dataclass(frozen=True)
class IssueClaimPreflight:
    issue_number: int
    item_id: str | None
    project_status: str | None
    assignees: tuple[str, ...]
    open_blockers: tuple[int, ...]
    complete: bool

@dataclass(frozen=True)
class IssueClaimCacheObservation:
    issue_number: int
    reason: str
    assignees: tuple[str, ...] = ()
    project_status: str | None = None
    open_blockers: tuple[int, ...] = ()

@dataclass(frozen=True)
class CachedIssueClaimSkip:
    issue_number: int
    reason: str
    observed_at_epoch: float
    assignees: tuple[str, ...] = ()
    project_status: str | None = None
    open_blockers: tuple[int, ...] = ()

@dataclass(frozen=True)
class WorkflowRunResult:
    claimed_issue: ClaimedIssue
    success: bool
    started_at: float | None = None
    failure_was_external: bool = False

@dataclass(frozen=True)
class DynamicAccessCoverageSnapshot:
    covered_calls: int
    total_calls: int
    coverage_ratio: float
    source: str
