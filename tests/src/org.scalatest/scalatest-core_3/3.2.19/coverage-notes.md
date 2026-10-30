# Public API coverage boundaries

The CLI, configuration wrapper, private-method-testing DSL, suite lifecycle,
custom reporters, and sbt remote runner are exercised through public APIs.
Discovery scans a temporary JAR containing entry names for test fixtures already
on the application classpath; it introduces no new class definitions.

The following targets remain untested because they are private GUI implementation
details with no non-GUI public entry point:

- `org.scalatest.tools.IconEmbellishedListCellRenderer$Icons$`
- `org.scalatest.tools.ProgressBarPanel`
- `org.scalatest.tools.RunnerJFrame`
- `org.scalatest.SuiteRerunner`
- `org.scalatest.TestRerunner`

`Runner.run` with `-g` starts an interactive Swing window and blocks until it is
closed. The rerunner implementations are constructed only from that GUI's selected
events. These tests do not bypass visibility or reflectively invoke those internals.

The `Suite` companion's `yeOldeTestNames` method has no callers in this artifact.
Its `checkForPublicNoArgConstructor` method is called only by `SuiteRerunner`.
The ignored-method reporting path does exercise `Suite.getMethodForTestName`.
The optional JUnit and TestNG runner bridges are exercised via `Runner.run` with
`-j` and `-b`, respectively, using the real ScalaTestPlus integrations. The TestNG
suite XML names a fixture already on the application classpath and is read as a
normal file, not a classloader resource.
