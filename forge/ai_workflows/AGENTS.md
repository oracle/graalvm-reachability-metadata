# AGENTS

## PGO Coverage Workflow
- PGO-based coverage improvement runs through `improve_library_coverage.py` with strategy `pgo_profile_driven_exploration_main_sources_pi_gpt-5.5`.
- When testing branch `ai/vjovanov/pgo-per-call-unreachable-stats`, create a separate reachability-metadata worktree for the run instead of using the main `master/` checkout. Example:
  ```console
  cd /home/vjovanov/c/grm/master
  git worktree add --detach /tmp/grm-pgo-<artifact> ai/vjovanov/pgo-per-call-unreachable-stats
  cd /tmp/grm-pgo-<artifact>/forge
  PYTHONPATH=$PWD python3 ai_workflows/improve_library_coverage.py \
    --coordinates <group:artifact:version> \
    --strategy-name pgo_profile_driven_exploration_main_sources_pi_gpt-5.5 \
    -v
  ```
- Keep `PYTHONPATH=$PWD` when invoking Forge entry points so Python imports this checkout's `forge/` modules instead of another local metadata-forge checkout.
- PGO near-call report forwarding uses the inherited `AllCoordinatesExecTask.appendProperty(...)` helper. Do not add a duplicate private `appendProperty(...)` method in `GeneratePgoDynamicAccessNearCallReportInvocationTask`, because that weakens the inherited access level and breaks `:tck-build-logic:compileGroovy`.
