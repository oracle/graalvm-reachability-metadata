# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import argparse
import os
import subprocess
import sys

if __package__ in (None, ""):
    sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))

import ai_workflows.agents  # noqa: F401 - triggers agent registration
import ai_workflows.core  # noqa: F401 - triggers strategy registration
from ai_workflows.agents import Agent
from ai_workflows.core.fix_metadata_codex import run_codex_metadata_fix
from ai_workflows.core.workflow_strategy import (
    RUN_STATUS_SUCCESS,
    SUCCESS_WITH_INTERVENTION_STATUS,
    WorkflowStrategy,
)
from ai_workflows.drivers.improve_library_coverage import prepare_library_update_target
from git_scripts.common_git import build_ai_branch_name, delete_remote_branch_if_exists
from utility_scripts import metrics_writer
from utility_scripts.dynamic_access_report import load_dynamic_access_coverage_report
from utility_scripts.gradle_environment import gradle_command_environment
from utility_scripts.library_preparation_preflight import (
    prepare_library_preparation_preflight,
)
from utility_scripts.metadata_index import resolve_metadata_version, resolve_test_version
from utility_scripts.metrics_writer import create_failure_run_metrics_output
from utility_scripts.repo_path_resolver import require_complete_reachability_repo, resolve_repo_roots
from utility_scripts.schema_validator import validate_run_metrics
from utility_scripts.source_context import (
    normalize_source_context_types,
    populate_artifact_urls,
    prepare_source_contexts,
    resolve_test_source_layout,
)
from utility_scripts.stage_logger import log_stage
from utility_scripts.strategy_loader import require_strategy_by_name
from utility_scripts.workflow_setup import resolve_graalvm_java_home, validate_repo_paths

DEFAULT_MODEL_NAME = "oca/gpt-5.5"
DEFAULT_STRATEGY_NAME = "library_update_pi_gpt-5.5"
METRICS_TASK_TYPE = "fix_ni_run"


def build_parser():
    parser = argparse.ArgumentParser(
        prog="fix_ni_run.py",
        description="Run fixTestNativeImageRun Gradle task for a library version upgrade.",
        epilog=(
            "Example:\n"
            "  python3 ai_workflows/drivers/fix_ni_run.py \\\n"
            "      --coordinates com.example:lib:1.2.3 \\\n"
            "      --new-version 1.2.4 \\\n"
            "      --reachability-metadata-path /path/to/graalvm-reachability-metadata\n"
        ),
        formatter_class=argparse.RawTextHelpFormatter,
    )
    parser.add_argument(
        "--coordinates",
        required=True,
        help="Maven coordinates Group:Artifact:Version for the current library version",
    )
    parser.add_argument(
        "--new-version",
        required=True,
        help="Target library version which needs a fix",
    )
    parser.add_argument(
        "--reachability-metadata-path",
        help=(
            "Path to the graalvm-reachability-metadata repository. "
            "If omitted, the parent checkout of this Forge directory is used."
        ),
    )
    parser.add_argument(
        "--metrics-repo-path",
        help="Path where dispatcher-created workflow context artifacts are written.",
    )
    parser.add_argument(
        "--strategy-name",
        dest="strategy_name",
        default=DEFAULT_STRATEGY_NAME,
        metavar="NAME",
        help=(
            "Dynamic-access strategy used for the conditional exploration phase "
            f"(default: {DEFAULT_STRATEGY_NAME})"
        ),
    )
    parser.add_argument(
        "--library-preparation-preflight-path",
        help="Path to the dispatcher-created library preparation preflight JSON record.",
    )
    return parser


def list_all_files(directory_path: str) -> list[str]:
    """Recursively list all file paths in the given directory."""
    files: list[str] = []
    if not directory_path or not os.path.isdir(directory_path):
        return files
    for root_dir, _, file_names in os.walk(directory_path):
        for file_name in file_names:
            files.append(os.path.join(root_dir, file_name))
    return files


def resolve_repo_paths(explicit_repo_path, explicit_metrics_repo_path):
    """Resolve repository paths for code and metrics outputs."""
    resolved_reachability_repo, resolved_metrics_repo = resolve_repo_roots(
        explicit_repo_path,
        explicit_metrics_repo_path,
    )
    resolved_metrics_dir = os.path.join(resolved_metrics_repo, "script_run_metrics")
    os.makedirs(resolved_metrics_dir, exist_ok=True)
    return resolved_reachability_repo, resolved_metrics_dir, resolved_metrics_repo


