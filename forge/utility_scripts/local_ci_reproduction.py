# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Opt-in full local reproduction of the per-PR CI surface.

Runs the changed-metadata native test matrix and the Spring AOT smoke tests
that the default pre-publication gate leaves to repository CI
(§FS-local-ci-equivalent-verification.2). The gate orchestration lives in
`local_ci_verification.py`.
"""

from __future__ import annotations

import json
import os
import re
import subprocess
import tempfile

from git_scripts.common_git import run_git_transport
from utility_scripts.local_ci_commands import (
    CommandRecord,
    LocalCIVerificationResult,
    parse_coordinate_parts,
    _GradleOutputFailure,
    _gradle_output,
    _log_local_ci,
    _run_recorded_command,
    _run_recorded_command_without_new_docker_images,
)
from utility_scripts.metadata_index import resolve_test_version

SPRING_AOT_BRANCH = "main"
SPRING_AOT_REPO_URL = "https://github.com/spring-projects/spring-aot-smoke-tests.git"


def _run_spring_aot_verification(
        repo_path: str,
        base_commit: str,
        changed_files: list[str],
        result: LocalCIVerificationResult,
) -> CommandRecord | None:
    if not _should_run_spring_aot_tests(changed_files):
        return None

    _log_local_ci("Running full Spring AOT verification", indent_level=1)
    with tempfile.TemporaryDirectory(prefix="forge-spring-aot-") as spring_parent:
        os.symlink(os.path.join(repo_path, "metadata"), os.path.join(spring_parent, "metadata"))
        spring_path = os.path.join(spring_parent, "spring-aot-smoke-tests")
        _log_local_ci("Fetching Spring AOT smoke tests", indent_level=1)
        run_git_transport(
            ["clone", "--depth", "1", "--branch", SPRING_AOT_BRANCH, SPRING_AOT_REPO_URL, spring_path],
            cwd=repo_path,
        )

        try:
            spring_matrix = _gradle_json_output(
                repo_path,
                "generateAffectedSpringTestMatrix",
                base_commit,
                result,
                "affected-spring-aot-matrix",
                extra_args=[
                    f"-PspringAotBranch={SPRING_AOT_BRANCH}",
                    f"-PspringAotPath={spring_path}",
                ],
            )
        except _GradleOutputFailure as exc:
            return exc.record

        return _run_spring_aot_matrix_entries(repo_path, spring_path, _matrix_entries(spring_matrix), result)


def _should_run_spring_aot_tests(changed_files: list[str]) -> bool:
    return any(path.startswith("metadata/") for path in changed_files)


def _run_test_matrix_entries(
        repo_path: str,
        entries: list[dict],
        result: LocalCIVerificationResult,
) -> CommandRecord | None:
    if not entries:
        _log_local_ci("Full metadata test matrix is empty", indent_level=1)
        return None

    pulled_coordinates: set[str] = set()
    for entry in entries:
        coordinates = str(entry.get("coordinates") or "").strip()
        if not coordinates or coordinates in pulled_coordinates:
            continue
        if not _coordinate_uses_docker(repo_path, coordinates):
            continue
        _log_local_ci(f"Pre-pulling Docker images for {coordinates}", indent_level=1)
        failed = _run_recorded_command(
            repo_path,
            "pull-allowed-docker-images",
            ["./gradlew", "pullAllowedDockerImages", f"-Pcoordinates={coordinates}"],
            result,
        )
        if failed is not None:
            # A declared image may be missing from the allow-list (e.g. a preflight pin dropped
            # by the library-scoped scaffold commit). Pin it deterministically and retry once.
            if _repair_missing_allowed_images(repo_path, coordinates):
                failed = _run_recorded_command(
                    repo_path,
                    "pull-allowed-docker-images",
                    ["./gradlew", "pullAllowedDockerImages", f"-Pcoordinates={coordinates}"],
                    result,
                )
            if failed is not None:
                return failed
        pulled_coordinates.add(coordinates)

    for entry in entries:
        coordinates = str(entry.get("coordinates") or "").strip()
        versions = entry.get("versions")
        if not coordinates or not isinstance(versions, list):
            continue
        version_text = ", ".join(str(version) for version in versions)
        _log_local_ci(f"Running full metadata test matrix entry for {coordinates}: {version_text}", indent_level=1)
        env = _matrix_env(entry)
        failed = _run_recorded_command(
            repo_path,
            "check-metadata-files",
            ["./gradlew", "checkMetadataFiles", f"-Pcoordinates={coordinates}"],
            result,
            env=env,
        )
        if failed is not None:
            return failed
        failed = _run_recorded_command_without_new_docker_images(
            repo_path,
            "run-consecutive-tests",
            [
                "bash",
                "./.github/workflows/scripts/run-consecutive-tests.sh",
                coordinates,
                json.dumps([str(version) for version in versions]),
            ],
            result,
            env=env,
            failure_output_pattern=r"^FAILED",
        )
        if failed is not None:
            return failed
    return None


def _run_spring_aot_matrix_entries(
        repo_path: str,
        spring_path: str,
        entries: list[dict],
        result: LocalCIVerificationResult,
) -> CommandRecord | None:
    _log_local_ci(f"Spring AOT matrix entries: {len(entries)}", indent_level=1)
    for entry in entries:
        project = str(entry.get("project") or "").strip()
        if not project:
            continue
        _log_local_ci(f"Running Spring AOT smoke test for {project}", indent_level=1)
        env = _spring_aot_env(entry)
        failed = _run_recorded_command(
            repo_path,
            "spring-aot-smoke-test",
            [
                "bash",
                ".github/workflows/scripts/run-spring-aot-triaged-test.sh",
                spring_path,
                project,
                "4.1.x",
            ],
            result,
            env=env,
        )
        if failed is not None:
            return failed
    return None


def _gradle_json_output(
        repo_path: str,
        task_name: str,
        base_commit: str,
        result: LocalCIVerificationResult,
        gate: str,
        extra_args: list[str] | None = None,
) -> dict:
    output = _gradle_output(repo_path, task_name, base_commit, result, gate, extra_args=extra_args)
    raw_matrix = output.get("matrix")
    if raw_matrix is None:
        return {}
    try:
        parsed = json.loads(raw_matrix)
    except json.JSONDecodeError as exc:
        raise RuntimeError(f"Failed to parse matrix output from {task_name}: {exc}") from exc
    return parsed if isinstance(parsed, dict) else {}


def _matrix_entries(matrix: dict) -> list[dict]:
    include = matrix.get("include")
    if isinstance(include, list):
        return [entry for entry in include if isinstance(entry, dict)]
    return []


def _matrix_env(entry: dict) -> dict[str, str]:
    env: dict[str, str] = {}
    native_image_mode = str(entry.get("nativeImageMode") or "").strip()
    if native_image_mode:
        env["GVM_TCK_NATIVE_IMAGE_MODE"] = native_image_mode
    java_version = str(entry.get("version") or "").strip()
    graalvm_home = _graalvm_home_for_java_version(java_version)
    if graalvm_home:
        env["GRAALVM_HOME"] = graalvm_home
        env["JAVA_HOME"] = graalvm_home
    return env


def _coordinate_uses_docker(repo_path: str, coordinates: str) -> bool:
    """Return whether the coordinate declares Docker images for its tests."""
    required_images_path = _required_docker_images_path(repo_path, coordinates)
    if required_images_path is None:
        return False
    with open(required_images_path, "r", encoding="utf-8") as required_images_file:
        return any(_is_required_docker_image_line(line) for line in required_images_file)


def _required_docker_images_path(repo_path: str, coordinates: str) -> str | None:
    """Return the Docker declaration path for the coordinate's resolved test version."""
    group, artifact, _version = parse_coordinate_parts(coordinates)
    test_version = _resolve_coordinate_test_version(repo_path, coordinates)
    if test_version is None:
        return None
    required_images_path = os.path.join(
        repo_path,
        "tests",
        "src",
        group,
        artifact,
        test_version,
        "required-docker-images.txt",
    )
    if not os.path.isfile(required_images_path):
        return None
    return required_images_path


