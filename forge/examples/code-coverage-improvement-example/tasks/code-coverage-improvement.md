### Task code-coverage-convert: Convert issue 8380
**State:** prepared

- Source issue: `https://github.com/oracle/graalvm-reachability-metadata/issues/8380`
- Repository: `oracle/graalvm-reachability-metadata`
- Required label: `code-coverage-improvement`
- Coordinate override: `org.example:example-library:1.2.3`
- Source checkout: `../../..`
- Worktree root: `../../../.agents/worktrees`
- Work subdirectory: `forge`
- Project owner: `oracle`
- Project number: `30`
- Purpose: fetch one `code-coverage-improvement` issue and create or reuse the per-issue
  worktree for the code coverage workflow §WF-code-coverage-improvement.
- Required work:
  - Fetch the issue with `gh issue view 8380 --repo oracle/graalvm-reachability-metadata`.
  - Verify that it carries `code-coverage-improvement`.
  - If `org.example:example-library:1.2.3` is non-empty, use it as the coordinate; otherwise parse
    exactly one `group:artifact:version` coordinate from the issue body.
  - If `oracle` and `30` are non-empty, verify the
    issue's Project item is in `Todo`, then move it to
    `In Progress` only after the worktree and conversion artifacts
    are written.
  - Create or reuse one worktree below `../../../.agents/worktrees` for the issue using a
    branch name like `rhei/code-coverage-issue-8380-<slug>`.
  - Record the resolved worktree and work path, where work path is the worktree
    joined with `forge`.
  - Write `runtime/code-coverage/issues/conversion.json` with exactly these
    fields: `coordinate`, `worktreePath`, `workPath`,
    `coverageSuiteAbsolutePath`, and `coverageSuiteRepoRelativePath`
    (`tests/src/<group>/<artifact>/<test-version>/code-coverage-improvement`,
    where `<test-version>` is the indexed test project directory that covers
    the coordinate — resolve it with
    `utility_scripts.metadata_index.resolve_test_dir`). The deterministic
    finalization program reads this record; all paths except the
    repository-relative suite path must be absolute.
- Artifacts:
  - `runtime/code-coverage/issues/inventory.md`
  - `runtime/code-coverage/issues/conversion.md`
  - `runtime/code-coverage/issues/conversion.json`
  - `runtime/code-coverage/work/code-coverage-convert.md`

### Task code-coverage-prepare: Prepare library
**State:** prepared
**Prior:** Task code-coverage-convert

- Source artifact: `runtime/code-coverage/issues/conversion.md`
- Helper preference: reuse Forge path and source-context helpers before adding
  task-specific setup logic.
- Purpose: resolve the target coordinate, confirm it is already represented in
  the reachability repository, and create or verify the dedicated code coverage
  test suite.
- Required work:
  - Resolve `group`, `artifact`, and `version` from the conversion artifact.
  - Confirm the library has an existing metadata or test entry in the source
    checkout.
  - Resolve the existing metadata-generation test location.
  - Create or verify the tracked extension suite at
    `code-coverage-improvement/` inside the resolved test project, including
    `src/test/java` and optional `src/test/resources` below that suite root,
    plus a `suite.json` recording the true `coordinates` being improved.
  - Prepare source context for main sources, upstream tests, and documentation
    when available.
  - Record baseline facts without mutating metadata-generation tests.
- Artifacts:
  - `runtime/code-coverage/prepare/library.json`
  - `runtime/code-coverage/prepare/source-context.md`
  - `runtime/code-coverage/prepare/baseline.md`
  - `runtime/code-coverage/work/code-coverage-prepare.md`

### Task code-coverage-api-inventory: Generate API inventory
**State:** prepared
**Prior:** Task code-coverage-prepare

- Helper script: `forge/utility_scripts/code_coverage_api_inventory.py`
- Purpose: deterministically describe public user-callable API targets for the
  coordinate.
- Required work:
  - Use the prepared library record and source context.
  - Generate compact JSON and Markdown reports under
    `runtime/code-coverage/api-inventory/`.
  - Make the canonical target `id` carry the full target identity; avoid
    redundant split fields unless needed for stable processing.
  - Include public constructors, instance/static methods, generated enum
    accessor methods, builders, configuration, parsing, serialization,
    adapters, lifecycle methods, and error-handling calls. Exclude fields.
  - Do not prioritize private implementation details as direct test targets.
