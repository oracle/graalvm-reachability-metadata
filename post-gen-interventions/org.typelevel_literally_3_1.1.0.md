# Post-generation intervention report

Library: org.typelevel:literally_3:1.1.0
Stage: metadata_fix_failed

## Summary

The failing generated tests were not failing because of missing GraalVM reachability metadata. Six tests attempted to invoke the Scala 3 compiler (`dotty.tools.dotc.Driver`) from inside the native JUnit executable to compile generated snippets at runtime. In the native-image test environment the compiler could not see the Scala core libraries and reported:

```text
Could not find package scala from compiler core libraries.
Make sure the compiler core libraries are on the classpath.
```

The positive compile assertions then saw `hasErrors == true`, and the negative compile assertions saw the Scala compiler classpath error instead of the expected macro validation messages.

## Root cause by failed test

- `validIntegerLiteralInterpolatorCompilesThroughLiterallyMacro()` — non-metadata test issue. The runtime Scala compiler invocation failed before exercising the `literally` macro, so the test asserted `hasErrors` should be false but it was true.
- `returnedExpressionCanConstructDomainValues()` — non-metadata test issue. The embedded Scala compiler could not load Scala core libraries, so snippet compilation failed before checking returned expression construction.
- `independentLiteralImplementationsCompileInTheSameUserProgram()` — non-metadata test issue. The runtime compiler classpath problem caused compilation to fail before the independent literal implementations were validated.
- `escapeSequenceTextIsPassedToValidation()` — non-metadata test issue. The snippet compiler failed before the literal validation path could run.
- `validationFailuresAreReportedAsCompileTimeErrors()` — non-metadata test issue. The compiler emitted the missing Scala core libraries error instead of the expected `literally` validation error.
- `interpolationArgumentsAreRejectedByTheDefaultLiteralApplyMethod()` — non-metadata test issue. The compiler emitted the missing Scala core libraries error instead of the expected `interpolation not supported` diagnostic.

The Codex metadata-fix log did not show a `MissingReflectionRegistrationError`, `MissingResourceRegistrationError`, or similar metadata registration failure. Codex also observed that `java.class.path` was empty in the native executable and attempted a build/test classpath workaround, but the native run still failed with the same Scala compiler core-library error. That confirms this is a generated test design problem rather than incomplete metadata.

## Intervention

Removed the generated tests that depend on invoking the Scala compiler at runtime, along with their test-only compiler harness and the `scala3-compiler_3` test dependency. Metadata files were not modified.

## Preserved support

The remaining generated support should be preserved because it still gives native-image coverage for the actual `org.typelevel.literally.Literally` runtime-visible API shape used by applications: creating `Literally` instances and accessing their generated `Expr` helpers as ordinary runtime values. This keeps useful reachability coverage for the library without relying on an unsupported/native-hostile runtime Scala compiler invocation.

## Verification

`cd tests/src/org.typelevel/literally_3/1.1.0 && ../../../../../gradlew nativeTest --console=plain --stacktrace` completed successfully after removing the non-metadata tests.
