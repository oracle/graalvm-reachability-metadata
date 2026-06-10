# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Compatibility router for missing-version library-update requests."""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import tempfile
from dataclasses import dataclass
from typing import Any

from ai_workflows.drivers.improve_library_coverage import clone_library_update_support
from utility_scripts.gradle_environment import gradle_command_environment
from utility_scripts.library_stats import stats_artifact_dir
from utility_scripts.metadata_index import (
    MATCH_NEW_VERSION,
    coordinate_parts,
    is_not_for_native_image_entry,
    load_index_entries,
    resolve_library_update_target,
)
from utility_scripts.stage_logger import log_stage
from utility_scripts.task_logs import build_timestamped_task_log_path, display_log_path

LIBRARY_UPDATE_ROUTE_FILENAME = ".library_update_route.json"
ROUTE_IMPROVE_COVERAGE = "improve_library_coverage"
ROUTE_FIX_JAVAC = "fix_javac_fail"
ROUTE_FIX_JAVA_RUN = "fix_java_run_fail"


@dataclass(frozen=True)
class LibraryUpdateRoute:
    """Persisted route selected for a `library-update-request` issue."""

    selected_driver: str
    requested_coordinates: str
    baseline_coordinates: str | None
    new_version: str
    reason: str
    match_type: str
    compile_exit_code: int | None = None
    java_test_exit_code: int | None = None
    compile_log_path: str | None = None
    java_test_log_path: str | None = None

    def to_json(self) -> dict[str, Any]:
        return {
            "selected_driver": self.selected_driver,
            "requested_coordinates": self.requested_coordinates,
            "baseline_coordinates": self.baseline_coordinates,
            "new_version": self.new_version,
            "reason": self.reason,
            "match_type": self.match_type,
            "compile_exit_code": self.compile_exit_code,
            "java_test_exit_code": self.java_test_exit_code,
            "compile_log_path": self.compile_log_path,
            "java_test_log_path": self.java_test_log_path,
        }

    @classmethod
    def from_json(cls, payload: dict[str, Any]) -> "LibraryUpdateRoute":
        selected_driver = _required_str(payload, "selected_driver")
        requested_coordinates = _required_str(payload, "requested_coordinates")
        new_version = _required_str(payload, "new_version")
        reason = _required_str(payload, "reason")
        match_type = _required_str(payload, "match_type")
        baseline_coordinates = _optional_str(payload, "baseline_coordinates")
        return cls(
            selected_driver=selected_driver,
            requested_coordinates=requested_coordinates,
            baseline_coordinates=baseline_coordinates,
            new_version=new_version,
            reason=reason,
            match_type=match_type,
            compile_exit_code=_optional_int(payload, "compile_exit_code"),
            java_test_exit_code=_optional_int(payload, "java_test_exit_code"),
            compile_log_path=_optional_str(payload, "compile_log_path"),
            java_test_log_path=_optional_str(payload, "java_test_log_path"),
        )


@dataclass(frozen=True)
class _ProbeCommandResult:
    exit_code: int
    log_path: str


@dataclass(frozen=True)
class _SnapshotEntry:
    path: str
    snapshot_path: str
    existed: bool
    was_dir: bool


class _PathSnapshot:
    """Restore source-control paths touched by the compatibility probe."""

    def __init__(self, paths: list[str]):
        self._root = tempfile.mkdtemp(prefix="forge-library-update-probe-")
        self._entries: list[_SnapshotEntry] = []
        for index, path in enumerate(paths):
            snapshot_path = os.path.join(self._root, str(index))
            existed = os.path.exists(path)
            was_dir = os.path.isdir(path)
            if existed:
                if was_dir:
                    shutil.copytree(path, snapshot_path)
                else:
                    os.makedirs(os.path.dirname(snapshot_path), exist_ok=True)
                    shutil.copy2(path, snapshot_path)
            self._entries.append(_SnapshotEntry(path, snapshot_path, existed, was_dir))

    def restore(self) -> None:
        try:
            for entry in self._entries:
                if os.path.exists(entry.path):
                    if os.path.isdir(entry.path):
                        shutil.rmtree(entry.path)
                    else:
                        os.remove(entry.path)
                if not entry.existed:
                    continue
                os.makedirs(os.path.dirname(entry.path), exist_ok=True)
                if entry.was_dir:
                    shutil.copytree(entry.snapshot_path, entry.path)
                else:
                    shutil.copy2(entry.snapshot_path, entry.path)
        finally:
            shutil.rmtree(self._root, ignore_errors=True)


def route_sidecar_path(metrics_repo_root: str) -> str:
    """Return the dispatcher-side route sidecar path."""
    return os.path.join(metrics_repo_root, LIBRARY_UPDATE_ROUTE_FILENAME)


