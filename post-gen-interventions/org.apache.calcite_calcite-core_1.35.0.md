# Post-generation intervention report

Library: org.apache.calcite:calcite-core:1.35.0
Stage: `metadata_fix_failed`

## Summary
The generated suite mixed two different failure classes:

1. **Non-metadata failures** caused by Calcite paths that do **runtime Java code generation / Janino compilation inside the native image**.
2. **Metadata failures** caused by still-missing native-image configuration for Calcite's reflective property helpers and Java serialization.

I removed only the generated tests in category 1 and kept the category 2 tests in place.

> Note: the requested Codex log path (`logs/org.apache.calcite:calcite-core:1.35.0/metadata-fix/codex.log`) was not present in this worktree, so the diagnosis below is based on the generated sources, metadata diff, and the Gradle failure output.

## Non-metadata failures removed
These failures are not good metadata candidates because they rely on runtime-generated classes or runtime compiler behavior that Native Image cannot reliably predeclare.

### Removed tests
- `JaninoCompilerTest`
- `EnumerableInterpretableTest`
- `RexExecutableTest`
- `RexToLixTranslatorTest`
- `RexImpTableInnerUserDefinedAggReflectiveImplementorTest`
- `AggregateNodeInnerUdaAccumulatorFactoryTest.bindableAggregateCreatesBuiltInUserDefinedAggregateByNoArgConstructor()`

### Why these are not metadata issues
The failing stack traces revolve around Janino/runtime compilation problems such as:
- `No implementation of org.codehaus.commons.compiler could be loaded`
- generated/runtime-only class names such as `JaninoCompilerGeneratedHello`, `Reducer`, and `GeneratedMetadata_CollationHandler`
- SQL paths that only fail once Calcite switches into enumerable/bindable code generation

Those class names are produced dynamically at runtime, so adding static reachability metadata for them is not a stable or supportable fix. The generated metadata changes also show Codex trying to add entries for synthetic names like `Baz`, `Reducer`, and `JaninoCompilerGeneratedHello`, which confirms the failure mode is runtime code generation rather than missing ordinary library metadata.

## Metadata-related failures preserved
These tests were **not** removed.

### `SaffronPropertiesInnerHelperTest.createsSingletonFromClasspathPropertiesAndDefaults()`
**Root cause:** still-missing effective reflection metadata for Calcite's property-wrapper helper classes, specifically the constructor path for `org.apache.calcite.runtime.Resources$BooleanProp` (and related property wrapper types) used while building `SaffronProperties.INSTANCE`.

**Why this is metadata-related:** the failure is a reflective constructor lookup:
- `NoSuchMethodException: org.apache.calcite.runtime.Resources$BooleanProp.<init>(org.apache.calcite.runtime.Resources$PropertyAccessor, java.lang.reflect.Method)`

That is exactly the kind of constructor access Native Image needs reflection metadata for. This should stay as a signal that Codex did not produce a working reflective configuration for the `Resources` property-helper path.

### `SerializableCharsetTest.restoresNamedCharsetAfterDeserialization()`
### `SerializableCharsetTest.preservesCharsetAcrossJavaSerializationRoundTrip()`
**Root cause:** serialization metadata for `org.apache.calcite.util.SerializableCharset` is still incomplete or ineffective for Native Image deserialization.

**Why this is metadata-related:** GraalVM reports:
- `UnsupportedFeatureError: SerializationConstructorAccessor class not found for declaringClass: org.apache.calcite.util.SerializableCharset`

That points to missing/insufficient serialization configuration, not a bad test. The test should remain so the missing serialization support is fixed in metadata rather than hidden by deleting coverage.

## Why the remaining generated support should be preserved
The remaining generated support still covers real Calcite behaviors that are appropriate for reachability metadata validation: reflective factories, proxy-backed resources, parser metadata, schema/model loading, and serialization/reflection-sensitive utility code.

The removed tests were narrow runtime-codegen/Janino scenarios that are poor metadata targets. By contrast, the preserved failing tests identify concrete metadata gaps that should be fixed later:
- reflective constructor access for `Resources` property helper classes
- serialization support for `SerializableCharset`

Keeping the rest of the generated support preserves useful coverage for Calcite while removing only the unsupported runtime-compiler cases.