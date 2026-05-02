# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import io
import tempfile
import unittest
from unittest.mock import patch

from ai_workflows.workflow_strategies.increase_dynamic_access_coverage_strategy import IncreaseDynamicAccessCoverageStrategy
from ai_workflows.workflow_strategies.dynamic_access_iterative_strategy import DynamicAccessIterativeStrategy
from ai_workflows.workflow_strategies.workflow_strategy import RUN_STATUS_CHUNK_READY
from utility_scripts.dynamic_access_report import DynamicAccessClass, DynamicAccessCoverageReport
from utility_scripts.large_library_progress import LargeLibraryProgressState, find_progress_state_path


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

    def test_first_large_library_part_uses_current_coverage_as_chunk_baseline(self) -> None:
        strategy = self._strategy()
        strategy.large_library_progress_state = LargeLibraryProgressState.create(
            coordinate="org.example:lib:1.0.0",
            issue_number=1412,
            request_label="library-update-request",
            strategy_name="library_update_pi_gpt-5.5",
        )
        report = DynamicAccessCoverageReport(
            coordinate="org.example:lib:1.0.0",
            has_dynamic_access=True,
            total_calls=103,
            covered_calls=45,
            classes=[],
        )

        self.assertEqual(strategy._initial_part_covered_calls(report), 45)

    def test_resumed_large_library_part_uses_saved_coverage_as_chunk_baseline(self) -> None:
        strategy = self._strategy()
        strategy.large_library_progress_state = LargeLibraryProgressState.create(
            coordinate="org.example:lib:1.0.0",
            issue_number=1412,
            request_label="library-update-request",
            strategy_name="library_update_pi_gpt-5.5",
        )
        strategy.large_library_progress_state.update_coverage(67, 103)
        report = DynamicAccessCoverageReport(
            coordinate="org.example:lib:1.0.0",
            has_dynamic_access=True,
            total_calls=103,
            covered_calls=80,
            classes=[],
        )

        self.assertEqual(strategy._initial_part_covered_calls(report), 67)

    def test_partially_covered_exhausted_class_is_persisted_for_continuation(self) -> None:
        class FakeAgent:
            def send_prompt(self, prompt: str) -> None:
                pass

            def run_test_command(self, command: str) -> str:
                return "BUILD SUCCESSFUL"

            def clear_context(self) -> None:
                pass

        initial_report = DynamicAccessCoverageReport(
            coordinate="org.example:lib:1.0.0",
            has_dynamic_access=True,
            total_calls=2,
            covered_calls=0,
            classes=[self._class_coverage("org.example.Partial", 2, 0)],
        )
        updated_report = DynamicAccessCoverageReport(
            coordinate="org.example:lib:1.0.0",
            has_dynamic_access=True,
            total_calls=2,
            covered_calls=1,
            classes=[self._class_coverage("org.example.Partial", 2, 1)],
        )

        with tempfile.TemporaryDirectory() as tmpdir:
            strategy = self._strategy()
            strategy.large_library_progress_state = LargeLibraryProgressState.create(
                coordinate="org.example:lib:1.0.0",
                issue_number=1412,
                request_label="library-update-request",
                strategy_name="library_update_pi_gpt-5.5",
            )
            strategy.large_library_progress_state_path = strategy.large_library_progress_state.default_path(tmpdir)

            with patch.object(strategy, "_render_prompt", return_value="prompt"), \
                    patch.object(strategy, "_generate_dynamic_access_report", return_value=updated_report), \
                    patch.object(strategy, "_commit_test_sources"), \
                    patch.object(strategy, "_run_native_test_verification_gate", return_value=True), \
                    patch.object(strategy, "_refresh_report_after_gate", return_value=None), \
                    patch.object(strategy, "_library_test_change_signature", return_value="clean"), \
                    patch(
                        "ai_workflows.workflow_strategies.dynamic_access_iterative_strategy.subprocess.check_output",
                        return_value="checkpoint\n",
                    ):
                phase_ok, iterations = strategy._run_dynamic_access_phase(FakeAgent(), initial_report)

            saved_state = LargeLibraryProgressState.load(strategy.large_library_progress_state_path)

        self.assertTrue(phase_ok)
        self.assertEqual(iterations, 1)
        self.assertEqual(saved_state.exhausted_classes, ["org.example.Partial"])
        self.assertEqual(saved_state.covered_calls, 1)

    def test_should_stop_for_large_library_chunk_returns_false_without_state(self) -> None:
        strategy = self._strategy()
        report = DynamicAccessCoverageReport(
            coordinate="org.example:lib:1.0.0",
            has_dynamic_access=True,
            total_calls=10,
            covered_calls=5,
            classes=[self._class_coverage("org.example.A", 5, 0)],
        )

        self.assertFalse(
            strategy._should_stop_for_large_library_chunk(report, set(), 100, 0),
        )

    def test_should_stop_for_large_library_chunk_skips_when_no_uncovered_class_remains(self) -> None:
        strategy = self._strategy()
        strategy.large_library_progress_state = LargeLibraryProgressState.create(
            coordinate="org.example:lib:1.0.0",
            issue_number=1412,
            request_label="library-new-request",
            strategy_name="dynamic_access_main_sources_pi_gpt-5.5",
        )
        strategy.chunk_class_limit = 1
        report = DynamicAccessCoverageReport(
            coordinate="org.example:lib:1.0.0",
            has_dynamic_access=True,
            total_calls=4,
            covered_calls=4,
            classes=[self._class_coverage("org.example.A", 2, 2)],
        )

        self.assertFalse(
            strategy._should_stop_for_large_library_chunk(report, {"org.example.A"}, 5, 0),
        )

    def test_should_stop_for_large_library_chunk_honors_class_limit(self) -> None:
        strategy = self._strategy()
        strategy.large_library_progress_state = LargeLibraryProgressState.create(
            coordinate="org.example:lib:1.0.0",
            issue_number=1412,
            request_label="library-new-request",
            strategy_name="dynamic_access_main_sources_pi_gpt-5.5",
        )
        strategy.chunk_class_limit = 2
        report = DynamicAccessCoverageReport(
            coordinate="org.example:lib:1.0.0",
            has_dynamic_access=True,
            total_calls=10,
            covered_calls=5,
            classes=[self._class_coverage("org.example.Pending", 5, 0)],
        )

        self.assertFalse(
            strategy._should_stop_for_large_library_chunk(report, set(), 1, 0),
        )
        self.assertTrue(
            strategy._should_stop_for_large_library_chunk(report, set(), 2, 0),
        )

    def test_should_stop_for_large_library_chunk_honors_call_limit(self) -> None:
        strategy = self._strategy()
        strategy.large_library_progress_state = LargeLibraryProgressState.create(
            coordinate="org.example:lib:1.0.0",
            issue_number=1412,
            request_label="library-new-request",
            strategy_name="dynamic_access_main_sources_pi_gpt-5.5",
        )
        strategy.chunk_call_limit = 5
        report = DynamicAccessCoverageReport(
            coordinate="org.example:lib:1.0.0",
            has_dynamic_access=True,
            total_calls=20,
            covered_calls=8,
            classes=[self._class_coverage("org.example.Pending", 12, 0)],
        )

        self.assertFalse(
            strategy._should_stop_for_large_library_chunk(report, set(), 0, 4),
        )
        self.assertTrue(
            strategy._should_stop_for_large_library_chunk(report, set(), 0, 3),
        )

    def test_auto_large_library_series_starts_for_report_above_class_limit(self) -> None:
        report = DynamicAccessCoverageReport(
            coordinate="org.example:lib:1.0.0",
            has_dynamic_access=True,
            total_calls=7,
            covered_calls=0,
            classes=[
                self._class_coverage("org.example.A", 3, 0),
                self._class_coverage("org.example.B", 4, 0),
            ],
        )

        with tempfile.TemporaryDirectory() as tmpdir:
            strategy = self._strategy(
                large_library_issue_number=1412,
                large_library_request_label="library-new-request",
                large_library_metrics_repo_root=tmpdir,
                large_library_strategy_name="dynamic_access_main_sources_pi_gpt-5.5",
                chunk_class_limit=1,
            )

            strategy._maybe_activate_large_library_series(report)

            self.assertIsNotNone(strategy.large_library_progress_state)
            self.assertIsNotNone(strategy.large_library_progress_state_path)
            saved_state = LargeLibraryProgressState.load(strategy.large_library_progress_state_path)

        self.assertEqual(saved_state.issue_number, 1412)
        self.assertEqual(saved_state.request_label, "library-new-request")
        self.assertEqual(saved_state.class_order, ["org.example.A", "org.example.B"])
        self.assertEqual(saved_state.covered_calls, 0)
        self.assertEqual(saved_state.total_calls, 7)

    def test_auto_large_library_series_skips_report_within_configured_limits(self) -> None:
        report = DynamicAccessCoverageReport(
            coordinate="org.example:lib:1.0.0",
            has_dynamic_access=True,
            total_calls=3,
            covered_calls=0,
            classes=[self._class_coverage("org.example.A", 3, 0)],
        )

        with tempfile.TemporaryDirectory() as tmpdir:
            strategy = self._strategy(
                large_library_issue_number=1412,
                large_library_request_label="library-new-request",
                large_library_metrics_repo_root=tmpdir,
                large_library_strategy_name="dynamic_access_main_sources_pi_gpt-5.5",
                chunk_class_limit=1,
                chunk_call_limit=3,
            )

            strategy._maybe_activate_large_library_series(report)

        self.assertIsNone(strategy.large_library_progress_state)
        self.assertIsNone(strategy.large_library_progress_state_path)

    def test_increase_coverage_strategy_starts_large_library_series_for_oversized_report(self) -> None:
        class LargeReportWithoutWork(DynamicAccessCoverageReport):
            def next_uncovered_class(self, exhausted_classes: set[str]) -> DynamicAccessClass | None:
                return None

        report = LargeReportWithoutWork(
            coordinate="org.example:lib:1.0.0",
            has_dynamic_access=True,
            total_calls=7,
            covered_calls=0,
            classes=[
                self._class_coverage("org.example.A", 3, 0),
                self._class_coverage("org.example.B", 4, 0),
            ],
        )

        with tempfile.TemporaryDirectory() as tmpdir:
            strategy = IncreaseDynamicAccessCoverageStrategy(
                {
                    "model": "test-model",
                    "prompts": {"dynamic-access-iteration": "unused"},
                    "parameters": {
                        "max-iterations": 1,
                        "max-class-test-iterations": 1,
                    },
                },
                reachability_repo_path="/tmp/reachability",
                updated_library="org.example:lib:1.0.0",
                large_library_issue_number=1412,
                large_library_request_label="library-update-request",
                large_library_metrics_repo_root=tmpdir,
                large_library_strategy_name="library_update_pi_gpt-5.5",
                chunk_class_limit=1,
            )

            with patch.object(DynamicAccessIterativeStrategy, "_generate_dynamic_access_report", return_value=report), \
                    patch.object(DynamicAccessIterativeStrategy, "_library_test_change_signature", return_value="clean"):
                strategy.run(agent=object())

            state_path = find_progress_state_path(tmpdir, 1412)
            self.assertIsNotNone(state_path)
            saved_state = LargeLibraryProgressState.load(state_path)

        self.assertEqual(saved_state.request_label, "library-update-request")
        self.assertEqual(saved_state.strategy_name, "library_update_pi_gpt-5.5")
        self.assertEqual(saved_state.class_order, ["org.example.A", "org.example.B"])
        self.assertEqual(saved_state.covered_calls, 0)
        self.assertEqual(saved_state.total_calls, 7)

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
                "ai_workflows.workflow_strategies.increase_dynamic_access_coverage_strategy.DynamicAccessIterativeStrategy",
                ChunkReadyDynamicAccess,
        ):
            self.assertEqual(strategy.run(agent=object()), (RUN_STATUS_CHUNK_READY, 3))

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

    @staticmethod
    def _strategy(**context) -> DynamicAccessIterativeStrategy:
        return DynamicAccessIterativeStrategy(
            {
                "model": "test-model",
                "prompts": {"dynamic-access-iteration": "unused"},
                "parameters": {
                    "max-iterations": 1,
                    "max-class-test-iterations": 1,
                },
            },
            library="org.example:lib:1.0.0",
            reachability_repo_path="/tmp/reachability",
            **context,
        )


if __name__ == "__main__":
    unittest.main()
