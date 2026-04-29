# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os
from datetime import datetime, timezone


def sanitize_log_segment(value: str | None) -> str:
    """Return a filesystem-safe directory or file name segment."""
    if not value:
        return "unknown"
    safe_value = "".join(char if char.isalnum() or char in {"-", "_", "."} else "_" for char in value)
    return safe_value.strip("_") or "unknown"


def sanitize_library_log_segment(value: str | None) -> str:
    """Return a sanitized library log directory segment."""
    if not value:
        return "unknown"
    safe_value = "".join(
        char if char.isalnum() or char in {"-", "_", ".", ":", "+"} else "_"
        for char in value
    )
    return safe_value.strip("_") or "unknown"


def _repo_root() -> str:
    """Return the metadata-forge repository root."""
    return os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def resolve_logs_root() -> str:
    """Return the metadata-forge log root."""
    return os.path.join(_repo_root(), "logs")


def display_log_path(log_path: str) -> str:
    """Return a metadata-forge-relative log path for messages."""
    absolute_log_path = os.path.abspath(log_path)
    relative_path = os.path.relpath(absolute_log_path, _repo_root())
    if not relative_path.startswith("..") and not os.path.isabs(relative_path):
        return relative_path
    return os.path.relpath(absolute_log_path, os.getcwd())


def resolve_task_log_dir(task_type: str, library: str | None) -> str:
    """Return `logs/<library>/<task-type>/`, creating it when needed."""
    log_dir = os.path.join(
        resolve_logs_root(),
        sanitize_library_log_segment(library),
        sanitize_log_segment(task_type),
    )
    os.makedirs(log_dir, exist_ok=True)
    return log_dir


def build_task_log_path(task_type: str, library: str | None, file_name: str) -> str:
    """Return a log path under `logs/<library>/<task-type>/`."""
    return os.path.join(
        resolve_task_log_dir(task_type, library),
        sanitize_log_segment(os.path.basename(file_name)),
    )


def build_timestamped_task_log_path(
        task_type: str,
        library: str | None,
        file_prefix: str,
        suffix: str = ".log",
) -> str:
    """Return a timestamped log path under `logs/<library>/<task-type>/`."""
    timestamp = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H-%M-%S-%fZ")
    safe_prefix = sanitize_log_segment(file_prefix)
    safe_suffix = suffix if suffix.startswith(".") else f".{suffix}"
    return build_task_log_path(task_type, library, f"{safe_prefix}-{timestamp}{safe_suffix}")
