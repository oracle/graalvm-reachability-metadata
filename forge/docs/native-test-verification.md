# Native Test Verification — Specification

> Built on top of the [Native metadata exploration](native-metadata-exploration.md)
> trace loop introduced in
> [oracle/graalvm-reachability-metadata#3379](https://github.com/oracle/graalvm-reachability-metadata/pull/3379).
>
> **See also:** [Dynamic-access workflow](dynamic-access-workflow.md) ·
> [Java fail-fix workflow](fix-java-run-fail.md) ·
> [Workflow strategies & interventions](workflow-strategies.md).

## 1. Purpose

The verification gate ensures that the test binary passes on Native Image
for a given coordinate. Each outer cycle invokes a single Gradle command
— `./gradlew runNativeTraceImage` — whose binary is built with
`-H:+MetadataTracingSupport` (collects metadata) plus
`--exact-reachability-metadata` and
`-H:MissingRegistrationReportingMode=Exit` (causes
`ExitStatus.MISSING_METADATA` 172 on missing metadata instead of throwing).
A single binary execution therefore produces both signals: a per-cycle
trace dir and the binary's own exit code. The gate routes on that exit
code:

- `0`              -> `PASSED` (or `PASSED_WITH_INTERVENTION` if codex ran
  earlier in this gate invocation).
- `172`            -> metadata gap; append the cycle's trace dir to the
  running `metadataConfigDirs` so the next cycle's build sees it, then
  continue. If a later `172` cycle produces no new trace metadata
  entries, print the accumulated progress and fail fast because another
  rebuild would repeat the same missing-metadata state.
- any other non-0  -> code/test failure; route to codex (the coding
  agent). Codex finishes it: on codex success return
  `PASSED_WITH_INTERVENTION`; on codex failure return `FAILED`. The gate
  does **not** re-verify after codex.

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
| Output directory | Caller | Absolute path. The caller picks a path namespaced per (library, class) for the dynamic-access caller, or per coordinate for non-class-scoped callers — same convention as [native-metadata-exploration.md §4](native-metadata-exploration.md#4-output). On `PASSED`, the gate merges all accepted per-cycle trace dirs into this directory (one `mergeNativeTraceMetadata` invocation). |
| Condition packages | `condition_packages` argument to `verify_native_test_passes` | Default `[group]`. Passed to the binary at run time as `-XX:TraceMetadataConditionPackages=...`. |
| Outer budget | Strategy parameter `max-native-test-verification-iterations` | Default **100**. In practice convergence is expected within a handful of cycles; the high default is a soft cap, not a target. Each cycle rebuilds `nativeTestCompile`, so the wall-clock cost is dominated by native-image build time. |
| Per-cycle timeout | `cycle_timeout_seconds` argument | Default 30 minutes. Caps a single `runNativeTraceImage` invocation; on timeout the cycle is treated as a non-zero binary exit and routed to codex. |

## 3. Outputs

`NativeTestVerificationResult` carries:

- `status` — `PASSED`, `PASSED_WITH_INTERVENTION`, or `FAILED`.
- `output_dir` — echoes the caller's input. Populated with merged trace
  metadata when status is `PASSED` / `PASSED_WITH_INTERVENTION` (one
  `mergeNativeTraceMetadata` invocation at the end). Empty on `FAILED`.
- `iterations_used` — number of outer cycles consumed.
- `last_native_test_log_path` — absolute path to the last
  `runNativeTraceImage` Gradle log. Required when status is `FAILED`;
  callers surface it in run metrics and the PR description.
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
    Start([invoke gate]) --> Init[reset output_dir + runs_dir<br/>config_dirs = []<br/>i = 0]
    Init --> Outer{i &lt; max-native-test-verification-iterations?}
    Outer -- no --> FailExhausted([return FAILED<br/>metadata-gap-exhausted])
    Outer -- yes --> Run[gradlew runNativeTraceImage<br/>-PtraceMetadataPath=runs/cycle-i<br/>-PtraceMetadataConditionPackages=&lt;packages&gt;<br/>-PmetadataConfigDirs=&lt;config_dirs&gt;]
    Run --> Route{binary exit code}
    Route -- 0 --> Merge[mergeNativeTraceMetadata<br/>config_dirs &rarr; output_dir]
    Merge --> Pass([return PASSED<br/>or PASSED_WITH_INTERVENTION])
    Route -- 172 --> Append[config_dirs += runs/cycle-i]
    Append --> Bump[i = i + 1]
    Bump --> Outer
    Route -- other --> Codex[run_codex_metadata_fix]
    Codex --> CodexOK{codex rc == 0?}
    CodexOK -- yes --> PassI([return PASSED_WITH_INTERVENTION])
    CodexOK -- no --> FailCode([return FAILED<br/>code-failure])
```

Per-cycle semantics:

- **One Gradle invocation per cycle.** The cycle runs only
  `./gradlew runNativeTraceImage -Pcoordinates=<g:a:v>
  -PtraceMetadataPath=<runs_dir>/cycle-<i>
  -PtraceMetadataConditionPackages=<packages>
  -PmetadataConfigDirs=<config_dirs>` (the last property is omitted on
  the first cycle when no trace dirs have been accepted yet). The
  resulting binary is built with `-H:+MetadataTracingSupport`,
  `--exact-reachability-metadata`, and
  `-H:MissingRegistrationReportingMode=Exit` (see
  [native-metadata-exploration.md §7.2](native-metadata-exploration.md#72-runnativetraceimage)).
  The same execution writes a trace dir **and** returns an
  exact-metadata-aware exit code.
- **Exit-code routing**:
  - `0` → the metadata accumulated in `config_dirs` plus the code under
    test are sufficient. The gate runs a single
    `mergeNativeTraceMetadata` to populate `output_dir` with the merged
    metadata and returns.
  - `172` → `ExitStatus.MISSING_METADATA`. The trace dir captured at
    least one missing access; appending it to `config_dirs` makes the
    next cycle's build see that metadata. Codex is **not** invoked. If
    the current `172` cycle produces no new canonical metadata entries
    compared with accepted trace dirs, the gate prints the accumulated
    progress, prints the failure-log tail, and returns `FAILED`.
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
- **Two failure reasons, one status.** `FAILED` is returned on
  `metadata-gap-exhausted` (172 every cycle until the budget runs out) or
  `code-failure` (codex did not converge on a non-172 failure). The
  diagnostic is recorded in the result's `intervention_records`,
  `accepted_run_dirs`, and `last_native_test_log_path`.
- **Codex keeps prior config_dirs.** Codex's fixes are additive; the gate
  does not discard `config_dirs` before codex runs. (Codex returns
  terminally so the question of carrying state across codex is moot for
  the current design, but the contract preserves the dirs in the result
  for diagnostics.)
- **Hard fail.** Either failure reason aborts the calling workflow; see §6.

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

- `./gradlew runNativeTraceImage -Pcoordinates=...
  -PtraceMetadataPath=<runs_dir>/cycle-<i>
  -PtraceMetadataConditionPackages=<packages>
  -PmetadataConfigDirs=<accepted dirs>` — the per-cycle execution.
- `./gradlew mergeNativeTraceMetadata -PinputDirs=...
  -PoutputDir=<output_dir>` — single final merge on `PASSED`.
- `run_codex_metadata_fix` — codex acts as the coding agent on non-172
  failures and is the terminal step. Pi is **not** invoked.

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
2. Each outer cycle invokes `./gradlew runNativeTraceImage
   -Pcoordinates=... -PtraceMetadataPath=<runs_dir>/cycle-<i>
   -PtraceMetadataConditionPackages=<packages>` exactly once, with
   `-PmetadataConfigDirs=<accepted dirs>` appended whenever any prior
   cycle was accepted.
3. The verification binary's exit code is recovered from Gradle's
   "exit value N" log line and routed:
   - `0` → run `mergeNativeTraceMetadata` once with all accepted run
     dirs as `-PinputDirs=` and the caller's `output_dir` as
     `-PoutputDir=`, then return `PASSED` (or
     `PASSED_WITH_INTERVENTION` if codex ran in an earlier cycle).
   - `172` with new trace metadata entries → append
     `runs_dir/cycle-<i>` to the running config dirs and continue to the
     next outer cycle. Codex is **not** invoked.
   - `172` with no new trace metadata entries → print the accumulated
     progress and failure-log tail, then return `FAILED`.
   - any other non-zero → invoke `run_codex_metadata_fix` once and
     return based on its exit code: `PASSED_WITH_INTERVENTION` on codex
     success, `FAILED` on codex failure. The gate does not re-run
     `runNativeTraceImage` after codex.
4. The gate never invokes `run_pi_post_generation_fix`.
5. The function honors `max-native-test-verification-iterations` and never
   exceeds the configured outer budget. Reaching the budget without a
   passing exit returns `FAILED` with reason "metadata-gap-exhausted".
6. On `FAILED`, the result includes a non-empty
   `last_native_test_log_path`, a populated
   `last_native_test_exit_code` (when the binary actually ran), the
   ordered `accepted_run_dirs` list, and at most one entry in
   `intervention_records`.
7. Callers that observe `FAILED` propagate `RUN_STATUS_FAILURE` and reset
   the feature branch to their checkpoint.
