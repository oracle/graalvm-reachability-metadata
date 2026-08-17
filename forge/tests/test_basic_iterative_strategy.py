# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import unittest
from unittest.mock import patch

from ai_workflows.core.basic_iterative_strategy import BasicIterativeStrategy
from ai_workflows.core.dynamic_access_iterative_strategy import DynamicAccessIterativeStrategy
from ai_workflows.core.workflow_strategy import RUN_STATUS_FAILURE, RUN_STATUS_SUCCESS
from utility_scripts.dynamic_access_report import DynamicAccessCoverageReport
from utility_scripts.native_test_verification import (
    NativeTestVerificationResult,
    STATUS_FAILED,
    STATUS_PASSED,
)


LIBRARY = "org.example:lib:1.0.0"

# A `./gradlew test` run that reached nativeTest and failed there. The basic
# iterative loop accepts this as progress, which is exactly why the terminal
# native-test gate has to run afterwards (§WF-basic-iterative).
NATIVE_TEST_FAILED_OUTPUT = "> Task :nativeTest FAILED\n"


class _FakeAgent:
    """Minimal agent stub that always reports a nativeTest-stage test run."""

    def __init__(self, test_output: str = NATIVE_TEST_FAILED_OUTPUT) -> None:
        self.test_output = test_output
        self.prompts: list[str] = []

    def send_prompt(self, prompt: str) -> None:
        self.prompts.append(prompt)

    def run_test_command(self, command: str) -> str:
        return self.test_output

    def clear_context(self) -> None:
        pass

    def replace_persistent_instructions(self, instructions: str) -> None:
        pass


def _gate_result(status: str) -> NativeTestVerificationResult:
    return NativeTestVerificationResult(
        status=status,
        output_dir="/tmp/natively-collected/_global_",
        iterations_used=1,
        last_native_test_log_path="/tmp/native-test.log",
    )


def _basic_strategy(**parameters) -> BasicIterativeStrategy:
    strategy_parameters = {
        "max-test-iterations": 1,
        "max-failed-generations": 1,
        "max-successful-generations": 1,
    }
    strategy_parameters.update(parameters)
    return BasicIterativeStrategy(
        {
            "model": "test-model",
            "prompts": {
                "initial": "unused",
                "after-successful-iteration": "unused",
                "after-failed-iteration": "unused",
            },
            "parameters": strategy_parameters,
        },
        library=LIBRARY,
        reachability_repo_path="/tmp/reachability",
        test_version="1.0.0",
    )


class BasicIterativeNativeTestGateTests(unittest.TestCase):
    """The terminal native-test gate runs for every basic iterative invocation."""

    def setUp(self) -> None:
        prompt_patch = patch(
            "ai_workflows.core.workflow_strategy.load_prompt_template",
            return_value="prompt",
        )
        instructions_patch = patch(
            "ai_workflows.core.workflow_strategy.load_persistent_instructions",
            return_value="instructions",
        )
        commit_patch = patch.object(
            BasicIterativeStrategy,
            "_commit_test_sources",
            return_value="commit-sha",
        )
        for active_patch in (prompt_patch, instructions_patch, commit_patch):
            active_patch.start()
            self.addCleanup(active_patch.stop)

    def test_gate_runs_after_a_successful_loop(self) -> None:
        strategy = _basic_strategy()

        with patch(
            "ai_workflows.core.basic_iterative_strategy.verify_native_test_passes",
            return_value=_gate_result(STATUS_PASSED),
        ) as verify:
            status, _, unittest_number = strategy.run(_FakeAgent(), "checkpoint-sha")

        self.assertEqual(status, RUN_STATUS_SUCCESS)
        self.assertEqual(unittest_number, 1)
        verify.assert_called_once()
        self.assertEqual(verify.call_args.kwargs["coordinate"], LIBRARY)
        self.assertTrue(verify.call_args.kwargs["output_dir"].endswith("natively-collected/_global_"))

    def test_gate_uses_the_shared_default_budget(self) -> None:
        strategy = _basic_strategy()

        with patch(
            "ai_workflows.core.basic_iterative_strategy.verify_native_test_passes",
            return_value=_gate_result(STATUS_PASSED),
        ) as verify:
            strategy.run(_FakeAgent(), "checkpoint-sha")

        self.assertEqual(verify.call_args.kwargs["max_iterations"], 40)

    def test_gate_budget_is_configurable(self) -> None:
        strategy = _basic_strategy(**{"max-native-test-verification-iterations": 7})

        with patch(
            "ai_workflows.core.basic_iterative_strategy.verify_native_test_passes",
            return_value=_gate_result(STATUS_PASSED),
        ) as verify:
            strategy.run(_FakeAgent(), "checkpoint-sha")

        self.assertEqual(verify.call_args.kwargs["max_iterations"], 7)

    def test_gate_failure_fails_the_workflow(self) -> None:
        strategy = _basic_strategy()

        with patch(
            "ai_workflows.core.basic_iterative_strategy.verify_native_test_passes",
            return_value=_gate_result(STATUS_FAILED),
        ):
            status, _, _ = strategy.run(_FakeAgent(), "checkpoint-sha")

        self.assertEqual(status, RUN_STATUS_FAILURE)

    def test_gate_is_skipped_when_no_test_suite_was_produced(self) -> None:
        strategy = _basic_strategy()
        agent = _FakeAgent(test_output="> Task :compileTestJava FAILED\n")

        with patch(
            "ai_workflows.core.basic_iterative_strategy.verify_native_test_passes",
        ) as verify:
            with patch("subprocess.run"):
                status, _, unittest_number = strategy.run(agent, "checkpoint-sha")

        self.assertEqual(status, RUN_STATUS_FAILURE)
        self.assertEqual(unittest_number, 0)
        verify.assert_not_called()


