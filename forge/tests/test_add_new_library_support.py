# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import unittest
from contextlib import ExitStack
from unittest.mock import Mock, patch

from ai_workflows.core.workflow_strategy import (
    RUN_STATUS_CHUNK_READY,
    RUN_STATUS_FAILURE,
    RUN_STATUS_SUCCESS,
    SUCCESS_WITH_INTERVENTION_STATUS,
)
from ai_workflows.drivers import add_new_library_support
from ai_workflows.drivers.add_new_library_support import _should_create_failure_run_metrics, init_agent
from utility_scripts.continuation_marker import PHASE_SETUP
from utility_scripts.run_location import RunLocation, STEP_NORMAL_SETUP, reset_run_location


class AddNewLibrarySupportTests(unittest.TestCase):
    def tearDown(self) -> None:
        reset_run_location()

    def test_init_agent_forwards_strategy_thinking_level(self) -> None:
        agent_class = Mock(return_value=object())
        strategy = {
            "agent": "pi",
            "model": "gpt-5.6-sol",
            "thinking-level": "medium",
        }

        with patch("ai_workflows.drivers.add_new_library_support.Agent.get_class", return_value=agent_class):
            init_agent(strategy, "/tmp/worktree", [], [], model_name="gpt-5.6-sol")

        self.assertEqual(agent_class.call_args.kwargs["thinking_level"], "medium")

    def test_failure_after_generated_tests_uses_failure_metrics(self) -> None:
        self.assertTrue(_should_create_failure_run_metrics(RUN_STATUS_FAILURE, 1, False))

    def test_successful_statuses_with_generated_tests_use_normal_metrics(self) -> None:
        for status in (RUN_STATUS_SUCCESS, RUN_STATUS_CHUNK_READY, SUCCESS_WITH_INTERVENTION_STATUS):
            with self.subTest(status=status):
                self.assertFalse(_should_create_failure_run_metrics(status, 1, False))

    def test_no_generated_tests_uses_failure_metrics_except_intervention_status(self) -> None:
        self.assertTrue(_should_create_failure_run_metrics(RUN_STATUS_SUCCESS, 0, False))
        self.assertFalse(_should_create_failure_run_metrics(SUCCESS_WITH_INTERVENTION_STATUS, 0, False))

    def test_scaffold_placeholder_gate_uses_failure_metrics(self) -> None:
        self.assertTrue(_should_create_failure_run_metrics(RUN_STATUS_SUCCESS, 1, True))

    def test_scaffold_error_records_the_active_setup_step(self) -> None:
        reset_run_location()
        reported_locations: list[RunLocation] = []
        parsed_flags = (
            "g:a:1.0",
            "g",
            "a",
            "1.0",
            None,
            "strategy",
            False,
            "/repo",
            "/metrics",
            False,
            False,
            0,
            None,
            None,
            None,
        )

        with ExitStack() as stack:
            stack.enter_context(patch.object(
                add_new_library_support,
                "parse_flags",
                return_value=parsed_flags,
            ))
            stack.enter_context(patch.object(
                add_new_library_support,
                "require_strategy_by_name",
                return_value={"model": "model", "workflow": "workflow"},
            ))
            stack.enter_context(patch.object(
                add_new_library_support,
                "load_continuation_marker",
                return_value=None,
            ))
            stack.enter_context(patch.object(
                add_new_library_support,
                "resolve_workflow_repo_paths",
                return_value=("/repo", "/metrics", "/metrics-root"),
            ))
            stack.enter_context(patch.object(
                add_new_library_support,
                "prepare_library_preparation_preflight",
                return_value=(None, ""),
            ))
            stack.enter_context(patch.object(add_new_library_support, "save_phase_update"))
            stack.enter_context(patch.object(add_new_library_support, "resolve_graalvm_java_home"))
            stack.enter_context(patch.object(
                add_new_library_support.WorkflowStrategy,
                "get_class",
                return_value=object,
            ))
            stack.enter_context(patch.object(add_new_library_support, "validate_repo_paths"))
            stack.enter_context(patch.object(add_new_library_support.os, "chdir"))
            stack.enter_context(patch.object(add_new_library_support, "create_feature_branch_for_library"))
            stack.enter_context(patch.object(
                add_new_library_support,
                "prepare_native_image_eligible_artifact",
                return_value=True,
            ))
            stack.enter_context(patch.object(
                add_new_library_support,
                "run_scaffold",
                side_effect=add_new_library_support.ScaffoldError("boom"),
            ))
            stack.enter_context(patch.object(
                add_new_library_support,
                "report_run_failure",
                side_effect=lambda location, _detail: reported_locations.append(location),
            ))

            returncode = add_new_library_support.main([])

        self.assertEqual(returncode, 1)
        self.assertEqual(len(reported_locations), 1)
        self.assertEqual(reported_locations[0].phase, PHASE_SETUP)
        self.assertEqual(reported_locations[0].step, STEP_NORMAL_SETUP)
        self.assertEqual(reported_locations[0].operand, "g:a:1.0")


if __name__ == "__main__":
    unittest.main()
