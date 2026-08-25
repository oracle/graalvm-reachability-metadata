# Architecture

This directory holds everything that explains *how* the repository is wired —
build logic, task graphs, the CI that drives them, directory layout, component
boundaries. What the repository must *do* belongs in
[../functional-spec/](../functional-spec/README.md) instead.

Three kinds live here, and the prefix tells you which file to open:

| Kind | Home | Holds |
| --- | --- | --- |
| `AR` | this folder | The structural map and the build wiring. |
| `TCK` | [tck.md](tck.md) | The harness task surface, one citable section per task group. |
| `CI` | [ci.md](ci.md) | Every recurring workflow, composite action, and shared script. |

`TCK` and `CI` are filed here because the harness is the task surface the build
exposes and CI is that same task surface driven by GitHub Actions — both are
architecture. They keep their own prefixes: a `TCK-` or `CI-` ID is not an `AR-`
ID, and each is declared in its own home file above rather than anywhere in this
folder.

| ID | Subject |
| --- | --- |
| §AR-repository-architecture | Repository architecture and high-level overview |
| §AR-build-infrastructure | Build infrastructure |
| §TCK-test-harness | Test harness (TCK) task groups |
| §CI-repository-ci | Recurring CI workflows and composite actions |

`ci.md` additionally declares one `CI-<slug>` per workflow, composite action, and
shared-script group — for example §CI-test-changed-metadata or
§CI-detect-file-changes — so a workflow file can cite the exact declaration that
specifies it.

This index is navigational. Cite the specific declaration ID rather than this
file.

Files:

- [architecture.md](architecture.md) — the repository as a whole: components,
  how work flows through the system, and the implementation overview.
- [build-infra.md](build-infra.md) — the two-layer Gradle build, convention
  plugins, and scaffolding the suites run on.
- [tck.md](tck.md) — the harness task groups: selection, validation, test lanes,
  tracing, authoring, matrices, and reporting.
- [ci.md](ci.md) — the workflows, composite actions, and shared scripts, one
  citable declaration each.
