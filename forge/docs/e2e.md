# E2E-forge-workflow-testing: Forge end-to-end workflow testing

Forge end-to-end testing validates the whole issue-processing path, not only a
workflow engine (§WF-forge-workflow-system) or workflow driver. It exercises
orchestration alongside the workflow layer (§ORCH-forge-orchestration-spec) and
is expensive: it creates worktrees, runs agents, runs Gradle, writes metrics,
and reaches issue/PR publication handoff. Agents run hermetic fixture E2E when
required by [AGENTS.md](../../AGENTS.md), while live GitHub E2E requires an
explicit user request.

## 1. Purpose

End-to-end testing answers one question: can Forge take one supported issue
shape, route it through `forge_metadata.py`, execute the selected workflow,
verify the generated artifacts, and return the correct issue/PR and metrics
outcome?

This document is an instruction for agents testing the whole process. Unit
tests, mocked workflow tests, or direct driver invocations do not satisfy
this spec.

## 2. Hermetic Fixture E2E

The default E2E mode is a fixture-backed run. It uses local YAML files under
`forge/fixture_github_issues/` to imitate GitHub issue payloads, labels,
comments, blockers, and project state. The fixture issue does not need to exist
on GitHub. Fixture mode must not mutate live GitHub state.

A fixture-backed E2E still runs through `forge_metadata.py` and must exercise
the real control-plane responsibilities. The fixture-specific behavior is
limited to fetching the issue from local fixture state and suppressing live PR
publication; routing, workflow-driver invocation, verification, metrics, and
cleanup stay on the same modular path as normal issue processing:

- GitHub issue lookup through the fixture backend.
- Label-based routing.
- Direct construction of the claimed issue from the fixture payload, bypassing
  live GitHub claim mechanics such as assignee races, project-status checks, and
  blocker/preflight queries.
- Isolated worktree and metrics path setup.
- Dispatch to the matching workflow driver.
- Workflow engine execution through the configured strategy.
- Local verification, metrics writing, and dry-run publication handoff.

Fixture GitHub mode implies dry-run publication. It must record the issue,
label and any explicit strategy override, isolated worktree setup, fixture
masking, workflow driver invocation, workflow result, dry-run publication or
preservation handoff, and cleanup in local run output. It must not fork a
second implementation of orchestration or workflow behavior: fixture-only code
may replace issue fetch/state with local fixture data and replace publish/push
side effects with local artifacts, but the workflow drivers and git-script PR
body builders remain the source of truth. It does not assign real issues,
change real project items, push branches, or open pull requests.
`--strategy-name` is optional in fixture mode just as it is for normal
single-issue processing; when omitted, Forge routes the issue without passing a
strategy override and lets the selected workflow driver apply its default.

The bundled fixture scenarios live in `fixture_github_issues/`. The primary
runnable fixture is issue `9101`, a `library-new-request` scenario for
`com.google.http-client:google-http-client:1.42.2`; run it from `forge/` with:

```bash
python3 forge_metadata.py \
  --fixture-testing \
  --issue-number 9101 \
  --strategy-name dynamic_access_main_sources_pi_gpt-5.5 \
  --reachability-metadata-path .. \
  --keep-tests-without-dynamic-access
```

Every fixture run writes each issue's evidence under
`forge/fixture-e2e/issue-<number>/<run-timestamp>/`, whether the issue was
selected by `--issue-number`, a `--label` queue, or `--run-work-queues`. Issues
claimed by the same run share one `<run-timestamp>`, so a queue run that
processes several issues leaves sibling `issue-<number>/<run-timestamp>/`
directories rather than a separate queue-scoped directory. Each issue directory
contains `run.log`, a complete merged stdout/stderr log scoped to that issue, and
`publication.md` when the issue reaches dry-run PR publication. Fixture issue
processing is sequential — parallelism is pinned to 1 in fixture mode — so each
`run.log` stays scoped to its issue without interleaving.
`publication.md` must be built from the same git-script PR title/body builder
that live publication uses (§GIT-pr-preview-builders). Fixture mode does not
write a separate JSON E2E report, mutate live GitHub state, or simulate GitHub
claim/project-board races. §E2E-forge-workflow-testing.2 §E2E-forge-workflow-testing.9

The fixture run is a hard failure when the selected fixture issue cannot be
loaded/resolved, or when the workflow lifecycle exits incoherently. Routing,
masking, worktree setup, driver invocation, handoff, and cleanup must be logged
for agent or human inspection, but they are not a separate side-effect oracle.
After every fixture run, a human or agent must inspect the generated run
artifacts, especially `run.log` and any `publication.md`, before treating the
E2E result as accepted.

The primary fixture scenario is the new/update library dynamic-access path:

1. `library-new-request` is the main exhibited path because it exercises issue
   routing, new test scaffolding, source-context preparation, dynamic-access
   generation, metadata generation, metrics, and publication readiness.
2. `library-update-request` is the paired coverage-improvement path and should
   be used when the requested test is specifically about improving existing
   library coverage or chunked continuation.

