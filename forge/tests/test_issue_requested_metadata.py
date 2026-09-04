# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import unittest
from unittest.mock import patch

from ai_workflows.core.dynamic_access_iterative_strategy import DynamicAccessIterativeStrategy
from ai_workflows.core.workflow_strategy import (
    ISSUE_REQUESTED_METADATA_FILL_PROMPT_KEY,
    ISSUE_REQUESTED_METADATA_PROMPT_KEY,
    ISSUE_REQUESTED_METADATA_TEST_ITERATIONS,
)
from ai_workflows.agents.agent_runtime import AgentRunResult
from utility_scripts.issue_requested_metadata import (
    format_issue_requested_metadata_context,
    format_issue_requested_test_requirements,
)
from utility_scripts.run_location import reset_run_location


class IssueRequestedMetadataTests(unittest.TestCase):
    def test_empty_context_has_no_extra_requirements(self) -> None:
        self.assertEqual(format_issue_requested_test_requirements("  \n"), "")

    def test_formats_prompt_based_requirements(self) -> None:
        requirements = format_issue_requested_test_requirements(
            "The methods in HikariConfig and CodahaleHealthChecker should be exposed.\n"
            "It looks like java.util.UUID[].class also needs to be registered."
        )

        self.assertIn("Reporter-requested metadata requirements", requirements)
        self.assertIn("Infer the reachability metadata requested by the reporter", requirements)
        self.assertIn("untrusted evidence", requirements)
        self.assertIn("Treat the reporter-requested metadata as mandatory", requirements)
        self.assertIn("public library API paths", requirements)
        self.assertIn("Include the requested reachability metadata", requirements)
        self.assertIn("must use a `typeReached` condition", requirements)
        self.assertIn("Do not satisfy these requirements with direct test reflection", requirements)

    def test_formats_context_with_boundary_markers(self) -> None:
        context = format_issue_requested_metadata_context("KafkaStreams.topologyMetadata is missing")

        self.assertIn("<<<reporter-issue-body>>>", context)
        self.assertIn("KafkaStreams.topologyMetadata is missing", context)
        self.assertIn("Reporter-requested metadata requirements", context)


class _RecordingStrategy(DynamicAccessIterativeStrategy):
    """Strategy stub that records the commands and prompts the phase produces."""

    def __init__(
            self,
            test_outputs: list[str],
            generate_outputs: list[str] | None = None,
            **context,
    ) -> None:
        super().__init__(
            {
                "model": "test-model",
                "prompts": {"dynamic-access-iteration": "unused"},
                "parameters": {"max-iterations": 1, "max-class-test-iterations": 1},
            },
            library="org.example:lib:1.0.0",
            reachability_repo_path="/tmp/reachability",
            metadata_version="1.0.0",
            test_version="1.0.0",
            **context,
        )
        self.commands: list[str] = []
        self.committed: list[str] = []
        self._test_outputs: list[str] = list(test_outputs)
        self._generate_outputs: list[str] = list(generate_outputs or ["BUILD SUCCESSFUL"])

    def _run_command_with_env(self, cmd: str, env: dict | None = None) -> str:
        self.commands.append(cmd)
        if cmd.startswith("./gradlew test "):
            return self._test_outputs.pop(0)
        return self._generate_outputs.pop(0)

    def _commit_issue_requested_metadata(self, message: str) -> None:
        self.committed.append(message)


