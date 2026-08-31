# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import unittest
from unittest.mock import patch

from ai_workflows.core.increase_dynamic_access_coverage_strategy import (
    IncreaseDynamicAccessCoverageStrategy,
)
from ai_workflows.core.bulk_dynamic_access_strategy import BulkDynamicAccessStrategy
from ai_workflows.core.workflow_strategy import RUN_STATUS_FAILURE, RUN_STATUS_SUCCESS
from utility_scripts.dynamic_access_exhaust_report import DynamicAccessExhaustReport
from utility_scripts.dynamic_access_report import (
    BulkDynamicAccessProgress,
    DynamicAccessClass,
    DynamicAccessCoverageReport,
    compute_bulk_dynamic_access_progress,
)


class BulkDynamicAccessChunkTests(unittest.TestCase):
    def test_bulk_progress_compares_gated_report_and_excludes_processed_classes(self) -> None:
        initial_report = self._report(["A", "B", "C", "D"], [])
        final_report = self._report(["A", "B", "C", "D"], ["A", "B"])

        progress = compute_bulk_dynamic_access_progress(
            initial_report,
            final_report,
            {"D"},
        )

        self.assertIs(progress.final_report, final_report)
        self.assertEqual(progress.completed_classes, ("A", "B"))
        self.assertEqual(progress.remaining_classes, ("C",))

    def test_bulk_progress_is_recorded_in_shared_exhaust_state(self) -> None:
        exhaust_report = DynamicAccessExhaustReport.create(
            coordinate="org.example:lib:1.0.0",
            issue_number=1412,
        )
        exhaust_report.mark_skipped("D")
        strategy = self._bulk_strategy(
            chunk_class_count=15,
            dynamic_access_exhaust_report=exhaust_report,
        )

        strategy._record_bulk_chunk_progress(
            self._report(["A", "B", "C", "D"], []),
            self._report(["A", "B", "C", "D"], ["A", "B"]),
        )

        self.assertEqual(exhaust_report.completed_classes, ["A", "B"])
        self.assertEqual(exhaust_report.skipped_classes, ["D"])
        self.assertEqual(exhaust_report.class_threshold, 15)
        self.assertEqual(exhaust_report.current_chunk_class_count, 15)
        self.assertEqual(strategy.bulk_chunk_progress.remaining_classes, ("C",))

    def test_composite_routes_post_bulk_quota(self) -> None:
        scenarios = (
            (20, 80, 0, True),
            (10, 90, 5, False),
            (20, 10, 10, False),
            (0, 90, 15, False),
        )

        for completed, remaining, expected_budget, expected_chunk_ready in scenarios:
            with self.subTest(completed=completed, remaining=remaining):
                strategy = self._composite_strategy()
                primary = self._FakePrimary(self._progress(completed, remaining))
                strategy.primary = primary

                report, iterative_budget, chunk_ready = strategy._route_after_bulk()

                self.assertIs(report, primary.bulk_chunk_progress.final_report)
                self.assertEqual(iterative_budget, expected_budget)
                self.assertEqual(chunk_ready, expected_chunk_ready)
                self.assertEqual(primary.saved, expected_chunk_ready)

    def test_composite_passes_shortfall_and_gated_report_to_iterative(self) -> None:
        strategy = self._composite_strategy()
        primary = self._FakePrimary(self._progress(10, 90))
        strategy.primary = primary
        strategy.primary_workflow_name = "bulk_dynamic_access"
        captured_context: dict[str, object] = {}
        captured_reports: list[DynamicAccessCoverageReport] = []

        class FakeDynamicAccess:
            def __init__(self, strategy_obj: dict, **context: object) -> None:
                captured_context.update(context)
                self._last_phase_status = RUN_STATUS_SUCCESS

            def _run_dynamic_access_phase(
                    self,
                    agent: object,
                    report: DynamicAccessCoverageReport,
            ) -> tuple[bool, int]:
                captured_reports.append(report)
                return True, 2

            def has_issue_requested_metadata_context(self) -> bool:
                return False

        class FakeAgent:
            def clear_context(self) -> None:
                pass

        with patch(
                "ai_workflows.core.increase_dynamic_access_coverage_strategy.DynamicAccessIterativeStrategy",
                FakeDynamicAccess,
        ):
            result = strategy.run(FakeAgent())

        self.assertEqual(result, (RUN_STATUS_SUCCESS, 5, 1))
        self.assertEqual(captured_context["chunk_class_count"], 5)
        self.assertEqual(captured_reports, [primary.bulk_chunk_progress.final_report])

    def test_composite_fails_when_required_iterative_shortfall_fails(self) -> None:
        for has_reporter_context in (False, True):
            with self.subTest(has_reporter_context=has_reporter_context):
                strategy = self._composite_strategy()
                strategy.primary = self._FakePrimary(self._progress(10, 90))
                reporter_phase_calls: list[bool] = []

                class FailingDynamicAccess:
                    def __init__(self, strategy_obj: dict, **context: object) -> None:
                        self._last_phase_status = RUN_STATUS_SUCCESS

                    def _run_dynamic_access_phase(
                            self,
                            agent: object,
                            report: DynamicAccessCoverageReport,
                    ) -> tuple[bool, int]:
                        return False, 0

                    def has_issue_requested_metadata_context(self) -> bool:
                        return has_reporter_context

                    def _run_issue_requested_metadata_phase(
                            self,
                            agent: object,
                    ) -> tuple[bool, int]:
                        reporter_phase_calls.append(True)
                        return True, 1

                class FakeAgent:
                    def clear_context(self) -> None:
                        pass

                with patch(
                        "ai_workflows.core.increase_dynamic_access_coverage_strategy."
                        "DynamicAccessIterativeStrategy",
                        FailingDynamicAccess,
                ):
                    result = strategy.run(FakeAgent())

                expected_iterations = 4 if has_reporter_context else 3
                self.assertEqual(result, (RUN_STATUS_FAILURE, expected_iterations, 1))
                self.assertEqual(reporter_phase_calls, [True] if has_reporter_context else [])

    @staticmethod
    def _bulk_strategy(**context: object) -> BulkDynamicAccessStrategy:
        return BulkDynamicAccessStrategy(
            {
                "model": "test-model",
                "prompts": {"bulk-dynamic-access-iteration": "unused"},
                "parameters": {
                    "max-bulk-iterations": 3,
                    "max-test-iterations": 2,
                },
            },
            library="org.example:lib:1.0.0",
            reachability_repo_path="/tmp/reachability",
            test_version="1.0.0",
            **context,
        )

    @staticmethod
    def _composite_strategy() -> IncreaseDynamicAccessCoverageStrategy:
        strategy = IncreaseDynamicAccessCoverageStrategy(
            {
                "model": "test-model",
                "parameters": {},
                "prompts": {},
            },
            reachability_repo_path="/tmp/reachability",
            library="org.example:lib:1.0.0",
            chunk_class_count=15,
        )
        strategy.primary_workflow_name = "bulk_dynamic_access"
        return strategy

    @classmethod
    def _progress(cls, completed: int, remaining: int) -> BulkDynamicAccessProgress:
        final_report = cls._report(
            [f"completed-{index}" for index in range(completed)]
            + [f"remaining-{index}" for index in range(remaining)],
            [f"completed-{index}" for index in range(completed)],
        )
        return BulkDynamicAccessProgress(
            final_report=final_report,
            completed_classes=tuple(f"completed-{index}" for index in range(completed)),
            remaining_classes=tuple(f"remaining-{index}" for index in range(remaining)),
        )

    @staticmethod
    def _report(
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
                DynamicAccessClass(
                    class_name=class_name,
                    source_file=None,
                    resolved_source_file=None,
                    total_calls=1,
                    covered_calls=1 if class_name in covered else 0,
                    call_sites=[],
                )
                for class_name in class_names
            ],
        )

    class _FakePrimary:
        def __init__(self, progress: BulkDynamicAccessProgress) -> None:
            self.bulk_chunk_progress = progress
            self.saved = False
            self.post_generation_intervention = None

        def run(self, agent: object, **kwargs: object) -> tuple[str, int, int]:
            return RUN_STATUS_SUCCESS, 3, 1

        def save_bulk_chunk_state(self) -> None:
            self.saved = True


