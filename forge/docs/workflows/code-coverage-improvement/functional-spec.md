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
(§AR-code-coverage-improvement).
