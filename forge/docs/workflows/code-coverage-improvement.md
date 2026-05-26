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

The tests produced by this workflow must be a separate test suite from the
tests used to generate or validate reachability metadata. Code coverage tests
exist to exercise the whole practical library API; metadata-generation tests
exist to exercise dynamic-access behavior and validate native-image metadata.
The suites may target the same coordinate, but they must have separate
locations, metrics, and publication evidence so improving broad API coverage
does not change the meaning of metadata-generation coverage.

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
- **PGO profile observation** — profile-derived evidence of what library code
  the current repo tests execute.

Coverage targets should describe behavior to exercise, not raw bytecode
addresses or dynamic-access call sites. Examples include untested public
builders, serializers, parsers, adapters, configuration branches, error
handling paths, and common object lifecycle operations.

The workflow should aim to cover the whole practical library API over repeated
runs. If the API is too large for one PR, the workflow may use chunks, but each
chunk must persist enough target/exhaust state for a later run to continue
without redoing already completed, skipped, or semantically impossible targets
(§GOAL-maximize-library-coverage).

## 4. Workflow

1. **Eligibility and baseline** — resolve the supported coordinate, confirm
   that the repo already contains a test suite, and run baseline verification.
2. **Baseline profile** — run the existing tests and collect a GraalVM PGO
   runtime profile for the target coordinate.
3. **Target discovery** — combine API inventory with profile observation to
   produce a promptable list of weakly covered or uncovered behavior targets.
4. **Target selection** — choose a bounded set of targets for this run or
   chunk, preserving durable skipped/exhausted state for targets that cannot be
   handled automatically.
5. **Test generation** — ask the configured agent to add or improve tests that
   cover selected behavior through public or realistically reachable APIs.
6. **Profile comparison** — rerun profile collection and compare the updated
   profile against the baseline and selected targets.
7. **Verification** — run local CI-equivalent verification for the coordinate
   before returning a PR-eligible status (§FS-local-ci-equivalent-verification).

## 5. Acceptance Criteria

A code coverage improvement run is successful only when all of these hold:

- The generated tests are meaningful behavior tests and do not only execute
  methods for superficial profile hits.
- The generated tests are written to the separate code coverage suite, not to
  the metadata-generation test suite.
- The profile comparison shows a measurable increase in executed library API or
  runtime surface for the selected targets, or the run records why a target was
  skipped or exhausted.
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

The workflow is planned and not yet implemented. Until a driver, workflow
engine, profile collector, API inventory builder, target-state format, metrics
schema, and publication path exist, references to this workflow describe
intended Forge functionality rather than runnable automation
(§WF-code-coverage-improvement-architecture).

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

- **Workflow driver** — resolves the coordinate, existing metadata-generation
  test suite, separate code coverage test suite, strategy, metrics root, source
  context, and optional chunk state before constructing the workflow engine.
- **PGO profile collector** — runs the configured test command with GraalVM PGO
  profile collection enabled and stores the raw profile artifact plus a stable
  normalized summary.
- **API inventory builder** — derives promptable API and behavior groups from
  the library artifact, sources, documentation, and upstream tests when
  available.
- **Coverage analyzer** — joins the API inventory with baseline and updated
  profile summaries to identify weakly covered or uncovered behavior.
- **Target-state store** — persists selected, completed, skipped, exhausted,
  and failed API coverage targets for chunked or resumed runs.
- **Workflow engine** — owns prompt/command cycles, retries, target selection,
  profile comparison, verification interpretation, and terminal status.
- **Publication handoff** — publishes only PR-eligible runs after local
  CI-equivalent verification passes (§AR-forge-verification-publication-boundary).

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

This architecture is planned. The component requires a new driver or
driver mode, a registered workflow engine, PGO collector utilities, an API
inventory builder, a coverage analyzer, a target-state schema, metrics support,
and publication wiring before it is executable.
