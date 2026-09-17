# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Assemble run-metrics payloads for workflow drivers from collected measurements and token usage."""

import datetime as dt
import os

from utility_scripts.library_measurements import (
    collect_version_coverage_metrics,
    count_generated_loc,
    count_metadata_entries,
    count_test_only_metadata_entries,
    load_library_stats_snapshot,
    resolve_metadata_artifact_path,
    resolve_test_version_dir,
)
from utility_scripts.source_context import resolve_test_source_layout
from utility_scripts.stage_logger import log_plain_detail
from utility_scripts.strategy_loader import load_strategy_by_name
from utility_scripts.token_costs import collect_token_usage_metrics


def resolve_agent(strategy_name: str | None) -> str | None:
    """Resolve the configured agent for a strategy."""
    if not strategy_name:
        return None

    strategy = load_strategy_by_name(strategy_name)
    if strategy is None:
        return None

    return strategy.get("agent")


def collect_and_print_metrics(
        repo_path: str,
        package: str,
        artifact: str,
        library_version: str,
        agent,
        model_name: str | None,
        global_iterations: int,
        starting_commit: str | None = None,
):
    """
    Compute metrics and print the same output produced previously.

    Returns a dict with keys:
      - coverage_percent (float)
      - input_tokens_used (int)
      - cached_input_tokens_used (int | None)
      - output_tokens_used (int)
      - input_cost_usd (float)
      - cached_input_cost_usd (float)
      - output_cost_usd (float)
      - generated_loc (int)
      - total_entries (int)
    """
    test_version = resolve_test_version_dir(repo_path, package, artifact, library_version)
    coordinate = f"{package}:{artifact}:{library_version}"
    module_dir = os.path.join(repo_path, "tests", "src", package, artifact, test_version)
    tests_root = resolve_test_source_layout(repo_path, coordinate, module_dir).source_root
    coverage_percent, lines_covered = collect_version_coverage_metrics(repo_path, package, artifact, library_version)

    # Compute and print code generation metrics
    token_metrics = collect_token_usage_metrics(agent, model_name)
    input_tokens_used = token_metrics["input_tokens_used"]
    cached_input_tokens_used = token_metrics["cached_input_tokens_used"]
    output_tokens_used = token_metrics["output_tokens_used"]
    total_cost_usd = token_metrics["cost_usd"]

    # Count generated LOC
    generated_loc = count_generated_loc(tests_root)

    # Calculating generated metadata entries
    total_entries = count_metadata_entries(repo_path, package, artifact, library_version)
    test_only_metadata_entries = count_test_only_metadata_entries(
        repo_path,
        package,
        artifact,
        library_version,
        base_commit=starting_commit,
    )

    cached_tokens_suffix = ""
    if cached_input_tokens_used is not None:
        cached_tokens_suffix = f" Cached Input Tokens: {cached_input_tokens_used}"

    test_metadata_suffix = ""
    if test_only_metadata_entries > 0:
        test_metadata_suffix = f" Test-Only Metadata Entries: {test_only_metadata_entries}"

    # Persisted metrics stay available without becoming finalization progress.
    # §FS-forge-run-output-legibility.5
    log_plain_detail(
        f"Iterations: {global_iterations}  Input Tokens: {input_tokens_used}"
        f"{cached_tokens_suffix} Output Tokens: {output_tokens_used} "
        f"Metadata Entries: {total_entries}{test_metadata_suffix} "
        f"Coverage: {coverage_percent:.2f}% Generated LOC: {generated_loc} "
        f"Cost: {total_cost_usd:.4f} Library lines covered: {lines_covered}",
    )

    metrics = {
        "coverage_percent": coverage_percent,
        "lines_covered": lines_covered,
        "input_tokens_used": input_tokens_used,
        "output_tokens_used": output_tokens_used,
        "input_cost_usd": token_metrics["input_cost_usd"],
        "cached_input_cost_usd": token_metrics["cached_input_cost_usd"],
        "output_cost_usd": token_metrics["output_cost_usd"],
        "generated_loc": generated_loc,
        "total_entries": total_entries,
        "cost_usd": total_cost_usd,
    }
    if test_only_metadata_entries > 0:
        metrics["test_only_metadata_entries"] = test_only_metadata_entries
    if cached_input_tokens_used is not None:
        metrics["cached_input_tokens_used"] = cached_input_tokens_used
    return metrics


