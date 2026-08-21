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

That worktree's branch is created from the HEAD of the source checkout, never
from `origin/master`. Measurement resolves this workflow's own helpers from the
issue worktree, so basing it on a commit that predates them fails the
measurement steps outright rather than degrading gracefully.

The instantiated workspace directory must be named
`code-coverage-<issue_number>`. Rhei qualifies `{task_id}` with the workspace
directory name, so that name decides where every task's output is expected,
while the task bodies name those artifacts from `issue_number`. Any other
directory name makes the two disagree: the agent is told one path and the
output-existence check reads another, and the task stalls in a non-terminal
state after an agent that exited zero. Runs that must not collide — a second
model, agent, or pass budget on one library — separate themselves by parent
directory, and their published branches by the model the head branch already
names and by the publication ID it carries, never by renaming the workspace. A workspace
under a parent directory must also set `workspace_path`, since the cover states
tell the agent where to resolve artifacts from and that path is no longer
derivable from `work_subdir` alone.

Restart a run by re-instantiating the workspace, never with `rhei reset`. Reset
rewrites every task to the state machine's single initial state, `prepared`,
while this workflow's measured tasks declare their own entry states —
`api-measure`, `deep-measure`, `reviewed-prepared`. A reset workspace therefore
runs both coverage loops as one generic pipeline pass each: an agent is spawned,
the task reaches `completed`, and no measurement program ever runs. The run
produces no JaCoCo report, no ranked prompt, and no coverage figure, yet reports
every task terminal — and publication will happily open a pull request for it.

Cover states name the Gradle invocation rather than leaving the agent to infer
it: compiling and testing happen inside the issue worktree, scoped with
`-Pcoordinates=<coordinate> -PincludeCodeCoverageSuite=true`. An unscoped
invocation configures every library in the repository, so it runs for hours and
still never compiles the suite under measurement; `--no-daemon` pays that
configuration cost again on every call. Naming the command is harness usage, not
coverage guidance — it says nothing about what to cover — so an arm that needs
it stays comparable to one that inferred the same form on its own.

The tests produced by this workflow must be a separate test suite from the
tests used to generate or validate reachability metadata. Code coverage tests
exist to exercise the whole practical library API; metadata-generation tests
exist to exercise dynamic-access behavior and validate native-image metadata.
The suites may target the same coordinate, but they must have separate
locations, metrics, and publication evidence so improving broad API coverage
does not change the meaning of metadata-generation coverage.

Generated code coverage tests are a tracked extension suite written under
`tests/src/<group>/<artifact>/<test-version>/code-coverage-improvement`, where
`<test-version>` is the indexed test project directory that covers the
coordinate. The workflow may read the metadata-generation tests as context, but
generated code coverage tests must not be placed in the metadata-generation
suite, and the regular `src/test` sources are read-only for this workflow.

The suite root must contain `src/test/java` and may contain
`src/test/resources` and a `suite.json` recording the true coordinate being
improved. It must not carry a metadata directory of its own. Reachability
metadata has exactly two homes in this repository, and the extension suite
contributes to the same two as every other test: entries a consumer needs go
to `metadata/<group>/<artifact>/<version>/reachability-metadata.json`, and
entries that exist only for the tests go to the test project's
`src/test/resources/META-INF/native-image/reachability-metadata.json`
(§root/METADATA-suite.2, §root/FS-repository-functional-spec.5.1). The single-file
`reachability-metadata.json` format is the only one `native-image` loads here;
the legacy split-config files — `reflect-config.json`, `jni-config.json`,
`resource-config.json`, `serialization-config.json`, `proxy-config.json` — are
never used in this repository (§root/METADATA-suite.1), so a native-image tracing
agent run that emits them has produced input to convert, not output to commit.
Which of the two homes an entry belongs to is not decided by hand: finalization
runs `splitTestOnlyMetadata` and the split is by the entry's own type
(§4). The build maps the suite to a dedicated
`codeCoverage` source set with its own `codeCoverageTest` JVM task and a
combined `jacocoCodeCoverageReport`; `jacocoTestReport` stays the
regular-suite baseline. Native lanes opt in with
`-PincludeCodeCoverageSuite=true`, which widens the test source set for that
invocation because the plugin-managed native test binary is derived from it.
Ordinary metadata-generation and validation commands that omit the property
continue to use only their existing test source tree.

