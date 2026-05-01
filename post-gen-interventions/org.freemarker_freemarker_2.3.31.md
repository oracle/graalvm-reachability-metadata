# Post-generation intervention report

Library: org.freemarker:freemarker:2.3.31
Stage: metadata_fix_failed

## Summary

The Codex metadata-fix pass partially fixed the generated FreeMarker support. It reduced the native runtime failures, but the post-fix Gradle run still had six failures. Five failures were not reachability-metadata issues, so I removed the generated test coverage that exercised unsupported native-image or test-only class-loading behavior. The remaining failure is metadata-related and was preserved.

After removing the non-metadata tests, `./gradlew test -Pcoordinates=org.freemarker:freemarker:2.3.31 --stacktrace` reports 55 native tests with 54 successful and 1 failed.

## Root cause by failure

- `FreemarkerDebugImplDebuggerServerInnerDebuggerAuthProtocolTest.authenticatesDebuggerClientAndRejectsWrongPassword()`
  - **Classification:** metadata-related; test preserved.
  - **Root cause:** the RMI server thread raises `MissingReflectionRegistrationError` while serializing a return value because `java.lang.Throwable.writeObject(java.io.ObjectOutputStream)` is not registered for reflective invocation. The client then observes `java.rmi.UnmarshalException` with nested `EOFException`.
  - **Missing metadata still required:** reflection metadata for private method `java.lang.Throwable.writeObject(java.io.ObjectOutputStream)`. Codex stopped after adding related Throwable/Exception fields and exception hierarchy registrations, but did not add this private serialization method.

- `FreemarkerDebugImplRmiDebugModelImplStubTest.initializesGeneratedRmiStubWithFreshClassLoader()`
  - **Classification:** not metadata-related; generated test removed.
  - **Root cause:** the test asserts that an RMI stub class can be loaded by a custom fresh class loader. In the native image the class is already part of the closed-world image and is reported as loaded by the application class loader, so the assertion cannot be satisfied by metadata.

- `FreemarkerDebugImplRmiDebuggedEnvironmentImplStubTest.initializesGeneratedRmiDebuggedEnvironmentStubWithFreshClassLoader()`
  - **Classification:** not metadata-related; generated test removed.
  - **Root cause:** same native-image class-loading limitation as above. The test additionally patches class-file debug attributes before defining the class, which is test-only dynamic class-definition behavior rather than library reachability metadata.

- `FreemarkerDebugImplRmiDebuggerListenerImplStubTest.initializesGeneratedRmiDebuggerListenerStubWithFreshClassLoader()`
  - **Classification:** not metadata-related; only this generated method and its test-only loader helper were removed.
  - **Root cause:** the test tries to locate and reload `freemarker/debug/impl/RmiDebuggerListenerImpl_Stub.class` through a custom URL class loader. In the native image this class-file resource is not available as a normal classpath resource for redefinition. The same test class still preserves the useful RMI listener stub dispatch coverage.

- `FreemarkerExtDomNodeModelTest.evaluatesXmlQueryWithConfiguredXPathSupport()`
  - **Classification:** not metadata-related; generated test removed.
  - **Root cause:** FreeMarker's default DOM XPath initialization reaches `SunInternalXalanXPathSupport`, which implements JDK-internal `com.sun.org.apache.xml.internal.utils.PrefixResolver`. On JDK 25, `java.xml` does not export that package to the unnamed module, causing `IllegalAccessError`. This is a JDK module-export/platform limitation, not missing native-image metadata.

- `FreemarkerTemplateUtilityClassUtilTest.classBasedResourceFallbackUsesResourceUrlLookupAfterStreamLookupFailure()`
  - **Classification:** not metadata-related; only this generated method and its test-only byte-array class loader were removed.
  - **Root cause:** the test defines `fm.ClassUtilResourceAnchor` from embedded bytecode via a custom class loader. Native image cannot satisfy this dynamic test-only class-loading pattern, so `ClassNotFoundException` is expected and cannot be fixed with reachability metadata.

## Changes made

- Removed generated tests that depended on unsupported native-image/custom class-loading behavior:
  - `tests/src/org.freemarker/freemarker/2.3.31/src/test/java/fm/FreemarkerDebugImplRmiDebugModelImplStubTest.java`
  - `tests/src/org.freemarker/freemarker/2.3.31/src/test/java/fm/FreemarkerDebugImplRmiDebuggedEnvironmentImplStubTest.java`
  - `tests/src/org.freemarker/freemarker/2.3.31/src/test/java/fm/FreemarkerExtDomNodeModelTest.java`
- Removed only the failing generated methods/helpers from:
  - `tests/src/org.freemarker/freemarker/2.3.31/src/test/java/fm/FreemarkerDebugImplRmiDebuggerListenerImplStubTest.java`
  - `tests/src/org.freemarker/freemarker/2.3.31/src/test/java/fm/FreemarkerTemplateUtilityClassUtilTest.java`
- Did not modify metadata files.

## Why the remaining generated support should be preserved

The remaining generated tests cover real FreeMarker reachability behavior across template loading, bean introspection, JSP/taglib integration, Jython object-wrapper selection, serialization, logging, resource bundles, RMI stub dispatch, and template utility resource lookup. After removing only tests that rely on unsupported native-image platform behavior, the suite still has 54 successful native tests. The one remaining failing test exposes a concrete missing metadata entry (`java.lang.Throwable.writeObject(java.io.ObjectOutputStream)`) and should stay as a regression target for the next metadata-fix pass.
