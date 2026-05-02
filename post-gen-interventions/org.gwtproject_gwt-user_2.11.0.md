# Post-generation intervention

Library: org.gwtproject:gwt-user:2.11.0
Stage: metadata_fix_failed

## Summary

The metadata-fix run reduced the native test failures to a mixed set. Most remaining failures were not missing reachability metadata: they were generated tests that exercised GWT tooling assumptions that do not hold in this native-image test layout, such as GWT compiler module loading from `jar:`/non-file resources and WebAppCreator installation discovery. Those generated tests were removed.

One remaining failure path was metadata-related in the Gradle output: `EnumMapTest.readObjectConsumesSerializedMappings()` still reported missing reflective access to the JDK `java.util.EnumMap` internals, specifically the `keyType` field on the serialization path. Codex continued iterating on `java.util.EnumMap` metadata and the log shows it attempted follow-up entries for `keyType`/`serialVersionUID`, but the workflow did not finish with a verified successful full `./gradlew test -Pcoordinates=org.gwtproject:gwt-user:2.11.0` run after the final metadata edit. I did not modify metadata files in this intervention.

## Failure classification and actions

- `AbstractClientBundleGeneratorTest.compilesClientBundleWithTextResourceGenerator()` failed with `IllegalArgumentException: URI scheme is not "file"` from `ModuleDefLoader`. This is a GWT compiler/resource-loading limitation with classpath resources in the native-image test layout, not a metadata miss. Removed the test and its `clientbundle` test resources.
- `AbstractLocalizableImplCreatorTest.compilesMessagesInterfaceWithGeneratedCatalogFormats()` failed with the same `URI scheme is not "file"` `ModuleDefLoader` path. Removed the test and its generated `i18n` compiler resources.
- `ImageBundleBuilderTest.compilesImageBundleWithClasspathImageResource()` failed with the same non-file URI module-loading issue. Removed the test and its `imagebundlebuilder` resources.
- `MessagesMethodCreatorTest.compilesPluralMessagesForLocalizedLocale()` failed with the same non-file URI module-loading issue. Removed the test and its `messagesmethodcreator` resources.
- `SerializableTypeOracleBuilderTest.compilesRpcServiceWithDetachableJdoEntity()` failed with the same non-file URI module-loading issue. Removed the test and its `serializabletypeoraclebuilder` resources.
- `UiBinderWriterTest.compilesUiRendererTemplateWithEventDispatch()` failed with the same non-file URI module-loading issue. Removed the test and its `uibinder` resources.
- `JUnitShellTest.startsShellAndReadsBannedPlatformAnnotations()` failed because the expected `JUnitFatalLaunchException` was hidden by the same native classpath/module URI handling path, with Codex also noting a related `jar` URL protocol issue in `JettyLauncher`. Removed the generated test.
- `WebAppCreatorTest.mainFlowGeneratesReadmeTemplateFromClasspathResources()` failed with `Installation problem detected, please reinstall GWT` because `WebAppCreator` expects a GWT jar installation directory and cannot determine it from the native-image test layout. Removed the generated test.
- `EnumMapUtilTest.getKeyTypeResolvesTypeForEmptyEnumMap()` failed with `InvalidClassException` due a `com.google.gwt.user.server.rpc.EnumMap` `serialVersionUID` mismatch during Java serialization. This is a generated test fragility/serialization compatibility issue rather than a missing metadata registration. Removed the generated test.
- `EnumMapTest.readObjectConsumesSerializedMappings()` was metadata-related in the Gradle failure output: native-image reported missing reflective field access for `java.util.EnumMap.keyType` on the serialization path. The test was preserved.

## Preserved support

The remaining generated tests should be preserved because the Codex log shows that many real reachability gaps were found and addressed before the non-metadata failures dominated the run, including AutoBean proxy registrations, RPC custom field serializer construction, ECJ `Messages` reflection, and `EnumMap` serialization metadata. The passing and preserved tests continue to exercise meaningful server-side GWT RPC, AutoBean, validation, reflection, serialization, and utility paths without depending on unsupported GWT compiler installation/module-loading behavior in the native-image test harness.

## Verification

After removing only the non-metadata failing tests and their dedicated generated resources, I ran:

```text
./gradlew compileTestJava -Pcoordinates=org.gwtproject:gwt-user:2.11.0 --stacktrace
```

The compile check passed.
