# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Bulk remainder, chunk-stop, and increase-coverage strategy run tests."""

import os
import tempfile
import unittest
from unittest.mock import patch

from ai_workflows.core.increase_dynamic_access_coverage_strategy import IncreaseDynamicAccessCoverageStrategy
from ai_workflows.core.dynamic_access_iterative_strategy import DynamicAccessIterativeStrategy
from ai_workflows.core.workflow_strategy import RUN_STATUS_CHUNK_READY, RUN_STATUS_FAILURE, RUN_STATUS_SUCCESS
from utility_scripts.dynamic_access_report import DynamicAccessClass, DynamicAccessCoverageReport
from utility_scripts.dynamic_access_exhaust_report import DynamicAccessExhaustReport
from utility_scripts.continuation_marker import PHASE_EXPLORE
from utility_scripts.run_location import (
    RunLocation,
    STEP_GENERATE_TESTS,
    enter_phase,
    failed_run_location,
    reset_run_location,
)


class DynamicAccessChunkedRunTests(unittest.TestCase):
    def setUp(self) -> None:
        reset_run_location()
        enter_phase(PHASE_EXPLORE)

    def tearDown(self) -> None:
        reset_run_location()

    def test_final_remainder_succeeds_after_bulk_completed_classes(self) -> None:
        """A terminal iterative remainder keeps productive bulk work successful.

        §FS-forge-chunked-dynamic-access
        """
        class FakeAgent:
            def send_prompt(self, prompt: str) -> None:
                pass

            def run_test_command(self, command: str) -> str:
                return "BUILD SUCCESSFUL"

            def clear_context(self) -> None:
                pass

        completed_classes = [f"org.example.Completed{index}" for index in range(27)]
        remaining_classes = [f"org.example.Remaining{index}" for index in range(6)]
        current_report = self._report_for_class_names(
            completed_classes + remaining_classes,
            completed_classes,
        )

        with tempfile.TemporaryDirectory() as tmpdir:
            exhaust_report_path = os.path.join(tmpdir, "dynamic-access-exhaust-report.json")
            exhaust_report = DynamicAccessExhaustReport.create(
                coordinate="org.example:lib:1.0.0",
                issue_number=9776,
            )
            for class_name in completed_classes:
                exhaust_report.mark_completed(class_name)
            strategy = self._strategy(
                dynamic_access_exhaust_report=exhaust_report,
                dynamic_access_exhaust_report_path=exhaust_report_path,
                chunk_class_count=15,
            )

            with patch.object(strategy, "_render_prompt", return_value="prompt"), \
                    patch.object(strategy, "_generate_dynamic_access_report", return_value=current_report), \
                    patch.object(strategy, "_print_failure_analysis"), \
                    patch.object(
                        strategy,
                        "_commit_dynamic_access_exhaust_report",
                        side_effect=lambda message: strategy._save_dynamic_access_exhaust_report(),
                    ), \
                    patch.object(strategy, "_library_test_change_signature", return_value="clean"), \
                    patch(
                        "ai_workflows.core.dynamic_access_iterative_strategy.subprocess.check_output",
                        return_value="checkpoint\n",
                    ):
                phase_ok, iterations = strategy._run_dynamic_access_phase(FakeAgent(), current_report)

            saved_report = DynamicAccessExhaustReport.load(exhaust_report_path)

        self.assertTrue(phase_ok)
        self.assertEqual(iterations, 6)
        self.assertEqual(len(saved_report.completed_classes), 27)
        self.assertEqual(set(saved_report.exhausted_classes), set(remaining_classes))
        self.assertIsNone(failed_run_location())

    def test_final_remainder_succeeds_after_partial_bulk_call_gain(self) -> None:
        """A partial bulk gain remains successful after iterative exhaustion.

        §FS-forge-chunked-dynamic-access
        """
        phase_ok, iterations, exhaust_report = self._run_exhausted_partial_remainder(1)

        self.assertTrue(phase_ok)
        self.assertEqual(iterations, 1)
        self.assertEqual(exhaust_report.exhausted_classes, ["org.example.Partial"])

    def test_final_remainder_fails_without_partial_bulk_call_gain(self) -> None:
        """An unchanged bulk report does not excuse iterative exhaustion.

        §FS-forge-chunked-dynamic-access
        """
        phase_ok, iterations, _ = self._run_exhausted_partial_remainder(0)

        self.assertFalse(phase_ok)
        self.assertEqual(iterations, 1)

    def test_partial_bulk_call_gain_does_not_mask_report_failure(self) -> None:
        """Accumulated progress applies only at natural phase completion.

        §FS-forge-chunked-dynamic-access
        """
        phase_ok, iterations, _ = self._run_exhausted_partial_remainder(
            1,
            report_refresh_succeeds=False,
        )

        self.assertFalse(phase_ok)
        self.assertEqual(iterations, 1)

    def _run_exhausted_partial_remainder(
            self,
            preceding_covered_call_gain: int,
            report_refresh_succeeds: bool = True,
    ) -> tuple[bool, int, DynamicAccessExhaustReport]:
        class FakeAgent:
            def send_prompt(self, prompt: str) -> None:
                pass

            def run_test_command(self, command: str) -> str:
                return "BUILD SUCCESSFUL"

            def clear_context(self) -> None:
                pass

        class_name = "org.example.Partial"
        current_report = DynamicAccessCoverageReport(
            coordinate="org.example:lib:1.0.0",
            has_dynamic_access=True,
            total_calls=2,
            covered_calls=1,
            classes=[self._class_coverage(class_name, 2, 1)],
        )
        exhaust_report = DynamicAccessExhaustReport.create(
            coordinate="org.example:lib:1.0.0",
            issue_number=9864,
        )
        strategy = self._strategy(
            dynamic_access_exhaust_report=exhaust_report,
            chunk_class_count=15,
            preceding_dynamic_access_covered_call_gain=preceding_covered_call_gain,
        )
        refreshed_report = current_report if report_refresh_succeeds else None

        with patch.object(strategy, "_render_prompt", return_value="prompt"), \
                patch.object(strategy, "_generate_dynamic_access_report", return_value=refreshed_report), \
                patch.object(strategy, "_print_failure_analysis"), \
                patch.object(strategy, "_library_test_change_signature", return_value="clean"), \
                patch(
                    "ai_workflows.core.dynamic_access_iterative_strategy.subprocess.check_output",
                    return_value="checkpoint\n",
                ):
            phase_ok, iterations = strategy._run_dynamic_access_phase(FakeAgent(), current_report)

        return phase_ok, iterations, exhaust_report

    def test_terminal_exhaustion_defers_failure_location_to_caller(self) -> None:
        """A phase result alone does not record a terminal workflow failure.

        §FS-forge-run-location-reporting.3
        """
        class_name = "org.example.Exhausted"
        current_report = self._report_for_class_names([class_name], [])
        exhaust_report = DynamicAccessExhaustReport.create(
            coordinate="org.example:lib:1.0.0",
            issue_number=9776,
        )
        exhaust_report.mark_exhausted(class_name)
        strategy = self._strategy(
            dynamic_access_exhaust_report=exhaust_report,
            chunk_class_count=15,
        )

        with patch.object(strategy, "_library_test_change_signature", return_value="clean"):
            phase_ok, iterations = strategy._run_dynamic_access_phase(object(), current_report)

        self.assertFalse(phase_ok)
        self.assertEqual(iterations, 0)
        self.assertIsNone(failed_run_location())

    def test_standalone_run_locates_terminal_exploration_failure(self) -> None:
        """The standalone workflow records zero progress when it returns failure.

        §FS-forge-run-location-reporting.3
        """
        class_name = "org.example.Exhausted"
        current_report = self._report_for_class_names([class_name], [])
        exhaust_report = DynamicAccessExhaustReport.create(
            coordinate="org.example:lib:1.0.0",
            issue_number=9776,
        )
        exhaust_report.mark_exhausted(class_name)
        strategy = self._strategy(
            dynamic_access_exhaust_report=exhaust_report,
            chunk_class_count=15,
        )

        with patch.object(strategy, "_generate_dynamic_access_report", return_value=current_report), \
                patch.object(strategy, "_library_test_change_signature", return_value="clean"), \
                patch("ai_workflows.core.dynamic_access_iterative_strategy.subprocess.run"):
            result = strategy.run(agent=object(), checkpoint_commit_hash="checkpoint")

        self.assertEqual(result, (RUN_STATUS_FAILURE, 0, 0))
        self.assertEqual(
            failed_run_location(),
            RunLocation(PHASE_EXPLORE, STEP_GENERATE_TESTS, "org.example:lib:1.0.0"),
        )

    def test_should_stop_for_chunked_dynamic_access_returns_false_without_report(self) -> None:
        strategy = self._strategy()
        report = DynamicAccessCoverageReport(
            coordinate="org.example:lib:1.0.0",
            has_dynamic_access=True,
            total_calls=10,
            covered_calls=5,
            classes=[self._class_coverage("org.example.A", 5, 0)],
        )

        self.assertFalse(
            strategy._should_stop_for_chunked_dynamic_access(report, set(), {"org.example.A"}),
        )

    def test_should_stop_for_chunked_dynamic_access_skips_when_no_uncovered_class_remains(self) -> None:
        strategy = self._strategy(
            dynamic_access_exhaust_report=DynamicAccessExhaustReport.create(
                coordinate="org.example:lib:1.0.0",
                issue_number=1412,
            ),
            chunk_class_count=1,
        )
        report = DynamicAccessCoverageReport(
            coordinate="org.example:lib:1.0.0",
            has_dynamic_access=True,
            total_calls=4,
            covered_calls=4,
            classes=[self._class_coverage("org.example.A", 2, 2)],
        )

        self.assertFalse(
            strategy._should_stop_for_chunked_dynamic_access(report, {"org.example.A"}, {"org.example.A"}),
        )

    def test_should_stop_for_chunked_dynamic_access_honors_class_count(self) -> None:
        strategy = self._strategy(
            dynamic_access_exhaust_report=DynamicAccessExhaustReport.create(
                coordinate="org.example:lib:1.0.0",
                issue_number=1412,
            ),
            chunk_class_count=2,
        )
        report = DynamicAccessCoverageReport(
            coordinate="org.example:lib:1.0.0",
            has_dynamic_access=True,
            total_calls=10,
            covered_calls=5,
            classes=[self._class_coverage("org.example.Pending", 5, 0)],
        )

        self.assertFalse(
            strategy._should_stop_for_chunked_dynamic_access(report, set(), {"org.example.A"}),
        )
        self.assertTrue(
            strategy._should_stop_for_chunked_dynamic_access(report, set(), {"org.example.A", "org.example.B"}),
        )

    def test_increase_coverage_strategy_propagates_chunk_ready_from_dynamic_access_phase(self) -> None:
        class ChunkReadyDynamicAccess:
            def __init__(self, strategy_obj: dict, **context) -> None:
                self._last_phase_status = RUN_STATUS_CHUNK_READY

            def _run_dynamic_access_phase(self, agent) -> tuple[bool, int]:
                return True, 3

        strategy = IncreaseDynamicAccessCoverageStrategy(
            {
                "model": "test-model",
                "parameters": {},
                "prompts": {},
            },
            reachability_repo_path="/tmp/reachability",
            library="org.example:lib:1.0.0",
        )

        with patch(
                "ai_workflows.core.increase_dynamic_access_coverage_strategy.DynamicAccessIterativeStrategy",
                ChunkReadyDynamicAccess,
        ):
            self.assertEqual(strategy.run(agent=object()), (RUN_STATUS_CHUNK_READY, 3))

    def test_increase_coverage_strategy_does_not_run_issue_requested_phase(self) -> None:
        calls: list[str] = []

        class ReporterRequestedDynamicAccess:
            def __init__(self, strategy_obj: dict, **context) -> None:
                self._last_phase_status = RUN_STATUS_SUCCESS

            def _run_dynamic_access_phase(self, agent) -> tuple[bool, int]:
                calls.append("dynamic-access")
                return False, 0

        strategy = IncreaseDynamicAccessCoverageStrategy(
            {
                "model": "test-model",
                "parameters": {},
                "prompts": {},
            },
            reachability_repo_path="/tmp/reachability",
            library="org.example:lib:1.0.0",
            issue_requested_metadata_context="Reporter-provided missing metadata context:\nmissing resource",
        )

        with patch(
                "ai_workflows.core.increase_dynamic_access_coverage_strategy.DynamicAccessIterativeStrategy",
                ReporterRequestedDynamicAccess,
        ), patch.object(
                strategy,
                "run_issue_requested_metadata_phase",
        ) as reporter_phase:
            self.assertEqual(strategy.run(agent=object()), (RUN_STATUS_FAILURE, 0))

        self.assertEqual(calls, ["dynamic-access"])
        reporter_phase.assert_not_called()
        self.assertEqual(
            failed_run_location(),
            RunLocation(PHASE_EXPLORE, STEP_GENERATE_TESTS, "org.example:lib:1.0.0"),
        )

    def test_increase_coverage_strategy_fails_when_no_primary_dynamic_access_or_issue_work_succeeds(self) -> None:
        class NoProgressDynamicAccess:
            def __init__(self, strategy_obj: dict, **context) -> None:
                self._last_phase_status = RUN_STATUS_SUCCESS

            def _run_dynamic_access_phase(self, agent) -> tuple[bool, int]:
                return False, 0

        strategy = IncreaseDynamicAccessCoverageStrategy(
            {
                "model": "test-model",
                "parameters": {},
                "prompts": {},
            },
            reachability_repo_path="/tmp/reachability",
            library="org.example:lib:1.0.0",
        )

        with patch(
                "ai_workflows.core.increase_dynamic_access_coverage_strategy.DynamicAccessIterativeStrategy",
                NoProgressDynamicAccess,
        ):
            self.assertEqual(strategy.run(agent=object()), (RUN_STATUS_FAILURE, 0))
        self.assertEqual(
            failed_run_location(),
            RunLocation(PHASE_EXPLORE, STEP_GENERATE_TESTS, "org.example:lib:1.0.0"),
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

    @classmethod
    def _report_for_class_names(
            cls,
            class_names: list[str],
            covered_class_names: list[str],
    ) -> DynamicAccessCoverageReport:
        covered = set(covered_class_names)
        return DynamicAccessCoverageReport(
            coordinate="org.example:lib:1.0.0",
            has_dynamic_access=True,
            total_calls=len(class_names),
            covered_calls=len(covered),
            classes=[
                cls._class_coverage(class_name, 1, 1 if class_name in covered else 0)
                for class_name in class_names
            ],
        )

    @staticmethod
    def _strategy(**context) -> DynamicAccessIterativeStrategy:
        library = context.pop("library", "org.example:lib:1.0.0")
        reachability_repo_path = context.pop("reachability_repo_path", "/tmp/reachability")
        parameters = {
            "max-iterations": 1,
            "max-class-test-iterations": 1,
        }
        parameters.update(context.pop("parameters", {}))
        return DynamicAccessIterativeStrategy(
            {
                "model": "test-model",
                "prompts": {"dynamic-access-iteration": "unused"},
                "parameters": parameters,
            },
            library=library,
            reachability_repo_path=reachability_repo_path,
            **context,
        )


if __name__ == "__main__":
    unittest.main()
