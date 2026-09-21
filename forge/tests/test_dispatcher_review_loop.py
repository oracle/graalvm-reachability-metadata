# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

from dispatcher_test_support import *  # noqa: F401,F403 - shared dispatcher test fixtures


class PullRequestReviewSelectionTests(unittest.TestCase):
    def test_pull_request_state_loads_exact_head_identity(self) -> None:
        payload = {
            "data": {
                "repository": {
                    "pullRequest": _pull_request_state(9656, "SUCCESS"),
                },
            },
        }
        with patch.object(pr_state, "gh_json", return_value=payload) as gh_json:
            pr_state.get_pull_request_state(9656)

        query_argument = gh_json.call_args.args[-1]
        self.assertIn("headRepository", query_argument)
        self.assertIn("nameWithOwner", query_argument)
        self.assertIn("author", query_argument)
        self.assertIn("statusCheckRollup", query_argument)
        self.assertIn("autoMergeRequest", query_argument)

    def test_descriptor_approval_targets_exact_head_commit(self) -> None:
        state = _pull_request_state(9656, "SUCCESS")
        with patch.object(pr_publication, "gh") as gh:
            pr_publication.approve_pull_request_from_descriptor(state)

        self.assertIn("commit_id=head-9656", gh.call_args.args)
        self.assertIn("event=APPROVE", gh.call_args.args)

    def test_auto_merge_targets_the_exact_approved_head(self) -> None:
        state = _pull_request_state(9656, "PENDING")
        with patch.object(pr_publication, "gh") as gh:
            pr_publication.enable_pull_request_auto_merge(state)

        gh.assert_called_once_with(
            "pr",
            "merge",
            "9656",
            "--repo",
            config.REPO,
            "--auto",
            "--match-head-commit",
            "head-9656",
            "--squash",
        )

    def test_rejected_maintainer_override_is_not_approved(self) -> None:
        state = _pull_request_state(9656, "FAILURE")
        validated = _validated_publication("rejected", "human-intervention")
        with (
                patch.object(
                    review_loop, "validate_pull_request_publication",
                    return_value=validated,
                ),
                patch.object(review_loop, "reconcile_rejected_publication") as reject,
                patch.object(review_loop, "approve_pull_request_from_descriptor") as approve,
                patch.object(review_loop, "enable_pull_request_auto_merge") as enable_auto_merge,
        ):
            review_loop._process_descriptor_pull_request(
                state,
                "/tmp/reachability",
                maintainer_override=True,
            )

        reject.assert_called_once_with(state, validated)
        approve.assert_not_called()
        enable_auto_merge.assert_not_called()

    def test_human_intervention_pr_is_left_unapproved(self) -> None:
        state = _pull_request_state(9656, "SUCCESS")
        state["labels"] = [{"name": config.LABEL_HUMAN_INTERVENTION}]
        with (
                patch.object(
                    review_loop, "validate_pull_request_publication",
                    return_value=_validated_publication(),
                ),
                patch.object(review_loop, "ensure_pull_request_unapproved") as unapprove,
                patch.object(review_loop, "approve_pull_request_from_descriptor") as approve,
                patch.object(review_loop, "enable_pull_request_auto_merge") as enable_auto_merge,
        ):
            review_loop._process_descriptor_pull_request(
                state,
                "/tmp/reachability",
            )

        unapprove.assert_called_once_with(state)
        approve.assert_not_called()
        enable_auto_merge.assert_not_called()

    def test_unresolved_conflict_is_withdrawn_without_approval(self) -> None:
        state = _pull_request_state(9656, "PENDING", mergeable="CONFLICTING")
        events: list[str] = []
        with (
                patch.object(
                    review_loop, "validate_pull_request_publication",
                    return_value=_validated_publication(),
                ),
                patch.object(
                    review_loop, "validate_pull_request_indexes_before_merge",
                ) as validate_indexes,
                patch.object(
                    review_loop, "approve_pull_request_from_descriptor",
                    side_effect=lambda *_: events.append("approve"),
                ),
                patch.object(
                    review_loop, "enable_pull_request_auto_merge",
                    side_effect=lambda *_: events.append("auto-merge"),
                ),
                patch.object(
                    review_loop, "resolve_pull_request_merge_conflict",
                    side_effect=lambda *_: events.append("resolve") or False,
                ),
                patch.object(
                    review_loop, "ensure_pull_request_unapproved",
                    side_effect=lambda *_: events.append("withdraw"),
                ),
                patch.object(review_loop, "add_pull_request_label"),
        ):
            review_loop._process_descriptor_pull_request(state, "/tmp/reachability")

        self.assertEqual(["resolve", "withdraw"], events)
        validate_indexes.assert_not_called()

    def test_human_intervention_withdraws_only_forge_approval(self) -> None:
        state = _pull_request_state(9656, "FAILURE")
        state["reviewDecision"] = "APPROVED"
        state["autoMergeRequest"] = {"enabledAt": "now"}
        reviews = [
            {
                "id": 11,
                "state": "APPROVED",
                "body": f"{config.FORGE_APPROVAL_BODY_PREFIX}head-9656.",
            },
            {"id": 12, "state": "APPROVED", "body": "Human approval."},
        ]
        with (
                patch.object(pr_publication, "get_pull_request_reviews", return_value=reviews),
                patch.object(pr_publication, "gh") as gh,
        ):
            pr_publication.ensure_pull_request_unapproved(state)

        self.assertEqual(2, gh.call_count)
        self.assertIn("--disable-auto", gh.call_args_list[0].args)
        self.assertIn("/reviews/11/dismissals", gh.call_args_list[1].args[3])

    def test_transient_ci_outcome_names_workflow_runs(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            verdict_path = os.path.join(temp_dir, "verdict.json")
            with open(verdict_path, "w", encoding="utf-8") as verdict_file:
                json.dump({
                    "decision": "transient",
                    "workflow_run_ids": [101, 102],
                    "review_comment": "The runner was unavailable.",
                }, verdict_file)

            outcome = ci_repair._read_ci_repair_outcome(verdict_path)

        self.assertIsNotNone(outcome)
        self.assertEqual("transient", outcome.decision)
        self.assertEqual((101, 102), outcome.workflow_run_ids)

    def test_descriptor_dispositions_are_exact(self) -> None:
        self.assertEqual(
            ("approved", None),
            pr_publication.publication_review_disposition(
                _validated_publication(),
            ),
        )
        self.assertEqual(
            ("rejected", "human-intervention"),
            pr_publication.publication_review_disposition(
                _validated_publication("rejected", "human-intervention"),
            ),
        )
        self.assertEqual(
            ("approved", None),
            pr_publication.publication_review_disposition(
                _validated_publication(task_type="code-coverage-benchmark-result"),
            ),
        )

    def test_fork_is_rejected_before_descriptor_fetch(self) -> None:
        state = _pull_request_state(9656, "SUCCESS")
        state["isCrossRepository"] = True
        with patch.object(pr_publication, "run_git_transport") as fetch:
            with self.assertRaisesRegex(ValueError, "head repository"):
                pr_publication.validate_pull_request_publication(
                    state, "/tmp/reachability",
                )
        fetch.assert_not_called()

    def test_approved_publication_arms_auto_merge_without_semantic_agent(self) -> None:
        pull_request = _pull_request(9656, [forge_metadata.LABEL_LIBRARY_NEW])
        state = _pull_request_state(9656, "SUCCESS")
        validated = _validated_publication()
        with (
                patch.object(review_loop, "get_pull_requests_with_labels", return_value=[]),
                patch.object(
                    review_loop, "get_pull_requests_with_label",
                    return_value=[pull_request],
                ),
                patch.object(pr_state, "get_pull_request_state", return_value=state),
                patch.object(
                    review_loop, "validate_pull_request_publication",
                    return_value=validated,
                ),
                patch.object(
                    review_loop, "validate_pull_request_indexes_before_merge",
                ) as validate_indexes,
                patch.object(review_loop, "approve_pull_request_from_descriptor") as approve,
                patch.object(review_loop, "enable_pull_request_auto_merge") as enable_auto_merge,
                patch.object(ci_repair, "analysis_agent_run") as agent,
        ):
            review_loop.process_pull_requests_with_label(
                forge_metadata.LABEL_LIBRARY_NEW,
                1,
                "/tmp/reachability",
                "automation-user",
            )

        validate_indexes.assert_called_once_with(9656, "head-9656", "/tmp/reachability")
        self.assertEqual(9656, approve.call_args.args[0]["number"])
        self.assertEqual(9656, enable_auto_merge.call_args.args[0]["number"])
        agent.assert_not_called()

    def test_rejected_publication_is_reconciled_without_approval(self) -> None:
        pull_request = _pull_request(9656, [forge_metadata.LABEL_LIBRARY_NEW])
        state = _pull_request_state(9656, "FAILURE")
        validated = _validated_publication("rejected", "close")
        with (
                patch.object(review_loop, "get_pull_requests_with_labels", return_value=[]),
                patch.object(
                    review_loop, "get_pull_requests_with_label",
                    return_value=[pull_request],
                ),
                patch.object(pr_state, "get_pull_request_state", return_value=state),
                patch.object(
                    review_loop, "validate_pull_request_publication",
                    return_value=validated,
                ),
                patch.object(review_loop, "reconcile_rejected_publication") as reject,
                patch.object(review_loop, "approve_pull_request_from_descriptor") as approve,
        ):
            review_loop.process_pull_requests_with_label(
                forge_metadata.LABEL_LIBRARY_NEW,
                1,
                "/tmp/reachability",
                "automation-user",
            )

        self.assertEqual(9656, reject.call_args.args[0]["number"])
        self.assertIs(validated, reject.call_args.args[1])
        approve.assert_not_called()

    def test_rejected_close_updates_issue_before_closing_pr(self) -> None:
        events: list[str] = []
        validated = _validated_publication("rejected", "close")
        comments = [{"body": config.LOCAL_REVIEW_CLOSE_MARKER}]
        with (
                patch.object(pr_publication, "get_pull_request_reviews", return_value=[]),
                patch.object(pr_publication, "get_issue_comments", return_value=comments),
                patch.object(
                    pr_publication, "add_issue_label",
                    side_effect=lambda *_: events.append("label-issue"),
                ),
                patch.object(
                    pr_publication, "close_issue",
                    side_effect=lambda *_: events.append("close-issue"),
                ),
                patch.object(
                    pr_publication, "gh",
                    side_effect=lambda *_: events.append("close-pr"),
                ),
        ):
            pr_publication.reconcile_rejected_publication(
                {"number": 9656},
                validated,
            )

        self.assertEqual(events, ["label-issue", "close-issue", "close-pr"])

    def test_ci_repair_paths_are_scoped_to_the_exact_version(self) -> None:
        descriptor = _validated_publication().descriptor
        allowed_paths = [
            "metadata/org.example/demo/index.json",
            "metadata/org.example/demo/1.0/reachability-metadata.json",
            "tests/src/org.example/demo/1.0/build.gradle",
            "stats/org.example/demo/1.0/execution-metrics.json",
            (
                "tests/tck-build-logic/src/main/resources/allowed-docker-images/"
                "Dockerfile-example"
            ),
        ]
        rejected_paths = [
            "metadata/org.example/demo/2.0/reachability-metadata.json",
            "tests/src/org.example/demo/2.0/build.gradle",
            "stats/org.example/demo/2.0/execution-metrics.json",
            "forge/forge_metadata.py",
        ]

        self.assertTrue(all(
            ci_repair._ci_repair_path_is_allowed(path, descriptor)
            for path in allowed_paths
        ))
        self.assertFalse(any(
            ci_repair._ci_repair_path_is_allowed(path, descriptor)
            for path in rejected_paths
        ))

    def test_review_queue_fetches_past_an_ineligible_first_page(self) -> None:
        first_page = [
            _pull_request(number, [forge_metadata.LABEL_LIBRARY_NEW])
            for number in range(1, 21)
        ]
        valid_pull_request = _pull_request(21, [forge_metadata.LABEL_LIBRARY_NEW])
        processed_numbers: list[int] = []

        def process_candidate(
                pull_request: dict,
                _reachability_metadata_path: str,
                maintainer_override: bool = False,
        ) -> None:
            del maintainer_override
            if pull_request["number"] != 21:
                raise ValueError("not a trusted Forge publication")
            processed_numbers.append(pull_request["number"])

        with (
                patch.object(
                    review_loop, "get_pull_requests_with_labels", return_value=[],
                ),
                patch.object(
                    review_loop, "get_pull_requests_with_label",
                    side_effect=[first_page, [*first_page, valid_pull_request]],
                ) as fetch,
                patch.object(
                    review_loop, "attach_pull_request_state",
                    side_effect=lambda pull_request, _: pull_request,
                ),
                patch.object(
                    review_loop, "_process_descriptor_pull_request",
                    side_effect=process_candidate,
                ),
        ):
            review_loop.process_pull_requests_with_label(
                forge_metadata.LABEL_LIBRARY_NEW,
                1,
                "/tmp/reachability",
                "automation-user",
            )

        self.assertEqual(processed_numbers, [21])
        self.assertEqual(
            [item.args[1] for item in fetch.call_args_list],
            [20, 40],
        )

    # The whole check rollup is the gate: nothing is spent, approved, or armed
    # above it (§FS-automated-pr-review).
    MERGE_READINESS_CALLS = (
        "validate_pull_request_indexes_before_merge",
        "approve_pull_request_from_descriptor",
        "enable_pull_request_auto_merge",
    )

    def _review_descriptor_head(self, state: dict, *names: str) -> dict:
        """Review one head with `names` patched, returning the mocks by name."""
        with contextlib.ExitStack() as stack:
            stack.enter_context(patch.object(
                review_loop, "validate_pull_request_publication",
                return_value=_validated_publication(),
            ))
            mocks = {
                name: stack.enter_context(patch.object(review_loop, name))
                for name in names
            }
            review_loop._process_descriptor_pull_request(state, "/tmp/reachability")
        return mocks

    def test_pending_ci_spends_approves_and_arms_nothing(self) -> None:
        mocks = self._review_descriptor_head(
            _pull_request_state(9656, "PENDING"),
            *self.MERGE_READINESS_CALLS,
        )
        for name, mock in mocks.items():
            with self.subTest(call=name):
                mock.assert_not_called()

    def test_failed_ci_is_never_approved_and_gives_up_any_arming(self) -> None:
        state = _pull_request_state(9656, "FAILURE")
        mocks = self._review_descriptor_head(
            state,
            *self.MERGE_READINESS_CALLS,
            "disable_pull_request_auto_merge",
            "reconcile_failed_ci_pull_request",
        )
        for name in self.MERGE_READINESS_CALLS:
            with self.subTest(call=name):
                mocks[name].assert_not_called()
        mocks["disable_pull_request_auto_merge"].assert_called_once_with(state)
        repair = mocks["reconcile_failed_ci_pull_request"]
        repair.assert_called_once()
        self.assertIs(state, repair.call_args.args[0])

    def test_green_ci_validates_indexes_before_approving_and_arming(self) -> None:
        order: list[str] = []
        with contextlib.ExitStack() as stack:
            stack.enter_context(patch.object(
                review_loop, "validate_pull_request_publication",
                return_value=_validated_publication(),
            ))
            for call_name in self.MERGE_READINESS_CALLS:
                stack.enter_context(patch.object(
                    review_loop, call_name,
                    side_effect=lambda *_, name=call_name: order.append(name),
                ))
            for quiet in (
                    "mark_pull_request_merge_follow_up_pending",
                    "reconcile_auto_merged_pull_request_follow_ups",
            ):
                stack.enter_context(patch.object(review_loop, quiet))
            review_loop._process_descriptor_pull_request(
                _pull_request_state(9656, "SUCCESS"),
                "/tmp/reachability",
            )

        self.assertEqual(list(self.MERGE_READINESS_CALLS), order)

    def test_failed_ci_runs_agent_before_any_rerun(self) -> None:
        state = _pull_request_state(9656, "FAILURE")
        validated = _validated_publication()
        with (
                patch.object(ci_repair, "repair_failed_ci_pull_request") as repair,
                patch.object(
                    ci_repair, "rerun_failed_pull_request_workflow_jobs",
                ) as rerun,
        ):
            ci_repair.reconcile_failed_ci_pull_request(
                state,
                validated,
                "/tmp/reachability",
            )

        repair.assert_called_once_with(state, validated, "/tmp/reachability")
        rerun.assert_not_called()

    def test_rerun_failed_jobs_uses_only_agent_selected_current_head_runs(self) -> None:
        workflow_runs = [
            {"id": 101, "conclusion": "failure", "run_attempt": 4},
            {"id": 102, "conclusion": "failure", "run_attempt": 1},
            {"id": 103, "conclusion": "success", "run_attempt": 1},
        ]
        with (
                patch.object(
                    pr_state, "get_pull_request_state",
                    return_value={
                        **_pull_request_state(3513, "FAILURE"),
                        "headRefOid": "abc123",
                    },
                ),
                patch.object(
                    pr_state, "get_pull_request_workflow_runs",
                    return_value=workflow_runs,
                ),
                patch.object(pr_state, "gh") as gh,
        ):
            count = pr_state.rerun_failed_pull_request_workflow_jobs(
                3513,
                "abc123",
                (101,),
            )

        self.assertEqual(1, count)
        gh.assert_called_once_with(
            "api",
            "--method",
            "POST",
            f"/repos/{config.REPO}/actions/runs/101/rerun-failed-jobs",
        )
