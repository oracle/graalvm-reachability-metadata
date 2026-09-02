# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os
import subprocess

from ai_workflows.agents.agent import send_agent_prompt
from ai_workflows.core.workflow_strategy import RUN_STATUS_FAILURE, RUN_STATUS_SUCCESS, WorkflowStrategy
from utility_scripts.continuation_marker import PHASE_EXPLORE, PHASE_FIX, save_phase_update
from utility_scripts.run_location import (
    PHASE_EXPLORE as RUN_PHASE_EXPLORE,
    STEP_GENERATE_TESTS,
    STEP_NATIVE_TRACE_GATE,
    RunLocation,
    enter_phase,
    log_step_progress,
    record_step_failure,
    run_step,
)
from utility_scripts.metadata_index import resolve_test_version
from utility_scripts.native_test_verification import global_output_dir
from utility_scripts.stage_logger import log_detail


BASIC_ITERATIVE_PERSISTENT_INSTRUCTIONS_PATH = "prompt_templates/persistent/basic_iterative_rules.md"


"""
    ┌─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
    │                                                    Scaffold                                                             │
    │   https://github.com/oracle/graalvm-reachability-metadata/blob/master/docs/CONTRIBUTING.md#generate-metadata-and-test   │
    └─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘
                                                            │
                                                            ▼
                                                ┌──────────────────────┐
                                                │   Init Agent         │
                                                └──────────────────────┘
                                                            │
                                                            ▼
                                            ┌───────────────────────────────────┐
                                            │ Input for agent:                  │
                                            │   - initial prompt                │
                                            │   - docs/source of library        │
                                            │   - test that needs edit          │
                                            └───────────────────────────────────┘
                                                            │
                                                            ▼
                        ┌──────────────────────────────────────────────────────────────────────────┐
    ┌──────────────────►│ Looping and testing                                                      │◄─────────────────────────────────────┐
    │                   └──────────────────────────────────────────────────────────────────────────┘                                      │
    │                     │                 │                       │                         │                                           │
    │                     │                 │                       │                         │                                           │
    │                     ▼                 ▼                       ▼                         ▼                                           │
    │           ┌────────────────┐  ┌────────────────────┐  ┌──────────────────────┐  ┌───────────────────────────┐                       │
    │           │ NativeTest     │  │ NativeTest         │  │ compileJava          │  │ test (java runtime)       │                       │
    │           │ fails          │  │ succeeds           │  │ fails                │  │ fails                     │                       │
    │           └────────────────┘  └────────────────────┘  └──────────────────────┘  └───────────────────────────┘                       │
    │                     │                 │                       │                         │                                           │
    │                     ▼                 │                       └──────────────┬──────────┘                                           │
    │           ┌──────────────────────┐    │                                      ▼                                                      │
    │           │ Clear agent context  │    │                         ┌──────────────────────────────┐      NO                            │
    │           └──────────────────────┘    │                         │     MAX_TEST_ITERATIONS?     │────────────────────────────────────┘
    │                     │                 │                         │                              │                                    │
    │                     ▼                 │                         └──────────────┬───────────────┘                                    │
    │           ┌──────────────────────┐    │                                        │                                                    │
    │           │ Add checkpoint       │    │                                     YES│                                                    │
    │           └──────────────────────┘    │                                        ▼                                                    │
    │                     │                 │                        ┌──────────────────────────────┐                                     │
    │                     │                 └───────────────────────►│ Return to checkpoint         │                                     │
    │                     ▼                                          └──────────────────────────────┘                                     │
    │               NO  ┌──────────────────────────────────────┐                      │                                                   │
    └───────────────────│      MAX_SUCCESSFUL_GENERATIONS      │                      ▼                                                   │
                        └──────────────┬───────────────────────┘              ┌────────────────────────────┐    NO                        │
                                       │                                      │  MAX_FAILED_GENERATIONS?   │──────────────────────────────┘
                                    YES│                                      └────────┬───────────────────┘
                                       │                                               │
                                       └─────────────────────────────┐                 │
                                                                     │          YES    │
                                                                     │                 ▼
                                                                     │        ┌──────────────────────┐
                                                                     └───────►│ Collect Metadata     │
                                                                              └──────────────────────┘
                                                                                       │
                                                                                       ▼
                                                                ┌────────────────────────────────────────────────────┐
                                                                │   Test again to see if the script was successful   │
                                                                └────────────────────────────────────────────────────┘
                                                                                       │
                                                                                       ▼
                                                                                      END
"""


