# Diagnose the failing CI of pull request #{number}

Pull request #{number} in `{repo}` (route label `{label}`, coordinate `{coordinate}`)
fails CI on its head `{head}` (branch `{branch}`, base `{base}`). Your working
directory is a detached checkout of exactly that head. The failing jobs and the
tail of each failed log are in `{evidence}`. Read it first.

The run that produced this pull request already passed the coordinate's own
native lanes, so the cause is most likely something those lanes do not cover:
the Spring AOT smoke tests, another tested version that shares the metadata,
`master` moving under the pull request, transient infrastructure, or a flaky
test (§FS-published-pr-ci-failures).

Find the root cause. Reproduce the failing job locally when that helps, read the
library's and GraalVM's sources, issues, and release notes on the web when the
failure may come from them. Then classify it as exactly one cause.

## Causes

1. **`transient`**: a runner failure, a network or Docker-pull failure, or a
   cancelled job the change cannot have caused
   (§FS-published-pr-ci-failures.3.1). Change nothing; the failed jobs are rerun.
2. **`defect`**: the contribution's own files cause it — a test, the metadata,
   another tested version sharing the metadata, or an `index.json` that `master`
   moved under (§FS-published-pr-ci-failures.3.2). Repair it in this checkout,
   inside the contribution's file set only: `metadata/{group}/{artifact}/`,
   `tests/src/{group}/{artifact}/`, and `stats/{group}/{artifact}/`
   (§root/FS-contribution-contract.5.2).
3. **`flaky`**: a test fails without a change to its inputs
   (§FS-published-pr-ci-failures.3.3). Make it deterministic under
   §root/FS-test-contract; never delete, disable, or weaken it.
4. **`outside`**: a GraalVM change or regression, a Spring AOT failure caused by
   GraalVM, a harness or workflow defect, or anything else the contribution's
   files cannot fix (§FS-published-pr-ci-failures.3.4). Change nothing; describe
   the issue to open.

Fix pushes left on this pull request: {pushes_left}. With none left, still
report the true cause; the program then opens an issue instead of pushing.

## Repairing a `defect` or a `flaky` test

- Never rebase. To bring `master` in, run `git fetch origin master` and
  `git merge --no-edit FETCH_HEAD`; your commit must descend from `{head}`.
- **Index buckets.** When `validateIndexFiles` fails because tested versions of
  `metadata/{group}/{artifact}/index.json` sit in the wrong metadata-version
  bucket after `master` moved: merge `master` first so the right bucket exists,
  then move each tested version that is at or above the next entry's
  `metadata-version` into that later entry, and remove a tested version from an
  older entry when the version has its own entry. Keep every other field —
  URLs, `allowed-packages`, `requires`, `latest`, `metadata-version` — and the
  file's formatting and version order. `./gradlew validateIndexFiles
  -Pcoordinates=all` must pass.
- **Another tested version.** When CI fails for a tested version that shares
  this metadata, reproduce it with
  `GVM_TCK_LV=<version> ./gradlew test -Pcoordinates={coordinate}` and fix the
  metadata or the test so that every tested version passes.
- Verify with the failing job's own command, run locally from the repository
  root, plus `./gradlew checkMetadataFiles -Pcoordinates={coordinate}` when the
  metadata or `index.json` changed and `./gradlew spotlessCheck checkstyle`
  when a test changed. Commit only when every command passes, with a subject
  under 60 characters and no attribution.

## What you must not do

Do not push, and do not run any `gh` command that writes: no comment, label,
rerun, issue, review, or edit. The program performs the action your diagnosis
names after checking it.

## Your answer

Write `{diagnosis}` as JSON with these fields:

- `cause`: `transient`, `defect`, `flaky`, or `outside`.
- `summary`: the root cause and its evidence in a few sentences, naming the
  failing job.
- For `outside` only, `issue`: an object with `title`, `body`, and
  `signature`. The body carries the whole diagnosis — the failing job, a log
  excerpt, the root cause, and the evidence. The signature is a short stable
  key of the failure, the same for every pull request it breaks, such as
  `graalvm-ea-25.1-spring-aot-proxy-npe`.

For example: `{{"cause": "defect", "summary": "..."}}`.
