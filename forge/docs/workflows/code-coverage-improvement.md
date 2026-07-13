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
report. JaCoCo is the sole coverage metric. Sampled GraalVM PGO profiles and the
Native Image static call graph provide a later, separate navigation signal that
shows an agent how current execution diverges from JaCoCo-uncovered internal
library methods; sampling never changes a coverage result.

## 2. Scope

The workflow targets supported libraries with an existing test suite in the
repo. It may also run after a new-library or version-update workflow has
produced a passing test suite, but its own work product is broader API coverage
for a library that is already represented locally.

The initial automation entry point should be a Rhei workspace template for one
GitHub issue labeled `code-coverage-improvement`. The issue body must identify one
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


The suite root must contain `src/test/java` and may contain
`src/test/resources`. Workflow commands pass its absolute path as
`codeCoverageSuitePath`; the coordinate Gradle project adds it only for those
opt-in commands. Ordinary metadata-generation and validation commands that omit
the property continue to use only their existing test source tree.
This workflow must not replace dynamic-access generation. It complements it:

- Dynamic-access workflows answer whether tests cover metadata-relevant calls.
- Code coverage improvement answers whether tests exercise the wider library
  API and runtime implementation.
- A PR may contain both improvements when a strategy deliberately chains the
  workflows, but each workflow must report its own metrics.

## 3. Coverage Model

The workflow has two ordered phases with separate targets, reports, and prompts.

JaCoCo is authoritative in both phases; sampled PGO never changes whether a
method is covered.

### 3.1 Public API entry coverage

The first phase covers the public user-callable API surface derived from the
library artifacts. It joins exact canonical method identities from the API
inventory with JVM JaCoCo method coverage. Overloads must remain distinct, and
an absent or ambiguous correlation must not inherit another method's status.

Each API-cover prompt contains only public API methods that the latest JaCoCo
report marks uncovered. The agent must work across the whole supplied batch
through realistic public API usage and assertions, without superficial
coverage-only invocation. The phase runs for at most five iterations and stops
early when no public target remains uncovered.

### 3.2 Deep implementation coverage

The second phase starts only after public API coverage and native metadata
preparation. Its target universe is library-owned methods reported by JaCoCo
that are not public API inventory entries. JaCoCo remains the sole coverage
metric for these internal methods.

The Native Image analysis call-tree CSV dump and sampled PGO profile provide
navigation for JaCoCo-uncovered internal targets. For each target, the analyzer
uses the shortest directed static path from any sampled frame. When no sampled
frame joins, it may use the shortest path from a public API inventory entry.
Distance is the primary ranking key; frame quality and sample count may only
break equal-distance ties.

A target absent from the static graph remains JaCoCo-uncovered but is recorded
as not present in the current graph. A target present in the graph without a
sampled or public-API route remains in the full JSON report as a no-route
candidate. Neither condition changes its JaCoCo status. Only actionable
sampled-path and public-entry-path targets enter the agent prompt.

The prompt navigation stays compact and groups paths that share a divergence:

```text
Observed:
Parser.parse(...) → parseJson(...)

Uncovered paths:
Parser.parse(...) → parseCSV(...)
Parser.parse(...) → parseXML(...)
```

`Observed` is sampled guidance only. Every `Uncovered paths` target is
uncovered according to exact JaCoCo evidence. The agent must reach internal
methods through the shown public behavior rather than invoke implementation
methods directly.

The full JSON report retains every uncovered internal target, its JaCoCo
evidence, graph status, rank, sampled context, and static path. The prompt-facing
Markdown and target-id list contain at most 100 methods globally. Attempt,
completion, skip, and exhaustion state must let later iterations advance beyond
the first 100 instead of repeatedly selecting the same unsuccessful targets.
The deep phase runs for at most five iterations and stops early when no
actionable target remains.

Sampled observations may be emitted as LCOV guidance for standard tooling. That
artifact contains positive sample counts only, is labeled guidance-only, and is
never used as a coverage result.

Coverage targets should describe behavior to exercise, not raw bytecode
addresses or dynamic-access call sites. Examples include untested public
builders, serializers, parsers, adapters, configuration branches, error
handling paths, and common object lifecycle operations. The API inventory is
emitted as compact JSON and Markdown under
`runtime/code-coverage/api-inventory/`; its canonical target `id` carries the
full method identity.

