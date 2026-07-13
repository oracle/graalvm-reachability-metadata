# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""
JVM coverage validator for the code coverage improvement workflow
(§WF-code-coverage-improvement.3.1, §WF-code-coverage-improvement-architecture).

Public-API phase helper. It compiles the tracked code-coverage-improvement
extension suite and runs the regular plus extension JVM tests under JaCoCo
(genuine Java-agent instrumentation, not the rejected PGO `.exec` path), and
correlates exact JaCoCo method coverage against the phase-3 API inventory to
compute which public-API targets remain uncovered after the iteration. It
writes the per-iteration JaCoCo report copy and the remaining-uncovered-API
report that feeds the next API-cover pass.

Native Image and reachability-metadata concerns are intentionally NOT here:
JaCoCo coverage measurement needs only the JVM. Metadata is generated and
repaired once before the PGO discovery phase by
`code_coverage_prepare_native_metadata.py`, so the PGO-sampling native build can
succeed without rebuilding a native image on every API-cover iteration.

The deterministic, library-execution-free core is `correlate_jacoco`: it joins
JaCoCo `<method>` entries against API inventory target ids. The Gradle steps are
thin wrappers over the existing harness tasks (`compileTestJava`,
`codeCoverageTest`, `jacocoCodeCoverageReport`). The extension suite lives at
the tracked `code-coverage-improvement/` directory inside the coordinate's
indexed test project (§forge/WF-code-coverage-improvement.3.1).

Usage:
  python3 utility_scripts/code_coverage_validate.py \
    --repo-path <worktree> --coordinate group:artifact:version \
    --api-inventory <api-inventory.json> \
    --iteration 1 \
    --output-dir runtime/code-coverage/validation [--skip-gradle]
"""

from __future__ import annotations

import argparse
import json
import os
import shlex
import shutil
import sys

from utility_scripts.code_coverage_jacoco import (
    JacocoMethodCoverage,
    JacocoReportError,
    load_jacoco_method_coverage,
)
from utility_scripts.code_coverage_model import MethodRef, parse_inventory_id
from utility_scripts.gradle_test_runner import run_gradle_test_command
from utility_scripts.metadata_index import resolve_test_dir


def correlate_jacoco(inventory: dict, jacoco_xml_paths: list[str]) -> dict:
    """Classify public API methods from exact JaCoCo evidence only.

    Sampled evidence and loose owner/name/arity keys cannot affect phase-one
    coverage (§WF-code-coverage-improvement.3.1).
    """
    coverage_by_id: dict[str, JacocoMethodCoverage] = load_jacoco_method_coverage(
        jacoco_xml_paths
    )

    targets: list[dict] = []
    covered_count: int = 0
    uncovered_count: int = 0
    not_reported_count: int = 0
    for target in inventory.get("targets", []):
        target_id: str = target.get("id", "")
        ref: MethodRef | None = parse_inventory_id(target_id)
        if ref is None:
            raise JacocoReportError(
                f"API inventory target is not a canonical method or constructor id: '{target_id}'."
            )
        coverage: JacocoMethodCoverage | None = coverage_by_id.get(ref.canonical_id)
        entry: dict = {
            "id": ref.canonical_id,
            "kind": target.get("kind"),
        }
        if coverage is None:
            not_reported_count += 1
            entry.update({
                "status": "not-reported",
                "sourcePath": target.get("sourcePath"),
                "sourceLine": target.get("sourceLine"),
                "evidence": [],
                "reason": "No exact method record exists in the JaCoCo report.",
            })
        else:
            entry.update({
                "status": coverage.status,
                "sourcePath": coverage.source_path,
                "sourceLine": coverage.source_line,
                "evidence": ["jacoco"],
            })
            if coverage.covered:
                covered_count += 1
            else:
                uncovered_count += 1
        targets.append(entry)

    measured_count: int = covered_count + uncovered_count
    return {
        "coordinate": inventory.get("coordinate"),
        "summary": {
            "total": len(targets),
            "measured": measured_count,
            "covered": covered_count,
            "uncovered": uncovered_count,
            "notReported": not_reported_count,
            "coveragePercent": (
                round(100.0 * covered_count / measured_count, 2) if measured_count else 0.0
            ),
        },
        "targets": targets,
    }


def find_jacoco_reports(repo_path: str, group: str, artifact: str, version: str) -> list[str]:
    """Locate the one deterministic combined report in the coordinate's indexed test project."""
    module_dir: str = resolve_test_dir(repo_path, group, artifact, version)
    report_path: str = os.path.join(
        module_dir, "build", "reports", "jacoco",
        "jacocoCodeCoverageReport", "jacocoCodeCoverageReport.xml",
    )
    return [report_path] if os.path.isfile(report_path) else []


def resolve_coverage_suite_dir(repo_path: str, group: str, artifact: str, version: str) -> str:
    """Return the tracked extension-suite directory inside the indexed test project."""
    module_dir: str = resolve_test_dir(repo_path, group, artifact, version)
    return os.path.join(module_dir, "code-coverage-improvement")


def _gradle_step(
        repo_path: str,
        task: str,
        coordinate: str,
) -> dict:
    coordinate_arg: str = shlex.quote(f"-Pcoordinates={coordinate}")
    command: str = f"./gradlew {task} {coordinate_arg} --stacktrace"
    output: str = run_gradle_test_command(
        command,
        working_dir=repo_path,
        library=coordinate,
    )
    succeeded: bool = "BUILD SUCCESSFUL" in output
    return {
        "task": task,
        "command": command,
        "succeeded": succeeded,
        "tail": output[-2000:] if not succeeded else "",
    }


