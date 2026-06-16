# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import unittest

from git_scripts import make_pr_code_coverage_improvement as pr_module


class PullRequestBodyTests(unittest.TestCase):
    def _metrics(self) -> dict:
        return {
            "coverageSuitePath": "tests/src/com.example/demo/1.0.0/code-coverage",
            "baselineCoverage": {"jvmPercent": 40.0, "pgoExecutedMethods": 100},
            "finalCoverage": {"jvmPercent": 72.5, "pgoExecutedMethods": 180},
            "coverageDelta": {"jvmPercent": 32.5},
            "completedTargets": ["com.example.Greeter#greet():java.lang.String"],
            "skippedTargets": [{"id": "com.example.Greeter#secret():java.lang.String",
                                "reason": "not public user-callable"}],
            "exhaustedTargets": [],
            "validationCommands": ["./gradlew javaTest -Pcoordinates=com.example:demo:1.0.0"],
        }

    def test_body_contains_required_sections(self) -> None:
        body = pr_module.build_pull_request_body("com.example:demo:1.0.0", 8380, self._metrics())
        self.assertIn("Source issue: #8380", body)
        self.assertIn("Coordinate: `com.example:demo:1.0.0`", body)
        self.assertIn("Coverage suite path: `tests/src/com.example/demo/1.0.0/code-coverage`", body)
        self.assertIn("Baseline coverage: JVM JaCoCo 40.0%", body)
        self.assertIn("Final coverage: JVM JaCoCo 72.5%", body)
        self.assertIn("Coverage delta: JVM JaCoCo 32.5pp", body)
        self.assertIn("com.example.Greeter#greet():java.lang.String", body)
        self.assertIn("skipped: not public user-callable", body)
        self.assertIn("./gradlew javaTest -Pcoordinates=com.example:demo:1.0.0", body)

    def test_links_without_autoclose_by_default(self) -> None:
        body = pr_module.build_pull_request_body("com.example:demo:1.0.0", 8380, self._metrics())
        self.assertIn("Refs #8380.", body)
        self.assertNotIn("Closes #8380", body)

    def test_closes_when_resolves_issue(self) -> None:
        metrics = self._metrics()
        metrics["resolvesIssue"] = True
        body = pr_module.build_pull_request_body("com.example:demo:1.0.0", 8380, metrics)
        self.assertIn("Closes #8380.", body)


if __name__ == "__main__":
    unittest.main()
