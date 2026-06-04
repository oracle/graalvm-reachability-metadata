# Saga: Basic Iterative Runtime API Benchmark

## Context

Metadata Forge currently has `basic_iterative` strategies that generate tests without source context and dynamic-access strategies that use `generateDynamicAccessCoverageReport` to show exact uncovered runtime-access call sites to the agent. The proposed experiment adds a middle strategy: keep the `basic_iterative` workflow, but give the agent downloaded main source context plus a static runtime API recognition table. The goal is to test whether the agent can find and exercise dynamic-access-relevant source paths without receiving the dynamic-access report itself.

The runtime API table should describe APIs such as `java.lang.Class#getMethod(java.lang.String, java.lang.Class[])`, `java.lang.reflect.Method#invoke(java.lang.Object, java.lang.Object[])`, serialization APIs, resource lookup APIs, and proxy APIs. It must not include per-library dynamic-access report call sites or class-specific uncovered-call instructions.

## Goals

- Add a new `basic_iterative` strategy variant that requests main source context.
- Add specialized prompt templates for the new strategy that tell the agent to inspect source context for runtime API usage and write public-API tests that reach those paths.
- Keep the workflow implementation as `basic_iterative`; do not reuse `dynamic_access_iterative`, `optimistic_dynamic_access`, or the dynamic-access report prompt.
- Add benchmark configurations for Genesis and Nexus so the new strategy can be compared against existing basic and dynamic-access runs.
- Preserve benchmark-mode metrics compatibility, including final `generateLibraryStats` output that can still report dynamic-access coverage after the run.

## Tasks

### Task 1: Define the Runtime API Table Prompt Content
**State:** `completed`

Create a reusable prompt fragment that lists runtime APIs the agent should search for in source context. The table should be API-level, not benchmark-level and not library-level.

#### Subtask 1.1: Choose Prompt Location

Add a prompt template under `forge/prompt_templates/initial/` or `forge/prompt_templates/persistent/`. Prefer an initial prompt if the guidance should be visible at test-generation time only; prefer persistent instructions if the guidance should also survive failed-iteration loops.

#### Subtask 1.2: Include API Categories

Cover these semantic categories:

- reflection: `Class#forName`, `Class#getMethod`, `Class#getDeclaredMethod`, constructor lookup, method invocation, field access, class loading, and reflective array creation.
- resources: `Class#getResource`, `Class#getResourceAsStream`, `ClassLoader#getResource`, `ClassLoader#getResourceAsStream`, and `ClassLoader#getResources`.
- serialization: `ObjectInputStream#readObject`, `ObjectInputStream#resolveClass`, and `ObjectOutputStream#writeObject`.
- proxy: `Proxy#getProxyClass` and `Proxy#newProxyInstance`.

#### Subtask 1.3: State the Experiment Constraint

Make the prompt explicit that the agent must infer candidate paths from source context and the runtime API table. It must not ask for, depend on, or simulate the dynamic-access report. It should still use only public library APIs in tests and should not directly call reflection, serialization, proxy, or resource APIs from the test just to satisfy the table.

### Task 2: Create Specialized Basic Iterative Prompt Templates
**State:** `completed`
**Prior:** Task 1

Create strategy-specific prompt templates for `basic_iterative` that incorporate source context and runtime API search guidance.

#### Subtask 2.1: Initial Prompt

Create an initial prompt, for example `forge/prompt_templates/initial/basic_source_runtime_api_initial.md`. It should include `{library}`, `{test_language_display_name}`, `{test_source_dir_name}`, and `{source_context_overview}` placeholders. It should instruct the agent to inspect read-only source files for runtime API usages from the table, choose reachable public API flows, and write tests that exercise those flows under native image.

#### Subtask 2.2: Successful-Iteration Prompt

Create or reuse an after-successful-iteration prompt. If creating a new one, it should ask the agent to inspect a different runtime API path or source class than prior tests, avoiding duplicate coverage.

#### Subtask 2.3: Failed-Iteration Behavior

Decide whether `basic_after_fail.md` is sufficient. If not, add a specialized failed-iteration prompt that reminds the agent to fix compilation/runtime failures without abandoning the source-guided runtime API objective.

### Task 3: Add New Predefined Strategies
**State:** `completed`
**Prior:** Task 2

Add new entries to `forge/strategies/predefined_strategies.json` that use the existing `basic_iterative` workflow and request main source context.

#### Subtask 3.1: Add Pi Strategy

Add a Pi GPT-5.5 strategy, for example:

```json
{
  "name": "basic_iterative_source_runtime_api_pi_gpt-5.5",
  "description": "Basic iterative Pi workflow using downloaded main source context and a runtime API recognition table instead of a dynamic-access report.",
  "workflow": "basic_iterative",
  "agent": "pi",
  "model": "oca/gpt-5.5",
  "prompts": {
    "initial": "prompt_templates/initial/basic_source_runtime_api_initial.md",
    "after-successful-iteration": "prompt_templates/after-successful-iteration/basic_source_runtime_api_after_success.md",
    "after-failed-iteration": "prompt_templates/after-failed-iteration/basic_after_fail.md"
  },
  "parameters": {
    "max-test-iterations": 5,
    "max-failed-generations": 2,
    "max-successful-generations": 3,
    "source-context-types": ["main"]
  }
}
```

