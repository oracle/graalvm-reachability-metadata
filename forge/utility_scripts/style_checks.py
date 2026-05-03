# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import subprocess
import sys

from utility_scripts.pi_logs import build_pi_log_path
from utility_scripts.repo_path_resolver import require_complete_reachability_repo
from utility_scripts.task_logs import display_log_path

DEFAULT_PI_MODEL_NAME = "oca/gpt-5.4"
DEFAULT_PI_TIMEOUT_SECONDS = 600
MAX_CHECKSTYLE_OUTPUT_CHARS = 12000
MAX_TEST_OUTPUT_CHARS = 12000
MAX_PI_CHECKSTYLE_ATTEMPTS = 3


def _run_gradle_task(repo_path: str, command: list[str]) -> bool:
    require_complete_reachability_repo(repo_path)
    result = subprocess.run(
        command,
        cwd=repo_path,
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
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=False,
    )


def _build_pi_checkstyle_log_path(coordinates: str) -> str:
    """Build the Pi checkstyle log path under the library log directory."""
    return build_pi_log_path("checkstyle", coordinates)


def _trim_output(output: str, limit: int) -> str:
    """Return the tail of `output` constrained to `limit` characters."""
    if len(output) <= limit:
        return output
    return output[-limit:]


def _append_pi_output(log_path: str, section_name: str, attempt: int, output: str) -> None:
    """Append Pi output for one style-fix step to the log."""
    with open(log_path, "a", encoding="utf-8") as log_file:
        log_file.write(f"=== Pi {section_name} attempt {attempt} ===\n")
        if output:
            log_file.write(output)
            if not output.endswith("\n"):
                log_file.write("\n")
        else:
            log_file.write("<no output>\n")


def _run_pi_checkstyle_fix(
        repo_path: str,
        checkstyle_output: str,
        log_path: str,
        attempt: int,
        model_name: str,
        timeout_seconds: int,
) -> bool:
    """Invoke Pi to fix checkstyle errors shown in the output."""
    trimmed = _trim_output(checkstyle_output, MAX_CHECKSTYLE_OUTPUT_CHARS)
    prompt = "\n".join([
        "Fix all checkstyle errors in the files shown in the output below.",
        "Only modify the lines that cause checkstyle violations — do not refactor or restructure.",
        "",
        "Checkstyle output:",
        "```text",
        trimmed,
        "```",
    ])
    command = ["pi", "-p", "--no-session", "--model", model_name, prompt]
    try:
        result = subprocess.run(
            command,
            cwd=repo_path,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            timeout=timeout_seconds,
            check=False,
        )
    except subprocess.TimeoutExpired as exc:
        _append_pi_output(log_path, "checkstyle", attempt, exc.stdout or "")
        print("ERROR: Pi checkstyle fix timed out.", file=sys.stderr)
        return False

    _append_pi_output(log_path, "checkstyle", attempt, result.stdout or "")

    if result.returncode != 0:
        print(
            f"ERROR: Pi checkstyle fix failed. See {display_log_path(log_path)} for details.",
            file=sys.stderr,
        )
        return False

    return True


def _run_pi_test_fix_after_checkstyle(
        repo_path: str,
        checkstyle_output: str,
        test_output: str,
        log_path: str,
        attempt: int,
        model_name: str,
        timeout_seconds: int,
) -> bool:
    """Invoke Pi to fix a test failure introduced while fixing checkstyle."""
    prompt = "\n".join([
        "Fix the repository so the failing Gradle test command passes.",
        "Only make the minimal changes required to resolve the failure.",
        "",
        "./gradlew test fails with:",
        "```text",
        _trim_output(test_output, MAX_TEST_OUTPUT_CHARS),
        "```",
        "",
        "when checkstyle fix is done for:",
        "```text",
        _trim_output(checkstyle_output, MAX_CHECKSTYLE_OUTPUT_CHARS),
        "```",
    ])
    command = ["pi", "-p", "--no-session", "--model", model_name, prompt]
    try:
        result = subprocess.run(
            command,
            cwd=repo_path,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            timeout=timeout_seconds,
            check=False,
        )
    except subprocess.TimeoutExpired as exc:
        _append_pi_output(log_path, "test-after-checkstyle", attempt, exc.stdout or "")
        print("ERROR: Pi post-checkstyle test fix timed out.", file=sys.stderr)
        return False

    _append_pi_output(log_path, "test-after-checkstyle", attempt, result.stdout or "")

    if result.returncode != 0:
        print(
            f"ERROR: Pi post-checkstyle test fix failed. See {display_log_path(log_path)} for details.",
            file=sys.stderr,
        )
        return False

    return True


def run_style_fix_and_checks(
        repo_path: str,
        coordinates: str,
        model_name: str | None = None,
        timeout_seconds: int = DEFAULT_PI_TIMEOUT_SECONDS,
) -> bool:
    """Apply style fixes and run style checks for the provided library coordinates."""
    coordinate_arg = f"-Pcoordinates={coordinates}"
    pi_model_name = model_name or DEFAULT_PI_MODEL_NAME

    if not _run_gradle_task(repo_path, ["./gradlew", "spotlessApply", coordinate_arg]):
        return False
    if not _run_gradle_task(repo_path, ["./gradlew", "spotlessCheck", coordinate_arg]):
        return False

    checkstyle_result = _run_checkstyle(repo_path, coordinate_arg)
    if checkstyle_result.returncode == 0:
        return True

    print(f"[checkstyle] Gradle command failed: ./gradlew checkstyle {coordinate_arg}", file=sys.stderr)
    log_path = _build_pi_checkstyle_log_path(coordinates)
    print(f"[checkstyle] Pi output: {display_log_path(log_path)}")

    for attempt in range(1, MAX_PI_CHECKSTYLE_ATTEMPTS + 1):
        print(f"[checkstyle] Attempting Pi checkstyle fix ({attempt}/{MAX_PI_CHECKSTYLE_ATTEMPTS})...")
        if not _run_pi_checkstyle_fix(
            repo_path,
            checkstyle_result.stdout,
            log_path,
            attempt,
            pi_model_name,
            timeout_seconds,
        ):
            continue

        checkstyle_result = _run_checkstyle(repo_path, coordinate_arg)
        if checkstyle_result.returncode != 0:
            continue

        test_result = _run_test(repo_path, coordinate_arg)
        if test_result.returncode == 0:
            print("[checkstyle] Pi checkstyle fix succeeded")
            return True

        print(
            f"[checkstyle] ./gradlew test {coordinate_arg} failed after checkstyle fix; attempting Pi recovery...",
            file=sys.stderr,
        )
        if not _run_pi_test_fix_after_checkstyle(
            repo_path=repo_path,
            checkstyle_output=checkstyle_result.stdout,
            test_output=test_result.stdout,
            log_path=log_path,
            attempt=attempt,
            model_name=pi_model_name,
            timeout_seconds=timeout_seconds,
        ):
            return False

        retry_test_result = _run_test(repo_path, coordinate_arg)
        if retry_test_result.returncode != 0:
            print("ERROR: ./gradlew test still fails after Pi post-checkstyle fix.", file=sys.stderr)
            print(retry_test_result.stdout)
            return False

        checkstyle_result = _run_checkstyle(repo_path, coordinate_arg)
        if checkstyle_result.returncode == 0:
            print("[checkstyle] Pi checkstyle fix succeeded")
            return True

    print("[checkstyle] ERROR: Checkstyle still failing after Pi fix.", file=sys.stderr)
    print(checkstyle_result.stdout)
    return False
