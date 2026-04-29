# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

from utility_scripts.task_logs import build_timestamped_task_log_path, sanitize_log_segment


def _sanitize_log_name(value: str) -> str:
    """Return a filesystem-safe log name segment."""
    return sanitize_log_segment(value)


def _resolve_library_name(library: str | None) -> str:
    """Return the full library identifier when available, else a sanitized fallback."""
    if not library:
        return "unknown"
    return library


def build_pi_log_path(action: str, library: str | None, task_type: str | None = None) -> str:
    """Return a Pi log path under `logs/<library>/<task-type>/`."""
    safe_action = _sanitize_log_name(action)
    return build_timestamped_task_log_path(
        task_type or action,
        _resolve_library_name(library),
        f"pi-{safe_action}",
    )
