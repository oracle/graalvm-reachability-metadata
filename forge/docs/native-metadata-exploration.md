# Native Metadata Exploration — Specification

> Implementation backing the
> [`native_trace_collect`](workflow-strategies.md#53-native_trace_collect)
> post-generation intervention. Strategies opt in by setting
> `"post-generation-intervention": { "name": "native_trace_collect" }` in
> their predefined-strategy entry; this document defines the underlying
> trace loop the intervention runs.

At a glance:

```text
   setup (clear output_dir + runs_dir)
       |
       v
  +-------------------------------------------+
  | iteration loop                            |
  |   nativeTraceImage   (cfg = prior runs)   |
  |   runNativeTraceImage -> metadata-run-i   |
  |   converged?  -- yes -->  break           |
  |   build_failed? -- yes -->  break         |
  |   budget_exhausted? -- yes -->  break     |
  +-------------------+-----------------------+
                      |
                      v
            mergeNativeTraceMetadata
            (only if any run accepted)
                      |
                      v
        result = status + output_dir + failure
                      |
                      v
              caller -> codex fixup
              (every status, including BUILD_FAILED)
```

> **See also:** [Functional spec](functional-spec.md) ·
> [Architecture](architecture.md) ·
> [Workflow strategies & interventions](workflow-strategies.md) ·
> [Native test verification gate](native-test-verification.md) ·
> [Dynamic-access workflow](dynamic-access-workflow.md) ·
> [Java fail-fix workflow](fix-java-run-fail.md)
>
> The deterministic loop specified here is also composed into the
> [native test verification gate](native-test-verification.md), which
> wraps it with `nativeTest` execution and codex/Pi recovery so callers
> can assert "Native Image tests pass" rather than just "metadata was
> collected".

## 1. Purpose

This document specifies the iterative native-image metadata-tracing loop
that produces reachability metadata for the target library by **running a
native image with metadata tracing enabled** rather than by static analysis
or agent inference. The traced metadata is written to a caller-supplied
output directory (see §4) and is then available to downstream steps —
notably the codex metadata fixup — as additional, ground-truth context.

The loop is reusable and self-contained: it has no knowledge of which
intervention or workflow invokes it. The single caller in production is the
[`native_trace_collect`](workflow-strategies.md#53-native_trace_collect)
post-generation intervention, which wraps this driver, runs codex with the
result, and falls back to Pi when codex cannot recover (mirroring
`codex_then_pi`). Strategies that want trace-driven recovery activate it by
configuring `"post-generation-intervention": { "name": "native_trace_collect" }`
in their predefined-strategy entry; nothing else changes inside the
strategy.

## 2. Execution Model

The contract this phase implements is the GraalVM `MetadataTracingSupport`
recipe. The key insight is that **rebuilding inside the loop matters**:
metadata collected by an earlier pass unlocks code paths that only later
image builds reach. There are no caller-supplied "scenarios" — there is one
binary invocation, repeated until **a run discovers no new metadata** (the
convergence signal) or the iteration budget is exhausted.

The phase **must not** shell out to `native-image` or `native-image-utils`
directly. All native-toolchain steps are mediated by Gradle tasks in the
reachability repo (see §7). The Python phase orchestrates the loop and the
convergence check; every individual build, run, and merge is a `./gradlew`
invocation.

Pseudo-code (the Python phase mirrors this):

```text
run_dirs = []
for i in 0..max_iterations:
    config_dirs = run_dirs                  # raw, never merged
    rc = ./gradlew nativeTraceImage \
            -Pcoordinates=<g:a:v> \
            -PmetadataConfigDirs=<config_dirs joined by ',' or empty>
    if rc != 0: return BUILD_FAILED

    run_i = "<runs_dir>/metadata-run-<i>"
    ./gradlew runNativeTraceImage \
        -Pcoordinates=<g:a:v> \
        -PtraceMetadataPath=<run_i> \
        -PtraceMetadataConditionPackages=<group>
    # exit code is informational only

    if iteration_added_no_new_entries(run_i, run_dirs):
        break                                # convergence
    run_dirs.append(run_i)

if len(run_dirs) > 0:
    ./gradlew mergeNativeTraceMetadata \
        -PinputDirs=<run_dirs joined by ','> \
        -PoutputDir=<output_dir>
```

The phase does **not** accept a list of run arguments — the test binary is
invoked the same way every iteration; only the metadata already collected
changes between iterations.

## 3. Inputs

| Input | Source | Notes |
| --- | --- | --- |
| Coordinate `group:artifact:version` | Caller | Identifies the test module under `tests/src/<group>/<artifact>/<version>`. |
| Reachability repo path | Caller | Working directory for Gradle. |
| **Output directory** | **Caller** | Absolute path to the directory that the final merge writes into. Caller is responsible for choosing a path that is unique to the (library, class) it is exploring for; see §4. |
| Condition packages | Strategy parameter `trace-condition-packages` | Mapped to `-XX:TraceMetadataConditionPackages=...`. Defaults to `[group]`. |
| Iteration budget | Strategy parameter `max-trace-iterations` | Default 5. Caps the build/run loop. |

Notably absent: any list of run arguments, scenarios, or test selectors. The
binary is invoked exactly the same way each iteration.

## 4. Output

The phase writes its final merged metadata to **the caller-supplied output
directory**. The phase does not pick or default this path — that is a
deliberate choice so that concurrent invocations for different libraries or
different classes within the same library do not stomp on each other.

The recommended convention for callers in this project is:

```text
tests/src/<group>/<artifact>/<version>/build/natively-collected/<class-key>/
```

where `<class-key>` is a sanitized form of the dynamic-access class the
caller is currently working on (or a literal string like `_global_` for
phases that are not class-scoped, e.g. native-run failure fix). The
`build/` segment keeps these artifacts under Gradle's clean target.

Inside the output directory, the layout is the standard
`META-INF/native-image/...` structure emitted by
`native-image-utils generate`:

```text
<output_dir>/
  reachability-metadata.json
  ...
```

Per-iteration raw traces (`metadata-run-<i>/`) are written under a sibling
working directory chosen by the phase (under
`<output_dir>/../runs/`, exact path returned in the result) and are kept
until the phase exits so callers can inspect them. They are not part of the
caller-visible contract beyond their existence in the result object.

The output directory is **not** the canonical metadata path and is not
committed as a metadata release. It is a build artifact consumed by:

- The codex metadata fixup, as additional read-only context.
- Diagnostics emitted into the run-metrics record.

The canonical metadata under `metadata/<group>/<artifact>/<version>/` continues
to be produced by `generateMetadata`.

## 5. Loop Workflow

```mermaid
flowchart TD
    Start([invoke metadata exploration]) --> Init[Remove output_dir and runs_dir<br/>run_dirs = [], i = 0]
    Init --> Loop[Iteration i]
    Loop --> Build[gradlew nativeTraceImage<br/>-PmetadataConfigDirs=run_dirs<br/>if non-empty — raw, no merge]
    Build --> BuildOK{task ok?}
    BuildOK -- no --> BuildFail([return BUILD_FAILED])
    BuildOK -- yes --> Run[gradlew runNativeTraceImage<br/>-PtraceMetadataPath=run-i<br/>-PtraceMetadataConditionPackages=&lt;packages&gt;<br/>task exit code is informational only]
    Run --> Delta{run-i contains entries<br/>not already in run_dirs?}
    Delta -- no --> Converged[Phase converged —<br/>no new metadata]
    Delta -- yes --> Append[run_dirs += run-i]
    Append --> Budget{i + 1 &lt; max-trace-iterations?}
    Budget -- yes --> Next[i = i + 1]
    Next --> Loop
    Budget -- no --> Cap[Budget exhausted]
    Cap --> FinalMerge
    Converged --> FinalMerge[**Single** gradlew mergeNativeTraceMetadata<br/>-PinputDirs=run-0,...,run-N<br/>-PoutputDir=output_dir]
    FinalMerge --> Done([return SUCCESS<br/>or BUDGET_EXHAUSTED])
```

Per-iteration semantics:

- All toolchain steps go through Gradle. The phase invokes `./gradlew
  nativeTraceImage`, `./gradlew runNativeTraceImage`, and `./gradlew
  mergeNativeTraceMetadata`. No direct call to `native-image` or
  `native-image-utils` ever appears in the phase implementation.
- The image is **rebuilt every iteration** with all prior raw
  `metadata-run-<i>` directories passed through
  `-PmetadataConfigDirs=run-0,run-1,...`. The Gradle task is responsible
  for translating that into `-H:ConfigurationFileDirectories=...`. No
  merging is required for the build to consume them.
- The traced run's **task exit code is ignored** for termination purposes.
  A run that crashes can still discover new metadata before crashing; that
  trace is kept.
- **Convergence is the termination condition**: when an iteration's
  `metadata-run-<i>` contains no entries that are not already present in the
  union of `metadata-run-0 … metadata-run-<i-1>`, the phase converges. This
  check operates on the raw per-run directories produced by Gradle; no
  merge artifact is produced or required during the loop.
- **Merging happens only once, after the loop terminates.** A single
  `./gradlew mergeNativeTraceMetadata` invocation produces the final
  `<output_dir>`. No intermediate merged directory ever exists.
- The final merge runs whenever **at least one** `metadata-run-<i>` was
  accepted, regardless of how the loop terminated. A later iteration's
  build failure does not throw away earlier successful traces — those are
  still merged into `<output_dir>` so callers (and codex) get whatever
  partial signal exists. The merge is only skipped if no iteration
  produced new metadata.

Status enumeration:

| Status | Meaning | Caller action |
| --- | --- | --- |
| `SUCCESS` | The phase converged (no new metadata in the last iteration) and the final merge succeeded. | Pass the output directory to codex fixup as additional read-only context. |
| `BUDGET_EXHAUSTED` | `max-trace-iterations` reached before convergence; the final merge still ran on whatever was collected. | Pass the partial output directory to codex fixup, plus a note that the budget was exhausted. |
| `BUILD_FAILED` | The trace image build failed in some iteration, **or** the final `mergeNativeTraceMetadata` invocation failed. The output directory may be empty or partial. | Pass whatever was merged plus the failure diagnostics to codex fixup — see §9. |

**Every status routes through codex fixup.** Failure of the exploration
phase is no longer a reason to skip codex. Instead, the failure
diagnostics become part of codex's input — see §9 for the contract.

## 6. Reusable Implementation Surface

A deterministic, side-effect-free Python module owns the loop and is the
sole non-Gradle implementation surface:

```text
utility_scripts/native_metadata_exploration.py
```

The `native_trace_collect` intervention imports this module; it is not
called from anywhere else. Public entry:

```python
def run_native_metadata_exploration(
    reachability_repo_path: str,
    coordinate: str,                          # "group:artifact:version"
    output_dir: str,                          # absolute; caller-chosen, see §4
    condition_packages: list[str] | None = None,
    max_iterations: int = 5,
) -> NativeExplorationResult: ...
```

The phase **does not invent** `output_dir`. It is required, and the caller
is responsible for namespacing it per (library, class) to avoid concurrent
clobbering.

`NativeExplorationResult` carries:

- the status,
- the absolute path to the output directory (echoes the caller's input),
- the absolute path to the per-iteration runs directory (`runs/`),
- the per-iteration build/run log paths,
- the iteration count actually used,
- **`failure`** — populated whenever the status is not `SUCCESS`. Carries:
  - `failed_task` — the Gradle task name that triggered the status
    (`nativeTraceImage`, `runNativeTraceImage`, or
    `mergeNativeTraceMetadata`),
  - `failed_iteration` — the iteration index when applicable, or `None`
    for merge failures,
  - `failure_log_path` — absolute path to the persisted Gradle output for
    the failing task,
  - `failure_summary` — short string extracted from the log (first failed
    Gradle subtask, or the first `BUILD FAILED` block), suitable for
    embedding in a codex prompt without dumping the full log.

Even when `status == BUILD_FAILED`, `output_dir` is still populated
whenever earlier iterations produced accepted traces; callers must inspect
both `failure` and `output_dir`.

The module must not depend on any workflow strategy or intervention.
Sequencing with `generateMetadata`, codex fixup, agent hand-off, etc. is
the responsibility of the `native_trace_collect` intervention that wraps
it (see §8 and §9).

## 7. Required Gradle Support

The phase shells out **only** to `./gradlew`. The reachability repo must
provide the following tasks. They are the contract surface of this phase;
their internal implementation (which native-image flags they pass, where
they put the resulting binary, etc.) is a Gradle-side concern.

### 7.1 `nativeTraceImage`

Builds the test module's native image with metadata-tracing support
enabled.

| Property | Required | Meaning |
| --- | --- | --- |
| `-Pcoordinates=<group:artifact:version>` | yes | Identifies the test module to build. |
| `-PmetadataConfigDirs=<dir1,dir2,...>` | no | Comma-separated absolute paths translated to `-H:ConfigurationFileDirectories=...`. Omitted on the first iteration. |

Behavior:

- The task adds `-H:+UnlockExperimentalVMOptions
  -H:+MetadataTracingSupport -H:-UnlockExperimentalVMOptions` (or the
  equivalent supported invocation) to the native-image build.
- The task produces a binary at a path the next task can locate using only
  `-Pcoordinates=...` (i.e., the path is derivable from the coordinates;
  the phase does not pass the binary path back in).
- A non-zero task exit code is mapped to `BUILD_FAILED`.

### 7.2 `runNativeTraceImage`

Runs the binary produced by `nativeTraceImage` once, capturing traced
metadata to a caller-specified directory. Used by both the deterministic
trace loop in this spec **and** the
[native test verification gate](native-test-verification.md), which drives
it directly per outer cycle.

| Property | Required | Meaning |
| --- | --- | --- |
| `-Pcoordinates=<group:artifact:version>` | yes | Same coordinates used to build. |
| `-PtraceMetadataPath=<absolute path>` | yes | Becomes `-XX:TraceMetadata=path=...`. Must be a fresh per-iteration directory. |
| `-PtraceMetadataConditionPackages=<pkg1,pkg2,...>` | yes | Becomes `-XX:TraceMetadataConditionPackages=...`. |
| `-PmetadataConfigDirs=<dir1,dir2,...>` | no | Comma-separated absolute paths of prior accepted trace dirs; translated to `-H:ConfigurationFileDirectories=...` so the rebuild sees what tracing has already collected. Omitted on the first iteration. |

Build-flag behavior:

- The task adds `-H:+UnlockExperimentalVMOptions
  -H:+MetadataTracingSupport -H:-UnlockExperimentalVMOptions` so the
  binary writes traced metadata at runtime.
- The task **also** adds `--exact-reachability-metadata` and
  `-H:MissingRegistrationReportingMode=Exit`. These ensure the binary
  exits with `ExitStatus.MISSING_METADATA` (172) when an access misses
  the metadata supplied via `-PmetadataConfigDirs`, instead of throwing.
  The verification gate routes on this exit code; the trace-only loop in
  this spec ignores it (its termination is convergence-based, see §5).

Runtime behavior:

- The task always invokes the binary the same way. There are no
  caller-supplied program arguments.
- The Exec uses `ignoreExitValue=true`, so the binary's exit code does
  not fail the Gradle invocation. Callers recover the actual binary exit
  code from the captured Gradle log (Gradle prints
  `... finished with non-zero exit value N` for any non-zero exit).
- For the deterministic trace loop (§5), the task's exit code remains
  **informational only**: the loop terminates on convergence, budget
  exhaustion, or a `nativeTraceImage` build failure — never on the run
  task's exit code.

### 7.3 `mergeNativeTraceMetadata`

Wraps `native-image-utils generate`. Invoked **once** per phase
invocation, after the loop has terminated.

| Property | Required | Meaning |
| --- | --- | --- |
| `-PinputDirs=<dir1,dir2,...>` | yes | Absolute paths of every accepted `metadata-run-<i>` directory. |
| `-PoutputDir=<absolute path>` | yes | Caller-supplied output directory (§4). Pre-existing contents are replaced. |

Behavior:

- The task fails the phase with a non-`SUCCESS` status only if the merge
  itself fails (the existing status enum does not currently distinguish
  this; the loop maps a merge failure to `BUILD_FAILED` and surfaces the
  task output in the result's `failure` block).
- The task is the **only** point at which `native-image-utils` is invoked.

### 7.4 General requirements

- `gh` / `gradle` toolchain configuration (GraalVM home, Java home, native
  image binary, `native-image-utils` location) is the reachability repo's
  responsibility. The Python phase does not export, override, or check any
  toolchain environment variables beyond what Gradle already requires.
- All four tasks must accept being invoked with `--no-daemon` and must be
  idempotent across phase invocations on the same coordinate (i.e., a
  rerun must be possible after the phase exits, regardless of status).

Concrete Gradle wiring is out of scope for this spec but is a prerequisite
for implementation.

## 8. Callers

The sole production caller is the
[`native_trace_collect`](workflow-strategies.md#53-native_trace_collect)
post-generation intervention. Strategies do not invoke this loop directly;
they enable it by setting `post-generation-intervention.name` to
`native_trace_collect` in their `predefined_strategies.json` entry. The
intervention is responsible for sequencing the trace loop with codex
fixup and the Pi fall-through; this spec only defines the loop contract.

Workflow-specific guidance on when to choose `native_trace_collect` over
`codex_then_pi` lives with each strategy spec:

| Strategy / workflow | Where the routing decision is documented |
| --- | --- |
| Dynamic-access workflow | [dynamic-access-workflow.md](dynamic-access-workflow.md) |
| Native-run failure fix workflow | [fix-java-run-fail.md](fix-java-run-fail.md) |

## 9. Failure Handoff to Codex Fixup

> The hand-off is owned by the `native_trace_collect` intervention. This
> section specifies what diagnostics the loop must surface and how the
> intervention is required to use them; it does **not** mean the trace
> loop itself shells out to codex (see §10).

A failed exploration is **not** evidence that the problem is purely a
metadata gap. The Gradle tasks fail for a wide range of reasons:

- A test class does not compile under metadata-tracing flags.
- The trace binary crashes immediately for reasons unrelated to missing
  metadata (e.g. a missing dependency, a wrong main class, a JVM-only
  test that does not run under native image at all).
- `--exact-reachability-metadata` fails because the test exercises a
  package outside the configured condition packages.
- The merge task hits a malformed per-run trace.

Some of these are non-metadata code problems that only the agent can fix.
Therefore the phase produces a **diagnostic-rich result on every status**
and the calling specs must treat every status as eligible for codex
fixup.

### 9.1 Loop responsibilities

The trace loop itself never invokes codex (this remains a hard rule —
see §10). On any non-`SUCCESS` outcome it must:

1. Persist the full Gradle stdout/stderr of the failing task to a stable
   absolute path under the runs directory.
2. Populate `result.failure.failed_task`, `failed_iteration`,
   `failure_log_path`, and `failure_summary` (§6).
3. Run the final merge if any prior iteration produced new traces, so
   `result.output_dir` carries whatever partial signal exists.
4. Return; the caller decides how to use the result.

### 9.2 Intervention responsibilities

The `native_trace_collect` intervention routes the result to
`run_codex_metadata_fix(...)` for **every** status — including `SUCCESS`,
where the merged trace dir becomes ground-truth context for codex, and
`BUILD_FAILED`, where it becomes diagnostics. The codex invocation must
receive, at minimum:

- The coordinate.
- `result.output_dir` (added to read-only context, even if empty or
  partial — codex must be told this is "natively-collected metadata so
  far, may be incomplete").
- `result.failure.failure_summary` (always inlined into the prompt so
  codex sees what failed in plain text).
- `result.failure.failure_log_path` (added to read-only context so codex
  can read the full failing build log when the summary is insufficient).

The recommended prompt shape for codex when status is not `SUCCESS`:

```text
Native metadata exploration for {coordinate} did not converge cleanly.

Final status: {status}
Failed task: {failure.failed_task}
Iteration: {failure.failed_iteration}
Summary: {failure.failure_summary}

Partially-collected metadata is in:
  {output_dir}
The full failing build log is in:
  {failure.failure_log_path}

Investigate the failure. If it is a missing-metadata problem, complete
the metadata under {coordinate} using the partial output as a starting
point. If it is a non-metadata code problem (compilation error, wrong
main class, native-incompatible test code, etc.), fix the underlying
issue in the test sources first; metadata can be regenerated afterwards.
```

The current `run_codex_metadata_fix(reachability_repo_path, coordinates)`
helper takes only the coordinate. To support the handoff, it must be
extended to accept the result object (or the relevant fields) and render
this prompt accordingly. That extension is a follow-up implementation
task; this spec defines the contract.

### 9.3 Routing rule

The intervention always chains: trace loop → codex fixup → Pi fall-through
(when codex cannot recover). The only reason this chain does not run is
that the post-iteration `./gradlew test` already passed, in which case
`_run_test_with_retry` does not invoke any intervention.

```mermaid
flowchart LR
    Explore[run_native_metadata_exploration] --> Status{status}
    Status -- "SUCCESS" --> CodexCtx[codex fixup with output_dir as context]
    Status -- "BUDGET_EXHAUSTED" --> CodexCtx3[codex fixup with partial output_dir + budget note]
    Status -- "BUILD_FAILED" --> CodexFail[codex fixup with partial output_dir + failure diagnostics<br/>see §9.2]
    CodexCtx --> PiFall[Pi fall-through if tests still fail]
    CodexCtx3 --> PiFall
    CodexFail --> PiFall
    PiFall --> Result[InterventionResult: passed / passed_with_intervention / failed]
```

## 10. Acceptance Criteria

A `run_native_metadata_exploration(...)` invocation is correct iff:

1. The phase shells out **only** to `./gradlew`. It must not invoke
   `native-image`, `native-image-utils`, or any other toolchain binary
   directly. The four Gradle tasks listed in §7 are the entire toolchain
   surface.
2. The caller-supplied `output_dir` and the sibling per-run directory are
   removed (or recreated empty) at the start of the call so no stale
   entries leak across runs.
3. Each iteration **rebuilds** the trace image by invoking `./gradlew
   nativeTraceImage`, passing all prior raw `metadata-run-<i>` directories
   as `-PmetadataConfigDirs=run-0,run-1,...` from the second iteration
   onward. The first iteration omits `-PmetadataConfigDirs`.
4. Each iteration runs the binary exactly once via `./gradlew
   runNativeTraceImage`, with `-PtraceMetadataPath=` pointing at a fresh
   per-iteration path under the runs directory.
5. The `runNativeTraceImage` task exit code does not affect termination.
6. **Convergence check** — after each run, the phase compares the new
   `metadata-run-<i>` to the union of prior `metadata-run-<j>` directories.
   If `metadata-run-<i>` contains no entries that are not already present
   in that union, the loop terminates with convergence.
7. The loop also terminates when (a) the iteration budget is exhausted, or
   (b) `nativeTraceImage` fails.
8. **`./gradlew mergeNativeTraceMetadata` is invoked at most once per
   phase invocation**, after the loop terminates, with `-PinputDirs=`
   listing every accepted `metadata-run-<i>` and
   `-PoutputDir=<output_dir>`. The merge runs whenever at least one
   iteration produced new metadata, **regardless of how the loop
   terminated** — a later `BUILD_FAILED` does not discard earlier
   successful traces. The merge is skipped only when no iteration
   produced new metadata, in which case `<output_dir>` is left empty.
9. On any non-`SUCCESS` status, the result's `failure` field is
   populated with `failed_task`, `failed_iteration` (when applicable),
   `failure_log_path` (an absolute path to a persisted Gradle log), and
   a short `failure_summary`. This information is the sole contract for
   callers handing the failure off to codex fixup (§9).
10. The function returns the status enum, the absolute output directory,
    the absolute per-run directory, and (on failure) the diagnostic
    fields described in §6 and §9.
11. No code path inside the loop shells out to codex fixup, the agent,
    or any LLM. The loop is purely deterministic. Routing the result to
    codex (and the Pi fall-through) is the responsibility of the
    `native_trace_collect` intervention that wraps it.
