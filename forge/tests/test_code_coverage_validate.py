# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import json
import os
import shutil
import tempfile
import unittest
from unittest.mock import patch

from utility_scripts import code_coverage_validate as validate_module
from utility_scripts.code_coverage_jacoco import (
    JacocoReportError,
    load_jacoco_method_coverage,
)
from utility_scripts.code_coverage_model import parse_jvm_descriptor

FIXTURES = os.path.join(os.path.dirname(__file__), "fixtures", "code_coverage")
DEMO_JACOCO = os.path.join(FIXTURES, "demo_jacoco.xml")
EXACT_JACOCO = os.path.join(FIXTURES, "validator_exact_jacoco.xml")


class DescriptorTests(unittest.TestCase):
    def test_parse_jvm_descriptor(self) -> None:
        params: tuple[str, ...]
        return_type: str
        params, return_type = parse_jvm_descriptor("(Ljava/lang/String;[I)V")
        self.assertEqual(params, ("java.lang.String", "int[]"))
        self.assertEqual(return_type, "void")
        params, return_type = parse_jvm_descriptor("()Ljava/lang/String;")
        self.assertEqual(params, ())
        self.assertEqual(return_type, "java.lang.String")

    def test_rejects_semantically_invalid_jvm_descriptors(self) -> None:
        invalid = ("", "I)V", "(Q)V", "(V)V", "([V)V", "()Q", "()Vjunk", "(L;)V")
        for descriptor in invalid:
            with self.subTest(descriptor=descriptor), self.assertRaises(ValueError):
                parse_jvm_descriptor(descriptor)


