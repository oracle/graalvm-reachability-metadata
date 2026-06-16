# WF-code-coverage-improvement: Code coverage improvement workflow

Code coverage improvement is a planned Forge workflow
(§WF-forge-workflow-system, §FS-forge-code-coverage-improvement) for increasing
how much of an already-supported library's API and runtime behavior is
exercised by generated tests. It is separate from dynamic-access coverage:
dynamic-access workflows target calls that require reachability metadata,
while this workflow targets the broader library API surface even when the
executed code has no dynamic-access signal.

## 1. Purpose

The workflow exists because dynamic-access coverage can rise while ordinary
library code coverage remains weak. A test suite can exercise enough
reflection, resource, proxy, JNI, or serialization paths to improve metadata
confidence without exercising the wider public API behavior that maintainers
expect from a useful support test.

The intent is to add or improve tests for libraries that are already present in
the reachability repo. The generated tests should drive realistic public API
usage across the library, not only the calls that appear in a dynamic-access
report. GraalVM PGO runtime profiles provide the execution signal: Forge
collects a baseline profile for the current tests, identifies API areas and
runtime code that remain unexecuted or weakly exercised, generates tests for
those gaps, and compares the updated profile with the baseline.

## 2. Scope

The workflow targets supported libraries with an existing test suite in the
repo. It may also run after a new-library or version-update workflow has
produced a passing test suite, but its own work product is broader API coverage
for a library that is already represented locally.

The initial automation entry point should be a Rhei workspace template for one
GitHub issue labeled `improve-code-coverage`. The issue body must identify one
Maven coordinate in `group:artifact:version` form. Template conversion resolves
that coordinate, verifies that the library is already represented locally,
creates or reuses a per-issue worktree, and generates bounded Rhei tasks for
preparation, inventory, coverage generation, validation, discovery, finalization,
and publication.

The tests produced by this workflow must be a separate test suite from the
tests used to generate or validate reachability metadata. Code coverage tests
exist to exercise the whole practical library API; metadata-generation tests
exist to exercise dynamic-access behavior and validate native-image metadata.
The suites may target the same coordinate, but they must have separate
locations, metrics, and publication evidence so improving broad API coverage
does not change the meaning of metadata-generation coverage.

Generated code coverage tests must be written under
`tests/<group>/<artifact>/<version>/code-coverage`. The workflow may read the
metadata-generation tests as context, but generated code coverage tests must not
be placed in the metadata-generation suite.

This workflow must not replace dynamic-access generation. It complements it:

- Dynamic-access workflows answer whether tests cover metadata-relevant calls.
- Code coverage improvement answers whether tests exercise the wider library
  API and runtime implementation.
- A PR may contain both improvements when a strategy deliberately chains the
  workflows, but each workflow must report its own metrics.

## 3. Coverage Model

The workflow should build a target model from two inputs:

- **API inventory** — public or realistically reachable library packages,
  classes, methods, constructors, and behavior groups derived from library
  artifacts, sources, documentation, or upstream tests.
- **JVM coverage observation** — JaCoCo method and line coverage for the code
  coverage suite, correlated with the API inventory to identify public API
  targets still uncovered after an iteration.
- **Native Image discovery observation** — instrumented GraalVM PGO profile
  data correlated with the Native Image static call graph and reachable-method
  denominator to find reachable library behavior that the tests did not execute.

Coverage targets should describe behavior to exercise, not raw bytecode
addresses or dynamic-access call sites. Examples include untested public
builders, serializers, parsers, adapters, configuration branches, error
handling paths, and common object lifecycle operations.

The API inventory should be emitted as compact JSON and Markdown under
`runtime/code-coverage/api-inventory/`. Its canonical target `id` carries the
full identity; redundant split fields should be avoided unless a helper needs
them for stable processing.

