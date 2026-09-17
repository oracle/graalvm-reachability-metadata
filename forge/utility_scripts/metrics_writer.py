# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""
Persist run-metrics JSON: per-library execution metrics, pending metrics, and committed metrics files.
"""

import json
import os
import shutil
import subprocess
import sys
import tempfile

from utility_scripts.metadata_index import resolve_metadata_version
from utility_scripts.library_measurements import (  # noqa: F401 - re-exported for existing callers
    count_metadata_entries,
    count_test_only_metadata_entries,
)
from utility_scripts.run_metrics_payloads import (  # noqa: F401 - re-exported for existing callers
    create_failure_run_metrics_output,
    create_java_run_fix_run_metrics_output_json,
    create_javac_fix_run_metrics_output_json,
)
from utility_scripts.schema_validator import validate_run_metrics
from git_scripts.common_git import GitTransportError, git_remote_exists, run_git_transport


def _load_run_metrics_entries_for_update(path):
    """Load an existing metrics array from disk, tolerating an empty file."""
    if not os.path.isfile(path):
        return []

    with open(path, "r", encoding="utf-8") as f:
        try:
            data = json.load(f)
        except json.JSONDecodeError:
            data = None

    if data is None:
        return []
    if not isinstance(data, list):
        raise TypeError(f"ERROR: Expected metrics array in {path}")
    return data


def _run_metrics_entry_sort_key(entry: dict) -> str:
    """Return the library-name sort key for persisted run metrics."""
    if not isinstance(entry, dict):
        return ""

    library = entry.get("library")
    return library if isinstance(library, str) else ""


def _run_metrics_key(task_type: str, timestamp: str) -> str:
    """Build the stable execution-metrics key for a run."""
    date = timestamp.split("T", 1)[0]
    return f"{task_type}:{date}"


def execution_metrics_path(repo_path: str, run_metrics: dict) -> str:
    """Return the per-library execution metrics path for a run metrics entry."""
    library = run_metrics.get("library")
    if not isinstance(library, str):
        raise TypeError("ERROR: run_metrics library must be a string")

    parts = library.split(":")
    if len(parts) != 3 or any(not part for part in parts):
        raise ValueError(f"ERROR: run_metrics library must be group:artifact:version: {library}")

    group, artifact, version = parts
    metadata_version = resolve_metadata_version(repo_path, group, artifact, version)
    return os.path.join(repo_path, "stats", group, artifact, metadata_version, "execution-metrics.json")


def execution_metrics_path_for_library(repo_path: str, library: str) -> str:
    """Return the per-library execution metrics path for a coordinate."""
    parts = library.split(":")
    if len(parts) != 3 or any(not part for part in parts):
        raise ValueError(f"ERROR: library must be group:artifact:version: {library}")

    group, artifact, version = parts
    metadata_version = resolve_metadata_version(repo_path, group, artifact, version)
    return os.path.join(repo_path, "stats", group, artifact, metadata_version, "execution-metrics.json")


def _load_execution_metrics_entries(path: str) -> dict:
    """Load an execution-metrics object from disk."""
    if not os.path.isfile(path):
        return {}

    with open(path, "r", encoding="utf-8") as metrics_file:
        data = json.load(metrics_file)

    if not isinstance(data, dict):
        raise TypeError(f"ERROR: Expected execution metrics object in {path}")
    return data


def load_execution_metrics_for_timestamp(
        repo_path: str,
        library: str,
        timestamp: str,
        task_type: str | None = None,
) -> dict | None:
    """Load the committed execution metrics entry for a library and timestamp."""
    metrics_path = execution_metrics_path_for_library(repo_path, library)
    entries = _load_execution_metrics_entries(metrics_path)
    if task_type is not None:
        entries = {
            key: entry
            for key, entry in entries.items()
            if key.startswith(f"{task_type}:")
        }
    for entry in entries.values():
        if not isinstance(entry, dict):
            continue
        if entry.get("library") == library and entry.get("timestamp") == timestamp:
            return dict(entry)
    return None


def _public_execution_metrics_entry(run_metrics: dict) -> dict:
    """Return the committed execution-metrics entry without local-only PR details."""
    public_metrics = dict(run_metrics)
    public_metrics.pop("post_generation_intervention", None)
    return public_metrics


def append_execution_metrics(repo_path: str, run_metrics: dict, task_type: str) -> str:
    """Append one run metrics entry under stats/<group>/<artifact>/<version>/execution-metrics.json."""
    timestamp = run_metrics.get("timestamp")
    if not isinstance(timestamp, str) or not timestamp:
        raise TypeError("ERROR: run_metrics timestamp must be a string")

    metrics_path = execution_metrics_path(repo_path, run_metrics)
    entries = _load_execution_metrics_entries(metrics_path)
    entries[_run_metrics_key(task_type, timestamp)] = _public_execution_metrics_entry(run_metrics)

    os.makedirs(os.path.dirname(metrics_path), exist_ok=True)
    with open(metrics_path, "w", encoding="utf-8") as metrics_file:
        json.dump(dict(sorted(entries.items())), metrics_file, indent=2, ensure_ascii=False)
        metrics_file.write("\n")

    return metrics_path


def in_metadata_repo_metrics_root(metrics_repo_root: str | None) -> str | None:
    """Return the parent reachability repo root when metrics root is the in-repo metadata-forge directory."""
    if not metrics_repo_root:
        return None

    repo_path = os.path.dirname(os.path.abspath(metrics_repo_root))
    if os.path.isdir(os.path.join(repo_path, "metadata")) and os.path.isdir(os.path.join(repo_path, "stats")):
        return repo_path
    return None


def _merge_run_metrics_entries(existing_entries: list, run_metrics: dict) -> list:
    """Merge a run metrics entry into an existing metrics array and keep library ordering stable."""
    merged_entries = list(existing_entries)

    if run_metrics not in merged_entries:
        merged_entries.append(run_metrics)

    merged_entries.sort(key=_run_metrics_entry_sort_key)
    return merged_entries


def _write_run_metrics_entries(path, entries) -> None:
    """Persist a run metrics array to disk."""
    parent_dir = os.path.dirname(path)
    if parent_dir:
        os.makedirs(parent_dir, exist_ok=True)

    with open(path, "w", encoding="utf-8") as f:
        json.dump(entries, f, indent=2, ensure_ascii=False)


def append_run_metrics(run_metrics, path):
    """
    Validate, merge, and write a single run_metrics object to a JSON array at `path`.
    """
    if not isinstance(run_metrics, dict):
        raise TypeError("ERROR: run_metrics must be a dict")

    existing = _load_run_metrics_entries_for_update(path)
    merged_entries = _merge_run_metrics_entries(existing, run_metrics)
    _write_run_metrics_entries(path, merged_entries)


def load_run_metrics_entries(path):
    """Load a metrics JSON array from disk, returning an empty list if absent."""
    if not os.path.isfile(path):
        return []

    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)

    if not isinstance(data, list):
        raise TypeError(f"ERROR: Expected metrics array in {path}")
    return data


def _select_latest_matching_run_metrics(entries: list, library: str, previous_library: str | None = None):
    """Return the latest matching run metrics entry from a loaded metrics array."""
    for entry in reversed(entries):
        if not isinstance(entry, dict):
            continue
        if entry.get("library") != library:
            continue
        if previous_library is not None and entry.get("previous_library") != previous_library:
            continue
        return entry
    return None


def load_matching_run_metrics(path, library, previous_library: str | None = None):
    """Load the most recent metrics entry matching the requested library identifiers."""
    entries = load_run_metrics_entries(path)
    return _select_latest_matching_run_metrics(entries, library, previous_library=previous_library)


def append_matching_run_metrics(source_path, destination_path, library, previous_library: str | None = None):
    """Append the latest matching metrics entry from one metrics file into another."""
    matched = load_matching_run_metrics(
        source_path,
        library,
        previous_library=previous_library,
    )
    if matched is None:
        if previous_library is None:
            raise ValueError(f"ERROR: No metrics entry found for {library} in {source_path}")
        raise ValueError(
            f"ERROR: No metrics entry found for {library} with previous library {previous_library} in {source_path}"
        )

    append_run_metrics(matched, destination_path)
    return matched


PENDING_METRICS_FILENAME = ".pending_metrics.json"


def _numeric_metric(value) -> int | float | None:
    if isinstance(value, bool):
        return None
    if isinstance(value, (int, float)):
        return value
    return None


def _same_metrics_series(previous_run_metrics: dict, run_metrics: dict) -> bool:
    """Return True when two metrics payloads describe the same logical library run."""
    if previous_run_metrics.get("library") != run_metrics.get("library"):
        return False
    return previous_run_metrics.get("timestamp") != run_metrics.get("timestamp")


def _merge_usage_metric(previous_metrics: dict, current_metrics: dict, key: str, integer: bool) -> None:
    previous = _numeric_metric(previous_metrics.get(key))
    if previous is None:
        return
    current = _numeric_metric(current_metrics.get(key)) or 0
    if integer:
        current_metrics[key] = int(current) + int(previous)
    else:
        current_metrics[key] = round(float(current) + float(previous), 4)


def merge_pending_usage_metrics(metrics_repo_root: str | None, run_metrics: dict) -> dict:
    """Add prior pending token/cost counters to this run before metrics are persisted."""
    if not metrics_repo_root:
        return run_metrics
    pending_path = os.path.join(metrics_repo_root, PENDING_METRICS_FILENAME)
    if not os.path.isfile(pending_path):
        return run_metrics
    try:
        previous_run_metrics = read_pending_metrics(metrics_repo_root)
    except (OSError, json.JSONDecodeError, TypeError):
        return run_metrics
    if not isinstance(previous_run_metrics, dict) or not _same_metrics_series(previous_run_metrics, run_metrics):
        return run_metrics

    previous_metrics = previous_run_metrics.get("metrics")
    current_metrics = run_metrics.get("metrics")
    if not isinstance(previous_metrics, dict) or not isinstance(current_metrics, dict):
        return run_metrics

    for key in ["input_tokens_used", "cached_input_tokens_used", "output_tokens_used", "iterations"]:
        _merge_usage_metric(previous_metrics, current_metrics, key, integer=True)
    for key in ["input_cost_usd", "cached_input_cost_usd", "output_cost_usd", "cost_usd"]:
        _merge_usage_metric(previous_metrics, current_metrics, key, integer=False)
    return run_metrics


def write_pending_metrics(metrics_repo_root: str, run_metrics: dict) -> None:
    """Write a run metrics dict to a pending file in the metrics worktree root."""
    path = os.path.join(metrics_repo_root, PENDING_METRICS_FILENAME)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(run_metrics, f, indent=2, ensure_ascii=False)


def read_pending_metrics(metrics_repo_root: str) -> dict:
    """Read the pending metrics dict from the metrics worktree root."""
    path = os.path.join(metrics_repo_root, PENDING_METRICS_FILENAME)
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def resolve_workflow_metrics_json(
        run_metrics: dict,
        metrics_repo_dir: str,
        metrics_repo_root: str | None,
        task_type: str,
) -> str:
    """Resolve the run-metrics JSON path for a workflow driver execution."""
    in_repo_root = in_metadata_repo_metrics_root(metrics_repo_root)
    if in_repo_root:
        return execution_metrics_path(in_repo_root, run_metrics)
    return os.path.join(metrics_repo_dir, f"{task_type}.json")


def write_workflow_run_metrics(
        run_metrics: dict,
        metrics_repo_dir: str,
        metrics_repo_root: str | None,
        task_type: str,
) -> str:
    """Append run metrics, write pending metrics, and validate the written file.

    The shared metrics-publication path for all workflow drivers
    (§AR-forge-driver-finalization); drivers contribute only their task type and
    the per-workflow metrics payload.
    """
    run_metrics = merge_pending_usage_metrics(metrics_repo_root, run_metrics)
    metrics_json = resolve_workflow_metrics_json(run_metrics, metrics_repo_dir, metrics_repo_root, task_type)
    in_repo_root = in_metadata_repo_metrics_root(metrics_repo_root)
    if in_repo_root:
        written_metrics_json = append_execution_metrics(in_repo_root, run_metrics, task_type)
        if written_metrics_json != metrics_json:
            raise ValueError(
                f"ERROR: Resolved metrics path {metrics_json} does not match written path {written_metrics_json}"
            )
    else:
        append_run_metrics(run_metrics, metrics_json)
    if metrics_repo_root:
        write_pending_metrics(metrics_repo_root, run_metrics)
    validate_run_metrics(metrics_json)
    return metrics_json


def _resolve_primary_worktree_root(repo_root: str) -> str:
    """Resolve the main checkout root that owns the shared git metadata."""
    result = subprocess.run(
        ["git", "rev-parse", "--git-common-dir"],
        cwd=repo_root,
        check=True,
        capture_output=True,
        text=True,
    )
    common_git_dir = result.stdout.strip()
    if not os.path.isabs(common_git_dir):
        common_git_dir = os.path.normpath(os.path.join(repo_root, common_git_dir))
    return os.path.dirname(common_git_dir)


def _commit_metrics_locally(
        metrics_repo_root: str,
        metrics_json_relative_path: str,
        run_metrics: dict,
        commit_message: str,
        extra_paths_to_stage: list[str] | None = None,
) -> None:
    """Commit metrics in the main checkout so detached scratch worktrees can be removed safely."""
    commit_repo_root = _resolve_primary_worktree_root(metrics_repo_root)
    metrics_json_absolute_path = os.path.join(commit_repo_root, metrics_json_relative_path)
    extra_paths = list(extra_paths_to_stage or [])
    snapshot_root = _snapshot_extra_paths(metrics_repo_root, extra_paths)

    try:
        append_run_metrics(run_metrics, metrics_json_absolute_path)
        _restore_extra_paths(commit_repo_root, snapshot_root, extra_paths)
        subprocess.run(["git", "add", metrics_json_relative_path], check=True, cwd=commit_repo_root)
        for relative in extra_paths:
            if os.path.exists(os.path.join(commit_repo_root, relative)):
                subprocess.run(["git", "add", relative], check=True, cwd=commit_repo_root)

        result = subprocess.run(["git", "diff", "--cached", "--quiet"], cwd=commit_repo_root)
        if result.returncode == 0:
            print(f"No changes in {metrics_json_relative_path} to commit in metrics repo.")
            return

        subprocess.run(
            [
                "git",
                "-c",
                "user.name=metadata-forge",
                "-c",
                "user.email=metadata-forge@local",
                "commit",
                "-m",
                commit_message,
            ],
            check=True,
            cwd=commit_repo_root,
        )
    finally:
        if snapshot_root:
            shutil.rmtree(snapshot_root, ignore_errors=True)


def _snapshot_extra_paths(metrics_repo_root: str, relative_paths: list[str]) -> str | None:
    """Snapshot working-tree paths to a temp dir so they survive a hard reset."""
    if not relative_paths:
        return None
    snapshot_root = tempfile.mkdtemp(prefix="forge-metrics-snapshot-")
    for relative in relative_paths:
        source = os.path.join(metrics_repo_root, relative)
        if not os.path.exists(source):
            continue
        destination = os.path.join(snapshot_root, relative)
        os.makedirs(os.path.dirname(destination), exist_ok=True)
        if os.path.isdir(source):
            shutil.copytree(source, destination, dirs_exist_ok=True)
        else:
            shutil.copy2(source, destination)
    return snapshot_root


def _restore_extra_paths(metrics_repo_root: str, snapshot_root: str | None, relative_paths: list[str]) -> None:
    """Restore previously snapshotted paths back into the metrics worktree."""
    if not snapshot_root:
        return
    for relative in relative_paths:
        source = os.path.join(snapshot_root, relative)
        if not os.path.exists(source):
            continue
        destination = os.path.join(metrics_repo_root, relative)
        os.makedirs(os.path.dirname(destination), exist_ok=True)
        if os.path.isdir(source):
            shutil.copytree(source, destination, dirs_exist_ok=True)
        else:
            shutil.copy2(source, destination)


def commit_run_metrics_with_retry(
        metrics_repo_root: str,
        metrics_json_relative_path: str,
        run_metrics: dict,
        commit_message: str,
        max_attempts: int = 5,
        extra_paths_to_stage: list[str] | None = None,
) -> None:
    """Merge a metrics entry into the latest remote file state, then commit and push with retries.

    `extra_paths_to_stage` lists repo-relative paths whose working-tree contents must survive the
    hard reset to origin/master and be staged alongside the metrics file (used for durable
    dynamic-access exhaust report). Both files and directories are supported.
    """
    metrics_json_absolute_path = os.path.join(metrics_repo_root, metrics_json_relative_path)
    has_origin = git_remote_exists("origin", cwd=metrics_repo_root)
    extra_paths = list(extra_paths_to_stage or [])

    if not has_origin:
        _commit_metrics_locally(
            metrics_repo_root=metrics_repo_root,
            metrics_json_relative_path=metrics_json_relative_path,
            run_metrics=run_metrics,
            commit_message=commit_message,
            extra_paths_to_stage=extra_paths,
        )
        return

    snapshot_root = _snapshot_extra_paths(metrics_repo_root, extra_paths)
    try:
        for attempt in range(1, max_attempts + 1):
            run_git_transport(["fetch", "origin", "master"], cwd=metrics_repo_root)
            subprocess.run(["git", "reset", "--hard", "origin/master"], check=True, cwd=metrics_repo_root)

            append_run_metrics(run_metrics, metrics_json_absolute_path)
            _restore_extra_paths(metrics_repo_root, snapshot_root, extra_paths)
            subprocess.run(["git", "add", metrics_json_relative_path], check=True, cwd=metrics_repo_root)
            for relative in extra_paths:
                if os.path.exists(os.path.join(metrics_repo_root, relative)):
                    subprocess.run(["git", "add", relative], check=True, cwd=metrics_repo_root)

            result = subprocess.run(["git", "diff", "--cached", "--quiet"], cwd=metrics_repo_root)
            if result.returncode == 0:
                print(f"No changes in {metrics_json_relative_path} to commit in metrics repo.")
                return

            subprocess.run(["git", "commit", "-m", commit_message], check=True, cwd=metrics_repo_root)
            try:
                run_git_transport(["push", "origin", "HEAD:master"], cwd=metrics_repo_root)
                return
            except GitTransportError as exc:
                push_output = exc.output or str(exc)

                if attempt < max_attempts:
                    print(
                        f"Retrying metrics push after attempt {attempt} failed: {push_output}",
                        file=sys.stderr,
                    )
                    continue

                print(
                    f"ERROR: Failed to push metrics after {max_attempts} attempts: {push_output}",
                    file=sys.stderr,
                )
                raise
    finally:
        if snapshot_root:
            shutil.rmtree(snapshot_root, ignore_errors=True)
