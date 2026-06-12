# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Split library-update tested-version aliases when generated JVM tests stop being compatible."""

import copy
import json
import os
import re
import shutil
import subprocess
from typing import Any

from git_scripts.common_git import (
    get_issue_project_item_status,
    gh,
    gh_json,
    parse_coordinate_parts,
    stage_and_commit as stage_and_commit_common,
)
from utility_scripts.gradle_environment import gradle_command_environment
from utility_scripts.metadata_index import (
    index_path,
    load_index_entries,
    resolve_metadata_version,
    resolve_test_version,
)
from utility_scripts.metrics_writer import (
    PENDING_METRICS_FILENAME,
    read_pending_metrics,
    write_pending_metrics,
)
from utility_scripts.task_logs import build_timestamped_task_log_path, display_log_path


ALIAS_SPLIT_METRICS_KEY = "library_update_alias_split"
FOLLOW_UP_TRAILER = "Forge-Unblocks-Issue"
PROJECT_NUMBER = 30
STATUS_FIELD_NAME = "Status"
STATUS_IN_PROGRESS = "In Progress"


def format_follow_up_trailer(issue_number: int) -> str:
    """Return the machine-readable follow-up issue trailer used by merge follow-up."""
    return f"{FOLLOW_UP_TRAILER}: #{issue_number}"


def extract_follow_up_issue_numbers(body: str | None) -> list[int]:
    """Return follow-up issues from PR text trailers, ignoring casual references."""
    if not isinstance(body, str):
        return []
    return [
        int(match.group(1))
        for match in re.finditer(rf"(?m)^{re.escape(FOLLOW_UP_TRAILER)}:\s*#(\d+)\b", body)
    ]


def load_alias_split_metrics(metrics_repo_path: str | None) -> dict[str, Any] | None:
    """Load recorded split metadata from pending metrics."""
    if metrics_repo_path is None:
        return None
    pending_path = os.path.join(metrics_repo_path, PENDING_METRICS_FILENAME)
    if not os.path.isfile(pending_path):
        return None
    metrics = read_pending_metrics(metrics_repo_path)
    split = metrics.get(ALIAS_SPLIT_METRICS_KEY)
    return split if isinstance(split, dict) else None


def write_alias_split_metrics(metrics_repo_path: str | None, split: dict[str, Any]) -> None:
    """Persist split metadata so PR publication can reference the follow-up issue."""
    if metrics_repo_path is None:
        raise RuntimeError("Cannot persist library-update alias split without a metrics repository path.")
    pending_path = os.path.join(metrics_repo_path, PENDING_METRICS_FILENAME)
    if not os.path.isfile(pending_path):
        raise RuntimeError(f"Cannot persist library-update alias split; missing {pending_path}.")
    metrics = read_pending_metrics(metrics_repo_path)
    metrics[ALIAS_SPLIT_METRICS_KEY] = split
    write_pending_metrics(metrics_repo_path, metrics)


