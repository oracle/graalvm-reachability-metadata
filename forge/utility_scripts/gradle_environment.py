# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

from __future__ import annotations

import hashlib
import os
import subprocess
import tempfile

FORGE_GRADLE_USER_HOME_ENV = "FORGE_GRADLE_USER_HOME"
_GRADLE_USER_HOME_ROOT = "metadata-forge-gradle"


def gradle_user_home_for_repo(repo_path: str) -> str:
    """Return the Forge Gradle user home for a reachability-metadata checkout."""
    return _resolve_gradle_user_home(repo_path, os.environ.get(FORGE_GRADLE_USER_HOME_ENV))


def gradle_command_environment(repo_path: str, base_env: dict[str, str] | None = None) -> dict[str, str]:
    """Return an environment that keeps Gradle state scoped to one local clone."""
    env = dict(os.environ if base_env is None else base_env)
    _align_graalvm_java_home(env)
    gradle_user_home = _resolve_gradle_user_home(repo_path, env.get(FORGE_GRADLE_USER_HOME_ENV))
    os.makedirs(gradle_user_home, exist_ok=True)
    env["GRADLE_USER_HOME"] = gradle_user_home
    return env


def _align_graalvm_java_home(env: dict[str, str]) -> None:
    graalvm_home = env.get("GRAALVM_HOME")
    java_home = env.get("JAVA_HOME")
    if graalvm_home and _has_native_image(graalvm_home):
        env["GRAALVM_HOME"] = graalvm_home
        env["JAVA_HOME"] = graalvm_home
        return
    if java_home and _has_native_image(java_home):
        env["GRAALVM_HOME"] = java_home
        env["JAVA_HOME"] = java_home


def _has_native_image(home: str) -> bool:
    return os.path.isfile(os.path.join(home, "bin", "native-image"))


def _resolve_gradle_user_home(repo_path: str, override: str | None) -> str:
    if override:
        return os.path.abspath(os.path.expanduser(override))

    repo_id = hashlib.sha256(_gradle_cache_identity(repo_path).encode("utf-8")).hexdigest()[:16]
    return os.path.join(tempfile.gettempdir(), _GRADLE_USER_HOME_ROOT, repo_id)


def _gradle_cache_identity(repo_path: str) -> str:
    git_common_dir = _resolve_git_common_dir(repo_path)
    if git_common_dir:
        return git_common_dir
    return os.path.realpath(repo_path)


def _resolve_git_common_dir(repo_path: str) -> str | None:
    try:
        result = subprocess.run(
            ["git", "rev-parse", "--git-common-dir"],
            cwd=repo_path,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            text=True,
            timeout=5,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired):
        return None
    if result.returncode != 0:
        return None
    git_common_dir = result.stdout.strip()
    if not git_common_dir:
        return None
    if not os.path.isabs(git_common_dir):
        git_common_dir = os.path.join(repo_path, git_common_dir)
    return os.path.realpath(git_common_dir)
