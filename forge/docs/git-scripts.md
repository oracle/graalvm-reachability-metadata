# GIT-forge-publication: Forge git scripts publication

Git scripts are Forge's publication component for issue resolution
(§FS-forge-issue-resolution-goal). The `git_scripts/` directory holds
workflow-specific `make_pr_*.py` publishers plus shared GitHub and git helpers
in `common_git.py`. Orchestration (§ORCH-forge-orchestration-spec) chooses when
publication is allowed and invokes the matching publisher after a workflow
returns a PR-eligible status (§GIT-pr-eligibility); git scripts own how the
verified diff becomes a labeled, linked pull request. A publisher reads the
workflow context and metrics, stages only the workflow's expected paths
(§GIT-expected-paths), creates the final commit, writes the PR body recording
the run's tracked parameters (§GIT-pr-body), opens the pull request with `gh`,
applies the workflow-specific PR label and issue links (§GIT-issue-linking),
and reports the PR URL back to orchestration for issue/project bookkeeping.
Chunked dynamic-access runs publish their parts incrementally
(§GIT-chunked-linking). Git scripts run after workflow generation and the local
verification step described in §FS-local-ci-equivalent-verification, not during
the strategy loop.

## GIT-pr-eligibility: PR eligibility boundary

Git scripts must publish only statuses that the orchestration layer has already
classified as PR-eligible: `RUN_STATUS_SUCCESS`,
`SUCCESS_WITH_INTERVENTION_STATUS`, or `RUN_STATUS_CHUNK_READY`. They must not
turn a failed workflow into a PR, and they must preserve the verification,
intervention, metrics, and diagnostics context produced by
§FS-local-ci-equivalent-verification in the commit or PR body.

## GIT-expected-paths: Expected path staging

Each git publication script must encode workflow-specific staging policy instead
of a generic `git add .`, staging only the workflow's expected paths: generated
tests, metadata directories, metadata index entries, stats, execution metrics,
and any workflow-specific resumable state. New-library support, Java-fix,
native-run-fix, and coverage-improvement publishers each know which paths are
expected for their workflow; the not-for-native-image publisher
(§GIT-not-for-native-image-publication) stages only the marker
`metadata/<group>/<artifact>/index.json`. Shared repository edits are allowed
only when local verification (§FS-local-ci-equivalent-verification) proved them
necessary, and those paths must be exposed for maintainer review.

## GIT-pr-body: Pull request body contents

Each publication script must record the verified run's tracked parameters in
the PR body so maintainers can review the result without rerunning Forge. The
common contents are the issue reference (§GIT-issue-linking), a human-readable
summary of what the PR changes, the Forge monitored branch / branch / commit,
any post-generation intervention record, and the local CI-equivalent
verification commands and outcomes (§FS-local-ci-equivalent-verification). On
top of that common base, each publisher records only the subset of tracked
parameters its workflow's metrics actually produce. The subsections below state
that per-publisher subset; publishers whose workflows share a body shape are
grouped together.

### New library support and coverage improvement

Publishers: `make_pr_new_library_support.py` (`library-new-request`) and
`make_pr_improve_coverage.py` (`library-update-request`). Both report the agent
generation metrics — strategy, agent, and model; input, cached-input, and output
token counts; iteration count; library-coverage and generated lines-of-code
metrics; metadata-entry counts; and dynamic-access coverage with its supporting
evidence.

They differ only in the stats view: new-library support reports the generated
library stats plus an explanation when covered-call and metadata-entry counts
diverge, while coverage improvement reports a before/after stats diff computed
from the run's baseline snapshot. New-library PRs link with `Fixes:` for a
single-PR run and `Refs:` for non-final large-library parts
(§GIT-chunked-linking).

### Java fail-fix (javac and java-run)

Publishers: `make_pr_javac_fix.py` (`fixes-javac-fail`) and
`make_pr_java_run_fix.py` (`fixes-java-run-fail`). They share one body shape:
the agent generation metrics above, a stats comparison for the bumped version,
and a unified diff between the previous version's and the new version's test
sources so reviewers can see exactly what the fix changed. The two differ only
in workflow identity (compilation vs. runtime wording), the metrics file, and
the PR label (§WF-java-fail-fix-workflow).

### Native-image run-fix

Publisher: `make_pr_ni_run_fix.py` (`fixes-native-image-run-fail`). This
workflow is metadata-first and does not produce agent token metrics, so its body
omits them. It reports the previous and new library-coverage percentages, a
stats diff between the previous and new coordinate, a severe-metadata-drop note
when the new version's metadata shrank suspiciously, and the local
CI-equivalent verification section (§WF-native-image-run-fix-workflow).

### Not-for-native-image

Publisher: `make_pr_not_for_native_image.py` (§GIT-not-for-native-image-publication).
No generation happened, so the body has no generation metrics. It states why the
artifact is not a Native Image target, includes any replacement guidance, the
`Fixes:` issue reference, and the local CI-equivalent verification section.

## GIT-issue-linking: Issue linking and labels

Publication owns user-visible GitHub linkage: PR labels, `Fixes:` issue
references, review text, metrics summaries, and human-intervention visibility.
It must apply the PR label that corresponds to the successful workflow result,
not the issue queue label when those differ. A single-PR workflow links the PR
to its claimed issue with `Fixes: #<issue>`, so merging the PR closes the issue.

## GIT-chunked-linking: Chunked dynamic-access PR linking

Chunked dynamic-access PRs must use `Refs: #<issue>` until the final chunk;
only the final chunk may use `Fixes: #<issue>`, as specified by
§WF-chunked-dynamic-access-pr-linking. This keeps the backing issue open
across chunks and preserves it until the final PR closes it. The exhaust report
state committed by non-final chunks lets the next run resume without
reprocessing classes (§WF-dynamic-access-exhaust-report).

## GIT-not-for-native-image-publication: Not-for-native-image publication

`make_pr_not_for_native_image.py` is the publisher for artifacts that the
`library-new-request` driver judged not to be GraalVM Native Image targets
(§WF-forge-workflow-drivers). It is invoked by orchestration when a completed
new-library run left a `not-for-native-image` marker in the worktree instead of
generated tests and metadata.

The publisher stages only the marker `metadata/<group>/<artifact>/index.json`
(§GIT-expected-paths), runs the same local CI-equivalent verification as the
other publishers (§FS-local-ci-equivalent-verification), and opens a PR whose
body records why the artifact is not a Native Image target plus any replacement
guidance (§GIT-pr-body). It links the PR to the claimed issue with
`Fixes: #<issue>` so merging closes it (§GIT-issue-linking), and applies the
`GenAI`, `library-new-request`, and `not-for-native-image` labels. If local
verification shows that shared repository files changed, it also applies
`human-intervention` and lists those paths for maintainer review
(§FS-human-intervention-policy).
