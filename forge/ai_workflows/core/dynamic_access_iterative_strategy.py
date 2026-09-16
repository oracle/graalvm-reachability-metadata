# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os
import subprocess

from ai_workflows.core.dynamic_access_class_loop import DynamicAccessClassLoop
from ai_workflows.core.dynamic_access_report_support import DynamicAccessReportSupport
from ai_workflows.core.dynamic_access_run_support import DynamicAccessRunSupport
from ai_workflows.core.workflow_strategy import (
    RUN_STATUS_CHUNK_READY,
    RUN_STATUS_FAILURE,
    RUN_STATUS_SUCCESS,
    WorkflowStrategy,
)
from utility_scripts.dynamic_access_exhaust_report import DynamicAccessExhaustReport
from utility_scripts.metadata_index import resolve_test_version
from utility_scripts.strategy_loader import load_strategy_by_name


FALLBACK_STRATEGY_NAME = "basic_iterative_pi_gpt-5.6-sol"
DEFAULT_NATIVE_TEST_VERIFICATION_BATCH_SIZE = 5


@WorkflowStrategy.register("dynamic_access_iterative")
class DynamicAccessIterativeStrategy(
    DynamicAccessClassLoop,
    DynamicAccessRunSupport,
    DynamicAccessReportSupport,
    WorkflowStrategy,
):
    """Iterative strategy guided by per-class dynamic-access coverage.

    This is the engine behind §AR-dynamic-access-iterative: it owns
    fallback selection, uncovered-class prompting, coverage deltas, per-class
    checkpointing, native-test gate batching, and chunk-ready returns.
    """

    REQUIRED_PROMPTS = ["dynamic-access-iteration"]
    REQUIRED_PARAMS = [
        "max-iterations",
        "max-class-test-iterations",
    ]

    def __init__(self, strategy_obj: dict, **context):
        super().__init__(strategy_obj, **context)
        self.library = self.context["library"]
        self.reachability_repo_path = self.context["reachability_repo_path"]
        self.keep_tests_without_dynamic_access = bool(self.context.get("keep_tests_without_dynamic_access", False))
        self._last_dynamic_access_report_issue = "not_run"
        self.group, self.artifact, self.version = self.library.split(":")
        self.test_version = str(
            self.context.get("test_version")
            or resolve_test_version(self.reachability_repo_path, self.group, self.artifact, self.version)
        )
        self.package = self.group
        self.max_class_iterations = self.parameters["max-iterations"]
        # The established configuration key budgets complete repair rounds.
        # §FS-predefined-strategy-parameter-families
        self.max_class_test_repairs = self.parameters["max-class-test-iterations"]
        self.native_test_verification_batch_size = self._parameter_int(
            "native-test-verification-batch-size",
            DEFAULT_NATIVE_TEST_VERIFICATION_BATCH_SIZE,
        )
        if self.native_test_verification_batch_size < 1:
            raise ValueError("Strategy parameter 'native-test-verification-batch-size' must be >= 1")
        self.dynamic_access_exhaust_report: DynamicAccessExhaustReport | None = self.context.get(
            "dynamic_access_exhaust_report",
        )
        self.dynamic_access_exhaust_report_path: str | None = self.context.get(
            "dynamic_access_exhaust_report_path",
        )
        self.preceding_dynamic_access_covered_call_gain: int = int(
            self.context.get("preceding_dynamic_access_covered_call_gain") or 0
        )
        self.chunk_class_count = int(self.context.get("chunk_class_count") or 0)
        self._last_phase_status = RUN_STATUS_SUCCESS
        self._latest_class_checkpoint: str | None = None
        self.dynamic_access_report_path = os.path.join(
            self.reachability_repo_path,
            "tests",
            "src",
            self.group,
            self.artifact,
            self.test_version,
            "build",
            "reports",
            "dynamic-access",
            "dynamic-access-coverage.json",
        )

    def run(self, agent, checkpoint_commit_hash):
        """Run one dynamic-access generation attempt.

        Basic fallback is allowed only before dynamic-access guidance is
        available; once the class loop begins, report loss and gate failure are
        hard workflow failures, per §AR-dynamic-access-fallback-and-failure.
        """
        self._latest_class_checkpoint = checkpoint_commit_hash
        initial_report = self._generate_dynamic_access_report()
        if self._should_fallback_to_basic_flow(initial_report):
            self._print_dynamic_access_message(
                "Falling back to basic iterative metadata flow: "
                f"cause={self._dynamic_access_fallback_cause()} "
                f"previous_report={self._current_dynamic_access_status()} "
                f"coordinate={self.library}"
            )
            return self._run_basic_iterative_fallback(agent, checkpoint_commit_hash)

        global_iterations = 0
        phase_ok, extra_iterations = self._run_dynamic_access_phase(agent, initial_report)
        global_iterations += extra_iterations
        if self._last_phase_status == RUN_STATUS_CHUNK_READY:
            return RUN_STATUS_CHUNK_READY, global_iterations, 1
        if not phase_ok:
            self._locate_explore_failure()
            recovery_checkpoint = self._latest_class_checkpoint or checkpoint_commit_hash
            subprocess.run(["git", "reset", "--hard", recovery_checkpoint], check=False)
            return RUN_STATUS_FAILURE, global_iterations, 0

        return RUN_STATUS_SUCCESS, global_iterations, 1

    def _run_basic_iterative_fallback(self, agent, checkpoint_commit_hash):
        """Instantiate a BasicIterativeStrategy and delegate to it."""
        from ai_workflows.core.basic_iterative_strategy import (
            BASIC_ITERATIVE_PERSISTENT_INSTRUCTIONS_PATH,
            BasicIterativeStrategy,
        )
        fallback_obj = load_strategy_by_name(FALLBACK_STRATEGY_NAME)
        if fallback_obj is None:
            raise ValueError(f"Fallback strategy '{FALLBACK_STRATEGY_NAME}' not found in predefined strategies")
        fallback_obj = dict(fallback_obj)
        fallback_obj["persistent-instructions"] = BASIC_ITERATIVE_PERSISTENT_INSTRUCTIONS_PATH
        fallback = BasicIterativeStrategy(fallback_obj, **self.context)
        agent.replace_persistent_instructions(fallback.persistent_instructions)
        return fallback.run(agent, checkpoint_commit_hash)
