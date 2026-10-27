# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Stopping and recycling the Gradle daemons behind one Forge Gradle user home.

Every worktree has its own home, so its daemons are stopped with the worktree;
a daemon that still holds stale build logic only ever fails that worktree's
gates, which rerun once after recycling it.
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
    """Stop the daemons of the Gradle user home a build under `repo_path` uses, best effort.

    `GRADLE_USER_HOME` scopes `--stop` to that home's daemon registry, so the
    daemons of any other Gradle user home stay untouched. A stop that fails is
    reported and never fails the caller. §FS-forge-run-requirements.4
    """
    return stop_gradle_daemons_of_home(
        gradle_user_home_for_repo(repo_path),
        repo_path,
        gradle_command_environment(repo_path),
        subject,
        reason,
    )


def stop_gradle_daemons_of_home(
        gradle_user_home: str,
        gradlew_dir: str,
        env: dict[str, str],
        subject: str | None,
        reason: str,
) -> bool:
    """Stop the daemons registered in `gradle_user_home` with the wrapper of `gradlew_dir`."""
    result = run_logged_command(
        ["./gradlew", "--stop", "--quiet"],
        cwd=gradlew_dir,
        task_type=_STAGE,
        subject=subject,
        action="gradle --stop",
        env={**env, "GRADLE_USER_HOME": gradle_user_home},
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
