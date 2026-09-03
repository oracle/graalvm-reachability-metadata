# Code coverage benchmark implementation plan

## Objective

Build a configurable benchmark runner around the existing code coverage
improvement Rhei workflow. Every execution uses the five libraries from one
fixed repository commit, skips GitHub issue claiming, preserves its local Rhei
workspace, and publishes one compact metrics entry to this repository.
§FS-code-coverage-benchmarking

This is a normal implementation guide. It is not itself a Rhei plan.

## Fixed benchmark input

All library worktrees start from commit:

```text
92a2a4fa60b2d6532fa533f2d4f8f795dd28a1cb
```

The runner may execute from a later clean commit. Every result records
`benchmarkSuiteCommit`, which identifies the fixed library state, and
`runnerCommit`, which identifies the runner implementation. The latter is
provenance, not another suite version; no campaign or `v1` identifier is
needed.

The fixed libraries and their checked-in method coverage are:

| Index | Coordinate | Covered | All methods | Coverage |
| ---: | --- | ---: | ---: | ---: |
| 1 | `io.github.resilience4j:resilience4j-core:2.3.0` | 9 | 359 | 2.51% |
| 2 | `org.apache.commons:commons-compress:1.23.0` | 269 | 4,366 | 6.16% |
| 3 | `org.apache.kafka:kafka-streams:3.6.0` | 1,855 | 19,283 | 9.62% |
| 4 | `com.h2database:h2:2.1.210` | 2,412 | 11,943 | 20.20% |
| 5 | `com.google.code.gson:gson:2.14.0` | 366 | 1,040 | 35.19% |

Later merged coverage improvements do not alter this input. A run's frozen
JaCoCo method universe is authoritative in its result. Record any difference
from the checked-in total instead of reconciling it silently.

## Default execution matrix

With no selection flags, run 75 executions:

```text
5 libraries × 5 agent/model configurations × 3 thinking levels = 75
```

Configurations:

- Pi: `gpt-5.6-sol`, `gpt-5.6-luna`, and `gpt-5.6-terra`.
- Claude Code: Sonnet 5 and Opus 5.
- Every model: `medium`, `high`, and `xhigh`.

Sonnet 5 and Opus 5 are stable configuration names mapped to concrete Claude
Code model identifiers. Record both the configured and observed identifiers.

## Runner interface

The no-flag command selects the whole matrix:

```console
./run-code-coverage-benchmark.sh
```

Selections restrict the cross-product; unspecified dimensions retain all
configured values:

```console
./run-code-coverage-benchmark.sh --library-index 1 3 5
./run-code-coverage-benchmark.sh --agent claude-code
./run-code-coverage-benchmark.sh --model gpt-5.6-luna
./run-code-coverage-benchmark.sh --thinking high
./run-code-coverage-benchmark.sh \
  --library-index 3 \
  --agent claude-code \
  --model sonnet-5 \
  --thinking high
```

Accept multiple values for `--library-index`, `--agent`, `--model`, and
`--thinking`. Reject unknown, duplicate, or agent-incompatible selections
before creating worktrees. Print the selected matrix before the first mutation.

Each cell receives a unique run ID and parent. Create its source worktree from
`benchmarkSuiteCommit`. Instantiate the Rhei workspace with synthetic issue
number `99000`, retaining the required `code-coverage-99000` name below the
unique parent.

## Benchmark template mode

Add a boolean input to the existing code coverage template:

```yaml
- name: benchmark
  description: Run without GitHub issue mutation and publish benchmark metrics.
  type: boolean
  default: false
```

The benchmark launcher supplies `benchmark=true`; the issue launcher retains
the default. Use supported MiniJinja `{% if benchmark %}` blocks to change the
rendered task structure rather than asking an agent to interpret the mode.

Additional inputs carry the run ID, fixed suite commit, runner commit, source
worktree, runner Forge path, and metrics publishing checkout. Deterministic
benchmark preparation rejects missing values.

### Conversion

Keep the `code-coverage-convert` task ID so existing prerequisites remain
valid.

- With `benchmark=false`, render the current issue conversion and Project
  validation agent task.
- With `benchmark=true`, render a deterministic `benchmark-convert` program
  state.

