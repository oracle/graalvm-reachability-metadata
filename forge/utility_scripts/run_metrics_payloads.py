# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Assemble the per-run metrics record from measured counts and token usage.

The record is what every run persists for its library version (§FS-forge-run-metrics).
"""

import datetime as dt


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


