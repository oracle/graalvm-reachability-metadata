# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import json
import os
import re
import sys
from collections.abc import Callable

from ai_workflows.agents.agent_runtime import analysis_agent_run, get_analysis_agent
from utility_scripts.foreign_metadata_owner_issue import (
    ForeignMetadataOwnerFailure,
    ensure_foreign_metadata_owner_issue,
    load_foreign_metadata_owner_failure,
)
from utility_scripts.gradle_environment import gradle_command_environment
from utility_scripts.logged_command import LoggedCommandResult, run_logged_command
from utility_scripts.metadata_index import find_index_entry_for_version
from utility_scripts.style_checks import run_style_fix_and_checks
from utility_scripts.native_image_config_policy import (
    find_changed_legacy_test_native_image_config_files_for_coordinate,
    find_uncommitted_legacy_test_native_image_config_files_for_coordinate,
    format_legacy_test_native_image_config_error,
)
from utility_scripts.repo_path_resolver import require_complete_reachability_repo
from utility_scripts.run_location import (
    PHASE_FINALIZATION,
    STEP_AGENT_FIX,
    current_run_location,
    log_step_progress,
    record_step_failure,
    run_step,
)
from utility_scripts.stage_logger import log_detail, log_stage
from utility_scripts.task_logs import display_log_path
from utility_scripts.test_quality_checks import (
    collect_generated_test_validity_issues,
    format_generated_test_validity_issue,
)

CHECK_METADATA_FIX_TIMEOUT_SECONDS = 1200
MAX_CHECK_METADATA_FIX_ATTEMPTS = 3


def _run_finalization_agent_fix(
        library: str,
        target: str,
        attempt: int,
        maximum_attempts: int,
        operation: Callable[[], bool],
) -> bool:
    """Run one repair with concise finalization progress when phase-bound.

    §FS-forge-run-output-legibility.1 §FS-forge-run-output-legibility.5
    """
    location = current_run_location()
    if location is None or location.phase != PHASE_FINALIZATION:
        return operation()

    with run_step(PHASE_FINALIZATION, STEP_AGENT_FIX, operand=f"{library} {target}"):
        log_step_progress(
            PHASE_FINALIZATION,
            STEP_AGENT_FIX,
            f"Running agent fix for {target} on {library} "
            f"(attempt {attempt}/{maximum_attempts})",
        )
        fixed = operation()
        outcome = "completed" if fixed else "failed"
        log_step_progress(
            PHASE_FINALIZATION,
            STEP_AGENT_FIX,
            f"Agent fix {outcome} for {target} on {library} "
            f"(attempt {attempt}/{maximum_attempts})",
        )
        if not fixed and attempt == maximum_attempts:
            record_step_failure()
        return fixed


def _run_gradle_command_with_output(repo_path: str, command: list[str]) -> LoggedCommandResult:
    """Run a finalization Gradle command quietly with durable output."""
    require_complete_reachability_repo(repo_path)
    action = command[1] if len(command) > 1 else "gradle"
    coordinate_argument = next(
        (argument for argument in command if argument.startswith("-Pcoordinates=")),
        "-Pcoordinates=unknown",
    )
    return run_logged_command(
        command,
        cwd=repo_path,
        task_type="finalization",
        subject=coordinate_argument.removeprefix("-Pcoordinates="),
        action=action,
        env=gradle_command_environment(repo_path),
        stage="finalization",
    )


def _run_gradle_command(repo_path: str, command: list[str]) -> bool:
    """Run a Gradle command in the reachability repo, returning True on success."""
    result = _run_gradle_command_with_output(repo_path, command)
    if result.returncode != 0:
        return False
    return True


def _run_route_foreign_metadata(repo_path: str, library: str) -> LoggedCommandResult:
    """Run ownership routing while retaining its output for agent evidence."""
    return _run_gradle_command_with_output(
        repo_path,
        ["./gradlew", "routeForeignMetadata", f"-Pcoordinates={library}"],
    )


