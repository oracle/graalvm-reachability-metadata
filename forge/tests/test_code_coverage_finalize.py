# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import json
import os
import tempfile
import unittest

from utility_scripts import code_coverage_finalize as module

COORDINATE = "com.example:demo:1.0.0"


def _api(statuses: list[str]) -> dict:
    covered = statuses.count("covered")
    uncovered = statuses.count("uncovered")
    missing = statuses.count("not-reported")
    return {
        "coordinate": COORDINATE,
        "summary": {
            "total": len(statuses),
            "measured": covered + uncovered,
            "covered": covered,
            "uncovered": uncovered,
            "notReported": missing,
            "coveragePercent": 0,
        },
        "targets": [
            {"id": f"example.Api#m{index}():void", "status": status}
            for index, status in enumerate(statuses)
        ],
    }


def _deep(statuses: list[str], samples: int) -> dict:
    covered = statuses.count("covered")
    return {
        "coordinate": COORDINATE,
        "profileKind": "sampled-guidance",
        "summary": {
            "deepMethods": len(statuses),
            "deepCovered": covered,
            "deepUncovered": len(statuses) - covered,
            "samplingContexts": 2,
            "sampledObservedMethods": 3,
            "totalSampleCount": samples,
            "sampledJoins": 1,
        },
        "deepMethods": [
            {"id": f"example.Internal#m{index}():void", "status": status}
            for index, status in enumerate(statuses)
        ],
    }


