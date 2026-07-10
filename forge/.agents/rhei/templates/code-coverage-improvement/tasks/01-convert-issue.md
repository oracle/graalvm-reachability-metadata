### Task code-coverage-convert: Convert issue {{issue_number}}
**State:** simple-prepared

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
- Artifacts:
  - `runtime/code-coverage/issues/inventory.md`
  - `runtime/code-coverage/issues/conversion.md`
  - `runtime/code-coverage/work/code-coverage-convert.md`

### Task code-coverage-prepare: Prepare library
**State:** simple-prepared
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
  - Create or verify
    `tests/<group>/<artifact>/<version>/code-coverage` in the issue worktree.
  - Prepare source context for main sources, upstream tests, and documentation
    when available.
  - Record baseline facts without mutating metadata-generation tests.
- Artifacts:
  - `runtime/code-coverage/prepare/library.json`
  - `runtime/code-coverage/prepare/source-context.md`
  - `runtime/code-coverage/prepare/baseline.md`
  - `runtime/code-coverage/work/code-coverage-prepare.md`

### Task code-coverage-api-inventory: Generate API inventory
**State:** simple-prepared
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
  - Include public classes, constructors, methods, static methods, enum APIs,
    builder/configuration APIs, parser/serializer/adapter APIs, lifecycle APIs,
    and error-handling APIs reachable through public methods.
  - Do not prioritize private implementation details as direct test targets.
- Artifacts:
  - `runtime/code-coverage/api-inventory/api-inventory.json`
  - `runtime/code-coverage/api-inventory/api-inventory.md`
  - `runtime/code-coverage/work/code-coverage-api-inventory.md`

### Task code-coverage-api-cover-1: Cover API inventory targets, iteration 1
**State:** prepared
**Prior:** Task code-coverage-api-inventory

- Target report: `runtime/code-coverage/api-inventory/api-inventory.json`
- Coverage suite: `tests/<group>/<artifact>/<version>/code-coverage`
- Purpose: add or refine tests for uncovered public API targets through normal
  public usage.
- Required work:
  - Select a bounded batch of pending API inventory targets.
  - Add meaningful behavior tests only under the coverage suite.
  - Match the repository's existing test style.
  - Do not call private internals only to increase coverage.
  - If a target is not meaningfully coverable, write a skipped-target note with
    the reason.
- Artifacts:
  - `runtime/code-coverage/targets/api-cover-1.md`
  - `runtime/code-coverage/work/code-coverage-api-cover-1.md`

### Task code-coverage-test-validate-1: Validate API cover iteration 1
**State:** prepared
**Prior:** Task code-coverage-api-cover-1

- Helper script: `forge/utility_scripts/code_coverage_validate.py`
- Purpose: compile tests, run JVM tests under JaCoCo, and correlate JaCoCo
  evidence with the API inventory. This phase is JVM-only; reachability metadata
  is prepared once before PGO discovery, not here.
- Required work:
  - Run Java compilation for the coordinate.
  - Run JVM tests under JaCoCo agent instrumentation.
  - Correlate JaCoCo method/line coverage against the API inventory and report
    covered vs still-uncovered public API targets.
- Artifacts:
  - `runtime/code-coverage/validation/jacoco-1.xml`
  - `runtime/code-coverage/validation/api-cover-report-1.md`
  - `runtime/code-coverage/validation/api-cover-report-1.json`
  - `runtime/code-coverage/work/code-coverage-test-validate-1.md`

### Task code-coverage-api-cover-2: Cover API inventory targets, iteration 2
**State:** prepared
**Prior:** Task code-coverage-test-validate-1

- Target report: `runtime/code-coverage/validation/api-cover-report-1.json`
- Coverage suite: `tests/<group>/<artifact>/<version>/code-coverage`
- Purpose: continue API target coverage from the first validation report.
- Required work:
  - If no public API targets remain uncovered, record a no-op completion note.
  - Otherwise select a bounded batch of still-uncovered API targets.
  - Add meaningful behavior tests only under the coverage suite.
  - Record completed, skipped, exhausted, and failed targets.
- Artifacts:
  - `runtime/code-coverage/targets/api-cover-2.md`
  - `runtime/code-coverage/work/code-coverage-api-cover-2.md`

### Task code-coverage-test-validate-2: Validate API cover iteration 2
**State:** prepared
**Prior:** Task code-coverage-api-cover-2

- Helper script: `forge/utility_scripts/code_coverage_validate.py`
- Purpose: repeat the JVM-only JaCoCo validation after the second API-cover pass.
- Required work:
  - Run Java compilation for the coordinate.
  - Run JVM tests under JaCoCo and correlate against the API inventory.
- Artifacts:
  - `runtime/code-coverage/validation/jacoco-2.xml`
  - `runtime/code-coverage/validation/api-cover-report-2.md`
  - `runtime/code-coverage/validation/api-cover-report-2.json`
  - `runtime/code-coverage/work/code-coverage-test-validate-2.md`

### Task code-coverage-api-cover-3: Cover API inventory targets, iteration 3
**State:** prepared
**Prior:** Task code-coverage-test-validate-2

