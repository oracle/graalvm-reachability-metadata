# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Run log readers shared by review and failure analysis
(§AR-forge-dispatcher-decomposition, §FS-durable-generation-logs)."""


import json
import os

import subprocess
import sys

def read_log_tail(log_path: str, max_lines: int = 20) -> str:
    """Return the last lines from a log file when it exists."""
    if not os.path.isfile(log_path):
        return ""
    with open(log_path, "r", encoding="utf-8") as log_file:
        return "\n".join(log_file.read().strip().splitlines()[-max_lines:])


def read_log_text(log_path: str) -> str:
    """Return the complete text from a log file when it exists."""
    if not os.path.isfile(log_path):
        return ""
    with open(log_path, "r", encoding="utf-8") as log_file:
        return log_file.read().strip()


def output_tail(output: str | None, max_lines: int = 80) -> str:
    """Return the final lines from command output."""
    if not output:
        return ""
    return "\n".join(output.strip().splitlines()[-max_lines:])


def run_checked_command(
        command: list[str],
        cwd: str,
        error_message: str,
) -> subprocess.CompletedProcess:
    """Run a command and report its captured output on failure."""
    try:
        return subprocess.run(
            command,
            cwd=cwd,
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
        )
    except subprocess.CalledProcessError as exc:
        tail = output_tail(exc.stdout)
        print(f"ERROR: {error_message}" + (f":\n{tail}" if tail else ""), file=sys.stderr)
        raise