class FinalizerTests(unittest.TestCase):

    def setUp(self) -> None:
        self.directory = tempfile.TemporaryDirectory(prefix="coverage-finalize-")
        self.addCleanup(self.directory.cleanup)

    def _write(self, name: str, value: dict) -> str:
        path = os.path.join(self.directory.name, name)
        with open(path, "w", encoding="utf-8") as output:
            json.dump(value, output)
        return path

    def _run(self) -> dict:
        baseline_api = self._write(
            "api-0.json", _api(["covered", "uncovered", "uncovered", "uncovered"])
        )
        final_api = self._write(
            "api-5.json", _api(["covered", "covered", "covered", "uncovered"])
        )
        baseline_deep = self._write(
            "deep-0.json",
            _deep(
                ["covered", "uncovered", "uncovered", "uncovered", "uncovered"],
                42,
            ),
        )
        final_deep = self._write(
            "deep-5.json",
            _deep(
                ["covered", "covered", "uncovered", "uncovered", "uncovered"],
                84,
            ),
        )
        state = self._write(
            "targets.json",
            {
                "coordinate": COORDINATE,
                "iteration": 5,
                "targets": [
                    {
                        "id": "example.Internal#m1():void",
                        "status": "completed",
                        "attemptCount": 2,
                        "lastAttemptedIteration": 5,
                        "reason": None,
                    },
                    {
                        "id": "example.Internal#m2():void",
                        "status": "skipped",
                        "attemptCount": 1,
                        "lastAttemptedIteration": 3,
                        "reason": "No feasible public route.",
                    },
                    {
                        "id": "example.Internal#m3():void",
                        "status": "exhausted",
                        "attemptCount": 5,
                        "lastAttemptedIteration": 5,
                        "reason": "Static path remained infeasible.",
                    },
                    {
                        "id": "example.Internal#m4():void",
                        "status": "failed",
                        "attemptCount": 2,
                        "lastAttemptedIteration": 4,
                        "reason": "Coverage harness failed.",
                    },
                ],
            },
        )
        return module.finalize_coverage(
            coordinate=COORDINATE,
            coverage_suite_path="tests/src/com.example/demo/1.0.0/code-coverage-improvement",
            api_baseline_path=baseline_api,
            api_final_path=final_api,
            deep_baseline_path=baseline_deep,
            deep_final_path=final_deep,
            target_state_paths=[state],
            validation_commands=["./gradlew test -Pcoordinates=com.example:demo:1.0.0"],
            output_dir=os.path.join(self.directory.name, "output"),
        )

    def test_coverage_reports_determine_completion(self) -> None:
        metrics = self._run()

        self.assertEqual(metrics["apiJacoco"]["delta"]["coveragePercentagePoints"], 50)
        self.assertEqual(metrics["deepJacoco"]["delta"]["coveragePercentagePoints"], 20)
        self.assertTrue(metrics["pgoGuidance"]["guidanceOnly"])
        self.assertNotIn("coveragePercent", metrics["pgoGuidance"]["final"])
        self.assertEqual(metrics["pgoGuidance"]["final"]["sampleCount"], 84)
        completed = {
            (target["phase"], target["id"])
            for target in metrics["targets"]["completed"]
        }
        self.assertEqual(completed, {
            ("api", "example.Api#m1():void"),
            ("api", "example.Api#m2():void"),
            ("deep", "example.Internal#m1():void"),
        })
        deep_completed = next(
            target
            for target in metrics["targets"]["completed"]
            if target["phase"] == "deep"
        )
        self.assertEqual(deep_completed["attemptCount"], 2)
        self.assertEqual(deep_completed["lastAttemptedIteration"], 5)
        self.assertEqual(len(metrics["targets"]["skipped"]), 1)
        self.assertEqual(len(metrics["targets"]["exhausted"]), 1)
        self.assertEqual(len(metrics["targets"]["failed"]), 1)
        self.assertTrue(metrics["needsHumanIntervention"])
        module.validate_final_metrics(metrics)

    def test_writes_schema_valid_artifacts(self) -> None:
        self._run()
        output = os.path.join(self.directory.name, "output")

        loaded = module.load_validated_final_metrics(
            os.path.join(output, "final-metrics.json")
        )
        self.assertEqual(loaded["schemaVersion"], "1.0.0")
        with open(
                os.path.join(output, "final-summary.md"),
                encoding="utf-8",
        ) as summary_file:
            summary = summary_file.read()
        self.assertIn("### Failed (1)", summary)
        self.assertIn("Needs human intervention: yes", summary)
        self.assertIn("attempts: 2, last attempted iteration: 4", summary)
        self.assertIn("## Public API JaCoCo", summary)
        self.assertIn("## Deep-method JaCoCo", summary)
        self.assertIn("## Sampled PGO guidance only", summary)
        self.assertIn("Sample counts do not measure coverage", summary)
        self.assertNotIn("PGO coverage", summary)

    def test_failed_outcome_requires_human_intervention(self) -> None:
        metrics = self._run()
        metrics["needsHumanIntervention"] = False

        with self.assertRaises(module.FinalizationError):
            module.validate_final_metrics(metrics)

    def test_rejects_coordinate_mismatch(self) -> None:
        report = _api(["covered"])
        report["coordinate"] = "com.example:other:1.0.0"

        with self.assertRaisesRegex(module.FinalizationError, "expected"):
            module._api_snapshot(report, COORDINATE, "API baseline")

    def test_not_reported_api_target_can_complete(self) -> None:
        completed = module._completed_transitions(
            {"example.Api#parse():void": "not-reported"},
            {"example.Api#parse():void": "covered"},
            "api",
        )

        self.assertEqual(completed, [{
            "id": "example.Api#parse():void",
            "phase": "api",
            "status": "completed",
        }])

    def test_final_coverage_overrides_stale_failed_state(self) -> None:
        state = self._write(
            "stale-failed.json",
            {
                "coordinate": COORDINATE,
                "iteration": 5,
                "targets": [{
                    "id": "example.Internal#m0():void",
                    "status": "failed",
                    "attemptCount": 2,
                    "lastAttemptedIteration": 5,
                    "reason": "Stale failure.",
                }],
            },
        )

        outcomes = module._target_outcomes(
            [state],
            COORDINATE,
            _api([]),
            _api([]),
            _deep(["uncovered"], 1),
            _deep(["covered"], 1),
        )

        self.assertEqual(len(outcomes["completed"]), 1)
        self.assertEqual(outcomes["failed"], [])

    def test_rejects_state_outside_final_deep_universe(self) -> None:
        state = self._write(
            "unknown-target.json",
            {
                "coordinate": COORDINATE,
                "iteration": 5,
                "targets": [{
                    "id": "example.Internal#unknown():void",
                    "status": "pending",
                    "attemptCount": 0,
                    "lastAttemptedIteration": None,
                    "reason": None,
                }],
            },
        )

        with self.assertRaisesRegex(module.FinalizationError, "deep JaCoCo universe"):
            module._target_outcomes(
                [state],
                COORDINATE,
                _api([]),
                _api([]),
                _deep(["uncovered"], 1),
                _deep(["uncovered"], 1),
            )

    def test_rejects_completed_state_when_final_jacoco_is_uncovered(self) -> None:
        state = self._write(
            "contradictory-completed.json",
            {
                "coordinate": COORDINATE,
                "iteration": 5,
                "targets": [{
                    "id": "example.Internal#m0():void",
                    "status": "completed",
                    "attemptCount": 2,
                    "lastAttemptedIteration": 5,
                    "reason": None,
                }],
            },
        )

        with self.assertRaisesRegex(module.FinalizationError, "completed"):
            module._target_outcomes(
                [state],
                COORDINATE,
                _api([]),
                _api([]),
                _deep(["uncovered"], 1),
                _deep(["uncovered"], 1),
            )

    def test_repeatable_target_state_uses_latest_status(self) -> None:
        first = self._write(
            "first.json",
            {
                "coordinate": COORDINATE,
                "iteration": 1,
                "targets": [
                    {
                        "id": "example.Internal#m2():void",
                        "status": "selected",
                        "attemptCount": 0,
                        "lastAttemptedIteration": None,
                        "reason": None,
                    }
                ],
            },
        )
        final = self._write(
            "final.json",
            {
                "coordinate": COORDINATE,
                "iteration": 5,
                "targets": [
                    {
                        "id": "example.Internal#m2():void",
                        "status": "failed",
                        "attemptCount": 3,
                        "lastAttemptedIteration": 5,
                        "reason": "Harness remained broken.",
                    }
                ],
            },
        )

        states = module._load_latest_target_states([first, final], COORDINATE)

        self.assertEqual(states["example.Internal#m2():void"]["status"], "failed")
        self.assertEqual(states["example.Internal#m2():void"]["attemptCount"], 3)
        self.assertEqual(
            states["example.Internal#m2():void"]["lastAttemptedIteration"], 5
        )

    def test_accepts_non_terminal_target_state(self) -> None:
        path = self._write(
            "targets.json",
            {
                "coordinate": COORDINATE,
                "iteration": 1,
                "targets": [
                    {
                        "id": "example.Internal#pending():void",
                        "status": "pending",
                        "attemptCount": 0,
                        "lastAttemptedIteration": None,
                        "reason": None,
                    }
                ],
            },
        )

        states = module._load_latest_target_states([path], COORDINATE)

        self.assertEqual(states["example.Internal#pending():void"]["status"], "pending")

    def test_rejects_noncanonical_target_state_id(self) -> None:
        path = self._write(
            "targets.json",
            {
                "coordinate": COORDINATE,
                "iteration": 1,
                "targets": [
                    {
                        "id": "example.Internal#bad():java/lang/String",
                        "status": "pending",
                        "attemptCount": 0,
                        "lastAttemptedIteration": None,
                        "reason": None,
                    }
                ],
            },
        )

        with self.assertRaisesRegex(module.FinalizationError, "canonical"):
            module._load_latest_target_states([path], COORDINATE)

    def test_failed_target_requires_reason(self) -> None:
        path = self._write(
            "targets.json",
            {
                "coordinate": COORDINATE,
                "iteration": 5,
                "targets": [
                    {
                        "id": "example.Internal#failed():void",
                        "status": "failed",
                        "attemptCount": 1,
                        "lastAttemptedIteration": 5,
                        "reason": None,
                    }
                ],
            },
        )

        with self.assertRaisesRegex(module.FinalizationError, "reason"):
            module._load_latest_target_states([path], COORDINATE)

    def test_rejects_suite_path_escape(self) -> None:
        with self.assertRaises(module.FinalizationError):
            module._suite_path(os.path.join("..", "outside"))


if __name__ == "__main__":
    unittest.main()
