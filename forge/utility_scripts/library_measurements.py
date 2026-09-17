# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Repository-derived measurements for one library version: stats snapshots, metadata entry counts, coverage, and generated LOC."""

import os
import sys

import utility_scripts.count_reachability_entries as reachability_metadata_count
import utility_scripts.count_native_image_config_entries as legacy_metadata_count
from utility_scripts.library_stats import load_library_stats_entry
from utility_scripts.metadata_index import resolve_metadata_version, resolve_test_version
from utility_scripts.native_image_config_policy import (
    find_changed_legacy_test_native_image_config_files_for_coordinate,
    format_legacy_test_native_image_config_error,
)


def _is_valid_dynamic_access_entry(entry) -> bool:
    """Return True when the dynamic-access stats entry matches the expected object shape."""
    if not isinstance(entry, dict):
        return False

    required_keys = {"coveredCalls", "totalCalls", "coverageRatio", "breakdown"}
    return required_keys.issubset(entry.keys()) and isinstance(entry.get("breakdown"), dict)


def _is_valid_library_coverage_entry(entry) -> bool:
    """Return True when the library-coverage entry matches the expected object shape."""
    if not isinstance(entry, dict):
        return False

    required_keys = {"covered", "total", "ratio"}
    return required_keys.issubset(entry.keys())


def _sanitize_library_coverage(library_coverage) -> dict | None:
    """Drop invalid coverage counters so metrics output remains schema-compatible."""
    if not isinstance(library_coverage, dict):
        return None

    sanitized = {}
    for metric in ("instruction", "line", "method"):
        entry = library_coverage.get(metric)
        if _is_valid_library_coverage_entry(entry):
            sanitized[metric] = entry

    return sanitized or None


def _sanitize_stats_snapshot(version_entry) -> dict | None:
    """Normalize a stats snapshot loaded from exploded stats files for metrics serialization."""
    if not isinstance(version_entry, dict):
        return None

    version = version_entry.get("version")
    if not isinstance(version, str) or not version:
        return None

    sanitized = {"version": version}

    dynamic_access = version_entry.get("dynamicAccess")
    if _is_valid_dynamic_access_entry(dynamic_access):
        sanitized["dynamicAccess"] = dynamic_access

    library_coverage = _sanitize_library_coverage(version_entry.get("libraryCoverage"))
    if library_coverage is not None:
        sanitized["libraryCoverage"] = library_coverage

    return sanitized


def load_library_stats_snapshot(repo_path: str, package: str, artifact: str, library_version: str) -> dict | None:
    """Load the matching version entry from exploded stats files if available."""
    return _sanitize_stats_snapshot(load_library_stats_entry(repo_path, package, artifact, library_version))


def _load_raw_library_stats_version_entry(repo_path: str, package: str, artifact: str, library_version: str) -> dict | None:
    """Load the raw matching version entry from exploded stats files if available."""
    return load_library_stats_entry(repo_path, package, artifact, library_version)


def _stats_coverage_metrics(repo_path: str, package: str, artifact: str, library_version: str) -> tuple[float, int] | None:
    """Return coverage metrics from exploded stats files when available."""
    version_entry = _load_raw_library_stats_version_entry(repo_path, package, artifact, library_version)
    if not isinstance(version_entry, dict):
        return None

    library_coverage = version_entry.get("libraryCoverage")
    if not isinstance(library_coverage, dict):
        return None

    line_coverage = library_coverage.get("line")
    if isinstance(line_coverage, dict):
        ratio = line_coverage.get("ratio")
        covered = line_coverage.get("covered")
        if isinstance(ratio, (int, float)) and isinstance(covered, int):
            return float(ratio) * 100.0, covered

    if line_coverage != "N/A":
        return None

    instruction_coverage = library_coverage.get("instruction")
    if instruction_coverage == "N/A":
        return 100.0, 0
    if isinstance(instruction_coverage, dict):
        ratio = instruction_coverage.get("ratio")
        if isinstance(ratio, (int, float)):
            return float(ratio) * 100.0, 0
    return None


