# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Native test verification gate.

Drives ``./gradlew runNativeTraceImage`` per outer cycle. The trace binary is
built with ``-H:+MetadataTracingSupport`` (collects metadata) plus
``--exact-reachability-metadata`` and ``-H:MissingRegistrationReportingMode=Exit``
(causes ``ExitStatus.MISSING_METADATA`` 172 on missing metadata instead of
throwing). One run produces both signals: a trace dir and the binary's exit
code. The gate routes on that exit code:

- ``0``     → ``PASSED`` (or ``PASSED_WITH_INTERVENTION`` if codex ran in an
  earlier cycle).
- ``172``   → metadata gap; append the cycle's trace dir to the running
  ``metadataConfigDirs`` so the next cycle's build sees it, then continue.
- any other → run ``run_codex_metadata_fix`` once. Codex finishes it: on
  codex success return ``PASSED_WITH_INTERVENTION``; on codex failure return
  ``FAILED``. The gate does not re-verify after codex.

Pi is not invoked. Removing failing tests would mask code/test issues that
must surface to the coding agent. See
``forge/docs/native-test-verification.md`` for the full contract.
"""

from __future__ import annotations

import json
import os
import re
import shutil
import subprocess
from dataclasses import dataclass, field

from ai_workflows.fix_metadata_codex import run_codex_metadata_fix
from utility_scripts.stage_logger import log_stage
from utility_scripts.task_logs import (
    build_timestamped_task_log_path,
    display_log_path,
    sanitize_library_log_segment,
)


STATUS_PASSED = "PASSED"
STATUS_PASSED_WITH_INTERVENTION = "PASSED_WITH_INTERVENTION"
STATUS_FAILED = "FAILED"

# Exit code emitted by ExitStatus.MISSING_METADATA when the binary was built
# with -H:MissingRegistrationReportingMode=Exit and encounters missing
# reachability metadata at runtime.
MISSING_METADATA_EXIT_CODE = 172

_GATE_STAGE = "native-test-verify"
_LOG_TASK_TYPE = "native-test-verify"

# Gradle prints "...finished with non-zero exit value N" when the trace
# binary's Exec returns a non-zero exit. We recover the binary's exit code
# from this line because the Exec itself runs with ignoreExitValue=true and
# Gradle's own exit code does not include it.
_EXIT_VALUE_PATTERN = re.compile(r"exit\s+value\s+(\d+)", re.IGNORECASE)
_TRACE_SENTINEL_FILE_NAMES = frozenset({"binary-exit-code"})
_AGGREGATED_METADATA_FILE_NAME = "reachability-metadata.json"
_FAILURE_LOG_TAIL_LINE_LIMIT = 300


@dataclass
class InterventionRecord:
    """One codex run that took place inside the gate."""

    stage: str
    kind: str  # "codex"
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


DEFAULT_CYCLE_TIMEOUT_SECONDS = 30 * 60


def verify_native_test_passes(
        reachability_repo_path: str,
        coordinate: str,
        output_dir: str,
        condition_packages: list[str] | None = None,
        max_iterations: int = 100,
        cycle_timeout_seconds: int = DEFAULT_CYCLE_TIMEOUT_SECONDS,
) -> NativeTestVerificationResult:
    """Iteratively run runNativeTraceImage until the binary passes or fails.

    See ``forge/docs/native-test-verification.md`` for the full contract.
    """
    if max_iterations < 1:
        raise ValueError("max_iterations must be >= 1")
    if not os.path.isabs(output_dir):
        raise ValueError("output_dir must be an absolute path")

    group = coordinate.split(":", 1)[0]
    packages = list(condition_packages) if condition_packages else [group]
    condition_packages_arg = ",".join(packages)

    runs_dir = os.path.normpath(os.path.join(output_dir, "..", "runs"))
    _reset_directory(output_dir)
    _reset_directory(runs_dir)

    log_stage(
        _GATE_STAGE,
        f"start coordinate={coordinate} output_dir={output_dir} budget={max_iterations}",
    )

    accepted_run_dirs: list[str] = []
    intervention_records: list[InterventionRecord] = []
    intervention_used = False
    last_log_path: str | None = None
    last_binary_rc: int | None = None

    def _make_result(status: str, iterations_used: int) -> NativeTestVerificationResult:
        return NativeTestVerificationResult(
            status=status,
            output_dir=output_dir,
            iterations_used=iterations_used,
            last_native_test_log_path=last_log_path,
            last_native_test_exit_code=last_binary_rc,
            accepted_run_dirs=list(accepted_run_dirs),
            intervention_records=intervention_records,
        )

    for cycle in range(max_iterations):
        log_stage(_GATE_STAGE, f"cycle {cycle + 1}/{max_iterations}")

        run_dir = os.path.join(runs_dir, f"cycle-{cycle}")
        os.makedirs(run_dir, exist_ok=True)
        log_path = _gate_log_path(coordinate, cycle, "runNativeTraceImage")
        gradle_rc, binary_rc = _run_native_trace_image(
            reachability_repo_path=reachability_repo_path,
            coordinate=coordinate,
            run_dir=run_dir,
            condition_packages_arg=condition_packages_arg,
            metadata_config_dirs=accepted_run_dirs,
            log_path=log_path,
            timeout_seconds=cycle_timeout_seconds,
        )
        last_log_path = log_path
        last_binary_rc = binary_rc if binary_rc is not None else gradle_rc
        _print_collected_metadata(run_dir, cycle + 1)
        collected_metadata_files = _metadata_files(run_dir)
        usable_metadata_files = _usable_metadata_files(run_dir)

        if binary_rc == 0:
            log_stage(
                _GATE_STAGE,
                f"cycle {cycle + 1}: binary passed (exit 0); merging trace dirs",
            )
            run_dirs_to_merge = list(accepted_run_dirs)
            if usable_metadata_files:
                run_dirs_to_merge.append(run_dir)
            if not _merge_into_output(
                reachability_repo_path=reachability_repo_path,
                run_dirs=run_dirs_to_merge,
                output_dir=output_dir,
            ):
                return _make_result(STATUS_FAILED, cycle + 1)
            status = STATUS_PASSED_WITH_INTERVENTION if intervention_used else STATUS_PASSED
            return _make_result(status, cycle + 1)

        if binary_rc == MISSING_METADATA_EXIT_CODE:
            if not usable_metadata_files:
                log_stage(
                    _GATE_STAGE,
                    f"cycle {cycle + 1}: binary exited 172 but produced no usable trace metadata; failing fast",
                )
                _print_failure_log_tail(log_path, cycle + 1)
                return _make_result(STATUS_FAILED, cycle + 1)
            log_stage(
                _GATE_STAGE,
                f"cycle {cycle + 1}: binary exited 172 (missing metadata); "
                f"adding {os.path.basename(run_dir)} to config_dirs",
            )
            accepted_run_dirs.append(run_dir)
            continue

        log_stage(
            _GATE_STAGE,
            f"cycle {cycle + 1}: binary failed (gradle_exit={gradle_rc}, binary_exit={binary_rc}); "
            "routing to codex (terminal)",
        )
        if not collected_metadata_files:
            _print_failure_log_tail(log_path, cycle + 1)
        codex_rc, codex_log_path, codex_timed_out = run_codex_metadata_fix(
            reachability_repo_path,
            coordinate,
            reproduction_command=_run_native_trace_image_command(
                coordinate=coordinate,
                run_dir=run_dir,
                condition_packages_arg=condition_packages_arg,
                metadata_config_dirs=accepted_run_dirs,
            ),
        )
        intervention_records.append(
            InterventionRecord(
                stage=f"cycle-{cycle + 1}-codex",
                kind="codex",
                log_path=codex_log_path,
            )
        )
        if codex_timed_out or codex_rc != 0:
            log_stage(
                _GATE_STAGE,
                f"codex did not converge (timed_out={codex_timed_out}, rc={codex_rc}); FAILED",
            )
            return _make_result(STATUS_FAILED, cycle + 1)
        log_stage(_GATE_STAGE, "codex finished; trusting codex's outcome")
        return _make_result(STATUS_PASSED_WITH_INTERVENTION, cycle + 1)

    log_stage(
        _GATE_STAGE,
        f"FAILED after {max_iterations} cycles "
        f"(metadata-gap-exhausted; last binary_exit={last_binary_rc})",
    )
    return _make_result(STATUS_FAILED, max_iterations)


def _run_native_trace_image_command(
        coordinate: str,
        run_dir: str,
        condition_packages_arg: str,
        metadata_config_dirs: list[str],
) -> str:
    parts = [
        "./gradlew runNativeTraceImage",
        f"-Pcoordinates={coordinate}",
        f"-PtraceMetadataPath={run_dir}",
        f"-PtraceMetadataConditionPackages={condition_packages_arg}",
    ]
    if metadata_config_dirs:
        parts.append(f"-PmetadataConfigDirs={','.join(metadata_config_dirs)}")
    return " ".join(parts)


def _run_native_trace_image(
        reachability_repo_path: str,
        coordinate: str,
        run_dir: str,
        condition_packages_arg: str,
        metadata_config_dirs: list[str],
        log_path: str,
        timeout_seconds: int = DEFAULT_CYCLE_TIMEOUT_SECONDS,
) -> tuple[int, int | None]:
    """Run ``runNativeTraceImage`` and surface the binary's exit code.

    Returns ``(gradle_rc, binary_rc)``. ``binary_rc`` is read from the
    sentinel file written by the Gradle task (``-PexitFile=<path>``); the
    sentinel mechanism is preferred because it is independent of Gradle's
    log wording. When the sentinel is missing (e.g. the build failed before
    the run task fired) we fall back to the legacy "exit value N"
    log-scrape — see ``_parse_binary_exit_code``. ``binary_rc`` is
    ``None`` when neither source produced a value.
    """
    exit_file = os.path.join(run_dir, "binary-exit-code")
    cmd = [
        "./gradlew",
        "runNativeTraceImage",
        f"-Pcoordinates={coordinate}",
        f"-PtraceMetadataPath={run_dir}",
        f"-PtraceMetadataConditionPackages={condition_packages_arg}",
        f"-PtraceBinaryExitFile={exit_file}",
    ]
    if metadata_config_dirs:
        cmd.append(f"-PmetadataConfigDirs={','.join(metadata_config_dirs)}")
    log_stage(
        _GATE_STAGE,
        f"$ {' '.join(cmd)}  (log: {display_log_path(log_path)})",
        indent_level=1,
    )
    with open(log_path, "w", encoding="utf-8") as log_file:
        try:
            result = subprocess.run(
                cmd,
                cwd=reachability_repo_path,
                stdout=log_file,
                stderr=subprocess.STDOUT,
                check=False,
                timeout=timeout_seconds,
            )
        except subprocess.TimeoutExpired:
            log_stage(
                _GATE_STAGE,
                f"runNativeTraceImage exceeded {timeout_seconds}s timeout",
                indent_level=1,
            )
            return 1, None
    binary_rc = _read_exit_file(exit_file)
    if binary_rc is None:
        binary_rc = _parse_binary_exit_code(log_path)
    if binary_rc is None and result.returncode == 0:
        # Gradle exited 0 and neither the sentinel nor the log mentions an
        # exit value; the binary must have returned 0.
        binary_rc = 0
    return result.returncode, binary_rc


def _print_collected_metadata(run_dir: str, cycle_number: int) -> None:
    """Print the trace metadata collected in ``run_dir`` for one gate cycle."""
    metadata_files = _metadata_files(run_dir)
    if not metadata_files:
        log_stage(
            _GATE_STAGE,
            f"cycle {cycle_number}: collected metadata: none ({run_dir})",
            indent_level=1,
        )
        return

    log_stage(
        _GATE_STAGE,
        f"cycle {cycle_number}: collected metadata from {len(metadata_files)} file(s) ({run_dir})",
        indent_level=1,
    )
    for metadata_file in metadata_files:
        relative = os.path.relpath(metadata_file, run_dir)
        log_stage(_GATE_STAGE, f"{relative}:", indent_level=2)
        for line in _format_metadata_file(metadata_file).splitlines():
            log_stage(_GATE_STAGE, line, indent_level=3)


def _print_failure_log_tail(log_path: str, cycle_number: int) -> None:
    """Print the native trace failure log tail."""
    log_stage(
        _GATE_STAGE,
        f"cycle {cycle_number}: failure log tail (last {_FAILURE_LOG_TAIL_LINE_LIMIT} lines) from {log_path}:",
        indent_level=1,
    )
    for line in _extract_failure_log_tail(log_path).splitlines():
        log_stage(_GATE_STAGE, line, indent_level=2)


def _extract_failure_log_tail(log_path: str) -> str:
    """Extract a bounded tail from a native trace run log."""
    try:
        with open(log_path, "r", encoding="utf-8", errors="replace") as handle:
            lines = handle.read().splitlines()
    except OSError as exc:
        return f"<unable to read log: {exc}>"

    if not lines:
        return "<empty log>"

    excerpt = lines[-_FAILURE_LOG_TAIL_LINE_LIMIT:]
    if not excerpt:
        return "<no log excerpt available>"
    return "\n".join(excerpt)


def _metadata_files(run_dir: str) -> list[str]:
    """Return trace metadata files below ``run_dir`` in deterministic order."""
    result: list[str] = []
    for root, _dirs, files in os.walk(run_dir):
        for name in files:
            if name in _TRACE_SENTINEL_FILE_NAMES:
                continue
            result.append(os.path.join(root, name))
    return sorted(result, key=lambda path: os.path.relpath(path, run_dir))


def _usable_metadata_files(run_dir: str) -> list[str]:
    """Return trace metadata files that contain at least one metadata entry."""
    return [path for path in _metadata_files(run_dir) if _metadata_file_has_entries(path)]


def _metadata_file_has_entries(path: str) -> bool:
    """Return true when ``path`` contains usable trace metadata."""
    try:
        with open(path, "r", encoding="utf-8") as handle:
            data = json.load(handle)
    except (json.JSONDecodeError, UnicodeDecodeError):
        try:
            return os.path.getsize(path) > 0
        except OSError:
            return False
    except OSError:
        return False
    return _json_has_entries(data)


def _json_has_entries(value: object) -> bool:
    """Return true when a parsed JSON value contains a non-empty entry."""
    if isinstance(value, dict):
        return any(_json_has_entries(child) for child in value.values())
    if isinstance(value, list):
        return any(_json_has_entries(child) for child in value)
    return value is not None


def _format_metadata_file(path: str) -> str:
    """Return a readable representation of one collected metadata file."""
    try:
        with open(path, "r", encoding="utf-8") as handle:
            data = json.load(handle)
    except (json.JSONDecodeError, UnicodeDecodeError):
        return _read_text_or_binary_summary(path)
    except OSError as exc:
        return f"<unreadable: {exc}>"
    return json.dumps(data, indent=2, sort_keys=True)


def _read_text_or_binary_summary(path: str) -> str:
    """Return text content for UTF-8 files, or a compact summary for binary files."""
    try:
        with open(path, "rb") as handle:
            raw = handle.read()
    except OSError as exc:
        return f"<unreadable: {exc}>"
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError:
        return f"<binary metadata file: {len(raw)} bytes>"
    return text if text else "<empty>"


def _read_exit_file(path: str) -> int | None:
    """Read the binary exit code written by the Gradle task to ``path``."""
    try:
        with open(path, "r", encoding="utf-8") as handle:
            text = handle.read().strip()
    except OSError:
        return None
    if not text:
        return None
    try:
        return int(text)
    except ValueError:
        return None


def _parse_binary_exit_code(log_path: str) -> int | None:
    """Fallback: recover the binary exit code from Gradle's "exit value N" line.

    The sentinel file written by the Gradle task is the primary source; this
    parser is kept for the case where the sentinel was not written (e.g. the
    build failed before the run step). The regex matches Gradle's stable
    failure message format; if Gradle ever changes its wording, the
    sentinel path still works.
    """
    try:
        with open(log_path, "r", encoding="utf-8", errors="replace") as handle:
            content = handle.read()
    except OSError:
        return None
    matches = _EXIT_VALUE_PATTERN.findall(content)
    if not matches:
        return None
    try:
        return int(matches[-1])
    except ValueError:
        return None


_MERGE_TIMEOUT_SECONDS = 5 * 60


def _merge_into_output(
        reachability_repo_path: str,
        run_dirs: list[str],
        output_dir: str,
) -> bool:
    """Merge accepted per-cycle trace dirs into the caller's ``output_dir``."""
    if not run_dirs:
        return True
    cmd = [
        "./gradlew",
        "mergeNativeTraceMetadata",
        f"-PinputDirs={','.join(run_dirs)}",
        f"-PoutputDir={output_dir}",
    ]
    log_stage(_GATE_STAGE, f"$ {' '.join(cmd)}", indent_level=1)
    try:
        result = subprocess.run(
            cmd,
            cwd=reachability_repo_path,
            check=False,
            timeout=_MERGE_TIMEOUT_SECONDS,
        )
        if result.returncode != 0:
            log_stage(
                _GATE_STAGE,
                f"mergeNativeTraceMetadata failed with exit code {result.returncode}",
                indent_level=1,
            )
            return False
        _print_aggregated_metadata_path(output_dir)
        return True
    except subprocess.TimeoutExpired:
        log_stage(
            _GATE_STAGE,
            f"mergeNativeTraceMetadata exceeded {_MERGE_TIMEOUT_SECONDS}s timeout",
            indent_level=1,
        )
        return False


def _print_aggregated_metadata_path(output_dir: str) -> None:
    """Print only the path to the merged reachability metadata file."""
    log_stage(
        _GATE_STAGE,
        os.path.join(output_dir, _AGGREGATED_METADATA_FILE_NAME),
        indent_level=1,
    )


def _reset_directory(path: str) -> None:
    if os.path.exists(path):
        shutil.rmtree(path)
    os.makedirs(path, exist_ok=True)


def _gate_log_path(coordinate: str, cycle_index: int, suffix: str) -> str:
    return build_timestamped_task_log_path(
        _LOG_TASK_TYPE,
        sanitize_library_log_segment(coordinate),
        f"cycle-{cycle_index}-{suffix}",
    )


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
