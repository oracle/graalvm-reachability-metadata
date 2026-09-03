# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Run and publish fixed-input code coverage improvement benchmarks.

The runner owns deterministic matrix expansion, isolated worktrees, benchmark
conversion, compact metrics extraction, and immediate same-repository
publication. Rhei continues to own the coverage phases themselves.
§FS-code-coverage-benchmarking §AR-code-coverage-benchmarking
"""

from __future__ import annotations

import argparse
import datetime as dt
import fcntl
import json
import os
import re
import shutil
import subprocess
import sys
import uuid
from dataclasses import dataclass
from pathlib import Path
from typing import Any, TextIO

from jsonschema import Draft202012Validator, FormatChecker, ValidationError

FORGE_ROOT = Path(__file__).resolve().parents[1]
REPOSITORY_ROOT = FORGE_ROOT.parent
SUITE_PATH = FORGE_ROOT / "benchmarks" / "code_coverage_suite.json"
SUITE_SCHEMA_PATH = (
    FORGE_ROOT / "schemas" / "code_coverage_benchmark_suite_schema.json"
)
RESULT_SCHEMA_PATH = (
    FORGE_ROOT / "schemas" / "code_coverage_benchmark_result_schema.json"
)
FINAL_METRICS_SCHEMA_PATH = (
    FORGE_ROOT / "schemas" / "code_coverage_final_metrics_schema.json"
)
DEFAULT_WORKSPACE_ROOT = (
    FORGE_ROOT / "local_repositories" / "code_coverage_benchmarks"
)
BENCHMARK_DIR = Path("runtime") / "code-coverage" / "benchmark"
RUN_RECORD = BENCHMARK_DIR / "run.json"
RESULT_RECORD = BENCHMARK_DIR / "result.json"
PUBLICATION_MARKER = BENCHMARK_DIR / "publication.json"
RESULT_SCHEMA_VERSION = "1.0.0"
COMMIT_SUBJECT = "Record code coverage benchmark"
MAX_PUBLISH_ATTEMPTS = 5

sys.path.insert(0, str(FORGE_ROOT))

from git_scripts.common_git import (  # noqa: E402
    GitTransportError,
    run_git_transport,
)
from utility_scripts.code_coverage_jacoco import (  # noqa: E402
    load_jacoco_method_coverage,
)
from utility_scripts.metadata_index import resolve_test_dir  # noqa: E402


class BenchmarkError(RuntimeError):
    """Raised when benchmark evidence or repository state is unsafe."""


@dataclass(frozen=True)
class Library:
    """One fixed benchmark subject."""

    index: int
    coordinate: str
    covered_methods: int
    all_methods: int
    coverage_percent: float


@dataclass(frozen=True)
class AgentConfiguration:
    """One configured agent/model pair."""

    agent: str
    configured_model: str
    provider: str
    target_model: str

    def target(self, thinking: str) -> str:
        """Render the Rhei target selector for this configuration."""
        return (
            f"{self.agent}[{thinking}]:"
            f"{self.provider}/{self.target_model}"
        )


@dataclass(frozen=True)
class MatrixCell:
    """One library/configuration/thinking execution."""

    library: Library
    configuration: AgentConfiguration
    thinking: str


@dataclass(frozen=True)
class Suite:
    """Validated checked-in benchmark configuration."""

    commit: str
    issue_number: int
    workspace_name: str
    libraries: tuple[Library, ...]
    configurations: tuple[AgentConfiguration, ...]
    thinking_levels: tuple[str, ...]


def _read_json(path: Path) -> Any:
    with path.open(encoding="utf-8") as source:
        return json.load(source)


def _write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.{uuid.uuid4().hex}.tmp")
    with temporary.open("w", encoding="utf-8") as destination:
        json.dump(value, destination, indent=2, ensure_ascii=False)
        destination.write("\n")
    os.replace(temporary, path)


def _validate(value: Any, schema_path: Path) -> None:
    schema: dict[str, Any] = _read_json(schema_path)
    validator = Draft202012Validator(schema, format_checker=FormatChecker())
    validator.validate(value)


def load_suite(path: Path = SUITE_PATH) -> Suite:
    """Load and semantically validate the fixed benchmark suite."""
    raw: dict[str, Any] = _read_json(path)
    _validate(raw, SUITE_SCHEMA_PATH)
    libraries = tuple(
        Library(
            index=int(item["index"]),
            coordinate=str(item["coordinate"]),
            covered_methods=int(item["coveredMethods"]),
            all_methods=int(item["allMethods"]),
            coverage_percent=float(item["coveragePercent"]),
        )
        for item in raw["libraries"]
    )
    configurations = tuple(
        AgentConfiguration(
            agent=str(item["agent"]),
            configured_model=str(item["configuredModel"]),
            provider=str(item["provider"]),
            target_model=str(item["targetModel"]),
        )
        for item in raw["configurations"]
    )
    indexes = [library.index for library in libraries]
    if indexes != list(range(1, len(libraries) + 1)):
        raise BenchmarkError("Library indexes must be consecutive and ordered.")
    if len({library.coordinate for library in libraries}) != len(libraries):
        raise BenchmarkError("Benchmark library coordinates must be unique.")
    config_keys = {
        (configuration.agent, configuration.configured_model)
        for configuration in configurations
    }
    if len(config_keys) != len(configurations):
        raise BenchmarkError("Benchmark agent/model configurations must be unique.")
    for library in libraries:
        expected = round(100.0 * library.covered_methods / library.all_methods, 2)
        if expected != library.coverage_percent:
            raise BenchmarkError(
                f"Checked-in coverage for {library.coordinate} is inconsistent."
            )
    return Suite(
        commit=str(raw["benchmarkSuiteCommit"]),
        issue_number=int(raw["syntheticIssueNumber"]),
        workspace_name=str(raw["workspaceName"]),
        libraries=libraries,
        configurations=configurations,
        thinking_levels=tuple(str(value) for value in raw["thinkingLevels"]),
    )


def _reject_duplicates(values: list[Any] | None, label: str) -> None:
    if values is not None and len(values) != len(set(values)):
        raise BenchmarkError(f"Duplicate {label} selection.")


def expand_matrix(
        suite: Suite,
        library_indexes: list[int] | None = None,
        agents: list[str] | None = None,
        models: list[str] | None = None,
        thinking_levels: list[str] | None = None,
) -> list[MatrixCell]:
    """Return the filtered cross-product after validating all selections."""
    _reject_duplicates(library_indexes, "library index")
    _reject_duplicates(agents, "agent")
    _reject_duplicates(models, "model")
    _reject_duplicates(thinking_levels, "thinking")

    known_indexes = {library.index for library in suite.libraries}
    known_agents = {configuration.agent for configuration in suite.configurations}
    known_models = {
        configuration.configured_model
        for configuration in suite.configurations
    }
    known_thinking = set(suite.thinking_levels)
    for selected, known, label in (
        (library_indexes, known_indexes, "library index"),
        (agents, known_agents, "agent"),
        (models, known_models, "model"),
        (thinking_levels, known_thinking, "thinking"),
    ):
        unknown = set(selected or []) - known
        if unknown:
            values = ", ".join(str(value) for value in sorted(unknown))
            raise BenchmarkError(f"Unknown {label} selection: {values}.")

    selected_libraries = [
        library
        for library in suite.libraries
        if library_indexes is None or library.index in library_indexes
    ]
    selected_configurations = [
        configuration
        for configuration in suite.configurations
        if (agents is None or configuration.agent in agents)
        and (
            models is None
            or configuration.configured_model in models
        )
    ]
    if agents is not None and models is not None:
        for agent in agents:
            if not any(
                    configuration.agent == agent
                    for configuration in selected_configurations
            ):
                raise BenchmarkError(
                    f"Selected models are incompatible with agent '{agent}'."
                )
        for model in models:
            if not any(
                    configuration.configured_model == model
                    for configuration in selected_configurations
            ):
                raise BenchmarkError(
                    f"Model '{model}' is incompatible with the selected agents."
                )
    selected_thinking = [
        thinking
        for thinking in suite.thinking_levels
        if thinking_levels is None or thinking in thinking_levels
    ]
    return [
        MatrixCell(library, configuration, thinking)
        for library in selected_libraries
        for configuration in selected_configurations
        for thinking in selected_thinking
    ]


def print_matrix(cells: list[MatrixCell]) -> None:
    """Print every selected cell before any repository mutation."""
    print(f"Selected {len(cells)} code coverage benchmark execution(s):")
    for ordinal, cell in enumerate(cells, start=1):
        print(
            f"  {ordinal:>2}. library={cell.library.index} "
            f"{cell.library.coordinate} agent={cell.configuration.agent} "
            f"model={cell.configuration.configured_model} "
            f"thinking={cell.thinking}"
        )


def _git_output(repo_path: Path, *arguments: str) -> str:
    result = subprocess.run(
        ["git", *arguments],
        cwd=repo_path,
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()


def _runner_commit() -> str:
    return _git_output(REPOSITORY_ROOT, "rev-parse", "HEAD")


def _verify_preconditions(suite: Suite, cells: list[MatrixCell]) -> None:
    if shutil.which("rhei") is None:
        raise BenchmarkError("rhei is required and must be available on PATH.")
    for agent in {cell.configuration.agent for cell in cells}:
        executable = "claude" if agent == "claude-code" else agent
        if shutil.which(executable) is None:
            raise BenchmarkError(
                f"{executable} is required for selected agent '{agent}'."
            )
    subprocess.run(
        ["git", "cat-file", "-e", f"{suite.commit}^{{commit}}"],
        cwd=REPOSITORY_ROOT,
        check=True,
    )
    tracked_status = _git_output(
        REPOSITORY_ROOT,
        "status",
        "--porcelain",
        "--untracked-files=no",
    )
    if tracked_status:
        raise BenchmarkError(
            "The benchmark runner checkout must have no tracked changes."
        )


def _safe_segment(value: str) -> str:
    return re.sub(r"[^A-Za-z0-9_.-]+", "-", value).strip("-.")


def _new_run_id(cell: MatrixCell) -> str:
    timestamp = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    return "-".join((
        timestamp,
        f"l{cell.library.index}",
        _safe_segment(cell.configuration.agent),
        _safe_segment(cell.configuration.configured_model),
        cell.thinking,
        uuid.uuid4().hex[:10],
    ))


def _create_publication_worktree(
        repository_root: Path,
        path: Path,
        start_point: str,
) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        ["git", "worktree", "add", "--detach", str(path), start_point],
        cwd=repository_root,
        check=True,
    )


def _remove_worktree(
        path: Path,
        repository_root: Path = REPOSITORY_ROOT,
) -> None:
    subprocess.run(
        ["git", "worktree", "remove", "--force", str(path)],
        cwd=repository_root,
        check=True,
    )


def _discard_source_worktree(path: Path) -> None:
    try:
        _remove_worktree(path)
    except (OSError, subprocess.SubprocessError) as error:
        print(
            f"ERROR: Could not remove published source worktree {path}: "
            f"{error}",
            file=sys.stderr,
        )


def _create_source_worktree(path: Path, suite_commit: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        ["git", "worktree", "add", "--detach", str(path), suite_commit],
        cwd=REPOSITORY_ROOT,
        check=True,
    )


def _run_record_path(workspace: Path) -> Path:
    return workspace / RUN_RECORD


def _result_record_path(workspace: Path) -> Path:
    return workspace / RESULT_RECORD


def _publication_marker_path(workspace: Path) -> Path:
    return workspace / PUBLICATION_MARKER


def _run_identity(
        *,
        run_id: str,
        suite: Suite,
        runner_commit: str,
        cell: MatrixCell,
        source_worktree: Path,
) -> dict[str, Any]:
    return {
        "schemaVersion": "1.0.0",
        "runId": run_id,
        "startedAt": (
            dt.datetime.now(dt.timezone.utc)
            .isoformat(timespec="seconds")
            .replace("+00:00", "Z")
        ),
        "benchmarkSuiteCommit": suite.commit,
        "runnerCommit": runner_commit,
        "coordinate": cell.library.coordinate,
        "workspaceName": suite.workspace_name,
        "agent": cell.configuration.agent,
        "configuredModel": cell.configuration.configured_model,
        "targetModel": cell.configuration.target_model,
        "thinking": cell.thinking,
        "checkedInAllMethods": cell.library.all_methods,
        "sourceWorktree": str(source_worktree.resolve()),
        "runnerForgePath": str(FORGE_ROOT.resolve()),
    }


def _ensure_run_record(
        workspace: Path,
        identity: dict[str, Any],
) -> dict[str, Any]:
    path = _run_record_path(workspace)
    if path.is_file():
        existing = _read_json(path)
        for key, value in identity.items():
            if existing.get(key) != value:
                raise BenchmarkError(
                    f"Existing benchmark run record conflicts on '{key}'."
                )
        return existing
    _write_json(path, identity)
    return identity


def _record_completion(
        workspace: Path,
        status: str,
        exit_code: int | None,
) -> None:
    run_record = _read_json(_run_record_path(workspace))
    run_record["requestedStatus"] = status
    run_record["rheiExitCode"] = exit_code
    _write_json(_run_record_path(workspace), run_record)


def _instantiate_command(
        suite: Suite,
        cell: MatrixCell,
        run_id: str,
        runner_commit: str,
        source_worktree: Path,
        workspace: Path,
        identity: dict[str, Any],
) -> list[str]:
    configuration = cell.configuration
    values = {
        "issue_number": str(suite.issue_number),
        "coordinate": cell.library.coordinate,
        "benchmark": "true",
        "benchmark_run_id": run_id,
        "benchmark_suite_commit": suite.commit,
        "benchmark_runner_commit": runner_commit,
        "benchmark_started_at": identity["startedAt"],
        "benchmark_source_worktree": str(source_worktree.resolve()),
        "benchmark_runner_forge_path": str(FORGE_ROOT.resolve()),
        "benchmark_agent": configuration.agent,
        "benchmark_model": configuration.configured_model,
        "benchmark_target_model": configuration.target_model,
        "benchmark_thinking": cell.thinking,
        "benchmark_checked_in_all_methods": str(cell.library.all_methods),
        "repo_checkout": str(source_worktree.resolve()),
        "work_subdir": "forge",
        "workspace_path": str(workspace.resolve()),
        "worker_agent": configuration.target(cell.thinking),
    }
    command = ["rhei", "instantiate", "code-coverage-improvement"]
    command.extend(f"{key}={value}" for key, value in values.items())
    command.extend(["--output", str(workspace), "--execute"])
    return command


def _execute_cell(
        suite: Suite,
        cell: MatrixCell,
        runner_commit: str,
        workspace_root: Path,
) -> tuple[bool, bool]:
    run_id = _new_run_id(cell)
    run_parent = workspace_root / run_id
    source_worktree = run_parent / "source"
    workspace = run_parent / suite.workspace_name
    _create_source_worktree(source_worktree, suite.commit)
    identity = _run_identity(
        run_id=run_id,
        suite=suite,
        runner_commit=runner_commit,
        cell=cell,
        source_worktree=source_worktree,
    )
    command = _instantiate_command(
        suite,
        cell,
        run_id,
        runner_commit,
        source_worktree,
        workspace,
        identity,
    )
    try:
        result = subprocess.run(command, cwd=FORGE_ROOT, check=False)
        _ensure_run_record(workspace, identity)
        result_path = _result_record_path(workspace)
        recorded_result = _read_json(result_path) if result_path.is_file() else None
        status = (
            "success"
            if _publication_marker_path(workspace).is_file()
            or (
                isinstance(recorded_result, dict)
                and recorded_result.get("status") == "success"
            )
            else "failure"
        )
        _record_completion(workspace, status, result.returncode)
        if not _publication_marker_path(workspace).is_file():
            publish_workspace(
                workspace,
                requested_status=status,
                exit_code=result.returncode,
            )
        if _publication_marker_path(workspace).is_file():
            _discard_source_worktree(source_worktree)
            print(f"Preserved benchmark workspace: {workspace.resolve()}")
            return status == "success", True
    except (BenchmarkError, OSError, subprocess.SubprocessError) as error:
        print(f"ERROR: Benchmark run {run_id} failed: {error}", file=sys.stderr)
        try:
            _ensure_run_record(workspace, identity)
            _record_completion(workspace, "failure", None)
            if not _publication_marker_path(workspace).is_file():
                publish_workspace(workspace, requested_status="failure")
            if _publication_marker_path(workspace).is_file():
                _discard_source_worktree(source_worktree)
                print(f"Preserved benchmark workspace: {workspace.resolve()}")
                return False, True
        except (BenchmarkError, OSError, subprocess.SubprocessError) as publish_error:
            print(
                f"ERROR: Benchmark result {run_id} was not published: "
                f"{publish_error}",
                file=sys.stderr,
            )
    print(f"Preserved benchmark workspace: {workspace.resolve()}")
    print(f"Preserved source worktree: {source_worktree.resolve()}")
    return False, False


def _iteration_files(directory: Path, prefix: str, suffix: str) -> list[Path]:
    pattern = re.compile(
        rf"^{re.escape(prefix)}-([0-9]+){re.escape(suffix)}$"
    )
    matched: list[tuple[int, Path]] = []
    if directory.is_dir():
        for path in directory.iterdir():
            match = pattern.fullmatch(path.name)
            if match:
                matched.append((int(match.group(1)), path))
    return [path for _, path in sorted(matched)]


def _jacoco_snapshot(path: Path) -> tuple[int, int] | None:
    try:
        coverage = load_jacoco_method_coverage([str(path)])
    except (OSError, ValueError):
        return None
    return len(coverage), sum(1 for method in coverage.values() if method.covered)


def _phase_coverage(
        before: int | None,
        after: int | None,
        all_methods: int | None,
) -> dict[str, int | float | None]:
    gained = None
    percentage = None
    if before is not None and after is not None:
        gained = after - before
        if all_methods is not None and all_methods > 0:
            percentage = round(100.0 * gained / all_methods, 2)
    return {
        "coveredBefore": before,
        "coveredAfter": after,
        "methodsGained": gained,
        "percentagePointsGained": percentage,
        "allMethods": all_methods,
    }


def _coverage_from_final_metrics(
        workspace: Path,
) -> tuple[dict[str, Any], dict[str, Any], dict[str, Any]] | None:
    path = (
        workspace
        / "runtime"
        / "code-coverage"
        / "finalization"
        / "final-metrics.json"
    )
    if not path.is_file():
        return None
    try:
        metrics: dict[str, Any] = _read_json(path)
        _validate(metrics, FINAL_METRICS_SCHEMA_PATH)
        checkpoints = {
            checkpoint["name"]: checkpoint
            for checkpoint in metrics["runCoverage"]["checkpoints"]
        }
        all_methods = int(metrics["runCoverage"]["universe"])
        run_start = int(checkpoints["runStart"]["covered"])
        after_api = int(checkpoints["afterApiPhase"]["covered"])
        final = int(checkpoints["final"]["covered"])
    except (KeyError, TypeError, ValueError, json.JSONDecodeError, ValidationError):
        return None
    return (
        _phase_coverage(run_start, after_api, all_methods),
        _phase_coverage(after_api, final, all_methods),
        _phase_coverage(run_start, final, all_methods),
    )


def _coverage_from_reports(
        workspace: Path,
) -> tuple[dict[str, Any], dict[str, Any], dict[str, Any]]:
    validation = workspace / "runtime" / "code-coverage" / "validation"
    discovery = workspace / "runtime" / "code-coverage" / "discovery"
    api_paths = _iteration_files(validation, "jacoco", ".xml")
    deep_paths = _iteration_files(discovery, "jacoco-deep", ".xml")
    api_snapshots = [
        snapshot
        for snapshot in (_jacoco_snapshot(path) for path in api_paths)
        if snapshot is not None
    ]
    deep_snapshots = [
        snapshot
        for snapshot in (_jacoco_snapshot(path) for path in deep_paths)
        if snapshot is not None
    ]
    all_methods = api_snapshots[0][0] if api_snapshots else (
        deep_snapshots[0][0] if deep_snapshots else None
    )

    def covered(snapshot: tuple[int, int] | None) -> int | None:
        if snapshot is None or all_methods is None or snapshot[0] != all_methods:
            return None
        return snapshot[1]

    run_start = covered(api_snapshots[0] if api_snapshots else None)
    api_after = covered(
        deep_snapshots[0]
        if deep_snapshots
        else (api_snapshots[-1] if api_snapshots else None)
    )
    deep_before = covered(deep_snapshots[0] if deep_snapshots else None)
    deep_after = covered(deep_snapshots[-1] if deep_snapshots else None)
    latest = deep_after if deep_after is not None else api_after
    return (
        _phase_coverage(run_start, api_after, all_methods),
        _phase_coverage(deep_before, deep_after, all_methods),
        _phase_coverage(run_start, latest, all_methods),
    )


def _load_invocations(workspace: Path) -> tuple[list[dict[str, Any]], bool]:
    directory = workspace / "runtime" / "accounting" / "invocations"
    if not directory.is_dir():
        return [], False
    invocations: list[dict[str, Any]] = []
    for path in sorted(directory.glob("*.json")):
        try:
            value = _read_json(path)
        except (OSError, ValueError):
            continue
        if isinstance(value, dict):
            invocations.append(value)
    return invocations, True


def _nested_token(invocation: dict[str, Any], *keys: str) -> int | None:
    value: Any = invocation
    try:
        for key in keys:
            value = value[key]
        value = value["value"]
    except (KeyError, TypeError):
        return None
    return value if type(value) is int and value >= 0 else None


def _phase_tokens(
        invocations: list[dict[str, Any]],
        accounting_exists: bool,
        phase: str,
        phase_has_evidence: bool,
) -> dict[str, int | None]:
    states = {f"{phase}-cover", f"{phase}-fix"}
    relevant = [
        invocation
        for invocation in invocations
        if invocation.get("state") in states
    ]
    if not relevant:
        empty_value = 0 if accounting_exists and phase_has_evidence else None
        return {
            "input": empty_value,
            "cachedInputRead": empty_value,
            "output": empty_value,
        }

    def total(*keys: str) -> int | None:
        values = [_nested_token(invocation, *keys) for invocation in relevant]
        if any(value is None for value in values):
            return None
        return sum(value for value in values if value is not None)

    return {
        "input": total("tokens", "input", "total"),
        "cachedInputRead": total("tokens", "input", "cached_read"),
        "output": total("tokens", "output", "total"),
    }


def _stop_passes(workspace: Path, phase: str) -> int | None:
    final_metrics = (
        workspace
        / "runtime"
        / "code-coverage"
        / "finalization"
        / "final-metrics.json"
    )
    if final_metrics.is_file():
        try:
            for decision in _read_json(final_metrics).get("stopDecisions", []):
                if decision.get("phase") == phase and type(decision.get("passes")) is int:
                    return int(decision["passes"])
        except (OSError, ValueError, TypeError):
            pass
    directory = "validation" if phase == "api" else "discovery"
    path = (
        workspace
        / "runtime"
        / "code-coverage"
        / directory
        / f"{phase}-stop-decision.json"
    )
    if path.is_file():
        try:
            passes = _read_json(path).get("passes")
            return passes if type(passes) is int and passes >= 0 else None
        except (OSError, ValueError, TypeError):
            return None
    return None


def _sum_nullable(left: int | None, right: int | None) -> int | None:
    if left is None or right is None:
        return None
    return left + right


def _failure_phase(workspace: Path) -> str | None:
    path = workspace / "runtime" / "state-transitions.log"
    if not path.is_file():
        return None
    try:
        lines = [
            line.strip()
            for line in path.read_text(encoding="utf-8").splitlines()
            if line.strip()
        ]
    except OSError:
        return None
    if not lines:
        return None
    task_id = lines[-1].split()[0]
    task = task_id.rsplit(".code-coverage-", 1)[-1]
    mapping = {
        "convert": "conversion",
        "prepare": "preparation",
        "api-inventory": "api",
        "api-coverage": "api",
        "prepare-native-metadata": "native-metadata",
        "deep-coverage": "deep",
        "finalization": "finalization",
        "benchmark-publication": "benchmark-publication",
    }
    return mapping.get(task)


def _collect_result(
        workspace: Path,
        requested_status: str,
        exit_code: int | None,
) -> dict[str, Any]:
    result_path = _result_record_path(workspace)
    if result_path.is_file():
        existing: dict[str, Any] = _read_json(result_path)
        _validate([existing], RESULT_SCHEMA_PATH)
        return existing
    run: dict[str, Any] = _read_json(_run_record_path(workspace))
    finalized = _coverage_from_final_metrics(workspace)
    if requested_status == "success" and finalized is None:
        raise BenchmarkError(
            "A successful benchmark must have valid final coverage metrics."
        )
    api_coverage, deep_coverage, total_coverage = (
        finalized or _coverage_from_reports(workspace)
    )
    invocations, accounting_exists = _load_invocations(workspace)

    def phase_result(
        phase: str,
        coverage: dict[str, Any],
    ) -> dict[str, Any]:
        evidence = any(value is not None for value in coverage.values())
        relevant = [
            invocation
            for invocation in invocations
            if invocation.get("state") in {f"{phase}-cover", f"{phase}-fix"}
        ]
        fix_count = (
            sum(invocation.get("state") == f"{phase}-fix" for invocation in relevant)
            if accounting_exists and (evidence or relevant)
            else None
        )
        return {
            "coverPasses": _stop_passes(workspace, phase),
            "fixInvocations": fix_count,
            "tokens": _phase_tokens(
                invocations,
                accounting_exists,
                phase,
                evidence,
            ),
            "coverage": coverage,
        }

    api = phase_result("api", api_coverage)
    deep = phase_result("deep", deep_coverage)
    observed_models = sorted({
        str(invocation["model"])
        for invocation in invocations
        if isinstance(invocation.get("model"), str)
        and invocation["model"]
    })
    if len(observed_models) > 1:
        raise BenchmarkError(
            "Rhei accounting reports multiple observed models: "
            + ", ".join(observed_models)
        )
    total_tokens = {
        key: _sum_nullable(api["tokens"][key], deep["tokens"][key])
        for key in ("input", "cachedInputRead", "output")
    }
    total = {
        "coverPasses": _sum_nullable(
            api["coverPasses"], deep["coverPasses"]
        ),
        "fixInvocations": _sum_nullable(
            api["fixInvocations"], deep["fixInvocations"]
        ),
        "tokens": total_tokens,
        "coverage": total_coverage,
    }
    all_methods = total_coverage["allMethods"]
    status = "success" if requested_status == "success" else "failure"
    result = {
        "schemaVersion": RESULT_SCHEMA_VERSION,
        "runId": run["runId"],
        "timestamp": (
            dt.datetime.now(dt.timezone.utc)
            .isoformat(timespec="seconds")
            .replace("+00:00", "Z")
        ),
        "benchmarkSuiteCommit": run["benchmarkSuiteCommit"],
        "runnerCommit": run["runnerCommit"],
        "coordinate": run["coordinate"],
        "workspaceName": run["workspaceName"],
        "agent": run["agent"],
        "configuredModel": run["configuredModel"],
        "observedModel": observed_models[0] if observed_models else None,
        "thinking": run["thinking"],
        "status": status,
        "failure": None if status == "success" else {
            "phase": _failure_phase(workspace),
            "exitCode": exit_code,
        },
        "checkedInAllMethods": run["checkedInAllMethods"],
        "measuredAllMethodsDifference": (
            all_methods - run["checkedInAllMethods"]
            if all_methods is not None
            else None
        ),
        "api": api,
        "deep": deep,
        "total": total,
    }
    _validate([result], RESULT_SCHEMA_PATH)
    _write_json(result_path, result)
    return result


def _metrics_relative_path(coordinate: str) -> Path:
    group, artifact, version = coordinate.split(":")
    return Path("code-coverage-benchmarks") / group / artifact / f"{version}.json"


def _merge_result(path: Path, result: dict[str, Any]) -> bool:
    entries: list[dict[str, Any]] = []
    if path.is_file():
        value = _read_json(path)
        if not isinstance(value, list):
            raise BenchmarkError(f"Benchmark result file is not a list: {path}")
        entries = value
    for existing in entries:
        if existing.get("runId") != result["runId"]:
            continue
        if existing != result:
            raise BenchmarkError(
                f"Run ID {result['runId']} already has different metrics."
            )
        _validate(entries, RESULT_SCHEMA_PATH)
        return False
    entries.append(result)
    entries.sort(key=lambda entry: (entry["timestamp"], entry["runId"]))
    _validate(entries, RESULT_SCHEMA_PATH)
    _write_json(path, entries)
    return True


def _publish_lock(metrics_repo_path: Path) -> TextIO:
    common_dir = _git_output(metrics_repo_path, "rev-parse", "--git-common-dir")
    common_path = Path(common_dir)
    if not common_path.is_absolute():
        common_path = (metrics_repo_path / common_path).resolve()
    lock_path = common_path / "code-coverage-benchmark-publish.lock"
    lock_handle = lock_path.open("a+", encoding="utf-8")
    fcntl.flock(lock_handle.fileno(), fcntl.LOCK_EX)
    return lock_handle


def _discard_publication_worktree(
        repository_root: Path,
        path: Path,
) -> None:
    removal = subprocess.run(
        ["git", "worktree", "remove", "--force", str(path)],
        cwd=repository_root,
        check=False,
        capture_output=True,
        text=True,
    )
    if removal.returncode != 0:
        detail = removal.stderr.strip() or removal.stdout.strip()
        print(
            f"ERROR: Could not remove disposable publication worktree "
            f"{path}: {detail}",
            file=sys.stderr,
        )


def _publish_result(
        repository_root: Path,
        workspace: Path,
        result: dict[str, Any],
) -> str:
    relative_path = _metrics_relative_path(result["coordinate"])
    lock_handle = _publish_lock(repository_root)
    try:
        for attempt in range(1, MAX_PUBLISH_ATTEMPTS + 1):
            publisher = workspace.parent / f"publisher-{uuid.uuid4().hex[:12]}"
            created = False
            try:
                run_git_transport(
                    ["fetch", "origin", "master"],
                    cwd=str(repository_root),
                )
                _create_publication_worktree(
                    repository_root,
                    publisher,
                    "origin/master",
                )
                created = True
                changed = _merge_result(publisher / relative_path, result)
                subprocess.run(
                    ["git", "add", str(relative_path)],
                    cwd=publisher,
                    check=True,
                )
                staged = subprocess.run(
                    ["git", "diff", "--cached", "--quiet"],
                    cwd=publisher,
                    check=False,
                )
                if staged.returncode == 0:
                    return _git_output(publisher, "rev-parse", "HEAD")
                if not changed:
                    raise BenchmarkError(
                        "Identical benchmark metrics unexpectedly changed "
                        "the index."
                    )
                subprocess.run(
                    [
                        "git",
                        "-c",
                        "user.name=metadata-forge",
                        "-c",
                        "user.email=metadata-forge@local",
                        "commit",
                        "-m",
                        COMMIT_SUBJECT,
                    ],
                    cwd=publisher,
                    check=True,
                )
                run_git_transport(
                    ["push", "origin", "HEAD:master"],
                    cwd=str(publisher),
                )
                return _git_output(publisher, "rev-parse", "HEAD")
            except GitTransportError as error:
                if attempt == MAX_PUBLISH_ATTEMPTS:
                    raise
                print(
                    f"Retrying benchmark metrics publication after attempt "
                    f"{attempt} failed: {error}",
                    file=sys.stderr,
                )
            finally:
                if created:
                    _discard_publication_worktree(repository_root, publisher)
        raise BenchmarkError("Benchmark metrics publication exhausted retries.")
    finally:
        fcntl.flock(lock_handle.fileno(), fcntl.LOCK_UN)
        lock_handle.close()


def publish_workspace(
        workspace: Path,
        *,
        requested_status: str | None = None,
        exit_code: int | None = None,
        repository_root: Path = REPOSITORY_ROOT,
) -> dict[str, Any]:
    """Collect and idempotently publish one workspace's compact result."""
    workspace = workspace.resolve()
    run: dict[str, Any] = _read_json(_run_record_path(workspace))
    status = requested_status or run.get("requestedStatus") or "failure"
    known_exit = exit_code
    if known_exit is None:
        candidate = run.get("rheiExitCode")
        known_exit = candidate if type(candidate) is int else None
    result = _collect_result(workspace, status, known_exit)
    published_commit = _publish_result(repository_root.resolve(), workspace, result)
    marker = {
        "schemaVersion": "1.0.0",
        "runId": result["runId"],
        "publishedAt": (
            dt.datetime.now(dt.timezone.utc)
            .isoformat(timespec="seconds")
            .replace("+00:00", "Z")
        ),
        "repositoryCommit": published_commit,
        "resultPath": str(_metrics_relative_path(result["coordinate"])),
    }
    _write_json(_publication_marker_path(workspace), marker)
    print(
        f"Published benchmark result {result['runId']} at "
        f"{marker['resultPath']}."
    )
    return result


