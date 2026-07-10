# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""
Native-metadata preparation for the code coverage improvement workflow
(§WF-code-coverage-improvement, §WF-code-coverage-improvement-architecture).

This runs once between the JVM JaCoCo phase (phase 5) and the sampled-PGO
discovery phase (phase 6). The PGO phase builds a Native Image, which needs
valid reachability metadata for the newly generated code coverage tests; the
JaCoCo phase deliberately stays JVM-only. Mirroring how the other workflows
finalize, this helper generates metadata and then repairs it with the
Codex-backed `fix-missing-reachability-metadata` skill until a Native Image test
passes, so the three PGO-sampling builds do not each have to discover and
repair metadata gaps.

Loop: `generateMetadata` -> `nativeTest`; while it fails and a fix budget
remains, `run_codex_metadata_fix` then re-run `nativeTest`. If Native Image
validation still cannot pass automatically, the run is flagged
`needsHumanIntervention` (exit code 3) so the reviewed Rhei task routes to
human intervention.

Usage:
  python3 utility_scripts/code_coverage_prepare_native_metadata.py \
    --repo-path <worktree> --coordinate group:artifact:version \
    --output-dir runtime/code-coverage/prepare [--max-fix-passes 2] [--skip-gradle]
"""

from __future__ import annotations

import argparse
import json
import os
import sys

from ai_workflows.core.fix_metadata_codex import run_codex_metadata_fix
from utility_scripts.gradle_test_runner import run_gradle_test_command


def _gradle_succeeded(output: str) -> bool:
    return "BUILD SUCCESSFUL" in output


def prepare_native_metadata(repo_path: str, coordinate: str, output_dir: str,
                            max_fix_passes: int, skip_gradle: bool) -> dict:
    """Generate metadata and repair it until Native Image validation passes."""
    steps: list[dict] = []
    report = {
        "coordinate": coordinate,
        "metadataGenerated": False,
        "nativeTestPassed": False,
        "fixPasses": 0,
        "needsHumanIntervention": False,
        "steps": steps,
    }
    if skip_gradle:
        _write_reports(report, output_dir)
        return report

    native_command = f"./gradlew nativeTest -Pcoordinates={coordinate} --stacktrace"

    generate_output = run_gradle_test_command(
        f"./gradlew generateMetadata -Pcoordinates={coordinate} --stacktrace",
        working_dir=repo_path, library=coordinate,
    )
    report["metadataGenerated"] = _gradle_succeeded(generate_output)
    steps.append({"task": "generateMetadata", "succeeded": report["metadataGenerated"]})

    native_output = run_gradle_test_command(native_command, working_dir=repo_path, library=coordinate)
    passed = _gradle_succeeded(native_output)
    steps.append({"task": "nativeTest", "attempt": 0, "succeeded": passed})

    fix_passes = 0
    while not passed and fix_passes < max_fix_passes:
        fix_passes += 1
        return_code, _log_path, _timed_out = run_codex_metadata_fix(
            repo_path, coordinate, reproduction_command=native_command,
        )
        steps.append({"task": "codexMetadataFix", "pass": fix_passes, "returncode": return_code})
        native_output = run_gradle_test_command(native_command, working_dir=repo_path, library=coordinate)
        passed = _gradle_succeeded(native_output)
        steps.append({"task": "nativeTest", "attempt": fix_passes, "succeeded": passed})

    report["fixPasses"] = fix_passes
    report["nativeTestPassed"] = passed
    report["needsHumanIntervention"] = not passed
    _write_reports(report, output_dir)
    return report


def _write_reports(report: dict, output_dir: str) -> None:
    os.makedirs(output_dir, exist_ok=True)
    json_path = os.path.join(output_dir, "native-metadata-prepare.json")
    md_path = os.path.join(output_dir, "native-metadata-prepare.md")
    with open(json_path, "w", encoding="utf-8") as json_file:
        json.dump(report, json_file, indent=2)
        json_file.write("\n")
    lines = [
        f"# Native metadata preparation — {report['coordinate']}",
        "",
        f"- Metadata generated: {report['metadataGenerated']}",
        f"- Native Image test passed: {report['nativeTestPassed']}",
        f"- Codex metadata fix passes: {report['fixPasses']}",
        f"- Needs human intervention: {report['needsHumanIntervention']}",
        "",
        "## Steps",
        "",
    ]
    for step in report["steps"]:
        lines.append(f"- {json.dumps(step)}")
    with open(md_path, "w", encoding="utf-8") as md_file:
        md_file.write("\n".join(lines) + "\n")


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate and repair native metadata before PGO discovery.")
    parser.add_argument("--repo-path", required=True, help="Issue worktree / repo root.")
    parser.add_argument("--coordinate", required=True, help="group:artifact:version.")
    parser.add_argument("--output-dir", required=True, help="Directory for preparation artifacts.")
    parser.add_argument("--max-fix-passes", type=int, default=2, help="Maximum Codex metadata fix passes.")
    parser.add_argument("--skip-gradle", action="store_true", help="Write a no-op report without running Gradle.")
    args = parser.parse_args()

    report = prepare_native_metadata(
        repo_path=args.repo_path,
        coordinate=args.coordinate,
        output_dir=args.output_dir,
        max_fix_passes=args.max_fix_passes,
        skip_gradle=args.skip_gradle,
    )
    print(f"Native metadata prepare: generated={report['metadataGenerated']} "
          f"nativeTestPassed={report['nativeTestPassed']} fixPasses={report['fixPasses']}.")
    if report["needsHumanIntervention"]:
        print("Native Image metadata could not be repaired automatically; "
              "route to human intervention.", file=sys.stderr)
        raise SystemExit(3)


if __name__ == "__main__":
    main()
