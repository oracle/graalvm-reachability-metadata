# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Local reproduction of the CI gates that run on generated Forge PRs."""

from __future__ import annotations

import json
import os
import re
import subprocess
import tempfile
from dataclasses import asdict, dataclass, field
from pathlib import Path

from utility_scripts.metrics_writer import PENDING_METRICS_FILENAME, read_pending_metrics, write_pending_metrics
from utility_scripts.task_logs import build_timestamped_task_log_path, display_log_path

LOCAL_CI_VERIFICATION_KEY = "local_ci_verification"
HUMAN_INTERVENTION_LABEL = "human-intervention"
MAX_OUTPUT_CHARS = 12000
MAX_FIXUP_ATTEMPTS = 2
CODEX_MODEL_NAME = "oca/gpt-5.4"
CODEX_TIMEOUT_SECONDS = 1800
DEFAULT_BASE_BRANCH = "master"
SPRING_AOT_BRANCH = "main"
SPRING_AOT_REPO_URL = "https://github.com/spring-projects/spring-aot-smoke-tests.git"
NO_SUDO_FAILURE_MESSAGE = "ERROR: Local CI verification runs must not invoke sudo."
UNEXPECTED_DOCKER_IMAGE_FAILURE_MESSAGE = (
    "ERROR: Local CI verification detected Docker images created during the no-network-equivalent test gate."
)
DOCKER_IMAGE_LIST_COMMAND = ["docker", "image", "ls", "--format", "{{.Repository}}:{{.Tag}} {{.ID}}"]


@dataclass
class CommandRecord:
    """A single local CI command and its outcome."""

    gate: str
    command: list[str]
    returncode: int
    env: dict[str, str] = field(default_factory=dict)
    log_path: str | None = None
    output_excerpt: str = ""


@dataclass
class FixupRecord:
    """A verifier fixup attempt."""

    gate: str
    command: list[str]
    commit: str | None
    changed_paths: list[str]
    log_path: str | None = None


@dataclass
class LocalCIVerificationResult:
    """Final verifier result stored in metrics and reflected in PR metadata."""

    status: str
    base_commit: str
    final_commit: str | None = None
    commands: list[CommandRecord] = field(default_factory=list)
    fixups: list[FixupRecord] = field(default_factory=list)
    repo_fix_paths: list[str] = field(default_factory=list)
    human_intervention_required: bool = False
    failure_gate: str | None = None
    failure_command: list[str] | None = None

    def to_metrics(self) -> dict:
        """Return a JSON-serializable metrics block."""
        return asdict(self)


class LocalCIVerificationError(RuntimeError):
    """Raised when local CI-equivalent verification cannot be satisfied."""

    def __init__(self, result: LocalCIVerificationResult):
        self.result = result
        gate = result.failure_gate or "unknown"
        super().__init__(f"Local CI verification failed at gate '{gate}'")


class _GradleOutputFailure(RuntimeError):
    """Internal control flow for Gradle output-producing tasks."""

    def __init__(self, record: CommandRecord):
        self.record = record
        super().__init__(f"Gradle output task failed at gate '{record.gate}'")


def parse_coordinate_parts(coordinates: str) -> tuple[str, str, str]:
    """Return group, artifact, version from Maven coordinates."""
    parts = coordinates.split(":")
    if len(parts) != 3:
        raise ValueError(f"Expected Maven coordinates group:artifact:version, got {coordinates!r}")
    return parts[0], parts[1], parts[2]


