# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""
Native-metadata preparation for the code coverage improvement workflow
(§WF-code-coverage-improvement.3.2,
§WF-code-coverage-improvement-architecture).

This runs once between public API coverage and sampled-PGO deep discovery. The
deep phase builds Native Images from the dedicated code coverage suite, which
needs valid reachability metadata; the public JaCoCo phase deliberately stays
JVM-only. Mirroring how the other workflows finalize, this helper generates
metadata and then repairs it with the Codex-backed
`fix-missing-reachability-metadata` skill until a Native Image test passes, so
the six deep collections (one baseline and five post-iteration reports) do not
each have to discover and repair metadata gaps.

Loop: `generateMetadata` -> `nativeTest`; while it fails and a fix budget
remains, `run_codex_metadata_fix` then re-run `nativeTest`. If Native Image
validation still cannot pass automatically, the run is flagged
`needsHumanIntervention` (exit code 3) so the reviewed Rhei task routes to
human intervention. A failed `generateMetadata` stops immediately instead of
validating or repairing stale metadata.

Usage:
  python3 utility_scripts/code_coverage_prepare_native_metadata.py \
    --repo-path <worktree> --coordinate group:artifact:version \
    --coverage-suite tests/<group>/<artifact>/<version>/code-coverage \
    --output-dir runtime/code-coverage/prepare [--max-fix-passes 2] [--skip-gradle]