The workflow should aim to cover the whole practical library API and internal
runtime surface over repeated runs. If the target set is too large for one PR,
the workflow may use chunks, but each chunk must persist enough target/exhaust
state for a later run to continue without redoing already completed, skipped,
or semantically impossible targets (§GOAL-maximize-library-coverage).

## 4. Workflow

The Rhei template should decompose the workflow into these phases:

1. **Convert issue** — fetch one `code-coverage-improvement` issue, parse the
   coordinate, create or reuse the worktree, and record conversion rationale.
2. **Prepare library** — resolve the coordinate, confirm existing repository
   support, create or verify the code coverage suite, prepare source context,
   and record baseline facts.
3. **Generate API inventory** — deterministically write compact JSON and
   Markdown reports for public user-callable API targets.
4. **API coverage loop** — one task cycling deterministic measurement and an
   agent cover pass. Measurement runs JVM JaCoCo
   plus exact API-inventory correlation, persists `api-cover-report-<n>` history
   and one fixed-location report, and decides the loop: the phase completes when
   no uncovered public target remains or at most five cover passes are spent.
   When the loop continues, measurement also derives the prompt of exact
   JaCoCo-uncovered public methods from that report. The cover agent attempts the complete supplied batch through
   normal public API behavior and always returns to measurement. Reachability
   metadata and Native Image are intentionally out of scope in this phase.
5. **Prepare native metadata** — run once after the API loop and before
   deep discovery: generate reachability metadata and repair it with the Codex
   `fix-missing-reachability-metadata` skill until a Native Image test passes.
   Route unresolved metadata or Native Image failures to human intervention.
6. **Deep coverage loop** — the same measure/cover cycle for internal
   methods. Measurement runs JaCoCo over the library-owned method set, builds
   and runs native tests with PGO sampling, loads one coherent analysis
   call-tree CSV triplet, excludes public API inventory entries, ranks exact
   JaCoCo-uncovered internal methods by shortest sampled/static path, retains
   every record in JSON plus sampled-guidance LCOV, persists
   `discovery-report-<n>` history and one fixed-location report, and decides
   the loop with the same five-pass budget, and derives the compact
   at-most-100-method prompt when it continues. The cover agent batches related paths, reaches
   internal methods through public behavior, records completed, skipped, and
   exhausted target state, and always returns to measurement.
7. **Finalization** — a deterministic step program: read the machine-readable
   conversion record, run the JVM tests with the dedicated coverage suite, and
   persist final metrics from the baseline and highest-iteration JaCoCo and
   deep reports. No Native Image validation runs at this stage; a nonzero exit
   code names the failed step.
8. **Publication** — open a PR with source issue, coordinate, coverage suite,
   baseline/final JaCoCo coverage, completed paths, skipped/exhausted targets,
   sampled guidance evidence, and validation commands.

The pipeline tasks run unreviewed: deterministic helpers, schema-validated
artifacts, and zero-exit validation gates decide their completion. The
finalization task executes as a deterministic program of numbered steps whose
nonzero exit code names the failed step, and its completion is decided by a
deterministic verification program, not by an agent's own claim: it checks the
finalization artifacts exist, schema-validates the final metrics, and inspects
their outcomes. Fixable step or verification failures return to a bounded fix
state, after which the steps re-run; failed targets or an explicit
human-intervention flag in the metrics, and failures that survive the fix
budget, route to human intervention.

## 5. Acceptance Criteria

A code coverage improvement run is successful only when all of these hold:

- A Rhei template can convert one `code-coverage-improvement` issue into an
  executable workspace with deterministic finalization verification and bounded
  fix routing.
- The generated tests are meaningful behavior tests and do not invoke internal
  methods directly merely to raise coverage.
- Public API and deep implementation work use separate reports and prompts.
- API inventory generation produces compact JSON and Markdown for public
  user-callable methods and constructors.
- API iteration zero establishes a JaCoCo baseline; each of at most five public
  API agent iterations is followed by exact JaCoCo correlation.
- API prompts contain only exact JaCoCo-uncovered public targets and ask the
  agent to attempt the complete supplied batch.
- Deep targets are library-owned JaCoCo methods minus public API inventory
  entries. Exact JaCoCo evidence alone determines their status.
- Sampled PGO and the static call graph change only deep-path guidance and
  ranking; they never change covered, uncovered, or unknown status.
