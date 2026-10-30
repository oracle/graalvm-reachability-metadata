# AR-forge-launcher: Forge launcher and exit codes

The launcher loop that claims an issue and starts one run of the issue
workflow, and the exit codes by which that run's programs route it
(§FS-forge-issue-resolution-goal). The run itself, state by state, is
§AR-forge-workflow-pipeline.

## 1. Launcher

`forge_metadata.py` finds and claims work and starts one run; every decision
about the issue is made by `prepare` (§FS-forge-run-requirements). One cycle:

1. **Fetch.** One `gh issue list` per label and priority tier, every cycle, with
   no cache: `high-priority`, then `priority`, then the rest, oldest first,
   unassigned, excluding `not-for-native-image`, `graalvm-bug`
   (§FS-forge-run-requirements.2.1) and `human-intervention` and, unless the
   operator takes blocked issues, every issue with an open blocker
   (`is:blocked`). Each query returns a fixed number of issues, so an issue the
   claim refuses (§FS-forge-run-requirements.2), held or blocked, is left out of
   it: the oldest issues of a queue are the ones most often held or waiting on
   another, and they would fill every query and starve the queue. Each tier
   excludes the labels of the tiers above it, so an issue carrying both priority
   labels is served once, in its highest tier, as the repository status
   classifies it (§root/FS-repository-status-report.1). The labels are
   `library-new-request`, `library-update-request`, `fails-javac-compile`,
   `fails-java-run`, `fails-native-image-build`, and `fails-native-image-run`
   (§FS-forge-scope). A fixture file can stand in for GitHub.
2. **Claim.** The live claimability check and the optimistic claim are the
   dispatcher's (§FS-forge-run-requirements.2): assign, wait, verify, move the
   board item to `In Progress`, release on any failure. A GitHub outage there
   ends the cycle with `75` (§AR-forge-launcher.1.2).
3. **Issue form.** The five form rules decide against the pinned base's
   `index.json`; a malformed issue gets the predefined comment and is closed
   (§FS-forge-run-requirements.3).
4. **Run.** `rhei instantiate forge-metadata` with the issue number and the two
   agent targets (§FS-forge-agent-runtime-selection), then `rhei run` with the
   dashboard. A workspace that cannot be created releases the claim. Each
   attempt at an issue has its own workspace, `runs/forge-<issue>-<attempt>`:
   an attempt whose launcher died is resumed by the next cycle on its host
   (§AR-forge-launcher.1.1), the log of the state it was killed in moved to
   `runtime/forge/interrupted/` first, since no spawn record accounts for it
   and Rhei refuses to spawn over it. One that `rhei run`
   returned from is finished if Rhei ran a state of it, and a halted one gets
   the infrastructure outcome recorded, since Rhei never re-runs a state whose
   attempts are spent. A run Rhei ran no state of, once those logs are moved,
   is a no-advance halt: the first keeps the attempt's worktree for a later
   claim to resume, and the failed cycle backs the loop off; a second in a row
   ends the attempt in `human-intervention`, its comment quoting Rhei's halt
   message. The count lives in `runtime/forge/`, and any run that runs a state
   resets it. A halt the run's event log attributes to a Rhei budget
   (`budget_halt`) is a transient host condition that resets with the
   allowance, so it never counts: the attempt keeps its worktree, the count
   stays as it was, the issue returns to `Todo`, and the launcher exits with
   the transient status `75`, so the loop backs off. A finished attempt gives
   up its worktree and stays as the record, because Rhei keys a workspace's
   budget ledger by its path and refuses a new workspace on a used one; one
   labeled `human-intervention` first keeps its work at its own ref
   (§FS-human-intervention-policy).
5. **Close out.** The final state decides the issue's bookkeeping:
   `completed` leaves it `In Progress` for the pull request to close;
   `human-intervention` and a bad input label it `human-intervention` with the
   reason and return it to `Todo` (§FS-human-intervention-policy); an
   infrastructure failure only returns it to `Todo`; an issue that closed after
   the claim only loses the Forge assignee (§FS-forge-run-requirements.2). A
   `human-intervention` comment also quotes what the run recorded in its
   outcome: the state that stopped, its failing commands with a bounded excerpt
   of their output, and the last fix report. Each excerpt sits in a fence no
   backtick run inside it can close, and the report's summary, agent text, is
   quoted trimmed to 1500 characters, with `§` escaped as `<§>` and every `@`
   kept from mentioning. A bad input's comment also names the step's reason and
   quotes the output it was read from (§FS-forge-run-requirements.2).
   Bookkeeping GitHub refuses does not stop the launcher
   (§FS-forge-run-requirements). A `human-intervention` label that cannot be
   added keeps the claim, which holds the issue out of the queue as the label
   would: released unlabeled, it would be claimed again and fail the same way.
   A comment that cannot be posted is logged, and the labeled issue is still
   released.

`run-forge.sh` loops cycles until stopped, checks the host before each claim
(§FS-forge-host-requirements), and reviews the open Forge pull requests with the
`review_agent` after every run, once the gate first passes, before the first
claim, and whenever nothing can be claimed (§FS-published-pr-review.1,
§AR-forge-dispatcher-decomposition.1). Once the gate has passed, a transient
gate failure or a failed cycle makes the loop back off and wait for the gate to
pass again (§FS-forge-host-requirements.1).

