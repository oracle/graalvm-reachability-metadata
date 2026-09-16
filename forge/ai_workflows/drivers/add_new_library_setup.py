# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Deterministic setup steps for the new-library support driver.

Owns the feature branch, native-image eligibility gate, scaffold run, and
agent initialization the driver performs before the workflow engine starts
(§AR-forge-drivers). The driver itself lives in `add_new_library_support.py`.
"""

import os
import sys

from ai_workflows.agents import Agent
from git_scripts.common_git import (
    build_ai_branch_name,
    delete_remote_branch_if_exists,
    switch_branch_quietly,
)
from utility_scripts.gradle_environment import gradle_command_environment
from utility_scripts.logged_command import LoggedCommandResult, run_logged_command
from utility_scripts.metadata_index import is_not_for_native_image, write_not_for_native_image_marker
from utility_scripts.native_image_artifact import evaluate_native_image_eligibility
from utility_scripts.repo_path_resolver import require_complete_reachability_repo
from utility_scripts.source_context import discover_artifact_metadata
from utility_scripts.stage_logger import log_detail, log_stage
from utility_scripts.task_logs import display_log_path

DEFAULT_MODEL_NAME = "gpt-5.4"


class ScaffoldError(RuntimeError):
    """Raised when the Gradle scaffold task fails unexpectedly."""


def create_feature_branch_for_library(group, artifact, library_version):
    """
    Reset the feature branch for the given coordinates to the current detached HEAD.
    Branch name is in the following format:
    ai/<login>/add-lib-support-<group>-<artifact>-<version>
    """

    new_branch = build_ai_branch_name(f"add-lib-support-{group}-{artifact}-{library_version}")
    delete_remote_branch_if_exists(new_branch)
    switch_branch_quietly(new_branch)


def prepare_native_image_eligible_artifact(reachability_repo_path: str, library: str) -> bool:
    """Return true when the coordinate should proceed to Native Image scaffold."""
    package, artifact, _library_version = library.split(":")
    if is_not_for_native_image(reachability_repo_path, package, artifact):
        log_stage(
            "native-image-eligibility",
            f"{package}:{artifact} is already marked not-for-native-image",
        )
        return False

    discover_artifact_metadata(reachability_repo_path, library)
    eligibility = evaluate_native_image_eligibility(reachability_repo_path, library)
    if not eligibility.not_for_native_image:
        return True

    marker_path = write_not_for_native_image_marker(
        reachability_repo_path,
        package,
        artifact,
        eligibility.reason or "Artifact is not applicable to GraalVM Native Image metadata.",
        eligibility.replacement,
    )
    log_stage(
        "native-image-eligibility",
        (
            f"Marked {package}:{artifact} as not-for-native-image in "
            f"{os.path.relpath(marker_path, reachability_repo_path)}"
        ),
    )
    return False


def _metadata_already_exists(scaffold_proc: LoggedCommandResult) -> bool:
    """Return True when Gradle reports that metadata for the library already exists."""
    output = scaffold_proc.stdout
    return "already exists" in output and "Use --force to overwrite existing metadata" in output


def run_scaffold(library: str) -> bool:
    """Run scaffold quietly while preserving its complete output.

    §FS-forge-run-output-legibility §FS-durable-generation-logs
    """
    repo_path = os.getcwd()
    require_complete_reachability_repo(repo_path)
    scaffold_proc = run_logged_command(
        ["./gradlew", "scaffold", f"--coordinates={library}", "--rerun-tasks"],
        cwd=repo_path,
        task_type="scaffold",
        subject=library,
        action="scaffold",
        env=gradle_command_environment(repo_path),
        stage="scaffold",
        failure_is_detail=True,
    )
    if scaffold_proc.returncode == 0:
        return True
    if _metadata_already_exists(scaffold_proc):
        return False
    log_stage(
        "scaffold",
        f"scaffold failed with exit code {scaffold_proc.returncode} "
        f"(log: {display_log_path(scaffold_proc.log_path)})",
    )
    raise ScaffoldError(scaffold_proc.stdout or "Gradle scaffold task failed")


def init_agent(
        strategy,
        working_dir,
        editable_files,
        read_only_files,
        library=None,
        task_type="session",
        verbose=False,
        model_name=DEFAULT_MODEL_NAME,
        persistent_instructions: str | None = None,
        thinking_level: str | None = None,
):
    """Initialize the agent selected by the predefined strategy bundle.

    Workflow drivers bind the backend, model, MCPs, prompt context, and persistent
    instructions named by the bundle (§FS-forge-predefined-strategy-contract);
    strategy code owns the iteration loop on the far side of that boundary
    (§AR-forge-strategy-agent-boundary).
    """
    strategy_agent = strategy.get("agent")
    if not strategy_agent:
        print("ERROR: Strategy is missing required field: agent", file=sys.stderr)
        sys.exit(1)

    log_detail("init-agent", f"Initializing {strategy_agent} agent")
    agent_class = Agent.get_class(strategy_agent)
    return agent_class(
        model_name=model_name,
        editable_files=editable_files,
        read_only_files=read_only_files,
        working_dir=working_dir,
        provider=strategy.get("provider"),
        library=library,
        task_type=task_type,
        verbose=verbose,
        mcps=strategy.get("mcps", []),
        persistent_instructions=persistent_instructions,
        thinking_level=thinking_level or strategy.get("thinking-level"),
        agent_name=strategy.get("agent-command"),
    )
