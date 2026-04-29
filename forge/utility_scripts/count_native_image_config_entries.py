# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""
Generic counter for Native Image config-style JSON files.

Find and count entries across any JSON files under given paths that follow
the typical Native Image config pattern.
"""

import json
import os
import sys
import argparse
from typing import Any, Dict, List, Union


JsonType = Union[Dict[str, Any], List[Any], str, int, float, bool, None]


def find_json_files(paths: List[str]) -> List[str]:
    """
    Recursively find all .json files under the given paths, skipping any file named 'index.json'.
    If a path is a file and ends with .json (and is not named 'index.json'), include it directly.
    """
    found: List[str] = []
    for p in paths:
        if os.path.isdir(p):
            for root, _, files in os.walk(p):
                for fn in files:
                    if fn.lower() == "index.json":
                        continue
                    if fn.lower().endswith(".json"):
                        found.append(os.path.join(root, fn))
        elif os.path.isfile(p):
            if p.lower().endswith(".json") and os.path.basename(p).lower() != "index.json":
                found.append(p)
        else:
            # path doesn't exist, ignore
            pass
    # Deduplicate and sort for stable output
    return sorted(set(found))


def count_segment_entries(segment: Dict[str, Any]) -> int:
    """
    Counts number of entries of provided segment, ignoring 'condition'.
    Lists add len(list), non-lists add 1.
    """
    if not isinstance(segment, dict):
        return 0
    count = 0
    for key, value in segment.items():
        if key == "condition":
            continue
        if isinstance(value, list):
            count += len(value)
        else:
            count += 1
    return count


def traverse_and_count(node: JsonType) -> int:
    """
    Traverse arbitrary JSON data and count entries for any dict that
    contains a 'condition' key (treated as a segment).
    """
    total = 0
    if isinstance(node, dict):
        # If this dict is a 'segment', count it and do not descend further
        # to avoid double-counting its internal structures.
        if "condition" in node:
            return count_segment_entries(node)
        # Otherwise, traverse children
        for v in node.values():
            total += traverse_and_count(v)
    elif isinstance(node, list):
        for item in node:
            total += traverse_and_count(item)
    # Scalars contribute nothing
    return total


def count_entries_in_file(path: str) -> int:
    """
    Load JSON from a file and count entries by traversing the structure
    looking for dicts that contain 'condition'.
    """
    try:
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
    except Exception as e:
        print(f"Skipping {path}: failed to parse JSON ({e})", file=sys.stderr)
        return 0
    return traverse_and_count(data)


def build_parser() -> "argparse.ArgumentParser":
    parser = argparse.ArgumentParser(
        prog="count_native_image_config_entries.py",
        description=(
            "Generic counter for Native Image config-style JSON files.\n\n"
            "Counting rules:\n"
            "- Treat any dict that contains a 'condition' key as a segment.\n"
            "- For each segment, ignore the 'condition' property.\n"
            "- For each other property: count 1 for scalars, and N for a list of length N."
        ),
        epilog=(
            "Examples:\n"
            "  python3 utility_scripts/count_native_image_config_entries.py /path/to/graalvm-reachability-metadata/metadata/com.hazelcast/hazelcast/5.2.1\n"
        ),
        formatter_class=argparse.RawTextHelpFormatter,
    )

    return parser

def main():
    paths = sys.argv[1:]
    if not paths:
        print("Usage: python3 utility_scripts/count_native_image_config_entries.py <path ...>", file=sys.stderr)
        sys.exit(2)

    json_files = find_json_files(paths)

    if not json_files:
        print(f"No .json files found under: {', '.join(paths)}", file=sys.stderr)
        sys.exit(1)

    grand_total = 0
    files_with_segments = 0
    
    for path in json_files:
        count = count_entries_in_file(path)
        if count > 0:
            print(f"{path}: entries={count}")
            grand_total += count
            files_with_segments += 1

    print(f"total={grand_total}")


if __name__ == "__main__":
    # Provide a detailed help message without changing existing behavior.
    if any(a in ("-h", "--help") for a in sys.argv[1:]):
        build_parser().print_help()
        sys.exit(0)
    main()
