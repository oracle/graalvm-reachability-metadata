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
ParsedMetadataVersion: TypeAlias = tuple[tuple[int, ...], tuple[int, int]]


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


@dataclass(frozen=True)
class VersionBackfillBaseline:
    """Usable test and metadata support selected for a version backfill."""

    entry: dict[str, Any]
    metadata_version: str
    test_version: str
    supported_version: str
    match_type: str
    reason: str


@dataclass(frozen=True)
class _BaselineCandidate:
    entry: dict[str, Any]
    supported_version: str
    parsed_version: ParsedMetadataVersion


_RELEASE_QUALIFIER = "release"
_VERSION_PATTERN = re.compile(
    r"^(\d+(?:\.\d+)*)(?:\.(?:Final|RELEASE))?"
    r"(?:[-.](alpha\d*|beta\d*|rc\d*|cr\d*|m\d+|ea\d*|b\d+|\d+|preview)(?:[-.](.*))?)?$",
    re.IGNORECASE,
)
_MAVEN_NUMERIC_VERSION_PATTERN = re.compile(
    r"^([vr]?)(\d+(?:\.\d+)*)(?:[A-Za-z._+\-][A-Za-z0-9._+\-]*)?$",
    re.IGNORECASE,
)
_QUALIFIER_PATTERN = re.compile(r"^(alpha|beta|rc|cr|m|ea|b|preview)(\d*)$", re.IGNORECASE)
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
    "variant": 90,
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


def resolve_version_backfill_baseline(
        repo_path: str,
        group: str,
        artifact: str,
        requested_version: str,
) -> VersionBackfillBaseline | None:
    """Resolve one usable same-major baseline for deterministic version backfill.

    Exact index ownership takes precedence over version ordering. Non-exact
    selection never crosses a major-version boundary. §WF-forge-workflow-drivers.2
    """
    target = resolve_library_update_target(repo_path, group, artifact, requested_version)
    if target.match_type != MATCH_NEW_VERSION and target.matched_entry is not None:
        if not _entry_has_usable_support(repo_path, group, artifact, target.matched_entry):
            return None
        return _version_backfill_baseline(
            target.matched_entry,
            requested_version,
            target.match_type,
            f"exact {target.match_type} ownership of {requested_version}",
        )

    requested_parsed = _parse_metadata_version(requested_version)
    if requested_parsed is None:
        return None

    entries = load_index_entries(repo_path, group, artifact) or []
    candidates: list[_BaselineCandidate] = []
    for entry in entries:
        if (
                not isinstance(entry, dict)
                or is_not_for_native_image_entry(entry)
                or not _entry_has_usable_support(repo_path, group, artifact, entry)
        ):
            continue
        for supported_version in _entry_supported_versions(entry):
            parsed_version = _parse_metadata_version(supported_version)
            if parsed_version is None:
                continue
            candidates.append(_BaselineCandidate(
                entry=entry,
                supported_version=supported_version,
                parsed_version=parsed_version,
            ))

    requested_numbers = requested_parsed[0]
    same_major_minor = [
        candidate for candidate in candidates
        if _same_version_line(candidate.parsed_version[0], requested_numbers, 2)
    ]
    selected = _select_ordered_baseline_candidate(same_major_minor, requested_version)
    if selected is not None:
        ordering = _baseline_ordering_reason(selected.supported_version, requested_version)
        return _version_backfill_baseline(
            selected.entry,
            selected.supported_version,
            "same-major-minor",
            f"{ordering} same major/minor supported version {selected.supported_version}",
        )

    same_major = [
        candidate for candidate in candidates
        if _same_version_line(candidate.parsed_version[0], requested_numbers, 1)
    ]
    selected = _select_ordered_baseline_candidate(same_major, requested_version)
    if selected is not None:
        ordering = _baseline_ordering_reason(selected.supported_version, requested_version)
        return _version_backfill_baseline(
            selected.entry,
            selected.supported_version,
            "same-major",
            f"{ordering} same-major supported version {selected.supported_version}",
        )
    return None


def require_version_backfill_baseline(
        repo_path: str,
        group: str,
        artifact: str,
        requested_version: str,
) -> VersionBackfillBaseline:
    """Resolve a compatible baseline or raise an actionable routing error."""
    baseline = resolve_version_backfill_baseline(
        repo_path,
        group,
        artifact,
        requested_version,
    )
    if baseline is not None:
        return baseline

    coordinate = f"{group}:{artifact}:{requested_version}"
    index_display = os.path.relpath(index_path(repo_path, group, artifact), repo_path)
    raise RuntimeError(
        "ERROR: Cannot resolve a compatible version-backfill baseline for "
        f"{coordinate} from {index_display}. Expected a usable exact owner or "
        "a supported test suite in the same major version; each baseline must "
        "have both metadata and test directories. A cross-major `latest` entry "
        "is not a compatible baseline. Restore a compatible test suite or "
        "route the issue for human intervention."
    )


