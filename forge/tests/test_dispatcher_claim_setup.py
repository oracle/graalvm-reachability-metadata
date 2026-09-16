# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

from dispatcher_test_support import *  # noqa: F401,F403 - shared dispatcher test fixtures


class IssueFormGateClaimOrderTests(unittest.TestCase):
    """Claim preparation pins a fresh base and checks the isolated worktree."""

    def test_issue_base_is_fetched_and_resolved(self) -> None:
        completed_process = subprocess.CompletedProcess(args=[], returncode=0, stdout="")

        with patch.object(
                worktrees,
                "run_git_transport",
                return_value=completed_process,
        ) as run, patch.object(
                worktrees,
                "resolve_git_commit",
                return_value="base-sha",
        ) as resolve:
            commit = worktrees.fetch_issue_base_commit("/repo")

        self.assertEqual(commit, "base-sha")
        run.assert_called_once_with(
            [
                "fetch",
                "--quiet",
                "origin",
                "+master:refs/remotes/origin/master",
            ],
            cwd="/repo",
        )
        resolve.assert_called_once_with("/repo", "refs/remotes/origin/master")

    def test_base_fetch_failure_leaves_issue_unclaimed(self) -> None:
        with patch.object(claim_setup, "refresh_issue_payload_for_claim", return_value=True), \
                patch.object(
                    claim_setup,
                    "fetch_issue_base_commit",
                    side_effect=RuntimeError("origin unavailable"),
                ), \
                patch.object(claim_setup, "try_claim_issue") as claim, \
                patch.object(claim_setup, "create_issue_workspace") as workspace:
            claimed_issue = forge_metadata.claim_issue_for_processing(
                _form_issue(),
                forge_metadata.LABEL_LIBRARY_NEW,
                "/repo",
                "/metrics",
                "runner",
            )

        self.assertIsNone(claimed_issue)
        claim.assert_not_called()
        workspace.assert_not_called()

    def test_malformed_issue_uses_pinned_worktree_before_rejection(self) -> None:
        issue = _form_issue(title="Add support for Widget")
        rejection = issue_form.IssueFormRejection(
            rule=config.ISSUE_FORM_RULE_MAVEN_COORDINATES,
            offending_value="Add support for Widget",
            requirement="Name the coordinates.",
        )
        events: list[str] = []

        with patch.object(claim_setup, "refresh_issue_payload_for_claim", return_value=True), \
                patch.object(
                    claim_setup,
                    "fetch_issue_base_commit",
                    side_effect=lambda *_args: events.append("base") or "base-sha",
                ), \
                patch.object(
                    claim_setup,
                    "try_claim_issue",
                    side_effect=lambda *_args: events.append("claim") or "item-4242",
                ), \
                patch.object(
                    claim_setup,
                    "create_issue_workspace",
                    side_effect=lambda *_args: events.append("workspace") or ("/worktree", "/metrics"),
                ) as workspace, \
                patch.object(
                    claim_setup,
                    "create_preflight_info_dir",
                    return_value="/preflight",
                ), \
                patch.object(
                    claim_setup,
                    "check_issue_form",
                    side_effect=lambda *_args: (
                        events.append("check")
                        or issue_form.IssueFormVerdict(rejection=rejection)
                    ),
                ) as check, \
                patch.object(
                    claim_setup,
                    "reject_issue_form",
                    side_effect=lambda *_args: events.append("reject") or True,
                ) as reject, \
                patch.object(
                    claim_setup,
                    "cleanup_claim_preparation_workspace",
                    side_effect=lambda *_args: events.append("cleanup"),
                ) as cleanup:
            claimed_issue = forge_metadata.claim_issue_for_processing(
                issue,
                forge_metadata.LABEL_LIBRARY_NEW,
                "/repo",
                "/metrics",
                "runner",
            )

        self.assertIsNone(claimed_issue)
        self.assertEqual(events, ["base", "claim", "workspace", "check", "reject", "cleanup"])
        workspace.assert_called_once_with("/repo", "/metrics", 4242, "base-sha")
        check.assert_called_once_with(issue, forge_metadata.LABEL_LIBRARY_NEW, "/worktree")
        reject.assert_called_once_with(issue, rejection)
        cleanup.assert_called_once_with("/repo", "/worktree", "/preflight")

    def test_undecided_form_releases_claim_and_worktree(self) -> None:
        with patch.object(claim_setup, "refresh_issue_payload_for_claim", return_value=True), \
                patch.object(claim_setup, "fetch_issue_base_commit", return_value="base-sha"), \
                patch.object(claim_setup, "try_claim_issue", return_value="item-4242"), \
                patch.object(
                    claim_setup,
                    "create_issue_workspace",
                    return_value=("/worktree", "/metrics"),
                ), \
                patch.object(
                    claim_setup,
                    "create_preflight_info_dir",
                    return_value="/preflight",
                ), \
                patch.object(
                    claim_setup,
                    "check_issue_form",
                    return_value=issue_form.IssueFormVerdict(
                        undecided_reason="host unreachable",
                    ),
                ), \
                patch.object(claim_setup, "reject_issue_form") as reject, \
                patch.object(claim_setup, "revert_issue_claim") as revert, \
                patch.object(claim_setup, "cleanup_claim_preparation_workspace") as cleanup:
            claimed_issue = forge_metadata.claim_issue_for_processing(
                _form_issue(),
                forge_metadata.LABEL_LIBRARY_NEW,
                "/repo",
                "/metrics",
                "runner",
            )

        self.assertIsNone(claimed_issue)
        reject.assert_not_called()
        revert.assert_called_once_with("item-4242", 4242, "issue-form check was undecided")
        cleanup.assert_called_once_with("/repo", "/worktree", "/preflight")

    def test_failed_rejection_releases_claim_and_worktree(self) -> None:
        rejection = issue_form.IssueFormRejection(
            rule=config.ISSUE_FORM_RULE_MAVEN_COORDINATES,
            offending_value="Add support for Widget",
            requirement="Name the coordinates.",
        )
        with patch.object(claim_setup, "refresh_issue_payload_for_claim", return_value=True), \
                patch.object(claim_setup, "fetch_issue_base_commit", return_value="base-sha"), \
                patch.object(claim_setup, "try_claim_issue", return_value="item-4242"), \
                patch.object(
                    claim_setup,
                    "create_issue_workspace",
                    return_value=("/worktree", "/metrics"),
                ), \
                patch.object(
                    claim_setup,
                    "create_preflight_info_dir",
                    return_value="/preflight",
                ), \
                patch.object(
                    claim_setup,
                    "check_issue_form",
                    return_value=issue_form.IssueFormVerdict(rejection=rejection),
                ), \
                patch.object(claim_setup, "reject_issue_form", return_value=False), \
                patch.object(claim_setup, "revert_issue_claim") as revert, \
                patch.object(claim_setup, "cleanup_claim_preparation_workspace") as cleanup:
            claimed_issue = forge_metadata.claim_issue_for_processing(
                _form_issue(title="Add support for Widget"),
                forge_metadata.LABEL_LIBRARY_NEW,
                "/repo",
                "/metrics",
                "runner",
            )

        self.assertIsNone(claimed_issue)
        revert.assert_called_once_with(
            "item-4242",
            4242,
            "issue-form rule 'maven-coordinates' could not close the issue",
        )
        cleanup.assert_called_once_with("/repo", "/worktree", "/preflight")

    def test_unexpected_post_claim_failure_releases_claim_and_worktree(self) -> None:
        with patch.object(claim_setup, "refresh_issue_payload_for_claim", return_value=True), \
                patch.object(claim_setup, "fetch_issue_base_commit", return_value="base-sha"), \
                patch.object(claim_setup, "try_claim_issue", return_value="item-4242"), \
                patch.object(
                    claim_setup,
                    "create_issue_workspace",
                    return_value=("/worktree", "/metrics"),
                ), \
                patch.object(
                    claim_setup,
                    "create_preflight_info_dir",
                    return_value="/preflight",
                ), \
                patch.object(
                    claim_setup,
                    "check_issue_form",
                    return_value=issue_form.ISSUE_FORM_ACCEPTED,
                ), \
                patch.object(
                    claim_setup,
                    "maybe_handle_not_for_native_image_issue",
                    return_value=False,
                ), \
                patch.object(
                    claim_setup,
                    "build_claim_metadata",
                    side_effect=RuntimeError("invalid metadata index"),
                ) as build_metadata, \
                patch.object(claim_setup, "revert_issue_claim") as revert, \
                patch.object(claim_setup, "cleanup_claim_preparation_workspace") as cleanup:
            claimed_issue = forge_metadata.claim_issue_for_processing(
                _form_issue(),
                forge_metadata.LABEL_LIBRARY_NEW,
                "/repo",
                "/metrics",
                "runner",
            )

        self.assertIsNone(claimed_issue)
        build_metadata.assert_called_once_with(
            unittest.mock.ANY,
            forge_metadata.LABEL_LIBRARY_NEW,
            "/worktree",
        )
        revert.assert_called_once_with(
            "item-4242",
            4242,
            "post-claim preparation failure (RuntimeError)",
        )
        cleanup.assert_called_once_with("/repo", "/worktree", "/preflight")

    def test_successful_claim_uses_one_pinned_base_for_all_repository_state(self) -> None:
        issue = _form_issue()
        with patch.object(claim_setup, "refresh_issue_payload_for_claim", return_value=True), \
                patch.object(claim_setup, "fetch_issue_base_commit", return_value="base-sha"), \
                patch.object(claim_setup, "try_claim_issue", return_value="item-4242"), \
                patch.object(
                    claim_setup,
                    "create_issue_workspace",
                    return_value=("/worktree", "/metrics"),
                ) as workspace, \
                patch.object(
                    claim_setup,
                    "create_preflight_info_dir",
                    return_value="/preflight",
                ), \
                patch.object(
                    claim_setup,
                    "check_issue_form",
                    return_value=issue_form.ISSUE_FORM_ACCEPTED,
                ) as check, \
                patch.object(
                    claim_setup,
                    "maybe_handle_not_for_native_image_issue",
                    return_value=False,
                ) as native_image, \
                patch.object(
                    claim_setup,
                    "build_claim_metadata",
                    return_value=("org.example:widget:1.2.3", None, None),
                ) as build_metadata, \
                patch.object(
                    claim_setup,
                    "resolve_issue_continuation_marker",
                    return_value=None,
                ) as continuation, \
                patch.object(
                    claim_setup,
                    "resolve_chunked_dynamic_access_exhaust_report",
                    return_value=None,
                ) as exhaust_report, \
                patch.object(claim_setup, "cleanup_claim_preparation_workspace") as cleanup:
            claimed_issue = forge_metadata.claim_issue_for_processing(
                issue,
                forge_metadata.LABEL_LIBRARY_NEW,
                "/repo",
                "/canonical-metrics",
                "runner",
            )

        self.assertIsNotNone(claimed_issue)
        assert claimed_issue is not None
        self.assertEqual(claimed_issue.issue_base_commit, "base-sha")
        self.assertEqual(claimed_issue.worktree_path, "/worktree")
        workspace.assert_called_once_with("/repo", "/canonical-metrics", 4242, "base-sha")
        check.assert_called_once_with(issue, forge_metadata.LABEL_LIBRARY_NEW, "/worktree")
        native_image.assert_called_once_with(issue, "/worktree")
        build_metadata.assert_called_once_with(issue, forge_metadata.LABEL_LIBRARY_NEW, "/worktree")
        continuation.assert_called_once_with(
            issue,
            forge_metadata.LABEL_LIBRARY_NEW,
            "org.example:widget:1.2.3",
            "/worktree",
        )
        exhaust_report.assert_called_once_with(
            issue,
            "/worktree",
            "org.example:widget:1.2.3",
            None,
        )
        cleanup.assert_not_called()

    def test_missing_chunk_report_returns_claim_to_todo(self) -> None:
        issue = _form_issue(label_names=[
            forge_metadata.LABEL_LIBRARY_NEW,
            config.LABEL_CHUNKED_DYNAMIC_ACCESS,
        ])
        with patch.object(claim_setup, "refresh_issue_payload_for_claim", return_value=True), \
                patch.object(claim_setup, "fetch_issue_base_commit", return_value="base-sha"), \
                patch.object(claim_setup, "try_claim_issue", return_value="item-4242"), \
                patch.object(
                    claim_setup,
                    "create_issue_workspace",
                    return_value=("/worktree", "/metrics"),
                ), \
                patch.object(
                    claim_setup,
                    "create_preflight_info_dir",
                    return_value="/preflight",
                ), \
                patch.object(
                    claim_setup,
                    "check_issue_form",
                    return_value=issue_form.ISSUE_FORM_ACCEPTED,
                ), \
                patch.object(
                    claim_setup,
                    "maybe_handle_not_for_native_image_issue",
                    return_value=False,
                ), \
                patch.object(
                    claim_setup,
                    "build_claim_metadata",
                    return_value=("org.example:widget:1.2.3", None, None),
                ), \
                patch.object(
                    claim_setup,
                    "resolve_issue_continuation_marker",
                    return_value=None,
                ), \
                patch.object(
                    claim_setup,
                    "resolve_chunked_dynamic_access_exhaust_report",
                    side_effect=RuntimeError("missing report"),
                ), \
                patch.object(claim_setup, "revert_issue_claim") as revert, \
                patch.object(claim_setup, "cleanup_claim_preparation_workspace") as cleanup:
            claimed_issue = forge_metadata.claim_issue_for_processing(
                issue,
                forge_metadata.LABEL_LIBRARY_NEW,
                "/repo",
                "/metrics",
                "runner",
            )

        self.assertIsNone(claimed_issue)
        revert.assert_called_once_with(
            "item-4242",
            4242,
            "chunked-dynamic-access setup failure (RuntimeError)",
        )
        cleanup.assert_called_once_with("/repo", "/worktree", "/preflight")

    def test_continuation_rebases_onto_pinned_base_without_refetching_master(self) -> None:
        marker = SimpleNamespace(
            preserved_branch="ai/runner/preserved",
            issue_number=4242,
            continue_from="explore",
        )
        completed = subprocess.CompletedProcess(args=[], returncode=0, stdout="")

        with patch.object(
                continuation,
                "fetch_remote_branch",
                return_value="refs/remotes/origin/ai/runner/preserved",
        ) as fetch_branch, patch.object(
                subprocess,
                "run",
                return_value=completed,
        ) as run:
            resumed = continuation.checkout_continuation_branch(
                "/worktree",
                marker,
                "base-sha",
            )

        self.assertTrue(resumed)
        fetch_branch.assert_called_once_with("/worktree", "ai/runner/preserved")
        self.assertIn(
            call(
                ["git", "rebase", "base-sha"],
                cwd="/worktree",
                env=unittest.mock.ANY,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                check=False,
            ),
            run.call_args_list,
        )
