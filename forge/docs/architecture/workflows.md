# AR-forge-workflow-system: Forge workflows

A **workflow** is a registered engine that owns the ordered run state for one
claimed unit of work: it sends prompts through the agent API, runs deterministic
gates, interprets the results, decides whether to retry, advance, fail, or
succeed, and returns exactly one terminal status. Workflows are the core
execution objects of Forge (§FS-forge-issue-resolution-goal).

Two neighbouring things are not workflows. A **driver** prepares one claimed
issue — worktree, branch, test project, source context, strategy, metrics — then
instantiates a workflow and finalizes what it returns; drivers are per-queue and
are specified by §AR-forge-drivers. A **strategy** is the named configuration
bundle that selects which workflow runs and with which agent, model, prompts,
and parameters; the bundle contract is §FS-forge-predefined-strategy-contract.
The same workflow runs under many strategies, and one driver may run different
workflows for different phases of its issue.

Forge registers six workflows:

| Workflow | Role in a run |
| --- | --- |
| `basic_iterative` | Generate tests with no coverage report to steer by. |
| `javac_iterative` | Repair test sources that no longer compile against a bumped version. |
| `java_run_iterative` | Repair JVM-mode test failures against a bumped version. |
| `dynamic_access_iterative` | Explore a dynamic-access report one uncovered class at a time. |
| `bulk_dynamic_access` | Explore the whole dynamic-access report in one broad pass. |
| `increase_dynamic_access_coverage` | Composite: run a primary workflow, then explore what it left uncovered. |

Most Forge work is one of two shapes. A **fix** workflow makes a broken test
project work again; an **explore** workflow raises dynamic-access coverage. The
composite is how a driver runs both in one issue: fix first, then explore if the
remaining surface is worth exploring.

## AR-forge-workflow-engine: What every workflow owns

A workflow receives an agent, rendered prompt configuration, workflow
parameters, repository paths, coordinate context, and run metadata. The agent is
the backend-neutral API (§AR-agent-api), not a per-workflow implementation.

It owns, for the whole run: checkpointing, the prompt and command cycle, local
gate interpretation, retry budgets, failure handoff, metrics updates, and the
choice of terminal status (§FS-forge-run-status).

It does not claim issues, prepare worktrees, resolve which library version to
edit, or publish. Those belong to the control plane, the driver, and publication
respectively (§AR-forge-control-plane, §AR-forge-drivers,
§AR-forge-verification-publication-boundary). A workflow that cannot finish
returns a failure status and leaves its logs and working tree intact; it never
decides that a partial result is publishable (§FS-durable-generation-logs).

### 1. Progress must be committed as it is accepted

Every workflow checkpoints accepted work so that a later failure resets to the
last coherent state rather than discarding the run. What counts as coherent
differs by workflow and is stated with each one below, but the rule is the same:
a reset must preserve work that already passed a gate and discard only the
attempt that failed.

### 2. Every workflow ends on the native test verification gate

A passing JVM test proves nothing about the metadata a native image needs, so no
workflow may report success on JVM evidence alone. Whatever a workflow used to
steer generation — a coverage report, a compiler error, nothing at all — its
terminal criterion is the native test verification gate
(§FS-native-test-verification-gate). A `FAILED` gate is a workflow failure;
partial recovery is not an acceptable terminal state. Which engine invokes it,
and how often, is specified by §AR-native-test-verification-callers.

This holds for repairs too, including a compilation repair whose own failure mode
was not about metadata. A version bump that changed an API changes what the tests
execute, and what they execute is what determines the metadata the image needs —
so a repaired version is not a verified version until a native image has run it.
Finalization's native-test lanes (§FS-local-ci-equivalent-verification.1) are a
publication gate, not a substitute: they run once, after the workflow has already
declared success, and a workflow must not hand a branch to finalization on
evidence it has not gathered itself.

Direct gating matters most where the generation signal is weakest. A
dynamic-access report only names call sites in the requested artifact, so metadata
required by a transitive dependency is invisible to it, and the gate is where that
is caught.

## AR-forge-workflow-strategy-config: Strategies bind to workflows