def run_fix_test_native_image_run(
        reachability_metadata_path: str,
        current_coordinates: str,
        new_version: str,
) -> subprocess.CompletedProcess[str]:
    """Run the fixTestNativeImageRun Gradle task."""
    require_complete_reachability_repo(reachability_metadata_path)
    return subprocess.run(
        [
            "./gradlew", "fixTestNativeImageRun",
            f"-PtestLibraryCoordinates={current_coordinates}",
            f"-PnewLibraryVersion={new_version}",
        ],
        cwd=reachability_metadata_path,
        env=gradle_command_environment(reachability_metadata_path),
    )


def run_gradle_test(
        reachability_metadata_path: str,
        coordinates: str,
) -> subprocess.CompletedProcess[str]:
    """Run Gradle tests for the provided coordinates."""
    require_complete_reachability_repo(reachability_metadata_path)
    return subprocess.run(
        [
            "./gradlew", "test",
            f"-Pcoordinates={coordinates}",
        ],
        cwd=reachability_metadata_path,
        env=gradle_command_environment(reachability_metadata_path),
    )


def create_or_switch_branch(reachability_metadata_path: str, branch: str) -> None:
    """Reset the workflow branch to the current detached HEAD."""
    delete_remote_branch_if_exists(branch, cwd=reachability_metadata_path)
    subprocess.run(
        ["git", "switch", "-C", branch],
        cwd=reachability_metadata_path,
        check=True,
    )


