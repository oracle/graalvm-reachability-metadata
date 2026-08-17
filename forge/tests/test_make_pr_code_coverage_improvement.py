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
                    "total": 16, "measured": 10, "covered": 4,
                    "uncovered": 6, "notReported": 6, "coveragePercent": 40.0,
                },
                "final": {
                    "total": 16, "measured": 10, "covered": 8,
                    "uncovered": 2, "notReported": 6, "coveragePercent": 80.0,
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

    def test_body_reports_phase_totals(self) -> None:
        body = module.build_pull_request_body(
            "com.example:demo:1.0.0", 8380, self._metrics()
        )

        self.assertIn("### Simple Jacoco guidance phase", body)
        # `measured`, not `total`: the 6 not-reported entries are methods JaCoCo
        # can never rule on, so counting them against the run understates it.
        self.assertIn("Baseline: 4/10 (40.0%)", body)
        self.assertIn("Final: 8/10 (80.0%)", body)
        self.assertIn("Delta: +40.0pp", body)
        self.assertIn("Remaining uncovered: 2", body)
        self.assertIn("### PGO guidance phase", body)
        self.assertIn("Final: 12/20 (60.0%)", body)
        self.assertNotIn("Sampled PGO", body)
        self.assertNotIn("84 samples", body)

        self.assertIn("## Completed targets (1)", body)
        self.assertNotIn("example.Greeter#greet():java.lang.String", body)
        self.assertIn("No public route.", body)
        self.assertIn("## Failed targets (1)", body)
        self.assertIn("Needs human intervention: yes", body)
        self.assertIn("attempts: 4, last attempted iteration: 5", body)

    def test_body_combines_both_phases(self) -> None:
        body = module.build_pull_request_body(
            "com.example:demo:1.0.0", 8380, self._metrics()
        )

        # Disjoint universes, so 10 measured API + 20 deep methods add up.
        self.assertIn("### Both phases combined", body)
        self.assertIn("Baseline: 9/30 (30.0%)", body)
        self.assertIn("Final: 20/30 (66.67%)", body)
        self.assertIn("Delta: +36.67pp", body)

    def test_body_reports_token_usage_in_workflow_order(self) -> None:
        usage = [
            {"phase": "convert", "input": 1000, "cached": 2000, "output": 30},
            {"phase": "api-coverage", "input": 5000, "cached": 6000, "output": 70},
        ]

        body = module.build_pull_request_body(
            "com.example:demo:1.0.0", 8380, self._metrics(), usage
        )

        self.assertIn("## Token usage", body)
        self.assertIn("| convert | 1,000 | 2,000 | 30 |", body)
        self.assertIn("| api-coverage | 5,000 | 6,000 | 70 |", body)
        self.assertIn("| **Total** | **6,000** | **8,000** | **100** |", body)
        self.assertLess(body.index("## Token usage"), body.index("Refs #8380."))

    def test_body_omits_token_section_without_accounting(self) -> None:
        body = module.build_pull_request_body(
            "com.example:demo:1.0.0", 8380, self._metrics(), []
        )

        self.assertNotIn("## Token usage", body)

    def test_token_usage_orders_phases_and_skips_unmeasured(self) -> None:
        with tempfile.TemporaryDirectory() as accounting:
            tasks = os.path.join(accounting, "tasks")
            os.makedirs(tasks)
            for phase, value in (("deep-coverage", 7), ("convert", 3)):
                with open(
                    os.path.join(tasks, f"ws.code-coverage-{phase}.json"),
                    "w",
                    encoding="utf-8",
                ) as handle:
                    json.dump({"direct": {
                        "input_total": {"value": value},
                        "input_cached_read": {"value": value * 2},
                        "output_total": {"value": None},
                    }}, handle)

            rows = module.load_token_usage(accounting)

        self.assertEqual([row["phase"] for row in rows], ["convert", "deep-coverage"])
        self.assertIn("| convert | 3 | 6 | n/a |", "\n".join(module._token_lines(rows)))

    def test_token_usage_is_empty_without_accounting_directory(self) -> None:
        with tempfile.TemporaryDirectory() as empty:
            self.assertEqual(module.load_token_usage(empty), [])

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

    def _publish_with_branch_suffix(self, branch_suffix: str | None) -> str:
        """Return the head branch suffix `publish` asked `build_ai_branch_name` for."""
        with tempfile.TemporaryDirectory(prefix="coverage-publish-") as repo_path:
            finalization = os.path.join(repo_path, "finalization")
            os.makedirs(finalization)
            with open(
                    os.path.join(finalization, "final-metrics.json"),
                    "w",
                    encoding="utf-8",
            ) as metrics_file:
                json.dump(self._metrics(), metrics_file)
            coverage_suite = self._metrics()["coverageSuitePath"]
            os.makedirs(os.path.join(repo_path, coverage_suite))

            with patch.object(
                    module, "build_ai_branch_name", return_value="ai/kimeta/b"
            ) as build_branch, \
                    patch.object(module, "delete_remote_branch_if_exists"), \
                    patch.object(module.subprocess, "run"), \
                    patch.object(module, "stage_coverage_paths"), \
                    patch.object(module, "run_git_transport"), \
                    patch.object(module, "load_token_usage", return_value=[]), \
                    patch.object(module, "create_pull_request", return_value=42):
                module.publish(
                    repo_path,
                    "com.example:demo:1.0.0",
                    8380,
                    finalization,
                    coverage_suite,
                    "kimeta",
                    "kimeta",
                    "master",
                    None,
                    branch_suffix,
                )

        return build_branch.call_args.args[0]

    def test_branch_keeps_the_plain_coordinate_without_a_suffix(self) -> None:
        self.assertEqual(
            self._publish_with_branch_suffix(None), "code-coverage-demo-1.0.0"
        )
        self.assertEqual(
            self._publish_with_branch_suffix(""), "code-coverage-demo-1.0.0"
        )

    def test_branch_suffix_discriminates_coexisting_runs(self) -> None:
        # Publication force-replaces the remote head branch, so a second run on
        # the same coordinate must not resolve to the first run's branch.
        self.assertEqual(
            self._publish_with_branch_suffix("luna"),
            "code-coverage-demo-1.0.0-luna",
        )
        self.assertNotEqual(
            self._publish_with_branch_suffix("luna"),
            self._publish_with_branch_suffix("sol"),
        )

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
