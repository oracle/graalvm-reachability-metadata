# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Staged trace-metadata inspection, merging, and durable finalization.

Owns the gate's metadata staging areas: collected per-cycle trace files, their
convergence entries, native-image-utils merging, and finalization into the
durable library metadata file (§FS-native-test-verification-gate).
"""

from __future__ import annotations

import hashlib
import json
import os
import re
import shutil
import tempfile

from utility_scripts.gradle_environment import gradle_command_environment
from utility_scripts.metadata_index import resolve_metadata_version
from utility_scripts.native_trace_execution import (
    _FAILURE_LOG_TAIL_LINE_LIMIT,
    _GATE_STAGE,
    _extract_failure_log_tail,
    _gate_log_path,
    _run_logged_gradle_command,
)
from utility_scripts.stage_logger import log_detail, log_stage

_TRACE_SENTINEL_FILE_NAMES = frozenset({"binary-exit-code"})
_AGGREGATED_METADATA_FILE_NAME = "reachability-metadata.json"


def _print_collected_metadata(run_dir: str, cycle_number: int) -> None:
    """Print the trace metadata collected in ``run_dir`` for one gate cycle."""
    metadata_files = _metadata_files(run_dir)
    if not metadata_files:
        log_detail(
            _GATE_STAGE,
            f"cycle {cycle_number}: collected metadata: none ({run_dir})",
            indent_level=1,
        )
        return

    log_detail(
        _GATE_STAGE,
        f"cycle {cycle_number}: collected metadata from {len(metadata_files)} file(s) ({run_dir})",
        indent_level=1,
    )


def _print_failure_log_tail(log_path: str, cycle_number: int) -> None:
    """Print the native trace failure log tail."""
    log_detail(
        _GATE_STAGE,
        f"cycle {cycle_number}: failure log tail (last {_FAILURE_LOG_TAIL_LINE_LIMIT} lines) from {log_path}:",
        indent_level=1,
    )
    for line in _extract_failure_log_tail(log_path).splitlines():
        log_detail(_GATE_STAGE, line, indent_level=2)


def _print_metadata_progress(
        accepted_run_dirs: list[str],
        accepted_entry_count: int,
        current_entry_count: int,
        reason: str,
) -> None:
    """Print trace-loop progress before a no-progress failure."""
    log_detail(
        _GATE_STAGE,
        (
            f"metadata progress stalled ({reason}): "
            f"accepted_runs={len(accepted_run_dirs)}, "
            f"accepted_unique_entries={accepted_entry_count}, "
            f"current_cycle_entries={current_entry_count}"
        ),
        indent_level=1,
    )
    if not accepted_run_dirs:
        return
    log_detail(_GATE_STAGE, "accepted run dirs:", indent_level=1)
    for run_dir in accepted_run_dirs:
        log_detail(_GATE_STAGE, run_dir, indent_level=2)


def _existing_metadata_dirs(paths: list[str]) -> list[str]:
    return [
        path
        for path in paths
        if os.path.isfile(os.path.join(path, _AGGREGATED_METADATA_FILE_NAME))
    ]


def _metadata_files(run_dir: str) -> list[str]:
    """Return trace metadata files below ``run_dir`` in deterministic order."""
    result: list[str] = []
    for root, _dirs, files in os.walk(run_dir):
        for name in files:
            if name in _TRACE_SENTINEL_FILE_NAMES:
                continue
            result.append(os.path.join(root, name))
    return sorted(result, key=lambda path: os.path.relpath(path, run_dir))


def _usable_metadata_files(run_dir: str) -> list[str]:
    """Return trace metadata files that contain at least one metadata entry."""
    return [path for path in _metadata_files(run_dir) if _metadata_file_has_entries(path)]


def _metadata_file_has_entries(path: str) -> bool:
    """Return true when ``path`` contains usable trace metadata."""
    try:
        with open(path, "r", encoding="utf-8") as handle:
            data = json.load(handle)
    except (json.JSONDecodeError, UnicodeDecodeError):
        try:
            return os.path.getsize(path) > 0
        except OSError:
            return False
    except OSError:
        return False
    return _json_has_entries(data)


def _metadata_entries(run_dir: str) -> set[str]:
    """Return canonical metadata entries collected in ``run_dir``."""
    entries: set[str] = set()
    for metadata_file in _metadata_files(run_dir):
        relative_path = os.path.relpath(metadata_file, run_dir)
        try:
            with open(metadata_file, "r", encoding="utf-8") as handle:
                data = json.load(handle)
        except (json.JSONDecodeError, UnicodeDecodeError):
            opaque_digest = _opaque_file_digest(metadata_file)
            if opaque_digest is not None:
                entries.add(f"{relative_path}::raw::{opaque_digest}")
            continue
        except OSError:
            continue
        entries.update(
            f"{relative_path}::{entry}"
            for entry in _flatten_metadata_entries("", data)
        )
    return entries


def _flatten_metadata_entries(path: str, value: object) -> list[str]:
    """Flatten metadata JSON into stable entry strings."""
    if isinstance(value, list):
        return [
            f"{path}::{json.dumps(item, sort_keys=True)}"
            for item in value
        ]
    if isinstance(value, dict):
        result: list[str] = []
        for key in sorted(value.keys()):
            child_path = f"{path}/{key}" if path else key
            result.extend(_flatten_metadata_entries(child_path, value[key]))
        return result
    return [f"{path}::{json.dumps(value, sort_keys=True)}"]


def _opaque_file_digest(path: str) -> str | None:
    """Return a stable digest for non-JSON metadata files."""
    try:
        with open(path, "rb") as handle:
            return hashlib.sha256(handle.read()).hexdigest()
    except OSError:
        return None


def _json_has_entries(value: object) -> bool:
    """Return true when a parsed JSON value contains a non-empty entry."""
    if isinstance(value, dict):
        return any(_json_has_entries(child) for child in value.values())
    if isinstance(value, list):
        return any(_json_has_entries(child) for child in value)
    return value is not None


def _format_metadata_file(path: str) -> str:
    """Return a readable representation of one collected metadata file."""
    try:
        with open(path, "r", encoding="utf-8") as handle:
            data = json.load(handle)
    except (json.JSONDecodeError, UnicodeDecodeError):
        return _read_text_or_binary_summary(path)
    except OSError as exc:
        return f"<unreadable: {exc}>"
    return json.dumps(data, indent=2, sort_keys=True)


def _read_text_or_binary_summary(path: str) -> str:
    """Return text content for UTF-8 files, or a compact summary for binary files."""
    try:
        with open(path, "rb") as handle:
            raw = handle.read()
    except OSError as exc:
        return f"<unreadable: {exc}>"
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError:
        return f"<binary metadata file: {len(raw)} bytes>"
    return text if text else "<empty>"


_MERGE_TIMEOUT_SECONDS = 5 * 60


def _merge_into_output(
        reachability_repo_path: str,
        run_dirs: list[str],
        output_dir: str,
        env: dict[str, str] | None = None,
) -> bool:
    """Merge accepted per-cycle trace dirs into the caller's ``output_dir``."""
    return _merge_metadata_dirs(
        reachability_repo_path,
        run_dirs,
        output_dir,
        print_output_path=True,
        env=env,
    )


