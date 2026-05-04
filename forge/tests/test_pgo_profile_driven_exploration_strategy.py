# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import json
import os
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from ai_workflows.workflow_strategies.pgo_profile_driven_exploration_strategy import (
    PgoNearCallGuidanceUnavailableError,
    PgoProfileDrivenExplorationStrategy,
)
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

    def test_refresh_fails_hard_when_pgo_diagnostic_task_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            strategy = self._strategy(tmpdir)
            active_class = self._report(0).classes[0]

            with patch.object(
                    strategy,
                    "_run_gradle_command_with_output",
                    return_value=subprocess.CompletedProcess(
                        args=[],
                        returncode=1,
                        stdout="Execution failed for task ':nativeTestCompile'.",
                    ),
            ), patch("builtins.print") as print_output:
                with self.assertRaisesRegex(PgoNearCallGuidanceUnavailableError, "gradle_diagnostic_task_failed"):
                    strategy._refresh_pgo_near_call_guidance(active_class)

        print_output.assert_any_call("Execution failed for task ':nativeTestCompile'.")

    def test_refresh_returns_unavailable_guidance_when_formatter_has_no_actionable_guidance(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            strategy = self._strategy(tmpdir)
            active_class = self._report(0).classes[0]

            with patch.object(
                    strategy,
                    "_run_gradle_command_with_output",
                    return_value=subprocess.CompletedProcess(args=[], returncode=0, stdout=""),
            ), patch(
                    "ai_workflows.workflow_strategies.pgo_profile_driven_exploration_strategy.format_pgo_near_call_guidance",
                    return_value="- PGO near-call guidance unavailable: no sampled path matched the uncovered call sites.",
            ):
                guidance = strategy._refresh_pgo_near_call_guidance(active_class)

        self.assertTrue(guidance.startswith("- PGO near-call guidance unavailable:"))

    def test_prepare_attempt_marks_unreachable_calls_and_skips_class_when_pgo_has_no_guidance(self) -> None:
        class FakeAgent:
            def send_prompt(self, prompt: str) -> str:
                return json.dumps({
                    "summary": "linux-only branch cannot reach macOS cleaner",
                    "callSites": [
                        {
                            "metadataType": "reflection",
                            "trackedApi": "java.lang.Class#forName(java.lang.String)",
                            "frame": "example.TargetHolder.call(TargetHolder.java:41)",
                            "line": 41,
                            "reachable": False,
                            "reason": "Guarded by an OS check that excludes this host.",
                            "requiredChanges": "",
                            "confidence": "high",
                        },
                        {
                            "metadataType": "reflection",
                            "trackedApi": "java.lang.Class#forName(java.lang.String)",
                            "frame": "example.TargetHolder.call(TargetHolder.java:42)",
                            "line": 42,
                            "reachable": False,
                            "reason": "Guarded by an OS check that excludes this host.",
                            "requiredChanges": "",
                            "confidence": "high",
                        },
                        {
                            "metadataType": "reflection",
                            "trackedApi": "java.lang.Class#forName(java.lang.String)",
                            "frame": "example.TargetHolder.call(TargetHolder.java:43)",
                            "line": 43,
                            "reachable": False,
                            "reason": "Guarded by an OS check that excludes this host.",
                            "requiredChanges": "",
                            "confidence": "high",
                        },
                    ],
                })

        with tempfile.TemporaryDirectory() as tmpdir:
            strategy = self._strategy(tmpdir)
            active_class = self._report(0).classes[0]

            with patch.object(
                    strategy,
                    "_refresh_pgo_near_call_guidance",
                    return_value="- PGO near-call guidance unavailable: no sampled path matched the uncovered call sites.",
            ), patch.object(
                    strategy,
                    "_render_reachability_analysis_prompt",
                    return_value="analysis prompt",
            ), patch.object(strategy, "_collect_test_runtime_classpath", return_value=["/tmp/lib.jar"]):
                should_attempt = strategy._prepare_dynamic_access_attempt(FakeAgent(), active_class, self._report(0), 0)

        self.assertFalse(should_attempt)
        self.assertEqual(len(strategy.dynamic_access_unreachable), 3)
        self.assertEqual(
            strategy.dynamic_access_unreachable[0]["reason"],
            "Guarded by an OS check that excludes this host.",
        )

    def test_prepare_attempt_fails_when_pgo_has_no_guidance_but_call_is_reachable(self) -> None:
        class FakeAgent:
            def send_prompt(self, prompt: str) -> str:
                return json.dumps({
                    "summary": "reachable by adding a dependency",
                    "callSites": [
                        {
                            "metadataType": "reflection",
                            "trackedApi": "java.lang.Class#forName(java.lang.String)",
                            "frame": "example.TargetHolder.call(TargetHolder.java:41)",
                            "line": 41,
                            "reachable": True,
                            "reason": "Reachable by adding an optional test dependency.",
                            "requiredChanges": "Add the optional module to build.gradle.",
                            "confidence": "medium",
                        },
                    ],
                })

        with tempfile.TemporaryDirectory() as tmpdir:
            strategy = self._strategy(tmpdir)
            active_class = self._report(0).classes[0]

            with patch.object(
                    strategy,
                    "_refresh_pgo_near_call_guidance",
                    return_value="- PGO near-call guidance unavailable: no sampled path matched the uncovered call sites.",
            ), patch.object(
                    strategy,
                    "_render_reachability_analysis_prompt",
                    return_value="analysis prompt",
            ), patch.object(strategy, "_collect_test_runtime_classpath", return_value=["/tmp/lib.jar"]):
                with self.assertRaisesRegex(PgoNearCallGuidanceUnavailableError, "near_call_guidance_unavailable"):
                    strategy._prepare_dynamic_access_attempt(FakeAgent(), active_class, self._report(0), 0)

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
                    "pgo-reachability-analysis": "unused",
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
