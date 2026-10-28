# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import io
import os
import subprocess
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path
from unittest.mock import patch

from utility_scripts.host_graalvm_checks import ISSUE_GRAALVM_ENV_VARS
from utility_scripts.host_requirements import (
    COVERAGE_REQUIREMENTS,
    AgentRequirement,
    HostRequirements,
    QueueRequirements,
    main,
    parse_args,
    resolve_queue_requirements,
)


class HostRequirementsTests(unittest.TestCase):
    def test_host_cli_accepts_verbose_output(self) -> None:
        args = parse_args(["--forge-dir", "/repo/forge", "--verbose"])

        self.assertTrue(args.verbose)

    def test_invalid_queue_limit_prints_one_actionable_fix(self) -> None:
        stdout = io.StringIO()
        stderr = io.StringIO()
        with patch.dict(os.environ, {"FORGE_WORK_LIMIT": "invalid"}, clear=True), \
                redirect_stdout(stdout), redirect_stderr(stderr):
            result = main(["--forge-dir", "/repo/forge"])

        self.assertEqual(1, result)
        self.assertEqual("", stdout.getvalue())
        self.assertIn("FORGE_WORK_LIMIT must be a non-negative integer", stderr.getvalue())
        self.assertEqual(1, stderr.getvalue().count("Fix:"))

    def test_queue_requirements_follow_effective_limits(self) -> None:
        environment = {
            "FORGE_JAVAC_WORK_LIMIT": "0",
            "FORGE_JAVA_RUN_WORK_LIMIT": "0",
            "FORGE_NI_RUN_WORK_LIMIT": "0",
            "FORGE_LIBRARY_UPDATE_WORK_LIMIT": "0",
            "FORGE_WORK_LIMIT": "0",
            "FORGE_REVIEW_LIMIT": "0",
            "FORGE_BULK_UPDATE_REVIEW_LIMIT": "2",
        }

        requirements = resolve_queue_requirements(environment)

        self.assertFalse(requirements.issue_work)
        self.assertTrue(requirements.review_work)

    def test_explicit_review_label_ignores_default_queue_overrides(self) -> None:
        environment = {
            "FORGE_JAVAC_WORK_LIMIT": "0",
            "FORGE_JAVA_RUN_WORK_LIMIT": "0",
            "FORGE_NI_RUN_WORK_LIMIT": "0",
            "FORGE_LIBRARY_UPDATE_WORK_LIMIT": "0",
            "FORGE_WORK_LIMIT": "0",
            "FORGE_REVIEW_LABEL": "library-new-request",
            "FORGE_REVIEW_LIMIT": "0",
            "FORGE_BULK_UPDATE_REVIEW_LIMIT": "2",
        }

        requirements = resolve_queue_requirements(environment)

        self.assertFalse(requirements.issue_work)
        self.assertFalse(requirements.review_work)

    def test_coverage_lane_selects_build_capabilities(self) -> None:
        self.assertTrue(COVERAGE_REQUIREMENTS.build_work)
        self.assertTrue(COVERAGE_REQUIREMENTS.any_work)
        self.assertFalse(COVERAGE_REQUIREMENTS.issue_work)
        self.assertTrue(COVERAGE_REQUIREMENTS.github_work)

    def test_queues_mode_selects_issue_work_review_and_the_review_agent(self) -> None:
        with patch.dict(os.environ, {}, clear=True), \
                patch.object(HostRequirements, "run", autospec=True, return_value=True) as run:
            result = main([
                "--forge-dir", "/repo/forge", "--mode", "queues",
                "--review-agent", "claude", "--review-model", "opus",
            ])

        self.assertEqual(0, result)
        host_requirements = run.call_args.args[0]
        self.assertTrue(host_requirements.requirements.issue_work)
        self.assertTrue(host_requirements.requirements.review_work)
        self.assertEqual(AgentRequirement("claude-code", "claude", "opus"), host_requirements.review_role)

    def test_issue_work_report_names_every_java_lane_and_the_review_agent(self) -> None:
        host_requirements = HostRequirements(
            "/repo/forge",
            "python3",
            {},
            requirements=QueueRequirements(issue_work=True, review_work=True),
            review_model="opus",
        )
        stdout = io.StringIO()
        with redirect_stdout(stdout):
            host_requirements._print_manifest()

        output = stdout.getvalue()
        self.assertIn("Modes: issue work=enabled, coverage work=disabled, PR review=enabled", output)
        for variable in ISSUE_GRAALVM_ENV_VARS:
            self.assertIn(f"  - {variable}=", output)
        self.assertIn("  - Review agent: claude model=opus", output)
        self.assertNotIn("Test agent", output)

    def test_successful_host_report_is_quiet_unless_verbose(self) -> None:
        def run_host(verbose: bool) -> str:
            host_requirements = HostRequirements(
                "/repo/forge",
                "python3",
                {},
                requirements=QueueRequirements(
                    issue_work=False,
                    review_work=False,
                    github_work=False,
                ),
            )
            stdout = io.StringIO()
            with patch.object(host_requirements, "_check_tools"), \
                    patch.object(host_requirements, "_check_environment"), \
                    patch.object(host_requirements, "_check_write_permissions"), \
                    patch.object(host_requirements, "_check_network"), \
                    patch.object(host_requirements, "_check_github"), \
                    patch.object(host_requirements, "_check_selected_agents"), \
                    patch.object(host_requirements, "_check_docker"), \
                    redirect_stdout(stdout):
                self.assertTrue(host_requirements.run(verbose=verbose))
            return stdout.getvalue()

        self.assertEqual("", run_host(verbose=False))
        verbose_output = run_host(verbose=True)
        self.assertIn("[forge-host] Deterministic host requirements", verbose_output)
        self.assertIn("[forge-host] Check results:", verbose_output)
        self.assertIn("PASS: all required host checks succeeded", verbose_output)

if __name__ == "__main__":
    unittest.main()
