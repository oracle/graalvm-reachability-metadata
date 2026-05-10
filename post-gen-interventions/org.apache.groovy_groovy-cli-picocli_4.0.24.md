# Post-generation intervention

Library: org.apache.groovy:groovy-cli-picocli:4.0.24

Stage: `metadata_fix_failed`

## Summary

The generated native test suite failed before any individual test logic could run. All `9` generated `Groovy_cli_picocliTest` methods failed during construction of the Groovy-based test class with `NoClassDefFoundError: Could not initialize class groovy.lang.GroovySystem`; the first failure was an `ExceptionInInitializerError` caused by a `NullPointerException` while `groovy.lang.GroovySystem` initialized `MetaClassRegistryImpl`.

## Root cause

This is not a missing reachability-metadata entry. The Gradle output and Codex log do not show a `Missing*RegistrationError` or a suggested metadata snippet. The failure occurs in Groovy runtime bootstrap (`GroovySystem.<clinit>` / `MetaClassRegistryImpl.<init>`) as soon as the native JUnit runner instantiates the Groovy test class, before the `groovy-cli-picocli` APIs are exercised.

Codex investigated the runtime initialization path and confirmed the remaining failure is tied to Groovy runtime/native-image behavior rather than an incomplete metadata entry. Since metadata files must not be modified for this intervention, the generated Groovy test class was removed:

- `tests/src/org.apache.groovy/groovy-cli-picocli/4.0.24/src/test/groovy/org_apache_groovy/groovy_cli_picocli/Groovy_cli_picocliTest.groovy`

## Why preserve the remaining generated support

The remaining generated support should be preserved because it records the library coordinate, build wiring, and agent/user-code filtering needed for future attempts to add coverage for `groovy-cli-picocli`. The removed file was the failing generated test artifact; the broader generated scaffold can still be reused if a Java-based test or a future GraalVM/Groovy runtime fix makes native execution viable without triggering `GroovySystem` initialization failure during test class construction.
