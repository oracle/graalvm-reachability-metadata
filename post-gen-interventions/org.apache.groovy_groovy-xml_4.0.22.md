# Post-generation intervention report

Library: org.apache.groovy:groovy-xml:4.0.22
Stage: metadata_fix_failed

## Summary

The native-image test executable was built successfully, but the generated Groovy test class failed before any individual `groovy-xml` API assertion could run. All 17 generated tests in `Groovy_xmlTest.groovy` failed from the same class-initialization problem, so the generated test source was removed:

- `tests/src/org.apache.groovy/groovy-xml/4.0.22/src/test/groovy/org_apache_groovy/groovy_xml/Groovy_xmlTest.groovy`

No metadata files were modified. After removing the failing test source, `./gradlew test -Pcoordinates=org.apache.groovy:groovy-xml:4.0.22 --stacktrace` completed successfully; the coordinate sub-build skipped native execution because there are no remaining test classes.

## Root cause

The first native test failure was:

```text
java.lang.BootstrapMethodError: java.lang.NullPointerException
  at org_apache_groovy.groovy_xml.Groovy_xmlTest.<clinit>(Groovy_xmlTest.groovy:46)
Caused by: java.lang.NullPointerException
  at java.lang.invoke.MethodHandles.insertArgumentsChecks(MethodHandles.java:5010)
  at java.lang.invoke.MethodHandles.insertArguments(MethodHandles.java:4979)
  at org.codehaus.groovy.vmplugin.v8.IndyInterface.make(IndyInterface.java:252)
```

The remaining 16 test failures were secondary `NoClassDefFoundError: Could not initialize class org_apache_groovy.groovy_xml.Groovy_xmlTest` errors caused by the same failed static initializer.

This is not a missing reachability metadata failure. The failure happens in Groovy's invokedynamic bootstrap path (`org.codehaus.groovy.vmplugin.v8.IndyInterface`) while initializing the generated Groovy test class itself. It is a Groovy/native-image runtime compatibility issue or generated-test-language issue, not an additive metadata registration gap in `groovy-xml`. Codex also concluded that the failure had moved outside legal `groovy-xml` metadata: the implicated code belongs to transitive Groovy core (`org.codehaus.groovy.*`), not to `groovy-xml`'s allowed packages, and repository metadata must not use class-initialization directives or `native-image.properties` to work around it.

## Why the remaining generated support should be preserved

The requested artifact metadata/index scaffold still targets the correct coordinate and should be kept for follow-up work. The failure is isolated to the generated Groovy-language test source, which cannot initialize in the native test executable. Preserving the remaining generated support keeps the library entry available for a later native-compatible test rewrite, for example using Java tests or a smaller fixture that exercises `groovy-xml` without triggering the Groovy invokedynamic bootstrap failure in the test class initializer.
