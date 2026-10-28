# FS-graalvm-blocked-work: Work blocked on a GraalVM defect

Some work cannot proceed until GraalVM itself changes: Native Image fails
inside the builder, or its agent emits metadata the rules forbid however the
test exercises the library. No disposition of §FS-contribution-contract.5
moves such an item. The contribution is not what is wrong, no repair inside it
reaches the cause, and the defect is not the repository's to fix. What the
repository owes the item is a record that outlives the wait: an item that
carries only `human-intervention` looks like every other item waiting on a
maintainer, so when GraalVM ships the fix nobody knows to look at it again. A
blocker that stays undeclared is never revisited, and coverage it holds back
never grows (§GOAL-broad-version-coverage). This rule binds every issue and
pull request, generated or not.

## 1. The blocker is declared on the item

An issue or pull request that cannot proceed until a GraalVM or Native Image
defect is fixed carries the `graalvm-bug` label, and its body or a comment
links the `oracle/graal` GitHub issue and names the GraalVM ticket key,
`GR-<n>`. The label says that the item waits; the two links say on what, and
they are the only way to learn when the wait is over.

Whoever finds the blocker declares it. Where no ticket exists, a maintainer
opens one: tickets are filed as `oracle/graal` issues and mirrored to Jira. An
agent or automation that cannot open a ticket labels the item
`human-intervention` and quotes the evidence that GraalVM is at fault, so a
maintainer files the ticket and adds `graalvm-bug` without re-deriving the
diagnosis.

The evidence points at GraalVM when it shows either signal:

- a builder-internal `VMError`, `HostedError` or `shouldNotReachHere` from
  `native-image`: the builder failing inside itself rather than reporting the
  code it builds;
- metadata the rules forbid (§FS-repository-functional-spec.5.1) that the
  Native Image agent emits on every generation; the report quotes the entry and
  the rule it breaks.

A missing registration, an `UnsupportedFeatureError`, or a failure only a
forbidden flag (§FS-test-contract.2.7.2) would silence is not a GraalVM defect:
the first is metadata the contribution owes, the second a limit Native Image
documents, and the third a check working as intended.

## 2. The label holds the item out of automation

`graalvm-bug` holds an item out of the automation that would meet the defect
again, on its own, whether or not the item also carries `human-intervention`:

- Forge does not claim an issue that carries it
  (§forge/FS-forge-run-requirements.2.1);
- Forge's review does not review a pull request that carries it
  (§forge/FS-published-pr-review.2);
- the maintainer drain queue leaves such a pull request alone
  (§FS-maintainer-agent-queues.3);
- the real-use review skips such a pull request
  (§real-use-tests/FS-real-use-test-generation.4).

Every attempt on a blocked item meets the same defect, so automation spent on
it buys nothing until GraalVM changes. A maintainer who assigns a blocked issue
to themselves is not such an attempt: writing the workaround is the work the
implement queue then does (§FS-maintainer-agent-queues.2).

The hold does not lean on `human-intervention` because that label does not stay
put while an item waits: the drain removes it from a failure it reads as
transient, and a maintainer's `human-intervention-fixed` reopens Forge's review.
Neither resolves a defect GraalVM has not fixed, and an item released by either
would go back to automation only to fail again.

## 3. A maintainer releases it once GraalVM is out of the way

A maintainer releases the item once any of these holds:

1. the cited fix has reached the GraalVM version CI tests against;
2. a workaround that cites the ticket has landed;
3. the item's own checks pass on that GraalVM, as an item held by an
   intermittent defect can on a re-run; the ticket, and the issue that tracks
   the defect, stay open.

On an issue the release removes `graalvm-bug` and `human-intervention`, which
returns the issue to Forge's queue. On a pull request it removes `graalvm-bug`
and swaps `human-intervention` for `human-intervention-fixed`: removing the
label alone would not reopen a pull request Forge's review already marked
`reviewed`, and the swap is what does (§forge/FS-published-pr-review.2).

The release waits for the GraalVM that CI runs rather than for the fix to merge
upstream, because CI is what judges the item, and a release before the fix
reaches it sends automation into the same failure. A workaround releases the
item only when it cites the ticket, so the workaround can be found and removed
once the fix ships.
