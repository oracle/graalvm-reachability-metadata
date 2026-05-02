Task:
- Analyze the existing test methods in the provided file to determine the current coverage.
- Identify a new, functional feature of the library `{library}` that is not yet covered by the existing tests, and write tests for it.

Rules:
- Do not change any test logic that is already done.
- Do not re-test classes or logic flows already present in the file.
- Keep the test in `{test_language_display_name}` under `src/test/{test_source_dir_name}`.
- Follow idiomatic `{test_language_display_name}` coding conventions.
- Don't duplicate tested features.
- Use only the library’s public API, no direct reflection or serialization.
- Modify only the test file. Update build.gradle only if absolutely required.
- Use only the provided library version and avoid all deprecated APIs.
- Keep tests version-agnostic. Do not hardcode the artifact version in normal test inputs or assertions.
- The tests must execute under native image. Do not skip, disable, or short-circuit test logic in native image using assumptions, `@DisabledInNativeImage`, `isNativeImageRuntime()`, `ImageInfo.inImageRuntimeCode()`, or equivalent guards.
- Every individual test must complete in under 60 seconds. Use bounded waits and close all clients, servers, executors, and other background resources.
