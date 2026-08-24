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
- Prompt instructions and review skills are the fallback for what genuinely
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