class JacocoReportTests(unittest.TestCase):
    def test_loads_covered_uncovered_and_source_evidence_for_every_method(self) -> None:
        coverage = load_jacoco_method_coverage([EXACT_JACOCO])

        self.assertEqual(len(coverage), 4)
        string_method = coverage["com.example.Overloaded#select(java.lang.String):void"]
        integer_method = coverage["com.example.Overloaded#select(java.lang.Integer):void"]
        internal_method = coverage["com.example.Overloaded#internalHelper():void"]
        self.assertEqual(string_method.status, "covered")
        self.assertEqual(integer_method.status, "uncovered")
        self.assertEqual(internal_method.status, "uncovered")
        self.assertEqual(internal_method.source_path, "com/example/Overloaded.java")
        self.assertEqual(internal_method.source_line, 19)
        self.assertEqual(internal_method.report_paths, (EXACT_JACOCO,))

    def test_repeat_reports_merge_with_covered_winning(self) -> None:
        coverage = load_jacoco_method_coverage([EXACT_JACOCO, DEMO_JACOCO])

        greeting = coverage["com.example.Greeter#greet():java.lang.String"]
        self.assertTrue(greeting.covered)
        self.assertEqual(greeting.report_paths, (EXACT_JACOCO, DEMO_JACOCO))

    def test_missing_report_is_a_clear_error(self) -> None:
        missing_path = os.path.join(FIXTURES, "missing-validator-jacoco.xml")
        with self.assertRaisesRegex(JacocoReportError, "JaCoCo report does not exist"):
            load_jacoco_method_coverage([missing_path])

    def test_no_reports_is_a_clear_error(self) -> None:
        with self.assertRaisesRegex(JacocoReportError, "No JaCoCo XML reports"):
            load_jacoco_method_coverage([])

    def test_malformed_xml_is_a_clear_error(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_dir:
            malformed_path = os.path.join(temporary_dir, "malformed.xml")
            with open(malformed_path, "w", encoding="utf-8") as malformed_file:
                malformed_file.write("<report>")
            with self.assertRaisesRegex(JacocoReportError, "Cannot parse JaCoCo report"):
                load_jacoco_method_coverage([malformed_path])

    def test_semantically_invalid_descriptor_is_a_clear_error(self) -> None:
        report = '''<report name="invalid">
  <package name="com/example">
    <class name="com/example/Broken" sourcefilename="Broken.java">
      <method name="broken" desc="()Q" line="1">
        <counter type="METHOD" missed="1" covered="0"/>
      </method>
    </class>
  </package>
</report>
'''
        with tempfile.TemporaryDirectory() as temporary_dir:
            invalid_path = os.path.join(temporary_dir, "invalid.xml")
            with open(invalid_path, "w", encoding="utf-8") as invalid_file:
                invalid_file.write(report)
            with self.assertRaisesRegex(JacocoReportError, "invalid descriptor"):
                load_jacoco_method_coverage([invalid_path])


class JacocoCorrelationTests(unittest.TestCase):
    def _inventory(self) -> dict:
        inventory_path: str = os.path.join(FIXTURES, "demo_api_inventory.json")
        with open(inventory_path, encoding="utf-8") as inventory_file:
            return json.load(inventory_file)

    def test_covered_and_uncovered_classification(self) -> None:
        report: dict = validate_module.correlate_jacoco(self._inventory(), [DEMO_JACOCO])
        status: dict[str, str] = {
            target["id"]: target["status"] for target in report["targets"]
        }
        self.assertEqual(
            status["com.example.Greeter#<init>(java.lang.String):void"],
            "covered",
        )
        self.assertEqual(
            status["com.example.Greeter#greet():java.lang.String"],
            "covered",
        )
        self.assertEqual(
            status["com.example.Greeter#shout():java.lang.String"],
            "uncovered",
        )
        self.assertEqual(report["summary"]["covered"], 2)
        self.assertEqual(report["summary"]["uncovered"], 1)
        self.assertEqual(report["summary"]["notReported"], 0)

    def test_same_arity_overloads_keep_exact_statuses(self) -> None:
        inventory: dict = {
            "coordinate": "com.example:demo:1.0.0",
            "targets": [
                {
                    "id": "com.example.Overloaded#select(java.lang.String):void",
                    "kind": "method",
                },
                {
                    "id": "com.example.Overloaded#select(java.lang.Integer):void",
                    "kind": "method",
                },
                {
                    "id": "com.example.Overloaded#select(java.lang.Long):void",
                    "kind": "method",
                },
            ],
        }

        report: dict = validate_module.correlate_jacoco(inventory, [EXACT_JACOCO])
        targets: dict[str, dict] = {target["id"]: target for target in report["targets"]}

        self.assertEqual(
            targets["com.example.Overloaded#select(java.lang.String):void"]["status"],
            "covered",
        )
        self.assertEqual(
            targets["com.example.Overloaded#select(java.lang.Integer):void"]["status"],
            "uncovered",
        )
        unmatched = targets["com.example.Overloaded#select(java.lang.Long):void"]
        self.assertEqual(unmatched["status"], "not-reported")
        self.assertEqual(unmatched["evidence"], [])
        self.assertEqual(report["summary"]["measured"], 2)
        self.assertEqual(report["summary"]["notReported"], 1)

    def test_rejects_non_callable_inventory_target(self) -> None:
        inventory = {
            "coordinate": "com.example:demo:1.0.0",
            "targets": [{"id": "com.example.Mode#QUIET", "kind": "enumConstant"}],
        }
        with self.assertRaisesRegex(JacocoReportError, "canonical method or constructor"):
            validate_module.correlate_jacoco(inventory, [DEMO_JACOCO])

    def test_reported_target_carries_jacoco_source_evidence(self) -> None:
        report: dict = validate_module.correlate_jacoco(self._inventory(), [DEMO_JACOCO])
        greeting = next(
            target
            for target in report["targets"]
            if target["id"] == "com.example.Greeter#greet():java.lang.String"
        )
        self.assertEqual(greeting["sourcePath"], "com/example/Greeter.java")
        self.assertEqual(greeting["sourceLine"], 9)
        self.assertEqual(greeting["evidence"], ["jacoco"])

    def test_reports_written_with_skip_gradle(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_dir:
            inventory_path = os.path.join(temporary_dir, "api-inventory.json")
            with open(inventory_path, "w", encoding="utf-8") as inventory_file:
                json.dump(self._inventory(), inventory_file)
            module = os.path.join(
                temporary_dir,
                "tests",
                "src",
                "com.example",
                "demo",
                "1.0.0",
                "build",
                "reports",
                "jacoco",
                "jacocoCodeCoverageReport",
            )
            os.makedirs(module)
            shutil.copy2(DEMO_JACOCO, os.path.join(module, "jacocoCodeCoverageReport.xml"))
            output_dir = os.path.join(temporary_dir, "out")
            report: dict = validate_module.run_validation(
                repo_path=temporary_dir,
                coordinate="com.example:demo:1.0.0",
                api_inventory_path=inventory_path,
                iteration=2,
                output_dir=output_dir,
                skip_gradle=True,
            )
            self.assertEqual(report["summary"]["covered"], 2)
            self.assertTrue(os.path.isfile(os.path.join(output_dir, "api-cover-report-2.json")))
            self.assertTrue(os.path.isfile(os.path.join(output_dir, "api-cover-report-2.md")))
            self.assertTrue(os.path.isfile(os.path.join(output_dir, "jacoco-2.xml")))

    def test_validation_rejects_mismatched_inventory_coordinate(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_dir:
            inventory = self._inventory()
            inventory["coordinate"] = "other.group:other-artifact:9.9.9"
            inventory_path = os.path.join(temporary_dir, "api-inventory.json")
            with open(inventory_path, "w", encoding="utf-8") as inventory_file:
                json.dump(inventory, inventory_file)
            with self.assertRaisesRegex(ValueError, "does not match"):
                validate_module.run_validation(
                    repo_path=temporary_dir,
                    coordinate="com.example:demo:1.0.0",
                    api_inventory_path=inventory_path,
                    iteration=1,
                    output_dir=os.path.join(temporary_dir, "out"),
                    skip_gradle=True,
                )

    def test_validation_reports_missing_inventory_clearly(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_dir:
            with self.assertRaisesRegex(ValueError, "Cannot read API inventory"):
                validate_module.run_validation(
                    repo_path=temporary_dir,
                    coordinate="com.example:demo:1.0.0",
                    api_inventory_path=os.path.join(temporary_dir, "missing.json"),
                    iteration=1,
                    output_dir=os.path.join(temporary_dir, "out"),
                    skip_gradle=True,
                )


    def test_validation_uses_indexed_test_version_report(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_dir:
            coordinate = "com.example:demo:2.0.0"
            inventory = self._inventory()
            inventory["coordinate"] = coordinate
            inventory_path = os.path.join(temporary_dir, "api-inventory.json")
            with open(inventory_path, "w", encoding="utf-8") as inventory_file:
                json.dump(inventory, inventory_file)
            index_dir = os.path.join(temporary_dir, "metadata", "com.example", "demo")
            os.makedirs(index_dir)
            with open(os.path.join(index_dir, "index.json"), "w", encoding="utf-8") as index_file:
                json.dump([
                    {
                        "metadata-version": "1.0.0",
                        "test-version": "shared-tests",
                        "tested-versions": ["2.0.0"],
                    }
                ], index_file)
            report_dir = os.path.join(
                temporary_dir, "tests", "src", "com.example", "demo",
                "shared-tests", "build", "reports", "jacoco", "jacocoCodeCoverageReport",
            )
            os.makedirs(report_dir)
            report_path = os.path.join(report_dir, "jacocoCodeCoverageReport.xml")
            shutil.copy2(DEMO_JACOCO, report_path)

            report = validate_module.run_validation(
                repo_path=temporary_dir,
                coordinate=coordinate,
                api_inventory_path=inventory_path,
                iteration=0,
                output_dir=os.path.join(temporary_dir, "out"),
                skip_gradle=True,
            )

            self.assertEqual(report["coordinate"], coordinate)
            self.assertEqual(report["summary"]["covered"], 2)
            self.assertEqual(
                validate_module.find_jacoco_reports(
                    temporary_dir, "com.example", "demo", "2.0.0",
                ),
                [report_path],
            )
    def test_failed_gradle_step_cannot_reuse_stale_jacoco(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_dir:
            inventory_path = os.path.join(temporary_dir, "api-inventory.json")
            with open(inventory_path, "w", encoding="utf-8") as inventory_file:
                json.dump(self._inventory(), inventory_file)
            stale_dir = os.path.join(
                temporary_dir, "tests", "src", "com.example", "demo", "1.0.0",
                "build", "reports", "jacoco",
            )
            os.makedirs(stale_dir)
            shutil.copy2(DEMO_JACOCO, os.path.join(stale_dir, "stale.xml"))
            failed_step = {
                "task": "compileTestJava",
                "command": "./gradlew compileTestJava",
                "succeeded": False,
                "tail": "BUILD FAILED",
            }
            with patch.object(validate_module, "_gradle_step", return_value=failed_step) as run:
                with self.assertRaisesRegex(JacocoReportError, "refusing to reuse"):
                    validate_module.run_validation(
                        repo_path=temporary_dir,
                        coordinate="com.example:demo:1.0.0",
                        api_inventory_path=inventory_path,
                        iteration=1,
                        output_dir=os.path.join(temporary_dir, "out"),
                        skip_gradle=False,
                        coverage_suite_path=stale_dir,
                    )
            run.assert_called_once_with(
                temporary_dir, "compileTestJava", "com.example:demo:1.0.0",
            )

    def test_validation_does_not_fall_back_to_another_coordinate_report(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_dir:
            inventory_path = os.path.join(temporary_dir, "api-inventory.json")
            with open(inventory_path, "w", encoding="utf-8") as inventory_file:
                json.dump(self._inventory(), inventory_file)
            unrelated_dir = os.path.join(
                temporary_dir,
                "tests",
                "src",
                "other.group",
                "other-artifact",
                "1.0.0",
                "build",
                "reports",
                "jacoco",
            )
            os.makedirs(unrelated_dir)
            shutil.copy2(DEMO_JACOCO, os.path.join(unrelated_dir, "jacocoTestReport.xml"))

            with self.assertRaisesRegex(JacocoReportError, "No JaCoCo XML reports"):
                validate_module.run_validation(
                    repo_path=temporary_dir,
                    coordinate="com.example:demo:1.0.0",
                    api_inventory_path=inventory_path,
                    iteration=1,
                    output_dir=os.path.join(temporary_dir, "out"),
                    skip_gradle=True,
                )


if __name__ == "__main__":
    unittest.main()
