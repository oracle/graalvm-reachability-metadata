# Logback AOT fixture

`LogbackModelFixture` recreates `src/test/resources/META-INF/spring/logback-model`
and `logback-pattern-rules` from `src/test/resources/aot-logging.xml` using
Spring Boot's public AOT contribution API. It does not serialize objects directly
or write Native Image metadata. The model reader test consumes these resources
on both the JVM and Native Image; the writer itself is a build-time-only path.

From the test project directory, with the resolved test dependencies as
`<test-runtime-classpath>`, recreate the fixtures with:

```console
java --class-path "<test-runtime-classpath>" src/fixture/java/org_springframework_boot/spring_boot/LogbackModelFixture.java src/test/resources
```
