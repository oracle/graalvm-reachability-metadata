# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Unit tests for the native-test verification gate driver.

Exercises the analysis-agent fixup prompt and the gate's outer-loop routing
via ``subprocess.run`` mocks. The Gradle / native-image side is intentionally
not exercised here.
"""

from __future__ import annotations

import io
import os
import subprocess
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest.mock import Mock, patch

# Tests run from the forge/ directory in CI; make the package imports work
# whether the test is invoked via pytest or `python -m unittest`.
_FORGE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(_FORGE_ROOT))

from utility_scripts import native_test_verification as ntv  # noqa: E402
from utility_scripts.run_location import (  # noqa: E402
    PHASE_EXPLORE,
    STEP_NATIVE_TRACE_GATE,
    run_step,
)

from tests.native_gate_support import (  # noqa: E402
    GATE_SUBPROCESS_RUN,
    GateHarness,
    _command_property,
    _rmtree,
    _write_user_code_filter,
)


class NativeTestFixPromptTests(unittest.TestCase):

    def test_preserves_the_original_reproduce_and_verify_prompt(self) -> None:
        prompt = ntv._build_native_test_fix_prompt(
            coordinates="g:a:1.0",
            reproduction_command="./gradlew reproduce",
            graalvm_home="/graalvm",
            failure_log_path=None,
        )

        self.assertEqual(
            prompt,
            "\n".join([
                "Reproduce the failure first and read the FULL stack trace, including every `Caused by:`",
                "line, to find the real cause before changing anything.",
                "- If the cause is missing or inactive-condition for metadata, fix that condition.",
                "- If the cause is native-image-unsupported behavior (dynamic class loading, runtime bytecode",
                "  or class definition, runtime lambda definition, URL/plugin/OSGi class-loader assumptions,",
                "  or a class reachable only through a custom class loader), Remove the",
                "  generated test that exercises it, or rewrite it to a native-compatible public-API path that",
                "  still validates metadata.",
                "Re-run the reproduce command after each change and keep running until the test passes.",
                "Do not use any skill for this.",
                "",
                "Use this exact GraalVM for every command; do not switch to another that appears on PATH:",
                "- GRAALVM_HOME=/graalvm",
                "- JAVA_HOME=/graalvm",
                "",
                "Reproduce with:",
                "./gradlew reproduce",
            ]),
        )

    def test_runs_the_central_analysis_agent_without_overrides(self) -> None:
        environment = {"FORGE_ANALYSIS_AGENT": "pi"}
        agent_result = Mock(return_code=0, log_path="/tmp/analysis.log", timed_out=False, failure_message=None)

        with patch(
                "utility_scripts.native_test_verification.analysis_agent_run",
                return_value=agent_result,
        ) as analysis_run:
            result = ntv.run_native_test_fix(
                repo_path="/repo",
                coordinates="g:a:1.0",
                reproduction_command="./gradlew reproduce",
                env=environment,
            )

        self.assertEqual(result, (0, "/tmp/analysis.log", False, None))
        call_kwargs = analysis_run.call_args.kwargs
        self.assertEqual(call_kwargs["environment"], environment)
        self.assertNotIn("model", call_kwargs)
        self.assertNotIn("selection", call_kwargs)


class GateRoutingTests(GateHarness):
    """End-to-end routing: 0 → PASSED, 172 → continue, other → codex."""

    def test_passes_after_jvm_agent_metadata_when_coordinate_test_passes(self) -> None:
        fake, calls = self._fake_run_factory([], test_rc=0, test_failed_task=None)
        with patch(GATE_SUBPROCESS_RUN, side_effect=fake):
            result = ntv.verify_native_test_passes(
                reachability_repo_path=self.repo,
                coordinate="g:a:1.0",
                output_dir=self.output_dir,
                max_iterations=5,
            )
        self.assertEqual(result.status, ntv.STATUS_PASSED)
        self.assertEqual(result.iterations_used, 0)
        self.assertIsNone(result.last_native_test_exit_code)
        self.assertTrue(any("generateMetadata" in c for c in calls))
        self.assertTrue(any("test" in c for c in calls))
        self.assertFalse(any("runNativeTraceImage" in c for c in calls))
        test_calls = [call for call in calls if "test" in call]
        self.assertEqual(len(test_calls), 2)
        self.assertTrue(any(arg.startswith("-PmetadataConfigDirs=") for arg in test_calls[0]))
        self.assertFalse(any(arg.startswith("-PmetadataConfigDirs=") for arg in test_calls[1]))

    def test_preflight_coordinate_test_uses_gate_timeout(self) -> None:
        observed_test_timeouts: list[int | None] = []

        def _fake_run(cmd, **kwargs):  # type: ignore[no-untyped-def]
            if "test" in cmd:
                observed_test_timeouts.append(kwargs.get("timeout"))
            return subprocess.CompletedProcess(cmd, 0)

        with patch(GATE_SUBPROCESS_RUN, side_effect=_fake_run):
            result = ntv.verify_native_test_passes(
                reachability_repo_path=self.repo,
                coordinate="g:a:1.0",
                output_dir=self.output_dir,
                max_iterations=5,
                cycle_timeout_seconds=17,
            )
        self.assertEqual(result.status, ntv.STATUS_PASSED)
        self.assertEqual(observed_test_timeouts, [17, 17])

    def test_caller_environment_is_used_for_every_gate_command(self) -> None:
        fake, _calls = self._fake_run_factory([], test_rc=0, test_failed_task=None)
        caller_env = {"GVM_TCK_NATIVE_IMAGE_MODE": "future-defaults-all"}
        command_env = {**caller_env, "GRADLE_USER_HOME": "/tmp/gradle-home"}

        with patch(
            "utility_scripts.native_test_verification.gradle_command_environment",
            return_value=command_env,
        ) as build_environment, patch(
            GATE_SUBPROCESS_RUN,
            side_effect=fake,
        ) as run:
            result = ntv.verify_native_test_passes(
                reachability_repo_path=self.repo,
                coordinate="g:a:1.0",
                output_dir=self.output_dir,
                max_iterations=5,
                env=caller_env,
            )

        self.assertEqual(result.status, ntv.STATUS_PASSED)
        build_environment.assert_called_once_with(self.repo, caller_env)
        self.assertTrue(run.call_args_list)
        self.assertTrue(all(call.kwargs["env"] is command_env for call in run.call_args_list))

    def test_routes_to_codex_when_finalized_jvm_agent_metadata_fails(self) -> None:
        fake, calls = self._fake_run_factory(
            [],
            test_rc=0,
            test_failed_task=None,
            finalized_test_rc=1,
            finalized_test_failed_task="nativeTest",
        )
        with patch(GATE_SUBPROCESS_RUN, side_effect=fake), patch(
            "utility_scripts.native_test_verification.run_native_test_fix",
            return_value=(0, "/tmp/codex.log", False, None),
        ) as codex_mock:
            result = ntv.verify_native_test_passes(
                reachability_repo_path=self.repo,
                coordinate="g:a:1.0",
                output_dir=self.output_dir,
                max_iterations=5,
            )

        self.assertEqual(result.status, ntv.STATUS_PASSED_WITH_INTERVENTION)
        codex_mock.assert_called_once()
        reproduction_command = codex_mock.call_args.kwargs["reproduction_command"]
        self.assertEqual(reproduction_command, "./gradlew test -Pcoordinates=g:a:1.0")
        test_calls = [call for call in calls if "test" in call]
        self.assertEqual(len(test_calls), 2)
        self.assertFalse(any("runNativeTraceImage" in call for call in calls))

    def test_continues_on_172_until_pass(self) -> None:
        fake, calls = self._fake_run_factory([172, 172, 0])
        with patch(GATE_SUBPROCESS_RUN, side_effect=fake):
            result = ntv.verify_native_test_passes(
                reachability_repo_path=self.repo,
                coordinate="g:a:1.0",
                output_dir=self.output_dir,
                max_iterations=5,
            )
        self.assertEqual(result.status, ntv.STATUS_PASSED)
        self.assertEqual(result.iterations_used, 3)
        self.assertEqual(len(result.accepted_run_dirs), 2)
        trace_calls = [call for call in calls if "runNativeTraceImage" in call]
        self.assertTrue(trace_calls)
        self.assertIn("-PtraceMetadataConditionPackages=g", trace_calls[0])

    def test_trace_fallback_uses_user_code_filter_condition_packages(self) -> None:
        _write_user_code_filter(
            self.repo,
            "org.apache.tomcat.embed:tomcat-embed-core:11.0.18",
            [
                {"excludeClasses": "**"},
                {"includeClasses": "org.apache.catalina.**"},
                {"includeClasses": "org.apache.tomcat.**"},
                {"includeClasses": "tomcat.**"},
            ],
        )
        test_source = Path(
            self.repo,
            "tests",
            "src",
            "org.apache.tomcat.embed",
            "tomcat-embed-core",
            "11.0.18",
            "src",
            "test",
            "java",
            "tomcat",
            "BootstrapTest.java",
        )
        test_source.parent.mkdir(parents=True, exist_ok=True)
        test_source.write_text("package tomcat;\nclass BootstrapTest {}\n", encoding="utf-8")
        fake, calls = self._fake_run_factory([172, 0])

        with patch(GATE_SUBPROCESS_RUN, side_effect=fake):
            result = ntv.verify_native_test_passes(
                reachability_repo_path=self.repo,
                coordinate="org.apache.tomcat.embed:tomcat-embed-core:11.0.18",
                output_dir=self.output_dir,
                max_iterations=5,
            )

        self.assertEqual(result.status, ntv.STATUS_PASSED)
        trace_calls = [call for call in calls if "runNativeTraceImage" in call]
        self.assertTrue(trace_calls)
        trace_condition_arg = next(
            arg for arg in trace_calls[0]
            if arg.startswith("-PtraceMetadataConditionPackages=")
        )
        self.assertEqual(
            trace_condition_arg,
            "-PtraceMetadataConditionPackages=org.apache.catalina,org.apache.tomcat",
        )

    def test_routes_to_codex_and_prints_stacktrace_when_172_produces_no_metadata(self) -> None:
        fake, _calls = self._fake_run_factory(
            [172],
            metadata_exit_codes=set(),
            log_text=(
                "some native output\n"
                "Exception in thread \"main\" com.example.MissingThingException: boom\n"
                "\tat com.example.App.main(App.java:12)\n"
            ),
        )
        output = io.StringIO()
        with patch(
            GATE_SUBPROCESS_RUN,
            side_effect=fake,
        ), patch(
            "utility_scripts.native_test_verification.run_native_test_fix",
            return_value=(0, "/tmp/codex.log", False, None),
        ), redirect_stdout(output):
            result = ntv.verify_native_test_passes(
                reachability_repo_path=self.repo,
                coordinate="g:a:1.0",
                output_dir=self.output_dir,
                max_iterations=5,
            )
        self.assertEqual(result.status, ntv.STATUS_PASSED_WITH_INTERVENTION)
        self.assertEqual(result.iterations_used, 1)
        printed = output.getvalue()
        self.assertIn("produced no usable trace metadata", printed)
        self.assertIn("com.example.MissingThingException: boom", printed)

    def test_routes_to_codex_when_172_produces_empty_metadata_json(self) -> None:
        def _fake_run(cmd, **kwargs):  # type: ignore[no-untyped-def]
            stdout = kwargs.get("stdout")
            if hasattr(stdout, "write"):
                stdout.write(
                    "\n".join(f"native log line {index}" for index in range(305)) +
                    "\ncom.oracle.svm.core.jdk.resources.MissingResourceRegistrationError: missing resource\n"
                )
            if "generateMetadata" in cmd:
                return subprocess.CompletedProcess(cmd, 0)
            if "test" in cmd:
                if hasattr(stdout, "write"):
                    stdout.write("> Task :nativeTest FAILED\n")
                return subprocess.CompletedProcess(cmd, 1)
            if "runNativeTraceImage" in cmd:
                exit_file = next(
                    (a.split("=", 1)[1] for a in cmd if a.startswith("-PtraceBinaryExitFile=")),
                    None,
                )
                run_dir = next(
                    (a.split("=", 1)[1] for a in cmd if a.startswith("-PtraceMetadataPath=")),
                    None,
                )
                if exit_file:
                    Path(exit_file).parent.mkdir(parents=True, exist_ok=True)
                    Path(exit_file).write_text(str(ntv.MISSING_METADATA_EXIT_CODE), encoding="utf-8")
                if run_dir:
                    Path(run_dir).mkdir(parents=True, exist_ok=True)
                    Path(run_dir, "reachability-metadata.json").write_text("{}", encoding="utf-8")
                return subprocess.CompletedProcess(cmd, 0)
            return subprocess.CompletedProcess(cmd, 0)

        output = io.StringIO()
        with patch(
            GATE_SUBPROCESS_RUN,
            side_effect=_fake_run,
        ), patch(
            "utility_scripts.native_test_verification.run_native_test_fix",
            return_value=(0, "/tmp/codex.log", False, None),
        ), redirect_stdout(output):
            result = ntv.verify_native_test_passes(
                reachability_repo_path=self.repo,
                coordinate="g:a:1.0",
                output_dir=self.output_dir,
                max_iterations=5,
            )
        self.assertEqual(result.status, ntv.STATUS_PASSED_WITH_INTERVENTION)
        self.assertEqual(result.iterations_used, 1)
        printed = output.getvalue()
        self.assertIn("collected metadata from 1 file(s)", printed)
        self.assertNotIn("reachability-metadata.json:", printed)
        self.assertNotIn("{}", printed)
        self.assertIn("produced no usable trace metadata", printed)
        self.assertIn("failure log tail (last 20 lines)", printed)
        self.assertNotIn("native log line 285\n", printed)
        self.assertIn("native log line 286\n", printed)
        self.assertIn("MissingResourceRegistrationError: missing resource", printed)

    def test_routes_to_codex_when_budget_exhausted_with_only_172(self) -> None:
        fake, _calls = self._fake_run_factory([172, 172])
        with patch(GATE_SUBPROCESS_RUN, side_effect=fake), patch(
            "utility_scripts.native_test_verification.run_native_test_fix",
            return_value=(0, "/tmp/codex.log", False, None),
        ) as codex_mock:
            result = ntv.verify_native_test_passes(
                reachability_repo_path=self.repo,
                coordinate="g:a:1.0",
                output_dir=self.output_dir,
                max_iterations=2,
            )
        self.assertEqual(result.status, ntv.STATUS_PASSED_WITH_INTERVENTION)
        self.assertEqual(result.iterations_used, 2)
        self.assertEqual(result.last_native_test_exit_code, ntv.MISSING_METADATA_EXIT_CODE)
        self.assertEqual(len(result.intervention_records), 1)
        reproduction_command = codex_mock.call_args.kwargs["reproduction_command"]
        trace_path = _command_property(reproduction_command, "-PtraceMetadataPath")
        config_dirs = _command_property(reproduction_command, "-PmetadataConfigDirs").split(",")
        self.assertIn("codex-repro-metadata-gap-exhausted", trace_path)
        self.assertNotIn(trace_path, config_dirs)

    def test_falls_back_to_native_trace_when_generate_metadata_fails(self) -> None:
        fake, calls = self._fake_run_factory(
            [0],
            generate_metadata_rc=1,
            log_text=(
                "FAILURE: Build failed with an exception.\n"
                "Caused by: java.lang.InternalError: platform encoding not initialized\n"
            ),
        )
        output = io.StringIO()
        with patch(GATE_SUBPROCESS_RUN, side_effect=fake), patch(
            "utility_scripts.native_test_verification.run_native_test_fix",
            return_value=(0, "/tmp/codex.log", False, None),
        ) as codex_mock, redirect_stdout(output):
            result = ntv.verify_native_test_passes(
                reachability_repo_path=self.repo,
                coordinate="g:a:1.0",
                output_dir=self.output_dir,
                max_iterations=5,
            )
        self.assertEqual(result.status, ntv.STATUS_PASSED)
        codex_mock.assert_not_called()
        self.assertTrue(any("generateMetadata" in c for c in calls))
        test_calls = [call for call in calls if "test" in call]
        self.assertEqual(len(test_calls), 1)
        self.assertFalse(any(arg.startswith("-PmetadataConfigDirs=") for arg in test_calls[0]))
        self.assertTrue(any("runNativeTraceImage" in c for c in calls))
        first_trace_call = next(c for c in calls if "runNativeTraceImage" in c)
        self.assertFalse(any(c.startswith("-PmetadataConfigDirs=") for c in first_trace_call))
        self.assertIn(
            "generateMetadata failure reason: Caused by: java.lang.InternalError: platform encoding not initialized",
            output.getvalue(),
        )

    def test_routes_to_codex_after_native_trace_failure_when_generate_metadata_fails(self) -> None:
        fake, calls = self._fake_run_factory([1], generate_metadata_rc=1)
        with patch(GATE_SUBPROCESS_RUN, side_effect=fake), patch(
            "utility_scripts.native_test_verification.run_native_test_fix",
            return_value=(0, "/tmp/codex.log", False, None),
        ) as codex_mock:
            result = ntv.verify_native_test_passes(
                reachability_repo_path=self.repo,
                coordinate="g:a:1.0",
                output_dir=self.output_dir,
                max_iterations=5,
            )
        self.assertEqual(result.status, ntv.STATUS_PASSED_WITH_INTERVENTION)
        codex_mock.assert_called_once()
        self.assertTrue(any("runNativeTraceImage" in c for c in calls))

    def test_routes_to_codex_when_test_fails_before_native_test(self) -> None:
        fake, calls = self._fake_run_factory([], test_rc=1, test_failed_task="compileTestJava")
        with patch(GATE_SUBPROCESS_RUN, side_effect=fake), patch(
            "utility_scripts.native_test_verification.run_native_test_fix",
            return_value=(0, "/tmp/codex.log", False, None),
        ) as codex_mock:
            result = ntv.verify_native_test_passes(
                reachability_repo_path=self.repo,
                coordinate="g:a:1.0",
                output_dir=self.output_dir,
                max_iterations=5,
            )
        self.assertEqual(result.status, ntv.STATUS_PASSED_WITH_INTERVENTION)
        codex_mock.assert_called_once()
        self.assertFalse(any("runNativeTraceImage" in c for c in calls))

    def test_analysis_agent_success_is_terminal_without_gate_rerun(self) -> None:
        fake, calls = self._fake_run_factory([1])
        with patch(
                GATE_SUBPROCESS_RUN,
                side_effect=fake,
        ), patch(
            "utility_scripts.native_test_verification.run_native_test_fix",
            return_value=(0, "/tmp/codex.log", False, None),
        ) as analysis_mock:
            result = ntv.verify_native_test_passes(
                reachability_repo_path=self.repo,
                coordinate="g:a:1.0",
                output_dir=self.output_dir,
                max_iterations=5,
            )
        self.assertEqual(result.status, ntv.STATUS_PASSED_WITH_INTERVENTION)
        analysis_mock.assert_called_once()
        self.assertEqual(result.last_native_test_exit_code, 1)
        self.assertEqual(len([call for call in calls if "runNativeTraceImage" in call]), 1)
        self.assertEqual(len(result.intervention_records), 1)
        self.assertEqual(result.intervention_records[0].kind, "codex")

    def test_concise_output_names_the_native_trace_agent_fix(self) -> None:
        fake, _calls = self._fake_run_factory([1])
        output = io.StringIO()
        with patch.dict(
                os.environ,
                {"FORGE_VERBOSE": "0", "FORGE_DEBUG_LOGGING": "0"},
        ), patch(
                GATE_SUBPROCESS_RUN,
                side_effect=fake,
        ), patch(
                "utility_scripts.native_test_verification.run_native_test_fix",
                return_value=(0, "/tmp/codex.log", False, None),
        ), redirect_stdout(output):
            with run_step(
                    PHASE_EXPLORE,
                    STEP_NATIVE_TRACE_GATE,
                    operand="g:a:1.0",
            ):
                result = ntv.verify_native_test_passes(
                    reachability_repo_path=self.repo,
                    coordinate="g:a:1.0",
                    output_dir=self.output_dir,
                    max_iterations=5,
                )

        self.assertEqual(result.status, ntv.STATUS_PASSED_WITH_INTERVENTION)
        printed = output.getvalue()
        self.assertIn(
            "[explore] Running native-trace agent fix for g:a:1.0:",
            printed,
        )
        self.assertIn(
            "[explore] Native-trace agent fix completed for g:a:1.0 (3/3)",
            printed,
        )
        self.assertNotIn("[native-test-verify]", printed)
        self.assertNotIn("./gradlew", printed)

    def test_concise_agent_fix_failure_keeps_error_and_log(self) -> None:
        fake, _calls = self._fake_run_factory([1])
        output = io.StringIO()
        with patch.dict(
                os.environ,
                {"FORGE_VERBOSE": "0", "FORGE_DEBUG_LOGGING": "0"},
        ), patch(
                GATE_SUBPROCESS_RUN,
                side_effect=fake,
        ), patch(
                "utility_scripts.native_test_verification.run_native_test_fix",
                return_value=(2, "/tmp/codex.log", False, "Agent repair failed"),
        ), redirect_stdout(output):
            with run_step(
                    PHASE_EXPLORE,
                    STEP_NATIVE_TRACE_GATE,
                    operand="g:a:1.0",
            ):
                result = ntv.verify_native_test_passes(
                    reachability_repo_path=self.repo,
                    coordinate="g:a:1.0",
                    output_dir=self.output_dir,
                    max_iterations=5,
                )

        self.assertEqual(result.status, ntv.STATUS_FAILED)
        self.assertIn(
            "[explore] Native-trace agent fix failed for g:a:1.0: "
            "Agent repair failed (log: ../../codex.log) (3/3)",
            output.getvalue(),
        )

    def test_routes_to_codex_with_same_graalvm_home_as_gate_commands(self) -> None:
        graalvm_home = tempfile.mkdtemp(prefix="gate-graalvm-")
        self.addCleanup(_rmtree, graalvm_home)
        Path(graalvm_home, "bin").mkdir()
        Path(graalvm_home, "bin", "native-image").write_text("", encoding="utf-8")
        fake, _calls = self._fake_run_factory([1])
        with patch.dict(os.environ, {"GRAALVM_HOME": graalvm_home, "JAVA_HOME": "/plain-jdk"}, clear=True), patch(
                GATE_SUBPROCESS_RUN,
                side_effect=fake,
        ), patch(
            "utility_scripts.native_test_verification.run_native_test_fix",
            return_value=(0, "/tmp/codex.log", False, None),
        ) as codex_mock:
            result = ntv.verify_native_test_passes(
                reachability_repo_path=self.repo,
                coordinate="g:a:1.0",
                output_dir=self.output_dir,
                max_iterations=5,
            )

        self.assertEqual(result.status, ntv.STATUS_PASSED_WITH_INTERVENTION)
        self.assertEqual(codex_mock.call_args.kwargs["graalvm_home"], graalvm_home)
        self.assertEqual(codex_mock.call_args.kwargs["env"]["GRAALVM_HOME"], graalvm_home)
        self.assertEqual(codex_mock.call_args.kwargs["env"]["JAVA_HOME"], graalvm_home)

    def test_failed_when_codex_does_not_converge(self) -> None:
        fake, _calls = self._fake_run_factory([1])
        with patch(
                GATE_SUBPROCESS_RUN,
                side_effect=fake,
        ), patch(
            "utility_scripts.native_test_verification.run_native_test_fix",
            return_value=(2, "/tmp/codex.log", False, "Agent repair failed"),
        ):
            result = ntv.verify_native_test_passes(
                reachability_repo_path=self.repo,
                coordinate="g:a:1.0",
                output_dir=self.output_dir,
                max_iterations=5,
            )
        self.assertEqual(result.status, ntv.STATUS_FAILED)
        self.assertEqual(result.failure_detail, "Agent repair failed")
        self.assertEqual(result.failure_log_path, "/tmp/codex.log")


if __name__ == "__main__":
    unittest.main()