- Artifacts:
  - `runtime/code-coverage/api-inventory/api-inventory.json`
  - `runtime/code-coverage/api-inventory/api-inventory.md`
  - `runtime/code-coverage/work/code-coverage-api-inventory.md`

### Task code-coverage-api-coverage: API coverage loop
**State:** api-measure
**Prior:** Task code-coverage-api-inventory

- Measurement program: `forge/utility_scripts/code_coverage_validate.py`
  (JVM JaCoCo run plus exact API-inventory correlation), driven by the
  `api-measure` state.
- Fixed report location: `runtime/code-coverage/validation/api-cover-report.json`
  (iteration history stays at `api-cover-report-<n>.json`).
- Prompt location: `runtime/code-coverage/prompts/api-cover-prompt.md`, derived
  by the measurement program from the fixed report when the loop continues.
- Loop: measure -> cover -> measure. Measurement always writes the
  report to the fixed location and lists only exact JaCoCo-uncovered
  public targets in the prompt; the cover agent generates meaningful behavior tests in the
  dedicated coverage suite and always returns to measurement. The phase
  completes when no uncovered public target remains or the iteration budget is
  spent. Only re-measurement moves the loop forward; the agent cannot claim
  coverage.

### Task code-coverage-prepare-native-metadata: Prepare native metadata
**State:** prepared
**Prior:** Task code-coverage-api-coverage

- Helper script: `forge/utility_scripts/code_coverage_prepare_native_metadata.py`
- Invoke it with the resolved `--repo-path`, `--coordinate`, absolute
  `--coverage-suite`, preparation `--output-dir`, and bounded
  `--max-fix-passes`.
- Purpose: generate and repair reachability metadata once after public API
  coverage so the deep sampled-PGO builds can run
  §WF-code-coverage-improvement.
- Required work:
  - Read the resolved coordinate and absolute suite root from the conversion
    and preparation artifacts.
  - Generate metadata with `./gradlew generateMetadata -Pcoordinates=<resolved coordinate> -PincludeCodeCoverageSuite=true`.
  - Run `./gradlew nativeTest -Pcoordinates=<resolved coordinate> -PincludeCodeCoverageSuite=true`; if it fails, repair
    metadata with the Codex `fix-missing-reachability-metadata` skill and re-run,
    up to the helper's fix budget.
  - If Native Image validation cannot be repaired automatically, request
    `human-intervention`.
- Artifacts:
  - `runtime/code-coverage/prepare/native-metadata-prepare.json`
  - `runtime/code-coverage/prepare/native-metadata-prepare.md`
  - `runtime/code-coverage/work/code-coverage-prepare-native-metadata.md`

### Task code-coverage-deep-coverage: Deep coverage loop
**State:** deep-measure
**Prior:** Task code-coverage-prepare-native-metadata

- Measurement program: `jacocoCodeCoverageReport`, `nativeTestPGOSampling`,
  `runNativeTestPGO` (both native tasks with `-PincludeCodeCoverageSuite=true`),
  one coherent call-tree CSV triplet, then
  `forge/utility_scripts/code_coverage_profile_report.py`, driven by the
  `deep-measure` state.
- Fixed report location: `runtime/code-coverage/discovery/discovery-report.json`
  (iteration history stays at `discovery-report-<n>.json`).
- Prompt location: `runtime/code-coverage/prompts/deep-cover-prompt.md`, taken
  by the measurement program from the analyzer's compact
  `Observed` / `Uncovered paths` Markdown when the loop continues.
- Target state: measurement-owned. Attempt counts and rotation are carried
  deterministically in the `discovery-report-<n>.json` history; every target
  prompted in one iteration is deprioritized at the next. The cover agent
  writes no target state.
- Loop: measure -> cover -> measure, exactly like the API loop.
  JaCoCo is the sole coverage authority; sampled PGO and the static call graph
  provide navigation only. The phase completes when no actionable target
  remains or the iteration budget is spent.

### Task code-coverage-finalization: Finalize validation and metrics
**State:** reviewed-prepared
**Prior:** Task code-coverage-deep-coverage

- Final API report: highest-iteration `runtime/code-coverage/validation/api-cover-report-<n>.json`
- Final deep report: highest-iteration `runtime/code-coverage/discovery/discovery-report-<n>.json`
- Helper script: `forge/utility_scripts/code_coverage_finalize.py`
- Purpose: gate publication on deterministic post-loop validation and
  summarize separate JaCoCo results and sampled-path guidance.
