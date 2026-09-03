# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import io
import os
import json
import subprocess
import tempfile
import unittest
from contextlib import redirect_stdout
from unittest.mock import patch

from ai_workflows.agents.agent import AgentFailureError
from ai_workflows.core.workflow_strategy import (
    RUN_STATUS_CHUNK_READY,
    RUN_STATUS_FAILURE,
    RUN_STATUS_SUCCESS,
    SUCCESS_WITH_INTERVENTION_STATUS,
    WorkflowStrategy,
)
from utility_scripts.native_test_verification import NativeTestVerificationResult
from utility_scripts.run_location import (
    PHASE_FINALIZATION,
    STEP_FINALIZE_RUN,
    enter_phase,
    reset_run_location,
    run_step,
)


class _TestWorkflowStrategy(WorkflowStrategy):
    def run(self) -> dict:
        return {}


class WorkflowStrategyTests(unittest.TestCase):
    def setUp(self) -> None:
        reset_run_location()

    def tearDown(self) -> None:
        reset_run_location()

    def test_native_gate_agent_failure_reaches_terminal_reporter(self) -> None:
        strategy = _TestWorkflowStrategy(
            {"model": "test-model"},
            reachability_repo_path="/tmp/reachability",
            library="org.example:demo:1.0.0",
        )
        strategy.library = "org.example:demo:1.0.0"
        failed_result = NativeTestVerificationResult(
            status="FAILED",
            output_dir="/tmp/output",
            iterations_used=1,
            failure_detail="Agent native_test_verify() timed out after 30:00",
            failure_log_path="/tmp/native-agent.log",
        )

        with patch(
                "ai_workflows.core.workflow_strategy.verify_native_test_passes",
                return_value=failed_result,
        ), self.assertRaises(AgentFailureError) as raised:
            strategy.verify_native_test_gate("/tmp/output")

        self.assertEqual(
            str(raised.exception),
            "native-trace-gate agent failed with: Agent native_test_verify() timed out after 30:00",
        )
        self.assertEqual(raised.exception.log_path, "/tmp/native-agent.log")

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

    def test_concise_finalization_output_names_the_three_native_test_lanes(self) -> None:
        strategy = _TestWorkflowStrategy(
            {"model": "test-model"},
            reachability_repo_path="/tmp/reachability",
            library="org.example:demo:1.0.0",
        )
        strategy._run_command_with_env = lambda *_args, **_kwargs: "BUILD SUCCESSFUL"
        output = io.StringIO()

        with patch.dict(
                os.environ,
                {
                    "FORGE_VERBOSE": "0",
                    "FORGE_DEBUG_LOGGING": "0",
                    "GRAALVM_HOME": "/dev/graalvm",
                    "JAVA_HOME": "/dev/graalvm",
                    "GRAALVM_HOME_25_0": "/dev/graalvm-25",
                },
                clear=True,
        ), redirect_stdout(output):
            enter_phase(PHASE_FINALIZATION)
            with run_step(
                    PHASE_FINALIZATION,
                    STEP_FINALIZE_RUN,
                    operand="org.example:demo:1.0.0",
            ):
                status = strategy._run_test_with_retry("org.example:demo:1.0.0")

        self.assertEqual(status, RUN_STATUS_SUCCESS)
        printed = output.getvalue()
        self.assertIn(
            "[finalization]   Running native-test lane 1/3 for "
            "org.example:demo:1.0.0: latest GraalVM, current defaults (1/2)",
            printed,
        )
        self.assertIn(
            "[finalization]   Running native-test lane 2/3 for "
            "org.example:demo:1.0.0: latest GraalVM, future defaults (1/2)",
            printed,
        )
        self.assertIn(
            "[finalization]   Running native-test lane 3/3 for "
            "org.example:demo:1.0.0: GraalVM 25, current defaults (1/2)",
            printed,
        )
        self.assertIn(
            "[finalization]   Passed native-test lane 3/3 for "
            "org.example:demo:1.0.0: GraalVM 25, current defaults (1/2)",
            printed,
        )
        self.assertNotIn("[post-generation-test]", printed)
        self.assertNotIn("./gradlew", printed)

    def test_verbose_finalization_restores_native_test_lane_narration(self) -> None:
        strategy = _TestWorkflowStrategy(
            {"model": "test-model"},
            reachability_repo_path="/tmp/reachability",
            library="org.example:demo:1.0.0",
        )
        strategy._run_command_with_env = lambda *_args, **_kwargs: "BUILD SUCCESSFUL"
        output = io.StringIO()

        with patch.dict(
                os.environ,
                {
                    "FORGE_VERBOSE": "1",
                    "GRAALVM_HOME": "/dev/graalvm",
                    "JAVA_HOME": "/dev/graalvm",
                    "GRAALVM_HOME_25_0": "/dev/graalvm-25",
                },
                clear=True,
        ), redirect_stdout(output):
            enter_phase(PHASE_FINALIZATION)
            with run_step(
                    PHASE_FINALIZATION,
                    STEP_FINALIZE_RUN,
                    operand="org.example:demo:1.0.0",
            ):
                status = strategy._run_test_with_retry("org.example:demo:1.0.0")

        self.assertEqual(status, RUN_STATUS_SUCCESS)
        self.assertIn(
            "[post-generation-test] Running current-defaults latest GRAALVM test "
            "for org.example:demo:1.0.0",
            output.getvalue(),
        )

    def test_concise_finalization_groups_repository_checks(self) -> None:
        strategy = _TestWorkflowStrategy(
            {"model": "test-model"},
            reachability_repo_path="/tmp/reachability",
            library="org.example:demo:1.0.0",
            metadata_version="1.0.0",
            test_version="1.0.0",
        )
        strategy.group = "org.example"
        strategy.artifact = "demo"
        strategy.library = "org.example:demo:1.0.0"
        strategy.version = "1.0.0"
        strategy.reachability_repo_path = "/tmp/reachability"
        output = io.StringIO()

        with patch.dict(
                os.environ,
                {"FORGE_VERBOSE": "0", "FORGE_DEBUG_LOGGING": "0"},
        ), patch.object(
                strategy,
                "verify_native_test_gate",
                return_value=True,
        ), patch.object(
                strategy,
                "_run_test_with_retry",
                return_value=RUN_STATUS_SUCCESS,
        ), patch.object(
                strategy,
                "_commit_library_iteration",
                return_value=True,
        ), patch(
                "ai_workflows.core.workflow_strategy.run_library_finalization",
                return_value=True,
        ), patch(
                "ai_workflows.core.workflow_strategy.subprocess.check_output",
                return_value="checkpoint\n",
        ), redirect_stdout(output):
            status = strategy.finalize_run("base")

        self.assertEqual(status, RUN_STATUS_SUCCESS)
        printed = output.getvalue()
        self.assertIn(
            "[finalization]   Running final repository checks for "
            "org.example:demo:1.0.0 (1/2)",
            printed,
        )
        self.assertIn(
            "[finalization]   Final repository checks passed for "
            "org.example:demo:1.0.0 (1/2)",
            printed,
        )
        self.assertIn(
            "[finalization] Finalization completed for org.example:demo:1.0.0 (1/2)",
            printed,
        )
        self.assertNotIn("splitTestOnlyMetadata", printed)
        self.assertNotIn("checkMetadataFiles", printed)
        self.assertNotIn("generateLibraryStats", printed)

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
            os.makedirs(os.path.join(repo, "stats", "org.example", "demo", "1.0.0"))
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
        self.assertIn(os.path.join(repo, "stats"), git_add)

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
        finalization_steps: list[str] = []

        def fake_finalization(**kwargs: object) -> bool:
            library = str(kwargs["library"])
            finalized_libraries.append(library)
            finalization_steps.append(f"finalize:{library}")
            return True

        with patch.object(
                    strategy,
                    "verify_native_test_gate",
                    side_effect=lambda _output_dir: finalization_steps.append("native-gate") or True,
                ) as native_gate, \
                patch.object(strategy, "_run_command") as run_command, \
                patch.object(
                    strategy,
                    "_run_test_with_retry",
                    side_effect=lambda library: (
                        tested_libraries.append(library)
                        or finalization_steps.append(f"test:{library}")
                        or RUN_STATUS_SUCCESS
                    ),
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
        native_gate.assert_called_once()
        run_command.assert_not_called()
        self.assertTrue(
            native_gate.call_args.args[0].endswith(
                "1.0.1/build/natively-collected/_global_",
            )
        )
        self.assertEqual(
            finalization_steps,
            [
                "native-gate",
                "test:org.example:demo:1.0.1",
                "test:org.example:demo:1.0.0",
                "finalize:org.example:demo:1.0.1",
                "finalize:org.example:demo:1.0.0",
            ],
        )

    def test_finalize_successful_iteration_stops_when_terminal_native_gate_fails(self) -> None:
        strategy = _TestWorkflowStrategy(
            {"model": "test-model"},
            reachability_repo_path="/tmp/reachability",
            library="org.example:demo:1.0.0",
            test_version="1.0.0",
        )
        strategy.group = "org.example"
        strategy.artifact = "demo"
        strategy.library = "org.example:demo:1.0.0"
        strategy.version = "1.0.0"
        strategy.reachability_repo_path = "/tmp/reachability"

        with patch.object(strategy, "verify_native_test_gate", return_value=False), \
                patch.object(strategy, "_run_test_with_retry") as run_test, \
                patch("ai_workflows.core.workflow_strategy.run_library_finalization") as finalize, \
                patch.object(strategy, "_commit_library_iteration") as commit:
            status, checkpoint = strategy._finalize_successful_iteration()

        self.assertEqual(status, RUN_STATUS_FAILURE)
        self.assertIsNone(checkpoint)
        run_test.assert_not_called()
        finalize.assert_not_called()
        commit.assert_not_called()

    def test_finalize_run_merges_finalization_status_for_all_driver_cases(self) -> None:
        strategy = _TestWorkflowStrategy(
            {"model": "test-model"},
            reachability_repo_path="/tmp/reachability",
            library="org.example:demo:1.0.0",
        )
        strategy.library = "org.example:demo:1.0.0"
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
