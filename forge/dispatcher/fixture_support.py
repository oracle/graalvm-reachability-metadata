# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Fixture-mode GitHub state and run logs (§AR-forge-dispatcher-decomposition)."""

import contextlib
import os
import shlex
import sys
import threading
from datetime import datetime

from utility_scripts.fixture_github import FixtureGitHubState, load_fixture_github_state
from utility_scripts.repo_path_resolver import get_repo_root
from utility_scripts.stage_logger import log_stage

from dispatcher.config import FIXTURE_E2E_LOG_DIRNAME, FIXTURE_RUN_LOG_FILENAME


fixture_github_state: FixtureGitHubState | None = None
fixture_run_timestamp_value: str | None = None

class FixtureRunLogTee:
    """Mirror process stdout/stderr, including child process output, into one log file."""

    def __init__(self, log_path: str) -> None:
        self.log_path = log_path
        self._log_file = open(log_path, "ab", buffering=0)
        self._lock = threading.Lock()
        self._original_stdout_fd = os.dup(1)
        self._original_stderr_fd = os.dup(2)
        self._stdout_read_fd, self._stdout_write_fd = os.pipe()
        self._stderr_read_fd, self._stderr_write_fd = os.pipe()
        self._threads: list[threading.Thread] = []
        self._closed = False

    def start(self) -> None:
        sys.stdout.flush()
        sys.stderr.flush()
        self._threads = [
            threading.Thread(
                target=self._copy_stream,
                args=(self._stdout_read_fd, self._original_stdout_fd),
                daemon=True,
            ),
            threading.Thread(
                target=self._copy_stream,
                args=(self._stderr_read_fd, self._original_stderr_fd),
                daemon=True,
            ),
        ]
        for thread in self._threads:
            thread.start()
        os.dup2(self._stdout_write_fd, 1)
        os.dup2(self._stderr_write_fd, 2)
        os.close(self._stdout_write_fd)
        os.close(self._stderr_write_fd)
        if hasattr(sys.stdout, "reconfigure"):
            sys.stdout.reconfigure(line_buffering=True, write_through=True)
        if hasattr(sys.stderr, "reconfigure"):
            sys.stderr.reconfigure(line_buffering=True, write_through=True)

    def close(self) -> None:
        if self._closed:
            return
        self._closed = True
        sys.stdout.flush()
        sys.stderr.flush()
        os.dup2(self._original_stdout_fd, 1)
        os.dup2(self._original_stderr_fd, 2)
        for thread in self._threads:
            thread.join(timeout=5)
        os.close(self._original_stdout_fd)
        os.close(self._original_stderr_fd)
        self._log_file.close()

    def _copy_stream(self, read_fd: int, target_fd: int) -> None:
        # The terminal receives every byte unchanged so the live `\r`-updating
        # status line renders exactly as before. The log file instead stores the
        # terminal-resolved text: carriage returns overwrite in place, so the many
        # in-place status frames collapse to the final state of each line.
        line = bytearray()
        cursor = 0
        try:
            while True:
                chunk = os.read(read_fd, 65536)
                if not chunk:
                    break
                os.write(target_fd, chunk)
                log_bytes, line, cursor = self._resolve_carriage_returns(chunk, line, cursor)
                if log_bytes:
                    with self._lock:
                        self._log_file.write(log_bytes)
        finally:
            if line:
                with self._lock:
                    self._log_file.write(bytes(line))
            os.close(read_fd)

    @staticmethod
    def _resolve_carriage_returns(
            chunk: bytes,
            line: bytearray,
            cursor: int,
    ) -> tuple[bytes, bytearray, int]:
        """Apply terminal `\\r`/`\\n` semantics to `chunk` for the log file.

        Returns the bytes of any completed lines plus the carried-over partial
        line and cursor. `\\r` resets the cursor to column 0 so later bytes
        overwrite the current line, mirroring how a terminal displays an in-place
        status update; `\\n` flushes the resolved line.
        """
        if 0x0D not in chunk and cursor == len(line):
            # Fast path: no carriage returns and nothing pending to overwrite, so
            # this is plain appending. Emit all complete lines, carry the rest.
            line += chunk
            split_at = line.rfind(0x0A)
            if split_at == -1:
                return b"", line, len(line)
            completed = bytes(line[:split_at + 1])
            remainder = bytearray(line[split_at + 1:])
            return completed, remainder, len(remainder)

        output = bytearray()
        for byte in chunk:
            if byte == 0x0A:  # newline: flush the resolved line
                output += line
                output.append(0x0A)
                line = bytearray()
                cursor = 0
            elif byte == 0x0D:  # carriage return: overwrite from column 0
                cursor = 0
            elif cursor < len(line):
                line[cursor] = byte
                cursor += 1
            else:
                line.append(byte)
                cursor += 1
        return bytes(output), line, cursor