The benchmark conversion helper validates the coordinate, commits, run ID,
worktree, runner, and workspace; confirms the worktree is at the fixed commit;
resolves the exact test project and coverage suite; and writes the existing
conversion JSON and Markdown artifacts. It must not call `gh`, assign an
issue, validate a label, or mutate Project status.

Reuse preparation, API coverage, native metadata preparation, deep coverage,
and finalization unchanged. §AR-code-coverage-improvement.4.1

### Publication

After finalization, render exactly one terminal task:

- Normal mode renders the current source publication task.
- Benchmark mode renders deterministic
  `code-coverage-benchmark-publication` in a `benchmark-publication` state.

Benchmark publication collects metrics, updates the coordinate JSON, validates
it, commits it, and pushes it. It must not push generated source, create a Forge
publication descriptor, open a pull request, or mutate an issue.

If Rhei stops before the terminal task, the outer launcher invokes the same
idempotent collector so failed or partial executions are recorded.

## Metrics storage

Store one JSON list per coordinate:

```text
code-coverage-benchmarks/
  <group>/
    <artifact>/
      <version>.json
```

Each finished execution appends one timestamped object. `runId` makes retries
idempotent. Keep entries ordered by timestamp.

Publish immediately after each execution, not after the selected matrix
finishes. Use a dedicated publishing worktree of this same repository so
fetch/reset operations do not touch the user checkout, source worktrees, or
preserved workspaces. Reuse Forge's fetch/reset/reappend/commit/push retry
behavior and serialize local publishers. Commit subjects must be at most 60
characters.

Write the normalized result into the preserved workspace before Git
publication. Write a publication marker only after a successful push. Repeating
publication for the same `runId` validates the identical entry instead of
appending it again.

## Initial metrics

Every entry contains:

- run ID and timestamp;
- suite and runner commits;
- coordinate, agent, configured model, observed model, and thinking;
- status and known failure phase/exit code;
- API, deep, and total metrics.

API and deep each contain:

- cover passes and fix invocations;
- input, cached-input-read, and output tokens for all agent invocations in that
  phase, including fixes;
- covered methods before and after;
- methods and percentage points gained;
- `allMethods`, the common frozen JaCoCo denominator.

Total sums API and deep passes, fixes, and token classes and records run-start,
final, and gained coverage with the same denominator. Initial token totals
exclude conversion, preparation, finalization, and benchmark publication.

Coverage comes from `runCoverage`; cover passes come from stop decisions; fix
and token values come from Rhei accounting. Missing partial-run evidence is
`null`, never zero.

## Workspace lifecycle

Preserve every Rhei workspace locally after success or failure. Print its
absolute path when the execution ends. Portable metrics retain the run ID and
stable workspace name, not a machine-specific absolute path.

Remove the disposable source worktree only after its result is pushed. If
collection or publication fails, retain both workspace and worktree. Provide a
command that discovers workspaces without a publication marker and retries
publication without rerunning coverage.

Do not commit or push runtime artifacts and logs in the initial implementation.

## Implementation sequence

1. Align `FS-code-coverage-benchmarking` with this final design, removing
   superseded `v1` and deferred storage/matrix language.
2. Add the checked-in suite configuration and JSON Schema.
3. Implement deterministic worktree preparation and conversion artifacts.
4. Implement complete/partial metrics extraction and idempotent publication.
5. Add template conditionals, benchmark states, terminal routing, and explicit
   Pi and Claude Code modes.
6. Implement matrix expansion, selection flags, unique paths, failure fallback,
   cleanup, and pending-publication retry.
7. Test both template branches, all selection rules, metrics derivation,
   schema validation, publication retries, and workspace lifecycle.
8. Document commands and run focused tests, template instantiation/dry-runs,
   style checks, `grund check`, and `git diff --check`.

## Required verification

- Normal mode retains the current issue-driven workflow.
- Benchmark mode contains no GitHub claim path.
- Middle coverage tasks are identical in both modes.
- No flags select exactly 75 executions.
- All filter combinations produce the expected cross-product.
- Claude accounting is tested without live model calls.
- Complete and partial records validate and are idempotent.
- Each result is pushed after its execution.
- Workspaces remain; worktrees are removed only after publication.

## Later decisions

- Advanced cost, timing, efficiency, quality, and stability metrics.
- Statistical replication and leaderboard presentation.
- Execution concurrency and scheduling policy.
- Remote archival of Rhei runtime artifacts and logs.
- Future changes to the fixed libraries or suite commit.