Additional fixture scenarios cover each supported issue label and expected
driver: `library-new-request`, `library-update-request`, `fails-javac-compile`,
`fails-java-run`, and `fails-native-image-run`. The non-primary fixtures are
intended to make routing and current-version resolution demonstrable for those
labels; the primary `9101` fixture remains the default dynamic-access acceptance
target. §E2E-forge-workflow-testing.5

For `library-new-request` fixtures that use an already-supported dynamic-access
library, fixture setup removes the requested version entry and version-scoped
metadata, tests, and stats from the isolated worktree before workflow routing.
The primary `9101` fixture uses Google HTTP Client because its existing
generated dynamic-access report has 17 call sites: large enough to exercise the
dynamic-access workflow, but small enough for practical fixture runs. Live
metadata is not changed. Cleanup is fixture infrastructure and is logged during
setup.

For the version-upgrade failure fixtures — `fails-javac-compile`,
`fails-java-run`, and `fails-native-image-run` — the issue title names the new
(failing) version and the body includes the original reproducer with the
previous `-Pcoordinates=group:artifact:version`. Fixture setup removes the
requested version entry and its version-scoped metadata, tests, and stats from
the isolated worktree index and marks that previous version as `latest` before
resolving current coordinates. This recreates the repository state at the moment
the failure issue was filed: the new version is not yet supported, so
control-plane resolution derives `current` from the previous `latest` entry and
`new_version` from the title, and the routed fix driver regenerates the new
version from that baseline. Live metadata is not changed.

For one fixture issue:

```bash
python3 forge_metadata.py \
  --fixture-testing \
  --issue-number <fixture-issue> \
  --strategy-name <strategy-name> \
  --reachability-metadata-path <repo> \
  [--keep-tests-without-dynamic-access]
```

For a fixture label or queue run:

```bash
python3 forge_metadata.py \
  --fixture-testing \
  --label <queue-label> \
  --limit <count> \
  --strategy-name <strategy-name> \
  --reachability-metadata-path <repo> \
  [--keep-tests-without-dynamic-access]
```

```bash
FORGE_JAVAC_WORK_LIMIT=0 \
FORGE_JAVA_RUN_WORK_LIMIT=1 \
FORGE_NI_RUN_WORK_LIMIT=0 \
FORGE_LIBRARY_UPDATE_WORK_LIMIT=0 \
FORGE_WORK_LABEL=library-new-request \
FORGE_WORK_LIMIT=1 \
FORGE_PARALLELISM=1 \
python3 forge_metadata.py \
  --fixture-testing \
  --run-work-queues \
  --strategy-name <strategy-name> \
  --reachability-metadata-path <repo> \
  [--keep-tests-without-dynamic-access]
```

`--fixture-testing` selects the local fixture backend. `--issue-number` names
one mocked issue number from the YAML fixture. `--label` and `--limit` drive a
single labeled queue directly, while `--run-work-queues` runs every configured
queue and reads its labels and per-queue limits from the same `FORGE_*`
environment as normal issue processing: `FORGE_JAVAC_WORK_LIMIT`,
`FORGE_JAVA_RUN_WORK_LIMIT`, `FORGE_NI_RUN_WORK_LIMIT`,
`FORGE_LIBRARY_UPDATE_WORK_LIMIT`, the primary `FORGE_WORK_LABEL` /
`FORGE_WORK_LIMIT` queue, and `FORGE_PARALLELISM` (a limit of `0` disables that
queue). All three forms still build claimed issues from fixture state instead of
live GitHub claims.

A mocked issue YAML should look like a small GitHub issue plus project state:

```yaml
number: 9101
title: "Add support for com.google.http-client:google-http-client:1.42.2"
state: OPEN
labels:
  - library-new-request
assignees: []
project:
  number: 30
  item_id: fixture-project-item-9101
  status: Todo
blocked_by: []
body: |
  Please add metadata and tests for
  `com.google.http-client:google-http-client:1.42.2`.
comments:
  - author: fixture-author
    body: "Please cover reflective field metadata, data models, and I/O helpers."
```

Fixture queue scanning is local to the loaded YAML issues. It does not perform
live GitHub search, live issue claiming, or live project-board mutation.

## 3. Live GitHub Smoke E2E

Live GitHub E2E is an optional smoke test for GitHub API compatibility,
permissions, and real project integration. It may claim a real issue and may
mutate real issue/project state, so agents must run it only when the user
explicitly asks to test an exact GitHub issue number.

Do not choose a live issue by scanning a real GitHub queue. Do not use
`--run-work-queues`, random offsets, or "next available issue" behavior for a
live E2E pass/fail test. If the user does not provide an exact GitHub issue
number, use fixture mode instead or stop and ask for the issue number.

Use the exact issue, strategy, model, and queue requested by the user. If the
user names an issue but does not name a strategy, use the currently configured
default for that issue's queue.

## 4. Required Invocation

