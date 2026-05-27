# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os
import subprocess
import sys

from utility_scripts.pi_logs import build_pi_log_path
from utility_scripts.stage_logger import log_stage
from utility_scripts.task_logs import display_log_path

DEFAULT_PI_TIMEOUT_SECONDS = 600
DEFAULT_MAX_TEST_OUTPUT_CHARS = 12000
POST_GENERATION_STAGE_METADATA_FIX_FAILED = "metadata_fix_failed"


def _trim_text(text: str, limit: int) -> str:
    """Return the tail of `text` constrained to `limit` characters."""
    if len(text) <= limit:
        return text
    return text[-limit:]


def _build_intervention_path(reachability_metadata_path: str, coordinates: str) -> str:
    """Build the intervention markdown path for a library."""
    safe_name = coordinates.replace(":", "_")
    return os.path.join(reachability_metadata_path, "post-gen-interventions", f"{safe_name}.md")


def _repo_relative_path(path: str, repo_path: str) -> str:
    """Return a path relative to the given repository root."""
    return os.path.relpath(os.path.abspath(path), os.path.abspath(repo_path))


def _build_prompt(
        coordinates: str,
        codex_log_path: str,
        test_output: str,
        intervention_path: str,
        max_test_output_chars: int,
) -> str:
    """Build the Pi post-generation intervention prompt."""
    return "\n".join(
        [
            "You are handling a post-generation workflow failure in the current repository.",
            "Inspect the Codex metadata-fix log and the Gradle failure output.",
            "",
            "Determine the root cause of each test failure:",
            "",
            "1. If the failure is NOT metadata-related (e.g. a native-image limitation, a test bug,",
            "   or an unsupported platform feature), remove the generated test that causes that failure.",
            "   You may also remove generated test-only files that are only needed by that failing test.",
            "",
            "2. If the failure IS metadata-related (e.g. Codex did not finish or only partially fixed",
            "   missing metadata), do NOT remove the test. Instead, describe in the report what metadata",
            "   is still missing and why Codex could not resolve it.",
            "",
            "Do not modify metadata files.",
            "",
            "Write a Markdown report to the exact report path below.",
            "The report must include:",
            "- the exact library line requested below",
            "- a short summary of what failed",
            "- why the remaining generated support should still be preserved",
            "",
            "Task details:",
            f"- Library: {coordinates}",
            f"- Stage: {POST_GENERATION_STAGE_METADATA_FIX_FAILED}",
            f"- Codex log path: {codex_log_path}",
            f"- Intervention report path: {intervention_path}",
            "",
            f"The report must contain the literal line `Library: {coordinates}` near the top.",
            "```",
            "",
            "Gradle failure output excerpt:",
            "```text",
            _trim_text(test_output, max_test_output_chars),
            "```",
        ]
    )


def _write_pi_post_generation_log(log_path: str, output: str) -> None:
    """Write Pi post-generation output to the log file."""
    with open(log_path, "w", encoding="utf-8") as log_file:
        log_file.write(output or "")


def run_pi_post_generation_fix(
        reachability_metadata_path: str,
        coordinates: str,
        codex_log_path: str,
        test_output: str,
        model_name: str,
        timeout_seconds: int = DEFAULT_PI_TIMEOUT_SECONDS,
        max_test_output_chars: int = DEFAULT_MAX_TEST_OUTPUT_CHARS,
) -> tuple[int, str, bool]:
    """Run Pi to perform post-generation intervention and write the markdown artifact."""
    log_stage("post-generation-fix", f"Running Pi post-generation fix for {coordinates}")
    log_path = build_pi_log_path("post-gen", coordinates)
    log_path_display = display_log_path(log_path)
    log_stage("post-generation-fix", f"Pi post-generation output: {log_path_display}")
    intervention_path = _build_intervention_path(reachability_metadata_path, coordinates)
    intervention_path_display = _repo_relative_path(intervention_path, reachability_metadata_path)
    os.makedirs(os.path.dirname(intervention_path), exist_ok=True)
    if os.path.exists(intervention_path):
        os.remove(intervention_path)

    prompt = _build_prompt(
        coordinates=coordinates,
        codex_log_path=display_log_path(codex_log_path),
        test_output=test_output,
        intervention_path=intervention_path_display,
        max_test_output_chars=max_test_output_chars,
    )
    command = ["pi", "-p", "--no-session", "--model", model_name, prompt]
    try:
        result = subprocess.run(
            command,
            cwd=reachability_metadata_path,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            timeout=timeout_seconds,
            check=False,
        )
    except subprocess.TimeoutExpired as exc:
        _write_pi_post_generation_log(log_path, exc.stdout or "")
        print(
            f"ERROR: Pi post-generation intervention timed out for {coordinates}.",
            file=sys.stderr,
        )
        return (1, intervention_path, True)

    _write_pi_post_generation_log(log_path, result.stdout or "")

    if result.returncode != 0:
        print(
            f"ERROR: Pi post-generation intervention failed for {coordinates}. See {log_path_display} for details.",
            file=sys.stderr,
        )
        return (1, intervention_path, False)

    if not os.path.isfile(intervention_path):
        print(
            f"ERROR: Pi post-generation intervention did not write report {intervention_path_display} for {coordinates}.",
            file=sys.stderr,
        )
        return (1, intervention_path, False)

    with open(intervention_path, "r", encoding="utf-8") as intervention_file:
        contents = intervention_file.read()
    if f"Library: {coordinates}" not in contents:
        print(
            f"ERROR: Intervention report {intervention_path_display} does not contain the library name for {coordinates}.",
            file=sys.stderr,
        )
        return (1, intervention_path, False)

    log_stage("post-generation-fix", f"Pi post-generation fix completed for {coordinates}")
    return (0, intervention_path, False)