def maybe_split_library_update_tested_versions(
        *,
        repo_path: str,
        coordinates: str,
        base_ref: str,
        metrics_repo_path: str | None,
) -> dict[str, Any] | None:
    """Run the JVM alias sweep and split at the first successor failure.

    Library-update generated tests must remain compatible with every alias in
    the changed index entry. When a later alias fails, the generated prefix and
    copied baseline successor range are split before PR eligibility.
    §FS-library-update-tested-version-split
    """
    group, artifact, requested_version = parse_coordinate_parts(coordinates)
    target_metadata_version = resolve_metadata_version(repo_path, group, artifact, requested_version)
    target_test_version = resolve_test_version(repo_path, group, artifact, requested_version)
    target_coordinates = f"{group}:{artifact}:{target_metadata_version}"
    entries = load_index_entries(repo_path, group, artifact) or []
    target_entry = _find_entry_by_metadata_version(entries, target_metadata_version)
    if target_entry is None:
        return None
    if not _has_changed_tests(repo_path, base_ref, group, artifact, target_test_version):
        return None

    tested_versions = _entry_tested_versions(target_entry)
    if len(tested_versions) <= 1:
        return None

    existing_split = load_alias_split_metrics(metrics_repo_path)
    if existing_split is not None and existing_split.get("successor_metadata_version"):
        return existing_split

    sweep = _run_java_alias_sweep(repo_path, target_coordinates, tested_versions)
    if sweep["failed_version"] is None:
        return None

    failed_index = int(sweep["failed_index"])
    if failed_index == 0:
        raise RuntimeError(
            f"Library-update JVM alias sweep failed on the first tested version "
            f"{sweep['failed_version']} for {target_coordinates}; no passing prefix can be split."
        )

    base_entries = _load_index_entries_from_commit(base_ref, repo_path, group, artifact)
    original_entry = _find_entry_covering_version(base_entries, str(sweep["failed_version"]))
    if original_entry is None:
        raise RuntimeError(
            f"Could not find base index entry covering {group}:{artifact}:{sweep['failed_version']} "
            f"in {base_ref}."
        )

    split = _apply_alias_split(
        repo_path=repo_path,
        base_ref=base_ref,
        group=group,
        artifact=artifact,
        requested_coordinates=coordinates,
        target_metadata_version=target_metadata_version,
        original_entry=original_entry,
        tested_versions=tested_versions,
        failed_index=failed_index,
        sweep=sweep,
    )
    write_alias_split_metrics(metrics_repo_path, split)
    stage_and_commit_common(
        [
            os.path.join("metadata", group, artifact, "index.json"),
            os.path.join("metadata", group, artifact, split["successor_metadata_version"]),
            os.path.join("tests", "src", group, artifact, split["successor_metadata_version"]),
        ],
        f"Split tested-version aliases for {coordinates}",
        cwd=repo_path,
    )
    return split


def ensure_alias_split_follow_up_issue(
        *,
        metrics_repo_path: str | None,
        current_issue_number: int | None,
        repo: str,
) -> dict[str, Any] | None:
    """Create and park the successor library-update issue after local CI passes.

    The issue is kept `In Progress` until the current PR merges, preventing the
    normal work queue from claiming the successor too early.
    §FS-library-update-tested-version-split
    """
    split = load_alias_split_metrics(metrics_repo_path)
    if split is None:
        return None
    existing_issue = split.get("follow_up_issue_number")
    if isinstance(existing_issue, int):
        return split

    coordinates = str(split["successor_coordinates"])
    issue_number = _find_existing_open_issue_number(repo, coordinates)
    if issue_number is None:
        issue_number = _create_follow_up_issue(repo, split, current_issue_number)
    _ensure_issue_project_status(repo, PROJECT_NUMBER, issue_number, STATUS_IN_PROGRESS)

    updated_split = dict(split)
    updated_split["follow_up_issue_number"] = issue_number
    write_alias_split_metrics(metrics_repo_path, updated_split)
    return updated_split


def format_alias_split_pr_section(split: dict[str, Any] | None) -> str:
    """Return PR body text for a tested-version alias split."""
    if not isinstance(split, dict):
        return ""
    issue_number = split.get("follow_up_issue_number")
    issue_lines = ""
    if isinstance(issue_number, int):
        issue_lines = (
            f"Refs: #{issue_number}\n"
            f"{format_follow_up_trailer(issue_number)}\n"
        )
    return (
        "\n### Tested-Version Alias Split\n\n"
        f"- First failing JVM alias: `{split.get('failed_version')}`\n"
        f"- Generated prefix retained on `{split.get('current_metadata_version')}`: "
        f"{_format_version_list(split.get('passing_versions'))}\n"
        f"- Baseline successor entry: `{split.get('successor_metadata_version')}` with "
        f"{_format_version_list(split.get('successor_versions'))}\n"
        f"- Baseline metadata copied from: `{split.get('original_metadata_version')}`\n"
        f"- Baseline tests copied from: `{split.get('original_test_version')}`\n"
        f"{issue_lines}"
    )


def _entry_tested_versions(entry: dict[str, Any]) -> list[str]:
    versions = entry.get("tested-versions")
    if not isinstance(versions, list):
        return []
    return [str(version) for version in versions]


def _find_entry_by_metadata_version(entries: list[dict[str, Any]], metadata_version: str) -> dict[str, Any] | None:
    for entry in entries:
        if isinstance(entry, dict) and entry.get("metadata-version") == metadata_version:
            return entry
    return None