def _fixture_run_timestamp() -> str:
    return datetime.now().astimezone().strftime("%Y%m%d-%H%M%S-%f%z")


def get_fixture_run_timestamp() -> str:
    """Return one stable timestamp shared by every issue processed in this run.

    Issues claimed by the same queue or label run land in sibling
    `issue-<number>/<timestamp>/` directories so they sort and correlate together.
    """
    global fixture_run_timestamp_value
    if fixture_run_timestamp_value is None:
        fixture_run_timestamp_value = _fixture_run_timestamp()
    return fixture_run_timestamp_value


def get_fixture_issue_artifact_dir(issue_number: int) -> str:
    """Return the artifact directory for one fixture issue: `issue-<number>/<timestamp>/`.

    Single-issue, label, and work-queue runs all write each issue's evidence here,
    alongside any other results for that issue. §FS-durable-generation-logs
    """
    issue_dir = os.path.join(
        get_repo_root(),
        FIXTURE_E2E_LOG_DIRNAME,
        f"issue-{issue_number}",
        get_fixture_run_timestamp(),
    )
    os.makedirs(issue_dir, exist_ok=True)
    return issue_dir


@contextlib.contextmanager
def fixture_issue_run_log(issue_number: int):
    """Tee one fixture issue's stdout/stderr into its own `run.log`.

    Fixture issue processing is sequential (parallelism is pinned to 1 in fixture
    mode), so a single process-wide tee per issue keeps each `run.log` scoped to
    that issue without interleaving.
    """
    issue_dir = get_fixture_issue_artifact_dir(issue_number)
    tee = FixtureRunLogTee(os.path.join(issue_dir, FIXTURE_RUN_LOG_FILENAME))
    tee.start()
    log_stage("fixture-log", f"Writing fixture run artifacts for issue #{issue_number} to {issue_dir}")
    log_stage("fixture-log", f"Complete fixture run log: {tee.log_path}")
    log_stage(
        "fixture-log",
        f"Fixture command: {' '.join(shlex.quote(argument) for argument in [sys.executable, *sys.argv])}",
    )
    try:
        yield issue_dir
    finally:
        tee.close()


def configure_fixture_testing(
        fixture_paths: list[str] | None = None,
        fixture_state: FixtureGitHubState | None = None,
) -> FixtureGitHubState:
    """Configure the dispatcher to use local fixture GitHub state."""
    global fixture_github_state
    if fixture_state is not None and fixture_paths is not None:
        raise ValueError("Pass either fixture_paths or fixture_state, not both")
    fixture_github_state = fixture_state or load_fixture_github_state(fixture_paths)
    return fixture_github_state


def is_fixture_testing_enabled() -> bool:
    """Return True when GitHub helper calls should use fixture state."""
    return fixture_github_state is not None


def require_fixture_github_state() -> FixtureGitHubState:
    """Return configured fixture GitHub state or fail with a dispatcher error."""
    if fixture_github_state is None:
        raise RuntimeError("Fixture GitHub state is not configured")
    return fixture_github_state
