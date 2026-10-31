# ClassGraph scan fixtures

`src/test/resources/scan-fixtures.data` is a JAR containing only this suite's
`ScanFixtures` classes and `catalog/payload.txt`. Tests copy it to a temporary
JAR and scan its bytes, so scanning does not depend on a machine's classpath.
The fixture types also compile normally into the test application. Class-loading
tests delegate to that application's existing class loader; they never define
new classes from the fixture JAR.

To regenerate the fixture archive from the repository root using `JAVA_HOME`:

```sh
project=tests/src/io.github.classgraph/classgraph/4.8.165
"$JAVA_HOME/bin/javac" -d "$project/build/scan-fixtures" \
  "$project/src/test/java/io_github_classgraph/classgraph/ScanFixtures.java"
"$JAVA_HOME/bin/jar" --create --no-compress \
  --file "$project/src/test/resources/scan-fixtures.data" \
  -C "$project/build/scan-fixtures" . \
  -C "$project/src/test/resources" catalog/payload.txt
```

The optional JVM-Driver and Narcissus reflection backends are not exercised:
the test dependencies do not include those backends, and requesting them without
the actual implementations would only test fallback to standard reflection.
Their legacy JVM-specific class-finding branches cannot be selected portably on
the test JVM. All other tests use only `io.github.classgraph` public APIs.
