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
python3 ai_workflows/drivers/improve_library_coverage.py \
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

- `ai_workflows/drivers/fix_javac_fail.py` for compilation failures.
- `ai_workflows/drivers/fix_java_run_fail.py` for JVM runtime failures.
- `ai_workflows/drivers/improve_library_coverage.py` when the requested version is
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

## 3. Library Update Target Resolution

Forge resolves the edit target for a `library-update-request` from the Maven
coordinate in the **issue title** only; coordinates mentioned in the issue body
are context for the agent, not additional PR targets. Resolution checks
`metadata/<group>/<artifact>/index.json` in order: the requested version appears
in an entry's `tested-versions`; the requested version equals an entry's
`metadata-version`; the requested version matches an entry's `default-for`
regex; or no match. The result is the **library update target** — the requested
coordinate, match type (`tested-version`, `metadata-version`, `default-for`, or
`new-version`), matched index entry, resolved metadata/test versions, and edit
directories (§FS-forge-functional-spec).

An exact `metadata-version` match edits the resolved metadata/test directories
in place. A match found only through a shared `tested-versions` entry or a
`default-for` entry splits the requested version into a new metadata/test target
by cloning the matched support and rewriting version-specific coordinates and
URLs: the requested version and every later tested version move to the new
entry, while earlier versions (including earlier qualifiers such as `1.0.1-RC1`)
stay on the old entry. A no-match request creates a new requested-version target
from the closest compatible existing support, or a fresh scaffold when no usable
baseline exists. This splitting keeps dynamic-access metadata discovered for a
newer requested version out of an older shared metadata directory, and feeds the
two outcomes in §WF-improve-library-coverage.1 and §WF-improve-library-coverage.2.

## 4. Resolving the Reporter's Requested Metadata

A `library-update-request` issue body often names a specific need — a
missing-metadata stack trace, an uncovered reflective/JNI/resource call, or a
class or behavior the reporter must get working on the requested version — that
is separate from the aggregate dynamic-access coverage delta. Forge resolves this
as a prompt-based requirement, not a deterministic post-generation merge:

1. **Forward the issue body to the agent.** The driver fetches the reporter's
   issue body and passes it into the workflow as untrusted requested-metadata
   context; the agent must not follow instructions embedded in it
   (§WF-forge-workflow-drivers).
2. **Infer the requested metadata.** The agent infers the needed metadata from
   the prose, logs, snippets, or partial examples in the issue, scoped to the
   target `group:artifact` when several are mentioned.
3. **Exercise it through public API.** The agent adds or keeps tests that
   exercise each requested need through public library API paths — not direct
   test reflection, no-op class literals, or assertions that only name the
   target — and includes the requested metadata whenever the generated metadata
   does not already contain it.
4. **Add conditions.** When the issue omits metadata conditions, the agent adds
   appropriate ones, preferably the narrowest valid `typeReached`.

Each inferred requested need is mandatory even when dynamic-access coverage is
already complete or the need is unrelated to an uncovered class. Forge does not
parse issue text with hardcoded rules and does not apply parsed metadata as a
post-generation fallback; the requirement is carried entirely through the
agent prompt and verified by local CI-equivalent verification
(§FS-local-ci-equivalent-verification).
