# Development Cheat Sheet

## Prerequisites (assume exists)
- JAVA_HOME set to JDK 21 (GraalVM recommended to match CI)
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

## Forge automation E2E
- After changing Forge control-plane, workflow, strategy, git-publication, or
  shared workflow code under `forge/`, run the hermetic fixture Forge E2E
  (§forge/E2E-forge-workflow-testing) as described in
  [forge/docs/e2e.md](forge/docs/e2e.md); treat `forge_metadata.py` runs as the
  source of truth instead of mock-only or direct-driver tests. Live GitHub Forge
  E2E still requires an explicit user request.

## Grounding with grund (v3)

This project uses [`grund`](https://github.com/vjovanov/grund): every spec, goal, decision, and end-to-end test has a stable ID `<KIND>-<slug>[.<section>]` (`KIND ∈ {GRUND, GOAL, FS, AR, TCK, E2E, CI, METADATA, TESTS, SKILL}`), cited with the marker `§` — for example, `FS-user-login.3.1` is only a shape illustration, not a real ID in this repo. Type `$$` in a grund-aware editor and it becomes `§`. Bare ID-shaped tokens are ignored — `[reference] strict = true` is set in `.agents/grund.toml`, so only `§`-prefixed citations are checked.

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
- [FS](docs): Repository functional behavior and contributor-facing requirements
- [AR](docs): Repository architecture and build infrastructure
- [TCK](docs/tck.md): Test harness (TCK): task groups
- [E2E](docs/e2e.md): Infrastructure end-to-end tests (testInfra/testAllInfra)
- [CI](docs/ci.md): Recurring CI workflows and composite actions
- [METADATA](docs/metadata.md): The metadata/ suite: shipped reachability metadata
- [TESTS](docs/tests.md): The tests/ suite: tests that justify the metadata
- [SKILL](skills): Agent review and automation skills

### Project namespaces

A namespace is a project boundary, not a docs folder. The current project is the local namespace: cite its IDs as `§<ID>`.

Create or use a separate namespace when work introduces an independently checked app, package, service, or subproject. Give that project its own `.agents/grund.toml`, add it to the workspace root's `[workspace] members`, run `grund init` there, and set a stable `project_name`.

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
- **Inline citation style.** Inline notes: ≤ 1 line preferred, hard cap 3 lines; ≤ 100 columns.
- **Always cite the most-specific point.**

### Citation directions

- **GOAL** should cite GRUND or GOAL.
- **FS** should cite GOAL or FS.
- **AR** should cite FS or GOAL.
- **TCK** should cite FS or AR or GOAL.
- **E2E** should cite FS or TCK or AR.
- **CI** should cite FS or TCK or METADATA or TESTS or E2E or GOAL.
- **METADATA** should cite FS or GOAL.
- **TESTS** should cite FS or METADATA or GOAL.
- **SKILL** should cite FS or CI or METADATA or TESTS.
- **code** (any file outside a kind home) should cite FS or AR or TCK or E2E or CI or METADATA or TESTS.
Unlisted kinds and pairs are fine.