def _unsupported_owner_agent_evidence(
        failure: ForeignMetadataOwnerFailure,
        issue_url: str,
        routing_output: str,
) -> str:
    """Format the deterministic owner-resolution evidence for metadata repair."""
    return "\n".join([
        "routeForeignMetadata failed after resolving the foreign condition owner.",
        f"Reason: {failure.reason}",
        f"Resolved dependency coordinate: {failure.coordinate}",
        f"Follow-up issue: {issue_url}",
        "Do not add the resolved dependency package to the source artifact's allowed-packages.",
        "",
        "Captured routeForeignMetadata output:",
        "```text",
        routing_output,
        "```",
    ])


def _routing_failure_agent_evidence(routing_output: str) -> str:
    """Format a routing failure that did not resolve one unsupported owner."""
    return "\n".join([
        "routeForeignMetadata failed without a supported resolved dependency owner.",
        "",
        "Captured routeForeignMetadata output:",
        "```text",
        routing_output,
        "```",
    ])


def _run_check_metadata_fix(repo_path: str, library: str, failure_output: str) -> bool:
    """Ask the analysis agent to repair an unresolved metadata validation failure."""
    analysis_backend = get_analysis_agent().backend
    prompt = "\n".join([
        f"The deterministic checkMetadataFiles step failed for {library}.",
        "Make the smallest metadata correction supported by the captured failure output.",
        "Do not remove tested versions, weaken validation, or change the command to suppress the failure.",
        "Forge will rerun the exact command after this repair and provide new evidence if another attempt is needed.",
        "",
        "Exact command:",
        f"./gradlew checkMetadataFiles -Pcoordinates={library}",
        "",
        "Captured failure output:",
        "```text",
        failure_output,
        "```",
    ])
    result = analysis_agent_run(
        working_dir=repo_path,
        context=prompt,
        task_type="check-metadata-fix",
        library=library,
        timeout=CHECK_METADATA_FIX_TIMEOUT_SECONDS,
    )
    if result.return_code != 0:
        print(
            f"ERROR: {analysis_backend} metadata fix failed for {library}. "
            f"See {display_log_path(result.log_path)}.",
            file=sys.stderr,
        )
        return False
    return True


def _extract_missing_allowed_packages(check_metadata_output: str) -> set[str]:
    """Extract package names from TypeReached entries for index.json allowed-packages."""
    packages: set[str] = set()
    pattern = re.compile(r"^TypeReached:\s+([A-Za-z0-9_$.]+)\s*$")
    for line in check_metadata_output.splitlines():
        match = pattern.match(line.strip())
        if match is None:
            continue
        class_name = match.group(1)
        if "." not in class_name:
            continue
        packages.add(class_name.rsplit(".", 1)[0])
    return packages


def _resolve_index_entry_for_current_version(
        repo_path: str,
        group: str,
        artifact: str,
        index_entries: list[dict],
        library_version: str,
) -> dict | None:
    """Return the metadata index entry that should receive allowed-package updates."""
    resolved_entry = find_index_entry_for_version(repo_path, group, artifact, library_version)
    if resolved_entry is not None:
        return resolved_entry

    matching_version_entries = [
        entry for entry in index_entries if str(entry.get("metadata-version") or "") == library_version
    ]
    if matching_version_entries:
        latest_matching_entries = [entry for entry in matching_version_entries if entry.get("latest")]
        if latest_matching_entries:
            return latest_matching_entries[0]
        return matching_version_entries[0]

    latest_entries = [entry for entry in index_entries if entry.get("latest")]
    if len(latest_entries) == 1:
        return latest_entries[0]
    if len(index_entries) == 1:
        return index_entries[0]
    return None


