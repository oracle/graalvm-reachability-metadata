# Post-generation intervention report

Library: org.scala-lang:scala-reflect:2.11.8

Stage: metadata_fix_failed

## Summary

The native test run failed after generation with two distinct failure classes:

- Non-metadata native-image URL protocol failures in tests that construct `jar:` URLs directly.
- Metadata-related Scala reflection/class-loading failures where classes, resources, and Scala runtime symbols are not rooted in the native image.

I removed the generated tests whose only failure cause was the disabled `jar:` URL protocol:

- `tests/src/org.scala-lang/scala-reflect/2.11.8/src/test/scala/org_scala_lang/scala_reflect/ManifestResourcesAnonymous3Test.scala`
- `tests/src/org.scala-lang/scala-reflect/2.11.8/src/test/scala/org_scala_lang/scala_reflect/AbstractFileClassLoaderTest.scala`

## Failure root causes

### Removed: `jar:` URL protocol tests

`ManifestResourcesAnonymous3Test.readsManifestClassEntryFromContextClassLoaderResource()` failed with:

```text
java.net.MalformedURLException: Accessing a URL protocol that was not enabled. The URL protocol jar is not tested and might not work as expected.
```

`AbstractFileClassLoaderTest.buildsProtectionDomainFromScalaRuntimeJarResource()` has the same root cause: it creates a `jar:file:/...!/scala/runtime/package.class` URL. This is not a missing reachability metadata issue; it requires `--enable-url-protocols=jar`, which is a native-image option/platform capability rather than library metadata. The generated tests were removed instead of preserving a test-only native-image option workaround.

### Preserved: Scala reflection/runtime metadata failures

The remaining failures are metadata-related and should not be removed. They exercise real `scala-reflect` behavior that depends on runtime class discovery and reflective access:

- `ReflectionUtilsTest.loadsStaticSingletonModuleByName()` failed because `org_scala_lang.scala_reflect.ReflectionUtilsStaticSingletonFixture$` was not available for `Class.forName` in the native image.
- `ReflectionUtilsTest.invokesInnerSingletonAccessorOnOuterInstance()` failed because the generated inner singleton accessor/class metadata for `ReflectionUtilsOuterFixture.NestedSingleton` was not fully available.
- `RichClassLoaderTest.loadsClassesAndClassResources()`, `createsInstancesWithNoArgAndSelectedConstructors()`, and `runsStaticMainWithContextClassLoader()` failed because the native image lacked test fixture classes/resources loaded by name through `RichClassLoader`.
- The `JavaMirrors...` tests failed during `scala.reflect.runtime` mirror initialization and method/constructor mirroring. The first excerpted failures showed missing Scala core symbols such as `scala.Array`; the Codex loop later progressed to additional missing symbols, ending at `scala.throws`.

These are reachability gaps: the native image needs additional reflection/resource registrations for the Scala runtime classes and the test fixture classes that `scala-reflect` discovers dynamically.

## Why Codex could not finish the metadata fix

The requested Codex log was inspected at the matching repository log location. Codex attempted to regenerate metadata with the native-image tracing agent, but the environment did not have the agent library installed:

```text
Could not find agent library native-image-agent on the library path, with error: libnative-image-agent.so: cannot open shared object file: No such file or directory
```

After regeneration failed, Codex tried manual metadata iteration. That exposed a cascade of Scala runtime symbols needed by `scala.reflect.runtime.JavaMirrors$JavaMirror` (`scala.Array`, `scala.Option`, `scala.Unit`, `scala.annotation.compileTimeOnly`, `scala.AnyValCompanion`, and later `scala.throws`). This indicates the metadata is still incomplete rather than that the generated reflection tests are invalid.

## Why remaining generated support should be preserved

The remaining generated tests cover meaningful `scala-reflect` use cases: runtime mirrors, Java method/constructor mirrors, singleton lookup, `RichClassLoader`, and test fixture class/resource discovery. Their failures are exactly the kind of dynamic-access behavior reachability metadata is meant to support. Removing those tests would hide missing metadata for real Scala reflection paths; only the `jar:` URL protocol tests were removed because their failure is caused by an unsupported/disabled native-image protocol option, not by reachability metadata.
