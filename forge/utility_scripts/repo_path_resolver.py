# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os
import subprocess
import sys

REACHABILITY_REPO_CLONE_URL = "git@github.com:oracle/graalvm-reachability-metadata.git"


def get_repo_root() -> str:
    return os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def get_forge_subdir_name() -> str:
    """Return the Forge directory name inside the reachability metadata repo."""
    return os.path.basename(get_repo_root())


def git_env_limited_to_repo_root(repo_root: str) -> dict[str, str]:
    """Return an environment that prevents Git from discovering parent repos."""
    resolved_root = os.path.abspath(repo_root)
    env = os.environ.copy()
    env["GIT_CEILING_DIRECTORIES"] = os.path.dirname(resolved_root)
    return env


def _run_git(cmd: list[str], cwd: str) -> None:
    """Run a git command and exit with a clear error on failure."""
    result = subprocess.run(
        cmd,
        cwd=cwd,
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        print(
            f"ERROR: Failed to run `{' '.join(cmd)}` in {cwd}: {result.stdout or result.stderr}",
            file=sys.stderr,
        )
        raise SystemExit(1)


def _is_git_checkout(repo_root: str) -> bool:
    """Return True when the exact path is a git checkout, including linked worktrees."""
    if not os.path.isdir(repo_root):
        return False

    resolved_root = os.path.abspath(repo_root)
    result = subprocess.run(
        ["git", "rev-parse", "--show-toplevel"],
        cwd=resolved_root,
        env=git_env_limited_to_repo_root(resolved_root),
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        return False
    return os.path.realpath(result.stdout.strip()) == os.path.realpath(resolved_root)


def ensure_local_reachability_repo(reachability_root: str) -> str:
    """Clone the default reachability-metadata repository when it does not already exist."""
    if _is_git_checkout(reachability_root):
        return require_complete_reachability_repo(reachability_root)

    if os.path.exists(reachability_root) and os.listdir(reachability_root):
        print(
            f"ERROR: Reachability metadata repository path exists but is not a git checkout: {reachability_root}",
            file=sys.stderr,
        )
        raise SystemExit(1)

    parent_dir = os.path.dirname(reachability_root)
    os.makedirs(parent_dir, exist_ok=True)

    print(f"[Cloning graalvm-reachability-metadata into {reachability_root}...]")
    _run_git(["git", "clone", REACHABILITY_REPO_CLONE_URL, reachability_root], cwd=parent_dir)
    return require_complete_reachability_repo(reachability_root)


def ensure_local_metrics_repo(metrics_root: str) -> str:
    """Create a local metrics git repository when it does not already exist."""
    os.makedirs(metrics_root, exist_ok=True)

    if _is_git_checkout(metrics_root):
        return metrics_root

    print(f"[Initializing local metrics repository at {metrics_root}...]")
    _run_git(["git", "init", "-b", "master"], cwd=metrics_root)

    script_metrics_dir = os.path.join(metrics_root, "script_run_metrics")
    benchmark_metrics_dir = os.path.join(metrics_root, "benchmark_run_metrics")
    os.makedirs(script_metrics_dir, exist_ok=True)
    os.makedirs(benchmark_metrics_dir, exist_ok=True)

    with open(os.path.join(metrics_root, ".gitignore"), "w", encoding="utf-8") as gitignore_file:
        gitignore_file.write(".pending_metrics.json\n")

    for gitkeep_path in (
        os.path.join(script_metrics_dir, ".gitkeep"),
        os.path.join(benchmark_metrics_dir, ".gitkeep"),
    ):
        with open(gitkeep_path, "a", encoding="utf-8"):
            pass

    _run_git(
        ["git", "add", ".gitignore", "script_run_metrics/.gitkeep", "benchmark_run_metrics/.gitkeep"],
        cwd=metrics_root,
    )
    _run_git(
        [
            "git",
            "-c",
            "user.name=metadata-forge",
            "-c",
            "user.email=metadata-forge@local",
            "commit",
            "-m",
            "Initialize local metrics repository",
        ],
        cwd=metrics_root,
    )
    return metrics_root


def ensure_in_repo_metrics_root(metrics_root: str) -> str:
    """Create in-repository metrics directories without initializing a separate git repo."""
    os.makedirs(os.path.join(metrics_root, "script_run_metrics"), exist_ok=True)
    os.makedirs(os.path.join(metrics_root, "benchmark_run_metrics"), exist_ok=True)
    return metrics_root


def _missing_reachability_repo_requirements(repo_root: str) -> list[str]:
    """Return missing requirements for a complete reachability-metadata checkout."""
    missing: list[str] = []
    if not os.path.isdir(repo_root):
        return ["directory"]
    if not _is_git_checkout(repo_root):
        missing.append("git checkout or worktree")
    if not os.path.isfile(os.path.join(repo_root, "gradlew")):
        missing.append("gradlew")
    if not (
            os.path.isfile(os.path.join(repo_root, "settings.gradle"))
            or os.path.isfile(os.path.join(repo_root, "settings.gradle.kts"))
    ):
        missing.append("settings.gradle or settings.gradle.kts")
    if not (
            os.path.isfile(os.path.join(repo_root, "build.gradle"))
            or os.path.isfile(os.path.join(repo_root, "build.gradle.kts"))
    ):
        missing.append("build.gradle or build.gradle.kts")
    for directory in (get_forge_subdir_name(), "metadata", "tests", "gradle"):
        if not os.path.isdir(os.path.join(repo_root, directory)):
            missing.append(f"{directory}/")
    for file_path in (
            os.path.join("gradle", "wrapper", "gradle-wrapper.jar"),
            os.path.join("gradle", "wrapper", "gradle-wrapper.properties"),
    ):
        if not os.path.isfile(os.path.join(repo_root, file_path)):
            missing.append(file_path)
    return missing


def _looks_like_reachability_metadata_repo(repo_root: str) -> bool:
    """Return True when the path has the expected reachability-metadata repo shape."""
    return not _missing_reachability_repo_requirements(repo_root)


def require_complete_reachability_repo(repo_root: str) -> str:
    """Require a complete reachability-metadata checkout or linked worktree."""
    missing = _missing_reachability_repo_requirements(repo_root)
    if missing:
        print(
            (
                "ERROR: Reachability metadata path must be a complete "
                f"graalvm-reachability-metadata checkout or worktree: {repo_root}. "
                f"Missing: {', '.join(missing)}."
            ),
            file=sys.stderr,
        )
        raise SystemExit(1)
    return os.path.abspath(repo_root)


def resolve_parent_reachability_repo() -> str:
    """Resolve the parent graalvm-reachability-metadata checkout for merged layout runs."""
    forge_root = get_repo_root()
    parent_repo = os.path.dirname(forge_root)
    if _looks_like_reachability_metadata_repo(parent_repo):
        return parent_repo

    print(
        "ERROR: Forge must be inside a "
        "graalvm-reachability-metadata checkout.",
        file=sys.stderr,
    )
    raise SystemExit(1)


def resolve_in_repo_metrics_root(reachability_root: str) -> str:
    """Return the Forge directory inside a reachability-metadata checkout."""
    metrics_root = os.path.join(reachability_root, get_forge_subdir_name())
    if not os.path.isdir(metrics_root):
        print(
            f"ERROR: Expected Forge directory in reachability checkout: {metrics_root}",
            file=sys.stderr,
        )
        raise SystemExit(1)
    return ensure_in_repo_metrics_root(metrics_root)


def metrics_json_repo_relative_path(
        repo_path: str,
        metrics_repo_root: str,
        metrics_json_relative_path: str,
) -> str:
    """Return a metrics JSON path relative to the reachability repo root."""
    return os.path.relpath(
        os.path.join(metrics_repo_root, metrics_json_relative_path),
        repo_path,
    )


def resolve_repo_roots(
        explicit_reachability_path: str | None,
        explicit_metrics_repo_path: str | None,
):
    """Resolve root paths for the target reachability repository and metrics storage."""
    # graalvm-reachability-metadata repo root
    print("[Resolving graalvm-reachability-metadata root path...]")
    if explicit_reachability_path:
        resolved_reachability_root = explicit_reachability_path
    else:
        resolved_reachability_root = resolve_parent_reachability_repo()
    require_complete_reachability_repo(resolved_reachability_root)

    # metrics root
    print("[Resolving Forge metrics root path...]")
    if explicit_metrics_repo_path:
        resolved_metrics_root = ensure_in_repo_metrics_root(explicit_metrics_repo_path)
    else:
        resolved_metrics_root = resolve_in_repo_metrics_root(resolved_reachability_root)

    return resolved_reachability_root, resolved_metrics_root
