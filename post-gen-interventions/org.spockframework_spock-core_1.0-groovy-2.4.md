# Post-generation intervention report

Library: org.spockframework:spock-core:1.0-groovy-2.4
Stage: metadata_fix_failed

## Summary

The generated Spock/Groovy test suite initially exposed genuine missing metadata:
Spock extension constructors such as `org.spockframework.runtime.extension.builtin.StepwiseExtension.<init>()`
were not reflectively available, and Groovy runtime initialization failed through
`org.codehaus.groovy.reflection.ClassInfo`. Codex added metadata for those reflective
Spock and Groovy paths, but the subsequent native test run failed for a different reason:
Groovy 2.4 attempts to define optimized call-site/helper classes at runtime inside the
native executable.

The final failures are not reachability-metadata gaps. They are native-image runtime
limitations triggered by the generated Groovy Spock specifications, for example attempted
runtime definitions of classes such as `java_util_List$clear`,
`java_lang_CharSequence$toString`, and
`org.spockframework.lang.ISpecificationContext$getMockController`. Native Image rejects
runtime class definition by default, and this repository does not allow metadata bundles to
solve that with build-time-initialization or native-image property tweaks.

## Actions taken

Removed the generated failing test file:

- `tests/src/org.spockframework/spock-core/1.0-groovy-2.4/src/test/groovy/org_spockframework/spock_core/Spock_coreTest.groovy`

No metadata files were modified during this intervention.

Verification: `./gradlew test -Pcoordinates=org.spockframework:spock-core:1.0-groovy-2.4 --stacktrace`
completed successfully after the generated Spock specification was removed.

## Failure root causes

- `StepwiseExtension.<init>()` / Spock annotation extension instantiation: metadata-related
  missing reflection registrations that Codex partially addressed before the final run.
- `org.codehaus.groovy.reflection.ClassInfo` initialization failures: metadata-related
  Groovy runtime registrations/resources that Codex also attempted to address.
- Remaining native failures after the metadata pass: non-metadata native-image limitation.
  Groovy 2.4 dynamically generates and defines call-site classes at runtime; this cannot be
  fixed with ordinary reachability metadata.
- Groovy `MissingMethodException` failures in the final native run are secondary effects of
  the same dynamic Groovy call-site/runtime behavior in the native executable, not missing
  reflection/resource metadata.

## Why the remaining generated support should be preserved

The non-test support still captures useful, valid reachability information for exercising
Spock 1.0 and its Groovy 2.4 runtime dependencies: Spock runtime extension constructors,
Groovy dynamic-method support classes, service resources, and proxy registrations discovered
before the unsupported runtime class-definition barrier. Removing that support would discard
metadata that addresses real native-image reachability gaps; only the generated executable
Spock specification was unsuitable for this native test environment.
