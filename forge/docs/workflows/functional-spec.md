# WF-forge-workflow-system: Forge workflow system specification

Forge workflows are the behavioral contracts for resolving supported issue
queues (§FS-forge-issue-resolution-goal). A workflow defines the inputs it
accepts, the generated tests and metadata it may change, the verification gates
it must pass, and the terminal status it returns to orchestration
(§ORCH-forge-orchestration-spec). It must preserve durable logs
(§FS-durable-generation-logs) for every agent session, generation step, and
deterministic command that contributes to the run. The implementation is a
state-machine-like workflow engine selected and parameterized by a predefined
strategy bundle.

The workflow spec set covers workflow drivers (§WF-forge-workflow-drivers),
dynamic-access generation (§WF-dynamic-access-workflow), Java failure repair
(§WF-java-fail-fix-workflow), native-image run repair
(§WF-native-image-run-fix-workflow), native metadata tracing and verification
(§WF-native-metadata-tracing), dynamic-access coverage improvement
(§WF-improve-library-coverage), and planned code coverage improvement
(§WF-code-coverage-improvement).

Workflow specs describe what must happen. Workflow implementation architecture
(§AR-forge-architecture), including workflow drivers, predefined strategy
configuration, workflow engines, agents, shared utility modules, and PR
publication, belongs in Forge architecture and component architecture docs;
the planned code coverage workflow has its own architecture file
(§AR-code-coverage-improvement).