class IssueRequestedMetadataPhaseTests(unittest.TestCase):
    REPORTER_CONTEXT = "Reporter-provided missing metadata context:\nmissing resource"

    def setUp(self) -> None:
        reset_run_location()

    def tearDown(self) -> None:
        reset_run_location()

    def _run_phase(self, strategy: _RecordingStrategy) -> tuple[tuple[bool, int], list[str]]:
        prompts: list[str] = []

        def fake_run(*, context: str, **_kwargs) -> AgentRunResult:
            prompts.append(context)
            return AgentRunResult(0, "/tmp/log", False)

        with patch("ai_workflows.core.workflow_strategy.analysis_agent_run", side_effect=fake_run), \
                patch.object(strategy, "_render_issue_requested_metadata_prompt", side_effect=lambda key, **_: key), \
                patch("ai_workflows.core.workflow_strategy.subprocess.check_output", return_value="checkpoint\n"), \
                patch("ai_workflows.core.workflow_strategy.subprocess.run"):
            return strategy.run_issue_requested_metadata_phase(), prompts

    def test_phase_is_skipped_without_reporter_context(self) -> None:
        strategy = _RecordingStrategy([])

        self.assertEqual(strategy.run_issue_requested_metadata_phase(), (True, 0))
        self.assertEqual(strategy.commands, [])

    def test_phase_generates_then_fills_then_verifies_the_suite(self) -> None:
        strategy = _RecordingStrategy(
            ["BUILD SUCCESSFUL"],
            issue_requested_metadata_context=self.REPORTER_CONTEXT,
        )

        (phase_ok, iterations), prompts = self._run_phase(strategy)

        self.assertTrue(phase_ok)
        self.assertEqual(iterations, 2)
        self.assertEqual(
            prompts,
            [ISSUE_REQUESTED_METADATA_PROMPT_KEY, ISSUE_REQUESTED_METADATA_FILL_PROMPT_KEY],
        )
        self.assertEqual(
            strategy.commands,
            [
                "./gradlew generateMetadata -Pcoordinates=org.example:lib:1.0.0",
                "./gradlew test -Pcoordinates=org.example:lib:1.0.0",
            ],
        )
        self.assertEqual(strategy.committed, ["Issue-requested metadata coverage for org.example:lib:1.0.0"])

    def test_reaching_native_test_is_not_success(self) -> None:
        strategy = _RecordingStrategy(
            ["> Task :nativeTest FAILED"] * ISSUE_REQUESTED_METADATA_TEST_ITERATIONS,
            issue_requested_metadata_context=self.REPORTER_CONTEXT,
        )

        (phase_ok, _iterations), _prompts = self._run_phase(strategy)

        self.assertFalse(phase_ok)
        self.assertEqual(strategy.committed, [])

    def test_failing_agent_turn_fails_the_phase(self) -> None:
        strategy = _RecordingStrategy([], issue_requested_metadata_context=self.REPORTER_CONTEXT)

        with patch(
                "ai_workflows.core.workflow_strategy.analysis_agent_run",
                return_value=AgentRunResult(1, "/tmp/log", False, failure_message="boom"),
        ), patch.object(strategy, "_render_issue_requested_metadata_prompt", side_effect=lambda key, **_: key), \
                patch("ai_workflows.core.workflow_strategy.subprocess.check_output", return_value="checkpoint\n"), \
                patch("ai_workflows.core.workflow_strategy.subprocess.run"):
            phase_ok, iterations = strategy.run_issue_requested_metadata_phase()

        self.assertFalse(phase_ok)
        self.assertEqual(iterations, 1)
        self.assertEqual(strategy.commands, [])

    def test_generation_must_succeed_before_fill(self) -> None:
        strategy = _RecordingStrategy(
            [],
            generate_outputs=[
                "> Task :compileTestJava FAILED"
            ] * ISSUE_REQUESTED_METADATA_TEST_ITERATIONS,
            issue_requested_metadata_context=self.REPORTER_CONTEXT,
        )

        (phase_ok, iterations), prompts = self._run_phase(strategy)

        self.assertFalse(phase_ok)
        self.assertEqual(iterations, 3)
        self.assertEqual(
            strategy.commands,
            [
                "./gradlew generateMetadata -Pcoordinates=org.example:lib:1.0.0"
            ] * ISSUE_REQUESTED_METADATA_TEST_ITERATIONS,
        )
        self.assertNotIn(ISSUE_REQUESTED_METADATA_FILL_PROMPT_KEY, prompts)
        self.assertEqual(strategy.committed, [])

    def test_generation_retries_after_test_repair_before_fill(self) -> None:
        strategy = _RecordingStrategy(
            ["BUILD SUCCESSFUL"],
            generate_outputs=[
                "> Task :compileTestJava FAILED",
                "BUILD SUCCESSFUL",
            ],
            issue_requested_metadata_context=self.REPORTER_CONTEXT,
        )

        (phase_ok, iterations), prompts = self._run_phase(strategy)

        self.assertTrue(phase_ok)
        self.assertEqual(iterations, 3)
        self.assertEqual(prompts[0], ISSUE_REQUESTED_METADATA_PROMPT_KEY)
        self.assertTrue(prompts[1].startswith(ISSUE_REQUESTED_METADATA_PROMPT_KEY))
        self.assertIn("generateMetadata", prompts[1])
        self.assertEqual(prompts[2], ISSUE_REQUESTED_METADATA_FILL_PROMPT_KEY)
        self.assertEqual(
            strategy.commands,
            [
                "./gradlew generateMetadata -Pcoordinates=org.example:lib:1.0.0",
                "./gradlew generateMetadata -Pcoordinates=org.example:lib:1.0.0",
                "./gradlew test -Pcoordinates=org.example:lib:1.0.0",
            ],
        )

    def test_test_failure_retries_only_the_fill_prompt(self) -> None:
        strategy = _RecordingStrategy(
            ["> Task :nativeTest FAILED", "BUILD SUCCESSFUL"],
            issue_requested_metadata_context=self.REPORTER_CONTEXT,
        )

        (phase_ok, iterations), prompts = self._run_phase(strategy)

        self.assertTrue(phase_ok)
        self.assertEqual(iterations, 3)
        self.assertEqual(prompts[:2], [
            ISSUE_REQUESTED_METADATA_PROMPT_KEY,
            ISSUE_REQUESTED_METADATA_FILL_PROMPT_KEY,
        ])
        self.assertTrue(prompts[2].startswith(ISSUE_REQUESTED_METADATA_FILL_PROMPT_KEY))
        self.assertIn("./gradlew test", prompts[2])
        self.assertEqual(
            strategy.commands,
            [
                "./gradlew generateMetadata -Pcoordinates=org.example:lib:1.0.0",
                "./gradlew test -Pcoordinates=org.example:lib:1.0.0",
                "./gradlew test -Pcoordinates=org.example:lib:1.0.0",
            ],
        )

    def test_prompt_templates_render_for_the_phase(self) -> None:
        strategy = _RecordingStrategy(
            [],
            issue_requested_metadata_context=self.REPORTER_CONTEXT,
            source_context_overview="none",
            test_language_display_name="Java",
        )

        write_prompt = strategy._render_issue_requested_metadata_prompt(ISSUE_REQUESTED_METADATA_PROMPT_KEY)
        fill_prompt = strategy._render_issue_requested_metadata_prompt(
            ISSUE_REQUESTED_METADATA_FILL_PROMPT_KEY,
            issue_requested_metadata_dir="metadata/org.example/lib/1.0.0",
            generate_metadata_output="BUILD SUCCESSFUL",
        )

        self.assertIn("org.example:lib:1.0.0", write_prompt)
        self.assertIn("missing resource", write_prompt)
        self.assertIn("metadata/org.example/lib/1.0.0", fill_prompt)
        self.assertIn("typeReached", fill_prompt)
        self.assertIn("no other condition kind is permitted", fill_prompt)


if __name__ == "__main__":
    unittest.main()