The Native Image discovery phase must treat `.iprof` files as positive evidence
only. Sound coverage interpretation requires instrumented profiles, not sampled
profiles; a reachable-method denominator from the image analysis/call-tree
reports; and bytecode-index-to-source mapping from preserved debug information.
Executed methods and edges are derived from profile context chains, not from the
profile's method symbol table. Uncovered discovery targets are computed by
joining the static call graph with observed profile edges and walking backward
to the nearest public API inventory target. PGO-derived coverage should also be
emitted as LCOV so standard coverage tools can consume it.

The workflow should aim to cover the whole practical library API over repeated
runs. If the API is too large for one PR, the workflow may use chunks, but each
chunk must persist enough target/exhaust state for a later run to continue
without redoing already completed, skipped, or semantically impossible targets
(§GOAL-maximize-library-coverage).

## 4. Workflow

The Rhei template should decompose the workflow into these phases:

1. **Convert issue** — fetch one `improve-code-coverage` issue, parse the
   coordinate, create or reuse the worktree, and record conversion rationale.
2. **Prepare library** — resolve the coordinate, confirm existing repository
   support, create or verify the code coverage suite, prepare source context,
   and record baseline facts.
3. **Generate API inventory** — deterministically write compact JSON and
   Markdown reports for public user-callable API targets.
4. **API cover loop** — run bounded agent work that adds or refines tests for
   API inventory targets through normal public API usage.
5. **Test validate** — JVM-only: run Java compilation and JVM tests under
   JaCoCo, correlate JaCoCo coverage against the API inventory, and emit the
   per-iteration JaCoCo and API-cover reports. Reachability metadata and Native
   Image are intentionally out of scope here, since JaCoCo coverage needs only
   the JVM.
6. **Prepare native metadata** — run once after the API-cover loop and before
   PGO discovery: generate reachability metadata and repair it with the Codex
   `fix-missing-reachability-metadata` skill until a Native Image test passes, so
   the instrumented PGO builds succeed without rediscovering metadata on every
   iteration. Route unresolved metadata or Native Image failures to human
   intervention.
7. **Generate PGO correlation report** — collect an instrumented Native Image
   PGO profile, collect the static call graph and reachable set, reject sampled
   profiles, emit LCOV, and write prompt-ready discovery reports with reaching
   paths from public API entries to reachable-but-unobserved targets.
8. **Discovery cover loop** — group reachable-but-unobserved targets by shared
   public entry point, reaching path, or owning class, and cover batches rather
   than one target per iteration.
9. **Finalization** — verify the final result against latest GraalVM, metadata
   validity, JVM tests, Native Image tests, and the final PGO correlation report.
10. **Publication** — open a PR with source issue, coordinate, coverage suite,
    baseline/final coverage, coverage delta, completed targets,
    skipped/exhausted targets, and validation commands.

Agent-reviewed Rhei tasks should use a lightweight review/fix loop. Review
checks whether the task followed its generated body, wrote required artifacts,
stayed inside the intended worktree and coverage-test location, targeted public
user-callable API behavior, and included validation evidence. Verification work
routes to human intervention when metadata generation or Native Image validation
cannot be resolved automatically.

## 5. Acceptance Criteria

A code coverage improvement run is successful only when all of these hold:

- A Rhei template can convert one `improve-code-coverage` issue into an
  executable workspace with bounded tasks and review routing.
- The generated tests are meaningful behavior tests and do not only execute
  methods for superficial profile hits.
- The generated tests are written to the separate code coverage suite, not to
  the metadata-generation test suite.
- API inventory generation produces compact JSON and Markdown reports for public
  user-callable targets.
- JVM validation runs under JaCoCo and reports which inventory targets remain
  uncovered after each API-cover iteration.
- Instrumented PGO coverage is normalized against the call-graph denominator,
  sampled profiles are rejected, executed edges come from profile contexts, and
  not-observed-but-reachable targets carry backward-derived reaching paths to
  public API entries.
- PGO-derived coverage is emitted as LCOV.
- Discovery work covers reachable-but-unobserved targets in batches and records
  skipped or exhausted targets with reasons.
