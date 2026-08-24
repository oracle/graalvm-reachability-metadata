# Architecture

Repository architecture and build infrastructure live here as `AR-<slug>`
declarations. A document belongs in this directory when it explains *how* the
repository is wired — build logic, task graphs, directory layout, component
boundaries. What the repository must *do* belongs in
[../functional-spec/](../functional-spec/README.md) instead.

Each file declares its IDs at its headings; an `AR` ID always lives in this
folder, so the prefix tells you to open this directory.

| ID | Subject |
| --- | --- |
| §AR-repository-architecture | Repository architecture and high-level overview |
| §AR-build-infrastructure | Build infrastructure |

This index is navigational. Cite the specific declaration ID rather than this
file.

Files:

- [architecture.md](architecture.md) — the repository as a whole: suites,
  directory layout, and how a coordinate flows through the build.
- [build-infra.md](build-infra.md) — Gradle build logic and the task
  infrastructure the suites run on.
