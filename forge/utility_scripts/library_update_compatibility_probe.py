# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Isolated compatibility probe for missing-version library-update routing."""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import time
from dataclasses import dataclass
from typing import Any, Literal

from utility_scripts.gradle_environment import gradle_command_environment
from utility_scripts.library_update_baseline import (
    LibraryUpdateBaseline,
    LibraryUpdateBaselineSetupError,
    prepare_probe_test_project,
    resolve_latest_supported_baseline,
)
from utility_scripts.repo_path_resolver import (
    get_repo_root,
    require_complete_reachability_repo,
)
from utility_scripts.task_logs import sanitize_library_log_segment, sanitize_log_segment
from utility_scripts.versioned_test_project import VersionedTestProjectPreparation


ROUTE_JAVAC_FAILURE = "javac-failure"
ROUTE_JAVA_RUN_FAILURE = "java-run-failure"
ROUTE_COMPATIBLE = "compatible"
ROUTE_SETUP_FAILURE = "setup-failure"
ROUTE_NAMES = (
    ROUTE_JAVAC_FAILURE,
    ROUTE_JAVA_RUN_FAILURE,
    ROUTE_COMPATIBLE,
    ROUTE_SETUP_FAILURE,
)
RouteName = Literal["javac-failure", "java-run-failure", "compatible", "setup-failure"]

ROUTER_DIR_NAME = "library-update-router"
PROBE_WORKTREE_DIR_NAME = "forge_compatibility_probes"
RESULT_FILE_NAME = "route-result.json"
COMPILE_LOG_FILE_NAME = "compileTestJava.log"
JAVA_TEST_LOG_FILE_NAME = "javaTest.log"


class CompatibilityProbeSetupError(RuntimeError):
    """Raised when the compatibility probe cannot create a runnable project."""


@dataclass(frozen=True)
class ProbeCommandResult:
    """Record for one ordered Gradle probe command."""

    command_args: tuple[str, ...]
    cwd: str
    exit_code: int | None
    elapsed_seconds: float
    log_path: str
    start_error: str | None = None

    def to_json(self) -> dict[str, Any]:
        """Return a small JSON-serializable command record."""
        return {
            "command_args": list(self.command_args),
            "cwd": self.cwd,
            "exit_code": self.exit_code,
            "elapsed_seconds": self.elapsed_seconds,
            "log_path": self.log_path,
            "start_error": self.start_error,
        }


@dataclass(frozen=True)
class ProbeIndexMapping:
    """Transient index entry that makes the requested coordinate runnable."""

    index_path: str
    metadata_version: str
    test_version: str
    tested_versions: tuple[str, ...]
    inserted_entry_index: int
    metadata_dir: str
    test_dir: str

    def to_json(self) -> dict[str, Any]:
        """Return a JSON-serializable index mapping record."""
        return {
            "index_path": self.index_path,
            "metadata_version": self.metadata_version,
            "test_version": self.test_version,
            "tested_versions": list(self.tested_versions),
            "inserted_entry_index": self.inserted_entry_index,
            "metadata_dir": self.metadata_dir,
            "test_dir": self.test_dir,
        }


@dataclass(frozen=True)
class CompatibilityProbeResult:
    """Final route decision and sidecar evidence for a missing-version probe."""

    requested_coordinate: str
    selected_route: RouteName
    selected_baseline_coordinate: str | None
    baseline_index_path: str | None
    metadata_version: str | None
    test_version: str | None
    baseline_selection_reason: str | None
    prepared_test_project: dict[str, Any] | None
    probe_index_mapping: dict[str, Any] | None
    probe_worktree_path: str | None
    cleanup_status: dict[str, Any]
    command_records: tuple[ProbeCommandResult, ...]
    setup_error: str | None
    sidecar_json_path: str | None
    log_paths: tuple[str, ...]

    def to_json(self) -> dict[str, Any]:
        """Return a JSON-serializable route result."""
        return {
            "requested_coordinate": self.requested_coordinate,
            "selected_route": self.selected_route,
            "selected_baseline_coordinate": self.selected_baseline_coordinate,
            "baseline_index_path": self.baseline_index_path,
            "metadata_version": self.metadata_version,
            "test_version": self.test_version,
            "baseline_selection_reason": self.baseline_selection_reason,
            "prepared_test_project": self.prepared_test_project,
            "probe_index_mapping": self.probe_index_mapping,
            "probe_worktree_path": self.probe_worktree_path,
            "cleanup_status": self.cleanup_status,
            "command_records": [
                command_result.to_json() for command_result in self.command_records
            ],
            "setup_error": self.setup_error,
            "sidecar_json_path": self.sidecar_json_path,
            "log_paths": list(self.log_paths),
        }