def _version_backfill_baseline(
        entry: dict[str, Any],
        supported_version: str,
        match_type: str,
        reason: str,
) -> VersionBackfillBaseline:
    metadata_version = _entry_metadata_version(entry, supported_version)
    test_version = _entry_test_version(entry, metadata_version)
    return VersionBackfillBaseline(
        entry=entry,
        metadata_version=metadata_version,
        test_version=test_version,
        supported_version=supported_version,
        match_type=match_type,
        reason=reason,
    )


def _entry_has_usable_support(
        repo_path: str,
        group: str,
        artifact: str,
        entry: dict[str, Any],
) -> bool:
    metadata_version = _entry_metadata_version(entry, "")
    if not metadata_version:
        return False
    test_version = _entry_test_version(entry, metadata_version)
    metadata_dir = os.path.join(repo_path, "metadata", group, artifact, metadata_version)
    test_dir = os.path.join(repo_path, "tests", "src", group, artifact, test_version)
    return os.path.isdir(metadata_dir) and os.path.isdir(test_dir)


def _entry_supported_versions(entry: dict[str, Any]) -> list[str]:
    versions: list[str] = []
    for version in [entry.get("metadata-version"), entry.get("test-version"), *_tested_versions(entry)]:
        if isinstance(version, str) and version and version not in versions:
            versions.append(version)
    return versions


def _same_version_line(
        candidate_numbers: tuple[int, ...],
        requested_numbers: tuple[int, ...],
        component_count: int,
) -> bool:
    if len(candidate_numbers) < component_count or len(requested_numbers) < component_count:
        return False
    return candidate_numbers[:component_count] == requested_numbers[:component_count]


def _select_ordered_baseline_candidate(
        candidates: list[_BaselineCandidate],
        requested_version: str,
) -> _BaselineCandidate | None:
    if not candidates:
        return None
    prior_candidates = [
        candidate for candidate in candidates
        if (_compare_parseable_metadata_versions(candidate.supported_version, requested_version) or 0) <= 0
    ]
    if prior_candidates:
        selected = prior_candidates[0]
        for candidate in prior_candidates[1:]:
            comparison = _compare_parseable_metadata_versions(
                candidate.supported_version,
                selected.supported_version,
            )
            if comparison is not None and comparison > 0:
                selected = candidate
        return selected

    selected = candidates[0]
    for candidate in candidates[1:]:
        comparison = _compare_parseable_metadata_versions(
            candidate.supported_version,
            selected.supported_version,
        )
        if comparison is not None and comparison < 0:
            selected = candidate
    return selected


def _baseline_ordering_reason(supported_version: str, requested_version: str) -> str:
    comparison = _compare_parseable_metadata_versions(supported_version, requested_version)
    if comparison is not None and comparison <= 0:
        return "nearest prior"
    return "nearest following"


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
    comparison = _compare_parseable_metadata_versions(candidate_version, current_version)
    return comparison is not None and comparison > 0


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
    """Parse known qualifiers or retain the numeric line of a Maven variant.

    §WF-forge-workflow-drivers.2
    """
    match = _VERSION_PATTERN.match(version)
    if not match:
        return _parse_maven_numeric_version_fallback(version)

    base = tuple(int(part) for part in match.group(1).split("."))
    qualifier_token = match.group(2)
    qualifier_tail = match.group(3)
    if not qualifier_token:
        return base, (_QUALIFIER_RANK[_RELEASE_QUALIFIER], 0)

    if qualifier_token.isdigit():
        if qualifier_tail and any(not part.isdigit() for part in re.split(r"[-.]", qualifier_tail)):
            return _parse_maven_numeric_version_fallback(version)
        return base, (_QUALIFIER_RANK["number"], int(qualifier_token))

    qualifier_match = _QUALIFIER_PATTERN.match(qualifier_token)
    if not qualifier_match:
        return _parse_maven_numeric_version_fallback(version)

    qualifier = qualifier_match.group(1).lower()
    qualifier_number = qualifier_match.group(2)
    if not qualifier_number and qualifier_tail:
        first_tail_part = re.split(r"[-.]", qualifier_tail)[0]
        if first_tail_part.isdigit():
            qualifier_number = first_tail_part
    return base, (_QUALIFIER_RANK[qualifier], int(qualifier_number or "0"))


def _parse_maven_numeric_version_fallback(version: str) -> ParsedMetadataVersion | None:
    """Retain a Maven variant's complete leading numeric version line."""
    match = _MAVEN_NUMERIC_VERSION_PATTERN.match(version)
    if not match:
        return None
    base = tuple(int(part) for part in match.group(2).split("."))
    if match.group(1):
        base = (0, *base)
    return base, (_QUALIFIER_RANK["variant"], 0)


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
