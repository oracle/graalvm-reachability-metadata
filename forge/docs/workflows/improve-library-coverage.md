# WF-improve-library-coverage: Improve library coverage workflow

Coverage improvement is part of the Forge workflow system
(§WF-forge-workflow-system).

## Problem

Many already-supported libraries have low dynamic-access coverage — their
existing tests exercise only a small fraction of the library's reflective,
JNI, proxy, and resource calls. Improving coverage requires generating
additional tests without weakening existing ones. A `library-update-request`
may target either an already-tested library version or a newer artifact version
that is not represented in the repository yet (§WF-dynamic-access-workflow).

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

The driver owns deterministic setup, strategy selection, metrics paths, and
finalization for the coverage run (§WF-forge-workflow-drivers).

## 1. Existing Test Suite Case

This is the currently implemented `library-update-request` path. It applies
when the requested `group:artifact:version` already has a test suite in the
repository under `tests/src/<group>/<artifact>/<version>/`.

The workflow must preserve the existing implementation shape:

1. **Setup** — resolve repository paths, create a feature branch, and
   resolve the existing test directory for the requested version.
2. **Baseline snapshot** — save the current stats to `.baseline-stats.json`
   so the git publication script can produce a before/after comparison.
3. **Checkpoint** — commit the current test directory and metadata index so
   failure handling can distinguish pre-existing state from generated changes.
4. **Context preparation** — populate artifact URLs, materialize configured
   source context, resolve the test-source layout, and initialize the agent
   with only the target test sources and `build.gradle` editable.
5. **Strategy execution** — run the configured strategy (default:
   `library_update_pi_gpt-5.5`), which uses
   `IncreaseDynamicAccessCoverageStrategy` in coverage-only mode (no primary
   workflow) (§WF-dynamic-access-composite-strategy).
6. **Finalization** — on success, finalize the iteration after local
   CI-equivalent verification (§FS-local-ci-equivalent-verification) and write
   run metrics with durable session logs (§FS-durable-generation-logs).

This case must not treat the request as a version-bump repair. It improves
coverage for the existing requested test suite.

## 2. Missing Requested Version Case

This case applies when the repository already supports the requested
`group:artifact` artifact, but the requested `version` does not yet have a test
suite in the repository.

Forge must first resolve the latest supported test version for the artifact and
probe the requested version with that latest test suite. The probe determines
which driver owns the rest of the work:

1. **Latest-suite preparation** — copy or otherwise prepare the latest
   supported test suite so it runs against the requested version, preserving the
   previous version as the baseline.
2. **Compatibility probe** — run the requested version with the latest test
   suite before selecting the repair or coverage path.
3. **Javac failure** — if the probe fails at Java compilation, dispatch the
   javac-fix driver for the previous version and requested version. That
   driver owns project copying, metadata-index updates, javac repair, and
   the composite coverage phase (§WF-java-fail-fix-workflow).
4. **Java runtime failure** — if compilation succeeds but the JVM test run
   fails, dispatch the java-run fix driver with the java-run composite
   strategy. That driver owns runtime repair and the composite coverage
   phase (§WF-java-fail-fix-workflow).
5. **Compatible version** — if compilation and JVM tests both pass, the
   requested version is compatible with the latest test suite. Forge should add
   or select the requested version's test project and run the regular
   improve-coverage workflow for that requested version
   (§WF-improve-library-coverage.1).

All branches of this case end in coverage improvement for the requested
version. The difference is which driver owns the prerequisite work before
coverage starts:

- `ai_workflows/fix_javac_fail.py` for compilation failures.
- `ai_workflows/fix_java_run_fail.py` for JVM runtime failures.
- `ai_workflows/improve_library_coverage.py` when the requested version is
  already compatible with the latest supported test suite.

### PR creation

`git_scripts/make_pr_improve_coverage.py` reads the baseline snapshot,
loads the current on-disk stats, and formats a before/after comparison in
the PR body. The baseline file is removed before staging so it is never
committed (§GIT-forge-publication).

### Pipeline label

Triggered by the `library-update-request` label. Issues with this label
are processed via the dedicated work queue controlled by
`FORGE_LIBRARY_UPDATE_WORK_LIMIT` (default 1; set to 0 to disable the queue)
(§ORCH-forge-orchestration-spec).

## 3. Resolving the User Request in the Issue Body

A `library-update-request` is not always a generic "raise coverage" task. The
"Update an existing library" issue template lets a reporter describe a specific
problem — a missing-metadata stack trace, a reflective/JNI/resource call that is
not covered, or a concrete class or behavior the user needs to work on the
requested version. That explicit ask lives in the issue description, separate
from the aggregate dynamic-access coverage delta.

For both the existing-suite case (§WF-improve-library-coverage.1) and the
missing-version case (§WF-improve-library-coverage.2), the workflow must treat
the user-requested metadata and tests as required outcomes of the run, not
optional extras:

1. **Read the request** — extract the concrete items from the issue body: the
   named classes, missing-metadata entries, stack traces, or behaviors the
   reporter asked for.
2. **Check the generated result** — after the coverage strategy finishes its
   generation pass, verify whether the requested metadata and tests are present
   in the generated output.
3. **Add what the original generation missed** — when part of the request is not
   covered, add the missing metadata and tests so the requested behavior is
   actually exercised, then re-verify.
4. **Treat the request as the acceptance signal** — the run resolves the issue
   only when the user's request is satisfied and the result still passes local
   CI-equivalent verification (§FS-local-ci-equivalent-verification). The check
   and any added items belong in the durable session log and run metrics
   (§FS-durable-generation-logs).

This keeps a `library-update-request` honest: coverage numbers can rise while
still missing the exact thing the reporter asked for, so the explicit request in
the issue body — not just the coverage delta — is what determines whether the
issue is done.
