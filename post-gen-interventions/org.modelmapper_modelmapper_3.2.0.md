# Post-generation intervention report

Library: org.modelmapper:modelmapper:3.2.0
Stage: `metadata_fix_failed`
Codex log: `logs/org.modelmapper:modelmapper:3.2.0/metadata-fix/codex.log`

## Summary

The generated native test suite originally failed in two broad ways:

1. **68 failures were not metadata-related** and came from tests that exercise Byte Buddy runtime class generation, agent/instrumentation flows, VM-internal field patching, or other native-image-incompatible behavior.
2. **1 failure is still metadata-related**: `SunReflectionFactoryHelperTest` still throws `MissingReflectionRegistrationError` for `java.lang.Object.<init>()`.

I removed the generated tests that only covered the non-metadata-native-image limitations and kept the metadata-signaling test in place.

After those removals, the targeted run is down to a single failure:

- `./gradlew test -Pcoordinates=org.modelmapper:modelmapper:3.2.0 --stacktrace`
- Result: `43` tests successful, `1` test failed
- Remaining failure: `org_modelmapper.modelmapper.SunReflectionFactoryHelperTest > createsSerializationConstructorBackedInstantiator()`

## Non-metadata failures that were removed

### 1. Byte Buddy runtime class definition / agent / module-dispatch tests

Most failures were caused by Byte Buddy paths that expect to define helper classes, install transformers, inspect instrumentation state, or bootstrap dispatcher/proxy classes at runtime.

Representative errors:

- `UnsupportedFeatureError: Classes cannot be defined at runtime by default...`
- `NoClassDefFoundError: Could not initialize class org.modelmapper.internal.bytebuddy.utility.dispatcher.JavaDispatcher`
- `NoClassDefFoundError: Could not initialize class org.modelmapper.internal.bytebuddy.description.type.TypeDescription$ForLoadedType`
- `NoClassDefFoundError: Could not initialize class org.modelmapper.internal.bytebuddy.utility.JavaModule`

These are native-image/runtime limitations or test-shape issues, not missing reachability metadata. The affected generated tests were removed because they assert behavior that depends on runtime code generation, instrumentation, or VM/module mechanisms that Native Image does not support in this configuration.

This bucket included the generated tests around:

- `AgentBuilder*`
- `AnnotationDescription*` / `AnnotationValue*`
- `ByteArrayClassLoader*`
- `ClassFileLocator*` (instrumentation/module-specific ones)
- `ClassInjector*` (instrumentation/unsafe/reflection injection ones)
- `ClassReloadingStrategyTest`
- `ClassVisitorFactoryInnerCreateClassVisitorFactoryTest`
- `JavaDispatcher*` dynamic-dispatch tests
- `LambdaFactoryTest`
- `MemberSubstitution*`
- `NexusAccessorInnerDispatcherInnerCreationActionTest`
- `PrivilegedMemberLookupActionTest`
- `ResettableClassFileTransformer*`
- `TypeDescription*LazyProxy*`
- `TypePoolInnerClassLoadingTest`
- related downstream tests such as `ExplicitMappingBuilderTest` and `MembersTest`, which failed only because `ProxyFactory`/Byte Buddy initialization failed first

For classes that also had passing coverage, only the failing methods were removed and the passing methods were preserved.

### 2. Tests that patch or depend on VM-private field layouts

A smaller set of failures came from tests that try to poke private/internal fields that are not present in the Native Image runtime layout:

- `Android18InstantiatorTest` → `NoSuchFieldException: type`
- `AndroidSerializationInstantiatorTest` → `NoSuchFieldException: declaredMethods`
- `GCJInstantiatorTest` → `NoSuchFieldException: type`
- `GCJSerializationInstantiatorTest` → `NoSuchFieldException: type`
- `PercInstantiatorTest` → `NoSuchFieldException: newInstanceMethod`

These are not metadata gaps. They are assumptions about alternate VM internals or reflective patch points that do not hold in this native-image environment, so those generated tests were removed.

### 3. Native-image semantic mismatch tests

Two failures were test-behavior mismatches rather than metadata gaps:

- `ClassFileVersionInnerVersionLocatorInnerResolverTest` expected VM version resolution logic that produced `Unknown Java version: 0` under Native Image.
- `TypeResolverTest.resolvesLambdaMethodReferenceArgumentsFromConstantPool()` expected lambda/method-reference generic information from constant-pool inspection, but Native Image resolved the arguments as `TypeResolver.Unknown`.

Those expectations are runtime-specific and not fixed by reachability metadata, so the failing methods were removed while preserving the passing methods in the same classes.

## Remaining metadata-related failure

The only remaining failure is:

- `SunReflectionFactoryHelperTest.createsSerializationConstructorBackedInstantiator()`
- Error: `MissingReflectionRegistrationError` for reflective invocation of `public java.lang.Object()`

The missing registration reported by the native run is:

```json
{
  "type": "java.lang.Object",
  "methods": [
    {
      "name": "<init>",
      "parameterTypes": []
    }
  ]
}
```

### Why this is classified as metadata-related

The failure is a direct GraalVM missing-reflection-registration error coming from:

- `org.modelmapper.internal.objenesis.instantiator.sun.SunReflectionFactoryInstantiator.newInstance(...)`

That is exactly the kind of failure that should be solved by reachability metadata, so the test was **not** removed.

### Why Codex could not resolve it cleanly

The Codex log shows it already reached the point of adding an unconditional `java.lang.Object.<init>()` registration in `metadata/org.modelmapper/modelmapper/3.2.0/reachability-metadata.json`, but the native run still reports the same missing reflection registration afterward.

That means the remaining issue is not another obvious non-metadata test bug. Instead, it suggests that one of the following still needs investigation outside this post-generation intervention step:

- the registration is still incomplete for this reflective construction path,
- the registration is not being consumed as expected during the native test image build, or
- this `ReflectionFactory`-backed serialization-constructor path needs a more specific metadata shape than the straightforward constructor entry Codex added.

Because the failure is still a true missing-registration error, I preserved the test and documented the unresolved metadata gap instead of deleting it.

## Why the remaining generated support should be preserved

The remaining generated support is still valuable:

- after trimming native-image-incompatible tests, `43` generated tests pass,
- those passing tests still cover useful `modelmapper` behavior such as object mapping, class/resource helpers, package/manifest handling, supported instantiator paths, ASM/type utilities, and other non-agent/non-runtime-codegen flows,
- the surviving failing test now isolates the only remaining actionable metadata problem instead of being buried under dozens of unrelated native-image limitation failures.

In short, the post-generation support is still worth keeping because it now provides a mostly stable regression suite plus one focused metadata signal that still needs a follow-up metadata fix.
