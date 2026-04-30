# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""
Helper module for collecting AI generated metrics, creating JSON objects and appending them to output.
"""

import json
import datetime as dt
import os
import subprocess
import sys

import utility_scripts.count_reachability_entries as reachability_metadata_count
import utility_scripts.count_native_image_config_entries as legacy_metadata_count
from utility_scripts.library_stats import load_library_stats_entry
from utility_scripts.source_context import resolve_test_source_layout
from utility_scripts.strategy_loader import load_strategy_by_name
from git_scripts.common_git import git_remote_exists

# Rates are attached to the workflow model name.
DEFAULT_INPUT_RATE_PER_1M = 3.00
DEFAULT_CACHED_INPUT_RATE_PER_1M = 3.00
DEFAULT_OUTPUT_RATE_PER_1M = 12.00

INPUT_TOKEN_RATE_PER_1M_BY_MODEL = {
    "oca/gpt-5.4": 2.50,
    "oca/gpt-5.5": 5.00,
}

CACHED_INPUT_TOKEN_RATE_PER_1M_BY_MODEL = {
    "oca/gpt-5.4": 0.25,
    "oca/gpt-5.5": 0.50,
}

OUTPUT_TOKEN_RATE_PER_1M_BY_MODEL = {
    "oca/gpt-5.4": 15.00,
    "oca/gpt-5.5": 30.00,
}


def calc_token_cost(token_count: int, rate_per_1m: float) -> float:
    return round((token_count / 1_000_000.0) * rate_per_1m, 4)


def calc_input_cost(input_tokens: int, rate_per_1m: float = DEFAULT_INPUT_RATE_PER_1M) -> float:
    return calc_token_cost(input_tokens, rate_per_1m)


def calc_output_cost(output_tokens: int, rate_per_1m: float = DEFAULT_OUTPUT_RATE_PER_1M) -> float:
    return calc_token_cost(output_tokens, rate_per_1m)


def _get_model_rate(model_name: str | None, rates_by_model: dict[str, float], default_rate: float | None) -> float | None:
    if model_name and model_name in rates_by_model:
        return rates_by_model[model_name]
    return default_rate


def resolve_agent(strategy_name: str | None) -> str | None:
    """Resolve the configured agent for a strategy."""
    if not strategy_name:
        return None

    strategy = load_strategy_by_name(strategy_name)
    if strategy is None:
        return None

    return strategy.get("agent")


def _is_valid_dynamic_access_entry(entry) -> bool:
    """Return True when the dynamic-access stats entry matches the expected object shape."""
    if not isinstance(entry, dict):
        return False

    required_keys = {"coveredCalls", "totalCalls", "coverageRatio", "breakdown"}
    return required_keys.issubset(entry.keys()) and isinstance(entry.get("breakdown"), dict)


def _is_valid_library_coverage_entry(entry) -> bool:
    """Return True when the library-coverage entry matches the expected object shape."""
    if not isinstance(entry, dict):
        return False

    required_keys = {"covered", "total", "ratio"}
    return required_keys.issubset(entry.keys())


def _sanitize_library_coverage(library_coverage) -> dict | None:
    """Drop invalid coverage counters so metrics output remains schema-compatible."""
    if not isinstance(library_coverage, dict):
        return None

    sanitized = {}
    for metric in ("instruction", "line", "method"):
        entry = library_coverage.get(metric)
        if _is_valid_library_coverage_entry(entry):
            sanitized[metric] = entry

    return sanitized or None


def _sanitize_stats_snapshot(version_entry) -> dict | None:
    """Normalize a stats snapshot loaded from exploded stats files for metrics serialization."""
    if not isinstance(version_entry, dict):
        return None

    version = version_entry.get("version")
    if not isinstance(version, str) or not version:
        return None

    sanitized = {"version": version}

    dynamic_access = version_entry.get("dynamicAccess")
    if _is_valid_dynamic_access_entry(dynamic_access):
        sanitized["dynamicAccess"] = dynamic_access

    library_coverage = _sanitize_library_coverage(version_entry.get("libraryCoverage"))
    if library_coverage is not None:
        sanitized["libraryCoverage"] = library_coverage

    return sanitized


def load_library_stats_snapshot(repo_path: str, package: str, artifact: str, library_version: str) -> dict | None:
    """Load the matching version entry from exploded stats files if available."""
    return _sanitize_stats_snapshot(load_library_stats_entry(repo_path, package, artifact, library_version))


def _load_raw_library_stats_version_entry(repo_path: str, package: str, artifact: str, library_version: str) -> dict | None:
    """Load the raw matching version entry from exploded stats files if available."""
    return load_library_stats_entry(repo_path, package, artifact, library_version)


def _stats_coverage_metrics(repo_path: str, package: str, artifact: str, library_version: str) -> tuple[float, int] | None:
    """Return coverage metrics from exploded stats files when available."""
    version_entry = _load_raw_library_stats_version_entry(repo_path, package, artifact, library_version)
    if not isinstance(version_entry, dict):
        return None

    library_coverage = version_entry.get("libraryCoverage")
    if not isinstance(library_coverage, dict):
        return None

    line_coverage = library_coverage.get("line")
    if isinstance(line_coverage, dict):
        ratio = line_coverage.get("ratio")
        covered = line_coverage.get("covered")
        if isinstance(ratio, (int, float)) and isinstance(covered, int):
            return float(ratio) * 100.0, covered

    if line_coverage != "N/A":
        return None

    instruction_coverage = library_coverage.get("instruction")
    if instruction_coverage == "N/A":
        return 100.0, 0
    if isinstance(instruction_coverage, dict):
        ratio = instruction_coverage.get("ratio")
        if isinstance(ratio, (int, float)):
            return float(ratio) * 100.0, 0
    return None


def collect_token_usage_metrics(agent, model_name: str | None) -> dict[str, int | float | None]:
    input_tokens_used = int(getattr(agent, "total_tokens_sent", 0) or 0)
    output_tokens_used = int(getattr(agent, "total_tokens_received", 0) or 0)
    cached_input_tokens_used = getattr(agent, "cached_input_tokens_used", None)
    if cached_input_tokens_used is not None:
        cached_input_tokens_used = int(cached_input_tokens_used or 0)

    input_rate = _get_model_rate(model_name, INPUT_TOKEN_RATE_PER_1M_BY_MODEL, DEFAULT_INPUT_RATE_PER_1M)
    output_rate = _get_model_rate(model_name, OUTPUT_TOKEN_RATE_PER_1M_BY_MODEL, DEFAULT_OUTPUT_RATE_PER_1M)
    cached_input_rate = _get_model_rate(
        model_name,
        CACHED_INPUT_TOKEN_RATE_PER_1M_BY_MODEL,
        DEFAULT_CACHED_INPUT_RATE_PER_1M,
    )

    billable_input_tokens = input_tokens_used
    cached_input_cost_usd = 0.0
    if cached_input_tokens_used is not None:
        billable_input_tokens = max(input_tokens_used - cached_input_tokens_used, 0)
        cached_input_cost_usd = calc_input_cost(
            cached_input_tokens_used,
            cached_input_rate or DEFAULT_CACHED_INPUT_RATE_PER_1M,
        )

    input_cost_usd = calc_input_cost(billable_input_tokens, input_rate or DEFAULT_INPUT_RATE_PER_1M)
    output_cost_usd = calc_output_cost(output_tokens_used, output_rate or DEFAULT_OUTPUT_RATE_PER_1M)

    return {
        "input_tokens_used": input_tokens_used,
        "cached_input_tokens_used": cached_input_tokens_used,
        "output_tokens_used": output_tokens_used,
        "input_cost_usd": input_cost_usd,
        "cached_input_cost_usd": cached_input_cost_usd,
        "output_cost_usd": output_cost_usd,
        "cost_usd": round(input_cost_usd + cached_input_cost_usd + output_cost_usd, 4),
    }


def count_generated_loc(root_dir: str) -> int:
    if not root_dir or not os.path.isdir(root_dir):
        return 0
    total = 0
    for dirpath, _, filenames in os.walk(root_dir):
        for fname in filenames:
            if not _is_test_source_file(fname):
                continue
            fpath = os.path.join(dirpath, fname)
            with open(fpath, "r", encoding="utf-8", errors="ignore") as f:
                for line in f:
                    if line.strip():
                        total += 1
    return total


def count_metadata_entries(repo_path: str, package: str, artifact: str, library_version: str):
    metadata_dir = os.path.join(repo_path, "metadata", package, artifact, library_version)
    reach_json = os.path.join(metadata_dir, "reachability-metadata.json")

    if os.path.isfile(reach_json):
        counts = reachability_metadata_count.count_reachability_file(reach_json)
        return int(counts.get("total", 0))

    json_files = legacy_metadata_count.find_json_files([metadata_dir])
    total = 0
    for path in json_files:
        total += int(legacy_metadata_count.count_entries_in_file(path))
    return total


def count_test_only_metadata_entries(repo_path: str, package: str, artifact: str, library_version: str) -> int:
    """Count test-only reachability metadata entries for a library version."""
    test_version = _resolve_test_version_dir(repo_path, package, artifact, library_version)
    reach_json = os.path.join(
        repo_path,
        "tests",
        "src",
        package,
        artifact,
        test_version,
        "src",
        "test",
        "resources",
        "META-INF",
        "native-image",
        "reachability-metadata.json",
    )
    if not os.path.isfile(reach_json):
        return 0

    counts = reachability_metadata_count.count_reachability_file(reach_json)
    return int(counts.get("total", 0))


def _get_repo_root() -> str:
    # repo root is two directories up from this file
    return os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def _resolve_test_version_dir(repo_path: str, package: str, artifact: str, library_version: str) -> str:
    """Resolve the tests/src directory name for a library version using metadata index.json."""
    index_path = os.path.join(repo_path, "metadata", package, artifact, "index.json")
    if os.path.isfile(index_path):
        try:
            with open(index_path, "r", encoding="utf-8") as f:
                entries = json.load(f)
            for entry in entries:
                tested = entry.get("tested-versions") or []
                metadata_version = entry.get("metadata-version")
                if library_version != metadata_version and library_version not in tested:
                    continue

                test_version = entry.get("test-version") or metadata_version
                if not test_version:
                    break

                indexed_module_dir = os.path.join(repo_path, "tests", "src", package, artifact, str(test_version))
                if os.path.isdir(indexed_module_dir):
                    return str(test_version)

                print(
                    f"ERROR: Test directory specified in index.json (`{indexed_module_dir}`) is missing for "
                    f"{package}:{artifact}:{library_version}.",
                    file=sys.stderr,
                )
                break
        except Exception:
            pass
    return library_version


def collect_version_coverage_metrics(repo_path: str, package: str, artifact: str, library_version: str):
    """Return coverage metrics from exploded stats files for a library version."""
    stats_coverage = _stats_coverage_metrics(repo_path, package, artifact, library_version)
    if stats_coverage is not None:
        return stats_coverage
    print(
        "ERROR: Coverage stats not found in "
        f"stats/<groupId>/<artifactId>/<metadata-version>/stats.json for {package}:{artifact}:{library_version}.",
        file=sys.stderr,
    )
    return 0.0, 0


def collect_and_print_metrics(
        repo_path: str,
        package: str,
        artifact: str,
        library_version: str,
        agent,
        model_name: str | None,
        global_iterations: int,
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
    test_version = _resolve_test_version_dir(repo_path, package, artifact, library_version)
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
    test_only_metadata_entries = count_test_only_metadata_entries(repo_path, package, artifact, library_version)

    cached_tokens_suffix = ""
    if cached_input_tokens_used is not None:
        cached_tokens_suffix = f" Cached Input Tokens: {cached_input_tokens_used}"

    test_metadata_suffix = ""
    if test_only_metadata_entries > 0:
        test_metadata_suffix = f" Test-Only Metadata Entries: {test_only_metadata_entries}"

    print(
        f"Iterations: {global_iterations}  Input Tokens: {input_tokens_used}{cached_tokens_suffix} Output Tokens: {output_tokens_used} Metadata Entries: {total_entries}{test_metadata_suffix} Coverage: {coverage_percent:.2f}% Generated LOC: {generated_loc} Cost: {total_cost_usd:.4f} Library lines covered: {lines_covered}")

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
        test_file: str,
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
    run_metrics["metrics"] = metrics
    run_metrics["artifacts"] = {
        "test_file": test_file,
        "metadata_file": metadata_file,
    }

    return run_metrics


def resolve_artifact_paths(repo_path, package, artifact, library_version, tests_root):
    """Resolve test file and metadata file paths for a library version."""
    test_file_path = None
    for dirpath, _, filenames in os.walk(tests_root):
        for fname in filenames:
            if _is_test_source_file(fname):
                test_file_path = os.path.relpath(os.path.join(dirpath, fname), repo_path)
                break
        if test_file_path:
            break

    # Determine metadata_file path (either reachability-metadata.json or the metadata dir)
    reach_json = os.path.join(repo_path, "metadata", package, artifact, library_version, "reachability-metadata.json")
    if os.path.isfile(reach_json):
        metadata_file_path = os.path.relpath(reach_json, repo_path)
    else:
        metadata_file_path = os.path.relpath(
            os.path.join(repo_path, "metadata", package, artifact, library_version),
            repo_path,
        )

    return str(test_file_path), str(metadata_file_path)


def _is_test_source_file(file_name: str) -> bool:
    return file_name.endswith((".java", ".kt", ".scala", ".groovy"))


def collect_base_metrics(repo_path, package, artifact, library_version, agent, model_name, global_iterations):
    """Collect metrics and compute cost. Returns a flat dict of metric values."""
    metrics = collect_and_print_metrics(
        repo_path=repo_path,
        package=package,
        artifact=artifact,
        library_version=library_version,
        agent=agent,
        model_name=model_name,
        global_iterations=global_iterations,
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
        tests_root,
        strategy_name,
        status,
        starting_commit: str | None = None,
        ending_commit: str | None = None,
        post_generation_intervention: dict | None = None,
):
    """
    Build a run_metrics dict using collected metrics.
    """
    metrics = collect_base_metrics(repo_path, package, artifact, library_version, agent, model_name, global_iterations)
    test_file, metadata_file = resolve_artifact_paths(repo_path, package, artifact, library_version, tests_root)
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
        test_file=test_file,
        metadata_file=metadata_file,
        stats=stats,
        post_generation_intervention=post_generation_intervention,
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
        tests_root,
        strategy_name,
        status,
        starting_commit: str | None = None,
        ending_commit: str | None = None,
        post_generation_intervention: dict | None = None,
):
    """Build run metrics for fix_javac_fail workflow including previous-version metrics."""
    metrics = collect_base_metrics(repo_path, package, artifact, new_library_version, agent, model_name, global_iterations)
    test_file, metadata_file = resolve_artifact_paths(repo_path, package, artifact, new_library_version, tests_root)

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
        test_file=test_file,
        metadata_file=metadata_file,
        previous_library=f"{package}:{artifact}:{previous_library_version}",
        previous_library_metadata_entries=previous_entries,
        previous_library_test_only_metadata_entries=previous_test_entries,
        previous_library_coverage_percent=previous_coverage_percent,
        stats=stats,
        previous_library_stats=previous_stats,
        post_generation_intervention=post_generation_intervention,
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
        test_file="None",
        metadata_file="None",
    )


def _load_run_metrics_entries_for_update(path):
    """Load an existing metrics array from disk, tolerating an empty file."""
    if not os.path.isfile(path):
        return []

    with open(path, "r", encoding="utf-8") as f:
        try:
            data = json.load(f)
        except json.JSONDecodeError:
            data = None

    if data is None:
        return []
    if not isinstance(data, list):
        raise TypeError(f"ERROR: Expected metrics array in {path}")
    return data



def _run_metrics_entry_sort_key(entry: dict) -> str:
    """Return the library-name sort key for persisted run metrics."""
    if not isinstance(entry, dict):
        return ""

    library = entry.get("library")
    return library if isinstance(library, str) else ""


def _run_metrics_key(task_type: str, timestamp: str) -> str:
    """Build the stable execution-metrics key for a run."""
    date = timestamp.split("T", 1)[0]
    return f"{task_type}:{date}"


def execution_metrics_path(repo_path: str, run_metrics: dict) -> str:
    """Return the per-library execution metrics path for a run metrics entry."""
    library = run_metrics.get("library")
    if not isinstance(library, str):
        raise TypeError("ERROR: run_metrics library must be a string")

    parts = library.split(":")
    if len(parts) != 3 or any(not part for part in parts):
        raise ValueError(f"ERROR: run_metrics library must be group:artifact:version: {library}")

    group, artifact, version = parts
    return os.path.join(repo_path, "stats", group, artifact, version, "execution-metrics.json")


def _load_execution_metrics_entries(path: str) -> dict:
    """Load an execution-metrics object from disk."""
    if not os.path.isfile(path):
        return {}

    with open(path, "r", encoding="utf-8") as metrics_file:
        data = json.load(metrics_file)

    if not isinstance(data, dict):
        raise TypeError(f"ERROR: Expected execution metrics object in {path}")
    return data


def _public_execution_metrics_entry(run_metrics: dict) -> dict:
    """Return the committed execution-metrics entry without local-only PR details."""
    public_metrics = dict(run_metrics)
    public_metrics.pop("post_generation_intervention", None)
    return public_metrics


def append_execution_metrics(repo_path: str, run_metrics: dict, task_type: str) -> str:
    """Append one run metrics entry under stats/<group>/<artifact>/<version>/execution-metrics.json."""
    timestamp = run_metrics.get("timestamp")
    if not isinstance(timestamp, str) or not timestamp:
        raise TypeError("ERROR: run_metrics timestamp must be a string")

    metrics_path = execution_metrics_path(repo_path, run_metrics)
    entries = _load_execution_metrics_entries(metrics_path)
    entries[_run_metrics_key(task_type, timestamp)] = _public_execution_metrics_entry(run_metrics)

    os.makedirs(os.path.dirname(metrics_path), exist_ok=True)
    with open(metrics_path, "w", encoding="utf-8") as metrics_file:
        json.dump(dict(sorted(entries.items())), metrics_file, indent=2, ensure_ascii=False)
        metrics_file.write("\n")

    return metrics_path


def in_metadata_repo_metrics_root(metrics_repo_root: str | None) -> str | None:
    """Return the parent reachability repo root when metrics root is the in-repo metadata-forge directory."""
    if not metrics_repo_root:
        return None

    repo_path = os.path.dirname(os.path.abspath(metrics_repo_root))
    if os.path.isdir(os.path.join(repo_path, "metadata")) and os.path.isdir(os.path.join(repo_path, "stats")):
        return repo_path
    return None



def _merge_run_metrics_entries(existing_entries: list, run_metrics: dict) -> list:
    """Merge a run metrics entry into an existing metrics array and keep library ordering stable."""
    merged_entries = list(existing_entries)

    if run_metrics not in merged_entries:
        merged_entries.append(run_metrics)

    merged_entries.sort(key=_run_metrics_entry_sort_key)
    return merged_entries



def _write_run_metrics_entries(path, entries) -> None:
    """Persist a run metrics array to disk."""
    parent_dir = os.path.dirname(path)
    if parent_dir:
        os.makedirs(parent_dir, exist_ok=True)

    with open(path, "w", encoding="utf-8") as f:
        json.dump(entries, f, indent=2, ensure_ascii=False)



def append_run_metrics(run_metrics, path):
    """
    Validate, merge, and write a single run_metrics object to a JSON array at `path`.
    """
    if not isinstance(run_metrics, dict):
        raise TypeError("ERROR: run_metrics must be a dict")

    existing = _load_run_metrics_entries_for_update(path)
    merged_entries = _merge_run_metrics_entries(existing, run_metrics)
    _write_run_metrics_entries(path, merged_entries)


def load_run_metrics_entries(path):
    """Load a metrics JSON array from disk, returning an empty list if absent."""
    if not os.path.isfile(path):
        return []

    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)

    if not isinstance(data, list):
        raise TypeError(f"ERROR: Expected metrics array in {path}")
    return data



def _select_latest_matching_run_metrics(entries: list, library: str, previous_library: str | None = None):
    """Return the latest matching run metrics entry from a loaded metrics array."""
    for entry in reversed(entries):
        if not isinstance(entry, dict):
            continue
        if entry.get("library") != library:
            continue
        if previous_library is not None and entry.get("previous_library") != previous_library:
            continue
        return entry
    return None



def load_matching_run_metrics(path, library, previous_library: str | None = None):
    """Load the most recent metrics entry matching the requested library identifiers."""
    entries = load_run_metrics_entries(path)
    return _select_latest_matching_run_metrics(entries, library, previous_library=previous_library)


def append_matching_run_metrics(source_path, destination_path, library, previous_library: str | None = None):
    """Append the latest matching metrics entry from one metrics file into another."""
    matched = load_matching_run_metrics(
        source_path,
        library,
        previous_library=previous_library,
    )
    if matched is None:
        if previous_library is None:
            raise ValueError(f"ERROR: No metrics entry found for {library} in {source_path}")
        raise ValueError(
            f"ERROR: No metrics entry found for {library} with previous library {previous_library} in {source_path}"
        )

    append_run_metrics(matched, destination_path)
    return matched



PENDING_METRICS_FILENAME = ".pending_metrics.json"


def write_pending_metrics(metrics_repo_root: str, run_metrics: dict) -> None:
    """Write a run metrics dict to a pending file in the metrics worktree root."""
    path = os.path.join(metrics_repo_root, PENDING_METRICS_FILENAME)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(run_metrics, f, indent=2, ensure_ascii=False)


def read_pending_metrics(metrics_repo_root: str) -> dict:
    """Read the pending metrics dict from the metrics worktree root."""
    path = os.path.join(metrics_repo_root, PENDING_METRICS_FILENAME)
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def _resolve_primary_worktree_root(repo_root: str) -> str:
    """Resolve the main checkout root that owns the shared git metadata."""
    result = subprocess.run(
        ["git", "rev-parse", "--git-common-dir"],
        cwd=repo_root,
        check=True,
        capture_output=True,
        text=True,
    )
    common_git_dir = result.stdout.strip()
    if not os.path.isabs(common_git_dir):
        common_git_dir = os.path.normpath(os.path.join(repo_root, common_git_dir))
    return os.path.dirname(common_git_dir)


def _commit_metrics_locally(
        metrics_repo_root: str,
        metrics_json_relative_path: str,
        run_metrics: dict,
        commit_message: str,
) -> None:
    """Commit metrics in the main checkout so detached scratch worktrees can be removed safely."""
    commit_repo_root = _resolve_primary_worktree_root(metrics_repo_root)
    metrics_json_absolute_path = os.path.join(commit_repo_root, metrics_json_relative_path)

    append_run_metrics(run_metrics, metrics_json_absolute_path)
    subprocess.run(["git", "add", metrics_json_relative_path], check=True, cwd=commit_repo_root)

    result = subprocess.run(["git", "diff", "--cached", "--quiet"], cwd=commit_repo_root)
    if result.returncode == 0:
        print(f"No changes in {metrics_json_relative_path} to commit in metrics repo.")
        return

    subprocess.run(
        [
            "git",
            "-c",
            "user.name=metadata-forge",
            "-c",
            "user.email=metadata-forge@local",
            "commit",
            "-m",
            commit_message,
        ],
        check=True,
        cwd=commit_repo_root,
    )


def commit_run_metrics_with_retry(
        metrics_repo_root: str,
        metrics_json_relative_path: str,
        run_metrics: dict,
        commit_message: str,
        max_attempts: int = 5,
) -> None:
    """Merge a metrics entry into the latest remote file state, then commit and push with retries."""
    metrics_json_absolute_path = os.path.join(metrics_repo_root, metrics_json_relative_path)
    has_origin = git_remote_exists("origin", cwd=metrics_repo_root)

    if not has_origin:
        _commit_metrics_locally(
            metrics_repo_root=metrics_repo_root,
            metrics_json_relative_path=metrics_json_relative_path,
            run_metrics=run_metrics,
            commit_message=commit_message,
        )
        return

    for attempt in range(1, max_attempts + 1):
        subprocess.run(["git", "fetch", "origin", "master"], check=True, cwd=metrics_repo_root)
        subprocess.run(["git", "reset", "--hard", "origin/master"], check=True, cwd=metrics_repo_root)

        append_run_metrics(run_metrics, metrics_json_absolute_path)
        subprocess.run(["git", "add", metrics_json_relative_path], check=True, cwd=metrics_repo_root)

        result = subprocess.run(["git", "diff", "--cached", "--quiet"], cwd=metrics_repo_root)
        if result.returncode == 0:
            print(f"No changes in {metrics_json_relative_path} to commit in metrics repo.")
            return

        subprocess.run(["git", "commit", "-m", commit_message], check=True, cwd=metrics_repo_root)
        push_result = subprocess.run(
            ["git", "push", "origin", "HEAD:master"],
            cwd=metrics_repo_root,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
        )
        if push_result.returncode == 0:
            return

        if attempt < max_attempts:
            print(
                f"Retrying metrics push after attempt {attempt} failed: {push_result.stdout}",
                file=sys.stderr,
            )
            continue

        if attempt == max_attempts:
            print(
                f"ERROR: Failed to push metrics after {max_attempts} attempts: {push_result.stdout}",
                file=sys.stderr,
            )
            raise subprocess.CalledProcessError(
                push_result.returncode,
                ["git", "push", "origin", "HEAD:master"],
                output=push_result.stdout,
            )


def collect_new_library_support_quality_issues(run_metrics: dict) -> list[str]:
    """Return validation failures for a new-library-support run that is not meaningful enough for a PR."""
    status = run_metrics.get("status")
    if status not in {"success", "success_with_intervention"}:
        return [f"workflow status is `{status or 'unknown'}`"]

    metrics = run_metrics.get("metrics") or {}

    code_coverage_percent = float(metrics.get("code_coverage_percent", 0.0) or 0.0)
    if code_coverage_percent <= 0.0:
        return ["library coverage percentage is zero"]

    return []
