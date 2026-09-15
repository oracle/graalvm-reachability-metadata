# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Setup helpers for the native-image-run fix driver.

Deterministic seeding, branch and checkpoint plumbing, the dynamic-access
coverage gate, and strategy/agent construction for `fix_ni_run`
(§AR-forge-driver-queues.4), kept out of the driver's orchestration flow.
"""

import os
import subprocess

from ai_workflows.agents import Agent
from ai_workflows.core.workflow_strategy import WorkflowStrategy
from git_scripts.common_git import (
    delete_remote_branch_if_exists,
    switch_branch_quietly,
)
from utility_scripts.dynamic_access_report import load_dynamic_access_coverage_report
from utility_scripts.edit_scope import format_resolved_edit_scope_context
from utility_scripts.gradle_environment import gradle_command_environment
from utility_scripts.issue_requested_metadata import format_issue_requested_metadata_context
from utility_scripts.logged_command import LoggedCommandResult, run_logged_command
from utility_scripts.metadata_index import resolve_metadata_version, resolve_test_version
from utility_scripts.repo_path_resolver import require_complete_reachability_repo
from utility_scripts.source_context import (
    normalize_source_context_types,
    prepare_source_contexts,
    resolve_test_source_layout,
)
from utility_scripts.stage_logger import log_detail
from utility_scripts.strategy_loader import require_strategy_by_name
from utility_scripts.workflow_setup import list_all_files

DEFAULT_MODEL_NAME = "gpt-5.6-sol"


def run_fix_test_native_image_run(
        reachability_metadata_path: str,
        current_coordinates: str,
        new_version: str,
) -> LoggedCommandResult:
    """Run the fixTestNativeImageRun task with durable quiet output.

    §FS-forge-run-output-legibility §FS-durable-generation-logs
    """
    require_complete_reachability_repo(reachability_metadata_path)
    group, artifact, _ = current_coordinates.split(":", 2)
    library = f"{group}:{artifact}:{new_version}"
    return run_logged_command(
        [
            "./gradlew", "fixTestNativeImageRun",
            f"-PtestLibraryCoordinates={current_coordinates}",
            f"-PnewLibraryVersion={new_version}",
        ],
        cwd=reachability_metadata_path,
        task_type="native-image-run-fix",
        subject=library,
        action="fixTestNativeImageRun",
        env=gradle_command_environment(reachability_metadata_path),
        stage="native-image-run-fix",
    )


def create_or_switch_branch(reachability_metadata_path: str, branch: str) -> None:
    """Reset the workflow branch to the current detached HEAD."""
    delete_remote_branch_if_exists(branch, cwd=reachability_metadata_path)
    switch_branch_quietly(branch, cwd=reachability_metadata_path)


def commit_checkpoint(reachability_metadata_path: str, library: str) -> str:
    """Commit the seeded state as a checkpoint and return its commit hash.

    The checkpoint captures the valid seed after artifact URL population and
    before any exploratory test-suite split. If best-effort exploration resets to
    this checkpoint, finalization can still publish the seeded fix
    (§AR-forge-driver-queues.4).
    """
    subprocess.run(["git", "add", "-A"], cwd=reachability_metadata_path, check=False)
    subprocess.run(
        ["git", "commit", "--allow-empty", "-m", f"Checkpoint for native-image-run fix of {library}"],
        cwd=reachability_metadata_path,
        capture_output=True,
        text=True,
        check=False,
    )
    return subprocess.check_output(
        ["git", "rev-parse", "HEAD"],
        cwd=reachability_metadata_path,
        text=True,
    ).strip()


def run_dynamic_access_coverage_report(
        reachability_metadata_path: str,
        library: str,
) -> LoggedCommandResult:
    """Generate the dynamic-access report with durable quiet output.

    §FS-forge-run-output-legibility §FS-durable-generation-logs
    """
    require_complete_reachability_repo(reachability_metadata_path)
    return run_logged_command(
        [
            "./gradlew", "generateDynamicAccessCoverageReport",
            f"-Pcoordinates={library}",
        ],
        cwd=reachability_metadata_path,
        task_type="dynamic-access-report",
        subject=library,
        action="generateDynamicAccessCoverageReport",
        env=gradle_command_environment(reachability_metadata_path),
        stage="dynamic-access",
    )


def should_explore_new_version(reachability_metadata_path: str, group: str, artifact: str, version: str) -> bool:
    """Return True when the new version has uncovered dynamic-access call sites.

    Exploration is conditional: an empty or fully-covered dynamic-access report
    skips the version-specific suite preparation and exploration entirely, and
    the workflow finalizes the metadata-first seed directly
    (§AR-forge-driver-queues.4).
    """
    library = f"{group}:{artifact}:{version}"
    result = run_dynamic_access_coverage_report(reachability_metadata_path, library)
    if result.returncode != 0:
        log_detail("coverage-gate", f"Dynamic-access report unavailable for {library}; skipping exploration")
        return False

    test_version = resolve_test_version(reachability_metadata_path, group, artifact, version)
    report_path = os.path.join(
        reachability_metadata_path,
        "tests", "src", group, artifact, test_version,
        "build", "reports", "dynamic-access", "dynamic-access-coverage.json",
    )
    try:
        report = load_dynamic_access_coverage_report(report_path)
    except FileNotFoundError:
        log_detail("coverage-gate", f"Dynamic-access report file missing for {library}; skipping exploration")
        return False

    uncovered_calls = max(report.total_calls - report.covered_calls, 0)
    if not report.has_dynamic_access or report.total_calls == 0 or uncovered_calls == 0:
        log_detail(
            "coverage-gate",
            "No uncovered dynamic-access calls for {library} "
            "(hasDynamicAccess={has}, {covered}/{total} covered); skipping exploration".format(
                library=library,
                has=str(report.has_dynamic_access).lower(),
                covered=report.covered_calls,
                total=report.total_calls,
            ),
        )
        return False

    log_detail(
        "coverage-gate",
        "{uncovered} uncovered dynamic-access call(s) for {library}; preparing exploration".format(
            uncovered=uncovered_calls,
            library=library,
        ),
    )
    return True


def build_strategy_and_agent(
        strategy_name: str,
        reachability_metadata_path: str,
        library: str,
        group: str,
        artifact: str,
        version: str,
        library_preparation_preflight_context,
        explore: bool,
        issue_requested_metadata_context: str = "",
        continuation_marker_path: str | None = None,
):
    """Construct the dynamic-access strategy object and its agent for the new coordinate.

    Source contexts are downloaded only when exploring: the skip-exploration path
    finalizes the seed through `finalize_run`, which sends no
    agent prompts and needs neither downloaded sources nor a live agent session.
    """
    strategy = require_strategy_by_name(strategy_name)
    workflow_name = strategy.get("workflow")
    if not workflow_name:
        raise ValueError("Strategy is missing required field: workflow")
    strategy_agent = strategy.get("agent")
    if not strategy_agent:
        raise ValueError("Strategy is missing required field: agent")

    test_version = resolve_test_version(reachability_metadata_path, group, artifact, version)
    metadata_version = resolve_metadata_version(reachability_metadata_path, group, artifact, version)
    tests_dir = os.path.join(reachability_metadata_path, "tests", "src", group, artifact, test_version)
    build_gradle_file = os.path.join(tests_dir, "build.gradle")
    test_source_layout = resolve_test_source_layout(reachability_metadata_path, library, tests_dir)

    source_context_overview = ""
    source_context_available = False
    source_context_files: list[str] = []
    if explore:
        forge_repo_root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
        source_context_types = normalize_source_context_types(
            strategy.get("parameters", {}).get("source-context-types"),
        )
        prepared_source_context = prepare_source_contexts(
            repo_root=forge_repo_root,
            reachability_repo_path=reachability_metadata_path,
            coordinate=library,
            source_context_types=source_context_types,
        )
        source_context_overview = prepared_source_context.to_prompt_overview()
        source_context_available = prepared_source_context.is_available
        source_context_files = list(prepared_source_context.read_only_files)

    strategy_class = WorkflowStrategy.get_class(workflow_name)
    strategy_obj = strategy_class(
        strategy_obj=strategy,
        reachability_repo_path=reachability_metadata_path,
        library=library,
        test_version=test_version,
        build_gradle_file=build_gradle_file,
        source_context_overview=source_context_overview,
        source_context_available=source_context_available,
        source_context_files=source_context_files,
        test_language=test_source_layout.language,
        test_language_display_name=test_source_layout.display_language,
        test_source_dir_name=test_source_layout.source_dir_name,
        metadata_version=metadata_version,
        library_preparation_preflight_context=library_preparation_preflight_context,
        issue_requested_metadata_context=format_issue_requested_metadata_context(
            issue_requested_metadata_context,
        ),
        resolved_edit_scope_context=format_resolved_edit_scope_context(
            reachability_metadata_path,
            tests_dir,
            test_source_layout.source_root,
            build_gradle_file,
        ),
        continuation_marker_path=continuation_marker_path,
    )

    model_name = strategy.get("model") or DEFAULT_MODEL_NAME
    editable_files = list_all_files(test_source_layout.source_root)
    editable_files.append(build_gradle_file)
    agent_class = Agent.get_class(strategy_agent)
    agent = agent_class(
        model_name=model_name,
        editable_files=editable_files,
        read_only_files=source_context_files,
        working_dir=reachability_metadata_path,
        provider=strategy.get("provider"),
        library=library,
        task_type="fix-native-image-run",
        verbose=False,
        mcps=strategy.get("mcps", []),
        persistent_instructions=strategy_obj.persistent_instructions,
        thinking_level=strategy.get("thinking-level"),
        agent_name=strategy.get("agent-command"),
    )
    return strategy_obj, agent, model_name, test_source_layout.source_root
