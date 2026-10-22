# AR-forge-workflow-pipeline: Forge workflow pipeline

One issue run is one Rhei workspace instantiated from the `forge-metadata`
template, and this declaration is the single picture of what runs between a
labeled issue and a pushed publication branch (§FS-forge-issue-resolution-goal).
Programs make every decision and agents only edit: a program's exit code is the
whole routing decision, an agent state has one outgoing edge, and no agent
report is a verdict — the program that asked for work reruns its check and
decides (§root/PRCPL-prefer-algorithmic). Every agent state starts a fresh
session. Code coverage improvement (§AR-code-coverage-improvement) is a separate
template and is not covered here.

The template lives in `forge/.agent-grounds/rhei/templates/forge-metadata/`.
Its programs are subcommands of `forge/.agent-grounds/rhei/programs/forge_run.py`,
run from the source checkout with the workspace as working directory; they sit
outside the template because Rhei renders every template file. The prompts each
agent state receives are §AR-forge-workflow-prompts.

## 1. Launcher

`forge_metadata.py` finds and claims work and starts one run; every decision
about the issue is made by `prepare` (§FS-forge-run-requirements). One cycle:

1. **Fetch.** One `gh issue list` per label and priority tier, every cycle, with
   no cache: `high-priority`, then `priority`, then the rest, oldest first,
   unassigned, excluding `not-for-native-image`. The labels are
   `library-new-request`, `library-update-request`, `fails-javac-compile`,
   `fails-java-run`, and `fails-native-image-run` (§FS-forge-scope). A fixture
   file can stand in for GitHub.
2. **Claim.** The live claimability check and the optimistic claim are the
   dispatcher's (§FS-forge-run-requirements.2): assign, wait, verify, move the
   board item to `In Progress`, release on any failure.
3. **Issue form.** The five form rules decide against the pinned base's
   `index.json`; a malformed issue gets the predefined comment and is closed
   (§FS-forge-run-requirements.3).
4. **Run.** `rhei instantiate forge-metadata` with the issue number and the two
   agent targets (§FS-forge-agent-runtime-selection), then `rhei run` with the
   dashboard. A workspace that cannot be created releases the claim.
5. **Close out.** The final state decides the issue's bookkeeping:
   `completed` leaves it `In Progress` for the pull request to close;
   `human-intervention` and a bad input label it `human-intervention` with the
   reason and return it to `Todo` (§FS-human-intervention-policy); an
   infrastructure failure only returns it to `Todo`.

`run-forge.sh` loops cycles until stopped, checks the host before each claim
(§FS-forge-host-requirements), and every third run, and whenever nothing can be
claimed, reviews up to three open Forge pull requests with the worker agent and
the label's `skills/review-*` rules (§FS-automated-pr-review).

## 2. Exit codes

| Exit | Meaning |
| --- | --- |
| `0` | proceed |
| `10` | work for an agent: a cover prompt or a fix request is written |
| `11` | `measure` only: the JVM tests fail and a fix request is written |
| `12` | the check still fails right after its own fix |
| `20` | nothing to do: no target, exploration deferred, explore budget spent, or nothing to publish |
| `2` / `3` | bad input / infrastructure or crash |
| `30`–`33` | `fix-route` only: return to `repair`, `measure`, `native`, `finalize` |
| `40` | `fix-route` only: the fix reported that it could not help |

Every program also routes any other non-zero exit to `failed`, and every exit
writes Rhei's result file, which an edge into a final state requires.

## 3. State machine

Eight programs, three agent states, four final states:

```mermaid
stateDiagram-v2
    state "setup (A)" as setup
    state "cover (A)" as cover
    state "fix (A)" as fix
    state "fix-route" as route
    state "nothing-to-publish" as nothing
    state "human-intervention" as human

    [*] --> prepare
    prepare --> setup: 0
    prepare --> failed: 2 or 3
    setup --> repair
    repair --> analyze: 0, 20 new library
    repair --> fix: 10
    analyze --> cover: 10
    analyze --> native: 20
    cover --> measure
    measure --> cover: 10
    measure --> fix: 11
    measure --> native: 0, 20
    native --> finalize: 0
    native --> fix: 10
    finalize --> publish: 0
    finalize --> fix: 10
    fix --> route
    route --> repair: 30
    route --> measure: 31
    route --> native: 32
    route --> finalize: 33
    route --> human: 40
    repair --> human: 12
    measure --> human: 12
    native --> human: 12
    finalize --> human: 12
    publish --> completed: 0
    publish --> nothing: 20
    completed --> [*]
    nothing --> [*]
    human --> [*]
    failed --> [*]
```

A cover pass costs two transitions and a fix three, so the default explore
budget of 20 passes stays inside the host's transition limit of 80.

## 4. Programs