@WorkflowStrategy.register("basic_iterative")
class BasicIterativeStrategy(WorkflowStrategy):
    """Iterative add-new-library workflow strategy."""

    REQUIRED_PROMPTS = ["initial", "after-successful-iteration", "after-failed-iteration"]
    REQUIRED_PARAMS = ["max-test-iterations", "max-failed-generations", "max-successful-generations"]

    def __init__(self, strategy_obj: dict, **context):
        super().__init__(strategy_obj, **context)
        self.library = self.context["library"]
        self.prompt_initial = self._load_prompt("initial")
        self.prompt_after_success = self._load_prompt("after-successful-iteration")
        self.prompt_after_failed = self._load_prompt("after-failed-iteration")
        self.max_test_iterations = self.parameters["max-test-iterations"]
        self.max_failed_generations = self.parameters["max-failed-generations"]
        self.max_successful_generations = self.parameters["max-successful-generations"]
        self.reachability_repo_path = self.context["reachability_repo_path"]
        self.group, self.artifact, self.version = self.library.split(":")
        self.test_version = str(
            self.context.get("test_version")
            or resolve_test_version(self.reachability_repo_path, self.group, self.artifact, self.version)
        )
        self.package = self.library.split(":")[0]

    @staticmethod
    def _print_message(message: str) -> None:
        log_detail("basic-iterative", message)

    @classmethod
    def _print_detail(cls, message: str, indent_level: int = 1) -> None:
        log_detail("basic-iterative", message, indent_level=indent_level)

    def _commit_test_sources(self, message: str) -> str:
        tests_dir = os.path.join(
            self.reachability_repo_path,
            "tests",
            "src",
            self.group,
            self.artifact,
            self.test_version,
        )
        subprocess.run(
            ["git", "add", "-A", tests_dir],
            cwd=self.reachability_repo_path,
            check=False,
        )
        diff_result = subprocess.run(
            ["git", "diff", "--cached", "--quiet", "--", tests_dir],
            cwd=self.reachability_repo_path,
            check=False,
        )
        if diff_result.returncode != 0:
            subprocess.run(
                ["git", "commit", "-m", message],
                cwd=self.reachability_repo_path,
                capture_output=True,
                check=False,
            )
        return subprocess.check_output(
            ["git", "rev-parse", "HEAD"],
            cwd=self.reachability_repo_path,
            text=True,
        ).strip()

    def run(
            self,
            agent: object,
            checkpoint_commit_hash: str,
            complete_explore_phase: bool = True,
    ) -> tuple[str, int, int]:
        """Generate unguided tests as exploration.

        The caller may retain phase ownership for later bulk or iterative work
        (§FS-forge-run-continuation.1).
        """
        save_phase_update(
            self.continuation_marker_path,
            lambda marker: (
                marker.mark_phase_skipped_if_pending(PHASE_FIX),
                marker.mark_phase_running(PHASE_EXPLORE),
            ),
        )
        enter_phase(RUN_PHASE_EXPLORE)
        global_iterations = 0
        failed_iterations = 0
        unittest_number = 0

        while failed_iterations < self.max_failed_generations and unittest_number < self.max_successful_generations:
            generation_number = unittest_number + 1
            generation_label = (
                f"retry after failed generation {failed_iterations}/{self.max_failed_generations}"
                if failed_iterations > 0
                else f"generation {generation_number}/{self.max_successful_generations}"
            )
            log_step_progress(
                RUN_PHASE_EXPLORE,
                STEP_GENERATE_TESTS,
                f"Generating tests: unguided {generation_label}",
            )
            self._print_message(
                "successful generation {generation}/{max_generations}, failed attempts {attempt}/{max_attempts}".format(
                    generation=unittest_number,
                    max_generations=self.max_successful_generations,
                    attempt=failed_iterations,
                    max_attempts=self.max_failed_generations,
                )
            )
            with run_step(RUN_PHASE_EXPLORE, STEP_GENERATE_TESTS, operand=self.library):
                if failed_iterations > 0:
                    self._print_detail("agent: running failed-iteration prompt")
                    send_agent_prompt(
                        agent, self.prompt_after_failed, "failed_iteration()",
                    )
                else:
                    prompt_name = "initial" if unittest_number < 1 else "successful-iteration"
                    self._print_detail(f"agent: running {prompt_name} prompt")
                    send_agent_prompt(
                        agent,
                        self.prompt_initial if unittest_number < 1 else self.prompt_after_success,
                        "initial_generation()" if unittest_number < 1 else "successful_iteration()",
                    )
                self._print_detail("agent: complete")

            global_iterations += 1
            save_phase_update(
                self.continuation_marker_path,
                lambda marker: marker.record_iteration(PHASE_EXPLORE, global_iterations),
            )
            reached_native_test = False
            for test_iter in range(self.max_test_iterations):
                log_step_progress(
                    RUN_PHASE_EXPLORE,
                    STEP_GENERATE_TESTS,
                    f"Running test {test_iter + 1}/{self.max_test_iterations}",
                    indent_level=1,
                )
                self._print_detail(
                    "test {test_iteration}/{max_test_iterations}".format(
                        test_iteration=test_iter + 1,
                        max_test_iterations=self.max_test_iterations,
                    )
                )
                self._print_detail(
                    "test: running ./gradlew test -Pcoordinates={library}".format(
                        library=self.library,
                    ),
                    indent_level=2,
                )
                test_output = agent.run_test_command(f"./gradlew test -Pcoordinates={self.library}")
                failed_task = self._get_first_failed_task(test_output)
                if failed_task == "nativeTest":
                    test_outcome = "reached nativeTest"
                elif failed_task is None:
                    test_outcome = "passed"
                else:
                    test_outcome = f"failed at {failed_task}"
                log_step_progress(
                    RUN_PHASE_EXPLORE,
                    STEP_GENERATE_TESTS,
                    f"Test {test_iter + 1}/{self.max_test_iterations} {test_outcome}",
                    indent_level=1,
                )
                self._print_detail(
                    "test: complete (failed task: {failed_task})".format(
                        failed_task=failed_task or "none",
                    ),
                    indent_level=2,
                )

                if failed_task in {"nativeTest", None}:
                    if failed_task == "nativeTest":
                        self._print_detail("result: reached nativeTest", indent_level=2)
                    else:
                        self._print_detail("result: tests passed without metadata", indent_level=2)
                    reached_native_test = True
                    unittest_number += 1
                    checkpoint_commit_hash = self._commit_test_sources(
                        f"Checkpoint generated tests for {self.library} ({unittest_number})"
                    )
                    agent.clear_context()
                    break

                self._print_detail(
                    "agent: test failed before nativeTest; sending failure output back to agent",
                    indent_level=2,
                )
                log_step_progress(
                    RUN_PHASE_EXPLORE,
                    STEP_GENERATE_TESTS,
                    f"Running feedback fix after {failed_task}",
                    indent_level=2,
                )
                send_agent_prompt(
                    agent,
                    f"The following test command failed:\n./gradlew test -Pcoordinates={self.library}\n\nOutput:\n{test_output}",
                    "feedback_fix()",
                )
                self._print_detail("agent: complete", indent_level=2)
                global_iterations += 1

            if reached_native_test:
                failed_iterations = 0
                continue

            failed_iterations += 1
            log_step_progress(
                RUN_PHASE_EXPLORE,
                STEP_GENERATE_TESTS,
                "Generation failed before nativeTest; reverting generated tests",
            )
            self._print_detail("result: failed before reaching nativeTest, reverting to checkpoint")
            subprocess.run(["git", "reset", "--hard", checkpoint_commit_hash], check=False)

        if unittest_number == 0:
            save_phase_update(
                self.continuation_marker_path,
                lambda marker: marker.mark_phase_pending(PHASE_EXPLORE, iteration=global_iterations),
            )
            record_step_failure(
                location=RunLocation(RUN_PHASE_EXPLORE, STEP_GENERATE_TESTS, self.library),
            )
            return RUN_STATUS_FAILURE, global_iterations, unittest_number

        # The loop above accepts a failing nativeTest as progress, so the gate is what
        # validates native-image behavior and traces misses this workflow cannot see —
        # including transitive-dependency metadata (§AR-basic-iterative).
        with run_step(RUN_PHASE_EXPLORE, STEP_NATIVE_TRACE_GATE, operand=self.library):
            gate_passed = self.verify_native_test_gate(global_output_dir(
                self.reachability_repo_path, self.group, self.artifact, self.test_version,
            ))
        if not gate_passed:
            save_phase_update(
                self.continuation_marker_path,
                lambda marker: marker.mark_phase_pending(PHASE_EXPLORE, iteration=global_iterations),
            )
            record_step_failure(
                location=RunLocation(RUN_PHASE_EXPLORE, STEP_NATIVE_TRACE_GATE, self.library),
            )
            return RUN_STATUS_FAILURE, global_iterations, unittest_number

        if complete_explore_phase:
            save_phase_update(
                self.continuation_marker_path,
                lambda marker: marker.mark_phase_completed(PHASE_EXPLORE, iteration=global_iterations),
            )
        return RUN_STATUS_SUCCESS, global_iterations, unittest_number
