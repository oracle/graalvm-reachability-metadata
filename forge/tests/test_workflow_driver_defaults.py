# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import io
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest.mock import patch

import forge_metadata
from utility_scripts.dynamic_access_report import DynamicAccessClass, DynamicAccessCoverageReport
from utility_scripts.strategy_loader import load_predefined_strategies


def _report(uncovered_class_count: int) -> DynamicAccessCoverageReport:
    classes = [
        DynamicAccessClass(
            class_name=f"g.a.Class{index}",
            source_file=None,
            resolved_source_file=None,
            total_calls=1,
            covered_calls=0,
            call_sites=[],
        )
        for index in range(uncovered_class_count)
    ]
    return DynamicAccessCoverageReport(
        coordinate="g:a:2.0",
        has_dynamic_access=True,
        total_calls=uncovered_class_count,
        covered_calls=0,
        classes=classes,
    )


def _claimed_issue(
        label: str,
        continuation_marker: forge_metadata.ContinuationMarker | None = None,
) -> forge_metadata.ClaimedIssue:
    return forge_metadata.ClaimedIssue(
        issue={"number": 1412},
        label=label,
        item_id="item-1",
        base_reachability_metadata_path="/repo",
        worktree_path="/worktree",
        scratch_metrics_repo_path="/metrics",
        issue_coordinates="g:a:2.0",
        current_coordinates="g:a:1.0",
        new_version="2.0",
        continuation_marker=continuation_marker,
    )