def probe_missing_version_compatibility(
        repo_path: str,
        scratch_metrics_path: str,
        requested_coordinate: str,
        issue_number: int | None = None,
        run_identifier: str | None = None,
) -> dict[str, Any]:
    """Probe a missing requested version in an isolated complete repo worktree.

    The claimed workflow worktree is used only as the `HEAD` source for a
    detached probe worktree. All copied tests and Gradle output stay in the
    probe worktree and sidecar directory.
    """
    sidecar_dir = _build_sidecar_dir(
        scratch_metrics_path,
        requested_coordinate,
        issue_number,
        run_identifier,
    )
    try:
        os.makedirs(sidecar_dir, exist_ok=True)
    except OSError as exc:
        return CompatibilityProbeResult(
            requested_coordinate=requested_coordinate,
            selected_route=ROUTE_SETUP_FAILURE,
            selected_baseline_coordinate=None,
            baseline_index_path=None,
            metadata_version=None,
            test_version=None,
            baseline_selection_reason=None,
            prepared_test_project=None,
            probe_index_mapping=None,
            probe_worktree_path=None,
            cleanup_status={"attempted": False, "removed": False, "error": None},
            command_records=(),
            setup_error=f"Failed to create router sidecar directory {sidecar_dir}: {exc}",
            sidecar_json_path=None,
            log_paths=(),
        ).to_json()

    sidecar_json_path = os.path.join(sidecar_dir, RESULT_FILE_NAME)
    claimed_repo_path = os.path.abspath(repo_path)
    probe_worktree_path = _build_probe_worktree_path(
        requested_coordinate,
        issue_number,
        run_identifier,
    )
    selected_route: RouteName = ROUTE_SETUP_FAILURE
    baseline: LibraryUpdateBaseline | None = None
    prepared_project: VersionedTestProjectPreparation | None = None
    probe_index_mapping: ProbeIndexMapping | None = None
    command_records: list[ProbeCommandResult] = []
    setup_error: str | None = None
    cleanup_status: dict[str, Any] = {"attempted": False, "removed": False, "error": None}

    try:
        _require_complete_repo(claimed_repo_path)
        _create_probe_worktree(claimed_repo_path, probe_worktree_path)
        _require_complete_repo(probe_worktree_path)
        baseline = resolve_latest_supported_baseline(probe_worktree_path, requested_coordinate)
        prepared_project = prepare_probe_test_project(probe_worktree_path, baseline)
        probe_index_mapping = _prepare_probe_index_mapping(
            probe_worktree_path,
            baseline,
            prepared_project,
        )

        compile_result = _run_gradle_probe_command(
            ["./gradlew", "compileTestJava", f"-Pcoordinates={requested_coordinate}"],
            probe_worktree_path,
            os.path.join(sidecar_dir, COMPILE_LOG_FILE_NAME),
        )
        command_records.append(compile_result)
        if compile_result.start_error is not None:
            selected_route = ROUTE_SETUP_FAILURE
            setup_error = compile_result.start_error
        elif compile_result.exit_code != 0:
            selected_route = ROUTE_JAVAC_FAILURE
        else:
            java_result = _run_gradle_probe_command(
                ["./gradlew", "javaTest", f"-Pcoordinates={requested_coordinate}"],
                probe_worktree_path,
                os.path.join(sidecar_dir, JAVA_TEST_LOG_FILE_NAME),
            )
            command_records.append(java_result)
            if java_result.start_error is not None:
                selected_route = ROUTE_SETUP_FAILURE
                setup_error = java_result.start_error
            elif java_result.exit_code != 0:
                selected_route = ROUTE_JAVA_RUN_FAILURE
            else:
                selected_route = ROUTE_COMPATIBLE
    except (
            CompatibilityProbeSetupError,
            LibraryUpdateBaselineSetupError,
            FileNotFoundError,
            OSError,
            subprocess.SubprocessError,
    ) as exc:
        selected_route = ROUTE_SETUP_FAILURE
        setup_error = str(exc)
    except SystemExit as exc:
        selected_route = ROUTE_SETUP_FAILURE
        setup_error = f"Complete reachability repo validation failed with exit code {exc.code}"
    finally:
        cleanup_status = _remove_probe_worktree(claimed_repo_path, probe_worktree_path)

    result = CompatibilityProbeResult(
        requested_coordinate=requested_coordinate,
        selected_route=selected_route,
        selected_baseline_coordinate=baseline.baseline_coordinate if baseline else None,
        baseline_index_path=baseline.index_path if baseline else None,
        metadata_version=baseline.metadata_version if baseline else None,
        test_version=baseline.test_version if baseline else None,
        baseline_selection_reason=baseline.selection_reason if baseline else None,
        prepared_test_project=_prepared_project_to_json(prepared_project),
        probe_index_mapping=(
            probe_index_mapping.to_json() if probe_index_mapping is not None else None
        ),
        probe_worktree_path=probe_worktree_path,
        cleanup_status=cleanup_status,
        command_records=tuple(command_records),
        setup_error=setup_error,
        sidecar_json_path=sidecar_json_path,
        log_paths=tuple(command_result.log_path for command_result in command_records),
    )
    _write_sidecar_json(sidecar_json_path, result.to_json())
    return result.to_json()


