# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Coverage benchmark run lifecycle tests: source worktrees, publication, and retries.

§FS-code-coverage-benchmarking
"""

import subprocess
import tempfile
import unittest
from types import SimpleNamespace
from unittest.mock import patch
from pathlib import Path

from benchmarks import code_coverage_benchmark as benchmark
from benchmarks import code_coverage_benchmark_common as benchmark_common
from code_coverage_benchmark_test_support import _git, _write_json


def _write_harness(repo: Path, marker: str) -> None:
    """Give a fixture repository every path the source worktree overlays."""
    for harness_path in benchmark_common.HARNESS_PATHS:
        target: Path = repo / harness_path
        if "." not in harness_path and not harness_path.startswith("gradlew"):
            target = target / "harness.txt"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(f"{marker}\n", encoding="utf-8")


def _commit_all(repo: Path, message: str) -> str:
    _git(repo, "add", "-A")
    _git(
        repo,
        "-c",
        "user.name=test",
        "-c",
        "user.email=test@example.com",
        "commit",
        "-m",
        message,
    )
    return _git(repo, "rev-parse", "HEAD").stdout.strip()


class CodeCoverageBenchmarkLifecycleTests(unittest.TestCase):

    def setUp(self) -> None:
        suite = benchmark.load_suite()
        self.suite = suite
        self.cell = benchmark.expand_matrix(
            suite,
            library_indexes=[1],
            agents=["pi"],
            models=["gpt-5.6-luna"],
            thinking_levels=["high"],
        )[0]

    def test_replaces_existing_source_worktree(self) -> None:
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)
        repository = root / "repository"
        source = root / "run" / "source"
        repository.mkdir()
        _git(repository, "init", "-b", "master")
        (repository / "README.md").write_text("seed\n", encoding="utf-8")
        _write_harness(repository, "seed")
        commit = _commit_all(repository, "seed")

        benchmark.create_source_worktree(source, commit, commit, repository)
        (source / "stale.txt").write_text("stale\n", encoding="utf-8")

        benchmark.create_source_worktree(source, commit, commit, repository)

        self.assertFalse((source / "stale.txt").exists())
        self.assertEqual(commit, _git(source, "rev-parse", "HEAD").stdout.strip())
        benchmark.remove_worktree(source, repository)

    def test_source_worktree_measures_the_pin_with_the_runner_harness(self) -> None:
        """§FS-code-coverage-benchmarking.1"""
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)
        repository = root / "repository"
        source = root / "run" / "source"
        test_file = Path("tests/src/com.example/demo/1.0.0/DemoTest.java")
        metadata_file = Path("metadata/com.example/demo/1.0.0/reachability-metadata.json")
        retired_harness = Path("tests/tck-build-logic/retired.gradle")
        added_harness = Path("tests/tck-build-logic/added.gradle")
        repository.mkdir()
        _git(repository, "init", "-b", "master")
        _write_harness(repository, "pin")
        for path in (test_file, metadata_file, retired_harness):
            (repository / path).parent.mkdir(parents=True, exist_ok=True)
            (repository / path).write_text("pin\n", encoding="utf-8")
        suite_commit = _commit_all(repository, "pin")
        _write_harness(repository, "runner")
        for path in (test_file, metadata_file, added_harness):
            (repository / path).write_text("runner\n", encoding="utf-8")
        (repository / retired_harness).unlink()
        runner_commit = _commit_all(repository, "runner")

        benchmark.create_source_worktree(source, suite_commit, runner_commit, repository)
        self.addCleanup(benchmark.remove_worktree, source, repository)

        self.assertEqual("pin\n", (source / test_file).read_text(encoding="utf-8"))
        self.assertEqual("pin\n", (source / metadata_file).read_text(encoding="utf-8"))
        self.assertEqual("runner\n", (source / "ci.json").read_text(encoding="utf-8"))
        self.assertEqual("runner\n", (source / added_harness).read_text(encoding="utf-8"))
        self.assertFalse((source / retired_harness).exists())
        self.assertEqual(suite_commit, _git(source, "rev-parse", "HEAD").stdout.strip())

    def test_removes_source_only_after_publication_marker(self) -> None:
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)

        def execute(command: list[str], **_: object) -> subprocess.CompletedProcess:
            workspace = Path(command[command.index("--output") + 1])
            _write_json(
                workspace / benchmark.PUBLICATION_MARKER,
                {"schemaVersion": "1.0.0"},
            )
            return subprocess.CompletedProcess(command, 0)

        with patch.object(
                benchmark,
                "_new_run_id",
                return_value="run-success",
        ), patch.object(
                benchmark,
                "create_source_worktree",
        ), patch.object(
                benchmark.subprocess,
                "run",
                side_effect=execute,
        ), patch.object(
                benchmark,
                "publish_workspace",
        ) as publish, patch.object(
                benchmark_common,
                "remove_worktree",
        ) as remove:
            outcome = benchmark._execute_cell(
                self.suite,
                self.cell,
                "a" * 40,
                root,
            )

        self.assertEqual((True, True), outcome)
        publish.assert_not_called()
        remove.assert_called_once_with(root / "run-success" / "source")

    def test_source_cleanup_failure_does_not_raise(self) -> None:
        error = subprocess.CalledProcessError(1, ["git", "worktree", "remove"])

        with patch.object(
                benchmark_common,
                "remove_worktree",
                side_effect=error,
        ), patch("builtins.print") as output:
            benchmark.discard_source_worktree(Path("/tmp/source"))

        output.assert_called_once()

    def test_skips_cell_when_source_worktree_creation_fails(self) -> None:
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)
        error = subprocess.CalledProcessError(1, ["git", "worktree", "add"])

        with patch.object(
                benchmark,
                "_new_run_id",
                return_value="run-skipped",
        ), patch.object(
                benchmark,
                "create_source_worktree",
                side_effect=error,
        ), patch.object(
                benchmark.subprocess,
                "run",
        ) as execute, patch.object(
                benchmark,
                "publish_workspace",
        ) as publish, patch("builtins.print"):
            outcome = benchmark._execute_cell(
                self.suite,
                self.cell,
                "a" * 40,
                root,
            )

        self.assertEqual((False, False), outcome)
        execute.assert_not_called()
        publish.assert_not_called()

    def test_holds_stopped_run_for_human_intervention(self) -> None:
        """A stop before terminal publication publishes nothing: the launcher
        retains the workspace and source and reports the run as pending."""
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)

        with patch.object(
                benchmark,
                "_new_run_id",
                return_value="run-failure",
        ), patch.object(
                benchmark,
                "create_source_worktree",
        ), patch.object(
                benchmark.subprocess,
                "run",
                return_value=subprocess.CompletedProcess([], 1),
        ), patch.object(
                benchmark,
                "publish_workspace",
        ) as publish, patch.object(
                benchmark_common,
                "remove_worktree",
        ) as remove, patch("builtins.print") as output:
            outcome = benchmark._execute_cell(
                self.suite,
                self.cell,
                "a" * 40,
                root,
            )

        self.assertEqual((False, False), outcome)
        publish.assert_not_called()
        remove.assert_not_called()
        messages = [str(call.args[0]) for call in output.call_args_list if call.args]
        self.assertTrue(
            any("PENDING HUMAN INTERVENTION" in message for message in messages)
        )
        workspace = root / "run-failure" / "code-coverage-99000"
        run = benchmark.read_json(workspace / benchmark.RUN_RECORD)
        self.assertEqual("failure", run["requestedStatus"])

    def test_retries_publication_when_record_exists_without_marker(self) -> None:
        """A workflow that wrote its record but could not push it retries Git
        publication of that exact record instead of being held."""
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)

        def execute(command: list[str], **_: object) -> subprocess.CompletedProcess:
            workspace = Path(command[command.index("--output") + 1])
            _write_json(workspace / benchmark.RESULT_RECORD, {"status": "success"})
            return subprocess.CompletedProcess(command, 1)

        def publish(workspace: Path, **_: object) -> dict[str, object]:
            _write_json(
                workspace / benchmark.PUBLICATION_MARKER,
                {"schemaVersion": "1.0.0"},
            )
            return {"status": "success"}

        with patch.object(
                benchmark,
                "_new_run_id",
                return_value="run-retry",
        ), patch.object(
                benchmark,
                "create_source_worktree",
        ), patch.object(
                benchmark.subprocess,
                "run",
                side_effect=execute,
        ), patch.object(
                benchmark,
                "publish_workspace",
                side_effect=publish,
        ) as republish, patch.object(
                benchmark_common,
                "remove_worktree",
        ) as remove, patch("builtins.print"):
            outcome = benchmark._execute_cell(
                self.suite,
                self.cell,
                "a" * 40,
                root,
            )

        self.assertEqual((True, True), outcome)
        republish.assert_called_once()
        remove.assert_called_once_with(root / "run-retry" / "source")

    def test_retry_pending_republishes_records_and_lists_held_workspaces(self) -> None:
        """Only a workspace with a written record retries publication; one
        without a record is listed as pending human intervention."""
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)
        held = root / "run-held" / "code-coverage-99000"
        publishable = root / "run-pub" / "code-coverage-99000"
        for run_id, workspace in (("run-held", held), ("run-pub", publishable)):
            _write_json(
                workspace / benchmark.RUN_RECORD,
                {
                    "runId": run_id,
                    "sourceWorktree": str(workspace.parent / "source"),
                },
            )
        _write_json(publishable / benchmark.RESULT_RECORD, {"status": "failure"})

        with patch.object(
                benchmark,
                "publish_workspace",
        ) as publish, patch("builtins.print") as output:
            code = benchmark.retry_pending(SimpleNamespace(workspace_root=root))

        self.assertEqual(0, code)
        publish.assert_called_once_with(publishable)
        messages = [str(call.args[0]) for call in output.call_args_list if call.args]
        self.assertTrue(
            any("PENDING HUMAN INTERVENTION" in message for message in messages)
        )


if __name__ == "__main__":
    unittest.main()
