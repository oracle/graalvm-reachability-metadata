# Post-generation intervention report

Library: org.aspectj:aspectjweaver:1.9.7
Stage: `metadata_fix_failed`

## Summary

Codex reduced the native test failures, but the remaining failures split into two categories:

1. `ClassLoaderWeavingAdaptorTest` exposed a non-metadata native-image/runtime limitation around AspectJ load-time weaving and runtime class definition, so that generated test was removed.
2. `ResolvedTypeMungerTest` and `AsmManagerTest` still fail because metadata is still missing for legacy Java serialization paths. Those tests were kept.

## Failure-by-failure root cause

### Removed as non-metadata-related

- `org_aspectj.aspectjweaver.ClassLoaderWeavingAdaptorTest#initializesWithConcreteAspectAndLintResource`
- `org_aspectj.aspectjweaver.ClassLoaderWeavingAdaptorTest#initializesWithLegacyUnsafeDefineClassPath`

Root cause:
- These checks depend on `org.aspectj.weaver.loadtime.ClassLoaderWeavingAdaptor` registering and defining a generated concrete aspect at runtime.
- The JVM test output already shows internal `defineClass`/`Unsafe` warnings from AspectJ during this flow, and the native run leaves `adaptor.getNamespace()` empty instead of registering the generated aspect name.
- That makes this a runtime/platform limitation around load-time weaving and dynamic class definition in Native Image, not a missing reachability-metadata entry.

Action taken:
- Removed `tests/src/org.aspectj/aspectjweaver/1.9.7/src/test/java/org_aspectj/aspectjweaver/ClassLoaderWeavingAdaptorTest.java`
- Removed its test-only resource `tests/src/org.aspectj/aspectjweaver/1.9.7/src/test/resources/org_aspectj/aspectjweaver/class-loader-weaving-adaptor-lint.properties`

### Preserved as metadata-related

- `org_aspectj.aspectjweaver.ResolvedTypeMungerTest#serializesAndDeserializesSourceLocationUsingLegacyObjectStreams`

Missing metadata still required:
- Reflection registration for `java.lang.Object` constructor `"<init>"()`

Why it fails:
- `ResolvedTypeMunger.readSourceLocation(...)` deserializes a legacy `SourceLocation` through `ObjectInputStream`.
- Native Image reports `MissingReflectionRegistrationError` for reflective construction of `java.lang.Object` during that deserialization path.

Why Codex could not fully resolve it:
- The remaining miss is in a JDK serialization constructor reached indirectly through `ObjectStreamClass`, not a library-owned reflective call site with an obvious one-step metadata fix.
- Codex partially repaired related native test support, but this deeper object-stream path still needs explicit reflection metadata for a JDK type.

- `org_aspectj.aspectjweaver.AsmManagerTest#persistsAndRestoresStructureModelFromConfigurationPath`

Missing metadata still required:
- Reflection registration for `java.util.AbstractMap` constructor `"<init>"()`

Why it fails:
- `AsmManager.readStructureModel(...)` restores AspectJ's serialized structure model through `ObjectInputStream`.
- Native Image reports `MissingReflectionRegistrationError` for reflective construction of `java.util.AbstractMap` during deserialization.

Why Codex could not fully resolve it:
- This is the same legacy Java serialization problem class as above: the remaining registration is for a JDK type created by the object-stream machinery, not for a direct `org.aspectj.*` reflective access that Codex could finish from the library stack alone.

## Notes from the Codex run

The failure excerpt and Codex log show that the original native failures were broader, including serialization-related misses such as `java.util.HashMap.writeObject(java.io.ObjectOutputStream)` in the simple-cache tests. Codex partially repaired that area, which is why those tests no longer appear among the final remaining failures. The unresolved metadata issues are the later serialization misses listed above.

## Why the remaining generated support should be preserved

Most of the generated `org.aspectj:aspectjweaver:1.9.7` support is still valuable and should remain in place:

- Codex already reduced the failing surface significantly.
- The surviving tests cover real library behavior across reflection, signature resolution, resources, parsers, repositories, caches, and other weaver APIs.
- The remaining kept failures point to specific missing serialization metadata, not to bad or scaffold-only tests.
- Removing the whole generated suite would discard useful coverage that is already working.
