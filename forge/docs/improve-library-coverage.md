# improve-library-coverage

> **See also:** [Architecture](architecture.md) ·
> [Dynamic-access workflow](dynamic-access-workflow.md) ·
> [Workflow strategies](workflow-strategies.md)

## Problem

Many already-supported libraries have low dynamic-access coverage — their
existing tests exercise only a small fraction of the library's reflective,
JNI, proxy, and resource calls. Improving coverage usually means generating
additional tests and metadata for the Maven coordinate requested by a
`library-update-request` issue.

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
2. **Target resolution** — resolve the requested coordinate from the issue
   title against `metadata/<group>/<artifact>/index.json` and prepare the
   metadata/test target to edit.
3. **Baseline snapshot** — save the current stats to `.baseline-stats.json`
   so the PR script can produce a before/after comparison.
4. **Strategy execution** — run the configured strategy (default:
   `library_update_pi_gpt-5.5`), which uses
   `IncreaseDynamicAccessCoverageStrategy` in coverage-only mode (no primary
   workflow).
5. **Finalization** — on success, finalize the iteration and write run
   metrics.

### Library update target resolution

For `library-update-request`, Forge uses only the Maven coordinate from the
issue title as the PR target. Coordinates mentioned in the issue body are
treated as context for the agent, not as additional PR targets.

Target resolution checks `index.json` in this order:

1. Requested version appears in an entry's `tested-versions`.
2. Requested version equals an entry's `metadata-version`.
3. Requested version matches an entry's `default-for` regex.
4. No match.

Exact `metadata-version` matches reuse the resolved metadata/test directories
in place. When the requested version is covered only by a shared
`tested-versions` entry or `default-for` entry, Forge splits the requested
version into a new metadata/test target by cloning the matched support and
rewriting version-specific coordinates and URLs. No-match requests create a
new requested-version target from the closest compatible existing support, or
fall back to a fresh scaffold when no usable baseline exists.

When a split creates a new middle metadata version, Forge moves the requested
version and every later tested version from the old shared entry to the new
entry. Earlier versions stay on the old entry; earlier qualifier versions such
as `1.0.1-RC1` remain below `1.0.1`.

### PR creation

`git_scripts/make_pr_improve_coverage.py` reads the baseline snapshot,
loads the current on-disk stats, and formats a before/after comparison in
the PR body. The baseline file is removed before staging so it is never
committed.

### Pipeline label

Triggered by the `library-update-request` label. Issues with this label
are processed via the dedicated work queue controlled by
`FORGE_LIBRARY_UPDATE_WORK_LIMIT` (default 0, disabled).
