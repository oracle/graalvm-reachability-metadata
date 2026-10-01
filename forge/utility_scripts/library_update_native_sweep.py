# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Native-test sweep of every version still on a regenerated library-update suite.

A suite that passes on the JVM can still exercise dynamic access a version's
metadata does not register. Each failing version is repaired through the native
test verification gate: on an entry's own version the metadata is added to the
entry, on a later tested version the entry is split first.
§FS-library-update-tested-version-split.2
"""

import copy
import os
import shutil
import tempfile
from typing import Any

from git_scripts.common_git import (
    parse_coordinate_parts,
    stage_and_commit as stage_and_commit_common,
)
from utility_scripts.library_update_alias_split import (
    ALIAS_SWEEP_STAGE,
    entry_tested_versions,
    find_entry_by_metadata_version,
    find_entry_index_by_metadata_version,
    generate_library_stats,
    hand_over_latest,
    has_changed_tests,
    load_alias_split_metrics,
    run_tested_version_sweep,
    write_alias_split_metrics,
    write_index_entries,
)
from utility_scripts.library_update_consumer_split import sort_entries_by_metadata_version
from utility_scripts.metadata_index import load_index_entries, resolve_test_version
from utility_scripts.native_test_verification import STATUS_FAILED, verify_native_test_passes
from utility_scripts.stage_logger import log_stage


NATIVE_SWEEP_METRICS_KEY = "library_update_native_sweep"
NATIVE_SWEEP_TASK_TYPE = "library-update-native-sweep"
REPAIR_METADATA = "metadata"
REPAIR_ENTRY_SPLIT = "entry_split"


def entries_on_suite(entries: list[dict[str, Any]], test_version: str) -> list[dict[str, Any]]:
    """Return the entries whose test directory is the suite, by `metadata-version`."""
    on_suite = [
        entry
        for entry in entries
        if isinstance(entry, dict)
        and str(entry.get("test-version") or entry.get("metadata-version")) == test_version
    ]
    return sort_entries_by_metadata_version(on_suite)


def run_library_update_native_sweep(
        *,
        repo_path: str,
        coordinates: str,
        base_ref: str,
        metrics_repo_path: str | None,
) -> dict[str, Any] | None:
    """Run native tests for every version on the regenerated suite and repair failures.

    §FS-library-update-tested-version-split.2
    """
    group, artifact, requested_version = parse_coordinate_parts(coordinates)
    test_version = resolve_test_version(repo_path, group, artifact, requested_version)
    if not has_changed_tests(repo_path, base_ref, group, artifact, test_version):
        return None

    existing_sweep = load_alias_split_metrics(metrics_repo_path, NATIVE_SWEEP_METRICS_KEY)
    if existing_sweep is not None:
        log_stage(
            ALIAS_SWEEP_STAGE,
            f"Existing native sweep already recorded for {coordinates}; skipping sweep.",
        )
        return existing_sweep

    entries = load_index_entries(repo_path, group, artifact) or []
    # Each item is an entry and the index of the first tested version still to check.
    pending: list[tuple[str, int]] = [
        (str(entry["metadata-version"]), 0) for entry in entries_on_suite(entries, test_version)
    ]
    log_stage(
        ALIAS_SWEEP_STAGE,
        f"Starting native sweep of tests/src/{group}/{artifact}/{test_version} "
        f"across {len(pending)} index entr(ies).",
    )
    repairs: list[dict[str, Any]] = []
    while pending:
        metadata_version, start = pending.pop(0)
        entry = find_entry_by_metadata_version(load_index_entries(repo_path, group, artifact) or [], metadata_version)
        versions = entry_tested_versions(entry or {})
        if not versions[start:]:
            continue
        sweep = run_tested_version_sweep(
            repo_path,
            f"{group}:{artifact}:{metadata_version}",
            versions[start:],
            gradle_task="test",
        )
        if sweep["failed_version"] is None:
            continue
        failed_version = str(sweep["failed_version"])
        failed_index = start + int(sweep["failed_index"])
        # The first tested version has no prefix to keep, so it is the entry's own.
        if failed_version == metadata_version or failed_index == 0:
            repair_native_metadata(repo_path, f"{group}:{artifact}:{metadata_version}", failed_version)
            generate_library_stats(repo_path, f"{group}:{artifact}:{metadata_version}", NATIVE_SWEEP_TASK_TYPE)
            repairs.append({
                "kind": REPAIR_METADATA,
                "metadata_version": metadata_version,
                "failed_version": failed_version,
            })
            pending.insert(0, (metadata_version, failed_index + 1))
            continue
        moved_versions = split_entry_at_version(
            repo_path, group, artifact, metadata_version, failed_version, test_version,
        )
        successor_coordinates = f"{group}:{artifact}:{failed_version}"
        repair_native_metadata(repo_path, successor_coordinates, failed_version)
        generate_library_stats(repo_path, successor_coordinates, NATIVE_SWEEP_TASK_TYPE)
        repairs.append({
            "kind": REPAIR_ENTRY_SPLIT,
            "metadata_version": metadata_version,
            "successor_metadata_version": failed_version,
            "failed_version": failed_version,
            "moved_versions": moved_versions,
        })
        pending.insert(0, (failed_version, 1))

    if not repairs:
        log_stage(ALIAS_SWEEP_STAGE, f"Native sweep passed for every version on {test_version}; no repair needed.")
        return None

    record = {"test_version": test_version, "repairs": repairs}
    write_alias_split_metrics(metrics_repo_path, record, NATIVE_SWEEP_METRICS_KEY)
    changed_versions = sorted({
        str(repair.get("successor_metadata_version") or repair["metadata_version"]) for repair in repairs
    })
    stage_and_commit_common(
        [
            os.path.join("metadata", group, artifact, "index.json"),
            *[os.path.join("metadata", group, artifact, version) for version in changed_versions],
            *[os.path.join("stats", group, artifact, version) for version in changed_versions],
        ],
        f"Repair native tests on the shared suite of {coordinates}",
        cwd=repo_path,
    )
    return record


def split_entry_at_version(
        repo_path: str,
        group: str,
        artifact: str,
        metadata_version: str,
        failed_version: str,
        test_version: str,
) -> list[str]:
    """Move a failing tested version and every later one to a new entry on the suite.

    The new entry starts from the original entry's metadata and keeps running the
    regenerated suite through `test-version`. §FS-library-update-tested-version-split.2.2
    """
    entries = load_index_entries(repo_path, group, artifact) or []
    entry_index = find_entry_index_by_metadata_version(entries, metadata_version)
    if entry_index is None:
        raise RuntimeError(f"Missing index entry for metadata-version {metadata_version}")
    current_entry = entries[entry_index]
    versions = entry_tested_versions(current_entry)
    failed_index = versions.index(failed_version)
    current_entry["tested-versions"] = versions[:failed_index]

    successor_entry: dict[str, Any] = {}
    for key, value in copy.deepcopy(current_entry).items():
        if key == "test-version":
            continue
        successor_entry[key] = value
        if key == "metadata-version":
            successor_entry["metadata-version"] = failed_version
            successor_entry["test-version"] = test_version
    successor_entry["tested-versions"] = versions[failed_index:]
    hand_over_latest(current_entry, successor_entry)
    entries.insert(entry_index + 1, successor_entry)
    write_index_entries(repo_path, group, artifact, entries)

    metadata_root = os.path.join(repo_path, "metadata", group, artifact)
    successor_metadata_dir = os.path.join(metadata_root, failed_version)
    if os.path.exists(successor_metadata_dir):
        shutil.rmtree(successor_metadata_dir)
    shutil.copytree(os.path.join(metadata_root, metadata_version), successor_metadata_dir)
    return versions[failed_index:]


def repair_native_metadata(repo_path: str, coordinates: str, library_version: str) -> None:
    """Add the metadata a version needs through the native test verification gate."""
    log_stage(ALIAS_SWEEP_STAGE, f"Repairing native metadata of {coordinates} for {library_version}.")
    env = dict(os.environ)
    env["GVM_TCK_LV"] = library_version
    with tempfile.TemporaryDirectory(prefix="forge-native-sweep-") as gate_root:
        result = verify_native_test_passes(
            reachability_repo_path=repo_path,
            coordinate=coordinates,
            output_dir=os.path.join(gate_root, "gate"),
            env=env,
        )
    if result.status == STATUS_FAILED:
        raise RuntimeError(
            f"Native sweep could not repair {coordinates} for {library_version}: "
            f"{result.failure_detail or 'native tests still fail'} "
            f"(log: {result.failure_log_path or result.last_native_test_log_path})"
        )