def _merge_metadata_dirs(
        reachability_repo_path: str,
        input_dirs: list[str],
        output_dir: str,
        print_output_path: bool = False,
        env: dict[str, str] | None = None,
) -> bool:
    """Merge metadata directories through native-image-utils."""
    if not input_dirs:
        return True
    cmd = [
        "./gradlew",
        "mergeNativeTraceMetadata",
        f"-PinputDirs={','.join(input_dirs)}",
        f"-PoutputDir={output_dir}",
    ]
    merge_log_path = _gate_log_path("native-trace", 0, "mergeNativeTraceMetadata")
    result = _run_logged_gradle_command(
        reachability_repo_path=reachability_repo_path,
        cmd=cmd,
        log_path=merge_log_path,
        timeout_seconds=_MERGE_TIMEOUT_SECONDS,
        env=env or gradle_command_environment(reachability_repo_path),
    )
    if result.returncode != 0:
        log_stage(
            _GATE_STAGE,
            f"mergeNativeTraceMetadata failed with exit code {result.returncode}",
            indent_level=1,
        )
        return False
    if print_output_path:
        _print_aggregated_metadata_path(output_dir)
    return True


def _print_aggregated_metadata_path(output_dir: str) -> None:
    """Print only the path to the merged reachability metadata file."""
    log_detail(
        _GATE_STAGE,
        os.path.join(output_dir, _AGGREGATED_METADATA_FILE_NAME),
        indent_level=1,
    )


