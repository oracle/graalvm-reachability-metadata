# Post-generation intervention report

Library: com.thoughtworks.xstream:xstream:1.4.20
Stage: `metadata_fix_failed`

## Summary

The `metadata-fix` run only partially repaired the generated `xstream` support. The Codex log shows it successfully chased several missing registrations, but the native test lane still contained a mix of:

- **non-metadata native-image limitations** in a few generated tests, and
- **real remaining metadata gaps** in XStream's dynamically loaded mappers, converters, and reflective serialization paths.

I removed the generated tests that fail for non-metadata reasons and kept the rest of the generated support intact.

## Non-metadata failures removed

### 1. `FontConverterTest`
- **Root cause:** not a reachability-metadata problem.
- Codex traced this to an AWT startup failure in native image: `libawt` performs a JNI `FindClass(...)` lookup for `java.awt.GraphicsEnvironment`.
- The log shows this is **not expressible in this repository's allowed metadata schema**: both top-level `jni` and `jniAccessibleType` attempts were rejected by validation/schema checks.
- **Action:** removed `tests/src/com.thoughtworks.xstream/xstream/1.4.20/src/test/java/com_thoughtworks_xstream/xstream/FontConverterTest.java`.

### 2. `LambdaMapperTest#serializesLegacyNamedLambdaProxyWithAdditionalInterfaceAsFunctionalInterface()`
- **Root cause:** not a reachability-metadata problem.
- This test synthesizes a hidden lambda proxy with `MethodHandles.Lookup#defineHiddenClass(...)`.
- Codex identified the native failure as `UnsupportedFeatureError`: runtime class definition / hidden-class generation is not supported by default in Native Image.
- **Action:** removed that generated test method and its hidden-class helper setup from `tests/src/com.thoughtworks.xstream/xstream/1.4.20/src/test/java/com_thoughtworks_xstream/xstream/LambdaMapperTest.java`.

### 3. `TreeSetConverterInnerReflectionsTest#restoresTreeSetWhenOptimizedAddAllIsUnavailable()`
- **Root cause:** not a reachability-metadata problem.
- This test depends on a custom child-first class loader plus bytecode patching to force `JVM.hasOptimizedTreeSetAddAll()` to return `false`.
- The Codex log explicitly called this out as a **custom isolated classloading** / native-image behavior issue. In native image, that classloader-based override does not reliably reproduce the JVM behavior, leading to the assertion `Expected TreeSet.addAll optimization to be disabled`.
- **Action:** removed that generated test path and the supporting isolated-classloader scaffolding from `tests/src/com.thoughtworks.xstream/xstream/1.4.20/src/test/java/com_thoughtworks_xstream/xstream/TreeSetConverterInnerReflectionsTest.java`.

## Remaining metadata-related failures

The remaining failures should **not** be removed. They still point to genuine missing registrations that Codex did not finish resolving.

### Dynamic mapper / converter loading is still incomplete
The Gradle output and Codex log both show XStream constructing parts of its mapper/converter chain with late `Class.forName(...)` lookups. Failures in this bucket include examples such as:

- `ClassNotFoundException: com.thoughtworks.xstream.mapper.EnumMapper`
- `CannotResolveClassException` / `NoClassDefFoundError` around dynamically resolved mapper and converter classes
- later Codex-discovered gaps such as `SubjectConverter` and `PackageAliasingMapper`

These are metadata issues because the classes are present in the library JAR, but native image cannot resolve them without explicit registration.

### Reflective access for serialization helpers is still incomplete
The failures also include unresolved reflective access such as:

- `MissingReflectionRegistrationError` for `java.lang.Object.<init>()`
- `NoSuchFieldException` / reflective lookup failures around XStream internals such as `com.thoughtworks.xstream.core.JVM.optimizedTreeMapPutAll`
- `NoSuchMethodException` for clone paths such as `XmlFriendlyNameCoder.clone()` and `XmlFriendlyReplacer.clone()`

These are still metadata-related because the failing code paths rely on reflection in native image, and the missing members need explicit registration.

### Why Codex could not finish the metadata repair
The Codex log shows an iterative pattern: once one missing registration was added, XStream advanced to the next dynamically loaded mapper/converter or reflective helper. In other words, the failure was not a single missing entry but a broad late-binding surface area inside XStream. Codex reached the point where the clearly non-metadata failures were isolated, but it did not complete the full remaining registration chain.

## Why the remaining generated support should be preserved

The generated support should still be preserved because:

- the surviving tests continue to exercise real XStream native-image behavior;
- the remaining failures are still valuable signal for unresolved metadata, not evidence that the whole generation was invalid;
- Codex already demonstrated that the suite moves forward as registrations are added, which means the generated coverage is materially useful rather than scaffold-only.

In short, only the native-image-incompatible generated tests were removed. The rest of the generated support still captures legitimate `xstream` reachability behavior and should remain available for future metadata completion.
