# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import json
import os
import re
import subprocess
import sys

from utility_scripts.style_checks import run_style_fix_and_checks
from utility_scripts.stage_logger import log_stage


def _run_gradle_command_with_output(repo_path: str, command: list[str]) -> subprocess.CompletedProcess[str]:
    """Run a Gradle command in the reachability repo and capture combined output."""
    return subprocess.run(
        command,
        cwd=repo_path,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=False,
    )


def _run_gradle_command(repo_path: str, command: list[str]) -> bool:
    """Run a Gradle command in the reachability repo, returning True on success."""
    result = _run_gradle_command_with_output(repo_path, command)
    if result.returncode != 0:
        print(result.stdout)
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


def _resolve_index_entry_for_current_version(index_entries: list[dict], library_version: str) -> dict | None:
    """Return the metadata index entry that should receive allowed-package updates."""
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

    index_entry = _resolve_index_entry_for_current_version(index_entries, library_version)
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

    log_stage("allowed-packages", f"Updated {index_path_display}: {', '.join(added_packages)}")
    return True


def _run_check_metadata_files_with_allowed_packages_fix(
        repo_path: str,
        library: str,
        group: str,
        artifact: str,
        library_version: str,
        log_stage_name: str,
) -> bool:
    """Run checkMetadataFiles and update missing allowed-packages when the task reports them."""
    log_stage(log_stage_name, f"Running checkMetadataFiles for {library}")
    seen_packages: set[str] = set()
    for attempt in range(1, 4):
        log_stage(log_stage_name, f"Running checkMetadataFiles attempt {attempt}/3 for {library}")
        result = _run_gradle_command_with_output(
            repo_path,
            ["./gradlew", "checkMetadataFiles", f"-Pcoordinates={library}"],
        )
        if result.returncode == 0:
            log_stage(log_stage_name, f"checkMetadataFiles passed for {library}")
            return True

        log_stage(log_stage_name, f"checkMetadataFiles failed for {library}; resolving missing allowed-packages")
        missing_packages = _extract_missing_allowed_packages(result.stdout)
        new_packages = missing_packages - seen_packages
        if not new_packages:
            log_stage(log_stage_name, "No new TypeReached packages found in checkMetadataFiles output")
            print(result.stdout)
            return False
        log_stage("allowed-packages", f"Adding allowed-packages for {library}: {', '.join(sorted(new_packages))}")
        if not _append_allowed_packages_to_metadata_index(
            repo_path=repo_path,
            library=library,
            group=group,
            artifact=artifact,
            library_version=library_version,
            packages=new_packages,
        ):
            print(result.stdout)
            return False
        seen_packages.update(new_packages)

    print(f"ERROR: checkMetadataFiles still fails after updating allowed-packages for {library}.", file=sys.stderr)
    return False


def run_library_finalization(
        repo_path: str,
        library: str,
        group: str,
        artifact: str,
        library_version: str,
        log_prefix: str | None = None,
        model_name: str | None = None,
) -> bool:
    """Run the shared end-of-workflow finalization steps for one library."""
    del log_prefix
    log_stage("split-test-only-metadata", f"Running splitTestOnlyMetadata for {library}")
    if not _run_gradle_command(repo_path, ["./gradlew", "splitTestOnlyMetadata", f"-Pcoordinates={library}"]):
        return False
    if not _run_check_metadata_files_with_allowed_packages_fix(
        repo_path=repo_path,
        library=library,
        group=group,
        artifact=artifact,
        library_version=library_version,
        log_stage_name="check-metadata-files",
    ):
        return False
    log_stage("style-checks", f"Running style checks for {library}")
    if not run_style_fix_and_checks(repo_path, library, model_name=model_name):
        return False
    log_stage("generate-library-stats", f"Running generateLibraryStats for {library}")
    if not _run_gradle_command(repo_path, ["./gradlew", "generateLibraryStats", f"-Pcoordinates={library}"]):
        return False
    return True