def _find_entry_covering_version(entries: list[dict[str, Any]], version: str) -> dict[str, Any] | None:
    for entry in entries:
        if isinstance(entry, dict) and version in _entry_tested_versions(entry):
            return entry
    return None


def _has_changed_tests(repo_path: str, base_ref: str, group: str, artifact: str, test_version: str) -> bool:
    test_prefix = os.path.join("tests", "src", group, artifact, test_version).replace(os.sep, "/")
    result = subprocess.run(
        ["git", "diff", "--name-only", "--diff-filter=ACMRT", base_ref, "HEAD", "--", test_prefix],
        cwd=repo_path,
        capture_output=True,
        text=True,
        check=True,
    )
    return any(line.strip() for line in result.stdout.splitlines())


def _load_index_entries_from_commit(
        commit: str,
        repo_path: str,
        group: str,
        artifact: str,
) -> list[dict[str, Any]]:
    relative_path = os.path.relpath(index_path(repo_path, group, artifact), repo_path).replace(os.sep, "/")
    result = subprocess.run(
        ["git", "show", f"{commit}:{relative_path}"],
        cwd=repo_path,
        capture_output=True,
        text=True,
        check=True,
    )
    parsed = json.loads(result.stdout)
    if not isinstance(parsed, list):
        raise RuntimeError(f"Base index is not a JSON array: {relative_path}")
    return [entry for entry in parsed if isinstance(entry, dict)]


def _run_java_alias_sweep(repo_path: str, coordinates: str, versions: list[str]) -> dict[str, Any]:
    commands: list[dict[str, Any]] = []
    for index, version in enumerate(versions):
        log_path = build_timestamped_task_log_path(
            "library-update-alias-sweep",
            coordinates,
            f"javaTest-{version}",
        )
        env = gradle_command_environment(repo_path, dict(os.environ))
        env["GVM_TCK_LV"] = version
        env.pop("GVM_TCK_NATIVE_IMAGE_MODE", None)
        command = ["./gradlew", "clean", "javaTest", f"-Pcoordinates={coordinates}"]
        with open(log_path, "w", encoding="utf-8") as log_file:
            completed = subprocess.run(
                command,
                cwd=repo_path,
                env=env,
                stdin=subprocess.DEVNULL,
                stdout=log_file,
                stderr=subprocess.STDOUT,
                text=True,
                check=False,
            )
        command_record = {
            "version": version,
            "command": " ".join(command),
            "returncode": completed.returncode,
            "log_path": display_log_path(log_path),
        }
        commands.append(command_record)
        if completed.returncode != 0:
            return {
                "commands": commands,
                "failed_version": version,
                "failed_index": index,
            }
    return {
        "commands": commands,
        "failed_version": None,
        "failed_index": None,
    }


def _apply_alias_split(
        *,
        repo_path: str,
        base_ref: str,
        group: str,
        artifact: str,
        requested_coordinates: str,
        target_metadata_version: str,
        original_entry: dict[str, Any],
        tested_versions: list[str],
        failed_index: int,
        sweep: dict[str, Any],
) -> dict[str, Any]:
    failed_version = tested_versions[failed_index]
    passing_versions = tested_versions[:failed_index]
    successor_versions = tested_versions[failed_index:]
    original_metadata_version = str(original_entry.get("metadata-version") or "")
    original_test_version = str(original_entry.get("test-version") or original_metadata_version)
    if not original_metadata_version or not original_test_version:
        raise RuntimeError(f"Base entry covering {failed_version} lacks metadata/test version fields.")

    _copy_tree_from_commit(
        repo_path,
        base_ref,
        os.path.join("metadata", group, artifact, original_metadata_version),
        os.path.join(repo_path, "metadata", group, artifact, failed_version),
    )
    _copy_tree_from_commit(
        repo_path,
        base_ref,
        os.path.join("tests", "src", group, artifact, original_test_version),
        os.path.join(repo_path, "tests", "src", group, artifact, failed_version),
    )

    entries = load_index_entries(repo_path, group, artifact) or []
    target_index = _find_entry_index_by_metadata_version(entries, target_metadata_version)
    if target_index is None:
        raise RuntimeError(f"Missing current index entry for metadata-version {target_metadata_version}")

    current_entry = entries[target_index]
    split_latest = current_entry.get("latest") is True
    current_entry["tested-versions"] = passing_versions
    if split_latest:
        current_entry.pop("latest", None)

    successor_entry = copy.deepcopy(original_entry)
    successor_entry["metadata-version"] = failed_version
    successor_entry["tested-versions"] = successor_versions
    successor_entry.pop("test-version", None)
    if split_latest:
        successor_entry["latest"] = True
    else:
        successor_entry.pop("latest", None)

    entries.insert(target_index, successor_entry)
    _write_index_entries(repo_path, group, artifact, entries)

    return {
        "requested_coordinates": requested_coordinates,
        "current_coordinates": f"{group}:{artifact}:{target_metadata_version}",
        "successor_coordinates": f"{group}:{artifact}:{failed_version}",
        "current_metadata_version": target_metadata_version,
        "successor_metadata_version": failed_version,
        "failed_version": failed_version,
        "passing_versions": passing_versions,
        "successor_versions": successor_versions,
        "original_metadata_version": original_metadata_version,
        "original_test_version": original_test_version,
        "commands": sweep.get("commands") or [],
    }


