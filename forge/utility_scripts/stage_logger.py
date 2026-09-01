# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os
import sys
import threading
from typing import TextIO


ANSI_RESET = "\033[0m"
ANSI_BOLD_GREEN = "\033[1;32m"
ANSI_BOLD_RED = "\033[1;31m"
ANSI_BOLD_CYAN = "\033[1;36m"
BANNER_WIDTH = 88
DEBUG_LOGGING_ENV_VAR = "FORGE_DEBUG_LOGGING"
VERBOSE_LOGGING_ENV_VAR = "FORGE_VERBOSE"


class _NarrationState(threading.local):
    """Per-run phase state deciding whether normal detail is suppressed."""

    def __init__(self) -> None:
        self.compact: bool = False


_NARRATION_STATE = _NarrationState()


def _environment_flag_enabled(variable: str) -> bool:
    """Return True when an environment flag carries a conventional true value."""
    return os.environ.get(variable, "").strip().lower() in {"1", "true", "yes", "on"}


def enable_verbose_logging() -> None:
    """Enable narration-level logging for this process and its children."""
    os.environ[VERBOSE_LOGGING_ENV_VAR] = "1"


def debug_logging_enabled() -> bool:
    """Return True when narration-level logging is switched on."""
    return (
        _environment_flag_enabled(VERBOSE_LOGGING_ENV_VAR)
        or _environment_flag_enabled(DEBUG_LOGGING_ENV_VAR)
    )


def log_stage(stage: str, message: str, indent_level: int = 0) -> None:
    """Print a workflow log line that starts with the current stage."""
    indent = "  " * indent_level
    print(f"[{stage}] {indent}{message}")


def set_compact_narration(compact: bool) -> None:
    """Select whether the current thread's announced phase hides detail."""
    _NARRATION_STATE.compact = compact


def log_detail(stage: str, message: str, indent_level: int = 0) -> None:
    """Print narration unless the current compact phase hides it."""
    if _NARRATION_STATE.compact and not debug_logging_enabled():
        return
    log_stage(stage, message, indent_level)


def log_debug(stage: str, message: str, indent_level: int = 0) -> None:
    """Print verbose narration that is debugging detail, not normal run output.

    Keeps the human-intervention handoff's git narration out of failure output
    unless verbose logging is enabled. §FS-forge-run-location-reporting.4
    """
    if debug_logging_enabled():
        log_stage(stage, message, indent_level)


def log_phase_banner(phase: str, context: str | None = None, file: TextIO | None = None) -> None:
    """Print the bounded banner that opens a run phase.

    A phase transition must be findable by eye in a long run log, and it names
    the run it belongs to because runs are interleaved on a pool.
    §FS-forge-run-location-reporting.2
    """
    output = sys.stdout if file is None else file
    title = f" PHASE: {phase.upper()} " if context is None else f" PHASE: {phase.upper()} — {context} "
    title_line = title.center(BANNER_WIDTH, "#")
    print(f"\n{ANSI_BOLD_CYAN}{title_line}{ANSI_RESET}", file=output)


def log_status_banner(title: str, message: str, color: str, file: TextIO | None = None) -> None:
    """Print a highly visible colored multiline status banner."""
    output = sys.stdout if file is None else file
    delimiter = "=" * BANNER_WIDTH
    title_line = f" {title.upper()} ".center(BANNER_WIDTH, "=")
    print(
        f"\n{color}{delimiter}\n{title_line}\n{delimiter}\n{message}\n{delimiter}{ANSI_RESET}",
        file=output,
    )


def log_success_banner(message: str, file: TextIO | None = None) -> None:
    """Print a green success banner."""
    log_status_banner("SUCCESS", message, ANSI_BOLD_GREEN, file=file)


def log_failure_banner(message: str, file: TextIO | None = None) -> None:
    """Print a red failure banner."""
    log_status_banner("FAILED", message, ANSI_BOLD_RED, file=file)