class DynamicAccessFallbackNativeTestGateTests(unittest.TestCase):
    """A 0/0 dynamic-access report still reaches native tracing through the fallback.

    Regression test for the transitive-dependency metadata gap: the requested
    artifact reports no dynamic-access call sites, the basic iterative fallback
    generates a working test suite, and `nativeTest` still needs metadata that
    only the native-test gate can trace (§WF-dynamic-access-fallback-and-failure).
    """

    def test_zero_call_report_falls_back_and_still_runs_the_gate(self) -> None:
        empty_report = DynamicAccessCoverageReport(
            coordinate=LIBRARY,
            has_dynamic_access=False,
            total_calls=0,
            covered_calls=0,
            classes=[],
        )
        strategy = DynamicAccessIterativeStrategy(
            {
                "model": "test-model",
                "prompts": {"dynamic-access-iteration": "unused"},
                "parameters": {
                    "max-iterations": 1,
                    "max-class-test-iterations": 1,
                },
            },
            library=LIBRARY,
            reachability_repo_path="/tmp/reachability",
            test_version="1.0.0",
        )
        fallback_obj = {
            "name": "basic_iterative_pi_gpt-5.6-sol",
            "model": "test-model",
            "prompts": {
                "initial": "unused",
                "after-successful-iteration": "unused",
                "after-failed-iteration": "unused",
            },
            "parameters": {
                "max-test-iterations": 1,
                "max-failed-generations": 1,
                "max-successful-generations": 1,
            },
        }

        with patch(
            "ai_workflows.core.workflow_strategy.load_prompt_template",
            return_value="prompt",
        ), patch(
            "ai_workflows.core.workflow_strategy.load_persistent_instructions",
            return_value="instructions",
        ), patch.object(
            BasicIterativeStrategy, "_commit_test_sources", return_value="commit-sha",
        ), patch.object(
            DynamicAccessIterativeStrategy,
            "_generate_dynamic_access_report",
            return_value=empty_report,
        ), patch(
            "ai_workflows.core.dynamic_access_iterative_strategy.load_strategy_by_name",
            return_value=fallback_obj,
        ), patch(
            "ai_workflows.core.basic_iterative_strategy.verify_native_test_passes",
            return_value=_gate_result(STATUS_PASSED),
        ) as verify:
            status, _, _ = strategy.run(_FakeAgent(), "checkpoint-sha")

        self.assertEqual(status, RUN_STATUS_SUCCESS)
        verify.assert_called_once()
        self.assertEqual(verify.call_args.kwargs["coordinate"], LIBRARY)


if __name__ == "__main__":
    unittest.main()
