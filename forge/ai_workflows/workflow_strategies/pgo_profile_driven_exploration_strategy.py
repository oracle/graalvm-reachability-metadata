# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os

from ai_workflows.workflow_strategies.dynamic_access_iterative_strategy import DynamicAccessIterativeStrategy
from utility_scripts.pgo_near_call_report import format_pgo_near_call_guidance


DEFAULT_PGO_SAMPLING_PERIOD_MICROS = 100
MINIMUM_PGO_SAMPLING_PERIOD_MICROS = 100


@DynamicAccessIterativeStrategy.register("pgo_profile_driven_exploration")
class PgoProfileDrivenExplorationStrategy(DynamicAccessIterativeStrategy):
    """Dynamic-access workflow that augments prompts with PGO near-call path guidance."""

    PROMPT_KEY = "pgo-profile-driven-exploration"
    REQUIRED_PROMPTS = ["pgo-profile-driven-exploration"]
    REQUIRED_PARAMS = [
        "max-iterations",
        "max-class-test-iterations",
    ]

    def __init__(self, strategy_obj: dict, **context):
        super().__init__(strategy_obj, **context)
        self.pgo_sampling_period_micros = self._parameter_int(
            "pgo-sampling-period-micros",
            DEFAULT_PGO_SAMPLING_PERIOD_MICROS,
        )
        if self.pgo_sampling_period_micros < MINIMUM_PGO_SAMPLING_PERIOD_MICROS:
            raise ValueError(
                "Strategy parameter 'pgo-sampling-period-micros' must be at least "
                f"{MINIMUM_PGO_SAMPLING_PERIOD_MICROS}"
            )
        self.pgo_near_call_report_dir = os.path.join(
            self.reachability_repo_path,
            "tests",
            "src",
            self.group,
            self.artifact,
            self.test_version,
            "build",
            "reports",
            "pgo-near-call",
        )

    def _dynamic_access_prompt_extras(self, active_class) -> dict:
        return {
            "pgo_near_call_guidance": self._refresh_pgo_near_call_guidance(active_class),
        }

    def _refresh_pgo_near_call_guidance(self, active_class) -> str:
        result = self._run_gradle_command_with_output([
            "./gradlew",
            "generatePgoDynamicAccessNearCallReport",
            f"-Pcoordinates={self.library}",
            f"-PpgoSamplingPeriodMicros={self.pgo_sampling_period_micros}",
        ])
        if result.returncode != 0:
            self._print_dynamic_access_detail(
                "PGO near-call report refresh failed: exit_code={exit_code}".format(
                    exit_code=result.returncode,
                ),
                indent_level=2,
            )
            return "- PGO near-call guidance unavailable: Gradle diagnostic task failed."

        guidance = format_pgo_near_call_guidance(
            self.pgo_near_call_report_dir,
            active_class.uncovered_call_sites,
        )
        self._print_dynamic_access_detail("PGO near-call guidance refreshed", indent_level=2)
        return guidance

    def _should_continue_class_attempts(self, class_attempts: int, class_attempts_without_progress: int) -> bool:
        return class_attempts_without_progress < self.max_class_iterations

    def _format_class_attempt_status(self, class_attempts: int, class_attempts_without_progress: int) -> str:
        return "attempt {attempt} (no-progress budget used: {stalled}/{budget})".format(
            attempt=class_attempts + 1,
            stalled=class_attempts_without_progress,
            budget=self.max_class_iterations,
        )

    def _format_class_exhaustion_attempts(self, class_attempts: int, class_attempts_without_progress: int) -> str:
        return "{attempts} ({stalled} consecutive without progress)".format(
            attempts=class_attempts,
            stalled=class_attempts_without_progress,
        )
