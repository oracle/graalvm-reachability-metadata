# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The `prepare` program: read the issue, pin the base, spawn the worktree, add the version.

Every decision about the issue is made here, not by the launcher
(§AR-forge-workflow-pipeline.4): the route from its label, the coordinate from
its title, the version added beside its nearest supported one, the chunk state,
and the reporter's request. Inputs are verified before anything runs
(§root/PRCPL-verify-inputs, §FS-forge-run-requirements).
"""
from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
from datetime import datetime, timezone
from typing import Any

from utility_scripts.dynamic_access_chunks import require_previous_chunk_merged
from utility_scripts.dynamic_access_exhaust_report import (
    DynamicAccessExhaustReport,
    find_dynamic_access_exhaust_report_path,
)
from utility_scripts.gradle_dependency_cache import ensure_shared_dependency_cache
from utility_scripts.issue_requested_metadata import format_issue_requested_metadata_context
from utility_scripts.library_measurements import load_library_baseline
from utility_scripts.library_stats import load_library_stats_entry
from utility_scripts.library_update_target import library_update_target_summary, prepare_library_update_target
from utility_scripts.metadata_index import (
    LibraryUpdateTarget,
    VersionBackfillBaseline,
    is_not_for_native_image,
    nearest_prior_supported_version,
    resolve_metadata_version,
    resolve_version_backfill_baseline,
)

import gates
import issue
import index_record
import run_note
from build_tools import BASE_BRANCH, checkpoint, git, head, run_gradle, stop_gradle_daemons, upstream_remote
from exit_codes import BAD_INPUT, INFRASTRUCTURE, NOT_FOR_NATIVE_IMAGE_RECORDED, OK
from run_state import ROUTE_NEW, Budgets, Chunk, RunState, load, runtime_path, save, write_text

# <agent>[<mode>]:<model> with agent pi or claude-code (§FS-forge-agent-runtime-selection).
TARGET: re.Pattern[str] = re.compile(r"^(pi|claude-code)(\[[A-Za-z0-9_-]+\])?:.+$")
# Issues the compatibility automation opens carry no reporter request (§root/FS-test-contract.1.5).
AUTOMATION_AUTHORS: tuple[str, ...] = ("github", "app/github", "graalvmbot")
MAX_REQUEST_CHARS: int = 50_000


def _resolve(workspace: str, path: str) -> str:
    return path if os.path.isabs(path) else os.path.normpath(os.path.join(workspace, path))


def pin_base(repo: str) -> str:
    """Fetch upstream `master` and return the commit the run branches from."""
    remote: str = upstream_remote(repo)
    git(repo, "fetch", "--quiet", remote, BASE_BRANCH)
    return git(repo, "rev-parse", f"{remote}/{BASE_BRANCH}").strip()


def spawn_worktree(repo: str, path: str, branch: str, base: str) -> None:
    """Create the run worktree on its branch, reset to the pinned base.

    A leftover of an earlier run of the coordinate, its worktree or its branch
    alone, is removed first: a run never carries old commits over a new base
    (§AR-forge-workflow-pipeline.4). A resumed run never gets here, because
    `run` keeps a worktree its own state names.
    """
    git(repo, "worktree", "prune")
    if os.path.exists(path):
        git(repo, "worktree", "remove", "--force", path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    git(repo, "worktree", "add", "--quiet", "-B", branch, path, base)
    # The path's Gradle home outlives the worktree, and so do its idle daemons.
    stop_gradle_daemons(path)
    share_dependency_cache(repo, path)


def share_dependency_cache(repo: str, worktree: str) -> None:
    """Prune orphaned Gradle homes, warm the checkout home from `worktree`, and publish its cache.

    Best effort (§FS-forge-run-requirements.4): a failure is reported, the run
    continues, and its worktree downloads more. The run's Gradle commands read
    the published cache through `gradle_command_environment`.
    """
    try:
        ensure_shared_dependency_cache(repo, worktree)
    except OSError as error:
        print(f"WARNING: the shared Gradle dependency cache was not prepared: {error}", file=sys.stderr)


def attach_worktree(workspace: str, worktree: str) -> None:
    """Point Rhei at the worktree, so every later agent state of the task runs inside it."""
    task_id: str | None = os.environ.get("RHEI_TASK_ID")
    if not task_id:
        return
    write_text(os.path.join(workspace, "runtime", "worktree-refs", f"{task_id}.yaml"), f"path: {worktree}\n")


def reporter_request(payload: dict[str, Any]) -> str:
    """The reporter's issue body, bounded, or empty for an issue the automation opened."""
    login: str = str((payload.get("author") or {}).get("login") or "")
    if login.startswith(AUTOMATION_AUTHORS):
        return ""
    body: str = str(payload.get("body") or "").strip()
    return body if len(body) <= MAX_REQUEST_CHARS else body[:MAX_REQUEST_CHARS].rstrip() + "\n[truncated]"


