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
from utility_scripts.dynamic_access_report import DynamicAccessCallSite, DynamicAccessClass


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

    @staticmethod
    def _strategy(
            reachability_repo_path: str,
            pgo_sampling_period_micros: int = 250,
    ) -> PgoProfileDrivenExplorationStrategy:
        return PgoProfileDrivenExplorationStrategy(
            {
                "model": "test-model",
                "prompts": {
                    "pgo-profile-driven-exploration": "unused",
                },
                "parameters": {
                    "max-iterations": 1,
                    "max-class-test-iterations": 1,
                    "pgo-sampling-period-micros": pgo_sampling_period_micros,
                },
            },
            library="org.example:lib:1.0.0",
            reachability_repo_path=reachability_repo_path,
            test_version="1.0.0",
        )


if __name__ == "__main__":
    unittest.main()