When an extension test exposes a real metadata gap, the fix belongs in the
shipped `metadata/`, and the test justifying it should be promoted into the
regular metadata-generation suite so the invariant "shipped metadata is
justified by always-run tests" holds; the extension suite remains an optional
breadth layer.
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
report marks uncovered, at most 400 per pass. The cap is 400 rather than 200
because consumption tracks the supply of feasible targets: raising it moved a
measured run from +950 to +1082 covered methods and shifted the gain from
collateral coverage to targets the prompt actually named, with hits
concentrated in ranks 201-400. The agent must work across the
whole supplied batch through realistic public API usage and assertions, without
superficial coverage-only invocation. The phase runs for a fixed budget of
`coverage_iterations` passes and stops early when no public target remains
uncovered. The budget is deliberately a constant rather than a function of the
baseline uncovered count: scaling it made phase length depend on how that count
is defined, so redefining the report summary silently halved it, and a
constant cannot drift that way.

#### 3.1.1 Target selection by unlocked internal code

Public API entries are not equally valuable: some open up large amounts of
internal code, others only themselves. Selection therefore ranks entries by how
much still-uncovered code each one unlocks, and never by identifier order.
Ranking is advisory navigation; it never changes any method's JaCoCo status.

Ranking decides the **order** of the prompt, never its membership. Every
eligible uncovered public entry (§3.1.2) remains a legitimate target, because
this phase is scored on public methods covered — an entry that reaches no
internal code is still an uncovered public method worth a test. Selection must
therefore fill the prompt to its cap whenever that many candidates exist.

The unlock universe is every still-uncovered library-owned method **with a body,
derived from the library bytecode**, public API entries included. Membership of
the entries themselves is what guarantees the previous paragraph, and it holds
**against every other pick**: a candidate's own bit is never subtracted by
another selection, so a body-carrying candidate always scores at least one and
can never be eliminated. Only a candidate that holds no bit at all can score
zero. That distinction is the whole contract here — being *reached* by a
selected entry is a static over-approximation, whereas the score is exact
execution, so an entry left out because something else statically reaches it is
an uncovered public method the prompt can no longer ask anyone to call.

Methods without a body are the sole zero: JaCoCo can never mark an abstract or
interface method covered, so it cannot be a target. Deriving the
universe from JaCoCo instead would be wrong here: a JaCoCo report contains only classes some test
loaded, so a JaCoCo-derived universe systematically omits the untouched code
this phase exists to open up. JaCoCo remains the sole coverage authority — the
bytecode decides which methods exist, JaCoCo decides which are covered, and any
universe method absent from the JaCoCo report counts as uncovered.

Note that this universe is deliberately **not** the same set as the deep phase's
target universe (§3.2), which is JaCoCo-derived. The two lists serve different
jobs: this one ranks entries for selection, that one enumerates deep targets for
measurement.

Reachability comes from a static call graph built from the library bytecode, not
from the Native Image analysis call tree. The analysis call tree contains only
methods reachable in the built image, which excludes precisely the code no
current test reaches, and it costs a native build to obtain. Virtual and
interface calls are resolved by class-hierarchy analysis, so reachability
over-approximates; that is acceptable for ordering candidates and never used as
evidence of coverage.

