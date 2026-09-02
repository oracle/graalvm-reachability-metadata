# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import sys
from collections.abc import Callable

from ai_workflows.agents.agent_runtime import analysis_agent_run, get_analysis_agent
from utility_scripts.gradle_environment import gradle_command_environment
from utility_scripts.logged_command import LoggedCommandResult, run_logged_command
from utility_scripts.repo_path_resolver import require_complete_reachability_repo
from utility_scripts.run_location import (
    PHASE_FINALIZATION,
    STEP_AGENT_FIX,
    current_run_location,
    log_step_progress,
    record_step_failure,
    run_step,
)
from utility_scripts.stage_logger import log_detail
from utility_scripts.task_logs import build_timestamped_task_log_path, display_log_path

DEFAULT_STYLE_FIX_TIMEOUT_SECONDS = 600
MAX_CHECKSTYLE_OUTPUT_CHARS = 12000
MAX_TEST_OUTPUT_CHARS = 12000
MAX_CHECKSTYLE_FIX_ATTEMPTS = 3


def _run_finalization_agent_fix(
        coordinates: str,
        target: str,
        attempt: int,
        terminal_on_failure: bool,
        operation: Callable[[], bool],
) -> bool:
    """Run one style repair with concise finalization progress when bound.

    §FS-forge-run-output-legibility.1 §FS-forge-run-output-legibility.5
    """
    location = current_run_location()
    if location is None or location.phase != PHASE_FINALIZATION:
        return operation()

    with run_step(PHASE_FINALIZATION, STEP_AGENT_FIX, operand=f"{coordinates} {target}"):
        log_step_progress(
            PHASE_FINALIZATION,
            STEP_AGENT_FIX,
            f"Running agent fix for {target} on {coordinates} "
            f"(attempt {attempt}/{MAX_CHECKSTYLE_FIX_ATTEMPTS})",
        )
        fixed = operation()
        outcome = "completed" if fixed else "failed"
        log_step_progress(
            PHASE_FINALIZATION,
            STEP_AGENT_FIX,
            f"Agent fix {outcome} for {target} on {coordinates} "
            f"(attempt {attempt}/{MAX_CHECKSTYLE_FIX_ATTEMPTS})",
        )
        if not fixed and terminal_on_failure:
            record_step_failure()
        return fixed


def _run_logged_style_command(repo_path: str, command: list[str]) -> LoggedCommandResult:
    require_complete_reachability_repo(repo_path)
    coordinate_argument = next(
        (argument for argument in command if argument.startswith("-Pcoordinates=")),
        "-Pcoordinates=unknown",
    )
    return run_logged_command(
        command,
        cwd=repo_path,
        task_type="style-checks",
        subject=coordinate_argument.removeprefix("-Pcoordinates="),
        action=command[1],
        env=gradle_command_environment(repo_path),
        stage="style-checks",
    )


def _run_gradle_task(repo_path: str, command: list[str]) -> bool:
    return _run_logged_style_command(repo_path, command).returncode == 0


def _run_checkstyle(repo_path: str, coordinate_arg: str) -> LoggedCommandResult:
    """Run the checkstyle Gradle task and return its logged result."""
    return _run_logged_style_command(
        repo_path,
        ["./gradlew", "checkstyle", coordinate_arg],
    )


def _run_test(repo_path: str, coordinate_arg: str) -> LoggedCommandResult:
    """Run the test Gradle task and return its logged result."""
    return _run_logged_style_command(
        repo_path,
        ["./gradlew", "test", coordinate_arg],
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
    log_detail("checkstyle", f"Analysis-agent output: {display_log_path(log_path)}")

    for attempt in range(1, MAX_CHECKSTYLE_FIX_ATTEMPTS + 1):
        log_detail(
            "checkstyle",
            "Attempting analysis-agent Checkstyle fix "
            f"({attempt}/{MAX_CHECKSTYLE_FIX_ATTEMPTS})...",
        )
        if not _run_finalization_agent_fix(
            coordinates,
            "Checkstyle",
            attempt,
            attempt == MAX_CHECKSTYLE_FIX_ATTEMPTS,
            lambda: _run_analysis_checkstyle_fix(
                repo_path,
                coordinates,
                checkstyle_result.stdout,
                log_path,
                attempt,
                timeout_seconds,
            ),
        ):
            continue

        checkstyle_result = _run_checkstyle(repo_path, coordinate_arg)
        if checkstyle_result.returncode != 0:
            continue

        test_result = _run_test(repo_path, coordinate_arg)
        if test_result.returncode == 0:
            log_detail("checkstyle", "Analysis-agent Checkstyle fix succeeded")
            return True

        print(
            f"[checkstyle] ./gradlew test {coordinate_arg} failed after Checkstyle fix; "
            "attempting analysis-agent recovery...",
            file=sys.stderr,
        )
        if not _run_finalization_agent_fix(
            coordinates,
            "test after Checkstyle repair",
            attempt,
            True,
            lambda: _run_analysis_test_fix_after_checkstyle(
                repo_path=repo_path,
                coordinates=coordinates,
                checkstyle_output=checkstyle_result.stdout,
                test_output=test_result.stdout,
                log_path=log_path,
                attempt=attempt,
                timeout_seconds=timeout_seconds,
            ),
        ):
            return False

        retry_test_result = _run_test(repo_path, coordinate_arg)
        if retry_test_result.returncode != 0:
            print("ERROR: ./gradlew test still fails after post-Checkstyle repair.", file=sys.stderr)
            return False

        checkstyle_result = _run_checkstyle(repo_path, coordinate_arg)
        if checkstyle_result.returncode == 0:
            log_detail("checkstyle", "Analysis-agent Checkstyle fix succeeded")
            return True

    print("[checkstyle] ERROR: Checkstyle still fails after analysis repair.", file=sys.stderr)
    return False
