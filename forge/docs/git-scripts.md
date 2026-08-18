# GIT-forge-publication: Forge branch and pull-request publication

Forge publication has a local branch-finalization half and a trusted GitHub
Actions half (§FS-forge-issue-resolution-goal). The `git_scripts/` directory
owns the local half: workflow-specific expected-path staging, rebase,
pre-publication verification, descriptor creation, the single final commit, and
the direct push to the upstream `ai/**` branch
(§GIT-shared-publication-pipeline).

After an unprivileged Branch Ready workflow accepts that exact SHA, publisher
code and templates loaded from the default branch own the GitHub half
(§GIT-actions-publication): trusted rendering, labels, reviewers, idempotent PR
creation, and publication reporting. Publication opens the pull request and
nothing else; every issue and project mutation stays local, where Forge already
owns the claimed issue. The feature branch supplies data only; neither it nor
the local Forge process receives the publisher App credentials.
Chunked dynamic-access runs identify each part before the push
(§GIT-chunked-linking). Local finalization runs after workflow generation and
the verification required by §FS-local-ci-equivalent-verification, never during
the strategy loop.

## GIT-pr-eligibility: PR eligibility boundary

Local finalization accepts only statuses that orchestration has already
classified as PR-eligible: `RUN_STATUS_SUCCESS`,
`SUCCESS_WITH_INTERVENTION_STATUS`, or `RUN_STATUS_CHUNK_READY`. It must not
turn a failed workflow into a pushed publication branch. The descriptor and
commit preserve the verification, intervention, metrics, and diagnostic context
that the trusted publisher later renders.

## GIT-shared-publication-pipeline: Shared branch publication pipeline

Every workflow route must use one shared local finalization pipeline. Routes
contribute only their expected paths, commit wording, and bounded hooks needed
before rebase or verification. The pipeline creates a publication ID and unique
`ai/<producer>/<suffix>-<publication-id>` branch, stages expected paths, rebases
onto a fresh upstream `master`, runs the pre-publication verification gate,
writes and validates the descriptor, commits any resulting changes, and pushes
the final HEAD directly to `oracle/graalvm-reachability-metadata`.

The descriptor and every file used by the publisher must be in that final
commit. Local Forge must neither run `gh pr create` nor make a second
post-publication bookkeeping commit. A resumed publication reuses its persisted
publication ID and branch. Once the exact branch has been pushed, local
finalization completes without waiting for Actions to create the PR.

## GIT-expected-paths: Expected path staging

Each local publication route must encode workflow-specific staging policy
instead of a generic `git add .`, staging only generated tests, metadata
directories, metadata index entries, stats, execution metrics, the publication
descriptor, and workflow-specific resumable state. New-library support,
Java-fix, native-run-fix, and coverage-improvement routes each define their
expected paths; the not-for-native-image route
(§GIT-not-for-native-image-publication) stages the marker
`metadata/<group>/<artifact>/index.json`, its stats publication path, and the
descriptor. Shared repository edits are allowed only when local verification
(§FS-local-ci-equivalent-verification) proved them necessary, and those paths

## GIT-publication-descriptor: Durable publication descriptor

Local finalization writes schema version `1` to
`stats/<group>/<artifact>/<version>/forge-publication.json` and validates it
against `.github/scripts/forge_pr_publisher/schema.json` before the publication
commit. Exactly one descriptor path may change in a publication diff. The file
remains after merge; a later publication for the coordinate replaces the
current file while Git history retains prior provenance.

The descriptor contains data, never GitHub instructions:

- `schema_version`, the stable `publication_id`, UTC `timestamp`, unique
  `branch`, producer login, upstream base commit, issue number, target library,
  and optional previous library;
- `task_type`, which selects the trusted primary label, and `template_type`,
  which selects a compatible trusted renderer when a library-update request was
  repaired through a Java or Native Image route;
- workflow status, strategy name when applicable, committed execution-metrics
  reference and publication metrics, local verification evidence, optional
  post-generation intervention, and Forge revision evidence;
- typed flags for chunking, final-chunk state, and human-intervention evidence;
- typed follow-up facts for deferred dynamic-access coverage or a tested-version
  split, each carrying the number of the issue Forge already opened locally, so
  the publisher only references it.

The descriptor cannot contain labels, reviewer names, template paths, token
permissions, arbitrary commands, or a requested publication mode. The schema
and trusted route table reject unknown task/template combinations and missing
route-specific fields. The pushed head SHA is intentionally not a descriptor
field because a commit cannot contain its own object ID; both Actions workflows
take the exact SHA from the GitHub event and validate the descriptor at that
commit.

Publication identity is derived from durable run inputs so a resumed
publication recreates the same ID, descriptor, and branch. A fresh run for the
same issue gets a distinct identity. The producer must equal the authenticated
login that owns the `ai/<producer>/...` branch.

## GIT-actions-publication: Trusted GitHub Actions publisher