Selection is a budgeted greedy maximum-coverage pass. It repeatedly takes the
uncovered public entry that unlocks the most universe methods not already
unlocked by an earlier pick, plus its own bit, then removes that entry's
reachable set from further consideration. Overlap subtraction is what orders
redundant overloads and delegating wrappers last without any special-casing:
once one member of a delegating family is picked, the rest unlock nothing new
and fall to their own single bit — last in the order, but still in the prompt,
because covering the caller is not the same as executing the callee. Ties break
on canonical id so selection is deterministic.

The call graph is a property of the library, so it is extracted once and cached
under `runtime/code-coverage/graph/` for the whole phase. Reachable sets are not
cached: the universe holds only still-uncovered methods, so it shrinks after
every pass and the bit layout moves with it. They are recomputed per iteration
from the cached graph, which is one linear pass over the condensation and cheap
next to the JaCoCo run that precedes it.

Selection cost weighting — preferring entries that are cheap for an agent to
construct — is deliberately not part of this contract. Parameter count is a poor
proxy for construction difficulty, and repeated-attempt state provides an
empirical signal instead.

#### 3.1.2 Target eligibility by receiver obtainability

Ranking orders candidates; eligibility decides which entries may become
candidates at all. A public method is only a legitimate prompt target when a
test can actually invoke it, and the `public` modifier alone does not establish
that. A public method on a class the test can never obtain an instance of is
unreachable from any test, no matter how much internal code it statically
unlocks.

An entry is eligible when one of the following holds:

- it is a public constructor, or a public static method — neither needs a
  receiver;
- its declaring type is **obtainable**;
- it overrides a method declared on an obtainable supertype. The test holds the
  supertype and dynamic dispatch lands in the implementation, so it never names
  the declaring class. This case carries the DSL implementation classes, whose
  interface declarations are abstract and therefore outside the unlock universe
  entirely — without this rule the only coverable member of such a pair is
  ineligible while the only eligible one has no body.

A type is obtainable when it has a public constructor, when it is the return
type of an eligible method (element type, if that return type is an array), or
when it is a supertype of an obtainable type — holding an instance permits every
supertype method call on it. The definition is a least fixed point over those
three rules, computed once per library from the same bytecode the call graph
comes from.

A type reachable only by an explicit downcast is **not** obtainable. Casting to
an internal implementation is not realistic public API usage, which the prompt
requires, and the cast's success is a runtime property no static rule
establishes. Type erasure is the known limitation of this definition: a generic
return type erases to its bound, so a type obtainable only as a generic element
is invisible here and is treated as not obtainable.

Ineligible entries stay in the coverage denominator. They are still library code
and still execute collaterally when a realistic test exercises the surrounding
subsystem; what they are not is something an agent can be asked to target.

### 3.2 Deep implementation coverage

The second phase starts only after public API coverage and native metadata
preparation. Its target universe is library-owned methods reported by JaCoCo
that are not public API inventory entries. JaCoCo remains the sole coverage
metric for these internal methods.

"Library-owned" is decided by the resolved library jars, not by the JaCoCo
report alone. A JaCoCo report covers every instrumented class on the test
runtime classpath, and a library that publishes a `test`-classifier artifact
puts its own unit tests there — in its own packages, so no package prefix
separates them. Taking the report at face value silently makes those tests deep
targets and inflates the denominator the phase reports. The universe is
therefore intersected with the method list the bytecode extractor writes for the
API phase (§3.1.1), so both phases anchor to the same jars. Methods dropped this
way are counted as `nonLibraryMethodsExcluded`; when no method list is supplied
the report carries an explicit caveat instead of filtering silently.

Note that the corresponding Gradle behaviour is deliberately left alone:
`resolveTestedLibraryJars` matches artifacts by group and name without a
classifier, so JaCoCo analyses the `test` artifact too. Narrowing it there would
also narrow the repository's dynamic-access measurement, which may legitimately
need metadata for classes in that artifact. The workflow defends itself instead.

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
Markdown and target-id list contain at most 200 methods globally. Measurement
itself carries attempt state deterministically in the discovery-report history:
every target it prompted gets its attempt count incremented at the next
measurement, ranking prefers less-attempted targets, and covered targets leave
the uncovered set — so later iterations advance beyond the first 200 without
any agent-written state.
The deep phase runs for the same fixed `coverage_iterations` budget and stops
early when no actionable target remains.

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

