# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os
import json
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from ai_workflows.core.workflow_strategy import (
    RUN_STATUS_CHUNK_READY,
    RUN_STATUS_FAILURE,
    RUN_STATUS_SUCCESS,
    SUCCESS_WITH_INTERVENTION_STATUS,
    WorkflowStrategy,
)


class _TestWorkflowStrategy(WorkflowStrategy):
    def run(self) -> dict:
        return {}


class WorkflowStrategyTests(unittest.TestCase):
    def test_post_generation_tests_run_latest_and_graalvm_25_current_defaults_lanes(self) -> None:
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
                patch.dict(
                    os.environ,
                    {
                        "GRAALVM_HOME": "/dev/graalvm",
                        "JAVA_HOME": "/dev/graalvm",
                        "GRAALVM_HOME_25_0": "/dev/graalvm-25",
                    },
                    clear=True,
                ):
            status = strategy._run_test_with_retry("org.example:demo:1.0.0")

        self.assertEqual(status, RUN_STATUS_SUCCESS)
        self.assertEqual(len(commands), 3)
        # Lane 1: current defaults on the latest GraalVM (ambient environment).
        self.assertIsNone(commands[0][1])
        # Lane 2: future-defaults on the latest GraalVM.
        self.assertEqual(commands[1][1]["GRAALVM_HOME"], "/dev/graalvm")
        self.assertEqual(commands[1][1]["JAVA_HOME"], "/dev/graalvm")
        self.assertEqual(commands[1][1]["GVM_TCK_NATIVE_IMAGE_MODE"], "future-defaults-all")
        # Lane 3: current defaults on the GraalVM 25 toolchain.
        self.assertEqual(commands[2][1]["GRAALVM_HOME"], "/dev/graalvm-25")
        self.assertEqual(commands[2][1]["JAVA_HOME"], "/dev/graalvm-25")
        self.assertNotIn("GVM_TCK_NATIVE_IMAGE_MODE", commands[2][1])

    def test_commit_library_iteration_stages_resolved_test_version(self) -> None:
        with tempfile.TemporaryDirectory() as repo:
            metadata_root = os.path.join(repo, "metadata", "org.example", "demo")
            os.makedirs(metadata_root)
            with open(os.path.join(metadata_root, "index.json"), "w", encoding="utf-8") as index_file:
                json.dump([
                    {
                        "metadata-version": "1.0.0",
                        "tested-versions": ["1.0.0", "1.0.1"],
                    }
                ], index_file)
            os.makedirs(os.path.join(repo, "metadata", "org.example", "demo", "1.0.0"))
            os.makedirs(os.path.join(repo, "tests", "src", "org.example", "demo", "1.0.0"))
            strategy = _TestWorkflowStrategy(
                {"model": "test-model"},
                reachability_repo_path=repo,
                library="org.example:demo:1.0.1",
            )
            strategy.reachability_repo_path = repo
            strategy.group = "org.example"
            strategy.artifact = "demo"
            strategy.version = "1.0.1"
            calls: list[list[str]] = []

            def fake_run(command, **kwargs):  # type: ignore[no-untyped-def]
                calls.append(list(command))
                if command[:4] == ["git", "diff", "--cached", "--quiet"]:
                    return subprocess.CompletedProcess(command, 0)
                return subprocess.CompletedProcess(command, 0)

            with patch("ai_workflows.core.workflow_strategy.subprocess.run", side_effect=fake_run):
                self.assertTrue(strategy._commit_library_iteration())

        git_add = calls[0]
        self.assertEqual(git_add[:3], ["git", "add", "-A"])
        self.assertIn(os.path.join(repo, "tests", "src", "org.example", "demo", "1.0.0"), git_add)
        self.assertNotIn(os.path.join(repo, "tests", "src", "org.example", "demo", "1.0.1"), git_add)

    def test_finalization_libraries_include_resolved_metadata_coordinate(self) -> None:
        strategy = _TestWorkflowStrategy(
            {"model": "test-model"},
            reachability_repo_path="/tmp/reachability",
            library="org.example:demo:1.0.1",
            metadata_version="1.0.0",
        )
        strategy.group = "org.example"
        strategy.artifact = "demo"
        strategy.library = "org.example:demo:1.0.1"
        strategy.version = "1.0.1"
        strategy.reachability_repo_path = "/tmp/reachability"

        self.assertEqual(
            strategy._finalization_libraries(),
            ["org.example:demo:1.0.1", "org.example:demo:1.0.0"],
        )

    def test_finalize_successful_iteration_validates_requested_and_resolved_metadata_coordinate(self) -> None:
        strategy = _TestWorkflowStrategy(
            {"model": "test-model"},
            reachability_repo_path="/tmp/reachability",
            library="org.example:demo:1.0.1",
            metadata_version="1.0.0",
        )
        strategy.group = "org.example"
        strategy.artifact = "demo"
        strategy.library = "org.example:demo:1.0.1"
        strategy.version = "1.0.1"
        strategy.reachability_repo_path = "/tmp/reachability"
        tested_libraries: list[str] = []
        finalized_libraries: list[str] = []

        def fake_finalization(**kwargs):  # type: ignore[no-untyped-def]
            finalized_libraries.append(kwargs["library"])
            return True

        with patch.object(strategy, "_run_command"), \
                patch.object(
                    strategy,
                    "_run_test_with_retry",
                    side_effect=lambda library: tested_libraries.append(library) or RUN_STATUS_SUCCESS,
                ), \
                patch.object(strategy, "_commit_library_iteration", return_value=True), \
                patch("ai_workflows.core.workflow_strategy.run_library_finalization",
                      side_effect=fake_finalization), \
                patch(
                    "ai_workflows.core.workflow_strategy.subprocess.check_output",
                    return_value="checkpoint\n",
                ):
            status, checkpoint = strategy._finalize_successful_iteration()

        expected_libraries = ["org.example:demo:1.0.1", "org.example:demo:1.0.0"]
        self.assertEqual(status, RUN_STATUS_SUCCESS)
        self.assertEqual(checkpoint, "checkpoint")
        self.assertEqual(tested_libraries, expected_libraries)
        self.assertEqual(finalized_libraries, expected_libraries)

    def test_finalize_run_merges_finalization_status_for_all_driver_cases(self) -> None:
        strategy = _TestWorkflowStrategy(
            {"model": "test-model"},
            reachability_repo_path="/tmp/reachability",
            library="org.example:demo:1.0.0",
        )
        # (finalization status, incoming workflow status) -> merged run status.
        cases = [
            (RUN_STATUS_SUCCESS, RUN_STATUS_SUCCESS, RUN_STATUS_SUCCESS),
            (SUCCESS_WITH_INTERVENTION_STATUS, RUN_STATUS_SUCCESS, SUCCESS_WITH_INTERVENTION_STATUS),
            (RUN_STATUS_FAILURE, RUN_STATUS_SUCCESS, RUN_STATUS_FAILURE),
            (RUN_STATUS_SUCCESS, RUN_STATUS_CHUNK_READY, RUN_STATUS_CHUNK_READY),
            (SUCCESS_WITH_INTERVENTION_STATUS, RUN_STATUS_CHUNK_READY, RUN_STATUS_CHUNK_READY),
            (RUN_STATUS_FAILURE, RUN_STATUS_CHUNK_READY, RUN_STATUS_FAILURE),
        ]
        for finalize_status, workflow_status, expected_status in cases:
            with self.subTest(finalize_status=finalize_status, workflow_status=workflow_status), \
                    patch.object(
                        strategy,
                        "_finalize_successful_iteration",
                        return_value=(finalize_status, "checkpoint"),
                    ) as finalize:
                self.assertEqual(strategy.finalize_run("base", workflow_status), expected_status)
                finalize.assert_called_once_with(base_commit="base")


if __name__ == "__main__":
    unittest.main()
