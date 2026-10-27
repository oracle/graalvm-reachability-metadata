# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The `publish` program: commit the contribution, describe the run, and push.

Publication checks nothing itself: `finalize` ran every check before it, on the
run rebased onto fresh `master`, so the pushed tree is the verified one
(§FS-local-ci-equivalent-verification.2). Only the contribution's file set is
committed, and a path outside it stops the run (§AR-expected-paths); the run
metrics and the descriptor are written (§FS-forge-run-metrics, §AR-publication-descriptor),
and the pushed `ai/**` branch is the whole handoff to the trusted publisher
(§AR-forge-verification-publication-boundary). A deferred exploration opens its
follow-up issue first, and a chunk records its identity for the next run
(§FS-forge-chunked-dynamic-access). A skip record ships only its index and
descriptor, with no execution metrics for the skipped version
(§FS-unsupportable-version-diagnosis.3).
"""
from __future__ import annotations

import os
import subprocess
import sys
from typing import Any

from git_scripts.github_cli import get_authenticated_login
from git_scripts.publication_descriptor import (
    PublicationDescriptorInput,
    build_publication_branch,
    build_publication_id,
    write_publication_descriptor,
)
from git_scripts.publication_evidence import load_dynamic_access_metadata_evidence
from utility_scripts.contribution_paths import stray_paths
from utility_scripts.dynamic_access_exhaust_report import DynamicAccessExhaustReport, dynamic_access_exhaust_report_path
from utility_scripts.java_fix_coverage_follow_up import ensure_coverage_follow_up_issue
from utility_scripts.library_measurements import (
    collect_version_coverage_metrics,
    count_metadata_entries,
    count_test_only_metadata_entries,
    load_library_stats_snapshot,
    resolve_metadata_artifact_path,
)
from utility_scripts.library_stats import load_library_stats_entry
from utility_scripts.local_ci_verification import CommandRecord, FixupRecord, LocalCIVerificationResult
from utility_scripts.metrics_writer import append_execution_metrics
from utility_scripts.run_metrics_payloads import build_run_metrics_dict
from utility_scripts.token_costs import calc_model_session_cost

import explore
import issue
import run_tokens
from build_tools import changed_paths, checkpoint, git, head, upstream_remote
from exit_codes import INFRASTRUCTURE, NOTHING_TO_DO, OK
from run_state import ROUTE_NEW, RunState, load, runtime_path, save, write_text

STATUS_SUCCESS: str = "success"
STATUS_CHUNK_READY: str = "chunk_ready"
MAX_SUBJECT: int = 60


def _subject(text: str) -> str:
    return text if len(text) <= MAX_SUBJECT else text[: MAX_SUBJECT - 3] + "..."


def stray(state: RunState) -> list[str]:
    """The changed paths outside the contribution's file set; `finalize` and `audit` leave none."""
    return stray_paths(state.worktree, state.coordinate, state.base_commit,
                       changed_paths(state.worktree, state.base_commit))


def commit_contribution(state: RunState, message: str) -> str:
    return checkpoint(state.worktree, state.contribution_paths(), _subject(message))


def has_changes(state: RunState) -> bool:
    return bool(git(state.worktree, "diff", "--name-only", state.base_commit, "HEAD", "--", *state.coordinate_paths).strip())


def worker_model(state: RunState) -> tuple[str, str]:
    """The worker agent and the model it ran, as the run metrics record them."""
    agent, _, model = state.worker_agent.partition(":")
    return agent.split("[")[0], model


def chunk_is_final(workspace: str, state: RunState) -> bool:
    if not state.chunk.active:
        return True
    if not state.explore.listed:
        # Without a listing the chunk's progress is unknown, so its issue stays open.
        return False
    return state.chunk.final and not explore.remaining(state.explore, explore.accepted_report(workspace))