- Target report: `runtime/code-coverage/validation/api-cover-report-2.json`
- Coverage suite: `tests/<group>/<artifact>/<version>/code-coverage`
- Purpose: final bounded API target coverage pass.
- Required work:
  - If no public API targets remain uncovered, record a no-op completion note.
  - Otherwise select a bounded batch of still-uncovered API targets.
  - Add meaningful behavior tests only under the coverage suite.
  - Record completed, skipped, exhausted, and failed targets.
- Artifacts:
  - `runtime/code-coverage/targets/api-cover-3.md`
  - `runtime/code-coverage/work/code-coverage-api-cover-3.md`

### Task code-coverage-test-validate-3: Validate API cover iteration 3
**State:** prepared
**Prior:** Task code-coverage-api-cover-3

- Helper script: `forge/utility_scripts/code_coverage_validate.py`
- Purpose: produce the final JVM-only JaCoCo API-cover report before native
  metadata preparation and PGO discovery.
- Required work:
  - Run Java compilation for the coordinate.
  - Run JVM tests under JaCoCo and correlate against the API inventory.
- Artifacts:
  - `runtime/code-coverage/validation/jacoco-3.xml`
  - `runtime/code-coverage/validation/api-cover-report-3.md`
  - `runtime/code-coverage/validation/api-cover-report-3.json`
  - `runtime/code-coverage/work/code-coverage-test-validate-3.md`

### Task code-coverage-prepare-native-metadata: Prepare native metadata
**State:** prepared
**Prior:** Task code-coverage-test-validate-3

- Helper script: `forge/utility_scripts/code_coverage_prepare_native_metadata.py`
- Purpose: generate and repair reachability metadata once so the PGO-sampling
  Native Image builds succeed, keeping metadata work out of the JVM JaCoCo
  phase and out of every PGO iteration §WF-code-coverage-improvement.
- Required work:
  - Generate metadata with `./gradlew generateMetadata -Pcoordinates={{coordinate}}`.
  - Run `./gradlew nativeTest -Pcoordinates={{coordinate}}`; if it fails, repair
    metadata with the Codex `fix-missing-reachability-metadata` skill and re-run,
    up to the helper's fix budget.
  - If Native Image validation cannot be repaired automatically, request
    `human-intervention`.
- Artifacts:
  - `runtime/code-coverage/prepare/native-metadata-prepare.json`
  - `runtime/code-coverage/prepare/native-metadata-prepare.md`
  - `runtime/code-coverage/work/code-coverage-prepare-native-metadata.md`

### Task code-coverage-pgo-report-1: Generate PGO near-call report 1
**State:** simple-prepared
**Prior:** Task code-coverage-prepare-native-metadata

- Helper script: `forge/utility_scripts/code_coverage_profile_report.py`
- Collection tasks: `./gradlew nativeTestPGOSampling -Pcoordinates={{coordinate}}`
  then `./gradlew runNativeTestPGO -Pcoordinates={{coordinate}} -PpgoProfilePath=<abs .iprof>`.
- Purpose: collect sampled Native Image PGO evidence and rank uncovered public
  API inventory targets by near-call distance.
- Required work:
  - Build the PGO-sampling image and collect a sampled `.iprof` profile with
    `runNativeTestPGO`; profiles without a `samplingProfiles` section are
    rejected.
  - Read the analysis call-tree CSV dump
    (`reports/call_tree_{methods,invokes,targets}_*.csv`) emitted by the
    `nativeTestPGOSampling` build.
  - Take covered/uncovered statuses from the latest JaCoCo api-cover report;
    sampled evidence is guidance only and never proves non-execution.
  - For each uncovered public API method, compute the near-call record: the
    shortest static call-graph path from a sampled stack frame (or from the
    nearest public API entry when no sampled frame joins) to the target.
  - Rank targets nearest-first, group by owning class, and list at most 100
    methods; attach detailed sampled-stack/static-path/divergence guidance for
    the top targets.
  - Emit LCOV plus prompt-ready JSON and Markdown reports.
- Artifacts:
  - `runtime/code-coverage/discovery/pgo-profile-1.iprof`
  - `runtime/code-coverage/discovery/call-tree-1/` (copied `call_tree_*.csv`)
  - `runtime/code-coverage/discovery/coverage-1.lcov`
  - `runtime/code-coverage/discovery/discovery-report-1.json`
  - `runtime/code-coverage/discovery/discovery-report-1.md`
  - `runtime/code-coverage/work/code-coverage-pgo-report-1.md`

### Task code-coverage-discovery-cover-1: Bulk-cover uncovered API methods, iteration 1
**State:** prepared
**Prior:** Task code-coverage-pgo-report-1

- Discovery report: `runtime/code-coverage/discovery/discovery-report-1.json`
- Coverage suite: `tests/<group>/<artifact>/<version>/code-coverage`
- Purpose: cover the listed uncovered public API methods in bulk.
- Required work:
  - Work from the report's bulk list (at most 100 methods, nearest-first,
    grouped by owning class).
  - Batch related methods per owning class or shared entry path; add tests that
    drive the public entry and cover as much of each batch as possible.
  - Use the detailed near-call guidance to steer execution from the sampled
    join point toward each target.
  - Prefer broad behavior coverage per iteration over superficial invocations.
  - Mark statically-reachable-but-infeasible targets skipped or exhausted with a
    concrete reason.