#### 3.2.1 Synthetic method attribution and route honesty

One lambda in source leaves three artifacts in bytecode: the enclosing method a
person wrote, the body the compiler extracts (`lambda$enclosing$0`), and the
class the image generator emits to implement the functional interface
(`Owner$$Lambda/0x…`). Only the first carries a name an agent can write a test
against.

Compiler-owned methods — extracted lambda bodies and access bridges — are
therefore never prompt targets. They stay in the deep universe and in the
coverage denominator, because JaCoCo reports them and JaCoCo is the sole
coverage authority; dropping them would raise the reported percentage without a
single new test. Their counts are reported as `deepSyntheticMethods` and
`deepSyntheticUncovered`, so the exclusion is visible rather than silent.

What those rows carried is attributed to the enclosing method instead: its
prompt entry states how many closures it owns and how many never execute, which
tells the agent that entering the method is not sufficient. The enclosing method
is resolved through the generated class — the caller of its constructor — rather
than by parsing the body name, which carries the enclosing name without its
parameter types and so cannot separate overloads. When the enclosing method is a
public API entry rather than a deep target, the note belongs to the API-cover
prompt (§3.1.1); when it resolves to nothing, no note is emitted.

Route selection distinguishes two edges that the analysis dump renders alike. A
**creation** edge runs from the method that captures a closure to that closure's
body, and is synthesized where the dump links the body only to the generated
class; it is honest navigation, because calling the enclosing method is what
brings the body into play. A **dispatch** edge runs from a call on a functional
interface to every implementation the analysis considers possible; its receiver
was captured elsewhere and handed in, so the edge says nothing about what its
caller reaches. Dispatch edges remain in the graph, since removing them would
cut reachability, but they are not used to build routes. A target whose only
route was a dispatch edge falls back to a longer honest route, or to none, which
is where it belongs.

Prompt-facing paths render synthetic nodes as the method that creates them and
collapse consecutive nodes that render alike; the untranslated path is retained
in JSON. When a route reaches its target through a closure the enclosing method
hands to a scheduler or executor, the entry says so: the body then runs on
another thread, and a test that does not wait for it covers nothing.

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
   no uncovered public target remains or the fixed budget of
   `coverage_iterations` (default 10) passes is spent.
   When the loop continues, measurement also derives the prompt of at most 200
   exact JaCoCo-uncovered public methods from that report. The cover agent attempts the complete supplied batch through
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
   the loop with the same fixed `coverage_iterations` budget, and derives the compact
   at-most-200-method prompt when it continues. The cover agent batches related paths, reaches
   internal methods through public behavior, and always returns to measurement;
   it writes no target state — measurement tracks attempts and rotation
   deterministically from its own report history.
7. **Finalization** — a deterministic step program: read the machine-readable
   conversion record; run `splitTestOnlyMetadata` and then `checkMetadataFiles`;
   run checkstyle over the coordinate's subprojects (including the tracked
   coverage suite); run the regular JVM tests (`javaTest`) and the tracked
   extension suite (`codeCoverageTest`); and persist final metrics from the
   baseline and highest-iteration JaCoCo and deep reports. The split is the same
   step the dynamic-access workflows run at their own finalization
   (§WF-improve-library-coverage), and for the same reason: metadata the
   extension suite needed only for its own helper types must not reach a
   consumer, and deciding that by hand is exactly what the task automates
   (§root/METADATA-suite.2, §root/TCK-test-harness.5). The step also fails the run when a
   legacy split-config file survives anywhere under the coordinate's test tree,
   which is how a tracing-agent artifact left behind by metadata preparation is
   caught before publication rather than in review (§2). No Native Image
   validation runs at this stage; a nonzero exit code names the failed step.