def _append_allowed_packages_to_metadata_index(
        repo_path: str,
        library: str,
        group: str,
        artifact: str,
        library_version: str,
        packages: set[str],
) -> bool:
    """Append missing allowed packages to the library metadata index.json entry."""
    index_path = os.path.join(
        repo_path,
        "metadata",
        group,
        artifact,
        "index.json",
    )
    index_path_display = os.path.relpath(index_path, repo_path)
    try:
        with open(index_path, "r", encoding="utf-8") as index_file:
            index_entries = json.load(index_file)
    except (OSError, json.JSONDecodeError) as exc:
        print(f"ERROR: Failed to load metadata index {index_path_display}: {exc}", file=sys.stderr)
        return False

    if not isinstance(index_entries, list):
        print(f"ERROR: Metadata index {index_path_display} does not contain a JSON array.", file=sys.stderr)
        return False

    index_entry = _resolve_index_entry_for_current_version(
        repo_path,
        group,
        artifact,
        index_entries,
        library_version,
    )
    if index_entry is None:
        print(
            f"ERROR: Could not resolve metadata index entry for {library} in {index_path_display}.",
            file=sys.stderr,
        )
        return False

    allowed_packages = index_entry.get("allowed-packages")
    if not isinstance(allowed_packages, list):
        allowed_packages = []
        index_entry["allowed-packages"] = allowed_packages

    added_packages = [package for package in sorted(packages) if package not in allowed_packages]
    if not added_packages:
        return True

    allowed_packages.extend(added_packages)
    with open(index_path, "w", encoding="utf-8") as index_file:
        json.dump(index_entries, index_file, indent=2)
        index_file.write("\n")

    log_detail("allowed-packages", f"Updated {index_path_display}: {', '.join(added_packages)}")
    return True


def _run_check_metadata_files_with_allowed_packages_fix(
        repo_path: str,
        library: str,
        group: str,
        artifact: str,
        library_version: str,
        log_stage_name: str,
) -> tuple[bool, str]:
    """Run checkMetadataFiles and update missing allowed-packages when the task reports them."""
    log_detail(log_stage_name, f"Running checkMetadataFiles for {library}")
    seen_packages: set[str] = set()
    for attempt in range(1, 4):
        log_detail(log_stage_name, f"Running checkMetadataFiles attempt {attempt}/3 for {library}")
        metadata_valid, metadata_output = _run_check_metadata_files(repo_path, library, log_stage_name)
        if metadata_valid:
            return (True, metadata_output)

            log_detail(log_stage_name, f"checkMetadataFiles failed for {library}; resolving missing allowed-packages")
        missing_packages = _extract_missing_allowed_packages(metadata_output)
        new_packages = missing_packages - seen_packages
        if not new_packages:
            log_detail(log_stage_name, "No new TypeReached packages found in checkMetadataFiles output")
            return (False, metadata_output)
        log_detail("allowed-packages", f"Adding allowed-packages for {library}: {', '.join(sorted(new_packages))}")
        if not _append_allowed_packages_to_metadata_index(
            repo_path=repo_path,
            library=library,
            group=group,
            artifact=artifact,
            library_version=library_version,
            packages=new_packages,
        ):
            return (False, metadata_output)
        seen_packages.update(new_packages)

    print(f"ERROR: checkMetadataFiles still fails after updating allowed-packages for {library}.", file=sys.stderr)
    return (False, metadata_output)


def _run_check_metadata_files(repo_path: str, library: str, log_stage_name: str) -> tuple[bool, str]:
    """Run one read-only metadata check and return its status and output."""
    result = _run_gradle_command_with_output(
        repo_path,
        ["./gradlew", "checkMetadataFiles", f"-Pcoordinates={library}"],
    )
    if result.returncode == 0:
        log_detail(log_stage_name, f"checkMetadataFiles passed for {library}")
        return (True, result.stdout)
    return (False, result.stdout)


