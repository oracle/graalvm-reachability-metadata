# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Deterministic library-update target preparation.

Resolves the requested library-update target and, when needed, clones or
scaffolds its metadata, tests, stats, and index entries so the coverage driver
starts from a version-specific workspace (§AR-forge-drivers).
"""

import copy
import json
import os
import re
import shutil
from typing import Any

from utility_scripts.gradle_environment import gradle_command_environment
from utility_scripts.library_stats import stats_artifact_dir
from utility_scripts.logged_command import run_logged_command
from utility_scripts.metadata_index import (
    MATCH_NEW_VERSION,
    LibraryUpdateTarget,
    is_newer_than_latest_metadata_version,
    load_index_entries,
    resolve_library_update_target,
    resolve_version_backfill_baseline,
)
from utility_scripts.stage_logger import log_detail
from utility_scripts.worktree_reset import reset_worktree_preserving_paths

LIBRARY_UPDATE_TARGET_FILENAME = ".library_update_target.json"


def _version_numbers(version: str) -> tuple[int, ...]:
    """Return numeric version parts for compatibility ranking."""
    return tuple(int(part) for part in re.findall(r"\d+", version))


def _padded_version_numbers(version: str, length: int = 4) -> tuple[int, ...]:
    numbers = _version_numbers(version)
    return numbers[:length] + (0,) * max(length - len(numbers), 0)


def _version_is_at_or_after(version: str, requested_version: str) -> bool:
    """Return true when a tested version should move off the older split baseline."""
    if version == requested_version:
        return True
    if version.startswith(f"{requested_version}-"):
        return False
    version_numbers = _version_numbers(version)
    requested_numbers = _version_numbers(requested_version)
    if not version_numbers or not requested_numbers:
        return False
    return _padded_version_numbers(version) >= _padded_version_numbers(requested_version)


def _entry_test_version(entry: dict[str, Any]) -> str:
    return str(entry.get("test-version") or entry.get("metadata-version"))


def _copytree_replace(destination: str, source: str) -> None:
    if os.path.abspath(destination) == os.path.abspath(source):
        return
    if os.path.exists(destination):
        shutil.rmtree(destination)
    shutil.copytree(source, destination)


def _safe_version_pattern(version: str) -> re.Pattern:
    """Match a standalone version token without touching longer version-like strings."""
    return re.compile(rf"(?<![A-Za-z0-9_.-]){re.escape(version)}(?![A-Za-z0-9_.-])")


def _allows_bare_version_rewrite(path: str) -> bool:
    """Return whether bare version tokens may be rewritten in this cloned file."""
    file_name = os.path.basename(path)
    if file_name == "gradle.properties":
        return True
    return os.path.splitext(file_name)[1] in {
        ".groovy",
        ".java",
        ".json",
        ".kt",
        ".properties",
        ".xml",
        ".yaml",
        ".yml",
    }


def _rewrite_text_files(root_dir: str, replacements: list[tuple[str, str]], allow_bare_versions: bool = False) -> None:
    if not os.path.isdir(root_dir):
        return
    literal_replacements = [
        (old_value, new_value)
        for old_value, new_value in replacements
        if old_value and ":" in old_value
    ]
    version_replacements = [
        (_safe_version_pattern(old_value), new_value)
        for old_value, new_value in replacements
        if old_value and ":" not in old_value
    ]
    for current_root, _, file_names in os.walk(root_dir):
        for file_name in file_names:
            path = os.path.join(current_root, file_name)
            try:
                with open(path, "r", encoding="utf-8") as file:
                    original = file.read()
            except UnicodeDecodeError:
                continue
            updated = original
            for old_value, new_value in literal_replacements:
                updated = updated.replace(old_value, new_value)
            if allow_bare_versions and _allows_bare_version_rewrite(path):
                for pattern, new_value in version_replacements:
                    updated = pattern.sub(new_value, updated)
            if updated != original:
                with open(path, "w", encoding="utf-8") as file:
                    file.write(updated)


def _rewrite_cloned_gradle_properties(
        test_dir: str,
        group: str,
        artifact: str,
        requested_version: str,
) -> None:
    """Update scaffold-owned Gradle properties after cloning a test project."""
    gradle_properties_path = os.path.join(test_dir, "gradle.properties")
    if not os.path.isfile(gradle_properties_path):
        return

    expected_values = {
        "library.coordinates": f"{group}:{artifact}:{requested_version}",
        "library.version": requested_version,
        "metadata.dir": f"{group}/{artifact}/{requested_version}/",
    }
    with open(gradle_properties_path, "r", encoding="utf-8") as properties_file:
        lines = properties_file.readlines()

    updated_lines: list[str] = []
    for line in lines:
        line_ending = "\n" if line.endswith("\n") else ""
        content = line[:-1] if line_ending else line
        stripped = content.lstrip()
        if not stripped or stripped.startswith("#"):
            updated_lines.append(line)
            continue
        key_separator = "=" if "=" in stripped else ":" if ":" in stripped else None
        if key_separator is None:
            updated_lines.append(line)
            continue
        key = stripped.split(key_separator, 1)[0].strip()
        if key not in expected_values:
            updated_lines.append(line)
            continue
        indent = content[:len(content) - len(stripped)]
        updated_lines.append(f"{indent}{key} = {expected_values[key]}{line_ending}")

    updated_content = "".join(updated_lines)
    original_content = "".join(lines)
    if updated_content != original_content:
        with open(gradle_properties_path, "w", encoding="utf-8") as properties_file:
            properties_file.write(updated_content)


def _replace_version_in_value(value: Any, old_versions: set[str], new_version: str) -> Any:
    if isinstance(value, str):
        updated = value
        for old_version in sorted(old_versions, key=len, reverse=True):
            updated = updated.replace(old_version, new_version)
        return updated
    if isinstance(value, list):
        return [_replace_version_in_value(item, old_versions, new_version) for item in value]
    if isinstance(value, dict):
        return {
            key: _replace_version_in_value(item, old_versions, new_version)
            for key, item in value.items()
        }
    return value


def _new_index_entry_from_baseline(
        baseline_entry: dict[str, Any],
        requested_version: str,
        tested_versions: list[str] | None = None,
        mark_latest: bool = True,
) -> dict[str, Any]:
    old_versions = {
        str(value)
        for value in [
            baseline_entry.get("metadata-version"),
            baseline_entry.get("test-version"),
            *list(baseline_entry.get("tested-versions") or []),
        ]
        if value
    }
    new_entry = copy.deepcopy(baseline_entry)
    new_entry = _replace_version_in_value(new_entry, old_versions, requested_version)
    if mark_latest:
        new_entry["latest"] = True
    else:
        new_entry.pop("latest", None)
    new_entry["metadata-version"] = requested_version
    new_entry.pop("test-version", None)
    new_entry.pop("default-for", None)
    new_entry.pop("skipped-versions", None)
    new_entry["tested-versions"] = tested_versions or [requested_version]
    return new_entry


def _tested_versions_for_split_entry(
        baseline_entry: dict[str, Any],
        requested_version: str,
) -> list[str]:
    """Return tested versions that should move to the newly split metadata entry."""
    tested_versions = baseline_entry.get("tested-versions")
    moved_versions: list[str] = []
    if isinstance(tested_versions, list):
        moved_versions = [
            str(version)
            for version in tested_versions
            if _version_is_at_or_after(str(version), requested_version)
        ]
    return [requested_version] + [
        version for version in moved_versions
        if version != requested_version
    ]


def _write_index_entries(repo_path: str, group: str, artifact: str, entries: list[dict[str, Any]]) -> None:
    index_path = os.path.join(repo_path, "metadata", group, artifact, "index.json")
    os.makedirs(os.path.dirname(index_path), exist_ok=True)
    with open(index_path, "w", encoding="utf-8") as index_file:
        json.dump(entries, index_file, indent=2)
        index_file.write("\n")


def _run_scaffold(repo_path: str, coordinate: str) -> None:
    """Run scaffold quietly with a durable log and clear failure.

    §FS-forge-run-output-legibility §FS-durable-generation-logs
    """
    command = ["./gradlew", "scaffold", "--coordinates", coordinate]
    result = run_logged_command(
        command,
        cwd=repo_path,
        task_type="library-update-target",
        subject=coordinate,
        action="scaffold",
        env=gradle_command_environment(repo_path),
        stage="library-update-target",
    )
    if result.returncode != 0:
        raise RuntimeError(
            "Failed to scaffold library-update target "
            f"{coordinate}; command exited with status {result.returncode}: {' '.join(command)}"
        )


def clone_library_update_support(
        repo_path: str,
        group: str,
        artifact: str,
        requested_version: str,
        baseline_entry: dict[str, Any],
) -> None:
    """Clone baseline metadata/tests/stats support for a requested new version."""
    baseline_metadata_version = str(baseline_entry["metadata-version"])
    baseline_test_version = _entry_test_version(baseline_entry)
    target_metadata_dir = os.path.join(repo_path, "metadata", group, artifact, requested_version)
    target_test_dir = os.path.join(repo_path, "tests", "src", group, artifact, requested_version)
    baseline_metadata_dir = os.path.join(repo_path, "metadata", group, artifact, baseline_metadata_version)
    baseline_test_dir = os.path.join(repo_path, "tests", "src", group, artifact, baseline_test_version)
    _copytree_replace(target_metadata_dir, baseline_metadata_dir)
    _copytree_replace(target_test_dir, baseline_test_dir)

    baseline_stats_dir = os.path.join(stats_artifact_dir(repo_path, group, artifact), baseline_metadata_version)
    target_stats_dir = os.path.join(stats_artifact_dir(repo_path, group, artifact), requested_version)
    if os.path.isdir(baseline_stats_dir):
        _copytree_replace(target_stats_dir, baseline_stats_dir)

    replacements = [
        (f"{group}:{artifact}:{baseline_metadata_version}", f"{group}:{artifact}:{requested_version}"),
        (f"{group}:{artifact}:{baseline_test_version}", f"{group}:{artifact}:{requested_version}"),
        (baseline_metadata_version, requested_version),
        (baseline_test_version, requested_version),
    ]
    _rewrite_text_files(target_metadata_dir, replacements, allow_bare_versions=True)
    _rewrite_text_files(target_test_dir, replacements)
    _rewrite_cloned_gradle_properties(target_test_dir, group, artifact, requested_version)
    _rewrite_text_files(target_stats_dir, replacements, allow_bare_versions=True)

    entries = load_index_entries(repo_path, group, artifact) or []
    moved_tested_versions = _tested_versions_for_split_entry(baseline_entry, requested_version)
    new_entry_tested_versions = moved_tested_versions
    if baseline_metadata_version == requested_version:
        tested_versions = baseline_entry.get("tested-versions")
        if isinstance(tested_versions, list):
            new_entry_tested_versions = [str(version) for version in tested_versions]
    mark_new_entry_latest = (
        baseline_metadata_version == requested_version
        and baseline_entry.get("latest") is True
    ) or is_newer_than_latest_metadata_version(repo_path, group, artifact, requested_version)
    updated_entries: list[dict[str, Any]] = []
    new_entry = _new_index_entry_from_baseline(
        baseline_entry,
        requested_version,
        new_entry_tested_versions,
        mark_latest=mark_new_entry_latest,
    )
    for entry in entries:
        if not isinstance(entry, dict):
            continue
        entry_copy = copy.deepcopy(entry)
        if mark_new_entry_latest and entry_copy.get("latest") is True:
            entry_copy.pop("latest", None)
        if entry_copy.get("metadata-version") == baseline_metadata_version:
            if baseline_metadata_version == requested_version:
                updated_entries.append(new_entry)
                continue
            tested_versions = entry_copy.get("tested-versions")
            if isinstance(tested_versions, list):
                entry_copy["tested-versions"] = [
                    version for version in tested_versions
                    if not _version_is_at_or_after(str(version), requested_version)
                ]
        updated_entries.append(entry_copy)
    if baseline_metadata_version != requested_version:
        updated_entries.append(new_entry)
    _write_index_entries(repo_path, group, artifact, updated_entries)


def prepare_library_update_target(
        repo_path: str,
        group: str,
        artifact: str,
        requested_version: str,
        issue_requested_metadata_context: str = "",
) -> LibraryUpdateTarget:
    """Ensure the requested library-update target exists and return its resolved paths."""
    target = resolve_library_update_target(repo_path, group, artifact, requested_version)
    must_split_shared_target = (
        target.match_type != MATCH_NEW_VERSION
        and (
            target.resolved_metadata_version != requested_version
            or target.resolved_test_version != requested_version
        )
    )
    if must_split_shared_target and target.matched_entry is not None:
        clone_library_update_support(repo_path, group, artifact, requested_version, target.matched_entry)
        log_detail(
            "library-update-target",
            "Split {group}:{artifact}:{requested_version} from shared metadata-version {metadata_version} "
            "for version-specific library-update coverage".format(
                group=group,
                artifact=artifact,
                requested_version=requested_version,
                metadata_version=target.resolved_metadata_version,
            ),
        )
        return LibraryUpdateTarget(
            requested_coordinate=f"{group}:{artifact}:{requested_version}",
            match_type=MATCH_NEW_VERSION,
            matched_entry=target.matched_entry,
            resolved_metadata_version=requested_version,
            resolved_test_version=requested_version,
            metadata_dir=os.path.join(repo_path, "metadata", group, artifact, requested_version),
            test_dir=os.path.join(repo_path, "tests", "src", group, artifact, requested_version),
        )

    if target.match_type != MATCH_NEW_VERSION:
        return target

    baseline = resolve_version_backfill_baseline(repo_path, group, artifact, requested_version)
    if baseline is not None:
        clone_library_update_support(repo_path, group, artifact, requested_version, baseline.entry)
        log_detail(
            "library-update-target",
            "Cloned support for {group}:{artifact}:{requested_version} from {baseline_coordinates}; "
            "reason: {reason}".format(
                group=group,
                artifact=artifact,
                requested_version=requested_version,
                baseline_coordinates=f"{group}:{artifact}:{baseline.test_version}",
                reason=baseline.reason,
            ),
        )
    else:
        coordinate = f"{group}:{artifact}:{requested_version}"
        log_detail("library-update-target", f"No compatible support found; scaffolding {coordinate}")
        _run_scaffold(repo_path, coordinate)

    return target


def _target_metrics(target: LibraryUpdateTarget) -> dict[str, Any]:
    matched_test_version = None
    if target.matched_entry is not None:
        matched_test_version = (
            target.matched_entry.get("test-version")
            or target.matched_entry.get("metadata-version")
        )
    return {
        "requested_coordinate": target.requested_coordinate,
        "match_type": target.match_type,
        "matched_metadata_version": (
            target.matched_entry.get("metadata-version")
            if target.matched_entry is not None else None
        ),
        "matched_test_version": matched_test_version,
        "resolved_metadata_version": target.resolved_metadata_version,
        "resolved_test_version": target.resolved_test_version,
    }


def write_library_update_target_sidecar(metrics_repo_root: str | None, target: LibraryUpdateTarget) -> None:
    """Write PR-only library-update target details outside validated run metrics."""
    if not metrics_repo_root:
        return
    sidecar_path = os.path.join(metrics_repo_root, LIBRARY_UPDATE_TARGET_FILENAME)
    with open(sidecar_path, "w", encoding="utf-8") as sidecar_file:
        json.dump(_target_metrics(target), sidecar_file, indent=2)
        sidecar_file.write("\n")


def reset_failed_library_update_worktree(
        repo_path: str,
        checkpoint_commit: str,
        target: LibraryUpdateTarget,
) -> str:
    """Reset to checkpoint while preserving generated target files for follow-up branches."""
    group, artifact, _version = target.requested_coordinate.split(":")
    paths = [
        target.test_dir,
        os.path.join(repo_path, "metadata", group, artifact, "index.json"),
        target.metadata_dir,
        stats_artifact_dir(repo_path, group, artifact),
    ]
    return reset_worktree_preserving_paths(repo_path, checkpoint_commit, paths)
