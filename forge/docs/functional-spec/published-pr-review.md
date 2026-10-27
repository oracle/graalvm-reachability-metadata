# FS-published-pr-review: Review of a published pull request

A published Forge pull request arrives audited: its tree was reviewed locally
before the push (§FS-local-branch-review) and passed the coordinate's own native
lanes (§FS-local-ci-equivalent-verification.2.1). What is left for the review
after publication is what those lanes do not cover: the other tested versions
and the dependent libraries of the changed-metadata matrix, the Spring AOT smoke
tests, `master` moving under the pull request, transient infrastructure, and
GraalVM itself. The review takes every pull request to an outcome — merge
armed, a fix pushed, jobs rerun, or an issue — never to silence
(§GOAL-shorten-issue-to-shipped-metadata).

## 1. Cadence and queue

The launcher reviews every third run, and whenever nothing can be claimed. The
queue is read from GitHub on every pass: the open Forge pull requests, oldest
first, of which at most three get an agent per pass. A pull request is Forge's
when its head repository is `oracle/graalvm-reachability-metadata`, its head
branch is an upstream `ai/**` branch, and it carries a Forge route label
(§FS-forge-functional-spec). No local state carries over between passes, so a
pass on another host reaches the same decisions.

## 2. Skipped before any agent

Each pull request is read again right before it is judged, because an earlier
agent in the same pass may have run for an hour. It is skipped, with no agent
spent, when:

1. it is not open;
2. it is already approved;
3. it is labeled `reviewed` without `human-intervention-fixed`, or labeled
   `human-intervention`;
4. another review holds it under a fresh `in-review` lock
   (§FS-published-pr-review.3);
5. any check on its head is still running, or none has reported yet. Unlike the
   real-use review, this review waits for CI: its decisions read the finished
   checks.

A maintainer who fixes a pull request swaps `human-intervention` for
`human-intervention-fixed` (§FS-human-intervention-policy), and that reopens the
review of a pull request already labeled `reviewed`.

## 3. The in-review lock

Before its agent runs, the review labels the pull request `in-review`, and it
removes the label once the outcome is recorded, so concurrent launchers do not
review the same head twice. Removal is a plain step, not a guaranteed cleanup:
a killed review leaves its label behind, and a label older than three hours is
stale and taken over by the next pass.

## 4. The reviewer

Each pull request gets one invocation of the review agent in a throwaway
detached checkout of its exact head. The review agent is the launcher's
`review_agent` input (§FS-forge-agent-runtime-selection), always a Claude Code
target, because the review needs the web and the GraalVM history. The agent
receives the pull request's number, branch, head, route label, and coordinate;
its body, which renders the local review from the descriptor; the head's check
results with the tail of each failed log; the bounds left
(§FS-published-pr-review.5); and the Metadata and Native Image rules of the
run's `fix` state, which bind its repairs exactly as they bind a run's.

The agent approves, arms auto-merge, reruns jobs, and pushes with `gh` and `git`
itself. It does not label, comment, or open issues: it reports its outcome and,
for an issue, the issue's title, body, and signature, and the program confirms
the outcome, files the issue, and sets the labels and markers. The agent takes
the first case below that applies.

### 4.1 All green

Every check passed and the local review in the body is clean: the audit, the
reviewer gates, and the native lanes. The agent approves the pull request and
arms auto-merge for exactly that head: `gh pr review <n> --approve`, then
`gh pr merge <n> --auto --squash --match-head-commit <head>`. Approval is
offered only when the program read every check as passed.

### 4.2 Infrastructure failure

Every failing job died of something the change cannot have caused: the network,
the runner, Gradle plugin resolution, a Docker pull, or a cancellation. The
agent reruns the failed jobs of each such run: `gh run rerun <run-id> --failed`.

### 4.3 A library test lane failed

A test lane of the changed-metadata matrix failed, for this library or for a
dependent library. The agent finds the cause.

1. **A metadata miss for the current library** — a missing registration, or a
   condition reached too late — is fixed under the `fix` state's rules, verified
   by running that exact lane locally (the same JDK, native-image mode, and
   library version), and pushed to the head branch.
