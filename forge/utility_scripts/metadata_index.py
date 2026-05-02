# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Helpers for metadata/<group>/<artifact>/index.json files."""

import json
import os
import sys
from typing import Any


NOT_FOR_NATIVE_IMAGE_FIELD = "not-for-native-image"


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
