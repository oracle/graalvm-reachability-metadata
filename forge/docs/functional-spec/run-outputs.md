# FS-forge-outputs: Run outputs

- **Per-run metrics record** persisted to
  `stats/<group>/<artifact>/<version>/execution-metrics.json`, as required by
  §FS-forge-run-metrics.
- **Durable session and generation logs** for every agent session,
  generation attempt, deterministic Gradle command, metadata fixup, native
  trace cycle, and local verification gate that contributes to the run, as
  required by §FS-durable-generation-logs.
- **Generated tests + metadata + index.json** committed on a per-run feature
  branch in the reachability repo, named `ai/<authenticated-login>/<suffix>`
  where `<suffix>` names the route and encodes the coordinate (e.g.
  `add-lib-support-<group>-<artifact>-<version>` for new-library support,
  `library-update-request-…`, `fixes-javac-fail-…`, `fixes-java-run-fail-…`,
  `fixes-native-image-build-fail-…`, `fixes-native-image-run-fail-…`). Publication branches are
  unique per publication ID and are pushed directly to
  `oracle/graalvm-reachability-metadata` so the repository's `push` workflow can
  observe them.
- **Pull request** created asynchronously by the trusted Forge Actions
  publisher after Branch Ready accepts the exact pushed SHA.

## FS-forge-run-output-legibility: Legible run output

The run's own output is one of the run outputs defined by §FS-forge-outputs.
Whoever is running generation watches it live, so it must answer two questions
at a glance: which concrete step the run is in right now, and — when the run
stops — what exactly failed. Clarity is the requirement, not volume: a wall of
text that has to be read backwards to locate the current step fails this section
as surely as silence does. This is the live counterpart of the durable record
required by §FS-durable-generation-logs, and it keeps the loop short as called
for by §GOAL-shorten-issue-to-shipped-metadata.

### 1. Every step locates its state

On entering a pipeline step, the normal output prints one concise line naming
the phase, the work and its operand, plus the step's derived position as
`(n/total)` (`setup/neural_setup`, `explore/native_trace_gate`,
`finalization/local_ci_check`, …). A completed step or unit adds one outcome
line under the same position instead of narrating its intermediate work. The
reader must never have to infer the current step from incidental output such as
a Gradle banner or an agent's prose.

### 2. Every failure names its location and its cause

A failing run states the phase, the step, the operand, and the concrete reason
it stopped, in that order, before any surrounding detail — the same pair
required everywhere a failure surfaces by
§FS-forge-run-location-reporting.3. A failure reported only as a
status, a stack trace, or a generic message does not satisfy this requirement.

### 3. The inside of a step stays quiet

Work within a step is reported by outcome, not by narration: one line per
completed unit (a generated class, a passed gate, a trace cycle) and nothing per
intermediate operation. Repeated or retried work says that it is a retry and
which attempt it is, so a stalled loop is visible as a loop.

### 4. Detail lives in the logs, not in the terminal

Full agent conversations, Gradle output, and native-image output are written to
the durable logs; the run output prints the path to the relevant log instead of
reproducing it, so a maintainer can escalate from the summary to the evidence in
one step (§FS-durable-generation-logs).

### 5. Verbose mode restores narration

`--verbose` adds the intermediate operations hidden by normal output, including
the registered method-style step announcement and deterministic setup details.
It never replaces the concise progress and outcome lines. A failed gate prints
the detail needed to diagnose that failure even when verbose mode is off.

Inside a `forge-metadata` run each state writes its own log, and the dashboard
names the state; a program prints one line per check it runs and its outcome,
and command output, branch commands, source downloads, and checkpoints stay in
the state's log (§AR-forge-workflow-pipeline.3). A failed command remains
visible with its cause and durable log path.

