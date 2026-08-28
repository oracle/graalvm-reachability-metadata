### Task code-coverage-convert: Convert issue {{issue_number}}
**State:** prepared

- Source issue: `https://github.com/{{repo}}/issues/{{issue_number}}`
- Repository: `{{repo}}`
- Required label: `{{issue_label}}`
- Coordinate override: `{{coordinate}}`
- Source checkout: `{{repo_checkout}}`
- Worktree root: `{{worktree_root}}`
- Work subdirectory: `{{work_subdir}}`
- Project owner: `{{project_owner}}`
- Project number: `{{project_number}}`
- Purpose: fetch one `{{issue_label}}` issue and create or reuse the per-issue
  worktree for the code coverage workflow §AR-code-coverage-improvement.
- Required work:
  - Fetch the issue with `gh issue view {{issue_number}} --repo {{repo}}`.
  - Verify that it carries `{{issue_label}}`.
  - If `{{coordinate}}` is non-empty, use it as the coordinate; otherwise parse
    exactly one `group:artifact:version` coordinate from the issue title.
  - If `{{project_owner}}` and `{{project_number}}` are non-empty, verify the
    issue's Project item is in `{{todo_status}}`, then move it to
    `{{in_progress_status}}` only after the worktree and conversion artifacts
    are written.
  - Create or reuse one worktree below `{{worktree_root}}` for the issue using a
    branch name like `rhei/code-coverage-issue-{{issue_number}}-<slug>`, with
    its branch created from the HEAD of `{{repo_checkout}}` — never from
    `origin/master`. The workflow's own measurement helpers are resolved from
    the issue worktree, so a base without them fails measurement outright
    (§AR-code-coverage-improvement.2).
  - Record the resolved worktree and work path, where work path is the worktree
    joined with `{{work_subdir}}`.
  - Write `runtime/code-coverage/issues/conversion.json` with exactly these
    fields: `coordinate`, `worktreePath`, `workPath`,
    `coverageSuiteAbsolutePath`, and `coverageSuiteRepoRelativePath`
    (`tests/src/<group>/<artifact>/<version>/code-coverage-improvement`). The
    deterministic finalization program reads this record; all paths except the
    repository-relative suite path must be absolute.
- Artifacts:
  - `runtime/code-coverage/issues/inventory.md`
  - `runtime/code-coverage/issues/conversion.md`
  - `runtime/code-coverage/issues/conversion.json`
  - `runtime/code-coverage/work/code-coverage-{{issue_number}}.code-coverage-convert.md`

### Task code-coverage-prepare: Prepare library
**State:** prepared
**Prior:** Task code-coverage-convert

- Source artifact: `runtime/code-coverage/issues/conversion.md`
- Helper preference: reuse Forge path and source-context helpers before adding
  task-specific setup logic.
- Purpose: create or verify the dedicated code coverage test suite and prepare
  its context for the launcher-validated coordinate.
- Required work:
  - Read the coordinate and canonical coverage-suite paths from the conversion
    artifact.
  - Create or verify the tracked extension suite at
    `coverageSuiteAbsolutePath`, including `src/test/java` and optional
    `src/test/resources` below that suite root, plus a `suite.json` recording
    the true `coordinates` being improved.
  - Prepare source context for main sources, upstream tests, and documentation
    when available.
  - Record baseline facts without mutating metadata-generation tests.
- Artifacts:
  - `runtime/code-coverage/prepare/library.json`
  - `runtime/code-coverage/prepare/source-context.md`
  - `runtime/code-coverage/prepare/baseline.md`
  - `runtime/code-coverage/work/code-coverage-{{issue_number}}.code-coverage-prepare.md`

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
  - Record the resolved library jars as `libraryJars` so the later bytecode
    call-graph extraction resolves no artifacts of its own.
- Artifacts:
  - `runtime/code-coverage/api-inventory/api-inventory.json`
  - `runtime/code-coverage/api-inventory/api-inventory.md`
  - `runtime/code-coverage/work/code-coverage-{{issue_number}}.code-coverage-api-inventory.md`

### Task code-coverage-api-coverage: API coverage loop
**State:** api-measure
**Prior:** Task code-coverage-api-inventory