def _build_sidecar_dir(
        scratch_metrics_path: str,
        requested_coordinate: str,
        issue_number: int | None,
        run_identifier: str | None,
) -> str:
    run_id = _build_run_id(requested_coordinate, issue_number, run_identifier)
    return os.path.join(
        os.path.abspath(scratch_metrics_path),
        ROUTER_DIR_NAME,
        run_id,
    )


def _build_probe_worktree_path(
        requested_coordinate: str,
        issue_number: int | None,
        run_identifier: str | None,
) -> str:
    run_id = _build_run_id(requested_coordinate, issue_number, run_identifier)
    return os.path.join(
        get_repo_root(),
        "local_repositories",
        PROBE_WORKTREE_DIR_NAME,
        run_id,
    )


def _build_run_id(
        requested_coordinate: str,
        issue_number: int | None,
        run_identifier: str | None,
) -> str:
    if run_identifier:
        return sanitize_log_segment(run_identifier)
    safe_coordinate = sanitize_library_log_segment(requested_coordinate)
    if issue_number is not None:
        return sanitize_log_segment(f"issue-{issue_number}-{safe_coordinate}")
    return sanitize_log_segment(safe_coordinate)


def _require_complete_repo(repo_path: str) -> None:
    require_complete_reachability_repo(repo_path)


def _create_probe_worktree(claimed_repo_path: str, probe_worktree_path: str) -> None:
    os.makedirs(os.path.dirname(probe_worktree_path), exist_ok=True)
    _remove_stale_probe_path(claimed_repo_path, probe_worktree_path)
    result = subprocess.run(
        ["git", "worktree", "add", "--detach", probe_worktree_path, "HEAD"],
        cwd=claimed_repo_path,
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )
    if result.returncode != 0:
        raise CompatibilityProbeSetupError(
            "Failed to create compatibility probe worktree "
            f"{probe_worktree_path}: {result.stdout.strip()}"
        )


