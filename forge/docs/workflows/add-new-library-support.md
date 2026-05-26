# WF-add-new-library-support: Add new library support workflow

New library support is part of the Forge workflow system
(§WF-forge-workflow-system). It resolves the `library-new-request` queue
(§FS-forge-issue-resolution-goal): for a previously unsupported coordinate it
generates a JUnit/Kotlin/Scala test suite, produces GraalVM reachability
metadata, and opens a pull request that adds the library to the repository
(§GOAL-maximize-library-coverage).

## Driver and preparation

The queue is entered by the deterministic driver
`ai_workflows/drivers/add_new_library_support.py` (§WF-forge-workflow-drivers).
Before any agent work, the driver scaffolds the test project and decides whether
the coordinate is a Native Image target. When it is not, the driver writes the
`not-for-native-image` marker and stops without generating tests or metadata
(§WF-forge-workflow-drivers, §GIT-not-for-native-image-publication).

## Generation

Generation is the dynamic-access workflow; this spec does not restate it. The
driver instantiates a dynamic-access strategy to exercise the coordinate's
dynamic-access call sites and produce the reachability metadata
(§WF-dynamic-access-workflow). Oversized issues run in chunked mode and resume
across chunk PRs (§WF-dynamic-access-exhaust-report). After the coverage phase,
native-image behavior is validated through native metadata tracing
(§WF-native-metadata-tracing), gated by the native test verification gate
(§WF-native-test-verification-gate), before the run becomes PR-eligible.

## WF-basic-iterative: Basic iterative workflow

`basic_iterative` is the most basic Forge workflow: a bounded loop that prompts
the agent to write tests, runs `./gradlew test`, and repairs failures, with no
dynamic-access report to guide class selection. Its retry budget comes from the
basic iterative strategy bundles (§STRAT-predefined-strategy-parameter-families),
and `basic_iterative_pi_gpt-5.4` is the default strategy.

It is not a separate issue queue. It is the narrow fallback the dynamic-access
workflow delegates to only when no usable dynamic-access report exists at the
start of a run — the report task fails, the report is missing or unparsable,
reporting is disabled, or the report has zero dynamic-access calls
(§WF-dynamic-access-fallback-and-failure).
