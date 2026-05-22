# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Policy checks for Native Image config files in generated test resources."""

from __future__ import annotations

import os
import subprocess

from utility_scripts.metadata_index import resolve_test_version

LEGACY_TEST_NATIVE_IMAGE_CONFIG_FILENAMES = frozenset(
    {
        "reflect-config.json",
        "resource-config.json",
        "proxy-config.json",
        "serialization-config.json",
        "jni-config.json",
        "predefined-classes-config.json",
    }
)
TEST_NATIVE_IMAGE_RESOURCE_SEGMENT = "/src/test/resources/META-INF/native-image/"


def format_legacy_test_native_image_config_error(paths: list[str]) -> str:
    """Return the standard error shown when legacy test-resource config is found."""
    joined_paths = "\n - ".join(paths)
    return (
        "ERROR: Legacy Native Image config files are not supported in generated test resources. "
        "Move test-only metadata into reachability-metadata.json and remove:\n - "
        f"{joined_paths}"
    )


def parse_coordinate_parts(coordinates: str) -> tuple[str, str, str]:
    """Return group, artifact, version from Maven coordinates."""
    parts = coordinates.split(":")
    if len(parts) != 3:
        raise ValueError(f"Expected Maven coordinates group:artifact:version, got {coordinates!r}")
    return parts[0], parts[1], parts[2]


def find_legacy_test_native_image_config_files_for_coordinate(repo_path: str, coordinates: str) -> list[str]:
    """Return legacy Native Image config files under the resolved test resources for one coordinate."""
    native_image_dir = os.path.join(repo_path, _coordinate_test_native_image_prefix(repo_path, coordinates))
    if not os.path.isdir(native_image_dir):
        return []

    paths: list[str] = []
    for root, _dirnames, filenames in os.walk(native_image_dir):
        for filename in filenames:
            if filename in LEGACY_TEST_NATIVE_IMAGE_CONFIG_FILENAMES:
                paths.append(_repo_relative_path(repo_path, os.path.join(root, filename)))
    return sorted(paths)


def find_changed_legacy_test_native_image_config_files_for_coordinate(
        repo_path: str,
        coordinates: str,
        base_commit: str,
        head_commit: str = "HEAD",
) -> list[str]:
    """Return changed legacy test-resource Native Image config files for one coordinate."""
    paths = find_changed_legacy_test_native_image_config_files(repo_path, base_commit, head_commit)
    return _filter_coordinate_test_native_image_paths(repo_path, coordinates, paths)


def find_changed_legacy_test_native_image_config_files(
        repo_path: str,
        base_commit: str,
        head_commit: str = "HEAD",
) -> list[str]:
    """Return changed legacy Native Image config files under test resources."""
    paths = _changed_files(repo_path, base_commit, head_commit)
    return sorted(path for path in paths if is_legacy_test_native_image_config_path(path))


def find_uncommitted_legacy_test_native_image_config_files_for_coordinate(repo_path: str, coordinates: str) -> list[str]:
    """Return uncommitted legacy test-resource Native Image config files for one coordinate."""
    paths = _uncommitted_files(repo_path)
    legacy_paths = [path for path in paths if is_legacy_test_native_image_config_path(path)]
    return _filter_coordinate_test_native_image_paths(repo_path, coordinates, legacy_paths)


def is_legacy_test_native_image_config_path(path: str) -> bool:
    """Return True when a repo-relative path is a legacy test-resource Native Image config file."""
    normalized = path.replace("\\", "/")
    return (
        normalized.startswith("tests/src/")
        and TEST_NATIVE_IMAGE_RESOURCE_SEGMENT in normalized
        and os.path.basename(normalized) in LEGACY_TEST_NATIVE_IMAGE_CONFIG_FILENAMES
    )


def _coordinate_test_native_image_prefix(repo_path: str, coordinates: str) -> str:
    group, artifact, version = parse_coordinate_parts(coordinates)
    test_version = resolve_test_version(repo_path, group, artifact, version)
    return (
        f"tests/src/{group}/{artifact}/{test_version}/"
        "src/test/resources/META-INF/native-image/"
    )


def _filter_coordinate_test_native_image_paths(repo_path: str, coordinates: str, paths: list[str]) -> list[str]:
    prefix = _coordinate_test_native_image_prefix(repo_path, coordinates)
    return sorted(path for path in paths if path.replace("\\", "/").startswith(prefix))


def _changed_files(repo_path: str, base_commit: str, head_commit: str) -> list[str]:
    diff_range = f"{base_commit}...{head_commit}"
    result = subprocess.run(
        ["git", "diff", "--name-only", "--diff-filter=ACMRT", diff_range],
        cwd=repo_path,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        check=False,
    )
    if result.returncode != 0:
        result = subprocess.run(
            ["git", "diff", "--name-only", "--diff-filter=ACMRT", base_commit, head_commit],
            cwd=repo_path,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            check=True,
        )
    return [line.strip() for line in result.stdout.splitlines() if line.strip()]


def _uncommitted_files(repo_path: str) -> list[str]:
    tracked_result = subprocess.run(
        ["git", "diff", "--name-only", "--diff-filter=ACMRT", "HEAD"],
        cwd=repo_path,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        check=True,
    )
    untracked_result = subprocess.run(
        ["git", "ls-files", "--others", "--exclude-standard"],
        cwd=repo_path,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        check=True,
    )
    return sorted({
        line.strip()
        for output in (tracked_result.stdout, untracked_result.stdout)
        for line in output.splitlines()
        if line.strip()
    })


def _repo_relative_path(repo_path: str, path: str) -> str:
    return os.path.relpath(path, repo_path).replace(os.sep, "/")
