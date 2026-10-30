# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Coverage benchmark metrics collection, record merging, and result publication tests.

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
from benchmarks import code_coverage_benchmark_publication as benchmark_publication
from code_coverage_benchmark_test_support import FINAL_METRICS, _git, _write_json


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
            cache_write_tokens: int = 0,
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
                        "cache_write": {"value": cache_write_tokens},
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
        self._write_invocation(workspace, "1", "api-cover", 10, 20, 3, 3)
        self._write_invocation(workspace, "2", "api-fix", 1, 2, 3, 1)
        self._write_invocation(workspace, "3", "deep-cover", 4, 5, 6)
        self._write_invocation(workspace, "4", "deep-fix", 7, 8, 9)

        result = benchmark.collect_result(workspace, "success", 0)

        self.assertEqual("success", result["status"])
        self.assertIsNone(result["failure"])
        self.assertEqual("anthropic/claude-sonnet-5", result["observedModel"])
        self.assertEqual(9, result["api"]["coverPasses"])
        self.assertEqual(15, result["deep"]["coverPasses"])
        self.assertEqual(1, result["api"]["fixInvocations"])
        self.assertEqual(1, result["deep"]["fixInvocations"])
        self.assertEqual(
            {"input": 11, "cachedInputRead": 22, "cachedInputWrite": 4, "output": 6},
            result["api"]["tokens"],
        )
        self.assertEqual(
            {"input": 22, "cachedInputRead": 35, "cachedInputWrite": 4, "output": 21},
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

    def test_input_stays_disjoint_from_cached_input(self) -> None:
        """A Rhei total that already contains the cached classes is restated.

        Newer Rhei runtimes report `input.total` inclusive of cached read and
        cache write; the record must still publish ordinary input separately
        (§FS-code-coverage-benchmarking.4).
        """
        _, workspace = self._workspace()
        self._write_run(workspace)
        final_dir = workspace / "runtime" / "code-coverage" / "finalization"
        final_dir.mkdir(parents=True)
        shutil.copy2(FINAL_METRICS, final_dir / "final-metrics.json")
        # api: total 100 = 60 cached_read + 5 cache_write + 35 ordinary.
        self._write_invocation(workspace, "1", "api-cover", 100, 60, 7, 5)
        # deep: an older runtime, whose total already excludes the 8 cached.
        self._write_invocation(workspace, "2", "deep-cover", 3, 8, 9)

        result = benchmark.collect_result(workspace, "success", 0)

        self.assertEqual(
            {"input": 35, "cachedInputRead": 60, "cachedInputWrite": 5, "output": 7},
            result["api"]["tokens"],
        )
        self.assertEqual(
            {"input": 3, "cachedInputRead": 8, "cachedInputWrite": 0, "output": 9},
            result["deep"]["tokens"],
        )
        self.assertEqual(38, result["total"]["tokens"]["input"])

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

        result = benchmark.collect_result(workspace, "failure", 7)

        self.assertEqual("failure", result["status"])
        self.assertEqual({"phase": "deep", "exitCode": 7}, result["failure"])
        self.assertEqual(1, result["deep"]["fixInvocations"])
        self.assertEqual(
            {"input": 7, "cachedInputRead": 8, "cachedInputWrite": 0, "output": 9},
            result["deep"]["tokens"],
        )
        self.assertIsNone(result["deep"]["coverPasses"])
        self.assertIsNone(result["total"]["tokens"]["input"])
        self.assertIsNone(result["total"]["coverage"]["allMethods"])

    def test_unmeasured_invocation_keeps_the_measured_ones(self) -> None:
        """A phase keeps the tokens it measured when one invocation has none.

        An invocation killed on a timeout emits no usage, so Rhei records its
        token values as unknown. The invocations that did report are still
        valid measurements and must survive it
        (§FS-code-coverage-benchmarking.4).
        """
        _, workspace = self._workspace()
        self._write_run(workspace)
        self._write_invocation(workspace, "1", "deep-cover", 10, 20, 3, 3)
        self._write_invocation(workspace, "2", "deep-cover", 1, 2, 3, 1)
        _write_json(
            workspace
            / "runtime"
            / "accounting"
            / "invocations"
            / "3.json",
            {
                "state": "deep-cover",
                "agent": "claude-code",
                "model": "anthropic/claude-sonnet-5",
                "extraction_status": "no-usage-emitted",
                "tokens": {
                    "input": {
                        "total": {"status": "unknown"},
                        "cached_read": {"status": "unknown"},
                        "cache_write": {"status": "unknown"},
                    },
                    "output": {"total": {"status": "unknown"}},
                },
            },
        )

        result = benchmark.collect_result(workspace, "failure", 7)

        self.assertEqual(
            {"input": 11, "cachedInputRead": 22, "cachedInputWrite": 4, "output": 6},
            result["deep"]["tokens"],
        )

    def test_written_record_is_immutable_for_its_run_id(self) -> None:
        """A written record is returned verbatim whatever status is requested
        later, even when terminal evidence appears after the write."""
        _, workspace = self._workspace()
        self._write_run(workspace)
        recorded = benchmark.collect_result(workspace, "failure", 7)
        self.assertEqual("failure", recorded["status"])

        final_metrics = benchmark.final_metrics_path(workspace)
        final_metrics.parent.mkdir(parents=True)
        shutil.copy2(FINAL_METRICS, final_metrics)

        self.assertEqual(recorded, benchmark.collect_result(workspace, "success", 0))
        self.assertEqual(recorded, benchmark.collect_result(workspace, None, None))

    def test_collecting_a_new_record_requires_an_explicit_status(self) -> None:
        """Without a written record there is no automatic failure default: the
        operator must decide the status explicitly."""
        _, workspace = self._workspace()
        self._write_run(workspace)
        with self.assertRaises(benchmark.BenchmarkError):
            benchmark.collect_result(workspace, None, None)
        self.assertFalse(benchmark.result_record_path(workspace).is_file())

    def test_merge_is_idempotent_and_rejects_conflicts(self) -> None:
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        path = Path(temporary.name) / "metrics.json"
        _, workspace = self._workspace()
        self._write_run(workspace)
        result = benchmark.collect_result(workspace, "failure", 1)

        self.assertTrue(benchmark.merge_result(path, result))
        self.assertFalse(benchmark.merge_result(path, result))
        conflicting = dict(result, status="success", failure=None)
        with self.assertRaisesRegex(benchmark.BenchmarkError, "different metrics"):
            benchmark.merge_result(path, conflicting)

    def test_publication_pushes_one_result_only_branch(self) -> None:
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)
        remote = root / "remote.git"
        repository = root / "repository"
        remote.mkdir()
        repository.mkdir()
        _git(remote, "init", "--bare", "-b", "master")
        _git(repository, "init", "-b", "master")
        # The publication commits too; a host without a git identity must not fail it.
        _git(repository, "config", "user.name", "test")
        _git(repository, "config", "user.email", "test@example.com")
        (repository / "README.md").write_text("seed\n", encoding="utf-8")
        _git(repository, "add", "README.md")
        _git(repository, "commit", "-m", "seed")
        _git(repository, "remote", "add", "origin", str(remote))
        _git(repository, "push", "-u", "origin", "master")
        workspace = root / "run-1" / "code-coverage-99000"
        workspace.mkdir(parents=True)
        self._write_run(workspace)
        result = benchmark.collect_result(workspace, "failure", 1)

        with patch.object(
                benchmark_publication,
                "get_authenticated_login",
                return_value="test-bot",
        ):
            first = benchmark.publish_result(repository, workspace, result)
            second = benchmark.publish_result(repository, workspace, result)

        self.assertEqual(first, second)
        self.assertFalse(first.already_merged)
        self.assertIsNotNone(first.branch)
        metrics_path = "code-coverage-benchmarks/com.example/demo/1.0.0.json"
        stored = _git(
            repository,
            "show",
            f"{first.commit}:{metrics_path}",
        ).stdout
        self.assertEqual([result], json.loads(stored))
        self.assertEqual(
            benchmark.COMMIT_SUBJECT,
            _git(
                repository,
                "show",
                "-s",
                "--format=%s",
                first.commit,
            ).stdout.strip(),
        )
        master_object = subprocess.run(
            ["git", "cat-file", "-e", f"origin/master:{metrics_path}"],
            cwd=repository,
            check=False,
            capture_output=True,
            text=True,
        )
        self.assertNotEqual(0, master_object.returncode)
        changed = _git(
            repository,
            "diff",
            "--name-only",
            "origin/master",
            first.commit,
        ).stdout.splitlines()
        self.assertEqual(
            [metrics_path],
            sorted(line for line in changed if line),
        )
        self.assertEqual([], list(workspace.parent.glob("publisher-*")))


if __name__ == "__main__":
    unittest.main()
