# Post-generation intervention report

Library: org.apache.groovy:groovy-groovysh:4.0.24
Stage: metadata_fix_failed

## Summary

The remaining failures are metadata-related, so no generated tests were removed.

All six native test failures share the same startup root cause: the generated native executable fails while initializing `groovy.lang.GroovySystem`, before any test-specific assertion logic runs. The first failing test reports an `ExceptionInInitializerError`; the other five tests then report `NoClassDefFoundError: Could not initialize class groovy.lang.GroovySystem` because the same static initialization has already failed.

The full Gradle log shows the underlying failure immediately before the summarized JUnit failures:

- `java.io.IOException: Stream closed`
- in `org.codehaus.groovy.reflection.GeneratedMetaMethod$DgmMethodRecord.loadDgmInfo`
- while `MetaClassRegistryImpl.registerMethods` is reading Groovy's `META-INF/dgminfo` bootstrap resource

The later `NullPointerException` at `MetaClassRegistryImpl.java:122` is a secondary symptom of the same failed Groovy bootstrap path, not an independent test bug.

## Root cause by failing test

- `Groovy_groovyshTest.createsUnselectedBuffersAndDeletesSelectedBufferByMovingBackward` failed because `GroovySystem` initialization failed while reading `META-INF/dgminfo` during Groovy metaclass registration.
- `Groovy_groovyshTest.limitsParsedCommandArgumentsAndRejectsUnclosedQuotes` failed because `GroovySystem` was already left uninitialized after the same bootstrap failure.
- `Groovy_groovyshTest.parsesCommandArgumentsWithQuotesAndEscapedBlanks` failed because `GroovySystem` was already left uninitialized after the same bootstrap failure.
- `Groovy_groovyshTest.keepsSeparateContentsForSelectableShellBuffers` failed because `GroovySystem` was already left uninitialized after the same bootstrap failure.
- `NavigablePropertiesCompleterTest.completesNavigableMapKeysAndQuotesKeysThatAreNotIdentifiers` failed because `GroovySystem` was already left uninitialized after the same bootstrap failure.
- `NavigablePropertiesCompleterTest.completesNodeChildrenAndNodeListEntriesByPrefix` failed because `GroovySystem` was already left uninitialized after the same bootstrap failure.

## Metadata still missing or unresolved

Codex found that the native run initially missed Groovy bootstrap metadata, including the `META-INF/dgminfo` resource and a long cascade of reflective accesses used by Groovy's DGM/SAM/metaclass initialization. It partially addressed that cascade and got the exact trace loop to complete, but the normal `nativeTest` still fails when Groovy reads `META-INF/dgminfo` from the native image.

The unresolved metadata issue is therefore not tied to one generated test method. It is the native-image registration/packaging needed for Groovy's `META-INF/dgminfo` resource and the associated Groovy metaclass bootstrap path to behave correctly in the normal native test executable.

Codex could not finish the repair because the tracing-agent path was unreliable in this environment, and after the partial manual fixes the remaining failure no longer surfaces as a clean `Missing*RegistrationError` with a suggested JSON snippet. The remaining `Stream closed` failure during `loadDgmInfo` does not identify a precise additional metadata entry, so further metadata changes would be speculative.

## Why the generated support should be preserved

The generated tests exercise meaningful, public `groovy-groovysh` behavior: shell buffer management, command argument parsing, and navigable property completion. The failures occur before those behaviors execute, during shared Groovy runtime bootstrap inside the native image. Removing the tests would hide a real reachability/metadata gap for this artifact rather than remove a bad or unsupported test case.