| State | Does |
| --- | --- |
| `prepare` | Reads the issue; derives the route from its label and the coordinate from its title; fetches and pins `origin/master`; spawns the worktree on `rhei/forge-<artifact>-<version>` and attaches it to the task; scaffolds a new library, or adds the version beside its nearest supported one (§FS-library-update-tested-version-split); resolves the chunk state (§FS-forge-chunked-dynamic-access); stores the reporter's issue body as untrusted evidence; checkpoints; writes `run.md` |
| `repair` | Runs the setup tail once (§AR-forge-workflow-pipeline.4.1); then a new library exits `20`, and every other route runs the version check, `javaTest`, and the native check (§AR-forge-workflow-pipeline.5); a failure is one "make this version work" fix request |
| `analyze` | Once: builds the native test image and lists the dynamic-access call sites, runs the JVM tests with JaCoCo and correlates, picks the explore mode (§AR-forge-workflow-pipeline.7), and writes the first cover prompt |
| `measure` | Reruns the JVM tests with JaCoCo and correlates against the stored call sites, with no native build; keeps a pass that gained and resets one that did not; advances the mode and writes the next cover prompt |
| `native` | The native check over the tests exploration added |
| `finalize` | `spotlessApply`, then `spotlessCheck`, `checkstyle`, `generateLibraryStats`, and the pre-publication gate (§FS-local-ci-equivalent-verification.2), each once; the first failure becomes the fix request |
| `fix-route` | Reads the request's return code and the fix report: `cannot-fix` exits `40`, anything else the return code |
| `publish` | Commits only the coordinate's paths, writes the run metrics (§FS-forge-run-metrics) and the descriptor (§AR-publication-descriptor), rebases onto `master`, and pushes the publication branch; a deferred exploration opens its follow-up issue first |

### 4.1 The setup tail

`setup` may change only the coordinate entry's four URL fields in `index.json`,
the test project's `build.gradle`, and its `required-docker-images.txt`. The
first `repair` reverts every other edit, verifies the four URLs, downloads the
main sources into the run's source context, writes the user-code filter and
`allowed-packages` from the library JAR when the project has no filter yet, and
checkpoints. A missing URL, or a declaration that does not resolve, is a bad
input.

### 4.2 Call sites are listed once

The dynamic-access call sites depend only on the library JARs, which the
tracking build preserves whole, so `analyze` lists them once and keeps a copy
in the workspace; every later correlation passes that copy to
`generateDynamicAccessCoverageReport` and runs only JaCoCo
(§root/AR-test-harness.8).

## 5. The native check

`generateMetadata` runs the JVM tests under the native-image agent and merges
what the filter allows into the shipped metadata; `checkMetadataFiles` and
`nativeTest` then run once each. The program judges only pass or fail and never
merges, filters, or edits metadata itself. Whatever still fails goes to `fix` as
one request with every failing output, where an exact missing registration or a
listed native flag is a legal repair (§root/FS-test-contract.2.7). `repair`
runs the same check after `javaTest`; `native` runs it after exploration.

## 6. The loop guard

A sender records in the run state that it sent a request. When its check fails
again on its very next run, it exits `12` instead of asking again; a later,
unrelated failure is a new request. `measure` has one exception: it resets to
the last passing explore checkpoint and continues, so a cover pass that cannot
be fixed costs that pass, not the run. Nothing else bounds `fix`: one turn is
bounded by its agent timeout and the run by Rhei's transition limit.

## 7. The explore decision

`analyze` decides once whether and how to explore; `uncovered` counts the
classes with uncovered dynamic-access calls, less those an earlier chunk
processed.

| Condition | Mode | Exit |
| --- | --- | --- |
| no dynamic-access calls, new library | basic: tests of the main public API | `10` |
| no dynamic-access calls, or `uncovered` is 0, or the previous version's coverage is already met | skip, unless the reporter requested metadata, which gets one basic pass | `20` |
| `fails-*` label and `uncovered` above `defer_classes` | defer: `publish` opens the follow-up library-update issue | `20` |
| new library or update and `uncovered` above `defer_classes` | chunk: label the issue, keep an exhaust report, and spend at most `defer_classes` classes this run | as below |
| `uncovered` at least `bulk_min_classes` | bulk, then class | `10` |
| `uncovered` below `bulk_min_classes` | class only | `10` |

`measure` owns the mode and its counters. A bulk pass covers the whole report
and repeats while it gains, up to `bulk_passes`; then class mode takes one class
at a time until it is covered or has spent `class_attempts` passes, when it is
exhausted. Exploration ends when the goal is met — no actionable class left for
a new library, the previous version's covered calls for every other label — or
when `explore_passes` passes are spent. A pass that gained nothing is reset, and
the next prompt for that class names the tests that were reset.

## 8. Agent states

| State | Target | May change | Report |
| --- | --- | --- | --- |
| `setup` | `setup_agent` | the four URL fields, `build.gradle` dependencies, `required-docker-images.txt` | `{"urls", "libraries", "dockerImages"}` |
| `cover` | `worker_agent` | tests under the test project, its `build.gradle` and image list | `{"targets": [...]}` |
| `fix` | `worker_agent` | only the files the request lists | `{"result": "fixed" \| "cannot-fix", "changed", "summary"}` |

None has a visit budget: `measure` counts cover passes and the loop guard
bounds `fix`.

## 9. Steps Forge no longer has

The strategy registry, per-label drivers, the library-update router, the
continuation marker, the native trace gate and program-side metadata merging,
per-class native runs, the pre-push local review, the library preparation
preflight with its not-for-native-image and unsupportable-version verdicts,
foreign-owner follow-up issues, and every per-site repair budget are gone: strategies are fixed per label, Rhei resumes a run at its
state and the run branch holds the checkpoints, and each former repair site is
one fix request. The issue-form rules, the claim, bulk-then-class exploration,
the dynamic-access report as the only oracle, the pre-publication gate, and the
trusted publisher are kept.