class BulkDynamicAccessRunTests(unittest.TestCase):
    """The bulk loop must run on the report the run prepared for it."""

    def test_usable_report_starts_bulk_iterations_without_a_fallback(self) -> None:
        strategy, agent = self._runnable_strategy(reports=[self._usable_report()])
        with patch.object(strategy, "_run_basic_iterative_fallback") as fallback:
            status, iterations, generations = strategy.run(agent)

        fallback.assert_not_called()
        self.assertEqual(status, RUN_STATUS_SUCCESS)
        self.assertEqual(generations, 1)
        self.assertEqual(iterations, 1)
        self.assertEqual(len(agent.prompts), 1)

    def test_empty_report_bootstraps_through_the_fallback_then_runs_bulk(self) -> None:
        # A report only becomes usable once tests exist; the fallback creates
        # them and bulk continues on the refreshed report
        # (§AR-dynamic-access-fallback-and-failure).
        strategy, agent = self._runnable_strategy(
            reports=[self._empty_report(), self._usable_report()],
        )
        with patch.object(
                strategy,
                "_run_basic_iterative_fallback",
                return_value=(RUN_STATUS_SUCCESS, 2, 1),
        ) as fallback:
            status, iterations, generations = strategy.run(agent)

        fallback.assert_called_once()
        self.assertEqual(status, RUN_STATUS_SUCCESS)
        self.assertEqual(generations, 2)
        self.assertEqual(iterations, 3)
        self.assertEqual(len(agent.prompts), 1)

    def _runnable_strategy(
            self,
            reports: list[DynamicAccessCoverageReport],
    ) -> tuple[BulkDynamicAccessStrategy, "BulkDynamicAccessRunTests._FakeAgent"]:
        strategy = BulkDynamicAccessChunkTests._bulk_strategy()
        # One report per refresh: the initial one, then one per accepted iteration.
        pending = list(reports) + [reports[-1]]
        strategy._generate_dynamic_access_report = lambda *args, **kwargs: pending.pop(0)
        strategy._render_prompt = lambda *args, **kwargs: "prompt"
        strategy._commit_test_sources = lambda message: None
        strategy.verify_native_test_gate = lambda output_dir: True
        return strategy, self._FakeAgent()

    @staticmethod
    def _usable_report() -> DynamicAccessCoverageReport:
        return BulkDynamicAccessChunkTests._report(["A", "B"], ["A", "B"])

    @staticmethod
    def _empty_report() -> DynamicAccessCoverageReport:
        return DynamicAccessCoverageReport(
            coordinate="org.example:lib:1.0.0",
            has_dynamic_access=False,
            total_calls=0,
            covered_calls=0,
            classes=[],
        )

    class _FakeAgent:
        def __init__(self) -> None:
            self.prompts: list[str] = []

        def clear_context(self) -> None:
            pass

        def send_prompt(self, prompt: str) -> None:
            self.prompts.append(prompt)

        def run_test_command(self, command: str) -> str:
            return "BUILD SUCCESSFUL"


if __name__ == "__main__":
    unittest.main()