def _finalize_staged_metadata(
        reachability_repo_path: str,
        coordinate: str,
        metadata_dirs: list[str],
        env: dict[str, str] | None = None,
) -> bool:
    """Merge staged agent/trace metadata into the durable library metadata file."""
    staged_metadata_dirs = _existing_metadata_dirs(metadata_dirs)
    if not staged_metadata_dirs:
        log_detail(_GATE_STAGE, "no staged reachability-metadata.json to finalize")
        return True

    try:
        group, artifact, library_version = coordinate.split(":", 2)
        metadata_version = resolve_metadata_version(
            reachability_repo_path,
            group,
            artifact,
            library_version,
        )
    except (OSError, ValueError) as exc:
        log_stage(_GATE_STAGE, f"failed to resolve durable metadata path: {exc}", indent_level=1)
        return False

    durable_metadata_dir = os.path.join(
        reachability_repo_path,
        "metadata",
        group,
        artifact,
        metadata_version,
    )
    durable_metadata_path = os.path.join(
        durable_metadata_dir,
        _AGGREGATED_METADATA_FILE_NAME,
    )
    input_dirs = []
    if os.path.isfile(durable_metadata_path):
        input_dirs.append(durable_metadata_dir)
    input_dirs.extend(staged_metadata_dirs)

    try:
        with tempfile.TemporaryDirectory(prefix="native-trace-durable-") as temp_dir:
            merged_output_dir = os.path.join(temp_dir, "merged")
            if not _merge_metadata_dirs(reachability_repo_path, input_dirs, merged_output_dir, env=env):
                return False
            merged_metadata_path = os.path.join(merged_output_dir, _AGGREGATED_METADATA_FILE_NAME)
            if not os.path.isfile(merged_metadata_path):
                log_stage(
                    _GATE_STAGE,
                    "native-image-utils produced no reachability-metadata.json for durable aggregation",
                    indent_level=1,
                )
                return False
            if os.path.isfile(durable_metadata_path) and _same_file_contents(
                    durable_metadata_path,
                    merged_metadata_path,
            ):
                log_detail(_GATE_STAGE, "native trace metadata already present in durable metadata")
                return True

            os.makedirs(durable_metadata_dir, exist_ok=True)
            shutil.copyfile(merged_metadata_path, durable_metadata_path)
    except OSError as exc:
        log_stage(_GATE_STAGE, f"failed to aggregate native trace metadata: {exc}", indent_level=1)
        return False
    log_detail(
        _GATE_STAGE,
        f"finalized staged metadata into {os.path.relpath(durable_metadata_path, reachability_repo_path)}",
    )
    return True


def _same_file_contents(left_path: str, right_path: str) -> bool:
    with open(left_path, "rb") as left_file, open(right_path, "rb") as right_file:
        return left_file.read() == right_file.read()


def _reset_directory(path: str) -> None:
    if os.path.exists(path):
        shutil.rmtree(path)
    os.makedirs(path, exist_ok=True)


def class_key_from_class_name(class_name: str) -> str:
    """Sanitize a Java class name for use as a per-class output-dir segment."""
    return re.sub(r"[^A-Za-z0-9_.-]", "_", class_name)


def per_class_output_dir(
        reachability_repo_path: str,
        group: str,
        artifact: str,
        version: str,
        class_name: str,
) -> str:
    """Return the per-class natively-collected output directory."""
    return os.path.join(
        reachability_repo_path,
        "tests",
        "src",
        group,
        artifact,
        version,
        "build",
        "natively-collected",
        class_key_from_class_name(class_name),
    )


def global_output_dir(
        reachability_repo_path: str,
        group: str,
        artifact: str,
        version: str,
) -> str:
    """Return the global (non-class-scoped) natively-collected output directory."""
    return os.path.join(
        reachability_repo_path,
        "tests",
        "src",
        group,
        artifact,
        version,
        "build",
        "natively-collected",
        "_global_",
    )