def record_chunk(state: RunState, final: bool, publication_id: str, branch: str) -> dict[str, Any] | None:
    """Update the coordinate's exhaust report: drop it for a final chunk, record this chunk otherwise."""
    if not state.chunk.active:
        return None
    path: str = dynamic_access_exhaust_report_path(state.worktree, state.coordinate)
    report: DynamicAccessExhaustReport = (
        DynamicAccessExhaustReport.load(path) if os.path.isfile(path)
        else DynamicAccessExhaustReport.create(coordinate=state.coordinate, issue_number=state.issue_number)
    )
    for name in state.explore.completed:
        report.mark_completed(name)
    for name in state.explore.exhausted:
        report.mark_exhausted(name)
    report.update_chunk_limits(state.budgets.defer_classes, state.chunk.budget or state.budgets.defer_classes)
    summary: dict[str, Any] = report.to_dict()
    if final:
        if os.path.isfile(path):
            os.remove(path)
    else:
        report.record_publication_identity(publication_id, branch)
        report.save(path)
    commit_contribution(state, f"Record the chunk of {state.coordinate}")
    return summary


def follow_ups(state: RunState) -> list[dict[str, Any]]:
    """The parked library-update issue of a deferred exploration.

    §FS-forge-chunked-dynamic-access
    """
    if state.explore.deferred_classes is None:
        return []
    number: int = ensure_coverage_follow_up_issue(
        coordinate=state.coordinate,
        repair_issue_number=state.issue_number,
        uncovered_class_count=state.explore.deferred_classes,
        class_threshold=state.budgets.defer_classes,
        repo=issue.REPOSITORY,
    )
    return [{
        "type": "deferred_dynamic_access_coverage",
        "coordinate": state.coordinate,
        "uncovered_class_count": state.explore.deferred_classes,
        "class_threshold": state.budgets.defer_classes,
        "issue_number": number,
    }]


def run_metrics(workspace: str, state: RunState, status: str) -> dict[str, Any]:
    """The schema-validated per-run record (§FS-forge-run-metrics)."""
    usage: run_tokens.Usage = run_tokens.total_usage(workspace)
    agent, model = worker_model(state)
    measured: bool = not state.skip_recorded
    coverage_percent, lines_covered = collect_version_coverage_metrics(
        state.worktree, state.group, state.artifact, state.version) if measured else (0.0, 0)
    metrics: dict[str, Any] = build_run_metrics_dict(
        package=state.group,
        artifact=state.artifact,
        library_version=state.version,
        strategy_name=f"forge-metadata:{state.label}",
        status=status,
        global_iterations=state.explore.passes,
        input_tokens_used=usage.input,
        output_tokens_used=usage.output,
        cached_input_tokens_used=usage.cached,
        cost_usd=calc_model_session_cost(model.rsplit("/", 1)[-1], usage.input, usage.cached, usage.output),
        lines_covered=lines_covered,
        coverage_percent=coverage_percent,
        total_entries=count_metadata_entries(state.worktree, state.group, state.artifact, state.version) if measured else 0,
        test_only_metadata_entries=count_test_only_metadata_entries(
            state.worktree, state.group, state.artifact, state.version) if measured else 0,
        metadata_file=resolve_metadata_artifact_path(
            state.worktree, state.group, state.artifact, state.version) if measured else state.index_path,
        starting_commit=state.base_commit,
        ending_commit=head(state.worktree),
        previous_library=state.previous_coordinate,
        agent_name=agent,
        model_name=model,
        stats=load_library_stats_snapshot(state.worktree, state.group, state.artifact, state.version) if measured else None,
    )
    metrics["timestamp"] = state.started_at
    return metrics


def render(workspace: str, state: RunState, chunk_summary: dict[str, Any] | None) -> dict[str, Any]:
    """What the trusted publisher renders into the pull request (§AR-pr-body)."""
    measured: bool = not state.skip_recorded
    evidence = load_dynamic_access_metadata_evidence(state.worktree, state.coordinate) if measured else None
    values: dict[str, Any] = {
        "library_stats": load_library_stats_entry(
            state.worktree, state.group, state.artifact, state.version) if measured else None,
        "current_metadata_entries": count_metadata_entries(
            state.worktree, state.group, state.artifact, state.version) if measured else None,
        "current_test_only_entries": count_test_only_metadata_entries(
            state.worktree, state.group, state.artifact, state.version) if measured else None,
        "dynamic_access": chunk_summary,
        "dynamic_access_evidence": None if evidence is None else {
            "covered_call_sites": evidence.covered_call_sites,
            "metadata_rules": evidence.metadata_rules,
        },
        "token_usage": [
            {"phase": state_name, "input": usage.input, "cached": usage.cached, "output": usage.output}
            for state_name, usage in run_tokens.usage_by_state(workspace).items()
        ][:32],
        "worker_model": worker_model(state)[1],
    }
    if state.previous_coordinate is not None:
        group, artifact, version = state.previous_coordinate.split(":")
        values["baseline_stats"] = load_library_stats_entry(state.worktree, group, artifact, version)
        values["baseline_metadata_entries"] = count_metadata_entries(state.worktree, group, artifact, version)
        values["baseline_test_only_entries"] = count_test_only_metadata_entries(state.worktree, group, artifact, version)
    return values


