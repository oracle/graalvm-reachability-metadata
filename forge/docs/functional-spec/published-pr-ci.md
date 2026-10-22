# FS-published-pr-ci-failures: Failing CI on a published pull request

A published Forge pull request gets no semantic review: its tree was audited
locally before the push (§FS-local-branch-review), so after publication Forge
watches only its CI. A green head needs nothing from Forge; a maintainer merges
it. A failing head gets one diagnosis, and the diagnosis ends in a fix, a rerun,
or an issue — never in silence (§GOAL-shorten-issue-to-shipped-metadata).

The run already passed the coordinate's own native lanes
(§FS-local-ci-equivalent-verification.2.1), so the failures expected here are
the ones those lanes do not cover: the Spring AOT smoke tests, the other tested
versions that share the metadata, `master` moving under the pull request,
transient infrastructure, and flaky tests.

## 1. Cadence and eligibility

Every third run, and whenever nothing can be claimed, the launcher runs one CI
pass over the open Forge pull requests, oldest first, and diagnoses at most
three of them. Eligibility is structural. A pull request is looked at only when
its head repository is `oracle/graalvm-reachability-metadata`, its head branch
is an upstream `ai/**` branch, it carries a Forge route label
(§FS-forge-functional-spec), and no human has taken it over: one approved by a
reviewer, or labeled `human-intervention` without `human-intervention-fixed`, is
skipped.

## 2. The failing state

A program reads the state from the checks on the pull request's head commit. A
head with a check still running is skipped until the next pass, and a head
whose checks all passed is skipped for good. Only a head with a failed,
cancelled, or timed-out check and nothing still running is failing. The failing
jobs and the tail of each one's failed log are the diagnosis's evidence.

## 3. One diagnosis per head

Each failing head commit is diagnosed once. Forge records every head it handled
in a marker comment on the pull request, written by its own account, and never
diagnoses a recorded head again; a push changes the head, so the pushed fix's
CI gets its own look.

The diagnosis is one turn of the setup agent (§FS-forge-agent-runtime-selection),
because telling a contribution defect from a GraalVM change takes the web. The
agent works in a throwaway checkout of the exact head and receives the pull
request's route label and coordinate, the base and head commits, and the
evidence of §FS-published-pr-ci-failures.2. It changes nothing on GitHub: it
classifies the cause and writes its decision, and the program carries the
decision out, so every action is checked before it is recorded
(§root/PRCPL-verify-inputs).

### 3.1 Transient infrastructure

A runner failure, a network or Docker-pull failure, or a cancelled job the
change cannot have caused is rerun: the program reruns the failed jobs of the
head. A head is rerun at most once; if its rerun fails again, the transient
diagnosis was wrong, and the head goes to an issue with that diagnosis and the
new failure (§FS-published-pr-ci-failures.3.4).

### 3.2 A defect in the contribution

A failure caused by the contribution's own files — a test, the metadata, another
tested version sharing the metadata, or an `index.json` that `master` has moved
under — is repaired inside the contribution's file set
(§root/FS-contribution-contract.5.1, §root/FS-contribution-contract.5.2). The
agent fixes it in the checkout, reruns the failing lane locally, and commits;
the program pushes the commit to the head branch. An `index.json` whose tested
versions sit in the wrong metadata-version bucket after `master` moved is fixed
by merging `master` into the branch and moving or removing only those tested
versions until `validateIndexFiles` passes over every coordinate; the other
fields of the entries stay as they are. The branch is never rebased, so every
fix descends from the head it repairs.

### 3.3 A flaky test

A test that fails without a change to its inputs is made deterministic, under
the same contract as any test (§root/FS-test-contract), verified locally, and
pushed like a repair. A flaky test is never deleted, disabled, or weakened.

### 3.4 Outside the contribution

A GraalVM change or regression, a Spring AOT failure caused by GraalVM, a
harness or workflow defect, or any cause the contribution's files cannot fix
becomes an issue in `oracle/graalvm-reachability-metadata` carrying the whole
diagnosis: the failing job, a log excerpt, the root cause, and the evidence for
it (§root/FS-contribution-contract.5.3). An open issue with the same failure
signature is reused instead of opening a second one. The pull request links
the issue and is labeled `human-intervention` (§FS-human-intervention-policy).

## 4. Verification and bounds

The program records a head as handled only once its action is confirmed:

1. **Rerun.** The failed runs of the head were restarted.
2. **Push.** The commit descends from the diagnosed head, its diff against
   `master` changes only the contribution's files, and it is the head branch's
   tip after the push. A pull request takes at most two fix pushes; a failing
   head after the second goes to an issue.
3. **Issue.** The issue exists, the pull request comment links it, and the
   pull request carries `human-intervention`.

A diagnosis that is missing, malformed, asks for an action its cause does not
allow, or whose action cannot be confirmed becomes an issue and
`human-intervention` as in §FS-published-pr-ci-failures.3.4. Every failing head
therefore ends in a rerun, a pushed fix, or an issue.

## 5. Dry run

`--ci --dry-run` runs the same reads and the same diagnosis against the open
Forge pull requests, but reruns, pushes, opens, labels, and comments nothing,
and prints what the pass would have done for each pull request. `--pr <number>`
narrows a pass to the named pull requests, open or closed, so a past failure can
be diagnosed again; eligibility still applies.

## 6. Merged pull requests release their issues

A maintainer may merge between passes, so every pass first reconciles merged
pull requests labeled for follow-up: a non-final chunk releases its issue for
the next chunk, and a parked follow-up issue is released once its repair merged
(§FS-forge-chunked-dynamic-access). The reconciliation is idempotent and never
releases an issue a later run has claimed. A dry run skips it.
