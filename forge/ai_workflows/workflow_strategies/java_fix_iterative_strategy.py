# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

from ai_workflows.workflow_strategies.workflow_strategy import RUN_STATUS_FAILURE, RUN_STATUS_SUCCESS, WorkflowStrategy
from utility_scripts.native_test_verification import (
    STATUS_FAILED as NATIVE_TEST_GATE_FAILED,
    global_output_dir,
    verify_native_test_passes,
)


DEFAULT_MAX_NATIVE_TEST_VERIFICATION_ITERATIONS = 100


"""
                                  ┌───────────────┐
                                  │  Init Agent   │
                                  └───────────────┘
                                         │
                                         ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│ Input for agent: initial prompt, library docs/source, new version error,     │
│ failing test                                                                 │
└──────────────────────────────────────────────────────────────────────────────┘
                                         │
                                         ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│ Looping & testing                                                            │<───────────────────────────────────────┐
└──────────────────────────────────────────────────────────────────────────────┘                                        │
                   ┌─────────────────────│────────────────────────────────┐                                             │
                   ▼                                                      ▼                                             │
┌────────────────┐   ┌───────────────────────┐   ┌──────────────────────┐   ┌───────────────────────────┐               │
│ NativeTest fail│   │ NativeTest success    │   │ compileJava fails    │   │ test (java runtime) fails │               │
└────────────────┘   └───────────────────────┘   └──────────────────────┘   └───────────────────────────┘               │
        │                   │                        │                              │                                   │
        ▼                   │                        ┴──────────────┬───────────────┴                                   │
┌─────────────────┐         │                                       ▼                                                   │
│ Collect metadata│         │                       ┌──────────────────────────────┐   NO                               │
└─────────────────┘         │                       │ maxIterations reached?       │────────────────────────────────────┘
        │                   │                       └──────────────────────────────┘
        └────────END────────┘                                     │
                                                             YES  │
                                                                  │
                                                                  ▼
                                                        ┌─────────────────────┐
                                                        │ Undo all changes    │
                                                        └─────────────────────┘
"""


# Mode type constants
JAVA_FIX_MODE_JAVAC = "javac"
JAVA_FIX_MODE_JAVA_RUN = "java-run"


class _JavaTestFixIterativeBase(WorkflowStrategy):
    """Shared iterative fix strategy for javac compilation and java runtime test failures."""

    REQUIRED_PROMPTS = ["initial"]
    REQUIRED_PARAMS = ["max-test-iterations"]

    def __init__(self, strategy_obj: dict, fix_mode: str, **context):
        super().__init__(strategy_obj, **context)
        self.fix_mode = fix_mode
        self.reachability_repo_path = self.context["reachability_repo_path"]
        self.library = self.context["updated_library"]
        self.group, self.artifact, self.version = self.library.split(":")
        self.package = self.group
        self.max_test_iterations = self.parameters["max-test-iterations"]
        self.max_native_test_verification_iterations = self._parameter_int(
            "max-native-test-verification-iterations",
            DEFAULT_MAX_NATIVE_TEST_VERIFICATION_ITERATIONS,
        )

    @property
    def _log_prefix(self) -> str:
        if self.fix_mode == JAVA_FIX_MODE_JAVA_RUN:
            return "fix-java-run-iterative"
        return "fix-javac-iterative"

    @property
    def _retry_prompt_title(self) -> str:
        return "The following test command failed:"

    @property
    def _retry_detail_message(self) -> str:
        if self.fix_mode == JAVA_FIX_MODE_JAVA_RUN:
            return "agent: runtime test failed before nativeTest; sending failure output back to agent"
        return "agent: test failed before nativeTest; sending failure output back to agent"

    def _print_message(self, message: str) -> None:
        print(f"[{self._log_prefix}] {message}")

    @classmethod
    def _print_detail(cls, message: str, indent_level: int = 1) -> None:
        print(f"{'  ' * indent_level}{message}")

    def run(self, agent):
        workflow_status = RUN_STATUS_FAILURE

        self._print_message("running initial gradle test to collect errors")
        initial_error = agent.run_test_command(f"./gradlew test -Pcoordinates={self.library}")
        self._print_message("running agent...")
        agent.send_prompt(self._render_prompt("initial", initial_error=initial_error))
        self._print_message("agent complete")
        global_iterations = 1

        for test_iter in range(self.max_test_iterations):
            self._print_message(
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
                workflow_status = RUN_STATUS_SUCCESS
                break

            global_iterations += 1
            self._print_detail(
                self._retry_detail_message,
                indent_level=2,
            )
            agent.send_prompt(
                f"{self._retry_prompt_title}\n./gradlew test -Pcoordinates={self.library}\n\nOutput:\n{test_output}"
            )
            self._print_detail("agent: complete", indent_level=2)

        if workflow_status == RUN_STATUS_SUCCESS and self.fix_mode == JAVA_FIX_MODE_JAVA_RUN:
            if not self._run_native_test_verification_gate():
                workflow_status = RUN_STATUS_FAILURE

        agent.clear_context()
        return workflow_status, global_iterations

    def _run_native_test_verification_gate(self) -> bool:
        """Terminal gate: verify nativeTest passes; FAILED aborts the workflow."""
        output_dir = global_output_dir(
            self.reachability_repo_path, self.group, self.artifact, self.version,
        )
        self._print_message(
            f"native-test gate: starting output_dir={output_dir} "
            f"budget={self.max_native_test_verification_iterations}"
        )
        result = verify_native_test_passes(
            reachability_repo_path=self.reachability_repo_path,
            coordinate=self.library,
            output_dir=output_dir,
            max_iterations=self.max_native_test_verification_iterations,
            model_name=self.model_name,
        )
        if result.status == NATIVE_TEST_GATE_FAILED:
            log_path = result.last_native_test_log_path or "(none)"
            self._print_message(
                f"native-test gate FAILED after {result.iterations_used} cycles "
                f"(last log: {log_path})"
            )
            return False
        self._print_message(
            f"native-test gate {result.status} after {result.iterations_used} cycles"
        )
        return True


@WorkflowStrategy.register("javac_iterative")
class JavacIterativeStrategy(_JavaTestFixIterativeBase):
    """Iterative javac fix strategy for version bump test repair."""

    def __init__(self, strategy_obj: dict, **context):
        super().__init__(strategy_obj, fix_mode=JAVA_FIX_MODE_JAVAC, **context)


@WorkflowStrategy.register("java_run_iterative")
class JavaRunIterativeStrategy(_JavaTestFixIterativeBase):
    """Iterative java runtime fix strategy for version bump test repair."""

    def __init__(self, strategy_obj: dict, **context):
        super().__init__(strategy_obj, fix_mode=JAVA_FIX_MODE_JAVA_RUN, **context)
