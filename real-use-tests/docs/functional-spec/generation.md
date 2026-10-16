# FS-real-use-test-generation: Generating real-use tests

A Rhei workflow generates the real-use project for one coordinate and publishes
it as a pull request (§FS-real-use-tests). Judgment is used only where it
cannot be replaced by an algorithm (§root/PRCPL-prefer-algorithmic): choosing
the supporting libraries, writing scenarios, fixing a native failure the
shipped metadata cannot, and fixing what the style checks report are agent
work, and every other phase is a
deterministic program whose result decides what runs next. No agent's report
counts as evidence that a test passes; only a program's run of the real-use
tasks does.

## 1. Inputs

The run takes the number of an open issue labeled `real-use-test`, the number
of scenario passes, and the worker agent. The issue asks for real-use tests of
one coordinate and names it as `group:artifact:version` in its title; the run
resolves that issue. Before anything else runs, a program verifies the pass
count, that the issue is open and labeled, and that its title names exactly one
coordinate (§root/PRCPL-verify-inputs). Such issues are opened only for a
library the repository already supports (§FS-real-use-tests.1), so the
coordinate is not checked against the index again. The worker agent is Pi or
Claude Code; the run accepts no other agent.

An operator starts a run with the launcher, which picks the issue itself.
Before it reads the queue it verifies the host the way Forge's code-coverage
launcher does (§forge/FS-forge-host-requirements): `gh` new enough to read
project items and logged in, one GraalVM of JDK 25 or newer with Native Image,
and the chosen worker agent: Pi or Claude Code installed and logged in, checked
without invoking a model. `JAVA_HOME` and `GRAALVM_HOME` must name that same
GraalVM, because Gradle and the native-image agent run on `JAVA_HOME`. It then
takes the most urgent open, unassigned, unblocked `real-use-test` issue in
project status `Todo` whose title names exactly one coordinate, and claims it
the way Forge claims an issue (§forge/FS-forge-run-requirements.2): it re-reads
the issue, sets itself as the sole assignee, waits a random few seconds,
re-reads the assignees, and only while it is still the sole assignee moves the
issue to `In Progress`. A lost claim moves on to the next candidate. When the
run's workspace cannot then be created, the launcher releases the claim back to
`Todo`.

## 2. Phases

### 2.1 Prepare

A program creates the run's worktree and branch and scaffolds the coordinate
project when it does not exist yet.

### 2.2 Preflight: declare the libraries

Before any scenario exists, the agent decides what the project's scenarios will
need beside the target library: the supporting libraries
(§FS-real-use-test-contract.1.4) and the Docker images. It may inspect the
target's JARs, and it writes only `build.gradle` and
`required-docker-images.txt`. A program then reverts any other edit, resolves
the declared libraries, and writes `user-code-filter.json` listing every package
present in the target's and the supporting libraries' JARs
(§FS-real-use-tests.1). From here on the declared libraries, the image list,
and the filter are fixed for the rest of the run; a scenario uses only what
preflight declared. The build file changes again only when the native fix adds
native-image options (§FS-real-use-test-generation.2.5).

### 2.3 Generate one scenario per pass

The run makes the configured number of passes, and each pass is one agent
invocation that adds one scenario; no program runs between passes or after the
last one, which leads straight to the native run. The agent reads the project's
existing scenarios, picks a subsystem of the target library that none of them
covers, may inspect the library JARs, and writes the scenario
(§FS-real-use-test-contract). It runs the JVM real-use task, fixing the
scenario until it passes; style is checked once, at publication. It writes only under the project's
`src/test/` and never the test-local metadata file. Its report names the
subsystem and the scenario class.

### 2.4 Run on Native Image

Once every pass is spent, a program runs the native real-use task on the whole
project, built as a consumer builds it, with the native-image options the
project's build file names and shipped metadata resolved the way a consumer
resolves it (§FS-real-use-tests.2.1). Success leads to publication;
failure leads to the fix of §FS-real-use-test-generation.2.5. Native tracing
never runs, because its cost does not fit the workflow; the fix runs the JVM
agent once.