8. **Publication** — push the verified branch and let trusted GitHub Actions
   open the pull request. Local publication stages the coverage suite and the
   metadata it justified, rebases onto upstream `master`, runs the
   pre-publication verification gate, writes
   `stats/<group>/<artifact>/<version>/forge-publication.json`, and pushes
   `ai/<login>/...` — nothing else. `Forge Branch Ready` then validates that
   exact commit as data, and only its success lets `Forge Open PR` render the
   body and open the PR as the machine account, with the fixed `GenAI` and
   `code-coverage-improvement` labels and the configured reviewers
   (§GIT-actions-publication). The workflow keeps no PR-creation credential and
   opens nothing itself (§AR-forge-verification-publication-boundary).

   The descriptor carries the render inputs and only those: coordinate, coverage
   suite path, baseline and final JaCoCo coverage for each guidance phase, the
   human-intervention flag, the issue-resolution flag, the generating model, and
   per-phase token usage read from the Rhei accounting directory. Per-target
   rosters, sampled PGO evidence, and the validation command list stay in the
   finalization artifacts: the target counts restate what the coverage figures
   already say, and the commands embed the run's own absolute worktree paths,
   which no reader of the PR can execute. A reviewer who wants per-target detail
   reads it from the run.

   The trusted renderer reports coverage against the methods JaCoCo reports, not
   against every inventory entry: entries JaCoCo never reports are ones no run
   can cover, and charging them to the run understates it. The combined figure
   adds the two phases directly, which is sound because the deep universe holds
   exactly the library methods the API inventory does not. A phase whose
   accounting is not yet written is omitted from the token table rather than
   reported as zero; publication itself is normally omitted, since its own
   invocations are still running when the descriptor is written.

   The head branch is
   `ai/<login>/code-coverage-<artifact>-<version>-<model>[-<suffix>]-<publication-id>`.
   The model segment is the model of the run's `worker_agent` target — the
   trailing component of `<agent>[<mode>]:<provider>/<model>` — so a coordinate
   measured on two models publishes two branches without any operator action,
   and the branch names what generated the pull request it carries. The
   publication ID is derived from the issue, the finalized run's `generatedAt`
   timestamp, the coordinate, and the task type, so it separates two runs that
   share a model while a retried publication of one run rebuilds the same branch
   and reuses its pull request. `branch_suffix` no longer keeps runs apart; it
   only labels which run a branch belongs to.

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

Every fix state that can be entered more than once is a counted Rhei state and
writes a visit-scoped output. API and deep fix states use the corresponding
measurement visit cap, which records each fix entry without tightening the
measurement-owned retry budget. The finalization fix state uses the configured
`fix_passes` cap. This ensures that an output from an earlier repair pass cannot
satisfy the output-existence completion check for a later pass.

## 5. Acceptance Criteria

A code coverage improvement run is successful only when all of these hold:

- A Rhei template can convert one `code-coverage-improvement` issue into an
  executable workspace with deterministic finalization verification and bounded
  fix routing.
- Re-entering an API, deep, or finalization fix state starts a fresh agent pass
  and requires a distinct visit-scoped fix artifact.
- The generated tests are meaningful behavior tests and do not invoke internal
  methods directly merely to raise coverage.
- Public API and deep implementation work use separate reports and prompts.
- API inventory generation produces compact JSON and Markdown for public
  user-callable methods and constructors.
- API iteration zero establishes a JaCoCo baseline; each public API agent
  iteration is followed by exact JaCoCo correlation.
- API prompts contain only exact JaCoCo-uncovered public targets and ask the
  agent to attempt the complete supplied batch.
- Deep targets are library-owned JaCoCo methods minus public API inventory
  entries. Exact JaCoCo evidence alone determines their status.