def previous_coverage(worktree: str, coordinate: str) -> tuple[int, int] | None:
    """Covered and total dynamic-access calls the coordinate's stats record, the explore goal of an update."""
    group, artifact, version = coordinate.split(":")
    entry: dict[str, Any] | None = load_library_stats_entry(worktree, group, artifact, version)
    dynamic_access: Any = (entry or {}).get("dynamicAccess")
    if not isinstance(dynamic_access, dict):
        return None
    return int(dynamic_access.get("coveredCalls") or 0), int(dynamic_access.get("totalCalls") or 0)


def previous_version(state: RunState, baseline: VersionBackfillBaseline | None) -> str | None:
    """The metadata version the run is measured against (§AR-forge-workflow-pipeline.7).

    It is the entry the version is added beside, or, for a version owning its
    entry, the entry of the nearest earlier supported version. A tested version
    is resolved to its entry, since only the entry has metadata, stats, and tests.
    """
    if baseline is None:
        return None
    if baseline.metadata_version != state.version:
        return baseline.metadata_version
    prior: str | None = nearest_prior_supported_version(state.worktree, state.group, state.artifact, state.version)
    return None if prior is None else resolve_metadata_version(state.worktree, state.group, state.artifact, prior)


def add_version(state: RunState, workspace: str) -> int:
    """Scaffold a new library, or add the version beside its nearest supported one."""
    if state.route == ROUTE_NEW:
        if os.path.isdir(state.path(state.test_dir)):
            return OK
        scaffold = run_gradle(
            state.worktree, ["scaffold", f"--coordinates={state.coordinate}"],
            runtime_path(workspace, "logs", "scaffold.log"),
        )
        if not scaffold.ok or not os.path.isdir(state.path(state.test_dir)):
            print(f"ERROR: scaffolding {state.coordinate} failed:\n{scaffold.tail}", file=sys.stderr)
            return INFRASTRUCTURE
        return OK
    baseline: VersionBackfillBaseline | None = resolve_version_backfill_baseline(
        state.worktree, state.group, state.artifact, state.version)
    previous: str | None = previous_version(state, baseline)
    if previous is not None:
        state.previous_coordinate = f"{state.group}:{state.artifact}:{previous}"
        goal: tuple[int, int] | None = previous_coverage(state.worktree, state.previous_coordinate)
        if goal is not None:
            state.goal_covered_calls, state.goal_total_calls = goal
    target: LibraryUpdateTarget = prepare_library_update_target(state.worktree, state.group, state.artifact,
                                                                 state.version)
    if not os.path.isdir(state.path(state.test_dir)):
        print(f"ERROR: no test project for {state.coordinate} after adding the version", file=sys.stderr)
        return INFRASTRUCTURE
    # Before any agent edits (§AR-pr-body): an owned version's pinned-base state, or else
    # the copy of the entry it was added beside.
    state.update_target = library_update_target_summary(target)
    state.before = load_library_baseline(state.worktree, state.group, state.artifact, state.version)
    return OK


def resolve_chunk(state: RunState, labels: frozenset[str]) -> None:
    """Load a chunked issue's processed classes once its previous chunk merged.

    §FS-forge-chunked-dynamic-access
    """
    if issue.CHUNKED_LABEL not in labels:
        return
    report_path: str | None = find_dynamic_access_exhaust_report_path(state.worktree, state.coordinate)
    if report_path is None:
        raise ValueError(f"chunked issue has no exhaust report for {state.coordinate}")
    report: DynamicAccessExhaustReport = DynamicAccessExhaustReport.load(report_path)
    require_previous_chunk_merged(report, state.worktree)
    state.chunk = Chunk(active=True, processed=sorted(report.processed_classes()),
                        budget=state.budgets.defer_classes)


def validate_inputs(args: argparse.Namespace) -> str | None:
    """The first bad template input, or `None`."""
    for name in ("bulk_min_classes", "bulk_passes", "class_attempts", "defer_classes", "explore_passes",
                 "finalize_fixes", "native_fixes"):
        if getattr(args, name) < 1:
            return f"{name} must be positive"
    for name in ("setup_agent", "worker_agent"):
        if TARGET.match(getattr(args, name)) is None:
            return f"{name} {getattr(args, name)} must be a pi or claude-code target"
    if not args.setup_agent.startswith("claude-code"):
        return f"setup_agent {args.setup_agent} must be a claude-code target, which has web search"
    return None