#### Subtask 3.2: Decide Codex Coverage

Codex coverage is deferred. The initial implementation adds only the Pi GPT-5.5 strategy so the experiment has one clean comparison against existing Pi GPT-5.5 basic and dynamic-access benchmarks.

#### Subtask 3.3: Validate Strategy Loading

Use the existing strategy loader path to verify required prompts and parameters resolve. The strategy must instantiate as `BasicIterativeStrategy`, not a dynamic-access strategy.

### Task 4: Add Benchmark Suite Entries
**State:** `completed`
**Prior:** Task 3

Add benchmark configurations to `forge/benchmarks/benchmark_suite.json` for the new strategy.

#### Subtask 4.1: Genesis Benchmark

Add a Genesis benchmark for `ch.qos.logback:logback-core:1.1.3`, for example `genesis_basic_source_runtime_api_pi_gpt-5.5`, pointing to `basic_iterative_source_runtime_api_pi_gpt-5.5`.

#### Subtask 4.2: Nexus Benchmark

Add a Nexus benchmark for `com.h2database:h2:2.1.210`, for example `nexus_basic_source_runtime_api_pi_gpt-5.5`, pointing to `basic_iterative_source_runtime_api_pi_gpt-5.5`.

#### Subtask 4.3: Preserve Naming Contrast

Keep names distinct from `*_da_*`, `*_optimistic_*`, and `*_bulk_*` benchmarks. These are basic iterative runs with source context and a runtime API table, not dynamic-access-report runs.

### Task 5: Verify Source Context Behavior
**State:** `completed`
**Prior:** Task 3

Confirm the new strategy receives source context through existing `source-context-types` handling in `add_new_library_support.py`.

#### Subtask 5.1: Prompt Substitution

Verify `{source_context_overview}` renders in the initial prompt and does not fail when source artifacts are unavailable.

#### Subtask 5.2: Read-Only Files

Confirm downloaded main sources are passed as `read_only_files` to the agent while generated tests and `build.gradle` remain the only editable files.

#### Subtask 5.3: No Dynamic-Access Report Input

Confirm the prompt and strategy do not call `generateDynamicAccessCoverageReport`, do not load `dynamic-access-coverage.json`, and do not include class-specific uncovered call sites.

### Task 6: Validate Benchmark Configuration
**State:** `completed`
**Prior:** Task 4, Task 5

Run lightweight validation before executing expensive benchmarks.

#### Subtask 6.1: JSON Validation

Validate `forge/strategies/predefined_strategies.json` and `forge/benchmarks/benchmark_suite.json` with `jq empty`.

#### Subtask 6.2: Benchmark Lookup

Use `benchmark_runner.py` lookup behavior or a direct JSON query to confirm the new Genesis and Nexus benchmark names resolve to the intended strategy.

#### Subtask 6.3: Dry Instantiation

Instantiate the new strategy path far enough to catch missing prompt keys, missing template placeholders, and unsupported `source-context-types` values without running a full benchmark.

### Task 7: Run and Compare Benchmarks
**State:** `pending`
**Prior:** Task 6

Run the new benchmark configurations and compare their final stats against existing basic and dynamic-access baselines.

#### Subtask 7.1: Run Genesis

Run `benchmark_runner.py --benchmark-name genesis_basic_source_runtime_api_pi_gpt-5.5` with the normal metrics repo path. Preserve benchmark-mode metrics under `benchmark_run_metrics/add_new_library_support.json`.

#### Subtask 7.2: Run Nexus

Run `benchmark_runner.py --benchmark-name nexus_basic_source_runtime_api_pi_gpt-5.5` with the normal metrics repo path. Preserve benchmark-mode metrics under `benchmark_run_metrics/add_new_library_support.json`.

#### Subtask 7.3: Compare Outcomes

Compare `stats.dynamicAccess`, generated LOC, iterations, cost, and final status against:

- plain basic iterative GPT-5.5.
- dynamic-access main-sources GPT-5.5.
- standalone/bulk dynamic-access GPT-5.5 where available.

### Task 8: Document Experiment Interpretation
**State:** `pending`
**Prior:** Task 7

Summarize what the benchmark does and does not prove.

#### Subtask 8.1: State the Controlled Difference

Document that the new strategy has source context plus a generic runtime API table, while dynamic-access strategies have source context plus exact class/call-site report data.

#### Subtask 8.2: State Expected Readout

Use final `stats.dynamicAccess` as the primary signal for how well the agent found runtime-access paths without the report. Use library line coverage and generated LOC as secondary signals.

#### Subtask 8.3: Capture Limitations

Note that the runtime API table identifies API shapes only. It does not tell the agent which library classes contain those APIs, which call sites are currently uncovered, or which public API sequences reach them.
