# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

from contextlib import redirect_stderr
from io import StringIO
import json
import os
import tempfile
import unittest
from unittest.mock import patch

from git_scripts import make_pr_code_coverage_improvement as module


class PublisherTests(unittest.TestCase):

    def _metrics(self) -> dict:
        return {
            "schemaVersion": "1.0.0",
            "coordinate": "com.example:demo:1.0.0",
            "coverageSuitePath": "tests/src/com.example/demo/1.0.0/code-coverage-improvement",
            "apiJacoco": {
                "baseline": {
                    "total": 10, "measured": 10, "covered": 4,
                    "uncovered": 6, "notReported": 0, "coveragePercent": 40.0,
                },
                "final": {
                    "total": 10, "measured": 10, "covered": 8,
                    "uncovered": 2, "notReported": 0, "coveragePercent": 80.0,
                },
                "delta": {
                    "covered": 4, "uncovered": -4, "notReported": 0,
                    "coveragePercentagePoints": 40.0,
                },
            },
            "deepJacoco": {
                "baseline": {
                    "total": 20, "covered": 5, "uncovered": 15,
                    "coveragePercent": 25.0,
                },
                "final": {
                    "total": 20, "covered": 12, "uncovered": 8,
                    "coveragePercent": 60.0,
                },
                "delta": {
                    "covered": 7, "uncovered": -7,
                    "coveragePercentagePoints": 35.0,
                },
            },
            "pgoGuidance": {
                "guidanceOnly": True,
                "note": (
                    "Sampled PGO is navigation evidence only. Sample counts do "
                    "not measure coverage."
                ),
                "baseline": {
                    "samplingContexts": 2, "sampledMethods": 5,
                    "sampleCount": 42, "sampledJoins": 2,
                },
                "final": {
                    "samplingContexts": 3, "sampledMethods": 8,
                    "sampleCount": 84, "sampledJoins": 4,
                },
            },
            "targets": {
                "completed": [
                    {
                        "id": "example.Greeter#greet():java.lang.String",
                        "phase": "api",
                        "status": "completed",
                    }
                ],
                "skipped": [
                    {
                        "id": "example.Internal#secret():java.lang.String",
                        "phase": "deep",
                        "status": "skipped",
                        "reason": "No public route.",
                        "attemptCount": 1,
                        "lastAttemptedIteration": 2,
                    }
                ],
                "exhausted": [],
                "failed": [
                    {
                        "id": "example.Internal#broken():java.lang.String",
                        "phase": "deep",
                        "status": "failed",
                        "attemptCount": 4,
                        "lastAttemptedIteration": 5,
                        "reason": "Harness remained broken.",
                    }
                ],
            },
            "validationCommands": [
                "./gradlew test -Pcoordinates=com.example:demo:1.0.0"
            ],
            "needsHumanIntervention": True,
        }

    def test_body_keeps_evidence_separate(self) -> None:
        body = module.build_pull_request_body(
            "com.example:demo:1.0.0", 8380, self._metrics()
        )

        self.assertIn("### Public API entries", body)
        self.assertIn("Baseline: 4/10 (40.0%)", body)
        self.assertIn("### Deep internal methods", body)
        self.assertIn("Final: 12/20 (60.0%)", body)
        self.assertIn("## Sampled PGO guidance only", body)
        self.assertIn("84 samples", body)
        self.assertNotIn("PGO coverage", body)
        self.assertNotIn("executed methods", body)
        self.assertIn("[api] `example.Greeter#greet():java.lang.String`", body)
        self.assertIn("No public route.", body)

        self.assertIn("## Failed targets (1)", body)
        self.assertIn("Needs human intervention: yes", body)
        self.assertIn("attempts: 4, last attempted iteration: 5", body)

    def test_commit_subject_is_at_most_sixty_characters(self) -> None:
        coordinate = f"com.{'verylong.' * 10}:artifact-with-long-name:1.0.0"

        subject = module._coverage_commit_subject(coordinate)

        self.assertEqual(len(subject), module.MAX_COMMIT_SUBJECT_LENGTH)
        self.assertTrue(subject.startswith("Improve code coverage for "))
        self.assertTrue(subject.endswith("..."))

    def test_stages_indexed_test_and_metadata_versions(self) -> None:
        with tempfile.TemporaryDirectory(prefix="coverage-stage-") as repo_path:
            group = "com.example"
            artifact = "demo"
            requested_version = "1.1.0"
            test_version = "shared-tests"
            metadata_version = "1.0.0"
            index_dir = os.path.join(repo_path, "metadata", group, artifact)
            os.makedirs(index_dir)
            with open(
                    os.path.join(index_dir, "index.json"),
                    "w",
                    encoding="utf-8",
            ) as index_file:
                json.dump(
                    [{
                        "metadata-version": metadata_version,
                        "test-version": test_version,
                        "tested-versions": [requested_version],
                    }],
                    index_file,
                )
            os.makedirs(os.path.join(index_dir, metadata_version))
            coverage_suite = os.path.join(
                "tests",
                "src",
                group,
                artifact,
                test_version,
                "code-coverage-improvement",
            )
            os.makedirs(os.path.join(repo_path, coverage_suite))

            with patch.object(module, "stage_and_commit") as stage_and_commit:
                module.stage_coverage_paths(
                    repo_path,
                    group,
                    artifact,
                    requested_version,
                    coverage_suite,
                )

        staged_paths, subject = stage_and_commit.call_args.args
        mapped_test_dir = os.path.join(
            "tests", "src", group, artifact, test_version
        )
        requested_test_dir = os.path.join(
            "tests", "src", group, artifact, requested_version
        )
        mapped_metadata_dir = os.path.join(
            "metadata", group, artifact, metadata_version
        )
        requested_metadata_dir = os.path.join(
            "metadata", group, artifact, requested_version
        )
        self.assertIn(mapped_test_dir, staged_paths)
        self.assertNotIn(requested_test_dir, staged_paths)
        self.assertEqual(
            subject,
            module._coverage_commit_subject(
                f"{group}:{artifact}:{requested_version}"
            ),
        )
        self.assertIn(mapped_metadata_dir, staged_paths)
        self.assertNotIn(requested_metadata_dir, staged_paths)
        self.assertEqual(stage_and_commit.call_args.kwargs["cwd"], repo_path)

    def test_links_without_autoclose_by_default(self) -> None:
        body = module.build_pull_request_body(
            "com.example:demo:1.0.0", 8380, self._metrics()
        )
        self.assertIn("Refs #8380.", body)
        self.assertNotIn("Closes #8380", body)

    def test_closes_when_metrics_resolve_issue(self) -> None:
        metrics = self._metrics()
        metrics["resolvesIssue"] = True

        body = module.build_pull_request_body(
            "com.example:demo:1.0.0", 8380, metrics
        )

        self.assertIn("Closes #8380.", body)

    def test_load_metrics_validates_schema(self) -> None:
        with tempfile.TemporaryDirectory(prefix="coverage-pr-") as directory:
            path = os.path.join(directory, "final-metrics.json")
            with open(path, "w", encoding="utf-8") as output:
                json.dump(self._metrics(), output)

            loaded = module.load_finalization_metrics(directory)

        self.assertEqual(loaded["coordinate"], "com.example:demo:1.0.0")

    def test_load_metrics_rejects_invalid_schema(self) -> None:
        metrics = self._metrics()
        metrics["pgoGuidance"]["guidanceOnly"] = False
        with tempfile.TemporaryDirectory(prefix="coverage-pr-") as directory:
            with open(
                    os.path.join(directory, "final-metrics.json"),
                    "w",
                    encoding="utf-8",
            ) as output:
                json.dump(metrics, output)

            with redirect_stderr(StringIO()):
                with self.assertRaises(SystemExit):
                    module.load_finalization_metrics(directory)


if __name__ == "__main__":
    unittest.main()
