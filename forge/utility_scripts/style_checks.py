# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import subprocess
import sys

from ai_workflows.agents.agent_runtime import analysis_agent_run, get_analysis_agent
from utility_scripts.gradle_environment import gradle_command_environment
from utility_scripts.repo_path_resolver import require_complete_reachability_repo
from utility_scripts.task_logs import build_timestamped_task_log_path, display_log_path

DEFAULT_STYLE_FIX_TIMEOUT_SECONDS = 600
MAX_CHECKSTYLE_OUTPUT_CHARS = 12000
MAX_TEST_OUTPUT_CHARS = 12000
MAX_CHECKSTYLE_FIX_ATTEMPTS = 3


def _run_gradle_task(repo_path: str, command: list[str]) -> bool:
    require_complete_reachability_repo(repo_path)
    result = subprocess.run(
        command,
        cwd=repo_path,
        env=gradle_command_environment(repo_path),
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=False,
    )
    if result.returncode == 0:
        return True
    print(f"ERROR: Gradle command failed: {' '.join(command)}", file=sys.stderr)
    print(result.stdout)
    return False


def _run_checkstyle(repo_path: str, coordinate_arg: str) -> subprocess.CompletedProcess:
    """Run the checkstyle Gradle task and return the CompletedProcess."""
    require_complete_reachability_repo(repo_path)
    return subprocess.run(
        ["./gradlew", "checkstyle", coordinate_arg],
        cwd=repo_path,
        env=gradle_command_environment(repo_path),
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=False,
    )


def _run_test(repo_path: str, coordinate_arg: str) -> subprocess.CompletedProcess:
    """Run the test Gradle task and return the CompletedProcess."""
    require_complete_reachability_repo(repo_path)
    return subprocess.run(
        ["./gradlew", "test", coordinate_arg],
        cwd=repo_path,
        env=gradle_command_environment(repo_path),
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=False,
    )


def _build_checkstyle_log_path(coordinates: str) -> str:
    """Build the analysis-agent Checkstyle log path."""
    return build_timestamped_task_log_path("checkstyle", coordinates, "analysis")


def _trim_output(output: str, limit: int) -> str:
    """Return the tail of `output` constrained to `limit` characters."""
    if len(output) <= limit:
        return output
    return output[-limit:]


def _append_analysis_output(log_path: str, section_name: str, attempt: int, output: str) -> None:
    """Append analysis-agent output for one style-fix step to the log."""
    with open(log_path, "a", encoding="utf-8") as log_file:
        log_file.write(f"=== Analysis agent {section_name} attempt {attempt} ===\n")
        if output:
            log_file.write(output)
            if not output.endswith("\n"):
                log_file.write("\n")
        else:
            log_file.write("<no output>\n")


def _run_analysis_checkstyle_fix(
        repo_path: str,
        coordinates: str,
        checkstyle_output: str,
        log_path: str,
        attempt: int,
        timeout_seconds: int,
) -> bool:
    """Invoke the analysis agent on the captured Checkstyle failure."""
    analysis_backend = get_analysis_agent().backend
    trimmed = _trim_output(checkstyle_output, MAX_CHECKSTYLE_OUTPUT_CHARS)
    prompt = "\n".join([
        f"The deterministic Checkstyle step failed for {coordinates}.",
        "Fix all Checkstyle errors in the files shown in the captured output.",
        "Only modify the lines that cause checkstyle violations — do not refactor or restructure.",
        "Forge will rerun Checkstyle and the coordinate test after this repair.",
        "",
        "Exact command:",
        f"./gradlew checkstyle -Pcoordinates={coordinates}",
        "",
        "Captured failure output:",
        "```text",
        trimmed,
        "```",
    ])
    result = analysis_agent_run(
        working_dir=repo_path,
        context=prompt,
        task_type="checkstyle",
        library=coordinates,
        timeout=timeout_seconds,
    )
    _append_analysis_output(log_path, "checkstyle", attempt, result.response)
    if result.return_code != 0:
        print(
            f"ERROR: {analysis_backend} checkstyle fix failed. "
            f"See {display_log_path(log_path)} for details.",
            file=sys.stderr,
        )
        return False

    return True


