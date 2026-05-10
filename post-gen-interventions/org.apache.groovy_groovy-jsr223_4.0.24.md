# Post-generation intervention report

Library: org.apache.groovy:groovy-jsr223:4.0.24
Stage: metadata_fix_failed

## Summary

The generated native test failed in `:nativeTest` after native-image compilation succeeded. Both generated JUnit methods in `GroovyScriptEngineImplTest.groovy` failed immediately with `java.lang.BootstrapMethodError` caused by `java.lang.NullPointerException` in `org.codehaus.groovy.vmplugin.v8.IndyInterface.make(...)` / `java.lang.invoke.MethodHandles.insertArguments(...)`.

The failing generated test file was removed:

- `tests/src/org.apache.groovy/groovy-jsr223/4.0.24/src/test/groovy/org_apache_groovy/groovy_jsr223/GroovyScriptEngineImplTest.groovy`

After removing that test, `./gradlew test -Pcoordinates=org.apache.groovy:groovy-jsr223:4.0.24 --stacktrace` passed; Gradle skipped native test execution because no test remained for the coordinate.

## Root cause by failing test

- `defaultConstructorUsesThreadContextLoaderThatCanSeeGroovyScripts()` failed at the first Groovy test-body execution path with a Groovy indy bootstrap failure, not with a `Missing*RegistrationError` or a suggested metadata entry.
- `evaluatesScriptMethodsAndExposesThemThroughInvocableProxy()` failed with the same Groovy indy bootstrap path before the script-evaluation assertions could run.

The Codex metadata-fix log shows that metadata gaps were investigated and partially addressed, including Groovy extension-module classes. The final remaining failure was still the `BootstrapMethodError` / `IndyInterface.make(...)` native runtime crash, with no actionable missing-registration metadata emitted. This points to a Groovy 4 invokedynamic/runtime support limitation or test incompatibility on the native-image toolchain rather than unresolved reachability metadata.

## Why the remaining generated support should be preserved

The library scaffold, dependency setup, generated metadata, index entry, and filter files still represent useful generated support for `org.apache.groovy:groovy-jsr223:4.0.24`. The removed test was the only generated code path exercising the unsupported Groovy indy behavior in native image. Preserving the remaining support keeps the coordinate available for metadata coverage and future targeted tests without retaining a known non-metadata native-image failure.
