# AR-repository-ci: Recurring CI workflows and composite actions

This is the inventory and behavioral contract for the repository's continuous
integration: every GitHub Actions workflow, composite action, and shared script
that runs on a schedule or in response to a pull request or issue. CI is the
authoritative quality gate — local runs are best-effort, and no metadata ships
without passing the relevant gates here (§FS-repository-functional-spec.4,
§GOAL-protect-shipped-metadata, §PRCPL-prefer-algorithmic). The normative run-size limits and per-gate
requirements are stated in the functional spec's CI-gates section; this document
maps each requirement to the concrete workflow that enforces it.

Each workflow under `.github/workflows/` carries a `§`-citation back to its
declaration below, and each composite action under `.github/actions/` to its
declaration in [ci-actions.md](ci-actions.md), so the source of truth for *what
a workflow does* is this document and the workflow file points here rather than
the reverse. Mutating scheduled steps run only on
the canonical `oracle/graalvm-reachability-metadata` repository.

## AR-matrix-source: Matrix source of truth (`ci.json`)

`ci.json` is the single source of truth for which GraalVM JDK versions and OS
runners the test matrix uses, plus the build arguments for the build. The Gradle
matrix-generation tasks (§AR-test-harness) read it and emit the GitHub Actions
matrices the test workflows consume. It supports optional per-native-image-mode
Java overrides via `nativeImageModeJavaVersions` when a mode should run on only a
subset of the configured JDKs, and an optional per-section `native-image-modes`
list when one matrix should run only some of the modes. Because the whole matrix derives from this file,
changing it re-runs everything: the infrastructure workflow
(§AR-test-changed-infrastructure) treats a `ci.json` change as an infrastructure
change.
Serves §FS-repository-functional-spec.4.3 — ci.json is that section's single source of
truth for the OS/JDK matrix.

## 1. Pull-request validation workflows

These run on `pull_request` and are the per-PR merge gates
(§FS-repository-functional-spec.5.3). Each gates its real steps on the
`detect-file-changes` action (§AR-detect-file-changes) so a PR that does not
touch the guarded paths is a no-op.

### AR-required-status-checks: Required status checks on the default branch

The merge gate is a GitHub ruleset on the default branch that requires one
status check per PR-validation workflow (§FS-repository-functional-spec.5.3).
Matrix workflows expose a single always-running aggregator job that succeeds
when its matrix passed or nothing relevant changed, and that aggregator — not
the per-library or per-project matrix job — is the required context; single-job
workflows are required by their job name. Without the ruleset, an approval plus
auto-merge merges as soon as the organization's OCA check passes, which is how a
head whose Spring AOT smoke matrix had already failed reached `master`.

The ruleset lives in `.github/rulesets/master-required-checks.json` and is
applied with `.github/scripts/apply_required_checks_ruleset.py` by a repository
administrator (creating or updating the ruleset by name). The same script's
`--check` mode runs in the grund-check workflow (§AR-grund-check) and fails when
a required context no longer matches a `pull_request` job name, so renaming a
gate job without updating the ruleset cannot land unnoticed. Contexts are job
display names, emoji included.

### AR-test-changed-metadata: Test changed metadata

Triggers on PRs touching `metadata/` or `tests/src/`. Runs
`generateChangedMetadataTestMatrix` with the PR base/head SHAs to test only what
changed: it batches each changed metadata version's `tested-versions` into chunks
of up to 30 versions per job, includes newly added tested versions from
artifact-level index diffs, pulls allowed Docker images, disables Docker
networking, validates config, and runs only that batch (§FS-metadata,
§FS-tests).

### AR-test-changed-real-use: Test changed real-use projects

Triggers on PRs touching `real-use-tests/`. Runs `generateChangedRealUseMatrix`
with the PR base/head SHAs, then `realUseJavaTest` and `realUseNativeTest` for
each changed real-use project on its own `generateChangedRealUseMatrix` lane
(`latest-ea`, `current-defaults`), after pulling allowed Docker images and disabling Docker networking.
It is one of the gates of §FS-repository-functional-spec.5.3, for the suite of
§real-use-tests/FS-real-use-tests.3; the job itself is specified in
§real-use-tests/AR-real-use-test-harness.4.

