# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from ai_workflows.workflow_strategies.pgo_profile_driven_exploration_strategy import PgoProfileDrivenExplorationStrategy
from utility_scripts.dynamic_access_report import DynamicAccessCallSite, DynamicAccessClass, DynamicAccessCoverageReport


class PgoProfileDrivenExplorationStrategyTests(unittest.TestCase):
    def test_refresh_runs_pgo_diagnostic_task_and_formats_guidance_for_active_class(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            strategy = self._strategy(tmpdir)
            active_class = DynamicAccessClass(
                class_name="example.TargetHolder",
                source_file="TargetHolder.java",
                resolved_source_file=None,
                total_calls=1,
                covered_calls=0,
                call_sites=[
                    DynamicAccessCallSite(
                        metadata_type="reflection",
                        tracked_api="java.lang.Class#forName(java.lang.String)",
                        frame="example.TargetHolder.call(TargetHolder.java:42)",
                        line=42,
                        covered=False,
                    )
                ],
            )

            with patch.object(
                    strategy,
                    "_run_gradle_command_with_output",
                    return_value=subprocess.CompletedProcess(args=[], returncode=0, stdout=""),
            ) as run_gradle, patch(
                    "ai_workflows.workflow_strategies.pgo_profile_driven_exploration_strategy.format_pgo_near_call_guidance",
                    return_value="guidance",
            ) as format_guidance:
                result = strategy._refresh_pgo_near_call_guidance(active_class)

        self.assertEqual(result, "guidance")
        run_gradle.assert_called_once_with([
            "./gradlew",
            "generatePgoDynamicAccessNearCallReport",
            "-Pcoordinates=org.example:lib:1.0.0",
            "-PpgoSamplingPeriodMicros=250",
        ])
        format_guidance.assert_called_once_with(
            os.path.join(
                tmpdir,
                "tests",
                "src",
                "org.example",
                "lib",
                "1.0.0",
                "build",
                "reports",
                "pgo-near-call",
            ),
            active_class.uncovered_call_sites,
        )

    def test_strategy_rejects_sampling_periods_below_native_image_minimum(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            with self.assertRaisesRegex(ValueError, "must be at least 100"):
                self._strategy(tmpdir, pgo_sampling_period_micros=99)

    def test_class_attempt_budget_resets_when_coverage_improves(self) -> None:
        class FakeAgent:
            def __init__(self) -> None:
                self.prompts = 0

            def send_prompt(self, prompt: str) -> None:
                self.prompts += 1

            def run_test_command(self, command: str) -> str:
                return "BUILD SUCCESSFUL"

            def clear_context(self) -> None:
                pass

        initial_report = self._report(0)
        report_one_covered = self._report(1)
        report_two_covered = self._report(2)
        fake_agent = FakeAgent()

        with tempfile.TemporaryDirectory() as tmpdir:
            strategy = self._strategy(tmpdir, max_iterations=3)
            with patch.object(strategy, "_refresh_pgo_near_call_guidance", return_value="guidance"), \
                    patch.object(strategy, "_render_prompt", return_value="prompt"), \
                    patch.object(
                        strategy,
                        "_generate_dynamic_access_report",
                        side_effect=[
                            report_one_covered,
                            report_two_covered,
                            report_two_covered,
                            report_two_covered,
                            report_two_covered,
                        ],
                    ), \
                    patch.object(strategy, "_commit_test_sources"), \
                    patch.object(strategy, "_run_native_test_verification_gate", return_value=True), \
                    patch.object(strategy, "_refresh_report_after_gate", return_value=None), \
                    patch.object(strategy, "_library_test_change_signature", return_value="clean"), \
                    patch(
                        "ai_workflows.workflow_strategies.dynamic_access_iterative_strategy.subprocess.check_output",
                        return_value="checkpoint\n",
                    ):
                phase_ok, iterations = strategy._run_dynamic_access_phase(fake_agent, initial_report)

        self.assertTrue(phase_ok)
        self.assertEqual(iterations, 5)
        self.assertEqual(fake_agent.prompts, 5)

    @staticmethod
    def _strategy(
            reachability_repo_path: str,
            pgo_sampling_period_micros: int = 250,
            max_iterations: int = 1,
    ) -> PgoProfileDrivenExplorationStrategy:
        return PgoProfileDrivenExplorationStrategy(
            {
                "model": "test-model",
                "prompts": {
                    "pgo-profile-driven-exploration": "unused",
                },
                "parameters": {
                    "max-iterations": max_iterations,
                    "max-class-test-iterations": 1,
                    "pgo-sampling-period-micros": pgo_sampling_period_micros,
                },
            },
            library="org.example:lib:1.0.0",
            reachability_repo_path=reachability_repo_path,
            test_version="1.0.0",
        )

    @staticmethod
    def _report(covered_calls: int) -> DynamicAccessCoverageReport:
        return DynamicAccessCoverageReport(
            coordinate="org.example:lib:1.0.0",
            has_dynamic_access=True,
            total_calls=3,
            covered_calls=covered_calls,
            classes=[
                DynamicAccessClass(
                    class_name="example.TargetHolder",
                    source_file="TargetHolder.java",
                    resolved_source_file=None,
                    total_calls=3,
                    covered_calls=covered_calls,
                    call_sites=[
                        DynamicAccessCallSite(
                            metadata_type="reflection",
                            tracked_api="java.lang.Class#forName(java.lang.String)",
                            frame=f"example.TargetHolder.call(TargetHolder.java:{line})",
                            line=line,
                            covered=index < covered_calls,
                        )
                        for index, line in enumerate([41, 42, 43])
                    ],
                )
            ],
        )


if __name__ == "__main__":
    unittest.main()
