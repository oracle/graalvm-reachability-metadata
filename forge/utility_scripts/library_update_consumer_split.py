# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Split test-version consumers that no longer pass on a regenerated suite.

Other index entries can run the regenerated suite through `test-version`, and CI
tests each of them, so they are swept before publication and the first failing
consumer moves, with every later one, back to the base commit's suite.
§FS-library-update-tested-version-split.1
"""

import os
from functools import cmp_to_key
from typing import Any

from git_scripts.common_git import (
    parse_coordinate_parts,
    stage_and_commit as stage_and_commit_common,
)
from utility_scripts.library_update_alias_split import (
    ALIAS_SWEEP_STAGE,
    copy_tree_from_commit,
    entry_tested_versions,
    find_entry_by_metadata_version,
    has_changed_tests,
    load_alias_split_metrics,
    run_tested_version_sweep,
    write_alias_split_metrics,
    write_index_entries,
)
from utility_scripts.metadata_index import (
    compare_metadata_versions,
    load_index_entries,
    resolve_metadata_version,
    resolve_test_version,
)
from utility_scripts.stage_logger import log_stage
from utility_scripts.test_project_properties import rewrite_test_project_gradle_properties


CONSUMER_SPLIT_METRICS_KEY = "library_update_consumer_split"


def sort_entries_by_metadata_version(entries: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Return index entries in ascending `metadata-version` order."""
    return sorted(
        entries,
        key=cmp_to_key(
            lambda first, second: compare_metadata_versions(
                str(first.get("metadata-version")),
                str(second.get("metadata-version")),
            )
        ),
    )


def find_test_version_consumers(
        entries: list[dict[str, Any]],
        test_version: str,
        target_metadata_version: str,
) -> list[dict[str, Any]]:
    """Return the other entries whose `test-version` names the suite.

    §FS-library-update-tested-version-split.1.1
    """
    consumers = [
        entry
        for entry in entries
        if isinstance(entry, dict)
        and entry.get("test-version") == test_version
        and entry.get("metadata-version") != target_metadata_version
    ]
    return sort_entries_by_metadata_version(consumers)


def sweep_consumers(
        repo_path: str,
        group: str,
        artifact: str,
        consumers: list[dict[str, Any]],
) -> dict[str, Any]:
    """Run the tests for every consumer's tested versions; stop at the first failure."""
    commands: list[dict[str, Any]] = []
    for index, consumer in enumerate(consumers):
        metadata_version = str(consumer["metadata-version"])
        sweep = run_tested_version_sweep(
            repo_path,
            f"{group}:{artifact}:{metadata_version}",
            entry_tested_versions(consumer),
        )
        commands.extend(sweep["commands"])
        if sweep["failed_version"] is not None:
            return {
                "failed_index": index,
                "failed_metadata_version": metadata_version,
                "failed_version": sweep["failed_version"],
                "commands": commands,
            }
    return {
        "failed_index": None,
        "failed_metadata_version": None,
        "failed_version": None,
        "commands": commands,
    }


def maybe_split_test_version_consumers(
        *,
        repo_path: str,
        coordinates: str,
        base_ref: str,
        metrics_repo_path: str | None,
) -> dict[str, Any] | None:
    """Sweep the regenerated suite's consumers and split at the first failure.

    §FS-library-update-tested-version-split.1.2
    """
    group, artifact, requested_version = parse_coordinate_parts(coordinates)
    target_metadata_version = resolve_metadata_version(repo_path, group, artifact, requested_version)
    test_version = resolve_test_version(repo_path, group, artifact, requested_version)
    if not has_changed_tests(repo_path, base_ref, group, artifact, test_version):
        return None

    existing_split = load_alias_split_metrics(metrics_repo_path, CONSUMER_SPLIT_METRICS_KEY)
    if existing_split is not None:
        log_stage(
            ALIAS_SWEEP_STAGE,
            f"Existing test-version consumer split already recorded for {coordinates}; skipping sweep.",
        )
        return existing_split

    consumers = find_test_version_consumers(
        load_index_entries(repo_path, group, artifact) or [],
        test_version,
        target_metadata_version,
    )
    if not consumers:
        return None

    suite = f"tests/src/{group}/{artifact}/{test_version}"
    log_stage(ALIAS_SWEEP_STAGE, f"Sweeping {len(consumers)} test-version consumer(s) of {suite}.")
    sweep = sweep_consumers(repo_path, group, artifact, consumers)
    if sweep["failed_index"] is None:
        log_stage(ALIAS_SWEEP_STAGE, f"All test-version consumers of {suite} passed; no split needed.")
        return None

    failed_index = int(sweep["failed_index"])
    split_version = str(sweep["failed_metadata_version"])
    passing = [str(consumer["metadata-version"]) for consumer in consumers[:failed_index]]
    repointed = [str(consumer["metadata-version"]) for consumer in consumers[failed_index + 1:]]
    split_test_dir = os.path.join(repo_path, "tests", "src", group, artifact, split_version)
    copy_tree_from_commit(
        repo_path,
        base_ref,
        os.path.join("tests", "src", group, artifact, test_version),
        split_test_dir,
    )
    rewrite_test_project_gradle_properties(split_test_dir, group, artifact, split_version)
    entries = repoint_consumers(repo_path, group, artifact, split_version, repointed)

    log_stage(
        ALIAS_SWEEP_STAGE,
        f"Re-running {split_version} and {len(repointed)} re-pointed consumer(s) on the baseline suite.",
    )
    baseline_consumers = [
        find_entry_by_metadata_version(entries, version) or {}
        for version in [split_version, *repointed]
    ]
    baseline = sweep_consumers(repo_path, group, artifact, baseline_consumers)
    if baseline["failed_index"] is not None:
        raise RuntimeError(
            f"Test-version consumer {group}:{artifact}:{baseline['failed_metadata_version']} fails "
            f"{baseline['failed_version']} on the base commit's {suite} suite; "
            "the split cannot keep its baseline support."
        )

    split_entry = find_entry_by_metadata_version(entries, split_version) or {}
    split = {
        "requested_coordinates": coordinates,
        "current_coordinates": f"{group}:{artifact}:{target_metadata_version}",
        "successor_coordinates": f"{group}:{artifact}:{split_version}",
        "successor_metadata_version": split_version,
        "failed_version": str(sweep["failed_version"]),
        "successor_versions": entry_tested_versions(split_entry),
        "test_version": test_version,
        "passing_consumers": passing,
        "repointed_consumers": repointed,
        "commands": [*sweep["commands"], *baseline["commands"]],
    }
    write_alias_split_metrics(metrics_repo_path, split, CONSUMER_SPLIT_METRICS_KEY)
    stage_and_commit_common(
        [
            os.path.join("metadata", group, artifact, "index.json"),
            os.path.join("tests", "src", group, artifact, split_version),
        ],
        f"Split test-version consumers of {coordinates}",
        cwd=repo_path,
    )
    return split


def repoint_consumers(
        repo_path: str,
        group: str,
        artifact: str,
        split_version: str,
        repointed: list[str],
) -> list[dict[str, Any]]:
    """Give the split consumer its own suite and point later consumers at it."""
    entries = load_index_entries(repo_path, group, artifact) or []
    for entry in entries:
        if not isinstance(entry, dict):
            continue
        metadata_version = str(entry.get("metadata-version"))
        if metadata_version == split_version:
            entry.pop("test-version", None)
        elif metadata_version in repointed:
            entry["test-version"] = split_version
    write_index_entries(repo_path, group, artifact, entries)
    return entries
