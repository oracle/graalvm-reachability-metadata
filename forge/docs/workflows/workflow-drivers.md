# WF-forge-workflow-drivers: Workflow drivers

Workflow drivers are deterministic scripts under `ai_workflows/drivers/`
that run one already-selected Forge workflow. (Drivers are being moved into that
subdirectory by §ROADMAP-forge-ai-workflows-structure; the core workflow objects
they invoke live under `ai_workflows/core/`.) They are used for four things:

1. Translate a claimed issue or explicit CLI invocation into one isolated run:
   resolve repository and metrics paths, normalize the GraalVM/Java
   environment, create or select the feature branch, and establish checkpoints
   (§ORCH-forge-orchestration-spec).
2. Prepare deterministic context before any agent work begins: scaffold or copy
   test directories, populate artifact URLs, materialize source context, load
   the predefined strategy bundle, instantiate the workflow engine, and create
   the configured agent (§AR-forge-workflow-boundary).
3. Delegate issue-resolution behavior to the selected workflow engine. An
   workflow driver may perform setup and finalization, but it must not embed a
   competing workflow state machine for prompting, retrying, fallback, or
   terminal status selection (§WF-forge-workflow-engine).
4. Finalize the run after the workflow engine returns: run required metadata or
   quality gates owned by the driver, write schema-validated metrics, and
   return a terminal status to orchestration. PR publication remains outside
   workflow drivers and belongs to git scripts (§GIT-forge-publication).

Driver setup must be explicit Python logic, shared utility code, or
predefined strategy configuration (§STRAT-forge-predefined-strategy-contract).
Codex, Pi, or any other LLM agent receives prepared context and works on the
library-resolution task (§AR-forge-strategy-agent-boundary); it must not decide
driver setup policy (§AR-forge-workflow-boundary), directory layout, branch
setup, metrics paths, or other deterministic Forge plumbing during a generated
run, and every such step belongs in the durable session log
(§FS-durable-generation-logs).

## 1. Common preparation contract

Every issue-driven driver receives an already selected issue from
`forge_metadata.py`. The dispatcher (§AR-forge-control-plane) owns issue
scanning, label routing, claiming, project state, worktree selection, and PR
publication handoff (§ORCH-forge-orchestration-spec); the driver owns
deterministic preparation inside the selected worktree.

Before an agent is created, each driver must prepare these inputs in normal
Python code or shared utility code:

- Resolve the reachability repository and metrics roots.
- Normalize the Java/GraalVM environment required by Gradle and Native Image.
- Load and validate the requested predefined strategy, including workflow,
  model, agent, MCP, and source-context parameters.
- Create or select the feature branch for the target coordinate.
- Resolve the target test and metadata directories.
- Capture a checkpoint commit or baseline snapshot that lets failure handling
  distinguish setup output from generated work.
- Populate artifact URLs and prepare any requested source context before prompt
  execution.
- Build the workflow strategy object with the resolved paths, coordinate,
  strategy configuration, chunk/progress state when applicable, and language
  layout.
- Create the configured agent with editable files limited to the target test
  tree and build file, plus read-only docs and source-context files.

## 2. Issue-specific preparation

### `library-new-request`

Driver: `ai_workflows/drivers/add_new_library_support.py`.

Preparation:

- Resolve the requested `group:artifact:version`, repository roots, metrics
  root, GraalVM home, and strategy.
- Create the feature branch for the new library.
- Resolve large-library progress state when the issue is chunked.
- Decide Native Image eligibility before generation (see below). If the
  artifact is not a Native Image target, write the marker and stop without
  scaffolding, generating tests, or generating metadata.
- Run Gradle scaffold for the coordinate.
- Populate artifact URLs and materialize configured source context.
- Resolve the generated test-source layout.
- Instantiate the dynamic-access workflow strategy with language layout,
  source context, chunk limits, and progress-state inputs.
- Commit the scaffold and metadata index as the checkpoint.
- Initialize the agent with the scaffolded test sources and `build.gradle` as
  editable files.
- Prepare optional graphify context after source extraction and before the
  workflow runs.

#### Native Image eligibility (not-for-native-image)

Not every requested artifact is a JVM library that GraalVM `native-image`
consumes. Before scaffolding, the `library-new-request` driver decides whether
the `group:artifact` is a Native Image metadata target:

1. If `metadata/<group>/<artifact>/index.json` already marks the artifact
   `not-for-native-image`, the driver stops immediately — the artifact is
   already tracked and there is nothing to generate.
2. Otherwise the driver runs the Gradle artifact-discovery task and then a
   conservative eligibility check that classifies the coordinate from three
   signals: a Gradle discovery file that already flags the artifact; coordinate
   naming conventions (Scala.js, Android/AndroidX, Kotlin Native/Wasm, and
   Kotlin/JS artifacts); and Maven inspection (POM packaging of `aar`/`klib`, or
   a published JAR that contains no JVM class files — for example Scala.js IR or
   Kotlin Native metadata only). When a likely JVM replacement coordinate is
   obvious (for example a platform-suffixed artifact, or a Netty `*-classes`
   dependency), the check records it as replacement guidance.
