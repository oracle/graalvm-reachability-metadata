# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Compact metrics extraction for one benchmark workspace.

Coverage, token, and pass accounting distilled from a workspace's JaCoCo
reports, final metrics, and Rhei invocation records into one immutable
result record (§FS-code-coverage-benchmarking.3).
"""

from __future__ import annotations

import datetime as dt
import json
import re
from pathlib import Path
from typing import Any

from jsonschema import ValidationError

from benchmarks.code_coverage_benchmark_common import (
    FORGE_ROOT,
    RESULT_SCHEMA_PATH,
    BenchmarkError,
    read_json,
    result_record_path,
    run_record_path,
    validate,
    write_json,
)
from utility_scripts.code_coverage_jacoco import load_jacoco_method_coverage

FINAL_METRICS_SCHEMA_PATH = (
    FORGE_ROOT / "schemas" / "code_coverage_final_metrics_schema.json"
)
RESULT_SCHEMA_VERSION = "1.1.0"


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


def final_metrics_path(workspace: Path) -> Path:
    return (
        workspace
        / "runtime"
        / "code-coverage"
        / "finalization"
        / "final-metrics.json"
    )


def _coverage_from_final_metrics(
        workspace: Path,
) -> tuple[dict[str, Any], dict[str, Any], dict[str, Any]] | None:
    path = final_metrics_path(workspace)
    if not path.is_file():
        return None
    try:
        metrics: dict[str, Any] = read_json(path)
        validate(metrics, FINAL_METRICS_SCHEMA_PATH)
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


def _naive_coverage_from_reports(workspace: Path) -> dict[str, Any] | None:
    """Baseline-arm coverage from the naive loop's own JaCoCo copies.

    The naive arm has no finalization; the loop's last report is the final
    metric and publication reads it directly (§AR-code-coverage-benchmarking.3).
    """
    validation = workspace / "runtime" / "code-coverage" / "validation"
    paths = _iteration_files(validation, "jacoco-naive", ".xml")
    snapshots = [
        snapshot
        for snapshot in (_jacoco_snapshot(path) for path in paths)
        if snapshot is not None
    ]
    if not snapshots:
        return None
    all_methods = snapshots[0][0]

    def covered(snapshot: tuple[int, int]) -> int | None:
        return snapshot[1] if snapshot[0] == all_methods else None

    return _phase_coverage(
        covered(snapshots[0]), covered(snapshots[-1]), all_methods
    )


def _load_invocations(workspace: Path) -> tuple[list[dict[str, Any]], bool]:
    directory = workspace / "runtime" / "accounting" / "invocations"
    if not directory.is_dir():
        return [], False
    invocations: list[dict[str, Any]] = []
    for path in sorted(directory.glob("*.json")):
        try:
            value = read_json(path)
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


def _ordinary_input(
        total_input: int | None,
        cached_read: int | None,
        cache_write: int | None,
) -> int | None:
    """Input tokens with the cached classes removed.

    Rhei reports `tokens.input.total` inclusive of the cached classes on newer
    runtimes and exclusive of them on older ones, so copying it through makes
    `input` mean one thing in some records and another in the rest. Ordinary
    input must stay disjoint from cached input either way
    (§FS-code-coverage-benchmarking.4), so the cached classes are subtracted
    only from a total large enough to contain them.
    """
    if total_input is None:
        return None
    cached = (cached_read or 0) + (cache_write or 0)
    return total_input - cached if total_input >= cached else total_input


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
            "cachedInputWrite": empty_value,
            "output": empty_value,
        }

    def total(*keys: str) -> int | None:
        values = [_nested_token(invocation, *keys) for invocation in relevant]
        if any(value is None for value in values):
            return None
        return sum(value for value in values if value is not None)

    cached_read = total("tokens", "input", "cached_read")
    cache_write = total("tokens", "input", "cache_write")
    return {
        "input": _ordinary_input(
            total("tokens", "input", "total"), cached_read, cache_write
        ),
        "cachedInputRead": cached_read,
        # A separately billed input class on some providers and zero on the
        # rest, so omitting it understates exactly one side of a comparison
        # (§FS-code-coverage-benchmarking.3).
        "cachedInputWrite": cache_write,
        "output": total("tokens", "output", "total"),
    }


def _stop_passes(workspace: Path, phase: str) -> int | None:
    final_metrics = final_metrics_path(workspace)
    if final_metrics.is_file():
        try:
            for decision in read_json(final_metrics).get("stopDecisions", []):
                if decision.get("phase") == phase and type(decision.get("passes")) is int:
                    return int(decision["passes"])
        except (OSError, ValueError, TypeError):
            pass
    directory = "discovery" if phase == "deep" else "validation"
    path = (
        workspace
        / "runtime"
        / "code-coverage"
        / directory
        / f"{phase}-stop-decision.json"
    )
    if path.is_file():
        try:
            passes = read_json(path).get("passes")
            return passes if type(passes) is int and passes >= 0 else None
        except (OSError, ValueError, TypeError):
            return None
    return None


def _sum_nullable(left: int | None, right: int | None) -> int | None:
    if left is None or right is None:
        return None
    return left + right


def failure_phase(workspace: Path) -> str | None:
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
        "naive-coverage": "naive",
        "finalization": "finalization",
        "benchmark-publication": "benchmark-publication",
    }
    return mapping.get(task)


def collect_result(
        workspace: Path,
        requested_status: str | None,
        exit_code: int | None,
) -> dict[str, Any]:
    # A written record is immutable for its runId, success or failure
    # (§FS-code-coverage-benchmarking.3).
    result_path = result_record_path(workspace)
    if result_path.is_file():
        existing: dict[str, Any] = read_json(result_path)
        validate([existing], RESULT_SCHEMA_PATH)
        return existing
    if requested_status is None:
        raise BenchmarkError(
            "Publishing without a recorded result requires an explicit --status."
        )
    run: dict[str, Any] = read_json(run_record_path(workspace))
    strategy = str(run.get("strategy", "guided"))
    if strategy == "naive":
        # The naive arm has no finalization: its last JaCoCo report is the
        # final metric (§AR-code-coverage-benchmarking.3).
        naive_coverage = _naive_coverage_from_reports(workspace)
        if requested_status == "success" and naive_coverage is None:
            raise BenchmarkError(
                "A successful naive benchmark must have naive JaCoCo reports."
            )
        empty = _phase_coverage(None, None, None)
        api_coverage, deep_coverage = dict(empty), dict(empty)
        total_coverage = naive_coverage or dict(empty)
    else:
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
    naive = (
        phase_result("naive", total_coverage) if strategy == "naive" else None
    )
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
    if naive is not None:
        total = {
            "coverPasses": naive["coverPasses"],
            "fixInvocations": naive["fixInvocations"],
            "tokens": dict(naive["tokens"]),
            "coverage": total_coverage,
        }
    else:
        total_tokens = {
            key: _sum_nullable(api["tokens"][key], deep["tokens"][key])
            for key in ("input", "cachedInputRead", "cachedInputWrite", "output")
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
        "strategy": strategy,
        "status": status,
        "failure": None if status == "success" else {
            "phase": failure_phase(workspace),
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
        "naive": naive,
        "total": total,
    }
    validate([result], RESULT_SCHEMA_PATH)
    write_json(result_path, result)
    return result