def convert_workspace(args: argparse.Namespace) -> None:
    """Write issue-compatible conversion artifacts without GitHub access."""
    workspace = Path(args.workspace).resolve()
    source_worktree = Path(args.source_worktree).resolve()
    runner_forge_path = Path(args.runner_forge_path).resolve()
    if _git_output(source_worktree, "rev-parse", "HEAD") != args.suite_commit:
        raise BenchmarkError(
            "Benchmark source worktree is not at the configured suite commit."
        )
    if _git_output(runner_forge_path.parent, "rev-parse", "HEAD") != args.runner_commit:
        raise BenchmarkError(
            "Benchmark runner checkout is not at the recorded runner commit."
        )
    group, artifact, _ = args.coordinate.split(":")
    test_dir = Path(
        resolve_test_dir(
            str(source_worktree),
            group,
            artifact,
            args.coordinate.rsplit(":", 1)[1],
        )
    )
    coverage_suite = test_dir / "code-coverage-improvement"
    if coverage_suite.exists():
        raise BenchmarkError(
            "The fixed benchmark input already contains a coverage suite: "
            f"{coverage_suite}"
        )
    work_path = source_worktree / "forge"
    conversion = {
        "coordinate": args.coordinate,
        "worktreePath": str(source_worktree),
        "workPath": str(work_path),
        "coverageSuiteAbsolutePath": str(coverage_suite),
        "coverageSuiteRepoRelativePath": str(
            coverage_suite.relative_to(source_worktree)
        ),
    }
    issue_dir = workspace / "runtime" / "code-coverage" / "issues"
    _write_json(issue_dir / "conversion.json", conversion)
    (issue_dir / "inventory.md").write_text(
        f"# Benchmark input\n\n- Coordinate: `{args.coordinate}`\n"
        f"- Suite commit: `{args.suite_commit}`\n",
        encoding="utf-8",
    )
    (issue_dir / "conversion.md").write_text(
        "# Benchmark conversion\n\n"
        f"- Coordinate: `{args.coordinate}`\n"
        f"- Worktree: `{source_worktree}`\n"
        f"- Work path: `{work_path}`\n"
        f"- Coverage suite: `{coverage_suite}`\n",
        encoding="utf-8",
    )
    run_identity = {
        "schemaVersion": "1.0.0",
        "runId": args.run_id,
        "startedAt": args.started_at,
        "benchmarkSuiteCommit": args.suite_commit,
        "runnerCommit": args.runner_commit,
        "coordinate": args.coordinate,
        "workspaceName": workspace.name,
        "agent": args.agent,
        "configuredModel": args.configured_model,
        "targetModel": args.target_model,
        "thinking": args.thinking,
        "checkedInAllMethods": args.checked_in_all_methods,
        "sourceWorktree": str(source_worktree),
        "runnerForgePath": str(runner_forge_path),
    }
    _ensure_run_record(workspace, run_identity)
    work_path_output = (
        workspace
        / "runtime"
        / "code-coverage"
        / "work"
        / "code-coverage-99000.code-coverage-convert.md"
    )
    work_path_output.parent.mkdir(parents=True, exist_ok=True)
    work_path_output.write_text(
        "# Benchmark conversion complete\n\n"
        "No GitHub issue or Project operation was performed.\n",
        encoding="utf-8",
    )