def run_local_ci_verification(
        *,
        repo_path: str,
        coordinates: str,
        base_commit: str,
        metrics_repo_path: str | None = None,
        max_fixup_attempts: int = MAX_FIXUP_ATTEMPTS,
) -> LocalCIVerificationResult:
    """Run CI-equivalent local verification, fixing and retrying when possible."""
    result = LocalCIVerificationResult(status="running", base_commit=base_commit)
    for attempt in range(max_fixup_attempts + 1):
        failed_command = _run_verification_once(repo_path, base_commit, result)
        if failed_command is None:
            result.status = "success"
            result.final_commit = _git_stdout(repo_path, ["rev-parse", "HEAD"])
            result.repo_fix_paths = classify_repo_fix_paths(repo_path, base_commit, coordinates)
            result.human_intervention_required = bool(result.repo_fix_paths)
            _write_verification_metrics(metrics_repo_path, result)
            return result

        result.failure_gate = failed_command.gate
        result.failure_command = failed_command.command
        if attempt >= max_fixup_attempts:
            result.status = "failure"
            result.final_commit = _git_stdout(repo_path, ["rev-parse", "HEAD"])
            _write_verification_metrics(metrics_repo_path, result)
            raise LocalCIVerificationError(result)

        fixup = _run_fixup(repo_path, coordinates, failed_command)
        result.fixups.append(fixup)
        if fixup.commit is None:
            result.status = "failure"
            result.final_commit = _git_stdout(repo_path, ["rev-parse", "HEAD"])
            _write_verification_metrics(metrics_repo_path, result)
            raise LocalCIVerificationError(result)

    result.status = "failure"
    _write_verification_metrics(metrics_repo_path, result)
    raise LocalCIVerificationError(result)


def fetch_pr_base_ref(repo_path: str, repo: str, base_branch: str = DEFAULT_BASE_BRANCH) -> str:
    """Fetch the upstream PR base and return a local ref suitable for diffing and rebasing."""
    target_repo = repo.lower()
    for remote_name in ("upstream", "origin"):
        remote_url = subprocess.run(
            ["git", "remote", "get-url", remote_name],
            cwd=repo_path,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            text=True,
            check=False,
        )
        remote_repo = _github_repo_slug_from_url(remote_url.stdout.strip()) if remote_url.returncode == 0 else None
        if remote_repo == target_repo:
            subprocess.run(["git", "fetch", remote_name, base_branch], cwd=repo_path, check=True)
            return f"{remote_name}/{base_branch}"

    subprocess.run(["git", "fetch", f"https://github.com/{target_repo}.git", base_branch], cwd=repo_path, check=True)
    return "FETCH_HEAD"


def _github_repo_slug_from_url(remote_url: str) -> str | None:
    """Return the lowercase GitHub owner/repo slug from common remote URL forms."""
    patterns = (
        r"^https://github\.com/(?P<slug>[^/]+/[^/]+?)(?:\.git)?/?$",
        r"^git@github\.com:(?P<slug>[^/]+/[^/]+?)(?:\.git)?$",
        r"^ssh://git@github\.com/(?P<slug>[^/]+/[^/]+?)(?:\.git)?/?$",
    )
    for pattern in patterns:
        match = re.match(pattern, remote_url)
        if match:
            return match.group("slug").lower()
    return None


def format_local_ci_verification_pr_section(local_ci_verification: dict | None) -> str:
    """Format a concise PR body section for local CI verification."""
    if not isinstance(local_ci_verification, dict):
        return ""
    status = local_ci_verification.get("status", "unknown")
    commands = local_ci_verification.get("commands")
    command_count = len(commands) if isinstance(commands, list) else 0
    fixups = local_ci_verification.get("fixups")
    fixup_count = len(fixups) if isinstance(fixups, list) else 0
    repo_fix_paths = local_ci_verification.get("repo_fix_paths")
    repo_fix_list = repo_fix_paths if isinstance(repo_fix_paths, list) else []
    body = (
        "\n### Local CI Verification\n\n"
        f"- Status: `{status}`\n"
        f"- Commands run: {command_count}\n"
        f"- Fixup attempts: {fixup_count}\n"
    )
    if local_ci_verification.get("human_intervention_required"):
        body += "- Human intervention: required because repository-level files changed during verification.\n"
    if repo_fix_list:
        body += "- Repository-level fix paths:\n"
        body += "".join(f"  - `{path}`\n" for path in repo_fix_list[:20])
    return body


def local_ci_requires_human_intervention(local_ci_verification: dict | None) -> bool:
    """Return True if verification metrics require the PR label."""
    return bool(isinstance(local_ci_verification, dict) and local_ci_verification.get("human_intervention_required"))