def _remove_stale_probe_path(claimed_repo_path: str, probe_worktree_path: str) -> None:
    if not os.path.exists(probe_worktree_path):
        return
    try:
        subprocess.run(
            ["git", "worktree", "remove", "--force", probe_worktree_path],
            cwd=claimed_repo_path,
            check=False,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
        )
    except OSError:
        pass
    if os.path.isdir(probe_worktree_path):
        shutil.rmtree(probe_worktree_path)
    elif os.path.exists(probe_worktree_path):
        os.remove(probe_worktree_path)


def _remove_probe_worktree(claimed_repo_path: str, probe_worktree_path: str) -> dict[str, Any]:
    if not probe_worktree_path or not os.path.exists(probe_worktree_path):
        return {"attempted": False, "removed": True, "error": None}
    git_exit_code: int | None = None
    command_error: str | None = None
    try:
        result = subprocess.run(
            ["git", "worktree", "remove", "--force", probe_worktree_path],
            cwd=claimed_repo_path,
            check=False,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
        )
        git_exit_code = result.returncode
        command_error = result.stdout.strip() if result.returncode != 0 else None
    except OSError as exc:
        command_error = str(exc)
    fallback_error: str | None = None
    if os.path.exists(probe_worktree_path):
        try:
            if os.path.isdir(probe_worktree_path):
                shutil.rmtree(probe_worktree_path)
            else:
                os.remove(probe_worktree_path)
        except OSError as exc:
            fallback_error = str(exc)

    removed = not os.path.exists(probe_worktree_path)
    error = fallback_error or (command_error if not removed else None)
    return {
        "attempted": True,
        "removed": removed,
        "git_exit_code": git_exit_code,
        "error": error,
    }


def _run_gradle_probe_command(
        command_args: list[str],
        cwd: str,
        log_path: str,
) -> ProbeCommandResult:
    start_time = time.monotonic()
    os.makedirs(os.path.dirname(log_path), exist_ok=True)
    start_error: str | None = None
    exit_code: int | None = None
    with open(log_path, "w", encoding="utf-8") as log_file:
        try:
            process = subprocess.run(
                command_args,
                cwd=cwd,
                env=gradle_command_environment(cwd),
                check=False,
                stdout=log_file,
                stderr=subprocess.STDOUT,
                text=True,
            )
            exit_code = process.returncode
        except OSError as exc:
            start_error = f"Failed to start probe command {' '.join(command_args)}: {exc}"
            log_file.write(f"ERROR: {start_error}\n")

    elapsed_seconds = round(time.monotonic() - start_time, 3)
    return ProbeCommandResult(
        command_args=tuple(command_args),
        cwd=cwd,
        exit_code=exit_code,
        elapsed_seconds=elapsed_seconds,
        log_path=log_path,
        start_error=start_error,
    )


def _parse_requested_coordinate(requested_coordinate: str) -> tuple[str, str, str]:
    parts = requested_coordinate.split(":")
    if len(parts) != 3 or any(not part for part in parts):
        raise CompatibilityProbeSetupError(
            f"Invalid requested coordinate {requested_coordinate!r}; expected group:artifact:version"
        )
    return parts[0], parts[1], parts[2]