### AR-test-changed-forge: Test changed Forge code

Runs Forge's Python tests (`forge/tests`) and the Rhei program tests, which drive
the `forge-metadata` state machine against a fake harness
(§forge/AR-forge-workflow-pipeline.3), each with
`python -m unittest discover -s tests`. It is the `test-*` gate of
§FS-repository-functional-spec.5.3 for the Forge automation of
§FS-repository-functional-spec.4.6, so a Forge change cannot merge with either
suite failing.

It runs the suites when a PR touches `forge/` or a file they read outside it: the
PR publisher under `.github/scripts/forge_pr_publisher/`, the `stats/` schemas,
and the committed run records (`stats/*/*/*/execution-metrics.json`), which the
path-provenance tests scan for operator paths (§forge/FS-durable-generation-logs).
A record carrying one thus fails the PR that commits it, not the next Forge
change. On any other PR the job succeeds without running them. A push to
`master` on those paths runs them too, since two PRs can pass apart and fail
together.

The job installs only `forge/requirements.txt`, on Python 3.10, the floor of
`requires-python` in `forge/pyproject.toml`, and sets up a GraalVM for JDK 25
(§forge/FS-forge-host-requirements), without which the code coverage tests that
compile and run Java skip. No GitHub credentials reach the tests, and the suites
must pass on any host and from any checkout location: they isolate the home,
temporary and Gradle user directories, give every repository they commit in its
own git identity, never reach the network, and compute the paths they expect. It is a single job, so its name is the required context itself
(§AR-required-status-checks).

### AR-test-changed-infrastructure: Test changed infrastructure

Triggers on PRs touching `tests/tck-build-logic/`, `gradle/`, `build.gradle`,
`settings.gradle`, or `gradle.properties`. Runs
`generateInfrastructureChangedCoordinatesMatrix`, then pulls allowed images,
disables Docker networking, validates config, and runs tests for the affected
coordinates. This is the CI counterpart of the local `testInfra` and
`testAllInfra` harness tasks (§AR-test-harness).
Serves §FS-repository-functional-spec.4.3 — the PR-scoped changed-infrastructure lane.

### AR-test-affected-spring-aot: Test affected Spring AOT smoke tests

Triggers on PRs touching `metadata/`. Runs `generateAffectedSpringTestMatrix` to
compute impacted Spring AOT projects, then runs triaged native tests via
`run-spring-aot-triaged-test.sh` (§AR-shared-scripts). Only runs when metadata
changes actually affect a Spring AOT project (§FS-repository-functional-spec.5.3).

### AR-index-file-validation: Validate index.json files

Triggers on PRs touching `metadata/*/*/index.json`. Runs
`generateChangedIndexFileCoordinatesList` to find the changed index coordinates,
then `validateIndexFiles` to check index schema and
`metadata-version`/`tested-versions` consistency (§FS-metadata).

### AR-library-stats-validation: Validate library stats

Triggers on PRs changing exploded stats files under `stats/`, the stats schema,
or mirrored files under `metadata/`. Runs `validateLibraryStats` to enforce
schema compliance and normalized sorting of the `stats/` files, including the
Forge run records (§forge/FS-forge-run-metrics, §FS-repository-functional-spec.4.1).

### AR-library-and-framework-list-validation: Validate library-and-framework list

Triggers on PRs touching `metadata/library-and-framework-list.json` or its
schema. Validates and sorts the master supported-library list against its schema
(§FS-metadata).

### AR-grund-check: Validate grund citations