def write_reports(
        report: dict,
        gradle_steps: list[dict],
        iteration: int,
        output_dir: str,
        jacoco_xml_paths: list[str],
) -> None:
    os.makedirs(output_dir, exist_ok=True)
    report = dict(report)
    report["iteration"] = iteration
    report["gradle"] = gradle_steps
    json_path: str = os.path.join(output_dir, f"api-cover-report-{iteration}.json")
    md_path: str = os.path.join(output_dir, f"api-cover-report-{iteration}.md")
    with open(json_path, "w", encoding="utf-8") as json_file:
        json.dump(report, json_file, indent=2)
        json_file.write("\n")

    shutil.copy2(jacoco_xml_paths[0], os.path.join(output_dir, f"jacoco-{iteration}.xml"))

    summary: dict = report["summary"]
    lines: list[str] = [
        f"# API cover report {iteration} — {report.get('coordinate')}",
        "",
        f"- JVM JaCoCo coverage of reported methods: {summary['covered']}/{summary['measured']} "
        f"({summary['coveragePercent']}%)",
        f"- API methods absent from the JaCoCo report: {summary['notReported']}",
        "",
        "## Gradle validation steps",
        "",
    ]
    for step in gradle_steps:
        lines.append(f"- `{step['task']}`: {'ok' if step['succeeded'] else 'FAILED'}")
    lines += ["", "## Still-uncovered public API targets", ""]
    uncovered: list[dict] = [
        target for target in report["targets"] if target["status"] == "uncovered"
    ]
    if not uncovered:
        lines.append("_All JaCoCo-reported API methods are covered by the JVM suite._")
    for target in uncovered:
        lines.append(f"- `{target['id']}` ({target['kind']})")

    not_reported: list[dict] = [
        target for target in report["targets"] if target["status"] == "not-reported"
    ]
    if not_reported:
        lines += ["", "## API methods without exact JaCoCo evidence", ""]
        lines += [f"- `{target['id']}` ({target['kind']})" for target in not_reported]

    with open(md_path, "w", encoding="utf-8") as md_file:
        md_file.write("\n".join(lines) + "\n")


def run_validation(
        repo_path: str,
        coordinate: str,
        api_inventory_path: str,
        iteration: int,
        output_dir: str,
        skip_gradle: bool,
        coverage_suite_path: str | None = None,
) -> dict:
    coordinate_parts: list[str] = coordinate.split(":")
    if len(coordinate_parts) != 3 or any(not part for part in coordinate_parts):
        raise ValueError(
            f"Coordinate must use non-empty group:artifact:version form; got '{coordinate}'."
        )
    group: str
    artifact: str
    version: str
    group, artifact, version = coordinate_parts
    try:
        with open(api_inventory_path, "r", encoding="utf-8") as inventory_file:
            inventory: dict = json.load(inventory_file)
    except (OSError, json.JSONDecodeError) as error:
        raise ValueError(f"Cannot read API inventory '{api_inventory_path}': {error}") from error
    inventory_coordinate: str | None = inventory.get("coordinate")
    if inventory_coordinate != coordinate:
        raise ValueError(
            f"API inventory coordinate '{inventory_coordinate}' does not match '{coordinate}'."
        )

    gradle_steps: list[dict] = []
    if not skip_gradle:
        resolved_suite_path: str = os.path.abspath(
            coverage_suite_path
            or resolve_coverage_suite_dir(repo_path, group, artifact, version)
        )
        if coverage_suite_path is not None and not os.path.isdir(resolved_suite_path):
            raise ValueError(f"Code coverage suite does not exist: '{resolved_suite_path}'.")
        for task in ("compileTestJava", "codeCoverageTest", "jacocoCodeCoverageReport"):
            step: dict = _gradle_step(
                repo_path, task, coordinate,
            )
            gradle_steps.append(step)
            if not step["succeeded"]:
                raise JacocoReportError(
                    f"Gradle task '{task}' failed; refusing to reuse existing JaCoCo XML. "
                    f"Command: {step['command']}"
                )

    jacoco_xml_paths: list[str] = find_jacoco_reports(
        repo_path,
        group,
        artifact,
        version,
    )
    report: dict = correlate_jacoco(inventory, jacoco_xml_paths)
    write_reports(report, gradle_steps, iteration, output_dir, jacoco_xml_paths)
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description="Validate JVM coverage against the API inventory.")
    parser.add_argument("--repo-path", required=True, help="Issue worktree / repo root.")
    parser.add_argument("--coordinate", required=True, help="group:artifact:version.")
    parser.add_argument("--api-inventory", required=True, help="api-inventory.json path.")
    parser.add_argument(
        "--coverage-suite",
        default=None,
        help="Extension suite root; defaults to <indexed test project>/code-coverage-improvement.",
    )
    parser.add_argument("--iteration", type=int, default=1, help="API-cover iteration number.")
    parser.add_argument("--output-dir", required=True, help="Directory for validation artifacts.")
    parser.add_argument(
        "--skip-gradle",
        action="store_true",
        help="Only correlate existing JaCoCo reports; do not run Gradle.",
    )
    args = parser.parse_args()

    try:
        report: dict = run_validation(
            repo_path=args.repo_path,
            coordinate=args.coordinate,
            api_inventory_path=args.api_inventory,
            iteration=args.iteration,
            output_dir=args.output_dir,
            skip_gradle=args.skip_gradle,
            coverage_suite_path=args.coverage_suite,
        )
    except (JacocoReportError, ValueError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(2) from error
    summary: dict = report["summary"]
    print(
        f"JVM coverage {summary['covered']}/{summary['measured']} "
        f"({summary['coveragePercent']}%); uncovered {summary['uncovered']}; "
        f"not reported {summary['notReported']}."
    )


if __name__ == "__main__":
    main()
