# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Command records and recorded-command execution for local CI verification.

Owns the result dataclasses stored in metrics, the recorded-command runner with
its sudo and Docker-image guards, and the git/output helpers the verification
gates share. The verification orchestration itself lives in
`local_ci_verification.py`.
"""

from __future__ import annotations

import os
import re
import shlex
import subprocess
import tempfile
from dataclasses import asdict, dataclass, field
from pathlib import Path

from utility_scripts.gradle_environment import gradle_command_environment
from utility_scripts.stage_logger import log_stage
from utility_scripts.task_logs import build_timestamped_task_log_path, display_log_path

MAX_OUTPUT_CHARS = 12000
NO_SUDO_FAILURE_MESSAGE = "ERROR: Local CI verification runs must not invoke sudo."
UNEXPECTED_DOCKER_IMAGE_FAILURE_MESSAGE = (
    "ERROR: Local CI verification detected Docker images created during the no-network-equivalent test gate."
)
DOCKER_IMAGE_LIST_COMMAND = ["docker", "image", "ls", "--format", "{{.Repository}}:{{.Tag}} {{.ID}}"]


@dataclass
class CommandRecord:
    """A single local CI command and its outcome.

    The log path is deliberately absent: the record reaches the publication
    descriptor, where a path into the operator's gitignored `logs/` tree resolves
    for no reader. The path is printed for the operator instead.
    §FS-durable-generation-logs
    """

    gate: str
    command: list[str]
    returncode: int
    env: dict[str, str] = field(default_factory=dict)
    output_excerpt: str = ""


@dataclass
class FixupRecord:
    """A verifier fixup attempt.

    Like `CommandRecord`, it reaches the publication descriptor and so carries no
    log path. §FS-durable-generation-logs
    """

    gate: str
    command: list[str]
    commit: str | None
    changed_paths: list[str]


@dataclass(frozen=True)
class RepairAttempt:
    """Outcome of one bounded centralized analysis-agent repair."""

    command: list[str]
    commit: str | None
    changed_paths: list[str]
    log_path: str | None
    failure_reason: str | None = None


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
    """Raised when local CI verification cannot be satisfied."""

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


def _log_local_ci(message: str, indent_level: int = 0) -> None:
    log_stage("local-ci", message, indent_level=indent_level)


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
    _remove_gradle_java_home_overrides(command_env)
    command_env = gradle_command_environment(repo_path, command_env)
    log_path = build_timestamped_task_log_path("local-ci", gate, Path(command[0]).name)
    _log_local_ci(f"Running gate {gate}: {shlex.join(command)}", indent_level=1)
    _log_local_ci(f"Log: {display_log_path(log_path)}", indent_level=2)
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
            output_excerpt=output,
        )
        result.commands.append(record)
        _log_local_ci(f"Gate {gate} failed before execution: sudo is not allowed", indent_level=1)
        return record

    with open(log_path, "w+", encoding="utf-8") as log_file:
        completed = subprocess.run(
            command,
            cwd=repo_path,
            env=command_env,
            stdin=subprocess.DEVNULL,
            stdout=log_file,
            stderr=subprocess.STDOUT,
            text=True,
            check=False,
        )
        log_file.flush()
        log_file.seek(0)
        output = log_file.read()
        if not output and completed.stdout:
            output = completed.stdout
            log_file.write(output)
    returncode = completed.returncode
    if returncode == 0 and failure_output_pattern and re.search(failure_output_pattern, output, re.MULTILINE):
        returncode = 1
    record = CommandRecord(
        gate=gate,
        command=command,
        returncode=returncode,
        env=display_env,
        output_excerpt=_tail(output),
    )
    result.commands.append(record)
    displayed_log_path = display_log_path(log_path)
    if returncode != 0:
        _log_local_ci(
            f"Gate {gate} failed with exit code {returncode}; log: {displayed_log_path}",
            indent_level=1,
        )
        return record
    _log_local_ci(f"Gate {gate} passed; log: {displayed_log_path}", indent_level=1)
    return None


def _remove_gradle_java_home_overrides(command_env: dict[str, str]) -> None:
    """Let the selected `JAVA_HOME` drive Gradle instead of inherited local overrides."""
    for env_name in ("GRADLE_OPTS", "JAVA_OPTS"):
        value = command_env.get(env_name)
        if not value:
            continue
        try:
            tokens = shlex.split(value)
        except ValueError:
            continue
        filtered_tokens = [token for token in tokens if not token.startswith("-Dorg.gradle.java.home=")]
        if filtered_tokens:
            command_env[env_name] = shlex.join(filtered_tokens)
        else:
            command_env.pop(env_name, None)


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
        output_excerpt=_tail(output),
    )
    result.commands.append(record)
    _log_local_ci(f"Gate {gate} failed; log: {display_log_path(log_path)}", indent_level=1)
    return record
