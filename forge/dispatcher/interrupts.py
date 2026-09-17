# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Interrupt and shutdown state shared across the dispatcher
(§AR-forge-dispatcher-decomposition)."""

import threading

from utility_scripts.shutdown_signal import get_active_shutdown_signal_path, is_shutdown_requested

from dispatcher.config import (
    INTERRUPT_EXIT_CODES,
    INTERRUPT_REASON_CTRL_C,
    INTERRUPT_REASON_GRADLE_BOOTSTRAP,
    INTERRUPT_REASON_SHUTDOWN,
)


import time
from utility_scripts.stage_logger import log_stage
from dispatcher.config import SHUTDOWN_SIGNAL_POLL_SECONDS

_user_interrupt_requested = threading.Event()
_user_interrupt_reason = INTERRUPT_REASON_CTRL_C

def mark_user_interrupt_requested(reason: str = INTERRUPT_REASON_CTRL_C) -> None:
    """Record that the current run is shutting down before normal completion."""
    global _user_interrupt_reason

    _user_interrupt_reason = reason
    _user_interrupt_requested.set()


def preserve_user_interrupt_reason(reason: str = INTERRUPT_REASON_CTRL_C) -> None:
    """Mark the run as unwinding without overwriting an already recorded reason.

    Unwinding passes several handlers, and the first one to fire knows why the
    run is stopping; later ones must not relabel a shared bootstrap stop as a
    Ctrl+C (§FS-human-intervention-policy).
    """
    if not is_user_interrupt_requested():
        mark_user_interrupt_requested(reason)


def clear_user_interrupt_requested() -> None:
    """Clear the shutdown marker before starting a new top-level run."""
    global _user_interrupt_reason

    _user_interrupt_reason = INTERRUPT_REASON_CTRL_C
    _user_interrupt_requested.clear()


def is_user_interrupt_requested() -> bool:
    """Return True when the current run is unwinding before normal completion."""
    return _user_interrupt_requested.is_set()


def get_user_interrupt_reason() -> str:
    """Return the current run's shutdown reason."""
    return _user_interrupt_reason


def is_shutdown_request_interrupt() -> bool:
    """Return True when the current run is stopping because of the shared marker."""
    return is_user_interrupt_requested() and get_user_interrupt_reason() == INTERRUPT_REASON_SHUTDOWN


def is_gradle_bootstrap_interrupt() -> bool:
    """Return True when the run is stopping on a shared Gradle bootstrap outage."""
    return is_user_interrupt_requested() and get_user_interrupt_reason() == INTERRUPT_REASON_GRADLE_BOOTSTRAP


def mark_shutdown_requested() -> None:
    """Record that the shared Forge stop marker requested shutdown."""
    mark_user_interrupt_requested(INTERRUPT_REASON_SHUTDOWN)


def describe_active_shutdown_signal_path() -> str:
    """Return the active stop marker path for log messages."""
    return get_active_shutdown_signal_path() or "the configured stop marker"


def raise_if_shutdown_requested() -> None:
    """Raise KeyboardInterrupt when the shared Forge stop marker exists."""
    if is_shutdown_requested():
        mark_shutdown_requested()
        raise KeyboardInterrupt


def is_interrupt_exit_code(returncode: int | None) -> bool:
    """Return True for process return codes that conventionally mean Ctrl+C."""
    return returncode in INTERRUPT_EXIT_CODES


def is_interrupt_exception(exc: BaseException) -> bool:
    """Return True when an exception represents a Ctrl+C interrupt."""
    if isinstance(exc, KeyboardInterrupt):
        return True
    if isinstance(exc, SystemExit) and isinstance(exc.code, int):
        return is_interrupt_exit_code(exc.code)
    return False


def handle_sigint(_signum, _frame) -> None:
    mark_user_interrupt_requested(INTERRUPT_REASON_CTRL_C)
    raise KeyboardInterrupt


def sleep_until_shutdown_or_timeout(period_seconds: int) -> bool:
    """Sleep for up to the requested period and return True if shutdown was requested."""
    deadline = time.monotonic() + period_seconds
    while True:
        if is_shutdown_requested():
            log_stage(
                "shutdown",
                f"Stop marker exists at {describe_active_shutdown_signal_path()}; exiting sleep",
            )
            return True
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            return False
        time.sleep(min(remaining, SHUTDOWN_SIGNAL_POLL_SECONDS))