"""

from __future__ import annotations

import argparse
import json
import os
import shlex
import sys

from ai_workflows.core.fix_metadata_codex import run_codex_metadata_fix
from utility_scripts.gradle_test_runner import run_gradle_test_command


def _gradle_succeeded(output: str) -> bool:
    return "BUILD SUCCESSFUL" in output


class NativeMetadataPreparationError(ValueError):
    """Raised when deterministic preparation inputs violate the workflow contract."""


def _validate_coordinate(coordinate: str) -> None:
    if not isinstance(coordinate, str):
        raise NativeMetadataPreparationError(
            f"Coordinate must use non-empty group:artifact:version form; got '{coordinate}'."
        )
    parts: list[str] = coordinate.split(":")
    if (
            len(parts) != 3
            or any(not part or any(char.isspace() for char in part) for part in parts)
    ):
        raise NativeMetadataPreparationError(
            f"Coordinate must use non-empty group:artifact:version form; got '{coordinate}'."
        )


def _normalize_coverage_suite(repo_path: str, coverage_suite: str) -> str:
    if not isinstance(coverage_suite, str) or not coverage_suite.strip():
        raise NativeMetadataPreparationError("Coverage suite path is required.")
    expanded_path: str = os.path.expanduser(coverage_suite)
    if not os.path.isabs(expanded_path):
        expanded_path = os.path.join(os.path.abspath(repo_path), expanded_path)
    suite_path: str = os.path.abspath(expanded_path)
    if not os.path.isdir(suite_path):
        raise NativeMetadataPreparationError(
            f"Coverage suite directory does not exist: '{suite_path}'."
        )
    java_sources: str = os.path.join(suite_path, "src", "test", "java")
    if not os.path.isdir(java_sources):
        raise NativeMetadataPreparationError(
            f"Coverage suite '{suite_path}' must contain 'src/test/java'."
        )
    return suite_path


def _gradle_command(task: str, coordinate: str, coverage_suite: str) -> str:
    coordinate_arg: str = shlex.quote(f"-Pcoordinates={coordinate}")
    suite_arg: str = shlex.quote(f"-PcodeCoverageSuitePath={coverage_suite}")
    return f"./gradlew {task} {coordinate_arg} {suite_arg} --stacktrace"


def prepare_native_metadata(
        repo_path: str,
        coordinate: str,
        coverage_suite: str,
        output_dir: str,
        max_fix_passes: int,
        skip_gradle: bool,
) -> dict:
    """Generate metadata and repair it until Native Image validation passes."""
    _validate_coordinate(coordinate)
    if type(max_fix_passes) is not int or max_fix_passes < 0:
        raise NativeMetadataPreparationError(
            f"max_fix_passes must be a non-negative integer; got '{max_fix_passes}'."
        )
    suite_path: str = _normalize_coverage_suite(repo_path, coverage_suite)
    steps: list[dict] = []
    report: dict = {
        "coordinate": coordinate,
        "coverageSuite": suite_path,
        "metadataGenerated": False,
        "nativeTestPassed": False,
        "fixPasses": 0,
        "needsHumanIntervention": False,
        "failureReason": None,
        "steps": steps,
    }
    if skip_gradle:
        _write_reports(report, output_dir)
        return report

    generate_command: str = _gradle_command("generateMetadata", coordinate, suite_path)
    native_command: str = _gradle_command("nativeTest", coordinate, suite_path)

    generate_output: str = run_gradle_test_command(
        generate_command,
        working_dir=repo_path, library=coordinate,
    )
    report["metadataGenerated"] = _gradle_succeeded(generate_output)
    steps.append({
        "task": "generateMetadata",
        "command": generate_command,
        "succeeded": report["metadataGenerated"],
    })
    if not report["metadataGenerated"]:
        report["needsHumanIntervention"] = True
        report["failureReason"] = (
            "Metadata generation failed; native validation was not run against stale metadata."
        )
        _write_reports(report, output_dir)
        return report

    native_output: str = run_gradle_test_command(
        native_command,
        working_dir=repo_path,
        library=coordinate,
    )
    passed: bool = _gradle_succeeded(native_output)
    steps.append({
        "task": "nativeTest",
        "command": native_command,
        "attempt": 0,
        "succeeded": passed,
    })

    fix_passes: int = 0
    while not passed and fix_passes < max_fix_passes:
        fix_passes += 1
        return_code, _log_path, _timed_out = run_codex_metadata_fix(
            repo_path, coordinate, reproduction_command=native_command,
        )
        steps.append({"task": "codexMetadataFix", "pass": fix_passes, "returncode": return_code})
        native_output = run_gradle_test_command(
            native_command,
            working_dir=repo_path,
            library=coordinate,
        )
        passed = _gradle_succeeded(native_output)
        steps.append({
            "task": "nativeTest",
            "command": native_command,
            "attempt": fix_passes,
            "succeeded": passed,
        })

    report["fixPasses"] = fix_passes
    report["nativeTestPassed"] = passed
    report["needsHumanIntervention"] = not passed
    if not passed:
        report["failureReason"] = "Native Image validation did not pass within the fix budget."
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
        f"- Coverage suite: `{report['coverageSuite']}`",
        f"- Metadata generated: {report['metadataGenerated']}",
        f"- Native Image test passed: {report['nativeTestPassed']}",
        f"- Codex metadata fix passes: {report['fixPasses']}",
        f"- Needs human intervention: {report['needsHumanIntervention']}",
        f"- Failure reason: {report['failureReason'] or 'none'}",
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
    parser.add_argument(
        "--coverage-suite",
        required=True,
        help="Dedicated code coverage suite root containing src/test/java.",
    )
    parser.add_argument("--output-dir", required=True, help="Directory for preparation artifacts.")
    parser.add_argument("--max-fix-passes", type=int, default=2, help="Maximum Codex metadata fix passes.")
    parser.add_argument("--skip-gradle", action="store_true", help="Write a no-op report without running Gradle.")
    args = parser.parse_args()

    try:
        report: dict = prepare_native_metadata(
            repo_path=args.repo_path,
            coordinate=args.coordinate,
            coverage_suite=args.coverage_suite,
            output_dir=args.output_dir,
            max_fix_passes=args.max_fix_passes,
            skip_gradle=args.skip_gradle,
        )
    except NativeMetadataPreparationError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(2) from error
    print(f"Native metadata prepare: generated={report['metadataGenerated']} "
          f"nativeTestPassed={report['nativeTestPassed']} fixPasses={report['fixPasses']}.")
    if report["needsHumanIntervention"]:
        print("Native Image metadata could not be repaired automatically; "
              "route to human intervention.", file=sys.stderr)
        raise SystemExit(3)


if __name__ == "__main__":
    main()