def _find_entry_index_by_metadata_version(entries: list[dict[str, Any]], metadata_version: str) -> int | None:
    for index, entry in enumerate(entries):
        if isinstance(entry, dict) and entry.get("metadata-version") == metadata_version:
            return index
    return None


def _copy_tree_from_commit(repo_path: str, commit: str, source_rel: str, destination_abs: str) -> None:
    source_rel = source_rel.replace(os.sep, "/")
    result = subprocess.run(
        ["git", "ls-tree", "-r", "-z", "--name-only", commit, "--", source_rel],
        cwd=repo_path,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=True,
    )
    paths = [path.decode("utf-8") for path in result.stdout.split(b"\0") if path]
    if not paths:
        raise RuntimeError(f"No files found in {commit}:{source_rel}")
    if os.path.exists(destination_abs):
        shutil.rmtree(destination_abs)
    for path in paths:
        file_result = subprocess.run(
            ["git", "show", f"{commit}:{path}"],
            cwd=repo_path,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=True,
        )
        relative_file = os.path.relpath(path, source_rel)
        destination_file = os.path.join(destination_abs, relative_file)
        os.makedirs(os.path.dirname(destination_file), exist_ok=True)
        with open(destination_file, "wb") as output_file:
            output_file.write(file_result.stdout)


def _write_index_entries(repo_path: str, group: str, artifact: str, entries: list[dict[str, Any]]) -> None:
    path = index_path(repo_path, group, artifact)
    with open(path, "w", encoding="utf-8") as index_file:
        json.dump(entries, index_file, indent=2)
        index_file.write("\n")


def _find_existing_open_issue_number(repo: str, coordinates: str) -> int | None:
    issues = gh_json(
        "issue",
        "list",
        "--repo",
        repo,
        "--state",
        "open",
        "--search",
        f'"{coordinates}" in:title,body',
        "--json",
        "number,title,body",
        "--limit",
        "20",
    )
    if not isinstance(issues, list):
        return None
    for issue in issues:
        if not isinstance(issue, dict):
            continue
        text = f"{issue.get('title') or ''}\n{issue.get('body') or ''}"
        if coordinates in text and isinstance(issue.get("number"), int):
            return int(issue["number"])
    return None


def _create_follow_up_issue(repo: str, split: dict[str, Any], current_issue_number: int | None) -> int:
    coordinates = str(split["successor_coordinates"])
    blocked_by = f"Blocked by the current update issue #{current_issue_number}." if current_issue_number else ""
    body = f"""This issue tracks the successor library-update split for `{coordinates}`.

Forge split the previous tested-version range because generated JVM tests first failed at `{split['failed_version']}`.

{blocked_by}

Successor tested versions to preserve:
{_format_issue_version_lines(split.get("successor_versions"))}

The current PR keeps the generated progress for `{split['current_coordinates']}`
and copies baseline support for this successor range.
"""
    result = gh(
        "issue",
        "create",
        "--repo",
        repo,
        "--title",
        f"Update existing library: {coordinates}",
        "--body",
        body,
        "--label",
        "library-update-request",
    )
    match = re.search(r"/issues/(\d+)", result.stdout)
    if match is None:
        raise RuntimeError(f"Could not parse created follow-up issue number from: {result.stdout.strip()}")
    return int(match.group(1))