Run the E2E through `forge_metadata.py`, not by calling a workflow driver
directly. For a single issue:

```bash
python3 forge_metadata.py \
  --issue-number <issue> \
  --strategy-name <strategy-name> \
  --reachability-metadata-path <repo> \
  [--keep-tests-without-dynamic-access]
```

For live GitHub E2E, `<issue>` must be the exact GitHub issue number explicitly
requested by the user.

Live GitHub E2E must use the real control-plane responsibilities:

- GitHub issue lookup and label routing.
- Claiming and project-status checks.
- Isolated worktree and metrics path setup.
- Dispatch to the matching workflow driver.
- Workflow engine execution through the configured strategy.
- Local verification, metrics writing, and publication handoff when the run is
  PR-eligible.

## 5. Step Verification

During and after the run, verify each boundary instead of relying only on the
process exit code:

The verification steps apply to both E2E modes: hermetic fixture E2E
(§E2E-forge-workflow-testing.2) and live GitHub smoke E2E
(§E2E-forge-workflow-testing.3).

1. **Issue routing** — the issue had the expected queue label and routed to the
   expected workflow driver. In live GitHub mode, also verify that the issue was
   claimable:
   `add_new_library_support.py` for `library-new-request` or
   `improve_library_coverage.py` for `library-update-request`.
2. **Setup** — Forge created or selected the expected worktree/branch, resolved
   the reachability repo and metrics root, normalized the GraalVM environment,
   loaded the requested predefined strategy, and completed the issue-specific
   driver preparation contract for the routed label
   (§WF-forge-workflow-drivers).
3. **Workflow execution** — the selected workflow engine ran the real prompt,
   Gradle, retry, fallback, native-test verification, and finalization path
   required by its spec. For the new/update dynamic-access path, verify the
   dynamic-access strategy family behavior (§WF-dynamic-access-workflow) and
   fallback rules (§WF-dynamic-access-fallback-and-failure).
4. **Artifacts and metrics** — generated tests are meaningful, metadata is
   present when expected, local CI-equivalent verification was not skipped,
   metrics validate against schema, and no unrelated edits were introduced
   (§FS-local-ci-equivalent-verification).
5. **Issue/PR result** — successful non-final chunks reference the issue,
   final or non-chunk successful runs are publication-ready, failures preserve
   useful logs/metrics, and live GitHub runs verify project/assignment cleanup.

## 6. Logs and Review

Inspect logs while the workflow runs and after it exits. Do not ignore a
timeout; a timeout is an E2E failure signal unless there is clear external
infrastructure evidence.

For agent logs, verify that:

- The conversation stays focused on the active library and failure.
- The agent reacts to Gradle evidence instead of repeating guesses.
- The agent does not do unrelated repository exploration or edits.
- Prompt and response content is debuggable.

When metadata repair or native metadata tracing runs
(§WF-native-metadata-tracing), inspect the relevant logs and verify that
traced metadata, missing-registration output, and Codex fixup are coherent
with the failing native-image evidence and the native test verification gate
(§WF-native-test-verification-gate).

## 7. Pass Criteria

An E2E test passes only if all of these are true:

- The E2E run was required by [AGENTS.md](../../AGENTS.md), or the user explicitly
  requested it.
- The test used `forge_metadata.py` as the entry point for the process under
  test.
- A fixture-backed supported issue scenario was used, or the user explicitly
  requested a live E2E run for an exact GitHub issue number.
- Each boundary in §5 was verified from logs, generated files, metrics, and
  issue/PR state.
- Generated tests and metadata are meaningful and scoped to the target
  coordinate.
- No timeout, failed gate, suspicious metric, or unrelated edit was ignored.

## 8. Fail Criteria

An E2E test fails if any of these happen:

- It was run without being required by [AGENTS.md](../../AGENTS.md) or explicitly
  requested by the user.
- It bypassed `forge_metadata.py` and called only a workflow driver.
- It used live GitHub state without an exact user-requested GitHub issue number.
- It used random live issue selection, random offsets, or live queue scanning
  for a pass/fail E2E test.
- It used only unit-test mocks, toy coordinates, or synthetic repositories.
- Routing, setup, workflow execution, verification, metrics, or publication
  handoff could not be verified. In live GitHub mode, issue claiming must also
  be verified.
- Generated tests are trivial, broken, misleading, or achieved success by
  weakening the test.
- Agent, metadata-fix, native tracing, or verification logs show drift,
  timeout, incoherent recovery, or ignored failures.

## 9. Reporting

When the E2E test finishes, report:

- The mode used: fixture-backed E2E or live GitHub smoke E2E.
- The issue number, queue label, strategy, model, fixture path when applicable,
  and command used.
- The routed driver and workflow engine.
- The observed result at each boundary in §5.
- Links or paths to the fixture artifact directory, `run.log`, metrics,
  generated tests, metadata, and `publication.md` or preserved branch.
- Any suspicious behavior, residual risk, or reason the result should not be
  trusted.
