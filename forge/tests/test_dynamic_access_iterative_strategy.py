# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import io
import unittest
from unittest.mock import patch

from ai_workflows.workflow_strategies.dynamic_access_iterative_strategy import DynamicAccessIterativeStrategy
from utility_scripts.dynamic_access_report import DynamicAccessClass, DynamicAccessCoverageReport


class DynamicAccessProgressLoggingTests(unittest.TestCase):
    def test_class_completion_progress_prints_overall_coverage(self) -> None:
        report = DynamicAccessCoverageReport(
            coordinate="org.example:lib:1.0.0",
            has_dynamic_access=True,
            total_calls=103,
            covered_calls=45,
            classes=[],
        )

        output = io.StringIO()
        with patch("sys.stdout", output):
            DynamicAccessIterativeStrategy._print_class_completion_progress(
                "org.example.SomeClass",
                5,
                35,
                report,
            )

        self.assertEqual(
            output.getvalue().strip(),
            "[dynamic-access] ===================================================================================\n"
            "[dynamic-access] Progress after org.example.SomeClass: "
            "classes 5/35 complete; overall coverage 45/103 covered (58 remaining)\n"
            "[dynamic-access] ===================================================================================",
        )

    def test_completed_class_count_includes_fully_covered_report_classes(self) -> None:
        report = DynamicAccessCoverageReport(
            coordinate="org.example:lib:1.0.0",
            has_dynamic_access=True,
            total_calls=5,
            covered_calls=3,
            classes=[
                self._class_coverage("org.example.Covered", 2, 2),
                self._class_coverage("org.example.Exhausted", 2, 1),
                self._class_coverage("org.example.Pending", 1, 0),
            ],
        )

        self.assertEqual(
            DynamicAccessIterativeStrategy._completed_class_count(
                report,
                {"org.example.Exhausted"},
            ),
            2,
        )

    @staticmethod
    def _class_coverage(class_name: str, total_calls: int, covered_calls: int) -> DynamicAccessClass:
        return DynamicAccessClass(
            class_name=class_name,
            source_file=None,
            resolved_source_file=None,
            total_calls=total_calls,
            covered_calls=covered_calls,
            call_sites=[],
        )


if __name__ == "__main__":
    unittest.main()
