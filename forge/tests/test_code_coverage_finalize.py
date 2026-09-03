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


def _jacoco(api_covered: int, deep_covered: int, api_total: int, deep_total: int) -> str:
    """A JaCoCo XML naming the same ids the API and deep rosters use.

    The first `api_covered` / `deep_covered` methods of each class carry a
    covered METHOD counter; the rest are reported but uncovered.
    """
    def methods(prefix: str, total: int, covered: int) -> str:
        return "".join(
            f'<method name="m{index}" desc="()V" line="{index + 1}">'
            f'<counter type="METHOD" missed="{0 if index < covered else 1}" '
            f'covered="{1 if index < covered else 0}"/></method>'
            for index in range(total)
        )
    return (
        '<?xml version="1.0" encoding="UTF-8"?><report name="demo">'
        f'<package name="example"><class name="example/Api" sourcefilename="Api.java">'
        f'{methods("Api", api_total, api_covered)}</class>'
        f'<class name="example/Internal" sourcefilename="Internal.java">'
        f'{methods("Internal", deep_total, deep_covered)}</class>'
        "</package></report>"
    )


class FinalizerTests(unittest.TestCase):

    def setUp(self) -> None:
        self.directory = tempfile.TemporaryDirectory(prefix="coverage-finalize-")
        self.addCleanup(self.directory.cleanup)

    def _write(self, name: str, value: dict) -> str:
        path = os.path.join(self.directory.name, name)
        with open(path, "w", encoding="utf-8") as output:
            json.dump(value, output)
        return path

    def _write_xml(self, name: str, value: str) -> str:
        path = os.path.join(self.directory.name, name)
        with open(path, "w", encoding="utf-8") as output:
            output.write(value)
        return path

    def _run(
            self,
            include_target_state: bool = True,
            include_stop_decisions: bool = True,
    ) -> dict:
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
        stop_decisions = [
            self._write(
                f"{phase}-stop-decision.json",
                {
                    "phase": phase,
                    "schemaVersion": "1.0.0",
                    "threshold": 10,
                    "window": 2,
                    "floor": 4,
                    "budget": 15,
                    "passes": passes,
                    "targetsRemaining": 1,
                    "covered": [],
                    "passYields": yields,
                    "stopped": True,
                    "reason": reason,
                },
            )
            for phase, passes, yields, reason in (
                ("api", 5, [40, 30, 20, 6, 4], "marginal-yield"),
                ("deep", 15, [40] * 15, "budget-spent"),
            )
        ]
        # Three checkpoints of one run: 1/9 -> 4/9 -> 6/9 of the frozen universe
        # of 4 API plus 5 deep methods.
        checkpoints = tuple(
            self._write_xml(f"jacoco-{name}.xml", _jacoco(api, deep, 4, 5))
            for name, api, deep in (
                ("run-start", 1, 0),
                ("after-api", 3, 1),
                ("final", 3, 3),
            )
        )
        return module.finalize_coverage(
            coordinate=COORDINATE,
            coverage_suite_path="tests/src/com.example/demo/1.0.0/code-coverage-improvement",
            api_baseline_path=baseline_api,
            api_final_path=final_api,
            deep_baseline_path=baseline_deep,
            deep_final_path=final_deep,
            jacoco_paths=checkpoints,
            target_state_paths=[state] if include_target_state else [],
            stop_decision_paths=stop_decisions if include_stop_decisions else [],
            validation_commands=["./gradlew test -Pcoordinates=com.example:demo:1.0.0"],
            output_dir=os.path.join(self.directory.name, "output"),
        )

    def test_run_coverage_is_one_denominator_and_sequential_checkpoints(self) -> None:
        """Each phase begins at the checkpoint the previous phase ended on."""
        run = self._run()["runCoverage"]

        self.assertEqual(run["universe"], 9)
        self.assertEqual((run["apiUniverse"], run["deepUniverse"]), (4, 5))
        self.assertEqual(
            [(point["name"], point["covered"]) for point in run["checkpoints"]],
            [("runStart", 1), ("afterApiPhase", 4), ("final", 6)],
        )
        # Every share divides by the same 9, so the phase gains sum to the run's.
        self.assertEqual(
            [point["coveragePercent"] for point in run["checkpoints"]],
            [11.11, 44.44, 66.67],
        )
        self.assertEqual(
            [(phase["name"], phase["covered"]) for phase in run["phases"]],
            [("api", 3), ("deep", 2)],
        )
        gains = sum(phase["coveragePercentagePoints"] for phase in run["phases"])
        first, last = run["checkpoints"][0], run["checkpoints"][-1]
        self.assertAlmostEqual(
            gains, last["coveragePercent"] - first["coveragePercent"], places=2
        )

    def test_run_coverage_rejects_a_universe_that_moved(self) -> None:
        """A frozen id missing from a later report is an error, not a zero."""
        with self.assertRaises(module.FinalizationError) as raised:
            module._checkpoint(
                "final",
                self._write_xml("short.xml", _jacoco(1, 1, 2, 2)),
                ["example.Api#m0():void", "example.Api#m9():void"],
                [],
            )

        self.assertIn("the universe moved", str(raised.exception))
        self.assertIn("example.Api#m9():void", str(raised.exception))

    def test_run_start_report_defines_both_rosters(self) -> None:
        """A method the run never reports is dropped on either side, not fatal.

        The deep roster gets the same treatment as the inventory: charging an
        unreportable method to the denominator understates every figure, and
        aborting on one roster while quietly dropping from the other would make
        the same condition fatal or free depending on which side it landed on.
        """
        run_start = module.load_jacoco_method_coverage(
            [self._write_xml("narrow.xml", _jacoco(1, 1, 2, 2))]
        )

        api_ids, deep_ids = module._universe_ids(
            _api(["covered", "uncovered", "not-reported"]),
            _deep(["covered", "uncovered", "uncovered"], 12),
            run_start,
        )

        self.assertEqual(len(api_ids), 2)
        self.assertEqual(len(deep_ids), 2)
        self.assertNotIn("example.Internal#m2():void", deep_ids)

    def test_finalizes_without_target_state_files(self) -> None:
        metrics = self._run(include_target_state=False)

        completed = {target["id"] for target in metrics["targets"]["completed"]}
        self.assertIn("example.Internal#m1():void", completed)
        self.assertEqual(metrics["targets"]["skipped"], [])
        self.assertEqual(metrics["targets"]["exhausted"], [])
        self.assertEqual(metrics["targets"]["failed"], [])
        self.assertFalse(metrics["needsHumanIntervention"])

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

    def test_stop_decisions_are_carried_api_first(self) -> None:
        """A phase that ended short must say why (§AR-code-coverage-improvement.3.3)."""
        decisions = self._run()["stopDecisions"]

        self.assertEqual([entry["phase"] for entry in decisions], ["api", "deep"])
        self.assertEqual(decisions[0]["reason"], "marginal-yield")
        self.assertEqual(decisions[0]["passYields"][-2:], [6, 4])
        self.assertEqual(decisions[1]["reason"], "budget-spent")
        self.assertEqual(decisions[1]["passes"], 15)

    def test_finalizes_without_stop_decisions(self) -> None:
        metrics = self._run(include_stop_decisions=False)

        self.assertEqual(metrics["stopDecisions"], [])
        module.validate_final_metrics(metrics)

    def test_writes_schema_valid_artifacts(self) -> None:
        self._run()
        output = os.path.join(self.directory.name, "output")

        loaded = module.load_validated_final_metrics(
            os.path.join(output, "final-metrics.json")
        )
        self.assertEqual(loaded["schemaVersion"], "1.2.0")
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
        self.assertIn("- api: marginal-yield after 5/15 passes", summary)
        self.assertIn("2\u00d7<10 methods after pass 4", summary)
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
