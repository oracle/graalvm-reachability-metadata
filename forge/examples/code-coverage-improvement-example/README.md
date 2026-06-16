# Code Coverage Improvement Template

This Rhei template converts one GitHub issue labeled `improve-code-coverage`
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

The rendered workspace contains a static phase chain:

1. Convert the issue and create or reuse the per-issue worktree.
2. Prepare the already-supported library and dedicated coverage suite.
3. Generate API inventory artifacts.
4. Run three JVM-only API-cover and JaCoCo validation iterations.
5. Prepare native metadata once (generate plus Codex repair) for the PGO builds.
6. Run three instrumented-PGO discovery report and discovery-cover iterations.
7. Finalize local validation.
8. Publish a pull request with coverage evidence.

Generated tests are constrained to
`tests/<group>/<artifact>/<version>/code-coverage`, while runtime evidence stays
under `runtime/code-coverage/` inside the Rhei workspace. Reviewed tasks use a
lightweight `agent-review` and `agent-fix` loop capped by `review_passes`; helper
tasks complete directly after writing their artifacts. The review checks are
limited to task adherence, artifact sufficiency, scope, public API targeting,
validation evidence, and whether manual metadata or Native Image handling must
route to `human-intervention` §WF-code-coverage-improvement.

The rendered example in `examples/code-coverage-improvement-example/` is a dry
workspace that validates and dry-runs without touching GitHub or a worktree.