def classify_repo_fix_paths(repo_path: str, base_commit: str, coordinates: str) -> list[str]:
    """Return changed tracked paths outside the expected target-library output."""
    group, artifact, _ = parse_coordinate_parts(coordinates)
    allowed_prefixes = (
        f"metadata/{group}/{artifact}/",
        f"tests/src/{group}/{artifact}/",
        f"stats/{group}/{artifact}/",
    )
    repo_fix_paths: list[str] = []
    for path in _changed_files(repo_path, base_commit, "HEAD", diff_filter="ACMRTD"):
        if path.startswith(allowed_prefixes):
            continue
        repo_fix_paths.append(path)
    return sorted(repo_fix_paths)


def changed_files_for_ci(repo_path: str, base_commit: str, head_commit: str = "HEAD") -> list[str]:
    """Return changed files using the pull-request CI diff filter."""
    return _changed_files(repo_path, base_commit, head_commit, diff_filter="ACMRT")


def _run_verification_once(
        repo_path: str,
        base_commit: str,
        result: LocalCIVerificationResult,
) -> CommandRecord | None:
    changed_files = changed_files_for_ci(repo_path, base_commit)

    try:
        changed_metadata_matrix = _gradle_json_output(
            repo_path,
            "generateChangedMetadataTestMatrix",
            base_commit,
            result,
            "changed-metadata-matrix",
        )
        changed_tested_versions_matrix = _gradle_json_output(
            repo_path,
            "generateChangedTestedVersionsMatrix",
            base_commit,
            result,
            "changed-tested-versions-matrix",
        )
        changed_infrastructure_matrix = {}
        if _should_run_infrastructure_tests(changed_files):
            changed_infrastructure_matrix = _gradle_json_output(
                repo_path,
                "generateInfrastructureChangedCoordinatesMatrix",
                base_commit,
                result,
                "changed-infrastructure-matrix",
            )
    except _GradleOutputFailure as exc:
        return exc.record

    test_entries = _merge_test_matrix_entries(
        _matrix_entries(changed_metadata_matrix),
        _matrix_entries(changed_tested_versions_matrix),
    )
    failed = _run_test_matrix_entries(repo_path, test_entries, result)
    if failed is not None:
        return failed

    failed = _run_infrastructure_matrix_entries(repo_path, _matrix_entries(changed_infrastructure_matrix), result)
    if failed is not None:
        return failed

    failed = _run_spring_aot_verification(repo_path, base_commit, changed_files, result)
    if failed is not None:
        return failed

    failed = _run_index_validation(repo_path, base_commit, changed_files, result)
    if failed is not None:
        return failed

    failed = _run_style_validation(repo_path, base_commit, changed_files, result)
    if failed is not None:
        return failed

    if _should_run_library_stats_validation(changed_files):
        failed = _run_recorded_command(repo_path, "library-stats-validation", ["./gradlew", "validateLibraryStats"], result)
        if failed is not None:
            return failed

    failed = _run_recorded_command(repo_path, "doc-links", ["./gradlew", "checkDocLinks", "--console=plain"], result)
    if failed is not None:
        return failed

    if _should_run_docker_scan(changed_files):
        failed = _run_recorded_command(
            repo_path,
            "docker-image-scan",
            ["./gradlew", "checkAllowedDockerImages", f"--baseCommit={base_commit}", "--newCommit=HEAD"],
            result,
        )
        if failed is not None:
            return failed

    return None


def _run_test_matrix_entries(
        repo_path: str,
        entries: list[dict],
        result: LocalCIVerificationResult,
) -> CommandRecord | None:
    if not entries:
        return None

    pulled_coordinates: set[str] = set()
    for entry in entries:
        coordinates = str(entry.get("coordinates") or "").strip()
        if not coordinates or coordinates in pulled_coordinates:
            continue
        if not _coordinate_uses_docker(repo_path, coordinates):
            continue
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