def _resolve_coordinate_test_version(repo_path: str, coordinates: str) -> str | None:
    """Resolve the test-version used by pullAllowedDockerImages for a coordinate."""
    group, artifact, version = parse_coordinate_parts(coordinates)
    return resolve_test_version(repo_path, group, artifact, version)


def _is_required_docker_image_line(line: str) -> bool:
    stripped = line.strip()
    return bool(stripped and not stripped.startswith("#"))


def _docker_image_slug(image: str) -> str:
    """Derive the allow-list slug: repository path without tag, `/` -> `_`."""
    if ":" in image.rsplit("/", 1)[-1]:
        image = image.rsplit(":", 1)[0]
    return image.replace("/", "_")


def _repair_missing_allowed_images(repo_path: str, coordinates: str) -> list[str]:
    """Pin allow-list Dockerfiles for images declared in `required-docker-images.txt` but not
    yet allow-listed, then commit them. Returns the repaired images (empty when nothing to do).
    """
    required_path = _required_docker_images_path(repo_path, coordinates)
    if required_path is None or not os.path.isfile(required_path):
        return []
    with open(required_path, "r", encoding="utf-8") as required_file:
        required = [line.strip() for line in required_file if _is_required_docker_image_line(line)]
    images_dir = os.path.join(repo_path, "tests", "tck-build-logic", "src", "main", "resources", "allowed-docker-images")
    if not os.path.isdir(images_dir):
        return []
    allowed: set[str] = set()
    for name in os.listdir(images_dir):
        path = os.path.join(images_dir, name)
        if not os.path.isfile(path):
            continue
        with open(path, "r", encoding="utf-8") as image_file:
            allowed.update(line[len("FROM"):].strip() for line in image_file if line.startswith("FROM"))
    repaired: list[str] = []
    for image in required:
        if image in allowed:
            continue
        target = os.path.join(images_dir, f"Dockerfile-{_docker_image_slug(image)}")
        if os.path.isfile(target):
            continue
        with open(target, "w", encoding="utf-8") as image_file:
            image_file.write(f"FROM {image}\n")
        repaired.append(image)
    if repaired:
        subprocess.run(["git", "add", images_dir], cwd=repo_path, check=False)
        subprocess.run(["git", "commit", "-m", "Pin required Docker images to allow-list"], cwd=repo_path, check=False)
    return repaired


def _spring_aot_env(entry: dict) -> dict[str, str]:
    env: dict[str, str] = {}
    java_version = str(entry.get("java") or "").strip()
    graalvm_home = _graalvm_home_for_java_version(java_version)
    if graalvm_home:
        env["GRAALVM_HOME"] = graalvm_home
        env["JAVA_HOME"] = graalvm_home
    return env


def _graalvm_home_for_java_version(java_version: str) -> str | None:
    if not java_version:
        return None
    if java_version == "25":
        return _required_env("GRAALVM_HOME_25_0")
    if java_version == "latest-ea":
        return _required_env("GRAALVM_HOME_LATEST_EA")
    env_name = "GRAALVM_HOME_" + re.sub(r"[^A-Za-z0-9]", "_", java_version).upper()
    return _required_env(env_name)


def _required_env(name: str) -> str:
    value = os.environ.get(name)
    if not value:
        raise RuntimeError(f"Missing required environment variable {name} for local CI verification.")
    return value