- Near-call distance is the shortest directed static path from a sampled frame;
  prompt-quality and sample-count preferences only break equal-distance ties.
- Full JSON retains every deep target and path record. Prompt Markdown contains
  at most 100 actionable methods and uses compact `Observed` /
  `Uncovered paths` navigation.
- A fifth deep-cover iteration is followed by a fifth report before
  finalization.
- Sampled observations emitted as LCOV contain positive sample evidence only
  and are labeled guidance-only.
- Completion, skip, and exhaustion state prevents a hard target batch from
  starving later methods.
- Existing dynamic-access coverage, metadata validity, JVM/native tests, and
  local CI-equivalent verification do not regress.
- Metrics and PR evidence keep JaCoCo coverage results separate from PGO
  sampling guidance and include the validation commands.

The checked-in Rhei example must pass:

```bash
rhei validate examples/code-coverage-improvement-example
rhei run examples/code-coverage-improvement-example --dry-run --parallel 2
```

## 6. Boundaries

PGO profile data is navigation, not a replacement for JaCoCo or maintainer
review. The workflow must not claim that profile growth or sample absence proves
coverage or semantic completeness. Metrics and the PR description expose
JaCoCo results, sampled paths, generated-test rationale, and skipped/exhausted
targets as separate evidence so reviewers can judge whether the tests exercise
valuable library behavior.

The workflow has a runnable Rhei lane backed by deterministic Forge helpers for
API inventory, exact JVM JaCoCo validation, native metadata preparation,
sampled-PGO/static-path correlation, durable target state, final metrics, and
PR publication. The `nativeTestPGOSampling` / `runNativeTestPGO` Gradle tasks
provide the sampled profile and coherent call-tree inputs. A Forge driver or
driver mode is still required before the control plane can autonomously claim
issues and launch this lane; that missing integration does not make the Rhei
workspace or helper chain non-executable
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

- **Rhei template and converter** — consumes one `code-coverage-improvement` issue,
  resolves the coordinate and worktree, and renders the executable task plan.
- **Workflow driver** — resolves the coordinate, existing metadata-generation
  test suite, separate code coverage test suite, strategy, metrics root, source
  context, and optional chunk state before constructing the workflow engine.
- **API inventory builder** — derives promptable API and behavior groups from
  the library artifact, sources, documentation, and upstream tests when
  available. Implemented deterministically from the library jar via `javap` in
  `forge/utility_scripts/code_coverage_api_inventory.py`.
- **JVM coverage validator** — runs Java compilation and JVM tests under JaCoCo,
  joins exact JaCoCo identities with the API inventory, and writes the public
  API baseline and post-iteration reports. Implemented in
  `forge/utility_scripts/code_coverage_validate.py`, driving the existing
  `compileTestJava`/`javaTest`/`jacocoTestReport` harness tasks.
- **JaCoCo evidence parser** — normalizes every method in the tested library's
  JaCoCo XML into an exact identity, covered/uncovered status, and source
  evidence shared by the public API validator and deep analyzer. Implemented in
  `forge/utility_scripts/code_coverage_jacoco.py`. It never uses arity-only
  identities to decide coverage.
- **Native metadata preparer** — runs once after the API-cover loop and before
  PGO discovery: generates reachability metadata and repairs it with the Codex
  `fix-missing-reachability-metadata` skill until a Native Image test passes, so
  the PGO-sampling builds succeed. Implemented in
  `forge/utility_scripts/code_coverage_prepare_native_metadata.py`.
- **Native Image deep-path analyzer** — intersects exact JaCoCo library methods
  with the analysis call-tree CSV graph, subtracts public API inventory entries,
  and uses sampled `.iprof` stacks only to navigate JaCoCo-uncovered internal
  methods. It retains every record in JSON and emits compact `Observed` /
  `Uncovered paths` Markdown capped at 100 methods. Implemented in
  `forge/utility_scripts/code_coverage_profile_report.py`; the sampling image
  and call-tree CSVs are produced by the `nativeTestPGOSampling` and
  `runNativeTestPGO` harness tasks (`--pgo-sampling
  -H:PGOSamplingPeriodMicros=<micros> -H:+PrintAnalysisCallTree
  -H:PrintAnalysisCallTreeType=CSV`; the run dumps the profile through
  `-XX:ProfilesDumpFile`). Profile `<`-chain contexts are leaf-first
  (`callee:bci<caller:bci`), so sampled stacks read right-to-left from the root.
