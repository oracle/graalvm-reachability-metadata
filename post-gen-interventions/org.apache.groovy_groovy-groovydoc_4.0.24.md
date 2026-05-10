# Post-generation intervention report

Library: org.apache.groovy:groovy-groovydoc:4.0.24
Stage: metadata_fix_failed

## Summary

The native test run failed in all five generated Groovy test methods. The provided Gradle excerpt showed `groovy.lang.GroovySystem` failing during initialization, and a rebuilt run with the current metadata progressed to a remaining `java.lang.BootstrapMethodError` caused by `java.lang.NullPointerException` in `org.codehaus.groovy.vmplugin.v8.IndyInterface.make` / `MethodHandles.insertArguments`.

The remaining failure is not a reachability-metadata miss for `groovy-groovydoc`; it is caused by executing the generated tests as Groovy-compiled test classes under native image. The failure occurs in Groovy's invokedynamic bootstrap machinery before the tests can validate the library behavior.

The requested Codex log path was not present in this checkout: `logs/org.apache.groovy:groovy-groovydoc:4.0.24/metadata-fix/codex.log`.

## Intervention

Removed the generated Groovy test classes that trigger the native-image/Groovy invokedynamic failure:

- `tests/src/org.apache.groovy/groovy-groovydoc/4.0.24/src/test/groovy/org_apache_groovy/groovy_groovydoc/Groovy_groovydocTest.groovy`
- `tests/src/org.apache.groovy/groovy-groovydoc/4.0.24/src/test/groovy/org_apache_groovy/groovy_groovydoc/GroovydocModelObjectsTest.groovy`

No metadata files were modified.

## Root cause by failing test

- `Groovy_groovydocTest.groovyDocToolBuildsPackageAndClassDocsFromSource(Path)`: non-metadata failure in Groovy invokedynamic bootstrap in native image.
- `Groovy_groovydocTest.groovyDocToolBuildsJavaClassDocsWithMembers(Path)`: same non-metadata Groovy invokedynamic bootstrap failure.
- `GroovydocModelObjectsTest.simpleDocParsesTagsAndReportsDefinitionKinds()`: same non-metadata Groovy invokedynamic bootstrap failure.
- `GroovydocModelObjectsTest.simpleTypesParametersAndMemberDocsExposeConfiguredModelData()`: same non-metadata Groovy invokedynamic bootstrap failure.
- `GroovydocModelObjectsTest.simpleClassDocStoresFieldsMethodsPropertiesAndExternalLinks()`: same non-metadata Groovy invokedynamic bootstrap failure.

## Why the remaining generated support should be preserved

The generated support still adds reachability metadata and index entries for `org.apache.groovy:groovy-groovydoc:4.0.24` and its Groovy runtime dependency. The failure was isolated to the generated Groovy test harness, not to the library support itself. Preserving the metadata keeps the discovered native-image configuration available for consumers and for future tests written without the Groovy-compiled test-class invokedynamic limitation.

## Verification

After removing the failing generated tests, `./gradlew test -Pcoordinates=org.apache.groovy:groovy-groovydoc:4.0.24 --stacktrace` completed successfully.
