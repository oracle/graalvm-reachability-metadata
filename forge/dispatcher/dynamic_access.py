# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Chunked dynamic-access preparation and chunk PR linking
(§AR-forge-dispatcher-decomposition, §FS-forge-chunked-dynamic-access)."""


import contextlib
import json
import os
import subprocess
import sys
from ai_workflows.drivers.add_new_library_support import ScaffoldError
from ai_workflows.drivers.add_new_library_support import create_feature_branch_for_library
from ai_workflows.drivers.add_new_library_support import prepare_native_image_eligible_artifact
from ai_workflows.drivers.add_new_library_support import run_scaffold as run_new_library_scaffold
from ai_workflows.drivers.improve_library_coverage import prepare_library_update_target
from typing import Any
from utility_scripts.continuation_marker import ContinuationMarker
from utility_scripts.continuation_marker import PHASE_EXPLORE
from utility_scripts.continuation_marker import RESUMED_TREE_PHASES
from utility_scripts.dynamic_access_exhaust_report import DynamicAccessExhaustReport
from utility_scripts.dynamic_access_exhaust_report import find_dynamic_access_exhaust_report_path
from utility_scripts.dynamic_access_report import load_dynamic_access_coverage_report
from utility_scripts.gradle_environment import gradle_command_environment
from utility_scripts.java_fix_coverage_follow_up import uncovered_dynamic_access_class_count
from utility_scripts.logged_command import run_logged_command
from utility_scripts.stage_logger import log_detail
from utility_scripts.stage_logger import log_stage
from utility_scripts.strategy_loader import require_strategy_by_name
from dispatcher.config import (
    DEFAULT_DYNAMIC_ACCESS_CHUNK_CLASS_THRESHOLD,
    LABEL_CHUNKED_DYNAMIC_ACCESS,
    LABEL_LIBRARY_NEW,
    LABEL_LIBRARY_UPDATE,
    REPO,
)
from dispatcher.fixture_support import is_fixture_testing_enabled
from dispatcher.github_api import gh
from dispatcher.human_intervention import _resolve_dynamic_access_report_path
from dispatcher.issue_admin import add_issue_label
from dispatcher.issue_queue import (
    add_issue_label_to_payload,
    issue_has_label,
)
from dispatcher.records import ClaimedIssue

def dynamic_access_chunk_class_threshold() -> int:
    """Return the configured chunked dynamic-access class threshold."""
    raw_value = os.environ.get("FORGE_DYNAMIC_ACCESS_CHUNK_CLASS_THRESHOLD")
    if raw_value is None or raw_value.strip() == "":
        return DEFAULT_DYNAMIC_ACCESS_CHUNK_CLASS_THRESHOLD
    try:
        threshold = int(raw_value)
    except ValueError as exc:
        raise ValueError("FORGE_DYNAMIC_ACCESS_CHUNK_CLASS_THRESHOLD must be an integer") from exc
    if threshold < 1:
        raise ValueError("FORGE_DYNAMIC_ACCESS_CHUNK_CLASS_THRESHOLD must be >= 1")
    return threshold


def _issue_has_chunked_dynamic_access_state(issue: dict) -> bool:
    """Return whether the issue is already in chunked dynamic-access mode."""
    return issue_has_label(issue, LABEL_CHUNKED_DYNAMIC_ACCESS)


def _processed_dynamic_access_classes(report: DynamicAccessExhaustReport | None) -> set[str]:
    """Return classes that a resumed chunk must not select again."""
    if report is None:
        return set()
    return report.processed_classes()


def _continuation_processed_dynamic_access_classes(marker: ContinuationMarker | None) -> set[str]:
    """Return dynamic-access classes recorded in the continuation marker."""
    if marker is None:
        return set()
    explore_phase: dict[str, object] = marker.phases.get(PHASE_EXPLORE, {})
    exhausted_classes: object = explore_phase.get("exhaustedClasses", [])
    if not isinstance(exhausted_classes, list):
        return set()
    return {
        class_name
        for class_name in exhausted_classes
        if isinstance(class_name, str) and class_name
    }


def _dynamic_access_processed_class_count(
        report: DynamicAccessExhaustReport | None,
        marker: ContinuationMarker | None,
) -> int:
    """Return the processed class count used for chunking logs."""
    processed_classes = _processed_dynamic_access_classes(report)
    processed_classes.update(_continuation_processed_dynamic_access_classes(marker))
    return len(processed_classes)


def _continuation_active_chunk_remaining_budget(marker: ContinuationMarker | None) -> int | None:
    """Return the remaining class budget for a resumed active chunk."""
    if marker is None or marker.continue_from != PHASE_EXPLORE:
        return None
    explore_phase: dict[str, object] = marker.phases.get(PHASE_EXPLORE, {})
    raw_chunk_class_count: object = explore_phase.get("chunkClassCount")
    if raw_chunk_class_count is None:
        return None
    try:
        chunk_class_count = int(raw_chunk_class_count)
        chunk_processed_class_count = int(explore_phase.get("chunkProcessedClassCount") or 0)
    except (TypeError, ValueError):
        return None
    if chunk_class_count <= 0:
        return None
    return max(chunk_class_count - max(chunk_processed_class_count, 0), 0)


def _remaining_uncovered_dynamic_access_classes(
        report,
        exhaust_report: DynamicAccessExhaustReport | None,
        continuation_marker: ContinuationMarker | None,
) -> list[str]:
    """Return current uncovered classes not already recorded as processed."""
    processed_classes = _processed_dynamic_access_classes(exhaust_report)
    processed_classes.update(_continuation_processed_dynamic_access_classes(continuation_marker))
    return [
        class_coverage.class_name
        for class_coverage in report.classes
        if class_coverage.uncovered_calls > 0 and class_coverage.class_name not in processed_classes
    ]


def _prepare_new_library_dynamic_access_report(claimed_issue: ClaimedIssue) -> bool:
    """Run new-library setup far enough for the dispatcher dynamic-access report."""
    group, artifact, version = claimed_issue.issue_coordinates.split(":")
    with contextlib.chdir(claimed_issue.worktree_path):
        create_feature_branch_for_library(group, artifact, version)
        if not prepare_native_image_eligible_artifact(
            claimed_issue.worktree_path,
            claimed_issue.issue_coordinates,
        ):
            log_stage(
                "native-image-eligibility",
                (
                    f"{group}:{artifact} is not a Native Image metadata target; "
                    "skipping dispatcher dynamic-access pre-scan"
                ),
            )
            return False
        try:
            run_new_library_scaffold(claimed_issue.issue_coordinates)
        except ScaffoldError as exc:
            print(
                f"ERROR: Dispatcher scaffold failed for {claimed_issue.issue_coordinates}: {exc}",
                file=sys.stderr,
            )
            raise
    return True


def _prepare_library_update_dynamic_access_report(claimed_issue: ClaimedIssue) -> None:
    """Run library-update setup far enough for the dispatcher dynamic-access report."""
    group, artifact, version = claimed_issue.issue_coordinates.split(":")
    prepare_library_update_target(claimed_issue.worktree_path, group, artifact, version)


def _generate_dispatcher_dynamic_access_report(claimed_issue: ClaimedIssue) -> None:
    """Generate or refresh the dynamic-access report used for dispatcher chunking."""
    result = run_logged_command(
        [
            "./gradlew",
            "generateDynamicAccessCoverageReport",
            f"-Pcoordinates={claimed_issue.issue_coordinates}",
        ],
        cwd=claimed_issue.worktree_path,
        task_type="dynamic-access-report",
        subject=claimed_issue.issue_coordinates,
        action="generateDynamicAccessCoverageReport",
        env=gradle_command_environment(claimed_issue.worktree_path),
        stage="dynamic-access",
    )
    if result.returncode != 0:
        log_detail(
            "dynamic-access-chunking",
            (
                "Dispatcher dynamic-access report refresh failed for "
                f"{claimed_issue.issue_coordinates}; workflow fallback/failure handling will decide the run."
            ),
        )


def _load_dispatcher_dynamic_access_report(claimed_issue: ClaimedIssue):
    """Load the dispatcher-generated dynamic-access report if it is usable."""
    try:
        report = load_dynamic_access_coverage_report(_resolve_dynamic_access_report_path(claimed_issue))
    except FileNotFoundError:
        return None
    if not report.has_dynamic_access or report.total_calls <= 0:
        return None
    return report


def _resolve_dynamic_access_exhaust_report(
        claimed_issue: ClaimedIssue,
) -> tuple[DynamicAccessExhaustReport | None, str | None]:
    """Load the existing exhaust report for this issue, if one exists."""
    report_path = find_dynamic_access_exhaust_report_path(
        claimed_issue.worktree_path,
        claimed_issue.issue_coordinates,
    )
    if report_path is not None:
        return DynamicAccessExhaustReport.load(report_path), report_path
    return None, None


def _create_dynamic_access_exhaust_report(
        claimed_issue: ClaimedIssue,
) -> tuple[DynamicAccessExhaustReport, str]:
    """Create the coordinate-derived exhaust report for a newly chunked issue."""
    exhaust_report = DynamicAccessExhaustReport.create(
        coordinate=claimed_issue.issue_coordinates,
        issue_number=claimed_issue.issue["number"],
    )
    return exhaust_report, exhaust_report.default_path(claimed_issue.worktree_path)


def _strategy_has_bulk_phase(strategy_name: str | None) -> bool:
    """Return whether the selected strategy makes the chunk decision after bulk."""
    if not strategy_name:
        return False
    strategy: dict = require_strategy_by_name(strategy_name)
    workflow_name: str | None = strategy.get("workflow")
    return (
        workflow_name == "bulk_dynamic_access"
        or (
            workflow_name == "increase_dynamic_access_coverage"
            and strategy.get("primary-workflow") == "bulk_dynamic_access"
        )
    )


def _continuation_resumes_existing_tree(marker: ContinuationMarker | None) -> bool:
    """Return whether the run resumes a preserved tree the drivers keep as is."""
    return marker is not None and marker.continue_from in RESUMED_TREE_PHASES


def _prepare_dispatcher_dynamic_access_report(claimed_issue: ClaimedIssue) -> bool:
    """Build the dynamic-access report input every chunk-eligible run measures.

    Preparation precedes every chunk decision, including the ones deferred to a
    bulk phase, so no workflow starts against a report that was never
    built (§FS-forge-chunked-dynamic-access).
    """
    if _continuation_resumes_existing_tree(claimed_issue.continuation_marker):
        log_detail(
            "dynamic-access-chunking",
            (
                f"Issue #{claimed_issue.issue['number']} resumes a preserved tree; "
                "refreshing the dynamic-access report without re-preparing it."
            ),
        )
    elif claimed_issue.label == LABEL_LIBRARY_NEW:
        if not _prepare_new_library_dynamic_access_report(claimed_issue):
            return False
    else:
        _prepare_library_update_dynamic_access_report(claimed_issue)
    _generate_dispatcher_dynamic_access_report(claimed_issue)
    return True


def _dispatcher_uncovered_class_count(claimed_issue: ClaimedIssue) -> str:
    """Return the prepared report's uncovered class count, or that it is unavailable.

    A library that reports no dynamic access counts zero; only a report that was
    never written is unavailable (§FS-forge-chunked-dynamic-access).
    """
    try:
        report = load_dynamic_access_coverage_report(_resolve_dynamic_access_report_path(claimed_issue))
    except FileNotFoundError:
        return "unavailable"
    return str(uncovered_dynamic_access_class_count(report))


def prepare_dynamic_access_chunking(
        claimed_issue: ClaimedIssue,
        strategy_name: str | None,
) -> int | None:
    """Return the iterative budget or deferred post-bulk class boundary.

    Every chunk-eligible run prepares the same report input. Iterative-only work
    then keeps dispatcher-owned report selection, while a bulk phase
    receives the configured boundary and decides after its gated bulk loop, when
    its exact progress is known (§FS-forge-chunked-dynamic-access).
    """
    if claimed_issue.label not in {LABEL_LIBRARY_NEW, LABEL_LIBRARY_UPDATE}:
        return None

    threshold: int = dynamic_access_chunk_class_threshold()
    active_chunk_remaining_budget: int | None = _continuation_active_chunk_remaining_budget(
        claimed_issue.continuation_marker,
    )
    if not _prepare_dispatcher_dynamic_access_report(claimed_issue):
        return None

    if _strategy_has_bulk_phase(strategy_name):
        chunk_boundary: int = threshold
        if active_chunk_remaining_budget is not None:
            chunk_boundary = min(chunk_boundary, active_chunk_remaining_budget)
        log_detail(
            "dynamic-access-chunking",
            "Deferring chunk selection for '{strategy}' until its bulk phase completes; "
            "uncovered_classes={uncovered}, class_boundary={boundary}.".format(
                strategy=strategy_name,
                uncovered=_dispatcher_uncovered_class_count(claimed_issue),
                boundary=chunk_boundary,
            ),
        )
        return chunk_boundary

    report = _load_dispatcher_dynamic_access_report(claimed_issue)
    if report is None:
        log_detail(
            "dynamic-access-chunking",
            (
                f"No dispatcher dynamic-access report for issue #{claimed_issue.issue['number']} "
                f"({claimed_issue.issue_coordinates}); chunking disabled for this run."
            ),
        )
        return None

    exhaust_report, exhaust_report_path = _resolve_dynamic_access_exhaust_report(claimed_issue)
    already_chunked = _issue_has_chunked_dynamic_access_state(claimed_issue.issue) or exhaust_report is not None
    current_uncovered_classes = [
        class_coverage.class_name
        for class_coverage in report.classes
        if class_coverage.uncovered_calls > 0
    ]
    remaining_classes = _remaining_uncovered_dynamic_access_classes(
        report,
        exhaust_report,
        claimed_issue.continuation_marker,
    )
    processed_class_count = _dynamic_access_processed_class_count(
        exhaust_report,
        claimed_issue.continuation_marker,
    )
    if not already_chunked and len(current_uncovered_classes) <= threshold:
        log_detail(
            "dynamic-access-chunking",
            (
                f"Chunking not selected for issue #{claimed_issue.issue['number']}: "
                f"total_uncovered_classes={len(current_uncovered_classes)}, "
                f"processed_classes={processed_class_count}, "
                f"remaining_classes={len(remaining_classes)}, threshold={threshold}."
            ),
        )
        return None

    current_chunk_class_count = min(threshold, len(remaining_classes))
    active_chunk_remaining_budget = _continuation_active_chunk_remaining_budget(
        claimed_issue.continuation_marker
    )
    if active_chunk_remaining_budget is not None:
        current_chunk_class_count = min(current_chunk_class_count, active_chunk_remaining_budget)
    if current_chunk_class_count <= 0:
        log_detail(
            "dynamic-access-chunking",
            (
                f"No chunk selected for issue #{claimed_issue.issue['number']}: "
                f"total_uncovered_classes={len(current_uncovered_classes)}, "
                f"processed_classes={processed_class_count}, "
                f"remaining_classes={len(remaining_classes)}."
            ),
        )
        return None

    if exhaust_report is None or exhaust_report_path is None:
        exhaust_report, exhaust_report_path = _create_dynamic_access_exhaust_report(claimed_issue)
    exhaust_report.update_chunk_limits(threshold, current_chunk_class_count)
    exhaust_report.save(exhaust_report_path)

    if not issue_has_label(claimed_issue.issue, LABEL_CHUNKED_DYNAMIC_ACCESS):
        add_issue_label(claimed_issue.issue["number"], LABEL_CHUNKED_DYNAMIC_ACCESS)
        add_issue_label_to_payload(claimed_issue.issue, LABEL_CHUNKED_DYNAMIC_ACCESS)

    log_detail(
        "dynamic-access-chunking",
        "Chunked dynamic-access selected for issue #{issue_number}: "
        "already_chunked={already_chunked}, total_uncovered_classes={uncovered_count}, "
        "processed_classes={processed_count}, remaining_classes={remaining_count}, "
        "threshold={threshold}, chunk_class_count={chunk_count}, exhaust_report={report_path}.".format(
            issue_number=claimed_issue.issue["number"],
            already_chunked=already_chunked,
            uncovered_count=len(current_uncovered_classes),
            processed_count=processed_class_count,
            remaining_count=len(remaining_classes),
            threshold=threshold,
            chunk_count=current_chunk_class_count,
            report_path=os.path.relpath(exhaust_report_path, claimed_issue.worktree_path),
        ),
    )
    return current_chunk_class_count


def append_chunked_dynamic_access_workflow_args(
        pipeline_argv: list[str],
        claimed_issue: ClaimedIssue,
        chunk_class_count: int | None,
) -> None:
    """Append issue context and concrete chunk limits for oversized dynamic-access runs.

    The dispatcher (§AR-forge-control-plane) computes the issue-scoped chunking
    context and passes only execution flags to the workflow driver, so the
    chunk limits stay consistent with the exhaust report
    (§AR-dynamic-access-exhaust-report).
    """
    issue_number = claimed_issue.issue["number"]
    pipeline_argv.extend(["--issue-number", str(issue_number)])
    if chunk_class_count is not None:
        pipeline_argv.extend(["--chunk-class-count", str(chunk_class_count)])

def resolve_chunked_dynamic_access_exhaust_report(
        issue: dict,
        base_reachability_metadata_path: str,
        coordinate: str,
        continuation_marker: ContinuationMarker | None = None,
) -> DynamicAccessExhaustReport | None:
    """Resolve the persisted exhaust report for an already chunked issue."""
    if not issue_has_label(issue, LABEL_CHUNKED_DYNAMIC_ACCESS):
        return None
    report_path = find_dynamic_access_exhaust_report_path(base_reachability_metadata_path, coordinate)
    if report_path is None:
        if continuation_marker is not None:
            log_stage(
                "chunked-dynamic-access",
                (
                    f"Resuming chunked dynamic-access issue #{issue['number']} without an exhaust report; "
                    "processed classes will be read from the continuation marker."
                ),
            )
            return None
        raise RuntimeError(
            f"Chunked dynamic-access issue #{issue['number']} has no exhaust report for {coordinate}."
        )
    exhaust_report = DynamicAccessExhaustReport.load(report_path)
    verify_chunked_dynamic_access_previous_pr_merged(exhaust_report)
    return exhaust_report


def verify_chunked_dynamic_access_previous_pr_merged(exhaust_report: DynamicAccessExhaustReport) -> None:
    """Fail fast when continuation is requested before the previous PR merged."""
    has_publication_identity = (
        exhaust_report.latest_chunk_publication_id is not None
        or exhaust_report.latest_chunk_branch is not None
    )
    if not has_publication_identity and exhaust_report.latest_chunk_pull_request is None:
        return
    if is_fixture_testing_enabled():
        previous_publication = (
            exhaust_report.latest_chunk_publication_id
            or f"PR #{exhaust_report.latest_chunk_pull_request}"
        )
        log_stage(
            "chunked-dynamic-access",
            f"Fixture mode: treating previous chunk publication {previous_publication} as merged.",
        )
        return
    payload = resolve_chunked_dynamic_access_previous_pr(exhaust_report)
    if payload.get("state") != "MERGED":
        raise RuntimeError(
            f"Chunked dynamic-access issue #{exhaust_report.issue_number} cannot continue "
            f"before PR #{payload.get('number')} is merged "
            f"(state={payload.get('state')})."
        )


def resolve_chunked_dynamic_access_previous_pr(
        exhaust_report: DynamicAccessExhaustReport,
) -> dict[str, Any]:
    """Resolve the previous chunk PR by publication identity or legacy number."""
    publication_id = exhaust_report.latest_chunk_publication_id
    branch = exhaust_report.latest_chunk_branch
    if publication_id is not None or branch is not None:
        if publication_id is None or branch is None:
            raise RuntimeError(
                "Chunked dynamic-access publication identity requires both ID and branch"
            )
        result = gh(
            "pr",
            "list",
            "--repo",
            REPO,
            "--head",
            branch,
            "--state",
            "all",
            "--limit",
            "100",
            "--json",
            "number,state,body,mergeCommit",
            check=True,
        )
        payload = json.loads(result.stdout)
        trailer = f"Forge-Publication-ID: {publication_id}"
        matches = [
            pull_request
            for pull_request in payload
            if trailer in str(pull_request.get("body") or "").splitlines()
        ]
        if not matches:
            raise RuntimeError(
                f"No PR yet for branch {branch!r} and publication {publication_id!r}. "
                "The branch is pushed but trusted publication has not opened its PR; "
                "retry once the Forge Open PR workflow has run."
            )
        if len(matches) > 1:
            raise RuntimeError(
                f"Ambiguous PRs for branch {branch!r} and publication {publication_id!r}; "
                f"found {len(matches)}"
            )
        return matches[0]

    previous_pr = exhaust_report.latest_chunk_pull_request
    if previous_pr is None:
        raise RuntimeError("Chunked dynamic-access report has no previous publication identity")
    result = gh(
        "pr",
        "view",
        str(previous_pr),
        "--repo",
        REPO,
        "--json",
        "number,state,body,mergeCommit",
        check=True,
    )
    return dict(json.loads(result.stdout))

def verify_chunked_dynamic_access_base_contains_published_commit(
        exhaust_report: DynamicAccessExhaustReport,
        worktree_path: str,
) -> None:
    """Ensure the continuation worktree includes the latest published chunk commit."""
    commit = resolve_chunked_dynamic_access_published_base_commit(exhaust_report)
    if not commit:
        return
    result = subprocess.run(
        ["git", "merge-base", "--is-ancestor", commit, "HEAD"],
        cwd=worktree_path,
        check=False,
    )
    if result.returncode != 0:
        raise RuntimeError(
            f"Base branch does not contain latest chunked dynamic-access published commit {commit}"
        )


def resolve_chunked_dynamic_access_published_base_commit(
        exhaust_report: DynamicAccessExhaustReport,
) -> str | None:
    """Return the commit that should be present on the continuation base."""
    if is_fixture_testing_enabled():
        return exhaust_report.latest_chunk_commit
    has_previous_publication = (
        exhaust_report.latest_chunk_publication_id is not None
        or exhaust_report.latest_chunk_branch is not None
        or exhaust_report.latest_chunk_pull_request is not None
    )
    if has_previous_publication:
        payload = resolve_chunked_dynamic_access_previous_pr(exhaust_report)
        merge_commit = payload.get("mergeCommit") or {}
        oid = merge_commit.get("oid")
        if oid:
            return str(oid)
    return exhaust_report.latest_chunk_commit
