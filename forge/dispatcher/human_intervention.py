# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Failure analysis for human intervention: candidate resolution, log
collection, and analysis agents (§AR-forge-dispatcher-decomposition,
§FS-human-intervention-policy)."""


import json
import os
import re
import sys
from ai_workflows.agents.agent_runtime import analysis_agent_run
from ai_workflows.agents.agent_runtime import get_analysis_agent
from ai_workflows.core.workflow_strategy import RUN_STATUS_CHUNK_READY
from ai_workflows.core.workflow_strategy import RUN_STATUS_FAILURE
from ai_workflows.drivers.add_new_library_setup import DEFAULT_MODEL_NAME
from ai_workflows.drivers.add_new_library_setup import init_agent as init_workflow_agent
from dataclasses import dataclass
from git_scripts.common_git import GitTransportError
from git_scripts.github_cli import GitHubError
from utility_scripts.dynamic_access_report import load_dynamic_access_coverage_report
from utility_scripts.library_stats import resolve_stats_file_path
from utility_scripts.metadata_index import resolve_metadata_version
from utility_scripts.metadata_index import resolve_test_dir
from utility_scripts.metrics_writer import read_pending_metrics
from utility_scripts.source_context_discovery import GradleBootstrapFailure
from utility_scripts.stage_logger import log_stage
from utility_scripts.strategy_loader import load_strategy_by_name
from utility_scripts.task_logs import build_task_log_path
from utility_scripts.task_logs import display_log_path
from utility_scripts.task_logs import resolve_logs_root
from utility_scripts.task_logs import sanitize_library_log_segment
from utility_scripts.workflow_setup import list_all_files
from dispatcher.config import (
    FAILURE_ANALYSIS_TIMEOUT_SECONDS,
    HUMAN_INTERVENTION_NON_FAILURE_STATUSES,
    LABEL_LIBRARY_NEW,
    LABEL_LIBRARY_UPDATE,
    LOW_DYNAMIC_ACCESS_COVERAGE_RATIO,
)
from dispatcher.failure_preservation import FailurePreservationResult
from dispatcher.records import (
    ClaimedIssue,
    DynamicAccessCoverageSnapshot,
)
from dispatcher.run_logs import read_log_tail
from dispatcher.worktrees import claimed_issue_worktree_is_valid

@dataclass(frozen=True)
class HumanInterventionCandidate:
    strategy_name: str | None
    workflow_status: str
    coverage: DynamicAccessCoverageSnapshot | None = None
    reason: str = "low_dynamic_access_coverage"

def _load_pending_run_metrics(metrics_worktree_path: str) -> dict | None:
    """Load the pending run metrics from the metrics worktree."""
    try:
        return read_pending_metrics(metrics_worktree_path)
    except (json.JSONDecodeError, TypeError, FileNotFoundError):
        return None


def _load_dynamic_access_snapshot_from_metrics(run_metrics: dict | None) -> DynamicAccessCoverageSnapshot | None:
    """Load dynamic-access coverage from the stored run metrics stats block."""
    if not isinstance(run_metrics, dict):
        return None

    stats = run_metrics.get("stats")
    if not isinstance(stats, dict):
        return None

    dynamic_access = stats.get("dynamicAccess")
    if not isinstance(dynamic_access, dict):
        return None

    total_calls = int(dynamic_access.get("totalCalls", 0) or 0)
    if total_calls <= 0:
        return None

    covered_calls = int(dynamic_access.get("coveredCalls", 0) or 0)
    coverage_ratio = dynamic_access.get("coverageRatio")
    if coverage_ratio is None:
        coverage_ratio = covered_calls / total_calls

    return DynamicAccessCoverageSnapshot(
        covered_calls=covered_calls,
        total_calls=total_calls,
        coverage_ratio=float(coverage_ratio),
        source="stats",
    )


def _resolve_dynamic_access_report_path(claimed_issue: ClaimedIssue) -> str:
    """Resolve the dynamic-access coverage report path for a new-library issue."""
    group, artifact, version = claimed_issue.issue_coordinates.split(":")
    return os.path.join(
        resolve_test_dir(claimed_issue.worktree_path, group, artifact, version),
        "build",
        "reports",
        "dynamic-access",
        "dynamic-access-coverage.json",
    )


def _load_dynamic_access_snapshot_from_report(claimed_issue: ClaimedIssue) -> DynamicAccessCoverageSnapshot | None:
    """Load dynamic-access coverage directly from the generated report as a fallback."""
    report_path = _resolve_dynamic_access_report_path(claimed_issue)
    try:
        report = load_dynamic_access_coverage_report(report_path)
    except FileNotFoundError:
        return None

    if not report.has_dynamic_access or report.total_calls <= 0:
        return None

    return DynamicAccessCoverageSnapshot(
        covered_calls=report.covered_calls,
        total_calls=report.total_calls,
        coverage_ratio=(report.covered_calls / report.total_calls),
        source="report",
    )


def is_external_failure_exception(exc: BaseException | None) -> bool:
    """Return True when a workflow exception is an external dependency failure.

    GitHub (rate-limit or transient), git transport, and shared Gradle bootstrap
    outages surface as typed exceptions (`GitHubError` / `GitTransportError` /
    `GradleBootstrapFailure`). An external failure releases the issue claim for
    retry without `human-intervention`, while any other exception (including agent
    timeouts) is logical. The cause/context chain is walked so a wrapped external
    error is still recognized.
    §FS-human-intervention-policy
    """
    error: BaseException | None = exc
    while error is not None:
        if isinstance(error, (GitHubError, GitTransportError, GradleBootstrapFailure)):
            return True
        error = error.__cause__ or error.__context__
    return False


def resolve_human_intervention_candidate(
        claimed_issue: ClaimedIssue,
        workflow_success: bool = True,
) -> HumanInterventionCandidate | None:
    """Return follow-up data for a logical issue failure that needs human intervention.

    External failures are filtered out upstream (see `handle_failed_claimed_issue`),
    so any library-issue failure that reaches here is logical (driver/core/CI check,
    including agent timeouts) and is labeled. §FS-human-intervention-policy
    """
    if claimed_issue.label not in {LABEL_LIBRARY_NEW, LABEL_LIBRARY_UPDATE}:
        return None

    run_metrics = _load_pending_run_metrics(claimed_issue.scratch_metrics_repo_path)
    if not workflow_success:
        strategy_name = None
        workflow_status = RUN_STATUS_FAILURE
        coverage_snapshot = None
        if isinstance(run_metrics, dict):
            strategy_name = run_metrics.get("strategy_name")
            workflow_status = str(run_metrics.get("status") or RUN_STATUS_FAILURE)
            coverage_snapshot = _load_dynamic_access_snapshot_from_metrics(run_metrics)
        if workflow_status in HUMAN_INTERVENTION_NON_FAILURE_STATUSES:
            return None
        if coverage_snapshot is None:
            coverage_snapshot = _load_dynamic_access_snapshot_from_report(claimed_issue)
        return HumanInterventionCandidate(
            strategy_name=strategy_name,
            workflow_status=workflow_status,
            coverage=coverage_snapshot,
            reason="test_generation_failed",
        )

    if run_metrics is None:
        return None

    strategy_name = run_metrics.get("strategy_name")
    if not strategy_name:
        return None
    if run_metrics.get("status") == RUN_STATUS_CHUNK_READY:
        return None

    strategy = load_strategy_by_name(strategy_name)
    if strategy is None or strategy.get("workflow") != "dynamic_access_iterative":
        return None

    coverage_snapshot = _load_dynamic_access_snapshot_from_metrics(run_metrics)
    if coverage_snapshot is None:
        coverage_snapshot = _load_dynamic_access_snapshot_from_report(claimed_issue)
    if coverage_snapshot is None:
        return None

    if (
            coverage_snapshot.covered_calls > 0
            and coverage_snapshot.coverage_ratio > LOW_DYNAMIC_ACCESS_COVERAGE_RATIO
    ):
        return None

    return HumanInterventionCandidate(
        strategy_name=strategy_name,
        workflow_status=str(run_metrics.get("status") or "unknown"),
        coverage=coverage_snapshot,
        reason="low_dynamic_access_coverage",
    )


def _collect_human_intervention_read_only_files(claimed_issue: ClaimedIssue) -> list[str]:
    """Collect the key generated files that help the analysis agent explain the gap."""
    group, artifact, version = claimed_issue.issue_coordinates.split(":")
    metadata_version = resolve_metadata_version(claimed_issue.worktree_path, group, artifact, version)
    candidate_paths = [
        resolve_test_dir(claimed_issue.worktree_path, group, artifact, version),
        os.path.join(claimed_issue.worktree_path, "metadata", group, artifact, "index.json"),
        os.path.join(claimed_issue.worktree_path, "metadata", group, artifact, metadata_version),
        resolve_stats_file_path(claimed_issue.worktree_path, group, artifact, version),
        _resolve_dynamic_access_report_path(claimed_issue),
    ]

    read_only_files: list[str] = []
    for candidate_path in candidate_paths:
        if os.path.isdir(candidate_path):
            read_only_files.extend(list_all_files(candidate_path))
        elif os.path.isfile(candidate_path):
            read_only_files.append(candidate_path)

    # Preserve order while removing duplicates.
    return list(dict.fromkeys(read_only_files))


def _build_human_intervention_analysis_prompt(
        claimed_issue: ClaimedIssue,
        candidate: HumanInterventionCandidate,
) -> str:
    """Create the analysis prompt for the follow-up agent run."""
    if candidate.coverage is None:
        return _build_failed_generation_analysis_prompt(claimed_issue, candidate, [], None)

    coverage = candidate.coverage
    coverage_percent = coverage.coverage_ratio * 100.0
    report_path = _resolve_dynamic_access_report_path(claimed_issue)
    report_path_display = _repo_relative_path(report_path, claimed_issue.worktree_path)
    return (
        "Read-only analysis task. Do not modify files, create commits, or propose automated edits.\n\n"
        "Project issue details:\n"
        f"- Issue number: {claimed_issue.issue['number']}\n"
        f"- Library: {claimed_issue.issue_coordinates}\n"
        f"- Workflow strategy: {candidate.strategy_name}\n"
        f"- Workflow status: {candidate.workflow_status}\n"
        f"- Dynamic access coverage: {coverage.covered_calls}/{coverage.total_calls} "
        f"({coverage_percent:.2f}%) from {coverage.source}\n"
        f"- Dynamic access report path: {report_path_display}\n\n"
        "Task:\n"
        "Analyze the generated tests, metadata, stats, and dynamic-access report in this repository. "
        "Write the exact GitHub issue comment that should be posted because this library likely needs human follow-up.\n\n"
        "Comment requirements:\n"
        "- Start with a short heading: `Human intervention needed`.\n"
        "- Explain briefly why dynamic-access coverage is missing or still very low.\n"
        "- Ground the explanation in concrete project observations.\n"
        "- End with 2 or 3 specific manual next steps.\n"
        "- Keep the comment concise and do not use code fences.\n"
        "- Do not mention being an AI."
    )


def _build_human_intervention_fallback_comment(
        claimed_issue: ClaimedIssue,
        candidate: HumanInterventionCandidate,
) -> str:
    """Build a deterministic fallback comment when the analysis agent fails."""
    if candidate.reason == "test_generation_failed" or candidate.coverage is None:
        return _build_failed_generation_fallback_comment(claimed_issue, candidate, [])

    coverage_percent = candidate.coverage.coverage_ratio * 100.0
    return (
        "Human intervention needed\n\n"
        f"Automation completed for `{claimed_issue.issue_coordinates}`, but dynamic-access coverage remains "
        f"`{candidate.coverage.covered_calls}/{candidate.coverage.total_calls}` ({coverage_percent:.2f}%). "
        "This suggests the generated tests are not exercising the required dynamic-access paths well enough.\n\n"
        "Recommended manual follow-up:\n"
        "- Inspect the generated tests and compare them with the dynamic-access report to find uncovered call sites.\n"
        "- Verify whether the library requires environment setup, fixtures, or execution paths that automation did not trigger.\n"
        "- Add or adjust focused tests for the uncovered paths before relying on the generated metadata."
    )


def _sanitize_log_name(value: str) -> str:
    """Return a filesystem-safe log name segment."""
    return re.sub(r"[^A-Za-z0-9_.-]+", "_", value).strip("_") or "unknown"


def _repo_relative_path(path: str, repo_path: str) -> str:
    """Return a path relative to the given repository root."""
    return os.path.relpath(os.path.abspath(path), os.path.abspath(repo_path))


def get_codex_failure_analysis_log_path(issue_number: int, coordinates: str) -> str:
    """Return the Codex failure-analysis log path for the target issue."""
    return build_task_log_path(
        "failure-analysis",
        coordinates,
        f"codex_failure_analysis_issue_{issue_number}.log",
    )


def _log_file_mentions_issue(log_path: str, claimed_issue: ClaimedIssue) -> bool:
    """Return True when a log file appears to belong to the claimed issue."""
    coordinate = claimed_issue.issue_coordinates
    group, artifact, version = coordinate.split(":")
    try:
        with open(log_path, "r", encoding="utf-8", errors="replace") as log_file:
            content = log_file.read(2_000_000)
    except OSError:
        return False

    if coordinate in content:
        return True
    return artifact in content and version in content and group in content


def collect_issue_log_paths(claimed_issue: ClaimedIssue, started_at: float | None) -> list[str]:
    """Collect run logs that are useful for a failed library-generation analysis."""
    log_dir = resolve_logs_root()
    if not os.path.isdir(log_dir):
        return []

    coordinate = claimed_issue.issue_coordinates
    group, artifact, version = coordinate.split(":")
    filename_needles = {
        sanitize_library_log_segment(coordinate),
        _sanitize_log_name(coordinate),
        _sanitize_log_name(coordinate.replace(":", "_")),
        _sanitize_log_name(f"{group}_{artifact}_{version}"),
    }
    candidates: list[tuple[float, str]] = []
    for root, _, file_names in os.walk(log_dir):
        for file_name in file_names:
            log_path = os.path.join(root, file_name)
            if not os.path.isfile(log_path):
                continue
            try:
                modified_at = os.path.getmtime(log_path)
            except OSError:
                continue

            relative_path = os.path.relpath(log_path, log_dir)
            path_matches = any(needle and needle in relative_path for needle in filename_needles)
            recent_enough = started_at is not None and modified_at >= started_at - 5
            if path_matches or (recent_enough and _log_file_mentions_issue(log_path, claimed_issue)):
                candidates.append((modified_at, log_path))
    candidates.sort(reverse=True)
    return [path for _, path in candidates[:8]]


def _format_failure_metrics_summary(run_metrics: dict | None) -> str:
    """Format relevant failure metrics for a Codex issue-analysis prompt."""
    if not isinstance(run_metrics, dict):
        return "- Metrics: unavailable"

    metrics = run_metrics.get("metrics")
    if not isinstance(metrics, dict):
        metrics = {}

    lines = [
        f"- Metrics status: {run_metrics.get('status', 'unknown')}",
        f"- Strategy: {run_metrics.get('strategy_name', 'unknown')}",
        f"- Agent: {run_metrics.get('agent', 'unknown')}",
        f"- Model: {run_metrics.get('model', 'unknown')}",
        f"- Iterations: {metrics.get('iterations', 'unknown')}",
        f"- Generated LOC: {metrics.get('generated_loc', 'unknown')}",
        f"- Starting commit: {run_metrics.get('starting_commit', 'unknown')}",
        f"- Ending commit: {run_metrics.get('ending_commit', 'unknown')}",
    ]
    return "\n".join(lines)


def _format_log_path_list(log_paths: list[str]) -> str:
    """Format log paths for prompts and fallback comments."""
    if not log_paths:
        return "- No matching run logs were found."
    return "\n".join(f"- {display_log_path(log_path)}" for log_path in log_paths)


def _build_failed_generation_analysis_prompt(
        claimed_issue: ClaimedIssue,
        candidate: HumanInterventionCandidate,
        log_paths: list[str],
        run_metrics: dict | None,
        preservation_result: FailurePreservationResult | None = None,
) -> str:
    """Create a Codex prompt that asks for the exact failure-analysis issue comment."""
    coverage_details = "- Dynamic-access coverage: unavailable"
    if candidate.coverage is not None:
        coverage_percent = candidate.coverage.coverage_ratio * 100.0
        coverage_details = (
            f"- Dynamic-access coverage: {candidate.coverage.covered_calls}/"
            f"{candidate.coverage.total_calls} ({coverage_percent:.2f}%) from {candidate.coverage.source}"
        )

    preserved_work_details = "- Preserved work branch: unavailable"
    if preservation_result is not None:
        preserved_work_details = (
            f"- Preserved work branch: {preservation_result.branch_url}\n"
            f"- Branch name: `{preservation_result.branch_name}`\n"
            f"- Preservation commit created: {preservation_result.committed_changes}"
        )

    return (
        "Read-only analysis task. Do not modify files, create commits, run fix commands, or open a PR.\n\n"
        "Project issue details:\n"
        f"- Issue number: {claimed_issue.issue['number']}\n"
        f"- Library: {claimed_issue.issue_coordinates}\n"
        f"- Pipeline label: {claimed_issue.label}\n"
        f"- Workflow status: {candidate.workflow_status}\n"
        f"- Human-intervention reason: {candidate.reason}\n"
        f"{coverage_details}\n\n"
        "Preserved work:\n"
        f"{preserved_work_details}\n\n"
        "Run metrics summary:\n"
        f"{_format_failure_metrics_summary(run_metrics)}\n\n"
        "Relevant log paths:\n"
        f"{_format_log_path_list(log_paths)}\n\n"
        "Generated project paths to inspect if present:\n"
        "- Current reachability-metadata worktree\n"
        "- Scratch metrics repository for this run\n\n"
        "Task:\n"
        "Inspect the available logs, generated tests, Gradle output, and metrics. "
        "Write the exact GitHub issue comment that should be posted for human follow-up.\n\n"
        "Comment requirements:\n"
        "- Start with the heading `Human intervention needed`.\n"
        "- State that the automated job failed.\n"
        "- Include the preserved work branch URL when it is available.\n"
        "- Explain the most likely failing stage and the concrete evidence from logs or metrics.\n"
        "- Mention specific log paths that a maintainer should inspect.\n"
        "- End with 2 or 3 specific manual next steps.\n"
        "- Keep the comment concise but detailed enough to act on.\n"
        "- Do not use code fences.\n"
        "- Do not mention being an AI."
    )


def _build_failed_generation_fallback_comment(
        claimed_issue: ClaimedIssue,
        candidate: HumanInterventionCandidate,
        log_paths: list[str],
        preservation_result: FailurePreservationResult | None = None,
) -> str:
    """Build a deterministic failed-generation comment when Codex analysis fails."""
    logs_section = _format_log_path_list(log_paths)
    preserved_work_section = "Preserved work branch: unavailable"
    if preservation_result is not None:
        preserved_work_section = (
            f"Preserved work branch: {preservation_result.branch_url}\n"
            f"Branch name: `{preservation_result.branch_name}`"
        )

    return (
        "Human intervention needed\n\n"
        f"The automated `{claimed_issue.label}` job failed for `{claimed_issue.issue_coordinates}` with "
        f"workflow status `{candidate.workflow_status}`. The automation could not produce a PR-ready update, "
        "so this issue needs manual follow-up. Any work left in the failed run has been preserved.\n\n"
        f"{preserved_work_section}\n\n"
        "Relevant logs:\n"
        f"{logs_section}\n\n"
        "Recommended manual follow-up:\n"
        "- Inspect the run logs to identify the first Gradle or agent failure.\n"
        "- Review the preserved branch and decide which generated changes can be salvaged.\n"
        "- Re-run the workflow or continue manually after addressing the root cause."
    )

def run_codex_failed_generation_analysis(
        claimed_issue: ClaimedIssue,
        candidate: HumanInterventionCandidate,
        started_at: float | None,
        preservation_result: FailurePreservationResult | None = None,
) -> str:
    """Use the analysis agent to analyze a failed generation run."""
    run_metrics = _load_pending_run_metrics(claimed_issue.scratch_metrics_repo_path)
    log_paths = collect_issue_log_paths(claimed_issue, started_at)
    if not claimed_issue_worktree_is_valid(claimed_issue, "failed-run analysis"):
        return _build_failed_generation_fallback_comment(
            claimed_issue,
            candidate,
            log_paths,
            preservation_result,
        )
    prompt = _build_failed_generation_analysis_prompt(
        claimed_issue,
        candidate,
        log_paths,
        run_metrics,
        preservation_result,
    )
    selection = get_analysis_agent()

    log_stage(
        "failure-analysis",
        f"Running {selection.backend} failure analysis for issue #{claimed_issue.issue['number']}",
    )
    result = analysis_agent_run(
        working_dir=claimed_issue.worktree_path,
        context=prompt,
        task_type="failure-analysis",
        library=claimed_issue.issue_coordinates,
        timeout=FAILURE_ANALYSIS_TIMEOUT_SECONDS,
    )
    if result.return_code == 0 and result.response.strip():
        return result.response.strip()

    output_tail = read_log_tail(result.log_path)
    print(
        (
            f"ERROR: {selection.backend} failure analysis failed for issue "
            f"#{claimed_issue.issue['number']}. Log: {display_log_path(result.log_path)}."
            + (f"\n{output_tail}" if output_tail else "")
        ),
        file=sys.stderr,
    )
    return _build_failed_generation_fallback_comment(
        claimed_issue,
        candidate,
        log_paths,
        preservation_result,
    )


def run_human_intervention_analysis(
        claimed_issue: ClaimedIssue,
        candidate: HumanInterventionCandidate,
        started_at: float | None = None,
        preservation_result: FailurePreservationResult | None = None,
) -> str:
    """Run a separate agent pass that writes the issue comment for human follow-up."""
    if candidate.reason in {"test_generation_failed", "job_failed"}:
        return run_codex_failed_generation_analysis(
            claimed_issue,
            candidate,
            started_at,
            preservation_result,
        )

    strategy = load_strategy_by_name(candidate.strategy_name)
    if strategy is None:
        return _build_human_intervention_fallback_comment(claimed_issue, candidate)

    model_name = strategy.get("model") or DEFAULT_MODEL_NAME
    read_only_files = _collect_human_intervention_read_only_files(claimed_issue)
    prompt = _build_human_intervention_analysis_prompt(claimed_issue, candidate)
    if not claimed_issue_worktree_is_valid(claimed_issue, "human-intervention analysis"):
        return _build_human_intervention_fallback_comment(claimed_issue, candidate)

    try:
        agent = init_workflow_agent(
            strategy=strategy,
            working_dir=claimed_issue.worktree_path,
            editable_files=[],
            read_only_files=read_only_files,
            verbose=False,
            model_name=model_name,
        )
        response = (agent.send_prompt(prompt) or "").strip()
        if response:
            return response
    except Exception as exc:
        print(
            f"ERROR: Human-intervention analysis failed for issue #{claimed_issue.issue['number']}: {exc!r}",
            file=sys.stderr,
        )

    return _build_human_intervention_fallback_comment(claimed_issue, candidate)