def _ensure_issue_project_status(repo: str, project_number: int, issue_number: int, status: str) -> None:
    item_id, current_status = get_issue_project_item_status(
        repo,
        project_number,
        issue_number,
        STATUS_FIELD_NAME,
    )
    project_id, field_id, option_ids = _project_status_field_info(repo, project_number)
    if status not in option_ids:
        raise RuntimeError(f"Missing project status option {status!r}")
    if item_id is None:
        issue_id = _issue_node_id(repo, issue_number)
        item_id = _add_issue_to_project(project_id, issue_id)
    if current_status != status:
        _set_project_item_status(project_id, item_id, field_id, option_ids[status])


def _project_status_field_info(repo: str, project_number: int) -> tuple[str, str, dict[str, str]]:
    owner, _ = repo.split("/")
    query = f"""
    query {{
      organization(login: "{owner}") {{
        projectV2(number: {project_number}) {{
          id
          fields(first: 50) {{
            nodes {{
              ... on ProjectV2SingleSelectField {{
                id
                name
                options {{
                  id
                  name
                }}
              }}
            }}
          }}
        }}
      }}
    }}
    """
    result = gh_json("api", "graphql", "-f", f"query={query}")
    project = (
        result.get("data", {})
        .get("organization", {})
        .get("projectV2", {})
    )
    if not isinstance(project, dict) or not project.get("id"):
        raise RuntimeError(f"Missing GitHub project {project_number} for {owner}")
    for field in project.get("fields", {}).get("nodes", []):
        if not isinstance(field, dict) or field.get("name") != STATUS_FIELD_NAME:
            continue
        options = {
            str(option["name"]): str(option["id"])
            for option in field.get("options", [])
            if isinstance(option, dict) and option.get("name") and option.get("id")
        }
        return str(project["id"]), str(field["id"]), options
    raise RuntimeError(f"Missing project status field {STATUS_FIELD_NAME!r}")


def _issue_node_id(repo: str, issue_number: int) -> str:
    data = gh_json(
        "issue",
        "view",
        str(issue_number),
        "--repo",
        repo,
        "--json",
        "id",
    )
    issue_id = data.get("id") if isinstance(data, dict) else None
    if not isinstance(issue_id, str) or not issue_id:
        raise RuntimeError(f"Missing GitHub node id for issue #{issue_number}")
    return issue_id


def _add_issue_to_project(project_id: str, issue_id: str) -> str:
    mutation = """
    mutation($projectId: ID!, $contentId: ID!) {
      addProjectV2ItemById(input: {projectId: $projectId, contentId: $contentId}) {
        item {
          id
        }
      }
    }
    """
    result = gh_json(
        "api",
        "graphql",
        "-f",
        f"query={mutation}",
        "-f",
        f"projectId={project_id}",
        "-f",
        f"contentId={issue_id}",
    )
    item_id = (
        result.get("data", {})
        .get("addProjectV2ItemById", {})
        .get("item", {})
        .get("id")
    )
    if not isinstance(item_id, str) or not item_id:
        raise RuntimeError("Missing project item id after adding follow-up issue")
    return item_id


def _set_project_item_status(project_id: str, item_id: str, field_id: str, option_id: str) -> None:
    mutation = """
    mutation($projectId: ID!, $itemId: ID!, $fieldId: ID!, $optionId: String!) {
      updateProjectV2ItemFieldValue(input: {
        projectId: $projectId,
        itemId: $itemId,
        fieldId: $fieldId,
        value: {singleSelectOptionId: $optionId}
      }) {
        projectV2Item {
          id
        }
      }
    }
    """
    gh_json(
        "api",
        "graphql",
        "-f",
        f"query={mutation}",
        "-f",
        f"projectId={project_id}",
        "-f",
        f"itemId={item_id}",
        "-f",
        f"fieldId={field_id}",
        "-f",
        f"optionId={option_id}",
    )


def _format_version_list(value: Any) -> str:
    if not isinstance(value, list):
        return "`none`"
    return ", ".join(f"`{version}`" for version in value) or "`none`"


def _format_issue_version_lines(value: Any) -> str:
    if not isinstance(value, list):
        return "- none"
    return "\n".join(f"- `{version}`" for version in value) or "- none"
