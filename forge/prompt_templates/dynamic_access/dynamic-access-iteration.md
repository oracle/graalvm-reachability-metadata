Role: You are an expert JVM test engineer specializing in high-coverage integration testing for external libraries.

Task:
Improve test coverage for the active dynamic-access class in `{library}`.

Source Context:
{source_context_overview}

Active class:
- Class: {active_class_name}
- Source file: {active_class_source_file}

Progress since the previous dynamic-access report:
{dynamic_access_progress}

Remaining uncovered dynamic-access call sites for this class:
{uncovered_dynamic_access_calls}

Rules:
- Add or refine tests so execution reaches the remaining uncovered call sites for the active class.
- Focus on the active class only.
- Keep coverage for the active class in its own dedicated `{test_language_display_name}` test file under `src/test/{test_source_dir_name}` in `graalvm-reachability-metadata`.
- Maintain a one-to-one mapping between dynamic-access report classes and generated test class files.
- Never use `$` in test class or file names. When naming test classes for inner or anonymous classes (classes whose name contains `$`), replace `$` followed by a number (anonymous classes like `Foo$4`) with `Anonymous4` (e.g., `FooAnonymous4Test`), and replace `$` followed by a name (named inner classes like `Foo$Bar`) with `Inner` and the name (e.g., `FooInnerBarTest`).
- Do not broaden the patch into unrelated features.
- Use upstream test sources only as behavioral examples.
- Use documentation only as API guidance.
- Do not use reflection directly in the tests unless the public API requires it naturally.
- After finishing this generation, create a git commit with a focused message describing the test changes.
- Do not compile or run tests yourself. The workflow will do that externally.
- Follow idiomatic `{test_language_display_name}` coding conventions.
- All top-level test classes must be public.
- Keep tests outside the library’s packages. Do not place a test in the same package as the library just to access package-private or internal code.
- Keep tests version-agnostic. Do not hardcode the artifact version in normal test inputs or assertions.
- The tests must execute under native image. Do not skip, disable, or short-circuit test logic in native image using assumptions, `@DisabledInNativeImage`, `isNativeImageRuntime()`, `ImageInfo.inImageRuntimeCode()`, or equivalent guards.
