# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

from __future__ import annotations

import hashlib
import os
import shlex
import subprocess
import tempfile

FORGE_GRADLE_USER_HOME_ENV = "FORGE_GRADLE_USER_HOME"
FORGE_GRADLE_DISTRIBUTIONS_HOME_ENV = "FORGE_GRADLE_DISTRIBUTIONS_HOME"
_GRADLE_USER_HOME_ROOT = "metadata-forge-gradle"
_GRADLE_DISTRIBUTIONS_DIR = "wrapper-dists"
_GRADLE_PROPERTIES_FILENAME = "gradle.properties"
_DEFAULT_HOST_GRADLE_HOME_DIR = ".gradle"
_GRADLE_JAVA_HOME_OPTION = "-Dorg.gradle.java.home="
_WORKTREE_HOMES_DIR = "worktree-homes"
_READ_ONLY_DEPENDENCY_CACHE_DIR = "ro-dep-cache"
GRADLE_MODULES_CACHE_DIR = "modules-2"
WORKTREE_MARKER_FILENAME = "forge-worktree-path"


def checkout_gradle_home_for_repo(repo_path: str) -> str:
    """Return the long-lived Forge Gradle user home of the checkout containing `repo_path`."""
    return _resolve_checkout_gradle_home(repo_path, os.environ.get(FORGE_GRADLE_USER_HOME_ENV))


def gradle_user_home_for_repo(repo_path: str) -> str:
    """Return the Gradle user home a build under `repo_path` uses.

    A linked worktree builds in its own home so its daemons serve it alone; the
    checkout itself builds in the long-lived home (§FS-forge-run-requirements.4).
    """
    checkout_home = checkout_gradle_home_for_repo(repo_path)
    worktree_root = _resolve_linked_worktree_root(repo_path)
    if worktree_root is None:
        return checkout_home
    return worktree_gradle_home(checkout_home, worktree_root)


def worktree_gradle_home(checkout_home: str, worktree_root: str) -> str:
    """Return the Gradle user home dedicated to one linked worktree."""
    resolved_root = os.path.realpath(worktree_root)
    digest = hashlib.sha256(resolved_root.encode("utf-8")).hexdigest()[:8]
    return os.path.join(worktree_gradle_homes_root(checkout_home), f"{os.path.basename(resolved_root)}-{digest}")


def worktree_gradle_homes_root(checkout_home: str) -> str:
    """Return the directory holding every worktree home of one checkout home."""
    return os.path.join(checkout_home, _WORKTREE_HOMES_DIR)


def read_only_dependency_cache_path(checkout_home: str) -> str:
    """Return where the checkout home's published dependency cache lives."""
    return os.path.join(checkout_home, _READ_ONLY_DEPENDENCY_CACHE_DIR)


def is_linked_worktree(repo_path: str) -> bool:
    """Return True when `repo_path` lies in a linked git worktree rather than the checkout."""
    return _resolve_linked_worktree_root(repo_path) is not None


def gradle_command_environment(repo_path: str, base_env: dict[str, str] | None = None) -> dict[str, str]:
    """Return an environment that keeps Gradle daemons scoped to one worktree.

    Dependencies stay shared: a worktree reads the checkout home's published
    dependency cache through `GRADLE_RO_DEP_CACHE` and downloads only what that
    copy lacks (§FS-forge-run-requirements.4).
    """
    env = dict(os.environ if base_env is None else base_env)
    _align_graalvm_java_home(env)
    user_home_override = env.get(FORGE_GRADLE_USER_HOME_ENV)
    checkout_home = _resolve_checkout_gradle_home(repo_path, user_home_override)
    os.makedirs(checkout_home, exist_ok=True)
    if not user_home_override:
        _share_gradle_wrapper_distributions(checkout_home, env.get(FORGE_GRADLE_DISTRIBUTIONS_HOME_ENV))
        _share_host_gradle_properties(checkout_home, env)
    worktree_root = _resolve_linked_worktree_root(repo_path)
    if worktree_root is None:
        env["GRADLE_USER_HOME"] = checkout_home
        return env
    worktree_home = worktree_gradle_home(checkout_home, worktree_root)
    _prepare_worktree_gradle_home(worktree_home, checkout_home, worktree_root)
    env["GRADLE_USER_HOME"] = worktree_home
    read_only_cache = read_only_dependency_cache_path(checkout_home)
    if os.path.isdir(os.path.join(read_only_cache, GRADLE_MODULES_CACHE_DIR)):
        env["GRADLE_RO_DEP_CACHE"] = read_only_cache
    return env


