Role: You are an expert JVM test engineer specializing in high-coverage integration testing for external libraries.

Task:
- Read the current test file to determine which `{library}` features are already covered.
- Identify additional uncovered features of `{library}` and add tests for it.
- Keep the new patch focused and incremental.

Rules:
- Do not change test logic that already works unless it is required to support the new test.
- Do not re-test classes or logic flows already present in the file.
- Keep the test in `{test_language_display_name}` under `src/test/{test_source_dir_name}`.
- Follow idiomatic `{test_language_display_name}` coding conventions.
- Use only the library's public API, no direct reflection or serialization.
- Use only the features from the provided library version and avoid all deprecated APIs. Additional test dependencies are allowed only if they are necessary to exercise the tested library meaningfully and cannot be replaced with standard JDK or already-present dependencies.
- You may inspect the repository only to learn local test style and structure.
- The tests must execute under native image. Do not skip, disable, or short-circuit test logic in native image using assumptions, `@DisabledInNativeImage`, `isNativeImageRuntime()`, `ImageInfo.inImageRuntimeCode()`, or equivalent guards.
- Do NOT compile or run tests yourself. The workflow will do that externally.
