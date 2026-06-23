# Post-generation intervention report

Library: org.mockito:mockito-core:5.0.0
Stage: metadata_fix_failed

## Summary

The native test failure was caused by the generated `InlineDelegateByteBuddyMockMakerTest`, specifically `inlineMockMakerReportsInitializationFailureWhenDispatcherResourceCannotBeRead()`. The failing native run threw `ClassNotFoundException: org.mockito.Mockito` from the test's child-first `URLClassLoader` before it could exercise a missing reachability metadata path.

This is not a metadata-related failure. The test deliberately reloads Mockito and Byte Buddy through a custom runtime class loader and then attempts to exercise Mockito's inline Byte Buddy mock maker. Runtime class loading and Byte Buddy-backed inline mocking are unsupported native-image behavior for this workflow, so preserving the test by adding more metadata would not be appropriate.

## Action taken

Removed the generated test file:

- `tests/src/org.mockito/mockito-core/5.0.0/src/test/java/org_mockito/mockito_core/InlineDelegateByteBuddyMockMakerTest.java`

No metadata files were modified as part of this intervention.

## Why the remaining generated support should be preserved

The remaining generated Mockito tests pass in both JVM and native execution after removing only the unsupported inline/class-loader test. They still cover valid native-image-compatible Mockito paths, including interface proxy mocking, plugin initialization through `mockito-extensions`, member accessor reflection, runner-based initialization, captors, spies with supported failure handling, and Java 8 default return behavior.

These passing tests continue to justify the generated support and metadata because they exercise supported reflection, proxy, resource-loading, and Mockito extension paths without relying on runtime bytecode generation, custom redefinition, Java agent self-attach, or Byte Buddy inline mock-maker behavior.

## Verification

Ran:

```text
./gradlew test -Pcoordinates=org.mockito:mockito-core:5.0.0 --stacktrace
```

Result: build passed; native test reported 11 successful tests and 0 failures.
