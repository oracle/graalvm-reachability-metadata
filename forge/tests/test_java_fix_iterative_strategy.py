# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import unittest
from unittest.mock import patch

from ai_workflows.core.java_fix_iterative_strategy import (
    JavacIterativeStrategy,
    JavaRunIterativeStrategy,
)
from ai_workflows.core.workflow_strategy import RUN_STATUS_FAILURE, RUN_STATUS_SUCCESS
from utility_scripts.native_test_verification import (
    NativeTestVerificationResult,
    STATUS_FAILED,
    STATUS_PASSED,
)


LIBRARY = "org.example:demo:2.0.0"


class _PassingAgent:
    def send_prompt(self, _prompt: str) -> None:
        pass

    def run_test_command(self, _command: str) -> str:
        return "BUILD SUCCESSFUL"

    def clear_context(self) -> None:
        pass


def _strategy(
        strategy_class: type[JavacIterativeStrategy] | type[JavaRunIterativeStrategy],
) -> JavacIterativeStrategy | JavaRunIterativeStrategy:
    return strategy_class(
        {
            "model": "test-model",
            "prompts": {"initial": "unused"},
            "parameters": {"max-test-iterations": 1},
        },
        reachability_repo_path="/tmp/reachability",
        updated_library=LIBRARY,
    )


def _gate_result(status: str) -> NativeTestVerificationResult:
    return NativeTestVerificationResult(
        status=status,
        output_dir="/tmp/natively-collected/_global_",
        iterations_used=1,
        last_native_test_log_path="/tmp/native-test.log",
    )


class JavaFixIterativeNativeGateTests(unittest.TestCase):
    """Every successful Java repair invokes the gate. §AR-native-test-verification-callers"""

    def test_both_fix_modes_run_the_native_gate(self) -> None:
        for strategy_class in (JavacIterativeStrategy, JavaRunIterativeStrategy):
            with self.subTest(strategy_class=strategy_class.__name__):
                strategy = _strategy(strategy_class)
                with patch.object(strategy, "_render_prompt", return_value="prompt"), \
                        patch(
                            "ai_workflows.core.workflow_strategy.verify_native_test_passes",
                            return_value=_gate_result(STATUS_PASSED),
                        ) as gate:
                    status, _iterations = strategy.run(_PassingAgent())

                self.assertEqual(status, RUN_STATUS_SUCCESS)
                gate.assert_called_once()
                self.assertEqual(gate.call_args.kwargs["coordinate"], LIBRARY)
                self.assertTrue(
                    gate.call_args.kwargs["output_dir"].endswith(
                        "2.0.0/build/natively-collected/_global_",
                    )
                )

    def test_gate_failure_fails_a_javac_repair(self) -> None:
        strategy = _strategy(JavacIterativeStrategy)
        with patch.object(strategy, "_render_prompt", return_value="prompt"), \
                patch(
                    "ai_workflows.core.workflow_strategy.verify_native_test_passes",
                    return_value=_gate_result(STATUS_FAILED),
                ):
            status, _iterations = strategy.run(_PassingAgent())

        self.assertEqual(status, RUN_STATUS_FAILURE)


if __name__ == "__main__":
    unittest.main()