def _convert_parser(subparsers: Any) -> None:
    parser = subparsers.add_parser("convert")
    parser.add_argument("--workspace", required=True)
    parser.add_argument("--coordinate", required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--started-at", required=True)
    parser.add_argument("--suite-commit", required=True)
    parser.add_argument("--runner-commit", required=True)
    parser.add_argument("--source-worktree", required=True)
    parser.add_argument("--runner-forge-path", required=True)
    parser.add_argument("--agent", required=True, choices=("pi", "claude-code"))
    parser.add_argument("--configured-model", required=True)
    parser.add_argument("--target-model", required=True)
    parser.add_argument(
        "--thinking",
        required=True,
        choices=("medium", "high", "xhigh"),
    )
    parser.add_argument("--checked-in-all-methods", required=True, type=int)


def _run_parser(subparsers: Any) -> None:
    parser = subparsers.add_parser("run")
    parser.add_argument("--library-index", nargs="+", type=int)
    parser.add_argument("--agent", nargs="+")
    parser.add_argument("--model", nargs="+")
    parser.add_argument("--thinking", nargs="+")
    parser.add_argument(
        "--workspace-root",
        type=Path,
        default=DEFAULT_WORKSPACE_ROOT,
    )
    parser.add_argument("--dry-run", action="store_true")


def _publish_parser(subparsers: Any) -> None:
    parser = subparsers.add_parser("publish")
    parser.add_argument("--workspace", required=True, type=Path)
    parser.add_argument("--status", choices=("success", "failure"))
    parser.add_argument("--exit-code", type=int)


def _retry_parser(subparsers: Any) -> None:
    parser = subparsers.add_parser("retry-pending")
    parser.add_argument(
        "--workspace-root",
        type=Path,
        default=DEFAULT_WORKSPACE_ROOT,
    )


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="code_coverage_benchmark.py",
        description="Run or publish fixed code coverage benchmark cells.",
    )
    subparsers = parser.add_subparsers(dest="command", required=True)
    _run_parser(subparsers)
    _convert_parser(subparsers)
    _publish_parser(subparsers)
    _retry_parser(subparsers)
    return parser