def verification(state: RunState) -> LocalCIVerificationResult:
    """The pre-publication gate's record from `finalize`."""
    raw: dict[str, Any] = dict(state.local_ci or {})
    if not raw:
        raise ValueError("finalize recorded no pre-publication gate result")
    raw["commands"] = [CommandRecord(**command) for command in raw.get("commands", [])]
    raw["fixups"] = [FixupRecord(**fixup) for fixup in raw.get("fixups", [])]
    return LocalCIVerificationResult(**raw)


def label_chunked(state: RunState) -> None:
    """A published non-final chunk keeps the issue in chunked mode for its next claim."""
    subprocess.run(["gh", "issue", "edit", str(state.issue_number), "--repo", issue.REPOSITORY,
                    "--add-label", issue.CHUNKED_LABEL], check=True, capture_output=True, text=True)


def publish(workspace: str) -> int:
    state: RunState = load(workspace)
    outside: list[str] = stray(state)
    if outside:
        print(f"ERROR: paths outside the contribution's file set: {', '.join(outside)}", file=sys.stderr)
        return INFRASTRUCTURE
    state.checkpoint = commit_contribution(state, f"Finish {state.coordinate}")
    if not has_changes(state):
        print(f"[publish] {state.coordinate} is unchanged; nothing to publish")
        return NOTHING_TO_DO
    task_type: str = issue.TASK_TYPES[state.label]
    final: bool = state.skip_recorded or chunk_is_final(workspace, state)
    status: str = STATUS_SUCCESS if final else STATUS_CHUNK_READY
    producer: str = get_authenticated_login(cwd=state.worktree)
    publication_id: str = build_publication_id(state.issue_number, state.started_at, state.coordinate, task_type)
    suffix: str = f"{'add-lib-support' if state.route == ROUTE_NEW else task_type}-{state.group}-{state.artifact}-{state.version}"
    branch: str = build_publication_branch(producer, suffix, publication_id)
    chunk_summary: dict[str, Any] | None = None if state.skip_recorded else record_chunk(
        state, final, publication_id, branch)
    descriptor_follow_ups: list[dict[str, Any]] = follow_ups(state)
    metrics: dict[str, Any] = run_metrics(workspace, state, status)
    if not state.skip_recorded:
        append_execution_metrics(state.worktree, metrics, task_type)
    commit_contribution(state, f"Record the Forge run for {state.coordinate}")
    write_publication_descriptor(
        repo_path=state.worktree,
        coordinates=state.coordinate,
        producer=producer,
        branch=branch,
        publication_id=publication_id,
        base_commit=state.base_commit,
        descriptor_input=PublicationDescriptorInput(
            issue_number=state.issue_number,
            task_type=task_type,
            template_type=task_type,
            status=status,
            timestamp=state.started_at,
            run_metrics=metrics,
            previous_coordinates=state.previous_coordinate,
            chunked_dynamic_access=state.chunk.active,
            chunk_final=final,
            follow_ups=descriptor_follow_ups,
            render=render(workspace, state, chunk_summary),
        ),
        local_ci_verification=verification(state),
        local_review=None,
    )
    commit_contribution(state, "Add Forge publication descriptor")
    remote: str = upstream_remote(state.worktree)
    git(state.worktree, "push", "--quiet", "--force", remote, f"HEAD:refs/heads/{branch}")
    if not final:
        label_chunked(state)
    state.checkpoint = head(state.worktree)
    save(workspace, state)
    write_text(runtime_path(workspace, "publication.md"), f"Pushed {branch} for {state.coordinate}.\n")
    print(f"[publish] pushed {branch}")
    return OK
