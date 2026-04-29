# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""
Helpers for working with exploded library stats files under `stats/<group>/<artifact>/<metadata-version>/stats.json`.
"""

import json
import os


def _load_index_entries(repo_path: str, group: str, artifact: str) -> list[dict] | None:
    """Load `metadata/<group>/<artifact>/index.json` when present and valid."""
    index_path = os.path.join(repo_path, "metadata", group, artifact, "index.json")
    if not os.path.isfile(index_path):
        return None

    try:
        with open(index_path, "r", encoding="utf-8") as index_file:
            index_entries = json.load(index_file)
    except (OSError, json.JSONDecodeError):
        return None

    if not isinstance(index_entries, list):
        return None
    return index_entries


def resolve_stats_metadata_version(repo_path: str, group: str, artifact: str, library_version: str) -> str:
    """Resolve the metadata-version directory that owns stats for a requested library version."""
    index_entries = _load_index_entries(repo_path, group, artifact)
    if not index_entries:
        return library_version

    for entry in index_entries:
        metadata_version = str(entry.get("metadata-version") or "")
        if metadata_version == library_version:
            return metadata_version

    for entry in index_entries:
        tested_versions = entry.get("tested-versions") or []
        if library_version in tested_versions:
            metadata_version = str(entry.get("metadata-version") or "")
            if metadata_version:
                return metadata_version

    return library_version


def stats_artifact_dir(repo_path: str, group: str, artifact: str) -> str:
    """Return the exploded stats root for one library."""
    return os.path.join(repo_path, "stats", group, artifact)


def stats_file_path(repo_path: str, group: str, artifact: str, metadata_version: str) -> str:
    """Return the exploded stats file path for one metadata version."""
    return os.path.join(stats_artifact_dir(repo_path, group, artifact), metadata_version, "stats.json")


def resolve_stats_file_path(repo_path: str, group: str, artifact: str, library_version: str) -> str:
    """Return the exploded stats file path that should contain the requested version entry."""
    metadata_version = resolve_stats_metadata_version(repo_path, group, artifact, library_version)
    return stats_file_path(repo_path, group, artifact, metadata_version)


def load_library_stats_entry(repo_path: str, group: str, artifact: str, library_version: str) -> dict | None:
    """Load one version entry from an exploded stats file."""
    metadata_version = resolve_stats_metadata_version(repo_path, group, artifact, library_version)
    resolved_stats_path = stats_file_path(repo_path, group, artifact, metadata_version)
    if not os.path.isfile(resolved_stats_path):
        return None

    try:
        with open(resolved_stats_path, "r", encoding="utf-8") as stats_file:
            data = json.load(stats_file)
    except (OSError, json.JSONDecodeError):
        return None

    versions = data.get("versions") or []
    if not isinstance(versions, list):
        return None

    for version_entry in versions:
        if isinstance(version_entry, dict) and version_entry.get("version") == library_version:
            return version_entry

    if library_version != metadata_version:
        for version_entry in versions:
            if isinstance(version_entry, dict) and version_entry.get("version") == metadata_version:
                return version_entry

    return None