A strategy bundle names exactly one workflow. That name is the binding: the
loader resolves it to the registered engine, and the engine interprets the
bundle's parameters (§FS-predefined-strategy-loader). A parameter a workflow
does not read has no effect, so the set of parameters a workflow interprets is
part of that workflow's contract and is listed with it below.

Strategies come in families rather than as individually meaningful bundles: the
registry holds many agent and model permutations of the same configuration, and
which permutation is current changes without any behavior changing. This spec
therefore names families and parameters, never bundle names or model versions —
the registry is the source of truth for what exists today
(§FS-forge-predefined-strategy-contract).

Changing a strategy may change how an existing workflow is parameterized or
which workflow is selected. Changing what a workflow *does* is a change to the
workflow and to this spec, not to a bundle
(§FS-workflow-strategy-registry).

## AR-basic-iterative: Basic iterative

`basic_iterative` is the narrowest workflow: prompt the agent to write tests,
run the coordinate's test task, feed failures back, repeat within budget. It has
no coverage report, so it cannot choose what to cover next and cannot measure
whether an attempt gained anything.

It is not the workflow for any issue queue. It exists as the fallback the
dynamic-access workflows delegate to when no usable report exists at the start
of a run (§AR-dynamic-access-fallback-and-failure). Because its loop accepts a
failing native test as progress, the terminal gate is doing all the real
validation for this workflow. The fallback is exploration, not repair: it skips
`fix`, reports its steps under `explore`, and leaves `explore` running when its
caller has more exploration work to perform.

Parameters: generation and test-iteration budgets
(§FS-predefined-strategy-parameter-families). Families: `basic_iterative_*`.

## AR-java-fail-fix-workflow: Java fix workflows

`javac_iterative` and `java_run_iterative` repair an already-supported library's
test project after a version bump breaks it on the JVM, before any native-image
concern. They share one model — run the test task, render the failure into a
prompt, loop until the failure clears or the budget runs out — and differ only
in what counts as the failure they are chasing:

- `javac_iterative` chases a compilation failure: the bumped library changed an
  API the test source uses.
- `java_run_iterative` chases a runtime failure: the test compiles but fails
  during the JVM run with a missing method, a missing class, changed behavior,
  or a new exception.

Reaching the native test task, or no failed task at all, is JVM success — these
workflows are not responsible for native-image behavior beyond the terminal
gate.

A repair must keep the test meaningful. Making a test pass by reducing it to
triviality is a workflow failure even though the command exits zero, because the
test no longer justifies the metadata it produces
(§FS-local-ci-equivalent-verification). Neither workflow raises coverage: a
driver that wants the repaired version explored runs them as the primary
workflow of the composite (§AR-dynamic-access-composite).

Parameters: test-iteration budget, source-context types. Families:
`javac_iterative_*`, `java_run_iterative_*`.

## AR-dynamic-access-workflow: Dynamic-access exploration

Reachability metadata can only be generated for dynamic access that actually
happens at runtime. The reachability repo's coverage tooling makes that
measurable: it produces a **dynamic-access report** naming every class in the
target library that performs reflection, JNI, resource, serialization, or proxy
access, with each call site marked covered or uncovered
(§root/FS-tests). "Covered" means the repository's tests
reached it. Exploration is the work of turning uncovered call sites into covered
ones by writing tests that exercise them through public API
(§GOAL-maximize-library-coverage).

The report is also the measurement. A workflow regenerates it after each
accepted attempt and correlates the new report against the previous one to
decide what the attempt achieved: call sites newly covered are progress worth
committing, an unchanged report is a wasted attempt, and a class that vanished
from the report is resolved only if overall coverage rose. This correlation —
not the agent's account of what it did, and not whether the test suite passed —
is what advances the run.

Forge explores in two ways, and they trade the same thing against each other.
**Iterative** exploration prompts one uncovered class at a time with that
class's remaining call sites, so every attempt has a precise target and a
precise measurement, and progress commits per class. **Bulk** exploration
hands the agent the entire report and asks for one broad pass, which is far
cheaper when the library is easy and wasteful when it is not. An optimistic
composite runs both, bulk first and iterative to refine what the bulk pass
missed.

### 1. Chunking