def run(args: argparse.Namespace) -> int:
    workspace: str = os.path.abspath(args.workspace)
    if os.path.isfile(runtime_path(workspace, "state.json")):
        state: RunState = load(workspace)
        if os.path.isdir(state.worktree):
            attach_worktree(workspace, state.worktree)
            print(f"[prepare] resuming {state.coordinate} in {state.worktree}")
            return NOT_FOR_NATIVE_IMAGE_RECORDED if state.not_for_native_image is not None else OK
    error: str | None = validate_inputs(args)
    if error is not None:
        print(f"ERROR: {error}.", file=sys.stderr)
        return BAD_INPUT
    repo: str = _resolve(workspace, args.repo_checkout)
    try:
        payload: dict[str, Any] = issue.read_issue(args.issue_number, repo)
        parsed: issue.Issue = issue.parse_issue(payload)
    except ValueError as error_value:
        print(f"ERROR: issue #{args.issue_number}: {error_value}", file=sys.stderr)
        return BAD_INPUT
    run_name: str = "forge-" + parsed.coordinate.replace(":", "-")
    # Resolved like git's own top-level path, which the worktree ref must name.
    worktree: str = os.path.realpath(os.path.join(_resolve(workspace, args.worktree_root), run_name))
    branch: str = f"rhei/{run_name}"
    base: str = pin_base(repo)
    try:
        spawn_worktree(repo, worktree, branch, base)
    except subprocess.CalledProcessError as error_value:
        print(f"ERROR: worktree: {error_value}", file=sys.stderr)
        return BAD_INPUT
    state = RunState(
        issue_number=parsed.number,
        label=parsed.label,
        route=parsed.route,
        coordinate=parsed.coordinate,
        repo_checkout=repo,
        worktree=worktree,
        branch=branch,
        base_commit=base,
        checkpoint=head(worktree),
        started_at=datetime.now(timezone.utc).replace(microsecond=0).isoformat(),
        setup_agent=args.setup_agent,
        worker_agent=args.worker_agent,
        budgets=Budgets(args.bulk_min_classes, args.bulk_passes, args.class_attempts,
                        args.defer_classes, args.explore_passes, args.finalize_fixes, args.native_fixes),
    )
    if is_not_for_native_image(worktree, state.group, state.artifact):
        print(f"ERROR: {state.group}:{state.artifact} is marked not-for-native-image.", file=sys.stderr)
        return BAD_INPUT
    code: int | None = index_record.record_not_for_native_image(workspace, state)
    if code is not None:
        if code == NOT_FOR_NATIVE_IMAGE_RECORDED:
            attach_worktree(workspace, worktree)
            save(workspace, state)
            run_note.write(workspace, state)
            print(f"[prepare] recorded {state.coordinate} as not-for-native-image at {state.checkpoint}")
        return code
    code = add_version(state, workspace)
    if code != OK:
        return code
    try:
        resolve_chunk(state, parsed.labels)
    except ValueError as error_value:
        print(f"ERROR: {error_value}", file=sys.stderr)
        return BAD_INPUT
    except RuntimeError as error_value:
        print(f"ERROR: the previous chunk is not ready: {error_value}", file=sys.stderr)
        return INFRASTRUCTURE
    request: str = reporter_request(payload)
    state.has_reporter_request = bool(request)
    state.requested_metadata = gates.requested_entries(request)
    if request:
        write_text(runtime_path(workspace, "reporter-request.md"), format_issue_requested_metadata_context(request) + "\n")
    state.checkpoint = checkpoint(worktree, state.coordinate_paths, f"Prepare {state.coordinate}")
    attach_worktree(workspace, worktree)
    save(workspace, state)
    run_note.write(workspace, state)
    print(f"[prepare] {parsed.label} {state.coordinate} on {branch} at {state.checkpoint}")
    return OK


def add_arguments(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--issue-number", type=int, required=True)
    parser.add_argument("--setup-agent", required=True)
    parser.add_argument("--worker-agent", required=True)
    for name in ("bulk-min-classes", "bulk-passes", "class-attempts", "defer-classes", "explore-passes",
                 "finalize-fixes", "native-fixes"):
        parser.add_argument(f"--{name}", type=int, required=True)
    parser.add_argument("--repo-checkout", required=True)
    parser.add_argument("--worktree-root", required=True)