2. **Otherwise the lanes are compared.** When JDK 25 `current-defaults` fails
   but `latest-ea` passes, the agent reproduces it locally with the exact GraalVM
   build the lane used, read from its log; a local failure is most likely a
   Native Image fix that was never backported to the 25.0 release, confirmed by
   a fix on `oracle/graal` master that the release branch lacks. When `latest-ea`
   fails, and fails locally with the same configuration, it is most likely a
   regression in the newest GraalVM, confirmed in the `oracle/graal` history.
   Either way the outcome is an issue (§FS-published-pr-review.4.6).

### 4.4 A Spring AOT smoke test failed

The agent first tries to fix the pull request's metadata. A smoke test that
cannot pass because of Native Image behavior, or that is flaky — a failure that
recurs on unrelated pull requests touching the same library — is removed from
the gate through the repository's Spring AOT exclusion list
(§root/FS-repository-functional-spec.5.3), with its reason. A cause in a
dependent library, or a Native Image 25.0 bug, cannot be fixed in the
repository, and the outcome is an issue (§FS-published-pr-review.4.6). Spring
smoke tests run on the newest GraalVM LTS, so a fix that exists only in a newer
release is such a bug.

### 4.5 Fix first

The review always tries to fix the pull request first. A test of ours that fails
only because Native Image does not support a behavior is deleted or re-scoped
under §root/FS-test-contract.4.3.2. Anything else it cannot fix that looks like a
Native Image error in something that usually works is investigated in the
`oracle/graal` history and becomes an issue.

### 4.6 The issue

An issue in `oracle/graalvm-reachability-metadata` carries the whole diagnosis:
the failing lane (JDK, native-image mode, library version), the error quoted,
the local reproduction with exact versions, the suspected Native Image cause
with `oracle/graal` commit and pull request links, and why the cause is not the
pull request's metadata. Its signature is a short stable key of the failure, the
same for every pull request it breaks; an open issue carrying the same
signature is reused instead of opening a second
(§root/FS-contribution-contract.5.3). The pull request links the issue and is
labeled `human-intervention` (§FS-human-intervention-policy).

## 5. Outcomes, labels, and bounds

The program confirms each outcome against GitHub before it records it, and only
the program writes labels and markers:

1. **Terminal.** Approved with auto-merge armed, or an issue filed and the pull
   request labeled `human-intervention`. Either adds `reviewed`, so the pull
   request is never reviewed again until a maintainer reopens it
   (§FS-published-pr-review.2); an issue also removes `human-intervention-fixed`.
2. **Not terminal.** A pushed fix or a rerun removes only `in-review` and leaves
   a comment by the Forge account that marks the head and the action. The next
   pass reviews the new head, or the rerun head, once its checks have finished.

The markers bound the loop: a head is rerun at most once, and a pull request
takes at most two fix pushes. The agent is told what is left, and past either
bound the outcome is an issue. An outcome that is missing, malformed, outside
the bounds, offered approval on a head that was not green, or cannot be
confirmed becomes an issue the program writes from the evidence, with the same
labels; every reviewed head therefore ends in one of the outcomes above.

## 6. Dry run

`--review --dry-run` runs the same reads and the same agent, but approves,
merges, reruns, pushes, labels, comments, and opens nothing, and prints what the
review would do for each pull request. The agent is told it is a dry run, and it
gets no GitHub credential and no push URL, so it cannot write even against its
instruction; it reports the outcome it would have taken. `--pr <number>` names
pull requests, open or closed, and replays them: the skips of
§FS-published-pr-review.2 that describe their lifecycle — the state, the
approval, the labels, and the lock — are printed but not applied. `--head <sha>`
replays one named pull request at an earlier head, so a past failure can be
reviewed again.

## 7. Merged pull requests release their issues

GitHub may complete an armed merge between passes, so every pass first
reconciles merged pull requests labeled for follow-up: a non-final chunk
releases its issue for the next chunk, and a parked follow-up issue is released
once its repair merged (§FS-forge-chunked-dynamic-access). The reconciliation
is idempotent and never releases an issue a later run has claimed. A dry run
skips it.
