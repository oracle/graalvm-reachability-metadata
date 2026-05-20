# Post-generation intervention report

Library: org.jetbrains.kotlin:kotlin-compiler-embeddable:2.3.20
Stage: metadata_fix_failed

## Summary

The generated native test image builds, but `nativeTest` fails at runtime. Four generated tests fail while exercising Kotlin compiler CLI entry points and argument parsing:

- `reportsVersionAndAdvancedOptions()` returns `COMPILATION_ERROR` instead of `OK`.
- `acceptsNoSourceCompilationThroughMessageCollector(Path)` throws `ExceptionInInitializerError` from `org.jetbrains.kotlin.cli.common.ArgumentsKt.<clinit>`.
- `parsesJvmCompilerArgumentsIntoStronglyTypedOptions()` rejects valid Kotlin JVM compiler options such as `-jvm-target`, `-module-name`, `-no-stdlib`, and `-no-reflect`.
- `reportsSyntaxDiagnosticsForInvalidKotlinSource(Path)` reports those same options as invalid before it reaches the invalid source file diagnostics.

## Root cause

These failures are metadata-related, so no generated test was removed.

The common failure path is Kotlin compiler argument metadata discovery. The most explicit error is:

```text
Caused by: java.lang.IllegalStateException: Java field should be present for var org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments.fragments: kotlin.Array<kotlin.String>?
  at org.jetbrains.kotlin.cli.common.arguments.ArgumentUtilsKt.getArgumentAnnotation(argumentUtils.kt:123)
  at org.jetbrains.kotlin.cli.common.arguments.ArgumentUtilsKt.getCliArgument(argumentUtils.kt:131)
  at org.jetbrains.kotlin.cli.common.ArgumentsKt.<clinit>(arguments.kt:239)
```

The invalid-argument failures are a downstream symptom of the same incomplete reflective argument metadata: the native image cannot discover the Kotlin compiler argument fields/annotations correctly, so valid `K2JVMCompilerArguments` options are treated as unknown.

The Codex metadata-fix log shows that Codex made partial progress. It fixed earlier metadata gaps for Kotlin argument classes, PSI stub element constructors/arrays, and Kotlin's shaded IntelliJ `ReflectionUtil` access to `sun.misc.Unsafe.theUnsafe`. It also tried an invalid schema property (`queryAllPublicConstructors`) and reverted it after `checkMetadataFiles` rejected the metadata. After those fixes, the remaining failure no longer produced a clean `Missing*RegistrationError` with a suggested JSON snippet; it surfaced as Kotlin reflection failing to map `CommonCompilerArguments.fragments` to its Java field and as parser state losing valid CLI arguments. Because the remaining issue is still reflective metadata discovery but lacks a precise GraalVM missing-registration payload, Codex could not safely derive the next minimal metadata entry before the workflow failed.

## Why the remaining generated support should be preserved

The generated tests that pass continue to exercise useful native-image coverage for compiler configuration keys, diagnostic rendering, and module XML parsing. The failing tests are also valuable because they expose real missing reachability coverage in the Kotlin compiler embeddable CLI path rather than a test-only bug or unsupported platform feature. Keeping the generated support preserves the reproducible failure needed to finish the metadata work without losing coverage for the already-working Kotlin compiler APIs.