### 2.5 Fix a native failure

The fix is optional: it runs only when the native run fails, and in two steps,
each only when the one before did not make the native run pass.

1. A program runs the JVM real-use task once with the native-image agent,
   writing its metadata to a temporary directory. Starting from the test-local
   file as the generate passes found it, so nothing a pass wrote there
   survives, it copies in every collected entry that holds to
   §FS-real-use-tests.2.2 and runs the native task again. An entry the shipped
   metadata already has is copied anyway; the duplicate changes nothing.
2. One agent invocation, limited to two hours, runs the native task until it
   passes, fixing each failure the way a consumer would, in this order:
   - A missing registration is an entry added to the project's test-local
     metadata file (§FS-real-use-tests.2.2), whichever library it describes.
   - A failure a consumer fixes with a native-image option rather than
     metadata, such as a URL protocol or a feature that is off by default, is
     an option added to the project's build file: a build-time argument of
     the native test image, or a run-time argument of it. The agent tries at
     most three options for one failure and keeps only the ones that make the
     run pass. An option that silences a check or defers a failure to run
     time is never added: nothing that reports unsupported elements at run
     time, allows an incomplete classpath, or turns off missing-registration
     errors. Metadata is never passed as an option, because the test-local
     file holds it. This overrides the flag rule of §root/FS-test-contract.2.7
     (§FS-real-use-test-contract.3).
   - A scenario neither saves is deleted, whole: one that generates bytecode
     or defines classes at run time (§root/FS-test-contract.4.5) at once, and
     one whose failure survives the option attempts then.

   It edits nothing else. Its report lists the entries it added, each option
   it added with the failure it fixes, and each scenario it deleted with the
   reason.

Publication follows either way (§FS-real-use-test-generation.2.7).

### 2.6 Remove a scenario

The unit of removal is a whole scenario: its source file is deleted
(§FS-real-use-test-contract.1.1). Only the native fix agent removes scenarios,
the ones neither metadata nor a native-image option can save
(§FS-real-use-test-generation.2.5). Each removal
is recorded with its reason, and no failing scenario routes the run to a human.

### 2.7 Publish

Publication checks one thing: the repository's style checks, the ones every
contribution passes (§root/FS-repository-functional-spec.6), over the
coordinate project. They are the license header
every file opens with, the file formatting, and checkstyle. When they fail, one
agent invocation fixes what they report, adding a missing header, reformatting
a file, or fixing a checkstyle finding, and edits nothing else; it neither
changes what a scenario does nor deletes one. A program then commits the
coordinate project's changes as they stand, on top of the run's earlier
commits, and publishes that project through the repository's shared
publication pipeline, which opens the pull request
(§forge/AR-real-use-tests-publication). The pull request resolves the run's
issue. Its description opens with what the run achieved and what it cost,
so a reviewer sees the numbers before the diff:

- the target library's coverage on the JVM as JaCoCo measures it over the
  published scenarios: the total, then lines, instructions, and methods,
  each as covered of total;
- the tokens the run's agent invocations spent, split as the other
  generation workflows report them: input, cached input, cache-write input,
  and output;
- the number of iterations, each one agent invocation of the run.

Measuring the coverage is one more JVM run over the published scenarios, not a
check: it gates nothing.

It then lists each scenario the native fix removed, with the reason. The
scenarios themselves and the test-local metadata entries are read in the
diff (§FS-real-use-tests.2.3).

## 3. Outcomes

| Outcome | When |
| --- | --- |
| Published | At least one scenario remains, and the branch is pushed for the pipeline to open the pull request. |
| Nothing to publish | Every scenario was removed; no pull request is opened and the run records why. |
| Failed | A program step itself failed: a bad input, scaffolding, resolving the declared libraries, the build, the commit, or the push. |
