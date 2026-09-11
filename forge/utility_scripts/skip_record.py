# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Apply a preflight unsupportable-version verdict as a committed skip record.

A workflow driver honors the verdict before preparing any scaffold: the
entries land in the artifact's ``index.json``, the index is validated, and the
index-only tree goes to the unchanged publication pipeline
(§FS-unsupportable-version-diagnosis). A verdict that cannot be applied falls
through to normal generation.
"""

import json
import os
import subprocess
from typing import Callable

from utility_scripts.continuation_marker import (
    PHASE_FINALIZATION,
    PHASE_PUBLICATION,
    save_phase_update,
)
from utility_scripts.gradle_environment import gradle_command_environment
from utility_scripts.logged_command import run_logged_command
from utility_scripts.stage_logger import log_detail


def write_skip_record(
        repo_path: str,
        group: str,
        artifact: str,
        entries: list[dict[str, str]],
) -> list[str]:
    """Merge the verdict's skipped versions into the artifact's index.json.

    Entries land on the unique ``latest: true`` index entry, matching the shape
    the compatibility automation reads (§root/FS-library-version-update-automation.1).
    Versions already recorded keep their existing reason. Returns the versions
    newly written.
    """
    index_path = os.path.join(repo_path, "metadata", group, artifact, "index.json")
    with open(index_path, "r", encoding="utf-8") as index_file:
        index_entries = json.load(index_file)
    if not isinstance(index_entries, list) or not index_entries:
        raise ValueError(f"Unexpected index.json shape at {index_path}")

    target_entry = next(
        (entry for entry in index_entries if isinstance(entry, dict) and entry.get("latest") is True),
        index_entries[0],
    )
    existing = target_entry.get("skipped-versions") or []
    existing_versions = {
        record.get("version") for record in existing if isinstance(record, dict)
    }
    written: list[str] = []
    for skipped in entries:
        if skipped["version"] in existing_versions:
            continue
        existing.append({"version": skipped["version"], "reason": skipped["reason"]})
        written.append(skipped["version"])
    if not written:
        return written

    # Keep skipped-versions in the position validateIndexFiles renders it:
    # rebuild the entry with the record after tested-versions when present.
    rebuilt: dict = {}
    inserted = False
    for key, value in target_entry.items():
        if key == "skipped-versions":
            continue
        rebuilt[key] = value
        if key == "tested-versions":
            rebuilt["skipped-versions"] = existing
            inserted = True
    if not inserted:
        rebuilt["skipped-versions"] = existing
    index_entries[index_entries.index(target_entry)] = rebuilt

    rendered = json.dumps(index_entries, indent=2, ensure_ascii=False).replace('": ', '" : ')
    with open(index_path, "w", encoding="utf-8") as index_file:
        index_file.write(rendered + "\n")
    return written


def _validate_index_with_gradle(repo_path: str, coordinates: str) -> bool:
    """Run validateIndexFiles for the coordinates; the gate on the skip record."""
    result = run_logged_command(
        ["./gradlew", "validateIndexFiles", f"-Pcoordinates={coordinates}"],
        cwd=repo_path,
        task_type="preflight-skip-record",
        subject=coordinates,
        action="validateIndexFiles",
        env=gradle_command_environment(repo_path),
        stage="preflight-skip-record",
    )
    return result.returncode == 0


def apply_preflight_skip_record(
        *,
        reachability_repo_path: str,
        group: str,
        artifact: str,
        target_version: str,
        entries: list[dict[str, str]],
        continuation_marker_path: str | None,
        validate_index: Callable[[str, str], bool] = _validate_index_with_gradle,
) -> str | None:
    """Turn a preflight skip verdict into a committed, validated skip record.

    Returns the ending commit when the index-only tree is ready for
    publication, None when the run should fall through to normal generation
    (§FS-unsupportable-version-diagnosis).
    """
    coordinates = f"{group}:{artifact}:{target_version}"
    index_path = os.path.join("metadata", group, artifact, "index.json")
    try:
        written = write_skip_record(reachability_repo_path, group, artifact, entries)
    except (OSError, ValueError) as exc:
        log_detail("preflight-skip-record", f"Could not write skip record for {coordinates}: {exc}")
        return None
    if not written:
        log_detail("preflight-skip-record", f"All skip entries for {coordinates} are already recorded.")
        _restore_index(reachability_repo_path, index_path)
        return None
    if not validate_index(reachability_repo_path, coordinates):
        log_detail("preflight-skip-record", f"validateIndexFiles rejected the skip record for {coordinates}.")
        _restore_index(reachability_repo_path, index_path)
        return None
    subprocess.run(["git", "add", index_path], cwd=reachability_repo_path, check=True)
    subprocess.run(
        ["git", "commit", "-m", f"Record skipped versions for {group}:{artifact}"],
        cwd=reachability_repo_path,
        capture_output=True,
        text=True,
        check=True,
    )
    log_detail("preflight-skip-record", f"Recorded skipped versions for {group}:{artifact}: {', '.join(written)}")
    save_phase_update(
        continuation_marker_path,
        lambda marker: (
            marker.mark_phase_completed(PHASE_FINALIZATION),
            marker.mark_phase_pending(PHASE_PUBLICATION),
        ),
    )
    return subprocess.check_output(
        ["git", "rev-parse", "HEAD"], cwd=reachability_repo_path, text=True,
    ).strip()


def _restore_index(reachability_repo_path: str, index_path: str) -> None:
    """Drop the uncommitted index edit so normal generation starts clean."""
    subprocess.run(
        ["git", "checkout", "--", index_path],
        cwd=reachability_repo_path,
        check=True,
    )