Triggers on PRs touching any project's scanned roots — `docs/`, `skills/`,
`.ephor/`, the harness build logic, the workflows and composite actions, the
top-level Markdown, a member's docs, agent instructions and scanned code, or a
`grund.toml`. Installs a pinned `grund` release
and runs `grund check` once from the repository root, which is the only scope
where the workspace alias table resolves `§forge/<ID>`,
`§real-use-tests/<ID>` and `<§>graal/GR-<number>`, so the run covers every
namespace. It never fetches: a GraalVM ticket citation resolves against the
snapshot committed in `graalvm-tickets/tickets.md`. It fails on a
citation that no longer resolves, on an `AGENTS.md` managed block that has
drifted from its `grund.toml`, and on a `must`-level citation direction that is
not met (§FS-repository-functional-spec.5.6). A second step reads the test
sources grund does not, `tests/src` and `real-use-tests/src`, which the trigger
also covers, and fails a bare `GR-<number>` in a comment there that has no
snapshot (§PRCPL-graalvm-workaround-tickets.3).

The `grund` version is pinned in the workflow rather than floating: a newer
release can render a newer managed block, which would fail every PR until
someone re-runs `grund init`. Bump the version and its checksum together.

### AR-checkstyle: Checkstyle

Triggers on PRs that change code (excluding `docs/**`, `**.md`, and the framework
list). Runs the `checkstyle` harness task against the changed coordinates; the
source of truth for style rules is `gradle/checkstyle.xml` (§AR-test-harness).
Serves §FS-repository-functional-spec.5.3 — checkstyle is one of the named PR gates.

### AR-scan-docker-images: Scan Docker images

On PRs touching `allowed-docker-images/**`, runs `checkAllowedDockerImages`
between the base and head commits to scan only the changed images with grype; on
a weekly schedule (`0 0 * * 6`) it scans all allowed images. Image management is
part of the harness (§AR-test-harness).
Serves §FS-repository-functional-spec.4.3 — the schedule-driven Docker image
vulnerability scan.

The PR-scoped scan is a ratchet, not an absolute bar. An image that already
appears in the allow list at the base commit must carry no more critical or high
findings than its base-commit version, so a tag bump can only hold or improve the
count. An image with no counterpart at the base commit has nothing to ratchet
against: it is accepted, and its counts — printed in the job log so a reviewer
can weigh them — become the baseline that later changes to that image are
measured against. The alternative, rejecting every first-time image with findings,
makes the gate unsatisfiable for any contribution that needs a container the
repository has not used before, because no realistic tag of a service image is
free of high findings.

The baseline is read from the base commit the task is given rather than from a
fixed branch, so a pull request is measured against the commit it branched from.

### AR-sync-docker-images: Sync Docker images

On PRs touching `allowed-docker-images/**` (Dependabot updates), synchronizes
Docker image tags across the repository and back-commits the synchronized tags
directly into the Dependabot PR, making it merge-ready (§FS-repository-functional-spec.5.3).

### AR-macaron-check: Check GitHub Actions with Macaron

On PRs touching `.github/workflows/**` and on manual dispatch, runs Oracle
Macaron against the `check-github-actions` policy to enforce supply-chain hygiene
on the workflow definitions themselves (for example, pinned action SHAs).
Serves §FS-repository-functional-spec.4.3 — it gates the workflow definitions that
section enumerates.

## 2. Scheduled workflows

See [scheduled CI workflows](ci-scheduled.md) for test sweeps, updates, and releases.

## 3. Event-triggered automation

### AR-triage-new-issues: Triage new issues

Runs when a GitHub issue is opened (ignores pull requests). For user-created
requests, the `library-new-request` label from the issue template makes the issue
eligible. For automated native-build-tools issues with no labels and the standard
`Support for groupId:artifactId:version` title, the workflow adds
`library-new-request` and `priority` first. Once eligible it extracts and
validates the Maven coordinates, closes invalid/duplicate/already-supported
requests, and also closes requests whose `groupId:artifactId` already has an
`index.json` recorded as `not-for-native-image` even when that index carries no
per-version `tested-versions`.