- Existing dynamic-access coverage, metadata validity, and JVM/native test
  gates do not regress.
- Metrics record the baseline profile, updated profile, selected targets,
  profile comparison summary, generated test paths, strategy, model, skipped or
  exhausted targets, and verification commands.
- Local CI-equivalent verification passes before PR publication
  (§FS-local-ci-equivalent-verification).

## 6. Boundaries

PGO profile data is a workflow signal, not a replacement for maintainer review.
The workflow should not claim that profile growth proves semantic completeness.
It should expose the API targets, profile comparison, generated test rationale,
and skipped/exhausted targets in metrics and the PR description so reviewers
can judge whether the new tests exercise valuable library behavior.

The workflow is partially implemented as a Rhei workspace template backed by
deterministic Forge helper scripts (API inventory, JVM JaCoCo validation,
instrumented-PGO discovery correlation, and PR publication) and the
`nativeTestPGOInstrument` / `runNativeTestPGO` Gradle harness tasks. Until a
Forge driver or driver mode, a target-state store, and a metrics schema wire
those helpers into an autonomous lane, references to the end-to-end automation
beyond the template and helpers describe intended Forge functionality rather
than a fully runnable lane (§WF-code-coverage-improvement-architecture).

# WF-code-coverage-improvement-architecture: Code coverage improvement workflow architecture

Code coverage improvement (§WF-code-coverage-improvement) should be implemented
as its own workflow component because its intent, inputs, metrics, and review
evidence differ from dynamic-access coverage. The workflow reuses Forge's
normal driver, strategy, agent, verification, metrics, and publication
boundaries (§WF-forge-workflow-architecture), but it owns the PGO profile analysis
and API-target state needed to broaden tests for already-supported libraries.

## 1. Component Boundaries

The component should be split into deterministic utilities plus a workflow
engine:

- **Rhei template and converter** — consumes one `improve-code-coverage` issue,
  resolves the coordinate and worktree, and renders the executable task plan.
- **Workflow driver** — resolves the coordinate, existing metadata-generation
  test suite, separate code coverage test suite, strategy, metrics root, source
  context, and optional chunk state before constructing the workflow engine.
- **API inventory builder** — derives promptable API and behavior groups from
  the library artifact, sources, documentation, and upstream tests when
  available. Implemented deterministically from the library jar via `javap` in
  `forge/utility_scripts/code_coverage_api_inventory.py`.
- **JVM coverage validator** — runs Java compilation and JVM tests under JaCoCo,
  joins JaCoCo evidence with the API inventory, and writes remaining-target
  reports for the API-cover loop. Implemented in
  `forge/utility_scripts/code_coverage_validate.py`, driving the existing
  `compileTestJava`/`javaTest`/`jacocoTestReport` harness tasks. It is JVM-only;
  reachability metadata and Native Image are handled by the native metadata
  preparer below.
- **Native metadata preparer** — runs once after the API-cover loop and before
  PGO discovery: generates reachability metadata and repairs it with the Codex
  `fix-missing-reachability-metadata` skill until a Native Image test passes, so
  the instrumented PGO builds succeed. Implemented in
  `forge/utility_scripts/code_coverage_prepare_native_metadata.py`.
- **Native Image PGO analyzer** — collects instrumented `.iprof` data, rejects
  sampled profiles, joins profile contexts with the static call graph and
  reachable-method set, maps BCI evidence to source, and emits LCOV plus
  prompt-ready discovery reports. Implemented in
  `forge/utility_scripts/code_coverage_profile_report.py`; the instrumented
  image and analysis call-tree dump are produced by the `nativeTestPGOInstrument`
  and `runNativeTestPGO` harness tasks (`--pgo-instrument -g
  -H:+PrintAnalysisCallTree`). Executed methods and observed edges are derived
  from the profile `<`-chain contexts, which are leaf-first
  (`callee:bci<caller:bci`), so observed edges read right-to-left.
