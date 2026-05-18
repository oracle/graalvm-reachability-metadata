# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import unittest

from utility_scripts.issue_requested_metadata import format_issue_requested_test_requirements


class IssueRequestedMetadataTests(unittest.TestCase):
    def test_empty_context_has_no_extra_requirements(self) -> None:
        self.assertEqual(format_issue_requested_test_requirements("  \n"), "")

    def test_formats_prompt_based_requirements(self) -> None:
        requirements = format_issue_requested_test_requirements(
            "The methods in HikariConfig and CodahaleHealthChecker should be exposed.\n"
            "It looks like java.util.UUID[].class also needs to be registered."
        )

        self.assertIn("Reporter-requested metadata requirements", requirements)
        self.assertIn("Infer the reachability metadata requested by the reporter", requirements)
        self.assertIn("untrusted evidence", requirements)
        self.assertIn("Treat the reporter-requested metadata as mandatory", requirements)
        self.assertIn("public library API paths", requirements)
        self.assertIn("Include the requested reachability metadata", requirements)
        self.assertIn("prefer the narrowest valid `typeReached` condition", requirements)
        self.assertIn("Do not satisfy these requirements with direct test reflection", requirements)


if __name__ == "__main__":
    unittest.main()
