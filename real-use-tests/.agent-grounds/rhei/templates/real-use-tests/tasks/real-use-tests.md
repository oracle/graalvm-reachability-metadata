### Task real-use: Real-use tests for {{coordinate}}
**State:** prepare

- Coordinate: `{{coordinate}}`
- Scenario passes: {{scenario_passes}}, each adding one scenario for a
  subsystem nothing covers yet.
- Source checkout: `{{repo_checkout}}`; worktree root: `{{worktree_root}}`.
- Purpose: generate the real-use project for the coordinate in a dedicated
  worktree, prove it on the JVM and on Native Image against the shipped
  metadata, and commit it for publication (§FS-real-use-test-generation).
- Run state, prompts, reports, logs, and the pull-request description live
  under `runtime/real-use/`.
