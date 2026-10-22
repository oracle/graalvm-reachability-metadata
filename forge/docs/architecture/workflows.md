# AR-forge-workflow-system: Forge workflows (retired)

§FS-forge-issue-resolution-goal

The registered workflow engines, their strategies, and the native test
verification gate they shared are gone: every issue run is the one Rhei
template of §AR-forge-workflow-pipeline, and exploration is its `analyze` and
`measure` programs (§AR-forge-workflow-pipeline.7). The declarations below stay
only until the last citation of each is removed; the exhaust report and chunk
linking are still live.

## AR-java-fail-fix-workflow: Java fix workflows (retired)

Retired: `repair` and one fix request replace it (§AR-forge-workflow-pipeline.4) (§FS-forge-issue-resolution-goal).

## AR-dynamic-access-workflow: Dynamic-access exploration (retired)

Retired: §AR-forge-workflow-pipeline.7 replaces it (§FS-forge-issue-resolution-goal).

## AR-dynamic-access-iterative: Iterative exploration (retired)

Retired: class mode of §AR-forge-workflow-pipeline.7 replaces it (§FS-forge-issue-resolution-goal).

## AR-dynamic-access-bulk: Bulk exploration (retired)

Retired: bulk mode of §AR-forge-workflow-pipeline.7 replaces it (§FS-forge-issue-resolution-goal).

## AR-dynamic-access-composite: Composite fix-then-explore (retired)

Retired: `repair` followed by exploration replaces it (§AR-forge-workflow-pipeline.3) (§FS-forge-issue-resolution-goal).

## AR-dynamic-access-fallback-and-failure: Fallback and failure (retired)

Retired: the explore decision of §AR-forge-workflow-pipeline.7 replaces it (§FS-forge-issue-resolution-goal).

## AR-dynamic-access-exhaust-report: Exhaust report

The exhaust report is the durable, coordinate-scoped record of which classes a
coordinate has already processed, and the state a chunked run resumes from
(§FS-forge-chunked-dynamic-access). It is intentionally minimal: the coordinate
and issue, the class threshold and current chunk count, the class names recorded
as completed, skipped, exhausted, or failed, and the latest chunk's publication
identity.

It deliberately does not store a chunk manifest. Every later run's `analyze`
measures the dynamic-access report afresh and filters out the classes already
recorded, so a
resume reflects the library's current surface rather than a plan made against a
stale one. Its location is derived from the coordinate and it is stored with the
library's test suite, so each merged chunk carries the state the next run needs
without any resume argument being passed.

Repository-wide stats stay authoritative: coverage is reported against the full
current dynamic-access surface, and a chunk may additionally report how many
classes it processed and how many remain.

## AR-chunked-dynamic-access-pr-linking: Chunk PR linking

A chunk must link its pull request to the issue without completing it, because
the issue is only done when the last chunk lands (§FS-forge-chunked-dynamic-access). Non-final chunk PRs reference
the issue; only the final chunk PR closes it and moves it to done. Every chunk PR
carries the merged exhaust state the next run needs. Iterative exploration can
attribute completed, skipped, exhausted, and failed classes and records all four
states its run can attribute: `measure` records the classes a pass fully
covered and the ones class mode exhausted (§AR-forge-workflow-pipeline.7).

Each chunk also records its publication identity and unique head branch in the
exhaust report before the verified push, which is how a later chunk resolves the
preceding PR without committing a GitHub-assigned number back to the branch. The
publication mechanism is §AR-chunked-linking.