def _run_infrastructure_matrix_entries(
        repo_path: str,
        entries: list[dict],
        result: LocalCIVerificationResult,
) -> CommandRecord | None:
    for entry in entries:
        coordinates = str(entry.get("coordinates") or "").strip()
        if not coordinates:
            continue
        env = _matrix_env(entry)
        if _coordinate_uses_docker(repo_path, coordinates):
            failed = _run_recorded_command(
                repo_path,
                "infrastructure-pull-allowed-docker-images",
                ["./gradlew", "pullAllowedDockerImages", f"-Pcoordinates={coordinates}"],
                result,
                env=env,
            )
            if failed is not None:
                return failed
        failed = _run_recorded_command(
            repo_path,
            "infrastructure-check-metadata-files",
            ["./gradlew", "checkMetadataFiles", f"-Pcoordinates={coordinates}"],
            result,
            env=env,
        )
        if failed is not None:
            return failed
        failed = _run_recorded_command(
            repo_path,
            "test-infra",
            ["./gradlew", "testInfra", f"-Pcoordinates={coordinates}", "-Pparallelism=1", "--stacktrace"],
            result,
            env=env,
        )
        if failed is not None:
            return failed
    return None


def _run_spring_aot_verification(
        repo_path: str,
        base_commit: str,
        changed_files: list[str],
        result: LocalCIVerificationResult,
) -> CommandRecord | None:
    if not _should_run_spring_aot_tests(changed_files):
        return None

    with tempfile.TemporaryDirectory(prefix="forge-spring-aot-") as spring_parent:
        os.symlink(os.path.join(repo_path, "metadata"), os.path.join(spring_parent, "metadata"))
        spring_path = os.path.join(spring_parent, "spring-aot-smoke-tests")
        failed = _run_recorded_command(
            repo_path,
            "checkout-spring-aot-smoke-tests",
            ["git", "clone", "--depth", "1", "--branch", SPRING_AOT_BRANCH, SPRING_AOT_REPO_URL, spring_path],
            result,
        )
        if failed is not None:
            return failed

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


def _run_spring_aot_matrix_entries(
        repo_path: str,
        spring_path: str,
        entries: list[dict],
        result: LocalCIVerificationResult,
) -> CommandRecord | None:
    for entry in entries:
        project = str(entry.get("project") or "").strip()
        if not project:
            continue
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


def _run_index_validation(
        repo_path: str,
        base_commit: str,
        changed_files: list[str],
        result: LocalCIVerificationResult,
) -> CommandRecord | None:
    if not any(_is_metadata_index_file(path) for path in changed_files):
        return None
    try:
        output = _gradle_output(
            repo_path,
            "generateChangedIndexFileCoordinatesList",
            base_commit,
            result,
            "changed-index-coordinates",
        )
    except _GradleOutputFailure as exc:
        return exc.record
    changed_coordinates = str(output.get("changed-coordinates") or "").strip()
    if not changed_coordinates:
        return None
    return _run_recorded_command(
        repo_path,
        "index-file-validation",
        ["./gradlew", "validateIndexFiles", f"-Pcoordinates={changed_coordinates}"],
        result,
    )


