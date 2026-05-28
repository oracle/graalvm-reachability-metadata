# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Utilities for copying and retargeting versioned test projects."""

import os
import re
import shutil
from dataclasses import dataclass
from typing import Pattern


@dataclass(frozen=True)
class VersionedTestProjectPreparation:
    """Description of a copied or retargeted versioned test project."""

    source_test_dir: str
    target_test_dir: str
    copied: bool
    replacements: tuple[tuple[str, str], ...]
    gradle_properties_path: str | None


def same_filesystem_path(first_path: str, second_path: str) -> bool:
    """Return true when two paths resolve to the same filesystem location."""
    return os.path.normcase(os.path.realpath(first_path)) == os.path.normcase(os.path.realpath(second_path))


def copy_directory_replace(destination: str, source: str) -> None:
    """Replace a destination directory with a copy of the source directory."""
    if same_filesystem_path(destination, source):
        return
    if os.path.exists(destination):
        shutil.rmtree(destination)
    shutil.copytree(source, destination)


def _safe_version_pattern(version: str) -> Pattern[str]:
    """Match a standalone version token without touching longer version-like strings."""
    return re.compile(rf"(?<![A-Za-z0-9_.-]){re.escape(version)}(?![A-Za-z0-9_.-])")


def _allows_bare_version_rewrite(path: str) -> bool:
    """Return whether bare version tokens may be rewritten in this cloned file."""
    file_name = os.path.basename(path)
    if file_name == "gradle.properties":
        return True
    return os.path.splitext(file_name)[1] in {
        ".groovy",
        ".java",
        ".json",
        ".kt",
        ".properties",
        ".xml",
        ".yaml",
        ".yml",
    }


def rewrite_text_files(
        root_dir: str,
        replacements: list[tuple[str, str]],
        allow_bare_versions: bool = False,
) -> None:
    """Rewrite literal coordinates and optional standalone version tokens in text files."""
    if not os.path.isdir(root_dir):
        return
    literal_replacements = [
        (old_value, new_value)
        for old_value, new_value in replacements
        if old_value and ":" in old_value
    ]
    version_replacements = [
        (_safe_version_pattern(old_value), new_value)
        for old_value, new_value in replacements
        if old_value and ":" not in old_value
    ]
    for current_root, _, file_names in os.walk(root_dir):
        for file_name in file_names:
            path = os.path.join(current_root, file_name)
            try:
                with open(path, "r", encoding="utf-8") as file:
                    original = file.read()
            except UnicodeDecodeError:
                continue
            updated = original
            for old_value, new_value in literal_replacements:
                updated = updated.replace(old_value, new_value)
            if allow_bare_versions and _allows_bare_version_rewrite(path):
                for pattern, new_value in version_replacements:
                    updated = pattern.sub(new_value, updated)
            if updated != original:
                with open(path, "w", encoding="utf-8") as file:
                    file.write(updated)


def rewrite_gradle_properties_versions(
        gradle_properties_path: str,
        old_versions: tuple[str, ...],
        new_version: str,
) -> None:
    """Rewrite old version tokens in a Gradle properties file."""
    if not os.path.isfile(gradle_properties_path) or not old_versions:
        return
    with open(gradle_properties_path, "r", encoding="utf-8") as properties_file:
        original = properties_file.read()
    updated = original
    for old_version in sorted(set(old_versions), key=len, reverse=True):
        updated = updated.replace(old_version, new_version)
    if updated != original:
        with open(gradle_properties_path, "w", encoding="utf-8") as properties_file:
            properties_file.write(updated)


def normalize_cloned_gradle_properties(
        test_dir: str,
        group: str,
        artifact: str,
        requested_version: str,
) -> str | None:
    """Update scaffold-owned Gradle properties after cloning a test project."""
    gradle_properties_path = os.path.join(test_dir, "gradle.properties")
    if not os.path.isfile(gradle_properties_path):
        return None

    expected_values = {
        "library.coordinates": f"{group}:{artifact}:{requested_version}",
        "library.version": requested_version,
        "metadata.dir": f"{group}/{artifact}/{requested_version}/",
    }
    with open(gradle_properties_path, "r", encoding="utf-8") as properties_file:
        lines = properties_file.readlines()

    updated_lines: list[str] = []
    for line in lines:
        line_ending = "\n" if line.endswith("\n") else ""
        content = line[:-1] if line_ending else line
        stripped = content.lstrip()
        if not stripped or stripped.startswith("#"):
            updated_lines.append(line)
            continue
        key_separator = "=" if "=" in stripped else ":" if ":" in stripped else None
        if key_separator is None:
            updated_lines.append(line)
            continue
        key = stripped.split(key_separator, 1)[0].strip()
        if key not in expected_values:
            updated_lines.append(line)
            continue
        indent = content[:len(content) - len(stripped)]
        updated_lines.append(f"{indent}{key} = {expected_values[key]}{line_ending}")

    updated_content = "".join(updated_lines)
    original_content = "".join(lines)
    if updated_content != original_content:
        with open(gradle_properties_path, "w", encoding="utf-8") as properties_file:
            properties_file.write(updated_content)
    return gradle_properties_path


def prepare_versioned_test_project(
        repo_path: str,
        group: str,
        artifact: str,
        source_version: str,
        target_version: str,
        *,
        source_test_dir: str | None = None,
        replace_existing: bool = True,
        rewrite_project_files: bool = True,
        replacements: list[tuple[str, str]] | None = None,
        allow_bare_versions: bool = False,
        normalize_gradle_properties: bool = True,
        gradle_property_old_versions: tuple[str, ...] = (),
        log_prefix: str | None = None,
) -> VersionedTestProjectPreparation:
    """Copy a versioned test project and retarget selected references.

    Mutations are limited to `tests/src/<group>/<artifact>/<target_version>`.
    """
    resolved_source_dir = source_test_dir or os.path.join(
        repo_path,
        "tests",
        "src",
        group,
        artifact,
        source_version,
    )
    target_test_dir = os.path.join(repo_path, "tests", "src", group, artifact, target_version)
    if not os.path.isdir(resolved_source_dir):
        raise FileNotFoundError(f"Missing source test project directory: {resolved_source_dir}")

    copied = False
    if same_filesystem_path(resolved_source_dir, target_test_dir):
        if log_prefix:
            print(f"{log_prefix} Source and destination test project are the same: {target_test_dir}")
    else:
        if replace_existing:
            copy_directory_replace(target_test_dir, resolved_source_dir)
        else:
            os.makedirs(target_test_dir, exist_ok=True)
            shutil.copytree(resolved_source_dir, target_test_dir, dirs_exist_ok=True)
        copied = True

    active_replacements = replacements or [
        (f"{group}:{artifact}:{source_version}", f"{group}:{artifact}:{target_version}"),
        (source_version, target_version),
    ]
    if rewrite_project_files:
        rewrite_text_files(target_test_dir, active_replacements, allow_bare_versions=allow_bare_versions)

    gradle_properties_path = os.path.join(target_test_dir, "gradle.properties")
    rewrite_gradle_properties_versions(gradle_properties_path, gradle_property_old_versions, target_version)
    normalized_path = None
    if normalize_gradle_properties:
        normalized_path = normalize_cloned_gradle_properties(target_test_dir, group, artifact, target_version)

    return VersionedTestProjectPreparation(
        source_test_dir=resolved_source_dir,
        target_test_dir=target_test_dir,
        copied=copied,
        replacements=tuple(active_replacements),
        gradle_properties_path=normalized_path if normalized_path is not None else (
            gradle_properties_path if os.path.isfile(gradle_properties_path) else None
        ),
    )
