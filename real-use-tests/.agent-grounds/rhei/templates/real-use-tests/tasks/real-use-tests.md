### Task real-use: Real-use tests for issue #{{issue_number}}
**State:** prepare

- Issue: #{{issue_number}}, labeled `real-use-test`; its title names the
  coordinate, which `prepare` writes to `runtime/real-use/run.md`.
- Scenario passes: {{scenario_passes}}, each adding one scenario for a
  subsystem nothing covers yet.
- Source checkout: `{{repo_checkout}}`; worktree root: `{{worktree_root}}`.
- Purpose: resolve the issue by generating the real-use project for the
  coordinate in a dedicated worktree, proving it on the JVM and on Native
  Image against the shipped metadata, and publishing it
  (§FS-real-use-test-generation).
- Run state, prompts, reports, logs, and the publication record live under
  `runtime/real-use/`.
