# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Helpers for metadata/<group>/<artifact>/index.json files."""

import json
import os
import re
import sys
from dataclasses import dataclass
from typing import Any


NOT_FOR_NATIVE_IMAGE_FIELD = "not-for-native-image"
MATCH_TESTED_VERSION = "tested-version"
MATCH_METADATA_VERSION = "metadata-version"
MATCH_DEFAULT_FOR = "default-for"
MATCH_NEW_VERSION = "new-version"


@dataclass(frozen=True)
class LibraryUpdateTarget:
    """Resolved metadata/test target for a library-update-request coordinate."""
    requested_coordinate: str
    match_type: str
    matched_entry: dict[str, Any] | None
    resolved_metadata_version: str
    resolved_test_version: str
    metadata_dir: str
    test_dir: str


def coordinate_parts(coordinate: str) -> tuple[str, str, str | None]:
    """Parse group:artifact[:version] coordinates."""
    parts = coordinate.split(":")
    if len(parts) == 2:
        return parts[0], parts[1], None
    if len(parts) == 3:
        return parts[0], parts[1], parts[2]
    print(f"ERROR: Invalid coordinates format: {coordinate}", file=sys.stderr)
    raise SystemExit(1)


def index_path(repo_path: str, group: str, artifact: str) -> str:
    """Return the metadata index path for an artifact."""
    return os.path.join(repo_path, "metadata", group, artifact, "index.json")


def load_index_entries(repo_path: str, group: str, artifact: str) -> list[dict[str, Any]] | None:
    """Load an artifact index, returning None when it is absent."""
    path = index_path(repo_path, group, artifact)
    if not os.path.isfile(path):
        return None
    with open(path, "r", encoding="utf-8") as index_file:
        entries = json.load(index_file)
    if not isinstance(entries, list):
        print(f"ERROR: Metadata index is not a JSON array: {os.path.relpath(path, repo_path)}", file=sys.stderr)
        raise SystemExit(1)
    return entries


def _tested_versions(entry: dict[str, Any]) -> list[str]:
    tested_versions = entry.get("tested-versions") or []
    if not isinstance(tested_versions, list):
        return []
    return [str(version) for version in tested_versions]


def _default_for_matches(entry: dict[str, Any], library_version: str) -> bool:
    default_for = entry.get("default-for")
    if not isinstance(default_for, str) or not default_for:
        return False
    try:
        return re.fullmatch(default_for, library_version) is not None
    except re.error:
        print(
            f"ERROR: Invalid default-for regular expression {default_for!r}",
            file=sys.stderr,
        )
        raise SystemExit(1)


def _entry_test_version(entry: dict[str, Any], fallback_version: str) -> str:
    test_version = entry.get("test-version") or entry.get("metadata-version")
    if isinstance(test_version, str) and test_version:
        return test_version
    return fallback_version


def _entry_metadata_version(entry: dict[str, Any], fallback_version: str) -> str:
    metadata_version = entry.get("metadata-version")
    if isinstance(metadata_version, str) and metadata_version:
        return metadata_version
    return fallback_version


def _target_for_entry(
        repo_path: str,
        group: str,
        artifact: str,
        library_version: str,
        match_type: str,
        entry: dict[str, Any],
) -> LibraryUpdateTarget:
    metadata_version = _entry_metadata_version(entry, library_version)
    test_version = _entry_test_version(entry, metadata_version)
    return LibraryUpdateTarget(
        requested_coordinate=f"{group}:{artifact}:{library_version}",
        match_type=match_type,
        matched_entry=entry,
        resolved_metadata_version=metadata_version,
        resolved_test_version=test_version,
        metadata_dir=os.path.join(repo_path, "metadata", group, artifact, metadata_version),
        test_dir=os.path.join(repo_path, "tests", "src", group, artifact, test_version),
    )


