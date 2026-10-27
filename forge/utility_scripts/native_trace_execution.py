# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Gradle command execution and log parsing for the native-test verification gate.

Runs the gate's Gradle commands (``generateMetadata``, ``test``,
``runNativeTraceImage``), recovers binary exit codes, and summarizes Gradle
failure logs for §FS-native-test-verification-gate routing decisions.
"""

from __future__ import annotations

import os
import re
import subprocess

from utility_scripts.gradle_environment import gradle_command_environment
from utility_scripts.stage_logger import log_detail, log_stage
from utility_scripts.task_logs import (
    build_timestamped_task_log_path,
    display_log_path,
    sanitize_library_log_segment,
)

GATE_STAGE = "native-test-verify"
LOG_TASK_TYPE = "native-test-verify"

# Gradle prints "...finished with non-zero exit value N" when the trace
# binary's Exec returns a non-zero exit. We recover the binary's exit code
# from this line because the Exec itself runs with ignoreExitValue=true and
# Gradle's own exit code does not include it.
_EXIT_VALUE_PATTERN = re.compile(r"exit\s+value\s+(\d+)", re.IGNORECASE)
FAILURE_LOG_TAIL_LINE_LIMIT = 20
_FAILED_TASK_PATTERN = re.compile(r"> Task :(\S+) FAILED")

DEFAULT_CYCLE_TIMEOUT_SECONDS = 30 * 60


def run_generate_metadata(
        reachability_repo_path: str,
        coordinate: str,
        output_dir: str,
        log_path: str,
        env: dict[str, str],
        gradle_properties: tuple[str, ...] = (),
) -> int:
    """Run JVM-agent metadata generation for the coordinate into a staging dir.

    Always the gate's first metadata action, before any native tracing
    (§FS-native-test-verification-gate).
    """
    cmd = [
        "./gradlew",
        "generateMetadata",
        f"-Pcoordinates={coordinate}",
        *gradle_properties,
        "--agentAllowedPackages=fromJar",
        f"--metadataOutputDir={output_dir}",
    ]
    return run_logged_gradle_command(
        reachability_repo_path=reachability_repo_path,
        cmd=cmd,
        log_path=log_path,
        env=env,
    ).returncode


def run_coordinate_test(
        reachability_repo_path: str,
        coordinate: str,
        metadata_config_dirs: list[str],
        log_path: str,
        timeout_seconds: int,
        env: dict[str, str],
        gradle_properties: tuple[str, ...] = (),
) -> tuple[int, str | None]:
    """Run normal coordinate tests and return the first failed Gradle task."""
    cmd = ["./gradlew", "test", f"-Pcoordinates={coordinate}", *gradle_properties]
    if metadata_config_dirs:
        cmd.append(f"-PmetadataConfigDirs={','.join(metadata_config_dirs)}")
    result = run_logged_gradle_command(
        reachability_repo_path=reachability_repo_path,
        cmd=cmd,
        log_path=log_path,
        timeout_seconds=timeout_seconds,
        env=env,
    )
    return result.returncode, _parse_first_failed_task(log_path)


def run_logged_gradle_command(
        reachability_repo_path: str,
        cmd: list[str],
        log_path: str,
        timeout_seconds: int | None = None,
        env: dict[str, str] | None = None,
) -> subprocess.CompletedProcess:
    """Run a Gradle command and write stdout/stderr to ``log_path``."""
    log_detail(
        GATE_STAGE,
        f"$ {' '.join(cmd)}  (log: {display_log_path(log_path)})",
        indent_level=1,
    )
    with open(log_path, "w", encoding="utf-8") as log_file:
        try:
            return subprocess.run(
                cmd,
                cwd=reachability_repo_path,
                env=env or gradle_command_environment(reachability_repo_path),
                stdout=log_file,
                stderr=subprocess.STDOUT,
                check=False,
                timeout=timeout_seconds,
            )
        except subprocess.TimeoutExpired:
            log_file.write(f"\nCommand exceeded {timeout_seconds}s timeout\n")
            return subprocess.CompletedProcess(cmd, 1)


def _parse_first_failed_task(log_path: str) -> str | None:
    """Extract the first Gradle task name that failed from a log file."""
    try:
        with open(log_path, "r", encoding="utf-8", errors="replace") as handle:
            content = handle.read()
    except OSError:
        return None
    match = _FAILED_TASK_PATTERN.search(content)
    return match.group(1) if match else None


def summarize_gradle_failure_reason(log_path: str) -> str:
    """Extract a short, human-readable Gradle failure reason from a log."""
    try:
        with open(log_path, "r", encoding="utf-8", errors="replace") as handle:
            lines = [line.strip() for line in handle.readlines()]
    except OSError as exc:
        return f"unable to read log: {exc}"

    non_empty_lines = [line for line in lines if line]
    if not non_empty_lines:
        return "log is empty"

    priority_patterns = [
        "Could not find agent library",
        "platform encoding not initialized",
        "FATAL ERROR in native method",
        "Error occurred during initialization of VM",
        "MissingReflectionRegistrationError",
        "MissingResourceRegistrationError",
        "MissingSerializationRegistrationError",
        "MissingJNIRegistrationError",
        "MissingProxyRegistrationError",
        "Caused by:",
        "Execution failed for task",
        "Cannot generate metadata",
        "finished with non-zero exit value",
    ]
    for pattern in priority_patterns:
        for line in non_empty_lines:
            if pattern in line:
                return _trim_failure_reason(line)

    failed_task = _FAILED_TASK_PATTERN.search("\n".join(non_empty_lines))
    if failed_task:
        return f"task {failed_task.group(1)} failed"

    for line in non_empty_lines:
        if line.startswith("ERROR:") or line.startswith("error:"):
            return _trim_failure_reason(line)

    fallback_lines = [
        line for line in non_empty_lines
        if not line.startswith("BUILD SUCCESSFUL")
        and not line.startswith("Deprecated Gradle features were used")
    ]
    if fallback_lines:
        return _trim_failure_reason(fallback_lines[-1])
    return f"no specific failure reason found in {display_log_path(log_path)}"


def _trim_failure_reason(reason: str, max_length: int = 240) -> str:
    """Keep failure summaries compact enough for one log line."""
    if len(reason) <= max_length:
        return reason
    return reason[:max_length - 3].rstrip() + "..."


def run_native_trace_image_command(
        coordinate: str,
        run_dir: str,
        condition_packages: list[str],
        metadata_config_dirs: list[str],
        gradle_properties: tuple[str, ...] = (),
) -> str:
    parts = [
        "./gradlew runNativeTraceImage",
        f"-Pcoordinates={coordinate}",
        *gradle_properties,
        f"-PtraceMetadataPath={run_dir}",
        f"-PtraceMetadataConditionPackages={','.join(condition_packages)}",
    ]
    if metadata_config_dirs:
        parts.append(f"-PmetadataConfigDirs={','.join(metadata_config_dirs)}")
    return " ".join(parts)


def run_native_trace_image(
        reachability_repo_path: str,
        coordinate: str,
        run_dir: str,
        condition_packages: list[str],
        metadata_config_dirs: list[str],
        log_path: str,
        timeout_seconds: int = DEFAULT_CYCLE_TIMEOUT_SECONDS,
        env: dict[str, str] | None = None,
        gradle_properties: tuple[str, ...] = (),
) -> tuple[int, int | None]:
    """Run ``runNativeTraceImage`` and surface the binary's exit code.

    Drives the ``runNativeTraceImage`` task per its contract
    (§FS-native-test-verification-gate.5) and recovers the exact-metadata-aware exit
    code the gate routes on (§FS-native-test-verification-gate).

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
        *gradle_properties,
        f"-PtraceMetadataPath={run_dir}",
        f"-PtraceMetadataConditionPackages={','.join(condition_packages)}",
        f"-PtraceBinaryExitFile={exit_file}",
    ]
    if metadata_config_dirs:
        cmd.append(f"-PmetadataConfigDirs={','.join(metadata_config_dirs)}")
    log_detail(
        GATE_STAGE,
        f"$ {' '.join(cmd)}  (log: {display_log_path(log_path)})",
        indent_level=1,
    )
    with open(log_path, "w", encoding="utf-8") as log_file:
        try:
            result = subprocess.run(
                cmd,
                cwd=reachability_repo_path,
                env=env or gradle_command_environment(reachability_repo_path),
                stdout=log_file,
                stderr=subprocess.STDOUT,
                check=False,
                timeout=timeout_seconds,
            )
        except subprocess.TimeoutExpired:
            log_stage(
                GATE_STAGE,
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


def extract_failure_log_tail(log_path: str) -> str:
    """Extract a bounded tail from a native trace run log."""
    try:
        with open(log_path, "r", encoding="utf-8", errors="replace") as handle:
            lines = handle.read().splitlines()
    except OSError as exc:
        return f"<unable to read log: {exc}>"

    if not lines:
        return "<empty log>"

    excerpt = lines[-FAILURE_LOG_TAIL_LINE_LIMIT:]
    if not excerpt:
        return "<no log excerpt available>"
    return "\n".join(excerpt)


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


def gate_log_path(coordinate: str, cycle_index: int, suffix: str) -> str:
    return build_timestamped_task_log_path(
        LOG_TASK_TYPE,
        sanitize_library_log_segment(coordinate),
        f"cycle-{cycle_index}-{suffix}",
    )
