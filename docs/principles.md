# PRCPL-prefer-algorithmic: Everything that can be algorithmic should be algorithmic

When a rule, check, decision, or workflow step can be expressed as a
deterministic algorithm, implement it as one — a schema, a validator, a build
task, a CI gate, a script — rather than leaving it to prompt instructions,
agent judgment, or reviewer vigilance. Judgment, human or model, is reserved
for what cannot be decided mechanically.
§GRUND-repository-motivation §GOAL-protect-shipped-metadata

An algorithmic rule runs the same way every time, costs nothing per run, fails
loudly in CI instead of quietly in review, and cannot drift the way prompt
wording and reviewer attention do. Every rule left to judgment is a rule that
is enforced only sometimes.

Applying the principle:

- When a spec point can be checked mechanically, pair it with an enforcing gate
  — as `checkMetadataFiles`, `validateIndexFiles`, and `grund check` do
  (§FS-repository-functional-spec.5.3) — instead of restating it in prompts or
  review checklists.
- When writing new rules, prefer mechanically checkable formulations — numeric
  limits, enumerable patterns, exact paths — over qualitative language, so a
  rule stated for judgment today can be promoted into a gate tomorrow. The
  contribution-shape limits (§FS-contribution-contract.2) are written this way.
- When an agent or reviewer makes the same decision repeatedly, promote that
  decision into deterministic code and leave the agent only the judgment that
  remains — as Forge keeps setup and Gradle plumbing in workflow drivers rather
  than making agents decide it (§forge/AR-forge-strategy-agent-boundary).
- Prompt instructions and review skills are the fallback for what genuinely
  requires judgment, and even then each instruction cites the spec point it
  enforces (§FS-repository-functional-spec.5.6), so every rule keeps one
  checkable home.
