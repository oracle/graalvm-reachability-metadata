# Native Test Verification — Specification

> Uses the Gradle native-tracing task contract from
> [Native metadata exploration](native-metadata-exploration.md), introduced in
> [oracle/graalvm-reachability-metadata#3379](https://github.com/oracle/graalvm-reachability-metadata/pull/3379).
>
> **See also:** [Dynamic-access workflow](dynamic-access-workflow.md) ·
> [Java fail-fix workflow](fix-java-run-fail.md) ·
> [Workflow strategies & interventions](workflow-strategies.md).

## 1. Purpose

The verification gate ensures that the test binary passes on Native Image
for a given coordinate. The ordered recovery contract is:

1. Try the normal JVM `native-image-agent` path first via
   `./gradlew generateMetadata -Pcoordinates=<g:a:v> --agentAllowedPackages=fromJar
   --metadataOutputDir=<output_dir>/agent`. This is the primary metadata
   source and must not be skipped. It is staged separately from durable
   repository metadata. If this path fails, continue with native tracing
   instead of routing directly to Codex.
2. If JVM-agent metadata was generated, run the regular coordinate validation
   (`./gradlew test -Pcoordinates=<g:a:v>
   -PmetadataConfigDirs=<output_dir>/agent`). If the native tests pass, merge
   the staged agent metadata into durable repository metadata and return
   `PASSED`.
3. If native testing still fails after JVM-agent metadata was generated, or if
   the JVM-agent metadata path failed, use native tracing as a fallback. Each
   fallback cycle invokes
   `./gradlew runNativeTraceImage`; the trace binary is built with
   `-H:+MetadataTracingSupport`, `--exact-reachability-metadata`, and
   `-H:MissingRegistrationReportingMode=Exit`.
4. If native tracing converges, merge the accepted trace dirs into
   `<output_dir>/trace`, then run one final native-image-utils merge over
   existing durable metadata, `<output_dir>/agent` when present, and
   `<output_dir>/trace` when present. The final merge result is copied to
   `metadata/<group>/<artifact>/<version>/reachability-metadata.json`, and
   the gate returns `PASSED`. If tracing stalls, exhausts its budget, or
   fails for a non-metadata reason, route the accumulated diagnostics to
   codex. Codex is the final recovery step — the only failures that bypass
   it are failures of the post-success trace merge or final durable merge,
   which are infrastructure problems codex cannot repair and therefore
   terminate as `FAILED` directly (see §4 gate semantics).

Pi is **not** invoked. Removing failing tests would mask exactly the code
issues that must surface to the coding agent.

The gate is the per-class success criterion for the dynamic-access workflow
and a reusable terminal gate for any workflow whose acceptance contract is
"Native Image tests must pass" — for example, the native-run failure fix
workflow after the agent has produced its last edit.

Native Image must always work. A gate result of `FAILED` is therefore a hard
error: the calling workflow must return `RUN_STATUS_FAILURE` and reset the
branch to its checkpoint.

## 2. Inputs

| Input | Source | Notes |
| --- | --- | --- |
| Coordinate `group:artifact:version` | Caller | Identifies the test module. |
| Reachability repo path | Caller | Working directory for Gradle. |
| Output directory | Caller | Absolute staging root for this gate invocation. The caller picks a path namespaced per (library, class) for the dynamic-access caller, or per coordinate for non-class-scoped callers — same convention as [native-metadata-exploration.md §4](native-metadata-exploration.md#4-output). The gate writes JVM-agent metadata to `<output_dir>/agent`, merged trace metadata to `<output_dir>/trace`, and writes durable repository metadata only after the final native-image-utils merge succeeds. |
| Condition packages | `condition_packages` argument to `verify_native_test_passes` | Default `[group]`. Passed to the binary at run time as `-XX:TraceMetadataConditionPackages=...`. |
| Outer budget | Strategy parameter `max-native-test-verification-iterations` | Default **100**. In practice convergence is expected within a handful of cycles; the high default is a soft cap, not a target. Each cycle rebuilds `nativeTestCompile`, so the wall-clock cost is dominated by native-image build time. |
| Per-cycle timeout | `cycle_timeout_seconds` argument | Default 30 minutes. Caps the preflight `./gradlew test` invocation and each `runNativeTraceImage` invocation; on timeout the step is treated as a non-zero exit and routed to codex. |

## 3. Outputs

`NativeTestVerificationResult` carries:

- `status` — `PASSED`, `PASSED_WITH_INTERVENTION`, or `FAILED`.
- `output_dir` — echoes the caller's input. `<output_dir>/agent` contains
  staged JVM-agent metadata when `generateMetadata` succeeds.
  `<output_dir>/trace` contains merged trace metadata only when a fallback
  trace cycle reaches binary exit `0`. The durable
  `metadata/<group>/<artifact>/<version>/reachability-metadata.json` file is
  updated only by the final native-image-utils merge. On `FAILED`, staging
  dirs may contain partially completed agent or trace output.
- `iterations_used` — number of outer cycles consumed.
- `last_native_test_log_path` — absolute path to the last relevant
  Gradle log (`generateMetadata`, `test`, or `runNativeTraceImage`).
  Required when status is `FAILED`; callers surface it in run metrics and
  the PR description.
- `last_native_test_exit_code` — the verification binary's own exit code
  parsed from the Gradle log (e.g. `172`), or `None` when no exit-value
  line was captured.
- `accepted_run_dirs` — absolute paths of the per-cycle trace dirs that
  produced 172 and were folded into the running `metadataConfigDirs`.
- `intervention_records` — ordered list of `{stage, kind: "codex",
  log_path}` entries (zero or one entry; codex is invoked at most once
  per gate invocation).

## 4. Loop

```mermaid
flowchart TD
    Start([invoke gate]) --> Init[reset output_dir + runs_dir<br/>agent_dir = output_dir/agent<br/>trace_dir = output_dir/trace<br/>config_dirs = []<br/>i = 0]
    Init --> Agent[gradlew generateMetadata<br/>--agentAllowedPackages=fromJar<br/>--metadataOutputDir=agent_dir]
    Agent -- fail --> Outer{i &lt; max-native-test-verification-iterations?}
    Agent -- pass --> Test[gradlew test<br/>-Pcoordinates=&lt;g:a:v&gt;<br/>-PmetadataConfigDirs=agent_dir]
    Test -- pass --> FinalAgent[final merge<br/>durable + agent_dir]
    FinalAgent --> PassAgent([return PASSED])
    Test -- fails before nativeTest --> Codex
    Test -- nativeTest fails --> Outer
    Outer -- no --> Codex
    Outer -- yes --> Run[gradlew runNativeTraceImage<br/>-PtraceMetadataPath=runs/cycle-i<br/>-PtraceMetadataConditionPackages=&lt;packages&gt;<br/>-PmetadataConfigDirs=&lt;config_dirs&gt;]
    Run --> Route{binary exit code}
    Route -- 0 --> MergeTrace[mergeNativeTraceMetadata<br/>accepted trace dirs &rarr; trace_dir]
    MergeTrace --> FinalTrace[final merge<br/>durable + agent_dir + trace_dir]
    FinalTrace --> Pass([return PASSED])
    Route -- 172 --> Append[config_dirs += runs/cycle-i]
    Append --> Bump[i = i + 1]
    Bump --> Outer
    Route -- 172 without new metadata --> Codex
    Route -- other --> Codex
    Codex --> CodexOK{codex rc == 0?}
    CodexOK -- yes --> PassI([return PASSED_WITH_INTERVENTION])
    CodexOK -- no --> FailCode([return FAILED<br/>code-failure])
```

Gate semantics:

- **JVM agent first.** The first metadata action is always
  `./gradlew generateMetadata -Pcoordinates=<g:a:v>
  --agentAllowedPackages=fromJar --metadataOutputDir=<output_dir>/agent`.
  The JVM agent observes the test suite on HotSpot and writes staged agent
  metadata. Native tracing must not run before this step. If metadata
  generation fails, the gate logs the failure and starts native tracing with
  no accepted trace dirs.
- **Native test run second.** After JVM-agent metadata is generated, the
  gate runs `./gradlew test -Pcoordinates=<g:a:v>
  -PmetadataConfigDirs=<output_dir>/agent`. A pass finalizes staged agent
  metadata into durable repository metadata and returns `PASSED` without
  invoking native tracing. A failure before `nativeTest` is treated as a
  code/test problem and is routed to codex.
- **Native tracing is fallback.** The trace loop starts after JVM-agent
  metadata generation fails, or after JVM-agent metadata exists and native
  testing still fails.
- **One Gradle invocation per trace cycle.** The cycle runs only
  `./gradlew runNativeTraceImage -Pcoordinates=<g:a:v>
  -PtraceMetadataPath=<runs_dir>/cycle-<i>
  -PtraceMetadataConditionPackages=<packages>
  -PmetadataConfigDirs=<config_dirs>` where `config_dirs` is the staged
  agent dir, when present, followed by accepted trace dirs. The last
  property is omitted only when no staged agent metadata and no accepted
  trace dirs exist. The resulting binary is built with `-H:+MetadataTracingSupport`,
  `--exact-reachability-metadata`, and
  `-H:MissingRegistrationReportingMode=Exit` (see
  [native-metadata-exploration.md §7.2](native-metadata-exploration.md#72-runnativetraceimage)).
  The same execution writes a trace dir **and** returns an
  exact-metadata-aware exit code.
- **Exit-code routing**:
  - `0` → the metadata accumulated in `config_dirs` plus the code under
    test are sufficient. The gate runs `mergeNativeTraceMetadata` to
    populate `<output_dir>/trace` with merged accepted trace metadata, then
    runs the final native-image-utils merge over durable metadata, staged
    agent metadata, and staged trace metadata before returning `PASSED`.
  - `172` → `ExitStatus.MISSING_METADATA`. The trace dir captured at
    least one missing access; appending it to `config_dirs` makes the
    next cycle's build see that metadata. If the current `172` cycle
    produces no new canonical metadata entries compared with accepted
    trace dirs, tracing has stalled; the gate prints the accumulated
    progress, prints the failure-log tail, and routes to codex.
  - any other non-zero → the test or library code is broken in a way
    that more metadata cannot fix. Codex finishes it: codex is invoked
    once, and the gate returns based on codex's exit code. The gate does
    **not** re-run `runNativeTraceImage` after codex.
- **Codex is terminal when invoked.** Codex with the
  `fix-missing-reachability-metadata` skill has full repo write access
  and validates its own work; the gate does not second-guess by re-
  verifying. This avoids the gate getting stuck in a codex/verify ping-
  pong on a real code defect.
- **Pi is not used.** Pi's role in `codex_then_pi` is to remove failing
  tests when codex cannot recover — exactly the wrong move when the
  failure is a real code/test bug we want surfaced.
- **Codex after trace failure.** `metadata-gap-exhausted`, stalled
  metadata progress, trace timeouts, and non-172 trace failures all route
  to codex with a reproduction command that includes the accepted trace
  dirs as `metadataConfigDirs`. The codex process and codex instructions
  must pin `GRAALVM_HOME`, `JAVA_HOME`, and the full `native-image --version`
  output to the exact GraalVM distribution used by the failed gate command.
  Codex must fail instead of reproducing or verifying with a different
  GraalVM installation. `FAILED` is returned only when codex does not
  converge.
- **Codex keeps prior config_dirs.** Codex's fixes are additive; the gate
  does not discard `config_dirs` before codex runs. (Codex returns
  terminally so the question of carrying state across codex is moot for
  the current design, but the contract preserves the dirs in the result
  for diagnostics.)
- **Hard fail.** If codex does not converge, the gate returns `FAILED` and
  the calling workflow aborts; see §6.
- **Final merge failures are terminal without codex.** After the gate has
  enough staged metadata to pass, it performs one durable native-image-utils
  merge. The inputs are existing durable metadata when present,
  `<output_dir>/agent` when present, and `<output_dir>/trace` when present.
  The merge result is copied to
  `metadata/<group>/<artifact>/<version>/reachability-metadata.json`. If
  the trace-output merge or final durable merge fails, the gate returns
  `FAILED` directly. Codex is **not** invoked for these failures: they are
  infrastructure problems (Gradle merge task, filesystem write, malformed
  metadata input) downstream of a successful validation path, and codex
  cannot repair the metadata pipeline itself. The calling workflow handles
  them like any other `FAILED` (§6). This is the only carve-out from the
  "only codex failure may FAIL" invariant — every other failure mode
  (JVM-agent failure, `gradlew test` failure before `nativeTest`, `172`
  with no usable / no new metadata, non-`0`/`172` trace cycle exit, budget
  exhaustion) routes through codex first.

## 5. Reusable Implementation Surface

```text
utility_scripts/native_test_verification.py
```

Public entry:

```python
def verify_native_test_passes(
    reachability_repo_path: str,
    coordinate: str,
    output_dir: str,
    condition_packages: list[str] | None = None,
    max_iterations: int = 100,
    cycle_timeout_seconds: int = 30 * 60,
) -> NativeTestVerificationResult: ...
```

The module composes existing helpers and owns no domain logic of its own:

- `./gradlew generateMetadata -Pcoordinates=...
  --agentAllowedPackages=fromJar --metadataOutputDir=<output_dir>/agent` —
  primary JVM-agent metadata collection, attempted before the trace loop and
  staged outside durable repository metadata.
- `./gradlew test -Pcoordinates=...
  -PmetadataConfigDirs=<output_dir>/agent` — normal coordinate validation
  after JVM-agent metadata is available.
- `./gradlew runNativeTraceImage -Pcoordinates=...
  -PtraceMetadataPath=<runs_dir>/cycle-<i>
  -PtraceMetadataConditionPackages=<packages>
  -PmetadataConfigDirs=<agent dir,accepted trace dirs>` — fallback trace-cycle execution.
- `./gradlew mergeNativeTraceMetadata -PinputDirs=...
  -PoutputDir=<output_dir>/trace` — trace-dir merge on trace-backed
  `PASSED`.
- `./gradlew mergeNativeTraceMetadata -PinputDirs=...
  -PoutputDir=<temporary merge dir>` — final durable merge of existing
  durable metadata plus staged agent and trace metadata.
- `run_codex_metadata_fix` — codex acts as the coding agent after JVM-agent
  metadata plus native tracing fail to produce passing native tests. Pi is
  **not** invoked.

The standalone trace-loop driver in
[`utility_scripts/native_metadata_exploration.py`](native-metadata-exploration.md)
is **not** used by this gate; that module remains for callers (e.g. the
`native_trace_collect` post-generation intervention) that want a
deterministic trace-only loop without verification.

This module must not depend on any workflow strategy or post-generation
intervention.

## 6. Callers

| Caller | Where in flow | Output-dir convention |
| --- | --- | --- |
| `dynamic_access_iterative` per-class loop ([dynamic-access-workflow.md §6.2 / §6.4](dynamic-access-workflow.md)) | After every class with a coverage gain (Resolved or PartialCommit) | `tests/src/<group>/<artifact>/<version>/build/natively-collected/<class-key>/` |
| `fix_java_run_fail` native-mode path ([fix-java-run-fail.md](fix-java-run-fail.md)) | After the agent's final edit, as the success gate | `tests/src/<group>/<artifact>/<version>/build/natively-collected/_global_/` |

The dynamic-access caller invokes the gate per class; the fix-native-run
caller invokes it once per workflow run. Both treat `FAILED` as a hard
workflow failure.

## 7. Acceptance Criteria

A `verify_native_test_passes(...)` invocation is correct iff:

1. The output directory and the sibling `runs/` directory are reset (or
   recreated empty) at the start of the call so no stale entries leak
   across runs.
2. Before any native-trace cycle starts, the gate invokes
   `./gradlew generateMetadata -Pcoordinates=...
   --agentAllowedPackages=fromJar --metadataOutputDir=<output_dir>/agent`.
3. If JVM-agent metadata generation fails, the gate starts native tracing
   with no accepted trace dirs.
4. After successful JVM-agent metadata generation, the gate invokes
   `./gradlew test -Pcoordinates=...
   -PmetadataConfigDirs=<output_dir>/agent`. If the coordinate passes,
   finalize staged agent metadata into durable repository metadata and return
   `PASSED` without native tracing.
5. Each fallback outer cycle invokes `./gradlew runNativeTraceImage
   -Pcoordinates=... -PtraceMetadataPath=<runs_dir>/cycle-<i>
   -PtraceMetadataConditionPackages=<packages>` exactly once, with
   `-PmetadataConfigDirs=<agent dir,accepted trace dirs>` appended whenever
   staged agent metadata or any prior accepted trace cycle exists.
6. The verification binary's exit code is recovered from Gradle's
   "exit value N" log line and routed:
   - `0` → run `mergeNativeTraceMetadata` once with all accepted run
     dirs as `-PinputDirs=` and `<output_dir>/trace` as `-PoutputDir=`,
     then run one final native-image-utils merge of durable metadata,
     `<output_dir>/agent`, and `<output_dir>/trace`, copy that result to
     `metadata/<group>/<artifact>/<version>/reachability-metadata.json`,
     and return `PASSED`. If either merge fails, return `FAILED` directly
     — codex is **not** invoked for these post-success infrastructure
     failures.
   - `172` with new trace metadata entries → append
     `runs_dir/cycle-<i>` to the running config dirs and continue to the
     next outer cycle. Codex is **not** invoked.
   - `172` with no new trace metadata entries → print the accumulated
     progress and failure-log tail, then invoke codex.
   - any other non-zero → invoke `run_codex_metadata_fix` once and
     return based on its exit code: `PASSED_WITH_INTERVENTION` on codex
     success, `FAILED` on codex failure. The gate does not re-run
     `runNativeTraceImage` after codex.
7. The gate never invokes `run_pi_post_generation_fix`.
8. The function honors `max-native-test-verification-iterations` and never
   exceeds the configured outer budget. Reaching the budget without a
   passing exit invokes codex with reason "metadata-gap-exhausted".
9. On `FAILED`, the result includes a non-empty
   `last_native_test_log_path`, a populated
   `last_native_test_exit_code` (when the binary actually ran), the
   ordered `accepted_run_dirs` list, and at most one entry in
   `intervention_records`.
10. Callers that observe `FAILED` propagate `RUN_STATUS_FAILURE` and reset
   the feature branch to their checkpoint.