- Measurement programs, driven by the `api-measure` state in numbered steps:
  - `forge/utility_scripts/code_coverage_validate.py` — JVM JaCoCo run plus
    exact API-inventory correlation (step 2).
  - `forge/utility_scripts/java/CallGraphExtractor.java` — bytecode call graph,
    built once from `libraryJars` and cached under
    `runtime/code-coverage/graph/` (step 3).
  - `forge/utility_scripts/code_coverage_api_rank.py` — orders the uncovered
    public entries by how much still-uncovered code each unlocks and renders the
    prompt, filled to its 400-target cap (step 4).
- Fixed report location: `runtime/code-coverage/validation/api-cover-report.json`
  (iteration history stays at `api-cover-report-<n>.json`); ranking evidence
  stays at `api-rank-<n>.json`.
- Prompt location: `runtime/code-coverage/prompts/api-cover-prompt.md`, derived
  by the measurement program from the fixed report when the loop continues.
- Loop: measure -> cover -> measure. Measurement always writes the report to the
  fixed location and lists only exact JaCoCo-uncovered public targets in the
  prompt, ordered by unlocked internal code rather than by identifier
  (§AR-code-coverage-improvement.3.1.1); the cover agent generates meaningful
  behavior tests in the dedicated coverage suite and always returns to
  measurement. The phase completes when no uncovered public target remains or
  the iteration budget is spent. Only re-measurement moves the loop forward;
  the agent cannot claim coverage.

### Task code-coverage-prepare-native-metadata: Prepare native metadata
**State:** prepared
**Prior:** Task code-coverage-api-coverage

- Helper script: `forge/utility_scripts/code_coverage_prepare_native_metadata.py`
- Invoke it with the resolved `--repo-path`, `--coordinate`, absolute
  `--coverage-suite`, preparation `--output-dir`, and bounded
  `--max-fix-passes`.
- Purpose: generate and repair reachability metadata once after public API
  coverage so the deep sampled-PGO builds can run
  §AR-code-coverage-improvement.
- Required work:
  - Read the resolved coordinate and absolute suite root from the conversion
    and preparation artifacts.
  - Generate metadata with `./gradlew generateMetadata -Pcoordinates=<resolved coordinate> -PincludeCodeCoverageSuite=true`.
  - Metadata lands in the coordinate's existing `reachability-metadata.json`
    files and nowhere else. The coverage suite has no metadata directory of its
    own, and the legacy split-config files a tracing agent may emit
    (`jni-config.json`, `reflect-config.json`, `resource-config.json`,
    `serialization-config.json`, `proxy-config.json`) are input to convert, not
    output to commit — this repository loads none of them
    (§root/FS-metadata.1, §AR-code-coverage-improvement.2). Do not split shipped
    from test-only entries by hand: finalization runs `splitTestOnlyMetadata`.
  - Run `./gradlew test -Pcoordinates=<resolved coordinate> -PincludeCodeCoverageSuite=true`; if it fails, repair
    metadata with the Codex `fix-missing-reachability-metadata` skill and re-run,
    up to the helper's fix budget.
  - If Native Image validation cannot be repaired automatically, request
    `human-intervention`.
- Artifacts:
  - `runtime/code-coverage/prepare/native-metadata-prepare.json`
  - `runtime/code-coverage/prepare/native-metadata-prepare.md`
  - `runtime/code-coverage/work/code-coverage-{{issue_number}}.code-coverage-prepare-native-metadata.md`

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
- Target universe: JaCoCo methods that are neither API inventory entries nor
  absent from `runtime/code-coverage/graph/methods.csv`. The method list keeps
  the library's own `test`-classifier classes out of the universe, since JaCoCo
  reports them in the library's own packages
  (§AR-code-coverage-improvement.3.2).
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
- Purpose: gate publication on deterministic post-loop validation, update the
  committed library coverage stats, and summarize JaCoCo and sampled-path
  guidance.
- Execution: this task is a deterministic program (the `reviewed-execute`
  state), not an agent checklist. A nonzero exit code is the number of the
  failed step and routes to `finalize-fix`. Finalization runs no Native Image
  validation of the coverage tests; the stats step (5) does build a native image,
  because the dynamic-access half of `stats.json` is only observable from one.
