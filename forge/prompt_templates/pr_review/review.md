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
(§FS-published-pr-review.4.2).

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

A job named `<project> @ spring-aot:<branch> (GraalVM JDK <jdk>)` failed
(§FS-published-pr-review.4.4). First try to fix this pull request's metadata,
under the rules below.

- When the test cannot work because of Native Image behavior, or is flaky — the
  same failure recurs on unrelated pull requests touching the same library, as
  the spring-pulsar and spring-amqp-rabbit startup failures do across Netty pull
  requests — remove it from this pull request's gate: add
  `"<project>": "<reason, with the evidence>"` to the JSON object in
  `.github/spring-aot-excluded-tests.json` (§root/FS-repository-functional-spec.5.3),
  commit, and push as in case 3.
- When the cause is a bug in a dependent library or in Native Image 25.0, it
  cannot be fixed here: the outcome is an issue (case 6). Spring smoke tests run
  on the newest GraalVM LTS, so a Native Image fix that is only on master is such
  a bug.

## 5. Fix first

Always try to fix the pull request first (§FS-published-pr-review.4.5). A test of
ours under `tests/src` that fails only because Native Image does not support a
behavior is deleted or re-scoped under §root/FS-test-contract.4.3.2. Anything else
you cannot fix that looks like a Native Image error in something that usually
works: investigate the `oracle/graal` history, and the outcome is an issue.

## 6. The issue

Do not open it yourself; describe it in your answer, and the program reuses an
open issue with the same signature or opens it, then labels the pull request
(§FS-published-pr-review.4.6). The body names the failing lane (JDK, mode, library
version), quotes the error, gives the local reproduction with exact versions, the
suspected Native Image cause with `oracle/graal` commit and pull request links,
and why it is not our metadata. The signature is a short stable key of the
failure, the same for every pull request it breaks, such as
`ni-25.0-spring-security-type-not-found`.

## Metadata rules

These are the rules of the run's `fix` state, and they bind your repair:

{metadata_rules}

## What you must not do

Do not label the pull request, comment on it, or open issues: the program does
that from your answer. Never rebase or force-push: to bring `master` in, merge
`origin/master`, so your commit descends from `{head}`. Change only
`metadata/{group}/{artifact}/`, `tests/src/{group}/{artifact}/`,
`stats/{group}/{artifact}/`, and `.github/spring-aot-excluded-tests.json`.

## Your answer

Before your final message, write `{outcome}` as JSON:

- `outcome`: `approve`, `rerun`, `fix`, or `issue` — what you did{did_suffix}.
- `case`: the number of the case above that applied.
- `cause`: one line naming the cause, such as `metadata miss`,
  `Native Image 25.0 bug`, `flaky smoke test`, or `Gradle plugin resolution`.
- `summary`: the evidence in a few sentences, naming the failing job.
- For `issue` only, `issue`: an object with `title`, `body`, and `signature`.

For example: `{{"outcome": "rerun", "case": 2, "cause": "Gradle plugin resolution", "summary": "..."}}`.
