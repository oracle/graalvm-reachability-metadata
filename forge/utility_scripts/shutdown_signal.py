# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Shared stop marker helpers for Forge worker loops."""

import os
import re
import time

DEFAULT_SHUTDOWN_SIGNAL_FILENAME = ".metadata-forge-stop"
SHUTDOWN_SIGNAL_ENV_VAR = "FORGE_DO_WORK_STOP_FILE"
MONITORED_BRANCH_ENV_VAR = "FORGE_MONITORED_BRANCH"


def normalize_monitored_branch(branch_name: str) -> str:
    """Return the branch name used for branch-scoped stop markers."""
    return branch_name.removeprefix("origin/")


def sanitize_branch_for_signal_path(branch_name: str) -> str:
    """Return a filesystem-safe branch marker suffix."""
    normalized_branch = normalize_monitored_branch(branch_name)
    return re.sub(r"[^A-Za-z0-9._-]", "_", normalized_branch)


def get_shutdown_signal_path() -> str:
    """Return the filesystem path used to request all Forge workers to stop."""
    configured_path = os.environ.get(SHUTDOWN_SIGNAL_ENV_VAR)
    if configured_path:
        return os.path.abspath(os.path.expanduser(configured_path))
    return os.path.join(os.path.expanduser("~"), DEFAULT_SHUTDOWN_SIGNAL_FILENAME)


def get_branch_shutdown_signal_path(branch_name: str) -> str:
    """Return the filesystem path used to stop workers for one Forge branch."""
    return f"{get_shutdown_signal_path()}.{sanitize_branch_for_signal_path(branch_name)}"


def get_monitored_branch_shutdown_signal_path() -> str | None:
    """Return the active branch stop marker path, if this process monitors a branch."""
    monitored_branch = os.environ.get(MONITORED_BRANCH_ENV_VAR)
    if not monitored_branch:
        return None
    return get_branch_shutdown_signal_path(monitored_branch)


def get_active_shutdown_signal_path() -> str | None:
    """Return the existing stop marker path that applies to this process."""
    global_signal_path = get_shutdown_signal_path()
    if os.path.exists(global_signal_path):
        return global_signal_path
    branch_signal_path = get_monitored_branch_shutdown_signal_path()
    if branch_signal_path and os.path.exists(branch_signal_path):
        return branch_signal_path
    return None


def is_shutdown_requested() -> bool:
    """Return True when a global or monitored-branch Forge stop marker exists."""
    return get_active_shutdown_signal_path() is not None


def request_shutdown(branch_name: str | None = None) -> str:
    """Create a global or branch-scoped Forge stop marker and return its path."""
    signal_path = get_branch_shutdown_signal_path(branch_name) if branch_name else get_shutdown_signal_path()
    signal_dir = os.path.dirname(signal_path)
    if signal_dir:
        os.makedirs(signal_dir, exist_ok=True)
    with open(signal_path, "w", encoding="utf-8") as signal_file:
        signal_file.write(f"Metadata Forge shutdown requested at {time.strftime('%Y-%m-%d %H:%M:%S %Z')}\n")
    return signal_path


def clear_shutdown_request(branch_name: str | None = None) -> str:
    """Remove a global or branch-scoped Forge stop marker and return its path."""
    signal_path = get_branch_shutdown_signal_path(branch_name) if branch_name else get_shutdown_signal_path()
    try:
        os.remove(signal_path)
    except FileNotFoundError:
        pass
    return signal_path
