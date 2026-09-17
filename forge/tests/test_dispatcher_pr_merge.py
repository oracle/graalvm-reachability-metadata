# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

from dispatcher_test_support import *  # noqa: F401,F403 - shared dispatcher test fixtures


class PullRequestReviewTests(unittest.TestCase):

    def test_merged_auto_merge_follow_up_is_reconciled(self) -> None:
        pull_request = {
            "number": 3513,
            "labels": [{"name": config.LABEL_FORGE_MERGE_FOLLOW_UP}],
            "body": "Refs: #1412\n\nSummary:\n- Chunked dynamic-access: yes\n",
        }
        with (
                patch.object(
                    pr_merge,
                    "get_pull_requests_with_labels",
                    return_value=[pull_request],
                ) as get_pull_requests,
                patch.object(
                    pr_merge, "apply_chunked_dynamic_access_merge_follow_up",
                ) as apply_chunk,
                patch.object(
                    pr_merge, "apply_unblocked_issue_merge_follow_up",
                ) as apply_unblocked,
                patch.object(pr_merge, "remove_pull_request_label") as remove_label,
        ):
            pr_merge.reconcile_auto_merged_pull_request_follow_ups()

        get_pull_requests.assert_called_once_with(
            [config.LABEL_FORGE_MERGE_FOLLOW_UP],
            100,
            state="merged",
        )
        apply_chunk.assert_called_once_with(pull_request)
        apply_unblocked.assert_called_once_with(pull_request)
        remove_label.assert_called_once_with(
            3513,
            config.LABEL_FORGE_MERGE_FOLLOW_UP,
        )

    def test_index_guard_validates_current_master_candidate(self) -> None:
        with patch.object(
                pr_merge,
                "get_pull_request_changed_index_files",
                return_value=["metadata/org.example/demo/index.json"],
        ), patch.object(
                pr_merge,
                "validate_index_files_on_current_master_candidate",
        ) as validate_candidate:
            pr_merge.validate_pull_request_indexes_before_merge(
                3513,
                "abc123",
                "/repo",
            )

        validate_candidate.assert_called_once_with(3513, "abc123", "/repo")

    def test_index_guard_skips_unchanged_indexes(self) -> None:
        with patch.object(
                pr_merge,
                "get_pull_request_changed_index_files",
                return_value=[],
        ), patch.object(
                pr_merge,
                "validate_index_files_on_current_master_candidate",
        ) as validate_candidate:
            pr_merge.validate_pull_request_indexes_before_merge(
                3513,
                "abc123",
                "/repo",
            )

        validate_candidate.assert_not_called()

    def test_chunk_merge_follow_up_releases_non_final_issue(self) -> None:
        pull_request = {
            "number": 3513,
            "body": "Refs: #1412\n\nSummary:\n- Chunked dynamic-access: yes\n",
        }
        with (
                patch.object(pr_merge, "get_project_item_id", return_value="project-item"),
                patch.object(
                    pr_merge,
                    "get_issue_claim_payload",
                    return_value={
                        "labels": [
                            {"name": config.LABEL_CHUNKED_DYNAMIC_ACCESS},
                            {"name": config.LABEL_HUMAN_INTERVENTION},
                            {"name": config.LABEL_RESUMABLE},
                        ],
                    },
                ),
                patch.object(pr_merge, "remove_issue_label") as remove_issue_label,
                patch.object(pr_merge, "set_item_status") as set_item_status,
                patch.object(pr_merge, "clear_issue_assignees") as clear_issue_assignees,
                patch.object(
                    pr_merge, "invalidate_issue_claim_cache_entry",
                ) as invalidate_cache,
        ):
            pr_merge.apply_chunked_dynamic_access_merge_follow_up(pull_request)

        self.assertEqual(
            remove_issue_label.call_args_list,
            [
                call(1412, config.LABEL_HUMAN_INTERVENTION),
                call(1412, config.LABEL_RESUMABLE),
            ],
        )
        set_item_status.assert_called_once_with("project-item", config.STATUS_TODO)
        clear_issue_assignees.assert_called_once_with(1412)
        invalidate_cache.assert_called_once_with(1412)

    def test_chunk_merge_follow_up_does_not_release_final_issue(self) -> None:
        pull_request = {
            "number": 3513,
            "body": "Fixes: #1412\n\nSummary:\n- Chunked dynamic-access: yes\n",
        }
        with (
                patch.object(pr_merge, "set_item_status") as set_item_status,
                patch.object(pr_merge, "clear_issue_assignees") as clear_issue_assignees,
        ):
            pr_merge.apply_chunked_dynamic_access_merge_follow_up(pull_request)

        set_item_status.assert_not_called()
        clear_issue_assignees.assert_not_called()

    def test_index_guard_failure_prevents_approval(self) -> None:
        state = _pull_request_state(3513, "SUCCESS")
        with (
                patch.object(
                    review_loop, "validate_pull_request_publication",
                    return_value=_validated_publication(),
                ),
                patch.object(
                    review_loop,
                    "validate_pull_request_indexes_before_merge",
                    side_effect=RuntimeError("invalid index"),
                ),
                patch.object(review_loop, "approve_pull_request_from_descriptor") as approve,
                patch.object(review_loop, "enable_pull_request_auto_merge") as enable_auto_merge,
        ):
            with self.assertRaises(RuntimeError):
                review_loop._process_descriptor_pull_request(state, "/repo")

        approve.assert_not_called()
        enable_auto_merge.assert_not_called()

    def test_get_pull_request_changed_index_files_filters_library_indexes(self) -> None:
        with patch.object(
                pr_state,
                "get_pull_request_changed_files",
                return_value=[
                    "metadata/org.example/demo/index.json",
                    "metadata/schemas/metadata-library-index-schema-v2.3.0.json",
                    "tests/src/org.example/demo/1.0.0/build.gradle",
                    "metadata/org.example/demo/1.0.0/reachability-metadata.json",
                ],
        ):
            self.assertEqual(
                pr_state.get_pull_request_changed_index_files(3513),
                ["metadata/org.example/demo/index.json"],
            )


    def test_resolve_pull_request_merge_conflict_leaves_fork_heads_alone(self) -> None:
        pr = {
            "number": 3513,
            "headRefOid": "abc123",
            "headRefName": "contributor-branch",
            "isCrossRepository": True,
        }

        with patch.object(worktrees, "create_detached_worktree") as create_detached_worktree:
            self.assertFalse(
                pr_merge.resolve_pull_request_merge_conflict(pr, "/tmp/reachability")
            )

        create_detached_worktree.assert_not_called()

    def test_rerun_failed_jobs_rejects_agent_run_from_another_state(self) -> None:
        workflow_runs = [
            {"id": 101, "conclusion": "failure", "run_attempt": 1},
            {"id": 102, "conclusion": "success", "run_attempt": 1},
        ]

        with patch.object(
                pr_state,
                "get_pull_request_state",
                return_value={
                    **_pull_request_state(3513, "FAILURE"),
                    "headRefOid": "abc123",
                },
        ), patch.object(
                pr_state,
                "get_pull_request_workflow_runs",
                return_value=workflow_runs,
        ), patch.object(pr_state, "gh") as gh:
            self.assertEqual(
                pr_state.rerun_failed_pull_request_workflow_jobs(
                    3513,
                    "abc123",
                    (102,),
                ),
                0,
            )

        gh.assert_not_called()

    def test_fetch_review_base_ref_updates_origin_master_without_pull(self) -> None:
        completed_process = subprocess.CompletedProcess(args=[], returncode=0, stdout="")

        with patch.object(worktrees, "run_git_transport", return_value=completed_process) as run:
            worktrees.fetch_review_base_ref("/repo")

        run.assert_called_once_with(
            [
                "fetch",
                "--quiet",
                "origin",
                "+master:refs/remotes/origin/master",
            ],
            cwd="/repo",
        )


