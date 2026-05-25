# AR-code-coverage-improvement: Code coverage improvement workflow architecture

Code coverage improvement (§WF-code-coverage-improvement) should be implemented
as its own workflow component because its intent, inputs, metrics, and review
evidence differ from dynamic-access coverage. The workflow reuses Forge's
normal driver, strategy, agent, verification, metrics, and publication
boundaries (§AR-forge-workflow-system), but it owns the PGO profile analysis
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
