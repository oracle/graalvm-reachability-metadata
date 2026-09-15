# AR-code-coverage-benchmarking: Code coverage benchmark architecture

The code coverage benchmark is a control plane around the existing Rhei code
coverage improvement workflow. It does not implement another coverage engine.
The runner owns repeatable inputs, matrix expansion, worktree isolation,
portable metrics, and publication; Rhei owns preparation, API coverage,
native-metadata preparation, deep coverage, and finalization. This split
implements §FS-code-coverage-benchmarking while preserving
§AR-code-coverage-improvement.

## 1. Components and identity

| Component | Responsibility |
| --- | --- |
| `run-code-coverage-benchmark.sh` | Stable operator entry point for execution and pending-publication retry. |
| `code_coverage_suite.json` | Fixed suite commit, libraries, agent/model/provider tuples, and thinking levels. |
| `code_coverage_benchmark.py` | Matrix validation, worktrees, Rhei launch, conversion, metrics, publication, and retry. |
| Code coverage Rhei template | Switches conversion and publication behavior through the `benchmark` input. |
| Source worktree | Disposable checkout of the fixed `benchmarkSuiteCommit`. |
| Rhei workspace | Permanent local record keyed by `runId` and named `code-coverage-99000`. |
| Publication worktree | Fresh checkout of `origin/master` used to append one result and push its result-only PR branch, then removed. |
| Trusted Actions publisher | Validates the exact result and descriptor from default-branch code and opens the benchmark-result PR. |

Two commits describe different axes:

| Identity | Meaning | Update rule |
| --- | --- | --- |
| `benchmarkSuiteCommit` | Repository and library state being measured. | Fixed in the checked-in suite and changed only intentionally. |
| `runnerCommit` | Implementation that orchestrated and measured the run. | Resolved from the clean runner checkout at launch. |

Merged coverage improvements do not move an existing benchmark input. A newer
runner may still execute the old suite and records both identities.
§FS-code-coverage-benchmarking.1

The two identities map onto two directories, and the conversion record keeps
them apart. `worktreePath` is the pinned source worktree — the library state
under measurement — while `workPath` is the Forge tree the measurement helpers
are imported and executed from. In the issue-driven flow both live in the same
worktree, because it branches from `master` and its helpers are already current.
A benchmark cell is the case where they diverge, so `workPath` is the runner's
Forge path there and the pin is left holding only its input.
§FS-code-coverage-benchmarking.1

## 2. Configuration and matrix

A benchmark campaign is a matrix. Each row — a cell — pairs one suite library
with one agent/model configuration and one thinking level, and by default the
cross-product is 75 cells (§FS-code-coverage-benchmarking.2). Every cell runs
the complete workflow once and yields one preserved workspace and one
published record, so a row of the results table is directly a row of the
matrix. The settings a cell carries:

- **Library** — one of the five fixed coordinates, chosen to spread starting
  method coverage from 2.5% to 35% (§FS-code-coverage-benchmarking.1), so a
  strategy cannot look good only where coverage is cheap.
- **Agent and model** — `pi` driving `gpt-5.6-sol`, `gpt-5.6-luna`, or
  `gpt-5.6-terra` through the `openai-codex` provider, or `claude-code`
  driving `sonnet-5` or `opus-5`. The configured name is stable for
  comparison; the concrete target model is recorded separately.
- **Thinking level** — `medium`, `high`, or `xhigh`, carried in the Rhei
  target's mode bracket and mapped to the agent CLI's thinking flag by the
  bundled agent profile, never in the model string.
- **Strategy arm** — guided (§AR-code-coverage-improvement.5.2) or naive
  baseline (§3): which template mode the
  cell instantiates. The arm changes treatment only; every other setting keeps
  its meaning across arms.

The suite JSON is the single checked-in place to change the fixed commit,
libraries, checked-in method totals, agent/model/provider mappings, or thinking
levels. The CLI filters configured tuples rather than creating arbitrary
cross-products: `--model gpt-5.6-sol` selects Pi, while explicitly pairing
that model with Claude Code is rejected before mutation.

A configuration's `provider` reaches the Rhei target only for a provider-aware
backend. Rhei passes everything after the target's first colon to the agent
CLI's model flag, so `pi` renders `pi[<thinking>]:<provider>/<model>` while
Claude Code renders `claude-code[<thinking>]:<model>`: the Claude CLI
authenticates its own provider and rejects a prefixed model name outright,
which would otherwise fail every agent state of a run in seconds rather than
failing the cell. The recorded `provider` stays descriptive either way.

