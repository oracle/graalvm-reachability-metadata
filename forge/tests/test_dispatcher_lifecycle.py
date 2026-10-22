# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

from dispatcher import interrupts
from dispatcher_test_support import *  # noqa: F401,F403 - shared dispatcher test fixtures


class InterruptHandlingTests(unittest.TestCase):
    def setUp(self) -> None:
        interrupts.clear_user_interrupt_requested()
        self._original_cwd = os.getcwd()

    def tearDown(self) -> None:
        interrupts.clear_user_interrupt_requested()
        os.chdir(self._original_cwd)

    def test_interrupt_return_code_from_workflow_raises_keyboard_interrupt(self) -> None:
        claimed_issue = _claimed_issue()

        with patch.object(driver_invocation, "run_add_new_library_support_workflow", return_value=130), \
                patch.object(pipeline_execution, "require_claimed_issue_worktree"), \
                patch.object(pipeline_execution, "run_library_preparation_preflight", return_value=None), \
                patch.object(pipeline_execution, "prepare_dynamic_access_chunking", return_value=None), \
                patch.object(env_config, "require_issue_graalvm_homes") as require_graalvm_homes:
            with self.assertRaises(KeyboardInterrupt):
                pipeline_execution.invoke_pipeline(claimed_issue, None, False)

        self.assertTrue(interrupts.is_user_interrupt_requested())
        require_graalvm_homes.assert_not_called()

    def test_failed_run_analysis_uses_fallback_when_worktree_is_invalid(self) -> None:
        claimed_issue = _claimed_issue()
        candidate = human_intervention.HumanInterventionCandidate(
            strategy_name=None,
            workflow_status=RUN_STATUS_FAILURE,
            reason="job_failed",
        )

        with patch.object(human_intervention, "load_pending_run_metrics", return_value=None), \
                patch.object(human_intervention, "collect_issue_log_paths", return_value=[]), \
                patch.object(worktrees, "require_claimed_issue_worktree", side_effect=RuntimeError("invalid")), \
                patch.object(subprocess, "run") as run:
            comment = human_intervention.run_codex_failed_generation_analysis(
                claimed_issue,
                candidate,
                started_at=123.0,
                preservation_result=None,
            )

        self.assertIn("Human intervention needed", comment)
        run.assert_not_called()

    def test_human_intervention_analysis_uses_fallback_when_worktree_is_invalid(self) -> None:
        claimed_issue = _claimed_issue()
        candidate = human_intervention.HumanInterventionCandidate(
            strategy_name="strategy",
            workflow_status=RUN_STATUS_FAILURE,
            reason="low_dynamic_access_coverage",
        )
        strategy = {"model": "test-model"}

        with patch.object(human_intervention, "load_strategy_by_name", return_value=strategy), \
                patch.object(human_intervention, "_collect_human_intervention_read_only_files", return_value=[]), \
                patch.object(worktrees, "require_claimed_issue_worktree", side_effect=RuntimeError("invalid")), \
                patch.object(human_intervention, "init_workflow_agent") as init_agent:
            comment = human_intervention.run_human_intervention_analysis(
                claimed_issue,
                candidate,
                started_at=123.0,
                preservation_result=None,
            )

        self.assertIn("Human intervention needed", comment)
        init_agent.assert_not_called()

    def test_human_intervention_posting_noops_after_interrupt(self) -> None:
        interrupts.mark_user_interrupt_requested()

        with patch.object(failure_follow_up, "post_issue_comment") as post_issue_comment, \
                patch.object(failure_follow_up, "add_issue_label") as add_issue_label:
            failure_follow_up.post_human_intervention_comment_and_label(1412, "comment")

        post_issue_comment.assert_not_called()
        add_issue_label.assert_not_called()

    def test_gradle_bootstrap_failure_is_classified_as_external(self) -> None:
        failure = GradleBootstrapFailure("org.example:lib:1.0.0", "/tmp/discover.log")

        wrapped = RuntimeError("wrapped")
        wrapped.__cause__ = failure

        self.assertTrue(human_intervention.is_external_failure_exception(failure))
        self.assertTrue(human_intervention.is_external_failure_exception(wrapped))
