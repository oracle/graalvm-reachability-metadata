# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""
JVM coverage validator for the code coverage improvement workflow
(§WF-code-coverage-improvement, §WF-code-coverage-improvement-architecture).

Phase-5 helper. It is JVM-only: it compiles the coverage test suite, runs the
JVM tests under JaCoCo (genuine Java-agent instrumentation, not the rejected PGO
`.exec` path), and correlates JaCoCo method coverage against the phase-3 API
inventory to compute which public-API targets remain uncovered after the
iteration. It writes the per-iteration JaCoCo report copy and the
remaining-uncovered-API report that feeds the next API-cover pass.

Native Image and reachability-metadata concerns are intentionally NOT here:
JaCoCo coverage measurement needs only the JVM. Metadata is generated and
repaired once before the PGO discovery phase by
`code_coverage_prepare_native_metadata.py`, so the instrumented native build can
succeed without rebuilding a native image on every API-cover iteration.

The deterministic, library-execution-free core is `correlate_jacoco`: it joins
JaCoCo `<method>` entries against API inventory target ids. The Gradle steps are
thin wrappers over the existing harness tasks (`compileTestJava`, `javaTest`,
`jacocoTestReport`).

Usage:
  python3 utility_scripts/code_coverage_validate.py \
    --repo-path <worktree> --coordinate group:artifact:version \
    --api-inventory <api-inventory.json> --iteration 1 \
    --output-dir runtime/code-coverage/validation [--skip-gradle]
"""

from __future__ import annotations

import argparse
import glob
import json
import os
import shutil
import xml.etree.ElementTree as ET

from utility_scripts.code_coverage_model import (
    MethodRef,
    parse_inventory_id,
    parse_jvm_descriptor,
)
from utility_scripts.gradle_test_runner import run_gradle_test_command


def _method_covered(method_element: ET.Element) -> bool:
    for counter in method_element.findall("counter"):
        if counter.get("type") in ("INSTRUCTION", "LINE", "METHOD"):
            try:
                if int(counter.get("covered", "0")) > 0:
                    return True
            except ValueError:
                continue
    return False


def covered_refs_from_jacoco(xml_path: str) -> set[str]:
    """Return canonical ids of methods JaCoCo recorded as executed."""
    covered: set[str] = set()
    tree = ET.parse(xml_path)
    for class_element in tree.getroot().iter("class"):
        owner = (class_element.get("name") or "").replace("/", ".")
        if not owner:
            continue
        for method_element in class_element.findall("method"):
            name = method_element.get("name") or ""
            descriptor = method_element.get("desc") or ""
            if not name or "(" not in descriptor:
                continue
            if not _method_covered(method_element):
                continue
            params, return_type = parse_jvm_descriptor(descriptor)
            ref = MethodRef(owner=owner, name=name, params=params, return_type=return_type)
            covered.add(ref.canonical_id)
            covered.add(ref.loose_key)
    return covered


def correlate_jacoco(inventory: dict, jacoco_xml_paths: list[str]) -> dict:
    """Classify each inventory target as covered/uncovered from JaCoCo evidence."""
    covered_keys: set[str] = set()
    for xml_path in jacoco_xml_paths:
        if os.path.isfile(xml_path):
            covered_keys |= covered_refs_from_jacoco(xml_path)

    targets: list[dict] = []
    covered_count = 0
    for target in inventory.get("targets", []):
        ref = parse_inventory_id(target.get("id", ""))
        if ref is None:
            continue
        is_covered = ref.canonical_id in covered_keys or ref.loose_key in covered_keys
        if is_covered:
            covered_count += 1
        targets.append({"id": ref.canonical_id, "kind": target.get("kind"),
                        "status": "covered" if is_covered else "uncovered"})
    return {
        "coordinate": inventory.get("coordinate"),
        "summary": {
            "total": len(targets),
            "covered": covered_count,
            "uncovered": len(targets) - covered_count,
            "coveragePercent": round(100.0 * covered_count / len(targets), 2) if targets else 0.0,
        },
        "targets": targets,
    }


def find_jacoco_reports(repo_path: str, group: str, artifact: str, version: str) -> list[str]:
    """Locate JaCoCo XML reports produced for the coordinate's test module."""
    module_dir = os.path.join(repo_path, "tests", "src", group, artifact, version)
    patterns = [
        os.path.join(module_dir, "**", "build", "reports", "jacoco", "**", "*.xml"),
        os.path.join(repo_path, "tests", "**", "build", "reports", "jacoco", "**", "*.xml"),
    ]
    found: list[str] = []
    for pattern in patterns:
        found.extend(glob.glob(pattern, recursive=True))
    return sorted(set(found))


