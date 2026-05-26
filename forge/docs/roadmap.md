# ROADMAP-forge-implementation: Forge implementation roadmap

This roadmap lists the first implementation gaps to close in Forge against the
functional spec (§FS-forge-functional-spec), ordered by delivery priority so a
low-risk structural cleanup of `ai_workflows/` lands first, then fixture-backed
end-to-end testing, after which later workflow work can be validated through the
real orchestration boundary. It serves the overall Forge direction in
§GOAL-forge-direction.

1. Better structure of `ai_workflows/` (§ROADMAP-forge-ai-workflows-structure).
2. Fixture-backed E2E mode (§ROADMAP-forge-fixture-backed-e2e).
3. Library-specific preparation preflight (§ROADMAP-forge-library-preflight).
4. Missing-version library-update router (§ROADMAP-forge-missing-version-router).
5. Native metadata exploration finalization path (§ROADMAP-forge-native-finalization).
6. Human-intervention strictness (§ROADMAP-forge-human-intervention-strictness).
7. Planned code coverage improvement workflow (§ROADMAP-forge-code-coverage-workflow).

# ROADMAP-forge-ai-workflows-structure: Better structure of ai_workflows

Priority: first (part of §ROADMAP-forge-implementation).

Reorganize the `ai_workflows/` package into clear subpackages so the driver
layer and the core workflow layer are separated by directory, not just by file
name. The top-level package name stays `ai_workflows`; its contents move into:

- `ai_workflows/drivers/` — the deterministic workflow drivers, one per claimed
  unit of work (§WF-forge-workflow-drivers).
- `ai_workflows/core/` — the core workflow objects: the registered workflow
  engines and shared workflow orchestration that own run state
  (§WF-forge-workflow-engine); this is today's `ai_workflows/workflow_strategies/`.
- `ai_workflows/agents/` — unchanged; the backend-neutral agent integrations
  (§AR-agent-api).

This is a structural refactor with no behavior change: it must not alter
workflow contracts, driver responsibilities, or the strategy-agent boundary
(§WF-forge-workflow-architecture). Implementation moves the modules, updates imports
and the workflow-engine registration in `__init__.py`, and updates the path
references in code and in the remaining docs (README.md, AGENTS.md,
DEVELOPING.md, and workflow specs) to match the canonical layout already
described by §WF-forge-workflow-drivers and §WF-forge-workflow-engine.

# ROADMAP-forge-fixture-backed-e2e: Fixture-backed E2E mode

Priority: second (part of §ROADMAP-forge-implementation).

Implement hermetic fixture-backed E2E execution through `forge_metadata.py`,
per the hermetic fixture E2E mode (§E2E-forge-workflow-testing.2).
The command-line surface must accept `--fixture-testing`, an issue number from
the local YAML fixtures, a strategy name, a reachability-repo path, and the
existing dynamic-access preservation flag.

The fixture backend must model issues, labels, assignees, project items,
blockers, comments, and expected side effects without mutating live GitHub
state. It must still exercise the real control-plane responsibilities defined
in §ORCH-forge-orchestration-spec: issue lookup, label routing,
claiming/project-status checks, isolated worktree and metrics setup, workflow
dispatch, workflow execution, local verification, metrics writing, and dry-run
publication handoff.

Acceptance should be based on boundary evidence, not only process exit code.
Reports must name the fixture mode, issue number, queue label, strategy, model,
command, routed driver, workflow engine, logs, metrics
(§E2E-forge-workflow-testing.5), generated artifacts, and any suspicious
behavior (§E2E-forge-workflow-testing.9).

# ROADMAP-forge-library-preflight: Library-specific preparation preflight

This item of §ROADMAP-forge-implementation adds the bounded LLM preparation
decision before workflow dispatch (§ORCH-forge-orchestration-spec.1). The
decision must inspect issue text,
resolved artifact metadata, existing tests, source context, documentation, and
CI constraints, then produce explicit preparation context for special
dependencies, feature modules, Docker-backed services, fixtures, environment
setup, or other library-specific setup that labels alone cannot express.