Advanced metrics can extend the schema and collector without changing the Rhei
task graph. Runtime archive storage can add a portable archive reference while
the workspace remains the complete local record. Future concurrency belongs in
the runner but must retain unique run parents and serialized publication.

## 3. Baseline arm

The naive baseline is a benchmark-only strategy arm: it never runs from a
GitHub issue, and it exists so every guided signal is measured as a delta over
what an unguided agent achieves on the same fixed inputs
(§FS-code-coverage-benchmarking.7). The primary benchmarked workflow is
the full guided pipeline — its complete logic is the execution sequence at
§AR-code-coverage-improvement.5.2 — and this section describes the control
arm measured against it. The claim under test is the structured
workflow, not the models: a model run in the baseline arm and the same model
run in a guided cell differ only in the guidance they receive, so the pair
isolates what the structured workflow adds on top of raw model capability.
It instantiates the same Rhei template as the guided workflow with the naive
mode enabled: benchmark conversion and preparation run unchanged, the
measurement loop keeps its JaCoCo run and marginal-yield stop decision but
skips the API inventory, call-graph extraction, and unlock ranking — the
frozen JaCoCo method universe is the denominator, so no target list is ever
built — and the cover prompt is rendered from
`prompt_templates/code_coverage/naive-baseline-iteration.md` instead of the
ranked target list. The loop is its own state set — `naive-measure`,
`naive-cover`, `naive-fix` — rather than a mode of the API loop: the arm's
point is covering the whole library with far less information, so its
measurement is only the JaCoCo run, the stop decision, and the naive prompt
render, and it never inherits the API loop's extraction steps or their
failure modes. Native metadata
preparation, the deep phase, and finalization are not instantiated; the loop's last report is the final metric
and publication reads it directly (§FS-code-coverage-benchmarking.3).

The agent's only interface is the rendered prompt file: JaCoCo feeds
measurement, never the prompt, so the denominator stays arm-invariant while
the guidance is withheld. Only re-measurement moves the loop; the
budget is 30 passes with the methods-based early stop the guided phases use
(§AR-code-coverage-improvement.4.3).

One baseline cell, in the same shape as the guided execution sequence
(§AR-code-coverage-improvement.5.2): each shaded rectangle is one task of the
instantiated plan, and the loop hands control between deterministic
measurement and an agent pass.

```mermaid
%%{init: {"themeVariables": {"noteBkgColor": "#eef2f7", "noteTextColor": "#0f172a", "noteBorderColor": "#94a3b8", "signalTextColor": "#0f172a", "signalColor": "#334155", "actorTextColor": "#0f172a", "actorBkg": "#e2e8f0", "actorBorder": "#64748b", "labelTextColor": "#0f172a", "labelBoxBkgColor": "#e2e8f0", "labelBoxBorderColor": "#64748b", "loopTextColor": "#0f172a", "sequenceNumberColor": "#ffffff"}}}%%
sequenceDiagram
    autonumber
    participant R as Rhei orchestrator
    participant P as deterministic state programs
    participant A as worker agent
    participant W as source worktree

    rect rgb(147, 197, 253)
        note over R,P: Task benchmark-convert
        R->>P: validate the fixed benchmark cell
        P-->>R: conversion record and run.json written, exit 0
    end

    rect rgb(134, 239, 172)
        note over R,W: Task prepare
        R->>A: execute with the helper the task names
        A->>W: create or verify the coverage suite
        A-->>R: artifacts written, completed
    end

    rect rgb(253, 186, 116)
        note over R,W: Task naive coverage loop
        loop measure then cover, budget 30 passes
            R->>P: naive-measure
            P->>W: JVM JaCoCo run over the coverage suite
            P->>P: record the stop decision
            alt marginal yield or budget spent
                P-->>R: exit 0, phase completed
            else uncovered methods remain and budget is left
                P->>P: render the naive prompt, no ranking
                P-->>R: exit 10, schedule naive-cover
                R->>A: naive-cover with the naive prompt, fresh session
                A->>W: write behavior tests in the coverage suite
                A-->>R: turn ends, back to naive-measure
            else the suite is red or a step failed
                P-->>R: failing exit, schedule naive-fix
                R->>A: naive-fix repairs the suite
                A-->>R: back to naive-measure
            end
        end
    end

    rect rgb(249, 168, 212)
        note over R,P: Task benchmark-publication
        R->>P: read the last report, publish result.json
        P-->>R: published record, exit 0
    end
```

## 4. Data locations

Local evidence and portable comparison data have different homes:

