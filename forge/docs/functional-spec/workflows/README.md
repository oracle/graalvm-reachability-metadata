# Workflows

Per-workflow specifications live here as `WF-<slug>` declarations. A document
belongs in this directory when it governs one named workflow — what it does,
which strategies it accepts, when it fails, and what it must prove before it
publishes. Requirements that hold across every workflow belong in
[the functional spec](../README.md), and cross-cutting structure belongs in
[../../architecture/](../../architecture/README.md).

Each workflow has one file. A shared mechanism that several workflows invoke —
native metadata tracing, the workflow-driver contract — also gets its own file,
so a caller cites the mechanism rather than restating it.

| ID | Subject |
| --- | --- |
| §WF-forge-workflow-system | Forge workflow system specification |
| §WF-forge-workflow-architecture | Forge workflow system architecture |
| §WF-forge-workflow-engine | Workflow engines own run state |
| §WF-forge-workflow-strategy-config | Strategies configure workflow runs |
| §WF-forge-workflow-drivers | Workflow drivers |
| §WF-add-new-library-support | Add new library support workflow |
| §WF-basic-iterative | Basic iterative workflow |
| §WF-improve-library-coverage | Improve library coverage workflow |
| §WF-java-fail-fix-workflow | Java fail-fix workflow |
| §WF-native-image-run-fix-workflow | Native-image run-fix workflow |
| §WF-code-coverage-improvement | Code coverage improvement workflow |
| §WF-code-coverage-improvement-architecture | Code coverage improvement workflow architecture |
| §WF-dynamic-access-workflow | Dynamic-access workflow specification |
| §WF-dynamic-access-strategy-family | Dynamic-access strategy family |
| §WF-dynamic-access-iterative-strategy | Iterative dynamic-access strategy |
| §WF-dynamic-access-bulk-strategy | Bulk dynamic-access strategy |
| §WF-dynamic-access-composite-strategy | Composite dynamic-access strategy |
| §WF-dynamic-access-fallback-and-failure | Required fallback and failure behavior |
| §WF-dynamic-access-exhaust-report | Dynamic-access exhaust report |
| §WF-chunked-dynamic-access-pr-linking | Chunk PR linking |
| §WF-native-metadata-tracing | Native metadata tracing specification |
| §WF-native-test-verification-gate | Native test verification gate |
| §WF-native-test-verification-callers | Native test verification callers |
| §WF-native-trace-gradle-tasks | Native tracing Gradle task contract |
| §WF-native-tracing-reporter-divergence | Reporter and tracer are separate components |

This index is navigational. Cite the specific declaration ID rather than this
file.

Files:

- [workflow-system.md](workflow-system.md) — the workflow layer itself: engines,
  drivers, and how a strategy configures a run.
- [workflow-drivers.md](workflow-drivers.md) — the deterministic driver contract
  every workflow entry script follows.
- [add-new-library-support.md](add-new-library-support.md) — new-library
  generation, and the basic iterative fallback.
- [improve-library-coverage.md](improve-library-coverage.md) — raising coverage
  for a library that already has tests.
- [java-fail-fix.md](java-fail-fix.md) — repairing `javac` and JVM-run failures
  on a version bump.
- [native-image-run-fix.md](native-image-run-fix.md) — repairing a native-image
  run failure.
- [dynamic-access.md](dynamic-access.md) — the dynamic-access strategy family,
  chunking, and the exhaust report.
- [native-metadata-tracing.md](native-metadata-tracing.md) — the shared
  observe-record-rebuild loop and the verification gate every workflow ends on.
- [code-coverage-improvement.md](code-coverage-improvement.md) — the planned
  PGO-driven coverage workflow.
