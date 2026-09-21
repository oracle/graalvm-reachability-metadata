# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The shared dependency cache behind Forge's per-worktree Gradle user homes.

Worktrees build in homes of their own, so once per process the checkout's
long-lived home is warmed and its dependency cache published read-only as the
first worktree is created; homes whose worktree is gone are stopped and removed.
§FS-forge-run-requirements.4
"""

from __future__ import annotations

import glob
import os
import shutil
import time

from utility_scripts.gradle_daemons import stop_gradle_daemons_of_home
from utility_scripts.gradle_environment import (
    GRADLE_MODULES_CACHE_DIR,
    WORKTREE_MARKER_FILENAME,
    checkout_gradle_home_for_repo,
    gradle_command_environment,
    gradle_user_home_for_repo,
    is_linked_worktree,
    read_only_dependency_cache_path,
    worktree_gradle_homes_root,
)
from utility_scripts.logged_command import run_logged_command
from utility_scripts.stage_logger import log_detail, log_stage
from utility_scripts.task_logs import display_log_path

GRADLE_WARM_TIMEOUT_SECONDS = 1200
DAEMON_EXIT_TIMEOUT_SECONDS = 30
_STAGE = "gradle-cache"
_HARD_LINKED_CACHE_DIR = "files-2.1"
_VOLATILE_CACHE_FILENAMES = frozenset({"gc.properties"})
_VOLATILE_CACHE_SUFFIXES = (".lock",)
_prepared_checkout_homes: set[str] = set()


def ensure_shared_dependency_cache(checkout_path: str, build_dir: str) -> str | None:
    """Prepare the checkout home once per process, as its first worktree appears.

    `build_dir` is the fresh worktree whose build configures the home; the
    monitored checkout itself is never built (§FS-forge-run-requirements.4).
    """
    checkout_home = checkout_gradle_home_for_repo(checkout_path)
    if checkout_home in _prepared_checkout_homes:
        return None
    _prepared_checkout_homes.add(checkout_home)
    return prepare_shared_dependency_cache(checkout_path, build_dir)


def prepare_shared_dependency_cache(checkout_path: str, build_dir: str) -> str | None:
    """Ready the checkout home for a run: prune orphans, warm it, publish its cache.

    Every step is best effort; a failure is reported and leaves worktrees
    downloading more (§FS-forge-run-requirements.4).
    """
    prune_orphaned_worktree_gradle_homes(checkout_path)
    warm_checkout_gradle_home(checkout_path, build_dir)
    return publish_read_only_dependency_cache(checkout_path)


def warm_checkout_gradle_home(checkout_path: str, build_dir: str) -> bool:
    """Configure the build in `build_dir` once so the checkout home holds the root build's dependencies."""
    result = run_logged_command(
        ["./gradlew", "help", "--quiet", "--no-daemon"],
        cwd=build_dir,
        task_type=_STAGE,
        subject=os.path.basename(build_dir),
        action="gradle help",
        env=gradle_command_environment(checkout_path),
        timeout_seconds=GRADLE_WARM_TIMEOUT_SECONDS,
        stage=_STAGE,
        failure_is_detail=True,
    )
    checkout_home = checkout_gradle_home_for_repo(checkout_path)
    if result.returncode != 0 or result.timed_out:
        log_stage(
            _STAGE,
            f"Could not warm the checkout Gradle home {checkout_home} from {build_dir}; "
            f"continuing (log: {display_log_path(result.log_path)})",
        )
        return False
    log_detail(_STAGE, f"Warmed the checkout Gradle home {checkout_home} from {build_dir}")
    return True


def publish_read_only_dependency_cache(checkout_path: str) -> str | None:
    """Replace the published copy of the checkout home's dependency cache.

    The copy is assembled beside its final path and swapped in whole, so no
    worktree ever reads a half-written cache. Artifacts are hard-linked
    because Gradle never rewrites them in place; the indexes it does rewrite are
    copied.
    """
    checkout_home = checkout_gradle_home_for_repo(checkout_path)
    source = os.path.join(checkout_home, "caches", GRADLE_MODULES_CACHE_DIR)
    target = read_only_dependency_cache_path(checkout_home)
    if not os.path.isdir(source):
        log_stage(_STAGE, f"No dependency cache to publish under {checkout_home}; worktrees download everything")
        return None
    staging = f"{target}.staging"
    retired = f"{target}.retired"
    try:
        shutil.rmtree(staging, ignore_errors=True)
        shutil.rmtree(retired, ignore_errors=True)
        shutil.copytree(
            source,
            os.path.join(staging, GRADLE_MODULES_CACHE_DIR),
            symlinks=True,
            ignore=_volatile_cache_entries,
            copy_function=_link_or_copy,
        )
        if os.path.isdir(target):
            os.rename(target, retired)
        os.rename(staging, target)
        shutil.rmtree(retired, ignore_errors=True)
    except OSError as error:
        log_stage(_STAGE, f"Could not publish the read-only dependency cache at {target}: {error}; continuing")
        shutil.rmtree(staging, ignore_errors=True)
        return None
    log_detail(_STAGE, f"Published the read-only dependency cache at {target}")
    return target


