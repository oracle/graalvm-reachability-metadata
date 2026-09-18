# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Recycling of the Gradle daemon pool behind one Forge Gradle user home.

A daemon outlives the worktree that started it and can keep that worktree's
build logic loaded, so a later worktree's Gradle task fails before it exists.
§FS-forge-run-requirements.4 §FS-local-ci-equivalent-verification.1
"""

from __future__ import annotations

import shlex
from collections.abc import Callable

from utility_scripts.gradle_environment import gradle_command_environment, gradle_user_home_for_repo
from utility_scripts.logged_command import LoggedCommandResult, run_logged_command
from utility_scripts.stage_logger import log_detail, log_stage
from utility_scripts.task_logs import display_log_path

GRADLE_STOP_TIMEOUT_SECONDS = 120
STALE_DAEMON_FAILURE_MARKERS = (
    "Could not generate a decorated class",
    "NoClassDefFoundError",
)
_STAGE = "gradle-daemons"


def stop_gradle_daemons(repo_path: str, subject: str | None, reason: str) -> bool:
    """Stop the daemons of the Forge Gradle user home for `repo_path`, best effort.

    `GRADLE_USER_HOME` scopes `--stop` to that home's daemon registry, so the
    daemons of any other Gradle user home stay untouched. A stop that fails is
    reported and never fails the caller. §FS-forge-run-requirements.4
    """
    gradle_user_home = gradle_user_home_for_repo(repo_path)
    result = run_logged_command(
        ["./gradlew", "--stop", "--quiet"],
        cwd=repo_path,
        task_type=_STAGE,
        subject=subject,
        action="gradle --stop",
        env=gradle_command_environment(repo_path),
        timeout_seconds=GRADLE_STOP_TIMEOUT_SECONDS,
        stage=_STAGE,
        failure_is_detail=True,
    )
    if result.returncode != 0 or result.timed_out:
        log_stage(
            _STAGE,
            f"Could not stop the Gradle daemons of {gradle_user_home} ({reason}); "
            f"continuing (log: {display_log_path(result.log_path)})",
        )
        return False
    log_detail(_STAGE, f"Stopped the Gradle daemons of {gradle_user_home} ({reason})")
    return True


def is_stale_daemon_failure(output: str) -> bool:
    """Return True when Gradle output shows build logic a reused daemon cannot load."""
    return any(marker in output for marker in STALE_DAEMON_FAILURE_MARKERS)


def run_with_stale_daemon_retry(
        repo_path: str,
        subject: str | None,
        run: Callable[[], LoggedCommandResult],
) -> LoggedCommandResult:
    """Run one Gradle gate, recycling the daemons and rerunning once on a stale-daemon failure.

    §FS-local-ci-equivalent-verification.1
    """
    result = run()
    if result.returncode == 0 or not is_stale_daemon_failure(result.stdout):
        return result
    log_stage(
        _STAGE,
        f"{shlex.join(result.args)} failed before its task could be created (stale Gradle daemon); "
        "stopping the daemons and rerunning once",
    )
    stop_gradle_daemons(repo_path, subject, "stale daemon failure")
    return run()
