# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""
JaCoCo XML coverage parser.
Parses lines coverage from JaCoCo XML and aggregates all <counter type='LINE'> elements.

Usage:
  python3 utility_scripts/jacoco_parser.py <path-to-jacoco-xml>
"""

import os
import sys
import xml.etree.ElementTree as ET


def compute_line_coverage(xml_path: str):
    """
    Aggregate line counters from a JaCoCo XML report and return coverage percent.
    """
    try:
        tree = ET.parse(xml_path)
        root = tree.getroot()
    except Exception:
        return 0.0, 0

    covered_total = 0
    missed_total = 0

    for el in root.findall(".//counter[@type='LINE']"):
        try:
            covered = int(el.get("covered", "0"))
            missed = int(el.get("missed", "0"))
        except ValueError:
            covered = 0
            missed = 0
        covered_total += covered
        missed_total += missed

    denom = covered_total + missed_total

    return (covered_total / denom) * 100.0, covered_total


def main() -> None:
    if len(sys.argv) < 2:
        print("0.0")
        return

    xml_path = sys.argv[1]
    if not os.path.isfile(xml_path):
        print("0.0")
        return

    percent, covered_total = compute_line_coverage(xml_path)

    try:
        if percent != percent or percent in (float("inf"), float("-inf")):
            percent = 0.0
    except Exception:
        percent = 0.0

    print(covered_total)
    print(f"{percent:.2f}")


if __name__ == "__main__":
    main()