def write_library_update_route(metrics_repo_root: str, route: LibraryUpdateRoute) -> str:
    """Persist the selected route for publication handoff and diagnostics."""
    os.makedirs(metrics_repo_root, exist_ok=True)
    path = route_sidecar_path(metrics_repo_root)
    with open(path, "w", encoding="utf-8") as route_file:
        json.dump(route.to_json(), route_file, indent=2)
        route_file.write("\n")
    return path


def load_library_update_route(metrics_repo_root: str) -> LibraryUpdateRoute | None:
    """Load a selected library-update route, if the dispatcher wrote one."""
    path = route_sidecar_path(metrics_repo_root)
    if not os.path.isfile(path):
        return None
    with open(path, "r", encoding="utf-8") as route_file:
        payload = json.load(route_file)
    if not isinstance(payload, dict):
        raise ValueError(f"ERROR: Library-update route sidecar is not a JSON object: {path}")
    return LibraryUpdateRoute.from_json(payload)


def select_library_update_route(repo_path: str, metrics_repo_root: str, coordinates: str) -> LibraryUpdateRoute:
    """Select and persist the driver for one `library-update-request`.

    Existing requested-version suites keep the normal coverage path. Missing
    requested-version suites are probed from the latest supported test suite,
    then routed to the driver that owns the selected repair or coverage path.
    §WF-forge-workflow-drivers.2
    """
    group, artifact, requested_version = _require_versioned_coordinate(coordinates)
    target = resolve_library_update_target(repo_path, group, artifact, requested_version)
    if target.match_type != MATCH_NEW_VERSION or os.path.isdir(target.test_dir):
        route = LibraryUpdateRoute(
            selected_driver=ROUTE_IMPROVE_COVERAGE,
            requested_coordinates=coordinates,
            baseline_coordinates=None,
            new_version=requested_version,
            reason=f"requested version is already covered by {target.match_type} target",
            match_type=target.match_type,
        )
        write_library_update_route(metrics_repo_root, route)
        return route

    latest_entry = _latest_supported_entry(repo_path, group, artifact, coordinates)
    baseline_test_version = _entry_test_version(latest_entry)
    baseline_coordinates = f"{group}:{artifact}:{baseline_test_version}"
    log_stage(
        "library-update-router",
        (
            f"Missing requested-version test suite for {coordinates}; probing latest "
            f"supported test version {baseline_coordinates}"
        ),
    )
    compile_result, java_test_result = _probe_latest_suite(
        repo_path=repo_path,
        group=group,
        artifact=artifact,
        requested_version=requested_version,
        latest_entry=latest_entry,
    )
    if compile_result.exit_code != 0:
        route = LibraryUpdateRoute(
            selected_driver=ROUTE_FIX_JAVAC,
            requested_coordinates=coordinates,
            baseline_coordinates=baseline_coordinates,
            new_version=requested_version,
            reason="compatibility probe failed during Java compilation",
            match_type=target.match_type,
            compile_exit_code=compile_result.exit_code,
            compile_log_path=compile_result.log_path,
        )
    elif java_test_result is not None and java_test_result.exit_code != 0:
        route = LibraryUpdateRoute(
            selected_driver=ROUTE_FIX_JAVA_RUN,
            requested_coordinates=coordinates,
            baseline_coordinates=baseline_coordinates,
            new_version=requested_version,
            reason="compatibility probe compiled but failed JVM tests",
            match_type=target.match_type,
            compile_exit_code=compile_result.exit_code,
            java_test_exit_code=java_test_result.exit_code,
            compile_log_path=compile_result.log_path,
            java_test_log_path=java_test_result.log_path,
        )
    else:
        route = LibraryUpdateRoute(
            selected_driver=ROUTE_IMPROVE_COVERAGE,
            requested_coordinates=coordinates,
            baseline_coordinates=baseline_coordinates,
            new_version=requested_version,
            reason="compatibility probe passed Java compilation and JVM tests",
            match_type=target.match_type,
            compile_exit_code=compile_result.exit_code,
            java_test_exit_code=None if java_test_result is None else java_test_result.exit_code,
            compile_log_path=compile_result.log_path,
            java_test_log_path=None if java_test_result is None else java_test_result.log_path,
        )
    write_library_update_route(metrics_repo_root, route)
    log_stage("library-update-router", f"Selected {route.selected_driver} for {coordinates}: {route.reason}")
    return route


def _probe_latest_suite(
        repo_path: str,
        group: str,
        artifact: str,
        requested_version: str,
        latest_entry: dict[str, Any],
) -> tuple[_ProbeCommandResult, _ProbeCommandResult | None]:
    requested_coordinates = f"{group}:{artifact}:{requested_version}"
    snapshot = _PathSnapshot([
        os.path.join(repo_path, "metadata", group, artifact, "index.json"),
        os.path.join(repo_path, "metadata", group, artifact, requested_version),
        os.path.join(repo_path, "tests", "src", group, artifact, requested_version),
        os.path.join(stats_artifact_dir(repo_path, group, artifact), requested_version),
    ])
    try:
        clone_library_update_support(repo_path, group, artifact, requested_version, latest_entry)
        compile_result = _run_probe_gradle_task(repo_path, requested_coordinates, "compileTestJava")
        if compile_result.exit_code != 0:
            return compile_result, None
        return compile_result, _run_probe_gradle_task(repo_path, requested_coordinates, "javaTest")
    finally:
        snapshot.restore()


