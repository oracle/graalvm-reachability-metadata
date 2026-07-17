# Code Coverage Improvement Template

This Rhei template converts one GitHub issue labeled `code-coverage-improvement`
into a bounded workspace for the planned Forge code coverage workflow
§WF-code-coverage-improvement.

Instantiate it from `forge/` with the issue number and, optionally, an explicit
coordinate override:

```console
rhei instantiate code-coverage-improvement \
  --set issue_number=1234 \
  --set coordinate=com.example:library:1.2.3 \
  --output code-coverage-1234
```

The rendered workspace contains eight tasks:

1. Convert the issue and create or reuse the per-issue worktree.
2. Prepare the already-supported library and dedicated coverage suite.
3. Generate API inventory artifacts.
4. The API coverage loop: `api-measure -> api-cover -> api-measure`.
5. Prepare native metadata once (generate plus Codex repair) for the PGO builds.
6. The deep coverage loop: `deep-measure -> deep-cover -> deep-measure`.
7. Finalize validation and metrics deterministically.
8. Publish a pull request with separate JaCoCo and sampled-path evidence.

The coverage loops live in the state machine, not in unrolled tasks.
Measurement (JaCoCo report generation and correlation; for the deep loop also
sampled PGO and the static call graph) is a deterministic program that always
writes the current report to one fixed location and, when the loop continues,
derives the prompt of exact JaCoCo-uncovered targets from it in the same step.
The cover agent generates tests from the prompt
and always returns to measurement, so only re-measurement can complete a phase
— an agent can never claim progress. A phase completes when no actionable
target remains or its heuristic iteration budget (baseline-uncovered / 2 /
250 passes, at least one and at most `coverage_iterations`) is spent. Public
API prompts contain at most 200 methods the latest exact JaCoCo report marks
uncovered. Deep
prompts list at most 200 JaCoCo-uncovered internal methods using compact
`Observed` / `Uncovered paths` navigation. Measurement owns target rotation:
attempt counts are carried deterministically in the discovery-report history
and previously prompted methods are deprioritized; the cover agent writes no
target state.

Generated tests are constrained to
`tests/src/<group>/<artifact>/<test-version>/code-coverage-improvement/src/test/java` (plus optional
`src/test/resources`), while runtime evidence stays under
`runtime/code-coverage/` inside the Rhei workspace. The pipeline tasks run
unreviewed; finalization executes as a deterministic step program (JVM tests
only at this stage — no Native Image validation) whose nonzero exit code names
the failed step, and completes only when the deterministic `finalize-verify`
program accepts its artifacts. Fixable failures are repaired by `finalize-fix`
for at most `fix_passes` pass(es) before the steps re-run. Failed targets or an
explicit `needsHumanIntervention` flag route to `human-intervention`. Pipeline
tasks also route there when a required helper or artifact fails instead of
completing with partial evidence. Finalization writes schema-validated,
separate API/deep JaCoCo metrics and labels PGO as guidance only
§WF-code-coverage-improvement.

The rendered example in `examples/code-coverage-improvement-example/` is a dry
workspace that validates and dry-runs without touching GitHub or a worktree.
