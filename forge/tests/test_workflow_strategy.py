# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os
import unittest
from unittest.mock import patch

from ai_workflows.workflow_strategies.workflow_strategy import RUN_STATUS_SUCCESS, WorkflowStrategy


class _TestWorkflowStrategy(WorkflowStrategy):
    def run(self) -> dict:
        return {}


class WorkflowStrategyTests(unittest.TestCase):
    def test_post_generation_tests_use_current_graalvm_until_final_ci_verification(self) -> None:
        strategy = _TestWorkflowStrategy(
            {"model": "test-model"},
            reachability_repo_path="/tmp/reachability",
            library="org.example:demo:1.0.0",
        )
        commands: list[tuple[str, dict[str, str] | None]] = []

        def run_command(command: str, env: dict[str, str] | None = None) -> str:
            commands.append((command, env))
            return "BUILD SUCCESSFUL"

        with patch.object(strategy, "_run_command_with_env", side_effect=run_command), \
                patch.dict(os.environ, {"GRAALVM_HOME": "/dev/graalvm", "JAVA_HOME": "/dev/graalvm"}, clear=True):
            status = strategy._run_test_with_retry("org.example:demo:1.0.0")

        self.assertEqual(status, RUN_STATUS_SUCCESS)
        self.assertEqual(len(commands), 2)
        self.assertIsNone(commands[0][1])
        self.assertEqual(commands[1][1]["GRAALVM_HOME"], "/dev/graalvm")
        self.assertEqual(commands[1][1]["JAVA_HOME"], "/dev/graalvm")
        self.assertEqual(commands[1][1]["GVM_TCK_NATIVE_IMAGE_MODE"], "future-defaults-all")


if __name__ == "__main__":
    unittest.main()