if __name__ == "__main__":
    unittest.main()


class BenchmarkResultsConflictResolutionTests(unittest.TestCase):
    """Keyed union of the per-coordinate benchmark results file.

    The entries are keyed by run ID and the only legal edit is adding one, so
    two publications conflict textually while never disagreeing; the resolver
    takes base plus the head's additions and escalates real disagreement
    (§FS-automated-pr-review).
    """

    _PATH = "code-coverage-benchmarks/com.example/demo/1.0.0.json"

    @staticmethod
    def _entry(run_id: str, timestamp: str, output: int = 1) -> dict:
        return {"runId": run_id, "timestamp": timestamp, "tokens": {"output": output}}

    def _resolve(self, ancestor, head, base):
        stages = {1: ancestor, 2: head, 3: base}
        written: dict = {}

        def fake_run(command, cwd, message):
            if command[:2] == ["git", "show"]:
                stage = int(command[2].split(":")[1])
                return SimpleNamespace(stdout=json.dumps(stages[stage]))
            if command[:2] == ["git", "add"]:
                return SimpleNamespace(stdout="")
            raise AssertionError(f"unexpected command {command}")

        with tempfile.TemporaryDirectory() as worktree:
            os.makedirs(os.path.join(worktree, os.path.dirname(self._PATH)))
            with patch.object(pr_merge, "run_checked_command", side_effect=fake_run):
                resolved = pr_merge.resolve_benchmark_results_conflict(
                    worktree, self._PATH,
                )
            target = os.path.join(worktree, self._PATH)
            if os.path.isfile(target):
                written = json.load(open(target))
        return resolved, written

    def test_union_keeps_both_sides_sorted(self) -> None:
        ancestor = [self._entry("run-a", "2026-09-08T00:00:00Z")]
        head = ancestor + [self._entry("run-c", "2026-09-10T00:00:00Z")]
        base = ancestor + [self._entry("run-b", "2026-09-09T00:00:00Z")]
        resolved, written = self._resolve(ancestor, head, base)
        self.assertTrue(resolved)
        self.assertEqual([e["runId"] for e in written], ["run-a", "run-b", "run-c"])

    def test_identical_run_on_both_sides_is_kept_once(self) -> None:
        ancestor: list = []
        shared = self._entry("run-x", "2026-09-09T00:00:00Z")
        resolved, written = self._resolve(ancestor, [shared], [shared])
        self.assertTrue(resolved)
        self.assertEqual(written, [shared])

    def test_same_run_id_with_different_content_escalates(self) -> None:
        ancestor: list = []
        head = [self._entry("run-x", "2026-09-09T00:00:00Z", output=1)]
        base = [self._entry("run-x", "2026-09-09T00:00:00Z", output=2)]
        resolved, _ = self._resolve(ancestor, head, base)
        self.assertFalse(resolved)

    def test_head_that_modified_an_existing_entry_escalates(self) -> None:
        ancestor = [self._entry("run-a", "2026-09-08T00:00:00Z", output=1)]
        head = [self._entry("run-a", "2026-09-08T00:00:00Z", output=9)]
        base = ancestor + [self._entry("run-b", "2026-09-09T00:00:00Z")]
        resolved, _ = self._resolve(ancestor, head, base)
        self.assertFalse(resolved)

    def test_head_that_dropped_an_existing_entry_escalates(self) -> None:
        ancestor = [
            self._entry("run-a", "2026-09-08T00:00:00Z"),
            self._entry("run-b", "2026-09-09T00:00:00Z"),
        ]
        head = [ancestor[0], self._entry("run-c", "2026-09-10T00:00:00Z")]
        resolved, _ = self._resolve(ancestor, head, ancestor)
        self.assertFalse(resolved)
