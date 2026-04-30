# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Iterative native-image metadata-tracing loop.

Implements the contract specified in
``forge/docs/native-metadata-exploration.md``. The phase shells out only to
``./gradlew``; sequencing with codex / Pi / agents is the responsibility of
callers (today: the native test verification gate in
``utility_scripts/native_test_verification.py`` and the
``native_trace_collect`` post-generation intervention).
"""

from __future__ import annotations

import json
import os
import shutil
import subprocess
from dataclasses import dataclass, field
from typing import Any

from utility_scripts.stage_logger import log_stage
from utility_scripts.task_logs import (
    build_timestamped_task_log_path,
    display_log_path,
    sanitize_library_log_segment,
)


STATUS_SUCCESS = "SUCCESS"
STATUS_BUDGET_EXHAUSTED = "BUDGET_EXHAUSTED"
STATUS_BUILD_FAILED = "BUILD_FAILED"

_TRACE_STAGE = "native-trace"
_LOG_TASK_TYPE = "native-trace"


@dataclass
class NativeExplorationFailure:
    """Diagnostics for a non-SUCCESS exploration result."""

    failed_task: str
    failed_iteration: int | None
    failure_log_path: str
    failure_summary: str


@dataclass
class NativeExplorationResult:
    """Result of one ``run_native_metadata_exploration`` invocation."""

    status: str
    output_dir: str
    runs_dir: str
    iterations_used: int
    run_dirs: list[str] = field(default_factory=list)
    build_log_paths: list[str] = field(default_factory=list)
    run_log_paths: list[str] = field(default_factory=list)
    merge_log_path: str | None = None
    failure: NativeExplorationFailure | None = None


def run_native_metadata_exploration(
        reachability_repo_path: str,
        coordinate: str,
        output_dir: str,
        condition_packages: list[str] | None = None,
        max_iterations: int = 5,
) -> NativeExplorationResult:
    """Run the iterative trace loop for ``coordinate``.

    See ``forge/docs/native-metadata-exploration.md`` for the full contract.
    """
    if max_iterations < 1:
        raise ValueError("max_iterations must be >= 1")
    if not os.path.isabs(output_dir):
        raise ValueError("output_dir must be an absolute path")

    group = coordinate.split(":", 1)[0]
    packages = list(condition_packages) if condition_packages else [group]
    condition_packages_arg = ",".join(packages)

    runs_dir = os.path.normpath(os.path.join(output_dir, "..", "runs"))
    _reset_directory(output_dir)
    _reset_directory(runs_dir)

    log_stage(
        _TRACE_STAGE,
        f"start coordinate={coordinate} output_dir={output_dir} max_iterations={max_iterations}",
    )

    run_dirs: list[str] = []
    accepted_entries: set[str] = set()
    build_log_paths: list[str] = []
    run_log_paths: list[str] = []

    iterations_used = 0
    terminal_status: str | None = None
    failure: NativeExplorationFailure | None = None

    for i in range(max_iterations):
        iterations_used = i + 1

        # Build trace-enabled image with all prior raw runs as config dirs.
        build_cmd = [
            "./gradlew",
            "nativeTraceImage",
            f"-Pcoordinates={coordinate}",
        ]
        if run_dirs:
            build_cmd.append(f"-PmetadataConfigDirs={','.join(run_dirs)}")
        build_log = _new_log_path(coordinate, f"build-iter-{i}")
        build_log_paths.append(build_log)
        build_rc = _run_gradle(build_cmd, reachability_repo_path, build_log)
        if build_rc != 0:
            terminal_status = STATUS_BUILD_FAILED
            failure = NativeExplorationFailure(
                failed_task="nativeTraceImage",
                failed_iteration=i,
                failure_log_path=build_log,
                failure_summary=_extract_failure_summary(build_log),
            )
            log_stage(
                _TRACE_STAGE,
                f"iter={i} nativeTraceImage failed; see {display_log_path(build_log)}",
            )
            break

        # Run trace binary; exit code is informational only.
        run_i = os.path.join(runs_dir, f"metadata-run-{i}")
        os.makedirs(run_i, exist_ok=True)
        run_cmd = [
            "./gradlew",
            "runNativeTraceImage",
            f"-Pcoordinates={coordinate}",
            f"-PtraceMetadataPath={run_i}",
            f"-PtraceMetadataConditionPackages={condition_packages_arg}",
        ]
        run_log = _new_log_path(coordinate, f"run-iter-{i}")
        run_log_paths.append(run_log)
        _run_gradle(run_cmd, reachability_repo_path, run_log)

        # Convergence check on raw run dirs.
        new_entries = _collect_entries(run_i)
        added_entries = new_entries - accepted_entries
        if not added_entries:
            log_stage(
                _TRACE_STAGE,
                f"iter={i} converged: run produced no new entries",
            )
            terminal_status = STATUS_SUCCESS
            break

        accepted_entries.update(added_entries)
        run_dirs.append(run_i)
        log_stage(
            _TRACE_STAGE,
            f"iter={i} accepted: {len(added_entries)} new entries (total {len(accepted_entries)})",
        )
    else:
        terminal_status = STATUS_BUDGET_EXHAUSTED
        log_stage(_TRACE_STAGE, f"budget exhausted after {iterations_used} iterations")

    # Single merge if any iteration produced new metadata.
    merge_log_path: str | None = None
    if run_dirs:
        merge_cmd = [
            "./gradlew",
            "mergeNativeTraceMetadata",
            f"-PinputDirs={','.join(run_dirs)}",
            f"-PoutputDir={output_dir}",
        ]
        merge_log_path = _new_log_path(coordinate, "merge")
        merge_rc = _run_gradle(merge_cmd, reachability_repo_path, merge_log_path)
        if merge_rc != 0:
            log_stage(
                _TRACE_STAGE,
                f"mergeNativeTraceMetadata failed; see {display_log_path(merge_log_path)}",
            )
            terminal_status = STATUS_BUILD_FAILED
            failure = NativeExplorationFailure(
                failed_task="mergeNativeTraceMetadata",
                failed_iteration=None,
                failure_log_path=merge_log_path,
                failure_summary=_extract_failure_summary(merge_log_path),
            )

    log_stage(
        _TRACE_STAGE,
        f"end status={terminal_status} iterations={iterations_used} accepted_runs={len(run_dirs)}",
    )

    return NativeExplorationResult(
        status=terminal_status or STATUS_BUDGET_EXHAUSTED,
        output_dir=output_dir,
        runs_dir=runs_dir,
        iterations_used=iterations_used,
        run_dirs=run_dirs,
        build_log_paths=build_log_paths,
        run_log_paths=run_log_paths,
        merge_log_path=merge_log_path,
        failure=failure,
    )


def _reset_directory(path: str) -> None:
    if os.path.exists(path):
        shutil.rmtree(path)
    os.makedirs(path, exist_ok=True)


def _run_gradle(cmd: list[str], cwd: str, log_path: str) -> int:
    """Run a Gradle command, persisting combined stdout/stderr to ``log_path``."""
    log_stage(
        _TRACE_STAGE,
        f"$ {' '.join(cmd)}  (log: {display_log_path(log_path)})",
        indent_level=1,
    )
    with open(log_path, "w", encoding="utf-8") as log_file:
        result = subprocess.run(
            cmd,
            cwd=cwd,
            stdout=log_file,
            stderr=subprocess.STDOUT,
            check=False,
        )
    return result.returncode


def _new_log_path(coordinate: str, prefix: str) -> str:
    return build_timestamped_task_log_path(
        _LOG_TASK_TYPE,
        sanitize_library_log_segment(coordinate),
        prefix,
    )


def _collect_entries(run_dir: str) -> set[str]:
    """Return a set of canonical-string entries representing ``run_dir`` content.

    The set is used solely for the convergence comparison: an iteration that
    adds no element to the cumulative set is considered convergent. Each JSON
    file under ``run_dir`` is parsed and walked; primitive leaves and small
    dict/list nodes are emitted as canonical-JSON strings keyed by their
    file-relative location, so two runs producing identical content yield
    identical sets.
    """
    entries: set[str] = set()
    for root, _dirs, files in os.walk(run_dir):
        for name in files:
            absolute = os.path.join(root, name)
            relative = os.path.relpath(absolute, run_dir)
            try:
                with open(absolute, "r", encoding="utf-8") as handle:
                    data = json.load(handle)
            except (OSError, json.JSONDecodeError):
                # Non-JSON or unreadable: hash as a single opaque entry so
                # adding/removing it counts as a delta.
                try:
                    with open(absolute, "rb") as handle:
                        opaque = handle.read()
                except OSError:
                    continue
                entries.add(f"{relative}::raw::{hash(opaque)}")
                continue
            for entry in _flatten_json(data):
                entries.add(f"{relative}::{entry}")
    return entries


def _flatten_json(node: Any) -> list[str]:
    """Yield canonical-JSON-string entries from a JSON node."""
    if isinstance(node, list):
        result: list[str] = []
        for item in node:
            result.extend(_flatten_json(item))
        return result
    if isinstance(node, dict):
        # Treat each top-level k:v pair as a separate entry; this gives a
        # finer convergence signal than hashing the whole object.
        result = []
        for key in sorted(node.keys()):
            value = node[key]
            if isinstance(value, (list, dict)):
                result.extend(f"{key}/{leaf}" for leaf in _flatten_json(value))
            else:
                result.append(json.dumps({key: value}, sort_keys=True))
        return result
    return [json.dumps(node, sort_keys=True)]


def _extract_failure_summary(log_path: str) -> str:
    """Extract a short summary string from a Gradle log file."""
    try:
        with open(log_path, "r", encoding="utf-8") as handle:
            lines = handle.readlines()
    except OSError:
        return "log_unavailable"
    for line in lines:
        stripped = line.rstrip()
        if "FAILED" in stripped and stripped.lstrip().startswith("> Task :"):
            return stripped.strip()
    for line in lines:
        if "BUILD FAILED" in line:
            return line.strip()
    for line in lines:
        if line.startswith("FAILURE:"):
            return line.strip()
    return "build_failed_without_specific_summary"
