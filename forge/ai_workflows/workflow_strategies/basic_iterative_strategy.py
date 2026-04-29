# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os
import subprocess

from ai_workflows.workflow_strategies.workflow_strategy import RUN_STATUS_FAILURE, RUN_STATUS_SUCCESS, WorkflowStrategy
from utility_scripts.stage_logger import log_stage


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
        self.package = self.library.split(":")[0]

    @staticmethod
    def _print_message(message: str) -> None:
        log_stage("basic-iterative", message)

    @classmethod
    def _print_detail(cls, message: str, indent_level: int = 1) -> None:
        log_stage("basic-iterative", message, indent_level=indent_level)

    def _commit_test_sources(self, message: str) -> str:
        tests_dir = os.path.join(
            self.reachability_repo_path,
            "tests",
            "src",
            self.group,
            self.artifact,
            self.version,
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
            agent,
            checkpoint_commit_hash,
    ):
        global_iterations = 0
        failed_iterations = 0
        unittest_number = 0

        while failed_iterations < self.max_failed_generations and unittest_number < self.max_successful_generations:
            self._print_message(
                "successful generation {generation}/{max_generations}, failed attempts {attempt}/{max_attempts}".format(
                    generation=unittest_number,
                    max_generations=self.max_successful_generations,
                    attempt=failed_iterations,
                    max_attempts=self.max_failed_generations,
                )
            )
            if failed_iterations > 0:
                self._print_detail("agent: running failed-iteration prompt")
                agent.send_prompt(self.prompt_after_failed)
            else:
                prompt_name = "initial" if unittest_number < 1 else "successful-iteration"
                self._print_detail(f"agent: running {prompt_name} prompt")
                agent.send_prompt(self.prompt_initial if unittest_number < 1 else self.prompt_after_success)
            self._print_detail("agent: complete")

            global_iterations += 1
            reached_native_test = False
            for test_iter in range(self.max_test_iterations):
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
                agent.send_prompt(
                    f"The following test command failed:\n./gradlew test -Pcoordinates={self.library}\n\nOutput:\n{test_output}"
                )
                self._print_detail("agent: complete", indent_level=2)
                global_iterations += 1

            if reached_native_test:
                failed_iterations = 0
                continue

            failed_iterations += 1
            self._print_detail("result: failed before reaching nativeTest, reverting to checkpoint")
            subprocess.run(["git", "reset", "--hard", checkpoint_commit_hash], check=False)

        if unittest_number == 0:
            return RUN_STATUS_FAILURE, global_iterations, unittest_number

        return RUN_STATUS_SUCCESS, global_iterations, unittest_number
