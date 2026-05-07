# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import unittest

from utility_scripts.metrics_writer import collect_new_library_support_quality_issues


class NewLibrarySupportQualityTests(unittest.TestCase):
    def test_pr_6228_like_metrics_block_publication_for_zero_dynamic_access_coverage(self) -> None:
        issues = collect_new_library_support_quality_issues({
            "status": "success",
            "code_coverage_percent": 8.43,
            "stats": {
                "dynamicAccess": {
                    "coveredCalls": 0,
                    "totalCalls": 8,
                    "coverageRatio": 0.0,
                },
            },
        })

        self.assertEqual(1, len(issues))
        self.assertIn("dynamic-access coverage is 0/8 (0.00%)", issues[0])
        self.assertIn("coverage above 20%", issues[0])

    def test_dynamic_access_coverage_at_twenty_percent_blocks_publication(self) -> None:
        issues = collect_new_library_support_quality_issues({
            "status": "success",
            "metrics": {
                "code_coverage_percent": 10.0,
            },
            "stats": {
                "dynamicAccess": {
                    "coveredCalls": 1,
                    "totalCalls": 5,
                    "coverageRatio": 0.2,
                },
            },
        })

        self.assertEqual(1, len(issues))
        self.assertIn("dynamic-access coverage is 1/5 (20.00%)", issues[0])

    def test_dynamic_access_coverage_above_twenty_percent_allows_publication(self) -> None:
        issues = collect_new_library_support_quality_issues({
            "status": "success",
            "metrics": {
                "code_coverage_percent": 10.0,
            },
            "stats": {
                "dynamicAccess": {
                    "coveredCalls": 2,
                    "totalCalls": 8,
                    "coverageRatio": 0.25,
                },
            },
        })

        self.assertEqual([], issues)

    def test_missing_dynamic_access_calls_keeps_existing_line_coverage_gate_only(self) -> None:
        issues = collect_new_library_support_quality_issues({
            "status": "success",
            "metrics": {
                "code_coverage_percent": 10.0,
            },
            "stats": {
                "dynamicAccess": {
                    "coveredCalls": 0,
                    "totalCalls": 0,
                    "coverageRatio": 1.0,
                },
            },
        })

        self.assertEqual([], issues)


if __name__ == "__main__":
    unittest.main()