The preflight decision is advisory, not a verification result. It must be saved
in metrics with prompt, model, decision, evidence, and selected actions; local
CI-equivalent verification (§FS-local-ci-equivalent-verification) remains
responsible for rejecting untracked downloads, undeclared optional
dependencies, or Docker images that CI would not allow.

# ROADMAP-forge-missing-version-router: Missing-version library-update router

This item of §ROADMAP-forge-implementation implements the missing
requested-version path for `library-update-request`
(§WF-improve-library-coverage.2).
When the artifact is already supported but the requested version has no test
suite, Forge must resolve the latest supported test version, prepare that
suite against the requested version, run a compatibility probe, and dispatch
the rest of the work to the owning driver (§WF-forge-workflow-drivers.2).

The router must dispatch compilation failures to the javac-fix driver
(§WF-java-fail-fix-workflow), JVM-mode runtime failures to the java-run-fix
driver, and compatible versions to the ordinary improve-coverage driver
(§WF-improve-library-coverage.2). The router must not duplicate javac-fix,
java-run-fix, or coverage setup logic; the selected driver owns normal
preparation after the probe.

# ROADMAP-forge-native-finalization: Native test verification finalization path

This item of §ROADMAP-forge-implementation wires native test verification
(§WF-native-test-verification-gate) into successful dynamic-access finalization
(§WF-dynamic-access-workflow). A failing post-iteration native test must drive
the gate's ordered recovery — JVM-agent metadata, then native tracing, then
Codex — rather than invoking Codex directly, so Codex receives
runtime-observed metadata instead of being asked to invent it
(§WF-native-metadata-tracing).

The gate hands a residual failure to Codex with the coordinate, the staged
agent and trace metadata directories, and the failing native-image log, and
returns `FAILED` only when Codex does not converge. Pi is not used in this
path (§WF-native-test-verification-gate).

# ROADMAP-forge-human-intervention-strictness: Human-intervention strictness

This item of §ROADMAP-forge-implementation makes `human-intervention` handling
match the stricter policy in the spec (§FS-human-intervention-policy).
The label must be a maintainer follow-up signal for semantic generated-result,
repository-automation, metadata, or library-behavior concerns, not a generic
failure bucket for transient infrastructure, GitHub status/API problems, rate
limits, Maven/download failures, or review uncertainty.

Publication and review automation must preserve this distinction. Automated
review (§FS-automated-pr-review) may skip existing `human-intervention` PRs, and
may add or request the label only when the label-specific review rules identify
a semantic issue that cannot be handled by a normal approval or
requested-changes review. A `human-intervention-fixed` PR must remain the
explicit maintainer signal that manual follow-up is complete before normal
merge gates resume.

Local CI verification (§FS-local-ci-equivalent-verification) must be the
algorithmic source for the shared-repository edit case: when verification
passes only after repository-level files changed, metrics and the PR
description must list those paths and publication must add
`human-intervention`. Other local verification failures should remain normal
failures with diagnostics unless the evidence fits the policy's semantic cases.

# ROADMAP-forge-code-coverage-workflow: Planned code coverage improvement workflow

This item of §ROADMAP-forge-implementation implements the planned
PGO/API-coverage workflow for already-supported libraries
(§WF-code-coverage-improvement). The workflow must be separate from
dynamic-access coverage: it targets broader public API and runtime behavior
using GraalVM PGO runtime profiles, while dynamic-access workflows target
metadata-relevant calls (§FS-forge-code-coverage-improvement).

The implementation needs a driver mode, a registered workflow engine
(§WF-code-coverage-improvement-architecture), a PGO profile collector, an API inventory
builder, target selection and durable target-state format, metrics schema
support, and a publication path. Generated tests must live in a separate code
coverage suite with separate metrics and review evidence, not in the
metadata-generation suite (§WF-code-coverage-improvement.2).

Acceptance requires meaningful behavior tests, measurable profile improvement
or recorded skipped/exhausted targets, no regression in metadata validity or
JVM/native gates (§FS-local-ci-equivalent-verification), and metrics that record
baseline and updated profiles, selected targets, profile deltas, generated test
paths, strategy, model, skipped/exhausted targets, and verification commands
(§WF-code-coverage-improvement.5).
