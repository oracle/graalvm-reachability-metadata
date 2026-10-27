# Real-use tests

Tests that use a supported library the way an application does — one
realistic scenario per test class, with whatever other libraries the scenario
needs — run on the JVM and on Native Image against the metadata this
repository ships. The repository's `tests/` suite proves each shipped entry;
this suite shows that real use works, and reports whatever it still needed as
test-local metadata for a maintainer to review.

This project is specified but not implemented yet. Start from
[docs/README.md](docs/README.md); the suite is specified in
[docs/functional-spec/](docs/functional-spec/README.md) and its wiring in
[docs/architecture/](docs/architecture/README.md).

Layout:

- `src/<groupId>/<artifactId>/<version>/` — one Gradle test project per
  coordinate.
- `.agent-grounds/rhei/` — the Rhei template that generates
  and publishes those projects.
- `docs/` — this project's grounded documentation.
