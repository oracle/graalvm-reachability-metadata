# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

# The deep phase this prepares for: §AR-code-coverage-improvement.4.2. Running
# once between the phases as a deterministic program whose exit code is the
# state transition: §AR-code-coverage-improvement.5 item 5.

"""
Native-metadata preparation for the code coverage improvement workflow.

Runs once between public API coverage and sampled-PGO deep discovery, driving
the shared native trace gate (§FS-native-test-verification-gate) with the
coverage suite merged into every Gradle command. Durable metadata is written
only when the gate's finalized re-run passes. Exit codes: 0 gate passed,
2 invocation error, 3 gate failed (routes to human intervention).

Usage:
  python3 utility_scripts/code_coverage_prepare_native_metadata.py \
    --repo-path <worktree> --coordinate group:artifact:version \
    --coverage-suite <indexed test project>/code-coverage-improvement \
    --output-dir runtime/code-coverage/prepare [--max-cycles 40] [--skip-gradle]
"""

from __future__ import annotations

import argparse
import json
import os
import sys

from utility_scripts.native_test_verification import (
    NativeTestVerificationResult,
    STATUS_FAILED,
    verify_native_test_passes,
)

COVERAGE_SUITE_PROPERTY: str = "-PincludeCodeCoverageSuite=true"
DEFAULT_MAX_CYCLES: int = 40


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


def prepare_native_metadata(
        repo_path: str,
        coordinate: str,
        coverage_suite: str,
        output_dir: str,
        max_cycles: int,
        skip_gradle: bool,
) -> dict:
    """Run the native trace gate with the coverage suite included end to end."""
    _validate_coordinate(coordinate)
    if type(max_cycles) is not int or max_cycles < 1:
        raise NativeMetadataPreparationError(
            f"max_cycles must be a positive integer; got '{max_cycles}'."
        )
    suite_path: str = _normalize_coverage_suite(repo_path, coverage_suite)
    report: dict = {
        "coordinate": coordinate,
        "coverageSuite": suite_path,
        "metadataGenerated": False,
        "nativeTestPassed": False,
        "gateStatus": None,
        "gateCycles": 0,
        "agentInterventions": 0,
        "needsHumanIntervention": False,
        "failureReason": None,
        "lastNativeTestLog": None,
    }
    if skip_gradle:
        _write_reports(report, output_dir)
        return report

    gate_output_dir: str = os.path.join(os.path.abspath(output_dir), "native-gate")
    result: NativeTestVerificationResult = verify_native_test_passes(
        reachability_repo_path=os.path.abspath(repo_path),
        coordinate=coordinate,
        output_dir=gate_output_dir,
        max_iterations=max_cycles,
        gradle_properties=(COVERAGE_SUITE_PROPERTY,),
    )
    passed: bool = result.status != STATUS_FAILED
    report["metadataGenerated"] = passed
    report["nativeTestPassed"] = passed
    report["gateStatus"] = result.status
    report["gateCycles"] = result.iterations_used
    report["agentInterventions"] = len(result.intervention_records)
    report["needsHumanIntervention"] = not passed
    report["failureReason"] = result.failure_detail if not passed else None
    report["lastNativeTestLog"] = result.last_native_test_log_path
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
        f"- Gate status: {report['gateStatus'] or 'not run'}",
        f"- Gate cycles: {report['gateCycles']}",
        f"- Analysis-agent interventions: {report['agentInterventions']}",
        f"- Needs human intervention: {report['needsHumanIntervention']}",
        f"- Failure reason: {report['failureReason'] or 'none'}",
        f"- Last native-test log: {report['lastNativeTestLog'] or 'none'}",
    ]
    with open(md_path, "w", encoding="utf-8") as md_file:
        md_file.write("\n".join(lines) + "\n")


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Prepare native metadata through the native trace gate before PGO discovery."
    )
    parser.add_argument("--repo-path", required=True, help="Issue worktree / repo root.")
    parser.add_argument("--coordinate", required=True, help="group:artifact:version.")
    parser.add_argument(
        "--coverage-suite",
        required=True,
        help="Dedicated code coverage suite root containing src/test/java.",
    )
    parser.add_argument("--output-dir", required=True, help="Directory for preparation artifacts.")
    parser.add_argument(
        "--max-cycles",
        type=int,
        default=DEFAULT_MAX_CYCLES,
        help="Maximum native trace gate cycles.",
    )
    parser.add_argument("--skip-gradle", action="store_true", help="Write a no-op report without running Gradle.")
    args = parser.parse_args()

    try:
        report: dict = prepare_native_metadata(
            repo_path=args.repo_path,
            coordinate=args.coordinate,
            coverage_suite=args.coverage_suite,
            output_dir=args.output_dir,
            max_cycles=args.max_cycles,
            skip_gradle=args.skip_gradle,
        )
    except NativeMetadataPreparationError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(2) from error
    print(
        f"Native metadata prepare: gate={report['gateStatus'] or 'skipped'} "
        f"cycles={report['gateCycles']} interventions={report['agentInterventions']}."
    )
    if report["needsHumanIntervention"]:
        print("The native trace gate could not converge; "
              "route to human intervention.", file=sys.stderr)
        raise SystemExit(3)


if __name__ == "__main__":
    main()
