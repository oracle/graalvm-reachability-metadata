# BENCH-forge-generation-benchmarking: Forge generation benchmarking

Forge benchmarking measures generation quality, cost
(§GOAL-minimize-generation-cost), and coverage (§GOAL-maximize-library-coverage)
for strategy comparisons. It is mainly for the `library-new-request` flow
(§FS-forge-issue-resolution-goal): the benchmark runner clears selected library
tests/metadata, executes `ai_workflows/add_new_library_support.py` in benchmark
mode exercising the dynamic-access workflow (§WF-dynamic-access-workflow), and
stores durable per-library results under `benchmark_run_metrics/`
(§FS-durable-generation-logs).

## 1. Benchmark Scope

Benchmarking must evaluate full generation runs, not isolated prompt snippets
or mocked workflow calls. The primary subject is new-library support because it
exercises scaffold setup, source context, test generation, metadata generation,
verification, and benchmark metrics in one comparable process
(§WF-forge-workflow-drivers).

Other workflows may be benchmarked later, but they must define their own suite
shape and metric compatibility before their results are compared with
new-library benchmark runs.

## 2. Suite Shape

A benchmark suite must contain several real library coordinates and must be run
across a large set of different predefined strategies. The suite should include
libraries with different API shapes and metadata needs so strategy comparisons
do not overfit one artifact (§STRAT-forge-predefined-strategy-contract).

The benchmark suite file may represent one strategy per benchmark entry, but a
benchmark campaign must include multiple entries that cover the same or
overlapping library set with different `strategy` values. Comparing one
strategy on one library is a smoke run, not a benchmark.

## 3. Required Metrics

Benchmarking must preserve every generation metric supported by the benchmark
schemas and generated run metrics. At minimum each run must capture:

- Token usage for every tracked token class, including input tokens, cached
  input tokens when available, output tokens, and any future token fields added
  to the run metrics schema.
- Total cost in USD (§GOAL-minimize-generation-cost).
- Iteration count.
- Generated test LOC and tested library LOC.
- Code coverage percentage.
- Dynamic-access coverage, including covered calls, total calls, coverage
  ratio, and the per-kind breakdown from generated library stats
  (§GOAL-maximize-library-coverage).
- Metadata entry counts, including generated metadata entries, test-only
  metadata entries when present, and the original metadata count used for
  before/after comparisons.
- Status, timestamp, library coordinate, strategy name, agent, model,
  starting/ending commit, and post-generation intervention details when used
  (§FS-durable-generation-logs).

If the schema gains a new generation metric, benchmark output must include it
before benchmark results using that schema are treated as comparable.

## 4. Execution Rules

Benchmark runs must use `benchmarks/benchmark_runner.py`, which invokes
`add_new_library_support.py --benchmark-mode` for each library. Directly running
the workflow script can be useful for debugging, but it is not a benchmark
unless the run is recorded in the benchmark metrics structure.

The runner must isolate repository changes, clean existing tests/metadata for
the target coordinates, initialize a benchmark metrics record, run every
library with the configured strategy, and restore the repository state after the
campaign. Durable benchmark metrics must remain under `benchmarks/benchmark_results/`.

## 5. Pass Criteria

A benchmark result is valid only when:

- It used real library coordinates for the `library-new-request` generation
  path.
- It ran several libraries and was part of a campaign covering many strategy
  variants.
- Metrics validated against the benchmark metrics schema.
- Dynamic-access and code coverage metrics came from generated library stats,
  not manual estimates.
- Cost, token, iteration, LOC, coverage, dynamic-access, metadata, status,
  agent, model, and commit fields were all populated when available.

## 6. Fail Criteria

A benchmark result must not be used for strategy comparison when:

- It measured only one trivial library or one strategy in isolation.
- It skipped benchmark mode or wrote only ordinary script-run metrics.
- It lost token, cost, iteration, LOC, coverage, dynamic-access, or metadata
  metrics that the schema can represent.
- It ignored a failed generation, failed validation, timeout, or repository
  cleanup failure.
- It compared results produced from different suite inputs without documenting
  the difference.
