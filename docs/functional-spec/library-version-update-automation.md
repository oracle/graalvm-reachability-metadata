# FS-library-version-update-automation: Library version update automation
§GOAL-broad-version-coverage §GOAL-fresh-metadata

The repository must keep its tested-version coverage current as upstream
libraries release new versions, without a human watching Maven Central. The
[`verify-new-library-version-compatibility`](../../.github/workflows/verify-new-library-version-compatibility.yml)
GitHub Actions workflow owns this loop. It runs on a schedule (daily)
and on manual `workflow_dispatch`, and performs its mutating steps only on the
canonical `oracle/graalvm-reachability-metadata` repository. Each run discovers
newer upstream versions, tests every candidate across the supported environment
matrix, records the versions that pass as a single bulk-update pull request, and
files one tracking issue per version that fails. The failure issues are the
entry point of the Forge repair queue (§forge/FS-forge-functional-spec,
§forge/AR-forge-orchestration); the bulk-update PR is reviewed by a
maintainer. This section is the behavioral contract for
that workflow; the run-size limits are stated in §FS-repository-functional-spec.5.3
and the recording precondition in §FS-repository-functional-spec.5.2, and every
label it applies is defined in the GitHub label contract
(§FS-repository-functional-spec.4.6.1).

## 1. Discovery of newer versions

`./gradlew fetchExistingLibrariesWithNewerVersions` lists every already-supported
library whose unique `latest: true` index entry opts in with `auto-update: true`
and whose newest upstream release is ahead of its highest tested version,
emitting candidate versions newest-first. Libraries without the marker are
discarded before Maven Central is queried. Before a candidate enters the run it
is de-duplicated against open issues: a library whose newest candidate version
already has an open `[Automation] … fails for <group:artifact:version>` issue is
skipped, so a known-failing version is not re-tested every day.

That de-duplication is suppression while an issue is open, not a decision. A
version whose issue has been closed re-enters the run on the next day. The
permanent exclusion is the artifact's `skipped-versions`, whose entries are
removed from the candidate list before de-duplication and never re-enter it.
The distinction matters because §FS-library-version-update-automation.2 stops a
library at its first failing version: a version that cannot be supported and is
not skipped both re-files its own issue indefinitely and blocks every later
version of that library from ever being tested. Skip entries are written by the
disposition in §FS-contribution-contract.5.4.

`generateNewLibraryVersionCompatibilityMatrix` turns the surviving candidates
into the CI test matrix, bounded by the per-run library budget and the
≤ 30-newer-versions-per-library cap (§FS-repository-functional-spec.5.3). When
candidates remain, the run creates a timestamped working branch
`check-new-library-versions/<timestamp>` that later steps commit to.

## 2. Bulk compatibility testing

A matrix job expands every candidate library across the configured GraalVM
JDK/OS combinations and native-image modes (`ci.json`). For each cell,
[`run-consecutive-tests.sh`](../../.github/workflows/scripts/run-consecutive-tests.sh)
walks the candidate versions in ascending order and runs the full `test` lane
per version. On failure it bisects the failing stage in order —
`compileTestJava` (javac), `javaTest` (JVM run), `nativeTestCompile`
(native-image build), then `test` again (native-image run, retried) — to
classify the failure, records the first failing version, and stops walking that
library (versions are validated consecutively, so a break in compatibility ends
the chain). Each cell reports the versions that passed plus, if any, the first
failure with its stage, failing version, reproducer command, runner-log URL, and
a bounded log tail.

## 3. Recording newly supported versions

Results from all matrix cells are aggregated. A candidate version counts as
supported only when it passed in **every** required environment
(§FS-repository-functional-spec.5.2); the supported set is the intersection
across cells. For each newly supported version the workflow runs
`./gradlew addTestedVersion -Pcoordinates=<group:artifact:version> --lastSupportedVersion=<previous>`,
which appends the version to the artifact's `index.json` and refreshes the
mirrored `stats/` and shared test sources. The updates are committed to the
working branch (staging only `metadata`, `stats`, and `tests/src`) under a
fetch-rebase-retry loop so concurrent scheduled runs do not clobber each other.
When the branch carries changes, the workflow opens one pull request labeled
`library-bulk-update` (reviewers `kimeta`, `vjovanov`, `jormundur00`; assignee
`vjovanov`) summarizing the added tested versions per library. This PR records
already-tested upstream versions and requires no metadata repair, which is what
distinguishes `library-bulk-update` from the per-version repair PRs.

## 4. Opening failure tickets

For every `(library, failing version)` pair the workflow files one aggregated
GitHub issue. The title is `[Automation] <primary failure stage> fails for
<group:artifact:version>` and the labels are `library-unsupported-version` plus
the stage-specific `fails-*` label — `fails-javac-compile`, `fails-java-run`,
`fails-native-image-build`, or `fails-native-image-run` — derived from the stage
that `run-consecutive-tests.sh` isolated. When the library's unique
`latest: true` index entry also sets `high-priority: true`, the issue additionally
receives the `high-priority` label. `fails-native-image-build` issues are
assigned to a maintainer for follow-up; the other three are the queue that Forge
claims and resolves into `fixes-*` repair PRs. The body collects one section per
failing environment (OS, JDK, native-image mode, reproducer command, runner-log
URL, and the log tail), proportionally truncated to stay within GitHub's issue
size limit. Issue creation is idempotent: a pair that already has a matching open
`[Automation] … fails for …` issue is skipped, and a pair that the latest
`origin/master` already supports is treated as a stale failure and skipped.

## 5. Stale-issue hygiene

After recording results, the workflow closes superseded automation issues. When
a full release of a base version is now handled, older pre-release issues
(`alpha`, `beta`, `rc`, milestone, etc.) for the same library are closed as
`not planned`, so the queue reflects only versions that still need attention.
