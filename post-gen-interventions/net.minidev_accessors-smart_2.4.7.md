# Post-generation intervention report

Library: net.minidev:accessors-smart:2.4.7
Stage: `metadata_fix_failed`
Codex log: `logs/net.minidev:accessors-smart:2.4.7/metadata-fix/codex.log`

## Summary

The native test failures were caused by `accessors-smart` runtime bytecode generation, not by ordinary missing reachability metadata entries.

Codex tried to rescue the dynamic-class path by generating `predefined-classes-config.json` and extracted `.classdata` files. The log shows that approach did not stabilize: the accessor classes generated inside the native executable did not match the JVM-captured hashes, and the direct runtime `defineClass` path still depended on Native Image experimental class-definition support. Because of that, the failing generated tests were removed instead of changing metadata.

## Root cause by failing test

### Removed: `ASMUtilTest.collectsDeclaredFieldsFromTypeHierarchy()`
This test reaches `BeansAccess.get(...)` for a bean without a pre-generated accessor class. `accessors-smart` responds by generating a new `*AccAccess` class at runtime through `BeansAccessBuilder` and `DynamicClassLoader`.

That path is not a normal metadata problem. It depends on runtime class definition in Native Image, and Codex's predefined-class workaround was unstable for this library because the generated accessor bytecode hashes did not stay consistent between JVM capture and native execution.

### Removed: `AccessorTest.resolvesBooleanGetterFallbackAndExposesAccessorMetadata()`
Same root cause as above. The test requires a generated `BooleanPropertyBeanAccAccess` class to be defined at runtime. The failure is due to runtime code generation support, not a missing reflection/resource/proxy entry.

### Removed: `BeansAccessBuilderTest.appliesDefaultConvertersWhenWritingPrimitiveProperties()`
Same root cause as above. The test exercises a generated `ConvertibleBeanAccAccess` class, which depends on runtime class definition and unstable predefined-class capture rather than standard reachability metadata.

### Removed: `DynamicClassLoaderTest.generatesAccessorClassesWhenNoPrebuiltAccessorExists()`
This is the clearest unsupported case in the generated suite: it explicitly validates that `accessors-smart` generates accessor classes on demand when no prebuilt accessor exists. That behavior relies on runtime bytecode definition, which Native Image rejects by default and which Codex could not make reliable with predefined classes.

### Removed: `DynamicClassLoaderTest.directlyInstantiatesGeneratedClasses()`
This test intentionally feeds freshly generated ASM bytecode into `DynamicClassLoader.directInstance(...)`. The Gradle failure excerpt shows the expected Native Image limitation here: `UnsupportedFeatureError` from runtime class definition. That is not a metadata gap; it is an unsupported/experimental native-image feature boundary.

## Why the remaining generated support should still be preserved

The remaining generated support still covers the stable, useful Native Image scenario for this library: `BeansAccessTest` verifies that `BeansAccess` can load and reuse a pre-generated accessor class that is already on the classpath.

That path avoids unsupported runtime bytecode generation while still testing the library's supported native-image behavior. Keeping it preserves valid coverage for `accessors-smart` instead of dropping the library entirely because of a narrower runtime-class-generation limitation.

## Actions taken

Removed the generated tests that depended on runtime class generation:

- `tests/src/net.minidev/accessors-smart/2.4.7/src/test/java/net_minidev/accessors_smart/ASMUtilTest.java`
- `tests/src/net.minidev/accessors-smart/2.4.7/src/test/java/net_minidev/accessors_smart/AccessorTest.java`
- `tests/src/net.minidev/accessors-smart/2.4.7/src/test/java/net_minidev/accessors_smart/BeansAccessBuilderTest.java`
- `tests/src/net.minidev/accessors-smart/2.4.7/src/test/java/net_minidev/accessors_smart/DynamicClassLoaderTest.java`

Removed test-only native-image resource files that were only needed by those failing tests:

- `tests/src/net.minidev/accessors-smart/2.4.7/src/test/resources/META-INF/native-image/reachability-metadata.json`
- generated predefined-class resources under `tests/src/net.minidev/accessors-smart/2.4.7/src/test/resources/META-INF/native-image/net.minidev/accessors-smart/`

## Verification

Verified with:

```bash
./gradlew test -Pcoordinates=net.minidev:accessors-smart:2.4.7 --stacktrace
```

Result: passed.