`Forge Branch Ready` runs on pushes to upstream `ai/**` branches with read-only
repository permissions and no secrets. It treats the head tree as data, requires
one schema-valid descriptor, checks the branch namespace and descriptor/branch
identity, and validates changed paths against the task-specific allow-list. It
must not execute scripts, actions, build files, or other code from the feature
branch and must not create or modify GitHub resources.

A Branch Ready failure leaves the branch, issue assignment, labels, and project
status unchanged. Its job summary and logs must identify the exact SHA and each
validation error so a maintainer can inspect or repair the preserved branch and
push again.

`Forge Open PR` is triggered only by successful completion of Branch Ready via
`workflow_run`. It loads its workflow implementation, schema, route table, and
templates from the default branch. It materializes the triggering head SHA only
as publisher input and re-runs every security and descriptor validation instead
of trusting the preceding job. Feature-branch code is never imported or
executed in the credentialed process.

Before mutation the publisher must verify all of the following:

- the head repository is `oracle/graalvm-reachability-metadata`, the head branch
  is the descriptor branch under `ai/<producer>/`, and the workflow-run head SHA
  is the exact object being read;
- the triggering actor equals the descriptor producer and is listed in the
  trusted comma-separated repository variable `FORGE_AUTHORIZED_PUSHERS`;
- the base commit is an ancestor of the head SHA and of the trusted base branch,
  and exactly one descriptor changed in the publication diff;
- the coordinates, descriptor path, and publication identity agree, and the
  descriptor carries the fields the selected template renders.

The publisher does not re-check the changed-path scope, the execution metrics,
or the local verification evidence. Local CI already gated that work
(§FS-local-ci-equivalent-verification), and re-deriving it from branch-supplied
data proves nothing the branch could not also assert.

The workflow creates a short-lived token from
`FORGE_PUBLISHER_APP_ID` and `FORGE_PUBLISHER_PRIVATE_KEY`. The App is granted
only the repository pull-request and content-read permissions needed to open and
label the pull request. Reviewer requests come only from the trusted comma-separated
repository variable `FORGE_PR_REVIEWERS`. The producer remains eligible to
review the bot-authored PR.

The publisher renders the PR, applies only the fixed primary label and trusted
modifiers (`GenAI`, `chunked-dynamic-access`, and `human-intervention`),
requests configured reviewers, and records the PR URL in the job summary. It
references follow-up issues by the number the descriptor carries; creation and
project parking already happened locally, keyed off durable run state so a
retried run reuses the same issue.

`FORGE_PR_PUBLISH_MODE` controls rollout. A missing value or `shadow` renders
and uploads the title/body evidence without creating GitHub resources; only
`live` enables the App-backed mutations. For an existing PR with the same exact
head branch and publication ID, an open or merged PR is a successful no-op. A
closed, unmerged PR or ambiguous match fails for manual inspection. Any
publisher failure preserves the pushed branch and claimed issue state.
must be listed in descriptor verification evidence for maintainer review.

## GIT-pr-body: Pull request body contents

The trusted default-branch renderer records the verified run's tracked
parameters in the PR body so maintainers can review the result without rerunning
Forge. Common contents are the issue reference (§GIT-issue-linking), a
human-readable summary, Forge branch/revision evidence, publication ID trailer,
any post-generation intervention, and local verification commands and outcomes
(§FS-local-ci-equivalent-verification). On top of that common base, each trusted
template records only the subset of tracked parameters its workflow actually
produces. The subsections below state that per-template subset; routes whose
workflows share a body shape are grouped together.

PR bodies must remain publishable through GitHub. The shared publication helper
therefore bounds optional generated detail below GitHub's body limit while
preserving the issue link, summary, metrics, intervention record, and local CI
evidence. Version-to-version test comparisons include a diff stat and a bounded
excerpt; reviewers use the PR's **Files changed** tab for the complete diff.

### New library support and coverage improvement

Trusted templates `library-new-request` and `library-update-request` both report
the agent
generation metrics — strategy, agent, and model; input, cached-input, and output
token counts; iteration count; library-coverage and generated lines-of-code
metrics; metadata-entry counts; and dynamic-access coverage with its supporting
evidence.

They differ only in the stats view: new-library support reports the generated
library stats plus an explanation when covered-call and metadata-entry counts
diverge, while coverage improvement reports a before/after stats diff computed
from the run's baseline snapshot. New-library PRs link with `Fixes:` for a
single-PR run and `Refs:` for non-final chunked dynamic-access chunks
(§GIT-chunked-linking).

### Java fail-fix (javac and java-run)

Trusted templates `fixes-javac-fail` and `fixes-java-run-fail` share one body
shape:
the agent generation metrics, a stats comparison for the bumped version, and a
bounded test-source comparison so reviewers can see what the fix changed without
preventing PR creation. The two differ only in workflow identity (compilation
vs. runtime wording), the metrics file, and the PR label
(§WF-java-fail-fix-workflow).

