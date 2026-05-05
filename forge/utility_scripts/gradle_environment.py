# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

from __future__ import annotations

import hashlib
import os
import tempfile

FORGE_GRADLE_USER_HOME_ENV = "FORGE_GRADLE_USER_HOME"
FORGE_GRADLE_DISTRIBUTIONS_HOME_ENV = "FORGE_GRADLE_DISTRIBUTIONS_HOME"
_GRADLE_USER_HOME_ROOT = "metadata-forge-gradle"
_GRADLE_DISTRIBUTIONS_DIR = "wrapper-dists"


def gradle_user_home_for_repo(repo_path: str) -> str:
    """Return the isolated Gradle user home for a reachability-metadata worktree."""
    return _resolve_gradle_user_home(repo_path, os.environ.get(FORGE_GRADLE_USER_HOME_ENV))


def gradle_command_environment(repo_path: str, base_env: dict[str, str] | None = None) -> dict[str, str]:
    """Return an environment that keeps Gradle daemons scoped to one worktree."""
    env = dict(os.environ if base_env is None else base_env)
    _align_graalvm_java_home(env)
    user_home_override = env.get(FORGE_GRADLE_USER_HOME_ENV)
    gradle_user_home = _resolve_gradle_user_home(repo_path, user_home_override)
    os.makedirs(gradle_user_home, exist_ok=True)
    if not user_home_override:
        _share_gradle_wrapper_distributions(gradle_user_home, env.get(FORGE_GRADLE_DISTRIBUTIONS_HOME_ENV))
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

    repo_id = hashlib.sha256(os.path.realpath(repo_path).encode("utf-8")).hexdigest()[:16]
    return os.path.join(tempfile.gettempdir(), _GRADLE_USER_HOME_ROOT, repo_id)


def _share_gradle_wrapper_distributions(gradle_user_home: str, override: str | None) -> None:
    shared_dists_dir = _resolve_gradle_distributions_home(override)
    os.makedirs(shared_dists_dir, exist_ok=True)
    wrapper_dir = os.path.join(gradle_user_home, "wrapper")
    os.makedirs(wrapper_dir, exist_ok=True)

    dists_path = os.path.join(wrapper_dir, "dists")
    if os.path.islink(dists_path) or os.path.exists(dists_path):
        return

    try:
        os.symlink(shared_dists_dir, dists_path)
    except FileExistsError:
        return
    except OSError:
        os.makedirs(dists_path, exist_ok=True)


def _resolve_gradle_distributions_home(override: str | None) -> str:
    if override:
        return os.path.abspath(os.path.expanduser(override))
    return os.path.join(tempfile.gettempdir(), _GRADLE_USER_HOME_ROOT, _GRADLE_DISTRIBUTIONS_DIR)
