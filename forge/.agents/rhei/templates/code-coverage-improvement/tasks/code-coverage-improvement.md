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
  worktree for the code coverage workflow §WF-code-coverage-improvement.
- Required work:
  - Fetch the issue with `gh issue view {{issue_number}} --repo {{repo}}`.
  - Verify that it carries `{{issue_label}}`.
  - If `{{coordinate}}` is non-empty, use it as the coordinate; otherwise parse
    exactly one `group:artifact:version` coordinate from the issue body.
  - If `{{project_owner}}` and `{{project_number}}` are non-empty, verify the
    issue's Project item is in `{{todo_status}}`, then move it to
    `{{in_progress_status}}` only after the worktree and conversion artifacts
    are written.
  - Create or reuse one worktree below `{{worktree_root}}` for the issue using a
    branch name like `rhei/code-coverage-issue-{{issue_number}}-<slug>`.
  - Record the resolved worktree and work path, where work path is the worktree
    joined with `{{work_subdir}}`.
  - Write `runtime/code-coverage/issues/conversion.json` with exactly these
    fields: `coordinate`, `worktreePath`, `workPath`,
    `coverageSuiteAbsolutePath`, and `coverageSuiteRepoRelativePath`
    (`tests/<group>/<artifact>/<version>/code-coverage`). The deterministic
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
  - Create or verify the dedicated suite at
    `tests/<group>/<artifact>/<version>/code-coverage`, including
    `src/test/java` and optional `src/test/resources` below that suite root.
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
  - Generate metadata with `./gradlew generateMetadata -Pcoordinates=<resolved coordinate> -PcodeCoverageSuitePath=<absolute suite root>`.
  - Run `./gradlew nativeTest -Pcoordinates=<resolved coordinate> -PcodeCoverageSuitePath=<absolute suite root>`; if it fails, repair
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

- Measurement program: `jacocoTestReport`, `nativeTestPGOSampling`,
  `runNativeTestPGO`, one coherent call-tree CSV triplet, then
  `forge/utility_scripts/code_coverage_profile_report.py`, driven by the
  `deep-measure` state.
- Fixed report location: `runtime/code-coverage/discovery/discovery-report.json`
  (iteration history stays at `discovery-report-<n>.json`).
- Prompt location: `runtime/code-coverage/prompts/deep-cover-prompt.md`, taken
  by the measurement program from the analyzer's compact
  `Observed` / `Uncovered paths` Markdown when the loop continues.
- Target state: `runtime/code-coverage/targets/deep-cover.json`
  (schema `code_coverage_target_state`), updated by the cover agent and folded
  into every re-measurement so exhausted targets rotate out of the prompt.
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
- Purpose: gate publication on deterministic, post-iteration-five validation and
  summarize separate JaCoCo results and sampled-path guidance.
- Execution: this task is a deterministic program (the `reviewed-execute`
  state), not an agent checklist. A nonzero exit code is the number of the
  failed step and routes to `finalize-fix`. Finalization runs no Native Image
  validation at this stage.
- Program steps:
  1. Read `runtime/code-coverage/issues/conversion.json` for the resolved
     coordinate, worktree, work path, and coverage suite paths.
  2. Run the JVM tests with the dedicated coverage suite:
     `./gradlew javaTest -Pcoordinates=<resolved coordinate> -PcodeCoverageSuitePath=<absolute suite root> --stacktrace`.
  3. Invoke `forge/utility_scripts/code_coverage_finalize.py` with the resolved
     `--coordinate`, repository-relative `--coverage-suite-path`, the API
     baseline/final reports (`api-cover-report-0.json` and the highest-iteration report),
     the deep baseline/final reports (`discovery-report-0.json` and the
     highest-iteration report), every `runtime/code-coverage/targets/*.json`
     as repeated `--target-state` arguments, the exact JVM
     test command as `--validation-command`, and
     `--output-dir runtime/code-coverage/finalization`.
- Verification: the `finalize-verify` program then schema-validates
  `final-metrics.json` (`code_coverage_final_metrics` alias) and requires
  `final-summary.md`; failed targets or `needsHumanIntervention` route to
  `human-intervention` instead of publication.
- Artifacts:
  - `runtime/code-coverage/finalization/final-summary.md`
  - `runtime/code-coverage/finalization/final-metrics.json`

### Task code-coverage-publication: Publish pull request
**State:** prepared
**Prior:** Task code-coverage-finalization

- Helper script: `forge/git_scripts/make_pr_code_coverage_improvement.py`
- PR push remote: `{{pr_push_remote}}`
- PR head owner: `{{pr_head_owner}}`
- PR base branch: `{{pr_base_branch}}`
- Purpose: publish the verified code coverage improvement as a pull request.
- Required work:
  - Read `runtime/code-coverage/finalization/final-summary.md` and
    `runtime/code-coverage/finalization/final-metrics.json`.
  - Confirm the issue worktree branch is the expected issue branch.
  - Create a focused commit if verified changes are uncommitted.
  - Push to the configured fork remote or infer a writable fork remote.
  - Open a pull request against `{{repo}}` base `{{pr_base_branch}}`.
  - Include source issue, coordinate, coverage suite path, separate baseline and
    final API/deep JaCoCo coverage, coverage deltas, sampled guidance evidence,
    completed, skipped, exhausted, or failed targets, and validation commands
    in the PR body.
  - Use a closing keyword only when the PR fully resolves the source issue;
    otherwise link without auto-closing and describe remaining follow-up.
- Artifacts:
  - `runtime/code-coverage/publication/pr.md`
  - `runtime/code-coverage/work/code-coverage-publication.md`