When post-repair dynamic-access exploration is skipped because the report
exceeds the configured class threshold, the body reports only what that run
actually did: the strategy, agent, model, token, and iteration summary, then a
deferred-exploration section, Forge revision details, and the test-source
comparison. Metadata-entry counts, coverage percentages, and the stats
comparison are omitted, because they would describe coverage work the run never
attempted and invite reading a deliberate deferral as a regression. Runs that do
explore keep the full body unchanged.

The deferred-exploration section shows the uncovered class count and configured
threshold, then links the new fixed-version `library-update-request`. The body
retains `Fixes: #<repair-issue>`, adds `Refs: #<coverage-issue>`, and includes
`Forge-Unblocks-Issue: #<coverage-issue>` so orchestration releases the parked
coverage issue only after the repair PR merges (§WF-java-fail-fix-workflow).
The locally generated follow-up issue keeps the coordinate in its title and uses one
brief sentence stating that it was opened while resolving the repair issue
because the dynamic-access class count exceeded the threshold.

Neither template blocks on a dynamic-access category regression between the
previous and repaired version; coverage trade-offs are settled in review
(§WF-java-fail-fix-workflow).

### Native-image run-fix

The trusted `fixes-native-image-run-fail` template represents a workflow that
workflow is metadata-first and does not produce agent token metrics, so its body
omits them. It reports the previous and new library-coverage percentages, a
stats diff between the previous and new coordinate, a severe-metadata-drop note
when the new version's metadata shrank suspiciously, and the local
CI-equivalent verification section (§WF-native-image-run-fix-workflow).

### Not-for-native-image

The trusted `not-for-native-image` template follows
§GIT-not-for-native-image-publication.
No generation happened, so the body has no generation metrics. It states why the
artifact is not a Native Image target, includes any replacement guidance, the
`Fixes:` issue reference, and the local CI-equivalent verification section.

## GIT-pr-preview-builders: Reusable title and body builders

The default-branch publisher exposes one non-mutating render command that reads
a validated descriptor and exact feature tree. Both live and shadow publication
call that renderer before any GitHub mutation, making descriptor-plus-tree the
publication source of truth.

## GIT-issue-linking: Issue linking and labels

Publication owns user-visible GitHub linkage: PR labels, `Fixes:` issue
references, review text, metrics summaries, and human-intervention visibility.
It must apply the PR label that corresponds to the successful workflow result,
not the issue queue label when those differ. A single-PR workflow links the PR
to its claimed issue with `Fixes: #<issue>`, so merging the PR closes the issue.
When a library-update publication splits tested versions according to
§FS-library-update-tested-version-split, the PR body must also include a
human-visible `Refs: #<follow-up-issue>` line and a machine-readable
`Forge-Unblocks-Issue: #<follow-up-issue>` trailer. Forge automation must use
the trailer, not casual issue references, to release the follow-up issue after
the PR merges.

Deferred Java-fix coverage and tested-version splits do not create follow-up
issues locally. Their typed descriptor facts are resolved by the trusted
publisher, which searches for an existing matching issue before creating one,
applies `library-update-request`, and parks the project item in `In Progress`.
It then renders the resolved number into `Refs:` and
`Forge-Unblocks-Issue:`. Retrying publication must reuse the same issue.

## GIT-chunked-linking: Chunked dynamic-access PR linking

Chunked dynamic-access PRs carry the `chunked-dynamic-access` label and use
`Refs: #<issue>` until the final chunk; only the final chunk may use
`Fixes: #<issue>`, as specified by §WF-chunked-dynamic-access-pr-linking.

Before the one final push, the exhaust report stores the publication ID and
unique head branch also present in the descriptor. The publisher adds
`Forge-Publication-ID: <id>` to the PR body. A successive run loads that merged
JSON, resolves the PR by exact head repository and branch, requires one matching
publication trailer, verifies that it is merged, and uses its GitHub merge
commit for the base-ancestry check. The publisher never commits the assigned PR
number back to the branch, so the exact validated SHA remains unchanged.
Previously committed reports that contain `latestChunkPullRequest` remain
readable during migration.

## GIT-not-for-native-image-publication: Not-for-native-image publication

The `not-for-native-image` route handles artifacts that the
`library-new-request` driver judged not to be GraalVM Native Image targets
(§WF-forge-workflow-drivers). Local finalization stages the marker
`metadata/<group>/<artifact>/index.json`, its publication stats path, and the
descriptor, then runs the same verification and push boundary as other routes.

The trusted publisher renders the reason and replacement guidance, links the PR
with `Fixes: #<issue>`, and applies the fixed `GenAI`,
`library-new-request`, and `not-for-native-image` labels. When descriptor
verification evidence lists shared repository changes, it also applies
`human-intervention` and includes those paths for maintainer review
(§FS-human-intervention-policy).