- **Identity model** — normalizes API inventory, JaCoCo, call-tree CSV, and
  sampled-profile method identities in
  `forge/utility_scripts/code_coverage_model.py`.
- **Target-state store** — persists selected, attempted, completed, skipped,
  exhausted, and failed public/deep targets for chunked or resumed runs.
- **Workflow engine** — owns prompt/command cycles, retries, target selection,
  JaCoCo progress comparison, path refresh, and terminal status.
- **Publication handoff** — publishes only PR-eligible runs after local
  CI-equivalent verification passes (§AR-forge-verification-publication-boundary).
  Implemented in `forge/git_scripts/make_pr_code_coverage_improvement.py`, which
  renders the PR body from the phase-9 finalization metrics.

## 2. Workflow State

The workflow state is target-based, not call-site-based. Public API state comes
from the exact inventory/JaCoCo join. Deep state comes from exact JaCoCo library
methods joined to the static graph after public inventory entries are removed
(§WF-dynamic-access-workflow).

The code coverage test suite should be physically and logically separate from
the test suite used to generate reachability metadata. The workflow may inspect
metadata-generation tests as context, but generated coverage tests must be
written to the coverage-suite root and measured with code-coverage metrics.
Metadata-generation tests remain the source of dynamic-access and native-image
metadata evidence.

Target state should include:

- target identifier and human-readable behavior description
- phase and source of the target
- baseline and current exact JaCoCo evidence
- sampled context and static reaching path when available
- selected generated test files
- attempt count and last attempted iteration
- terminal target status: completed, skipped, exhausted, failed, or pending
- semantic reason when the target cannot be covered automatically

The persisted state should be coordinate-scoped and stable enough for
orchestration to resume later runs from the coordinate alone, following the same
operational shape as chunked dynamic-access exhaust state without sharing the
dynamic-access report schema (§WF-dynamic-access-exhaust-report).

## 3. PGO Profile Handling

Raw PGO artifacts are machine navigation evidence. The analyzer maps sampled
contexts onto the static graph and emits concise path groups; it does not infer
coverage or non-execution from sampling.

For each exact JaCoCo-uncovered internal method, the analyzer first finds the
shortest static path from any mapped sampled frame. If no sample joins, it finds
the shortest public API entry path. A missing sample, missing graph node, or
missing public route changes only navigation fields. It never changes the
JaCoCo status.

The prompt presents representative divergence groups rather than raw profile
records. JSON retains the complete sampled contexts and method records for
auditing. PGO progress asks whether navigation changed; JaCoCo progress asks
whether generated tests covered methods. Only the JaCoCo answer contributes to
coverage success.

A PR-eligible result pairs exact JaCoCo deltas with generated-test rationale,
sampled/static guidance, and deterministic verification results. Profile growth
alone is never sufficient.

## 4. Prompting and Review Evidence

The public prompt lists exact uncovered API entries. The deep prompt lists
compact observed/uncovered path groups and instructs the agent once to reach
internal methods through public behavior. Neither prompt encourages direct
invocation of implementation details merely to increase coverage.

Metrics and PR publication should expose:

- baseline and updated JaCoCo summary paths for each phase
- sampled-profile and static-graph evidence paths, labeled guidance-only
- selected public and deep targets with terminal statuses and attempt counts
- generated or modified test paths
- exact JaCoCo delta summary
- verification commands and results
- skipped or exhausted target reasons

Review automation and maintainers should be able to distinguish this workflow's
evidence from dynamic-access coverage evidence. A PR that improves code
coverage should not be presented as a dynamic-access coverage fix unless it
also ran and reported the dynamic-access workflow (§WF-improve-library-coverage).

## 5. Implementation Status

The Rhei implementation includes a validated template/example, exact API and
deep JaCoCo helpers, sampled-PGO/static-path analysis, an opt-in dedicated test
suite, native metadata preparation, durable target-state and final-metrics
schemas, schema-validated finalization, and PR publication. The Gradle harness
produces the sampled `.iprof` and one analysis call-tree CSV triplet per deep
report. The remaining architecture work is a Forge driver or driver mode that
lets the control plane claim an issue and launch this already executable Rhei
lane automatically.
