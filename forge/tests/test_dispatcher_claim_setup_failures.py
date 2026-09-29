# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

from dispatcher_test_support import *  # noqa: F401,F403 - shared dispatcher test fixtures


def _issue_label_name(issue: dict) -> str:
    """Return the queue label of a test issue payload."""
    return issue["labels"][0]["name"]


def _claim_with_patched_preparation(
        issue: dict,
        overrides: dict[str, object],
        captured: dict[str, unittest.mock.MagicMock] | None = None,
) -> tuple[records.ClaimedIssue | None, unittest.mock.MagicMock, unittest.mock.MagicMock]:
    """Run claim preparation with every step stubbed except the given overrides.

    `captured` receives the revert and release mocks before the claim runs, so a
    test whose claim raises can still inspect them.
    """
    steps: dict[str, object] = {
        "refresh_issue_payload_for_claim": True,
        "fetch_issue_base_commit": "base-sha",
        "try_claim_issue": "item-4242",
        "create_issue_workspace": ("/worktree", "/metrics"),
        "create_preflight_info_dir": "/preflight",
        "check_issue_form": issue_form.ISSUE_FORM_ACCEPTED,
        "maybe_handle_not_for_native_image_issue": False,
        "build_claim_metadata": ("org.example:widget:1.2.3", None, None),
        "resolve_issue_continuation_marker": None,
        "resolve_chunked_dynamic_access_exhaust_report": None,
    }
    with contextlib.ExitStack() as stack:
        for name, value in steps.items():
            override = overrides.get(name, value)
            if isinstance(override, BaseException):
                stack.enter_context(patch.object(claim_setup, name, side_effect=override))
            else:
                stack.enter_context(patch.object(claim_setup, name, return_value=override))
        revert = stack.enter_context(patch.object(claim_setup, "revert_issue_claim"))
        release = stack.enter_context(
            patch.object(claim_setup, "release_claim_after_logical_setup_failure")
        )
        stack.enter_context(patch.object(claim_setup, "cleanup_claim_preparation_workspace"))
        if captured is not None:
            captured.update(revert=revert, release=release)
        claimed_issue = forge_metadata.claim_issue_for_processing(
            issue,
            _issue_label_name(issue),
            "/repo",
            "/metrics",
            "runner",
        )
    return claimed_issue, revert, release


class ClaimSetupFailureClassificationTests(unittest.TestCase):
    """Only a logical precondition failure takes the issue out of rotation.

    §FS-forge-run-requirements.2
    """

    def test_external_precondition_failure_releases_claim_silently(self) -> None:
        with contextlib.redirect_stderr(io.StringIO()):
            claimed_issue, revert, release = _claim_with_patched_preparation(
                _form_issue(),
                {"resolve_chunked_dynamic_access_exhaust_report": github_cli.GitHubError("gh 502")},
            )

        self.assertIsNone(claimed_issue)
        release.assert_not_called()
        revert.assert_called_once_with(
            "item-4242",
            4242,
            "chunked-dynamic-access setup failure (GitHubError)",
        )

    def test_workspace_creation_failure_releases_claim_silently(self) -> None:
        with contextlib.redirect_stderr(io.StringIO()):
            claimed_issue, revert, release = _claim_with_patched_preparation(
                _form_issue(),
                {"create_issue_workspace": RuntimeError("no space left on device")},
            )

        self.assertIsNone(claimed_issue)
        release.assert_not_called()
        revert.assert_called_once_with("item-4242", 4242, "claim setup failure (RuntimeError)")

    def test_resumable_issue_without_marker_is_labeled(self) -> None:
        issue = _form_issue(label_names=[forge_metadata.LABEL_LIBRARY_NEW, config.LABEL_RESUMABLE])

        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            claimed_issue, revert, release = _claim_with_patched_preparation(issue, {})

        self.assertIsNone(claimed_issue)
        revert.assert_not_called()
        release.assert_called_once()
        self.assertEqual(release.call_args.args[3], "continuation check")
        self.assertEqual(
            release.call_args.args[5],
            "resumable issue has no valid continuation marker",
        )

    def test_interrupt_releases_claim_silently_and_propagates(self) -> None:
        captured: dict[str, unittest.mock.MagicMock] = {}
        with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(KeyboardInterrupt):
            _claim_with_patched_preparation(
                _form_issue(),
                {"build_claim_metadata": KeyboardInterrupt()},
                captured,
            )

        captured["release"].assert_not_called()
        captured["revert"].assert_called_once_with(
            "item-4242",
            4242,
            "post-claim preparation interrupted by Ctrl+C",
        )


