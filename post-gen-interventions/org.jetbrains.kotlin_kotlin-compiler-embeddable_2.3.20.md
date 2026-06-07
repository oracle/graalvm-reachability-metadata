# Post-generation intervention report

Library: org.jetbrains.kotlin:kotlin-compiler-embeddable:2.3.20
Stage: metadata_fix_failed

## Summary

The native test run failed in generated tests that exercise Kotlin compiler command-line parsing, Kotlin lexer/token initialization, and in-process JVM compilation. These failures are metadata-related, so no generated tests were removed and no metadata files were modified during this intervention.

## Failure analysis

- `parsesJvmCompilerCommandLineArguments()` failed because `parseCommandLineArguments` left JVM argument fields such as `languageVersion` unset. This indicates missing reflection metadata for Kotlin compiler argument classes and their reflective field/annotation access, not a test bug.
- `tokenizesKotlinSource()` failed with `NoClassDefFoundError: Could not initialize class org.jetbrains.kotlin.lexer.KtTokens`, rooted in `KtStubElementType` failing to reflectively find the `org.jetbrains.kotlin.psi.KtClass(ASTNode)` constructor. This is missing reflection metadata for Kotlin PSI stub element implementations.
- `compilesKotlinSourceToJvmClassFiles(Path)` failed because the embedded compiler reported valid CLI options as `invalid argument`. This is downstream of the same missing command-line argument metadata path and later the same `KtTokens`/PSI initialization path, so it is metadata-related.
- `reportsCompilationDiagnosticsForInvalidSource(Path)` failed for the same embedded compiler initialization/argument-processing reason: the compiler did not reach normal diagnostic reporting for `Broken.kt` because native-image metadata was still incomplete.

## Why Codex could not finish the metadata fix

The Codex metadata-fix log shows an iterative attempt to add compiler argument reflection metadata and PSI constructor metadata. It fixed some earlier command-line argument handling, but the remaining native run still failed with `KtTokens` class initialization errors and downstream `INTERNAL_ERROR` compiler results. At that point the native output no longer provided a clear `Missing*RegistrationError` with a directly actionable suggested JSON entry; the first failure was hidden behind `NoClassDefFoundError`/class-initialization failure, making the remaining missing PSI/token metadata ambiguous without broader, risky metadata expansion.

## Preservation rationale

The generated support should be preserved because the tests exercise real public usage paths for `kotlin-compiler-embeddable`: parsing compiler arguments, configuring compiler inputs, tokenizing Kotlin source, parsing module XML, and invoking the embeddable JVM compiler. The passing parts already validate useful native-image coverage, and the failing parts identify genuine incomplete reachability metadata rather than unsupported platform behavior or a generated test-only artifact problem.