def _run_analysis_test_fix_after_checkstyle(
        repo_path: str,
        coordinates: str,
        checkstyle_output: str,
        test_output: str,
        log_path: str,
        attempt: int,
        timeout_seconds: int,
) -> bool:
    """Invoke the analysis agent after a Checkstyle repair breaks the coordinate test."""
    analysis_backend = get_analysis_agent().backend
    prompt = "\n".join([
        "Fix the repository so the failing Gradle test command passes.",
        "Only make the minimal changes required to resolve the failure.",
        "",
        "./gradlew test fails with:",
        "```text",
        _trim_output(test_output, MAX_TEST_OUTPUT_CHARS),
        "```",
        "",
        "when the Checkstyle repair was made for:",
        "```text",
        _trim_output(checkstyle_output, MAX_CHECKSTYLE_OUTPUT_CHARS),
        "```",
    ])
    result = analysis_agent_run(
        working_dir=repo_path,
        context=prompt,
        task_type="test-after-checkstyle",
        library=coordinates,
        timeout=timeout_seconds,
    )
    _append_analysis_output(log_path, "test-after-checkstyle", attempt, result.response)
    if result.return_code != 0:
        print(
            f"ERROR: {analysis_backend} post-Checkstyle test fix failed. "
            f"See {display_log_path(log_path)} for details.",
            file=sys.stderr,
        )
        return False
    return True


def run_style_fix_and_checks(
        repo_path: str,
        coordinates: str,
        timeout_seconds: int = DEFAULT_STYLE_FIX_TIMEOUT_SECONDS,
) -> bool:
    """Apply style fixes and run style checks for the provided library coordinates."""
    coordinate_arg = f"-Pcoordinates={coordinates}"

    if not _run_gradle_task(repo_path, ["./gradlew", "spotlessApply", coordinate_arg]):
        return False
    if not _run_gradle_task(repo_path, ["./gradlew", "spotlessCheck", coordinate_arg]):
        return False

    checkstyle_result = _run_checkstyle(repo_path, coordinate_arg)
    if checkstyle_result.returncode == 0:
        return True

    print(f"[checkstyle] Gradle command failed: ./gradlew checkstyle {coordinate_arg}", file=sys.stderr)
    log_path = _build_checkstyle_log_path(coordinates)
    print(f"[checkstyle] Analysis-agent output: {display_log_path(log_path)}")

    for attempt in range(1, MAX_CHECKSTYLE_FIX_ATTEMPTS + 1):
        print(
            "[checkstyle] Attempting analysis-agent Checkstyle fix "
            f"({attempt}/{MAX_CHECKSTYLE_FIX_ATTEMPTS})..."
        )
        if not _run_analysis_checkstyle_fix(
            repo_path,
            coordinates,
            checkstyle_result.stdout,
            log_path,
            attempt,
            timeout_seconds,
        ):
            continue

        checkstyle_result = _run_checkstyle(repo_path, coordinate_arg)
        if checkstyle_result.returncode != 0:
            continue

        test_result = _run_test(repo_path, coordinate_arg)
        if test_result.returncode == 0:
            print("[checkstyle] Analysis-agent Checkstyle fix succeeded")
            return True

        print(
            f"[checkstyle] ./gradlew test {coordinate_arg} failed after Checkstyle fix; "
            "attempting analysis-agent recovery...",
            file=sys.stderr,
        )
        if not _run_analysis_test_fix_after_checkstyle(
            repo_path=repo_path,
            coordinates=coordinates,
            checkstyle_output=checkstyle_result.stdout,
            test_output=test_result.stdout,
            log_path=log_path,
            attempt=attempt,
            timeout_seconds=timeout_seconds,
        ):
            return False

        retry_test_result = _run_test(repo_path, coordinate_arg)
        if retry_test_result.returncode != 0:
            print("ERROR: ./gradlew test still fails after post-Checkstyle repair.", file=sys.stderr)
            print(retry_test_result.stdout)
            return False

        checkstyle_result = _run_checkstyle(repo_path, coordinate_arg)
        if checkstyle_result.returncode == 0:
            print("[checkstyle] Analysis-agent Checkstyle fix succeeded")
            return True

    print("[checkstyle] ERROR: Checkstyle still fails after analysis repair.", file=sys.stderr)
    print(checkstyle_result.stdout)
    return False