def _run_style_validation(
        repo_path: str,
        base_commit: str,
        changed_files: list[str],
        result: LocalCIVerificationResult,
) -> CommandRecord | None:
    if not _should_run_style_validation(changed_files):
        return None
    failed = _run_recorded_command(repo_path, "spotless", ["./gradlew", "spotlessCheck", "--console=plain"], result)
    if failed is not None:
        return failed
    try:
        matrix = _gradle_json_output(
            repo_path,
            "generateChangedCoordinatesOnlyMatrix",
            base_commit,
            result,
            "changed-coordinates-only-matrix",
        )
    except _GradleOutputFailure as exc:
        return exc.record
    coordinates = matrix.get("coordinates")
    if not isinstance(coordinates, list):
        return None
    for coordinate in coordinates:
        failed = _run_recorded_command(
            repo_path,
            "checkstyle",
            ["./gradlew", "checkstyle", f"-Pcoordinates={coordinate}", "--console=plain"],
            result,
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


def _gradle_output(
        repo_path: str,
        task_name: str,
        base_commit: str,
        result: LocalCIVerificationResult,
        gate: str,
        extra_args: list[str] | None = None,
) -> dict[str, str]:
    with tempfile.NamedTemporaryFile("w+", encoding="utf-8", delete=False) as output_file:
        output_path = output_file.name
    try:
        failed = _run_recorded_command(
            repo_path,
            gate,
            ["./gradlew", task_name, f"-PbaseCommit={base_commit}", "-PnewCommit=HEAD", *(extra_args or [])],
            result,
            env={"GITHUB_OUTPUT": output_path},
        )
        if failed is not None:
            raise _GradleOutputFailure(failed)
        return _parse_github_output_file(output_path)
    finally:
        try:
            os.remove(output_path)
        except OSError:
            pass


def _parse_github_output_file(path: str) -> dict[str, str]:
    output: dict[str, str] = {}
    with open(path, "r", encoding="utf-8") as file:
        for line in file:
            stripped = line.rstrip("\n")
            if not stripped or "=" not in stripped:
                continue
            key, value = stripped.split("=", 1)
            output[key] = value
    return output


def _matrix_entries(matrix: dict) -> list[dict]:
    include = matrix.get("include")
    if isinstance(include, list):
        return [entry for entry in include if isinstance(entry, dict)]
    return []


def _merge_test_matrix_entries(changed_metadata_entries: list[dict], changed_tested_version_entries: list[dict]) -> list[dict]:
    """Preserve changed-metadata batches and added tested-version entries without exact duplicates."""
    return _deduplicate_test_matrix_entries(changed_metadata_entries + changed_tested_version_entries)


def _deduplicate_test_matrix_entries(entries: list[dict]) -> list[dict]:
    seen: set[tuple[str, tuple[str, ...], str, str, str]] = set()
    deduplicated: list[dict] = []
    for entry in entries:
        versions = entry.get("versions")
        version_tuple = tuple(str(version) for version in versions) if isinstance(versions, list) else ()
        key = (
            str(entry.get("coordinates") or ""),
            version_tuple,
            str(entry.get("version") or ""),
            str(entry.get("os") or ""),
            str(entry.get("nativeImageMode") or ""),
        )
        if key in seen:
            continue
        seen.add(key)
        deduplicated.append(entry)
    return deduplicated


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
    index_path = os.path.join(repo_path, "metadata", group, artifact, "index.json")
    if not os.path.isfile(index_path):
        return version

    with open(index_path, "r", encoding="utf-8") as index_file:
        index_entries = json.load(index_file)
    if not isinstance(index_entries, list):
        return version

    for entry in index_entries:
        if not isinstance(entry, dict):
            continue
        tested_versions = entry.get("tested-versions")
        if isinstance(tested_versions, list) and version in [str(tested_version) for tested_version in tested_versions]:
            return str(entry.get("test-version") or entry.get("metadata-version") or version)

    for entry in index_entries:
        if not isinstance(entry, dict):
            continue
        if version == str(entry.get("metadata-version") or ""):
            return str(entry.get("test-version") or entry.get("metadata-version") or version)

    return version


def _is_required_docker_image_line(line: str) -> bool:
    stripped = line.strip()
    return bool(stripped and not stripped.startswith("#"))


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


def _run_recorded_command(
        repo_path: str,
        gate: str,
        command: list[str],
        result: LocalCIVerificationResult,
        env: dict[str, str] | None = None,
        failure_output_pattern: str | None = None,
) -> CommandRecord | None:
    command_env = dict(os.environ)
    display_env = dict(env or {})
    command_env.update(display_env)
    log_path = build_timestamped_task_log_path("local-ci", gate, Path(command[0]).name)
    sudo_reason = _sudo_usage_reason(repo_path, command)
    if sudo_reason:
        output = f"{NO_SUDO_FAILURE_MESSAGE} {sudo_reason}\n"
        with open(log_path, "w", encoding="utf-8") as log_file:
            log_file.write(output)
        record = CommandRecord(
            gate=gate,
            command=command,
            returncode=1,
            env=display_env,
            log_path=display_log_path(log_path),
            output_excerpt=output,
        )
        result.commands.append(record)
        return record

    completed = subprocess.run(
        command,
        cwd=repo_path,
        env=command_env,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=False,
    )
    output = completed.stdout or ""
    with open(log_path, "w", encoding="utf-8") as log_file:
        log_file.write(output)
    returncode = completed.returncode
    if returncode == 0 and failure_output_pattern and re.search(failure_output_pattern, output, re.MULTILINE):
        returncode = 1
    record = CommandRecord(
        gate=gate,
        command=command,
        returncode=returncode,
        env=display_env,
        log_path=display_log_path(log_path),
        output_excerpt=_tail(output),
    )
    result.commands.append(record)
    if returncode != 0:
        return record
    return None


def _run_recorded_command_without_new_docker_images(
        repo_path: str,
        gate: str,
        command: list[str],
        result: LocalCIVerificationResult,
        env: dict[str, str] | None = None,
        failure_output_pattern: str | None = None,
) -> CommandRecord | None:
    try:
        before_images = _docker_image_ids(repo_path)
    except RuntimeError as exc:
        output = f"ERROR: Cannot run local CI no-network-equivalent Docker validation. {exc}\n"
        return _record_synthetic_failure(repo_path, "docker-image-baseline", command, result, env, output)

    failed = _run_recorded_command(
        repo_path,
        gate,
        command,
        result,
        env=env,
        failure_output_pattern=failure_output_pattern,
    )
    if failed is not None:
        return failed

    try:
        after_images = _docker_image_ids(repo_path)
    except RuntimeError as exc:
        output = f"ERROR: Cannot complete local CI no-network-equivalent Docker validation. {exc}\n"
        return _record_synthetic_failure(repo_path, "docker-image-after-test", command, result, env, output)

    new_images = sorted(after_images - before_images)
    if not new_images:
        return None

    output = "\n".join([
        UNEXPECTED_DOCKER_IMAGE_FAILURE_MESSAGE,
        "CI disables Docker networking after `pullAllowedDockerImages`; local verification must not pass by pulling images on demand.",
        "New Docker images:",
        *[f"- {image}" for image in new_images],
        "",
    ])
    return _record_synthetic_failure(repo_path, "unexpected-docker-image-pull", command, result, env, output)


def _docker_image_ids(repo_path: str) -> set[str]:
    try:
        completed = subprocess.run(
            DOCKER_IMAGE_LIST_COMMAND,
            cwd=repo_path,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            check=False,
        )
    except OSError as exc:
        raise RuntimeError(str(exc)) from exc
    if completed.returncode != 0:
        output = (completed.stdout or "").strip()
        details = f" Output: {output}" if output else ""
        raise RuntimeError(f"Cannot list Docker images for local CI no-network-equivalent verification.{details}")
    images: set[str] = set()
    for line in completed.stdout.splitlines():
        stripped = line.strip()
        if stripped and not stripped.startswith("<none>:<none>"):
            images.add(stripped)
    return images


def _record_synthetic_failure(
        repo_path: str,
        gate: str,
        command: list[str],
        result: LocalCIVerificationResult,
        env: dict[str, str] | None,
        output: str,
) -> CommandRecord:
    log_path = build_timestamped_task_log_path("local-ci", gate, Path(command[0]).name)
    with open(log_path, "w", encoding="utf-8") as log_file:
        log_file.write(output)
    record = CommandRecord(
        gate=gate,
        command=command,
        returncode=1,
        env=dict(env or {}),
        log_path=display_log_path(log_path),
        output_excerpt=_tail(output),
    )
    result.commands.append(record)
    return record


def _sudo_usage_reason(repo_path: str, command: list[str]) -> str | None:
    """Return why a local command would require sudo, or None if it is allowed."""
    if _contains_sudo_token(command):
        return "The command line contains `sudo`."

    script_path = _shell_script_path(repo_path, command)
    if script_path is None or not os.path.isfile(script_path):
        return None
    sudo_line = _script_sudo_line(script_path)
    if sudo_line is None:
        return None
    return f"The script `{os.path.relpath(script_path, repo_path)}` invokes `sudo`: {sudo_line}"


def _contains_sudo_token(values: list[str]) -> bool:
    return any(re.search(r"(?<![\w.-])sudo(?![\w.-])", value) for value in values)


def _shell_script_path(repo_path: str, command: list[str]) -> str | None:
    if not command:
        return None
    executable = os.path.basename(command[0])
    if executable in {"bash", "sh"}:
        for arg in command[1:]:
            if arg == "-c":
                return None
            if arg.startswith("-"):
                continue
            return _resolve_command_path(repo_path, arg)
        return None
    if command[0].endswith(".sh") or _is_repo_local_path(command[0]):
        return _resolve_command_path(repo_path, command[0])
    return None


def _is_repo_local_path(path: str) -> bool:
    return not os.path.isabs(path) and (path.startswith("./") or path.startswith("../") or os.sep in path)


def _resolve_command_path(repo_path: str, path: str) -> str:
    if os.path.isabs(path):
        return path
    return os.path.normpath(os.path.join(repo_path, path))


def _script_sudo_line(script_path: str) -> str | None:
    with open(script_path, "r", encoding="utf-8", errors="ignore") as file:
        for line in file:
            stripped = line.strip()
            if not stripped or stripped.startswith("#"):
                continue
            if re.search(r"(?<![\w.-])sudo(?![\w.-])", stripped):
                return stripped
    return None


def _run_fixup(repo_path: str, coordinates: str, failed_command: CommandRecord) -> FixupRecord:
    before_paths = _worktree_changed_paths(repo_path)
    log_path = build_timestamped_task_log_path("local-ci-fixup", coordinates, failed_command.gate)
    prompt = _build_fixup_prompt(coordinates, failed_command)
    command = [
        "codex",
        "exec",
        "--dangerously-bypass-approvals-and-sandbox",
        "--json",
        "-c",
        'reasoning.effort="high"',
        "-m",
        CODEX_MODEL_NAME,
        prompt,
    ]
    try:
        with open(log_path, "w", encoding="utf-8") as log_file:
            completed = subprocess.run(
                command,
                cwd=repo_path,
                stdout=log_file,
                stderr=subprocess.STDOUT,
                timeout=CODEX_TIMEOUT_SECONDS,
                check=False,
            )
    except subprocess.TimeoutExpired:
        return FixupRecord(
            gate=failed_command.gate,
            command=command,
            commit=None,
            changed_paths=[],
            log_path=display_log_path(log_path),
        )

    if completed.returncode != 0:
        return FixupRecord(
            gate=failed_command.gate,
            command=command,
            commit=None,
            changed_paths=[],
            log_path=display_log_path(log_path),
        )

    after_paths = _worktree_changed_paths(repo_path)
    changed_paths = sorted(after_paths - before_paths)
    if not changed_paths:
        return FixupRecord(
            gate=failed_command.gate,
            command=command,
            commit=None,
            changed_paths=[],
            log_path=display_log_path(log_path),
        )

    subprocess.run(["git", "add", "-A", "--", *changed_paths], cwd=repo_path, check=True)
    staged = subprocess.run(["git", "diff", "--cached", "--quiet"], cwd=repo_path, check=False)
    if staged.returncode == 0:
        return FixupRecord(
            gate=failed_command.gate,
            command=command,
            commit=None,
            changed_paths=changed_paths,
            log_path=display_log_path(log_path),
        )
    subprocess.run(["git", "commit", "-m", "Apply local CI verification fixes"], cwd=repo_path, check=True)
    commit = _git_stdout(repo_path, ["rev-parse", "HEAD"])
    return FixupRecord(
        gate=failed_command.gate,
        command=command,
        commit=commit,
        changed_paths=changed_paths,
        log_path=display_log_path(log_path),
    )


def _build_fixup_prompt(coordinates: str, failed_command: CommandRecord) -> str:
    env_lines = [f"{key}={value}" for key, value in sorted(failed_command.env.items())]
    reproduction = " ".join(failed_command.command)
    if env_lines:
        reproduction = " ".join(env_lines + [reproduction])
    return "\n".join([
        "Fix the repository so the local CI-equivalent verification gate passes.",
        "The fix may update generated library files or shared repository files when the root cause is in the repository.",
        "Keep the change minimal and targeted to the failing gate.",
        "",
        f"Library: {coordinates}",
        f"Failing gate: {failed_command.gate}",
        "Reproduce with:",
        reproduction,
        "",
        "Failure output excerpt:",
        "```text",
        failed_command.output_excerpt,
        "```",
    ])


def _write_verification_metrics(metrics_repo_path: str | None, result: LocalCIVerificationResult) -> None:
    if metrics_repo_path is None:
        return
    pending_path = os.path.join(metrics_repo_path, PENDING_METRICS_FILENAME)
    if not os.path.isfile(pending_path):
        return
    metrics = read_pending_metrics(metrics_repo_path)
    metrics[LOCAL_CI_VERIFICATION_KEY] = result.to_metrics()
    write_pending_metrics(metrics_repo_path, metrics)


def _worktree_changed_paths(repo_path: str) -> set[str]:
    completed = subprocess.run(
        ["git", "status", "--porcelain"],
        cwd=repo_path,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=True,
    )
    paths: set[str] = set()
    for line in completed.stdout.splitlines():
        if not line:
            continue
        path = line[3:]
        if " -> " in path:
            path = path.split(" -> ", 1)[1]
        paths.add(path)
    return paths


def _changed_files(repo_path: str, base_commit: str, head_commit: str, diff_filter: str) -> list[str]:
    completed = subprocess.run(
        ["git", "diff", "--name-only", f"--diff-filter={diff_filter}", base_commit, head_commit],
        cwd=repo_path,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=True,
    )
    return [line.strip() for line in completed.stdout.splitlines() if line.strip()]


def _git_stdout(repo_path: str, args: list[str]) -> str:
    completed = subprocess.run(
        ["git", *args],
        cwd=repo_path,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=True,
    )
    return completed.stdout.strip()


def _tail(output: str) -> str:
    if len(output) <= MAX_OUTPUT_CHARS:
        return output
    return output[-MAX_OUTPUT_CHARS:]


def _is_metadata_index_file(path: str) -> bool:
    parts = path.split("/")
    return len(parts) == 4 and parts[0] == "metadata" and parts[3] == "index.json"


def _should_run_style_validation(changed_files: list[str]) -> bool:
    for path in changed_files:
        if path.startswith("docs/"):
            continue
        if path.endswith(".md"):
            continue
        if os.path.basename(path).startswith("library-and-framework-list") and path.endswith(".json"):
            continue
        return True
    return False


def _should_run_library_stats_validation(changed_files: list[str]) -> bool:
    for path in changed_files:
        if re.match(r"^stats/[^/]+/[^/]+/[^/]+/stats\.json$", path):
            return True
        if path.startswith("stats/schemas/") and os.path.basename(path).startswith("library-stats-schema"):
            return True
        if re.match(r"^metadata/[^/]+/[^/]+/[^/]+/", path):
            return True
    return False


def _should_run_infrastructure_tests(changed_files: list[str]) -> bool:
    return any(
        path == "build.gradle"
        or path == "settings.gradle"
        or path == "gradle.properties"
        or path.startswith("gradle/")
        or path.startswith("tests/tck-build-logic/")
        for path in changed_files
    )


def _should_run_spring_aot_tests(changed_files: list[str]) -> bool:
    return any(path.startswith("metadata/") for path in changed_files)


def _should_run_docker_scan(changed_files: list[str]) -> bool:
    return any(path.startswith("tests/tck-build-logic/src/main/resources/allowed-docker-images/") for path in changed_files)