- Program steps:
  1. Read `runtime/code-coverage/issues/conversion.json` for the resolved
     coordinate, worktree, work path, and coverage suite paths.
  2. Separate test-only metadata from shipped metadata and validate what
     remains:
     `./gradlew splitTestOnlyMetadata -Pcoordinates=<resolved coordinate> --stacktrace`
     then
     `./gradlew checkMetadataFiles -Pcoordinates=<resolved coordinate> --stacktrace`,
     followed by the legacy config policy check from
     `forge/utility_scripts/native_image_config_policy.py` over the coordinate's
     test project. The extension suite contributes to the same two
     `reachability-metadata.json` files as every other test and owns no metadata
     directory of its own; the split is by the entry's own type, never by hand
     (§AR-code-coverage-improvement.2, §root/FS-metadata.2, §root/AR-test-harness.5).
  3. Run checkstyle over the coordinate's subprojects, including the tracked
     coverage suite source set:
     `./gradlew checkstyle -Pcoordinates=<resolved coordinate> --stacktrace`.
  4. Run the regular JVM tests and the tracked extension suite:
     `./gradlew javaTest -Pcoordinates=<resolved coordinate> --stacktrace` and
     `./gradlew codeCoverageTest -Pcoordinates=<resolved coordinate> --stacktrace`.
  5. Regenerate committed coverage statistics from the combined main-JAR-only
     report by running `./gradlew generateLibraryStats -Pcoordinates=<resolved coordinate> --stacktrace`
     (§root/AR-test-harness.8). The task re-runs the coverage report and builds
     the dynamic-access native image, so this is the step that dominates the
     program's wall clock; a failed native build degrades `dynamicAccess` to
     `N/A` rather than failing the step.
  6. Invoke `forge/utility_scripts/code_coverage_finalize.py` with the resolved
     `--coordinate`, repository-relative `--coverage-suite-path`, the API
     baseline/final reports (`api-cover-report-0.json` and the highest-iteration report),
     the deep baseline/final reports (`discovery-report-0.json` and the
     highest-iteration report), any externally provided
     `runtime/code-coverage/targets/*.json` as repeated `--target-state`
     arguments (the workflow itself no longer produces them), the exact
     checkstyle, JVM test, and stats commands as repeated
     `--validation-command`s, and
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
- Worker agent: `{{worker_agent}}`
- Branch suffix: `{{branch_suffix}}`
- Purpose: push the verified code coverage improvement as a publication branch
  that trusted GitHub Actions turn into a pull request. This task does not open
  the pull request and must never call `gh pr create`
  (§AR-forge-verification-publication-boundary).
- Required work:
  - Read `runtime/code-coverage/finalization/final-summary.md` and
    `runtime/code-coverage/finalization/final-metrics.json`.
  - Confirm the issue worktree branch is the expected issue branch.
  - Leave verified changes uncommitted or committed; the helper stages the
    coverage suite, touched metadata, and the regenerated coverage stats itself
    and commits them.
  - Run the helper with `--repo-path`, `--coordinate`, `--issue-number`,
    `--finalization-dir`, `--coverage-suite-path`, and
    `--worker-agent {{worker_agent}}`. The helper names the head branch after
    that target's model, so a run of this coordinate on another model owns a
    different branch.
  - Pass `--branch-suffix {{branch_suffix}}` when that value is non-empty. It
    only labels which run a branch belongs to; the publication ID the helper
    appends already keeps two runs of one coordinate and model apart.
  - The helper rebases onto upstream `master`, runs the pre-publication
    verification gate, writes
    `stats/<group>/<artifact>/<version>/forge-publication.json`, and pushes the
    `ai/<login>/...` branch to `{{repo}}`. Pushing that branch is the whole
    task: `Forge Branch Ready` validates the exact commit as data, and only its
    success lets `Forge Open PR` render the body and open the pull request
    (§AR-actions-publication).
  - The descriptor carries the coordinate, coverage suite path, the whole-run
    coverage checkpoints and phase gains on one shared denominator (§4.1), the
    per-phase JaCoCo records, the human-intervention flag, the generating model,
    and per-phase token usage.
    The trusted renderer writes every section of the body from it. Do not
    hand-write a pull request body: a section an agent types is one no run
    publishes.
  - Report the pushed branch name in the work artifact.
- Artifacts:
  - `runtime/code-coverage/publication/branch.md`
  - `runtime/code-coverage/work/code-coverage-{{issue_number}}.code-coverage-publication.md`