- Artifacts:
  - `runtime/code-coverage/targets/discovery-cover-1.md`
  - `runtime/code-coverage/work/code-coverage-discovery-cover-1.md`

### Task code-coverage-pgo-report-2: Generate PGO near-call report 2
**State:** simple-prepared
**Prior:** Task code-coverage-discovery-cover-1

- Helper script: `forge/utility_scripts/code_coverage_profile_report.py`
- Purpose: refresh the near-call ranking after the first bulk cover pass.
- Required work: refresh the JaCoCo api-cover report with the JVM validate
  helper so covered statuses stay exact, then repeat the near-call requirements
  from report 1 and compare progress with the prior report.
- Artifacts:
  - `runtime/code-coverage/discovery/pgo-profile-2.iprof`
  - `runtime/code-coverage/discovery/call-tree-2/` (copied `call_tree_*.csv`)
  - `runtime/code-coverage/discovery/coverage-2.lcov`
  - `runtime/code-coverage/discovery/discovery-report-2.json`
  - `runtime/code-coverage/discovery/discovery-report-2.md`
  - `runtime/code-coverage/work/code-coverage-pgo-report-2.md`

### Task code-coverage-discovery-cover-2: Bulk-cover uncovered API methods, iteration 2
**State:** prepared
**Prior:** Task code-coverage-pgo-report-2

- Discovery report: `runtime/code-coverage/discovery/discovery-report-2.json`
- Coverage suite: `tests/<group>/<artifact>/<version>/code-coverage`
- Purpose: continue bulk coverage for the remaining listed uncovered public
  API methods.
- Required work:
  - If no actionable discovery targets remain, record a no-op completion note.
  - Otherwise group remaining targets and add meaningful public API tests.
  - Record completed, skipped, exhausted, and failed targets.
- Artifacts:
  - `runtime/code-coverage/targets/discovery-cover-2.md`
  - `runtime/code-coverage/work/code-coverage-discovery-cover-2.md`

### Task code-coverage-pgo-report-3: Generate PGO near-call report 3
**State:** simple-prepared
**Prior:** Task code-coverage-discovery-cover-2

- Helper script: `forge/utility_scripts/code_coverage_profile_report.py`
- Purpose: refresh the near-call ranking after the second bulk cover pass.
- Required work: refresh the JaCoCo api-cover report with the JVM validate
  helper so covered statuses stay exact, then repeat the near-call requirements
  from report 1 and compare progress with the prior report.
- Artifacts:
  - `runtime/code-coverage/discovery/pgo-profile-3.iprof`
  - `runtime/code-coverage/discovery/call-tree-3/` (copied `call_tree_*.csv`)
  - `runtime/code-coverage/discovery/coverage-3.lcov`
  - `runtime/code-coverage/discovery/discovery-report-3.json`
  - `runtime/code-coverage/discovery/discovery-report-3.md`
  - `runtime/code-coverage/work/code-coverage-pgo-report-3.md`

### Task code-coverage-discovery-cover-3: Bulk-cover uncovered API methods, iteration 3
**State:** prepared
**Prior:** Task code-coverage-pgo-report-3

- Discovery report: `runtime/code-coverage/discovery/discovery-report-3.json`
- Coverage suite: `tests/<group>/<artifact>/<version>/code-coverage`
- Purpose: final bounded discovery coverage pass.
- Required work:
  - If no actionable discovery targets remain, record a no-op completion note.
  - Otherwise group remaining targets and add meaningful public API tests.
  - Record completed, skipped, exhausted, and failed targets.
- Artifacts:
  - `runtime/code-coverage/targets/discovery-cover-3.md`
  - `runtime/code-coverage/work/code-coverage-discovery-cover-3.md`

### Task code-coverage-finalization: Finalize validation and metrics
**State:** simple-prepared
**Prior:** Task code-coverage-discovery-cover-3

- Helper preference: use Forge local verification and coverage helpers.
- Purpose: verify the final result against latest GraalVM and summarize
  baseline vs final code coverage evidence.
- Required work:
  - Run final Java compilation.
  - Run final JVM tests.
  - Run final Native Image tests.
  - Run final PGO coverage correlation.
  - Run metadata validity checks.
  - Compare baseline and final coverage.
  - Record validation commands exactly as run.
- Artifacts:
  - `runtime/code-coverage/finalization/final-summary.md`
  - `runtime/code-coverage/finalization/final-metrics.json`
  - `runtime/code-coverage/work/code-coverage-finalization.md`

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
  - Include source issue, coordinate, coverage suite path, baseline coverage,
    final coverage, coverage delta, completed targets, skipped or exhausted
    targets, and validation commands in the PR body.
  - Use a closing keyword only when the PR fully resolves the source issue;
    otherwise link without auto-closing and describe remaining follow-up.
- Artifacts:
  - `runtime/code-coverage/publication/pr.md`
  - `runtime/code-coverage/work/code-coverage-publication.md`
