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
from types import SimpleNamespace
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
            "claude-code[high]:claude-sonnet-5",
            configuration.target("high"),
        )

    def test_analysis_role_follows_the_cell_configuration(self) -> None:
        """A cell repairs its own output with its own agent."""
        claude = next(
            item for item in self.suite.configurations
            if item.configured_model == "opus-5"
        )
        self.assertEqual(
            {
                "FORGE_ANALYSIS_FAMILY": "claude-code",
                "FORGE_ANALYSIS_MODEL": "claude-opus-5",
                "FORGE_ANALYSIS_THINKING_LEVEL": "medium",
            },
            claude.analysis_role_environment("medium"),
        )

    def test_analysis_role_carries_a_provider_only_when_meaningful(self) -> None:
        """`pi` routes through a provider; Claude Code authenticates itself."""
        pi = next(
            item for item in self.suite.configurations
            if item.configured_model == "gpt-5.6-luna"
        )
        self.assertEqual(
            "openai-codex", pi.analysis_role_environment("high")["FORGE_ANALYSIS_PROVIDER"]
        )
        claude = next(
            item for item in self.suite.configurations
            if item.configured_model == "opus-5"
        )
        self.assertNotIn("FORGE_ANALYSIS_PROVIDER", claude.analysis_role_environment("high"))

    def test_provider_prefix_only_for_provider_aware_agents(self) -> None:
        """`claude` rejects a prefixed model; `pi` routes through a provider."""
        claude = next(
            item
            for item in self.suite.configurations
            if item.configured_model == "opus-5"
        )
        pi = next(
            item
            for item in self.suite.configurations
            if item.configured_model == "gpt-5.6-luna"
        )

        self.assertEqual("claude-code[medium]:claude-opus-5", claude.target("medium"))
        self.assertEqual("pi[medium]:openai-codex/gpt-5.6-luna", pi.target("medium"))


class CodeCoverageBenchmarkConversionTests(unittest.TestCase):
    """The pin supplies the input; the runner supplies the measurement.
    §FS-code-coverage-benchmarking.1
    """

    def _convert(self) -> dict:
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)
        runner = root / "runner"
        source = root / "run" / "source"
        workspace = root / "run" / "code-coverage-99000"
        (runner / "forge").mkdir(parents=True)
        (runner / "README.md").write_text("seed\n", encoding="utf-8")
        _git(runner, "init", "-b", "master")
        _git(runner, "add", "-A")
        _git(
            runner,
            "-c",
            "user.name=test",
            "-c",
            "user.email=test@example.com",
            "commit",
            "-m",
            "seed",
        )
        commit = _git(runner, "rev-parse", "HEAD").stdout.strip()
        benchmark._create_source_worktree(source, commit, runner)
        self.addCleanup(benchmark._remove_worktree, source, runner)
        test_dir = source / "tests" / "src" / "com.example" / "demo" / "1.0.0"
        test_dir.mkdir(parents=True)

        with patch.object(benchmark, "resolve_test_dir", return_value=str(test_dir)):
            benchmark.convert_workspace(
                SimpleNamespace(
                    workspace=str(workspace),
                    coordinate="com.example:demo:1.0.0",
                    run_id="run-1",
                    started_at="2026-09-09T00:00:00Z",
                    suite_commit=commit,
                    runner_commit=commit,
                    source_worktree=str(source),
                    runner_forge_path=str(runner / "forge"),
                    agent="pi",
                    configured_model="gpt-5.6-sol",
                    target_model="openai-codex/gpt-5.6-sol",
                    thinking="high",
                    strategy="guided",
                    checked_in_all_methods=11943,
                )
            )
        conversion = json.loads(
            (
                workspace / "runtime" / "code-coverage" / "issues" / "conversion.json"
            ).read_text(encoding="utf-8")
        )
        conversion["_runnerForge"] = str((runner / "forge").resolve())
        conversion["_source"] = str(source.resolve())
        return conversion

    def test_measurement_helpers_resolve_from_the_runner(self) -> None:
        conversion = self._convert()

        self.assertEqual(conversion["_runnerForge"], conversion["workPath"])

    def test_measured_input_stays_the_pinned_worktree(self) -> None:
        conversion = self._convert()

        self.assertEqual(conversion["_source"], conversion["worktreePath"])
        self.assertNotIn(conversion["_source"], conversion["workPath"])


class CodeCoverageBenchmarkTerminalResultTests(unittest.TestCase):
    """A program state on a terminal edge owes Rhei a non-empty result."""

    def test_writes_message_to_rhei_result_path(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "results" / "task.md"
            with patch.dict("os.environ", {"RHEI_RESULT_PATH": str(path)}):
                benchmark._write_terminal_result("Prepared benchmark run.")
            self.assertEqual(
                "Prepared benchmark run.\n",
                path.read_text(encoding="utf-8"),
            )

    def test_is_a_no_op_without_the_environment_variable(self) -> None:
        with patch.dict("os.environ", {}, clear=True):
            benchmark._write_terminal_result("ignored")

    def test_publication_records_its_own_terminal_result(self) -> None:
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)
        workspace = root / "run-1" / "code-coverage-99000"
        workspace.mkdir(parents=True)
        result = {
            "runId": "run-1",
            "coordinate": "com.example:demo:1.0.0",
            "status": "success",
        }
        path = root / "results" / "publication.md"

        with patch.dict("os.environ", {"RHEI_RESULT_PATH": str(path)}), \
                patch.object(benchmark, "_read_json", return_value={}), \
                patch.object(benchmark, "_collect_result", return_value=result), \
                patch.object(
                    benchmark,
                    "_publish_result",
                    return_value=benchmark.BenchmarkPublication(
                        "c" * 40, "ai/test/benchmark", "forge-benchmark-id", False,
                    ),
                ):
            benchmark.publish_workspace(workspace, requested_status="success")

        recorded = path.read_text(encoding="utf-8")
        self.assertIn("run-1", recorded)
        self.assertIn("com.example:demo:1.0.0", recorded)


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

    def test_published_commits_keep_the_ambient_author(self) -> None:
        """Benchmark commits are authored like every other publication path.

        GitHub resolves a commit to an account by its author email, so an
        override that no account owns publishes commits with no author at all.
        """
        temporary_directory = tempfile.TemporaryDirectory()
        self.addCleanup(temporary_directory.cleanup)
        repository = Path(temporary_directory.name) / "authored"
        repository.mkdir()
        for argument in (
                ["git", "init", "--quiet", "-b", "master"],
                ["git", "config", "user.name", "Benchmark Author"],
                ["git", "config", "user.email", "author@example.com"],
        ):
            subprocess.run(argument, cwd=repository, check=True)
        recorded = repository / "result.json"
        recorded.write_text("{}\n", encoding="utf-8")

        benchmark._commit_paths(repository, [recorded], "Record benchmark")

        self.assertEqual(
            subprocess.run(
                ["git", "log", "-1", "--format=%an <%ae>"],
                cwd=repository,
                check=True,
                capture_output=True,
                text=True,
            ).stdout.strip(),
            "Benchmark Author <author@example.com>",
        )


if __name__ == "__main__":
    unittest.main()