def resolve_library_update_target(
        repo_path: str,
        group: str,
        artifact: str,
        library_version: str,
) -> LibraryUpdateTarget:
    """Resolve the target metadata/test dirs for a library-update-request version."""
    entries = load_index_entries(repo_path, group, artifact) or []
    supported_entries = [
        entry for entry in entries
        if isinstance(entry, dict) and not is_not_for_native_image_entry(entry)
    ]

    for entry in supported_entries:
        if library_version in _tested_versions(entry):
            return _target_for_entry(
                repo_path,
                group,
                artifact,
                library_version,
                MATCH_TESTED_VERSION,
                entry,
            )

    for entry in supported_entries:
        if entry.get("metadata-version") == library_version:
            return _target_for_entry(
                repo_path,
                group,
                artifact,
                library_version,
                MATCH_METADATA_VERSION,
                entry,
            )

    for entry in supported_entries:
        if _default_for_matches(entry, library_version):
            return _target_for_entry(
                repo_path,
                group,
                artifact,
                library_version,
                MATCH_DEFAULT_FOR,
                entry,
            )

    return LibraryUpdateTarget(
        requested_coordinate=f"{group}:{artifact}:{library_version}",
        match_type=MATCH_NEW_VERSION,
        matched_entry=None,
        resolved_metadata_version=library_version,
        resolved_test_version=library_version,
        metadata_dir=os.path.join(repo_path, "metadata", group, artifact, library_version),
        test_dir=os.path.join(repo_path, "tests", "src", group, artifact, library_version),
    )


def find_index_entry_for_version(
        repo_path: str,
        group: str,
        artifact: str,
        library_version: str,
) -> dict[str, Any] | None:
    """Return the index entry that declares support for a library version."""
    target = resolve_library_update_target(repo_path, group, artifact, library_version)
    if target.match_type == MATCH_NEW_VERSION:
        return None
    return target.matched_entry


def resolve_test_version(repo_path: str, group: str, artifact: str, library_version: str) -> str:
    """Resolve the tests/src version directory for a supported library version."""
    return resolve_library_update_target(repo_path, group, artifact, library_version).resolved_test_version


def resolve_metadata_version(repo_path: str, group: str, artifact: str, library_version: str) -> str:
    """Resolve the metadata version directory for a supported library version."""
    return resolve_library_update_target(repo_path, group, artifact, library_version).resolved_metadata_version


def resolve_test_dir(repo_path: str, group: str, artifact: str, library_version: str) -> str:
    """Resolve the tests/src directory path for a supported library version."""
    test_version = resolve_test_version(repo_path, group, artifact, library_version)
    return os.path.join(repo_path, "tests", "src", group, artifact, test_version)


def is_not_for_native_image_entry(entry: dict[str, Any]) -> bool:
    """Return true when an index entry is the marker entry."""
    return entry.get(NOT_FOR_NATIVE_IMAGE_FIELD) is True


def is_not_for_native_image(repo_path: str, group: str, artifact: str) -> bool:
    """Return true when the artifact is marked as not applicable to Native Image."""
    entries = load_index_entries(repo_path, group, artifact)
    if not entries:
        return False
    return any(is_not_for_native_image_entry(entry) for entry in entries)


def get_not_for_native_image_marker(repo_path: str, group: str, artifact: str) -> dict[str, Any] | None:
    """Return the marker entry when present."""
    entries = load_index_entries(repo_path, group, artifact)
    if not entries:
        return None
    for entry in entries:
        if is_not_for_native_image_entry(entry):
            return entry
    return None


def write_not_for_native_image_marker(
        repo_path: str,
        group: str,
        artifact: str,
        reason: str,
        replacement: str | None = None,
) -> str:
    """Write a marker-only index.json for an artifact."""
    path = index_path(repo_path, group, artifact)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    marker: dict[str, Any] = {
        NOT_FOR_NATIVE_IMAGE_FIELD: True,
        "reason": reason,
    }
    if replacement:
        marker["replacement"] = replacement
    with open(path, "w", encoding="utf-8") as index_file:
        json.dump([marker], index_file, indent=2)
        index_file.write("\n")
    return path
