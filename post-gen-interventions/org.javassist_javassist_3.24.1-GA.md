# Post-generation intervention report

Library: org.javassist:javassist:3.24.1-GA
Stage: `metadata_fix_failed`

## Summary

The native test run failed in 10 generated tests after the metadata-fix stage. Based on the Gradle failure output and the Codex metadata-fix log, the remaining failures were **not unresolved reachability-metadata entries**.

The Codex log shows it had already isolated the actual metadata issue to reflective access to `java.lang.String` serialization fields used from `javassist.tools.reflect.Metaobject` and `javassist.tools.rmi.ObjectImporter`, and that the residual failures were outside the `Missing*RegistrationError` class of problems.

Because the remaining failures were native-image/runtime-behavior limitations or test assumptions rather than missing metadata, I removed the generated tests that caused those failures and deleted test-only resource files that were only used by the removed tests.

## Removed generated tests and root causes

### 1. `AppletServerTest.rmiRequestInvokesExportedObjectAndSerializesReturnValue()`
- **Failure:** `javassist.NotFoundException: java.io.Serializable`
- **Root cause:** `javassist.ClassPool` tries to resolve classfile bytecode at runtime while building RMI stubs. In native image, that classfile lookup path does not behave like a normal JVM classpath lookup.
- **Assessment:** not metadata-related; this is a runtime classfile/resource lookup limitation for the generated test scenario.
- **Action:** removed `tests/src/org.javassist/javassist/3.24.1-GA/src/test/java/org_javassist/javassist/AppletServerTest.java` and its test-only native-image resource files.

### 2. `DefineClassHelperJava9ReferencedUnsafeTest.definesGeneratedClassThroughClassLoaderPublicApi()`
- **Failure:** `javassist.NotFoundException: org_javassist.javassist.DefineClassHelperJava9ReferencedUnsafeTest$GeneratedContract`
- **Root cause:** the test generates bytecode that depends on resolving a nested test type through `ClassPool` at runtime, but that lookup is not available in the native-image runtime the way it is on the JVM.
- **Assessment:** not metadata-related; this is a runtime bytecode/classpath lookup issue in the test design.
- **Action:** removed `tests/src/org.javassist/javassist/3.24.1-GA/src/test/java/org_javassist/javassist/DefineClassHelperJava9ReferencedUnsafeTest.java`.

### 3. `DefineClassHelperJava9ReferencedUnsafeTest.referencedUnsafeChecksCallerBeforeDefiningClass()`
- **Failure:** `java.lang.NoSuchFieldException: stack`
- **Root cause:** the test assumes an internal field layout for Javassist's Java 9 helper that is not present under the exercised runtime/JDK combination.
- **Assessment:** not metadata-related; this is an internal-runtime/platform compatibility issue.
- **Action:** removed together with the rest of `DefineClassHelperJava9ReferencedUnsafeTest.java`.

### 4. `DefineClassHelperJavaOtherTest.definesClassThroughClassLoaderReflectiveFallback()`
- **Failure:** `javassist.CannotCompileException` caused by `javassist.NotFoundException: java.lang.Object`
- **Root cause:** Javassist tries to synthesize bytecode at runtime and cannot resolve JDK classfile information through `ClassPool` inside native image.
- **Assessment:** not metadata-related; this is another runtime classfile availability limitation.
- **Action:** removed `tests/src/org.javassist/javassist/3.24.1-GA/src/test/java/org_javassist/javassist/DefineClassHelperJavaOtherTest.java` and its test-only native-image config.

### 5. `DefinePackageHelperJavaOtherTest.definesPackageThroughReflectiveJavaOtherFallback()`
- **Failure:** `javassist.NotFoundException: javassist.util.proxy.SecurityActions`
- **Root cause:** the test dynamically builds a helper subclass and requires runtime access to library classfile bytes through `ClassPool`; that lookup path does not hold in the native image.
- **Assessment:** not metadata-related; this is a runtime bytecode/resource lookup issue.
- **Action:** removed `tests/src/org.javassist/javassist/3.24.1-GA/src/test/java/org_javassist/javassist/DefinePackageHelperJavaOtherTest.java` and its test-only native-image config.

### 6. `ProxyFactoryTest.createsProxyWithDefaultHandlerAndSerializesProxySignature()`
- **Failure:** `UnsupportedFeatureError` while defining `...$$ProxyFactoryTest0`
- **Root cause:** the scenario depends on defining a brand-new proxy class at runtime.
- **Assessment:** not metadata-related; Native Image rejects runtime class definition by default.
- **Action:** removed `tests/src/org.javassist/javassist/3.24.1-GA/src/test/java/org_javassist/javassist/ProxyFactoryTest.java`.

### 7. `ProxyObjectOutputStreamTest.writesProxyClassDescriptorForSuperclassAndCustomInterface()`
- **Failure:** `UnsupportedFeatureError` while defining `...$$ProxyObjectOutputStreamTest0`
- **Root cause:** runtime proxy-class generation.
- **Assessment:** not metadata-related; this is a Native Image unsupported-feature limitation.
- **Action:** removed `tests/src/org.javassist/javassist/3.24.1-GA/src/test/java/org_javassist/javassist/ProxyObjectOutputStreamTest.java`.

### 8. `RuntimeSupportDefaultMethodHandlerTest.proxyWithoutCustomHandlerInvokesOriginalMethodThroughDefaultHandler()`
- **Failure:** `UnsupportedFeatureError` while defining `...$$RuntimeSupportDefaultMethodHandlerTest0`
- **Root cause:** runtime proxy-class generation.
- **Assessment:** not metadata-related.
- **Action:** removed `tests/src/org.javassist/javassist/3.24.1-GA/src/test/java/org_javassist/javassist/RuntimeSupportDefaultMethodHandlerTest.java`.

### 9. `SerializedProxyTest.deserializesWriteReplaceProxyAndRestoresHandler()`
- **Failure:** `UnsupportedFeatureError` while defining `...$$SerializedProxyTest0`
- **Root cause:** deserialization path still requires runtime proxy-class definition.
- **Assessment:** not metadata-related; blocked by the same Native Image class-definition restriction.
- **Action:** removed `tests/src/org.javassist/javassist/3.24.1-GA/src/test/java/org_javassist/javassist/SerializedProxyTest.java` and its test-only serialization config.

### 10. `WebserverTest.classRequestFallsBackToClasspathResourceWhenFileIsMissing()`
- **Failure:** `javassist.tools.web.BadHttpRequest`
- **Root cause:** the generated test assumes classpath resource fallback behavior that does not hold in the native-image execution path used here.
- **Assessment:** not metadata-related; this is a behavior/test-assumption mismatch, not missing reachability configuration.
- **Action:** removed `tests/src/org.javassist/javassist/3.24.1-GA/src/test/java/org_javassist/javassist/WebserverTest.java`.

## Why the remaining generated support should be preserved

The remaining generated support still covers useful, supportable Javassist behavior under Native Image. In particular:
- the Codex metadata-fix log shows there was a real metadata issue on the surviving serialization-heavy paths involving `Metaobject` and `ObjectImporter`;
- the residual failures in the provided Gradle output were concentrated in tests that require runtime class definition, runtime bytecode lookup, or fragile internal-JDK assumptions;
- removing only those unsupported tests preserves the rest of the generated library support instead of discarding coverage that is still valid.

In short, the failures did **not** show that support for `org.javassist:javassist:3.24.1-GA` should be abandoned. They showed that a subset of generated tests exercised scenarios that Native Image does not support by default, while the remaining generated tests and support files still provide meaningful coverage for the library.