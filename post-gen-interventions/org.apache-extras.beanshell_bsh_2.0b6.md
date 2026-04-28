# Post-generation intervention report

Library: org.apache-extras.beanshell:bsh:2.0b6
Stage: metadata_fix_failed

## Summary

Native-image testing still had three generated test failures after the Codex metadata-fix pass.

I removed one failure that is **not** metadata-related:

- `tests/src/org.apache-extras.beanshell/bsh/2.0b6/src/test/java/org_apache_extras_beanshell/bsh/BshClassLoaderTest.java`
  - Removed `loadClassDelegatesToClassManagerBaseLoaderWhenNoLocalUrlMatches()`.
  - Root cause: this assertion depends on a synthetic base-loader callback being observed for an already built-in test class. Under Native Image, the class resolves without recording that callback, so the assertion fails with `loadedClassName == null`. That is a test assumption / class-loading behavior difference, not a missing reachability metadata entry.

I did **not** remove the remaining two failing tests because they are still metadata-related:

- `ClassBrowserTest.selectingClassDisplaysPublicConstructorsMethodsAndFields()`
  - Root cause: the AWT/Swing JNI bootstrap metadata is still incomplete for `ClassBrowser`.
  - The current native failure is `java.lang.NoSuchMethodError: java.awt.Component.getLocationOnScreen_NoTreeLock()Ljava/awt/Point;` during `java.awt.Component.initIDs`.
  - The Codex log shows it already generated and manually patched a large amount of AWT/Swing metadata, but it did not converge on the final private `java.awt.Component` JNI entry needed by this path.

- `ClassGeneratorUtilTest.scriptedSubclassGenerationInspectsSuperclassConstructorsAndOverriddenMethods()`
  - Root cause: Native Image still lacks the **correct predefined-class metadata** for the runtime-generated class `GeneratedArrayListCoverage`.
  - The remaining failure is `UnsupportedFeatureError` for hash `6ULO3OhZ5vEuOcTIXLCGv6` not being present in `predefined-classes-config.json`.
  - The Codex log shows why it could not finish this fix:
    - the tracing-agent flow produced a predefined-class hash, but `metadataCopy` failed because the corresponding `.classdata` file was missing;
    - a later manual recovery produced different hashes than the one Native Image asked for.
  - So the unresolved gap is not generic reflection metadata anymore; it is the exact predefined-class artifact for the runtime-generated BeanShell subclass.

## Why the remaining generated support should be preserved

The remaining generated support still covers useful BeanShell functionality and should not be discarded.

From the native test results, `28/31` generated tests were already passing. The preserved support still validates:

- interpreter execution,
- reflection and property access,
- array allocation and initialization,
- collection iteration,
- resource and classpath lookup,
- HTTP/resource serving,
- script bootstrap, and
- generated-script loading for the passing path.

Keeping that support preserves real coverage and narrows the unresolved follow-up work to two specific metadata gaps:

1. one remaining AWT/JNI bootstrap entry for `ClassBrowser`, and
2. one missing exact predefined-class artifact for `GeneratedArrayListCoverage`.