```text
forge/local_repositories/code_coverage_benchmarks/
  <runId>/
    source/                       # removed only after publication
    code-coverage-99000/          # always retained
      index.rhei.md
      states.yaml
      tasks/
      runtime/
        accounting/
        code-coverage/
          benchmark/
            run.json              # local identity and absolute paths
            result.json           # normalized portable result
            publication.json      # written after push
          finalization/
          validation/
          discovery/
          prompts/
          fixes/
          work/
        logs/
```

Only portable result lists enter the publication branch:

```text
code-coverage-benchmarks/
  <group>/
    <artifact>/
      <version>.json
```

The branch carries no publication descriptor: the appended result is the
complete record, and the trusted publisher validates and renders the pull
request from the branch diff against the merge base with the default branch
(§FS-code-coverage-benchmarking.3).

`run.json` may contain machine-specific paths because it drives recovery.
`result.json` contains no absolute paths; `runId` and
`workspaceName` are its local lookup key. Logs, prompts, reports, PGO
profiles, snapshots, and work notes remain in the workspace and are not staged.
§FS-code-coverage-benchmarking.5

## 5. Metrics derivation

Collection joins existing deterministic evidence rather than asking an agent to
summarize itself. §FS-code-coverage-benchmarking.4

| Result field | Source |
| --- | --- |
| Identity, commits, configured agent/model/thinking | `runtime/code-coverage/benchmark/run.json` |
| Coverage before/after, gains, and `allMethods` | Final `runCoverage`; partial runs use recorded JaCoCo checkpoints. |
| API/deep `coverPasses` | Phase stop-decision records. |
| API/deep `fixInvocations` | Rhei `api-fix` and `deep-fix` invocation records. |
| Input, cached-input-read, and output tokens | Rhei cover/fix invocation accounting for that phase. |
| Observed model | Rhei invocation accounting rather than the configured alias. |
| Failure phase | Last recorded Rhei task transition. |

API spans `runStart` to `afterApiPhase`, deep spans
`afterApiPhase` to `final`, and total spans `runStart` to
`final`. All use the whole-run denominator from
§AR-code-coverage-improvement.5.1.

Conversion, preparation, finalization, and publication tokens are excluded from
the initial metric. Missing partial evidence is `null`, never zero.
`checkedInAllMethods` keeps the suite snapshot and
`measuredAllMethodsDifference` exposes a changed measured universe.

### 5.1 Final-metrics contract ownership

The runner owns the final coverage metrics contract, and owns it by
construction rather than by convention. `utility_scripts/code_coverage_finalize.py`
writes `schemaVersion`, `schemas/code_coverage_final_metrics_schema.json`
constrains it, and `code_coverage_benchmark.py` reads the record during
publication. All three resolve from the runner: the first two through `workPath`,
the third because publication always ran from the runner.

Resolving the writer and its validator from the pinned worktree is what made a
schema change landing between the pin and the run fatal. The two pinned ends
agreed with each other, so finalization validated its own output honestly and
every in-run gate passed; only the reader was current, and the disagreement
surfaced at the terminal publication state with the whole execution already paid
for. Nothing forbade that split, because nothing said which commit owned the
record.

Comparing the pinned and runner schema versions at launch would not be a
safeguard against this. The suite commit deliberately does not advance
(§FS-code-coverage-benchmarking.1), so a pin older than the current schema is
the normal state, and refusing on that difference would refuse every valid
campaign. One resolution source, not an equality check, is what keeps the
contract whole. §FS-code-coverage-benchmarking.1

## 6. Publication boundary

Publication never edits the runner or source checkout. Each attempt creates a
fresh linked worktree from the fetched `origin/master` beside the run workspace
and removes it after that attempt. No publishing checkout is reused or reset.

Publication is serialized by a lock in the repository common Git directory.
Under that lock, every attempt:

1. Fetches `origin/master`.
2. Creates a disposable worktree at `origin/master`.
3. Upserts the preserved `result.json` object by `runId`.
4. Sorts by timestamp and `runId`.
5. Validates the complete coordinate list.
6. Commits only that coordinate JSON path.
7. Writes and validates a descriptor containing the exact result and path.
8. Commits the descriptor as the tip commit.
9. Pushes the unique `ai/**` publication branch.
10. Removes the publication worktree.

Trusted Actions then validates that the branch is exactly the base list plus the
descriptor's result and opens the pull request; feature-branch code never gets
publication credentials. A retry reuses an identical remote branch or accepts
an identical result already merged into `origin/master`; a conflicting entry is
rejected. The publication marker is last, so its presence means the portable
result is durable on a publication branch or already merged and source cleanup
may begin. Publication-worktree cleanup failure is operational state and must
not reclassify a successful result. §FS-code-coverage-benchmarking.3
