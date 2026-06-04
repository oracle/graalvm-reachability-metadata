# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import json
import os
import subprocess
import sys

from utility_scripts.task_logs import build_task_log_path, display_log_path
from utility_scripts.repo_path_resolver import require_complete_reachability_repo
from utility_scripts.gradle_environment import gradle_command_environment

CODEX_MODEL_NAME = "oca/gpt-5.4"
CODEX_TIMEOUT_SECONDS = 1200


def run_codex_metadata_fix(
        reachability_metadata_path: str,
        coordinates: str,
        reproduction_command: str | None = None,
        graalvm_home: str | None = None,
        base_env: dict[str, str] | None = None,
) -> tuple[int, str, bool]:
    """Run Codex to update metadata entries for the target library.

    Requires the ``fix-missing-reachability-metadata`` skill. The skill definition lives at:
    https://github.com/oracle/graalvm-reachability-metadata/blob/master/skills/fix-missing-reachability-metadata/SKILL.md
    """
    reachability_metadata_path = require_complete_reachability_repo(reachability_metadata_path)
    log_path = build_task_log_path("metadata-fix", coordinates, "codex.log")
    log_path_display = display_log_path(log_path)
    codex_env = _codex_environment(reachability_metadata_path, graalvm_home, base_env)
    required_graalvm_home = codex_env.get("GRAALVM_HOME")
    if not required_graalvm_home or not _has_native_image(required_graalvm_home):
        print(
            "ERROR: Codex metadata fix requires the exact GraalVM home from the failed run, "
            "but no GraalVM distribution with bin/native-image could be resolved.",
            file=sys.stderr,
        )
        return (1, log_path, False)
    graalvm_version = _native_image_version(required_graalvm_home, codex_env)
    developer_instructions = _codex_graalvm_instructions(required_graalvm_home, graalvm_version)
    prompt = f"Fix the metadata entries for {coordinates}"
    prompt += (
        "\n\nRequired GraalVM for every reproduction and verification command:\n"
        f"- GRAALVM_HOME={required_graalvm_home}\n"
        f"- JAVA_HOME={required_graalvm_home}\n"
        f"- native-image --version:\n{graalvm_version}\n"
        "\nUse this exact GraalVM distribution. Do not switch to another GraalVM, "
        "even if another `native-image` appears earlier on PATH."
    )
    if reproduction_command:
        prompt += f"\n\nReproduce the failure with:\n{reproduction_command}"
    cmd = [
        "codex", "exec",
        "--dangerously-bypass-approvals-and-sandbox",
        "--json",
        "-c", 'reasoning.effort="high"',
        "-c", f"developer_instructions={json.dumps(developer_instructions)}",
        "-m", CODEX_MODEL_NAME,
        prompt,
    ]
    print(f"[Codex running... Output: {log_path_display}]")
    try:
        with open(log_path, "w", encoding="utf-8") as log_file:
            result = subprocess.run(
                cmd,
                cwd=reachability_metadata_path,
                env=codex_env,
                stdout=log_file,
                stderr=subprocess.STDOUT,
                timeout=CODEX_TIMEOUT_SECONDS,
                check=False,
            )
    except subprocess.TimeoutExpired:
        print(
            f"ERROR: Codex metadata fix timed out after {CODEX_TIMEOUT_SECONDS} seconds for {coordinates}.",
            file=sys.stderr,
        )
        return (1, log_path, True)

    if result.returncode != 0:
        print(
            f"ERROR: Codex metadata fix failed for {coordinates} with exit code {result.returncode}.",
            file=sys.stderr,
        )
    return (result.returncode, log_path, False)


def _codex_environment(
        reachability_metadata_path: str,
        graalvm_home: str | None,
        base_env: dict[str, str] | None,
) -> dict[str, str]:
    env = dict(base_env or os.environ)
    if graalvm_home:
        env["GRAALVM_HOME"] = graalvm_home
        env["JAVA_HOME"] = graalvm_home
    return gradle_command_environment(reachability_metadata_path, env)


def _has_native_image(graalvm_home: str) -> bool:
    return os.path.isfile(os.path.join(graalvm_home, "bin", "native-image"))


def _native_image_version(graalvm_home: str, env: dict[str, str]) -> str:
    native_image = os.path.join(graalvm_home, "bin", "native-image")
    try:
        result = subprocess.run(
            [native_image, "--version"],
            env=env,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            check=False,
            timeout=30,
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        return f"<unable to run {native_image} --version: {exc}>"
    return result.stdout.strip() or f"<native-image --version exited {result.returncode} without output>"


def _codex_graalvm_instructions(graalvm_home: str, graalvm_version: str) -> str:
    return (
        "Hard requirement for this metadata-fix run: every Gradle, Java, and native-image command "
        "must use the exact same GraalVM distribution as the failed run.\n"
        f"Set and preserve GRAALVM_HOME={graalvm_home}\n"
        f"Set and preserve JAVA_HOME={graalvm_home}\n"
        "Do not use another GraalVM installation from PATH, SDKMAN, latest_graalvm_home, or any symlink "
        "unless it resolves to this same distribution.\n"
        "Before verifying, check `native-image --version` and ensure it matches this required version:\n"
        f"{graalvm_version}\n"
        "If this GraalVM distribution is unavailable, fail instead of reproducing or verifying with another version."
    )
