# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import io
import json
import os
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from ai_workflows.core.increase_dynamic_access_coverage_strategy import IncreaseDynamicAccessCoverageStrategy
from ai_workflows.core.dynamic_access_iterative_strategy import DynamicAccessIterativeStrategy
from ai_workflows.core.workflow_strategy import RUN_STATUS_CHUNK_READY, RUN_STATUS_FAILURE, RUN_STATUS_SUCCESS
from utility_scripts.dynamic_access_report import DynamicAccessClass, DynamicAccessCoverageReport
from utility_scripts.dynamic_access_exhaust_report import DynamicAccessExhaustReport


class DynamicAccessProgressLoggingTests(unittest.TestCase):
    def test_class_completion_progress_prints_overall_coverage(self) -> None:
        report = DynamicAccessCoverageReport(
            coordinate="org.example:lib:1.0.0",
            has_dynamic_access=True,
            total_calls=103,
            covered_calls=45,
            classes=[],
        )

        output = io.StringIO()
        with patch("sys.stdout", output):
            DynamicAccessIterativeStrategy._print_class_completion_progress(
                "org.example.SomeClass",
                5,
                35,
                report,
            )

        self.assertEqual(
            output.getvalue().strip(),
            "[dynamic-access] ===================================================================================\n"
            "[dynamic-access] Progress after org.example.SomeClass: "
            "classes 5/35 complete; overall coverage 45/103 covered (58 remaining)\n"
            "[dynamic-access] ===================================================================================",
        )

    def test_completed_class_count_includes_fully_covered_report_classes(self) -> None:
        report = DynamicAccessCoverageReport(
            coordinate="org.example:lib:1.0.0",
            has_dynamic_access=True,
            total_calls=5,
            covered_calls=3,
            classes=[
                self._class_coverage("org.example.Covered", 2, 2),
                self._class_coverage("org.example.Exhausted", 2, 1),
                self._class_coverage("org.example.Pending", 1, 0),
            ],
        )

        self.assertEqual(
            DynamicAccessIterativeStrategy._completed_class_count(
                report,
                {"org.example.Exhausted"},
            ),
            2,
        )

    def test_commit_library_metadata_stages_index_resolved_metadata_version(self) -> None:
        with tempfile.TemporaryDirectory() as repo:
            metadata_root = os.path.join(repo, "metadata", "org.example", "lib")
            os.makedirs(metadata_root)
            with open(os.path.join(metadata_root, "index.json"), "w", encoding="utf-8") as index_file:
                json.dump([
                    {
                        "metadata-version": "1.0.0",
                        "tested-versions": ["1.0.0", "1.1.0"],
                    }
                ], index_file)
            strategy = self._strategy(
                library="org.example:lib:1.1.0",
                reachability_repo_path=repo,
            )
            calls: list[list[str]] = []
            metadata_dir = os.path.join(repo, "metadata", "org.example", "lib", "1.0.0")

            def fake_run(cmd, **kwargs):  # type: ignore[no-untyped-def]
                calls.append(list(cmd))
                # `check_output` for `git rev-parse HEAD` delegates to `subprocess.run`.
                if "rev-parse" in cmd:
                    return subprocess.CompletedProcess(cmd, 0, stdout="abc123\n")
                # Simulate `git diff --cached --quiet` finding staged changes (rc=1).
                if "diff" in cmd and "--quiet" in cmd:
                    return subprocess.CompletedProcess(cmd, 1)
                return subprocess.CompletedProcess(cmd, 0)

            with patch(
                    "ai_workflows.core.dynamic_access_iterative_strategy.subprocess.run",
                    side_effect=fake_run,
            ):
                head_sha = strategy._commit_library_metadata("Native metadata for org.example.Demo")

        self.assertEqual(head_sha, "abc123")
        self.assertEqual(calls[0][:3], ["git", "add", "-A"])
        self.assertEqual(calls[0][3], metadata_dir)
        # Diff and commit must both be scoped to the metadata dir to avoid
        # picking up unrelated staged files in the index.
        self.assertEqual(calls[1][:5], ["git", "diff", "--cached", "--quiet", "--"])
        self.assertEqual(calls[1][5], metadata_dir)
        commit_call = calls[2]
        self.assertEqual(commit_call[:3], ["git", "commit", "-m"])
        self.assertEqual(commit_call[-2:], ["--", metadata_dir])

    def test_commit_library_metadata_skips_commit_when_no_staged_changes(self) -> None:
        with tempfile.TemporaryDirectory() as repo:
            os.makedirs(os.path.join(repo, "metadata", "org.example", "lib", "1.0.0"))
            strategy = self._strategy(
                library="org.example:lib:1.0.0",
                reachability_repo_path=repo,
            )
            calls: list[list[str]] = []

            def fake_run(cmd, **kwargs):  # type: ignore[no-untyped-def]
                calls.append(list(cmd))
                # `check_output` for `git rev-parse HEAD` delegates to `subprocess.run`.
                if "rev-parse" in cmd:
                    return subprocess.CompletedProcess(cmd, 0, stdout="abc123\n")
                # rc=0 from `git diff --cached --quiet` means nothing to commit.
                return subprocess.CompletedProcess(cmd, 0)

            with patch(
                    "ai_workflows.core.dynamic_access_iterative_strategy.subprocess.run",
                    side_effect=fake_run,
            ):
                strategy._commit_library_metadata("Native metadata for org.example.Demo")

        self.assertEqual([cmd[1] for cmd in calls], ["add", "diff", "rev-parse"])
        self.assertFalse(any("commit" in cmd for cmd in calls))

    def test_report_path_uses_indexed_test_version_for_reused_test_project(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            metadata_dir = os.path.join(tmpdir, "metadata", "org.example", "lib")
            os.makedirs(metadata_dir)
            with open(os.path.join(metadata_dir, "index.json"), "w", encoding="utf-8") as index_file:
                json.dump(
                    [
                        {
                            "metadata-version": "1.0.0",
                            "tested-versions": ["1.0.0", "1.0.1"],
                        }
                    ],
                    index_file,
                )

            strategy = self._strategy(
                library="org.example:lib:1.0.1",
                reachability_repo_path=tmpdir,
            )

        self.assertIn(
            os.path.join("tests", "src", "org.example", "lib", "1.0.0", "build", "reports"),
            strategy.dynamic_access_report_path,
        )

    def test_issue_requested_metadata_phase_commits_when_native_test_is_reached(self) -> None:
        class FakeAgent:
            def __init__(self) -> None:
                self.prompts: list[str] = []
                self.cleared = False

            def send_prompt(self, prompt: str) -> None:
                self.prompts.append(prompt)

            def run_test_command(self, command: str) -> str:
                return "> Task :nativeTest FAILED"

            def clear_context(self) -> None:
                self.cleared = True

        strategy = self._strategy(
            issue_requested_metadata_context="Reporter-provided missing metadata context:\nmissing resource",
        )
        strategy.prompts["issue-requested-metadata"] = "unused"
        agent = FakeAgent()

        with patch.object(strategy, "_render_prompt", return_value="prompt"), \
                patch.object(strategy, "_commit_test_sources") as commit_tests, \
                patch(
                    "ai_workflows.core.dynamic_access_iterative_strategy.subprocess.check_output",
                    return_value="checkpoint\n",
                ):
            phase_ok, iterations = strategy._run_issue_requested_metadata_phase(agent)

        self.assertTrue(phase_ok)
        self.assertEqual(iterations, 1)
        self.assertEqual(agent.prompts, ["prompt"])
        self.assertTrue(agent.cleared)
        commit_tests.assert_called_once_with("Issue-requested metadata coverage for org.example:lib:1.0.0")

    def test_native_test_gate_flushes_leftover_classes_at_end(self) -> None:
        class FakeAgent:
            def send_prompt(self, prompt: str) -> None:
                pass

            def run_test_command(self, command: str) -> str:
                return "BUILD SUCCESSFUL"

            def clear_context(self) -> None:
                pass

        class_names = ["org.example.A", "org.example.B", "org.example.C", "org.example.D"]
        reports = [
            self._report_for_class_names(class_names, ["org.example.A"]),
            self._report_for_class_names(class_names, ["org.example.A", "org.example.B"]),
            self._report_for_class_names(class_names, ["org.example.A", "org.example.B", "org.example.C"]),
            self._report_for_class_names(class_names, class_names),
            self._report_for_class_names(class_names, class_names),
        ]
        strategy = self._strategy()
        gate_calls: list[str] = []

        def _gate(class_name: str) -> bool:
            gate_calls.append(class_name)
            return True

        with patch.object(strategy, "_render_prompt", return_value="prompt"), \
                patch.object(strategy, "_generate_dynamic_access_report", side_effect=reports), \
                patch.object(strategy, "_commit_test_sources"), \
                patch.object(strategy, "_run_native_test_verification_gate", side_effect=_gate), \
                patch.object(strategy, "_library_test_change_signature", return_value="clean"), \
                patch(
                    "ai_workflows.core.dynamic_access_iterative_strategy.subprocess.check_output",
                    return_value="checkpoint\n",
                ):
            phase_ok, iterations = strategy._run_dynamic_access_phase(
                FakeAgent(),
                self._report_for_class_names(class_names, []),
            )

        self.assertTrue(phase_ok)
        self.assertEqual(iterations, 4)
        self.assertEqual(gate_calls, ["org.example.D__native_batch_4"])

    def test_native_test_gate_uses_configured_batch_size(self) -> None:
        class FakeAgent:
            def send_prompt(self, prompt: str) -> None:
                pass

            def run_test_command(self, command: str) -> str:
                return "BUILD SUCCESSFUL"

            def clear_context(self) -> None:
                pass

        class_names = ["org.example.A", "org.example.B", "org.example.C"]
        reports = [
            self._report_for_class_names(class_names, ["org.example.A"]),
            self._report_for_class_names(class_names, ["org.example.A", "org.example.B"]),
            self._report_for_class_names(class_names, ["org.example.A", "org.example.B"]),
            self._report_for_class_names(class_names, class_names),
            self._report_for_class_names(class_names, class_names),
        ]
        strategy = self._strategy(parameters={"native-test-verification-batch-size": 2})
        gate_calls: list[str] = []

        def _gate(class_name: str) -> bool:
            gate_calls.append(class_name)
            return True

        with patch.object(strategy, "_render_prompt", return_value="prompt"), \
                patch.object(strategy, "_generate_dynamic_access_report", side_effect=reports), \
                patch.object(strategy, "_commit_test_sources"), \
                patch.object(strategy, "_run_native_test_verification_gate", side_effect=_gate), \
                patch.object(strategy, "_library_test_change_signature", return_value="clean"), \
                patch(
                    "ai_workflows.core.dynamic_access_iterative_strategy.subprocess.check_output",
                    return_value="checkpoint\n",
                ):
            phase_ok, iterations = strategy._run_dynamic_access_phase(
                FakeAgent(),
                self._report_for_class_names(class_names, []),
            )

        self.assertTrue(phase_ok)
        self.assertEqual(iterations, 3)
        self.assertEqual(gate_calls, ["org.example.B__native_batch_2", "org.example.C"])

    def test_partially_covered_exhausted_class_is_persisted_for_continuation(self) -> None:
        class FakeAgent:
            def send_prompt(self, prompt: str) -> None:
                pass

            def run_test_command(self, command: str) -> str:
                return "BUILD SUCCESSFUL"

            def clear_context(self) -> None:
                pass

        initial_report = DynamicAccessCoverageReport(
            coordinate="org.example:lib:1.0.0",
            has_dynamic_access=True,
            total_calls=2,
            covered_calls=0,
            classes=[self._class_coverage("org.example.Partial", 2, 0)],
        )
        updated_report = DynamicAccessCoverageReport(
            coordinate="org.example:lib:1.0.0",
            has_dynamic_access=True,
            total_calls=2,
            covered_calls=1,
            classes=[self._class_coverage("org.example.Partial", 2, 1)],
        )

        with tempfile.TemporaryDirectory() as tmpdir:
            report_path = os.path.join(tmpdir, "dynamic-access-exhaust-report.json")
            strategy = self._strategy(
                dynamic_access_exhaust_report=DynamicAccessExhaustReport.create(
                    coordinate="org.example:lib:1.0.0",
                    issue_number=1412,
                ),
                dynamic_access_exhaust_report_path=report_path,
                chunk_class_count=5,
            )

            with patch.object(strategy, "_render_prompt", return_value="prompt"), \
                    patch.object(strategy, "_generate_dynamic_access_report", return_value=updated_report), \
                    patch.object(strategy, "_commit_test_sources"), \
                    patch.object(strategy, "_run_native_test_verification_gate", return_value=True), \
                    patch.object(strategy, "_refresh_report_after_gate", return_value=None), \
                    patch.object(strategy, "_library_test_change_signature", return_value="clean"), \
                    patch(
                        "ai_workflows.core.dynamic_access_iterative_strategy.subprocess.check_output",
                        return_value="checkpoint\n",
                    ):
                phase_ok, iterations = strategy._run_dynamic_access_phase(FakeAgent(), initial_report)

            saved_report = DynamicAccessExhaustReport.load(report_path)

        self.assertTrue(phase_ok)
        self.assertEqual(iterations, 1)
        self.assertEqual(saved_report.exhausted_classes, ["org.example.Partial"])

    def test_chunk_boundary_counts_indirectly_completed_classes(self) -> None:
        class FakeAgent:
            def send_prompt(self, prompt: str) -> None:
                pass

            def run_test_command(self, command: str) -> str:
                return "BUILD SUCCESSFUL"

            def clear_context(self) -> None:
                pass

        class_names = ["org.example.A", "org.example.B", "org.example.C"]
        initial_report = self._report_for_class_names(class_names, [])
        updated_report = self._report_for_class_names(class_names, ["org.example.A", "org.example.B"])

        with tempfile.TemporaryDirectory() as tmpdir:
            report_path = os.path.join(tmpdir, "dynamic-access-exhaust-report.json")
            strategy = self._strategy(
                dynamic_access_exhaust_report=DynamicAccessExhaustReport.create(
                    coordinate="org.example:lib:1.0.0",
                    issue_number=1412,
                ),
                dynamic_access_exhaust_report_path=report_path,
                chunk_class_count=2,
            )

            with patch.object(strategy, "_render_prompt", return_value="prompt"), \
                    patch.object(strategy, "_generate_dynamic_access_report", return_value=updated_report), \
                    patch.object(strategy, "_commit_test_sources"), \
                    patch.object(strategy, "_run_native_test_verification_gate", return_value=True), \
                    patch.object(strategy, "_refresh_report_after_gate", return_value=updated_report), \
                    patch.object(strategy, "_library_test_change_signature", return_value="clean"), \
                    patch(
                        "ai_workflows.core.dynamic_access_iterative_strategy.subprocess.check_output",
                        return_value="checkpoint\n",
                    ):
                phase_ok, iterations = strategy._run_dynamic_access_phase(FakeAgent(), initial_report)

            saved_report = DynamicAccessExhaustReport.load(report_path)

        self.assertTrue(phase_ok)
        self.assertEqual(iterations, 1)
        self.assertEqual(strategy._last_phase_status, RUN_STATUS_CHUNK_READY)
        self.assertEqual(set(saved_report.completed_classes), {"org.example.A", "org.example.B"})
        self.assertNotIn("org.example.C", saved_report.completed_classes)

    def test_should_stop_for_chunked_dynamic_access_returns_false_without_report(self) -> None:
        strategy = self._strategy()
        report = DynamicAccessCoverageReport(
            coordinate="org.example:lib:1.0.0",
            has_dynamic_access=True,
            total_calls=10,
            covered_calls=5,
            classes=[self._class_coverage("org.example.A", 5, 0)],
        )

        self.assertFalse(
            strategy._should_stop_for_chunked_dynamic_access(report, set(), {"org.example.A"}),
        )

    def test_should_stop_for_chunked_dynamic_access_skips_when_no_uncovered_class_remains(self) -> None:
        strategy = self._strategy(
            dynamic_access_exhaust_report=DynamicAccessExhaustReport.create(
                coordinate="org.example:lib:1.0.0",
                issue_number=1412,
            ),
            chunk_class_count=1,
        )
        report = DynamicAccessCoverageReport(
            coordinate="org.example:lib:1.0.0",
            has_dynamic_access=True,
            total_calls=4,
            covered_calls=4,
            classes=[self._class_coverage("org.example.A", 2, 2)],
        )

        self.assertFalse(
            strategy._should_stop_for_chunked_dynamic_access(report, {"org.example.A"}, {"org.example.A"}),
        )

    def test_should_stop_for_chunked_dynamic_access_honors_class_count(self) -> None:
        strategy = self._strategy(
            dynamic_access_exhaust_report=DynamicAccessExhaustReport.create(
                coordinate="org.example:lib:1.0.0",
                issue_number=1412,
            ),
            chunk_class_count=2,
        )
        report = DynamicAccessCoverageReport(
            coordinate="org.example:lib:1.0.0",
            has_dynamic_access=True,
            total_calls=10,
            covered_calls=5,
            classes=[self._class_coverage("org.example.Pending", 5, 0)],
        )

        self.assertFalse(
            strategy._should_stop_for_chunked_dynamic_access(report, set(), {"org.example.A"}),
        )
        self.assertTrue(
            strategy._should_stop_for_chunked_dynamic_access(report, set(), {"org.example.A", "org.example.B"}),
        )

    def test_increase_coverage_strategy_propagates_chunk_ready_from_dynamic_access_phase(self) -> None:
        class ChunkReadyDynamicAccess:
            def __init__(self, strategy_obj: dict, **context) -> None:
                self._last_phase_status = RUN_STATUS_CHUNK_READY

            def has_issue_requested_metadata_context(self) -> bool:
                return False

            def _run_dynamic_access_phase(self, agent) -> tuple[bool, int]:
                return True, 3

        strategy = IncreaseDynamicAccessCoverageStrategy(
            {
                "model": "test-model",
                "parameters": {},
                "prompts": {},
            },
            reachability_repo_path="/tmp/reachability",
            library="org.example:lib:1.0.0",
        )

        with patch(
                "ai_workflows.core.increase_dynamic_access_coverage_strategy.DynamicAccessIterativeStrategy",
                ChunkReadyDynamicAccess,
        ):
            self.assertEqual(strategy.run(agent=object()), (RUN_STATUS_CHUNK_READY, 3))

    def test_increase_coverage_strategy_runs_issue_requested_phase_after_dynamic_access(self) -> None:
        calls: list[str] = []

        class ReporterRequestedDynamicAccess:
            def __init__(self, strategy_obj: dict, **context) -> None:
                self._last_phase_status = RUN_STATUS_SUCCESS

            def has_issue_requested_metadata_context(self) -> bool:
                return True

            def _run_issue_requested_metadata_phase(self, agent) -> tuple[bool, int]:
                calls.append("issue-requested")
                return True, 1

            def _run_dynamic_access_phase(self, agent) -> tuple[bool, int]:
                calls.append("dynamic-access")
                return False, 0

        strategy = IncreaseDynamicAccessCoverageStrategy(
            {
                "model": "test-model",
                "parameters": {},
                "prompts": {},
            },
            reachability_repo_path="/tmp/reachability",
            library="org.example:lib:1.0.0",
            issue_requested_metadata_context="Reporter-provided missing metadata context:\nmissing resource",
        )

        with patch(
                "ai_workflows.core.increase_dynamic_access_coverage_strategy.DynamicAccessIterativeStrategy",
                ReporterRequestedDynamicAccess,
        ):
            self.assertEqual(strategy.run(agent=object()), (RUN_STATUS_SUCCESS, 1))

        self.assertEqual(calls, ["dynamic-access", "issue-requested"])

    def test_increase_coverage_strategy_fails_when_no_primary_dynamic_access_or_issue_work_succeeds(self) -> None:
        class NoProgressDynamicAccess:
            def __init__(self, strategy_obj: dict, **context) -> None:
                self._last_phase_status = RUN_STATUS_SUCCESS

            def has_issue_requested_metadata_context(self) -> bool:
                return False

            def _run_dynamic_access_phase(self, agent) -> tuple[bool, int]:
                return False, 0

        strategy = IncreaseDynamicAccessCoverageStrategy(
            {
                "model": "test-model",
                "parameters": {},
                "prompts": {},
            },
            reachability_repo_path="/tmp/reachability",
            library="org.example:lib:1.0.0",
        )

        with patch(
                "ai_workflows.core.increase_dynamic_access_coverage_strategy.DynamicAccessIterativeStrategy",
                NoProgressDynamicAccess,
        ):
            self.assertEqual(strategy.run(agent=object()), (RUN_STATUS_FAILURE, 0))

    @staticmethod
    def _class_coverage(class_name: str, total_calls: int, covered_calls: int) -> DynamicAccessClass:
        return DynamicAccessClass(
            class_name=class_name,
            source_file=None,
            resolved_source_file=None,
            total_calls=total_calls,
            covered_calls=covered_calls,
            call_sites=[],
        )

    @classmethod
    def _report_for_class_names(
            cls,
            class_names: list[str],
            covered_class_names: list[str],
    ) -> DynamicAccessCoverageReport:
        covered = set(covered_class_names)
        return DynamicAccessCoverageReport(
            coordinate="org.example:lib:1.0.0",
            has_dynamic_access=True,
            total_calls=len(class_names),
            covered_calls=len(covered),
            classes=[
                cls._class_coverage(class_name, 1, 1 if class_name in covered else 0)
                for class_name in class_names
            ],
        )

    @staticmethod
    def _strategy(**context) -> DynamicAccessIterativeStrategy:
        library = context.pop("library", "org.example:lib:1.0.0")
        reachability_repo_path = context.pop("reachability_repo_path", "/tmp/reachability")
        parameters = {
            "max-iterations": 1,
            "max-class-test-iterations": 1,
        }
        parameters.update(context.pop("parameters", {}))
        return DynamicAccessIterativeStrategy(
            {
                "model": "test-model",
                "prompts": {"dynamic-access-iteration": "unused"},
                "parameters": parameters,
            },
            library=library,
            reachability_repo_path=reachability_repo_path,
            **context,
        )


if __name__ == "__main__":
    unittest.main()