For `fix` and `explore`, normal output names the diagram step and only the unit
that advances it. Test generation identifies either the selected class and its
`current/total` class position, a bulk iteration, or an unguided generation
attempt. Beneath that state, tests report their bounded attempt, terminal task,
and whether `nativeTest` was reached; an agent retry is one indented `feedback
fix` state. The native-trace gate reports start, terminal status, and a nested
`agent_fix` state only when deterministic tracing cannot converge. Agent session
announcements, static start/completion lines, Gradle commands, report refreshes,
trace directories, metadata paths, cycle internals, and failure-log excerpts
are verbose narration. The in-place agent heartbeat remains the normal live
timer. Terminal failures still name their cause and durable log.

For `finalization`, normal output shows the terminal native-trace gate, then
the three native-test lanes with their `current/total` lane position, toolchain,
and Native Image mode. The remaining deterministic production work is one
`final repository checks` state per coordinate, followed by the finalization
outcome. An agent entered by a failed native lane, metadata check, or Checkstyle
check is reported as the separate `agent_fix` step with its attempt and target.
Metadata splitting, individual validation and style tasks, statistics and
metrics generation, schema validation, Gradle commands, paths, and timings are
verbose narration. A failed command or agent remains visible with its cause and
durable log path.

# FS-forge-run-metrics: Per-run metrics record
§GOAL-minimize-generation-cost

Every run must persist a per-library metrics record to
`stats/<group>/<artifact>/<version>/execution-metrics.json` so cost, token,
coverage, and status evidence stays attached to the library version it describes
(§GOAL-maximize-library-coverage). `publish` builds the record from the run's
own token accounting just before it writes the publication descriptor
(§AR-forge-workflow-pipeline.4).
Run metrics may identify the generated metadata artifact, but must not record a
single test-file artifact: the version-controlled test tree and pull-request
diff are the authoritative set of test files for the run.
A run Rhei resumes keeps its workspace, so its accounting already holds the
usage of the attempts before the resume.

# FS-durable-generation-logs: Durable generation and session logs

Every Forge session and generation step must be logged and saved to a stable
path because durable logs are the primary debugging surface for generated work.
This includes agent prompts and responses, session or thread identifiers,
persistent-instruction state, Gradle command output, metadata-fix output,
native check output, fix requests and fix reports, and local verification
output.

Logs must be scoped by task and coordinate where possible, and the workflow
must print or record the log path so a maintainer can inspect the exact
conversation or command that produced a generated artifact; the live run output
that points at those paths is specified by §FS-forge-run-output-legibility.
A workflow must not rely on transient terminal output as the only record of a
generation step.
If a run fails or times out, the saved logs are part of the diagnostic artifact
set that allows maintainers or a later Forge run to continue from evidence,
keeping the loop short as called for by §GOAL-shorten-issue-to-shipped-metadata.

# FS-forge-run-location-reporting: Run location in progress and failure output

A Forge run must always be able to say **where** it is: the phase it is in and
the step inside it. That location is one vocabulary used twice — to narrate
progress while the run works, and to report the location of a failure — so a
reader of a live run and a reader of a failed run name the same thing the same
way, serving §GOAL-shorten-issue-to-shipped-metadata. Inside a `forge-metadata`
run the location is the Rhei state, which the dashboard and the state's log
already name (§AR-forge-workflow-pipeline.3); the vocabulary below is the
launcher's and the code coverage workflow's.

## 1. Vocabulary

A **phase** is one banded segment of the run: `claim`, `setup`, `fix`,
`explore`, `finalization`, or `publication`. `claim` is the launcher-side
segment that runs before a run exists, and the phases are declared once, in the
run-location table (§AR-forge-run-location).

A **step** is the method that is executing, written with its parentheses —
for example `check_host_requirements()`, `claim_issue()`, `generate_tests()`,
`native_trace_gate()`, `agent_fix()`, `local_ci_check()`, `publish_branch()`. A step
is registered under the phase that runs it, and a phase's registered steps are
ordered by the order the run enters them, so a step has a position `<n>` within
a total `<total>`. A step name may belong to more than one phase when more than
one phase runs it, but the registry is the only place the binding is written: a
step the run enters must be registered, and `<total>` is derived from the
registry rather than counted by hand.

