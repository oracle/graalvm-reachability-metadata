# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

from dispatcher_test_support import *  # noqa: F401,F403 - shared dispatcher test fixtures


class InterruptHandlingTests(unittest.TestCase):
    def setUp(self) -> None:
        forge_metadata.clear_user_interrupt_requested()
        self._original_cwd = os.getcwd()

    def tearDown(self) -> None:
        forge_metadata.clear_user_interrupt_requested()
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

    def test_interrupted_failed_workflow_skips_human_intervention_handling(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path:
            claimed_issue = _claimed_issue_in(repo_path)

            def interrupted_run(*_args):
                interrupts.mark_user_interrupt_requested()
                return records.WorkflowRunResult(
                    claimed_issue=claimed_issue,
                    success=False,
                    started_at=123.0,
                )

            with patch.object(lifecycle, "run_claimed_issue", side_effect=interrupted_run), \
                    patch.object(lifecycle, "handle_completed_run") as handle_completed_run, \
                    patch.object(lifecycle, "handle_failed_claimed_issue") as handle_failed_claimed_issue, \
                    patch.object(lifecycle, "revert_claimed_issue") as revert_claimed_issue, \
                    patch.object(lifecycle, "cleanup_issue_workspace") as cleanup_issue_workspace:
                with self.assertRaises(KeyboardInterrupt):
                    forge_metadata.process_claimed_issue_lifecycle(
                        claimed_issue,
                        strategy_name=None,
                        keep_tests_without_dynamic_access=False,
                        canonical_metrics_repo_path="/tmp/metrics",
                    )

        handle_completed_run.assert_not_called()
        handle_failed_claimed_issue.assert_not_called()
        revert_claimed_issue.assert_called_once_with(claimed_issue, "Ctrl+C interrupt")
        cleanup_issue_workspace.assert_called_once_with(claimed_issue, "/tmp/metrics")

    def test_unhandled_system_exit_is_failed_issue_not_queue_stopper(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path:
            claimed_issue = _claimed_issue_in(repo_path)

            with patch.object(lifecycle, "run_claimed_issue", side_effect=SystemExit(1)), \
                    patch.object(lifecycle, "handle_completed_run") as handle_completed_run, \
                    patch.object(lifecycle, "handle_failed_claimed_issue") as handle_failed_claimed_issue, \
                    patch.object(lifecycle, "cleanup_issue_workspace") as cleanup_issue_workspace:
                handled = forge_metadata.process_claimed_issue_lifecycle(
                    claimed_issue,
                    strategy_name=None,
                    keep_tests_without_dynamic_access=False,
                    canonical_metrics_repo_path="/tmp/metrics",
                )

        self.assertFalse(handled)
        handle_completed_run.assert_not_called()
        handle_failed_claimed_issue.assert_called_once()
        cleanup_issue_workspace.assert_called_once_with(claimed_issue, "/tmp/metrics")

    def test_failed_run_analysis_uses_fallback_when_worktree_is_invalid(self) -> None:
        claimed_issue = _claimed_issue()
        candidate = human_intervention.HumanInterventionCandidate(
            strategy_name=None,
            workflow_status=RUN_STATUS_FAILURE,
            reason="job_failed",
        )

        with patch.object(human_intervention, "_load_pending_run_metrics", return_value=None), \
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

    def test_no_unwind_path_relabels_a_recorded_bootstrap_stop_as_ctrl_c(self) -> None:
        """A concurrent worker observing the interrupt must not reset the reason.

        With parallelism > 1 one issue can record the bootstrap stop while another
        worker unwinds through a generic interrupt handler; the main loop would then
        revert the remaining claims, and exit, under the wrong reason.
        """
        claimed_issue = _claimed_issue()
        interrupts.mark_user_interrupt_requested(config.INTERRUPT_REASON_GRADLE_BOOTSTRAP)

        with patch.object(driver_invocation, "run_add_new_library_support_workflow", return_value=130), \
                patch.object(pipeline_execution, "require_claimed_issue_worktree"), \
                patch.object(pipeline_execution, "run_library_preparation_preflight", return_value=None), \
                patch.object(pipeline_execution, "prepare_dynamic_access_chunking", return_value=None), \
                patch.object(pipeline_execution, "create_or_load_run_continuation_marker", return_value=None), \
                patch.object(pipeline_execution, "load_continuation_marker", return_value=None), \
                patch.object(pipeline_execution, "record_library_update_route_in_marker"):
            with self.assertRaises(KeyboardInterrupt):
                pipeline_execution.invoke_pipeline(claimed_issue, None, False)

        self.assertTrue(forge_metadata.is_gradle_bootstrap_interrupt())

    def test_ctrl_c_is_still_recorded_when_no_reason_was_set(self) -> None:
        forge_metadata.preserve_user_interrupt_reason()

        self.assertTrue(interrupts.is_user_interrupt_requested())
        self.assertEqual(
            interrupts.get_user_interrupt_reason(),
            config.INTERRUPT_REASON_CTRL_C,
        )

    def test_gradle_bootstrap_failure_is_classified_as_external(self) -> None:
        failure = GradleBootstrapFailure("org.example:lib:1.0.0", "/tmp/discover.log")

        wrapped = RuntimeError("wrapped")
        wrapped.__cause__ = failure

        self.assertTrue(human_intervention.is_external_failure_exception(failure))
        self.assertTrue(human_intervention.is_external_failure_exception(wrapped))

    def test_preserved_interrupt_reason_survives_later_generic_handlers(self) -> None:
        interrupts.mark_user_interrupt_requested(config.INTERRUPT_REASON_GRADLE_BOOTSTRAP)
        forge_metadata.preserve_user_interrupt_reason()

        self.assertTrue(forge_metadata.is_gradle_bootstrap_interrupt())
        self.assertEqual(
            interrupts.get_user_interrupt_reason(),
            config.INTERRUPT_REASON_GRADLE_BOOTSTRAP,
        )

    def test_gradle_bootstrap_failure_reverts_claim_without_human_intervention_follow_up(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path:
            claimed_issue = _claimed_issue_in(repo_path)
            failure = GradleBootstrapFailure(claimed_issue.issue_coordinates, "/tmp/discover.log")

            with patch.object(lifecycle, "run_claimed_issue", side_effect=failure), \
                    patch.object(lifecycle, "handle_completed_run") as handle_completed_run, \
                    patch.object(lifecycle, "handle_failed_claimed_issue") as handle_failed_claimed_issue, \
                    patch.object(lifecycle, "revert_claimed_issue") as revert_claimed_issue, \
                    patch.object(lifecycle, "cleanup_issue_workspace") as cleanup_issue_workspace:
                with self.assertRaises(KeyboardInterrupt):
                    forge_metadata.process_claimed_issue_lifecycle(
                        claimed_issue,
                        strategy_name=None,
                        keep_tests_without_dynamic_access=False,
                        canonical_metrics_repo_path="/tmp/metrics",
                    )

        self.assertEqual(
            interrupts.get_user_interrupt_reason(),
            config.INTERRUPT_REASON_GRADLE_BOOTSTRAP,
        )
        handle_completed_run.assert_not_called()
        handle_failed_claimed_issue.assert_not_called()
        revert_claimed_issue.assert_called_once_with(
            claimed_issue,
            config.INTERRUPT_REASON_GRADLE_BOOTSTRAP,
        )
        cleanup_issue_workspace.assert_called_once_with(claimed_issue, "/tmp/metrics")

    def test_gradle_bootstrap_failure_stops_the_issue_queue(self) -> None:
        issues = [
            {
                "number": issue_number,
                "title": f"Add support for org.example:lib{issue_number}:1.0.0",
                "labels": [],
                "assignees": [],
            }
            for issue_number in range(1, 4)
        ]

        with tempfile.TemporaryDirectory() as repo_path, tempfile.TemporaryDirectory() as lock_root:
            claimed_issue = _claimed_issue_in(repo_path)
            failure = GradleBootstrapFailure(claimed_issue.issue_coordinates, "/tmp/discover.log")

            with patch.object(issue_cache, "get_issue_claim_locks_root", return_value=lock_root), \
                    patch.object(issue_processing, "validate_issue_processing_environment"), \
                    patch.object(
                        issue_processing,
                        "get_prioritized_issues_with_label",
                        return_value=(issues, _scan_state(len(issues), exhausted=True)),
                    ), \
                    patch.object(claim_preflight, "get_issue_claim_preflights_or_empty"), \
                    patch.object(
                        issue_processing,
                        "claim_issue_for_processing",
                        return_value=claimed_issue,
                    ) as claim_issue_for_processing, \
                    patch.object(lifecycle, "run_claimed_issue", side_effect=failure), \
                    patch.object(lifecycle, "handle_completed_run") as handle_completed_run, \
                    patch.object(lifecycle, "handle_failed_claimed_issue") as handle_failed_claimed_issue, \
                    patch.object(lifecycle, "revert_claimed_issue"), \
                    patch.object(lifecycle, "cleanup_issue_workspace"):
                with self.assertRaises(KeyboardInterrupt):
                    forge_metadata.process_issues_with_label(
                        forge_metadata.LABEL_LIBRARY_NEW,
                        len(issues),
                        0,
                        "/tmp/reachability",
                        "/tmp/metrics",
                        None,
                        False,
                        "automation-user",
                        1,
                    )

        claim_issue_for_processing.assert_called_once()
        handle_completed_run.assert_not_called()
        handle_failed_claimed_issue.assert_not_called()
        self.assertEqual(
            interrupts.get_user_interrupt_reason(),
            config.INTERRUPT_REASON_GRADLE_BOOTSTRAP,
        )

    def test_process_issues_with_label_skips_queue_when_shutdown_requested(self) -> None:
        with patch.object(issue_processing, "is_shutdown_requested", return_value=True), \
                patch.object(issue_processing, "validate_issue_processing_environment") as validate_environment, \
                patch.object(issue_processing, "resolve_authenticated_user") as resolve_authenticated_user:
            processed = forge_metadata.process_issues_with_label(
                forge_metadata.LABEL_LIBRARY_NEW,
                1,
                0,
                "/tmp/reachability",
                "/tmp/metrics",
                None,
                False,
                "automation-user",
                1,
            )

        self.assertEqual(processed, 0)
        validate_environment.assert_not_called()
        resolve_authenticated_user.assert_not_called()
