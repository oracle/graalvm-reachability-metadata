# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Maven coordinate extraction from issue titles
(§AR-forge-dispatcher-decomposition, §FS-forge-issue-resolution-goal)."""


import json
import os
import re
import sys
from typing import Optional
from dispatcher.human_intervention import repo_relative_path

def extract_coordinate_parts(title: str) -> Optional[tuple[str, str, str]]:
    """
    Extract Maven coordinate parts (groupId, artifactId, version) from an issue title.
    """
    match = re.search(r'([\w.\-]+):([\w.\-]+):([\w.\-]+)', title)
    if match:
        return match.group(1), match.group(2), match.group(3)
    return None


def extract_maven_coordinates(title: str) -> Optional[str]:
    """
    Extract Maven coordinates (groupId:artifactId:version) from an issue title.
    Returns the coordinates string or None if not found.
    """
    coordinate_parts = extract_coordinate_parts(title)
    if coordinate_parts:
        return ":".join(coordinate_parts)
    return None


def load_current_metadata_version(
        reachability_metadata_path: str,
        group: str,
        artifact: str,
        report_errors: bool = True,
) -> Optional[str]:
    """Load the current metadata version from the latest index.json entry."""
    index_json_path = os.path.join(
        reachability_metadata_path,
        "metadata",
        group,
        artifact,
        "index.json",
    )
    index_json_path_display = repo_relative_path(index_json_path, reachability_metadata_path)
    if not os.path.isfile(index_json_path):
        if report_errors:
            print(f"ERROR: Missing metadata index file: {index_json_path_display}", file=sys.stderr)
        return None

    with open(index_json_path, "r", encoding="utf-8") as index_file:
        index_entries = json.load(index_file)

    for entry in index_entries:
        if entry.get("latest") is True:
            return entry.get("test-version") or entry.get("metadata-version")

    if report_errors:
        print(
            f"ERROR: No latest entry found in metadata index: {index_json_path_display}",
            file=sys.stderr,
        )
    return None
