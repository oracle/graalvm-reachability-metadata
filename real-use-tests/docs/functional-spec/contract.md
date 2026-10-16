# FS-real-use-test-contract: The real-use test contract

Every test in `real-use-tests/` exists to show that a supported library works in
a realistic application built with Native Image (§FS-real-use-tests). This
declaration is the single normative contract for what such a test must have and
must not do. It replaces the coverage-driven points of §root/FS-test-contract, which
is scoped to `tests/`, and inherits the rest of it point by point
(§FS-real-use-test-contract.3).

The generation prompts and any review skill for this suite restate points of
this contract; those restatements cite the points here, so a change to the
contract starts in this file.

## 1. What a real-use test must have

### 1.1 One scenario per test class, in one file

A test class is one scenario: a task an application performs with one
subsystem of the target library, from setup to an observable result. Its
methods are the steps or variants of that scenario. The first sentence of the
class doc comment names the subsystem. Helper types the scenario needs are
nested in the class, so the scenario is one source file and removing it
removes nothing another scenario uses (§FS-real-use-test-generation.2.6).

```text
Good: JsonbWithPoolTest — "Stores and queries jsonb documents through a
      pooled connection." Helper record OrderRow nested inside it.
Bad:  a class with one method per Statement setter, or a shared
      TestSupport.java that three scenarios import.
```

### 1.2 The target library is the subject

Each scenario exercises the target library. Supporting libraries appear only
as far as the scenario needs them, and no scenario asserts only on a
supporting library's behavior. Every scenario class references at least one
type from the target's `allowed-packages`.

```text
Good: Jackson maps the jsonb value the postgresql driver returned, and the
      test asserts on the persisted and re-read document.
Bad:  a class that round-trips polymorphic Jackson types and uses postgresql
      only to open a connection.
```

### 1.3 Consumer code with observable assertions

A scenario is written like application code: the library's documented public
API, configured the way its documentation shows, asserting on what a user
would observe — returned values, persisted state, emitted output — rather than
on internal state.

### 1.4 Any supporting library the scenario needs

`build.gradle` may declare any library a realistic application would use
alongside the target, pinned to a version compatible with the target version.
Each supporting library is the real artifact, and its packages are included in
the project's `user-code-filter.json` (§FS-real-use-tests.1).

### 1.5 In the target library's language

A scenario is written in the target library's language: the `language` of the
`metadata/<groupId>/<artifactId>/index.json` entry whose `tested-versions`
lists the coordinate's version, or Java when that entry names none. An
application calls a Kotlin or Scala library from Kotlin or Scala, and the
calling language decides which of the library's dynamic-access paths run, such
as Kotlin companion lookups, Scala module singletons, or serializers a
compiler plugin generates. A Java scenario would exercise paths no consumer
takes. The project is set up as a `tests/` project of that language
(§root/FS-test-contract.1.1).

```text
Good: exposed-json 1.2.0, whose entry names kotlin: a Kotlin class under
      src/test/kotlin/ declares a table with a json column as the Exposed
      documentation shows.
Bad:  the same scenario in Java, calling the static accessors Kotlin
      generates.
```

## 2. What a real-use test must not do

### 2.1 No coverage chasing

A scenario contains no call whose only purpose is to reach a dynamic-access
site. The coverage points of §root/FS-test-contract — 1.3, 1.5, 1.8, 3.1, and
3.5 — do not apply to this suite.

### 2.2 No writes outside the coordinate project

Work on a real-use project changes only files under its
`real-use-tests/src/<groupId>/<artifactId>/<version>/` directory: never
`metadata/`, `tests/`, another real-use project, or the harness. Publication
alone also writes the coordinate's stats record (§FS-real-use-tests.1).

### 2.3 No metadata outside the test-local file

A real-use project carries metadata only in its test-local
`reachability-metadata.json`, held to §FS-real-use-tests.2.2. It ships no other
Native Image configuration file and no `native-image.properties`.

### 2.4 No tolerated Native Image failure

A scenario never skips or tolerates a failure under Native Image: no
`@DisabledInNativeImage`, no `assumeFalse(...)`, no runtime check that changes
what runs natively, and no catch of an unsupported-feature error. The
sanctioned exception of §root/FS-test-contract.4.3 does not apply here. A scenario
that cannot pass natively is removed instead
(§FS-real-use-test-generation.2.6).

## 3. Points inherited from the test contract

These points of §root/FS-test-contract apply to every real-use test unchanged:

| Point | Rule |
| --- | --- |
| §root/FS-test-contract.1.2 | Public, idiomatic test classes. |
| §root/FS-test-contract.1.4 | Only the tested version's API. |
| §root/FS-test-contract.1.6 | A 60-second bound on every individual test. |
| §root/FS-test-contract.1.7 | A 10-second floor on explicit I/O timeouts. |
| §root/FS-test-contract.2.1 | No reflection or serialization shortcuts. |
| §root/FS-test-contract.2.2 | No tests inside the library's packages. |
| §root/FS-test-contract.2.3 | No stubs, fakes, or shadow classes for real packages. |
| §root/FS-test-contract.2.5 | No hardcoded versions of the target artifact. |
| §root/FS-test-contract.2.6 | No asserting known-broken behavior. |
| §root/FS-test-contract.2.8 | No JVM toolchain or compatibility overrides. |
| §root/FS-test-contract.3.3 | Upstream sources as guidance, not as source code. |
| §root/FS-test-contract.4.4 | No dependence on machine-local resource paths. |
| §root/FS-test-contract.4.5 | No behavior that requires runtime class definition. |
| §root/FS-test-contract.4.6 | Budget for slow native startup. |

§root/FS-test-contract.2.7 is overridden, not inherited. Its native build and
run flag rule never lets a flag repair a run, because in `tests/` a flag would
hide a missing metadata entry. A real-use project instead shows the build an
application ships, and that build carries the native-image options a consumer
of the library sets, so the native fix may add such an option to make the run
pass (§FS-real-use-test-generation.2.5). Its metadata rule is replaced by
§FS-real-use-test-contract.2.3.

The determinism and Docker rules of §root/FS-repository-functional-spec.6 apply as
they do to `tests/`.
