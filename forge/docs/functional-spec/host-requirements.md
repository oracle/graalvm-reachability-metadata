# FS-forge-host-requirements: Host requirements

§GOAL-shorten-issue-to-shipped-metadata §root/PRCPL-verify-inputs

Before Forge performs a self-update, queries queue state, claims an issue,
creates a review worktree, or invokes an agent, it must validate every host
capability the invoked mode needs, and it must do so without invoking a model or
mutating GitHub. This section is the complete list: what `forge_metadata.py`
needs in order to work is what the host requirements gate checks, and the
requirement has exactly one definition, so a repeated check elsewhere resolves
to the same rule rather than a second one.

The host must provide:

- **Executables on `PATH`**: the selected Python interpreter, `git`, the GitHub
  CLI, the agent backends the enabled roles select, the Docker CLI, the pinned
  `grype` release, the pinned `rhei` release, and the selected
  repository's Gradle wrapper. Every mode requires `rhei`, because each
  launcher runs its work as a Rhei workflow, and the shipped templates are
  written in the plan language of exactly that release.
- **A usable GitHub account**: the CLI authenticated, `Contents`, `Issues`, and
  `Pull requests` write on the repository, and `Projects` write on the tracked
  project board — read access alone is not enough, because Forge assigns and
  labels issues, comments, pushes generated branches, submits reviews, and
  merges eligible pull requests. The local process never holds the Forge
  publisher App credentials: it neither opens pull requests nor creates
  publication follow-up issues.
- **Agents that are usable, not merely installed**: each selected backend
  authenticated against its provider with unattended tool approval and a
  writable state directory. Validation covers only the executables, models, and
  state roots the enabled roles actually select
  (§FS-forge-agent-runtime-selection) — supporting a backend is not a reason to
  require it.
- **Every Java lane**, each pointing at a GraalVM distribution that provides
  Native Image, allows its `bin/java` to load the `native-image-agent`, and
  contains the reachability-metadata schema the checked-out repository requires:
  `GRAALVM_HOME` at the latest published GraalVM GA
  release, `GRAALVM_HOME_LATEST_EA` at the newest published Oracle GraalVM EA
  build, and `GRAALVM_HOME_25_0` at the repository-pinned 25.0.x release in
  `graalvm-versions.json`. The agent probe writes to a disposable output
  directory, so it verifies the JVM capability metadata generation depends on
  without leaving generated configuration behind. `GRAALVM_HOME` also provides
  `native-image-utils`, in `bin` or `lib/svm/bin`: metadata generation merges
  the agent's trace output with it, so a distribution without it cannot produce
  metadata. The operator updates the pinned value deliberately;
  GA and EA freshness are resolved from their authoritative release metadata on
  every run. An Oracle EA release label can advance without the runtime or
  JVMCI revision its binaries report changing, so the gate identifies the EA
  build by its JDK version, its Graal major.minor, and the `oracle/graal`
  commit its release notes name, which the installed `release` file records. `GRAALVM_HOME` and `JAVA_HOME` are aligned to one
  distribution, and `GRADLE_JAVA_HOME` and Gradle's `org.gradle.java.home`
  system property are pinned to that same distribution, so inherited host
  `GRADLE_OPTS` cannot silently hand Gradle test workers an agent-less JVM. A
  review-only host needs a working JDK on `JAVA_HOME` instead of the lanes.
  Every agent repair step runs against the exact distribution whose Gradle or
  Native Image failure triggered it — the selected `GRAALVM_HOME`, `JAVA_HOME`,
  and full `native-image --version` output go into the agent's instructions and
  its environment — and Forge fails rather than reproducing or verifying against
  a different installation.
- **Access to the Docker daemon**, not just the client.
- **Filesystem write access** to the Forge checkout, the `.git` directory of
  every checkout the run writes branches into, `local_repositories`, and
  temporary Gradle state.
- **Network reachability** on 443 to GitHub, the agent provider, the Gradle
  distribution and plugin services, Maven Central, and the Docker registry and
  its auth endpoint. Reachability is checked over the route Forge's own tools
  take — directly, or through the proxy the environment configures for HTTPS —
  so a proxied host is not reported as offline. Every configured artifact
  repository is checked at its exact base URL with an HTTP `HEAD` request and
  must return `200`, so Forge proves the same DNS, proxy, TLS, and HTTP path the
  issue-form artifact lookup will use before it claims an issue.
- **Git transport** to the monitored branch from every checkout the run uses.
- **A complete reachability repository** for anything that runs its Gradle
  tasks: testing, dynamic-access reporting, native metadata exploration,
  metadata generation, and final verification must run inside a whole
  `graalvm-reachability-metadata` checkout or worktree — repository root,
  `gradlew`, Gradle build logic, shared test infrastructure, `metadata/`,
  `tests/`, and `forge/` when the run records metrics, logs, or resumable
  state — never in a copied per-library directory, an extracted dependency
  artifact, or any partial tree. Isolated worktrees, resumable artifacts, and
  archives must each preserve that whole context; missing repository context is
  a hard setup failure.

