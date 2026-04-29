# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""
Count entries in reachability-metadata.json files.

Counting rules per segment:
- Ignore the "condition" property entirely
- For each other top-level property of a segment:
  - If the property's value is a list (e.g., "methods", "fields"), count 1 per element in that list.
  - Otherwise (e.g., "type", "allDeclaredFields", "jniAccessible"), count 1.

Example segment counting:
{
  "condition": {...},            # ignored
  "type": "com.example.Foo",     # +1
  "allDeclaredFields": true,     # +1
  "methods": [ {...} ]           # +2 (two element in the array)
}
Total for this segment: 4

Usage:
- python3 count_reachability_entries.py <path ...>
    For each provided path:
      * If it's a directory, recursively scans it for "reachability-metadata.json" files.
      * If it's a file, it is processed only if its basename is "reachability-metadata.json".
"""

import json
import os
import sys
import argparse
from typing import Any, Dict, List, Set


TARGET_BASENAME = "reachability-metadata.json"


def find_reachability_files(paths: List[str]) -> List[str]:
    found: List[str] = []
    for p in paths:
        if os.path.isdir(p):
            for root, _, files in os.walk(p):
                for fn in files:
                    if fn == TARGET_BASENAME:
                        found.append(os.path.join(root, fn))
        elif os.path.isfile(p):
            if os.path.basename(p) == TARGET_BASENAME:
                found.append(p)
        else:
            pass
    # Deduplicate and sort for stable output
    return sorted(set(found))


def count_segment_entries(segment: Dict[str, Any]) -> int:
    """
    Counts number of entries of provided segment.
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


def count_reachability_file(path: str) -> dict:
    """
    Returns a dictionary with keys: 'reflection', 'resources', 'total'.
    """
    try:
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
    except Exception as e:
        print(f"Skipping {path}: failed to parse JSON ({e})", file=sys.stderr)
        return {"reflection": 0, "resources": 0, "total": 0}

    reflection = data.get("reflection")
    resources = data.get("resources")
    reflection_count = 0
    resources_count = 0

    if isinstance(reflection, list):
        for segment in reflection:
            reflection_count += count_segment_entries(segment if isinstance(segment, dict) else {})

    if isinstance(resources, list):
        for segment in resources:
            resources_count += count_segment_entries(segment if isinstance(segment, dict) else {})

    total = reflection_count + resources_count

    return {
        "reflection": reflection_count,
        "resources": resources_count,
        "total": total
    }

def build_parser() :
    parser = argparse.ArgumentParser(
        prog="count_reachability_entries.py",
        description=(
            "Count entries in reachability-metadata.json files.\n\n"
            "Counting rules per segment:\n"
            "- Ignore the 'condition' property entirely\n"
            "- For each other top-level property of a segment:\n"
            "  - If the property's value is a list (e.g., 'methods', 'fields'), count 1 per element in that list.\n"
            "  - Otherwise (e.g., 'type', 'allDeclaredFields', 'jniAccessible'), count 1.\n\n"
            "Arguments:\n"
            "  paths: One or more paths containing the reachability-metadata.json files you want to count.\n"
            f"    - If a path is a directory, it is scanned recursively for files named '{TARGET_BASENAME}'.\n"      
            f"    - If a path is a file, it is processed only if its basename is '{TARGET_BASENAME}'.\n"
        ),
        epilog=(
            "Examples:\n"
            f"  python3 utility_scripts/count_reachability_entries.py /path/to/graalvm-reachability-metadata/metadata/com.hazelcast/hazelcast/5.2.1\n"
        ),
        formatter_class=argparse.RawTextHelpFormatter,
    )
    return parser


def main():
    paths = sys.argv[1:]
    reach_files = find_reachability_files(paths)

    if not reach_files:
        print(f"No '{TARGET_BASENAME}' files found under: {', '.join(paths)}", file=sys.stderr)
        sys.exit(1)

    reach_totals = {"reflection": 0, "resources": 0, "total": 0}

    for path in reach_files:
        counts = count_reachability_file(path)
        reach_totals["reflection"] += counts.get("reflection", 0)
        reach_totals["resources"] += counts.get("resources", 0)
        reach_totals["total"] += counts.get("total", 0)
        print(f"{path}: reflection={counts.get('reflection', 0)}  resources={counts.get('resources', 0)}  total={counts.get('total', 0)}")

if __name__ == "__main__":
    if any(a in ("-h", "--help") for a in sys.argv[1:]):
        build_parser().print_help()
        sys.exit(0)
    main()
