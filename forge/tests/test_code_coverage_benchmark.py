# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Coverage benchmark selection, collection, and publication tests.

§FS-code-coverage-benchmarking
"""

import json
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch
from pathlib import Path

from benchmarks import code_coverage_benchmark as benchmark


FORGE_ROOT = Path(__file__).resolve().parents[1]
FINAL_METRICS = (
    FORGE_ROOT / "tests" / "fixtures" / "code_coverage" / "final_metrics.json"
)


def _write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(value, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )


def _git(repo: Path, *arguments: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", *arguments],
        cwd=repo,
        check=True,
        capture_output=True,
        text=True,
    )


class CodeCoverageBenchmarkMatrixTests(unittest.TestCase):

    def setUp(self) -> None:
        self.suite = benchmark.load_suite()

    def test_default_matrix_has_seventy_five_cells(self) -> None:
        cells = benchmark.expand_matrix(self.suite)

        self.assertEqual(75, len(cells))
        self.assertEqual(
            {"pi", "claude-code"},
            {cell.configuration.agent for cell in cells},
        )
        self.assertEqual(
            {"medium", "high", "xhigh"},
            {cell.thinking for cell in cells},
        )

    def test_filters_compose_as_a_cross_product(self) -> None:
        cells = benchmark.expand_matrix(
            self.suite,
            library_indexes=[1, 3, 5],
            agents=["pi"],
            models=["gpt-5.6-luna", "gpt-5.6-terra"],
            thinking_levels=["high"],
        )

        self.assertEqual(6, len(cells))
        self.assertEqual({1, 3, 5}, {cell.library.index for cell in cells})
        self.assertEqual(
            {"gpt-5.6-luna", "gpt-5.6-terra"},
            {cell.configuration.configured_model for cell in cells},
        )

    def test_model_filter_selects_its_agent_when_agent_is_unspecified(self) -> None:
        cells = benchmark.expand_matrix(
            self.suite,
            models=["sonnet-5"],
            thinking_levels=["xhigh"],
        )

        self.assertEqual(5, len(cells))
        self.assertEqual(
            {"claude-code"},
            {cell.configuration.agent for cell in cells},
        )

    def test_rejects_duplicate_and_incompatible_selections(self) -> None:
        with self.assertRaisesRegex(
                benchmark.BenchmarkError,
                "Duplicate library index",
        ):
            benchmark.expand_matrix(self.suite, library_indexes=[1, 1])

        with self.assertRaisesRegex(
                benchmark.BenchmarkError,
                "incompatible with agent 'claude-code'",
        ):
            benchmark.expand_matrix(
                self.suite,
                agents=["claude-code"],
                models=["gpt-5.6-luna"],
            )

    def test_claude_target_uses_stable_and_concrete_model_names(self) -> None:
        configuration = next(
            item
            for item in self.suite.configurations
            if item.configured_model == "sonnet-5"
        )

        self.assertEqual("claude-sonnet-5", configuration.target_model)
        self.assertEqual(
            "claude-code[high]:anthropic/claude-sonnet-5",
            configuration.target("high"),
        )


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
        _git(repository, "add", "README.md")
        _git(
            repository,
            "-c",
            "user.name=test",
            "-c",
            "user.email=test@example.com",
            "commit",
            "-m",
            "seed",
        )
        commit = _git(repository, "rev-parse", "HEAD").stdout.strip()

        benchmark._create_source_worktree(source, commit, repository)
        (source / "stale.txt").write_text("stale\n", encoding="utf-8")

        benchmark._create_source_worktree(source, commit, repository)

        self.assertFalse((source / "stale.txt").exists())
        self.assertEqual(commit, _git(source, "rev-parse", "HEAD").stdout.strip())
        benchmark._remove_worktree(source, repository)

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
                "_create_source_worktree",
        ), patch.object(
                benchmark.subprocess,
                "run",
                side_effect=execute,
        ), patch.object(
                benchmark,
                "publish_workspace",
        ) as publish, patch.object(
                benchmark,
                "_remove_worktree",
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
                benchmark,
                "_remove_worktree",
                side_effect=error,
        ), patch("builtins.print") as output:
            benchmark._discard_source_worktree(Path("/tmp/source"))

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
                "_create_source_worktree",
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

    def test_retains_source_when_publication_has_no_marker(self) -> None:
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)

        with patch.object(
                benchmark,
                "_new_run_id",
                return_value="run-failure",
        ), patch.object(
                benchmark,
                "_create_source_worktree",
        ), patch.object(
                benchmark.subprocess,
                "run",
                return_value=subprocess.CompletedProcess([], 1),
        ), patch.object(
                benchmark,
                "publish_workspace",
                side_effect=benchmark.BenchmarkError("push failed"),
        ), patch.object(
                benchmark,
                "_remove_worktree",
        ) as remove, patch("builtins.print"):
            outcome = benchmark._execute_cell(
                self.suite,
                self.cell,
                "a" * 40,
                root,
            )

        self.assertEqual((False, False), outcome)
        remove.assert_not_called()


class CodeCoverageBenchmarkMetricsTests(unittest.TestCase):

    def _workspace(self) -> tuple[tempfile.TemporaryDirectory, Path]:
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        workspace = Path(temporary.name) / "code-coverage-99000"
        workspace.mkdir()
        return temporary, workspace

    def _write_run(self, workspace: Path, all_methods: int = 31) -> None:
        _write_json(
            workspace / benchmark.RUN_RECORD,
            {
                "schemaVersion": "1.0.0",
                "runId": "run-1",
                "startedAt": "2026-09-03T10:00:00Z",
                "benchmarkSuiteCommit": "a" * 40,
                "runnerCommit": "b" * 40,
                "coordinate": "com.example:demo:1.0.0",
                "workspaceName": "code-coverage-99000",
                "agent": "claude-code",
                "configuredModel": "sonnet-5",
                "targetModel": "claude-sonnet-5",
                "thinking": "high",
                "checkedInAllMethods": all_methods,
                "sourceWorktree": "/tmp/source",
                "runnerForgePath": "/tmp/forge",
            },
        )

    def _write_invocation(
            self,
            workspace: Path,
            name: str,
            state: str,
            input_tokens: int,
            cached_tokens: int,
            output_tokens: int,
    ) -> None:
        _write_json(
            workspace
            / "runtime"
            / "accounting"
            / "invocations"
            / f"{name}.json",
            {
                "state": state,
                "agent": "claude-code",
                "model": "anthropic/claude-sonnet-5",
                "tokens": {
                    "input": {
                        "total": {"value": input_tokens},
                        "cached_read": {"value": cached_tokens},
                    },
                    "output": {"total": {"value": output_tokens}},
                },
            },
        )

    def test_collects_complete_phase_and_total_metrics(self) -> None:
        _, workspace = self._workspace()
        self._write_run(workspace)
        final_dir = workspace / "runtime" / "code-coverage" / "finalization"
        final_dir.mkdir(parents=True)
        shutil.copy2(FINAL_METRICS, final_dir / "final-metrics.json")
        self._write_invocation(workspace, "1", "api-cover", 10, 20, 3)
        self._write_invocation(workspace, "2", "api-fix", 1, 2, 3)
        self._write_invocation(workspace, "3", "deep-cover", 4, 5, 6)
        self._write_invocation(workspace, "4", "deep-fix", 7, 8, 9)

        result = benchmark._collect_result(workspace, "success", 0)

        self.assertEqual("success", result["status"])
        self.assertIsNone(result["failure"])
        self.assertEqual("anthropic/claude-sonnet-5", result["observedModel"])
        self.assertEqual(9, result["api"]["coverPasses"])
        self.assertEqual(15, result["deep"]["coverPasses"])
        self.assertEqual(1, result["api"]["fixInvocations"])
        self.assertEqual(1, result["deep"]["fixInvocations"])
        self.assertEqual(
            {"input": 11, "cachedInputRead": 22, "output": 6},
            result["api"]["tokens"],
        )
        self.assertEqual(
            {"input": 22, "cachedInputRead": 35, "output": 21},
            result["total"]["tokens"],
        )
        self.assertEqual(
            {
                "coveredBefore": 9,
                "coveredAfter": 21,
                "methodsGained": 12,
                "percentagePointsGained": 40.0,
                "allMethods": 30,
            },
            result["total"]["coverage"],
        )
        self.assertEqual(-1, result["measuredAllMethodsDifference"])

    def test_partial_failure_keeps_known_accounting_and_nulls(self) -> None:
        _, workspace = self._workspace()
        self._write_run(workspace)
        self._write_invocation(workspace, "1", "deep-fix", 7, 8, 9)
        transitions = workspace / "runtime" / "state-transitions.log"
        transitions.parent.mkdir(parents=True, exist_ok=True)
        transitions.write_text(
            "code-coverage-99000.code-coverage-deep-coverage "
            "deep-measure@deep-fix\n",
            encoding="utf-8",
        )

        result = benchmark._collect_result(workspace, "failure", 7)

        self.assertEqual("failure", result["status"])
        self.assertEqual({"phase": "deep", "exitCode": 7}, result["failure"])
        self.assertEqual(1, result["deep"]["fixInvocations"])
        self.assertEqual(
            {"input": 7, "cachedInputRead": 8, "output": 9},
            result["deep"]["tokens"],
        )
        self.assertIsNone(result["deep"]["coverPasses"])
        self.assertIsNone(result["total"]["tokens"]["input"])
        self.assertIsNone(result["total"]["coverage"]["allMethods"])

    def test_merge_is_idempotent_and_rejects_conflicts(self) -> None:
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        path = Path(temporary.name) / "metrics.json"
        _, workspace = self._workspace()
        self._write_run(workspace)
        result = benchmark._collect_result(workspace, "failure", 1)

        self.assertTrue(benchmark._merge_result(path, result))
        self.assertFalse(benchmark._merge_result(path, result))
        conflicting = dict(result, status="success", failure=None)
        with self.assertRaisesRegex(benchmark.BenchmarkError, "different metrics"):
            benchmark._merge_result(path, conflicting)

    def test_publication_uses_one_disposable_worktree(self) -> None:
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)
        remote = root / "remote.git"
        repository = root / "repository"
        remote.mkdir()
        repository.mkdir()
        _git(remote, "init", "--bare", "-b", "master")
        _git(repository, "init", "-b", "master")
        (repository / "README.md").write_text("seed\n", encoding="utf-8")
        _git(repository, "add", "README.md")
        _git(
            repository,
            "-c",
            "user.name=test",
            "-c",
            "user.email=test@example.com",
            "commit",
            "-m",
            "seed",
        )
        _git(repository, "remote", "add", "origin", str(remote))
        _git(repository, "push", "-u", "origin", "master")
        workspace = root / "run-1" / "code-coverage-99000"
        workspace.mkdir(parents=True)
        self._write_run(workspace)
        result = benchmark._collect_result(workspace, "failure", 1)

        first_commit = benchmark._publish_result(repository, workspace, result)
        second_commit = benchmark._publish_result(repository, workspace, result)

        self.assertEqual(first_commit, second_commit)
        metrics_path = "code-coverage-benchmarks/com.example/demo/1.0.0.json"
        stored = _git(
            repository,
            "show",
            f"{second_commit}:{metrics_path}",
        ).stdout
        self.assertEqual([result], json.loads(stored))
        self.assertEqual(
            benchmark.COMMIT_SUBJECT,
            _git(
                repository,
                "show",
                "-s",
                "--format=%s",
                second_commit,
            ).stdout.strip(),
        )
        changed = _git(
            repository,
            "show",
            "--pretty=",
            "--name-only",
            second_commit,
        ).stdout.splitlines()
        self.assertEqual([metrics_path], [line for line in changed if line])
        self.assertEqual([], list(workspace.parent.glob("publisher-*")))


class CodeCoverageBenchmarkTemplateTests(unittest.TestCase):

    def test_template_has_distinct_conversion_and_terminal_branches(self) -> None:
        tasks = (
            FORGE_ROOT
            / ".agents"
            / "rhei"
            / "templates"
            / "code-coverage-improvement"
            / "tasks"
            / "code-coverage-improvement.md"
        ).read_text(encoding="utf-8")
        states = (
            FORGE_ROOT
            / ".agents"
            / "rhei"
            / "templates"
            / "code-coverage-improvement"
            / "states.yaml"
        ).read_text(encoding="utf-8")

        self.assertIn("{% if benchmark %}", tasks)
        self.assertIn("**State:** benchmark-convert", tasks)
        self.assertIn("code-coverage-benchmark-publication", tasks)
        self.assertIn("**State:** benchmark-publication", tasks)
        self.assertIn("### Task code-coverage-publication", tasks)
        self.assertIn("benchmark-convert:", states)
        self.assertIn("benchmark-publication:", states)

    def test_settings_define_all_pi_and_claude_effort_levels(self) -> None:
        settings_path = (
            FORGE_ROOT
            / ".agents"
            / "rhei"
            / "templates"
            / "code-coverage-improvement"
            / "settings.json"
        )
        settings = json.loads(settings_path.read_text(encoding="utf-8"))

        self.assertEqual(
            {"medium", "high", "xhigh"},
            set(settings["agents"]["pi"]["modes"]),
        )
        self.assertEqual(
            {"medium", "high", "xhigh"},
            set(settings["agents"]["claude-code"]["modes"]),
        )
        self.assertEqual(
            ["claude"],
            settings["agents"]["claude-code"]["command"],
        )


if __name__ == "__main__":
    unittest.main()