Two path sets are validated for what each owns. Forge-owned paths — the Forge
checkout, its `local_repositories`, the pinned GraalVM configuration, and the
self-update remote — always resolve from the checkout that contains Forge.
Repository-owned paths — the Gradle wrapper, the Git metadata generated branches
are written into, and the origin remote they are pushed to — resolve from the
repository the run selected, which may be a different checkout. The selected
repository is therefore resolved before the gate runs, so a run against another
checkout can neither pass on a broken target nor be rejected because an
unrelated parent checkout is broken.

Requirements are scoped to what the invoked mode actually does: a review-only
run does not need the issue-work Java lanes or Docker; an issue run
needs everything its enabled queues select; a fixture-testing run needs no live
GitHub access, because it mutates no GitHub state. Only the release-version
comparison for the Java lanes is relaxable, and relaxing it never waives Native
Image, the `native-image-agent`, or the reachability-metadata schema.

Code-coverage work is a separate host-requirement mode because its operator
entry point does not pass through `forge_metadata.py`. It needs the build
capabilities used by issue work, but only one GraalVM distribution: use
`GRAALVM_HOME`, taking the value of `JAVA_HOME` when it is unset, and require
JDK 25 or newer with Native Image, native-image-agent, and the
reachability-metadata schema. It does not require the three release-comparison
lanes or `grype`.

The gate must always print an operator-facing report. Each entry names the
capability, whether the invoked mode requires it, the exact executable,
environment variable, path, host, or permission checked, and — on failure —
exactly one concrete `Fix:` instruction, with current and required values shown
explicitly and command output reduced to the relevant fact.

The gate runs at the start of every work-starting `forge_metadata.py`
invocation, not only from the worker loop, and because the worker re-execs
itself between cycles, every new worker process revalidates the host before
doing more work. A standalone work-starting entry point must invoke the same
gate before its first queue query; the code-coverage launcher does so in its
coverage mode before selecting an issue or creating a Rhei workspace. The Forge
launcher does so before every claim in the issue-work mode with PR review
enabled, for its worker, setup, and review agents, because each of its cycles
may work an issue or review published pull requests. Any
failed required check stops the run with a non-zero exit before the first work
cycle; only the Forge launcher's loop waits out a failure that is transient
(§FS-forge-host-requirements.1). Capabilities the mode does not need are
reported as not required, a relaxed version mismatch does not fail startup, and
commands that start no work — stop, resume, help, cache maintenance — do not run
the gate at all.

## 1. A transient failure waits

A failed check is either a host fault or a transient condition, and the two
call for opposite responses. A host fault — a missing tool, credential,
permission, Docker daemon, agent login, or Java lane — lasts until the operator
fixes it, so it stops the worker loudly. A transient condition clears on its own
and says nothing about how the host is set up, so stopping on it only loses the
worker's throughput until a person notices and restarts it. Two conditions are
transient:

- **A GitHub rate limit**, primary or secondary. Several workers may share one
  GitHub account, and GitHub's short-lived limits trip while the hourly budget
  still has room, so one worker hitting a limit is not a missing permission.
  The gate recognizes a rate limit by the same rule as Forge's other GitHub
  calls (§FS-human-intervention-policy), and reports it as a rate limit whose
  fix is to wait, not to check access grants that are fine.
- **An endpoint that cannot be reached**: a required host whose name does not
  resolve, whose connection fails, which does not answer in time, or which
  answers with a server error or a rate-limit status. A check that depends on
  such an endpoint — a GitHub query, a Git transport, an agent login that
  refreshes its token — and fails while the endpoint cannot be reached reports
  the outage, not the account, so it is transient too. An endpoint or proxy
  that answers but refuses the request for any other reason, such as a proxy's
  403 or 407 or a repository's 404, or that presents a certificate the host
  does not trust, is a host fault: that policy will not change by itself.

A gate whose every failed check is transient exits with a distinct status, 75
(`EX_TEMPFAIL`), and reports only the failed checks. The Forge launcher then
starts no work, waits, and runs the gate again, with a backoff that doubles from
one minute to at most fifteen and one log line per wait. It waits for as long as
the condition lasts: a worker that waits resumes on its own once the condition
clears, and one that stops needs a person. Any host fault among the failures
still stops the run with the full report. Every other entry point exits with the
gate's status, so whatever started it can tell the two apart.

The launcher waits only once the gate has passed in the same process; before
that, a transient failure stops it like a host fault. A host that has never
passed proves nothing about its setup, and a misconfiguration can fail exactly
as an outage does — a sandbox that blocks an agent provider, or a host name that
never resolves — so a new worker that waited would hide the fault from the
operator who just started it. A worker that has passed has shown its setup
works, so its later transient failures are outages and it waits them out. A
work cycle that fails waits on the same backoff before the next gate, so a
failure the gate did not see, such as an outage that starts mid-cycle, does not
turn the loop into a tight retry against the same endpoints.
