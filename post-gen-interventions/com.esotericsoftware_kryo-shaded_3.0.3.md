# Post-generation intervention report

Library: com.esotericsoftware:kryo-shaded:3.0.3
Stage: metadata_fix_failed

## Summary

The Codex metadata-fix run partially repaired missing reachability metadata for `com.esotericsoftware:kryo-shaded:3.0.3`, reducing the native test failure set, but the final run still failed. Most remaining failures were not metadata-related: the generated tests exercised Kryo/ReflectASM paths that define `*FieldAccess`, `*MethodAccess`, or `*ConstructorAccess` classes at runtime, which Native Image rejects by default. Those generated tests were removed.

After removing the unsupported runtime-class-generation tests, a clean targeted run still has one metadata-related failure:

- `ExternalizableSerializerTest#usesJavaSerializationWhenAnInheritedWriteReplaceMethodIsPresent()` still fails with `MissingReflectionRegistrationError` for `public java.lang.Object()` during `ObjectInputStream` deserialization through Kryo's `JavaSerializer` / `ExternalizableSerializer` path.

## Removed non-metadata failures

The following generated tests were removed because their root cause is Native Image's default prohibition on runtime class definition, not missing reachability metadata:

- `FieldSerializerGenericsUtilTest`: both generated tests instantiate `FieldSerializer` for generated holder classes and ReflectASM tries to define generated `*FieldAccess` classes.
- `FieldSerializerTest`: all three generated tests serialize `IntArray` through `FieldSerializer`, causing ReflectASM to define `com.esotericsoftware.kryo.util.IntArrayFieldAccess` at runtime.
- `MethodAccessTest`: both generated tests call `MethodAccess.get(...)`, causing ReflectASM to define a generated `*MethodAccess` class at runtime.
- `BeanSerializerInnerCachedPropertyTest`: both generated tests create a bean serializer path that attempts generated ReflectASM constructor/method access classes at runtime.
- `ObjectFieldInnerObjectIntFieldTest`: all three generated tests create a `FieldSerializer<IntMap>` with ASM enabled, causing ReflectASM to define `com.esotericsoftware.kryo.util.IntMapFieldAccess` at runtime.
- `ClosureSerializerTest`: the generated lambda serialization test assumes a synthetic lambda `writeReplace` method is available in the native image; the native run failed with `NoSuchMethodException` rather than a missing metadata registration.
- `ExternalizableSerializerTest#writesAndReadsUsingTheExternalizableContract()`: the read path creates a default `FieldSerializer` for the generated `SampleExternalizable` test class, causing a generated `SampleExternalizableFieldAccess` class definition at runtime. The associated `SampleExternalizable` helper was also removed.

## Remaining metadata-related failure

The preserved `ExternalizableSerializerTest#usesJavaSerializationWhenAnInheritedWriteReplaceMethodIsPresent()` still reports missing reflection metadata for `java.lang.Object`'s no-arg constructor when Java deserialization reconstructs the replacement payload:

```text
org.graalvm.nativeimage.MissingReflectionRegistrationError: Cannot reflectively invoke constructor 'public java.lang.Object()'
```

Codex attempted to add metadata for this path, including a `java.lang.Object` constructor entry conditioned on `com.esotericsoftware.kryo.serializers.ExternalizableSerializer`, but the clean native run still reports the missing registration. This indicates the metadata fix was incomplete or the condition does not match the actual `ObjectInputStream` deserialization reachability path. Per the task instructions, this test was preserved because the remaining failure is metadata-related and should be fixed in metadata, not by deleting the test.

## Why the remaining generated support should be preserved

The remaining generated support is valuable because the cleaned test set still exercises broad Kryo behavior that is compatible with Native Image and validates the metadata Codex already added: shaded ASM resource access, class reader/writer behavior, default serializers, Java serialization, array serializers, object-field access for supported primitive/reference cases, reflection serializer factory constructors, Kryo instantiator fallback behavior, `UnsafeUtil`, and utility formatting. In the clean targeted run after intervention, these tests reached `47` successful native tests, leaving only the metadata-related `java.lang.Object()` deserialization registration failure described above.