class WorkflowDriverDefaultTests(unittest.TestCase):
    def test_new_and_update_queues_default_to_sol_composite_strategies(self) -> None:
        self.assertEqual(
            forge_metadata.DEFAULT_NEW_LIBRARY_STRATEGY_NAME,
            "optimistic_dynamic_access_iterative_pi_gpt-5.6-sol",
        )
        self.assertEqual(
            forge_metadata.DEFAULT_LIBRARY_UPDATE_STRATEGY_NAME,
            "library_update_optimistic_pi_gpt-5.6-sol",
        )
        self.assertEqual(
            forge_metadata.DEFAULT_WORK_QUEUE_STRATEGY_NAME,
            "optimistic_dynamic_access_iterative_pi_gpt-5.6-sol",
        )

    def test_do_work_defaults_new_libraries_to_the_sol_composite(self) -> None:
        script_path: Path = Path(__file__).parents[1] / "do_up_to_date_work.sh"
        script: str = script_path.read_text(encoding="utf-8")
        self.assertIn(
            'WORK_STRATEGY_NAME="${FORGE_STRATEGY_NAME:-'
            'optimistic_dynamic_access_iterative_pi_gpt-5.6-sol}"',
            script,
        )

    def test_bulk_phase_strategies_prepare_report_before_deferring_selection(self) -> None:
        # The deferred decision is the chunk boundary, not the report the bulk
        # phase measures itself against (§FS-forge-chunked-dynamic-access).
        strategies = (
            "dynamic_access_bulk_pi_gpt-5.6-sol",
            "optimistic_dynamic_access_iterative_pi_gpt-5.6-sol",
        )
        with patch.object(forge_metadata, "_prepare_new_library_dynamic_access_report", return_value=True) \
                as prepare_report, \
                patch.object(forge_metadata, "_generate_dispatcher_dynamic_access_report") as generate_report, \
                patch.object(forge_metadata, "_resolve_dynamic_access_report_path", return_value="/worktree/report.json"), \
                patch.object(forge_metadata, "load_dynamic_access_coverage_report", return_value=_report(3)):
            for strategy_name in strategies:
                with self.subTest(strategy=strategy_name):
                    output = io.StringIO()
                    with redirect_stdout(output):
                        chunk_count = forge_metadata.prepare_dynamic_access_chunking(
                            _claimed_issue(forge_metadata.LABEL_LIBRARY_NEW),
                            strategy_name,
                        )
                    self.assertEqual(chunk_count, 15)
                    self.assertIn("uncovered_classes=3, class_boundary=15", output.getvalue())
        self.assertEqual(prepare_report.call_count, len(strategies))
        self.assertEqual(generate_report.call_count, len(strategies))

    def test_bulk_deferral_names_an_unavailable_report(self) -> None:
        with patch.object(forge_metadata, "_prepare_new_library_dynamic_access_report", return_value=True), \
                patch.object(forge_metadata, "_generate_dispatcher_dynamic_access_report"), \
                patch.object(forge_metadata, "_resolve_dynamic_access_report_path", return_value="/worktree/report.json"), \
                patch.object(forge_metadata, "load_dynamic_access_coverage_report", side_effect=FileNotFoundError):
            output = io.StringIO()
            with redirect_stdout(output):
                chunk_count = forge_metadata.prepare_dynamic_access_chunking(
                    _claimed_issue(forge_metadata.LABEL_LIBRARY_NEW),
                    "dynamic_access_bulk_pi_gpt-5.6-sol",
                )
        self.assertEqual(chunk_count, 15)
        self.assertIn("uncovered_classes=unavailable", output.getvalue())

    def test_bulk_new_library_outside_native_image_disables_chunking(self) -> None:
        with patch.object(forge_metadata, "_prepare_new_library_dynamic_access_report", return_value=False), \
                patch.object(forge_metadata, "_generate_dispatcher_dynamic_access_report") as generate_report:
            chunk_count = forge_metadata.prepare_dynamic_access_chunking(
                _claimed_issue(forge_metadata.LABEL_LIBRARY_NEW),
                "dynamic_access_bulk_pi_gpt-5.6-sol",
            )
        self.assertIsNone(chunk_count)
        generate_report.assert_not_called()

    def test_library_update_preparation_precedes_the_deferred_decision(self) -> None:
        with patch.object(forge_metadata, "_prepare_library_update_dynamic_access_report") as prepare_target, \
                patch.object(forge_metadata, "_generate_dispatcher_dynamic_access_report") as generate_report, \
                patch.object(forge_metadata, "_resolve_dynamic_access_report_path", return_value="/worktree/report.json"), \
                patch.object(forge_metadata, "load_dynamic_access_coverage_report", return_value=_report(2)):
            chunk_count = forge_metadata.prepare_dynamic_access_chunking(
                _claimed_issue(forge_metadata.LABEL_LIBRARY_UPDATE),
                "library_update_optimistic_pi_gpt-5.6-sol",
            )
        self.assertEqual(chunk_count, 15)
        prepare_target.assert_called_once()
        generate_report.assert_called_once()

    def test_optimistic_resume_uses_remaining_active_chunk_budget(self) -> None:
        marker = forge_metadata.ContinuationMarker.create(
            strategy_name="optimistic_dynamic_access_iterative_pi_gpt-5.6-sol",
            issue_number=1412,
            label=forge_metadata.LABEL_LIBRARY_NEW,
            coordinate="g:a:2.0",
            new_version="2.0",
        )
        marker.mark_phase_completed("setup")
        marker.mark_phase_skipped("fix")
        marker.mark_phase_running("explore")
        marker.record_chunk_progress(5, 2)
        claimed_issue = _claimed_issue(
            forge_metadata.LABEL_LIBRARY_NEW,
            continuation_marker=marker,
        )

        with patch.object(forge_metadata, "_prepare_new_library_dynamic_access_report") as prepare_report, \
                patch.object(forge_metadata, "_generate_dispatcher_dynamic_access_report") as generate_report, \
                patch.object(forge_metadata, "_resolve_dynamic_access_report_path", return_value="/worktree/report.json"), \
                patch.object(forge_metadata, "load_dynamic_access_coverage_report", return_value=_report(3)), \
                patch.dict(
                    "os.environ",
                    {"FORGE_DYNAMIC_ACCESS_CHUNK_CLASS_THRESHOLD": "15"},
                    clear=True,
                ):
            chunk_count = forge_metadata.prepare_dynamic_access_chunking(
                claimed_issue,
                "optimistic_dynamic_access_iterative_pi_gpt-5.6-sol",
            )

        self.assertEqual(chunk_count, 3)
        prepare_report.assert_not_called()
        generate_report.assert_called_once()

    def test_optimistic_resume_preserves_exhausted_active_chunk_budget(self) -> None:
        marker = forge_metadata.ContinuationMarker.create(
            strategy_name="optimistic_dynamic_access_iterative_pi_gpt-5.6-sol",
            issue_number=1412,
            label=forge_metadata.LABEL_LIBRARY_NEW,
            coordinate="g:a:2.0",
            new_version="2.0",
        )
        marker.mark_phase_completed("setup")
        marker.mark_phase_skipped("fix")
        marker.mark_phase_running("explore")
        marker.record_chunk_progress(5, 5)
        claimed_issue = _claimed_issue(
            forge_metadata.LABEL_LIBRARY_NEW,
            continuation_marker=marker,
        )

        with patch.object(forge_metadata, "_prepare_new_library_dynamic_access_report") as prepare_report, \
                patch.object(forge_metadata, "_generate_dispatcher_dynamic_access_report") as generate_report, \
                patch.object(forge_metadata, "_resolve_dynamic_access_report_path", return_value="/worktree/report.json"), \
                patch.object(forge_metadata, "load_dynamic_access_coverage_report", return_value=_report(3)), \
                patch.dict(
                    "os.environ",
                    {"FORGE_DYNAMIC_ACCESS_CHUNK_CLASS_THRESHOLD": "15"},
                    clear=True,
                ):
            chunk_count = forge_metadata.prepare_dynamic_access_chunking(
                claimed_issue,
                "optimistic_dynamic_access_iterative_pi_gpt-5.6-sol",
            )

        self.assertEqual(chunk_count, 0)
        prepare_report.assert_not_called()
        generate_report.assert_called_once()

    def test_coverage_composites_declare_the_reporter_metadata_prompt(self) -> None:
        # A library-update issue body may carry reporter-requested metadata, and
        # the composite then runs that phase unconditionally. A coverage
        # composite without the prompt raises instead of covering the request.
        coverage_composites = [
            strategy for strategy in load_predefined_strategies()
            if strategy.get("workflow") == "increase_dynamic_access_coverage"
            and strategy.get("primary-workflow") in {None, "bulk_dynamic_access"}
        ]
        self.assertTrue(coverage_composites)
        for strategy in coverage_composites:
            with self.subTest(strategy=strategy["name"]):
                self.assertIn("issue-requested-metadata", strategy["prompts"])

    def test_java_repair_queues_default_to_sol_strategies(self) -> None:
        expected_defaults = {
            forge_metadata.LABEL_JAVAC_FAIL:
                "javac_iterative_with_coverage_sources_pi_gpt-5.6-sol",
            forge_metadata.LABEL_JAVA_RUN_FAIL:
                "java_run_iterative_with_coverage_sources_pi_gpt-5.6-sol",
        }

        for label, expected_strategy in expected_defaults.items():
            with self.subTest(label=label):
                self.assertEqual(
                    forge_metadata.resolve_workflow_default_strategy_name(
                        _claimed_issue(label),
                        library_update_route=None,
                    ),
                    expected_strategy,
                )

    def test_direct_native_image_run_forwards_strategy_override(self) -> None:
        invocation = forge_metadata.build_workflow_driver_invocation(
            claimed_issue=_claimed_issue(forge_metadata.LABEL_NI_RUN_FAIL),
            strategy_name="library_update_dynamic_access_bulk_pi_gpt-5.6-sol",
            keep_tests_without_dynamic_access=False,
        )

        self.assertIn("--strategy-name", invocation.argv)
        strategy_index = invocation.argv.index("--strategy-name")
        self.assertEqual(
            invocation.argv[strategy_index + 1],
            "library_update_dynamic_access_bulk_pi_gpt-5.6-sol",
        )

    def test_direct_native_image_run_omits_unset_strategy_override(self) -> None:
        invocation = forge_metadata.build_workflow_driver_invocation(
            claimed_issue=_claimed_issue(forge_metadata.LABEL_NI_RUN_FAIL),
            strategy_name=None,
            keep_tests_without_dynamic_access=False,
        )

        self.assertNotIn("--strategy-name", invocation.argv)
        self.assertEqual(
            forge_metadata.resolve_workflow_default_strategy_name(
                _claimed_issue(forge_metadata.LABEL_NI_RUN_FAIL),
                library_update_route=None,
            ),
            "library_update_pi_gpt-5.6-sol",
        )


if __name__ == "__main__":
    unittest.main()
