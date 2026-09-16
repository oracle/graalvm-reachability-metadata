# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

from dispatcher_test_support import *  # noqa: F401,F403 - shared dispatcher test fixtures


class IssueFormGateTests(unittest.TestCase):
    """The claim-held issue-form gate. §FS-forge-run-requirements.3"""

    def test_well_formed_issue_is_accepted(self) -> None:
        with patch.object(issue_form, "artifact_is_published", return_value=True):
            verdict = issue_form.check_issue_form(
                _form_issue(),
                forge_metadata.LABEL_LIBRARY_NEW,
                "/nonexistent",
            )

        self.assertTrue(verdict.accepted)
        self.assertIsNone(verdict.rejection)

    def test_two_workflow_labels_are_rejected_naming_both(self) -> None:
        issue = _form_issue(
            label_names=[
                forge_metadata.LABEL_LIBRARY_NEW,
                forge_metadata.LABEL_JAVAC_FAIL,
                config.LABEL_PRIORITY,
            ],
        )

        with patch.object(issue_form, "artifact_is_published") as is_published:
            verdict = issue_form.check_issue_form(
                issue,
                forge_metadata.LABEL_LIBRARY_NEW,
                "/nonexistent",
            )

        self.assertEqual(verdict.rejection.rule, config.ISSUE_FORM_RULE_SINGLE_WORKFLOW_LABEL)
        self.assertEqual(
            verdict.rejection.offending_value,
            f"{forge_metadata.LABEL_JAVAC_FAIL}, {forge_metadata.LABEL_LIBRARY_NEW}",
        )
        # A rule decidable from the payload never reaches for the network.
        is_published.assert_not_called()

    def test_title_without_coordinates_is_rejected_quoting_the_title(self) -> None:
        issue = _form_issue(title="Please add support for Widget")

        with patch.object(issue_form, "artifact_is_published") as is_published:
            verdict = issue_form.check_issue_form(
                issue,
                forge_metadata.LABEL_LIBRARY_NEW,
                "/nonexistent",
            )

        self.assertEqual(verdict.rejection.rule, config.ISSUE_FORM_RULE_MAVEN_COORDINATES)
        self.assertEqual(verdict.rejection.offending_value, "Please add support for Widget")
        is_published.assert_not_called()

    def test_failure_issue_without_latest_entry_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path:
            _write_index(repo_path, "org.example", "widget", [{"metadata-version": "1.0.0"}])

            with (
                    patch.object(issue_form, "artifact_is_published") as is_published,
                    patch("sys.stderr", new_callable=io.StringIO) as stderr,
            ):
                verdict = issue_form.check_issue_form(
                    _form_issue(
                        title="Fix javac failure for org.example:widget:1.2.3",
                        label_names=[forge_metadata.LABEL_JAVAC_FAIL],
                    ),
                    forge_metadata.LABEL_JAVAC_FAIL,
                    repo_path,
                )

        self.assertEqual(verdict.rejection.rule, config.ISSUE_FORM_RULE_CURRENT_LATEST_VERSION)
        self.assertEqual(verdict.rejection.offending_value, "org.example:widget")
        is_published.assert_not_called()
        self.assertEqual("", stderr.getvalue())

    def test_failure_issue_at_or_below_latest_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path:
            _write_index(
                repo_path,
                "org.example",
                "widget",
                [{"metadata-version": "2.0.0", "latest": True}],
            )

            with patch.object(issue_form, "artifact_is_published") as is_published:
                verdict = issue_form.check_issue_form(
                    _form_issue(
                        title="Fix javac failure for org.example:widget:1.9.0",
                        label_names=[forge_metadata.LABEL_JAVAC_FAIL],
                    ),
                    forge_metadata.LABEL_JAVAC_FAIL,
                    repo_path,
                )

        self.assertEqual(verdict.rejection.rule, config.ISSUE_FORM_RULE_NEWER_THAN_LATEST)
        self.assertEqual(verdict.rejection.offending_value, "1.9.0")
        self.assertIn("2.0.0", verdict.rejection.requirement)
        is_published.assert_not_called()

    def test_failure_issue_above_latest_passes_the_version_rule(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path:
            _write_index(
                repo_path,
                "org.example",
                "widget",
                [{"metadata-version": "2.0.0", "latest": True}],
            )

            with patch.object(issue_form, "artifact_is_published", return_value=True):
                verdict = issue_form.check_issue_form(
                    _form_issue(
                        title="Fix javac failure for org.example:widget:2.1.0",
                        label_names=[forge_metadata.LABEL_JAVAC_FAIL],
                    ),
                    forge_metadata.LABEL_JAVAC_FAIL,
                    repo_path,
                )

        self.assertTrue(verdict.accepted)

    def test_unpublished_coordinate_is_rejected_naming_the_repositories(self) -> None:
        with patch.object(issue_form, "artifact_is_published", return_value=False):
            verdict = issue_form.check_issue_form(
                _form_issue(),
                forge_metadata.LABEL_LIBRARY_NEW,
                "/nonexistent",
            )

        self.assertEqual(verdict.rejection.rule, config.ISSUE_FORM_RULE_PUBLISHED_ARTIFACT)
        self.assertEqual(verdict.rejection.offending_value, "org.example:widget:1.2.3")
        for repository_url in ARTIFACT_REPOSITORY_URLS:
            self.assertIn(repository_url, verdict.rejection.requirement)

    def test_unreachable_repository_leaves_the_form_undecided(self) -> None:
        with patch.object(issue_form, "artifact_is_published", return_value=None):
            verdict = issue_form.check_issue_form(
                _form_issue(),
                forge_metadata.LABEL_LIBRARY_NEW,
                "/nonexistent",
            )

        self.assertIsNone(verdict.rejection)
        self.assertFalse(verdict.accepted)
        self.assertIn("org.example:widget:1.2.3", verdict.undecided_reason)


class IssueFormRejectionTests(unittest.TestCase):
    """Rejection feedback: one comment, then a closed issue. §FS-forge-run-requirements.3"""

    def setUp(self) -> None:
        self.addCleanup(setattr, fixture_support, "fixture_github_state", None)
        self.addCleanup(forge_metadata.clear_issue_caches)

    def _reject(self, state: FixtureGitHubState, issue: dict) -> issue_form.IssueFormRejection:
        forge_metadata.configure_fixture_testing(fixture_state=state)
        rejection = issue_form.check_issue_form(
            issue,
            forge_metadata.LABEL_LIBRARY_NEW,
            "/nonexistent",
        ).rejection
        issue_form.reject_issue_form(issue, rejection)
        return rejection

    def test_rejection_comments_the_rule_then_closes_the_issue(self) -> None:
        state = FixtureGitHubState([_fixture_form_issue(title="Add support for Widget")])
        issue = _form_issue(title="Add support for Widget")

        with patch("sys.stdout", new_callable=io.StringIO) as stdout:
            rejection = self._reject(state, issue)

        comments = state.get_issue_comments(4242)
        self.assertEqual(len(comments), 1)
        body = comments[0]["body"]
        self.assertIn(config.ISSUE_FORM_RULE_MAVEN_COORDINATES, body)
        self.assertIn("`Add support for Widget`", body)
        self.assertIn(rejection.requirement, body)
        self.assertEqual(state.get_issue_claim_payload(4242)["state"], "CLOSED")
        self.assertNotIn(
            config.LABEL_HUMAN_INTERVENTION,
            state.get_issue_labels(4242),
        )
        output = stdout.getvalue()
        self.assertIn(
            "[issue-form] Rejecting issue #4242: rule 'maven-coordinates'",
            output,
        )
        self.assertIn(
            "[issue-form] Posting rejection comment to issue #4242: "
            "rule 'maven-coordinates'",
            output,
        )
        self.assertIn(
            "[issue-close] Closing issue #4242: "
            "issue-form rule 'maven-coordinates' failed",
            output,
        )

    def _reopened_state(self, title: str) -> FixtureGitHubState:
        """Fixture state for an issue reopened with its rejection comment still on it."""
        rejection = issue_form.IssueFormRejection(
            rule=config.ISSUE_FORM_RULE_MAVEN_COORDINATES,
            offending_value=title,
            requirement="Name the coordinates.",
        )
        return FixtureGitHubState([
            _fixture_form_issue(
                title=title,
                comments=[FixtureComment(
                    author=config.FIXTURE_AUTHENTICATED_USER,
                    body=issue_form.build_issue_form_rejection_comment(rejection),
                )],
            ),
        ])

    def test_reopened_issue_with_the_same_defect_is_closed_without_a_second_comment(self) -> None:
        state = self._reopened_state("Add support for Widget")

        with patch("sys.stdout", new_callable=io.StringIO) as stdout:
            self._reject(state, _form_issue(title="Add support for Widget"))

        self.assertEqual(len(state.get_issue_comments(4242)), 1)
        self.assertEqual(state.get_issue_claim_payload(4242)["state"], "CLOSED")
        self.assertIn(
            "[issue-form] Skipping rejection comment for issue #4242: "
            "rule 'maven-coordinates' was already reported",
            stdout.getvalue(),
        )

    def test_edited_title_is_judged_afresh_and_gets_its_own_comment(self) -> None:
        state = self._reopened_state("Add support for Widget")

        self._reject(state, _form_issue(title="Add support for Widget please"))

        self.assertEqual(len(state.get_issue_comments(4242)), 2)

    def _reject_live(self, issue: dict) -> tuple:
        rejection = issue_form.IssueFormRejection(
            rule=config.ISSUE_FORM_RULE_MAVEN_COORDINATES,
            offending_value="Add support for Widget",
            requirement="Name the coordinates.",
        )
        events: list[str] = []

        def record(event: str) -> Callable[..., None]:
            return lambda *_args, **_kwargs: events.append(event)

        with patch.object(issue_form, "get_issue_comments", return_value=[]), \
                patch.object(
                    issue_form,
                    "post_issue_comment",
                    side_effect=record("comment"),
                ) as comment, \
                patch.object(
                    issue_form,
                    "close_issue",
                    side_effect=record("close"),
                ) as close, \
                patch.object(
                    issue_form,
                    "clear_issue_assignees",
                    side_effect=record("clear"),
                ) as clear, \
                patch.object(issue_admin, "add_issue_label") as label:
            succeeded = issue_form.reject_issue_form(issue, rejection)
        return succeeded, events, comment, close, clear, label

    def test_rejection_closes_claim_before_clearing_assignee(self) -> None:
        issue = _form_issue(title="Add support for Widget", assignees=["runner"])

        succeeded, events, comment, close, clear, label = self._reject_live(issue)

        self.assertTrue(succeeded)
        self.assertEqual(events, ["comment", "close", "clear"])
        comment.assert_called_once()
        close.assert_called_once()
        clear.assert_called_once_with(4242)
        label.assert_not_called()

    def test_failed_comment_does_not_close_or_clear_the_claim(self) -> None:
        rejection = issue_form.IssueFormRejection(
            rule=config.ISSUE_FORM_RULE_MAVEN_COORDINATES,
            offending_value="Add support for Widget",
            requirement="Name the coordinates.",
        )
        with patch.object(issue_form, "get_issue_comments", return_value=[]), \
                patch.object(
                    issue_form,
                    "post_issue_comment",
                    side_effect=RuntimeError("comment failed"),
                ), \
                patch.object(issue_form, "close_issue") as close, \
                patch.object(issue_form, "clear_issue_assignees") as clear:
            succeeded = issue_form.reject_issue_form(
                _form_issue(title="Add support for Widget", assignees=["runner"]),
                rejection,
            )

        self.assertFalse(succeeded)
        close.assert_not_called()
        clear.assert_not_called()
