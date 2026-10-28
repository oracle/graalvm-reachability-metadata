### Task forge: Forge issue #{{issue_number}}
**State:** prepare

- Issue: #{{issue_number}}; its label decides the route and its title the
  coordinate, which `prepare` writes to `runtime/forge/run.md`.
- Agents: setup `{{setup_agent}}`, cover and fix `{{worker_agent}}`.
- Explore budgets: bulk from {{bulk_min_classes}} classes, {{bulk_passes}} bulk
  passes, {{class_attempts}} attempts per class, {{explore_passes}} passes in
  all; a `fails-*` issue defers above {{defer_classes}} classes, and a chunk
  spends at most {{defer_classes}} classes.
- Finalize budget: at most {{finalize_fixes}} fix requests.
- Native budget: at most {{native_fixes}} fix requests.
- Source checkout: `{{repo_checkout}}`; worktree root: `{{worktree_root}}`.
- Purpose: resolve the issue by adding or repairing its library version in a
  dedicated worktree, covering its dynamic-access call sites with tests,
  proving the metadata on Native Image, and pushing the publication branch.
- Run state, prompts, reports, logs, and the outcome live under `runtime/forge/`.