Only steps the pipeline actually enters are registered. A pipeline method that
is still folded into a larger one is registered when it becomes a step of its
own, so `<n>/<total>` never counts a step that never runs.

A step may carry an **operand**: the one value the step was working on when it
ran — the class being generated, the gate command that returned non-zero, the
coordinate being published. A location renders as `<phase>/<step>` without an
operand and `<phase>/<step>[<operand>]` with one.

## 2. Progress output

Entering a phase prints a bounded, visually distinct banner naming the phase and
the run it belongs to, so a phase transition is findable by eye in a long run
log even when runs are interleaved (§AR-forge-run-location). The
banner marks a transition: re-entering the phase the run is already in prints
nothing.

Entering a step prints exactly one line:

```
Running step <step> (<n>/<total>) of phase <phase>
```

with ` on <operand>` appended when the step has an operand. The words "Running
step" and "of phase" are fixed: this line is the anchor a reader and a log
search look for.

## 3. Failure output

A terminal failure prints one line before its error detail:

```
run failed in <phase>/<step>
```

The same pair must appear, unchanged, in every place the failure surfaces: the
worker's terminal output and run log, the run status the workflow returns, and
the human-intervention comment, which leads with it
(§FS-human-intervention-policy).

The location is reported once per run — the failure that ends a run is one
failure, and the first boundary to report it owns the line — and the innermost
step that failed is the one reported, however many intermediate handlers the
failure passes through. A failure that returns a status code instead of raising
records its location at the point it gives up, so both routes out reach the
lifecycle boundary with one location.

A user interrupt is not a run failure: it carries no location, records none, and
prints none.

No failure path may report a phase without a step. A failure raised outside
every step boundary is a defect in Forge, not a formatting gap: it is reported
with the step `<unlocated-step>` and an explicit defect notice on stderr, so it
is visible rather than silently tolerated.

## 4. Failure output stays short

Failure output is the location, the error, and the preserved-work link. The
human-intervention handoff itself is unchanged
(§FS-human-intervention-policy) — the work is still preserved on a branch, the
comment is still posted, the label is still applied — but its narration is
debugging detail, not failure output: branch switches, staging, commits, pushes,
and claim-revert bookkeeping are logged at debug level and are off unless debug
logging is enabled. Errors, the failure location, and the preserved branch URL
are never suppressed.

# FS-forge-run-status: Run status semantics

Every `forge-metadata` run ends in one final state, and the launcher acts on it
(§AR-forge-workflow-pipeline.1):

| Final state | Meaning |
| --- | --- |
| `completed` | Every check and the local CI-equivalent verification passed (§FS-local-ci-equivalent-verification) and the publication branch is pushed. A chunk that is not the last is published the same way, with `RUN_STATUS_CHUNK_READY` in its metrics; the issue must not be resumed until that chunk's PR has merged. A skip record is published the same way (§FS-unsupportable-version-diagnosis.3), and so is a not-for-native-image record (§FS-not-for-native-image-record.2). |
| `nothing-to-publish` | The coordinate's paths are unchanged, counting the exhaust record a chunk writes first (§FS-forge-chunked-dynamic-access); no branch is pushed. A chunk that is not the last and completed or exhausted a class, or a last chunk that removes an earlier chunk's report, always has something to publish. |
| `human-intervention` | A check still failed after its fix, or the fix reported that nothing within the rules helps; no branch is pushed (§FS-human-intervention-policy). |
| `failed` | A bad input, an infrastructure failure, or an issue that closed after the claim (§FS-forge-run-requirements.2); no branch is pushed. |

A run whose checks pass only after a fix request is still `completed`: the fix
request and report stay in the workspace, and the PR body carries the run's
metrics like any other.