def build_run_metrics_dict(
        package: str,
        artifact: str,
        library_version: str,
        strategy_name: str,
        status: str,
        global_iterations: int,
        input_tokens_used: int,
        output_tokens_used: int,
        cost_usd: float,
        lines_covered: int,
        coverage_percent: float,
        total_entries: int,
        metadata_file: str,
        test_only_metadata_entries: int = 0,
        generated_loc: int | None = None,
        cached_input_tokens_used: int | None = None,
        starting_commit: str | None = None,
        ending_commit: str | None = None,
        previous_library: str | None = None,
        previous_library_metadata_entries: int | None = None,
        previous_library_test_only_metadata_entries: int | None = None,
        previous_library_coverage_percent: float | None = None,
        agent_name: str | None = None,
        model_name: str | None = None,
        stats: dict | None = None,
        previous_library_stats: dict | None = None,
        post_generation_intervention: dict | None = None,
        library_preparation_preflight: dict | None = None,
):
    """Assemble the run_metrics dict."""
    metrics = {
        "input_tokens_used": input_tokens_used,
    }
    if cached_input_tokens_used is not None:
        metrics["cached_input_tokens_used"] = cached_input_tokens_used
    metrics["output_tokens_used"] = output_tokens_used
    metrics["iterations"] = global_iterations
    metrics["cost_usd"] = round(cost_usd, 4)
    if generated_loc is not None:
        metrics["generated_loc"] = generated_loc
    metrics["tested_library_loc"] = lines_covered
    metrics["metadata_entries"] = total_entries
    if test_only_metadata_entries > 0:
        metrics["test_only_metadata_entries"] = test_only_metadata_entries
    if previous_library_metadata_entries is not None:
        metrics["previous_library_metadata_entries"] = previous_library_metadata_entries
    if previous_library_test_only_metadata_entries is not None and previous_library_test_only_metadata_entries > 0:
        metrics["previous_library_test_only_metadata_entries"] = previous_library_test_only_metadata_entries
    metrics["code_coverage_percent"] = round(coverage_percent, 2)
    if previous_library_coverage_percent is not None:
        metrics["previous_library_coverage_percent"] = round(previous_library_coverage_percent, 2)

    run_metrics = {
        "timestamp": dt.datetime.utcnow().isoformat(timespec="microseconds") + "Z",
        "library": f"{package}:{artifact}:{library_version}",
    }
    if starting_commit is not None:
        run_metrics["starting_commit"] = starting_commit
    if ending_commit is not None:
        run_metrics["ending_commit"] = ending_commit
    if previous_library is not None:
        run_metrics["previous_library"] = previous_library
    run_metrics["strategy_name"] = strategy_name
    if agent_name is not None:
        run_metrics["agent"] = agent_name
    if model_name is not None:
        run_metrics["model"] = model_name
    run_metrics["status"] = status
    if stats is not None:
        run_metrics["stats"] = stats
    if previous_library_stats is not None:
        run_metrics["previous_library_stats"] = previous_library_stats
    if post_generation_intervention is not None:
        run_metrics["post_generation_intervention"] = post_generation_intervention
    if library_preparation_preflight is not None:
        run_metrics["library_preparation_preflight"] = library_preparation_preflight
    run_metrics["metrics"] = metrics
    run_metrics["artifacts"] = {
        "metadata_file": metadata_file,
    }

    return run_metrics


def collect_base_metrics(
        repo_path,
        package,
        artifact,
        library_version,
        agent,
        model_name,
        global_iterations,
        starting_commit: str | None = None,
):
    """Collect metrics and compute cost. Returns a flat dict of metric values."""
    metrics = collect_and_print_metrics(
        repo_path=repo_path,
        package=package,
        artifact=artifact,
        library_version=library_version,
        agent=agent,
        model_name=model_name,
        global_iterations=global_iterations,
        starting_commit=starting_commit,
    )
    return metrics


def create_run_metrics_output_json(
        repo_path,
        package,
        artifact,
        library_version,
        agent,
        model_name,
        global_iterations,
        strategy_name,
        status,
        starting_commit: str | None = None,
        ending_commit: str | None = None,
        post_generation_intervention: dict | None = None,
        library_preparation_preflight: dict | None = None,
):
    """
    Build a run_metrics dict using collected metrics.
    """
    metrics = collect_base_metrics(
        repo_path,
        package,
        artifact,
        library_version,
        agent,
        model_name,
        global_iterations,
        starting_commit=starting_commit,
    )
    metadata_file = resolve_metadata_artifact_path(repo_path, package, artifact, library_version)
    agent_name = resolve_agent(strategy_name)
    stats = load_library_stats_snapshot(repo_path, package, artifact, library_version)

    return build_run_metrics_dict(
        package=package,
        artifact=artifact,
        library_version=library_version,
        strategy_name=strategy_name,
        agent_name=agent_name,
        model_name=model_name,
        status=status,
        global_iterations=global_iterations,
        input_tokens_used=metrics.get("input_tokens_used", 0),
        output_tokens_used=metrics.get("output_tokens_used", 0),
        cost_usd=metrics.get("cost_usd"),
        generated_loc=metrics.get("generated_loc", 0),
        cached_input_tokens_used=metrics.get("cached_input_tokens_used"),
        starting_commit=starting_commit,
        ending_commit=ending_commit,
        lines_covered=metrics.get("lines_covered", 0),
        coverage_percent=metrics.get("coverage_percent", 0.0),
        total_entries=metrics.get("total_entries", 0),
        test_only_metadata_entries=metrics.get("test_only_metadata_entries", 0),
        metadata_file=metadata_file,
        stats=stats,
        post_generation_intervention=post_generation_intervention,
        library_preparation_preflight=library_preparation_preflight,
    )