- Sampled PGO and the static call graph change only deep-path guidance and
  ranking; they never change covered, uncovered, or unknown status.
- Near-call distance is the shortest directed static path from a sampled frame;
  prompt-quality and sample-count preferences only break equal-distance ties.
- Full JSON retains every deep target and path record. Prompt Markdown contains
  at most 200 actionable methods and uses compact `Observed` /
  `Uncovered paths` navigation.
- The last budgeted deep-cover iteration is followed by a final report before
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
JaCoCo results, sampled paths, generated-test rationale, and target outcomes
as separate evidence so reviewers can judge whether the tests exercise
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
- **Bytecode call-graph extractor** — reads the resolved library artifacts and
  emits every method and every call edge as CSV, using canonical identities
  shared with the identity model. Implemented with the JDK Class-File API in
  `forge/utility_scripts/java/CallGraphExtractor.java`, run through single-file
  source launch so it needs no build step. It reads class files directly rather
  than parsing `javap` output, which keeps `invokedynamic` lambda targets and
  raw descriptors exact (§WF-code-coverage-improvement.3.1.1).
- **API target ranker** — orders JaCoCo-uncovered public entries by the amount of
  still-uncovered code each unlocks and renders the API-cover prompt, filling it
  to the cap. Implemented in `forge/utility_scripts/code_coverage_api_rank.py`.
  It reuses the cached call graph and recomputes reachable sets each iteration
  against the shrinking universe (§WF-code-coverage-improvement.3.1.1).
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
  restricts what remains to the methods the resolved library jars declare via
  `--library-methods`, and uses sampled `.iprof` stacks only to navigate
  JaCoCo-uncovered internal methods. It retains every record in JSON and emits compact `Observed` /
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
- **Target-state store** — measurement-owned attempt and rotation state carried
  in the discovery-report history; agents never author target state. Externally
  supplied schema-valid state files remain accepted at finalization.
- **Workflow engine** — owns prompt/command cycles, retries, target selection,
  JaCoCo progress comparison, path refresh, and terminal status.
- **Worker target** — the single `worker_agent` input drives every agent state,
  defaulting to `pi[high]:openai-codex/gpt-5.6-luna`. Rhei's target grammar
  carries the reasoning level in the mode bracket, not in the model string: a
  model written as `<model>:<thinking>` is parsed as a provider/model pair and
  silently resolves to a different model. The template therefore bundles a `pi`
  agent profile in its `settings.json` whose `high` and `xhigh` modes add
  `--thinking`, and publication reads the model back out of the same target to
  name the head branch (§WF-code-coverage-improvement.4). That profile replaces
  Rhei's built-in one outright rather than extending it, so it restates the
  `session` block as well: without it Rhei passes no `--session-dir`, cannot
  read back the agent's native transcript, and silently captures no per-state
  snapshot for a workflow whose every agent state is otherwise snapshotted.
- **Publication handoff** — publishes only PR-eligible runs after local
  CI-equivalent verification passes (§AR-forge-verification-publication-boundary).
  Implemented in `forge/git_scripts/publish_code_coverage_improvement.py`, which
  contributes this route's expected paths and descriptor to the shared branch
  publication pipeline (§GIT-shared-publication-pipeline); the trusted
  `code-coverage-improvement` template in
  `.github/scripts/forge_pr_publisher/publisher.py` renders the body from the
  descriptor. The descriptor's timestamp is `generatedAt` in the finalization
  metrics rather than the wall clock, which is what makes a retried publication
  reuse one branch and one pull request instead of opening a second.

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

Target state is derived deterministically by measurement from its own prompt
and report history — no agent-authored status exists. It should include:

- target identifier and human-readable behavior description
- phase and source of the target
- baseline and current exact JaCoCo evidence
- sampled context and static reaching path when available
- attempt count and last attempted iteration

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