def _volatile_cache_entries(directory: str, names: list[str]) -> set[str]:
    del directory
    return {
        name for name in names
        if name in _VOLATILE_CACHE_FILENAMES or name.endswith(_VOLATILE_CACHE_SUFFIXES)
    }


def _link_or_copy(source: str, destination: str) -> str:
    if f"{os.sep}{_HARD_LINKED_CACHE_DIR}{os.sep}" in source:
        try:
            os.link(source, destination)
            return destination
        except OSError:
            pass
    return shutil.copy2(source, destination)


def discard_worktree_gradle_home(worktree_path: str, checkout_path: str) -> None:
    """Stop the daemons of a worktree's Gradle home and delete the home, before the worktree goes."""
    if not is_linked_worktree(worktree_path):
        return
    gradle_user_home = gradle_user_home_for_repo(worktree_path)
    if not os.path.isdir(gradle_user_home):
        return
    gradlew_dir = worktree_path if os.path.isfile(os.path.join(worktree_path, "gradlew")) else checkout_path
    _discard_gradle_home(gradle_user_home, gradlew_dir, checkout_path, "worktree removal")


def prune_orphaned_worktree_gradle_homes(checkout_path: str) -> list[str]:
    """Stop and remove every worktree home whose worktree no longer exists."""
    homes_root = worktree_gradle_homes_root(checkout_gradle_home_for_repo(checkout_path))
    if not os.path.isdir(homes_root):
        return []
    pruned: list[str] = []
    for name in sorted(os.listdir(homes_root)):
        gradle_user_home = os.path.join(homes_root, name)
        worktree_path = _read_worktree_marker(gradle_user_home)
        if worktree_path is None or os.path.isdir(worktree_path):
            continue
        _discard_gradle_home(gradle_user_home, checkout_path, checkout_path, f"orphaned by {worktree_path}")
        pruned.append(gradle_user_home)
    return pruned


def _read_worktree_marker(gradle_user_home: str) -> str | None:
    marker_path = os.path.join(gradle_user_home, WORKTREE_MARKER_FILENAME)
    try:
        with open(marker_path, "r", encoding="utf-8") as marker_file:
            worktree_path = marker_file.read().strip()
    except OSError:
        return None
    return worktree_path or None


def _discard_gradle_home(gradle_user_home: str, gradlew_dir: str, checkout_path: str, reason: str) -> None:
    stop_gradle_daemons_of_home(
        gradle_user_home,
        gradlew_dir,
        gradle_command_environment(checkout_path),
        os.path.basename(gradle_user_home),
        reason,
    )
    # `--stop` returns before the daemon JVM exits, and its shutdown hooks write
    # the registry and cache housekeeping back into the home.
    _wait_for_daemons_to_exit(gradle_user_home)
    shutil.rmtree(gradle_user_home, ignore_errors=True)
    if os.path.exists(gradle_user_home):
        time.sleep(2)
        shutil.rmtree(gradle_user_home, ignore_errors=True)
    log_detail(_STAGE, f"Removed the Gradle user home {gradle_user_home} ({reason})")


def _wait_for_daemons_to_exit(gradle_user_home: str, timeout_seconds: float = DAEMON_EXIT_TIMEOUT_SECONDS) -> None:
    deadline = time.monotonic() + timeout_seconds
    live_pids = _daemon_pids(gradle_user_home)
    while live_pids and time.monotonic() < deadline:
        time.sleep(0.5)
        live_pids = {pid for pid in live_pids if _is_process_alive(pid)}


def _daemon_pids(gradle_user_home: str) -> set[int]:
    """Return the PIDs of every daemon that ever logged into the home."""
    pids: set[int] = set()
    for log_path in glob.glob(os.path.join(gradle_user_home, "daemon", "*", "daemon-*.out.log")):
        pid_text = os.path.basename(log_path).removeprefix("daemon-").removesuffix(".out.log")
        if pid_text.isdigit() and _is_process_alive(int(pid_text)):
            pids.add(int(pid_text))
    return pids


def _is_process_alive(pid: int) -> bool:
    try:
        os.kill(pid, 0)
    except ProcessLookupError:
        return False
    except PermissionError:
        return True
    return True
