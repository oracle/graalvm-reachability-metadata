# Post-generation intervention report

Library: org.apache.groovy:groovy-docgenerator:4.0.24
Stage: metadata_fix_failed

## Summary

The generated native tests for `org.apache.groovy:groovy-docgenerator:4.0.24` all failed in native execution before they could validate `DocGenerator` behavior. The Gradle excerpt shows `groovy.lang.GroovySystem` failing during static initialization with a `NullPointerException` in `MetaClassRegistryImpl`, followed by `NoClassDefFoundError: Could not initialize class groovy.lang.GroovySystem` for the remaining test instances.

The Codex metadata-fix log did not show a remaining `Missing*RegistrationError`. Instead, Codex encountered Groovy runtime/native-image failures around Groovy invokedynamic call-site support, including `UnsupportedFeatureError: Unsupported method java.lang.invoke.MethodHandleNatives.setCallSiteTargetNormal(CallSite, MethodHandle) is reachable` from `org.codehaus.groovy.vmplugin.v8.IndyInterface.realBootstrap`. Codex attempted a test-only substitution for `IndyInterface`, but that moved the failure into invalid test support code on the image classpath rather than revealing missing metadata.

## Root cause by failing test

All five generated test methods had the same non-metadata root cause: the generated test was written as a Groovy test class, so native-image execution had to initialize Groovy's own runtime/metaclass and invokedynamic machinery. That runtime path is not covered by ordinary reachability metadata and currently fails as a native-image/Groovy runtime limitation or generated-test bug.

- `commandLineUsesConfiguredExternalDocumentationLinks()` failed while initializing/executing Groovy runtime call-site support, not because a metadata registration was missing.
- `generatesLinkedHtmlDocumentationFromCommandLine()` failed for the same `GroovySystem`/Groovy indy call-site initialization path.
- `commandLineReportsVersion()` failed for the same `GroovySystem` initialization path.
- `parsesSourceIntoDocumentedGdkTargetTypesAndMethods()` failed before the test could exercise the docgenerator assertions because the Groovy test instance could not be initialized.
- `collectsInheritedGdkMethodsFromDocumentedSuperTypes()` failed for the same generated Groovy test initialization problem.

## Intervention

Because the failures are not metadata-related, I removed the generated failing test class:

- `tests/src/org.apache.groovy/groovy-docgenerator/4.0.24/src/test/groovy/org_apache_groovy/groovy_docgenerator/Groovy_docgeneratorTest.groovy`

I also removed the generated test-only substitution source that Codex added for that failing path:

- `tests/src/org.apache.groovy/groovy-docgenerator/4.0.24/src/test/java/org/codehaus/groovy/vmplugin/v8/Target_IndyInterface.java`

No metadata files were modified during this intervention.

After removing the non-metadata-related generated test code, `./gradlew test -Pcoordinates=org.apache.groovy:groovy-docgenerator:4.0.24 --stacktrace` completed successfully with no test sources to execute for this coordinate.

## Why remaining generated support should still be preserved

The remaining generated support is still valuable because the failure does not prove that the generated reachability metadata is incorrect or unnecessary. The metadata additions describe resources and reflection used by `groovy-docgenerator` and Groovy runtime initialization, such as docgenerator templates/resources and Groovy extension-module resources. Those entries should be preserved for downstream consumers and for future tests that exercise `DocGenerator` from a native-friendly Java test harness rather than from a Groovy test class that triggers unsupported Groovy runtime call-site initialization.
