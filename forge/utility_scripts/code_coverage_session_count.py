# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""
Count library coverage after each deep cover session (§AR-code-coverage-trajectory.4).

The dispatch state marks the session it hands out. On its next visit, once that
session has returned, it runs the combined JaCoCo report alone and writes the
covered count as `session-coverage-<iteration>.json`, which Rhei records as the
`deep-session-coverage` metric. The first dispatch of the deep phase writes the
baseline from the JaCoCo XML the first deep measurement copied. A count that
fails writes nothing and never stops dispatch: the pass measurement, not this
count, decides the loop.
"""

from __future__ import annotations

import glob
import json
import os
import re
import subprocess
import sys

from utility_scripts.code_coverage_jacoco import load_jacoco_method_coverage
from utility_scripts.code_coverage_naive import summarize_universe
from utility_scripts.code_coverage_validate import find_jacoco_reports

#: The session dispatch handed out last and whose coverage is not counted yet.
SESSION_MARKER: str = "deep-session-running.json"
#: The per-session count Rhei reads, numbered by its iteration counter.
COUNT_STEM: str = "session-coverage"


def mark_session(discovery_dir: str, session: dict) -> None:
    """Record the session just handed out, so the next dispatch counts it."""
    with open(os.path.join(discovery_dir, SESSION_MARKER), "w", encoding="utf-8") as marker:
        json.dump(session, marker, indent=2)


def next_iteration(discovery_dir: str) -> int:
    """Rhei's next iteration of this metric, or the count files written so far."""
    from_rhei: str | None = os.environ.get("RHEI_ITERATION")
    if from_rhei is not None and from_rhei.isdigit():
        return int(from_rhei)
    return len(glob.glob(os.path.join(discovery_dir, f"{COUNT_STEM}-*.json")))


def latest_measured_xml(discovery_dir: str) -> str | None:
    """The JaCoCo XML of the newest deep measurement."""
    numbered: list[tuple[int, str]] = []
    for path in glob.glob(os.path.join(discovery_dir, "jacoco-deep-*.xml")):
        match: re.Match[str] | None = re.search(r"-(\d+)\.xml$", path)
        if match:
            numbered.append((int(match.group(1)), path))
    return max(numbered)[1] if numbered else None


def run_jacoco_report(worktree: str, coordinate: str) -> str | None:
    """Run the combined JaCoCo report alone; return its XML, or None if it failed."""
    command: list[str] = [
        f"{worktree}/gradlew", f"-Pcoordinates={coordinate}", "jacocoCodeCoverageReport",
    ]
    print(f"[deep-dispatch] {' '.join(command)}", flush=True)
    if subprocess.run(command, cwd=worktree).returncode != 0:
        return None
    group, artifact, version = coordinate.split(":")
    reports: list[str] = find_jacoco_reports(worktree, group, artifact, version)
    return reports[0] if reports else None


def write_count(
        discovery_dir: str,
        iteration: int,
        jacoco_xml: str,
        session: dict | None,
) -> dict:
    """Write one `session-coverage-<iteration>.json` from a JaCoCo XML."""
    count: dict = {
        "iteration": iteration,
        "session": session,
        "jacoco": jacoco_xml,
        "summary": summarize_universe(load_jacoco_method_coverage([jacoco_xml])),
    }
    path: str = os.path.join(discovery_dir, f"{COUNT_STEM}-{iteration}.json")
    with open(path, "w", encoding="utf-8") as count_file:
        json.dump(count, count_file, indent=2)
        count_file.write("\n")
    return count


def count_session(discovery_dir: str, worktree: str, coordinate: str) -> dict | None:
    """Count the coverage the returned session left, or the phase baseline."""
    marker_path: str = os.path.join(discovery_dir, SESSION_MARKER)
    iteration: int = next_iteration(discovery_dir)
    try:
        if os.path.isfile(marker_path):
            with open(marker_path, encoding="utf-8") as marker:
                session: dict | None = json.load(marker)
            os.remove(marker_path)
            jacoco_xml: str | None = run_jacoco_report(worktree, coordinate)
        elif iteration == 0:
            session = None
            jacoco_xml = latest_measured_xml(discovery_dir)
        else:
            return None
        if jacoco_xml is None:
            print("[deep-dispatch] session count skipped: no JaCoCo report", file=sys.stderr)
            return None
        count: dict = write_count(discovery_dir, iteration, jacoco_xml, session)
    except Exception as error:
        print(f"[deep-dispatch] session count skipped: {error}", file=sys.stderr)
        return None
    summary: dict = count["summary"]
    print(
        f"[deep-dispatch] count {iteration}: {summary['covered']}/{summary['total']} "
        "library methods covered",
        flush=True,
    )
    return count
