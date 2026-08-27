# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os
import subprocess
import sys

from ai_workflows.agents.agent_runtime import analysis_agent_run
from utility_scripts.task_logs import build_task_log_path, display_log_path
from utility_scripts.host_requirements import check_graalvm_installation
from utility_scripts.repo_path_resolver import require_complete_reachability_repo
from utility_scripts.gradle_environment import gradle_command_environment

METADATA_FIX_TIMEOUT_SECONDS = 1200


def run_metadata_fix(
        reachability_metadata_path: str,
        coordinates: str,
        reproduction_command: str | None = None,
        graalvm_home: str | None = None,
        base_env: dict[str, str] | None = None,
        library_preparation_preflight_context: str | None = None,
) -> tuple[int, str, bool]:
    """Run the configured analysis agent to update metadata entries.

    Requires the ``fix-missing-reachability-metadata`` skill. The skill definition lives at:
    https://github.com/oracle/graalvm-reachability-metadata/blob/master/skills/fix-missing-reachability-metadata/SKILL.md
    """
    reachability_metadata_path = require_complete_reachability_repo(reachability_metadata_path)
    fallback_log_path = build_task_log_path("metadata-fix", coordinates, "metadata-fix.log")
    agent_env = _agent_environment(reachability_metadata_path, graalvm_home, base_env)
    required_graalvm_home = agent_env.get("GRAALVM_HOME")
    problems = check_graalvm_installation(required_graalvm_home) if required_graalvm_home else ["GRAALVM_HOME is unset"]
    if problems:
        print(
            "ERROR: Metadata fix requires the exact GraalVM home from the failed run, "
            f"but that home cannot run Forge work: {'; '.join(problems)}.",
            file=sys.stderr,
        )
        return (1, fallback_log_path, False)
    graalvm_version = _native_image_version(required_graalvm_home, agent_env)
    developer_instructions = _graalvm_instructions(required_graalvm_home, graalvm_version)
    skill_path = os.path.join(
        reachability_metadata_path,
        "skills",
        "fix-missing-reachability-metadata",
        "SKILL.md",
    )
    if not os.path.isfile(skill_path):
        skill_path = os.path.abspath(
            os.path.join(
                os.path.dirname(__file__),
                "..", "..", "..",
                "skills", "fix-missing-reachability-metadata", "SKILL.md",
            )
        )
    with open(skill_path, "r", encoding="utf-8") as skill_file:
        developer_instructions += f"\n\nLocal metadata-fix procedure:\n{skill_file.read()}"
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
    if library_preparation_preflight_context:
        prompt += (
            "\n\nLibrary preparation preflight context:\n"
            f"{library_preparation_preflight_context}"
        )
    result = analysis_agent_run(
        working_dir=reachability_metadata_path,
        context=prompt,
        task_type="metadata-fix",
        library=coordinates,
        timeout=METADATA_FIX_TIMEOUT_SECONDS,
        instructions=developer_instructions,
        environment=agent_env,
    )
    if result.return_code != 0:
        print(
            f"ERROR: Metadata fix failed for {coordinates}. "
            f"See {display_log_path(result.log_path)}.",
            file=sys.stderr,
        )
    return (result.return_code, result.log_path, result.timed_out)


def _agent_environment(
        reachability_metadata_path: str,
        graalvm_home: str | None,
        base_env: dict[str, str] | None,
) -> dict[str, str]:
    env = dict(base_env or os.environ)
    if graalvm_home:
        env["GRAALVM_HOME"] = graalvm_home
        env["JAVA_HOME"] = graalvm_home
    return gradle_command_environment(reachability_metadata_path, env)


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


def _graalvm_instructions(graalvm_home: str, graalvm_version: str) -> str:
    return (
        "Hard requirement for this metadata-fix run: every Gradle, Java, and native-image command "
        "must use the exact same GraalVM distribution as the failed run.\n"
        f"Set and preserve GRAALVM_HOME={graalvm_home}\n"
        f"Set and preserve JAVA_HOME={graalvm_home}\n"
        "Do not use another GraalVM installation from PATH, SDKMAN, latest_graalvm_home, or any symlink "
        "unless it resolves to this same distribution.\n"
        "Before verifying, check `native-image --version` and ensure it matches this required version:\n"
        f"{graalvm_version}\n"
        "If this GraalVM distribution is unavailable, fail instead of reproducing or verifying with another version.\n\n"
        "Reachability metadata condition rule: if GraalVM reports that metadata for an access was found "
        "but is inactive because runtime conditions were not satisfied, treat the existing condition as "
        "too late for that access. Read the access stack and move or duplicate the matching metadata entry "
        "under the narrowest library type that is reached before the reflective, resource, proxy, "
        "serialization, or JNI access occurs. Do not reuse an unsatisfied condition merely because it is "
        "related to the same library feature."
    )
