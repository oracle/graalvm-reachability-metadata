# FS-forge-generation-benchmarking: Forge generation benchmarking

Forge benchmarking measures generation quality, cost
(§GOAL-minimize-generation-cost), and coverage (§GOAL-maximize-library-coverage)
for strategy comparisons. It is mainly for the `library-new-request` flow
(§FS-forge-issue-resolution-goal): the benchmark runner clears selected library
tests/metadata, executes `ai_workflows/drivers/add_new_library_support.py` in
benchmark mode exercising the dynamic-access workflow
(§AR-dynamic-access-workflow), and stores durable per-library results under
`benchmark_run_metrics/`
(§FS-durable-generation-logs).

## 1. Benchmark Scope

Benchmarking must evaluate full generation runs, not isolated prompt snippets
or mocked workflow calls. The primary subject is new-library support because it
exercises scaffold setup, source context, test generation, metadata generation,
verification, and benchmark metrics in one comparable process
(§AR-forge-drivers).

Other workflows may be benchmarked later, but they must define their own suite
shape and metric compatibility before their results are compared with
new-library benchmark runs.

## 2. Suite Shape

A benchmark suite must contain several real library coordinates and must be run
across a large set of different predefined strategies. The suite should include
libraries with different API shapes and metadata needs so strategy comparisons
do not overfit one artifact (§FS-forge-predefined-strategy-contract).

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

# FS-code-coverage-benchmarking: Code coverage improvement benchmarking

Code coverage improvement benchmarking compares agent configurations by running
the complete code coverage improvement workflow against the same already-supported
libraries and the same repository state. Its initial contract is deliberately
small: preserve the Rhei workspace for inspection and publish one compact metrics
record per execution (§FS-forge-generation-benchmarking.1,
§GOAL-maximize-library-coverage, §GOAL-minimize-generation-cost).

## 1. Initial benchmark suite and baseline

The initial suite contains these exact coordinates. The checked-in statistics at
baseline commit `92a2a4fa60b2d6532fa533f2d4f8f795dd28a1cb` report the following
method coverage; none of the five test projects contains a
`code-coverage-improvement` suite at that commit.

| Coordinate | Covered methods | All methods | Method coverage |
| --- | ---: | ---: | ---: |
| `io.github.resilience4j:resilience4j-core:2.3.0` | 9 | 359 | 2.51% |
| `org.apache.commons:commons-compress:1.23.0` | 269 | 4,366 | 6.16% |
| `org.apache.kafka:kafka-streams:3.6.0` | 1,855 | 19,283 | 9.62% |
| `com.h2database:h2:2.1.210` | 2,412 | 11,943 | 20.20% |
| `com.google.code.gson:gson:2.14.0` | 366 | 1,040 | 35.19% |

The commit is the fixed benchmark-suite input. Every execution must create its
source worktree from that exact commit, never from the launcher's current `HEAD`,
`master`, or `origin/master`. A code coverage improvement merged later therefore
does not change the input. The suite commit must not advance automatically.

The benchmark runner itself may execute from a later clean commit. Every result
records both `benchmarkSuiteCommit` and `runnerCommit`: the first identifies the
library state being measured and the second is implementation provenance, not a
second baseline or campaign identifier.

The checked-in totals select and describe the suite; the run's own frozen JaCoCo
method universe is authoritative in its metrics. A difference between the
checked-in `All methods` value and the measured universe must be recorded, not
silently reconciled.

## 2. Matrix selection and preparation

The default matrix is the cross-product of five libraries, five agent/model
configurations, and three thinking levels, for 75 executions:

- Pi with `gpt-5.6-sol`, `gpt-5.6-luna`, or `gpt-5.6-terra`.
- Claude Code with the stable configuration names `sonnet-5` or `opus-5`.
- `medium`, `high`, or `xhigh` thinking for every configuration.

The launcher accepts multiple library indexes, agents, models, and thinking
levels as filters. Unspecified dimensions retain all configured values. Unknown,
duplicate, and agent-incompatible selections must fail before any worktree is
created, and the complete selected matrix must be printed before the first
mutation. With no selection flags, the launcher selects exactly 75 executions.

For each execution, the launcher must:

1. Validate that the coordinate belongs to the fixed suite.
2. Allocate a unique run identifier and a unique parent directory.
3. Create a disposable source worktree from the fixed suite commit.
4. Instantiate a Rhei workspace named `code-coverage-99000` below that unique
   parent directory. `99000` is a fixed synthetic issue number used only to
   satisfy the code coverage workspace naming contract.
5. Prepare the conversion inputs deterministically, including the coordinate,
   commits, run identifier, source worktree, runner Forge path, metrics
   publishing checkout, and coverage suite paths expected in
   `runtime/code-coverage/issues/conversion.json`.
6. Execute the existing preparation, API coverage, native metadata preparation,
   deep coverage, and finalization behavior without claiming or reading a GitHub
   issue.