An oversized report is not explored in one run. An iterative-only run receives
a concrete class budget before it starts. A run with a bulk phase
receives the configured class boundary and decides after bulk: classes completed
by bulk count first, iterative exploration fills only the shortfall, and a final
remainder no larger than the boundary is finished in the same run. The workflow
returns a chunk-ready status once the resulting part passes local verification,
and work resumes in a later run against the merged result
(§FS-forge-chunked-dynamic-access). The workflow also owns the durable record of
which classes are already done (§AR-dynamic-access-exhaust-report).

## AR-dynamic-access-iterative: Iterative exploration

`dynamic_access_iterative` is the required workflow when Forge must make
class-scoped, reviewable progress on the coverage-improvement queue
(§FS-forge-scope). It generates the initial report, selects one
uncovered class, and gives that class a bounded number of prompt attempts; each
attempt gets a bounded number of test-repair rounds before the class is rolled
back to its checkpoint.

Per class, the correlated report decides the outcome. All call sites covered
resolves the class and commits it. Some call sites newly covered commits partial
progress and advances the checkpoint, then tries the class again. No gain
consumes an attempt without a commit. A class that exhausts its attempts is
recorded as exhausted and never reselected, so a later resumed run does not
spend budget on a class Forge has already failed to cover.

Committing per class is what makes chunking and resume possible: each terminal
class transition advances the checkpoint past its own commit, so a whole-phase
reset preserves the classes already recorded rather than resurrecting them.

Parameters: prompt-attempt budget per class, test-iteration budget per class,
source-context types, native-test verification budget. Families:
`dynamic_access_*`, library-update coverage strategies.

## AR-dynamic-access-bulk: Bulk exploration

`bulk_dynamic_access` refreshes the report, gives the agent all of it, and
asks for a broad pass. Test failures before the native test task go back to the
agent until the retry budget is exhausted; an iteration that never reaches the
native test task resets to the checkpoint and the next iteration may try again.
An iteration that does reach it regenerates the report, commits the attempt, and
runs the terminal gate.

When the initial report cannot steer generation, bulk exploration first
runs the gated basic-iterative fallback to create a real test surface, then
refreshes the report. A usable refreshed report enters the normal bulk iteration
budget; the composite subsequently hands the final gated report to iterative
class-by-class exploration. If the fallback succeeds but the refreshed report
still has no dynamic-access calls, its gated result is terminal because neither
bulk nor class-scoped exploration has a target
(§AR-dynamic-access-fallback-and-failure).

Unlike iterative exploration, this workflow does not commit per class and does
not record exhausted classes — a bulk attempt is accepted or reset whole. It
succeeds only if at least one iteration reached the accepted state. For a
chunked run it retains the initial report and, after the full iteration budget,
compares it with the report refreshed immediately before the last passing gate.
Every class that changed from uncovered to covered is recorded as completed;
bulk never records a class as skipped, exhausted, or failed because it cannot
attribute those outcomes to one class. The same comparison records the total
covered-call gain so a partial improvement inside one class remains visible to
the composite. This comparison runs once after the bulk loop and does not
regenerate the report.

In a pure-bulk workflow, a productive pass with more than the configured
threshold still remaining returns chunk-ready. A zero-yield pass is final, as
is a pass whose remainder is no larger than the threshold, because no iterative
phase exists to make a stronger class-scoped attempt.

Bulk exploration is the right first phase when the library is expected
to be easy, when a coverage improvement should be cheap, and for runs that
supply a larger source graph as context (§GOAL-shorten-issue-to-shipped-metadata).

Parameters: bulk-iteration budget, test-iteration budget, source-context types,
optional graph-assisted context. Families: `dynamic_access_bulk_*`,
`dynamic_access_graphify_bulk_*`.

## AR-dynamic-access-composite: Composite fix-then-explore

`increase_dynamic_access_coverage` runs a configured primary workflow and then
explores what it left uncovered, so one run can serve both a repair queue and
the coverage-improvement queue (§FS-forge-scope). It serves two profiles:

- **Fix then explore** — a Java fix workflow as primary, so a repaired version
  is also improved before it is published.
