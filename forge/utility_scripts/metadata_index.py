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
from typing import Any, TypeAlias


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


_RELEASE_QUALIFIER = "release"
_VERSION_PATTERN = re.compile(
    r"^(\d+(?:\.\d+)*)(?:\.(?:Final|RELEASE))?"
    r"(?:[-.](alpha\d*|beta\d*|rc\d*|cr\d*|m\d+|ea\d*|b\d+|\d+|preview)(?:[-.](.*))?)?$",
    re.IGNORECASE,
)
_QUALIFIER_PATTERN = re.compile(r"^(alpha|beta|rc|cr|m|ea|b|preview)(\d*)$", re.IGNORECASE)
ParsedMetadataVersion: TypeAlias = tuple[tuple[int, ...], tuple[int, int]]
_QUALIFIER_RANK = {
    "alpha": 10,
    "beta": 20,
    "m": 30,
    "ea": 35,
    "preview": 40,
    "rc": 50,
    "cr": 50,
    "b": 60,
    "number": 70,
    _RELEASE_QUALIFIER: 100,
}


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


def latest_metadata_version(repo_path: str, group: str, artifact: str) -> str | None:
    """Return the metadata-version of the single latest entry, if one exists."""
    entries = load_index_entries(repo_path, group, artifact)
    if not entries:
        return None

    latest_entries = [
        entry for entry in entries
        if isinstance(entry, dict) and entry.get("latest") is True
    ]
    if len(latest_entries) != 1:
        return None

    metadata_version = latest_entries[0].get("metadata-version")
    if isinstance(metadata_version, str) and metadata_version:
        return metadata_version
    return None


def is_newer_parseable_metadata_version(candidate_version: str, current_version: str) -> bool:
    """Return true when both versions are parseable and candidate is newer."""
    comparison = compare_parseable_metadata_versions(candidate_version, current_version)
    return comparison is not None and comparison > 0


def is_parseable_metadata_version(version: str) -> bool:
    """Return true when the version can be compared by metadata-version ordering."""
    return _parse_metadata_version(version) is not None


def compare_parseable_metadata_versions(first_version: str, second_version: str) -> int | None:
    """Compare parseable metadata versions, returning None when either is unsupported."""
    return _compare_parseable_metadata_versions(first_version, second_version)


def is_newer_than_latest_metadata_version(
        repo_path: str,
        group: str,
        artifact: str,
        candidate_version: str,
) -> bool:
    """Return true when candidate is parseably newer than the current latest entry."""
    current_latest = latest_metadata_version(repo_path, group, artifact)
    if current_latest is None:
        return False
    return is_newer_parseable_metadata_version(candidate_version, current_latest)


def _compare_parseable_metadata_versions(first_version: str, second_version: str) -> int | None:
    first = _parse_metadata_version(first_version)
    second = _parse_metadata_version(second_version)
    if first is None or second is None:
        return None

    first_base, first_qualifier = first
    second_base, second_qualifier = second
    base_comparison = _compare_version_numbers(first_base, second_base)
    if base_comparison != 0:
        return base_comparison

    rank_comparison = first_qualifier[0] - second_qualifier[0]
    if rank_comparison != 0:
        return rank_comparison
    return first_qualifier[1] - second_qualifier[1]


def _parse_metadata_version(version: str) -> ParsedMetadataVersion | None:
    match = _VERSION_PATTERN.match(version)
    if not match:
        return None

    base = tuple(int(part) for part in match.group(1).split("."))
    qualifier_token = match.group(2)
    qualifier_tail = match.group(3)
    if not qualifier_token:
        return base, (_QUALIFIER_RANK[_RELEASE_QUALIFIER], 0)

    if qualifier_token.isdigit():
        if qualifier_tail and any(not part.isdigit() for part in re.split(r"[-.]", qualifier_tail)):
            return None
        return base, (_QUALIFIER_RANK["number"], int(qualifier_token))

    qualifier_match = _QUALIFIER_PATTERN.match(qualifier_token)
    if not qualifier_match:
        return None

    qualifier = qualifier_match.group(1).lower()
    qualifier_number = qualifier_match.group(2)
    if not qualifier_number and qualifier_tail:
        first_tail_part = re.split(r"[-.]", qualifier_tail)[0]
        if first_tail_part.isdigit():
            qualifier_number = first_tail_part
    return base, (_QUALIFIER_RANK[qualifier], int(qualifier_number or "0"))


def _compare_version_numbers(first: tuple[int, ...], second: tuple[int, ...]) -> int:
    component_count = max(len(first), len(second))
    padded_first = first + ((0,) * (component_count - len(first)))
    padded_second = second + ((0,) * (component_count - len(second)))
    if padded_first > padded_second:
        return 1
    if padded_first < padded_second:
        return -1
    return 0



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
