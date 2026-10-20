# GRUND-real-use-motivation: Why: real-use tests

The repository's `tests/` suite justifies shipped metadata: each test drives the
paths that trigger a dynamic access, so a missing entry makes it fail
(§root/GOAL-tested-metadata). That makes the suite precise about entries and
silent about use. A test written to reach a call site says little about whether
an application that uses the library the way its documentation describes — with
the other libraries such an application pulls in — builds and runs on Native
Image with the metadata the repository ships. That is the promise the
repository makes to application developers (§root/GRUND-repository-motivation).

This project keeps that promise checked. It holds tests written the way
applications are written, runs them on the JVM and on Native Image against the
shipped metadata, and turns whatever they still need into evidence a maintainer
can act on — without changing what the repository ships or how its `tests/`
suite works.
