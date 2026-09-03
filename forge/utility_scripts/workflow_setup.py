# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os
import sys

from utility_scripts.gradle_environment import gradle_command_environment, pin_gradle_java_home
from utility_scripts.host_requirements import (
    GRAALVM_SCHEMA_PATH,
    check_graalvm_installation,
    require_graalvm_home_env,
)
from utility_scripts.library_finalization import run_library_finalization
from utility_scripts.logged_command import LoggedCommandResult, run_logged_command
from utility_scripts.repo_path_resolver import require_complete_reachability_repo, resolve_repo_roots
from utility_scripts.stage_logger import log_stage


def list_all_files(directory_path: str) -> list[str]:
    """Recursively list all file paths under the provided directory."""
    files: list[str] = []
    if not directory_path or not os.path.exists(directory_path):
        return files
    for root_dir, _, file_names in os.walk(directory_path):
        for file_name in file_names:
            files.append(os.path.join(root_dir, file_name))
    return files


def resolve_workflow_repo_paths(
        explicit_repo_path: str | None,
        explicit_metrics_repo_path: str | None,
        metrics_subdir: str = "script_run_metrics",
) -> tuple[str, str, str]:
    """Resolve the reachability repo, metrics output dir, and metrics root for a driver run."""
    resolved_reachability_repo, resolved_metrics_repo = resolve_repo_roots(
        explicit_repo_path,
        explicit_metrics_repo_path,
    )
    resolved_metrics_dir = os.path.join(resolved_metrics_repo, metrics_subdir)
    os.makedirs(resolved_metrics_dir, exist_ok=True)
    return resolved_reachability_repo, resolved_metrics_dir, resolved_metrics_repo


def resolve_graalvm_java_home() -> str:
    """
    Align every Java selector on the first Forge-usable GraalVM in the environment.
    Logic:
    - Take GRAALVM_HOME, then JAVA_HOME, and keep the first that satisfies
      `check_graalvm_installation` — the single Forge-usable GraalVM rule
      (§FS-forge-host-requirements) — for Java, GraalVM, and Gradle selectors.
    - Require GRAALVM_HOME_25_0 for the post-generation GraalVM 25 validation lane.
    - Otherwise, print why each candidate was rejected and exit(1).
    """
    rejected: list[str] = []
    for variable in ("GRAALVM_HOME", "JAVA_HOME"):
        home = os.environ.get(variable)
        if not home:
            continue
        problems = check_graalvm_installation(home, os.environ)
        if problems:
            rejected.append(f"  {variable}={home}: {'; '.join(problems)}")
            continue
        pin_gradle_java_home(os.environ, home)
        require_graalvm_home_env("GRAALVM_HOME_25_0")
        return home

    print("ERROR: Unable to locate a GraalVM that can run Forge work in GRAALVM_HOME or JAVA_HOME.", file=sys.stderr)
    for rejection in rejected:
        print(rejection, file=sys.stderr)
    print(
        "Fix: export `GRAALVM_HOME=/absolute/path/to/a/graalvm` that provides Native Image, "
        "its agent, and "
        f"{GRAALVM_SCHEMA_PATH}.",
        file=sys.stderr,
    )
    sys.exit(1)


def build_graalvm_environment(graalvm_home: str, base_env: dict[str, str] | None = None) -> dict[str, str]:
    """Return an environment configured to run Gradle with the provided GraalVM."""
    env = dict(base_env or os.environ)
    pin_gradle_java_home(env, graalvm_home)
    return env


def run_gradle_test_with_graalvm(repo_path: str, library: str, graalvm_home: str) -> LoggedCommandResult:
    """Run the library test task with a specific GraalVM/JAVA_HOME."""
    require_complete_reachability_repo(repo_path)
    return run_logged_command(
        ["./gradlew", "test", f"-Pcoordinates={library}"],
        cwd=repo_path,
        task_type="post-generation-test",
        subject=library,
        action="test",
        env=gradle_command_environment(repo_path, build_graalvm_environment(graalvm_home)),
        stage="post-generation-test",
    )


def run_metadata_fix_until_tests_pass(
        repo_path: str,
        library: str,
        graalvm_home: str,
        graalvm_env_var_name: str,
        max_attempts: int = 5,
        finalize_on_success: bool = False,
) -> bool:
    """Run tests with a specific GraalVM and iterate metadata fixes until they pass."""
    for attempt in range(1, max_attempts + 1):
        log_stage(
            "post-generation-test",
            f"Running Gradle test for {library} with {graalvm_env_var_name} (attempt {attempt}/{max_attempts})",
        )
        result = run_gradle_test_with_graalvm(repo_path, library, graalvm_home)
        if result.returncode == 0:
            log_stage("post-generation-test", f"Gradle test passed for {library} with {graalvm_env_var_name}")
            if finalize_on_success:
                group, artifact, library_version = library.split(":")
                if not run_library_finalization(
                    repo_path=repo_path,
                    library=library,
                    group=group,
                    artifact=artifact,
                    library_version=library_version,
                    log_prefix=f"[post-generation:{graalvm_env_var_name}]",
                ):
                    return False
            return True

        if attempt == max_attempts:
            break

        # Imported here because `ai_workflows.core` imports this module while initializing.
        from ai_workflows.core.metadata_fix import run_metadata_fix

        log_stage("metadata-fix", f"Running Codex metadata fix for {library} after {graalvm_env_var_name} failure")
        codex_rc, _codex_log_path, codex_timed_out = run_metadata_fix(
            repo_path,
            library,
            graalvm_home=graalvm_home,
            base_env=build_graalvm_environment(graalvm_home),
        )
        if codex_timed_out:
            print(
                f"ERROR: Codex metadata fix timed out while validating {library} with {graalvm_env_var_name}.",
                file=sys.stderr,
            )
            return False
        if codex_rc != 0:
            print(
                f"ERROR: Codex metadata fix failed while validating {library} with {graalvm_env_var_name}.",
                file=sys.stderr,
            )
            return False

    print(
        f"ERROR: Gradle test still fails for {library} with {graalvm_env_var_name} after {max_attempts} attempts.",
        file=sys.stderr,
    )
    return False


def validate_repo_paths(reachability_repo_path: str, metrics_repo_path: str) -> None:
    """Validate required repository paths for workflow execution."""
    require_complete_reachability_repo(reachability_repo_path)

    if not os.path.exists(metrics_repo_path):
        print(f"ERROR: Metrics repository path does not exist: {os.path.relpath(metrics_repo_path)}", file=sys.stderr)
        sys.exit(1)