- **Explore only** — no primary workflow configured, so the run starts directly
  at iterative exploration. This is how coverage is improved for a library whose
  tests already exist and already pass.

A failing primary workflow is returned unchanged and exploration never starts:
there is nothing to explore on a test project that does not work, and the
primary failure is the one worth reporting. When the exploration phase returns
chunk-ready, the composite returns that immediately — a chunk is a reviewable
boundary, and work that depends on reaching final success waits for the resumed
run rather than blocking the chunk.

When the primary workflow is `bulk_dynamic_access`, the composite takes
the chunk boundary after the bulk loop and its gates, before iterative
exploration. Bulk-completed classes count toward the invocation's class
boundary. If more than the boundary remain and bulk already met the boundary,
the composite returns chunk-ready immediately. If bulk did not meet it,
iterative exploration receives only the shortfall; a zero-yield bulk pass
therefore gives iterative the whole boundary. When no more than the boundary
remain, iterative exploration receives the entire remainder and finishes the
final chunk even if bulk already met the boundary.

An optimistic composite first generates the report that would steer bulk. When
the report is usable but names fewer uncovered classes than the configured
`bulk-min-uncovered-classes`, it skips the bulk primary and starts iterative
exploration with that same report. The budget it hands over is the remainder
iterative exploration can still take, measured against the exhaust report and
continuation marker like the post-bulk boundary is. A small report whose
remainder is entirely processed therefore stays with bulk, which is the only
phase that prompts again on a class per-class exploration exhausted. A report
at or above the minimum is handed to bulk without another refresh. A report
with nothing uncovered left skips both phases and completes exploration. An
unavailable or empty report still enters bulk so its basic-iterative bootstrap
remains available. Whenever the composite skips the primary it releases the
same pending fix phase the primary would have released, so a skipped primary
never holds back the continuation phase order (§FS-forge-run-continuation.1).
This routing belongs to the composite only; a pure-bulk strategy always runs
its configured bulk workflow.

The bulk primary and iterative refinement are one `explore` phase. The bulk
primary leaves that phase running when the composite owns the next
transition. The composite completes it only when the bulk result is already a
chunk boundary or covers the whole report; otherwise the iterative workflow
completes or leaves pending the same phase (§FS-forge-run-continuation.1).
At the natural end of iterative refinement, any positive covered-call gain from
the gated bulk phase counts as acceptable progress even when bulk did not fully
complete a class and iterative work added no further coverage. That accumulated
gain does not mask an iterative report, test, or native-gate failure, which ends
the phase before the natural completion decision.

Parameters: the primary workflow's parameters plus the iterative exploration
parameters, from one bundle. Families: composite coverage strategies, Java-fix
composite strategies.

## AR-dynamic-access-fallback-and-failure: Fallback and failure

Fallback is deliberately narrow. An exploration workflow may delegate to
`basic_iterative` only when the report cannot provide guidance *at the start* of
a run: the report task fails, the file is missing or unparsable, reporting is
disabled, or the report names zero dynamic-access calls. Once a phase has begun
from a usable report, losing that report is a failure, not a fallback — the run
has already committed to measuring itself against something that no longer
exists.

The four fallback causes are distinct and are reported as such. A report task
that fails, a missing file, and a report naming zero dynamic-access calls are
different facts about the run, and only the last one is a statement about the
library. The harness never writes a zero-call report over a dynamic-access input
it could not produce (§root/AR-test-harness.8), so an exploration workflow may
treat a zero-call report as the library's own shape and must report an
unavailable report as unavailable rather than as a library without dynamic
access.

For bulk exploration, a successful fallback is a bootstrap rather than
an exit: the workflow refreshes the report and, when it is now usable, continues
through bulk iterations before the composite's iterative refinement. The
fallback's prompts, gate, and iterations remain part of `explore`; it never
claims or completes `fix`.

Falling back does not skip native-image validation. `basic_iterative` runs the
same terminal gate, so a fallback run still reaches native metadata tracing for
metadata the report could never have named (§FS-native-test-verification-gate).