- Execution: this task is a deterministic program (the `reviewed-execute`
  state), not an agent checklist. A nonzero exit code is the number of the
  failed step and routes to `finalize-fix`. Finalization runs no Native Image
  validation at this stage.
- Program steps:
  1. Read `runtime/code-coverage/issues/conversion.json` for the resolved
     coordinate, worktree, work path, and coverage suite paths.
  2. Run checkstyle over the coordinate's subprojects, including the tracked
     coverage suite source set:
     `./gradlew checkstyle -Pcoordinates=<resolved coordinate> --stacktrace`.
  3. Run the regular JVM tests and the tracked extension suite:
     `./gradlew javaTest -Pcoordinates=<resolved coordinate> --stacktrace` and
     `./gradlew codeCoverageTest -Pcoordinates=<resolved coordinate> --stacktrace`.
  4. Invoke `forge/utility_scripts/code_coverage_finalize.py` with the resolved
     `--coordinate`, repository-relative `--coverage-suite-path`, the API
     baseline/final reports (`api-cover-report-0.json` and the highest-iteration report),
     the deep baseline/final reports (`discovery-report-0.json` and the
     highest-iteration report), any externally provided
     `runtime/code-coverage/targets/*.json` as repeated `--target-state`
     arguments (the workflow itself no longer produces them), the exact
     checkstyle and JVM test commands as repeated `--validation-command`s, and
     `--output-dir runtime/code-coverage/finalization`.
- Verification: the `finalize-verify` program then schema-validates
  `final-metrics.json` (`code_coverage_final_metrics` alias) and requires
  `final-summary.md`; failed targets or `needsHumanIntervention` route to
  `human-intervention` instead of publication.
- Artifacts:
  - `runtime/code-coverage/finalization/final-summary.md`
  - `runtime/code-coverage/finalization/final-metrics.json`

### Task code-coverage-publication: Publish the verified branch
**State:** prepared
**Prior:** Task code-coverage-finalization

- Helper script: `forge/git_scripts/publish_code_coverage_improvement.py`
- Worker agent: `pi[high]:openai-codex/gpt-5.6-luna`
- Branch suffix: ``
- Purpose: push the verified code coverage improvement as a publication branch
  that trusted GitHub Actions turn into a pull request. This task does not open
  the pull request and must never call `gh pr create`
  (§AR-forge-verification-publication-boundary).
- Required work:
  - Read `runtime/code-coverage/finalization/final-summary.md` and
    `runtime/code-coverage/finalization/final-metrics.json`.
  - Confirm the issue worktree branch is the expected issue branch.
  - Leave verified changes uncommitted or committed; the helper stages the
    coverage suite and touched metadata itself and commits them.
  - Run the helper with `--repo-path`, `--coordinate`, `--issue-number`,
    `--finalization-dir`, `--coverage-suite-path`, and
    `--worker-agent pi[high]:openai-codex/gpt-5.6-luna`. The helper names the head branch after
    that target's model, so a run of this coordinate on another model owns a
    different branch.
  - Pass `--branch-suffix ` when that value is non-empty. It
    only labels which run a branch belongs to; the publication ID the helper
    appends already keeps two runs of one coordinate and model apart.
  - The helper rebases onto upstream `master`, runs the pre-publication
    verification gate, writes
    `stats/<group>/<artifact>/<version>/forge-publication.json`, and pushes the
    `ai/<login>/...` branch to `oracle/graalvm-reachability-metadata`. Pushing that branch is the whole
    task: `Forge Branch Ready` validates the exact commit as data, and only its
    success lets `Forge Open PR` render the body and open the pull request
    (§GIT-actions-publication).
  - The descriptor carries the coordinate, coverage suite path, separate
    baseline and final API/deep JaCoCo coverage, the human-intervention flag,
    the issue-resolution flag, the generating model, and per-phase token usage.
    The trusted renderer writes every section of the body from it. Do not
    hand-write a pull request body: a section an agent types is one no run
    publishes.
  - Report the pushed branch name in the work artifact.
- Artifacts:
  - `runtime/code-coverage/publication/branch.md`
  - `runtime/code-coverage/work/code-coverage-8380.code-coverage-publication.md`