def _prepare_probe_index_mapping(
        repo_path: str,
        baseline: LibraryUpdateBaseline,
        prepared_project: VersionedTestProjectPreparation,
) -> ProbeIndexMapping:
    """Append a transient probe-only index entry for the requested version."""
    group, artifact, requested_version = _parse_requested_coordinate(baseline.requested_coordinate)
    metadata_dir = os.path.join(
        repo_path,
        "metadata",
        group,
        artifact,
        baseline.metadata_version,
    )
    if not os.path.isdir(metadata_dir):
        raise CompatibilityProbeSetupError(
            "Selected baseline metadata directory is missing in the probe worktree: "
            f"{metadata_dir}"
        )
    if not os.path.isdir(prepared_project.target_test_dir):
        raise CompatibilityProbeSetupError(
            "Prepared probe test directory is missing in the probe worktree: "
            f"{prepared_project.target_test_dir}"
        )

    try:
        with open(baseline.index_path, "r", encoding="utf-8") as index_file:
            entries = json.load(index_file)
    except (json.JSONDecodeError, OSError) as exc:
        raise CompatibilityProbeSetupError(
            f"Failed to read probe metadata index {baseline.index_path}: {exc}"
        ) from exc
    if not isinstance(entries, list):
        raise CompatibilityProbeSetupError(
            f"Probe metadata index is not a JSON array: {baseline.index_path}"
        )

    baseline_entry = _find_baseline_index_entry(entries, baseline)
    probe_entry = dict(baseline_entry)
    probe_entry.pop("latest", None)
    probe_entry["metadata-version"] = baseline.metadata_version
    probe_entry["test-version"] = requested_version
    probe_entry["tested-versions"] = [requested_version]

    updated_entries = _entries_without_requested_version(entries, requested_version)
    updated_entries.append(probe_entry)
    try:
        with open(baseline.index_path, "w", encoding="utf-8") as index_file:
            json.dump(updated_entries, index_file, indent=2)
            index_file.write("\n")
    except OSError as exc:
        raise CompatibilityProbeSetupError(
            f"Failed to write probe metadata index {baseline.index_path}: {exc}"
        ) from exc

    return ProbeIndexMapping(
        index_path=baseline.index_path,
        metadata_version=baseline.metadata_version,
        test_version=requested_version,
        tested_versions=(requested_version,),
        inserted_entry_index=len(updated_entries) - 1,
        metadata_dir=metadata_dir,
        test_dir=prepared_project.target_test_dir,
    )


def _find_baseline_index_entry(
        entries: list[Any],
        baseline: LibraryUpdateBaseline,
) -> dict[str, Any]:
    for entry in entries:
        if not isinstance(entry, dict):
            continue
        metadata_version = entry.get("metadata-version")
        test_version = entry.get("test-version") or metadata_version
        if (
                metadata_version == baseline.metadata_version
                and test_version == baseline.test_version
        ):
            return entry
    raise CompatibilityProbeSetupError(
        "Selected baseline entry is missing from probe metadata index "
        f"{baseline.index_path}: metadata-version={baseline.metadata_version} "
        f"test-version={baseline.test_version}"
    )


def _entries_without_requested_version(
        entries: list[Any],
        requested_version: str,
) -> list[Any]:
    updated_entries: list[Any] = []
    for entry in entries:
        if not isinstance(entry, dict):
            updated_entries.append(entry)
            continue
        tested_versions = entry.get("tested-versions")
        if not isinstance(tested_versions, list) or requested_version not in tested_versions:
            updated_entries.append(entry)
            continue
        updated_entry = dict(entry)
        updated_entry["tested-versions"] = [
            version for version in tested_versions if version != requested_version
        ]
        updated_entries.append(updated_entry)
    return updated_entries


def _prepared_project_to_json(
        prepared_project: VersionedTestProjectPreparation | None,
) -> dict[str, Any] | None:
    if prepared_project is None:
        return None
    return {
        "source_test_dir": prepared_project.source_test_dir,
        "target_test_dir": prepared_project.target_test_dir,
        "copied": prepared_project.copied,
        "replacements": [
            {"old": old_value, "new": new_value}
            for old_value, new_value in prepared_project.replacements
        ],
        "gradle_properties_path": prepared_project.gradle_properties_path,
    }


def _write_sidecar_json(sidecar_json_path: str, result: dict[str, Any]) -> None:
    with open(sidecar_json_path, "w", encoding="utf-8") as sidecar_file:
        json.dump(result, sidecar_file, indent=2, sort_keys=True)
        sidecar_file.write("\n")