def run_selected(args: argparse.Namespace) -> int:
    suite = load_suite()
    cells = expand_matrix(
        suite,
        library_indexes=args.library_index,
        agents=args.agent,
        models=args.model,
        thinking_levels=args.thinking,
    )
    print_matrix(cells)
    if args.dry_run:
        return 0
    _verify_preconditions(suite, cells)
    runner_commit = _runner_commit()
    workspace_root = args.workspace_root.resolve()
    failures = 0
    for cell in cells:
        workflow_succeeded, _ = _execute_cell(
            suite,
            cell,
            runner_commit,
            workspace_root,
        )
        if not workflow_succeeded:
            failures += 1
    return 1 if failures else 0


def retry_pending(args: argparse.Namespace) -> int:
    workspace_root = args.workspace_root.resolve()
    run_records = sorted(workspace_root.glob(f"*/code-coverage-99000/{RUN_RECORD}"))
    pending = [
        path.parents[3]
        for path in run_records
        if not _publication_marker_path(path.parents[3]).is_file()
    ]
    print(f"Found {len(pending)} unpublished benchmark workspace(s).")
    if not pending:
        return 0
    failures = 0
    for workspace in pending:
        try:
            publish_workspace(workspace)
            run = _read_json(_run_record_path(workspace))
            source = Path(run["sourceWorktree"])
            if source.exists():
                _discard_source_worktree(source)
        except (BenchmarkError, OSError, subprocess.SubprocessError) as error:
            failures += 1
            print(
                f"ERROR: Could not publish {workspace}: {error}",
                file=sys.stderr,
            )
    return 1 if failures else 0


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        if args.command == "run":
            return run_selected(args)
        if args.command == "convert":
            convert_workspace(args)
            return 0
        if args.command == "publish":
            publish_workspace(
                args.workspace,
                requested_status=args.status,
                exit_code=args.exit_code,
            )
            return 0
        if args.command == "retry-pending":
            return retry_pending(args)
        raise BenchmarkError(f"Unsupported command: {args.command}")
    except (
            BenchmarkError,
            GitTransportError,
            OSError,
            subprocess.SubprocessError,
            ValueError,
    ) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
