# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import json
import os
import tempfile
import unittest

from utility_scripts import code_coverage_validate as validate_module
from utility_scripts.code_coverage_model import parse_jvm_descriptor

FIXTURES = os.path.join(os.path.dirname(__file__), "fixtures", "code_coverage")


class DescriptorTests(unittest.TestCase):
    def test_parse_jvm_descriptor(self) -> None:
        params, ret = parse_jvm_descriptor("(Ljava/lang/String;[I)V")
        self.assertEqual(params, ("java.lang.String", "int[]"))
        self.assertEqual(ret, "void")
        params, ret = parse_jvm_descriptor("()Ljava/lang/String;")
        self.assertEqual(params, ())
        self.assertEqual(ret, "java.lang.String")


class JacocoCorrelationTests(unittest.TestCase):
    def _inventory(self) -> dict:
        with open(os.path.join(FIXTURES, "demo_api_inventory.json"), encoding="utf-8") as f:
            return json.load(f)

    def test_covered_and_uncovered_classification(self) -> None:
        report = validate_module.correlate_jacoco(
            self._inventory(), [os.path.join(FIXTURES, "demo_jacoco.xml")]
        )
        status = {t["id"]: t["status"] for t in report["targets"]}
        self.assertEqual(status["com.example.Greeter#<init>(java.lang.String):void"], "covered")
        self.assertEqual(status["com.example.Greeter#greet():java.lang.String"], "covered")
        self.assertEqual(status["com.example.Greeter#shout():java.lang.String"], "uncovered")
        self.assertEqual(report["summary"]["covered"], 2)
        self.assertEqual(report["summary"]["uncovered"], 1)

    def test_reports_written_with_skip_gradle(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            inventory_path = os.path.join(tmp, "api-inventory.json")
            with open(inventory_path, "w", encoding="utf-8") as f:
                json.dump(self._inventory(), f)
            # Lay out the JaCoCo report where find_jacoco_reports expects it.
            module = os.path.join(tmp, "tests", "src", "com.example", "demo", "1.0.0",
                                  "build", "reports", "jacoco", "test")
            os.makedirs(module)
            import shutil
            shutil.copy2(os.path.join(FIXTURES, "demo_jacoco.xml"),
                         os.path.join(module, "jacocoTestReport.xml"))
            out = os.path.join(tmp, "out")
            report = validate_module.run_validation(
                repo_path=tmp, coordinate="com.example:demo:1.0.0",
                api_inventory_path=inventory_path, iteration=2,
                output_dir=out, skip_gradle=True,
            )
            self.assertEqual(report["summary"]["covered"], 2)
            self.assertTrue(os.path.isfile(os.path.join(out, "api-cover-report-2.json")))
            self.assertTrue(os.path.isfile(os.path.join(out, "api-cover-report-2.md")))
            self.assertTrue(os.path.isfile(os.path.join(out, "jacoco-2.xml")))


if __name__ == "__main__":
    unittest.main()