- **Coverage analyzer** — joins the API inventory with JVM and Native Image
  coverage summaries to identify weakly covered or uncovered behavior. Shared
  identity normalization lives in `forge/utility_scripts/code_coverage_model.py`.
- **Target-state store** — persists selected, completed, skipped, exhausted,
  and failed API coverage targets for chunked or resumed runs.
- **Workflow engine** — owns prompt/command cycles, retries, target selection,
  profile comparison, verification interpretation, and terminal status.
- **Publication handoff** — publishes only PR-eligible runs after local
  CI-equivalent verification passes (§AR-forge-verification-publication-boundary).
  Implemented in `forge/git_scripts/make_pr_code_coverage_improvement.py`, which
  renders the PR body from the phase-8 finalization metrics.

## 2. Workflow State

The workflow state should be target-based, not call-site-based. Dynamic-access
workflows iterate over uncovered dynamic-access classes and calls; code
coverage improvement iterates over API and behavior targets selected from the
inventory/profile join (§WF-dynamic-access-workflow).

The code coverage test suite should be physically and logically separate from
the test suite used to generate reachability metadata. The workflow may inspect
metadata-generation tests as context, but generated coverage tests must be
written to the coverage-suite root and measured with code-coverage metrics.
Metadata-generation tests remain the source of dynamic-access and native-image
metadata evidence.

Target state should include:

- target identifier and human-readable behavior description
- source of the target, such as artifact inspection, source context,
  documentation, upstream tests, or profile gap
- baseline profile evidence and updated profile evidence
- selected generated test files
- terminal target status: completed, skipped, exhausted, failed, or pending
- semantic reason when the target cannot be covered automatically

The persisted state should be coordinate-scoped and stable enough for
orchestration to resume later runs from the coordinate alone, following the same
operational shape as chunked dynamic-access exhaust state without sharing the
dynamic-access report schema (§WF-dynamic-access-exhaust-report).

## 3. PGO Profile Handling

Raw PGO artifacts should be treated as machine evidence. Prompts should receive
normalized summaries that are stable, concise, and mapped back to library API
targets. The collector/analyzer boundary should hide GraalVM profile file
details from the agent unless a targeted debugging prompt needs them.

Profile comparison should answer two questions:

- Did generated tests execute the selected API or behavior targets more than
  the baseline?
- Did the run preserve existing dynamic-access, metadata, JVM, and native-image
  verification quality?

Profile growth alone is not sufficient for success. The workflow engine should
pair profile evidence with a generated-test rationale and deterministic
verification results before reporting a PR-eligible status.

## 4. Prompting and Review Evidence

The agent prompt should be framed around library behavior, public API usage,
and existing repo test conventions. It should avoid instructions that encourage
direct invocation of implementation details only to increase profile counts.

Metrics and PR publication should expose:

- baseline and updated profile summary paths
- selected API coverage targets and their terminal statuses
- generated or modified test paths
- profile delta summary for the selected targets
- verification commands and results
- skipped or exhausted target reasons

Review automation and maintainers should be able to distinguish this workflow's
evidence from dynamic-access coverage evidence. A PR that improves code
coverage should not be presented as a dynamic-access coverage fix unless it
also ran and reported the dynamic-access workflow (§WF-improve-library-coverage).

## 5. Implementation Status

This architecture is partially implemented. The component has a Rhei template,
a validated example workspace, the deterministic helper utilities (API inventory
builder, JVM JaCoCo coverage validator, instrumented-PGO and call-graph
correlation analyzer, shared identity model, and PR publication helper), and the
`nativeTestPGOInstrument` / `runNativeTestPGO` Gradle harness tasks that produce
the instrumented `.iprof` and analysis call-tree denominator. It still requires
a new Forge driver or driver mode, a target-state schema for chunked/resumed
runs, and metrics support before it is a fully autonomous Forge lane; today the
helpers are invoked through the Rhei task plan.
