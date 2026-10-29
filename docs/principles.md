# PRCPL-prefer-algorithmic: Everything that can be algorithmic should be algorithmic

When a rule, check, decision, or workflow step can be expressed as a
deterministic algorithm, implement it as one — a schema, a validator, a build
task, a CI gate, a script — rather than leaving it to prompt instructions,
agent judgment, or reviewer vigilance. Judgment, human or model, is reserved
for what cannot be decided mechanically.
§GOAL-protect-shipped-metadata

An algorithmic rule runs the same way every time, costs nothing per run, fails
loudly in CI instead of quietly in review, and cannot drift the way prompt
wording and reviewer attention do. Every rule left to judgment is a rule that
is enforced only sometimes.

Applying the principle:

- Pair mechanically checkable spec points with an enforcing gate instead of
  restating them in prompts or review checklists.
- Prefer mechanically checkable formulations — numeric limits, enumerable
  patterns, exact paths — over qualitative language, so a rule stated for
  judgment today can be promoted into a gate tomorrow.
- When an agent or reviewer makes the same decision repeatedly, promote that
  decision into deterministic code and leave the agent only the judgment that
  remains.
- Prompt instructions are the fallback for what genuinely
  requires judgment, and even then each instruction cites the spec point it
  enforces, so every rule keeps one checkable home.

# PRCPL-verify-inputs: Define inputs exactly and verify them before running

Every component declares its inputs exactly — a schema, a property list, a
path set, a capability list — and verifies them at its boundary before doing
any work. A component that runs on unverified inputs converts its caller's bug
into its own mysterious failure, and in a system with this many components —
harness, CI, Forge control plane, drivers, agents, publication — the debugging
then happens in the wrong component. Fail fast at the boundary with a message
naming the violated input contract, so a defect surfaces in the component that
introduced it.
§GOAL-protect-shipped-metadata

Applying the principle:

- Give every persisted or exchanged artifact a schema and validate on read,
  not just on write; a reader that trusts its writer inherits the writer's
  bugs.
- Validate required properties, paths, and environment capabilities before the
  first side effect, and reject with the exact missing or malformed input, not
  a downstream stack trace.
- When a failure is diagnosed to a missing input check, add the check to the
  component that consumed the input — not a workaround in the component where
  the failure happened to surface.

# PRCPL-graalvm-workaround-tickets: Every GraalVM workaround cites its GraalVM ticket

A workaround for a defect in GraalVM or Native Image — the image builder, the
tracing agent, or the image runtime — encodes behavior the product will stop
having. Each one points at the ticket that tracks the defect, so it is removed
when the defect is fixed instead of outliving it, and the tests and metadata
keep describing Native Image as it is rather than as it was.
§GOAL-tested-metadata

## 1. Every workaround cites its ticket

A GraalVM ticket is GraalVM's fact, not this repository's: GraalVM owns its
wording and its state. So the tickets keep their Jira IDs in a namespace of
their own, apart from the repository's specification, and a workaround cites
the ticket that tracks its defect as `<§>graal/GR-<number>` from every
namespace — in test code, build logic, CI, Forge, the real-use tests, or the
documentation. Metadata files cannot cite, so a workaround in the shipped
metadata cites its ticket in the test that justifies that metadata, or in the
pull request that introduces it. Under `tests/src` and `real-use-tests/src`,
which Checkstyle holds to ASCII, the workaround's comment names the bare
`GR-<number>` instead.

## 2. No ticket, no workaround

A defect found without a ticket gets one opened before its workaround lands.
Work held up by a GraalVM defect is declared, not left implicit: its issue or
pull request carries the `graalvm-bug` GitHub label and links the oracle/graal
issue and the GR ticket.

## 3. The ticket is a checked fact

Every cited ticket has a committed snapshot, and a citation without one fails
`grund check` (§FS-repository-functional-spec.5.6); a bare `GR-<number>` under
`tests/src` or `real-use-tests/src`, which grund does not read, is held to the
same rule by the gate. The tracker is internal and this repository is public,
so a snapshot carries only the ticket's public fields: its summary, status,
resolution, fix versions, and public links.

## 4. Removed once the fix ships

A workaround is removed once its ticket's fix ships in the GraalVM versions the
required lanes run (§FS-repository-functional-spec.5.2). A snapshot outlives
its last citation unless someone deletes it, and grund then only warns that it
is declared but never cited, so the change that removes a ticket's last
workaround deletes its snapshot too.
