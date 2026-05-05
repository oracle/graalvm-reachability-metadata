# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os
import subprocess
import tempfile
from contextlib import ExitStack
from types import SimpleNamespace
import unittest
from unittest.mock import patch

from ai_workflows.java_fail_workflow import (
    JAVAC_CONFIG,
    copy_and_prepare_project_dir,
    create_project_prep_checkpoint,
    reset_failed_java_fix_worktree,
    run_java_fail_workflow,
)
from ai_workflows.workflow_strategies.workflow_strategy import RUN_STATUS_FAILURE


class JavaFailWorkflowProjectPrepTests(unittest.TestCase):
    def test_copy_and_prepare_project_dir_skips_same_source_and_destination(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            test_project_dir = os.path.join(
                temp_dir,
                "tests",
                "src",
                "org.example",
                "demo",
                "1.0.0",
            )
            os.makedirs(test_project_dir)
            gradle_properties_path = os.path.join(test_project_dir, "gradle.properties")
            with open(gradle_properties_path, "w", encoding="utf-8") as file:
                file.write("version=1.0.0\n")

            previous_cwd = os.getcwd()
            try:
                os.chdir(temp_dir)
                with patch("ai_workflows.java_fail_workflow.shutil.copytree") as copytree:
                    copy_and_prepare_project_dir("org.example", "demo", "1.0.0", "1.0.0")
            finally:
                os.chdir(previous_cwd)

            copytree.assert_not_called()
            with open(gradle_properties_path, "r", encoding="utf-8") as file:
                self.assertEqual(file.read(), "version=1.0.0\n")

    def test_create_project_prep_checkpoint_allows_no_staged_changes(self) -> None:
        with patch("ai_workflows.java_fail_workflow.build_ai_branch_name", return_value="ai/test-branch"), \
                patch("ai_workflows.java_fail_workflow.delete_remote_branch_if_exists") as delete_remote, \
                patch("ai_workflows.java_fail_workflow.subprocess.run") as run, \
                patch(
                    "ai_workflows.java_fail_workflow.subprocess.check_output",
                    return_value="abc123\n",
                ) as check_output:
            run.side_effect = [
                subprocess.CompletedProcess(["git", "switch"], 0),
                subprocess.CompletedProcess(["git", "add"], 0),
                subprocess.CompletedProcess(["git", "diff"], 0),
            ]

            checkpoint = create_project_prep_checkpoint(JAVAC_CONFIG, "org.example", "demo", "1.0.0")

        self.assertEqual(checkpoint, "abc123")
        delete_remote.assert_called_once_with("ai/test-branch")
        self.assertEqual(run.call_count, 3)
        self.assertEqual(run.call_args_list[2].args[0], ["git", "diff", "--cached", "--quiet"])
        check_output.assert_called_once_with(["git", "rev-parse", "HEAD"], text=True)

    def test_failed_workflow_reset_preserves_last_passing_candidate(self) -> None:
        with patch("ai_workflows.java_fail_workflow.subprocess.run") as run, \
                patch(
                    "ai_workflows.java_fail_workflow.subprocess.check_output",
                    return_value="candidate\n",
                ) as check_output, \
                patch("ai_workflows.java_fail_workflow.shutil.rmtree") as rmtree:
            ending_commit = reset_failed_java_fix_worktree(
                reachability_repo_path="/repo",
                commit_checkpoint="prep",
                last_passing_candidate_commit="candidate",
                tests_dir="/repo/tests/src/org.example/demo/2.0.0",
                metadata_dir="/repo/metadata/org.example/demo/2.0.0",
                tests_dir_preexisted=False,
                metadata_dir_preexisted=False,
            )

        self.assertEqual(ending_commit, "candidate")
        run.assert_called_once_with(["git", "reset", "--hard", "candidate"], cwd="/repo", check=True)
        check_output.assert_called_once_with(["git", "rev-parse", "HEAD"], cwd="/repo", text=True)
        rmtree.assert_not_called()

    def test_failed_workflow_reset_cleans_scaffold_when_no_candidate_passed(self) -> None:
        with patch("ai_workflows.java_fail_workflow.subprocess.run") as run, \
                patch(
                    "ai_workflows.java_fail_workflow.subprocess.check_output",
                    return_value="prep\n",
                ), \
                patch("ai_workflows.java_fail_workflow.shutil.rmtree") as rmtree:
            ending_commit = reset_failed_java_fix_worktree(
                reachability_repo_path="/repo",
                commit_checkpoint="prep",
                last_passing_candidate_commit=None,
                tests_dir="/repo/tests/src/org.example/demo/2.0.0",
                metadata_dir="/repo/metadata/org.example/demo/2.0.0",
                tests_dir_preexisted=False,
                metadata_dir_preexisted=False,
            )

        self.assertEqual(ending_commit, "prep")
        run.assert_called_once_with(["git", "reset", "--hard", "prep"], cwd="/repo", check=True)
        rmtree.assert_any_call("/repo/tests/src/org.example/demo/2.0.0", ignore_errors=True)
        rmtree.assert_any_call("/repo/metadata/org.example/demo/2.0.0", ignore_errors=True)

    def test_failed_workflow_preserves_generated_worktree_for_follow_up(self) -> None:
        class FailingStrategy:
            post_generation_intervention = None
            persistent_instructions = None

            def __init__(self, **_kwargs):
                pass

            def run(self, agent):
                del agent
                return RUN_STATUS_FAILURE, 2

        class Agent:
            total_tokens_sent = 0
            total_tokens_received = 0
            cached_input_tokens_used = 0

        with tempfile.TemporaryDirectory() as temp_dir:
            source_root = os.path.join(
                temp_dir,
                "tests",
                "src",
                "org.example",
                "demo",
                "2.0.0",
                "src",
                "test",
                "java",
            )
            os.makedirs(source_root)
            parsed_flags = (
                "org.example",
                "demo",
                "1.0.0",
                "2.0.0",
                None,
                "test-strategy",
                False,
                temp_dir,
                temp_dir,
            )
            source_context = SimpleNamespace(
                to_prompt_overview=lambda: "",
                is_available=False,
                read_only_files=[],
            )
            test_layout = SimpleNamespace(
                language="java",
                display_language="Java",
                source_dir_name="java",
                source_root=source_root,
            )
            previous_cwd = os.getcwd()
            try:
                with ExitStack() as stack:
                    stack.enter_context(
                        patch("ai_workflows.java_fail_workflow.parse_flags", return_value=parsed_flags)
                    )
                    stack.enter_context(
                        patch(
                            "ai_workflows.java_fail_workflow.require_strategy_by_name",
                            return_value={"workflow": "dummy", "parameters": {}, "model": "model"},
                        )
                    )
                    stack.enter_context(
                        patch(
                            "ai_workflows.java_fail_workflow.resolve_repo_roots",
                            return_value=(temp_dir, temp_dir),
                        )
                    )
                    stack.enter_context(patch("ai_workflows.java_fail_workflow.ensure_gh_authenticated"))
                    stack.enter_context(
                        patch("ai_workflows.java_fail_workflow.resolve_graalvm_java_home", return_value="/graalvm")
                    )
                    stack.enter_context(
                        patch("ai_workflows.java_fail_workflow.build_graalvm_environment", return_value={})
                    )
                    stack.enter_context(patch("ai_workflows.java_fail_workflow.validate_repo_paths"))
                    stack.enter_context(patch("ai_workflows.java_fail_workflow.copy_and_prepare_project_dir"))
                    stack.enter_context(patch("ai_workflows.java_fail_workflow.update_metadata_index_json"))
                    stack.enter_context(patch("ai_workflows.java_fail_workflow.create_versioned_metadata_dir"))
                    stack.enter_context(
                        patch(
                            "ai_workflows.java_fail_workflow.create_project_prep_checkpoint",
                            return_value="checkpoint",
                        )
                    )
                    stack.enter_context(patch("ai_workflows.java_fail_workflow.populate_artifact_urls"))
                    stack.enter_context(
                        patch("ai_workflows.java_fail_workflow.prepare_source_contexts", return_value=source_context)
                    )
                    stack.enter_context(
                        patch("ai_workflows.java_fail_workflow.resolve_test_source_layout", return_value=test_layout)
                    )
                    stack.enter_context(
                        patch(
                            "ai_workflows.java_fail_workflow.WorkflowStrategy.get_class",
                            return_value=FailingStrategy,
                        )
                    )
                    stack.enter_context(patch("ai_workflows.java_fail_workflow.init_agent", return_value=Agent()))
                    stack.enter_context(
                        patch(
                            "ai_workflows.java_fail_workflow.subprocess.check_output",
                            return_value="candidate\n",
                        )
                    )
                    create_metrics = stack.enter_context(
                        patch(
                            "ai_workflows.java_fail_workflow.metrics_writer.create_javac_fix_run_metrics_output_json",
                            return_value={"status": "failure"},
                        )
                    )
                    fallback_metrics = stack.enter_context(
                        patch("ai_workflows.java_fail_workflow.create_failure_run_metrics_output")
                    )
                    write_metrics = stack.enter_context(patch("ai_workflows.java_fail_workflow.write_fix_metrics"))
                    run = stack.enter_context(patch("ai_workflows.java_fail_workflow.subprocess.run"))
                    result = run_java_fail_workflow(JAVAC_CONFIG, [])
            finally:
                os.chdir(previous_cwd)

        self.assertEqual(result, 1)
        reset_calls = [
            call for call in run.call_args_list
            if call.args and call.args[0] == ["git", "reset", "--hard", "checkpoint"]
        ]
        self.assertEqual(reset_calls, [])
        create_metrics.assert_called_once()
        self.assertEqual(create_metrics.call_args.kwargs["ending_commit"], "candidate")
        fallback_metrics.assert_not_called()
        write_metrics.assert_called_once()
