# improve-library-coverage

> **See also:** [Architecture](architecture.md) ·
> [Dynamic-access workflow](dynamic-access-workflow.md) ·
> [Workflow strategies](workflow-strategies.md)

## Problem

Many already-supported libraries have low dynamic-access coverage — their
existing tests exercise only a small fraction of the library's reflective,
JNI, proxy, and resource calls. Improving coverage requires generating
additional tests without modifying existing ones or bumping library versions.

## Design

### Entry point

```console
python3 ai_workflows/improve_library_coverage.py \
  --coordinates <group:artifact:version> \
  [--strategy-name NAME] \
  [--reachability-metadata-path /path/to/graalvm-reachability-metadata] \
  [--metrics-repo-path /path/to/metrics-storage] \
  [--docs-path /path/to/docs] \
  [-v]
```

### Workflow

1. **Setup** — resolve repository paths, create a feature branch, and
   checkpoint the current commit.
2. **Baseline snapshot** — save the current stats to `.baseline-stats.json`
   so the PR script can produce a before/after comparison.
3. **Strategy execution** — run the configured strategy (default:
   `library_update_pi_gpt-5.5`), which uses
   `IncreaseDynamicAccessCoverageStrategy` in coverage-only mode (no primary
   workflow).
4. **Finalization** — on success, finalize the iteration and write run
   metrics.

### PR creation

`git_scripts/make_pr_improve_coverage.py` reads the baseline snapshot,
loads the current on-disk stats, and formats a before/after comparison in
the PR body. The baseline file is removed before staging so it is never
committed.

### Pipeline label

Triggered by the `library-update-request` label. Issues with this label
are processed via the dedicated work queue controlled by
`FORGE_LIBRARY_UPDATE_WORK_LIMIT` (default 0, disabled).