class ClaimSetupFailureFollowUpTests(unittest.TestCase):
    """The follow-up labels the issue, then releases and caches the claim."""

    def _release(self, issue: dict) -> unittest.mock.MagicMock:
        calls = unittest.mock.MagicMock()
        with patch.object(
                failure_follow_up,
                "post_human_intervention_comment_and_label",
                calls.post,
        ), patch.object(failure_follow_up, "remove_issue_label", calls.remove_label), \
                patch.object(failure_follow_up, "revert_issue_claim", calls.revert), \
                patch.object(failure_follow_up, "record_issue_claim_cache_observations", calls.cache):
            failure_follow_up.release_claim_after_logical_setup_failure(
                issue,
                forge_metadata.LABEL_LIBRARY_UPDATE,
                "item-4242",
                "chunked-dynamic-access setup",
                "RuntimeError: missing report",
                "chunked-dynamic-access setup failure (RuntimeError)",
            )
        return calls

    def test_labels_then_releases_then_caches(self) -> None:
        calls = self._release(_form_issue(label_names=[forge_metadata.LABEL_LIBRARY_UPDATE]))

        self.assertEqual(
            [recorded[0] for recorded in calls.mock_calls],
            ["post", "revert", "cache"],
        )
        issue_number, comment = calls.post.call_args.args
        self.assertEqual(issue_number, 4242)
        self.assertIn("chunked-dynamic-access setup failed before any workflow phase ran", comment)
        self.assertIn("RuntimeError: missing report", comment)
        calls.revert.assert_called_once_with(
            "item-4242",
            4242,
            "chunked-dynamic-access setup failure (RuntimeError)",
        )
        observation = calls.cache.call_args.args[0][0]
        self.assertEqual(observation.reason, config.ISSUE_CLAIM_CACHE_REASON_HUMAN_INTERVENTION)

    def test_removes_resumable_so_the_label_takes_effect(self) -> None:
        calls = self._release(_form_issue(label_names=[
            forge_metadata.LABEL_LIBRARY_UPDATE,
            config.LABEL_RESUMABLE,
        ]))

        calls.remove_label.assert_called_once_with(4242, config.LABEL_RESUMABLE)
        self.assertEqual(calls.post.call_args.kwargs, {})


class FixQueueHumanInterventionCandidateTests(unittest.TestCase):
    """A logical workflow failure is labeled on every queue. §FS-human-intervention-policy"""

    def test_fix_queue_failure_is_a_candidate(self) -> None:
        claimed_issue = _claimed_issue(forge_metadata.LABEL_JAVAC_FAIL)

        with patch.object(human_intervention, "load_pending_run_metrics", return_value=None), \
                patch.object(
                    human_intervention,
                    "_load_dynamic_access_snapshot_from_report",
                ) as load_report:
            candidate = human_intervention.resolve_human_intervention_candidate(
                claimed_issue,
                workflow_success=False,
            )

        self.assertIsNotNone(candidate)
        self.assertEqual(candidate.reason, "test_generation_failed")
        self.assertEqual(candidate.workflow_status, RUN_STATUS_FAILURE)
        self.assertIsNone(candidate.coverage)
        load_report.assert_not_called()

    def test_successful_fix_queue_run_is_not_a_candidate(self) -> None:
        claimed_issue = _claimed_issue(forge_metadata.LABEL_NI_RUN_FAIL)

        with patch.object(
                human_intervention,
                "load_pending_run_metrics",
                return_value={"strategy_name": "strategy", "status": "success"},
        ):
            candidate = human_intervention.resolve_human_intervention_candidate(
                claimed_issue,
                workflow_success=True,
            )

        self.assertIsNone(candidate)


if __name__ == "__main__":
    unittest.main()
