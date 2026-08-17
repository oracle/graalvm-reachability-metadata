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
_GRADLE_PROPERTIES_FILENAME = "gradle.properties"
_DEFAULT_HOST_GRADLE_HOME_DIR = ".gradle"


def gradle_user_home_for_repo(repo_path: str) -> str:
    """Return the Forge Gradle user home for a reachability-metadata checkout."""
    return _resolve_gradle_user_home(repo_path, os.environ.get(FORGE_GRADLE_USER_HOME_ENV))


def gradle_command_environment(repo_path: str, base_env: dict[str, str] | None = None) -> dict[str, str]:
    """Return an environment that keeps Gradle state scoped to one checkout."""
    env = dict(os.environ if base_env is None else base_env)
    _align_graalvm_java_home(env)
    user_home_override = env.get(FORGE_GRADLE_USER_HOME_ENV)
    gradle_user_home = _resolve_gradle_user_home(repo_path, user_home_override)
    os.makedirs(gradle_user_home, exist_ok=True)
    if not user_home_override:
        _share_gradle_wrapper_distributions(gradle_user_home, env.get(FORGE_GRADLE_DISTRIBUTIONS_HOME_ENV))
        _share_host_gradle_properties(gradle_user_home, env)
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
    """Return the cache key shared by every linked worktree of one checkout.

    Keying on the common git directory lets all issue worktrees of a checkout
    resolve the root build's plugins once instead of once per issue
    (§FS-shared-infrastructure-bootstrap-failure).
    """
    return _resolve_git_common_dir(repo_path) or os.path.realpath(repo_path)


def _resolve_git_common_dir(repo_path: str) -> str | None:
    """Return the `.git` directory shared by a checkout and its linked worktrees.

    Read from the filesystem, the way `git rev-parse --git-common-dir` resolves
    it, so building a Gradle environment never shells out.
    """
    git_dir = _resolve_git_dir(repo_path)
    if git_dir is None:
        return None
    common_dir = _read_git_pointer_file(os.path.join(git_dir, "commondir"))
    if common_dir is None:
        return os.path.realpath(git_dir)
    if not os.path.isabs(common_dir):
        common_dir = os.path.join(git_dir, common_dir)
    return os.path.realpath(common_dir)


def _resolve_git_dir(repo_path: str) -> str | None:
    """Return the git directory of the checkout containing `repo_path`."""
    current = os.path.realpath(repo_path)
    while True:
        git_path = os.path.join(current, ".git")
        if os.path.isdir(git_path):
            return git_path
        if os.path.isfile(git_path):
            return _resolve_linked_git_dir(current, git_path)
        parent = os.path.dirname(current)
        if parent == current:
            return None
        current = parent


def _resolve_linked_git_dir(repo_path: str, git_file_path: str) -> str | None:
    pointer = _read_git_pointer_file(git_file_path, prefix="gitdir:")
    if pointer is None:
        return None
    if not os.path.isabs(pointer):
        pointer = os.path.join(repo_path, pointer)
    return pointer


def _read_git_pointer_file(path: str, prefix: str | None = None) -> str | None:
    if not os.path.isfile(path):
        return None
    try:
        with open(path, "r", encoding="utf-8") as pointer_file:
            content = pointer_file.read()
    except OSError:
        return None
    for line in content.splitlines():
        candidate = line.strip()
        if prefix is not None:
            if not candidate.startswith(prefix):
                continue
            candidate = candidate[len(prefix):].strip()
        if candidate:
            return candidate
    return None


def _share_host_gradle_properties(gradle_user_home: str, env: dict[str, str]) -> None:
    """Link the host's `gradle.properties` into the isolated Gradle user home.

    Isolating Gradle state must not discard host Gradle configuration. On a host
    that reaches Maven Central and the Gradle plugin repository only through an
    HTTP proxy, that file carries the proxy settings, and an isolated home
    without it fails every plugin and distribution download during root-build
    configuration (§FS-shared-infrastructure-bootstrap-failure).
    """
    host_properties = _resolve_host_gradle_properties(gradle_user_home, env)
    if host_properties is None:
        return

    link_path = os.path.join(gradle_user_home, _GRADLE_PROPERTIES_FILENAME)
    if os.path.islink(link_path) or os.path.exists(link_path):
        return

    # Linked rather than copied so host credentials in the file are not duplicated
    # into a shared temporary directory.
    try:
        os.symlink(host_properties, link_path)
    except OSError:
        return


def _resolve_host_gradle_properties(gradle_user_home: str, env: dict[str, str]) -> str | None:
    host_home = _resolve_host_gradle_home(gradle_user_home, env)
    host_properties = os.path.join(host_home, _GRADLE_PROPERTIES_FILENAME)
    return host_properties if os.path.isfile(host_properties) else None


def _resolve_host_gradle_home(gradle_user_home: str, env: dict[str, str]) -> str:
    forge_homes_root = os.path.join(tempfile.gettempdir(), _GRADLE_USER_HOME_ROOT)
    inherited_home = env.get("GRADLE_USER_HOME")
    if inherited_home:
        inherited_home = os.path.abspath(os.path.expanduser(inherited_home))
        # An inherited value pointing at a Forge home is this layer's own output
        # from an outer call, not host configuration.
        if not _is_within(inherited_home, forge_homes_root) and inherited_home != gradle_user_home:
            return inherited_home
    return os.path.join(os.path.expanduser("~"), _DEFAULT_HOST_GRADLE_HOME_DIR)


def _is_within(path: str, root: str) -> bool:
    return os.path.commonpath([os.path.realpath(path), os.path.realpath(root)]) == os.path.realpath(root)


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