def _gradle_step(repo_path: str, task: str, coordinate: str) -> dict:
    command = f"./gradlew {task} -Pcoordinates={coordinate} --stacktrace"
    output = run_gradle_test_command(command, working_dir=repo_path, library=coordinate)
    succeeded = "BUILD SUCCESSFUL" in output
    return {"task": task, "command": command, "succeeded": succeeded,
            "tail": output[-2000:] if not succeeded else ""}


def write_reports(report: dict, gradle_steps: list[dict], iteration: int, output_dir: str,
                  jacoco_xml_paths: list[str]) -> None:
    os.makedirs(output_dir, exist_ok=True)
    report = dict(report)
    report["iteration"] = iteration
    report["gradle"] = gradle_steps
    json_path = os.path.join(output_dir, f"api-cover-report-{iteration}.json")
    md_path = os.path.join(output_dir, f"api-cover-report-{iteration}.md")
    with open(json_path, "w", encoding="utf-8") as json_file:
        json.dump(report, json_file, indent=2)
        json_file.write("\n")

    # Keep a per-iteration copy of the JaCoCo XML alongside the report.
    if jacoco_xml_paths:
        shutil.copy2(jacoco_xml_paths[0], os.path.join(output_dir, f"jacoco-{iteration}.xml"))

    summary = report["summary"]
    lines = [
        f"# API cover report {iteration} — {report.get('coordinate')}",
        "",
        f"- JVM JaCoCo coverage of inventory: {summary['covered']}/{summary['total']} "
        f"({summary['coveragePercent']}%)",
        "",
        "## Gradle validation steps",
        "",
    ]
    for step in gradle_steps:
        lines.append(f"- `{step['task']}`: {'ok' if step['succeeded'] else 'FAILED'}")
    lines += ["", "## Still-uncovered public API targets", ""]
    uncovered = [t for t in report["targets"] if t["status"] == "uncovered"]
    if not uncovered:
        lines.append("_All inventory targets are covered by the JVM suite._")
    for target in uncovered:
        lines.append(f"- `{target['id']}` ({target['kind']})")
    with open(md_path, "w", encoding="utf-8") as md_file:
        md_file.write("\n".join(lines) + "\n")


def run_validation(repo_path: str, coordinate: str, api_inventory_path: str, iteration: int,
                   output_dir: str, skip_gradle: bool) -> dict:
    group, artifact, version = coordinate.split(":")
    with open(api_inventory_path, "r", encoding="utf-8") as inventory_file:
        inventory = json.load(inventory_file)

    gradle_steps: list[dict] = []
    if not skip_gradle:
        for task in ("compileTestJava", "javaTest", "jacocoTestReport"):
            gradle_steps.append(_gradle_step(repo_path, task, coordinate))

    jacoco_xml_paths = find_jacoco_reports(repo_path, group, artifact, version)
    report = correlate_jacoco(inventory, jacoco_xml_paths)
    write_reports(report, gradle_steps, iteration, output_dir, jacoco_xml_paths)
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description="Validate JVM coverage against the API inventory.")
    parser.add_argument("--repo-path", required=True, help="Issue worktree / repo root.")
    parser.add_argument("--coordinate", required=True, help="group:artifact:version.")
    parser.add_argument("--api-inventory", required=True, help="api-inventory.json path.")
    parser.add_argument("--iteration", type=int, default=1, help="API-cover iteration number.")
    parser.add_argument("--output-dir", required=True, help="Directory for validation artifacts.")
    parser.add_argument("--skip-gradle", action="store_true",
                        help="Only correlate existing JaCoCo reports; do not run Gradle.")
    args = parser.parse_args()

    report = run_validation(
        repo_path=args.repo_path,
        coordinate=args.coordinate,
        api_inventory_path=args.api_inventory,
        iteration=args.iteration,
        output_dir=args.output_dir,
        skip_gradle=args.skip_gradle,
    )
    summary = report["summary"]
    print(f"JVM coverage {summary['covered']}/{summary['total']} ({summary['coveragePercent']}%); "
          f"uncovered {summary['uncovered']}.")


if __name__ == "__main__":
    main()
