# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

from dispatcher_test_support import *  # noqa: F401,F403 - shared dispatcher test fixtures


class FinalizeSuccessfulIssueTests(unittest.TestCase):
    def test_restores_missing_pending_metrics_from_execution_metrics_and_marker_extras(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path:
            forge_path = os.path.join(repo_path, "forge")
            metadata_index_dir = os.path.join(repo_path, "metadata", "org.example", "lib")
            stats_dir = os.path.join(repo_path, "stats", "org.example", "lib", "1.0.0")
            os.makedirs(forge_path)
            os.makedirs(metadata_index_dir)
            os.makedirs(stats_dir)
            with open(os.path.join(metadata_index_dir, "index.json"), "w", encoding="utf-8") as index_file:
                json.dump(
                    [
                        {
                            "metadata-version": "1.0.0",
                            "tested-versions": ["1.0.0"],
                        }
                    ],
                    index_file,
                )
            run_metrics = {
                "timestamp": "2026-06-18T14:44:56.782450Z",
                "library": "org.example:lib:1.0.0",
                "status": "success",
                "metrics": {"input_tokens_used": 1},
            }
            with open(os.path.join(stats_dir, "execution-metrics.json"), "w", encoding="utf-8") as metrics_file:
                json.dump({"add_new_library_support:2026-06-18": run_metrics}, metrics_file)
            marker = ContinuationMarker.create(
                strategy_name="dynamic_access_main_sources_pi_gpt-5.6-sol",
                issue_number=1412,
                label=forge_metadata.LABEL_LIBRARY_NEW,
                coordinate="org.example:lib:1.0.0",
                new_version=None,
            )
            pending_metrics = {
                **run_metrics,
                "post_generation_intervention": {"stage": "future-defaults-all"},
                "local_ci_verification": {"status": "passed"},
            }
            marker.record_publication_metrics(pending_metrics, config.PUBLICATION_METRICS_EXTRA_KEYS)
            marker.save(continuation_marker_path(repo_path))

            claimed_issue = records.ClaimedIssue(
                issue={"number": 1412},
                label=forge_metadata.LABEL_LIBRARY_NEW,
                item_id="project-item",
                base_reachability_metadata_path=repo_path,
                worktree_path=repo_path,
                scratch_metrics_repo_path=forge_path,
                issue_coordinates="org.example:lib:1.0.0",
            )

            publication.restore_pending_run_metrics_from_execution_metrics(claimed_issue)

            with open(os.path.join(forge_path, PENDING_METRICS_FILENAME), "r", encoding="utf-8") as pending_file:
                self.assertEqual(json.load(pending_file), pending_metrics)

    def test_records_existing_pending_metrics_in_continuation_marker(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path:
            forge_path = os.path.join(repo_path, "forge")
            os.makedirs(forge_path)
            run_metrics = {
                "timestamp": "2026-06-18T14:44:56.782450Z",
                "library": "org.example:lib:1.0.0",
                "status": "success",
                "metrics": {"input_tokens_used": 1},
                "post_generation_intervention": {"stage": "current-defaults"},
                "library_update_alias_split": {"follow_up_issue": 1413},
            }
            with open(os.path.join(forge_path, PENDING_METRICS_FILENAME), "w", encoding="utf-8") as pending_file:
                json.dump(run_metrics, pending_file)
            marker = ContinuationMarker.create(
                strategy_name="dynamic_access_main_sources_pi_gpt-5.6-sol",
                issue_number=1412,
                label=forge_metadata.LABEL_LIBRARY_NEW,
                coordinate="org.example:lib:1.0.0",
                new_version=None,
            )
            marker.save(continuation_marker_path(repo_path))

            claimed_issue = records.ClaimedIssue(
                issue={"number": 1412},
                label=forge_metadata.LABEL_LIBRARY_NEW,
                item_id="project-item",
                base_reachability_metadata_path=repo_path,
                worktree_path=repo_path,
                scratch_metrics_repo_path=forge_path,
                issue_coordinates="org.example:lib:1.0.0",
            )

            publication.restore_pending_run_metrics_from_execution_metrics(claimed_issue)

            saved_marker = load_continuation_marker(continuation_marker_path(repo_path))
            self.assertIsNotNone(saved_marker)
            self.assertEqual(
                saved_marker.publication_metrics,
                {
                    "library": "org.example:lib:1.0.0",
                    "timestamp": "2026-06-18T14:44:56.782450Z",
                    "extras": {
                        "post_generation_intervention": {"stage": "current-defaults"},
                        "library_update_alias_split": {"follow_up_issue": 1413},
                    },
                },
            )

    def test_not_for_native_image_pr_receives_metrics_repo_path_for_local_ci(self) -> None:
        claimed_issue = _claimed_issue()

        with patch.object(publication, "find_dynamic_access_exhaust_report_path", return_value=None), \
                patch.object(publication, "require_claimed_issue_worktree"), \
                patch.object(publication, "_load_pending_run_metrics", return_value={"status": "success"}), \
                patch.object(publication, "metadata_coordinate_parts", return_value=("org.example", "lib", "1.0.0")), \
                patch.object(publication, "is_not_for_native_image", return_value=True), \
                patch.object(publication, "run_publish_not_for_native_image") as make_pr:
            lifecycle.finalize_successful_issue(claimed_issue)

        make_pr.assert_called_once_with([
            "--coordinates", "org.example:lib:1.0.0",
            "--issue-number", "1412",
            "--reachability-metadata-path", "/tmp/reachability-worktree",
            "--metrics-repo-path", "/tmp/metrics-worktree",
        ])
