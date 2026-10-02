# FS-maintainer-agent-queues: Maintainer agent queues

A maintainer can hand two kinds of repository work to agents through
[ephor](https://github.com/agent-grounds/ephor) and its runtime, rhei: issues
assigned to them, and generated pull requests that stopped on the
`human-intervention` label. Both queues act under the same rules as any other
contributor — a change is verified before it is published and nothing merges
without the repository's gates (§GOAL-protect-shipped-metadata) — and a
generated pull request is disposed of exactly as review would dispose of it
(§FS-contribution-contract.5).

## 1. The repository ships the work; each maintainer adopts the spending

What is the same for every maintainer is project truth and lives in the
repository: how its CI is read and re-run, the state machine the work runs
under, and the instructions each queue's agent receives. A change to any of
them reaches every maintainer through git rather than through copies that
drift.

What spends agent time is not the repository's to decide. Which issues count
as a maintainer's own, whether a queue runs unattended, how often it looks for
work, and how much it may spend are each maintainer's own configuration. A
maintainer who adopts nothing gets no agent activity from the repository.

## 2. The implement queue

The queue takes issues assigned to the maintainer that carry none of the
labels Forge works from: `library-new-request`, `library-update-request`,
`library-unsupported-version`, `fails-javac-compile`, `fails-java-run`,
`fails-native-image-build`, `fails-native-image-run`, `chunked-dynamic-access`,
`human-intervention`, `resumable`. Those issues are Forge's queue; a second
automation working them races Forge and opens duplicate pull requests.

The agent works on a branch the maintainer pull-request workflow publishes, so
the pull request is opened by the bot account and the maintainer stays
eligible to review it. A behavior change updates the spec before the code,
and the change is verified with the narrowest task that proves it. A second
agent pass reviews the work against the issue. Only a review that finds the
issue answered and the change right publishes it; the agent then gives the
pull request a title and a description that states the problem first and
closes the issue. A review that finds the work partial or blocked publishes
nothing and names what the maintainer has to decide.

## 3. The drain queue

The queue takes open `GenAI` pull requests labeled `human-intervention` and
not yet `human-intervention-fixed`. The failing checks are read before any
model is spent, and a pull request whose checks are green, which is closed,
or whose current head was already escalated ends without one.

The agent takes the first disposition that applies:

1. **Transient.** Every failing job died of infrastructure the change cannot
   have caused. The label is removed and the failed jobs are re-run. A
   failure that already came back after a re-run is not transient.
2. **Repair, skip, or close.** The dispositions of
   §FS-contribution-contract.5.1 through §FS-contribution-contract.5.4. The
   repair is verified for the target version and for every index entry whose
   `test-version` resolves to a changed test directory; only a verified
   change is pushed, and the label then becomes `human-intervention-fixed`.
3. **Escalate.** §FS-contribution-contract.5.3 and §FS-contribution-contract.5.5.
   The comment mentions the maintainer running the queue and marks the head
   it was written for, so the queue does not spend again on that head until
   a new push arrives.

The queue never merges. Merging stays with the review automation and its
gates (§forge/FS-automated-pr-review).