def _prepare_worktree_gradle_home(worktree_home: str, checkout_home: str, worktree_root: str) -> None:
    """Create a worktree home that borrows the checkout home's wrapper and properties."""
    os.makedirs(worktree_home, exist_ok=True)
    marker_path = os.path.join(worktree_home, WORKTREE_MARKER_FILENAME)
    if not os.path.exists(marker_path):
        with open(marker_path, "w", encoding="utf-8") as marker_file:
            marker_file.write(os.path.realpath(worktree_root) + "\n")
    checkout_dists = os.path.join(checkout_home, "wrapper", "dists")
    os.makedirs(checkout_dists, exist_ok=True)
    _share_gradle_wrapper_distributions(worktree_home, checkout_dists)
    checkout_properties = os.path.join(checkout_home, _GRADLE_PROPERTIES_FILENAME)
    if os.path.isfile(checkout_properties):
        _link_gradle_properties(worktree_home, checkout_properties)


def _align_graalvm_java_home(env: dict[str, str]) -> None:
    graalvm_home = env.get("GRAALVM_HOME")
    java_home = env.get("JAVA_HOME")
    if graalvm_home and _has_native_image(graalvm_home):
        pin_gradle_java_home(env, graalvm_home)
        return
    if java_home and _has_native_image(java_home):
        pin_gradle_java_home(env, java_home)


def pin_gradle_java_home(env: dict[str, str], graalvm_home: str) -> None:
    """Pin every Gradle Java selector to one GraalVM distribution.

    `GRADLE_OPTS` can carry an inherited `org.gradle.java.home` that wins over
    `JAVA_HOME`; append the authoritative value so nested Gradle invocations use
    the same agent-capable JVM (§FS-forge-host-requirements).
    """
    env["GRAALVM_HOME"] = graalvm_home
    env["JAVA_HOME"] = graalvm_home
    env["GRADLE_JAVA_HOME"] = graalvm_home
    java_home_option = f"{_GRADLE_JAVA_HOME_OPTION}{graalvm_home}"
    existing_options = env.get("GRADLE_OPTS", "").strip()
    rendered_option = (
        subprocess.list2cmdline([java_home_option])
        if os.name == "nt"
        else shlex.quote(java_home_option)
    )
    if existing_options == rendered_option or existing_options.endswith(f" {rendered_option}"):
        env["GRADLE_OPTS"] = existing_options
        return
    env["GRADLE_OPTS"] = " ".join(option for option in (existing_options, rendered_option) if option)


def _has_native_image(home: str) -> bool:
    return os.path.isfile(os.path.join(home, "bin", "native-image"))


def _resolve_checkout_gradle_home(repo_path: str, override: str | None) -> str:
    if override:
        return os.path.abspath(os.path.expanduser(override))

    repo_id = hashlib.sha256(_gradle_cache_identity(repo_path).encode("utf-8")).hexdigest()[:16]
    return os.path.join(tempfile.gettempdir(), _GRADLE_USER_HOME_ROOT, repo_id)


def _gradle_cache_identity(repo_path: str) -> str:
    """Return the cache key shared by every linked worktree of one checkout.

    Keying on the common git directory gives all issue worktrees of a checkout
    one home to publish dependencies from, so the root build's plugins are
    resolved once instead of once per issue (§FS-forge-run-requirements.4).
    """
    return _resolve_git_common_dir(repo_path) or os.path.realpath(repo_path)


def _resolve_linked_worktree_root(repo_path: str) -> str | None:
    """Return the root of the linked worktree containing `repo_path`, if any.

    A linked worktree carries a `.git` file pointing into the checkout's common
    git directory; the checkout itself carries a `.git` directory.
    """
    entry = _find_git_entry(repo_path)
    if entry is None:
        return None
    root, git_path = entry
    if not os.path.isfile(git_path):
        return None
    return root if _resolve_linked_git_dir(root, git_path) is not None else None


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
    entry = _find_git_entry(repo_path)
    if entry is None:
        return None
    root, git_path = entry
    if os.path.isdir(git_path):
        return git_path
    return _resolve_linked_git_dir(root, git_path)


def _find_git_entry(repo_path: str) -> tuple[str, str] | None:
    """Return the nearest enclosing directory with a `.git` entry and that entry."""
    current = os.path.realpath(repo_path)
    while True:
        git_path = os.path.join(current, ".git")
        if os.path.isdir(git_path) or os.path.isfile(git_path):
            return current, git_path
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
    configuration (§FS-human-intervention-policy).
    """
    host_properties = _resolve_host_gradle_properties(gradle_user_home, env)
    if host_properties is None:
        return
    _link_gradle_properties(gradle_user_home, host_properties)


def _link_gradle_properties(gradle_user_home: str, properties_path: str) -> None:
    link_path = os.path.join(gradle_user_home, _GRADLE_PROPERTIES_FILENAME)
    if os.path.islink(link_path) or os.path.exists(link_path):
        return

    # Linked rather than copied so host credentials in the file are not duplicated
    # into a shared temporary directory.
    try:
        os.symlink(properties_path, link_path)
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
    resolved_root = os.path.realpath(root)
    try:
        return os.path.commonpath([os.path.realpath(path), resolved_root]) == resolved_root
    except ValueError:
        # Windows raises when the two paths live on different drives, which is
        # itself proof that `path` is not under `root`.
        return False


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
