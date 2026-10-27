# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

# The benchmark baseline arm's measurement: the frozen JaCoCo method universe is
# the denominator and no target list is ever built: §AR-code-coverage-benchmarking.3.
# Arm-invariant instrumentation, naive prompt as the sole agent interface:
# §FS-code-coverage-benchmarking.7.

"""
Naive-baseline measurement for the code coverage benchmark.

`measure` runs the same Gradle JaCoCo steps as the guided validator and writes
the per-iteration naive cover report straight from the JaCoCo method universe,
with no API-inventory join, ranking, or call-graph input. `render` fills the
naive cover prompt template with the report's numbers — the only information
the baseline cover agent ever receives.
"""

from __future__ import annotations

import argparse
import json
import os
import shutil
import sys

from utility_scripts.code_coverage_jacoco import (
    JacocoMethodCoverage,
    JacocoReportError,
    load_jacoco_method_coverage,
)
from utility_scripts.code_coverage_validate import (
    _gradle_step,
    find_jacoco_reports,
    resolve_coverage_suite_dir,
)

#: The fixed handoff report the cover prompt points the agent at, relative to
#: the Rhei workspace root.
FIXED_REPORT_DISPLAY_PATH: str = "runtime/code-coverage/validation/naive-cover-report.md"


def summarize_universe(coverage_by_id: dict[str, JacocoMethodCoverage]) -> dict:
    """Coverage summary over every method JaCoCo reports for the library."""
    total: int = len(coverage_by_id)
    covered: int = sum(1 for method in coverage_by_id.values() if method.covered)
    return {
        "total": total,
        "covered": covered,
        "uncovered": total - covered,
        "coveragePercent": round(100.0 * covered / total, 2) if total else 0.0,
    }


def run_measurement(
        repo_path: str,
        coordinate: str,
        iteration: int,
        output_dir: str,
        skip_gradle: bool,
) -> dict:
    group, artifact, version = coordinate.split(":")
    gradle_steps: list[dict] = []
    if not skip_gradle:
        resolve_coverage_suite_dir(repo_path, group, artifact, version)
        for task in ("compileTestJava", "codeCoverageTest", "jacocoCodeCoverageReport"):
            step: dict = _gradle_step(repo_path, task, coordinate)
            gradle_steps.append(step)
            if not step["succeeded"]:
                raise JacocoReportError(
                    f"Gradle task '{task}' failed; refusing to reuse existing JaCoCo XML. "
                    f"Command: {step['command']}"
                )
    jacoco_xml_paths: list[str] = find_jacoco_reports(repo_path, group, artifact, version)
    coverage_by_id: dict[str, JacocoMethodCoverage] = load_jacoco_method_coverage(
        jacoco_xml_paths
    )
    summary: dict = summarize_universe(coverage_by_id)
    report: dict = {
        "coordinate": coordinate,
        "iteration": iteration,
        "summary": summary,
        "gradle": gradle_steps,
    }

    os.makedirs(output_dir, exist_ok=True)
    json_path: str = os.path.join(output_dir, f"naive-cover-report-{iteration}.json")
    with open(json_path, "w", encoding="utf-8") as json_file:
        json.dump(report, json_file, indent=2)
        json_file.write("\n")
    md_lines: list[str] = [
        f"# Naive cover report (iteration {iteration}) — {coordinate}",
        "",
        f"- Library method coverage: {summary['covered']}/{summary['total']} "
        f"({summary['coveragePercent']}%)",
        "",
        "## Gradle validation steps",
        "",
    ]
    for step in gradle_steps:
        md_lines.append(f"- `{step['task']}`: {'ok' if step['succeeded'] else 'FAILED'}")
    md_path: str = os.path.join(output_dir, f"naive-cover-report-{iteration}.md")
    with open(md_path, "w", encoding="utf-8") as md_file:
        md_file.write("\n".join(md_lines) + "\n")
    shutil.copy(json_path, os.path.join(output_dir, "naive-cover-report.json"))
    shutil.copy(md_path, os.path.join(output_dir, "naive-cover-report.md"))
    shutil.copy2(
        jacoco_xml_paths[0], os.path.join(output_dir, f"jacoco-naive-{iteration}.xml")
    )
    return report


def render_prompt(
        report_path: str,
        conversion_path: str,
        template_path: str,
        prompt_path: str,
) -> None:
    with open(report_path, "r", encoding="utf-8") as report_file:
        report: dict = json.load(report_file)
    with open(conversion_path, "r", encoding="utf-8") as conversion_file:
        conversion: dict = json.load(conversion_file)
    with open(template_path, "r", encoding="utf-8") as template_file:
        template: str = template_file.read()

    coordinate: str = report["coordinate"]
    summary: dict = report["summary"]
    suite_source_root: str = os.path.join(
        conversion["coverageSuiteAbsolutePath"], "src", "test", "java"
    )
    test_command: str = (
        f"cd {conversion['worktreePath']} && ./gradlew codeCoverageTest "
        f"-Pcoordinates={coordinate} -PincludeCodeCoverageSuite=true"
    )
    values: dict[str, str] = {
        "{library}": coordinate,
        "{coverage_suite_test_source_root}": suite_source_root,
        "{test_command}": test_command,
        "{jacoco_report_path}": FIXED_REPORT_DISPLAY_PATH,
        "{coverage_percent}": str(summary["coveragePercent"]),
        "{covered_methods}": str(summary["covered"]),
        "{all_methods}": str(summary["total"]),
    }
    prompt: str = template
    for placeholder, value in values.items():
        prompt = prompt.replace(placeholder, value)
    os.makedirs(os.path.dirname(prompt_path), exist_ok=True)
    with open(prompt_path, "w", encoding="utf-8") as prompt_file:
        prompt_file.write(prompt)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Measure or prompt the code coverage benchmark's naive baseline arm.",
    )
    subparsers = parser.add_subparsers(dest="command", required=True)
    measure = subparsers.add_parser("measure")
    measure.add_argument("--repo-path", required=True, help="Issue worktree / repo root.")
    measure.add_argument("--coordinate", required=True, help="group:artifact:version.")
    measure.add_argument("--iteration", type=int, required=True, help="Naive-cover iteration.")
    measure.add_argument("--output-dir", required=True, help="Directory for validation artifacts.")
    measure.add_argument(
        "--skip-gradle",
        action="store_true",
        help="Reuse existing JaCoCo XML instead of running Gradle (tests only).",
    )
    render = subparsers.add_parser("render")
    render.add_argument("--report", required=True, help="naive-cover-report JSON path.")
    render.add_argument("--conversion", required=True, help="conversion.json path.")
    render.add_argument("--template", required=True, help="Naive prompt template path.")
    render.add_argument("--prompt-path", required=True, help="Rendered prompt destination.")
    return parser


def main() -> None:
    args: argparse.Namespace = build_parser().parse_args()
    if args.command == "measure":
        report: dict = run_measurement(
            repo_path=args.repo_path,
            coordinate=args.coordinate,
            iteration=args.iteration,
            output_dir=args.output_dir,
            skip_gradle=args.skip_gradle,
        )
        summary: dict = report["summary"]
        print(
            f"[naive-measure] iteration {args.iteration}: "
            f"{summary['covered']}/{summary['total']} library methods covered "
            f"({summary['coveragePercent']}%)"
        )
    else:
        render_prompt(
            report_path=args.report,
            conversion_path=args.conversion,
            template_path=args.template,
            prompt_path=args.prompt_path,
        )


if __name__ == "__main__":
    sys.exit(main())
