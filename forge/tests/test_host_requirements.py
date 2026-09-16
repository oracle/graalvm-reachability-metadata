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

from utility_scripts.host_requirements import (
    COVERAGE_REQUIREMENTS,
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

    def test_test_role_defaults_come_from_the_selected_strategy(self) -> None:
        host_requirements = HostRequirements(
            "/repo/forge",
            "python3",
            environment={},
            requirements=QueueRequirements(issue_work=True, review_work=False),
            test_strategy_name="dynamic_access_main_sources_pi_gpt-5.6-sol",
        )

        self.assertEqual(host_requirements.test_family, "pi")
        self.assertEqual(host_requirements.test_agent, "pi")
        self.assertEqual(host_requirements.test_model, "gpt-5.6-sol")

    def test_required_failure_stops_before_work_and_prints_remediation(self) -> None:
        environment = {
            "FORGE_JAVAC_WORK_LIMIT": "0",
            "FORGE_JAVA_RUN_WORK_LIMIT": "0",
            "FORGE_NI_RUN_WORK_LIMIT": "0",
            "FORGE_LIBRARY_UPDATE_WORK_LIMIT": "0",
            "FORGE_WORK_LIMIT": "0",
            "FORGE_REVIEW_LIMIT": "1",
        }
        host_requirements = HostRequirements("/repo/forge", "python3", environment)

        def fail_tools() -> None:
            host_requirements._add(
                "tool",
                "GitHub CLI",
                True,
                False,
                "gh was not found",
                "Install gh.",
            )

        stdout = io.StringIO()
        stderr = io.StringIO()
        with patch.object(host_requirements, "_check_tools", side_effect=fail_tools), \
                patch.object(host_requirements, "_check_environment"), \
                patch.object(host_requirements, "_check_write_permissions"), \
                patch.object(host_requirements, "_check_network"), \
                patch.object(host_requirements, "_check_github"), \
                patch.object(host_requirements, "_check_codex"), \
                patch.object(host_requirements, "_check_docker"), \
                redirect_stdout(stdout), redirect_stderr(stderr):
            passed = host_requirements.run()

        self.assertFalse(passed)
        self.assertIn("[FAIL] tool: GitHub CLI", stdout.getvalue())
        self.assertIn("Fix: Install gh.", stdout.getvalue())
        self.assertIn("No work was started", stdout.getvalue())
        self.assertEqual("", stderr.getvalue())

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

    def test_worker_validates_host_requirements_before_first_cycle(self) -> None:
        worker_path = Path(__file__).resolve().parents[1] / "do_up_to_date_work.sh"
        worker = worker_path.read_text(encoding="utf-8")
        startup = worker.rindex("\nrun_host_requirements\n")
        first_cycle = worker.rindex("\nrun_cycle\n")

        self.assertLess(startup, first_cycle)

    def test_worker_forwards_take_blocked_issues_to_dispatcher(self) -> None:
        """The do-work override reaches every work queue (§FS-forge-run-requirements.2)."""
        worker_path = Path(__file__).resolve().parents[1] / "do_up_to_date_work.sh"

        def run_worker(
                extra_args: list[str],
                take_blocked_environment: str | None = None,
        ) -> list[str]:
            with tempfile.TemporaryDirectory() as temp_dir:
                fake_python = os.path.join(temp_dir, "python3")
                fake_git = os.path.join(temp_dir, "git")
                fake_gh = os.path.join(temp_dir, "gh")
                args_path = os.path.join(temp_dir, "args.txt")
                with open(fake_python, "w", encoding="utf-8") as output_file:
                    output_file.write(
                        "#!/usr/bin/env bash\n"
                        "if [[ \"$1\" == */forge_metadata.py ]]; then\n"
                        "  shift\n"
                        "  printf '%s\\n' \"$@\" > \"$FORGE_TEST_ARGS_FILE\"\n"
                        "fi\n"
                        "exit 0\n"
                    )
                with open(fake_git, "w", encoding="utf-8") as output_file:
                    output_file.write("#!/usr/bin/env bash\nexit 0\n")
                with open(fake_gh, "w", encoding="utf-8") as output_file:
                    output_file.write("#!/usr/bin/env bash\nprintf '{\"resources\":{}}\\n'\n")
                os.chmod(fake_python, 0o755)
                os.chmod(fake_git, 0o755)
                os.chmod(fake_gh, 0o755)

                environment = dict(os.environ)
                environment.pop("FORGE_TAKE_BLOCKED_ISSUES", None)
                environment.pop("FORGE_VERBOSE", None)
                environment.update({
                    "PYTHON_BIN": fake_python,
                    "PATH": f"{temp_dir}{os.pathsep}{environment['PATH']}",
                    "FORGE_TEST_ARGS_FILE": args_path,
                    "FORGE_DO_WORK_STOP_FILE": os.path.join(temp_dir, "stop"),
                    "DO_WORK_CLEAN_LOCAL_REPOSITORIES_EVERY": "99",
                    "FORGE_WORK_LIMIT": "0",
                    "FORGE_JAVAC_WORK_LIMIT": "0",
                    "FORGE_JAVA_RUN_WORK_LIMIT": "0",
                    "FORGE_NI_RUN_WORK_LIMIT": "0",
                    "FORGE_LIBRARY_UPDATE_WORK_LIMIT": "0",
                    "FORGE_REVIEW_LIMIT": "0",
                })
                if take_blocked_environment is not None:
                    environment["FORGE_TAKE_BLOCKED_ISSUES"] = take_blocked_environment

                result = subprocess.run(
                    [str(worker_path), "--once", *extra_args],
                    env=environment,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    text=True,
                    check=False,
                )
                self.assertEqual(0, result.returncode, result.stderr)
                with open(args_path, encoding="utf-8") as input_file:
                    return input_file.read().splitlines()

        self.assertNotIn("--take-blocked-issues", run_worker([]))
        self.assertIn("--take-blocked-issues", run_worker(["--take-blocked-issues"]))
        self.assertIn("--take-blocked-issues", run_worker([], "1"))
        self.assertNotIn("--verbose", run_worker([]))
        self.assertIn("--verbose", run_worker(["--verbose"]))

    def test_worker_propagates_the_analysis_role_selection(self) -> None:
        worker_path = Path(__file__).resolve().parents[1] / "do_up_to_date_work.sh"
        with tempfile.TemporaryDirectory() as temp_dir:
            fake_python = os.path.join(temp_dir, "python3")
            args_path = os.path.join(temp_dir, "args.txt")
            with open(fake_python, "w", encoding="utf-8") as output_file:
                output_file.write(
                    "#!/usr/bin/env bash\n"
                    "printf '%s\\n' \"$@\" > \"$FORGE_TEST_ARGS_FILE\"\n"
                    "exit 1\n"
                )
            os.chmod(fake_python, 0o755)
            environment = dict(os.environ)
            for variable in (
                    "FORGE_AGENT_FAMILY",
                    "FORGE_ANALYSIS_AGENT",
                    "FORGE_ANALYSIS_FAMILY",
                    "FORGE_ANALYSIS_MODEL",
            ):
                environment.pop(variable, None)
            environment.update({
                "PYTHON_BIN": fake_python,
                "FORGE_TEST_ARGS_FILE": args_path,
            })

            result = subprocess.run(
                [
                    str(worker_path), "--once",
                    "--analysis-agent", "cdx",
                    "--analysis-family", "codex",
                ],
                env=environment,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                check=False,
            )
            with open(args_path, encoding="utf-8") as input_file:
                arguments = input_file.read().splitlines()

        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(arguments[arguments.index("--analysis-agent") + 1], "cdx")
        self.assertEqual(arguments[arguments.index("--analysis-family") + 1], "codex")
        self.assertEqual(arguments[arguments.index("--analysis-model") + 1], "gpt-5.6-luna")

    def test_worker_forwards_setup_options_only_when_configured(self) -> None:
        worker_path = Path(__file__).resolve().parents[1] / "do_up_to_date_work.sh"

        def run_worker(extra_args: list[str]) -> list[str]:
            with tempfile.TemporaryDirectory() as temp_dir:
                fake_python = os.path.join(temp_dir, "python3")
                args_path = os.path.join(temp_dir, "args.txt")
                with open(fake_python, "w", encoding="utf-8") as output_file:
                    output_file.write(
                        "#!/usr/bin/env bash\n"
                        "printf '%s\\n' \"$@\" > \"$FORGE_TEST_ARGS_FILE\"\n"
                        "exit 1\n"
                    )
                os.chmod(fake_python, 0o755)
                environment = dict(os.environ)
                for variable in (
                        "FORGE_AGENT_FAMILY",
                        "FORGE_ANALYSIS_AGENT",
                        "FORGE_ANALYSIS_FAMILY",
                        "FORGE_ANALYSIS_MODEL",
                        "FORGE_SETUP_AGENT",
                        "FORGE_SETUP_FAMILY",
                        "FORGE_SETUP_MODEL",
                        "FORGE_SETUP_PROVIDER",
                ):
                    environment.pop(variable, None)
                environment.update({
                    "PYTHON_BIN": fake_python,
                    "FORGE_TEST_ARGS_FILE": args_path,
                })
                subprocess.run(
                    [str(worker_path), "--once", *extra_args],
                    env=environment,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    text=True,
                    check=False,
                )
                with open(args_path, encoding="utf-8") as input_file:
                    return input_file.read().splitlines()

        # Unset means the shared default, not the analysis setting.
        arguments = run_worker([])
        self.assertNotIn("--setup-agent", arguments)
        self.assertNotIn("--setup-family", arguments)
        self.assertNotIn("--setup-model", arguments)
        self.assertNotIn("--setup-provider", arguments)

        arguments = run_worker([
            "--setup-family", "pi",
            "--setup-agent", "my-pi",
            "--setup-model", "cheap-model",
            "--setup-provider", "openrouter",
        ])
        self.assertEqual(arguments[arguments.index("--setup-family") + 1], "pi")
        self.assertEqual(arguments[arguments.index("--setup-agent") + 1], "my-pi")
        self.assertEqual(arguments[arguments.index("--setup-model") + 1], "cheap-model")
        self.assertEqual(arguments[arguments.index("--setup-provider") + 1], "openrouter")

    def test_worker_only_forwards_options_the_gate_defines(self) -> None:
        """A stale forwarded option would exit the gate with code 2 before any work starts."""
        worker_path = Path(__file__).resolve().parents[1] / "do_up_to_date_work.sh"

        def gate_arguments(extra_args: list[str], extra_environment: dict[str, str]) -> list[str]:
            with tempfile.TemporaryDirectory() as temp_dir:
                fake_python = os.path.join(temp_dir, "python3")
                args_path = os.path.join(temp_dir, "args.txt")
                with open(fake_python, "w", encoding="utf-8") as output_file:
                    output_file.write(
                        "#!/usr/bin/env bash\n"
                        "printf '%s\\n' \"$@\" > \"$FORGE_TEST_ARGS_FILE\"\n"
                        "exit 1\n"
                    )
                os.chmod(fake_python, 0o755)
                environment = dict(os.environ)
                for variable in (
                        "FORGE_AGENT_FAMILY",
                        "FORGE_ANALYSIS_AGENT",
                        "FORGE_ANALYSIS_FAMILY",
                        "FORGE_ANALYSIS_MODEL",
                ):
                    environment.pop(variable, None)
                environment.update({
                    "PYTHON_BIN": fake_python,
                    "FORGE_TEST_ARGS_FILE": args_path,
                })
                environment.update(extra_environment)
                subprocess.run(
                    [str(worker_path), "--once", *extra_args],
                    env=environment,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    text=True,
                    check=False,
                )
                with open(args_path, encoding="utf-8") as input_file:
                    recorded = input_file.read().splitlines()
                self.assertTrue(recorded[0].endswith("host_requirements.py"), recorded[0])
                return recorded[1:]

        for extra_args, extra_environment in (
                ([], {}),
                (["--agent-family", "codex"], {}),
                ([], {"FORGE_ANALYSIS_MODEL": "gpt-5.6-luna"}),
                (["--setup-family", "pi", "--setup-model", "cheap-model"], {}),
        ):
            with self.subTest(extra_args=extra_args, extra_environment=extra_environment):
                parse_args(gate_arguments(extra_args, extra_environment))

    def test_worker_names_enabled_strategies_and_never_retargets_them(self) -> None:
        worker_path = Path(__file__).resolve().parents[1] / "do_up_to_date_work.sh"
        with tempfile.TemporaryDirectory() as temp_dir:
            fake_python = os.path.join(temp_dir, "python3")
            args_path = os.path.join(temp_dir, "args.txt")
            with open(fake_python, "w", encoding="utf-8") as output_file:
                output_file.write(
                    "#!/usr/bin/env bash\n"
                    "printf '%s\\n' \"$@\" > \"$FORGE_TEST_ARGS_FILE\"\n"
                    "exit 1\n"
                )
            os.chmod(fake_python, 0o755)
            environment = dict(os.environ)
            for variable in (
                    "FORGE_AGENT_FAMILY",
                    "FORGE_ANALYSIS_AGENT",
                    "FORGE_ANALYSIS_FAMILY",
                    "FORGE_ANALYSIS_MODEL",
            ):
                environment.pop(variable, None)
            environment.update({
                "PYTHON_BIN": fake_python,
                "FORGE_TEST_ARGS_FILE": args_path,
                "FORGE_WORK_LIMIT": "1",
                "FORGE_JAVAC_WORK_LIMIT": "1",
                "FORGE_JAVAC_STRATEGY_NAME": "dynamic_access_main_sources_codex_gpt-5.6-sol",
                "FORGE_JAVA_RUN_WORK_LIMIT": "0",
                "FORGE_NI_RUN_WORK_LIMIT": "0",
                "FORGE_LIBRARY_UPDATE_WORK_LIMIT": "0",
            })

            result = subprocess.run(
                [str(worker_path), "--once"],
                env=environment,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                check=False,
            )
            with open(args_path, encoding="utf-8") as input_file:
                arguments = input_file.read().splitlines()

        self.assertNotEqual(result.returncode, 0)
        # The strategy is the only thing that names a test-role backend.
        self.assertNotIn("--test-agent", arguments)
        self.assertNotIn("--test-family", arguments)
        self.assertNotIn("--test-model", arguments)
        self.assertNotIn("--test-provider", arguments)
        strategy_values = [
            arguments[index + 1]
            for index, argument in enumerate(arguments)
            if argument == "--test-strategy"
        ]
        self.assertEqual(
            strategy_values,
            [
                "optimistic_dynamic_access_iterative_pi_gpt-5.6-sol",
                "dynamic_access_main_sources_codex_gpt-5.6-sol",
            ],
        )

    def test_worker_family_only_selection_uses_default_executable(self) -> None:
        worker_path = Path(__file__).resolve().parents[1] / "do_up_to_date_work.sh"
        with tempfile.TemporaryDirectory() as temp_dir:
            fake_python = os.path.join(temp_dir, "python3")
            args_path = os.path.join(temp_dir, "args.txt")
            with open(fake_python, "w", encoding="utf-8") as output_file:
                output_file.write(
                    "#!/usr/bin/env bash\n"
                    "printf '%s\\n' \"$@\" > \"$FORGE_TEST_ARGS_FILE\"\n"
                    "exit 1\n"
                )
            os.chmod(fake_python, 0o755)
            environment = dict(os.environ)
            for variable in (
                    "FORGE_AGENT_FAMILY",
                    "FORGE_ANALYSIS_AGENT",
                    "FORGE_ANALYSIS_FAMILY",
                    "FORGE_ANALYSIS_MODEL",
            ):
                environment.pop(variable, None)
            environment.update({
                "PYTHON_BIN": fake_python,
                "FORGE_TEST_ARGS_FILE": args_path,
            })

            result = subprocess.run(
                [str(worker_path), "--once", "--analysis-family", "pi"],
                env=environment,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                check=False,
            )
            with open(args_path, encoding="utf-8") as input_file:
                arguments = input_file.read().splitlines()

        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(arguments[arguments.index("--analysis-family") + 1], "pi")
        self.assertEqual(arguments[arguments.index("--analysis-agent") + 1], "pi")


if __name__ == "__main__":
    unittest.main()
