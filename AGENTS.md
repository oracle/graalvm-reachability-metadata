# Development Cheat Sheet

## Prerequisites (assume exists)
- JAVA_HOME set to JDK 25 (GraalVM recommended to match CI)
- Docker
- grype v0.104.0 (install: curl -sSfL https://get.anchore.io/grype/v0.104.0/install.sh | sudo sh -s -- -b /usr/local/bin)

## Setup
- Always use Gradle wrapper from repo root:
  - Unix: ./gradlew <task> [options]
  - Windows: gradlew.bat <task> [options]
- Tip: add --stacktrace for debugging

## One command for complete infrastructure testing
./gradlew testAllInfra -Pparallelism=4 --stacktrace

## Code Style
- Always try to reuse existing code.
- Be assertive in code.
- Write type annotations in all functions and most variables.
- Document code without being too verbose.
- In Java and Groovy, always import classes and use them without qualified names.
- In Java use multi-line strings where possible.
- In Java use the markdown style for comments.

## Testing individual components

- Clean previous build outputs for the selected coordinates: ./gradlew clean -Pcoordinates=[group:artifact:version|k/n|all]
- Pre-fetch Docker images allowed by metadata (used in tests) for the selected coordinates: ./gradlew pullAllowedDockerImages -Pcoordinates=[group:artifact:version|k/n|all]
- Validate reachability metadata files for the selected coordinates: ./gradlew checkMetadataFiles -Pcoordinates=[group:artifact:version|k/n|all]
- Run Checkstyle for the selected coordinates: ./gradlew checkstyle -Pcoordinates=[group:artifact:version|k/n|all]
- Compile test sources for the selected coordinates: ./gradlew compileTestJava -Pcoordinates=[group:artifact:version|k/n|all]
- Run JVM-based tests for the selected coordinates: ./gradlew javaTest -Pcoordinates=[group:artifact:version|k/n|all]
- Build native images used by native tests (compile-only) for the selected coordinates: ./gradlew nativeTestCompile -Pcoordinates=[group:artifact:version|k/n|all]
- Run all tests for the selected coordinates: ./gradlew test -Pcoordinates=[group:artifact:version|k/n|all]


## Check style and formatting
- Style check: ./gradlew checkstyle
- Format check: ./gradlew spotlessCheck

## Testing the metadata
- Single library (replace with group:artifact:version):
  - ./gradlew pullAllowedDockerImages -Pcoordinates=group:artifact:version
  - ./gradlew checkMetadataFiles -Pcoordinates=group:artifact:version
  - ./gradlew test -Pcoordinates=group:artifact:version
- Sharded example (1/64):
  - ./gradlew pullAllowedDockerImages -Pcoordinates=1/64
  - ./gradlew checkMetadataFiles -Pcoordinates=1/64
  - ./gradlew test -Pcoordinates=1/64

### Generating Metadata
- Generate metadata for a certain library version:
   - ./gradlew generateMetadata -Pcoordinates=com.hazelcast:hazelcast:5.2.1
- Generate metadata for a certain library version and derive `user-code-filter.json` from the resolved library JAR:
   - ./gradlew generateMetadata -Pcoordinates=org.postgresql:postgresql:42.7.3 --agentAllowedPackages=fromJar
- Generate metadata for a certain library version and create or update the user-code-filter.json:
   - ./gradlew generateMetadata -Pcoordinates=org.postgresql:postgresql:42.7.3 --agentAllowedPackages=org.example.app,com.acme.service

### Fix failing tasks

- Generates new metadata for library's new version which is failing native-image run:
  - ./gradlew fixTestNativeImageRun -PtestLibraryCoordinates=org.postgresql:postgresql:42.7.3 -PnewLibraryVersion=42.7.4

## Docker Image Vulnerability Scanning
- Changed images between commits:
  - ./gradlew checkAllowedDockerImages --baseCommit=$(git rev-parse origin/master) --newCommit=$(git rev-parse HEAD)
- All allowed images:
  - ./gradlew checkAllowedDockerImages

## Compatibility Automation (latest library versions)
- List libs with newer upstream versions:
  - ./gradlew fetchExistingLibrariesWithNewerVersions --quiet
- Record a newly tested version:
  - ./gradlew addTestedVersion -Pcoordinates="group:artifact:newVersion" --lastSupportedVersion="oldVersion"
  - Example: ./gradlew addTestedVersion -Pcoordinates="org.postgresql:postgresql:42.7.4" --lastSupportedVersion="42.7.3"

## Releases and Packaging
- Package artifacts: ./gradlew package

<!-- BEGIN GRUND MANAGED BLOCK -->
## Grounding with grund (v7)

This project uses [`grund`](https://github.com/vjovanov/grund): every spec, goal, decision, and end-to-end test has a stable ID `<KIND>-<slug>[.<section>]` (`KIND ∈ {GRUND, GOAL, PRCPL, FS, AR}`), cited with the marker `§` — e.g. `<§>FS-user-login.3.1` (the `FS-user-login` here is a shape illustration, not a real ID in this repo, hence the `<§>` escape). Type `$$` in a grund-aware editor and it becomes `§`. Bare ID-shaped tokens are ignored — `[reference] strict = true` is set in `grund.toml`, so only `§`-prefixed citations are checked.

### Grounding from a citation

A `§<ID>` is a pointer to a fact, not a file path. Resolve it with `grund` and climb only as far as needed:

- `grund <ID>` — the lead (heading-less, cut at the first child section). The cheap first read for a bare `§<ID>` citation.
- `grund <ID> --toc` — the lead plus the nested section map. Use to choose which subsection to fetch next.
- `grund <ID> --full` — the entire body. Escalate to this when narrower reads aren't enough.
- `grund <ID> --brief` — heading + first paragraph only.
- `grund refs <ID>` — every site that cites the ID; add `--summary` for one line per file. Run before renaming or moving a declaration.
- `grund list` / `grund list --kind FS,AR` — discover IDs if you get lost

### Project map

- [GRUND](docs/grund.md): Why: repository motivation
- [GOAL](docs/goals.md): Where: repository direction and outcomes
- [PRCPL](docs/principles.md): Cross-cutting principles for how the repository works
- [FS](docs/functional-spec): Repository functional behavior and contributor-facing requirements
- [AR](docs/architecture): Repository architecture and build infrastructure
- [skills/](skills): Agent review and automation skills
- [.github/workflows/](.github/workflows): CI and release workflows: the gate on GitHub
- [.github/actions/](.github/actions): Composite actions the workflows reuse
- [metadata/](metadata): The shipped reachability metadata, verbatim
- [tests/](tests): Per-library test projects that justify the metadata
- [tests/tck-build-logic/](tests/tck-build-logic): The TCK harness build logic the test projects run on
- [stats/](stats): Generated library statistics
- [docs/assets/](docs/assets): README images, shipped verbatim

### Project namespaces

A namespace is a project boundary, not a docs folder. The current project is the local namespace: cite its IDs as `§<ID>`.

Create or use a separate namespace when work introduces an independently checked app, package, service, or subproject. Give that project its own `grund.toml`, add it to the workspace root's `[workspace] members`, run `grund init` there, and set a stable `project_name`.

Do not create a namespace for a regular module or component that still belongs to this project. Cite across namespaces as `§alias/<ID>` and run `grund check` from the workspace root.

### Workspace members

Cross-project citations use §alias/<ID>.

- [`forge`](forge/AGENTS.md): Automation subproject that turns labeled issues into reviewed metadata pull requests
- [`root`](AGENTS.md): Repository for shared GraalVM reachability metadata, tests, and release infrastructure

### Declarations and citations

Declarations are heading lines `# FS-user-login: …` in markdown. In a code doc-comment (Rustdoc, Javadoc, JSDoc, Python docstring, Go `//`, …) drop the `#` — write `/// FS-user-login: …` directly. Numbered headings inside a declaration are citable sections: use depth-matching headings (`## 1. …`, `### 1.1 …`, etc.) so `§<ID>.1` / `§<ID>.1.1` resolve; mismatched heading depth is a `grund check` error. Plain headings or bold labels are fine for non-citable local structure. One doc-comment may declare multiple IDs (e.g. an `AR-` and an `FS-` on the same class) — each gets its own body. An inline source declaration is reachable from the configured kind home via a one-line stub: `# <ID>: [<path>](<path>)`.

### Rules

- **Spec first.** For behavior or design changes, write or update the most-specific spec point before code.
- **Cite as you write.** Place `§<ID>` at the point a claim or behavior is made — on the doc-comment for a whole behavior, inline beside the clause it enforces.
- **Marker = live citation.** A `§`-prefixed token resolves and is checked wherever it appears — including inside Markdown backticks. To mention an ID without citing it, write `<§><ID>`, omit the marker, or use a fenced code block.
- **Inline citation style.** Inline notes: ≤ 1 line preferred, hard cap 3 lines; ≤ 100 columns.
- **Always cite the most-specific point.**

### Citation directions

- **GOAL** must cite GRUND.
- **PRCPL** must cite GOAL.
- **FS** should cite GOAL or FS.
- **AR** must cite FS or GOAL.
- **skills/** must cite FS; should cite AR.
- **.github/workflows/** should cite FS or AR.
- **.github/actions/** should cite FS or AR.
- **tests/tck-build-logic/** should cite FS or AR.
- **code** (Build files and repository scripts outside a kind home) must cite FS or AR.
Unlisted kinds and pairs are fine.

### Clickable citations

On repository web surfaces, link `§<ID>` to the PR branch in PR bodies, the reviewed commit in reviews, an exact commit for permalinks, and the default branch otherwise; fall back to plain when unsure.
<!-- END GRUND MANAGED BLOCK -->

## Grounding conventions for this repository

The block above is generated by `grund init` and is rewritten on every run, so
repository-specific conventions live here instead.

Start from [docs/README.md](docs/README.md) for the documentation index.

### Namespaces in this repository

`forge` is a workspace member with its own namespace and its own kinds
(`KIND ∈ {GRUND, GOAL, AR, FS, DW, STRAT, ORCH, GIT, WF, E2E, BENCH, ROADMAP}`),
documented in [forge/AGENTS.md](forge/AGENTS.md). Inside `forge/`, cite Forge
facts as `§<ID>`; from repository docs, cite them as `§forge/<ID>` — for example
`§forge/GOAL-forge-direction`. Run `grund check` from the repository root, which
is the only place the workspace alias table is in scope.

### Additional rules

- **The spec is the foundation.** Every idea, issue resolution, and design change is
  checked against the spec before it is implemented. If it aligns, cite the most-specific
  point it implements and proceed. If it does not align, stop and say so, then offer the
  user two paths: **(dangerous)** change the spec, or work out how the idea fits inside
  the current constraints. Never widen a spec point silently to make an implementation
  legal. A spec change is dangerous because other points and code cite it: run
  `grund refs <ID>` first to see the blast radius, get explicit approval, and land the
  spec change as its own commit ahead of the code.
- **The spec says how and why, not what each file is.** Spec points state the behavior
  the repository guarantees and the reasoning behind it. They are not per-module or
  per-function descriptions — if a paragraph would go stale merely because a file was
  renamed or a function moved, it does not belong in the spec.
- **Grund and goals are namespace-local top-level docs.** Repository motivation
  and direction live in `docs/grund.md` and `docs/goals.md`; Forge motivation and
  direction live in `forge/docs/grund.md` and `forge/docs/goals.md`.
- **Document by component when complexity warrants it.** A complex component,
  such as a module, service, workflow family, script family, or large
  behavior-owning file, may have its own functional spec and architecture
  following the same behavior/requirements vs how split.
- **Do not over-nest simple components.** If a component only needs one
  architecture explanation and has no separate behavioral contract, keep it as a
  single architecture declaration/file rather than creating a subdirectory.
- **Every kind has its own home.** No two kinds may share a `file` or `folder`
  in `grund.toml`. A kind with one home file is declared as `file`, a kind that
  spreads across several files as `folder`. The generated project map links each
  kind to its home, so a shared home makes the map useless — the prefix must
  tell you which file or folder to open.
- **A `file` home may sit inside a `folder` home.** `docs/architecture/` is the
  `AR` folder home and also holds the `TCK` and `CI` file homes; `docs/functional-spec/`
  is the `FS` folder home and also holds the `METADATA` and `TESTS` file homes.
  `forge/docs/architecture/` does the same with `DW`, `ORCH`, and `GIT`. A kind
  is filed under the folder whose question it answers — the harness and CI
  document how the repository is wired, the two suites state what the shipped
  metadata and its tests must be. The kinds stay distinct: `TCK-` and `CI-` IDs
  are not `AR-` IDs, and `METADATA-` and `TESTS-` IDs are not `FS-` IDs. Locate a
  nested kind by its own `file` entry in `grund.toml`, not by the folder it
  happens to live in.
