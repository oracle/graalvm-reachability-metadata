# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Pre-publication verification gate for generated Forge PRs.

Implements the second verification tier of §FS-local-ci-equivalent-verification.2:
the post-rebase, cross-cutting checks a single-library generation cannot settle
on its own. Library-scoped verification (metadata, style, stats, and the native
test lanes) is owned by the generation/finalization tier
(§FS-local-ci-equivalent-verification.1) and is not repeated here by default. The
opt-in `reproduce_full_ci` flag additionally reproduces the expensive per-PR CI
surface locally — the whole changed-metadata native test matrix and the Spring
AOT smoke tests.

Recorded-command execution lives in `local_ci_commands.py` and the full CI
reproduction lanes in `local_ci_reproduction.py`; their entry points are
re-exported here.
"""

from __future__ import annotations

import os
import re
import subprocess

from ai_workflows.agents.agent_runtime import analysis_agent_run, get_analysis_agent
from git_scripts.common_git import run_git_transport
from utility_scripts.local_ci_commands import (
    CommandRecord,
    FixupRecord,
    LocalCIVerificationError,
    LocalCIVerificationResult,
    RepairAttempt,
    parse_coordinate_parts,
    _GradleOutputFailure,
    _changed_files,
    _git_stdout,
    _gradle_output,
    _log_local_ci,
    _run_recorded_command,
    _worktree_changed_paths,
)
from utility_scripts.local_ci_reproduction import (
    _gradle_json_output,
    _matrix_entries,
    _run_spring_aot_verification,
    _run_test_matrix_entries,
    _should_run_spring_aot_tests,
)
from utility_scripts.metrics_writer import PENDING_METRICS_FILENAME, read_pending_metrics, write_pending_metrics
from utility_scripts.task_logs import display_log_path

LOCAL_CI_VERIFICATION_KEY = "local_ci_verification"
FINDINGS_RELATIVE_PATH = "forge/FINDINGS.md"
EXPECTED_PUBLICATION_PATHS = frozenset({FINDINGS_RELATIVE_PATH})
HUMAN_INTERVENTION_LABEL = "human-intervention"
MAX_FIXUP_ATTEMPTS = 2
LOCAL_CI_FIXUP_TIMEOUT_SECONDS = 1800
DEFAULT_BASE_BRANCH = "master"


def run_local_ci_verification(
        *,
        repo_path: str,
        coordinates: str,
        base_commit: str,
        metrics_repo_path: str | None = None,
        max_fixup_attempts: int = MAX_FIXUP_ATTEMPTS,
        reproduce_full_ci: bool = False,
) -> LocalCIVerificationResult:
    """Run local verification, fixing and retrying when possible.

    When `reproduce_full_ci` is set, the pre-publication gate additionally
    reproduces the per-PR CI surface locally — the whole changed-metadata native
    test matrix and the Spring AOT smoke tests — instead of leaving those to
    repository CI (§FS-local-ci-equivalent-verification.2).
    """
    result = LocalCIVerificationResult(status="running", base_commit=base_commit)
    _log_local_ci(f"Starting local CI verification for {coordinates}")
    for attempt in range(max_fixup_attempts + 1):
        _log_local_ci(f"Verification attempt {attempt + 1}/{max_fixup_attempts + 1}", indent_level=1)
        failed_command = _run_verification_once(repo_path, base_commit, result, reproduce_full_ci=reproduce_full_ci)
        if failed_command is None:
            result.status = "success"
            result.final_commit = _git_stdout(repo_path, ["rev-parse", "HEAD"])
            result.repo_fix_paths = classify_repo_fix_paths(repo_path, base_commit, coordinates)
            result.human_intervention_required = bool(result.repo_fix_paths)
            _log_local_ci(f"Verification passed for {coordinates}", indent_level=1)
            write_verification_metrics(metrics_repo_path, result)
            return result

        result.failure_gate = failed_command.gate
        result.failure_command = failed_command.command
        _log_local_ci(f"Verification failed at gate {failed_command.gate}", indent_level=1)
        if attempt >= max_fixup_attempts:
            result.status = "failure"
            result.final_commit = _git_stdout(repo_path, ["rev-parse", "HEAD"])
            write_verification_metrics(metrics_repo_path, result)
            raise LocalCIVerificationError(result)

        _log_local_ci(f"Running fixup after {failed_command.gate}", indent_level=1)
        fixup = _run_fixup(repo_path, coordinates, failed_command)
        result.fixups.append(fixup)
        if fixup.commit is None:
            result.status = "failure"
            result.final_commit = _git_stdout(repo_path, ["rev-parse", "HEAD"])
            write_verification_metrics(metrics_repo_path, result)
            raise LocalCIVerificationError(result)

    result.status = "failure"
    write_verification_metrics(metrics_repo_path, result)
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
            run_git_transport(["fetch", remote_name, base_branch], cwd=repo_path)
            return f"{remote_name}/{base_branch}"

    run_git_transport(["fetch", f"https://github.com/{target_repo}.git", base_branch], cwd=repo_path)
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
        if path.startswith(allowed_prefixes) or path in EXPECTED_PUBLICATION_PATHS:
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
        reproduce_full_ci: bool = False,
) -> CommandRecord | None:
    # Pre-publication gate (§FS-local-ci-equivalent-verification.2): run only the
    # cross-cutting checks a single-library generation cannot settle on its own —
    # index-file validation (an aggregate that depends on the rebased master
    # state), the shared Docker-image vulnerability scan, and the
    # human-intervention classification performed by the caller. Library-scoped
    # verification (metadata, style, stats, native tests) belongs to the
    # generation/finalization tier (§FS-local-ci-equivalent-verification.1).
    changed_files = changed_files_for_ci(repo_path, base_commit)
    _log_local_ci(f"Changed files detected: {len(changed_files)}", indent_level=1)

    failed = _run_index_validation(repo_path, base_commit, changed_files, result)
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

    # Opt-in full local reproduction of the per-PR CI surface the gate otherwise
    # leaves to repository CI (§FS-local-ci-equivalent-verification.2): the whole
    # changed-metadata native test matrix and the Spring AOT smoke tests.
    if reproduce_full_ci:
        failed = _run_full_ci_reproduction(repo_path, base_commit, changed_files, result)
        if failed is not None:
            return failed

    return None


def _run_full_ci_reproduction(
        repo_path: str,
        base_commit: str,
        changed_files: list[str],
        result: LocalCIVerificationResult,
) -> CommandRecord | None:
    """Reproduce the expensive per-PR CI surface locally: native matrix + Spring AOT."""
    _log_local_ci("Full CI reproduction: changed-metadata native test matrix and Spring AOT", indent_level=1)
    try:
        changed_metadata_matrix = _gradle_json_output(
            repo_path,
            "generateChangedMetadataTestMatrix",
            base_commit,
            result,
            "changed-metadata-matrix",
        )
    except _GradleOutputFailure as exc:
        return exc.record

    test_entries = _matrix_entries(changed_metadata_matrix)
    _log_local_ci(f"Changed-metadata test matrix entries: {len(test_entries)}", indent_level=1)
    failed = _run_test_matrix_entries(repo_path, test_entries, result)
    if failed is not None:
        return failed

    if _should_run_spring_aot_tests(changed_files):
        failed = _run_spring_aot_verification(repo_path, base_commit, changed_files, result)
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


def run_repair_agent(
        *,
        repo_path: str,
        prompt: str,
        task_type: str,
        library: str,
        commit_message: str,
        timeout_seconds: int = LOCAL_CI_FIXUP_TIMEOUT_SECONDS,
) -> RepairAttempt:
    """Run one centralized analysis-agent repair and commit only its edits."""
    before_paths = _worktree_changed_paths(repo_path)
    selection = get_analysis_agent()
    command = [selection.backend, selection.model]
    result = analysis_agent_run(
        working_dir=repo_path,
        context=prompt,
        task_type=task_type,
        library=library,
        timeout=timeout_seconds,
    )
    displayed_log_path = display_log_path(result.log_path)
    if result.return_code != 0:
        failure_reason = (
            "timed out" if result.timed_out
            else f"exited with code {result.return_code}"
        )
        return RepairAttempt(command, None, [], displayed_log_path, failure_reason)

    changed_paths = sorted(_worktree_changed_paths(repo_path) - before_paths)
    if not changed_paths:
        return RepairAttempt(command, None, [], displayed_log_path, "changed no files")

    subprocess.run(["git", "add", "-A", "--", *changed_paths], cwd=repo_path, check=True)
    staged = subprocess.run(["git", "diff", "--cached", "--quiet"], cwd=repo_path, check=False)
    if staged.returncode == 0:
        return RepairAttempt(
            command, None, changed_paths, displayed_log_path, "left nothing to commit",
        )
    subprocess.run(["git", "commit", "-m", commit_message], cwd=repo_path, check=True)
    return RepairAttempt(
        command,
        _git_stdout(repo_path, ["rev-parse", "HEAD"]),
        changed_paths,
        displayed_log_path,
        None,
    )


def _run_fixup(repo_path: str, coordinates: str, failed_command: CommandRecord) -> FixupRecord:
    attempt = run_repair_agent(
        repo_path=repo_path,
        prompt=_build_fixup_prompt(coordinates, failed_command),
        task_type="local-ci-fixup",
        library=coordinates,
        commit_message="Apply local CI verification fixes",
    )
    outcome = attempt.failure_reason or f"committed {attempt.commit}"
    # The record no longer carries the path, so this line is the only place the
    # operator learns which log holds the fixup turn. §FS-durable-generation-logs
    _log_local_ci(
        f"Fixup for gate {failed_command.gate} {outcome}; log: {attempt.log_path}",
        indent_level=1,
    )
    return FixupRecord(
        gate=failed_command.gate,
        command=attempt.command,
        commit=attempt.commit,
        changed_paths=attempt.changed_paths,
    )


def _build_fixup_prompt(coordinates: str, failed_command: CommandRecord) -> str:
    env_lines = [f"{key}={value}" for key, value in sorted(failed_command.env.items())]
    reproduction = " ".join(failed_command.command)
    if env_lines:
        reproduction = " ".join(env_lines + [reproduction])
    return "\n".join([
        "Fix the repository so the local CI-equivalent verification gate passes.",
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


def write_verification_metrics(metrics_repo_path: str | None, result: LocalCIVerificationResult) -> None:
    if metrics_repo_path is None:
        return
    pending_path = os.path.join(metrics_repo_path, PENDING_METRICS_FILENAME)
    if not os.path.isfile(pending_path):
        return
    metrics = read_pending_metrics(metrics_repo_path)
    metrics[LOCAL_CI_VERIFICATION_KEY] = result.to_metrics()
    write_pending_metrics(metrics_repo_path, metrics)


def _is_metadata_index_file(path: str) -> bool:
    parts = path.split("/")
    return len(parts) == 4 and parts[0] == "metadata" and parts[3] == "index.json"


def _should_run_docker_scan(changed_files: list[str]) -> bool:
    return any(path.startswith("tests/tck-build-logic/src/main/resources/allowed-docker-images/") for path in changed_files)
