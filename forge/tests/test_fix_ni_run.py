# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import subprocess
import unittest
from contextlib import ExitStack
from unittest.mock import Mock, patch

from ai_workflows.core.workflow_strategy import RUN_STATUS_SUCCESS
from ai_workflows.drivers import fix_ni_run


class _FakeStrategy:
    def __init__(self, gate_result: bool = True) -> None:
        self.gate_result = gate_result
        self.gate_calls: list[tuple[str, str | None]] = []
        self.run_calls: list[tuple[object, str]] = []
        self.finalize_calls: list[str] = []
        self.post_generation_intervention: dict | None = None

    def verify_native_test_gate(self, output_dir: str, label: str | None = None) -> bool:
        self.gate_calls.append((output_dir, label))
        return self.gate_result

    def run(self, agent: object, checkpoint_commit_hash: str) -> tuple[str, int]:
        self.run_calls.append((agent, checkpoint_commit_hash))
        return RUN_STATUS_SUCCESS, 1

    def finalize_run(self, checkpoint: str) -> str:
        self.finalize_calls.append(checkpoint)
        return RUN_STATUS_SUCCESS


class NativeImageRunDriverTests(unittest.TestCase):
    def _run_driver(
            self,
            seed_returncode: int,
            explore: bool,
            strategy: _FakeStrategy,
    ) -> tuple[int, Mock, Mock, object]:
        agent = object()
        seed_result = subprocess.CompletedProcess(args=["./gradlew"], returncode=seed_returncode)

        with ExitStack() as stack:
            stack.enter_context(patch.object(
                fix_ni_run,
                "resolve_workflow_repo_paths",
                return_value=("/repo", "/metrics", "/metrics-root"),
            ))
            stack.enter_context(patch.object(fix_ni_run, "load_continuation_marker", return_value=None))
            stack.enter_context(patch.object(
                fix_ni_run,
                "prepare_library_preparation_preflight",
                return_value=(None, ""),
            ))
            stack.enter_context(patch.object(fix_ni_run, "save_phase_update"))
            stack.enter_context(patch.object(fix_ni_run, "resolve_graalvm_java_home"))
            stack.enter_context(patch.object(fix_ni_run, "validate_repo_paths"))
            stack.enter_context(patch.object(fix_ni_run.os, "chdir"))
            stack.enter_context(patch.object(fix_ni_run, "build_ai_branch_name", return_value="branch"))
            stack.enter_context(patch.object(fix_ni_run, "create_or_switch_branch"))
            run_seed = stack.enter_context(patch.object(
                fix_ni_run,
                "run_fix_test_native_image_run",
                return_value=seed_result,
            ))
            stack.enter_context(patch.object(fix_ni_run, "resolve_test_version", return_value="2.0"))
            populate_urls = stack.enter_context(patch.object(fix_ni_run, "populate_artifact_urls"))
            stack.enter_context(patch.object(fix_ni_run, "commit_checkpoint", return_value="checkpoint"))
            stack.enter_context(patch.object(
                fix_ni_run,
                "should_explore_new_version",
                return_value=explore,
            ))
            stack.enter_context(patch.object(fix_ni_run, "prepare_library_update_target"))
            stack.enter_context(patch.object(
                fix_ni_run,
                "build_strategy_and_agent",
                return_value=(strategy, agent, "model", "/tests"),
            ))
            stack.enter_context(patch.object(
                fix_ni_run.subprocess,
                "check_output",
                return_value="ending\n",
            ))
            stack.enter_context(patch.object(
                fix_ni_run.metrics_writer,
                "create_java_run_fix_run_metrics_output_json",
                return_value={},
            ))
            stack.enter_context(patch.object(fix_ni_run.metrics_writer, "write_workflow_run_metrics"))

            returncode = fix_ni_run.main([
                "--coordinates", "g:a:1.0",
                "--new-version", "2.0",
            ])

        return returncode, run_seed, populate_urls, agent

    def test_failed_seed_enters_native_gate_then_finalization(self) -> None:
        strategy = _FakeStrategy()

        returncode, run_seed, populate_urls, _agent = self._run_driver(
            seed_returncode=1,
            explore=False,
            strategy=strategy,
        )

        self.assertEqual(returncode, 0)
        run_seed.assert_called_once()
        populate_urls.assert_called_once_with("/repo", "g:a:2.0")
        self.assertEqual(
            strategy.gate_calls,
            [
                (
                    "/repo/tests/src/g/a/2.0/build/natively-collected/_global_",
                    "fixTestNativeImageRun failure",
                ),
            ],
        )
        self.assertEqual(strategy.run_calls, [])
        self.assertEqual(strategy.finalize_calls, ["checkpoint"])

    def test_successful_seed_checks_exploration_then_finalizes(self) -> None:
        strategy = _FakeStrategy()

        returncode, _run_seed, _populate_urls, agent = self._run_driver(
            seed_returncode=0,
            explore=True,
            strategy=strategy,
        )

        self.assertEqual(returncode, 0)
        self.assertEqual(strategy.gate_calls, [])
        self.assertEqual(strategy.run_calls, [(agent, "checkpoint")])
        self.assertEqual(strategy.finalize_calls, ["checkpoint"])

    def test_failed_seed_stops_when_native_gate_fails(self) -> None:
        strategy = _FakeStrategy(gate_result=False)

        returncode, _run_seed, populate_urls, _agent = self._run_driver(
            seed_returncode=1,
            explore=False,
            strategy=strategy,
        )

        self.assertEqual(returncode, 1)
        self.assertEqual(len(strategy.gate_calls), 1)
        populate_urls.assert_not_called()
        self.assertEqual(strategy.run_calls, [])
        self.assertEqual(strategy.finalize_calls, [])


if __name__ == "__main__":
    unittest.main()
