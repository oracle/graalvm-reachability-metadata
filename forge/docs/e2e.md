# E2E-forge-workflow-testing: Forge end-to-end workflow testing

Forge end-to-end testing validates the whole issue-processing path, not only a
workflow engine (§WF-forge-workflow-system) or workflow driver. It exercises
orchestration alongside the workflow layer (§ORCH-forge-orchestration-spec) and
is expensive: it creates worktrees, runs agents, runs Gradle, writes metrics,
and reaches issue/PR publication handoff. Agents must run this test only when
the user explicitly asks for end-to-end testing.

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
`forge/fixtures/github-issues/` to imitate GitHub issues, labels, assignees,
project items, blockers, comments, and expected side effects. The fixture issue
does not need to exist on GitHub. Fixture mode must not mutate live GitHub
state.

A fixture-backed E2E still runs through `forge_metadata.py` and must exercise
the real control-plane responsibilities:

- GitHub issue lookup through the fixture backend.
- Label-based routing.
- Claiming and project-status checks in fixture state.
- Isolated worktree and metrics path setup.
- Dispatch to the matching workflow driver.
- Workflow engine execution through the configured strategy.
- Local verification, metrics writing, and dry-run publication handoff.

Fixture GitHub mode implies dry-run publication. It must record the issue,
project, label, comment, assignment, and publication effects in local run
output instead of assigning real issues, changing real project items, pushing
branches, or opening pull requests.

The bundled fixture scenarios live in `fixtures/github-issues/`. The primary
runnable fixture is issue `9101`, a `library-new-request` scenario for
`org.apache.commons:commons-csv:1.11.0`; run it from `forge/` with:

```bash
python3 forge_metadata.py \
  --fixture-testing \
  --issue-number 9101 \
  --strategy-name dynamic_access_main_sources_pi_gpt-5.5 \
  --reachability-metadata-path .. \
  --keep-tests-without-dynamic-access
```

The persisted fixture E2E report is written under
`script_run_metrics/fixture-e2e/issue-<number>-<run-id>/fixture-e2e-report.json`.
Fixture mode records issue, project, comment, label, assignment, and dry-run
publication effects locally in that report; it does not mutate live GitHub
state. §E2E-forge-workflow-testing.2 §E2E-forge-workflow-testing.9

The primary fixture scenario is the new/update library dynamic-access path:

1. `library-new-request` is the main exhibited path because it exercises issue
   claiming, new test scaffolding, source-context preparation, dynamic-access
   generation, metadata generation, metrics, and publication readiness.
2. `library-update-request` is the paired coverage-improvement path and should
   be used when the requested test is specifically about improving existing
   library coverage or chunked continuation.

Additional fixture scenarios cover each supported issue label and expected
driver: `library-new-request`, `library-update-request`, `fails-javac-compile`,
`fails-java-run`, and `fails-native-image-run`. The non-primary fixtures are
intended to make routing, current-version resolution, and claim behavior
demonstrable for those labels; the primary `9101` fixture remains the default
dynamic-access acceptance target. §E2E-forge-workflow-testing.5

Issues `9107`, `9108`, and `9109` are focused missing-version router fixtures
for the `library-update-request` queue. They request real supported artifact
versions that are absent from the local index and should route as follows:
`9107` (`org.apache.commons:commons-lang3:3.19.0`) to `compatible`,
`9108` (`commons-net:commons-net:3.14.0`) to `javac-failure`, and `9109`
(`org.glassfish.grizzly:grizzly-framework:2.3.6`) to `java-run-failure`. Run
one router fixture from `forge/` with:

```bash
python3 forge_metadata.py \
  --fixture-testing \
  --issue-number <9107|9108|9109> \
  --strategy-name library_update_pi_gpt-5.5 \
  --reachability-metadata-path ..
```

Fixture mode requires `--strategy-name` for the claimed issue. Routed
`javac-failure` and `java-run-failure` drivers still use their Java-fail
defaults unless `FORGE_JAVAC_STRATEGY_NAME` or
`FORGE_JAVA_RUN_STRATEGY_NAME` is set. Inspect
`script_run_metrics/fixture-e2e/issue-<number>-<run-id>/fixture-e2e-report.json`
for `library_update_route`, `routed_driver`,
`preserved_report_artifacts.library_update_route_sidecar`,
`preserved_report_artifacts.library_update_route_logs`,
`library_update_route_sidecar_json`, `dry_run_publication_handoff`, and
`boundary_verification.issue_routing.library_update_route`. These fields show
the selected route, selected baseline coordinate, ordered `compileTestJava` and
`javaTest` probe evidence, selected driver, preserved route logs, and dry-run
publication script/label. The fixture also evaluates
`expected_report_assertions`; failed assertions are listed in
`report_assertion_comparison` and make the trust assessment suspicious.
§E2E-forge-workflow-testing.2 §E2E-forge-workflow-testing.5


For a fixture issue:

```bash
python3 forge_metadata.py \
  --fixture-testing \
  --issue-number <fixture-issue> \
  --strategy-name <strategy-name> \
  --reachability-metadata-path <repo> \
  [--keep-tests-without-dynamic-access]
```

`--fixture-testing` selects the local fixture backend. `--issue-number` names
the mocked issue number from the YAML fixture that is being tested. Fixture
mode records assignment, project status, labels/comments, failed-work
preservation, and publication handoff locally. It does not assign live issues,
move live project items, push branches, or open pull requests.

A mocked issue YAML should look like a small GitHub issue plus project state:

```yaml
number: 9101
title: "Add support for org.apache.commons:commons-csv:1.11.0"
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
  `org.apache.commons:commons-csv:1.11.0`.
comments:
  - author: fixture-author
    body: "Please cover CSVFormat, CSVParser, and CSVPrinter."
expected_side_effects:
  - action: set-assignee
    username: fixture-runner
  - action: set-project-status
    status: In Progress
  - action: publication-handoff
    script_name: git_scripts/make_pr_new_library_support.py
    issue_label: library-new-request
    result_label: library-new-request
    coordinates: org.apache.commons:commons-csv:1.11.0
```

For fixture queue scanning, use `--run-work-queues` only when the user
explicitly asks to exercise queue scanning. The queue must come from fixture
state and must not use random offsets.

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

The test must use the real control-plane responsibilities:

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

1. **Issue routing** — the issue had the expected queue label, was claimable,
   and routed to the expected workflow driver:
   `add_new_library_support.py` for `library-new-request`,
   `improve_library_coverage.py` for an existing-version
   `library-update-request`, or the missing-version route selected under
   `library_update_route` for a router fixture.
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
   useful logs/metrics, and project/assignment cleanup matches the
   orchestration contract.

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

- The user explicitly requested an E2E run.
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

- It was run without an explicit user request for E2E testing.
- It bypassed `forge_metadata.py` and called only a workflow driver.
- It used live GitHub state without an exact user-requested GitHub issue number.
- It used random live issue selection, random offsets, or live queue scanning
  for a pass/fail E2E test.
- It used only unit-test mocks, toy coordinates, or synthetic repositories.
- Issue claiming, routing, setup, workflow execution, verification, metrics, or
  publication handoff could not be verified.
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
- Links or paths to the important logs, metrics, generated tests, metadata, and
  PR or branch.
- Any suspicious behavior, residual risk, or reason the result should not be
  trusted.
