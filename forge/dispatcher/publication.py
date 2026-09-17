# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Publication handoff for successful runs
(§AR-forge-dispatcher-decomposition, §FS-forge-publication-readiness)."""


import os
import shutil
from ai_workflows.core.workflow_strategy import RUN_STATUS_CHUNK_READY
from ai_workflows.drivers.library_update_router import LibraryUpdateRoute
from ai_workflows.drivers.library_update_router import ROUTE_FIX_JAVAC
from ai_workflows.drivers.library_update_router import ROUTE_FIX_JAVA_RUN
from ai_workflows.drivers.library_update_router import ROUTE_FIX_NI_RUN
from ai_workflows.drivers.library_update_router import load_library_update_route
from collections.abc import Callable
from dataclasses import dataclass
from git_scripts.publish_improve_coverage import main as run_publish_improve_coverage
from git_scripts.publish_java_run_fix import main as run_publish_java_run_fix
from git_scripts.publish_javac_fix import main as run_publish_javac_fix
from git_scripts.publish_new_library_support import main as run_publish_new_library_support
from git_scripts.publish_ni_run_fix import main as run_publish_ni_run_fix
from git_scripts.publish_not_for_native_image import main as run_publish_not_for_native_image
from utility_scripts.continuation_marker import ContinuationMarker
from utility_scripts.continuation_marker import PHASE_PUBLICATION
from utility_scripts.continuation_marker import continuation_marker_path
from utility_scripts.continuation_marker import load_continuation_marker
from utility_scripts.continuation_marker import save_phase_update
from utility_scripts.dynamic_access_exhaust_report import find_dynamic_access_exhaust_report_path
from utility_scripts.java_fix_coverage_follow_up import ensure_coverage_follow_up_issue
from utility_scripts.library_preparation_preflight import LIBRARY_PREPARATION_PREFLIGHT_FILENAME
from utility_scripts.metadata_index import coordinate_parts as metadata_coordinate_parts
from utility_scripts.metadata_index import is_not_for_native_image
from utility_scripts.metrics_writer import PENDING_METRICS_FILENAME
from utility_scripts.metrics_writer import load_execution_metrics_for_timestamp
from utility_scripts.metrics_writer import write_pending_metrics
from utility_scripts.run_location import STEP_PUBLISH_BRANCH
from utility_scripts.run_location import enter_phase
from utility_scripts.run_location import run_step
from utility_scripts.stage_logger import log_stage
from dispatcher.config import (
    FIXTURE_COVERAGE_FOLLOW_UP_ISSUE_OFFSET,
    FIXTURE_PREFLIGHT_EVIDENCE_DIRNAME,
    LABEL_CHUNKED_DYNAMIC_ACCESS,
    LABEL_JAVAC_FAIL,
    LABEL_JAVA_RUN_FAIL,
    LABEL_LIBRARY_NEW,
    LABEL_LIBRARY_UPDATE,
    LABEL_NI_RUN_FAIL,
    LABEL_NOT_FOR_NATIVE_IMAGE,
    LABEL_PR_JAVAC_FIX,
    LABEL_PR_JAVA_RUN_FIX,
    LABEL_PR_LIBRARY_UPDATE,
    LABEL_PR_NI_RUN_FIX,
    REPO,
)
from dispatcher.continuation import library_update_route_artifact_root
from dispatcher.failure_preservation import record_publication_metrics_from_pending
from dispatcher.fixture_support import (
    get_fixture_issue_artifact_dir,
    is_fixture_testing_enabled,
)
from dispatcher.human_intervention import load_pending_run_metrics
from dispatcher.issue_admin import add_issue_label
from dispatcher.records import ClaimedIssue
from dispatcher.worktrees import require_claimed_issue_worktree

@dataclass(frozen=True)
class PublicationHandoff:
    script_name: str
    runner_name: str
    runner: Callable[[list[str]], None]
    argv: list[str]
    issue_number: int
    issue_label: str
    result_label: str
    coordinates: str | None
    current_coordinates: str | None
    new_version: str | None
    worktree_path: str
    scratch_metrics_path: str
    workflow_status: str | None
    chunked_dynamic_access_args: list[str]
    dynamic_access_exhaust_report_path: str | None
    chunked_dynamic_access_final: bool | None
    not_for_native_image: bool = False
    publication_kind: str | None = None
    coverage_follow_up_issue_number: int | None = None
    coverage_follow_up_class_count: int | None = None
    coverage_follow_up_class_threshold: int | None = None

    def to_json(self) -> dict:
        return {
            "script_name": self.script_name,
            "runner_name": self.runner_name,
            "argv": list(self.argv),
            "issue_number": self.issue_number,
            "issue_label": self.issue_label,
            "result_label": self.result_label,
            "coordinates": self.coordinates,
            "current_coordinates": self.current_coordinates,
            "new_version": self.new_version,
            "worktree_path": self.worktree_path,
            "scratch_metrics_path": self.scratch_metrics_path,
            "workflow_status": self.workflow_status,
            "chunked_dynamic_access_args": list(self.chunked_dynamic_access_args),
            "dynamic_access_exhaust_report_path": self.dynamic_access_exhaust_report_path,
            "chunked_dynamic_access_final": self.chunked_dynamic_access_final,
            "not_for_native_image": self.not_for_native_image,
            "publication_kind": self.publication_kind,
        }

def build_chunked_dynamic_access_pr_args(
        exhaust_report_path: str | None,
        workflow_status: str | None,
) -> list[str]:
    """Build PR flags for an active chunked dynamic-access issue.

    Non-final chunks publish with continuation state and must not close the
    backing issue, per the chunk PR linking contract
    (§AR-chunked-dynamic-access-pr-linking, §AR-issue-linking).
    """
    if exhaust_report_path is None:
        return []
    args = ["--chunked-dynamic-access"]
    if workflow_status != RUN_STATUS_CHUNK_READY:
        args.append("--chunk-final")
    return args


def _require_publication_value(value: str | None, field_name: str, claimed_issue: ClaimedIssue) -> str:
    if value is None:
        raise ValueError(
            f"Issue #{claimed_issue.issue['number']} {claimed_issue.label} publication requires {field_name}."
        )
    return value


def _load_library_update_publication_route(claimed_issue: ClaimedIssue) -> LibraryUpdateRoute | None:
    if claimed_issue.label != LABEL_LIBRARY_UPDATE:
        return None
    return load_library_update_route(library_update_route_artifact_root(claimed_issue))


def _pending_run_metrics_path(claimed_issue: ClaimedIssue) -> str:
    """Return the transient metrics file path for this claimed issue."""
    return os.path.join(
        claimed_issue.scratch_metrics_repo_path,
        PENDING_METRICS_FILENAME,
    )


def record_pending_publication_metrics_for_resume(claimed_issue: ClaimedIssue) -> None:
    """Persist the pending metrics reconstruction inputs in the continuation marker."""
    marker_path = continuation_marker_path(claimed_issue.worktree_path)
    marker = load_continuation_marker(marker_path)
    if marker is None:
        return
    if record_publication_metrics_from_pending(marker, claimed_issue.scratch_metrics_repo_path):
        marker.save(marker_path)


def _restore_pending_run_metrics_from_marker(
        claimed_issue: ClaimedIssue,
        marker: ContinuationMarker,
) -> bool:
    """Restore transient metrics from durable metrics plus marker-local extras."""
    marker_metrics = marker.publication_metrics
    if not isinstance(marker_metrics, dict):
        return False
    library = marker_metrics.get("library")
    timestamp = marker_metrics.get("timestamp")
    if not isinstance(library, str) or not isinstance(timestamp, str):
        return False
    run_metrics = load_execution_metrics_for_timestamp(claimed_issue.worktree_path, library, timestamp)
    if run_metrics is None:
        log_stage(
            "publication",
            (
                "Could not restore pending metrics from continuation marker because no durable "
                f"execution metrics entry matched {library} at {timestamp}."
            ),
        )
        return False
    extras = marker_metrics.get("extras")
    if isinstance(extras, dict):
        run_metrics.update(extras)
    write_pending_metrics(claimed_issue.scratch_metrics_repo_path, run_metrics)
    log_stage(
        "publication",
        (
            "Restored missing pending metrics from durable execution metrics and "
            f"continuation marker extras for {library}."
        ),
    )
    return True


def restore_pending_run_metrics_from_execution_metrics(claimed_issue: ClaimedIssue) -> None:
    """Restore or snapshot transient publication metrics for resumable PR creation."""
    pending_metrics_path = _pending_run_metrics_path(claimed_issue)
    if os.path.isfile(pending_metrics_path):
        record_pending_publication_metrics_for_resume(claimed_issue)
        return

    marker = load_continuation_marker(continuation_marker_path(claimed_issue.worktree_path))
    if marker is not None:
        _restore_pending_run_metrics_from_marker(claimed_issue, marker)


def build_publication_handoff(
        claimed_issue: ClaimedIssue,
        coverage_follow_up_issue_number: int | None = None,
        coverage_follow_up_class_count: int | None = None,
        coverage_follow_up_class_threshold: int | None = None,
) -> PublicationHandoff:
    """Build the live-or-fixture PR publication handoff.

    The dispatcher makes the routing decision once, then either executes the
    matching git script or records a dry-run fixture handoff
    (§AR-forge-verification-publication-boundary).
    """
    require_claimed_issue_worktree(claimed_issue, "successful finalization")
    issue_number = claimed_issue.issue["number"]
    restore_pending_run_metrics_from_execution_metrics(claimed_issue)
    exhaust_report_path = find_dynamic_access_exhaust_report_path(
        claimed_issue.worktree_path,
        claimed_issue.issue_coordinates,
    )
    run_metrics = load_pending_run_metrics(claimed_issue.scratch_metrics_repo_path)
    workflow_status = None if run_metrics is None else run_metrics.get("status")
    issue_number_args = ["--issue-number", str(issue_number)]
    chunked_dynamic_access_args = build_chunked_dynamic_access_pr_args(
        exhaust_report_path,
        workflow_status,
    )
    chunked_dynamic_access_final = None
    coverage_follow_up_args: list[str] = []
    if coverage_follow_up_issue_number is not None:
        if coverage_follow_up_class_count is None or coverage_follow_up_class_threshold is None:
            raise ValueError("Coverage follow-up count and threshold are required")
        coverage_follow_up_args = [
            "--coverage-follow-up-issue-number",
            str(coverage_follow_up_issue_number),
            "--coverage-follow-up-class-count",
            str(coverage_follow_up_class_count),
            "--coverage-follow-up-class-threshold",
            str(coverage_follow_up_class_threshold),
        ]
    if exhaust_report_path is not None:
        chunked_dynamic_access_final = workflow_status != RUN_STATUS_CHUNK_READY

    script_name: str
    runner_name: str
    runner: Callable[[list[str]], None]
    argv: list[str]
    result_label: str
    publication_kind: str | None = None
    coordinates: str | None = claimed_issue.issue_coordinates
    current_coordinates: str | None = claimed_issue.current_coordinates
    new_version: str | None = claimed_issue.new_version
    not_for_native_image = False
    library_update_route = _load_library_update_publication_route(claimed_issue)

    if claimed_issue.label == LABEL_LIBRARY_NEW:
        group, artifact, _version = metadata_coordinate_parts(claimed_issue.issue_coordinates)
        not_for_native_image = is_not_for_native_image(claimed_issue.worktree_path, group, artifact)
        if not_for_native_image:
            script_name = "git_scripts/publish_not_for_native_image.py"
            runner_name = "run_publish_not_for_native_image"
            runner = run_publish_not_for_native_image
            result_label = LABEL_NOT_FOR_NATIVE_IMAGE
            argv = [
                "--coordinates", claimed_issue.issue_coordinates,
                *issue_number_args,
                "--reachability-metadata-path", claimed_issue.worktree_path,
                "--metrics-repo-path", claimed_issue.scratch_metrics_repo_path,
            ]
        else:
            script_name = "git_scripts/publish_new_library_support.py"
            runner_name = "run_publish_new_library_support"
            runner = run_publish_new_library_support
            result_label = LABEL_LIBRARY_NEW
            argv = [
                "--coordinates", claimed_issue.issue_coordinates,
                *issue_number_args,
                "--reachability-metadata-path", claimed_issue.worktree_path,
                "--metrics-repo-path", claimed_issue.scratch_metrics_repo_path,
                *chunked_dynamic_access_args,
            ]
    elif claimed_issue.label == LABEL_JAVAC_FAIL:
        script_name = "git_scripts/publish_javac_fix.py"
        runner_name = "run_publish_javac_fix"
        runner = run_publish_javac_fix
        result_label = LABEL_PR_JAVAC_FIX
        current_coordinates = _require_publication_value(
            claimed_issue.current_coordinates,
            "current_coordinates",
            claimed_issue,
        )
        new_version = _require_publication_value(claimed_issue.new_version, "new_version", claimed_issue)
        coordinates = None
        argv = [
            "--coordinates", current_coordinates,
            "--new-version", new_version,
            "--issue-number", str(issue_number),
            "--reachability-metadata-path", claimed_issue.worktree_path,
            "--metrics-repo-path", claimed_issue.scratch_metrics_repo_path,
            *coverage_follow_up_args,
        ]
    elif claimed_issue.label == LABEL_JAVA_RUN_FAIL:
        script_name = "git_scripts/publish_java_run_fix.py"
        runner_name = "run_publish_java_run_fix"
        runner = run_publish_java_run_fix
        result_label = LABEL_PR_JAVA_RUN_FIX
        current_coordinates = _require_publication_value(
            claimed_issue.current_coordinates,
            "current_coordinates",
            claimed_issue,
        )
        new_version = _require_publication_value(claimed_issue.new_version, "new_version", claimed_issue)
        coordinates = None
        argv = [
            "--coordinates", current_coordinates,
            "--new-version", new_version,
            "--issue-number", str(issue_number),
            "--reachability-metadata-path", claimed_issue.worktree_path,
            "--metrics-repo-path", claimed_issue.scratch_metrics_repo_path,
            *coverage_follow_up_args,
        ]
    elif claimed_issue.label == LABEL_NI_RUN_FAIL:
        script_name = "git_scripts/publish_ni_run_fix.py"
        runner_name = "run_publish_ni_run_fix"
        runner = run_publish_ni_run_fix
        result_label = LABEL_PR_NI_RUN_FIX
        current_coordinates = _require_publication_value(
            claimed_issue.current_coordinates,
            "current_coordinates",
            claimed_issue,
        )
        new_version = _require_publication_value(claimed_issue.new_version, "new_version", claimed_issue)
        coordinates = None
        argv = [
            "--coordinates", current_coordinates,
            "--new-version", new_version,
            "--issue-number", str(issue_number),
            "--reachability-metadata-path", claimed_issue.worktree_path,
            "--metrics-repo-path", claimed_issue.scratch_metrics_repo_path,
        ]
    elif claimed_issue.label == LABEL_LIBRARY_UPDATE:
        if library_update_route is not None and library_update_route.selected_driver == ROUTE_FIX_JAVAC:
            script_name = "git_scripts/publish_javac_fix.py"
            runner_name = "run_publish_javac_fix"
            runner = run_publish_javac_fix
            result_label = LABEL_PR_LIBRARY_UPDATE
            publication_kind = LABEL_PR_JAVAC_FIX
            current_coordinates = _require_publication_value(
                library_update_route.baseline_coordinates,
                "library_update_route.baseline_coordinates",
                claimed_issue,
            )
            new_version = library_update_route.new_version
            coordinates = None
            argv = [
                "--coordinates", current_coordinates,
                "--new-version", new_version,
                "--issue-number", str(issue_number),
                "--pr-label", result_label,
                "--reachability-metadata-path", claimed_issue.worktree_path,
                "--metrics-repo-path", claimed_issue.scratch_metrics_repo_path,
                *coverage_follow_up_args,
            ]
        elif library_update_route is not None and library_update_route.selected_driver == ROUTE_FIX_JAVA_RUN:
            script_name = "git_scripts/publish_java_run_fix.py"
            runner_name = "run_publish_java_run_fix"
            runner = run_publish_java_run_fix
            result_label = LABEL_PR_LIBRARY_UPDATE
            publication_kind = LABEL_PR_JAVA_RUN_FIX
            current_coordinates = _require_publication_value(
                library_update_route.baseline_coordinates,
                "library_update_route.baseline_coordinates",
                claimed_issue,
            )
            new_version = library_update_route.new_version
            coordinates = None
            argv = [
                "--coordinates", current_coordinates,
                "--new-version", new_version,
                "--issue-number", str(issue_number),
                "--pr-label", result_label,
                "--reachability-metadata-path", claimed_issue.worktree_path,
                "--metrics-repo-path", claimed_issue.scratch_metrics_repo_path,
                *coverage_follow_up_args,
            ]
        elif library_update_route is not None and library_update_route.selected_driver == ROUTE_FIX_NI_RUN:
            script_name = "git_scripts/publish_ni_run_fix.py"
            runner_name = "run_publish_ni_run_fix"
            runner = run_publish_ni_run_fix
            result_label = LABEL_PR_LIBRARY_UPDATE
            publication_kind = LABEL_PR_NI_RUN_FIX
            current_coordinates = _require_publication_value(
                library_update_route.baseline_coordinates,
                "library_update_route.baseline_coordinates",
                claimed_issue,
            )
            new_version = library_update_route.new_version
            coordinates = None
            argv = [
                "--coordinates", current_coordinates,
                "--new-version", new_version,
                "--issue-number", str(issue_number),
                "--pr-label", result_label,
                "--reachability-metadata-path", claimed_issue.worktree_path,
                "--metrics-repo-path", claimed_issue.scratch_metrics_repo_path,
            ]
        else:
            script_name = "git_scripts/publish_improve_coverage.py"
            runner_name = "run_publish_improve_coverage"
            runner = run_publish_improve_coverage
            result_label = LABEL_PR_LIBRARY_UPDATE
            argv = [
                "--coordinates", claimed_issue.issue_coordinates,
                *issue_number_args,
                "--reachability-metadata-path", claimed_issue.worktree_path,
                "--metrics-repo-path", claimed_issue.scratch_metrics_repo_path,
                *chunked_dynamic_access_args,
            ]
    else:
        raise ValueError(f"Unknown label '{claimed_issue.label}'")

    return PublicationHandoff(
        script_name=script_name,
        runner_name=runner_name,
        runner=runner,
        argv=argv,
        issue_number=issue_number,
        issue_label=claimed_issue.label,
        result_label=result_label,
        coordinates=coordinates,
        current_coordinates=current_coordinates,
        new_version=new_version,
        worktree_path=claimed_issue.worktree_path,
        scratch_metrics_path=claimed_issue.scratch_metrics_repo_path,
        workflow_status=workflow_status,
        chunked_dynamic_access_args=chunked_dynamic_access_args,
        dynamic_access_exhaust_report_path=exhaust_report_path,
        chunked_dynamic_access_final=chunked_dynamic_access_final,
        not_for_native_image=not_for_native_image,
        publication_kind=publication_kind or result_label,
        coverage_follow_up_issue_number=coverage_follow_up_issue_number,
        coverage_follow_up_class_count=coverage_follow_up_class_count,
        coverage_follow_up_class_threshold=coverage_follow_up_class_threshold,
    )


def preserve_fixture_preflight_evidence(claimed_issue: ClaimedIssue) -> None:
    """Copy fixture metrics/preflight files before scratch worktree cleanup."""
    issue_number = int(claimed_issue.issue["number"])
    evidence_dir = os.path.join(
        get_fixture_issue_artifact_dir(issue_number),
        FIXTURE_PREFLIGHT_EVIDENCE_DIRNAME,
    )
    os.makedirs(evidence_dir, exist_ok=True)
    evidence_files = [
        (claimed_issue.scratch_metrics_repo_path, PENDING_METRICS_FILENAME, "pending-metrics.json"),
        (
            claimed_issue.preflight_info_path or claimed_issue.scratch_metrics_repo_path,
            LIBRARY_PREPARATION_PREFLIGHT_FILENAME,
            LIBRARY_PREPARATION_PREFLIGHT_FILENAME,
        ),
        (
            claimed_issue.preflight_info_path or claimed_issue.scratch_metrics_repo_path,
            "library-preflight-prompt.txt",
            "library-preflight-prompt.txt",
        ),
        (
            claimed_issue.preflight_info_path or claimed_issue.scratch_metrics_repo_path,
            "library-preflight-response.txt",
            "library-preflight-response.txt",
        ),
    ]
    copied_files: list[str] = []
    for source_root, source_name, target_name in evidence_files:
        source_path = os.path.join(source_root, source_name)
        if not os.path.isfile(source_path):
            continue
        target_path = os.path.join(evidence_dir, target_name)
        shutil.copy2(source_path, target_path)
        copied_files.append(target_name)
    if copied_files:
        log_stage(
            "fixture-evidence",
            (
                f"Preserved fixture preflight evidence for issue #{issue_number}: "
                f"{', '.join(copied_files)}"
            ),
        )


def prepare_java_fix_coverage_follow_up(
        claimed_issue: ClaimedIssue,
) -> tuple[int, int, int] | None:
    """Open a fixed-version coverage issue when post-repair exploration was oversized.

    The composite strategy already made this decision against the report it had
    when the repair finished, so publication reads that recorded decision instead
    of regenerating a report and deciding a second time.
    §AR-forge-driver-queues.3
    """
    is_java_fix_issue = claimed_issue.label in {LABEL_JAVAC_FAIL, LABEL_JAVA_RUN_FAIL}
    library_update_route = _load_library_update_publication_route(claimed_issue)
    is_library_update_java_fix = (
        claimed_issue.label == LABEL_LIBRARY_UPDATE
        and library_update_route is not None
        and library_update_route.selected_driver in {ROUTE_FIX_JAVAC, ROUTE_FIX_JAVA_RUN}
    )
    if not is_java_fix_issue and not is_library_update_java_fix:
        return None

    marker = load_continuation_marker(continuation_marker_path(claimed_issue.worktree_path))
    deferred_coverage = marker.deferred_dynamic_access_coverage() if marker is not None else None
    if deferred_coverage is None:
        return None
    uncovered_class_count, threshold = deferred_coverage
    existing_issue_number = marker.coverage_follow_up_issue_number()
    marker_path = continuation_marker_path(claimed_issue.worktree_path)

    if is_fixture_testing_enabled():
        issue_number = existing_issue_number
        if issue_number is None:
            issue_number = FIXTURE_COVERAGE_FOLLOW_UP_ISSUE_OFFSET + int(
                claimed_issue.issue["number"]
            )
            save_phase_update(
                marker_path,
                lambda current_marker: current_marker.record_coverage_follow_up_issue(
                    issue_number
                ),
            )
        log_stage(
            "coverage-follow-up",
            f"Fixture mode: using simulated library-update issue #{issue_number}.",
        )
        return issue_number, uncovered_class_count, threshold

    issue_number = ensure_coverage_follow_up_issue(
        coordinate=claimed_issue.issue_coordinates,
        repair_issue_number=int(claimed_issue.issue["number"]),
        uncovered_class_count=uncovered_class_count,
        class_threshold=threshold,
        repo=REPO,
        existing_issue_number=existing_issue_number,
        record_issue_number=lambda resolved_issue_number: save_phase_update(
            marker_path,
            lambda current_marker: current_marker.record_coverage_follow_up_issue(
                resolved_issue_number
            ),
        ),
    )
    return issue_number, uncovered_class_count, threshold
