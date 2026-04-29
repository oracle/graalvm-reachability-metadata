# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os
import subprocess
import sys

from ai_workflows.fix_metadata_codex import run_codex_metadata_fix
from utility_scripts.library_finalization import run_library_finalization
from utility_scripts.stage_logger import log_stage


def resolve_graalvm_java_home():
    """
    Resolve GRAALV_HOME/JAVA_HOME from environment variables.
    Logic:
    - If GRAALVM_HOME is set and contains bin/native-image, use it for both GRAALVM_HOME and JAVA_HOME.
    - Else, if JAVA_HOME is set and contains bin/native-image, use it for both GRAALVM_HOME and JAVA_HOME.
    - Otherwise, print an error and exit(1).
    """

    def has_native_image(home: str):
        native_image_path = os.path.join(home, "bin", "native-image")
        return os.path.isfile(native_image_path)

    graalvm_home_env = os.environ.get("GRAALVM_HOME")
    if graalvm_home_env and has_native_image(graalvm_home_env):
        os.environ["GRAALVM_HOME"] = graalvm_home_env
        os.environ["JAVA_HOME"] = graalvm_home_env
        return graalvm_home_env

    java_home_env = os.environ.get("JAVA_HOME")
    if java_home_env and has_native_image(java_home_env):
        os.environ["GRAALVM_HOME"] = java_home_env
        os.environ["JAVA_HOME"] = java_home_env
        return java_home_env

    print(
        "ERROR: Unable to locate a GraalVM Java home with native-image. "
        "Please set GRAALVM_HOME or JAVA_HOME to a GraalVM distribution where bin/native-image exists."
    )
    sys.exit(1)


def has_native_image(home: str) -> bool:
    """Return True when the provided GraalVM home contains native-image."""
    native_image_path = os.path.join(home, "bin", "native-image")
    return os.path.isfile(native_image_path)


def require_graalvm_home_env(env_var_name: str) -> str:
    """Require a GraalVM home environment variable that contains native-image."""
    graalvm_home = os.environ.get(env_var_name)
    if not graalvm_home:
        print(f"ERROR: Required environment variable '{env_var_name}' is not set.", file=sys.stderr)
        sys.exit(1)
    if not has_native_image(graalvm_home):
        print(
            f"ERROR: Environment variable '{env_var_name}' must point to a GraalVM distribution "
            f"where {os.path.join('bin', 'native-image')} exists.",
            file=sys.stderr,
        )
        sys.exit(1)
    return graalvm_home


def build_graalvm_environment(graalvm_home: str, base_env: dict[str, str] | None = None) -> dict[str, str]:
    """Return an environment configured to run Gradle with the provided GraalVM."""
    env = dict(base_env or os.environ)
    env["GRAALVM_HOME"] = graalvm_home
    env["JAVA_HOME"] = graalvm_home
    return env


def run_gradle_test_with_graalvm(repo_path: str, library: str, graalvm_home: str) -> subprocess.CompletedProcess[str]:
    """Run the library test task with a specific GraalVM/JAVA_HOME."""
    return subprocess.run(
        ["./gradlew", "test", f"-Pcoordinates={library}"],
        cwd=repo_path,
        env=build_graalvm_environment(graalvm_home),
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=False,
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

        print(result.stdout)
        if attempt == max_attempts:
            break

        log_stage("metadata-fix", f"Running Codex metadata fix for {library} after {graalvm_env_var_name} failure")
        codex_rc, _codex_log_path, codex_timed_out = run_codex_metadata_fix(repo_path, library)
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


def validate_repo_paths(reachability_repo_path: str, metrics_repo_path: str):
    """Validate required repository paths for workflow execution."""
    if not os.path.exists(reachability_repo_path):
        print(f"ERROR: Repository path does not exist: {os.path.relpath(reachability_repo_path)}")
        sys.exit(1)

    if not os.path.exists(metrics_repo_path):
        print(f"ERROR: Metrics repository path does not exist: {os.path.relpath(metrics_repo_path)}")
        sys.exit(1)
