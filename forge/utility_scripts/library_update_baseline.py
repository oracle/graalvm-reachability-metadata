# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Resolve baseline test suites for missing-version library-update routing."""

import os
from dataclasses import dataclass
from functools import cmp_to_key
from typing import Any

from utility_scripts.metadata_index import (
    compare_parseable_metadata_versions,
    index_path,
    is_not_for_native_image_entry,
    is_parseable_metadata_version,
    load_index_entries,
)
from utility_scripts.versioned_test_project import (
    VersionedTestProjectPreparation,
    prepare_versioned_test_project,
)


class LibraryUpdateBaselineSetupError(RuntimeError):
    """Raised when no usable supported baseline test suite can be resolved."""


@dataclass(frozen=True)
class LibraryUpdateBaseline:
    """Selected baseline for probing a requested missing library-update version."""

    requested_coordinate: str
    baseline_coordinate: str
    metadata_version: str
    test_version: str
    source_test_dir: str
    selection_reason: str
    index_path: str


@dataclass(frozen=True)
class _BaselineCandidate:
    metadata_version: str
    test_version: str
    source_test_dir: str
    latest: bool
    entry_index: int

    @property
    def test_version_parseable(self) -> bool:
        return is_parseable_metadata_version(self.test_version)

    @property
    def metadata_version_parseable(self) -> bool:
        return is_parseable_metadata_version(self.metadata_version)

    @property
    def selection_version(self) -> str | None:
        if self.test_version_parseable:
            return self.test_version
        if self.metadata_version_parseable:
            return self.metadata_version
        return None

    @property
    def has_test_dir(self) -> bool:
        return os.path.isdir(self.source_test_dir)


def _parse_requested_coordinate(requested_coordinate: str) -> tuple[str, str, str]:
    parts = requested_coordinate.split(":")
    if len(parts) != 3 or any(not part for part in parts):
        raise LibraryUpdateBaselineSetupError(
            f"Invalid requested coordinate {requested_coordinate!r}; expected group:artifact:version"
        )
    return parts[0], parts[1], parts[2]


def _candidate_from_entry(
        repo_path: str,
        group: str,
        artifact: str,
        entry: dict[str, Any],
        entry_index: int,
) -> _BaselineCandidate | None:
    metadata_version = entry.get("metadata-version")
    if not isinstance(metadata_version, str) or not metadata_version:
        return None
    test_version = entry.get("test-version") or metadata_version
    if not isinstance(test_version, str) or not test_version:
        return None
    return _BaselineCandidate(
        metadata_version=metadata_version,
        test_version=test_version,
        source_test_dir=os.path.join(repo_path, "tests", "src", group, artifact, test_version),
        latest=entry.get("latest") is True,
        entry_index=entry_index,
    )


def _candidate_label(candidate: _BaselineCandidate) -> str:
    return (
        f"entry[{candidate.entry_index}] "
        f"metadata-version={candidate.metadata_version} test-version={candidate.test_version}"
    )


def _entry_label(entry: dict[str, Any], entry_index: int) -> str:
    metadata_version = entry.get("metadata-version")
    test_version = entry.get("test-version") or metadata_version
    return f"entry[{entry_index}] metadata-version={metadata_version!r} test-version={test_version!r}"


def _newest_candidate(candidates: list[_BaselineCandidate]) -> _BaselineCandidate | None:
    parseable_candidates = [
        candidate for candidate in candidates
        if candidate.selection_version is not None
    ]
    if not parseable_candidates:
        return None

    def compare(first: _BaselineCandidate, second: _BaselineCandidate) -> int:
        first_version = first.selection_version
        second_version = second.selection_version
        if first_version is None or second_version is None:
            return 0
        comparison = compare_parseable_metadata_versions(first_version, second_version)
        if comparison is None:
            return 0
        if comparison != 0:
            return comparison
        metadata_comparison = compare_parseable_metadata_versions(first.metadata_version, second.metadata_version)
        if metadata_comparison is not None and metadata_comparison != 0:
            return metadata_comparison
        return first.entry_index - second.entry_index

    return sorted(parseable_candidates, key=cmp_to_key(compare))[-1]


def _format_failure_details(
        *,
        requested_coordinate: str,
        resolved_index_path: str,
        ignored_markers: list[str],
        invalid_entries: list[str],
        missing_test_dirs: list[str],
        unparseable_candidates: list[str],
        latest_reason: str,
) -> str:
    detail_parts = [
        f"No usable supported test suite exists for {requested_coordinate}.",
        f"index={resolved_index_path}",
    ]
    detail_parts.append("ignored not-for-native-image entries: " + (
        ", ".join(ignored_markers) if ignored_markers else "none"
    ))
    detail_parts.append("invalid supported entries: " + (
        ", ".join(invalid_entries) if invalid_entries else "none"
    ))
    detail_parts.append("missing test directories: " + (
        ", ".join(missing_test_dirs) if missing_test_dirs else "none"
    ))
    detail_parts.append("unparseable candidate versions: " + (
        ", ".join(unparseable_candidates) if unparseable_candidates else "none"
    ))
    detail_parts.append(f"latest marker selection: {latest_reason}")
    return " ".join(detail_parts)