An exploration workflow fails when the terminal gate returns `FAILED`, when the
report becomes unreadable after the phase started, when the iterative phase
makes no acceptable progress and keeping unjustified tests was not requested,
when final metadata generation or local verification fails, or when a chunk
boundary is reached but the chunk cannot pass the verification a reviewable
chunk PR requires (§FS-local-ci-equivalent-verification).

On failure the workflow resets to its last coherent checkpoint — the latest
committed class for iterative exploration, the scaffold checkpoint for bulk —
and a composite preserves and returns its primary workflow's failure rather than
replacing it with its own.

## AR-dynamic-access-exhaust-report: Exhaust report

The exhaust report is the durable, coordinate-scoped record of which classes a
coordinate has already processed, and the state a chunked run resumes from
(§FS-forge-chunked-dynamic-access). It is intentionally minimal: the coordinate
and issue, the class threshold and current chunk count, the class names recorded
as completed, skipped, exhausted, or failed, and the latest chunk's publication
identity.

It deliberately does not store a chunk manifest. Every resumed run regenerates
the dynamic-access report and filters out the classes already recorded, so a
resume reflects the library's current surface rather than a plan made against a
stale one. Its location is derived from the coordinate and it is stored with the
library's test suite, so each merged chunk carries the state the next run needs
without any resume argument being passed.

Repository-wide stats stay authoritative: coverage is reported against the full
current dynamic-access surface, and a chunk may additionally report how many
classes it processed and how many remain.

## AR-native-test-verification-callers: Callers

Every caller reaches the gate through **one** invocation point on the workflow
base class, so the budget, logging, and `FAILED` handling are identical wherever
it runs. An engine chooses only two things: when to invoke it, and which output
directory to scope it to. The budget is the strategy parameter
`max-native-test-verification-iterations` (§FS-predefined-strategy-parameter-families).

| Engine | When it invokes the gate | Output-dir scope |
| --- | --- | --- |
| `dynamic_access_iterative` (§AR-dynamic-access-iterative) | after classes with a coverage gain — resolved or partial — flushed in batches of `native-test-verification-batch-size`, and again at phase wrap-up for any pending classes | one directory per class |
| `bulk_dynamic_access` (§AR-dynamic-access-bulk) | after **every** accepted bulk iteration, once the attempt is committed and checkpointed — not once per run | one directory per coordinate |
| `javac_iterative` (§AR-java-fail-fix-workflow) | once, after the compilation repair succeeds | one directory per coordinate |
| `java_run_iterative` (§AR-java-fail-fix-workflow) | once, after the agent's final edit, when the JVM fix succeeded | one directory per coordinate |
| `basic_iterative` (§AR-basic-iterative) | once, after the loop commits at least one test suite | one directory per coordinate |
| `increase_dynamic_access_coverage` (§AR-dynamic-access-composite) | never directly — its primary workflow and its exploration phase each gate their own result | inherited |

The composite is the one engine that adds no gate of its own, and that is not an
exemption: every path through it is already gated by the phase that produced the
work. Adding another would re-verify an unchanged tree.

So exploration gates repeatedly and a fix gates once. That asymmetry is
intentional: exploration adds metadata continuously and each addition can break
the native image, whereas a fix produces one final state worth verifying once.

`FAILED` is handled the same way by all of them — the calling workflow propagates
a failure status and resets its feature branch to its checkpoint
(§AR-dynamic-access-fallback-and-failure). No caller retries the gate, downgrades
its result, or treats a partial recovery as success.

## AR-chunked-dynamic-access-pr-linking: Chunk PR linking

A chunk must link its pull request to the issue without completing it, because
the issue is only done when the last chunk lands (§FS-forge-chunked-dynamic-access). Non-final chunk PRs reference
the issue; only the final chunk PR closes it and moves it to done. Every chunk PR
carries the merged exhaust state the next run needs. Iterative exploration can
attribute completed, skipped, exhausted, and failed classes and records all four
states. Bulk can attribute only classes proven completed by its baseline/final
report comparison and records only those; a composite carries the union of the
states its two phases can attribute.

Each chunk also records its publication identity and unique head branch in the
exhaust report before the verified push, which is how a later chunk resolves the
preceding PR without committing a GitHub-assigned number back to the branch. The
publication mechanism is §AR-chunked-linking.
