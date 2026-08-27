# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

from __future__ import annotations

import ast
import inspect
import shutil
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

_FORGE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(_FORGE_ROOT))

from ai_workflows.agents.agent_runtime import AgentRunResult  # noqa: E402
from ai_workflows.core.post_generation_fix import run_post_generation_fix  # noqa: E402

_LIBRARY = "g:a:1.0"


class PostGenerationFixTests(unittest.TestCase):
    def setUp(self) -> None:
        self.repo = tempfile.mkdtemp(prefix="repo-")
        self.addCleanup(shutil.rmtree, self.repo, ignore_errors=True)

    def _run(self, agent_result: AgentRunResult, report: str | None):
        def write_report(**kwargs):
            if report is not None:
                path = Path(self.repo) / "post-gen-interventions" / "g_a_1.0.md"
                path.write_text(report, encoding="utf-8")
            return agent_result

        with patch(
                "ai_workflows.core.post_generation_fix.analysis_agent_run",
                side_effect=write_report,
        ) as agent, patch(
                "ai_workflows.core.post_generation_fix.get_analysis_agent"
        ) as selection:
            selection.return_value.backend = "codex"
            result = run_post_generation_fix(
                reachability_metadata_path=self.repo,
                coordinates=_LIBRARY,
                analysis_log_path="/tmp/analysis.log",
                test_output="FAILED: SomeTest",
            )
        return result, agent

    def test_runs_on_the_analysis_role_and_accepts_a_complete_report(self) -> None:
        (rc, _path, timed_out), agent = self._run(
            AgentRunResult(0, "/tmp/post-gen.log", False, "done"),
            f"Library: {_LIBRARY}\n\nRemoved the failing test.\n",
        )

        self.assertEqual(rc, 0)
        self.assertFalse(timed_out)
        kwargs = agent.call_args.kwargs
        self.assertEqual(kwargs["task_type"], "post-gen")
        self.assertEqual(kwargs["library"], _LIBRARY)
        # The role picks the backend; no caller-supplied model or agent name.
        self.assertNotIn("model", kwargs)
        self.assertNotIn("backend", kwargs)

    def test_failed_agent_turn_reports_its_timeout(self) -> None:
        (rc, _path, timed_out), _agent = self._run(
            AgentRunResult(1, "/tmp/post-gen.log", True), None
        )

        self.assertEqual(rc, 1)
        self.assertTrue(timed_out)

    def test_missing_report_fails_the_intervention(self) -> None:
        (rc, _path, _timed_out), _agent = self._run(
            AgentRunResult(0, "/tmp/post-gen.log", False, "done"), None
        )

        self.assertEqual(rc, 1)

    def test_report_without_the_library_line_fails_the_intervention(self) -> None:
        (rc, _path, _timed_out), _agent = self._run(
            AgentRunResult(0, "/tmp/post-gen.log", False, "done"),
            "Removed the failing test.\n",
        )

        self.assertEqual(rc, 1)

    def test_workflow_base_class_passes_parameters_this_function_accepts(self) -> None:
        """The caller drifted from this signature once; keep them bound."""
        source = (_FORGE_ROOT / "ai_workflows" / "core" / "workflow_strategy.py").read_text()
        calls = [
            node
            for node in ast.walk(ast.parse(source))
            if isinstance(node, ast.Call)
            and getattr(node.func, "id", "") == "run_post_generation_fix"
        ]
        self.assertEqual(len(calls), 1)
        accepted = set(inspect.signature(run_post_generation_fix).parameters)
        self.assertEqual(set(), {k.arg for k in calls[0].keywords} - accepted)


if __name__ == "__main__":
    unittest.main()