def _run_probe_gradle_task(repo_path: str, coordinates: str, task: str) -> _ProbeCommandResult:
    command = ["./gradlew", "clean", task, f"-Pcoordinates={coordinates}"]
    log_path = build_timestamped_task_log_path("library-update-router", coordinates, task)
    log_stage(
        "library-update-router",
        f"$ {' '.join(command)}  (log: {display_log_path(log_path)})",
    )
    with open(log_path, "w", encoding="utf-8") as log_file:
        result = subprocess.run(
            command,
            cwd=repo_path,
            env=gradle_command_environment(repo_path),
            stdout=log_file,
            stderr=subprocess.STDOUT,
            text=True,
            check=False,
        )
    return _ProbeCommandResult(exit_code=result.returncode, log_path=log_path)


def _latest_supported_entry(repo_path: str, group: str, artifact: str, coordinates: str) -> dict[str, Any]:
    entries = load_index_entries(repo_path, group, artifact)
    if entries is None:
        _raise_unusable_latest_supported_suite(repo_path, group, artifact, coordinates, [], [])
    assert entries is not None
    latest_entries = [
        entry
        for entry in entries
        if isinstance(entry, dict)
        and entry.get("latest") is True
    ]
    usable_latest_entries = [
        entry
        for entry in latest_entries
        if _is_usable_supported_entry(repo_path, group, artifact, entry)
    ]
    if len(usable_latest_entries) != 1:
        _raise_unusable_latest_supported_suite(
            repo_path,
            group,
            artifact,
            coordinates,
            latest_entries,
            usable_latest_entries,
        )
    return usable_latest_entries[0]


def _raise_unusable_latest_supported_suite(
        repo_path: str,
        group: str,
        artifact: str,
        coordinates: str,
        latest_entries: list[dict[str, Any]],
        usable_latest_entries: list[dict[str, Any]],
) -> None:
    index_path = os.path.join(repo_path, "metadata", group, artifact, "index.json")
    rel_index_path = os.path.relpath(index_path, repo_path)
    raise RuntimeError(
        "ERROR: Cannot route missing-version library-update request for "
        f"{coordinates}: expected exactly one usable latest supported suite in "
        f"{rel_index_path}, found {len(usable_latest_entries)} usable latest "
        f"entries out of {len(latest_entries)} latest=true entries. A usable "
        "latest entry must not be not-for-native-image, must have a non-empty "
        "`metadata-version`, and must have both "
        f"`metadata/{group}/{artifact}/<metadata-version>` and "
        f"`tests/src/{group}/{artifact}/<test-version-or-metadata-version>`. "
        "Fix the index or restore the latest test suite before rerunning."
    )


def _is_usable_supported_entry(repo_path: str, group: str, artifact: str, entry: dict[str, Any]) -> bool:
    if is_not_for_native_image_entry(entry):
        return False
    metadata_version = entry.get("metadata-version")
    if not isinstance(metadata_version, str) or not metadata_version:
        return False
    test_version = _entry_test_version(entry)
    return (
        os.path.isdir(os.path.join(repo_path, "metadata", group, artifact, metadata_version))
        and os.path.isdir(os.path.join(repo_path, "tests", "src", group, artifact, test_version))
    )


def _entry_test_version(entry: dict[str, Any]) -> str:
    test_version = entry.get("test-version") or entry.get("metadata-version")
    if not isinstance(test_version, str) or not test_version:
        raise RuntimeError(f"Index entry does not have a usable test version: {entry}")
    return test_version


def _require_versioned_coordinate(coordinates: str) -> tuple[str, str, str]:
    group, artifact, version = coordinate_parts(coordinates)
    if version is None:
        raise ValueError(f"Expected versioned coordinates for library-update router: {coordinates}")
    return group, artifact, version


def _required_str(payload: dict[str, Any], key: str) -> str:
    value = payload.get(key)
    if not isinstance(value, str) or not value:
        raise ValueError(f"ERROR: Library-update route field `{key}` must be a non-empty string.")
    return value


def _optional_str(payload: dict[str, Any], key: str) -> str | None:
    value = payload.get(key)
    if value is None:
        return None
    if not isinstance(value, str):
        raise ValueError(f"ERROR: Library-update route field `{key}` must be a string when present.")
    return value


def _optional_int(payload: dict[str, Any], key: str) -> int | None:
    value = payload.get(key)
    if value is None:
        return None
    if not isinstance(value, int):
        raise ValueError(f"ERROR: Library-update route field `{key}` must be an integer when present.")
    return value
