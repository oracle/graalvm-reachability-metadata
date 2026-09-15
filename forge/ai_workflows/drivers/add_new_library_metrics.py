# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Metrics publication for the new-library support driver.

Resolves the metrics JSON path per execution mode and writes run or benchmark
metrics (§AR-forge-driver-finalization). The driver itself lives in
`add_new_library_support.py`.
"""

import json
import os
import sys

from ai_workflows.core.workflow_strategy import (
    RUN_STATUS_FAILURE,
    SUCCESS_WITH_INTERVENTION_STATUS,
)
from utility_scripts import metrics_writer
from utility_scripts.schema_validator import validate_benchmark_run_metrics
from utility_scripts.stage_logger import log_detail, log_stage

METRICS_TASK_TYPE = "add_new_library_support"


def resolve_add_new_library_support_metrics_json(
        run_metrics: dict,
        metrics_repo_dir: str,
        metrics_repo_root: str | None,
        is_benchmark_mode: bool,
) -> str:
    """Resolve the metrics JSON path for the current execution mode."""
    if is_benchmark_mode:
        return os.path.join(metrics_repo_dir, f"{METRICS_TASK_TYPE}.json")
    return metrics_writer.resolve_workflow_metrics_json(
        run_metrics,
        metrics_repo_dir,
        metrics_repo_root,
        METRICS_TASK_TYPE,
    )


def _build_benchmark_metrics_entry(run_metrics: dict) -> dict:
    """Return a benchmark metrics entry without workflow artifact paths."""
    benchmark_entry = dict(run_metrics)
    benchmark_entry.pop("artifacts", None)
    return benchmark_entry


def write_add_new_library_support_metrics(run_metrics, metrics_repo_dir, is_benchmark_mode, package, artifact,
                                          library_version, metrics_repo_root=None):
    """Write or update add_new_library_support metrics depending on the execution mode.

    Non-benchmark runs publish through the shared workflow metrics writer
    (§AR-forge-driver-finalization); benchmark mode updates the last benchmark
    record instead of appending a run entry.
    """
    if not is_benchmark_mode:
        log_detail("schema-validation", "Validating schema")
        metrics_writer.write_workflow_run_metrics(run_metrics, metrics_repo_dir, metrics_repo_root, METRICS_TASK_TYPE)
        log_detail("schema-validation", "Schema validated")
        return

    metrics_json = os.path.join(metrics_repo_dir, f"{METRICS_TASK_TYPE}.json")
    if not os.path.isfile(metrics_json):
        print(f"ERROR: Benchmark metrics file not found: {metrics_json}")
        sys.exit(1)

    with open(metrics_json, "r", encoding="utf-8") as f:
        data = json.load(f)

    benchmark_obj = data[-1]
    metrics_array = benchmark_obj.get("metrics")
    library_id = f"{package}:{artifact}:{library_version}"

    updated = False
    for index, item in enumerate(metrics_array):
        if item.get("library") == library_id:
            metrics_array[index] = _build_benchmark_metrics_entry(run_metrics)
            updated = True
            break

    if not updated:
        print(f"ERROR: No benchmark metrics entry found for library {library_id}.")
        sys.exit(1)

    with open(metrics_json, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")

    log_detail("schema-validation", "Validating schema")
    validate_benchmark_run_metrics(metrics_json)
    log_detail("schema-validation", "Schema validated")


def _should_create_failure_run_metrics(
        workflow_status: str,
        unittest_number: int,
        scaffold_placeholder_quality_gate_failed: bool,
) -> bool:
    """Return True when normal coverage/metadata metrics must not be collected."""
    return (
        scaffold_placeholder_quality_gate_failed
        or workflow_status == RUN_STATUS_FAILURE
        or (unittest_number == 0 and workflow_status != SUCCESS_WITH_INTERVENTION_STATUS)
    )


def run_benchmark_mode_generation(strategy_obj, library: str) -> bool:
    """Run benchmark-mode metadata and stats generation instead of finalize_run."""
    log_stage("generate-metadata", f"Benchmark mode: running generateMetadata and generateLibraryStats for {library}")
    if not strategy_obj._run_gradle_command([
        "./gradlew",
        "generateMetadata",
        f"-Pcoordinates={library}",
        "--agentAllowedPackages=fromJar",
    ]):
        return False
    if not strategy_obj._run_gradle_command([
        "./gradlew",
        "generateLibraryStats",
        f"-Pcoordinates={library}",
    ]):
        return False
    return bool(strategy_obj._commit_library_iteration())
