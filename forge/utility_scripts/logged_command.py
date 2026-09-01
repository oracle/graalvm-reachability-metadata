# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Quiet subprocess execution backed by durable per-task logs.

Command output belongs in the durable log by default; debug mode tees the same
bytes to the terminal while the process runs. §FS-forge-run-output-legibility.4
§FS-durable-generation-logs
"""

from __future__ import annotations

from dataclasses import dataclass
import os
import shlex
import subprocess
import sys
import threading
import time

from utility_scripts.stage_logger import debug_logging_enabled, log_detail, log_stage
from utility_scripts.task_logs import build_timestamped_task_log_path, display_log_path


@dataclass(frozen=True)
class LoggedCommandResult:
    """Captured command outcome and its durable evidence path."""

    args: list[str]
    returncode: int
    stdout: str
    log_path: str
    timed_out: bool
    duration_seconds: float


def run_logged_command(
        command: list[str],
        *,
        cwd: str,
        task_type: str,
        subject: str | None,
        action: str,
        env: dict[str, str] | None = None,
        timeout_seconds: int | None = None,
        stage: str = "command",
        failure_is_detail: bool = False,
) -> LoggedCommandResult:
    """Run one command quietly, teeing live output only in debug mode."""
    log_path = build_timestamped_task_log_path(task_type, subject, action)
    displayed_log_path = display_log_path(log_path)
    log_detail(stage, f"Running {action} (log: {displayed_log_path})")
    started_at = time.monotonic()
    output_chunks: list[str] = []
    timed_out = False

    with open(log_path, "w", encoding="utf-8") as log_file:
        log_file.write(f"Command: {shlex.join(command)}\n")
        log_file.write(f"Working directory: {os.path.abspath(cwd)}\n")
        log_file.write("\n--- output ---\n")
        log_file.flush()
        try:
            process = subprocess.Popen(
                command,
                cwd=cwd,
                env=env,
                stdin=subprocess.DEVNULL,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
            )
        except OSError as exc:
            message = f"Failed to start command: {exc}\n"
            log_file.write(message)
            duration_seconds = time.monotonic() - started_at
            failure_logger = log_detail if failure_is_detail else log_stage
            failure_logger(stage, f"{action} failed to start (log: {displayed_log_path})")
            return LoggedCommandResult(
                command, 1, message, log_path, False, duration_seconds,
            )

        output_lock = threading.Lock()

        def copy_output() -> None:
            if process.stdout is None:
                return
            while True:
                chunk = process.stdout.read(4096)
                if not chunk:
                    return
                with output_lock:
                    output_chunks.append(chunk)
                    log_file.write(chunk)
                    log_file.flush()
                if debug_logging_enabled():
                    print(chunk, end="", file=sys.stdout, flush=True)

        output_thread = threading.Thread(target=copy_output, daemon=True)
        output_thread.start()
        try:
            returncode = process.wait(timeout=timeout_seconds)
        except subprocess.TimeoutExpired:
            timed_out = True
            process.kill()
            returncode = process.wait()
            timeout_message = f"\nCommand exceeded {timeout_seconds}s timeout\n"
            with output_lock:
                output_chunks.append(timeout_message)
                log_file.write(timeout_message)
                log_file.flush()
        finally:
            output_thread.join()
            if process.stdout is not None:
                process.stdout.close()

    duration_seconds = time.monotonic() - started_at
    output = "".join(output_chunks)
    duration = _format_duration(duration_seconds)
    if returncode == 0 and not timed_out:
        log_detail(stage, f"{action} completed in {duration} (log: {displayed_log_path})")
    else:
        reason = "timed out" if timed_out else f"failed with exit code {returncode}"
        failure_logger = log_detail if failure_is_detail else log_stage
        failure_logger(stage, f"{action} {reason} after {duration} (log: {displayed_log_path})")
    return LoggedCommandResult(
        command, returncode, output, log_path, timed_out, duration_seconds,
    )


def _format_duration(duration_seconds: float) -> str:
    total_seconds = max(int(duration_seconds), 0)
    minutes, seconds = divmod(total_seconds, 60)
    if minutes >= 60:
        hours, minutes = divmod(minutes, 60)
        return f"{hours:02d}:{minutes:02d}:{seconds:02d}"
    return f"{minutes:02d}:{seconds:02d}"
