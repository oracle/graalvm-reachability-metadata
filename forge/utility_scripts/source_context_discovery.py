# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Agent-driven artifact-URL and metadata discovery through Gradle.

`populateArtifactURLs` and `discoverArtifactMetadata` resolve the URL fields
that source-context preparation reads from `index.json`
(§AR-dynamic-access-workflow); this module builds the agent command Gradle
runs for them and separates shared Gradle bootstrap outages from per-library
failures (§FS-human-intervention-policy).
"""

import json
import os
import shlex
import subprocess
import sys
import time

from ai_workflows.agents.opencode_agent import opencode_config
from ai_workflows.agents.agent_runtime import (
    CODEX_BYPASS_APPROVALS_AND_SANDBOX_FLAG,
    DEFAULT_AGENT_PROVIDER,
    agent_process_environment,
    get_setup_agent,
)
from utility_scripts.gradle_environment import gradle_command_environment
from utility_scripts.repo_path_resolver import require_complete_reachability_repo
from utility_scripts.stage_logger import log_detail
from utility_scripts.task_logs import build_task_log_path, display_log_path

GRADLE_BOOTSTRAP_RETRY_DELAY_SECONDS = 5
PI_URL_FETCH_EXTENSION_PATH = os.path.abspath(os.path.join(
    os.path.dirname(__file__),
    "..",
    "ai_workflows",
    "agents",
    "pi_url_fetch_extension.ts",
))
# A wrapper failure is only ever reported alongside the distribution URL it was
# fetching, so that URL is the narrowing guard and the markers below only have to
# separate a failed fetch from a successful one.
GRADLE_DISTRIBUTION_URL_MARKER = "services.gradle.org/distributions/gradle-"
GRADLE_WRAPPER_DOWNLOAD_FAILURE_MARKERS = (
    "org.gradle.wrapper.install",
    "could not install gradle distribution",
    "sockettimeoutexception",
    "unknownhostexception",
    "read timed out",
    "connect timed out",
    "connection timed out",
    "connection reset",
    "connection refused",
)


class GradleBootstrapFailure(RuntimeError):
    """Raised when shared Gradle bootstrap infrastructure prevents discovery.

    A configuration-time Gradle failure is host-wide, not a property of the
    claimed library, so the control plane releases the claim without
    `human-intervention` and stops the run
    (§FS-human-intervention-policy).
    """

    def __init__(self, coordinate: str, log_path: str):
        self.coordinate = coordinate
        self.log_path = log_path
        super().__init__(
            f"Gradle bootstrap failed for {coordinate}. See {display_log_path(log_path)} for details."
        )


def url_fetch_agent_command() -> str:
    """Build the agent command Gradle runs for URL discovery.

    ``populateArtifactURLs`` and ``discoverArtifactMetadata`` resolve URL fields
    before any generation starts, which is setup work
    (§FS-forge-agent-runtime-selection). Gradle owns the process for these two
    tasks, so the role reaches them as a command string rather than an adapter,
    and the selection is read through `get_setup_agent` instead of being run.
    """
    selection = get_setup_agent()
    model = shlex.quote(selection.model)
    # Each backend spells reasoning effort differently, and OpenCode takes it as
    # configuration rather than a flag. The command carries whatever the role
    # resolved, so both setup steps run at the same effort.
    thinking = shlex.quote(selection.thinking_level) if selection.thinking_level else None
    if selection.backend == "codex":
        executable = selection.agent or "codex"
        return (
            f"{shlex.quote(executable)} exec "
            f"{CODEX_BYPASS_APPROVALS_AND_SANDBOX_FLAG} "
            + (f"-c reasoning.effort={thinking} " if thinking else "")
            + f"-m {model}"
        )
    if selection.backend == "claude-code":
        return (
            f"{shlex.quote(selection.agent or 'claude')} -p --permission-mode dontAsk "
            + (f"--effort {thinking} " if thinking else "")
            + f"--model {model}"
        )
    if selection.backend == "pi":
        # Pi has no built-in web fetch, so this extension is its only network reach.
        return (
            f"{shlex.quote(selection.agent or 'pi')} -p --no-session "
            f"--extension {shlex.quote(PI_URL_FETCH_EXTENSION_PATH)} "
            f"--provider {shlex.quote(selection.provider or DEFAULT_AGENT_PROVIDER)} "
            + (f"--thinking {thinking} " if thinking else "")
            + f"--model {model}"
        )
    qualified_model = (
        selection.model
        if not selection.provider or "/" in selection.model
        else f"{selection.provider}/{selection.model}"
    )
    rendered_config = shlex.quote(json.dumps(
        opencode_config(qualified_model, selection.thinking_level),
        separators=(",", ":"),
    ))
    return (
        f"env OPENCODE_CONFIG_CONTENT={rendered_config} "
        f"{shlex.quote(selection.agent or 'opencode')} run --auto "
        f"--model {shlex.quote(qualified_model)}"
    )


def _effective_agent_command(agent_command: str | None) -> str:
    return agent_command or url_fetch_agent_command()


def populate_artifact_urls(
        reachability_repo_path: str,
        coordinate: str,
        agent_command: str | None = None,
) -> None:
    """Populate artifact URLs in ``index.json`` before source-context download.

    Satisfies the dynamic-access setup precondition that source/test/docs URLs
    be available before the agent receives read-only context
    (§AR-dynamic-access-workflow).
    """
    require_complete_reachability_repo(reachability_repo_path)
    log_path = build_task_log_path("populate-artifact-urls", coordinate, "populate_artifact_urls.log")
    log_path_display = display_log_path(log_path)
    log_detail("populate-artifact-urls", f"Populating artifact URLs for {coordinate}; output: {log_path_display}")
    command = [
        "./gradlew",
        "populateArtifactURLs",
        f"--coordinates={coordinate}",
        f"--agent-command={_effective_agent_command(agent_command)}",
    ]
    result = subprocess.run(
        command,
        cwd=reachability_repo_path,
        env=agent_process_environment(gradle_command_environment(reachability_repo_path)),
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=False,
    )
    with open(log_path, "w", encoding="utf-8") as log_file:
        log_file.write(result.stdout)
    if result.returncode != 0:
        print(
            f"ERROR: populateArtifactURLs failed for {coordinate}. See {log_path_display} for details.",
            file=sys.stderr,
        )
        raise SystemExit(1)
    log_detail("populate-artifact-urls", f"Artifact URLs populated for {coordinate}")


def discover_artifact_metadata(
        reachability_repo_path: str,
        coordinate: str,
        agent_command: str | None = None,
) -> None:
    """Discover artifact metadata, separating bootstrap outages from library failures.

    This is the first Gradle invocation of a new-library run, so a shared
    bootstrap outage surfaces here. It is retried once with a dependency refresh
    and, if still failing, reported as `GradleBootstrapFailure` rather than as
    this library's failure (§FS-human-intervention-policy).
    """
    require_complete_reachability_repo(reachability_repo_path)
    log_path = build_task_log_path("discover-artifact-metadata", coordinate, "discover_artifact_metadata.log")
    log_path_display = display_log_path(log_path)
    log_detail("discover-artifact-metadata", f"Discovering artifact metadata for {coordinate}; output: {log_path_display}")
    effective_agent_command = _effective_agent_command(agent_command)
    command = _discover_artifact_metadata_command(coordinate, effective_agent_command)
    command_env = agent_process_environment(gradle_command_environment(reachability_repo_path))
    result = _run_gradle_discovery_command(command, reachability_repo_path, command_env)
    attempt_logs = [_format_gradle_attempt_log("initial", command, result)]
    _write_gradle_attempt_logs(log_path, attempt_logs)

    if result.returncode != 0 and is_gradle_bootstrap_failure(result.stdout):
        retry_command = _discover_artifact_metadata_command(
            coordinate,
            effective_agent_command,
            diagnostics=True,
        )
        log_detail(
            "discover-artifact-metadata",
            (
                f"Gradle bootstrap failed for {coordinate}; retrying once with "
                "dependency refresh and diagnostic logging"
            ),
        )
        time.sleep(GRADLE_BOOTSTRAP_RETRY_DELAY_SECONDS)
        result = _run_gradle_discovery_command(retry_command, reachability_repo_path, command_env)
        attempt_logs.append(_format_gradle_attempt_log("diagnostic-retry", retry_command, result))
        _write_gradle_attempt_logs(log_path, attempt_logs)

    if result.returncode != 0:
        if is_gradle_bootstrap_failure(result.stdout):
            print(
                f"ERROR: Gradle bootstrap failed for {coordinate}. See {log_path_display} for details.",
                file=sys.stderr,
            )
            raise GradleBootstrapFailure(coordinate, log_path)
        print(
            f"ERROR: discoverArtifactMetadata failed for {coordinate}. See {log_path_display} for details.",
            file=sys.stderr,
        )
        raise SystemExit(1)
    log_detail("discover-artifact-metadata", f"Artifact metadata discovered for {coordinate}")


def _discover_artifact_metadata_command(
        coordinate: str,
        agent_command: str,
        diagnostics: bool = False,
) -> list[str]:
    command = ["./gradlew", "--no-daemon"]
    if diagnostics:
        command.extend(["--stacktrace", "--info", "--refresh-dependencies"])
    command.extend([
        "discoverArtifactMetadata",
        f"--coordinates={coordinate}",
        f"--agent-command={agent_command}",
    ])
    return command


def _run_gradle_discovery_command(
        command: list[str],
        reachability_repo_path: str,
        command_env: dict[str, str],
) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        command,
        cwd=reachability_repo_path,
        env=command_env,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=False,
    )


def _format_gradle_attempt_log(
        attempt_name: str,
        command: list[str],
        result: subprocess.CompletedProcess[str],
) -> str:
    rendered_command = " ".join(shlex.quote(part) for part in command)
    output = result.stdout or ""
    if output and not output.endswith("\n"):
        output += "\n"
    return (
        f"[forge] Gradle discovery attempt: {attempt_name}\n"
        f"[forge] Exit code: {result.returncode}\n"
        f"$ {rendered_command}\n\n"
        f"{output}"
    )


def _write_gradle_attempt_logs(log_path: str, attempt_logs: list[str]) -> None:
    with open(log_path, "w", encoding="utf-8") as log_file:
        log_file.write("\n\n".join(attempt_logs))


def is_gradle_bootstrap_failure(output: str | None) -> bool:
    """Return True when Gradle failed before leaving root-build configuration."""
    return _is_spotless_plugin_bootstrap_failure(output) or _is_gradle_wrapper_download_failure(output)


def _is_spotless_plugin_bootstrap_failure(output: str | None) -> bool:
    if not output:
        return False
    normalized = output.lower()
    return (
        "com.diffplug.spotless" in normalized
        and (
            "could not resolve plugin artifact" in normalized
            or "plugin [id: 'com.diffplug.spotless'" in normalized
        )
        and (
            "was not found" in normalized
            or "gradle central plugin repository" in normalized
            or "plugin repositories" in normalized
        )
    )


def _is_gradle_wrapper_download_failure(output: str | None) -> bool:
    if not output:
        return False
    normalized = output.lower()
    if GRADLE_DISTRIBUTION_URL_MARKER not in normalized:
        return False
    return any(marker in normalized for marker in GRADLE_WRAPPER_DOWNLOAD_FAILURE_MARKERS)
