# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

from dispatcher_test_support import *  # noqa: F401,F403 - shared dispatcher test fixtures


class RunFailureLocationTests(unittest.TestCase):
    """The failure location a step recorded reaches every reporting surface.

    §FS-forge-run-location-reporting.3
    """

    def setUp(self) -> None:
        run_location.reset_run_location()

    def tearDown(self) -> None:
        run_location.reset_run_location()

    def test_terminal_failure_prints_and_forwards_the_recorded_phase_and_step(self) -> None:
        claimed_issue = _claimed_issue()
        expected_line = "run failed in explore/native_trace_gate()[com.acme.Thing]"
        stderr = io.StringIO()

        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(stderr), \
                patch.object(lifecycle, "preserve_failed_work_for_follow_up", return_value=None), \
                patch.object(lifecycle, "refresh_preserved_branch_logs"), \
                patch.object(lifecycle, "revert_claimed_issue"), \
                patch.object(lifecycle, "apply_failed_run_follow_up") as follow_up:
            with run_location.run_step(
                run_location.PHASE_EXPLORE,
                run_location.STEP_NATIVE_TRACE_GATE,
                operand="com.acme.Thing",
            ):
                run_location.record_step_failure()
            lifecycle.handle_failed_claimed_issue(claimed_issue, "workflow failure")

        self.assertIn(expected_line, stderr.getvalue())
        forwarded = follow_up.call_args.kwargs["failure_location"]
        self.assertEqual(format_run_failure_line(forwarded), expected_line)

    def test_human_intervention_comment_leads_with_the_same_pair(self) -> None:
        claimed_issue = _claimed_issue(forge_metadata.LABEL_JAVAC_FAIL)
        failure_location = run_location.RunLocation(
            run_location.PHASE_EXPLORE,
            run_location.STEP_NATIVE_TRACE_GATE,
            "com.acme.Thing",
        )

        with patch.object(
                failure_follow_up,
                "resolve_human_intervention_candidate",
                return_value="candidate",
        ), \
                patch.object(
                    failure_follow_up,
                    "run_codex_failed_generation_analysis",
                    return_value="Analysis body.",
                ), \
                patch.object(
                    failure_follow_up,
                    "post_human_intervention_comment_and_label",
                ) as post_follow_up:
            failure_follow_up.apply_failed_run_follow_up(
                claimed_issue,
                failure_location=failure_location,
            )

        comment_body = post_follow_up.call_args.args[1]
        self.assertTrue(
            comment_body.startswith("`run failed in explore/native_trace_gate()[com.acme.Thing]`"),
            comment_body,
        )
        self.assertIn("Analysis body.", comment_body)


class FailedRunFollowUpTests(unittest.TestCase):
    def test_publication_marker_applies_publication_failure_follow_up(self) -> None:
        claimed_issue = _claimed_issue(forge_metadata.LABEL_JAVAC_FAIL)

        with tempfile.TemporaryDirectory() as repo_path:
            marker = ContinuationMarker.create(
                strategy_name="strategy",
                issue_number=1412,
                label=claimed_issue.label,
                coordinate=claimed_issue.issue_coordinates,
                new_version=None,
            )
            marker.mark_setup_done(skip_fix_phase=True)
            marker.mark_phase_skipped(PHASE_EXPLORE)
            marker.mark_phase_completed(PHASE_FINALIZATION)
            self.assertEqual(marker.continue_from, PHASE_PUBLICATION)
            marker.save(continuation_marker_path(repo_path))
            preservation_result = failure_preservation.FailurePreservationResult(
                branch_name="ai/test/preserved",
                branch_url="https://github.com/oracle/graalvm-reachability-metadata/tree/ai/test/preserved",
                committed_changes=True,
                reviewable_worktree_path=repo_path,
            )

            with patch.object(failure_follow_up, "resolve_human_intervention_candidate", return_value=None), \
                    patch.object(
                        failure_follow_up,
                        "post_human_intervention_comment_and_label",
                    ) as post_follow_up:
                failure_follow_up.apply_failed_run_follow_up(
                    claimed_issue,
                    preservation_result=preservation_result,
                )

        post_follow_up.assert_called_once()
        self.assertEqual(post_follow_up.call_args.args[0], 1412)
        self.assertIn("publishing the pull request did not finish", post_follow_up.call_args.args[1])
        self.assertEqual(post_follow_up.call_args.kwargs, {"resumable": True})

    def test_setup_marker_does_not_apply_publication_failure_follow_up(self) -> None:
        claimed_issue = _claimed_issue(forge_metadata.LABEL_JAVAC_FAIL)

        with tempfile.TemporaryDirectory() as repo_path:
            marker = ContinuationMarker.create(
                strategy_name="strategy",
                issue_number=1412,
                label=claimed_issue.label,
                coordinate=claimed_issue.issue_coordinates,
                new_version=None,
            )
            self.assertEqual(marker.continue_from, PHASE_SETUP)
            marker.save(continuation_marker_path(repo_path))
            preservation_result = failure_preservation.FailurePreservationResult(
                branch_name="ai/test/preserved",
                branch_url="https://github.com/oracle/graalvm-reachability-metadata/tree/ai/test/preserved",
                committed_changes=True,
                reviewable_worktree_path=repo_path,
            )

            with patch.object(failure_follow_up, "resolve_human_intervention_candidate", return_value=None), \
                    patch.object(
                        failure_follow_up,
                        "post_human_intervention_comment_and_label",
                    ) as post_follow_up:
                failure_follow_up.apply_failed_run_follow_up(
                    claimed_issue,
                    preservation_result=preservation_result,
                )

        post_follow_up.assert_not_called()