def commit_checkpoint(reachability_metadata_path: str, library: str) -> str:
    """Commit the pre-generation state as a checkpoint and return its commit hash.

    The checkpoint captures any preflight setup so finalization and metrics can
    treat seed and exploration output as the generated work
    (§WF-forge-workflow-drivers).
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
) -> subprocess.CompletedProcess[str]:
    """Generate the dynamic-access coverage report for the new coordinate."""
    require_complete_reachability_repo(reachability_metadata_path)
    return subprocess.run(
        [
            "./gradlew", "generateDynamicAccessCoverageReport",
            f"-Pcoordinates={library}",
        ],
        cwd=reachability_metadata_path,
        env=gradle_command_environment(reachability_metadata_path),
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=False,
    )


def should_explore_new_version(reachability_metadata_path: str, group: str, artifact: str, version: str) -> bool:
    """Return True when the new version has uncovered dynamic-access call sites.

    Exploration is conditional: an empty or fully-covered dynamic-access report
    skips the version-specific suite preparation and exploration entirely, and
    the workflow finalizes the metadata-first seed directly
    (§WF-native-image-run-fix-workflow.3).
    """
    library = f"{group}:{artifact}:{version}"
    result = run_dynamic_access_coverage_report(reachability_metadata_path, library)
    if result.returncode != 0:
        log_stage("coverage-gate", f"Dynamic-access report unavailable for {library}; skipping exploration")
        print(result.stdout)
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
        log_stage("coverage-gate", f"Dynamic-access report file missing for {library}; skipping exploration")
        return False

    uncovered_calls = max(report.total_calls - report.covered_calls, 0)
    if not report.has_dynamic_access or report.total_calls == 0 or uncovered_calls == 0:
        log_stage(
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

    log_stage(
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
):
    """Construct the dynamic-access strategy object and its agent for the new coordinate."""
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

    forge_repo_root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    source_context_types = normalize_source_context_types(strategy.get("parameters", {}).get("source-context-types"))
    prepared_source_context = prepare_source_contexts(
        repo_root=forge_repo_root,
        reachability_repo_path=reachability_metadata_path,
        coordinate=library,
        source_context_types=source_context_types,
    )

    strategy_class = WorkflowStrategy.get_class(workflow_name)
    strategy_obj = strategy_class(
        strategy_obj=strategy,
        reachability_repo_path=reachability_metadata_path,
        library=library,
        test_version=test_version,
        build_gradle_file=build_gradle_file,
        source_context_overview=prepared_source_context.to_prompt_overview(),
        source_context_available=prepared_source_context.is_available,
        source_context_files=prepared_source_context.read_only_files,
        test_language=test_source_layout.language,
        test_language_display_name=test_source_layout.display_language,
        test_source_dir_name=test_source_layout.source_dir_name,
        metadata_version=metadata_version,
        library_preparation_preflight_context=library_preparation_preflight_context,
    )

    model_name = strategy.get("model") or DEFAULT_MODEL_NAME
    editable_files = list_all_files(test_source_layout.source_root)
    editable_files.append(build_gradle_file)
    agent_class = Agent.get_class(strategy_agent)
    agent = agent_class(
        model_name=model_name,
        editable_files=editable_files,
        read_only_files=list(prepared_source_context.read_only_files),
        working_dir=reachability_metadata_path,
        provider=strategy.get("provider"),
        library=library,
        task_type="fix-native-image-run",
        verbose=False,
        mcps=strategy.get("mcps", []),
        persistent_instructions=strategy_obj.persistent_instructions,
    )
    return strategy_obj, agent, model_name, test_source_layout.source_root


def write_metrics(run_metrics: dict, metrics_repo_dir: str, metrics_repo_root: str | None) -> None:
    """Append run metrics to execution-metrics.json (or local fallback) and write pending metrics."""
    in_repo_root = metrics_writer.in_metadata_repo_metrics_root(metrics_repo_root)
    if in_repo_root:
        metrics_json = metrics_writer.execution_metrics_path(in_repo_root, run_metrics)
        written = metrics_writer.append_execution_metrics(in_repo_root, run_metrics, METRICS_TASK_TYPE)
        if written != metrics_json:
            raise ValueError(f"ERROR: Resolved metrics path {metrics_json} does not match written path {written}")
    else:
        metrics_json = os.path.join(metrics_repo_dir, f"{METRICS_TASK_TYPE}.json")
        metrics_writer.append_run_metrics(run_metrics, metrics_json)
    if metrics_repo_root:
        metrics_writer.write_pending_metrics(metrics_repo_root, run_metrics)
    validate_run_metrics(metrics_json)


def main(argv=None) -> int:
    """Run the Native Image fix driver.

    The single-run driver (§WF-forge-workflow-drivers) for
    §WF-native-image-run-fix-workflow. `fixTestNativeImageRun` produces the seed;
    dynamic-access exploration runs only when the new version has uncovered
    calls; the shared `_finalize_successful_iteration` path (three native-test
    lanes + dual-coordinate finalization) always gates PR eligibility.
    """
    args = build_parser().parse_args(sys.argv[1:] if argv is None else argv)

    reachability_metadata_path, metrics_repo_dir, metrics_repo_root = resolve_repo_paths(
        args.reachability_metadata_path,
        args.metrics_repo_path,
    )

    current_coordinates = args.coordinates
    new_version = args.new_version
    # Apply deterministic preflight setup into the resolved worktree before
    # generation; only advisory guidance reaches the prompt context.
    library_preparation_preflight, library_preparation_preflight_context = (
        prepare_library_preparation_preflight(
            args.library_preparation_preflight_path,
            reachability_metadata_path,
        )
    )
    if library_preparation_preflight is not None:
        print("[pipeline] Library preparation preflight:")
        print(library_preparation_preflight_context)

    resolve_graalvm_java_home()
    validate_repo_paths(reachability_metadata_path, metrics_repo_dir)
    os.chdir(reachability_metadata_path)

    group, artifact, old_version = current_coordinates.split(":")
    library = f"{group}:{artifact}:{new_version}"
    branch = build_ai_branch_name(
        f"fix-native-image-run-{group}-{artifact}-{new_version}",
        cwd=reachability_metadata_path,
    )
    print(f"[pipeline] Creating branch: {branch}")
    create_or_switch_branch(reachability_metadata_path, branch)
    checkpoint = commit_checkpoint(reachability_metadata_path, library)

    print(f"[pipeline] Running fixTestNativeImageRun for: {current_coordinates} -> {new_version}")
    result = run_fix_test_native_image_run(
        reachability_metadata_path=reachability_metadata_path,
        current_coordinates=current_coordinates,
        new_version=new_version,
    )

    if result.returncode != 0:
        generated_metadata_file = os.path.join(
            reachability_metadata_path,
            "metadata",
            group,
            artifact,
            new_version,
            "reachability-metadata.json",
        )
        if not os.path.isfile(generated_metadata_file):
            print(
                "ERROR: fixTestNativeImageRun failed before generating "
                f"{generated_metadata_file}. Skipping Codex metadata repair.",
                file=sys.stderr,
            )
            return result.returncode
        print(f"[pipeline] Detected missing metadata entries for {library}. Running Codex fix.")
        gradle_env = gradle_command_environment(reachability_metadata_path)
        codex_rc, _codex_log, _codex_timed_out = run_codex_metadata_fix(
            reachability_metadata_path,
            library,
            graalvm_home=gradle_env.get("GRAALVM_HOME"),
            library_preparation_preflight_context=library_preparation_preflight_context,
        )
        if codex_rc != 0:
            print(f"ERROR: Codex failed with return code: {codex_rc}", file=sys.stderr)
            return codex_rc
        print(f"[pipeline] Codex metadata fix completed. Running Gradle test for {library}.")
        result = run_gradle_test(
            reachability_metadata_path=reachability_metadata_path,
            coordinates=library,
        )
        if result.returncode != 0:
            print("[pipeline] Gradle test failed after Codex metadata fix. Skipping PR creation.", file=sys.stderr)
            return result.returncode

    # Coverage gate: explore only when the new version has uncovered calls.
    explore = should_explore_new_version(reachability_metadata_path, group, artifact, new_version)
    if explore:
        print(f"[pipeline] Preparing version-specific test-suite for {library}")
        prepare_library_update_target(reachability_metadata_path, group, artifact, new_version)

    strategy_obj, agent, model_name, tests_root = build_strategy_and_agent(
        strategy_name=args.strategy_name,
        reachability_metadata_path=reachability_metadata_path,
        library=library,
        group=group,
        artifact=artifact,
        version=new_version,
        library_preparation_preflight_context=library_preparation_preflight_context,
    )

    iterations = 0
    if explore:
        print(f"[pipeline] Running dynamic-access exploration for {library}")
        run_result = strategy_obj.run(agent=agent, checkpoint_commit_hash=checkpoint)
        explore_status, iterations = run_result[0], run_result[1]
        # Best-effort: a partial or failed explore must not abort. The seed is
        # already valid and the finalization gate decides PR eligibility.
        log_stage("explore", f"Dynamic-access exploration completed with status: {explore_status}")

    populate_artifact_urls(reachability_metadata_path, library)
    finalize_status, _ = strategy_obj._finalize_successful_iteration(base_commit=checkpoint)
    succeeded = finalize_status in {RUN_STATUS_SUCCESS, SUCCESS_WITH_INTERVENTION_STATUS}

    if succeeded:
        if finalize_status == SUCCESS_WITH_INTERVENTION_STATUS:
            print("[pipeline] Finalization produced PR-eligible post-generation intervention output.")
        ending_commit = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
        run_metrics = metrics_writer.create_java_run_fix_run_metrics_output_json(
            repo_path=reachability_metadata_path,
            package=group,
            artifact=artifact,
            previous_library_version=old_version,
            new_library_version=new_version,
            agent=agent,
            model_name=model_name,
            global_iterations=iterations,
            tests_root=tests_root,
            strategy_name=args.strategy_name,
            status=finalize_status,
            starting_commit=checkpoint,
            ending_commit=ending_commit,
            post_generation_intervention=strategy_obj.post_generation_intervention,
            library_preparation_preflight=library_preparation_preflight,
        )
    else:
        # Keep the generated working-tree state as the debugging surface for the
        # next maintainer or Forge run (§WF-native-image-run-fix-workflow.5).
        print("[pipeline] Finalization failed. Skipping PR creation.", file=sys.stderr)
        ending_commit = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
        run_metrics = create_failure_run_metrics_output(
            package=group,
            artifact=artifact,
            library_version=new_version,
            agent=agent,
            model_name=model_name,
            global_iterations=iterations,
            strategy_name=args.strategy_name,
            starting_commit=checkpoint,
            ending_commit=ending_commit,
            library_preparation_preflight=library_preparation_preflight,
        )

    write_metrics(run_metrics, metrics_repo_dir, metrics_repo_root)

    if not succeeded:
        return 1
    print(f"[pipeline] Workflow succeeded for {current_coordinates}")
    return 0


if __name__ == "__main__":
    if any(a in ("-h", "--help") for a in sys.argv[1:]):
        build_parser().print_help()
        sys.exit(0)
    sys.exit(main())