def run_library_finalization(
        repo_path: str,
        library: str,
        group: str,
        artifact: str,
        library_version: str,
        log_prefix: str | None = None,
        base_commit: str | None = None,
) -> bool:
    """Run the shared end-of-workflow finalization steps for one library.

    §AR-forge-driver-finalization
    """
    del log_prefix
    log_detail("split-test-only-metadata", f"Running splitTestOnlyMetadata for {library}")
    if not _run_gradle_command(repo_path, ["./gradlew", "splitTestOnlyMetadata", f"-Pcoordinates={library}"]):
        return False
    legacy_test_config_paths = set(
        find_uncommitted_legacy_test_native_image_config_files_for_coordinate(repo_path, library)
    )
    if base_commit is not None:
        legacy_test_config_paths.update(
            find_changed_legacy_test_native_image_config_files_for_coordinate(repo_path, library, base_commit)
        )
    if legacy_test_config_paths:
        print(format_legacy_test_native_image_config_error(sorted(legacy_test_config_paths)), file=sys.stderr)
        return False
    metadata_valid, metadata_failure_output = _run_check_metadata_files(
        repo_path,
        library,
        "check-metadata-files",
    )
    routing_failure_evidence: str | None = None
    unsupported_owner_reported = False
    if not metadata_valid:
        log_detail("route-foreign-metadata", f"Running routeForeignMetadata after validation failed for {library}")
        route_result = _run_route_foreign_metadata(repo_path, library)
        if route_result.returncode == 0:
            metadata_valid, metadata_failure_output = _run_check_metadata_files_with_allowed_packages_fix(
                repo_path=repo_path,
                library=library,
                group=group,
                artifact=artifact,
                library_version=library_version,
                log_stage_name="check-metadata-files",
            )
        else:
            owner_failure = load_foreign_metadata_owner_failure(repo_path, library)
            if owner_failure is not None:
                issue_url = ensure_foreign_metadata_owner_issue(repo_path, library, owner_failure)
                routing_failure_evidence = _unsupported_owner_agent_evidence(
                    owner_failure,
                    issue_url,
                    route_result.stdout,
                )
                unsupported_owner_reported = True
            else:
                routing_failure_evidence = _routing_failure_agent_evidence(route_result.stdout)
    if not metadata_valid:
        for attempt in range(1, MAX_CHECK_METADATA_FIX_ATTEMPTS + 1):
            log_detail(
                "check-metadata-files",
                f"Running analysis metadata fix attempt {attempt}/{MAX_CHECK_METADATA_FIX_ATTEMPTS} for {library}",
            )
            agent_evidence = "\n\n".join(filter(None, [metadata_failure_output, routing_failure_evidence]))
            if not _run_finalization_agent_fix(
                    library,
                    "metadata validation",
                    attempt,
                    MAX_CHECK_METADATA_FIX_ATTEMPTS,
                    lambda: _run_check_metadata_fix(repo_path, library, agent_evidence),
            ):
                continue
            if not unsupported_owner_reported:
                metadata_valid, metadata_failure_output = _run_check_metadata_files_with_allowed_packages_fix(
                    repo_path=repo_path,
                    library=library,
                    group=group,
                    artifact=artifact,
                    library_version=library_version,
                    log_stage_name="check-metadata-files",
                )
            else:
                metadata_valid, metadata_failure_output = _run_check_metadata_files(
                    repo_path,
                    library,
                    "check-metadata-files",
                )
            if metadata_valid:
                break
        else:
            return False
    log_detail("style-checks", f"Running style checks for {library}")
    if not run_style_fix_and_checks(repo_path, library):
        return False
    test_source_root = os.path.join(repo_path, "tests", "src", group, artifact, library_version, "src", "test")
    generated_test_validity_issues = collect_generated_test_validity_issues(test_source_root)
    if generated_test_validity_issues:
        for issue in generated_test_validity_issues:
            log_stage(
                "generated-test-quality",
                "WARNING: Suspicious generated test target requires human review: "
                f"{format_generated_test_validity_issue(issue, repo_path)}",
            )
        return False
    log_detail("generate-library-stats", f"Running generateLibraryStats for {library}")
    if not _run_gradle_command(repo_path, ["./gradlew", "generateLibraryStats", f"-Pcoordinates={library}"]):
        return False
    return True
