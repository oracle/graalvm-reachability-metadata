# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Native test verification gate.

Composes the iterative trace loop in ``utility_scripts/native_metadata_exploration``
with the codex / Pi recovery cascade to assert that ``./gradlew nativeTest``
passes for a coordinate. See ``forge/docs/native-test-verification.md`` for
the full contract.
"""

from __future__ import annotations

import os
import re
import subprocess
from dataclasses import dataclass, field

from ai_workflows.fix_metadata_codex import run_codex_metadata_fix
from ai_workflows.fix_post_generation_pi import (
    DEFAULT_MAX_TEST_OUTPUT_CHARS,
    DEFAULT_PI_TIMEOUT_SECONDS,
    run_pi_post_generation_fix,
)
from utility_scripts.native_metadata_exploration import (
    NativeExplorationResult,
    run_native_metadata_exploration,
)
from utility_scripts.stage_logger import log_stage
from utility_scripts.task_logs import (
    build_timestamped_task_log_path,
    display_log_path,
    sanitize_library_log_segment,
)


STATUS_PASSED = "PASSED"
STATUS_PASSED_WITH_INTERVENTION = "PASSED_WITH_INTERVENTION"
STATUS_FAILED = "FAILED"

_GATE_STAGE = "native-test-verify"
_LOG_TASK_TYPE = "native-test-verify"


@dataclass
class InterventionRecord:
    """One recovery step that ran during a verification cycle."""

    stage: str  # which cycle index this happened in, e.g. "iter-3-codex"
    kind: str  # "codex" or "pi"
    log_path: str


@dataclass
class NativeTestVerificationResult:
    """Outcome of a ``verify_native_test_passes`` invocation."""

    status: str
    output_dir: str
    iterations_used: int
    last_native_test_log_path: str | None = None
    intervention_records: list[InterventionRecord] = field(default_factory=list)
    last_exploration: NativeExplorationResult | None = None


def verify_native_test_passes(
        reachability_repo_path: str,
        coordinate: str,
        output_dir: str,
        condition_packages: list[str] | None = None,
        max_iterations: int = 100,
        max_trace_iterations: int = 5,
        model_name: str | None = None,
        post_generation_timeout_seconds: int = DEFAULT_PI_TIMEOUT_SECONDS,
        post_generation_test_output_chars: int = DEFAULT_MAX_TEST_OUTPUT_CHARS,
) -> NativeTestVerificationResult:
    """Iteratively trace, verify ``nativeTest`` passes, and recover via codex/Pi.

    See ``forge/docs/native-test-verification.md`` for the full contract.
    """
    if max_iterations < 1:
        raise ValueError("max_iterations must be >= 1")
    if not os.path.isabs(output_dir):
        raise ValueError("output_dir must be an absolute path")

    log_stage(
        _GATE_STAGE,
        f"start coordinate={coordinate} output_dir={output_dir} budget={max_iterations}",
    )

    intervention_records: list[InterventionRecord] = []
    intervention_used = False
    last_native_test_log: str | None = None
    last_exploration: NativeExplorationResult | None = None

    def _make_result(status: str, iterations_used: int) -> NativeTestVerificationResult:
        return NativeTestVerificationResult(
            status=status,
            output_dir=output_dir,
            iterations_used=iterations_used,
            last_native_test_log_path=last_native_test_log,
            intervention_records=intervention_records,
            last_exploration=last_exploration,
        )

    for cycle in range(max_iterations):
        log_stage(_GATE_STAGE, f"cycle {cycle + 1}/{max_iterations}")

        last_exploration = run_native_metadata_exploration(
            reachability_repo_path=reachability_repo_path,
            coordinate=coordinate,
            output_dir=output_dir,
            condition_packages=condition_packages,
            max_iterations=max_trace_iterations,
            verify=False,
        )

        nt_log = _native_test_log_path(coordinate, cycle, "post-trace")
        nt_rc = _run_native_test(reachability_repo_path, coordinate, output_dir, nt_log)
        last_native_test_log = nt_log
        if nt_rc == 0:
            log_stage(_GATE_STAGE, f"cycle {cycle + 1}: nativeTest passed after trace")
            status = STATUS_PASSED_WITH_INTERVENTION if intervention_used else STATUS_PASSED
            return _make_result(status, cycle + 1)

        log_stage(
            _GATE_STAGE,
            f"cycle {cycle + 1}: nativeTest failed after trace; running codex",
        )
        codex_rc, codex_log_path, codex_timed_out = run_codex_metadata_fix(
            reachability_repo_path,
            coordinate,
            reproduction_command=_native_test_command(coordinate, output_dir),
        )
        intervention_records.append(
            InterventionRecord(
                stage=f"cycle-{cycle + 1}-codex",
                kind="codex",
                log_path=codex_log_path,
            )
        )

        recovery_test_output = _read_log(nt_log)
        if not codex_timed_out and codex_rc == 0:
            intervention_used = True
            nt_log = _native_test_log_path(coordinate, cycle, "post-codex")
            nt_rc = _run_native_test(reachability_repo_path, coordinate, output_dir, nt_log)
            last_native_test_log = nt_log
            if nt_rc == 0:
                log_stage(_GATE_STAGE, f"cycle {cycle + 1}: nativeTest passed after codex")
                return _make_result(STATUS_PASSED_WITH_INTERVENTION, cycle + 1)
            recovery_test_output = _read_log(nt_log)

        if model_name:
            log_stage(
                _GATE_STAGE,
                f"cycle {cycle + 1}: nativeTest failed after codex; running Pi",
            )
            pi_rc, pi_intervention_path, pi_timed_out = run_pi_post_generation_fix(
                reachability_metadata_path=reachability_repo_path,
                coordinates=coordinate,
                codex_log_path=codex_log_path,
                test_output=recovery_test_output,
                model_name=model_name,
                timeout_seconds=post_generation_timeout_seconds,
                max_test_output_chars=post_generation_test_output_chars,
            )
            intervention_records.append(
                InterventionRecord(
                    stage=f"cycle-{cycle + 1}-pi",
                    kind="pi",
                    log_path=pi_intervention_path,
                )
            )
            if not pi_timed_out and pi_rc == 0:
                intervention_used = True
                nt_log = _native_test_log_path(coordinate, cycle, "post-pi")
                nt_rc = _run_native_test(reachability_repo_path, coordinate, output_dir, nt_log)
                last_native_test_log = nt_log
                if nt_rc == 0:
                    log_stage(_GATE_STAGE, f"cycle {cycle + 1}: nativeTest passed after Pi")
                    return _make_result(STATUS_PASSED_WITH_INTERVENTION, cycle + 1)
        else:
            log_stage(
                _GATE_STAGE,
                f"cycle {cycle + 1}: skipping Pi fall-through (no model_name configured)",
            )

    log_stage(
        _GATE_STAGE,
        f"FAILED after {max_iterations} cycles; last log: {display_log_path(last_native_test_log) if last_native_test_log else 'none'}",
    )
    return _make_result(STATUS_FAILED, max_iterations)


def _native_test_command(coordinate: str, output_dir: str) -> str:
    return (
        f"./gradlew nativeTest -Pcoordinates={coordinate} "
        f"-PmetadataConfigDirs={output_dir}"
    )


def _run_native_test(
        reachability_repo_path: str,
        coordinate: str,
        output_dir: str,
        log_path: str,
) -> int:
    cmd = [
        "./gradlew",
        "nativeTest",
        f"-Pcoordinates={coordinate}",
        f"-PmetadataConfigDirs={output_dir}",
    ]
    log_stage(
        _GATE_STAGE,
        f"$ {' '.join(cmd)}  (log: {display_log_path(log_path)})",
        indent_level=1,
    )
    with open(log_path, "w", encoding="utf-8") as log_file:
        result = subprocess.run(
            cmd,
            cwd=reachability_repo_path,
            stdout=log_file,
            stderr=subprocess.STDOUT,
            check=False,
        )
    return result.returncode


def _native_test_log_path(coordinate: str, cycle_index: int, suffix: str) -> str:
    return build_timestamped_task_log_path(
        _LOG_TASK_TYPE,
        sanitize_library_log_segment(coordinate),
        f"cycle-{cycle_index}-nativeTest-{suffix}",
    )


def _read_log(path: str | None, limit: int = 64 * 1024) -> str:
    if not path:
        return ""
    try:
        with open(path, "r", encoding="utf-8") as handle:
            data = handle.read()
    except OSError:
        return ""
    if len(data) > limit:
        return data[-limit:]
    return data


def class_key_from_class_name(class_name: str) -> str:
    """Sanitize a Java class name for use as a per-class output-dir segment."""
    return re.sub(r"[^A-Za-z0-9_.-]", "_", class_name)


def per_class_output_dir(
        reachability_repo_path: str,
        group: str,
        artifact: str,
        version: str,
        class_name: str,
) -> str:
    """Return the per-class natively-collected output directory."""
    return os.path.join(
        reachability_repo_path,
        "tests",
        "src",
        group,
        artifact,
        version,
        "build",
        "natively-collected",
        class_key_from_class_name(class_name),
    )


def global_output_dir(
        reachability_repo_path: str,
        group: str,
        artifact: str,
        version: str,
) -> str:
    """Return the global (non-class-scoped) natively-collected output directory."""
    return os.path.join(
        reachability_repo_path,
        "tests",
        "src",
        group,
        artifact,
        version,
        "build",
        "natively-collected",
        "_global_",
    )