Without `--label`, every cycle fetches every queue in the claim order above.
Each `--label LABEL[:COUNT]` instead gives its queue COUNT turns in a row, one
by default, in a rotation that follows the order the labels are given, so a
long queue cannot starve the others: `--label library-new-request:2 --label
fails-java-run` serves the new-library queue twice, then `fails-java-run` once,
and starts over. A label given twice, or a COUNT that is not a positive integer,
is an argument error. Each run takes the next turn and fetches only that turn's
queue. A queue with nothing to claim passes the turn, and the rest of its turns
in this rotation, to the next queue in the same loop iteration, so nothing can
be claimed only when no queue in the rotation can; the idle review and wait
follow, and the rotation resumes at the turn it stood at.

### 1.1 Stranded claims

A cycle resumes its host's stranded claim before it claims anything new
(§FS-forge-stranded-claims). A launcher marks the issue it works on with an
exclusive `flock` on `runs/locks/forge-<issue>.lock`, taken without waiting
before the claim and held until the close-out returns. The file names the
holder's pid for a reader, but only the lock decides. The kernel drops a
`flock` with its holder's last descriptor, so `SIGKILL` releases it, and the
descriptor is not inherited, so neither `rhei run` nor a Gradle daemon an agent
leaves behind can hold it. The lock belongs to the issue rather than to one
attempt because a new attempt's workspace exists only once `rhei instantiate`
has created it, after the claim, and only an issue's latest attempt can be
live. A `rhei run` holds an exclusive `flock` on its workspace's
`.rhei/run.lock` while it lives, so a shared lock that cannot be taken there
marks a run that outlived its launcher, or one a launcher started before this
lock existed. The pre-publication gate reads the same locks, its own issue's
aside, before and after each test run, to tell whether another run could have
pulled a Docker image it finds (§FS-local-ci-equivalent-verification.2.2).

After the fetch, the launcher lists the issues whose latest attempt has no
`runtime/forge/outcome.json`, oldest issue first, from the runs directory
alone. For each, it takes the issue's lock, reads the latest attempt again
under it, and skips an attempt a `rhei run` holds. Only then does it read the
issue and its project item, two GitHub reads; a read GitHub refuses skips the
issue for this cycle instead of stopping the launcher. The first stranded claim
found is run, its lock held, through the same resume and close-out as a claimed
issue, so the cycle exits with the same statuses.

### 1.2 Board writes and pending releases

The claim's and the release's GitHub calls are the project status edit
(`gh project item-edit` and the field lookup it needs), the assignee `PATCH`,
and the reads that verify them. They go through the shared retrying runners
with rate limits retried too: up to five attempts, two seconds apart and
doubling, on a server error, an endpoint that does not connect or answer, or a
rate limit (§FS-forge-stranded-claims.4.1). GraphQL's `Something went wrong
while executing your query` is a server error. Every other GitHub call still
raises on a rate limit at once, so a run stops for the reset. A release
repeats its round, a minute apart, only while a call fails transiently.

A release that still fails transiently writes `runs/releases/forge-<issue>.json`
with the project item, the reason and the time; one GitHub confirms or refuses
removes it (§FS-forge-stranded-claims.4.2). Before the stranded-claim scan a
cycle lists these records, oldest issue first, and for each takes the issue's
lock, skipping an issue a live launcher holds, reads the record again under it,
since another launcher on the host may have retried it, then reads the issue
over REST.
A record whose issue is closed, assigned to anyone but the user, or updated
after the record was written is removed, and the issue named for a
maintainer. Any other is released again through the same release. A release
GitHub still refuses, or an issue it cannot read, ends the cycle with `75`
before the scan, so the loop backs off (§FS-forge-stranded-claims.4.3). A claim
whose GitHub calls still fail is released the same way, and the cycle ends
with `75` too (§FS-forge-stranded-claims.4.4).

## 2. Exit codes

| Exit | Meaning |
| --- | --- |
| `0` | proceed |
| `10` | work for an agent: a cover prompt or a fix request is written |
| `11` | `measure` only: the JVM tests fail and a fix request is written |
| `12` | the check still fails right after its own fix, after `native`'s or `finalize`'s last fix, or after the audit's edit; for `analyze`, its first coverage report fails (§AR-forge-workflow-pipeline.4.5) |
| `20` | nothing to do: a new library has no version to repair, no target, exploration deferred, explore budget spent, or nothing to publish |
| `21` | `setup-check` only: the setup verdict is recorded as a skip; go straight to `publish` |
| `22` | `prepare` only: the artifact is recorded as not for Native Image; go straight to `publish` |
| `23` | `prepare` only: the issue closed after the claim; the run ends in `failed` with no issue action (§FS-forge-run-requirements.2) |
| `2` / `3` | bad input / infrastructure or crash |
| `30`–`35` | `fix-route` only: return to `jvm`, `measure`, `native`, `finalize`, `audit`, `native-repair` |
| `40` | `fix-route` only: the fix reported that it could not help, except for `native` |

Every program also routes any other non-zero exit to `failed`, and every exit
writes Rhei's result file, which an edge into a final state requires; a crash,
a `SystemExit` from a shared helper included, is an infrastructure exit.
