# Review pull request #{number}

Pull request #{number} in `{repo}` was published by Forge for `{coordinate}`
(route label `{label}`, branch `{branch}`). Your working directory is a detached
checkout of its head `{head}`, and `origin/master` is fetched. Read these first:

- `{body}`: the pull request's title and body. The body renders the run's local
  review from its descriptor: the audit, the reviewer gates, and the native lanes.
- `{evidence}`: every check on the head with its conclusion, and the tail of each
  failed job's log. Each job URL names its run: `.../actions/runs/<run-id>/job/<job-id>`.

{checks}

Left on this pull request: {reruns_left} rerun of this head and {pushes_left} fix
pushes. When the action a case calls for has none left, the outcome is an issue.
{dry_run}
Take the first case below that applies (§FS-published-pr-review.4).

## 1. All green

{approval}

## 2. Infrastructure failure

Every failing job died of something the change cannot have caused: the network,
the runner, Gradle plugin resolution, a Docker pull, or a cancelled job. Rerun the
failed jobs of each such run with `gh run rerun <run-id> --repo {repo} --failed`
(§FS-published-pr-review.4.2). Check this case first for every failure, Spring
AOT included. A connection error is transient only when nothing in its stack
points at Native Image: `Unable to create Channel from class ...EpollSocketChannel`
or an `ExceptionInInitializerError` is a metadata miss that looks like the network.

## 3. A library test lane failed

A lane of the changed-metadata matrix failed, named like
`<group>:<artifact>:<version> [batch …] [<mode>] (GraalVM for JDK <jdk> @ <os>)`,
for this library or a dependent one. Find the cause (§FS-published-pr-review.4.3).

- **A metadata miss for `{coordinate}`**, such as a missing registration or a
  condition reached too late: fix it under the metadata rules below. Run that
  exact lane locally — the same JDK, mode, and library version — from the
  repository root with `GVM_TCK_NATIVE_IMAGE_MODE=<mode> GVM_TCK_LV=<version>
  ./gradlew test -Pcoordinates=<the lane's coordinate>`, commit with a subject
  under 60 characters and no attribution, and push to the head branch with
  `git push origin HEAD:refs/heads/{branch}`.
- **Otherwise compare the lanes.**
  - JDK 25 `current-defaults` fails but `latest-ea` passes: reproduce it locally
    with the exact GraalVM build the lane used, read from its log (for example
    `graalvm-jdk-25.0.4+7.1`), downloaded into `~/.cache/forge-review/graalvm/`
    when it is not installed. A local failure is most likely a Native Image fix
    that was never backported to 25.0: confirm it by finding the fix on
    `oracle/graal` master — commits, pull requests, issues — and its absence on
    the release branch.
  - `latest-ea` fails, and fails locally with the same configuration: most likely
    a regression in the newest GraalVM. Confirm it in the `oracle/graal` history.
  - Either way the outcome is an issue (case 6).

## 4. A Spring AOT smoke test failed

A job named `<project> @ spring-aot:<branch> (GraalVM JDK <jdk>)` failed, and
case 2 does not apply (§FS-published-pr-review.4.4). There are two outcomes:

- **A metadata miss of `{coordinate}`**: fix it under the metadata rules below.
  Reproduce and verify with the job's own script: check out
  `spring-projects/spring-aot-smoke-tests` at the `ref` pinned in
  `.github/workflows/test-affected-spring-aot-main.yml` into
  `~/.cache/forge-review/spring-aot-smoke-tests`, then run
  `bash .github/workflows/scripts/run-spring-aot-triaged-test.sh <that path>
  '<project>' <branch>` from the repository root. Commit and push as in case 3.
- **Anything else**: the outcome is an issue (case 6). Do not decide whether
  the bug is in Native Image or in Spring, and do not search the `oracle/graal`
  history: the issue names this pull request, the failing project and branch,
  and the error, and asks its assignee to find whether the bug is on the Native
  Image side or the Spring side.

## 5. Fix first

Always try to fix the pull request first (§FS-published-pr-review.4.5). A test of
ours under `tests/src` that fails only because Native Image does not support a
behavior is deleted or re-scoped under §root/FS-test-contract.4.3.2. Anything else
in a library lane that you cannot fix and that looks like a Native Image error in
something that usually works: investigate the `oracle/graal` history, and the
outcome is an issue.

## 6. The issue

Do not open it yourself; describe it in your answer, and the program reuses an
open issue with the same signature or opens it, then labels the pull request
(§FS-published-pr-review.4.6). The body names the failing lane (JDK, mode, library
version), quotes the error, and says why it is not our metadata. For a library
lane it adds the local reproduction with exact versions and the suspected Native
Image cause with `oracle/graal` commit and pull request links. For a Spring AOT
smoke test it adds the project and branch, and asks the assignee to find whether
the bug is on the Native Image side or the Spring side. The signature is a short stable key of the
failure, the same for every pull request it breaks, such as
`ni-25.0-spring-security-type-not-found`. When the evidence is a GraalVM defect — a builder-internal
`VMError`, `HostedError` or `shouldNotReachHere`, or forbidden metadata the agent emits on every
generation — say so in `cause` and quote it in the body: a maintainer files the GraalVM ticket from it
(§root/FS-graalvm-blocked-work.1).

## Metadata rules

These are the rules of the run's `fix` state, and they bind your repair:

{metadata_rules}

## What you must not do

Do not label the pull request, comment on it, or open issues: the program does
that from your answer. Never rebase or force-push: to bring `master` in, merge
`origin/master`, so your commit descends from `{head}`. Stay inside the
contribution's file set (§root/FS-contribution-contract.2): the version's own
metadata, index, stats, and tests; routed owner metadata that only adds entries
conditioned on the owner's packages; and an allowed-Docker-image file the test
lists. The program rejects a fix that changes anything else.

## Your answer

Before your final message, write `{outcome}` as JSON:

- `outcome`: `approve`, `rerun`, `fix`, or `issue` — what you did{did_suffix}.
- `case`: the number of the case above that applied.
- `cause`: one line naming the cause, such as `metadata miss`,
  `Native Image 25.0 bug`, `Spring AOT smoke test`, or `Gradle plugin resolution`.
- `summary`: the evidence in a few sentences, naming the failing job.
- For `issue` only, `issue`: an object with `title`, `body`, and `signature`.

For example: `{{"outcome": "rerun", "case": 2, "cause": "Gradle plugin resolution", "summary": "..."}}`.