def create_javac_fix_run_metrics_output_json(
        repo_path,
        package,
        artifact,
        previous_library_version,
        new_library_version,
        agent,
        model_name,
        global_iterations,
        strategy_name,
        status,
        starting_commit: str | None = None,
        ending_commit: str | None = None,
        post_generation_intervention: dict | None = None,
        library_preparation_preflight: dict | None = None,
):
    """Build run metrics for fix_javac_fail workflow including previous-version metrics."""
    metrics = collect_base_metrics(
        repo_path,
        package,
        artifact,
        new_library_version,
        agent,
        model_name,
        global_iterations,
        starting_commit=starting_commit,
    )
    metadata_file = resolve_metadata_artifact_path(repo_path, package, artifact, new_library_version)

    previous_coverage_percent, _ = collect_version_coverage_metrics(
        repo_path=repo_path,
        package=package,
        artifact=artifact,
        library_version=previous_library_version,
    )
    previous_entries = count_metadata_entries(repo_path, package, artifact, previous_library_version)
    previous_test_entries = count_test_only_metadata_entries(repo_path, package, artifact, previous_library_version)
    agent_name = resolve_agent(strategy_name)
    stats = load_library_stats_snapshot(repo_path, package, artifact, new_library_version)
    previous_stats = load_library_stats_snapshot(repo_path, package, artifact, previous_library_version)

    return build_run_metrics_dict(
        package=package,
        artifact=artifact,
        library_version=new_library_version,
        strategy_name=strategy_name,
        agent_name=agent_name,
        model_name=model_name,
        status=status,
        global_iterations=global_iterations,
        input_tokens_used=metrics.get("input_tokens_used", 0),
        output_tokens_used=metrics.get("output_tokens_used", 0),
        cost_usd=metrics.get("cost_usd", 0),
        cached_input_tokens_used=metrics.get("cached_input_tokens_used"),
        starting_commit=starting_commit,
        ending_commit=ending_commit,
        lines_covered=metrics.get("lines_covered", 0),
        coverage_percent=metrics.get("coverage_percent", 0.0),
        total_entries=metrics.get("total_entries", 0),
        test_only_metadata_entries=metrics.get("test_only_metadata_entries", 0),
        metadata_file=metadata_file,
        previous_library=f"{package}:{artifact}:{previous_library_version}",
        previous_library_metadata_entries=previous_entries,
        previous_library_test_only_metadata_entries=previous_test_entries,
        previous_library_coverage_percent=previous_coverage_percent,
        stats=stats,
        previous_library_stats=previous_stats,
        post_generation_intervention=post_generation_intervention,
        library_preparation_preflight=library_preparation_preflight,
    )


# java-run fix uses the same metrics shape as javac fix
create_java_run_fix_run_metrics_output_json = create_javac_fix_run_metrics_output_json


def create_failure_run_metrics_output(
        package,
        artifact,
        library_version,
        agent,
        model_name,
        global_iterations,
        strategy_name,
        starting_commit: str | None = None,
        ending_commit: str | None = None,
        library_preparation_preflight: dict | None = None,
):
    """Builds failure metrics when no valid unit tests were generated."""
    token_metrics = collect_token_usage_metrics(agent, model_name)
    agent_name = resolve_agent(strategy_name)

    return build_run_metrics_dict(
        package=package,
        artifact=artifact,
        library_version=library_version,
        strategy_name=strategy_name,
        agent_name=agent_name,
        model_name=model_name,
        status="failure",
        global_iterations=global_iterations,
        input_tokens_used=token_metrics.get("input_tokens_used", 0),
        output_tokens_used=token_metrics.get("output_tokens_used", 0),
        cost_usd=token_metrics.get("cost_usd", 0.0),
        generated_loc=0,
        cached_input_tokens_used=token_metrics.get("cached_input_tokens_used"),
        starting_commit=starting_commit,
        ending_commit=ending_commit,
        lines_covered=0,
        coverage_percent=0.0,
        total_entries=0,
        metadata_file="None",
        library_preparation_preflight=library_preparation_preflight,
    )


def collect_new_library_support_quality_issues(run_metrics: dict) -> list[str]:
    """Return validation failures for a new-library-support run that is not meaningful enough for a PR."""
    status = run_metrics.get("status")
    if status not in {"success", "success_with_intervention", "chunk_ready"}:
        return [f"workflow status is `{status or 'unknown'}`"]

    metrics = run_metrics.get("metrics") or {}

    code_coverage_percent = float(metrics.get("code_coverage_percent", 0.0) or 0.0)
    if code_coverage_percent <= 0.0:
        return ["library coverage percentage is zero"]

    return []