Benchmark preparation must not query an issue, validate an issue label, assign
an issue, or mutate a GitHub Project status. The coordinate supplied by the
launcher is the complete benchmark input that replaces issue conversion. The
normal issue-driven launcher remains unchanged. A boolean `benchmark` template
input, defaulting to false, renders either the existing issue conversion and
source publication tasks or deterministic benchmark conversion and metrics
publication tasks. The middle preparation, API, native-metadata, deep, and
finalization tasks are the same in both modes.

The Rhei workspace must live outside the disposable source worktree. Distinct
run parents prevent collisions while preserving the required fixed workspace
name.

## 3. Benchmark metrics publication

A benchmark workspace routes successful finalization to a deterministic
`code-coverage-benchmark-publication` task instead of the normal publication
task. It must not push
the generated source branch, publish a Forge branch descriptor, open a pull
request, or change the synthetic issue.

Benchmark publication reads the finalized coverage record and Rhei accounting,
writes the metrics record in §FS-code-coverage-benchmarking.4, and commits and
pushes only that record to the same reachability-metadata repository. Results
live in one JSON list per coordinate:

```text
code-coverage-benchmarks/<group>/<artifact>/<version>.json
```

Every execution is appended immediately after it finishes, ordered by timestamp.
Its `runId` is the idempotency key: retrying an identical result validates the
existing entry instead of appending it again, while conflicting data for one
run ID is rejected. Publication serializes local writers and creates a fresh
disposable worktree from the latest `origin/master` for each result. It appends
the result, commits, and pushes from that worktree, then removes it. A push race
retries with another fresh worktree; publication never reuses or resets a
publishing checkout.

The outer launcher must invoke the same metrics collector when Rhei terminates
before reaching benchmark publication. Thus a failed or partial workflow remains
a benchmark result rather than disappearing from the comparison.

The normalized result must be written into the workspace before Git publication,
and a publication marker may be written only after a successful push. The
source worktree may be removed only after that marker exists. The Rhei workspace
must not be removed: it remains on the machine for later inspection of its
reports, prompts, accounting, fixes, and work notes. If metrics writing or
pushing fails, both the source worktree and workspace remain so completion can
be retried without losing evidence.

The launcher provides a command that discovers workspaces without a publication
marker and retries their result publication without rerunning coverage.

## 4. Initial metrics record

The initial record contains identity, status, and the API, deep, and total
results. It publishes no Rhei runtime artifacts or logs.

Identity and status must contain:

- schema version, unique run identifier, and timestamp;
- the fixed suite commit and runner commit;
- coordinate, agent, configured model, observed model, and thinking level;
- terminal status; and
- for an unsuccessful execution, the phase and exit code when known.

The API and deep records must each contain:

- `coverPasses`, meaning invocations of that phase's cover state;
- `fixInvocations`, meaning invocations of that phase's fix state;
- input, cached-input-read, and output tokens consumed by all agent invocations
  in that phase, including fixes;
- covered methods before and after the phase;
- methods gained and coverage percentage points gained; and
- `allMethods`, the frozen whole-run JaCoCo method universe used as the common
  denominator.

The total record must contain the sums of API and deep cover passes, fix
invocations, and token classes, plus run-start covered methods, final covered
methods, total methods gained, total percentage points gained, and
`allMethods`. Tokens from conversion, preparation, finalization, and benchmark
completion are outside the initial token total.

Coverage values must come from the existing whole-run `runCoverage` checkpoints
and phase gains, so API starts at `runStart`, deep starts at `afterApiPhase`, and
the total ends at `final`. All three use one denominator
(§AR-code-coverage-improvement.4.1). Cover-pass counts come from the recorded
phase stop decisions; fix counts and phase token usage come from Rhei invocation
and task accounting. Cached input must remain separate from ordinary input.

When a failed execution lacks a final checkpoint or another metric, the field is
`null`, not zero. Metrics that exist before the failure must still be retained.

## 5. Preserved local workspace

The preserved workspace is the detailed local lookup record for an execution.
Its stable parent is keyed by the run identifier, and it retains the rendered
Rhei plan and the complete `runtime/` tree produced by the workflow. It is not
committed or pushed by the initial benchmark implementation.

The launcher must print the preserved workspace's absolute path when the run
ends. The metrics record must carry its run identifier and workspace name so a
local lookup can resolve the corresponding directory without making a
machine-specific absolute path part of the portable comparison data.

## 6. Deferred decisions

The following are explicitly deferred until the base runner and metrics record
exist:

- when and how the fixed library set or baseline commit is revised;
- execution concurrency, scheduling, and statistical replication;
- cost calculation, timing, efficiency, quality, stability, and other advanced
  metrics; and
- publication or remote archival of preserved Rhei workspaces and runtime
  artifacts.