def resolve_latest_supported_baseline(repo_path: str, requested_coordinate: str) -> LibraryUpdateBaseline:
    """Resolve the latest usable supported test suite for a missing requested version."""
    group, artifact, _ = _parse_requested_coordinate(requested_coordinate)
    resolved_index_path = index_path(repo_path, group, artifact)
    entries = load_index_entries(repo_path, group, artifact)
    if entries is None:
        raise LibraryUpdateBaselineSetupError(
            f"Metadata index not found for {requested_coordinate}: {resolved_index_path}"
        )

    ignored_markers: list[str] = []
    invalid_entries: list[str] = []
    invalid_latest_entries: list[str] = []
    supported_candidates: list[_BaselineCandidate] = []
    for entry_index, entry in enumerate(entries):
        if not isinstance(entry, dict):
            invalid_entries.append(f"entry[{entry_index}] is not an object")
            continue
        if is_not_for_native_image_entry(entry):
            ignored_markers.append(_entry_label(entry, entry_index))
            continue
        candidate = _candidate_from_entry(repo_path, group, artifact, entry, entry_index)
        if candidate is None:
            entry_description = _entry_label(entry, entry_index)
            invalid_entries.append(entry_description)
            if entry.get("latest") is True:
                invalid_latest_entries.append(entry_description)
            continue
        supported_candidates.append(candidate)

    usable_candidates = [candidate for candidate in supported_candidates if candidate.has_test_dir]
    missing_test_dirs = [
        f"{_candidate_label(candidate)} path={candidate.source_test_dir}"
        for candidate in supported_candidates
        if not candidate.has_test_dir
    ]
    unparseable_candidates = [
        _candidate_label(candidate)
        for candidate in usable_candidates
        if candidate.selection_version is None
    ]
    latest_candidates = [candidate for candidate in supported_candidates if candidate.latest]
    latest_usable_candidates = [candidate for candidate in latest_candidates if candidate.has_test_dir]
    latest_marker_count = len(latest_candidates) + len(invalid_latest_entries)
    latest_descriptions = [
        _candidate_label(candidate) for candidate in latest_candidates
    ] + invalid_latest_entries

    if latest_marker_count == 1 and len(latest_usable_candidates) == 1:
        selected = latest_usable_candidates[0]
        reason = f"selected single valid latest entry: {_candidate_label(selected)}"
    else:
        if latest_marker_count == 0:
            latest_reason = "no latest marker present"
        elif latest_marker_count > 1:
            latest_reason = "latest marker ambiguous: " + ", ".join(latest_descriptions)
        else:
            latest_reason = "latest marker unusable: " + ", ".join(latest_descriptions)
        selected = _newest_candidate(usable_candidates)
        if selected is None:
            raise LibraryUpdateBaselineSetupError(
                _format_failure_details(
                    requested_coordinate=requested_coordinate,
                    resolved_index_path=resolved_index_path,
                    ignored_markers=ignored_markers,
                    invalid_entries=invalid_entries,
                    missing_test_dirs=missing_test_dirs,
                    unparseable_candidates=unparseable_candidates,
                    latest_reason=latest_reason,
                )
            )
        reason = f"{latest_reason}; selected newest parseable candidate: {_candidate_label(selected)}"

    return LibraryUpdateBaseline(
        requested_coordinate=requested_coordinate,
        baseline_coordinate=f"{group}:{artifact}:{selected.test_version}",
        metadata_version=selected.metadata_version,
        test_version=selected.test_version,
        source_test_dir=selected.source_test_dir,
        selection_reason=reason,
        index_path=resolved_index_path,
    )


def prepare_probe_test_project(
        repo_path: str,
        baseline: LibraryUpdateBaseline,
) -> VersionedTestProjectPreparation:
    """Prepare only a copied test suite for an isolated compatibility probe."""
    group, artifact, requested_version = _parse_requested_coordinate(baseline.requested_coordinate)
    replacements = [
        (f"{group}:{artifact}:{baseline.metadata_version}", baseline.requested_coordinate),
        (f"{group}:{artifact}:{baseline.test_version}", baseline.requested_coordinate),
        (baseline.metadata_version, requested_version),
        (baseline.test_version, requested_version),
    ]
    return prepare_versioned_test_project(
        repo_path,
        group,
        artifact,
        baseline.test_version,
        requested_version,
        source_test_dir=baseline.source_test_dir,
        replace_existing=True,
        rewrite_project_files=True,
        replacements=replacements,
        allow_bare_versions=False,
        normalize_gradle_properties=True,
    )
