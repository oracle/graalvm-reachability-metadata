# Post-generation intervention report

Library: org.spockframework:spock-core:1.0-groovy-2.4
Stage: metadata_fix_failed

## Summary

The remaining native test failures are metadata-related. The generated Spock TCK compiles and the JVM test phase passes, but the native executable fails immediately when Groovy/Spock test classes are instantiated. The visible Gradle failure is a cascade of `NoClassDefFoundError` failures for Groovy bootstrap classes such as `org.codehaus.groovy.reflection.ClassInfo`, `groovy.lang.GroovySystem`, and `org.codehaus.groovy.runtime.InvokerHelper`.

No generated tests were removed.

## Root cause of the failures

All reported test failures share the same root cause: Groovy 2.4's runtime metaclass bootstrap is still missing native-image reflection metadata.

- The JUnit Jupiter methods on `Spock_coreTest` fail before their test bodies execute because constructing the Groovy test class calls `Spock_coreTest.$getStaticMetaClass(...)`, which reaches `ClassInfo`/`GroovySystem` initialization.
- The JUnit Vintage Spock specifications fail for the same reason while Spock attempts to instantiate each generated `Specification` subclass. Some nested specs surface the same bootstrap failure through `InvokerHelper` because their static initialization creates Groovy lists or other Groovy runtime objects.

The Codex metadata-fix log confirms this is not a test bug or an unsupported feature in a specific generated test. In tracing runs, the same startup path produced concrete `MissingReflectionRegistrationError` entries from Groovy metadata initialization, including:

- `groovy.lang.IntRange`, loaded from `GeneratedMetaMethod$DgmMethodRecord.loadDgmInfo(...)` while processing `META-INF/dgminfo`.
- `java.io.Serializable`, queried via `Class.getMethods()` from `CachedSAMClass.getSAMMethodImpl(...)` while Groovy builds cached class metadata.
- `java.lang.Cloneable`, queried from the same Groovy `ClassInfo`/`CachedClass` interface-caching path for array classes.

Codex added several Groovy bootstrap registrations, but it stopped immediately after adding the `java.lang.Cloneable` registration and did not complete another verify/trace iteration. The final non-tracing Gradle failure still collapses to `NoClassDefFoundError`, so the next exact missing metadata entry is not visible in the provided Gradle output. The remaining missing metadata is therefore still in the Groovy 2.4 metaclass bootstrap path, but the next concrete class/member/resource would need another trace-mode native run to identify.

## Why the generated support should be preserved

The generated tests exercise meaningful `spock-core` behavior: specification lifecycle, data tables, exception conditions, built-in extensions, annotations, old-value assertions, asynchronous utilities, and Hamcrest matcher integration. The failures occur before those assertions run and are caused by incomplete reachability metadata for Groovy/Spock runtime initialization, not by invalid test logic.

Preserving the generated support keeps broad coverage for the library once the remaining Groovy bootstrap metadata is completed, and avoids discarding valid tests only because the metadata-fix loop stopped partway through the missing-registration sequence.