It then closes requests whose artifact cannot be resolved from any repository the
test builds configure (Maven Central and Confluent, per
`org.graalvm.internal.tck.gradle`). Resolution is a closed-world search: a miss
proves only that the artifact is absent from the repositories we query, never
that it does not exist, so the close comment asks the reporter for the repository
URL when the artifact is published somewhere public and points them at the
tracing agent when it is private. The check distinguishes an unknown
`groupId:artifactId` (no `maven-metadata.xml` anywhere) from a known artifact
whose requested version is missing, and reports each with its own message. Only a
definite `404` from every configured repository closes an issue; transport
failures and `5xx` responses leave the issue open for normal triage, so an
upstream outage cannot mass-close valid requests.

The repository list the gate probes is maintained by hand in the workflow and
must be kept in sync with the `repositories` block in
`org.graalvm.internal.tck.gradle`: `mavenCentral()` contributes no URL literal to
extract, so the list cannot be derived from the build. Adding a repository to the
build therefore requires adding it to the workflow in the same change. A workflow
list that has fallen behind the build closes requests the build could in fact
resolve. It then — via
`open-dependency-issues-and-link-blockers.js`
(§AR-shared-scripts) — generates a deps.dev dependency graph and opens or reuses
`library-new-request` issues for unsupported transitive dependencies, linking
them as blockers. Newly created transitive dependency issues do not receive
`priority`, even when the direct request has it. The label vocabulary it applies
is defined in
§FS-repository-functional-spec.4.

Duplicate detection converges without a lock. Requests opened seconds apart,
as a native-build-tools run or a batch of transitive expansions produces them,
are triaged by concurrent runs, each of which lists open issues before any
other run has created its own; a `concurrency` group is no remedy because it
cancels the queued run whenever a third request arrives. The rule that makes
concurrent runs agree is that for one `groupId:artifactId` the lowest-numbered
open request is canonical: a request is closed as a duplicate only when an open
request with a lower number exists. The lowest-numbered request can therefore
never be closed by this rule, and because issue numbers follow creation order,
the run that holds the higher number sees the lower one as soon as the lower
one is visible to reads at all. This workflow lists seconds after the request
was opened, which is long past that point. The dependency script applies the
same rule to the issues it opens, but it lists right after creating them, so it
first pauses for a few seconds to let the concurrent run's creation become
visible, then re-lists the requests opened since its own listing once for all
the issues it created and, where a lower-numbered open request for the same
coordinate appeared meanwhile, closes its own as a duplicate and links the lower
one as the blocker instead. The script must do this itself because issues it
opens with the workflow token raise no `issues: opened` event and never reach
this workflow's duplicate check.

## 4. Composite actions

See [CI composite actions](ci-actions.md) for the reusable setup and change-detection actions.

## AR-shared-scripts: Shared scripts and test isolation

Helpers under `.github/workflows/scripts/` used by the workflows above (these are
`.sh`/`.js` files and are referenced from the workflows, not cited individually):

- **Docker isolation** — `disable-docker.sh` / `restore-docker.sh`, plus
  `dockerd.service` and `discard-port.conf`. After allowed images are pulled, CI
  disables Docker networking so tests run deterministically against pre-pulled
  images with no network access (§FS-repository-functional-spec.5.3,
  §FS-repository-functional-spec.6). You usually do not need this locally.
- **Consecutive version testing** — `run-consecutive-tests.sh` walks a library's
  candidate versions in ascending order, runs the full `test` lane per version,
  and on failure bisects the failing stage (`compileTestJava` → `javaTest` →
  `nativeTestCompile` → `test`) to classify it, recording the first failing
  version and stopping the chain. Drives §AR-verify-new-library-version-compatibility.
- **Spring AOT triage** — `run-spring-aot-triaged-test.sh` runs the triaged
  native Spring AOT smoke tests for the projects computed by
  §AR-test-affected-spring-aot.
- **Dependency issue linking** — `open-dependency-issues-and-link-blockers.js`
  opens/reuses issues for unsupported transitive dependencies and links them as
  blockers, used by §AR-triage-new-issues.
