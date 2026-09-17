# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Native test verification gate.

The gate first tries the normal JVM ``native-image-agent`` metadata collection
(``generateMetadata``). If that succeeds, it validates the coordinate with
``./gradlew test``. Native tracing is the fallback when JVM-agent metadata
generation fails or when native testing still fails after that metadata exists.
The configured analysis agent is the terminal repair path when the fallback
cannot converge.

This module implements the gate of §FS-native-test-verification-gate; see
``forge/docs/functional-spec/workflows/native-metadata-tracing.md`` for the full contract.
Command execution, condition-package derivation, and metadata staging live in
``native_trace_execution``, ``native_trace_conditions``, and
``native_trace_metadata``.
"""

from __future__ import annotations

import os
from dataclasses import dataclass, field

from ai_workflows.agents.agent_runtime import analysis_agent_run, get_analysis_agent
from utility_scripts.gradle_environment import gradle_command_environment
from utility_scripts.native_trace_conditions import _default_condition_packages
from utility_scripts.native_trace_execution import (
    DEFAULT_CYCLE_TIMEOUT_SECONDS,
    _GATE_STAGE,
    _LOG_TASK_TYPE,
    _extract_failure_log_tail,
    _gate_log_path,
    _run_coordinate_test,
    _run_generate_metadata,
    _run_native_trace_image,
    _run_native_trace_image_command,
    _summarize_gradle_failure_reason,
)
from utility_scripts.native_trace_metadata import (
    global_output_dir,
    per_class_output_dir,
    _existing_metadata_dirs,
    _finalize_staged_metadata,
    _merge_into_output,
    _metadata_entries,
    _metadata_files,
    _print_collected_metadata,
    _print_failure_log_tail,
    _print_metadata_progress,
    _reset_directory,
    _usable_metadata_files,
)
from utility_scripts.repo_path_resolver import require_complete_reachability_repo
from utility_scripts.run_location import (
    PHASE_STEPS,
    STEP_AGENT_FIX,
    RunLocation,
    current_run_location,
    log_step_progress,
    run_step,
)
from utility_scripts.stage_logger import log_detail
from utility_scripts.task_logs import display_log_path


STATUS_PASSED = "PASSED"
STATUS_PASSED_WITH_INTERVENTION = "PASSED_WITH_INTERVENTION"
STATUS_FAILED = "FAILED"

# Exit code emitted by ExitStatus.MISSING_METADATA when the binary was built
# with -H:MissingRegistrationReportingMode=Exit and encounters missing
# reachability metadata at runtime.
MISSING_METADATA_EXIT_CODE = 172


@dataclass
class InterventionRecord:
    """One analysis-agent run that took place inside the gate."""

    stage: str
    kind: str
    log_path: str


@dataclass
class NativeTestVerificationResult:
    """Outcome of a ``verify_native_test_passes`` invocation."""

    status: str
    output_dir: str
    iterations_used: int
    last_native_test_log_path: str | None = None
    last_native_test_exit_code: int | None = None
    accepted_run_dirs: list[str] = field(default_factory=list)
    intervention_records: list[InterventionRecord] = field(default_factory=list)
    failure_detail: str | None = None
    failure_log_path: str | None = None


DEFAULT_MAX_ITERATIONS = 40

# Quick analysis-agent fixup used as the terminal gate recovery.
_NATIVE_TEST_FIX_TIMEOUT_SECONDS = 30 * 60


def run_native_test_fix(
        repo_path: str,
        coordinates: str,
        reproduction_command: str,
        env: dict[str, str],
        graalvm_home: str | None = None,
        failure_log_path: str | None = None,
) -> tuple[int, str, bool, str | None]:
    """Run a quick analysis-agent fixup for a failing native test.

    Returns ``(return_code, log_path, timed_out, failure_message)``. The
    reproduction command and GraalVM home are pinned to the environment that
    produced the failed native run so the repair uses the exact same distribution.
    """
    prompt = _build_native_test_fix_prompt(
        coordinates, reproduction_command, graalvm_home, failure_log_path
    )
    result = analysis_agent_run(
        working_dir=repo_path,
        context=prompt,
        task_type=_LOG_TASK_TYPE,
        library=coordinates,
        timeout=_NATIVE_TEST_FIX_TIMEOUT_SECONDS,
        environment=env,
    )
    if result.return_code != 0:
        print(
            f"ERROR: Native-test fix failed for {coordinates}. "
            f"See {display_log_path(result.log_path)}.",
        )
    return (
        result.return_code,
        result.log_path,
        result.timed_out,
        result.failure_message,
    )


def _build_native_test_fix_prompt(
        coordinates: str,
        reproduction_command: str,
        graalvm_home: str | None,
        failure_log_path: str | None,
) -> str:
    """Build the diagnose-first prompt for the quick native-test fixup."""
    lines = [
        "Reproduce the failure first and read the FULL stack trace, including every `Caused by:`",
        "line, to find the real cause before changing anything.",
        "- If the cause is missing or inactive-condition for metadata, fix that condition.",
        "- If the cause is native-image-unsupported behavior (dynamic class loading, runtime bytecode",
        "  or class definition, runtime lambda definition, URL/plugin/OSGi class-loader assumptions,",
        "  or a class reachable only through a custom class loader), Remove the",
        "  generated test that exercises it, or rewrite it to a native-compatible public-API path that",
        "  still validates metadata.",
        "Re-run the reproduce command after each change and keep running until the test passes.",
        "Do not use any skill for this."
    ]
    if graalvm_home:
        lines += [
            "",
            "Use this exact GraalVM for every command; do not switch to another that appears on PATH:",
            f"- GRAALVM_HOME={graalvm_home}",
            f"- JAVA_HOME={graalvm_home}",
        ]
    lines += [
        "",
        "Reproduce with:",
        reproduction_command,
    ]
    if failure_log_path:
        lines += [
            "",
            "Failure output excerpt:",
            "```text",
            _extract_failure_log_tail(failure_log_path),
            "```",
        ]
    return "\n".join(lines)


def verify_native_test_passes(
        reachability_repo_path: str,
        coordinate: str,
        output_dir: str,
        condition_packages: list[str] | None = None,
        max_iterations: int = DEFAULT_MAX_ITERATIONS,
        cycle_timeout_seconds: int = DEFAULT_CYCLE_TIMEOUT_SECONDS,
        env: dict[str, str] | None = None,
        gradle_properties: tuple[str, ...] = (),
) -> NativeTestVerificationResult:
    """Try JVM-agent metadata first, then use native tracing as fallback.

    The public entry for §FS-native-test-verification-gate: it stages agent and
    trace metadata outside durable repository metadata, finalizes only after a
    passing validation path, and invokes the analysis agent at most once as the
    terminal repair step. A caller-supplied environment selects the exact image
    mode and GraalVM lane used by every command in the gate, and
    ``gradle_properties`` rides on every Gradle command and every reproduction
    command so a caller that widens the test source set repairs exactly the
    build that failed (§FS-native-test-verification-gate.2).
    """
    require_complete_reachability_repo(reachability_repo_path)
    if max_iterations < 1:
        raise ValueError("max_iterations must be >= 1")
    if not os.path.isabs(output_dir):
        raise ValueError("output_dir must be an absolute path")
    command_env = gradle_command_environment(reachability_repo_path, env)
    required_graalvm_home = command_env.get("GRAALVM_HOME")

    runs_dir = os.path.normpath(os.path.join(output_dir, "..", "runs"))
    _reset_directory(output_dir)
    _reset_directory(runs_dir)
    agent_metadata_dir = os.path.join(output_dir, "agent")
    trace_metadata_dir = os.path.join(output_dir, "trace")
    trace_condition_packages: list[str] | None = list(condition_packages) if condition_packages else None

    log_detail(
        _GATE_STAGE,
        f"start coordinate={coordinate} output_dir={output_dir} budget={max_iterations}",
    )

    accepted_run_dirs: list[str] = []
    accepted_metadata_entries: set[str] = set()
    intervention_records: list[InterventionRecord] = []
    last_log_path: str | None = None
    last_binary_rc: int | None = None

    def _make_result(
            status: str,
            iterations_used: int,
            failure_detail: str | None = None,
            failure_log_path: str | None = None,
    ) -> NativeTestVerificationResult:
        return NativeTestVerificationResult(
            status=status,
            output_dir=output_dir,
            iterations_used=iterations_used,
            last_native_test_log_path=last_log_path,
            last_native_test_exit_code=last_binary_rc,
            accepted_run_dirs=list(accepted_run_dirs),
            intervention_records=intervention_records,
            failure_detail=failure_detail,
            failure_log_path=failure_log_path,
        )

    def _route_to_analysis_agent(
            stage: str,
            reason: str,
            reproduction_command: str,
            iterations_used: int,
    ) -> NativeTestVerificationResult:
        """Run the analysis agent as the terminal recovery path for gate failures.

        Per §FS-native-test-verification-gate, the gate preserves accepted
        metadata dirs and pins the same GraalVM environment that produced the
        failed native command.
        """
        analysis_backend = get_analysis_agent(command_env).backend
        log_detail(
            _GATE_STAGE,
            f"{stage}: {reason}; routing to the analysis agent (terminal)",
        )

        def _run_agent_fix(location: RunLocation | None) -> NativeTestVerificationResult:
            if location is not None:
                log_step_progress(
                    location.phase,
                    STEP_AGENT_FIX,
                    f"Running native-trace agent fix for {coordinate}: {reason}",
                )
            require_complete_reachability_repo(reachability_repo_path)
            fix_rc, fix_log_path, fix_timed_out, fix_failure_message = run_native_test_fix(
                reachability_repo_path,
                coordinate,
                reproduction_command=reproduction_command,
                env=command_env,
                graalvm_home=required_graalvm_home,
                failure_log_path=last_log_path,
            )
            intervention_records.append(
                InterventionRecord(
                    stage=f"{stage}-analysis-agent",
                    kind=analysis_backend,
                    log_path=fix_log_path,
                )
            )
            if fix_timed_out or fix_rc != 0:
                failure_detail = fix_failure_message or (
                    f"{analysis_backend} agent failed with exit code {fix_rc}"
                )
                if location is not None:
                    log_step_progress(
                        location.phase,
                        STEP_AGENT_FIX,
                        f"Native-trace agent fix failed for {coordinate}: {failure_detail} "
                        f"(log: {display_log_path(fix_log_path)})",
                    )
                log_detail(
                    _GATE_STAGE,
                    f"{analysis_backend} did not converge "
                    f"(timed_out={fix_timed_out}, rc={fix_rc}); FAILED",
                )
                return _make_result(
                    STATUS_FAILED,
                    iterations_used,
                    failure_detail=failure_detail,
                    failure_log_path=fix_log_path,
                )
            require_complete_reachability_repo(reachability_repo_path)
            if location is not None:
                log_step_progress(
                    location.phase,
                    STEP_AGENT_FIX,
                    f"Native-trace agent fix completed for {coordinate}",
                )
            log_detail(
                _GATE_STAGE,
                f"{analysis_backend} exited successfully; PASSED_WITH_INTERVENTION",
            )
            return _make_result(STATUS_PASSED_WITH_INTERVENTION, iterations_used)

        active_location = current_run_location()
        if (
                active_location is not None
                and STEP_AGENT_FIX in PHASE_STEPS[active_location.phase]
        ):
            with run_step(active_location.phase, STEP_AGENT_FIX, operand=coordinate):
                return _run_agent_fix(active_location)
        return _run_agent_fix(None)

    def _finalize_and_verify_durable_metadata(
            metadata_dirs: list[str],
            iterations_used: int,
            stage: str,
    ) -> NativeTestVerificationResult | None:
        """Finalize staged metadata and verify the durable merged form."""
        nonlocal last_log_path
        if not _finalize_staged_metadata(
            reachability_repo_path=reachability_repo_path,
            coordinate=coordinate,
            metadata_dirs=metadata_dirs,
            env=command_env,
        ):
            return _make_result(STATUS_FAILED, iterations_used)

        finalized_test_log_path = _gate_log_path(coordinate, iterations_used, "finalizedTest")
        test_rc, failed_task = _run_coordinate_test(
            reachability_repo_path=reachability_repo_path,
            coordinate=coordinate,
            metadata_config_dirs=[],
            log_path=finalized_test_log_path,
            timeout_seconds=cycle_timeout_seconds,
            env=command_env,
            gradle_properties=gradle_properties,
        )
        last_log_path = finalized_test_log_path
        if test_rc == 0:
            log_detail(_GATE_STAGE, "finalized durable metadata passed native tests")
            return None

        failed_task_display = failed_task or "unknown"
        return _route_to_analysis_agent(
            stage=f"{stage}-finalized-test",
            reason=(
                "finalized durable metadata failed coordinate test "
                f"(failed_task={failed_task_display}, exit={test_rc})"
            ),
            reproduction_command=_coordinate_test_command(
                coordinate, gradle_properties=gradle_properties,
            ),
            iterations_used=iterations_used,
        )

    generate_metadata_log_path = _gate_log_path(coordinate, 0, "generateMetadata")
    generate_metadata_rc = _run_generate_metadata(
        reachability_repo_path=reachability_repo_path,
        coordinate=coordinate,
        output_dir=agent_metadata_dir,
        log_path=generate_metadata_log_path,
        env=command_env,
        gradle_properties=gradle_properties,
    )
    last_log_path = generate_metadata_log_path
    agent_metadata_dirs = [agent_metadata_dir] if generate_metadata_rc == 0 else []
    if generate_metadata_rc != 0:
        failure_reason = _summarize_gradle_failure_reason(generate_metadata_log_path)
        log_detail(
            _GATE_STAGE,
            f"generateMetadata failed (exit={generate_metadata_rc}); starting native trace fallback",
        )
        log_detail(_GATE_STAGE, f"generateMetadata failure reason: {failure_reason}", indent_level=1)
    else:
        test_log_path = _gate_log_path(coordinate, 0, "test")
        test_rc, failed_task = _run_coordinate_test(
            reachability_repo_path=reachability_repo_path,
            coordinate=coordinate,
            metadata_config_dirs=[agent_metadata_dir],
            log_path=test_log_path,
            timeout_seconds=cycle_timeout_seconds,
            env=command_env,
            gradle_properties=gradle_properties,
        )
        last_log_path = test_log_path
        if test_rc == 0:
            log_detail(_GATE_STAGE, "JVM-agent metadata made native tests pass")
            finalized_result = _finalize_and_verify_durable_metadata(
                [agent_metadata_dir],
                0,
                "jvm-agent",
            )
            if finalized_result is not None:
                return finalized_result
            return _make_result(STATUS_PASSED, 0)
        if failed_task != "nativeTest":
            failed_task_display = failed_task or "unknown"
            return _route_to_analysis_agent(
                stage="test",
                reason=f"test failed before native trace fallback (failed_task={failed_task_display}, exit={test_rc})",
                reproduction_command=_coordinate_test_command(
                    coordinate, [agent_metadata_dir], gradle_properties=gradle_properties,
                ),
                iterations_used=0,
            )

        log_detail(_GATE_STAGE, "nativeTest still fails after JVM-agent metadata; starting native trace fallback")

    if trace_condition_packages is None:
        trace_condition_packages = _default_condition_packages(reachability_repo_path, coordinate)
    log_detail(
        _GATE_STAGE,
        f"trace condition packages: {','.join(trace_condition_packages)}",
        indent_level=1,
    )

    for cycle in range(max_iterations):
        log_detail(_GATE_STAGE, f"cycle {cycle + 1}/{max_iterations}")

        run_dir = os.path.join(runs_dir, f"cycle-{cycle}")
        os.makedirs(run_dir, exist_ok=True)
        log_path = _gate_log_path(coordinate, cycle, "runNativeTraceImage")
        gradle_rc, binary_rc = _run_native_trace_image(
            reachability_repo_path=reachability_repo_path,
            coordinate=coordinate,
            run_dir=run_dir,
            condition_packages=trace_condition_packages,
            metadata_config_dirs=_existing_metadata_dirs(agent_metadata_dirs + accepted_run_dirs),
            log_path=log_path,
            timeout_seconds=cycle_timeout_seconds,
            env=command_env,
            gradle_properties=gradle_properties,
        )
        last_log_path = log_path
        last_binary_rc = binary_rc if binary_rc is not None else gradle_rc
        _print_collected_metadata(run_dir, cycle + 1)
        collected_metadata_files = _metadata_files(run_dir)
        usable_metadata_files = _usable_metadata_files(run_dir)

        if binary_rc == 0:
            log_detail(
                _GATE_STAGE,
                f"cycle {cycle + 1}: binary passed (exit 0); merging trace dirs",
            )
            run_dirs_to_merge = list(accepted_run_dirs)
            if usable_metadata_files:
                run_dirs_to_merge.append(run_dir)
            if not _merge_into_output(
                reachability_repo_path=reachability_repo_path,
                run_dirs=run_dirs_to_merge,
                output_dir=trace_metadata_dir,
                env=command_env,
            ):
                return _make_result(STATUS_FAILED, cycle + 1)
            finalized_result = _finalize_and_verify_durable_metadata(
                agent_metadata_dirs + [trace_metadata_dir],
                cycle + 1,
                f"cycle-{cycle + 1}",
            )
            if finalized_result is not None:
                return finalized_result
            return _make_result(STATUS_PASSED, cycle + 1)

        if binary_rc == MISSING_METADATA_EXIT_CODE:
            if not usable_metadata_files:
                log_detail(
                    _GATE_STAGE,
                    f"cycle {cycle + 1}: binary exited 172 but produced no usable trace metadata",
                )
                _print_failure_log_tail(log_path, cycle + 1)
                return _route_to_analysis_agent(
                    stage=f"cycle-{cycle + 1}",
                    reason="binary exited 172 but produced no usable trace metadata",
                    reproduction_command=_run_native_trace_image_command(
                        coordinate=coordinate,
                        run_dir=run_dir,
                        condition_packages=trace_condition_packages,
                        metadata_config_dirs=_existing_metadata_dirs(agent_metadata_dirs + accepted_run_dirs),
                        gradle_properties=gradle_properties,
                    ),
                    iterations_used=cycle + 1,
                )
            metadata_entries = _metadata_entries(run_dir)
            added_entries = metadata_entries - accepted_metadata_entries
            if not added_entries:
                _print_metadata_progress(
                    accepted_run_dirs=accepted_run_dirs,
                    accepted_entry_count=len(accepted_metadata_entries),
                    current_entry_count=len(metadata_entries),
                    reason="no new trace metadata entries",
                )
                _print_failure_log_tail(log_path, cycle + 1)
                return _route_to_analysis_agent(
                    stage=f"cycle-{cycle + 1}",
                    reason="binary exited 172 without new trace metadata",
                    reproduction_command=_run_native_trace_image_command(
                        coordinate=coordinate,
                        run_dir=run_dir,
                        condition_packages=trace_condition_packages,
                        metadata_config_dirs=_existing_metadata_dirs(agent_metadata_dirs + accepted_run_dirs),
                        gradle_properties=gradle_properties,
                    ),
                    iterations_used=cycle + 1,
                )
            log_detail(
                _GATE_STAGE,
                f"cycle {cycle + 1}: binary exited 172 (missing metadata); "
                f"adding {os.path.basename(run_dir)} to config_dirs "
                f"({len(added_entries)} new entr{'y' if len(added_entries) == 1 else 'ies'})",
            )
            accepted_metadata_entries.update(added_entries)
            accepted_run_dirs.append(run_dir)
            continue

        if not collected_metadata_files:
            _print_failure_log_tail(log_path, cycle + 1)
        return _route_to_analysis_agent(
            stage=f"cycle-{cycle + 1}",
            reason=f"binary failed (gradle_exit={gradle_rc}, binary_exit={binary_rc})",
            reproduction_command=_run_native_trace_image_command(
                coordinate=coordinate,
                run_dir=run_dir,
                condition_packages=trace_condition_packages,
                metadata_config_dirs=_existing_metadata_dirs(agent_metadata_dirs + accepted_run_dirs),
                gradle_properties=gradle_properties,
            ),
            iterations_used=cycle + 1,
        )

    log_detail(
        _GATE_STAGE,
        f"FAILED after {max_iterations} cycles "
        f"(metadata-gap-exhausted; last binary_exit={last_binary_rc})",
    )
    codex_reproduction_run_dir = os.path.join(
        runs_dir,
        "codex-repro-metadata-gap-exhausted",
    )
    return _route_to_analysis_agent(
        stage="metadata-gap-exhausted",
        reason=f"metadata gap exhausted after {max_iterations} cycles",
        reproduction_command=_run_native_trace_image_command(
            coordinate=coordinate,
            run_dir=codex_reproduction_run_dir,
            condition_packages=trace_condition_packages,
            metadata_config_dirs=_existing_metadata_dirs(agent_metadata_dirs + accepted_run_dirs),
            gradle_properties=gradle_properties,
        ),
        iterations_used=max_iterations,
    )


def _coordinate_test_command(
        coordinate: str,
        metadata_config_dirs: list[str] | None = None,
        gradle_properties: tuple[str, ...] = (),
) -> str:
    parts = ["./gradlew test", f"-Pcoordinates={coordinate}", *gradle_properties]
    if metadata_config_dirs:
        parts.append(f"-PmetadataConfigDirs={','.join(metadata_config_dirs)}")
    return " ".join(parts)