def count_generated_loc(root_dir: str) -> int:
    if not root_dir or not os.path.isdir(root_dir):
        return 0
    total = 0
    for dirpath, _, filenames in os.walk(root_dir):
        for fname in filenames:
            if not _is_test_source_file(fname):
                continue
            fpath = os.path.join(dirpath, fname)
            with open(fpath, "r", encoding="utf-8", errors="ignore") as f:
                for line in f:
                    if line.strip():
                        total += 1
    return total


def count_metadata_entries(repo_path: str, package: str, artifact: str, library_version: str):
    metadata_version = resolve_metadata_version(repo_path, package, artifact, library_version)
    metadata_dir = os.path.join(repo_path, "metadata", package, artifact, metadata_version)
    reach_json = os.path.join(metadata_dir, "reachability-metadata.json")

    if os.path.isfile(reach_json):
        counts = reachability_metadata_count.count_reachability_file(reach_json)
        return int(counts.get("total", 0))

    json_files = legacy_metadata_count.find_json_files([metadata_dir])
    total = 0
    for path in json_files:
        total += int(legacy_metadata_count.count_entries_in_file(path))
    return total


def count_test_only_metadata_entries(
        repo_path: str,
        package: str,
        artifact: str,
        library_version: str,
        base_commit: str | None = None,
) -> int:
    """Count test-only reachability metadata entries for a library version."""
    coordinate = f"{package}:{artifact}:{library_version}"
    legacy_test_config_paths = (
        find_changed_legacy_test_native_image_config_files_for_coordinate(repo_path, coordinate, base_commit)
        if base_commit is not None
        else []
    )
    if legacy_test_config_paths:
        raise ValueError(format_legacy_test_native_image_config_error(legacy_test_config_paths))

    test_version = resolve_test_version_dir(repo_path, package, artifact, library_version)
    reach_json = os.path.join(
        repo_path,
        "tests",
        "src",
        package,
        artifact,
        test_version,
        "src",
        "test",
        "resources",
        "META-INF",
        "native-image",
        "reachability-metadata.json",
    )
    if not os.path.isfile(reach_json):
        return 0

    counts = reachability_metadata_count.count_reachability_file(reach_json)
    return int(counts.get("total", 0))


def resolve_test_version_dir(repo_path: str, package: str, artifact: str, library_version: str) -> str:
    """Resolve the tests/src directory name for a library version using metadata index.json."""
    return resolve_test_version(repo_path, package, artifact, library_version)


def collect_version_coverage_metrics(repo_path: str, package: str, artifact: str, library_version: str):
    """Return coverage metrics from exploded stats files for a library version."""
    stats_coverage = _stats_coverage_metrics(repo_path, package, artifact, library_version)
    if stats_coverage is not None:
        return stats_coverage
    print(
        "ERROR: Coverage stats not found in "
        f"stats/<groupId>/<artifactId>/<metadata-version>/stats.json for {package}:{artifact}:{library_version}.",
        file=sys.stderr,
    )
    return 0.0, 0


def resolve_metadata_artifact_path(repo_path: str, package: str, artifact: str, library_version: str) -> str:
    """Resolve the metadata artifact path for a library version."""
    metadata_version = resolve_metadata_version(repo_path, package, artifact, library_version)
    reach_json = os.path.join(repo_path, "metadata", package, artifact, metadata_version, "reachability-metadata.json")
    if os.path.isfile(reach_json):
        metadata_file_path = os.path.relpath(reach_json, repo_path)
    else:
        metadata_file_path = os.path.relpath(
            os.path.join(repo_path, "metadata", package, artifact, metadata_version),
            repo_path,
        )

    return str(metadata_file_path)


def _is_test_source_file(file_name: str) -> bool:
    return file_name.endswith((".java", ".kt", ".scala", ".groovy"))
