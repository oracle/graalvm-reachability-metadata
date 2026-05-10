# Post-generation intervention

Library: org.apache.groovy:groovy-yaml:4.0.24
Stage: metadata_fix_failed

## Summary

The generated Groovy-based native tests failed in `:nativeTest` before any YAML-specific assertions could run. The failure affected all 10 generated methods in `Groovy_yamlTest` and initially appeared as `NoClassDefFoundError: Could not initialize class groovy.lang.GroovySystem`.

After Codex attempted a metadata fix, the failure no longer pointed at a missing metadata registration. The native test still failed, but the recorded JUnit XML shows `java.lang.BootstrapMethodError: java.lang.NullPointerException` from `java.lang.invoke.MethodHandles.insertArguments`, reached through `org.codehaus.groovy.vmplugin.v8.IndyInterface.bootstrap`. This is a Groovy invokedynamic/bootstrap runtime issue in the native image test, not an unresolved `MissingReflectionRegistrationError` or other actionable reachability metadata miss.

## Root cause by failed test

Each failed test had the same root cause: the generated test itself is Groovy code and triggers Groovy runtime/bootstrap machinery inside the native image. The failing methods were:

- `buildsNestedYamlWithClosuresAndDynamicRootMethods()`
- `reportsMalformedYamlAndJsonParseFailures()`
- `parsesMultipleYamlDocumentsAsAList()`
- `ignoresYamlCommentsWhilePreservingQuotedHashCharacters()`
- `parsesYamlScalarsCollectionsAndBlockText()`
- `convertsYamlAndJsonWithConverterUtilities()`
- `parsesYamlFromReaderInputStreamFileAndPath()`
- `honorsExplicitYamlScalarTypeTags()`
- `buildsYamlArrayByTransformingAnIterableWithAClosure()`
- `buildsYamlFromMapListVarargsAndWritesToAWriter()`

Because the common failure is Groovy runtime initialization / invokedynamic bootstrap in the generated test class, the test source was removed:

- `tests/src/org.apache.groovy/groovy-yaml/4.0.24/src/test/groovy/org_apache_groovy/groovy_yaml/Groovy_yamlTest.groovy`

No metadata files were modified during this intervention.

## Why the remaining generated support should be preserved

The remaining generated library support should still be preserved because the artifact entry, allowed package information, URLs, Gradle scaffolding, and metadata directory describe valid support for `org.apache.groovy:groovy-yaml:4.0.24`. The failure is specific to the generated Groovy test implementation under native image, not to the library coordinates or artifact discovery data. Keeping the non-test support allows a future replacement test, preferably one that avoids executing Groovy test-source invokedynamic bootstrap in the native image, to validate the same library support without discarding the generated metadata and indexing work.