3. When the artifact is found ineligible, the driver writes a marker-only
   `metadata/<group>/<artifact>/index.json` recording `not-for-native-image:
   true`, the reason, and any replacement guidance, then returns success without
   generating tests or reachability metadata. Orchestration then routes the
   marker to the dedicated publisher (§GIT-not-for-native-image-publication),
   and a pre-claim check on already-marked `group:artifact` lets orchestration
   close such issues with an explanatory comment instead of dispatching a run.

The eligibility check is intentionally conservative: an artifact that does carry
JVM class files proceeds to normal scaffolding and dynamic-access generation.

### `library-update-request`

Driver selection depends on whether the requested version already has a
test suite. Both paths are `library-update-request` work because both end by
improving coverage for the requested version (§WF-improve-library-coverage).

#### Existing requested-version test suite

Driver: `ai_workflows/drivers/improve_library_coverage.py`.

Preparation:

- Resolve the existing library coordinate, repository roots, metrics root,
  GitHub authentication, GraalVM home, and strategy.
- Create an `improve-coverage-*` feature branch.
- Resolve the indexed tested version and existing test directory.
- Fail fast if the target test directory is absent.
- Commit the current test directory and metadata index as a checkpoint.
- Snapshot baseline library stats, metadata entry counts, and test-only
  metadata entry counts into the test directory.
- Resolve large-library progress state when the issue is chunked.
- Populate artifact URLs and materialize configured source context.
- Resolve the test-source layout.
- Instantiate the coverage workflow strategy with baseline, language layout,
  source context, chunk limits, and progress-state inputs.
- Initialize the agent with the existing test sources and `build.gradle` as
  editable files.

This preparation preserves the current implementation for a requested version
that already exists in the repository (§WF-improve-library-coverage.1).

#### Missing requested-version test suite

Driver: selected after a compatibility probe.

Preparation:

- Resolve the requested `group:artifact:version`.
- Confirm the repository already supports the requested `group:artifact`.
- Resolve the latest supported test version for that artifact.
- Prepare the latest supported test suite so it runs against the requested
  version, preserving the latest supported version as the baseline.
- Run a compatibility probe against the requested version.
- If Java compilation fails, dispatch `ai_workflows/drivers/fix_javac_fail.py`;
  that driver owns the version-copy preparation, javac repair, and composite
  coverage phase.
- If compilation passes but the JVM test run fails, dispatch
  `ai_workflows/drivers/fix_java_run_fail.py`; that driver owns runtime repair
  and the java-run composite coverage phase.
- If compilation and JVM tests pass, dispatch
  `ai_workflows/drivers/improve_library_coverage.py` for the requested version
  because the latest test suite is compatible.

The selected driver must own its normal setup after the probe; the
`library-update-request` router must not duplicate javac-fix, java-run-fix, or
coverage workflow setup logic (§WF-improve-library-coverage.2).

### `fails-javac-compile`

Driver: `ai_workflows/drivers/fix_javac_fail.py`, backed by the core workflow
object `ai_workflows/core/java_fail_workflow.py`.

Preparation:

- Resolve the previous and failing library versions, repository roots, metrics
  root, GitHub authentication, GraalVM home, and javac-fix strategy.
- Copy the previous version's test project to the failing version.
- Update the metadata index for the new version.
- Create the versioned metadata directory.
- Record whether the target test and metadata directories existed before
  preparation so failure cleanup can restore the correct state.
- Commit the prepared project as the checkpoint.
- Populate artifact URLs and materialize configured source context.
- Resolve the test-source layout.
- Instantiate the javac-fix workflow strategy with previous/new version context.
- Initialize the agent with the copied test sources and `build.gradle` as
  editable files.

### `fails-java-run`

Driver: `ai_workflows/drivers/fix_java_run_fail.py`, backed by the core workflow
object `ai_workflows/core/java_fail_workflow.py`.

Preparation:

- Use the same version-copy, metadata-index update, metadata-directory
  creation, checkpoint, artifact URL, source-context, layout, and editable-file
  preparation as `fails-javac-compile`.
- Load the java-run strategy, runtime-failure prompt wording, runtime task
  type, and `fix_java_run_fail.json` metrics target.
- Prepare the project from the last supported version; the project is expected
  to compile while the agent focuses on JVM runtime behavior.

### `fails-native-image-run`

Driver: `ai_workflows/drivers/fix_ni_run.py`.

Preparation:

- Resolve the current coordinate, new version, and reachability repository
  path.
- Create a `fix-native-image-run-*` feature branch.
- Run the Gradle `fixTestNativeImageRun` task to copy or update the test
  project and generate native-image metadata for the new version.
- If the Gradle fix task fails after generating metadata, run Codex metadata
  repair against the generated metadata file.
- Rerun the coordinate test after Codex metadata repair.
- Populate artifact URLs after the new version is prepared.
- Run library finalization for the generated test and metadata before returning
  success.
